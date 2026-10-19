/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Published under the GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.fax.provider;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.HttpEntityWrapper;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.Closer;

/** Bounds transport bytes and ordinary metadata separately from streamed document bytes. */
final class FaxResponseStream extends FilterInputStream {
    private final long limit;
    private long count;
    private long metadataBytes;
    boolean document;

    private FaxResponseStream(InputStream input, long limit) {
        super(input);
        this.limit = limit;
    }

    /**
     * Discards the request's connection before closing its body, including decoder wrappers.
     * Each provider call owns its HTTP client, so there is no connection to reuse. Graceful
     * stream/response closure would otherwise drain rejected bodies outside the byte limits.
     */
    static void abortOnClose(CloseableHttpResponse response) {
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            response.setEntity(new HttpEntityWrapper(entity) {
                private boolean connectionDiscarded;

                private void discardConnection() {
                    // Adapted/test responses can delegate close back to this entity.
                    if (!connectionDiscarded) {
                        connectionDiscarded = true;
                        response.close(CloseMode.IMMEDIATE);
                    }
                }

                @Override
                public InputStream getContent() throws IOException {
                    return new FilterInputStream(super.getContent()) {
                        @Override
                        public void close() {
                            discardConnection();
                            // Release decoder resources after cancellation; socket close failures are expected.
                            Closer.closeQuietly(in);
                        }
                    };
                }

                @Override
                public void close() {
                    discardConnection();
                    Closer.closeQuietly(entity);
                }
            });
        }
    }

    static FaxResponseStream open(HttpEntity entity) throws IOException {
        long cap = BoundedResponseReader.maxResponseBytes();
        if (entity.getContentLength() > cap) {
            throw new IOException("Fax response exceeds fax.max_response_mb");
        }
        return new FaxResponseStream(entity.getContent(), cap);
    }

    private void counted(int bytes) throws IOException {
        if (bytes <= 0) {
            return;
        }
        count += bytes;
        if (count > limit) {
            throw new IOException("Fax response exceeds fax.max_response_mb");
        }
        // The parser may prefetch a small buffer at field boundaries. It must never
        // materialize an unbounded metadata field disguised as a document.
        if (!document) {
            metadataBytes += bytes;
            if (metadataBytes > 1024 * 1024) {
                throw new IOException("Fax response metadata exceeds 1 MiB");
            }
        }
    }

    @Override
    public int read() throws IOException {
        int value = in.read();
        counted(value < 0 ? 0 : 1);
        return value;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        int read = in.read(bytes, offset, (int) Math.min(length, limit - count + 1));
        counted(read);
        return read;
    }
}
