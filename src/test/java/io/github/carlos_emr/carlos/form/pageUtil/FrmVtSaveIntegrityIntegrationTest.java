/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.form.pageUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

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
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The Vascular Tracker save (issue #4674) through the real action, its XML rules and the real form-row insert
 * against an isolated H2 database, inside a real JDBC transaction.
 *
 * <ul>
 *   <li>Validation accumulates: an invalid value anywhere, or an invalid visit or observation date, refuses the save
 *       with no form row and no measurement written, and the refused form is shown again with its errors and the
 *       values that were typed.</li>
 *   <li>Save &amp; Exit writes the form row and the measurements in one transaction: a failure of the form insert or
 *       of a later measurement write leaves neither.</li>
 * </ul>
 *
 * <p>The measurement DAO is a stand-in that writes its row with JDBC through the same Spring-managed connection, the
 * way the JPA DAO joins the production JpaTransactionManager's transaction, so a rollback is observed in the database
 * rather than inferred from mock calls. Authentication and the patient lookup are mocked.</p>
 *
 * @since 2026-10-22
 */
@Tag("integration")
@Tag("controller")
@DisplayName("Vascular Tracker save: validation and atomic persistence")
class FrmVtSaveIntegrityIntegrationTest {

    private Connection keeper;
    private JdbcTemplate jdbc;
    private MockedStatic<SpringUtils> spring;
    private MockedStatic<CarlosProperties> properties;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MeasurementDao measurements;
    /** Fails the measurement write with this 1-based number; 0 never fails. */
    private final AtomicInteger failMeasurementWrite = new AtomicInteger();
    private final AtomicInteger measurementWrites = new AtomicInteger();

    @BeforeEach
    void prepareOwnedDatabase() throws SQLException {
        var source = new DriverManagerDataSource("jdbc:h2:mem:vt_save_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_UPPER=false", "sa", "");
        keeper = source.getConnection();
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE formVTForm (ID INT AUTO_INCREMENT PRIMARY KEY, demographic_no INT NOT NULL, "
                + "provider_no VARCHAR(10), visitCod VARCHAR(10), "
                + "BPValue VARCHAR(10), BPDate VARCHAR(10), BPComments VARCHAR(255), "
                + "WTValue VARCHAR(10), WTDate VARCHAR(10), WTComments VARCHAR(255), "
                + "HTValue VARCHAR(10), HTDate VARCHAR(10), HTComments VARCHAR(255))");
        jdbc.execute("CREATE TABLE measurements (id INT AUTO_INCREMENT PRIMARY KEY, demographicNo INT, "
                + "type VARCHAR(10), dataField VARCHAR(30))");

        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), anyString(), anyString(), eq("101"))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(), eq(101))).thenReturn(true);
        var forms = mock(EncounterFormDao.class);
        var registration = new EncounterForm();
        registration.setFormTable("formVTForm");
        registration.setFormValue("../form/SetupForm.do?formName=VTForm&demographic_no=");
        when(forms.findByFormTable("formVTForm")).thenReturn(List.of(registration));
        var demographics = mock(DemographicManager.class);
        var patient = new Demographic();
        patient.setDemographicNo(101);
        patient.setFirstName("Synthetic");
        patient.setLastName("VT save");
        patient.setSex("F");
        patient.setYearOfBirth("1980");
        patient.setMonthOfBirth("01");
        patient.setDateOfBirth("02");
        when(demographics.getDemographic(any(), eq("101"))).thenReturn(patient);

        // find(criteria), the duplicate check, answers the mock's empty list: nothing is stored yet.
        measurements = mock(MeasurementDao.class);
        doAnswer(invocation -> {
            Measurement measurement = invocation.getArgument(0);
            if (measurementWrites.incrementAndGet() == failMeasurementWrite.get()) {
                throw new IllegalStateException("synthetic measurement write failure");
            }
            // JdbcTemplate takes its connection from DataSourceUtils, so it joins the save's transaction.
            jdbc.update("INSERT INTO measurements(demographicNo,type,dataField) VALUES(?,?,?)",
                    measurement.getDemographicId(), measurement.getType(), measurement.getDataField());
            return null;
        }).when(measurements).persist(any(Measurement.class));

        spring = mockStatic(SpringUtils.class);
        spring.when(() -> SpringUtils.getBean(DataSource.class)).thenReturn(source);
        spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
        spring.when(() -> SpringUtils.getBean(EncounterFormDao.class)).thenReturn(forms);
        spring.when(() -> SpringUtils.getBean(DemographicManager.class)).thenReturn(demographics);
        spring.when(() -> SpringUtils.getBean(MeasurementDao.class)).thenReturn(measurements);
        spring.when(() -> SpringUtils.getBean(PlatformTransactionManager.class))
                .thenReturn(new DataSourceTransactionManager(source));
        var config = mock(CarlosProperties.class);
        when(config.getProperty("db_type", "")).thenReturn("mysql");
        when(config.getProperty("save_as_xml", "false")).thenReturn("false");
        properties = mockStatic(CarlosProperties.class);
        properties.when(CarlosProperties::getInstance).thenReturn(config);

        request = new MockHttpServletRequest(new MockServletContext() {
            @Override
            public InputStream getResourceAsStream(String path) {
                assertThat(path).isEqualTo("/form/VTForm.xml");
                try {
                    return Files.newInputStream(Path.of("src/main/webapp/form/VTForm.xml"));
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
            }
        });
        response = new MockHttpServletResponse();
        request.setMethod("POST");
        var login = new LoggedInInfo();
        var provider = new Provider();
        provider.setProviderNo("999998");
        login.setLoggedInProvider(provider);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        request.setParameter("demographic_no", "101");
        request.setParameter("value(formName)", "VTForm");
        request.setParameter("value(formId)", "0");
        request.setParameter("value(visitCod)", "2026-10-15");
        for (String name : List.of("diagnosisVT", "subjective", "objective", "assessment", "plan")) {
            request.setParameter("value(" + name + ")", "");
        }
    }

    @AfterEach
    void closeOwnedDatabase() throws SQLException {
        if (properties != null) properties.close();
        if (spring != null) spring.close();
        if (keeper != null) keeper.close(); // Last connection closes and destroys this test's DB.
    }

    /** The action under test, with the redisplay and message lookups (which need a running Struts container) stubbed. */
    private FrmForm2Action action() throws Exception {
        FrmForm2Action action = spy(new FrmForm2Action());
        doAnswer(invocation -> invocation.getArgument(0) + ":" + String.join("|", (String[]) invocation.getArgument(1)))
                .when(action).getText(anyString(), any(String[].class));
        doAnswer(invocation -> invocation.getArgument(0)).when(action).getText(anyString());
        doReturn(ActionSupport.NONE).when(action).redisplayInvalid(anyString(), anyString(), anyString(), any());
        return action;
    }

    private FrmForm2Action submit(String submit) throws Exception {
        request.setParameter("submit", submit);
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            FrmForm2Action action = action();
            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            return action;
        }
    }

    private int formRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM formVTForm", Integer.class);
    }

    private int measurementRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM measurements", Integer.class);
    }

    @ParameterizedTest
    @CsvSource({"GET,save", "HEAD,save", "PUT,save", "GET,exit", "HEAD,exit", "PUT,exit"})
    void shouldKeepRealClinicalTablesUnchanged_whenAValidSubmissionUsesAnotherHttpMethod(String method, String submit)
            throws Exception {
        request.setMethod(method);
        request.setParameter("value(BPValue)", "120/80");
        request.setParameter("value(WTValue)", "75");
        request.setParameter("value(WTDate)", "2026-10-15");
        List<Map<String, Object>> formsBefore = jdbc.queryForList("SELECT * FROM formVTForm ORDER BY ID");
        List<Map<String, Object>> measurementsBefore = jdbc.queryForList("SELECT * FROM measurements ORDER BY id");

        submit(submit);

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        assertThat(jdbc.queryForList("SELECT * FROM formVTForm ORDER BY ID")).isEqualTo(formsBefore);
        assertThat(jdbc.queryForList("SELECT * FROM measurements ORDER BY id")).isEqualTo(measurementsBefore);
        verifyNoInteractions(measurements);
        assertThat(response.getForwardedUrl()).isNull();
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> redisplayedValues(FrmForm2Action action) throws Exception {
        ArgumentCaptor<Map<String, String>> values = ArgumentCaptor.forClass(Map.class);
        verify(action).redisplayInvalid(eq("VTForm"), eq("0"), eq("101"), values.capture());
        return values.getValue();
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({
            "save,invalid early measurement before valid later ones",
            "exit,invalid early measurement before valid later ones",
            "save,invalid last measurement",
            "exit,invalid last measurement",
            "save,invalid visit date",
            "exit,invalid visit date",
            "save,invalid observation date",
            "exit,invalid observation date",
    })
    @DisplayName("should refuse the whole save and keep the typed values when any value is invalid")
    void shouldRefuseWholeSave_whenAnyValueIsInvalid(String submit, String scenario) throws Exception {
        // BP is the definition's first measurement and WT a later one; the visit date is checked after the loop.
        request.setParameter("value(BPValue)", "120/80");
        request.setParameter("value(WTValue)", "75");
        request.setParameter("value(WTDate)", "2026-10-15");
        request.setParameter("value(WTComments)", "FAKE typed comment");
        switch (scenario) {
            case "invalid early measurement before valid later ones" -> request.setParameter("value(BPValue)", "120-80");
            // Hypercholesterolemia is the definition's last measurement, a Yes/No answer.
            case "invalid last measurement" -> request.setParameter("value(HchlValue)", "maybe");
            case "invalid visit date" -> request.setParameter("value(visitCod)", "2026-13-45");
            case "invalid observation date" -> request.setParameter("value(WTDate)", "15/10/2026");
            default -> throw new IllegalArgumentException(scenario);
        }

        FrmForm2Action action = submit(submit);

        assertThat(formRows()).as("form rows").isZero();
        assertThat(measurementRows()).as("measurement rows").isZero();
        verify(measurements, never()).persist(any(Measurement.class));
        assertThat(action.getActionErrors()).isNotEmpty();
        assertThat(String.join(" ", action.getActionErrors())).doesNotContain("<li>");
        Map<String, String> shown = redisplayedValues(action);
        assertThat(shown).containsEntry("BPValue", request.getParameter("value(BPValue)"))
                .containsEntry("WTValue", request.getParameter("value(WTValue)"))
                .containsEntry("WTDate", request.getParameter("value(WTDate)"))
                .containsEntry("WTComments", "FAKE typed comment")
                .containsEntry("visitCod", request.getParameter("value(visitCod)"));
        // The VT history carriers have no editor; a refused form shows the stored record's, not a forged one.
        assertThat(shown).doesNotContainKeys("iExValue", "WHRValue");
    }

    @Test
    @DisplayName("should name the measurement and its range in a refused value's message")
    void shouldNameMeasurementAndRange_whenValueIsOutOfRange() throws Exception {
        request.setParameter("value(WTValue)", "999");

        FrmForm2Action action = submit("save");

        assertThat(action.getActionErrors()).contains("errors.range:Weight|0|300");
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "exit"})
    @DisplayName("should save the form, and on Save & Exit its measurements, when every value is valid")
    void shouldSaveFormAndMeasurements_whenEveryValueIsValid(String submit) throws Exception {
        request.setParameter("value(BPValue)", "120/80");
        request.setParameter("value(WTValue)", "75");
        request.setParameter("value(WTDate)", "2026-10-15");

        FrmForm2Action action = submit(submit);

        assertThat(action.getActionErrors()).isEmpty();
        assertThat(formRows()).isEqualTo(1);
        assertThat(measurementRows()).isEqualTo("exit".equals(submit) ? 2 : 0);
        assertThat(response.getStatus()).isEqualTo("exit".equals(submit) ? 200 : 302);
    }

    @Test
    @DisplayName("should write no measurement when the form row cannot be inserted")
    void shouldWriteNoMeasurement_whenFormInsertFails() throws Exception {
        request.setParameter("value(BPValue)", "120/80");
        request.setParameter("value(WTValue)", "75");
        // A value longer than its column makes the form insert fail after validation passed.
        request.setParameter("value(WTComments)", "x".repeat(300));

        submit("exit");

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getErrorMessage()).contains("No measurements were saved");
        assertThat(formRows()).isZero();
        assertThat(measurementRows()).isZero();
    }

    @Test
    @DisplayName("should roll back the form row and earlier measurements when a later measurement write fails")
    void shouldRollBackEverything_whenLaterMeasurementWriteFails() throws Exception {
        request.setParameter("value(BPValue)", "120/80");
        request.setParameter("value(WTValue)", "75");
        request.setParameter("value(HTValue)", "170");
        failMeasurementWrite.set(2);

        submit("exit");

        assertThat(measurementWrites.get()).isEqualTo(2);
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(formRows()).as("form rows after rollback").isZero();
        assertThat(measurementRows()).as("measurement rows after rollback").isZero();

        // A retry of the same submission then saves once, with no duplicates left by the failed attempt.
        failMeasurementWrite.set(0);
        measurementWrites.set(0);
        response = new MockHttpServletResponse();
        submit("exit");
        assertThat(formRows()).isEqualTo(1);
        assertThat(measurementRows()).isEqualTo(3);
    }

    @Test
    @DisplayName("should drop the list markup the shared bundle wraps messages in")
    void shouldDropListMarkup_fromBundleMessage() {
        assertThat(FrmForm2Action.withoutListMarkup("<li>Weight is not in the range 0 through 300</li>"))
                .isEqualTo("Weight is not in the range 0 through 300");
        assertThat(FrmForm2Action.withoutListMarkup(" plain ")).isEqualTo("plain");
        assertThat(FrmForm2Action.withoutListMarkup(null)).isEmpty();
    }
}
