package io.github.carlos_emr.carlos.email.helpers;

import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.apache.commons.codec.binary.Base64;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.ssl.SSLContexts;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * SendGrid API-based email sender for OpenO EMR healthcare system.
 *
 * This class provides functionality to send emails through the SendGrid Web API v3,
 * supporting healthcare-specific requirements including security privilege checks,
 * SSL/TLS encryption, and file attachments. All email operations are subject to
 * HIPAA/PIPEDA compliance requirements and require appropriate security permissions.
 *
 * The implementation uses Apache HttpClient with TLS for secure communication
 * with SendGrid's API endpoint. Email content is serialized to JSON format according
 * to SendGrid's API specification, supporting multiple recipients, HTML/plain text
 * content, and Base64-encoded file attachments.
 *
 * Security considerations:
 * - Requires _email WRITE privilege for all send operations
 * - API keys are stored in EmailConfig and must be protected
 * - SendGrid API calls are made over HTTPS/TLS
 * - PHI data in email content must be appropriately secured
 *
 * @see EmailConfig
 * @see EmailAttachment
 * @see SecurityInfoManager
 * @see io.github.carlos_emr.carlos.utility.EmailSendingException
 * @since 2026-01-24
 */
public class APISendGridEmailSender {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_ENDPOINT = "https://api.sendgrid.com/v3/mail/send";
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private LoggedInInfo loggedInInfo;
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private EmailConfig emailConfig;
    private String[] recipients = new String[0];
    private String subject;
    private String body;
    private String additionalParams;
    private List<EmailAttachment> attachments;
    private byte[] preparedPayloadBytes;

