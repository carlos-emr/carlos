/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.export;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.report.ReportPdfResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;

/** Complete-document response boundary for shared Jasper PDF callers. @since 2026-11-09 */
public final class PdfDownload {
    private PdfDownload() { }
    @FunctionalInterface
    public interface Renderer { void render(OutputStream output) throws Exception; }

    public static void inline(HttpServletResponse response, String filename, Renderer renderer) throws IOException {
        if (!filename.matches("[A-Za-z0-9_.-]+\\.pdf")) throw new IllegalArgumentException("Invalid report filename");
        try {
            ReportPdfResponse.write(response, "inline; filename=" + filename, output -> {
                try { renderer.render(output); }
                catch (IOException failure) { throw failure; }
                catch (Exception failure) { throw new IOException("PDF rendering failed", failure); }
            });
        } catch (Exception failure) {
            MiscUtils.getLogger().error("PDF generation failed ({})", failure.getClass().getName());
            if (response.isCommitted()) {
                throw new IOException("PDF transfer failed");
            }
            response.reset();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
