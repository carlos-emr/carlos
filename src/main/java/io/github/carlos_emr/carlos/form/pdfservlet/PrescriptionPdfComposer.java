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


package io.github.carlos_emr.carlos.form.pdfservlet;

import java.io.*;

import java.util.*;
import java.util.List;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;

import org.openpdf.text.*;
import org.openpdf.text.pdf.*;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.PharmacyInfo;
import io.github.carlos_emr.carlos.utility.LocaleUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.web.PrescriptionQrCodeUIBean;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.prescript.data.RxPharmacyData;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.stereotype.Service;

/**
 * Composes customized prescription PDF documents.
 *
 * <p>Renders prescription content into a bordered PDF with clinic/patient headers,
 * prescriber signature, and optional QR codes using OpenPDF direct content rendering.
 *
 * <p>PDF layout is constructed entirely through {@link PdfContentByte} direct drawing
 * (lines, text, images) and {@link PdfPTable} cell placement, rather than template-based
 * AcroForm filling. Page events are handled by the inner {@link EndPage} class which
 * extends {@link PdfPageEventHelper}.
 *
 * @see FrmPDFServlet
 * @see io.github.carlos_emr.carlos.web.PrescriptionQrCodeUIBean
 * @since 2001 (McMaster University)
 */
@Service
public class PrescriptionPdfComposer {

    private static Logger logger = MiscUtils.getLogger();

    /**
     * OpenPDF page event handler that renders the prescription page layout on each page end.
     *
     * <p>Draws the complete prescription frame including:
     * <ul>
     *   <li>Rx logo and prescriber information header</li>
     *   <li>Pharmacy attention block (when a pharmacy is selected)</li>
     *   <li>Patient demographics (name, DOB, address, HIN, chart number)</li>
     *   <li>Black border around the prescription area</li>
     *   <li>Signature line with optional signature image overlay</li>
     *   <li>Reprint information and page numbers</li>
     *   <li>Fax confidentiality disclaimer</li>
     * </ul>
     *
     * <p>All coordinates use the OpenPDF coordinate system where (0,0) is the
     * bottom-left corner of the page, measured in points (1/72 inch).
     */
    class EndPage extends PdfPageEventHelper {

        private String clinicName;
        private String clinicTel;
        private String clinicFax;
        private String patientPhone;
        private String patientCityPostal;
        private String patientAddress;
        private String patientName;
        private String patientDOB;
        private String patientHIN;
        private String patientChartNo;
        private String pracNo;
        private String sigDoctorName;
        private String rxDate;
        private String promoText;
        private String origPrintDate = null;
        private String numPrint = null;
        private Image signatureImage;
        Locale locale = null;
        private String billingNumber;

        private PharmacyInfo pharmacyInfo;

        public EndPage() {
        }

