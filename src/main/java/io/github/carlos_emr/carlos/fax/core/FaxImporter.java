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
import java.net.URLEncoder;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.http.HttpStatus;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.fax.connector.FaxConnector;
import io.github.carlos_emr.carlos.fax.connector.FaxConnectorFactory;
import io.github.carlos_emr.carlos.fax.connector.FaxInboundResult;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import com.itextpdf.text.pdf.codec.Base64;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;

/**
 * Polls remote fax services for incoming faxes, downloads them, and saves
 * them into the CARLOS document management system.
 * <p>
 * Supports two integration modes based on each fax account's configuration:
 * <ul>
 *   <li><b>Legacy Gateway</b>: Uses HTTP GET calls to the external CXF REST gateway
 *       to retrieve and delete incoming faxes.</li>
 *   <li><b>Direct API</b> (e.g. SRFax): Uses the {@link FaxConnector} interface to
 *       poll, download, and mark faxes as read on the remote service.</li>
 * </ul>
 * Downloaded faxes are saved as PDF documents via {@link EDocUtil}, linked to the
 * configured inbox queue, and routed through provider lab routing for notification.
 * Called periodically by the {@code FaxSchedulerJob} TimerTask.
 *
 * @since 2026-02-09 (refactored for dual-mode fax support)
 */
public class FaxImporter {

    /** REST path segment for the legacy fax gateway endpoint. */
    private static String PATH = "/fax";
    /** Filesystem directory for storing downloaded fax documents. */
    private static String DOCUMENT_DIR = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");
    /** Default provider ID for system-generated fax records. */
    private static String DEFAULT_USER = "-1";
    private FaxConfigDao faxConfigDao = SpringUtils.getBean(FaxConfigDao.class);
    private FaxJobDao faxJobDao = SpringUtils.getBean(FaxJobDao.class);
    private QueueDocumentLinkDao queueDocumentLinkDao = SpringUtils.getBean(QueueDocumentLinkDao.class);
    private ProviderLabRoutingDao providerLabRoutingDao = SpringUtils.getBean(ProviderLabRoutingDao.class);
    private Logger log = MiscUtils.getLogger();

    /**
     * Main polling entry point. Iterates all active fax configurations that have
     * downloading enabled and checks each for incoming faxes.
     */
    public void poll() {

        log.info("CHECKING REMOTE FOR INCOMING FAXES");

        List<FaxConfig> faxConfigList = faxConfigDao.findAll(null, null);

        for (FaxConfig faxConfig : faxConfigList) {
            if (faxConfig.isActive() && faxConfig.isDownload()) {

                // Dispatch to the appropriate code path based on integration type
                if (FaxConnectorFactory.isLegacyGateway(faxConfig)) {
                    pollViaLegacyGateway(faxConfig);
                } else {
                    pollViaDirectApi(faxConfig);
                }
            }
        }

    }

