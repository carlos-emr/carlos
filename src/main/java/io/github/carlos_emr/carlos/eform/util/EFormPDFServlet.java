/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.eform.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.awt.Color;
import org.openpdf.text.*;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.Logger;
import org.owasp.encoder.Encode;
import io.github.carlos_emr.carlos.commn.printing.FontSettings;
import io.github.carlos_emr.carlos.commn.printing.PdfWriterFactory;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.form.graphic.FrmGraphicFactory;
import io.github.carlos_emr.carlos.form.graphic.FrmPdfGraphic;
import io.github.carlos_emr.carlos.form.pdfservlet.FrmPDFPostValueProcessor;
import io.github.carlos_emr.carlos.form.pdfservlet.PostProcessorRegistry;
import io.github.carlos_emr.carlos.util.ConcatPDF;

import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfImportedPage;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfWriter;

/**
 * Servlet that generates PDF documents from electronic form (e-form) templates.
 *
 * <p>Overlays form field values onto a pre-existing PDF template using coordinate-based
 * positioning defined in text configuration files. Supports single-line text, multi-line
 * text areas, checkboxes (ZapfDingbats), rectangles, lines, and graphical plots
 * (e.g., growth charts for Rourke forms).</p>
 *
 * <p>Configuration files ({@code .txt}) define field placement using a CSV format:
 * {@code paramName : alignment, X, Y, 0, font, fontSize [, textToPrint] [, topRightX, topRightY, lineSpacing]}.
 * Coordinates reference the bottom-left corner of each element and are measured in PDF points.
 * Graphic configuration files drive measurement plotting via {@link FrmPdfGraphic} implementations.</p>
 *
 * <p>Uses OpenPDF ({@code org.openpdf.*}) for PDF template reading, content overlay,
 * and multi-page document generation.</p>
 *
 * @see io.github.carlos_emr.carlos.form.graphic.FrmPdfGraphic
 * @see io.github.carlos_emr.carlos.form.graphic.FrmGraphicFactory
 * @see io.github.carlos_emr.carlos.commn.printing.PdfWriterFactory
 * @since 2013-07-26
 */
public class EFormPDFServlet extends HttpServlet {

    Logger log = MiscUtils.getLogger();

    /**
     * Default constructor.
     */
    public EFormPDFServlet() {
        super();
    }

    /**
     * Delegates GET requests to {@link #doPost(HttpServletRequest, HttpServletResponse)}.
     *
     * @param req HttpServletRequest the incoming request
     * @param res HttpServletResponse the outgoing response
     * @throws jakarta.servlet.ServletException if a servlet error occurs
     * @throws java.io.IOException if an I/O error occurs
     */
    public void doGet(HttpServletRequest req, HttpServletResponse res) throws jakarta.servlet.ServletException,
            java.io.IOException {
        doPost(req, res);
    }

