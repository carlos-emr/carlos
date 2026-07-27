/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.eform.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Shared HTTP boundary for the renderer-only capability cookie.
 *
 * <p>This deliberately does not use {@code HttpSession}: LoginFilter and the render resource
 * servlets can recognize the browser lease without creating a CARLOS/CSRF logical session.</p>
 */
public final class EFormRendererRequestAuthorization {

    public static final String COOKIE_NAME = "CARLOS_EFORM_RENDER";
    private static final int MAX_AGE_SECONDS = 120;
    private static final Set<String> PASSIVE_STATIC_EXTENSIONS = Set.of(
            "css", "js", "json", "png", "jpg", "jpeg", "gif", "svg", "ico",
            "woff", "woff2", "ttf", "eot", "map");
    private static final Pattern CSS_REFERENCE = Pattern.compile(
            "(?:url\\s*\\(\\s*|@import\\s+(?:url\\s*\\(\\s*)?)[\"']?([^\"')\\s]+)",
            Pattern.CASE_INSENSITIVE);
    private static final int MAX_STATIC_REFERENCES = 256;
    private static final int MAX_CSS_DEPTH = 4;
    private static final int MAX_CSS_BYTES = 1_048_576;

    private EFormRendererRequestAuthorization() {
    }

    /**
     * Forwarding headers a reverse proxy adds. Their presence proves the request did not come from
     * the render browser.
     */
    private static final String[] FORWARDING_HEADERS = {
        "X-Forwarded-For", "Forwarded", "X-Real-IP", "X-Forwarded-Host", "X-Forwarded-Proto"
    };

    static EFormRenderTokenService.RenderGrant grantFromCookie(HttpServletRequest request) {
        if (wasForwarded(request)) {
            return null;
        }
        return EFormRenderTokenService.getInstance().peekSession(readCookie(request));
    }

