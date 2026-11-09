/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.export;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Complete-document response boundary for shared Jasper PDF callers. @since 2026-11-09 */
public final class PdfDownload {
    private PdfDownload() { }
    @FunctionalInterface
    public interface Renderer { void render(OutputStream output) throws Exception; }

    public static void inline(HttpServletResponse response, String filename, Renderer renderer) throws IOException {
        if (!filename.matches("[A-Za-z0-9_.-]+\\.pdf")) throw new IllegalArgumentException("Invalid report filename");
        Path file = null;
        try {
            file = PathValidationUtils.createSecureTempFile("carlos-pdf-", ".pdf").toPath();
            try (var output = Files.newOutputStream(file)) { renderer.render(output); }
            try (var input = Files.newInputStream(file)) {
                if (!java.util.Arrays.equals(input.readNBytes(5), "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                    throw new IllegalStateException("Report generation did not produce a PDF");
                }
            }
            response.setHeader("Cache-Control", "max-age=0");
            response.setDateHeader("Expires", 0);
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "inline; filename=" + filename);
            response.setContentLengthLong(Files.size(file));
            Files.copy(file, response.getOutputStream());
        } catch (Exception failure) {
            MiscUtils.getLogger().error("PDF generation failed ({})", failure.getClass().getName());
            if (response.isCommitted()) {
                throw new IOException("PDF transfer failed");
            }
            response.reset();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            if (file != null) {
                try { Files.deleteIfExists(file); }
                catch (IOException cleanup) { MiscUtils.getLogger().warn("Could not remove temporary PDF report"); }
            }
        }
    }
}
