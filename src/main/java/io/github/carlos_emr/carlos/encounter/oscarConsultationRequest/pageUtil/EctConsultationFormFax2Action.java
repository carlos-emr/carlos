/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.openpdf.text.DocumentException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.fax.core.FaxAccount;
import io.github.carlos_emr.carlos.fax.core.FaxRecipient;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import io.github.carlos_emr.carlos.utility.LogSafe;
import java.util.Set;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * Struts2 action that handles faxing consultation requests to specialists and copy-to recipients.
 *
 * <p>Renders the consultation form (with all attachments) as a PDF via
 * {@link DocumentAttachmentManager}, then creates {@link FaxJob} entries for each recipient.
 * Supports optional cover pages prepended to the fax PDF. Each fax job is persisted and
 * logged for audit purposes.</p>
 *
 * <p>Fax recipients include the primary specialist plus any additional copy-to recipients
 * parsed from JSON-encoded form parameters. The sender's fax line is validated against
 * configured {@link FaxConfig} entries.</p>
 *
 * <p>Requires {@code _con} read privilege via {@link SecurityInfoManager}.</p>
 *
 * @see ConsultationPDFCreator
 * @see DocumentAttachmentManager
 * @see FaxManager
 * @since 2012-04-09
 */
public class EctConsultationFormFax2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final Logger logger = MiscUtils.getLogger();
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final FaxConfigDao faxConfigDao = SpringUtils.getBean(FaxConfigDao.class);
    private final FaxManager faxManager = SpringUtils.getBean(FaxManager.class);
    private final ClinicDAO clinicDAO = SpringUtils.getBean(ClinicDAO.class);

    private final DocumentAttachmentManager documentAttachmentManager = SpringUtils.getBean(DocumentAttachmentManager.class);

    private final NioFileManager nioFileManager = SpringUtils.getBean(NioFileManager.class);

    public EctConsultationFormFax2Action() {
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Renders the consultation form PDF and queues fax jobs for all recipients.
     *
     * <p>Processing flow:</p>
     * <ol>
     *   <li>Validates {@code _con} read privilege</li>
     *   <li>Renders the consultation form with attachments into a PDF</li>
     *   <li>Copies the PDF to the CARLOS documents directory</li>
     *   <li>For each fax recipient: validates the fax number, optionally prepends a cover page,
     *       creates and persists a {@link FaxJob}, and logs the transaction</li>
     * </ol>
     *
     * @return String "success" on successful fax queuing, "cancel" if cancelled,
     *         "error" on failure, or null on unexpected error
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use.
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of the literal HTTP method name (GET/HEAD) for the method-verb gate; not a security or authorization decision on user identity.
    @SuppressFBWarnings(value = {"PATH_TRAVERSAL_IN", "IMPROPER_UNICODE"}, justification = "path validated for directory containment via PathValidationUtils before use; method-name comparison is the HTTP verb gate, not an identity decision")
    @Override
    public String execute() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_con", "r", null)) {
            throw new SecurityException("missing required sec object (_con)");
        }
        // Faxing PHI to a request-selected recipient is a fax mutation, so it must also carry _fax
        // write — the same gate Fax2Action enforces. _con read alone let a consult-only user queue
        // PHI to an arbitrary fax number.
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_fax)");
        }
        // Reject GET/HEAD before any side effect (render, cover-page write, FaxJob persist): this
        // action queues a PHI fax to a request-supplied number, and CSRFGuard validates non-GET
        // requests only — a bare <img src="...ConsultationFormFax?..."> in the clinician's browser
        // could otherwise fire a fax with no CSRF token. CoverPage.jsp submits via <form method="post">
        // (cancel is a `method=cancel` body param on the same POST), so no UI change is required.
        // Mirrors the gate the same PR added to Fax2Action.
        String httpMethod = request.getMethod();
        if ("GET".equalsIgnoreCase(httpMethod) || "HEAD".equalsIgnoreCase(httpMethod)) {
            sendErrorQuietly(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Method not allowed");
            return NONE;
        }

        //EctConsultationFaxForm ectConsultationFaxForm = (EctConsultationFaxForm) form;

        if ("cancel".equals(this.getMethod())) {
            return "cancel";
        }

    	this.setRequest(request);
	   	String reqId = this.getRequestId();
		String demoNo = this.getDemographicNo();
		// Patient-record access check (same as Fax2Action): a fax carries this demographic's PHI, so
		// the user must be allowed to access this patient's record, not merely hold _con/_fax.
		if (demoNo != null && !demoNo.trim().isEmpty()) {
			int demographicNo;
			try {
				demographicNo = Integer.parseInt(demoNo.trim());
			} catch (NumberFormatException e) {
				// A non-numeric demographic cannot be authorized against any patient record; fail closed.
				throw new SecurityException("missing required patient access");
			}
			if (!securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, demographicNo)) {
				throw new SecurityException("missing required patient access");
			}
		}
		String faxNumber = this.getSenderFaxNumber();
		String consultResponsePage = request.getParameter("consultResponsePage");
		boolean doCoverPage = this.isCoverpage();
		String note = "";
		if ( doCoverPage ) {
			note = request.getParameter("note") == null ? "" : request.getParameter("note");
			// dont ask!
			if (note.isEmpty()) {
				note = this.getComments();
			}
		}
		FaxAccount sender = this.getSender();
		Clinic clinic = clinicDAO.getClinic();
		sender.setSubText(clinic.getClinicName());
		sender.setAddress(clinic.getClinicAddress());
		sender.setFacilityName(clinic.getClinicName());

        /*
         * This is a temporary solution until the fax code is refactored and added to their respective manager classes.
         */
        String provider_no = loggedInInfo.getLoggedInProviderNo();
        String error = "";
        Exception exception = null;

        request.setAttribute("reqId", reqId);
        request.setAttribute("demographicId", demoNo);
        Path faxPdf = null;
        try {
            faxPdf = documentAttachmentManager.renderConsultationFormWithAttachments(request, response);
        } catch (PDFGenerationException e) {
            logger.error(e.getMessage(), e);
            String errorMessage = "This fax could not be sent. \n\n" + e.getMessage();
            request.setAttribute("errorMessage", errorMessage);
            return "error";
        }
        String faxPdfPath = nioFileManager.copyFileToOscarDocuments(faxPdf.toString());
        if (faxPdfPath == null) {
            // Promotion into the document store failed (copy error or destination conflict) —
            // details are in the server log. Surface it instead of NPE-ing on Paths.get(null).
            logger.error("Consultation fax PDF could not be stored in the document directory; aborting fax");
            request.setAttribute("errorMessage",
                    "This fax could not be sent. \n\nThe fax document could not be stored for sending; please retry or contact your administrator.");
            return "error";
        }
        faxPdf = Paths.get(faxPdfPath);
        Path pdfToFax;
        List<FaxConfig> faxConfigs = faxConfigDao.findAll(null, null);
        int count = 0;
        Set<FaxRecipient> faxRecipients;
        try {
            faxRecipients = this.getAllFaxRecipients();
        } catch (RuntimeException e) {
            logger.error("Consultation fax aborted: could not parse the copy-to recipient list", e);
            request.setAttribute("errorMessage",
                    "This fax could not be sent. \n\nOne or more copy-to recipients could not be read; no faxes were queued.");
            return "error";
        }

        // Pre-validate the whole batch BEFORE persisting any job, so the consultation fax is
        // all-or-nothing. Previously both failure modes were only discovered mid-loop: a
        // misconfigured sender account silently persisted ERROR jobs (never sent) yet still returned
        // SUCCESS — a referral the clinician believed went out — and a too-short recipient number
        // threw a DocumentException after earlier recipients were already committed and sendable
        // (partial transmission). The sender-account match is the same for every recipient (it keys
        // on the sender's own fax number), so it is resolved once here.
        FaxConfig matchedConfig = null;
        for (FaxConfig faxConfig : faxConfigs) {
            if (faxConfig.getFaxNumber().equals(faxNumber)) {
                matchedConfig = faxConfig;
                break;
            }
        }
        if (matchedConfig == null) {
            logger.error("Consultation fax aborted: no active fax account configured for the sender fax number {}",
                    LogSafe.sanitize(faxNumber));
            request.setAttribute("errorMessage",
                    "This fax could not be sent. \n\nThe sending fax account is not configured; please contact your administrator.");
            return "error";
        }
        List<String> invalidRecipients = new ArrayList<>();
        for (FaxRecipient faxRecipient : faxRecipients) {
            String recipientFax = faxRecipient.getFax();
            if (recipientFax == null || recipientFax.length() < 7) {
                invalidRecipients.add(faxRecipient.getName());
            }
        }
        if (!invalidRecipients.isEmpty()) {
            logger.error("Consultation fax aborted: {} recipient(s) have an invalid fax number; no jobs queued",
                    invalidRecipients.size());
            request.setAttribute("errorMessage",
                    "This fax could not be sent. \n\nOne or more recipients have an invalid fax number; no faxes were queued.");
            return "error";
        }
        sender.setFaxNumberOwner(matchedConfig.getAccountName());

        // Build every FaxJob (including its cover page and page count) BEFORE persisting any, then
        // hand the whole batch to a single @Transactional manager call. Cover-page creation is a
        // filesystem side effect that a JPA rollback cannot undo, so it stays in this build phase;
        // the DB persist+audit-log of all recipients then commits atomically. Previously each job was
        // persisted inline in the loop with no surrounding transaction, so a failure on recipient N
        // (a cover-page/page-count error, or a DB error) left recipients 1..N-1 as committed WAITING
        // rows the FaxSender would transmit while the clinician only saw an error page — a partial
        // fax the "all-or-nothing" comment above wrongly promised was impossible.
        List<FaxJob> builtFaxJobs = new ArrayList<>();
        try {
            for (FaxRecipient faxRecipient : faxRecipients) {

                // reset target pdf.
                pdfToFax = faxPdf;

                String faxNo = faxRecipient.getFax().trim().replaceAll("\\D", "");

                logger.info("Setting up consultation fax to {}", LogSafe.sanitize(faxRecipient.getName()));

                FaxJob faxJob = new FaxJob();
                faxJob.setDestination(faxNo);
                faxJob.setRecipient(faxRecipient.getName());
                faxJob.setFax_line(faxNumber);
                faxJob.setStamp(new Date());
                faxJob.setOscarUser(provider_no);
                faxJob.setDemographicNo(Integer.parseInt(demoNo));
                // Sender account was validated above, so every recipient is a real WAITING job.
                faxJob.setStatus(FaxJob.STATUS.WAITING);
                faxJob.setUser(matchedConfig.getFaxUser());

                //todo rethink this process.  It takes up too much disc space.
                if (doCoverPage) {
                    pdfToFax = faxManager.addCoverPage(loggedInInfo, note, faxRecipient, sender, faxPdf);

                    // delete the source file to save some disc space
                    if (count == (faxRecipients.size() - 1)) {
                        faxPdf = PathValidationUtils.validateExistingPath(faxPdf.toFile(), new File(NioFileManager.DOCUMENT_DIRECTORY)).toPath();
                        Files.deleteIfExists(faxPdf);
                    }
                }

                int numPages = EDocUtil.getPDFPageCount(pdfToFax.toString());

                faxJob.setFile_name(pdfToFax.getFileName().toString());
                faxJob.setNumPages(numPages);

                builtFaxJobs.add(faxJob);

                count++;
            }
        } catch (DocumentException de) {
            error = "DocumentException";
            exception = de;
        } catch (IOException ioe) {
            error = "IOException";
            exception = ioe;
        }
        if (error.equals("")) {
            // Atomic: persists all recipients and their audit logs in one transaction, so a failure
            // persisting any recipient rolls the whole batch back rather than leaving sendable orphans.
            faxManager.persistAndLogConsultationFaxJobs(loggedInInfo, builtFaxJobs, Integer.parseInt(reqId));
            LogAction.addLog(provider_no, LogConst.SENT, LogConst.CON_FAX, "CONSULT " + reqId);
            request.setAttribute("faxSuccessful", true);
            return SUCCESS;
        }
        if (!error.equals("")) {
            logger.error(error + " occured insided ConsultationPrintAction", exception);
            request.setAttribute("printError", Boolean.valueOf(true));
            return "error";
        }
        return null;
    }


    private String method;
    private String recipient;
    private String from;
    private String recipientFaxNumber;
    private String sendersPhone;
    private String sendersFax;
    private String senderFaxNumber;
    private String comments;
    private String requestId;
    private String transType;
    private String demographicNo;
    private String[] faxRecipients;
    private boolean coverpage;
    private Set<FaxRecipient> allFaxRecipients;
    private Set<FaxRecipient> copiedTo;
    private FaxAccount sender;

    public String getMethod() {
        return method;
    }
    @StrutsParameter
    public void setMethod(String method) {
        this.method = method;
    }
    public String getRecipient() {
        return recipient;
    }
    @StrutsParameter
    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }
    public String getFrom() {
        return from;
    }
    @StrutsParameter
    public void setFrom(String from) {
        this.from = from;
    }
    public String getRecipientFaxNumber() {
        if (recipientFaxNumber != null) {
            recipientFaxNumber = recipientFaxNumber.trim().replaceAll("\\D", "");
        }
        return recipientFaxNumber;
    }
    @StrutsParameter
    public void setRecipientFaxNumber(String recipientFaxNumber) {
        this.recipientFaxNumber = recipientFaxNumber;
    }
    public String getSendersPhone() {
        return sendersPhone;
    }
    @StrutsParameter
    public void setSendersPhone(String sendersPhone) {
        this.sendersPhone = sendersPhone;
    }
    public String getSendersFax() {
        return sendersFax;
    }
    @StrutsParameter
    public void setSendersFax(String sendersFax) {
        this.sendersFax = sendersFax;
    }

    public String getSenderFaxNumber() {
        return senderFaxNumber;
    }

    @StrutsParameter
    public void setSenderFaxNumber(String senderFaxNumber) {
        this.senderFaxNumber = senderFaxNumber;
    }

    public String getComments() {
        return comments;
    }
    @StrutsParameter
    public void setComments(String comments) {
        this.comments = comments;
    }
    public String getRequestId() {
        return requestId;
    }
    @StrutsParameter
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    public String getTransType() {
        return transType;
    }
    @StrutsParameter
    public void setTransType(String transType) {
        this.transType = transType;
    }
    public String getDemographicNo() {
        return demographicNo;
    }
    @StrutsParameter
    public void setDemographicNo(String demographicNo) {
        this.demographicNo = demographicNo;
    }
    public String[] getFaxRecipients() {
        if (faxRecipients ==  null) {
            return new String[]{};
        }
        return faxRecipients;
    }
    @StrutsParameter
    public void setFaxRecipients(String[] faxRecipients) {
        this.faxRecipients = faxRecipients;
    }
    public boolean isCoverpage() {
        return coverpage;
    }
    @StrutsParameter
    public void setCoverpage(boolean coverpage) {
        this.coverpage = coverpage;
    }
    /**
     * Returns all fax recipients, including the primary recipient and any copy-to entries.
     *
     * <p>Lazily initialized on first access. Combines the primary recipient (from
     * {@link #getRecipient()} and {@link #getRecipientFaxNumber()}) with all copy-to
     * recipients from {@link #getCopiedTo()}.</p>
     *
     * @return Set of FaxRecipient all fax recipients for this consultation
     */
    public Set<FaxRecipient> getAllFaxRecipients() {
        if (allFaxRecipients == null) {
            allFaxRecipients = new HashSet<FaxRecipient>();
            allFaxRecipients.add( new FaxRecipient( getRecipient(), getRecipientFaxNumber() ) );
            allFaxRecipients.addAll(getCopiedTo());
        }

        return allFaxRecipients;
    }

    /**
     * Parses and returns the copy-to fax recipients from the JSON-encoded form parameters.
     *
     * <p>Each entry in the {@code faxRecipients} array is a JSON fragment containing
     * recipient name and fax number, parsed into {@link FaxRecipient} objects.</p>
     *
     * @return Set of FaxRecipient the copy-to recipients (empty set if none)
     */
    public Set<FaxRecipient> getCopiedTo() {
        if (copiedTo == null) {
            // Parse into a local set and only publish it if every entry parsed: a silently dropped
            // copy-to recipient means the clinician believes a copy went out that never will, so a
            // parse failure fails the whole fax fast (execute() turns it into an error result) rather
            // than sending a partial batch. The raw recipient payload (name + fax contact data) is
            // never logged.
            Set<FaxRecipient> parsed = new HashSet<FaxRecipient>();
            int failures = 0;
            for (String faxRecipient : getFaxRecipients()) {
                try {
                    ObjectNode jsonObject = (ObjectNode) objectMapper.readTree("{" + faxRecipient + "}");
                    parsed.add(new FaxRecipient(jsonObject));
                }
                catch (Exception e) {
                    failures++;
                    logger.error("Consultation fax: a copy-to recipient entry could not be parsed (entry {})", failures);
                }
            }
            if (failures > 0) {
                throw new IllegalArgumentException(failures + " copy-to fax recipient(s) could not be parsed");
            }
            copiedTo = parsed;
        }
        return copiedTo;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public void setRequest(HttpServletRequest request) {
        this.request = request;
    }

    /**
     * Builds and returns the sender's fax account details from form parameters.
     *
     * @return FaxAccount the sender account with fax number, letterhead name, and phone
     */
    public FaxAccount getSender() {
        if (sender == null) {
            sender = new FaxAccount();
        }

        sender.setFax(getSenderFaxNumber());
        sender.setLetterheadName(getFrom());
        sender.setPhone(getSendersPhone());

        return sender;
    }

    /**
     * Writes an HTTP error status without letting an {@link IOException} escape into the Struts
     * result pipeline (mirrors {@code Fax2Action.sendErrorQuietly}). Used by the GET/HEAD method gate.
     */
    private void sendErrorQuietly(int statusCode, String message) {
        try {
            response.sendError(statusCode, message);
        } catch (IOException ex) {
            logger.error("Failed to send HTTP error response for the consultation fax method gate", ex);
        }
    }

}
