package io.github.carlos_emr.carlos.email.helpers;

import java.io.File;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.core.io.ByteArrayResource;
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
 * @see io.github.carlos_emr.carlos.commn.model.EmailConfig
 * @see io.github.carlos_emr.carlos.commn.model.EmailAttachment
 * @see io.github.carlos_emr.carlos.utility.EmailSendingException
 * @see io.github.carlos_emr.carlos.managers.SecurityInfoManager
 * @since 2026-01-24
 */
public class SMTPEmailSender {
    private static final HexFormat HEX_FORMAT = HexFormat.of();
    private static final String DEFAULT_ATTACHMENT_CONTENT_TYPE = "application/octet-stream";

    private final Logger logger = MiscUtils.getLogger();
    private LoggedInInfo loggedInInfo;

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private JavaMailSender javaMailSender = SpringUtils.getBean(JavaMailSender.class);

    private EmailConfig emailConfig;
    private String[] recipients = new String[0];
    private String subject;
    private String body;
    private List<EmailAttachment> attachments;
    private MimeMessage preparedMessage;
    private List<PreparedAttachment> preparedAttachments = List.of();

    public static final class PreparedAttachment {
        private final EmailAttachment attachment;
        private final Path path;
        private final String contentType;
        private final String sha256Hash;
        private final long byteSize;

        private PreparedAttachment(EmailAttachment attachment, Path path, String contentType, String sha256Hash, long byteSize) {
            this.attachment = attachment;
            this.path = path;
            this.contentType = contentType;
            this.sha256Hash = sha256Hash;
            this.byteSize = byteSize;
        }

        public EmailAttachment getAttachment() {
            return attachment;
        }

        public Path getPath() {
            return path;
        }

        public String getContentType() {
            return contentType;
        }

        public String getSha256Hash() {
            return sha256Hash;
        }

        public long getByteSize() {
            return byteSize;
        }
    }

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
     * <p>Security: Requires the _email write privilege. Throws SecurityException
     * if the user lacks required permissions.</p>
     *
     * @throws EmailSendingException if email transmission fails due to network errors,
     *         invalid configuration, authentication failure, or attachment processing errors
     * @throws SecurityException if the user lacks required _email write privilege
     */
    public void send() throws EmailSendingException {
        prepareMessageBytes();
        sendPreparedMessage();
    }

    /**
     * Builds, finalizes, and serializes the SMTP message that will later be sent.
     *
     * <p>Attachments are read once into the prepared MIME message and their archive
     * metadata is recorded from that same byte snapshot. Callers must archive the
     * returned RFC 822 bytes before invoking {@link #sendPreparedMessage()}.</p>
     *
     * @return finalized RFC 822 message bytes suitable for outbound archive storage
     * @throws EmailSendingException if message construction, attachment reading, or serialization fails
     * @throws SecurityException if the current user lacks the required "_email" write privilege
     */
    public byte[] prepareMessageBytes() throws EmailSendingException {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_email)");
        }

        javaMailSender = createTLSMailSender(emailConfig);
        preparedMessage = null;
        preparedAttachments = List.of();
        MimeMessage message = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(emailConfig.getSenderEmail(), emailConfig.getSenderFullName());
            helper.setTo(recipients);
            helper.setSubject(subject);
            helper.setText(body, false);
            List<PreparedAttachment> attachmentSnapshots = addAttachments(helper, attachments);
            message.saveChanges();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            message.writeTo(outputStream);
            preparedAttachments = attachmentSnapshots;
            preparedMessage = message;
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new EmailSendingException(e.getMessage(), e);
        }
    }

    /**
     * Sends the message previously finalized by {@link #prepareMessageBytes()}.
     *
     * @throws EmailSendingException if the message has not been prepared or transport delivery fails
     */
    public void sendPreparedMessage() throws EmailSendingException {
        if (preparedMessage == null) {
            throw new EmailSendingException("SMTP message must be prepared before sending");
        }
        try {
            javaMailSender.send(preparedMessage);
        } catch (Exception e) {
            throw new EmailSendingException(e.getMessage(), e);
        }
    }

    public List<PreparedAttachment> getPreparedAttachments() {
        return preparedAttachments;
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
    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    private List<PreparedAttachment> addAttachments(MimeMessageHelper helper, List<EmailAttachment> attachments) throws MessagingException, IOException {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }

        List<PreparedAttachment> attachmentSnapshots = new ArrayList<>();
        for (EmailAttachment attachment : attachments) {
            if (attachment == null || attachment.getFilePath() == null) {
                throw new MessagingException("Email attachment path is required");
            }
            if (attachment.getFileName() == null || attachment.getFileName().isBlank()) {
                throw new MessagingException("Email attachment file name is required");
            }

            Path attachmentPath = PathValidationUtils.resolveTrustedPath(new File(attachment.getFilePath())).toPath();
            byte[] attachmentBytes = Files.readAllBytes(attachmentPath);
            String contentType = resolveAttachmentContentType(helper, attachment, attachmentPath);
            helper.addAttachment(attachment.getFileName(), new ByteArrayResource(attachmentBytes, attachment.getFileName()), contentType);
            attachmentSnapshots.add(new PreparedAttachment(attachment, attachmentPath, contentType, sha256Hex(attachmentBytes), attachmentBytes.length));
        }
        return List.copyOf(attachmentSnapshots);
    }

    private String resolveAttachmentContentType(MimeMessageHelper helper, EmailAttachment attachment, Path attachmentPath) {
        String fileName = attachment.getFileName();
        String contentType = null;
        if (fileName != null && !fileName.isBlank()) {
            contentType = helper.getFileTypeMap().getContentType(fileName);
        }

        if (isBlankOrDefaultContentType(contentType)) {
            try {
                contentType = Files.probeContentType(attachmentPath);
            } catch (IOException ignored) {
            }
        }

        if (isBlankOrDefaultContentType(contentType) && attachmentPath.getFileName() != null) {
            contentType = URLConnection.guessContentTypeFromName(attachmentPath.getFileName().toString());
        }

        return contentType != null && !contentType.isBlank() ? contentType : DEFAULT_ATTACHMENT_CONTENT_TYPE;
    }

    private boolean isBlankOrDefaultContentType(String contentType) {
        return contentType == null || contentType.isBlank() || DEFAULT_ATTACHMENT_CONTENT_TYPE.equals(contentType);
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX_FORMAT.formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

}
