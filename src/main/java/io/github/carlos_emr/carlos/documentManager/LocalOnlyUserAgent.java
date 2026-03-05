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

import java.io.InputStream;
import java.util.Locale;

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
 * <p>This subclass overrides {@link #resolveAndOpenStream(String)}, the single chokepoint
 * through which all Flying Saucer resource loading flows. Only {@code data:} URIs (inline
 * base64, no network request) are allowed through; all network schemes ({@code http:},
 * {@code https:}, {@code ftp:}, protocol-relative {@code //}) are blocked.
 *
 * <p>Usage: call {@link #createRestrictedRenderer()} instead of {@code new ITextRenderer()}.
 *
 * @see ITextUserAgent
 * @since 2026-03-04
 */
public class LocalOnlyUserAgent extends ITextUserAgent {

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
     *   <li>Local paths — delegated to parent for {@code file:} and relative resolution</li>
     * </ul>
     *
     * <p>Blocked:
     * <ul>
     *   <li>{@code http:}, {@code https:}, {@code ftp:} — external network requests</li>
     *   <li>{@code //} — protocol-relative URLs</li>
     * </ul>
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

        // Block all network schemes
        if (lower.startsWith("http:") || lower.startsWith("https:")
                || lower.startsWith("ftp:") || lower.startsWith("//")) {
            logger.warn("Blocked external resource fetch during PDF rendering: {}",
                    uri.substring(0, Math.min(uri.indexOf(':') + 1, 10)));
            return null;
        }

        // Allow local file access (file: scheme and relative paths)
        return super.resolveAndOpenStream(uri);
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
     * @return ITextRenderer a renderer that cannot make outbound network requests
     */
    public static ITextRenderer createRestrictedRenderer() {
        ITextOutputDevice outputDevice = new ITextOutputDevice(DEFAULT_DOTS_PER_POINT);
        LocalOnlyUserAgent userAgent = new LocalOnlyUserAgent(outputDevice, DEFAULT_DOTS_PER_PIXEL);
        return new ITextRenderer(DEFAULT_DOTS_PER_POINT, DEFAULT_DOTS_PER_PIXEL, outputDevice, userAgent);
    }
}
