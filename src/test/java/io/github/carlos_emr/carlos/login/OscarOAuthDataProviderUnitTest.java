/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.carlos.commn.dao.ServiceAccessTokenDao;
import io.github.carlos_emr.carlos.commn.model.ServiceAccessToken;
import io.github.carlos_emr.carlos.webserv.oauth.Client;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1Exception;
import io.github.carlos_emr.carlos.webserv.oauth.RequestTokenRegistration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@DisplayName("OscarOAuthDataProvider access token expiry")
@Tag("unit")
@Tag("security")
class OscarOAuthDataProviderUnitTest {

    @Test
    @DisplayName("should reject expired access token during secret lookup")
    void shouldRejectExpiredAccessToken_whenSecretLookupRequested() {
        ServiceAccessTokenDao accessTokenDao = mock(ServiceAccessTokenDao.class);
        ServiceAccessToken token = accessToken("expired-token", "secret", "999998",
                (System.currentTimeMillis() / 1000) - 7200, 3600);
        when(accessTokenDao.findByTokenId("expired-token")).thenReturn(token);
        OscarOAuthDataProvider provider = provider(accessTokenDao);

        String secret = provider.getAccessTokenSecret("expired-token");

        assertThat(secret).isNull();
        verify(accessTokenDao).remove(token);
    }

    @Test
    @DisplayName("should return provider number when access token is current")
    void shouldReturnProviderNo_whenAccessTokenIsCurrent() {
        ServiceAccessTokenDao accessTokenDao = mock(ServiceAccessTokenDao.class);
        ServiceAccessToken token = accessToken("current-token", "secret", "999998",
                System.currentTimeMillis() / 1000, 3600);
        when(accessTokenDao.findByTokenId("current-token")).thenReturn(token);
        OscarOAuthDataProvider provider = provider(accessTokenDao);

        String providerNo = provider.getProviderNoByAccessToken("current-token");

        assertThat(providerNo).isEqualTo("999998");
        verify(accessTokenDao).findByTokenId("current-token");
    }

    @Test
    @DisplayName("should not remove access token when lookup misses")
    void shouldNotRemoveAccessToken_whenLookupMisses() {
        ServiceAccessTokenDao accessTokenDao = mock(ServiceAccessTokenDao.class);
        OscarOAuthDataProvider provider = provider(accessTokenDao);

        assertThat(provider.getAccessTokenSecret("missing-token")).isNull();

        verify(accessTokenDao).findByTokenId("missing-token");
        verifyNoMoreInteractions(accessTokenDao);
    }

    @Test
    @DisplayName("should reject request token callback outside registered callback")
    void shouldRejectRequestTokenCallback_whenOutsideRegisteredCallback() {
        OscarOAuthDataProvider provider = new OscarOAuthDataProvider();
        RequestTokenRegistration registration = registration("https://trusted.example/callback",
                "https://attacker.example/callback");

        assertThatThrownBy(() -> provider.createRequestToken(registration))
                .isInstanceOf(OAuth1Exception.class)
                .hasMessage("callback_uri not allowed");
    }

    @Test
    @DisplayName("should reject request token callback when registered callback is OOB")
    void shouldRejectRequestTokenCallback_whenRegisteredCallbackIsOob() {
        OscarOAuthDataProvider provider = new OscarOAuthDataProvider();
        RequestTokenRegistration registration = registration("oob", "https://trusted.example/callback");

        assertThatThrownBy(() -> provider.createRequestToken(registration))
                .isInstanceOf(OAuth1Exception.class)
                .hasMessage("callback_uri not allowed");
    }

    @Test
    @DisplayName("should reject request token callback when registered callback is missing")
    void shouldRejectRequestTokenCallback_whenRegisteredCallbackIsMissing() {
        OscarOAuthDataProvider provider = new OscarOAuthDataProvider();
        RequestTokenRegistration registration = registration(null, "https://trusted.example/callback");

        assertThatThrownBy(() -> provider.createRequestToken(registration))
                .isInstanceOf(OAuth1Exception.class)
                .hasMessage("callback_uri not allowed");
    }

    @Test
    @DisplayName("should reject request token callback when path only shares prefix")
    void shouldRejectRequestTokenCallback_whenPathOnlySharesPrefix() {
        OscarOAuthDataProvider provider = new OscarOAuthDataProvider();
        RequestTokenRegistration registration = registration("https://trusted.example/app",
                "https://trusted.example/application");

        assertThatThrownBy(() -> provider.createRequestToken(registration))
                .isInstanceOf(OAuth1Exception.class)
                .hasMessage("callback_uri not allowed");
    }

    private static OscarOAuthDataProvider provider(ServiceAccessTokenDao accessTokenDao) {
        OscarOAuthDataProvider provider = new OscarOAuthDataProvider();
        ReflectionTestUtils.setField(provider, "serviceAccessTokenDao", accessTokenDao);
        return provider;
    }

    private static ServiceAccessToken accessToken(String tokenId, String secret, String providerNo,
                                                  long issued, long lifetime) {
        ServiceAccessToken token = new ServiceAccessToken();
        token.setTokenId(tokenId);
        token.setTokenSecret(secret);
        token.setProviderNo(providerNo);
        token.setIssued(issued);
        token.setLifetime(lifetime);
        return token;
    }

    private static RequestTokenRegistration registration(String registeredCallback, String requestedCallback) {
        Client client = new Client("consumer", "secret", "App", "https://trusted.example");
        client.setCallbackUri(registeredCallback);
        RequestTokenRegistration registration = new RequestTokenRegistration(client);
        registration.setCallback(requestedCallback);
        return registration;
    }
}
