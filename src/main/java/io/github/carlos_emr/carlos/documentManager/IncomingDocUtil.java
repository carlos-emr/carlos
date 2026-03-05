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


import io.github.carlos_emr.OscarProperties;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.ResourceBundle;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.logging.log4j.Logger;

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
    private static final Logger logger = MiscUtils.getLogger();
    
    /**
     * Validates that a path component does not contain path traversal sequences.
     * Delegates to PathValidationUtils for consistent validation.
     * @param pathComponent The path component to validate
     * @return true if the component is safe, false otherwise
     */
    private static boolean isValidPathComponent(String pathComponent) {
        if (pathComponent == null || pathComponent.isEmpty()) {
            return false;
        }

        // Use PathValidationUtils to validate - try to construct a safe path
        try {
            File tempDir = new File(System.getProperty("java.io.tmpdir"));
            PathValidationUtils.validatePath(pathComponent, tempDir);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    /**
     * Validates that a constructed path is within the allowed base directory.
     * Delegates to PathValidationUtils for consistent validation.
     * @param basePath The base directory path
     * @param targetPath The path to validate
     * @return true if the path is within bounds, false otherwise
     */
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
    private ArrayList<String> pdfListModifiedDate = new ArrayList<String>();

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
     * @return ArrayList of String date strings in "yyyy-MM-dd HH:mm:ss" format
     */
    public ArrayList getPdfListModifiedDate() {
        return pdfListModifiedDate;

    }

    /**
     * Lists all PDF files in the specified directory, sorted by last-modified date ascending.
     * Also populates the internal {@link #pdfListModifiedDate} list with corresponding
     * formatted timestamps.
     *
     * @param directory String the absolute path to the directory to scan for PDF files
     * @return ArrayList of String PDF filenames found in the directory
     */
    public ArrayList getDocList(String directory) {
        ArrayList<String> docList = new ArrayList<String>();

        String docName;
        pdfListModifiedDate.clear();

        FilenameFilter pdfFilter;

        pdfFilter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return (name.toLowerCase().endsWith(".pdf"));
            }
        };

        File dir = new File(directory);
        File[] listOfFiles = dir.listFiles(pdfFilter);
        if (listOfFiles != null) {

            Arrays.sort(listOfFiles, lastModified);

            for (int i = 0; i < listOfFiles.length; i++) {
                if (listOfFiles[i].isFile()) {
                    docName = listOfFiles[i].getName();
                    long dateTime = listOfFiles[i].lastModified();
                    Date d = new Date(dateTime);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    String dateString = sdf.format(d);
                    docList.add(docName);
                    pdfListModifiedDate.add(dateString);
                }
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
        PdfReader reader = null;
        try {
            reader = new PdfReader(filePath);
            numOfPages = reader.getNumberOfPages();
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        } finally {
            if (reader != null) {
                reader.close();
            }
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
    public static String getIncomingDocumentFilePathName(String queueId, String pdfDir, String pdfName) {
        // Validate pdfName to prevent path traversal
        if (!isValidPathComponent(pdfName)) {
            throw new IllegalArgumentException("Invalid pdfName: contains illegal characters or path traversal sequences");
        }
        
        String filePathName = getIncomingDocumentFilePath(queueId, pdfDir);
        
        // Use File constructor to safely combine paths
        File file = new File(filePathName, pdfName);
        
        // Validate the final path is within bounds
        String baseDir = OscarProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        if (!isPathWithinBounds(baseDir, file.getPath())) {
            throw new SecurityException("Attempted path traversal detected in file path");
        }
        
        return file.getPath();
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
    public static String getAndCreateIncomingDocumentFilePathName(String queueId, String pdfDir, String pdfName) {
        // Validate pdfName to prevent path traversal
        if (!isValidPathComponent(pdfName)) {
            throw new IllegalArgumentException("Invalid pdfName: contains illegal characters or path traversal sequences");
        }
        
        String filePathName = getAndCreateIncomingDocumentFilePath(queueId, pdfDir);
        
        // Use File constructor to safely combine paths
        File file = new File(filePathName, pdfName);
        
        // Validate the final path is within bounds
        String baseDir = OscarProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        if (!isPathWithinBounds(baseDir, file.getPath())) {
            throw new SecurityException("Attempted path traversal detected in file path");
        }
        
        return file.getPath();
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
    public static String getIncomingDocumentDeletedFilePath(String queueId, String pdfDir) {
        String filePath;

        filePath = OscarProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalStateException("INCOMINGDOCUMENT_DIR property not configured");
        }

        if (!filePath.endsWith(File.separator)) {
            filePath += File.separator;
        }
        
        // Validate queueId to prevent path traversal
        if (!isValidPathComponent(queueId)) {
            throw new IllegalArgumentException("Invalid queueId: contains illegal characters or path traversal sequences");
        }
        
        filePath += queueId + File.separator;
        
        // Validate pdfDir and restrict to allowed values
        if (pdfDir != null && (pdfDir.equals("Fax")
                || pdfDir.equals("Mail")
                || pdfDir.equals("File")
                || pdfDir.equals("Refile"))) {
            
            try {
                File baseDir = new File(OscarProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR"));
                File deletedPathDir = new File(filePath, pdfDir + "_deleted");

                // Validate path is within bounds using PathValidationUtils
                PathValidationUtils.validateExistingPath(deletedPathDir, baseDir);

                File canonicalDeletedDir = deletedPathDir.getCanonicalFile();

                if (!canonicalDeletedDir.exists()) {
                    canonicalDeletedDir.mkdirs();
                }

                filePath = canonicalDeletedDir.getPath();
            } catch (IOException e) {
                throw new SecurityException("Failed to validate deleted directory path", e);
            }
        } else if (pdfDir != null && !pdfDir.isEmpty()) {
            throw new IllegalArgumentException("Invalid pdfDir: must be one of Fax, Mail, File, or Refile");
        }
        
        return filePath;
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
    public static String getIncomingDocumentFilePath(String queueId, String pdfDir) {
        String filePath;

        filePath = OscarProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalStateException("INCOMINGDOCUMENT_DIR property not configured");
        }

        if (!filePath.endsWith(File.separator)) {
            filePath += File.separator;
        }
        
        // Validate queueId to prevent path traversal
        if (!isValidPathComponent(queueId)) {
            throw new IllegalArgumentException("Invalid queueId: contains illegal characters or path traversal sequences");
        }

        filePath += queueId + File.separator;

        // Validate pdfDir and restrict to allowed values
        if (pdfDir != null && (pdfDir.equals("Fax")
                || pdfDir.equals("Mail")
                || pdfDir.equals("File")
                || pdfDir.equals("Refile"))) {
            filePath = filePath + pdfDir;
        } else if (pdfDir != null && !pdfDir.isEmpty()) {
            // If pdfDir is provided but not in allowed list, throw exception
            throw new IllegalArgumentException("Invalid pdfDir: must be one of Fax, Mail, File, or Refile");
        }

        return filePath;
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
    public static String getAndCreateIncomingDocumentFilePath(String queueId, String pdfDir) {
        String filePath = getIncomingDocumentFilePath(queueId, pdfDir);
        
        // Get the base directory for validation
        String baseDir = OscarProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
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
            File baseDirFile = new File(baseDir);
            PathValidationUtils.validateExistingPath(filePathDir, baseDirFile);

            File canonicalDir = filePathDir.getCanonicalFile();

            if (!canonicalDir.exists()) {
                boolean created = canonicalDir.mkdirs();
                if (!created) {
                    logger.warn("Failed to create directory: " + canonicalDir.getPath());
                }
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
    public static void rotatePage(String queueId, String myPdfDir, String myPdfName, String MyPdfPageNumber, int degrees) throws Exception {
        long lastModified;
        String filePathName, tempFilePathName;
        int rot;
        int rotatedegrees;

        // Validate myPdfName for temp file
        if (!isValidPathComponent(myPdfName)) {
            throw new IllegalArgumentException("Invalid myPdfName: contains illegal characters or path traversal sequences");
        }
        
        String basePath = getIncomingDocumentFilePath(queueId, myPdfDir);
        File validatedTempFile = PathValidationUtils.validatePath("T" + myPdfName, new File(basePath));
        tempFilePathName = validatedTempFile.getPath();
        filePathName = getIncomingDocumentFilePathName(queueId, myPdfDir, myPdfName);

        File f = new File(filePathName);
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
            File f1 = new File(tempFilePathName);
            f1.setLastModified(lastModified);
            success = f1.renameTo(new File(filePathName));
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
    public static void rotateAlPages(String queueId, String myPdfDir, String myPdfName, int degrees) throws Exception {
        long lastModified;
        String filePathName, tempFilePathName;
        int rot;
        int rotatedegrees;

        // Validate myPdfName for temp file
        if (!isValidPathComponent(myPdfName)) {
            throw new IllegalArgumentException("Invalid myPdfName: contains illegal characters or path traversal sequences");
        }
        
        String basePath = getIncomingDocumentFilePath(queueId, myPdfDir);
        File validatedTempFile = PathValidationUtils.validatePath("T" + myPdfName, new File(basePath));
        tempFilePathName = validatedTempFile.getPath();
        filePathName = getIncomingDocumentFilePathName(queueId, myPdfDir, myPdfName);

        File f = new File(filePathName);
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
            File f1 = new File(tempFilePathName);
            f1.setLastModified(lastModified);
            success = f1.renameTo(new File(filePathName));
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
    public static void deletePage(String queueId, String myPdfDir, String myPdfName, String PageNumberToDelete) throws Exception {
        long lastModified;
        String filePathName, tempFilePathName;

        // Validate myPdfName for temp file
        if (!isValidPathComponent(myPdfName)) {
            throw new IllegalArgumentException("Invalid myPdfName: contains illegal characters or path traversal sequences");
        }
        
        String basePath = getIncomingDocumentFilePath(queueId, myPdfDir);
        File validatedTempFile = PathValidationUtils.validatePath("T" + myPdfName, new File(basePath));
        tempFilePathName = validatedTempFile.getPath();
        filePathName = getIncomingDocumentFilePathName(queueId, myPdfDir, myPdfName);

        File f = new File(filePathName);
        lastModified = f.lastModified();
        f.setReadOnly();

        File deleteDir = new File(getIncomingDocumentDeletedFilePath(queueId, myPdfDir));
        File validatedDeleteFile = null;
        int index = myPdfName.indexOf(".pdf");

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
        if (!OscarProperties.getInstance().getBooleanProperty("INCOMINGDOCUMENT_RECYCLEBIN", "true")) {
            if (validatedDeleteFile != null) {
                success = validatedDeleteFile.delete();
                if (!success) {
                    throw new Exception("Error in deleting file:" + validatedDeleteFile.getPath());
                }
            }
        }

        success = f.delete();
        if (success) {
            File f1 = new File(tempFilePathName);
            f1.setLastModified(lastModified);
            success = f1.renameTo(new File(filePathName));
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
    public static void extractPage(String queueId, String myPdfDir, String myPdfName, String pageNumbersToExtract) throws Exception {
        long lastModified;
        String filePathName, tempFilePathName;

        // Validate myPdfName for temp file
        if (!isValidPathComponent(myPdfName)) {
            throw new IllegalArgumentException("Invalid myPdfName: contains illegal characters or path traversal sequences");
        }
        
        String basePath = getIncomingDocumentFilePath(queueId, myPdfDir);
        File validatedTempFile = PathValidationUtils.validatePath("T" + myPdfName, new File(basePath));
        tempFilePathName = validatedTempFile.getPath();
        filePathName = getIncomingDocumentFilePathName(queueId, myPdfDir, myPdfName);

        File f = new File(filePathName);
        lastModified = f.lastModified();
        f.setReadOnly();

        File extractBaseDir = new File(getIncomingDocumentFilePath(queueId, myPdfDir));
        int index = myPdfName.toLowerCase().indexOf(".pdf");
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
            File f1 = new File(tempFilePathName);
            f1.setLastModified(lastModified);
            success = f1.renameTo(new File(filePathName));
            if (!success) {
                throw new Exception("Error in renaming file from:" + tempFilePathName + "to " + filePathName);
            }

            File f2 = new File(extractPath);
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
    public static void DeletePDF(String queueId, String myPdfDir, String myPdfName) throws Exception {
        String filePathName;
        boolean success;

        filePathName = getIncomingDocumentFilePathName(queueId, myPdfDir, myPdfName);
        File f = new File(filePathName);

        // Validate myPdfName to prevent path traversal
        if (!isValidPathComponent(myPdfName)) {
            throw new IllegalArgumentException("Invalid myPdfName: contains illegal characters or path traversal sequences");
        }
        
        String deletedPath = getIncomingDocumentDeletedFilePath(queueId, myPdfDir);
        File deleteFile = new File(deletedPath, myPdfName);
        String deletePathName = deleteFile.getPath();

        File deletef = new File(deletePathName);

        if (OscarProperties.getInstance().getBooleanProperty("INCOMINGDOCUMENT_RECYCLEBIN", "true")) {
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
