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
import io.github.carlos_emr.carlos.fax.connector.FaxSendResult;
import io.github.carlos_emr.carlos.fax.connector.FaxStatusCheckResult;
import io.github.carlos_emr.carlos.fax.connector.srfax.result.GetFaxInboxResult;
import io.github.carlos_emr.carlos.fax.connector.srfax.result.GetFaxStatusResult;
import io.github.carlos_emr.carlos.fax.connector.srfax.resultWrapper.ListWrapper;
import io.github.carlos_emr.carlos.fax.connector.srfax.resultWrapper.SingleWrapper;
import io.github.carlos_emr.carlos.fax.exception.FaxApiConnectionException;
import io.github.carlos_emr.carlos.fax.exception.FaxApiValidationException;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
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

    public static final String INTEGRATION_TYPE = "SRFAX";

    private static final Logger logger = MiscUtils.getLogger();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern(SRFaxApiConnector.DATE_FORMAT);

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
                long externalId = result.getResult().longValue();
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

    @Override
    public List<FaxInboundResult> pollIncomingFaxes(FaxConfig faxConfig) {
        try {
            SRFaxApiConnector api = createApiConnector(faxConfig);

            String startDate = LocalDate.now().minusDays(1).format(DATE_FMT);
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
                    fir.setReceivedDate(new Date());

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

    @Override
    public boolean deleteFax(FaxConfig faxConfig, String faxReference) {
        // SRFax marks faxes as read rather than deleting them
        // The mark-as-read call is handled by markFaxAsRead()
        return true;
    }

    @Override
    public FaxStatusCheckResult checkFaxStatus(FaxConfig faxConfig, long externalJobId) {
        try {
            SRFaxApiConnector api = createApiConnector(faxConfig);

            SingleWrapper<GetFaxStatusResult> result = api.getFaxStatus(String.valueOf(externalJobId));

            if (result != null && result.isSuccess()) {
                GetFaxStatusResult statusResult = result.getResult();
                String remoteSentStatus = statusResult.getSentStatus();

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

    @Override
    public String getIntegrationType() {
        return INTEGRATION_TYPE;
    }

    private SRFaxApiConnector createApiConnector(FaxConfig faxConfig) {
        return new SRFaxApiConnector(faxConfig.getFaxUser(), faxConfig.getFaxPasswd());
    }
}
