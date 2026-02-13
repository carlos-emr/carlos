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

import io.github.carlos_emr.OscarProperties;
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
 * <p><strong>Fax Sending Process:</strong></p>
 * <ol>
 *   <li>Query all active FaxConfig accounts with provider credentials</li>
 *   <li>Retrieve queued faxes with status READY or WAITING (via FaxJobDao.getReadyToSendFaxes)</li>
 *   <li>Validate and resolve file path (DOCUMENT_DIR or approved temp directory)</li>
 *   <li>Send fax via provider client (SRFaxProviderClient or middleware)</li>
 *   <li>Update FaxJob status (SENT, ERROR, or WAITING for retry)</li>
 *   <li>Record audit trail in FaxClientLog with timestamps and results</li>
 * </ol>
 *
 * <p><strong>Error Handling Strategies:</strong></p>
 * <ul>
 *   <li><strong>Invalid File Path:</strong> Marks fax as ERROR with detailed status message</li>
 *   <li><strong>Connection Errors:</strong> Reverts to WAITING status to allow retry on next poll</li>
 *   <li><strong>Provider Errors:</strong> Marks as ERROR with provider error message for investigation</li>
 *   <li><strong>Security Violations:</strong> Logs security warnings and prevents send (path traversal attempt)</li>
 * </ul>
 *
 * <p><strong>File Path Security:</strong></p>
 * <p>All file paths are validated using {@link PathValidationUtils} to prevent path traversal attacks.
 * The service accepts both:</p>
 * <ul>
 *   <li><strong>Relative paths:</strong> Resolved against DOCUMENT_DIR (standard document storage)</li>
 *   <li><strong>Absolute paths:</strong> Validated to be under DOCUMENT_DIR or in allowed temp directories
 *       (for dynamically generated cover sheets)</li>
 * </ul>
 *
 * <p><strong>Configuration Properties:</strong></p>
 * <pre>
 * # oscar_mcmaster.properties
 * DOCUMENT_DIR=/path/to/documents  # Required, primary fax document storage
 * </pre>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * // Typically invoked by scheduled job (FaxScheduler) or manually via admin UI
 * faxSender.send();  // Processes all queued faxes across all active accounts
 * </pre>
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
     * <p><strong>Process Flow:</strong></p>
     * <ol>
     *   <li>Retrieves all FaxConfig accounts from database (active and inactive)</li>
     *   <li>Iterates through active accounts only (skip inactive to prevent send attempts)</li>
     *   <li>For each active account, retrieves ready-to-send faxes (status READY or WAITING)</li>
     *   <li>For each fax:
     *     <ul>
     *       <li>Validates file path using PathValidationUtils (prevent path traversal)</li>
     *       <li>Resolves file location (DOCUMENT_DIR or approved temp directory)</li>
     *       <li>Sends fax via FaxProviderClient (SRFax, middleware, etc.)</li>
     *       <li>Updates FaxJob status based on provider response (SENT, ERROR, WAITING)</li>
     *       <li>Records audit trail in FaxClientLog with timestamps and result</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * <p><strong>Status Transitions:</strong></p>
     * <ul>
     *   <li><strong>READY/WAITING → SENT:</strong> Provider successfully accepted fax</li>
     *   <li><strong>READY/WAITING → ERROR:</strong> Invalid file path, provider error, or validation failure</li>
     *   <li><strong>READY/WAITING → WAITING:</strong> Connection error (retry on next poll)</li>
     * </ul>
     *
     * <p><strong>Error Handling:</strong></p>
     * <p>Errors are caught and logged but do NOT stop processing of remaining faxes.
     * This ensures one problematic fax does not block the entire queue:</p>
     * <ul>
     *   <li><strong>IllegalArgumentException/SecurityException:</strong> Invalid or unsafe file path
     *       (path traversal attempt). Fax marked as ERROR with detailed status message.</li>
     *   <li><strong>FaxProviderException:</strong> Provider communication failure. If error message
     *       contains "Connection error", fax reverted to WAITING for retry; otherwise marked ERROR.</li>
     *   <li><strong>Finally Block:</strong> Ensures FaxJob status and FaxClientLog are ALWAYS updated
     *       regardless of success or failure.</li>
     * </ul>
     *
     * <p><strong>Connection Error Retry Logic:</strong></p>
     * <p>When provider throws FaxProviderException with "Connection error" message, the fax is
     * reverted to WAITING status rather than ERROR. This allows automatic retry on next scheduled
     * poll, handling transient network issues without manual intervention.</p>
     *
     * <p><strong>Audit Trail:</strong></p>
     * <p>FaxClientLog entries are updated with:</p>
     * <ul>
     *   <li><strong>result:</strong> Final status (SENT, ERROR, WAITING)</li>
     *   <li><strong>endTime:</strong> Timestamp when send attempt completed</li>
     * </ul>
     *
     * <p><strong>Security:</strong></p>
     * <p>File path validation prevents path traversal attacks via {@link #resolveFilePath}.
     * Only files under DOCUMENT_DIR or in explicitly allowed temp directories can be sent.
     * Security violations are logged with WARNING level and include fax ID for audit.</p>
     *
     * <p><strong>Performance:</strong></p>
     * <p>Processing is synchronous within each fax account. For high-volume deployments,
     * consider implementing batch processing or async execution. Current implementation logs
     * fax count per account to aid monitoring: "SENDING N faxes from fax account X".</p>
     *
     * <p>This method is typically invoked by scheduled job (FaxScheduler) but can also be
     * triggered manually via admin UI or management console.</p>
     *
     * @throws FaxProviderException propagated from provider client (logged, not thrown)
     * @see FaxJobDao#getReadyToSendFaxes
     * @see FaxProviderClient#sendFax
     * @see #resolveFilePath
     * @since 2014-08-29
     */
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

                try {
                    Path filePath = resolveFilePath(faxJob.getFile_name(), documentDir);
                    log.info("sending fax from file path {}", filePath);

                    FaxProviderClient providerClient = faxProviderClientFactory.getClient(faxConfig);
                    FaxJob sentFax = providerClient.sendFax(faxConfig, faxJob, filePath);
                    faxStatus = sentFax.getStatus();
                } catch (IllegalArgumentException | SecurityException e) {
                    String statusMessage = "INVALID OR UNSAFE FAX FILE PATH";
                    faxJob.setStatusString(statusMessage);
                    log.warn("Skipping fax id {} due to invalid or unsafe file path", faxJob.getId(), e);
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
