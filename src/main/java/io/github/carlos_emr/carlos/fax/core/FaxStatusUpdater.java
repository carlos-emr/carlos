/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.fax.core;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.http.HttpStatus;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.fax.connector.FaxConnector;
import io.github.carlos_emr.carlos.fax.connector.FaxConnectorFactory;
import io.github.carlos_emr.carlos.fax.connector.FaxStatusCheckResult;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Polls for in-progress outbound fax jobs and updates their delivery status.
 * <p>
 * Supports two integration modes based on each fax account's configuration:
 * <ul>
 *   <li><b>Legacy Gateway</b>: Queries the external CXF REST gateway server via
 *       HTTP GET for the job's current status.</li>
 *   <li><b>Direct API</b> (e.g. SRFax): Uses the {@link FaxConnector} interface to
 *       check delivery status directly with the cloud fax provider.</li>
 * </ul>
 * Called periodically by the {@code FaxSchedulerJob} TimerTask.
 *
 * @since 2026-02-09 (refactored for dual-mode fax support)
 */
public class FaxStatusUpdater {

    private FaxJobDao faxJobDao = SpringUtils.getBean(FaxJobDao.class);
    private FaxConfigDao faxConfigDao = SpringUtils.getBean(FaxConfigDao.class);
    private Logger log = MiscUtils.getLogger();

    /**
     * Main status update entry point. Retrieves all in-progress fax jobs (those with
     * a non-null jobId and SENT status) and checks their delivery status with the
     * appropriate fax service.
     */
    public void updateStatus() {

        List<FaxJob> faxJobList = faxJobDao.getInprogressFaxesByJobId();
        FaxConfig faxConfig;

        log.info("CHECKING STATUS OF {} FAXES", faxJobList.size());

        for (FaxJob faxJob : faxJobList) {
            // Look up the fax config by the fax line number stored on the job
            faxConfig = faxConfigDao.getConfigByNumber(faxJob.getFax_line());

            if (faxConfig == null) {
                log.error("Could not find faxConfig while processing fax id: {}. Has the fax number changed?", faxJob.getId());
            } else if (faxConfig.isActive()) {

                // Dispatch to the appropriate code path based on integration type
                if (FaxConnectorFactory.isLegacyGateway(faxConfig)) {
                    updateStatusViaLegacyGateway(faxConfig, faxJob);
                } else {
                    updateStatusViaDirectApi(faxConfig, faxJob);
                }
            }
        }
    }

    /**
     * Check fax status using the legacy external gateway server.
     * This preserves the original code path for backward compatibility.
     */
    private void updateStatusViaLegacyGateway(FaxConfig faxConfig, FaxJob faxJob) {

        Credentials credentials = new UsernamePasswordCredentials(faxConfig.getSiteUser(), faxConfig.getPasswd());
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT), credentials);

        try (CloseableHttpClient client = HttpClientBuilder.create()
                .setDefaultCredentialsProvider(credentialsProvider)
                .build()) {

            HttpGet mGet = new HttpGet(faxConfig.getUrl() + "/" + faxJob.getJobId());
            mGet.setHeader("accept", "application/json");
            mGet.setHeader("user", faxConfig.getFaxUser());
            mGet.setHeader("passwd", faxConfig.getFaxPasswd());

            HttpResponse response = client.execute(mGet);
            log.info("RESPONSE: {}", response.getStatusLine().getStatusCode());

            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {

                HttpEntity httpEntity = response.getEntity();
                String content = EntityUtils.toString(httpEntity);

                ObjectMapper mapper = new ObjectMapper();
                FaxJob faxJobUpdated = mapper.readValue(content, FaxJob.class);

                faxJob.setStatus(faxJobUpdated.getStatus());
                faxJob.setStatusString(faxJobUpdated.getStatusString());

                log.info("UPDATED FAX JOB ID {} WITH STATUS {}", faxJob.getJobId(), faxJob.getStatus());
                faxJobDao.merge(faxJob);

            } else {
                log.error("WEB SERVICE RESPONDED WITH {}", response.getStatusLine().getStatusCode());
            }

        } catch (IOException e) {
            log.error("HTTP WS CLIENT ERROR", e);
        }
    }

    /**
     * Check fax delivery status using a direct API connector (e.g. SRFax).
     * <p>
     * Obtains the appropriate {@link FaxConnector} via the factory, queries the
     * remote service for the job's current status, and persists the updated status
     * back to the database if the query succeeds.
     *
     * @param faxConfig FaxConfig the fax account configuration
     * @param faxJob FaxJob the in-progress fax job to check
     */
    private void updateStatusViaDirectApi(FaxConfig faxConfig, FaxJob faxJob) {

        FaxConnector connector = FaxConnectorFactory.getConnector(faxConfig);

        try {
            // Guard against null jobId to prevent NPE during Long-to-long unboxing
            if (faxJob.getJobId() == null) {
                log.error("Fax job {} has null jobId, cannot check status via direct API", faxJob.getId());
                return;
            }
            FaxStatusCheckResult result = connector.checkFaxStatus(faxConfig, faxJob.getJobId());

            if (result.isSuccess()) {
                faxJob.setStatus(result.getStatus());
                faxJob.setStatusString(result.getStatusMessage());
                faxJobDao.merge(faxJob);
                log.info("UPDATED FAX JOB ID {} WITH STATUS {} via direct API", faxJob.getJobId(), faxJob.getStatus());
            } else {
                log.warn("Failed to check status for fax job {}: {}", faxJob.getJobId(), result.getStatusMessage());
            }
        } catch (Exception e) {
            log.error("Error checking fax status via direct API for job {}", faxJob.getJobId(), e);
        }
    }

}
