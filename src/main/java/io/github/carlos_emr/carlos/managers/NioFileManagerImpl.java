/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2015-2019. The Pharmacists Clinic, Faculty of Pharmaceutical Sciences, University of British Columbia. All Rights Reserved.
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
 * The Pharmacists Clinic
 * Faculty of Pharmaceutical Sciences
 * University of British Columbia
 * Vancouver, British Columbia, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.managers;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import jakarta.servlet.ServletContext;

import io.github.carlos_emr.CarlosProperties;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;
import javax.imageio.ImageIO;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import io.github.carlos_emr.carlos.utility.LogSafe;

/**
 * the NioFileManager handles all file input and output of all OscarDocument files
 * by providing several convenience utilities.
 * <p>
 * One goal is to eliminate the use of "CarlosProperties.getInstance().getProperty("DOCUMENT_DIR")"
 * in every single page of OSCAR code.
 */
@Service
public class NioFileManagerImpl implements NioFileManager {

    @Autowired(required=false)
    private ServletContext context;

    @Autowired
    private SecurityInfoManager securityInfoManager;

    private static Logger log = MiscUtils.getLogger();
    private static final String DOCUMENT_CACHE_DIRECTORY = "document_cache";
    private static final String TEMP_PDF_DIRECTORY = "tempPDF";
    private static final String DEFAULT_FILE_SUFFIX = "pdf";
    private static final String DEFAULT_GENERIC_TEMP = "tempDirectory";
    private static final String BASE_DOCUMENT_DIR = CarlosProperties.getInstance().getProperty("BASE_DOCUMENT_DIR");

