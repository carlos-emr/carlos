/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.FlowSheetUserCreatedDao;
import io.github.carlos_emr.carlos.commn.dao.Icd9Dao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.TicklerDao;
import io.github.carlos_emr.carlos.commn.dao.TicklerLinkDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.dao.ValidationsDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.commn.model.Validations;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementTemplateFlowSheetConfig;
import io.github.carlos_emr.carlos.flowsheet.Flowsheet2Action;
import io.github.carlos_emr.carlos.mds.pageUtil.ReportMacro2Action;
import io.github.carlos_emr.carlos.measurements.web.MeasurementData2Action;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

@DisplayName("JSON response header regression tests")
@Tag("unit")
@Tag("web")
class JsonResponseHeaderRegressionTest extends CarlosUnitTestBase {
    private static final Path PROJECT_ROOT = Path.of(System.getProperty("user.dir"));
    private static final Path REPORT_MACRO_ACTION = PROJECT_ROOT.resolve(
            "src/main/java/io/github/carlos_emr/carlos/mds/pageUtil/ReportMacro2Action.java");
    private static final Path MEASUREMENT_DATA_ACTION = PROJECT_ROOT.resolve(
            "src/main/java/io/github/carlos_emr/carlos/measurements/web/MeasurementData2Action.java");

    private MockedStatic<ServletActionContext> servletActionContextMock;

    @AfterEach
    void tearDownServletActionContext() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should set JSON content type before report macro writes")
    void shouldSetJsonContentType_beforeReportMacroWrites() throws IOException {
        String source = Files.readString(REPORT_MACRO_ACTION, StandardCharsets.UTF_8);

        int contentTypeIndex = source.indexOf("response.setContentType(JSON_CONTENT_TYPE);");
        int firstWriteIndex = source.indexOf("response.getWriter().write");

        assertThat(source).contains("application/json; charset=UTF-8");
        assertThat(contentTypeIndex).isNotNegative();
        assertThat(firstWriteIndex).isNotNegative();
        assertThat(contentTypeIndex).isLessThan(firstWriteIndex);
    }

    @Test
    @DisplayName("should set JSON content type for measurement write paths")
    void shouldSetJsonContentType_forMeasurementWritePaths() throws IOException {
        String source = Files.readString(MEASUREMENT_DATA_ACTION, StandardCharsets.UTF_8);

        int getLatestValuesIndex = source.indexOf("public String getLatestValues()");
        int javascriptContentTypeIndex = source.indexOf(
                "response.setContentType(\"application/javascript; charset=UTF-8\");", getLatestValuesIndex);
        int latestValuesWriteIndex = source.indexOf("response.getWriter().print(script);", getLatestValuesIndex);
        int getDataByTypeIndex = source.indexOf("public String getDataByType()");
        int directJsonContentTypeIndex = source.indexOf(
                "response.setContentType(JSON_CONTENT_TYPE);", getDataByTypeIndex);
        int directJsonWriteIndex = source.indexOf("objectMapper.writeValue(response.getWriter(), json);",
                getDataByTypeIndex);
        int writeJsonIndex = source.indexOf("private void writeJson");
        int helperJsonContentTypeIndex = source.indexOf("response.setContentType(JSON_CONTENT_TYPE);",
                writeJsonIndex);
        int helperJsonWriteIndex = source.indexOf(
                "response.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));", writeJsonIndex);

        assertThat(javascriptContentTypeIndex).isNotNegative();
        assertThat(latestValuesWriteIndex).isNotNegative();
        assertThat(javascriptContentTypeIndex).isLessThan(latestValuesWriteIndex);
        assertThat(directJsonContentTypeIndex).isNotNegative();
        assertThat(directJsonWriteIndex).isNotNegative();
        assertThat(directJsonContentTypeIndex).isLessThan(directJsonWriteIndex);
        assertThat(helperJsonContentTypeIndex).isNotNegative();
        assertThat(helperJsonWriteIndex).isNotNegative();
        assertThat(helperJsonContentTypeIndex).isLessThan(helperJsonWriteIndex);
    }


