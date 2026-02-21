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


package io.github.carlos_emr.carlos.commn.service;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.commn.dao.EFormValueDao;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EFormValue;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.printing.FontSettings;
import io.github.carlos_emr.carlos.commn.printing.PdfWriterFactory;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONExt;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.DateUtils;
import io.github.carlos_emr.carlos.eform.util.GraphicalCanvasToImage;
import io.github.carlos_emr.carlos.eform.APExecute;

import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfWriter;

import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JRExporterParameter;
import net.sf.jasperreports.engine.JRExporter;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.engine.JREmptyDataSource;

import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;

public class PdfRecordPrinter {

    private static Logger logger = MiscUtils.getLogger();

    private static final String BILLING_INVOICE_TEMPLATE_FILE = "org/oscarehr/common/web/BillingInvoiceTemplate.jrxml";
    //private static final String OSCAR_LOGO_FILE = "org/oscarehr/commons/www/images/Oscar.jpg";

    private OutputStream os;

    private Document document;
    //private PdfContentByte cb;
    private BaseFont bf;
    private Font font, boldFont;
    private boolean newPage = false;

    private SimpleDateFormat formatter;

    public final int LINESPACING = 1;
    public final float LEADING = 12;
    public final float FONTSIZE = 10;
    public final int NUMCOLS = 2;

    private Demographic demographic;
    private Appointment appointment;
    private String signingProvider;

    private PdfWriter writer;

    private BillingONCHeader1Dao billingONCHeader1Dao = (BillingONCHeader1Dao) SpringUtils.getBean(BillingONCHeader1Dao.class);
    private BillingONExtDao billingONExtDao = (BillingONExtDao) SpringUtils.getBean(BillingONExtDao.class);
    private ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
    private ClinicDAO clinicDao = (ClinicDAO) SpringUtils.getBean(ClinicDAO.class);

    public PdfRecordPrinter(OutputStream os) {
        this.os = os;
        formatter = new SimpleDateFormat("dd-MMM-yyyy");

        document = null;
        writer = null;
        bf = null;
        font = null;
        boldFont = null;
    }

    public void start() throws DocumentException, IOException {
        //Create the font we are going to print to
        bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        font = new Font(bf, FONTSIZE, Font.NORMAL);
        boldFont = new Font(bf, FONTSIZE, Font.BOLD);

        //Create the document we are going to write to
        document = new Document();
        writer = PdfWriterFactory.newInstance(document, os, FontSettings.HELVETICA_10PT);
        // writer = PdfWriter.getInstance(document,os);
        // writer.setPageEvent(new EndPage());
        writer.setStrictImageSequence(true);

        document.setPageSize(PageSize.LETTER);
        document.open();
    }

    public OutputStream getOutputStream() {
        return os;
    }

    public void setDemographic(Demographic demographic) {
        this.demographic = demographic;
    }

    public void setAppointment(Appointment appointment) {
        this.appointment = appointment;
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
        Paragraph p = new Paragraph(value, font);
        return p;
    }

    public String getSigningProvider() {
        return signingProvider;
    }

    public void setSigningProvider(String signingProvider) {
        this.signingProvider = signingProvider;
    }

    public void footer() {
        PdfContentByte cb = writer.getDirectContent();
        cb.saveState();

        Date now = new Date();
        String promoTxt = CarlosProperties.getInstance().getProperty("FORMS_PROMOTEXT");
        if (promoTxt == null) {
            promoTxt = new String();
        }

        String strFooter = promoTxt + " " + formatter.format(now);

        float textBase = document.bottom();
        cb.beginText();
        cb.setFontAndSize(font.getBaseFont(), FONTSIZE);
        Rectangle page = document.getPageSize();
        float width = page.getWidth();

        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, strFooter, (width / 2.0f), textBase - 20, 0);

