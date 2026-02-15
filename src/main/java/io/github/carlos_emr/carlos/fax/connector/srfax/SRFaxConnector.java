/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * Copyright (c) 2012-2018. CloudPractice Inc. All Rights Reserved.
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
 * SRFax integration ported to CARLOS EMR from JunoEMR (2026).
 */
package io.github.carlos_emr.carlos.fax.connector.srfax;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.fax.connector.FaxConnector;
import io.github.carlos_emr.carlos.fax.connector.FaxInboundResult;
import io.github.carlos_emr.carlos.fax.connector.FaxIntegrationType;
import io.github.carlos_emr.carlos.fax.connector.FaxSendResult;
import io.github.carlos_emr.carlos.fax.connector.FaxStatusCheckResult;
import io.github.carlos_emr.carlos.fax.connector.srfax.result.GetFaxInboxResult;
import io.github.carlos_emr.carlos.fax.connector.srfax.result.GetFaxStatusResult;
import io.github.carlos_emr.carlos.fax.connector.srfax.resultWrapper.ListWrapper;
import io.github.carlos_emr.carlos.fax.connector.srfax.resultWrapper.SingleWrapper;
import io.github.carlos_emr.carlos.fax.exception.FaxApiConnectionException;
import io.github.carlos_emr.carlos.fax.exception.FaxApiValidationException;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.OscarProperties;
import org.apache.logging.log4j.Logger;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link FaxConnector} implementation that communicates directly with the SRFax cloud API.
 * <p>
 * This connector eliminates the need for an external fax gateway server by making
 * direct HTTPS calls to the SRFax service. Credentials (loginId/loginPassword)
 * are stored in the {@link FaxConfig} entity's faxUser/faxPasswd fields.
 * <p>
 * Ported from JunoEMR's CloudPractice fax module and adapted to work with
 * CARLOS EMR's existing FaxConfig/FaxJob data model.
 *
 * @since 2026-02-09
 */
public class SRFaxConnector implements FaxConnector {

    /** Integration type constant registered with {@link io.github.carlos_emr.carlos.fax.connector.FaxConnectorFactory}. */
    public static final String INTEGRATION_TYPE = FaxIntegrationType.SRFAX;

    /** Property key for configuring the fax inbox polling lookback window in days. */
    private static final String FAX_POLL_WINDOW_DAYS_KEY = "faxPollWindowDays";
    /** Default lookback window in days for polling the fax inbox. */
    private static final int DEFAULT_POLL_WINDOW_DAYS = 5;

    private static final Logger logger = MiscUtils.getLogger();
    /** Date formatter matching the SRFax API date format (yyyyMMdd). */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern(SRFaxApiConnector.DATE_FORMAT);

