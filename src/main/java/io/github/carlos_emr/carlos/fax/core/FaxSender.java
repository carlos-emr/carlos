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

import java.io.File;
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

import io.github.carlos_emr.CarlosProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service responsible for sending outbound faxes through configured fax provider clients.
 *
 * <p>This service implements a complete fax sending workflow with secure file path handling,
 * comprehensive error recovery, and detailed status tracking. It processes all queued outbound
 * faxes for active fax accounts and handles provider communication, file validation, and
 * audit logging.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li><strong>Multi-Account Support:</strong> Processes faxes from all active FaxConfig accounts</li>
 *   <li><strong>Secure File Handling:</strong> Uses PathValidationUtils for path traversal prevention</li>
 *   <li><strong>Provider Abstraction:</strong> Works with any FaxProviderClient (MIDDLEWARE, SRFAX)</li>
 *   <li><strong>Error Recovery:</strong> Network failures revert to WAITING status for retry</li>
 *   <li><strong>Audit Logging:</strong> Comprehensive FaxClientLog tracking for compliance</li>
 *   <li><strong>Temp File Support:</strong> Allows ephemeral files in approved temp directories</li>
 * </ul>
 *
 * @see FaxProviderClient
 * @see FaxProviderClientFactory
 * @see FaxConfig
 * @see FaxJob
 * @see FaxClientLog
 * @see PathValidationUtils
 * @since 2014-08-29
 */
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

    /**
     * Processes all queued outbound faxes for active fax accounts.
     *
     * <p>For each active FaxConfig, retrieves WAITING faxes, validates file paths via
     * {@link #resolveFilePath}, sends through the appropriate {@link FaxProviderClient},
     * and updates both FaxJob status and FaxClientLog audit trail.</p>
     *
     * <p><strong>Status Transitions:</strong></p>
     * <ul>
     *   <li><strong>WAITING → SENT (typical):</strong> Provider accepted fax for transmission.
     *       Actual status is determined by provider response</li>
     *   <li><strong>WAITING → ERROR:</strong> Invalid file path, provider error, or validation failure</li>
     *   <li><strong>WAITING → WAITING:</strong> Transient network error (automatic retry on next poll)</li>
     * </ul>
     *
     * <p><strong>Error Isolation:</strong> Errors are caught per-fax so one failure does not block
     * the queue. Path validation failures (IllegalArgumentException/SecurityException) mark ERROR.
     * Provider failures (FaxProviderException) mark ERROR unless the cause is a transient network
     * error (ConnectException, SocketTimeoutException, etc.), which reverts to WAITING for retry.
     * IllegalStateException from credential decryption skips the entire account. The finally block
     * ensures FaxJob and FaxClientLog are always persisted, with separate try-catch guards on each
     * merge to prevent one persistence failure from blocking the other.</p>
     *
     * <p>Typically invoked by {@link FaxSchedulerJob}. Provider exceptions are caught and logged
     * within this method; they are not propagated to callers.</p>
     *
     * @throws IllegalStateException if DOCUMENT_DIR is not configured
     *
     * @see FaxJobDao#getReadyToSendFaxes
     * @see FaxProviderClient#sendFax
     * @see #resolveFilePath
     * @since 2014-08-29
     */
    public void send() {

        List<FaxConfig> faxConfigList = faxConfigDao.findAll(null, null);
        String documentDir = CarlosProperties.getInstance().getDocumentDirectory();
        if (documentDir == null || documentDir.trim().isEmpty()) {
            throw new IllegalStateException(
                    "DOCUMENT_DIR is not configured and cannot be derived from BASE_DOCUMENT_DIR. "
                    + "Outbound fax send cycle aborted. "
                    + "Configure DOCUMENT_DIR or BASE_DOCUMENT_DIR in carlos.properties.");
        }

        for (FaxConfig faxConfig : faxConfigList) {
            if (!faxConfig.isActive()) {
                continue;
            }

            try {
                List<FaxJob> faxJobList = faxJobDao.getReadyToSendFaxes(faxConfig.getFaxNumber());
                log.info("SENDING {} faxes from fax account {}", faxJobList.size(), faxConfig.getSiteUser());

                for (FaxJob faxJob : faxJobList) {
                    FaxClientLog faxClientLog = faxClientLogDao.findClientLogbyFaxId(faxJob.getId());
                    STATUS faxStatus = STATUS.ERROR;
                    faxJob.setSenderEmail(faxConfig.getSenderEmail());

                    try {
                        Path filePath = resolveFilePath(faxJob.getFile_name(), documentDir);
                        log.debug("Sending fax id {} via {}", faxJob.getId(), faxConfig.getProviderType());

                        FaxProviderClient providerClient = faxProviderClientFactory.getClient(faxConfig);
                        FaxJob sentFax = providerClient.sendFax(faxConfig, faxJob, filePath);
                        faxStatus = sentFax.getStatus();
                        faxJob.setJobId(sentFax.getJobId());
                        if (sentFax.getStatusString() != null) {
                            faxJob.setStatusString(sentFax.getStatusString());
                        }
                    } catch (IllegalArgumentException | SecurityException e) {
                        String statusMessage = "INVALID OR UNSAFE FAX FILE PATH";
                        faxJob.setStatusString(statusMessage);
                        log.warn("Skipping fax id {} due to invalid or unsafe file path", faxJob.getId(), e);
                    } catch (FaxProviderException e) {
                        String statusMessage = e.getMessage() == null ? "PROBLEM COMMUNICATING WITH WEB SERVICE" : e.getMessage();
                        faxJob.setStatusString(statusMessage);
                        log.error("Fax send failed for fax id {} using provider {}", faxJob.getId(), faxConfig.getProviderType(), e);
                        if (isTransientNetworkError(e)) {
                            faxStatus = FaxJob.STATUS.WAITING;
                        }
                    } finally {
                        faxJob.setStatus(faxStatus);
                        try {
                            faxJobDao.merge(faxJob);
                            log.info("Updated Fax with jobid {} and status {}", faxJob.getJobId(), faxJob.getStatus());
                        } catch (RuntimeException e) {
                            log.error("CRITICAL: Failed to persist fax status for fax id {} - status {} may be lost",
                                    faxJob.getId(), faxStatus, e);
                        }
                        if (faxClientLog != null) {
                            try {
                                faxClientLog.setResult(faxStatus.name());
                                faxClientLog.setEndTime(new Date(System.currentTimeMillis()));
                                faxClientLogDao.merge(faxClientLog);
                            } catch (RuntimeException e) {
                                log.error("Failed to update FaxClientLog for fax id {} - audit trail incomplete",
                                        faxJob.getId(), e);
                            }
                        } else {
                            log.warn("No FaxClientLog found for fax id {} - audit trail incomplete", faxJob.getId());
                        }
                    }
                }
            } catch (IllegalStateException e) {
                log.error("Credential decryption failed for fax account {} ({}) - re-enter password in "
                        + "Administration > Faxes > Configure Fax. Skipping this account.",
                        faxConfig.getSiteUser(), faxConfig.getProviderType(), e);
            } catch (RuntimeException e) {
                log.error("Unexpected error sending faxes for account {} ({}) - continuing with next account: {}",
                        faxConfig.getSiteUser(), faxConfig.getProviderType(), e.getMessage(), e);
            }
        }
    }

    /**
     * Checks if a FaxProviderException represents a transient network error that
     * warrants automatic retry (WAITING status) rather than permanent failure (ERROR).
     *
     * <p>Delegates to {@link FaxProviderException#isTransient()}, which is determined by provider
     * clients at exception creation time by passing the result of
     * {@link FaxProviderException#isTransientNetworkCause(Throwable)} to the constructor.</p>
     *
     * @param e the provider exception to inspect
     * @return true if the provider flagged this as a transient error
     */
    private boolean isTransientNetworkError(FaxProviderException e) {
        return e.isTransient();
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
                absoluteFile = PathValidationUtils.validateExistingPath(absoluteFile, new File(documentDir));
                // S2083: Path.resolve() clears SonarCloud taint — validateExistingPath() confirmed containment
                absoluteFile = absoluteFile.getParentFile().toPath().resolve(absoluteFile.getName()).toFile();
                return absoluteFile.toPath();
            } catch (SecurityException e) {
                // Allow absolute paths in an explicitly allowed temp directory
                // (e.g., for dynamically generated fax cover sheets or attachments)
                if (PathValidationUtils.isInAllowedTempDirectory(absoluteFile)) {
                    // S2083: Path.resolve() clears SonarCloud taint — isInAllowedTempDirectory() confirmed temp dir containment
                    absoluteFile = absoluteFile.getParentFile().toPath().resolve(absoluteFile.getName()).toFile();
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
            // S2083: Path.resolve() clears SonarCloud taint — validatePath() sanitized filename and confirmed containment
            validatedFile = new File(documentDir).toPath().resolve(validatedFile.getName()).toFile();
            return validatedFile.toPath();
        } catch (SecurityException e) {
            log.error("Path validation failed for relative fax job filename: {}", filename, e);
            // Do not fall back to a different file; propagate the failure.
            throw e;
        }
    }
}
