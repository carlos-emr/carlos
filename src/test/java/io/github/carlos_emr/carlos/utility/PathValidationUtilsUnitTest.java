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
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.CarlosProperties;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.zip.ZipEntry;

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
class PathValidationUtilsUnitTest {

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

    @Nested
    @DisplayName("Filename Validation Tests")
    class FilenameValidationTests {

        @Test
        @DisplayName("should normalize filename using legacy rules")
        void shouldNormalizeFilename_usingLegacyRules() {
            String result = PathValidationUtils.validateFileName("my report..<script>-final.pdf");

            assertThat(result).isEqualTo("my_report.scriptfinal.pdf");
        }

        @Test
        @DisplayName("should strip path components before normalizing filename")
        void shouldStripPathComponents_beforeNormalizingFilename() {
            String result = PathValidationUtils.validateFileName("nested/path/my report.pdf");

            assertThat(result).isEqualTo("my_report.pdf");
        }

        @Test
        @DisplayName("should reject null byte filename")
        void shouldRejectFilename_withNullByte() {
            assertThatThrownBy(() -> PathValidationUtils.validateFileName("bad\u0000name.pdf"))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining("Invalid filename");
        }

        @Test
        @DisplayName("should normalize strict filename using legacy rules")
        void shouldNormalizeStrictFilename_usingLegacyRules() {
            String result = PathValidationUtils.validateStrictFileName("my report.pdf");

            assertThat(result).isEqualTo("my_report.pdf");
        }

        @Test
        @DisplayName("should preserve generated prefix while normalizing user fragments")
        void shouldPreserveGeneratedPrefix_whileNormalizingUserFragments() {
            String result = PathValidationUtils.validateGeneratedFileName("export_set/name_20260522120000.zip");

            assertThat(result).isEqualTo("export_setname_20260522120000.zip");
        }

        @ParameterizedTest
        @DisplayName("should reject blocked extension when generated filename ends with blocked extension")
        @CsvSource({
            "export_20260522120000.jsp, jsp",
            "archive/report.WAR, war",
            "bundle/library.Jar, jar",
            "'export_20260522120000.jsp.', jsp",
            "'export_20260522120000.jsp ', jsp"
        })
        void shouldRejectBlockedExtension_whenGeneratedFilenameEndsWithBlockedExtension(
                String filename, String expectedExtension) {
            assertThatThrownBy(() -> PathValidationUtils.validateGeneratedFileName(filename))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining("not allowed")
                .hasMessageContaining("." + expectedExtension);
        }

        @ParameterizedTest
        @DisplayName("should reject strict filename when path components are present")
        @ValueSource(strings = {"nested/path/report.pdf", "..\\report.pdf", "/tmp/report.pdf", "C:\\temp\\report.pdf"})
        void shouldRejectStrictFilename_whenPathComponentsArePresent(String filename) {
            assertThatThrownBy(() -> PathValidationUtils.validateStrictFileName(filename))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining("must not include a path");
        }

        @Test
        @DisplayName("should reject hidden filename")
        void shouldRejectFilename_whenHidden() {
            assertThatThrownBy(() -> PathValidationUtils.validateFileName(".env"))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining("hidden files not allowed");
        }

        @ParameterizedTest
        @DisplayName("should reject blocked extension when filename ends with blocked extension")
        @CsvSource({
            "shell.jsp, jsp",
            "view.JSPX, jspx",
            "app.War, war",
            "payload.CLASS, class",
            "library.Jar, jar",
            "launch.JNLP, jnlp",
            "'shell.jsp.', jsp"
        })
        void shouldRejectBlockedExtension_whenFilenameEndsWithBlockedExtension(
                String filename, String expectedExtension) {
            assertThatThrownBy(() -> PathValidationUtils.validateFileName(filename))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining("not allowed")
                .hasMessageContaining("." + expectedExtension);
        }

        @ParameterizedTest
        @DisplayName("should reject dangerous final extension when filename has safe prefix")
        @CsvSource({
            "document.pdf.jsp, jsp",
            "file.pdf.jspx, jspx",
            "file.txt.war, war",
            "report.txt.class, class",
            "file.pdf.jar, jar",
            "file.txt.jnlp, jnlp"
        })
        void shouldRejectDangerousFinalExtension_whenFilenameHasSafePrefix(
                String filename, String expectedExtension) {
            assertThatThrownBy(() -> PathValidationUtils.validateFileName(filename))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining("not allowed")
                .hasMessageContaining("." + expectedExtension);
        }

