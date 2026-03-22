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
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FilenameUtils;

/**
 * Struts2 action for fax operations including queuing, previewing, and preparing outbound faxes.
 *
 * <p>This action dispatches requests based on the {@code method} parameter to handle the full
 * lifecycle of an outbound fax: prepare a PDF document, preview it, queue it for transmission,
 * and retrieve page counts. Security validation ensures the user has {@code _fax} write privilege
 * and authorized access to the patient record before processing.</p>
 *
 * <p>Struts parameter binding is handled via {@link StrutsParameter}-annotated setters for
 * fax metadata such as file path, recipient, demographic number, and transaction context.</p>
 *
 * @see io.github.carlos_emr.carlos.managers.FaxManager
 * @see FaxJobParams
 * @since 2026-03-17
 */
public class Fax2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = MiscUtils.getLogger();
    private final FaxManager faxManager = SpringUtils.getBean(FaxManager.class);
    private final DocumentAttachmentManager documentAttachmentManager = SpringUtils.getBean(DocumentAttachmentManager.class);
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);


    /**
     * Dispatches fax requests based on the {@code method} parameter.
     *
     * <p>Supported methods: {@code queue}, {@code prepareFax}, {@code getPreview},
     * {@code getPageCount}. Defaults to {@link #cancel()} if no method matches.</p>
     *
     * @return String Struts result name, or null for methods that write directly to the response
     */
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

    /**
     * Cancels the fax workflow, flushes temporary files, and redirects based on transaction type.
     *
     * <p>For {@code CONSULTATION} transactions, redirects to the consultation view.
     * For {@code EFORM} transactions, redirects to the eform data view.
     * Otherwise returns the transaction type string as the Struts result.</p>
     *
     * @return String Struts result name, or {@link #NONE} after redirect
     */
    @SuppressWarnings("unused")
    public String cancel() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String faxForward = transactionType;

        faxManager.flush(loggedInInfo, faxFilePath);




        if (TransactionType.CONSULTATION.name().equalsIgnoreCase(transactionType)) {
            try {
                response.sendRedirect(request.getContextPath() + "/oscarEncounter/ViewRequest.do?de=" + demographicNo + "&requestId=" + transactionId);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return NONE;
        } else if (TransactionType.EFORM.name().equalsIgnoreCase(transactionType)) {
            try {
                response.sendRedirect(request.getContextPath() + "/eform/efmshowform_data.jsp?fdid=" + transactionId + "&parentAjaxId=eforms");
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
        String faxFilePath = request.getParameter("faxFilePath");
        String pageNumber = request.getParameter("pageNumber");
        String showAs = request.getParameter("showAs");
        Path outfile = null;
        int page = 1;
        String jobId = request.getParameter("jobId");
        FaxJob faxJob = null;

        if (jobId != null && !jobId.isEmpty()) {
            faxJob = faxManager.getFaxJob(loggedInInfo, Integer.parseInt(jobId));
        }

        if (faxJob != null) {
            faxFilePath = faxJob.getFile_name();
        }

        if (pageNumber != null && !pageNumber.isEmpty()) {
            page = Integer.parseInt(pageNumber);
        }

        /*
         * Displaying the entire PDF using the default browser's view before faxing an EForm (in CoverPage.jsp),
         * and when viewing it in the fax records (Manage Faxes), it is shown as images.
         */
        if (faxFilePath != null && !faxFilePath.isEmpty()) {
            if (showAs != null && showAs.equals("image")) {
                // The faxManager.getFaxPreviewImage method already handles path validation
                outfile = faxManager.getFaxPreviewImage(loggedInInfo, faxFilePath, page);
                if (outfile != null && outfile.getFileName() != null) {
                    response.setContentType("image/png");
                    String sanitizedFilename = FilenameUtils.getName(outfile.getFileName().toString());
                    // Encode filename to prevent HTTP response splitting by removing any control characters
                    String encodedFilename = URLEncoder.encode(sanitizedFilename, StandardCharsets.UTF_8)
                            .replaceAll("\\+", "%20"); // Replace + with %20 for spaces in filenames
                    response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFilename + "\"");
                }
            } else {
                // Validate and resolve the PDF path using FaxManager
                try {
                    outfile = faxManager.resolveAndValidateFilePath(faxFilePath);
                    response.setContentType("application/pdf");
                } catch (SecurityException e) {
                    logger.error("Security validation failed for file path: " + faxFilePath, e);
                    try {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
                    } catch (IOException ex) {
                        logger.error("Error sending error response", ex);
                    }
                    return;
                } catch (IOException e) {
                    logger.error("File not found or error processing file path: " + faxFilePath, e);
                    try {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found");
                    } catch (IOException ex) {
                        logger.error("Error sending error response", ex);
                    }
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
                    outs.write(data);
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
            request.setAttribute("faxFilePath", pdfPath);
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

    /** Path to the temporary or permanent PDF file to be faxed */
    private String faxFilePath;
    /** Identifier for the source transaction (e.g., consultation or eform ID) */
    private Integer transactionId;
    /** Patient demographic number for access control and audit */
    private Integer demographicNo;
    /** Source transaction type (e.g., CONSULTATION, EFORM) */
    private String transactionType;
    /** Recipient name for the fax cover page */
    private String recipient;
    /** Destination fax number */
    private String recipientFaxNumber;
    /** Letterhead fax number for the sender */
    private String letterheadFax;
    /** Sender fax number displayed on the cover page */
    private String senderFaxNumber;
    /** Optional comments for the fax cover page */
    private String comments;
    /** Cover page template identifier */
    private String coverpage;
    /** JSON-encoded additional recipients for copy-to faxing */
    private String[] copyToRecipients;

    /**
     * Returns the fax file path.
     *
     * @return String path to the PDF document being faxed
     */
    public String getFaxFilePath() {
        return faxFilePath;
    }

    /**
     * Sets the fax file path from Struts parameter binding.
     *
     * @param faxFilePath String path to the PDF document
     */
    @StrutsParameter
    public void setFaxFilePath(String faxFilePath) {
        this.faxFilePath = faxFilePath;
    }

    /**
     * Returns the source transaction identifier.
     *
     * @return Integer the transaction ID (consultation ID, eform ID, etc.)
     */
    public Integer getTransactionId() {
        return transactionId;
    }

    /**
     * Sets the source transaction identifier from Struts parameter binding.
     *
     * @param transactionId Integer the transaction ID
     */
    @StrutsParameter
    public void setTransactionId(Integer transactionId) {
        this.transactionId = transactionId;
    }

    /**
     * Returns the patient demographic number.
     *
     * @return Integer the patient demographic number
     */
    public Integer getDemographicNo() {
        return demographicNo;
    }

    /**
     * Sets the patient demographic number from Struts parameter binding.
     *
     * @param demographicNo Integer the patient demographic number
     */
    @StrutsParameter
    public void setDemographicNo(Integer demographicNo) {
        this.demographicNo = demographicNo;
    }

    /**
     * Returns the source transaction type.
     *
     * @return String the transaction type name (e.g., CONSULTATION, EFORM)
     */
    public String getTransactionType() {
        return transactionType;
    }

    /**
     * Sets the source transaction type from Struts parameter binding.
     *
     * @param transactionType String the transaction type name
     */
    @StrutsParameter
    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    /**
     * Returns the fax recipient name.
     *
     * @return String the recipient name for the cover page
     */
    public String getRecipient() {
        return recipient;
    }

    /**
     * Sets the fax recipient name from Struts parameter binding.
     *
     * @param recipient String the recipient name
     */
    @StrutsParameter
    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    /**
     * Returns the destination fax number.
     *
     * @return String the recipient fax number
     */
    public String getRecipientFaxNumber() {
        return recipientFaxNumber;
    }

    /**
     * Sets the destination fax number from Struts parameter binding.
     *
     * @param recipientFaxNumber String the recipient fax number
     */
    @StrutsParameter
    public void setRecipientFaxNumber(String recipientFaxNumber) {
        this.recipientFaxNumber = recipientFaxNumber;
    }

    /**
     * Returns the letterhead fax number.
     *
     * @return String the letterhead fax number for the sender
     */
    public String getLetterheadFax() {
        return letterheadFax;
    }

    /**
     * Sets the letterhead fax number from Struts parameter binding.
     *
     * @param letterheadFax String the letterhead fax number
     */
    @StrutsParameter
    public void setLetterheadFax(String letterheadFax) {
        this.letterheadFax = letterheadFax;
    }

    /**
     * Returns the sender fax number.
     *
     * @return String the sender fax number
     */
    public String getSenderFaxNumber() {
        return senderFaxNumber;
    }

    /**
     * Sets the sender fax number from Struts parameter binding.
     *
     * @param senderFaxNumber String the sender fax number
     */
    @StrutsParameter
    public void setSenderFaxNumber(String senderFaxNumber) {
        this.senderFaxNumber = senderFaxNumber;
    }

    /**
     * Returns the cover page comments.
     *
     * @return String the comments text for the fax cover page
     */
    public String getComments() {
        return comments;
    }

    /**
     * Sets the cover page comments from Struts parameter binding.
     *
     * @param comments String the comments text
     */
    @StrutsParameter
    public void setComments(String comments) {
        this.comments = comments;
    }

    /**
     * Returns the cover page template identifier.
     *
     * @return String the cover page template name
     */
    public String getCoverpage() {
        return coverpage;
    }

    /**
     * Sets the cover page template from Struts parameter binding.
     *
     * @param coverpage String the cover page template identifier
     */
    @StrutsParameter
    public void setCoverpage(String coverpage) {
        this.coverpage = coverpage;
    }

    /**
     * Returns the array of copy-to recipients.
     *
     * @return String[] JSON-encoded additional recipients, or null if none
     */
    public String[] getCopyToRecipients() {
        return copyToRecipients;
    }

    /**
     * Sets the copy-to recipients from Struts parameter binding.
     *
     * @param copyToRecipients String[] JSON-encoded additional recipients
     */
    @StrutsParameter
    public void setCopyToRecipients(String[] copyToRecipients) {
        this.copyToRecipients = copyToRecipients;
    }
}
