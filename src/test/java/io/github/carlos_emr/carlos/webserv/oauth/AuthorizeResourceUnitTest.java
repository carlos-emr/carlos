/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.webserv.oauth;

import io.github.carlos_emr.carlos.login.OAuthData;
import io.github.carlos_emr.carlos.login.OscarOAuthDataProvider;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AuthorizeResource consent nonce")
@Tag("unit")
@Tag("security")
class AuthorizeResourceUnitTest {

    @Test
    @DisplayName("should stage nonce without binding provider on GET")
    void shouldStageNonce_whenShowingConsent() throws Exception {
        OscarOAuthDataProvider provider = mock(OscarOAuthDataProvider.class);
        RequestToken token = requestToken("request-token");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/oauth/authorize");
        request.setContextPath("/carlos");
        request.getSession().setAttribute("user", "999");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(provider.getRequestToken("request-token")).thenReturn(token);
        AuthorizeResource resource = resource(request, response, provider);

        resource.showConsent("request-token");

        OAuthData data = (OAuthData) request.getAttribute("oauthData");
        assertThat(data.getAuthenticityToken()).isNotBlank();
        assertThat(request.getSession().getAttribute("oauth.authorize.nonce.request-token"))
                .isEqualTo(data.getAuthenticityToken());
        verify(provider, never()).finalizeAuthorization(token, "999");
    }

    @Test
    @DisplayName("should reject approve when nonce is invalid")
    void shouldRejectApprove_whenNonceInvalid() {
        OscarOAuthDataProvider provider = mock(OscarOAuthDataProvider.class);
        RequestToken token = requestToken("request-token");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/oauth/authorize");
        request.getSession().setAttribute("user", "999");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(provider.getRequestToken("request-token")).thenReturn(token);
        AuthorizeResource resource = resource(request, response, provider);

        Response result = resource.approve("request-token", "wrong", "allow");

        assertThat(result.getStatus()).isEqualTo(403);
        assertThat(result.getEntity()).isEqualTo("invalid_authorization_nonce");
        verify(provider, never()).finalizeAuthorization(token, "999");
    }

    @Test
    @DisplayName("should bind provider and verifier when nonce is valid")
    void shouldBindProviderAndVerifier_whenNonceValid() {
        OscarOAuthDataProvider provider = mock(OscarOAuthDataProvider.class);
        RequestToken token = requestToken("request-token");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/oauth/authorize");
        request.getSession().setAttribute("user", "999");
        request.getSession().setAttribute("oauth.authorize.nonce.request-token", "nonce-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(provider.getRequestToken("request-token")).thenReturn(token);
        when(provider.finalizeAuthorization(token, "999")).thenReturn("verifier-123");
        AuthorizeResource resource = resource(request, response, provider);

        Response result = resource.approve("request-token", "nonce-123", "allow");

        assertThat(result.getStatus()).isEqualTo(200);
        assertThat(result.getEntity()).isEqualTo("oauth_verifier=verifier-123");
        assertThat(request.getSession().getAttribute("oauth.authorize.nonce.request-token")).isNull();
        verify(provider).finalizeAuthorization(token, "999");
    }

    @Test
    @DisplayName("should reject approve when decision denies consent")
    void shouldRejectApprove_whenDecisionDeniesConsent() {
        OscarOAuthDataProvider provider = mock(OscarOAuthDataProvider.class);
        RequestToken token = requestToken("request-token");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/oauth/authorize");
        request.getSession().setAttribute("user", "999");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(provider.getRequestToken("request-token")).thenReturn(token);
        AuthorizeResource resource = resource(request, response, provider);

        Response result = resource.approve("request-token", "nonce-123", "deny");

        assertThat(result.getStatus()).isEqualTo(403);
        assertThat(result.getEntity()).isEqualTo("authorization_denied");
        verify(provider, never()).finalizeAuthorization(token, "999");
    }

    private static AuthorizeResource resource(MockHttpServletRequest request,
                                              MockHttpServletResponse response,
                                              OscarOAuthDataProvider provider) {
        AuthorizeResource resource = new AuthorizeResource();
        ReflectionTestUtils.setField(resource, "request", request);
        ReflectionTestUtils.setField(resource, "response", response);
        ReflectionTestUtils.setField(resource, "provider", provider);
        return resource;
    }

    private static RequestToken requestToken(String tokenId) {
        RequestToken token = new RequestToken(new Client("consumer", "secret", "App", "https://app.example"),
                tokenId, "secret");
        token.setCallback("oob");
        return token;
    }
}
