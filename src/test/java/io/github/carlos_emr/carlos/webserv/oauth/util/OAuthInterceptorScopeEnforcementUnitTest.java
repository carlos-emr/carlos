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

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ServiceAccessToken;
import io.github.carlos_emr.carlos.login.OscarOAuthDataProvider;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
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

    private static final String LEGACY_ACCESS_PROPERTY = "oauth.scope.legacy.access";

    private String previousEnforcementValue;
    private String previousLegacyAccessValue;

    @BeforeEach
    void captureEnforcementFlag() {
        previousEnforcementValue = CarlosProperties.getInstance().getProperty(ENFORCEMENT_PROPERTY, null);
        previousLegacyAccessValue = CarlosProperties.getInstance().getProperty(LEGACY_ACCESS_PROPERTY, null);
    }

    @AfterEach
    void restoreEnforcementFlag() {
        // Restore (not just remove) so this test cannot clobber a pre-existing value in the shared singleton.
        restore(ENFORCEMENT_PROPERTY, previousEnforcementValue);
        restore(LEGACY_ACCESS_PROPERTY, previousLegacyAccessValue);
    }

    private static void restore(String key, String value) {
        if (value == null) {
            CarlosProperties.getInstance().remove(key);
        } else {
            CarlosProperties.getInstance().setProperty(key, value);
        }
    }

    private void enableEnforcement() {
        CarlosProperties.getInstance().setProperty(ENFORCEMENT_PROPERTY, "true");
    }

    private void legacyMode(String access) {
        CarlosProperties.getInstance().setProperty(ENFORCEMENT_PROPERTY, "false");
        CarlosProperties.getInstance().setProperty(LEGACY_ACCESS_PROPERTY, access);
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
    @DisplayName("should warn the operator once per client about a token with no granted scopes")
    void shouldWarnOnce_whenScopelessTokenIsRefusedRepeatedly() {
        enableEnforcement();
        OAuthInterceptor interceptor = interceptorWith(accessToken(null));

        try (LogCapture capture = LogCapture.forLogger(OAuthInterceptor.class)) {
            for (int i = 0; i < 3; i++) {
                Message message = scheduleReadRequest();
                assertThat(catchThrowableOfType(() -> interceptor.handleMessage(message), Fault.class)).isNotNull();
            }

            assertThat(capture.messages())
                    .filteredOn(m -> m.contains("no granted scopes"))
                    .singleElement()
                    .satisfies(m -> assertThat(m).contains(CONSUMER_KEY).contains(ENFORCEMENT_PROPERTY));
        }
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
    @DisplayName("should admit the request without enforcement when an operator opened full legacy access")
    void shouldAdmitRequest_whenEnforcementDisabled() {
        // Only an explicit off value disables enforcement (#4419), and only an explicit full legacy access
        // admits an endpoint outside the legacy list. A narrow/irrelevant scope is then admitted.
        legacyMode("full");
        OAuthInterceptor interceptor = interceptorWith(authenticatedTokenGranting("tickler.read"));
        MockHttpServletRequest request = scheduleReadServletRequest();
        Message message = messageWith(request);

        interceptor.handleMessage(message);

        Object attached = request.getAttribute(new LoggedInInfo().getLoggedInInfoKey());
        assertThat(attached).isInstanceOf(LoggedInInfo.class);
    }

    @Test
    @DisplayName("should honour a multi-scope grant stored still percent-encoded")
    void shouldAdmitRequest_whenStoredScopesArePercentEncoded() {
        // Tokens issued before /initiate decoded scopes hold "a.read%20b.read" as one string.
        enableEnforcement();
        OAuthInterceptor interceptor = interceptorWith(authenticatedTokenGranting("tickler.read%20schedule.read"));
        MockHttpServletRequest request = scheduleReadServletRequest();

        interceptor.handleMessage(messageWith(request));

        assertThat(request.getAttribute(new LoggedInInfo().getLoggedInInfoKey())).isInstanceOf(LoggedInInfo.class);
    }

    @Test
    @DisplayName("should enforce scopes with HTTP 403 when the flag is absent")
    void shouldRaiseFault_withHttp403WhenFlagAbsent() {
        // #4419: enforcement is on by default; an absent property must not reopen full access.
        CarlosProperties.getInstance().remove(ENFORCEMENT_PROPERTY);
        OAuthInterceptor interceptor = interceptorWith(authenticatedTokenGranting("tickler.read"));

        Fault fault = catchThrowableOfType(() -> interceptor.handleMessage(scheduleReadRequest()), Fault.class);

        assertThat(fault).isNotNull();
        assertThat(fault.getStatusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("should refuse with HTTP 403 a root that has no scope decision")
    void shouldRaiseFault_withHttp403ForUnmappedRoot() {
        enableEnforcement();
        OAuthInterceptor interceptor = interceptorWith(authenticatedTokenGranting("tickler.write schedule.write"));
        MockHttpServletRequest request = servletRequest("POST", "/services/notyetmapped/do");

        Fault fault = catchThrowableOfType(() -> interceptor.handleMessage(messageWith(request)), Fault.class);

        assertThat(fault).isNotNull();
        assertThat(fault.getStatusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("should refuse a write reached through a .json extension mapping without the write scope")
    void shouldRaiseFault_withHttp403ForExtensionMappedWrite() {
        // CXF routes /services/tickler.json to /tickler; before #4419 the root read as "tickler.json",
        // an unmapped root, and needed no scope at all.
        enableEnforcement();
        OAuthInterceptor interceptor = interceptorWith(authenticatedTokenGranting("tickler.read"));
        MockHttpServletRequest request = servletRequest("POST", "/services/tickler.json");

        Fault fault = catchThrowableOfType(() -> interceptor.handleMessage(messageWith(request)), Fault.class);

        assertThat(fault).isNotNull();
        assertThat(fault.getStatusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("should admit an extension-mapped read POST with the read scope")
    void shouldAdmitRequest_forExtensionMappedReadPost() {
        enableEnforcement();
        OAuthInterceptor interceptor = interceptorWith(authenticatedTokenGranting("tickler.read"));
        MockHttpServletRequest request = servletRequest("POST", "/services/tickler/search.json");

        interceptor.handleMessage(messageWith(request));

        assertThat(request.getAttribute(new LoggedInInfo().getLoggedInInfoKey())).isInstanceOf(LoggedInInfo.class);
    }

    @Test
    @DisplayName("should require the write scope for an extension-mapped POST carrying a matrix parameter")
    void shouldRaiseFault_withHttp403ForExtensionMappedPostWithMatrixParameter() {
        // CXF does not strip the extension here, so the routed operation is ambiguous; the stricter answer wins.
        enableEnforcement();
        OAuthInterceptor interceptor = interceptorWith(authenticatedTokenGranting("tickler.read"));
        MockHttpServletRequest request = servletRequest("POST", "/services/tickler/search.json");
        request.setRequestURI("/carlos/ws/services/tickler;x=1/search.json");

        Fault fault = catchThrowableOfType(() -> interceptor.handleMessage(messageWith(request)), Fault.class);

        assertThat(fault).isNotNull();
        assertThat(fault.getStatusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("should admit the scope-exempt oauth info endpoint with any valid token")
    void shouldAdmitRequest_forScopeExemptOauthInfo() {
        enableEnforcement();
        OAuthInterceptor interceptor = interceptorWith(authenticatedTokenGranting("tickler.read"));
        MockHttpServletRequest request = servletRequest("GET", "/services/oauth/info");

        interceptor.handleMessage(messageWith(request));

        assertThat(request.getAttribute(new LoggedInInfo().getLoggedInInfoKey())).isInstanceOf(LoggedInInfo.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "GET,getSettings,tickler.read,false",
        "GET,getSettings,ocean.read,true",
        "GET,getSettings,ocean.write,true",
        "POST,saveSettings,ocean.read,false",
        "POST,saveSettings,ocean.write,true"
    })
    void shouldEnforceOceanScopes_whenOceanEndpointRequested(String method, String operation, String scope, boolean allowed) {
        enableEnforcement();
        OAuthInterceptor interceptor = interceptorWith(authenticatedTokenGranting(scope));
        MockHttpServletRequest request = scheduleReadServletRequest();
        request.setMethod(method);
        request.setRequestURI("/carlos/ws/services/ocean/" + operation);
        request.setPathInfo("/services/ocean/" + operation);
        Fault fault = catchThrowableOfType(() -> interceptor.handleMessage(messageWith(request)), Fault.class);
        if (allowed) {
            assertThat(fault).isNull();
            assertThat(request.getAttribute(new LoggedInInfo().getLoggedInInfoKey())).isInstanceOf(LoggedInInfo.class);
        } else {
            assertThat(fault).isNotNull();
            assertThat(fault.getStatusCode()).isEqualTo(403);
        }
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

    private static MockHttpServletRequest servletRequest(String method, String pathInfo) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/carlos/ws" + pathInfo);
        request.setPathInfo(pathInfo);
        request.addParameter("oauth_consumer_key", CONSUMER_KEY);
        request.addParameter("oauth_token", TOKEN);
        return request;
    }

    private static Message messageWith(MockHttpServletRequest request) {
        Message message = new MessageImpl();
        message.put(AbstractHTTPDestination.HTTP_REQUEST, request);
        return message;
    }

    @Test
    @DisplayName("should refuse an always-blocked endpoint even with full legacy access")
    void shouldRaiseFault_withHttp403ForBlockedEndpointUnderFullLegacyAccess() {
        legacyMode("full");
        OAuthInterceptor interceptor = interceptorWith(accessToken(null));
        MockHttpServletRequest request = servletRequest("GET", "/services/jobs/all");

        Fault fault = catchThrowableOfType(() -> interceptor.handleMessage(messageWith(request)), Fault.class);

        assertThat(fault).isNotNull();
        assertThat(fault.getStatusCode()).isEqualTo(403);
        assertThat(fault.getCause()).hasMessage("blocked_endpoint");
        assertThat(request.getAttribute(new LoggedInInfo().getLoggedInInfoKey())).isNull();
    }

    @Test
    @DisplayName("should refuse an always-blocked endpoint even when the token holds the matching scope")
    void shouldRaiseFault_withHttp403ForBlockedEndpointDespiteScope() {
        enableEnforcement();
        OAuthInterceptor interceptor = interceptorWith(authenticatedTokenGranting("persona.write provider.write"));
        for (String path : new String[] {"/services/persona/rights", "/services/providerService/settings/999998/save"}) {
            MockHttpServletRequest request = servletRequest("GET", path);

            Fault fault = catchThrowableOfType(() -> interceptor.handleMessage(messageWith(request)), Fault.class);

            assertThat(fault).as(path).isNotNull();
            assertThat(fault.getCause()).as(path).hasMessage("blocked_endpoint");
        }
    }

    @Test
    @DisplayName("should admit any ordinary endpoint to a scopeless token under full legacy access")
    void shouldAdmitRequest_underFullLegacyAccess() {
        legacyMode("full");
        OAuthInterceptor interceptor = interceptorWith(accessToken(null));
        MockHttpServletRequest request = servletRequest("GET", "/services/tickler/mine");

        interceptor.handleMessage(messageWith(request));

        assertThat(request.getAttribute(new LoggedInInfo().getLoggedInInfoKey())).isInstanceOf(LoggedInInfo.class);
    }

    @Test
    @DisplayName("should admit the Cortico calls to a scopeless token under restricted legacy access")
    void shouldAdmitCorticoCalls_underRestrictedLegacyAccess() {
        legacyMode("restricted");
        OAuthInterceptor interceptor = interceptorWith(accessToken(null));
        String[][] calls = {
            {"POST", "/services/demographics/"}, {"PUT", "/services/demographics/"},
            {"GET", "/services/demographics/12345"}, {"POST", "/services/document/saveDocumentToDemographic/"},
            {"GET", "/services/oauth/info"},
        };
        for (String[] call : calls) {
            MockHttpServletRequest request = servletRequest(call[0], call[1]);

            interceptor.handleMessage(messageWith(request));

            assertThat(request.getAttribute(new LoggedInInfo().getLoggedInInfoKey()))
                    .as("%s %s", call[0], call[1]).isInstanceOf(LoggedInInfo.class);
        }
    }

    @Test
    @DisplayName("should refuse everything outside the Cortico calls under restricted legacy access, scopes or not")
    void shouldRaiseFault_withHttp403OutsideCorticoCallsUnderRestrictedLegacyAccess() {
        legacyMode("restricted");
        // Scopes are not consulted in this mode: a tickler.write grant does not open tickler.
        OAuthInterceptor interceptor = interceptorWith(authenticatedTokenGranting("tickler.write demographic.write"));
        String[][] calls = {
            {"GET", "/services/tickler/mine"}, {"DELETE", "/services/demographics/12345"},
            {"POST", "/services/demographics/search"}, {"GET", "/services/demographics/"},
        };
        for (String[] call : calls) {
            MockHttpServletRequest request = servletRequest(call[0], call[1]);

            Fault fault = catchThrowableOfType(() -> interceptor.handleMessage(messageWith(request)), Fault.class);

            assertThat(fault).as("%s %s", call[0], call[1]).isNotNull();
            assertThat(fault.getStatusCode()).as("%s %s", call[0], call[1]).isEqualTo(403);
            assertThat(fault.getCause()).as("%s %s", call[0], call[1]).hasMessage("restricted_endpoint");
        }
    }

    @Test
    @DisplayName("should restrict legacy access by default when enforcement is merely turned off")
    void shouldRestrictLegacyAccess_whenOnlyEnforcementIsOff() {
        CarlosProperties.getInstance().setProperty(ENFORCEMENT_PROPERTY, "false");
        CarlosProperties.getInstance().remove(LEGACY_ACCESS_PROPERTY);
        OAuthInterceptor interceptor = interceptorWith(accessToken(null));
        MockHttpServletRequest request = servletRequest("GET", "/services/tickler/mine");

        Fault fault = catchThrowableOfType(() -> interceptor.handleMessage(messageWith(request)), Fault.class);

        assertThat(fault).isNotNull();
        assertThat(fault.getCause()).hasMessage("restricted_endpoint");
    }
}
