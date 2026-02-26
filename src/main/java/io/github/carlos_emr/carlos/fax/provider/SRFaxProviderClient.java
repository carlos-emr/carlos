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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import org.apache.cxf.common.util.Base64Utility;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.logging.log4j.Logger;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;
import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Fax provider client implementation for direct SRFax API integration.
 *
 * <p>This implementation uses the SRFax HTTP form-based API. The endpoint URL defaults to
 * {@link #DEFAULT_SRFAX_API_URL} but can be overridden via the {@code srfax.api.url} property
 * in carlos.properties. Credentials are mapped from fax config fields as follows:</p>
 * <ul>
 *   <li><strong>access_id</strong>: {@link FaxConfig#getFaxUser()} (SRFax account number)</li>
 *   <li><strong>access_pwd</strong>: {@link FaxConfig#getFaxPasswd()} (SRFax account password)</li>
 * </ul>
 *
 * <p><strong>SRFax API Reference:</strong>
 * <a href="https://www.srfax.com/developers/internet-fax-api/getting-started/">
 * https://www.srfax.com/developers/internet-fax-api/getting-started/</a></p>
 *
 * @since 2026-02-11
 */
@Component
public class SRFaxProviderClient implements FaxProviderClient {

    /** Default SRFax API endpoint, used when srfax.api.url is not configured in properties. */
    public static final String DEFAULT_SRFAX_API_URL = "https://www.srfax.com/SRF_SecWebSvc.php";

    private static final String ACTION_QUEUE_FAX = "Queue_Fax";
    private static final String ACTION_GET_INBOX = "Get_Fax_Inbox";
    private static final String ACTION_RETRIEVE_FAX = "Retrieve_Fax";
    private static final String ACTION_GET_STATUS = "Get_FaxStatus";

    private static final Logger logger = MiscUtils.getLogger();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Returns the SRFax API endpoint URL. Defaults to {@link #DEFAULT_SRFAX_API_URL} and
     * can only be overridden via {@code srfax.api.url} in carlos.properties.
     *
     * <p>The override must point to an official srfax.com domain. Any other value is rejected
     * with a warning log and the default is used instead. This prevents SSRF or credential
     * theft via admin-configured URLs.</p>
     */
    private String getSrfaxApiUrl() {
        String configured = OscarProperties.getInstance().getProperty("srfax.api.url");
        if (configured != null && !configured.trim().isEmpty()) {
            String trimmed = configured.trim();
            try {
                java.net.URI uri = new java.net.URI(trimmed);
                String host = uri.getHost();
                if (host != null
                        && (host.equalsIgnoreCase("srfax.com") || host.toLowerCase().endsWith(".srfax.com"))
                        && "https".equalsIgnoreCase(uri.getScheme())) {
                    return trimmed;
                }
            } catch (java.net.URISyntaxException e) {
                logger.warn("Configured srfax.api.url '{}' is not a valid URI: {} - using default",
                        trimmed, e.getMessage());
                return DEFAULT_SRFAX_API_URL;
            }
            logger.warn("Configured srfax.api.url '{}' is not a valid srfax.com HTTPS endpoint - using default", trimmed);
        }
        return DEFAULT_SRFAX_API_URL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FaxConfig.ProviderType getProviderType() {
        return FaxConfig.ProviderType.SRFAX;
    }

    /**
     * Sends an outbound fax through SRFax.
     *
     * <p>The document is sent as base64 content and destination is taken from
     * {@link FaxJob#getDestination()}.</p>
     */
    @Override
    public FaxJob sendFax(FaxConfig faxConfig, FaxJob faxJob, Path filePath) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        validateCredentials(faxConfig);

        try {
            // Validate required fields before expensive file I/O
            if (faxJob.getDestination() == null || faxJob.getDestination().trim().isEmpty()) {
                throw new FaxProviderException("Fax destination number is required but was not provided");
            }
            if (faxConfig.getFaxNumber() == null || faxConfig.getFaxNumber().trim().isEmpty()) {
                throw new FaxProviderException("Caller ID (fax number) is required but was not configured");
            }

            if (filePath != null) {
                if (!Files.exists(filePath)) {
                    throw new FaxProviderException("Fax document file not found");
                }
                if (!Files.isReadable(filePath)) {
                    throw new FaxProviderException("Fax document file is not readable (check permissions)");
                }
                faxJob.setDocument(Base64Utility.encode(Files.readAllBytes(filePath)));
            }

            if (faxJob.getDocument() == null || faxJob.getDocument().isEmpty()) {
                throw new FaxProviderException("Fatal error locating document. Not found in filesystem or database backup");
            }

            // Log at DEBUG level with masked destination to avoid PHI exposure
            if (logger.isDebugEnabled()) {
                String maskedDestination = faxJob.getDestination().length() > 4
                        ? "***" + faxJob.getDestination().substring(faxJob.getDestination().length() - 4)
                        : "****";
                logger.debug("SRFax send requested destination={}", maskedDestination);
            }

            List<NameValuePair> params = createAuthParams(faxConfig);
            params.add(new BasicNameValuePair("action", ACTION_QUEUE_FAX));
            params.add(new BasicNameValuePair("sCallerID", faxConfig.getFaxNumber()));
            params.add(new BasicNameValuePair("sSenderEmail", faxConfig.getSenderEmail()));
            params.add(new BasicNameValuePair("sFaxType", "SINGLE"));
            params.add(new BasicNameValuePair("sToFaxNumber", faxJob.getDestination()));
            params.add(new BasicNameValuePair("sFileName_1", faxJob.getFile_name()));
            params.add(new BasicNameValuePair("sFileContent_1", faxJob.getDocument()));

            // Free memory now that request params are built
            faxJob.setDocument(null);

            JsonNode root = postForm(getSrfaxApiUrl(), params);
            ensureSuccess(root, "Failed to queue fax with SRFax");

            // SRFax returns the FaxDetailsID directly as a flat string in the Result field.
            String jobId = textAt(root, "Result");

            FaxJob result = new FaxJob();
            if (jobId != null) {
                try {
                    result.setJobId(Long.parseLong(jobId));
                } catch (NumberFormatException e) {
                    logger.warn("SRFax returned non-numeric job ID: {} - fax may not be trackable for status updates", jobId);
                    result.setStatusString("SRFax queued with non-numeric id: " + jobId);
                }
            }

            result.setStatus(FaxJob.STATUS.SENT);
            if (logger.isInfoEnabled()) {
                logger.info("SRFax send queued providerJobId={}", result.getJobId());
            }
            if (result.getStatusString() == null || result.getStatusString().isEmpty()) {
                result.setStatusString("Queued with SRFax");
            }
            return result;
        } catch (IOException e) {
            throw new FaxProviderException("Failed to read fax document file: " + e.getMessage(), e);
        }
    }

    /**
     * Lists inbound fax headers available in SRFax inbox.
     *
     * <p><strong>Duplicate Prevention Strategy:</strong> This method pulls only unread faxes
     * (sViewedStatus=UNREAD per SRFax API spec) to avoid re-importing documents. Faxes are marked
     * as read after successful local import via {@link #markFaxAsRead(FaxConfig, FaxJob)}.
     * This three-phase approach prevents both duplicates and fax loss: if import fails, the fax
     * remains unread and will be re-fetched on the next poll.</p>
     *
     * @param faxConfig SRFax account configuration with authentication credentials
     * @return list of unread fax jobs available for import
     * @throws FaxProviderException if authentication fails or API request fails
     */
    @Override
    public List<FaxJob> listInboundFaxes(FaxConfig faxConfig) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        validateCredentials(faxConfig);

        List<NameValuePair> params = createAuthParams(faxConfig);
        params.add(new BasicNameValuePair("action", ACTION_GET_INBOX));
        // Pull only unread faxes to avoid duplicates (per SRFax API spec: sViewedStatus).
        params.add(new BasicNameValuePair("sViewedStatus", "UNREAD"));

        JsonNode root = postForm(getSrfaxApiUrl(), params);
        ensureSuccess(root, "Failed to fetch SRFax inbox");

        List<FaxJob> result = new ArrayList<>();
        // Per SRFax spec, Result is directly an array of fax property objects.
        JsonNode rows = nodeAt(root, "Result");

        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) {
                FaxJob faxJob = new FaxJob();
                String fileName = textOrDefault(row, "FileName", "FaxFileName", "FaxName", String.valueOf(System.currentTimeMillis()));
                faxJob.setFile_name(fileName);
                faxJob.setRecipient(textOrDefault(row, "CallerID", "FromFaxNumber", "Unknown"));
                faxJob.setStatus(FaxJob.STATUS.RECEIVED);
                faxJob.setStatusString("Ready for SRFax download");
                faxJob.setStamp(new Date());

                // Per SRFax spec, the FaxDetailsID is embedded in the FileName after a pipe character.
                // Example: "20260219124500-1234-1_1|12345678"
                if (fileName.contains("|")) {
                    String faxDetailsId = fileName.substring(fileName.lastIndexOf('|') + 1).trim();
                    if (!faxDetailsId.isEmpty()) {
                        try {
                            faxJob.setJobId(Long.parseLong(faxDetailsId));
                        } catch (NumberFormatException e) {
                            logger.debug("SRFax FileName pipe segment is non-numeric: {}", faxDetailsId);
                        }
                    }
                }

                result.add(faxJob);
            }
        }

        if (logger.isInfoEnabled()) {
            logger.info("SRFax inbox listed unreadCount={}", result.size());
        }
        return result;
    }

    /**
     * Downloads a specific fax document from SRFax without marking it as read.
     *
     * <p>This is the first phase of the three-phase import strategy: download the fax content
     * without side-effects. The fax remains unread on the SRFax server until
     * {@link #markFaxAsRead(FaxConfig, FaxJob)} is called after successful local import.
     * This prevents fax loss if the import fails after download.</p>
     */
    @Override
    public FaxJob downloadFax(FaxConfig faxConfig, FaxJob fax) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        validateCredentials(faxConfig);

        logger.debug("SRFax download requested for fax id={}", fax.getId());
        List<NameValuePair> params = createAuthParams(faxConfig);
        params.add(new BasicNameValuePair("action", ACTION_RETRIEVE_FAX));
        params.add(new BasicNameValuePair("sFaxFileName", fax.getFile_name()));
        params.add(new BasicNameValuePair("sDirection", "IN"));  // Required: "IN" for received faxes, "OUT" for sent
        params.add(new BasicNameValuePair("sFaxFormat", "PDF")); // Explicit format request (API defaults to PDF)
        // Mark-as-read is deferred to markFaxAsRead() after successful local import (three-phase strategy).

        JsonNode root = postForm(getSrfaxApiUrl(), params);
        ensureSuccess(root, "Failed to download SRFax document");

        // SRFax returns the base64-encoded document directly in the Result field.
        String base64Doc = textAt(root, "Result");
        if (base64Doc == null || base64Doc.isEmpty()) {
            throw new FaxProviderException("SRFax download response did not include document content");
        }

        FaxJob downloaded = new FaxJob(fax);
        downloaded.setDocument(base64Doc);
        downloaded.setStatus(FaxJob.STATUS.RECEIVED);
        downloaded.setStatusString("Downloaded from SRFax");
        logger.info("SRFax download completed");
        return downloaded;
    }

    /**
     * Marks a remote inbound fax as read on the SRFax server after successful local import.
     *
     * <p>This is the second phase of the three-phase import strategy. The {@link #downloadFax}
     * method downloads content without marking as read. This method is called only after the
     * fax has been successfully persisted locally, preventing fax loss if import fails.</p>
     *
     * <p>Uses the {@code Retrieve_Fax} action with {@code sMarkasViewed=Y} to mark the fax
     * as read. The response content is not needed -- we only care about the side-effect of
     * marking the fax as viewed.</p>
     *
     * @param faxConfig SRFax account configuration with authentication credentials
     * @param fax FaxJob fax metadata identifying which fax to mark as read (uses file_name)
     * @throws FaxProviderException when the mark-as-read API call fails
     * @since 2026-02-13
     */
    @Override
    public void markFaxAsRead(FaxConfig faxConfig, FaxJob fax) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        validateCredentials(faxConfig);

        List<NameValuePair> params = createAuthParams(faxConfig);
        params.add(new BasicNameValuePair("action", ACTION_RETRIEVE_FAX));
        params.add(new BasicNameValuePair("sFaxFileName", fax.getFile_name()));
        params.add(new BasicNameValuePair("sDirection", "IN"));
        params.add(new BasicNameValuePair("sMarkasViewed", "Y"));

        JsonNode root = postForm(getSrfaxApiUrl(), params);
        ensureSuccess(root, "Failed to mark fax as read on SRFax");

        logger.info("SRFax fax marked as read: id={}", fax.getId());
    }

    /**
     * Acknowledges inbound fax handling.
     *
     * <p>For SRFax we do not delete server-side faxes. Duplicate prevention is handled by
     * pulling unread-only and marking as read via {@link #markFaxAsRead(FaxConfig, FaxJob)}
     * after successful local import.</p>
     */
    @Override
    public void deleteFax(FaxConfig faxConfig, FaxJob fax) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);

        // Intentionally no-op for SRFax.
        logger.debug("SRFax delete skipped (using unread/read semantics)");
    }

    /**
     * Fetches delivery status for outbound fax jobs.
     */
    @Override
    public FaxJob fetchFaxStatus(FaxConfig faxConfig, FaxJob faxJob) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        validateCredentials(faxConfig);

        logger.debug("SRFax status requested for providerJobId={}", faxJob.getJobId());
        List<NameValuePair> params = createAuthParams(faxConfig);
        params.add(new BasicNameValuePair("action", ACTION_GET_STATUS));
        params.add(new BasicNameValuePair("sFaxDetailsID", String.valueOf(faxJob.getJobId()))); // Correct parameter name from Queue_Fax response

        JsonNode root = postForm(getSrfaxApiUrl(), params);
        ensureSuccess(root, "Failed to fetch SRFax fax status");

        // SRFax returns delivery status in the SentStatus field within the Result object.
        // Possible values: "In Progress", "Sent", "Failed", "Sending Email"
        String providerStatus = textAt(root, "Result", "SentStatus");

        FaxJob updated = new FaxJob(faxJob);
        updated.setStatus(mapStatus(providerStatus));
        updated.setStatusString(providerStatus == null ? "Unknown SRFax status" : providerStatus);
        if (logger.isDebugEnabled()) {
            logger.debug("SRFax status response providerJobId={} status={}", faxJob.getJobId(), updated.getStatusString());
        }
        return updated;
    }

    /**
     * Maps SRFax provider status strings to internal {@link FaxJob.STATUS} enumeration values.
     *
     * <p><strong>Status Mapping Tolerance:</strong> This method uses fuzzy matching (contains checks)
     * because SRFax status text varies across API versions and account configurations. Status strings
     * are normalized to lowercase for case-insensitive matching. This defensive approach prevents
     * status mapping failures when the SRFax API returns status strings in different formats.</p>
     *
     * <p><strong>SRFax SentStatus values</strong> (per API spec):
     * "In Progress", "Sent", "Failed", "Sending Email".</p>
     *
     * <p>Mapping rules (fuzzy matching for forward-compatibility). Order matters -- in-progress
     * statuses are checked first because "Sending Email" contains "sent":</p>
     * <ul>
     *   <li>QUEUE/PROCESSING/PROGRESS/RETRY/SENDING → {@code SENT} (in-progress, checked first)</li>
     *   <li>SUCCESS/COMPLETE/SENT → {@code COMPLETE} (delivery confirmed)</li>
     *   <li>CANCEL → {@code CANCELLED}</li>
     *   <li>ERROR/FAIL/BUSY/NO ANSWER → {@code ERROR}</li>
     *   <li>null or unrecognized → {@code UNKNOWN}</li>
     * </ul>
     *
     * @param providerStatus raw status text from SRFax API response (may be null)
     * @return normalized FaxJob.STATUS value, defaulting to UNKNOWN for null/unrecognized values
     */
    FaxJob.STATUS mapStatus(String providerStatus) {
        if (providerStatus == null) {
            return FaxJob.STATUS.UNKNOWN;
        }

        String normalized = providerStatus.trim().toLowerCase();

        // Queue/in-progress statuses MUST be checked before terminal statuses because
        // "Sending Email" contains "sent" which would otherwise match COMPLETE.
        if (normalized.contains("queue") || normalized.contains("processing") || normalized.contains("progress")
                || normalized.contains("retry") || normalized.contains("sending")) {
            return FaxJob.STATUS.SENT;
        }

        // Completed/terminal success statuses (SRFax "Sent" = delivery confirmed)
        if (normalized.contains("success") || normalized.contains("complete") || normalized.contains("sent")) {
            return FaxJob.STATUS.COMPLETE;
        }

        // Explicit cancel/failed statuses
        if (normalized.contains("cancel")) {
            return FaxJob.STATUS.CANCELLED;
        }
        if (normalized.contains("error") || normalized.contains("fail") || normalized.contains("busy") || normalized.contains("no answer")) {
            return FaxJob.STATUS.ERROR;
        }

        return FaxJob.STATUS.UNKNOWN;
    }

    /**
     * Posts a URL-encoded form request and parses a JSON response body.
     * 
     * <p>Configures explicit timeouts to prevent hung sockets from stalling fax processing:
     * <ul>
     *   <li>Connection timeout: 30 seconds</li>
     *   <li>Socket timeout: 60 seconds</li>
     *   <li>Connection request timeout: 30 seconds</li>
     * </ul>
     */
    private JsonNode postForm(String endpoint, List<NameValuePair> params) throws FaxProviderException {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(30_000)           // Connection establishment timeout
                .setSocketTimeout(60_000)            // Socket read timeout
                .setConnectionRequestTimeout(30_000) // Connection pool timeout
                .build();

        HttpPost httpPost = new HttpPost(endpoint);
        httpPost.setConfig(requestConfig);

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {
            httpPost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));

            try (CloseableHttpResponse response = client.execute(httpPost)) {
                // Validate HTTP status code
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != HttpStatus.SC_OK) {
                    throw new FaxProviderException("SRFax API returned HTTP " + statusCode + 
                            ": " + response.getStatusLine().getReasonPhrase());
                }

                // Validate entity is not null
                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    throw new FaxProviderException("SRFax API returned null response entity");
                }

                String payload = EntityUtils.toString(entity);
                return objectMapper.readTree(payload);
            }
        } catch (IOException e) {
            throw new FaxProviderException("SRFax API communication failure", e, FaxProviderException.isTransientNetworkCause(e));
        }
    }

    /**
     * Validates that SRFax credentials are configured and non-empty.
     * Called before any API operation to fail fast with a clear message.
     */
    private void validateCredentials(FaxConfig faxConfig) throws FaxProviderException {
        if (faxConfig.getFaxUser() == null || faxConfig.getFaxUser().trim().isEmpty()) {
            throw new FaxProviderException("SRFax username (faxUser) is not configured for this fax account");
        }
        if (faxConfig.getFaxPasswd() == null || faxConfig.getFaxPasswd().trim().isEmpty()) {
            throw new FaxProviderException("SRFax password is not configured for this fax account");
        }
    }

    /**
     * Creates the standard SRFax authentication parameters (access_id, access_pwd) from fax configuration.
     */
    private List<NameValuePair> createAuthParams(FaxConfig faxConfig) {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("access_id", faxConfig.getFaxUser()));
        params.add(new BasicNameValuePair("access_pwd", faxConfig.getFaxPasswd()));
        params.add(new BasicNameValuePair("sResponseFormat", "JSON"));
        return params;
    }

    /**
     * Ensures provider response indicates success.
     */
    private void ensureSuccess(JsonNode root, String errorMessage) throws FaxProviderException {
        // Per SRFax spec, Status is always at the top level of the JSON response.
        String status = textAt(root, "Status");

        if (status == null) {
            // Check if Result contains data despite missing Status
            JsonNode result = nodeAt(root, "Result");
            if (result == null || result.isMissingNode() || result.isNull()) {
                throw new FaxProviderException(errorMessage +
                        ": SRFax response missing both Status and Result fields - " +
                        "response may be malformed or indicate API version mismatch");
            }
            // Fail closed: Status field is expected in all SRFax responses.
            // If the API changes shape, we should detect it immediately rather than
            // silently processing potentially malformed data.
            throw new FaxProviderException(errorMessage +
                    ": SRFax response missing expected Status field. " +
                    "Result data is present but cannot be validated without Status. " +
                    "This may indicate an SRFax API version change.");
        }

        String normalized = status.trim().toLowerCase();
        if (normalized.contains("fail") || normalized.contains("error") || "0".equals(normalized)) {
            // Per SRFax spec, on failure the Result field is a flat string with the error reason.
            String message = textAt(root, "Result");
            throw new FaxProviderException(errorMessage + (message == null ? "" : (": " + message)));
        }

        // Warn on unrecognized status to detect SRFax API changes early
        if (!"success".equalsIgnoreCase(normalized) && !"1".equals(normalized)) {
            logger.warn("SRFax returned unrecognized status '{}' for operation: {} - treating as success", status, errorMessage);
        }
    }

    /**
     * Reads a nested text value from a JSON object path.
     */
    private String textAt(JsonNode root, String... path) {
        JsonNode node = nodeAt(root, path);
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    /**
     * Reads a nested JSON node from a path.
     */
    private JsonNode nodeAt(JsonNode root, String... path) {
        JsonNode cursor = root;
        for (String key : path) {
            if (cursor == null || cursor.isMissingNode()) {
                return null;
            }
            cursor = cursor.path(key);
        }
        return cursor;
    }

    /**
     * Returns first available text value from a list of candidate keys.
     */
    private String textOrDefault(JsonNode root, String key1, String key2, String fallback) {
        String v1 = textAt(root, key1);
        if (v1 != null && !v1.isEmpty()) {
            return v1;
        }
        String v2 = textAt(root, key2);
        if (v2 != null && !v2.isEmpty()) {
            return v2;
        }
        return fallback;
    }

    private String textOrDefault(JsonNode root, String key1, String key2, String key3, String fallback) {
        String v1 = textAt(root, key1);
        if (v1 != null && !v1.isEmpty()) {
            return v1;
        }
        String v2 = textAt(root, key2);
        if (v2 != null && !v2.isEmpty()) {
            return v2;
        }
        String v3 = textAt(root, key3);
        if (v3 != null && !v3.isEmpty()) {
            return v3;
        }
        return fallback;
    }
}