    /**
     * Generates one or more e-form PDFs and streams the result to the HTTP response.
     *
     * <p>If the {@code multiple} parameter is present, generates multiple PDFs and
     * concatenates them via {@link ConcatPDF}. Otherwise generates a single PDF.
     * The response is set to {@code application/pdf} with inline content disposition.</p>
     *
     * @param req HttpServletRequest containing e-form field values and configuration parameters
     * @param res HttpServletResponse to write the generated PDF to
     * @throws jakarta.servlet.ServletException if a servlet error occurs
     * @throws java.io.IOException if an I/O error occurs
     */
    public void doPost(HttpServletRequest req, HttpServletResponse res) throws jakarta.servlet.ServletException,
            java.io.IOException {

        ByteArrayOutputStream baosPDF = null;
        FileInputStream fis = null;
        File tmpFile = null;

        try {

            if (req.getParameter("multiple") != null) {
                ArrayList<Object> files = new ArrayList<Object>();
                for (int x = 0; x < Integer.parseInt(req.getParameter("multiple")); x++) {
                    baosPDF = generatePDFDocumentBytes(req, this.getServletContext(), x);
                    tmpFile = File.createTempFile("formpdf", String.valueOf((int) Math.random() * 10000));
                    baosPDF.writeTo(new FileOutputStream(tmpFile));
                    files.add(tmpFile.getAbsolutePath());
                    tmpFile.deleteOnExit();
                }
                tmpFile = File.createTempFile("formpdf", String.valueOf((int) Math.random() * 10000));
                ConcatPDF.concat(files, tmpFile.getAbsolutePath());
            } else {
                baosPDF = generatePDFDocumentBytes(req, this.getServletContext(), 0);
                tmpFile = File.createTempFile("formpdf", String.valueOf((int) Math.random() * 10000));
                baosPDF.writeTo(new FileOutputStream(tmpFile));
            }
            StringBuilder sbFilename = new StringBuilder();
            sbFilename.append("filename_");
            sbFilename.append(".pdf");

            // set the Cache-Control header
            res.setHeader("Cache-Control", "max-age=0");
            res.setDateHeader("Expires", 0);
            res.setContentType("application/pdf");

            // The Content-disposition value will be inline

            StringBuilder sbContentDispValue = new StringBuilder();
            sbContentDispValue.append("inline; filename="); //inline - display
            sbContentDispValue.append(sbFilename);

            res.setHeader("Content-disposition", sbContentDispValue.toString());
            res.setContentLength((int) tmpFile.length());

            ServletOutputStream sout = res.getOutputStream();
            fis = new FileInputStream(tmpFile);
            byte[] buffer = new byte[64000];
            int bytesRead = 0;

            while (true) {
                bytesRead = fis.read(buffer);
                if (bytesRead == -1)
                    break;

                sout.write(buffer, 0, bytesRead);
            }

        } catch (Exception e) {
            log.error("Error generating eForm PDF", e);
            if (!res.isCommitted()) {
                res.setContentType("text/html");
                PrintWriter writer = res.getWriter();
                writer.println("<p>An error occurred while generating the PDF. Please try again or contact support.</p>");
                writer.close();
            }
        } finally {
            if (baosPDF != null) baosPDF.close();
            if (fis != null) fis.close();
            if (tmpFile != null) tmpFile.deleteOnExit();
        }
    }


