//CHECKSTYLE:OFF
package ca.openosp.openo.utility;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Utility class for validating file paths to prevent path traversal attacks.
 *
 * <p>Usage for validating a user-provided filename (sanitizes and constructs safe path):</p>
 * <pre>
 * File safePath = PathValidationUtils.validatePath(userProvidedFileName, allowedDir);
 * // Now safe to read from or write to safePath
 * </pre>
 *
 * <p>Usage for validating an existing/internal path (containment check only, no sanitization):</p>
 * <pre>
 * File validatedFile = PathValidationUtils.validateExistingPath(file, allowedDir);
 * // Now safe to access or delete validatedFile
 * </pre>
 *
 * <p>Usage for validating uploaded temp files:</p>
 * <pre>
 * PathValidationUtils.validateUpload(sourceFile);
 * // Now safe to read from sourceFile
 * </pre>
 *
 * <p>Usage for complete upload validation (source + destination):</p>
 * <pre>
 * File destination = PathValidationUtils.validateUpload(
 *     sourceFile, userProvidedFileName, destinationDir);
 * // Now safe to copy sourceFile to destination
 * </pre>
 *
 * @since 2025-12-09
 */
public final class PathValidationUtils {

    private static final Logger logger = MiscUtils.getLogger();

    /**
     * Lazily-initialized set of allowed temp directories.
     * Uses LinkedHashSet to preserve insertion order for debugging.
     */
    private static volatile Set<String> allowedTempDirectories;

    private PathValidationUtils() {
        // Utility class - prevent instantiation
    }

    // ========================================================================
    // PATH VALIDATION - For validating user-provided paths within a directory
    // ========================================================================

    /**
     * Validates a user-provided filename and returns a safe path within the allowed directory.
     * Use this for both read and write operations where a user provides a filename.
     *
     * <p>Performs the following validations:</p>
     * <ol>
     *   <li>Sanitizes the user-provided filename (strips path components, rejects hidden files)</li>
     *   <li>Validates the resulting path is within the allowed directory</li>
     * </ol>
     *
     * @param userProvidedFileName the filename provided by the user
     * @param allowedDir the directory the file must be within
     * @return the validated File path
     * @throws SecurityException if validation fails
     */
    public static File validatePath(String userProvidedFileName, File allowedDir) {
		// 1. Sanitize filename
		String safeName = sanitizeFileName(userProvidedFileName);

		// 2. Build and validate path
		File path = new File(allowedDir, safeName);
		validateWithinDirectory(path, allowedDir);

		return path;
    }

    /**
     * Validates that an existing file path is within the allowed directory.
     * Use this for validating internal/application-created paths before deletion or access.
     * Unlike validatePath(), this does NOT sanitize or reconstruct the path - it validates
     * the actual file location.
     *
     * <p>This method performs strict validation with NO fallback to temp directories.
     * Use {@link #isInAllowedTempDirectory(File)} separately if you need to check
     * temp directories as a fallback.</p>
     *
     * @param file the file to validate
     * @param allowedDir the directory the file must be within
     * @return the validated File (same as input if valid)
     * @throws SecurityException if the file is outside the allowed directory
     */
    public static File validateExistingPath(File file, File allowedDir) {
        if (file == null) {
            throw new SecurityException("File is null");
        }
        validateWithinDirectory(file, allowedDir);
        return file;
    }

    // ========================================================================
    // UPLOAD VALIDATION - For validating uploaded files
    // ========================================================================

    /**
     * Validates an uploaded source file is from an allowed temp location.
     * Use this when reading uploaded file content without writing to a destination.
     *
     * @param sourceFile the uploaded file (from Struts2/Tomcat)
     * @return the validated File (same as input if valid) - use this return value for file operations
     * @throws SecurityException if validation fails
     */
    public static File validateUpload(File sourceFile) {
        validateSource(sourceFile, null);
        return sourceFile;
    }

