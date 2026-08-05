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
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

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

    /**
     * Validates that a constructed path is within the allowed base directory.
     * Delegates to PathValidationUtils for consistent validation.
     * @param basePath The base directory path
     * @param targetPath The path to validate
     * @return true if the path is within bounds, false otherwise
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
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

        FilenameFilter pdfFilter;

        pdfFilter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    return false;
                }

                // Keep listing consistent with validatePathComponent on the read path:
                // entries that cannot be addressed safely must not appear as broken rows.
                try {
                    validatePathComponent(name, "queued PDF filename");
                    return true;
                } catch (FileValidationException e) {
                    return false;
                }
            }
        };

        String incomingRootPath = CarlosProperties.getInstance().getProperty(INCOMING_DOCUMENT_DIR_PROPERTY);
        if (incomingRootPath == null || incomingRootPath.isEmpty()) {
            throw new IllegalStateException("INCOMINGDOCUMENT_DIR property not configured");
        }

        // A queue subdirectory is only created by the first upload or fax import, so a
        // never-used queue has no directory yet. That is an empty queue, not a
        // configuration error — validating it as one sent fresh installs to the error page.
        // Only the missing CHILD is an empty queue: when the base directory itself is
        // absent (config typo, unmounted volume), rendering every queue as empty would
        // hide accumulating incoming documents from intake staff, so that still fails
        // loudly below. The candidate is containment-validated before any probe.
        File incomingBaseDir = new File(incomingRootPath);
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
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
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
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
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
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
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
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
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
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
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
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public static void rotatePage(String queueId, String myPdfDir, String myPdfName, String MyPdfPageNumber, int degrees) throws Exception {
        long lastModified;
        String filePathName, tempFilePathName;
        int rot;
        int rotatedegrees;

        // Validate myPdfName for temp file
        myPdfName = validatePathComponent(myPdfName, "myPdfName");
        
        String basePath = getIncomingDocumentFilePath(queueId, myPdfDir);
        File validatedTempFile = PathValidationUtils.validatePath("T" + myPdfName, new File(basePath));
        tempFilePathName = validatedTempFile.getPath();
        filePathName = getIncomingDocumentFilePathName(queueId, myPdfDir, myPdfName);

        File f = PathValidationUtils.validateExistingPath(new File(filePathName), new File(basePath));
        filePathName = f.getPath();
        lastModified = f.lastModified();

        try (PdfReader reader = new PdfReader(filePathName);
             FileOutputStream fos = new FileOutputStream(validatedTempFile)) {
            rot = reader.getPageRotation(Integer.parseInt(MyPdfPageNumber));
            rotatedegrees = rot + degrees;
            rotatedegrees = rotatedegrees % 360;

            reader.getPageN(Integer.parseInt(MyPdfPageNumber)).put(PdfName.ROTATE, new PdfNumber(rotatedegrees));
            PdfStamper stp = new PdfStamper(reader, fos);
            stp.close();
        }


        boolean success = f.delete();

        if (success) {
            File f1 = PathValidationUtils.validateExistingPath(new File(tempFilePathName), new File(basePath));
            f1.setLastModified(lastModified);
            success = f1.renameTo(f);
            if (!success) {
                throw new Exception("Error in renaming file from:" + tempFilePathName + " to " + filePathName);
            }
        } else {
            throw new Exception("Error in deleting file:" + filePathName);
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
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public static void rotateAlPages(String queueId, String myPdfDir, String myPdfName, int degrees) throws Exception {
        long lastModified;
        String filePathName, tempFilePathName;
        int rot;
        int rotatedegrees;

        // Validate myPdfName for temp file
        myPdfName = validatePathComponent(myPdfName, "myPdfName");
        
        String basePath = getIncomingDocumentFilePath(queueId, myPdfDir);
        File validatedTempFile = PathValidationUtils.validatePath("T" + myPdfName, new File(basePath));
        tempFilePathName = validatedTempFile.getPath();
        filePathName = getIncomingDocumentFilePathName(queueId, myPdfDir, myPdfName);

        File f = PathValidationUtils.validateExistingPath(new File(filePathName), new File(basePath));
        filePathName = f.getPath();
        lastModified = f.lastModified();

        try (PdfReader reader = new PdfReader(filePathName);
             FileOutputStream fos = new FileOutputStream(validatedTempFile)) {
            for (int p = 1; p <= reader.getNumberOfPages(); ++p) {
                rot = reader.getPageRotation(p);
                rotatedegrees = rot + degrees;
                rotatedegrees = rotatedegrees % 360;

                reader.getPageN(p).put(PdfName.ROTATE, new PdfNumber(rotatedegrees));
            }
            PdfStamper stp = new PdfStamper(reader, fos);
            stp.close();
        }

        boolean success = f.delete();

        if (success) {
            File f1 = PathValidationUtils.validateExistingPath(new File(tempFilePathName), new File(basePath));
            f1.setLastModified(lastModified);
            success = f1.renameTo(f);
            if (!success) {
                throw new Exception("Error in renaming file from:" + tempFilePathName + "to " + filePathName);
            }
        } else {
            throw new Exception("Error in deleting file:" + filePathName);
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
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public static void deletePage(String queueId, String myPdfDir, String myPdfName, String PageNumberToDelete) throws Exception {
        long lastModified;
        String filePathName, tempFilePathName;

        // Validate myPdfName for temp file
        myPdfName = validatePathComponent(myPdfName, "myPdfName");
        
        String basePath = getIncomingDocumentFilePath(queueId, myPdfDir);
        File validatedTempFile = PathValidationUtils.validatePath("T" + myPdfName, new File(basePath));
        tempFilePathName = validatedTempFile.getPath();
        filePathName = getIncomingDocumentFilePathName(queueId, myPdfDir, myPdfName);

        File f = PathValidationUtils.validateExistingPath(new File(filePathName), new File(basePath));
        filePathName = f.getPath();
        lastModified = f.lastModified();
        f.setReadOnly();

        File deleteDir = PathValidationUtils.validateConfiguredDirectory(getIncomingDocumentDeletedFilePath(queueId, myPdfDir), "incoming deleted directory");
        File validatedDeleteFile = null;
        // getIncomingDocumentFilePathName has already verified the final extension
        // case-insensitively, so split from that final four-character suffix. The former
        // case-sensitive indexOf(".pdf") crashed for valid queued names such as SCAN.PDF.
        int index = myPdfName.length() - 4;

        String myPdfNameF = myPdfName.substring(0, index);
        String myPdfNameExt = myPdfName.substring(index, myPdfName.length());

        try (PdfReader reader = new PdfReader(filePathName);
             FileOutputStream copyFos = new FileOutputStream(validatedTempFile)) {
            String deleteFileName = myPdfNameF + "d" + PageNumberToDelete + "of" + Integer.toString(reader.getNumberOfPages()) + myPdfNameExt;
            validatedDeleteFile = PathValidationUtils.validatePath(deleteFileName, deleteDir);

            try (FileOutputStream deleteFos = new FileOutputStream(validatedDeleteFile)) {
                Document document = new Document(reader.getPageSizeWithRotation(1));
                PdfCopy copy = new PdfCopy(document, copyFos);
                PdfCopy deleteCopy = new PdfCopy(document, deleteFos);
                document.open();

                try {
                    for (int pageNumber = 1; pageNumber <= reader.getNumberOfPages(); pageNumber++) {
                        if (!(pageNumber == (Integer.parseInt(PageNumberToDelete)))) {
                            copy.addPage(copy.getImportedPage(reader, pageNumber));
                        } else {
                            deleteCopy.addPage(copy.getImportedPage(reader, pageNumber));
                        }
                    }
                } finally {
                    // PdfCopy must be closed before Document.close() to flush buffered pages
                    copy.close();
                    deleteCopy.close();
                    document.close();
                }
            }
        }

        boolean success;
        if (!CarlosProperties.getInstance().getBooleanProperty("INCOMINGDOCUMENT_RECYCLEBIN", "true")) {
            if (validatedDeleteFile != null) {
                success = validatedDeleteFile.delete();
                if (!success) {
                    throw new Exception("Error in deleting file:" + validatedDeleteFile.getPath());
                }
            }
        }

        success = f.delete();
        if (success) {
            File f1 = PathValidationUtils.validateExistingPath(new File(tempFilePathName), new File(basePath));
            f1.setLastModified(lastModified);
            success = f1.renameTo(f);
            if (!success) {
                throw new Exception("Error in renaming file from:" + tempFilePathName + "to " + filePathName);
            }
        } else {
            throw new Exception("Error in deleting file:" + filePathName);
        }
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
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = {"IMPROPER_UNICODE", "PATH_TRAVERSAL_IN"}, justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision; path validated for directory containment via PathValidationUtils before use")
    public static void extractPage(String queueId, String myPdfDir, String myPdfName, String pageNumbersToExtract) throws Exception {
        long lastModified;
        String filePathName, tempFilePathName;

        // Validate myPdfName for temp file
        myPdfName = validatePathComponent(myPdfName, "myPdfName");
        
        String basePath = getIncomingDocumentFilePath(queueId, myPdfDir);
        File validatedTempFile = PathValidationUtils.validatePath("T" + myPdfName, new File(basePath));
        tempFilePathName = validatedTempFile.getPath();
        filePathName = getIncomingDocumentFilePathName(queueId, myPdfDir, myPdfName);

        File f = PathValidationUtils.validateExistingPath(new File(filePathName), new File(basePath));
        filePathName = f.getPath();
        lastModified = f.lastModified();
        f.setReadOnly();

        File extractBaseDir = PathValidationUtils.validateConfiguredDirectory(getIncomingDocumentFilePath(queueId, myPdfDir), "incoming extract directory");
        int index = myPdfName.length() - 4;
        String myPdfNameF = myPdfName.substring(0, index);
        String myPdfNameExt = myPdfName.substring(index, myPdfName.length());

        ArrayList<String> extractList = new ArrayList<String>();
        int startPage, endPage;
        boolean cancelExtract = false;

        PdfReader reader = null;
        Document document = null;
        PdfCopy copy = null;
        PdfCopy extractCopy = null;
        FileOutputStream copyFos = null;
        FileOutputStream extractFos = null;
        String extractPath = null;

        try {
            reader = new PdfReader(filePathName);
            String extractFileName = myPdfNameF + "E" + Integer.toString(reader.getNumberOfPages()) + myPdfNameExt;
            File validatedExtractFile = PathValidationUtils.validatePath(extractFileName, extractBaseDir);
            extractPath = validatedExtractFile.getPath();

            // extractList uses 1-based indexing (matching PDF page numbers),
            // so index 0 is an unused placeholder
            for (int pgIndex = 0; pgIndex <= reader.getNumberOfPages(); pgIndex++) {
                extractList.add(pgIndex, "0");
            }

            String tmpPageNumbersToExtract = pageNumbersToExtract;
            String[] pageList = tmpPageNumbersToExtract.split(",");
            for (int i = 0; i < pageList.length; i++) {
                if (!pageList[i].isEmpty()) {
                    String[] rangeList = pageList[i].split("-");
                    if (rangeList.length > 2) {
                        cancelExtract = true;
                    }
                    for (int j = 0; j < rangeList.length; j++) {
                        if (!rangeList[j].matches("^[0-9]+$")) {
                            cancelExtract = true;
                        }
                    }
                    if (!cancelExtract) {
                        if (rangeList.length == 1) {
                            startPage = Integer.parseInt(rangeList[0], 10);
                            if (startPage > extractList.size() || startPage == 0) {
                                cancelExtract = true;
                            } else {
                                extractList.set(startPage, "1");
                            }
                        } else if (rangeList.length == 2) {
                            startPage = Integer.parseInt(rangeList[0], 10);
                            endPage = Integer.parseInt(rangeList[1], 10);

                            for (int k = startPage; k <= endPage; k++) {

                                if (k > extractList.size() || k == 0) {
                                    cancelExtract = true;
                                } else {
                                    extractList.set(k, "1");
                                }
                            }
                        }
                    }
                }
            }
            // Reject extraction if ALL pages would be extracted (nothing would remain)
            if (!cancelExtract) {
                cancelExtract = true;
                for (int pageNumber = 1; pageNumber <= reader.getNumberOfPages(); pageNumber++) {
                    if (!(extractList.get(pageNumber).equals("1"))) {
                        cancelExtract = false;
                    }
                }
            }
            if (cancelExtract == true) {
                reader.close();
                throw new Exception(myPdfName + " : Invalid Pages to Extract " + pageNumbersToExtract);
            }

            document = new Document(reader.getPageSizeWithRotation(1));
            copyFos = new FileOutputStream(validatedTempFile);
            copy = new PdfCopy(document, copyFos);
            extractFos = new FileOutputStream(validatedExtractFile);
            extractCopy = new PdfCopy(document, extractFos);
            document.open();
            for (int pageNumber = 1; pageNumber <= reader.getNumberOfPages(); pageNumber++) {
                if (!(extractList.get(pageNumber).equals("1"))) {
                    copy.addPage(copy.getImportedPage(reader, pageNumber));
                } else {
                    extractCopy.addPage(copy.getImportedPage(reader, pageNumber));
                }
            }


        } finally {
            // Each close is independently protected so a failure in one
            // does not prevent cleanup of the remaining resources.
            // PdfCopy closed before Document to match deletePage ordering.
            try { if (copy != null) copy.close(); }
            catch (Exception e) { MiscUtils.getLogger().error("Error closing copy writer during page extraction", e); }
            try { if (extractCopy != null) extractCopy.close(); }
            catch (Exception e) { MiscUtils.getLogger().error("Error closing extract writer during page extraction", e); }
            try { if (document != null) document.close(); }
            catch (Exception e) { MiscUtils.getLogger().error("Error closing PDF document during page extraction", e); }
            try { if (copyFos != null) copyFos.close(); }
            catch (Exception e) { MiscUtils.getLogger().error("Error closing copy output stream during page extraction", e); }
            try { if (extractFos != null) extractFos.close(); }
            catch (Exception e) { MiscUtils.getLogger().error("Error closing extract output stream during page extraction", e); }
            try { if (reader != null) reader.close(); }
            catch (Exception e) { MiscUtils.getLogger().error("Error closing PDF reader during page extraction", e); }
        }

        boolean success = f.delete();

        if (success) {
            File f1 = PathValidationUtils.validateExistingPath(new File(tempFilePathName), new File(basePath));
            f1.setLastModified(lastModified);
            success = f1.renameTo(f);
            if (!success) {
                throw new Exception("Error in renaming file from:" + tempFilePathName + "to " + filePathName);
            }

            File f2 = PathValidationUtils.validateExistingPath(new File(extractPath), extractBaseDir);
            f2.setLastModified(lastModified);
        } else {
            throw new Exception("Error in deleting file:" + filePathName);
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
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
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
        
        String deletedPath = getIncomingDocumentDeletedFilePath(queueId, myPdfDir);
        File deleteDir = PathValidationUtils.validateConfiguredDirectory(deletedPath, "incoming deleted directory");
        File deletef = PathValidationUtils.validateGeneratedChildPath(myPdfName, deleteDir);
        String deletePathName = deletef.getPath();

        if (CarlosProperties.getInstance().getBooleanProperty("INCOMINGDOCUMENT_RECYCLEBIN", "true")) {
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
