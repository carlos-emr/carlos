/**
 * Copyright (c) 2025. Magenta Health. All Rights Reserved.
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
 * Magenta Health
 * Toronto, Ontario, Canada
 */
package ca.openosp.openo.utility;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for PathValidationUtils security utility class.
 *
 * <p>This test class verifies path traversal prevention, filename sanitization,
 * and upload validation to ensure security-critical operations work correctly.</p>
 *
 * <p><b>Test Categories:</b></p>
 * <ul>
 *   <li>Path validation - valid paths within allowed directories</li>
 *   <li>Path traversal prevention - blocking ../ and similar attacks</li>
 *   <li>Filename sanitization - stripping paths, rejecting hidden files</li>
 *   <li>Upload validation - temp file pattern matching and directory checks</li>
 *   <li>Edge cases - null inputs, empty strings, equal paths</li>
 * </ul>
 *
 * @since 2025-12-11
 * @see PathValidationUtils
 */
@DisplayName("PathValidationUtils Security Tests")
@Tag("unit")
@Tag("fast")
@Tag("security")
public class PathValidationUtilsTest {

    @TempDir
    Path tempDir;

    private File allowedDir;

    @BeforeEach
    void setUp() {
        allowedDir = tempDir.toFile();
    }

    // ========================================================================
    // PATH VALIDATION - Valid Paths
    // ========================================================================

    @Nested
    @DisplayName("Valid Path Tests")
    class ValidPathTests {

        @Test
        @DisplayName("should return valid file path when filename is simple")
        void shouldReturnValidFilePath_whenFilenameIsSimple() {
            // Given
            String filename = "test.txt";

            // When
            File result = PathValidationUtils.validatePath(filename, allowedDir);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getParentFile()).isEqualTo(allowedDir);
            assertThat(result.getName()).isEqualTo("test.txt");
        }

        @Test
        @DisplayName("should return valid file path when filename has extension")
        void shouldReturnValidFilePath_whenFilenameHasExtension() {
            // Given
            String filename = "document.pdf";

            // When
            File result = PathValidationUtils.validatePath(filename, allowedDir);

            // Then
            assertThat(result.getName()).isEqualTo("document.pdf");
        }

        @Test
        @DisplayName("should return valid file path when filename has multiple dots")
        void shouldReturnValidFilePath_whenFilenameHasMultipleDots() {
            // Given
            String filename = "file.backup.tar.gz";

            // When
            File result = PathValidationUtils.validatePath(filename, allowedDir);

            // Then
            assertThat(result.getName()).isEqualTo("file.backup.tar.gz");
        }

