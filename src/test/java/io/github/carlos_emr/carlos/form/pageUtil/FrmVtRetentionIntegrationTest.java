/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.form.pageUtil;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Actual action, XML rules, owned JDBC read, and FrmRecordHelp inserts against isolated H2.
 * Authentication/demographics and the measurement DAO are mocked: this proves real form-row
 * retention plus absence of measurement DAO calls, not a Hibernate/MariaDB integration run.
 */
@Tag("integration")
@Tag("controller")
@DisplayName("VT hidden history retention through actual form persistence")
class FrmVtRetentionIntegrationTest {
    private static final List<String> RETAINED = List.of("iExValue", "iExDate", "iExComments",
            "WHRValue", "WHRDate", "WHRComments");
    private Connection keeper;
    private JdbcTemplate jdbc;
    private MockedStatic<SpringUtils> spring;
    private MockedStatic<CarlosProperties> properties;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MeasurementDao measurements;

    @BeforeEach
    void prepareOwnedDatabase() throws SQLException {
        var source = new DriverManagerDataSource("jdbc:h2:mem:vt_retention_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_UPPER=false", "sa", "");
        keeper = source.getConnection();
        jdbc = new JdbcTemplate(source);
        String columns = " (ID INT AUTO_INCREMENT PRIMARY KEY, demographic_no INT NOT NULL, "
                + "iExValue VARCHAR(10), iExDate VARCHAR(10), iExComments VARCHAR(255), "
                + "WHRValue VARCHAR(10), WHRDate VARCHAR(10), WHRComments VARCHAR(255), "
                + "WTValue VARCHAR(30), WTDate VARCHAR(10), WTComments VARCHAR(255))";
        jdbc.execute("CREATE TABLE formVTForm" + columns);
        jdbc.execute("CREATE TABLE formOtherForm" + columns);
        jdbc.update("INSERT INTO formVTForm(ID,demographic_no,WTValue) VALUES(88,101,'62.3'),(99,202,'88.5')");

        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), anyString(), anyString(), eq("101"))).thenReturn(true);
        var forms = mock(EncounterFormDao.class);
        for (String name : List.of("VTForm", "OtherForm")) {
            var registration = new EncounterForm();
            registration.setFormTable("form" + name);
            registration.setFormValue("../form/SetupForm.do?formName=" + name + "&demographic_no=");
            when(forms.findByFormTable("form" + name)).thenReturn(List.of(registration));
        }
        var demographics = mock(DemographicManager.class);
        var patient = new Demographic();
        patient.setDemographicNo(101);
        patient.setFirstName("Synthetic");
        patient.setLastName("VT retention");
        patient.setSex("F");
        patient.setYearOfBirth("1980");
        patient.setMonthOfBirth("01");
        patient.setDateOfBirth("02");
        when(demographics.getDemographic(any(), eq("101"))).thenReturn(patient);
        measurements = mock(MeasurementDao.class);
        spring = mockStatic(SpringUtils.class);
        spring.when(() -> SpringUtils.getBean(DataSource.class)).thenReturn(source);
        spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
        spring.when(() -> SpringUtils.getBean(EncounterFormDao.class)).thenReturn(forms);
        spring.when(() -> SpringUtils.getBean(DemographicManager.class)).thenReturn(demographics);
        spring.when(() -> SpringUtils.getBean(MeasurementDao.class)).thenReturn(measurements);
        // The save runs the form insert and the measurement writes in one transaction (#4674).
        spring.when(() -> SpringUtils.getBean(PlatformTransactionManager.class))
                .thenReturn(new DataSourceTransactionManager(source));
        var config = mock(CarlosProperties.class);
        when(config.getProperty("db_type", "")).thenReturn("mysql");
        when(config.getProperty("save_as_xml", "false")).thenReturn("false");
        properties = mockStatic(CarlosProperties.class);
        properties.when(CarlosProperties::getInstance).thenReturn(config);

        request = new MockHttpServletRequest(new MockServletContext() {
            @Override public InputStream getResourceAsStream(String path) {
                assertThat(path).isIn("/form/VTForm.xml", "/form/OtherForm.xml");
                // OtherForm deliberately uses the same rules: retention must depend on form
                // identity, not merely a measurement type present in its definition.
                try {
                    return Files.newInputStream(Path.of("src/main/webapp/form/VTForm.xml"));
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
            }
        });
        response = new MockHttpServletResponse();
        var login = new LoggedInInfo();
        var provider = new Provider();
        provider.setProviderNo("999998");
        login.setLoggedInProvider(provider);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        request.setParameter("demographic_no", "101");
        request.setParameter("value(formName)", "VTForm");
        request.setParameter("value(formId)", "88");
        request.setParameter("value(visitCod)", "2026-10-15");
        for (String name : List.of("diagnosisVT", "subjective", "objective", "assessment", "plan")) {
            request.setParameter("value(" + name + ")", "");
        }
        for (String field : RETAINED) request.setParameter("value(" + field + ")", "FORGED invalid value");
    }

    @AfterEach
    void closeOwnedDatabase() throws SQLException {
        if (properties != null) properties.close();
        if (spring != null) spring.close();
        if (keeper != null) keeper.close(); // Last connection closes and destroys this test's DB.
    }

    @ParameterizedTest
    @CsvSource({"save,dated", "exit,dated", "save,nullable", "exit,nullable", "save,blank", "exit,blank"})
    void shouldRetainExactOwnedHistory_whenHiddenSubmissionIsForged(String submit, String dateKind) throws Exception {
        String date = switch (dateKind) {
            case "dated" -> "2019-06-07";
            case "blank" -> "";
            default -> null;
        };
        jdbc.update("UPDATE formVTForm SET iExValue=?,iExDate=?,iExComments=?,WHRValue=?,WHRDate=?,WHRComments=? WHERE ID=88",
                " yes ", date, "  <b>literal eye</b>\n ", "0.8750", "2020-01-02", " \t ratio & history  ");
        Map<String, Object> original = row(88);
        Map<String, Object> other = row(99);
        execute(submit);
        Map<String, Object> saved = row(0);
        for (String field : RETAINED) assertThat(saved.get(field)).as(field).isEqualTo(original.get(field));
        assertThat(row(88)).isEqualTo(original);
        assertThat(row(99)).isEqualTo(other);
        assertThat(saved.get("demographic_no")).isEqualTo(101);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM formVTForm", Integer.class)).isEqualTo(3);
        verifyNoInteractions(measurements);
    }

    @ParameterizedTest
    @CsvSource({"0,save", "0,exit", "88,save", "88,exit"})
    void shouldKeepAbsentHistoryNull_whenNewOrExistingFormSubmitsForgedValues(String formId, String submit) throws Exception {
        request.setParameter("value(formId)", formId);
        execute(submit);
        Map<String, Object> saved = row(0);
        for (String field : RETAINED) assertThat(saved.get(field)).as(field).isNull();
        verifyNoInteractions(measurements);
    }

    @ParameterizedTest
    @ValueSource(strings = {"VTForm", "OtherForm"})
    void shouldSaveEditableMeasurements_whenTypesAreNotRetentionOnly(String formName) throws Exception {
        request.setParameter("value(formName)", formName);
        request.setParameter("value(formId)", "0");
        request.setParameter("value(WTValue)", "75");
        request.setParameter("value(WTDate)", "2026-10-15");
        if ("OtherForm".equals(formName)) {
            request.setParameter("value(iExValue)", "yes");
            request.setParameter("value(iExDate)", "2018-03-04");
            request.setParameter("value(WHRValue)", "0.91");
            request.setParameter("value(WHRDate)", "2017-01-02");
        }
        execute("exit");
        ArgumentCaptor<Measurement> written = ArgumentCaptor.forClass(Measurement.class);
        if ("VTForm".equals(formName)) {
            verify(measurements).persist(written.capture());
            assertThat(written.getValue().getType()).isEqualTo("WT");
            assertThat(written.getValue().getDataField()).isEqualTo("75");
        } else {
            verify(measurements, times(3)).persist(written.capture());
            assertThat(written.getAllValues()).extracting(Measurement::getType).containsExactlyInAnyOrder("WT", "iEx", "WHR");
            Map<String, Object> saved = jdbc.queryForMap("SELECT * FROM formOtherForm WHERE ID=0");
            assertThat(saved).containsEntry("iExValue", "yes").containsEntry("iExDate", "2018-03-04")
                    .containsEntry("WHRValue", "0.91").containsEntry("WHRDate", "2017-01-02");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "exit"})
    void shouldRejectForeignRecord_beforeRetentionOrAnyInsert(String submit) throws Exception {
        request.setParameter("value(formId)", "99");
        request.setParameter("submit", submit);
        List<Map<String, Object>> before = jdbc.queryForList("SELECT * FROM formVTForm ORDER BY ID");
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            assertThat(new FrmForm2Action().execute()).isEqualTo(ActionSupport.NONE);
        }
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(jdbc.queryForList("SELECT * FROM formVTForm ORDER BY ID")).isEqualTo(before);
        verifyNoInteractions(measurements);
    }

    private Map<String, Object> row(int id) {
        // H2 keeps an explicitly inserted identity 0; MySQL allocates an ID. We assert the
        // actual inserted row here, without pretending this verifies MariaDB ID allocation.
        return jdbc.queryForMap("SELECT * FROM formVTForm WHERE ID=?", id);
    }

    private void execute(String submit) throws Exception {
        request.setParameter("submit", submit);
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            FrmForm2Action action = new FrmForm2Action();
            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(action.getActionErrors()).isEmpty();
        }
        assertThat(response.getStatus()).isEqualTo("exit".equals(submit) ? 200 : 302);
    }
}
