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

import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.openpdf.text.*;
import org.openpdf.text.pdf.*;
import org.apache.commons.io.FileUtils;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.dao.PrescriptionDao;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.prescript.util.RxUtil;
import io.github.carlos_emr.carlos.providers.data.ProSignatureData;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import io.github.carlos_emr.carlos.commn.exception.PatientDirectiveException;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.DigitalSignatureManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.commn.model.FaxJob.Direction;
import io.github.carlos_emr.carlos.commn.model.PharmacyInfo;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.FaxManager.TransactionType;
import io.github.carlos_emr.carlos.utility.LocaleUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.web.PrescriptionQrCodeUIBean;

import org.owasp.encoder.Encode;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.prescript.data.RxPharmacyData;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Servlet that generates customized prescription PDF documents with support for faxing.
 *
 * <p>This servlet handles two primary workflows:
 * <ul>
 *   <li><strong>PDF Generation</strong> -- Renders prescription content into a bordered PDF
 *       with clinic/patient headers, prescriber signature, and optional QR codes using OpenPDF
 *       direct content rendering.</li>
 *   <li><strong>Fax Submission</strong> -- Persists the generated PDF and creates a
 *       {@link FaxJob} record for asynchronous fax delivery to a pharmacy.</li>
 * </ul>
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
public class FrmCustomedPDFServlet extends HttpServlet {

    private static Logger logger = MiscUtils.getLogger();
    private final FaxJobDao faxJobDao = SpringUtils.getBean(FaxJobDao.class);

