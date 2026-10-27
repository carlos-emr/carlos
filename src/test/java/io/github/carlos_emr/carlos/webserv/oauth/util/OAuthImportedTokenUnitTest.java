/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.webserv.oauth.util;

import io.github.carlos_emr.carlos.commn.dao.ServiceAccessTokenDao;
import io.github.carlos_emr.carlos.commn.dao.ServiceClientDao;
import io.github.carlos_emr.carlos.commn.dao.ServiceOAuthNonceDao;
import io.github.carlos_emr.carlos.commn.model.ServiceAccessToken;
import io.github.carlos_emr.carlos.commn.model.ServiceClient;
import io.github.carlos_emr.carlos.commn.model.ServiceOAuthNonce;
import io.github.carlos_emr.carlos.login.AppOAuth1Config;
import io.github.carlos_emr.carlos.login.OscarOAuthDataProvider;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1Exception;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Fresh signed requests using persisted O19 grants, without another handshake. */
@Tag("unit")
@Tag("security")
@DisplayName("Imported O19 token signature continuity")
class OAuthImportedTokenUnitTest {
    private static final String PATH = "/oscar/ws/services/demographics/123";
    private ServiceAccessToken token;
    private ServiceAccessTokenDao tokens;
    private OscarOAuthDataProvider provider;
    private OAuth1SignatureVerifierImplementation verifier;

    @BeforeEach
    void setUp() {
        token = new ServiceAccessToken();
        token.setClientId(7);
        token.setTokenId("fixture-o19-token");
        token.setTokenSecret("fixture-o19-token-secret");
        token.setIssued(1550000000L);
        token.setLifetime(-1);
        token.setProviderNo("999998");
        token.setScopes("demographics.read");
        tokens = mock(ServiceAccessTokenDao.class);
        when(tokens.findByTokenId(token.getTokenId())).thenReturn(token);
        ServiceClient client = new ServiceClient();
        client.setKey("fixture-o19-consumer");
        client.setSecret("fixture-o19-consumer-secret");
        ServiceClientDao clients = mock(ServiceClientDao.class);
        when(clients.find(Integer.valueOf(7))).thenReturn(client);
        ServiceOAuthNonceDao nonces = mock(ServiceOAuthNonceDao.class);
        Set<String> consumed = new HashSet<>();
        doAnswer(call -> {
            ServiceOAuthNonce nonce = call.getArgument(0);
            if (!consumed.add(nonce.getNonceKeyHash())) {
                throw new DataIntegrityViolationException("duplicate fixture nonce");
            }
            return null;
        }).when(nonces).persist(any(ServiceOAuthNonce.class));
        provider = new OscarOAuthDataProvider();
        ReflectionTestUtils.setField(provider, "serviceAccessTokenDao", tokens);
        ReflectionTestUtils.setField(provider, "serviceClientDao", clients);
        ReflectionTestUtils.setField(provider, "serviceOAuthNonceDao", nonces);
        verifier = new OAuth1SignatureVerifierImplementation(provider);
    }

    @Test
    void shouldAcceptOriginalCredentials_whenImportedTokenHasNoExpiry() throws Exception {
        MockHttpServletRequest request = signed("fixture-o19-consumer", "fixture-o19-consumer-secret");
        assertThat(verifier.verifySignature(request, config("fixture-o19-consumer", "fixture-o19-consumer-secret")))
                .isEqualTo(token.getTokenId());
        assertThat(token.getIssued()).isEqualTo(1550000000L);
        assertThat(token.getLifetime()).isEqualTo(-1);
        verify(tokens, never()).remove(token);
    }

    @Test
    void shouldRejectDifferentConsumer_evenWithAValidHmacAndTheTokenSecret() throws Exception {
        MockHttpServletRequest request = signed("other-consumer", "other-secret");
        assertThatThrownBy(() -> verifier.verifySignature(request, config("other-consumer", "other-secret")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(tokens, never()).remove(token);
    }

    @Test
    void shouldRejectReplay_withoutRevokingTheOriginalGrant() throws Exception {
        MockHttpServletRequest request = signed("fixture-o19-consumer", "fixture-o19-consumer-secret");
        AppOAuth1Config config = config("fixture-o19-consumer", "fixture-o19-consumer-secret");
        verifier.verifySignature(request, config);
        assertThatThrownBy(() -> verifier.verifySignature(request, config)).isInstanceOf(OAuth1Exception.class);
        verify(tokens, never()).remove(token);
    }

    @Test
    void shouldRejectExpiredImportedToken_withoutRenewingIt() throws Exception {
        token.setLifetime(3600);
        MockHttpServletRequest request = signed("fixture-o19-consumer", "fixture-o19-consumer-secret");
        assertThatThrownBy(() -> verifier.verifySignature(request,
                config("fixture-o19-consumer", "fixture-o19-consumer-secret")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(tokens).remove(token);
        assertThat(token.getIssued()).isEqualTo(1550000000L);
    }

    @Test
    void shouldRejectRevokedToken_withoutCreatingAnotherGrant() throws Exception {
        when(tokens.findByTokenId(token.getTokenId())).thenReturn(null);
        MockHttpServletRequest request = signed("fixture-o19-consumer", "fixture-o19-consumer-secret");
        assertThatThrownBy(() -> verifier.verifySignature(request,
                config("fixture-o19-consumer", "fixture-o19-consumer-secret")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(tokens, never()).persist(any(ServiceAccessToken.class));
    }

    @Test
    void shouldRejectForgery_withoutDeletingTheImportedGrant() throws Exception {
        MockHttpServletRequest request = signed("fixture-o19-consumer", "wrong-consumer-secret");
        assertThatThrownBy(() -> verifier.verifySignature(request,
                config("fixture-o19-consumer", "fixture-o19-consumer-secret")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(tokens, never()).remove(token);
    }

    private static AppOAuth1Config config(String key, String secret) {
        AppOAuth1Config config = new AppOAuth1Config();
        config.setConsumerKey(key);
        config.setConsumerSecret(secret);
        return config;
    }

    private MockHttpServletRequest signed(String consumer, String secret) throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("oauth_consumer_key", consumer);
        params.put("oauth_token", token.getTokenId());
        params.put("oauth_signature_method", "HMAC-SHA1");
        params.put("oauth_timestamp", Long.toString(System.currentTimeMillis() / 1000));
        params.put("oauth_nonce", UUID.randomUUID().toString());
        String normalized = params.entrySet().stream().map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
        String base = "GET&" + enc("https://clinic.example" + PATH) + "&" + enc(normalized);
        Mac mac = Mac.getInstance("HmacSHA1");
        String signingKey = enc(secret) + '&' + enc(token.getTokenSecret());
        mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        params.put("oauth_signature", Base64.getEncoder().encodeToString(mac.doFinal(base.getBytes(StandardCharsets.UTF_8))));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
        request.setScheme("https");
        request.setServerName("clinic.example");
        request.setServerPort(443);
        params.forEach(request::addParameter);
        return request;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A");
    }
}
