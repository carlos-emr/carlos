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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.io.FilenameUtils;

public class Fax2Action extends ActionSupport {
    private static final String FAX_PREVIEW_PATHS_SESSION_KEY = Fax2Action.class.getName() + ".faxPreviewPaths";

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = MiscUtils.getLogger();
    private final FaxManager faxManager = SpringUtils.getBean(FaxManager.class);
    private final DocumentAttachmentManager documentAttachmentManager = SpringUtils.getBean(DocumentAttachmentManager.class);
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);


    public String execute() {
        String method = request.getParameter("method");
        if ("queue".equals(method)) {
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
        return cancel();
    }

    @SuppressWarnings("unused")
    public String cancel() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String faxForward = transactionType;

        flushPreviewToken(loggedInInfo);




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
                logger.warn("Unauthorized access attempt to demographic " + demographicNo + " by provider " + loggedInInfo.getLoggedInProviderNo());
                throw new SecurityException("Unauthorized access to patient record");
            }
        }

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
                logger.error("Potential XSS attempt in recipient name: " + recipient);
                throw new SecurityException("Invalid characters in recipient name");
            }
        }

        // Validate copyToRecipients array if present
        // Note: copyToRecipients contains JSON strings like: "name":"Test","fax":"1234567890"
        if (copyToRecipients != null && copyToRecipients.length > 0) {
            for (int i = 0; i < copyToRecipients.length; i++) {
                String copyRecipient = copyToRecipients[i];
                if (copyRecipient != null && !copyRecipient.trim().isEmpty()) {
                    // Parse JSON to extract fax number for validation
                    try {
                        String jsonString = "{" + copyRecipient + "}";
                        ObjectNode json = (ObjectNode) objectMapper.readTree(jsonString);
                        String faxNumber = json.has("fax") ? json.get("fax").asText() : null;
                        if (faxNumber != null && !faxNumber.trim().isEmpty()) {
                            faxManager.validateFaxNumber(faxNumber, "copy-to recipient fax number [" + i + "]");
                        }
                    } catch (Exception e) {
                        logger.error("Failed to parse copy-to recipient JSON at index " + i + ": " + copyRecipient, e);
                        throw new SecurityException("Invalid copy-to recipient format at index " + i);
                    }
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
    @SuppressWarnings("unused")
    public String queue() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // Validate all inputs before processing
        validateFaxInputs(loggedInInfo);
        Path faxDocument;
        try {
            faxDocument = resolveFaxPreviewPath(faxFileToken);
        } catch (SecurityException | IOException e) {
            logger.error("Invalid fax preview token while queueing fax", e);
            throw new SecurityException("Invalid fax preview token", e);
        }

        TransactionType transactionType = TransactionType.valueOf(getTransactionType().toUpperCase());

        // Sanitize text inputs to prevent injection attacks
        String sanitizedRecipient = recipient != null ? Encode.forHtml(recipient) : null;
        String sanitizedComments = comments != null ? Encode.forHtml(comments) : null;

        // Build fax job parameters using builder pattern
        FaxJobParams params = FaxJobParams.builder()
                .faxFilePath(faxDocument.toString())
                .recipient(sanitizedRecipient)
                .recipientFaxNumber(recipientFaxNumber)
                .senderFaxNumber(senderFaxNumber)
                .demographicNo(demographicNo)
                .comments(sanitizedComments)
                .coverpage(coverpage)
                .copyToRecipients(copyToRecipients)
                .build();

        List<FaxJob> faxJobList = faxManager.createAndSaveFaxJob(loggedInInfo, params.toMap());

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
            try {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
            } catch (IOException e) {
                logger.error("Error sending forbidden response in getPreview", e);
            }
            return;
        }
        String faxFileToken = request.getParameter("faxFileToken");
        String pageNumber = request.getParameter("pageNumber");
        String showAs = request.getParameter("showAs");
        Path outfile = null;
        Path faxDocument = null;
        int page = 1;
        String jobId = request.getParameter("jobId");
        FaxJob faxJob = null;

        if (jobId != null && !jobId.isEmpty()) {
            faxJob = faxManager.getFaxJob(loggedInInfo, Integer.parseInt(jobId));
        }

        if (faxJob != null) {
            try {
                faxDocument = faxManager.resolveAndValidateFilePath(faxJob.getFile_name());
            } catch (SecurityException | IOException e) {
                logger.error("Security validation failed for fax job file path", e);
                sendPreviewError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
                return;
            }
        } else if (faxFileToken != null && !faxFileToken.isEmpty()) {
            try {
                faxDocument = resolveFaxPreviewPath(faxFileToken);
            } catch (SecurityException | IOException e) {
                logger.error("Security validation failed for fax preview token", e);
                sendPreviewError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
                return;
            }
        }

        if (pageNumber != null && !pageNumber.isEmpty()) {
            page = Integer.parseInt(pageNumber);
        }

        /*
         * Displaying the entire PDF using the default browser's view before faxing an EForm (in CoverPage.jsp),
         * and when viewing it in the fax records (Manage Faxes), it is shown as images.
         */
        if (faxDocument != null) {
            if (showAs != null && showAs.equals("image")) {
                outfile = faxManager.getFaxPreviewImage(loggedInInfo, faxDocument, page);
                if (outfile != null && outfile.getFileName() != null) {
                    response.setContentType("image/png");
                    String sanitizedFilename = FilenameUtils.getName(outfile.getFileName().toString());
                    // Encode filename to prevent HTTP response splitting by removing any control characters
                    String encodedFilename = URLEncoder.encode(sanitizedFilename, StandardCharsets.UTF_8)
                            .replaceAll("\\+", "%20"); // Replace + with %20 for spaces in filenames
                    response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFilename + "\"");
                }
            } else {
                outfile = faxDocument;
                response.setContentType("application/pdf");
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
            } catch (IOException e) {
                logger.error("Error reading or writing file", e);
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
                    logger.error(e.getMessage(), e);
                    String errorMessage = "This eForm (and attachments, if applicable) cannot be faxed. \\n\\n" + e.getMessage();
                    request.setAttribute("errorMessage", errorMessage);
                    return "eFormError";
                }
            }
        } else {
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
            request.setAttribute("faxFileToken", registerFaxPreviewPath(pdfPath));
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
        int pageCount = 0;

        if (jobId != null && !jobId.isEmpty()) {
            pageCount = faxManager.getPageCount(loggedInInfo, Integer.parseInt(jobId));
        }

        ObjectNode jsonObject = objectMapper.createObjectNode();
        jsonObject.put("jobId", jobId);
        jsonObject.put("pageCount", pageCount);

        JSONUtil.jsonResponse(response, jsonObject);
    }

    private String registerFaxPreviewPath(Path pdfPath) {
        String token = UUID.randomUUID().toString();
        faxPreviewPaths(true).put(token, pdfPath.toString());
        return token;
    }

    private Path resolveFaxPreviewPath(String token) throws IOException {
        if (token == null || token.trim().isEmpty()) {
            throw new SecurityException("Missing fax preview token");
        }
        try {
            UUID.fromString(token);
        } catch (IllegalArgumentException e) {
            throw new SecurityException("Invalid fax preview token", e);
        }

        Map<String, String> previewPaths = faxPreviewPaths(false);
        String previewPath = previewPaths == null ? null : previewPaths.get(token);
        if (previewPath == null) {
            throw new SecurityException("Unknown fax preview token");
        }
        return faxManager.resolveAndValidateFilePath(previewPath);
    }

    private void flushPreviewToken(LoggedInInfo loggedInInfo) {
        if (faxFileToken == null || faxFileToken.trim().isEmpty()) {
            return;
        }

        try {
            Path previewPath = resolveFaxPreviewPath(faxFileToken);
            faxManager.flush(loggedInInfo, previewPath.toString());
            removeFaxPreviewToken(faxFileToken);
        } catch (SecurityException | IOException e) {
            logger.warn("Skipping fax preview cleanup for invalid or expired token", e);
        }
    }

    private void removeFaxPreviewToken(String token) {
        Map<String, String> previewPaths = faxPreviewPaths(false);
        if (previewPaths != null) {
            previewPaths.remove(token);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> faxPreviewPaths(boolean create) {
        HttpSession session = request.getSession(create);
        if (session == null) {
            return null;
        }

        Object existing = session.getAttribute(FAX_PREVIEW_PATHS_SESSION_KEY);
        if (existing instanceof Map<?, ?> map) {
            return (Map<String, String>) map;
        }
        if (!create) {
            return null;
        }

        Map<String, String> previewPaths = new HashMap<>();
        session.setAttribute(FAX_PREVIEW_PATHS_SESSION_KEY, previewPaths);
        return previewPaths;
    }

    private void sendPreviewError(int statusCode, String message) {
        try {
            response.sendError(statusCode, message);
        } catch (IOException e) {
            logger.error("Error sending preview error response", e);
        }
    }

    private String faxFileToken;
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
        return null;
    }

    @StrutsParameter
    public void setFaxFilePath(String faxFilePath) {
        // Raw preview paths are deliberately ignored. Fax previews are resolved from faxFileToken.
    }

    public String getFaxFileToken() {
        return faxFileToken;
    }

    @StrutsParameter
    public void setFaxFileToken(String faxFileToken) {
        this.faxFileToken = faxFileToken;
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
