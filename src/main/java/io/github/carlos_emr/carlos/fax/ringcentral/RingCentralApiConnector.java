/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.fax.ringcentral;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderException;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.annotation.PreDestroy;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.Timeout;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Low-level RingCentral fax API connector.
 *
 * @since 2026-05-05
 */
@Component
public class RingCentralApiConnector {

    public static final String DEFAULT_RINGCENTRAL_API_URL = "https://platform.ringcentral.com";
    public static final String DEFAULT_RINGCENTRAL_SANDBOX_API_URL = "https://platform.devtest.ringcentral.com";

    private static final String GRANT_TYPE_JWT = "urn:ietf:params:oauth:grant-type:jwt-bearer";
    private static final int INBOX_PAGE_SIZE = 100;
    private static final int MAX_INBOX_PAGES = 50;
    private static final Logger logger = MiscUtils.getLogger();
    private static final RequestConfig REQUEST_CONFIG = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(30))
            .setResponseTimeout(Timeout.ofSeconds(60))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
            .setMaxConnTotal(20)
            .setMaxConnPerRoute(10)
            .build();
    private final CloseableHttpClient httpClient = HttpClients.custom()
            .setDefaultRequestConfig(REQUEST_CONFIG)
            .setConnectionManager(connectionManager)
            .build();
    private final Supplier<String> apiUrlResolver;

    public RingCentralApiConnector() {
        this(RingCentralApiConnector::resolveFromProperties);
    }

    /**
     * Test-friendly constructor that lets callers pin the API origin without going through
     * {@link CarlosProperties}. Production code uses the no-arg constructor; HTTP fixture tests
     * use this overload to point the connector at a local server.
     */
    RingCentralApiConnector(Supplier<String> apiUrlResolver) {
        this.apiUrlResolver = apiUrlResolver;
    }

    public RingCentralResponse.Token authenticate(String clientId, String clientSecret, String jwtToken)
            throws RingCentralException {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("grant_type", GRANT_TYPE_JWT));
        params.add(new BasicNameValuePair("assertion", jwtToken));

        HttpPost post = new HttpPost(getApiUrl() + "/restapi/oauth/token");
        post.setHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8)));
        post.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));

        return executeJson(post, RingCentralResponse.Token.class, "RingCentral authentication failed");
    }

    public RingCentralResponse.Message sendFax(String accessToken, String accountId, String extensionId,
            String destination, byte[] document, String fileName) throws RingCentralException {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.putArray("to").addObject().put("phoneNumber", destination);
        metadata.put("faxResolution", "High");

        HttpPost post = new HttpPost(buildMessageStoreUrl(accountId, extensionId) + "/fax");
        post.setHeader("Authorization", bearer(accessToken));
        post.setEntity(MultipartEntityBuilder.create()
                .addTextBody("json", metadata.toString(), ContentType.APPLICATION_JSON)
                .addBinaryBody("attachment", document, ContentType.create("application/pdf"),
                        StringUtils.defaultIfBlank(fileName, "fax.pdf"))
                .build());

        return executeJson(post, RingCentralResponse.Message.class, "RingCentral fax send failed");
    }

    /**
     * Lists unread inbound RingCentral fax messages, walking the {@code navigation.nextPage} cursor
     * up to {@link #MAX_INBOX_PAGES} pages so that inboxes larger than one page are not silently
     * truncated. The page-cap acts as a safety bound against runaway pagination if RingCentral
     * returns a self-loop cursor.
     */
    public RingCentralResponse.MessageList getInboundFaxes(String accessToken, String accountId, String extensionId)
            throws RingCentralException {
        String firstPageUri = buildMessageStoreUrl(accountId, extensionId)
                + "?messageType=Fax&direction=Inbound&readStatus=Unread&perPage=" + INBOX_PAGE_SIZE;

        List<RingCentralResponse.Message> aggregated = new ArrayList<>();
        String nextUri = firstPageUri;
        int pages = 0;
        while (nextUri != null) {
            if (pages >= MAX_INBOX_PAGES) {
                logger.warn("RingCentral inbox pagination capped at {} pages ({} records pulled) - "
                        + "remaining unread faxes will be picked up on the next poll cycle",
                        MAX_INBOX_PAGES, aggregated.size());
                break;
            }
            HttpGet get = new HttpGet(nextUri);
            get.setHeader("Authorization", bearer(accessToken));
            RingCentralResponse.MessageList page = executeJson(get, RingCentralResponse.MessageList.class,
                    "RingCentral inbox fetch failed");
            aggregated.addAll(page.getRecords());
            nextUri = nextPageUri(page);
            pages++;
        }

        RingCentralResponse.MessageList combined = new RingCentralResponse.MessageList();
        combined.setRecords(aggregated);
        return combined;
    }

    public byte[] downloadFax(String accessToken, String accountId, String extensionId, String messageId,
            String attachmentId) throws RingCentralException {
        HttpGet get = new HttpGet(buildMessageStoreUrl(accountId, extensionId) + "/"
                + normalizeMessageId(messageId) + "/content/" + normalizeMessageId(attachmentId));
        get.setHeader("Authorization", bearer(accessToken));
        return executeBytes(get, "RingCentral fax download failed");
    }

    public RingCentralResponse.Message getFaxStatus(String accessToken, String accountId, String extensionId,
            String messageId) throws RingCentralException {
        HttpGet get = new HttpGet(buildMessageStoreUrl(accountId, extensionId) + "/" + normalizeMessageId(messageId));
        get.setHeader("Authorization", bearer(accessToken));
        return executeJson(get, RingCentralResponse.Message.class, "RingCentral fax status fetch failed");
    }

    public void markFaxAsRead(String accessToken, String accountId, String extensionId, String messageId)
            throws RingCentralException {
        HttpPut put = new HttpPut(buildMessageStoreUrl(accountId, extensionId) + "/" + normalizeMessageId(messageId));
        put.setHeader("Authorization", bearer(accessToken));
        put.setEntity(new StringEntity("{\"readStatus\":\"Read\"}", ContentType.APPLICATION_JSON));
        executeNoContent(put, "RingCentral mark-as-read failed");
    }

    /**
     * Returns the resolved RingCentral API origin for this connector instance.
     */
    public String getApiUrl() {
        return apiUrlResolver.get();
    }

    /**
     * Resolves the configured RingCentral API origin from {@link CarlosProperties}, falling back to
     * official defaults. Public so tests can verify the property handling without instantiating the
     * connector and its HTTP client.
     *
     * @return bare RingCentral HTTPS origin for the configured environment
     */
    public static String resolveRingCentralApiUrl() {
        return resolveFromProperties();
    }

    private static String resolveFromProperties() {
        CarlosProperties properties = CarlosProperties.getInstance();
        boolean sandbox = "true".equalsIgnoreCase(StringUtils.trimToEmpty(properties.getProperty("ringcentral.use.sandbox")));
        String propertyName = sandbox ? "ringcentral.api.sandbox.url" : "ringcentral.api.url";
        String fallback = sandbox ? DEFAULT_RINGCENTRAL_SANDBOX_API_URL : DEFAULT_RINGCENTRAL_API_URL;
        String configured = StringUtils.trimToNull(properties.getProperty(propertyName));
        if (configured == null) {
            return fallback;
        }

        try {
            URI uri = new URI(configured);
            String host = uri.getHost();
            if ("https".equalsIgnoreCase(uri.getScheme())
                    && ("platform.ringcentral.com".equalsIgnoreCase(host)
                    || "platform.devtest.ringcentral.com".equalsIgnoreCase(host))
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && StringUtils.isBlank(uri.getUserInfo())
                    && (StringUtils.isBlank(uri.getPath()) || "/".equals(uri.getPath()))
                    && StringUtils.isBlank(uri.getQuery())
                    && StringUtils.isBlank(uri.getFragment())) {
                return "https://" + host;
            }
        } catch (URISyntaxException e) {
            logger.warn("Configured {} is not a valid URI: {} - using default", propertyName, e.getMessage());
            return fallback;
        }
        logger.warn("Configured {} is not an official RingCentral HTTPS endpoint - using default", propertyName);
        return fallback;
    }

    private String buildMessageStoreUrl(String accountId, String extensionId) {
        return getApiUrl() + "/restapi/v1.0/account/" + normalizeAccountOrExtensionId(accountId)
                + "/extension/" + normalizeAccountOrExtensionId(extensionId) + "/message-store";
    }

    private String nextPageUri(RingCentralResponse.MessageList page) {
        RingCentralResponse.Navigation navigation = page.getNavigation();
        if (navigation == null || navigation.nextPage() == null) {
            return null;
        }
        String uri = StringUtils.trimToNull(navigation.nextPage().uri());
        if (uri == null) {
            return null;
        }
        // RingCentral returns absolute URIs against its own API origin. Validate before issuing
        // the next request so a hostile or misconfigured response can't redirect the pagination
        // to an attacker-controlled host. Same-origin check pins host AND scheme to the configured
        // API URL; tests using a localhost http fixture pass because their resolver returns http.
        try {
            URI parsed = new URI(uri);
            URI apiUri = new URI(getApiUrl());
            if (parsed.getHost() == null
                    || !parsed.getHost().equalsIgnoreCase(apiUri.getHost())
                    || apiUri.getScheme() == null
                    || !apiUri.getScheme().equalsIgnoreCase(parsed.getScheme())) {
                logger.warn("Refusing to follow RingCentral nextPage cursor with unexpected origin '{}'",
                        parsed.getHost());
                return null;
            }
        } catch (URISyntaxException e) {
            logger.warn("Refusing to follow malformed RingCentral nextPage cursor: {}", e.getMessage());
            return null;
        }
        return uri;
    }

    /**
     * Normalises an account or extension id for the RingCentral message-store path. Null, blank,
     * and post-sanitisation-empty inputs collapse to {@code "~"}, RingCentral's documented
     * sentinel for the access-token's current account/extension.
     */
    static String normalizeAccountOrExtensionId(String value) {
        String trimmed = StringUtils.trimToNull(value);
        if (trimmed == null) {
            return "~";
        }
        String sanitized = trimmed.replaceAll("[^A-Za-z0-9~_-]", "");
        return sanitized.isEmpty() ? "~" : sanitized;
    }

    /**
     * Normalises a RingCentral message or attachment id for direct path embedding. Unlike account
     * ids, the {@code "~"} sentinel has no documented meaning here, so blank or sanitisation-empty
     * values fail fast rather than silently retargeting the request.
     */
    static String normalizeMessageId(String value) throws RingCentralException {
        String trimmed = StringUtils.trimToNull(value);
        if (trimmed == null) {
            throw new RingCentralException("RingCentral message id is required for this request");
        }
        String sanitized = trimmed.replaceAll("[^A-Za-z0-9_-]", "");
        if (sanitized.isEmpty()) {
            throw new RingCentralException("RingCentral message id contains no valid characters");
        }
        return sanitized;
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private <T> T executeJson(HttpUriRequestBase request, Class<T> responseType,
            String errorMessage) throws RingCentralException {
        byte[] payload = executeBytes(request, errorMessage);
        try {
            return objectMapper.readValue(payload, responseType);
        } catch (IOException e) {
            throw new RingCentralException(errorMessage + ": invalid JSON response", e);
        }
    }

    private byte[] executeBytes(HttpUriRequestBase request, String errorMessage)
            throws RingCentralException {
        request.setConfig(REQUEST_CONFIG);

        // The entity body is drained and closed via EntityUtils.consume() rather than
        // try-with-resources because consume() is the documented null-tolerant drain-plus-close
        // hook (HttpEntity.close() alone does not consume an unread body).
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getCode();
            HttpEntity entity = response.getEntity();
            byte[] payload;
            try {
                payload = entity == null ? new byte[0] : EntityUtils.toByteArray(entity);
            } finally {
                consumeQuietly(entity);
            }
            if (statusCode < 200 || statusCode >= 300) {
                // Surface the response body so operators can distinguish RingCentral's many 4xx
                // subcases ("Phone number not authorized" vs "Invalid grant" vs ...). Mark 5xx
                // and 429 transient so upstream retry/queue logic treats them like network
                // failures rather than permanent client errors. RingCentral 4xx/5xx responses
                // return small typed JSON envelopes (errorCode/message/errors[]); the body is
                // capped at 1KB to bound log volume regardless of envelope shape.
                boolean transientError = statusCode >= 500 || statusCode == 429;
                throw new RingCentralException(
                        errorMessage + " with HTTP " + statusCode + bodyExcerpt(payload),
                        statusCode, transientError);
            }
            return payload;
        } catch (IOException e) {
            throw new RingCentralException(errorMessage + ": communication failure", e,
                    FaxProviderException.isTransientNetworkCause(e));
        }
    }

    private static String bodyExcerpt(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return "";
        }
        int max = Math.min(payload.length, 1024);
        return ": " + new String(payload, 0, max, StandardCharsets.UTF_8);
    }

    private void executeNoContent(HttpUriRequestBase request, String errorMessage)
            throws RingCentralException {
        executeBytes(request, errorMessage);
    }

    private void consumeQuietly(HttpEntity entity) {
        try {
            EntityUtils.consume(entity);
        } catch (IOException e) {
            // Failure to drain the body leaves the connection unreturned to the pool; surface
            // the chained cause at WARN so pool-exhaustion symptoms are diagnosable.
            logger.warn("Failed to drain RingCentral response entity (connection may not be returned to pool)", e);
        }
    }

    @PreDestroy
    public void close() throws IOException {
        httpClient.close();
    }
}