    /**
     * Generates a PDF document by overlaying e-form field values onto a PDF template.
     *
     * <p>The form txt config file has lines in the form:</p>
     * <ul>
     *   <li><strong>Checkboxes:</strong>
     *     {@code paramName : alignment, X, Y, 0, BaseFont.ZAPFDINGBATS, fontSize, checkmark}</li>
     *   <li><strong>Single-line text:</strong>
     *     {@code paramName : alignment, X, Y, 0, font, fontSize}</li>
     *   <li><strong>Multi-line text:</strong>
     *     {@code paramName : alignment, X1, Y1, 0, font, fontSize, _, X2, Y2, lineSpacing}</li>
     * </ul>
     * <p>Coordinates are in PDF points (1/72 inch) from the bottom-left corner.</p>
     *
     * @param req HttpServletRequest containing e-form field values and template parameters
     * @param ctx ServletContext used for resource resolution of template and config files
     * @param multiple int zero-based page index for multi-page rendering
     * @return ByteArrayOutputStream containing the generated PDF bytes
     * @throws Exception if template reading, PDF generation, or config loading fails
     */
    protected ByteArrayOutputStream generatePDFDocumentBytes(final HttpServletRequest req, final ServletContext ctx, int multiple) throws Exception {


        String suffix = (multiple > 0) ? String.valueOf(multiple) : "";

        ByteArrayOutputStream baosPDF = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter writer = null;
        PdfReader reader = null;
        try {
            writer = PdfWriterFactory.newInstance(document, baosPDF, FontSettings.HELVETICA_6PT);

            String title = req.getParameter("__title" + suffix) != null ? req.getParameter("__title" + suffix) : "Unknown";
            String template = req.getParameter("__template" + suffix) != null ? req.getParameter("__template" + suffix) + ".pdf" : "";

            int numPages = 1;
            String pages = req.getParameter("__numPages" + suffix);
            if (pages != null) {
                numPages = Integer.parseInt(pages);
            }

            //load config files
            Properties[] printCfg = loadPrintCfg(req, suffix, numPages);
            Properties[][] graphicCfg = loadGraphicCfg(req, suffix, numPages);
            int cfgFileNo = printCfg == null ? 0 : printCfg.length;

            Properties props = new Properties();
            getPrintPropValues(props, req, suffix);

            Properties measurements = new Properties();

            //initialise measurement collections = a list of pages sections measurements
            List<List<List<String>>> xMeasurementValues = new ArrayList<List<List<String>>>();
            List<List<List<String>>> yMeasurementValues = new ArrayList<List<List<String>>>();
            for (int idx = 0; idx < numPages; ++idx) {
                MiscUtils.getLogger().debug("Adding page " + idx);
                xMeasurementValues.add(new ArrayList<List<String>>());
                yMeasurementValues.add(new ArrayList<List<String>>());
            }

            saveMeasurementValues(measurements, props, req, numPages, xMeasurementValues, yMeasurementValues);
            addDocumentProps(document, title, props);

            // create a reader for a certain document
            String propFilename = CarlosProperties.getInstance().getEformImageDirectory() + "/" + template;

            try {
                reader = new PdfReader(propFilename);
                log.debug("Found template at " + propFilename);
            } catch (Exception dex) {
                log.warn("Cannot find template at: {}", propFilename);
                throw new IOException("Cannot load PDF template: " + propFilename, dex);
            }

            // retrieve the total number of pages
            int n = reader.getNumberOfPages();
            // retrieve the size of the first page
            Rectangle pSize = reader.getPageSize(1);
            float height = pSize.getHeight();

            PdfContentByte cb = writer.getDirectContent();
            int i = 0;

            while (i < n) {
                document.newPage();

                i++;
                PdfImportedPage page1 = writer.getImportedPage(reader, i);
                cb.addTemplate(page1, 1, 0, 0, 1, 0, 0);

                cb.setRGBColorStroke(0, 0, 255);
                // LEFT/CENTER/RIGHT, X, Y,

                if (i <= cfgFileNo) {
                    writeContent(printCfg[i - 1], props, measurements, height, cb);
                } //end if there are print properties

                //graphic
                Properties[] tempPropertiesArray;
                if (i <= graphicCfg.length) {
                    tempPropertiesArray = graphicCfg[i - 1];
                    MiscUtils.getLogger().debug("Plotting page " + i);
                } else {
                    tempPropertiesArray = null;
                    MiscUtils.getLogger().debug("Skipped Plotting page " + i);
                }

                //if there are properties to plot
                if (tempPropertiesArray != null) {
                    MiscUtils.getLogger().debug("TEMP PROP LENGTH " + tempPropertiesArray.length);
                    for (int k = 0; k < tempPropertiesArray.length; k++) {

                        //initialise with measurement values which are mapped to config file by form get graphic function
                        List<String> xDate, yHeight;
                        if (xMeasurementValues.get(i - 1).size() > k && yMeasurementValues.get(i - 1).size() > k) {
                            xDate = new ArrayList<String>(xMeasurementValues.get(i - 1).get(k));
                            yHeight = new ArrayList<String>(yMeasurementValues.get(i - 1).get(k));
                        } else {
                            xDate = new ArrayList<String>();
                            yHeight = new ArrayList<String>();
                        }

                        plotProperties(tempPropertiesArray[k], props, xDate, yHeight, height, cb, (k % 2 == 0));
                    }
                } //end: if there are properties to plot
            }

        } finally {
            if (document.isOpen())
                document.close();
            if (writer != null)
                writer.close();
            if (reader != null)
                reader.close();
        }

        return baosPDF;
    }

