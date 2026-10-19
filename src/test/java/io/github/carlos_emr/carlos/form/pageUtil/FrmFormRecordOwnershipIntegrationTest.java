/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.form.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean;
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
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real bound ownership queries; clinical prefill and persistence must remain unreachable on rejection. */
@Tag("integration")
@Tag("form")
@DisplayName("Form record ownership against a real isolated database")
public class FrmFormRecordOwnershipIntegrationTest {
    private Connection keeper;
    private JdbcTemplate jdbc;
    private MockedStatic<SpringUtils> spring;
    private SecurityInfoManager security;
    private EncounterFormDao forms;

    @BeforeEach
    void createOwnedDatabase() throws SQLException {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:form4556_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false", "sa", "");
        // The database disappears when this final connection closes; no shared fixtures or files.
        keeper = source.getConnection();
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE formVTForm (ID INT PRIMARY KEY, demographic_no INT NOT NULL, WTValue VARCHAR(30), WTComments VARCHAR(100))");
        jdbc.update("INSERT INTO formVTForm VALUES (?, ?, ?, ?)", 88, 101, "62.3", "Synthetic A <text>");
        jdbc.update("INSERT INTO formVTForm VALUES (?, ?, ?, ?)", 99, 202, "88.5", "Synthetic B <text>");
        security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), anyString(), anyString(), anyString())).thenReturn(true);
        forms = mock(EncounterFormDao.class);
        EncounterForm registration = new EncounterForm();
        registration.setFormTable("formVTForm");
        registration.setFormValue("../form/SetupForm.do?formName=VTForm&demographic_no=");
        when(forms.findByFormTable("formVTForm")).thenReturn(List.of(registration));
        spring = mockStatic(SpringUtils.class);
        spring.when(() -> SpringUtils.getBean(DataSource.class)).thenReturn(source);
        spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
        spring.when(() -> SpringUtils.getBean(EncounterFormDao.class)).thenReturn(forms);
    }

    @AfterEach
    void closeOwnedDatabase() throws SQLException {
        if (spring != null) spring.close();
        if (keeper != null) keeper.close();
    }

    @ParameterizedTest
    @CsvSource({"101,88,62.3,Synthetic A <text>", "202,99,88.5,Synthetic B <text>"})
    void shouldReadOnlyTheOwnedRecord_whenPatientAndFormMatch(String patient, String id, String value, String comments) throws SQLException {
        var row = FrmSetupForm2Action.loadFormRecord("VTForm", id, patient);
        assertThat(row.getProperty("ID")).isEqualTo(id);
        assertThat(row.getProperty("demographic_no")).isEqualTo(patient);
        assertThat(row.getProperty("WTValue")).isEqualTo(value);
        assertThat(row.getProperty("WTComments")).isEqualTo(comments);
    }

    @ParameterizedTest
    @CsvSource({"true,99", "false,99", "true,999", "false,999"})
    void shouldRejectBeforeAnyClinicalWrite_whenFormIsForeignOrAbsent(boolean setup, String formId) throws Exception {
        List<Map<String, Object>> before = jdbc.queryForList("SELECT * FROM formVTForm ORDER BY ID");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("demographic_no", "101");
        request.setParameter("formName", "VTForm");
        request.setParameter("value(formName)", "VTForm");
        request.setParameter("value(formId)", formId);
        request.setParameter("submit", "exit");
        LoggedInInfo login = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        EctSessionBean otherChart = new EctSessionBean();
        otherChart.setDemographicNo("202");
        request.getSession().setAttribute("EctSessionBean", otherChart);
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            assertThat(setup ? new FrmSetupForm2Action().execute() : new FrmForm2Action().execute())
                    .isEqualTo(ActionSupport.NONE);
        }
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getForwardedUrl()).isNull();
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(jdbc.queryForList("SELECT * FROM formVTForm ORDER BY ID")).isEqualTo(before);
        assertThat(request.getSession().getAttribute("EctSessionBean")).isSameAs(otherChart);
        assertThat(otherChart.getDemographicNo()).isEqualTo("202");
        // No clinical bean is made available: the rejected actions must not request one.
        spring.verify(() -> SpringUtils.getBean(io.github.carlos_emr.carlos.commn.dao.MeasurementDao.class), never());
        spring.verify(() -> SpringUtils.getBean(io.github.carlos_emr.carlos.managers.DemographicManager.class), never());
    }
}
