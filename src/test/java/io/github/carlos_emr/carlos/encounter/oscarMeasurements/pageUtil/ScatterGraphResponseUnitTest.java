/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.eform.util.BrowserChartRenderer;
import io.github.carlos_emr.carlos.eform.util.ChartRenderFailure;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** The public scatter endpoint must preserve binary responses and expose actionable, safe failures. */
@Tag("unit")
@Tag("measurement")
class ScatterGraphResponseUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void prepare() {
        var security = createAndRegisterMock(SecurityInfoManager.class);
        var dao = createAndRegisterMock(MeasurementDao.class);
        createAndRegisterMock(MeasurementTypeDao.class);
        var login = mock(LoggedInInfo.class);
        request = new MockHttpServletRequest("GET", "/carlos/encounter/ScatterPlotChart");
        request.setParameter("demographicNo", "101");
        request.setParameter("type", "BP");
        request.setParameter("mInstrc", "Lab");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        when(security.hasPrivilege(login, "_measurement", "r", "101")).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(login, 101)).thenReturn(true);
        Date day = new Date(1_705_276_800_000L);
        var reading = new Measurement();
        reading.setDateObserved(day);
        reading.setDataField("120/80");
        when(dao.findByDemographicNoTypeAndMeasuringInstruction(101, "BP", "Lab")).thenReturn(List.of(day));
        when(dao.findByDemographicNoTypeAndDate(101, "BP", day)).thenReturn(reading);
        response = new MockHttpServletResponse();
    }

    @Test
    void shouldReturnNoContent_whenNoNumericSeriesExists() throws Exception {
        request.setParameter("type", "WT");
        new ScatterPlotChartServlet().service(request, response);
        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getContentType()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {422, 500, 503})
    void shouldDiscardPartialImages_andClassifyFailuresSafely(int status) throws Exception {
        Throwable failure = status == 503 ? new ChartRenderFailure(ChartRenderFailure.Reason.BUSY, "SYNTHETIC_PRIVATE")
                : status == 422 ? new IllegalArgumentException("SYNTHETIC_PRIVATE") : new IllegalStateException("SYNTHETIC_PRIVATE");
        try (var images = mockStatic(BrowserChartRenderer.class); var logs = LogCapture.forLogger(ScatterPlotChartServlet.class)) {
            images.when(() -> BrowserChartRenderer.write(any(), any(), any(), anyInt(), anyInt(), anyString()))
                    .thenAnswer(invocation -> {
                        invocation.<java.io.OutputStream>getArgument(1).write(new byte[]{1, 2, 3});
                        throw failure;
                    });
            new ScatterPlotChartServlet().service(request, response);
            assertThat(logs.messages()).anySatisfy(message -> assertThat(message)
                    .contains("exception=" + failure.getClass().getName()).doesNotContain("SYNTHETIC_PRIVATE"));
            assertThat(logs.events()).allSatisfy(event -> assertThat(event.getThrown()).isNull());
        }
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getContentType()).isNull();
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getErrorMessage()).doesNotContain("SYNTHETIC_PRIVATE");
        assertThat(response.getHeader("Retry-After")).isEqualTo(status == 503 ? "5" : null);
    }

    @Test
    void shouldPreserveACommittedResponse_whenRenderingFails() throws Exception {
        response.getOutputStream().write(7);
        response.setCommitted(true);
        try (var images = mockStatic(BrowserChartRenderer.class)) {
            images.when(() -> BrowserChartRenderer.write(any(), any(), any(), anyInt(), anyInt(), anyString()))
                    .thenThrow(new IllegalStateException("SYNTHETIC_PRIVATE"));
            new ScatterPlotChartServlet().service(request, response);
        }
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsByteArray()).containsExactly((byte) 7);
        assertThat(response.getErrorMessage()).isNull();
    }
}
