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


package ca.openosp.openo.form.pdfservlet;

import java.io.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import org.apache.commons.io.FileUtils;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.FaxConfigDao;
import ca.openosp.openo.commn.dao.FaxJobDao;
import ca.openosp.openo.commn.model.FaxConfig;
import ca.openosp.openo.commn.model.FaxJob;
import ca.openosp.openo.commn.model.FaxJob.Direction;
import ca.openosp.openo.commn.model.PharmacyInfo;
import ca.openosp.openo.managers.FaxManager;
import ca.openosp.openo.managers.FaxManager.TransactionType;
import ca.openosp.openo.utility.LocaleUtils;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PathValidationUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.web.PrescriptionQrCodeUIBean;

import org.owasp.encoder.Encode;
import ca.openosp.OscarProperties;
import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.log.LogConst;
import ca.openosp.openo.prescript.data.RxPharmacyData;

public class FrmCustomedPDFServlet extends HttpServlet {

    private static Logger logger = MiscUtils.getLogger();
    private final FaxJobDao faxJobDao = SpringUtils.getBean(FaxJobDao.class);

    private final FaxConfigDao faxConfigDao = SpringUtils.getBean(FaxConfigDao.class);
    private static FaxManager faxManager = SpringUtils.getBean(FaxManager.class);

