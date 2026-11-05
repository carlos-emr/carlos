/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsExtDao;
import io.github.carlos_emr.carlos.eform.util.BrowserChartRenderer;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.MeasurementGraphAction22Action;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.demographic.data.DemographicNameAgeString;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.IOException;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The graph action streams an image, so it must own its error response: a renderer failure
 * (service down, renderer busy) answers an HTTP error with no image headers and no HTML body
 * rather than letting Struts replace the image with an error page.
 */
@Tag("unit")
@Tag("measurement")
class MeasurementGraphErrorResponseUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;
    private LoggedInInfo login;

    @BeforeEach
    void setUp() {
        security = createAndRegisterMock(SecurityInfoManager.class);
        createAndRegisterMock(MeasurementDao.class);
        createAndRegisterMock(MeasurementsExtDao.class);
        login = mock(LoggedInInfo.class);
        request = new MockHttpServletRequest("GET", "/carlos/encounter/GraphMeasurements");
        request.setContextPath("/carlos");
        request.getSession().setAttribute("userrole", "doctor");
        request.getSession().setAttribute("user", "999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        request.setParameter("demographicNo", "101");
        request.setParameter("type", "WT");
        response = new MockHttpServletResponse();
        when(security.hasPrivilege(login, "_measurement", "r", "101")).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(login, 101)).thenReturn(true);
    }

    private MeasurementGraphAction22Action action() {
        MeasurementChart graph = mock(MeasurementChart.class);
        return new MeasurementGraphAction22Action() {
            @Override
            MeasurementChart defaultChart(Integer patient, String firstType, String secondType,
                                          String patientName, String chartTitle) {
                return graph;
            }
        };
    }

    @Test
    void shouldAnswer503WithoutImageHeaders_whenTheRendererFails() throws Exception {
        DemographicNameAgeString nameLookup = mock(DemographicNameAgeString.class);
        when(nameLookup.getNameAgeString(login, 101)).thenReturn("Synthetic patient");
        try (var servlet = mockStatic(ServletActionContext.class);
             var names = mockStatic(DemographicNameAgeString.class);
             var images = mockStatic(BrowserChartRenderer.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            names.when(DemographicNameAgeString::getInstance).thenReturn(nameLookup);
            images.when(() -> BrowserChartRenderer.write(any(), any(), any(), anyInt(), anyInt(), anyString()))
                    .thenThrow(new IOException("Chart rendering failed"));

            assertThat(action().execute()).isEqualTo("none");

            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(response.getContentType()).isNotEqualTo("image/png");
            assertThat(response.getContentAsByteArray()).isEmpty();
        }
    }

    @Test
    void shouldSendImageHeadersOnlyAfterRendering_whenTheRendererSucceeds() throws Exception {
        DemographicNameAgeString nameLookup = mock(DemographicNameAgeString.class);
        when(nameLookup.getNameAgeString(login, 101)).thenReturn("Synthetic patient");
        byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
        try (var servlet = mockStatic(ServletActionContext.class);
             var names = mockStatic(DemographicNameAgeString.class);
             var images = mockStatic(BrowserChartRenderer.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            names.when(DemographicNameAgeString::getInstance).thenReturn(nameLookup);
            images.when(() -> BrowserChartRenderer.write(any(), any(), any(), anyInt(), anyInt(), anyString()))
                    .thenAnswer(invocation -> {
                        invocation.<java.io.OutputStream>getArgument(1).write(png);
                        return null;
                    });

            assertThat(action().execute()).isEqualTo("none");

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentType()).isEqualTo("image/png");
            assertThat(response.getContentAsByteArray()).isEqualTo(png);
        }
    }
}
