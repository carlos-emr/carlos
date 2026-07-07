/**
 * File: AuthorizeResource.java
 *
 * Purpose:
 *   Implements the OAuth 1.0a "authorize" endpoint (Step 2 of the flow).
 *   Provides the consent page for resource owners and issues/verifies
 *   the oauth_verifier value required to exchange a request token
 *   for an access token.
 *
 * Responsibilities:
 *   • GET  /ws/oauth/authorize: Display consent UI (3rdpartyLogin.jsp),
 *     pre-populated with client and requested scopes, and stage a
 *     one-time server-side authorization nonce.
 *   • POST /ws/oauth/authorize: Handle approval, finalize authorization
 *     with the provider, and redirect the user agent back to the client
 *     with oauth_token and oauth_verifier.
 *   • Support "oob" (out-of-band) flows by returning the verifier in
 *     the response body instead of redirecting.
 *
 * Context / Why Added:
 *   Part of OAuth 1.0a implementation replacing CXF’s generic handlers.
 *   Explicit JAX-RS resource allows integration with OSCAR’s login/session
 *   model and JSP-based consent UI.
 *
 * Notes:
 *   • Requires an authenticated user session (via "user" attribute) before
 *     POST approval can bind the request token to a provider.
 *   • Does not log or expose verifier/token values beyond what’s required
 *     by the protocol.
 *   • Responds with 400/401 for missing tokens, invalid request tokens,
 *     or unauthenticated users.
 */

package io.github.carlos_emr.carlos.webserv.oauth;

import jakarta.inject.Inject;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import io.github.carlos_emr.carlos.login.OscarOAuthDataProvider;
import io.github.carlos_emr.carlos.login.OAuthData; // model used by 3rdpartyLogin.jsp

@Produces(MediaType.TEXT_HTML)
public class AuthorizeResource {

    @Context private HttpServletRequest  request;
    @Context private HttpServletResponse response;

    @Inject  private OscarOAuthDataProvider provider;

    private String getLoggedInProviderNo() {
        HttpSession session = request.getSession(false);
        Object u = session == null ? null : session.getAttribute("user");
        return u != null ? u.toString() : null;
    }

    /** GET /ws/oauth/authorize?oauth_token=... — show consent UI */
    @GET
    @Path("/authorize")
    public void showConsent(@QueryParam("oauth_token") String tokenId) throws Exception {
        if (tokenId == null || tokenId.isEmpty()) {
            response.sendError(400, "Missing oauth_token");
            return;
        }

        // Use YOUR RequestToken type
        RequestToken rt = provider.getRequestToken(tokenId);
        if (rt == null) {
            response.sendError(400, "Invalid oauth_token");
            return;
        }

        Client c = rt.getClient();
        OAuthData od = new OAuthData();
        od.setOauthToken(tokenId);
        od.setReplyTo(request.getContextPath() + "/ws/oauth/authorize");
        od.setAuthenticityToken(OAuthAuthorizationSessionState.stageNonce(request.getSession(), tokenId));
        if (c != null) {
            od.setApplicationName(c.getName());
            od.setApplicationURI(c.getUri());
        }
        List<String> scopes = (rt.getScopes() == null)
                ? java.util.Collections.emptyList()
                : rt.getScopes().stream().map(OAuth1Permission::getPermission).collect(Collectors.toList());
        od.setPermissions(scopes);

        request.setAttribute("oauthData", od);

        // Correct servlet forward. JSP now lives behind /WEB-INF/jsp/ since
        // the tail-interactive migration; RequestDispatcher can still reach
        // it via internal dispatch (see follow-up validation ticket #1731).
        RequestDispatcher rd = request.getRequestDispatcher("/WEB-INF/jsp/login/3rdpartyLogin.jsp");
        rd.forward(request, response); // response committed by forward
    }

    /** POST /ws/oauth/authorize — approve and redirect (or OOB) */
    @POST
    @Path("/authorize")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response approve(@FormParam("oauth_token") String tokenId,
                            @FormParam("session_authenticity_token") String submittedNonce,
                            @FormParam("oauthDecision") String oauthDecision) {
        if (tokenId == null || tokenId.isEmpty()) {
            return textResponse(400, "missing oauth_token");
        }

        RequestToken rt = provider.getRequestToken(tokenId);
        if (rt == null) {
            return textResponse(400, "invalid_request_token");
        }
        if (!"allow".equals(oauthDecision)) {
            return textResponse(403, "authorization_denied");
        }
        if (!OAuthAuthorizationSessionState.consumeNonce(request.getSession(false), tokenId, submittedNonce)) {
            return textResponse(403, "invalid_authorization_nonce");
        }

        String providerNo = getLoggedInProviderNo();
        if (providerNo == null || providerNo.isEmpty()) {
            return textResponse(401, "login_required");
        }

        URI callbackUri;
        try {
            callbackUri = callbackRedirectUri(rt.getCallback());
        } catch (OAuth1Exception e) {
            return textResponse(e.getHttpCode(), e.getMessage());
        }

        String verifier = provider.finalizeAuthorization(rt, providerNo);

        if (callbackUri != null) {
            return redirectToCallback(rt.getCallback(), tokenId, verifier);
        }

        // OOB: show verifier
        return Response.ok("oauth_verifier=" + enc(verifier)).type(MediaType.TEXT_PLAIN).build();
    }

    private static URI callbackRedirectUri(String callback) {
        if (callback == null || isOutOfBandCallback(callback)) {
            return null;
        }
        URI callbackUri;
        try {
            callbackUri = URI.create(callback);
        } catch (IllegalArgumentException e) {
            throw new OAuth1Exception(400, "invalid_callback");
        }
        String scheme = callbackUri.getScheme();
        if (!isHttpScheme(scheme)) {
            throw new OAuth1Exception(400, "invalid_callback_scheme");
        }
        String host = callbackUri.getHost();
        if (host == null || host.isBlank()) {
            throw new OAuth1Exception(400, "invalid_callback");
        }
        return callbackUri;
    }

    private static Response redirectToCallback(String callback, String tokenId, String verifier) {
        String sep = callback.contains("?") ? "&" : "?";
        String loc = callback + sep + "oauth_token=" + enc(tokenId) + "&oauth_verifier=" + enc(verifier);
        // nosemgrep: open-redirect -- callback comes from the server-persisted request token
        // (set during /initiate by OscarRequestTokenService), not from user input in this POST.
        return Response.seeOther(URI.create(loc)).build(); // 303 redirect
    }

    private static Response textResponse(int status, String entity) {
        return Response.status(status).entity(entity).type(MediaType.TEXT_PLAIN).build();
    }

    private static boolean isOutOfBandCallback(String callback) {
        return asciiEqualsIgnoreCase(callback, "oob");
    }

    private static boolean isHttpScheme(String scheme) {
        return asciiEqualsIgnoreCase(scheme, "http") || asciiEqualsIgnoreCase(scheme, "https");
    }

    private static boolean asciiEqualsIgnoreCase(String actual, String expected) {
        if (actual == null || actual.length() != expected.length()) {
            return false;
        }
        for (int i = 0; i < actual.length(); i++) {
            char c = actual.charAt(i);
            char lowered = c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c;
            if (lowered != expected.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static String enc(String v) { return URLEncoder.encode(v, StandardCharsets.UTF_8); }

}