    private final FaxConfigDao faxConfigDao = SpringUtils.getBean(FaxConfigDao.class);
    private static FaxManager faxManager = SpringUtils.getBean(FaxManager.class);
    private final PrescriptionDao prescriptionDao = SpringUtils.getBean(PrescriptionDao.class);
    private final DigitalSignatureManager digitalSignatureManager = SpringUtils.getBean(DigitalSignatureManager.class);
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    private final DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);

    /** Every request parameter that names or locates the patient on the rendered page. */
    private static final List<String> IDENTITY_PARAMETERS = List.of(
            "patientName", "patientDOB", "patientAddress", "patientCityPostal",
            "patientHIN", "patientPhone", "patientChartNo");

    /** Parses a positive script number (prescription.script_no is a signed int); -1 when invalid. */
    private static int parsePositiveInt(String value) {
        if (value == null || !value.matches("\\d{1,10}")) {
            return -1;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** True when the bytes decode as an image OpenPDF can render; guards against a non-image upload. */
    private static boolean isRenderableImage(byte[] image) {
        try {
            org.openpdf.text.Image.getInstance(image);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Main entry point for prescription PDF generation and fax submission.
     * Parses request parameters, generates the PDF, and either streams it directly
     * to the response or persists it for asynchronous fax delivery.
     *
     * @param req HttpServletRequest containing prescription form parameters and fax details
     * @param res HttpServletResponse to write the PDF or fax status HTML to
     * @throws jakarta.servlet.ServletException if a servlet error occurs
     * @throws java.io.IOException if an I/O error occurs during PDF generation
     */
    @Override
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    // FindSecBugs XSS_SERVLET: fax branch writes fixed status HTML and encodes dynamic values; PDF branch writes binary content
    @SuppressFBWarnings(value = {"XSS_SERVLET", "PATH_TRAVERSAL_IN"}, justification = "XSS_SERVLET: fax branch writes fixed status HTML and encodes dynamic values; PDF branch writes binary content. PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use")
    public void service(HttpServletRequest req, HttpServletResponse res) throws jakarta.servlet.ServletException, java.io.IOException {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(req);
        boolean isFax = "oscarRxFax".equals(req.getParameter("__method"));
        boolean responseOutputStreamOpened = false;

        // The prescription named by scriptId is loaded ONCE here and shared by the privilege
        // pre-check, the signature gate and the fax content binding below.
        Prescription prescription = requestedPrescription(req);

        // An authorization refusal must be reported as one. resolveSignatureImage withholds the
        // signature for a caller without _rx write on the patient, and reporting that as "not
        // signed" would send a read-only user to sign a script that IS signed (and that they could
        // not sign anyway). Only a caller who may already READ the script gets that specific
        // message. A caller WITHOUT read falls through to the "not signed" reply below — the very
        // same answer an id that matches no prescription produces — so the wording cannot be used
        // to tell an existing script from one that does not exist.
        if (isFax && isFaxDeniedByPrivilege(prescription, loggedInInfo)) {
            res.setContentType("text/html");
            res.getWriter().println("<div id='fax-failure'><h3>Error: you do not have permission to fax this prescription.</h3></div>");
            return;
        }

        // Resolve the prescriber's signature before touching the document: a fax is an outbound
        // legal copy and must never leave unsigned, whatever the page's Fax button gating said.
        byte[] signatureImage = resolveSignatureImage(req, loggedInInfo, prescription);
        if (isFax && signatureImage == null) {
            res.setContentType("text/html");
            res.getWriter().println("<div id='fax-failure'><h3>Error: the prescription is not signed. Sign it before faxing.</h3></div>");
            return;
        }

        // A fax is rendered from the prescription RECORD, never from the request body: the stored
        // signature drawn on it belongs to that record, so the drug lines above it and the signing
        // name must be the record's too (see bindFaxContentToRecord).
        HttpServletRequest pdfRequest = req;
        if (isFax) {
            pdfRequest = bindFaxContentToRecord(req, prescription);
            if (pdfRequest == null) {
                res.setContentType("text/html");
                res.getWriter().println("<div id='fax-failure'><h3>Error: the prescription record has no drugs to fax.</h3></div>");
                return;
            }
        }

        try (ByteArrayOutputStream baosPDF = generatePDFDocumentBytes(pdfRequest, this.getServletContext(), signatureImage)) {

            if (isFax) {
                // this fax method shouldn't be here and will be removed in future edits.
                res.setContentType("text/html");
                PrintWriter writer = res.getWriter();
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
                    String document_dir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
                    
                    Path filepath = prepareValidatedFaxFilesOrReportFailure(document_dir, pdfid, pdfFile, faxNo,
                            baosPDF, res, writer);
                    if (filepath == null) {
                        return;
                    }

                    List<FaxConfig> faxConfigs = faxConfigDao.findAll(null, null);
                    String provider_no = LoggedInInfo.getLoggedInInfoFromSession(req).getLoggedInProviderNo();
                    FaxJob faxJob;
                    boolean validFaxNumber = false;

                    for (FaxConfig faxConfig : faxConfigs) {

                        if (faxConfig.getFaxNumber().equals(faxNumber)) {

                            int numPages;
                            try (PdfReader pdfReader = new PdfReader(filepath.toString())) {
                                numPages = pdfReader.getNumberOfPages();
                            }

                            faxJob = new FaxJob();
                            faxJob.setDestination(faxNo);
                            faxJob.setFax_line(faxNumber);
                            faxJob.setFile_name(pdfFile);
                            faxJob.setUser(faxConfig.getFaxUser());
                            faxJob.setRecipient(pharmaName);
                            faxJob.setNumPages(numPages);
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
                writer.flush();
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
                responseOutputStreamOpened = true;
                baosPDF.writeTo(sos);

                sos.flush();
            }
        } catch (DocumentException dex) {
            if (responseOutputStreamOpened) {
                throw new IOException("PDF response failed after output stream was opened", dex);
            }
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
            if (responseOutputStreamOpened) {
                throw dex;
            }
            // Log the error
            logger.debug("Signature file not found", dex);
            
            res.setContentType("text/html");
            PrintWriter writer = res.getWriter();
            writer.println("<script>alert('Signature not found. Please sign the prescription.');</script>");
        }

    }

    private Path prepareValidatedFaxFilesOrReportFailure(String documentDir, String pdfid, String pdfFile,
            String faxNo, ByteArrayOutputStream baosPDF, HttpServletResponse res, PrintWriter writer) {
        try {
            return prepareValidatedFaxFiles(documentDir, pdfid, pdfFile, faxNo, baosPDF);
        } catch (SecurityException | IOException e) {
            logger.warn("Prescription fax file preparation failed: type={}, message={}",
                    e.getClass().getSimpleName(), LogSafe.sanitize(e.getMessage(), 1024), e);
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            writer.println("<div id='fax-failure'><h3>Error: Unable to generate fax.</h3><p>Please try again or contact support if the problem persists.</p></div>");
            writer.flush();
            return null;
        }
    }

    private Path prepareValidatedFaxFiles(String documentDir, String pdfid, String pdfFile, String faxNo,
            ByteArrayOutputStream baosPDF) throws IOException {
        // Use PathValidationUtils for proper path validation
        File baseDirFile = PathValidationUtils.resolveConfiguredDirectory(documentDir, "DOCUMENT_DIR");
        File validatedPdfFile = PathValidationUtils.validatePath(pdfFile, baseDirFile);
        Path filepath = validatedPdfFile.toPath();

        writePdfFileIfMissing(filepath, baosPDF);

        // write to temporary file
        String tempPath = CarlosProperties.getInstance().getProperty("fax_file_location", System.getProperty("java.io.tmpdir"));
        File tempDirFile = PathValidationUtils.resolveConfiguredDirectory(tempPath, "fax_file_location");
        File validatedTempPdf = PathValidationUtils.validatePath("prescription_" + pdfid + ".pdf", tempDirFile);
        Path tempPdf = validatedTempPdf.toPath();

        // Copying the fax pdf.
        if (Files.exists(filepath) && !Files.exists(tempPdf)) {
            FileUtils.copyFile(filepath.toFile(), tempPdf.toFile());
        }

        File validatedTxtFile = PathValidationUtils.validatePath("prescription_" + pdfid + ".txt", tempDirFile);
        writeFaxTrackingFile(validatedTxtFile, faxNo);
        return filepath;
    }

    private void writePdfFileIfMissing(Path filepath, ByteArrayOutputStream baosPDF) throws IOException {
        try (java.io.OutputStream fileOut = Files.newOutputStream(filepath,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            baosPDF.writeTo(fileOut); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- PDF bytes written to file, not HTTP response
        } catch (FileAlreadyExistsException e) {
            // Preserve the existing PDF if another request created it first.
        }
    }

    private void writeFaxTrackingFile(File trackingFile, String faxNo) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(trackingFile.toPath(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            if (faxNo != null) {
                out.write(faxNo);
            }
        }
    }


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
        private byte[] signatureImage;
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
                       byte[] signatureImage, String patientHIN, String patientChartNo, String pracNo, Locale locale, String billingNumber, String pharmacyInfo) {
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
                if (this.signatureImage != null && this.signatureImage.length > 0) {
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

            } catch (Exception e) {
                logger.error("Error", e);
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
        String[] ar = s.split("</b>");
        String[] ar2 = ar[1].split("<br>");
        ArrayList<String> lst = new ArrayList<String>(Arrays.asList(ar2));
        lst.remove(0);
        String tel = lst.get(3);
        tel = tel.replace("Tel: ", "");
        String fax = lst.get(4);
        fax = fax.replace("Fax: ", "");
        String clinicName = lst.get(0) + "\n" + lst.get(1) + "\n" + lst.get(2);
        logger.debug("tel: {}", LogSafe.sanitize(tel));
        logger.debug("fax: {}", LogSafe.sanitize(fax));
        logger.debug("clinicName: {}", LogSafe.sanitize(clinicName));
        hm.put("clinicName", clinicName);
        hm.put("clinicTel", tel);
        hm.put("clinicFax", fax);

        return hm;

    }

    /**
     * Binds an outbound fax to the prescription RECORD named by {@code scriptId}. The drug lines
     * are regenerated from the script's drugs rows (the same {@code getFullOutLine} text
     * Preview2.jsp built the request from) and the signing name from the script's prescriber, so
     * the stored signature the fax is drawn with can only ever appear above that prescriber's own
     * prescription. Every other field (pharmacy, clinic, patient header) is still taken from the
     * request as before. When the request body is exactly a reordering of the record, its order is
     * kept so an honest fax is what the prescriber previewed; anything else is replaced by the
     * record and logged, because {@code scriptId} and the body are independent request parameters
     * and a caller with {@code _rx} write on the patient could otherwise fax arbitrary text under
     * another prescriber's stored signature.
     *
     * @return the request to render the fax from, or {@code null} when the record has no drugs
     *         (nothing legitimate to fax) or cannot be loaded
     */
    HttpServletRequest bindFaxContentToRecord(HttpServletRequest req) {
        return bindFaxContentToRecord(req, requestedPrescription(req));
    }

    /** As {@link #bindFaxContentToRecord(HttpServletRequest)} with the prescription already loaded. */
    HttpServletRequest bindFaxContentToRecord(HttpServletRequest req, Prescription prescription) {
        if (prescription == null || prescription.getDemographicId() == null) {
            return null;
        }
        // The row was loaded by this very id (requestedPrescription), so it is the script number.
        int scriptNo = parsePositiveInt(req.getParameter("scriptId"));
        String newline = System.getProperty("line.separator");
        List<String> recordLines = new ArrayList<>();
        for (RxPrescriptionData.Prescription drug
                : new RxPrescriptionData().getPrescriptionsByScriptNo(scriptNo, prescription.getDemographicId())) {
            String line = recordBlock(drug, newline);
            if (line != null && !line.isBlank()) {
                recordLines.add(line);
            }
        }
        if (recordLines.isEmpty()) {
            logger.warn("Refusing to fax prescription {}: its record has no drug lines", LogSafe.sanitize(String.valueOf(scriptNo)));
            return null;
        }

        // Keep the prescriber's preview order where the request is a reordering of the record.
        List<String> remaining = new ArrayList<>(recordLines);
        List<String> ordered = new ArrayList<>();
        List<String> requestBlocks = splitRxBlocks(req.getParameter("rx"), newline);
        for (String block : requestBlocks) {
            String wanted = normalizeRxBlock(block);
            for (Iterator<String> it = remaining.iterator(); it.hasNext(); ) {
                String candidate = it.next();
                if (normalizeRxBlock(candidate).equals(wanted)) {
                    ordered.add(candidate);
                    it.remove();
                    break;
                }
            }
        }
        if (!remaining.isEmpty() || ordered.size() != requestBlocks.size()) {
            // Anything but an exact reordering of the record is discarded wholesale: the fax is the
            // record in the record's own order, never a partially request-shaped body.
            logger.warn("Fax body for prescription {} did not match its record; faxing the record instead",
                    LogSafe.sanitize(String.valueOf(scriptNo)));
            ordered = new ArrayList<>(recordLines);
        }
        StringBuilder body = new StringBuilder();
        for (String line : ordered) {
            // Emit the block structure directly. generatePDFDocumentBytes splits rx on the platform
            // separator and starts a new block on an empty or one-character line, so a blank line
            // between drugs is all that is needed. Do NOT join with ";;" and then substitute every
            // ';' for a newline: this body is built from the record's own text, and a semicolon a
            // prescriber typed inside an instruction ("1 tab PO BID; hold if SBP<100") would become
            // a line break — and a one-character remainder a spurious block separator. Ordering was
            // already matched above through normalizeRxBlock, which ignores semicolons, so nothing
            // downstream depends on this body reproducing the page's ';'-encoded wire format.
            body.append(line.replace("\r\n", "\n").replace("\n", newline)).append(newline).append(newline);
        }

        String prescriber = prescription.getProviderNo();
        String signingName = "";
        String collegeId = "";
        String billingNo = "";
        if (prescriber != null && !prescriber.isBlank()) {
            ProSignatureData signatureData = new ProSignatureData();
            Provider provider = providerDao.getProvider(prescriber);
            if (signatureData.hasSignature(prescriber)) {
                signingName = signatureData.getSignature(prescriber);
            } else if (provider != null) {
                signingName = ((provider.getFirstName() == null ? "" : provider.getFirstName()) + " "
                        + (provider.getLastName() == null ? "" : provider.getLastName())).trim();
            }
            if (provider != null) {
                collegeId = provider.getPractitionerNo() == null ? "" : provider.getPractitionerNo();
                billingNo = provider.getBillingNo() == null ? "" : provider.getBillingNo();
            }
        }
        // EVERY field the fax renders must come from the record, not just the drug lines. The PDF
        // also prints additNotes immediately above the signature line, and the College ID and
        // billing number beside the prescriber's name — all read straight from the request. Binding
        // only "rx" and "sigDoctorName" left the control bypassable through a sibling parameter: a
        // caller with _rx write on the patient could post arbitrary additNotes and have it render
        // above another prescriber's stored signature, under that prescriber's name. Each of these
        // has a record source, so bind them the same way.
        Map<String, String> bound = new HashMap<>();
        bound.put("rx", body.toString());
        bound.put("sigDoctorName", signingName == null ? "" : signingName);
        bound.put("additNotes", prescription.getComments() == null ? "" : prescription.getComments());
        bound.put("pracNo", collegeId);
        bound.put("billingNumber", billingNo);
        bindPatientIdentity(req, prescription.getDemographicId(), bound);
        return new RecordBoundRequest(req, bound);
    }

    /**
     * Binds the patient block of the fax to the prescription's own demographic.
     *
     * <p>The drugs, the signing name and the prescriber's numbers are already taken from the
     * record, and {@link #resolveSignatureImage} has established that the caller holds {@code _rx}
     * write for the prescription's patient and that {@code demographic_no} names that same patient.
     * The identity PRINTED on the page was still whatever the request said: {@code patientName},
     * {@code patientDOB}, {@code patientHIN}, {@code patientAddress}, {@code patientCityPostal} and
     * {@code patientPhone} were read straight from parameters. A stale preview, or a tampered post
     * from a caller who legitimately holds {@code _rx} write on the patient, could therefore send
     * the verified drugs under the prescriber's stored signature while heading the page with a
     * different person's name, date of birth and health number — a correctly signed prescription
     * for the wrong patient. Bind them the same way as everything else.</p>
     *
     * <p>The formats reproduce what {@code rx/Preview2.jsp} posts for a legitimate render, so a
     * bound fax is byte-identical to an untampered one: the city/province/postal line uses the
     * page's own spacing rules, and the phone carries the localized {@code RxPreview.msgTel} label
     * the page prefixes. {@code showPatientDOB} is deliberately NOT bound — it selects whether the
     * clinic prints dates of birth at all, which is a display preference rather than identity, and
     * the value it gates is now the record's either way.</p>
     *
     * <p>{@code patientChartNo} is bound to empty because the Rx preview never populates it: the
     * faxed script has never shown a chart number, so the only thing the parameter could carry is
     * text an attacker chose. Leaving it caller-controlled would reopen the same hole in the one
     * identity slot that has no record source here.</p>
     */
    private void bindPatientIdentity(HttpServletRequest req, Integer demographicId, Map<String, String> bound) {
        // Every identity slot is overridden, including the ones that come back empty. A field left
        // unbound is a field the request still controls, so the blanks are part of the control:
        // absent demographic data must print as absent, never as whatever the caller supplied.
        for (String field : IDENTITY_PARAMETERS) {
            bound.put(field, "");
        }
        // rx/Preview2.jsp reaches the same row through RxPatientData, which is a pass-through over
        // DemographicManager; going straight to the manager keeps the consent and audit behaviour
        // of the preview without RxPatientData's static bean plumbing.
        Demographic demographic;
        try {
            demographic = demographicManager.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(req), demographicId);
        } catch (PatientDirectiveException e) {
            // The manager's read is gated by its own privilege check, which surfaces a consent directive
            // as PatientDirectiveException. resolveSignatureImage has already authorized this caller for
            // this patient on the same check, so this is defence in depth rather than an expected path;
            // when it does fire, the heading stays blank -- the same outcome as a missing row, and never
            // the request's values. ONLY that exception is absorbed: a database or wiring failure must
            // abort the fax loudly, not send a prescription with no patient on it.
            logger.warn("Faxing prescription for demographic {} with a blank patient heading: a directive refused the demographic read",
                    LogSafe.sanitize(String.valueOf(demographicId)), e);
            return;
        }
        if (demographic == null) {
            logger.warn("Faxing prescription for demographic {} with a blank patient heading: its demographic row is missing",
                    LogSafe.sanitize(String.valueOf(demographicId)));
            return;
        }
        String first = demographic.getFirstName() == null ? "" : demographic.getFirstName();
        String surname = demographic.getLastName() == null ? "" : demographic.getLastName();
        String city = demographic.getCity() == null ? "" : demographic.getCity();
        String province = demographic.getProvince() == null ? "" : demographic.getProvince();
        String postal = demographic.getPostal() == null ? "" : demographic.getPostal();
        String phone = demographic.getPhone() == null ? "" : demographic.getPhone();
        Date dob = demographic.getBirthDay() == null ? null : demographic.getBirthDay().getTime();

        bound.put("patientName", (first + " " + surname).trim());
        bound.put("patientDOB", RxUtil.DateToString(dob, "MMM d, yyyy"));
        bound.put("patientAddress", demographic.getAddress() == null ? "" : demographic.getAddress());
        bound.put("patientCityPostal", formatCityPostal(city, province, postal));
        bound.put("patientHIN", demographic.getHin() == null ? "" : demographic.getHin());
        bound.put("patientPhone", LocaleUtils.getMessage(req.getLocale(), "RxPreview.msgTel") + ": " + phone);
    }

    /**
     * The city/province/postal line exactly as {@code rx/Preview2.jsp} composes it, separator
     * chosen by which of the two parts are present:
     *
     * <ul>
     *   <li>both city and province: {@code ", "} — {@code "Hamilton, ON L8S 4L8"};</li>
     *   <li>province only: nothing, so the line starts at the province — {@code "ON L8S 4L8"};</li>
     *   <li>city only (or neither): a single space, which the empty province leaves as a double
     *       space before the postal code — {@code "Hamilton  L8S 4L8"}.</li>
     * </ul>
     *
     * <p>That last case looks like a typo and is not: it is what the page emits today, and the
     * point of reproducing the rule rather than tidying it is that binding this field must not
     * visibly change a legitimate fax. Fix it in {@code Preview2.jsp} and here together, or not at
     * all.</p>
     */
    static String formatCityPostal(String city, String province, String postal) {
        int check = (city.trim().isEmpty() ? 0 : 1) | (province.trim().isEmpty() ? 0 : 2);
        String separator = check == 3 ? ", " : check == 2 ? "" : " ";
        return String.format("%s%s%s %s", city, separator, province, postal);
    }


    /**
     * One drug's fax block, with the record's own line breaks restored.
     *
     * <p>{@code getFullOutLine()} flattens {@code special + "\n" + extra} by joining every line
     * with {@code "; "}, which makes a structural separator indistinguishable from a semicolon a
     * prescriber typed inside an instruction. Un-substituting semicolons therefore cannot be
     * correct in both directions: it either breaks "1 tab PO BID; hold if SBP&lt;100" across lines,
     * or leaves a genuinely multi-line direction collapsed onto one.</p>
     *
     * <p>So this rebuilds from the same source rather than decoding the flattened form: the
     * {@code special} field still carries its real line breaks, and whatever {@code getFullOutLine}
     * appended beyond it (refill and substitution notes) is recovered as the trailing remainder. A
     * shape that does not match falls back to the canonical single line, which is never wrong —
     * only less pretty.</p>
     */
    static String recordBlock(RxPrescriptionData.Prescription drug, String newline) {
        String flat = drug.getFullOutLine();
        if (flat == null || flat.isBlank()) {
            return flat;
        }
        String special = drug.getSpecial();
        if (special == null || special.isBlank()) {
            return flat;
        }
        String flatSpecial = RxPrescriptionData.getFullOutLine(special);
        if (flatSpecial.isEmpty() || !flat.startsWith(flatSpecial)) {
            return flat;
        }
        List<String> lines = new ArrayList<>();
        for (String specialLine : special.split("\n")) {
            if (!specialLine.isBlank()) {
                lines.add(specialLine.trim());
            }
        }
        String extra = flat.substring(flatSpecial.length()).replaceFirst("^;\\s*", "").trim();
        if (!extra.isEmpty()) {
            lines.add(extra);
        }
        return lines.isEmpty() ? flat : String.join(newline, lines);
    }

    /**
     * The blank-line-separated blocks of a posted {@code rx} body. Only a blank or whitespace-only
     * line (which includes a stray {@code \r} from CRLF input) separates blocks; a one-character
     * line such as a quantity of "1" is content and stays inside its block.
     */
    static List<String> splitRxBlocks(String rx, String newline) {
        List<String> blocks = new ArrayList<>();
        if (rx == null) {
            return blocks;
        }
        StringBuilder current = new StringBuilder();
        for (String s : rx.split(newline)) {
            if (s.isBlank()) {
                if (current.length() > 0) {
                    blocks.add(current.toString());
                }
                current.setLength(0);
            } else {
                current.append(s).append(newline);
            }
        }
        if (current.length() > 0) {
            blocks.add(current.toString());
        }
        return blocks;
    }

    /** Whitespace- and separator-insensitive form of a drug block ("; " and line breaks both collapse). */
    static String normalizeRxBlock(String block) {
        return block == null ? "" : block.replace(";", " ").replaceAll("\\s+", " ").trim();
    }

    /** A request whose {@code rx} and {@code sigDoctorName} come from the prescription record. */
    private static final class RecordBoundRequest extends HttpServletRequestWrapper {
        private final Map<String, String> overrides;

        RecordBoundRequest(HttpServletRequest request, Map<String, String> overrides) {
            super(request);
            this.overrides = overrides;
        }

        @Override
        public String getParameter(String name) {
            return overrides.containsKey(name) ? overrides.get(name) : super.getParameter(name);
        }

        @Override
        public String[] getParameterValues(String name) {
            return overrides.containsKey(name) ? new String[] {overrides.get(name)} : super.getParameterValues(name);
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> merged = new LinkedHashMap<>(super.getParameterMap());
            overrides.forEach((key, value) -> merged.put(key, new String[] {value}));
            return Collections.unmodifiableMap(merged);
        }
    }

    /**
     * True only when the prescription named by {@code scriptId} exists with a patient and the
     * caller may READ it but lacks {@code _rx} WRITE for that patient. A missing session,
     * malformed id, absent row, or a caller without READ is NOT reported as a privilege denial
     * (it returns {@code false}) and is left to the signature gate, which reports those as
     * "not signed" exactly as before.
     */
    boolean isFaxDeniedByPrivilege(HttpServletRequest req, LoggedInInfo loggedInInfo) {
        return isFaxDeniedByPrivilege(requestedPrescription(req), loggedInInfo);
    }

    /**
     * As {@link #isFaxDeniedByPrivilege(HttpServletRequest, LoggedInInfo)} with the prescription
     * already loaded. True only for a caller who holds {@code _rx} READ but not WRITE for the
     * prescription's patient.
     *
     * <p>A caller without READ answers {@code false} here and is therefore told the prescription is
     * "not signed" — identical to the answer for a {@code scriptId} that matches no prescription at
     * all, since both reach that reply with no signature resolved. That collision is deliberate: it
     * is what stops the permission wording from revealing whether a script id exists.</p>
     */
    boolean isFaxDeniedByPrivilege(Prescription prescription, LoggedInInfo loggedInInfo) {
        if (loggedInInfo == null || prescription == null || prescription.getDemographicId() == null) {
            return false;
        }
        String patient = String.valueOf(prescription.getDemographicId());
        try {
            return securityInfoManager.hasPrivilege(loggedInInfo, "_rx", SecurityInfoManager.READ, patient)
                    && !securityInfoManager.hasPrivilege(loggedInInfo, "_rx", SecurityInfoManager.WRITE, patient);
        } catch (RuntimeException e) {
            // hasPrivilege rethrows PatientDirectiveException (SecurityInfoManagerImpl); unguarded it
            // would crash the servlet instead of refusing the fax. Answer "not denied HERE" so the
            // request falls through to resolveSignatureImage, whose own guard withholds the signature
            // and produces the generic "not signed" reply. Nothing is authorized by this answer — it
            // only declines to emit the specific permission wording, which is right under a directive:
            // that wording would confirm the script exists.
            logger.warn("Privilege check failed for the fax permission gate; deferring to the signature gate", e);
            return false;
        }
    }

    /**
     * Splits the {@code rx} body the PDF renders into one entry per drug block.
     *
     * <p>A block ends at an empty line, a line that is just the separator, or a one-character line —
     * the last of those because a browser-posted body is CRLF-normalised on submit, so each
     * separator arrives as a lone {@code \r}.</p>
     *
     * <p><strong>The tail must be flushed explicitly.</strong> {@code String.split} drops TRAILING
     * empty strings, so a body ending with its own separator loses that separator from the array
     * entirely and the final block would never be added: a one-drug script would render with no
     * drug lines at all, above a real signature. The browser body hides this because its trailing
     * separator survives as that lone {@code \r}; the record-bound fax body is built server-side
     * with plain newlines and has no such sentinel. {@link #splitRxBlocks} flushes its tail the
     * same way.</p>
     *
     * @param rx      the body to split
     * @param newline the platform line separator the body was written with
     * @return one entry per block, in order; empty when {@code rx} holds no content
     */
    static List<String> splitRenderedRxBlocks(String rx, String newline) {
        List<String> listRx = new ArrayList<String>();
        if (rx == null) {
            return listRx;
        }
        String listElem = "";
        for (String s : rx.split(newline)) {
            // ONLY the lone carriage return is a separator, never any one-character line. The
            // sentinel this looks for is the "\r" left behind when a browser-submitted CRLF body is
            // split on a "\n" platform separator; a bare one-character line cannot occur that way,
            // because every line of such a body still carries its own trailing "\r". The
            // record-bound fax body (bindFaxContentToRecord) is built server-side with plain
            // newlines and has no sentinel at all, so under the old "s.length() == 1" test a
            // genuine one-character prescription line -- a standalone dose such as "1" -- was
            // treated as a block break and silently dropped from the faxed script.
            //
            // The former "s.equals(newline)" branch is gone with it: split(newline) never yields a
            // segment equal to its own delimiter, so it was unreachable and only made the separator
            // contract read as though a literal newline token could arrive here.
            if (s.isEmpty() || s.equals("\r")) {
                listRx.add(listElem);
                listElem = "";
            } else {
                listElem = listElem + s;
                listElem += newline;
            }
        }
        if (!listElem.isEmpty()) {
            listRx.add(listElem);
        }
        return listRx;
    }

    /** The prescription named by the request's {@code scriptId}, or {@code null} when absent or malformed. */
    Prescription requestedPrescription(HttpServletRequest req) {
        int scriptNo = parsePositiveInt(req.getParameter("scriptId"));
        return scriptNo > 0 ? prescriptionDao.find(scriptNo) : null;
    }

    /**
     * Resolves the prescriber's signature image for the PDF.
     *
     * <p>Authorization comes first and is derived from the prescription itself: the script named by
     * {@code scriptId} is loaded, and everything below is released only to a caller holding
     * {@code _rx} read for THAT prescription's patient — the same gate {@code ImageRenderingServlet}
     * applies to the on-screen preview. Neither the caller-supplied {@code demographic_no} nor the
     * caller-supplied {@code imgFile} can widen that: the patient context is the prescription's.</p>
     *
     * <p>With that established, the image is, in priority order:</p>
     * <ol>
     *   <li>the signature-pad capture named by {@code imgFile} — only THIS provider's capture
     *       ({@code signature_<loggedInProviderNo><digits>.jpg}) inside {@code java.io.tmpdir}, and
     *       only if it decodes as an image;</li>
     *   <li>the {@link DigitalSignature} stored on the prescription: a hand-drawn signature saved
     *       earlier, or the stamp applied on write. This is also what a reprint renders. It is used
     *       only when it is a prescription signature for the same patient.</li>
     * </ol>
     *
     * @return decoded image bytes, or {@code null} when no signature applies or the caller is not
     *         authorized for the prescription's patient
     */
    // FindSecBugs PATH_TRAVERSAL_IN: the pad file name is reduced to its base name and confined to java.io.tmpdir by PathValidationUtils.validatePath
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "the pad file name is reduced to its base name and confined to java.io.tmpdir by PathValidationUtils.validatePath")
    byte[] resolveSignatureImage(HttpServletRequest req, LoggedInInfo loggedInInfo) {
        return resolveSignatureImage(req, loggedInInfo, loggedInInfo == null ? null : requestedPrescription(req));
    }

    /** As {@link #resolveSignatureImage(HttpServletRequest, LoggedInInfo)} with the prescription already loaded. */
    byte[] resolveSignatureImage(HttpServletRequest req, LoggedInInfo loggedInInfo, Prescription prescription) {
        if (loggedInInfo == null || prescription == null) {
            return null;
        }
        // The row was loaded by this very id (requestedPrescription), so it is the script number.
        int scriptNo = parsePositiveInt(req.getParameter("scriptId"));
        Integer demographicId = prescription.getDemographicId();
        // Authorize once, up front, by the prescription's OWN patient. Both the pad capture and the
        // stored signature are gated behind this, so a caller cannot fax another patient's (or a
        // stray) signature by supplying a different demographic_no or imgFile. Faxing persists a
        // FaxJob (an outbound mutation), so it requires _rx WRITE; a print/preview requires READ.
        boolean isFax = "oscarRxFax".equals(req.getParameter("__method"));
        String requiredRight = isFax ? SecurityInfoManager.WRITE : SecurityInfoManager.READ;
        boolean authorized;
        try {
            authorized = demographicId != null
                    && securityInfoManager.hasPrivilege(loggedInInfo, "_rx", requiredRight, String.valueOf(demographicId));
        } catch (RuntimeException e) {
            // hasPrivilege rethrows PatientDirectiveException; unguarded it would abort PDF generation
            // with a 500 instead of the deliberate refusal below. Any failure to establish the right
            // means the signature is not released — the same outcome as lacking it outright.
            logger.warn("Privilege check failed while resolving the signature for prescription {}; withholding it",
                    LogSafe.sanitize(String.valueOf(scriptNo)), e);
            authorized = false;
        }
        if (!authorized) {
            logger.debug("Denied signature render for prescription {}: caller lacks _rx {} for its patient",
                    LogSafe.sanitize(String.valueOf(scriptNo)), requiredRight);
            return null;
        }
        // The caller-supplied demographic_no is what the fax branch stamps onto the FaxJob (its audit
        // linkage). For a FAX it MUST be present, positive, and equal to the prescription's patient —
        // an absent/invalid value would otherwise reach FaxJob.demographicNo unchecked, so it is
        // required, not merely validated when present. For a print/preview the value is not
        // persisted, so only a positive mismatch is rejected (an absent one is harmless).
        int requestDemographic = parsePositiveInt(req.getParameter("demographic_no"));
        boolean badDemographic = isFax
                ? (requestDemographic <= 0 || demographicId.intValue() != requestDemographic)
                : (requestDemographic > 0 && demographicId.intValue() != requestDemographic);
        if (badDemographic) {
            logger.debug("Denied signature render for prescription {}: demographic_no missing or does not match its patient",
                    LogSafe.sanitize(String.valueOf(scriptNo)));
            return null;
        }

        // 1. the signature-pad capture written for this signing session.
        // The pad is only honoured for the prescriber's OWN script. The rendered document names the
        // persisted prescriber (bindFaxContentToRecord takes the name from the record), so accepting
        // another provider's fresh capture here would put provider B's ink under provider A's name.
        // A non-prescriber falls through to the stored signature, which is the designed
        // reprint/refax path: it renders the signature the prescriber themselves left on the script.
        String imgFile = req.getParameter("imgFile");
        String signingProviderNo = loggedInInfo.getLoggedInProviderNo();
        String prescribingProviderNo = prescription.getProviderNo();
        if (imgFile != null && !imgFile.isBlank() && signingProviderNo != null && !signingProviderNo.isBlank()
                && signingProviderNo.equals(prescribingProviderNo)) {
            try {
                File tempDir = PathValidationUtils.validateConfiguredDirectory(System.getProperty("java.io.tmpdir"), "java.io.tmpdir");
                File padFile = PathValidationUtils.validatePath(imgFile, tempDir);
                // Only THIS provider's signature-pad capture is honoured. The pad writes
                // signature_<requestId>.jpg into the shared java.io.tmpdir, and the request id is
                // <providerNo><millis> (DigitalSignatureUtils.generateSignatureRequestId), where
                // millis is System.currentTimeMillis() — exactly 13 digits from 2001 to 2286.
                // Requiring EXACTLY 13 trailing digits gives an unambiguous boundary between the
                // provider number and the timestamp, so a shorter provider number cannot prefix-match
                // a longer provider's capture (e.g. "99999" claiming "999998"'s file) and fax a
                // prescription under someone else's freshly drawn signature.
                String padPattern = "signature_" + Pattern.quote(signingProviderNo) + "\\d{13}\\.jpg";
                if (padFile.getName().matches(padPattern) && padFile.isFile()) {
                    byte[] image = Files.readAllBytes(padFile.toPath());
                    if (image.length > 0 && isRenderableImage(image)) {
                        return image;
                    }
                    logger.debug("Signature pad file is empty or not a readable image; falling back to the stored prescription signature");
                } else {
                    logger.debug("Signature pad file not present or not a pad capture; falling back to the stored prescription signature");
                }
            } catch (SecurityException e) {
                logger.warn("Blocked signature pad file path; falling back to the stored prescription signature", e);
            } catch (IOException e) {
                logger.warn("Unable to read signature pad file; falling back to the stored prescription signature", e);
            }
        }

        // 2. the stored signature on the prescription (hand-drawn earlier, or the stamp).
        if (prescription.getDigitalSignatureId() == null) {
            return null;
        }
        int signatureId = prescription.getDigitalSignatureId();
        DigitalSignature metadata = digitalSignatureManager.getDigitalSignatureMetadata(signatureId);
        if (metadata == null || metadata.getModuleType() != ModuleType.PRESCRIPTION
                || metadata.getDemographicId() == null
                || !metadata.getDemographicId().equals(demographicId)) {
            logger.debug("Stored signature does not belong to prescription {}; not rendering it", LogSafe.sanitize(String.valueOf(scriptNo)));
            return null;
        }
        DigitalSignature signature = digitalSignatureManager.getDigitalSignature(signatureId);
        if (signature == null || signature.getSignatureImage() == null || signature.getSignatureImage().length == 0) {
            return null;
        }
        // Same bar as the pad capture: a stored blob that OpenPDF cannot decode would pass the
        // "signed" gate here and then be dropped silently in EndPage, sending a fax reported as
        // signed with a blank signature line. Treat undecodable bytes as no signature at all.
        if (!isRenderableImage(signature.getSignatureImage())) {
            logger.warn("Stored signature {} for prescription {} is not a readable image; treating the script as unsigned",
                    LogSafe.sanitize(String.valueOf(signatureId)), LogSafe.sanitize(String.valueOf(scriptNo)));
            return null;
        }
        return signature.getSignatureImage();
    }

    /**
     * Generates the prescription PDF document as a byte array output stream.
     *
     * <p>Extracts all prescription parameters from the HTTP request (clinic info, patient
     * demographics, prescription text, QR code settings), constructs
     * an OpenPDF {@link Document} with the appropriate page size and margins, and writes
     * prescription entries as paragraphs with an {@link EndPage} event handler for the
     * page frame rendering.
     *
     * @param req HttpServletRequest containing all prescription form parameters
     * @param ctx ServletContext for resource resolution
     * @param signatureImage decoded signature image bytes to draw above the signature line, or
     *                       {@code null} to leave the line blank (see {@link #resolveSignatureImage})
     * @return ByteArrayOutputStream containing the generated PDF bytes
     * @throws DocumentException if an OpenPDF document error occurs during PDF generation
     * @throws IOException if an I/O error occurs during PDF generation
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private ByteArrayOutputStream generatePDFDocumentBytes(final HttpServletRequest req, final ServletContext ctx, final byte[] signatureImage) throws DocumentException, IOException {
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

        logger.debug("method in generatePDFDocumentBytes {}", LogSafe.sanitize(method));
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
        List<String> listRx = splitRenderedRxBlocks(rx, newline);

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
