package io.github.carlos_emr.carlos.email.core;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchive;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveAttachmentDto;
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
    private static final String RFC822_CONTENT_TYPE = "message/rfc822";

    private LoggedInInfo loggedInInfo;

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private EmailConfig emailConfig;
    private String[] recipients = new String[0];
    private String subject;
    private String body;
    private String additionalParams;
    private List<EmailAttachment> attachments;
    private SMTPEmailSender preparedSmtpSendHelper;

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

        switch (emailConfig.getEmailType()) {
            case SMTP:
                createSmtpSender().send();
                break;
            case API:
                sendAPIMail();
                break;
            default:
                throw new EmailSendingException("Invalid email configuration");
        }
    }

    /**
     * Indicates whether this sender can prepare a durable outbound archive artifact.
     *
     * @return {@code true} for SMTP configurations, where the finalized RFC 822 message can be captured before transport
     * @since 2026-07-20
     */
    public boolean supportsOutboundArchive() {
        return emailConfig != null && emailConfig.getEmailType() == EmailConfig.EmailType.SMTP;
    }

    /**
     * Prepares the finalized SMTP message bytes and wraps them in an archive request.
     *
     * <p>This method must run before {@link #sendPrepared()} so the exact prepared
     * message is archived before it is transported.</p>
     *
     * @param emailLog persisted email log that owns the archive artifact
     * @return archive request containing the RFC 822 artifact and attachment metadata
     * @throws EmailSendingException if this configuration cannot be archived or message preparation fails
     * @throws SecurityException if the current user lacks the required "_email" write privilege
     * @since 2026-07-20
     */
    public OutboundEmailArchiveDto prepareOutboundArchive(EmailLog emailLog) throws EmailSendingException {
        assertEmailWritePrivilege();
        if (!supportsOutboundArchive()) {
            throw new EmailSendingException("Outbound email archive is not supported for this email configuration");
        }
        if (preparedSmtpSendHelper != null) {
            throw new EmailSendingException("SMTP message has already been prepared");
        }

        preparedSmtpSendHelper = createSmtpSender();
        try {
            byte[] artifactBytes = preparedSmtpSendHelper.prepareMessageBytes();

            OutboundEmailArchiveDto archiveRequest = new OutboundEmailArchiveDto();
            archiveRequest.setEmailLog(emailLog);
            archiveRequest.setArtifactBytes(artifactBytes);
            archiveRequest.setFileName("outbound-email-" + emailLog.getId() + ".eml");
            archiveRequest.setContentType(RFC822_CONTENT_TYPE);
            archiveRequest.setArtifactType(OutboundEmailArchive.ARTIFACT_TYPE_SMTP_RFC822);
            archiveRequest.setTransportType(emailConfig.getEmailType().name());
            archiveRequest.setProviderName(emailConfig.getEmailProvider() != null ? emailConfig.getEmailProvider().name() : null);
            archiveRequest.setAttachments(buildAttachmentArchiveMetadata(preparedSmtpSendHelper.getPreparedAttachments()));
            return archiveRequest;
        } catch (EmailSendingException | RuntimeException e) {
            preparedSmtpSendHelper.discardPreparedMessage();
            preparedSmtpSendHelper = null;
            throw e;
        }
    }

    /**
     * Sends the SMTP message previously prepared for outbound archiving.
     *
     * @throws EmailSendingException if no prepared SMTP message exists or transport delivery fails
     * @throws SecurityException if the current user lacks the required "_email" write privilege
     * @since 2026-07-20
     */
    public void sendPrepared() throws EmailSendingException {
        assertEmailWritePrivilege();
        if (preparedSmtpSendHelper == null) {
            throw new EmailSendingException("SMTP message must be prepared before sending");
        }
        preparedSmtpSendHelper.sendPreparedMessage();
    }

    /**
     * Releases a prepared SMTP message when archiving or another pre-send step fails.
     */
    public void discardPrepared() {
        if (preparedSmtpSendHelper != null) {
            preparedSmtpSendHelper.discardPreparedMessage();
            preparedSmtpSendHelper = null;
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

    private List<OutboundEmailArchiveAttachmentDto> buildAttachmentArchiveMetadata(List<SMTPEmailSender.PreparedAttachment> preparedAttachments) throws EmailSendingException {
        if (preparedAttachments == null || preparedAttachments.isEmpty()) {
            return List.of();
        }

        java.util.ArrayList<OutboundEmailArchiveAttachmentDto> attachmentDtos = new java.util.ArrayList<>();
        for (SMTPEmailSender.PreparedAttachment preparedAttachment : preparedAttachments) {
            attachmentDtos.add(buildAttachmentArchiveMetadata(preparedAttachment));
        }
        return attachmentDtos;
    }

    private OutboundEmailArchiveAttachmentDto buildAttachmentArchiveMetadata(SMTPEmailSender.PreparedAttachment preparedAttachment) throws EmailSendingException {
        if (preparedAttachment == null || preparedAttachment.getAttachment() == null) {
            throw new EmailSendingException("Prepared attachment is required for archive metadata");
        }

        EmailAttachment attachment = preparedAttachment.getAttachment();
        OutboundEmailArchiveAttachmentDto attachmentDto = new OutboundEmailArchiveAttachmentDto();
        attachmentDto.setFileName(attachment.getFileName());
        attachmentDto.setContentType(preparedAttachment.getContentType());
        attachmentDto.setSha256Hash(preparedAttachment.getSha256Hash());
        attachmentDto.setByteSize(preparedAttachment.getByteSize());
        attachmentDto.setSourceDocumentType(attachment.getDocumentType() != null ? attachment.getDocumentType().name() : null);
        attachmentDto.setSourceDocumentId(attachment.getDocumentId());
        return attachmentDto;
    }
}
