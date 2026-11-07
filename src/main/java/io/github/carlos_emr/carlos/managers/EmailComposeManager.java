package io.github.carlos_emr.carlos.managers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.validator.EmailValidator;
import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.EmailConfigDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.EmailLogDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Consent;
import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.AttachmentOwnershipService;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.github.carlos_emr.carlos.util.StringUtils;

/**
 * Manager service for composing and preparing healthcare-related email communications.
 *
 * This manager orchestrates the preparation of email data for display on the emailCompose.jsp page,
 * including attachment handling for various healthcare document types (EForms, EDocs, Labs, HRMs, Forms),
 * recipient validation, consent status verification, and sender account management.
 *
 * All operations enforce security privilege checks to ensure HIPAA/PIPEDA compliance for PHI (Patient Health Information).
 * Email communications require explicit patient consent verification through the configured consent types.
 *
 * @see EmailConfig
 * @see EmailLog
 * @see EmailAttachment
 * @see DocumentAttachmentManager
 * @see PatientConsentManager
 * @since 2026-01-24
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
    private NioFileManager nioFileManager;
    @Autowired
    private AttachmentOwnershipService attachmentOwnershipService;
    private final SecurityInfoManager securityInfoManager;

    /** The failure for an attachment of another patient, or one that cannot be found; it names no record. */
    private static final String ATTACHMENT_NOT_RENDERED = "A selected email attachment could not be rendered";

    @Autowired
    public EmailComposeManager(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = java.util.Objects.requireNonNull(securityInfoManager);
    }

    /**
     * Prepares an existing email for resending by retrieving its log entry.
     *
     * This method retrieves the email log for a previously sent email to allow resending
     * with the same content and attachments. Requires READ privilege on the _email security object.
     *
     * @param loggedInInfo LoggedInInfo the current logged-in user session information
     * @param emailLogId Integer the unique identifier of the email log entry to retrieve
     * @return EmailLog the email log entry containing the original email data
     * @throws SecurityException if the user lacks the required _email READ privilege
     */
    public EmailLog prepareEmailForResend(LoggedInInfo loggedInInfo, Integer emailLogId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_email)");
        }

        EmailLog emailLog = emailLogDao.find(emailLogId);
        return emailLog;
    }

    /**
     * Prepares EForm attachments for email composition by rendering them as PDF documents.
     *
     * This method converts selected EForms into PDF format for email attachment. The fdid parameter
     * represents a featured form that will be prepended to the list of attached forms. Each EForm
     * is rendered with proper security context and file size calculation.
     *
     * <p>Every eForm, the featured one included, must belong to {@code demographicNo} (its
     * {@code eform_data} patient). If one does not, nothing is rendered and the attachment is refused
     * like one that cannot be found.</p>
     *
     * @param loggedInInfo LoggedInInfo the current logged-in user session information
     * @param fdid String the featured/primary EForm document ID to prepend to attachments (optional, may be null)
     * @param attachedEForms String[] array of EForm IDs to attach to the email
     * @param demographicNo the email's patient, whom every attachment must belong to
     * @return List&lt;EmailAttachment&gt; list of prepared PDF email attachments with metadata
     * @throws PDFGenerationException if PDF rendering fails for any EForm, or an eForm cannot be found
     *         or belongs to another patient
     * @throws SecurityException if the user lacks the required _eform READ privilege
     */
    public List<EmailAttachment> prepareEFormAttachments(LoggedInInfo loggedInInfo, String fdid, String[] attachedEForms,
            Integer demographicNo) throws PDFGenerationException {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_eform)");
        }

        List<String> attachedEFormIds = convertToList(attachedEForms);
        if (!StringUtils.isNullOrEmpty(fdid)) {
            attachedEFormIds.add(0, fdid);
        }

        List<EmailAttachment> emailAttachments = new ArrayList<>();
        for (Integer eFormId : patientAttachmentIds(DocumentType.EFORM, attachedEFormIds, demographicNo)) {
            Path eFormPDFPath = documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.EFORM, eFormId);
            if (eFormPDFPath != null) {
                emailAttachments.add(new EmailAttachment(eFormPDFPath.getFileName().toString(), eFormPDFPath.toString(), DocumentType.EFORM, eFormId, getFileSize(eFormPDFPath)));
            }
        }

        return emailAttachments;
    }

    /**
     * Prepares EDoc (electronic document) attachments for email composition by rendering them as PDF documents.
     *
     * This method converts selected electronic documents into PDF format for email attachment.
     * Each EDoc is rendered with proper security context and file size calculation.
     *
     * <p>Every document must belong to {@code demographicNo} (a {@code ctl_document} link to that
     * patient, not deleted). If one does not, nothing is rendered and the attachment is refused like
     * one that cannot be found.</p>
     *
     * @param loggedInInfo LoggedInInfo the current logged-in user session information
     * @param attachedDocuments String[] array of EDoc IDs to attach to the email
     * @param demographicNo the email's patient, whom every attachment must belong to
     * @return List&lt;EmailAttachment&gt; list of prepared PDF email attachments with metadata
     * @throws PDFGenerationException if PDF rendering fails for any electronic document, or a document
     *         cannot be found or belongs to another patient
     * @throws SecurityException if the user lacks the required _edoc READ privilege
     */
    public List<EmailAttachment> prepareEDocAttachments(LoggedInInfo loggedInInfo, String[] attachedDocuments,
            Integer demographicNo) throws PDFGenerationException {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        List<String> attachedEDocIds = convertToList(attachedDocuments);

        List<EmailAttachment> emailAttachments = new ArrayList<>();
        for (Integer eDocId : patientAttachmentIds(DocumentType.DOC, attachedEDocIds, demographicNo)) {
            Path eDocPDFPath = applicationTempFile(
                    documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.DOC, eDocId));
            if (eDocPDFPath != null) {
                emailAttachments.add(new EmailAttachment(eDocPDFPath.getFileName().toString(), eDocPDFPath.toString(), DocumentType.DOC, eDocId, getFileSize(eDocPDFPath)));
            }
        }

        return emailAttachments;
    }

    /**
     * The file an email attachment is prepared from, inside the CARLOS application temp tree.
     *
     * <p>A PDF document renders in place: {@code renderDocument} hands back the stored file itself.
     * The compose window's attachment preview serves only application temp files, so before this a
     * PDF document could never be emailed ("PDF preview is outside the CARLOS temp directory"). It is
     * copied into the temp tree instead, so the preview and the send read the copy and the stored
     * document is never handed on. A rendered file already in the temp tree is used as it is.</p>
     *
     * @param rendered a rendered attachment PDF, or {@code null}
     * @return {@code rendered} if it is already an application temp file (or {@code null}), else a copy
     *         of it in the application temp tree
     * @throws PDFGenerationException if the copy cannot be made
     */
    public Path applicationTempFile(Path rendered) throws PDFGenerationException {
        if (rendered == null || PathValidationUtils.isInApplicationTempDirectory(rendered.toFile())) {
            return rendered;
        }
        try {
            return nioFileManager.createTempFileFrom(rendered.getFileName().toString(), rendered);
        } catch (IOException | RuntimeException e) {
            // No file name or path in the message.
            throw new PDFGenerationException("A document could not be copied for the email", e);
        }
    }

    /**
     * Prepares laboratory result attachments for email composition by rendering them as PDF documents.
     *
     * This method converts selected laboratory results into PDF format for email attachment.
     * Each lab result is rendered with proper security context and file size calculation.
     *
     * <p>Every lab must belong to {@code demographicNo} (an HL7 {@code patientLabRouting} row for that
     * patient; the lab renderer renders HL7 labs only). If one does not, nothing is rendered and the
     * attachment is refused like one that cannot be found.</p>
     *
     * @param loggedInInfo LoggedInInfo the current logged-in user session information
     * @param attachedLabs String[] array of lab result IDs to attach to the email
     * @param demographicNo the email's patient, whom every attachment must belong to
     * @return List&lt;EmailAttachment&gt; list of prepared PDF email attachments with metadata
     * @throws PDFGenerationException if PDF rendering fails for any laboratory result, or a lab cannot
     *         be found or belongs to another patient
     * @throws SecurityException if the user lacks the required _lab READ privilege
     */
    public List<EmailAttachment> prepareLabAttachments(LoggedInInfo loggedInInfo, String[] attachedLabs,
            Integer demographicNo) throws PDFGenerationException {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }

        List<String> attachedLabIds = convertToList(attachedLabs);

        List<EmailAttachment> emailAttachments = new ArrayList<>();
        for (Integer labId : patientAttachmentIds(DocumentType.LAB, attachedLabIds, demographicNo)) {
            Path labPDFPath = documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.LAB, labId);
            if (labPDFPath != null) {
                emailAttachments.add(new EmailAttachment(labPDFPath.getFileName().toString(), labPDFPath.toString(), DocumentType.LAB, labId, getFileSize(labPDFPath)));
            }
        }

        return emailAttachments;
    }

    /**
     * Prepares HRM (Hospital Report Manager) attachments for email composition by rendering them as PDF documents.
     *
     * This method converts selected hospital reports into PDF format for email attachment.
     * HRM functionality is only available in Ontario billing regions. If not in Ontario, an empty list is returned.
     * Security violations are logged but do not throw exceptions, instead returning an empty list.
     *
     * <p>Every report must belong to {@code demographicNo} (an {@code HRMDocumentToDemographic} link
     * to that patient). If one does not, nothing is rendered and the attachment is refused like one
     * that cannot be found.</p>
     *
     * @param loggedInInfo LoggedInInfo the current logged-in user session information
     * @param attachedHRMDocuments String[] array of HRM document IDs to attach to the email
     * @param demographicNo the email's patient, whom every attachment must belong to
     * @return List&lt;EmailAttachment&gt; list of prepared PDF email attachments with metadata, or empty list if not in Ontario or lacking privileges
     * @throws PDFGenerationException if PDF rendering fails for any hospital report, or a report cannot
     *         be found or belongs to another patient
     */
    public List<EmailAttachment> prepareHRMAttachments(LoggedInInfo loggedInInfo, String[] attachedHRMDocuments,
            Integer demographicNo) throws PDFGenerationException {
        if (!CarlosProperties.getInstance().isOntarioBillingRegion()) {
            return new ArrayList<>();
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_hrm", SecurityInfoManager.READ, null)) {
            LogAction.addLogSynchronous(loggedInInfo.getLoggedInProviderNo(), "EmailComposeManager.prepareHRMAttachments", "UNAUTHORIZED", "missing required security object (_hrm)", loggedInInfo.getIp());
            logger.warn("missing required security object (_hrm)");
            return new ArrayList<>();
        }

        List<String> attachedHRMIds = convertToList(attachedHRMDocuments);

        List<EmailAttachment> emailAttachments = new ArrayList<>();
        for (Integer hrmId : patientAttachmentIds(DocumentType.HRM, attachedHRMIds, demographicNo)) {
            Path hrmPDFPath = documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.HRM, hrmId);
            if (hrmPDFPath != null) {
                emailAttachments.add(new EmailAttachment(hrmPDFPath.getFileName().toString(), hrmPDFPath.toString(), DocumentType.HRM, hrmId, getFileSize(hrmPDFPath)));
            }
        }

        return emailAttachments;
    }

    /**
     * Prepares clinical form attachments for email composition by rendering them as PDF documents.
     *
     * This method converts selected clinical forms into PDF format for email attachment.
     * Forms are rendered with the associated demographic context and require both servlet request/response
     * objects for proper rendering.
     *
     * @param request HttpServletRequest the servlet request object required for form rendering
     * @param response HttpServletResponse the servlet response object required for form rendering
     * @param attachedForms String[] array of form IDs to attach to the email
     * @param demographicId Integer the patient demographic ID associated with the forms
     * @return List&lt;EmailAttachment&gt; list of prepared PDF email attachments with metadata
     * @throws PDFGenerationException if PDF rendering fails for any clinical form
     * @throws SecurityException if the user lacks the required _form READ privilege for the specified demographic
     */
    public List<EmailAttachment> prepareFormAttachments(HttpServletRequest request, HttpServletResponse response, String[] attachedForms, Integer demographicId) throws PDFGenerationException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, String.valueOf(demographicId))) {
            throw new SecurityException("missing required sec object (_form)");
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

    /**
     * Sanitizes email attachment filenames by replacing them with standardized sequential names.
     *
     * This method renames all attachments to a standardized format (attachment_001.pdf, attachment_002.pdf, etc.)
     * to prevent PHI disclosure through original filenames and ensure consistent naming across all email attachments.
     * This is a security measure to protect patient privacy.
     *
     * @param emailAttachments List&lt;EmailAttachment&gt; the list of email attachments to sanitize (modified in place)
     */
    public void sanitizeAttachments(List<EmailAttachment> emailAttachments) {
        DecimalFormat formatter = new DecimalFormat("000");
        int attachmentNumber = 1;
        for (EmailAttachment emailAttachment : emailAttachments) {
            String attachmentName = "attachment_" + formatter.format(attachmentNumber++) + ".pdf";
            emailAttachment.setFileName(attachmentName);
        }
    }

    /**
     * Retrieves the email communication consent status for a patient.
     *
     * This method checks the patient's consent status for email communications based on the configured
     * consent type in user properties. Returns a two-element array containing the consent type name
     * and the consent status (Unknown, Explicit Opt-In, or Explicit Opt-Out).
     *
     * @param loggedInInfo LoggedInInfo the current logged-in user session information
     * @param demographicId Integer the patient demographic ID to check consent for
     * @return String[] array with two elements: [0] consent type name, [1] consent status description
     * @throws SecurityException if the user lacks the required _email READ privilege
     */
    public String[] getEmailConsentStatus(LoggedInInfo loggedInInfo, Integer demographicId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_email)");
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

    /**
     * Checks if email consent tracking is properly configured in the system.
     *
     * This method verifies that a valid and active consent type is configured in user properties
     * for email communication tracking. This is required for PIPEDA/HIPAA compliance before
     * allowing email communications with patients.
     *
     * @return Boolean TRUE if email consent is properly configured with an active consent type, FALSE otherwise
     */
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

    /**
     * Retrieves all active email sender account configurations.
     *
     * This method returns all configured and active email sender accounts (SMTP configurations)
     * that can be used for sending patient communications.
     *
     * @return List&lt;EmailConfig&gt; list of all active email configuration accounts
     */
    public List<EmailConfig> getAllSenderAccounts() {
        return emailConfigDao.fillAllActiveEmailConfigs();
    }

    /**
     * Checks if at least one active email sender account is configured.
     *
     * This method verifies that the system has at least one configured SMTP account
     * available for sending emails.
     *
     * @return Boolean TRUE if at least one active sender account exists, FALSE otherwise
     */
    public Boolean hasActiveSenderAccount() {
        if (getAllSenderAccounts().isEmpty()) {
            return false;
        }
        return true;
    }

    /**
     * Checks if a patient has at least one valid email recipient address.
     *
     * This method validates that the patient's demographic record contains at least one
     * properly formatted email address that can be used as a recipient.
     *
     * @param loggedInInfo LoggedInInfo the current logged-in user session information
     * @param demographicId Integer the patient demographic ID to check for valid recipients
     * @return Boolean TRUE if at least one valid email address exists for the patient, FALSE otherwise
     */
    public Boolean hasValidRecipient(LoggedInInfo loggedInInfo, Integer demographicId) {
        List<String> validRecipients = (List<String>) getRecipients(loggedInInfo, demographicId)[0];
        if (validRecipients.isEmpty()) {
            return false;
        }
        return true;
    }

    /**
     * Checks if the email system is fully enabled and ready for use.
     *
     * This method verifies that both email consent tracking is properly configured
     * and at least one active sender account exists. Both conditions must be met
     * for the email system to be considered enabled.
     *
     * @return Boolean TRUE if email consent is configured and sender accounts exist, FALSE otherwise
     */
    public Boolean isEmailEnabled() {
        if (isEmailConsentConfigured() && !getAllSenderAccounts().isEmpty()) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    /**
     * Retrieves and validates email recipients for a patient.
     *
     * This method extracts email addresses from the patient's demographic record and validates
     * each address using RFC-compliant email validation. Returns two lists: one containing
     * valid email addresses and one containing invalid addresses.
     *
     * @param loggedInInfo LoggedInInfo the current logged-in user session information
     * @param demographicId Integer the patient demographic ID to retrieve recipients for
     * @return List&lt;?&gt;[] array of two lists: [0] List&lt;String&gt; valid email addresses, [1] List&lt;String&gt; invalid email addresses
     */
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

    /**
     * Creates a password for encrypting PDF attachments based on patient demographic data.
     *
     * This method generates a password by concatenating the patient's birth date components
     * (year, month, day) and health insurance number (HIN). This provides a patient-specific
     * password that the patient can reconstruct using their own demographic information.
     *
     * @param loggedInInfo LoggedInInfo the current logged-in user session information
     * @param demographicId Integer the patient demographic ID to create password for
     * @return String the generated PDF password in format: YYYYMMDDHIN
     */
    public String createEmailPDFPassword(LoggedInInfo loggedInInfo, Integer demographicId) {
        Demographic demographic = demographicManager.getDemographic(loggedInInfo, demographicId);
        return demographic.getYearOfBirth() + demographic.getMonthOfBirth() + demographic.getDateOfBirth() + demographic.getHin();
    }

    /**
     * Checks if the current user has a specific privilege for email operations.
     *
     * This method verifies that the logged-in user has the specified privilege level
     * (READ, WRITE, UPDATE, or DELETE) for the _email security object.
     *
     * @param loggedInInfo LoggedInInfo the current logged-in user session information
     * @param privilege String the privilege level to check (e.g., "r", "w", "u", "d")
     * @return boolean true if the user has the specified email privilege, false otherwise
     */
    public boolean hasEmailPrivilege(LoggedInInfo loggedInInfo, String privilege) {
        boolean hasEmailPrivilege = securityInfoManager.hasPrivilege(loggedInInfo, "_email", privilege, null);
        return hasEmailPrivilege;
    }

    /**
     * Validates an email address using RFC-compliant validation rules.
     *
     * This method uses Apache Commons EmailValidator to verify that the email address
     * conforms to RFC 822 standards for email address format.
     *
     * @param email String the email address to validate
     * @return boolean true if the email address is valid according to RFC standards, false otherwise
     */
    private boolean isValidEmail(String email) {
        EmailValidator emailValidator = EmailValidator.getInstance();
        return emailValidator.isValid(email);
    }

    /**
     * The selected ids of one attachment type, parsed, once each is known to belong to the email's
     * patient ({@code demographicNo}) through {@link AttachmentOwnershipService}, the rule the send
     * applies too. Email attachments must belong to the email's patient: a record of another patient,
     * or one that cannot be found, is refused the same way, before anything is rendered and with
     * nothing about the record in the failure.
     */
    private List<Integer> patientAttachmentIds(DocumentType type, List<String> selected, Integer demographicNo)
            throws PDFGenerationException {
        List<Integer> ids = new ArrayList<>(selected.size());
        for (String id : selected) {
            ids.add(Integer.parseInt(id));
        }
        if (!ids.isEmpty() && !attachmentOwnershipService.allBelongToDemographic(type, demographicNo, ids)) {
            throw new PDFGenerationException(ATTACHMENT_NOT_RENDERED);
        }
        return ids;
    }

    /**
     * Converts a string array to a mutable ArrayList.
     *
     * This method converts a string array into an ArrayList to allow modification.
     * If the input array is null, an empty list is returned.
     *
     * @param stringArray String[] the array to convert (may be null)
     * @return List&lt;String&gt; a mutable list containing all elements from the array, or empty list if input is null
     */
    private List<String> convertToList(String[] stringArray) {
        List<String> stringList = new ArrayList<>();
        if (stringArray != null) {
            Collections.addAll(stringList, stringArray);
        }
        return stringList;
    }

    /**
     * Retrieves the file size in bytes for a given file path.
     *
     * This method safely retrieves the file size for attachment metadata.
     * If an IOException occurs during file access, the error is logged and 0 is returned.
     *
     * @param filePath Path the file path to get the size for
     * @return Long the file size in bytes, or 0 if the file cannot be accessed
     */
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
