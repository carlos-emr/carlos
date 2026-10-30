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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.engine;

import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

/**
 * Reads the payload of a {@code data:} URI.
 *
 * <p>Only base64 payloads are supported, which is what browsers and editors write for images; a
 * percent-encoded payload is not read. The payload is limited to a number of decoded bytes, checked
 * from the length of the text before anything is decoded, so a hostile document cannot make the
 * decoder allocate more than the limit.</p>
 *
 * @since 2026-10-29
 */
public final class DataUris {

    private DataUris() {
    }

    /**
     * Whether a {@code src} is a {@code data:} URI, whatever the case of the scheme.
     *
     * @param uri the value
     * @return {@code true} if it starts with {@code data:}
     */
    // FindSecBugs IMPROPER_UNICODE: case-fold of a URI scheme to recognise data: URIs; not a security decision
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "case-fold of a URI scheme to recognise data: URIs; the payload checks decide, not this comparison")
    public static boolean isDataUri(String uri) {
        return uri != null && uri.regionMatches(true, 0, "data:", 0, 5);
    }

    /**
     * Decodes the base64 payload of a {@code data:} URI.
     *
     * @param uri the URI, for example {@code data:image/png;base64,iVBOR...}
     * @param maxDecodedBytes the most bytes to decode
     * @return the bytes, or empty if the URI is not a base64 {@code data:} URI, is malformed, or its
     *         payload would decode to more than {@code maxDecodedBytes}
     */
    // FindSecBugs IMPROPER_UNICODE: case-fold of the media-type header to find ";base64"; not a security decision
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "case-fold of the data: URI header to find ;base64; the length and decoding checks decide")
    public static Optional<byte[]> decodeBase64(String uri, long maxDecodedBytes) {
        if (!isDataUri(uri)) {
            return Optional.empty();
        }
        int comma = uri.indexOf(',');
        if (comma < 0 || !uri.substring(0, comma).toLowerCase(Locale.ROOT).contains(";base64")) {
            return Optional.empty();
        }
        long encodedLength = (long) uri.length() - comma - 1;
        if (encodedLength / 4 * 3 > maxDecodedBytes) {
            return Optional.empty();
        }
        try {
            byte[] decoded = Base64.getMimeDecoder().decode(uri.substring(comma + 1));
            return decoded.length > maxDecodedBytes ? Optional.empty() : Optional.of(decoded);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