        strFooter = "-" + writer.getPageNumber() + "-";
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, strFooter, (width / 2.0f), textBase - 10, 0);

        cb.endText();
        cb.restoreState();
    }

    public void printDocHeaderFooter() throws DocumentException {

        String headerTitle = demographic.getFormattedName() + " " + demographic.getAge() + " " + demographic.getSex() + " DOB:" + demographic.getFormattedDob();

        if (newPage) {
            document.newPage();
            newPage = false;
        }

        //Header will be printed at top of every page beginning with p2
        Phrase headerPhrase = new Phrase(LEADING, headerTitle, boldFont);

        getDocument().add(headerPhrase);
        getDocument().add(new Phrase("\n"));

        Paragraph p = new Paragraph("Tel:" + demographic.getPhone(), getFont());
        p.setAlignment(Paragraph.ALIGN_LEFT);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Paragraph p2 = new Paragraph("Date of Visit: " + sdf.format(appointment.getAppointmentDate()), getFont());
        p2.setAlignment(Paragraph.ALIGN_RIGHT);

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
        cell1.setBorder(PdfPCell.BOTTOM);
        cell1.setHorizontalAlignment(Element.ALIGN_LEFT);
        cell2 = new PdfPCell(getParagraph("RFR:" + this.appointment.getReason()));
        cell2.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell2.setBorder(PdfPCell.BOTTOM);
        PdfPCell cell3 = new PdfPCell(getParagraph("Ref:" + this.getRefName(this.demographic)));
        cell3.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell3.setBorder(PdfPCell.BOTTOM);
        table.addCell(cell1);
        table.addCell(cell2);
        table.addCell(cell3);
        getDocument().add(table);
    }

    public void printBillingInvoice(Integer invoiceNo, Locale locale) {
        CarlosProperties props = CarlosProperties.getInstance();
        InputStream is = null;
        InputStream imageIS = null;
        try {
            String templateFilepath = props.getProperty("billing_template_file", "");
            //look for custom billing template file, otherwise use default.
            if (templateFilepath.isEmpty())
                is = this.getClass().getClassLoader().getResourceAsStream(BILLING_INVOICE_TEMPLATE_FILE);
            else
                is = new FileInputStream(templateFilepath);
            //get Jasper Report

            JasperReport jasperReport = JasperCompileManager.compileReport(is);

            if (jasperReport != null) {

                //populate data in Jasper Report
                Map<String, Object> parameters = new HashMap<String, Object>();
                JRParameter[] jrParams = jasperReport.getParameters();

                if (jrParams != null) {

                    APExecute apExe = new APExecute();
                    BillingONCHeader1 billingONCHeader1 = billingONCHeader1Dao.find(invoiceNo);

                    for (JRParameter jrParam : jrParams) {
                        if (!jrParam.isSystemDefined()) {
                            String paramName = jrParam.getName();

                            if (paramName.equals("invoice_no")) {
                                parameters.put(paramName, invoiceNo);
                            } else if (paramName.equals("clinic_logo")) {
                                String imagePath = props.getProperty("clinic_logo", "");
                                if (imagePath.isEmpty()) {
                                    //DO NOTHING imageIS = this.getClass().getClassLoader().getResourceAsStream(OSCAR_LOGO_FILE);
                                } else {
                                    imageIS = new FileInputStream(imagePath);
                                }
                                parameters.put(paramName, imageIS);
                            } else if (paramName.equals("billing_print_date")) {
                                parameters.put(paramName, DateUtils.formatDate(new Date(), locale));
                            } else if (paramName.equals("billing_due_date")) {
                                String dueDateStr = "";
                                if (props.hasProperty("invoice_due_date")) {
                                    BillingONExt dueDateExt = billingONExtDao.getDueDate(billingONCHeader1);
                                    if (dueDateExt != null) {
                                        dueDateStr = dueDateExt.getValue();
                                    } else {
                                        Integer numDaysTilDue = Integer.parseInt(props.getProperty("invoice_due_date", "0"));
                                        Date serviceDate = billingONCHeader1.getBillingDate();
                                        dueDateStr = DateUtils.sumDate(serviceDate, numDaysTilDue, locale);
                                    }
                                }

                                parameters.put(paramName, dueDateStr);

                            } else if (paramName.equals("payee")) {
                                String payee = props.getProperty("PAYEE", "");
                                if (payee.isEmpty()) {
                                    payee = providerDao.getProviderName(billingONCHeader1.getProviderNo());
                                }
                                parameters.put(paramName, payee);
                            } else if (paramName.equals("remit_to_phone")) {
                                String remitToPhone = props.getProperty("clinic_billing_phone", "");
                                if (remitToPhone.isEmpty()) {
                                    remitToPhone = clinicDao.getClinic().getClinicPhone();
                                }
                                parameters.put(paramName, remitToPhone);
                            } else if (paramName.equals("use_billext")) {
                                String useBillTo = "off";
                                BillingONExt useBillToExt = billingONExtDao.getUseBillTo(billingONCHeader1);
                                if (useBillToExt != null) {
                                    useBillTo = useBillToExt.getValue();
                                }
                                parameters.put(paramName, useBillTo);
                            } else if (paramName.equals("billext_billto")) {
                                String billTo = "";
                                BillingONExt billToExt = billingONExtDao.getBillTo(billingONCHeader1);
                                if (billToExt != null) {
                                    billTo = billToExt.getValue();
                                }
                                parameters.put(paramName, billTo);
                            } else {
                                parameters.put(paramName, apExe.execute(paramName, String.valueOf(billingONCHeader1.getDemographicNo()), invoiceNo));
                            }
                        }
                    }

                    //print Jasper Report
                    JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, new JREmptyDataSource());

                    //Exports Jasper Report as PDF to output stream.
                    JRExporter exporter = new JRPdfExporter();
                    exporter.setParameter(JRExporterParameter.JASPER_PRINT, jasperPrint);
                    exporter.setParameter(JRExporterParameter.OUTPUT_STREAM, os);

                    exporter.exportReport();
                }
            }

        } catch (net.sf.jasperreports.engine.JRException e) {
            MiscUtils.getLogger().error("An unexpected jasper exception occurred:", e);
        } catch (java.io.FileNotFoundException e) {
            MiscUtils.getLogger().error("Cannot file billing template file:", e);
        } finally {
            try {
                if (is != null) is.close();
                if (imageIS != null) imageIS.close();
            } catch (java.io.IOException e) {
                MiscUtils.getLogger().error("Cannot close InputStream:", e);
            }
        }
    }

    public void printRx(String demoNo) throws DocumentException {
        printRx(demoNo, null);
    }

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
        Phrase phrase = new Phrase(LEADING, "", obsfont);
        p.setAlignment(Paragraph.ALIGN_LEFT);
        phrase.add("Prescriptions");
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
        phrase = new Phrase(LEADING, heading, obsfont);
        p.add(phrase);
        document.add(p);
        newPage = false;
        this.printNotes(notes, true);


        // cb.endText();

    }

    public void printCPPItem(String heading, Measurement measurement) throws DocumentException {
        if (newPage)
            document.newPage();
        //  else
        //      newPage = true;

        Font obsfont = new Font(bf, FONTSIZE, Font.UNDERLINE);

        Paragraph p = null;
        Phrase phrase = null;


        p = new Paragraph();
        p.setAlignment(Paragraph.ALIGN_LEFT);
        phrase = new Phrase(LEADING, heading, obsfont);
        p.add(phrase);
        document.add(p);
        newPage = false;

        p = new Paragraph();
        phrase = new Phrase(LEADING, "", font);
        //phrase.add(new Chunk(formatter.format(measurement.getDateObserved()) + ":"));
        phrase.add(measurement.getDataField() + "\n");
        p.add(phrase);
        document.add(p);

        // cb.endText();

    }

    public void printBlankLine() throws DocumentException {
        document.add(new Phrase("\n"));
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
        //phrase.add(content[idx]);
        //ct.addText(phrase);

//            //do we need a page break?  check if we're within a fudge factor of the bottom
//            if( lworkingYcoord <= (bottom * 1.1) && rworkingYcoord <= (bottom*1.1) ) {
//                document.newPage();
//                rworkingYcoord = lworkingYcoord = document.top();
//            }
//
//            //Are we in right column?  if so, flip over to left column if there is room
//            if( column % 2 == 1 ) {
//                if( lworkingYcoord > bottom ) {
//                    ct.setSimpleColumn(document.left(), bottom, (document.right()/2f)-10f, lworkingYcoord);
//                    ++column;
//                }
//            }
//            //Are we in left column?  if so, flip over to right column only if text will fit
//            else {
//                ct.setSimpleColumn((document.right()/2f)+10f, bottom, document.right(), rworkingYcoord);
//
//                if( ct.go(true) == ColumnText.NO_MORE_COLUMN ) {
//                    ct.setSimpleColumn(document.left(), bottom, (document.right()/2f)-10f, lworkingYcoord);
//                }
//                else {
//                    ct.setYLine(rworkingYcoord);
//                    ++column;
//                }
//
//                //ct.go(true) consumes input so we reload
//                phrase = new Phrase(LEADING, "", font);
//                chunk = new Chunk(headings[idx], obsfont);
//                phrase.add(chunk);
//                phrase.add(content[idx]);
//                ct.setText(phrase);
//            }
//
//            //while there is text to write, fill columns/page break when page full
//            while( ct.go() == ColumnText.NO_MORE_COLUMN ) {
//                if( column % 2 == 0 ) {
//                    lworkingYcoord = bottom;
//                    middle = (document.right()/4f)*3f;
//                    headerContd = headings[idx] + " cont'd";
//                    cb.setFontAndSize(bf, FONTSIZE);
//                    cb.showTextAligned(PdfContentByte.ALIGN_CENTER, headerContd, middle, rworkingYcoord-phrase.leading(), 0f);
//                    //cb.showTextAligned(PdfContentByte.ALIGN_CENTER, headings[idx] + " cont'd", middle, rworkingYcoord, 0f);
//                    rworkingYcoord -= phrase.leading();
//                    ct.setSimpleColumn((document.right()/2f)+10f, bottom, document.right(), rworkingYcoord);
//                }
//                else {
//                    document.newPage();
//                    rworkingYcoord = lworkingYcoord = document.top();
//                    middle = (document.right()/4f);
//                    headerContd = headings[idx] + " cont'd";
//                    cb.setFontAndSize(bf, FONTSIZE);
//                    cb.showTextAligned(PdfContentByte.ALIGN_CENTER, headerContd, middle, lworkingYcoord-phrase.leading(), 0f);
//                    lworkingYcoord -= phrase.leading();
//                    ct.setSimpleColumn(document.left(), bottom, (document.right()/2f)-10f, lworkingYcoord);
//                }
//                ++column;
//            }
//
//            if( column % 2 == 0 )
//                lworkingYcoord -= (ct.getLinesWritten() * ct.getLeading() + (ct.getLeading() * 2f));
//            else
//                rworkingYcoord -= (ct.getLinesWritten() * ct.getLeading() + (ct.getLeading() * 2f));
//        }
//        cb.endText();
    }

    public void printNotes(Collection<CaseManagementNote> notes) throws DocumentException {
        printNotes(notes, false);
    }

    public void printNotes(Collection<CaseManagementNote> notes, boolean compact) throws DocumentException {

        CaseManagementNote note;
        Font obsfont = new Font(bf, FONTSIZE, Font.UNDERLINE);
        Paragraph p;
        Phrase phrase;
        Chunk chunk;

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
            phrase = new Phrase(LEADING, "", font);

            if (compact) {
                phrase.add(new Chunk(formatter.format(note.getObservation_date()) + ":"));
            } else {
                chunk = new Chunk("Impression/Plan: (" + formatter.format(note.getObservation_date()) + ")\n", obsfont);
                phrase.add(chunk);
            }
            if (compact) {
                phrase.add(note.getNote() + "\n");
            } else {
                phrase.add(note.getNote() + "\n\n");
            }
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
    class EndPage extends PdfPageEventHelper {
        private Date now;
        private String promoTxt;

        public EndPage() {
            now = new Date();
            promoTxt = CarlosProperties.getInstance().getProperty("FORMS_PROMOTEXT");
            if (promoTxt == null) {
                promoTxt = new String();
            }
        }

        public void onEndPage(PdfWriter writer, Document document) {
            //Footer contains page numbers and date printed on all pages
            PdfContentByte cb = writer.getDirectContent();
            cb.saveState();

            String strFooter = promoTxt + " " + formatter.format(now);

            float textBase = document.bottom();
            cb.beginText();
            cb.setFontAndSize(font.getBaseFont(), FONTSIZE);
            Rectangle page = document.getPageSize();
            float width = page.getWidth();
            cb.showTextAligned(PdfContentByte.ALIGN_CENTER, strFooter, (width / 2.0f), textBase - 20, 0);

            strFooter = "-" + writer.getPageNumber() + "-";
            cb.showTextAligned(PdfContentByte.ALIGN_CENTER, strFooter, (width / 2.0f), textBase - 10, 0);

            cb.endText();
            cb.restoreState();
        }
    }



    public void printAllergies(List<Allergy> allergies) throws DocumentException {
        Font obsfont = new Font(getBaseFont(), FONTSIZE, Font.UNDERLINE);


        Paragraph p = new Paragraph();
        p.setAlignment(Paragraph.ALIGN_LEFT);
        Phrase phrase = new Phrase(LEADING, "\n", getFont());
        p.add(phrase);
        phrase = new Phrase(LEADING, "Allergies", obsfont);
        p.add(phrase);
        getDocument().add(p);

        for (Allergy allergy : allergies) {
            p = new Paragraph();
            phrase = new Phrase(LEADING, "", getFont());
            Chunk chunk = new Chunk(allergy.getDescription());
            phrase.add(chunk);
            p.add(phrase);
            getDocument().add(p);
        }
        getDocument().add(new Phrase("\n", getFont()));
    }



    public void printPhotos(String contextPath, List<io.github.carlos_emr.carlos.commn.model.Document> photos) throws DocumentException {
        writer.setStrictImageSequence(true);

        if (photos.size() > 0) {
            Font obsfont = new Font(getBaseFont(), FONTSIZE, Font.UNDERLINE);
            Paragraph p = new Paragraph();
            p.setAlignment(Paragraph.ALIGN_LEFT);
            Phrase phrase = new Phrase(LEADING, "\n\n", getFont());
            p.add(phrase);
            phrase = new Phrase(LEADING, "Photos:", obsfont);
            p.add(phrase);
            getDocument().add(p);
        }

        for (io.github.carlos_emr.carlos.commn.model.Document doc : photos) {
            Image img = null;
            try {
                String location = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR").trim() + doc.getDocfilename();
                logger.info("adding image " + location);
                img = Image.getInstance(location);
            } catch (IOException e) {
                MiscUtils.getLogger().error("error:", e);
                continue;
            }
            img.scaleToFit(getDocument().getPageSize().getWidth() - getDocument().leftMargin() - getDocument().rightMargin(), getDocument().getPageSize().getHeight());

            Chunk chunk = new Chunk(img, getDocument().getPageSize().getWidth() - getDocument().leftMargin() - getDocument().rightMargin(), getDocument().getPageSize().getHeight());

            Paragraph p = new Paragraph();
            p.add(img);
            p.add(new Phrase("Description:" + doc.getDocdesc(), getFont()));
            getDocument().add(p);


        }
    }

    public void printDiagrams(List<EFormValue> diagrams) throws DocumentException {
        writer.setStrictImageSequence(true);

        EFormValueDao eFormValueDao = (EFormValueDao) SpringUtils.getBean(EFormValueDao.class);

        if (diagrams.size() > 0) {
            Font obsfont = new Font(getBaseFont(), FONTSIZE, Font.UNDERLINE);
            Paragraph p = new Paragraph();
            p.setAlignment(Paragraph.ALIGN_LEFT);
            Phrase phrase = new Phrase(LEADING, "\n\n", getFont());
            p.add(phrase);
            phrase = new Phrase(LEADING, "Diagrams:", obsfont);
            p.add(phrase);
            getDocument().add(p);
        }

        for (EFormValue value : diagrams) {
            //this is a form from our group, and our appt
            String imgPath = CarlosProperties.getInstance().getEformImageDirectory();
            EFormValue imageName = eFormValueDao.findByFormDataIdAndKey(value.getFormDataId(), "image");
            EFormValue drawData = eFormValueDao.findByFormDataIdAndKey(value.getFormDataId(), "DrawData");
            EFormValue subject = eFormValueDao.findByFormDataIdAndKey(value.getFormDataId(), "subject");

            String image = imgPath + File.separator + imageName.getVarValue();
            logger.debug("image for eform is " + image);
            GraphicalCanvasToImage convert = new GraphicalCanvasToImage();
            File tempFile = null;
            try {
                tempFile = File.createTempFile("graphicImg", ".png");
                FileOutputStream fos = new FileOutputStream(tempFile);
                convert.convertToImage(image, drawData.getVarValue(), "PNG", fos);
                logger.debug("converted image is " + tempFile.getName());
                fos.close();
            } catch (IOException e) {
                logger.error("Error", e);
                if (tempFile != null) {
                    tempFile.delete();
                }
                continue;
            }

            Image img = null;
            try {
                logger.info("adding diagram " + tempFile.getAbsolutePath());
                img = Image.getInstance(tempFile.getAbsolutePath());
            } catch (IOException e) {
                logger.error("error:", e);
                continue;
            }
            img.scaleToFit(getDocument().getPageSize().getWidth() - getDocument().leftMargin() - getDocument().rightMargin(), getDocument().getPageSize().getHeight());
            Paragraph p = new Paragraph();
            p.add(img);
            p.add(new Phrase("Subject:" + subject.getVarValue(), getFont()));
            getDocument().add(p);

            tempFile.deleteOnExit();
        }

    }

    private String getRefName(Demographic d) {
        String referral = d.getFamilyDoctor();

        if (referral == null || referral.length() == 0)
            return new String();

        int start = referral.indexOf("<rd>");
        int end = referral.indexOf("</rd>");
        String ref = new String();

        if (start >= 0 && end >= 0) {
            String subreferral = referral.substring(start + 4, end);
            if (!"".equalsIgnoreCase(subreferral.trim())) {
                ref = subreferral;

            }
        }
        return ref;
    }
}
