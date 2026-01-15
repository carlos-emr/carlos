//CHECKSTYLE:OFF
package ca.openosp.openo.managers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.validator.EmailValidator;
import org.apache.logging.log4j.Logger;

import ca.openosp.OscarProperties;
import ca.openosp.openo.commn.dao.EmailConfigDaoImpl;
import ca.openosp.openo.commn.dao.EmailLogDaoImpl;
import ca.openosp.openo.commn.dao.UserPropertyDAO;
import ca.openosp.openo.commn.model.Consent;
import ca.openosp.openo.commn.model.ConsentType;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.EmailAttachment;
import ca.openosp.openo.commn.model.EmailConfig;
import ca.openosp.openo.commn.model.EmailLog;
import ca.openosp.openo.commn.model.UserProperty;
import ca.openosp.openo.commn.model.enumerator.DocumentType;
import ca.openosp.openo.documentManager.DocumentAttachmentManager;
import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PDFGenerationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ca.openosp.openo.util.StringUtils;

/*
 * The purpose of the EmailComposeManager is to help prepare all necessary data to display on the emailCompose.jsp page.
 */
@Service
public class EmailComposeManager {
    private final Logger logger = MiscUtils.getLogger();

    @Autowired
    private EmailConfigDaoImpl emailConfigDao;
    @Autowired
    private EmailLogDaoImpl emailLogDao;
    @Autowired
    private UserPropertyDAO userPropertyDAO;

    @Autowired
    private DemographicManager demographicManager;
    @Autowired
    private DocumentAttachmentManager documentAttachmentManager;
    @Autowired
    private FormsManager formsManager;
    @Autowired
    private PatientConsentManager patientConsentManager;
    @Autowired
    private SecurityInfoManager securityInfoManager;

