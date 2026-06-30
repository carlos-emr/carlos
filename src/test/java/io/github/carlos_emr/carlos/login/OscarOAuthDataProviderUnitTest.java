/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.carlos.commn.dao.ServiceAccessTokenDao;
import io.github.carlos_emr.carlos.commn.dao.ServiceClientDao;
import io.github.carlos_emr.carlos.commn.dao.ServiceOAuthNonceDao;
import io.github.carlos_emr.carlos.commn.dao.ServiceRequestTokenDao;
import io.github.carlos_emr.carlos.commn.model.ServiceAccessToken;
import io.github.carlos_emr.carlos.commn.model.ServiceClient;
import io.github.carlos_emr.carlos.commn.model.ServiceOAuthNonce;
import io.github.carlos_emr.carlos.webserv.oauth.AccessToken;
import io.github.carlos_emr.carlos.webserv.oauth.Client;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1Exception;
import io.github.carlos_emr.carlos.webserv.oauth.RequestToken;
import io.github.carlos_emr.carlos.webserv.oauth.RequestTokenRegistration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

// NOTE: this class predates the CarlosUnitTestBase convention and wires Mockito
// mocks directly. The nonce tests added for issue #2955 follow that existing
// style to avoid re-basing the pre-existing callback tests; migrating the whole
// class onto CarlosUnitTestBase is left as a separate test-hygiene follow-up.
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
    @DisplayName("should reject request token callback when requested callback is OOB")
    void shouldRejectRequestTokenCallback_whenRequestedCallbackIsOob() {
        OscarOAuthDataProvider provider = new OscarOAuthDataProvider();
        RequestTokenRegistration registration = registration("https://trusted.example/callback", "oob");

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

    @Test
    @DisplayName("should reject request token callback when ampersand only shares path prefix")
    void shouldRejectRequestTokenCallback_whenAmpersandOnlySharesPathPrefix() {
        OscarOAuthDataProvider provider = new OscarOAuthDataProvider();
        RequestTokenRegistration registration = registration("https://trusted.example/app",
                "https://trusted.example/app&evil=1");

        assertThatThrownBy(() -> provider.createRequestToken(registration))
                .isInstanceOf(OAuth1Exception.class)
                .hasMessage("callback_uri not allowed");
    }

    @Test
    @DisplayName("should allow request token creation when requested callback is null")
    void shouldAllowRequestToken_whenRequestedCallbackIsNull() {
        OscarOAuthDataProvider provider = providerWithCallbackDaos();
        RequestTokenRegistration registration = registration("https://trusted.example/callback", null);

        RequestToken rt = provider.createRequestToken(registration);

        assertThat(rt).isNotNull();
        assertThat(rt.getCallback()).isNull();
    }

    @Test
    @DisplayName("should reject request token callback when registered callback is blank")
    void shouldRejectRequestTokenCallback_whenRegisteredCallbackIsBlank() {
        OscarOAuthDataProvider provider = new OscarOAuthDataProvider();
        RequestTokenRegistration registration = registration("   ", "https://trusted.example/callback");

        assertThatThrownBy(() -> provider.createRequestToken(registration))
                .isInstanceOf(OAuth1Exception.class)
                .hasMessage("callback_uri not allowed");
    }

    @Test
    @DisplayName("should reject request token callback when requested callback scheme is not http or https")
    void shouldRejectRequestTokenCallback_whenRequestedCallbackSchemeIsInvalid() {
        OscarOAuthDataProvider provider = new OscarOAuthDataProvider();
        RequestTokenRegistration registration = registration("https://trusted.example/callback",
                "ftp://trusted.example/callback");

        assertThatThrownBy(() -> provider.createRequestToken(registration))
                .isInstanceOf(OAuth1Exception.class)
                .hasMessage("invalid_callback_scheme");
    }

    @Test
    @DisplayName("should reject request token callback when requested callback host is missing")
    void shouldRejectRequestTokenCallback_whenRequestedCallbackHostIsMissing() {
        OscarOAuthDataProvider provider = new OscarOAuthDataProvider();
        RequestTokenRegistration registration = registration("https://trusted.example/callback",
                "https:callback");

        assertThatThrownBy(() -> provider.createRequestToken(registration))
                .isInstanceOf(OAuth1Exception.class)
                .hasMessage("invalid_callback");
    }

    @ParameterizedTest
    @CsvSource({
            "oob, oob, oob",
            "HTTPS://TRUSTED.EXAMPLE/callback, https://trusted.example/callback, https://trusted.example/callback",
            "HTTPS://TRUSTED.EXAMPLE:443/callback, https://trusted.example/callback, https://trusted.example/callback",
            "https://trusted.example/app, https://trusted.example/app/sub, https://trusted.example/app/sub",
            "https://trusted.example/app?client=car, https://trusted.example/app?client=car&state=ready, https://trusted.example/app?client=car&state=ready"
    })
    @DisplayName("should allow request token creation when callback is allowed")
    void shouldAllowRequestToken_whenCallbackIsAllowed(String registeredCallback, String requestedCallback,
                                                       String expectedCallback) {
        OscarOAuthDataProvider provider = providerWithCallbackDaos();
        RequestTokenRegistration registration = registration(registeredCallback, requestedCallback);

        RequestToken rt = provider.createRequestToken(registration);

        assertThat(rt).isNotNull();
        assertThat(rt.getCallback()).isEqualTo(expectedCallback);
    }

    @Test
    @DisplayName("should return a token with no scopes when persisted scopes are null")
    void shouldReturnTokenWithoutScopes_whenPersistedScopesAreNull() {
        ServiceAccessTokenDao accessTokenDao = mock(ServiceAccessTokenDao.class);
        Integer clientId = 7;
        ServiceAccessToken token = accessToken("scopeless-token", "secret", "999998",
                System.currentTimeMillis() / 1000, 3600);
        token.setClientId(clientId);
        // scopes intentionally left unset -> getScopes() is null (legacy/empty token)
        when(accessTokenDao.findByTokenId("scopeless-token")).thenReturn(token);

        ServiceClientDao clientDao = mock(ServiceClientDao.class);
        ServiceClient client = new ServiceClient();
        client.setKey("consumer");
        when(clientDao.find(clientId)).thenReturn(client);
        when(clientDao.findByKey("consumer")).thenReturn(client);

        OscarOAuthDataProvider provider = new OscarOAuthDataProvider();
        ReflectionTestUtils.setField(provider, "serviceAccessTokenDao", accessTokenDao);
        ReflectionTestUtils.setField(provider, "serviceClientDao", clientDao);

        AccessToken at = provider.getAccessToken("scopeless-token");

        assertThat(at).isNotNull();
        assertThat(at.getScopes()).isEmpty();
    }

    @Test
    @DisplayName("should persist a consumed nonce when seen for the first time")
    void shouldPersistConsumedNonce_whenSeenForFirstTime() {
        ServiceOAuthNonceDao nonceDao = mock(ServiceOAuthNonceDao.class);
        OscarOAuthDataProvider provider = providerWithNonceDao(nonceDao);

        provider.consumeNonce("consumer", "token", "abc", 1000L, 600L);

        ArgumentCaptor<ServiceOAuthNonce> captor = ArgumentCaptor.forClass(ServiceOAuthNonce.class);
        verify(nonceDao).persist(captor.capture());
        ServiceOAuthNonce stored = captor.getValue();
        assertThat(stored.getConsumerKey()).isEqualTo("consumer");
        assertThat(stored.getTokenId()).isEqualTo("token");
        assertThat(stored.getNonce()).isEqualTo("abc");
        assertThat(stored.getOauthTimestamp()).isEqualTo(1000L);
        assertThat(stored.getDateCreated()).isNotNull();
        // A SHA-256 hex digest is 64 lowercase hex characters.
        assertThat(stored.getNonceKeyHash()).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("should reject a replayed nonce when the unique key is already persisted")
    void shouldRejectReplayedNonce_whenNonceKeyAlreadyPersisted() {
        ServiceOAuthNonceDao nonceDao = mock(ServiceOAuthNonceDao.class);
        // The unique key on nonceKeyHash is the sole replay detector: a duplicate
        // (a prior or a concurrent request) trips it on flush, sequential or racing.
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate"))
                .when(nonceDao).flush();
        OscarOAuthDataProvider provider = providerWithNonceDao(nonceDao);

        assertThatThrownBy(() -> provider.consumeNonce("consumer", "token", "abc", 1000L, 600L))
                .isInstanceOf(OAuth1Exception.class)
                .hasMessage("nonce_replayed");
    }

    @Test
    @DisplayName("should reject consumption when a mandatory parameter is blank")
    void shouldRejectNonce_whenMandatoryParameterBlank() {
        ServiceOAuthNonceDao nonceDao = mock(ServiceOAuthNonceDao.class);
        OscarOAuthDataProvider provider = providerWithNonceDao(nonceDao);

        assertThatThrownBy(() -> provider.consumeNonce("", "token", "abc", 1000L, 600L))
                .isInstanceOf(OAuth1Exception.class)
                .hasMessage("invalid_oauth_parameters");
        assertThatThrownBy(() -> provider.consumeNonce("consumer", "token", null, 1000L, 600L))
                .isInstanceOf(OAuth1Exception.class)
                .hasMessage("invalid_oauth_parameters");
        // Whitespace-only values are blank too and must not become replay keys.
        assertThatThrownBy(() -> provider.consumeNonce("   ", "token", "abc", 1000L, 600L))
                .isInstanceOf(OAuth1Exception.class)
                .hasMessage("invalid_oauth_parameters");
        assertThatThrownBy(() -> provider.consumeNonce("consumer", "token", " ", 1000L, 600L))
                .isInstanceOf(OAuth1Exception.class)
                .hasMessage("invalid_oauth_parameters");

        verify(nonceDao, never()).persist(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("should reject consumption when the retention window is not positive")
    void shouldRejectNonce_whenRetentionWindowNotPositive() {
        ServiceOAuthNonceDao nonceDao = mock(ServiceOAuthNonceDao.class);
        OscarOAuthDataProvider provider = providerWithNonceDao(nonceDao);

        assertThatThrownBy(() -> provider.consumeNonce("consumer", "token", "abc", 1000L, 0L))
                .isInstanceOf(IllegalArgumentException.class);

        verify(nonceDao, never()).deleteOlderThan(org.mockito.ArgumentMatchers.anyLong());
        verify(nonceDao, never()).persist(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("should reject a nonce field that exceeds the stored column length")
    void shouldRejectNonce_whenFieldExceedsMaxLength() {
        ServiceOAuthNonceDao nonceDao = mock(ServiceOAuthNonceDao.class);
        OscarOAuthDataProvider provider = providerWithNonceDao(nonceDao);
        String tooLong = "x".repeat(256);

        assertThatThrownBy(() -> provider.consumeNonce("consumer", "token", tooLong, 1000L, 600L))
                .isInstanceOf(OAuth1Exception.class)
                .hasMessage("oauth_parameter_too_long");

        verify(nonceDao, never()).persist(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("should derive a stable hash for the same tuple and distinct hashes for different tuples")
    void shouldDeriveStableHash_whenSameTupleConsumed() {
        String hashAbc1 = capturedKeyHash("consumer", "token", "abc");
        String hashAbc2 = capturedKeyHash("consumer", "token", "abc");
        String hashOther = capturedKeyHash("consumer", "token", "xyz");
        // Field boundaries must not be ambiguous: ("ab","c",..) != ("a","bc",..).
        String hashSplitA = capturedKeyHash("ab", "c", "abc");
        String hashSplitB = capturedKeyHash("a", "bc", "abc");

        assertThat(hashAbc1).isEqualTo(hashAbc2);
        assertThat(hashOther).isNotEqualTo(hashAbc1);
        assertThat(hashSplitA).isNotEqualTo(hashSplitB);
    }

    @Test
    @DisplayName("should prune nonces older than the retention window before recording")
    void shouldPruneNonces_whenRetentionWindowGiven() {
        ServiceOAuthNonceDao nonceDao = mock(ServiceOAuthNonceDao.class);
        OscarOAuthDataProvider provider = providerWithNonceDao(nonceDao);

        long retentionSeconds = 600L;
        long before = System.currentTimeMillis() / 1000;
        provider.consumeNonce("consumer", "token", "abc", 1000L, retentionSeconds);
        long after = System.currentTimeMillis() / 1000;

        ArgumentCaptor<Long> cutoff = ArgumentCaptor.forClass(Long.class);
        verify(nonceDao).deleteOlderThan(cutoff.capture());
        assertThat(cutoff.getValue())
                .isBetween(before - retentionSeconds, after - retentionSeconds);
    }

    @Test
    @DisplayName("should store an empty token id when the request carries no oauth_token")
    void shouldStoreEmptyTokenId_whenTokenIsNull() {
        ServiceOAuthNonceDao nonceDao = mock(ServiceOAuthNonceDao.class);
        OscarOAuthDataProvider provider = providerWithNonceDao(nonceDao);

        provider.consumeNonce("consumer", null, "abc", 1000L, 600L);

        ArgumentCaptor<ServiceOAuthNonce> captor = ArgumentCaptor.forClass(ServiceOAuthNonce.class);
        verify(nonceDao).persist(captor.capture());
        assertThat(captor.getValue().getTokenId()).isEmpty();
    }

    /** Runs consumeNonce and returns the nonceKeyHash it stored. */
    private static String capturedKeyHash(String consumerKey, String tokenId, String nonce) {
        ServiceOAuthNonceDao nonceDao = mock(ServiceOAuthNonceDao.class);
        providerWithNonceDao(nonceDao).consumeNonce(consumerKey, tokenId, nonce, 1000L, 600L);
        ArgumentCaptor<ServiceOAuthNonce> stored = ArgumentCaptor.forClass(ServiceOAuthNonce.class);
        verify(nonceDao).persist(stored.capture());
        return stored.getValue().getNonceKeyHash();
    }

    private static OscarOAuthDataProvider providerWithNonceDao(ServiceOAuthNonceDao nonceDao) {
        OscarOAuthDataProvider provider = new OscarOAuthDataProvider();
        ReflectionTestUtils.setField(provider, "serviceOAuthNonceDao", nonceDao);
        return provider;
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

    private static OscarOAuthDataProvider providerWithCallbackDaos() {
        OscarOAuthDataProvider provider = new OscarOAuthDataProvider();
        ServiceClientDao clientDao = mock(ServiceClientDao.class);
        ServiceRequestTokenDao requestTokenDao = mock(ServiceRequestTokenDao.class);
        ReflectionTestUtils.setField(provider, "serviceClientDao", clientDao);
        ReflectionTestUtils.setField(provider, "serviceRequestTokenDao", requestTokenDao);
        when(clientDao.findByKey("consumer")).thenReturn(mock(ServiceClient.class));
        return provider;
    }
}
