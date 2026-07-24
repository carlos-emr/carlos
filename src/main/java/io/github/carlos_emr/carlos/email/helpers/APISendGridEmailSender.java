package io.github.carlos_emr.carlos.email.helpers;

import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLContext;

import org.apache.commons.codec.binary.Base64;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
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
 * The implementation uses Apache HttpClient with SSL context for secure communication
 * with SendGrid's API endpoint. Email content is serialized to JSON format according
 * to SendGrid's API specification, supporting multiple recipients, HTML/plain text
 * content, and Base64-encoded file attachments.
 *
 * Security considerations:
 * - Requires _email WRITE privilege for all send operations
 * - API keys are stored in EmailConfig and must be protected
 * - SSL client authentication is enabled for enhanced security
 * - PHI data in email content must be appropriately secured
 *
 * @see EmailConfig
 * @see EmailAttachment
 * @see SecurityInfoManager
 * @see io.github.carlos_emr.carlos.utility.EmailSendingException
 * @since 2026-01-24
 */
public class APISendGridEmailSender {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private LoggedInInfo loggedInInfo;
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private EmailConfig emailConfig;
    private String[] recipients = new String[0];
    private String subject;
    private String body;
    private String additionalParams;
    private String DEFAULT_END_POINT = "https://api.sendgrid.com/v3/mail/send";
    private List<EmailAttachment> attachments;

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
     *                    in the email, may be empty but not null
     */
    public APISendGridEmailSender(LoggedInInfo loggedInInfo, EmailConfig emailConfig, String[] recipients, String subject, String body, List<EmailAttachment> attachments) {
        this.loggedInInfo = loggedInInfo;
        this.emailConfig = emailConfig;
        this.recipients = recipients;
        this.subject = subject;
        this.body = body;
        this.attachments = attachments;
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
        this.loggedInInfo = loggedInInfo;
        this.emailConfig = emailConfig;
        this.recipients = recipients;
        this.subject = subject;
        this.body = body;
        this.additionalParams = additionalParams;
        this.attachments = attachments;
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
     * The method uses Apache HttpClient with custom SSL context configuration that
     * enables client authentication for enhanced security. All attachments are
     * Base64-encoded before transmission.
     *
     * HIPAA/PIPEDA Compliance Note: Ensure that any Protected Health Information (PHI)
     * included in email content is appropriately secured and that transmission is
     * authorized under applicable privacy regulations.
     *
     * @throws EmailSendingException if the user lacks required security privileges,
     *                               if SSL context initialization fails, if the HTTP
     *                               request fails (status code >= 400), if API credentials
     *                               are invalid, or if attachment encoding fails
     * @throws RuntimeException if the logged-in user does not have _email WRITE privilege
     */
    public void send() throws EmailSendingException {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_email)");
        }

        try {
            SSLContext sslContext = SSLContexts.custom().build();
            sslContext.getDefaultSSLParameters().setNeedClientAuth(true);
            sslContext.getDefaultSSLParameters().setWantClientAuth(true);

            HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
                            .setSslContext(sslContext)
                            .build())
                    .build();

