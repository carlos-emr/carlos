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
import org.apache.commons.io.FilenameUtils;
import org.owasp.encoder.Encode;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;

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
 * InputStream template = creator.getDocumentStream("reports/monthly.jrxml");
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
     * Constructs a new OscarDocumentCreator instance.
     */
    public OscarDocumentCreator() {

    }

    /**
     * Loads a report template from the classpath.
     * 
     * <p>The path must not contain path traversal sequences ({@code ..}) or null bytes,
     * and the filename component must consist of safe characters only.</p>
     *
     * @param path the classpath path to the report template file
     * @return InputStream for the report template, or null if not found or path is invalid
     */
    public InputStream getDocumentStream(String path) {
        if (path == null) {
            MiscUtils.getLogger().error("Classpath resource path must not be null");
            return null;
        }
        // Reject path traversal sequences and null bytes regardless of encoding
        String normalizedPath = path.replace("\\", "/");
        if (normalizedPath.contains("..") || normalizedPath.contains("//") || path.contains("\0")) {
            MiscUtils.getLogger().error("Invalid classpath resource path rejected: " + Encode.forJava(path));
            return null;
        }
        // Validate the filename component (last segment) using FilenameUtils
        int lastSlash = normalizedPath.lastIndexOf('/');
        String filename = (lastSlash >= 0) ? normalizedPath.substring(lastSlash + 1) : normalizedPath;
        String safeFilename = FilenameUtils.getName(filename);
        if (!safeFilename.equals(filename)) {
            MiscUtils.getLogger().error("Invalid filename component in classpath path: " + Encode.forJava(path));
            return null;
        }
        InputStream reportInstream = null;
        reportInstream = getClass().getClassLoader().getResourceAsStream(path);
        return reportInstream;
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