        @Test
        @DisplayName("should strip directory components from filename")
        void shouldStripDirectoryComponents_whenFilenameContainsPath() {
            // Given - filename with path prefix that should be stripped
            String filename = "somedir/subdir/actualfile.txt";

            // When
            File result = PathValidationUtils.validatePath(filename, allowedDir);

            // Then - only the filename part should remain
            assertThat(result.getName()).isEqualTo("actualfile.txt");
            assertThat(result.getParentFile()).isEqualTo(allowedDir);
        }
    }

    // ========================================================================
    // PATH TRAVERSAL PREVENTION
    // ========================================================================

    @Nested
    @DisplayName("Path Traversal Prevention Tests")
    class PathTraversalTests {

        @ParameterizedTest
        @DisplayName("should reject path traversal attempts")
        @ValueSource(strings = {
            "../etc/passwd",
            "../../etc/passwd",
            "../../../etc/passwd",
            "..\\etc\\passwd",
            "..\\..\\etc\\passwd",
            "foo/../../../etc/passwd",
            "foo/bar/../../../etc/passwd"
        })
        void shouldRejectPathTraversalAttempts(String maliciousPath) {
            // When/Then - The sanitizer strips path components, so these become just the filename
            // The actual file would be "passwd" in the allowed directory
            File result = PathValidationUtils.validatePath(maliciousPath, allowedDir);

            // Verify the result is within allowedDir, not traversing out
            assertThat(result.getParentFile()).isEqualTo(allowedDir);
            assertThat(result.getName()).isEqualTo("passwd");
        }

        @Test
        @DisplayName("should treat encoded traversal as literal filename")
        void shouldTreatEncodedTraversal_asLiteralFilename() {
            // Given - URL encoded path traversal attempt that doesn't start with dot
            // FilenameUtils.getName treats %2F as literal characters, not path separators
            String filename = "foo%2F..%2F..%2Fetc%2Fpasswd";

            // When
            File result = PathValidationUtils.validatePath(filename, allowedDir);

            // Then - should be treated as a literal filename (no decoding happens)
            assertThat(result.getParentFile()).isEqualTo(allowedDir);
            assertThat(result.getName()).isEqualTo("foo%2F..%2F..%2Fetc%2Fpasswd");
        }
    }

    // ========================================================================
    // HIDDEN FILE REJECTION
    // ========================================================================

    @Nested
    @DisplayName("Hidden File Rejection Tests")
    class HiddenFileTests {

        @ParameterizedTest
        @DisplayName("should reject hidden files starting with dot")
        @ValueSource(strings = {
            ".htaccess",
            ".gitignore",
            ".env",
            ".bashrc"
        })
        void shouldRejectHiddenFiles(String hiddenFile) {
            // When/Then
            assertThatThrownBy(() -> PathValidationUtils.validatePath(hiddenFile, allowedDir))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("hidden files not allowed");
        }

        @Test
        @DisplayName("should allow non-hidden file from hidden directory path")
        void shouldAllowNonHiddenFile_whenFromHiddenDirectoryPath() {
            // Given - path contains hidden dir but filename itself is not hidden
            // FilenameUtils.getName extracts just "authorized_keys"
            String path = ".ssh/authorized_keys";

            // When
            File result = PathValidationUtils.validatePath(path, allowedDir);

            // Then - the filename "authorized_keys" is not hidden, so it's allowed
            assertThat(result.getName()).isEqualTo("authorized_keys");
        }

        @Test
        @DisplayName("should allow files with dot in middle of name")
        void shouldAllowFiles_whenDotInMiddleOfName() {
            // Given
            String filename = "file.with.dots.txt";

            // When
            File result = PathValidationUtils.validatePath(filename, allowedDir);

            // Then
            assertThat(result.getName()).isEqualTo("file.with.dots.txt");
        }
    }

    // ========================================================================
    // NULL AND EMPTY INPUT HANDLING
    // ========================================================================

    @Nested
    @DisplayName("Null and Empty Input Tests")
    class NullEmptyInputTests {

        @Test
        @DisplayName("should throw SecurityException when filename is null")
        void shouldThrowSecurityException_whenFilenameIsNull() {
            // When/Then
            assertThatThrownBy(() -> PathValidationUtils.validatePath(null, allowedDir))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("null or empty");
        }

        @Test
        @DisplayName("should throw SecurityException when filename is empty")
        void shouldThrowSecurityException_whenFilenameIsEmpty() {
            // When/Then
            assertThatThrownBy(() -> PathValidationUtils.validatePath("", allowedDir))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("null or empty");
        }

        @Test
        @DisplayName("should throw SecurityException when filename is whitespace only")
        void shouldThrowSecurityException_whenFilenameIsWhitespaceOnly() {
            // When/Then
            assertThatThrownBy(() -> PathValidationUtils.validatePath("   ", allowedDir))
                .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("should throw SecurityException when allowedDir is null")
        void shouldThrowSecurityException_whenAllowedDirIsNull() {
            // When/Then
            assertThatThrownBy(() -> PathValidationUtils.validatePath("test.txt", null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("null");
        }
    }

    // ========================================================================
    // UPLOAD VALIDATION - SOURCE FILE
    // ========================================================================

    @Nested
    @DisplayName("Upload Source Validation Tests")
    class UploadSourceValidationTests {

        @Test
        @DisplayName("should throw SecurityException when source file is null")
        void shouldThrowSecurityException_whenSourceFileIsNull() {
            // When/Then
            assertThatThrownBy(() -> PathValidationUtils.validateUpload(null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("null");
        }

        @Test
        @DisplayName("should throw SecurityException when source file does not exist")
        void shouldThrowSecurityException_whenSourceFileDoesNotExist() {
            // Given
            File nonExistentFile = new File(tempDir.toFile(), "nonexistent.tmp");

            // When/Then
            assertThatThrownBy(() -> PathValidationUtils.validateUpload(nonExistentFile))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("does not exist");
        }

        @Test
        @DisplayName("should throw SecurityException when source is a directory")
        void shouldThrowSecurityException_whenSourceIsDirectory() throws IOException {
            // Given
            File directory = tempDir.resolve("subdir").toFile();
            directory.mkdir();

            // When/Then
            assertThatThrownBy(() -> PathValidationUtils.validateUpload(directory))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not a regular file");
        }
    }

    // ========================================================================
    // TEMP FILE VALIDATION
    // ========================================================================

    @Nested
    @DisplayName("Temp File Validation Tests")
    class TempFileValidationTests {

        @ParameterizedTest
        @DisplayName("should accept files in system temp directory regardless of naming pattern")
        @ValueSource(strings = {
            "upload_c850bd37_8bd7_40cb_88ae_1e86670a61ee_00000000.tmp",
            "upload__37055a77_11ac9568d10__7ffe_00000033.tmp",
            "any_filename.tmp",
            "document.pdf",
            "random_file.txt"
        })
        void shouldAcceptFilesInTempDirectory(String tempFileName) throws IOException {
            // Given - create a file in system temp dir
            String systemTempDir = System.getProperty("java.io.tmpdir");
            File tempFile = new File(systemTempDir, tempFileName);
            tempFile.createNewFile();
            tempFile.deleteOnExit();

            // When/Then - should not throw (file is in allowed temp directory)
            assertThatCode(() -> PathValidationUtils.validateUpload(tempFile))
                .doesNotThrowAnyException();
        }
    }

    // ========================================================================
    // COMPLETE UPLOAD VALIDATION (SOURCE + DESTINATION)
    // ========================================================================

    @Nested
    @DisplayName("Complete Upload Validation Tests")
    class CompleteUploadValidationTests {

        @Test
        @DisplayName("should return valid destination when upload is valid")
        void shouldReturnValidDestination_whenUploadIsValid() throws IOException {
            // Given - create a valid temp file in system temp directory
            String systemTempDir = System.getProperty("java.io.tmpdir");
            File sourceFile = new File(systemTempDir, "upload_a1b2c3d4_5678_90ab_cdef_123456789abc_00000000.tmp");
            sourceFile.createNewFile();
            sourceFile.deleteOnExit();

            String userFilename = "myfile.txt";
            File destDir = tempDir.toFile();

            // When
            File result = PathValidationUtils.validateUpload(sourceFile, userFilename, destDir);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getParentFile()).isEqualTo(destDir);
            assertThat(result.getName()).isEqualTo("myfile.txt");
        }

        @Test
        @DisplayName("should reject upload when destination filename is hidden")
        void shouldRejectUpload_whenDestinationFilenameIsHidden() throws IOException {
            // Given
            String systemTempDir = System.getProperty("java.io.tmpdir");
            File sourceFile = new File(systemTempDir, "upload_a1b2c3d4_5678_90ab_cdef_123456789abc_00000000.tmp");
            sourceFile.createNewFile();
            sourceFile.deleteOnExit();

            // When/Then
            assertThatThrownBy(() ->
                PathValidationUtils.validateUpload(sourceFile, ".htaccess", tempDir.toFile()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("hidden files not allowed");
        }

        @Test
        @DisplayName("should accept file already in destination directory")
        void shouldAcceptFile_whenAlreadyInDestinationDirectory() throws IOException {
            // Given - file already in the destination directory
            File existingFile = tempDir.resolve("existing_file.txt").toFile();
            existingFile.createNewFile();

            // When/Then - should pass validation as file is in expected base dir
            File result = PathValidationUtils.validateUpload(existingFile, "newname.txt", tempDir.toFile());

            assertThat(result.getName()).isEqualTo("newname.txt");
        }
    }

    // ========================================================================
    // EDGE CASES
    // ========================================================================

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle filenames with special characters")
        void shouldHandleFilenames_whenSpecialCharactersPresent() {
            // Given
            String filename = "file with spaces (1).txt";

            // When
            File result = PathValidationUtils.validatePath(filename, allowedDir);

            // Then
            assertThat(result.getName()).isEqualTo("file with spaces (1).txt");
        }

        @Test
        @DisplayName("should handle very long filenames")
        void shouldHandleVeryLongFilenames() {
            // Given - 200 character filename
            String longName = "a".repeat(195) + ".txt";

            // When
            File result = PathValidationUtils.validatePath(longName, allowedDir);

            // Then
            assertThat(result.getName()).isEqualTo(longName);
        }

        @Test
        @DisplayName("should handle Windows-style path separators")
        void shouldHandleWindowsStylePathSeparators() {
            // Given
            String windowsPath = "dir\\subdir\\file.txt";

            // When
            File result = PathValidationUtils.validatePath(windowsPath, allowedDir);

            // Then - should extract just the filename
            assertThat(result.getName()).isEqualTo("file.txt");
        }

        @Test
        @DisplayName("should handle mixed path separators")
        void shouldHandleMixedPathSeparators() {
            // Given
            String mixedPath = "dir/subdir\\file.txt";

            // When
            File result = PathValidationUtils.validatePath(mixedPath, allowedDir);

            // Then
            assertThat(result.getName()).isEqualTo("file.txt");
        }
    }

    // ========================================================================
    // SYMLINK HANDLING (Platform Dependent)
    // ========================================================================

    @Nested
    @DisplayName("Symlink Handling Tests")
    @Tag("filesystem")
    class SymlinkTests {

        @Test
        @DisplayName("should block symlink escape via upload validation")
        void shouldBlockSymlinkEscape_viaUploadValidation() throws IOException {
            // Given - use a file that exists outside any temp directory
            // /etc/hostname exists on Linux and is definitely not in /tmp or Tomcat work dirs
            Path outsideFile = Path.of("/etc/hostname");

            // Skip test if the outside file doesn't exist (different OS or environment)
            Assumptions.assumeTrue(Files.exists(outsideFile),
                "Test requires /etc/hostname to exist (Linux-specific)");

            // Create symlink inside temp dir pointing to the outside file
            Path symlink = tempDir.resolve("symlink_to_outside.txt");
            try {
                Files.createSymbolicLink(symlink, outsideFile);
                symlink.toFile().deleteOnExit();
            } catch (UnsupportedOperationException | IOException e) {
                // Skip test on systems that don't support symlinks
                Assumptions.assumeTrue(false, "Symlinks not supported on this system");
                return;
            }

            // When/Then - the symlink's canonical path resolves to /etc/hostname
            // which is outside all allowed temp directories
            File symlinkFile = symlink.toFile();

            assertThatThrownBy(() -> PathValidationUtils.validateUpload(symlinkFile))
                .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("should accept symlink when target is within allowed directory")
        void shouldAcceptSymlink_whenTargetIsWithinAllowedDirectory() throws IOException {
            // Given - create a file inside the allowed directory
            Path realFile = tempDir.resolve("real_file.txt");
            Files.createFile(realFile);

            // Create symlink also inside allowed dir pointing to real file
            Path symlink = tempDir.resolve("symlink_to_real.txt");
            try {
                Files.createSymbolicLink(symlink, realFile);
                symlink.toFile().deleteOnExit();
            } catch (UnsupportedOperationException | IOException e) {
                // Skip test on systems that don't support symlinks
                Assumptions.assumeTrue(false, "Symlinks not supported on this system");
                return;
            }

            // When/Then - symlink points to file within same directory, should be accepted
            // when using the 3-arg validateUpload with tempDir as expectedBaseDir
            File symlinkFile = symlink.toFile();
            File result = PathValidationUtils.validateUpload(symlinkFile, "output.txt", tempDir.toFile());

            assertThat(result.getName()).isEqualTo("output.txt");
        }
    }
}