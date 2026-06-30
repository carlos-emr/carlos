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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
 * <p><b>Read/write model.</b> Scopes are {@code <domain>.read} / {@code <domain>.write}. A {@code .write}
 * grant implies the matching {@code .read} (see {@link #isSatisfiedBy}). Each JAX-RS service under
 * {@code /ws/services} maps to a domain ({@link #DOMAIN_BY_PATH_ROOT}), and read vs. write is classified
 * per endpoint rather than purely by HTTP method, so a read-only POST (e.g. {@code tickler/search}) maps to
 * {@code .read} instead of forcing a {@code .write} grant (issue #3102). A request whose root is not mapped
 * resolves to {@link #NO_SCOPE_REQUIRED}.
 *
 * <p>Enforcement remains gated by the {@code oauth.scope.enforcement.enabled} flag (default off); this class
 * only computes what scope a request requires, it does not decide whether to enforce.
 *
 * <p>All methods are pure functions of their arguments; this type holds no request state and is safe to
 * call from any thread.
 */
public final class OAuthScopes {

    /** Sentinel returned by {@link #requiredScope} for endpoints not in the enforcement map. */
    public static final String NO_SCOPE_REQUIRED = null;

    private static final String READ = "read";
    private static final String WRITE = "write";

    /**
     * Path-root segment (the first path element under {@code /services/} in the servlet path info) → scope
     * domain, covering the JAX-RS services wired into the {@code /ws/services} CXF endpoint. Roots are
     * matched case-insensitively (the resolver lower-cases them). A request whose root is not listed here
     * resolves to {@link #NO_SCOPE_REQUIRED}. Several roots intentionally share a domain (e.g. {@code rx}
     * and {@code rxlookup} → {@code rx}, {@code reporting} and {@code reportbytemplate} → {@code report});
     * the {@code /initiate} vocabulary de-duplicates them.
     */
    private static final Map<String, String> DOMAIN_BY_PATH_ROOT = Map.ofEntries(
        Map.entry("allergies", "allergy"),
        Map.entry("app", "app"),
        Map.entry("billing", "billing"),
        Map.entry("consentservice", "consent"),
        Map.entry("consults", "consultation"),
        Map.entry("demographics", "demographic"),
        // NB: "dxregisty" intentionally matches DiseaseRegistryService's actual (misspelled)
        // @Path("/dxRegisty"); "correcting" it to "dxregistry" would stop matching the routed path.
        Map.entry("dxregisty", "diseaseregistry"),
        Map.entry("document", "document"),
        Map.entry("eform", "eform"),
        Map.entry("eforms", "eform"),
        Map.entry("forms", "form"),
        Map.entry("inbox", "inbox"),
        Map.entry("jobs", "job"),
        Map.entry("labs", "lab"),
        Map.entry("measurements", "measurement"),
        Map.entry("messaging", "messaging"),
        Map.entry("notes", "note"),
        Map.entry("patientdetailstatusservice", "patientstatus"),
        Map.entry("persona", "persona"),
        Map.entry("pharmacies", "pharmacy"),
        Map.entry("preventions", "prevention"),
        Map.entry("program", "program"),
        Map.entry("providerservice", "provider"),
        Map.entry("recordux", "recordux"),
        Map.entry("reportbytemplate", "report"),
        Map.entry("reporting", "report"),
        Map.entry("resources", "resource"),
        Map.entry("rx", "rx"),
        Map.entry("rxlookup", "rx"),
        Map.entry("schedule", "schedule"),
        Map.entry("status", "status"),
        Map.entry("tickler", "tickler")
    );

    /**
     * Path roots whose entire non-safe-method surface is read-only (no create/update/delete operations),
     * so a {@code POST}/{@code PUT}/{@code DELETE} under them still resolves to {@code .read}. Revisit if a
     * mutating endpoint is ever added to one of these services.
     */
    private static final Set<String> NONSAFE_READ_ROOTS = Set.of(
        "measurements", "recordux", "rxlookup", "patientdetailstatusservice"
    );

    /**
     * For mixed read/write services, the lower-cased operation path segments that denote a <em>read</em>
     * performed with a non-safe HTTP method (search/query/lookup/print endpoints that use {@code POST}).
     * Keyed by path root; a non-safe request whose path contains one of these segments resolves to
     * {@code .read} instead of {@code .write}. Everything else under the root defaults to {@code .write}.
     */
    private static final Map<String, Set<String>> READ_OP_SEGMENTS_BY_ROOT = Map.ofEntries(
        Map.entry("schedule", Set.of("getappointment", "appointmenthistory")),
        Map.entry("tickler", Set.of("search")),
        Map.entry("demographics", Set.of("search")),
        Map.entry("consults", Set.of("searchrequests", "searchresponses")),
        Map.entry("dxregisty", Set.of("findlikeissue")),   // misspelled root, matches @Path("/dxRegisty")
        Map.entry("notes", Set.of("all", "getcurrentnote", "getissuenote", "getgroupnoteext",
            "getissueid", "getissuebyid", "searchissues", "checkeditnotenew")),
        Map.entry("persona", Set.of("hasrights", "isallowedaccesstopatientrecord", "preferences")),
        Map.entry("providerservice", Set.of("search")),
        Map.entry("reporting", Set.of("patientlist", "getreport", "runreport")),
        Map.entry("rx", Set.of("print"))
    );

    /** The complete set of scope strings a client may request at {@code /initiate}. */
    private static final Set<String> KNOWN_SCOPES = buildKnownScopes();

    private OAuthScopes() {
        // static-utility holder; not instantiable
    }

    private static Set<String> buildKnownScopes() {
        java.util.Set<String> scopes = new java.util.HashSet<>();
        for (String domain : DOMAIN_BY_PATH_ROOT.values()) {
            scopes.add(domain + "." + READ);
            scopes.add(domain + "." + WRITE);
        }
        return Set.copyOf(scopes);
    }

    /**
     * The scope a request must carry to be authorized, or {@link #NO_SCOPE_REQUIRED} when the target
     * endpoint's root is not in {@link #DOMAIN_BY_PATH_ROOT}.
     *
     * <p>The domain comes from the first path segment under {@code /services/}. The read/write qualifier is
     * per-endpoint: safe methods ({@code GET}/{@code HEAD}/{@code OPTIONS}) are reads; non-safe methods are
     * writes <em>unless</em> the endpoint is a known read that happens to use a non-safe method (a root in
     * {@link #NONSAFE_READ_ROOTS}, or a path containing a {@link #READ_OP_SEGMENTS_BY_ROOT} marker — e.g.
     * {@code tickler/search} or {@code schedule/getAppointment}). This avoids forcing a {@code .write} grant
     * to call a read-only POST.
     *
     * @param httpMethod  the request method (case-insensitive); a {@code null}/blank method is treated as a
     *                    write for fail-safe behaviour
     * @param servicePath the request's servlet path info (e.g. {@code /services/schedule/day/2026-06-29}
     *                    from {@code HttpServletRequest.getPathInfo()}); the segment after {@code /services/}
     *                    selects the domain
     * @return the required scope string, or {@link #NO_SCOPE_REQUIRED} if the root is not mapped
     */
    public static String requiredScope(String httpMethod, String servicePath) {
        List<String> segments = serviceSegments(servicePath);
        if (segments.isEmpty()) {
            return NO_SCOPE_REQUIRED;
        }
        String root = segments.get(0);
        String domain = DOMAIN_BY_PATH_ROOT.get(root);
        if (domain == null) {
            return NO_SCOPE_REQUIRED;
        }
        boolean read = isSafeMethod(httpMethod) || isNonSafeRead(root, segments);
        return domain + "." + (read ? READ : WRITE);
    }

    /**
     * Whether a non-safe-method request under {@code root} is nonetheless a read — either because the whole
     * service is read-only on non-safe methods, or because the path carries a known read-operation segment.
     */
    private static boolean isNonSafeRead(String root, List<String> segments) {
        if (NONSAFE_READ_ROOTS.contains(root)) {
            return true;
        }
        Set<String> markers = READ_OP_SEGMENTS_BY_ROOT.get(root);
        if (markers == null) {
            return false;
        }
        for (int i = 1; i < segments.size(); i++) {
            if (markers.contains(segments.get(i))) {
                return true;
            }
        }
        return false;
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
     * The non-empty path segments after the {@code /services/} marker, lower-cased (the first is the domain
     * root, the rest identify the operation); empty if the path has no {@code /services/} segment or nothing
     * usable follows it.
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
     * cannot reach a mapped resource while looking unmapped here.
     */
    private static List<String> serviceSegments(String servicePath) {
        if (servicePath == null) {
            return List.of();
        }
        String marker = "/services/";
        int idx = servicePath.indexOf(marker);
        if (idx < 0) {
            return List.of();
        }
        String rest = servicePath.substring(idx + marker.length());
        List<String> segments = new ArrayList<>();
        for (String seg : rest.split("/")) {
            if (!seg.isEmpty()) {
                segments.add(asciiLowerCase(seg));
            }
        }
        return segments;
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
