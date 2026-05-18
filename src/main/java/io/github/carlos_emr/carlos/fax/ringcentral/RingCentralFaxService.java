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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClient;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderException;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * RingCentral implementation of CARLOS EMR's provider-agnostic fax transport.
 *
 * @since 2026-05-05
 */
@Component
public class RingCentralFaxService implements FaxProviderClient {

    private static final Logger logger = MiscUtils.getLogger();

    // Lenient phone-number sanity check: digits, optional + prefix, plus separators commonly
    // accepted by RingCentral (spaces, dashes, parentheses, dots). RingCentral does its own
    // E.164 normalisation server-side; this only catches the obvious typo class so the operator
    // sees a useful error instead of a generic HTTP 400.
    private static final Pattern PHONE_NUMBER_PATTERN =
            Pattern.compile("^\\+?[0-9 .()\\-]{4,32}$");

    private final RingCentralApiConnector apiConnector;
    private final RingCentralAuthService authService;

    @Autowired
    public RingCentralFaxService(RingCentralApiConnector apiConnector, RingCentralAuthService authService) {
        this.apiConnector = apiConnector;
        this.authService = authService;
    }

    @Override
    public FaxConfig.ProviderType getProviderType() {
        return FaxConfig.ProviderType.RINGCENTRAL;
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Side effect:</strong> on every code path (success, validation throw, IO failure,
     * RingCentral failure), the input {@code faxJob}'s {@code document} field is cleared (set to
     * {@code null}) so the base64 payload can be garbage-collected promptly rather than retained
     * on the FaxJob through the rest of the outbound pipeline. Callers that need the payload
     * after send must snapshot it before this call.</p>
     */
    @Override
    public FaxJob sendFax(FaxConfig faxConfig, FaxJob faxJob, Path filePath) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        validateSendRequest(faxJob);