    public EmailLog prepareEmailForResend(LoggedInInfo loggedInInfo, Integer emailLogId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_email)");
        }

        EmailLog emailLog = emailLogDao.find(emailLogId);
        return emailLog;
    }

    public List<EmailAttachment> prepareEFormAttachments(LoggedInInfo loggedInInfo, String fdid, String[] attachedEForms) throws PDFGenerationException {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_eform)");
        }

        List<String> attachedEFormIds = convertToList(attachedEForms);
        if (!StringUtils.isNullOrEmpty(fdid)) {
            attachedEFormIds.add(0, fdid);
        }

        List<EmailAttachment> emailAttachments = new ArrayList<>();
        for (String eFormId : attachedEFormIds) {
            Path eFormPDFPath = documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.EFORM, Integer.parseInt(eFormId));
            if (eFormPDFPath != null) {
                emailAttachments.add(new EmailAttachment(eFormPDFPath.getFileName().toString(), eFormPDFPath.toString(), DocumentType.EFORM, Integer.parseInt(eFormId), getFileSize(eFormPDFPath)));
            }
        }

        return emailAttachments;
    }

    public List<EmailAttachment> prepareEDocAttachments(LoggedInInfo loggedInInfo, String[] attachedDocuments) throws PDFGenerationException {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_edoc)");
        }

        List<String> attachedEDocIds = convertToList(attachedDocuments);

        List<EmailAttachment> emailAttachments = new ArrayList<>();
        for (String eDocId : attachedEDocIds) {
            Path eDocPDFPath = documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.DOC, Integer.parseInt(eDocId));
            if (eDocPDFPath != null) {
                emailAttachments.add(new EmailAttachment(eDocPDFPath.getFileName().toString(), eDocPDFPath.toString(), DocumentType.DOC, Integer.parseInt(eDocId), getFileSize(eDocPDFPath)));
            }
        }

        return emailAttachments;
    }

    public List<EmailAttachment> prepareLabAttachments(LoggedInInfo loggedInInfo, String[] attachedLabs) throws PDFGenerationException {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_lab)");
        }

        List<String> attachedLabIds = convertToList(attachedLabs);

        List<EmailAttachment> emailAttachments = new ArrayList<>();
        for (String labId : attachedLabIds) {
            Path labPDFPath = documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.LAB, Integer.parseInt(labId));
            if (labPDFPath != null) {
                emailAttachments.add(new EmailAttachment(labPDFPath.getFileName().toString(), labPDFPath.toString(), DocumentType.LAB, Integer.parseInt(labId), getFileSize(labPDFPath)));
            }
        }

        return emailAttachments;
    }

    public List<EmailAttachment> prepareHRMAttachments(LoggedInInfo loggedInInfo, String[] attachedHRMDocuments) throws PDFGenerationException {
        if (!OscarProperties.getInstance().isOntarioBillingRegion()) {
            return new ArrayList<>();
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_hrm", SecurityInfoManager.READ, null)) {
            LogAction.addLogSynchronous(loggedInInfo.getLoggedInProviderNo(), "EmailComposeManager.prepareHRMAttachments", "UNAUTHORIZED", "missing required security object (_hrm)", loggedInInfo.getIp());
            logger.warn("missing required security object (_hrm)");
            return new ArrayList<>();
        }

        List<String> attachedHRMIds = convertToList(attachedHRMDocuments);

        List<EmailAttachment> emailAttachments = new ArrayList<>();
        for (String hrmId : attachedHRMIds) {
            Path hrmPDFPath = documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.HRM, Integer.parseInt(hrmId));
            if (hrmPDFPath != null) {
                emailAttachments.add(new EmailAttachment(hrmPDFPath.getFileName().toString(), hrmPDFPath.toString(), DocumentType.HRM, Integer.parseInt(hrmId), getFileSize(hrmPDFPath)));
            }
        }

        return emailAttachments;
    }

    public List<EmailAttachment> prepareFormAttachments(HttpServletRequest request, HttpServletResponse response, String[] attachedForms, Integer demographicId) throws PDFGenerationException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, String.valueOf(demographicId))) {
            throw new RuntimeException("missing required sec object (_form)");
        }

        List<String> attachedFormIds = convertToList(attachedForms);

        List<EmailAttachment> emailAttachments = new ArrayList<>();
        for (String formId : attachedFormIds) {
            Path formPDFPath = formsManager.renderForm(request, response, Integer.parseInt(formId), demographicId);
            if (formPDFPath != null) {
                emailAttachments.add(new EmailAttachment(formPDFPath.getFileName().toString(), formPDFPath.toString(), DocumentType.FORM, Integer.parseInt(formId), getFileSize(formPDFPath)));
            }
        }

        return emailAttachments;
    }

    public void sanitizeAttachments(List<EmailAttachment> emailAttachments) {
        DecimalFormat formatter = new DecimalFormat("000");
        int attachmentNumber = 1;
        for (EmailAttachment emailAttachment : emailAttachments) {
            String attachmentName = "attachment_" + formatter.format(attachmentNumber++) + ".pdf";
            emailAttachment.setFileName(attachmentName);
        }
    }

    public String[] getEmailConsentStatus(LoggedInInfo loggedInInfo, Integer demographicId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_email)");
        }

        String UNKNOWN = "Unknown", OPTIN = "Explicit Opt-In", OPTOUT = "Explicit Opt-Out";
        UserProperty userProperty = userPropertyDAO.getProp(UserProperty.EMAIL_COMMUNICATION);
        if (userProperty == null || StringUtils.isNullOrEmpty(userProperty.getValue())) {
            return new String[]{"", UNKNOWN};
        }

        String property = userProperty.getValue().split("[,;\\s()]+")[0];
        ConsentType consentType = patientConsentManager.getConsentType(property);
        if (consentType == null || !consentType.isActive()) {
            return new String[]{"", UNKNOWN};
        }

        Consent consent = patientConsentManager.getConsentByDemographicAndConsentType(loggedInInfo, demographicId, consentType);
        if (consent == null) {
            return new String[]{consentType.getName(), UNKNOWN};
        }

        return consent.getPatientConsented() ? new String[]{consentType.getName(), OPTIN} : new String[]{consentType.getName(), OPTOUT};
    }

    public Boolean isEmailConsentConfigured() {
        UserProperty userProperty = userPropertyDAO.getProp(UserProperty.EMAIL_COMMUNICATION);
        if (userProperty == null || StringUtils.isNullOrEmpty(userProperty.getValue())) {
            return Boolean.FALSE;
        }

        String property = userProperty.getValue().split("[,;\\s()]+")[0];
        ConsentType consentType = patientConsentManager.getConsentType(property);
        if (consentType == null || !consentType.isActive()) {
            return Boolean.FALSE;
        }

        return Boolean.TRUE;
    }

    public List<EmailConfig> getAllSenderAccounts() {
        return emailConfigDao.fillAllActiveEmailConfigs();
    }

    public Boolean hasActiveSenderAccount() {
        if (getAllSenderAccounts().isEmpty()) {
            return false;
        }
        return true;
    }

    public Boolean hasValidRecipient(LoggedInInfo loggedInInfo, Integer demographicId) {
        List<String> validRecipients = (List<String>) getRecipients(loggedInInfo, demographicId)[0];
        if (validRecipients.isEmpty()) {
            return false;
        }
        return true;
    }

    public Boolean isEmailEnabled() {
        if (isEmailConsentConfigured() && !getAllSenderAccounts().isEmpty()) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    public List<?>[] getRecipients(LoggedInInfo loggedInInfo, Integer demographicId) {
        String recipientsString = demographicManager.getDemographicEmail(loggedInInfo, demographicId);
        List<String> validRecipients = new ArrayList<>();
        List<String> invalidRecipients = new ArrayList<>();
        if (StringUtils.isNullOrEmpty(recipientsString)) {
            return new List<?>[]{validRecipients, invalidRecipients};
        }

        String[] recipients = recipientsString.split("[,;\\s()]+");
        for (String recipient : recipients) {
            if (isValidEmail(recipient)) {
                validRecipients.add(recipient);
            } else {
                invalidRecipients.add(recipient);
            }
        }

        return new List<?>[]{validRecipients, invalidRecipients};
    }

    public String createEmailPDFPassword(LoggedInInfo loggedInInfo, Integer demographicId) {
        Demographic demographic = demographicManager.getDemographic(loggedInInfo, demographicId);
        return demographic.getYearOfBirth() + demographic.getMonthOfBirth() + demographic.getDateOfBirth() + demographic.getHin();
    }

    public boolean hasEmailPrivilege(LoggedInInfo loggedInInfo, String privilege) {
        boolean hasEmailPrivilege = securityInfoManager.hasPrivilege(loggedInInfo, "_email", privilege, null);
        return hasEmailPrivilege;
    }

    private boolean isValidEmail(String email) {
        EmailValidator emailValidator = EmailValidator.getInstance();
        return emailValidator.isValid(email);
    }

    private List<String> convertToList(String[] stringArray) {
        List<String> stringList = new ArrayList<>();
        if (stringArray != null) {
            Collections.addAll(stringList, stringArray);
        }
        return stringList;
    }

    public Long getFileSize(Path filePath) {
        Long fileSize = 0l;
        try {
            fileSize = Files.size(filePath);
        } catch (IOException e) {
            logger.error("Error accessing file: " + e.getMessage(), e);
        }
        return fileSize;
    }

}
