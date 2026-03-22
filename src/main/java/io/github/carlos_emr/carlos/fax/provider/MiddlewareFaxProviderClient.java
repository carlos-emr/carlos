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
package io.github.carlos_emr.carlos.fax.provider;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.common.util.Base64Utility;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Provider client that preserves the existing middleware fax API contract.
 *
 * <p>This class extracts all middleware transport details from core fax orchestration so the
 * pipeline can remain provider-agnostic.</p>
 *
 * <p><strong>Note on markFaxAsRead:</strong> This client intentionally does NOT override the
 * default no-op {@link FaxProviderClient#markFaxAsRead} method. Middleware uses delete semantics
 * for duplicate prevention (via {@link #deleteFax}) rather than read/unread semantics. After
 * a fax is successfully imported locally, {@link #deleteFax} removes it from the relay server.</p>
 *
 * @since 2026-02-11
 */
@Component
public class MiddlewareFaxProviderClient implements FaxProviderClient {

    private static final String PATH = "/fax";
    private static final Logger logger = MiscUtils.getLogger();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * {@inheritDoc}
     */
    @Override
    public FaxConfig.ProviderType getProviderType() {
        return FaxConfig.ProviderType.MIDDLEWARE;
    }

    /**
     * Sends outbound fax through existing middleware /fax/send endpoint.
     *
     * <p>Note: No URL validation is performed on {@code faxConfig.getUrl()} because the middleware
     * endpoint is intentionally configurable to any custom external fax handler. Clinics may run
     * their own relay servers at arbitrary URLs. Credentials are sent via Basic Auth and custom
     * headers per the middleware protocol.</p>
     */
    @Override
    public FaxJob sendFax(FaxConfig faxConfig, FaxJob faxJob, Path filePath) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        validateMiddlewareConfig(faxConfig);

        WebClient client = null;
        try {
            client = WebClient.create(faxConfig.getUrl());
            client.path(PATH + "/send/" + faxConfig.getFaxUser());
            client.type(MediaType.APPLICATION_XML);
            client.accept(MediaType.APPLICATION_XML);

            String login = faxConfig.getSiteUser() + ":" + faxConfig.getPasswd();
            String authorizationHeader = "Basic " + Base64Utility.encode(login.getBytes());
            client.header("Authorization", authorizationHeader);
            client.header("user", faxJob.getUser());
            client.header("passwd", faxConfig.getFaxPasswd());

            HTTPConduit conduit = WebClient.getConfig(client).getHttpConduit();
            HTTPClientPolicy policy = new HTTPClientPolicy();
            policy.setConnectionTimeout(30000);
            policy.setReceiveTimeout(60000);
            conduit.setClient(policy);

            if (filePath != null) {
                if (!Files.exists(filePath)) {
                    throw new FaxProviderException("Fax document file not found");
                }
                if (!Files.isReadable(filePath)) {
                    throw new FaxProviderException("Fax document file is not readable (check permissions)");
                }
                faxJob.setDocument(Base64Utility.encode(Files.readAllBytes(filePath)));
            }

            if (StringUtils.isBlank(faxJob.getDocument())) {
                throw new FaxProviderException("Fatal error locating document. Not found in filesystem or database backup");
            }

            Response httpResponse = client.post(faxJob);
            // Free memory after POST serialization
            faxJob.setDocument(null);
            if (httpResponse.getStatus() != HttpStatus.SC_OK) {
                throw new FaxProviderException("WEB SERVICE RESPONDED WITH " + httpResponse.getStatus());
            }

            FaxJob result = httpResponse.readEntity(FaxJob.class);
            return result;
        } catch (FaxProviderException e) {
            // Re-throw FaxProviderException as-is to preserve specific error messages
            throw e;
        } catch (IOException e) {
            throw new FaxProviderException("Failed to read fax document file: " + e.getMessage(), e);
        } catch (ProcessingException e) {
            throw new FaxProviderException("PROBLEM COMMUNICATING WITH WEB SERVICE", e,
                    FaxProviderException.isTransientNetworkCause(e));
        } catch (WebApplicationException e) {
            throw new FaxProviderException("WEB SERVICE RESPONDED WITH ERROR: " + e.getMessage(), e);
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    logger.debug("WebClient close failed during fax send cleanup: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Lists inbound fax metadata from the middleware relay endpoint.
     *
     * <p>Sends an authenticated GET request to the middleware fax list endpoint and
     * deserializes the JSON response into a list of {@link FaxJob} objects.</p>
     *
     * @param faxConfig FaxConfig provider configuration with middleware URL and credentials
     * @return List&lt;FaxJob&gt; list of inbound fax metadata available for download
     * @throws FaxProviderException when the middleware returns an error or communication fails
     */
    @Override
    public List<FaxJob> listInboundFaxes(FaxConfig faxConfig) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        validateMiddlewareConfig(faxConfig);

        try (CloseableHttpClient client = createDownloadClient(faxConfig)) {
            HttpGet get = new HttpGet(faxConfig.getUrl() + PATH + "/" + URLEncoder.encode(faxConfig.getFaxUser(), "UTF-8"));
            get.setHeader("accept", "application/json");
            get.setHeader("user", faxConfig.getFaxUser());
            get.setHeader("passwd", faxConfig.getFaxPasswd());

            var response = client.execute(get);
            int statusCode = response.getCode();

            if (statusCode != HttpStatus.SC_OK) {
                throw new FaxProviderException(
                        "Middleware list faxes failed with HTTP " + statusCode +
                        ": " + response.getReasonPhrase() +
                        ". Check middleware server logs and fax account configuration.");
            }

            HttpEntity httpEntity = response.getEntity();
            if (httpEntity == null) {
                throw new FaxProviderException(
                        "Middleware returned HTTP 200 but response body is empty. " +
                        "This may indicate a middleware server error.");
            }

            String content = EntityUtils.toString(httpEntity);
            if (content == null || content.trim().isEmpty()) {
                logger.warn("Middleware returned empty content for fax list - treating as no faxes available");
                return new java.util.ArrayList<>();
            }

            return mapper.readValue(content, new TypeReference<List<FaxJob>>() { });
        } catch (IOException | ParseException e) {
            throw new FaxProviderException("Middleware fax list communication failure: " + e.getMessage(), e,
                    FaxProviderException.isTransientNetworkCause(e));
        }
    }

    /**
     * Downloads an inbound fax from middleware endpoint.
     */
    @Override
    public FaxJob downloadFax(FaxConfig faxConfig, FaxJob fax) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        validateMiddlewareConfig(faxConfig);

        try (CloseableHttpClient client = createDownloadClient(faxConfig)) {
            HttpGet get = new HttpGet(faxConfig.getUrl() + PATH + "/"
                    + URLEncoder.encode(faxConfig.getFaxUser(), "UTF-8") + "/"
                    + URLEncoder.encode(fax.getFile_name(), "UTF-8"));
            get.setHeader("accept", "application/json");
            get.setHeader("user", faxConfig.getFaxUser());
            get.setHeader("passwd", faxConfig.getFaxPasswd());

            var response = client.execute(get);
            int statusCode = response.getCode();

            if (statusCode != HttpStatus.SC_OK) {
                throw new FaxProviderException(
                        "Middleware download failed for fax " + fax.getFile_name() +
                        " with HTTP " + statusCode + ": " + response.getReasonPhrase());
            }

            HttpEntity httpEntity = response.getEntity();
            if (httpEntity == null) {
                throw new FaxProviderException(
                        "Middleware returned HTTP 200 but response body is empty for fax " + fax.getFile_name());
            }

            String content = EntityUtils.toString(httpEntity);
            FaxJob downloaded = mapper.readValue(content, FaxJob.class);
            if (FaxJob.STATUS.ERROR.equals(downloaded.getStatus())) {
                throw new FaxProviderException("Downloaded fax is in ERROR status: " + downloaded.getStatusString());
            }
            return downloaded;
        } catch (IOException | ParseException e) {
            throw new FaxProviderException("Middleware fax download failure for " + fax.getFile_name() + ": " + e.getMessage(), e,
                    FaxProviderException.isTransientNetworkCause(e));
        }
    }

    /**
     * Deletes an inbound fax on middleware after local import succeeds.
     */
    @Override
    public void deleteFax(FaxConfig faxConfig, FaxJob fax) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        validateMiddlewareConfig(faxConfig);

        try (CloseableHttpClient client = createDownloadClient(faxConfig)) {
            HttpDelete delete = new HttpDelete(faxConfig.getUrl() + PATH + "/"
                    + URLEncoder.encode(faxConfig.getFaxUser(), "UTF-8") + "/"
                    + URLEncoder.encode(fax.getFile_name(), "UTF-8"));
            delete.setHeader("accept", "application/json");
            delete.setHeader("user", faxConfig.getFaxUser());
            delete.setHeader("passwd", faxConfig.getFaxPasswd());

            var response = client.execute(delete);
            if (response.getCode() != HttpStatus.SC_NO_CONTENT) {
                throw new FaxProviderException("CANNOT DELETE " + fax.getFile_name());
            }
        } catch (IOException e) {
            throw new FaxProviderException("Middleware fax delete communication failure", e,
                    FaxProviderException.isTransientNetworkCause(e));
        }
    }

    /**
     * Retrieves outbound fax delivery status from middleware.
     */
    @Override
    public FaxJob fetchFaxStatus(FaxConfig faxConfig, FaxJob faxJob) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        validateMiddlewareConfig(faxConfig);

        try (CloseableHttpClient client = createDownloadClient(faxConfig)) {
            HttpGet get = new HttpGet(faxConfig.getUrl() + PATH + "/" + faxJob.getJobId());
            get.setHeader("accept", "application/json");
            get.setHeader("user", faxConfig.getFaxUser());
            get.setHeader("passwd", faxConfig.getFaxPasswd());

            var response = client.execute(get);
            int statusCode = response.getCode();

            if (statusCode != HttpStatus.SC_OK) {
                throw new FaxProviderException(
                        "Middleware status check failed for job " + faxJob.getJobId() +
                        " with HTTP " + statusCode + ": " + response.getReasonPhrase());
            }

            HttpEntity httpEntity = response.getEntity();
            if (httpEntity == null) {
                throw new FaxProviderException(
                        "Middleware returned HTTP 200 but response body is empty for job " + faxJob.getJobId());
            }

            String content = EntityUtils.toString(httpEntity);
            return mapper.readValue(content, FaxJob.class);
        } catch (IOException | ParseException e) {
            throw new FaxProviderException("Middleware status check communication failure", e,
                    FaxProviderException.isTransientNetworkCause(e));
        }
    }

    /**
     * Validates that middleware connection parameters are configured.
     * Called before any API operation to fail fast with a clear message.
     */
    private void validateMiddlewareConfig(FaxConfig faxConfig) throws FaxProviderException {
        if (faxConfig.getUrl() == null || faxConfig.getUrl().trim().isEmpty()) {
            throw new FaxProviderException("Middleware URL is not configured for this fax account");
        }
        if (faxConfig.getSiteUser() == null || faxConfig.getSiteUser().trim().isEmpty()) {
            throw new FaxProviderException("Middleware site user is not configured for this fax account");
        }
        if (faxConfig.getFaxUser() == null || faxConfig.getFaxUser().trim().isEmpty()) {
            throw new FaxProviderException("Middleware fax user is not configured for this fax account");
        }
        if (faxConfig.getPasswd() == null || faxConfig.getPasswd().trim().isEmpty()) {
            throw new FaxProviderException("Middleware site password is not configured for this fax account");
        }
        if (faxConfig.getFaxPasswd() == null || faxConfig.getFaxPasswd().trim().isEmpty()) {
            throw new FaxProviderException("Middleware fax password is not configured for this fax account");
        }
    }

    /**
     * Creates authenticated HTTP client for middleware pull endpoints with timeout configuration.
     *
     * <p>Configures 30-second connection timeout and 60-second socket timeout to prevent
     * hung connections from stalling fax processing.</p>
     */
    private CloseableHttpClient createDownloadClient(FaxConfig faxConfig) {
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(null, -1),
                new UsernamePasswordCredentials(faxConfig.getSiteUser(), faxConfig.getPasswd().toCharArray()));

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(30))
                .setResponseTimeout(Timeout.ofSeconds(60))
                .build();

        return HttpClientBuilder.create()
                .setDefaultCredentialsProvider(credentialsProvider)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }
}
