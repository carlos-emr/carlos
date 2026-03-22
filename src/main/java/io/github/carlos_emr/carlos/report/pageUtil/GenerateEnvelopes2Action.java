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

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
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

/**
 * Struts 2 action that generates addressed envelopes for a list of patients
 * using JasperReports. Merges demographic data (name, address) into an envelope
 * template and streams the resulting PDF. Requires {@code _report} read privilege.
 *
 * @since 2001-01-01
 */
public class GenerateEnvelopes2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Generates the envelope PDF for the selected demographics and writes it to the response.
     *
     * <p>Loads user printer preferences, creates a #10 envelope-sized PDF for each
     * selected patient, and optionally adds auto-print JavaScript. Returns {@code null}
     * because the response is written directly via the output stream.</p>
     *
     * @return String {@code null} (response is written directly as PDF)
     * @throws SecurityException if the logged-in user lacks {@code _report} read privilege
     */
    public String execute() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_report", "r", null)) {
            throw new SecurityException("missing required sec object (_report)");
        }

        String[] demos = request.getParameterValues("demos");

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
            if (prop.getValue().equalsIgnoreCase("yes")) {
                silentPrintPDFLabel = true;
            }
        }
        String exportPdfJavascript = "";

        if (defaultPrinterNamePDFLabel != null && !defaultPrinterNamePDFLabel.isEmpty()) {
            exportPdfJavascript = "var params = this.getPrintParams();"
                    + "params.pageHandling=params.constants.handling.none;"
                    + "params.printerName='" + defaultPrinterNamePDFLabel + "';";
            if (silentPrintPDFLabel == true) {
                exportPdfJavascript += "params.interactive=params.constants.interactionLevel.silent;";
            }
            exportPdfJavascript += "this.print(params);";
        }
        //TODO: Change to be able to use other size envelopes
        Rectangle _10Envelope = new Rectangle(0, 0, 684, 297);
        float marginLeft = 252;
        float marginRight = 0;
        float marginTop = 144;
        float marginBottom = 0;
        Document document = new Document(_10Envelope, marginLeft, marginRight, marginTop, marginBottom);
        response.setContentType("application/pdf");
        //response.setHeader("Content-Disposition", "attachment; filename=\"envelopePDF-"+UtilDateUtilities.getToday("yyyy-mm-dd.hh.mm.ss")+".pdf\"");
        response.setHeader("Content-Disposition", "filename=\"envelopePDF-" + UtilDateUtilities.getToday("yyyy-mm-dd.hh.mm.ss") + ".pdf\"");

        try {
            PdfWriter writer = PdfWriter.getInstance(document, response.getOutputStream());
            document.open();


            for (int i = 0; i < demos.length; i++) {
                DemographicData demoData = new DemographicData();
                Demographic d = demoData.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), demos[i]);
                String address = d.getAddress() == null ? "" : d.getAddress();
                String city = d.getCity() == null ? "" : d.getCity();
                String province = d.getProvince() == null ? "" : d.getProvince();
                String postal = d.getPostal() == null ? "" : d.getPostal();
                String envelopeLabel = d.getFirstName() + " " + d.getLastName() + "\n" + address + "\n" + city + ", " + province + "\n" + postal;

                document.add(getEnvelopeLabel(envelopeLabel));
                document.newPage();
            }
            PdfAction action = PdfAction.javaScript(exportPdfJavascript, writer);
            writer.setOpenAction(action);
        } catch (DocumentException de) {
            logger.error("", de);
        } catch (IOException ioe) {
            logger.error("", ioe);
        }
        document.close();


        return null;
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
