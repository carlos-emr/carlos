/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.carlos.webserv.oauth.Client;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1Exception;
import io.github.carlos_emr.carlos.webserv.oauth.RequestToken;
import io.github.carlos_emr.carlos.webserv.oauth.RequestTokenRegistration;
import io.github.carlos_emr.carlos.webserv.oauth.util.OAuth1ParamParser;
import io.github.carlos_emr.carlos.webserv.oauth.util.OAuth1SignatureVerifierImplementation;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("security")
class OAuthEncodedCallbackUnitTest {
    private final String consumerSecret = java.util.UUID.randomUUID().toString();
    private static final String CALLBACK = "https://app.example/callback?fixture=owned&context=owned%20callback%20%26%20value&literal=%2520&plus=%2B";

    @ParameterizedTest
    @ValueSource(strings = {"header", "form", "query"})
    void shouldVerifyOnceAndPreserveCallbackEscapes_forEachParameterTransport(String transport) throws Exception {
        var provider = mock(OscarOAuthDataProvider.class);
        var client = new Client("consumer", consumerSecret, "Fixture", "https://app.example");
        client.setCallbackUri("https://app.example/callback?fixture=owned");
        when(provider.getClient("consumer")).thenReturn(client);
        when(provider.createRequestToken(any())).thenReturn(new RequestToken(client, "token", "token-secret"));
        var service = new OscarRequestTokenService(provider, new OAuth1ParamParser());
        ReflectionTestUtils.setField(service, "verifier", new OAuth1SignatureVerifierImplementation(provider));
        var request = signedRequest(transport, false, consumerSecret);

        assertThat(service.initiatePost(request).getStatus()).isEqualTo(200);
        var registration = ArgumentCaptor.forClass(RequestTokenRegistration.class);
        verify(provider).createRequestToken(registration.capture());
        assertThat(registration.getValue().getCallback()).isEqualTo(CALLBACK);
        verify(provider).consumeNonce(eq("consumer"), isNull(), eq("nonce%20literal+é"), anyLong(), eq(600L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"header", "form", "query"})
    void shouldRefuseAnInvalidSignature_withoutMintingCredentialsOrConsumingNonce(String transport) throws Exception {
        var provider = mock(OscarOAuthDataProvider.class);
        var client = new Client("consumer", consumerSecret, "Fixture", "https://app.example");
        when(provider.getClient("consumer")).thenReturn(client);
        var service = new OscarRequestTokenService(provider, new OAuth1ParamParser());
        ReflectionTestUtils.setField(service, "verifier", new OAuth1SignatureVerifierImplementation(provider));

        assertThatThrownBy(() -> service.initiatePost(signedRequest(transport, true, consumerSecret)))
                .isInstanceOfSatisfying(OAuth1Exception.class, error -> {
                    assertThat(error.getHttpCode()).isEqualTo(401);
                    assertThat(error).hasMessage("invalid_signature");
                });
        verify(provider, never()).createRequestToken(any());
        verify(provider, never()).consumeNonce(any(), any(), any(), anyLong(), anyLong());
    }

    private static MockHttpServletRequest signedRequest(String transport, boolean tamper, String consumerSecret) throws Exception {
        var request = new MockHttpServletRequest("POST", "/ws/oauth/initiate");
        request.setScheme("https");
        request.setServerName("clinic.example");
        request.setServerPort(443);
        var parameters = new TreeMap<String, String>(Map.of(
                "oauth_consumer_key", "consumer", "oauth_callback", CALLBACK,
                "oauth_signature_method", "HMAC-SHA1", "oauth_nonce", "nonce%20literal+é",
                "oauth_timestamp", Long.toString(System.currentTimeMillis() / 1000)));
        parameters.put("scope", "demographic.read");
        String normalized = parameters.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
        String base = "POST&" + encode("https://clinic.example/ws/oauth/initiate") + "&" + encode(normalized);
        var mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec((encode(consumerSecret) + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        parameters.put("oauth_signature", tamper ? "invalid" : Base64.getEncoder().encodeToString(mac.doFinal(base.getBytes(StandardCharsets.UTF_8))));
        if ("header".equals(transport)) {
            request.setQueryString("scope=demographic.read");
            request.addHeader("Authorization", "OAuth " + parameters.entrySet().stream()
                    .filter(e -> e.getKey().startsWith("oauth_"))
                    .map(e -> e.getKey() + "=\"" + encode(e.getValue()) + "\"")
                    .collect(java.util.stream.Collectors.joining(", ")));
        } else {
            if ("form".equals(transport)) request.setContentType("application/x-www-form-urlencoded");
            else request.setQueryString(parameters.entrySet().stream()
                    .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                    .collect(java.util.stream.Collectors.joining("&")));
            parameters.forEach(request::addParameter);
        }
        return request;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
    }
}
