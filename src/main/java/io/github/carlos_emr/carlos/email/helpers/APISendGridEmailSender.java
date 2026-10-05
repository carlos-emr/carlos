package io.github.carlos_emr.carlos.email.helpers;

import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.Closeable;
import java.io.ByteArrayOutputStream;
import io.github.carlos_emr.carlos.email.core.BoundedEmailOutputStream;
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
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
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
import io.github.carlos_emr.carlos.email.core.EmailConfigSecrets;
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
    private static final int MAX_PAYLOAD_BYTES = 50 * 1024 * 1024;
    private static final String JSON_CONTENT_TYPE = "application/json";
    private final jakarta.activation.FileTypeMap attachmentFileTypes =
            new org.springframework.mail.javamail.ConfigurableMimeFileTypeMap();
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
        prepareArtifactBytes();
        sendPrepared();
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
            ValidatedHttpEndpoint endpoint = validateEndpoint(getEndPoint());
            HttpPost request = new HttpPost(endpoint.uri());
            request.setHeader("Content-Type", JSON_CONTENT_TYPE);
            request.setHeader("Authorization", "Bearer " + getAPIKey());
            request.setEntity(new ByteArrayEntity(payloadBytes, ContentType.APPLICATION_JSON));
            dispatchRequest(createHttpClient(endpoint), request);
        } catch (EmailSendingException e) {
            throw e;
        } catch (RuntimeException | GeneralSecurityException e) {
            throw new EmailSendingException("The SendGrid request could not be prepared.", e);
        }
    }

    private static CloseableHttpClient createHttpClient(ValidatedHttpEndpoint endpoint)
            throws GeneralSecurityException {
        SSLContext sslContext = SSLContexts.custom().build();
        HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
                        .setSslContext(sslContext).build())
                .setDnsResolver(endpoint.pinnedDnsResolver())
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(30))
                        .setSocketTimeout(Timeout.ofSeconds(60)).build())
                .build();
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(30))
                .setResponseTimeout(Timeout.ofSeconds(60)).build();
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .disableRedirectHandling()
                .disableAutomaticRetries().build();
    }

    /** Owns the client and response; cleanup cannot change a conclusive transport outcome. */
    static void dispatchRequest(CloseableHttpClient client, HttpPost request) throws EmailSendingException {
        CloseableHttpResponse response = null;
        try {
            response = client.execute(request);
            assertAccepted(response.getCode());
        } catch (EmailSendingException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new EmailSendingException(
                    "SendGrid did not confirm whether the message was accepted.", e, true);
        } finally {
            closeTransportResource(response);
            closeTransportResource(client);
        }
    }

    private static void closeTransportResource(Closeable resource) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (IOException | RuntimeException cleanupFailure) {
            // Do not expose remote response content or credentials in a cleanup diagnostic.
            io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().warn(
                    "SendGrid transport resource cleanup failed; the send outcome is unchanged");
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
                    "SendGrid did not accept the request: expected HTTP 202, got " + statusCode + ".",
                    new org.apache.hc.client5.http.HttpResponseException(statusCode, "Request rejected"));
        }
    }

    static ValidatedHttpEndpoint validateEndpoint(String endpoint) throws EmailSendingException {
        ValidatedHttpEndpoint validatedEndpoint;
        try {
            validatedEndpoint = ValidatedHttpEndpoint.resolve(
                    endpoint, "carlos.email.sendgrid.allowedHosts");
        } catch (ValidatedHttpEndpoint.ValidationException e) {
            throw new EmailSendingException("Configured email endpoint was rejected.", e);
        }
        if (!validatedEndpoint.isHttps()) {
            throw new EmailSendingException("Configured email endpoint must use HTTPS.");
        }
        return validatedEndpoint;
    }

    // Package-private for unit testing that the serialized payload no longer carries the API key.
    String createEmailJSON() throws EmailSendingException {
        return new String(createPayloadBytes(), StandardCharsets.UTF_8);
    }

    private byte[] createPayloadBytes() throws EmailSendingException {
        ObjectNode emailJson = objectMapper.createObjectNode();
        addTo(emailJson);
        addFrom(emailJson);
        addSubject(emailJson);
        addBody(emailJson);
        addAttachments(emailJson);
        addAdditionalParams(emailJson);
        // The API key is sent only in the Authorization: Bearer header (see send()). It is
        // deliberately NOT embedded in the request body: SendGrid ignores a body "apiKey", but any
        // request-logging intermediary or debug capture would record it, creating a second leak
        // channel for the credential.
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var bounded = new BoundedEmailOutputStream(bytes, MAX_PAYLOAD_BYTES)) {
            objectMapper.writeValue(bounded, emailJson);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new EmailSendingException("The SendGrid payload exceeds the archive limit or cannot be serialized.", e);
        }
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
        // Base64 needs four output bytes per three input bytes, before JSON overhead.
        int remainingBytes = MAX_PAYLOAD_BYTES / 4 * 3;
        for (EmailAttachment emailAttachment : attachments) {
            if (emailAttachment == null
                    || emailAttachment.getFilePath() == null
                    || emailAttachment.getFilePath().isBlank()) {
                throw new EmailSendingException("An email attachment has no readable file path.");
            }
            if (emailAttachment.getFileName() == null || emailAttachment.getFileName().isBlank()) {
                throw new EmailSendingException("An email attachment has no file name.");
            }
            try {
                ObjectNode jsonAttachment = objectMapper.createObjectNode();
                Path path = PathValidationUtils.resolveTrustedPath(new File(emailAttachment.getFilePath())).toPath();
                byte[] attachmentBytes;
                try (var input = Files.newInputStream(path)) {
                    attachmentBytes = input.readNBytes(remainingBytes + 1);
                }
                if (attachmentBytes.length > remainingBytes) {
                    throw new EmailSendingException("SendGrid attachments exceed the archive size limit.");
                }
                remainingBytes -= attachmentBytes.length;
                jsonAttachment.put("content", Base64.encodeBase64String(attachmentBytes));
                jsonAttachment.put("filename", emailAttachment.getFileName());
                String contentType = attachmentFileTypes.getContentType(emailAttachment.getFileName());
                jsonAttachment.put("type", contentType);
                jsonAttachment.put("disposition", "attachment");
                jsonAttachments.add(jsonAttachment);
                attachmentMetadata.add(describeAttachment(emailAttachment, attachmentBytes, contentType));
            } catch (IOException | SecurityException e) {
                throw new EmailSendingException("An email attachment could not be read.", e);
            }
        }
        emailJson.put("attachments", jsonAttachments);
        preparedAttachmentMetadata = List.copyOf(attachmentMetadata);
    }

    private OutboundEmailArchiveAttachmentDto describeAttachment(EmailAttachment attachment, byte[] attachmentBytes, String contentType)
            throws EmailSendingException {
        OutboundEmailArchiveAttachmentDto attachmentDto = new OutboundEmailArchiveAttachmentDto();
        attachmentDto.setFileName(attachment.getFileName());
        // The declared type, not a sniffed one: this records what SendGrid was told the part is.
        attachmentDto.setContentType(contentType);
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
            preparedPayloadBytes = createPayloadBytes();
            return preparedPayloadBytes.clone();
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

    // Package-private for unit testing the credential-validation branches without a live send.
    String getAPIKey() throws EmailSendingException {
        JsonNode jsonNode = getConfigDetails();
        String apiKey;
        JsonNode apiKeyNode = jsonNode.path("api_key");
        if (apiKeyNode.isMissingNode() || apiKeyNode.isNull()
                || !apiKeyNode.isValueNode() || apiKeyNode.asText().isBlank()) {
            // Missing/blank api_key must surface as a clean credential error, not an NPE.
            throw invalidCredentialsException();
        }
        // Decrypt the at-rest credential only here, at send time. Legacy plaintext keys pass
        // through unchanged during the migration window.
        apiKey = EmailConfigSecrets.decryptSecret(apiKeyNode.asText());
        if (apiKey == null || apiKey.isBlank()) {
            // A stored value that decrypts to blank must not travel as an empty Authorization: Bearer.
            throw invalidCredentialsException();
        }
        return apiKey;
    }

    private String getEndPoint() throws EmailSendingException {
        JsonNode jsonNode = getConfigDetails();
        JsonNode endPointNode = jsonNode.get("end_point");
        return endPointNode != null ? endPointNode.asText() : DEFAULT_END_POINT;
    }

    /**
     * Parses and validates the shared provider configuration before either endpoint or credential
     * access. Keeping this in one place prevents the real send path from throwing an unchecked
     * exception before {@link #getAPIKey()} can report a sanitized credential failure.
     */
    private JsonNode getConfigDetails() throws EmailSendingException {
        String configJson = emailConfig.getConfigDetailsJson();
        if (configJson == null || configJson.isBlank()) {
            throw invalidCredentialsException();
        }
        try {
            JsonNode jsonNode = objectMapper.readTree(configJson);
            if (jsonNode == null || !jsonNode.isObject()) {
                throw invalidCredentialsException();
            }
            return jsonNode;
        } catch (IOException e) {
            // Intentionally no cause: a Jackson parse exception can echo a fragment of the source
            // JSON (which holds the secret), so keep the exception safe for logs and the EmailLog.
            throw invalidCredentialsException();
        }
    }

    private EmailSendingException invalidCredentialsException() {
        return new EmailSendingException("Invalid credentials configured for " + emailConfig.getSenderEmail());
    }
}
