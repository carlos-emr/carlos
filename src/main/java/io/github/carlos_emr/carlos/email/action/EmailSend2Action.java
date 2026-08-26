package io.github.carlos_emr.carlos.email.action;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailConsentStatus;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailSessionKeys;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Struts2 action controller for handling email sending functionality within the OpenO EMR system.
 *
 * <p>This action supports multiple email sending workflows including:</p>
 * <ul>
 *   <li>Sending emails directly with healthcare data and attachments</li>
 *   <li>Sending electronic forms (EForms) via email with optional deletion after send</li>
 *   <li>Handling email encryption and password protection for PHI compliance</li>
 *   <li>Managing email attachments from session storage</li>
 *   <li>Canceling email operations and redirecting to source contexts</li>
 * </ul>
 *
 * <p>The action integrates with the EmailManager service for core email functionality and
 * EformDataManager for electronic form handling. All email operations are logged via
 * EmailLog entities for audit trail and compliance purposes.</p>
 *
 * <p>This action follows the 2Action pattern for Struts2 migration, using method-based
 * routing via the "method" request parameter to handle different email workflows within
 * a single action class.</p>
 *
 * <p><strong>Security Considerations:</strong> This action handles Protected Health Information (PHI)
 * and supports encryption for both email bodies and attachments. All operations are performed
 * within the context of a logged-in provider using LoggedInInfo.</p>
 *
 * @since 2026-01-24
 * @see io.github.carlos_emr.carlos.managers.EmailManager
 * @see io.github.carlos_emr.carlos.managers.EformDataManager
 * @see io.github.carlos_emr.carlos.commn.model.EmailLog
 * @see io.github.carlos_emr.carlos.email.core.EmailData
 */
