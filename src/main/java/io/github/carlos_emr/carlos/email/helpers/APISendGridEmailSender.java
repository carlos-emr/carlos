package io.github.carlos_emr.carlos.email.helpers;

import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import javax.net.ssl.SSLContext;

import org.apache.commons.codec.binary.Base64;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchive;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveAttachmentDto;
import io.github.carlos_emr.carlos.email.core.OutboundEmailTransport;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.ValidatedHttpEndpoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Sends privilege-gated email through a validated SendGrid HTTPS endpoint.
 *
 * <p>The HTTP client pins the validated DNS result, rejects redirects, and applies bounded
 * connection and response timeouts. Attachments are encoded into the SendGrid JSON request.</p>
 */
public class APISendGridEmailSender implements OutboundEmailTransport {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final LoggedInInfo loggedInInfo;
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private final EmailConfig emailConfig;
    private final String[] recipients;
    private final String subject;
    private final String body;
    private final String additionalParams;
    private static final String DEFAULT_END_POINT = "https://api.sendgrid.com/v3/mail/send";
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String SENDGRID_ATTACHMENT_CONTENT_TYPE = "application/pdf";
    private static final HexFormat HEX_FORMAT = HexFormat.of();
    private final List<EmailAttachment> attachments;

    private byte[] preparedPayloadBytes;
    private List<OutboundEmailArchiveAttachmentDto> preparedAttachmentMetadata = List.of();

    /**
     * Constructs an APISendGridEmailSender with email parameters and attachments.
     *
     * This constructor initializes the email sender with all required parameters for
     * sending emails through SendGrid's API. The logged-in user information is used
     * for security privilege checks to ensure the user has permission to send emails.
     *
     * @param loggedInInfo LoggedInInfo the current logged-in user session information,
     *                     used for security privilege validation
     * @param emailConfig EmailConfig the email configuration containing sender details,
     *                    API credentials, and SendGrid endpoint information
     * @param recipients String[] array of recipient email addresses in RFC 5322 format
     * @param subject String the email subject line
     * @param body String the email body content (plain text format)
     * @param attachments List&lt;EmailAttachment&gt; list of file attachments to include
     *                    in the email, may be empty but not null
     */
    public APISendGridEmailSender(LoggedInInfo loggedInInfo, EmailConfig emailConfig, String[] recipients, String subject, String body, List<EmailAttachment> attachments) {
        this(loggedInInfo, emailConfig, recipients, subject, body, null, attachments);
    }

    /**
     * Constructs an APISendGridEmailSender with email parameters, additional parameters, and attachments.
     *
     * This extended constructor includes support for additional custom parameters that may be
     * required for specific SendGrid API features or custom email processing requirements.
     * The logged-in user information is used for security privilege checks.
     *
     * @param loggedInInfo LoggedInInfo the current logged-in user session information,
     *                     used for security privilege validation
     * @param emailConfig EmailConfig the email configuration containing sender details,
     *                    API credentials, and SendGrid endpoint information
     * @param recipients String[] array of recipient email addresses in RFC 5322 format
     * @param subject String the email subject line
     * @param body String the email body content (plain text format)
     * @param additionalParams String additional custom parameters for SendGrid API,
     *                         may be null if not required
     * @param attachments List&lt;EmailAttachment&gt; list of file attachments to include
     *                    in the email, may be empty but not null
     */
    public APISendGridEmailSender(LoggedInInfo loggedInInfo, EmailConfig emailConfig, String[] recipients, String subject, String body, String additionalParams, List<EmailAttachment> attachments) {
        this.loggedInInfo = Objects.requireNonNull(loggedInInfo, "loggedInInfo must not be null");
        this.emailConfig = Objects.requireNonNull(emailConfig, "emailConfig must not be null");
        this.recipients = Objects.requireNonNull(recipients, "recipients must not be null").clone();
        this.subject = Objects.requireNonNull(subject, "subject must not be null");
        this.body = Objects.requireNonNull(body, "body must not be null");
        this.additionalParams = additionalParams;
        this.attachments = List.copyOf(
                Objects.requireNonNull(attachments, "attachments must not be null"));
    }

