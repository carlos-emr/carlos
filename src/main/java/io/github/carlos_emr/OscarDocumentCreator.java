/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */

package io.github.carlos_emr;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.*;
import org.owasp.encoder.Encode;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Document creator for generating reports in various formats using JasperReports.
 *
 * <p>This class provides functionality to create and export reports in multiple formats:</p>
 * <ul>
 *   <li><strong>PDF</strong> - Portable Document Format with optional JavaScript</li>
 *   <li><strong>CSV</strong> - Comma-Separated Values for data export</li>
 *   <li><strong>Excel</strong> - Excel spreadsheet format (XLSX)</li>
 * </ul>
 *
 * <p>Supports various data sources:</p>
 * <ul>
 *   <li>Java Collections (List of beans)</li>
 *   <li>JDBC ResultSet</li>
 *   <li>Database Connection</li>
 *   <li>Custom JRDataSource implementations</li>
 *   <li>Empty data source for parameter-only reports</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>
 * OscarDocumentCreator creator = new OscarDocumentCreator();
 * HashMap params = new HashMap();
 * params.put("title", "Monthly Report");
 * InputStream template = creator.getDocumentStream("oscar/oscarBilling/ca/bc/reports/pdf_rep_invoice.jrxml");
 * creator.fillDocumentStream(params, outputStream, OscarDocumentCreator.PDF, template, dataList);
 * </pre>
 */
public class OscarDocumentCreator {
    /** PDF document format constant */
    public static final String PDF = "pdf";

    /** CSV document format constant */
    public static final String CSV = "csv";

    /** Excel spreadsheet format constant */
    public static final String EXCEL = "excel";

    /**
     * Allowlist of all permitted classpath resource paths for JasperReport templates.
     * Using a Map (key = caller-supplied path, value = hardcoded constant) ensures
     * the value passed to {@code getResourceAsStream} is never derived from user input,
     * which breaks the CodeQL taint chain for path injection analysis.
     * Keys and values are intentionally identical; the Map lookup is what matters.
     */
    private static final Map<String, String> ALLOWED_REPORT_PATHS;
    static {
        Map<String, String> m = new HashMap<>();
        // BC billing reports
        String bc = "oscar/oscarBilling/ca/bc/reports/";
        m.put(bc + "pdf_rep_invoice.jrxml",      bc + "pdf_rep_invoice.jrxml");
        m.put(bc + "pdf_rep_payref.jrxml",       bc + "pdf_rep_payref.jrxml");
        m.put(bc + "pdf_rep_payref_sum.jrxml",   bc + "pdf_rep_payref_sum.jrxml");
        m.put(bc + "pdf_rep_account_rec.jrxml",  bc + "pdf_rep_account_rec.jrxml");
        m.put(bc + "pdf_rep_rej.jrxml",          bc + "pdf_rep_rej.jrxml");
        m.put(bc + "pdf_rep_wo.jrxml",           bc + "pdf_rep_wo.jrxml");
        m.put(bc + "pdf_rep_msprem.jrxml",       bc + "pdf_rep_msprem.jrxml");
        m.put(bc + "pdf_rep_mspremsum.jrxml",    bc + "pdf_rep_mspremsum.jrxml");
        m.put(bc + "csv_rep_invoice.jrxml",      bc + "csv_rep_invoice.jrxml");
        m.put(bc + "csv_rep_payref.jrxml",       bc + "csv_rep_payref.jrxml");
        m.put(bc + "cvs_rep_payref_sum.jrxml",   bc + "cvs_rep_payref_sum.jrxml");
        m.put(bc + "csv_rep_account_rec.jrxml",  bc + "csv_rep_account_rec.jrxml");
        m.put(bc + "csv_rep_rej.jrxml",          bc + "csv_rep_rej.jrxml");
        m.put(bc + "csv_rep_wo.jrxml",           bc + "csv_rep_wo.jrxml");
        m.put(bc + "csv_rep_msprem.jrxml",       bc + "csv_rep_msprem.jrxml");
        m.put(bc + "csv_rep_mspremsum.jrxml",    bc + "csv_rep_mspremsum.jrxml");
        m.put(bc + "msppremsum.practsum.jrxml",  bc + "msppremsum.practsum.jrxml");
        m.put(bc + "msppremsum.s23.jrxml",       bc + "msppremsum.s23.jrxml");
        m.put(bc + "msppremsum.s23_orphan.jrxml", bc + "msppremsum.s23_orphan.jrxml");
        m.put(bc + "broadcastmessages.jrxml",    bc + "broadcastmessages.jrxml");
        // ON billing reports
        String on = "/oscar/oscarBilling/ca/on/reports/";
        m.put(on + "end_year_statement_report.jrxml",    on + "end_year_statement_report.jrxml");
        m.put(on + "end_year_statement_subreport.jrxml", on + "end_year_statement_subreport.jrxml");
        ALLOWED_REPORT_PATHS = Collections.unmodifiableMap(m);
    }