    /**
     * Private no-argument constructor to prevent instantiation without required parameters.
     */
    private APISendGridEmailSender() {
    }

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
     *                    in the email, may be null or empty
     */
    public APISendGridEmailSender(LoggedInInfo loggedInInfo, EmailConfig emailConfig, String[] recipients, String subject, String body, List<EmailAttachment> attachments) {
        this.loggedInInfo = loggedInInfo;
        this.emailConfig = emailConfig;
        this.recipients = safeRecipients(recipients);
        this.subject = subject;
        this.body = body;
        this.attachments = safeAttachments(attachments);
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
     *                    in the email, may be null or empty
     */
    public APISendGridEmailSender(LoggedInInfo loggedInInfo, EmailConfig emailConfig, String[] recipients, String subject, String body, String additionalParams, List<EmailAttachment> attachments) {
        this.loggedInInfo = loggedInInfo;
        this.emailConfig = emailConfig;
        this.recipients = safeRecipients(recipients);
        this.subject = subject;
        this.body = body;
        this.additionalParams = additionalParams;
        this.attachments = safeAttachments(attachments);
    }

    /**
     * Sends the email through SendGrid's Web API v3 with security validation.
     *
     * This method performs the following operations:
     * 1. Validates that the logged-in user has _email WRITE privilege
     * 2. Establishes an SSL/TLS connection to SendGrid's API endpoint
     * 3. Constructs the email JSON payload according to SendGrid API specification
     * 4. Transmits the email via HTTP POST request with Bearer token authentication
     * 5. Validates the HTTP response status code
     *
     * The method uses Apache HttpClient with TLS. All attachments are Base64-encoded
     * before transmission.
     *
     * HIPAA/PIPEDA Compliance Note: Ensure that any Protected Health Information (PHI)
     * included in email content is appropriately secured and that transmission is
     * authorized under applicable privacy regulations.
     *
     * @throws EmailSendingException if the user lacks required security privileges,
     *                               if SSL context initialization fails, if the HTTP
     *                               request fails (status code >= 400), if API credentials
     *                               are invalid, or if attachment encoding fails
     * @throws SecurityException if the logged-in user does not have _email WRITE privilege
     */
    public void send() throws EmailSendingException {
        preparePayloadBytes();
        sendPreparedPayload();
    }

    public byte[] preparePayloadBytes() throws EmailSendingException {
        requireEmailWritePrivilege();

        preparedPayloadBytes = createEmailJSON().getBytes(StandardCharsets.UTF_8);
        return preparedPayloadBytes.clone();
    }

    public void sendPreparedPayload() throws EmailSendingException {
        requireEmailWritePrivilege();
        if (preparedPayloadBytes == null) {
            preparePayloadBytes();
        }

        try {
            SendGridConfigDetails configDetails = readConfigDetails();
            SSLContext sslContext = SSLContexts.custom().build();

            HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
                            .setSslContext(sslContext)
                            .build())
                    .build();

            try (CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager).build()) {

                HttpPost httpPost = new HttpPost(configDetails.endpoint());
                httpPost.setHeader("Content-Type", "application/json");
                httpPost.setHeader("Authorization", "Bearer " + configDetails.apiKey());

                ByteArrayEntity entity = new ByteArrayEntity(preparedPayloadBytes, ContentType.APPLICATION_JSON);
                httpPost.setEntity(entity);
                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    if (response.getCode() >= 400) {
                        throw new EmailSendingException(response.getCode() + " " + response.getReasonPhrase()
                                + "\n" + EntityUtils.toString(response.getEntity()));
                    }
                }
            }
        } catch (EmailSendingException e) {
            throw e;
        } catch (IOException | GeneralSecurityException | ParseException | IllegalArgumentException e) {
            String message = e.getMessage() != null ? e.getMessage() : "Failed to send email using SendGrid";
            throw new EmailSendingException(message, e);
        }
    }

    private String createEmailJSON() throws EmailSendingException {
        ObjectNode emailJson = OBJECT_MAPPER.createObjectNode();
        addTo(emailJson);
        addFrom(emailJson);
        addSubject(emailJson);
        addBody(emailJson);
        addAttachments(emailJson);
        addAdditionalParams(emailJson);
        return emailJson.toString();
    }

    private void addTo(ObjectNode emailJson) {
        ArrayNode personalizations = OBJECT_MAPPER.createArrayNode();
        ObjectNode personalization = OBJECT_MAPPER.createObjectNode();

        ArrayNode toList = OBJECT_MAPPER.createArrayNode();
        for (String recipient : recipients) {
            ObjectNode to = OBJECT_MAPPER.createObjectNode();
            to.put("email", recipient);
            toList.add(to);
        }

        personalization.set("to", toList);
        personalizations.add(personalization);

        emailJson.set("personalizations", personalizations);
    }

    private void addFrom(ObjectNode emailJson) {
        ObjectNode from = OBJECT_MAPPER.createObjectNode();
        from.put("email", emailConfig.getSenderEmail());
        from.put("name", emailConfig.getSenderFullName());
        emailJson.set("from", from);
    }

    private void addSubject(ObjectNode emailJson) {
        emailJson.put("subject", subject);
    }

    private void addBody(ObjectNode emailJson) {
        ArrayNode content = OBJECT_MAPPER.createArrayNode();
        ObjectNode contentObj = OBJECT_MAPPER.createObjectNode();
        contentObj.put("type", "text/plain");
        contentObj.put("value", body);
        content.add(contentObj);
        emailJson.set("content", content);
    }

    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    private void addAttachments(ObjectNode emailJson) throws EmailSendingException {
        ArrayNode jsonAttachments = OBJECT_MAPPER.createArrayNode();
        for (EmailAttachment attachment : attachments) {
            EmailAttachment emailAttachment = requireValidAttachment(attachment);
            try {
                ObjectNode jsonAttachment = OBJECT_MAPPER.createObjectNode();
                Path path = PathValidationUtils.resolveTrustedPath(new File(emailAttachment.getFilePath())).toPath();
                jsonAttachment.put("content", Base64.encodeBase64String(Files.readAllBytes(path)));
                jsonAttachment.put("filename", emailAttachment.getFileName());
                jsonAttachment.put("type", PDF_CONTENT_TYPE);
                jsonAttachment.put("disposition", "attachment");
                jsonAttachments.add(jsonAttachment);
            } catch (IOException | IllegalArgumentException | SecurityException e) {
                throw new EmailSendingException("Failed to attach " + emailAttachment.getFileName() + " while sending email using SendGrid.", e);
            }
        }
        emailJson.set("attachments", jsonAttachments);
    }

    private void requireEmailWritePrivilege() {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_email)");
        }
    }

    private String[] safeRecipients(String[] recipients) {
        return recipients != null ? recipients.clone() : new String[0];
    }

    private List<EmailAttachment> safeAttachments(List<EmailAttachment> attachments) {
        return attachments != null ? new ArrayList<EmailAttachment>(attachments) : List.of();
    }

    private EmailAttachment requireValidAttachment(EmailAttachment attachment) throws EmailSendingException {
        if (attachment == null) {
            throw new EmailSendingException("Email attachment is required while sending email using SendGrid.");
        }
        if (attachment.getFileName() == null || attachment.getFileName().isBlank()) {
            throw new EmailSendingException("Email attachment filename is required while sending email using SendGrid.");
        }
        if (attachment.getFilePath() == null || attachment.getFilePath().isBlank()) {
            throw new EmailSendingException("Email attachment path is required while sending email using SendGrid.");
        }
        return attachment;
    }

    private void addAdditionalParams(ObjectNode emailJson) throws EmailSendingException {
        if (additionalParams != null && !additionalParams.isBlank()) {
            emailJson.put("additionalParams", additionalParams);
        }
    }

    private SendGridConfigDetails readConfigDetails() throws EmailSendingException {
        String invalidCredentialsMessage = "Invalid credentials configured for " + emailConfig.getSenderEmail();
        try {
            JsonNode jsonNode = OBJECT_MAPPER.readTree(emailConfig.getConfigDetailsJson());
            JsonNode apiKeyNode = jsonNode.get("api_key");
            if (apiKeyNode == null || apiKeyNode.asText().isBlank()) {
                throw new EmailSendingException(invalidCredentialsMessage);
            }

            String endpoint = DEFAULT_ENDPOINT;
            JsonNode endpointNode = jsonNode.get("end_point");
            if (endpointNode != null && !endpointNode.asText().isBlank()) {
                endpoint = endpointNode.asText().trim();
            }
            return new SendGridConfigDetails(apiKeyNode.asText().trim(), endpoint);
        } catch (IOException | IllegalArgumentException e) {
            throw new EmailSendingException(invalidCredentialsMessage, e);
        }
    }

    private static final class SendGridConfigDetails {
        private final String apiKey;
        private final String endpoint;

        private SendGridConfigDetails(String apiKey, String endpoint) {
            this.apiKey = apiKey;
            this.endpoint = endpoint;
        }

        private String apiKey() {
            return apiKey;
        }

        private String endpoint() {
            return endpoint;
        }
    }
}