    @Override
    public void service(HttpServletRequest req, HttpServletResponse res) throws javax.servlet.ServletException, java.io.IOException {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(req);
        boolean isFax = "oscarRxFax".equals(req.getParameter("__method"));
        try (ByteArrayOutputStream baosPDF = generatePDFDocumentBytes(req, this.getServletContext());
             PrintWriter writer = res.getWriter()) {

            if (isFax) {
                // this fax method shouldn't be here and will be removed in future edits.
                res.setContentType("text/html");
                String faxNo = req.getParameter("pharmaFax");
                if (faxNo != null) {
                    faxNo = faxNo.trim().replaceAll("\\D", "");
                }
                String pharmaName = req.getParameter("pharmaName");
                String faxNumber = req.getParameter("clinicFax");
                if (faxNumber != null) {
                    faxNumber = faxNumber.trim().replaceAll("\\D", "");
                }
                String demo = req.getParameter("demographic_no");

                if (faxNo != null && faxNo.length() < 7) {
                    writer.println("<div id='fax-failure'><h3>Error: Valid fax number not found!</h3></div>");
                } else {
                    // write to file
                    String pdfid = req.getParameter("pdfId");
                    // Sanitize pdfId to prevent path traversal
                    if (pdfid != null) {
                        pdfid = pdfid.replaceAll("[^a-zA-Z0-9_-]", "");
                    }
                    String pdfFile = "prescription_" + pdfid + ".pdf";
                    String document_dir = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");
                    
                    // Use PathValidationUtils for proper path validation
                    File baseDirFile = new File(document_dir);
                    File validatedPdfFile = PathValidationUtils.validatePath(pdfFile, baseDirFile);
                    Path filepath = validatedPdfFile.toPath();

                    if (!Files.exists(filepath)) {
                        baosPDF.writeTo(Files.newOutputStream(filepath));
                    }

                    // write to temporary file
                    String tempPath = OscarProperties.getInstance().getProperty("fax_file_location", System.getProperty("java.io.tmpdir"));
                    File tempDirFile = new File(tempPath);
                    File validatedTempPdf = PathValidationUtils.validatePath("prescription_" + pdfid + ".pdf", tempDirFile);
                    Path tempPdf = validatedTempPdf.toPath();

                    // Copying the fax pdf.
                    if (Files.exists(filepath) && !Files.exists(tempPdf)) {
                        FileUtils.copyFile(filepath.toFile(), tempPdf.toFile());
                    }

                    // tracking file
                    File validatedTxtFile = PathValidationUtils.validatePath("prescription_" + pdfid + ".txt", tempDirFile);
                    String txtFile = validatedTxtFile.toString();
                    try (FileWriter fstream = new FileWriter(txtFile);
                         BufferedWriter out = new BufferedWriter(fstream)) {
                        if (faxNo != null) {
                            out.write(faxNo);
                        }
                    }

                    List<FaxConfig> faxConfigs = faxConfigDao.findAll(null, null);
                    String provider_no = LoggedInInfo.getLoggedInInfoFromSession(req).getLoggedInProviderNo();
                    FaxJob faxJob;
                    boolean validFaxNumber = false;

                    for (FaxConfig faxConfig : faxConfigs) {

                        if (faxConfig.getFaxNumber().equals(faxNumber)) {

                            PdfReader pdfReader = new PdfReader(filepath.toString());

                            faxJob = new FaxJob();
                            faxJob.setDestination(faxNo);
                            faxJob.setFax_line(faxNumber);
                            faxJob.setFile_name(pdfFile);
                            faxJob.setUser(faxConfig.getFaxUser());
                            faxJob.setRecipient(pharmaName);
                            faxJob.setNumPages(pdfReader.getNumberOfPages());
                            faxJob.setStamp(new Date());
                            faxJob.setStatus(FaxJob.STATUS.WAITING);
                            faxJob.setOscarUser(provider_no);
                            faxJob.setDemographicNo(Integer.parseInt(demo));

                            faxJob.setSenderEmail(faxConfig.getSenderEmail());
                            faxJob.setDirection(Direction.OUT);

                            faxJobDao.persist(faxJob);
                            faxManager.logFaxJob(loggedInInfo, faxJob, TransactionType.RX, -1);
                            validFaxNumber = true;
                            break;
                        }
                    }

                    if (validFaxNumber) {
                        LogAction.addLog(provider_no, LogConst.SENT, LogConst.CON_FAX, "PRESCRIPTION " + pdfFile);
						writer.println("<div id='fax-success' style='color:green;'><h3>Fax successfully generated</h3><p>" + Encode.forHtml(pharmaName) + " (" + Encode.forHtml(faxNo) + ")</p><br><p>This window will close in <b>3</b> seconds...</p></div><script>setTimeout(() => window.top.close(), 3000);</script>");
                    }
                }
            } else {
                StringBuilder sbFilename = new StringBuilder();
                sbFilename.append("filename_");
                sbFilename.append(".pdf");

                // set the Cache-Control header
                res.setHeader("Cache-Control", "max-age=0");
                res.setDateHeader("Expires", 0);

                res.setContentType("application/pdf");

                // The Content-disposition value will be inline
                StringBuilder sbContentDispValue = new StringBuilder();
                sbContentDispValue.append("inline; filename="); // inline - display
                // the pdf file
                // directly rather
                // than open/save
                // selection
                // sbContentDispValue.append("; filename=");
                sbContentDispValue.append(sbFilename);

                res.setHeader("Content-disposition", sbContentDispValue.toString());
                res.setContentLength(baosPDF.size());
                ServletOutputStream sos = res.getOutputStream();
                baosPDF.writeTo(sos);

                sos.flush();
            }
        } catch (DocumentException dex) {
            // Log the detailed error for debugging
            logger.error("PDF generation error in FrmCustomedPDFServlet", dex);
            
            // Return generic error message to user
            res.setContentType("text/html");
            PrintWriter writer = res.getWriter();
            writer.println("<html><body>");
            writer.println("<h3>An error occurred generating the PDF.</h3>");
            writer.println("<p>Please try again or contact support if the problem persists.</p>");
            writer.println("</body></html>");
        } catch (java.io.FileNotFoundException dex) {
            // Log the error
            logger.debug("Signature file not found", dex);
            
            res.setContentType("text/html");
            PrintWriter writer = res.getWriter();
            writer.println("<script>alert('Signature not found. Please sign the prescription.');</script>");
        }

    }


