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

import io.github.carlos_emr.CarlosProperties;
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
 *
 * <p>This is a safety net over a pipeline that still buffers the whole
 * document in memory as base64. Streaming it to disk instead — which would
 * make this cap a formality — is tracked in
 * <a href="https://github.com/carlos-emr/carlos/issues/3512">#3512</a>.
 */
final class BoundedResponseReader {

    /**
     * Default ceiling, in mebibytes, on a single provider response.
     *
     * <p>Deliberately far above any real fax: this is a SAFETY NET against
     * an unbounded or hostile response, not a policy limit on fax size. The
     * cap bounds the base64 response, so 512 MiB allows a ~384 MiB PDF —
     * orders of magnitude past a bitonal fax (tens of KB per page) and well
     * past a provider that rasterises to greyscale (a few hundred KB per
     * page). No legitimate inbound fax should ever reach it.
     *
     * <p>The trade-off it still buys: a response this large is buffered at
     * roughly 4x its size while it is parsed and decoded, so a genuinely
     * enormous response needs heap to match ({@code CARLOS_JAVA_XMX};
     * {@link #warnIfCapExceedsHeap} says so at run time). Bounding it at all
     * is what turns "the JVM dies and the scheduler crash-loops on the same
     * item" into "one fax job fails".
     *
     * <p>Hitting the cap is recoverable, not destructive: the import marks
     * the remote fax as read only after a successful local import, so an
     * over-cap fax stays on the provider and arrives once the limit or the
     * heap is raised.
     */
    static final long DEFAULT_MAX_RESPONSE_MB = 512L;

    /** Operator override for {@link #DEFAULT_MAX_RESPONSE_MB}, in mebibytes. */
    static final String MAX_RESPONSE_MB_PROPERTY = "fax.max_response_mb";

    private static final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(BoundedResponseReader.class);

    private BoundedResponseReader() {
    }

    /** The configured ceiling in bytes, falling back to the default. */
    static long maxResponseBytes() {
        String configured = CarlosProperties.getInstance().getProperty(MAX_RESPONSE_MB_PROPERTY);
        if (configured != null && !configured.trim().isEmpty()) {
            try {
                long mb = Long.parseLong(configured.trim());
                if (mb > 0) {
                    // Clamp to what a single in-memory buffer can actually
                    // hold (ByteArrayOutputStream tops out near
                    // Integer.MAX_VALUE); this also makes mb*1024*1024
                    // overflow-proof for an absurd configured value.
                    long bytes = mb * 1024L * 1024L;
                    if (bytes <= 0 || bytes > Integer.MAX_VALUE - 8) {
                        logger.warn("{}={} exceeds the buffer limit; capping at ~2 GiB",
                                MAX_RESPONSE_MB_PROPERTY, configured);
                        return Integer.MAX_VALUE - 8;
                    }
                    return bytes;
                }
                logger.warn("{}={} is not positive; using the {} MiB default",
                        MAX_RESPONSE_MB_PROPERTY, configured, DEFAULT_MAX_RESPONSE_MB);
            } catch (NumberFormatException e) {
                logger.warn("{}={} is not a number; using the {} MiB default",
                        MAX_RESPONSE_MB_PROPERTY, configured, DEFAULT_MAX_RESPONSE_MB);
            }
        }
        return DEFAULT_MAX_RESPONSE_MB * 1024L * 1024L;
    }

    static String read(HttpEntity entity) throws IOException {
        long cap = maxResponseBytes();
        warnIfCapExceedsHeap(cap);
        return read(entity, cap);
    }

    /**
     * A cap the heap cannot honour is not a safety net — it just moves the
     * failure from a clean IOException to an OOM. Buffering costs roughly 4x
     * the response size (raw bytes, decoded String, the parser's copy, the
     * base64-decoded document), so warn once the ceiling outgrows the heap.
     * This never fails a fax; it tells the operator what to raise.
     */
    private static void warnIfCapExceedsHeap(long cap) {
        long maxHeap = Runtime.getRuntime().maxMemory();
        if (maxHeap != Long.MAX_VALUE && cap * 4 > maxHeap) {
            logger.warn("{} allows a {} MiB response, which would need about {} MiB of heap to "
                    + "buffer, but the JVM maximum is {} MiB — a response near the limit would "
                    + "exhaust the heap instead of failing cleanly. Raise CARLOS_JAVA_XMX or "
                    + "lower {}.",
                    MAX_RESPONSE_MB_PROPERTY, cap / (1024 * 1024), (cap * 4) / (1024 * 1024),
                    maxHeap / (1024 * 1024), MAX_RESPONSE_MB_PROPERTY);
        }
    }

    static String read(HttpEntity entity, long maxBytes) throws IOException {
        long declared = entity.getContentLength();
        if (declared > maxBytes) {
            throw new IOException(overLimitMessage(declared + " bytes", maxBytes));
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
            // Initial capacity is a small FIXED value, never the declared
            // Content-Length: a hostile header (huge declared, tiny body)
            // would otherwise reserve the whole cap up front — the exact
            // unbounded allocation this class exists to prevent, and one an
            // int cast could overflow past 2 GiB. ByteArrayOutputStream's
            // doubling growth is itself bounded by the read-loop cap below,
            // so growth reflects bytes ACTUALLY received.
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(64 * 1024);
            byte[] chunk = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(chunk)) != -1) {
                total += n;
                if (total > maxBytes) {
                    throw new IOException(overLimitMessage("more than " + maxBytes + " bytes", maxBytes));
                }
                buffer.write(chunk, 0, n);
            }
            return buffer.toString(charset);
        }
    }

    /**
     * One message for both limit paths, naming the knob: an operator whose
     * clinic legitimately receives very large faxes must be able to act on
     * this without reading the source.
     */
    private static String overLimitMessage(String actual, long maxBytes) {
        String limit = maxBytes >= 1024 * 1024
                ? (maxBytes / (1024 * 1024)) + " MiB"
                : maxBytes + " byte";
        // Kept short and front-loaded: it is surfaced through
        // faxes.statusString (varchar 255). Property name first so the
        // actionable part survives any clamp.
        return "fax response over " + limit + " (" + actual + "); raise "
                + MAX_RESPONSE_MB_PROPERTY + " + CARLOS_JAVA_XMX if faxes are truly this large. "
                + "It stays on the provider for a later poll.";
    }
}