    /**
     * Constructs a new OscarDocumentCreator instance.
     */
    public OscarDocumentCreator() {

    }

    /**
     * Loads a report template from the classpath.
     *
     * <p>The path must exactly match one of the known report template paths in
     * {@link #ALLOWED_REPORT_PATHS}. Paths not on the allowlist are rejected and
     * {@code null} is returned. This allowlist approach prevents path injection by
     * ensuring the value passed to {@code getResourceAsStream} is always a compile-time
     * constant from the Map, never derived from caller-supplied input.</p>
     *
     * <p>To add support for a new report template, add its classpath path to
     * {@code ALLOWED_REPORT_PATHS} in the static initializer above.</p>
     *
     * @param path the classpath path to the report template file
     * @return InputStream for the report template, or null if not found or path is not allowlisted
     */
    public InputStream getDocumentStream(String path) {
        if (path == null) {
            MiscUtils.getLogger().error("Classpath resource path must not be null");
            return null;
        }
        // Normalize backslashes before allowlist lookup
        String normalizedPath = path.replace("\\", "/");
        // Look up the path in the allowlist; the Map value (not the user-supplied key)
        // is what gets passed to getResourceAsStream, breaking the CodeQL taint chain.
        String safePath = ALLOWED_REPORT_PATHS.get(normalizedPath);
        if (safePath == null) {
            MiscUtils.getLogger().error("Classpath resource path not in allowlist: {}", Encode.forJava(path));
            return null;
        }
        return getClass().getClassLoader().getResourceAsStream(safePath);
    }

    /**
     * Fills and exports a report without PDF JavaScript.
     * 
     * @param parameters report parameters (e.g., title, date ranges)
     * @param sos output stream to write the generated document
     * @param docType document format (PDF, CSV, or EXCEL)
     * @param xmlDesign input stream containing the JasperReport XML design
     * @param dataSrc data source (List, Connection, ResultSet, JRDataSource, or null for empty)
     */
    @SuppressWarnings("rawtypes")
    public void fillDocumentStream(HashMap parameters, OutputStream sos,
                                   String docType, InputStream xmlDesign,
                                   Object dataSrc) {
        fillDocumentStream(parameters, sos, docType, xmlDesign, dataSrc, null);
    }

