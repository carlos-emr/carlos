//CHECKSTYLE:OFF
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
 */


/*
 * EctConsultationFormRequestPrintPdf.java
 *
 * Created on November 19, 2007, 4:05 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil;

import java.awt.Color;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.commn.dao.PatientLabRoutingDao;
import ca.openosp.openo.commn.model.PatientLabRouting;
import ca.openosp.openo.commn.printing.FontSettings;
import ca.openosp.openo.commn.printing.PdfWriterFactory;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.OscarProperties;
import ca.openosp.openo.documentManager.EDoc;
import ca.openosp.openo.documentManager.EDocUtil;
import ca.openosp.openo.clinic.ClinicData;
import ca.openosp.openo.lab.ca.all.pageUtil.LabPDFCreator;
import ca.openosp.openo.lab.ca.all.parsers.Factory;
import ca.openosp.openo.lab.ca.all.parsers.MessageHandler;
import ca.openosp.openo.util.ConcatPDF;
import ca.openosp.openo.util.ConversionUtils;
import ca.openosp.openo.util.UtilDateUtilities;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfImportedPage;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfWriter;

/**
 * @author wrighd
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
     * Creates a new instance of EctConsultationFormRequestPrintPdf
     */
    public EctConsultationFormRequestPrintPdf(HttpServletRequest request, HttpServletResponse response) {
        this.request = request;
        this.response = response;
    }

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
        document.addCreator("OSCAR");
        document.open();

        //Create the font we are going to print to
        bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);

        cb = writer.getDirectContent();
        ct = new ColumnText(cb);
        cb.setColorStroke(new BaseColor(0, 0, 0));

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
