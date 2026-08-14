package io.github.carlos_emr.carlos.email.action;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService.EmailComposeSubmissionContext;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService.EmailComposeSubmissionState;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.StringUtils;
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
    private transient EmailComposeSubmissionStateService emailComposeSubmissionStateService =
            SpringUtils.getBean(EmailComposeSubmissionStateService.class);

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
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        EmailComposeSubmissionContext context = null;
        EmailLog emailLog;
        try {
            int senderConfigId = validateSubmittedEmailFields(request);
            validateActiveSenderConfig(senderConfigId);
            try (EmailComposeSubmissionState composeState = resolveEmailComposeSubmissionState(request)) {
                context = composeState.context();
                ensureTransactionType(composeState, EmailLog.TransactionType.EFORM);
                emailLog = sendEmail(request, composeState);
            }
        } catch (EmailComposeStateException e) {
            return handleEmailComposeStateError(e, context);
        }

        boolean isEmailSuccessful = emailLog.getStatus() == EmailStatus.SUCCESS;
        request.setAttribute("isEmailSuccessful", isEmailSuccessful);
        if (isEmailSuccessful && context.deleteEFormAfterEmail() && StringUtils.filled(context.fdid())) {
            eformDataManager.removeEFormData(loggedInInfo, context.fdid());
        }
        request.setAttribute("isOpenEForm", context.openEFormAfterEmail());
        request.setAttribute("fdid", context.fdid());
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
        EmailComposeSubmissionContext context = null;
        EmailLog emailLog;
        try {
            int senderConfigId = validateSubmittedEmailFields(request);
            validateActiveSenderConfig(senderConfigId);
            try (EmailComposeSubmissionState composeState = resolveEmailComposeSubmissionState(request)) {
                context = composeState.context();
                ensureTransactionType(composeState, EmailLog.TransactionType.DIRECT);
                emailLog = sendEmail(request, composeState);
            }
        } catch (EmailComposeStateException e) {
            return handleEmailComposeStateError(e, context);
        }
        boolean isEmailSuccessful = emailLog.getStatus() == EmailStatus.SUCCESS;
        request.setAttribute("isEmailSuccessful", isEmailSuccessful);
        request.setAttribute("emailLog", emailLog);
        return SUCCESS;
    }

    private String handleEmailComposeStateError(
            EmailComposeStateException e,
            EmailComposeSubmissionContext trustedContext
    ) {
        logger.warn(e.getMessage());
        request.setAttribute("isEmailError", true);
        request.setAttribute("isEmailComposeStateError", true);
        request.setAttribute("emailErrorMessage", e.getMessage());
        preserveSubmittedComposeFields(request, trustedContext);
        String cancelToken = request.getParameter(
                EmailComposeSubmissionStateService.EMAIL_PDF_PASSWORD_TOKEN_PARAM);
        if (trustedContext != null) {
            try {
                cancelToken = emailComposeSubmissionStateService.storeCancelContext(request, trustedContext);
            } catch (RuntimeException tokenFailure) {
                logger.warn("Unable to preserve trusted email cancel context");
                cancelToken = "";
            }
        }
        request.setAttribute(
                EmailComposeSubmissionStateService.EMAIL_PDF_PASSWORD_TOKEN_PARAM,
                cancelToken);
        return SUCCESS;
    }

    private void preserveSubmittedComposeFields(
            HttpServletRequest request,
            EmailComposeSubmissionContext trustedContext
    ) {
        String[] receiverEmails = request.getParameterValues("receiverEmailAddress");
        request.setAttribute(
                "transactionType",
                trustedContext == null
                        ? EmailData.parseTransactionType(request.getParameter("transactionType"))
                        : trustedContext.transactionType());
        request.setAttribute("receiverEmailList", receiverEmails == null ? List.of() : Arrays.asList(receiverEmails));
        request.setAttribute("invalidReceiverEmailList", List.of());
        request.setAttribute("senderAccounts", List.of());
        request.setAttribute("senderConfigId", request.getParameter("senderConfigId"));
        request.setAttribute("subjectEmail", request.getParameter("subjectEmail"));
        request.setAttribute("bodyEmail", request.getParameter("bodyEmail"));
        request.setAttribute("encryptedMessageEmail", request.getParameter("encryptedMessage"));
        request.setAttribute("emailPatientChartOption", request.getParameter("patientChartOption"));
        request.setAttribute(
                "demographicId",
                trustedContext == null
                        ? integerParameterOrEmpty(request, "demographicId")
                        : trustedContext.demographicId());
        request.setAttribute(
                "fdid",
                trustedContext == null ? integerParameterOrEmpty(request, "fdid") : trustedContext.fdid());
        request.setAttribute(
                "openEFormAfterEmail",
                trustedContext == null
                        ? isTrueParameter(request, "openEFormAfterEmail")
                        : trustedContext.openEFormAfterEmail());
        request.setAttribute(
                "deleteEFormAfterEmail",
                trustedContext == null
                        ? isTrueParameter(request, "deleteEFormAfterEmail")
                        : trustedContext.deleteEFormAfterEmail());
        request.setAttribute("isEmailEncrypted", isTrueParameter(request, "isEmailEncrypted"));
        request.setAttribute("isEmailAttachmentEncrypted", isTrueParameter(request, "isEmailAttachmentEncrypted"));
        request.setAttribute("emailPDFPassword", "");
        request.setAttribute("emailPDFPasswordClue", "");
        request.setAttribute("emailAttachmentList", List.of());
    }

    private static String integerParameterOrEmpty(HttpServletRequest request, String parameterName) {
        String value = request.getParameter(parameterName);
        return StringUtils.isInteger(value) ? value : "";
    }

    private static boolean isTrueParameter(HttpServletRequest request, String parameterName) {
        return "true".equals(request.getParameter(parameterName));
    }

    /**
     * Cancels the email operation and redirects the user back to the appropriate source context.
     *
     * <p>This method handles the cancel workflow by:</p>
     * <ul>
     *   <li>Reading the token-bound transaction context to determine the return destination</li>
     *   <li>Consuming any submitted compose token so prepared state is not left pending</li>
     *   <li>Performing context-specific redirects based on the transaction type</li>
     *   <li>For EFORM transactions: redirects to the EForm display page with original form data</li>
     * </ul>
     *
     * <p>The method prefers token-bound context and falls back to submitted routing fields
     * only when no compose token state remains.</p>
     *
     * @return String Struts2 result identifier matching the transaction type name
     * @throws RuntimeException if IOException occurs during redirect for EFORM transactions
     */
    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    public String cancel() {
        EmailComposeSubmissionState composeState = emailComposeSubmissionStateService.consume(request);
        EmailComposeSubmissionContext trustedContext = composeState == null ? null : composeState.context();
        EmailLog.TransactionType transactionType = trustedContext == null
                ? EmailData.parseTransactionType(request.getParameter("transactionType"))
                : trustedContext.transactionType();
        String fdid = trustedContext == null ? request.getParameter("fdid") : trustedContext.fdid();
        if (composeState != null) {
            composeState.close();
        }
        String emailRedirect = transactionType.name();
        if (transactionType.equals(EmailLog.TransactionType.EFORM)) {
            try {
                response.sendRedirect(request.getContextPath() + "/eform/efmshowform_data?fdid="
                        + SafeEncode.forUriComponent(fdid) + "&parentAjaxId=eforms");
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
    private EmailLog sendEmail(HttpServletRequest request, EmailComposeSubmissionState composeState) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        EmailData emailData = prepareEmailFields(request, composeState);
        return emailManager.sendEmail(loggedInInfo, emailData);
    }

    /**
     * Extracts and prepares email data from HTTP request parameters and session attributes.
     *
     * <p>This private helper method performs comprehensive email data preparation including:</p>
     * <ul>
     *   <li>Extracting sender and recipient email addresses</li>
     *   <li>Retrieving subject, body, and internal comment fields</li>
     *   <li>Processing encryption settings (email body and attachment encryption)</li>
     *   <li>Resolving server-generated PDF password protection values</li>
     *   <li>Retrieving patient chart display options and demographic information</li>
     *   <li>Extracting transaction type and additional URL parameters</li>
     *   <li>Retrieving email attachments from tokenized server-side compose state</li>
     *   <li>Consuming the compose submission token so draft secrets are not reused</li>
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
        validateSubmittedEmailFields(request);
        return prepareEmailFields(request, resolveEmailComposeSubmissionState(request));
    }

    private EmailData prepareEmailFields(HttpServletRequest request, EmailComposeSubmissionState composeState) {
        String senderConfigId = request.getParameter("senderConfigId");
        String[] receiverEmails = request.getParameterValues("receiverEmailAddress");
        String subject = request.getParameter("subjectEmail");
        String body = request.getParameter("bodyEmail");
        String encryptedMessage = request.getParameter("encryptedMessage");
        String isEncrypted = request.getParameter("isEmailEncrypted");
        String isAttachmentEncrypted = request.getParameter("isEmailAttachmentEncrypted");
        if (!"true".equals(isEncrypted)) {
            isAttachmentEncrypted = "false";
        }
        boolean needsPdfPassword = "true".equals(isEncrypted) || "true".equals(isAttachmentEncrypted);
        String password = resolveEmailPdfPassword(composeState, needsPdfPassword);
        String passwordClue = needsPdfPassword ? resolveEmailPdfPasswordClue(request, composeState) : "";
        EmailComposeSubmissionContext context = composeState.context();
        String chartDisplayOption = request.getParameter("patientChartOption");
        String internalComment = request.getParameter("internalComment");
        String additionalParams = request.getParameter("additionalURLParams");
        List<EmailAttachment> emailAttachmentList = composeState.emailAttachmentList();

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
        emailData.setTransactionType(context.transactionType());
        emailData.setDemographicNo(context.demographicId());
        emailData.setProviderNo(providerNo);
        emailData.setAdditionalParams(additionalParams);
        emailData.setAttachments(emailAttachmentList);
        emailData.setWorkingDirectory(composeState.workingDirectory());

        return emailData;
    }

    private int validateSubmittedEmailFields(HttpServletRequest request) {
        String composeToken = request.getParameter(EmailComposeSubmissionStateService.EMAIL_PDF_PASSWORD_TOKEN_PARAM);
        if (composeToken == null || composeToken.isBlank()) {
            throw new EmailComposeStateException(EmailCompose2Action.EMAIL_COMPOSE_STATE_EXPIRED_MESSAGE);
        }
        try {
            return Integer.parseInt(request.getParameter("senderConfigId"));
        } catch (NumberFormatException e) {
            throw invalidSenderConfigException();
        }
    }

    private void validateActiveSenderConfig(int senderConfigId) {
        if (!emailManager.hasActiveEmailConfig(senderConfigId)) {
            throw invalidSenderConfigException();
        }
    }

    private EmailComposeStateException invalidSenderConfigException() {
        return new EmailComposeStateException(
                "This email compose window contains invalid sender information. "
                        + "Please reopen the email compose window and try again.");
    }

    private EmailComposeSubmissionState resolveEmailComposeSubmissionState(HttpServletRequest request) {
        EmailComposeSubmissionState composeState = emailComposeSubmissionStateService.consume(request);
        if (composeState == null) {
            throw new EmailComposeStateException(EmailCompose2Action.EMAIL_COMPOSE_STATE_EXPIRED_MESSAGE);
        }
        if (composeState.cancelOnly()) {
            composeState.close();
            throw new EmailComposeStateException(EmailCompose2Action.EMAIL_COMPOSE_STATE_EXPIRED_MESSAGE);
        }
        return composeState;
    }

    private void ensureTransactionType(
            EmailComposeSubmissionState composeState,
            EmailLog.TransactionType expectedTransactionType
    ) {
        if (composeState.context().transactionType() != expectedTransactionType) {
            throw new EmailComposeStateException(EmailCompose2Action.EMAIL_COMPOSE_STATE_EXPIRED_MESSAGE);
        }
    }

    private String resolveEmailPdfPassword(
            EmailComposeSubmissionState composeState,
            boolean needsPdfPassword
    ) {
        if (!needsPdfPassword) {
            return "";
        }

        if (!StringUtils.isNullOrEmpty(composeState.emailPDFPassword())) {
            return composeState.emailPDFPassword();
        }
        throw new EmailComposeStateException(EmailCompose2Action.EMAIL_COMPOSE_STATE_EXPIRED_MESSAGE);
    }

    private String resolveEmailPdfPasswordClue(
            HttpServletRequest request,
            EmailComposeSubmissionState composeState
    ) {
        if (!StringUtils.isNullOrEmpty(composeState.emailPDFPasswordClue())) {
            return composeState.emailPDFPasswordClue();
        }
        return EmailComposeSubmissionStateService.resolveEmailPdfPasswordDeliveryInstruction(request);
    }

    private static final class EmailComposeStateException extends RuntimeException {
        private EmailComposeStateException(String message) {
            super(message);
        }
    }
}