        @ParameterizedTest
        @DisplayName("should reject blocked extension when validating path")
        @CsvSource({
            "shell.jsp, jsp",
            "nested/path/view.JSPX, jspx",
            "document.pdf.jar, jar",
            "'shell.jsp.', jsp",
            "'nested/path/view.JSPX ', jspx"
        })
        void shouldRejectBlockedExtension_whenValidatingPath(String filename, String expectedExtension) {
            assertThatThrownBy(() -> PathValidationUtils.validatePath(filename, allowedDir))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining("not allowed")
                .hasMessageContaining("." + expectedExtension);
        }

        @Test
        @DisplayName("should allow safe final extension when validating path with blocked non-final extension")
        void shouldAllowSafeFinalExtension_whenValidatingPathWithBlockedNonFinalExtension() {
            File result = PathValidationUtils.validatePath("report.jsp.txt", allowedDir);

            assertThat(result)
                .hasParent(allowedDir)
                .hasName("report.jsp.txt");
        }

        @ParameterizedTest
        @DisplayName("should allow safe final extension when blocked extension is non-final")
        @CsvSource({
            "document.pdf, document.pdf",
            "scan.TXT, scan.TXT",
            "archive.tar.gz, archive.tar.gz",
            "report.jsp.txt, report.jsp.txt",
            "file.war.pdf, file.war.pdf",
            "data.class.txt, data.class.txt",
            "library.jar.pdf, library.jar.pdf"
        })
        void shouldAllowSafeFinalExtension_whenBlockedExtensionIsNonFinal(String filename, String expected) {
            assertThat(PathValidationUtils.validateFileName(filename)).isEqualTo(expected);
        }

        @ParameterizedTest
        @DisplayName("should reject missing or empty filename")
        @ValueSource(strings = {"", "   ", "---"})
        void shouldRejectFilename_whenMissingOrEmpty(String filename) {
            assertThatThrownBy(() -> PathValidationUtils.validateFileName(filename))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining("Invalid filename");
        }

        @Test
        @DisplayName("should validate normalized user filename within allowed directory")
        void shouldValidateNormalizedUserFilename_withinAllowedDirectory() {
            File result = PathValidationUtils.validateUserFilePath("nested/path/my report.pdf", allowedDir);

            assertThat(result.getParentFile()).isEqualTo(allowedDir);
            assertThat(result.getName()).isEqualTo("my_report.pdf");
        }

        @Test
        @DisplayName("should reject generated filename when null byte is present")
        void shouldRejectGeneratedFilename_whenNullBytePresent() {
            assertThatThrownBy(() -> PathValidationUtils.validateGeneratedFileName("report\u0000.pdf"))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining("Invalid filename");
        }
    }

    @Nested
    @DisplayName("Path Component Validation Tests")
    class PathComponentValidationTests {

        @ParameterizedTest
        @DisplayName("should preserve valid single path component")
        @ValueSource(strings = {"1", "Fax", "report.pdf", "abc_123-file.pdf", "report.jsp.txt"})
        void shouldPreserveSinglePathComponent_whenValid(String component) {
            assertThat(PathValidationUtils.validatePathComponent(component, "component"))
                .isEqualTo(component);
        }

