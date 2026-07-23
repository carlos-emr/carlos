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

import org.apache.struts2.ActionSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.commn.model.FaxJob.STATUS;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.fax.dto.FaxJobParams;
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
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class Fax2Action extends ActionSupport {

    private static final String ACCESS_DENIED = "Access denied";
    private static final String FAX_FILE_PATH_PARAM = "faxFilePath";
    private static final String ERROR_SENDING_ERROR_RESPONSE = "Error sending error response";
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = MiscUtils.getLogger();
    private final FaxManager faxManager = SpringUtils.getBean(FaxManager.class);
    private final DocumentAttachmentManager documentAttachmentManager = SpringUtils.getBean(DocumentAttachmentManager.class);
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);


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
     * (via {@link DocumentAttachmentManager#renderEFormWithAttachments}); it does not
     * persist a queued fax job or any permanent record.
     *
     * @return the Struts result name for the dispatched operation, or {@link #NONE}
     *         after a direct-response write or a 405 rejection
     */
    public String execute() {
        String method = request.getParameter("method");
        boolean readOnly = "getPreview".equals(method) || "getPageCount".equals(method) || "prepareFax".equals(method);
        String httpMethod = request.getMethod();
        if (!readOnly && ("GET".equalsIgnoreCase(httpMethod) || "HEAD".equalsIgnoreCase(httpMethod))) {
            // queue() persists fax jobs and promotes files; cancel() (also the no-method
            // fall-through below) deletes temp files and PHI preview caches -- mutations must
            // not ride a GET/HEAD. CoverPage.jsp submits both via <form method="post">, so no UI
            // change is required.
            sendErrorQuietly(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Method not allowed");
            return NONE;
        }
        if ("queue".equals(method)) {
            return queue();
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
            throw new SecurityException("User lacks required fax privileges");
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
                    // A blank entry used to skip validation entirely and then fail inside
                    // createAndSaveFaxJob — after the preview had already been destructively
                    // promoted out of temp storage, losing the user's only copy.
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
                    // An empty/absent fax number on a copy-to recipient used to slip past
                    // validation entirely (only the format was checked when present),
                    // silently dropping that recipient at send time. Reject it up front,
                    // same as the primary recipient fax number requirement above.
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
                request.setAttribute("actionErrors", new ArrayList<>(getActionErrors()));
            }
            throw e;
        }

        TransactionType transactionType = TransactionType.valueOf(getTransactionType().toUpperCase());

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

        List<FaxJob> faxJobList = faxManager.createAndSaveFaxJob(loggedInInfo, params.toMap());

        boolean success = true;
        for (FaxJob faxJob : faxJobList) {
            // ERROR jobs come back un-persisted (no id): there is no queued fax to correlate a
            // FaxClientLog row with, and logFaxJob would persist a null faxId.
            if (faxJob.getId() != null) {
                faxManager.logFaxJob(loggedInInfo, faxJob, transactionType, transactionId);
            }

            /*
             * only one error will derail the entire fax job.
             */
            if (STATUS.ERROR.equals(faxJob.getStatus())) {
                success = false;
            }
        }

        request.setAttribute("faxSuccessful", success);
        request.setAttribute("faxJobList", faxJobList);

        return "preview";
    }


    /**
     * Get a preview image of the entire fax document.
     */
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
                    String sanitizedFilename = FilenameUtils.getName(outfile.getFileName().toString());
                    // Encode filename to prevent HTTP response splitting by removing any control characters
                    String encodedFilename = URLEncoder.encode(sanitizedFilename, StandardCharsets.UTF_8)
                            .replaceAll("\\+", "%20"); // Replace + with %20 for spaces in filenames
                    // Inline (not attachment): this PNG is the in-page fax preview rendered in an
                    // <img>, so browsers that honour Content-Disposition on embedded resources must
                    // render it rather than download it. The explicit "Open PDF" link uses the
                    // separate application/pdf branch below.
                    response.setHeader("Content-Disposition", "inline; filename=\"" + encodedFilename + "\"");
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

                int data;
                while ((data = bfis.read()) != -1) {
                    outs.write(data); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- binary fax document download
                }
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
                request.setAttribute("fdid", String.valueOf(transactionId));
                request.setAttribute("demographicId", String.valueOf(demographicNo));

                try {
                    pdfPath = documentAttachmentManager.renderEFormWithAttachments(request, response);
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
            }
        } else {
            // No configured/active fax accounts: the preview screen shows a message but nothing gets
            // sent, so surface the misconfiguration for operators.
            logger.warn("prepareFax found no active fax accounts; nothing can be sent");
            request.setAttribute("message", "No active fax accounts found.");
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

        logger.debug("prepareFax end: transactionId={} actionForward={} responseCommitted={}",
                transactionId, actionForward, response.isCommitted());
        return actionForward;
    }

    /**
     * Get the actual number of pages in this PDF document.
     */
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
     */
    private void sendErrorQuietly(int statusCode, String message) {
        try {
            response.sendError(statusCode, message);
        } catch (IOException ex) {
            logger.error(ERROR_SENDING_ERROR_RESPONSE, ex);
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
