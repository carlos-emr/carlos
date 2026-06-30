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

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
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
     * Path-root segment (the first path element under {@code /ws/services/}) → scope domain, for the
     * endpoints currently in the enforcement pilot. Roots are matched case-insensitively. Anything not
     * listed here is treated as {@link #NO_SCOPE_REQUIRED}.
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
     * <p>The domain is derived from the first path segment under {@code /ws/services/}; the read/write
     * qualifier is derived from the HTTP method (safe methods — {@code GET}/{@code HEAD}/{@code OPTIONS} —
     * require {@code .read}, everything else requires {@code .write}).
     *
     * @param httpMethod the request method (case-insensitive); a {@code null}/blank method is treated as a
     *                   write for fail-safe behaviour
     * @param requestUri the request URI (e.g. {@code /carlos/ws/services/schedule/day/2026-06-29}); the
     *                   segment after {@code /services/} selects the domain
     * @return the required scope string, or {@link #NO_SCOPE_REQUIRED} if the endpoint is not piloted
     */
    public static String requiredScope(String httpMethod, String requestUri) {
        String root = pathRootUnderServices(requestUri);
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
     * The domain root of the request: the first non-empty path segment after the {@code /services/} marker,
     * lower-cased; {@code null} if the URI has no {@code /services/} segment or nothing usable follows it.
     *
     * <p>Each segment is percent-decoded and has any matrix parameters ({@code ;k=v}) stripped, and
     * {@code .}/{@code ..} segments are resolved away, <b>before</b> the root is matched — so it reflects
     * what JAX-RS/CXF actually routes to. Decoded segments are re-split on {@code /} so a percent-encoded
     * slash ({@code %2F}, or {@code %2e%2e%2f} → {@code ../}) is treated as a path boundary too. Without
     * this, requests such as {@code /ws/services/%73chedule/...}, {@code /ws/services/schedule;x=1/...},
     * {@code /ws/services/./schedule/...}, or {@code /ws/services/%2e%2e%2fschedule/...} would resolve to a
     * non-matching root, fall back to {@link #NO_SCOPE_REQUIRED}, and silently bypass scope enforcement
     * while still reaching the {@code schedule} resource. Resolution errs toward enforcement, never bypass.
     */
    private static String pathRootUnderServices(String requestUri) {
        if (requestUri == null) {
            return null;
        }
        String marker = "/services/";
        int idx = requestUri.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        String rest = requestUri.substring(idx + marker.length());
        // strip any query string before splitting on path separators
        int q = rest.indexOf('?');
        if (q >= 0) {
            rest = rest.substring(0, q);
        }
        Deque<String> segments = new ArrayDeque<>();
        for (String rawSegment : rest.split("/")) {
            if (rawSegment.isEmpty()) {
                continue;
            }
            // Decode first so a percent-encoded matrix delimiter (%3B) or slash (%2F) is revealed, then
            // re-split on any decoded '/' so an encoded slash is a path boundary, drop matrix parameters,
            // and collapse dot-segments — mirroring container/JAX-RS path resolution.
            for (String part : percentDecode(rawSegment).split("/")) {
                String segment = stripMatrixParameters(part);
                if (segment.isEmpty() || segment.equals(".")) {
                    continue;
                }
                if (segment.equals("..")) {
                    segments.pollLast();
                    continue;
                }
                segments.addLast(segment);
            }
        }
        String root = segments.peekFirst();
        return root == null ? null : asciiLowerCase(root);
    }

    private static String stripMatrixParameters(String segment) {
        int semi = segment.indexOf(';');
        return semi >= 0 ? segment.substring(0, semi) : segment;
    }

    /** Decodes {@code %XX} escapes only; intentionally leaves {@code +} untouched (literal in path segments). */
    private static String percentDecode(String value) {
        if (value.indexOf('%') < 0) {
            return value;
        }
        int n = value.length();
        StringBuilder out = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            char c = value.charAt(i);
            if (c == '%' && i + 2 < n) {
                int hi = Character.digit(value.charAt(i + 1), 16);
                int lo = Character.digit(value.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    out.append((char) ((hi << 4) + lo));
                    i += 2;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
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
