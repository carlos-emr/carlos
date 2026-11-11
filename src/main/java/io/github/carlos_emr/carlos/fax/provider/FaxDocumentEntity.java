/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Published under the GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.fax.provider;

import java.io.BufferedOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;

/** Repeatable, constant-memory base64 request body with small protocol envelopes. */
final class FaxDocumentEntity extends AbstractHttpEntity {
    private final byte[] prefix;
    private final byte[] suffix;
    private final Path file;
    private final String legacyDocument;
    private final boolean formEncoded;

    FaxDocumentEntity(String prefix, String suffix, ContentType contentType,
            Path file, String legacyDocument, boolean formEncoded) throws FaxProviderException {
        super(contentType, null, true);
        if (file != null && (!Files.isRegularFile(file) || !Files.isReadable(file))) {
            throw new FaxProviderException("Fax document file not found or is not readable (check permissions)");
        }
        if (file == null && (legacyDocument == null || legacyDocument.isBlank())) {
            throw new FaxProviderException("Fatal error locating document. Not found in filesystem or database backup");
        }
        this.prefix = prefix.getBytes(StandardCharsets.UTF_8);
        this.suffix = suffix.getBytes(StandardCharsets.UTF_8);
        this.file = file;
        this.legacyDocument = file == null ? legacyDocument : null;
        this.formEncoded = formEncoded;
    }

    @Override
    public boolean isRepeatable() {
        return true;
    }

    @Override
    public long getContentLength() {
        return -1;
    }

    @Override
    public InputStream getContent() {
        throw new UnsupportedOperationException("Use writeTo for a streaming fax request");
    }

    @Override
    public void writeTo(OutputStream destination) throws IOException {
        destination.write(prefix);
        // Closing the base64 encoder emits padding; it must not close the HTTP stream
        // before the trailing protocol bytes have been written.
        OutputStream shield = new FilterOutputStream(destination) {
            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                out.write(bytes, offset, length);
            }

            @Override
            public void close() throws IOException {
                flush();
            }
        };
        OutputStream escaped = formEncoded ? new FormOutputStream(new BufferedOutputStream(shield)) : shield;
        try (OutputStream encoded = Base64.getEncoder().wrap(new BufferedOutputStream(escaped));
                InputStream input = openDocument(file, legacyDocument)) {
            input.transferTo(encoded);
        }
        destination.write(suffix);
    }

    /** Compatibility for old database backups; new sends always use the validated file. */
    static InputStream openDocument(Path file, String legacyDocument) throws IOException {
        if (file != null) {
            return Files.newInputStream(file);
        }
        if (legacyDocument == null) {
            throw new IOException("Missing fax document payload");
        }
        return Base64.getMimeDecoder().wrap(new InputStream() {
            private int position;

            @Override
            public int read() {
                return position < legacyDocument.length() ? legacyDocument.charAt(position++) : -1;
            }
        });
    }

    @Override
    public boolean isStreaming() {
        return false;
    }

    @Override
    public void close() {
        // File streams are scoped to writeTo, including repeat/authentication attempts.
    }

    /** Base64's only non-form-safe characters are '+', '/' and '='. */
    private static final class FormOutputStream extends FilterOutputStream {
        private static final byte[] ESCAPED_PLUS = {'%', '2', 'B'};
        private static final byte[] ESCAPED_SLASH = {'%', '2', 'F'};
        private static final byte[] ESCAPED_EQUALS = {'%', '3', 'D'};

        private FormOutputStream(OutputStream output) {
            super(output);
        }

        @Override
        public void write(int value) throws IOException {
            switch (value) {
                case '+' -> out.write(ESCAPED_PLUS);
                case '/' -> out.write(ESCAPED_SLASH);
                case '=' -> out.write(ESCAPED_EQUALS);
                default -> out.write(value);
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            int start = offset;
            for (int i = offset; i < offset + length; i++) {
                if (bytes[i] == '+' || bytes[i] == '/' || bytes[i] == '=') {
                    out.write(bytes, start, i - start);
                    write(bytes[i]);
                    start = i + 1;
                }
            }
            out.write(bytes, start, offset + length - start);
        }
    }
}