    /**
     * Loads a CSV-format config file from the e-form image directory. Each entry maps a
     * form field name to a CSV line defining alignment, coordinates, font, and size.
     * The filename is validated against path traversal before access.
     *
     * @param cfgFilename String the configuration filename
     * @return Properties the parsed field layout entries, or empty Properties if not found
     */
    protected Properties getCfgProp(String cfgFilename) {
        Properties ret = new Properties();
        
        // Additional validation at file access point
        if (cfgFilename == null || cfgFilename.isEmpty()) {
            log.warn("Invalid config filename: null or empty");
            return ret;
        }
        
        // Final safety check - ensure no path traversal characters remain
        if (cfgFilename.contains("..") || cfgFilename.contains("/") || cfgFilename.contains("\\")) {
            log.warn("Potential path traversal attempt blocked: " + cfgFilename);
            return ret;
        }
        
        // Validate filename format - should only be alphanumeric with dots, dashes, underscores
        if (!cfgFilename.matches("^[a-zA-Z0-9._-]+$")) {
            log.warn("Invalid filename format: " + cfgFilename);
            return ret;
        }
        
        String propFilename = CarlosProperties.getInstance().getEformImageDirectory() + "/" + cfgFilename;
        InputStream is = null;

        try {
            log.debug("1Looking for the prop file! " + propFilename);
            is = new FileInputStream(propFilename); //getServletContext().getResourceAsStream(propFilename);
            if (is != null) {
                log.debug("2Found the prop file! " + cfgFilename);
                ret.load(is);
                is.close();
            }
        } catch (Exception e) {
            log.warn("Can't find the prop file: " + cfgFilename);
        } finally {
            if (is != null) try {
                is.close();
            } catch (IOException e) {
                log.warn("Cannot close FileInputStream");
            }
        }
        return ret;
    }


    private Properties[] loadPrintCfg(HttpServletRequest req, String suffix, int numPages) {
        Properties[] printCfg = null;
        
        // Get user parameters
        String[] userProvidedFiles = req.getParameterValues("__cfgfile" + suffix);
        
        // Validate and sanitize immediately at point of user input
        if (userProvidedFiles == null || userProvidedFiles.length == 0) {
            return printCfg;
        }
        
        // Create array for sanitized values only
        printCfg = new Properties[userProvidedFiles.length];
        
        for (int idx2 = 0; idx2 < userProvidedFiles.length; ++idx2) {
            // Sanitize each user input immediately
            String userInput = userProvidedFiles[idx2];
            String safeFilename = null;
            
            if (userInput != null && !userInput.isEmpty()) {
                // Extract just the filename, removing any path components
                String cleanName = org.apache.commons.io.FilenameUtils.getName(userInput);
                
                if (cleanName != null && !cleanName.isEmpty()) {
                    // Additional sanitization - remove dangerous patterns
                    cleanName = cleanName.replaceAll("\\.\\.", "")
                                        .replaceAll("[/\\\\]", "")
                                        .replaceAll("[^a-zA-Z0-9._-]", "");
                    
                    // Only proceed if we have a valid name after cleaning
                    if (!cleanName.isEmpty()) {
                        safeFilename = cleanName + ".txt";
                    }
                }
            }
            
            // Use sanitized filename or empty string
            printCfg[idx2] = getCfgProp(safeFilename != null ? safeFilename : "");
        }
        
        return printCfg;
    }

