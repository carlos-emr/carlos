package io.github.carlos_emr.carlos.email.action;

import java.io.IOException;
import java.nio.file.Path;

import io.github.carlos_emr.carlos.documentManager.PdfPreviewCapabilityService;
import io.github.carlos_emr.carlos.email.core.EmailComposeWorkingDirectory;
import io.github.carlos_emr.carlos.email.core.EmailPdfPasswordService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.email.core.EmailSendResult;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailConsentStatus;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService.EmailComposeSubmissionContext;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService.EmailComposeSubmissionState;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailComposeManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
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

    private static final String EMAIL_FOLLOW_UP_REQUIRED = "isEmailFollowUpRequired";
    private static final Logger logger = MiscUtils.getLogger();
    private EmailManager emailManager = SpringUtils.getBean(EmailManager.class);
    private transient EmailComposeManager emailComposeManager = SpringUtils.getBean(EmailComposeManager.class);
    private EformDataManager eformDataManager = SpringUtils.getBean(EformDataManager.class);
    private transient EmailComposeSubmissionStateService emailComposeSubmissionStateService =
            SpringUtils.getBean(EmailComposeSubmissionStateService.class);

    private static final String PARAM_RECEIVER_EMAIL_ADDRESS = "receiverEmailAddress";
    private static final String PARAM_PATIENT_CHART_OPTION = "patientChartOption";
    private static final String PARAM_MESSAGE = "message";
    private static final String PARAM_IS_EMAIL_ENCRYPTED = "isEmailEncrypted";
    private static final String PARAM_IS_EMAIL_ATTACHMENT_ENCRYPTED = "isEmailAttachmentEncrypted";
    private static final String PARAM_DELETE_EFORM_AFTER_EMAIL = "deleteEFormAfterEmail";
    private static final String PARAM_OPEN_EFORM_AFTER_EMAIL = "openEFormAfterEmail";
    private static final String PARAM_DEMOGRAPHIC_ID = "demographicId";
    private static final String PARAM_TRANSACTION_TYPE = "transactionType";
    private static final String PARAM_SENDER_CONFIG_ID = "senderConfigId";
    private static final String PARAM_SUBJECT_EMAIL = "subjectEmail";
    private static final String PARAM_EMAIL_PDF_PASSWORD = "emailPDFPassword";
    private static final String PARAM_EMAIL_PDF_PASSWORD_CLUE = "emailPDFPasswordClue";
    private static final String PARAM_INTERNAL_COMMENT = "internalComment";
    private static final String PARAM_CONSENT_OVERRIDE = "consentOverride";
    private static final String PARAM_CONSENT_OVERRIDE_REASON = "consentOverrideReason";
    private transient EmailPdfPasswordService emailPdfPasswordService = SpringUtils.getBean(EmailPdfPasswordService.class);
    private transient PdfPreviewCapabilityService pdfPreviewCapabilityService = SpringUtils.getBean(PdfPreviewCapabilityService.class);
    private static final int MAXIMUM_MESSAGE_LENGTH = 10_000;

    /**
     * Main execution method that routes to specific email handling methods based on the "method" request parameter.
     *
     * <p>This method implements method-based routing for the following email workflows:</p>
     * <ul>
     *   <li><strong>sendDirectEmail</strong> - Sends email directly without EForm context</li>
     *   <li><strong>sendEFormEmail</strong> - Sends email with EForm context</li>
     *   <li><strong>cancel</strong> - Cancels email operation and redirects to source</li>
     *   <li><strong>default</strong> - Sends email with EForm context when no method parameter is specified</li>
     * </ul>
     * Unsupported operations are rejected with HTTP 400 rather than defaulting to a mutation;
     * an absent operation retains the legacy EForm-send behavior.
     *
     * <p><strong>HTTP-method contract:</strong> the entire action is POST-only. Send dispatches
     * transmit patient email outbound, persist an {@link EmailLog}, and may delete eForm data;
     * cancel consumes the in-progress attachment state. Non-POST requests are rejected with 405
     * before dispatch, matching both {@code HttpMethodGuardFilter} and
     * {@code MutatorActionGetRejectionContractUnitTest}.</p>
     *
     * <p>Only the three named dispatches above and the absent-method eForm default are accepted.
     * Any other method parameter is rejected with 400 rather than falling through to an email
     * send, so a misspelled cancel or send action cannot trigger the default mutation.</p>
     *
     * @return String Struts2 result identifier - "success" for successful email operations,
     *         or NONE after cancellation or request rejection
     */
    @Override
    // FindSecBugs XSS_SERVLET: validation failures are fixed server-authored strings returned as
    // text/plain, never request-derived content or HTML.
    @SuppressFBWarnings(
            value = "XSS_SERVLET",
            justification = "response is text/plain and validation messages are fixed server-authored strings")
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", "w", null)) {
            throw new SecurityException("missing required sec object (_email)");
        }

        // A crafted GET link could otherwise send PHI outbound, delete eForm data, or consume
        // in-progress attachment state. The deployed HttpMethodGuardFilter also rejects GET/HEAD
        // method=cancel, so keep the action-level contract consistently POST-only.
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            return rejectRequest(
                    HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    "POST required",
                    "Failed to send 405 on non-POST email action attempt");
        }

        try {
            String method = request.getParameter("method");
            if (method == null || "sendEFormEmail".equals(method)) {
                return sendEFormEmail();
            }
            if ("sendDirectEmail".equals(method)) {
                return sendDirectEmail();
            }
            if ("cancel".equals(method)) {
                return cancel();
            }
            return rejectRequest(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Unsupported email action",
                    "Failed to send 400 for unsupported email action");
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

    private String rejectRequest(int status, String clientMessage, String logMessage) {
        try {
            response.sendError(status, clientMessage);
        } catch (IOException | IllegalStateException e) {
            // sendError throws IllegalStateException if the response is already committed and
            // IOException on write failure. Returning NONE still prevents action dispatch.
            logger.warn(logMessage, e);
        }
        return NONE;
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
     * <p>The method checks the PARAM_DELETE_EFORM_AFTER_EMAIL request parameter to determine if the
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
        EmailSendResult sendResult;
        try {
            validateMessageRequirement(request);
            validateEncryptionRequirements(request);
            validateConsentOverrideReason(request);
            int senderConfigId = validateSubmittedEmailFields(request);
            validateActiveSenderConfig(senderConfigId);
            try (EmailComposeSubmissionState composeState = resolveEmailComposeSubmissionState(request)) {
                context = composeState.context();
                ensureSendCapable(composeState);
                ensureTransactionType(composeState, EmailLog.TransactionType.EFORM);
                sendResult = sendEmail(request, composeState);
                if (!sendResult.isTransportAccepted() && !sendResult.isDeliveryUnconfirmed()) {
                    prepareRetry(composeState, sendResult.getEmailLog());
                }
            }
        } catch (EmailComposeStateException e) {
            return handleEmailComposeStateError(e, context);
        }
        EmailLog emailLog = sendResult.getEmailLog();

        boolean isEmailSuccessful = sendResult.isTransportAccepted();
        request.setAttribute("isEmailSuccessful", isEmailSuccessful);
        request.setAttribute("isEmailDeliveryUnconfirmed", sendResult.isDeliveryUnconfirmed());
        request.setAttribute("isEmailStatusRecorded", sendResult.isTransportOutcomeRecorded());
        request.setAttribute(EMAIL_FOLLOW_UP_REQUIRED, sendResult.isFollowUpRequired());
        if (isEmailSuccessful && context.deleteEFormAfterEmail() && StringUtils.filled(context.fdid())) {
            try {
                eformDataManager.removeEFormData(loggedInInfo, context.fdid());
            } catch (RuntimeException cleanupFailure) {
                logger.error("Email accepted but eForm cleanup failed for emailLogId={}",
                        emailLog.getId(), cleanupFailure);
                request.setAttribute(EMAIL_FOLLOW_UP_REQUIRED, true);
            }
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
        EmailSendResult sendResult;
        try {
            validateMessageRequirement(request);
            validateEncryptionRequirements(request);
            validateConsentOverrideReason(request);
            int senderConfigId = validateSubmittedEmailFields(request);
            validateActiveSenderConfig(senderConfigId);
            try (EmailComposeSubmissionState composeState = resolveEmailComposeSubmissionState(request)) {
                context = composeState.context();
                ensureSendCapable(composeState);
                ensureTransactionType(composeState, EmailLog.TransactionType.DIRECT);
                sendResult = sendEmail(request, composeState);
                if (!sendResult.isTransportAccepted() && !sendResult.isDeliveryUnconfirmed()) {
                    prepareRetry(composeState, sendResult.getEmailLog());
                }
            }
        } catch (EmailComposeStateException e) {
            return handleEmailComposeStateError(e, context);
        }
        EmailLog emailLog = sendResult.getEmailLog();
        boolean isEmailSuccessful = sendResult.isTransportAccepted();
        request.setAttribute("isEmailSuccessful", isEmailSuccessful);
        request.setAttribute("isEmailDeliveryUnconfirmed", sendResult.isDeliveryUnconfirmed());
        request.setAttribute("isEmailStatusRecorded", sendResult.isTransportOutcomeRecorded());
        request.setAttribute(EMAIL_FOLLOW_UP_REQUIRED, sendResult.isFollowUpRequired());
        request.setAttribute("emailLog", emailLog);
        return SUCCESS;
    }

    /** Replaces a consumed attempt with fresh secret state only after a definite failure. */
    private void prepareRetry(EmailComposeSubmissionState previous, EmailLog emailLog) {
        EmailComposeWorkingDirectory retryDirectory = null;
        try {
            preserveComposeInputsForReRender(emailLog);
            retryDirectory = emailComposeSubmissionStateService.createWorkingDirectory();
            List<EmailAttachment> attachments = copyAttachments(previous.emailAttachmentList());
            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
            for (EmailAttachment attachment : attachments) {
                Path owned = retryDirectory.adoptGeneratedPdf(Path.of(attachment.getFilePath()));
                attachment.setFilePath(owned.toString());
                attachment.setPreviewToken(pdfPreviewCapabilityService.issue(request, loggedInInfo, owned));
            }
            var state = emailComposeSubmissionStateService.preparePdfPasswordSubmissionState(
                    request, emailPdfPasswordService, attachments, previous.context(), retryDirectory);
            retryDirectory = null; // The cache now owns the replacement files.
            request.setAttribute(PARAM_EMAIL_PDF_PASSWORD, state.emailPDFPassword());
            request.setAttribute(PARAM_EMAIL_PDF_PASSWORD_CLUE, state.emailPDFPasswordClue());
            request.setAttribute(EmailComposeSubmissionStateService.EMAIL_PDF_PASSWORD_TOKEN_PARAM,
                    state.emailPDFPasswordToken());
            request.setAttribute("emailAttachmentList", attachments);
            request.setAttribute(PARAM_DEMOGRAPHIC_ID, previous.context().demographicId());
            request.setAttribute(PARAM_TRANSACTION_TYPE, previous.context().transactionType());
            request.setAttribute("fdid", previous.context().fdid());
            request.setAttribute(PARAM_OPEN_EFORM_AFTER_EMAIL, previous.context().openEFormAfterEmail());
            request.setAttribute(PARAM_DELETE_EFORM_AFTER_EMAIL, previous.context().deleteEFormAfterEmail());
            request.setAttribute("isEmailAutoSend", false);
        } catch (IOException | PDFGenerationException | RuntimeException failure) {
            if (retryDirectory != null) {
                retryDirectory.close();
            }
            throw new EmailComposeStateException(EmailCompose2Action.EMAIL_COMPOSE_STATE_UNAVAILABLE_MESSAGE);
        }
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
        String[] receiverEmails = request.getParameterValues(PARAM_RECEIVER_EMAIL_ADDRESS);
        request.setAttribute(
                PARAM_TRANSACTION_TYPE,
                trustedContext == null
                        ? EmailData.parseTransactionType(request.getParameter(PARAM_TRANSACTION_TYPE))
                        : trustedContext.transactionType());
        request.setAttribute("receiverEmailList", receiverEmails == null ? List.of() : Arrays.asList(receiverEmails));
        request.setAttribute("invalidReceiverEmailList", List.of());
        request.setAttribute("senderAccounts", List.of());
        request.setAttribute(PARAM_SENDER_CONFIG_ID, request.getParameter(PARAM_SENDER_CONFIG_ID));
        request.setAttribute(PARAM_SUBJECT_EMAIL, request.getParameter(PARAM_SUBJECT_EMAIL));
        request.setAttribute(PARAM_MESSAGE, request.getParameter(PARAM_MESSAGE));
        request.setAttribute("emailPatientChartOption", request.getParameter(PARAM_PATIENT_CHART_OPTION));
        request.setAttribute(
                PARAM_DEMOGRAPHIC_ID,
                trustedContext == null
                        ? integerParameterOrEmpty(request, PARAM_DEMOGRAPHIC_ID)
                        : trustedContext.demographicId());
        request.setAttribute(
                "fdid",
                trustedContext == null ? integerParameterOrEmpty(request, "fdid") : trustedContext.fdid());
        request.setAttribute(
                PARAM_OPEN_EFORM_AFTER_EMAIL,
                trustedContext == null
                        ? isTrueParameter(request, PARAM_OPEN_EFORM_AFTER_EMAIL)
                        : trustedContext.openEFormAfterEmail());
        request.setAttribute(
                PARAM_DELETE_EFORM_AFTER_EMAIL,
                trustedContext == null
                        ? isTrueParameter(request, PARAM_DELETE_EFORM_AFTER_EMAIL)
                        : trustedContext.deleteEFormAfterEmail());
        request.setAttribute(PARAM_IS_EMAIL_ENCRYPTED, isTrueParameter(request, PARAM_IS_EMAIL_ENCRYPTED));
        request.setAttribute(PARAM_IS_EMAIL_ATTACHMENT_ENCRYPTED, isTrueParameter(request, PARAM_IS_EMAIL_ATTACHMENT_ENCRYPTED));
        request.setAttribute(PARAM_EMAIL_PDF_PASSWORD, "");
        request.setAttribute(PARAM_EMAIL_PDF_PASSWORD_CLUE, "");
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
     * Re-seeds the provider's submitted compose inputs into request scope so a failed-send re-render
     * of emailCompose.jsp preserves both the typed message AND the chosen encryption state. Without
     * this, the JSP re-initializes the encryption toggle from {@code isEmailEncrypted} (unset after a
     * send), so a blind retry of a failed encrypted send could silently go out as cleartext — a
     * PHI-safety regression (issue #3118).
     */
    private void preserveComposeInputsForReRender(EmailLog emailLog) {
        request.setAttribute(PARAM_MESSAGE, request.getParameter(PARAM_MESSAGE));
        // Fail closed on both encryption flags, matching prepareEmailFields: only an explicit
        // "false" re-renders a toggle OFF, so a failed draft cannot silently lose protection.
        request.setAttribute(PARAM_IS_EMAIL_ENCRYPTED,
                isEncryptionEnabled(request.getParameter(PARAM_IS_EMAIL_ENCRYPTED)));
        request.setAttribute(PARAM_IS_EMAIL_ATTACHMENT_ENCRYPTED,
                isEncryptionEnabled(request.getParameter(PARAM_IS_EMAIL_ATTACHMENT_ENCRYPTED)));
        request.setAttribute(PARAM_DEMOGRAPHIC_ID, request.getParameter(PARAM_DEMOGRAPHIC_ID));
        request.setAttribute("fdid", request.getParameter("fdid"));
        request.setAttribute("fid", request.getParameter("fid"));
        request.setAttribute(
                PARAM_OPEN_EFORM_AFTER_EMAIL, request.getParameter(PARAM_OPEN_EFORM_AFTER_EMAIL));
        request.setAttribute(
                PARAM_DELETE_EFORM_AFTER_EMAIL, request.getParameter(PARAM_DELETE_EFORM_AFTER_EMAIL));
        request.setAttribute(PARAM_TRANSACTION_TYPE, request.getParameter(PARAM_TRANSACTION_TYPE));
        request.setAttribute(PARAM_SENDER_CONFIG_ID, request.getParameter(PARAM_SENDER_CONFIG_ID));
        request.setAttribute(PARAM_SUBJECT_EMAIL, request.getParameter(PARAM_SUBJECT_EMAIL));
        request.setAttribute(
                PARAM_EMAIL_PDF_PASSWORD, "");
        request.setAttribute(
                PARAM_EMAIL_PDF_PASSWORD_CLUE, "");
        request.setAttribute("emailPatientChartOption", request.getParameter(PARAM_PATIENT_CHART_OPTION));
        request.setAttribute(PARAM_INTERNAL_COMMENT, request.getParameter(PARAM_INTERNAL_COMMENT));
        request.setAttribute("emailAdditionalParams", request.getParameter("additionalURLParams"));
        request.setAttribute("emailConsentName", request.getParameter("emailConsentName"));
        EmailConsentStatus consentStatus = emailLog.getConsentStatus() != null
                ? emailLog.getConsentStatus()
                : parseConsentStatus(request.getParameter("emailConsentStatus"));
        request.setAttribute("emailConsentStatus", consentStatus.name());
        request.setAttribute("emailConsentMessageKey", consentStatus.getMessageKey());
        // Re-seed only the persisted, send-time decision. Trusting request parameters here would
        // let a forged OPT_OUT submission render as though an override had been accepted.
        request.setAttribute(PARAM_CONSENT_OVERRIDE, emailLog.getConsentOverride());
        request.setAttribute(PARAM_CONSENT_OVERRIDE_REASON,
                emailLog.getConsentOverride() ? emailLog.getConsentOverrideReason() : "");
        request.setAttribute("invalidReceiverEmailList", List.of());

        String[] recipients = request.getParameterValues(PARAM_RECEIVER_EMAIL_ADDRESS);
        request.setAttribute("receiverEmailList",
                recipients == null ? List.of() : Arrays.asList(recipients));
        // Keep every currently active sender available. The selected account may be the reason the
        // delivery failed, so restricting the retry form to that one account prevents recovery.
        request.setAttribute("senderAccounts", getSenderAccountsForRetry(emailLog));
        if (emailLog.getEmailConfig() != null) {
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

    private List<EmailConfig> getSenderAccountsForRetry(EmailLog emailLog) {
        try {
            return emailComposeManager.getAllSenderAccounts();
        } catch (RuntimeException e) {
            logger.warn("Unable to load sender accounts for email retry; using the failed account", e);
            EmailConfig failedSender = emailLog.getEmailConfig();
            return failedSender == null ? List.of() : List.of(failedSender);
        }
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
     * The action is POST-only; completed cancellations return {@link #NONE}.
     *
     * @return {@link #NONE} after cancellation is handled
     * @throws RuntimeException if IOException occurs during redirect for EFORM transactions
     */
    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    public String cancel() {
        String submittedType = request.getParameter(PARAM_TRANSACTION_TYPE);
        if (!"DIRECT".equals(submittedType) && !"EFORM".equals(submittedType)) {
            return rejectRequest(HttpServletResponse.SC_BAD_REQUEST,
                    "Unsupported email transaction type", "Failed to send cancellation error");
        }
        EmailComposeSubmissionState composeState = emailComposeSubmissionStateService.consume(request);
        EmailComposeSubmissionContext trustedContext = composeState == null ? null : composeState.context();
        EmailLog.TransactionType transactionType = trustedContext == null
                ? EmailData.parseTransactionType(request.getParameter(PARAM_TRANSACTION_TYPE))
                : trustedContext.transactionType();
        String fdid = trustedContext == null ? request.getParameter("fdid") : trustedContext.fdid();
        if (composeState != null) {
            composeState.close();
        }
        if (transactionType.equals(EmailLog.TransactionType.EFORM)) {
            try {
                response.sendRedirect(request.getContextPath() + "/eform/efmshowform_data?fdid="
                        + SafeEncode.forUriComponent(fdid) + "&parentAjaxId=eforms");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        }
        return NONE;
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
     * @return result containing both the transport outcome and its associated email log
     */
    private EmailSendResult sendEmail(HttpServletRequest request, EmailComposeSubmissionState composeState) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        EmailData emailData = prepareEmailFields(request, composeState);
        return emailManager.sendEmailWithResult(loggedInInfo, emailData);
    }

    /**
     * Enforces the compose form's required message at the server boundary. Client-side validation
     * can be bypassed by a direct POST, and an empty encrypted message would otherwise send only a
     * notice claiming that a password-protected PDF is attached.
     *
     * <p>This check intentionally runs before preparing or sending the email. A rejected request
     * must not discard attachments that the provider may need to recover.</p>
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
     * validation is only a usability aid and can be bypassed by a direct POST. The passphrase
     * itself is resolved exclusively from server-owned submission state.
     *
     * <p>This check intentionally runs before preparing or sending the email. A rejected request
     * must not discard attachments that the provider may need to recover.</p>
     *
     * @param request request containing the submitted encryption fields
     * @throws EmailSendValidationException when the encryption toggles conflict
     */
    private void validateEncryptionRequirements(HttpServletRequest request) {
        boolean encrypted = isEncryptionEnabled(request.getParameter(PARAM_IS_EMAIL_ENCRYPTED));
        boolean attachmentEncrypted = isEncryptionEnabled(
                request.getParameter(PARAM_IS_EMAIL_ATTACHMENT_ENCRYPTED));
        if (!encrypted && attachmentEncrypted) {
            throw new EmailSendValidationException(
                    "Attachment encryption requires message encryption");
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
        String reason = request.getParameter(PARAM_CONSENT_OVERRIDE_REASON);
        if (reason != null
                && reason.trim().length() > EmailData.CONSENT_OVERRIDE_REASON_MAX_LENGTH) {
            throw new EmailSendValidationException(
                    "Consent override reason must not exceed "
                            + EmailData.CONSENT_OVERRIDE_REASON_MAX_LENGTH + " characters");
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
    private EmailData prepareEmailFields(HttpServletRequest request, EmailComposeSubmissionState composeState) {
        String senderConfigId = request.getParameter(PARAM_SENDER_CONFIG_ID);
        String[] receiverEmails = request.getParameterValues(PARAM_RECEIVER_EMAIL_ADDRESS);
        String subject = request.getParameter(PARAM_SUBJECT_EMAIL);
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
        // that omits or garbles either toggle defaults to ENCRYPTED, so PHI is never routed to the
        // cleartext body or sent in an unprotected attachment when intent is unclear. See #3118.
        boolean encrypted = isEncryptionEnabled(isEncrypted);
        boolean attachmentEncrypted = isEncryptionEnabled(
                request.getParameter(PARAM_IS_EMAIL_ATTACHMENT_ENCRYPTED));
        String body = encrypted ? encryptedBodyNotice() : message;
        String encryptedMessage = encrypted ? message : "";

        String password = resolveEmailPdfPassword(composeState, encrypted);
        String passwordClue = encrypted ? resolveEmailPdfPasswordClue(request, composeState) : "";
        EmailComposeSubmissionContext context = composeState.context();
        String chartDisplayOption = request.getParameter(PARAM_PATIENT_CHART_OPTION);
        String internalComment = request.getParameter(PARAM_INTERNAL_COMMENT);
        String additionalParams = request.getParameter("additionalURLParams");
        String consentOverride = request.getParameter(PARAM_CONSENT_OVERRIDE);
        String consentOverrideReason = request.getParameter(PARAM_CONSENT_OVERRIDE_REASON);
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
        emailData.setIsEncrypted(encrypted);
        emailData.setIsAttachmentEncrypted(attachmentEncrypted);
        emailData.setChartDisplayOption(chartDisplayOption);
        emailData.setInternalComment(internalComment);
        emailData.setTransactionType(context.transactionType());
        emailData.setDemographicNo(context.demographicId());
        emailData.setProviderNo(providerNo);
        emailData.setAdditionalParams(additionalParams);
        emailData.setAttachments(copyAttachments(emailAttachmentList));
        emailData.setConsentOverride(consentOverride);
        emailData.setConsentOverrideReason(consentOverrideReason);
        emailData.setWorkingDirectory(composeState.workingDirectory());

        return emailData;
    }

    private int validateSubmittedEmailFields(HttpServletRequest request) {
        String composeToken = request.getParameter(EmailComposeSubmissionStateService.EMAIL_PDF_PASSWORD_TOKEN_PARAM);
        if (composeToken == null || composeToken.isBlank()) {
            throw new EmailComposeStateException(EmailCompose2Action.EMAIL_COMPOSE_STATE_EXPIRED_MESSAGE);
        }
        try {
            return Integer.parseInt(request.getParameter(PARAM_SENDER_CONFIG_ID));
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
        return composeState;
    }

    private void ensureSendCapable(EmailComposeSubmissionState composeState) {
        if (composeState.cancelOnly()) {
            throw new EmailComposeStateException(EmailCompose2Action.EMAIL_COMPOSE_STATE_EXPIRED_MESSAGE);
        }
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
        if (!needsPdfPassword || io.github.carlos_emr.carlos.integration.patientportal.PortalEmailDelivery.isEnabled()) {
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

    /** Only an explicit false value opts out of message or attachment encryption. */
    private static boolean isEncryptionEnabled(String value) {
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
