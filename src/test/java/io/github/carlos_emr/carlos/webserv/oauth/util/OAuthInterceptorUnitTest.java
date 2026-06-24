/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.webserv.oauth.util;

import io.github.carlos_emr.carlos.login.AppOAuth1Config;
import io.github.carlos_emr.carlos.login.OscarOAuthDataProvider;
import io.github.carlos_emr.carlos.webserv.oauth.Client;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1Exception;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1SignatureVerifier;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("OAuthInterceptor fault status")
@Tag("unit")
@Tag("security")
class OAuthInterceptorUnitTest {

    @Test
    @DisplayName("should fault with the carried 401 status when the nonce is replayed")
    void shouldFaultWithCarriedStatus_whenNonceReplayed() {
        OscarOAuthDataProvider dataProvider = mock(OscarOAuthDataProvider.class);
        OAuth1SignatureVerifier verifier = mock(OAuth1SignatureVerifier.class);
        OAuthInterceptor interceptor = new OAuthInterceptor();
        ReflectionTestUtils.setField(interceptor, "oauthDataProvider", dataProvider);
        ReflectionTestUtils.setField(interceptor, "verifier", verifier);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/ws/services/demographics");
        req.addParameter("oauth_consumer_key", "consumer");
        req.addParameter("oauth_token", "access-token");
        when(dataProvider.getClient("consumer"))
                .thenReturn(new Client("consumer", "secret", "App", "https://trusted.example"));
        when(verifier.verifySignature(eq(req), any(AppOAuth1Config.class)))
                .thenThrow(new OAuth1Exception(401, "nonce_replayed"));
        Message message = mock(Message.class);
        when(message.get(AbstractHTTPDestination.HTTP_REQUEST)).thenReturn(req);

        Fault fault = catchThrowableOfType(() -> interceptor.handleMessage(message), Fault.class);

        assertThat(fault).isNotNull();
        assertThat(fault.getStatusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("should fault with the carried 400 status when a required parameter is missing")
    void shouldFaultWithCarriedStatus_whenAccessTokenMissing() {
        OAuthInterceptor interceptor = new OAuthInterceptor();
        ReflectionTestUtils.setField(interceptor, "oauthDataProvider", mock(OscarOAuthDataProvider.class));
        ReflectionTestUtils.setField(interceptor, "verifier", mock(OAuth1SignatureVerifier.class));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/ws/services/demographics");
        req.addParameter("oauth_consumer_key", "consumer"); // no oauth_token
        Message message = mock(Message.class);
        when(message.get(AbstractHTTPDestination.HTTP_REQUEST)).thenReturn(req);

        Fault fault = catchThrowableOfType(() -> interceptor.handleMessage(message), Fault.class);

        assertThat(fault).isNotNull();
        assertThat(fault.getStatusCode()).isEqualTo(400);
    }
}
