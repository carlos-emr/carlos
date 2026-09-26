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
 *
 * Portions adapted from open-osp/Open-O commit cbc04aa297 ("filter only valid URL's"),
 * Colcamex Resources Inc, GPL-2.0-or-later.
 */
package io.github.carlos_emr.carlos.documentManager;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import io.github.carlos_emr.carlos.utility.SafeEncode;

/**
 * URL normalization and stored-HTML generation for Document Manager "Add Link" documents.
 *
 * <p>An Add Link document is stored as a small HTML page in {@code document.docxml} and served
 * back through {@code ManageDocument2Action} as stored HTML. The URL the user typed therefore ends
 * up inside markup that the browser executes, so it must be restricted to web schemes and encoded
 * for the context it lands in.</p>
 *
 * <p>Historically the action prepended {@code http://} to anything that did not contain
 * {@code http://} (turning every {@code https://} link into {@code http://https://...}) and
 * concatenated the raw value into {@code window.location='...'}, which allowed quote break-out and
 * {@code javascript:} URLs. See GitHub issue #3949.</p>
 *
 * @since 2026-09-26
 */
public final class DocumentLink {

    /** Suffix appended to the document description so link documents are recognisable in lists. */
    public static final String DESCRIPTION_SUFFIX = " (link)";

    /**
     * Leading {@code scheme:} per RFC 3986 section 3.1. The negative look-ahead for a digit keeps
     * {@code host:8080/path} (a schemeless host with a port) from being read as scheme {@code host}.
     */
    private static final Pattern EXPLICIT_SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.\\-]*:(?!\\d)");

    private DocumentLink() {
    }

    /**
     * Normalizes a user-entered link to an absolute {@code http}/{@code https} URL.
     *
     * <p>Rules:</p>
     * <ul>
     *   <li>Surrounding whitespace is trimmed; a blank value is rejected.</li>
     *   <li>A value with no scheme gets {@code https://} prepended ({@code //host} gets
     *       {@code https:}); an existing {@code http} or {@code https} scheme is kept as-is.</li>
     *   <li>Any other scheme ({@code javascript:}, {@code data:}, {@code file:}, {@code mailto:}...)
     *       is rejected.</li>
     *   <li>The result must parse as a {@link URI} with a non-empty authority, so characters that
     *       are illegal in a URI (spaces, double quotes, angle brackets, control characters) are
     *       rejected rather than silently rewritten.</li>
     * </ul>
     *
     * @param rawUrl the value from the Add Link form; may be {@code null}
     * @return the normalized ASCII URL, or empty when the value is not an acceptable web link
     */
    public static Optional<String> normalizeUrl(String rawUrl) {
        if (rawUrl == null) {
            return Optional.empty();
        }
        String candidate = rawUrl.strip();
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        if (candidate.startsWith("//")) {
            candidate = "https:" + candidate;
        } else if (!EXPLICIT_SCHEME.matcher(candidate).find()) {
            candidate = "https://" + candidate;
        }

        URI uri;
        try {
            uri = new URI(candidate);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            return Optional.empty();
        }
        String lowerScheme = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(lowerScheme) && !"https".equals(lowerScheme)) {
            return Optional.empty();
        }
        String authority = uri.getRawAuthority();
        if (uri.isOpaque() || authority == null || authority.isBlank()) {
            return Optional.empty();
        }

        // toASCIIString percent-encodes any non-ASCII characters the lenient URI parser accepted,
        // so the stored value is a plain ASCII URL. Only the scheme is re-cased; the rest of the
        // URL is kept exactly as the user entered it.
        String ascii = uri.toASCIIString();
        return Optional.of(lowerScheme + ascii.substring(scheme.length()));
    }

    /**
     * Builds the stored HTML page for a normalized link.
     *
     * <p>The page navigates with a meta refresh rather than inline script, so it works under a
     * Content-Security-Policy without {@code 'unsafe-inline'}, and it shows a plain anchor as a
     * fallback. The URL is HTML-attribute encoded in both places. {@code no-referrer} keeps the
     * CARLOS document URL (which carries the document id) out of the external site's logs.</p>
     *
     * @param normalizedUrl a value returned by {@link #normalizeUrl(String)}
     * @return the HTML to store as the link document's content
     */
    public static String toRedirectHtml(String normalizedUrl) {
        String attrUrl = SafeEncode.forHtmlAttribute(normalizedUrl);
        String textUrl = SafeEncode.forHtmlContent(normalizedUrl);
        return "<!DOCTYPE html>\n"
                + "<html><head>\n"
                + "<meta charset=\"UTF-8\">\n"
                + "<meta name=\"referrer\" content=\"no-referrer\">\n"
                + "<meta http-equiv=\"refresh\" content=\"0; url=" + attrUrl + "\">\n"
                + "</head><body>\n"
                + "<p><a href=\"" + attrUrl + "\" rel=\"noopener noreferrer\">" + textUrl + "</a></p>\n"
                + "</body></html>";
    }
}
