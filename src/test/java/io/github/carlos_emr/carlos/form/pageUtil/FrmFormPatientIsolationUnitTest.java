/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.form.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.EctFindMeasurementTypeUtil;
import io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean;
import io.github.carlos_emr.carlos.form.FrmRecordHelp;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPatientData;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual form actions must keep the requesting window's patient at every persistence boundary. */
@Tag("unit")
@Tag("controller")
@DisplayName("Measurement form patient isolation across chart windows")
public class FrmFormPatientIsolationUnitTest extends CarlosUnitTestBase {
    private static final String DEFINITION = "<formProp><measurement><type>WT</type>"
            + "<typeDesc>Weight</typeDesc><measuringInstrc>in kg</measuringInstrc>"
            + "<validationRule><name>Weight</name><regularExp>.*</regularExp>"
            + "<maxLength>20</maxLength></validationRule></measurement>"
            + "<measurement><type>iEx</type><typeDesc>Eye</typeDesc><validationRule>"
            + "<name>Yes/No</name><regularExp>yes|no</regularExp></validationRule></measurement>"
            + "<measurement><type>WHR</type><typeDesc>Ratio</typeDesc><validationRule>"
            + "<name>Ratio</name><isNumeric>1</isNumeric><minValue>0.5</minValue><maxValue>2</maxValue>"
            + "</validationRule></measurement></formProp>";
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private LoggedInInfo user;
    private SecurityInfoManager security;
    private EncounterFormDao forms;
    private MeasurementDao measurements;
    private DemographicManager demographics;
    private EChartDao charts;
    private EctSessionBean lastChart;

