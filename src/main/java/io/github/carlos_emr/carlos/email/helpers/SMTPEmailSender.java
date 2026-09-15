package io.github.carlos_emr.carlos.email.helpers;

import java.io.File;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FilterOutputStream;
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
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailPreparationException;
import io.github.carlos_emr.carlos.email.core.EmailConfigSecrets;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * SMTP email sender for CARLOS EMR healthcare system.
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
    static final long MAX_PREPARED_MESSAGE_BYTES = 50L * 1024L * 1024L;
    static final int SMTP_CONNECTION_TIMEOUT_MILLIS = 30_000;
    static final int SMTP_IO_TIMEOUT_MILLIS = 60_000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HexFormat HEX_FORMAT = HexFormat.of();
    private static final String DEFAULT_ATTACHMENT_CONTENT_TYPE = "application/octet-stream";

    private final Logger logger = MiscUtils.getLogger();
    private LoggedInInfo loggedInInfo;

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private JavaMailSender javaMailSender = SpringUtils.getBean(JavaMailSender.class);
    private NioFileManager nioFileManager = SpringUtils.getBean(NioFileManager.class);

    private EmailConfig emailConfig;
    private String[] recipients = new String[0];
    private String subject;
    private String body;
    private List<EmailAttachment> attachments;
    private MimeMessage preparedMessage;
    private List<PreparedAttachment> preparedAttachments = List.of();
    private List<Path> preparedAttachmentSnapshots = List.of();

    /**
     * Metadata captured for an attachment that has been embedded in a prepared SMTP message.
     *
     * @since 2026-07-20
     */
    public static final class PreparedAttachment {
        private final EmailAttachment attachment;
        private final String contentType;
        private final String sha256Hash;
        private final long byteSize;

        private PreparedAttachment(EmailAttachment attachment, String contentType, String sha256Hash, long byteSize) {
            this.attachment = attachment;
            this.contentType = contentType;
            this.sha256Hash = sha256Hash;
            this.byteSize = byteSize;
        }

        public EmailAttachment getAttachment() {
            return attachment;
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
     * @since 2026-07-20
     */
    public byte[] prepareMessageBytes() throws EmailSendingException {
        assertEmailWritePrivilege();

        discardPreparedMessage();
        List<Path> attachmentSnapshotPaths = new ArrayList<>();
        try {
            javaMailSender = createTLSMailSender(emailConfig);
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(emailConfig.getSenderEmail(), emailConfig.getSenderFullName());
            helper.setTo(recipients);
            helper.setSubject(subject);
            helper.setText(body, false);
            List<PreparedAttachment> attachmentSnapshots = addAttachments(helper, attachments, attachmentSnapshotPaths);
            message.saveChanges();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            message.writeTo(new LimitedOutputStream(outputStream, MAX_PREPARED_MESSAGE_BYTES));
            preparedAttachments = attachmentSnapshots;
            preparedAttachmentSnapshots = List.copyOf(attachmentSnapshotPaths);
            preparedMessage = message;
            return outputStream.toByteArray();
        } catch (SecurityException | EmailSendingException e) {
            deleteAttachmentSnapshots(attachmentSnapshotPaths);
            throw e;
        } catch (Exception e) {
            deleteAttachmentSnapshots(attachmentSnapshotPaths);
            throw new EmailSendingException("The SMTP message could not be prepared.", e);
        }
    }

    /**
     * Sends the message previously finalized by {@link #prepareMessageBytes()}.
     *
     * @throws EmailSendingException if the message has not been prepared or transport delivery fails
     * @throws SecurityException if the current user lacks the required "_email" write privilege
     * @since 2026-07-20
     */
    public void sendPreparedMessage() throws EmailSendingException {
        try {
            assertEmailWritePrivilege();
            if (preparedMessage == null) {
                throw new EmailSendingException("SMTP message must be prepared before sending");
            }
            javaMailSender.send(preparedMessage);
        } catch (SecurityException | EmailSendingException e) {
            throw e;
        } catch (MailAuthenticationException | MailPreparationException e) {
            throw new EmailSendingException("SMTP failed before accepting the message.", e);
        } catch (Exception e) {
            if (isDefinitelyUnsent(e)) {
                throw new EmailSendingException("SMTP failed before accepting the message.", e);
            }
            // A lost SMTP acknowledgement cannot prove non-delivery; do not invite a duplicate.
            throw new EmailSendingException(
                    "SMTP transport did not confirm whether the message was accepted.", e, true);
        } finally {
            discardPreparedMessage();
        }
    }

    private boolean isDefinitelyUnsent(Exception failure) {
        if (!(failure instanceof org.springframework.mail.MailSendException sendFailure)) {
            return false;
        }
        // This sender dispatches exactly one message. Spring's connectTransport failure reports
        // the same exception as both the top-level cause and that message's failure. Failures
        // during DATA have no top-level cause; closing an accepted connection has no failed message.
        // Do not infer the stage from a TLS/timeout exception type or from remote diagnostic text.
        Exception[] messageFailures = sendFailure.getMessageExceptions();
        return sendFailure.getCause() != null && messageFailures.length == 1
                && messageFailures[0] == sendFailure.getCause();
    }

    /**
     * Releases any attachment snapshots held by a prepared message that will not be sent.
     */
    public void discardPreparedMessage() {
        deleteAttachmentSnapshots(preparedAttachmentSnapshots);
        preparedAttachmentSnapshots = List.of();
        preparedAttachments = List.of();
        preparedMessage = null;
    }

    /**
     * Returns attachment metadata captured while preparing the current SMTP message.
     *
     * @return prepared attachment metadata, or an empty list when no message has been prepared
     * @since 2026-07-20
     */
    public List<PreparedAttachment> getPreparedAttachments() {
        return preparedAttachments;
    }

    private void assertEmailWritePrivilege() {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_email)");
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
     * <p>All required fields are validated here, before any message bytes are built. An
     * archive-first caller writes a durable eDoc artifact between preparation and transport, so a
     * configuration that could never have sent must fail before that artifact exists rather than
     * after.</p>
     *
     * @param emailConfig EmailConfig the email configuration containing JSON-encoded SMTP settings
     * @return JavaMailSender configured mail sender instance ready for message transmission
     * @throws EmailSendingException if the configuration JSON is invalid or missing required fields
     */
    protected JavaMailSender createTLSMailSender(EmailConfig emailConfig) throws EmailSendingException {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        JsonNode jsonNode = parseConfig(emailConfig);
        String host = requiredText(jsonNode, "host", emailConfig).trim();
        String port = requiredText(jsonNode, "port", emailConfig).trim();
        String username = requiredText(jsonNode, "username", emailConfig).trim();
        // Decrypt the at-rest credential only here, at send time. Legacy plaintext passwords
        // pass through unchanged during the migration window. Missing or blank passwords fail
        // before an archive is created for this authenticated transport.
        JsonNode passwordNode = jsonNode.get("password");
        if (passwordNode != null && !passwordNode.isNull() && !passwordNode.isValueNode()) {
            throw invalidConfiguration(emailConfig);
        }
        String password = (passwordNode != null && !passwordNode.isNull())
                ? EmailConfigSecrets.decryptSecret(passwordNode.asText())
                : null;

        if (password == null || password.isBlank()) {
            throw invalidConfiguration(emailConfig);
        }
        mailSender.setHost(host);
        mailSender.setPort(parsePort(port, invalidConfiguration(emailConfig).getMessage()));
        mailSender.setUsername(username);
        mailSender.setPassword(password);

        Properties properties = new Properties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.starttls.required", "true");
        properties.put("mail.smtp.ssl.protocols", "TLSv1.2");
        properties.put("mail.debug", "false");

        applySmtpTimeouts(properties);
        mailSender.setJavaMailProperties(properties);
        return mailSender;
    }

    /** Parses active SMTP configuration without retaining credential-bearing parser errors. */
    protected JsonNode parseConfig(EmailConfig emailConfig) throws EmailSendingException {
        String configJson = emailConfig != null ? emailConfig.getConfigDetailsJson() : null;
        if (configJson == null || configJson.isBlank()) {
            throw invalidConfiguration(emailConfig);
        }
        try {
            JsonNode config = OBJECT_MAPPER.readTree(configJson);
            if (config == null || !config.isObject()) {
                throw invalidConfiguration(emailConfig);
            }
            return config;
        } catch (IOException e) {
            // Do not retain a Jackson cause: parse exceptions may include fragments of the config
            // JSON, which contains the credential this change is intended to protect.
            throw invalidConfiguration(emailConfig);
        }
    }

    protected String requiredText(JsonNode config, String field, EmailConfig emailConfig)
            throws EmailSendingException {
        JsonNode value = config.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw invalidConfiguration(emailConfig);
        }
        return value.asText();
    }

    protected EmailSendingException invalidConfiguration(EmailConfig emailConfig) {
        String senderEmail = emailConfig != null ? emailConfig.getSenderEmail() : "unknown";
        return new EmailSendingException("Invalid credentials configured for " + senderEmail);
    }

    static void applySmtpTimeouts(Properties properties) {
        properties.put("mail.smtp.connectiontimeout",
                String.valueOf(SMTP_CONNECTION_TIMEOUT_MILLIS));
        properties.put("mail.smtp.timeout", String.valueOf(SMTP_IO_TIMEOUT_MILLIS));
        properties.put("mail.smtp.writetimeout", String.valueOf(SMTP_IO_TIMEOUT_MILLIS));
    }

    /**
     * Parses the configured SMTP port, failing closed on a non-numeric or out-of-range value.
     *
     * @param port configured port value
     * @param invalidCredentialsMessage PHI-free failure message reused across all validation paths
     * @return the parsed port
     * @throws EmailSendingException if the port is not a valid TCP port number
     */
    protected int parsePort(String port, String invalidCredentialsMessage) throws EmailSendingException {
        try {
            int parsedPort = Integer.parseInt(port);
            if (parsedPort < 1 || parsedPort > 65535) {
                throw new EmailSendingException(invalidCredentialsMessage);
            }
            return parsedPort;
        } catch (NumberFormatException e) {
            throw new EmailSendingException(invalidCredentialsMessage, e);
        }
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
    private List<PreparedAttachment> addAttachments(MimeMessageHelper helper,
                                                     List<EmailAttachment> attachments,
                                                     List<Path> attachmentSnapshotPaths) throws MessagingException, IOException {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }

        List<PreparedAttachment> attachmentSnapshots = new ArrayList<>();
        long remainingAttachmentBytes = MAX_PREPARED_MESSAGE_BYTES;
        for (EmailAttachment attachment : attachments) {
            if (attachment == null || attachment.getFilePath() == null) {
                throw new MessagingException("Email attachment path is required");
            }
            if (attachment.getFileName() == null || attachment.getFileName().isBlank()) {
                throw new MessagingException("Email attachment file name is required");
            }

            Path attachmentPath = PathValidationUtils.resolveTrustedPath(new File(attachment.getFilePath())).toPath();
            String contentType = resolveAttachmentContentType(helper, attachment, attachmentPath);
            Path attachmentSnapshot = nioFileManager.createManagedTempFile(
                    "carlos-smtp-attachment-", ".snapshot");
            // Register before copying so any checked or unchecked preparation failure cleans up.
            attachmentSnapshotPaths.add(attachmentSnapshot);
            try (InputStream source = Files.newInputStream(attachmentPath);
                    OutputStream target = new LimitedOutputStream(Files.newOutputStream(attachmentSnapshot),
                            remainingAttachmentBytes)) {
                // Preserve the managed file's owner-only permissions: replacing it copies source permissions.
                source.transferTo(target);
            }
            long byteSize = Files.size(attachmentSnapshot);
            remainingAttachmentBytes -= byteSize;
            String sha256Hash = sha256Hex(attachmentSnapshot);
            helper.addAttachment(attachment.getFileName(), new FileSystemResource(attachmentSnapshot), contentType);
            attachmentSnapshots.add(new PreparedAttachment(attachment, contentType, sha256Hash, byteSize));
        }
        return List.copyOf(attachmentSnapshots);
    }

    /** Bounds both attachment snapshots and the final MIME serialization before transport. */
    private static final class LimitedOutputStream extends FilterOutputStream {
        private long remaining;

        private LimitedOutputStream(OutputStream output, long limit) {
            super(output);
            remaining = limit;
        }

        @Override
        public void write(int value) throws IOException {
            requireCapacity(1);
            out.write(value);
            remaining--;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            java.util.Objects.checkFromIndexSize(offset, length, bytes.length);
            requireCapacity(length);
            out.write(bytes, offset, length);
            remaining -= length;
        }

        private void requireCapacity(int length) throws IOException {
            if (length > remaining) {
                throw new IOException("Prepared SMTP message exceeds the 50 MiB archive limit");
            }
        }
    }

    private void deleteAttachmentSnapshots(List<Path> snapshotPaths) {
        for (Path snapshotPath : snapshotPaths) {
            try {
                Files.deleteIfExists(snapshotPath);
            } catch (IOException | RuntimeException e) {
                logger.warn("Unable to delete prepared SMTP attachment snapshot; causeType={}",
                        e.getClass().getSimpleName());
            }
        }
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
                // MIME probing is optional; filename detection and octet-stream remain available.
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

    private String sha256Hex(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return HEX_FORMAT.formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable", e);
        }
    }

}
