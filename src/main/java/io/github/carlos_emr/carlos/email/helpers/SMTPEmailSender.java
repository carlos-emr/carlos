package io.github.carlos_emr.carlos.email.helpers;

import java.io.File;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchive;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveAttachmentDto;
import io.github.carlos_emr.carlos.email.core.OutboundEmailTransport;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.core.io.FileSystemResource;
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
public class SMTPEmailSender implements OutboundEmailTransport {
    private static final HexFormat HEX_FORMAT = HexFormat.of();
    private static final String DEFAULT_ATTACHMENT_CONTENT_TYPE = "application/octet-stream";
    private static final String RFC822_CONTENT_TYPE = "message/rfc822";

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
        prepareArtifactBytes();
        sendPrepared();
    }

    /**
     * Builds, finalizes, and serializes the SMTP message that will later be sent.
     *
     * <p>Attachments are read once into the prepared MIME message and their archive
     * metadata is recorded from that same byte snapshot. Callers must archive the
     * returned RFC 822 bytes before invoking {@link #sendPrepared()}.</p>
     *
     * @return finalized RFC 822 message bytes suitable for outbound archive storage
     * @throws EmailSendingException if message construction, attachment reading, or serialization fails
     * @throws SecurityException if the current user lacks the required "_email" write privilege
     * @since 2026-07-20
     */
    public byte[] prepareArtifactBytes() throws EmailSendingException {
        assertEmailWritePrivilege();

        discardPrepared();
        javaMailSender = createTLSMailSender(emailConfig);
        MimeMessage message = javaMailSender.createMimeMessage();
        List<Path> attachmentSnapshotPaths = new ArrayList<>();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(emailConfig.getSenderEmail(), emailConfig.getSenderFullName());
            helper.setTo(recipients);
            helper.setSubject(subject);
            helper.setText(body, false);
            List<PreparedAttachment> attachmentSnapshots = addAttachments(helper, attachments, attachmentSnapshotPaths);
            message.saveChanges();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            message.writeTo(outputStream);
            preparedAttachments = attachmentSnapshots;
            preparedAttachmentSnapshots = List.copyOf(attachmentSnapshotPaths);
            preparedMessage = message;
            return outputStream.toByteArray();
        } catch (Exception e) {
            deleteAttachmentSnapshots(attachmentSnapshotPaths);
            throw new EmailSendingException(e.getMessage(), e);
        }
    }

    /**
     * Sends the message previously finalized by {@link #prepareArtifactBytes()}.
     *
     * @throws EmailSendingException if the message has not been prepared or transport delivery fails
     * @throws SecurityException if the current user lacks the required "_email" write privilege
     * @since 2026-07-20
     */
    public void sendPrepared() throws EmailSendingException {
        try {
            assertEmailWritePrivilege();
            if (preparedMessage == null) {
                throw new EmailSendingException("SMTP message must be prepared before sending");
            }
            javaMailSender.send(preparedMessage);
        } catch (SecurityException | EmailSendingException e) {
            throw e;
        } catch (Exception e) {
            throw new EmailSendingException(e.getMessage(), e);
        } finally {
            discardPrepared();
        }
    }

    /**
     * Releases any attachment snapshots held by a prepared message that will not be sent.
     */
    public void discardPrepared() {
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

    // --- OutboundEmailTransport ---------------------------------------------------------------
    // send(), prepareArtifactBytes(), sendPrepared() and discardPrepared() above are the
    // interface methods directly; they carry no SMTP-specific aliases. Two public names for one
    // operation would let a caller -- or a mock -- bind to the name the archive path does not
    // use, which is the same class of silent divergence this interface exists to remove.

    @Override
    public String getArchiveArtifactType() {
        return OutboundEmailArchive.ARTIFACT_TYPE_SMTP_RFC822;
    }

    @Override
    public String getArchiveContentType() {
        return RFC822_CONTENT_TYPE;
    }

    @Override
    public String getArchiveFileName(EmailLog emailLog) {
        return "outbound-email-" + (emailLog != null ? emailLog.getId() : null) + ".eml";
    }

    /**
     * Derives archive attachment metadata from the MIME parts captured while preparing the
     * message, so the recorded hash and size describe the bytes actually attached rather than a
     * re-read of the source file, which could have changed underneath us.
     */
    @Override
    public List<OutboundEmailArchiveAttachmentDto> describePreparedAttachments() throws EmailSendingException {
        List<PreparedAttachment> snapshots = getPreparedAttachments();
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }

        List<OutboundEmailArchiveAttachmentDto> attachmentDtos = new ArrayList<>();
        for (PreparedAttachment preparedAttachment : snapshots) {
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
            attachmentDtos.add(attachmentDto);
        }
        return attachmentDtos;
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
        ObjectMapper objectMapper = new ObjectMapper();
        String invalidCredentialsMessage = "Invalid credentials configured for "
                + (emailConfig != null ? emailConfig.getSenderEmail() : "");
        if (emailConfig == null) {
            throw new EmailSendingException(invalidCredentialsMessage);
        }
        JsonNode jsonNode;
        try {
            jsonNode = objectMapper.readTree(emailConfig.getConfigDetailsJson());
        } catch (IOException | IllegalArgumentException e) {
            throw new EmailSendingException(invalidCredentialsMessage, e);
        }

        String host = requiredConfigValue(jsonNode, "host", invalidCredentialsMessage);
        String port = requiredConfigValue(jsonNode, "port", invalidCredentialsMessage);
        String username = requiredConfigValue(jsonNode, "username", invalidCredentialsMessage);
        String password = requiredConfigValue(jsonNode, "password", invalidCredentialsMessage);

        mailSender.setHost(host);
        mailSender.setPort(parsePort(port, invalidCredentialsMessage));
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
        return mailSender;
    }

    /**
     * Reads a required SMTP configuration value, failing closed when it is absent or blank.
     *
     * <p>{@link JsonNode#get(String)} returns {@code null} for an absent key, so calling
     * {@code asText()} directly on the result raises a {@link NullPointerException} that escapes
     * this class's declared {@link EmailSendingException} contract and bypasses the caller's
     * failure handling.</p>
     *
     * @param configNode parsed SMTP configuration document
     * @param fieldName required field to read
     * @param invalidCredentialsMessage PHI-free failure message reused across all validation paths
     * @return the non-blank configured value; surrounding whitespace is preserved for passwords
     * @throws EmailSendingException if the field is absent, null, or blank
     */
    private String requiredConfigValue(JsonNode configNode, String fieldName, String invalidCredentialsMessage) throws EmailSendingException {
        JsonNode fieldNode = configNode != null ? configNode.get(fieldName) : null;
        if (fieldNode == null || fieldNode.isNull() || fieldNode.asText().isBlank()) {
            throw new EmailSendingException(invalidCredentialsMessage);
        }
        return "password".equals(fieldName) ? fieldNode.asText() : fieldNode.asText().trim();
    }

    /**
     * Parses the configured SMTP port, failing closed on a non-numeric or out-of-range value.
     *
     * @param port configured port value
     * @param invalidCredentialsMessage PHI-free failure message reused across all validation paths
     * @return the parsed port
     * @throws EmailSendingException if the port is not a valid TCP port number
     */
    private int parsePort(String port, String invalidCredentialsMessage) throws EmailSendingException {
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
            try {
                Files.copy(attachmentPath, attachmentSnapshot, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                long byteSize = Files.size(attachmentSnapshot);
                String sha256Hash = sha256Hex(attachmentSnapshot);
                helper.addAttachment(attachment.getFileName(), new FileSystemResource(attachmentSnapshot), contentType);
                attachmentSnapshotPaths.add(attachmentSnapshot);
                attachmentSnapshots.add(new PreparedAttachment(attachment, contentType, sha256Hash, byteSize));
            } catch (IOException | MessagingException e) {
                try {
                    Files.deleteIfExists(attachmentSnapshot);
                } catch (IOException cleanupFailure) {
                    e.addSuppressed(cleanupFailure);
                }
                throw e;
            }
        }
        return List.copyOf(attachmentSnapshots);
    }

    private void deleteAttachmentSnapshots(List<Path> snapshotPaths) {
        for (Path snapshotPath : snapshotPaths) {
            try {
                Files.deleteIfExists(snapshotPath);
            } catch (IOException e) {
                logger.warn("Unable to delete prepared SMTP attachment snapshot");
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