    private Properties[][] loadGraphicCfg(HttpServletRequest req, String suffix, int numPages) {
        Properties[][] graphicCfg = new Properties[numPages][];
        String[] cfgGraphicFile;
        String paramName;
        int cfgGraphicFileNo;
        for (int idx = 0; idx < numPages; ++idx) {
            if (idx == 0) {
                cfgGraphicFile = req.getParameterValues("__cfgGraphicFile" + suffix);
            } else {
                paramName = "__cfgGraphicFile" + String.valueOf(idx) + suffix;
                cfgGraphicFile = req.getParameterValues(paramName);
            }

            cfgGraphicFileNo = cfgGraphicFile == null ? 0 : cfgGraphicFile.length;
            if (cfgGraphicFileNo == 0) {
                graphicCfg[idx] = null;
            } else {
                graphicCfg[idx] = new Properties[cfgGraphicFileNo];
                for (int idx2 = 0; idx2 < cfgGraphicFileNo; ++idx2) {
                    // Sanitize user input to prevent path traversal
                    String userInput = cfgGraphicFile[idx2];
                    String safeFilename = null;
                    
                    if (userInput != null && !userInput.isEmpty()) {
                        // Extract just the filename, removing any path components
                        String cleanName = FilenameUtils.getName(userInput);
                        
                        if (cleanName != null && !cleanName.isEmpty()) {
                            // Additional sanitization - remove dangerous patterns
                            cleanName = cleanName.replaceAll("\\.\\.", "")
                                                .replaceAll("[/\\\\]", "")
                                                .replaceAll("[^a-zA-Z0-9._-]", "");
                            
                            // Only proceed if we have a valid name after cleaning
                            if (!cleanName.isEmpty()) {
                                safeFilename = cleanName + ".txt";
                            }
                        }
                    }
                    
                    // Use sanitized filename or empty string
                    graphicCfg[idx][idx2] = getCfgProp(safeFilename != null ? safeFilename : "");
                }
            }
        }
        return graphicCfg;
    }

    private void getPrintPropValues(Properties props, HttpServletRequest req, String suffix) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(req);
        StringBuilder temp = new StringBuilder("");
        for (Enumeration<String> e = req.getParameterNames(); e.hasMoreElements(); ) {
            temp = new StringBuilder(e.nextElement().toString());
            props.setProperty(temp.toString(), req.getParameter(temp.toString()));
        }

        String postProcessorName = req.getParameter("postProcessor" + suffix);
        if (postProcessorName != null) {
            Optional<FrmPDFPostValueProcessor> pp = PostProcessorRegistry.resolve(postProcessorName);
            if (pp.isPresent()) {
                try {
                    props = pp.get().process(props);
                } catch (Exception e) {
                    log.warn("Post-processor {} failed during execution - form rendered without post-processing", Encode.forJava(postProcessorName), e);
                }
            } else {
                log.warn("Post-processor '{}' is not on the allowlist and will not be applied", Encode.forJava(postProcessorName));
            }
        }

