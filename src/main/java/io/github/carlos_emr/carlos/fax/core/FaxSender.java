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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.FaxClientLogDao;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.model.FaxClientLog;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.commn.model.FaxJob.STATUS;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClient;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClientFactory;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderException;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import io.github.carlos_emr.OscarProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FaxSender {

    private final FaxConfigDao faxConfigDao;
    private final FaxJobDao faxJobDao;
    private final FaxClientLogDao faxClientLogDao;
    private final FaxProviderClientFactory faxProviderClientFactory;

    private Logger log = MiscUtils.getLogger();

    @Autowired
    public FaxSender(FaxConfigDao faxConfigDao, FaxJobDao faxJobDao, FaxClientLogDao faxClientLogDao,
            FaxProviderClientFactory faxProviderClientFactory) {
        this.faxConfigDao = faxConfigDao;
        this.faxJobDao = faxJobDao;
        this.faxClientLogDao = faxClientLogDao;
        this.faxProviderClientFactory = faxProviderClientFactory;
    }

    public void send() {

        List<FaxConfig> faxConfigList = faxConfigDao.findAll(null, null);
        String documentDir = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");

        for (FaxConfig faxConfig : faxConfigList) {
            if (!faxConfig.isActive()) {
                continue;
            }

            List<FaxJob> faxJobList = faxJobDao.getReadyToSendFaxes(faxConfig.getFaxNumber());
            log.info("SENDING " + faxJobList.size() + " faxes from fax account " + faxConfig.getSiteUser());

            for (FaxJob faxJob : faxJobList) {
                FaxClientLog faxClientLog = faxClientLogDao.findClientLogbyFaxId(faxJob.getId());
                STATUS faxStatus = STATUS.ERROR;
                faxJob.setSenderEmail(faxConfig.getSenderEmail());

                Path filePath = resolveFilePath(faxJob.getFile_name(), documentDir);
                log.info("sending fax from file path " + filePath);

                try {
                    FaxProviderClient providerClient = faxProviderClientFactory.getClient(faxConfig);
                    FaxJob sentFax = providerClient.sendFax(faxConfig, faxJob, filePath);
                    faxStatus = sentFax.getStatus();
                } catch (FaxProviderException e) {
                    String statusMessage = e.getMessage() == null ? "PROBLEM COMMUNICATING WITH WEB SERVICE" : e.getMessage();
                    faxJob.setStatusString(statusMessage);
                    log.error("Fax send failed for fax id " + faxJob.getId() + " using provider " + faxConfig.getProviderType(), e);
                    if (statusMessage.contains("Connection error")) {
                        faxStatus = FaxJob.STATUS.WAITING;
                    }
                } finally {
                    faxJob.setStatus(faxStatus);
                    faxJobDao.merge(faxJob);
                    log.info("Updated Fax with jobid " + faxJob.getJobId() + " and status " + faxJob.getStatus());
                    if (faxClientLog != null) {
                        faxClientLog.setResult(faxStatus.name());
                        faxClientLog.setEndTime(new Date(System.currentTimeMillis()));
                        faxClientLogDao.merge(faxClientLog);
                    }
                }
            }
        }
    }

    private Path resolveFilePath(String filename, String documentDir) {
        Path filePath = Paths.get(filename);

        if (!Files.exists(filePath)) {
            // Extract just the file name and validate path to prevent traversal attacks
            try {
                File validatedFile = PathValidationUtils.validatePath(filename, new File(documentDir));
                filePath = validatedFile.toPath();
            } catch (SecurityException e) {
                log.error("Path validation failed for filename: {}", filename, e);
                // Fall back to document dir with just the base filename
                Path baseFilename = Paths.get(filename).getFileName();
                if (baseFilename != null) {
                    filePath = Paths.get(documentDir, baseFilename.toString());
                }
            }
        }

        return filePath;
    }
}
