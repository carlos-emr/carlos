//CHECKSTYLE:OFF
package ca.openosp.openo.email.action;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.logging.log4j.Logger;

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


    public String execute() {
        return prepareComposeEFormMailer();
    }

    public String prepareComposeEFormMailer() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // Get email information from session (survives redirect)
        HttpSession session = request.getSession();
        Boolean attachEFormItSelfObj = (Boolean) session.getAttribute("attachEFormItSelf");
        boolean attachEFormItSelf = attachEFormItSelfObj != null && attachEFormItSelfObj;
        String fdid = attachEFormItSelf ? (String) session.getAttribute("fdid") : "";
        String demographicId = (String) session.getAttribute("demographicId");
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

    private String emailComposeError(HttpServletRequest request, String errorMessage) {
        request.setAttribute("errorMessage", errorMessage);
        return "eFormError";
    }
}