        String currentDate = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());
        int totalPages = 1;
        if (req.getParameter("multiple") != null)
            totalPages = Integer.parseInt(req.getParameter("multiple"));
        String currentUser = loggedInInfo.getLoggedInProvider().getFormattedName();
        String pg = suffix.length() == 0 || suffix.equals("0") ? "0" : suffix;
        String currentPage = String.valueOf(Integer.parseInt(pg) + 1);

        props.setProperty("total_pages", String.valueOf(totalPages));
        props.setProperty("current_page", currentPage);
        props.setProperty("current_user", currentUser);
        props.setProperty("current_date", currentDate);
        props.setProperty("printer_info", "Printed on " + currentDate + " by " + currentUser + ": Page " + currentPage + " of " + totalPages);
    }

    private void saveMeasurementValues(Properties measurements, Properties props, HttpServletRequest req, int numPages, List<List<List<String>>> xMeasurementValues, List<List<List<String>>> yMeasurementValues) {
        StringBuilder temp = null;
        String tempValue = null;
        String elementNum = null;
        int page, section;
        int index, index2, index3;

        for (Enumeration<String> e = req.getAttributeNames(); e.hasMoreElements(); ) {
            temp = new StringBuilder(e.nextElement().toString());
            measurements.setProperty(temp.toString(), req.getAttribute(temp.toString()).toString());

            //for graphing measurements of height and weight
            //since we don't know how many there could be they cannot be defined in a config file
            //save them here so they can be added to the graph vectors below    
            //naming convention of measurements is xVal_num_section_page, yVal_num_section_page
            //num uniquely identifies value so x num should = y num
            //section allows graphing of more than one measurement axis e.g. if top of page is different graph than bottom of page see rourke
            //page is the pdf page it should be plotted on
            if (temp.toString().startsWith("xVal_")) {
                MiscUtils.getLogger().debug("Processing " + temp.toString());

                index = temp.indexOf("_");
                index2 = temp.indexOf("_", index + 1);
                elementNum = temp.substring(index + 1, index2);

                index3 = temp.lastIndexOf("_");
                section = Integer.parseInt(temp.substring(index2 + 1, index3));
                page = Integer.parseInt(temp.substring(index3 + 1));

                //page is zero based, numPages is not
                if (page >= numPages) {
                    continue;
                }

                //if this is the first measurement of the section init array
                while (xMeasurementValues.get(page).size() <= section) {
                    MiscUtils.getLogger().debug("Adding section " + section);
                    List<List<String>> list = xMeasurementValues.get(page);
                    list.add(new ArrayList<String>());
                }

                while (yMeasurementValues.get(page).size() <= section) {
                    List<List<String>> list = yMeasurementValues.get(page);
                    list.add(new ArrayList<String>());
                }

                xMeasurementValues.get(page).get(section).add((String) req.getAttribute(temp.toString()));
                MiscUtils.getLogger().debug("Setting xMeasurementDate to " + (String) req.getAttribute(temp.toString()));

                temp = new StringBuilder("yVal_");
                temp = temp.append(elementNum);
                temp = temp.append("_" + section);
                temp = temp.append("_" + page);
                MiscUtils.getLogger().debug("Key " + temp);
                tempValue = (String) req.getAttribute(temp.toString());
                yMeasurementValues.get(page).get(section).add(tempValue);
                MiscUtils.getLogger().debug("Setting yMeasurementValue to " + tempValue);
            } else {
                props.setProperty(temp.toString(), req.getAttribute(temp.toString()).toString());
            }
        }
    }

    private void addDocumentProps(Document document, String title, Properties props) {
        document.addTitle(title);
        document.addSubject("");
        document.addKeywords("pdf");
        document.addCreator("CARLOS EMR");
        document.addAuthor("");

        // A0-A10, LEGAL, LETTER, HALFLETTER, _11x17, LEDGER, NOTE, B0-B5, ARCH_A-ARCH_E, FLSA
        // and FLSE
        // the following shows a temp way to get a print page size
        final String PAGESIZE = "printPageSize";
        Rectangle pageSize = PageSize.LETTER;
        if ("PageSize.HALFLETTER".equals(props.getProperty(PAGESIZE)))
            pageSize = PageSize.HALFLETTER;
        if ("PageSize.A6".equals(props.getProperty(PAGESIZE)))
            pageSize = PageSize.A6;
        document.setPageSize(pageSize);
        document.open();
    }

    /**
     * Core rendering loop that iterates config entries and draws form field content onto
     * the PDF canvas. Handles multi-line text via ColumnText, rectangles, lines, static
     * text (__-prefixed), and checkbox glyphs (ZapfDingbats).
     *
     * @param printCfg Properties field-name-to-CSV-layout mappings
     * @param props Properties form field values from the request
     * @param measurements Properties additional measurement values
     * @param height float page height in points for coordinate conversion
     * @param cb PdfContentByte the direct content layer to draw on
     * @throws Exception if font creation or rendering fails
     */
    private void writeContent(Properties printCfg, Properties props, Properties measurements, float height, PdfContentByte cb) throws Exception {
        for (Enumeration e = printCfg.propertyNames(); e.hasMoreElements(); ) {
            StringBuilder temp = new StringBuilder(e.nextElement().toString());
            String[] cfgVal = printCfg.getProperty(temp.toString()).split(",");
            for (int x = 0; x < cfgVal.length; x++) {
                cfgVal[x].trim();
            }

            String[] fontType = null;
            int fontFlags = 0;
            if (cfgVal[4].indexOf(";") > -1) {
                fontType = cfgVal[4].split(";");
                if (fontType[1].trim().equals("italic"))
                    fontFlags = Font.ITALIC;
                else if (fontType[1].trim().equals("bold"))
                    fontFlags = Font.BOLD;
                else if (fontType[1].trim().equals("bolditalic"))
                    fontFlags = Font.BOLDITALIC;
                else
                    fontFlags = Font.NORMAL;
            } else {
                fontFlags = Font.NORMAL;
                fontType = new String[]{cfgVal[4].trim()};
            }

            String encoding = null;
            if (fontType[0].trim().equals("BaseFont.HELVETICA")) {
                fontType[0] = BaseFont.HELVETICA;
                encoding = BaseFont.CP1252;  //latin1 encoding
            } else if (fontType[0].trim().equals("BaseFont.HELVETICA_OBLIQUE")) {
                fontType[0] = BaseFont.HELVETICA_OBLIQUE;
                encoding = BaseFont.CP1252;
            } else if (fontType[0].trim().equals("BaseFont.ZAPFDINGBATS")) {
                fontType[0] = BaseFont.ZAPFDINGBATS;
                encoding = BaseFont.ZAPFDINGBATS;
            } else {
                fontType[0] = BaseFont.COURIER;
                encoding = BaseFont.CP1252;
            }

            BaseFont bf = BaseFont.createFont(fontType[0], encoding, BaseFont.NOT_EMBEDDED);
            String propValue = props.getProperty(temp.toString());
            //if not in regular config then check measurements
            if (propValue == null) {
                propValue = measurements.getProperty(temp.toString(), "");
            }

            ColumnText ct = new ColumnText(cb);
            // write in a rectangle area
            if (cfgVal.length >= 9) {
                Font font = new Font(bf, Integer.parseInt(cfgVal[5].trim()), fontFlags);
                ct.setSimpleColumn(Integer.parseInt(cfgVal[1].trim()), (height - Integer.parseInt(cfgVal[2]
                        .trim())), Integer.parseInt(cfgVal[7].trim()), (height - Integer.parseInt(cfgVal[8]
                        .trim())), Integer.parseInt(cfgVal[9].trim()), (cfgVal[0].trim().equals("left") ?
                        Element.ALIGN_LEFT : (cfgVal[0].trim().equals("right") ? Element.ALIGN_RIGHT :
                        Element.ALIGN_CENTER)));

                ct.setText(new Phrase(12, propValue, font));
                ct.go();
                continue;
            }

            //adapted by DENNIS WARREN June 2012 to allow a colour rectangle
            // handy for covering up parts of a document
            if (temp.toString().startsWith("__$rectangle")) {

                float llx = Float.parseFloat(cfgVal[0].trim());
                float lly = Float.parseFloat(cfgVal[1].trim());
                float urx = Float.parseFloat(cfgVal[2].trim());
                float ury = Float.parseFloat(cfgVal[3].trim());

                Rectangle rec = new Rectangle(llx, lly, urx, ury);
                rec.setBackgroundColor(Color.WHITE);
                cb.rectangle(rec);

            } else if (temp.toString().startsWith("__$line")) {
                cb.setRGBColorStrokeF(0f, 0f, 0f);
                cb.setLineWidth(Float.parseFloat(cfgVal[4].trim()));
                cb.moveTo(Float.parseFloat(cfgVal[0].trim()), Float.parseFloat(cfgVal[1].trim()));
                cb.lineTo(Float.parseFloat(cfgVal[2].trim()), Float.parseFloat(cfgVal[3].trim()));
                cb.stroke();

            } else if (temp.toString().startsWith("__")) {
                cb.beginText();
                cb.setFontAndSize(bf, Integer.parseInt(cfgVal[5].trim()));
                cb.showTextAligned((cfgVal[0].trim().equals("left") ? PdfContentByte.ALIGN_LEFT
                        : (cfgVal[0].trim().equals("right") ? PdfContentByte.ALIGN_RIGHT
                        : PdfContentByte.ALIGN_CENTER)), (cfgVal.length >= 7 ? (cfgVal[6]
                        .trim()) : propValue), Integer
                        .parseInt(cfgVal[1].trim()), (height - Integer.parseInt(cfgVal[2].trim())), 0);
                cb.endText();
            } else { // write prop text
                cb.beginText();
                cb.setFontAndSize(bf, Integer.parseInt(cfgVal[5].trim()));
                cb.showTextAligned((cfgVal[0].trim().equals("left") ? PdfContentByte.ALIGN_LEFT
                        : (cfgVal[0].trim().equals("right") ? PdfContentByte.ALIGN_RIGHT
                        : PdfContentByte.ALIGN_CENTER)), (cfgVal.length >= 7 ? ((propValue.equals("") ? "" : cfgVal[6].trim()))
                        : propValue), Integer.parseInt(cfgVal[1]
                        .trim()), (height - Integer.parseInt(cfgVal[2].trim())), 0);

                cb.endText();
            }
        }
    }

    private void plotProperties(Properties tp, Properties props, List<String> xDate, List<String> yHeight, float height, PdfContentByte cb, boolean countEven) {
        StringBuilder temp = null;
        String tempValue = null;
        String className = null;
        String[] tempYcoords;
        int origX = 0;
        int origY = 0;
        Properties args = new Properties();

        for (Enumeration e = tp.propertyNames(); e.hasMoreElements(); ) {
            temp = new StringBuilder(e.nextElement().toString());
            tempValue = tp.getProperty(temp.toString()).trim();
            if (temp.toString().equals("__finalEDB"))
                args.setProperty(temp.toString(), props.getProperty(tempValue));
            else if (temp.toString().equals("__xDateScale"))
                args.setProperty(temp.toString(), props.getProperty(tempValue));
            else if (temp.toString().equals("__dateFormat"))
                args.setProperty(temp.toString(), tempValue);
            else if (temp.toString().equals("__nMaxPixX"))
                args.setProperty(temp.toString(), tempValue);
            else if (temp.toString().equals("__nMaxPixY"))
                args.setProperty(temp.toString(), tempValue);
            else if (temp.toString().equals("__fStartX"))
                args.setProperty(temp.toString(), tempValue);
            else if (temp.toString().equals("__fEndX"))
                args.setProperty(temp.toString(), tempValue);
            else if (temp.toString().equals("__fStartY"))
                args.setProperty(temp.toString(), tempValue);
            else if (temp.toString().equals("__fEndY"))
                args.setProperty(temp.toString(), tempValue);
            else if (temp.toString().equals("__origX"))
                origX = Integer.parseInt(tempValue);
            else if (temp.toString().equals("__origY"))
                origY = Integer.parseInt(tempValue);
            else if (temp.toString().equals("__className"))
                className = tempValue;
            else {
                MiscUtils.getLogger().debug("Adding xDate " + temp.toString() + " VAL: " + props.getProperty(temp.toString()));
                MiscUtils.getLogger().debug("Adding yHeight " + tempValue + " VAL: " + props.getProperty(tempValue));
                xDate.add(props.getProperty(temp.toString()));
                yHeight.add(props.getProperty(tempValue));
            }
        } // end for read in from config file                                                

        FrmPdfGraphic pdfGraph = FrmGraphicFactory.create(className);
        pdfGraph.init(args);

        Properties gProp = pdfGraph.getGraphicXYProp(xDate, yHeight);

        //draw the pic
        cb.setLineWidth(1.5f);

        if (countEven) {
            cb.setRGBColorStrokeF(0f, 0f, 255f);
        } else {
            cb.setRGBColorStrokeF(255f, 0f, 0f);
        }

        for (Enumeration e = gProp.propertyNames(); e.hasMoreElements(); ) {
            temp = new StringBuilder(e.nextElement().toString());
            tempValue = gProp.getProperty(temp.toString(), "");

            if (tempValue.equals("")) {
                continue;
            }

            tempYcoords = tempValue.split(",");
            for (int idx = 0; idx < tempYcoords.length; ++idx) {
                tempValue = tempYcoords[idx];
                cb.circle((origX + Float.parseFloat(temp.toString())), (height - origY + Float.parseFloat(tempValue)), 1.5f);
                cb.stroke();
            }
        }
    }
}
