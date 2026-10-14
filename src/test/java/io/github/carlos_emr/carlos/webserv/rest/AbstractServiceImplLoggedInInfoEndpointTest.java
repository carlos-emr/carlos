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
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.Map;
import org.springframework.mock.web.MockHttpSession;

/**
 * Pins which principal {@link AbstractServiceImpl#getLoggedInInfo()} hands to the REST data
 * services on each surface.
 *
 * <p>The session surface ({@code /ws/rs}) authenticates the browser session, so the principal is in
 * the session. The OAuth surface ({@code /ws/services}) authenticates a signed access token in
 * {@code OAuthInterceptor}, which puts the principal on the request. That caller has no session.
 * Before issue #3446 the lookup read the session first and only fell back to the request when the
 * session held a principal without a provider. An OAuth caller holds none, so every OAuth data call
 * through this base class failed with "Authentication info is not available" (HTTP 500), and each
 * one created a session.
 *
 * @since 2026-10-08
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("AbstractServiceImpl principal lookup")
class AbstractServiceImplLoggedInInfoEndpointTest extends CarlosRestTestBase {

    private static final String LOGGED_IN_INFO_KEY = new LoggedInInfo().LOGGED_IN_INFO_KEY;

    /** Minimal data service that reports the provider it was handed. */
    @Path("/whoami")
    public static class WhoAmIService extends AbstractServiceImpl {
        @GET
        @Produces(MediaType.APPLICATION_JSON)
        public Map<String, String> whoAmI() {
            return Map.of("providerNo", getLoggedInInfo().getLoggedInProviderNo());
        }
    }

    @Override
    protected Object getServiceBean() {
        return new WhoAmIService();
    }

    @BeforeEach
    void clearBasePrincipals() {
        // The base class puts the same mock in both places; each test sets its own shape.
        mockServletRequest.removeAttribute(LOGGED_IN_INFO_KEY);
        mockServletRequest.setSession(null);
    }

    @Test
    @DisplayName("should use the request principal when an OAuth call has no session")
    void shouldUseRequestPrincipal_whenOAuthCallHasNoSession() {
        LoggedInInfo.setLoggedInInfoIntoRequest(mockServletRequest, principal("999998"));

        try (var response = request().path("/whoami").get()) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(responseJson(response).path("providerNo").asText()).isEqualTo("999998");
        }
        assertThat(mockServletRequest.getSession(false))
                .as("an OAuth data call must not create a session")
                .isNull();
    }

    @Test
    @DisplayName("should use the session principal when a session call has no request principal")
    void shouldUseSessionPrincipal_whenSessionCallHasNoRequestPrincipal() {
        MockHttpSession session = new MockHttpSession();
        LoggedInInfo.setLoggedInInfoIntoSession(session, principal("111"));
        mockServletRequest.setSession(session);

        try (var response = request().path("/whoami").get()) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(responseJson(response).path("providerNo").asText()).isEqualTo("111");
        }
    }

    @Test
    @DisplayName("should prefer the OAuth request principal over a browser session")
    void shouldPreferRequestPrincipal_whenSessionAlsoHasOne() {
        // The signed access token is what this call proved. A session cookie riding along must not
        // swap in a different provider.
        MockHttpSession session = new MockHttpSession();
        LoggedInInfo.setLoggedInInfoIntoSession(session, principal("111"));
        mockServletRequest.setSession(session);
        LoggedInInfo.setLoggedInInfoIntoRequest(mockServletRequest, principal("999998"));

        try (var response = request().path("/whoami").get()) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(responseJson(response).path("providerNo").asText()).isEqualTo("999998");
        }
    }

    @Test
    @DisplayName("should refuse when neither the request nor a session carries a principal")
    void shouldRefuse_whenNoPrincipalIsPresent() {
        // On a deployment both surfaces' interceptors refuse such a call first (401); this is the
        // last line of defence. The local transport's direct dispatch hands the service's
        // exception back to the client instead of a status.
        assertThatThrownBy(() -> request().path("/whoami").get().close())
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Authentication info is not available.");
        assertThat(mockServletRequest.getSession(false)).isNull();
    }

    private static LoggedInInfo principal(String providerNo) {
        Provider provider = new Provider();
        provider.setProviderNo(providerNo);
        LoggedInInfo info = new LoggedInInfo();
        info.setLoggedInProvider(provider);
        return info;
    }
}
