package io.github.carlos_emr.carlos.email.action;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailFieldLengthException;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailComposeManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Struts2 action controller for handling email sending functionality within the OpenO EMR system.
 *
 * <p>This action supports multiple email sending workflows including:</p>
 * <ul>
 *   <li>Sending emails directly with healthcare data and attachments</li>
 *   <li>Sending electronic forms (EForms) via email with optional deletion after send</li>
 *   <li>Handling email encryption and password protection for PHI compliance</li>
 *   <li>Managing email attachments from session storage</li>
 *   <li>Canceling email operations and redirecting to source contexts</li>
 * </ul>
 *
 * <p>The action integrates with the EmailManager service for core email functionality and
 * EformDataManager for electronic form handling. All email operations are logged via
 * EmailLog entities for audit trail and compliance purposes.</p>
 *
 * <p>This action follows the 2Action pattern for Struts2 migration, using method-based
 * routing via the "method" request parameter to handle different email workflows within
 * a single action class.</p>
 *
 * <p><strong>Security Considerations:</strong> This action handles Protected Health Information (PHI)
 * and supports encryption for both email bodies and attachments. All operations are performed
 * within the context of a logged-in provider using LoggedInInfo.</p>
 *
 * @since 2026-01-24
 * @see io.github.carlos_emr.carlos.managers.EmailManager
 * @see io.github.carlos_emr.carlos.managers.EformDataManager
 * @see io.github.carlos_emr.carlos.commn.model.EmailLog
 * @see io.github.carlos_emr.carlos.email.core.EmailData
 */
public class EmailSend2Action extends ActionSupport {

    /** Session key of the prepared attachment list consumed by the next send. */
    public static final String ATTACHMENT_LIST_SESSION_KEY = "emailAttachmentList";
    /**
     * Session key of the patient the prepared attachment list was built for. The list is shared by
     * every email window in the session, so a send uses it only for that same patient.
     */
    public static final String ATTACHMENT_OWNER_SESSION_KEY = "emailAttachmentDemographicNo";

    /** Ids echoed back into the retry form are plain positive integers; anything else is dropped. */
    private static final Pattern NUMERIC_ID = Pattern.compile("\\d{1,10}");

    private final SecurityInfoManager securityInfoManager;

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final Logger logger = MiscUtils.getLogger();
    private final EmailManager emailManager;
    private final EformDataManager eformDataManager;
    private final EmailComposeManager emailComposeManager;
    private final DemographicManager demographicManager;

