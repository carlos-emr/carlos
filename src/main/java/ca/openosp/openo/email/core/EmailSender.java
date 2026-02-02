//CHECKSTYLE:OFF
package ca.openosp.openo.email.core;

import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.model.EmailAttachment;
import ca.openosp.openo.commn.model.EmailConfig;
import ca.openosp.openo.email.helpers.APISendGridEmailSender;
import ca.openosp.openo.email.helpers.LocalSMTPEmailSender;
import ca.openosp.openo.email.helpers.SMTPEmailSender;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.EmailSendingException;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

/**
 * Core email sending service for OpenO EMR that handles healthcare-related email communications.
 *
 * <p>This class provides a unified interface for sending emails through multiple delivery methods
 * (SMTP and API-based) and providers (local SMTP, external SMTP, SendGrid). It enforces security
 * checks to ensure only authorized users can send emails, which is critical in a healthcare context
 * to prevent unauthorized access to patient information and maintain HIPAA/PIPEDA compliance.</p>
 *
 * <p>The EmailSender supports:</p>
 * <ul>
 *   <li>SMTP-based email delivery (both local and external providers)</li>
 *   <li>API-based email delivery (SendGrid)</li>
 *   <li>Email attachments for sharing medical documents and reports</li>
 *   <li>Security privilege checking to ensure proper authorization</li>
 *   <li>Comprehensive logging for audit trails</li>
 * </ul>
 *
 * <p>All email operations require the user to have the "_email" security privilege with WRITE access.
 * This ensures that only authorized healthcare providers and staff can send emails containing
 * potentially sensitive patient health information (PHI).</p>
 *
 * @see EmailConfig
 * @see EmailData
 * @see EmailAttachment
 * @see SMTPEmailSender
 * @see LocalSMTPEmailSender
 * @see APISendGridEmailSender
 * @see SecurityInfoManager
 * @since 2026-01-24
 */
public class EmailSender {
    private final Logger logger = MiscUtils.getLogger();
    private LoggedInInfo loggedInInfo;

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private EmailConfig emailConfig;
    private String[] recipients = new String[0];
    private String subject;
    private String body;
    private String additionalParams;
    private List<EmailAttachment> attachments;

    /**
     * Private no-argument constructor to prevent direct instantiation without required parameters.
     *
     * <p>This constructor is not intended for use. EmailSender instances must be created with
     * appropriate configuration and email data using one of the public constructors.</p>
     */
    private EmailSender() {
    }

    /**
     * Constructs an EmailSender with email data encapsulated in an EmailData object.
     *
     * <p>This constructor is the preferred way to create an EmailSender when you have
     * all email parameters collected in an EmailData object. It extracts recipients,
     * subject, body, attachments, and additional parameters from the EmailData instance.</p>
     *
     * @param loggedInInfo LoggedInInfo containing the current user's session information and provider context
     * @param emailConfig EmailConfig defining the email provider and delivery method (SMTP or API)
     * @param emailData EmailData containing all email content and recipient information
     */
    public EmailSender(LoggedInInfo loggedInInfo, EmailConfig emailConfig, EmailData emailData) {
        this.loggedInInfo = loggedInInfo;
        this.emailConfig = emailConfig;
        this.recipients = emailData.getRecipients();
        this.subject = emailData.getSubject();
        this.body = emailData.getBody();
        this.attachments = emailData.getAttachments();
        this.additionalParams = emailData.getAdditionalParams();
    }

    /**
     * Constructs an EmailSender with individual email parameters.
     *
     * <p>This constructor allows direct specification of email recipients, subject, body, and
     * attachments without encapsulating them in an EmailData object. Use this when you have
     * individual email parameters readily available and don't need additional parameters.</p>
     *
     * @param loggedInInfo LoggedInInfo containing the current user's session information and provider context
     * @param emailConfig EmailConfig defining the email provider and delivery method (SMTP or API)
     * @param recipients String array of email addresses to receive the email
     * @param subject String containing the email subject line
     * @param body String containing the email body content (supports HTML and plain text)
     * @param attachments List of EmailAttachment objects to include with the email, or null if no attachments
     */
    public EmailSender(LoggedInInfo loggedInInfo, EmailConfig emailConfig, String[] recipients, String subject, String body, List<EmailAttachment> attachments) {
        this.loggedInInfo = loggedInInfo;
        this.emailConfig = emailConfig;
        this.recipients = recipients;
        this.subject = subject;
        this.body = body;
        this.attachments = attachments;
    }

