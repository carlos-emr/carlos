/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest;

import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.webserv.SoapTokenService;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("integration")
@Tag("rest")
class SoapTokenResourceEndpointTest extends CarlosRestTestBase {
    @Mock private SoapTokenService tokens;

    @Override
    protected Object getServiceBean() {
        var resource = new SoapTokenResource(tokens, "http://localhost");
        resource.configureRevocationOrigin();
        return resource;
    }

    @Override
    protected List<Object> additionalServerProviders() {
        return List.of(new io.github.carlos_emr.carlos.webserv.rest.exceptionMapping.SecurityExceptionMapper(),
                new io.github.carlos_emr.carlos.webserv.rest.exceptionMapping.GeneralExceptionMapper());
    }

    @BeforeEach
    void setup() {
        var security = new Security();
        security.setId(41);
        when(mockLoggedInInfo.getLoggedInSecurity()).thenReturn(security);
    }

    @Test
    void shouldRevokeTheAuthenticatedAccount_whenIntentIsExplicit() {
        try (Response response = request().path("/soapTokens").header("X-Carlos-Token-Action", "revoke").header("Origin", "http://localhost").delete()) {
            assertThat(response.getStatus()).isEqualTo(204);
        }
        verify(tokens).revokeAll(mockLoggedInInfo, 41);
    }

    @Test
    void shouldDelegateAdministrativeTargets_toTheSameManagerAuthorization() {
        doThrow(new SecurityException("denied")).when(tokens).revokeAll(mockLoggedInInfo, 42);
        try (Response response = request().path("/soapTokens").query("securityId", 42)
                .header("X-Carlos-Token-Action", "revoke").delete()) {
            assertThat(response.getStatus()).isEqualTo(403);
        }
        verify(tokens).revokeAll(mockLoggedInInfo, 42);
    }

    @Test
    void shouldRefuseImplicitOrCrossOriginRequests_beforeAnyRevocation() {
        try (Response response = request().path("/soapTokens").delete()) {
            assertThat(response.getStatus()).isEqualTo(400);
        }
        for (String origin : List.of("https://example.invalid", "null", "http://localhost/path", "http://localhost:81", "http://user@localhost", "http://localhost?x=y", "http://localhost#x", "http://localhost:0", "http://localhost:65536")) {
            try (Response response = request().path("/soapTokens").header("X-Carlos-Token-Action", "revoke")
                    .header("Origin", origin).delete()) {
                assertThat(response.getStatus()).isEqualTo(403);
            }
        }
        verifyNoInteractions(tokens);
    }
    @Test
    void shouldIgnoreClientControlledHost_whenCheckingRevocationOrigin() {
        try (Response response = request().path("/soapTokens").header("X-Carlos-Token-Action", "revoke")
                .header("Host", "attacker.invalid").header("Origin", "http://attacker.invalid").delete()) {
            assertThat(response.getStatus()).isEqualTo(403);
        }
        verifyNoInteractions(tokens);
    }

    @Test
    void shouldAcceptCanonicalOriginCaseAndDefaultPort_withoutUsingProxyHeaders() {
        try (Response response = request().path("/soapTokens").header("X-Carlos-Token-Action", "revoke")
                .header("Origin", "HTTP://LOCALHOST:80").header("X-Forwarded-Host", "attacker.invalid").delete()) {
            assertThat(response.getStatus()).isEqualTo(204);
        }
        verify(tokens).revokeAll(mockLoggedInInfo, 41);
    }

    @Test
    void shouldRejectMalformedConfiguredOrigins_explicitly() {
        for (String value : List.of("null", "https://localhost/", "https://user@localhost", "https://localhost?x=y")) {
            SoapTokenResource resource = new SoapTokenResource(tokens, value);
            org.assertj.core.api.Assertions.assertThatThrownBy(resource::configureRevocationOrigin)
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("soap.token.revoke.origin");
        }
    }

    @Test
    void shouldRefuseBrowserOrigins_whenNoTrustedOriginIsConfigured() {
        SoapTokenResource resource = new SoapTokenResource(tokens, "");
        resource.configureRevocationOrigin();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> resource.revoke(null, "revoke", "http://localhost"))
                .isInstanceOf(jakarta.ws.rs.ForbiddenException.class);
        verifyNoInteractions(tokens);
    }

}
