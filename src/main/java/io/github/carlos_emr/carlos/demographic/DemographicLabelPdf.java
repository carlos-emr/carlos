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
import io.github.carlos_emr.carlos.utility.MiscUtils;

/** Generates the complete label before committing a successful PDF response. */
final class DemographicLabelPdf {
    private DemographicLabelPdf() { }

    static void write(HttpServletResponse response, Map<String, Object> parameters,
                      InputStream input, String printJavascript) throws IOException {
        try (InputStream template = input) {
            if (template == null) throw new JRException("Label template is missing");
            JasperReport report = JasperCompileManager.compileReport(template);
            if (printJavascript != null) {
                report.setProperty("net.sf.jasperreports.export.pdf.javascript", printJavascript);
            }
            JasperPrint print;
            try (Connection connection = LegacyJdbcQuery.getConnection()) {
                print = JasperFillManager.fillReport(report, new HashMap<>(parameters), connection);
            }
            if (print.getPages().isEmpty()) throw new JRException("Label contains no patient data");
            byte[] pdf = JasperExportManager.exportReportToPdf(print);
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "inline; filename=label.pdf");
            response.setHeader("Cache-Control", "no-store");
            response.setContentLength(pdf.length);
            response.getOutputStream().write(pdf);
        } catch (JRException | SQLException | RuntimeException ex) {
            // Report template/JDBC details can contain patient data. Keep the public
            // error generic and do not log SQL values or template expressions.
            MiscUtils.getLogger().error("Label PDF generation failed ({})", ex.getClass().getSimpleName());
            if (response.isCommitted()) throw new IOException("Label PDF response failed", ex);
            response.reset();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to generate patient label");
        }
    }
}
