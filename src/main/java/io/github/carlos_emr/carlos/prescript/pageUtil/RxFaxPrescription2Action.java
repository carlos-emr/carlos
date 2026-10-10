/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.FileAlreadyExistsException;
import java.util.Date;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.openpdf.text.pdf.PdfReader;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.commn.model.FaxJob.Direction;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.fax.provider.FaxDestination;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderException;
import io.github.carlos_emr.carlos.form.pdfservlet.PrescriptionFaxService;
import io.github.carlos_emr.carlos.form.pdfservlet.PrescriptionFaxService.PreparedFaxFiles;
import io.github.carlos_emr.carlos.form.pdfservlet.PrescriptionPdfComposer;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Faxes a signed prescription to a pharmacy: renders it from the prescription record, writes the fax
 * files and queues the {@link FaxJob}.
 *
 * <p>This is a mutation with an outbound side effect, so it answers POST only and refuses GET and HEAD
 * with 405 before anything else runs (issue #3108). CSRFGuard checks the token on every POST, so the
 * fax cannot be triggered cross-site. The prescription page's Fax button posts here; the read-only
 * preview stays on {@code /form/createcustomedpdf}.</p>
 *
 * <p>The reply is a small HTML fragment the prescription page reads from its frame: {@code fax-success},
 * {@code fax-failure} (nothing was queued, safe to retry) or {@code fax-uncertain} (the job may already be
 * queued). The status says the same thing: 200 queued, 400 bad request, 401 no session, 403 not allowed,
 * 409 the prescription cannot be faxed as it stands, 500 failed, 503 outcome unknown.</p>
 *
 * @since 2026-07-06
 */
public class RxFaxPrescription2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();

    private final PrescriptionPdfComposer prescriptionPdfComposer;
    private final PrescriptionFaxService prescriptionFaxService;

    public RxFaxPrescription2Action() {
        this(SpringUtils.getBean(PrescriptionPdfComposer.class), SpringUtils.getBean(PrescriptionFaxService.class));
    }

    RxFaxPrescription2Action(PrescriptionPdfComposer prescriptionPdfComposer,
            PrescriptionFaxService prescriptionFaxService) {
        this.prescriptionPdfComposer = prescriptionPdfComposer;
        this.prescriptionFaxService = prescriptionFaxService;
    }

    /**
     * Faxes the prescription named by {@code scriptId}; see the class description for the reply.
     *
     * @return {@link ActionSupport#NONE}: the response is written directly
     */
    @Override
    public String execute() throws IOException {
        faxPrescription(ServletActionContext.getRequest(), ServletActionContext.getResponse());
        return NONE;
    }

    // FindSecBugs XSS_SERVLET: replies are fixed status HTML; the two dynamic values are HTML-encoded
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "replies are fixed status HTML; the two dynamic values are HTML-encoded")
    void faxPrescription(HttpServletRequest req, HttpServletResponse res) throws IOException {
        // Method tokens are case-sensitive (RFC 9110) and every browser sends "POST"; no case fold.
        if (!"POST".equals(req.getMethod())) {
            res.setHeader("Allow", "POST");
            res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Faxing a prescription requires POST");
            return;
        }

        res.setContentType("text/html");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(req);
        if (loggedInInfo == null) {
            reportRefusal(res, HttpServletResponse.SC_UNAUTHORIZED,
                    "Error: your session has ended. Log in again before faxing.");
            return;
        }

        byte[] signatureImage;
        Prescription prescription;
        HttpServletRequest pdfRequest;
        try {
            // Refuse a caller without the right to fax prescriptions for the patient the request names before
            // the script is even looked up. This uses only demographic_no, never scriptId, so the refusal says
            // nothing about whether a script exists (issue #3108: no Rx/fax privilege -> 403).
            if (!prescriptionFaxService.mayFaxForRequestedPatient(loggedInInfo, req.getParameter("demographic_no"))) {
                reportRefusal(res, HttpServletResponse.SC_FORBIDDEN,
                        "Error: you do not have permission to fax this prescription.");
                return;
            }

            // The prescription named by scriptId is loaded ONCE here and shared by the record-based
            // privilege check, the signature gate and the fax content binding below.
            prescription = prescriptionPdfComposer.requestedPrescription(req);

            // An authorization refusal must be reported as one. resolveSignatureImage withholds the
            // signature for a caller without _rx write on the patient, and reporting that as "not
            // signed" would send a read-only user to sign a script that IS signed (and that they could
            // not sign anyway). Only a caller who may already READ the script gets that specific
            // message. A caller WITHOUT read falls through to the "not signed" reply below — the very
            // same answer an id that matches no prescription produces — so the wording cannot be used
            // to tell an existing script from one that does not exist.
            if (prescriptionFaxService.isFaxDeniedByPrivilege(prescription, loggedInInfo)) {
                reportRefusal(res, HttpServletResponse.SC_FORBIDDEN,
                        "Error: you do not have permission to fax this prescription.");
                return;
            }

            // Resolve the prescriber's signature before touching the document: a fax is an outbound
            // legal copy and must never leave unsigned, whatever the page's Fax button gating said.
            signatureImage = prescriptionPdfComposer.resolveSignatureImage(req, loggedInInfo, prescription, true);
            if (signatureImage == null) {
                reportRefusal(res, HttpServletResponse.SC_CONFLICT,
                        "Error: the prescription is not signed. Sign it before faxing.");
                return;
            }

            // A fax is rendered from the prescription RECORD, never from the request body: the stored
            // signature drawn on it belongs to that record, so the drug lines above it and the signing
            // name must be the record's too (see PrescriptionPdfComposer.bindFaxContentToRecord).
            pdfRequest = prescriptionPdfComposer.bindFaxContentToRecord(req, prescription);
            if (pdfRequest == null) {
                reportRefusal(res, HttpServletResponse.SC_CONFLICT,
                        "Error: the prescription record is incomplete and cannot be faxed.");
                return;
            }
        } catch (RuntimeException failure) {
            // No PDF/file/queue writes have started: unlike a failed persistence response,
            // a record/signature/provider lookup failure is definitely safe to retry.
            reportFaxFailure(res, res.getWriter(), "Prescription fax record preparation failed", failure);
            return;
        }

        PrintWriter writer = res.getWriter();
        try (ByteArrayOutputStream baosPDF = composeOrReportFailure(pdfRequest, signatureImage, prescription, res)) {
            if (baosPDF == null) {
                return;
            }
            String faxNo = req.getParameter("pharmaFax");
            String rawFaxNo = faxNo;
            if (faxNo != null) {
                faxNo = faxNo.trim().replaceAll("\\D", "");
            }
            String pharmaName = req.getParameter("pharmaName");
            String faxNumber = req.getParameter("clinicFax");
            if (faxNumber != null) {
                faxNumber = faxNumber.trim().replaceAll("\\D", "");
            }
            if (faxNo == null || faxNo.length() < 7) {
                res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                writer.println("<div id='fax-failure'><h3>Error: Valid fax number not found!</h3></div>");
            } else {
                // write to file
                String pdfid = req.getParameter("pdfId");
                // Reject, rather than rewrite, an invalid identifier. A missing identifier used
                // to create prescription_null.pdf and rewriting could make two distinct caller
                // values collide on one already-signed clinical document.
                if (!PrescriptionFaxService.isValidDocumentId(pdfid)) {
                    res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    writer.println("<div id='fax-failure'><h3>Error: Unable to generate fax.</h3></div>");
                    writer.flush();
                    return;
                }
                FaxConfig selectedFaxConfig;
                try {
                    selectedFaxConfig = prescriptionFaxService.findActiveFaxConfig(faxNumber);
                } catch (RuntimeException failure) {
                    reportFaxFailure(res, writer, "Prescription fax account lookup failed", failure);
                    return;
                }
                if (selectedFaxConfig == null) {
                    res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    writer.println("<div id='fax-failure'><h3>Error: the selected fax line is not configured.</h3></div>");
                    writer.flush();
                    return;
                }

                try {
                    faxNo = FaxDestination.forQueue(rawFaxNo, selectedFaxConfig.getProviderType());
                } catch (FaxProviderException invalidDestination) {
                    res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    writer.println("<div id='fax-failure'><h3>Error: Valid fax number not found!</h3></div>");
                    writer.flush();
                    return;
                }

                String pdfFile = "prescription_" + pdfid + ".pdf";
                String documentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");

                PreparedFaxFiles preparedFiles = prepareFaxFilesOrReportFailure(documentDir, pdfid, pdfFile, faxNo,
                        baosPDF, res, writer);
                if (preparedFiles == null) {
                    return;
                }

                String providerNo = loggedInInfo.getLoggedInProviderNo();
                boolean persistenceAttempted = false;
                try {
                    int numPages;
                    try (PdfReader pdfReader = new PdfReader(preparedFiles.getDocumentPdf().toString())) {
                        numPages = pdfReader.getNumberOfPages();
                    }

                    FaxJob faxJob = new FaxJob();
                    faxJob.setDestination(faxNo);
                    faxJob.setFax_line(faxNumber);
                    faxJob.setFile_name(pdfFile);
                    faxJob.setUser(selectedFaxConfig.getFaxUser());
                    faxJob.setRecipient(pharmaName);
                    faxJob.setNumPages(numPages);
                    faxJob.setStamp(new Date());
                    faxJob.setStatus(FaxJob.STATUS.WAITING);
                    faxJob.setOscarUser(providerNo);
                    faxJob.setDemographicNo(prescription.getDemographicId());
                    faxJob.setSenderEmail(selectedFaxConfig.getSenderEmail());
                    faxJob.setDirection(Direction.OUT);

                    // A commit acknowledgement can be lost after the job becomes visible.
                    // Once persistence starts, an exception does not prove rollback: retain
                    // the files and report uncertainty rather than inviting a duplicate fax.
                    persistenceAttempted = true;
                    prescriptionFaxService.queueFaxJob(loggedInInfo, faxJob, prescription.getId());
                } catch (IOException | RuntimeException e) {
                    if (persistenceAttempted) {
                        reportFaxUncertain(res, writer, e);
                    } else {
                        preparedFiles.cleanupAfterFailure(e);
                        reportFaxFailure(res, writer, "Prescription fax preparation failed", e);
                    }
                    return;
                }

                // The fax is committed and queueable now. A secondary legacy audit failure must
                // not turn the response into a failure (which would invite a duplicate retry),
                // and its document must not be removed out from under FaxSender. The
                // transaction above already wrote the correlated FaxClientLog audit row.
                try {
                    LogAction.addLog(providerNo, LogConst.SENT, LogConst.CON_FAX, "PRESCRIPTION " + pdfFile);
                } catch (RuntimeException e) {
                    logger.error("Prescription fax was queued, but the legacy SENT audit entry failed ({})",
                            e.getClass().getSimpleName());
                }
                writer.println("<div id='fax-success' style='color:green;'><h3>Fax successfully generated</h3><p>" + SafeEncode.forHtml(pharmaName) + " (" + SafeEncode.forHtml(faxNo) + ")</p><br><p>This window will close after follow-up processing completes.</p></div>");
            }
            writer.flush();

        }
    }

    private ByteArrayOutputStream composeOrReportFailure(HttpServletRequest request, byte[] signatureImage,
            Prescription prescription, HttpServletResponse response) throws IOException {
        try {
            return prescriptionPdfComposer.compose(request, signatureImage, prescription);
        } catch (IOException | RuntimeException failure) {
            // org.openpdf.text.DocumentException in OpenPDF 3 is a RuntimeException, so this includes it.
            // This boundary is strictly before file preparation and persistence. Rendering failures are
            // safe to retry, unlike a lost acknowledgement of the queue commit.
            reportFaxFailure(response, response.getWriter(), "Prescription fax PDF generation failed", failure);
            return null;
        }
    }

    private PreparedFaxFiles prepareFaxFilesOrReportFailure(String documentDir, String pdfid, String pdfFile,
            String faxNo, ByteArrayOutputStream baosPDF, HttpServletResponse res, PrintWriter writer) {
        // A missing file does not mean its name is unowned: an older WAITING job
        // may still reference it after partial cleanup. Never republish new clinical
        // content under that job's filename, even if every artifact is missing.
        try {
            if (prescriptionFaxService.isFileNameOwnedByJob(pdfFile)) {
                reportFaxUncertain(res, writer, new IllegalStateException("Fax artifact name is already owned by a job"));
                return null;
            }
        } catch (RuntimeException e) {
            reportFaxUncertain(res, writer, e);
            return null;
        }
        try {
            return prescriptionFaxService.prepareFaxFiles(documentDir, pdfid, pdfFile, faxNo, baosPDF);
        } catch (FileAlreadyExistsException e) {
            // A replay can collide with artifacts from an already queued job. Reject
            // without overwriting them, but never claim that another send is safe.
            reportFaxUncertain(res, writer, e);
            return null;
        } catch (IOException | RuntimeException e) {
            reportFaxFailure(res, writer, "Prescription fax file preparation failed", e);
            return null;
        }
    }

    /** A deliberate refusal: nothing was written or queued, so the page may let the user try again. */
    private static void reportRefusal(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        PrintWriter writer = res.getWriter();
        writer.println("<div id='fax-failure'><h3>" + SafeEncode.forHtml(message) + "</h3></div>");
        writer.flush();
    }

    private static void reportFaxUncertain(HttpServletResponse res, PrintWriter writer, Exception failure) {
        logger.error("Prescription fax outcome is uncertain; preserving existing fax artifacts ({})", failure.getClass().getSimpleName());
        res.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        writer.println("<div id='fax-uncertain'><h3>The fax result could not be confirmed.</h3>"
                + "<p>The job may already be queued. Check the fax outbox before sending again.</p></div>");
        writer.flush();
    }

    private static void reportFaxFailure(HttpServletResponse res, PrintWriter writer, String stage, Exception failure) {
        logger.warn("{}: type={}", stage, failure.getClass().getSimpleName());
        res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        writer.println("<div id='fax-failure'><h3>Error: Unable to generate fax.</h3><p>Please try again or contact support if the problem persists.</p></div>");
        writer.flush();
    }
}