    /**
     * {@inheritDoc}
     * <p>
     * Queues the fax document with SRFax via the {@link SRFaxApiConnector#queueFax} API.
     * The document content must already be base64-encoded in the FaxJob entity.
     * Connection errors return WAITING status (transient), validation errors return ERROR (permanent).
     */
    @Override
    public FaxSendResult sendFax(FaxConfig faxConfig, FaxJob faxJob) {
        try {
            SRFaxApiConnector api = createApiConnector(faxConfig);

            String coverLetterOption = null;
            Map<String, String> fileMap = new HashMap<>(1);

            String fileName = faxJob.getFile_name();
            if (fileName == null || fileName.isEmpty()) {
                fileName = "fax_document.pdf";
            }
            fileMap.put(fileName, faxJob.getDocument());

            SingleWrapper<Integer> result = api.queueFax(
                    faxConfig.getFaxNumber(),
                    faxConfig.getSenderEmail(),
                    faxJob.getDestination(),
                    fileMap,
                    coverLetterOption);

            if (result != null && result.isSuccess()) {
                Integer resultValue = result.getResult();
                if (resultValue == null) {
                    logger.warn("SRFax returned success but null result");
                    return new FaxSendResult(false, 0, FaxJob.STATUS.ERROR, "SRFax returned null job ID");
                }
                long externalId = resultValue.longValue();
                logger.info("SRFax send success, externalId={}", externalId);
                return new FaxSendResult(true, externalId, FaxJob.STATUS.SENT, "Sending via SRFax");
            } else {
                String errorMsg = (result != null) ? result.getError() : "No response";
                logger.warn("SRFax send failure: {}", errorMsg);
                return new FaxSendResult(false, 0, FaxJob.STATUS.ERROR, errorMsg);
            }
        } catch (FaxApiConnectionException e) {
            logger.warn("SRFax connection failure: {}", e.getMessage());
            return new FaxSendResult(false, 0, FaxJob.STATUS.WAITING, e.getUserFriendlyMessage());
        } catch (FaxApiValidationException e) {
            logger.warn("SRFax validation failure: {}", e.getMessage());
            return new FaxSendResult(false, 0, FaxJob.STATUS.ERROR, e.getUserFriendlyMessage());
        } catch (Exception e) {
            logger.error("Unexpected error sending fax via SRFax", e);
            return new FaxSendResult(false, 0, FaxJob.STATUS.ERROR, "Unexpected error");
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Polls the SRFax inbox for unread faxes received in the configured lookback window
     * (default 5 days, configurable via {@code faxPollWindowDays} property).
     * Each inbox item is mapped to a {@link FaxInboundResult} with the received
     * date parsed from the SRFax epoch time field.
     */
    @Override
    public List<FaxInboundResult> pollIncomingFaxes(FaxConfig faxConfig) {
        try {
            SRFaxApiConnector api = createApiConnector(faxConfig);

            // Get configurable lookback window from oscar.properties (default 5 days)
            int pollWindowDays = DEFAULT_POLL_WINDOW_DAYS;
            String pollWindowProperty = OscarProperties.getInstance().getProperty(FAX_POLL_WINDOW_DAYS_KEY);
            if (pollWindowProperty != null && !pollWindowProperty.trim().isEmpty()) {
                try {
                    pollWindowDays = Integer.parseInt(pollWindowProperty.trim());
                    if (pollWindowDays < 1) {
                        logger.warn("Invalid faxPollWindowDays value '{}', using default {}", pollWindowProperty, DEFAULT_POLL_WINDOW_DAYS);
                        pollWindowDays = DEFAULT_POLL_WINDOW_DAYS;
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Could not parse faxPollWindowDays '{}', using default {}", pollWindowProperty, DEFAULT_POLL_WINDOW_DAYS);
                }
            }

            // Query for unread faxes from N days ago through today
            String startDate = LocalDate.now().minusDays(pollWindowDays).format(DATE_FMT);
            String endDate = LocalDate.now().format(DATE_FMT);

            ListWrapper<GetFaxInboxResult> listResult = api.getFaxInbox(
                    SRFaxApiConnector.PERIOD_RANGE,
                    startDate,
                    endDate,
                    SRFaxApiConnector.VIEWED_STATUS_UNREAD,
                    null);

            if (listResult != null && listResult.isSuccess() && listResult.getResult() != null) {
                List<FaxInboundResult> results = new ArrayList<>();
                for (GetFaxInboxResult inboxItem : listResult.getResult()) {
                    FaxInboundResult fir = new FaxInboundResult();
                    fir.setExternalReference(inboxItem.getDetailsId());
                    fir.setFileName(inboxItem.getFileName());
                    fir.setCallerNumber(inboxItem.getCallerId());
                    // Parse the actual received date from the SRFax epoch time field
                    Date receivedDate = null;
                    if (inboxItem.getEpochTime() != null && !inboxItem.getEpochTime().isEmpty()) {
                        try {
                            long epoch = Long.parseLong(inboxItem.getEpochTime());
                            receivedDate = new Date(epoch * 1000L);
                        } catch (NumberFormatException nfe) {
                            logger.warn("Could not parse epoch time '{}' for fax {}", inboxItem.getEpochTime(), inboxItem.getFileName());
                        }
                    }
                    fir.setReceivedDate(receivedDate);

                    try {
                        fir.setPageCount(Integer.parseInt(inboxItem.getPages()));
                    } catch (NumberFormatException e) {
                        fir.setPageCount(0);
                    }

                    fir.setHasError(false);
                    results.add(fir);
                }
                return results;
            } else {
                String errorMsg = (listResult != null) ? listResult.getError() : "No response";
                logger.warn("SRFax inbox poll failure: {}", errorMsg);
                return Collections.emptyList();
            }
        } catch (FaxApiConnectionException e) {
            logger.warn("SRFax connection error during poll: {}", e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            logger.error("Unexpected error polling SRFax inbox", e);
            return Collections.emptyList();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Downloads the fax content as a base64-encoded PDF string via the SRFax Retrieve_Fax API.
     */
    @Override
    public String downloadFax(FaxConfig faxConfig, String faxReference) {
        try {
            SRFaxApiConnector api = createApiConnector(faxConfig);

            SingleWrapper<String> result = api.retrieveFax(
                    null,
                    faxReference,
                    SRFaxApiConnector.RETRIEVE_DIRECTION_IN);

            if (result != null && result.isSuccess()) {
                return result.getResult();
            } else {
                String errorMsg = (result != null) ? result.getError() : "No response";
                logger.warn("SRFax download failure: {}", errorMsg);
                return null;
            }
        } catch (Exception e) {
            logger.error("Error downloading fax via SRFax", e);
            return null;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * SRFax does not support permanent deletion of individual faxes from the inbox.
     * Instead, faxes are marked as read via {@link #markFaxAsRead(FaxConfig, String)}
     * to prevent them from appearing in subsequent unread inbox polls.
     * This method always returns {@code true} as a no-op.
     */
    @Override
    public boolean deleteFax(FaxConfig faxConfig, String faxReference) {
        // SRFax marks faxes as read rather than deleting them
        // The mark-as-read call is handled by markFaxAsRead()
        return true;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Queries the SRFax Get_FaxStatus API and maps the remote status string
     * ("Sent", "Failed", "In Progress") to the corresponding {@link FaxJob.STATUS} enum value.
     */
    @Override
    public FaxStatusCheckResult checkFaxStatus(FaxConfig faxConfig, long externalJobId) {
        try {
            SRFaxApiConnector api = createApiConnector(faxConfig);

            SingleWrapper<GetFaxStatusResult> result = api.getFaxStatus(String.valueOf(externalJobId));

            if (result != null && result.isSuccess()) {
                GetFaxStatusResult statusResult = result.getResult();
                if (statusResult == null) {
                    logger.warn("SRFax returned success but null status result for job {}", externalJobId);
                    return new FaxStatusCheckResult(false, null, "SRFax returned null status");
                }
                String remoteSentStatus = statusResult.getSentStatus();

                // Map SRFax status strings to CARLOS FaxJob.STATUS enum
                FaxJob.STATUS mappedStatus;
                if (SRFaxApiConnector.RESPONSE_STATUS_SENT.equalsIgnoreCase(remoteSentStatus)) {
                    mappedStatus = FaxJob.STATUS.COMPLETE;
                } else if (SRFaxApiConnector.RESPONSE_STATUS_FAILED.equalsIgnoreCase(remoteSentStatus)) {
                    mappedStatus = FaxJob.STATUS.ERROR;
                } else {
                    mappedStatus = FaxJob.STATUS.SENT;
                }

                String statusMsg = statusResult.getErrorCode();
                if (statusMsg == null || statusMsg.isEmpty()) {
                    statusMsg = remoteSentStatus;
                }

                return new FaxStatusCheckResult(true, mappedStatus, statusMsg);
            } else {
                String errorMsg = (result != null) ? result.getError() : "No response";
                logger.warn("SRFax status check failure: {}", errorMsg);
                return new FaxStatusCheckResult(false, null, errorMsg);
            }
        } catch (Exception e) {
            logger.error("Error checking fax status via SRFax", e);
            return new FaxStatusCheckResult(false, null, e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Uses the SRFax Update_Viewed_Status API to mark the fax as read,
     * preventing it from appearing in subsequent inbox polls.
     */
    @Override
    public void markFaxAsRead(FaxConfig faxConfig, String faxReference) {
        try {
            SRFaxApiConnector api = createApiConnector(faxConfig);
            SingleWrapper<String> result = api.updateViewedStatus(
                    null,
                    faxReference,
                    SRFaxApiConnector.RETRIEVE_DIRECTION_IN,
                    SRFaxApiConnector.MARK_AS_READ);

            if (result == null || !result.isSuccess()) {
                String errorMsg = (result != null) ? result.getError() : "No response";
                logger.error("Failed to mark fax({}) as read: {}", faxReference, errorMsg);
            }
        } catch (Exception e) {
            logger.error("Error marking fax as read via SRFax", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getIntegrationType() {
        return INTEGRATION_TYPE;
    }

    /**
     * Creates a new SRFaxApiConnector using the fax account credentials from the FaxConfig.
     *
     * @param faxConfig FaxConfig the fax account configuration containing SRFax login credentials
     * @return SRFaxApiConnector a new API connector instance
     */
    private SRFaxApiConnector createApiConnector(FaxConfig faxConfig) {
        return new SRFaxApiConnector(faxConfig.getFaxUser(), faxConfig.getFaxPasswd());
    }
}
