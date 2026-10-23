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


package io.github.carlos_emr.carlos.casemgmt.print;

import io.github.carlos_emr.carlos.commn.printing.PdfFonts;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import jakarta.servlet.http.HttpServletRequest;


import java.awt.Color;
import io.github.carlos_emr.carlos.commn.printing.FontSettings;
import io.github.carlos_emr.carlos.commn.printing.PdfWriterFactory;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import org.openpdf.text.*;
import org.openpdf.text.pdf.*;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.commn.dao.AdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.CustomFilter;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.commn.model.Dxresearch;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.clinic.ClinicData;
import io.github.carlos_emr.carlos.demographic.data.DemographicRelationship;

/**
 * Generates comprehensive patient E-Chart PDFs for CARLOS EMR.
 *
 * <p>Assembles a multi-section PDF document containing patient demographics, clinical notes,
 * prescriptions, allergies, preventions, ticklers, disease registry entries, appointment history,
 * current/past admissions, and active issues. The output is suitable for printing or inclusion
 * in a concatenated medical record package (see {@link io.github.carlos_emr.carlos.casemgmt.service.CaseManagementPrint}).</p>
 *
 * <p>Implements {@link java.io.Closeable} so callers can use try-with-resources to ensure
 * the underlying PDF {@link Document} is always closed, even if an exception occurs during
 * rendering.</p>
 *
 * <p>Uses OpenPDF ({@code org.openpdf.*}) for all PDF generation, with
 * {@link io.github.carlos_emr.carlos.commn.printing.PdfWriterFactory PdfWriterFactory}-installed
 * page-event handlers ({@link io.github.carlos_emr.carlos.casemgmt.service.PageNumberStamper PageNumberStamper}
 * and {@link io.github.carlos_emr.carlos.casemgmt.service.PromoTextStamper PromoTextStamper})
 * that render promotional text and page numbers in the footer of every page.</p>
 *
 * @see io.github.carlos_emr.carlos.casemgmt.web.EChartPrint2Action
 * @see io.github.carlos_emr.carlos.casemgmt.service.CaseManagementPrint
 * @since 2011-08-16
 */
public class OscarChartPrinter implements java.io.Closeable {

    /** Line spacing multiplier for paragraph leading calculations. */
    public final int LINESPACING = 1;
    /** Default leading (inter-line spacing in points) for PDF text. */
    public final float LEADING = 12;
    /** Default font size in points for body text. */
    public final float FONTSIZE = 10;
    /** Default number of columns for tabular layouts. */
    public final int NUMCOLS = 2;

    private Demographic demographic;
    private String signingProvider;

    private PdfWriter writer;

    private HttpServletRequest request;
    private OutputStream os;
    private SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
    private SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm");
    private Document document;
    private BaseFont bf;
    private Font font, boldFont;
    boolean newPage;