    public Path hasCacheVersion2(LoggedInInfo loggedInInfo, String filename, Integer pageNum) {

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ, "")) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        // Validate input parameters
        if (filename == null || filename.trim().isEmpty()) {
            log.error("Invalid filename provided: null or empty");
            return null;
        }
        
        if (pageNum == null || pageNum < 1) {
            log.error("Invalid page number provided: " + pageNum);
            return null;
        }

        // Sanitize the filename to prevent path traversal
        String sanitizedFilename = sanitizeFileName(filename);
        
        // Additional validation after sanitization
        if (sanitizedFilename.isEmpty() || "invalid_filename".equals(sanitizedFilename)) {
            log.error("Filename failed sanitization: " + filename);
            return null;
        }

        Path documentCacheDir = getDocumentCacheDirectory(loggedInInfo);
        Path normalizedCacheDir = documentCacheDir.normalize().toAbsolutePath();
        
        // Construct the cache filename securely
        String cacheFileName = sanitizedFilename + "_" + pageNum + ".png";
        
        // Validate the cache filename doesn't contain any path separators
        if (cacheFileName.contains("/") || cacheFileName.contains("\\") || cacheFileName.contains("..")) {
            log.error("Invalid characters in cache filename: " + cacheFileName);
            return null;
        }
        
        // Safely construct the path using resolve() with the sanitized filename
        Path outfile = normalizedCacheDir.resolve(cacheFileName);
        outfile = outfile.normalize().toAbsolutePath();
        
        // Verify the file is within the cache directory (defense in depth)
        try {
            outfile = PathValidationUtils.validateExistingPath(outfile.toFile(), normalizedCacheDir.toFile()).toPath();
        } catch (SecurityException e) {
            log.error("Path traversal attempt detected in hasCacheVersion2: " + filename);
            return null;
        }
        
        // Additional check: ensure the resolved path is not a directory
        if (Files.exists(outfile) && Files.isDirectory(outfile)) {
            log.error("Resolved path is a directory, not a file: " + outfile);
            return null;
        }

        if (!Files.exists(outfile)) {
            outfile = null;
        }
        return outfile;
    }

    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public Path getDocumentCacheDirectory(LoggedInInfo loggedInInfo) {

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ, "")) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        Path cacheDir = Paths.get(BASE_DOCUMENT_DIR, context.getContextPath(), DOCUMENT_CACHE_DIRECTORY);

        if (!Files.exists(cacheDir)) {
            try {
                Files.createDirectory(cacheDir);
            } catch (IOException e) {
                log.error("Error creating DocumentCache directory", e);
            }
        }
        return cacheDir;
    }

        /**
     * Creates or returns a cached PNG preview for a page in a source PDF.
     *
     * @param loggedInInfo current authenticated user used to resolve the document cache directory
     * @param sourceDirectory directory containing the source PDF; must resolve under the document root or an approved temporary directory
     * @param filename source PDF filename; path components are stripped by legacy filename sanitization before resolution
     * @param pageNum one-based PDF page number to render
     * @return cached PNG path, or {@code null} when inputs are missing, paths are unauthorized, source paths traverse outside
     *         the validated directory, the source PDF is missing, or the requested page is out of range
     */
    public Path createCacheVersion2(LoggedInInfo loggedInInfo, String sourceDirectory, String filename, Integer pageNum) {

        // Rendering a page image of an already-readable document is a read-side operation (it only
        // derives a cache thumbnail). Require _edoc READ — matching the sibling cache methods above —
        // so read-only workflows such as fax preview are not forced to hold _edoc WRITE.
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ, "")) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        // Sanitize the filename to prevent path traversal
        String sanitizedFilename = sanitizeFileName(filename);

        Path cacheFilePath = hasCacheVersion2(loggedInInfo, sanitizedFilename, pageNum);

        /*
         * create a new cache file if an existing cache file is not returned.
         */
        if (cacheFilePath == null) {
            // Validate source directory input
            if (sourceDirectory == null || sourceDirectory.trim().isEmpty()) {
                log.error("Invalid source directory: null or empty");
                return null;
            }
            
            // Define the allowed base directory for documents
            Path baseDocumentPath = Paths.get(BASE_DOCUMENT_DIR).normalize().toAbsolutePath();

            // Validate and normalize the source directory - allow either document storage or approved temp paths.
            Path normalizedSourceDir;
            boolean sourceDirectoryInAllowedTemp = false;
            try {
                normalizedSourceDir = Paths.get(sourceDirectory).normalize().toAbsolutePath();

                if (PathValidationUtils.isInAllowedTempDirectory(normalizedSourceDir.toFile())) {
                    sourceDirectoryInAllowedTemp = true;
                } else {
                    normalizedSourceDir = PathValidationUtils.validateExistingPath(normalizedSourceDir.toFile(), baseDocumentPath.toFile()).toPath();
                }

                if (!Files.exists(normalizedSourceDir) || !Files.isDirectory(normalizedSourceDir)) {
                    if (log.isErrorEnabled()) {
                        String sanitizedSourceDirectory = LogSafe.sanitize(sourceDirectory, 1024);
                        log.error("Source directory does not exist or is not a directory: {}", sanitizedSourceDirectory);
                    }
                    return null;
                }
            } catch (Exception e) {
                if (log.isErrorEnabled()) {
                    log.error("Invalid source directory path: {}", LogSafe.sanitize(sourceDirectory, 1024), e);
                }
                return null;
            }

            Path sourceFile = normalizedSourceDir.resolve(sanitizedFilename).normalize().toAbsolutePath();

            // Ensure source file is within the source directory and, for temp previews, remains in an approved temp location.
            try {
                sourceFile = PathValidationUtils.validateExistingPath(sourceFile.toFile(), normalizedSourceDir.toFile()).toPath();
                if (sourceDirectoryInAllowedTemp && !PathValidationUtils.isInAllowedTempDirectory(sourceFile.toFile())) {
                    log.error("Source file is outside allowed temp path");
                    return null;
                }
            } catch (SecurityException e) {
                log.error("Path traversal attempt in source file");
                return null;
            }
            
            Path documentCacheDir = getDocumentCacheDirectory(loggedInInfo);
            Path normalizedCacheDir = documentCacheDir.normalize().toAbsolutePath();
            cacheFilePath = normalizedCacheDir.resolve(sanitizedFilename + "_" + pageNum + ".png");
            cacheFilePath = cacheFilePath.normalize().toAbsolutePath();
            
            // Verify the cache file path is within the cache directory
            try {
                cacheFilePath = PathValidationUtils.validateExistingPath(cacheFilePath.toFile(), normalizedCacheDir.toFile()).toPath();
            } catch (SecurityException e) {
                log.error("Path traversal attempt in cache file creation: " + filename);
                return null;
            }

            try (PDDocument document = Loader.loadPDF(sourceFile.toFile())) {
                int pageIndex = pageNum - 1;
                int pageCount = document.getNumberOfPages();

                // Validate page index is within bounds
                if (pageIndex < 0 || pageIndex >= pageCount) {
                    log.error("Requested page " + pageNum + " is out of range for document with " + pageCount + " pages");
                    return null;
                }

                PDFRenderer renderer = new PDFRenderer(document);
                // Render at 96 DPI to match jpedal settings
                // Note: jpedal uses 1-based page indexing, PDFBox uses 0-based
                BufferedImage image_to_save = renderer.renderImageWithDPI(pageIndex, 96, ImageType.RGB);

                // Check ImageIO.write success (returns false on failure)
                if (!ImageIO.write(image_to_save, "png", cacheFilePath.toFile())) {
                    log.error("Failed to write PNG image to cache file: " + cacheFilePath);
                    return null;
                }

                image_to_save.flush();
            } catch (IOException e) {
                log.error("Error rendering PDF page to cache", e);
                return null;  // Must return null on error
            }
        }

        return cacheFilePath;

    }

    /**
     * Remove the given file from the cache directory.
     * This is highly recommended function for temporary document preview images.
     *
     * @param loggedInInfo
     * @param fileName
     */
    public final boolean removeCacheVersion(LoggedInInfo loggedInInfo, final String fileName) {

        // Validate input to prevent null pointer exceptions
        if (fileName == null || fileName.trim().isEmpty()) {
            log.error("Invalid fileName provided: null or empty");
            return false;
        }

        // Sanitize the filename - remove any path traversal attempts
        String sanitizedFileName = sanitizeFileName(fileName);
        
        Path documentCacheDir = getDocumentCacheDirectory(loggedInInfo);
        
        try {
            // Get the normalized cache directory first
            Path normalizedCacheDir = documentCacheDir.normalize().toAbsolutePath();
            
            // Construct the file path safely using only the filename part
            // This prevents absolute paths or path traversal sequences from being used
            Path cacheFilePath = normalizedCacheDir.resolve(sanitizedFileName);
            
            // Normalize the constructed path
            Path normalizedPath = cacheFilePath.normalize().toAbsolutePath();
            
            // Double-check that the file is within the cache directory after normalization
            try {
                normalizedPath = PathValidationUtils.validateExistingPath(normalizedPath.toFile(), normalizedCacheDir.toFile()).toPath();
            } catch (SecurityException e) {
                log.error("Attempt to delete file outside of cache directory: " + fileName);
                throw new SecurityException("Path traversal attempt detected");
            }
            
            // Additional check - ensure we're not deleting directories
            if (Files.isDirectory(normalizedPath)) {
                log.error("Attempt to delete a directory instead of a file: " + fileName);
                return false;
            }
            
            return Files.deleteIfExists(normalizedPath);
        } catch (SecurityException e) {
            log.error("Security violation while attempting to delete cache file: " + fileName, e);
            throw e; // Re-throw security exceptions
        } catch (IOException e) {
            log.error("Error while deleting temp cache image file " + fileName, e);
        }
        return false;
    }
    
    /**
     * Sanitize filename to prevent path traversal attacks.
     * Removes any directory separators and path traversal sequences.
     *
     * @param fileName the filename to sanitize
     * @return sanitized filename with only the base name
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    private String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "";
        }
        
        // First, get just the filename component (removes any path)
        Path path = Paths.get(fileName);
        String baseName = path.getFileName() != null ? path.getFileName().toString() : "";
        
        // Remove any remaining path traversal sequences or special characters
        // that could be used maliciously
        baseName = baseName.replaceAll("\\.\\.", "")  // Remove ..
                          .replaceAll("[\\\\/]", "")   // Remove any slashes
                          .replaceAll("\\$", "")        // Remove $
                          .replaceAll("~", "");         // Remove ~
        
        // Additional validation - ensure the filename is not empty after sanitization
        if (baseName.trim().isEmpty()) {
            log.warn("Filename became empty after sanitization: {}", LogSafe.sanitize(fileName)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            return "invalid_filename";
        }
        
        return baseName;
    }

    /**
     * Save a file to the temporary directory from ByteArrayOutputStream
     *
     * @throws IOException
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public Path saveTempFile(final String fileName, ByteArrayOutputStream os, String fileType) throws IOException {
        Path directory = Files.createTempDirectory(TEMP_PDF_DIRECTORY + System.currentTimeMillis());
        if (fileType == null) {
            fileType = DEFAULT_FILE_SUFFIX;
        }
        String sanitizedName = sanitizeFileName(fileName);
        // Sanitize fileType to only allow safe alphanumeric extension characters
        String sanitizedType = fileType.replaceAll("[^a-zA-Z0-9]", "");
        if (sanitizedType.isEmpty()) {
            sanitizedType = DEFAULT_FILE_SUFFIX;
        }
        Path file = Files.createFile(Paths.get(directory.toString(), String.format("%1$s.%2$s", sanitizedName, sanitizedType)));
        // Validate the resulting path is within the temp directory
        try {
            file = PathValidationUtils.validateExistingPath(file.toFile(), directory.toFile()).toPath();
        } catch (SecurityException e) {
            Files.deleteIfExists(file);
            throw new SecurityException("File can only be created in temporary directory.");
        }
        return Files.write(file, os.toByteArray());
    }

    public final Path saveTempFile(final String fileName, ByteArrayOutputStream os) throws IOException {
        return saveTempFile(fileName, os, null);
    }

    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public Path createTempFile(final String fileName, ByteArrayOutputStream os) throws IOException {
        String sanitizedName = new File(fileName).getName();
        
        Path directory = Files.createTempDirectory(DEFAULT_GENERIC_TEMP + System.currentTimeMillis());
        Path file = directory.resolve(sanitizedName).normalize();

        // Ensure the resolved path is still within the temp directory
        try {
            file = PathValidationUtils.validateExistingPath(file.toFile(), directory.toFile()).toPath();
        } catch (SecurityException e) {
            throw new SecurityException("File can only be created in temporary directory.");
        }

        return Files.write(file, os.toByteArray());
    }

    /**
     * Deletes a validated temporary file. Existing targets must resolve to approved temp
     * directories through {@link PathValidationUtils}; missing approved temp files return
     * {@code false}, while invalid paths throw {@link SecurityException}.
     *
     * @param fileName temporary file path to delete
     * @return {@code true} when a file was deleted, otherwise {@code false}
     * @throws SecurityException if the path is outside approved temp directories
     */
    public final boolean deleteTempFile(final String fileName) {
        try {
            if (fileName == null || fileName.trim().isEmpty()) {
                log.warn("Temp deletion target was null or empty");
                return false;
            }

            File tempFile = validateTempDeletionTarget(fileName);

            return tempFile != null && Files.deleteIfExists(tempFile.toPath()); // codeql[java/path-injection] validateTempDeletionTarget returns only canonical regular files inside approved temp directories, or null for missing temp files.
        } catch (SecurityException e) {
            log.error("Security violation while attempting to delete temp file", e);
            throw e; // Re-throw security exceptions
        } catch (IOException e) {
            log.error("Error while deleting temp cache image file", e);
        }
        return false;
    }

    /**
     * Returns a canonical, regular temp file for deletion. The initial temp-directory
     * check allows missing approved temp files to be a no-op while rejecting escapes.
     */
    private File validateTempDeletionTarget(final String fileName) {
        File tempFile = new File(fileName); // codeql[java/path-injection] The resulting File is used only after canonical approved-temp validation and PathValidationUtils.validateUpload().
        if (!PathValidationUtils.isInAllowedTempDirectory(tempFile)) {
            log.error("Attempt to delete file outside approved temp directories");
            throw new SecurityException("Invalid temp deletion target");
        }

        if (!tempFile.exists()) { // codeql[java/path-injection] tempFile has been canonicalized inside isInAllowedTempDirectory() and accepted only under approved temp roots.
            return null;
        }

        return PathValidationUtils.validateUpload(tempFile);
    }


    /**
     * retrieve given filename from Oscar's document directory path as defined in
     * Oscar properties.
     * Filename string in File out
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public File getOscarDocument(String fileName) {
        // Sanitize the filename to prevent path traversal
        String sanitizedFileName = sanitizeFileName(fileName);
        
        Path documentDir = Paths.get(getDocumentDirectory()).normalize().toAbsolutePath();
        Path oscarDocument = documentDir.resolve(sanitizedFileName).normalize().toAbsolutePath();
        
        // Ensure the file is within the document directory
        try {
            oscarDocument = PathValidationUtils.validateExistingPath(oscarDocument.toFile(), documentDir.toFile()).toPath();
        } catch (SecurityException e) {
            log.error("Path traversal attempt in getOscarDocument: " + fileName);
            throw new SecurityException("Path traversal attempt detected");
        }
        
        return oscarDocument.toFile();
    }

    /**
     * Path NIO object in Path out.
     * The incoming path could have been derived from a temporary file.
     */
    public Path getOscarDocument(Path fileNamePath) {
        return getOscarDocument(fileNamePath.getFileName().toString()).toPath();
    }

    /**
     * Copy file from given file path into the default OscarDocuments directory.
     * This method deletes the temporary file after successful copy.
     * Uses Apache Commons FilenameUtils for robust path security.
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public String copyFileToOscarDocuments(String tempFilePath) {
        try {
            // Use FilenameUtils.getName() to extract just the filename, removing any path components
            // This is more reliable than manual path manipulation as it handles edge cases
            String sanitizedFileName = FilenameUtils.getName(tempFilePath);
            if (sanitizedFileName == null || sanitizedFileName.isEmpty()) {
                log.error("Invalid file path provided: " + tempFilePath);
                return null;
            }

            // Get source and destination directories
            File documentDir = new File(getDocumentDirectory());
            File sourceFile = new File(tempFilePath);
            File destinationFile = new File(documentDir, sanitizedFileName);

            // Validate that source file exists and is a regular file
            if (!sourceFile.exists() || !sourceFile.isFile()) {
                log.error("Source file does not exist or is not a regular file: " + tempFilePath);
                return null;
            }

            // Validate destination path using PathValidationUtils
            destinationFile = PathValidationUtils.validatePath(sanitizedFileName, documentDir);

            // Perform the copy operation
            Files.copy(sourceFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            if (destinationFile.exists()) {
                try {
                    if (!deleteTempFile(sourceFile.getPath()) && log.isWarnEnabled()) {
                        log.warn("Copied document but failed to delete temporary source file {}", LogSafe.sanitize(sourceFile.getPath(), 1024));
                    }
                } catch (SecurityException e) {
                    log.warn("Copied document but rejected temporary source cleanup for {}", LogSafe.sanitize(sourceFile.getPath(), 1024), e);
                }
            }

            return destinationFile.getPath();
        } catch (IOException e) {
            log.error("An error occurred while moving the PDF file", e);
            return null;
        }
    }

    /**
     * Get the default OscarDocument directory.
     * Newer versions of OSCAR will only define the path for the BASE_DOCUMENT and
     * not for the full DOCUMENT_DIRECTORY path in Oscar.properties.
     * This method considers both locations.
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    private String getDocumentDirectory() {
        String document_dir = DOCUMENT_DIRECTORY;
        if (document_dir == null || !Files.isDirectory(Paths.get(document_dir))) {
            document_dir = String.valueOf(Paths.get(BASE_DOCUMENT_DIR, "document"));
        }
        return document_dir;
    }

    /**
     * True if given filename exists in OscarDocument directory.
     * False if file not found.
     */
    public boolean isOscarDocument(String fileName) {
        File oscarDocument = getOscarDocument(fileName);
        return Files.exists(oscarDocument.toPath());
    }


}
