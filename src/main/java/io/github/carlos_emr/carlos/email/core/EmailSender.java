package io.github.carlos_emr.carlos.email.core;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveDto;
import io.github.carlos_emr.carlos.email.helpers.APISendGridEmailSender;
import io.github.carlos_emr.carlos.email.helpers.LocalSMTPEmailSender;
import io.github.carlos_emr.carlos.email.helpers.SMTPEmailSender;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

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
    private LoggedInInfo loggedInInfo;

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private EmailConfig emailConfig;
    private String[] recipients = new String[0];
    private String subject;
    private String body;
    private String additionalParams;
    private List<EmailAttachment> attachments;
    private OutboundEmailTransport preparedTransport;

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
     * @throws SecurityException if the current user lacks the required "_email" security privilege
     * @throws EmailSendingException if there is an error during email transmission, including
     *         invalid configuration, network issues, authentication failures, or provider-specific errors
     */
    public void send() throws EmailSendingException {
        assertEmailWritePrivilege();
        createTransport().send();
    }

    /**
     * Resolves the configured transport, or refuses the send.
     *
     * <p>This is the single place that decides which transports exist. Both {@link #send()} and
     * {@link #prepareOutboundArchive(EmailLog)} route through it, so "can this configuration
     * send?" and "can this configuration be archived?" cannot give different answers — every
     * transport reachable here is an {@link OutboundEmailTransport} and therefore describes its
     * own archive artifact.</p>
     *
     * <p>An unconfigured or unrecognised transport throws rather than falling through to an
     * unarchived send. Adding a provider means adding a branch here, and a branch can only be
     * added by supplying something that implements the archive contract.</p>
     *
     * @return the transport for this configuration
     * @throws EmailSendingException if the configuration names no supported transport
     */
    private OutboundEmailTransport createTransport() throws EmailSendingException {
        if (emailConfig == null || emailConfig.getEmailType() == null) {
            throw new EmailSendingException("Invalid email configuration");
        }
        switch (emailConfig.getEmailType()) {
            case SMTP:
                return createSmtpSender();
            case API:
                return createApiSender();
            default:
                throw new EmailSendingException("Invalid email configuration");
        }
    }

    /**
     * Captures the exact payload this configuration will transmit and wraps it in an archive request.
     *
     * <p>Must run before {@link #sendPrepared()}: the point of preparing is that the bytes handed
     * to the archive are the bytes later put on the wire, not a reconstruction of them.</p>
     *
     * <p>There is no "is archiving supported?" question to ask first. Any configuration that can
     * send resolves to an {@link OutboundEmailTransport} through {@link #createTransport()}, and
     * every such transport supplies its own artifact; a configuration that resolves to nothing
     * throws here exactly as it would from {@link #send()}.</p>
     *
     * @param emailLog persisted email log that owns the archive artifact
     * @return archive request containing the transport's artifact and attachment metadata
     * @throws EmailSendingException if the configuration names no transport, or preparation fails
     * @throws SecurityException if the current user lacks the required "_email" write privilege
     * @since 2026-07-20
     */
    public OutboundEmailArchiveDto prepareOutboundArchive(EmailLog emailLog) throws EmailSendingException {
        assertEmailWritePrivilege();
        if (preparedTransport != null) {
            throw new EmailSendingException("Outbound message has already been prepared");
        }

        preparedTransport = createTransport();
        try {
            byte[] artifactBytes = preparedTransport.prepareArtifactBytes();

            OutboundEmailArchiveDto archiveRequest = new OutboundEmailArchiveDto();
            archiveRequest.setEmailLog(emailLog);
            archiveRequest.setArtifactBytes(artifactBytes);
            archiveRequest.setFileName(preparedTransport.getArchiveFileName(emailLog));
            archiveRequest.setContentType(preparedTransport.getArchiveContentType());
            archiveRequest.setArtifactType(preparedTransport.getArchiveArtifactType());
            archiveRequest.setTransportType(emailConfig.getEmailType().name());
            archiveRequest.setProviderName(emailConfig.getEmailProvider() != null ? emailConfig.getEmailProvider().name() : null);
            archiveRequest.setAttachments(preparedTransport.describePreparedAttachments());
            return archiveRequest;
        } catch (EmailSendingException | RuntimeException e) {
            discardPrepared();
            throw e;
        }
    }

    /**
     * Sends the payload previously captured for outbound archiving.
     *
     * @throws EmailSendingException if nothing has been prepared or transport delivery fails
     * @throws SecurityException if the current user lacks the required "_email" write privilege
     * @since 2026-07-20
     */
    public void sendPrepared() throws EmailSendingException {
        try {
            assertEmailWritePrivilege();
            if (preparedTransport == null) {
                throw new EmailSendingException("Outbound message must be prepared before sending");
            }
            preparedTransport.sendPrepared();
        } finally {
            discardPrepared();
        }
    }

    /**
     * Releases a prepared payload when archiving or another pre-send step fails.
     */
    public void discardPrepared() {
        if (preparedTransport != null) {
            preparedTransport.discardPrepared();
            preparedTransport = null;
        }
    }

    /**
     * Resolves the API-based transport for the configured provider.
     *
     * <p>Currently supported API providers:</p>
     * <ul>
     *   <li><strong>SendGrid:</strong> the SendGrid Web API v3 mail/send endpoint</li>
     * </ul>
     *
     * <p>An API configuration naming any other provider throws. That is deliberate and it is the
     * same refusal the caller would get for an unknown {@code EmailType}: the enum permits
     * providers that have no implementation, and a send that cannot be archived is a retention
     * gap in a legal record, so the configuration is rejected rather than sent unrecorded.</p>
     *
     * @return the SendGrid transport
     * @throws EmailSendingException if the configured API provider has no implementation
     */
    private OutboundEmailTransport createApiSender() throws EmailSendingException {
        if (emailConfig.getEmailProvider() == null) {
            throw new EmailSendingException("Invalid email configuration");
        }
        switch (emailConfig.getEmailProvider()) {
            case SENDGRID:
                return new APISendGridEmailSender(loggedInInfo, emailConfig, recipients, subject, body, additionalParams, attachments);
            default:
                throw new EmailSendingException("Invalid email configuration");
        }
    }

    private SMTPEmailSender createSmtpSender() {
        if (emailConfig.getEmailProvider() == EmailConfig.EmailProvider.LOCAL) {
            return new LocalSMTPEmailSender(loggedInInfo, emailConfig, recipients, subject, body, attachments);
        }
        return new SMTPEmailSender(loggedInInfo, emailConfig, recipients, subject, body, attachments);
    }

    private void assertEmailWritePrivilege() {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_email)");
        }
    }

}
