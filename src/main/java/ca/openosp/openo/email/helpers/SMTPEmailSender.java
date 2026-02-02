//CHECKSTYLE:OFF
package ca.openosp.openo.email.helpers;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.model.EmailAttachment;
import ca.openosp.openo.commn.model.EmailConfig;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.EmailSendingException;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * SMTP email sender for OpenO EMR healthcare system.
 *
 * <p>Provides secure email transmission functionality with TLS encryption for
 * healthcare communications. This class handles the construction and delivery
 * of email messages with support for attachments, ensuring all email operations
 * comply with security requirements through privilege checks.</p>
 *
 * <p>The sender uses JavaMailSender with configurable SMTP settings extracted
 * from EmailConfig objects. All email transmissions require the _email write
 * privilege.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>TLS 1.2 encryption for secure transmission</li>
 *   <li>Multi-recipient support</li>
 *   <li>File attachment handling</li>
 *   <li>Security privilege validation</li>
 *   <li>Configurable SMTP server settings</li>
 * </ul>
 *
 * @see ca.openosp.openo.commn.model.EmailConfig
 * @see ca.openosp.openo.commn.model.EmailAttachment
 * @see ca.openosp.openo.utility.EmailSendingException
 * @see ca.openosp.openo.managers.SecurityInfoManager
 * @since 2026-01-24
 */
public class SMTPEmailSender {
    private final Logger logger = MiscUtils.getLogger();
    private LoggedInInfo loggedInInfo;

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private JavaMailSender javaMailSender = SpringUtils.getBean(JavaMailSender.class);

    private EmailConfig emailConfig;
    private String[] recipients = new String[0];
    private String subject;
    private String body;
    private List<EmailAttachment> attachments;

    /**
     * Private default constructor to prevent instantiation without required parameters.
     */
    private SMTPEmailSender() {
    }

    /**
     * Constructs an SMTP email sender with all required email components.
     *
     * <p>Initializes a new email sender instance with the specified configuration,
     * recipients, subject, body content, and optional attachments. The logged-in
     * user context is required for security privilege validation during send operations.</p>
     *
     * @param loggedInInfo LoggedInInfo the current user's session information for security validation
     * @param emailConfig EmailConfig the SMTP server configuration including host, port, and credentials
     * @param recipients String[] array of recipient email addresses
     * @param subject String the email subject line
     * @param body String the email body content (plain text)
     * @param attachments List&lt;EmailAttachment&gt; optional list of file attachments, may be null
     */
    public SMTPEmailSender(LoggedInInfo loggedInInfo, EmailConfig emailConfig, String[] recipients, String subject, String body, List<EmailAttachment> attachments) {
        this.loggedInInfo = loggedInInfo;
        this.emailConfig = emailConfig;
        this.recipients = recipients;
        this.subject = subject;
        this.body = body;
        this.attachments = attachments;
    }

    /**
     * Sends the configured email message via SMTP with TLS encryption.
     *
     * <p>Validates user privileges, creates a TLS-enabled mail sender, constructs
     * a MIME message with the configured subject, body, and attachments, and
     * transmits the message to all specified recipients.</p>
     *
     * <p>Security: Requires the _email write privilege. Throws RuntimeException
     * if the user lacks required permissions.</p>
     *
     * @throws EmailSendingException if email transmission fails due to network errors,
     *         invalid configuration, authentication failure, or attachment processing errors
     * @throws RuntimeException if the user lacks required _email write privilege
     */
    public void send() throws EmailSendingException {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_email)");
        }

        javaMailSender = createTLSMailSender(emailConfig);
        MimeMessage message = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(emailConfig.getSenderEmail(), emailConfig.getSenderFullName());
            helper.setTo(recipients);
            helper.setSubject(subject);
            helper.setText(body, false);
            addAttachments(helper, attachments);
            javaMailSender.send(message);
        } catch (Exception e) {
            throw new EmailSendingException(e.getMessage(), e);
        }
    }

    /**
     * Creates a JavaMailSender configured for TLS-encrypted SMTP transmission.
     *
     * <p>Parses the EmailConfig's JSON configuration to extract SMTP server settings
     * (host, port, username, password) and constructs a JavaMailSenderImpl with
     * TLS 1.2 encryption enabled. The mail sender is configured with SMTP authentication
     * and requires STARTTLS for secure transmission.</p>
     *
     * <p>SMTP Properties configured:</p>
     * <ul>
     *   <li>Transport protocol: smtp</li>
     *   <li>SMTP authentication: enabled</li>
     *   <li>STARTTLS: enabled and required</li>
     *   <li>SSL protocol: TLSv1.2</li>
     *   <li>Debug mode: disabled</li>
     * </ul>
     *
     * @param emailConfig EmailConfig the email configuration containing JSON-encoded SMTP settings
     * @return JavaMailSender configured mail sender instance ready for message transmission
     * @throws EmailSendingException if the configuration JSON is invalid or missing required fields
     */
    protected JavaMailSender createTLSMailSender(EmailConfig emailConfig) throws EmailSendingException {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode jsonNode = objectMapper.readTree(emailConfig.getConfigDetailsJson());
            String host = jsonNode.get("host").asText();
            String port = jsonNode.get("port").asText();
            String username = jsonNode.get("username").asText();
            String password = jsonNode.get("password").asText();

            mailSender.setHost(host);
            mailSender.setPort(Integer.parseInt(port));
            mailSender.setUsername(username);
            mailSender.setPassword(password);

            Properties properties = new Properties();
            properties.put("mail.transport.protocol", "smtp");
            properties.put("mail.smtp.auth", "true");
            properties.put("mail.smtp.starttls.enable", "true");
            properties.put("mail.smtp.starttls.required", "true");
            properties.put("mail.smtp.ssl.protocols", "TLSv1.2");
            properties.put("mail.debug", "false");

            mailSender.setJavaMailProperties(properties);
        } catch (IOException e) {
            throw new EmailSendingException("Invalid credentials configured for " + emailConfig.getSenderEmail(), e);
        }
        return mailSender;
    }

    /**
     * Attaches files to the email message being constructed.
     *
     * <p>Iterates through the provided list of EmailAttachment objects and adds
     * each file to the MIME message using the MimeMessageHelper. If the attachments
     * list is null, no action is taken.</p>
     *
     * @param helper MimeMessageHelper the message helper for adding attachments to the MIME message
     * @param attachments List&lt;EmailAttachment&gt; list of file attachments to add, may be null
     * @throws MessagingException if attachment processing fails due to invalid file paths
     *         or I/O errors when accessing attachment files
     */
    private void addAttachments(MimeMessageHelper helper, List<EmailAttachment> attachments) throws MessagingException {
        if (attachments == null) {
            return;
        }

        for (EmailAttachment attachment : attachments) {
            helper.addAttachment(attachment.getFileName(), new File(attachment.getFilePath()));
        }
    }

}