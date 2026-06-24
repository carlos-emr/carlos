/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.carlos.webserv.oauth.Client;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1Exception;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1Request;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1SignatureVerifier;
import io.github.carlos_emr.carlos.webserv.oauth.RequestToken;
import io.github.carlos_emr.carlos.webserv.oauth.RequestTokenRegistration;
import io.github.carlos_emr.carlos.webserv.oauth.util.OAuth1ParamParser;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OscarRequestTokenService callback validation")
@Tag("unit")
@Tag("security")
class OscarRequestTokenServiceUnitTest {

    @Test
    @DisplayName("should reject non HTTP callback scheme when initiating request token")
    void shouldRejectNonHttpCallbackScheme_whenInitiatingRequestToken() {
        OscarOAuthDataProvider dataProvider = mock(OscarOAuthDataProvider.class);
        OAuth1ParamParser parser = mock(OAuth1ParamParser.class);
        OAuth1SignatureVerifier verifier = mock(OAuth1SignatureVerifier.class);
        OscarRequestTokenService service = new OscarRequestTokenService(dataProvider, parser);
        ReflectionTestUtils.setField(service, "verifier", verifier);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/oauth/initiate");
        request.setServerName("carlos.example");
        request.setScheme("https");
        OAuth1Request oauthRequest = new OAuth1Request();
        oauthRequest.consumerKey = "consumer";
        oauthRequest.callback = "javascript:alert(1)";
        Client client = new Client("consumer", "secret", "App", "https://trusted.example");
        client.setCallbackUri("https://trusted.example/callback");
        when(parser.parseFromRequest(request)).thenReturn(oauthRequest);
        when(dataProvider.getClient("consumer")).thenReturn(client);

        assertThatThrownBy(() -> service.initiatePost(request))
                .isInstanceOf(OAuth1Exception.class)
                .hasMessage("invalid_callback_scheme");
        verify(dataProvider, never()).createRequestToken(any());
    }

    @Test
    @DisplayName("should reject callback without host when initiating request token")
    void shouldRejectCallbackWithoutHost_whenInitiatingRequestToken() {
        OscarOAuthDataProvider dataProvider = mock(OscarOAuthDataProvider.class);
        OAuth1ParamParser parser = mock(OAuth1ParamParser.class);
        OAuth1SignatureVerifier verifier = mock(OAuth1SignatureVerifier.class);
        OscarRequestTokenService service = new OscarRequestTokenService(dataProvider, parser);
        ReflectionTestUtils.setField(service, "verifier", verifier);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/oauth/initiate");
        request.setServerName("carlos.example");
        request.setScheme("https");
        OAuth1Request oauthRequest = new OAuth1Request();
        oauthRequest.consumerKey = "consumer";
        oauthRequest.callback = "https:callback";
        Client client = new Client("consumer", "secret", "App", "https://trusted.example");
        client.setCallbackUri("https://trusted.example/callback");
        when(parser.parseFromRequest(request)).thenReturn(oauthRequest);
        when(dataProvider.getClient("consumer")).thenReturn(client);

        assertThatThrownBy(() -> service.initiatePost(request))
                .isInstanceOf(OAuth1Exception.class)
                .hasMessage("invalid_callback");
        verify(dataProvider, never()).createRequestToken(any());
    }

