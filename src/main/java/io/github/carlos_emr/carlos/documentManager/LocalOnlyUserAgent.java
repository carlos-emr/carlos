/**
 * Copyright (c) 2024-2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.xhtmlrenderer.pdf.ITextOutputDevice;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.pdf.ITextUserAgent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * SSRF-safe {@link ITextUserAgent} that blocks all external network resource fetching
 * during Flying Saucer PDF rendering.
 *
 * <p>Flying Saucer's default {@code NaiveUserAgent} opens unrestricted HTTP connections
 * to fetch any resource referenced in the HTML ({@code <img src>}, {@code <link href>},
 * CSS {@code @import url()}, CSS {@code background-image: url()}, etc). This allows
 * Server-Side Request Forgery (SSRF) if the HTML is user-controlled — for example,
 * fetching cloud metadata at {@code http://169.254.169.254/}.
 *
 * <p>This subclass provides two layers of defense:
 * <ol>
 *   <li>Overrides {@link #resolveAndOpenStream(String)} to block all external network schemes.
 *       Only {@code data:} URIs (inline base64), {@code file:} URIs, and local paths are
 *       permitted. All other schemes ({@code http:}, {@code https:}, {@code ftp:}, {@code jar:},
 *       protocol-relative {@code //}, etc.) are blocked.</li>
 *   <li>Overrides {@link #openStream(String)} to enforce path containment on {@code file:} URIs,
 *       preventing local file disclosure attacks (e.g., reading {@code /etc/passwd} or other
 *       patients' documents). Only paths under the webapp root, system temp directory, and
 *       Catalina work directory are permitted.</li>
 * </ol>
 *
 * <p>Usage: call {@link #createRestrictedRenderer()} instead of {@code new ITextRenderer()}.
 *
 * @see ITextUserAgent
 * @since 2026-03-04
 */
public final class LocalOnlyUserAgent extends ITextUserAgent {

    private static final Logger logger = MiscUtils.getLogger();

    /**
     * Default dots-per-point value matching {@link ITextRenderer}'s no-arg constructor.
     * {@code ITextRenderer()} delegates to {@code ITextRenderer(26.666666f, 20)}.
     */
    private static final float DEFAULT_DOTS_PER_POINT = 26.666666f;

    /**
     * Default dots-per-pixel value matching {@link ITextRenderer}'s no-arg constructor.
     */
    private static final int DEFAULT_DOTS_PER_PIXEL = 20;

