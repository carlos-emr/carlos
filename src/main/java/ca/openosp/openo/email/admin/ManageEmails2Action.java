//CHECKSTYLE:OFF
package ca.openosp.openo.email.admin;

import ca.openosp.openo.managers.*;
import org.apache.struts2.ActionSupport;
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

    public String execute() {
        String mtd = request.getParameter("method");
        if ("fetchEmails".equals(mtd)) {
            return fetchEmails();
        } else if ("resendEmail".equals(mtd)) {
            return resendEmail();
        }

        return showEmailManager();
    }

    public String showEmailManager() {
        request.setAttribute("emailStatusList", EmailStatus.values());
        request.setAttribute("senderAccountList", emailComposeManager.getAllSenderAccounts());
        return "show";
    }

    /*
     * This method is being called from the 'Admin > Emails > Manage Emails' page, when user clicks on the 'Fetch Emails' button
     * On that page, the sender email address and status are dropdowns.
     * The '-1' option is the default option, and '-1' means 'All'.
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
     * This method is called from the 'Admin > Emails > Manage Emails' section.
     * When a user clicks on the 'Resend' email button from any of the sent emails, Oscar will call this method.
     * Using this method and the emailLog ID (on which the user clicked Resend), it prepares for the email compose page.
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
