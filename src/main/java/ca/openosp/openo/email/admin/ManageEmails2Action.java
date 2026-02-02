//CHECKSTYLE:OFF
package ca.openosp.openo.email.admin;

import ca.openosp.openo.managers.*;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.commn.model.EmailAttachment;
import ca.openosp.openo.commn.model.EmailConfig;
import ca.openosp.openo.commn.model.EmailLog;
import ca.openosp.openo.commn.model.EmailLog.EmailStatus;
import ca.openosp.openo.commn.model.EmailLog.TransactionType;
import ca.openosp.openo.commn.model.enumerator.DocumentType;
import ca.openosp.openo.documentManager.DocumentAttachmentManager;
import ca.openosp.openo.email.core.EmailStatusResult;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PDFGenerationException;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.form.JSONUtil;
import ca.openosp.openo.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();
    private static final Logger logger = MiscUtils.getLogger();

    private final DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    private final EmailComposeManager emailComposeManager = SpringUtils.getBean(EmailComposeManager.class);
    private final EmailManager emailManager = SpringUtils.getBean(EmailManager.class);
    private final DocumentAttachmentManager documentAttachmentManager = SpringUtils.getBean(DocumentAttachmentManager.class);
    private final FormsManager formsManager = SpringUtils.getBean(FormsManager.class);
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Main entry point for the ManageEmails2Action, routing requests to appropriate handler methods.
     *
     * This method examines the "method" parameter from the HTTP request and delegates to
     * the corresponding method handler. Supported methods include "fetchEmails" for retrieving
     * email logs based on search criteria, and "resendEmail" for preparing a previously sent
     * email for resending.
     *
     * If no method parameter is provided or the method is not recognized, defaults to displaying
     * the email management interface via {@link #showEmailManager()}.
     *
     * @return String Struts2 result name ("show", "emailstatus", "compose", or null for errors)
     * @see #fetchEmails()
     * @see #resendEmail()
     * @see #showEmailManager()
     */
    public String execute() {
        String mtd = request.getParameter("method");
        if ("fetchEmails".equals(mtd)) {
            return fetchEmails();
        } else if ("resendEmail".equals(mtd)) {
            return resendEmail();
        }

        return showEmailManager();
    }

    /**
     * Displays the email management interface with available email statuses and sender accounts.
     *
     * This method prepares the initial view for the email management page by populating
     * request attributes with all possible email statuses (SENT, FAILED, PENDING, RESOLVED)
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

        List<EmailStatusResult> emailStatusResults = emailManager.getEmailStatusByDateDemographicSenderStatus(loggedInInfo, dateBeginStr, dateEndStr, demographic_no, senderEmailAddress, emailStatus);
        request.setAttribute("emailStatusResults", emailStatusResults);

        return "emailstatus";
    }

    /**
     * Marks a specific email log entry as resolved.
     *
     * This method updates the status of an email log to RESOLVED, typically used when
     * an administrator has addressed a failed or problematic email. The method validates
     * the provided log ID to ensure it is a valid integer before processing.
     *
     * If the log ID is invalid (not an integer), the method returns a JSON error response
     * to the client and does not modify any data. Upon successful validation, the email
     * status is updated via {@link EmailManager#updateEmailStatus}.
     *
     * @see EmailManager#updateEmailStatus
     * @see EmailStatus#RESOLVED
     */
    public void setResolved() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String emailLogId = request.getParameter("logId");
        if (!StringUtils.isInteger(emailLogId)) {
            JSONUtil.errorResponse(response, "errorMessage", "Invalid email log id");
            return;
        }
        emailManager.updateEmailStatus(loggedInInfo, Integer.parseInt(emailLogId), EmailStatus.RESOLVED, null);
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
     * </ul>
     *
     * If PDF regeneration fails for any attachment, an error message is set and the user
     * is advised to create a new email instead of resending. The method returns null in
     * case of validation errors (invalid log ID).
     *
     * All email data including encryption settings, password protection, chart display options,
     * and additional parameters are preserved from the original email for potential modification
     * before resending.
     *
     * @return String Struts2 result name "compose" to display the email composition page, or null if validation fails
     * @see EmailComposeManager#prepareEmailForResend
     * @see #refreshEmailAttachments
     * @see EmailLog
     * @see TransactionType#DIRECT
     */
    public String resendEmail() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String emailLogId = request.getParameter("logId");
        if (!StringUtils.isInteger(emailLogId)) {
            JSONUtil.errorResponse(response, "errorMessage", "Invalid email log id");
            return null;
        }

        /*
         * The purpose of the EmailComposeManager is to help prepare all necessary data to display on the emailCompose.jsp page.
         */
        EmailLog emailLog = emailComposeManager.prepareEmailForResend(loggedInInfo, Integer.parseInt(emailLogId));
        List<EmailAttachment> emailAttachmentList = new ArrayList<>();
        try {
            emailAttachmentList = refreshEmailAttachments(request, response, emailLog);
        } catch (PDFGenerationException e) {
            request.setAttribute("emailErrorMessage", "This previously sent email cannot be re-opened for editing/resending. Please generate a new email instead. \\n\\n" + e.getMessage());
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
        request.getSession().setAttribute("emailAttachmentList", emailAttachmentList);

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
     * @throws RuntimeException if the user lacks required _email security privilege
     * @see DocumentAttachmentManager#renderDocument
     * @see FormsManager#renderForm
     * @see DocumentType
     */
    private List<EmailAttachment> refreshEmailAttachments(HttpServletRequest request, HttpServletResponse response, EmailLog emailLog) throws PDFGenerationException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_email)");
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
                    Path eDocPDFPath = documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.DOC, emailAttachment.getDocumentId());
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
            }
        }
        return emailAttachmentList;
    }
}
