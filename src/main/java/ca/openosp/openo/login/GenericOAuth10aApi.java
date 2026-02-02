package ca.openosp.openo.login;

import com.github.scribejava.core.builder.api.DefaultApi10a;

/**
 * Generic OAuth 1.0a API implementation for healthcare provider authentication.
 *
 * This class provides a configurable OAuth 1.0a authentication implementation
 * using the ScribeJava library. It supports healthcare system integration by
 * allowing dynamic configuration of OAuth endpoints via a base URL parameter.
 * This is part of OpenO EMR's migration from CXF OAuth2 to ScribeJava OAuth 1.0a
 * for improved provider credential management and facility integration.
 *
 * <p>The class extends {@link DefaultApi10a} and implements the three required
 * OAuth 1.0a endpoints: request token, access token, and authorization. All
 * endpoints are constructed by appending standard OAuth paths to the configured
 * base URL.</p>
 *
 * @since 2026-01-24
 * @see DefaultApi10a
 * @see ca.openosp.openo.login.OscarOAuthDataProvider
 */
public class GenericOAuth10aApi extends DefaultApi10a {
    private final String baseUrl;

    /**
     * Constructs a new GenericOAuth10aApi with the specified base URL.
     *
     * @param baseUrl String the base URL for OAuth endpoints (e.g., "https://emr.example.com").
     *                Must not include trailing slash. All OAuth endpoints will be constructed
     *                by appending standard paths to this base URL.
     */
    public GenericOAuth10aApi(String baseUrl) { this.baseUrl = baseUrl; }

    /**
     * Returns the OAuth 1.0a request token endpoint URL.
     *
     * This endpoint is used in the first step of the OAuth 1.0a three-legged
     * authentication flow to obtain a temporary request token. The request token
     * is exchanged for user authorization before obtaining the final access token.
     *
     * @return String the full URL to the request token endpoint, constructed as
     *         baseUrl + "/oauth/request_token"
     */
    @Override
    public String getRequestTokenEndpoint() {
        return baseUrl + "/oauth/request_token";
    }

    /**
     * Returns the OAuth 1.0a access token endpoint URL.
     *
     * This endpoint is used in the final step of the OAuth 1.0a authentication
     * flow to exchange an authorized request token for a long-lived access token.
     * The access token is used to authenticate subsequent API requests for healthcare
     * provider operations within OpenO EMR.
     *
     * @return String the full URL to the access token endpoint, constructed as
     *         baseUrl + "/oauth/access_token"
     */
    @Override
    public String getAccessTokenEndpoint() {
        return baseUrl + "/oauth/access_token";
    }

    /**
     * Returns the OAuth 1.0a authorization base URL.
     *
     * This endpoint is used in the second step of the OAuth 1.0a authentication
     * flow where the user is redirected to authorize the application's access.
     * Healthcare providers are presented with a consent screen to approve access
     * to their EMR data and facilities before the request token can be exchanged
     * for an access token.
     *
     * @return String the full URL to the authorization endpoint, constructed as
     *         baseUrl + "/oauth/authorize"
     */
    @Override
    public String getAuthorizationBaseUrl() {
        return baseUrl + "/oauth/authorize";
    }
}