    /** Pattern matching control characters (newlines, tabs, etc.) that could be used for log forging. */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x1f\\x7f]");

    /** Cached system temp directory path (stable after JVM startup). */
    private static final Path CACHED_TMPDIR;

    /** Cached Catalina work directory path (stable after JVM startup). */
    private static final Path CACHED_CATALINA_WORK;

    static {
        Path tmpDir = null;
        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp != null) {
            try {
                tmpDir = Path.of(tmp).normalize().toAbsolutePath();
            } catch (IllegalArgumentException e) {
                logger.warn("Could not parse java.io.tmpdir as allowed directory path: {}", e.getMessage());
            }
        }
        CACHED_TMPDIR = tmpDir;

        Path catalinaWork = null;
        String catalinaBase = System.getProperty("catalina.base");
        if (catalinaBase != null) {
            try {
                catalinaWork = Path.of(catalinaBase, "work").normalize().toAbsolutePath();
            } catch (IllegalArgumentException e) {
                logger.warn("Could not parse catalina.base work directory as allowed path: {}", e.getMessage());
            }
        }
        CACHED_CATALINA_WORK = catalinaWork;
    }

    /**
     * Constructs a LocalOnlyUserAgent with the given output device and pixel ratio.
     *
     * @param outputDevice ITextOutputDevice the PDF output device
     * @param dotsPerPixel int the dots-per-pixel ratio for resource scaling
     */
    public LocalOnlyUserAgent(ITextOutputDevice outputDevice, int dotsPerPixel) {
        super(outputDevice, dotsPerPixel);
    }

    /**
     * Resolves and opens an input stream for the given URI, blocking all external network schemes.
     *
     * <p>Allowed:
     * <ul>
     *   <li>{@code data:} URIs — inline base64 content, no network request</li>
     *   <li>{@code file:} URIs — delegated to parent (validated by {@link #openStream} path containment)</li>
     *   <li>Absolute paths starting with {@code /} — delegated to parent (validated by {@link #openStream})</li>
     *   <li>Relative paths (no scheme) and dot-relative paths ({@code ./}, {@code ../}) — delegated to parent</li>
     * </ul>
     *
     * <p>Blocked (everything else):
     * <ul>
     *   <li>{@code http:}, {@code https:}, {@code ftp:} — external network requests</li>
     *   <li>{@code jar:} — can trigger outbound requests via {@code jar:https://...}</li>
     *   <li>{@code //} — protocol-relative URLs</li>
     *   <li>Any other scheme ({@code javascript:}, {@code vbscript:}, {@code gopher:}, {@code ldap:}, etc.)</li>
     * </ul>
     *
     * <p>Local paths allowed through this method are further validated by
     * {@link #openStream(String)} which enforces path containment to allowed directories.
     *
     * @param uri String the resource URI to resolve
     * @return InputStream for the resource, or null if blocked or unresolvable
     */
    @Override
    protected InputStream resolveAndOpenStream(String uri) {
        if (uri == null) {
            return null;
        }

        String lower = uri.toLowerCase(Locale.ROOT);

        // Allow data: URIs (inline base64, no network)
        if (lower.startsWith("data:")) {
            return super.resolveAndOpenStream(uri);
        }

        // Block protocol-relative URLs (//evil.com) — inherits scheme from base URL
        if (uri.startsWith("//")) {
            logger.warn("Blocked external resource fetch during PDF rendering (scheme: {})", "//");
            return null;
        }

        // Allow file: scheme, absolute paths, relative paths (no scheme), and dot-relative paths.
        // The colon check uses indexOf to find scheme-like prefixes; single-letter prefixes
        // (e.g. "C:" on Windows) are treated as drive letters, not schemes, for portability.
        if (lower.startsWith("file:") || uri.startsWith("/") || uri.startsWith(".")) {
            return super.resolveAndOpenStream(uri);
        }

        int colonIdx = uri.indexOf(':');
        if (colonIdx < 0 || colonIdx == 1) {
            // No colon at all (relative path) or single-letter before colon (drive letter)
            return super.resolveAndOpenStream(uri);
        }

        // Block everything else (http, https, ftp, jar, gopher, ldap, etc.)
        String scheme = lower.substring(0, colonIdx + 1);
        String safeUri = sanitizeForLog(uri);
        logger.warn("Blocked external resource fetch during PDF rendering (scheme: {}, uri: {})", scheme, safeUri);
        return null;
    }

    /**
     * Opens an input stream for the given resolved URI, enforcing path containment
     * for {@code file:} URIs to prevent local file disclosure attacks.
     *
     * <p>An authenticated user can craft HTML with {@code <img src="file:///etc/passwd"/>}
     * or references to other patients' documents. This method validates that any
     * {@code file:} URI resolves to a path under an allowed directory before opening it.
     *
     * <p>Allowed directories:
     * <ul>
     *   <li>Webapp root — derived from {@link #getBaseURL()} (CSS, images, static resources)</li>
     *   <li>{@code java.io.tmpdir} — temp files created during rendering</li>
     *   <li>Catalina work directory — Tomcat compilation artifacts</li>
     * </ul>
     *
     * @param uri String the resolved URI (typically absolute after resolution by the parent)
     * @return InputStream for the resource, or null if path is outside allowed directories
     * @throws IOException if an I/O error occurs opening the stream
     */
    @Override
    protected InputStream openStream(String uri) throws IOException {
        if (uri != null) {
            String lower = uri.toLowerCase(Locale.ROOT);

            String safeUri = sanitizeForLog(uri);

            // Defense-in-depth: block non-file, non-data schemes even if they somehow
            // bypassed resolveAndOpenStream (e.g., via a future code path or parent resolution)
            if (lower.contains(":") && !lower.startsWith("file:") && !lower.startsWith("data:")) {
                logger.warn("Blocked non-local URI in openStream during PDF rendering: {}", safeUri);
                return null;
            }

            if (lower.startsWith("file:") && !isAllowedLocalPath(uri)) {
                logger.warn("Blocked file access outside allowed directories during PDF rendering: {}", safeUri);
                return null;
            }
        }
        return super.openStream(uri);
    }

    /**
     * Checks whether the given {@code file:} URI resolves to a path under an allowed directory.
     *
     * <p>Uses canonical path comparison (resolves symlinks, normalizes {@code ..}) to prevent
     * path traversal attacks. Fails closed: returns {@code false} on any parse error.
     *
     * @param uri String a {@code file:} URI to validate
     * @return true if the path is under an allowed directory; false if outside or if the URI cannot be parsed (fail-closed)
     */
    private boolean isAllowedLocalPath(String uri) {
        try {
            String canonicalTarget = Path.of(new URI(uri)).toFile().getCanonicalPath();

            for (Path allowedDir : getAllowedDirectories()) {
                String canonicalAllowed = allowedDir.toFile().getCanonicalPath();
                if (canonicalTarget.startsWith(canonicalAllowed + File.separator)
                        || canonicalTarget.equals(canonicalAllowed)) {
                    return true;
                }
            }
        } catch (URISyntaxException | IOException | IllegalArgumentException e) {
            logger.warn("Failed to parse file URI for path containment check (resource will be blocked): {}",
                    e.getMessage());
        }
        return false;
    }

    /**
     * Builds the list of directories that {@code file:} URIs are permitted to access.
     *
     * <p>The webapp root (from {@link #getBaseURL()}) is computed on each call because
     * Flying Saucer sets it lazily after document loading begins. The system temp directory
     * and Catalina work directory are cached in static fields since they are stable after
     * JVM startup.
     *
     * @return List of Path objects representing allowed directory roots
     */
    private List<Path> getAllowedDirectories() {
        List<Path> dirs = new ArrayList<>(3);

        // Webapp root from the base URL set by Flying Saucer (dynamic — set lazily)
        String baseUrl = getBaseURL();
        if (baseUrl != null) {
            try {
                Path webappRoot = Path.of(new URI(baseUrl)).normalize().toAbsolutePath();
                dirs.add(webappRoot);
            } catch (URISyntaxException | IllegalArgumentException e) {
                logger.warn("Could not parse base URL as allowed directory path (local resources may be blocked): {}",
                        e.getMessage());
            }
        }

        // System temp directory (cached at class init — stable after JVM startup)
        if (CACHED_TMPDIR != null) {
            dirs.add(CACHED_TMPDIR);
        }

        // Catalina work directory (cached at class init — stable after JVM startup)
        if (CACHED_CATALINA_WORK != null) {
            dirs.add(CACHED_CATALINA_WORK);
        }

        return Collections.unmodifiableList(dirs);
    }

    /**
     * Creates an {@link ITextRenderer} configured with a {@link LocalOnlyUserAgent}
     * that blocks all external network resource fetching.
     *
     * <p>The default values match {@code ITextRenderer}'s no-arg constructor:
     * {@code ITextRenderer()} → {@code ITextRenderer(26.666666f, 20)}
     * → {@code new ITextOutputDevice(26.666666f)}
     * → {@code new ITextUserAgent(outputDevice, 20)}.
     *
     * @return ITextRenderer a renderer that blocks outbound network requests and restricts local file access to allowed directories
     */
    public static ITextRenderer createRestrictedRenderer() {
        ITextOutputDevice outputDevice = new ITextOutputDevice(DEFAULT_DOTS_PER_POINT);
        LocalOnlyUserAgent userAgent = new LocalOnlyUserAgent(outputDevice, DEFAULT_DOTS_PER_PIXEL);
        return new ITextRenderer(DEFAULT_DOTS_PER_POINT, DEFAULT_DOTS_PER_PIXEL, outputDevice, userAgent);
    }

    /**
     * Rejects non-{@code file:} base URLs to prevent relative URI resolution against
     * an HTTP origin. If the base URL were {@code http://...}, any relative path
     * ({@code images/logo.png}) allowed through {@link #resolveAndOpenStream} would
     * resolve to an HTTP URL — enabling SSRF.
     *
     * @param url String the base URL to set (only {@code file:} and {@code null} are accepted)
     */
    @Override
    public void setBaseURL(String url) {
        if (url != null && !url.toLowerCase(Locale.ROOT).startsWith("file:")) {
            logger.warn("Rejected non-file base URL to prevent SSRF via relative URI resolution: {}", sanitizeForLog(url));
            return;
        }
        super.setBaseURL(url);
    }

    /**
     * Strips control characters (newlines, tabs, etc.) from a URI string before logging
     * to prevent log forging via embedded control characters in user-controlled URIs.
     *
     * @param uri String the URI to sanitize
     * @return String the URI with control characters replaced by underscores
     */
    private static String sanitizeForLog(String uri) {
        if (uri == null) {
            return "null";
        }
        return CONTROL_CHARS.matcher(uri).replaceAll("_");
    }
}
