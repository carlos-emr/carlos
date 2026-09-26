/**
 * Copyright (c) 2012- Centre de Medecine Integree
 * <p>
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
 * This software was written for
 * Centre de Medecine Integree, Saint-Laurent, Quebec, Canada to be provided
 * as part of the OSCAR McMaster EMR System
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.documentManager;


import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import org.openpdf.text.Document;
import org.openpdf.text.pdf.PdfCopy;
import org.openpdf.text.pdf.PdfName;
import org.openpdf.text.pdf.PdfNumber;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfStamper;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.LogSafe;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Utility class for managing incoming documents in the CARLOS EMR document management system.
 *
 * <p>Provides operations for incoming PDF documents including:
 * <ul>
 *   <li>Page rotation (single page and all pages) using OpenPDF PdfStamper</li>
 *   <li>Page deletion with optional recycle bin support</li>
 *   <li>Page extraction into separate PDF files using OpenPDF PdfCopy</li>
 *   <li>Complete PDF deletion with recycle bin support</li>
 *   <li>File path construction and validation for incoming document queues</li>
 *   <li>User preference management for document queue, view mode, and entry mode</li>
 * </ul>
 *
 * <p>All file path operations are secured against path traversal attacks using
 * {@link PathValidationUtils}. Document directories are organized by queue ID
 * and subdirectory type (Fax, Mail, File, Refile).
 *
 * @see PathValidationUtils
 * @see EDocUtil
 * @since 2013-05-12
 */
public final class IncomingDocUtil {
    private static final String INCOMING_DOCUMENT_DIR_PROPERTY = "INCOMINGDOCUMENT_DIR";
    private static final Logger logger = MiscUtils.getLogger();
    /** Naming of the scratch files page operations write next to the queued document. */
    private static final String SCRATCH_PREFIX = ".carlos-";
    private static final String SCRATCH_SUFFIX = ".tmp";
    /**
     * A scratch file older than this belongs to an operation whose process died before its
     * {@code finally} ran: a page operation takes seconds, never an hour. Such a file is a
     * partial or complete copy of a queued document, invisible in the {@code *.pdf} listing, so
     * it is swept rather than left for good.
     */
    private static final long STALE_SCRATCH_AGE_MILLIS = 60L * 60 * 1000;
    
    /**
     * Validates that a request-controlled path segment is exactly one path
     * component. Unlike PathValidationUtils.validatePath(), this preserves the
     * original value and rejects path separators instead of stripping them.
     */
    private static String validatePathComponent(String pathComponent, String label) {
        return PathValidationUtils.validatePathComponent(pathComponent, label);
    }

    private static String validateIncomingDocumentDir(String pdfDir) {
        String validatedPdfDir = validatePathComponent(pdfDir, "pdfDir");
        if (validatedPdfDir.equals("Fax")
                || validatedPdfDir.equals("Mail")
                || validatedPdfDir.equals("File")
                || validatedPdfDir.equals("Refile")) {
            return validatedPdfDir;
        }
        throw new IllegalArgumentException("Invalid pdfDir: must be one of Fax, Mail, File, or Refile");
    }

    private static String addPdfNameSuffix(String pdfName, String suffix) {
        if (pdfName == null || !pdfName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new FileValidationException("Incoming document names must end in .pdf");
        }

        int extensionIndex = pdfName.length() - 4;
        return pdfName.substring(0, extensionIndex) + suffix + pdfName.substring(extensionIndex);
    }

