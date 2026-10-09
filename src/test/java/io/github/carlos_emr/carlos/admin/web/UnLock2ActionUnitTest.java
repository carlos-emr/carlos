/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.admin.web;

import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.login.LoginCheckLogin;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("unit")
class UnLock2ActionUnitTest extends CarlosUnitTestBase {
    private SecurityInfoManager privileges;
    private SecurityDao accounts;
    private LoggedInInfo login;
    private MockHttpServletRequest request;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> loggedIn;
    private MockedConstruction<LoginCheckLogin> loginChecks;
    private final List<String> locks = new ArrayList<>();
    private UnLock2Action action;

    @BeforeEach
    void setUp() {
        privileges = mock(SecurityInfoManager.class);
        accounts = mock(SecurityDao.class);
        login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("900001");
        registerMock(SecurityInfoManager.class, privileges);
        registerMock(SecurityDao.class, accounts);
        request = new MockHttpServletRequest("POST", "/admin/UnLock");
        request.setRemoteAddr("192.0.2.1");
        request.setParameter("submit", "Unlock");
        request.setParameter("userName", "permitted");
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        loggedIn = mockStatic(LoggedInInfo.class);
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
        loginChecks = mockConstruction(LoginCheckLogin.class, (check, context) -> {
            when(check.findLockList()).thenAnswer(invocation -> new Vector<>(locks));
            when(check.unlock(anyString())).thenAnswer(invocation -> locks.remove(invocation.<String>getArgument(0)));
        });
        locks.addAll(List.of("denied-one", "denied-two", "permitted", "192.0.2.2"));
        Security permitted = new Security();
        permitted.setUserName("permitted");
        when(accounts.findByProviderSite("900001")).thenReturn(List.of(permitted));
        action = new UnLock2Action();
    }

    @AfterEach
    void tearDown() {
        loginChecks.close();
        loggedIn.close();
        servlet.close();
    }

    private void scopedAdmin() {
        when(privileges.hasPrivilege(login, "_admin.unlockAccount", "r", null)).thenReturn(true);
        when(privileges.hasPrivilege(login, "_site_access_privacy", "r", null)).thenReturn(true);
    }

    @Test
    void shouldRefuseBeforeReadingOrUnlocking_whenAdminPrivilegesAreMissing() {
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        verifyNoInteractions(accounts);
        assertThat(loginChecks.constructed()).isEmpty();
        logActionMock.verifyNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"_admin", "_admin.userAdmin", "_admin.unlockAccount"})
    void shouldListAccounts_whenAnExistingAdministrationPrivilegeIsGranted(String privilege) {
        when(privileges.hasPrivilege(login, privilege, "r", null)).thenReturn(true);
        request.setMethod("GET");
        assertThat(action.execute()).isEqualTo("success");
        assertThat(request.getAttribute("lockList")).isEqualTo(new Vector<>(locks));
        verifyNoInteractions(accounts);
        verify(loginChecks.constructed().get(0), never()).unlock(anyString());
        logActionMock.verifyNoInteractions();
    }

    @Test
    void shouldFilterEveryDeniedEntry_whenAdjacentAccountsAndAnAddressAreTracked() {
        scopedAdmin();
        request.setMethod("GET");
        assertThat(action.execute()).isEqualTo("success");
        assertThat(request.getAttribute("lockList")).isEqualTo(new Vector<>(List.of("permitted")));
        assertThat(locks).containsExactly("denied-one", "denied-two", "permitted", "192.0.2.2");
        logActionMock.verifyNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"denied-one", "denied-two", "192.0.2.2", "unknown"})
    void shouldRejectBeforeUnlockingOrAuditing_whenPostedAccountIsOutsideTheSites(String username) {
        scopedAdmin();
        request.setParameter("userName", username);
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        assertThat(loginChecks.constructed()).isEmpty();
        assertThat(locks).containsExactly("denied-one", "denied-two", "permitted", "192.0.2.2");
        logActionMock.verifyNoInteractions();
    }

    @Test
    void shouldUnlockAndAuditOnce_whenAnAllowedRequestIsReplayed() {
        scopedAdmin();
        assertThat(action.execute()).isEqualTo("success");
        assertThat(request.getAttribute("msg")).isEqualTo("Account unlocked: permitted");
        assertThat(request.getAttribute("lockList")).isEqualTo(new Vector<String>());
        assertThat(locks).containsExactly("denied-one", "denied-two", "192.0.2.2");
        assertThat(action.execute()).isEqualTo("success");
        assertThat(request.getAttribute("msg").toString()).contains("already been unlocked");
        logActionMock.verify(() -> LogAction.addLog("900001", "unlock", "adminUnlock", "permitted", "192.0.2.1"), times(1));
        logActionMock.verifyNoMoreInteractions();
    }

    @Test
    void shouldPreserveAddressUnlock_whenSitePrivacyIsAbsent() {
        when(privileges.hasPrivilege(login, "_admin", "r", null)).thenReturn(true);
        request.setParameter("userName", "192.0.2.2");
        assertThat(action.execute()).isEqualTo("success");
        assertThat(locks).containsExactly("denied-one", "denied-two", "permitted");
        verifyNoInteractions(accounts);
        logActionMock.verify(() -> LogAction.addLog("900001", "unlock", "adminUnlock", "192.0.2.2", "192.0.2.1"));
    }

    @Test
    void shouldLeaveLocksUntouched_whenSiteLookupFails() {
        scopedAdmin();
        when(accounts.findByProviderSite("900001")).thenThrow(new IllegalStateException("Owned lookup failure"));
        assertThatThrownBy(action::execute).isInstanceOf(IllegalStateException.class);
        assertThat(loginChecks.constructed()).isEmpty();
        logActionMock.verifyNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void shouldAvoidUnlocking_whenSelectionIsEmptyOrOutsideTheAllowedSet(String username) {
        scopedAdmin();
        request.setParameter("userName", username);
        if (username.isEmpty()) {
            assertThat(action.execute()).isEqualTo("success");
            verify(loginChecks.constructed().get(0), never()).unlock(anyString());
        } else {
            assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
            assertThat(loginChecks.constructed()).isEmpty();
        }
        logActionMock.verifyNoInteractions();
    }
}
