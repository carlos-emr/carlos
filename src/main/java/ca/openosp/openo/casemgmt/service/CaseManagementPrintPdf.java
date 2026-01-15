//CHECKSTYLE:OFF
/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 */


package ca.openosp.openo.casemgmt.service;


import ca.openosp.openo.prescript.data.RxPrescriptionData;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfWriter;
import org.apache.commons.lang3.StringUtils;
import ca.openosp.openo.PMmodule.model.Program;
import ca.openosp.openo.PMmodule.model.ProgramProvider;
import ca.openosp.openo.casemgmt.model.CaseManagementNote;
import ca.openosp.openo.commn.model.Allergy;
import ca.openosp.openo.commn.model.Prevention;
import ca.openosp.openo.commn.printing.FontSettings;
import ca.openosp.openo.commn.printing.PdfWriterFactory;
import ca.openosp.openo.managers.ProgramManager2;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.OscarProperties;
import ca.openosp.openo.clinic.ClinicData;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ResourceBundle;

/**
 * @author rjonasz
 */
public class CaseManagementPrintPdf {

    private HttpServletRequest request;
    private OutputStream os;

    private float upperYcoord;
    private Document document;
    private PdfContentByte cb;
    private BaseFont bf;
    private Font font;
    private boolean newPage = false;

    private SimpleDateFormat formatter;

    public final int LINESPACING = 1;
    public final float LEADING = 12;
    public final float FONTSIZE = 10;
    public final int NUMCOLS = 2;

