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
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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

    /**
     * Resolves the configured document root on each call rather than capturing it in a load-time
     * {@code static final}, so a test can point it at an isolated temporary directory (and so a
     * reconfigured root is picked up without a redeploy).
     */
    private static String baseDocumentDir() {
        return CarlosProperties.getInstance().getProperty("BASE_DOCUMENT_DIR");
    }

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
            log.error("Invalid page number provided: {}", pageNum);
            return null;
        }

        // Sanitize the filename to prevent path traversal
        String sanitizedFilename = sanitizeFileName(filename);
        
        // Additional validation after sanitization
        if (sanitizedFilename.isEmpty() || "invalid_filename".equals(sanitizedFilename)) {
            log.error("Filename failed sanitization: {}", LogSafe.sanitize(filename));
            return null;
        }

        Path documentCacheDir = getDocumentCacheDirectory(loggedInInfo);
        Path normalizedCacheDir = documentCacheDir.normalize().toAbsolutePath();
        
        // Construct the cache filename securely
        String cacheFileName = sanitizedFilename + "_" + pageNum + ".png";
        
        // Validate the cache filename doesn't contain any path separators
        if (cacheFileName.contains("/") || cacheFileName.contains("\\") || cacheFileName.contains("..")) {
            log.error("Invalid characters in cache filename: {}", LogSafe.sanitize(cacheFileName));
            return null;
        }
        
        // Safely construct the path using resolve() with the sanitized filename
        Path outfile = normalizedCacheDir.resolve(cacheFileName);
        outfile = outfile.normalize().toAbsolutePath();
        
        // Verify the file is within the cache directory (defense in depth)
        try {
            outfile = PathValidationUtils.validateExistingPath(outfile.toFile(), normalizedCacheDir.toFile()).toPath();
        } catch (SecurityException e) {
            log.error("Path traversal attempt detected in hasCacheVersion2: {}", LogSafe.sanitize(filename));
            return null;
        }
        
        // Additional check: ensure the resolved path is not a directory
        if (Files.exists(outfile) && Files.isDirectory(outfile)) {
            log.error("Resolved path is a directory, not a file: {}", LogSafe.sanitize(String.valueOf(outfile)));
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

        return resolveDocumentCacheDirectory();
    }

    /**
     * Resolves (creating if absent) the document preview-cache directory without a privilege check.
     * {@link #getDocumentCacheDirectory} wraps this behind an {@code _edoc} READ gate; the fax-preview
     * flush path reaches it through {@link #removeCacheVersions}, which is authorized by its own caller's
     * {@code _fax} READ and must not require {@code _edoc} (cubic SJD9t).
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    private Path resolveDocumentCacheDirectory() {
        // context.getContextPath() is "/carlos" (leading slash) in production. Paths.get(String,
        // String...) JOINS its segments and collapses redundant separators — it does NOT re-anchor on
        // a leading slash the way Path.resolve("/carlos") would — so baseDocumentDir() is preserved
        // either way. Strip a single leading separator so the intent is explicit and stays correct if
        // this is ever refactored to resolve() (copilot/cubic HNrA/HXQl/HYt6 — a clarity/robustness
        // change; no behavioral bug here).
        Path cacheDir = Paths.get(baseDocumentDir(), stripLeadingSeparator(context.getContextPath()), DOCUMENT_CACHE_DIRECTORY);

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
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public Path createCacheVersion2(LoggedInInfo loggedInInfo, String sourceDirectory, String filename, Integer pageNum) {

        // Rendering a page image of an already-readable document is a read-side operation (it only
        // derives a cache thumbnail). Require _edoc READ — matching the sibling cache methods above —
        // so read-only workflows such as fax preview are not forced to hold _edoc WRITE.
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ, "")) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        // Sanitize the filename to prevent path traversal
        String sanitizedFilename = sanitizeFileName(filename);

        // Validate the source directory up front. The cache entry is scoped to the canonical source
        // directory below, so resolving it first is required to build the lookup key — and it means
        // two different temp/document directories that happen to reuse the same PDF filename can
        // never collide on a shared "<filename>_<page>.png" cache key and hand back one another's
        // rendered page (cubic SCQQI). A caller with only a filename (no source) cannot mint a
        // preview at all, which is the intended contract for the fax-preview flow.
        if (sourceDirectory == null || sourceDirectory.trim().isEmpty()) {
            log.error("Invalid source directory: null or empty");
            return null;
        }

        // Validate and normalize the source directory to an allowed preview location (a CARLOS-owned
        // temp subtree or the document root). Shared with removeCacheVersions so the writer and remover
        // agree on both the allowed-source set and the derived key (cubic SJD9x).
        Path normalizedSourceDir = resolveAllowedPreviewSourceDir(sourceDirectory);
        if (normalizedSourceDir == null) {
            return null;
        }
        boolean sourceDirectoryInAllowedTemp = PathValidationUtils.isInApplicationTempDirectory(normalizedSourceDir.toFile());

        // Source-scoped cache identity: fold a stable digest of the canonical source directory into
        // the cache filename so both the lookup and the write are unique per source (cubic SCQQI).
        // The filename portion is length-bounded so an overlong (but valid) PDF name can't push the
        // cache component past the filesystem's per-component limit (cubic HmTc). removeCacheVersions
        // reuses the same helper so flush() removes exactly what this writes (copilot SI8_2).
        String scopedCacheName = scopedCacheBaseName(sanitizedFilename, normalizedSourceDir);

        Path cacheFilePath = hasCacheVersion2(loggedInInfo, scopedCacheName, pageNum);

        if (cacheFilePath != null) {
            log.debug("Preview cache hit for page {} of {}", pageNum, LogSafe.sanitize(sanitizedFilename));
        } else {
            log.debug("Preview cache miss for page {} of {}; rendering", pageNum, LogSafe.sanitize(sanitizedFilename));
        }

        /*
         * create a new cache file if an existing cache file is not returned.
         */
        if (cacheFilePath == null) {
            Path sourceFile = normalizedSourceDir.resolve(sanitizedFilename).normalize().toAbsolutePath();

            // Ensure source file is within the source directory and, for temp previews, remains in an approved temp location.
            try {
                sourceFile = PathValidationUtils.validateExistingPath(sourceFile.toFile(), normalizedSourceDir.toFile()).toPath();
                if (sourceDirectoryInAllowedTemp && !PathValidationUtils.isInApplicationTempDirectory(sourceFile.toFile())) {
                    // Security-relevant event: keep the sanitized filename so it can be correlated
                    // with the request that supplied it.
                    log.error("Source file is outside allowed temp path: {}", LogSafe.sanitize(sanitizedFilename));
                    return null;
                }
            } catch (SecurityException e) {
                log.error("Path traversal attempt in source file: {}", LogSafe.sanitize(sanitizedFilename), e);
                return null;
            }

            Path documentCacheDir = getDocumentCacheDirectory(loggedInInfo);
            Path normalizedCacheDir = documentCacheDir.normalize().toAbsolutePath();
            cacheFilePath = normalizedCacheDir.resolve(scopedCacheName + "_" + pageNum + ".png");
            cacheFilePath = cacheFilePath.normalize().toAbsolutePath();
            
            // Verify the cache file path is within the cache directory
            try {
                cacheFilePath = PathValidationUtils.validateExistingPath(cacheFilePath.toFile(), normalizedCacheDir.toFile()).toPath();
            } catch (SecurityException e) {
                log.error("Path traversal attempt in cache file creation: {}", LogSafe.sanitize(filename));
                return null;
            }

            try (PDDocument document = Loader.loadPDF(sourceFile.toFile())) {
                int pageIndex = pageNum - 1;
                int pageCount = document.getNumberOfPages();

                // Validate page index is within bounds
                if (pageIndex < 0 || pageIndex >= pageCount) {
                    log.error("Requested page {} is out of range for document with {} pages", pageNum, pageCount);
                    return null;
                }

                PDFRenderer renderer = new PDFRenderer(document);
                // Render at 96 DPI to match jpedal settings
                // Note: jpedal uses 1-based page indexing, PDFBox uses 0-based
                BufferedImage image_to_save = renderer.renderImageWithDPI(pageIndex, 96, ImageType.RGB);

                // Render to a same-directory temp file and move it into place atomically: writing
                // straight to the final cache path let a concurrent request's hasCacheVersion2 see
                // the half-written file as a cache hit and serve a truncated PNG, and let a
                // concurrent flush delete the file out from under this writer. The ".tmp" suffix
                // keeps partials invisible to the page-suffix pattern removeCacheVersions matches.
                Path partialFile = Files.createTempFile(normalizedCacheDir, scopedCacheName + "_", ".png.tmp");
                try {
                    // Check ImageIO.write success (returns false on failure)
                    if (!ImageIO.write(image_to_save, "png", partialFile.toFile())) {
                        log.error("Failed to write PNG image to cache file: {}", LogSafe.sanitize(String.valueOf(cacheFilePath)));
                        return null;
                    }
                    try {
                        Files.move(partialFile, cacheFilePath, StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException e) {
                        // Same-directory rename is atomic on POSIX; this fallback only exists for
                        // filesystems that cannot promise atomicity, where a brief window is still
                        // better than always writing in place.
                        Files.move(partialFile, cacheFilePath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(partialFile);
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
                log.error("Attempt to delete file outside of cache directory: {}", LogSafe.sanitize(fileName));
                throw new SecurityException("Path traversal attempt detected");
            }
            
            // Additional check - ensure we're not deleting directories
            if (Files.isDirectory(normalizedPath)) {
                log.error("Attempt to delete a directory instead of a file: {}", LogSafe.sanitize(fileName));
                return false;
            }
            
            return Files.deleteIfExists(normalizedPath);
        } catch (SecurityException e) {
            log.error("Security violation while attempting to delete cache file: {}", LogSafe.sanitize(fileName), e);
            throw e; // Re-throw security exceptions
        } catch (IOException e) {
            log.error("Error while deleting temp cache image file {}", LogSafe.sanitize(fileName), e);
        }
        return false;
    }

    /**
     * Removes every cached page image {@code createCacheVersion2} wrote for one source PDF. The writer
     * names pages {@code <boundedFilename>_<sourceKey>_<page>.png}, so a single-file removal keyed on the
     * raw PDF name (as {@link #removeCacheVersion} does) matches nothing — this method rebuilds the same
     * source-scoped prefix and deletes all of its page images (copilot SI8_2). The source directory is
     * normalized the way {@code createCacheVersion2}'s CARLOS-owned-temp branch normalizes it, which is
     * the branch the fax-preview flow (the only caller) exercises. Matching is done in Java rather than a
     * directory glob so a filename containing glob metacharacters cannot misfire.
     *
     * @return the number of cache page images removed (0 if none matched or the source could not be keyed)
     */
    @Override
    // FindSecBugs PATH_TRAVERSAL_IN: each candidate is confined to the cache directory via
    // PathValidationUtils.validateExistingPath before deletion; the source is validated to an allowed
    // preview location and the key derives from that validated path plus server config.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public final int removeCacheVersions(LoggedInInfo loggedInInfo, String sourceDirectory, String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return 0;
        }
        // No _edoc gate: the only caller (FaxManagerImpl.flush) is already authorized by _fax READ and
        // this removes only that preview's own regenerable page-image cache. Requiring _edoc here broke
        // the fax-cancel/flush flow for users holding _fax READ but not _edoc READ, throwing before the
        // approved temp file could be deleted (cubic SJD9t). The source is validated to the same allowed
        // preview locations the writer accepts, so a caller cannot target caches for arbitrary paths
        // (cubic SJD9x).
        Path normalizedSourceDir = resolveAllowedPreviewSourceDir(sourceDirectory);
        if (normalizedSourceDir == null) {
            return 0;
        }
        String scopedPrefix = scopedCacheBaseName(sanitizeFileName(filename), normalizedSourceDir) + "_";

        Path normalizedCacheDir = resolveDocumentCacheDirectory().normalize().toAbsolutePath();
        if (!Files.isDirectory(normalizedCacheDir)) {
            return 0;
        }

        int removed = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(normalizedCacheDir)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                // Only this source's page images: "<scopedPrefix><digits>.png".
                if (!name.startsWith(scopedPrefix) || !name.endsWith(".png")) {
                    continue;
                }
                String pageSegment = name.substring(scopedPrefix.length(), name.length() - ".png".length());
                if (pageSegment.isEmpty() || !pageSegment.chars().allMatch(Character::isDigit)) {
                    continue;
                }
                try {
                    Path validated = PathValidationUtils.validateExistingPath(entry.toFile(), normalizedCacheDir.toFile()).toPath();
                    if (!Files.isDirectory(validated) && Files.deleteIfExists(validated)) {
                        removed++;
                    }
                } catch (SecurityException | IOException e) {
                    log.error("Unable to remove preview cache page {}", LogSafe.sanitize(name), e);
                }
            }
        } catch (IOException e) {
            log.error("Error while clearing source-scoped preview cache", e);
        }
        if (removed > 0) {
            log.debug("Cleared {} preview cache page image(s) for {} (provider={})", removed,
                    LogSafe.sanitize(filename),
                    LogSafe.sanitize(loggedInInfo == null ? null : loggedInInfo.getLoggedInProviderNo()));
        }
        return removed;
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
     * Derives a short, stable, filename-safe cache-key segment from a canonical source directory so
     * a page-preview cache entry is scoped to its source. Two different source directories that reuse
     * the same PDF filename therefore resolve to distinct cache files and can never return one
     * another's rendered page.
     *
     * @param sourceDirectory canonicalized, normalized source directory
     * @return 16-character lowercase hex digest of the directory path (SHA-256 truncated)
     */
    private static String sourceDirectoryCacheKey(Path sourceDirectory) {
        return sha256Hex16(sourceDirectory.toString());
    }

    /** First 8 bytes of the SHA-256 digest of {@code input}, rendered as 16 lowercase hex chars. */
    private static String sha256Hex16(String input) {
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder key = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                key.append(Character.forDigit((hash[i] >> 4) & 0xF, 16));
                key.append(Character.forDigit(hash[i] & 0xF, 16));
            }
            return key.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a required JCA algorithm; fall back to a non-negative hashCode key if absent.
            // Zero-pad to a fixed 8 hex chars so leading zeros are preserved (avoids collisions that
            // Integer.toHexString would introduce by dropping them).
            return String.format("%08x", input.hashCode() & 0x7fffffff);
        }
    }

    /**
     * Bounds the filename portion of a source-scoped cache name. Short filenames pass through
     * verbatim; an overlong one is replaced with a fixed-length digest that keeps its extension, so
     * the final {@code <name>_<sourceKey>_<page>.png} cache component cannot exceed the filesystem's
     * per-component length limit and a legitimately long PDF name can still be previewed (cubic HmTc).
     */
    private static String boundedCacheBaseName(String filename) {
        final int maxBaseLength = 120;
        if (filename.length() <= maxBaseLength) {
            return filename;
        }
        int dot = filename.lastIndexOf('.');
        String extension = (dot > 0 && dot < filename.length() - 1) ? filename.substring(dot) : "";
        // Bound the extension too: a filename whose "extension" is itself pathologically long would
        // otherwise re-inflate the digest-based name past the component limit. Drop it in that case —
        // the 16-char digest still keeps the cache entry unique and source-scoped (cubic SIt6B).
        if (extension.length() > maxBaseLength - 16) {
            extension = "";
        }
        return sha256Hex16(filename) + extension;
    }

    /**
     * Builds the source-scoped cache base name {@code <boundedFilename>_<sourceKey>} shared by the
     * preview-cache writer ({@link #createCacheVersion2}) and remover ({@link #removeCacheVersions}).
     * Having a single formula means {@code flush()} deletes exactly the page images the writer produced,
     * even after the filename/source-key scoping changed the on-disk names (copilot SI8_2).
     *
     * @param sanitizedFilename filename already run through {@link #sanitizeFileName}
     * @param normalizedSourceDir source directory normalized identically on both the write and remove paths
     */
    private static String scopedCacheBaseName(String sanitizedFilename, Path normalizedSourceDir) {
        return boundedCacheBaseName(sanitizedFilename) + "_" + sourceDirectoryCacheKey(normalizedSourceDir);
    }

    /**
     * Resolves and validates a preview source directory to the same allowed locations the cache writer
     * accepts: a CARLOS-owned temp subtree ({@link PathValidationUtils#isInApplicationTempDirectory}) or
     * the document root. The temp branch is scoped to application-owned temp subtrees, not the whole
     * shared temp root, so a caller with {@code _edoc}/{@code _fax} READ cannot point the cache at an
     * unrelated file another process left in {@code java.io.tmpdir} or Tomcat work (cubic SCQPk).
     *
     * <p>Shared between {@link #createCacheVersion2} and {@link #removeCacheVersions} so the remover
     * cannot clean caches for arbitrary caller-supplied paths and both derive the key identically
     * (cubic SJD9x).</p>
     *
     * @return the canonicalized source directory (falling back to normalized-absolute only when
     *         canonicalization fails; the document branch is additionally containment-validated),
     *         or {@code null} if it is not an allowed, existing preview source
     */
    // FindSecBugs PATH_TRAVERSAL_IN: baseDocumentDir() is trusted server config, and the caller-supplied
    // sourceDirectory is confined to a CARLOS-owned temp subtree or the document root before use; the
    // resolved directory is only stat'd and hashed into a cache key here, never read as file content.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    private Path resolveAllowedPreviewSourceDir(String sourceDirectory) {
        if (sourceDirectory == null || sourceDirectory.trim().isEmpty()) {
            return null;
        }
        Path baseDocumentPath = Paths.get(baseDocumentDir()).normalize().toAbsolutePath();
        try {
            // Canonicalize (resolve symlinks) so the source-scoped cache key matches the write side, which
            // keys off EDocUtil.resolvePath()'s canonical path. Without this, a symlinked java.io.tmpdir
            // (e.g. macOS /tmp -> /private/tmp) makes the write key hash the canonical dir while flush hashes
            // the symlinked dir, so removeCacheVersions matches nothing and the SI8_2 flush fix silently
            // no-ops on such hosts. Falls back to normalize() if the path cannot be canonicalized.
            Path normalizedSourceDir;
            try {
                normalizedSourceDir = Paths.get(sourceDirectory).toFile().getCanonicalFile().toPath();
            } catch (IOException canonicalizationFailure) {
                normalizedSourceDir = Paths.get(sourceDirectory).normalize().toAbsolutePath();
            }
            if (!PathValidationUtils.isInApplicationTempDirectory(normalizedSourceDir.toFile())) {
                normalizedSourceDir = PathValidationUtils.validateExistingPath(
                        normalizedSourceDir.toFile(), baseDocumentPath.toFile()).toPath();
            }
            // The directory is containment-validated above (application-temp or document root) and is
            // only stat'd and hashed into a cache key, never read as content — the suppression must sit
            // on the flagged sink line itself to take effect.
            if (!Files.exists(normalizedSourceDir) || !Files.isDirectory(normalizedSourceDir)) { // codeql[java/path-injection]
                if (log.isErrorEnabled()) {
                    log.error("Source directory does not exist or is not a directory: {}", LogSafe.sanitize(sourceDirectory, 1024));
                }
                return null;
            }
            return normalizedSourceDir;
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error("Invalid source directory path: {}", LogSafe.sanitize(sourceDirectory, 1024), e);
            }
            return null;
        }
    }

    /** Removes a single leading path separator so a servlet context path ("/carlos") joins as a relative segment. */
    private static String stripLeadingSeparator(String segment) {
        if (segment == null || segment.isEmpty()) {
            return "";
        }
        return (segment.charAt(0) == '/' || segment.charAt(0) == File.separatorChar)
                ? segment.substring(1)
                : segment;
    }

    /**
     * Returns (creating if absent) the CARLOS-owned temporary root beneath {@code java.io.tmpdir}.
     * Application-generated temp files (e.g. fax-preview PDFs) live under this named root so the fax
     * preview endpoints can accept them via {@link PathValidationUtils#isInApplicationTempDirectory}
     * without trusting the entire shared temp space.
     */
    private static Path applicationTempParent() throws IOException {
        Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"));
        Path parent = tmpDir.resolve(PathValidationUtils.APPLICATION_TEMP_ROOT_NAME);
        Path created = Files.createDirectories(parent);
        // Reject if carlos-temp itself is a symlink, and reject if it resolves outside the real
        // java.io.tmpdir. Both guards defend against a local process that pre-created/swapped the root
        // to redirect application temp writes, but the leaf-symlink guard also keeps this write path
        // consistent with the read side: isInApplicationTempDirectory canonicalizes files (resolving a
        // symlinked carlos-temp), so a symlinked root would still pass the real-path check here yet make
        // every file written beneath it fail the downstream first-segment check — a silent self-DoS on
        // fax-preview temp files. Comparing real paths (rather than rejecting all symlinks) still
        // tolerates a legitimately symlinked java.io.tmpdir *ancestor* (e.g. macOS /tmp -> /private/tmp),
        // where both sides canonicalize consistently. The residual check-to-use window is bounded
        // because the files written beneath this root are private (cubic SIZkO, SIwOk, HtRV).
        if (Files.isSymbolicLink(created) || !created.toRealPath().startsWith(tmpDir.toRealPath())) {
            throw new IOException("Application temp root resolves outside java.io.tmpdir: " + created);
        }
        return created;
    }

    /**
     * Save a file to the temporary directory from ByteArrayOutputStream
     *
     * @throws IOException
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public Path saveTempFile(final String fileName, ByteArrayOutputStream os, String fileType) throws IOException {
        Path directory = Files.createTempDirectory(applicationTempParent(), TEMP_PDF_DIRECTORY + System.currentTimeMillis());
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

        Path directory = Files.createTempDirectory(applicationTempParent(), DEFAULT_GENERIC_TEMP + System.currentTimeMillis());
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
            log.error("Path traversal attempt in getOscarDocument: {}", LogSafe.sanitize(fileName));
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
                log.error("Invalid file path provided: {}", LogSafe.sanitize(tempFilePath));
                return null;
            }

            // Get source and destination directories
            File documentDir = new File(getDocumentDirectory());
            File sourceFile = new File(tempFilePath);
            File destinationFile = new File(documentDir, sanitizedFileName);

            // Validate that source file exists and is a regular file
            if (!sourceFile.exists() || !sourceFile.isFile()) {
                log.error("Source file does not exist or is not a regular file: {}", LogSafe.sanitize(tempFilePath));
                return null;
            }

            // Validate destination path using PathValidationUtils
            destinationFile = PathValidationUtils.validatePath(sanitizedFileName, documentDir);

            // Never overwrite an existing document: DOCUMENT_DIR filenames are referenced by
            // persisted records, so a basename collision (two promotions reusing one name) must
            // yield a fresh unique name rather than silently replacing another document's content
            // — and the copy below deliberately omits REPLACE_EXISTING so a race still fails
            // closed instead of clobbering.
            if (destinationFile.exists()) {
                String uniquifiedName = FilenameUtils.getBaseName(sanitizedFileName)
                        + "-" + System.currentTimeMillis()
                        + (FilenameUtils.getExtension(sanitizedFileName).isEmpty()
                                ? "" : "." + FilenameUtils.getExtension(sanitizedFileName));
                destinationFile = PathValidationUtils.validatePath(uniquifiedName, documentDir);
            }

            try {
                Files.copy(sourceFile.toPath(), destinationFile.toPath());
            } catch (FileAlreadyExistsException e) {
                log.error("Refusing to overwrite existing document {}", LogSafe.sanitize(destinationFile.getName()), e);
                return null;
            }

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
     *
     * <p>Resolves live from {@code CarlosProperties} ({@code DOCUMENT_DIR}, or
     * {@code <BASE_DOCUMENT_DIR>/document} when unset) rather than the load-time
     * {@link NioFileManager#DOCUMENT_DIRECTORY} snapshot, so the document root stays consistent with
     * the live {@link #baseDocumentDir()} that {@link #createCacheVersion2} and
     * {@link #getDocumentCacheDirectory} validate against — a runtime {@code BASE_DOCUMENT_DIR} change
     * can no longer split document storage and cache/source validation across two different roots
     * (cubic HYtv).</p>
     */
    // FindSecBugs PATH_TRAVERSAL_IN: both resolved paths derive from trusted server configuration
    // (CarlosProperties DOCUMENT_DIR and the BASE_DOCUMENT_DIR system property), never user input;
    // the value is a directory root the deployment owns, not a request-supplied filename (SI3Hn/SI3Hq).
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    private String getDocumentDirectory() {
        // Resolve live (HYtv), but keep the legacy recovery: if the configured document dir is stale or
        // not an actual directory, fall back to <BASE_DOCUMENT_DIR>/document rather than returning an
        // unusable path (cubic SIt6A).
        String documentDirectory = CarlosProperties.getInstance().getDocumentDirectory();
        if (documentDirectory == null || !Files.isDirectory(Paths.get(documentDirectory))) {
            // Path is a deployment-owned document root, not PHI; safe to log to surface a misconfiguration.
            log.warn("Configured document directory is unset or not a directory; falling back to <BASE_DOCUMENT_DIR>/document");
            return Paths.get(baseDocumentDir(), "document").toString();
        }
        return documentDirectory;
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
