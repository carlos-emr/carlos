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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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
import com.itextpdf.text.pdf.PdfReader;

import io.github.carlos_emr.OscarProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service responsible for polling remote fax providers and importing inbound faxes into CARLOS EMR.
 *
 * <p>This service implements a complete fax import workflow with collision-free filename generation,
 * PDF validation, atomic file operations, and proper cleanup. It polls configured fax providers,
 * downloads fax documents, validates their integrity, and routes them to the provider inbox system.</p>
 *
 * <p><strong>Major Refactoring (February 2026):</strong> Enhanced with collision-free filename generation
 * using AtomicLong counters, PDF validation with iTextPDF, configurable temp directory support, and
 * atomic file operations to prevent partial imports. See related PR #345 for multi-provider support.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li><strong>Collision-Free Filenames:</strong> Uses timestamp + atomic counter to prevent filename collisions</li>
 *   <li><strong>PDF Validation:</strong> Validates PDF integrity and page count before importing</li>
 *   <li><strong>Atomic Operations:</strong> Uses atomic file moves to prevent partial imports</li>
 *   <li><strong>Temp Directory:</strong> Configurable via FAX_TEMP_DIR property with fallback to java.io.tmpdir</li>
 *   <li><strong>UNCLAIMED Routing:</strong> Routes faxes to unclaimed inbox for manual assignment</li>
 *   <li><strong>Error Recovery:</strong> Comprehensive error handling with cleanup and status tracking</li>
 * </ul>
 *
 * <p><strong>Configuration Properties:</strong></p>
 * <pre>
 * # oscar_mcmaster.properties
 * FAX_TEMP_DIR=/var/lib/carlos-fax-temp  # Optional, defaults to java.io.tmpdir/carlos-fax-import
 * DOCUMENT_DIR=/path/to/documents         # Required, final document storage location
 * </pre>
 *
 * @see FaxProviderClient
 * @see FaxConfig
 * @see FaxJob
 * @since 2014-08-29
 */
@Service
public class FaxImporter {

    private static final String DOCUMENT_DIR = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");

    /** Atomic counter for collision-free filename sequencing */
    private static final AtomicLong fileCounter = new AtomicLong(0);

    /** Temp directory for fax processing - initialized once at startup */
    private static final Path FAX_TEMP_DIR = initializeFaxTempDirectory();
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

    /**
     * Initializes the fax temporary directory for processing.
     *
     * <p>Checks for configured {@code FAX_TEMP_DIR} property in oscar_mcmaster.properties.
     * Falls back to {@code java.io.tmpdir/carlos-fax-import} if not configured or if the
     * configured directory cannot be created.</p>
     *
     * <p><strong>Configuration:</strong></p>
     * <pre>
     * # oscar_mcmaster.properties
     * FAX_TEMP_DIR=/var/lib/carlos-fax-temp
     * </pre>
     *
     * <p><strong>Fail-Fast Behavior:</strong> This method throws IllegalStateException if the
     * temp directory cannot be created, preventing the service from starting in a broken state
     * that would cause cryptic failures later during fax processing.</p>
     *
     * @return Path to fax temp directory
     * @throws IllegalStateException if temp directory cannot be created (permission denied, disk full, etc.)
     * @since 2026-02-12
     */
    private static Path initializeFaxTempDirectory() {
        Logger initLogger = MiscUtils.getLogger();
        OscarProperties props = OscarProperties.getInstance();
        String configuredPath = props.getProperty("FAX_TEMP_DIR");

        if (configuredPath != null && !configuredPath.trim().isEmpty()) {
            Path path = Paths.get(configuredPath.trim());
            if (Files.exists(path)) {
                initLogger.info("Using configured fax temp directory: {}", path);
                return path;
            }

            // Try to create configured directory - fail fast if impossible
            try {
                Files.createDirectories(path);
                initLogger.info("Created configured fax temp directory: {}", path);
                return path;
            } catch (IOException e) {
                initLogger.error("CRITICAL: Cannot create configured FAX_TEMP_DIR: {} - {}",
                        configuredPath, e.getMessage(), e);
                throw new IllegalStateException(
                        "Failed to create configured fax temp directory: " + configuredPath +
                        ". Check permissions, disk space, and parent directory existence. " +
                        "Fax service cannot start without writable temp directory.", e);
            }
        }

        // Fallback: java.io.tmpdir/carlos-fax-import
        String javaTemp = System.getProperty("java.io.tmpdir");
        Path fallback = Paths.get(javaTemp, "carlos-fax-import");

        try {
            Files.createDirectories(fallback);
            initLogger.info("Using default fax temp directory: {}", fallback);
            return fallback;
        } catch (IOException e) {
            initLogger.error("CRITICAL: Cannot create fallback fax temp directory: {} - {}",
                    fallback, e.getMessage(), e);
            throw new IllegalStateException(
                    "Failed to create fax temp directory at fallback location: " + fallback +
                    ". Check system temp directory permissions. Fax service cannot start.", e);
        }
    }