    /**
     * Poll for incoming faxes using the legacy external gateway server.
     * This preserves the original code path for backward compatibility.
     */
    private void pollViaLegacyGateway(FaxConfig faxConfig) {

        Credentials credentials = new UsernamePasswordCredentials(faxConfig.getSiteUser(), faxConfig.getPasswd());
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT), credentials);

        HttpGet mGet = null;

        try (CloseableHttpClient client = HttpClientBuilder.create()
                .setDefaultCredentialsProvider(credentialsProvider)
                .build()) {

            HttpResponse response = null;
            int status = HttpStatus.SC_OK;

            log.debug("Service Path: {}{}{}{}", faxConfig.getUrl(), PATH, File.separator, faxConfig.getFaxUser());

            mGet = new HttpGet(faxConfig.getUrl() + PATH + File.separator + URLEncoder.encode(faxConfig.getFaxUser(), "UTF-8"));
            mGet.setHeader("accept", "application/json");
            mGet.setHeader("user", faxConfig.getFaxUser());
            mGet.setHeader("passwd", faxConfig.getFaxPasswd());
            response = client.execute(mGet);

            if (response != null) {
                status = response.getStatusLine().getStatusCode();
            }

            if (status == HttpStatus.SC_OK) {

                HttpEntity httpEntity = response.getEntity();
                String content = EntityUtils.toString(httpEntity);

                log.debug("CONTENT: {}", content);

                ObjectMapper mapper = new ObjectMapper();

                List<FaxJob> faxList = mapper.readValue(content, new TypeReference<List<FaxJob>>() {
                });

                for (FaxJob receivedFax : faxList) {

                    String fileName = null;
                    EDoc edoc = null;
                    FaxJob faxFile = null;

                    // if this receivedFax Object contains an error
                    // skip the download step there is no file to download.
                    if (!FaxJob.STATUS.ERROR.equals(receivedFax.getStatus())) {
                        faxFile = downloadFax(client, faxConfig, receivedFax);
                    }

                    // save the received fax to the file system and assign to an inbox Queue
                    if (faxFile != null) {
                        edoc = saveAndInsertIntoQueue(faxConfig, receivedFax, faxFile);
                    }

                    if (edoc != null) {
                        fileName = edoc.getFileName();
                    }

                    if (fileName != null) {
                        try {
                            providerRouting(Integer.parseInt(edoc.getDocId()));
                            deleteFax(client, faxConfig, receivedFax);
                        } catch (NumberFormatException nfe) {
                            log.error("Invalid document ID '{}' for received fax; skipping provider routing and delete.", edoc.getDocId(), nfe);
                            fileName = FaxJob.STATUS.ERROR.name();
                        }
                    } else {
                        fileName = FaxJob.STATUS.ERROR.name();
                    }

                    receivedFax.setFile_name(fileName);
                    saveFaxJob(new FaxJob(receivedFax));
                }

            } else {
                log.error("HTTP Status error with HTTP code: {}", status);
            }

        } catch (IOException e) {
            log.error("HTTP WS CLIENT ERROR", e);
        } finally {
            if (mGet != null) {
                mGet.reset();
            }
        }
    }

    /**
     * Poll for incoming faxes using a direct API connector (e.g. SRFax).
     * Downloads each fax, saves to the document system, and marks as read on the remote service.
     */
    private void pollViaDirectApi(FaxConfig faxConfig) {

        // Resolve the connector implementation based on the account's integration type
        FaxConnector connector = FaxConnectorFactory.getConnector(faxConfig);

        try {
            // Query the remote API for unread incoming faxes
            List<FaxInboundResult> inboundFaxes = connector.pollIncomingFaxes(faxConfig);

            if (inboundFaxes == null) {
                inboundFaxes = Collections.emptyList();
            }

            log.info("Direct API poll returned {} incoming faxes for account {}",
                    inboundFaxes.size(), faxConfig.getAccountName());

            // Process each inbound fax individually; errors on one fax don't block others
            for (FaxInboundResult inbound : inboundFaxes) {
                try {
                    processDirectApiInboundFax(connector, faxConfig, inbound);
                } catch (Exception e) {
                    log.error("Error processing inbound fax ref={}", inbound.getExternalReference(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error polling for incoming faxes via direct API", e);
        }
    }

    /**
     * Process a single inbound fax received through a direct API connector.
     * Downloads the fax content, saves to the document system, and marks as read.
     */
    private void processDirectApiInboundFax(FaxConnector connector, FaxConfig faxConfig, FaxInboundResult inbound) {

        String faxReference = inbound.getExternalReference();

        // Download the fax content as base64
        String base64Content = connector.downloadFax(faxConfig, faxReference);
        if (base64Content == null) {
            log.warn("Failed to download fax content for ref={}", faxReference);
            saveInboundFaxJob(faxConfig, inbound, FaxJob.STATUS.ERROR, "Download failed");
            return;
        }

        // Build a filename from the inbound result
        String filename = inbound.getFileName();
        if (filename == null || filename.isEmpty()) {
            filename = "fax_" + faxReference + ".pdf";
        }
        filename = filename.replace("|", "-").trim();
        if (!filename.toLowerCase().endsWith(".pdf")) {
            filename = filename + ".pdf";
        }

        // Save to filesystem and insert into document queue
        EDoc newDoc = new EDoc("Received Fax", "Received Fax", filename, "",
                DEFAULT_USER, DEFAULT_USER, "", 'A',
                org.apache.commons.lang3.time.DateFormatUtils.format(
                        inbound.getReceivedDate() != null ? inbound.getReceivedDate() : new Date(), "yyyy-MM-dd"),
                "", "", "demographic", DEFAULT_USER, inbound.getPageCount());

        newDoc.setDocPublic("0");
        filename = newDoc.getFileName();

        // Validate the destination path to prevent path traversal
        File documentDir = new File(DOCUMENT_DIR);
        File validatedPath = PathValidationUtils.validatePath(filename, documentDir);

        if (Base64.decodeToFile(base64Content, validatedPath.getAbsolutePath())) {

            newDoc.setContentType("application/pdf");
            newDoc.setNumberOfPages(inbound.getPageCount());
            String doc_no = EDocUtil.addDocumentSQL(newDoc);

            Integer queueId = faxConfig.getQueue();

            try {
                Integer docNum = Integer.parseInt(doc_no);
                queueDocumentLinkDao.addActiveQueueDocumentLink(queueId, docNum);
                log.info("Saved inbound fax {} to filesystem as document ID {}", filename, docNum);
                providerRouting(docNum);

                // Persist job record before marking as read to ensure we have a record even if mark-as-read fails
                saveInboundFaxJob(faxConfig, inbound, FaxJob.STATUS.RECEIVED, filename);

                // Mark as read on the remote service so it does not appear again
                connector.markFaxAsRead(faxConfig, faxReference);
            } catch (NumberFormatException nfe) {
                log.error("Invalid document ID '{}' for inbound fax ref={}; skipping routing and mark-as-read.",
                        doc_no, faxReference, nfe);
                saveInboundFaxJob(faxConfig, inbound, FaxJob.STATUS.ERROR, "Invalid document ID");
            }

        } else {
            log.error("Failed to save fax file {} to filesystem", filename);
            saveInboundFaxJob(faxConfig, inbound, FaxJob.STATUS.ERROR, "Failed to save to filesystem");
        }
    }

    /**
     * Persist an inbound fax job record from a direct API connector result.
     */
    private void saveInboundFaxJob(FaxConfig faxConfig, FaxInboundResult inbound, FaxJob.STATUS status, String fileName) {
        FaxJob faxJob = new FaxJob();
        faxJob.setUser(DEFAULT_USER);
        faxJob.setFax_line(faxConfig.getFaxNumber());
        faxJob.setFile_name(fileName);
        faxJob.setStatus(status);
        faxJob.setStamp(inbound.getReceivedDate() != null ? inbound.getReceivedDate() : new Date());
        faxJob.setNumPages(inbound.getPageCount());
        faxJob.setStatusString(inbound.getCallerNumber());
        faxJobDao.persist(faxJob);
    }

    private FaxJob downloadFax(CloseableHttpClient client, FaxConfig faxConfig, FaxJob fax) {

        FaxJob downloadedFax = null;
        HttpGet mGet = null;

        try {
            mGet = new HttpGet(faxConfig.getUrl() + PATH + "/"
                    + URLEncoder.encode(faxConfig.getFaxUser(), "UTF-8") + "/"
                    + URLEncoder.encode(fax.getFile_name(), "UTF-8"));
            mGet.setHeader("accept", "application/json");
            mGet.setHeader("user", faxConfig.getFaxUser());
            mGet.setHeader("passwd", faxConfig.getFaxPasswd());

            HttpResponse response = client.execute(mGet);

            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {

                HttpEntity httpEntity = response.getEntity();
                String content = EntityUtils.toString(httpEntity);

                ObjectMapper mapper = new ObjectMapper();

                downloadedFax = mapper.readValue(content, FaxJob.class);

                fax.setStatus(downloadedFax.getStatus());
                fax.setStatusString(downloadedFax.getStatusString());

                // the fileName will be null if there is an error
                // will need to modify the receivedFax header appropriately.
                if (FaxJob.STATUS.ERROR.equals(downloadedFax.getStatus())) {
                    downloadedFax = null;
                }
            }

        } catch (ClientProtocolException e) {
            log.error("HTTP WS CLIENT ERROR", e);
        } catch (IOException e) {
            log.error("IO ERROR", e);
        } finally {
            if (mGet != null) {
                mGet.reset();
            }
        }

        return downloadedFax;
    }

    private void deleteFax(CloseableHttpClient client, FaxConfig faxConfig, FaxJob fax)
            throws ClientProtocolException, IOException {
        HttpDelete mDelete = new HttpDelete(faxConfig.getUrl() + PATH + "/"
                + URLEncoder.encode(faxConfig.getFaxUser(), "UTF-8") + "/"
                + URLEncoder.encode(fax.getFile_name(), "UTF-8"));

        mDelete.setHeader("accept", "application/json");
        mDelete.setHeader("user", faxConfig.getFaxUser());
        mDelete.setHeader("passwd", faxConfig.getFaxPasswd());

        log.info("Deleting Fax file " + fax.getFile_name() + " from the host server.");

        HttpResponse response = client.execute(mDelete);
        mDelete.reset();

        if (!(response.getStatusLine().getStatusCode() == HttpStatus.SC_NO_CONTENT)) {
            log.debug("Failed to delete Fax file " + fax.getFile_name() + " from the host server.");
            throw new ClientProtocolException("CANNOT DELETE " + fax.getFile_name());
        } else {
            log.info("Fax file " + fax.getFile_name() + " has been deleted from the host server.");
        }
    }

    private EDoc saveAndInsertIntoQueue(FaxConfig faxConfig, FaxJob receivedFax, FaxJob faxFile) {

        String filename = receivedFax.getFile_name();

        filename = filename.replace("|", "-");

        if (filename.isEmpty()) {
            filename = System.currentTimeMillis() + ".pdf";
        }

        filename = filename.replace(".tif", ".pdf");

        if (!filename.endsWith(".pdf") || !filename.endsWith(".PDF")) {
            filename = filename + ".pdf";
        }

        filename = filename.trim();

        EDoc newDoc = new EDoc("Received Fax", "Received Fax", filename, "",
                DEFAULT_USER, DEFAULT_USER, "", 'A',
                DateFormatUtils.format(receivedFax.getStamp(), "yyyy-MM-dd"),
                "", "", "demographic", DEFAULT_USER, receivedFax.getNumPages());

        newDoc.setDocPublic("0");

        filename = newDoc.getFileName();

        if (Base64.decodeToFile(faxFile.getDocument(), DOCUMENT_DIR + "/" + filename)) {

            newDoc.setContentType("application/pdf");
            newDoc.setNumberOfPages(receivedFax.getNumPages());
            String doc_no = EDocUtil.addDocumentSQL(newDoc);

            Integer queueId = faxConfig.getQueue();
            Integer docNum = Integer.parseInt(doc_no);

            queueDocumentLinkDao.addActiveQueueDocumentLink(queueId, docNum);

            log.info("Saved file " + filename + " to filesystem at " + DOCUMENT_DIR + " as document ID " + docNum);

            newDoc.setDocId(doc_no);

            return newDoc;
        }

        log.debug("Failed to save file " + filename + " to filesystem at " + DOCUMENT_DIR);

        return null;

    }

    private Integer saveFaxJob(FaxJob saveFax) {
        saveFax.setUser(DEFAULT_USER);
        faxJobDao.persist(saveFax);
        return saveFax.getId();
    }


    private void providerRouting(Integer labNo) {
        ProviderLabRoutingModel providerLabRouting = new ProviderLabRoutingModel();
        providerLabRouting.setLabNo(labNo);
        providerRouting(providerLabRouting);
    }

    /**
     * Put an entry in Provider Lab Routing that will cause the unclaimed lab indicator
     * to light up next to the inbox.
     *
     * @return
     */
    private void providerRouting(ProviderLabRoutingModel providerLabRouting) {

        providerLabRouting.setLabType(ProviderLabRoutingDao.LAB_TYPE.DOC.name());
        providerLabRouting.setProviderNo(ProviderLabRoutingDao.UNCLAIMED_PROVIDER);
        providerLabRouting.setStatus(ProviderLabRoutingDao.STATUS.N.name());
        providerLabRouting.setTimestamp(new Date(System.currentTimeMillis()));
        providerLabRoutingDao.persist(providerLabRouting);

        Integer id = providerLabRouting.getId();
        if (id == null || id < 1) {
            log.warn("Failed to add Fax document id " + providerLabRouting.getLabNo() + " to provider lab routing.");
        }
    }

}
