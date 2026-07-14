package io.github.carlos_emr.carlos.email.action;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
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
    private EmailPdfPasswordService emailPdfPasswordService = SpringUtils.getBean(EmailPdfPasswordService.class);

    public static final String EMAIL_PDF_PASSWORD_TOKEN_PARAM = "emailPDFPasswordToken";
    static final int MAX_PENDING_EMAIL_COMPOSE_STATES = 8;
    private static final int MAX_PENDING_EMAIL_COMPOSE_SUBMISSION_CACHE_STATES = 1024;
    static final long PENDING_EMAIL_COMPOSE_STATE_MAX_AGE_MILLIS = 15L * 60 * 1000;
    private static final Object EMAIL_COMPOSE_SUBMISSION_STATES_LOCK = new Object();
    private static final Map<EmailComposeSubmissionStateKey, EmailComposeSubmissionState>
            PENDING_EMAIL_COMPOSE_SUBMISSION_STATES = new HashMap<>();

    private static final String[] EMAIL_SESSION_KEYS = {
        "attachEFormItSelf", "fdid", "demographicId", "emailAttachmentList",
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
     *   <li>Generates a server-assigned random PDF passphrase</li>
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
     *   <li>emailPDFPassword (String) - ignored legacy session value; replaced with a generated passphrase</li>
     *   <li>emailPDFPasswordClue (String) - ignored legacy session value; replaced with a delivery instruction</li>
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
     *   <li>emailPDFPassword (String) - generated PDF passphrase shown once for separate delivery</li>
     *   <li>emailPDFPasswordClue (String) - provider delivery instruction</li>
     *   <li>emailPDFPasswordToken (String) - per-compose token used to consume prepared submission state</li>
     *   <li>demographicId (String) - patient demographic identifier</li>
     *   <li>fdid (String) - form data ID</li>
     *   <li>fid (String) - validated form ID or null if invalid</li>
     * </ul>
     *
     * Session Attributes Set:
     * <ul>
     *   <li>emailComposeSubmissionStates (Map) - tokenized prepared compose states</li>
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
     * If PDF generation fails for any attachment (eForm, document, lab, form, HRM), the method
     * returns the "eFormError" result with a descriptive error message. This prevents incomplete
     * emails from being composed when required attachments cannot be generated.
     *
     * @return String the Struts2 result name: "compose" for successful preparation,
     *         "eFormError" if PDF generation fails for any attachment
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
        String demographicId = (String) session.getAttribute("demographicId");
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

        String[] emailConsent = emailComposeManager.getEmailConsentStatus(loggedInInfo, Integer.parseInt(demographicId));

        String receiverName = demographicManager.getDemographicFormattedName(loggedInInfo, Integer.parseInt(demographicId));
        List<?>[] receiverEmailList = emailComposeManager.getRecipients(loggedInInfo, Integer.parseInt(demographicId));

        List<EmailConfig> senderAccounts = emailComposeManager.getAllSenderAccounts();

        String emailPDFPassword = emailPdfPasswordService.generatePassphrase();
        String emailPDFPasswordClue = EmailPdfPasswordService.DELIVERY_INSTRUCTION;

        List<EmailAttachment> emailAttachmentList = new ArrayList<>();
        try {
            emailAttachmentList.addAll(emailComposeManager.prepareEFormAttachments(loggedInInfo, fdid, attachedEForms));
            emailAttachmentList.addAll(emailComposeManager.prepareEDocAttachments(loggedInInfo, attachedDocuments));
            emailAttachmentList.addAll(emailComposeManager.prepareLabAttachments(loggedInInfo, attachedLabs));
            emailAttachmentList.addAll(emailComposeManager.prepareHRMAttachments(loggedInInfo, attachedHRMDocuments));
            emailAttachmentList.addAll(emailComposeManager.prepareFormAttachments(request, response, attachedForms, Integer.parseInt(demographicId)));
        } catch (PDFGenerationException e) {
            logger.error(e.getMessage(), e);
            return emailComposeError(request, "This eForm (and attachments, if applicable) could not be emailed. \\n\\n" + e.getMessage());
        }
        emailComposeManager.sanitizeAttachments(emailAttachmentList);

        // Set request attributes for JSP (from session and computed values)
        request.setAttribute("transactionType", TransactionType.EFORM);
        request.setAttribute("emailConsentName", emailConsent[0]);
        request.setAttribute("emailConsentStatus", emailConsent[1]);
        request.setAttribute("receiverName", receiverName);
        request.setAttribute("receiverEmailList", receiverEmailList[0]);
        request.setAttribute("invalidReceiverEmailList", receiverEmailList[1]);
        request.setAttribute("senderAccounts", senderAccounts);
        request.setAttribute("emailPDFPassword", emailPDFPassword);
        request.setAttribute("emailPDFPasswordClue", emailPDFPasswordClue);
        request.setAttribute("emailAttachmentList", emailAttachmentList);
        request.setAttribute("senderEmail", senderEmail);
        request.setAttribute("subjectEmail", subjectEmail);
        request.setAttribute("bodyEmail", bodyEmail);
        request.setAttribute("encryptedMessageEmail", encryptedMessageEmail);
        request.setAttribute("emailPatientChartOption", emailPatientChartOption);
        request.setAttribute("demographicId", demographicId);
        request.setAttribute("fdid", session.getAttribute("fdid"));
        request.setAttribute("fid", fid);
        request.setAttribute("openEFormAfterEmail", session.getAttribute("openEFormAfterEmail"));
        request.setAttribute("deleteEFormAfterEmail", session.getAttribute("deleteEFormAfterEmail"));
        request.setAttribute("isEmailEncrypted", session.getAttribute("isEmailEncrypted"));
        request.setAttribute("isEmailAttachmentEncrypted", session.getAttribute("isEmailAttachmentEncrypted"));
        request.setAttribute("isEmailAutoSend", session.getAttribute("isEmailAutoSend"));

        cleanupEmailSessionAttributes(request);
        String emailPDFPasswordToken = storeEmailComposeSubmissionState(
                request, emailPDFPassword, emailPDFPasswordClue, emailAttachmentList);
        request.setAttribute("emailPDFPasswordToken", emailPDFPasswordToken);

        return "compose";
    }

    /**
     * Stores generated PDF passphrase state for one compose form submission.
     *
     * <p>The returned token is bound to the current HTTP session id, while the generated
     * passphrase, delivery instruction, and attachment snapshot stay in a short-lived
     * server-side cache instead of the serializable HTTP session. Storing a new entry also
     * prunes expired entries and caps both the current session and the global cache.</p>
     *
     * @param request HttpServletRequest used to bind the token to the active session
     * @param emailPDFPassword generated PDF passphrase to use when sending
     * @param emailPDFPasswordClue delivery instruction displayed with the compose page
     * @param emailAttachmentList prepared attachment list to bind to the compose token
     * @return opaque token that must be submitted back with the compose form
     * @since 2026-07-14
     */
    public static String storeEmailComposeSubmissionState(
            HttpServletRequest request,
            String emailPDFPassword,
            String emailPDFPasswordClue,
            List<EmailAttachment> emailAttachmentList
    ) {
        return storeEmailComposeSubmissionState(
                request, emailPDFPassword, emailPDFPasswordClue, emailAttachmentList, System.currentTimeMillis());
    }

    static String storeEmailComposeSubmissionState(
            HttpServletRequest request,
            String emailPDFPassword,
            String emailPDFPasswordClue,
            List<EmailAttachment> emailAttachmentList,
            long createdAtMillis
    ) {
        HttpSession session = request.getSession();
        String token = UUID.randomUUID().toString();
        EmailComposeSubmissionState state = new EmailComposeSubmissionState(
                emailPDFPassword,
                emailPDFPasswordClue,
                List.copyOf(emailAttachmentList != null ? emailAttachmentList : List.of()),
                createdAtMillis);

        synchronized (EMAIL_COMPOSE_SUBMISSION_STATES_LOCK) {
            pruneExpiredEmailComposeSubmissionStates(createdAtMillis);
            PENDING_EMAIL_COMPOSE_SUBMISSION_STATES.put(
                    new EmailComposeSubmissionStateKey(session.getId(), token), state);
            trimEmailComposeSubmissionStates(session.getId());
            trimEmailComposeSubmissionStateCache();
        }
        return token;
    }

    /**
     * Consumes the generated compose state for the submitted token.
     *
     * <p>Consumption is one-time: a valid token is removed from the server-side cache before
     * returning its state. Missing, blank, expired, cross-session, or reused tokens return
     * {@code null}. Each consume attempt also prunes expired cached entries.</p>
     *
     * @param request HttpServletRequest containing the compose token parameter
     * @return generated compose state for the current session and token, or {@code null}
     * @since 2026-07-14
     */
    public static EmailComposeSubmissionState consumeEmailComposeSubmissionState(HttpServletRequest request) {
        String token = request.getParameter(EMAIL_PDF_PASSWORD_TOKEN_PARAM);
        if (token == null || token.isBlank()) {
            return null;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }

        synchronized (EMAIL_COMPOSE_SUBMISSION_STATES_LOCK) {
            pruneExpiredEmailComposeSubmissionStates(System.currentTimeMillis());
            return PENDING_EMAIL_COMPOSE_SUBMISSION_STATES.remove(
                    new EmailComposeSubmissionStateKey(session.getId(), token));
        }
    }

    private static void pruneExpiredEmailComposeSubmissionStates(long now) {
        PENDING_EMAIL_COMPOSE_SUBMISSION_STATES.entrySet().removeIf(entry ->
                now - entry.getValue().createdAtMillis() > PENDING_EMAIL_COMPOSE_STATE_MAX_AGE_MILLIS);
    }

    private static void trimEmailComposeSubmissionStates(String sessionId) {
        while (emailComposeSubmissionStateCount(sessionId) > MAX_PENDING_EMAIL_COMPOSE_STATES) {
            EmailComposeSubmissionStateKey oldestKey = oldestEmailComposeSubmissionStateKey(sessionId);
            if (oldestKey == null) {
                return;
            }
            PENDING_EMAIL_COMPOSE_SUBMISSION_STATES.remove(oldestKey);
        }
    }

    private static void trimEmailComposeSubmissionStateCache() {
        while (PENDING_EMAIL_COMPOSE_SUBMISSION_STATES.size()
                > MAX_PENDING_EMAIL_COMPOSE_SUBMISSION_CACHE_STATES) {
            EmailComposeSubmissionStateKey oldestKey = oldestEmailComposeSubmissionStateKey(null);
            if (oldestKey == null) {
                return;
            }
            PENDING_EMAIL_COMPOSE_SUBMISSION_STATES.remove(oldestKey);
        }
    }

    private static int emailComposeSubmissionStateCount(String sessionId) {
        int count = 0;
        for (EmailComposeSubmissionStateKey key : PENDING_EMAIL_COMPOSE_SUBMISSION_STATES.keySet()) {
            if (key.sessionId().equals(sessionId)) {
                count++;
            }
        }
        return count;
    }

    private static EmailComposeSubmissionStateKey oldestEmailComposeSubmissionStateKey(String sessionId) {
        EmailComposeSubmissionStateKey oldestKey = null;
        long oldestCreatedAt = Long.MAX_VALUE;
        for (Map.Entry<EmailComposeSubmissionStateKey, EmailComposeSubmissionState> entry
                : PENDING_EMAIL_COMPOSE_SUBMISSION_STATES.entrySet()) {
            if (sessionId != null && !entry.getKey().sessionId().equals(sessionId)) {
                continue;
            }
            if (entry.getValue().createdAtMillis() < oldestCreatedAt) {
                oldestKey = entry.getKey();
                oldestCreatedAt = entry.getValue().createdAtMillis();
            }
        }
        return oldestKey;
    }

    private record EmailComposeSubmissionStateKey(String sessionId, String token) {
    }

    /**
     * Generated compose state associated with one opaque compose token.
     *
     * @since 2026-07-14
     */
    public record EmailComposeSubmissionState(
            String emailPDFPassword,
            String emailPDFPasswordClue,
            List<EmailAttachment> emailAttachmentList,
            long createdAtMillis
    ) {
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
     * This method is called when email composition preparation fails, typically due to PDF generation
     * errors for attachments. It sets the error message as a request attribute for display on the
     * error page.
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
     * @param errorMessage String the error message to display to the user, typically includes
     *                     the specific exception message from PDFGenerationException
     * @return String the Struts2 result name "eFormError" which maps to the error display page
     * @see io.github.carlos_emr.carlos.utility.PDFGenerationException
     */
    private String emailComposeError(HttpServletRequest request, String errorMessage) {
        request.setAttribute("errorMessage", errorMessage);
        return "eFormError";
    }
}