    @ParameterizedTest
    @CsvSource({
            "HTTPS://APP.EXAMPLE/callback, https://app.example/callback",
            "HTTPS://APP.EXAMPLE:443/callback, https://app.example/callback",
            "OOB, oob"
    })
    @DisplayName("should store canonical callback when request callback is allowed")
    void shouldStoreCanonicalCallback_whenRequestCallbackIsAllowed(String requestedCallback,
                                                                   String expectedCallback) {
        OscarOAuthDataProvider dataProvider = mock(OscarOAuthDataProvider.class);
        OAuth1ParamParser parser = mock(OAuth1ParamParser.class);
        OAuth1SignatureVerifier verifier = mock(OAuth1SignatureVerifier.class);
        OscarRequestTokenService service = new OscarRequestTokenService(dataProvider, parser);
        ReflectionTestUtils.setField(service, "verifier", verifier);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/oauth/initiate");
        request.setServerName("carlos.example");
        request.setScheme("https");
        OAuth1Request oauthRequest = new OAuth1Request();
        oauthRequest.consumerKey = "consumer";
        oauthRequest.callback = requestedCallback;
        Client client = new Client("consumer", "secret", "App", "https://trusted.example");
        client.setCallbackUri("https://trusted.example/callback");
        RequestToken token = new RequestToken(client, "token-id", "token-secret");
        when(parser.parseFromRequest(request)).thenReturn(oauthRequest);
        when(dataProvider.getClient("consumer")).thenReturn(client);
        when(dataProvider.createRequestToken(any())).thenReturn(token);

        Response response = service.initiatePost(request);

        ArgumentCaptor<RequestTokenRegistration> registrationCaptor =
                ArgumentCaptor.forClass(RequestTokenRegistration.class);
        verify(dataProvider).createRequestToken(registrationCaptor.capture());
        assertThat(registrationCaptor.getValue().getCallback()).isEqualTo(expectedCallback);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("should use registered callback URL when request omits callback and registered callback is HTTPS")
    void shouldUseRegisteredCallbackUrl_whenRequestOmitsCallbackAndRegisteredIsHttps() {
        OscarOAuthDataProvider dataProvider = mock(OscarOAuthDataProvider.class);
        OAuth1ParamParser parser = mock(OAuth1ParamParser.class);
        OAuth1SignatureVerifier verifier = mock(OAuth1SignatureVerifier.class);
        OscarRequestTokenService service = new OscarRequestTokenService(dataProvider, parser);
        ReflectionTestUtils.setField(service, "verifier", verifier);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/oauth/initiate");
        request.setServerName("carlos.example");
        request.setScheme("https");
        OAuth1Request oauthRequest = new OAuth1Request();
        oauthRequest.consumerKey = "consumer";
        Client client = new Client("consumer", "secret", "App", "https://trusted.example/callback");
        client.setCallbackUri("https://trusted.example/callback");
        RequestToken token = new RequestToken(client, "token-id", "token-secret");
        when(parser.parseFromRequest(request)).thenReturn(oauthRequest);
        when(dataProvider.getClient("consumer")).thenReturn(client);
        when(dataProvider.createRequestToken(any())).thenReturn(token);

        Response response = service.initiatePost(request);

        ArgumentCaptor<RequestTokenRegistration> registrationCaptor =
                ArgumentCaptor.forClass(RequestTokenRegistration.class);
        verify(dataProvider).createRequestToken(registrationCaptor.capture());
        assertThat(registrationCaptor.getValue().getCallback()).isEqualTo("https://trusted.example/callback");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("should use OOB callback when registered callback is OOB and request omits callback")
    void shouldUseOobCallback_whenRegisteredCallbackIsOobAndRequestOmitsCallback() {
        OscarOAuthDataProvider dataProvider = mock(OscarOAuthDataProvider.class);
        OAuth1ParamParser parser = mock(OAuth1ParamParser.class);
        OAuth1SignatureVerifier verifier = mock(OAuth1SignatureVerifier.class);
        OscarRequestTokenService service = new OscarRequestTokenService(dataProvider, parser);
        ReflectionTestUtils.setField(service, "verifier", verifier);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/oauth/initiate");
        request.setServerName("carlos.example");
        request.setScheme("https");
        OAuth1Request oauthRequest = new OAuth1Request();
        oauthRequest.consumerKey = "consumer";
        Client client = new Client("consumer", "secret", "App", "https://trusted.example/callback");
        client.setCallbackUri("OOB");
        RequestToken token = new RequestToken(client, "token-id", "token-secret");
        when(parser.parseFromRequest(request)).thenReturn(oauthRequest);
        when(dataProvider.getClient("consumer")).thenReturn(client);
        when(dataProvider.createRequestToken(any())).thenReturn(token);

        Response response = service.initiatePost(request);

        ArgumentCaptor<RequestTokenRegistration> registrationCaptor =
                ArgumentCaptor.forClass(RequestTokenRegistration.class);
        verify(dataProvider).createRequestToken(registrationCaptor.capture());
        assertThat(registrationCaptor.getValue().getCallback()).isEqualTo("oob");
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
