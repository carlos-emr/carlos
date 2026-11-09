/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import jakarta.servlet.http.HttpServletResponse;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

/**
 * Renders to an owner-only temporary file, validates the PDF signature and trailer,
 * then streams the complete document with headers that prohibit caching.
 * Temporary files are deleted on normal and exceptional exits.
 */
public final class ReportPdfResponse {
    private ReportPdfResponse() { }

    /** Writes the complete PDF, propagating generation and output failures. */
    @FunctionalInterface
    public interface Renderer {
        /**
         * @param output OutputStream owned and closed by the response helper
         * @throws IOException if rendering or writing fails
         */
        void render(OutputStream output) throws IOException;
    }

    /** Invalid renderer output, distinct from filesystem and servlet I/O failures. */
    public static final class InvalidPdfException extends IOException {
        private static final long serialVersionUID = 1L;
        private InvalidPdfException(String message) { super(message); }
    }

    /**
     * Sets download headers only after the renderer produces a PDF with a
     * {@code %PDF-} signature and {@code %%EOF} trailer.
     *
     * @param response HttpServletResponse receiving the complete PDF
     * @param disposition String trusted Content-Disposition header value
     * @param renderer Renderer producing the document into an owned temporary file
     * @throws InvalidPdfException if the rendered signature or trailer is invalid
     * @throws IOException if rendering, validation I/O, streaming, or cleanup fails
     */
    public static void write(HttpServletResponse response, String disposition, Renderer renderer) throws IOException {
        try (TemporaryPdf temp = new TemporaryPdf()) {
            try (OutputStream output = Files.newOutputStream(temp.path)) {
                renderer.render(output);
            }
            try (var input = Files.newInputStream(temp.path)) {
                if (!"%PDF-".equals(new String(input.readNBytes(5), StandardCharsets.US_ASCII))) {
                    throw new InvalidPdfException("Report renderer returned invalid PDF data");
                }
            }
            try (var channel = java.nio.channels.FileChannel.open(temp.path, java.nio.file.StandardOpenOption.READ)) {
                int length = (int) Math.min(channel.size(), 128);
                var tail = java.nio.ByteBuffer.allocate(length);
                channel.position(channel.size() - length);
                while (tail.hasRemaining() && channel.read(tail) >= 0) { /* read the bounded trailer */ }
                if (!new String(tail.array(), StandardCharsets.US_ASCII).matches("(?s).*%%EOF\\s*")) {
                    throw new InvalidPdfException("Report renderer returned incomplete PDF data");
                }
            }
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", disposition);
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
            response.setContentLengthLong(Files.size(temp.path));
            try (var input = Files.newInputStream(temp.path)) {
                input.transferTo(response.getOutputStream());
            }
        }
    }

    private static final class TemporaryPdf implements AutoCloseable {
        private final java.nio.file.Path path;
        private TemporaryPdf() throws IOException {
            path = PathValidationUtils.createSecureTempFile("carlos-report-", ".pdf").toPath();
        }
        @Override public void close() throws IOException { Files.deleteIfExists(path); }
    }
}
