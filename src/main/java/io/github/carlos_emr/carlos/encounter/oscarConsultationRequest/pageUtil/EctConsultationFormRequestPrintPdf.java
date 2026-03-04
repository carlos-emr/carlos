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


/*
 * EctConsultationFormRequestPrintPdf.java
 *
 * Created on November 19, 2007, 4:05 PM
 */

package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.printing.FontSettings;
import io.github.carlos_emr.carlos.commn.printing.PdfWriterFactory;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.clinic.ClinicData;
import io.github.carlos_emr.carlos.lab.ca.all.pageUtil.LabPDFCreator;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import io.github.carlos_emr.carlos.util.ConcatPDF;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

import java.awt.Color;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfImportedPage;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfWriter;

/**
 * Generates a consultation request PDF using a pre-designed PDF template with absolute-positioned
 * text fields, then concatenates attached documents and lab results.
 *
 * <p>Unlike {@link ConsultationPDFCreator} which builds a table-based layout programmatically,
 * this class overlays consultation data onto a pre-existing PDF template located at
 * {@code /oscar/oscarEncounter/oscarConsultationRequest/props/consultationFormRequest.pdf}.
 * Text is positioned using absolute coordinates via OpenPDF's {@link PdfContentByte} and
 * {@link ColumnText} for flowing text sections (clinical information, medications, etc.).</p>
 *
 * <p>The output PDF is saved to the configured {@code DOCUMENT_DIR}, then combined with
 * all attached PDF documents and lab results via {@link ConcatPDF}. Lab results are
 * rendered into temporary PDFs using {@link LabPDFCreator}. The final concatenated PDF
 * is streamed directly to the HTTP response as a download attachment.</p>
 *
 * @see ConsultationPDFCreator
 * @see ConcatPDF
 * @see LabPDFCreator
 * @since 2007-12-15
 */
public class EctConsultationFormRequestPrintPdf {
    private HttpServletRequest request;
    private HttpServletResponse response;

    private PdfReader reader;
    private PdfWriter writer;
    private ColumnText ct;
    private Document document;
    private PdfContentByte cb;
    private BaseFont bf;
    private float height;
    private final float LINEHEIGHT = 14;
    private final float FONTSIZE = 10;

    /**
     * Constructs a new instance with the given HTTP request and response.
     *
     * @param request  HttpServletRequest containing the consultation request ID and demographic data
     * @param response HttpServletResponse where the final combined PDF will be streamed
     */
    public EctConsultationFormRequestPrintPdf(HttpServletRequest request, HttpServletResponse response) {
        this.request = request;
        this.response = response;
    }

    /**
     * Generates the consultation request PDF from the template and combines it with attached documents.
     *
     * <p>Loads the consultation request data, overlays it onto the PDF template with
     * absolute-positioned text fields, saves the result to {@code DOCUMENT_DIR}, then
     * calls {@link #combinePDFs} to append all attached documents and lab results.</p>
     *
     * @param loggedInInfo LoggedInInfo the authenticated session context
     * @throws IOException       when an I/O error occurs reading the template or writing the PDF
     * @throws DocumentException when OpenPDF encounters an error during PDF construction
     */
    public void printPdf(LoggedInInfo loggedInInfo) throws IOException, DocumentException {

        EctConsultationFormRequestUtil reqForm = new EctConsultationFormRequestUtil();
        reqForm.estRequestFromId(loggedInInfo, (String) request.getAttribute("reqId"));

        // init req form info
        reqForm.specAddr = request.getParameter("address");
        if (reqForm.specAddr == null) {
            reqForm.specAddr = new String();
        }
        reqForm.specPhone = request.getParameter("phone");
        if (reqForm.specPhone == null) {
            reqForm.specPhone = "";
        }
        reqForm.specFax = request.getParameter("fax");
        if (reqForm.specFax == null) {
            reqForm.specFax = "";
        }

        //Create new file to save form to
        String path = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");
        String fileName = path + "ConsultationRequestForm-" + UtilDateUtilities.getToday("yyyy-MM-dd.hh.mm.ss") + ".pdf";
        FileOutputStream out = new FileOutputStream(fileName);

        //Create the document we are going to write to
        document = new Document();
        // writer = PdfWriter.getInstance(document,out);
        writer = PdfWriterFactory.newInstance(document, out, FontSettings.HELVETICA_6PT);

        //Use the template located at '/oscar/oscarEncounter/oscarConsultationRequest/props'
        reader = new PdfReader("/oscar/oscarEncounter/oscarConsultationRequest/props/consultationFormRequest.pdf");
        Rectangle pSize = reader.getPageSize(1);
        height = pSize.getHeight();
        document.setPageSize(pSize);

        document.addTitle("Consultation Form Request");
        document.addCreator("CARLOS EMR");
        document.open();

        //Create the font we are going to print to
        bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);

        cb = writer.getDirectContent();
        ct = new ColumnText(cb);
        cb.setColorStroke(Color.BLACK);