    /**
     * Validates an upload operation end-to-end and returns the safe destination path.
     *
     * <p>Performs the following validations:</p>
     * <ol>
     *   <li>Validates source file exists and is from an allowed location</li>
     *   <li>Sanitizes the user-provided filename</li>
     *   <li>Validates the destination path is within the allowed directory</li>
     * </ol>
     *
     * @param sourceFile the uploaded file (from Struts2/Tomcat)
     * @param userProvidedFileName the original filename from the upload
     * @param destinationDir the directory where the file should be written
     * @return the validated destination File
     * @throws SecurityException if any validation fails
     */
    public static File validateUpload(
            File sourceFile,
            String userProvidedFileName,
            File destinationDir) {
		// 1. Validate source
		validateSource(sourceFile, destinationDir);

		// 2. Validate destination path
		return validatePath(userProvidedFileName, destinationDir);
    }

    // ========================================================================
    // TEMP DIRECTORY VALIDATION
    // ========================================================================

    /**
     * Checks if a file is located within an allowed system temp directory.
     *
     * <p>Allowed temp directories include:</p>
     * <ul>
     *   <li>java.io.tmpdir - System temp directory</li>
     *   <li>catalina.base/work - Tomcat work directory (where Struts2 stores uploads)</li>
     *   <li>catalina.home/work - Tomcat home work directory</li>
     * </ul>
     *
     * <p>Use this method as a fallback when validating files that may legitimately
     * be in system temp directories, such as application-created temp files or
     * files awaiting cleanup.</p>
     *
     * @param file the file to check
     * @return true if the file is within an allowed temp directory, false otherwise
     */
    public static boolean isInAllowedTempDirectory(File file) {
        if (file == null) {
            return false;
        }

        try {
            String canonicalPath = file.getCanonicalPath();
            Set<String> tempDirs = getAllowedTempDirectories();

            if (tempDirs.isEmpty()) {
                logger.warn("No temp directories configured - temp file operations will be rejected. Check java.io.tmpdir and catalina.base/catalina.home system properties.");
                return false;
            }

            for (String allowedDir : tempDirs) {
                if (canonicalPath.equals(allowedDir) || canonicalPath.startsWith(allowedDir + File.separator)) {
                    return true;
                }
            }

            return false;
        } catch (IOException e) {
            logger.error("Error validating file path", e);
            return false;
        }
    }

    // ========================================================================
    // INTERNAL VALIDATION METHODS
    // ========================================================================

    private static void validateSource(File sourceFile, File expectedBaseDir) {
        if (sourceFile == null) {
            throw new SecurityException("Uploaded file is null");
        }

        if (!sourceFile.exists()) {
            throw new SecurityException("Uploaded file does not exist");
        }

        if (!sourceFile.isFile()) {
            throw new SecurityException("Uploaded file is not a regular file");
        }

        // Check expected base directory first
        if (expectedBaseDir != null && isWithinDirectory(sourceFile, expectedBaseDir)) {
            return;
        }

        // Fallback: check temp directories - they're always valid sources for uploads
        if (isInAllowedTempDirectory(sourceFile)) {
            return;
        }

        logger.error("Invalid upload source path: {}", sourceFile.getPath());
        throw new SecurityException("Invalid upload source");
    }

    private static void validateWithinDirectory(File file, File allowedBaseDir) {
        if (allowedBaseDir == null || file == null) {
            throw new SecurityException("File or allowed base directory is null");
        }

        try {
            String baseCanonical = allowedBaseDir.getCanonicalPath();
            String fileCanonical = file.getCanonicalPath();

            if (!fileCanonical.equals(baseCanonical) && !fileCanonical.startsWith(baseCanonical + File.separator)) {
                logger.error("Path {} is outside allowed directory {}", fileCanonical, baseCanonical);
                throw new SecurityException("Invalid file path");
            }
        } catch (IOException e) {
            logger.error("Error validating file path", e);
            throw new SecurityException("Error validating file path");
        }
    }

