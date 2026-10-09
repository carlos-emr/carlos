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
import io.github.carlos_emr.carlos.email.core.EmailAttachmentSettings;
import io.github.carlos_emr.carlos.email.core.EmailAttachmentStaging;
import io.github.carlos_emr.carlos.email.core.EmailComposeStaging;
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
 * password-protected PDF attachments based on patient demographic data.
 *
 * Session Management:
 * The eForm save stages one immutable draft in the HTTP session under a one-time key and redirects
 * here with that key (#4101). This action takes exactly that draft, once, and transfers it to request
 * attributes for JSP rendering, so two windows that save close together each open their own email.
 * The attachments it prepares are staged the same way, under a new one-time key bound to the draft's
 * patient that the page posts back to the send (#4425), so one window's compose cannot change what
 * another window sends.
 *
 * Security Considerations:
 * <ul>
 *   <li>Validates fid parameter to ensure numeric format (prevents injection)</li>
 *   <li>Uses log-safe sanitization for invalid fid values in logs</li>
 *   <li>Generates patient-specific PDF passwords based on demographic information</li>
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
    private PdfPreviewCapabilityService pdfPreviewCapabilityService =
            SpringUtils.getBean(PdfPreviewCapabilityService.class);

    private static final String[] EMAIL_SESSION_KEYS = {
        "attachEFormItSelf", "fdid", "demographicId",
        "emailPDFPassword", "emailPDFPasswordClue",
        "attachedDocuments", "attachedLabs", "attachedForms",
        "attachedEForms", "attachedHRMDocuments",
        "deleteEFormAfterEmail", "isEmailEncrypted",
        "isEmailAttachmentEncrypted", "isEmailAutoSend",
        "openEFormAfterEmail", "senderEmail", "subjectEmail",
        "bodyEmail", "encryptedMessageEmail",
        "emailPatientChartOption",
        // The session-wide attachment list that versions before #4425 kept.
        "emailAttachmentList"
    };

    /**
     * Result for a request whose key finds no usable draft: missing, unknown, already used, or dropped
     * for a newer save (#4101). The eForm error page needs an eForm to show anything, and there is none
     * here, so this has its own page, worded from the bundle, naming no patient, eForm or key.
     */
    static final String DRAFT_EXPIRED_RESULT = "draftExpired";


    /**
     * Executes the default action for email composition.
     *
     * This method serves as the main entry point for the Struts2 action and delegates to
     * prepareComposeEFormMailer() to handle the email composition preparation logic.
     *
     * @return String the Struts2 result name: "compose" for successful preparation,
     *         {@link #DRAFT_EXPIRED_RESULT} if there is no usable draft for the window's key, or
     *         "eFormError" if PDF generation fails
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
     *   <li>Takes the draft the eForm save staged under the request's one-time key (#4101)</li>
     *   <li>Validates the draft's form ID (fid) for numeric format to prevent injection</li>
     *   <li>Retrieves patient email consent status and validates consent settings</li>
     *   <li>Fetches patient demographic information for recipient name display</li>
     *   <li>Retrieves and validates recipient email addresses (separates valid/invalid)</li>
     *   <li>Loads available sender email account configurations</li>
     *   <li>Generates PDF password encryption based on patient demographics if not already set</li>
     *   <li>Prepares all attachment types: eForms, eDocuments, labs, forms, HRM documents</li>
     *   <li>Sanitizes attachment filenames for security</li>
     *   <li>Transfers the draft's values to request attributes for JSP rendering</li>
     *   <li>Stages the prepared attachments under this window's own key for the send (#4425)</li>
     * </ol>
     *
     * Session State Consumed:
     * The eForm save stages an {@link EmailComposeStaging.Draft} (template id, patient, message,
     * options and attachment selections) under a one-time key passed as the {@code draft}
     * parameter. This takes exactly that draft, once, and reads nothing else from the session, so
     * another window's save cannot change it. A missing, unknown, reused or dropped key shows the
     * "draft expired" page ({@link #DRAFT_EXPIRED_RESULT}), never another window's draft. If preparing the
     * attachments fails, the draft goes back under its key, so refreshing retries.
     *
     * Request Parameters:
     * <ul>
     *   <li>draft (String) - the one-time key of the draft the eForm save staged</li>
     *   <li>fid (String, optional) - read by the eForm error page this may forward to; the compose
     *       itself uses the template id carried by the draft</li>
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
     *   <li>emailPDFPassword (String) - generated or existing PDF password</li>
     *   <li>emailPDFPasswordClue (String) - password hint for recipient</li>
     *   <li>demographicId (String) - patient demographic identifier</li>
     *   <li>fdid (String) - form data ID</li>
     *   <li>fid (String) - validated form ID or null if invalid</li>
     *   <li>emailAttachmentList (List&lt;EmailAttachment&gt;) - prepared and sanitized attachments, for display</li>
     *   <li>emailAttachmentKey (String) - the one-time key the send uses to take this window's attachments</li>
     * </ul>
     *
     * Session State Written:
     * The prepared attachments are staged in {@link EmailAttachmentStaging} under a new one-time key,
     * bound to this draft's patient (#4425). Nothing session-wide is overwritten, so composing for
     * another patient in another window cannot change what this window sends.
     *
     * Security Features:
     * <ul>
     *   <li>Validates fid parameter with regex pattern to ensure numeric format only</li>
     *   <li>Logs warnings for invalid fid values using OWASP-encoded output</li>
     *   <li>Generates patient-specific PDF passwords: YYYYMMDD (DOB) + 10-digit HIN</li>
     *   <li>Sanitizes all attachment filenames to prevent path traversal attacks</li>
     *   <li>Verifies patient email consent before allowing composition</li>
     *   <li>Reads one immutable draft per window, so overlapping saves cannot mix two patients</li>
     * </ul>
     *
     * Error Handling:
     * If PDF generation fails for any attachment (eForm, document, lab, form, HRM), the method
     * returns the "eFormError" result with a descriptive error message. This prevents incomplete
     * emails from being composed when required attachments cannot be generated. Without a usable
     * draft for the request's key, it returns {@link #DRAFT_EXPIRED_RESULT} with HTTP 410.
     *
     * @return String the Struts2 result name: "compose" for successful preparation,
     *         {@link #DRAFT_EXPIRED_RESULT} if there is no usable draft for the key, or "eFormError" if
     *         PDF generation fails for any attachment
     * @see io.github.carlos_emr.carlos.managers.EmailComposeManager#getEmailConsentStatus(LoggedInInfo, Integer)
     * @see io.github.carlos_emr.carlos.managers.EmailComposeManager#getRecipients(LoggedInInfo, Integer)
     * @see io.github.carlos_emr.carlos.managers.EmailComposeManager#createEmailPDFPassword(LoggedInInfo, Integer)
     * @see io.github.carlos_emr.carlos.managers.EmailComposeManager#prepareEFormAttachments(LoggedInInfo, String, String[])
     * @see io.github.carlos_emr.carlos.managers.EmailComposeManager#sanitizeAttachments(List)
     * @see #cleanupEmailSessionAttributes(HttpServletRequest)
     * @see #emailComposeError(HttpServletRequest, String)
     */
    public String prepareComposeEFormMailer() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // Take exactly the draft the eForm save staged for this window (#4101). Everything below
        // reads this one immutable snapshot and never the session again, so another window's save
        // cannot change what this window shows or sends, even while its attachments are rendered.
        HttpSession session = request.getSession();
        String draftKey = request.getParameter(EmailComposeStaging.DRAFT_PARAMETER);
        EmailComposeStaging.Draft draft = EmailComposeStaging.take(session, draftKey);
        if (draft == null) {
            return draftExpired();
        }
        // Fields that versions before #4101 staged one by one; nothing reads them now.
        cleanupEmailSessionAttributes(request);
        // After a failed preparation, the same window gets its draft back under its own key, so a
        // refresh retries.
        Runnable restoreDraft = () -> EmailComposeStaging.restore(session, draftKey, draft);

        EmailAttachmentSettings staged = draft.settings();
        boolean attachEFormItSelf = staged.attachEFormItSelf();
        String fdid = attachEFormItSelf ? staged.fdid() : "";
        String demographicId = staged.demographicNo();
        String fid = draft.fid();
        String emailPDFPassword = staged.emailPDFPassword();
        String emailPDFPasswordClue = staged.emailPDFPasswordClue();
        String[] attachedDocuments = staged.attachedDocuments();
        String[] attachedLabs = staged.attachedLabs();
        String[] attachedForms = staged.attachedForms();
        String[] attachedEForms = staged.attachedEForms();
        String[] attachedHRMDocuments = staged.attachedHRMDocuments();

        Integer demographicNo = parseDemographicNo(demographicId);
        if (demographicNo == null) {
            // The eForm save only stages a draft for a patient, so this should not happen; staff see
            // the expired page, and the log says why (no ids: the value itself is not trusted).
            logger.warn("eForm email draft had no usable patient number; showing the draft-expired page");
            return draftExpired();
        }

        // Validate the draft's fid is numeric if provided
        if (fid != null && !fid.matches("\\d+")) {
            if (logger.isWarnEnabled()) {
                String sanitizedFid = LogSafe.sanitize(fid);
                logger.warn("Invalid fid parameter received: {}", sanitizedFid);
            }
            fid = null;
        }

        String[] emailConsent = emailComposeManager.getEmailConsentStatus(loggedInInfo, demographicNo);

        String receiverName = demographicManager.getDemographicFormattedName(loggedInInfo, demographicNo);
        List<?>[] receiverEmailList = emailComposeManager.getRecipients(loggedInInfo, demographicNo);

        List<EmailConfig> senderAccounts = emailComposeManager.getAllSenderAccounts();

        if (emailPDFPassword == null) {
            emailPDFPassword = emailComposeManager.createEmailPDFPassword(loggedInInfo, demographicNo);
            emailPDFPasswordClue = "To protect your privacy, the PDF attachments in this email have been encrypted with a 18 digit password - your date of birth in the format YYYYMMDD followed by the 10 digits of your health insurance number.";
        }

        List<EmailAttachment> emailAttachmentList = new ArrayList<>();
        try {
            emailAttachmentList.addAll(emailComposeManager.prepareEFormAttachments(loggedInInfo, fdid, attachedEForms));
            emailAttachmentList.addAll(emailComposeManager.prepareEDocAttachments(loggedInInfo, attachedDocuments));
            emailAttachmentList.addAll(emailComposeManager.prepareLabAttachments(loggedInInfo, attachedLabs));
            emailAttachmentList.addAll(emailComposeManager.prepareHRMAttachments(loggedInInfo, attachedHRMDocuments));
            emailAttachmentList.addAll(emailComposeManager.prepareFormAttachments(request, response, attachedForms, demographicNo));
            emailComposeManager.sanitizeAttachments(emailAttachmentList);
            for (EmailAttachment attachment : emailAttachmentList) {
                attachment.setPreviewToken(pdfPreviewCapabilityService.issue(
                        request, loggedInInfo, java.nio.file.Path.of(attachment.getFilePath())));
            }
        } catch (PDFGenerationException | RuntimeException e) {
            logger.error(e.getMessage(), e);
            restoreDraft.run();
            return emailComposeError(request, "This eForm (and attachments, if applicable) could not be emailed. \\n\\n" + e.getMessage());
        }

        // Set request attributes for JSP (from the draft and computed values)
        request.setAttribute("transactionType", TransactionType.EFORM);
        request.setAttribute("emailConsentName", emailConsent[0]);
        request.setAttribute("emailConsentStatus", emailConsent[1]);
        request.setAttribute("receiverName", receiverName);
        request.setAttribute("receiverEmailList", receiverEmailList[0]);
        request.setAttribute("invalidReceiverEmailList", receiverEmailList[1]);
        request.setAttribute("senderAccounts", senderAccounts);
        request.setAttribute("emailPDFPassword", emailPDFPassword);
        request.setAttribute("emailPDFPasswordClue", emailPDFPasswordClue);
        request.setAttribute("senderEmail", staged.senderEmail());
        request.setAttribute("subjectEmail", staged.subjectEmail());
        request.setAttribute("bodyEmail", staged.bodyEmail());
        request.setAttribute("encryptedMessageEmail", staged.encryptedMessageEmail());
        request.setAttribute("emailPatientChartOption", staged.emailPatientChartOption());
        request.setAttribute("demographicId", demographicId);
        request.setAttribute("fdid", staged.fdid());
        request.setAttribute("fid", fid);
        request.setAttribute("openEFormAfterEmail", staged.openAfterEmail());
        request.setAttribute("deleteEFormAfterEmail", staged.deleteEFormAfterEmail());
        request.setAttribute("isEmailEncrypted", staged.isEmailEncrypted());
        request.setAttribute("isEmailAttachmentEncrypted", staged.isEmailAttachmentEncrypted());
        request.setAttribute("isEmailAutoSend", staged.isEmailAutoSend());
        // Stage this window's attachments under its own key, bound to this patient (#4425). The send
        // takes exactly this entry, so another window's compose or resend cannot change what this
        // window sends.
        EmailAttachmentStaging.Staged stagedAttachments =
                EmailAttachmentStaging.stage(session, demographicNo, emailAttachmentList);
        request.setAttribute("emailAttachmentList", stagedAttachments.prepared().attachments());
        request.setAttribute(EmailAttachmentStaging.KEY_ATTRIBUTE, stagedAttachments.key());

        return "compose";
    }

    /** @return the patient number, or null when the draft has none that parses */
    private static Integer parseDemographicNo(String demographicId) {
        if (demographicId == null) {
            return null;
        }
        try {
            return Integer.valueOf(demographicId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Removes the compose fields that versions before #4101 staged as separate session attributes.
     * Nothing writes them any more, so clearing them cannot affect another window's keyed draft.
     *
     * @param request the HTTP servlet request containing the session to clean up
     * @since 2025-01-18
     */
    protected static void cleanupEmailSessionAttributes(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }

        for (String key : EMAIL_SESSION_KEYS) {
            session.removeAttribute(key);
        }
    }

    /**
     * Shows the "draft expired" page: this window's one-time draft is gone, so 410 Gone. The page
     * tells staff to close the window and email the eForm again.
     *
     * @return {@link #DRAFT_EXPIRED_RESULT}
     */
    private String draftExpired() {
        response.setStatus(HttpServletResponse.SC_GONE);
        return DRAFT_EXPIRED_RESULT;
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