        /**
         * Constructs an EndPage event handler with prescription layout data including
         * clinic info, patient demographics, prescriber signature, and pharmacy details.
         */
        public EndPage(String clinicName, String clinicTel, String clinicFax, String patientPhone, String patientCityPostal, String patientAddress,
                       String patientName, String patientDOB, String sigDoctorName, String rxDate, String origPrintDate, String numPrint,
                       Image signatureImage, String patientHIN, String patientChartNo, String pracNo, Locale locale, String billingNumber, String pharmacyInfo) {
            this.clinicName = clinicName == null ? "" : clinicName;
            this.clinicTel = clinicTel == null ? "" : clinicTel;
            this.clinicFax = clinicFax == null ? "" : clinicFax;
            this.patientPhone = patientPhone == null ? "" : patientPhone;
            this.patientCityPostal = patientCityPostal == null ? "" : patientCityPostal;
            this.patientAddress = patientAddress == null ? "" : patientAddress;
            this.patientName = patientName == null ? "" : patientName;
            this.patientDOB = patientDOB == null ? "" : patientDOB;
            this.sigDoctorName = sigDoctorName == null ? "" : sigDoctorName;
            this.rxDate = rxDate == null ? "" : rxDate;
            this.promoText = CarlosProperties.getInstance().getProperty("FORMS_PROMOTEXT");
            this.origPrintDate = origPrintDate;
            this.numPrint = numPrint;
            if (promoText == null) {
                promoText = "";
            }
            this.signatureImage = signatureImage;
            this.patientHIN = patientHIN == null ? "" : patientHIN;
            this.patientChartNo = patientChartNo == null ? "" : patientChartNo;
            this.pracNo = pracNo == null ? "" : pracNo;
            this.locale = locale;
            this.billingNumber = billingNumber == null ? "" : billingNumber;

            if (pharmacyInfo != null && !pharmacyInfo.isEmpty()) {
                RxPharmacyData pharmacyData = new RxPharmacyData();
                this.pharmacyInfo = pharmacyData.getPharmacy(pharmacyInfo);
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            renderPage(writer, document);
        }

        /**
         * @param cb        Pdf Content bytes
         * @param bf        Base Font
         * @param fontSize  Current size of font
         * @param alignment Alignment of text: left, right, centre
         * @param text      The text to be written into the content bytes
         * @param x         X (horizontal) position relative to the bottom LEFT of the page
         * @param y         Y (vertical) position relative to the bottom LEFT of the page
         * @param rotation  Degree of rotation for the text (usually 0)
         */
        public void writeDirectContent(PdfContentByte cb, BaseFont bf, float fontSize, int alignment, String text, float x, float y, float rotation) {
            cb.beginText();
            cb.setFontAndSize(bf, fontSize);
            cb.showTextAligned(alignment, text, x, y, rotation);
            cb.endText();
        }

        private String geti18nTagValue(Locale locale, String tag) {
            return LocaleUtils.getMessage(locale, tag);
        }

        /**
         * Renders the prescription page frame: prescriber info, patient demographics,
         * border lines, signature block, and fax disclaimer. Draws all content using
         * PdfContentByte direct positioning in PDF points from bottom-left origin.
         */
        public void renderPage(PdfWriter writer, Document document) {
            Rectangle page = document.getPageSize();
            float height = page.getHeight();
            boolean showPatientDOB = (this.patientDOB != null && this.patientDOB.length() > 0);
            PdfContentByte cb = writer.getDirectContent();
            String newline = System.getProperty("line.separator");

            try {
                BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
                BaseFont bfBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);

                /*
                 *  Create the special CARLOS Rx logo at the top
                 *  left side of the prescription
                 */
                writeDirectContent(cb, bf, 12, PdfContentByte.ALIGN_LEFT, "c a r l o s", 21, height - 60, 90);
                // draw R
                writeDirectContent(cb, bf, 50, PdfContentByte.ALIGN_LEFT, "P", 24, height - 53, 0);
                // draw X
                writeDirectContent(cb, bf, 43, PdfContentByte.ALIGN_LEFT, "X", 38, height - 69, 0);

                /*
                 * put the Pharmacy info at the top offset next to the prescribers name
                 */
                if (this.pharmacyInfo != null) {
                    List<String> pharmacy = new ArrayList<>();
                    pharmacy.add("ATTENTION:");
                    pharmacy.add(pharmacyInfo.getName());
                    pharmacy.add(pharmacyInfo.getAddress());
                    pharmacy.add(pharmacyInfo.getCity() + ", " + pharmacyInfo.getProvince() + ", " + pharmacyInfo.getPostalCode());
                    pharmacy.add(pharmacyInfo.getPhone1());
                    pharmacy.add(pharmacyInfo.getFax());
                    float position = height - 26f;
                    for (String pharmacyItem : pharmacy) {
                        writeDirectContent(cb, bf, 10, PdfContentByte.ALIGN_LEFT, pharmacyItem, 300, position, 0);
                        position -= 11f;
                    }
                }

                /*
                 * create a column for placing the prescriber information
                 * next to the Rx logo.
                 */
                PdfPTable prescriberHeadingTable = new PdfPTable(1);
                prescriberHeadingTable.setTotalWidth(180f);

                StringBuilder prescriberHeading = new StringBuilder();

                /*
                 * Add the prescribers name
                 */
                prescriberHeading.append(this.sigDoctorName);

                /*
                 * Add the prescribers identifiers:
                 * College Id:
                 * Billing Number:
                 */
                if (billingNumber != null && !billingNumber.isEmpty()) {
                    prescriberHeading.append(newline).append("Billing Number: ").append(this.billingNumber);
                }

                if (pracNo != null && !pracNo.isEmpty()) {
                    prescriberHeading.append(newline).append("College ID: ").append(this.pracNo);
                }

                /*
                 * add the clinic contact info for the prescriber
                 * Clinic name
                 * Address
                 * City, Province, Postal
                 * Telephone
                 * Fax
                 */
                prescriberHeading.append(newline);
                prescriberHeading.append(newline).append(clinicName);

                // render clnicaTel;
                if (this.clinicTel != null && !this.clinicTel.isEmpty()) {
                    prescriberHeading.append(newline).append(geti18nTagValue(locale, "RxPreview.msgTel")).append(": ").append(this.clinicTel);
                }
                if (this.clinicFax != null && !this.clinicFax.isEmpty()) {
                    prescriberHeading.append(newline).append(geti18nTagValue(locale, "RxPreview.msgFax")).append(": ").append(this.clinicFax);
                }
                PdfPCell cell = new PdfPCell(new Phrase(prescriberHeading.toString(), new Font(bf, 10)));
                cell.setBorder(0);
                prescriberHeadingTable.addCell(cell);
                prescriberHeadingTable.writeSelectedRows(0, -1, 80f, height - 13f, cb);

                /*
                 * Create the patient information heading
                 * Patient name
                 * Address
                 * City, Province, Postal
                 * Phone
                 * PHN and or DOB
                 */
                PdfPTable patientHeadingTable = new PdfPTable(1);

                // Rx date at top right, over the patient heading.
                PdfPCell dateCell = new PdfPCell(new Phrase(this.rxDate, new Font(bfBold, 10)));
                dateCell.setBorder(0);
                dateCell.setHorizontalAlignment(PdfContentByte.ALIGN_RIGHT);
                patientHeadingTable.addCell(dateCell);

                StringBuilder patientHeading = new StringBuilder(this.patientName);
                if (showPatientDOB) {
                    patientHeading.append(newline).append(geti18nTagValue(locale, "RxPreview.msgDOB")).append(": ").append(this.patientDOB);
                }
                patientHeading.append(newline).append(this.patientAddress).append(newline).append(this.patientCityPostal).append(newline).append(this.patientPhone);

                if (patientHIN != null && patientHIN.trim().length() > 0) {
                    patientHeading.append(newline).append(geti18nTagValue(locale, "io.github.carlos_emr.carlos.rx.hin")).append(" ").append(patientHIN);
                }

                if (patientChartNo != null && !patientChartNo.isEmpty()) {
                    String chartNoTitle = geti18nTagValue(locale, "io.github.carlos_emr.carlos.rx.chartNo");
                    patientHeading.append(newline).append(chartNoTitle).append(patientChartNo);
                }

                patientHeadingTable.addCell(new Phrase(patientHeading.toString(), new Font(bf, 10)));
                patientHeadingTable.setTotalWidth(272f);
                patientHeadingTable.writeSelectedRows(0, -1, 13f, height - 110f, cb);
                patientHeadingTable.setSpacingAfter(10f);

                /*
                 * find the current position of the PDF writer
                 * and then draw black borders around the prescription
                 */
                float endPara = writer.getVerticalPosition(true);

                // draw left line
                cb.setRGBColorStrokeF(0f, 0f, 0f);
                cb.setLineWidth(0.5f);
                cb.moveTo(13f, endPara - 60);
                cb.lineTo(13f, height - 15f);
                cb.stroke();

                // draw right line 285, 20, 285, 405, 0.5
                cb.setRGBColorStrokeF(0f, 0f, 0f);
                cb.setLineWidth(0.5f);
                cb.moveTo(285f, endPara - 60);
                cb.lineTo(285f, height - 15f);
                cb.stroke();

                // draw top line 10, 405, 285, 405, 0.5
                cb.setRGBColorStrokeF(0f, 0f, 0f);
                cb.setLineWidth(0.5f);
                cb.moveTo(13f, height - 15f);
                cb.lineTo(285f, height - 15f);
                cb.stroke();

                // draw bottom line 10, 20, 285, 20, 0.5
                cb.setRGBColorStrokeF(0f, 0f, 0f);
                cb.setLineWidth(0.5f);
                cb.moveTo(13f, endPara - 60);
                cb.lineTo(285f, endPara - 60);
                cb.stroke();

                /*
                 * Add the "signature" label and draw a line to display under
                 * the Signature ____________________________
                 */
                writeDirectContent(cb, bf, 10, PdfContentByte.ALIGN_LEFT, geti18nTagValue(locale, "RxPreview.msgSignature"), 20f, endPara - 30f, 0); // Render line for Signature 75, 55, 280, 55, 0.5
                cb.setRGBColorStrokeF(0f, 0f, 0f);
                cb.setLineWidth(0.5f);
                cb.moveTo(75f, endPara - 30f);
                cb.lineTo(280f, endPara - 30f);
                cb.stroke();

                /*
                 *  Insert the signature image just above the Signature line.
                 *  The line is placed Y: -30f above the end of the prescription
                 *  The line length starts at X: 75f and ends at X: 280f.
                 *  Therefore, the image total width = 205f - maybe add 10f in padding to 185f
                 *  with the bottom left corner located at X: 75f Y: -31f
                 *  Also need to account for the height of the signature
                 */
                if (this.signatureImage != null) {
                    Image img = Image.getInstance(this.signatureImage);
                    float imageWidth = 185f;
                    float imageHeight = 40f;
                    // scale the origin image to fix these exact parameters width x height
                    img.scaleToFit(imageWidth, imageHeight);
                    // image, image_width, 0, 0, image_height, x, y
                    cb.addImage(img, imageWidth, 0, 0, imageHeight, 75f, endPara - 28f);
                }

                /*
                 * Add the prescribers name just below the signature line
                 */
                writeDirectContent(cb, bf, 10, PdfContentByte.ALIGN_LEFT, this.sigDoctorName, 90, endPara - 40f, 0);

                /*
                 * add the number times printed to the bottom right of the Rx
                 */
                if (origPrintDate != null && numPrint != null) {
                    String rePrintStr = geti18nTagValue(locale, "RxPreview.msgReprintBy") + " " + this.sigDoctorName + "; " + geti18nTagValue(locale, "RxPreview.msgOrigPrinted") + ": " + origPrintDate + "; " + geti18nTagValue(locale, "RxPreview.msgTimesPrinted") + ": " + numPrint;
                    writeDirectContent(cb, bf, 6, PdfContentByte.ALIGN_LEFT, rePrintStr, 50, endPara - 48, 0);
                }

                /*
                 * Add the page number, also to the bottom right of the Rx
                 */
                String footer = String.valueOf(writer.getPageNumber());
                writeDirectContent(cb, bf, 10, PdfContentByte.ALIGN_RIGHT, footer, 280, endPara - 57, 0);

                /*
                 * Add preferred fax cover page disclaimer comment to bottom of Faxed Rx
                 */
                String confidentiality = CarlosProperties.getInstance().getProperty("DEFAULT_FAX_COVERPAGE_COMMENT", "");
                ColumnText columnText = new ColumnText(cb);
                columnText.addText(new Chunk(confidentiality, new Font(bf, 9)));
                columnText.setSimpleColumn(0, 0, page.getWidth(), 60, 10, Element.ALIGN_CENTER | Element.ALIGN_TOP);
                columnText.go();

            } catch (DocumentException | IOException e) {
                throw new IllegalStateException("Failed to render prescription PDF page frame", e);
            }
        }
    }

