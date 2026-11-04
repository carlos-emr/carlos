/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.demographic;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.HashMap;
import jakarta.servlet.http.HttpServletResponse;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/** Generates the complete label before committing a successful PDF response. */
final class DemographicLabelPdf {
    private DemographicLabelPdf() { }

    static void write(HttpServletResponse response, Map<String, Object> parameters,
                      InputStream input, String printJavascript) throws IOException {
        String failureContext = "loading label template";
        try (InputStream template = input) {
            if (template == null) throw new JRException("Label template is missing");
            failureContext = "compiling label template";
            JasperReport report = JasperCompileManager.compileReport(template);
            if (printJavascript != null) {
                report.setProperty("net.sf.jasperreports.export.pdf.javascript", printJavascript);
            }
            JasperPrint print;
            failureContext = "opening label database connection";
            try (Connection connection = LegacyJdbcQuery.getConnection()) {
                failureContext = "filling label report";
                print = JasperFillManager.fillReport(report, new HashMap<>(parameters), connection);
                failureContext = "closing label database connection";
            }
            failureContext = "checking report pages (label contains no patient data)";
            if (print.getPages().isEmpty()) throw new JRException("Label contains no patient data");
            failureContext = "exporting label PDF";
            byte[] pdf = JasperExportManager.exportReportToPdf(print);
            failureContext = "writing label PDF response";
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "inline; filename=label.pdf");
            response.setHeader("Cache-Control", "no-store");
            response.setContentLength(pdf.length);
            // Jasper-generated PDF bytes with application/pdf, not an HTML response.
            // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer
            response.getOutputStream().write(pdf);
            failureContext = "closing label template";
        } catch (JRException | SQLException | RuntimeException ex) {
            // Use a fixed diagnostic message plus cause types and code locations. Raw
            // Jasper/JDBC messages (including nested/suppressed ones) can contain PHI.
            // Do not attach the original throwable to the logging event.
            MiscUtils.getLogger().error("Label PDF generation failed while {}{}",
                    failureContext, LogSafe.exceptionTrace(ex));
            if (response.isCommitted()) throw new IOException("Label PDF response failed", ex);
            response.reset();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to generate patient label");
        }
    }
}