    /**
     * Constructs an EmailSender with individual email parameters including additional parameters.
     *
     * <p>This is the most comprehensive constructor, allowing specification of all email parameters
     * including additional provider-specific parameters. The additionalParams field can be used to
     * pass configuration options specific to certain email providers (e.g., SendGrid template IDs,
     * tracking settings, or custom headers).</p>
     *
     * @param loggedInInfo LoggedInInfo containing the current user's session information and provider context
     * @param emailConfig EmailConfig defining the email provider and delivery method (SMTP or API)
     * @param recipients String array of email addresses to receive the email
     * @param subject String containing the email subject line
     * @param body String containing the email body content (supports HTML and plain text)
     * @param additionalParams String containing provider-specific additional parameters, or null if not needed
     * @param attachments List of EmailAttachment objects to include with the email, or null if no attachments
     */
    public EmailSender(LoggedInInfo loggedInInfo, EmailConfig emailConfig, String[] recipients, String subject, String body, String additionalParams, List<EmailAttachment> attachments) {
        this.loggedInInfo = loggedInInfo;
        this.emailConfig = emailConfig;
        this.recipients = recipients;
        this.subject = subject;
        this.body = body;
        this.attachments = attachments;
        this.additionalParams = additionalParams;
    }

    /**
     * Sends the email using the configured provider and delivery method.
     *
     * <p>This method performs security validation to ensure the current user has the "_email"
     * privilege with WRITE access before attempting to send. This is critical for HIPAA/PIPEDA
     * compliance as it prevents unauthorized users from sending emails that may contain
     * patient health information (PHI).</p>
     *
     * <p>The email is dispatched based on the configured email type:</p>
     * <ul>
     *   <li><strong>SMTP:</strong> Uses either LocalSMTPEmailSender for local providers or
     *       SMTPEmailSender for external SMTP servers</li>
     *   <li><strong>API:</strong> Delegates to sendAPIMail() which handles API-based providers
     *       like SendGrid</li>
     * </ul>
     *
     * <p>All email sending operations are logged for audit trail purposes, which is required
     * for healthcare compliance and security monitoring.</p>
     *
     * @throws RuntimeException if the current user lacks the required "_email" security privilege
     * @throws EmailSendingException if there is an error during email transmission, including
     *         invalid configuration, network issues, authentication failures, or provider-specific errors
     */
    public void send() throws EmailSendingException {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_email)");
        }

        switch (emailConfig.getEmailType()) {
            case SMTP:
                SMTPEmailSender smtpSendHelper;

                // Use specialized sender for a LOCAL provider
                if (emailConfig.getEmailProvider() == EmailConfig.EmailProvider.LOCAL) {
                    smtpSendHelper = new LocalSMTPEmailSender(loggedInInfo, emailConfig, recipients, subject, body, attachments);
                } else {
                    smtpSendHelper = new SMTPEmailSender(loggedInInfo, emailConfig, recipients, subject, body, attachments);
                }

                smtpSendHelper.send();
                break;
            case API:
                sendAPIMail();
                break;
            default:
                throw new EmailSendingException("Invalid email configuration");
        }
    }

    /**
     * Sends email using API-based providers.
     *
     * <p>This private helper method handles email delivery through API-based email services
     * rather than traditional SMTP. It routes the email to the appropriate provider-specific
     * sender implementation based on the configured email provider.</p>
     *
     * <p>Currently supported API providers:</p>
     * <ul>
     *   <li><strong>SendGrid:</strong> Uses the SendGrid Web API for email delivery with
     *       support for templates, tracking, and advanced features</li>
     * </ul>
     *
     * <p>This method is called internally by send() when the email configuration specifies
     * an API-based delivery method.</p>
     *
     * @throws EmailSendingException if the configured provider is not supported or if there
     *         is an error during API-based email transmission
     */
    private void sendAPIMail() throws EmailSendingException {
        switch (emailConfig.getEmailProvider()) {
            case SENDGRID:
                APISendGridEmailSender apiSendGridSendHelper = new APISendGridEmailSender(loggedInInfo, emailConfig, recipients, subject, body, additionalParams, attachments);
                apiSendGridSendHelper.send();
                break;
            default:
                throw new EmailSendingException("Invalid email configuration");
        }
    }

    // For debugging
}
