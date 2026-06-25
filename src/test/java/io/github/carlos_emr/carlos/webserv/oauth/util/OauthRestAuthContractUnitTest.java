/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.webserv.oauth.util;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.login.OscarOAuthDataProvider;
import io.github.carlos_emr.carlos.webserv.oauth.Client;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1Exception;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1SignatureVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Contract tests for the OAuth rejection behavior that guards every native CARLOS REST data API
 * published under {@code /ws/services} (demographics, document, schedule, provider, ...).
 *
 * <p>This proves the native CARLOS auth contract referenced by
 * {@code docs/api/cortico-carlos-compatibility.md}: an unsigned or improperly-credentialed request
 * to an OAuth-protected REST route is rejected with an accurate 400/401 status carried on the CXF
 * {@link Fault}, never silently allowed and never masked as a 500. Enforcement is native CARLOS
 * responsibility; supplying valid signed OAuth requests is the integrating client's responsibility.</p>
 *
 * <p>Fixtures are synthetic: a mock request carries a fabricated {@code Authorization} header. No
 * PHI, real consumer keys, tokens, secrets, or signed payloads are used.</p>
 *
 * @since 2026-06-25
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth REST authentication-failure contract")
@Tag("unit")
@Tag("security")
class OauthRestAuthContractUnitTest {

    @Mock
    private OscarOAuthDataProvider oauthDataProvider;

    @Mock
    private ProviderDao providerDao;

    @Mock
    private OAuth1SignatureVerifier verifier;

    private OAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new OAuthInterceptor();
        ReflectionTestUtils.setField(interceptor, "oauthDataProvider", oauthDataProvider);
        ReflectionTestUtils.setField(interceptor, "providerDao", providerDao);
        ReflectionTestUtils.setField(interceptor, "verifier", verifier);
    }

    @Test
    @DisplayName("should pass non-OAuth1 requests through without authenticating")
    void shouldPassThrough_whenRequestIsNotOAuth1() {
        Message message = messageFor(new MockHttpServletRequest("GET", "/ws/services/demographics/1"));

        try (MockedStatic<LogAction> ignored = mockStatic(LogAction.class)) {
            assertThatCode(() -> interceptor.handleMessage(message)).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("should reject with 400 when the consumer key is missing")
    void shouldReject_whenConsumerKeyMissing() {
        Message message = oauthMessage("OAuth oauth_token=\"access-token\"");

        assertFaultStatus(message, 400, "missing_consumer_key");
    }

    @Test
    @DisplayName("should reject with 400 when the access token is missing")
    void shouldReject_whenAccessTokenMissing() {
        Message message = oauthMessage("OAuth oauth_consumer_key=\"consumer-key\"");

        assertFaultStatus(message, 400, "missing_access_token");
    }

    @Test
    @DisplayName("should reject with 401 when the consumer is unknown")
    void shouldReject_whenConsumerIsUnknown() {
        Message message = oauthMessage("OAuth oauth_consumer_key=\"consumer-key\", oauth_token=\"access-token\"");
        when(oauthDataProvider.getClient("consumer-key")).thenReturn(null);

        assertFaultStatus(message, 401, "invalid_consumer");
    }

    @Test
    @DisplayName("should reject with 401 when the signature does not match the token")
    void shouldReject_whenSignatureDoesNotMatchToken() {
        Message message = oauthMessage("OAuth oauth_consumer_key=\"consumer-key\", oauth_token=\"access-token\"");
        when(oauthDataProvider.getClient("consumer-key")).thenReturn(mock(Client.class));
        when(verifier.verifySignature(any(HttpServletRequest.class), any())).thenReturn("a-different-token");

        assertFaultStatus(message, 401, "invalid_signature");
    }

    // --- helpers -----------------------------------------------------------------------------

    private void assertFaultStatus(Message message, int expectedStatus, String expectedReason) {
        // Audit writes hit the DB; stub them out so the test stays a deterministic unit test.
        try (MockedStatic<LogAction> ignored = mockStatic(LogAction.class)) {
            Throwable thrown = catchThrowable(() -> interceptor.handleMessage(message));

            assertThat(thrown).isInstanceOf(Fault.class);
            Fault fault = (Fault) thrown;
            assertThat(fault.getStatusCode())
                    .as("the OAuth status code is carried on the Fault, not defaulted to 500")
                    .isEqualTo(expectedStatus);
            assertThat(fault.getCause()).isInstanceOf(OAuth1Exception.class);
            OAuth1Exception cause = (OAuth1Exception) fault.getCause();
            assertThat(cause.getHttpCode()).isEqualTo(expectedStatus);
            assertThat(cause.getMessage()).isEqualTo(expectedReason);
        }
    }

    private static Message oauthMessage(String authorizationHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/services/demographics");
        request.addHeader("Authorization", authorizationHeader);
        return messageFor(request);
    }

    private static Message messageFor(HttpServletRequest request) {
        Message message = mock(Message.class);
        when(message.get(AbstractHTTPDestination.HTTP_REQUEST)).thenReturn(request);
        return message;
    }
}