    /**
     * Whether this request reached Tomcat through a proxy, in which case it is not the renderer.
     *
     * <p>Every renderer surface authorizes through {@link #grantFromCookie}, so refusing a grant
     * here refuses all of them at once.</p>
     *
     * <p><strong>Why this is needed.</strong> The loopback checks elsewhere in this class read
     * {@code getRemoteAddr()}, which is the TCP peer. Behind a same-host reverse proxy — nginx
     * terminating TLS and forwarding to Tomcat on {@code 127.0.0.1}, the deployment this feature's
     * own documentation assumes — that peer is the proxy, so {@code getRemoteAddr()} is
     * {@code 127.0.0.1} for every request off the internet and {@code isLoopback} is true for a
     * remote caller. CARLOS ships {@code XforwardHeaderFilter} to rewrite the address, but it only
     * acts when the peer is in {@code WAF_TRUSTED_PROXY_IPS}/{@code WAF_TRUSTED_PROXY_CIDRS}, and
     * both are empty by default; there is no {@code RemoteIpValve} either. So the "loopback-only"
     * invariant asserted throughout this package does not hold on a default proxied install.</p>
     *
     * <p>The remaining gate in that situation is the 256-bit capability cookie, which is
     * unguessable — this is a defence-in-depth repair, not a live exploit. It costs nothing: the
     * render browser is a local process connecting straight to Tomcat, so it never sets any of
     * these headers, while a request that carries one demonstrably came from somewhere else.</p>
     */
    static boolean wasForwarded(HttpServletRequest request) {
        for (String header : FORWARDING_HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                return true;
            }
        }
        return false;
    }

    // FindSecBugs COOKIE_USAGE: the renderer cookie carries only an opaque 32-byte capability handle with a 2-minute TTL — no PHI, no user identity, no session authority. See docs/static-analysis-workflows.md
    // Do not widen this suppression to cookie reads or writes that carry user or clinical data.
    @SuppressFBWarnings(value = "COOKIE_USAGE", justification = "the renderer cookie carries only an opaque 32-byte capability handle with a 2-minute TTL; no PHI, no user identity, no session authority")
    static String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    static EFormRenderTokenService.RenderSession exchangeBootstrap(
            HttpServletRequest request, EFormRenderTokenService.RenderToken token) {
        return EFormRenderTokenService.getInstance().exchange(token, readCookie(request));
    }

    static void setRendererCookie(
            HttpServletRequest request,
            HttpServletResponse response,
            EFormRenderTokenService.RenderSession renderSession) {
        String contextPath = request.getContextPath();
        String path = contextPath == null || contextPath.isBlank() ? "/" : contextPath + "/";
        StringBuilder header = new StringBuilder(COOKIE_NAME)
                .append('=').append(renderSession.cookieValue())
                .append("; Path=").append(path)
                .append("; Max-Age=").append(MAX_AGE_SECONDS)
                .append("; HttpOnly; SameSite=Strict");
        if (request.isSecure()) {
            header.append("; Secure");
        }
        response.addHeader("Set-Cookie", header.toString());
    }

    /**
     * Used by LoginFilter for otherwise unauthenticated passive files. All conditions are required:
     * loopback, read method, live renderer cookie, passive extension, and exact path grant.
     */
    /**
     * True when this request comes from the PDF render browser: a loopback caller presenting a live
     * renderer capability cookie. Never true for a clinician's browser, which holds no such cookie.
     *
     * <p>Used to pick the rejection SHAPE for an unauthorized renderer subresource. The login
     * redirect is a {@code 302} to a page that answers {@code 200 text/html}, and the render
     * network gate only counts {@code status >= 400} — so redirecting the renderer turns a denied
     * asset into a silently blank region of a clinical PDF. A renderer request must fail with a
     * status the gate can see.</p>
     */
    public static boolean isRendererRequest(HttpServletRequest request) {
        return isLoopback(request.getRemoteAddr()) && grantFromCookie(request) != null;
    }

    public static boolean permitsStaticRequest(HttpServletRequest request) {
        if (!isLoopback(request.getRemoteAddr())
                || (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod()))) {
            return false;
        }
        String path = normalizeApplicationPath(request.getRequestURI(), request.getContextPath());
        if (path == null || !hasPassiveExtension(path)) {
            return false;
        }
        EFormRenderTokenService.RenderGrant grant = grantFromCookie(request);
        return grant != null && grant.allowsStaticPath(path);
    }

    static String normalizeApplicationPath(String requestPath, String contextPath) {
        if (requestPath == null || requestPath.indexOf('\0') >= 0 || requestPath.indexOf('\\') >= 0) {
            return null;
        }
        String path = requestPath;
        if (contextPath != null && !contextPath.isEmpty()
                && (path.equals(contextPath) || path.startsWith(contextPath + "/"))) {
            path = path.substring(contextPath.length());
        }
        try {
            URI uri = new URI(path);
            if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getQuery() != null || uri.getFragment() != null) {
                return null;
            }
            Deque<String> segments = new ArrayDeque<>();
            for (String rawSegment : uri.getPath().split("/")) {
                // Path parameters are stripped PER SEGMENT, matching what Tomcat does before it
                // resolves the file. Truncating the whole URI at the first ';' instead made this
                // check evaluate a prefix of the served path: "/library/eforms/APCache.js;a=/../..
                // /secret.json" authorized as "APCache.js" while Tomcat served "/secret.json",
                // silently degrading the exact-path grant into a prefix grant over every passive
                // file in the webapp.
                int semicolon = rawSegment.indexOf(';');
                String segment = semicolon >= 0 ? rawSegment.substring(0, semicolon) : rawSegment;
                if (segment.isEmpty() || ".".equals(segment)) {
                    continue;
                }
                if ("..".equals(segment)) {
                    if (segments.isEmpty()) {
                        return null;
                    }
                    segments.removeLast();
                } else {
                    segments.addLast(segment);
                }
            }
            return "/" + String.join("/", segments);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    static boolean hasPassiveExtension(String path) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return dot > slash && dot < path.length() - 1
                && PASSIVE_STATIC_EXTENSIONS.contains(path.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    static boolean isLoopback(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return false;
        }
        try {
            return InetAddress.getByName(remoteAddress).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * Records the exact passive webapp files referenced by the composed document, including bounded
     * recursive {@code url(...)} and {@code @import} references from local stylesheets.
     */
    static void authorizeReferencedStaticResources(
            EFormRenderTokenService.RenderGrant grant,
            String html,
            String contextPath,
            ServletContext servletContext) {
        Set<String> authorized = new HashSet<>();
        Deque<ResourceCandidate> pendingCss = new ArrayDeque<>();
        Document document = Jsoup.parse(html == null ? "" : html);
        collectElementReferences(document, contextPath, authorized, pendingCss);
        // .html(), NOT .text(): jsoup parses <style> content into a DataNode, and Element.text()
        // collects only TextNodes — so .text() returned the empty string for every stylesheet block
        // and this discovery pass silently did nothing. Assets referenced only from an inline
        // <style> (background url(...), @import, @font-face src) then never entered the grant.
        collectCssReferences(document.select("style").html(), "/", contextPath, authorized, pendingCss);

        int depth = 0;
        while (!pendingCss.isEmpty() && depth++ < MAX_CSS_DEPTH && authorized.size() < MAX_STATIC_REFERENCES) {
            int levelSize = pendingCss.size();
            for (int i = 0; i < levelSize && authorized.size() < MAX_STATIC_REFERENCES; i++) {
                ResourceCandidate candidate = pendingCss.removeFirst();
                if (servletContext == null) {
                    continue;
                }
                try (InputStream input = servletContext.getResourceAsStream(candidate.path())) {
                    if (input == null) {
                        continue;
                    }
                    String css = new String(input.readNBytes(MAX_CSS_BYTES + 1), StandardCharsets.UTF_8);
                    if (css.length() > MAX_CSS_BYTES) {
                        continue;
                    }
                    String base = candidate.path().substring(0, candidate.path().lastIndexOf('/') + 1);
                    collectCssReferences(css, base, contextPath, authorized, pendingCss);
                } catch (IOException ignored) {
                    // The browser/network completeness gate reports the actual load failure. This
                    // discovery pass never broadens access when a stylesheet cannot be inspected.
                }
            }
        }
        EFormRenderTokenService.getInstance().authorizeStaticPaths(grant, authorized);
        if (servletContext != null) {
            for (String path : authorized) {
                if (!path.toLowerCase(Locale.ROOT).endsWith(".js")
                        || path.endsWith("/library/eforms/APCache.js")) {
                    continue;
                }
                try (InputStream input = servletContext.getResourceAsStream(path)) {
                    if (input == null) {
                        continue;
                    }
                    String javascript = new String(
                            input.readNBytes(MAX_CSS_BYTES + 1), StandardCharsets.UTF_8);
                    if (javascript.length() <= MAX_CSS_BYTES) {
                        EFormRenderTokenService.getInstance().authorizeApKeys(
                                grant,
                                EFormRenderPdfHtmlComposer.referencedApCacheKeys(javascript));
                    }
                } catch (IOException ignored) {
                    // The renderer will surface the actual missing script; never broaden the grant.
                }
            }
        }
    }

    private static void collectElementReferences(
            Document document,
            String contextPath,
            Set<String> authorized,
            Deque<ResourceCandidate> pendingCss) {
        String[][] attributes = {
                {"script[src]", "src"}, {"link[href]", "href"}, {"img[src]", "src"},
                {"source[src]", "src"}, {"video[src]", "src"}, {"audio[src]", "src"},
                {"input[src]", "src"}
        };
        for (String[] selector : attributes) {
            for (Element element : document.select(selector[0])) {
                String path = resolveStaticPath(element.attr(selector[1]), "/", contextPath);
                addCandidate(path, authorized, pendingCss);
            }
        }
        for (Element element : document.select("[style]")) {
            collectCssReferences(element.attr("style"), "/", contextPath, authorized, pendingCss);
        }
    }

    private static void collectCssReferences(
            String css,
            String basePath,
            String contextPath,
            Set<String> authorized,
            Deque<ResourceCandidate> pendingCss) {
        Matcher matcher = CSS_REFERENCE.matcher(css == null ? "" : css);
        while (matcher.find() && authorized.size() < MAX_STATIC_REFERENCES) {
            String path = resolveStaticPath(matcher.group(1), basePath, contextPath);
            addCandidate(path, authorized, pendingCss);
        }
    }

    private static void addCandidate(
            String path, Set<String> authorized, Deque<ResourceCandidate> pendingCss) {
        if (path == null || !hasPassiveExtension(path) || !authorized.add(path)) {
            return;
        }
        if (path.toLowerCase(Locale.ROOT).endsWith(".css")) {
            pendingCss.addLast(new ResourceCandidate(path));
        }
    }

    private static String resolveStaticPath(String rawReference, String basePath, String contextPath) {
        if (rawReference == null || rawReference.isBlank()) {
            return null;
        }
        String reference = rawReference.trim();
        if (reference.startsWith("data:") || reference.startsWith("blob:")
                || reference.startsWith("#") || reference.startsWith("//")) {
            return null;
        }
        try {
            URI uri = new URI(reference);
            if (uri.isAbsolute() || uri.getRawAuthority() != null) {
                return null;
            }
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                return null;
            }
            String resolved;
            if (path.startsWith("/")) {
                resolved = path;
            } else {
                resolved = URI.create(basePath).resolve(path).getPath();
            }
            return normalizeApplicationPath(resolved, contextPath);
        } catch (IllegalArgumentException | URISyntaxException e) {
            return null;
        }
    }

    private record ResourceCandidate(String path) {
    }
}