        try {
            byte[] document = resolveDocument(faxJob, filePath);
            RingCentralResponse.Message response = withTokenRefresh(faxConfig, account ->
                    apiConnector.sendFax(account,
                            faxJob.getDestination(), document, faxJob.getFile_name()));

            Long jobId = parseMessageId(response.getId());
            if (jobId == null) {
                // Without a numeric job id we can't track status later (fetchFaxStatus throws on
                // null). Surface this immediately on send rather than queueing a FaxJob whose
                // status checks will fail forever. RingCentral's documented contract is numeric ids.
                throw new RingCentralException(
                        "RingCentral accepted send but returned non-numeric or missing message id '"
                        + response.getId() + "' - status tracking unavailable");
            }
            FaxJob result = new FaxJob();
            result.setJobId(jobId);
            result.setStatus(mapStatus(firstNonBlank(response.getFaxStatus(), response.getMessageStatus())));
            result.setStatusString(firstNonBlank(response.getFaxStatus(), response.getMessageStatus(), "Queued with RingCentral"));
            logger.info("RingCentral send queued providerJobId={}", result.getJobId());
            return result;
        } catch (IOException e) {
            throw new RingCentralException("Failed to read fax document file: " + e.getMessage(), e);
        } finally {
            // Clear the in-memory base64 payload regardless of outcome. On the failure paths the
            // caller's retry/failure handling no longer carries the document field, bounding heap
            // retention of PHI to the duration of this single send call.
            faxJob.setDocument(null);
        }
    }

    @Override
    public List<FaxJob> listInboundFaxes(FaxConfig faxConfig) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        RingCentralResponse.MessageList response = withTokenRefresh(faxConfig,
                apiConnector::getInboundFaxes);

        List<FaxJob> faxes = new ArrayList<>();
        List<RingCentralResponse.Message> records = response.getRecords();
        int totalRecords = records.size();
        int skippedRecords = 0;
        for (RingCentralResponse.Message message : records) {
            if (message == null) {
                logger.warn("Skipping null record in RingCentral fax inbox response");
                skippedRecords++;
                continue;
            }
            if (StringUtils.isBlank(message.getId())) {
                logger.warn("Skipping RingCentral fax record with missing message id");
                skippedRecords++;
                continue;
            }
            Long parsedMessageId = parseMessageId(message.getId());
            if (parsedMessageId == null) {
                // The same parser feeds FaxJob.jobId below; assigning null silently would create
                // a half-imported job whose status-check path throws forever. Treat non-numeric
                // ids as a skip so the inbox progresses and the operator sees a single WARN per
                // schema-drift record. parseMessageId already logs the offending value at WARN.
                skippedRecords++;
                continue;
            }
            List<RingCentralResponse.Attachment> attachments = message.getAttachments();
            if (attachments.isEmpty()) {
                logger.warn("Skipping RingCentral fax record providerMessageId={} with no attachments",
                        message.getId());
                skippedRecords++;
                continue;
            }

            // Emit one FaxJob per attachment so multi-attachment faxes are imported in full.
            // RingCentral marks-as-read at the message level, which is idempotent per message id,
            // so the per-attachment FaxJobs sharing a message id mark-as-read safely.
            String inboundCaller = message.getFrom() == null
                    ? "Unknown"
                    : StringUtils.defaultIfBlank(message.getFrom().phoneNumber(), "Unknown");
            Date stamp;
            try {
                stamp = parseCreationTime(message.getCreationTime(), message.getId());
            } catch (RingCentralException e) {
                // Skip the offending message; the rest of the inbox proceeds. The message stays
                // unread on RingCentral so it'll come back next poll — operator can fix the
                // upstream record by then.
                logger.warn("Skipping RingCentral fax record providerMessageId={} due to unparseable creationTime: {}",
                        message.getId(), e.getMessage());
                skippedRecords++;
                continue;
            }
            for (RingCentralResponse.Attachment attachment : attachments) {
                if (attachment == null || StringUtils.isBlank(attachment.id())) {
                    logger.warn("Skipping RingCentral attachment with missing id for providerMessageId={}",
                            message.getId());
                    continue;
                }

                FaxJob faxJob = new FaxJob();
                faxJob.setJobId(parsedMessageId);
                faxJob.setFile_name(DownloadReference.format(message.getId(), attachment.id(),
                        attachment.fileName()));
                faxJob.setRecipient(inboundCaller);
                faxJob.setStatus(FaxJob.STATUS.RECEIVED);
                faxJob.setStatusString("Ready for RingCentral download");
                faxJob.setStamp(stamp);
                faxes.add(faxJob);
            }
        }

        if (skippedRecords > 0) {
            // Aggregate signal so chronic schema drift stands out — individual per-record warns
            // get lost in operator logs once an inbox fills up. Counting at the message level (not
            // attachment) since attachment-level skips are within otherwise-valid records.
            logger.warn("RingCentral inbox produced {} unread fax(es) but skipped {} of {} records "
                    + "due to schema/validation issues - check earlier WARN lines for details",
                    faxes.size(), skippedRecords, totalRecords);
        } else {
            logger.info("RingCentral inbox listed unreadCount={}", faxes.size());
        }
        return faxes;
    }

    @Override
    public FaxJob downloadFax(FaxConfig faxConfig, FaxJob fax) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        DownloadReference reference = DownloadReference.parse(fax);
        byte[] content = withTokenRefresh(faxConfig, account ->
                apiConnector.downloadFax(account, reference.messageId(), reference.attachmentId()));

        FaxJob downloaded = new FaxJob(fax);
        downloaded.setDocument(Base64.getEncoder().encodeToString(content));
        downloaded.setStatus(FaxJob.STATUS.RECEIVED);
        downloaded.setStatusString("Downloaded from RingCentral");
        return downloaded;
    }

    @Override
    public void markFaxAsRead(FaxConfig faxConfig, FaxJob fax) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        DownloadReference reference = DownloadReference.parse(fax);
        withTokenRefresh(faxConfig, account -> {
            apiConnector.markFaxAsRead(account, reference.messageId());
            return null;
        });
        logger.info("RingCentral fax marked as read: providerMessageId={} localFaxId={}",
                reference.messageId(), fax.getId());
    }

    @Override
    public void deleteFax(FaxConfig faxConfig, FaxJob fax) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        // RingCentral uses unread/read semantics for duplicate prevention (matching SRFax). Acknowledge
        // happens via markFaxAsRead; this method is a contractual no-op so the FaxImporter cleanup loop
        // can call it uniformly across providers. See docs/fax-provider-configuration-and-ux.md.
        logger.debug("RingCentral delete skipped (using unread/read semantics)");
    }

    @Override
    public FaxJob fetchFaxStatus(FaxConfig faxConfig, FaxJob faxJob) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        if (faxJob.getJobId() == null) {
            throw new RingCentralException("RingCentral status check requires a provider message id");
        }

        RingCentralResponse.Message response = withTokenRefresh(faxConfig, account ->
                apiConnector.getFaxStatus(account, String.valueOf(faxJob.getJobId())));

        FaxJob updated = new FaxJob(faxJob);
        String providerStatus = firstNonBlank(response.getFaxStatus(), response.getMessageStatus());
        FaxJob.STATUS mapped = mapStatus(providerStatus);
        updated.setStatus(mapped);
        if (StringUtils.isBlank(providerStatus)) {
            updated.setStatusString("Unknown RingCentral status (no value returned)");
        } else if (mapped == FaxJob.STATUS.UNKNOWN) {
            // Distinguish "vendor returned an unmapped keyword" from "vendor said nothing" — both
            // map to STATUS.UNKNOWN, but the operator-facing string should make the difference
            // visible so a chronic schema gap stands out from a transient empty response.
            updated.setStatusString("Unknown RingCentral status (vendor: " + providerStatus + ")");
        } else {
            updated.setStatusString(providerStatus);
        }
        return updated;
    }

    /**
     * Fetches an access token, runs the connector call, and on HTTP 401 evicts the cached token
     * and retries the call exactly once with a freshly-authenticated token. A second 401 (or any
     * non-401 failure) propagates unchanged. This is the recovery path documented on
     * {@link RingCentralAuthService#invalidateToken}: without it a server-side credential rotation
     * would leave every call failing for the cache lifetime.
     */
    private <T> T withTokenRefresh(FaxConfig faxConfig, AccountedCall<T> call) throws RingCentralException {
        String accessToken = authService.getAccessToken(faxConfig, apiConnector);
        try {
            return call.execute(buildAccount(faxConfig, accessToken));
        } catch (RingCentralException e) {
            if (!isUnauthorized(e)) {
                throw e;
            }
            logger.warn("RingCentral returned 401 for faxConfigId={} - invalidating cached token and retrying once",
                    faxConfig.getId());
            authService.invalidateToken(faxConfig);
            String refreshed = authService.getAccessToken(faxConfig, apiConnector);
            try {
                return call.execute(buildAccount(faxConfig, refreshed));
            } catch (RingCentralException retryFailure) {
                if (isUnauthorized(retryFailure)) {
                    // Re-wrap the second 401 with explicit context so operator logs distinguish
                    // "stale cached token, refresh recovered" from "credentials genuinely revoked
                    // at provider". The original exception is chained for stack-trace correlation.
                    throw new RingCentralException(
                            "RingCentral returned 401 even after fresh authentication for faxConfigId="
                                    + faxConfig.getId()
                                    + " - credentials likely revoked at provider; re-issue JWT and re-save in admin",
                            retryFailure);
                }
                throw retryFailure;
            }
        }
    }

    private static RingCentralAccount buildAccount(FaxConfig faxConfig, String accessToken) {
        // RingCentral's documented "~" sentinel resolves to the access-token's current account /
        // extension. FaxConfig stores empty strings for un-set values, so substitute "~" before
        // constructing the record (which rejects blanks) — preserves the existing "leave blank
        // for self" admin UX without weakening RingCentralAccount's invariants.
        return new RingCentralAccount(accessToken,
                StringUtils.defaultIfBlank(faxConfig.getRingCentralAccountId(), "~"),
                StringUtils.defaultIfBlank(faxConfig.getRingCentralExtensionId(), "~"));
    }

    private static boolean isUnauthorized(RingCentralException e) {
        return e.getHttpStatus() != null && e.getHttpStatus() == 401;
    }

    @FunctionalInterface
    private interface AccountedCall<T> {
        T execute(RingCentralAccount account) throws RingCentralException;
    }

    FaxJob.STATUS mapStatus(String providerStatus) {
        if (providerStatus == null) {
            return FaxJob.STATUS.UNKNOWN;
        }

        // Failure/cancel checks must precede in-progress and complete checks because RingCentral
        // statuses such as "SendingFailed" and "undelivered" share substrings ("sending", "delivered")
        // with non-error states.
        String normalized = providerStatus.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return FaxJob.STATUS.UNKNOWN;
        }
        if (normalized.contains("fail") || normalized.contains("error") || normalized.contains("rejected")
                || normalized.contains("undeliver")) {
            return FaxJob.STATUS.ERROR;
        }
        if (normalized.contains("cancel")) {
            return FaxJob.STATUS.CANCELLED;
        }
        if (normalized.contains("queue") || normalized.contains("sending") || normalized.contains("progress")
                || normalized.contains("attempt")) {
            return FaxJob.STATUS.SENT;
        }
        if (normalized.contains("sent") || normalized.contains("delivered") || normalized.contains("complete")
                || normalized.contains("received")) {
            return FaxJob.STATUS.COMPLETE;
        }
        // Surface unmapped vendor keywords so a new RingCentral status doesn't silently leave a fax
        // stuck in UNKNOWN with no operator signal. providerStatus is a vendor enum, not PHI.
        logger.warn("Unmapped RingCentral status keyword: '{}' - treating as UNKNOWN", providerStatus);
        return FaxJob.STATUS.UNKNOWN;
    }

    void validateSendRequest(FaxJob faxJob) throws RingCentralException {
        if (faxJob == null) {
            throw new RingCentralException("Fax job is required for RingCentral send");
        }
        if (StringUtils.isBlank(faxJob.getDestination())) {
            throw new RingCentralException("Fax destination number is required but was not provided");
        }
        // Trim once and reuse: the regex check is applied to the trimmed value, and the trimmed
        // value is also what gets sent to RingCentral. Without this, leading/trailing whitespace
        // would pass validation but reach the API verbatim and be rejected as malformed E.164.
        String trimmedDestination = faxJob.getDestination().trim();
        if (!PHONE_NUMBER_PATTERN.matcher(trimmedDestination).matches()) {
            throw new RingCentralException(
                    "Fax destination is not a valid phone number; expected digits with optional + and separators");
        }
        faxJob.setDestination(trimmedDestination);
    }

    private byte[] resolveDocument(FaxJob faxJob, Path filePath) throws IOException, RingCentralException {
        if (filePath != null) {
            if (!Files.exists(filePath)) {
                throw new RingCentralException("Fax document file not found");
            }
            if (!Files.isReadable(filePath)) {
                throw new RingCentralException("Fax document file is not readable (check permissions)");
            }
            return Files.readAllBytes(filePath);
        }
        if (StringUtils.isBlank(faxJob.getDocument())) {
            throw new RingCentralException("Fatal error locating document. Not found in filesystem or database backup");
        }
        try {
            return Base64.getMimeDecoder().decode(faxJob.getDocument());
        } catch (IllegalArgumentException e) {
            throw new RingCentralException("Fax document payload is not valid base64", e);
        }
    }

    private Long parseMessageId(String messageId) {
        try {
            return StringUtils.isBlank(messageId) ? null : Long.parseLong(messageId);
        } catch (NumberFormatException e) {
            // Vendor message ids are documented numeric; surface the offending value so operators
            // can correlate the failed status-tracking row with the RingCentral inbox entry.
            // Provider message ids are vendor identifiers, not PHI.
            logger.warn("RingCentral returned non-numeric message id '{}' - status tracking may be unavailable",
                    messageId);
            return null;
        }
    }

    private Date parseCreationTime(String creationTime, String providerMessageId) throws RingCentralException {
        if (StringUtils.isBlank(creationTime)) {
            // No upstream timestamp at all is benign (rare, missing-field shape); keep "now" so
            // the inbox flow proceeds. The far more dangerous case — a non-blank value we can't
            // parse — falls through to the catch and is rejected.
            return new Date();
        }
        try {
            return Date.from(OffsetDateTime.parse(creationTime).toInstant());
        } catch (DateTimeParseException e) {
            // Falling back to "now" would silently fabricate a clinical-record timestamp that
            // later flows into the EDoc. Throw instead so the caller skips the message and the
            // operator can correct it manually. providerMessageId is a vendor surrogate, not PHI.
            throw new RingCentralException(
                    "Could not parse RingCentral creationTime for providerMessageId=" + providerMessageId
                    + " - skipping import to avoid fabricated clinical timestamp", e);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }
}
