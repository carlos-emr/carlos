/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.webserv.oauth.util;

import io.github.carlos_emr.carlos.login.OscarOAuthDataProvider;
import io.github.carlos_emr.carlos.webserv.oauth.Client;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the CXF status-code propagation for OAuth 1.0a authentication failures
 * (issue #2954). Without {@code Fault.setStatusCode(...)} CXF defaults an
 * in-interceptor fault to HTTP 500, so bad credentials would read to API callers
 * as a server error instead of a clean 400/401.
 */
@DisplayName("OAuthInterceptor auth-failure status codes")
@Tag("unit")
@Tag("security")
class OAuthInterceptorUnitTest {

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
    @DisplayName("should not default to HTTP 500 on authentication failure")
    void shouldNotDefaultToHttp500_onAuthenticationFailure() {
        OscarOAuthDataProvider dataProvider = mock(OscarOAuthDataProvider.class);
        when(dataProvider.getClient(anyConsumer())).thenReturn(mock(Client.class));

        OAuthInterceptor interceptor = new OAuthInterceptor();
        ReflectionTestUtils.setField(interceptor, "oauthDataProvider", dataProvider);

        MockHttpServletRequest request = oauthRequestWithoutCredentials();
        request.addParameter("oauth_consumer_key", anyConsumer());
        request.addParameter("oauth_token", "some-token");
        Message message = messageWith(request);

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOfSatisfying(Fault.class,
                        fault -> assertThat(fault.getStatusCode()).isNotEqualTo(500));
    }

    private static String anyConsumer() {
        return "consumer-key";
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