    /**
     * Parses a satellite clinic address from an HTML-formatted string.
     *
     * <p>Expects a format with bold name followed by {@code <br>}-delimited address lines
     * containing clinic name (3 lines), telephone, and fax.
     *
     * @param s String the HTML-formatted satellite clinic address
     * @return HashMap containing "clinicName", "clinicTel", and "clinicFax" entries
     */
    private HashMap<String, String> parseSCAddress(String s) {
        HashMap<String, String> hm = new HashMap<String, String>();
        hm.put("clinicName", "");
        hm.put("clinicTel", "");
        hm.put("clinicFax", "");
        if (s == null || !s.contains("</b>")) {
            return hm;
        }

        String[] ar = s.split("</b>");
        if (ar.length < 2) {
            return hm;
        }

        String[] ar2 = ar[1].split("<br>");
        ArrayList<String> lst = new ArrayList<String>(Arrays.asList(ar2));
        if (!lst.isEmpty()) {
            lst.remove(0);
        }

        String tel = lst.size() > 3 ? lst.get(3) : "";
        tel = tel.replace("Tel: ", "");
        String fax = lst.size() > 4 ? lst.get(4) : "";
        fax = fax.replace("Fax: ", "");
        String clinicName = "";
        if (lst.size() > 2) {
            clinicName = lst.get(0) + "\n" + lst.get(1) + "\n" + lst.get(2);
        }
        logger.debug("tel: {}", LogSafe.sanitize(tel));
        logger.debug("fax: {}", LogSafe.sanitize(fax));
        logger.debug("clinicName: {}", LogSafe.sanitize(clinicName));
        hm.put("clinicName", clinicName);
        hm.put("clinicTel", tel);
        hm.put("clinicFax", fax);

        return hm;

    }