    @BeforeEach
    void prepare() {
        MockServletContext context = new MockServletContext() {
            @Override public String getRealPath(String path) {
                return Path.of("src/main/webapp/form").toAbsolutePath().toString();
            }
            @Override public InputStream getResourceAsStream(String path) {
                assertThat(path).isEqualTo("/form/VTForm.xml");
                return definition();
            }
        };
        request = new MockHttpServletRequest(context);
        request.setContextPath("/carlos");
        response = new MockHttpServletResponse();
        user = new LoggedInInfo();
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        user.setLoggedInProvider(provider);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        lastChart = new EctSessionBean();
        lastChart.setDemographicNo("202");
        request.getSession().setAttribute("EctSessionBean", lastChart);
        security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(eq(user), anyString(), anyString(), anyString())).thenReturn(true);
        forms = createAndRegisterMock(EncounterFormDao.class);
        EncounterForm registered = new EncounterForm();
        registered.setFormValue("../form/SetupForm.do?formName=VTForm&demographic_no=");
        registered.setFormTable("formVTForm");
        when(forms.findByFormTable("formVTForm")).thenReturn(List.of(registered));
        measurements = createAndRegisterMock(MeasurementDao.class);
        // The save runs the form row and the measurements in one transaction (#4674); this test's
        // persistence is mocked, so a mock transaction manager stands in for the real one.
        createAndRegisterMock(PlatformTransactionManager.class);
        createAndRegisterMock(MeasurementTypeDao.class);
        createAndRegisterMock(DemographicCustDao.class);
        createAndRegisterMock(BillingDao.class);
        createAndRegisterMock(AllergyDao.class);
        charts = createAndRegisterMock(EChartDao.class);
        demographics = createAndRegisterMock(DemographicManager.class);
        for (String id : List.of("101", "202")) {
            Demographic patient = new Demographic();
            patient.setDemographicNo(Integer.parseInt(id));
            patient.setFirstName("Synthetic " + id);
            patient.setLastName("Owned");
            patient.setSex("F");
            patient.setYearOfBirth("1980");
            patient.setMonthOfBirth("01");
            patient.setDateOfBirth("02");
            when(demographics.getDemographic(user, id)).thenReturn(patient);
        }
        request.setParameter("formName", "VTForm");
        request.setParameter("value(formName)", "VTForm");
        request.setParameter("value(formId)", "0");
        request.setParameter("value(visitCod)", "2026-10-15");
        request.setParameter("value(WTValue)", "75");
        request.setParameter("value(WTDate)", "2026-10-15");
        request.setParameter("value(WTComments)", "Owned chart measurement");
        for (String field : List.of("diagnosisVT", "subjective", "objective", "assessment", "plan")) {
            request.setParameter("value(" + field + ")", "Synthetic " + field);
        }
        request.setParameter("submit", "save");
    }

    private static InputStream definition() {
        return new ByteArrayInputStream(DEFINITION.getBytes(StandardCharsets.UTF_8));
    }

    private MockedStatic<ServletActionContext> servlet() {
        MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        return servlet;
    }

    @ParameterizedTest
    @CsvSource({"101,202,0", "202,101,0", "101,202,88", "202,101,88"})
    void shouldRenderRequestedPatient_whenAnotherChartWasOpenedLast(String patient, String other, String formId) throws Exception {
        request.setParameter("demographic_no", patient);
        request.setParameter("value(formId)", formId);
        lastChart.setDemographicNo(other);
        var types = EctFindMeasurementTypeUtil.loadMeasurementTypes(definition());
        RxPatientData.Patient rxPatient = mock(RxPatientData.Patient.class);
        when(rxPatient.getPrescribedDrugsUnique()).thenReturn(new RxPrescriptionData.Prescription[0]);
        when(rxPatient.getActiveAllergies()).thenReturn(new Allergy[0]);
        ResultSet owned = ownedForm(patient);
        try (var servlet = servlet();
             var jdbc = mockStatic(LegacyJdbcQuery.class, CALLS_REAL_METHODS);
             var rx = mockStatic(RxPatientData.class);
             var definitions = mockStatic(EctFindMeasurementTypeUtil.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getPreparedResultSet(any(LegacyJdbcQuery.TrustedSql.class), eq(88), eq(Integer.parseInt(patient))))
                    .thenReturn(owned);
            rx.when(() -> RxPatientData.getPatient(user, Integer.parseInt(patient))).thenReturn(rxPatient);
            definitions.when(() -> EctFindMeasurementTypeUtil.checkMeasurmentTypes(any(), eq("VTForm"))).thenReturn(types);

            assertThat(new FrmSetupForm2Action().execute()).isEqualTo(ActionSupport.NONE);

            assertThat(response.getForwardedUrl()).isEqualTo("/WEB-INF/jsp/form/formVTForm.jsp");
            assertThat(request.getAttribute("formDemographicNo")).isEqualTo(patient);
            assertThat(((Demographic) request.getAttribute("formPatient")).getDemographicNo()).isEqualTo(Integer.valueOf(patient));
            assertThat(((Map<?, ?>) request.getAttribute("formValues")).get("visitCod")).isNotNull();
            Map<?, ?> values = (Map<?, ?>) request.getAttribute("formValues");
            assertThat(values.get("formId")).isEqualTo(formId);
            // No measurement history must render as an empty cell, never JSP's literal "null"
            // (#4612). This is checked on both fresh setup and an existing form's redisplay.
            for (String type : List.of("WT", "iEx", "WHR")) {
                assertThat(request.getAttribute(type + "LastData")).isEqualTo("");
                assertThat(request.getAttribute(type + "LDDate")).isEqualTo("");
            }
            if ("88".equals(formId)) {
                assertThat(values.get("WTValue")).isEqualTo("72.5");
                assertThat(values.get("WTComments")).isEqualTo("Owned <script> text");
                assertThat(values.get("subjective")).isEqualTo("Owned subjective");
                verify(owned).close();
            } else {
                assertThat(values.get("iExValue")).isEqualTo("");
                assertThat(values.get("iExDate")).isEqualTo("");
                assertThat(values.get("WHRValue")).isEqualTo("");
                assertThat(values.get("WHRDate")).isEqualTo("");
                assertThat(values.get("WTDate")).isNotNull().isNotEqualTo("");
            }
            assertThat(request.getSession().getAttribute("EctSessionBean")).isSameAs(lastChart);
            assertThat(lastChart.getDemographicNo()).isEqualTo(other);
            verify(charts).getLatestChart(Integer.valueOf(patient));
            verify(demographics, never()).getDemographic(user, other);
            verify(security).hasPrivilege(user, "_demographic", "w", patient);
            verify(security).hasPrivilege(user, "_form", "r", patient);
            verify(measurements, never()).persist(any());
        }
    }

    @ParameterizedTest
    @CsvSource({"101,202,save,0", "202,101,save,0", "101,202,exit,0", "202,101,exit,0",
            "101,202,save,88", "202,101,save,88"})
    void shouldSaveOnlyRequestedPatient_whenSessionPointsAtAnotherChart(String patient, String other, String submit, String formId) throws Exception {
        request.setParameter("demographicNo", patient);
        request.setParameter("value(formId)", formId);
        request.setParameter("submit", submit);
        request.setParameter("provider_no", "forged");
        lastChart.setDemographicNo(other);
        ResultSet owned = ownedForm(patient);
        try (var servlet = servlet();
             var jdbc = mockStatic(LegacyJdbcQuery.class, CALLS_REAL_METHODS);
             var records = mockConstruction(FrmRecordHelp.class, (record, context) ->
                     when(record.saveFormRecord(any(), anyString(), any(Object[].class))).thenReturn(77))) {
            jdbc.when(() -> LegacyJdbcQuery.getPreparedResultSet(any(LegacyJdbcQuery.TrustedSql.class), eq(88), eq(Integer.parseInt(patient))))
                    .thenReturn(owned);
            assertThat(new FrmForm2Action().execute()).isEqualTo(ActionSupport.NONE);
            ArgumentCaptor<Properties> saved = ArgumentCaptor.forClass(Properties.class);
            verify(records.constructed().getFirst()).saveFormRecord(saved.capture(),
                    eq("SELECT * FROM formVTForm WHERE demographic_no=? AND ID=0"), eq(patient));
            assertThat(saved.getValue().getProperty("demographic_no")).isEqualTo(patient);
            assertThat(saved.getValue().getProperty("provider_no")).isEqualTo("999998");
            assertThat(saved.getValue().getProperty("WTValue")).isEqualTo("75");
            assertThat(saved.getValue().getProperty("givenName")).isEqualTo("Synthetic " + patient);
            assertThat(lastChart.getDemographicNo()).isEqualTo(other);
            assertThat(request.getSession().getAttribute("EctSessionBean")).isSameAs(lastChart);
            if ("exit".equals(submit)) {
                ArgumentCaptor<Measurement> measured = ArgumentCaptor.forClass(Measurement.class);
                verify(measurements).persist(measured.capture());
                assertThat(measured.getValue().getDemographicId()).isEqualTo(Integer.valueOf(patient));
                assertThat(response.getForwardedUrl()).isEqualTo("/WEB-INF/jsp/form/formSaveAndExit.jsp");
                assertThat(request.getAttribute("demographicNo")).isEqualTo(patient);
                assertThat(request.getAttribute("diagnosisVT")).isEqualTo("See Vascular Tracker Template");
            } else {
                verify(measurements, never()).persist(any());
                assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/form/SetupForm?formName=VTForm&formId=77&demographic_no=" + patient);
            }
            verify(demographics, never()).getDemographic(user, other);
            verify(security).hasPrivilege(user, "_form", "w", patient);
            verify(security).hasPrivilege(user, "_demographic", "w", patient);
        }
    }

    private static ResultSet ownedForm(String patient) throws SQLException {
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        String[] columns = {"ID", "demographic_no", "visitCod", "WTValue", "WTDate", "WTComments", "Subjective"};
        String[] values = {"88", patient, "2026-10-15", "72.5", "2026-10-15", "Owned <script> text", "Owned subjective"};
        when(rows.next()).thenReturn(true, false);
        when(rows.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(columns.length);
        for (int i = 0; i < columns.length; i++) {
            when(metadata.getColumnName(i + 1)).thenReturn(columns[i]);
            when(rows.getString(i + 1)).thenReturn(values[i]);
        }
        return rows;
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "exit"})
    void shouldIgnoreForgedHiddenHistory_beforeValidationAndMeasurementWrites(String submit) throws Exception {
        request.setParameter("demographic_no", "101");
        request.setParameter("submit", submit);
        for (String type : List.of("iEx", "WHR")) {
            request.setParameter("value(" + type + "Value)", "forged invalid clinical result");
            request.setParameter("value(" + type + "Date)", "not a date");
            request.setParameter("value(" + type + "Comments)", "forged comment");
        }
        try (var servlet = servlet(); var records = mockConstruction(FrmRecordHelp.class, (record, context) ->
                when(record.saveFormRecord(any(), anyString(), any(Object[].class))).thenReturn(77))) {
            FrmForm2Action action = new FrmForm2Action();
            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            // Validation errors remain observable even though this legacy action overwrites its
            // boolean valid flag later; checking only a successful response would miss this guard.
            assertThat(action.getActionErrors()).isEmpty();
            ArgumentCaptor<Properties> saved = ArgumentCaptor.forClass(Properties.class);
            verify(records.constructed().getFirst()).saveFormRecord(saved.capture(), anyString(), eq("101"));
            assertThat(saved.getValue()).doesNotContainKeys("iExValue", "iExDate", "iExComments",
                    "WHRValue", "WHRDate", "WHRComments");
            if ("exit".equals(submit)) {
                ArgumentCaptor<Measurement> captured = ArgumentCaptor.forClass(Measurement.class);
                verify(measurements).persist(captured.capture());
                assertThat(captured.getValue().getType()).isEqualTo("WT");
                verify(measurements).find(any(MeasurementDaoImpl.SearchCriteria.class));
                verifyNoMoreInteractions(measurements);
            } else {
                verifyNoInteractions(measurements);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "negative", "overflow", "conflicting aliases", "conflicting repeats"})
    void shouldRejectInvalidFormId_beforeAnyFormOrClinicalAccess(String scenario) throws Exception {
        request.setParameter("demographic_no", "101");
        switch (scenario) {
            case "invalid" -> request.setParameter("value(formId)", "bad");
            case "negative" -> request.setParameter("value(formId)", "-1");
            case "overflow" -> request.setParameter("value(formId)", "2147483648");
            case "conflicting aliases" -> request.setParameter("formId", "88");
            case "conflicting repeats" -> request.setParameter("value(formId)", "0", "88");
            default -> throw new IllegalArgumentException(scenario);
        }
        try (var servlet = servlet(); var records = mockConstruction(FrmRecordHelp.class)) {
            assertThat(new FrmSetupForm2Action().execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(400);
            response = new MockHttpServletResponse();
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            assertThat(new FrmForm2Action().execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(400);
            verifyNoInteractions(forms, measurements, demographics, charts, security);
            assertThat(records.constructed()).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "invalid", "conflicting aliases", "conflicting repeats", "overflow", "zero"})
    void shouldRejectAmbiguousPatient_beforeAnyFormOrClinicalAccess(String scenario) throws Exception {
        switch (scenario) {
            case "invalid" -> request.setParameter("demographic_no", "101x");
            case "conflicting aliases" -> {
                request.setParameter("demographic_no", "101");
                request.setParameter("demographicNo", "202");
            }
            case "conflicting repeats" -> request.setParameter("demographic_no", "101", "202");
            case "overflow" -> request.setParameter("demographic_no", "2147483648");
            case "zero" -> request.setParameter("demographic_no", "0");
            default -> { }
        }
        try (var servlet = servlet(); var records = mockConstruction(FrmRecordHelp.class)) {
            assertThat(new FrmSetupForm2Action().execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(400);
            response = new MockHttpServletResponse();
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            assertThat(new FrmForm2Action().execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(400);
            verifyNoInteractions(forms, measurements, demographics, charts, security);
            assertThat(records.constructed()).isEmpty();
            assertThat(lastChart.getDemographicNo()).isEqualTo("202");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRejectForeignOrMissingForm_beforePrefillOrSaving(boolean setup) throws Exception {
        request.setParameter("demographic_no", "101");
        request.setParameter("value(formId)", "88");
        ResultSet rows = mock(ResultSet.class);
        try (var servlet = servlet(); var jdbc = mockStatic(LegacyJdbcQuery.class, CALLS_REAL_METHODS);
             var records = mockConstruction(FrmRecordHelp.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getPreparedResultSet(any(LegacyJdbcQuery.TrustedSql.class), eq(88), eq(101)))
                    .thenReturn(rows);
            assertThat(setup ? new FrmSetupForm2Action().execute() : new FrmForm2Action().execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(400);
            jdbc.verify(() -> LegacyJdbcQuery.getPreparedResultSet(any(LegacyJdbcQuery.TrustedSql.class), eq(88), eq(101)));
            verifyNoInteractions(measurements, demographics, charts);
            assertThat(records.constructed()).isEmpty();
            assertThat(lastChart.getDemographicNo()).isEqualTo("202");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldDenyUnauthorizedPatient_beforeAnyFormOrClinicalAccess(boolean setup) throws Exception {
        request.setParameter("demographic_no", "101");
        when(security.hasPrivilege(eq(user), anyString(), anyString(), eq("101"))).thenReturn(false);
        try (var servlet = servlet(); var records = mockConstruction(FrmRecordHelp.class)) {
            assertThatThrownBy(() -> {
                if (setup) new FrmSetupForm2Action().execute(); else new FrmForm2Action().execute();
            }).isInstanceOf(SecurityException.class);
            verifyNoInteractions(forms, measurements, demographics, charts);
            assertThat(records.constructed()).isEmpty();
        }
    }

    @Test
    void shouldReturnControlledServerError_whenOwnedRecordLookupFails() throws Exception {
        request.setParameter("demographic_no", "101");
        request.setParameter("value(formId)", "88");
        try (var servlet = servlet(); var jdbc = mockStatic(LegacyJdbcQuery.class, CALLS_REAL_METHODS);
             var records = mockConstruction(FrmRecordHelp.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getPreparedResultSet(any(LegacyJdbcQuery.TrustedSql.class), eq(88), eq(101)))
                    .thenThrow(new SQLException("Synthetic lookup failure"));

            assertThat(new FrmSetupForm2Action().execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getErrorMessage()).isEqualTo("Unable to read form record");
            assertThat(response.getForwardedUrl()).isNull();
            assertThat(response.getRedirectedUrl()).isNull();
            assertThat(request.getAttribute("formPatient")).isNull();
            assertThat(request.getAttribute("formValues")).isNull();
            assertThat(records.constructed()).isEmpty();
            verifyNoInteractions(measurements, demographics, charts);
            assertThat(request.getSession().getAttribute("EctSessionBean")).isSameAs(lastChart);
            assertThat(lastChart.getDemographicNo()).isEqualTo("202");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"SetupForm.doUnexpected?formName=VTForm", "SetupFormOther?formName=VTForm", "Other?formName=VTForm"})
    void shouldRejectUnregisteredEndpoint_despiteMatchingFormTable(String route) throws Exception {
        request.setParameter("demographic_no", "101");
        EncounterForm registration = new EncounterForm();
        registration.setFormValue(route);
        when(forms.findByFormTable("formVTForm")).thenReturn(List.of(registration));
        try (var servlet = servlet()) {
            assertThat(new FrmSetupForm2Action().execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(400);
            response = new MockHttpServletResponse();
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            assertThat(new FrmForm2Action().execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(400);
            verifyNoInteractions(measurements, demographics, charts);
        }
    }

    @Test
    void shouldReportFailedFormInsert_withoutRedirectingToAnotherRecord() throws Exception {
        request.setParameter("demographic_no", "101");
        try (var servlet = servlet(); var records = mockConstruction(FrmRecordHelp.class, (record, context) ->
                when(record.saveFormRecord(any(), anyString(), any(Object[].class))).thenThrow(new SQLException("owned failure")))) {
            assertThat(new FrmForm2Action().execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getRedirectedUrl()).isNull();
            verify(measurements, never()).persist(any());
        }
    }
}
