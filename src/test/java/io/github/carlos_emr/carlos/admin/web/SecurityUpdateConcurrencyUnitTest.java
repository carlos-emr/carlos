/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.admin.web;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.MfaManager;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.security.CarlosMethodSecurity;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import java.util.Date;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class SecurityUpdateConcurrencyUnitTest extends CarlosUnitTestBase {
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LogAction> audit;
    private MockedStatic<MfaManager> mfa;
    private MockedStatic<CarlosProperties> configuration;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private CarlosMethodSecurity access;
    private SecurityDao dao;
    private SecurityManager passwords;
    private CarlosProperties properties;
    private Security row;
    private boolean committed;

    @BeforeEach
    void setup() {
        request = new MockHttpServletRequest("POST", "/admin/SecurityUpdate");
        response = new MockHttpServletResponse();
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        audit = mockStatic(LogAction.class);
        mfa = mockStatic(MfaManager.class);
        mfa.when(MfaManager::isOscarLegacyPinEnabled).thenReturn(true);
        configuration = mockStatic(CarlosProperties.class);
        properties = mock(CarlosProperties.class);
        lenient().when(properties.getProperty(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        configuration.when(CarlosProperties::getInstance).thenReturn(properties);
        dao = createAndRegisterMock(SecurityDao.class);
        passwords = createAndRegisterMock(SecurityManager.class);
        access = mock(CarlosMethodSecurity.class);
        when(access.hasAdminWrite()).thenReturn(true);
        registerMock(PlatformTransactionManager.class, new AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object value, TransactionDefinition definition) {
                assertThat(definition.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            }
            @Override protected void doCommit(DefaultTransactionStatus status) { committed = true; }
            @Override protected void doRollback(DefaultTransactionStatus status) { }
        });
        row = new Security(); row.setId(17); row.setUserName("ownedlogin"); row.setProviderNo("999998");
        row.setPassword("stored-password-hash"); row.setPin("stored-pin-hash");
        row.setBExpireset(0); row.setDateExpiredate(java.sql.Date.valueOf("2100-01-01"));
        row.setBLocallockset(1); row.setBRemotelockset(1); row.setForcePasswordReset(false);
        row.setLastUpdateDate(new Date(1000));
        when(dao.findForUpdate(17)).thenReturn(row);
        request.getSession().setAttribute("user", "999998");
        request.setParameter("security_no", "17"); request.setParameter("user_name", "ownedlogin");
        request.setParameter("provider_no", "999998"); request.setParameter("password", "*********");
        request.setParameter("conPassword", "*********"); request.setParameter("pin", "****");
        request.setParameter("conPin", "****"); request.setParameter("date_ExpireDate", "2100-01-01");
        request.setParameter("b_LocalLockSet", "1"); request.setParameter("b_RemoteLockSet", "1");
        request.setParameter("forcePasswordReset", "0");
        request.setParameter(SecurityEditVersion.PARAMETER, SecurityEditVersion.of(row));
    }

    @AfterEach
    void closeStatics() { configuration.close(); mfa.close(); audit.close(); servlet.close(); }

    @ParameterizedTest
    @ValueSource(strings = {"reset", "password", "pin", "mfa", "mfaSecret", "provider", "expiry", "remotePin", "username"})
    void shouldRetainDraftWithoutWrites_whenOriginalStateChanged(String field) throws Exception {
        switch (field) {
            case "reset" -> row.setForcePasswordReset(true);
            case "password" -> row.setPassword("different-hash");
            case "pin" -> row.setPin("different-pin");
            case "mfa" -> row.setUsingMfa(true);
            case "mfaSecret" -> row.setMfaSecret("different-secret");
            case "provider" -> row.setProviderNo("222222");
            case "expiry" -> row.setBExpireset(1);
            case "remotePin" -> row.setBRemotelockset(0);
            case "username" -> row.setUserName("anotherlogin");
        }
        String current = SecurityEditVersion.of(row);
        String original = request.getParameter(SecurityEditVersion.PARAMETER);
        request.setParameter("user_name", "mydraft"); request.setParameter("b_ExpireSet", "1");
        assertThat(new SecurityUpdate2Action(access).execute()).isEqualTo("conflict");
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        Security draft = (Security) request.getAttribute("securityEditDraft");
        assertThat(draft.getUserName()).isEqualTo("mydraft"); assertThat(draft.getBExpireset()).isEqualTo(1);
        assertThat(request.getParameter(SecurityEditVersion.PARAMETER)).isEqualTo(original);
        assertThat(SecurityEditVersion.of(row)).isEqualTo(current);
        verify(dao, never()).saveEntity(any()); verifyNoInteractions(passwords); audit.verifyNoInteractions();
        assertThat(committed).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"malformed", "0"})
    void shouldFailClosed_whenOriginalVersionMissingOrMalformed(String value) throws Exception {
        if (value == null) request.removeParameter(SecurityEditVersion.PARAMETER);
        else request.setParameter(SecurityEditVersion.PARAMETER, value);
        assertThat(new SecurityUpdate2Action(access).execute()).isEqualTo("conflict");
        assertThat(response.getStatus()).isEqualTo(409);
        verify(dao, never()).saveEntity(any()); audit.verifyNoInteractions();
    }

    @Test
    void shouldRetainDraftWithoutRecoveryLink_whenDeleted() throws Exception {
        when(dao.findForUpdate(17)).thenReturn(null);
        assertThat(new SecurityUpdate2Action(access).execute()).isEqualTo("conflict");
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(request.getAttribute("securityReviewAvailable")).isEqualTo(false);
        assertThat(request.getAttribute("securityEditDraft")).isInstanceOf(Security.class);
        verify(dao, never()).saveEntity(any());
    }

    @Test
    void shouldConfirmCommitAndKeepCredentials_whenOnlyExpiryChanges() throws Exception {
        request.setParameter("b_ExpireSet", "1");
        assertThat(new SecurityUpdate2Action(access).execute()).isEqualTo("success");
        assertThat(committed).isTrue();
        assertThat(request.getAttribute("securityUpdateCommitted")).isEqualTo(true);
        assertThat(row.getBExpireset()).isEqualTo(1);
        assertThat(row.getPassword()).isEqualTo("stored-password-hash");
        assertThat(row.getPin()).isEqualTo("stored-pin-hash");
        verifyNoInteractions(passwords); verify(dao).saveEntity(row);
    }

    @Test
    void shouldKeepHiddenProtection_whenControlsAreOmittedByPolicy() throws Exception {
        row.setForcePasswordReset(true); row.setUsingMfa(true);
        request.setParameter(SecurityEditVersion.PARAMETER, SecurityEditVersion.of(row));
        request.removeParameter("forcePasswordReset"); request.removeParameter("pin"); request.removeParameter("conPin");
        request.removeParameter("b_LocalLockSet"); request.removeParameter("b_RemoteLockSet");
        assertThat(new SecurityUpdate2Action(access).execute()).isEqualTo("success");
        assertThat(row.isForcePasswordReset()).isTrue(); assertThat(row.isUsingMfa()).isTrue();
        assertThat(row.getBLocallockset()).isEqualTo(1); assertThat(row.getBRemotelockset()).isEqualTo(1);
        assertThat(row.getPin()).isEqualTo("stored-pin-hash");
    }

    @Test
    void shouldRejectInvalidPasswordBeforeAnyFieldChanges() throws Exception {
        String before = SecurityEditVersion.of(row);
        request.setParameter("user_name", "renamed"); request.setParameter("password", "changed");
        assertThat(new SecurityUpdate2Action(access).execute()).isEqualTo("conflict");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(SecurityEditVersion.of(row)).isEqualTo(before);
        verify(dao, never()).saveEntity(any()); verifyNoInteractions(passwords);
    }

    @Test
    void shouldIgnoreResetControl_whenMandatoryPolicyHidesIt() throws Exception {
        row.setForcePasswordReset(true);
        request.setParameter(SecurityEditVersion.PARAMETER, SecurityEditVersion.of(row));
        when(properties.getBooleanProperty("mandatory_password_reset", "false")).thenReturn(true);
        // A direct POST must not bypass the policy that hides this control in the form.
        request.setParameter("forcePasswordReset", "0");
        assertThat(new SecurityUpdate2Action(access).execute()).isEqualTo("success");
        assertThat(row.isForcePasswordReset()).isTrue();
    }
}
