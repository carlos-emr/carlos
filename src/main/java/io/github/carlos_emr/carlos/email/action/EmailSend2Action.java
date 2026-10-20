package io.github.carlos_emr.carlos.email.action;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.AttachmentOwnershipService;
import io.github.carlos_emr.carlos.email.core.EmailAttachmentStaging;
import io.github.carlos_emr.carlos.email.core.EmailData;
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
 *   <li>Sending exactly the attachments its own compose window staged, and only when every one
 *       belongs to the email's patient (#4425)</li>
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
    private final transient AttachmentOwnershipService attachmentOwnershipService;

    /**
     * Shown when the send is refused because the window's attachments are gone (already sent,
     * cancelled, or dropped as the oldest of many open composers) or do not belong to the email's
     * patient (#4425). Nothing is sent and nothing is logged as sent.
     */
    static final String ATTACHMENTS_REFUSED_MESSAGE = "This email was not sent: this window has expired,"
            + " or its attachments do not belong to this patient. Close it and start the email again.";

    /** Struts-created: resolves the ownership check from Spring. */
    public EmailSend2Action() {
        this(SpringUtils.getBean(AttachmentOwnershipService.class));
    }

    /** Test constructor. */
    EmailSend2Action(AttachmentOwnershipService attachmentOwnershipService) {
        this.attachmentOwnershipService = attachmentOwnershipService;
    }

    /**
     * Main execution method that routes to specific email handling methods based on the "method" request parameter.
     *
     * <p>This method implements method-based routing for the following email workflows:</p>
     * <ul>
     *   <li><strong>sendDirectEmail</strong> - Sends email directly without EForm context</li>
     *   <li><strong>cancel</strong> - Cancels email operation and redirects to source</li>
     *   <li><strong>default</strong> - Sends email with EForm context (if no method parameter specified)</li>
     * </ul>
     *
     * @return String Struts2 result identifier - "success" for successful email operations,
     *         or transaction type name for cancel operations
     */
    public String execute () {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", "w", null)) {
            throw new SecurityException("missing required sec object (_email)");
        }

        if ("sendDirectEmail".equals(request.getParameter("method"))) {
            return sendDirectEmail();
        } else if ("cancel".equals(request.getParameter("method"))) {
            return cancel();
        }
        return sendEFormEmail();
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
        Optional<List<EmailAttachment>> attachments = takeVerifiedAttachments(request);
        if (attachments.isEmpty()) {
            return refuseSend();
        }
        EmailLog emailLog = sendEmail(request, attachments.get());

        boolean isEmailSuccessful = emailLog.getStatus() == EmailStatus.SUCCESS;
        request.setAttribute("isEmailSuccessful", isEmailSuccessful);
        if (isEmailSuccessful && deleteEFormAfterEmail) {
            eformDataManager.removeEFormData(loggedInInfo, request.getParameter("fdid"));
        }
        request.setAttribute("isOpenEForm", request.getParameter("openEFormAfterEmail"));
        request.setAttribute("fdid", request.getParameter("fdid"));
        request.setAttribute("emailLog", emailLog);
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
        Optional<List<EmailAttachment>> attachments = takeVerifiedAttachments(request);
        if (attachments.isEmpty()) {
            return refuseSend();
        }
        EmailLog emailLog = sendEmail(request, attachments.get());
        boolean isEmailSuccessful = emailLog.getStatus() == EmailStatus.SUCCESS;
        request.setAttribute("isEmailSuccessful", isEmailSuccessful);
        request.setAttribute("emailLog", emailLog);
        return SUCCESS;
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
        // Discard this window's staged attachments; nothing will send them now.
        EmailAttachmentStaging.take(request.getSession(), request.getParameter(EmailAttachmentStaging.KEY_PARAMETER));
        EmailData emailData = prepareEmailFields(request, List.of());
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
     * @param attachments the verified attachments this window staged, from {@link #takeVerifiedAttachments}
     * @return EmailLog entity containing the result of the email send operation including
     *         status (SUCCESS/FAILURE), timestamps, and any error messages
     */
    private EmailLog sendEmail(HttpServletRequest request, List<EmailAttachment> attachments) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        EmailData emailData = prepareEmailFields(request, attachments);
        return emailManager.sendEmail(loggedInInfo, emailData);
    }

    /**
     * Takes the attachments this compose window staged and verifies they may go to this patient
     * (#4425).
     *
     * <p>The window's {@value EmailAttachmentStaging#KEY_PARAMETER} takes its own entry, once, so a
     * compose or resend in another window can no longer change what this one sends. The send is
     * refused (returns an empty {@code Optional}) when:</p>
     * <ul>
     *   <li>nothing is staged under the key: already sent or cancelled, dropped, or forged;</li>
     *   <li>the email's {@code demographicId} is not the patient the attachments were prepared for;</li>
     *   <li>any eForm, document, lab or HRM attachment's record does not belong to that patient, as
     *       {@link AttachmentOwnershipService} reads it now.</li>
     * </ul>
     *
     * <p>Encounter-form ({@link DocumentType#FORM}) attachments have no common owner column to
     * check; they were rendered server-side for the bound patient, which the second rule enforces.
     * The entry is consumed even when refused, so a refused window cannot be retried into sending.</p>
     *
     * @param request the send request
     * @return a mutable copy of the verified attachments (an empty list when the window staged
     *         none), or an empty {@code Optional} to refuse the send
     */
    Optional<List<EmailAttachment>> takeVerifiedAttachments(HttpServletRequest request) {
        EmailAttachmentStaging.Prepared prepared = EmailAttachmentStaging.take(
                request.getSession(), request.getParameter(EmailAttachmentStaging.KEY_PARAMETER));
        if (prepared == null) {
            logger.warn("Email send refused: no attachments are staged for this compose window");
            return Optional.empty();
        }
        Integer demographicNo = parseDemographicNo(request.getParameter("demographicId"));
        if (demographicNo == null || demographicNo != prepared.demographicNo()) {
            logger.warn("Email send refused: the email's patient is not the patient its attachments were prepared for");
            return Optional.empty();
        }
        Map<DocumentType, List<Integer>> idsByType = new EnumMap<>(DocumentType.class);
        for (EmailAttachment attachment : prepared.attachments()) {
            DocumentType type = attachment.getDocumentType();
            if (type == null) {
                logger.warn("Email send refused: an attachment has no document type");
                return Optional.empty();
            }
            if (type != DocumentType.FORM) {
                idsByType.computeIfAbsent(type, t -> new ArrayList<>()).add(attachment.getDocumentId());
            }
        }
        if (!attachmentOwnershipService.allBelongToDemographic(idsByType, demographicNo)) {
            logger.warn("Email send refused: an attachment does not belong to the email's patient");
            return Optional.empty();
        }
        return Optional.of(new ArrayList<>(prepared.attachments()));
    }

    /** Reports a refused send on the compose page, which alerts and closes the window. */
    private String refuseSend() {
        request.setAttribute("isEmailError", true);
        request.setAttribute("emailErrorMessage", ATTACHMENTS_REFUSED_MESSAGE);
        return SUCCESS;
    }

    private static Integer parseDemographicNo(String demographicId) {
        if (demographicId == null) {
            return null;
        }
        try {
            return Integer.valueOf(demographicId.trim());
        } catch (NumberFormatException _) {
            return null;
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
     *   <li>Attaching the verified attachments this window staged (#4425)</li>
     * </ul>
     *
     * <p>The method supports PHI protection through encryption options and associates
     * emails with specific healthcare providers and patients for audit trail purposes.</p>
     *
     * @param request HttpServletRequest containing email form parameters and session data
     * @param emailAttachmentList the attachments to send; never read from the session here
     * @return EmailData populated data transfer object containing all email parameters
     *         ready for processing by EmailManager
     */
    private EmailData prepareEmailFields(HttpServletRequest request, List<EmailAttachment> emailAttachmentList) {
        String senderConfigId = request.getParameter("senderConfigId");
        String[] receiverEmails = request.getParameterValues("receiverEmailAddress");
        String subject = request.getParameter("subjectEmail");
        String body = request.getParameter("bodyEmail");
        String encryptedMessage = request.getParameter("encryptedMessage");
        String password = request.getParameter("emailPDFPassword");
        String passwordClue = request.getParameter("emailPDFPasswordClue");
        String isEncrypted = request.getParameter("isEmailEncrypted");
        String isAttachmentEncrypted = request.getParameter("isEmailAttachmentEncrypted");
        String chartDisplayOption = request.getParameter("patientChartOption");
        String internalComment = request.getParameter("internalComment");
        String transactionType = request.getParameter("transactionType");
        String demographicNo = request.getParameter("demographicId");
        String additionalParams = request.getParameter("additionalURLParams");

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
        emailData.setIsEncrypted(isEncrypted);
        emailData.setIsAttachmentEncrypted(isAttachmentEncrypted);
        emailData.setChartDisplayOption(chartDisplayOption);
        emailData.setInternalComment(internalComment);
        emailData.setTransactionType(transactionType);
        emailData.setDemographicNo(demographicNo);
        emailData.setProviderNo(providerNo);
        emailData.setAdditionalParams(additionalParams);
        emailData.setAttachments(emailAttachmentList);

        return emailData;
    }
}
