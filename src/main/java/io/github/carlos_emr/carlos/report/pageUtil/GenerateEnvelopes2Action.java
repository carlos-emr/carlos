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


package io.github.carlos_emr.carlos.report.pageUtil;

import org.openpdf.text.pdf.PdfAction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.demographic.data.DemographicData;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfWriter;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;

/**
 * Struts2 action that generates mailing envelope PDFs for selected patient demographics.
 *
 * <p>Creates a multi-page PDF with one #10 envelope (684 x 297 points) per patient,
 * formatted with the patient's name and mailing address. Optionally embeds JavaScript
 * for auto-printing to a configured default envelope printer with silent print support.</p>
 *
 * <p>Printer preferences are loaded from {@link UserProperty} settings:
 * {@code DEFAULT_PRINTER_PDF_ENVELOPE} and {@code DEFAULT_PRINTER_PDF_ENVELOPE_SILENT_PRINT}.</p>
 *
 * <p>Requires the {@code _report} read privilege. Uses OpenPDF ({@code org.openpdf.*})
 * for PDF generation.</p>
 *
 * @see UserProperty#DEFAULT_PRINTER_PDF_ENVELOPE
 * @since 2006-09-25
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class GenerateEnvelopes2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();
    private static final Pattern DEMOGRAPHIC_NO = Pattern.compile("\\d{1,10}");
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Request attribute set when the submission names no printable patient. {@code GenerateLetters.jsp}
     * renders the localized "no patients selected" notice when it is {@link Boolean#TRUE}.
     */
    static final String NO_PATIENTS_SELECTED_ATTRIBUTE = "noPatientsSelected";

    /**
     * Generates the envelope PDF for the selected demographics and writes it to the response.
     *
     * <p>Loads user printer preferences, creates a #10 envelope-sized PDF for each
     * selected patient, and optionally adds auto-print JavaScript. The PDF is rendered into
     * memory first so that a generation failure can still produce a deliberate error response
     * instead of a truncated download.</p>
     *
     * @return String {@link #NONE} once the PDF (or an error status) has been written directly to
     *         the response, or {@link #INPUT} when no printable patient was selected so the
     *         Generate Letters page can explain the problem
     * @throws SecurityException if the logged-in user lacks {@code _report} read privilege
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String execute() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_report", "r", null)) {
            throw new SecurityException("missing required sec object (_report)");
        }

        // The envelope button on Generate Letters submits whatever boxes are checked, so an empty
        // selection is a normal user mistake, not a server fault. Answer it on the page instead
        // of failing with a NullPointerException (issue #3963).
        List<Demographic> patients = loadPrintablePatients(loggedInInfo, request.getParameterValues("demos"));
        if (patients.isEmpty()) {
            logger.warn("Envelope generation requested with no printable patients selected");
            request.setAttribute(NO_PATIENTS_SELECTED_ATTRIBUTE, Boolean.TRUE);
            return INPUT;
        }

        String curUser_no = (String) request.getSession().getAttribute("user");
        UserPropertyDAO propertyDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
        UserProperty prop;
        String defaultPrinterNamePDFLabel = "";
        Boolean silentPrintPDFLabel = false;
        prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_PDF_ENVELOPE);
        if (prop != null) {
            defaultPrinterNamePDFLabel = prop.getValue();
        }
        prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_PDF_ENVELOPE_SILENT_PRINT);
        if (prop != null) {
            if ("yes".equalsIgnoreCase(prop.getValue())) {
                silentPrintPDFLabel = true;
            }
        }

        byte[] pdf;
        try {
            pdf = renderEnvelopes(patients, buildAutoPrintScript(defaultPrinterNamePDFLabel, silentPrintPDFLabel));
        } catch (RuntimeException e) {
            // OpenPDF's DocumentException is unchecked, so this also covers document build failures.
            logger.error("Envelope PDF generation failed", e);
            sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return NONE;
        }

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "filename=\"envelopePDF-" + UtilDateUtilities.getToday("yyyy-mm-dd.hh.mm.ss") + ".pdf\"");
        response.setContentLength(pdf.length);
        try {
            response.getOutputStream().write(pdf);
        } catch (IOException ioe) {
            // Headers are already committed; the client most likely went away mid-download.
            logger.warn("Unable to write envelope PDF to the response", ioe);
        }
        return NONE;
    }

    /**
     * Resolves the submitted demographic numbers to patients that can be printed on an envelope.
     * Blank, non-numeric, unknown, and not-visible demographic numbers are skipped rather than
     * failing the whole batch.
     *
     * @param loggedInInfo LoggedInInfo the current user, used for the demographic read check
     * @param demos String[] the raw {@code demos} request values; may be {@code null}
     * @return List&lt;Demographic&gt; the printable patients in submission order; never {@code null}
     */
    List<Demographic> loadPrintablePatients(LoggedInInfo loggedInInfo, String[] demos) {
        List<Demographic> patients = new ArrayList<>();
        if (demos == null) {
            return patients;
        }
        DemographicData demoData = new DemographicData();
        for (String demo : demos) {
            if (demo == null || !DEMOGRAPHIC_NO.matcher(demo).matches()) {
                continue;
            }
            Demographic d = demoData.getDemographic(loggedInInfo, demo);
            if (d != null) {
                patients.add(d);
            }
        }
        return patients;
    }

    /**
     * Builds the Acrobat open-action script that sends the envelopes to the user's default
     * envelope printer.
     *
     * <p>The printer name is a free-text user preference that is embedded inside a JavaScript
     * string literal, so it is encoded with {@link SafeEncode#forJavaScript(String)}; otherwise a
     * quote in the preference would break out of the literal and run as PDF JavaScript.</p>
     *
     * @param printerName String the configured printer name; blank means no auto-print
     * @param silentPrint boolean whether to suppress the print dialog
     * @return String the script, or an empty string when no printer is configured
     */
    static String buildAutoPrintScript(String printerName, boolean silentPrint) {
        if (printerName == null || printerName.isEmpty()) {
            return "";
        }
        StringBuilder script = new StringBuilder("var params = this.getPrintParams();")
                .append("params.pageHandling=params.constants.handling.none;")
                .append("params.printerName='").append(SafeEncode.forJavaScript(printerName)).append("';");
        if (silentPrint) {
            script.append("params.interactive=params.constants.interactionLevel.silent;");
        }
        return script.append("this.print(params);").toString();
    }

    /**
     * Renders one #10 envelope page per patient into an in-memory PDF.
     *
     * @param patients List&lt;Demographic&gt; the patients to address; must not be empty
     * @param autoPrintScript String the open-action script, possibly empty
     * @return byte[] the complete PDF document
     * @throws DocumentException if OpenPDF cannot build the document
     */
    byte[] renderEnvelopes(List<Demographic> patients, String autoPrintScript) throws DocumentException {
        //TODO: Change to be able to use other size envelopes
        Rectangle _10Envelope = new Rectangle(0, 0, 684, 297);
        float marginLeft = 252;
        float marginRight = 0;
        float marginTop = 144;
        float marginBottom = 0;
        Document document = new Document(_10Envelope, marginLeft, marginRight, marginTop, marginBottom);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, out);
        document.open();
        try {
            for (Demographic d : patients) {
                document.add(getEnvelopeLabel(formatEnvelopeLabel(d)));
                document.newPage();
            }
            PdfAction action = PdfAction.javaScript(autoPrintScript, writer);
            writer.setOpenAction(action);
        } finally {
            document.close();
        }
        return out.toByteArray();
    }

    /**
     * Formats the mailing block for one patient. Missing name or address parts render as empty
     * text rather than the literal word {@code null}.
     *
     * @param d Demographic the patient
     * @return String name, street, "city, province" and postal code on separate lines
     */
    static String formatEnvelopeLabel(Demographic d) {
        return Objects.toString(d.getFirstName(), "") + " " + Objects.toString(d.getLastName(), "") + "\n"
                + Objects.toString(d.getAddress(), "") + "\n"
                + Objects.toString(d.getCity(), "") + ", " + Objects.toString(d.getProvince(), "") + "\n"
                + Objects.toString(d.getPostal(), "");
    }

    private void sendError(int status) {
        try {
            if (!response.isCommitted()) {
                response.sendError(status);
            }
        } catch (IOException ioe) {
            logger.warn("Unable to send envelope error status {}", status, ioe);
        }
    }

    /**
     * Creates a formatted paragraph for an envelope address label.
     *
     * @param text String the address text with newline separators
     * @return Paragraph the formatted label in Helvetica 18pt with 22pt leading
     */
    Paragraph getEnvelopeLabel(String text) {
        Paragraph p = new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA, 18));
        p.setLeading(22);
        return p;
    }
}
