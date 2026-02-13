/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * Copyright (c) 2017-2024. Juno EMR. All Rights Reserved.
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
 * Originally written for the Department of Family Medicine, McMaster University.
 * Portions contributed by Juno EMR.
 * Now maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.fax.provider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Fax provider client implementation for direct SRFax API integration.
 *
 * <p>This implementation uses the SRFax HTTP form-based API over a configurable endpoint stored in
 * {@link FaxConfig#getUrl()}. Credentials are mapped from fax config fields as follows:</p>
 * <ul>
 *   <li><strong>sFaxUserName</strong>: {@link FaxConfig#getFaxUser()}</li>
 *   <li><strong>sFaxPassword</strong>: {@link FaxConfig#getFaxPasswd()}</li>
 * </ul>
 *
 * <p>SRFax response payloads are parsed defensively because payload shape may vary by account-level
 * settings (JSON envelope variants and optional fields).</p>
 *
 * @since 2026-02-11
 */
@Component
public class SRFaxProviderClient implements FaxProviderClient {

    private static final String ACTION_QUEUE_FAX = "Queue_Fax";
    private static final String ACTION_GET_INBOX = "Get_Fax_Inbox";
    private static final String ACTION_RETRIEVE_FAX = "Retrieve_Fax";
    private static final String ACTION_GET_STATUS = "Get_Fax_Status";

    private static final Logger logger = MiscUtils.getLogger();

    private final ObjectMapper objectMapper = new ObjectMapper();

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
        if (faxConfig.getProviderType() != FaxConfig.ProviderType.SRFAX) {
            throw new IllegalArgumentException("SRFax client requires SRFAX provider type, but got: " + faxConfig.getProviderType());
        }

        try {
            if (Files.exists(filePath) && Files.isReadable(filePath)) {
                faxJob.setDocument(Base64Utility.encode(Files.readAllBytes(filePath)));
            }

            if (faxJob.getDocument() == null || faxJob.getDocument().isEmpty()) {
                throw new FaxProviderException("Fatal error locating document. Not found in filesystem or database backup");
            }

            // Log at DEBUG level with masked destination to avoid PHI exposure
            if (logger.isDebugEnabled()) {
                String maskedDestination = faxJob.getDestination() != null && faxJob.getDestination().length() > 4
                        ? "***" + faxJob.getDestination().substring(faxJob.getDestination().length() - 4)
                        : "****";
                logger.debug("SRFax send requested for fileName={} destination={}", faxJob.getFile_name(), maskedDestination);
            }

            List<NameValuePair> params = createAuthParams(faxConfig);
            params.add(new BasicNameValuePair("action", ACTION_QUEUE_FAX));
            params.add(new BasicNameValuePair("sCallerID", faxConfig.getFaxNumber()));
            params.add(new BasicNameValuePair("sSenderEmail", faxConfig.getSenderEmail()));
            params.add(new BasicNameValuePair("sFaxType", "SINGLE"));
            params.add(new BasicNameValuePair("sToFaxNumber", faxJob.getDestination()));
            params.add(new BasicNameValuePair("sFileName_1", faxJob.getFile_name()));
            params.add(new BasicNameValuePair("sFileContent_1", faxJob.getDocument()));

            JsonNode root = postForm(faxConfig.getUrl(), params);
            ensureSuccess(root, "Failed to queue fax with SRFax");

            // SRFax typically returns queue/fax identifiers in Result object; map if present.
            String jobId = textAt(root, "Result", "Queue_Fax_ID");
            if (jobId == null) {
                jobId = textAt(root, "Result", "FaxID");
            }

            if (jobId != null) {
                try {
                    faxJob.setJobId(Long.parseLong(jobId));
                } catch (NumberFormatException e) {
                    // Some SRFax IDs are non-numeric; preserve status details if it cannot be normalized.
                    faxJob.setStatusString("SRFax queued with non-numeric id: " + jobId);
                }
            }

            faxJob.setStatus(FaxJob.STATUS.SENT);
            if (logger.isInfoEnabled()) {
                logger.info("SRFax send queued for fileName={} providerJobId={}", faxJob.getFile_name(), faxJob.getJobId());
            }
            if (faxJob.getStatusString() == null || faxJob.getStatusString().isEmpty()) {
                faxJob.setStatusString("Queued with SRFax");
            }
            faxJob.setDocument(null);
            return faxJob;
        } catch (IOException e) {
            throw new FaxProviderException("CANNOT FIND Filepath: " + filePath, e);
        }
    }

    /**
     * Lists inbound fax headers available in SRFax inbox.
     *
     * <p><strong>Duplicate Prevention Strategy:</strong> This method pulls only unread faxes
     * (sIncludeRead=false, sUnreadOnly=true) to avoid re-importing documents. Faxes are marked
     * as read during download (see {@link #downloadFax}). This prevents duplicates without
     * deleting faxes from the SRFax server, allowing the server to retain faxes for
     * compliance and backup purposes.</p>
     *
     * @param faxConfig SRFax account configuration with authentication credentials
     * @return list of unread fax jobs available for import
     * @throws FaxProviderException if authentication fails or API request fails
     */
    @Override
    public List<FaxJob> listInboundFaxes(FaxConfig faxConfig) throws FaxProviderException {
        if (faxConfig.getProviderType() != FaxConfig.ProviderType.SRFAX) {
            throw new IllegalArgumentException("SRFax client requires SRFAX provider type, but got: " + faxConfig.getProviderType());
        }

        List<NameValuePair> params = createAuthParams(faxConfig);
        params.add(new BasicNameValuePair("action", ACTION_GET_INBOX));
        // Pull only unread faxes to avoid duplicates.
        params.add(new BasicNameValuePair("sIncludeRead", "false"));
        params.add(new BasicNameValuePair("sUnreadOnly", "true"));

        JsonNode root = postForm(faxConfig.getUrl(), params);
        ensureSuccess(root, "Failed to fetch SRFax inbox");

        List<FaxJob> result = new ArrayList<>();
        JsonNode rows = nodeAt(root, "Result", "faxes");
        if (rows == null || rows.isMissingNode()) {
            rows = nodeAt(root, "Result");
        }

        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) {
                FaxJob faxJob = new FaxJob();
                faxJob.setFile_name(textOrDefault(row, "FileName", "FaxFileName", "FaxName", String.valueOf(System.currentTimeMillis())));
                faxJob.setRecipient(textOrDefault(row, "CallerID", "FromFaxNumber", "Unknown"));
                faxJob.setStatus(FaxJob.STATUS.RECEIVED);
                faxJob.setStatusString("Ready for SRFax download");
                result.add(faxJob);
            }
        }

        logger.info("SRFax inbox listed unreadCount={}", result.size());
        return result;
    }

    /**
     * Downloads a specific fax document from SRFax.
     */
    @Override
    public FaxJob downloadFax(FaxConfig faxConfig, FaxJob fax) throws FaxProviderException {
        if (faxConfig.getProviderType() != FaxConfig.ProviderType.SRFAX) {
            throw new IllegalArgumentException("SRFax client requires SRFAX provider type, but got: " + faxConfig.getProviderType());
        }

        logger.info("SRFax download requested for fileName={}", fax.getFile_name());
        List<NameValuePair> params = createAuthParams(faxConfig);
        params.add(new BasicNameValuePair("action", ACTION_RETRIEVE_FAX));
        params.add(new BasicNameValuePair("sFaxFileName", fax.getFile_name()));
        params.add(new BasicNameValuePair("sDirection", "IN"));  // Required: "IN" for received faxes, "OUT" for sent
        params.add(new BasicNameValuePair("sFaxFormat", "PDF")); // Explicit format request (API defaults to PDF)
        // Mark fax as read at source after a successful pull to prevent re-import duplicates.
        params.add(new BasicNameValuePair("sMarkasViewed", "Y")); // Correct parameter name and value format

        JsonNode root = postForm(faxConfig.getUrl(), params);
        ensureSuccess(root, "Failed to download SRFax document");

        String base64Doc = textAt(root, "Result", "FileContents");
        if (base64Doc == null) {
            base64Doc = textAt(root, "Result", "DocumentContent");
        }
        if (base64Doc == null || base64Doc.isEmpty()) {
            throw new FaxProviderException("SRFax download response did not include document content");
        }

        FaxJob downloaded = new FaxJob(fax);
        downloaded.setDocument(base64Doc);
        downloaded.setStatus(FaxJob.STATUS.RECEIVED);
        downloaded.setStatusString("Downloaded from SRFax");
        logger.info("SRFax download completed for fileName={}", fax.getFile_name());
        return downloaded;
    }

    /**
     * Acknowledges inbound fax handling.
     *
     * <p>For SRFax we do not delete server-side faxes. Duplicate prevention is handled by
     * pulling unread-only and marking as read during retrieval.</p>
     */
    @Override
    public void deleteFax(FaxConfig faxConfig, FaxJob fax) throws FaxProviderException {
        if (faxConfig.getProviderType() != FaxConfig.ProviderType.SRFAX) {
            throw new IllegalArgumentException("SRFax client requires SRFAX provider type, but got: " + faxConfig.getProviderType());
        }

        // Intentionally no-op for SRFax.
        logger.debug("SRFax delete skipped for fileName={} (using unread/read semantics)", fax == null ? null : fax.getFile_name());
    }

    /**
     * Fetches delivery status for outbound fax jobs.
     */
    @Override
    public FaxJob fetchFaxStatus(FaxConfig faxConfig, FaxJob faxJob) throws FaxProviderException {
        if (faxConfig.getProviderType() != FaxConfig.ProviderType.SRFAX) {
            throw new IllegalArgumentException("SRFax client requires SRFAX provider type, but got: " + faxConfig.getProviderType());
        }

        logger.debug("SRFax status requested for providerJobId={}", faxJob.getJobId());
        List<NameValuePair> params = createAuthParams(faxConfig);
        params.add(new BasicNameValuePair("action", ACTION_GET_STATUS));
        params.add(new BasicNameValuePair("sFaxDetailsID", String.valueOf(faxJob.getJobId()))); // Correct parameter name from Queue_Fax response

        JsonNode root = postForm(faxConfig.getUrl(), params);
        ensureSuccess(root, "Failed to fetch SRFax fax status");

        String providerStatus = textAt(root, "Result", "Status");
        if (providerStatus == null) {
            providerStatus = textAt(root, "Result", "FaxStatus");
        }

        FaxJob updated = new FaxJob(faxJob);
        updated.setStatus(mapStatus(providerStatus));
        updated.setStatusString(providerStatus == null ? "Unknown SRFax status" : providerStatus);
        logger.debug("SRFax status response providerJobId={} status={}", faxJob.getJobId(), updated.getStatusString());
        return updated;
    }

    /**
     * Maps SRFax status text into local {@link FaxJob.STATUS} values.
     */
    /**
     * Maps SRFax provider status strings to internal {@link FaxJob.STATUS} enumeration values.
     *
     * <p><strong>Status Mapping Tolerance:</strong> This method uses fuzzy matching (contains checks)
     * because SRFax status text varies across API versions and account configurations. Status strings
     * are normalized to lowercase for case-insensitive matching. This defensive approach prevents
     * status mapping failures when the SRFax API returns status strings in different formats.</p>
     *
     * <p>Mapping rules:</p>
     * <ul>
     *   <li>SUCCESS/COMPLETE/SENT → {@code COMPLETE}</li>
     *   <li>QUEUE/PROCESSING/PROGRESS/RETRY → {@code SENT} (in-progress)</li>
     *   <li>CANCEL → {@code CANCELLED}</li>
     *   <li>ERROR/FAIL → {@code ERROR}</li>
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

        // Completed statuses
        if (normalized.contains("success") || normalized.contains("complete") || normalized.contains("sent")) {
            return FaxJob.STATUS.COMPLETE;
        }

        // Queue/in-progress statuses
        if (normalized.contains("queue") || normalized.contains("processing") || normalized.contains("progress") || normalized.contains("retry")) {
            return FaxJob.STATUS.SENT;
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
            httpPost.setEntity(new UrlEncodedFormEntity(params));

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
            throw new FaxProviderException("SRFax API communication failure", e);
        }
    }

    /**
     * Creates the standard SRFax auth parameters from fax configuration.
     */
    private List<NameValuePair> createAuthParams(FaxConfig faxConfig) {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("sFaxUserName", faxConfig.getFaxUser()));
        params.add(new BasicNameValuePair("sFaxPassword", faxConfig.getFaxPasswd()));
        params.add(new BasicNameValuePair("sResponseFormat", "JSON"));
        return params;
    }

    /**
     * Ensures provider response indicates success.
     */
    private void ensureSuccess(JsonNode root, String errorMessage) throws FaxProviderException {
        String status = textAt(root, "Status");
        if (status == null) {
            status = textAt(root, "Result", "Status");
        }

        if (status == null) {
            logger.warn("SRFax response missing Status field - response may be malformed or indicate API version mismatch");
            return;
        }

        String normalized = status.trim().toLowerCase();
        if (normalized.contains("fail") || normalized.contains("error") || normalized.equals("0")) {
            String message = textAt(root, "Result", "Result");
            throw new FaxProviderException(errorMessage + (message == null ? "" : (": " + message)));
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