    /**
     * Creates a fresh scratch file for rewriting a queued document. It lives in the queue's own
     * directory so the final {@link Files#move} is a same-filesystem rename, and it has a unique
     * name. The old fixed {@code "T" + name} scratch path overwrote any queued document that
     * happened to carry that name. The {@code .tmp} suffix keeps it out of the queue listing,
     * which shows only {@code *.pdf}.
     *
     * @param queueDir the validated queue directory
     * @param permissions the source document's permissions, applied so the replacement keeps
     *        them; {@code null} leaves the owner-only default
     * @return the new, empty scratch file
     * @throws IOException if the scratch file cannot be created
     */
    // queueDir comes from getIncomingDocumentFilePath (validated against INCOMINGDOCUMENT_DIR); the name is generated here
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "queueDir comes from getIncomingDocumentFilePath (validated against INCOMINGDOCUMENT_DIR); the name is generated here")
    private static File newScratchFile(File queueDir, Set<PosixFilePermission> permissions) throws IOException {
        removeStaleScratchFiles(queueDir);
        Path scratch = Files.createTempFile(queueDir.toPath(), SCRATCH_PREFIX, SCRATCH_SUFFIX);
        if (permissions != null) {
            try {
                Files.setPosixFilePermissions(scratch, permissions);
            } catch (IOException | UnsupportedOperationException e) {
                MiscUtils.getLogger().warn("Could not copy a queued document's permissions to its scratch file");
            }
        }
        return scratch.toFile();
    }

    /**
     * Removes scratch files that an interrupted page operation left in the given directory. Only
     * files carrying the scratch naming and older than {@link #STALE_SCRATCH_AGE_MILLIS} are
     * touched, so a scratch file an operation is writing right now (seconds old) is never taken.
     * Bounded to the one directory, best effort, and never throws: the operation or listing
     * that triggered it must not fail because an orphan could not be removed.
     *
     * @param dir a validated queue or recycle directory
     */
    private static void removeStaleScratchFiles(File dir) {
        File[] scratchFiles = dir.listFiles((parent, name) -> name.startsWith(SCRATCH_PREFIX) && name.endsWith(SCRATCH_SUFFIX));
        if (scratchFiles == null) {
            return;
        }
        long cutoff = System.currentTimeMillis() - STALE_SCRATCH_AGE_MILLIS;
        int removed = 0;
        for (File scratch : scratchFiles) {
            long lastModified = scratch.lastModified();
            // 0 means the time could not be read; leave such a file rather than guess at its age.
            if (!scratch.isFile() || lastModified == 0 || lastModified >= cutoff) {
                continue;
            }
            try {
                if (Files.deleteIfExists(scratch.toPath())) {
                    removed++;
                }
            } catch (IOException e) {
                logger.warn("Could not remove a stale incoming-document scratch file left by an interrupted page operation");
            }
        }
        if (removed > 0) {
            logger.warn("Removed {} stale incoming-document scratch file(s) left by an interrupted page operation", removed);
        }
    }

    /**
     * The document's own POSIX permissions, exactly as found, or null where the filesystem does
     * not support them. This is the set put back on the source after a failed operation and
     * onto the replacement after a successful one; it is never used for a scratch file, which
     * needs {@link #writable(Set)}.
     */
    private static Set<PosixFilePermission> permissionsOf(File file) {
        try {
            return Files.getPosixFilePermissions(file.toPath());
        } catch (IOException | UnsupportedOperationException e) {
            return null;
        }
    }

    /**
     * A scratch file's permissions: the source's plus owner read/write, so the copy can be
     * written even when the queued document itself is read-only. Kept separate from the source's
     * own set so a read-only document is not restored, or replaced, as a writable one.
     */
    private static Set<PosixFilePermission> writable(Set<PosixFilePermission> permissions) {
        if (permissions == null) {
            return null;
        }
        Set<PosixFilePermission> writable = EnumSet.noneOf(PosixFilePermission.class);
        writable.addAll(permissions);
        writable.add(PosixFilePermission.OWNER_READ);
        writable.add(PosixFilePermission.OWNER_WRITE);
        return writable;
    }

    /** Best-effort removal of a scratch or partial output file after a failed page operation. */
    private static void deleteQuietly(File file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            MiscUtils.getLogger().warn("Could not remove a scratch file left by a failed incoming-document page operation");
        }
    }

    /**
     * Puts a source document's own permissions back after a failed operation marked it read-only.
     * Where the filesystem has no POSIX permissions the only change made was
     * {@code setReadOnly()}, so the only thing to undo is that.
     */
    private static void restorePermissions(File file, Set<PosixFilePermission> permissions) {
        if (permissions == null) {
            if (!file.setWritable(true, true)) {
                MiscUtils.getLogger().warn("Could not make a queued document writable again after a failed page operation");
            }
            return;
        }
        applyPermissions(file, permissions);
    }

    /**
     * Gives the file that now holds a queued document the document's own permissions. After a
     * successful operation that file is the moved scratch copy, which was created writable so it
     * could be written; a document that was read-only in the queue stays read-only.
     */
    private static void applyPermissions(File file, Set<PosixFilePermission> permissions) {
        if (permissions == null) {
            return;
        }
        try {
            Files.setPosixFilePermissions(file.toPath(), permissions);
        } catch (IOException | UnsupportedOperationException e) {
            MiscUtils.getLogger().warn("Could not put a queued document's permissions on the file that now holds it");
        }
    }

    /**
     * Validates that a constructed path is within the allowed base directory.
     * Delegates to PathValidationUtils for consistent validation.
     * @param basePath The base directory path
     * @param targetPath The path to validate
     * @return true if the path is within bounds, false otherwise
     */
    // PATH_TRAVERSAL_IN: this is a containment predicate, not a file access — both File objects exist only to be canonicalized and compared, and validateExistingPath() is what decides the result.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
        justification = "containment predicate: both File objects exist only to be canonicalized and compared by "
            + "PathValidationUtils.validateExistingPath(); nothing is opened, read, or written here.")
    private static boolean isPathWithinBounds(String basePath, String targetPath) {
        try {
            File baseDir = new File(basePath).getCanonicalFile();
            File targetFile = new File(targetPath).getCanonicalFile();
            PathValidationUtils.validateExistingPath(targetFile, baseDir);
            return true;
        } catch (SecurityException | IOException e) {
            logger.error("Error validating path bounds", e);
            return false;
        }
    }

    /** List of formatted modification dates corresponding to PDF files returned by {@link #getDocList(String)}. */
    private final List<String> pdfListModifiedDate = new ArrayList<>();

    /** Comparator that sorts files by last-modified timestamp in ascending order. */
    private static final Comparator<File> lastModified = new Comparator<File>() {
        @Override
        public int compare(File o1, File o2) {
            return o1.lastModified() == o2.lastModified() ? 0 : (o1.lastModified() > o2.lastModified() ? 1 : -1);
        }
    };

    /**
     * Returns the list of formatted modification dates for PDF files found by the last
     * call to {@link #getDocList(String)}.
     *
     * @return immutable list of date strings in "yyyy-MM-dd HH:mm:ss" format
     */
    public List<String> getPdfListModifiedDate() {
        return List.copyOf(pdfListModifiedDate);
    }

    /**
     * Lists all PDF files in the specified directory, sorted by last-modified date ascending.
     * Also populates the internal {@link #pdfListModifiedDate} list with corresponding
     * formatted timestamps. A queue subdirectory that has not been created yet is treated
     * as an empty queue; a missing or misconfigured INCOMINGDOCUMENT_DIR base still fails
     * loudly so incoming documents cannot silently disappear from the intake screens.
     *
     * @param directory String the absolute path to the directory to scan for PDF files;
     * must resolve inside INCOMINGDOCUMENT_DIR
     * @return list of PDF filenames found in the directory, empty when the
     * queue subdirectory has not been created yet
     * @throws IllegalStateException if INCOMINGDOCUMENT_DIR is not configured
     * @throws SecurityException if the directory resolves outside INCOMINGDOCUMENT_DIR or
     * the configured directory is missing or cannot be listed
     */
    // FindSecBugs PATH_TRAVERSAL_IN: callers pass paths built by getIncomingDocumentFilePath from
    // validated components, and the candidate is containment-checked against INCOMINGDOCUMENT_DIR
    // before any filesystem probe.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "callers pass paths built by getIncomingDocumentFilePath from validated components; candidate is containment-checked against INCOMINGDOCUMENT_DIR before any filesystem probe")
    public List<String> getDocList(String directory) {
        List<String> docList = new ArrayList<>();

        String docName;
        pdfListModifiedDate.clear();

        String incomingRootPath = CarlosProperties.getInstance().getProperty(INCOMING_DOCUMENT_DIR_PROPERTY);
        if (incomingRootPath == null || incomingRootPath.isEmpty()) {
            throw new IllegalStateException("INCOMINGDOCUMENT_DIR property not configured");
        }
        File incomingBaseDir = new File(incomingRootPath);

        FilenameFilter pdfFilter;

        pdfFilter = new FilenameFilter() {
            // The enclosing method's suppression does not reach this filter: SpotBugs analyses
            // the anonymous class as a class of its own, so the containment guard below has to
            // be declared here as well.
            @Override
            @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "candidate is containment-checked against INCOMINGDOCUMENT_DIR by validatePathComponent and validateExistingPath before it is accepted; nothing is read here")
            public boolean accept(File dir, String name) {
                if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    return false;
                }

                // Keep listing consistent with validatePathComponent on the read path:
                // entries that cannot be addressed safely must not appear as broken rows.
                try {
                    validatePathComponent(name, "queued PDF filename");
                    PathValidationUtils.validateExistingPath(new File(dir, name), incomingBaseDir);
                    return true;
                } catch (SecurityException e) {
                    return false;
                }
            }
        };

        // A queue subdirectory is only created by the first upload or fax import, so a
        // never-used queue has no directory yet. That is an empty queue, not a
        // configuration error — validating it as one sent fresh installs to the error page.
        // Only the missing CHILD is an empty queue: when the base directory itself is
        // absent (config typo, unmounted volume), rendering every queue as empty would
        // hide accumulating incoming documents from intake staff, so that still fails
        // loudly below. The candidate is containment-validated before any probe.
        File queueDirCandidate = PathValidationUtils.validateChildPath(new File(directory), incomingBaseDir);
        // Files.notExists is true only when nonexistence can be established. File.exists
        // also returns false when access is denied, which would incorrectly hide an
        // inaccessible queue as an unused/empty one instead of taking the loud path below.
        if (incomingBaseDir.isDirectory() && Files.notExists(queueDirCandidate.toPath())) {
            // Logged so an operator can tell "never used" apart from "the queue volume
            // vanished" without having to reason from an empty screen.
            logger.debug("Incoming queue directory not created yet, reporting empty queue");
            return docList;
        }

        File dir = PathValidationUtils.validateConfiguredDirectory(directory, "incoming document directory");
        // The listing shows only *.pdf, so an orphaned scratch file would otherwise sit here unseen.
        removeStaleScratchFiles(dir);
        File[] listOfFiles = dir.listFiles(pdfFilter);
        if (listOfFiles == null) {
            logger.error("Unable to list incoming document directory: {}",
                    LogSafe.sanitize(dir.getPath())); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            throw new SecurityException("Unable to list incoming document directory");
        }

        Arrays.sort(listOfFiles, lastModified);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (File file : listOfFiles) {
            if (file.isFile()) {
                docName = file.getName();
                long dateTime = file.lastModified();
                Date d = new Date(dateTime);
                String dateString = dateFormat.format(d);
                docList.add(docName);
                pdfListModifiedDate.add(dateString);
            }
        }
        return docList;
    }

    /**
     * Returns the number of pages in the specified PDF document using OpenPDF PdfReader.
     *
     * @param queueId String the incoming document queue identifier
     * @param pdfDir String the subdirectory type (Fax, Mail, File, or Refile)
     * @param pdfName String the PDF filename
     * @return int the number of pages, or 0 if the file cannot be read
     */
    public static int getNumOfPages(String queueId, String pdfDir, String pdfName) {
        String filePath = getIncomingDocumentFilePathName(queueId, pdfDir, pdfName);
        int numOfPages = 0;
        try (PdfReader reader = new PdfReader(filePath)) {
            numOfPages = reader.getNumberOfPages();
        } catch (org.openpdf.text.exceptions.BadPasswordException e) {
            MiscUtils.getLogger().error("Cannot read page count - PDF is password-protected: {}",
                    LogSafe.sanitize(filePath), e);
        } catch (IOException e) {
            MiscUtils.getLogger().error("Cannot read page count for PDF file: {}", LogSafe.sanitize(filePath), e);
        }
        return numOfPages;
    }

    /**
     * Constructs and validates the full file path for an incoming document.
     * Validates the PDF name against path traversal and ensures the resulting
     * path is within the configured INCOMINGDOCUMENT_DIR.
     *
     * @param queueId String the incoming document queue identifier
     * @param pdfDir String the subdirectory type (Fax, Mail, File, or Refile)
     * @param pdfName String the PDF filename
     * @return String the validated full file path
     * @throws IllegalArgumentException if pdfName contains path traversal sequences
     * @throws SecurityException if the resolved path is outside the allowed directory
     */
    // validateStrictFileName() rejects traversal sequences in pdfName; validateExistingPath() then confirms the resolved path stays inside INCOMINGDOCUMENT_DIR.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
        justification = "validateStrictFileName() rejects traversal sequences in pdfName; "
            + "validateExistingPath() then confirms the resolved path stays inside INCOMINGDOCUMENT_DIR.")
    public static String getIncomingDocumentFilePathName(String queueId, String pdfDir, String pdfName) {
        // Validate pdfName without normalizing it: this resolves an EXISTING queued file,
        // so the on-disk name must be preserved exactly. Normalizing here rewrote names
        // containing spaces or parentheses (e.g. "scan (1).pdf" -> "scan_1.pdf") and made
        // every such uploaded document unresolvable — viewer, page count, rotate, delete.
        pdfName = validatePathComponent(pdfName, "pdfName");

        // Component validation preserves the name but, unlike the normalizing validator this
        // replaced, carries no extension allowlist. Queue contents are PDFs only (the listing
        // filter and the upload action both enforce that), so keep the dangerous-extension
        // door shut here rather than letting request-supplied names name anything else.
        if (!pdfName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new FileValidationException("Incoming document names must end in .pdf");
        }

        String filePathName = getIncomingDocumentFilePath(queueId, pdfDir);
        
        // Use File constructor to safely combine paths
        File file = new File(filePathName, pdfName);
        
        // Validate the final path is within bounds
        File baseDir = new File(CarlosProperties.getInstance().getProperty(INCOMING_DOCUMENT_DIR_PROPERTY));
        return PathValidationUtils.validateExistingPath(file, baseDir).getPath();
    }

    /**
     * Constructs, validates, and ensures the directory exists for the full incoming
     * document file path. Creates intermediate directories if they do not exist.
     *
     * @param queueId String the incoming document queue identifier
     * @param pdfDir String the subdirectory type (Fax, Mail, File, or Refile)
     * @param pdfName String the PDF filename
     * @return String the validated full file path with directories created
     * @throws IllegalArgumentException if pdfName contains path traversal sequences
     * @throws SecurityException if the resolved path is outside the allowed directory
     */
    // validatePathComponent() rejects traversal sequences in pdfName; the resolved path is then contained inside INCOMINGDOCUMENT_DIR.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
        justification = "validatePathComponent() rejects traversal sequences in pdfName; "
            + "the resolved path is then contained inside INCOMINGDOCUMENT_DIR.")
    public static String getAndCreateIncomingDocumentFilePathName(String queueId, String pdfDir, String pdfName) {
        // Validate pdfName to prevent path traversal
        pdfName = validatePathComponent(pdfName, "pdfName");
        
        String filePathName = getAndCreateIncomingDocumentFilePath(queueId, pdfDir);
        
        // Use File constructor to safely combine paths
        File file = new File(filePathName, pdfName);
        
        // Validate the final path is within bounds
        File baseDir = new File(CarlosProperties.getInstance().getProperty(INCOMING_DOCUMENT_DIR_PROPERTY));
        return PathValidationUtils.validateExistingPath(file, baseDir).getPath();
    }

    /**
     * Returns the path to the deleted-documents directory for the given queue and document type.
     * The deleted directory is named "{pdfDir}_deleted" (e.g., "Fax_deleted"). Creates
     * the directory if it does not exist.
     *
     * @param queueId String the incoming document queue identifier
     * @param pdfDir String the subdirectory type (Fax, Mail, File, or Refile)
     * @return String the validated path to the deleted-documents directory
     * @throws IllegalStateException if INCOMINGDOCUMENT_DIR is not configured
     * @throws IllegalArgumentException if queueId or pdfDir contains invalid characters
     * @throws SecurityException if the resolved path is outside the allowed directory
     */
    // pdfDir is whitelisted to {Fax, Mail, File, Refile}; PathValidationUtils.validateExistingPath() then confirms the path stays inside INCOMINGDOCUMENT_DIR.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
        justification = "pdfDir is whitelisted to {Fax, Mail, File, Refile}; "
            + "PathValidationUtils.validateExistingPath() then confirms the path stays inside INCOMINGDOCUMENT_DIR.")
    public static String getIncomingDocumentDeletedFilePath(String queueId, String pdfDir) {
        String filePath;

        filePath = CarlosProperties.getInstance().getProperty(INCOMING_DOCUMENT_DIR_PROPERTY);
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalStateException("INCOMINGDOCUMENT_DIR property not configured");
        }

        if (!filePath.endsWith(File.separator)) {
            filePath += File.separator;
        }
        
        // Validate queueId to prevent path traversal
        queueId = validatePathComponent(queueId, "queueId");
        
        filePath += queueId + File.separator;
        
        // Validate pdfDir and restrict to allowed values
        if (pdfDir != null && !pdfDir.isEmpty()) {
            pdfDir = validateIncomingDocumentDir(pdfDir);
            try {
                File baseDir = new File(CarlosProperties.getInstance().getProperty(INCOMING_DOCUMENT_DIR_PROPERTY));
                File deletedPathDir = new File(filePath, pdfDir + "_deleted");

                // Validate path is within bounds using PathValidationUtils
                deletedPathDir = PathValidationUtils.validateExistingPath(deletedPathDir, baseDir);

                File canonicalDeletedDir = deletedPathDir.getCanonicalFile();

                if (!canonicalDeletedDir.exists()) {
                    canonicalDeletedDir.mkdirs();
                }

                filePath = canonicalDeletedDir.getPath();
            } catch (IOException e) {
                throw new SecurityException("Failed to validate deleted directory path", e);
            }
        }
        
        File baseDir = new File(CarlosProperties.getInstance().getProperty(INCOMING_DOCUMENT_DIR_PROPERTY));
        return PathValidationUtils.validateExistingPath(new File(filePath), baseDir).getPath();
    }

    /**
     * Constructs the directory path for incoming documents based on queue ID and document type.
     * The path format is: {INCOMINGDOCUMENT_DIR}/{queueId}/{pdfDir}
     *
     * @param queueId String the incoming document queue identifier
     * @param pdfDir String the subdirectory type (Fax, Mail, File, or Refile), or null for queue root
     * @return String the validated directory path
     * @throws IllegalStateException if INCOMINGDOCUMENT_DIR is not configured
     * @throws IllegalArgumentException if queueId or pdfDir contains invalid values
     */
    // PATH_TRAVERSAL_IN: queueId goes through validatePathComponent and pdfDir through the {Fax, Mail, File, Refile} allowlist; validateExistingPath() then confirms containment in INCOMINGDOCUMENT_DIR.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
        justification = "queueId is validated by validatePathComponent() and pdfDir is restricted to the "
            + "{Fax, Mail, File, Refile} allowlist; PathValidationUtils.validateExistingPath() then confirms the "
            + "assembled path stays inside INCOMINGDOCUMENT_DIR.")
    public static String getIncomingDocumentFilePath(String queueId, String pdfDir) {
        String filePath;

        filePath = CarlosProperties.getInstance().getProperty(INCOMING_DOCUMENT_DIR_PROPERTY);

        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalStateException("INCOMINGDOCUMENT_DIR property not configured");
        }

        if (!filePath.endsWith(File.separator)) {
            filePath += File.separator;
        }
        
        // Validate queueId to prevent path traversal
        queueId = validatePathComponent(queueId, "queueId");

        filePath += queueId + File.separator;

        // Validate pdfDir and restrict to allowed values
        if (pdfDir != null && !pdfDir.isEmpty()) {
            filePath = filePath + validateIncomingDocumentDir(pdfDir);
        }

        File baseDir = new File(CarlosProperties.getInstance().getProperty(INCOMING_DOCUMENT_DIR_PROPERTY));
        return PathValidationUtils.validateExistingPath(new File(filePath), baseDir).getPath();
    }

    /**
     * Constructs the directory path for incoming documents and creates the directory
     * structure if it does not already exist.
     *
     * @param queueId String the incoming document queue identifier
     * @param pdfDir String the subdirectory type (Fax, Mail, File, or Refile), or null for queue root
     * @return String the canonical directory path with directories created
     * @throws IllegalStateException if INCOMINGDOCUMENT_DIR is not configured
     * @throws SecurityException if the resolved path is outside the allowed directory
     */
    // PATH_TRAVERSAL_IN: the path comes from getIncomingDocumentFilePath (already validated), and is re-checked by isPathWithinBounds and validateConfiguredDirectory before any directory is created.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
        justification = "the path is produced by the already-validating getIncomingDocumentFilePath() and is "
            + "re-checked by isPathWithinBounds() and PathValidationUtils.validateConfiguredDirectory() before any "
            + "directory is created.")
    public static String getAndCreateIncomingDocumentFilePath(String queueId, String pdfDir) {
        String filePath = getIncomingDocumentFilePath(queueId, pdfDir);
        
        // Get the base directory for validation
        String baseDir = CarlosProperties.getInstance().getProperty(INCOMING_DOCUMENT_DIR_PROPERTY);
        if (baseDir == null || baseDir.isEmpty()) {
            throw new IllegalStateException("INCOMINGDOCUMENT_DIR property not configured");
        }
        
        // Validate the constructed path is within bounds
        if (!isPathWithinBounds(baseDir, filePath)) {
            throw new SecurityException("Attempted path traversal detected");
        }
        
        File filePathDir = new File(filePath);
        
        // Validate path is within bounds using PathValidationUtils
        try {
            // The configured root may be a mounted document volume. Never recreate a
            // missing root locally: doing so would make successful writes disappear when
            // the real volume is remounted. Only queue children are lazy-created.
            File baseDirFile = PathValidationUtils.validateConfiguredDirectory(
                    baseDir, "incoming document root");
            filePathDir = PathValidationUtils.validateExistingPath(filePathDir, baseDirFile);

            File canonicalDir = filePathDir.getCanonicalFile();

            if (!canonicalDir.isDirectory()
                    && !canonicalDir.mkdirs()
                    && !canonicalDir.isDirectory()) {
                logger.error("Failed to create incoming document directory: {}", LogSafe.sanitize(canonicalDir.getPath())); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
                throw new IllegalStateException("Failed to create incoming document directory");
            }

            return canonicalDir.getPath();
        } catch (IOException e) {
            throw new SecurityException("Failed to validate directory path", e);
        }
    }

    /**
     * Rotates a single page of a PDF document by the specified number of degrees.
     * Uses OpenPDF PdfStamper to modify the page rotation in-place. The original
     * file's last-modified timestamp is preserved via a temp-file rename strategy.
     *
     * @param queueId String the incoming document queue identifier
     * @param myPdfDir String the subdirectory type (Fax, Mail, File, or Refile)
     * @param myPdfName String the PDF filename
     * @param MyPdfPageNumber String the 1-based page number to rotate
     * @param degrees int the rotation angle in degrees (e.g., 90, 180, -90)
     * @throws Exception if the rotation, file deletion, or rename operation fails
     */
    // path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public static void rotatePage(String queueId, String myPdfDir, String myPdfName, String MyPdfPageNumber, int degrees) throws Exception {
        myPdfName = validatePathComponent(myPdfName, "myPdfName");

        String basePath = getIncomingDocumentFilePath(queueId, myPdfDir);
        String filePathName = getIncomingDocumentFilePathName(queueId, myPdfDir, myPdfName);

        File f = PathValidationUtils.validateExistingPath(new File(filePathName), new File(basePath));
        filePathName = f.getPath();
        long lastModified = f.lastModified();

        Set<PosixFilePermission> permissions = permissionsOf(f);
        File scratch = newScratchFile(new File(basePath), writable(permissions));
        boolean replaced = false;
        try {
            try (PdfReader reader = new PdfReader(filePathName);
                 OutputStream fos = Files.newOutputStream(scratch.toPath())) {
                int rotatedegrees = (reader.getPageRotation(Integer.parseInt(MyPdfPageNumber)) + degrees) % 360;
                reader.getPageN(Integer.parseInt(MyPdfPageNumber)).put(PdfName.ROTATE, new PdfNumber(rotatedegrees));
                PdfStamper stp = new PdfStamper(reader, fos);
                stp.close();
            }
            // One move instead of delete-then-rename: a failed rename after the delete lost the
            // queued document outright.
            Files.move(scratch.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            replaced = true;
        } finally {
            if (!replaced) {
                deleteQuietly(scratch);
            }
        }
        // The replacement is the scratch copy, created writable; give it the document's own
        // permissions so a read-only queue entry stays read-only.
        applyPermissions(f, permissions);
        if (!f.setLastModified(lastModified)) {
            MiscUtils.getLogger().warn("Could not restore the last modified time of a queued document after rotating a page");
        }
    }


    /**
     * Rotates all pages of a PDF document by the specified number of degrees.
     * Uses OpenPDF PdfStamper to modify page rotations in-place. The original
     * file's last-modified timestamp is preserved via a temp-file rename strategy.
     *
     * @param queueId String the incoming document queue identifier
     * @param myPdfDir String the subdirectory type (Fax, Mail, File, or Refile)
     * @param myPdfName String the PDF filename
     * @param degrees int the rotation angle in degrees (e.g., 90, 180, -90)
     * @throws Exception if the rotation, file deletion, or rename operation fails
     */
    // path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public static void rotateAlPages(String queueId, String myPdfDir, String myPdfName, int degrees) throws Exception {
        myPdfName = validatePathComponent(myPdfName, "myPdfName");

        String basePath = getIncomingDocumentFilePath(queueId, myPdfDir);
        String filePathName = getIncomingDocumentFilePathName(queueId, myPdfDir, myPdfName);

        File f = PathValidationUtils.validateExistingPath(new File(filePathName), new File(basePath));
        filePathName = f.getPath();
        long lastModified = f.lastModified();

        Set<PosixFilePermission> permissions = permissionsOf(f);
        File scratch = newScratchFile(new File(basePath), writable(permissions));
        boolean replaced = false;
        try {
            try (PdfReader reader = new PdfReader(filePathName);
                 OutputStream fos = Files.newOutputStream(scratch.toPath())) {
                for (int p = 1; p <= reader.getNumberOfPages(); ++p) {
                    int rotatedegrees = (reader.getPageRotation(p) + degrees) % 360;
                    reader.getPageN(p).put(PdfName.ROTATE, new PdfNumber(rotatedegrees));
                }
                PdfStamper stp = new PdfStamper(reader, fos);
                stp.close();
            }
            // One move instead of delete-then-rename, as in rotatePage.
            Files.move(scratch.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            replaced = true;
        } finally {
            if (!replaced) {
                deleteQuietly(scratch);
            }
        }
        // The replacement is the scratch copy, created writable; give it the document's own
        // permissions so a read-only queue entry stays read-only.
        applyPermissions(f, permissions);
        if (!f.setLastModified(lastModified)) {
            MiscUtils.getLogger().warn("Could not restore the last modified time of a queued document after rotating its pages");
        }
    }


    /**
     * Deletes a single page from a PDF document using OpenPDF PdfCopy. The deleted page
     * is saved to the deleted-documents directory (if the recycle bin is enabled via
     * INCOMINGDOCUMENT_RECYCLEBIN property) with a descriptive filename indicating
     * which page was deleted and the original total page count.
     *
     * @param queueId String the incoming document queue identifier
     * @param myPdfDir String the subdirectory type (Fax, Mail, File, or Refile)
     * @param myPdfName String the PDF filename
     * @param PageNumberToDelete String the 1-based page number to delete
     * @throws Exception if the page deletion, file operations, or rename fails
     */
    // path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public static void deletePage(String queueId, String myPdfDir, String myPdfName, String PageNumberToDelete) throws Exception {
        myPdfName = validatePathComponent(myPdfName, "myPdfName");

        String basePath = getIncomingDocumentFilePath(queueId, myPdfDir);
        String filePathName = getIncomingDocumentFilePathName(queueId, myPdfDir, myPdfName);

        File f = PathValidationUtils.validateExistingPath(new File(filePathName), new File(basePath));
        filePathName = f.getPath();
        long lastModified = f.lastModified();
        Set<PosixFilePermission> permissions = permissionsOf(f);

        // With the recycle bin off, nothing touches the recycle directory: it is neither created
        // nor required, and no copy of the removed page is written anywhere. Requiring it anyway
        // made a page delete fail on an install that had turned recycling off and had no writable
        // Fax_deleted directory.
        boolean recycle = recycleBinEnabled();
        File deleteDir = recycle
                ? PathValidationUtils.validateConfiguredDirectory(getIncomingDocumentDeletedFilePath(queueId, myPdfDir), "incoming deleted directory")
                : null;
        File validatedDeleteFile = null;
        File scratch = null;
        File recycleScratch = null;
        File recycled = null;
        boolean replaced = false;
        try {
            scratch = newScratchFile(new File(basePath), writable(permissions));
            if (recycle) {
                // The removed page is written to a scratch file in the recycle directory, filed
                // under a name no other recycle entry holds, and only then is the queue document
                // replaced. If filing fails the queue is untouched; if the replacement fails the
                // entry this call filed is removed again. Either way no page is lost and no older
                // entry overwritten.
                recycleScratch = newScratchFile(deleteDir, writable(permissions));
            }
            // Only once setup has succeeded, so a failure above cannot leave the source read-only;
            // the finally below restores its permissions whenever the replacement did not happen.
            f.setReadOnly();
            try (PdfReader reader = new PdfReader(filePathName);
                 OutputStream copyFos = Files.newOutputStream(scratch.toPath())) {
                int pageToDelete = parsePageNumber(PageNumberToDelete, reader.getNumberOfPages());
                if (recycle) {
                    String deleteFileName = addPdfNameSuffix(myPdfName,
                            "d" + pageToDelete + "of" + Integer.toString(reader.getNumberOfPages()));
                    validatedDeleteFile = PathValidationUtils.validatePath(deleteFileName, deleteDir);
                }

                copyPagesExcept(reader, pageToDelete, copyFos);
                if (recycle) {
                    try (OutputStream deleteFos = Files.newOutputStream(recycleScratch.toPath())) {
                        copyOnePage(reader, pageToDelete, deleteFos);
                    }
                }
            }

            if (recycle) {
                recycled = moveToUnusedName(recycleScratch, validatedDeleteFile, deleteDir);
            }

            // Replace the queue entry in one move rather than deleting it and then renaming the
            // replacement over the gap. Delete-then-rename lost the document outright whenever the
            // rename failed (permissions, a cross-filesystem temp dir): the queue entry was already
            // gone and the remaining pages were left stranded under the temp name.
            Files.move(scratch.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            replaced = true;
        } finally {
            if (!replaced) {
                deleteQuietly(scratch);
                deleteQuietly(recycled);
                restorePermissions(f, permissions);
            }
            // Gone already when it was filed; otherwise (a failure before filing) discard it.
            deleteQuietly(recycleScratch);
        }

        // The replacement is the scratch copy, created writable; give it the document's own
        // permissions so a read-only queue entry stays read-only.
        applyPermissions(f, permissions);
        // Carrying the original mtime over is cosmetic and must not abort the operation:
        // File.setLastModified is best-effort and returns false on filesystems that do not
        // support it, long after the replacement is already in place.
        if (!f.setLastModified(lastModified)) {
            MiscUtils.getLogger().warn("Could not restore the last modified time of a queued document after deleting a page");
        }
    }


    /**
     * Moves {@code source} to {@code target}, or to {@code target} with a {@code -2}, {@code -3} ...
     * suffix when that name is taken, never replacing an existing file.
     *
     * @return the file the source now lives at
     * @throws IOException if the move fails for a reason other than the name being taken
     */
    // every candidate name is validated against dir by PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "every candidate name is validated against dir by PathValidationUtils before use")
    private static File moveToUnusedName(File source, File target, File dir) throws IOException {
        String name = target.getName();
        for (int attempt = 1; attempt <= 1000; attempt++) {
            File candidate = attempt == 1 ? target
                    : PathValidationUtils.validatePath(addPdfNameSuffix(name, "-" + attempt), dir);
            try {
                Files.move(source.toPath(), candidate.toPath());
                return candidate;
            } catch (FileAlreadyExistsException e) {
                // taken: try the next suffix
            }
        }
        throw new IOException("No unused recycle name for a deleted incoming-document page");
    }

    /** Writes every page but one to the stream: the queue document with the page removed. */
    private static void copyPagesExcept(PdfReader reader, int excludedPage, OutputStream out) throws Exception {
        Document document = new Document(reader.getPageSizeWithRotation(1));
        PdfCopy copy = new PdfCopy(document, out);
        try {
            document.open();
            for (int pageNumber = 1; pageNumber <= reader.getNumberOfPages(); pageNumber++) {
                if (pageNumber != excludedPage) {
                    copy.addPage(copy.getImportedPage(reader, pageNumber));
                }
            }
        } finally {
            // PdfCopy must be closed before Document.close() to flush buffered pages
            copy.close();
            document.close();
        }
    }

    /** Writes one page to the stream: the removed page, for the recycle bin. */
    private static void copyOnePage(PdfReader reader, int page, OutputStream out) throws Exception {
        Document document = new Document(reader.getPageSizeWithRotation(page));
        PdfCopy copy = new PdfCopy(document, out);
        try {
            document.open();
            copy.addPage(copy.getImportedPage(reader, page));
        } finally {
            // PdfCopy must be closed before Document.close() to flush buffered pages
            copy.close();
            document.close();
        }
    }

    /**
     * The 1-based page a request names, checked against the document. Delete-page used to rely on
     * the recycle copy being empty to reject a page outside the document; with the recycle bin
     * off there is no such copy, so the range is checked outright.
     */
    private static int parsePageNumber(String pageNumber, int pageCount) {
        int page;
        try {
            page = Integer.parseInt(pageNumber == null ? "" : pageNumber.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid page number", e);
        }
        if (page < 1 || page > pageCount) {
            throw new IllegalArgumentException("Page " + page + " is outside a document of " + pageCount + " pages");
        }
        return page;
    }

    /** Whether a deleted page is kept in the recycle directory (INCOMINGDOCUMENT_RECYCLEBIN is active). */
    private static boolean recycleBinEnabled() {
        return CarlosProperties.getInstance().getBooleanProperty("INCOMINGDOCUMENT_RECYCLEBIN", "true");
    }

    /**
     * Extracts specified pages from a PDF into a new file using OpenPDF PdfCopy.
     * The remaining pages stay in the original file; extracted pages are written
     * to a new PDF file with an "E" suffix in the same directory.
     *
     * <p>The page specification format supports individual pages and ranges:
     * "1,3,5-7" extracts pages 1, 3, 5, 6, and 7. Validation rejects invalid
     * ranges, non-numeric input, and requests that would extract all pages.
     *
     * @param queueId String the incoming document queue identifier
     * @param myPdfDir String the subdirectory type (Fax, Mail, File, or Refile)
     * @param myPdfName String the PDF filename
     * @param pageNumbersToExtract String comma-separated page numbers and/or ranges (e.g., "1,3-5")
     * @throws Exception if the page specification is invalid or file operations fail
     */
    // case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision; path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = {"IMPROPER_UNICODE", "PATH_TRAVERSAL_IN"}, justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision; path validated for directory containment via PathValidationUtils before use")
    public static void extractPage(String queueId, String myPdfDir, String myPdfName, String pageNumbersToExtract) throws Exception {
        myPdfName = validatePathComponent(myPdfName, "myPdfName");

        String basePath = getIncomingDocumentFilePath(queueId, myPdfDir);
        String filePathName = getIncomingDocumentFilePathName(queueId, myPdfDir, myPdfName);

        File f = PathValidationUtils.validateExistingPath(new File(filePathName), new File(basePath));
        filePathName = f.getPath();
        long lastModified = f.lastModified();
        Set<PosixFilePermission> permissions = permissionsOf(f);

        File extractBaseDir = PathValidationUtils.validateConfiguredDirectory(getIncomingDocumentFilePath(queueId, myPdfDir), "incoming extract directory");
        ArrayList<String> extractList;

        PdfReader reader = null;
        Document document = null;
        PdfCopy copy = null;
        PdfCopy extractCopy = null;
        OutputStream copyFos = null;
        OutputStream extractFos = null;
        File validatedExtractFile = null;
        File scratch = null;
        boolean extractCreated = false;
        boolean replaced = false;

        try {
            // Inside the try, so the finally restores the source's permissions on any failure.
            f.setReadOnly();
            try {
                reader = new PdfReader(filePathName);
                String extractFileName = addPdfNameSuffix(myPdfName,
                        "E" + Integer.toString(reader.getNumberOfPages()));
                validatedExtractFile = PathValidationUtils.validatePath(extractFileName, extractBaseDir);

                extractList = buildExtractList(pageNumbersToExtract, reader.getNumberOfPages());

                scratch = newScratchFile(new File(basePath), writable(permissions));
                copyFos = Files.newOutputStream(scratch.toPath());
                // CREATE_NEW: an earlier extraction (or any queued document) under this name is
                // someone's unfiled clinical document; overwriting it lost it without a trace.
                try {
                    extractFos = Files.newOutputStream(validatedExtractFile.toPath(),
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                } catch (FileAlreadyExistsException e) {
                    throw new Exception("A document named " + extractFileName
                            + " is already in this queue. File or delete it, then extract again.", e);
                }
                extractCreated = true;

                document = new Document(reader.getPageSizeWithRotation(1));
                copy = new PdfCopy(document, copyFos);
                extractCopy = new PdfCopy(document, extractFos);
                document.open();
                copyExtractedPages(reader, extractList, copy, extractCopy);
            } finally {
                closePageExtractionResources(copy, extractCopy, document, copyFos, extractFos, reader);
            }

            // One move instead of delete-then-rename, for the same reason as deletePage: a failed
            // rename after an unconditional delete lost the queued document entirely.
            Files.move(scratch.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            replaced = true;
        } finally {
            if (!replaced) {
                // Leave the queue exactly as it was: the source untouched and writable again, no
                // scratch file, and no half-written extract (but never one this call did not create).
                deleteQuietly(scratch);
                if (extractCreated) {
                    deleteQuietly(validatedExtractFile);
                }
                restorePermissions(f, permissions);
            }
        }

        // The replacement is the scratch copy, created writable; give it the document's own
        // permissions so a read-only queue entry stays read-only.
        applyPermissions(f, permissions);
        // Both mtime carry-overs are cosmetic and deliberately not fatal.
        if (!f.setLastModified(lastModified)) {
            MiscUtils.getLogger().warn("Could not restore the last modified time of a queued document after extracting pages");
        }
        if (!validatedExtractFile.setLastModified(lastModified)) {
            MiscUtils.getLogger().warn("Could not restore the last modified time of an extracted document");
        }
    }


    private static ArrayList<String> buildExtractList(String pageNumbersToExtract, int pageCount) {
        String sanitizedPageNumbersToExtract = LogSafe.sanitize(pageNumbersToExtract);
        if (pageNumbersToExtract == null || pageNumbersToExtract.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid Pages to Extract " + sanitizedPageNumbersToExtract);
        }

        ArrayList<String> extractList = initializeExtractList(pageCount);
        boolean validPages = true;
        boolean hasPageSpec = false;

        for (String pageSpec : pageNumbersToExtract.split(",")) {
            pageSpec = pageSpec.trim();
            if (!pageSpec.isEmpty()) {
                hasPageSpec = true;
                validPages = markPagesForExtraction(extractList, pageSpec, pageCount) && validPages;
            }
        }

        if (!hasPageSpec || !validPages || !hasPageRemaining(extractList, pageCount)) {
            throw new IllegalArgumentException("Invalid Pages to Extract " + sanitizedPageNumbersToExtract);
        }

        return extractList;
    }

    private static ArrayList<String> initializeExtractList(int pageCount) {
        ArrayList<String> extractList = new ArrayList<String>();
        for (int pgIndex = 0; pgIndex <= pageCount; pgIndex++) {
            extractList.add(pgIndex, "0");
        }
        return extractList;
    }

    private static boolean markPagesForExtraction(ArrayList<String> extractList, String pageSpec, int pageCount) {
        // Limit -1 keeps trailing empty segments, so an incomplete range such as "1-" stays a
        // two-element spec with an empty bound and is rejected instead of collapsing to page 1.
        String[] rangeList = pageSpec.split("-", -1);
        if (rangeList.length == 1) {
            return markExtractPage(extractList, rangeList[0], pageCount);
        }
        if (rangeList.length == 2 && isNumericPage(rangeList[0]) && isNumericPage(rangeList[1])) {
            try {
                int startPage = Integer.parseInt(rangeList[0], 10);
                int endPage = Integer.parseInt(rangeList[1], 10);
                return markExtractRange(extractList, startPage, endPage, pageCount);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private static boolean markExtractPage(ArrayList<String> extractList, String pageSpec, int pageCount) {
        if (!isNumericPage(pageSpec)) {
            return false;
        }
        int pageNumber;
        try {
            pageNumber = Integer.parseInt(pageSpec, 10);
        } catch (NumberFormatException e) {
            return false;
        }
        if (!isValidPageNumber(pageNumber, pageCount)) {
            return false;
        }
        extractList.set(pageNumber, "1");
        return true;
    }

    private static boolean markExtractRange(ArrayList<String> extractList, int startPage, int endPage, int pageCount) {
        if (startPage > endPage) {
            return false;
        }
        for (int pageNumber = startPage; pageNumber <= endPage; pageNumber++) {
            if (!isValidPageNumber(pageNumber, pageCount)) {
                return false;
            }
            extractList.set(pageNumber, "1");
        }
        return true;
    }

    private static boolean isNumericPage(String pageSpec) {
        return pageSpec.matches("^\\d+$");
    }

    private static boolean isValidPageNumber(int pageNumber, int pageCount) {
        return pageNumber >= 1 && pageNumber <= pageCount;
    }

    private static boolean hasPageRemaining(ArrayList<String> extractList, int pageCount) {
        for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
            if (!"1".equals(extractList.get(pageNumber))) {
                return true;
            }
        }
        return false;
    }

    private static void copyExtractedPages(PdfReader reader, ArrayList<String> extractList, PdfCopy copy,
            PdfCopy extractCopy) throws IOException {
        for (int pageNumber = 1; pageNumber <= reader.getNumberOfPages(); pageNumber++) {
            if ("1".equals(extractList.get(pageNumber))) {
                extractCopy.addPage(copy.getImportedPage(reader, pageNumber));
            } else {
                copy.addPage(copy.getImportedPage(reader, pageNumber));
            }
        }
    }

    private static void closePageExtractionResources(PdfCopy copy, PdfCopy extractCopy, Document document,
            OutputStream copyFos, OutputStream extractFos, PdfReader reader) {
        closePdfResource(copy, "Error closing copy writer during page extraction");
        closePdfResource(extractCopy, "Error closing extract writer during page extraction");
        closePdfResource(document, "Error closing PDF document during page extraction");
        closePdfResource(copyFos, "Error closing copy output stream during page extraction");
        closePdfResource(extractFos, "Error closing extract output stream during page extraction");
        closePdfResource(reader, "Error closing PDF reader during page extraction");
    }

    // message is always one of the fixed internal cleanup strings passed by closePageExtractionResources().
    @SuppressFBWarnings(
            value = "CRLF_INJECTION_LOGS",
            justification = "message is always one of the fixed internal cleanup strings passed by closePageExtractionResources().")
    private static void closePdfResource(AutoCloseable resource, String message) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Exception e) {
            // exceptionTrace, not the throwable: a close failure here carries the queue or temp PDF
            // path in its message, and this runs during cleanup of patient documents.
            MiscUtils.getLogger().error("{}: {}", message, LogSafe.exceptionTrace(e));
        }
    }

    /**
     * Deletes an entire PDF file. If the INCOMINGDOCUMENT_RECYCLEBIN property is enabled
     * (default: true), the file is moved to the deleted-documents directory instead of
     * being permanently removed.
     *
     * @param queueId String the incoming document queue identifier
     * @param myPdfDir String the subdirectory type (Fax, Mail, File, or Refile)
     * @param myPdfName String the PDF filename to delete
     * @throws Exception if the file cannot be deleted or moved to the recycle bin
     */
    // path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public static void DeletePDF(String queueId, String myPdfDir, String myPdfName) throws Exception {
        String filePathName;
        boolean success;

        filePathName = getIncomingDocumentFilePathName(queueId, myPdfDir, myPdfName);
        File baseDir = PathValidationUtils.validateConfiguredDirectory(getIncomingDocumentFilePath(queueId, myPdfDir), "incoming document directory");
        File f = PathValidationUtils.validateExistingPath(new File(filePathName), baseDir);
        filePathName = f.getPath();

        // Validate myPdfName to prevent path traversal
        myPdfName = validatePathComponent(myPdfName, "myPdfName");
        
        if (recycleBinEnabled()) {
            // As in deletePage: the recycle directory is created and required only when it is used.
            String deletedPath = getIncomingDocumentDeletedFilePath(queueId, myPdfDir);
            File deleteDir = PathValidationUtils.validateConfiguredDirectory(deletedPath, "incoming deleted directory");
            File deletef = PathValidationUtils.validateGeneratedChildPath(myPdfName, deleteDir);
            String deletePathName = deletef.getPath();
            success = f.renameTo(deletef);
            if (!success) {
                throw new Exception("Error in renaming file from:" + filePathName + " to " + deletePathName);
            }
        } else {
            success = f.delete();
            if (!success) {
                throw new Exception("Error in deleting file:" + filePathName);
            }
        }
    }

    /**
     * Gets and persists the user's preferred incoming document queue. If no queue is
     * selected and no preference is stored, defaults to queue "1".
     *
     * @param user_no String the provider number of the current user
     * @param selectedQueue String the user's queue selection, or null to use the stored preference
     * @return String the active queue identifier
     */
    public static String getAndSetIncomingDocQueue(String user_no, String selectedQueue) {
        String queue;
        UserPropertyDAO pref = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);

        UserProperty up = pref.getProp(user_no, UserProperty.INCOMING_DOCUMENT_DEFAULT_QUEUE);
        if (up == null) {
            up = new UserProperty();
            up.setName(UserProperty.INCOMING_DOCUMENT_DEFAULT_QUEUE);
            up.setProviderNo(user_no);
        }


        if (selectedQueue == null) {

            if (up.getValue() == null) {
                queue = "1";
            } else {
                queue = up.getValue();
            }
        } else {
            queue = selectedQueue;
        }

        if (up.getValue() == null || !(up.getValue().equals(queue))) {
            up.setValue(queue);
            pref.saveProp(up);
        }
        return queue;
    }

    /**
     * Gets and persists the user's preferred document viewing format (PDF or Image).
     * Defaults to "Pdf" if no preference is stored.
     *
     * @param user_no String the provider number of the current user
     * @param selectedImageType String the selected view type ("Pdf" or "Image"), or null to use stored preference
     * @return String the active view type ("Pdf" or "Image")
     */
    public static String getAndSetViewDocumentAs(String user_no, String selectedImageType) {

        String imageType;

        UserPropertyDAO pref = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
        UserProperty up = pref.getProp(user_no, UserProperty.VIEW_DOCUMENT_AS);

        if (up == null) {
            up = new UserProperty();
            up.setName(UserProperty.VIEW_DOCUMENT_AS);
            up.setProviderNo(user_no);
        }

        if (selectedImageType == null) {
            if (up.getValue() == null || up.getValue().equals("Pdf")) {
                imageType = "Pdf";
            } else {
                imageType = "Image";
            }
        } else {
            imageType = selectedImageType;
        }

        if (up.getValue() == null || !(up.getValue().equals(imageType))) {
            up.setValue(imageType);
            pref.saveProp(up);
        }
        return imageType;
    }

    /**
     * Gets and persists the user's preferred document entry mode. Defaults to "Normal"
     * if no preference is stored.
     *
     * @param user_no String the provider number of the current user
     * @param selectedEntryMode String the selected entry mode, or null to use stored preference
     * @return String the active entry mode
     */
    public static String getAndSetEntryMode(String user_no, String selectedEntryMode) {

        String entryMode;

        UserPropertyDAO pref = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
        UserProperty up = pref.getProp(user_no, UserProperty.INCOMING_DOCUMENT_ENTRY_MODE);

        if (up == null) {
            up = new UserProperty();
            up.setName(UserProperty.INCOMING_DOCUMENT_ENTRY_MODE);
            up.setProviderNo(user_no);
        }

        if (selectedEntryMode == null) {
            if (up.getValue() == null) {
                entryMode = "Normal";
            } else {
                entryMode = up.getValue();
            }
        } else {
            entryMode = selectedEntryMode;
        }

        if (up.getValue() == null || !(up.getValue().equals(entryMode))) {
            up.setValue(entryMode);
            pref.saveProp(up);
        }
        return entryMode;
    }

    /**
     * Dispatches a PDF page manipulation action based on the action name string.
     * Supports single-page rotation, all-page rotation, page deletion, PDF deletion,
     * and page extraction.
     *
     * @param pdfAction String the action to perform (Rotate90, Rotate180, RotateM90,
     *                  RotateAll90, RotateAll180, RotateAllM90, DeletePage, DeletePDF, ExtractPagePDF)
     * @param queueIdStr String the incoming document queue identifier
     * @param pdfDir String the subdirectory type (Fax, Mail, File, or Refile)
     * @param pdfName String the PDF filename
     * @param pdfPageNumber String the 1-based page number for single-page operations
     * @param pdfExtractPageNumber String comma-separated page specification for extraction
     * @param locale Locale for localized error messages
     * @throws Exception if the requested action fails, with a localized error message
     */
    public static void doPagesAction(String pdfAction, String queueIdStr, String pdfDir, String pdfName, String pdfPageNumber, String pdfExtractPageNumber, Locale locale) throws Exception {
        if (pdfAction == null || pdfAction.trim().isEmpty()) {
            return;
        }

        String filePathName = getIncomingDocumentFilePathName(queueIdStr, pdfDir, pdfName);
        ResourceBundle props = ResourceBundle.getBundle("oscarResources", locale);
        int degree = 0;

        // Action naming: "M" prefix means "minus" (counter-clockwise rotation),
        // e.g., RotateM90 = rotate -90 degrees. "All" prefix applies to every page.
        if (pdfAction.equals("Rotate180")
                || pdfAction.equals("Rotate90")
                || pdfAction.equals("RotateM90")) {

            if (pdfAction.equals("Rotate180")) {
                degree = 180;
            } else if (pdfAction.equals("Rotate90")) {
                degree = 90;
            } else if (pdfAction.equals("RotateM90")) {
                degree = -90;
            }
            try {
                rotatePage(queueIdStr, pdfDir, pdfName, pdfPageNumber, degree);
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
                throw new Exception(filePathName + " : " + props.getString("dms.incomingDocs.cannotRotatePage") + pdfPageNumber);
            }
        }

        if (pdfAction.equals("RotateAll180")
                || pdfAction.equals("RotateAll90")
                || pdfAction.equals("RotateAllM90")) {

            if (pdfAction.equals("RotateAll180")) {
                degree = 180;
            } else if (pdfAction.equals("RotateAll90")) {
                degree = 90;
            } else if (pdfAction.equals("RotateAllM90")) {
                degree = -90;
            }
            try {
                rotateAlPages(queueIdStr, pdfDir, pdfName, degree);
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
                throw new Exception(filePathName + " : " + props.getString("dms.incomingDocs.cannotRotateAllPages"));
            }
        }


        if (pdfAction.equals("DeletePage")) {
            try {
                deletePage(queueIdStr, pdfDir, pdfName, pdfPageNumber);
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
                throw new Exception(filePathName + " : " + props.getString("dms.incomingDocs.cannotDeletePage") + pdfPageNumber);
            }
        }

        if (pdfAction.equals("DeletePDF")) {
            try {
                DeletePDF(queueIdStr, pdfDir, pdfName);
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
                throw new Exception(props.getString("dms.incomingDocs.cannotDelete") + filePathName);
            }
        }

        if (pdfAction.equals("ExtractPagePDF")) {
            try {
                extractPage(queueIdStr, pdfDir, pdfName, pdfExtractPageNumber);
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
                throw e;
            }
        }
    }
}
