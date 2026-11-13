/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.admin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.admin.service.SecurityRecordUpdateService;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.security.CarlosMethodSecurity;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.www.admin.SecurityEditVersion;
import io.github.carlos_emr.carlos.www.admin.SecurityUpdatePasswordValidator;

/**
 * Administration, Security Records, Update Record: the save route with the real
 * {@link SecurityRecordUpdateService} over a mocked DAO. Administrator A's form, opened before
 * administrator B forced a password reset, must not put the reset back (ledger finding 355).
 */
@Tag("unit")
@Tag("security")
@Tag("admin")
@DisplayName("SecurityUpdate2Action")
class SecurityUpdate2ActionUnitTest extends CarlosUnitTestBase {

    /** A new password as an administrator would type it; built at run time so no credential-like literal is committed. */
    private static final String TYPED_PASSWORD = "FAKE-" + java.util.UUID.randomUUID();

    private static final String UNCHANGED = SecurityUpdatePasswordValidator.UNCHANGED_PASSWORD;

    private final SecurityDao securityDao = mock(SecurityDao.class);
    private final SecurityManager securityManager = mock(SecurityManager.class);
    private final CarlosMethodSecurity methodSecurity = mock(CarlosMethodSecurity.class);
    private MockedStatic<ServletActionContext> servletActionContext;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private Security login;

    @BeforeEach
    void setUp() {
        when(methodSecurity.hasAdminWrite()).thenReturn(true);
        login = new Security();
        login.setSecurityNo(41);
        login.setUserName("pwlogin");
        login.setProviderNo("700123");
        login.setBExpireset(0);
        login.setDateExpiredate(java.sql.Date.valueOf("2100-01-01"));
        login.setBLocallockset(1);
        login.setBRemotelockset(1);
        login.setForcePasswordReset(Boolean.FALSE);
        login.setUsingMfa(false);
        login.setLastUpdateDate(new Date(1_700_000_000_000L));
        login.setPassword("stored-password-hash");
        login.setPin("stored-pin");
        when(securityDao.findForUpdate(41)).thenReturn(login);
        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenAnswer(invocation -> request);
        servletActionContext.when(ServletActionContext::getResponse).thenAnswer(invocation -> response);
        renderAndPost(SecurityEditVersion.of(login));
    }

    @AfterEach
    void tearDown() {
        servletActionContext.close();
    }

    /** A fresh POST of the edit form as rendered with {@code version}; the expiry flag is ticked. */
    private void renderAndPost(String version) {
        request = new MockHttpServletRequest("POST", "/carlos/admin/SecurityUpdate");
        request.setRemoteAddr("192.0.2.10");
        request.getSession().setAttribute("user", "999998");
        request.addParameter("security_no", "41");
        request.addParameter("user_name", "pwlogin");
        request.addParameter("password", UNCHANGED);
        request.addParameter("conPassword", UNCHANGED);
        request.addParameter("provider_no", "700123");
        request.addParameter("b_ExpireSet", "1");
        request.addParameter("date_ExpireDate", "2100-01-01");
        request.addParameter("lockFlagsEditable", "1");
        request.addParameter("b_LocalLockSet", "1");
        request.addParameter("b_RemoteLockSet", "1");
        request.addParameter("pin", "****");
        request.addParameter("conPin", "****");
        request.addParameter("forcePasswordReset", "0");
        if (version != null) {
            request.addParameter(SecurityEditVersion.PARAMETER, version);
        }
        response = new MockHttpServletResponse();
    }

    private String execute() throws Exception {
        return new SecurityUpdate2Action(methodSecurity, new SecurityRecordUpdateService(securityDao, securityManager)).execute();
    }