        // start writing the pdf document
        PdfImportedPage page1 = writer.getImportedPage(reader, 1);
        cb.addTemplate(page1, 1, 0, 0, 1, 0, 0);
        // addFooter();
        setAppointmentInfo(reqForm);

        // add the dynamically positioned text elements
        float dynamicHeight = 0;
        dynamicHeight = addDynamicPositionedText("Reason For Consultation: ", reqForm.reasonForConsultation, dynamicHeight, reqForm);
        dynamicHeight = addDynamicPositionedText("Pertinent Clinical Information: ", reqForm.clinicalInformation, dynamicHeight, reqForm);
        dynamicHeight = addDynamicPositionedText("Significant Concurrent Problems: ", reqForm.concurrentProblems, dynamicHeight, reqForm);
        dynamicHeight = addDynamicPositionedText("Current Medications: ", reqForm.currentMedications, dynamicHeight, reqForm);
        dynamicHeight = addDynamicPositionedText("Allergies: ", reqForm.allergies, dynamicHeight, reqForm);

        document.close();
        reader.close();
        writer.close();
        out.close();

        // combine the recently created pdf with any pdfs that were added to the consultation request form
        combinePDFs(loggedInInfo, fileName);

    }


    /**
     * Adds a labeled text section at a dynamically calculated vertical position.
     *
     * <p>Calculates the required vertical space based on text length. If insufficient
     * room remains on the current page, starts a new page before rendering. The label
     * is displayed in bold followed by the text content in normal font.</p>
     *
     * @param name          String the bold label text (e.g., "Reason For Consultation: ")
     * @param text          String the content text to display
     * @param dynamicHeight float the current vertical offset from the starting position
     * @param reqForm       EctConsultationFormRequestUtil used for page header data on new pages
     * @return float the updated dynamic height after adding this text section
     * @throws DocumentException when OpenPDF encounters an error rendering the text
     */
    private float addDynamicPositionedText(String name, String text, float dynamicHeight, EctConsultationFormRequestUtil reqForm) throws DocumentException {
        if (text != null && text.length() > 0) {
            Font boldFont = new Font(bf, FONTSIZE, Font.BOLD);
            Font font = new Font(bf, FONTSIZE, Font.NORMAL);
            float lineCount = (name.length() + text.length()) / 100;

            // if there is not enough room on the page for the text start on the next page
            if ((height - 264 - dynamicHeight - lineCount * LINEHEIGHT) < LINEHEIGHT * 3) {
                nextPage(reqForm);
                dynamicHeight = LINEHEIGHT - 152;
            }

            ct.setSimpleColumn(Float.valueOf(85), height - 264 - dynamicHeight - lineCount * LINEHEIGHT, Float.valueOf(526), height - 250 - dynamicHeight, LINEHEIGHT, Element.ALIGN_LEFT);
            ct.addText(new Phrase(name, boldFont));
            ct.addText(new Phrase(text, font));
            ct.go();
            dynamicHeight += lineCount * LINEHEIGHT + LINEHEIGHT * 2;
        }

        return dynamicHeight;
    }

    /**
     * Renders the static consultation request fields (clinic header, specialist info, patient info)
     * at their fixed positions on the first page of the PDF template.
     *
     * @param reqForm EctConsultationFormRequestUtil containing all consultation request data
     * @throws DocumentException when OpenPDF encounters an error during text rendering
     */
    private void setAppointmentInfo(EctConsultationFormRequestUtil reqForm) throws DocumentException {

        printClinicData();
        Font font = new Font(bf, FONTSIZE, Font.NORMAL);

        // Set consultant info
        cb.beginText();
        cb.setFontAndSize(bf, FONTSIZE);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, reqForm.referalDate, 190, height - 112, 0);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, reqForm.urgency.equals("1") ? "Urgent" : (reqForm.urgency.equals("2") ? "Non-Urgent" : "Return"), 190, height - 125, 0);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, reqForm.getServiceName(reqForm.service), 190, height - 139, 0);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, reqForm.getSpecailistsName(reqForm.specialist), 190, height - 153, 0);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, reqForm.specPhone, 190, height - 166, 0);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, reqForm.specFax, 190, height - 181, 0);
        cb.endText();
        ct.setSimpleColumn(Float.valueOf(190), height - 223, Float.valueOf(290), height - 181, LINEHEIGHT, Element.ALIGN_LEFT);
        ct.addText(new Phrase(reqForm.specAddr.replaceAll("<br>", "\n"), font));
        ct.go();

        // Set patient info
        cb.beginText();
        cb.setFontAndSize(bf, FONTSIZE);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, reqForm.patientName, 385, height - 112, 0);
        cb.endText();
        ct.setSimpleColumn(Float.valueOf(385), height - 153, Float.valueOf(585), height - 112, LINEHEIGHT, Element.ALIGN_LEFT);
        ct.addText(new Phrase(reqForm.patientAddress.replaceAll("<br>", " "), font));
        ct.go();

        cb.beginText();
        cb.setFontAndSize(bf, FONTSIZE);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, reqForm.patientPhone, 385, height - 166, 0);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, reqForm.patientDOB, 385, height - 181, 0);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, (reqForm.patientHealthCardType + " " + reqForm.patientHealthNum + " " + reqForm.patientHealthCardVersionCode).trim(), 440, height - 195, 0);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, reqForm.appointmentHour + ":" + reqForm.appointmentMinute + " " + reqForm.appointmentPm + " " + reqForm.appointmentDate, 440, height - 208, 0);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, reqForm.patientChartNo, 385, height - 222, 0);
        cb.endText();
    }

    /**
     * Starts a new page using page 2 of the PDF template and prints the clinic header
     * and patient name.
     *
     * @param reqForm EctConsultationFormRequestUtil containing the patient name for the header
     */
    private void nextPage(EctConsultationFormRequestUtil reqForm) {
        PdfImportedPage page2 = writer.getImportedPage(reader, 2);
        document.newPage();
        cb.addTemplate(page2, 1, 0, 0, 1, 0, 0);

        printClinicData();
        cb.beginText();
        cb.setFontAndSize(bf, FONTSIZE);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, reqForm.patientName, 110, height - 82, 0);
        cb.endText();
    }

    /**
     * Renders the clinic name, address, phone, and fax at the top of the current page.
     */
    private void printClinicData() {
        ClinicData clinic = new ClinicData();
        clinic.refreshClinicData();

        cb.beginText();

        cb.setFontAndSize(bf, 16);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, clinic.getClinicName(), 90, height - 70, 0);

        cb.setFontAndSize(bf, FONTSIZE);
        cb.showTextAligned(PdfContentByte.ALIGN_RIGHT, clinic.getClinicAddress() + ", " + clinic.getClinicCity() + ", " + clinic.getClinicProvince() + ", " + clinic.getClinicPostal(), 533, height - 70, 0);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, clinic.getClinicPhone(), 360, height - 82, 0);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, clinic.getClinicFax(), 471, height - 82, 0);

        cb.endText();
    }

    /**
     * Combines the generated consultation PDF with all attached documents and lab results,
     * then streams the concatenated PDF to the HTTP response.
     *
     * <p>Collects attached PDF documents, generates PDFs for attached lab results using
     * {@link LabPDFCreator}, and concatenates everything using {@link ConcatPDF}.
     * The combined PDF is served as a downloadable attachment.</p>
     *
     * @param loggedInInfo    LoggedInInfo the authenticated session context
     * @param currentFileName String the path to the consultation form PDF just generated
     * @throws IOException when an I/O error occurs during file reading or response writing
     */
    private void combinePDFs(LoggedInInfo loggedInInfo, String currentFileName) throws IOException {

        String demoNo = (String) request.getAttribute("demo");
        String reqId = (String) request.getAttribute("reqId");
        ArrayList<EDoc> consultdocs = EDocUtil.listDocs(loggedInInfo, demoNo, reqId, EDocUtil.ATTACHED);
        ArrayList<Object> pdfDocs = new ArrayList<Object>();

        // add recently created pdf to the list
        pdfDocs.add(currentFileName);

        for (int i = 0; i < consultdocs.size(); i++) {
            EDoc curDoc = consultdocs.get(i);
            if (curDoc.isPDF())
                pdfDocs.add(curDoc.getFilePath());
        }
        // TODO:need to do something about the docs that are not PDFs
        // create pdfs from attached labs
        PatientLabRoutingDao dao = SpringUtils.getBean(PatientLabRoutingDao.class);

        for (Object[] i : dao.findRoutingsAndConsultDocsByRequestId(ConversionUtils.fromIntString(reqId), "L")) {
            PatientLabRouting p = (PatientLabRouting) i[0];

            String segmentId = "" + p.getLabNo();
            request.setAttribute("segmentID", segmentId);
            MessageHandler handler = Factory.getHandler(segmentId);
            String fileName = OscarProperties.getInstance().getProperty("DOCUMENT_DIR") + "//" + handler.getPatientName().replaceAll("\\s", "_") + "_" + handler.getMsgDate() + "_LabReport.pdf";

            try (OutputStream os = new FileOutputStream(fileName)) {
                LabPDFCreator pdf = new LabPDFCreator(request, os);
                pdf.printPdf();
                pdfDocs.add(fileName);
            } catch (Exception e) {
                request.setAttribute("printError", Boolean.valueOf(true));
                MiscUtils.getLogger().error("Failed while printing lab document " + p.getLabNo(), e);
                break;
            }
        }

        response.setContentType("application/pdf");  //octet-stream
        response.setHeader("Content-Disposition", "attachment; filename=\"ConsultationFormRequest.pdf\"");
        ConcatPDF.concat(pdfDocs, response.getOutputStream());

    }
}
