/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest;

import io.github.carlos_emr.carlos.webserv.SoapTokenService;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Session-authenticated revocation; the OAuth policy explicitly blocks this account-security operation. */
@Component
@Path("/soapTokens")
@jakarta.ws.rs.Consumes("*/*")
@jakarta.ws.rs.Produces("*/*")
public class SoapTokenResource extends AbstractServiceImpl {
    @Autowired private SoapTokenService tokens;
    @org.springframework.beans.factory.annotation.Value("${soap.token.revoke.origin:}")
    private String configuredRevocationOrigin = "";
    private java.net.URI allowedOrigin;

    @jakarta.annotation.PostConstruct
    public void configureRevocationOrigin() {
        allowedOrigin = null;
        if (configuredRevocationOrigin == null || configuredRevocationOrigin.isEmpty()) return;
        try { allowedOrigin = parseOrigin(configuredRevocationOrigin); }
        catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("soap.token.revoke.origin must be one absolute HTTP(S) origin without a path");
        }
    }

    @DELETE
    public Response revoke(@QueryParam("securityId") Integer securityId,
            @jakarta.ws.rs.HeaderParam("X-Carlos-Token-Action") String intent,
            @jakarta.ws.rs.HeaderParam("Origin") String origin) {
        // The existing /ws/* CSRF exemption also covers session REST. Require explicit non-form intent
        // and reject cross-origin browser requests even if a deployment adds permissive CORS headers.
        if (!"revoke".equals(intent)) throw new jakarta.ws.rs.BadRequestException("Explicit token revocation intent is required");
        if (origin != null && !sameOrigin(origin)) throw new jakarta.ws.rs.ForbiddenException();
        var actor = getLoggedInInfo();
        if (actor == null || actor.getLoggedInSecurity() == null) {
            throw new SecurityException("SOAP token revocation is not authorized");
        }
        Integer target = securityId == null ? actor.getLoggedInSecurity().getId() : securityId;
        tokens.revokeAll(actor, target);
        return Response.noContent().build();
    }
    private boolean sameOrigin(String origin) {
        if (allowedOrigin == null) return false;
        try {
            java.net.URI uri = parseOrigin(origin);
            return io.github.carlos_emr.carlos.utility.AsciiStrings.equalsIgnoreCase(uri.getHost(), allowedOrigin.getHost())
                    && io.github.carlos_emr.carlos.utility.AsciiStrings.equalsIgnoreCase(uri.getScheme(), allowedOrigin.getScheme())
                    && effectivePort(uri) == effectivePort(allowedOrigin);
        } catch (IllegalArgumentException invalid) { return false; }
    }

    private static java.net.URI parseOrigin(String value) {
        java.net.URI uri = java.net.URI.create(value);
        if (uri.getHost() == null || !(io.github.carlos_emr.carlos.utility.AsciiStrings.equalsIgnoreCase("https", uri.getScheme())
                || io.github.carlos_emr.carlos.utility.AsciiStrings.equalsIgnoreCase("http", uri.getScheme()))
                || uri.getPort() == 0 || uri.getPort() > 65535 || uri.getPort() < -1
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())) {
            throw new IllegalArgumentException("Invalid origin");
        }
        return uri;
    }
    private static int effectivePort(java.net.URI uri) {
        return uri.getPort() == -1 ? (io.github.carlos_emr.carlos.utility.AsciiStrings.equalsIgnoreCase("https", uri.getScheme())
                ? 443 : 80) : uri.getPort();
    }
}
