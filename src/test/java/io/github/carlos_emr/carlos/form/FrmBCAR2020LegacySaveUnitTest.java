/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form;

import java.sql.SQLException;
import java.util.Properties;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.EpisodeDao;
import io.github.carlos_emr.carlos.commn.dao.forms.FormBCAR2020Dao;
import io.github.carlos_emr.carlos.commn.dao.forms.FormBCAR2020DataDao;
import io.github.carlos_emr.carlos.commn.dao.forms.FormBCAR2020TextDao;
import io.github.carlos_emr.carlos.commn.web.Pregnancy2Action;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Exercises the real retired record through both overloads and its two remaining action callers (#5836). */
@Tag("unit")
@Tag("form")
@Tag("security")
@SuppressWarnings("deprecation")
class FrmBCAR2020LegacySaveUnitTest extends CarlosUnitTestBase {
    private static final String CLINICAL_FIELD = "FAKE-private-field-5836";
    private static final String CLINICAL_VALUE = "FAKE-private-value-5836";
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final LoggedInInfo actor = mock(LoggedInInfo.class);
    private final FormBCAR2020Dao forms = mock(FormBCAR2020Dao.class);
    private final FormBCAR2020DataDao data = mock(FormBCAR2020DataDao.class);
    private final FormBCAR2020TextDao text = mock(FormBCAR2020TextDao.class);
    private MockedStatic<ServletActionContext> servletContext;
    private MockedStatic<LoggedInInfo> loggedInInfo;

    @BeforeEach
    void prepare() {
        registerMock(DemographicManager.class, mock(DemographicManager.class));
        registerMock(DemographicExtDao.class, mock(DemographicExtDao.class));
        registerMock(EpisodeDao.class, mock(EpisodeDao.class));
        registerMock(FormBCAR2020Dao.class, forms);
        registerMock(FormBCAR2020DataDao.class, data);
        registerMock(FormBCAR2020TextDao.class, text);
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, security);
        when(security.hasPrivilege(eq(actor), anyString(), anyString(), isNull())).thenReturn(true);
        when(actor.getLoggedInProviderNo()).thenReturn("999998");
        request.setMethod("POST");
        request.getSession().setAttribute("user", "999998");
        request.setParameter("form_class", "BCAR2020");
        request.setParameter("demographic_no", "42");
        request.setParameter("form_link", "formbcar2020.jsp");
        request.setParameter(CLINICAL_FIELD, CLINICAL_VALUE);
        servletContext = mockStatic(ServletActionContext.class);
        servletContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletContext.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfo = mockStatic(LoggedInInfo.class);
        loggedInInfo.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(actor);
    }

    @AfterEach
    void releaseContexts() {
        loggedInInfo.close();
        servletContext.close();
    }

    @ParameterizedTest
    @CsvSource({"false,0", "false,7", "false,''", "false,not-a-number",
            "true,0", "true,7", "true,''", "true,not-a-number"})
    void shouldRefuseRetiredSave_regardlessOfPostedIdOrActorOverload(boolean withActor, String formId) {
        Properties props = postedProperties(formId);
        FrmBCAR2020Record record = new FrmBCAR2020Record();
        assertThatThrownBy(() -> {
            if (withActor) record.saveFormRecord(actor, props);
            else record.saveFormRecord(props);
        }).isInstanceOf(SQLException.class).hasMessageContaining("BCAR 2020");
        verifyNoInteractions(forms, data, text);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldNeverLogSubmittedClinicalFields_whenTheRetiredSaveIsAttempted(boolean withActor) throws Exception {
        FrmBCAR2020Record record = new FrmBCAR2020Record();
        try (LogCapture capture = LogCapture.forLogger(FrmBCAR2020Record.class)) {
            try {
                if (withActor) record.saveFormRecord(actor, postedProperties("7"));
                else record.saveFormRecord(postedProperties("7"));
            } catch (SQLException expectedRefusal) {
                assertThat(expectedRefusal.getMessage()).doesNotContain(CLINICAL_FIELD, CLINICAL_VALUE);
            }
            assertThat(capture.messages()).noneMatch(message -> message.contains(CLINICAL_FIELD) || message.contains(CLINICAL_VALUE));
        }
        verifyNoInteractions(forms, data, text);
    }

    @ParameterizedTest
    @CsvSource({"save,0", "save,7", "update,0", "update,7", "autosaveAjax,0", "autosaveAjax,7"})
    void shouldNotAcknowledgeOrAuditASave_throughTheGenericFormAction(String operation, String formId) {
        request.setParameter("formId", formId);
        request.setParameter("submit", "update".equals(operation) ? "save" : operation);
        if ("update".equals(operation)) request.setParameter("update", "true");
        assertThat(new Frm2Action().execute()).isEqualTo("failure");
        assertThat(request.getAttribute("saveSuccess")).isEqualTo(false);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getRedirectedUrl()).isNull();
        logActionMock.verifyNoInteractions();
        verifyNoInteractions(forms, data, text);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "7"})
    void shouldReturnErrorWithoutASuccessAudit_throughThePregnancyAction(String formId) throws Exception {
        request.setParameter("formId", formId);
        assertThat(new Pregnancy2Action().saveFormAjax()).isNull();
        assertThat(new ObjectMapper().readTree(response.getContentAsString()).get("value").asText()).isEqualTo("error");
        assertThat(response.getContentType()).startsWith("application/json");
        logActionMock.verifyNoInteractions();
        verifyNoInteractions(forms, data, text);
    }

    @ParameterizedTest
    @CsvSource({"t_patientSurname,true", "d_confirmedEDD,true", "t_comments,false"})
    void shouldRetainSharedAndPageSpecificFieldClassification_forSupportedFormReads(String field, boolean global) {
        assertThat(new FrmBCAR2020Record().isGlobalField(field)).isEqualTo(global);
    }

    private static Properties postedProperties(String formId) {
        Properties props = new Properties();
        props.setProperty("formId", formId);
        props.setProperty(CLINICAL_FIELD, CLINICAL_VALUE);
        return props;
    }
}
