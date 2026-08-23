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
package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.webserv.oauth.Client;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1Exception;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1Request;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1SignatureVerifier;
import io.github.carlos_emr.carlos.webserv.oauth.RequestToken;
import io.github.carlos_emr.carlos.webserv.oauth.util.OAuth1ParamParser;

import jakarta.ws.rs.core.Response;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the {@code /ws/oauth/initiate} scope vocabulary check (issue #3083): when enforcement is enabled an
 * empty or unknown scope is rejected with HTTP 400 before any token is persisted, while a known scope is
 * accepted and the historical lenient behaviour is preserved when the flag is off.
 */
@DisplayName("OscarRequestTokenService /initiate scope validation")
@Tag("unit")
@Tag("security")
class OscarRequestTokenServiceScopeValidationUnitTest {

    private static final String ENFORCEMENT_PROPERTY = "oauth.scope.enforcement.enabled";
    private static final String CONSUMER_KEY = "consumer-key";

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
    @DisplayName("should reject with HTTP 400 when an unknown scope is requested and enforcement is on")
    void shouldReject_whenUnknownScopeRequested() {
        enableEnforcement();
        OscarOAuthDataProvider dataProvider = mock(OscarOAuthDataProvider.class);
        OscarRequestTokenService service = serviceFor("totally_bogus_zzz", dataProvider);

        OAuth1Exception ex = catchThrowableOfType(
                () -> service.initiatePost(new MockHttpServletRequest()), OAuth1Exception.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getHttpCode()).isEqualTo(400);
        // The arbitrary scope must be rejected before any token is persisted.
        verify(dataProvider, never()).createRequestToken(any());
    }

    @Test
    @DisplayName("should reject with HTTP 400 when no scope is requested and enforcement is on")
    void shouldReject_whenNoScopeRequested() {
        enableEnforcement();
        OscarOAuthDataProvider dataProvider = mock(OscarOAuthDataProvider.class);
        OscarRequestTokenService service = serviceFor(null, dataProvider);

        OAuth1Exception ex = catchThrowableOfType(
                () -> service.initiatePost(new MockHttpServletRequest()), OAuth1Exception.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getHttpCode()).isEqualTo(400);
        // An empty scope set must be rejected before any token is persisted.
        verify(dataProvider, never()).createRequestToken(any());
    }

    @Test
    @DisplayName("should issue a request token when a known scope is requested and enforcement is on")
    void shouldIssueToken_whenKnownScopeRequested() {
        enableEnforcement();
        OscarOAuthDataProvider dataProvider = mock(OscarOAuthDataProvider.class);
        OscarRequestTokenService service = serviceFor("schedule.read", dataProvider);

        Response response = service.initiatePost(new MockHttpServletRequest());

        assertThat(response.getStatus()).isEqualTo(200);
        verify(dataProvider).createRequestToken(any());
    }

    @Test
    @DisplayName("should accept any scope when enforcement is disabled")
    void shouldAccept_whenEnforcementDisabled() {
        // Flag left unset (default). An unknown scope must still be accepted for backwards compatibility.
        OscarOAuthDataProvider dataProvider = mock(OscarOAuthDataProvider.class);
        OscarRequestTokenService service = serviceFor("totally_bogus_zzz", dataProvider);

        Response response = service.initiatePost(new MockHttpServletRequest());

        assertThat(response.getStatus()).isEqualTo(200);
        verify(dataProvider).createRequestToken(any());
    }

    /**
     * Builds a service whose parser yields a request carrying {@code scopesCsv} and an out-of-band callback,
     * with a known client and a passing signature so validation is the only gate exercised.
     */
    private OscarRequestTokenService serviceFor(String scopesCsv, OscarOAuthDataProvider dataProvider) {
        when(dataProvider.getClient(CONSUMER_KEY)).thenReturn(mock(Client.class));

        RequestToken requestToken = mock(RequestToken.class);
        when(requestToken.getTokenKey()).thenReturn("request-token");
        when(requestToken.getTokenSecret()).thenReturn("request-token-secret");
        when(dataProvider.createRequestToken(any())).thenReturn(requestToken);

        OAuth1ParamParser parser = mock(OAuth1ParamParser.class);
        OAuth1Request oreq = new OAuth1Request();
        oreq.consumerKey = CONSUMER_KEY;
        oreq.callback = "oob";
        oreq.scopesCsv = scopesCsv;
        when(parser.parseFromRequest(any())).thenReturn(oreq);

        OAuth1SignatureVerifier verifier = mock(OAuth1SignatureVerifier.class);
        when(verifier.verifySignature(any(), any())).thenReturn(null);

        OscarRequestTokenService service = new OscarRequestTokenService(dataProvider, parser);
        ReflectionTestUtils.setField(service, "verifier", verifier);
        return service;
    }
}
