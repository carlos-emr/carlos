/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("security")
class WsUtilsAuthenticationUnitTest extends CarlosUnitTestBase {
    private Security account;
    private SoapTokenService tokens;
    private SecurityManager passwords;
    private MockHttpServletRequest request;
    private static final String TOKEN = "ct1." + "A".repeat(43);

    @BeforeEach
    void setup() {
        registerMock(ProviderDao.class, mock(ProviderDao.class));
        tokens = mock(SoapTokenService.class);
        passwords = mock(SecurityManager.class);
        registerMock(SoapTokenService.class, tokens);
        registerMock(SecurityManager.class, passwords);
        account = new Security();
        account.setId(41);
        account.setPassword("FAKE-hash");
        request = new MockHttpServletRequest();
    }

    @Test
    void shouldEstablishTheSoapPrincipal_withAPersistedTokenWithoutIssuingAnother() {
        when(tokens.validate(account, TOKEN)).thenReturn(true);
        assertThat(WsUtils.checkAuthenticationAndSetLoggedInInfo(request, account, TOKEN)).isTrue();
        assertThat(LoggedInInfo.getLoggedInInfoFromRequest(request).getLoggedInSecurity()).isSameAs(account);
        verifyNoInteractions(passwords);
        verify(tokens, never()).issue(any());
    }

    @Test
    void shouldRetainPasswordAuthentication_withoutRequiringTokenStorage() {
        when(passwords.validatePassword("FAKE-password", account)).thenReturn(true);
        assertThat(WsUtils.checkAuthenticationAndSetLoggedInInfo(request, account, "FAKE-password")).isTrue();
        verifyNoInteractions(tokens);
    }

    @Test
    void shouldRejectHistoricalDeterministicTokens_withoutRegeneratingOrAcceptingThem() {
        assertThat(WsUtils.checkAuthenticationAndSetLoggedInInfo(request, account, "123456789012345")).isFalse();
        assertThat(LoggedInInfo.getLoggedInInfoFromRequest(request)).isNull();
        verifyNoInteractions(tokens);
        verify(passwords).validatePassword("123456789012345", account);
    }

    @Test
    void shouldRejectNullCredentialsAndAccounts_withoutCallingEitherVerifier() {
        assertThat(WsUtils.checkAuthenticationAndSetLoggedInInfo(request, account, null)).isFalse();
        assertThat(WsUtils.checkAuthenticationAndSetLoggedInInfo(request, null, TOKEN)).isFalse();
        verifyNoInteractions(tokens, passwords);
    }

    @Test
    void shouldPropagateTokenStorageFailures_withoutSilentlyFallingBackToPasswordAuthentication() {
        when(tokens.validate(account, TOKEN)).thenThrow(new IllegalStateException("FAKE-database-unavailable"));
        assertThatThrownBy(() -> WsUtils.checkAuthenticationAndSetLoggedInInfo(request, account, TOKEN))
                .isInstanceOf(IllegalStateException.class).hasMessage("FAKE-database-unavailable");
        verifyNoInteractions(passwords);
        assertThat(LoggedInInfo.getLoggedInInfoFromRequest(request)).isNull();
    }

    @Test
    void shouldDelegateIssuance_toTheTransactionalTokenService() {
        when(tokens.issue(account)).thenReturn(TOKEN);
        assertThat(WsUtils.generateSecurityToken(account)).isEqualTo(TOKEN);
        verify(tokens).issue(account);
    }
}