    /**
     * Fills and exports a report with optional PDF JavaScript.
     * 
     * <p>This method compiles the report design, fills it with data, and exports
     * it to the specified format. PDF reports can include JavaScript for actions
     * like auto-printing.</p>
     * 
     * @param parameters report parameters (e.g., title, date ranges)
     * @param sos output stream to write the generated document
     * @param docType document format (PDF, CSV, or EXCEL)
     * @param xmlDesign input stream containing the JasperReport XML design
     * @param dataSrc data source (List, Connection, ResultSet, JRDataSource, or null for empty)
     * @param exportPdfJavascript optional JavaScript code to embed in PDF (e.g., "this.print();")
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void fillDocumentStream(HashMap parameters, OutputStream sos,
                                   String docType, InputStream xmlDesign,
                                   Object dataSrc, String exportPdfJavascript) {
        try {
            JasperReport jasperReport = null;
            JasperPrint print = null;
            jasperReport = getJasperReport(xmlDesign);
            if (docType.equals(OscarDocumentCreator.PDF) && exportPdfJavascript != null) {
                jasperReport.setProperty("net.sf.jasperreports.export.pdf.javascript", exportPdfJavascript);
            }
            if (dataSrc == null) {
                print = JasperFillManager.fillReport(jasperReport, parameters, new JREmptyDataSource());
            } else if (dataSrc instanceof List) {
                JRDataSource ds = new JRBeanCollectionDataSource((List<?>) dataSrc);
                print = JasperFillManager.fillReport(jasperReport, parameters,
                        ds);
            } else if (dataSrc instanceof java.sql.Connection) {
                print = JasperFillManager.fillReport(jasperReport, parameters,
                        (Connection) dataSrc);
            } else if (dataSrc instanceof ResultSet) {
                JRDataSource ds = new JRResultSetDataSource((ResultSet) dataSrc);
                print = JasperFillManager.fillReport(jasperReport, parameters,
                        ds);
            } else {
                JRDataSource ds = (JRDataSource) dataSrc;
                print = JasperFillManager.fillReport(jasperReport, parameters, ds);
            }
            if (docType.equals(OscarDocumentCreator.PDF)) {
                JasperExportManager.exportReportToPdfStream(print, sos);
            } else if (docType.equals(OscarDocumentCreator.CSV)) {
                this.exportReportToCSVStream(print, sos);

            } else if (docType.equals(OscarDocumentCreator.EXCEL)) {
                this.exportReportToExcelStream(print, sos);
            }

        } catch (JRException ex) {
            MiscUtils.getLogger().error("Error", ex);
        }
    }

    /**
     * Returns a JasperReport instance reprepesenting the supplied InputStream
     *
     * @param xmlDesign InputStream
     * @return JasperReport
     */
    public JasperReport getJasperReport(InputStream xmlDesign) {
        JasperReport jasperReport = null;
        try {
            jasperReport = JasperCompileManager.compileReport(
                    xmlDesign);
        } catch (JRException ex) {
            MiscUtils.getLogger().error("Error", ex);
        }
        return jasperReport;
    }

    public JasperReport getJasperReport(byte[] xmlDesign) {
        JasperReport jasperReport = null;
        try {
            jasperReport = JasperCompileManager.compileReport(
                    new ByteArrayInputStream(xmlDesign));
        } catch (JRException ex) {
            MiscUtils.getLogger().error("Error", ex);
        }
        return jasperReport;
    }

    /**
     * Fills a servletoutout stream with data from a JasperReport
     *
     * @param jasperPrint JasperPrint
     * @param sos         ServletOutputStream
     * @throws JRException
     */
    private void exportReportToCSVStream(JasperPrint jasperPrint, OutputStream sos) throws JRException {
        JRCsvExporter exp = new JRCsvExporter();
        exp.setExporterInput(new SimpleExporterInput(jasperPrint));
        exp.setExporterOutput(new SimpleWriterExporterOutput(sos));
        SimpleCsvReportConfiguration configuration = new SimpleCsvReportConfiguration();
        exp.setConfiguration(configuration);
        exp.exportReport();
    }

    private void exportReportToExcelStream(JasperPrint jasperPrint, OutputStream os)
            throws JRException {
        JRXlsxExporter exp = new JRXlsxExporter();
        exp.setExporterInput(new SimpleExporterInput(jasperPrint));
        exp.setExporterOutput(new SimpleOutputStreamExporterOutput(os));

        SimpleXlsxReportConfiguration reportConfiguration = new SimpleXlsxReportConfiguration();
        reportConfiguration.setIgnorePageMargins(true);
        reportConfiguration.setOffsetX(0);
        reportConfiguration.setIgnoreCellBorder(false);
        reportConfiguration.setDetectCellType(true);
        reportConfiguration.setWhitePageBackground(false);
        reportConfiguration.setOnePagePerSheet(false);
        reportConfiguration.setMaxRowsPerSheet(65000);
        exp.setConfiguration(reportConfiguration);

        exp.exportReport();
    }

}