    /**
     * the form txt file has lines in the form: For Checkboxes: ie. ohip : left, 76, 193, 0, BaseFont.ZAPFDINGBATS, 8, \u2713 requestParamName : alignment, Xcoord, Ycoord, 0, font, fontSize, textToPrint[if empty, prints the value of the request param]
     * NOTE: the Xcoord and Ycoord refer to the bottom-left corner of the element For single-line text: ie. patientCity : left, 242, 261, 0, BaseFont.HELVETICA, 12 See checkbox explanation For multi-line text (textarea) ie. aci : left, 20, 308, 0,
     * BaseFont.HELVETICA, 8, _, 238, 222, 10 requestParamName : alignment, bottomLeftXcoord, bottomLeftYcoord, 0, font, fontSize, _, topRightXcoord, topRightYcoord, spacingBtwnLines NOTE: When working on these forms in linux, it helps to load the PDF file
     * into gimp, switch to pt. coordinate system and use the mouse to find the coordinates. Prepare to be bored!
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
        private String imgPath;
        Locale locale = null;
        private String billingNumber;

        private PharmacyInfo pharmacyInfo;

        public EndPage() {
        }

        public EndPage(String clinicName, String clinicTel, String clinicFax, String patientPhone, String patientCityPostal, String patientAddress,
                       String patientName, String patientDOB, String sigDoctorName, String rxDate, String origPrintDate, String numPrint,
                       String imgPath, String patientHIN, String patientChartNo, String pracNo, Locale locale, String billingNumber, String pharmacyInfo) {
            this.clinicName = clinicName == null ? "" : clinicName;
            this.clinicTel = clinicTel == null ? "" : clinicTel;
            this.clinicFax = clinicFax == null ? "" : clinicFax;
            this.patientPhone = patientPhone == null ? "" : patientPhone;
            this.patientCityPostal = patientCityPostal == null ? "" : patientCityPostal;
            this.patientAddress = patientAddress == null ? "" : patientAddress;
            this.patientName = patientName;
            this.patientDOB = patientDOB;
            this.sigDoctorName = sigDoctorName == null ? "" : sigDoctorName;
            this.rxDate = rxDate;
            this.promoText = OscarProperties.getInstance().getProperty("FORMS_PROMOTEXT");
            this.origPrintDate = origPrintDate;
            this.numPrint = numPrint;
            if (promoText == null) {
                promoText = "";
            }
            this.imgPath = imgPath;
            this.patientHIN = patientHIN == null ? "" : patientHIN;
            this.patientChartNo = patientChartNo == null ? "" : patientChartNo;
            this.pracNo = pracNo == null ? "" : pracNo;
            this.locale = locale;

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
                 *  Create the special OSCAR Rx logo at the top
                 *  left side of the prescription
                 */
                writeDirectContent(cb, bf, 12, PdfContentByte.ALIGN_LEFT, "o s c a r", 21, height - 60, 90);
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
                    patientHeading.append(newline).append(geti18nTagValue(locale, "ca.openosp.openo.rx.hin")).append(" ").append(patientHIN);
                }

                if (patientChartNo != null && !patientChartNo.isEmpty()) {
                    String chartNoTitle = geti18nTagValue(locale, "ca.openosp.openo.rx.chartNo");
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
                writeDirectContent(cb, bf, 10, PdfContentByte.ALIGN_LEFT, geti18nTagValue(locale, "RxPreview.msgSignature"), 20f, endPara - 30f, 0);// Render line for Signature 75, 55, 280, 55, 0.5
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
                if (this.imgPath != null) {
                    Image img = Image.getInstance(this.imgPath);
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
                String confidentiality = OscarProperties.getInstance().getProperty("DEFAULT_FAX_COVERPAGE_COMMENT", "");
                ColumnText columnText = new ColumnText(cb);
                columnText.addText(new Chunk(confidentiality, new Font(bf, 9)));
                columnText.setSimpleColumn(0, 0, page.getWidth(), 60, 10, Element.ALIGN_CENTER | Element.ALIGN_TOP);
                columnText.go();

            } catch (Exception e) {
                logger.error("Error", e);
            }
        }
    }

    private HashMap<String, String> parseSCAddress(String s) {
        HashMap<String, String> hm = new HashMap<String, String>();
        String[] ar = s.split("</b>");
        String[] ar2 = ar[1].split("<br>");
        ArrayList<String> lst = new ArrayList<String>(Arrays.asList(ar2));
        lst.remove(0);
        String tel = lst.get(3);
        tel = tel.replace("Tel: ", "");
        String fax = lst.get(4);
        fax = fax.replace("Fax: ", "");
        String clinicName = lst.get(0) + "\n" + lst.get(1) + "\n" + lst.get(2);
        logger.debug(tel);
        logger.debug(fax);
        logger.debug(clinicName);
        hm.put("clinicName", clinicName);
        hm.put("clinicTel", tel);
        hm.put("clinicFax", fax);

        return hm;

    }

    private ByteArrayOutputStream generatePDFDocumentBytes(final HttpServletRequest req, final ServletContext ctx) throws DocumentException, IOException {
        logger.debug("***in generatePDFDocumentBytes2 FrmCustomedPDFServlet.java***");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(req);
        String newline = System.getProperty("line.separator");
        String method = req.getParameter("__method");
        String origPrintDate = null;
        String numPrint = null;
        if ("true".equals(req.getParameter("rxReprint"))) {
            origPrintDate = req.getParameter("origPrintDate");
            numPrint = req.getParameter("numPrints");
        }

        logger.debug("method in generatePDFDocumentBytes " + method);
        String clinicName;
        String clinicTel;
        String clinicFax;
        // check if satellite clinic is used
        String useSatelliteClinic = req.getParameter("useSC");
        logger.debug(useSatelliteClinic);
        if (useSatelliteClinic != null && useSatelliteClinic.equalsIgnoreCase("true")) {
            String scAddress = req.getParameter("scAddress");
            logger.debug("clinic detail" + "=" + scAddress);
            HashMap<String, String> hm = parseSCAddress(scAddress);
            clinicName = hm.get("clinicName");
            clinicTel = hm.get("clinicTel");
            clinicFax = hm.get("clinicFax");
        } else {
            // parameters need to be passed to header and footer
            clinicName = req.getParameter("clinicName");
            logger.debug("clinicName" + "=" + clinicName);
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
        if (sigDoctorName == null) sigDoctorName = "";
        if (patientHIN == null) patientHIN = "";
        if (patientChartNo == null) patientChartNo = "";
        if (pracNo == null) pracNo = "";

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
        PdfWriter writer = PdfWriter.getInstance(document, baosPDF);

        document.setPageSize(pageSize);

        // 285=left margin+width of box, 5f is space for looking nice
        // document.setMargins(15, pageSize.getWidth() - 285f + 5f, 170, 60);// left, right, top , bottom
        document.setMargins(15, pageSize.getWidth() - 285f + 5f, 185, 60);// left, right, top , bottom

        //writer = PdfWriter.getInstance(document, baosPDF);
        writer.setPageEvent(new EndPage(clinicName, clinicTel, clinicFax, patientPhone, patientCityPostal, patientAddress, patientName, patientDOB, sigDoctorName, rxDate, origPrintDate, numPrint, imgFile, patientHIN, patientChartNo, pracNo, locale, billingNumber, pharmacyInfo));
        document.addTitle(title);
        document.addSubject("");
        document.addKeywords("pdf, itext");
        document.addCreator("OSCAR");
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
        if (PrescriptionQrCodeUIBean.isPrescriptionQrCodeEnabledForProvider(loggedInInfo.getLoggedInProviderNo())) {
            int scriptId = Integer.parseInt(req.getParameter("scriptId"));
            byte[] qrCodeImage = PrescriptionQrCodeUIBean.getPrescriptionHl7QrCodeImage(scriptId);
            Image qrCode = null;
            if (qrCodeImage != null) {
                qrCode = Image.getInstance(qrCodeImage);
            }
            if (qrCode != null) {
                document.add(qrCode);
            }
        }

        document.close();

        logger.debug("***END in generatePDFDocumentBytes2 FrmCustomedPDFServlet.java***");
        return baosPDF;

    }
}