    private Image loadSignatureImage(String imgFile) throws BadElementException, IOException {
        if (imgFile == null || imgFile.isBlank()) {
            return null;
        }
        File signatureFile = PathValidationUtils.validateExistingPath(
                new File(imgFile),
                new File(System.getProperty("java.io.tmpdir")));
        return Image.getInstance(signatureFile.getAbsolutePath());
    }

    /**
     * Generates the prescription PDF document as a byte array output stream.
     *
     * <p>Extracts all prescription parameters from the HTTP request (clinic info, patient
     * demographics, prescription text, signature image path, QR code settings), constructs
     * an OpenPDF {@link Document} with the appropriate page size and margins, and writes
     * prescription entries as paragraphs with an {@link EndPage} event handler for the
     * page frame rendering.
     *
     * @param req HttpServletRequest containing all prescription form parameters
     * @param ctx ServletContext for resource resolution
     * @return ByteArrayOutputStream containing the generated PDF bytes
     * @throws DocumentException if an OpenPDF document error occurs during PDF generation
     * @throws IOException if an I/O error occurs during PDF generation
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public ByteArrayOutputStream compose(final HttpServletRequest req, final ServletContext ctx) throws DocumentException, IOException {
        logger.debug("***in PrescriptionPdfComposer.compose***");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(req);
        String newline = System.getProperty("line.separator");
        String method = req.getParameter("__method");
        String origPrintDate = null;
        String numPrint = null;
        if ("true".equals(req.getParameter("rxReprint"))) {
            origPrintDate = req.getParameter("origPrintDate");
            numPrint = req.getParameter("numPrints");
        }

        logger.debug("method in PrescriptionPdfComposer.compose {}", LogSafe.sanitize(method));
        String clinicName;
        String clinicTel;
        String clinicFax;
        // check if satellite clinic is used
        String useSatelliteClinic = req.getParameter("useSC");
        logger.debug("useSatelliteClinic: {}", LogSafe.sanitize(useSatelliteClinic));
        if (useSatelliteClinic != null && useSatelliteClinic.equalsIgnoreCase("true")) {
            String scAddress = req.getParameter("scAddress");
            logger.debug("clinic detail={}", LogSafe.sanitize(scAddress));
            HashMap<String, String> hm = parseSCAddress(scAddress);
            clinicName = hm.get("clinicName");
            clinicTel = hm.get("clinicTel");
            clinicFax = hm.get("clinicFax");
        } else {
            // parameters need to be passed to header and footer
            clinicName = req.getParameter("clinicName");
            logger.debug("clinicName={}", LogSafe.sanitize(clinicName));
            clinicTel = req.getParameter("clinicPhone");
            clinicFax = req.getParameter("clinicFax");
        }
        String patientPhone = req.getParameter("patientPhone");
        String patientCityPostal = req.getParameter("patientCityPostal");
        String patientAddress = req.getParameter("patientAddress");
        String patientName = req.getParameter("patientName");
        String sigDoctorName = req.getParameter("sigDoctorName");
        String rxDate = req.getParameter("rxDate");
        String rx = req.getParameter("rx");
        String patientDOB = req.getParameter("patientDOB");
        String showPatientDOB = req.getParameter("showPatientDOB");
        String imgFile = req.getParameter("imgFile");
        Image signatureImage = loadSignatureImage(imgFile);
        String patientHIN = req.getParameter("patientHIN");
        String patientChartNo = req.getParameter("patientChartNo");
        String pracNo = req.getParameter("pracNo");
        Locale locale = req.getLocale();
        String billingNumber = req.getParameter("billingNumber");
        String pharmacyInfo = req.getParameter("pharmacyInfo");
        String title = req.getParameter("__title") != null ? req.getParameter("__title") : "Unknown";
        String additNotes = req.getParameter("additNotes");

        if (clinicName == null) clinicName = "";
        if (clinicTel == null) clinicTel = "";
        if (clinicFax == null) clinicFax = "";
        if (patientPhone == null) patientPhone = "";
        if (patientCityPostal == null) patientCityPostal = "";
        if (patientAddress == null) patientAddress = "";
        if (patientName == null) patientName = "";
        if (sigDoctorName == null) sigDoctorName = "";
        if (rxDate == null) rxDate = "";
        if (patientHIN == null) patientHIN = "";
        if (patientChartNo == null) patientChartNo = "";
        if (pracNo == null) pracNo = "";
        if (patientDOB == null) patientDOB = "";

        boolean isShowDemoDOB = (showPatientDOB != null && showPatientDOB.equalsIgnoreCase("true"));
        if (!isShowDemoDOB)
            patientDOB = "";
        if (rx == null) {
            rx = "";
        }

        // parse prescript and put into a list of prescript;
        String[] rxA = rx.split(newline);
        List<String> listRx = new ArrayList<String>();
        String listElem = "";

        for (String s : rxA) {

            if (s.equals("") || s.equals(newline) || s.length() == 1) {
                listRx.add(listElem);
                listElem = "";
            } else {
                listElem = listElem + s;
                listElem += newline;
            }
        }

        // A0-A10, LEGAL, LETTER, HALFLETTER, _11x17, LEDGER, NOTE, B0-B5, ARCH_A-ARCH_E, FLSA
        // and FLSE
        // the following shows a temp way to get a print page size
        Rectangle pageSize = PageSize.LETTER;
        String pageSizeParameter = req.getParameter("rxPageSize");
        if (pageSizeParameter != null) {
            if ("PageSize.HALFLETTER".equals(pageSizeParameter)) {
                pageSize = PageSize.HALFLETTER;
            } else if ("PageSize.A6".equals(pageSizeParameter)) {
                pageSize = PageSize.A6;
            } else if ("PageSize.A4".equals(pageSizeParameter)) {
                pageSize = PageSize.A4;
            }
        }

        ByteArrayOutputStream baosPDF = new ByteArrayOutputStream();

        Document document = new Document();
        Throwable renderFailure = null;
        try {
            PdfWriter writer = PdfWriter.getInstance(document, baosPDF);

            document.setPageSize(pageSize);

            // 285=left margin+width of box, 5f is space for looking nice
            // document.setMargins(15, pageSize.getWidth() - 285f + 5f, 170, 60); // left, right, top, bottom
            document.setMargins(15, pageSize.getWidth() - 285f + 5f, 185, 60); // left, right, top, bottom

            writer.setPageEvent(new EndPage(clinicName, clinicTel, clinicFax, patientPhone, patientCityPostal, patientAddress, patientName, patientDOB, sigDoctorName, rxDate, origPrintDate, numPrint, signatureImage, patientHIN, patientChartNo, pracNo, locale, billingNumber, pharmacyInfo));
            document.addTitle(title);
            document.addSubject("");
            document.addKeywords("pdf");
            document.addCreator("CARLOS EMR");
            document.addAuthor("");
            document.addHeader("Expires", "0");

            document.open();
            document.newPage();

            PdfContentByte cb = writer.getDirectContent();
            BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);

            cb.setRGBColorStroke(0, 0, 255);
            boolean hasAdditionalNote = (additNotes != null && !additNotes.equals(""));

            // render prescriptions
            Iterator<String> rxStr = listRx.iterator();

            while (rxStr.hasNext()) {
                Paragraph rxEntry = new Paragraph(new Phrase(rxStr.next(), new Font(bf, 10)));
                rxEntry.setKeepTogether(true);
                rxEntry.setSpacingBefore(1f);

                // this adds a small margin to the bottom to the list to
                // accommodate the prescriber's signature.
                if (!rxStr.hasNext() && !hasAdditionalNote) {
                    rxEntry.setSpacingAfter(40f);
                }

                document.add(rxEntry);
            }

            // render additional notes
            if (hasAdditionalNote) {
                Paragraph p = new Paragraph(new Phrase(additNotes, new Font(bf, 10)));
                p.setKeepTogether(true);
                p.setSpacingBefore(10f);
                p.setSpacingAfter(40f);
                document.add(p);
            }

            // render QrCode
            if (loggedInInfo != null
                    && PrescriptionQrCodeUIBean.isPrescriptionQrCodeEnabledForProvider(loggedInInfo.getLoggedInProviderNo())) {
                Integer scriptId = parseScriptId(req.getParameter("scriptId"));
                if (scriptId != null) {
                    byte[] qrCodeImage = PrescriptionQrCodeUIBean.getPrescriptionHl7QrCodeImage(scriptId);
                    Image qrCode = null;
                    if (qrCodeImage != null) {
                        qrCode = Image.getInstance(qrCodeImage);
                    }
                    if (qrCode != null) {
                        document.add(qrCode);
                    }
                }
            }

            document.close();
        } catch (IOException | RuntimeException e) {
            renderFailure = e;
            throw e;
        } finally {
            closeDocumentAfterFailure(document, renderFailure);
        }

        logger.debug("***END in PrescriptionPdfComposer.compose***");
        return baosPDF;

    }

    private Integer parseScriptId(String rawScriptId) {
        if (rawScriptId == null || !rawScriptId.matches("\\d+")) {
            return null;
        }
        try {
            return Integer.parseInt(rawScriptId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void closeDocumentAfterFailure(Document document, Throwable renderFailure) {
        if (!document.isOpen()) {
            return;
        }
        try {
            document.close();
        } catch (RuntimeException closeFailure) {
            if (renderFailure != null) {
                renderFailure.addSuppressed(closeFailure);
                return;
            }
            throw closeFailure;
        }
    }
}
