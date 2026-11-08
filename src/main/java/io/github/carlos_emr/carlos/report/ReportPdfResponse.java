/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import jakarta.servlet.http.HttpServletResponse;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

/** Render completely to an owner-only temporary file before committing a PDF response. */
public final class ReportPdfResponse {
    private ReportPdfResponse() { }
    @FunctionalInterface
    public interface Renderer { void render(OutputStream output) throws IOException; }

    public static void write(HttpServletResponse response, String disposition, Renderer renderer) throws IOException {
        try (TemporaryPdf temp = new TemporaryPdf()) {
            try (OutputStream output = Files.newOutputStream(temp.path)) {
                renderer.render(output);
            }
            try (var input = Files.newInputStream(temp.path)) {
                if (!"%PDF-".equals(new String(input.readNBytes(5), StandardCharsets.US_ASCII))) {
                    throw new IOException("Report renderer returned invalid PDF data");
                }
            }
            try (var channel = java.nio.channels.FileChannel.open(temp.path, java.nio.file.StandardOpenOption.READ)) {
                int length = (int) Math.min(channel.size(), 128);
                var tail = java.nio.ByteBuffer.allocate(length);
                channel.position(channel.size() - length);
                while (tail.hasRemaining() && channel.read(tail) >= 0) { /* read the bounded trailer */ }
                if (!new String(tail.array(), StandardCharsets.US_ASCII).matches("(?s).*%%EOF\\s*")) {
                    throw new IOException("Report renderer returned incomplete PDF data");
                }
            }
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", disposition);
            response.setHeader("Cache-Control", "max-age=0");
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
