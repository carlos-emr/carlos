/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.admin.web;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.login.LoginCheckLogin;
import io.github.carlos_emr.carlos.login.LoginList;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.HashMap;
import java.util.Map;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Integrates the administrative action with the real application-wide login registry (#5208). */
@Isolated
@Tag("integration")
class UnLock2ActionLockListIntegrationTest extends CarlosUnitTestBase {
    private static final String ADDRESS = "192.0.2.208";
    private static final String OTHER_ADDRESS = "192.0.2.209";
    private final Map<String, String> previousProperties = new HashMap<>();

    @BeforeEach
    void configureAddressLocking() {
        CarlosProperties properties = CarlosProperties.getInstance();
        for (String key : new String[]{"login_lock", "login_max_failed_times", "login_max_duration", "login_local_ip"}) {
            previousProperties.put(key, properties.getProperty(key));
        }
        properties.setProperty("login_max_failed_times", "3");
        properties.setProperty("login_max_duration", "10");
        properties.setProperty("login_local_ip", "10.255.");
        removeOwnedEntries();
    }

    @AfterEach
    void restoreState() {
        previousProperties.forEach((key, value) -> {
            if (value == null) CarlosProperties.getInstance().remove(key);
            else CarlosProperties.getInstance().setProperty(key, value);
        });
        removeOwnedEntries();
    }

    private static void removeOwnedEntries() {
        LoginList.getLoginListInstance().remove(ADDRESS);
        LoginList.getLoginListInstance().remove(OTHER_ADDRESS);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"false", " false "})
    void shouldListAndUnlockAnAddressAlreadyTrackedBeforeLockout(String mode) {
        if (mode == null) CarlosProperties.getInstance().remove("login_lock");
        else CarlosProperties.getInstance().setProperty("login_lock", mode);
        SecurityInfoManager privileges = mock(SecurityInfoManager.class);
        SecurityDao accounts = mock(SecurityDao.class);
        LoggedInInfo login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("900001");
        when(privileges.hasPrivilege(login, "_admin.unlockAccount", "r", null)).thenReturn(true);
        when(privileges.hasPrivilege(login, "_site_access_privacy", "r", null)).thenReturn(true);
        registerMock(SecurityInfoManager.class, privileges);
        registerMock(SecurityDao.class, accounts);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/UnLock");
        request.setRemoteAddr(ADDRESS);

        try (MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class);
                MockedStatic<LoggedInInfo> loggedIn = mockStatic(LoggedInInfo.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
            UnLock2Action action = new UnLock2Action();
            LoginCheckLogin check = new LoginCheckLogin();
            check.updateLoginList(ADDRESS, "issue5208");
            assertThat(check.isBlock(ADDRESS, "issue5208")).isFalse();
            assertThat(action.execute()).isEqualTo("success");
            // findLockList includes failed attempts BEFORE the address becomes blocked.
            assertThat(request.getAttribute("lockList")).asList().contains(ADDRESS);

            check.updateLoginList(ADDRESS, "issue5208");
            check.updateLoginList(ADDRESS, "issue5208");
            check.updateLoginList(OTHER_ADDRESS, "issue5208other");
            assertThat(new LoginCheckLogin().isBlock(ADDRESS, "issue5208")).isTrue();
            assertThat(action.execute()).isEqualTo("success");
            assertThat(request.getAttribute("lockList")).asList().contains(ADDRESS, OTHER_ADDRESS);

            request.setMethod("POST");
            request.setParameter("submit", "Unlock");
            request.setParameter("userName", ADDRESS);
            assertThat(action.execute()).isEqualTo("success");
            assertThat(request.getAttribute("msg")).isEqualTo("Account unlocked: " + ADDRESS);
            assertThat(new LoginCheckLogin().isBlock(ADDRESS, "issue5208")).isFalse();
            assertThat(request.getAttribute("lockList")).asList().contains(OTHER_ADDRESS).doesNotContain(ADDRESS);
            assertThat(action.execute()).isEqualTo("success");
            logActionMock.verify(() -> LogAction.addLog("900001", "unlock", "adminUnlock", ADDRESS, ADDRESS));
            logActionMock.verifyNoMoreInteractions();
            verifyNoInteractions(accounts);
        }
    }
}