    /**
     * Sends the email through SendGrid's Web API v3 with security validation.
     *
     * The request uses the validated, DNS-pinned HTTPS endpoint with redirects disabled and bounded
     * timeouts. Attachments are Base64-encoded into the JSON payload.
     *
     * @throws EmailSendingException if the user lacks required security privileges,
     *                               if SSL context initialization fails, if the HTTP
     *                               request fails (status code >= 400), if API credentials
     *                               are invalid, or if attachment encoding fails
     * @throws RuntimeException if the logged-in user does not have _email WRITE privilege
     */
    public void send() throws EmailSendingException {
        assertEmailWritePrivilege();
        postPayload(createEmailJSON().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * POSTs an already-serialized SendGrid payload to the validated endpoint.
     *
     * <p>Shared by {@link #send()} and {@link #sendPrepared()} so the archived bytes and the
     * transmitted bytes cannot drift apart through two separate request paths.</p>
     *
     * @param payloadBytes the exact JSON payload to transmit
     * @throws EmailSendingException if endpoint validation, transport, or the response status fails
     */
    private void postPayload(byte[] payloadBytes) throws EmailSendingException {
        try {
            String endPoint = getEndPoint();
            ValidatedHttpEndpoint validatedEndpoint = validateEndpoint(endPoint);
            SSLContext sslContext = SSLContexts.custom().build();

            HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
                            .setSslContext(sslContext)
                            .build())
                    .setDnsResolver(validatedEndpoint.pinnedDnsResolver())
                    .setDefaultConnectionConfig(ConnectionConfig.custom()
                            .setConnectTimeout(Timeout.ofSeconds(30))
                            .setSocketTimeout(Timeout.ofSeconds(60))
                            .build())
                    .build();
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectionRequestTimeout(Timeout.ofSeconds(30))
                    .setResponseTimeout(Timeout.ofSeconds(60))
                    .build();

            try (CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .setDefaultRequestConfig(requestConfig)
                    .disableRedirectHandling()
                    .build()) {
                HttpPost httpPost = new HttpPost(validatedEndpoint.uri());
                httpPost.setHeader("Content-Type", "application/json");
                httpPost.setHeader("Authorization", "Bearer " + getAPIKey());

                httpPost.setEntity(new ByteArrayEntity(payloadBytes, ContentType.APPLICATION_JSON));
                try (var response = httpClient.execute(httpPost)) {
                    assertAccepted(response.getCode());
                }
            }
        } catch (EmailSendingException e) {
            throw e;
        } catch (IOException | GeneralSecurityException e) {
            throw new EmailSendingException(e.getMessage(), e);
        }
    }

    /**
     * Accepts only SendGrid's {@code 202 Accepted}; every other status is a send failure.
     *
     * <p>Deliberately not a {@code < 400} test. Redirect handling is disabled on the client for SSRF
     * containment, so a {@code 301}/{@code 302}/{@code 307} is returned here rather than followed —
     * and a 3xx is not {@code >= 400}, so the old check passed it as success and the caller recorded
     * a clinical notification that was never queued. A {@code 200} is likewise not an acceptance.
     * Once you stop following redirects, "not an error" stops meaning "delivered".</p>
     *
     * <p>Extracted so this is reachable from a unit test: the status check previously sat inside the
     * {@code try-with-resources} around a live {@code CloseableHttpClient}, which is why nothing
     * covered it.</p>
     *
     * @param statusCode the HTTP status SendGrid returned
     * @throws EmailSendingException naming the received status, for anything other than 202
     */
    static void assertAccepted(int statusCode) throws EmailSendingException {
        if (statusCode != HttpStatus.SC_ACCEPTED) {
            throw new EmailSendingException(
                    "SendGrid did not accept the request: expected HTTP 202, got " + statusCode + ".");
        }
    }

    static ValidatedHttpEndpoint validateEndpoint(String endpoint) throws EmailSendingException {
        ValidatedHttpEndpoint validatedEndpoint;
        try {
            validatedEndpoint = ValidatedHttpEndpoint.resolve(
                    endpoint, "carlos.email.sendgrid.allowedHosts");
        } catch (ValidatedHttpEndpoint.ValidationException e) {
            throw new EmailSendingException("Configured email endpoint was rejected: " + e.getMessage());
        }
        if (!validatedEndpoint.isHttps()) {
            throw new EmailSendingException("Configured email endpoint must use HTTPS.");
        }
        return validatedEndpoint;
    }

    private String createEmailJSON() throws EmailSendingException {
        ObjectNode emailJson = objectMapper.createObjectNode();
        addTo(emailJson);
        addFrom(emailJson);
        addSubject(emailJson);
        addBody(emailJson);
        addAttachments(emailJson);
        addAdditionalParams(emailJson);
        // The API key is sent only via the Authorization: Bearer header (see the HTTP client setup).
        // It is deliberately NOT duplicated into the JSON request body.
        return emailJson.toString();
    }

    private void addTo(ObjectNode emailJson) {
        ArrayNode personalizations = objectMapper.createArrayNode();
        ObjectNode personalization = objectMapper.createObjectNode();

        ArrayNode toList = objectMapper.createArrayNode();
        for (String recipient : recipients) {
            ObjectNode to = objectMapper.createObjectNode();
            to.put("email", recipient);
            toList.add(to);
        }

        personalization.put("to", toList);
        personalizations.add(personalization);

        emailJson.put("personalizations", personalizations);
    }

    private void addFrom(ObjectNode emailJson) {
        ObjectNode from = objectMapper.createObjectNode();
        from.put("email", emailConfig.getSenderEmail());
        from.put("name", emailConfig.getSenderFullName());
        emailJson.put("from", from);
    }

    private void addSubject(ObjectNode emailJson) {
        emailJson.put("subject", subject);
    }

    private void addBody(ObjectNode emailJson) {
        ArrayNode content = objectMapper.createArrayNode();
        ObjectNode contentObj = objectMapper.createObjectNode();
        contentObj.put("type", "text/plain");
        contentObj.put("value", body);
        content.add(contentObj);
        emailJson.put("content", content);
    }

    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    private void addAttachments(ObjectNode emailJson) throws EmailSendingException {
        ArrayNode jsonAttachments = objectMapper.createArrayNode();
        // Archive metadata is captured from the same byte[] that is encoded into the payload
        // below, never from a second read of the file. A re-read could observe different bytes
        // (the temp file is regenerated per compose), which would make the recorded hash
        // describe something other than what the patient received.
        List<OutboundEmailArchiveAttachmentDto> attachmentMetadata = new ArrayList<>();
        for (EmailAttachment emailAttachment : attachments) {
            if (emailAttachment == null
                    || emailAttachment.getFilePath() == null
                    || emailAttachment.getFilePath().isBlank()) {
                throw new EmailSendingException("An email attachment has no readable file path.");
            }
            try {
                ObjectNode jsonAttachment = objectMapper.createObjectNode();
                Path path = PathValidationUtils.resolveTrustedPath(new File(emailAttachment.getFilePath())).toPath();
                byte[] attachmentBytes = Files.readAllBytes(path);
                jsonAttachment.put("content", Base64.encodeBase64String(attachmentBytes));
                jsonAttachment.put("filename", emailAttachment.getFileName());
                jsonAttachment.put("type", SENDGRID_ATTACHMENT_CONTENT_TYPE);
                jsonAttachment.put("disposition", "attachment");
                jsonAttachments.add(jsonAttachment);
                attachmentMetadata.add(describeAttachment(emailAttachment, attachmentBytes));
            } catch (IOException | SecurityException e) {
                throw new EmailSendingException("An email attachment could not be read.", e);
            }
        }
        emailJson.put("attachments", jsonAttachments);
        preparedAttachmentMetadata = List.copyOf(attachmentMetadata);
    }

    private OutboundEmailArchiveAttachmentDto describeAttachment(EmailAttachment attachment, byte[] attachmentBytes)
            throws EmailSendingException {
        OutboundEmailArchiveAttachmentDto attachmentDto = new OutboundEmailArchiveAttachmentDto();
        attachmentDto.setFileName(attachment.getFileName());
        // The declared type, not a sniffed one: this records what SendGrid was told the part is.
        attachmentDto.setContentType(SENDGRID_ATTACHMENT_CONTENT_TYPE);
        attachmentDto.setSha256Hash(sha256Hex(attachmentBytes));
        attachmentDto.setByteSize((long) attachmentBytes.length);
        attachmentDto.setSourceDocumentType(attachment.getDocumentType() != null ? attachment.getDocumentType().name() : null);
        attachmentDto.setSourceDocumentId(attachment.getDocumentId());
        return attachmentDto;
    }

    private String sha256Hex(byte[] content) throws EmailSendingException {
        try {
            return HEX_FORMAT.formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new EmailSendingException("SHA-256 is required to archive outbound email attachments.", e);
        }
    }

    // --- OutboundEmailTransport -----------------------------------------------------------------

    /**
     * Serializes the SendGrid request body once and keeps it for {@link #sendPrepared()}.
     *
     * <p>Attachment metadata is captured during serialization, so it describes the encoded parts
     * rather than being reconstructed by parsing the JSON back out.</p>
     */
    @Override
    public byte[] prepareArtifactBytes() throws EmailSendingException {
        assertEmailWritePrivilege();
        if (preparedPayloadBytes != null) {
            throw new EmailSendingException("SendGrid payload has already been prepared");
        }
        try {
            // Fail malformed credentials and rejected endpoints before a durable archive is
            // written. The endpoint is validated again immediately before transport so the
            // request still uses a fresh, pinned DNS result.
            getAPIKey();
            validateEndpoint(getEndPoint());
            byte[] payloadBytes = createEmailJSON().getBytes(StandardCharsets.UTF_8);
            preparedPayloadBytes = payloadBytes;
            return payloadBytes;
        } catch (EmailSendingException | RuntimeException e) {
            discardPrepared();
            throw e;
        }
    }

    @Override
    public void sendPrepared() throws EmailSendingException {
        try {
            assertEmailWritePrivilege();
            if (preparedPayloadBytes == null) {
                throw new EmailSendingException("SendGrid payload must be prepared before sending");
            }
            postPayload(preparedPayloadBytes);
        } finally {
            discardPrepared();
        }
    }

    @Override
    public void discardPrepared() {
        preparedPayloadBytes = null;
        preparedAttachmentMetadata = List.of();
    }

    @Override
    public List<OutboundEmailArchiveAttachmentDto> describePreparedAttachments() throws EmailSendingException {
        if (preparedPayloadBytes == null) {
            throw new EmailSendingException("SendGrid payload must be prepared before describing its attachments");
        }
        return preparedAttachmentMetadata;
    }

    @Override
    public String getArchiveArtifactType() {
        return OutboundEmailArchive.ARTIFACT_TYPE_API_PAYLOAD;
    }

    @Override
    public String getArchiveContentType() {
        return JSON_CONTENT_TYPE;
    }

    @Override
    public String getArchiveFileName(EmailLog emailLog) {
        return "outbound-email-" + (emailLog != null ? emailLog.getId() : null) + "-sendgrid.json";
    }

    private void assertEmailWritePrivilege() {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_email)");
        }
    }

