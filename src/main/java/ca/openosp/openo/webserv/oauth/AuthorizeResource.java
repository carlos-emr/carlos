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
 *     pre-populated with client and requested scopes.
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
 *   • Requires an authenticated user session (via "user" attribute).
 *   • Does not log or expose verifier/token values beyond what’s required
 *     by the protocol.
 *   • Responds with 400/401 for missing tokens, invalid request tokens,
 *     or unauthenticated users.
 */

package ca.openosp.openo.webserv.oauth;

import javax.inject.Inject;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import ca.openosp.openo.login.OscarOAuthDataProvider;
import ca.openosp.openo.login.OAuthData; // model used by 3rdpartyLogin.jsp

@Produces(MediaType.TEXT_HTML)
public class AuthorizeResource {

    @Context private HttpServletRequest  request;
    @Context private HttpServletResponse response;

    @Inject  private OscarOAuthDataProvider provider;

    private String getLoggedInProviderNo() {
        Object u = request.getSession().getAttribute("user");
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
        if (c != null) {
            od.setApplicationName(c.getName());
            od.setApplicationURI(c.getUri());
        }
        List<String> scopes = (rt.getScopes() == null)
                ? java.util.Collections.emptyList()
                : rt.getScopes().stream().map(OAuth1Permission::getPermission).collect(Collectors.toList());
        od.setPermissions(scopes);

        request.setAttribute("oauthData", od);

        // Correct servlet forward
        RequestDispatcher rd = request.getRequestDispatcher("/login/3rdpartyLogin.jsp");
        rd.forward(request, response); // response committed by forward
    }

    /** POST /ws/oauth/authorize — approve and redirect (or OOB) */
    @POST
    @Path("/authorize")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response approve(@FormParam("oauth_token") String tokenId) {
        if (tokenId == null || tokenId.isEmpty()) {
            return Response.status(400).entity("missing oauth_token").type(MediaType.TEXT_PLAIN).build();
        }

        RequestToken rt = provider.getRequestToken(tokenId);
        if (rt == null) {
            return Response.status(400).entity("invalid_request_token").type(MediaType.TEXT_PLAIN).build();
        }

        String providerNo = getLoggedInProviderNo();
        if (providerNo == null || providerNo.isEmpty()) {
            return Response.status(401).entity("login_required").type(MediaType.TEXT_PLAIN).build();
        }

        // Let the provider set & persist the verifier + providerNo
        String verifier = provider.finalizeAuthorization(rt);

        String cb = rt.getCallback();
        if (cb != null && !"oob".equalsIgnoreCase(cb)) {
            String sep = cb.contains("?") ? "&" : "?";
            String loc = cb + sep + "oauth_token=" + enc(tokenId) + "&oauth_verifier=" + enc(verifier);
            return Response.seeOther(URI.create(loc)).build(); // 303 redirect
        }

        // OOB: show verifier
        return Response.ok("oauth_verifier=" + enc(verifier)).type(MediaType.TEXT_PLAIN).build();
    }

    private static String enc(String v) { return URLEncoder.encode(v, StandardCharsets.UTF_8); }
}
