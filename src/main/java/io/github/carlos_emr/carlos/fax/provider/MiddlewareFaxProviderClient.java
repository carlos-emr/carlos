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
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Base64;
import java.io.StringWriter;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.util.Timeout;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.utility.ValidatedHttpEndpoint;
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
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * {@inheritDoc}
     */
    @Override
    public FaxConfig.ProviderType getProviderType() {
        return FaxConfig.ProviderType.MIDDLEWARE;
    }

    /**
     * Sends a fax through the configured middleware endpoint.
     */
    @Override
    public FaxJob sendFax(FaxConfig faxConfig, FaxJob faxJob, Path filePath) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        ValidatedHttpEndpoint endpoint = validateMiddlewareConfig(faxConfig);
        try (CloseableHttpClient client = createHttpClient(faxConfig, endpoint)) {
            HttpPost post = new HttpPost(endpointUri(endpoint,
                    PATH + "/send/" + encodePathSegment(faxConfig.getFaxUser())));
            post.setHeader("Accept", ContentType.APPLICATION_XML.getMimeType());
            post.setHeader("Authorization", basicAuthorization(faxConfig));
            post.setHeader("user", faxJob.getUser());
            post.setHeader("passwd", faxConfig.getFaxPasswd());

            StringWriter body = new StringWriter();
            Marshaller marshaller = JAXBContext.newInstance(FaxJob.class).createMarshaller();
            // Marshal a small placeholder so JAXB retains its exact XML envelope
            // and element order without ever seeing the document bytes.
            FaxJob metadata = new FaxJob(faxJob);
            metadata.setDocument("CARLOS_STREAMED_DOCUMENT");
            marshaller.marshal(metadata, body);
            String xml = body.toString();
            String marker = "<document>CARLOS_STREAMED_DOCUMENT</document>";
            int start = xml.indexOf(marker);
            post.setEntity(new FaxDocumentEntity(xml.substring(0, start) + "<document>",
                    "</document>" + xml.substring(start + marker.length()), ContentType.APPLICATION_XML,
                    filePath, faxJob.getDocument(), false));

            try (var response = client.execute(post)) {
                FaxResponseStream.abortOnClose(response);
                if (response.getCode() != HttpStatus.SC_OK) {
                    throw new FaxProviderException("WEB SERVICE RESPONDED WITH " + response.getCode());
                }
                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    throw new FaxProviderException("Middleware returned an empty fax response");
                }
                return FaxXmlResponseParser.read(entity);
            }
        } catch (FaxProviderException e) {
            throw e;
        } catch (IOException e) {
            throw new FaxProviderException("PROBLEM COMMUNICATING WITH WEB SERVICE", e,
                    FaxProviderException.isTransientNetworkCause(e));
        } catch (JAXBException e) {
            throw new FaxProviderException("Middleware fax XML could not be processed", e);
        } finally {
            faxJob.setDocument(null);
        }
    }

    /**
     * Lists inbound fax metadata from middleware endpoint.
     */
    @Override
    public List<FaxJob> listInboundFaxes(FaxConfig faxConfig) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        ValidatedHttpEndpoint endpoint = validateMiddlewareConfig(faxConfig);
        try (CloseableHttpClient client = createHttpClient(faxConfig, endpoint)) {
            HttpGet get = new HttpGet(endpointUri(endpoint,
                    PATH + "/" + encodePathSegment(faxConfig.getFaxUser())));
            get.setHeader("accept", "application/json");
            get.setHeader("user", faxConfig.getFaxUser());
            get.setHeader("passwd", faxConfig.getFaxPasswd());

            try (var response = client.execute(get)) {
                FaxResponseStream.abortOnClose(response);
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

                try (FaxResponseStream input = FaxResponseStream.open(httpEntity)) {
                    // Empty replies historically mean no pending faxes.
                    try (var parser = mapper.getFactory().createParser(input)) {
                        if (parser.nextToken() == null) {
                            return new java.util.ArrayList<>();
                        }
                        return mapper.readValue(parser, new TypeReference<List<FaxJob>>() { });
                    }
                }
            }
        } catch (IOException e) {
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
        ValidatedHttpEndpoint endpoint = validateMiddlewareConfig(faxConfig);
        try (CloseableHttpClient client = createHttpClient(faxConfig, endpoint)) {
            HttpGet get = new HttpGet(endpointUri(endpoint, PATH + "/"
                    + encodePathSegment(faxConfig.getFaxUser()) + "/"
                    + encodePathSegment(fax.getFile_name())));
            get.setHeader("accept", "application/json");
            get.setHeader("user", faxConfig.getFaxUser());
            get.setHeader("passwd", faxConfig.getFaxPasswd());

            try (var response = client.execute(get)) {
                FaxResponseStream.abortOnClose(response);
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

                try (FaxDownload download = FaxDownload.read(httpEntity, mapper, "document")) {
                    FaxJob downloaded = mapper.treeToValue(download.metadata(), FaxJob.class);
                    if (FaxJob.STATUS.ERROR.equals(downloaded.getStatus())) {
                        throw new FaxProviderException("Downloaded fax is in ERROR status");
                    }
                    downloaded.setDocumentPath(download.release());
                    return downloaded;
                }
            }
        } catch (IOException e) {
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
        ValidatedHttpEndpoint endpoint = validateMiddlewareConfig(faxConfig);
        try (CloseableHttpClient client = createHttpClient(faxConfig, endpoint)) {
            HttpDelete delete = new HttpDelete(endpointUri(endpoint, PATH + "/"
                    + encodePathSegment(faxConfig.getFaxUser()) + "/"
                    + encodePathSegment(fax.getFile_name())));
            delete.setHeader("accept", "application/json");
            delete.setHeader("user", faxConfig.getFaxUser());
            delete.setHeader("passwd", faxConfig.getFaxPasswd());

            try (var response = client.execute(delete)) {
                FaxResponseStream.abortOnClose(response);
                if (response.getCode() != HttpStatus.SC_NO_CONTENT) {
                    throw new FaxProviderException("CANNOT DELETE " + fax.getFile_name());
                }
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
        ValidatedHttpEndpoint endpoint = validateMiddlewareConfig(faxConfig);
        try (CloseableHttpClient client = createHttpClient(faxConfig, endpoint)) {
            HttpGet get = new HttpGet(endpointUri(endpoint, PATH + "/" + faxJob.getJobId()));
            get.setHeader("accept", "application/json");
            get.setHeader("user", faxConfig.getFaxUser());
            get.setHeader("passwd", faxConfig.getFaxPasswd());

            try (var response = client.execute(get)) {
                FaxResponseStream.abortOnClose(response);
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

                try (FaxResponseStream input = FaxResponseStream.open(httpEntity)) {
                    return mapper.readValue(input, FaxJob.class);
                }
            }
        } catch (IOException e) {
            throw new FaxProviderException("Middleware status check communication failure", e,
                    FaxProviderException.isTransientNetworkCause(e));
        }
    }

    /**
     * Cancels a queued outbound fax on the middleware relay via HTTP PUT.
     *
     * <p>Replaces the ad-hoc client formerly inlined in the Manage Faxes admin action: this
     * path now goes through the same endpoint allow-list validation, pinned DNS resolution,
     * timeouts, and disabled redirects as every other middleware operation.</p>
     */
    @Override
    public FaxJob cancelFax(FaxConfig faxConfig, FaxJob faxJob) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        if (faxJob.getJobId() == null) {
            throw new FaxProviderException("Cannot cancel fax: job has no provider job id");
        }
        ValidatedHttpEndpoint endpoint = validateMiddlewareConfig(faxConfig);
        try (CloseableHttpClient client = createHttpClient(faxConfig, endpoint)) {
            HttpPut put = new HttpPut(endpointUri(endpoint, PATH + "/" + faxJob.getJobId()));
            put.setHeader("accept", "application/json");
            put.setHeader("user", faxConfig.getFaxUser());
            put.setHeader("passwd", faxConfig.getFaxPasswd());

            try (var response = client.execute(put)) {
                FaxResponseStream.abortOnClose(response);
                int statusCode = response.getCode();
                if (statusCode != HttpStatus.SC_OK) {
                    throw new FaxProviderException(
                            "Middleware cancel failed for job " + faxJob.getJobId() +
                            " with HTTP " + statusCode + ": " + response.getReasonPhrase());
                }
                FaxJob cancelled = new FaxJob(faxJob);
                cancelled.setStatus(FaxJob.STATUS.CANCELLED);
                cancelled.setStatusString("Cancelled on middleware relay");
                return cancelled;
            }
        } catch (IOException e) {
            throw new FaxProviderException("Middleware fax cancel communication failure", e,
                    FaxProviderException.isTransientNetworkCause(e));
        }
    }

    /**
     * Validates that middleware connection parameters are configured.
     * Called before any API operation to fail fast with a clear message.
     */
    private ValidatedHttpEndpoint validateMiddlewareConfig(FaxConfig faxConfig) throws FaxProviderException {
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
        return resolveEndpoint(faxConfig);
    }

    private ValidatedHttpEndpoint resolveEndpoint(FaxConfig faxConfig) throws FaxProviderException {
        try {
            return ValidatedHttpEndpoint.resolve(
                    faxConfig.getUrl(), "carlos.fax.middleware.allowedHosts");
        } catch (ValidatedHttpEndpoint.ValidationException e) {
            throw new FaxProviderException("Middleware URL was rejected: " + e.getMessage(), e);
        }
    }

    /**
     * Creates authenticated HTTP client for middleware pull endpoints with timeout configuration.
     *
     * <p>Configures 30-second connection timeout and 60-second socket timeout to prevent
     * hung connections from stalling fax processing.</p>
     */
    private CloseableHttpClient createHttpClient(
            FaxConfig faxConfig, ValidatedHttpEndpoint endpoint) {
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        URI base = endpoint.uri();
        int port = base.getPort() >= 0
                ? base.getPort()
                : (endpoint.isHttps() ? 443 : 80);
        AuthScope authScope = new AuthScope(base.getHost(), port);
        credentialsProvider.setCredentials(authScope,
                new UsernamePasswordCredentials(faxConfig.getSiteUser(), faxConfig.getPasswd().toCharArray()));

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(30))
                .setResponseTimeout(Timeout.ofSeconds(60))
                .build();

        return HttpClientBuilder.create()
                // Fax jobs own retries; internal 503 retries drain uncapped bodies before returning.
                .disableAutomaticRetries()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(endpoint.pinnedDnsResolver())
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofSeconds(30))
                                .setSocketTimeout(Timeout.ofSeconds(60))
                                .build())
                        .build())
                .setDefaultCredentialsProvider(credentialsProvider)
                .setDefaultRequestConfig(requestConfig)
                .disableRedirectHandling()
                .build();
    }

    private URI endpointUri(ValidatedHttpEndpoint endpoint, String path) {
        String base = endpoint.uri().toString();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + path);
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String basicAuthorization(FaxConfig faxConfig) {
        String login = faxConfig.getSiteUser() + ":" + faxConfig.getPasswd();
        return "Basic " + Base64.getEncoder()
                .encodeToString(login.getBytes(StandardCharsets.UTF_8));
    }
}