    /**
     * Creates a new instance of CaseManagementPrintPdf
     */
    public CaseManagementPrintPdf(HttpServletRequest request, OutputStream os) {
        this.request = request;
        this.os = os;
        formatter = new SimpleDateFormat("dd-MMM-yyyy");
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public OutputStream getOutputStream() {
        return os;
    }

    public Font getFont() {
        return font;
    }

    public SimpleDateFormat getFormatter() {
        return formatter;
    }

    public Document getDocument() {
        return document;
    }

    public boolean getNewPage() {
        return newPage;
    }

    public void setNewPage(boolean b) {
        this.newPage = b;
    }

    public BaseFont getBaseFont() {
        return bf;
    }

    public void printDocHeaderFooter() throws IOException, DocumentException {
        //Create the document we are going to write to
        document = new Document();
        PdfWriter writer = PdfWriterFactory.newInstance(document, os, FontSettings.HELVETICA_12PT);

        // writer.setPageEvent(new EndPage());
        document.setPageSize(PageSize.LETTER);
        document.open();

        //Create the font we are going to print to
        bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        font = new Font(bf, FONTSIZE, Font.NORMAL);


        //set up document title and header
        ResourceBundle propResource = ResourceBundle.getBundle("oscarResources");
        String title = propResource.getString("oscarEncounter.pdfPrint.title") + " " + (String) request.getAttribute("demoName") + "\n";
        String gender = propResource.getString("oscarEncounter.pdfPrint.gender") + " " + (String) request.getAttribute("demoSex") + "\n";
        String dob = propResource.getString("oscarEncounter.pdfPrint.dob") + " " + (String) request.getAttribute("demoDOB") + "\n";
        String age = propResource.getString("oscarEncounter.pdfPrint.age") + " " + (String) request.getAttribute("demoAge") + "\n";
        String mrp = propResource.getString("oscarEncounter.pdfPrint.mrp") + " " + (String) request.getAttribute("mrp") + "\n";
        String phn = propResource.getString("oscarEncounter.pdfPrint.phn") + " " + (String) request.getAttribute("demoPhn") + "\n";

        String[] info;
        if ("true".equals(OscarProperties.getInstance().getProperty("print.includeMRP", "true"))) {
            info = new String[]{title, gender, dob, age, phn, mrp};
        } else {
            info = new String[]{title, gender, dob, age, phn};
        }

        ClinicData clinicData = new ClinicData();
        clinicData.refreshClinicData();
        String[] clinic = new String[]{clinicData.getClinicName(), clinicData.getClinicAddress(),
                clinicData.getClinicCity() + ", " + clinicData.getClinicProvince(),
                clinicData.getClinicPostal(), "Phone: " + clinicData.getClinicPhone(), "Fax: " + clinicData.getClinicFax()};

        if ("true".equals(OscarProperties.getInstance().getProperty("print.useCurrentProgramInfoInHeader", "false"))) {
            ProgramManager2 programManager2 = SpringUtils.getBean(ProgramManager2.class);
            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
            ProgramProvider pp = programManager2.getCurrentProgramInDomain(loggedInInfo, loggedInInfo.getLoggedInProviderNo());
            if (pp != null) {
                Program program = pp.getProgram();
                clinic = new String[]{
                        program.getDescription(),
                        program.getAddress(),
                        program.getPhone()
                };
            }
        }
        //Header will be printed at top of every page beginning with p2
        Phrase headerPhrase = new Phrase(LEADING, title, font);
        document.addHeader("", headerPhrase.getContent());

        //Write title with top and bottom borders on p1
        cb = writer.getDirectContent();
        cb.setColorStroke(BaseColor.BLACK);
        cb.setLineWidth(0.5f);

        cb.moveTo(document.left(), document.top());
        cb.lineTo(document.right(), document.top());
        cb.stroke();
        //cb.setFontAndSize(bf, FONTSIZE);

        upperYcoord = document.top() - (font.getCalculatedLeading(LINESPACING) * 2f);

        ColumnText ct = new ColumnText(cb);
        Paragraph p = new Paragraph();
        p.setAlignment(Paragraph.ALIGN_LEFT);
        Phrase phrase = new Phrase();

        float rowCount = Math.max(info.length, clinic.length);
        // Calculates header height based on the leading line space * rowCount
        upperYcoord -= phrase.getLeading() * rowCount;

        String del = "";
        for (int idx = 0; idx < clinic.length; ++idx) {
            String clinicItem = del + StringUtils.trimToEmpty(clinic[idx]);
            p.add(clinicItem);
            phrase.add(del + StringUtils.trimToEmpty(clinic[idx]));
            del = "\n";
        }
        ct.setSimpleColumn(document.left(), upperYcoord, document.right() / 2f, document.top());
        ct.addElement(phrase);
        ct.go();

        // Create and fill a dummy phrase with only new lines to keep the left column the
        // appropriate size in relation to the right column in the event the right column is larger.
        // rowCount has + 1 to account for the blank line created by the getCalculatedLeading above.
        List dummyphrase = Collections.nCopies((int) rowCount + 1, new Phrase("\n"));
        p.addAll(dummyphrase);
        document.add(p);

        //add patient info
        phrase = new Phrase();
        p = new Paragraph();
        p.setAlignment(Paragraph.ALIGN_RIGHT);
        for (int idx = 0; idx < info.length; ++idx) {
            phrase.add(info[idx]);
        }

        ct.setSimpleColumn(document.right() / 2f, upperYcoord, document.right(), document.top());
        p.add(phrase);
        ct.addElement(p);
        ct.go();

        cb.moveTo(document.left(), upperYcoord);
        cb.lineTo(document.right(), upperYcoord);
        cb.stroke();
        upperYcoord -= phrase.getLeading();

    }

    public void printPreventions(List<Prevention> preventions) throws DocumentException {
        if (preventions == null) {
            return;
        }


        if (newPage)
            document.newPage();
        else
            newPage = true;

        Paragraph p = new Paragraph();
        Font obsfont = new Font(bf, FONTSIZE, Font.UNDERLINE);
        Phrase phrase = new Phrase(LEADING, "", obsfont);
        p.setAlignment(Paragraph.ALIGN_CENTER);
        phrase.add("Patient Preventions History");
        p.add(phrase);
        document.add(p);

        Font normal = new Font(bf, FONTSIZE, Font.NORMAL);
        Font curFont;
        for (int idx = 0; idx < preventions.size(); idx++) {
            Prevention prevention = preventions.get(idx);
            p = new Paragraph();
            p.setAlignment(Paragraph.ALIGN_LEFT);
            if (!prevention.isDeleted()) {
                curFont = normal;
                phrase = new Phrase(LEADING, "", curFont);
                String refused = prevention.isRefused() ? " (Refused)" : "";
                 String preventionDate = prevention.getPreventionDate() == null ? "Unknown" : formatter.format(prevention.getPreventionDate());
                 phrase.add(preventionDate + " - ");
                phrase.add(prevention.getPreventionType() + refused);
                p.add(phrase);
                document.add(p);
            }
        }
        if (preventions.isEmpty()) {
            p = new Paragraph();
            p.setAlignment(Paragraph.ALIGN_LEFT);
            Phrase noPreventionsPhrase = new Phrase(LEADING, "", normal);
            noPreventionsPhrase.add("No preventions found");
            p.add(noPreventionsPhrase);
            document.add(p);
        }
    }

    public void printAllergies(List<Allergy> allergies) throws DocumentException {
        if (allergies == null) {
            return;
        }

        if (newPage)
            document.newPage();
        else
            newPage = true;

        Paragraph p = new Paragraph();
        Font obsfont = new Font(bf, FONTSIZE, Font.UNDERLINE);
        Phrase phrase = new Phrase(LEADING, "", obsfont);
        p.setAlignment(Paragraph.ALIGN_CENTER);
        phrase.add("Patient Allergies");
        p.add(phrase);
        document.add(p);

        Font normal = new Font(bf, FONTSIZE, Font.NORMAL);
        Font curFont;
        for (int idx = 0; idx < allergies.size(); idx++) {
            Allergy allergy = allergies.get(idx);

            // Print allergen name (bold)
            p = new Paragraph();
            p.setAlignment(Paragraph.ALIGN_LEFT);
            phrase = new Phrase(LEADING, "", obsfont);
            phrase.add(StringUtils.defaultIfBlank(allergy.getDescription(), "Unknown"));
            p.add(phrase);
            document.add(p);
            curFont = normal;

            if (allergy.getStartDate() != null) {
                p = new Paragraph();
                p.setAlignment(Paragraph.ALIGN_LEFT);
                phrase = new Phrase(LEADING, "Start Date: ", curFont);
                phrase.add(formatter.format(allergy.getStartDate()));
                p.add(phrase);
                document.add(p);
            }

            if (allergy.getReaction() != null && !allergy.getReaction().trim().isEmpty()) {
                p = new Paragraph();
                p.setAlignment(Paragraph.ALIGN_LEFT);
                phrase = new Phrase(LEADING, "Reaction: ", curFont);
                phrase.add(allergy.getReaction());
                p.add(phrase);
                document.add(p);
            }

            if (allergy.getSeverityOfReaction() != null) {
                p = new Paragraph();
                p.setAlignment(Paragraph.ALIGN_LEFT);
                phrase = new Phrase(LEADING, "Severity: ", curFont);
                phrase.add(allergy.getSeverityOfReactionDesc());
                p.add(phrase);
                document.add(p);
            }

            if (allergy.getOnsetOfReaction() != null) {
                p = new Paragraph();
                p.setAlignment(Paragraph.ALIGN_LEFT);
                phrase = new Phrase(LEADING, "Onset: ", curFont);
                phrase.add(allergy.getOnSetOfReactionDesc());
                p.add(phrase);
                document.add(p);
            }

            if (allergy.getLifeStage() != null) {
                p = new Paragraph();
                p.setAlignment(Paragraph.ALIGN_LEFT);
                phrase = new Phrase(LEADING, "Life Stage: ", curFont);
                phrase.add(allergy.getLifeStageDesc());
                p.add(phrase);
                document.add(p);
            }

            if (allergy.getAgeOfOnset() != null && !allergy.getAgeOfOnset().trim().isEmpty()) {
                p = new Paragraph();
                p.setAlignment(Paragraph.ALIGN_LEFT);
                phrase = new Phrase(LEADING, "Age of Onset: ", curFont);
                phrase.add(allergy.getAgeOfOnset());
                p.add(phrase);
                document.add(p);
            }
            document.add(new Phrase("\n", curFont));
        }
        if (allergies.isEmpty()) {
            p = new Paragraph();
            p.setAlignment(Paragraph.ALIGN_LEFT);
            Phrase noAllergiesPhrase = new Phrase(LEADING, "", normal);
            noAllergiesPhrase.add("No allergies found");
            p.add(noAllergiesPhrase);
            document.add(p);
        }
    }

    public void printRx(String demoNo) throws DocumentException {
        printRx(demoNo, null);
    }

    public void printRx(String demoNo, List<CaseManagementNote> cpp) throws DocumentException {
        if (demoNo == null)
            return;

        if (newPage)
            document.newPage();
        else
            newPage = true;

        Paragraph p = new Paragraph();
        Font obsfont = new Font(bf, FONTSIZE, Font.UNDERLINE);
        Phrase phrase = new Phrase(LEADING, "", obsfont);
        p.setAlignment(Paragraph.ALIGN_CENTER);
        phrase.add("Patient Rx History");
        p.add(phrase);
        document.add(p);

        Font normal = new Font(bf, FONTSIZE, Font.NORMAL);

        RxPrescriptionData prescriptData = new RxPrescriptionData();
        RxPrescriptionData.Prescription[] arr = {};
        arr = prescriptData.getUniquePrescriptionsByPatient(Integer.parseInt(demoNo));


        Font curFont;
        for (int idx = 0; idx < arr.length; ++idx) {
            RxPrescriptionData.Prescription drug = arr[idx];
            p = new Paragraph();
            p.setAlignment(Paragraph.ALIGN_LEFT);
            if (drug.isCurrent() && !drug.isArchived()) {
                curFont = normal;
                phrase = new Phrase(LEADING, "", curFont);
                phrase.add(formatter.format(drug.getRxDate()) + " - ");
                phrase.add(drug.getFullOutLine().replaceAll(";", " "));
                p.add(phrase);
                document.add(p);
            }
        }

        if (cpp != null) {
            List<CaseManagementNote> notes = cpp;
            if (notes != null && notes.size() > 0) {
                p = new Paragraph();
                p.setAlignment(Paragraph.ALIGN_LEFT);
                phrase = new Phrase(LEADING, "\nOther Meds\n", obsfont); //TODO:Needs to be i18n
                p.add(phrase);
                document.add(p);
                newPage = false;
                this.printNotes(notes);
            }

        }
    }

    public void printCPP(HashMap<String, List<CaseManagementNote>> cpp) throws DocumentException {
        if (cpp == null)
            return;

        if (newPage)
            document.newPage();
        else
            newPage = true;

        Font obsfont = new Font(bf, FONTSIZE, Font.UNDERLINE);


        Paragraph p = new Paragraph();
        p.setAlignment(Paragraph.ALIGN_CENTER);
        Phrase phrase = new Phrase(LEADING, "\n\n", font);
        p.add(phrase);
        phrase = new Phrase(LEADING, "Patient CPP", obsfont);
        p.add(phrase);
        document.add(p);
        //upperYcoord -= p.leading() * 2f;
        //lworkingYcoord = rworkingYcoord = upperYcoord;
        //ColumnText ct = new ColumnText(cb);
        String[] headings = {"Social History\n", "Other Meds\n", "Medical History\n", "Ongoing Concerns\n", "Reminders\n", "Family History\n", "Risk Factors\n"};
        String[] issueCodes = {"SocHistory", "OMeds", "MedHistory", "Concerns", "Reminders", "FamHistory", "RiskFactors"};
        //String[] content = {cpp.getSocialHistory(), cpp.getFamilyHistory(), cpp.getMedicalHistory(), cpp.getOngoingConcerns(), cpp.getReminders()};

        //init column to left side of page
        //ct.setSimpleColumn(document.left(), document.bottomMargin()+25f, document.right()/2f, lworkingYcoord);

        //int column = 1;
        //Chunk chunk;
        //float bottom = document.bottomMargin()+25f;
        //float middle;
        //bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        //cb.beginText();
        //String headerContd;
        //while there are cpp headings to process

        for (int idx = 0; idx < headings.length; ++idx) {
            p = new Paragraph();
            p.setAlignment(Paragraph.ALIGN_LEFT);
            phrase = new Phrase(LEADING, headings[idx], obsfont);
            p.add(phrase);
            document.add(p);
            newPage = false;
            this.printNotes(cpp.get(issueCodes[idx]));
        }
    }

    public void printNotes(List<CaseManagementNote> notes) throws DocumentException {

        CaseManagementNote note;
        Font obsfont = new Font(bf, FONTSIZE, Font.UNDERLINE);
        Paragraph p;
        Phrase phrase;
        Chunk chunk;

        if (newPage)
            document.newPage();
        else
            newPage = true;

        //Print notes
        for (int idx = 0; idx < notes.size(); ++idx) {
            note = notes.get(idx);
            p = new Paragraph();
            //p.setSpacingBefore(font.leading(LINESPACING)*2f);
            phrase = new Phrase(LEADING, "", font);
            chunk = new Chunk("Documentation Date: " + formatter.format(note.getObservation_date()) + "\n", obsfont);
            phrase.add(chunk);
            phrase.add(note.getNote() + "\n\n");
            p.add(phrase);
            document.add(p);
        }
    }

    public void finish() {
        document.close();
    }

    /*
     *Used to print footers on each page
     */
//    class EndPage extends PdfPageEventHelper {
//        private Date now;
//        private String promoTxt;
//
//        public EndPage() {
//            now = new Date();
//            promoTxt = OscarProperties.getInstance().getProperty("FORMS_PROMOTEXT");
//            if( promoTxt == null ) {
//                promoTxt = "";
//            }
//        }
//
//        public void onEndPage( PdfWriter writer, Document document ) {
//            //Footer contains page numbers and date printed on all pages
//            PdfContentByte cb = writer.getDirectContent();
//            cb.saveState();
//
//            String strFooter = promoTxt + " " + formatter.format(now);
//
//            float textBase = document.bottom();
//            cb.beginText();
//            cb.setFontAndSize(font.getBaseFont(),FONTSIZE);
//            Rectangle page = document.getPageSize();
//            float width = page.getWidth();
//
//            cb.showTextAligned(PdfContentByte.ALIGN_CENTER, strFooter, (width/2.0f), textBase - 20, 0);
//
//            strFooter = "-" + writer.getPageNumber() + "-";
//            cb.showTextAligned(PdfContentByte.ALIGN_CENTER, strFooter, (width/2.0f), textBase-10, 0);
//
//            cb.endText();
//            cb.restoreState();
//        }
//    }


}
