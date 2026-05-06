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
    private static final Logger logger = MiscUtils.getLogger();
    private static final RequestConfig REQUEST_CONFIG = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(30))
            .setResponseTimeout(Timeout.ofSeconds(60))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final CloseableHttpClient httpClient = HttpClients.custom()
            .setDefaultRequestConfig(REQUEST_CONFIG)
            .setMaxConnTotal(20)
            .setMaxConnPerRoute(10)
            .build();

    /**
     * Authenticates with RingCentral using OAuth 2.0 JWT bearer flow.
     */
    public RingCentralResponse.Token authenticate(String clientId, String clientSecret, String jwtToken)
            throws RingCentralException {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("grant_type", GRANT_TYPE_JWT));
        params.add(new BasicNameValuePair("assertion", jwtToken));

        HttpPost post = new HttpPost(resolveRingCentralApiUrl() + "/restapi/oauth/token");
        post.setHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8)));
        post.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));

        return executeJson(post, RingCentralResponse.Token.class, "RingCentral authentication failed");
    }

    /**
     * Sends a fax through RingCentral.
     */
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
     * Lists unread inbound RingCentral fax messages.
     */
    public RingCentralResponse.MessageList getInboundFaxes(String accessToken, String accountId, String extensionId)
            throws RingCentralException {
        HttpGet get = new HttpGet(buildMessageStoreUrl(accountId, extensionId)
                + "?messageType=Fax&direction=Inbound&readStatus=Unread&perPage=100");
        get.setHeader("Authorization", bearer(accessToken));
        return executeJson(get, RingCentralResponse.MessageList.class, "RingCentral inbox fetch failed");
    }

    /**
     * Downloads a RingCentral fax attachment.
     */
    public byte[] downloadFax(String accessToken, String accountId, String extensionId, String messageId,
            String attachmentId) throws RingCentralException {
        HttpGet get = new HttpGet(buildMessageStoreUrl(accountId, extensionId) + "/"
                + normalizePathId(messageId) + "/content/" + normalizePathId(attachmentId));
        get.setHeader("Authorization", bearer(accessToken));
        return executeBytes(get, "RingCentral fax download failed");
    }

    /**
     * Fetches message status for an outbound or inbound RingCentral fax.
     */
    public RingCentralResponse.Message getFaxStatus(String accessToken, String accountId, String extensionId,
            String messageId) throws RingCentralException {
        HttpGet get = new HttpGet(buildMessageStoreUrl(accountId, extensionId) + "/" + normalizePathId(messageId));
        get.setHeader("Authorization", bearer(accessToken));
        return executeJson(get, RingCentralResponse.Message.class, "RingCentral fax status fetch failed");
    }

    /**
     * Marks an inbound RingCentral fax as read after successful local persistence.
     */
    public void markFaxAsRead(String accessToken, String accountId, String extensionId, String messageId)
            throws RingCentralException {
        HttpPut put = new HttpPut(buildMessageStoreUrl(accountId, extensionId) + "/" + normalizePathId(messageId));
        put.setHeader("Authorization", bearer(accessToken));
        put.setEntity(new StringEntity("{\"readStatus\":\"Read\"}", ContentType.APPLICATION_JSON));
        executeNoContent(put, "RingCentral mark-as-read failed");
    }

    /**
     * Resolves the configured RingCentral API origin, falling back to official defaults.
     *
     * @return bare RingCentral HTTPS origin for the configured environment
     */
    public static String resolveRingCentralApiUrl() {
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
        return resolveRingCentralApiUrl() + "/restapi/v1.0/account/" + normalizePathId(accountId)
                + "/extension/" + normalizePathId(extensionId) + "/message-store";
    }

    private String normalizePathId(String value) {
        String trimmed = StringUtils.trimToNull(value);
        return trimmed == null ? "~" : trimmed.replaceAll("[^A-Za-z0-9~_-]", "");
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

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getCode();
            HttpEntity entity = response.getEntity();
            try {
                byte[] payload = entity == null ? new byte[0] : EntityUtils.toByteArray(entity);
                if (statusCode < 200 || statusCode >= 300) {
                    throw new RingCentralException(errorMessage + " with HTTP " + statusCode);
                }
                return payload;
            } finally {
                consumeQuietly(entity);
            }
        } catch (IOException e) {
            throw new RingCentralException(errorMessage + ": communication failure", e,
                    FaxProviderException.isTransientNetworkCause(e));
        }
    }

    private void executeNoContent(HttpUriRequestBase request, String errorMessage)
            throws RingCentralException {
        executeBytes(request, errorMessage);
    }

    private void consumeQuietly(HttpEntity entity) {
        try {
            EntityUtils.consume(entity);
        } catch (IOException e) {
            // Cleanup can fail if the provider already closed the connection; the primary response handling has completed.
            logger.debug("Failed to consume RingCentral response entity", e);
        }
    }

    /**
     * Closes the shared HTTP client when the Spring context is shutting down.
     */
    @PreDestroy
    public void close() throws IOException {
        httpClient.close();
    }
}
