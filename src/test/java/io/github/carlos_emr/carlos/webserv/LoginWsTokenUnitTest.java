/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.List;
import io.github.carlos_emr.carlos.utility.NotAuthorisedException;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("security")
class LoginWsTokenUnitTest extends CarlosUnitTestBase {
    private Security account;
    private SoapTokenService tokens;
    private SecurityDao securityDao;
    private SecurityManager passwords;
    private LoginWs login;
    private ProviderDao providers;
    private final String loginName = java.util.UUID.randomUUID().toString();
    private final String loginPassword = java.util.UUID.randomUUID().toString();
    private static final String TOKEN = "ct1." + "B".repeat(43);

    @BeforeEach
    void setup() {
        tokens = mock(SoapTokenService.class);
        securityDao = mock(SecurityDao.class);
        passwords = mock(SecurityManager.class);
        providers = mock(ProviderDao.class);
        registerMock(ProviderDao.class, providers);
        registerMock(SoapTokenService.class, tokens);
        registerMock(SecurityManager.class, passwords);
        account = new Security();
        account.setId(41);
        account.setPassword(java.util.UUID.randomUUID().toString());
        account.setUserName(loginName);
        when(securityDao.findByUserName(loginName)).thenReturn(List.of(account));
        var request = new MockHttpServletRequest();
        login = new LoginWs() {
            @Override
            protected jakarta.servlet.http.HttpServletRequest getHttpServletRequest() { return request; }
        };
        injectDependency(login, "securityDao", securityDao);
        injectDependency(login, "providerDao", providers);
        when(tokens.issue(account)).thenReturn(TOKEN);
    }

    @Test
    @SuppressWarnings("deprecation")
    void shouldKeepBothLoginResponseShapes_whenThePasswordIsValid() throws Exception {
        when(passwords.validatePassword(loginPassword, account)).thenReturn(true);
        var old = login.login(loginName, loginPassword);
        assertThat(old.getSecurityId()).isEqualTo(41);
        assertThat(old.getSecurityTokenKey()).isEqualTo(TOKEN);
        var current = login.login2(loginName, loginPassword);
        assertThat(current.getSecurityId()).isEqualTo(41);
        assertThat(current.getSecurityTokenKey()).isEqualTo(TOKEN);
        assertThat(current.getProvider()).isNull();
        verify(tokens, times(2)).issue(account);
    }

    @Test
    void shouldReturnTheProviderBillingIdentifiers_onLoginWithoutBillingRead() throws Exception {
        // The registered policy denies every privilege; a login response never depended on _billing.
        var policy = createAndRegisterMock(io.github.carlos_emr.carlos.managers.SecurityInfoManager.class);
        var provider = new io.github.carlos_emr.carlos.commn.model.Provider();
        provider.setProviderNo("41"); provider.setLastName("Synthetic");
        provider.setBillingNo("FAKE-BILLING"); provider.setOhipNo("FAKE-OHIP");
        provider.setRmaNo("FAKE-RMA"); provider.setHsoNo("FAKE-HSO");
        account.setProviderNo("41");
        when(providers.getProvider("41")).thenReturn(provider);
        when(passwords.validatePassword(loginPassword, account)).thenReturn(true);
        var result = login.login2(loginName, loginPassword);
        assertThat(result.getProvider()).extracting("providerNo", "billingNo", "ohipNo", "rmaNo", "hsoNo")
                .containsExactly("41", "FAKE-BILLING", "FAKE-OHIP", "FAKE-RMA", "FAKE-HSO");
        verifyNoInteractions(policy);
    }

    @Test
    void shouldRefreshUsingACurrentToken_withoutRequiringThePassword() throws Exception {
        String existing = "ct1." + "A".repeat(43);
        when(tokens.validate(account, existing)).thenReturn(true);
        assertThat(login.login2(loginName, existing).getSecurityTokenKey()).isEqualTo(TOKEN);
        verifyNoInteractions(passwords);
        verify(tokens).issue(account);
    }

    @Test
    void shouldRejectMissingOrInvalidCredentials_withoutIssuingAToken() {
        assertThatThrownBy(() -> login.login2(loginName, null)).isInstanceOf(NotAuthorisedException.class);
        assertThatThrownBy(() -> login.login2(loginName, "1234567890123")).isInstanceOf(NotAuthorisedException.class);
        verify(tokens, never()).issue(any());
    }
}
