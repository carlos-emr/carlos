/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.webserv.oauth.util;

import io.github.carlos_emr.carlos.login.OscarOAuthDataProvider;
import io.github.carlos_emr.carlos.webserv.oauth.Client;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1SignatureVerifier;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.transport.http.AbstractHTTPDestination;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the CXF status-code propagation for OAuth 1.0a authentication failures
 * (issue #2954). Without {@code Fault.setStatusCode(...)} CXF defaults an
 * in-interceptor fault to HTTP 500, so bad credentials would appear to API callers
 * as a server error instead of a clean 400/401.
 */
@DisplayName("OAuthInterceptor auth-failure status codes")
@Tag("unit")
@Tag("security")
class OAuthInterceptorUnitTest {

    private static final String TEST_CONSUMER_KEY = "consumer-key";

    @Test
    @DisplayName("should raise fault with HTTP 400 when consumer key is missing")
    void shouldRaiseFault_withHttp400WhenConsumerKeyMissing() {
        OAuthInterceptor interceptor = new OAuthInterceptor();
        Message message = messageWith(oauthRequestWithoutCredentials());

        Fault fault = catchThrowableOfType(() -> interceptor.handleMessage(message), Fault.class);

        assertThat(fault).isNotNull();
        assertThat(fault.getStatusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("should raise fault with HTTP 401 when consumer is unknown")
    void shouldRaiseFault_withHttp401WhenConsumerUnknown() {
        OscarOAuthDataProvider dataProvider = mock(OscarOAuthDataProvider.class);
        when(dataProvider.getClient("nope")).thenReturn(null);

        OAuthInterceptor interceptor = new OAuthInterceptor();
        ReflectionTestUtils.setField(interceptor, "oauthDataProvider", dataProvider);

        MockHttpServletRequest request = oauthRequestWithoutCredentials();
        request.addParameter("oauth_consumer_key", "nope");
        request.addParameter("oauth_token", "some-token");
        Message message = messageWith(request);

        Fault fault = catchThrowableOfType(() -> interceptor.handleMessage(message), Fault.class);

        assertThat(fault).isNotNull();
        assertThat(fault.getStatusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("should raise fault with HTTP 401 when signature verification fails")
    void shouldRaiseFault_withHttp401WhenSignatureVerificationFails() {
        OscarOAuthDataProvider dataProvider = mock(OscarOAuthDataProvider.class);
        when(dataProvider.getClient(TEST_CONSUMER_KEY)).thenReturn(mock(Client.class));
        // Verifier rejects the signature/timestamp the way it does for a real bad request.
        OAuth1SignatureVerifier verifier = mock(OAuth1SignatureVerifier.class);
        when(verifier.verifySignature(any(), any()))
                .thenThrow(new IllegalArgumentException("bad signature"));

        OAuthInterceptor interceptor = new OAuthInterceptor();
        ReflectionTestUtils.setField(interceptor, "oauthDataProvider", dataProvider);
        ReflectionTestUtils.setField(interceptor, "verifier", verifier);

        MockHttpServletRequest request = oauthRequestWithoutCredentials();
        request.addParameter("oauth_consumer_key", TEST_CONSUMER_KEY);
        request.addParameter("oauth_token", "some-token");
        Message message = messageWith(request);

        Fault fault = catchThrowableOfType(() -> interceptor.handleMessage(message), Fault.class);

        assertThat(fault).isNotNull();
        assertThat(fault.getStatusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("should raise fault with HTTP 500 when an unexpected error occurs")
    void shouldRaiseFault_withHttp500WhenUnexpectedError() {
        OscarOAuthDataProvider dataProvider = mock(OscarOAuthDataProvider.class);
        // A data-access style failure is a server error, not an authentication failure.
        when(dataProvider.getClient(TEST_CONSUMER_KEY))
                .thenThrow(new RuntimeException("database unavailable"));

        OAuthInterceptor interceptor = new OAuthInterceptor();
        ReflectionTestUtils.setField(interceptor, "oauthDataProvider", dataProvider);

        MockHttpServletRequest request = oauthRequestWithoutCredentials();
        request.addParameter("oauth_consumer_key", TEST_CONSUMER_KEY);
        request.addParameter("oauth_token", "some-token");
        Message message = messageWith(request);

        Fault fault = catchThrowableOfType(() -> interceptor.handleMessage(message), Fault.class);

        assertThat(fault).isNotNull();
        assertThat(fault.getStatusCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("should raise fault with HTTP 401 when request carries no OAuth credentials")
    void shouldRaiseFault_withHttp401WhenNoOAuthCredentials() {
        OAuthInterceptor interceptor = new OAuthInterceptor();
        // No Authorization header and no oauth_consumer_key param: the request is
        // not OAuth-authenticated. The OAuth-only /ws/services surface must fail
        // closed rather than let an anonymous caller reach the handler (#2798).
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/ws/services/notes/getGroupNoteExt/94");
        Message message = messageWith(request);

        Fault fault = catchThrowableOfType(() -> interceptor.handleMessage(message), Fault.class);

        assertThat(fault).isNotNull();
        assertThat(fault.getStatusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("should raise fault with HTTP 401 when no HTTP request is present")
    void shouldRaiseFault_withHttp401WhenRequestIsNull() {
        OAuthInterceptor interceptor = new OAuthInterceptor();
        // No HttpServletRequest on the message (e.g. a non-HTTP transport): the
        // request cannot be authenticated, so it must fail closed rather than NPE.
        Message message = new MessageImpl();

        Fault fault = catchThrowableOfType(() -> interceptor.handleMessage(message), Fault.class);

        assertThat(fault).isNotNull();
        assertThat(fault.getStatusCode()).isEqualTo(401);
    }

    /** A request that looks like OAuth1 (has an Authorization header) but carries no usable params. */
    private static MockHttpServletRequest oauthRequestWithoutCredentials() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/rest/example");
        request.addHeader("Authorization", "OAuth realm=\"carlos\"");
        return request;
    }

    private static Message messageWith(MockHttpServletRequest request) {
        Message message = new MessageImpl();
        message.put(AbstractHTTPDestination.HTTP_REQUEST, request);
        return message;
    }
}
