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
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.form.JSONUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import org.owasp.encoder.Encode;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

public class Fax2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = MiscUtils.getLogger();
    private final FaxManager faxManager = SpringUtils.getBean(FaxManager.class);
    private final DocumentAttachmentManager documentAttachmentManager = SpringUtils.getBean(DocumentAttachmentManager.class);
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private static final String FAX_PREVIEW_PATHS_SESSION_KEY = Fax2Action.class.getName() + ".PREVIEW_PATHS";
    private static final String FAX_FILE_TOKEN_PARAMETER = "faxFileToken";
    private static final String ACCESS_DENIED_MESSAGE = "Access denied";
    static final int MAX_PREVIEW_TOKENS_PER_SESSION = 20;


    public String execute() {
        String method = request.getParameter("method");
        if ("queue".equals(method)) {
            if (rejectNonPostMutationRequest()) {
                return NONE;
            }
            return queue();
        } else if ("prepareFax".equals(method)) {
            return prepareFax();
        } else if ("getPreview".equals(method)) {
            getPreview();
            return null;
        } else if ("getPageCount".equals(method)) {
            getPageCount();
            return null;
        }
        if (rejectNonPostMutationRequest()) {
            return NONE;
        }
        return cancel();
    }

    private boolean rejectNonPostMutationRequest() {
        if ("POST".equals(request.getMethod())) {
            return false;
        }
        try {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        } catch (IOException e) {
            logger.error("Error sending method-not-allowed response: {}", e.getClass().getSimpleName());
        }
        return true;
    }

    @SuppressWarnings("unused")
    public String cancel() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String faxForward = transactionType;

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", "w", null)) {
            throw new SecurityException("missing required sec object (_fax)");
        }
        String resolvedFaxFilePath = resolveSubmittedFaxFilePath(false);
        if (resolvedFaxFilePath != null && !resolvedFaxFilePath.trim().isEmpty()) {
            faxManager.validateFilePath(resolvedFaxFilePath);
        }
        faxManager.flush(loggedInInfo, resolvedFaxFilePath);
        removeSubmittedFaxFileToken();




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
    private void validateFaxInputs(LoggedInInfo loggedInInfo, String resolvedFaxFilePath) {
        validateDemographicAccess(loggedInInfo);
        faxManager.validateFilePath(resolvedFaxFilePath);
        validateFaxNumbers();
        validateRecipientName();
        validateCopyToRecipients();
    }

    private void validateDemographicAccess(LoggedInInfo loggedInInfo) {
        if (demographicNo != null) {
            if (demographicNo < 0) {
                throw new SecurityException("Invalid demographic number: must be non-negative");
            }
            if (!securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, demographicNo)) {
                logger.warn("Unauthorized access attempt to fax patient record");
                throw new SecurityException("Unauthorized access to patient record");
            }
        }
    }

    private void validateFaxNumbers() {
        if (recipientFaxNumber == null || recipientFaxNumber.trim().isEmpty()) {
            addActionError("Recipient fax number is required");
            throw new SecurityException("Recipient fax number is required");
        }
        faxManager.validateFaxNumber(recipientFaxNumber, "recipient fax number");
        faxManager.validateFaxNumber(senderFaxNumber, "sender fax number");
    }

    private void validateRecipientName() {
        if (recipient != null && !recipient.trim().isEmpty()) {
            if (recipient.contains("<script") || recipient.contains("javascript:") || recipient.contains("onerror=")) {
                logger.error("Potential XSS attempt in fax recipient name");
                throw new SecurityException("Invalid characters in recipient name");
            }
        }
    }

    private void validateCopyToRecipients() {
        if (copyToRecipients != null && copyToRecipients.length > 0) {
            for (int i = 0; i < copyToRecipients.length; i++) {
                String copyRecipient = copyToRecipients[i];
                if (copyRecipient != null && !copyRecipient.trim().isEmpty()) {
                    validateCopyToRecipientFaxNumber(copyRecipient, i);
                }
            }
        }
    }

    private void validateCopyToRecipientFaxNumber(String copyRecipient, int index) {
        try {
            String jsonString = "{" + copyRecipient + "}";
            ObjectNode json = (ObjectNode) objectMapper.readTree(jsonString);
            String faxNumber = json.has("fax") ? json.get("fax").asText() : null;
            if (faxNumber != null && !faxNumber.trim().isEmpty()) {
                faxManager.validateFaxNumber(faxNumber, "copy-to recipient fax number [" + index + "]");
            }
        } catch (Exception e) {
            logger.error("Failed to parse copy-to recipient JSON: {}", e.getClass().getSimpleName());
            throw new SecurityException("Invalid copy-to recipient format at index " + index);
        }
    }

    /**
     * Set up fax parameters for this fax to be sent with the next timed
     * batch process.
     * This action assumes that the fax has already been produced and reviewed
     * by the user.
     */
    @SuppressWarnings("unused")
    public String queue() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", "w", null)) {
            throw new SecurityException("missing required sec object (_fax)");
        }
        String resolvedFaxFilePath = resolveSubmittedFaxFilePath(true);
        faxFilePath = resolvedFaxFilePath;

        // Validate all inputs before processing
        validateFaxInputs(loggedInInfo, resolvedFaxFilePath);

        TransactionType transactionType = TransactionType.valueOf(getTransactionType().toUpperCase());

        // Sanitize text inputs to prevent injection attacks
        String sanitizedRecipient = recipient != null ? Encode.forHtml(recipient) : null;
        String sanitizedComments = comments != null ? Encode.forHtml(comments) : null;

        // Build fax job parameters using builder pattern
        FaxJobParams params = FaxJobParams.builder()
                .faxFilePath(faxFilePath)
                .recipient(sanitizedRecipient)
                .recipientFaxNumber(recipientFaxNumber)
                .senderFaxNumber(senderFaxNumber)
                .demographicNo(demographicNo)
                .comments(sanitizedComments)
                .coverpage(coverpage)
                .copyToRecipients(copyToRecipients)
                .build();

        List<FaxJob> faxJobList = faxManager.createAndSaveFaxJob(loggedInInfo, params.toMap());
        removeSubmittedFaxFileToken();

        boolean success = true;
        for (FaxJob faxJob : faxJobList) {
            faxManager.logFaxJob(loggedInInfo, faxJob, transactionType, transactionId);

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
            sendForbidden();
            return;
        }

        String previewFaxFilePath = resolvePreviewFaxFilePath(loggedInInfo);
        if (previewFaxFilePath == null) {
            return;
        }

        String showAs = request.getParameter("showAs");
        int page = parsePreviewPageNumber();
        Path outfile = resolvePreviewOutput(loggedInInfo, previewFaxFilePath, showAs, page);
        streamPreviewOutput(outfile);
    }

    private String resolvePreviewFaxFilePath(LoggedInInfo loggedInInfo) {
        String jobId = request.getParameter("jobId");
        Integer parsedJobId = parseOptionalInteger(jobId, "jobId");
        if (parsedJobId != null) {
            FaxJob faxJob = faxManager.getFaxJob(loggedInInfo, parsedJobId);
            if (faxJob != null) {
                return faxJob.getFile_name();
            }
        }
        try {
            return resolveSubmittedFaxFilePath(true);
        } catch (SecurityException e) {
            sendAccessDenied(e);
            return null;
        }
    }

    private int parsePreviewPageNumber() {
        Integer pageNumber = parseOptionalInteger(request.getParameter("pageNumber"), "pageNumber");
        return pageNumber != null ? pageNumber : 1;
    }

    private Integer parseOptionalInteger(String value, String parameterName) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid fax preview numeric request parameter: {}", parameterName);
            return null;
        }
    }

    private Path resolvePreviewOutput(LoggedInInfo loggedInInfo, String previewFaxFilePath, String showAs, int page) {
        if (previewFaxFilePath == null || previewFaxFilePath.isEmpty()) {
            return null;
        }
        if ("image".equals(showAs)) {
            return resolveImagePreviewOutput(loggedInInfo, previewFaxFilePath, page);
        }
        return resolvePdfPreviewOutput(previewFaxFilePath);
    }

    private Path resolveImagePreviewOutput(LoggedInInfo loggedInInfo, String previewFaxFilePath, int page) {
        Path outfile = faxManager.getFaxPreviewImage(loggedInInfo, previewFaxFilePath, page);
        if (outfile != null && outfile.getFileName() != null) {
            response.setContentType("image/png");
            String sanitizedFilename = outfile.getFileName().toString().replace('\0', '_');
            String encodedFilename = URLEncoder.encode(sanitizedFilename, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFilename + "\"");
        }
        return outfile;
    }

    private Path resolvePdfPreviewOutput(String previewFaxFilePath) {
        try {
            Path outfile = faxManager.resolveAndValidateFilePath(previewFaxFilePath);
            response.setContentType("application/pdf");
            return outfile;
        } catch (SecurityException e) {
            logger.error("Security validation failed for fax preview path: {}", e.getClass().getSimpleName());
            sendForbidden();
            return null;
        } catch (IOException e) {
            logger.error("File not found or error processing fax preview path: {}", e.getClass().getSimpleName());
            try {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found");
            } catch (IOException ex) {
                logger.error("Error sending error response: {}", ex.getClass().getSimpleName());
            }
            return null;
        }
    }

    private void streamPreviewOutput(Path outfile) {
        if (outfile != null) {
            try (InputStream inputStream = Files.newInputStream(outfile);
                 BufferedInputStream bfis = new BufferedInputStream(inputStream);
                 ServletOutputStream outs = response.getOutputStream()) {

                int data;
                while ((data = bfis.read()) != -1) {
                    outs.write(data); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- binary fax document download
                }
                outs.flush();
            } catch (IOException e) {
                logger.error("Error reading or writing file: {}", e.getClass().getSimpleName());
            }
        }
    }

    /**
     * Prepare a PDF of the given parameters an then return a path to
     * the for the user to review and add a cover page before sending final.
     */
    @SuppressWarnings("unused")
    public String prepareFax() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", "r", null)) {
            throw new SecurityException("missing required sec object (_fax)");
        }

        /*
         * Fax recipient info carried forward.
         */
        TransactionType transactionType = TransactionType.valueOf(getTransactionType().toUpperCase());
        String actionForward = ERROR;
        Path pdfPath = null;
        List<FaxConfig> accounts = faxManager.getFaxGatewayAccounts(loggedInInfo);

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
                } catch (PDFGenerationException e) {
                    logger.error("Failed to render eForm fax preview: {}", e.getClass().getSimpleName());
                    String errorMessage = "This eForm (and attachments, if applicable) cannot be faxed. \\n\\n" + e.getMessage();
                    request.setAttribute("errorMessage", errorMessage);
                    return "eFormError";
                }
            }
        } else {
            request.setAttribute("message", "No active fax accounts found.");
        }

        if (pdfPath != null) {
            request.setAttribute("accounts", accounts);
            request.setAttribute("demographicNo", demographicNo);
            request.setAttribute("transactionType", transactionType.name());
            request.setAttribute("transactionId", transactionId);
            request.setAttribute(FAX_FILE_TOKEN_PARAMETER, registerPreviewPath(pdfPath));
            request.setAttribute("letterheadFax", letterheadFax);
            request.setAttribute("professionalSpecialistName", recipient);
            request.setAttribute("fax", recipientFaxNumber);
            actionForward = "preview";
        }

        return actionForward;
    }

    /**
     * Get the actual number of pages in this PDF document.
     */
    @SuppressWarnings("unused")
    public void getPageCount() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String jobId = request.getParameter("jobId");
        Integer parsedJobId = parseOptionalInteger(jobId, "jobId");
        int pageCount = 0;

        if (parsedJobId != null) {
            pageCount = faxManager.getPageCount(loggedInInfo, parsedJobId);
        }

        ObjectNode jsonObject = objectMapper.createObjectNode();
        jsonObject.put("jobId", jobId);
        jsonObject.put("pageCount", pageCount);

        JSONUtil.jsonResponse(response, jsonObject);
    }

    private String registerPreviewPath(Path pdfPath) {
        String token = UUID.randomUUID().toString();
        getOrCreatePreviewPathStore().put(token, pdfPath.toString());
        return token;
    }

    private String resolveSubmittedFaxFilePath(boolean required) {
        String token;
        try {
            token = normalizeFaxFileToken(request.getParameter(FAX_FILE_TOKEN_PARAMETER));
        } catch (SecurityException e) {
            if (required) {
                throw e;
            }
            return null;
        }
        if (token == null || token.isBlank()) {
            if (required) {
                throw new SecurityException(ACCESS_DENIED_MESSAGE);
            }
            return null;
        }

        HttpSession session = request.getSession(false);
        PreviewPathStore previewPaths = getPreviewPathStore(session);
        String resolvedPath = previewPaths != null ? previewPaths.get(token) : null;
        if (resolvedPath == null || resolvedPath.isBlank()) {
            if (required) {
                throw new SecurityException(ACCESS_DENIED_MESSAGE);
            }
            return null;
        }
        return resolvedPath;
    }

    private void removeSubmittedFaxFileToken() {
        String token;
        try {
            token = normalizeFaxFileToken(request.getParameter(FAX_FILE_TOKEN_PARAMETER));
        } catch (SecurityException e) {
            return;
        }
        if (token == null || token.isBlank()) {
            return;
        }

        PreviewPathStore previewPaths = getPreviewPathStore(request.getSession(false));
        if (previewPaths != null) {
            previewPaths.remove(token);
        }
    }

    private PreviewPathStore getOrCreatePreviewPathStore() {
        HttpSession session = request.getSession();
        synchronized (session) {
            PreviewPathStore existingPreviewPaths = getPreviewPathStore(session);
            if (existingPreviewPaths != null) {
                return existingPreviewPaths;
            }

            PreviewPathStore previewPaths = new PreviewPathStore();
            session.setAttribute(FAX_PREVIEW_PATHS_SESSION_KEY, previewPaths);
            return previewPaths;
        }
    }

    private PreviewPathStore getPreviewPathStore(HttpSession session) {
        if (session == null) {
            return null;
        }

        Object value = session.getAttribute(FAX_PREVIEW_PATHS_SESSION_KEY);
        if (value instanceof PreviewPathStore previewPaths) {
            return previewPaths;
        }

        return null;
    }

    private String normalizeFaxFileToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(token);
            if (!uuid.toString().equals(token)) {
                throw new IllegalArgumentException("Non-canonical UUID token");
            }
            return token;
        } catch (IllegalArgumentException e) {
            throw new SecurityException(ACCESS_DENIED_MESSAGE);
        }
    }

    private void sendAccessDenied(Exception e) {
        logger.warn("Invalid fax preview token: {}", e.getClass().getSimpleName());
        sendForbidden();
    }

    private void sendForbidden() {
        try {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, ACCESS_DENIED_MESSAGE);
        } catch (IOException ex) {
            logger.error("Error sending forbidden response: {}", ex.getClass().getSimpleName());
        }
    }

    private static final class PreviewPathStore implements Serializable {
        private static final long serialVersionUID = 1L;
        private final LinkedHashMap<String, String> paths = new LinkedHashMap<>();

        private synchronized void put(String token, String path) {
            paths.put(token, path);
            trim();
        }

        private synchronized String get(String token) {
            return paths.get(token);
        }

        private synchronized void remove(String token) {
            paths.remove(token);
        }

        private void trim() {
            while (paths.size() > MAX_PREVIEW_TOKENS_PER_SESSION) {
                var tokens = paths.keySet().iterator();
                if (!tokens.hasNext()) {
                    return;
                }
                tokens.next();
                tokens.remove();
            }
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