    /**
     * Polls all active fax provider accounts for inbound faxes and imports them into EMR.
     *
     * <p><strong>Process:</strong></p>
     * <ol>
     *   <li>Iterate through all active FaxConfig accounts with download enabled</li>
     *   <li>List inbound faxes from provider (unread only for duplicate prevention)</li>
     *   <li>Download each fax document</li>
     *   <li>Validate PDF and save with collision-free filename</li>
     *   <li>Register in EMR document system and provider routing</li>
     *   <li>Acknowledge/delete remote fax based on provider policy</li>
     *   <li>Save FaxJob status for tracking</li>
     * </ol>
     *
     * <p><strong>Error Handling:</strong> Errors at any stage are logged and recorded in FaxJob status.
     * Failed faxes are marked as ERROR but not deleted from remote provider (allows retry).</p>
     *
     * <p><strong>Duplicate Prevention:</strong> Uses provider-specific strategies (e.g., SRFax unread-only
     * pull with mark-as-read on download).</p>
     *
     * <p>This method is typically invoked by scheduled job (FaxScheduler) but can also be triggered manually.</p>
     *
     * @throws FaxProviderException if provider communication fails (logged, not propagated)
     * @since 2026-02-11
     */
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
                            log.error("Failed to download incoming fax file {} from provider {} - marking as ERROR",
                                    receivedFax.getFile_name(), faxConfig.getProviderType(), e);
                            receivedFax.setStatus(FaxJob.STATUS.ERROR);
                            receivedFax.setStatusString("Download failed: " + e.getMessage());
                            saveFaxJob(new FaxJob(receivedFax));
                            continue; // Skip to next fax
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
                            log.error("Invalid document ID from EDoc: {} - fax saved to disk but routing failed",
                                    edoc.getDocId(), e);
                            receivedFax.setStatus(FaxJob.STATUS.ERROR);
                            receivedFax.setStatusString("Saved to disk but routing failed - manual assignment required");
                            receivedFax.setFile_name(fileName);
                            saveFaxJob(new FaxJob(receivedFax));
                            // DO NOT delete remote fax - leave for potential retry
                            continue; // Skip to next fax
                        }

                        try {
                            providerClient.deleteFax(faxConfig, receivedFax);
                        } catch (FaxProviderException e) {
                            log.error("CRITICAL: Failed to delete remote fax {} - duplicate import will occur on next poll",
                                    receivedFax.getFile_name(), e);
                            receivedFax.setStatusString("Import succeeded but remote deletion failed - duplicate risk");
                            // Note: fileName is already set, fax will be saved below
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

    /**
     * Saves fax with collision-free filename using temp file + atomic move pattern.
     *
     * <p><strong>Process:</strong></p>
     * <ol>
     *   <li>Generate collision-free unique filename (timestamp + atomic counter)</li>
     *   <li>Create temp file with Java-guaranteed unique name</li>
     *   <li>Decode base64 to temp file</li>
     *   <li>Validate PDF integrity (page count, structure)</li>
     *   <li>Atomic move to final DOCUMENT_DIR location</li>
     *   <li>Register with EMR and add to queue</li>
     *   <li>Cleanup temp file on any failure</li>
     * </ol>
     *
     * <p><strong>Collision Prevention:</strong> Uses AtomicLong counter to ensure uniqueness
     * even when multiple faxes arrive in the same second.</p>
     *
     * <p><strong>Corruption Prevention:</strong> Validates PDF has valid structure and page count
     * before moving to production directory.</p>
     *
     * @param faxConfig Fax configuration
     * @param receivedFax Fax metadata from provider
     * @param faxFile Fax content (base64 PDF)
     * @return EDoc if successful, null if failed
     * @since 2026-02-12
     */
    private EDoc saveAndInsertIntoQueue(FaxConfig faxConfig, FaxJob receivedFax, FaxJob faxFile) {
        File tempFile = null;

        try {
            // Step 1: Create temp file (collision-free by Java)
            tempFile = Files.createTempFile(FAX_TEMP_DIR, "fax-", ".pdf").toFile();
            log.debug("Created temp file for fax download: {}", tempFile.getAbsolutePath());

            // Step 2: Decode base64 to temp file
            if (!Base64.decodeToFile(faxFile.getDocument(), tempFile.getAbsolutePath())) {
                throw new FaxProviderException("Base64 decode failed - returned false");
            }

            // Step 3: Validate file size
            if (tempFile.length() == 0) {
                throw new FaxProviderException("Decoded file is empty (0 bytes)");
            }

            // Step 4: Validate PDF integrity and count pages
            int numberOfPages = validateAndCountPages(tempFile);
            if (numberOfPages == 0) {
                throw new FaxProviderException("PDF validation failed - 0 pages or corrupted PDF");
            }

            // Step 5: Generate collision-free final filename
            String uniqueFilename = generateUniqueFilename(receivedFax.getFile_name());

            // Step 6: Validate final file path for security
            File finalFile = PathValidationUtils.validatePath(uniqueFilename, new File(DOCUMENT_DIR));

            // Step 7: Atomic move to final location
            Files.move(tempFile.toPath(), finalFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
            log.info("Fax file moved to final location: {}", finalFile.getAbsolutePath());
            tempFile = null; // Successfully moved, don't delete in finally

            // Step 8: Create EDoc and register with EMR
            EDoc newDoc = new EDoc("Received Fax", "Received Fax", uniqueFilename, "",
                    DEFAULT_USER, DEFAULT_USER, "", 'A',
                    DateFormatUtils.format(receivedFax.getStamp(), "yyyy-MM-dd"),
                    "", "", "demographic", DEFAULT_USER, numberOfPages);
            newDoc.setDocPublic("0");
            newDoc.setContentType("application/pdf");
            newDoc.setNumberOfPages(numberOfPages);

            // Step 9: Register document in database
            String doc_no = EDocUtil.addDocumentSQL(newDoc);
            log.info("Registered fax in EMR: doc_id={}, filename={}, pages={}",
                    doc_no, uniqueFilename, numberOfPages);

            // Step 10: Add to document queue (for staff review)
            Integer queueId = faxConfig.getQueue();
            int docNum;
            try {
                docNum = Integer.parseInt(doc_no);
            } catch (NumberFormatException e) {
                log.error("Invalid document ID from EDocUtil: {}", doc_no, e);
                receivedFax.setStatus(FaxJob.STATUS.ERROR);
                receivedFax.setStatusString("Internal error: invalid document ID format");
                return null;
            }

            queueDocumentLinkDao.addActiveQueueDocumentLink(queueId, docNum);
            log.info("Added fax to document queue: queue_id={}, doc_id={}", queueId, docNum);

            newDoc.setDocId(doc_no);
            return newDoc;

        } catch (FaxProviderException e) {
            log.error("Fax validation failed: {}", e.getMessage(), e);
            receivedFax.setStatus(FaxJob.STATUS.ERROR);
            receivedFax.setStatusString("PDF validation failed: " + e.getMessage());
            return null;
        } catch (IOException e) {
            log.error("File I/O error during fax import: {}", e.getMessage(), e);
            receivedFax.setStatus(FaxJob.STATUS.ERROR);
            receivedFax.setStatusString("File system error: " + e.getMessage());
            return null;
        } catch (SecurityException e) {
            log.error("SECURITY: Path validation failed for fax: {}", e.getMessage(), e);
            receivedFax.setStatus(FaxJob.STATUS.ERROR);
            receivedFax.setStatusString("Security validation failed - suspicious filename");
            return null;
        } finally {
            // Cleanup: Delete temp file if it still exists (error occurred before move)
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                if (!deleted) {
                    log.warn("Failed to delete temp file: {}", tempFile.getAbsolutePath());
                } else {
                    log.debug("Cleaned up temp file: {}", tempFile.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Generates collision-free unique filename.
     *
     * <p><strong>Uniqueness Strategy:</strong></p>
     * <ul>
     *   <li>Timestamp (yyyyMMdd-HHmmss) - human-readable, sortable</li>
     *   <li>Atomic sequence counter - prevents same-second collisions</li>
     *   <li>Original filename - preserved for staff recognition</li>
     * </ul>
     *
     * <p><strong>Thread Safety:</strong> AtomicLong ensures no collisions across threads.</p>
     *
     * <p><strong>Example:</strong> {@code 20260212-143025-00042-referral.pdf}</p>
     *
     * @param originalFilename Original filename from SRFax (may not be unique)
     * @return Guaranteed unique filename
     * @since 2026-02-12
     */
    private String generateUniqueFilename(String originalFilename) {
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        long sequence = fileCounter.incrementAndGet();
        String sanitized = sanitizeFilename(originalFilename);

        return String.format("%s-%05d-%s", timestamp, sequence, sanitized);
    }

    /**
     * Sanitizes filename by removing dangerous characters and ensuring .pdf extension.
     *
     * @param filename Original filename
     * @return Sanitized filename safe for filesystem
     * @since 2026-02-12
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "fax-" + System.currentTimeMillis() + ".pdf";
        }

        // Remove dangerous characters
        String sanitized = filename.trim()
            .replace("|", "-")
            .replace("\\", "-")
            .replace("/", "-")
            .replace(":", "-")
            .replace("*", "-")
            .replace("?", "-")
            .replace("\"", "-")
            .replace("<", "-")
            .replace(">", "-")
            .replace(".tif", ".pdf")
            .replace(".TIF", ".pdf")
            .replace(".TIFF", ".pdf");

        // Ensure .pdf extension
        if (!sanitized.toLowerCase().endsWith(".pdf")) {
            sanitized += ".pdf";
        }

        return sanitized;
    }

    /**
     * Validates PDF and counts pages using iTextPDF.
     *
     * @param pdfFile PDF file to validate
     * @return Number of pages if valid PDF
     * @throws FaxProviderException if PDF is corrupted, password-protected, or has 0 pages
     * @since 2026-02-12
     */
    private int validateAndCountPages(File pdfFile) throws FaxProviderException {
        PdfReader reader = null;
        try {
            reader = new PdfReader(pdfFile.getAbsolutePath());
            int pages = reader.getNumberOfPages();

            if (pages == 0) {
                throw new FaxProviderException("PDF has 0 pages");
            }

            log.debug("PDF validation successful: {} pages", pages);
            return pages;

        } catch (com.itextpdf.text.exceptions.BadPasswordException e) {
            throw new FaxProviderException("PDF is password-protected - cannot process");
        } catch (IOException e) {
            throw new FaxProviderException("Cannot read PDF file: " + e.getMessage());
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    /**
     * Persists FaxJob record to database for tracking and audit.
     *
     * <p>Sets user to DEFAULT_USER ("-1") as fax imports are system-initiated (not user-initiated).
     * FaxJob records track status, timestamps, provider job IDs, and error messages for each fax.</p>
     *
     * @param saveFax FaxJob to persist
     * @return FaxJob ID from database
     * @since 2026-02-11
     */
    private Integer saveFaxJob(FaxJob saveFax) {
        saveFax.setUser(DEFAULT_USER);
        faxJobDao.persist(saveFax);
        return saveFax.getId();
    }


    /**
     * Routes fax document to provider inbox system.
     *
     * @param labNo Document ID from EDocUtil.addDocumentSQL()
     * @since 2026-02-11
     */
    private void providerRouting(Integer labNo) {
        ProviderLabRoutingModel providerLabRouting = new ProviderLabRoutingModel();
        providerLabRouting.setLabNo(labNo);
        providerRouting(providerLabRouting);
    }

    /**
     * Routes fax document to provider inbox system using UNCLAIMED strategy.
     *
     * <p><strong>UNCLAIMED Routing Strategy:</strong></p>
     * <p>Incoming faxes are routed to the UNCLAIMED inbox (provider_no="0") rather than to
     * specific providers. This ensures:</p>
     * <ul>
     *   <li><strong>Visibility:</strong> All providers see the unclaimed indicator in navigation bar</li>
     *   <li><strong>Manual Review:</strong> Staff manually assigns faxes to appropriate provider after review</li>
     *   <li><strong>No Auto-Filing:</strong> Prevents faxes from being automatically filed without review</li>
     *   <li><strong>No Auto-Forwarding:</strong> Bypasses IncomingLabRules forwarding (intentional)</li>
     * </ul>
     *
     * <p><strong>Database Table:</strong> {@code providerLabRouting}</p>
     * <p>Despite the name "Lab" routing, this table handles both labs (lab_type="LAB") and documents
     * (lab_type="DOC"). The confusing naming is historical - the table is actually a general provider
     * inbox routing system.</p>
     *
     * <p><strong>Status Values:</strong></p>
     * <ul>
     *   <li><strong>"N"</strong> - New/unread (triggers red indicator in provider UI)</li>
     *   <li><strong>"A"</strong> - Acknowledged (provider viewed the document)</li>
     *   <li><strong>"F"</strong> - Filed (document filed to patient chart)</li>
     *   <li><strong>"X"</strong> - Deleted (soft delete, not used for faxes)</li>
     * </ul>
     *
     * <p><strong>Alternative Approach (NOT Implemented):</strong></p>
     * <p>Using {@link io.github.carlos_emr.carlos.commn.dao.ProviderInboxRoutingDao#addToProviderInbox}
     * would apply forwarding rules and auto-filing, but this is not desirable for inbound faxes which
     * require manual review. Future enhancement could add optional auto-routing via FaxConfig.defaultProvider
     * field while maintaining unclaimed entry as safety net.</p>
     *
     * @param providerLabRouting Routing model with labNo (document ID) set
     * @see ProviderLabRoutingDao
     * @see io.github.carlos_emr.carlos.commn.dao.ProviderInboxRoutingDao
     * @since 2026-02-11
     */
    private void providerRouting(ProviderLabRoutingModel providerLabRouting) {
        // Set document type to DOC (not LAB) - providerLabRouting table handles both types
        providerLabRouting.setLabType(ProviderLabRoutingDao.LAB_TYPE.DOC.name());

        // Route to UNCLAIMED inbox ("0") - makes fax visible to all providers
        providerLabRouting.setProviderNo(ProviderLabRoutingDao.UNCLAIMED_PROVIDER);

        // Set status to NEW ("N") - triggers red unclaimed indicator in provider navigation bar
        providerLabRouting.setStatus(ProviderLabRoutingDao.STATUS.N.name());

        providerLabRouting.setTimestamp(new Date(System.currentTimeMillis()));
        providerLabRoutingDao.persist(providerLabRouting);

        Integer id = providerLabRouting.getId();
        if (id == null || id < 1) {
            log.warn("Failed to add Fax document id {} to provider lab routing.", providerLabRouting.getLabNo());
        }
    }

}
