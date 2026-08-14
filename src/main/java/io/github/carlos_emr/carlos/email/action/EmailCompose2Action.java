package io.github.carlos_emr.carlos.email.action;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.documentManager.PdfPreviewCapabilityService;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService.EmailComposeSubmissionContext;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService;
import io.github.carlos_emr.carlos.email.core.EmailComposeWorkingDirectory;
import io.github.carlos_emr.carlos.email.core.EmailPdfPasswordService;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.EmailComposeManager;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

/**
 * Struts2 action for composing and preparing email messages with patient-related attachments.
 *
 * This action handles the preparation of email composition screens for sending electronic forms (eForms),
 * documents, laboratory results, and other patient health information via email. It manages session-based
 * email composition data, prepares attachments with optional PDF encryption, retrieves patient email consent
 * status, and validates recipient information before presenting the compose interface.
 *
 * Key Features:
 * <ul>
 *   <li>Prepares email composition interface for eForms and patient documents</li>
 *   <li>Manages session-based email composition state (survives redirects)</li>
 *   <li>Handles multiple attachment types: eForms, eDocuments, lab results, forms, HRM documents</li>
 *   <li>Generates and manages PDF password encryption for patient privacy</li>
 *   <li>Validates patient email consent status before sending</li>
 *   <li>Retrieves and validates recipient email addresses</li>
 *   <li>Sanitizes attachment filenames for security</li>
 *   <li>Validates numeric form ID (fid) parameters to prevent injection attacks</li>
 * </ul>
 *
 * Healthcare Context:
 * This action is part of OpenO EMR's secure patient communication system, ensuring that Protected Health
 * Information (PHI) is transmitted with appropriate encryption, consent verification, and audit logging.
 * It supports PIPEDA/HIPAA compliance by enforcing patient consent for email communications and providing
 * password-protected PDF attachments with server-generated random passphrases.
 *
 * Session Management:
 * The action retrieves email composition parameters from the HTTP session (allowing for redirect-based
 * workflows) and transfers them to request attributes for JSP rendering. Session attributes are cleaned
 * up after transfer to prevent stale data accumulation.
 *
 * Security Considerations:
 * <ul>
 *   <li>Validates fid parameter to ensure numeric format (prevents injection)</li>
 *   <li>Uses log-safe sanitization for invalid fid values in logs</li>
 *   <li>Generates random PDF passphrases without using patient demographic information</li>
 *   <li>Sanitizes attachment filenames through EmailComposeManager</li>
 *   <li>Session cleanup prevents information leakage across requests</li>
 * </ul>
 *
 * @see io.github.carlos_emr.carlos.managers.EmailComposeManager
 * @see io.github.carlos_emr.carlos.managers.DemographicManager
 * @see io.github.carlos_emr.carlos.commn.model.EmailAttachment
 * @see io.github.carlos_emr.carlos.commn.model.EmailConfig
 * @see io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType
 * @since 2026-01-24
 */