    /**
     * Struts creates this legacy router with its no-arg constructor, so the collaborators are
     * looked up here once rather than in field initializers.
     */
    public EmailSend2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), SpringUtils.getBean(EmailManager.class),
                SpringUtils.getBean(EformDataManager.class), SpringUtils.getBean(EmailComposeManager.class),
                SpringUtils.getBean(DemographicManager.class));
    }

    EmailSend2Action(SecurityInfoManager securityInfoManager, EmailManager emailManager,
            EformDataManager eformDataManager, EmailComposeManager emailComposeManager,
            DemographicManager demographicManager) {
        this.securityInfoManager = securityInfoManager;
        this.emailManager = emailManager;
        this.eformDataManager = eformDataManager;
        this.emailComposeManager = emailComposeManager;
        this.demographicManager = demographicManager;
    }

    /**
     * Main execution method that routes to specific email handling methods based on the "method" request parameter.
     *
     * <p>Every workflow is a mutation, so the route is POST-only: a GET or HEAD is refused with
     * 405 and an {@code Allow: POST} header before any parameter is read or side effect fires.</p>
     *
     * <p>This method implements method-based routing for the following email workflows:</p>
     * <ul>
     *   <li><strong>sendDirectEmail</strong> - Sends email directly without EForm context</li>
     *   <li><strong>cancel</strong> - Cancels email operation and redirects to source</li>
     *   <li><strong>default</strong> - Sends email with EForm context (if no method parameter specified)</li>
     * </ul>
     *
     * @return String Struts2 result identifier - "success" for successful email operations,
     *         or transaction type name for cancel operations
     */
    public String execute () {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", "w", null)) {
            throw new SecurityException("missing required sec object (_email)");
        }

        // Every route mutates: sendDirectEmail and the default sendEFormEmail deliver mail and
        // persist an EmailLog (and may delete the eForm), and cancel only ever arrives from the
        // compose form's POST. HttpMethodGuardFilter does not classify this route, so the
        // POST-only gate lives here, before any parameter is parsed or any side effect fires.
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            try {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return NONE;
        }

        // Every route parses demographicId into an int (EmailData.setDemographicNo, and the retry
        // form's consent lookup). A tampered or overlong value would surface as a
        // NumberFormatException, i.e. a server error, instead of a refused request.
        if (!isValidDemographicId(request.getParameter("demographicId"))) {
            try {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "demographicId must be a patient number");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return NONE;
        }

        if ("sendDirectEmail".equals(request.getParameter("method"))) {
            return sendDirectEmail();
        } else if ("cancel".equals(request.getParameter("method"))) {
            return cancel();
        }
        return sendEFormEmail();
    }

    /**
     * Sends an email with electronic form (EForm) context and optionally deletes the EForm after successful send.
     *
     * <p>This method handles the complete workflow for emailing EForms including:</p>
     * <ul>
     *   <li>Processing email send operation via EmailManager</li>
     *   <li>Optionally deleting the source EForm if send is successful and deletion is requested</li>
     *   <li>Setting request attributes for success status, EForm opening preference, and email log</li>
     * </ul>
     *
     * <p>The method checks the "deleteEFormAfterEmail" request parameter to determine if the
     * EForm should be removed after successful email delivery. This is useful for workflows
     * where the EForm is a temporary artifact used only for email generation.</p>
     *
     * @return String Struts2 SUCCESS result for rendering the email result page
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String sendEFormEmail() {
        boolean deleteEFormAfterEmail = request.getParameter("deleteEFormAfterEmail") != null && "true".equalsIgnoreCase(request.getParameter("deleteEFormAfterEmail"));

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        request.setAttribute("isOpenEForm", request.getParameter("openEFormAfterEmail"));
        request.setAttribute("fdid", request.getParameter("fdid"));
        EmailLog emailLog;
        try {
            emailLog = sendEmail(request);
        } catch (EmailFieldLengthException e) {
            // Nothing was sent or persisted, so the eForm must not be deleted.
            return rejectOverLengthFields(e);
        }

        boolean isEmailSuccessful = emailLog.getStatus() == EmailStatus.SUCCESS;
        request.setAttribute("isEmailSuccessful", isEmailSuccessful);
        if (isEmailSuccessful && deleteEFormAfterEmail) {
            eformDataManager.removeEFormData(loggedInInfo, request.getParameter("fdid"));
        }
        request.setAttribute("emailLog", emailLog);
        return SUCCESS;
    }

    /**
     * Sends an email directly without electronic form (EForm) context.
     *
     * <p>This method provides a simplified email sending workflow for scenarios where
     * the email is not associated with an EForm. It handles:</p>
     * <ul>
     *   <li>Processing the email send operation via EmailManager</li>
     *   <li>Setting request attributes for success status and email log</li>
     * </ul>
     *
     * <p>Unlike sendEFormEmail(), this method does not handle EForm deletion or opening
     * preferences, making it suitable for general-purpose email sending within the EMR.</p>
     *
     * @return String Struts2 SUCCESS result for rendering the email result page
     */
    public String sendDirectEmail() {
        EmailLog emailLog;
        try {
            emailLog = sendEmail(request);
        } catch (EmailFieldLengthException e) {
            return rejectOverLengthFields(e);
        }
        boolean isEmailSuccessful = emailLog.getStatus() == EmailStatus.SUCCESS;
        request.setAttribute("isEmailSuccessful", isEmailSuccessful);
        request.setAttribute("emailLog", emailLog);
        return SUCCESS;
    }

    /**
     * Renders the compose result page with one i18n'd error per over-length field instead of
     * sending. The compose page checks the same limits before submitting, so this is the
     * server-side backstop for scripted or tampered submissions and for limits the browser
     * measured differently.
     *
     * @param e the validation failure raised before anything was persisted or sent
     * @return Struts2 SUCCESS result, which renders the failure panel of the compose page
     */
    private String rejectOverLengthFields(EmailFieldLengthException e) {
        // Sizes and message keys only; the field content is PHI and is never logged.
        logger.warn("Email rejected before sending: {}", e.getMessage());
        request.setAttribute("emailLengthViolations", e.getViolations());
        restoreComposeForm();
        return SUCCESS;
    }

    /**
     * Re-populates the compose page from the rejected POST so the provider can shorten the
     * reported fields and send again from the same window.
     *
     * <p>{@code isEmailSuccessful} is deliberately left unset: the page then renders the editable
     * form, with the length errors above it, instead of the sent/failed result panel. The compose
     * page reads most of its values from request attributes whose names differ from the POST
     * parameter names ({@code encryptedMessage} is rendered from {@code encryptedMessageEmail},
     * {@code patientChartOption} from {@code emailPatientChartOption}, and so on), so each one is
     * copied across here. Every value is output-encoded by the page; values the page writes
     * unencoded (the transaction type, the patient id and the eForm flags) are normalised to an
     * enum name, an integer or a boolean first.</p>
     */
    private void restoreComposeForm() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        TransactionType transactionType = parseTransactionType(request.getParameter("transactionType"));
        request.setAttribute("transactionType", transactionType);
        // Range-checked, not just digit-checked: a ten-digit id passes NUMERIC_ID but would
        // overflow the Integer the consent lookup below needs, turning the rejection into a 500.
        Integer demographicNo = parseDemographicId(request.getParameter("demographicId"));
        String demographicId = demographicNo == null ? null : demographicNo.toString();
        request.setAttribute("demographicId", demographicId);
        request.setAttribute("fdid", numericOrNull(request.getParameter("fdid")));
        request.setAttribute("fid", numericOrNull(request.getParameter("fid")));
        request.setAttribute("openEFormAfterEmail", "true".equals(request.getParameter("openEFormAfterEmail")));
        request.setAttribute("deleteEFormAfterEmail", "true".equals(request.getParameter("deleteEFormAfterEmail")));
        // Never auto-send the retry: the provider has to see and fix the reported fields. The
        // explicit false also stops the page picking up a session-scoped value by EL scope lookup.
        request.setAttribute("isEmailAutoSend", false);

        // Sender and recipients. Only the hidden recipient inputs are submitted (the visible ones
        // are disabled), and they are exactly what this send would have used, so they are echoed
        // as the valid list; a recipient the provider removed stays removed.
        request.setAttribute("senderAccounts", emailComposeManager.getAllSenderAccounts());
        request.setAttribute("senderConfigId", numericOrNull(request.getParameter("senderConfigId")));
        String[] recipients = request.getParameterValues("receiverEmailAddress");
        request.setAttribute("receiverEmailList", recipients == null
                ? Collections.emptyList() : new ArrayList<>(Arrays.asList(recipients)));
        request.setAttribute("invalidReceiverEmailList", Collections.emptyList());
        if (demographicNo != null) {
            request.setAttribute("receiverName", demographicManager.getDemographicFormattedName(loggedInInfo, demographicNo));
            String[] emailConsent = emailComposeManager.getEmailConsentStatus(loggedInInfo, demographicNo);
            request.setAttribute("emailConsentName", emailConsent[0]);
            request.setAttribute("emailConsentStatus", emailConsent[1]);
        }

        // Provider-entered text, under the attribute names the page renders.
        request.setAttribute("subjectEmail", request.getParameter("subjectEmail"));
        request.setAttribute("bodyEmail", request.getParameter("bodyEmail"));
        request.setAttribute("encryptedMessageEmail", request.getParameter("encryptedMessage"));
        request.setAttribute("emailPDFPassword", request.getParameter("emailPDFPassword"));
        request.setAttribute("emailPDFPasswordClue", request.getParameter("emailPDFPasswordClue"));
        request.setAttribute("internalComment", request.getParameter("internalComment"));
        request.setAttribute("emailAdditionalParams", request.getParameter("additionalURLParams"));
        String chartOption = request.getParameter("patientChartOption");
        request.setAttribute("emailPatientChartOption", isChartOption(chartOption) ? chartOption : null);

        // Encryption choices. Echo what was submitted so the re-rendered form never carries a
        // weaker encryption state than the one the provider chose (Copilot review on #3906).
        request.setAttribute("isEmailEncrypted", "true".equals(request.getParameter("isEmailEncrypted")));
        request.setAttribute("isEmailAttachmentEncrypted",
                "true".equals(request.getParameter("isEmailAttachmentEncrypted")));
    }

    private static String numericOrNull(String value) {
        return value != null && NUMERIC_ID.matcher(value).matches() ? value : null;
    }

    /**
     * Parses a submitted patient number, or returns {@code null} when it is absent, not all
     * digits, or too large for an {@code int}. Never throws: the value comes from a hidden form
     * field and may be tampered with or overlong.
     */
    static Integer parseDemographicId(String value) {
        if (numericOrNull(value) == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Whether a submitted {@code demographicId} can be sent on to the routes: absent or blank
     * (a direct email with no patient), or a parseable patient number.
     */
    static boolean isValidDemographicId(String value) {
        return value == null || value.isEmpty() || parseDemographicId(value) != null;
    }

    private static TransactionType parseTransactionType(String value) {
        for (TransactionType type : TransactionType.values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        return null;
    }

    private static boolean isChartOption(String value) {
        for (ChartDisplayOption option : ChartDisplayOption.values()) {
            if (option.getValue().equals(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cancels the email operation and redirects the user back to the appropriate source context.
     *
     * <p>This method handles the cancel workflow by:</p>
     * <ul>
     *   <li>Preparing email fields from the request (to determine transaction type)</li>
     *   <li>Performing context-specific redirects based on the transaction type</li>
     *   <li>For EFORM transactions: redirects to the EForm display page with original form data</li>
     * </ul>
     *
     * <p>The method uses the transaction type from the email data to determine the
     * appropriate return destination, ensuring users are returned to their original
     * workflow context when canceling an email operation.</p>
     *
     * @return String Struts2 result identifier matching the transaction type name
     * @throws RuntimeException if IOException occurs during redirect for EFORM transactions
     */
    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    public String cancel() {
        EmailData emailData = prepareEmailFields(request);
        String emailRedirect = emailData.getTransactionType().name();
        if (emailData.getTransactionType().equals(EmailLog.TransactionType.EFORM)) {
            try {
                response.sendRedirect(request.getContextPath() + "/eform/efmshowform_data?fdid="
                        + SafeEncode.forUriComponent(request.getParameter("fdid")) + "&parentAjaxId=eforms");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return emailRedirect;
    }

    /**
     * Sends an email using the EmailManager service with data extracted from the HTTP request.
     *
     * <p>This private helper method coordinates the email sending process by:</p>
     * <ul>
     *   <li>Retrieving logged-in provider information from the session</li>
     *   <li>Preparing email data from request parameters via prepareEmailFields()</li>
     *   <li>Delegating to EmailManager for actual email transmission</li>
     * </ul>
     *
     * @param request HttpServletRequest containing email parameters and session data
     * @return EmailLog entity containing the result of the email send operation including
     *         status (SUCCESS/FAILURE), timestamps, and any error messages
     */
    private EmailLog sendEmail(HttpServletRequest request) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        EmailData emailData = prepareEmailFields(request);
        try {
            return emailManager.sendEmail(loggedInInfo, emailData);
        } catch (EmailFieldLengthException e) {
            // Nothing was sent. prepareEmailFields took the prepared attachments out of the
            // session, so put them back: otherwise shortening the field and sending again would
            // silently send the email without its PDFs.
            if (emailData.getAttachments() != null && !emailData.getAttachments().isEmpty()) {
                request.getSession().setAttribute(ATTACHMENT_LIST_SESSION_KEY, emailData.getAttachments()); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
                request.getSession().setAttribute(ATTACHMENT_OWNER_SESSION_KEY, request.getParameter("demographicId")); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- only compared with the next request's patient, never used for lookups
            }
            // The retry form lists the attachments the next send will carry: the restored ones, or
            // none when they were dropped because they belonged to another patient.
            request.setAttribute(ATTACHMENT_LIST_SESSION_KEY, emailData.getAttachments() == null
                    ? Collections.emptyList() : emailData.getAttachments());
            throw e;
        }
    }

    /**
     * Extracts and prepares email data from HTTP request parameters and session attributes.
     *
     * <p>This private helper method performs comprehensive email data preparation including:</p>
     * <ul>
     *   <li>Extracting sender and recipient email addresses</li>
     *   <li>Retrieving subject, body, and internal comment fields</li>
     *   <li>Processing encryption settings (email body and attachment encryption)</li>
     *   <li>Handling password protection parameters (password and password clue)</li>
     *   <li>Retrieving patient chart display options and demographic information</li>
     *   <li>Extracting transaction type and additional URL parameters</li>
     *   <li>Retrieving email attachments from session storage</li>
     *   <li>Cleaning up session by removing attachment list after extraction</li>
     * </ul>
     *
     * <p>The method supports PHI protection through encryption options and associates
     * emails with specific healthcare providers and patients for audit trail purposes.</p>
     *
     * @param request HttpServletRequest containing email form parameters and session data
     * @return EmailData populated data transfer object containing all email parameters
     *         ready for processing by EmailManager
     */
    private EmailData prepareEmailFields(HttpServletRequest request) {
        String senderConfigId = request.getParameter("senderConfigId");
        String[] receiverEmails = request.getParameterValues("receiverEmailAddress");
        String subject = request.getParameter("subjectEmail");
        String body = request.getParameter("bodyEmail");
        String encryptedMessage = request.getParameter("encryptedMessage");
        String password = request.getParameter("emailPDFPassword");
        String passwordClue = request.getParameter("emailPDFPasswordClue");
        String isEncrypted = request.getParameter("isEmailEncrypted");
        String isAttachmentEncrypted = request.getParameter("isEmailAttachmentEncrypted");
        String chartDisplayOption = request.getParameter("patientChartOption");
        String internalComment = request.getParameter("internalComment");
        String transactionType = request.getParameter("transactionType");
        String demographicNo = request.getParameter("demographicId");
        String additionalParams = request.getParameter("additionalURLParams");
        List<EmailAttachment> emailAttachmentList = (List<EmailAttachment>) request.getSession().getAttribute(ATTACHMENT_LIST_SESSION_KEY);
        Object attachmentOwner = request.getSession().getAttribute(ATTACHMENT_OWNER_SESSION_KEY);
        if (emailAttachmentList != null && !emailAttachmentList.isEmpty()
                && (demographicNo == null || !demographicNo.equals(attachmentOwner == null ? null : String.valueOf(attachmentOwner)))) {
            // The list was prepared for another patient (or for no known patient) in another email
            // window of this session. Sending it would attach one patient's documents to another
            // patient's email, so it is dropped. Count only: ids and the patient are PHI-correlating.
            logger.warn("Discarded {} prepared email attachment(s) that were not prepared for this patient", emailAttachmentList.size());
            emailAttachmentList = null;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        EmailData emailData = new EmailData();
        emailData.setSenderConfigId(senderConfigId);
        emailData.setRecipients(receiverEmails);
        emailData.setSubject(subject);
        emailData.setBody(body);
        emailData.setEncryptedMessage(encryptedMessage);
        emailData.setPassword(password);
        emailData.setPasswordClue(passwordClue);
        emailData.setIsEncrypted(isEncrypted);
        emailData.setIsAttachmentEncrypted(isAttachmentEncrypted);
        emailData.setChartDisplayOption(chartDisplayOption);
        emailData.setInternalComment(internalComment);
        emailData.setTransactionType(transactionType);
        emailData.setDemographicNo(demographicNo);
        emailData.setProviderNo(providerNo);
        emailData.setAdditionalParams(additionalParams);
        emailData.setAttachments(emailAttachmentList);

        request.getSession().removeAttribute(ATTACHMENT_LIST_SESSION_KEY);
        request.getSession().removeAttribute(ATTACHMENT_OWNER_SESSION_KEY);

        return emailData;
    }
}