    @Test
    @DisplayName("should emit UTF-8 JSON for flowsheet validations")
    void shouldEmitUtf8Json_forFlowsheetValidations() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCharacterEncoding(StandardCharsets.ISO_8859_1.name());
        configureServletActionContext(request, response);

        ValidationsDao validationsDao = mock(ValidationsDao.class);
        registerFlowsheetActionBeans(mock(FlowSheetUserCreatedDao.class), validationsDao);
        when(validationsDao.findAll()).thenReturn(List.of(newValidation("José 東京")));

        new Flowsheet2Action().getValidations();

        String body = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(response.getContentType()).isEqualTo("application/json; charset=UTF-8");
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(body).contains("José 東京");
    }

    @Test
    @DisplayName("should emit UTF-8 JSON for flowsheet deletion")
    void shouldEmitUtf8Json_forDeleteFlowsheet() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCharacterEncoding(StandardCharsets.ISO_8859_1.name());
        request.setParameter("id", "42");
        configureServletActionContext(request, response);

        FlowSheetUserCreatedDao flowsheetUserCreatedDao = mock(FlowSheetUserCreatedDao.class);
        registerFlowsheetActionBeans(flowsheetUserCreatedDao, mock(ValidationsDao.class));
        MeasurementTemplateFlowSheetConfig flowsheetConfig = mock(MeasurementTemplateFlowSheetConfig.class);

        try (MockedStatic<MeasurementTemplateFlowSheetConfig> flowsheetConfigMock =
                     mockStatic(MeasurementTemplateFlowSheetConfig.class)) {
            flowsheetConfigMock.when(MeasurementTemplateFlowSheetConfig::getInstance).thenReturn(flowsheetConfig);

            new Flowsheet2Action().deleteFlowsheet();
        }

        String body = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(response.getContentType()).isEqualTo("application/json; charset=UTF-8");
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(body).contains("\"success\":true", "\"id\":\"42\"");
        verify(flowsheetUserCreatedDao).remove(42);
        verify(flowsheetConfig).reloadFlowsheets();
    }

    @Test
    @DisplayName("should emit UTF-8 JSON for report macro")
    void shouldEmitUtf8Json_forReportMacro() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCharacterEncoding(StandardCharsets.ISO_8859_1.name());
        request.setParameter("name", "東京");
        addLoggedInInfo(request);
        configureServletActionContext(request, response);

        SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        registerMock(TicklerDao.class, mock(TicklerDao.class));
        registerMock(TicklerLinkDao.class, mock(TicklerLinkDao.class));
        UserPropertyDAO userPropertyDAO = createAndRegisterMock(UserPropertyDAO.class);
        UserProperty macroProperty = new UserProperty();
        macroProperty.setValue("[{\"name\":\"東京\"}]");

        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_lab"), eq("w"), isNull()))
                .thenReturn(true);
        when(userPropertyDAO.getProp(eq("999998"), eq(UserProperty.LAB_MACRO_JSON))).thenReturn(macroProperty);

        new ReportMacro2Action().execute();

        assertThat(response.getContentType()).isEqualTo("application/json; charset=UTF-8");
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8)).contains("\"success\":true");
    }

    @Test
    @DisplayName("should emit UTF-8 JSON for measurement data")
    void shouldEmitUtf8Json_forMeasurementData() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCharacterEncoding(StandardCharsets.ISO_8859_1.name());
        request.setParameter("demoNo", "123");
        request.setParameter("typeList", "bp");
        request.setParameter("searchDate", "");
        configureServletActionContext(request, response);

        MeasurementDao measurementDao = createAndRegisterMock(MeasurementDao.class);
        createAndRegisterMock(OscarAppointmentDao.class);
        SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        Measurement measurement = newMeasurement("bp", "José 東京");

        when(measurementDao.findMeasurementByTypeAndDate(eq(123), eq("bp"), any(), any()))
                .thenReturn(List.of(measurement));

        MeasurementData2Action action = new MeasurementData2Action();
        injectDependency(action, "measurementDao", measurementDao);
        injectDependency(action, "securityInfoManager", securityInfoManager);

        action.getDataByType();

        String body = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(response.getContentType()).isEqualTo("application/json; charset=UTF-8");
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(body).contains("José 東京");
    }

    @Test
    @DisplayName("should emit UTF-8 JSON for measurement output stream")
    void shouldEmitUtf8Json_forMeasurementOutputStream() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCharacterEncoding(StandardCharsets.ISO_8859_1.name());
        request.setParameter("demographicNo", "123");
        request.setParameter("measurementType", "bp");
        configureServletActionContext(request, response);

        MeasurementDao measurementDao = createAndRegisterMock(MeasurementDao.class);
        createAndRegisterMock(OscarAppointmentDao.class);
        createAndRegisterMock(SecurityInfoManager.class);
        Measurement measurement = newMeasurement("bp", "José 東京");

        when(measurementDao.findByType(eq(123), eq("bp"))).thenReturn(List.of(measurement));

        MeasurementData2Action action = new MeasurementData2Action();
        injectDependency(action, "measurementDao", measurementDao);

        action.getMeasurementsByType();

        String body = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(response.getContentType()).isEqualTo("application/json; charset=UTF-8");
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(body).contains("José 東京");
    }

    @Test
    @DisplayName("should emit UTF-8 JavaScript for latest measurement values")
    void shouldEmitUtf8JavaScript_forLatestMeasurementValues() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCharacterEncoding(StandardCharsets.ISO_8859_1.name());
        request.setParameter("demographicNo", "123");
        request.setParameter("types", "bp");
        request.setParameter("appointmentNo", "1");
        addLoggedInInfo(request);
        configureServletActionContext(request, response);

        MeasurementDao measurementDao = createAndRegisterMock(MeasurementDao.class);
        OscarAppointmentDao appointmentDao = createAndRegisterMock(OscarAppointmentDao.class);
        SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        HashMap<String, Measurement> measurements = new HashMap<>();
        measurements.put("bp", newMeasurement("bp", "José 東京"));

        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"), eq("123")))
                .thenReturn(true);
        when(appointmentDao.getAppointmentHistory(123)).thenReturn(List.of());
        when(measurementDao.getMeasurements(eq(123), any(String[].class))).thenReturn(measurements);

        MeasurementData2Action action = new MeasurementData2Action();
        injectDependency(action, "measurementDao", measurementDao);
        injectDependency(action, "appointmentDao", appointmentDao);
        injectDependency(action, "securityInfoManager", securityInfoManager);

        action.getLatestValues();

        String body = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(response.getContentType()).isEqualTo("application/javascript; charset=UTF-8");
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(body).contains("jQuery");
    }

    private void registerFlowsheetActionBeans(FlowSheetUserCreatedDao flowsheetUserCreatedDao,
            ValidationsDao validationsDao) {
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        registerMock(FlowSheetUserCreatedDao.class, flowsheetUserCreatedDao);
        registerMock(ProviderDao.class, mock(ProviderDao.class));
        registerMock(Icd9Dao.class, mock(Icd9Dao.class));
        registerMock(DemographicDao.class, mock(DemographicDao.class));
        registerMock(MeasurementTypeDao.class, mock(MeasurementTypeDao.class));
        registerMock(ValidationsDao.class, validationsDao);
    }

    private Validations newValidation(String name) {
        Validations validation = new Validations();
        validation.setId(7);
        validation.setName(name);
        validation.setRegularExp(".*");
        validation.setMinValue(1.0);
        validation.setMaxValue(10.0);
        validation.setMinLength(1);
        validation.setMaxLength(20);
        return validation;
    }

    private void configureServletActionContext(MockHttpServletRequest request, MockHttpServletResponse response) {
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
    }

    private void addLoggedInInfo(MockHttpServletRequest request) {
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setLoggedInProvider(provider);
        loggedInInfo.setIp("127.0.0.1");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
    }

    private Measurement newMeasurement(String type, String dataField) {
        Measurement measurement = new Measurement();
        measurement.setType(type);
        measurement.setDataField(dataField);
        measurement.setAppointmentNo(1);
        measurement.setCreateDate(new Date());
        measurement.setDateObserved(new Date());
        return measurement;
    }
}
