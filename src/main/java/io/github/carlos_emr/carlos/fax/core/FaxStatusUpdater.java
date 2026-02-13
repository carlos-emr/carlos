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
package io.github.carlos_emr.carlos.fax.core;

import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClient;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClientFactory;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderException;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service responsible for polling remote fax providers for status updates on in-progress outbound faxes.
 *
 * <p>This service tracks the delivery status of faxes that have been submitted to providers but
 * have not yet reached a terminal state (SENT or ERROR). It periodically polls the provider APIs
 * to update local FaxJob records with the latest delivery status, enabling real-time tracking
 * and troubleshooting of fax transmission.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li><strong>In-Progress Polling:</strong> Only queries faxes with provider job IDs (not WAITING/READY)</li>
 *   <li><strong>Multi-Account Support:</strong> Handles status updates across multiple FaxConfig accounts</li>
 *   <li><strong>Provider Abstraction:</strong> Works with any FaxProviderClient (MIDDLEWARE, SRFAX)</li>
 *   <li><strong>Orphaned Fax Detection:</strong> Identifies faxes with deleted FaxConfig accounts</li>
 *   <li><strong>Error Resilience:</strong> Provider failures logged but do not stop processing queue</li>
 *   <li><strong>Status Synchronization:</strong> Updates both status enum and human-readable statusString</li>
 * </ul>
 *
 * <p><strong>Status Update Process:</strong></p>
 * <ol>
 *   <li>Query FaxJobDao for in-progress faxes (those with provider job IDs, not terminal status)</li>
 *   <li>For each in-progress fax:
 *     <ul>
 *       <li>Look up FaxConfig by fax_line (sender account)</li>
 *       <li>Handle orphaned faxes (deleted account) by marking ERROR</li>
 *       <li>Skip inactive accounts (no polling if account disabled)</li>
 *       <li>Fetch current status from provider API (SRFax Get_Fax_Outbox, etc.)</li>
 *       <li>Update FaxJob with latest status and status message</li>
 *       <li>Persist updated FaxJob to database</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p><strong>Terminal vs In-Progress States:</strong></p>
 * <ul>
 *   <li><strong>In-Progress (polled by this service):</strong> Faxes with provider job IDs that
 *       may still change status (e.g., "Queued", "Sending", "Retrying")</li>
 *   <li><strong>Terminal (not polled):</strong> SENT (delivered), ERROR (permanent failure),
 *       WAITING (local queue), READY (local queue)</li>
 * </ul>
 *
 * <p><strong>Orphaned Fax Handling:</strong></p>
 * <p>When a FaxConfig account is deleted while faxes are in-progress, those faxes become
 * "orphaned" with no configuration to poll. This service detects orphaned faxes and marks
 * them ERROR with message: "Fax account configuration was deleted - cannot check status".
 * This prevents infinite polling attempts and alerts administrators to the orphaned state.</p>
 *
 * <p><strong>Inactive Account Behavior:</strong></p>
 * <p>Faxes from inactive accounts (FaxConfig.active=false) are NOT polled for status updates.
 * This allows administrators to temporarily disable polling for specific accounts without
 * marking all in-progress faxes as ERROR. When account is reactivated, polling resumes.</p>
 *
 * <p><strong>Error Handling:</strong></p>
 * <ul>
 *   <li><strong>FaxProviderException:</strong> Provider communication failures are logged at
 *       ERROR level but do NOT stop processing of remaining faxes. This ensures one failing
 *       provider does not block status updates for all faxes.</li>
 *   <li><strong>No Status Change on Error:</strong> If provider fetch fails, FaxJob status
 *       remains unchanged (not marked ERROR). This allows retry on next poll.</li>
 * </ul>
 *
 * <p><strong>Performance Considerations:</strong></p>
 * <p>Status updates are synchronous and may involve multiple HTTP requests to provider APIs.
 * For high-volume deployments, monitor execution time and consider:</p>
 * <ul>
 *   <li>Increasing poll interval to reduce API load</li>
 *   <li>Implementing batch status queries if provider supports it</li>
 *   <li>Adding timeout configuration to prevent blocking on slow provider responses</li>
 * </ul>
 *
 * <p><strong>Logging:</strong></p>
 * <p>Service logs status update counts and individual fax updates:</p>
 * <ul>
 *   <li><strong>INFO:</strong> "CHECKING STATUS OF N FAXES" (start of poll)</li>
 *   <li><strong>INFO:</strong> "UPDATED FAX JOB ID X WITH STATUS Y" (successful update)</li>
 *   <li><strong>ERROR:</strong> "Failed to update fax status for fax id X" (provider failure)</li>
 *   <li><strong>ERROR:</strong> "Could not find faxConfig for fax id X" (orphaned fax)</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * // Typically invoked by scheduled job (FaxScheduler) or manually via admin UI
 * faxStatusUpdater.updateStatus();  // Polls all in-progress faxes across all active accounts
 * </pre>
 *
 * @see FaxProviderClient#fetchFaxStatus
 * @see FaxProviderClientFactory
 * @see FaxConfig
 * @see FaxJob
 * @see FaxJobDao#getInprogressFaxesByJobId
 * @since 2014-08-29
 */
