/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.pageUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("form")
class FormExists2ActionUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<EctFormData> data;
    private SecurityInfoManager security;
    private LoggedInInfo login;
    private EncounterForm form;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/encounter/formExists");
        request.setContextPath("/clinic");
        request.setParameter("demographicNo", "123");
        request.setParameter("formname", "Caregiver");
        request.setParameter("appointmentNo", "11");
        response = new MockHttpServletResponse();
        login = mock(LoggedInInfo.class);
        request.getSession().setAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY", login);
        security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(login, "_form", "r", "123")).thenReturn(true);
        registerMock(SecurityInfoManager.class, security);
        var dao = mock(EncounterFormDao.class);
        registerMock(EncounterFormDao.class, dao);
        form = new EncounterForm();
        form.setFormName("Caregiver");
        form.setFormTable("formCaregiver");
        form.setDisplayOrder(1);
        when(dao.findByFormName("Caregiver")).thenReturn(List.of(form));
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        data = mockStatic(EctFormData.class);
    }

    @AfterEach
    void tearDown() {
        if (data != null) data.close();
        if (servlet != null) servlet.close();
    }

    @Test
    void shouldReturnLatestShortcut_forClickedChart() throws Exception {
        var saved = new EctFormData.PatientForm("Caregiver", 7, 123,
                Timestamp.valueOf("2026-10-01 12:00:00"), Timestamp.valueOf("2026-10-01 12:00:00"));
        data.when(() -> EctFormData.getShortcutLatestPatientForm("123", "formCaregiver")).thenReturn(saved);
        assertThat(new FormExists2Action().execute()).isEqualTo("none");
        var json = new ObjectMapper().readTree(response.getContentAsString());
        assertThat(json.path("exists").asBoolean()).isTrue();
        assertThat(json.path("url").asText()).isEqualTo("/clinic/form/forwardshortcutname?formname=Caregiver&demographic_no=123&formId=latest&appointmentNo=11");
        assertThat(json.path("windowName").asText()).isEqualTo(Math.abs(("Caregiver123" + saved.getEdited()).hashCode()) + "started");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        verify(security).hasPrivilege(login, "_form", "r", "123");
    }

    @Test
    void shouldReturnFalse_whenNoSavedFormExists() throws Exception {
        new FormExists2Action().execute();
        assertThat(response.getContentAsString()).isEqualTo("{\"exists\":false}");
    }

    @Test
    void shouldSkipLookup_forSnapshotOrHiddenForms() throws Exception {
        for (String table : List.of("formAnnual", "formCaregiver")) {
            form.setFormTable(table);
            form.setDisplayOrder(table.equals("formCaregiver") ? 0 : 1);
            new FormExists2Action().execute();
        }
        data.verifyNoInteractions();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "-1", "123x", " 123", "1000000000"})
    void shouldRejectInvalidPatient_beforeLookup(String patient) throws Exception {
        if (patient == null) request.removeParameter("demographicNo");
        else request.setParameter("demographicNo", patient);
        new FormExists2Action().execute();
        assertThat(response.getStatus()).isEqualTo(400);
        data.verifyNoInteractions();
    }

    @Test
    void shouldRejectInvalidAppointment_beforeLookup() throws Exception {
        request.setParameter("appointmentNo", "1&other=2");
        new FormExists2Action().execute();
        assertThat(response.getStatus()).isEqualTo(400);
        data.verifyNoInteractions();
    }

    @Test
    void shouldDenyLookup_withoutFormPrivilege() {
        when(security.hasPrivilege(login, "_form", "r", "123")).thenReturn(false);
        assertThatThrownBy(() -> new FormExists2Action().execute()).isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_form)");
        data.verifyNoInteractions();
    }

    @Test
    void shouldRejectPost_forReadOnlyEndpoint() throws Exception {
        request.setMethod("POST");
        new FormExists2Action().execute();
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("GET");
    }
}
