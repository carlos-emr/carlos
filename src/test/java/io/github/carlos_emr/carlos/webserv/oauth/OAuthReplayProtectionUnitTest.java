/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.webserv.oauth;

import io.github.carlos_emr.carlos.login.AppOAuth1Config;
import io.github.carlos_emr.carlos.login.OscarOAuthDataProvider;
import io.github.carlos_emr.carlos.webserv.oauth.util.OAuth1ParamParser;
import io.github.carlos_emr.carlos.webserv.oauth.util.OAuthRequestParser;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for OAuth 1.0a nonce replay rejection and malformed header decoding.
 */
@DisplayName("OAuth replay protection")
@Tag("unit")
@Tag("security")
class OAuthReplayProtectionUnitTest {

    @Test
    @DisplayName("should reject nonce replay when timestamp matches")
    void shouldRejectNonceReplay_whenTimestampMatches() {
        OscarOAuthDataProvider provider = new OscarOAuthDataProvider();
        OAuth1Request request = oauthRequest("consumer", uniqueNonce(), currentTimestamp());

        assertThatCode(() -> provider.checkTimestampAndNonce(request)).doesNotThrowAnyException();

        assertThatThrownBy(() -> provider.checkTimestampAndNonce(request))
                .isInstanceOf(OAuth1Exception.class)
                .hasMessage("oauth_nonce_replayed")
                .extracting("httpCode")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("should allow same nonce when timestamp differs")
    void shouldAllowSameNonce_whenTimestampDiffers() {
        OscarOAuthDataProvider provider = new OscarOAuthDataProvider();
        long now = System.currentTimeMillis() / 1000L;
        String nonce = uniqueNonce();
        OAuth1Request first = oauthRequest("consumer", nonce, Long.toString(now));
        OAuth1Request second = oauthRequest("consumer", nonce, Long.toString(now + 1));

        assertThatCode(() -> provider.checkTimestampAndNonce(first)).doesNotThrowAnyException();

        assertThatCode(() -> provider.checkTimestampAndNonce(second)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should allow distinct nonce inputs when values contain delimiters")
    void shouldAllowDistinctNonceInputs_whenValuesContainDelimiters() {
        OscarOAuthDataProvider provider = new OscarOAuthDataProvider();
        String timestamp = currentTimestamp();
        String suffix = uniqueNonce();
        OAuth1Request first = oauthRequest("consumer-" + suffix, "nonce\nvalue", timestamp);
        OAuth1Request second = oauthRequest("consumer-" + suffix + "\nnonce", "value", timestamp);

        assertThatCode(() -> provider.checkTimestampAndNonce(first)).doesNotThrowAnyException();

        assertThatCode(() -> provider.checkTimestampAndNonce(second)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should check nonce before issuing access token")
    void shouldCheckNonce_whenIssuingAccessToken() {
        OscarOAuthDataProvider provider = mock(OscarOAuthDataProvider.class);
        OAuth1ParamParser parser = mock(OAuth1ParamParser.class);
        OAuth1SignatureVerifier verifier = mock(OAuth1SignatureVerifier.class);
        Client client = new Client("consumer", "secret", "App", "https://app.example");
        RequestToken requestToken = new RequestToken(client, "request-token", "request-secret");
        requestToken.setVerifier("verifier-123");
        AccessToken accessToken = new AccessToken(client, "access-token", "access-secret", 3600, 1);
        OAuth1Request oauthRequest = oauthRequest("consumer", "nonce-123", currentTimestamp());
        oauthRequest.token = "request-token";
        oauthRequest.verifier = "verifier-123";
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/ws/oauth/token");
        AccessTokenResource resource = new AccessTokenResource(provider, parser, verifier);
        ReflectionTestUtils.setField(resource, "req", servletRequest);
        when(parser.parseFromRequest(servletRequest)).thenReturn(oauthRequest);
        when(provider.getClient("consumer")).thenReturn(client);
        when(provider.getRequestToken("request-token")).thenReturn(requestToken);
        when(verifier.verifySignature(eq(servletRequest), any(AppOAuth1Config.class)))
                .thenReturn("request-token");
        when(provider.createAccessToken(requestToken)).thenReturn(accessToken);

        Response response = resource.exchange();

        assertThat(response.getStatus()).isEqualTo(200);
        InOrder inOrder = inOrder(provider);
        inOrder.verify(provider).checkTimestampAndNonce(oauthRequest);
        inOrder.verify(provider).createAccessToken(requestToken);
    }

    @Test
    @DisplayName("should reject access token request when nonce replays")
    void shouldRejectAccessTokenRequest_whenNonceReplays() {
        OscarOAuthDataProvider provider = mock(OscarOAuthDataProvider.class);
        OAuth1ParamParser parser = mock(OAuth1ParamParser.class);
        OAuth1SignatureVerifier verifier = mock(OAuth1SignatureVerifier.class);
        Client client = new Client("consumer", "secret", "App", "https://app.example");
        RequestToken requestToken = new RequestToken(client, "request-token", "request-secret");
        requestToken.setVerifier("verifier-123");
        OAuth1Request oauthRequest = oauthRequest("consumer", "nonce-123", currentTimestamp());
        oauthRequest.token = "request-token";
        oauthRequest.verifier = "verifier-123";
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/ws/oauth/token");
        AccessTokenResource resource = new AccessTokenResource(provider, parser, verifier);
        ReflectionTestUtils.setField(resource, "req", servletRequest);
        when(parser.parseFromRequest(servletRequest)).thenReturn(oauthRequest);
        when(provider.getClient("consumer")).thenReturn(client);
        when(provider.getRequestToken("request-token")).thenReturn(requestToken);
        when(verifier.verifySignature(eq(servletRequest), any(AppOAuth1Config.class)))
                .thenReturn("request-token");
        doThrow(new OAuth1Exception(401, "oauth_nonce_replayed"))
                .when(provider).checkTimestampAndNonce(oauthRequest);

        Response response = resource.exchange();

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getEntity()).isEqualTo("oauth_nonce_replayed");
        verify(provider, never()).createAccessToken(requestToken);
    }

    @Test
    @DisplayName("should reject malformed encoded OAuth header value")
    void shouldRejectMalformedEncodedOAuthHeaderValue_whenExtractingParameters() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/api");
        request.addHeader("Authorization", "OAuth oauth_consumer_key=\"consumer%\", oauth_signature=\"sig\"");

        assertThatThrownBy(() -> OAuthRequestParser.extractOAuthParameters(request))
                .isInstanceOf(OAuth1Exception.class)
                .hasMessage("invalid_oauth_parameters")
                .extracting("httpCode")
                .isEqualTo(400);
    }

    @Test
    @DisplayName("should reject malformed encoded OAuth signature")
    void shouldRejectMalformedEncodedOAuthSignature_whenExtractingSignature() {
        String header = "OAuth oauth_consumer_key=\"consumer\", oauth_signature=\"sig%\"";

        assertThatThrownBy(() -> OAuthRequestParser.extractSignatureFromHeader(header))
                .isInstanceOf(OAuth1Exception.class)
                .hasMessage("invalid_oauth_parameters")
                .extracting("httpCode")
                .isEqualTo(400);
    }

    private static OAuth1Request oauthRequest(String consumerKey, String nonce, String timestamp) {
        OAuth1Request request = new OAuth1Request();
        request.consumerKey = consumerKey;
        request.nonce = nonce;
        request.timestamp = timestamp;
        return request;
    }

    private static String currentTimestamp() {
        return Long.toString(System.currentTimeMillis() / 1000L);
    }

    private static String uniqueNonce() {
        return "nonce-" + System.nanoTime();
    }
}