    @Test
    void shouldRefuseWith409AndWriteNothing_whenAnotherAdministratorSavedAfterTheFormWasOpened() throws Exception {
        String rendered = SecurityEditVersion.of(login);
        // Administrator B forces a password reset and saves (the row lastUpdateDate moves).
        login.setForcePasswordReset(Boolean.TRUE);
        login.setLastUpdateDate(new Date(1_700_000_007_000L));
        // Administrator A, on the page opened before, posts its own change with the old version,
        // a new PIN included.
        renderAndPost(rendered);
        request.setParameter("pin", "4321");
        request.setParameter("conPin", "4321");

        String result = execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_CONFLICT);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(request.getAttribute(SecurityUpdate2Action.OUTCOME_ATTRIBUTE)).isEqualTo(SecurityUpdate2Action.OUTCOME_EDIT_CONFLICT);
        assertThat(request.getAttribute(SecurityUpdate2Action.CONFLICT_NO_ATTRIBUTE)).isEqualTo("41");
        assertThat(login.isForcePasswordReset()).as("B's Force password reset survives").isTrue();
        assertThat(login.getBExpireset()).as("A's expiry flag is not stored").isZero();
        assertThat(login.getPin()).as("A's PIN is not stored").isEqualTo("stored-pin");
        verify(securityDao, never()).saveEntity(any());
        logActionMock.verify(() -> LogAction.addLog(anyString(), anyString(), anyString(), anyString(), anyString()), never());
    }

    @Test
    void shouldSaveAndAudit_whenTheFormVersionIsCurrent() throws Exception {
        String result = execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(request.getAttribute(SecurityUpdate2Action.OUTCOME_ATTRIBUTE)).isEqualTo(SecurityUpdate2Action.OUTCOME_SAVED);
        assertThat(login.getBExpireset()).isEqualTo(1);
        verify(securityDao).saveEntity(login);
        logActionMock.verify(() -> LogAction.addLog("999998", LogConst.UPDATE, LogConst.CON_SECURITY, "41->pwlogin", "192.0.2.10"));
    }

    @Test
    void shouldSaveAsBefore_whenThePostCarriesNoVersion() throws Exception {
        login.setForcePasswordReset(Boolean.TRUE);
        renderAndPost(null);

        String result = execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(request.getAttribute(SecurityUpdate2Action.OUTCOME_ATTRIBUTE)).isEqualTo(SecurityUpdate2Action.OUTCOME_SAVED);
        assertThat(login.getBExpireset()).isEqualTo(1);
        assertThat(login.isForcePasswordReset()).isFalse();
        verify(securityDao).saveEntity(login);
    }

    @Test
    void shouldReportTheFirstSave_whenTheSameFormIsPostedTwice() throws Exception {
        String rendered = SecurityEditVersion.of(login);
        assertThat(execute()).isEqualTo(ActionSupport.SUCCESS);

        // The second click of the same form: its version predates its own first save.
        renderAndPost(rendered);
        String result = execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(request.getAttribute(SecurityUpdate2Action.OUTCOME_ATTRIBUTE)).isEqualTo(SecurityUpdate2Action.OUTCOME_SAVED);
        verify(securityDao, times(1)).saveEntity(login);
        logActionMock.verify(() -> LogAction.addLog(anyString(), anyString(), anyString(), anyString(), anyString()), times(1));
    }

    @Test
    void shouldRefuseBeforeTouchingTheLogin_whenThePasswordIsNotConfirmed() throws Exception {
        request.setParameter("password", TYPED_PASSWORD);
        request.setParameter("conPassword", "different");

        String result = execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute(SecurityUpdate2Action.OUTCOME_ATTRIBUTE)).isEqualTo(SecurityUpdate2Action.OUTCOME_PASSWORD_INVALID);
        assertThat(request.getAttribute(SecurityUpdate2Action.PASSWORD_ERROR_ATTRIBUTE)).isEqualTo("admin.securityrecord.msgPasswordNotConfirmed");
        verifyNoInteractions(securityDao, securityManager);
    }

    @Test
    void shouldRefuseBeforeTouchingTheLogin_whenTheUserNameIsInvalid() throws Exception {
        request.setParameter("user_name", "not valid!");

        execute();

        assertThat(request.getAttribute(SecurityUpdate2Action.OUTCOME_ATTRIBUTE)).isEqualTo(SecurityUpdate2Action.OUTCOME_USER_NAME_INVALID);
        verifyNoInteractions(securityDao, securityManager);
    }

    @Test
    void shouldReportFailure_whenTheLoginNumberIsMissingOrUnknown() throws Exception {
        request.removeParameter("security_no");
        execute();
        assertThat(request.getAttribute(SecurityUpdate2Action.OUTCOME_ATTRIBUTE)).isEqualTo(SecurityUpdate2Action.OUTCOME_FAILED);

        renderAndPost(null);
        request.setParameter("security_no", "77");
        execute();
        assertThat(request.getAttribute(SecurityUpdate2Action.OUTCOME_ATTRIBUTE)).isEqualTo(SecurityUpdate2Action.OUTCOME_FAILED);
        verify(securityDao, never()).saveEntity(any());
    }
}
