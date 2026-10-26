/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.dao.ValidationsDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.commn.model.Validations;
import io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("measurement")
class ScatterPlotRequestIsolationUnitTest extends CarlosUnitTestBase {
    private SecurityInfoManager security;
    private MeasurementDao measurements;
    private MeasurementTypeDao types;
    private ValidationsDao validations;
    private LoggedInInfo login;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        security = createAndRegisterMock(SecurityInfoManager.class);
        measurements = createAndRegisterMock(MeasurementDao.class);
        types = createAndRegisterMock(MeasurementTypeDao.class);
        validations = createAndRegisterMock(ValidationsDao.class);
        login = mock(LoggedInInfo.class);
        session = new MockHttpSession();
        LoggedInInfo.setLoggedInInfoIntoSession(session, login);
        when(security.isAllowedAccessToPatientRecord(eq(login), anyInt())).thenReturn(true);
        EctSessionBean global = new EctSessionBean();
        global.demographicNo = "202";
        session.setAttribute("EctSessionBean", global);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "aliases", "duplicates", "overflow", "blank"})
    void shouldReturn400BeforeSpringLookupDespiteSessionChart(String kind) throws Exception {
        MockHttpServletRequest request = request(null);
        switch (kind) {
            case "aliases" -> { request.setParameter("demographicNo", "101"); request.setParameter("demographic_no", "202"); }
            case "duplicates" -> request.setParameter("demographicNo", "101", "202");
            case "overflow" -> request.setParameter("demographicNo", "2147483648");
            case "blank" -> request.setParameter("demographicNo", "");
        }
        springUtilsMock.clearInvocations();
        MockHttpServletResponse response = new MockHttpServletResponse();
        new ScatterPlotChartServlet().service(request, response);
        assertThat(response.getStatus()).isEqualTo(400);
        springUtilsMock.verifyNoInteractions();
        verifyNoInteractions(security, measurements, types, validations);
    }

    @Test
    void shouldReturn403ForRequestedPatientBeforeReadingMeasurements() throws Exception {
        when(security.hasPrivilege(login, "_measurement", "r", "202")).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new ScatterPlotChartServlet().service(request("101"), response);
        assertThat(response.getStatus()).isEqualTo(403);
        verify(security).hasPrivilege(login, "_measurement", "r", "101");
        verifyNoInteractions(measurements, types, validations);
    }

    @Test
    void shouldBuildEachGraphFromOnlyItsRequestedPatientsReadings() throws Exception {
        when(security.hasPrivilege(eq(login), eq("_measurement"), eq("r"), anyString())).thenReturn(true);
        MeasurementType type = new MeasurementType();
        type.setValidation("1");
        when(types.findByTypeAndMeasuringInstruction("WT", "kg")).thenReturn(List.of(type));
        Validations numeric = new Validations();
        numeric.setNumeric(true);
        when(validations.find(1)).thenReturn(numeric);
        Date observed = new Date(1700000000000L);
        for (int patient : new int[]{101, 202}) {
            when(measurements.findObservationDatesByDemographicNoTypeAndMeasuringInstruction(patient, "WT", "kg"))
                    .thenReturn(List.of(observed));
            Measurement reading = new Measurement();
            reading.setDemographicId(patient);
            reading.setDateObserved(observed);
            reading.setDataField(patient == 101 ? "61" : "92");
            when(measurements.findByDemographicNoTypeAndDate(patient, "WT", observed)).thenReturn(reading);
        }
        List<Number> plotted = new ArrayList<>();
        try (var jpeg = mockStatic(ChartUtils.class)) {
            jpeg.when(() -> ChartUtils.writeChartAsJPEG(any(java.io.OutputStream.class), eq(1.0f),
                    any(JFreeChart.class), eq(550), eq(360))).thenAnswer(call -> {
                        JFreeChart chart = call.getArgument(2);
                        plotted.add(chart.getXYPlot().getDataset().getY(0, 0));
                        return null;
                    });
            for (String patient : new String[]{"000101", "202", "101"}) {
                MockHttpServletResponse response = new MockHttpServletResponse();
                new ScatterPlotChartServlet().service(request(patient), response);
                assertThat(response.getContentType()).isEqualTo("image/jpeg");
            }
        }
        assertThat(plotted).extracting(Number::intValue).containsExactly(61, 92, 61);
        verify(measurements, times(2)).findObservationDatesByDemographicNoTypeAndMeasuringInstruction(101, "WT", "kg");
        verify(measurements).findObservationDatesByDemographicNoTypeAndMeasuringInstruction(202, "WT", "kg");
        assertThat(((EctSessionBean) session.getAttribute("EctSessionBean")).demographicNo).isEqualTo("202");
    }

    @Test
    void shouldRefuseLockedRecord_beforeReadingOrRenderingAnyMeasurement() throws Exception {
        when(security.hasPrivilege(login, "_measurement", "r", "101")).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(login, 101)).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new ScatterPlotChartServlet().service(request("000101"), response);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getContentType()).isNull();
        verify(security).isAllowedAccessToPatientRecord(login, 101);
        verifyNoInteractions(measurements, types, validations);
    }

    private MockHttpServletRequest request(String patient) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/encounter/oscarMeasurements/ScatterPlot");
        request.setSession(session);
        if (patient != null) request.setParameter("demographicNo", patient);
        request.setParameter("type", "WT");
        request.setParameter("mInstrc", "kg");
        return request;
    }
}