            try (CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager).build()) {

                String endPoint = getEndPoint();
                validateEndpoint(endPoint);
                HttpPost httpPost = new HttpPost(endPoint);
                httpPost.setHeader("Content-Type", "application/json");
                httpPost.setHeader("Authorization", "Bearer " + getAPIKey());

                StringEntity entity = new StringEntity(createEmailJSON());
                httpPost.setEntity(entity);
                var response = httpClient.execute(httpPost);
                if (response.getCode() >= 400) {
                    throw new EmailSendingException(response.getCode() + " " + response.getReasonPhrase()
                            + "\n" + EntityUtils.toString(response.getEntity()));
                }
            }
        } catch (EmailSendingException e) {
            throw e;
        } catch (Exception e) {
            throw new EmailSendingException(e.getMessage(), e);
        }
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
        for (EmailAttachment emailAttachment : attachments) {
            try {
                ObjectNode jsonAttachment = objectMapper.createObjectNode();
                Path path = PathValidationUtils.resolveTrustedPath(new File(emailAttachment.getFilePath())).toPath();
                jsonAttachment.put("content", Base64.encodeBase64String(Files.readAllBytes(path)));
                jsonAttachment.put("filename", emailAttachment.getFileName());
                jsonAttachment.put("type", "application/pdf");
                jsonAttachment.put("disposition", "attachment");
                jsonAttachments.add(jsonAttachment);
            } catch (Exception e) {
                throw new EmailSendingException("Failed to attach " + emailAttachment.getFileName() + " while sending email using SendGrid.");
            }
        }
        emailJson.put("attachments", jsonAttachments);
    }

    private void addAdditionalParams(ObjectNode emailJson) throws EmailSendingException {
        emailJson.put("additionalParams", additionalParams);
    }

    private String getAPIKey() throws EmailSendingException {
        String apiKey;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(emailConfig.getConfigDetailsJson());
            apiKey = jsonNode.get("api_key").asText();
        } catch (IOException e) {
            throw new EmailSendingException("Invalid credentials configured for " + emailConfig.getSenderEmail());
        }
        return apiKey;
    }

    /**
     * Defense-in-depth on the admin-configured SendGrid endpoint before the bearer key + email payload
     * are POSTed to it. Mirrors the fax middleware SSRF guard: require an http(s) scheme with no
     * embedded user-info and a host that does not resolve to a loopback / link-local / any-local /
     * multicast address (private LAN is allowed for legitimate internal relays). An operator can
     * allowlist specific hosts via {@code carlos.email.sendgrid.allowedHosts} (comma-separated). The
     * endpoint is trusted operator config, but this bounds the blast radius if it is misconfigured or
     * tampered with. TLS is enforced at deployment, not here.
     */
    // IMPROPER_UNICODE: case-insensitive comparison of the literal URL scheme token, not user-identity folding.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "case-insensitive comparison of the literal URL scheme token, not user-identity folding")
    private void validateEndpoint(String endpoint) throws EmailSendingException {
        URI uri;
        try {
            uri = new URI(endpoint == null ? "" : endpoint.trim());
        } catch (URISyntaxException e) {
            throw new EmailSendingException("Configured email endpoint is not a valid URI");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new EmailSendingException("Configured email endpoint must use http or https");
        }
        if (uri.getUserInfo() != null) {
            throw new EmailSendingException("Configured email endpoint must not embed user-info credentials");
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new EmailSendingException("Configured email endpoint must include a host");
        }
        if (isEndpointHostAllowlisted(host)) {
            return;
        }
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                        || addr.isAnyLocalAddress() || addr.isMulticastAddress()) {
                    throw new EmailSendingException("Configured email endpoint host resolves to a disallowed address");
                }
            }
        } catch (UnknownHostException e) {
            throw new EmailSendingException("Configured email endpoint host could not be resolved");
        }
    }

    // IMPROPER_UNICODE: case-insensitive comparison of the literal allowlisted host token, not user-identity folding.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "case-insensitive comparison of the literal allowlisted host token, not user-identity folding")
    private boolean isEndpointHostAllowlisted(String host) {
        String allow = System.getProperty("carlos.email.sendgrid.allowedHosts", "");
        if (allow == null || allow.isBlank()) {
            return false;
        }
        for (String allowed : allow.split(",")) {
            if (allowed.trim().equalsIgnoreCase(host)) {
                return true;
            }
        }
        return false;
    }

    private String getEndPoint() throws EmailSendingException {
        StringBuilder endPointBuilder = new StringBuilder();
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(emailConfig.getConfigDetailsJson());
            endPointBuilder.append(jsonNode.get("end_point") != null ? jsonNode.get("end_point").asText() : DEFAULT_END_POINT);
        } catch (IOException e) {
            throw new EmailSendingException("Invalid credentials configured for " + emailConfig.getSenderEmail());
        }
        return endPointBuilder.toString();
    }
}
