package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.CarlosProperties;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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
// FindSecBugs PATH_TRAVERSAL_IN: this class IS the path-validation / secure-file utility; Find Security Bugs flags its internal File/Path operations, which are the containment checks and secure temp-file creation themselves, not vulnerable sinks.
@SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "PathValidationUtils is the path-validation utility; its internal File/Path operations are the containment checks and secure temp-file creation themselves, not vulnerable sinks")
public final class PathValidationUtils {

    public static final String INVALID_FILENAME_MESSAGE =
            "Invalid filename. Use letters, numbers, dots, underscores, or spaces; spaces are converted to underscores, and filenames must not start with a dot.";
    public static final String HIDDEN_FILENAME_MESSAGE =
            "Invalid filename: hidden files not allowed. Do not start the filename with a dot.";
    public static final String PATH_COMPONENT_FILENAME_MESSAGE =
            "Invalid filename: must not include a path.";
    private static final String BLOCKED_EXTENSION_MESSAGE =
            "Invalid filename: file extension .%s not allowed.";
    private static final String INVALID_FIELD_EMPTY_LOG = "Invalid {}: null or empty";
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "jsp", "jspx", "war", "class", "jar", "jnlp");

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
     *   <li>Rejects dangerous final filename extensions</li>
     *   <li>Validates the resulting path is within the allowed directory</li>
     * </ol>
     *
     * @param userProvidedFileName the filename provided by the user
     * @param allowedDir the directory the file must be within
     * @return the validated File path
     * @throws FileValidationException if the filename is null, empty, hidden, contains a null byte,
     * or has a blocked final extension
     * @throws SecurityException if the allowed directory is null or the resulting path is outside it
     * @since 2025-12-09
     */
    public static File validatePath(String userProvidedFileName, File allowedDir) {
        // 1. Sanitize filename
        String safeName = sanitizeFileName(userProvidedFileName);
        validateAllowedFinalExtension(safeName);

        // 2. Build and validate path
        File path = new File(allowedDir, safeName);
        validateWithinDirectory(path, allowedDir);

        return path;
    }

    /**
     * Validates a user-provided filename, applies legacy character normalization,
     * then validates the resulting path is inside the allowed directory.
     *
     * @param userProvidedFileName the filename provided by the user
     * @param allowedDir the directory the file must be within
     * @return the validated File path
     * @throws FileValidationException if the filename validation fails
     * @throws SecurityException if the resulting path is outside the allowed directory
     */
    public static File validateUserFilePath(String userProvidedFileName, File allowedDir) {
        String safeName = validateFileName(userProvidedFileName);
        File path = new File(allowedDir, safeName);
        validateWithinDirectory(path, allowedDir);
        return path;
    }

    /**
     * Validates a user-provided filename and returns a normalized safe filename component.
     * Normalization preserves the legacy {@link MiscUtils#sanitizeFileName(String)}
     * contract: whitespace becomes underscores, characters outside {@code [a-zA-Z0-9._]}
     * are removed, and repeated dots collapse to a single dot.
     *
     * @param userProvidedFileName the filename provided by the user
     * @return the validated filename component
     * @throws FileValidationException if validation fails, including when the normalized final
     * extension is blocked for server-side execution risk
     */
    public static String validateFileName(String userProvidedFileName) {
        String baseName = sanitizeFileName(userProvidedFileName);
        String normalizedName = normalizeFileNameCharacters(baseName);

        return validateNormalizedFileName(normalizedName);
    }

    /**
     * Validates an application-generated filename after applying legacy character normalization
     * to the complete generated value. Use this when the application adds trusted prefixes or
     * suffixes around user-provided fragments and those generated parts must be preserved.
     *
     * @param generatedFileName the generated filename
     * @return the validated filename component
     * @throws FileValidationException if validation fails
     */
    public static String validateGeneratedFileName(String generatedFileName) {
        if (generatedFileName == null || generatedFileName.trim().isEmpty()) {
            throw new FileValidationException(INVALID_FILENAME_MESSAGE);
        }
        validateAllowedFinalExtension(generatedFileName);

        String normalizedName = normalizeFileNameCharacters(generatedFileName);
        return validateNormalizedFileName(normalizedName);
    }

    private static String validateNormalizedFileName(String normalizedName) {
        if (normalizedName.trim().isEmpty()) {
            logger.warn("Filename became empty after normalization");
            throw new FileValidationException(INVALID_FILENAME_MESSAGE);
        }
        if (normalizedName.startsWith(".")) {
            logger.warn("Hidden filenames not allowed after normalization");
            throw new FileValidationException(HIDDEN_FILENAME_MESSAGE);
        }
        validateAllowedFinalExtension(normalizedName);
        return normalizedName;
    }

    private static void validateAllowedFinalExtension(String fileName) {
        if (fileName.indexOf('\0') >= 0) {
            logger.warn("Filename contains null byte");
            throw new FileValidationException(INVALID_FILENAME_MESSAGE);
        }
        // Check only the final extension, so report.jsp.txt stays allowed while report.txt.jsp is blocked.
        String extension = extractFinalExtension(stripTrailingDotsAndWhitespace(fileName));
        String blockedExtension = findBlockedExtension(extension);
        if (blockedExtension != null) {
            logger.warn("Blocked dangerous file extension: {}", blockedExtension);
            throw new FileValidationException(String.format(BLOCKED_EXTENSION_MESSAGE, blockedExtension));
        }
    }

    private static String extractFinalExtension(String fileName) {
        int lastSeparator = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot <= lastSeparator || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot + 1);
    }

    private static String stripTrailingDotsAndWhitespace(String fileName) {
        int end = fileName.length();
        while (end > 0) {
            char current = fileName.charAt(end - 1);
            if (current != '.' && !Character.isWhitespace(current)) {
                break;
            }
            end--;
        }
        return fileName.substring(0, end);
    }

    private static String findBlockedExtension(String extension) {
        for (String blockedExtension : BLOCKED_EXTENSIONS) {
            if (equalsAsciiIgnoreCase(extension, blockedExtension)) {
                return blockedExtension;
            }
        }
        return null;
    }

    private static boolean equalsAsciiIgnoreCase(String candidate, String expectedLowerCase) {
        if (candidate.length() != expectedLowerCase.length()) {
            return false;
        }
        for (int i = 0; i < candidate.length(); i++) {
            char candidateChar = candidate.charAt(i);
            if (candidateChar >= 'A' && candidateChar <= 'Z') {
                candidateChar = (char) (candidateChar + ('a' - 'A'));
            }
            if (candidateChar != expectedLowerCase.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validates a filename-only input where path components are not accepted.
     * Use this for explicit filename request parameters, not upload-origin names.
     *
     * @param userProvidedFileName the filename provided by the user
     * @return the validated filename component
     * @throws FileValidationException if validation fails
     */
    public static String validateStrictFileName(String userProvidedFileName) {
        if (userProvidedFileName == null || userProvidedFileName.trim().isEmpty()) {
            throw new FileValidationException(INVALID_FILENAME_MESSAGE);
        }

        try {
            if (FilenameUtils.getPrefixLength(userProvidedFileName) > 0
                    || userProvidedFileName.contains("/")
                    || userProvidedFileName.contains("\\")) {
                logger.warn("Path components not allowed in filename");
                throw new FileValidationException(PATH_COMPONENT_FILENAME_MESSAGE);
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Filename parser rejected invalid filename");
            throw new FileValidationException(INVALID_FILENAME_MESSAGE, e);
        }

        return validateFileName(userProvidedFileName);
    }

    /**
     * Validates a single path component without normalizing or stripping path
     * information. Use this for directory or filename segments that must be
     * preserved exactly, such as queue identifiers and existing queued document
     * names.
     *
     * @param value the path component to validate
     * @param label field name used in log messages
     * @return the original value when it is a safe single component
     * @throws FileValidationException if the value is blank, hidden, absolute,
     * contains path separators, or is a traversal component
     */
    public static String validatePathComponent(String value, String label) {
        String field = label == null || label.trim().isEmpty() ? "path component" : label;
        if (value == null || value.trim().isEmpty()) {
            logger.warn(INVALID_FIELD_EMPTY_LOG, field);
            throw new FileValidationException(INVALID_FILENAME_MESSAGE);
        }
        if (value.indexOf('\0') >= 0) {
            logger.warn("Invalid {}: contains null byte", field);
            throw new FileValidationException(INVALID_FILENAME_MESSAGE);
        }
        if (".".equals(value) || "..".equals(value) || value.startsWith(".")) {
            logger.warn("Invalid {}: hidden or traversal component", field);
            throw new FileValidationException(INVALID_FILENAME_MESSAGE);
        }

        try {
            if (FilenameUtils.getPrefixLength(value) > 0
                    || value.contains("/")
                    || value.contains("\\")
                    || !FilenameUtils.getName(value).equals(value)) {
                logger.warn("Invalid {}: path components not allowed", field);
                throw new FileValidationException(INVALID_FILENAME_MESSAGE);
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid {}: filename parser rejected value", field);
            throw new FileValidationException(INVALID_FILENAME_MESSAGE, e);
        }

        return value;
    }

    static String normalizeFileNameCharacters(String fileName) {
        return fileName.replaceAll("\\s+", "_")
                .replaceAll("[^a-zA-Z0-9._]", "")
                .replaceAll("\\.+", ".");
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

    /**
     * Validates that a child file path is within the allowed directory. The child
     * does not need to exist yet, so this helper is appropriate for creation paths.
     *
     * @param file the file path to validate
     * @param allowedDir the directory the file must be within
     * @return the validated File
     * @throws SecurityException if the file is outside the allowed directory
     */
    public static File validateChildPath(File file, File allowedDir) {
        return validateExistingPath(file, allowedDir);
    }

    /**
     * Canonicalizes a trusted internal path without treating it as a security boundary.
     * Use a trusted base-directory helper instead for request, upload, or other
     * externally controlled paths.
     *
     * @param file trusted file path to canonicalize
     * @return canonicalized file
     */
    public static File resolveTrustedPath(File file) {
        return resolveTrustedPath(file, "trusted file");
    }

    /**
     * Canonicalizes a trusted internal path without treating it as a security boundary.
     * Use a trusted base-directory helper instead for request, upload, or other
     * externally controlled paths.
     *
     * @param file trusted file path to canonicalize
     * @param label human-readable label for diagnostics
     * @return canonicalized file
     */
    public static File resolveTrustedPath(File file, String label) {
        String field = label == null || label.trim().isEmpty() ? "trusted file" : label;
        if (file == null) {
            throw new SecurityException("File is null");
        }
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            logger.error("Error resolving {}", field, e);
            throw new SecurityException("Error resolving trusted path", e);
        }
    }

    /**
     * Canonicalizes a trusted file path by delegating to {@link #resolveTrustedPath(File)}.
     * Despite the legacy name, it performs no parent-directory containment check and is not a
     * security boundary; use a trusted base-directory helper for untrusted paths.
     *
     * @param file file to canonicalize
     * @return canonicalized file
     * @deprecated use {@link #resolveTrustedPath(File)} for trusted internal paths,
     * or validate against a real trusted base directory.
     */
    @Deprecated
    public static File validateAgainstParentDirectory(File file) {
        return resolveTrustedPath(file);
    }

    /**
     * Canonicalizes a configured directory path while preserving support for
     * absolute deployment-specific locations from carlos.properties.
     *
     * <p>This method is for trusted configuration values, not request
     * parameters. Use it to establish the allowed base directory before
     * resolving user or generated children with the other validation helpers.</p>
     *
     * @param configuredPath configured directory path
     * @param label human-readable label for diagnostics
     * @return canonical directory File
     * @throws SecurityException if the path is blank, cannot be canonicalized, or is not a directory
     */
    public static File validateConfiguredDirectory(String configuredPath, String label) {
        String field = label == null || label.trim().isEmpty() ? "configured directory" : label;
        if (configuredPath == null || configuredPath.trim().isEmpty()) {
            logger.warn(INVALID_FIELD_EMPTY_LOG, field);
            throw new SecurityException("Invalid configured directory");
        }

        try {
            File directory = new File(configuredPath).getCanonicalFile();
            if (!directory.isDirectory()) {
                if (logger.isWarnEnabled()) {
                    logger.warn("{} is not a directory: {}", field, LogSafe.sanitize(directory.getPath(), 1024));
                }
                throw new SecurityException("Configured path is not a directory");
            }
            return directory;
        } catch (IOException e) {
            logger.error("Error validating {}", field, e);
            throw new SecurityException("Error validating configured directory", e);
        }
    }

    /**
     * Resolves an application-generated child name inside an allowed directory
     * and validates canonical containment without applying user filename
     * normalization. Use this only for constants or generated names controlled
     * by application code, such as metadata files or deterministic export names.
     *
     * @param generatedChildName application-generated filename
     * @param allowedDir directory the child must remain within
     * @return validated child File
     * @throws FileValidationException if the generated name is blank, contains a null byte,
     *         contains a path separator, or is "." or ".."
     * @throws SecurityException if the resolved child escapes {@code allowedDir}
     */
    public static File validateGeneratedChildPath(String generatedChildName, File allowedDir) {
        if (generatedChildName == null || generatedChildName.trim().isEmpty()) {
            throw new FileValidationException(INVALID_FILENAME_MESSAGE);
        }
        if (generatedChildName.indexOf('\0') >= 0) {
            throw new FileValidationException(INVALID_FILENAME_MESSAGE);
        }
        if (generatedChildName.contains("/") || generatedChildName.contains("\\")
                || ".".equals(generatedChildName) || "..".equals(generatedChildName)) {
            logger.warn("Generated child path must be a single filename component");
            throw new FileValidationException(PATH_COMPONENT_FILENAME_MESSAGE);
        }

        File path = new File(allowedDir, generatedChildName);
        validateWithinDirectory(path, allowedDir);
        return path;
    }

    /**
     * Canonicalizes a configured directory path that may be created later by
     * the caller. Existing non-directory files are rejected, but missing paths
     * are preserved to avoid changing legacy lazy-create behavior.
     *
     * @param configuredPath configured directory path
     * @param label human-readable label for diagnostics
     * @return canonical directory File
     */
    public static File resolveConfiguredDirectory(String configuredPath, String label) {
        String field = label == null || label.trim().isEmpty() ? "configured directory" : label;
        if (configuredPath == null || configuredPath.trim().isEmpty()) {
            logger.warn(INVALID_FIELD_EMPTY_LOG, field);
            throw new SecurityException("Invalid configured directory");
        }

        try {
            File directory = new File(configuredPath).getCanonicalFile();
            if (directory.exists() && !directory.isDirectory()) {
                if (logger.isWarnEnabled()) {
                    logger.warn("{} is not a directory: {}", field, LogSafe.sanitize(directory.getPath(), 1024));
                }
                throw new SecurityException("Configured path is not a directory");
            }
            return directory;
        } catch (IOException e) {
            logger.error("Error validating {}", field, e);
            throw new SecurityException("Error validating configured directory", e);
        }
    }

    /**
     * Resolves a trusted generated sibling beside a configured path. This is
     * for legacy layouts that derive files by appending a fixed suffix to a
     * configured path, such as {@code /path/outbox.timestamp}.
     *
     * @param configuredPath configured base path
     * @param suffix trusted suffix to append to the configured file or directory name
     * @param label human-readable label for diagnostics
     * @return validated sibling File
     */
    public static File validateGeneratedSiblingPath(String configuredPath, String suffix, String label) {
        String field = label == null || label.trim().isEmpty() ? "configured sibling" : label;
        if (configuredPath == null || configuredPath.trim().isEmpty() || suffix == null || suffix.trim().isEmpty()) {
            logger.warn("Invalid {}: blank configured path or suffix", field);
            throw new SecurityException("Invalid configured sibling path");
        }
        if (suffix.indexOf('\0') >= 0 || suffix.contains("/") || suffix.contains("\\")) {
            logger.warn("Invalid {}: suffix must be a trusted path suffix", field);
            throw new SecurityException("Invalid configured sibling suffix");
        }

        try {
            File configured = new File(configuredPath).getCanonicalFile();
            File parent = configured.getParentFile();
            if (parent == null) {
                throw new SecurityException("Configured path has no parent directory");
            }
            File sibling = new File(parent, configured.getName() + suffix);
            validateWithinDirectory(sibling, parent);
            return sibling;
        } catch (IOException e) {
            logger.error("Error validating {}", field, e);
            throw new SecurityException("Error validating configured sibling path", e);
        }
    }


    /**
     * Canonicalizes and validates a configured file path that must already exist.
     * This is for trusted configuration values, not request parameters.
     *
     * @param configuredPath configured file path
     * @param label human-readable label for diagnostics
     * @return canonical file
     */
    public static File validateConfiguredFile(String configuredPath, String label) {
        String field = label == null || label.trim().isEmpty() ? "configured file" : label;
        if (configuredPath == null || configuredPath.trim().isEmpty()) {
            logger.warn(INVALID_FIELD_EMPTY_LOG, field);
            throw new SecurityException("Invalid configured file");
        }

        try {
            File file = new File(configuredPath).getCanonicalFile();
            if (!file.isFile()) {
                if (logger.isWarnEnabled()) {
                    logger.warn("{} is not a file: {}", field, LogSafe.sanitize(file.getPath(), 1024));
                }
                throw new SecurityException("Configured path is not a file");
            }
            return file;
        } catch (IOException e) {
            logger.error("Error validating {}", field, e);
            throw new SecurityException("Error validating configured file", e);
        }
    }

    /**
     * Canonicalizes a configured file path that may be created later by the caller.
     * Existing non-file paths are rejected, but missing files are preserved to avoid
     * changing legacy lazy-create behavior.
     *
     * @param configuredPath configured file path
     * @param label human-readable label for diagnostics
     * @return canonical file
     */
    public static File resolveConfiguredFile(String configuredPath, String label) {
        String field = label == null || label.trim().isEmpty() ? "configured file" : label;
        if (configuredPath == null || configuredPath.trim().isEmpty()) {
            logger.warn(INVALID_FIELD_EMPTY_LOG, field);
            throw new SecurityException("Invalid configured file");
        }

        try {
            File file = new File(configuredPath).getCanonicalFile();
            if (file.exists() && !file.isFile()) {
                if (logger.isWarnEnabled()) {
                    logger.warn("{} is not a file: {}", field, LogSafe.sanitize(file.getPath(), 1024));
                }
                throw new SecurityException("Configured path is not a file");
            }
            return file;
        } catch (IOException e) {
            logger.error("Error validating {}", field, e);
            throw new SecurityException("Error validating configured file", e);
        }
    }

    /**
     * Resolves a ZIP entry under a destination directory and validates that the
     * canonical result remains within that directory. Directory entries are allowed.
     *
     * @param entry ZIP entry to resolve
     * @param destinationDir extraction root
     * @return validated target file for the entry
     * @throws FileValidationException if the entry name is absolute, empty, contains a null byte,
     *         or contains a traversal segment ("..", "/../", "/./")
     * @throws SecurityException if the entry or destination is null, or the resolved target
     *         escapes {@code destinationDir}
     */
    public static File validateZipEntryPath(ZipEntry entry, File destinationDir) {
        if (entry == null) {
            throw new SecurityException("ZIP entry is null");
        }
        if (destinationDir == null) {
            throw new SecurityException("ZIP destination directory is null");
        }

        String entryName = entry.getName();
        validateZipEntryNameComponent(entryName);
        File target = new File(destinationDir, entryName.replace('\\', '/'));
        validateWithinDirectory(target, destinationDir);
        return target;
    }

    /**
     * Builds a safe ZIP entry name for a file known to be under a source root.
     *
     * @param file file being added to the archive
     * @param sourceRoot root directory used to compute the relative entry name
     * @return slash-separated validated relative ZIP entry name
     * @throws SecurityException if the file is outside {@code sourceRoot} or the derived
     *         entry name is unsafe
     */
    public static String validateZipEntryName(File file, File sourceRoot) {
        File validatedFile = validateExistingPath(file, sourceRoot);
        try {
            File canonicalRoot = sourceRoot.getCanonicalFile();
            File canonicalFile = validatedFile.getCanonicalFile();
            String rootPath = canonicalRoot.getPath();
            String filePath = canonicalFile.getPath();
            String relativeName;
            if (filePath.equals(rootPath)) {
                relativeName = canonicalFile.getName();
            } else {
                relativeName = filePath.substring(rootPath.length() + 1);
            }
            relativeName = relativeName.replace(File.separatorChar, '/');
            validateZipEntryNameComponent(relativeName);
            return relativeName;
        } catch (IOException | RuntimeException e) {
            logger.error("Error validating ZIP entry name", e);
            throw new SecurityException("Invalid ZIP entry name", e);
        }
    }

    private static void validateZipEntryNameComponent(String entryName) {
        if (entryName == null || entryName.trim().isEmpty() || entryName.indexOf('\0') >= 0) {
            throw new FileValidationException(INVALID_FILENAME_MESSAGE);
        }
        String normalized = entryName.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.startsWith("../") || normalized.endsWith("/..") || normalized.contains("/../")) {
            throw new FileValidationException(PATH_COMPONENT_FILENAME_MESSAGE);
        }
        if (normalized.equals(".") || normalized.equals("..") || normalized.contains("/./") || normalized.startsWith("./") || normalized.endsWith("/.")) {
            throw new FileValidationException(PATH_COMPONENT_FILENAME_MESSAGE);
        }
        try {
            if (FilenameUtils.getPrefixLength(normalized) > 0) {
                throw new FileValidationException(PATH_COMPONENT_FILENAME_MESSAGE);
            }
        } catch (IllegalArgumentException e) {
            throw new FileValidationException(INVALID_FILENAME_MESSAGE, e);
        }
    }

    /**
     * Validates that the path string resolves to a file within the allowed directory.
     * Convenience overload that constructs the {@link File} internally, avoiding a
     * bare {@code new File(taintedPath)} at the call site and keeping the taint sink
     * inside this utility where SpotBugs can track containment.
     *
     * @param path the file path to validate; must be non-null and non-empty
     * @param allowedDir the directory the resolved file must be within
     * @return the validated File
     * @throws SecurityException if the path is null/empty or resolves outside allowedDir
     */
    public static File validateExistingPath(String path, File allowedDir) {
        if (path == null || path.isBlank()) {
            throw new SecurityException("File path is null or empty");
        }
        return validateExistingPath(new File(path), allowedDir);
    }

    /**
     * Returns DOCUMENT_DIR as a canonical directory, failing closed when it is not configured.
     *
     * @return the canonical DOCUMENT_DIR directory
     * @throws IOException if DOCUMENT_DIR is unavailable or cannot be canonicalized
     */
    public static File getRequiredDocumentDirectory() throws IOException {
        String documentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        if (documentDir == null || documentDir.isBlank()) {
            throw new IOException("DOCUMENT_DIR not configured; rejecting file access");
        }
        File canonicalDocumentDir = new File(documentDir).getCanonicalFile();
        if (!canonicalDocumentDir.isDirectory()) {
            throw new IOException("DOCUMENT_DIR is not an existing directory; rejecting file access");
        }
        return canonicalDocumentDir;
    }

    /**
     * Validates that the path string resolves to an existing file under DOCUMENT_DIR.
     * Use this for application-created lab file paths that must fail closed when
     * DOCUMENT_DIR is unavailable.
     *
     * @param path the file path to validate; must be non-null and non-empty
     * @return the validated File
     * @throws IOException if DOCUMENT_DIR is unavailable or cannot be canonicalized
     * @throws SecurityException if the path is null/empty or resolves outside DOCUMENT_DIR
     */
    public static File validateExistingDocumentPath(String path) throws IOException {
        return validateExistingPath(path, getRequiredDocumentDirectory());
    }

    // ========================================================================
    // UPLOAD VALIDATION - For validating uploaded files
    // ========================================================================

    /**
     * Validates an uploaded source file is from an allowed temp location.
     * Use this when reading uploaded file content without writing to a destination.
     *
     * @param sourceFile the uploaded file (from Struts2/Tomcat)
     * @return the canonicalized validated File - use this return value for all subsequent file operations
     * @throws SecurityException if validation fails
     */
    public static File validateUpload(File sourceFile) {
        return validateSource(sourceFile, null);
    }

    /**
     * Validates file content supplied by the Struts upload interceptor.
     *
     * @param uploadedContent the content returned from {@code UploadedFile#getContent()}
     * @return the canonicalized validated File - use this return value for all subsequent file operations
     * @throws SecurityException if the upload content is not a file or validation fails
     */
    public static File validateUploadContent(Object uploadedContent) {
        if (!(uploadedContent instanceof File sourceFile)) {
            throw new SecurityException("Uploaded content is not a file");
        }
        return validateUpload(sourceFile);
    }

    /**
     * Opens a stream for an uploaded temp file after canonicalizing it and
     * confirming it resolves inside an allowed upload temp directory.
     *
     * <p>Use this helper instead of opening upload paths directly from action
     * classes so the validation and file-open operation stay together.</p>
     *
     * @param sourceFile the uploaded file (from Struts2/Tomcat)
     * @return InputStream for the validated upload content; caller must close it
     * @throws IOException if validation fails or the stream cannot be opened
     * @since 2026-05-21
     */
    public static InputStream openValidatedUploadInputStream(File sourceFile) throws IOException {
        File validatedFile;
        try {
            validatedFile = validateUpload(sourceFile);
        } catch (SecurityException e) {
            throw new IOException("Invalid upload file", e);
        }

        return new FileInputStream(validatedFile); // codeql[java/path-injection] -- validateUpload restricts to allowed temp dirs.
    }

    /**
     * Creates a temporary file with owner-only read/write permissions on POSIX
     * filesystems, avoiding the world-readable default of {@link File#createTempFile}
     * for sensitive (e.g. PHI) content such as generated PDFs. Falls back to a default
     * temporary file on non-POSIX filesystems (for example Windows).
     *
     * @param prefix temp file name prefix; should be a validated/generated component
     *        (e.g. via {@link #validateGeneratedFileName(String)}), not raw user input
     * @param suffix temp file name suffix, appended verbatim (no leading {@code .} is inserted);
     *        may be null, in which case {@code .tmp} is used
     * @return the created temporary File
     * @throws IOException if the file cannot be created
     */
    public static File createSecureTempFile(String prefix, String suffix) throws IOException {
        try {
            return Files.createTempFile(prefix, suffix,
                    PosixFilePermissions.asFileAttribute(
                            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)))
                    .toFile();
        } catch (UnsupportedOperationException e) {
            // Non-POSIX filesystem (e.g. Windows): owner-only permissions are unavailable, so fall
            // back to a default temp file. Log it so the degraded (default-permission) mode is auditable.
            logger.debug("POSIX permissions unsupported on this filesystem; created temp file with default permissions");
            return Files.createTempFile(prefix, suffix).toFile(); // NOSONAR java:S5443 - non-POSIX fallback; OWNER-only perms unsupported on this platform
        }
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
     * @throws FileValidationException if the destination filename is null, empty, hidden,
     * contains a null byte, or has a blocked final extension
     * @throws SecurityException if source-file validation fails, the destination directory is null,
     * or the resulting destination path is outside it
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

    private static File validateSource(File sourceFile, File expectedBaseDir) {
        if (sourceFile == null) {
            throw new SecurityException("Uploaded file is null");
        }

        // Canonicalize first to resolve symlinks before any filesystem checks
        File canonicalFile;
        try {
            canonicalFile = sourceFile.getCanonicalFile();
        } catch (IOException e) {
            logger.error("Cannot resolve canonical path for uploaded file", e);
            throw new SecurityException("Cannot resolve upload file path");
        }

        if (!canonicalFile.exists()) {
            throw new SecurityException("Uploaded file does not exist");
        }

        if (!canonicalFile.isFile()) {
            throw new SecurityException("Uploaded file is not a regular file");
        }

        // Check expected base directory first
        if (expectedBaseDir != null && isWithinDirectory(canonicalFile, expectedBaseDir)) {
            return canonicalFile;
        }

        // Fallback: check temp directories - they're always valid sources for uploads
        if (isInAllowedTempDirectory(canonicalFile)) {
            return canonicalFile;
        }

        logger.error("Invalid upload source path");
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
                logger.error("Path {} is outside allowed directory {}",
                        LogSafe.sanitize(fileCanonical, 1024),
                        LogSafe.sanitize(baseCanonical, 1024)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
                throw new SecurityException("Invalid file path");
            }
        } catch (IOException e) {
            logger.error("Error validating file path", e);
            throw new SecurityException("Error validating file path");
        }
    }

    private static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new FileValidationException(INVALID_FILENAME_MESSAGE);
        }

        // Use Apache Commons IO to extract just the filename
        String baseName;
        try {
            baseName = FilenameUtils.getName(fileName);
        } catch (IllegalArgumentException e) {
            logger.warn("Filename parser rejected invalid filename");
            throw new FileValidationException(INVALID_FILENAME_MESSAGE, e);
        }

        // Reject hidden files (starting with .)
        if (baseName.startsWith(".")) {
            logger.warn("Hidden filenames not allowed");
            throw new FileValidationException(HIDDEN_FILENAME_MESSAGE);
        }

        // Ensure the result is not empty
        if (baseName.trim().isEmpty()) {
            logger.warn("Filename became empty after sanitization");
            throw new FileValidationException(INVALID_FILENAME_MESSAGE);
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
