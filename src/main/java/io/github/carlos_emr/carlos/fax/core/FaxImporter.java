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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClient;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClientFactory;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderException;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import com.itextpdf.text.pdf.codec.Base64;

import io.github.carlos_emr.OscarProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FaxImporter {

    private static String DOCUMENT_DIR = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");
    private static String DEFAULT_USER = "-1";
    private final FaxConfigDao faxConfigDao;
    private final FaxJobDao faxJobDao;
    private final QueueDocumentLinkDao queueDocumentLinkDao;
    private final ProviderLabRoutingDao providerLabRoutingDao;
    private final FaxProviderClientFactory faxProviderClientFactory;
    private Logger log = MiscUtils.getLogger();

    @Autowired
    public FaxImporter(FaxConfigDao faxConfigDao, FaxJobDao faxJobDao, QueueDocumentLinkDao queueDocumentLinkDao,
            ProviderLabRoutingDao providerLabRoutingDao, FaxProviderClientFactory faxProviderClientFactory) {
        this.faxConfigDao = faxConfigDao;
        this.faxJobDao = faxJobDao;
        this.queueDocumentLinkDao = queueDocumentLinkDao;
        this.providerLabRoutingDao = providerLabRoutingDao;
        this.faxProviderClientFactory = faxProviderClientFactory;
    }

    public void poll() {

        log.info("CHECKING REMOTE FOR INCOMING FAXES");

        List<FaxConfig> faxConfigList = faxConfigDao.findAll(null, null);

        for (FaxConfig faxConfig : faxConfigList) {
            if (!faxConfig.isActive() || !faxConfig.isDownload()) {
                continue;
            }

            try {
                FaxProviderClient providerClient = faxProviderClientFactory.getClient(faxConfig);
                List<FaxJob> faxList = providerClient.listInboundFaxes(faxConfig);

                for (FaxJob receivedFax : faxList) {

                    String fileName = null;
                    EDoc edoc = null;
                    FaxJob faxFile = null;

                    if (!FaxJob.STATUS.ERROR.equals(receivedFax.getStatus())) {
                        try {
                            faxFile = providerClient.downloadFax(faxConfig, receivedFax);
                        } catch (FaxProviderException e) {
                            log.error("Failed to download incoming fax file {}", receivedFax.getFile_name(), e);
                        }
                    }

                    if (faxFile != null) {
                        edoc = saveAndInsertIntoQueue(faxConfig, receivedFax, faxFile);
                    }

                    if (edoc != null) {
                        fileName = edoc.getFileName();
                    }

                    if (fileName != null) {
                        try {
                            int docId = Integer.parseInt(edoc.getDocId());
                            providerRouting(docId);
                        } catch (NumberFormatException e) {
                            log.error("Invalid document ID from EDoc: {}", edoc.getDocId(), e);
                            fileName = FaxJob.STATUS.ERROR.name();
                        }

                        try {
                            providerClient.deleteFax(faxConfig, receivedFax);
                        } catch (FaxProviderException e) {
                            log.error("Failed to delete remote fax file {}", receivedFax.getFile_name(), e);
                        }
                    } else {
                        fileName = FaxJob.STATUS.ERROR.name();
                    }

                    receivedFax.setFile_name(fileName);
                    saveFaxJob(new FaxJob(receivedFax));
                }

            } catch (FaxProviderException e) {
                log.error("HTTP WS CLIENT ERROR", e);
            }
        }

    }

    private EDoc saveAndInsertIntoQueue(FaxConfig faxConfig, FaxJob receivedFax, FaxJob faxFile) {

        String filename = receivedFax.getFile_name();

        filename = filename.replace("|", "-");

        if (filename.isEmpty()) {
            filename = System.currentTimeMillis() + ".pdf";
        }

        filename = filename.replace(".tif", ".pdf");

        if (!filename.endsWith(".pdf") && !filename.endsWith(".PDF")) {
            filename = filename + ".pdf";
        }

        filename = filename.trim();

        EDoc newDoc = new EDoc("Received Fax", "Received Fax", filename, "",
                DEFAULT_USER, DEFAULT_USER, "", 'A',
                DateFormatUtils.format(receivedFax.getStamp(), "yyyy-MM-dd"),
                "", "", "demographic", DEFAULT_USER, receivedFax.getNumPages());

        newDoc.setDocPublic("0");

        filename = newDoc.getFileName();

        // Validate file path to prevent path traversal attacks
        Path resolvedPath = Paths.get(DOCUMENT_DIR, filename).normalize();
        if (!resolvedPath.startsWith(Paths.get(DOCUMENT_DIR).normalize())) {
            log.error("Path traversal attempt blocked for filename: " + filename);
            return null;
        }

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
