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
package io.github.carlos_emr.carlos.fax.provider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ContentType;

/**
 * Reads an HTTP response body into a String with a hard size cap.
 *
 * <p>The fax provider clients previously buffered responses with
 * {@code EntityUtils.toString}, which allocates for whatever the remote
 * side sends. A hostile or malfunctioning provider (or a compromised
 * middleware) answering a routine poll with an enormous body could then
 * exhaust the heap — and because the packaged JVM runs with
 * {@code -XX:+ExitOnOutOfMemoryError}, one oversized response took down
 * the whole EMR, and the scheduler re-fetching the same item made it a
 * deterministic crash loop. Bounding the read here turns that into a
 * caught {@link IOException} on one fax job.
 *
 * <p>The cap is enforced on the bytes actually read, never trusted from
 * Content-Length (which chunked responses omit and a hostile server can
 * understate) — though an honest oversized declaration fails fast.
 */
final class BoundedResponseReader {

    /**
     * 64 MiB. The largest legitimate payload is SRFax's Retrieve response
     * carrying a fax file as base64 inside JSON: a ~200-page fax is a few
     * MB of TIFF, well under 48 MB even after base64 expansion. Anything
     * larger is not a fax; failing the one job beats risking the heap.
     */
    static final long MAX_RESPONSE_BYTES = 64L * 1024 * 1024;

    private BoundedResponseReader() {
    }

    static String read(HttpEntity entity) throws IOException {
        return read(entity, MAX_RESPONSE_BYTES);
    }

    static String read(HttpEntity entity, long maxBytes) throws IOException {
        long declared = entity.getContentLength();
        if (declared > maxBytes) {
            throw new IOException("response body declares " + declared
                    + " bytes, over the " + maxBytes + "-byte fax response limit");
        }
        Charset charset = StandardCharsets.UTF_8;
        if (entity.getContentType() != null) {
            try {
                ContentType parsed = ContentType.parse(entity.getContentType());
                if (parsed != null && parsed.getCharset() != null) {
                    charset = parsed.getCharset();
                }
            } catch (IllegalArgumentException e) {
                // A malformed Content-Type (unknown or illegal charset name)
                // must not escape as an unchecked exception past callers that
                // catch IOException — that would bypass the fax pipeline's
                // FaxProviderException handling. The reply is JSON either
                // way; UTF-8 is the right fallback.
            }
        }
        try (InputStream in = entity.getContent()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(chunk)) != -1) {
                total += n;
                if (total > maxBytes) {
                    throw new IOException("response body exceeds the "
                            + maxBytes + "-byte fax response limit");
                }
                buffer.write(chunk, 0, n);
            }
            return buffer.toString(charset);
        }
    }
}
