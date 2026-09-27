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
import java.nio.charset.StandardCharsets;
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

    /** ASCII scheme syntax; numeric opaque payloads are still explicit schemes. */
    private static final Pattern EXPLICIT_SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.\\-]*:");

    /** ASCII-only case matching avoids Unicode case folding in the scheme allowlist. */
    private static final Pattern WEB_SCHEME = Pattern.compile("https?", Pattern.CASE_INSENSITIVE);

    /**
     * Address-bar shorthand for a dotted host or localhost plus a numeric port.
     * Other single-label host:port inputs are ambiguous with opaque URI schemes;
     * callers must supply http:// or https:// for those names. IPv4/IPv6 literals
     * do not match EXPLICIT_SCHEME and are validated by URI's server parser.
     */
    private static final Pattern HOST_WITH_PORT = Pattern.compile(
            "(?:localhost|[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+\\.?):[0-9]+(?:[/?#].*)?",
            Pattern.CASE_INSENSITIVE);

    private DocumentLink() {
    }

    /**
     * Normalizes a user-entered link to an absolute {@code http}/{@code https} URL.
     *
     * <p>Rules:</p>
     * <ul>
     *   <li>Surrounding whitespace is trimmed; a blank value is rejected.</li>
     *   <li>A value with no scheme gets {@code https://} prepended ({@code //host} gets
     *       {@code https:}); an existing {@code http} or {@code https} URL is preserved rather than prefixed.</li>
     *   <li>Any other scheme ({@code javascript:}, {@code data:}, {@code file:}, {@code mailto:}...)
     *       is rejected. Dotted hosts and localhost with numeric ports are accepted as
     *       address shorthand; other single-label hosts with ports need an explicit web scheme.</li>
     *   <li>The result must parse as a server {@link URI} with a host and a valid port, so characters that
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
        if (candidate.isEmpty() || !StandardCharsets.UTF_8.newEncoder().canEncode(candidate)) {
            return Optional.empty();
        }
        if (candidate.startsWith("//")) {
            candidate = "https:" + candidate;
        } else if (!EXPLICIT_SCHEME.matcher(candidate).find() || HOST_WITH_PORT.matcher(candidate).matches()) {
            candidate = "https://" + candidate;
        }

        URI uri;
        try {
            uri = new URI(candidate).parseServerAuthority();
        } catch (URISyntaxException e) {
            return Optional.empty();
        }

        String scheme = uri.getScheme();
        if (scheme == null || !WEB_SCHEME.matcher(scheme).matches()
                || uri.isOpaque() || uri.getHost() == null || uri.getPort() > 65535) {
            return Optional.empty();
        }

        // Use fixed ASCII schemes. Do not case-fold untrusted Unicode or normalize
        // Unicode path/query text: canonically equivalent spellings can name distinct resources.
        String normalizedScheme = scheme.length() == 5 ? "https" : "http";
        return Optional.of(normalizedScheme + encodeNonAscii(candidate.substring(scheme.length())));
    }

    /** Percent-encodes UTF-8 bytes without Unicode normalization or double-encoding existing escapes. */
    private static String encodeNonAscii(String value) {
        StringBuilder encoded = new StringBuilder(value.length());
        final String hex = "0123456789ABCDEF";
        for (byte octet : value.getBytes(StandardCharsets.UTF_8)) {
            int unsigned = octet & 0xff;
            if (unsigned < 128) {
                encoded.append((char) unsigned);
            } else {
                encoded.append('%').append(hex.charAt(unsigned >>> 4)).append(hex.charAt(unsigned & 15));
            }
        }
        return encoded.toString();
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
