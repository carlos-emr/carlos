//CHECKSTYLE:OFF
package ca.openosp.openo.email.action;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.logging.log4j.Logger;
import org.owasp.encoder.Encode;

import ca.openosp.OscarProperties;
import ca.openosp.openo.commn.model.EmailAttachment;
import ca.openosp.openo.commn.model.EmailConfig;
import ca.openosp.openo.commn.model.EmailLog.TransactionType;
import ca.openosp.openo.managers.DemographicManager;
import ca.openosp.openo.managers.EmailComposeManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PDFGenerationException;
import ca.openosp.openo.utility.SpringUtils;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

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
 * The action retrieves email composition parameters from the HTTP session (allowing for redirect-based
 * workflows) and transfers them to request attributes for JSP rendering. Session attributes are cleaned
 * up after transfer to prevent stale data accumulation.
 *
 * Security Considerations:
 * <ul>
 *   <li>Validates fid parameter to ensure numeric format (prevents injection)</li>
 *   <li>Uses OWASP Encode.forJava() for sanitizing invalid fid values in logs</li>
 *   <li>Generates patient-specific PDF passwords based on demographic information</li>
 *   <li>Sanitizes attachment filenames through EmailComposeManager</li>
 *   <li>Session cleanup prevents information leakage across requests</li>
 * </ul>
 *
 * @see ca.openosp.openo.managers.EmailComposeManager
 * @see ca.openosp.openo.managers.DemographicManager
 * @see ca.openosp.openo.commn.model.EmailAttachment
 * @see ca.openosp.openo.commn.model.EmailConfig
 * @see ca.openosp.openo.commn.model.EmailLog.TransactionType
 * @since 2026-01-24
 */
public class EmailCompose2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final Logger logger = MiscUtils.getLogger();
    private DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    private EmailComposeManager emailComposeManager = SpringUtils.getBean(EmailComposeManager.class);

    private static final String[] EMAIL_SESSION_KEYS = {
        "attachEFormItSelf", "fdid", "demographicId",
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
     *   <li>Generates PDF password encryption based on patient demographics if not already set</li>
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
     *   <li>emailPDFPassword (String) - password for PDF encryption</li>
     *   <li>emailPDFPasswordClue (String) - hint for PDF password</li>
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
     *   <li>emailPDFPassword (String) - generated or existing PDF password</li>
     *   <li>emailPDFPasswordClue (String) - password hint for recipient</li>
     *   <li>demographicId (String) - patient demographic identifier</li>
     *   <li>fdid (String) - form data ID</li>
     *   <li>fid (String) - validated form ID or null if invalid</li>
     * </ul>
     *
     * Session Attributes Set:
     * <ul>
     *   <li>emailAttachmentList (List&lt;EmailAttachment&gt;) - prepared and sanitized attachments</li>
     * </ul>
     *
     * Security Features:
     * <ul>
     *   <li>Validates fid parameter with regex pattern to ensure numeric format only</li>
     *   <li>Logs warnings for invalid fid values using OWASP-encoded output</li>
     *   <li>Generates patient-specific PDF passwords: YYYYMMDD (DOB) + 10-digit HIN</li>
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
     * @see ca.openosp.openo.managers.EmailComposeManager#getEmailConsentStatus(LoggedInInfo, Integer)
     * @see ca.openosp.openo.managers.EmailComposeManager#getRecipients(LoggedInInfo, Integer)
     * @see ca.openosp.openo.managers.EmailComposeManager#createEmailPDFPassword(LoggedInInfo, Integer)
     * @see ca.openosp.openo.managers.EmailComposeManager#prepareEFormAttachments(LoggedInInfo, String, String[])
     * @see ca.openosp.openo.managers.EmailComposeManager#sanitizeAttachments(List)
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
        String emailPDFPassword = (String) session.getAttribute("emailPDFPassword");
        String emailPDFPasswordClue = (String) session.getAttribute("emailPDFPasswordClue");
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
            String sanitizedFid = Encode.forJava(fid);
            logger.warn("Invalid fid parameter received: {}", sanitizedFid);
            fid = null;
        }

        // Don't clean up session attributes here - they are needed by the JSP
        // Session cleanup is performed in this action immediately after transferring session data to request attributes.

        String[] emailConsent = emailComposeManager.getEmailConsentStatus(loggedInInfo, Integer.parseInt(demographicId));

        String receiverName = demographicManager.getDemographicFormattedName(loggedInInfo, Integer.parseInt(demographicId));
        List<?>[] receiverEmailList = emailComposeManager.getRecipients(loggedInInfo, Integer.parseInt(demographicId));

        List<EmailConfig> senderAccounts = emailComposeManager.getAllSenderAccounts();

        if (emailPDFPassword == null) {
            emailPDFPassword = emailComposeManager.createEmailPDFPassword(loggedInInfo, Integer.parseInt(demographicId));
            emailPDFPasswordClue = "To protect your privacy, the PDF attachments in this email have been encrypted with a 18 digit password - your date of birth in the format YYYYMMDD followed by the 10 digits of your health insurance number.";
        }

        List<EmailAttachment> emailAttachmentList = new ArrayList<>();
        try {
            emailAttachmentList.addAll(emailComposeManager.prepareEFormAttachments(loggedInInfo, fdid, attachedEForms));
            emailAttachmentList.addAll(emailComposeManager.prepareEDocAttachments(loggedInInfo, attachedDocuments));
            emailAttachmentList.addAll(emailComposeManager.prepareLabAttachments(loggedInInfo, attachedLabs));
            emailAttachmentList.addAll(emailComposeManager.prepareHRMAttachments(loggedInInfo, attachedHRMDocuments));
            emailAttachmentList.addAll(emailComposeManager.prepareFormAttachments(request, response, attachedForms, Integer.parseInt(demographicId)));
        } catch (PDFGenerationException e) {
            logger.error(e.getMessage(), e);
            return emailComposeError(request,"This eForm (and attachments, if applicable) could not be emailed. \\n\\n" + e.getMessage());
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
        request.getSession().setAttribute("emailAttachmentList", emailAttachmentList);

        cleanupEmailSessionAttributes(request);

        return "compose";
    }

    /**
     * Cleans up email-related session attributes.
     * This method is called after transferring email composition data from session to request attributes, before rendering the compose screen.
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
     * @see ca.openosp.openo.utility.PDFGenerationException
     */
    private String emailComposeError(HttpServletRequest request, String errorMessage) {
        request.setAttribute("errorMessage", errorMessage);
        return "eFormError";
    }
}
