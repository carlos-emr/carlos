//CHECKSTYLE:OFF
package ca.openosp.openo.email.action;

import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.model.EmailAttachment;
import ca.openosp.openo.commn.model.EmailLog;
import ca.openosp.openo.commn.model.EmailLog.EmailStatus;
import ca.openosp.openo.email.core.EmailData;
import ca.openosp.openo.managers.EformDataManager;
import ca.openosp.openo.managers.EmailManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

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
 * @see ca.openosp.openo.managers.EmailManager
 * @see ca.openosp.openo.managers.EformDataManager
 * @see ca.openosp.openo.commn.model.EmailLog
 * @see ca.openosp.openo.email.core.EmailData
 */
public class EmailSend2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final Logger logger = MiscUtils.getLogger();
    private EmailManager emailManager = SpringUtils.getBean(EmailManager.class);
    private EformDataManager eformDataManager = SpringUtils.getBean(EformDataManager.class);

    /**
     * Main execution method that routes to specific email handling methods based on the "method" request parameter.
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
    public String sendEFormEmail() {
        boolean deleteEFormAfterEmail = request.getParameter("deleteEFormAfterEmail") != null && "true".equalsIgnoreCase(request.getParameter("deleteEFormAfterEmail"));

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        EmailLog emailLog = sendEmail(request);

        boolean isEmailSuccessful = emailLog.getStatus() == EmailStatus.SUCCESS;
        request.setAttribute("isEmailSuccessful", isEmailSuccessful);
        if (isEmailSuccessful && deleteEFormAfterEmail) {
            eformDataManager.removeEFormData(loggedInInfo, request.getParameter("fdid"));
        }
        request.setAttribute("isOpenEForm", request.getParameter("openEFormAfterEmail"));
        request.setAttribute("fdid", request.getParameter("fdid"));
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
        EmailLog emailLog = sendEmail(request);
        boolean isEmailSuccessful = emailLog.getStatus() == EmailStatus.SUCCESS;
        request.setAttribute("isEmailSuccessful", isEmailSuccessful);
        request.setAttribute("emailLog", emailLog);
        return SUCCESS;
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
    public String cancel() {
        EmailData emailData = prepareEmailFields(request);
        String emailRedirect = emailData.getTransactionType().name();
        if (emailData.getTransactionType().equals(EmailLog.TransactionType.EFORM)) {
            try {
                response.sendRedirect(request.getContextPath() + "/eform/efmshowform_data.jsp?fdid="  + request.getParameter("fdid") + "&parentAjaxId=eforms");
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
        return emailManager.sendEmail(loggedInInfo, emailData);
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
        String fromEmail = request.getParameter("senderEmailAddress");
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
        List<EmailAttachment> emailAttachmentList = (List<EmailAttachment>) request.getSession().getAttribute("emailAttachmentList");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        EmailData emailData = new EmailData();
        emailData.setSender(fromEmail);
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

        request.getSession().removeAttribute("emailAttachmentList");

        return emailData;
    }
}