    private ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
    private DemographicCustDao demographicCustDao = (DemographicCustDao) SpringUtils.getBean(DemographicCustDao.class);
    private DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
    private OscarAppointmentDao appointmentDao = (OscarAppointmentDao) SpringUtils.getBean(OscarAppointmentDao.class);
    private PreventionDao preventionDao = (PreventionDao) SpringUtils.getBean(PreventionDao.class);
    private DemographicExtDao demographicExtDao = SpringUtils.getBean(DemographicExtDao.class);
    private TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);


    /**
     * Creates a new chart printer and initialises the PDF document with Letter page size
     * and embedded Unicode fonts.
     *
     * @param request HttpServletRequest providing session context and patient attributes
     * @param os OutputStream to which the finished PDF will be written
     * @throws DocumentException if the OpenPDF {@link PdfWriter} cannot be created
     * @throws IOException       if the base font cannot be loaded
     */
    public OscarChartPrinter(HttpServletRequest request, OutputStream os) throws DocumentException, IOException {
        this.request = request;
        this.os = os;

        document = new Document();
        document.setPageSize(PageSize.LETTER);
        writer = PdfWriterFactory.newInstance(document, os, FontSettings.HELVETICA_10PT);
        document.open();
        //Create the font we are going to print to
        bf = PdfFonts.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        font = new Font(bf, FONTSIZE, Font.NORMAL);
        boldFont = new Font(bf, FONTSIZE, Font.BOLD);
    }


    public HttpServletRequest getRequest() {
        return request;
    }

    public OutputStream getOutputStream() {
        return os;
    }

    public void setDemographic(Demographic demographic) {
        this.demographic = demographic;
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

    private Paragraph getParagraph(String value) {
        return PdfFonts.paragraph(value, font);
    }

    /**
     * Closes the PDF document. Delegates to {@link #close()}.
     *
     * @deprecated Use {@link #close()} directly or try-with-resources instead.
     */
    @Deprecated
    public void finish() {
        close();
    }

    /**
     * Closes the underlying PDF {@link Document} and writer.
     * Safe to call multiple times.
     */
    @Override
    public void close() {
        try {
            if (document != null && document.isOpen()) {
                document.close();
            }
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }


    /**
     * Prints the patient header block (name, age, sex, DOB, phone, provider info) and
     * draws a horizontal rule beneath it. Also sets the document-level header phrase
     * for subsequent pages.
     *
     * @throws DocumentException if PDF content cannot be added to the document
     */
    public void printDocHeaderFooter() throws DocumentException {
        String headerTitle = demographic.getFormattedName() + " " + demographic.getAge() + " " + demographic.getSex() + " DOB:" + demographic.getFormattedDob();

        //set up document title and header
        ResourceBundle propResource = ResourceBundle.getBundle("oscarResources");
        String title = propResource.getString("encounter.pdfPrint.title") + " " + (String) request.getAttribute("demoName") + "\n";
        String gender = propResource.getString("encounter.pdfPrint.gender") + " " + (String) request.getAttribute("demoSex") + "\n";
        String dob = propResource.getString("encounter.pdfPrint.dob") + " " + (String) request.getAttribute("demoDOB") + "\n";
        String age = propResource.getString("encounter.pdfPrint.age") + " " + (String) request.getAttribute("demoAge") + "\n";
        String mrp = propResource.getString("encounter.pdfPrint.mrp") + " " + (String) request.getAttribute("mrp") + "\n";
        String[] info = new String[]{title, gender, dob, age, mrp};

        ClinicData clinicData = new ClinicData();
        clinicData.refreshClinicData();
        String[] clinic = new String[]{clinicData.getClinicName(), clinicData.getClinicAddress(),
                clinicData.getClinicCity() + ", " + clinicData.getClinicProvince(),
                clinicData.getClinicPostal(), clinicData.getClinicPhone()};

        if (newPage) {
            document.newPage();
            newPage = false;
        }

        //Header will be printed at top of every page beginning with p2
        Phrase headerPhrase = PdfFonts.phrase(LEADING, title, font);
        document.addHeader("", headerPhrase.getContent());

        getDocument().add(headerPhrase);
        getDocument().add(new Phrase("\n"));

        Paragraph p = PdfFonts.paragraph("Tel:" + demographic.getPhone(), getFont());
        p.setAlignment(Paragraph.ALIGN_LEFT);
        // getDocument().add(p);

        Paragraph p2 = PdfFonts.paragraph("Date of Visit: ", getFont());
        p2.setAlignment(Paragraph.ALIGN_RIGHT);
        // getDocument().add(p);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100f);
        table.getDefaultCell().setBorder(PdfPCell.NO_BORDER);
        PdfPCell cell1 = new PdfPCell(p);
        cell1.setBorder(PdfPCell.NO_BORDER);
        cell1.setHorizontalAlignment(Element.ALIGN_LEFT);
        PdfPCell cell2 = new PdfPCell(p2);
        cell2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell2.setBorder(PdfPCell.NO_BORDER);
        table.addCell(cell1);
        table.addCell(cell2);

        getDocument().add(table);

        table = new PdfPTable(3);
        table.setWidthPercentage(100f);
        table.getDefaultCell().setBorder(PdfPCell.NO_BORDER);
        cell1 = new PdfPCell(getParagraph("Signed Provider:" + ((signingProvider != null) ? signingProvider : "")));
        cell1.setBorder(PdfPCell.NO_BORDER);
        cell1.setHorizontalAlignment(Element.ALIGN_LEFT);
        cell2 = new PdfPCell(getParagraph("RFR:"));
        cell2.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell2.setBorder(PdfPCell.NO_BORDER);
        PdfPCell cell3 = new PdfPCell(getParagraph("Ref:"));
        cell3.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell3.setBorder(PdfPCell.NO_BORDER);
        table.addCell(cell1);
        table.addCell(cell2);
        table.addCell(cell3);

        getDocument().add(table);

        //Write title with top and bottom borders on p1
        PdfContentByte cb = writer.getDirectContent();
        cb.setColorStroke(Color.BLACK);
        cb.setLineWidth(0.5f);

        cb.moveTo(document.left(), document.top() - (font.getCalculatedLeading(LINESPACING) * 5f));
        cb.lineTo(document.right(), document.top() - (font.getCalculatedLeading(LINESPACING) * 5f));
        cb.stroke();
    }


    /**
     * Prints the full patient master record including personal information, contact details,
     * health insurance, care team (physician, nurse, midwife, resident, referral doctor),
     * alerts/notes, and demographic relationships (emergency contacts, substitute decision makers).
     *
     * @throws DocumentException if PDF content cannot be added to the document
     */
    public void printMasterRecord() throws DocumentException {
        if (newPage) {
            document.newPage();
            newPage = false;
        }

        Paragraph p = null;
        Phrase phrase = null;


        p = new Paragraph();
        p.setAlignment(Paragraph.ALIGN_LEFT);
        phrase = PdfFonts.phrase(LEADING, "Patient Information", boldFont);
        PdfFonts.addText(phrase, "\n", phrase.getFont());
        p.add(phrase);
        document.add(p);

        p = new Paragraph();
        phrase = PdfFonts.phrase(LEADING, "", font);
        PdfFonts.addText(phrase, "Title: " + demographic.getTitle() + "\n", phrase.getFont());
        PdfFonts.addText(phrase, "Last Name: " + demographic.getLastName() + "\n", phrase.getFont());
        PdfFonts.addText(phrase, "First Name: " + demographic.getFirstName() + "\n", phrase.getFont());
        PdfFonts.addText(phrase, "Gender: " + demographic.getSex() + "\n", phrase.getFont());
        PdfFonts.addText(phrase, "Date of Birth: " + demographic.getFormattedDob() + "\n", phrase.getFont());
        PdfFonts.addText(phrase, "Offical Language: " + demographic.getOfficialLanguage() + "\n", phrase.getFont());
        PdfFonts.addText(phrase, "Spoken Language: " + demographic.getSpokenLanguage() + "\n", phrase.getFont());
        PdfFonts.addText(phrase, "\n", phrase.getFont());
        p.add(phrase);
        document.add(p);

        p = new Paragraph();
        phrase = PdfFonts.phrase(LEADING, "", font);
        PdfFonts.addText(phrase, "Roster Status: " + demographic.getRosterStatus() + "\n", phrase.getFont());
        PdfFonts.addText(phrase, "Date Rostered: " + "\n", phrase.getFont());
        PdfFonts.addText(phrase, "Patient Status: " + demographic.getPatientStatus() + "\n", phrase.getFont());
        PdfFonts.addText(phrase, "Chart No (MRN): " + demographic.getChartNo() + "\n", phrase.getFont());
        if (demographic.getDateJoined() != null) {
            PdfFonts.addText(phrase, "Date Joined: " + formatter.format(demographic.getDateJoined()) + "\n", phrase.getFont());
        }
        if (demographic.getEndDate() != null) {
            PdfFonts.addText(phrase, "End Date: " + formatter.format(demographic.getEndDate()) + "\n", phrase.getFont());
        }
        PdfFonts.addText(phrase, "\n", phrase.getFont());
        p.add(phrase);
        document.add(p);

        p = new Paragraph();
        phrase = PdfFonts.phrase(LEADING, "", font);
        PdfFonts.addText(phrase, "Address: " + demographic.getAddress() + "\n", phrase.getFont());
        PdfFonts.addText(phrase, "City: " + demographic.getCity() + "\n", phrase.getFont());
        PdfFonts.addText(phrase, "Province: " + demographic.getProvince() + "\n", phrase.getFont());
        PdfFonts.addText(phrase, "Postal Code: " + demographic.getPostal() + "\n", phrase.getFont());
        PdfFonts.addText(phrase, "Email: " + demographic.getEmail() + "\n", phrase.getFont());
        PdfFonts.addText(phrase, "Phone: " + demographic.getPhone() + "\n", phrase.getFont());

        List<DemographicExt> exts = demographicExtDao.getDemographicExtByDemographicNo(demographic.getDemographicNo());
        String phoneExt = null;
        String cell = null;
        for (DemographicExt ext : exts) {
            if (ext.getKey().equals("wPhoneExt")) {
                phoneExt = ext.getValue();
            }
            if (ext.getKey().equals("demo_cell")) {
                cell = ext.getValue();
            }
        }

        PdfFonts.addText(phrase, "Work Phone: " + demographic.getPhone2(), phrase.getFont());
        if (phoneExt != null) {
            PdfFonts.addText(phrase, " ext:" + phoneExt + "\n", phrase.getFont());
        } else {
            PdfFonts.addText(phrase, "\n", phrase.getFont());
        }

        if (cell != null) {
            PdfFonts.addText(phrase, "Cell Phone: " + cell + "\n", phrase.getFont());
        }

        PdfFonts.addText(phrase, "\n", phrase.getFont());
        p.add(phrase);
        document.add(p);

        p = new Paragraph();
        phrase = PdfFonts.phrase(LEADING, "", font);
        PdfFonts.addText(phrase, "Health Insurance #: " + demographic.getHin() + "\n", phrase.getFont());
        PdfFonts.addText(phrase, "HC Type: " + demographic.getHcType() + "\n", phrase.getFont());
        if (demographic.getEffDate() != null) {
            PdfFonts.addText(phrase, "Eff Date: " + formatter.format(demographic.getEffDate()) + "\n", phrase.getFont());
        }
        PdfFonts.addText(phrase, "\n", phrase.getFont());
        p.add(phrase);
        document.add(p);

        DemographicCust demographicCust = demographicCustDao.find(demographic.getDemographicNo());

        p = new Paragraph();
        phrase = PdfFonts.phrase(LEADING, "", font);
        PdfFonts.addText(phrase, "Physician: " + getProviderName(demographic.getProviderNo()) + "\n", phrase.getFont());
        if (demographicCust != null) {
            PdfFonts.addText(phrase, "Nurse: " + getProviderName(demographicCust.getNurse()) + "\n", phrase.getFont());
            PdfFonts.addText(phrase, "Midwife: " + getProviderName(demographicCust.getMidwife()) + "\n", phrase.getFont());
            PdfFonts.addText(phrase, "Resident: " + getProviderName(demographicCust.getResident()) + "\n", phrase.getFont());
        }
        PdfFonts.addText(phrase, "Referral Doctor: " + getReferralDoctor(demographic.getFamilyDoctor()) + "\n", phrase.getFont());
        PdfFonts.addText(phrase, "\n", phrase.getFont());
        p.add(phrase);
        document.add(p);

        p = new Paragraph();
        phrase = PdfFonts.phrase(LEADING, "", font);
        //alerts & notes
        if (demographicCust != null) {
            PdfFonts.addText(phrase, "Alerts: " + demographicCust.getAlert() + "\n", phrase.getFont());
            if (demographicCust.getNotes().length() > 0) {
                PdfFonts.addText(phrase, "Notes: " + SxmlMisc.getXmlContent(demographicCust.getNotes(), "unotes") + "\n", phrase.getFont());
            }
        }
        PdfFonts.addText(phrase, "\n", phrase.getFont());
        p.add(phrase);
        document.add(p);


        //relationships
        p = new Paragraph();
        phrase = PdfFonts.phrase(LEADING, "", font);

        DemographicRelationship demoRel = new DemographicRelationship();

        List<Map<String, String>> demoR = demoRel.getDemographicRelationships(String.valueOf(demographic.getDemographicNo()));
        for (int j = 0; j < demoR.size(); j++) {
            Map<String, String> r = demoR.get(j);
            String relationDemographicNo = r.get("demographic_no");
            Demographic relationDemographic = demographicDao.getClientByDemographicNo(Integer.parseInt(relationDemographicNo));
            String relation = r.get("relation");
            String subDecisionMaker = r.get("sub_decision_maker");
            String emergencyContact = r.get("emergency_contact");
            String notes = r.get("notes");

            PdfFonts.addText(phrase, relation + " - " + relationDemographic.getFormattedName() + " - " + subDecisionMaker + " - " + emergencyContact + " - " + notes + "\n", phrase.getFont());
        }
        PdfFonts.addText(phrase, "\n", phrase.getFont());
        p.add(phrase);
        document.add(p);

    }

    /**
     * Extracts the referral doctor name from the XML-encoded family doctor field.
     *
     * @param field String the XML-encoded family doctor field content
     * @return String the referral doctor name, or empty string if not available
     */
    private String getReferralDoctor(String field) {
        if (field != null && field.length() > 0) {
            return SxmlMisc.getXmlContent(field, "rd");
        }
        return "";
    }

    /**
     * Looks up the formatted display name for a provider by provider number.
     *
     * @param providerNo String the provider number to look up
     * @return String the provider's formatted name, or empty string if not found or {@code null}
     */
    private String getProviderName(String providerNo) {
        if (providerNo == null || providerNo.length() == 0) {
            return "";
        }
        Provider p = providerDao.getProvider(providerNo);
        if (p != null) {
            return p.getFormattedName();
        }
        return "";
    }


    /**
     * Prints the patient's appointment history as a 6-column table (date, from, to, reason,
     * provider, notes) sorted by appointment date.
     *
     * @throws DocumentException if PDF content cannot be added to the document
     */
    public void printAppointmentHistory() throws DocumentException {
        if (newPage) {
            document.newPage();
            newPage = false;
        }

        List<Appointment> appts = appointmentDao.getAppointmentHistory(demographic.getDemographicNo());

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100f);
        table.getDefaultCell().setBorder(PdfPCell.NO_BORDER);
        // Same 12pt size as OpenPDF's default cell font, but embedded like the rest of the chart.
        Font columnFont = PdfFonts.getFont(BaseFont.HELVETICA, 12);
        for (String column : new String[]{"Date", "From", "To", "Reason", "Provider", "Notes"}) {
            table.addCell(PdfFonts.phrase(column, columnFont));
        }

        for (Appointment appt : appts) {
            table.addCell(generalCellForApptHistory(formatter.format(appt.getAppointmentDate())));
            table.addCell(generalCellForApptHistory(timeFormatter.format(appt.getStartTime())));
            table.addCell(generalCellForApptHistory(timeFormatter.format(appt.getEndTime())));
            table.addCell(generalCellForApptHistory(appt.getReason()));
            table.addCell(generalCellForApptHistory(providerDao.getProvider(appt.getProviderNo()).getFormattedName()));
            table.addCell(generalCellForApptHistory(appt.getNotes()));
        }

        getDocument().add(table);
    }


    /**
     * Prints a single Cumulative Patient Profile (CPP) section with an underlined heading
     * followed by its associated clinical notes in compact format.
     *
     * @param heading String the section heading (e.g. "Social History", "Medical History")
     * @param notes Collection&lt;CaseManagementNote&gt; the clinical notes for this CPP section
     * @throws DocumentException if PDF content cannot be added to the document
     */
    public void printCPPItem(String heading, Collection<CaseManagementNote> notes) throws DocumentException {
        if (newPage)
            document.newPage();
        //  else
        //      newPage = true;

        Font obsfont = new Font(bf, FONTSIZE, Font.UNDERLINE);

        Paragraph p = null;
        Phrase phrase = null;


        p = new Paragraph();
        p.setAlignment(Paragraph.ALIGN_LEFT);
        phrase = PdfFonts.phrase(LEADING, heading, obsfont);
        p.add(phrase);
        document.add(p);
        newPage = false;
        this.printNotes(notes, true);
        // No text object is open here: the former cb.endText() made OpenPDF throw "unbalanced
        // begin/end text operators", cutting the chart off after the first CPP section (#5046).
    }


    /**
     * Prints clinical encounter notes to the PDF.
     *
     * @param notes   Collection&lt;CaseManagementNote&gt; the notes to print
     * @param compact boolean if {@code true}, prints date:note on one line; if {@code false},
     *                prints "Impression/Plan: (date)" as a heading followed by the note body
     * @throws DocumentException if PDF content cannot be added to the document
     */
    public void printNotes(Collection<CaseManagementNote> notes, boolean compact) throws DocumentException {

        CaseManagementNote note;
        Font obsfont = new Font(bf, FONTSIZE, Font.UNDERLINE);
        Paragraph p;
        Phrase phrase;

        //if( newPage )
        //     document.newPage();
        // else
        //     newPage = true;

        //Print notes
        Iterator<CaseManagementNote> notesIter = notes.iterator();
        while (notesIter.hasNext()) {
            note = notesIter.next();
            p = new Paragraph();
            //p.setSpacingBefore(font.leading(LINESPACING)*2f);
            phrase = PdfFonts.phrase(LEADING, "", font);

            if (compact) {
                PdfFonts.addText(phrase, formatter.format(note.getObservation_date()) + ":", phrase.getFont());
            } else {
                PdfFonts.addText(phrase, "Impression/Plan: (" + formatter.format(note.getObservation_date()) + ")\n", obsfont);
            }
            if (compact) {
                PdfFonts.addText(phrase, note.getNote() + "\n", phrase.getFont());
            } else {
                PdfFonts.addText(phrase, note.getNote() + "\n\n", phrase.getFont());
            }
            p.add(phrase);
            document.add(p);
        }
    }

    /**
     * Adds a blank line to the document for visual spacing between sections.
     *
     * @throws DocumentException if PDF content cannot be added to the document
     */
    public void printBlankLine() throws DocumentException {
        Paragraph p = new Paragraph();
        p.add(new Phrase("\n"));
        document.add(p);

    }

    /**
     * Creates a borderless, left-aligned table cell for appointment history table rows.
     *
     * @param text String the cell text content
     * @return PdfPCell a configured borderless cell
     */
    private PdfPCell generalCellForApptHistory(String text) {
        Paragraph p = PdfFonts.paragraph(text, getFont());
        p.setAlignment(Paragraph.ALIGN_LEFT);
        PdfPCell cell1 = new PdfPCell(p);
        cell1.setBorder(PdfPCell.NO_BORDER);
        cell1.setHorizontalAlignment(Element.ALIGN_LEFT);
        return cell1;

    }


    /**
     * Prints the patient's allergy list with an underlined "Allergies" heading.
     *
     * @param allergies List&lt;Allergy&gt; the patient's allergy records
     * @throws DocumentException if PDF content cannot be added to the document
     */
    public void printAllergies(List<Allergy> allergies) throws DocumentException {

        Font obsfont = new Font(getBaseFont(), FONTSIZE, Font.UNDERLINE);

        Paragraph p = new Paragraph();
        p.setAlignment(Paragraph.ALIGN_LEFT);
        Phrase phrase = PdfFonts.phrase(LEADING, "\n", getFont());
        p.add(phrase);
        phrase = PdfFonts.phrase(LEADING, "Allergies", obsfont);
        p.add(phrase);
        getDocument().add(p);

        for (Allergy allergy : allergies) {
            p = new Paragraph();
            phrase = PdfFonts.phrase(LEADING, "", getFont());
            PdfFonts.addText(phrase, allergy.getDescription(), phrase.getFont());
            p.add(phrase);
            getDocument().add(p);
        }
        getDocument().add(new Phrase("\n", getFont()));
    }


    /**
     * Prints current (non-archived) prescriptions for the patient.
     *
     * @param demoNo String the patient's demographic number
     * @throws DocumentException if PDF content cannot be added to the document
     */
    public void printRx(String demoNo) throws DocumentException {
        printRx(demoNo, null);
    }

    /**
     * Prints current (non-archived) prescriptions for the patient, with optional CPP notes.
     *
     * @param demoNo String the patient's demographic number; if {@code null}, method returns immediately
     * @param cpp    List&lt;CaseManagementNote&gt; optional CPP "Other Meds" notes (unused in current implementation)
     * @throws DocumentException if PDF content cannot be added to the document
     */
    public void printRx(String demoNo, List<CaseManagementNote> cpp) throws DocumentException {
        if (demoNo == null)
            return;
        /*
        if( newPage )
            document.newPage();
        else
            newPage = true;
        */
        RxPrescriptionData prescriptData = new RxPrescriptionData();
        RxPrescriptionData.Prescription[] arr = {};
        arr = prescriptData.getUniquePrescriptionsByPatient(Integer.parseInt(demoNo));

        if (arr.length == 0) {
            return;
        }

        Paragraph p = new Paragraph();
        Font obsfont = new Font(bf, FONTSIZE, Font.UNDERLINE);
        Phrase phrase = PdfFonts.phrase(LEADING, "", obsfont);
        p.setAlignment(Paragraph.ALIGN_LEFT);
        PdfFonts.addText(phrase, "Prescriptions", phrase.getFont());
        p.add(phrase);
        document.add(p);

        Font normal = new Font(bf, FONTSIZE, Font.NORMAL);


        Font curFont;
        for (int idx = 0; idx < arr.length; ++idx) {
            RxPrescriptionData.Prescription drug = arr[idx];
            p = new Paragraph();
            p.setAlignment(Paragraph.ALIGN_LEFT);
            if (drug.isCurrent() && !drug.isArchived()) {
                curFont = normal;
                phrase = PdfFonts.phrase(LEADING, "", curFont);
                PdfFonts.addText(phrase, formatter.format(drug.getRxDate()) + " - ", phrase.getFont());
                PdfFonts.addText(phrase, drug.getFullOutLine().replace(';', ' '), phrase.getFont());
                p.add(phrase);
                document.add(p);
            }
        }

    }


    /**
     * Prints the patient's prevention (immunization) history. Skips the section
     * entirely if no non-deleted preventions exist for the patient.
     *
     * @throws DocumentException if PDF content cannot be added to the document
     */
    public void printPreventions() throws DocumentException {
        List<Prevention> preventions = preventionDao.findNotDeletedByDemographicId(demographic.getDemographicNo());

        if (preventions.size() == 0) {
            return;
        }

        Paragraph p = new Paragraph();
        Font obsfont = new Font(bf, FONTSIZE, Font.UNDERLINE);
        Phrase phrase = PdfFonts.phrase(LEADING, "", obsfont);
        p.setAlignment(Paragraph.ALIGN_LEFT);
        PdfFonts.addText(phrase, "Preventions", phrase.getFont());
        p.add(phrase);
        document.add(p);

        for (Prevention prevention : preventions) {
            String type = prevention.getPreventionType();
            Date date = prevention.getPreventionDate();

            p = new Paragraph();
            phrase = PdfFonts.phrase(LEADING, "", getFont());
            PdfFonts.addText(phrase, type + " " + formatter.format(date) + "\n", phrase.getFont());
            p.add(phrase);
            getDocument().add(p);
        }

    }

    /**
     * Prints the patient's ticklers including provider, assignee, service date,
     * priority, status, and message. Skips the section if no ticklers exist.
     *
     * @param loggedInInfo LoggedInInfo the current provider's session info for authorization
     * @throws DocumentException if PDF content cannot be added to the document
     */
    public void printTicklers(LoggedInInfo loggedInInfo) throws DocumentException {
        CustomFilter filter = new CustomFilter();
        filter.setDemographicNo(String.valueOf(demographic.getDemographicNo()));
        List<Tickler> ticklers = ticklerManager.getTicklers(loggedInInfo, filter);

        if (ticklers.size() == 0) {
            return;
        }

        Paragraph p = new Paragraph();
        Font obsfont = new Font(bf, FONTSIZE, Font.UNDERLINE);
        Phrase phrase = PdfFonts.phrase(LEADING, "", obsfont);
        p.setAlignment(Paragraph.ALIGN_LEFT);
        PdfFonts.addText(phrase, "Ticklers", phrase.getFont());
        p.add(phrase);
        document.add(p);

        for (Tickler tickler : ticklers) {
            String providerName = getProviderName(tickler.getCreator());
            String assigneeName = getProviderName(tickler.getTaskAssignedTo());
            String serviceDate = tickler.getServiceDateWeb();
            String priority = tickler.getPriority().toString();
            char status = tickler.getStatus().toString().charAt(0);
            String message = tickler.getMessage();


            p = new Paragraph();
            phrase = PdfFonts.phrase(LEADING, "", getFont());
            PdfFonts.addText(phrase, "Provider:" + providerName + "\n", phrase.getFont());
            PdfFonts.addText(phrase, "Assignee:" + assigneeName + "\n", phrase.getFont());
            PdfFonts.addText(phrase, "Service Date:" + serviceDate + "\n", phrase.getFont());
            PdfFonts.addText(phrase, "Priority:" + priority + "\n", phrase.getFont());
            PdfFonts.addText(phrase, "Status:" + status + "\n", phrase.getFont());
            PdfFonts.addText(phrase, "Message:" + message + "\n\n", phrase.getFont());

            p.add(phrase);
            getDocument().add(p);
        }

    }

    /**
     * Prints the patient's disease registry entries (diagnosis research items) with
     * start date, issue description, and status. Skips if no entries exist.
     *
     * @throws DocumentException if PDF content cannot be added to the document
     */
    public void printDiseaseRegistry() throws DocumentException {
        DxresearchDAO dxDao = (DxresearchDAO) SpringUtils.getBean(DxresearchDAO.class);
        IssueDAO issueDao = (IssueDAO) SpringUtils.getBean(IssueDAO.class);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

        List<Dxresearch> dxs = dxDao.getDxResearchItemsByPatient(demographic.getDemographicNo());

        if (dxs.size() == 0) {
            return;
        }
        Paragraph p = new Paragraph();
        Font obsfont = new Font(bf, FONTSIZE, Font.UNDERLINE);
        Phrase phrase = PdfFonts.phrase(LEADING, "", obsfont);
        p.setAlignment(Paragraph.ALIGN_LEFT);
        PdfFonts.addText(phrase, "Disease Registry", phrase.getFont());
        p.add(phrase);
        document.add(p);

        for (Dxresearch dx : dxs) {
            String codingSystem = dx.getCodingSystem();
            String code = dx.getDxresearchCode();
            Date startDate = dx.getStartDate();
            char status = dx.getStatus();

            Issue issue = issueDao.findIssueByTypeAndCode(codingSystem, code);


            p = new Paragraph();
            phrase = PdfFonts.phrase(LEADING, "", getFont());
            PdfFonts.addText(phrase, "Start Date:" + formatter.format(startDate) + "\n", phrase.getFont());
            if (issue != null) {
                PdfFonts.addText(phrase, "Issue:" + issue.getDescription() + "\n", phrase.getFont());
            } else {
                PdfFonts.addText(phrase, "Issue: <Unknown>" + "\n", phrase.getFont());
            }
            PdfFonts.addText(phrase, "Status:" + status + "\n\n", phrase.getFont());

            p.add(phrase);
            getDocument().add(p);
        }

    }

    /**
     * Prints the patient's current (active) program admissions with program name,
     * type, admitting provider, date, and admission notes. Skips if none exist.
     *
     * @throws DocumentException if PDF content cannot be added to the document
     */
    public void printCurrentAdmissions() throws DocumentException {
        AdmissionDao admissionDao = (AdmissionDao) SpringUtils.getBean(AdmissionDao.class);
        ProgramDao programDao = (ProgramDao) SpringUtils.getBean(ProgramDao.class);

        List<Admission> admissions = admissionDao.getCurrentAdmissions(demographic.getDemographicNo());

        if (admissions.size() == 0) {
            return;
        }
        Paragraph p = new Paragraph();
        Font obsfont = new Font(bf, FONTSIZE, Font.UNDERLINE);
        Phrase phrase = PdfFonts.phrase(LEADING, "", obsfont);
        p.setAlignment(Paragraph.ALIGN_LEFT);
        PdfFonts.addText(phrase, "Current Admissions", phrase.getFont());
        p.add(phrase);
        document.add(p);

        for (Admission admission : admissions) {
            String admissionDate = admission.getAdmissionDate("yyyy-MM-dd");
            String admissionNotes = admission.getAdmissionNotes();
            String programName = programDao.getProgramName(admission.getProgramId());
            String programType = admission.getProgramType();
            String providerName = providerDao.getProvider(admission.getProviderNo()).getFormattedName();

            p = new Paragraph();
            phrase = PdfFonts.phrase(LEADING, "", getFont());
            PdfFonts.addText(phrase, "Summary:" + programName + "(" + programType + ")" + " by " + providerName + " on " + admissionDate + "\n", phrase.getFont());
            PdfFonts.addText(phrase, "Admission Notes:" + admissionNotes + "\n\n", phrase.getFont());

            p.add(phrase);
            getDocument().add(p);
        }

    }

    /**
     * Prints the patient's past (discharged) program admissions including discharge
     * date and notes. Filters out current admissions. Skips if none exist.
     *
     * @throws DocumentException if PDF content cannot be added to the document
     */
    public void printPastAdmissions() throws DocumentException {
        AdmissionDao admissionDao = (AdmissionDao) SpringUtils.getBean(AdmissionDao.class);
        ProgramDao programDao = (ProgramDao) SpringUtils.getBean(ProgramDao.class);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

        List<Admission> admissions = admissionDao.getAdmissions(demographic.getDemographicNo());
        admissions = filterOutCurrentAdmissions(admissions);

        if (admissions.size() == 0) {
            return;
        }
        Paragraph p = new Paragraph();
        Font obsfont = new Font(bf, FONTSIZE, Font.UNDERLINE);
        Phrase phrase = PdfFonts.phrase(LEADING, "", obsfont);
        p.setAlignment(Paragraph.ALIGN_LEFT);
        PdfFonts.addText(phrase, "Past Admissions", phrase.getFont());
        p.add(phrase);
        document.add(p);

        for (Admission admission : admissions) {
            String admissionDate = admission.getAdmissionDate("yyyy-MM-dd");
            String admissionNotes = admission.getAdmissionNotes();
            String programName = programDao.getProgramName(admission.getProgramId());
            String programType = admission.getProgramType();
            String providerName = providerDao.getProvider(admission.getProviderNo()).getFormattedName();
            String dischargeDate = formatter.format(admission.getDischargeDate());
            String dischargeNotes = admission.getDischargeNotes();

            p = new Paragraph();
            phrase = PdfFonts.phrase(LEADING, "", getFont());
            PdfFonts.addText(phrase, "Summary:" + programName + "(" + programType + ")" + " by " + providerName + " on " + admissionDate + "\n", phrase.getFont());
            PdfFonts.addText(phrase, "Admission Notes:" + admissionNotes + "\n\n", phrase.getFont());
            PdfFonts.addText(phrase, "Discharged on " + dischargeDate + ", Notes:" + dischargeNotes + "\n\n", phrase.getFont());

            p.add(phrase);
            getDocument().add(p);
        }

    }

    /**
     * Prints the patient's current case management issues with their descriptions.
     *
     * @throws DocumentException if PDF content cannot be added to the document
     */
    public void printCurrentIssues() throws DocumentException {
        CaseManagementIssueDAO cmIssueDao = (CaseManagementIssueDAO) SpringUtils.getBean(CaseManagementIssueDAO.class);

        List<CaseManagementIssue> issues = cmIssueDao.getIssuesByDemographic(String.valueOf(demographic.getDemographicNo()));

        Paragraph p = new Paragraph();
        Font obsfont = new Font(bf, FONTSIZE, Font.UNDERLINE);
        Phrase phrase = PdfFonts.phrase(LEADING, "", obsfont);
        p.setAlignment(Paragraph.ALIGN_LEFT);
        PdfFonts.addText(phrase, "Current Issues", phrase.getFont());
        p.add(phrase);
        document.add(p);

        for (CaseManagementIssue issue : issues) {
            String description = issue.getIssue().getDescription();

            p = new Paragraph();
            phrase = PdfFonts.phrase(LEADING, "", getFont());
            PdfFonts.addText(phrase, description + "\n\n", phrase.getFont());

            p.add(phrase);
            getDocument().add(p);
        }

    }

    /**
     * Filters out admissions with "current" status, returning only past/discharged admissions.
     *
     * @param admissions List of Admission records to filter
     * @return List of Admission containing only non-current admissions
     */
    private List<Admission> filterOutCurrentAdmissions(List<Admission> admissions) {
        List<Admission> results = new ArrayList<Admission>();
        for (Admission a : admissions) {
            if (!a.getAdmissionStatus().equals("current")) {
                results.add(a);
            }
        }
        return results;
    }

}
