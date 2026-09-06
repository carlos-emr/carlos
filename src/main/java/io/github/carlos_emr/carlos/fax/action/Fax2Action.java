/**
 * Copyright (c) 2015-2019. The Pharmacists Clinic, Faculty of Pharmaceutical Sciences, University of British Columbia. All Rights Reserved.
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
 * The Pharmacists Clinic
 * Faculty of Pharmaceutical Sciences
 * University of British Columbia
 * Vancouver, British Columbia, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.fax.action;

import io.github.carlos_emr.CarlosProperties;
import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.annotation.DocumentPatientLink;

import org.apache.struts2.ActionSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.commn.model.FaxJob.STATUS;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApprovalService;
import io.github.carlos_emr.carlos.fax.dto.FaxJobParams;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.FaxManager.TransactionType;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.form.JSONUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.Loader;
import org.springframework.http.ContentDisposition;
import org.apache.pdfbox.pdmodel.PDDocument;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class Fax2Action extends ActionSupport {

    private static final String ACCESS_DENIED = "Access denied";
    /** Request attribute the error JSPs render; Struts action errors do not reach them on their own. */
    private static final String ACTION_ERRORS_ATTRIBUTE = "actionErrors";
    private static final String EFORM_FAX_MISSING_CONTENT_MESSAGE =
            "This eForm could not be fully rendered because required content or behavior is missing. "
            + "You can fax it only after approving the listed issues, but the document may be incomplete.";
    private static final String FAX_FILE_PATH_PARAM = "faxFilePath";
    private static final String ERROR_SENDING_ERROR_RESPONSE = "Error sending error response";
    // Session-scoped record of every temp PDF path prepareFax() has handed to THIS authenticated
    // session for review but not yet resolved, so a later queue() call can prove the
    // client-supplied faxFilePath it is about to consume is actually one this session's own
    // prepareFax() produced -- rather than trusting whatever app-temp-directory path the client
    // happens to submit, which could belong to a different session's unrelated staged fax preview.
    // A Set of paths, not one fixed key or one entry per fdid: a session can have more than one
    // fax preview in flight at once (concurrent tabs, or re-previewing the same eForm before
    // queuing an earlier attempt), and a single shared slot per fdid would let a later prepareFax()
    // overwrite an earlier still-unresolved claim for the SAME fdid, silently losing the ability to
    // clean up that earlier claim's file if it were later rejected. Each queue() call for an EFORM
    // consumes (removes) exactly the one entry matching its own faxFilePath, regardless of whether
    // that promotion succeeds or is rejected, so entries never accumulate in a long-lived session.
    // Package-private (not private) solely so tests in this package can seed the session the same
    // way a prior prepareFax() call would, without driving the full render pipeline.
    static final String CLAIMED_FAX_FILE_PATHS_SESSION_KEY =
            "io.github.carlos_emr.carlos.fax.action.Fax2Action.claimedFaxFilePaths";
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = MiscUtils.getLogger();
    private final FaxManager faxManager = SpringUtils.getBean(FaxManager.class);
    private final DocumentAttachmentManager documentAttachmentManager = SpringUtils.getBean(DocumentAttachmentManager.class);
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private transient NioFileManager nioFileManager;
    private transient EFormRenderApprovalService renderApprovalService;
    private transient EFormDataDao eFormDataDao;


    /**
     * Dispatches on the {@code method} request parameter, gating mutations behind a
     * verb check first.
     *
     * <p>{@code queue()} persists {@link FaxJob} rows and promotes files into the fax
     * queue; {@code cancel()} -- including this method's own no-{@code method}
     * fall-through -- deletes temporary files and PHI preview caches. Both are
     * mutations and must never execute on GET/HEAD. {@code getPreview}, {@code
     * getPageCount}, and {@code prepareFax} stay verb-open: {@code CoverPage.jsp}
     * builds {@code <img src>}/link GETs for {@code getPreview} and polls {@code
     * getPageCount}, and {@code AddEForm2Action.redirectToPreparedFax()} issues a
     * server-side {@code sendRedirect()} to {@code prepareFax} that the browser
     * always follows with a GET -- rejecting GET there would break the eForm fax
     * flow. {@code prepareFax} itself only renders an ephemeral temp PDF for review
     * (via {@link DocumentAttachmentManager#stageEFormPacketForFaxPreview}); it does not
     * persist a queued fax job or any permanent record.
     *
     * @return the Struts result name for the dispatched operation, or {@link #NONE}
     *         after a direct-response write or a 405 rejection
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of the literal HTTP method name (GET/HEAD) for the method-verb gate; not a security or authorization decision on user identity.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of the literal HTTP method name (GET/HEAD) for the method-verb gate; not a security or authorization decision on user identity")
    public String execute() {
        String method = request.getParameter("method");
        boolean readOnly = "getPreview".equals(method) || "getPageCount".equals(method) || "prepareFax".equals(method);
        boolean approvalSubmission = "prepareFax".equals(method)
                && request.getParameter("renderApproval") != null;
        String httpMethod = request.getMethod();
        if ((!readOnly || approvalSubmission) && ("GET".equalsIgnoreCase(httpMethod) || "HEAD".equalsIgnoreCase(httpMethod))) {
            // queue() persists fax jobs and promotes files; cancel() (also the no-method
            // fall-through below) deletes temp files and PHI preview caches -- mutations must
            // not ride a GET/HEAD. CoverPage.jsp submits both via <form method="post">, so no UI
            // change is required.
            sendErrorQuietly(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Method not allowed");
            return NONE;
        }
        if ("queue".equals(method)) {
            return queue();
        } else if ("cancelStagedEFormFax".equals(method)) {
            cancelStagedEFormFax();
            return NONE;
        } else if ("prepareFax".equals(method)) {
            return prepareFax();
        } else if ("getPreview".equals(method)) {
            getPreview();
            // getPreview streams bytes directly to the response; return NONE (not a bare null) so Struts
            // does not resolve a named result / write HTML into the binary download (direct-response contract).
            return NONE;
        } else if ("getPageCount".equals(method)) {
            getPageCount();
            return NONE;
        }
        return cancel();
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = {"IMPROPER_UNICODE", "UNVALIDATED_REDIRECT"}, justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    @SuppressWarnings("unused")
    public String cancel() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        // Direct gate (2Action convention): cancel deletes temp files and PHI preview caches, so
        // it must not rely solely on flush()'s internal _fax gate — and the empty-path redirect
        // branch had no check at all.
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", "r", null)) {
            throw new SecurityException("missing required sec object (_fax)");
        }
        String faxForward = transactionType;

        if (faxFilePath != null && !faxFilePath.trim().isEmpty()) {
            faxManager.validateFilePath(faxFilePath);
            if (!faxManager.flush(loggedInInfo, faxFilePath)) {
                if (logger.isErrorEnabled()) {
                    logger.error("Failed to clear fax preview cache or temporary file: {}", LogSafe.sanitize(faxFilePath, 1024));
                }
                // Do not redirect: a redirect discards the action error and the user believes the
                // cancel (and PHI cleanup) succeeded. Render the preview page with the failure.
                request.setAttribute("faxCleanupFailed", Boolean.TRUE);
                return "preview";
            }
            // The staged file is gone, so its claim can never be promoted. Leaving it behind
            // grew the session's claim set by one entry for every preview a clinician opened
            // and abandoned, for the life of the session.
            consumeClaimedFaxFilePathFromSession();
        }

        if (TransactionType.CONSULTATION.name().equalsIgnoreCase(transactionType)) {
            try {
                response.sendRedirect(request.getContextPath() + "/encounter/ViewRequest?de=" + demographicNo + "&requestId=" + transactionId);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return NONE;
        } else if (TransactionType.EFORM.name().equalsIgnoreCase(transactionType)) {
            try {
                response.sendRedirect(request.getContextPath() + "/eform/efmshowform_data?fdid=" + transactionId + "&parentAjaxId=eforms");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return NONE;
        }

        return faxForward;
    }

    /** Revokes the one-time incomplete-render approval and deletes its unclaimed staged PDF. */
    // FindSecBugs UNVALIDATED_REDIRECT: the servlet context plus a fixed application route is same-origin; transactionId is an integer, not a redirect target.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is the same-origin servlet context plus a fixed application route; transactionId is an integer, not attacker-controlled URL input")
    private void cancelStagedEFormFax() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(
                loggedInInfo, "_fax", SecurityInfoManager.READ, null)) {
            sendErrorQuietly(HttpServletResponse.SC_FORBIDDEN, ACCESS_DENIED);
            return;
        }
        if (transactionId == null || demographicNo == null) {
            sendErrorQuietly(HttpServletResponse.SC_BAD_REQUEST, "Invalid eForm fax approval");
            return;
        }
        renderApprovalService().cancelStagedFaxPreview(
                request, loggedInInfo, transactionId, String.valueOf(demographicNo),
                request.getParameter("renderApproval"));
        // The same redirect is returned for an already-consumed, expired, or mismatched token so
        // this cleanup endpoint does not become a token-validity oracle.
        try {
            response.sendRedirect(request.getContextPath()
                    + "/eform/efmshowform_data?fdid=" + transactionId + "&parentAjaxId=eforms");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Validates all input parameters for security before processing the fax request.
     * Implements comprehensive input validation to prevent security vulnerabilities including:
     * - Path traversal attacks
     * - SQL injection
     * - Invalid patient access
     * - Malformed fax numbers
     *
     * @param loggedInInfo the logged-in user information
     * @throws SecurityException if validation fails or user lacks required privileges
     */
    private void validateFaxInputs(LoggedInInfo loggedInInfo) {
        // Validate fax privilege
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", "w", null)) {
            throw new SecurityException("missing required sec object (_fax)");
        }

        // Validate demographic number and access
        if (demographicNo != null) {
            if (demographicNo < 0) {
                throw new SecurityException("Invalid demographic number: must be non-negative");
            }
            // Verify user has access to this patient's record
            if (!securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, demographicNo)) {
                logger.warn("Unauthorized access attempt to demographic {} by provider {}",
                        LogSafe.sanitize(String.valueOf(demographicNo)), LogSafe.sanitize(loggedInInfo.getLoggedInProviderNo()));
                throw new SecurityException("Unauthorized access to patient record");
            }
        }

        // Validate fax file path to prevent path traversal attacks
        faxManager.validateFilePath(faxFilePath);

        // Validate recipient fax number format (required)
        if (recipientFaxNumber == null || recipientFaxNumber.trim().isEmpty()) {
            addActionError("Recipient fax number is required");
            throw new SecurityException("Recipient fax number is required");
        }
        faxManager.validateFaxNumber(recipientFaxNumber, "recipient fax number");

        // Validate sender fax number format (optional)
        faxManager.validateFaxNumber(senderFaxNumber, "sender fax number");

        // Sanitize recipient name to prevent injection attacks
        if (recipient != null && !recipient.trim().isEmpty()) {
            // Check for potential injection patterns
            if (recipient.contains("<script") || recipient.contains("javascript:") || recipient.contains("onerror=")) {
                // recipient failed the XSS screen, so it may carry markup/control chars — sanitize.
                logger.error("Potential XSS attempt in recipient name: {}", LogSafe.sanitize(recipient));
                throw new SecurityException("Invalid characters in recipient name");
            }
        }

        // Validate copyToRecipients array if present
        // Note: copyToRecipients contains JSON strings like: "name":"Test","fax":"1234567890"
        if (copyToRecipients != null && copyToRecipients.length > 0) {
            for (int i = 0; i < copyToRecipients.length; i++) {
                String copyRecipient = copyToRecipients[i];
                if (copyRecipient == null || copyRecipient.trim().isEmpty()) {
                    // Reject a blank entry before any file promotion or persistence begins.
                    addActionError("Copy-to recipient entry " + (i + 1) + " is empty");
                    throw new SecurityException("Copy-to recipient entry is blank at index " + i);
                }
                // Parse JSON to extract fax number for validation. Only the parse itself is
                // guarded: the deliberate rejections below must propagate with their own
                // honest messages instead of being caught here and re-labeled (and re-logged)
                // as a parse failure.
                String copyToFaxNumber;
                try {
                    String jsonString = "{" + copyRecipient + "}";
                    ObjectNode json = (ObjectNode) objectMapper.readTree(jsonString);
                    copyToFaxNumber = json.has("fax") ? json.get("fax").asText() : null;
                } catch (JsonProcessingException | ClassCastException e) {
                    logger.error("Failed to parse copy-to recipient JSON at index {}: {}", i, LogSafe.sanitize(copyRecipient), e);
                    addActionError("Copy-to recipient entry " + (i + 1) + " is not in a valid format");
                    throw new SecurityException("Invalid copy-to recipient format at index " + i);
                }
                if (copyToFaxNumber == null || copyToFaxNumber.trim().isEmpty()) {
                    // Require a fax number on every copy-to recipient (the format check only fires
                    // when one is present), so an empty/absent number is rejected up front rather than
                    // silently dropping that recipient at send time — same rule as the primary recipient.
                    addActionError("Copy-to recipient fax number is required");
                    throw new SecurityException("Copy-to recipient fax number is required at index " + i);
                }
                try {
                    faxManager.validateFaxNumber(copyToFaxNumber, "copy-to recipient fax number [" + i + "]");
                } catch (SecurityException e) {
                    addActionError("Copy-to recipient fax number is invalid at entry " + (i + 1));
                    throw e;
                }
            }
        }
    }

    /**
     * Set up fax parameters for this fax to be sent with the next timed
     * batch process.
     * This action assumes that the fax has already been produced and reviewed
     * by the user.
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @SuppressWarnings("unused")
    public String queue() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // Validate all inputs before processing
        try {
            validateFaxInputs(loggedInInfo);
        } catch (SecurityException e) {
            // securityError.jsp (the provider package's SecurityException mapping target) renders
            // the request attribute "actionErrors"; Struts action errors don't reach it on the
            // exception-mapping path without this bridge.
            if (!getActionErrors().isEmpty()) {
                request.setAttribute(ACTION_ERRORS_ATTRIBUTE, new ArrayList<>(getActionErrors()));
            }
            throw e;
        }

        TransactionType transactionType = TransactionType.valueOf(getTransactionType().toUpperCase());

        // prepareFax already revalidates the eForm's demographic binding immediately before it
        // hands the staged PDF off for preview, but queue() is a separate, later request that is
        // the actual promotion of that file into a sendable fax job -- the eForm can be reassigned
        // to a different patient in the gap between those two requests. Re-check the binding here
        // too, right before persistAndLogFaxJobs, instead of trusting the demographicNo the client
        // resubmitted with the cover-page form.
        revalidateEformBindingBeforePromotion(transactionType);

        // recipient/comments are persisted and rendered raw (encode-at-output, not
        // encode-at-write): pre-encoding here with Encode.forHtml produced literal HTML entities
        // on the faxed PDF cover page (PdfCoverPageCreator writes raw text into a PDF Phrase, not
        // HTML) and double-encoding on the Manage Faxes / CoverPage.jsp screens, which already
        // encode at render time via <carlos:encode>/${carlos:forHtml()}. The XSS screen in
        // validateFaxInputs (recipient <script/javascript:/onerror= check) still runs above.
        FaxJobParams params = FaxJobParams.builder()
                .faxFilePath(faxFilePath)
                .recipient(recipient)
                .recipientFaxNumber(recipientFaxNumber)
                .senderFaxNumber(senderFaxNumber)
                .demographicNo(demographicNo)
                .comments(comments)
                .coverpage(coverpage)
                .copyToRecipients(copyToRecipients)
                .build();

        // Persist AND audit-log in one transaction: a post-commit log failure must not leave a
        // sendable WAITING set behind that a retry would duplicate (double PHI transmission).
        List<FaxJob> faxJobList = faxManager.persistAndLogFaxJobs(loggedInInfo, params.toMap(), transactionType, transactionId);

        boolean success = true;
        for (FaxJob faxJob : faxJobList) {
            /*
             * only one error will derail the entire fax job.
             */
            if (STATUS.ERROR.equals(faxJob.getStatus())) {
                success = false;
            }
        }

        request.setAttribute("faxSuccessful", success);
        request.setAttribute("faxJobList", faxJobList);
        // Repopulate the sender-account list so a failed submit re-renders CoverPage.jsp with a
        // working sender dropdown (only prepareFax set it before; queue() left it empty on failure).
        request.setAttribute("accounts", faxManager.getFaxGatewayAccounts(loggedInInfo));

        return "preview";
    }

    /**
     * Re-checks that the eForm being faxed still belongs to the patient submitted with the
     * cover-page form, immediately before {@link #queue()} promotes its staged PDF into a
     * sendable fax job. {@code prepareFax()} performs the same check right before handing that
     * PDF off for preview, but {@code queue()} is a separate, later request, so the eForm can be
     * reassigned to a different patient in the gap between the two.
     *
     * @throws SecurityException if the eForm no longer belongs to the submitted demographic; the
     *         claimed staged PDF is deleted and a user-facing action error is recorded first
     */
    private void revalidateEformBindingBeforePromotion(TransactionType transactionType) {
        // Applies to EVERY type, before the per-type binding checks below. Those checks are
        // selected by transactionType, which the client supplies on this request — so branching
        // on it alone left CONSULTATION, FORM and RX with no check at all, while
        // FaxManagerImpl.validateFilePath still accepts any existing file under DOCUMENT_DIR.
        // Naming another patient's stored document under one of those types therefore faxed it.
        rejectUnstagedDocumentStorePath();

        if (transactionType == TransactionType.DOCUMENT) {
            revalidateDocumentClaimBeforePromotion();
            return;
        }
        if (transactionType != TransactionType.EFORM || transactionId == null) {
            return;
        }
        EFormData eFormAtPromotion = eFormDataDao().find(transactionId.intValue());
        String promotionDemographicNo = eFormAtPromotion == null || eFormAtPromotion.getDemographicId() == null
                ? null : String.valueOf(eFormAtPromotion.getDemographicId());
        // Consumed here regardless of outcome: THIS queue() call resolves this session's claim on
        // faxFilePath either way (accepted for promotion below, or rejected and deleted below), so
        // it must not linger in a long-lived clinician session accumulating one entry per fax
        // preview ever prepared.
        String claimedFaxFilePath = consumeClaimedFaxFilePathFromSession();
        if (promotionDemographicNo != null && demographicNo != null
                && promotionDemographicNo.equals(String.valueOf(demographicNo))) {
            return;
        }
        logger.warn("Rejected fax promotion: eForm {} no longer belongs to the demographic submitted with the fax job",
                transactionId);
        if (claimedFaxFilePath != null) {
            deleteRejectedClaimedFaxFile(claimedFaxFilePath);
        }
        // securityError.jsp renders the request attribute "actionErrors"; without bridging an
        // action error onto it here (as the validateFaxInputs catch in queue() already does for
        // its own SecurityExceptions), the clinician only sees the generic security error page
        // with no indication of why the fax was not sent.
        addActionError("The eForm no longer belongs to this patient");
        request.setAttribute(ACTION_ERRORS_ATTRIBUTE, new ArrayList<>(getActionErrors()));
        throw new SecurityException("The eForm no longer belongs to this patient");
    }

    /**
     * Deletes the claimed staged PDF for a rejected fax promotion.
     *
     * <p>Takes the path {@link #consumeClaimedFaxFilePathFromSession()} actually removed from the
     * session's trusted claim set -- not {@link #faxFilePath} directly -- even though the two are
     * verified equal by that point: a static analyzer cannot see that equality check as a
     * sanitizer, so deleting straight from the client-supplied request field here reads as an
     * unsanitized path-traversal sink again, the same class of finding the session-ownership check
     * exists to close off.</p>
     *
     * <p>The claimed PDF's ownership already transferred out of {@link EFormRenderApprovalService}
     * back in {@code prepareFax()} (its own bookkeeping no longer tracks or will ever clean up
     * this file), so a rejected promotion must delete it itself or it orphans on disk.</p>
     */
    private void deleteRejectedClaimedFaxFile(String claimedFaxFilePath) {
        try {
            deleteUnownedStagedFaxPreview(Path.of(claimedFaxFilePath));
        } catch (InvalidPathException e) {
            logger.warn("Unable to parse fax file path while cleaning up a rejected promotion", e);
        }
    }

    /**
     * Get a preview image of the entire fax document.
     */
    // FindSecBugs PATH_TRAVERSAL_IN: the request faxFilePath is confined to the CARLOS-owned temp workspace via PathValidationUtils.isInApplicationTempDirectory before any File use; a stored-document path is reachable only through its job binding.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "faxFilePath is containment-validated via PathValidationUtils.isInApplicationTempDirectory before any File use; stored documents are reachable only through their job binding")
    @SuppressWarnings("unused")
    public void getPreview() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", "r", null)) {
            sendErrorQuietly(HttpServletResponse.SC_FORBIDDEN, ACCESS_DENIED);
            return;
        }
        String requestedFaxFilePath = request.getParameter(FAX_FILE_PATH_PARAM);
        String pageNumber = request.getParameter("pageNumber");
        String showAs = request.getParameter("showAs");
        Path outfile = null;
        int page = 1;
        String jobId = request.getParameter("jobId");
        FaxJob faxJob = null;

        if (jobId != null && !jobId.isEmpty()) {
            try {
                faxJob = faxManager.getFaxJob(loggedInInfo, Integer.parseInt(jobId));
            } catch (NumberFormatException e) {
                logger.warn("Invalid jobId supplied for fax preview: {}", LogSafe.sanitize(jobId, 1024), e);
                sendErrorQuietly(HttpServletResponse.SC_BAD_REQUEST, "Invalid jobId");
                return;
            }
        }

        if (faxJob != null) {
            // A resolved job binds the file to a queued fax this user is permitted to see
            // (getFaxJob already gated on _fax read); when the job also carries a demographic
            // (set from the queue params by createFaxJob), enforce circle-of-care access the same
            // way validateFaxInputs does for the send path, so _fax-read alone cannot expose
            // another provider's patient's queued fax.
            if (faxJob.getDemographicNo() != null
                    && !securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, faxJob.getDemographicNo())) {
                logger.warn("Unauthorized access attempt to fax job preview by provider {}",
                        LogSafe.sanitize(loggedInInfo.getLoggedInProviderNo()));
                sendErrorQuietly(HttpServletResponse.SC_FORBIDDEN, ACCESS_DENIED);
                return;
            }
            requestedFaxFilePath = faxJob.getFile_name();
        }

        if (pageNumber != null && !pageNumber.isEmpty()) {
            try {
                page = Integer.parseInt(pageNumber);
            } catch (NumberFormatException e) {
                logger.warn("Invalid pageNumber supplied for fax preview: {}", LogSafe.sanitize(pageNumber, 1024), e);
                sendErrorQuietly(HttpServletResponse.SC_BAD_REQUEST, "Invalid pageNumber");
                return;
            }
        }

        /*
         * Displaying the entire PDF using the default browser's view before faxing an EForm (in CoverPage.jsp),
         * and when viewing it in the fax records (Manage Faxes), it is shown as images.
         */
        if (requestedFaxFilePath != null && !requestedFaxFilePath.isEmpty()) {
            // No jobId: the path came directly from the request parameter, not a resolved FaxJob.
            // CoverPage.jsp (the only direct-path caller) always supplies a freshly minted
            // carlos-temp artifact; Manage Faxes only ever supplies jobId. A stored document
            // (DOCUMENT_DIR) may therefore only be previewed through its job binding above — direct
            // paths outside the CARLOS-owned temp workspace are rejected before any use.
            boolean pathFromRequestParam = (faxJob == null);
            if (pathFromRequestParam && !PathValidationUtils.isInApplicationTempDirectory(new File(requestedFaxFilePath))) {
                logger.warn("Rejected fax preview for a non-temp path supplied directly as faxFilePath");
                sendErrorQuietly(HttpServletResponse.SC_FORBIDDEN, ACCESS_DENIED);
                return;
            }
            if (showAs != null && showAs.equals("image")) {
                // The faxManager.getFaxPreviewImage method already handles path validation.
                // Own the error response here: preview image generation goes through
                // NioFileManager.createCacheVersion2, which enforces an _edoc READ gate (getPreview only
                // gates _fax), and can throw on an invalid/inaccessible path. An uncaught throw would let
                // Struts write an HTML error page into this image/png stream (direct-response contract).
                try {
                    outfile = faxManager.getFaxPreviewImage(loggedInInfo, requestedFaxFilePath, page);
                } catch (SecurityException e) {
                    logger.error("Security validation failed for fax preview image: {}", LogSafe.sanitize(requestedFaxFilePath, 1024), e);
                    sendErrorQuietly(HttpServletResponse.SC_FORBIDDEN, ACCESS_DENIED);
                    return;
                } catch (RuntimeException e) {
                    logger.error("Error generating fax preview image: {}", LogSafe.sanitize(requestedFaxFilePath, 1024), e);
                    sendErrorQuietly(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to generate fax preview");
                    return;
                }
                if (outfile != null && outfile.getFileName() != null) {
                    response.setContentType("image/png");
                    // FilenameUtils.getName strips any path components (response-splitting /
                    // traversal defense); ContentDisposition owns the RFC 6266 header encoding
                    // (URL form encoding is not HTTP header encoding — it rendered spaces as
                    // literal %20 in the filename).
                    String sanitizedFilename = FilenameUtils.getName(outfile.getFileName().toString());
                    // Inline (not attachment): this PNG is the in-page fax preview rendered in an
                    // <img>, so browsers that honour Content-Disposition on embedded resources must
                    // render it rather than download it. The explicit "Open PDF" link uses the
                    // separate application/pdf branch below.
                    response.setHeader("Content-Disposition", ContentDisposition.inline()
                            .filename(sanitizedFilename, StandardCharsets.UTF_8).build().toString());
                }
            } else {
                // Validate and resolve the PDF path using FaxManager
                try {
                    outfile = faxManager.resolveAndValidateFilePath(requestedFaxFilePath);
                    response.setContentType("application/pdf");
                } catch (SecurityException e) {
                    logger.error("Security validation failed for file path: {}", LogSafe.sanitize(requestedFaxFilePath, 1024), e);
                    sendErrorQuietly(HttpServletResponse.SC_FORBIDDEN, ACCESS_DENIED);
                    return;
                } catch (IOException e) {
                    logger.error("File not found or error processing file path: {}", LogSafe.sanitize(requestedFaxFilePath, 1024), e);
                    sendErrorQuietly(HttpServletResponse.SC_NOT_FOUND, "File not found");
                    return;
                }
            }
        }

        if (outfile != null) {
            try (InputStream inputStream = Files.newInputStream(outfile);
                 BufferedInputStream bfis = new BufferedInputStream(inputStream);
                 ServletOutputStream outs = response.getOutputStream()) {

                bfis.transferTo(outs); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- binary fax document download
                outs.flush();
                logger.debug("Streamed fax preview to client");
            } catch (IOException e) {
                logger.error("Error reading or writing file", e);
                // The file vanished or broke mid-stream. If nothing has been committed yet, tell
                // the client instead of ending with an empty 200 it will render as a broken image.
                if (!response.isCommitted()) {
                    sendErrorQuietly(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to stream fax preview");
                }
            }
        } else {
            // No servable preview (e.g. the source PDF is gone — already warned server-side). An
            // empty 200 left the user staring at a broken image with no signal; a 404 lets the
            // preview page distinguish "not available" from "still loading".
            sendErrorQuietly(HttpServletResponse.SC_NOT_FOUND, "Preview not available");
        }
    }

    /**
     * Prepare a PDF of the given parameters an then return a path to
     * the for the user to review and add a cover page before sending final.
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @SuppressWarnings("unused")
    public String prepareFax() {
        long prepareStartedNanos = System.nanoTime();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.READ, null)) {
            sendErrorQuietly(HttpServletResponse.SC_FORBIDDEN, ACCESS_DENIED);
            return NONE;
        }

        /*
         * Fax recipient info carried forward.
         */
        TransactionType transactionType = TransactionType.valueOf(getTransactionType().toUpperCase());
        String actionForward = ERROR;
        Path pdfPath = null;
        List<FaxConfig> accounts = faxManager.getFaxGatewayAccounts(loggedInInfo);
        logger.debug("prepareFax start: transactionType={} transactionId={} accounts={}",
                transactionType, transactionId, accounts.size());

        /*
         * No fax accounts - No Fax.
         * This document is saved in a temporary directory as a PDF.
         */
        if (!accounts.isEmpty()) {
            if (transactionType.equals(TransactionType.EFORM)) {
                if (transactionId == null || demographicNo == null) {
                    sendErrorQuietly(HttpServletResponse.SC_BAD_REQUEST, "Invalid eForm fax request");
                    return NONE;
                }
                request.setAttribute("fdid", String.valueOf(transactionId));
                request.setAttribute("demographicId", String.valueOf(demographicNo));

                String approvalToken = request.getParameter("renderApproval");
                EFormData currentEForm = eFormDataDao().find(transactionId.intValue());
                if (currentEForm == null || currentEForm.getDemographicId() == null) {
                    renderApprovalService().cancelStagedFaxPreview(request, loggedInInfo, transactionId,
                            String.valueOf(demographicNo), approvalToken);
                    sendErrorQuietly(HttpServletResponse.SC_NOT_FOUND, "The eForm is no longer available");
                    return NONE;
                }
                String storedDemographicNo = String.valueOf(currentEForm.getDemographicId());
                if (!storedDemographicNo.equals(String.valueOf(demographicNo))) {
                    // The saved eForm moved to another patient after staging. Revoke the old tuple
                    // before returning so neither its token nor its PHI-bearing PDF survives.
                    renderApprovalService().cancelStagedFaxPreview(request, loggedInInfo, transactionId,
                            String.valueOf(demographicNo), approvalToken);
                    sendErrorQuietly(HttpServletResponse.SC_FORBIDDEN,
                            "The eForm no longer belongs to this patient");
                    return NONE;
                }
                if (!securityInfoManager.hasPrivilege(
                        loggedInInfo, "_eform", SecurityInfoManager.READ, storedDemographicNo)) {
                    renderApprovalService().cancelStagedFaxPreview(request, loggedInInfo, transactionId,
                            storedDemographicNo, approvalToken);
                    sendErrorQuietly(HttpServletResponse.SC_FORBIDDEN, ACCESS_DENIED);
                    return NONE;
                }
                EFormRenderApprovalService.StagedFaxPreview stagedPreview =
                        renderApprovalService().consumeStagedFaxPreview(request, loggedInInfo, transactionId,
                                storedDemographicNo, approvalToken);
                if (approvalToken != null && stagedPreview == null) {
                    sendErrorQuietly(HttpServletResponse.SC_FORBIDDEN,
                            "The incomplete-render approval is no longer available. Prepare the fax again.");
                    return NONE;
                }
                if (stagedPreview != null) {
                    // The stored eForm can be reassigned while the one-time preview is being
                    // claimed. Re-read its patient binding before the claimed PDF enters the fax
                    // pipeline; on a mismatch the preview is caller-owned and must be deleted.
                    EFormData claimedEForm = eFormDataDao().find(transactionId.intValue());
                    if (claimedEForm == null || claimedEForm.getDemographicId() == null
                            || !storedDemographicNo.equals(String.valueOf(claimedEForm.getDemographicId()))) {
                        deleteUnownedStagedFaxPreview(stagedPreview.path());
                        sendErrorQuietly(HttpServletResponse.SC_FORBIDDEN,
                                "The eForm changed while its fax preview was being approved.");
                        return NONE;
                    }
                }
                if (stagedPreview != null) {
                    pdfPath = stagedPreview.path();
                    recordClaimedFaxFilePathInSession(pdfPath);
                    request.setAttribute("advisoryIssues", stagedPreview.advisoryIssueCount());
                    logger.info("Fax staged eForm preview claimed: fdid={} prepareMs={}", transactionId,
                            (System.nanoTime() - prepareStartedNanos) / 1_000_000L);
                } else try {
                    EformDataManager.EformPdfRender rendered =
                            documentAttachmentManager.stageEFormPacketForFaxPreview(request, response,
                                    renderApprovalService().stagedFaxPreviewApproval(request, loggedInInfo,
                                            transactionId, storedDemographicNo));
                    if (rendered.completeness().hasBlockingOmissions()) {
                        String token;
                        boolean ownershipTransferred = false;
                        try {
                            token = renderApprovalService().issueStagedFaxPreview(request, loggedInInfo,
                                    transactionId, storedDemographicNo, rendered.formCompleteness(),
                                    rendered.completeness().advisoryIssueCount(), rendered.path());
                            ownershipTransferred = true;
                        } finally {
                            if (!ownershipTransferred) deleteUnownedStagedFaxPreview(rendered.path());
                        }
                        request.setAttribute("renderApproval", token);
                        request.setAttribute("missingContentMessage", EFORM_FAX_MISSING_CONTENT_MESSAGE);
                        request.setAttribute("transactionType", transactionType.name());
                        request.setAttribute("transactionId", transactionId);
                        request.setAttribute("demographicNo", demographicNo);
                        request.setAttribute("recipient", recipient);
                        request.setAttribute("recipientFaxNumber", recipientFaxNumber);
                        request.setAttribute("letterheadFax", letterheadFax);
                        request.setAttribute("failedContentResources", rendered.completeness().failedContentResources());
                        request.setAttribute("excludedContentElements", rendered.completeness().excludedContentElements());
                        request.setAttribute("signatureMissing", rendered.completeness().signatureMissing());
                        request.setAttribute("providerStampMissing", rendered.completeness().providerStampMissing());
                        request.setAttribute("timerCompatibilityFailure", rendered.completeness().timerCompatibilityFailure());
                        request.setAttribute("severeConsoleErrors", rendered.completeness().severeConsoleErrors());
                        request.setAttribute("severeConsoleErrorDetails", rendered.severeConsoleDetails());
                        request.setAttribute("containedInteractions", rendered.completeness().containedInteractions());
                        request.setAttribute("decorativeExcludedElements", rendered.completeness().decorativeExcludedElements());
                        request.setAttribute("stabilizationCapped", rendered.completeness().stabilizationCapped());
                        request.setAttribute("labDecisionSupportStubbed", rendered.completeness().labDecisionSupportStubbed());
                        logger.info("Fax eForm warning prepared: fdid={} prepareMs={} blockingIssues={}",
                                transactionId, (System.nanoTime() - prepareStartedNanos) / 1_000_000L,
                                rendered.completeness().blockingIssueCount());
                        return "eFormMissingContent";
                    }
                    pdfPath = rendered.path();
                    recordClaimedFaxFilePathInSession(pdfPath);
                    // Advisory conditions deliver the document rather than blocking it, so the fax
                    // preview must still say the render reported something. Count only: console and
                    // dialog text are form-authored and can carry PHI.
                    request.setAttribute("advisoryIssues", rendered.completeness().advisoryIssueCount());
                    if (logger.isDebugEnabled()) {
                        logger.debug("prepareFax renderEFormWithAttachments returned readable={} exists={}",
                                pdfPath != null && Files.isReadable(pdfPath),
                                pdfPath != null && Files.exists(pdfPath));
                    }
                } catch (PDFGenerationException e) {
                    logger.error(e.getMessage(), e);
                    String errorMessage = "This eForm (and attachments, if applicable) cannot be faxed. \\n\\n" + e.getMessage();
                    request.setAttribute("errorMessage", errorMessage);
                    return "eFormError";
                }
            } else if (transactionType.equals(TransactionType.DOCUMENT)) {
                if (transactionId == null) {
                    sendErrorQuietly(HttpServletResponse.SC_BAD_REQUEST, "Invalid document fax request");
                    return NONE;
                }
                try {
                    pdfPath = stageDocumentForFax(loggedInInfo, transactionId.intValue());
                } catch (SecurityException e) {
                    sendErrorQuietly(HttpServletResponse.SC_FORBIDDEN, ACCESS_DENIED);
                    return NONE;
                } catch (IllegalArgumentException e) {
                    sendErrorQuietly(HttpServletResponse.SC_NOT_FOUND, "The document is no longer available");
                    return NONE;
                } catch (IOException e) {
                    logger.error("Could not stage document {} for fax", transactionId, e);
                    sendErrorQuietly(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                            "The document could not be prepared for faxing.");
                    return NONE;
                }
                // Claim the staged copy for THIS session, exactly as the eForm path does, so
                // queue() can prove the faxFilePath it is handed is one this flow produced.
                recordClaimedFaxFilePathInSession(pdfPath);
            }
        } else {
            // No configured/active fax accounts: nothing can be sent. Fail with an honest HTTP status
            // and message rather than the "error" -> errorpage.jsp path, which renders
            // "CARLOS Error: 0" at HTTP 200 and drops the message attribute entirely.
            logger.warn("prepareFax found no active fax accounts; nothing can be sent");
            sendErrorQuietly(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "No active fax accounts are configured. Configure one under Administration > Faxes.");
            return NONE;
        }

        if (pdfPath != null) {
            List<Path> documents = new ArrayList<>();
            documents.add(pdfPath);
            request.setAttribute("accounts", accounts);
            request.setAttribute("demographicNo", demographicNo);
            request.setAttribute("documents", documents);
            request.setAttribute("transactionType", transactionType.name());
            request.setAttribute("transactionId", transactionId);
            request.setAttribute(FAX_FILE_PATH_PARAM, pdfPath);
            request.setAttribute("letterheadFax", letterheadFax);
            request.setAttribute("professionalSpecialistName", recipient);
            request.setAttribute("fax", recipientFaxNumber);
            actionForward = "preview";
        }

        if (ERROR.equals(actionForward)) {
            // Accounts exist but no document was produced (e.g. a transaction type this action does not
            // render). Fail honestly instead of falling through to the errorpage.jsp "CARLOS Error: 0".
            logger.warn("prepareFax produced no document for transactionType={} transactionId={}",
                    transactionType, transactionId);
            sendErrorQuietly(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "The fax document could not be prepared for this request.");
            return NONE;
        }

        logger.debug("prepareFax end: transactionId={} actionForward={} responseCommitted={}",
                transactionId, actionForward, response.isCommitted());
        return actionForward;
    }

    /**
     * Resolved on first use rather than in a field initialiser. Only the DOCUMENT fax path
     * needs it, and eagerly pulling the bean would force every test that constructs this
     * action to register a mock it has no interest in.
     */
    private NioFileManager nioFileManager() {
        if (nioFileManager == null) {
            nioFileManager = SpringUtils.getBean(NioFileManager.class);
        }
        return nioFileManager;
    }

    private EFormDataDao eFormDataDao() {
        if (eFormDataDao == null) {
            eFormDataDao = SpringUtils.getBean(EFormDataDao.class);
        }
        return eFormDataDao;
    }

    private EFormRenderApprovalService renderApprovalService() {
        if (renderApprovalService == null) {
            renderApprovalService = SpringUtils.getBean(EFormRenderApprovalService.class);
        }
        return renderApprovalService;
    }

    /**
     * Get the actual number of pages in this PDF document.
     */
    // FindSecBugs PATH_TRAVERSAL_IN: the request faxFilePath is confined to the CARLOS-owned temp workspace via PathValidationUtils.isInApplicationTempDirectory before any File use; a stored-document path is reachable only through its job binding.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "faxFilePath is containment-validated via PathValidationUtils.isInApplicationTempDirectory before any File use; stored documents are reachable only through their job binding")
    @SuppressWarnings("unused")
    public void getPageCount() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.READ, null)) {
            sendErrorQuietly(HttpServletResponse.SC_FORBIDDEN, ACCESS_DENIED);
            return;
        }
        String jobId = request.getParameter("jobId");
        int pageCount = resolvePageCount(loggedInInfo, jobId, request.getParameter(FAX_FILE_PATH_PARAM));
        if (response.isCommitted()) {
            return;
        }

        ObjectNode jsonObject = objectMapper.createObjectNode();
        jsonObject.put("jobId", jobId);
        jsonObject.put("pageCount", pageCount);

        JSONUtil.jsonResponse(response, jsonObject);
    }

    private int resolvePageCount(LoggedInInfo loggedInInfo, String jobId, String requestedFaxFilePath) {
        if (jobId != null && !jobId.isEmpty()) {
            try {
                return faxManager.getPageCount(loggedInInfo, Integer.parseInt(jobId));
            } catch (NumberFormatException e) {
                logger.warn("Invalid jobId supplied for fax page count: {}", LogSafe.sanitize(jobId, 1024), e);
                sendErrorQuietly(HttpServletResponse.SC_BAD_REQUEST, "Invalid jobId");
                return 0;
            }
        }
        if (requestedFaxFilePath == null || requestedFaxFilePath.isEmpty()) {
            return 0;
        }
        // No jobId: same direct-path exposure as getPreview. A stored document (DOCUMENT_DIR) may
        // only be paged through its job binding; direct paths are scoped to the CARLOS-owned temp
        // workspace before any use.
        if (!PathValidationUtils.isInApplicationTempDirectory(new File(requestedFaxFilePath))) {
            logger.warn("Rejected fax page count for a non-temp path supplied directly as faxFilePath");
            sendErrorQuietly(HttpServletResponse.SC_FORBIDDEN, ACCESS_DENIED);
            return 0;
        }
        try {
            Path resolvedPath = faxManager.resolveAndValidateFilePath(requestedFaxFilePath);
            try (PDDocument pdf = Loader.loadPDF(resolvedPath.toFile())) {
                return pdf.getNumberOfPages();
            }
        } catch (SecurityException e) {
            logger.error("Security validation failed for page count path: {}", LogSafe.sanitize(requestedFaxFilePath, 1024), e);
            sendErrorQuietly(HttpServletResponse.SC_FORBIDDEN, ACCESS_DENIED);
        } catch (IOException e) {
            logger.error("File not found or error processing page count path: {}", LogSafe.sanitize(requestedFaxFilePath, 1024), e);
            sendErrorQuietly(HttpServletResponse.SC_NOT_FOUND, "File not found");
        }
        return 0;
    }

    /**
     * Sends an HTTP error response, quietly logging (rather than propagating) any IO failure.
     * Protected so subclasses (e.g. the Manage Faxes admin action) reuse the same helper
     * instead of shadowing it.
     */
    protected void sendErrorQuietly(int statusCode, String message) {
        try {
            response.sendError(statusCode, message);
        } catch (IOException ex) {
            logger.error(ERROR_SENDING_ERROR_RESPONSE, ex);
        }
    }

    private static void deleteUnownedStagedFaxPreview(Path path) {
        if (path == null || !PathValidationUtils.isInApplicationTempDirectory(path.toFile())) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            logger.warn("Unable to delete staged fax preview after approval issuance failed: {}",
                    path, e);
        }
    }

    /**
     * Stages a stored document as an application-temp copy for the fax preview.
     *
     * <p>The copy, not the document-store file, is what enters the fax pipeline. That keeps
     * the permanent record out of reach of the promotion and cancel paths, and it gives
     * {@link #revalidateDocumentClaimBeforePromotion()} something session-scoped to claim.
     *
     * <p>The patient is derived from the document's own module link and checked against the
     * caller, never taken from the request. Before this branch existed, a DOCUMENT fax
     * submitted whatever {@code faxFilePath} the client sent with whatever
     * {@code demographicNo} the client sent, and nothing tied the two together.
     *
     * @throws SecurityException        if the caller may not see the document's patient
     * @throws IllegalArgumentException if the document or its file is missing
     */
    private Path stageDocumentForFax(LoggedInInfo loggedInInfo, int documentNo) throws IOException {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        EDoc doc = EDocUtil.getDoc(String.valueOf(documentNo));
        if (doc == null || StringUtils.isBlank(doc.getFileName())) {
            throw new IllegalArgumentException("Document not found");
        }

        // FaxDocument2Action refuses non-PDFs before redirecting here, but prepareFax is a
        // route in its own right: a caller can reach it directly with
        // transactionType=DOCUMENT and skip that gate. The pipeline can only send PDFs, so
        // the check has to live where the file actually enters it.
        if (!"application/pdf".equalsIgnoreCase(StringUtils.trimToEmpty(doc.getContentType()))) {
            throw new IllegalArgumentException("Only PDF documents can be faxed directly");
        }

        int documentDemographicNo = DocumentPatientLink.demographicNoOf(doc);
        if (documentDemographicNo > 0) {
            if (!securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, documentDemographicNo)) {
                throw new SecurityException("Unauthorized access to patient record");
            }
            // The cover-page form carries a demographic too; it must agree with the
            // document's own binding or the two identify different patients.
            if (demographicNo != null && demographicNo != documentDemographicNo) {
                throw new SecurityException("Document does not belong to the submitted patient");
            }
        }

        File documentDir = PathValidationUtils.resolveConfiguredDirectory(
                CarlosProperties.getInstance().getDocumentDirectory(), "DOCUMENT_DIR");
        File stored = PathValidationUtils.validateExistingPath(
                new File(documentDir, doc.getFileName()), documentDir);

        // Streamed file-to-file. Buffering the document first cost twice its size in heap per
        // concurrent preview, which repeated previews of large scans can exhaust.
        return nioFileManager().createTempFileFrom(doc.getFileName(), stored.toPath());
    }

    /**
     * Refuses to promote a file that lives in the document store rather than in this session's
     * staging area.
     *
     * <p>Every path {@code prepareFax} hands to the cover page is a temp copy it created:
     * {@code stageDocumentForFax} for DOCUMENT and the render/approval pipeline for EFORM. No
     * branch produces a {@code DOCUMENT_DIR} path, and the types that produce no path at all
     * (CONSULTATION, FORM, RX) cannot reach the cover page through this action. So a stored
     * document path arriving here was typed by the client, and the only thing it can accomplish
     * is faxing a file the pipeline never staged.
     *
     * <p>Deliberately not a claim check: the types above never record claims, and demanding one
     * would refuse them for the wrong reason. Containment is the invariant that actually holds.
     */
    private void rejectUnstagedDocumentStorePath() {
        String submitted = StringUtils.trimToNull(faxFilePath);
        if (submitted == null) {
            return;
        }
        java.io.File candidate;
        try {
            candidate = Path.of(submitted).toFile();
        } catch (InvalidPathException e) {
            throw new SecurityException("Invalid fax file path");
        }
        if (PathValidationUtils.isInApplicationTempDirectory(candidate)) {
            return;
        }
        logger.warn("Rejected fax promotion: the submitted file was not staged by this session");
        addActionError("This fax is no longer available to send. Open the item and try again.");
        request.setAttribute(ACTION_ERRORS_ATTRIBUTE, new ArrayList<>(getActionErrors()));
        throw new SecurityException("Fax file path outside the application staging area");
    }

    /**
     * Requires the {@code faxFilePath} submitted with a DOCUMENT fax to be one this session
     * staged in {@link #stageDocumentForFax}, AND the submitted {@code demographicNo} to still
     * match the document's own patient binding.
     *
     * <p>Without the path claim, {@code queue()} accepted any readable path inside the document
     * store, so a caller with fax rights could name another patient's document.
     *
     * <p>The path claim alone is not enough. {@code queue()} is a separate, later request than
     * {@code prepareFax}, and the cover-page form carries its own {@code demographicNo}: a
     * client could stage its own document legitimately and then submit the claimed path with a
     * different patient, filing the fax against the wrong chart. The document can also be
     * re-linked in the gap between the two requests. So the binding is re-derived from the
     * document here, exactly as {@link #revalidateEformBindingBeforePromotion} does for eForms,
     * rather than trusted from the form.
     */
    private void revalidateDocumentClaimBeforePromotion() {
        String claimed = consumeClaimedFaxFilePathFromSession();
        if (claimed != null && claimed.equals(faxFilePath)) {
            String rejection = documentPatientRebindingRejection();
            if (rejection == null) {
                return;
            }
            logger.warn("Rejected fax promotion: document {} no longer belongs to the demographic "
                    + "submitted with the fax job", transactionId);
            deleteRejectedClaimedFaxFile(claimed);
            addActionError(rejection);
            request.setAttribute(ACTION_ERRORS_ATTRIBUTE, new ArrayList<>(getActionErrors()));
            throw new SecurityException("The document no longer belongs to this patient");
        }
        logger.warn("Rejected fax promotion: document fax path was not staged by this session");
        if (claimed != null) {
            deleteRejectedClaimedFaxFile(claimed);
        }
        addActionError("This fax is no longer available to send. Open the document and try again.");
        request.setAttribute(ACTION_ERRORS_ATTRIBUTE, new ArrayList<>(getActionErrors()));
        throw new SecurityException("Unclaimed fax file path for document promotion");
    }

    /**
     * @return a user-facing reason the DOCUMENT promotion must be refused, or {@code null} when
     *         the submitted patient still agrees with the document's own binding. An unlinked
     *         document (module id absent, {@code 0} or {@code -1}) has no binding to contradict.
     */
    private String documentPatientRebindingRejection() {
        // A DOCUMENT promotion with no document number cannot be re-derived, so it must be
        // refused rather than waved through. prepareFax requires transactionId; queue() is a
        // separate request whose parameters the client re-supplies, so simply omitting the
        // hidden field would otherwise skip this check entirely and file the staged file
        // against whatever demographicNo the form carried.
        if (transactionId == null) {
            return "This fax is no longer available to send. Open the document and try again.";
        }
        EDoc doc = EDocUtil.getDoc(String.valueOf(transactionId.intValue()));
        // EDocUtil.getDoc never returns null: it allocates an EDoc and returns it whether or not
        // the query matched, so an unknown document number yields a default instance whose
        // moduleId is "". Testing for null let that instance fall through to the "unlinked
        // document, no binding to contradict" branch below — the same fail-open. Resolvability
        // is tested the way stageDocumentForFax tests it, on the filename.
        if (doc == null || StringUtils.isBlank(doc.getFileName())) {
            return "This document is no longer available to send.";
        }
        int documentDemographicNo = DocumentPatientLink.demographicNoOf(doc);
        if (documentDemographicNo == 0) {
            return null;
        }
        if (!securityInfoManager.isAllowedAccessToPatientRecord(
                LoggedInInfo.getLoggedInInfoFromSession(request), documentDemographicNo)) {
            return "You are not permitted to send this document.";
        }
        if (demographicNo != null && demographicNo != documentDemographicNo) {
            return "This document no longer belongs to this patient.";
        }
        return null;
    }

    /**
     * Records the staged file as claimable ONLY for the transaction it was staged from.
     *
     * <p>The claim used to be the bare path. That proved the file came from this session but said
     * nothing about which document or eForm it came from, and {@code queue()} re-reads
     * {@code transactionType}/{@code transactionId} from the client on a later request. A caller
     * could therefore stage their own patient's document legitimately and then promote that
     * genuine claimed path while naming a different {@code transactionId} and
     * {@code demographicNo}: the bytes faxed were the staged document, but the FaxJob, the chart
     * association and the FaxClientLog all recorded the substituted patient and document.
     *
     * <p>Binding the claim to {@code type|id|path} closes that: the promotion must name the same
     * transaction the file was staged for, or no claim matches.
     */
    // Package-private so a claim test can seed the session through the PRODUCTION recorder
    // rather than hand-building the stored form, which would make the test agree with itself
    // instead of with the code.
    void recordClaimedFaxFilePathInSession(Path claimedPath) {
        HttpSession session = request.getSession(false);
        if (session == null || claimedPath == null) {
            return;
        }
        // Every component is server-side: the transaction type and id were validated by the
        // staging branch that produced claimedPath, which is itself a renderer/staging temp path.
        claimedFaxFilePathsInSession(session).add(claimKey(transactionType, transactionId, claimedPath.toString())); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- every component is server-derived: the staging branch validated the transaction and generated the path
    }

    /**
     * Composite claim key. {@code transactionId} is nullable on legacy paths, so it is rendered as
     * an empty segment rather than omitted, keeping the arity fixed. Paths cannot contain a
     * newline on any supported filesystem, so the separator cannot be forged from the path.
     */
    // Package-private so the claim tests seed the session through the SAME key construction
    // the production path uses, rather than hand-building a string that could drift from it.
    static String claimKey(String type, Integer id, String path) {
        return StringUtils.upperCase(StringUtils.trimToEmpty(type)) + "\n"
                + (id == null ? "" : id.toString()) + "\n" + path;
    }

    /**
     * Removes and returns the entry matching {@link #faxFilePath} from this session's set of
     * outstanding claimed fax file paths, or {@code null} if none matches. Single-use per claim:
     * once consumed here, the same claim can never be consumed again, whether the promotion it
     * belongs to is accepted or rejected -- so it cannot linger in the session past the request
     * that resolves it, and a later, distinct claim for the same fdid is never confused with this
     * one.
     *
     * <p>Returns the value actually stored in the session's claim set -- populated only by
     * {@link #recordClaimedFaxFilePathInSession} from a server-generated renderer path, never from
     * {@link #faxFilePath} itself -- so callers that use the result downstream (e.g. to delete a
     * file) do so with a value a static analyzer can see originates from that trusted store, not
     * from the client-supplied request field, even though the two are verified equal here.</p>
     */
    private String consumeClaimedFaxFilePathFromSession() {
        HttpSession session = request.getSession(false);
        if (session == null || faxFilePath == null) {
            return null;
        }
        java.util.Set<String> claimedPaths = claimedFaxFilePathsInSession(session);
        // Matched on the composite key, so a claim staged for one transaction cannot be spent on
        // another even though the path is identical.
        String wanted = claimKey(transactionType, transactionId, faxFilePath);
        // Collections.synchronizedSet requires the caller to hold the set's own monitor while
        // iterating; per-call methods like remove() are internally synchronized, but this find-
        // and-remove needs the exact stored String back, which no Set method returns directly.
        synchronized (claimedPaths) {
            java.util.Iterator<String> iterator = claimedPaths.iterator();
            while (iterator.hasNext()) {
                String claimed = iterator.next();
                if (claimed.equals(wanted)) {
                    iterator.remove();
                    // Hand back the PATH component, which is what callers delete. It originates
                    // from the trusted store, not from the client-supplied request field.
                    return claimed.substring(claimed.lastIndexOf('\n') + 1);
                }
            }
        }
        return null;
    }

    // Guards the get-or-create of the session's claim-set attribute below. Deliberately a private
    // lock object per session, not the HttpSession itself: synchronizing on a method parameter (or
    // any object this class does not exclusively own) risks unpredictable contention or deadlock
    // with unrelated code that might also lock on the same shared session object. A single shared
    // lock object was tried first, but that made every session's fax preview/queue requests
    // contend on one process-wide monitor -- unrelated users blocking each other for no reason.
    // ConcurrentHashMap.computeIfAbsent is itself thread-safe (no external synchronization needed
    // to create an entry), and its per-key locking only ever contends for the SAME session id.
    // Cleared on session destruction (see OscarSessionListener) so this map does not grow
    // unboundedly over the life of the JVM.
    private static final java.util.concurrent.ConcurrentHashMap<String, Object> CLAIMED_FAX_FILE_PATHS_LOCKS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static Object claimedFaxFilePathsLockForSession(HttpSession session) {
        return CLAIMED_FAX_FILE_PATHS_LOCKS.computeIfAbsent(session.getId(), id -> new Object());
    }

    /**
     * Removes this session's dedicated claimed-fax-file-paths lock object. Invoked from
     * {@link io.github.carlos_emr.carlos.web.OscarSessionListener#sessionDestroyed} alongside its
     * other per-session cleanup (e.g. {@code EFormRenderApprovalService}'s own staged-preview
     * invalidation), so the per-session lock registry above does not accumulate one stray entry
     * per login for the life of the JVM.
     *
     * @param sessionId the destroyed session's id
     */
    public static void clearClaimedFaxFilePathsLockForSession(String sessionId) {
        CLAIMED_FAX_FILE_PATHS_LOCKS.remove(sessionId);
    }

    /** Test seam: reports whether a lock object is currently registered for the given session id. */
    public static boolean hasClaimedFaxFilePathsLockForTest(String sessionId) {
        return CLAIMED_FAX_FILE_PATHS_LOCKS.containsKey(sessionId);
    }

    /** Test seam: registers a lock object for the given session id, as a real request would. */
    public static void registerClaimedFaxFilePathsLockForTest(String sessionId) {
        CLAIMED_FAX_FILE_PATHS_LOCKS.computeIfAbsent(sessionId, id -> new Object());
    }

    @SuppressWarnings("unchecked")
    private static java.util.Set<String> claimedFaxFilePathsInSession(HttpSession session) {
        synchronized (claimedFaxFilePathsLockForSession(session)) {
            java.util.Set<String> claimedPaths =
                    (java.util.Set<String>) session.getAttribute(CLAIMED_FAX_FILE_PATHS_SESSION_KEY);
            if (claimedPaths == null) {
                claimedPaths = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
                session.setAttribute(CLAIMED_FAX_FILE_PATHS_SESSION_KEY, claimedPaths);
            }
            return claimedPaths;
        }
    }

    private String faxFilePath;
    private Integer transactionId;
    private Integer demographicNo;
    private String transactionType;
    private String recipient;
    private String recipientFaxNumber;
    private String letterheadFax;
    private String senderFaxNumber;
    private String comments;
    private String coverpage;
    private String[] copyToRecipients;

    public String getFaxFilePath() {
        return faxFilePath;
    }

    @StrutsParameter
    public void setFaxFilePath(String faxFilePath) {
        this.faxFilePath = faxFilePath;
    }

    public Integer getTransactionId() {
        return transactionId;
    }

    @StrutsParameter
    public void setTransactionId(Integer transactionId) {
        this.transactionId = transactionId;
    }

    public Integer getDemographicNo() {
        return demographicNo;
    }

    @StrutsParameter
    public void setDemographicNo(Integer demographicNo) {
        this.demographicNo = demographicNo;
    }

    public String getTransactionType() {
        return transactionType;
    }

    @StrutsParameter
    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public String getRecipient() {
        return recipient;
    }

    @StrutsParameter
    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getRecipientFaxNumber() {
        return recipientFaxNumber;
    }

    @StrutsParameter
    public void setRecipientFaxNumber(String recipientFaxNumber) {
        this.recipientFaxNumber = recipientFaxNumber;
    }

    public String getLetterheadFax() {
        return letterheadFax;
    }

    @StrutsParameter
    public void setLetterheadFax(String letterheadFax) {
        this.letterheadFax = letterheadFax;
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

    public String getCoverpage() {
        return coverpage;
    }

    @StrutsParameter
    public void setCoverpage(String coverpage) {
        this.coverpage = coverpage;
    }

    public String[] getCopyToRecipients() {
        return copyToRecipients;
    }

    @StrutsParameter
    public void setCopyToRecipients(String[] copyToRecipients) {
        this.copyToRecipients = copyToRecipients;
    }
}
