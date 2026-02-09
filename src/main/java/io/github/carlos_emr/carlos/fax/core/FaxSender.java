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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.cxf.common.util.Base64Utility;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.http.HttpStatus;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.FaxClientLogDao;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.model.FaxClientLog;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.commn.model.FaxJob.STATUS;
import io.github.carlos_emr.carlos.fax.connector.FaxConnector;
import io.github.carlos_emr.carlos.fax.connector.FaxConnectorFactory;
import io.github.carlos_emr.carlos.fax.connector.FaxSendResult;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.OscarProperties;

/**
 * Dispatches outbound fax jobs to the appropriate fax service.
 * <p>
 * Iterates over all active fax configurations, retrieves pending fax jobs for each,
 * and dispatches them based on the configuration's integration type:
 * <ul>
 *   <li><b>Legacy Gateway</b>: Uses the original CXF WebClient path to send faxes
 *       through an external REST-based fax gateway server.</li>
 *   <li><b>Direct API</b> (e.g. SRFax): Uses the {@link FaxConnector} interface to
 *       send faxes directly to the cloud fax provider's API.</li>
 * </ul>
 * Called periodically by the {@code FaxSchedulerJob} TimerTask.
 *
 * @since 2026-02-09 (refactored for dual-mode fax support)
 */
public class FaxSender {

    /** REST path segment for the legacy fax gateway endpoint. */
    private static String PATH = "/fax";
    private final FaxConfigDao faxConfigDao = SpringUtils.getBean(FaxConfigDao.class);
    private final FaxJobDao faxJobDao = SpringUtils.getBean(FaxJobDao.class);
    private final FaxClientLogDao faxClientLogDao = SpringUtils.getBean(FaxClientLogDao.class);

    private Logger log = MiscUtils.getLogger();

    /**
     * Main send entry point. Iterates all active fax configurations and sends
     * any pending fax jobs through the appropriate integration path.
     */
    public void send() {

        List<FaxConfig> faxConfigList = faxConfigDao.findAll(null, null);
        List<FaxJob> faxJobList;

        String document_dir = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");

        for (FaxConfig faxConfig : faxConfigList) {
            if (faxConfig.isActive()) {

                faxJobList = faxJobDao.getReadyToSendFaxes(faxConfig.getFaxNumber());

                log.info("SENDING {} faxes from fax account {}", faxJobList.size(), faxConfig.getAccountName());

                // Dispatch to the appropriate code path based on integration type
                if (FaxConnectorFactory.isLegacyGateway(faxConfig)) {
                    sendViaLegacyGateway(faxConfig, faxJobList, document_dir);
                } else {
                    sendViaDirectApi(faxConfig, faxJobList, document_dir);
                }
            }
        }
    }

    /**
     * Send faxes using the legacy external CXF REST gateway server.
     * This preserves the original code path for backward compatibility.
     */
    private void sendViaLegacyGateway(FaxConfig faxConfig, List<FaxJob> faxJobList, String document_dir) {

        WebClient client = WebClient.create(faxConfig.getUrl());
        client.path(PATH + "/send/" + faxConfig.getFaxUser());
        client.type(MediaType.APPLICATION_XML);
        client.accept(MediaType.APPLICATION_XML);

        String login = faxConfig.getSiteUser() + ":" + faxConfig.getPasswd();
        String authorizationHeader = "Basic " + Base64Utility.encode(login.getBytes());
        client.header("Authorization", authorizationHeader);

        HTTPConduit conduit = WebClient.getConfig(client).getHttpConduit();
        HTTPClientPolicy policy = new HTTPClientPolicy();
        policy.setConnectionTimeout(30000);
        policy.setReceiveTimeout(60000);
        conduit.setClient(policy);

        FaxJob faxJobId;
        String filename;
        Path filePath;

        for (FaxJob faxJob : faxJobList) {

            FaxClientLog faxClientLog = faxClientLogDao.findClientLogbyFaxId(faxJob.getId());
            STATUS faxStatus = STATUS.ERROR;

            client.header("user", faxJob.getUser());
            client.header("passwd", faxConfig.getFaxPasswd());

            faxJob.setSenderEmail(faxConfig.getSenderEmail());
            filename = faxJob.getFile_name();
            filePath = Paths.get(filename);

            if (!Files.exists(filePath)) {
                if (filename.contains(File.separator)) {
                    filename.replaceAll(File.separator, "");
                }
                filePath = Paths.get(document_dir, filename);
            }

            log.info("sending fax from file path {}", filePath);

            try {
                if (Files.exists(filePath) && Files.isReadable(filePath)) {
                    String base64 = Base64Utility.encode(Files.readAllBytes(filePath));
                    faxJob.setDocument(base64);
                }

                if (faxJob.getDocument() == null) {
                    log.error("Fatal error locating document. Not found in any directory or database.");
                    throw new IOException();
                }

                Response httpResponse = client.post(faxJob);

                if (httpResponse.getStatus() == HttpStatus.SC_OK) {
                    faxJobId = httpResponse.readEntity(FaxJob.class);
                    faxJob.setDocument(null);
                    faxJob.setJobId(faxJobId.getJobId());
                    faxJob.setStatusString(faxJobId.getStatusString());
                    faxStatus = faxJobId.getStatus();
                } else {
                    faxJob.setStatusString("WEB SERVICE RESPONDED WITH " + httpResponse.getStatus());
                    log.error("WEB SERVICE RESPONDED WITH {}", httpResponse.getStatus());
                }

            } catch (HttpHostConnectException e) {
                faxStatus = FaxJob.STATUS.WAITING;
                faxJob.setStatusString("Connection error. Check internet connection.");
                log.error("Connection error. Check internet connection. Filepath: {}", filePath);
            } catch (IOException e) {
                faxJob.setStatusString("CANNOT FIND file");
                log.error("CANNOT FIND Filepath: {}", filePath);
            } catch (Exception e) {
                faxJob.setStatusString("PROBLEM COMMUNICATING WITH WEB SERVICE");
                log.error("PROBLEM COMMUNICATING WITH WEB SERVICE", e);
            } finally {
                faxJob.setStatus(faxStatus);
                faxJobDao.merge(faxJob);
                log.info("Updated Fax with jobid {} and status {}", faxJob.getJobId(), faxJob.getStatus());
                if (faxClientLog != null) {
                    faxClientLog.setResult(faxStatus.name());
                    faxClientLog.setEndTime(new Date(System.currentTimeMillis()));
                    faxClientLogDao.merge(faxClientLog);
                }
            }
        }
    }

