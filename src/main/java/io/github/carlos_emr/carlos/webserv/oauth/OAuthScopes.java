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
package io.github.carlos_emr.carlos.webserv.oauth;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Vocabulary and matching rules for OAuth 1.0a access-token scopes (issue #3083).
 *
 * <p>OAuth tokens persist the scopes a provider approved on the consent screen, but historically
 * nothing read them: any valid token granted the full privileges of its provider across the entire
 * {@code /ws/services/*} surface. This class is the single, side-effect-free source of truth used to
 * close that gap, in two places:
 *
 * <ul>
 *   <li>{@code OscarRequestTokenService} — to reject unknown/empty scopes at {@code /ws/oauth/initiate}
 *       rather than persisting arbitrary strings; and</li>
 *   <li>{@code OAuthInterceptor} — to require the scope a request needs before letting the call through.</li>
 * </ul>
 *
 * <p><b>Coarse read/write model.</b> Scopes are {@code <domain>.read} / {@code <domain>.write}. A
 * {@code .write} grant implies the matching {@code .read} (see {@link #isSatisfiedBy}). Enforcement is
 * piloted on a deliberately small subset of domains; every other {@code /ws/services/*} endpoint maps to
 * {@link #NO_SCOPE_REQUIRED} so enabling enforcement does not break the un-piloted surface. Extending the
 * pilot to additional domains is tracked as follow-up to #3083.
 *
 * <p>All methods are pure functions of their arguments; this type holds no request state and is safe to
 * call from any thread.
 */
public final class OAuthScopes {

    /** Sentinel returned by {@link #requiredScope} for endpoints outside the enforcement pilot. */
    public static final String NO_SCOPE_REQUIRED = null;

    private static final String READ = "read";
    private static final String WRITE = "write";

    /**
     * Path-root segment (the first path element under {@code /services/} in the servlet path info) → scope
     * domain, for the endpoints currently in the enforcement pilot. Roots are matched case-insensitively.
     * Anything not listed here is treated as {@link #NO_SCOPE_REQUIRED}.
     */
    private static final Map<String, String> PILOT_DOMAIN_BY_PATH_ROOT = Map.of(
        "schedule", "schedule",
        "tickler", "tickler"
    );

    /** The complete set of scope strings a client may request at {@code /initiate} while the pilot stands. */
    private static final Set<String> KNOWN_SCOPES = buildKnownScopes();

    private OAuthScopes() {
        // static-utility holder; not instantiable
    }

    private static Set<String> buildKnownScopes() {
        java.util.Set<String> scopes = new java.util.HashSet<>();
        for (String domain : PILOT_DOMAIN_BY_PATH_ROOT.values()) {
            scopes.add(domain + "." + READ);
            scopes.add(domain + "." + WRITE);
        }
        return Set.copyOf(scopes);
    }

    /**
     * The scope a request must carry to be authorized, or {@link #NO_SCOPE_REQUIRED} when the target
     * endpoint is outside the enforcement pilot.
     *
     * <p>The domain is derived from the first path segment under {@code /services/}; the read/write
     * qualifier is derived from the HTTP method (safe methods — {@code GET}/{@code HEAD}/{@code OPTIONS} —
     * require {@code .read}, everything else requires {@code .write}).
     *
     * @param httpMethod  the request method (case-insensitive); a {@code null}/blank method is treated as a
     *                    write for fail-safe behaviour
     * @param servicePath the request's servlet path info (e.g. {@code /services/schedule/day/2026-06-29}
     *                    from {@code HttpServletRequest.getPathInfo()}); the segment after {@code /services/}
     *                    selects the domain
     * @return the required scope string, or {@link #NO_SCOPE_REQUIRED} if the endpoint is not piloted
     */
    public static String requiredScope(String httpMethod, String servicePath) {
        String root = pathRootUnderServices(servicePath);
        if (root == null) {
            return NO_SCOPE_REQUIRED;
        }
        String domain = PILOT_DOMAIN_BY_PATH_ROOT.get(root);
        if (domain == null) {
            return NO_SCOPE_REQUIRED;
        }
        return domain + "." + (isSafeMethod(httpMethod) ? READ : WRITE);
    }

    /**
     * Whether the scopes granted on a token satisfy a {@code requiredScope}. A {@code null}
     * {@code requiredScope} ({@link #NO_SCOPE_REQUIRED}) is always satisfied. Matching is exact, except
     * that a {@code <domain>.write} grant also satisfies {@code <domain>.read}.
     *
     * @param requiredScope the scope the request needs, or {@link #NO_SCOPE_REQUIRED}
     * @param grantedScopes the scopes present on the token (may be {@code null}/empty)
     * @return {@code true} if the request is permitted by the granted scopes
     */
    public static boolean isSatisfiedBy(String requiredScope, Collection<String> grantedScopes) {
        if (requiredScope == null) {
            return true;
        }
        if (grantedScopes == null || grantedScopes.isEmpty()) {
            return false;
        }
        String required = normalize(requiredScope);
        for (String granted : grantedScopes) {
            String g = normalize(granted);
            if (g == null) {
                continue;
            }
            if (g.equals(required)) {
                return true;
            }
            // write implies read on the same domain
            if (required.endsWith("." + READ)
                    && g.equals(required.substring(0, required.length() - READ.length()) + WRITE)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a scope string is part of the recognised vocabulary. Used to reject arbitrary scope strings
     * at {@code /initiate} when enforcement is enabled.
     *
     * @param scope a single scope token
     * @return {@code true} if {@code scope} is a known {@code <domain>.read}/{@code .write} value
     */
    public static boolean isKnownScope(String scope) {
        String normalized = normalize(scope);
        return normalized != null && KNOWN_SCOPES.contains(normalized);
    }

    private static boolean isSafeMethod(String httpMethod) {
        if (httpMethod == null) {
            return false;
        }
        String m = asciiLowerCase(httpMethod.trim());
        return m.equals("get") || m.equals("head") || m.equals("options");
    }

    /**
     * The domain root of the request: the first path segment after the {@code /services/} marker, lower-cased;
     * {@code null} if the path has no {@code /services/} segment or nothing usable follows it.
     *
     * <p>The caller passes the request's <em>servlet path info</em>
     * ({@link jakarta.servlet.http.HttpServletRequest#getPathInfo()}), which the servlet container has already
     * URL-decoded and canonicalized — dot-segments collapsed, matrix/path parameters stripped — and which is
     * the exact path JAX-RS/CXF route on. This method therefore does no decoding or normalization of its own:
     * doing it here would only risk diverging from how the request is actually routed.
     *
     * <p>Verified live on the CARLOS stack (Tomcat 11 + CXF 4.1.5): inside a {@code PRE_INVOKE} interceptor,
     * {@code getPathInfo()} is the raw container request's value, and for every routed request it is
     * {@code /services/<domain>/...} regardless of how the client percent-encoded the URI; matrix params and
     * {@code .}/{@code ..} segments are already resolved, and an encoded slash ({@code %2F}) is rejected by
     * the container (HTTP 400) before the interceptor runs. So an encoded mount prefix or domain segment
     * cannot reach a piloted resource while looking unpiloted here.
     */
    private static String pathRootUnderServices(String servicePath) {
        if (servicePath == null) {
            return null;
        }
        String marker = "/services/";
        int idx = servicePath.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        String rest = servicePath.substring(idx + marker.length());
        int slash = rest.indexOf('/');
        if (slash >= 0) {
            rest = rest.substring(0, slash);
        }
        return rest.isEmpty() ? null : asciiLowerCase(rest);
    }

    private static String normalize(String scope) {
        if (scope == null) {
            return null;
        }
        String trimmed = asciiLowerCase(scope.trim());
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * ASCII-only lower-casing. Scopes and HTTP methods are ASCII tokens, and folding them with the
     * locale-sensitive {@code String.toLowerCase} adds no value while tripping locale/Unicode scanners; a
     * fixed ASCII fold mirrors the existing OAuth helpers in this package.
     */
    private static String asciiLowerCase(String value) {
        StringBuilder lowered = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            lowered.append(c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c);
        }
        return lowered.toString();
    }
}
