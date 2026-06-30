/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.webserv.oauth.util;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ServiceAccessToken;
import io.github.carlos_emr.carlos.login.OscarOAuthDataProvider;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.oauth.Client;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1SignatureVerifier;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * Pins OAuth 1.0a scope enforcement in {@link OAuthInterceptor} (issue #3083): a token whose granted
 * scopes do not cover the target endpoint is rejected with HTTP 403, an in-scope token is admitted, and
 * the whole behaviour stays off unless the {@code oauth.scope.enforcement.enabled} flag is set.
 */
@DisplayName("OAuthInterceptor scope enforcement")
@Tag("unit")
@Tag("security")
class OAuthInterceptorScopeEnforcementUnitTest {

    private static final String ENFORCEMENT_PROPERTY = "oauth.scope.enforcement.enabled";
    private static final String CONSUMER_KEY = "consumer-key";
    private static final String TOKEN = "access-token-1";
    private static final String PROVIDER_NO = "999998";
    private static final String SCHEDULE_GET_URI = "/carlos/ws/services/schedule/day/2026-06-29";
    // The interceptor resolves the scope from getPathInfo(), which the container exposes relative to the
    // /ws/* servlet mapping (i.e. /services/<domain>/...), already decoded and canonicalized.
    private static final String SCHEDULE_GET_PATHINFO = "/services/schedule/day/2026-06-29";

    private String previousEnforcementValue;

    @BeforeEach
    void captureEnforcementFlag() {
        previousEnforcementValue = CarlosProperties.getInstance().getProperty(ENFORCEMENT_PROPERTY, null);
    }

    @AfterEach
    void restoreEnforcementFlag() {
        // Restore (not just remove) so this test cannot clobber a pre-existing value in the shared singleton.
        if (previousEnforcementValue == null) {
            CarlosProperties.getInstance().remove(ENFORCEMENT_PROPERTY);
        } else {
            CarlosProperties.getInstance().setProperty(ENFORCEMENT_PROPERTY, previousEnforcementValue);
        }
    }

    private void enableEnforcement() {
        CarlosProperties.getInstance().setProperty(ENFORCEMENT_PROPERTY, "true");
    }

    @Test
    @DisplayName("should raise fault with HTTP 403 when token scope does not cover the endpoint")
    void shouldRaiseFault_withHttp403WhenScopeInsufficient() {
        enableEnforcement();
        OAuthInterceptor interceptor = interceptorWith(authenticatedTokenGranting("tickler.read"));
        Message message = scheduleReadRequest();

        Fault fault = catchThrowableOfType(() -> interceptor.handleMessage(message), Fault.class);

        assertThat(fault).isNotNull();
        assertThat(fault.getStatusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("should fail closed with HTTP 403 when the token has no granted scopes")
    void shouldRaiseFault_withHttp403WhenTokenHasNoScopes() {
        enableEnforcement();
        OAuthInterceptor interceptor = interceptorWith(accessToken(null));  // null persisted scopes
        Message message = scheduleReadRequest();

        Fault fault = catchThrowableOfType(() -> interceptor.handleMessage(message), Fault.class);

        assertThat(fault).isNotNull();
        assertThat(fault.getStatusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("should admit the request when the token carries the required scope")
    void shouldAdmitRequest_whenTokenHasRequiredScope() {
        enableEnforcement();
        OAuthInterceptor interceptor = interceptorWith(authenticatedTokenGranting("schedule.read"));
        MockHttpServletRequest request = scheduleReadServletRequest();
        Message message = messageWith(request);

        interceptor.handleMessage(message);

        Object attached = request.getAttribute(new LoggedInInfo().getLoggedInInfoKey());
        assertThat(attached).isInstanceOf(LoggedInInfo.class);
    }

    @Test
    @DisplayName("should admit the request without enforcement when the flag is off")
    void shouldAdmitRequest_whenEnforcementDisabled() {
        // Flag intentionally left unset (default). A narrow/irrelevant scope must still be admitted.
        OAuthInterceptor interceptor = interceptorWith(authenticatedTokenGranting("tickler.read"));
        MockHttpServletRequest request = scheduleReadServletRequest();
        Message message = messageWith(request);

        interceptor.handleMessage(message);

        Object attached = request.getAttribute(new LoggedInInfo().getLoggedInInfoKey());
        assertThat(attached).isInstanceOf(LoggedInInfo.class);
    }

    /**
     * Builds an interceptor whose collaborators authenticate {@link #TOKEN} successfully (valid client,
     * good signature, resolvable provider) and return the supplied access token from the single token load.
     */
    private OAuthInterceptor interceptorWith(ServiceAccessToken accessToken) {
        OscarOAuthDataProvider dataProvider = mock(OscarOAuthDataProvider.class);
        when(dataProvider.getClient(CONSUMER_KEY)).thenReturn(mock(Client.class));
        when(dataProvider.findUnexpiredAccessToken(TOKEN)).thenReturn(accessToken);

        ProviderDao providerDao = mock(ProviderDao.class);
        when(providerDao.getProvider(PROVIDER_NO)).thenReturn(mock(Provider.class));

        OAuth1SignatureVerifier verifier = mock(OAuth1SignatureVerifier.class);
        when(verifier.verifySignature(any(), any())).thenReturn(TOKEN);

        OAuthInterceptor interceptor = new OAuthInterceptor();
        ReflectionTestUtils.setField(interceptor, "oauthDataProvider", dataProvider);
        ReflectionTestUtils.setField(interceptor, "providerDao", providerDao);
        ReflectionTestUtils.setField(interceptor, "verifier", verifier);
        return interceptor;
    }

    private static ServiceAccessToken authenticatedTokenGranting(String scopes) {
        return accessToken(scopes);
    }

    /** A persisted access token bound to {@link #PROVIDER_NO} with the given space-delimited scopes. */
    private static ServiceAccessToken accessToken(String scopes) {
        ServiceAccessToken sat = new ServiceAccessToken();
        sat.setProviderNo(PROVIDER_NO);
        sat.setScopes(scopes);
        return sat;
    }

    private static Message scheduleReadRequest() {
        return messageWith(scheduleReadServletRequest());
    }

    private static MockHttpServletRequest scheduleReadServletRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", SCHEDULE_GET_URI);
        request.setPathInfo(SCHEDULE_GET_PATHINFO);
        request.addParameter("oauth_consumer_key", CONSUMER_KEY);
        request.addParameter("oauth_token", TOKEN);
        return request;
    }

    private static Message messageWith(MockHttpServletRequest request) {
        Message message = new MessageImpl();
        message.put(AbstractHTTPDestination.HTTP_REQUEST, request);
        return message;
    }
}
