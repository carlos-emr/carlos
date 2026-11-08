/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import io.github.carlos_emr.carlos.commn.dao.FixedJasperReportDao;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The action authorizes first; this service loads detached rows before rendering the fixed template. */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class FixedDaySheetReportService {
    private final FixedJasperReportDao reports;
    public FixedDaySheetReportService(FixedJasperReportDao reports) { this.reports = reports; }

    /** The single allowlist for bundled template names and their trusted resource paths. */
    public enum Style {
        LAB("labDaySheet.xml", "/oscar/oscarReport/pageUtil/labDaySheet.xml"),
        BILLING("billDaySheet.xml", "/oscar/oscarReport/pageUtil/billDaySheet.xml");

        private final String filename;
        private final String path;
        Style(String filename, String path) { this.filename = filename; this.path = path; }

        /** @return String trusted filename passed to the report service */
        public String filename() { return filename; }

        /**
         * @param filename String requested template basename
         * @return Style matching the exact allowlisted name, or null if rejected
         */
        public static Style find(String filename) {
            for (Style style : values()) {
                if (style.filename.equals(filename)) return style;
            }
            return null;
        }
    }

    /**
     * Uses the configured Addresslabel.xml override when present; otherwise loads
     * detached rows for an allowlisted bundled template. Empty prints return HTTP 204.
     * An unusable override fails explicitly, without falling back to a different report.
     *
     * @param response HttpServletResponse receiving the validated PDF or no-content status
     * @param date String strict ISO appointment date for bundled reports; overrides retain null compatibility
     * @param style String allowlisted bundled template filename
     * @throws InvalidDateException if a bundled-report date is blank or malformed
     * @throws IOException if template access, export, response I/O, or cleanup fails
     * @throws JRException if Jasper compilation or filling fails
     * @throws SQLException if the configured override query fails
     * @throws SecurityException if the configured override is not a permitted regular file
     */
    public void writePdf(HttpServletResponse response, String date, String style) throws IOException, JRException, SQLException {
        // Keep the historical configured override and its SQL on the managed JDBC boundary.
        File override = PathValidationUtils.resolveConfiguredFile(
                new File(System.getProperty("user.home"), "Addresslabel.xml").getPath(), "day sheet override");
        JasperPrint print;
        if (override.isFile()) {
            var parameters = new java.util.HashMap<String, Object>();
            parameters.put("input_date", date);
            try (InputStream input = Files.newInputStream(override.toPath())) {
                print = ConfiguredJasperTemplateRenderer.fill(JasperCompileManager.compileReport(input),
                        parameters);
            }
        } else {
            if (date == null || date.isBlank()) throw new InvalidDateException();
            Style selectedStyle = Style.find(style);
            if (selectedStyle == null) throw new IllegalArgumentException("Unsupported day sheet template");
            LocalDate selected;
            try { selected = LocalDate.parse(date); }
            catch (java.time.DateTimeException | IllegalArgumentException _) { throw new InvalidDateException(); }
            var rows = reports.daySheet(selected);
            try (InputStream input = getClass().getResourceAsStream(selectedStyle.path)) {
                if (input == null) throw new IOException("Missing day sheet template");
                print = JasperFillManager.fillReport(JasperCompileManager.compileReport(input),
                        new java.util.HashMap<>(Map.<String, Object>of("input_date", date)), FixedJasperDataSources.daySheet(rows));
            }
        }
        // NoPages is an intentional template contract; distinguish it from a failed PDF export.
        if (print.getPages().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        ReportPdfResponse.write(response, "inline; filename=label_.pdf", output -> {
            try { JasperExportManager.exportReportToPdfStream(print, output); }
            catch (JRException ex) { throw new IOException("Could not export day sheet", ex); }
        });
    }

    /** A blank or malformed bundled-report date; the action maps this to HTTP 400. */
    public static final class InvalidDateException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private InvalidDateException() { super("Invalid day sheet date"); }
    }
}
