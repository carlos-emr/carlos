/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsExtDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementsExt;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementsDataBean;
import io.github.carlos_emr.carlos.eform.util.BrowserChartRenderer;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.demographic.data.DemographicNameAgeString;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.IOException;
import java.io.Serializable;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabTestValues;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

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
    private MeasurementsExtDao extensions;
    private LoggedInInfo login;

    @BeforeEach
    void setUp() {
        security = createAndRegisterMock(SecurityInfoManager.class);
        createAndRegisterMock(MeasurementDao.class);
        extensions = createAndRegisterMock(MeasurementsExtDao.class);
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
        when(security.hasPrivilege(login, "_lab", "r", "101")).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(login, 101)).thenReturn(true);
    }

    private MeasurementGraphAction22Action action() {
        MeasurementChart graph = mock(MeasurementChart.class);
        return new MeasurementGraphAction22Action() {
            @Override
            MeasurementChart defaultChart(Integer patient, String firstType, String secondType,
                                          String chartTitle) {
                return graph;
            }
        };
    }

    @Test
    void shouldRefuseRawSourceGraph_whenLabReadPrivilegeIsMissing() throws Exception {
        request.setParameter("method", "actualLab");
        when(security.hasPrivilege(login, "_lab", "r", "101")).thenReturn(false);
        DemographicNameAgeString nameLookup = mock(DemographicNameAgeString.class);
        when(nameLookup.getNameAgeString(login, 101)).thenReturn("Synthetic patient");
        try (var servlet = mockStatic(ServletActionContext.class);
             var names = mockStatic(DemographicNameAgeString.class);
             var images = mockStatic(BrowserChartRenderer.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            names.when(DemographicNameAgeString::getInstance).thenReturn(nameLookup);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> action().execute()).isInstanceOf(SecurityException.class);
            images.verifyNoInteractions();
            assertThat(response.getContentAsByteArray()).isEmpty();
        }
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
                    .thenThrow(new io.github.carlos_emr.carlos.eform.util.ChartRenderFailure(
                            io.github.carlos_emr.carlos.eform.util.ChartRenderFailure.Reason.UNAVAILABLE, "Chart rendering unavailable"));

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
    @ParameterizedTest
    @ValueSource(strings = {"inclRef", "labRef"})
    @SuppressWarnings("unchecked")
    void shouldPlotBothReferenceBounds_whenStoredAsSeparateMeasurementExtensions(String method) throws Exception {
        request.setParameter("method", method);
        when(extensions.getMeasurementsExtByMeasurementId(42)).thenReturn(List.of(
                extension("minimum", "55"), extension("maximum", "70")));
        AtomicReference<MeasurementChart> rendered = new AtomicReference<>();
        executeMeasurementChart("63", rendered);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("image/png");
        var datasets = (List<Map<String, Object>>) rendered.get().toConfig().get("datasets");
        assertThat(datasets).filteredOn(dataset -> "range".equals(dataset.get("kind"))).singleElement()
                .satisfies(dataset -> assertThat((List<Map<String, Object>>) dataset.get("data")).singleElement()
                        .satisfies(range -> assertThat(range).containsEntry("low", 55.0).containsEntry("high", 70.0)));
        verify(extensions).getMeasurementsExtByMeasurementId(42);
        verify(extensions, never()).find(anyInt());
    }

    /**
     * The former charts never found these bounds and still drew the measurement, so one malformed stored
     * bound leaves that range out of the image instead of failing the whole chart.
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldDrawTheChartWithoutTheRange_whenStoredReferenceBoundsAreInvalid() throws Exception {
        request.setParameter("method", "labRef");
        when(extensions.getMeasurementsExtByMeasurementId(42)).thenReturn(List.of(
                extension("minimum", "synthetic invalid bound"), extension("maximum", "70")));
        AtomicReference<MeasurementChart> rendered = new AtomicReference<>();
        executeMeasurementChart("63", rendered);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("image/png");
        var datasets = (List<Map<String, Object>>) rendered.get().toConfig().get("datasets");
        assertThat(datasets).noneMatch(dataset -> "range".equals(dataset.get("kind")))
                .anyMatch(dataset -> "line".equals(dataset.get("kind")));
    }

    @Test
    void shouldAnswer500WithoutRendering_whenMeasurementExtensionsCannotBeLoaded() throws Exception {
        request.setParameter("method", "inclRef");
        when(extensions.getMeasurementsExtByMeasurementId(42)).thenThrow(new IllegalStateException("Synthetic database unavailable"));
        AtomicReference<MeasurementChart> rendered = new AtomicReference<>();
        try (var logs = io.github.carlos_emr.carlos.test.logging.LogCapture.forLogger(MeasurementGraphAction22Action.class)) {
            executeMeasurementChart("63", rendered);
            assertThat(logs.messages()).anySatisfy(message -> assertThat(message)
                    .contains("reason=INTERNAL_ERROR", "exception=java.lang.IllegalStateException")
                    .doesNotContain("Synthetic database unavailable"));
            assertThat(logs.events()).allSatisfy(event -> assertThat(event.getThrown()).isNull());
        }

        assertThat(rendered).hasNullValue();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentType()).isNotEqualTo("image/png");
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRenderAnEmptyChart_whenNoMeasurementsAreAvailable() throws Exception {
        AtomicReference<MeasurementChart> rendered = new AtomicReference<>();
        executeMeasurementChart(null, rendered);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat((List<Map<String, Object>>) rendered.get().toConfig().get("datasets")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"loinc", "CML", "HL7"})
    @SuppressWarnings("unchecked")
    void shouldPlotLabObservations_whenTheProviderUsesNormalizedCollectionDates(String labType) throws Exception {
        request.setParameter("method", "actualLab");
        request.setParameter("labType", labType);
        request.setParameter("identifier", "synthetic-loinc");
        request.setParameter("testName", "Synthetic weight");
        request.setParameter("drug", new String[0]);
        Map<String, Serializable> observation = "loinc".equals(labType)
                ? Map.of("result", "63", "units", "kg", "collDateDate", "2024-01-15 12:00:00")
                : "HL7".equals(labType) ? Map.of("result", "63", "units", "kg", "collDate", "2024-01-15")
                : Map.of("result", "63", "units", "kg", "collDate", "15-Jan-24",
                        "collDateDate", new Date(1_705_276_800_000L));
        AtomicReference<MeasurementChart> rendered = new AtomicReference<>();
        try (var labs = mockStatic(CommonLabTestValues.class)) {
            if ("loinc".equals(labType)) {
                labs.when(() -> CommonLabTestValues.findValuesByLoinc2("101", "synthetic-loinc"))
                        .thenReturn(List.of(observation));
            } else {
                labs.when(() -> CommonLabTestValues.findValuesForTest(labType, 101, "Synthetic weight", "synthetic-loinc"))
                        .thenReturn(new ArrayList<>(List.of(observation)));
            }
            executeMeasurementChart(null, rendered);
        }

        assertThat(response.getStatus()).isEqualTo(200);
        var datasets = (List<Map<String, Object>>) rendered.get().toConfig().get("datasets");
        assertThat(datasets).singleElement().satisfies(dataset -> {
            assertThat(dataset).containsEntry("label", "Synthetic weight");
            assertThat((List<Map<String, Object>>) dataset.get("data")).singleElement()
                    .satisfies(point -> {
                        assertThat(point).containsEntry("y", 63.0);
                        if ("HL7".equals(labType)) {
                            long timestamp = ((Number) point.get("x")).longValue();
                            assertThat(java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate())
                                    .isEqualTo(java.time.LocalDate.of(2024, 1, 15));
                        }
                    });
        });
    }

    /**
     * The former lab-plus-medication chart drew an empty lab panel above the medications when the LOINC
     * lookup failed, and LOINC rows without an observed date (the text "null") have no point to plot.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SuppressWarnings("unchecked")
    void shouldDrawTheMedicationChart_whenLoincValuesFailOrHaveNoDate(boolean lookupFails) throws Exception {
        request.setParameter("method", "actualLab");
        request.setParameter("labType", "loinc");
        request.setParameter("identifier", "synthetic-loinc");
        request.setParameter("testName", "Synthetic weight");
        request.setParameter("drug", new String[0]);
        AtomicReference<MeasurementChart> rendered = new AtomicReference<>();
        try (var labs = mockStatic(CommonLabTestValues.class)) {
            if (lookupFails) {
                labs.when(() -> CommonLabTestValues.findValuesByLoinc2("101", "synthetic-loinc"))
                        .thenThrow(new IllegalStateException("Synthetic lookup failure"));
            } else {
                labs.when(() -> CommonLabTestValues.findValuesByLoinc2("101", "synthetic-loinc"))
                        .thenReturn(List.of(Map.of("result", "63", "units", "kg", "collDateDate", "null")));
            }
            executeMeasurementChart(null, rendered);
        }

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("image/png");
        assertThat(rendered.get().toConfig()).containsEntry("medicationPanel", true);
        var datasets = (List<Map<String, Object>>) rendered.get().toConfig().get("datasets");
        assertThat(datasets).singleElement().satisfies(dataset -> assertThat((List<?>) dataset.get("data")).isEmpty());
    }

    private void executeMeasurementChart(String result, AtomicReference<MeasurementChart> rendered) throws Exception {
        DemographicNameAgeString nameLookup = mock(DemographicNameAgeString.class);
        when(nameLookup.getNameAgeString(login, 101)).thenReturn("Synthetic patient");
        ArrayList<EctMeasurementsDataBean> values = new ArrayList<>();
        if (result != null) {
            values.add(new EctMeasurementsDataBean(42, "WT", "Weight", "kg", "101", "", "",
                    result, "", "", "2024-01-15", "2024-01-15", "1",
                    new Date(1_705_276_800_000L), new Date(1_705_276_800_000L)));
        }
        try (var servlet = mockStatic(ServletActionContext.class);
             var names = mockStatic(DemographicNameAgeString.class);
             var images = mockStatic(BrowserChartRenderer.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            names.when(DemographicNameAgeString::getInstance).thenReturn(nameLookup);
            images.when(() -> BrowserChartRenderer.write(any(), any(), any(), anyInt(), anyInt(), anyString()))
                    .thenAnswer(invocation -> {
                        rendered.set(invocation.getArgument(2));
                        return null;
                    });
            MeasurementGraphAction22Action graphAction = new MeasurementGraphAction22Action() {
                @Override
                ArrayList<EctMeasurementsDataBean> getList(Integer patient, String type) {
                    return values;
                }
            };
            assertThat(graphAction.execute()).isEqualTo("none");
        }
    }

    private static MeasurementsExt extension(String key, String value) {
        MeasurementsExt extension = new MeasurementsExt(42);
        extension.setKeyVal(key);
        extension.setVal(value);
        return extension;
    }

}
