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
            log.info("SENDING {} faxes from fax account {}", faxJobList.size(), faxConfig.getSiteUser());

            for (FaxJob faxJob : faxJobList) {
                FaxClientLog faxClientLog = faxClientLogDao.findClientLogbyFaxId(faxJob.getId());
                STATUS faxStatus = STATUS.ERROR;
                faxJob.setSenderEmail(faxConfig.getSenderEmail());

                Path filePath = resolveFilePath(faxJob.getFile_name(), documentDir);
                log.info("sending fax from file path {}", filePath);

                try {
                    FaxProviderClient providerClient = faxProviderClientFactory.getClient(faxConfig);
                    FaxJob sentFax = providerClient.sendFax(faxConfig, faxJob, filePath);
                    faxStatus = sentFax.getStatus();
                } catch (FaxProviderException e) {
                    String statusMessage = e.getMessage() == null ? "PROBLEM COMMUNICATING WITH WEB SERVICE" : e.getMessage();
                    faxJob.setStatusString(statusMessage);
                    log.error("Fax send failed for fax id {} using provider {}", faxJob.getId(), faxConfig.getProviderType(), e);
                    if (statusMessage.contains("Connection error")) {
                        faxStatus = FaxJob.STATUS.WAITING;
                    }
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
    }

    /**
     * Resolves and validates the file path for a fax document.
     *
     * <p>This method handles both absolute and relative file paths with proper security validation:
     * <ul>
     *   <li>Absolute paths are validated to ensure they are either under DOCUMENT_DIR or in an
     *       explicitly allowed temporary directory (e.g., for dynamically generated faxes).</li>
     *   <li>Relative paths are resolved against DOCUMENT_DIR and validated to prevent path
     *       traversal attacks.</li>
     * </ul>
     *
     * <p>Note on temporary file support: Some fax jobs may reference files in temporary directories
     * (outside DOCUMENT_DIR) for ephemeral documents like dynamically generated cover sheets.
     * These are allowed only if they reside in {@link PathValidationUtils#isInAllowedTempDirectory}.
     *
     * @param filename the filename or path from the fax job
     * @param documentDir the base document directory (DOCUMENT_DIR)
     * @return a validated Path pointing to the fax document
     * @throws IllegalArgumentException if filename is null or empty
     * @throws SecurityException if the path fails validation (path traversal attempt or
     *         unauthorized absolute path)
     */
    private Path resolveFilePath(String filename, String documentDir) {
        // ALWAYS validate paths using PathValidationUtils to prevent path traversal attacks
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Fax job filename must not be null or empty");
        }

        Path path = Paths.get(filename);

        // If an absolute path is provided, validate the existing file path first.
        if (path.isAbsolute()) {
            File absoluteFile = path.toFile();
            try {
                // Prefer files that are under DOCUMENT_DIR and already exist
                PathValidationUtils.validateExistingPath(absoluteFile, new File(documentDir));
                return absoluteFile.toPath();
            } catch (SecurityException e) {
                // Allow absolute paths in an explicitly allowed temp directory
                // (e.g., for dynamically generated fax cover sheets or attachments)
                if (PathValidationUtils.isInAllowedTempDirectory(absoluteFile)) {
                    return absoluteFile.toPath();
                }

                // Do NOT silently substitute a different file; fail the job instead.
                log.error("Invalid absolute fax job path: {}", filename, e);
                throw e;
            }
        }

        // For relative filenames, resolve safely under DOCUMENT_DIR using PathValidationUtils.
        try {
            File validatedFile = PathValidationUtils.validatePath(filename, new File(documentDir));
            return validatedFile.toPath();
        } catch (SecurityException e) {
            log.error("Path validation failed for relative fax job filename: {}", filename, e);
            // Do not fall back to a different file; propagate the failure.
            throw e;
        }
    }
}
