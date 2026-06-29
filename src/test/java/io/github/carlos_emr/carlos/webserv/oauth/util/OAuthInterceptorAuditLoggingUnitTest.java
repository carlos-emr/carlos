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

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.login.AppOAuth1Config;
import io.github.carlos_emr.carlos.login.OscarOAuthDataProvider;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.webserv.oauth.Client;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1SignatureVerifier;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OAuthInterceptor}'s authentication audit logging (parity with the
 * SOAP {@code AuthenticationInWSS4JInterceptor} WS_LOGIN_SUCCESS / WS_LOGIN_FAILURE entries).
 */
@DisplayName("OAuthInterceptor authentication audit logging")
@Tag("unit")
@Tag("security")
class OAuthInterceptorAuditLoggingUnitTest extends CarlosUnitTestBase {

    private static final String CONSUMER_KEY = "consumer-key-123";
    private static final String ACCESS_TOKEN = "access-token-abc";
    private static final String PROVIDER_NO = "999";
    private static final String REMOTE_IP = "203.0.113.7";

    @Mock
    private OscarOAuthDataProvider oauthDataProvider;

    @Mock
    private ProviderDao providerDao;

    @Mock
    private OAuth1SignatureVerifier verifier;

    @Mock
    private Message message;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private OAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(message.get(AbstractHTTPDestination.HTTP_REQUEST)).thenReturn(request);
        when(request.getRemoteAddr()).thenReturn(REMOTE_IP);
        // Marks the request as an OAuth1 request without an Authorization header.
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getHeaders("Authorization")).thenReturn(Collections.emptyEnumeration());
    }

    /** Stubs the request so the real OAuthRequestParser yields the given oauth_* parameters. */
    private void stubOAuthParameters(String consumerKey, String token) {
        Map<String, String[]> params = new HashMap<>();
        if (consumerKey != null) {
            params.put("oauth_consumer_key", new String[] {consumerKey});
            when(request.getParameter("oauth_consumer_key")).thenReturn(consumerKey);
        } else {
            // Still flag this as an OAuth1 request even though the consumer key is absent.
            when(request.getHeader("Authorization")).thenReturn("OAuth oauth_token=\"" + token + "\"");
        }
        if (token != null) {
            params.put("oauth_token", new String[] {token});
        }
        when(request.getParameterMap()).thenReturn(params);
    }

    private OscarLog captureSingleLog() {
        ArgumentCaptor<OscarLog> captor = ArgumentCaptor.forClass(OscarLog.class);
        logActionMock.verify(() -> LogAction.addLogSynchronous(captor.capture()));
        return captor.getValue();
    }

    @Test
    @DisplayName("logs OAUTH_LOGIN_SUCCESS with providerNo, IP and consumer key on success")
    void shouldLogSuccess_whenAuthenticationSucceeds() {
        stubOAuthParameters(CONSUMER_KEY, ACCESS_TOKEN);
        Client client = new Client(CONSUMER_KEY, "secret", "test-app", "http://localhost");
        when(oauthDataProvider.getClient(CONSUMER_KEY)).thenReturn(client);
        when(verifier.verifySignature(eq(request), any(AppOAuth1Config.class))).thenReturn(ACCESS_TOKEN);
        when(oauthDataProvider.getProviderNoByAccessToken(ACCESS_TOKEN)).thenReturn(PROVIDER_NO);
        when(providerDao.getProvider(PROVIDER_NO)).thenReturn(new Provider());

        interceptor.handleMessage(message);

        OscarLog log = captureSingleLog();
        assertThat(log.getAction()).isEqualTo("OAUTH_LOGIN_SUCCESS");
        assertThat(log.getProviderNo()).isEqualTo(PROVIDER_NO);
        assertThat(log.getIp()).isEqualTo(REMOTE_IP);
        assertThat(log.getContent()).isEqualTo(CONSUMER_KEY);
    }

    @Test
    @DisplayName("keeps the request authenticated when the success audit write fails")
    void shouldAuthenticate_whenSuccessAuditWriteFails() {
        stubOAuthParameters(CONSUMER_KEY, ACCESS_TOKEN);
        Client client = new Client(CONSUMER_KEY, "secret", "test-app", "http://localhost");
        when(oauthDataProvider.getClient(CONSUMER_KEY)).thenReturn(client);
        when(verifier.verifySignature(eq(request), any(AppOAuth1Config.class))).thenReturn(ACCESS_TOKEN);
        when(oauthDataProvider.getProviderNoByAccessToken(ACCESS_TOKEN)).thenReturn(PROVIDER_NO);
        when(providerDao.getProvider(PROVIDER_NO)).thenReturn(new Provider());
        // The audit write blows up (e.g. the audit store is unavailable).
        logActionMock.when(() -> LogAction.addLogSynchronous(any(OscarLog.class)))
            .thenThrow(new RuntimeException("audit store unavailable"));

        // The already-authenticated request must not be denied: no Fault propagates and the
        // LoggedInInfo is still attached for downstream endpoints.
        assertThatCode(() -> interceptor.handleMessage(message)).doesNotThrowAnyException();
        verify(request).setAttribute(anyString(), any());
    }

    @Test
    @DisplayName("logs OAUTH_LOGIN_FAILURE with IP but no providerNo when the consumer is invalid")
    void shouldLogFailure_whenConsumerIsInvalid() {
        stubOAuthParameters(CONSUMER_KEY, ACCESS_TOKEN);
        when(oauthDataProvider.getClient(CONSUMER_KEY)).thenReturn(null);

        assertThatThrownBy(() -> interceptor.handleMessage(message)).isInstanceOf(Fault.class);

        OscarLog log = captureSingleLog();
        assertThat(log.getAction()).isEqualTo("OAUTH_LOGIN_FAILURE");
        assertThat(log.getIp()).isEqualTo(REMOTE_IP);
        assertThat(log.getProviderNo()).isNull();
        assertThat(log.getContent()).isEqualTo(CONSUMER_KEY);
    }

    @Test
    @DisplayName("logs OAUTH_LOGIN_FAILURE with no consumer key when the consumer key is missing")
    void shouldLogFailure_whenConsumerKeyMissing() {
        stubOAuthParameters(null, ACCESS_TOKEN);

        assertThatThrownBy(() -> interceptor.handleMessage(message)).isInstanceOf(Fault.class);

        OscarLog log = captureSingleLog();
        assertThat(log.getAction()).isEqualTo("OAUTH_LOGIN_FAILURE");
        assertThat(log.getIp()).isEqualTo(REMOTE_IP);
        assertThat(log.getContent()).isNull();
    }

    @Test
    @DisplayName("logs OAUTH_LOGIN_FAILURE when signature verification rejects the request")
    void shouldLogFailure_whenSignatureRejected() {
        stubOAuthParameters(CONSUMER_KEY, ACCESS_TOKEN);
        Client client = new Client(CONSUMER_KEY, "secret", "test-app", "http://localhost");
        when(oauthDataProvider.getClient(CONSUMER_KEY)).thenReturn(client);
        when(verifier.verifySignature(eq(request), any(AppOAuth1Config.class)))
            .thenThrow(new IllegalArgumentException("bad signature"));

        assertThatThrownBy(() -> interceptor.handleMessage(message)).isInstanceOf(Fault.class);

        OscarLog log = captureSingleLog();
        assertThat(log.getAction()).isEqualTo("OAUTH_LOGIN_FAILURE");
        assertThat(log.getIp()).isEqualTo(REMOTE_IP);
    }

    @Test
    @DisplayName("does not record an auth failure for an unexpected server error")
    void shouldNotLog_onUnexpectedServerError() {
        stubOAuthParameters(CONSUMER_KEY, ACCESS_TOKEN);
        Client client = new Client(CONSUMER_KEY, "secret", "test-app", "http://localhost");
        when(oauthDataProvider.getClient(CONSUMER_KEY)).thenReturn(client);
        when(verifier.verifySignature(eq(request), any(AppOAuth1Config.class)))
            .thenThrow(new RuntimeException("database down"));

        assertThatThrownBy(() -> interceptor.handleMessage(message)).isInstanceOf(Fault.class);

        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("audits a login failure and rejects a request that is not an OAuth1 request")
    void shouldLogFailure_whenRequestIsNotOAuth1() {
        // No Authorization header (setUp) and no oauth_consumer_key: the OAuth-only surface must
        // fail closed (#2798), auditing the rejection and throwing a 401 rather than passing through.
        when(request.getParameter("oauth_consumer_key")).thenReturn(null);

        assertThatThrownBy(() -> interceptor.handleMessage(message)).isInstanceOf(Fault.class);

        OscarLog log = captureSingleLog();
        assertThat(log.getAction()).isEqualTo("OAUTH_LOGIN_FAILURE");
        assertThat(log.getIp()).isEqualTo(REMOTE_IP);
        assertThat(log.getProviderNo()).isNull();
        assertThat(log.getContent()).isNull();
    }
}
