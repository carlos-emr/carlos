/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.Date;
import java.sql.SQLException;
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
            if (date == null || date.isBlank()) throw new IllegalArgumentException("Missing report date");
            String path = switch (style) {
                case "billDaySheet.xml" -> "/oscar/oscarReport/pageUtil/billDaySheet.xml";
                case "labDaySheet.xml" -> "/oscar/oscarReport/pageUtil/labDaySheet.xml";
                default -> throw new IllegalArgumentException("Unsupported day sheet template");
            };
            Date selected = Date.valueOf(java.time.LocalDate.parse(date));
            var rows = reports.daySheet(selected);
            try (InputStream input = getClass().getResourceAsStream(path)) {
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
}