    private void addAdditionalParams(ObjectNode emailJson) throws EmailSendingException {
        emailJson.put("additionalParams", additionalParams);
    }

    private String getAPIKey() throws EmailSendingException {
        JsonNode apiKeyNode = parseConfigDetails().get("api_key");
        if (apiKeyNode == null || !apiKeyNode.isTextual() || apiKeyNode.asText().isBlank()) {
            throw invalidCredentialsException(null);
        }
        return apiKeyNode.asText();
    }

    private String getEndPoint() throws EmailSendingException {
        JsonNode endPointNode = parseConfigDetails().get("end_point");
        if (endPointNode == null || endPointNode.isNull()) {
            return DEFAULT_END_POINT;
        }
        if (!endPointNode.isTextual() || endPointNode.asText().isBlank()) {
            throw invalidCredentialsException(null);
        }
        return endPointNode.asText();
    }

    private JsonNode parseConfigDetails() throws EmailSendingException {
        try {
            JsonNode configDetails = objectMapper.readTree(emailConfig.getConfigDetailsJson());
            if (configDetails == null || !configDetails.isObject()) {
                throw invalidCredentialsException(null);
            }
            return configDetails;
        } catch (IOException | IllegalArgumentException e) {
            throw invalidCredentialsException(e);
        }
    }

    private EmailSendingException invalidCredentialsException(Throwable cause) {
        String message = "Invalid credentials configured for " + emailConfig.getSenderEmail();
        return cause != null ? new EmailSendingException(message, cause) : new EmailSendingException(message);
    }
}
