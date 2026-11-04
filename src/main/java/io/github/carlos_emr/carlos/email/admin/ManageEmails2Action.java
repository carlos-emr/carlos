package io.github.carlos_emr.carlos.email.admin;

import io.github.carlos_emr.carlos.managers.*;
import org.apache.struts2.ActionSupport;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.documentManager.PdfPreviewCapabilityService;
import io.github.carlos_emr.carlos.email.core.EmailAttachmentStaging;
import io.github.carlos_emr.carlos.email.core.EmailFailureMessage;
import io.github.carlos_emr.carlos.email.core.EmailStatusResult;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LocaleUtils;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.form.JSONUtil;
import io.github.carlos_emr.carlos.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Struts2 action for managing and administering emails in the OpenO EMR system.
 *
 * This action handles the email management interface in the Admin section, providing
 * functionality for viewing, searching, filtering, and resending previously sent patient
 * emails. It supports comprehensive email log management including status tracking,
 * sender filtering, date range queries, and patient-specific email history.
 *
 * <p>Key healthcare functionalities:</p>
 * <ul>
 *   <li>Email log retrieval with multi-criteria filtering (status, sender, date range, patient)</li>
 *   <li>Email resend capability with full attachment and encryption reconstruction</li>
 *   <li>Support for various document types (eForms, eDocs, Labs, HRM reports, clinical forms)</li>
 *   <li>Email status management and resolution tracking</li>
 *   <li>Patient consent verification and email validation</li>
 * </ul>
 *
 * <p>This is a method-based Struts2 action following the 2Action pattern, routing
 * requests via the "method" parameter to different handler methods. The main entry
 * point {@link #execute()} delegates to specialized methods based on the requested operation.</p>
 *
 * <p>Security: All operations respect the _email security privilege and maintain
 * audit trails through the LoggedInInfo context.</p>
 *
 * @see EmailManager
 * @see EmailComposeManager
 * @see EmailLog
 * @see EmailAttachment
 * @see DocumentAttachmentManager
 * @since 2026-01-24
 */
public class ManageEmails2Action extends ActionSupport {
    private static final String EMAIL_SECURITY_OBJECT = "_email";
    private static final String MISSING_EMAIL_PRIVILEGE_MESSAGE =
            "missing required sec object (" + EMAIL_SECURITY_OBJECT + ")";
    private static final String ERROR_MESSAGE_KEY = "errorMessage";
    private static final String INVALID_EMAIL_LOG_ID_MESSAGE = "Invalid email log id";
    private static final String EMAIL_LOG_NOT_FOUND_MESSAGE = "Email log not found";

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();
    private static final Logger logger = MiscUtils.getLogger();

    private final DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    private final EmailComposeManager emailComposeManager = SpringUtils.getBean(EmailComposeManager.class);
    private final EmailManager emailManager = SpringUtils.getBean(EmailManager.class);
    private final DocumentAttachmentManager documentAttachmentManager = SpringUtils.getBean(DocumentAttachmentManager.class);
    private final FormsManager formsManager = SpringUtils.getBean(FormsManager.class);
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final PdfPreviewCapabilityService pdfPreviewCapabilityService =
            SpringUtils.getBean(PdfPreviewCapabilityService.class);

    /**
     * Main entry point for the ManageEmails2Action, routing requests to appropriate handler methods.
     *
     * This method examines the "method" parameter from the HTTP request and delegates to
     * the corresponding method handler. Supported methods include "fetchEmails" for retrieving
     * email logs based on search criteria, "setResolved" for resolving a failed email, and
     * "resendEmail" for preparing a previously sent email for resending.
     *
     * If no method parameter is provided or the method is not recognized, defaults to displaying
     * the email management interface via {@link #showEmailManager()}.
     *
     * @return String Struts2 result name ("show", "emailstatus", "compose", or null for errors);
     *         {@code NONE} ("none") for a direct Resolve response
     * @throws SecurityException if a required {@code _email} READ or WRITE privilege is missing
     * @see #fetchEmails()
     * @see #resendEmail()
     * @see #showEmailManager()
     */
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, EMAIL_SECURITY_OBJECT, SecurityInfoManager.READ, null)) {
            throw new SecurityException(MISSING_EMAIL_PRIVILEGE_MESSAGE);
        }
        String mtd = request.getParameter("method");
        if ("fetchEmails".equals(mtd)) {
            return fetchEmails();
        } else if ("resendEmail".equals(mtd)) {
            return resendEmail();
        } else if ("setResolved".equals(mtd)) {
            setResolved();
            return NONE;
        }

        return showEmailManager();
    }

    /**
     * Displays the email management interface with available email statuses and sender accounts.
     *
     * This method prepares the initial view for the email management page by populating
     * request attributes with all possible email statuses (SUCCESS, FAILED, RESOLVED)
     * and the list of configured sender email accounts. This data is used to populate
     * the filter dropdowns on the email management interface.
     *
     * The email management interface allows administrators to search and filter sent emails
     * by various criteria including status, sender, date range, and patient.
     *
     * @return String Struts2 result name "show" to display the email management page
     * @see EmailStatus
     * @see EmailConfig
     */
    public String showEmailManager() {
        request.setAttribute("emailStatusList", EmailStatus.values());
        request.setAttribute("senderAccountList", emailComposeManager.getAllSenderAccounts());
        return "show";
    }

    /**
     * Retrieves and filters email logs based on specified search criteria.
     *
     * This method is invoked from the 'Admin > Emails > Manage Emails' page when the user
     * clicks the 'Fetch Emails' button. It supports multi-criteria filtering including
     * email status, sender email address, date range (begin/end), and specific patient
     * demographic number.
     *
     * Special handling for default values: The value '-1' in dropdown fields (emailStatus
     * and senderEmailAddress) represents 'All' and is converted to null to retrieve all
     * records without that filter applied. Empty or "null" string values for demographic_no
     * are also normalized to null.
     *
     * The method delegates to {@link EmailManager#getEmailStatusByDateDemographicSenderStatus}
     * to retrieve matching email records and populates the request with the results for
     * display on the email status results page.
     *
     * @return String Struts2 result name "emailstatus" to display filtered email results
     * @see EmailManager#getEmailStatusByDateDemographicSenderStatus
     * @see EmailStatusResult
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String fetchEmails() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String emailStatus = request.getParameter("emailStatus");
        String senderEmailAddress = request.getParameter("senderEmailAddress");
        String dateBeginStr = request.getParameter("dateBegin");
        String dateEndStr = request.getParameter("dateEnd");
        String demographic_no = request.getParameter("demographic_no");

        if (emailStatus != null && emailStatus.equals("-1")) {
            emailStatus = null;
        }
        if (senderEmailAddress != null && senderEmailAddress.equals("-1")) {
            senderEmailAddress = null;
        }
        if (demographic_no != null && ("null".equalsIgnoreCase(demographic_no) || "".equals(demographic_no))) {
            demographic_no = null;
        }

        try {
            List<EmailStatusResult> emailStatusResults = emailManager.getEmailStatusByDateDemographicSenderStatus(loggedInInfo, dateBeginStr, dateEndStr, demographic_no, senderEmailAddress, emailStatus);
            request.setAttribute("emailStatusResults", emailStatusResults);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid email search filters", e);
            request.setAttribute("emailValidationError", e.getMessage());
            request.setAttribute("emailStatusResults", List.of());
        }

        return "emailstatus";
    }

    /**
     * Marks a specific email log entry as resolved.
     *
     * Only FAILED rows transition to RESOLVED, clearing the error and preserving the stored send time.
     * Missing logs return HTTP 404; any refreshed status other than RESOLVED returns HTTP 409.
     * Repeated resolution of a RESOLVED log succeeds without another update.
     *
     * Invalid log IDs return a JSON error without modifying data. Eligible rows are updated
     * via {@link EmailManager#updateEmailStatus}; the refreshed stored state determines the response.
     * Later transport outcomes can supersede a resolution.
     * Only POST is accepted so the mutation is covered by CSRFGuard.
     *
     * @throws SecurityException if the user lacks {@code _email} WRITE privilege
     * @see EmailManager#updateEmailStatus
     * @see EmailStatus#RESOLVED
     */
    public void setResolved() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, EMAIL_SECURITY_OBJECT, SecurityInfoManager.WRITE, null)) {
            throw new SecurityException(MISSING_EMAIL_PRIVILEGE_MESSAGE);
        }
        if (!"POST".equals(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            response.setHeader("Allow", "POST");
            return;
        }
        String emailLogId = request.getParameter("logId");
        if (!StringUtils.isInteger(emailLogId)) {
            JSONUtil.errorResponse(response, ERROR_MESSAGE_KEY, INVALID_EMAIL_LOG_ID_MESSAGE);
            return;
        }
        EmailLog emailLog = emailManager.updateEmailStatus(loggedInInfo, Integer.parseInt(emailLogId), EmailStatus.RESOLVED, null);
        if (emailLog == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            JSONUtil.jsonResponse(response, ERROR_MESSAGE_KEY, EMAIL_LOG_NOT_FOUND_MESSAGE);
        } else if (emailLog.getStatus() != EmailStatus.RESOLVED) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            JSONUtil.jsonResponse(response, ERROR_MESSAGE_KEY, "Only failed emails can be resolved");
        }
    }

    /**
     * Prepares a previously sent email for resending by reconstructing its full state.
     *
     * This method is invoked from the 'Admin > Emails > Manage Emails' section when a user
     * clicks the 'Resend' button on a specific email log entry. It retrieves the original
     * email details and reconstructs all associated data including attachments, encryption
     * settings, patient information, and sender configuration.
     *
     * The method performs the following operations:
     * <ul>
     *   <li>Validates the email log ID parameter</li>
     *   <li>Retrieves the original email log via {@link EmailComposeManager#prepareEmailForResend}</li>
     *   <li>Refreshes all email attachments by re-rendering PDF documents</li>
     *   <li>Retrieves patient consent status and email addresses</li>
     *   <li>Populates request attributes for the email compose page</li>
     *   <li>Stages the attachments under this window's own one-time key, bound to the email's
     *       patient, for the send (#4425)</li>
     * </ul>
     *
     * If PDF regeneration fails for any attachment, an error message is set and the user
     * is advised to create a new email instead of resending. The method returns null in
     * case of an invalid log ID or a missing email log; missing logs return HTTP 404.
     *
     * All email data including encryption settings, password protection, chart display options,
     * and additional parameters are preserved from the original email for potential modification
     * before resending.
     *
     * @return String Struts2 result name "compose" to display the email composition page, or null for invalid/missing logs
     * @throws SecurityException if the user lacks {@code _email} READ privilege
     * @see EmailComposeManager#prepareEmailForResend
     * @see #refreshEmailAttachments
     * @see EmailLog
     * @see TransactionType#DIRECT
     */
    public String resendEmail() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String emailLogId = request.getParameter("logId");
        if (!StringUtils.isInteger(emailLogId)) {
            JSONUtil.errorResponse(response, ERROR_MESSAGE_KEY, INVALID_EMAIL_LOG_ID_MESSAGE);
            return null;
        }

        /*
         * The purpose of the EmailComposeManager is to help prepare all necessary data to display on the emailCompose.jsp page.
         */
        EmailLog emailLog = emailComposeManager.prepareEmailForResend(loggedInInfo, Integer.parseInt(emailLogId));
        if (emailLog == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            JSONUtil.jsonResponse(response, ERROR_MESSAGE_KEY, EMAIL_LOG_NOT_FOUND_MESSAGE);
            return null;
        }
        List<EmailAttachment> emailAttachmentList = new ArrayList<>();
        boolean attachmentsRefreshed = false;
        try {
            emailAttachmentList = refreshEmailAttachments(request, response, emailLog);
            attachmentsRefreshed = true;
        } catch (SecurityException e) {
            // A refusal is not a preparation failure: it goes to the access-denied page.
            throw e;
        } catch (PDFGenerationException | RuntimeException e) {
            // As for an eForm email: the exception's text stays out of the page and the log.
            String reference = EmailFailureMessage.newReference();
            logger.error("Unable to prepare attachments to resend an email; causeType={}, reference={}{}",
                    e.getClass().getName(), reference, LogSafe.exceptionTrace(e));
            request.setAttribute("emailErrorMessage", EmailFailureMessage.format(LocaleUtils.resolveBundleLocale(request),
                    EmailFailureMessage.RESEND_ATTACHMENTS_KEY, reference));
            request.setAttribute("isEmailError", true);
        }

        int demographicNo = emailLog.getDemographic().getDemographicNo();
        String[] emailConsent = emailComposeManager.getEmailConsentStatus(loggedInInfo, demographicNo);
        String receiverName = demographicManager.getDemographicFormattedName(loggedInInfo, demographicNo);
        List<?>[] receiverEmailList = emailComposeManager.getRecipients(loggedInInfo, demographicNo);
        List<EmailConfig> senderAccounts = emailComposeManager.getAllSenderAccounts();

        request.setAttribute("demographicId", demographicNo);
        request.setAttribute("transactionType", TransactionType.DIRECT);
        request.setAttribute("emailConsentName", emailConsent[0]);
        request.setAttribute("emailConsentStatus", emailConsent[1]);
        request.setAttribute("receiverName", receiverName);
        request.setAttribute("receiverEmailList", receiverEmailList[0]);
        request.setAttribute("invalidReceiverEmailList", receiverEmailList[1]);
        request.setAttribute("senderAccounts", senderAccounts);
        request.setAttribute("senderConfigId", emailLog.getEmailConfig() != null ? emailLog.getEmailConfig().getId() : null);
        request.setAttribute("senderEmail", emailLog.getFromEmail());
        request.setAttribute("subjectEmail", emailLog.getSubject());
        request.setAttribute("bodyEmail", emailLog.getBody());
        request.setAttribute("encryptedMessageEmail", emailLog.getEncryptedMessage());
        request.setAttribute("emailPDFPassword", emailLog.getPassword());
        request.setAttribute("emailPDFPasswordClue", emailLog.getPasswordClue());
        request.setAttribute("isEmailEncrypted", emailLog.getIsEncrypted());
        request.setAttribute("isEmailAttachmentEncrypted", emailLog.getIsAttachmentEncrypted());
        request.setAttribute("emailPatientChartOption", emailLog.getChartDisplayOption().getValue());
        request.setAttribute("emailAdditionalParams", emailLog.getAdditionalParams());
        // Stage the resend's attachments under this window's own key, bound to the logged email's
        // patient, exactly as a compose does (#4425). A session-wide list would let a compose or
        // resend in another window replace what this window sends. A failed refresh stages nothing:
        // its page only reports the error and closes, and without a key any send is refused.
        request.getSession().removeAttribute("emailAttachmentList");
        if (attachmentsRefreshed) {
            EmailAttachmentStaging.Staged stagedAttachments =
                    EmailAttachmentStaging.stage(request.getSession(), demographicNo, emailAttachmentList);
            request.setAttribute("emailAttachmentList", stagedAttachments.prepared().attachments());
            request.setAttribute(EmailAttachmentStaging.KEY_ATTRIBUTE, stagedAttachments.key());
        }

        return "compose";
    }

    /**
     * Refreshes email attachments by re-rendering documents to PDF format.
     *
     * This private helper method processes all attachments associated with an email log,
     * regenerating PDF versions of various document types. It is used when resending emails
     * to ensure that all attachments are current and accessible.
     *
     * The method handles the following document types:
     * <ul>
     *   <li>EFORM - Electronic forms rendered to PDF</li>
     *   <li>DOC - Electronic documents rendered to PDF</li>
     *   <li>LAB - Laboratory results rendered to PDF</li>
     *   <li>HRM - Hospital Report Manager reports rendered to PDF</li>
     *   <li>FORM - Clinical forms rendered to PDF</li>
     * </ul>
     *
     * For each attachment, the method:
     * <ol>
     *   <li>Renders the document to PDF using the appropriate manager</li>
     *   <li>Updates the attachment's file path to the newly generated PDF</li>
     *   <li>Calculates and updates the file size</li>
     * </ol>
     *
     * Security: Verifies the user has READ privilege for _email before processing.
     *
     * @param request HttpServletRequest containing the user session with LoggedInInfo
     * @param response HttpServletResponse for potential error handling
     * @param emailLog EmailLog containing the list of attachments to refresh
     * @return List<EmailAttachment> the updated list of email attachments with refreshed PDF paths and sizes
     * @throws PDFGenerationException if any document cannot be rendered to PDF
     * @throws SecurityException if the user lacks the _email read privilege
     * @see DocumentAttachmentManager#renderDocument
     * @see FormsManager#renderForm
     * @see DocumentType
     */
    private List<EmailAttachment> refreshEmailAttachments(HttpServletRequest request, HttpServletResponse response, EmailLog emailLog) throws PDFGenerationException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, EMAIL_SECURITY_OBJECT, SecurityInfoManager.READ, null)) {
            throw new SecurityException(MISSING_EMAIL_PRIVILEGE_MESSAGE);
        }

        List<EmailAttachment> emailAttachmentList = emailLog.getEmailAttachments();
        for (EmailAttachment emailAttachment : emailAttachmentList) {
            switch (emailAttachment.getDocumentType()) {
                case EFORM:
                    Path eFormPDFPath = documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.EFORM, emailAttachment.getDocumentId());
                    emailAttachment.setFilePath(eFormPDFPath.toString());
                    emailAttachment.setFileSize(emailComposeManager.getFileSize(eFormPDFPath));
                    break;
                case DOC:
                    // A PDF document renders in place; the preview needs an application temp copy.
                    Path eDocPDFPath = emailComposeManager.applicationTempFile(
                            documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.DOC, emailAttachment.getDocumentId()));
                    emailAttachment.setFilePath(eDocPDFPath.toString());
                    emailAttachment.setFileSize(emailComposeManager.getFileSize(eDocPDFPath));
                    break;
                case LAB:
                    Path labPDFPath = documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.LAB, emailAttachment.getDocumentId());
                    emailAttachment.setFilePath(labPDFPath.toString());
                    emailAttachment.setFileSize(emailComposeManager.getFileSize(labPDFPath));
                    break;
                case HRM:
                    Path hrmPDFPath = documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.HRM, emailAttachment.getDocumentId());
                    emailAttachment.setFilePath(hrmPDFPath.toString());
                    emailAttachment.setFileSize(emailComposeManager.getFileSize(hrmPDFPath));
                    break;
                case FORM:
                    Path formPDFPath = formsManager.renderForm(request, response, emailAttachment.getDocumentId(), emailLog.getDemographic().getDemographicNo());
                    emailAttachment.setFilePath(formPDFPath.toString());
                    emailAttachment.setFileSize(emailComposeManager.getFileSize(formPDFPath));
                    break;
                default:
                    break;
            }
            if (emailAttachment.getFilePath() != null && !emailAttachment.getFilePath().isBlank()) {
                emailAttachment.setPreviewToken(pdfPreviewCapabilityService.issue(
                        request, loggedInInfo, Path.of(emailAttachment.getFilePath())));
            }
        }
        return emailAttachmentList;
    }
}