public class EmailSend2Action extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final Logger logger = MiscUtils.getLogger();
    private EmailManager emailManager = SpringUtils.getBean(EmailManager.class);
    private EformDataManager eformDataManager = SpringUtils.getBean(EformDataManager.class);

    private static final String PARAM_MESSAGE = "message";
    private static final String PARAM_IS_EMAIL_ENCRYPTED = "isEmailEncrypted";
    private static final String PARAM_IS_EMAIL_ATTACHMENT_ENCRYPTED = "isEmailAttachmentEncrypted";
    private static final int MINIMUM_PDF_PASSWORD_LENGTH = 5;
    private static final int MAXIMUM_MESSAGE_LENGTH = 10_000;
    private static final int MAXIMUM_CONSENT_OVERRIDE_REASON_LENGTH = 255;

    /**
     * Main execution method that routes to specific email handling methods based on the "method" request parameter.
     *
     * <p>This method implements method-based routing for the following email workflows:</p>
     * <ul>
     *   <li><strong>sendDirectEmail</strong> - Sends email directly without EForm context</li>
     *   <li><strong>sendEFormEmail</strong> - Sends email with EForm context</li>
     *   <li><strong>cancel</strong> - Cancels email operation and redirects to source</li>
     * </ul>
     * Missing or unsupported operations are rejected with HTTP 400 rather than defaulting to a
     * mutation.
     *
     * @return String Struts2 result identifier - "success" for successful email operations,
     *         a transaction type name for cancel operations, or "none" for rejected requests
     */
    @Override
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", "w", null)) {
            throw new SecurityException("missing required sec object (_email)");
        }

        String httpMethod = request.getMethod();
        if (!"POST".equals(httpMethod)) {
            response.setHeader("Allow", "POST");
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        try {
            String actionMethod = request.getParameter("method");
            if ("sendDirectEmail".equals(actionMethod)) {
                return sendDirectEmail();
            } else if ("sendEFormEmail".equals(actionMethod)) {
                return sendEFormEmail();
            } else if ("cancel".equals(actionMethod)) {
                return cancel();
            }
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        } catch (EmailSendValidationException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("text/plain;charset=UTF-8");
            try {
                response.getWriter().write(SafeEncode.forHtmlContent(e.getMessage()));
            } catch (IOException ioException) {
                logger.warn("Unable to write email validation response", ioException);
            }
            return NONE;
        }
    }

    /**
     * Sends an email with electronic form (EForm) context and optionally deletes the EForm after successful send.
     *
     * <p>This method handles the complete workflow for emailing EForms including:</p>
     * <ul>
     *   <li>Processing email send operation via EmailManager</li>
     *   <li>Optionally deleting the source EForm if send is successful and deletion is requested</li>
     *   <li>Setting request attributes for success status, EForm opening preference, and email log</li>
     * </ul>
     *
     * <p>The method checks the "deleteEFormAfterEmail" request parameter to determine if the
     * EForm should be removed after successful email delivery. This is useful for workflows
     * where the EForm is a temporary artifact used only for email generation.</p>
     *
     * @return String Struts2 SUCCESS result for rendering the email result page
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String sendEFormEmail() {
        boolean deleteEFormAfterEmail = request.getParameter("deleteEFormAfterEmail") != null && "true".equalsIgnoreCase(request.getParameter("deleteEFormAfterEmail"));

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        EmailLog emailLog = sendEmail(request);

        boolean isEmailSuccessful = emailLog.getStatus() == EmailStatus.SUCCESS;
        request.setAttribute("isEmailSuccessful", isEmailSuccessful);
        if (isEmailSuccessful && deleteEFormAfterEmail) {
            eformDataManager.removeEFormData(loggedInInfo, request.getParameter("fdid"));
        }
        request.setAttribute("isOpenEForm", request.getParameter("openEFormAfterEmail"));
        request.setAttribute("fdid", request.getParameter("fdid"));
        request.setAttribute("emailLog", emailLog);
        if (!isEmailSuccessful) {
            preserveComposeInputsForReRender(emailLog);
        }
        return SUCCESS;
    }

    /**
     * Sends an email directly without electronic form (EForm) context.
     *
     * <p>This method provides a simplified email sending workflow for scenarios where
     * the email is not associated with an EForm. It handles:</p>
     * <ul>
     *   <li>Processing the email send operation via EmailManager</li>
     *   <li>Setting request attributes for success status and email log</li>
     * </ul>
     *
     * <p>Unlike sendEFormEmail(), this method does not handle EForm deletion or opening
     * preferences, making it suitable for general-purpose email sending within the EMR.</p>
     *
     * @return String Struts2 SUCCESS result for rendering the email result page
     */
    public String sendDirectEmail() {
        EmailLog emailLog = sendEmail(request);
        boolean isEmailSuccessful = emailLog.getStatus() == EmailStatus.SUCCESS;
        request.setAttribute("isEmailSuccessful", isEmailSuccessful);
        request.setAttribute("emailLog", emailLog);
        if (!isEmailSuccessful) {
            preserveComposeInputsForReRender(emailLog);
        }
        return SUCCESS;
    }

    /**
     * Re-seeds the provider's submitted compose inputs into request scope so a failed-send re-render
     * of emailCompose.jsp preserves both the typed message AND the chosen encryption state. Without
     * this, the JSP re-initializes the encryption toggle from {@code isEmailEncrypted} (unset after a
     * send), so a blind retry of a failed encrypted send could silently go out as cleartext — a
     * PHI-safety regression (issue #3118).
     */
    private void preserveComposeInputsForReRender(EmailLog emailLog) {
        request.setAttribute(PARAM_MESSAGE, request.getParameter(PARAM_MESSAGE));
        // Fail closed on the message-encryption flag, matching prepareEmailFields: only an explicit
        // "false" re-renders the toggle OFF, so a failed encrypted draft can never reopen as cleartext.
        request.setAttribute(PARAM_IS_EMAIL_ENCRYPTED,
                isMessageEncryptionEnabled(request.getParameter(PARAM_IS_EMAIL_ENCRYPTED)));
        request.setAttribute(PARAM_IS_EMAIL_ATTACHMENT_ENCRYPTED,
                Boolean.TRUE.toString().equals(request.getParameter(PARAM_IS_EMAIL_ATTACHMENT_ENCRYPTED)));
        request.setAttribute("demographicId", request.getParameter("demographicId"));
        request.setAttribute("fdid", request.getParameter("fdid"));
        request.setAttribute("fid", request.getParameter("fid"));
        request.setAttribute("openEFormAfterEmail", request.getParameter("openEFormAfterEmail"));
        request.setAttribute("deleteEFormAfterEmail", request.getParameter("deleteEFormAfterEmail"));
        request.setAttribute("transactionType", request.getParameter("transactionType"));
        request.setAttribute("senderConfigId", request.getParameter("senderConfigId"));
        request.setAttribute("subjectEmail", request.getParameter("subjectEmail"));
        request.setAttribute("emailPDFPassword", request.getParameter("emailPDFPassword"));
        request.setAttribute("emailPDFPasswordClue", request.getParameter("emailPDFPasswordClue"));
        request.setAttribute("emailPatientChartOption", request.getParameter("patientChartOption"));
        request.setAttribute("internalComment", request.getParameter("internalComment"));
        request.setAttribute("emailAdditionalParams", request.getParameter("additionalURLParams"));
        EmailConsentStatus consentStatus = emailLog.getConsentStatus() != null
                ? emailLog.getConsentStatus()
                : parseConsentStatus(request.getParameter("emailConsentStatus"));
        request.setAttribute("emailConsentStatus", consentStatus.name());
        request.setAttribute("emailConsentMessageKey", consentStatus.getMessageKey());
        request.setAttribute("invalidReceiverEmailList", List.of());

        String[] recipients = request.getParameterValues("receiverEmailAddress");
        request.setAttribute("receiverEmailList",
                recipients == null ? List.of() : Arrays.asList(recipients));
        if (emailLog.getEmailConfig() == null) {
            request.setAttribute("senderAccounts", List.of());
        } else {
            request.setAttribute("senderAccounts", List.of(emailLog.getEmailConfig()));
            request.setAttribute("senderEmail", emailLog.getFromEmail());
        }
        if (emailLog.getDemographic() != null) {
            request.setAttribute("receiverName", emailLog.getDemographic().getFormattedName());
        }
    }

    private EmailConsentStatus parseConsentStatus(String statusCode) {
        try {
            return EmailConsentStatus.valueOf(statusCode);
        } catch (IllegalArgumentException | NullPointerException e) {
            return EmailConsentStatus.UNKNOWN;
        }
    }

    /**
     * Cancels the email operation and redirects the user back to the appropriate source context.
     *
     * <p>This method handles the cancel workflow by:</p>
     * <ul>
     *   <li>Preparing email fields from the request (to determine transaction type)</li>
     *   <li>Performing context-specific redirects based on the transaction type</li>
     *   <li>For EFORM transactions: redirects to the EForm display page with original form data</li>
     * </ul>
     *
     * <p>The method uses the transaction type from the email data to determine the
     * appropriate return destination, ensuring users are returned to their original
     * workflow context when canceling an email operation.</p>
     *
     * @return String Struts2 result identifier matching the transaction type name
     * @throws RuntimeException if IOException occurs during redirect for EFORM transactions
     */
    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    public String cancel() {
        EmailData emailData = new EmailData();
        emailData.setTransactionType(request.getParameter("transactionType"));
        if ("POST".equals(request.getMethod())) {
            request.getSession().removeAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST);
        }
        String emailRedirect = emailData.getTransactionType().name();
        if (emailData.getTransactionType().equals(EmailLog.TransactionType.EFORM)) {
            try {
                response.sendRedirect(request.getContextPath() + "/eform/efmshowform_data?fdid="
                        + SafeEncode.forUriComponent(request.getParameter("fdid")) + "&parentAjaxId=eforms");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return emailRedirect;
    }

    /**
     * Sends an email using the EmailManager service with data extracted from the HTTP request.
     *
     * <p>This private helper method coordinates the email sending process by:</p>
     * <ul>
     *   <li>Retrieving logged-in provider information from the session</li>
     *   <li>Preparing email data from request parameters via prepareEmailFields()</li>
     *   <li>Delegating to EmailManager for actual email transmission</li>
     * </ul>
     *
     * @param request HttpServletRequest containing email parameters and session data
     * @return EmailLog entity containing the result of the email send operation including
     *         status (SUCCESS/FAILURE), timestamps, and any error messages
     */
    private EmailLog sendEmail(HttpServletRequest request) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        validateMessageRequirement(request);
        validateEncryptionRequirements(request);
        validateConsentOverrideReason(request);
        EmailData emailData = prepareEmailFields(request);
        EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData);
        if (emailLog.getStatus() == EmailStatus.SUCCESS) {
            request.getSession().removeAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST);
        }
        return emailLog;
    }

    /**
     * Enforces the compose form's required message at the server boundary. Client-side validation
     * can be bypassed by a direct POST, and an empty encrypted message would otherwise send only a
     * notice claiming that a password-protected PDF is attached.
     *
     * <p>This check intentionally runs before {@link #prepareEmailFields(HttpServletRequest)},
     * because that method consumes the session-scoped attachment list. A rejected request must not
     * discard attachments that the provider may need to recover.</p>
     *
     * @param request request containing the submitted message
     * @throws EmailSendValidationException when the message is missing or blank
     */
    private void validateMessageRequirement(HttpServletRequest request) {
        String message = request.getParameter(PARAM_MESSAGE);
        if (message == null || message.isBlank()) {
            throw new EmailSendValidationException("Message is required");
        }
        if (message.length() > MAXIMUM_MESSAGE_LENGTH) {
            throw new EmailSendValidationException("Message must not exceed 10000 characters");
        }
    }

    /**
     * Enforces the compose form's encryption requirements at the server boundary. Client-side
     * validation is only a usability aid and can be bypassed by a direct POST; allowing an empty
     * PDF user password would produce a document that opens without a password prompt.
     *
     * <p>This check intentionally runs before {@link #prepareEmailFields(HttpServletRequest)},
     * because that method consumes the session-scoped attachment list. A rejected request must not
     * discard attachments that the provider may need to recover.</p>
     *
     * @param request request containing the submitted encryption fields
     * @throws EmailSendValidationException when encrypted delivery lacks a usable password or clue
     */
    private void validateEncryptionRequirements(HttpServletRequest request) {
        if (!isMessageEncryptionEnabled(request.getParameter(PARAM_IS_EMAIL_ENCRYPTED))) {
            return;
        }

        String password = request.getParameter("emailPDFPassword");
        if (password == null || password.trim().length() < MINIMUM_PDF_PASSWORD_LENGTH) {
            throw new EmailSendValidationException(
                    "A PDF password of at least 5 characters is required for encrypted email");
        }
        String passwordClue = request.getParameter("emailPDFPasswordClue");
        if (passwordClue == null || passwordClue.trim().isEmpty()) {
            throw new EmailSendValidationException(
                    "A PDF password clue is required for encrypted email");
        }
    }

    /**
     * Rejects an audit reason that cannot be persisted in full before the send path consumes any
     * compose state.
     *
     * @param request request containing the optional consent override reason
     * @throws EmailSendValidationException when the reason exceeds the database column limit
     */
    private void validateConsentOverrideReason(HttpServletRequest request) {
        String reason = request.getParameter("consentOverrideReason");
        if (reason != null && reason.trim().length() > MAXIMUM_CONSENT_OVERRIDE_REASON_LENGTH) {
            throw new EmailSendValidationException(
                    "Consent override reason must not exceed 255 characters");
        }
    }

    /** Validation failure translated to HTTP 400 by {@link #execute()}. */
    private static final class EmailSendValidationException extends IllegalArgumentException {
        private EmailSendValidationException(String message) {
            super(message);
        }
    }

    /**
     * Extracts and prepares email data from HTTP request parameters and session attributes.
     *
     * <p>This private helper method performs comprehensive email data preparation including:</p>
     * <ul>
     *   <li>Extracting sender and recipient email addresses</li>
     *   <li>Retrieving subject, body, and internal comment fields</li>
     *   <li>Processing encryption settings (email body and attachment encryption)</li>
     *   <li>Handling password protection parameters (password and password clue)</li>
     *   <li>Retrieving patient chart display options and demographic information</li>
     *   <li>Extracting transaction type and additional URL parameters</li>
     *   <li>Retrieving email attachments from session storage</li>
     *   <li>Cleaning up session by removing attachment list after extraction</li>
     * </ul>
     *
     * <p>The method supports PHI protection through encryption options and associates
     * emails with specific healthcare providers and patients for audit trail purposes.</p>
     *
     * @param request HttpServletRequest containing email form parameters and session data
     * @return EmailData populated data transfer object containing all email parameters
     *         ready for processing by EmailManager
     */
    private EmailData prepareEmailFields(HttpServletRequest request) {
        String senderConfigId = request.getParameter("senderConfigId");
        String[] receiverEmails = request.getParameterValues("receiverEmailAddress");
        String subject = request.getParameter("subjectEmail");
        String isEncrypted = request.getParameter(PARAM_IS_EMAIL_ENCRYPTED);

        // Single "Message" field routed server-side by the encryption toggle so the client can never
        // populate both the cleartext body and the encrypted-PDF channel at once (see issue #3118).
        // Encryption ON  -> the message becomes the password-protected PDF (encryptedMessage), and the
        //                   visible email body is a fixed, PHI-free notice.
        // Encryption OFF -> the message is sent as the cleartext MIME body; there is no encrypted PDF.
        String message = request.getParameter(PARAM_MESSAGE);
        // Defensive: a direct POST may omit the message param entirely. Coalesce to empty so the
        // cleartext body / encrypted-PDF content is never null downstream.
        if (message == null) {
            message = "";
        }
        // Fail closed: treat only an explicit "false" as encryption OFF. A direct or malformed POST
        // that omits or garbles the toggle defaults to ENCRYPTED, so PHI is never routed to the
        // cleartext body when intent is unclear (the compose flow defaults encryption on). See #3118.
        boolean encrypted = isMessageEncryptionEnabled(isEncrypted);
        String body = encrypted ? encryptedBodyNotice() : message;
        String encryptedMessage = encrypted ? message : "";

        String password = request.getParameter("emailPDFPassword");
        String passwordClue = request.getParameter("emailPDFPasswordClue");
        String isAttachmentEncrypted = request.getParameter(PARAM_IS_EMAIL_ATTACHMENT_ENCRYPTED);
        String chartDisplayOption = request.getParameter("patientChartOption");
        String internalComment = request.getParameter("internalComment");
        String transactionType = request.getParameter("transactionType");
        String demographicNo = request.getParameter("demographicId");
        String additionalParams = request.getParameter("additionalURLParams");
        String consentOverride = request.getParameter("consentOverride");
        String consentOverrideReason = request.getParameter("consentOverrideReason");
        List<EmailAttachment> emailAttachmentList = (List<EmailAttachment>) request.getSession()
                .getAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST);

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        EmailData emailData = new EmailData();
        emailData.setSenderConfigId(senderConfigId);
        emailData.setRecipients(receiverEmails);
        emailData.setSubject(subject);
        emailData.setBody(body);
        emailData.setEncryptedMessage(encryptedMessage);
        emailData.setPassword(password);
        emailData.setPasswordClue(passwordClue);
        emailData.setIsEncrypted(encrypted);
        emailData.setIsAttachmentEncrypted(isAttachmentEncrypted);
        emailData.setChartDisplayOption(chartDisplayOption);
        emailData.setInternalComment(internalComment);
        emailData.setTransactionType(transactionType);
        emailData.setDemographicNo(demographicNo);
        emailData.setProviderNo(providerNo);
        emailData.setAdditionalParams(additionalParams);
        emailData.setAttachments(copyAttachments(emailAttachmentList));
        emailData.setConsentOverride(consentOverride);
        emailData.setConsentOverrideReason(consentOverrideReason);

        return emailData;
    }

    /** Only an explicit false value opts out of message encryption. */
    private static boolean isMessageEncryptionEnabled(String value) {
        return !Boolean.FALSE.toString().equals(value);
    }

    /**
     * EmailManager encrypts attachments in place. Send detached copies so the original compose
     * attachments and preview capabilities remain usable when delivery fails and the form is
     * rendered for retry.
     */
    private static List<EmailAttachment> copyAttachments(List<EmailAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }

        List<EmailAttachment> copies = new ArrayList<>(attachments.size());
        for (EmailAttachment attachment : attachments) {
            EmailAttachment copy = new EmailAttachment(
                    attachment.getFileName(), attachment.getFilePath(),
                    attachment.getDocumentType(), attachment.getDocumentId(), attachment.getFileSize());
            copy.setPreviewToken(attachment.getPreviewToken());
            copies.add(copy);
        }
        return copies;
    }

    /**
     * Resolves the fixed, PHI-free notice used as the visible cleartext email body when the
     * message is delivered encrypted. The actual clinical content lives only inside the
     * password-protected PDF; the body must never carry patient health information.
     *
     * <p>Extracted as a protected method so it can be overridden in unit tests without a live
     * Struts container backing {@link #getText(String)}.</p>
     *
     * @return the localized secure-message notice for the encrypted email body
     */
    protected String encryptedBodyNotice() {
        return getText("email.compose.msg.encryptedBodyNotice");
    }
}