    /**
     * Send faxes using a direct API connector (e.g. SRFax).
     * This is the new code path for accounts configured with a direct integration type.
     */
    private void sendViaDirectApi(FaxConfig faxConfig, List<FaxJob> faxJobList, String document_dir) {

        FaxConnector connector = FaxConnectorFactory.getConnector(faxConfig);

        for (FaxJob faxJob : faxJobList) {

            FaxClientLog faxClientLog = faxClientLogDao.findClientLogbyFaxId(faxJob.getId());
            STATUS faxStatus = STATUS.ERROR;

            try {
                // Ensure the document content is loaded for transmission
                ensureDocumentLoaded(faxJob, document_dir);

                if (faxJob.getDocument() == null) {
                    log.error("Fatal error locating document. Not found in any directory or database.");
                    faxJob.setStatusString("Document not found");
                    continue;
                }

                FaxSendResult result = connector.sendFax(faxConfig, faxJob);

                if (result.isSuccess()) {
                    faxJob.setDocument(null);
                    faxJob.setJobId(result.getExternalJobId());
                    faxStatus = result.getStatus();
                    faxJob.setStatusString(result.getStatusMessage());
                } else {
                    faxStatus = result.getStatus();
                    faxJob.setStatusString(result.getStatusMessage());
                }

            } catch (Exception e) {
                faxJob.setStatusString("Error sending via direct API connector");
                log.error("Error sending fax via direct API connector", e);
            } finally {
                faxJob.setStatus(faxStatus);
                faxJobDao.merge(faxJob);
                log.info("Updated Fax with jobid {} and status {}", faxJob.getJobId(), faxJob.getStatus());
                if (faxClientLog != null) {
                    faxClientLog.setResult(faxStatus.name());
                    faxClientLog.setEndTime(new Date(System.currentTimeMillis()));
                    faxClientLogDao.merge(faxClientLog);
                }
            }
        }
    }

    /**
     * Ensures the fax job has its document content loaded as base64.
     * Reads from the filesystem if not already present in the FaxJob entity.
     */
    private void ensureDocumentLoaded(FaxJob faxJob, String document_dir) {
        if (faxJob.getDocument() != null) {
            return;
        }

        String filename = faxJob.getFile_name();
        Path filePath = Paths.get(filename);

        if (!Files.exists(filePath)) {
            if (filename.contains(File.separator)) {
                filename = filename.replaceAll(File.separator, "");
            }
            filePath = Paths.get(document_dir, filename);
        }

        try {
            if (Files.exists(filePath) && Files.isReadable(filePath)) {
                String base64 = Base64Utility.encode(Files.readAllBytes(filePath));
                faxJob.setDocument(base64);
            }
        } catch (IOException e) {
            log.error("Error reading fax document from filesystem: {}", filePath, e);
        }
    }

}