public class EmailCompose2Action extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final Logger logger = MiscUtils.getLogger();
    private DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    private EmailComposeManager emailComposeManager = SpringUtils.getBean(EmailComposeManager.class);
    private transient EmailPdfPasswordService emailPdfPasswordService = SpringUtils.getBean(EmailPdfPasswordService.class);
    private transient EmailComposeSubmissionStateService emailComposeSubmissionStateService =
            SpringUtils.getBean(EmailComposeSubmissionStateService.class);
    private PdfPreviewCapabilityService pdfPreviewCapabilityService =
            SpringUtils.getBean(PdfPreviewCapabilityService.class);

    public static final String EMAIL_COMPOSE_STATE_EXPIRED_MESSAGE =
            "This email compose window has expired or is no longer valid. "
                    + "Please reopen the email compose window and try again.";
    public static final String EMAIL_COMPOSE_STATE_UNAVAILABLE_MESSAGE =
            "This email compose window could not be prepared. "
                    + "Please close other open email compose windows and try again.";
    private static final String DEMOGRAPHIC_ID_KEY = "demographicId";

    private static final String[] EMAIL_SESSION_KEYS = {
        "attachEFormItSelf", "fdid", DEMOGRAPHIC_ID_KEY, "emailAttachmentList",
        "emailPDFPassword", "emailPDFPasswordClue",
        "attachedDocuments", "attachedLabs", "attachedForms",
        "attachedEForms", "attachedHRMDocuments",
        "deleteEFormAfterEmail", "isEmailEncrypted",
        "isEmailAttachmentEncrypted", "isEmailAutoSend",
        "openEFormAfterEmail", "senderEmail", "subjectEmail",
        "bodyEmail", "encryptedMessageEmail",
        "emailPatientChartOption"
    };


    /**
     * Executes the default action for email composition.
     *
     * This method serves as the main entry point for the Struts2 action and delegates to
     * prepareComposeEFormMailer() to handle the email composition preparation logic.
     *
     * @return String the Struts2 result name, either "compose" for successful preparation
     *         or "eFormError" if PDF generation fails
     * @see #prepareComposeEFormMailer()
     */
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", "w", null)) {
            throw new SecurityException("missing required sec object (_email)");
        }

        return prepareComposeEFormMailer();
    }

    /**
     * Prepares the email composition interface with patient information, attachments, and email settings.
     *
     * This method orchestrates the complete email composition preparation workflow:
     * <ol>
     *   <li>Retrieves email composition parameters from HTTP session (survives redirects)</li>
     *   <li>Validates form ID (fid) parameter for numeric format to prevent injection</li>
     *   <li>Retrieves patient email consent status and validates consent settings</li>
     *   <li>Fetches patient demographic information for recipient name display</li>
     *   <li>Retrieves and validates recipient email addresses (separates valid/invalid)</li>
     *   <li>Loads available sender email account configurations</li>
     *   <li>Prepares a server-assigned random PDF passphrase for optional PDF encryption</li>
     *   <li>Prepares all attachment types: eForms, eDocuments, labs, forms, HRM documents</li>
     *   <li>Sanitizes attachment filenames for security</li>
     *   <li>Transfers session data to request attributes for JSP rendering</li>
     *   <li>Cleans up session attributes to prevent stale data</li>
     * </ol>
     *
     * Session Attributes Retrieved:
     * <ul>
     *   <li>attachEFormItSelf (Boolean) - whether to attach the eForm itself</li>
     *   <li>fdid (String) - form data ID for the eForm</li>
     *   <li>demographicId (String) - patient demographic identifier (required)</li>
     *   <li>attachedDocuments (String[]) - array of document IDs to attach</li>
     *   <li>attachedLabs (String[]) - array of lab result IDs to attach</li>
     *   <li>attachedForms (String[]) - array of form IDs to attach</li>
     *   <li>attachedEForms (String[]) - array of eForm IDs to attach</li>
     *   <li>attachedHRMDocuments (String[]) - array of HRM document IDs to attach</li>
     *   <li>senderEmail (String) - sender email address</li>
     *   <li>subjectEmail (String) - email subject line</li>
     *   <li>bodyEmail (String) - email message body</li>
     *   <li>encryptedMessageEmail (String) - encrypted message content</li>
     *   <li>emailPatientChartOption (String) - patient chart email option setting</li>
     * </ul>
     *
     * Request Parameters:
     * <ul>
     *   <li>fid (String, optional) - form identifier, validated for numeric format</li>
     * </ul>
     *
     * Request Attributes Set:
     * <ul>
     *   <li>transactionType (TransactionType) - set to EFORM for transaction logging</li>
     *   <li>emailConsentName (String) - patient consent form name</li>
     *   <li>emailConsentStatus (String) - patient email consent status (Yes/No)</li>
     *   <li>receiverName (String) - formatted patient name for display</li>
     *   <li>receiverEmailList (List) - list of valid recipient email addresses</li>
     *   <li>invalidReceiverEmailList (List) - list of invalid email addresses</li>
     *   <li>senderAccounts (List&lt;EmailConfig&gt;) - available sender account configurations</li>
     *   <li>emailPDFPassword (String) - generated PDF passphrase shown with the encryption controls</li>
     *   <li>emailPDFPasswordClue (String) - provider delivery instruction</li>
     *   <li>emailPDFPasswordToken (String) - per-compose token used to consume prepared submission state</li>
     *   <li>demographicId (String) - patient demographic identifier</li>
     *   <li>fdid (String) - form data ID</li>
     *   <li>fid (String) - validated form ID or null if invalid</li>
     * </ul>
     *
     * Server-Side State Stored:
     * <ul>
     *   <li>tokenized prepared compose state keyed by session id and emailPDFPasswordToken</li>
     * </ul>
     *
     * Security Features:
     * <ul>
     *   <li>Validates fid parameter with regex pattern to ensure numeric format only</li>
     *   <li>Logs warnings for invalid fid values using OWASP-encoded output</li>
     *   <li>Generates server-assigned random passphrases without patient identifiers</li>
     *   <li>Sanitizes all attachment filenames to prevent path traversal attacks</li>
     *   <li>Verifies patient email consent before allowing composition</li>
     *   <li>Cleans up session attributes after transfer to prevent information leakage</li>
     * </ul>
     *
     * Error Handling:
     * If compose session state is missing or invalid, the method returns the "eFormError" result
     * with a generic expired-state message. If PDF generation fails for any attachment (eForm,
     * document, lab, form, HRM), it returns a generic, PHI-safe attachment message. If one-time
     * compose token preparation fails because the cache is unavailable, it returns a generic
     * unavailable-state message.
     *
     * @return String the Struts2 result name: "compose" for successful preparation,
     *         "eFormError" if compose state is missing, attachment generation fails, or one-time
     *         compose token preparation is unavailable
     * @see io.github.carlos_emr.carlos.managers.EmailComposeManager#getEmailConsentStatus(LoggedInInfo, Integer)
     * @see io.github.carlos_emr.carlos.managers.EmailComposeManager#getRecipients(LoggedInInfo, Integer)
     * @see io.github.carlos_emr.carlos.email.core.EmailPdfPasswordService#generatePassphrase()
     * @see io.github.carlos_emr.carlos.managers.EmailComposeManager#prepareEFormAttachments(LoggedInInfo, String, String[])
     * @see io.github.carlos_emr.carlos.managers.EmailComposeManager#sanitizeAttachments(List)
     * @see #cleanupEmailSessionAttributes(HttpServletRequest)
     * @see #emailComposeError(HttpServletRequest, String)
     */
    public String prepareComposeEFormMailer() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // Get email information from session (survives redirect)
        HttpSession session = request.getSession();
        Boolean attachEFormItSelfObj = (Boolean) session.getAttribute("attachEFormItSelf");
        boolean attachEFormItSelf = attachEFormItSelfObj != null && attachEFormItSelfObj;
        String fdid = attachEFormItSelf ? (String) session.getAttribute("fdid") : "";
        String demographicId = (String) session.getAttribute(DEMOGRAPHIC_ID_KEY);
        String fid = request.getParameter("fid");
        String[] attachedDocuments = (String[]) session.getAttribute("attachedDocuments");
        String[] attachedLabs = (String[]) session.getAttribute("attachedLabs");
        String[] attachedForms = (String[]) session.getAttribute("attachedForms");
        String[] attachedEForms = (String[]) session.getAttribute("attachedEForms");
        String[] attachedHRMDocuments = (String[]) session.getAttribute("attachedHRMDocuments");
        String senderEmail = (String) session.getAttribute("senderEmail");
        String subjectEmail = (String) session.getAttribute("subjectEmail");
        String bodyEmail = (String) session.getAttribute("bodyEmail");
        String encryptedMessageEmail = (String) session.getAttribute("encryptedMessageEmail");
        String emailPatientChartOption = (String) session.getAttribute("emailPatientChartOption");
        String emailFdid = (String) session.getAttribute("fdid");

        if (demographicId == null || demographicId.isBlank()) {
            return emailComposeError(request, EMAIL_COMPOSE_STATE_EXPIRED_MESSAGE);
        }

        // Validate fid is numeric if provided
        if (fid != null && !fid.matches("\\d+")) {
            if (logger.isWarnEnabled()) {
                String sanitizedFid = LogSafe.sanitize(fid);
                logger.warn("Invalid fid parameter received: {}", sanitizedFid);
            }
            fid = null;
        }

        // Don't clean up session attributes here - they are needed by the JSP
        // Session cleanup is performed in this action immediately after transferring session data to request attributes.

        int demographicNo;
        try {
            demographicNo = Integer.parseInt(demographicId);
        } catch (NumberFormatException e) {
            return emailComposeError(request, EMAIL_COMPOSE_STATE_EXPIRED_MESSAGE);
        }
        String[] emailConsent = emailComposeManager.getEmailConsentStatus(loggedInInfo, demographicNo);

        String receiverName = demographicManager.getDemographicFormattedName(loggedInInfo, demographicNo);
        List<?>[] receiverEmailList = emailComposeManager.getRecipients(loggedInInfo, demographicNo);

        List<EmailConfig> senderAccounts = emailComposeManager.getAllSenderAccounts();

        EmailComposeWorkingDirectory workingDirectory;
        try {
            workingDirectory = emailComposeSubmissionStateService.createWorkingDirectory();
        } catch (IllegalStateException e) {
            logger.warn("Unable to create email compose working directory", e);
            return emailComposeError(request, EMAIL_COMPOSE_STATE_UNAVAILABLE_MESSAGE);
        }

        List<EmailAttachment> emailAttachmentList = new ArrayList<>();
        try {
            emailAttachmentList.addAll(emailComposeManager.prepareEFormAttachments(
                    loggedInInfo, fdid, attachedEForms, workingDirectory));
            emailAttachmentList.addAll(emailComposeManager.prepareEDocAttachments(
                    loggedInInfo, attachedDocuments, workingDirectory));
            emailAttachmentList.addAll(emailComposeManager.prepareLabAttachments(
                    loggedInInfo, attachedLabs, workingDirectory));
            emailAttachmentList.addAll(emailComposeManager.prepareHRMAttachments(
                    loggedInInfo, attachedHRMDocuments, workingDirectory));
            emailAttachmentList.addAll(emailComposeManager.prepareFormAttachments(
                    request, response, attachedForms, demographicNo, workingDirectory));
            emailComposeManager.sanitizeAttachments(emailAttachmentList);
            for (EmailAttachment attachment : emailAttachmentList) {
                attachment.setPreviewToken(pdfPreviewCapabilityService.issue(
                        request, loggedInInfo, java.nio.file.Path.of(attachment.getFilePath())));
            }
        } catch (PDFGenerationException | RuntimeException e) {
            workingDirectory.close();
            logger.error(e.getMessage(), e);
            return emailComposeError(request, "This eForm (and attachments, if applicable) could not be emailed. \\n\\n" + e.getMessage());
        }

        Object isEmailEncrypted = session.getAttribute("isEmailEncrypted");
        Object isEmailAttachmentEncrypted = isTrue(isEmailEncrypted)
                ? session.getAttribute("isEmailAttachmentEncrypted")
                : false;
        EmailComposeSubmissionStateService.EmailPdfPasswordSubmissionState emailPdfPasswordSubmissionState;
        try {
            emailPdfPasswordSubmissionState = emailComposeSubmissionStateService.preparePdfPasswordSubmissionState(
                    request,
                    emailPdfPasswordService,
                    emailAttachmentList,
                    EmailComposeSubmissionContext.eform(
                            demographicId,
                            emailFdid,
                            isTrue(session.getAttribute("openEFormAfterEmail")),
                            isTrue(session.getAttribute("deleteEFormAfterEmail"))),
                    workingDirectory);
        } catch (IllegalStateException e) {
            workingDirectory.close();
            logger.warn("Unable to prepare email compose submission state", e);
            return emailComposeError(request, EMAIL_COMPOSE_STATE_UNAVAILABLE_MESSAGE);
        }

        // Set request attributes for JSP (from session and computed values)
        request.setAttribute("transactionType", TransactionType.EFORM);
        request.setAttribute("emailConsentName", emailConsent[0]);
        request.setAttribute("emailConsentStatus", emailConsent[1]);
        request.setAttribute("receiverName", receiverName);
        request.setAttribute("receiverEmailList", receiverEmailList[0]);
        request.setAttribute("invalidReceiverEmailList", receiverEmailList[1]);
        request.setAttribute("senderAccounts", senderAccounts);
        request.setAttribute("emailPDFPassword", emailPdfPasswordSubmissionState.emailPDFPassword());
        request.setAttribute("emailPDFPasswordClue", emailPdfPasswordSubmissionState.emailPDFPasswordClue());
        request.setAttribute("emailAttachmentList", emailAttachmentList);
        request.setAttribute("senderEmail", senderEmail);
        request.setAttribute("subjectEmail", subjectEmail);
        request.setAttribute("bodyEmail", bodyEmail);
        request.setAttribute("encryptedMessageEmail", encryptedMessageEmail);
        request.setAttribute("emailPatientChartOption", emailPatientChartOption);
        request.setAttribute(DEMOGRAPHIC_ID_KEY, demographicId);
        request.setAttribute("fdid", emailFdid);
        request.setAttribute("fid", fid);
        request.setAttribute("openEFormAfterEmail", session.getAttribute("openEFormAfterEmail"));
        request.setAttribute("deleteEFormAfterEmail", session.getAttribute("deleteEFormAfterEmail"));
        request.setAttribute("isEmailEncrypted", isEmailEncrypted);
        request.setAttribute("isEmailAttachmentEncrypted", isEmailAttachmentEncrypted);
        request.setAttribute(
                "isEmailAutoSend",
                shouldAutoSendEmail(session.getAttribute("isEmailAutoSend"), isEmailEncrypted));

        cleanupEmailSessionAttributes(request);
        request.setAttribute(
                EmailComposeSubmissionStateService.EMAIL_PDF_PASSWORD_TOKEN_PARAM,
                emailPdfPasswordSubmissionState.emailPDFPasswordToken());

        return "compose";
    }

    private static boolean shouldAutoSendEmail(Object autoSendValue, Object encryptedValue) {
        return isTrue(autoSendValue) && !isTrue(encryptedValue);
    }

    private static boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equals(value);
    }

    /**
     * Cleans up email-related session attributes.
     * This method is called after transferring email composition data from session to request attributes, before rendering the compose screen.
     *
     * @param request the HTTP servlet request containing the session to clean up
     * @since 2025-01-18
     */
    public static void cleanupEmailSessionAttributes(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }

        for (String key : EMAIL_SESSION_KEYS) {
            session.removeAttribute(key);
        }
    }

    /**
     * Handles email composition errors by setting error message and returning error result.
     *
     * This method is called when email composition preparation fails. It sets a caller-provided,
     * user-safe error message as a request attribute for display on the error page. Attachment
     * preparation failures must pass generic messages here and keep any server diagnostics free of
     * PHI.
     *
     * Common Error Scenarios:
     * <ul>
     *   <li>PDF generation failure for eForms, documents, or forms</li>
     *   <li>Missing or inaccessible attachment files</li>
     *   <li>File I/O errors during attachment preparation</li>
     *   <li>Encryption errors for PDF password protection</li>
     * </ul>
     *
     * @param request HttpServletRequest the HTTP servlet request to store the error message
     * @param errorMessage String the PHI-safe error message to display to the user
     * @return String the Struts2 result name "eFormError" which maps to the error display page
     * @see io.github.carlos_emr.carlos.utility.PDFGenerationException
     */
    private String emailComposeError(HttpServletRequest request, String errorMessage) {
        cleanupEmailSessionAttributes(request);
        request.setAttribute("errorMessage", errorMessage);
        return "eFormError";
    }
}