    private static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new SecurityException("Filename is null or empty");
        }

        // Use Apache Commons IO to extract just the filename
        String baseName = FilenameUtils.getName(fileName);

        // Reject hidden files (starting with .)
        if (baseName.startsWith(".")) {
            logger.warn("Hidden filenames not allowed: {}", fileName);
            throw new SecurityException("Invalid filename: hidden files not allowed");
        }

        // Ensure the result is not empty
        if (baseName.trim().isEmpty()) {
            logger.warn("Filename became empty after sanitization: {}", fileName);
            throw new SecurityException("Invalid filename");
        }

        return baseName;
    }

    // ========================================================================
    // DIRECTORY MANAGEMENT
    // ========================================================================

    private static boolean isWithinDirectory(File file, File directory) {
        if (file == null || directory == null) {
            return false;
        }

        try {
            String canonicalPath = file.getCanonicalPath();
            String dirCanonical = directory.getCanonicalPath();
            return canonicalPath.equals(dirCanonical) || canonicalPath.startsWith(dirCanonical + File.separator);
        } catch (IOException e) {
            logger.error("Error checking if file is within directory", e);
            return false;
        }
    }

    /**
     * Returns the set of allowed temp directories. Uses lazy initialization with
     * double-checked locking for thread safety.
     *
     * @return Unmodifiable set of canonical paths for allowed temp directories
     */
    private static Set<String> getAllowedTempDirectories() {
        if (allowedTempDirectories == null) {
            synchronized (PathValidationUtils.class) {
                if (allowedTempDirectories == null) {
                    allowedTempDirectories = Collections.unmodifiableSet(buildAllowedTempDirectories());
                }
            }
        }
        return allowedTempDirectories;
    }

    /**
     * Builds the set of allowed temp directories from system properties.
     * Uses LinkedHashSet to maintain insertion order and automatically handle duplicates.
     *
     * <p>Temp directories are checked in the following order:</p>
     * <ol>
     *   <li>java.io.tmpdir - System temp directory (primary)</li>
     *   <li>catalina.base/work - Tomcat work directory (where Struts2 stores uploads)</li>
     *   <li>catalina.home/work - Tomcat home work directory (fallback if different from base)</li>
     * </ol>
     *
     * @return Set of canonical paths for temp directories
     */
    private static Set<String> buildAllowedTempDirectories() {
        Set<String> dirs = new LinkedHashSet<>();

        // System temp directory - primary location for temp files
        addTempDir(dirs, System.getProperty("java.io.tmpdir"));

        // Tomcat work directories - where Struts2 stores uploaded files
        addTempDir(dirs, System.getProperty("catalina.base"), "work");
        addTempDir(dirs, System.getProperty("catalina.home"), "work");

        return dirs;
    }

    /**
     * Adds a temp directory to the set if the path is valid and resolvable.
     *
     * @param dirs the set to add the directory to
     * @param basePath the base path (typically from a system property)
     */
    private static void addTempDir(Set<String> dirs, String basePath) {
        addTempDir(dirs, basePath, null);
    }

    /**
     * Adds a temp directory to the set if the path is valid and resolvable.
     * The Set naturally handles duplicates (e.g., when catalina.home == catalina.base).
     *
     * @param dirs the set to add the directory to
     * @param basePath the base path (typically from a system property)
     * @param subDir optional subdirectory to append
     */
    private static void addTempDir(Set<String> dirs, String basePath, String subDir) {
        if (basePath == null || basePath.trim().isEmpty()) {
            return;
        }

        try {
            File dir = (subDir != null) ? new File(basePath, subDir) : new File(basePath);
            dirs.add(dir.getCanonicalPath());
        } catch (IOException e) {
            logger.debug("Could not resolve canonical path for {}: {}", basePath, e.getMessage());
        }
    }
}