@Service
public class FaxStatusUpdater {

    private final FaxJobDao faxJobDao;
    private final FaxConfigDao faxConfigDao;
    private final FaxProviderClientFactory faxProviderClientFactory;
    private Logger log = MiscUtils.getLogger();

    @Autowired
    public FaxStatusUpdater(FaxJobDao faxJobDao, FaxConfigDao faxConfigDao,
            FaxProviderClientFactory faxProviderClientFactory) {
        this.faxJobDao = faxJobDao;
        this.faxConfigDao = faxConfigDao;
        this.faxProviderClientFactory = faxProviderClientFactory;
    }

    /**
     * Polls remote fax providers for status updates on all in-progress outbound faxes.
     *
     * <p><strong>Process Flow:</strong></p>
     * <ol>
     *   <li>Query FaxJobDao for faxes with provider job IDs (in-progress, not terminal status)</li>
     *   <li>Log total number of faxes to check (INFO level for monitoring)</li>
     *   <li>For each in-progress fax:
     *     <ul>
     *       <li>Look up FaxConfig by fax_line field (sender account number)</li>
     *       <li>Handle missing FaxConfig (orphaned fax from deleted account)</li>
     *       <li>Skip inactive accounts (FaxConfig.active=false prevents polling)</li>
     *       <li>Fetch current status via FaxProviderClient.fetchFaxStatus()</li>
     *       <li>Update local FaxJob with latest status and statusString from provider</li>
     *       <li>Persist updated FaxJob to database via FaxJobDao.merge()</li>
     *       <li>Log successful update with job ID and new status</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * <p><strong>Orphaned Fax Detection:</strong></p>
     * <p>When FaxConfig lookup returns null (account deleted while fax in-progress), the service
     * marks the fax as ERROR with message: "Fax account configuration was deleted - cannot check status".
     * This prevents infinite polling attempts on orphaned faxes and alerts administrators via logs:</p>
     * <pre>
     * ERROR: Could not find faxConfig for fax id 123 with fax_line +16045551234 - marking as ERROR.
     *        Has the fax account been deleted?
     * </pre>
     *
     * <p><strong>Inactive Account Handling:</strong></p>
     * <p>Faxes from inactive accounts (FaxConfig.active=false) are skipped silently. This allows
     * temporary account suspension without marking all in-progress faxes as ERROR. When account is
     * reactivated, status polling resumes automatically.</p>
     *
     * <p><strong>Provider API Calls:</strong></p>
     * <p>For each in-progress fax, the service makes one API call to the provider:
     * <ul>
     *   <li><strong>SRFax:</strong> Get_Fax_Outbox with sFaxDetailsID parameter</li>
     *   <li><strong>Middleware:</strong> Provider-specific status endpoint</li>
     * </ul>
     * Provider responses include status code (e.g., "Success", "Failed") and human-readable
     * message (e.g., "Delivered to recipient", "Busy - will retry").</p>
     *
     * <p><strong>Status Update Strategy:</strong></p>
     * <p>Both FaxJob fields are updated from provider response:</p>
     * <ul>
     *   <li><strong>status (enum):</strong> Machine-readable state (SENT, ERROR, IN_PROGRESS)</li>
     *   <li><strong>statusString:</strong> Human-readable message for UI display</li>
     * </ul>
     * <p>Example provider responses:</p>
     * <pre>
     * Status: SENT,       statusString: "Delivered to recipient"
     * Status: ERROR,      statusString: "Invalid fax number"
     * Status: IN_PROGRESS, statusString: "Busy - will retry (attempt 2 of 3)"
     * </pre>
     *
     * <p><strong>Error Handling:</strong></p>
     * <ul>
     *   <li><strong>FaxProviderException:</strong> Provider communication failures are logged at
     *       ERROR level but do NOT stop processing. The failing fax status remains unchanged,
     *       allowing retry on next poll.</li>
     *   <li><strong>Continue-on-Error:</strong> One failing fax does not block status updates for
     *       remaining faxes in the queue. This ensures maximum status synchronization even when
     *       individual providers are experiencing issues.</li>
     *   <li><strong>No Retry Limit:</strong> Failed status checks do not increment retry counters.
     *       Polling continues on every scheduled run until fax reaches terminal state or is
     *       manually marked complete.</li>
     * </ul>
     *
     * <p><strong>Concurrency Considerations:</strong></p>
     * <p>This method is designed to be called from a single-threaded scheduler (FaxScheduler).
     * If invoked concurrently from multiple threads/nodes, race conditions may occur during
     * status updates. For distributed deployments, ensure only one instance polls status at
     * a time, or implement optimistic locking on FaxJob.</p>
     *
     * <p><strong>Performance Monitoring:</strong></p>
     * <p>Monitor these log messages to detect performance issues:</p>
     * <ul>
     *   <li>"CHECKING STATUS OF N FAXES" - High N may indicate status not transitioning to terminal state</li>
     *   <li>Excessive FaxProviderException errors - May indicate provider API issues or rate limiting</li>
     *   <li>Orphaned fax errors - Indicates improper FaxConfig deletion workflow</li>
     * </ul>
     *
     * <p>This method is typically invoked by scheduled job (FaxScheduler) every 5-15 minutes,
     * but can also be triggered manually via admin UI for immediate status refresh.</p>
     *
     * @throws FaxProviderException propagated from provider client (caught and logged, not thrown)
     * @see FaxJobDao#getInprogressFaxesByJobId
     * @see FaxConfigDao#getConfigByNumber
     * @see FaxProviderClient#fetchFaxStatus
     * @since 2014-08-29
     */
    public void updateStatus() {

        List<FaxJob> faxJobList = faxJobDao.getInprogressFaxesByJobId();

        log.info("CHECKING STATUS OF {} FAXES", faxJobList.size());

        for (FaxJob faxJob : faxJobList) {
            FaxConfig faxConfig = faxConfigDao.getConfigByNumber(faxJob.getFax_line());

            if (faxConfig == null) {
                log.error("Could not find faxConfig for fax id {} with fax_line {} - marking as ERROR. Has the fax account been deleted?",
                        faxJob.getId(), faxJob.getFax_line());
                faxJob.setStatus(FaxJob.STATUS.ERROR);
                faxJob.setStatusString("Fax account configuration was deleted - cannot check status");
                faxJobDao.merge(faxJob);
                continue;
            }

            if (faxConfig.isActive()) {
                try {
                    FaxProviderClient providerClient = faxProviderClientFactory.getClient(faxConfig);
                    FaxJob faxJobUpdated = providerClient.fetchFaxStatus(faxConfig, faxJob);
                    faxJob.setStatus(faxJobUpdated.getStatus());
                    faxJob.setStatusString(faxJobUpdated.getStatusString());
                    log.info("UPDATED FAX JOB ID {} WITH STATUS {}", faxJob.getJobId(), faxJob.getStatus());
                    faxJobDao.merge(faxJob);
                } catch (FaxProviderException e) {
                    log.error("Failed to update fax status for fax id {}", faxJob.getId(), e);
                }
            }

        }
    }

}
