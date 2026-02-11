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
import org.apache.http.NameValuePair;
import org.apache.logging.log4j.Logger;
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
        try {
            if (Files.exists(filePath) && Files.isReadable(filePath)) {
                faxJob.setDocument(Base64Utility.encode(Files.readAllBytes(filePath)));
            }

            if (faxJob.getDocument() == null || faxJob.getDocument().isEmpty()) {
                throw new FaxProviderException("Fatal error locating document. Not found in filesystem or database backup");
            }

            logger.info("SRFax send requested for fileName={} destination={}", faxJob.getFile_name(), faxJob.getDestination());

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
            logger.info("SRFax send queued for fileName={} providerJobId={}", faxJob.getFile_name(), faxJob.getJobId());
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
     */
    @Override
    public List<FaxJob> listInboundFaxes(FaxConfig faxConfig) throws FaxProviderException {
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
        logger.info("SRFax download requested for fileName={}", fax.getFile_name());
        List<NameValuePair> params = createAuthParams(faxConfig);
        params.add(new BasicNameValuePair("action", ACTION_RETRIEVE_FAX));
        params.add(new BasicNameValuePair("sFaxFileName", fax.getFile_name()));
        // Mark fax as read at source after a successful pull to prevent re-import duplicates.
        params.add(new BasicNameValuePair("sMarkAsRead", "true"));

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
        // Intentionally no-op for SRFax.
        logger.debug("SRFax delete skipped for fileName={} (using unread/read semantics)", fax == null ? null : fax.getFile_name());
    }

    /**
     * Fetches delivery status for outbound fax jobs.
     */
    @Override
    public FaxJob fetchFaxStatus(FaxConfig faxConfig, FaxJob faxJob) throws FaxProviderException {
        logger.debug("SRFax status requested for providerJobId={}", faxJob.getJobId());
        List<NameValuePair> params = createAuthParams(faxConfig);
        params.add(new BasicNameValuePair("action", ACTION_GET_STATUS));
        params.add(new BasicNameValuePair("sFaxId", String.valueOf(faxJob.getJobId())));

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
     */
    private JsonNode postForm(String endpoint, List<NameValuePair> params) throws FaxProviderException {
        HttpPost httpPost = new HttpPost(endpoint);
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            httpPost.setEntity(new UrlEncodedFormEntity(params));

            try (CloseableHttpResponse response = client.execute(httpPost)) {
                String payload = EntityUtils.toString(response.getEntity());
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