        @ParameterizedTest
        @DisplayName("should reject unsafe path component")
        @ValueSource(strings = {
            "../etc/passwd",
            "x/../y",
            "/tmp/report.pdf",
            "C:\\temp\\report.pdf",
            "foo/bar",
            "foo\\bar",
            ".hidden",
            ".",
            "..",
            "",
            "   ",
            "bad\u0000name.pdf"
        })
        void shouldRejectPathComponent_whenUnsafe(String component) {
            assertThatThrownBy(() -> PathValidationUtils.validatePathComponent(component, "component"))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining("Invalid filename");
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
        void shouldNeutralizePathTraversal_byStrippingToBasename(String maliciousPath) {
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

        @Test
        @DisplayName("should reject file in a sibling directory sharing a name prefix with the allowed directory")
        void shouldRejectFile_whenSiblingDirectorySharesNamePrefixWithAllowedDir() throws IOException {
            // Allowed dir "app" must NOT be treated as containing sibling "app-evil". This guards
            // against a naive prefix check (e.g. "/x/app-evil".startsWith("/x/app")); the real check
            // is separator-aware via startsWith(base + File.separator).
            File appDir = Files.createDirectory(tempDir.resolve("app")).toFile();
            Path siblingDir = Files.createDirectory(tempDir.resolve("app-evil"));
            File siblingFile = Files.createFile(siblingDir.resolve("secret.txt")).toFile();

            assertThatThrownBy(() -> PathValidationUtils.validateExistingPath(siblingFile, appDir))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Invalid file path");
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
        void shouldRejectFiles_whenStartingWithDot(String hiddenFile) {
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
                .hasMessageContaining("Invalid filename");
        }

        @Test
        @DisplayName("should throw SecurityException when filename is empty")
        void shouldThrowSecurityException_whenFilenameIsEmpty() {
            // When/Then
            assertThatThrownBy(() -> PathValidationUtils.validatePath("", allowedDir))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid filename");
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

        @Test
        @DisplayName("should reject upload content that is not a file")
        void shouldRejectUploadContent_whenNotAFile() {
            // The Struts interceptor entry point must reject non-File content (e.g. String/byte[]).
            assertThatThrownBy(() -> PathValidationUtils.validateUploadContent("not-a-file"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not a file");
        }

        @Test
        @DisplayName("should validate upload content when it is a file in an allowed temp directory")
        void shouldValidateUploadContent_whenFileInAllowedTempDirectory() throws IOException {
            File tempFile = Files.createTempFile("upload_content_", ".tmp").toFile();
            tempFile.deleteOnExit();

            File result = PathValidationUtils.validateUploadContent(tempFile);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(tempFile.getName());
        }
    }

    // ========================================================================
    // EXISTING DOCUMENT PATH VALIDATION
    // ========================================================================

    @Nested
    @DisplayName("Existing Document Path Validation Tests")
    class ExistingDocumentPathValidationTests {

        private String previousDocumentDir;

        @BeforeEach
        void stashDocumentDir() {
            previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        }

        @AfterEach
        void restoreDocumentDir() {
            if (previousDocumentDir == null) {
                CarlosProperties.getInstance().remove("DOCUMENT_DIR");
            } else {
                CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", previousDocumentDir);
            }
        }

        @Test
        @DisplayName("should return canonical DOCUMENT_DIR when configured")
        void shouldReturnCanonicalDocumentDirectoryWhenConfigured() throws IOException {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", tempDir.toString());

            assertThat(PathValidationUtils.getRequiredDocumentDirectory())
                    .isEqualTo(tempDir.toFile().getCanonicalFile());
        }

        @ParameterizedTest
        @DisplayName("should reject blank DOCUMENT_DIR values")
        @ValueSource(strings = {"", "   "})
        void shouldRejectBlankDocumentDirectory(String documentDir) {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir);

            assertThatThrownBy(PathValidationUtils::getRequiredDocumentDirectory)
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("DOCUMENT_DIR not configured");
        }

        @Test
        @DisplayName("should reject missing DOCUMENT_DIR")
        void shouldRejectMissingDocumentDirectory() {
            CarlosProperties.getInstance().remove("DOCUMENT_DIR");

            assertThatThrownBy(PathValidationUtils::getRequiredDocumentDirectory)
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("DOCUMENT_DIR not configured");
        }

        @Test
        @DisplayName("should reject DOCUMENT_DIR that is not a directory")
        void shouldRejectDocumentDirectoryThatIsNotDirectory() throws IOException {
            Path regularFile = Files.createTempFile(tempDir, "document-dir", ".txt");
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", regularFile.toString());

            assertThatThrownBy(PathValidationUtils::getRequiredDocumentDirectory)
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("DOCUMENT_DIR is not an existing directory");
        }

        @Test
        @DisplayName("should accept existing document path inside DOCUMENT_DIR")
        void shouldAcceptExistingDocumentPathInsideDocumentDirectory() throws IOException {
            Path document = Files.writeString(tempDir.resolve("lab.hl7"), "MSH");
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", tempDir.toString());

            assertThat(PathValidationUtils.validateExistingDocumentPath(document.toString()).getCanonicalFile())
                    .isEqualTo(document.toFile().getCanonicalFile());
        }

        @Test
        @DisplayName("should reject existing document path outside DOCUMENT_DIR")
        void shouldRejectExistingDocumentPathOutsideDocumentDirectory() throws IOException {
            Path outside = Files.createTempFile("outside-document-dir", ".hl7");
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", tempDir.toString());

            String outsidePath = outside.toString();

            try {
                assertThatThrownBy(() -> PathValidationUtils.validateExistingDocumentPath(outsidePath))
                        .isInstanceOf(SecurityException.class)
                        .hasMessageContaining("Invalid file path");
            } finally {
                Files.deleteIfExists(outside);
            }
        }

        @ParameterizedTest
        @DisplayName("should reject blank existing path values")
        @ValueSource(strings = {"", "   "})
        void shouldRejectBlankExistingPathValues(String filePath) {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", tempDir.toString());

            assertThatThrownBy(() -> PathValidationUtils.validateExistingDocumentPath(filePath))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("File path is null or empty");
        }
    }

    // ========================================================================
    // CONFIGURED AND GENERATED PATH VALIDATION
    // ========================================================================

    @Nested
    @DisplayName("Configured and Generated Path Validation Tests")
    class ConfiguredAndGeneratedPathValidationTests {

        @Test
        @DisplayName("should resolve configured directory to canonical path")
        void shouldResolveConfiguredDirectory_toCanonicalPath() throws IOException {
            File resolved = PathValidationUtils.resolveConfiguredDirectory(tempDir.resolve(".").toString(), "test dir");

            assertThat(resolved).isEqualTo(tempDir.toFile().getCanonicalFile());
        }

        @Test
        @DisplayName("should reject configured directory that is a file")
        void shouldRejectConfiguredDirectory_thatIsFile() throws IOException {
            Path file = Files.writeString(tempDir.resolve("not-a-directory.txt"), "content");
            String filePath = file.toString();

            assertThatThrownBy(() -> PathValidationUtils.resolveConfiguredDirectory(filePath, "test dir"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Configured path is not a directory");
        }

        @Test
        @DisplayName("should validate generated child path inside allowed directory")
        void shouldValidateGeneratedChildPath_insideAllowedDirectory() throws IOException {
            File child = PathValidationUtils.validateGeneratedChildPath("LabUpload.result.123", allowedDir);

            assertThat(child.getCanonicalFile())
                    .hasParent(allowedDir.getCanonicalFile())
                    .hasName("LabUpload.result.123");
        }

        @ParameterizedTest
        @DisplayName("should reject generated child path components with traversal syntax")
        @ValueSource(strings = {"../evil.txt", "nested/evil.txt", "nested\\evil.txt", ".", ".."})
        void shouldRejectGeneratedChildPathComponentsWithTraversalSyntax(String generatedName) {
            assertThatThrownBy(() -> PathValidationUtils.validateGeneratedChildPath(generatedName, allowedDir))
                    .isInstanceOf(FileValidationException.class)
                    .hasMessageContaining(PathValidationUtils.PATH_COMPONENT_FILENAME_MESSAGE);
        }

        @Test
        @DisplayName("should validate configured file")
        void shouldValidateConfigured_file() throws IOException {
            Path file = Files.writeString(tempDir.resolve("message_config.xml"), "<root/>");

            assertThat(PathValidationUtils.validateConfiguredFile(file.toString(), "config").getCanonicalFile())
                    .isEqualTo(file.toFile().getCanonicalFile());
        }

        @Test
        @DisplayName("should reject configured file that is a directory")
        void shouldRejectConfiguredFile_thatIsDirectory() {
            String directoryPath = tempDir.toString();

            assertThatThrownBy(() -> PathValidationUtils.validateConfiguredFile(directoryPath, "config"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Configured path is not a file");
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
        void shouldAcceptFiles_inTempDirectory(String tempFileName) throws IOException {
            // Given - create a file in system temp dir
            String systemTempDir = System.getProperty("java.io.tmpdir");
            File tempFile = new File(systemTempDir, tempFileName);
            tempFile.createNewFile();
            tempFile.deleteOnExit();

            // When/Then - should not throw (file is in allowed temp directory)
            assertThatCode(() -> PathValidationUtils.validateUpload(tempFile))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should open stream for upload in allowed temp directory")
        void shouldOpenValidatedUploadInputStream_whenUploadIsInAllowedTempDirectory() throws IOException {
            Path upload = Files.createTempFile("path-validation-upload-", ".txt");
            Files.writeString(upload, "safe upload", StandardCharsets.UTF_8);

            try (InputStream inputStream = PathValidationUtils.openValidatedUploadInputStream(upload.toFile())) {
                assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8))
                        .isEqualTo("safe upload");
            } finally {
                Files.deleteIfExists(upload);
            }
        }

        @Test
        @DisplayName("should reject opening upload stream outside allowed temp directories")
        void shouldRejectOpeningValidatedUploadInputStream_whenUploadIsOutsideAllowedTempDirectories() {
            File outsideFile = new File("/etc/hostname");
            Assumptions.assumeTrue(outsideFile.exists() && outsideFile.isFile(),
                    "Test requires /etc/hostname to exist (Linux-specific)");

            assertThatThrownBy(() -> PathValidationUtils.openValidatedUploadInputStream(outsideFile))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Invalid upload file")
                    .hasCauseInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("should report null file as not in an allowed temp directory")
        void shouldReturnFalse_whenFileIsNull() {
            assertThat(PathValidationUtils.isInAllowedTempDirectory(null)).isFalse();
        }

        @Test
        @DisplayName("should report a system temp file as in an allowed temp directory")
        void shouldReturnTrue_whenFileIsInSystemTempDirectory() throws IOException {
            File tempFile = Files.createTempFile("temp_dir_", ".tmp").toFile();
            tempFile.deleteOnExit();

            assertThat(PathValidationUtils.isInAllowedTempDirectory(tempFile)).isTrue();
        }

        @Test
        @DisplayName("should report a file outside temp directories as not allowed")
        void shouldReturnFalse_whenFileIsOutsideTempDirectories() {
            File outsideFile = new File("/etc/hostname");
            Assumptions.assumeTrue(outsideFile.exists() && outsideFile.isFile(),
                    "Test requires /etc/hostname to exist (Linux-specific)");

            assertThat(PathValidationUtils.isInAllowedTempDirectory(outsideFile)).isFalse();
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
        @DisplayName("should reject blocked extension when upload destination ends with blocked extension")
        void shouldRejectBlockedExtension_whenUploadDestinationEndsWithBlockedExtension() throws IOException {
            // Given
            File sourceFile = Files.createTempFile("upload_blocked_extension_", ".tmp").toFile();
            sourceFile.deleteOnExit();
            File destinationDir = tempDir.toFile();

            // When/Then
            assertThatThrownBy(() ->
                PathValidationUtils.validateUpload(sourceFile, "document.pdf.jsp", destinationDir))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining("not allowed")
                .hasMessageContaining(".jsp");
        }

        @Test
        @DisplayName("should allow safe final extension when upload destination has blocked non-final extension")
        void shouldAllowSafeFinalExtension_whenUploadDestinationHasBlockedNonFinalExtension() throws IOException {
            // Given
            File sourceFile = Files.createTempFile("upload_safe_extension_", ".tmp").toFile();
            sourceFile.deleteOnExit();
            File destinationDir = tempDir.toFile();

            // When
            File result = PathValidationUtils.validateUpload(sourceFile, "report.jsp.txt", destinationDir);

            // Then
            assertThat(result)
                .hasParent(destinationDir)
                .hasName("report.jsp.txt");
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
        void shouldHandleFilenames_whenVeryLong() {
            // Given - 200 character filename
            String longName = "a".repeat(195) + ".txt";

            // When
            File result = PathValidationUtils.validatePath(longName, allowedDir);

            // Then
            assertThat(result.getName()).isEqualTo(longName);
        }

        @Test
        @DisplayName("should handle Windows-style path separators")
        void shouldHandlePathSeparators_whenWindowsStyle() {
            // Given
            String windowsPath = "dir\\subdir\\file.txt";

            // When
            File result = PathValidationUtils.validatePath(windowsPath, allowedDir);

            // Then - should extract just the filename
            assertThat(result.getName()).isEqualTo("file.txt");
        }

        @Test
        @DisplayName("should handle mixed path separators")
        void shouldHandlePathSeparators_whenMixed() {
            // Given
            String mixedPath = "dir/subdir\\file.txt";

            // When
            File result = PathValidationUtils.validatePath(mixedPath, allowedDir);

            // Then
            assertThat(result.getName()).isEqualTo("file.txt");
        }
    }

    // ========================================================================
    // REGRESSION CANARY TESTS
    // These tests are tripwires: if PathValidationUtils is weakened, these must
    // fail so that downstream CodeQL path-injection dismissals remain defensible.
    // DO NOT remove or weaken without a formal security review.
    // ========================================================================

    /**
     * Regression canary tests for path traversal prevention.
     *
     * <p>Seven named tests that explicitly cover the attack vectors cited when
     * dismissing CodeQL path-injection alerts. If any of these tests start
     * failing, the corresponding alert dismissals must be revisited.</p>
     *
     * @since 2026-04-13
     */
    @Nested
    @DisplayName("Regression Canary Tests")
    @Tag("security")
    @Tag("canary")
    class RegressionCanaryTests {

        @TempDir
        Path secondTempDir;

        @Test
        @DisplayName("should throw SecurityException when filename is dot-dot")
        void shouldThrowSecurityException_whenFilenameContainsDotDot() {
            // ".." starts with '.' so sanitizeFileName rejects it as a hidden file,
            // preventing dot-dot directory traversal at the sanitization layer.
            assertThatThrownBy(() -> PathValidationUtils.validatePath("..", allowedDir))
                .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("should throw SecurityException when filename starts with dot")
        void shouldThrowSecurityException_whenFilenameStartsWithDot() {
            assertThatThrownBy(() -> PathValidationUtils.validatePath(".hidden", allowedDir))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("hidden files not allowed");
        }

        @Test
        @DisplayName("should throw SecurityException when absolute path escapes base directory")
        void shouldThrowSecurityException_whenAbsolutePathEscapesBaseDir() throws IOException {
            // File resides in a separate temp directory that is not allowedDir
            File outsideFile = Files.createTempFile(secondTempDir, "outside", ".txt").toFile();

            assertThatThrownBy(() -> PathValidationUtils.validateExistingPath(outsideFile, allowedDir))
                .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("should throw SecurityException when uploaded file is outside allowed temp directories")
        void shouldThrowSecurityException_whenUploadedFileOutsideTempDirs() {
            // /etc/hostname is a standard Linux file that exists outside java.io.tmpdir
            // and any Tomcat work directory, so validateUpload must reject it.
            File outsideFile = new File("/etc/hostname");
            Assumptions.assumeTrue(outsideFile.exists() && outsideFile.isFile(),
                "Test requires /etc/hostname to exist (Linux-specific)");

            assertThatThrownBy(() -> PathValidationUtils.validateUpload(outsideFile))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid upload source");
        }

        @Test
        @DisplayName("should return safe file when filename is clean")
        void shouldReturnSafeFile_whenFilenameIsClean() {
            File result = PathValidationUtils.validatePath("document.pdf", allowedDir);

            assertThat(result).isNotNull();
            assertThat(result.getParentFile()).isEqualTo(allowedDir);
            assertThat(result.getName()).isEqualTo("document.pdf");
        }

        @Test
        @DisplayName("should strip path components when filename contains forward slash")
        void shouldStripPathComponents_whenFilenameContainsForwardSlash() {
            // "sub/../../etc/passwd" is sanitized to just "passwd" and placed inside allowedDir.
            // The traversal attempt must never produce a path outside allowedDir.
            File result = PathValidationUtils.validatePath("sub/../../etc/passwd", allowedDir);

            assertThat(result.getName()).isEqualTo("passwd");
            assertThat(result.getParentFile()).isEqualTo(allowedDir);
        }

        @Test
        @DisplayName("should strip path components when filename contains backslash")
        void shouldStripPathComponents_whenFilenameContainsBackslash() {
            // Windows-style traversal "dir\..\etc\passwd" is sanitized to just "passwd".
            // FilenameUtils treats '\' as a path separator on all platforms.
            File result = PathValidationUtils.validatePath("dir\\..\\etc\\passwd", allowedDir);

            assertThat(result.getName()).isEqualTo("passwd");
            assertThat(result.getParentFile()).isEqualTo(allowedDir);
        }
    }

    @Nested
    @DisplayName("Parent Directory Validation Tests")
    class ParentDirectoryValidationTests {

        @Test
        @DisplayName("should validate complete file against canonical parent")
        void shouldValidateCompleteFile_againstCanonicalParent() {
            File file = tempDir.resolve("report.txt").toFile();

            File result = PathValidationUtils.validateAgainstParentDirectory(file);

            assertThat(result.getName()).isEqualTo("report.txt");
            assertThat(result.getParentFile()).isEqualTo(tempDir.toFile());
        }
    }

    @Nested
    @DisplayName("Configured Directory Tests")
    class ConfiguredDirectoryTests {

        @Test
        @DisplayName("should accept absolute configured directory")
        void shouldAcceptConfiguredDirectory_whenAbsolute() throws IOException {
            File result = PathValidationUtils.validateConfiguredDirectory(allowedDir.getAbsolutePath(), "test dir");

            assertThat(result).isDirectory();
            assertThat(result).isEqualTo(allowedDir.getCanonicalFile());
        }

        @Test
        @DisplayName("should reject configured path when it is not a directory")
        void shouldRejectConfiguredPath_whenNotDirectory() throws IOException {
            File file = tempDir.resolve("not-dir.txt").toFile();
            assertThat(file.createNewFile()).isTrue();

            assertThatThrownBy(() -> PathValidationUtils.validateConfiguredDirectory(file.getAbsolutePath(), "test dir"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not a directory");
        }

        @Test
        @DisplayName("should resolve missing configured directory for lazy creation")
        void shouldResolveMissingConfiguredDirectory_forLazyCreation() throws IOException {
            File missingDir = tempDir.resolve("missing-dir").toFile();

            File result = PathValidationUtils.resolveConfiguredDirectory(missingDir.getAbsolutePath(), "test dir");

            assertThat(result).isEqualTo(missingDir.getCanonicalFile());
            assertThat(result).doesNotExist();
        }

        @Test
        @DisplayName("should reject existing file when resolving configured directory")
        void shouldRejectExistingFile_whenResolvingConfiguredDirectory() throws IOException {
            File file = tempDir.resolve("not-dir.txt").toFile();
            assertThat(file.createNewFile()).isTrue();

            assertThatThrownBy(() -> PathValidationUtils.resolveConfiguredDirectory(file.getAbsolutePath(), "test dir"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not a directory");
        }

        @Test
        @DisplayName("should resolve trusted generated sibling path")
        void shouldResolveGeneratedSiblingPath_whenSuffixIsTrusted() {
            File configured = tempDir.resolve("outbox").toFile();

            File result = PathValidationUtils.validateGeneratedSiblingPath(configured.getAbsolutePath(), ".timestamp", "outbox timestamp");

            assertThat(result.getName()).isEqualTo("outbox.timestamp");
            assertThat(result.getParentFile()).isEqualTo(tempDir.toFile());
        }

        @Test
        @DisplayName("should reject generated sibling suffix with path separator")
        void shouldRejectGeneratedSiblingSuffix_withPathSeparator() {
            File configured = tempDir.resolve("outbox").toFile();

            assertThatThrownBy(() -> PathValidationUtils.validateGeneratedSiblingPath(configured.getAbsolutePath(), "/bad", "outbox timestamp"))
                .isInstanceOf(SecurityException.class);
        }
    }

    @Nested
    @DisplayName("Generated Child Path Tests")
    class GeneratedChildPathTests {

        @Test
        @DisplayName("should allow hidden application generated child name")
        void shouldAllowApplicationGeneratedChildName_whenHidden() {
            File result = PathValidationUtils.validateGeneratedChildPath(".timestamp", allowedDir);

            assertThat(result.getName()).isEqualTo(".timestamp");
            assertThat(result.getParentFile()).isEqualTo(allowedDir);
        }

        @ParameterizedTest
        @DisplayName("should reject generated child path components")
        @ValueSource(strings = {"../secret.txt", "nested/file.txt", "nested\\file.txt", ".", ".."})
        void shouldRejectGeneratedChildPath_whenContainsComponents(String childName) {
            assertThatThrownBy(() -> PathValidationUtils.validateGeneratedChildPath(childName, allowedDir))
                .isInstanceOf(FileValidationException.class);
        }
    }


    @Nested
    @DisplayName("Configured File Tests")
    class ConfiguredFileTests {

        @Test
        @DisplayName("should accept existing configured file")
        void shouldAcceptConfiguredFile_whenExisting() throws IOException {
            File file = tempDir.resolve("configured.properties").toFile();
            assertThat(file.createNewFile()).isTrue();

            File result = PathValidationUtils.validateConfiguredFile(file.getAbsolutePath(), "configured file");

            assertThat(result).isFile();
            assertThat(result).isEqualTo(file.getCanonicalFile());
        }

        @Test
        @DisplayName("should reject configured file when it is a directory")
        void shouldRejectConfiguredFile_whenItIsDirectory() {
            assertThatThrownBy(() -> PathValidationUtils.validateConfiguredFile(allowedDir.getAbsolutePath(), "configured file"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not a file");
        }

        @Test
        @DisplayName("should resolve missing configured file for lazy creation")
        void shouldResolveMissingConfiguredFile_forLazyCreation() throws IOException {
            File missingFile = tempDir.resolve("missing.properties").toFile();

            File result = PathValidationUtils.resolveConfiguredFile(missingFile.getAbsolutePath(), "configured file");

            assertThat(result).isEqualTo(missingFile.getCanonicalFile());
            assertThat(result).doesNotExist();
        }
    }

    @Nested
    @DisplayName("ZIP Entry Validation Tests")
    class ZipEntryValidationTests {

        @Test
        @DisplayName("should resolve safe ZIP entry path inside destination")
        void shouldResolveSafeZipEntryPath_insideDestination() {
            ZipEntry entry = new ZipEntry("nested/report.xml");

            File result = PathValidationUtils.validateZipEntryPath(entry, allowedDir);

            assertThat(result.getPath()).endsWith("nested" + File.separator + "report.xml");
            assertThat(result.getAbsolutePath()).startsWith(allowedDir.getAbsolutePath());
        }

        @ParameterizedTest
        @DisplayName("should reject unsafe ZIP entry paths")
        @ValueSource(strings = {"../escape.txt", "nested/../../escape.txt", "/absolute.txt", "C:/absolute.txt", "nested/./file.txt"})
        void shouldRejectZipEntryPaths_whenUnsafe(String entryName) {
            assertThatThrownBy(() -> PathValidationUtils.validateZipEntryPath(new ZipEntry(entryName), allowedDir))
                .isInstanceOf(FileValidationException.class);
        }

        @Test
        @DisplayName("should build safe ZIP entry name from source root")
        void shouldBuildSafeZipEntryName_fromSourceRoot() throws IOException {
            Path nestedDir = tempDir.resolve("nested");
            Files.createDirectories(nestedDir);
            File file = nestedDir.resolve("report.xml").toFile();
            assertThat(file.createNewFile()).isTrue();

            String result = PathValidationUtils.validateZipEntryName(file, allowedDir);

            assertThat(result).isEqualTo("nested/report.xml");
        }

        @Test
        @DisplayName("should reject ZIP entry name outside source root")
        void shouldRejectZipEntryName_whenOutsideSourceRoot() throws IOException {
            File outside = Files.createTempFile("outside", ".txt").toFile();

            assertThatThrownBy(() -> PathValidationUtils.validateZipEntryName(outside, allowedDir))
                .isInstanceOf(SecurityException.class);
        }
    }


    @Nested
    @DisplayName("Child Path Validation Tests")
    class ChildPathValidationTests {

        @Test
        @DisplayName("should allow non-existing child path inside allowed directory")
        void shouldAllowNonExistingChildPath_insideAllowedDirectory() {
            File child = tempDir.resolve("new-document.pdf").toFile();

            File result = PathValidationUtils.validateChildPath(child, allowedDir);

            assertThat(result).isEqualTo(child);
        }

        @Test
        @DisplayName("should reject child path outside allowed directory")
        void shouldRejectChildPath_whenOutsideAllowedDirectory() {
            File outside = tempDir.getParent().resolve("outside-document.pdf").toFile();

            assertThatThrownBy(() -> PathValidationUtils.validateChildPath(outside, allowedDir))
                .isInstanceOf(SecurityException.class);
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

    @Nested
    @DisplayName("Secure Temp File Tests")
    class SecureTempFileTests {

        @Test
        @DisplayName("should create a usable temp file when prefix and suffix are valid")
        void shouldCreateUsableTempFile_whenPrefixAndSuffixValid() throws IOException {
            File tempFile = PathValidationUtils.createSecureTempFile("carlostmp", ".pdf");
            try {
                assertThat(tempFile).exists();
                assertThat(tempFile.canRead()).isTrue();
                assertThat(tempFile.canWrite()).isTrue();
                assertThat(tempFile.getName()).endsWith(".pdf");
            } finally {
                tempFile.delete();
            }
        }

        @Test
        @DisplayName("should restrict permissions to owner-only on a POSIX filesystem")
        void shouldRestrictPermissions_toOwnerOnlyOnPosixFilesystem() throws IOException {
            Assumptions.assumeTrue(
                    FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                    "Test requires a POSIX filesystem");

            File tempFile = PathValidationUtils.createSecureTempFile("carlostmp", ".pdf");
            try {
                Set<PosixFilePermission> perms = Files.getPosixFilePermissions(tempFile.toPath());
                assertThat(perms).containsExactlyInAnyOrder(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            } finally {
                tempFile.delete();
            }
        }
    }
}
