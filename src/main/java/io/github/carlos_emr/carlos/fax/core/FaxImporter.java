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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

import com.lowagie.text.pdf.PdfReader;

import java.nio.file.Files;

import io.github.carlos_emr.OscarProperties;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service responsible for polling remote fax providers and importing inbound faxes into CARLOS EMR.
 *
 * <p>Uses a quarantine-based import strategy: faxes are first saved to a local incoming directory,
 * then marked as read on the remote provider, then imported into the EMR document system. This
 * ensures no fax data is lost even if the import step fails — files remain in the incoming
 * directory and are automatically retried on the next poll cycle.</p>
 *
 * <p><strong>Three-Phase Import Strategy:</strong></p>
 * <ol>
 *   <li><strong>Phase 1 (Download):</strong> Download from provider, decode, validate PDF,
 *       save to {@code FAX_INCOMING_DIR/{configId}/}</li>
 *   <li><strong>Phase 2 (Acknowledge):</strong> Mark fax as read on provider (content is safe locally)</li>
 *   <li><strong>Phase 3 (Import):</strong> Move to DOCUMENT_DIR, register in EMR, route to inbox</li>
 * </ol>
 *
 * <p>If Phase 3 fails, the file stays in the incoming directory. On the next poll cycle,
 * {@link #retryPendingImports(List)} scans for pending files and retries the import.</p>
 *
 * <p><strong>Configuration Properties:</strong></p>
 * <pre>
 * # carlos.properties
 * FAX_INCOMING_DIR=/path/to/fax/incoming  # Optional; defaults resolved by OscarProperties:
 *                                         #   1. ${catalina.base}/fax-incoming (Tomcat)
 *                                         #   2. ${java.io.tmpdir}/carlos-fax-incoming (non-Tomcat)
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

    /**
     * Static initialization is intentional: OscarProperties is a read-once singleton with no
     * reload mechanism — property changes require a Tomcat restart. This matches the pattern
     * used by ManageDocument2Action and NioFileManager. The {@link #initialize()} PostConstruct
     * guard validates this value at Spring startup, failing fast if misconfigured.
     */
    private static final String DOCUMENT_DIR = OscarProperties.getInstance().getDocumentDirectory();

    /** Atomic counter for collision-free filename sequencing */
    private static final AtomicLong fileCounter = new AtomicLong(0);

    private static final String DEFAULT_USER = "-1";
    private final FaxConfigDao faxConfigDao;
    private final FaxJobDao faxJobDao;
    private final QueueDocumentLinkDao queueDocumentLinkDao;
    private final ProviderLabRoutingDao providerLabRoutingDao;
    private final FaxProviderClientFactory faxProviderClientFactory;
    private Logger log = MiscUtils.getLogger();

    /** Incoming directory for quarantined fax files, initialized at startup */
    private Path faxIncomingDir;

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
     * Validates directories and initializes the incoming fax directory at startup.
     *
     * @throws IllegalStateException if DOCUMENT_DIR is not configured
     */
    @PostConstruct
    public void initialize() {
        if (DOCUMENT_DIR == null || DOCUMENT_DIR.trim().isEmpty()) {
            throw new IllegalStateException(
                    "DOCUMENT_DIR is not configured and cannot be derived from BASE_DOCUMENT_DIR. "
                    + "Configure DOCUMENT_DIR or BASE_DOCUMENT_DIR in carlos.properties.");
        }

        String incomingDirPath = OscarProperties.getInstance().getFaxIncomingDirectory();
        if (incomingDirPath == null || incomingDirPath.trim().isEmpty()) {
            throw new IllegalStateException(
                    "FAX_INCOMING_DIR cannot be resolved. "
                    + "Configure FAX_INCOMING_DIR in carlos.properties.");
        }

        faxIncomingDir = Paths.get(incomingDirPath.trim());
        try {
            Files.createDirectories(faxIncomingDir);
            log.info("Fax incoming directory: {}", faxIncomingDir);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot create fax incoming directory: " + faxIncomingDir
                    + ". Check permissions and disk space.", e);
        }
    }

    /**
     * Polls all active fax provider accounts for inbound faxes and imports them into CARLOS EMR.
     *
     * <p>Before polling providers, retries any pending imports from the incoming directory
     * that failed on previous cycles. Then downloads new faxes using the three-phase strategy:
     * save locally, mark as read on provider, import into EMR.</p>
     *
     * @since 2014-08-29
     */
    public void poll() {

        log.info("CHECKING REMOTE FOR INCOMING FAXES");

        List<FaxConfig> faxConfigList = faxConfigDao.findAll(null, null);

        if (faxConfigList.isEmpty()) {
            log.warn("No fax accounts configured - scheduler running but nothing to poll");
            return;
        }

        // Retry any pending imports from previous failures before pulling new faxes
        retryPendingImports(faxConfigList);

        // Count active configs with download enabled
        long activeDownloadCount = faxConfigList.stream()
                .filter(fc -> fc.isActive() && fc.isDownload())
                .count();

        if (activeDownloadCount == 0) {
            log.warn("No active fax accounts with download enabled - scheduler running but nothing to poll");
            return;
        }

        for (FaxConfig faxConfig : faxConfigList) {
            if (!faxConfig.isActive() || !faxConfig.isDownload()) {
                continue;
            }

            try {
                FaxProviderClient providerClient = faxProviderClientFactory.getClient(faxConfig);
                List<FaxJob> faxList = providerClient.listInboundFaxes(faxConfig);

                for (FaxJob receivedFax : faxList) {

                    FaxJob faxFile = null;

                    if (!FaxJob.STATUS.ERROR.equals(receivedFax.getStatus())) {
                        try {
                            faxFile = providerClient.downloadFax(faxConfig, receivedFax);
                        } catch (FaxProviderException e) {
                            log.error("Failed to download incoming fax from provider {} - marking as ERROR",
                                    faxConfig.getProviderType(), e);
                            receivedFax.setStatus(FaxJob.STATUS.ERROR);
                            receivedFax.setStatusString("Download failed: " + e.getMessage());
                            saveFaxJob(new FaxJob(receivedFax));
                            continue;
                        }
                    }

                    // Phase 1: Save to incoming directory (quarantine)
                    Path incomingFile = null;
                    if (faxFile != null) {
                        incomingFile = saveToIncoming(faxConfig, receivedFax, faxFile);
                    }

                    if (incomingFile == null) {
                        // Download or save failed - leave fax unread on provider for retry
                        if (receivedFax.getStatus() == null) {
                            receivedFax.setStatus(FaxJob.STATUS.ERROR);
                        }
                        if (receivedFax.getStatusString() == null) {
                            receivedFax.setStatusString("Download or save to incoming directory failed");
                        }
                        saveFaxJob(new FaxJob(receivedFax));
                        continue;
                    }

                    // Phase 2: Content is safe locally - mark as read on provider.
                    // If this fails, the fax may be re-downloaded on next poll.
                    // The file already exists in incoming dir so dedup is handled by filename uniqueness.
                    try {
                        providerClient.markFaxAsRead(faxConfig, receivedFax);
                    } catch (FaxProviderException e) {
                        log.warn("Failed to mark fax as read on provider - may re-download on next poll", e);
                    }

                    // Phase 3: Import from incoming directory into EMR
                    String fileName = null;
                    EDoc edoc = importFromIncoming(incomingFile, faxConfig, receivedFax);

                    if (edoc != null) {
                        fileName = edoc.getFileName();

                        // Route to provider inbox
                        try {
                            int docId = Integer.parseInt(edoc.getDocId());
                            providerRouting(docId);
                        } catch (NumberFormatException e) {
                            log.error("Invalid document ID: {} - document saved but routing failed",
                                    edoc.getDocId(), e);
                            receivedFax.setStatus(FaxJob.STATUS.ERROR);
                            receivedFax.setStatusString("Imported but routing failed - manual assignment required");
                            // Fall through to deleteFax - content is imported, routing is a separate concern
                        } catch (RuntimeException e) {
                            log.error("Provider routing failed for doc_no={} - document exists but not in provider inbox",
                                    edoc.getDocId(), e);
                            receivedFax.setStatus(FaxJob.STATUS.ERROR);
                            receivedFax.setStatusString("IMPORTED BUT ROUTING FAILED - NEEDS MANUAL ASSIGNMENT");
                            // Fall through to deleteFax
                        }

                        // Acknowledge remote fax per provider policy
                        try {
                            providerClient.deleteFax(faxConfig, receivedFax);
                        } catch (FaxProviderException e) {
                            log.error("Failed to delete remote fax - duplicate may occur on next poll", e);
                        }
                    } else {
                        // Import failed but file is safe in incoming directory for retry
                        receivedFax.setStatus(FaxJob.STATUS.ERROR);
                        receivedFax.setStatusString("Downloaded but import failed - pending retry from incoming directory");
                    }

                    receivedFax.setFile_name(fileName);
                    saveFaxJob(new FaxJob(receivedFax));
                }

            } catch (FaxProviderException e) {
                log.error("Fax provider error for account {} ({}): {}",
                        faxConfig.getFaxUser(), faxConfig.getProviderType(), e.getMessage(), e);
            } catch (IllegalStateException e) {
                log.error("Credential decryption failed for fax account {} ({}) - re-enter password in "
                        + "Administration > Faxes > Configure Fax. Skipping this account.",
                        faxConfig.getFaxUser(), faxConfig.getProviderType(), e);
            } catch (RuntimeException e) {
                log.error("Unexpected error processing faxes for account {} ({}) - continuing with next account: {}",
                        faxConfig.getFaxUser(), faxConfig.getProviderType(), e.getMessage(), e);
            }
        }

    }

    /**
     * Saves a downloaded fax to the incoming directory (quarantine) with validation.
     *
     * <p>Decodes base64 content, validates PDF integrity, and saves with a collision-free
     * filename under {@code FAX_INCOMING_DIR/{configId}/}. Uses a temp file + atomic move
     * pattern to prevent partial writes.</p>
     *
     * @param faxConfig the fax account configuration (provides config ID for subdirectory)
     * @param receivedFax fax metadata from provider (status updated on error)
     * @param faxFile fax content with base64-encoded PDF document
     * @return Path to the saved file in the incoming directory, or null if save failed
     */
    private Path saveToIncoming(FaxConfig faxConfig, FaxJob receivedFax, FaxJob faxFile) {
        File tempFile = null;

        try {
            // Create config-specific subdirectory
            Path configDir = faxIncomingDir.resolve(String.valueOf(faxConfig.getId()));
            Files.createDirectories(configDir);

            // Generate collision-free filename
            String uniqueFilename = generateUniqueFilename(receivedFax.getFile_name());

            // Validate path security
            PathValidationUtils.validatePath(uniqueFilename, configDir.toFile());
            Path targetFile = configDir.resolve(uniqueFilename);

            // Decode to temp file first (atomic write pattern)
            tempFile = Files.createTempFile(configDir, "fax-tmp-", ".pdf").toFile();

            try {
                byte[] decoded = java.util.Base64.getDecoder().decode(faxFile.getDocument());
                Files.write(tempFile.toPath(), decoded);
            } catch (IllegalArgumentException e) {
                throw new FaxProviderException("Base64 decode failed", e);
            }

            if (tempFile.length() == 0) {
                throw new FaxProviderException("Decoded file is empty (0 bytes)");
            }

            int numberOfPages = validateAndCountPages(tempFile);
            if (numberOfPages == 0) {
                throw new FaxProviderException("PDF validation failed - 0 pages");
            }

            // Atomic move to final location within incoming directory
            moveFile(tempFile.toPath(), targetFile);
            tempFile = null; // Moved successfully
            log.info("Fax saved to incoming directory: {}/{}", faxConfig.getId(), uniqueFilename);
            return targetFile;

        } catch (FaxProviderException e) {
            log.error("Fax validation failed: {}", e.getMessage(), e);
            receivedFax.setStatus(FaxJob.STATUS.ERROR);
            receivedFax.setStatusString("PDF validation failed: " + e.getMessage());
            return null;
        } catch (IOException e) {
            log.error("File I/O error saving fax to incoming directory: {}", e.getMessage(), e);
            receivedFax.setStatus(FaxJob.STATUS.ERROR);
            receivedFax.setStatusString("File system error: " + e.getMessage());
            return null;
        } catch (SecurityException e) {
            log.error("SECURITY: Path validation failed for fax: {}", e.getMessage(), e);
            receivedFax.setStatus(FaxJob.STATUS.ERROR);
            receivedFax.setStatusString("Security validation failed - suspicious filename");
            return null;
        } finally {
            if (tempFile != null && tempFile.exists()) {
                if (!tempFile.delete()) {
                    log.warn("Failed to delete temp file: {}", tempFile.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Imports a fax file from the incoming directory into the EMR document system.
     *
     * <p>Moves the file from the incoming directory to DOCUMENT_DIR, creates a document record
     * in the database, and adds it to the document review queue. If import fails, the file
     * is left in (or moved back to) the incoming directory for retry on the next poll cycle.</p>
     *
     * @param incomingFile path to the PDF file in the incoming directory
     * @param faxConfig fax account configuration (provides queue ID)
     * @param receivedFax fax metadata (status updated on error)
     * @return EDoc if import succeeded, null if failed (file remains in incoming dir)
     */
    private EDoc importFromIncoming(Path incomingFile, FaxConfig faxConfig, FaxJob receivedFax) {
        String uniqueFilename = incomingFile.getFileName().toString();

        try {
            // Validate PDF and count pages
            int numberOfPages = validateAndCountPages(incomingFile.toFile());

            // Validate and resolve final path in DOCUMENT_DIR
            File finalFile = PathValidationUtils.validatePath(uniqueFilename, new File(DOCUMENT_DIR));

            // Move from incoming to DOCUMENT_DIR
            moveFile(incomingFile, finalFile.toPath());

            // Create EDoc and register with EMR
            EDoc newDoc = new EDoc("Received Fax", "Received Fax", uniqueFilename, "",
                    DEFAULT_USER, DEFAULT_USER, "", 'A',
                    DateFormatUtils.format(receivedFax.getStamp() != null ? receivedFax.getStamp() : new Date(), "yyyy-MM-dd"),
                    "", "", "demographic", DEFAULT_USER, numberOfPages);
            newDoc.setDocPublic("0");
            newDoc.setContentType("application/pdf");
            newDoc.setNumberOfPages(numberOfPages);

            String doc_no = EDocUtil.addDocumentSQL(newDoc);
            if (doc_no == null || doc_no.trim().isEmpty()) {
                log.error("Failed to create document record for fax - moving file back to incoming directory");
                receivedFax.setStatus(FaxJob.STATUS.ERROR);
                receivedFax.setStatusString("FAILED TO CREATE DOCUMENT RECORD - pending retry");
                // Move back to incoming for retry
                try {
                    moveFile(finalFile.toPath(), incomingFile);
                } catch (IOException moveBackEx) {
                    log.error("CRITICAL: Cannot move fax back to incoming directory. File at: {}",
                            finalFile.getAbsolutePath(), moveBackEx);
                }
                return null;
            }
            log.info("Registered fax in EMR: doc_id={}, pages={}", doc_no, numberOfPages);

            // Add to document queue for staff review
            Integer queueId = faxConfig.getQueue();
            if (queueId == null || queueId < 1) {
                queueId = 1;
                log.warn("FaxConfig has no valid queue ID, defaulting to queue 1");
            }
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
            log.error("PDF validation failed during import: {}", e.getMessage(), e);
            receivedFax.setStatus(FaxJob.STATUS.ERROR);
            receivedFax.setStatusString("PDF validation failed on import: " + e.getMessage());
            return null;
        } catch (IOException e) {
            log.error("File I/O error during import from incoming: {}", e.getMessage(), e);
            receivedFax.setStatus(FaxJob.STATUS.ERROR);
            receivedFax.setStatusString("File system error during import - pending retry");
            return null;
        } catch (SecurityException e) {
            log.error("SECURITY: Path validation failed during import: {}", e.getMessage(), e);
            receivedFax.setStatus(FaxJob.STATUS.ERROR);
            receivedFax.setStatusString("Security validation failed during import");
            return null;
        }
    }

    /**
     * Retries importing any PDF files left in the incoming directory from previous failed cycles.
     *
     * <p>Scans each config-ID subdirectory under FAX_INCOMING_DIR for PDF files and attempts
     * to import them into the EMR. Successfully imported files are moved to DOCUMENT_DIR;
     * files that still fail are left in place for the next retry cycle.</p>
     *
     * @param faxConfigList all fax configurations (for queue routing lookup)
     */
    private void retryPendingImports(List<FaxConfig> faxConfigList) {
        if (faxIncomingDir == null || !Files.exists(faxIncomingDir)) {
            return;
        }

        Map<Integer, FaxConfig> configMap = new HashMap<>();
        for (FaxConfig fc : faxConfigList) {
            if (fc.getId() != null) {
                configMap.put(fc.getId(), fc);
            }
        }

        try (DirectoryStream<Path> configDirs = Files.newDirectoryStream(faxIncomingDir)) {
            for (Path configDir : configDirs) {
                if (!Files.isDirectory(configDir)) {
                    continue;
                }

                Integer configId;
                try {
                    configId = Integer.parseInt(configDir.getFileName().toString());
                } catch (NumberFormatException e) {
                    continue; // Not a config ID directory
                }

                FaxConfig faxConfig = configMap.get(configId);
                if (faxConfig == null) {
                    log.warn("Pending fax files in incoming/{} but no matching FaxConfig - skipping", configId);
                    continue;
                }

                try (DirectoryStream<Path> pdfFiles = Files.newDirectoryStream(configDir, "*.pdf")) {
                    for (Path pdfFile : pdfFiles) {
                        // Skip temp files from interrupted saves
                        if (pdfFile.getFileName().toString().startsWith("fax-tmp-")) {
                            continue;
                        }

                        log.info("Retrying import of pending fax: {}/{}", configId, pdfFile.getFileName());

                        FaxJob retryFax = new FaxJob();
                        retryFax.setFile_name(pdfFile.getFileName().toString());
                        retryFax.setDirection(FaxJob.Direction.IN);
                        try {
                            retryFax.setStamp(new Date(Files.getLastModifiedTime(pdfFile).toMillis()));
                        } catch (IOException e) {
                            retryFax.setStamp(new Date());
                        }

                        EDoc edoc = importFromIncoming(pdfFile, faxConfig, retryFax);
                        if (edoc != null) {
                            try {
                                providerRouting(Integer.parseInt(edoc.getDocId()));
                            } catch (RuntimeException e) {
                                log.error("Routing failed for retried fax import doc_no={}: {}",
                                        edoc.getDocId(), e.getMessage(), e);
                                retryFax.setStatus(FaxJob.STATUS.ERROR);
                                retryFax.setStatusString("IMPORTED ON RETRY BUT ROUTING FAILED - NEEDS MANUAL ASSIGNMENT");
                            }

                            if (retryFax.getStatus() == null) {
                                retryFax.setStatus(FaxJob.STATUS.RECEIVED);
                            }
                            retryFax.setFile_name(edoc.getFileName());
                            saveFaxJob(retryFax);
                            log.info("Successfully imported pending fax on retry: {}", pdfFile.getFileName());
                        } else {
                            // Still failing - leave for next cycle, don't create duplicate FaxJob records
                            log.warn("Retry import still failing for: {}/{}", configId, pdfFile.getFileName());
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error scanning incoming fax directory for retry: {}", e.getMessage(), e);
        }
    }

    /**
     * Returns a list of pending (not yet imported) fax files in the incoming directory.
     *
     * <p>Used by the admin GUI to show visibility into faxes that have been downloaded
     * from the provider but not yet fully imported into the EMR document system.</p>
     *
     * @return list of maps with keys: fileName, sizeBytes, lastModifiedMs, configId
     */
    public List<Map<String, Object>> listPendingIncomingFaxes() {
        List<Map<String, Object>> pending = new ArrayList<>();

        if (faxIncomingDir == null || !Files.exists(faxIncomingDir)) {
            return pending;
        }

        try (DirectoryStream<Path> configDirs = Files.newDirectoryStream(faxIncomingDir)) {
            for (Path configDir : configDirs) {
                if (!Files.isDirectory(configDir)) {
                    continue;
                }

                String configIdStr = configDir.getFileName().toString();
                try {
                    Integer.parseInt(configIdStr);
                } catch (NumberFormatException e) {
                    continue;
                }

                try (DirectoryStream<Path> pdfFiles = Files.newDirectoryStream(configDir, "*.pdf")) {
                    for (Path pdfFile : pdfFiles) {
                        if (pdfFile.getFileName().toString().startsWith("fax-tmp-")) {
                            continue;
                        }

                        Map<String, Object> entry = new HashMap<>();
                        entry.put("fileName", pdfFile.getFileName().toString());
                        entry.put("configId", configIdStr);
                        try {
                            entry.put("sizeBytes", Files.size(pdfFile));
                            entry.put("lastModifiedMs", Files.getLastModifiedTime(pdfFile).toMillis());
                        } catch (IOException e) {
                            log.debug("Cannot read file metadata for pending fax {}: {}", pdfFile.getFileName(), e.getMessage());
                            entry.put("sizeBytes", 0L);
                            entry.put("lastModifiedMs", 0L);
                        }
                        pending.add(entry);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error listing pending incoming faxes: {}", e.getMessage(), e);
        }

        return pending;
    }

    /**
     * Moves a file, preferring atomic move but falling back to copy+delete for cross-filesystem moves.
     *
     * @param source source path
     * @param target target path
     * @throws IOException if the move fails
     */
    private void moveFile(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            log.warn("Atomic move not supported from {} to {} - falling back to copy+delete (cross-filesystem?)",
                    source.getParent(), target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            Files.delete(source);
        }
    }

    /**
     * Generates collision-free unique filename.
     *
     * <p>Format: {@code yyyyMMdd-HHmmss-NNNNN-originalname.pdf} where NNNNN is an atomic
     * sequence counter that prevents same-second collisions across threads.</p>
     *
     * @param originalFilename Original filename from fax provider (may not be unique)
     * @return Guaranteed unique filename
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
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "fax-" + System.currentTimeMillis() + ".pdf";
        }

        String sanitized = filename.trim()
            .replace("..", "")
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

        if (!sanitized.toLowerCase().endsWith(".pdf")) {
            sanitized += ".pdf";
        }

        return sanitized;
    }

    /**
     * Validates PDF and counts pages using iTextPDF.
     *
     * @param pdfFile PDF file to validate
     * @return number of pages if valid PDF
     * @throws FaxProviderException if PDF is corrupted, password-protected, or has 0 pages
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

        } catch (com.lowagie.text.exceptions.BadPasswordException e) {
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
     * @param saveFax FaxJob to persist
     * @return FaxJob ID from database
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
     */
    private void providerRouting(Integer labNo) {
        ProviderLabRoutingModel providerLabRouting = new ProviderLabRoutingModel();
        providerLabRouting.setLabNo(labNo);
        providerRouting(providerLabRouting);
    }

    /**
     * Routes fax document to provider inbox system using UNCLAIMED strategy.
     *
     * <p>Incoming faxes are routed to the UNCLAIMED inbox (provider_no="0") rather than to
     * specific providers. This ensures all providers see the unclaimed indicator and staff
     * can manually assign faxes after review.</p>
     *
     * @param providerLabRouting Routing model with labNo (document ID) set
     */
    private void providerRouting(ProviderLabRoutingModel providerLabRouting) {
        providerLabRouting.setLabType(ProviderLabRoutingDao.LAB_TYPE.DOC.name());
        providerLabRouting.setProviderNo(ProviderLabRoutingDao.UNCLAIMED_PROVIDER);
        providerLabRouting.setStatus(ProviderLabRoutingDao.STATUS.N.name());
        providerLabRouting.setTimestamp(new Date(System.currentTimeMillis()));
        providerLabRoutingDao.persist(providerLabRouting);

        Integer id = providerLabRouting.getId();
        if (id == null || id < 1) {
            throw new RuntimeException("Failed to add Fax document id " + providerLabRouting.getLabNo()
                    + " to provider lab routing - database did not generate routing ID");
        }
    }

}
