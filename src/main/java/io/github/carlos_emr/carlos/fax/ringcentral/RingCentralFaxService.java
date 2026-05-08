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

    @Override
    public FaxJob sendFax(FaxConfig faxConfig, FaxJob faxJob, Path filePath) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        validateSendRequest(faxJob);

        try {
            byte[] document = resolveDocument(faxJob, filePath);
            String accessToken = authService.getAccessToken(faxConfig, apiConnector);
            RingCentralResponse.Message response = apiConnector.sendFax(accessToken,
                    faxConfig.getRingCentralAccountId(), faxConfig.getRingCentralExtensionId(),
                    faxJob.getDestination(), document, faxJob.getFile_name());

            FaxJob result = new FaxJob();
            result.setJobId(parseMessageId(response.getId()));
            result.setStatus(mapStatus(firstNonBlank(response.getFaxStatus(), response.getMessageStatus())));
            result.setStatusString(firstNonBlank(response.getFaxStatus(), response.getMessageStatus(), "Queued with RingCentral"));
            faxJob.setDocument(null);
            logger.info("RingCentral send queued providerJobId={}", result.getJobId());
            return result;
        } catch (IOException e) {
            throw new RingCentralException("Failed to read fax document file: " + e.getMessage(), e);
        }
    }

    @Override
    public List<FaxJob> listInboundFaxes(FaxConfig faxConfig) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        String accessToken = authService.getAccessToken(faxConfig, apiConnector);
        RingCentralResponse.MessageList response = apiConnector.getInboundFaxes(accessToken,
                faxConfig.getRingCentralAccountId(), faxConfig.getRingCentralExtensionId());

        List<FaxJob> faxes = new ArrayList<>();
        for (RingCentralResponse.Message message : response.getRecords()) {
            if (message == null) {
                logger.warn("Skipping null record in RingCentral fax inbox response");
                continue;
            }
            if (StringUtils.isBlank(message.getId())) {
                logger.warn("Skipping RingCentral fax record with missing message id");
                continue;
            }
            List<RingCentralResponse.Attachment> attachments = message.getAttachments();
            if (attachments == null || attachments.isEmpty()) {
                logger.warn("Skipping RingCentral fax record providerMessageId={} with no attachments",
                        message.getId());
                continue;
            }

            // Emit one FaxJob per attachment so multi-attachment faxes are imported in full.
            // RingCentral marks-as-read at the message level, which is idempotent per message id,
            // so the per-attachment FaxJobs sharing a message id mark-as-read safely.
            String inboundCaller = message.getFrom() == null
                    ? "Unknown"
                    : StringUtils.defaultIfBlank(message.getFrom().phoneNumber(), "Unknown");
            Date stamp = parseCreationTime(message.getCreationTime(), message.getId());
            for (RingCentralResponse.Attachment attachment : attachments) {
                if (attachment == null || StringUtils.isBlank(attachment.id())) {
                    logger.warn("Skipping RingCentral attachment with missing id for providerMessageId={}",
                            message.getId());
                    continue;
                }

                FaxJob faxJob = new FaxJob();
                faxJob.setJobId(parseMessageId(message.getId()));
                faxJob.setFile_name(DownloadReference.format(message.getId(), attachment.id(),
                        attachment.fileName()));
                faxJob.setRecipient(inboundCaller);
                faxJob.setStatus(FaxJob.STATUS.RECEIVED);
                faxJob.setStatusString("Ready for RingCentral download");
                faxJob.setStamp(stamp);
                faxes.add(faxJob);
            }
        }

        logger.info("RingCentral inbox listed unreadCount={}", faxes.size());
        return faxes;
    }

    @Override
    public FaxJob downloadFax(FaxConfig faxConfig, FaxJob fax) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        DownloadReference reference = DownloadReference.parse(fax);
        String accessToken = authService.getAccessToken(faxConfig, apiConnector);
        byte[] content = apiConnector.downloadFax(accessToken, faxConfig.getRingCentralAccountId(),
                faxConfig.getRingCentralExtensionId(), reference.messageId(), reference.attachmentId());

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
        String accessToken = authService.getAccessToken(faxConfig, apiConnector);
        apiConnector.markFaxAsRead(accessToken, faxConfig.getRingCentralAccountId(),
                faxConfig.getRingCentralExtensionId(), reference.messageId());
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

        String accessToken = authService.getAccessToken(faxConfig, apiConnector);
        RingCentralResponse.Message response = apiConnector.getFaxStatus(accessToken,
                faxConfig.getRingCentralAccountId(), faxConfig.getRingCentralExtensionId(),
                String.valueOf(faxJob.getJobId()));

        FaxJob updated = new FaxJob(faxJob);
        String providerStatus = firstNonBlank(response.getFaxStatus(), response.getMessageStatus());
        updated.setStatus(mapStatus(providerStatus));
        updated.setStatusString(StringUtils.defaultIfBlank(providerStatus, "Unknown RingCentral status"));
        return updated;
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
        if (!PHONE_NUMBER_PATTERN.matcher(faxJob.getDestination().trim()).matches()) {
            throw new RingCentralException(
                    "Fax destination is not a valid phone number; expected digits with optional + and separators");
        }
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

    private Date parseCreationTime(String creationTime, String providerMessageId) {
        if (StringUtils.isBlank(creationTime)) {
            return new Date();
        }
        try {
            return Date.from(OffsetDateTime.parse(creationTime).toInstant());
        } catch (DateTimeParseException e) {
            // Falling back to current time keeps the import flow alive, but the fake stamp must be
            // visible so operators can spot RingCentral schema drift. providerMessageId is a vendor
            // surrogate, not PHI; we deliberately do NOT log the raw creationTime to keep the log
            // line short (operators can fetch the message via providerMessageId if needed).
            logger.warn("Could not parse RingCentral creationTime for providerMessageId={} - falling back to current time",
                    providerMessageId);
            return new Date();
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
