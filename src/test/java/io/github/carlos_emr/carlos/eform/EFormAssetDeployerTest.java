/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.eform;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import jakarta.servlet.ServletContext;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EFormAssetDeployer}.
 *
 * <p>Tests the Spring startup component that deploys bundled eForm assets
 * (editControl2.js, blank.rtl, editor_help.html) to the eForm images directory.
 * Validates skip-if-exists logic, error handling, and configuration edge cases.</p>
 *
 * @since 2026-03-23
 */
@DisplayName("EFormAssetDeployer Unit Tests")
@Tag("unit")
@Tag("eform")
class EFormAssetDeployerTest extends CarlosUnitTestBase {

    private static final String RESOURCE_EDITCONTROL = "/WEB-INF/eform-assets/editControl2.js";
    private static final String RESOURCE_BLANK = "/WEB-INF/eform-assets/blank.rtl";
    private static final String RESOURCE_HELP = "/WEB-INF/eform-assets/editor_help.html";

    private MockedStatic<CarlosProperties> carlosPropertiesMock;

    @Mock
    private CarlosProperties mockProperties;

    @Mock
    private ServletContext mockServletContext;

    private EFormAssetDeployer deployer;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Mock CarlosProperties singleton
        carlosPropertiesMock = mockStatic(CarlosProperties.class);
        carlosPropertiesMock.when(CarlosProperties::getInstance).thenReturn(mockProperties);

        // Create a real temp directory to test file operations
        tempDir = Files.createTempDirectory("eform-test-");

        deployer = new EFormAssetDeployer();
        deployer.setServletContext(mockServletContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (carlosPropertiesMock != null) {
            carlosPropertiesMock.close();
        }
        FileUtils.deleteQuietly(tempDir != null ? tempDir.toFile() : null);
    }

    private InputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private void stubAllAssets() {
        when(mockServletContext.getResourceAsStream(RESOURCE_EDITCONTROL)).thenReturn(toStream("js content"));
        when(mockServletContext.getResourceAsStream(RESOURCE_BLANK)).thenReturn(toStream("blank content"));
        when(mockServletContext.getResourceAsStream(RESOURCE_HELP)).thenReturn(toStream("help content"));
    }

    @Nested
    @Tag("unit")
    @Tag("eform")
    @DisplayName("Asset Deployment")
    class AssetDeployment {

        @Test
        @DisplayName("Should deploy all three assets when target files do not exist")
        void shouldDeployAllAssets_whenTargetFilesDoNotExist() throws Exception {
            when(mockProperties.getEformImageDirectory()).thenReturn(tempDir.toString());
            stubAllAssets();

            deployer.afterPropertiesSet();

            assertThat(new File(tempDir.toFile(), "editControl2.js")).exists();
            assertThat(new File(tempDir.toFile(), "blank.rtl")).exists();
            assertThat(new File(tempDir.toFile(), "editor_help.html")).exists();
        }

        @Test
        @DisplayName("Should write correct content to deployed files")
        void shouldWriteCorrectContent_toDeployedFiles() throws Exception {
            when(mockProperties.getEformImageDirectory()).thenReturn(tempDir.toString());

            String expectedContent = "// editControl2.js test content";
            when(mockServletContext.getResourceAsStream(RESOURCE_EDITCONTROL)).thenReturn(toStream(expectedContent));
            when(mockServletContext.getResourceAsStream(RESOURCE_BLANK)).thenReturn(null);
            when(mockServletContext.getResourceAsStream(RESOURCE_HELP)).thenReturn(null);

            deployer.afterPropertiesSet();

            File deployed = new File(tempDir.toFile(), "editControl2.js");
            assertThat(deployed).exists();
            assertThat(Files.readString(deployed.toPath())).isEqualTo(expectedContent);
        }

        @Test
        @DisplayName("Should fall back to regular move when atomic move is unsupported")
        void shouldFallbackToRegularMove_whenAtomicMoveUnsupported() throws Exception {
            when(mockProperties.getEformImageDirectory()).thenReturn(tempDir.toString());
            when(mockServletContext.getResourceAsStream(RESOURCE_EDITCONTROL)).thenReturn(toStream("js content"));
            when(mockServletContext.getResourceAsStream(RESOURCE_BLANK)).thenReturn(null);
            when(mockServletContext.getResourceAsStream(RESOURCE_HELP)).thenReturn(null);

            boolean[] fallbackUsed = {false};
            EFormAssetDeployer fallbackDeployer = new EFormAssetDeployer() {
                @Override
                void moveTempFileAtomically(Path tempFile, Path targetPath) throws IOException {
                    throw new AtomicMoveNotSupportedException(tempFile.toString(), targetPath.toString(), "test filesystem");
                }

                @Override
                void moveTempFileWithoutAtomicOption(Path tempFile, Path targetPath) throws IOException {
                    fallbackUsed[0] = true;
                    super.moveTempFileWithoutAtomicOption(tempFile, targetPath);
                }
            };
            fallbackDeployer.setServletContext(mockServletContext);

            fallbackDeployer.afterPropertiesSet();

            File deployed = new File(tempDir.toFile(), "editControl2.js");
            assertThat(fallbackUsed[0]).isTrue();
            assertThat(deployed).exists();
            assertThat(Files.readString(deployed.toPath())).isEqualTo("js content");
            assertThat(tempDir.toFile().listFiles((dir, name) -> name.startsWith("editControl2.js.") && name.endsWith(".tmp")))
                .isEmpty();
        }

        @Test
        @DisplayName("Should skip deployment when target file already exists")
        void shouldSkipDeployment_whenTargetFileAlreadyExists() throws Exception {
            when(mockProperties.getEformImageDirectory()).thenReturn(tempDir.toString());

            // Pre-create the file with custom content (simulating clinic customization)
            File existingFile = new File(tempDir.toFile(), "editControl2.js");
            Files.writeString(existingFile.toPath(), "clinic customized content");

            when(mockServletContext.getResourceAsStream(RESOURCE_BLANK)).thenReturn(null);
            when(mockServletContext.getResourceAsStream(RESOURCE_HELP)).thenReturn(null);

            deployer.afterPropertiesSet();

            // Verify the existing file was NOT overwritten
            assertThat(Files.readString(existingFile.toPath())).isEqualTo("clinic customized content");

            // Verify getResourceAsStream was NOT called for the existing file
            verify(mockServletContext, never()).getResourceAsStream(RESOURCE_EDITCONTROL);
        }
    }

    @Nested
    @Tag("unit")
    @Tag("eform")
    @DisplayName("Configuration Edge Cases")
    class ConfigurationEdgeCases {

        @Test
        @DisplayName("Should skip deployment when image directory is null")
        void shouldSkipDeployment_whenImageDirectoryNull() {
            when(mockProperties.getEformImageDirectory()).thenReturn(null);

            deployer.afterPropertiesSet();

            verifyNoInteractions(mockServletContext);
        }

        @Test
        @DisplayName("Should skip deployment when image directory is blank")
        void shouldSkipDeployment_whenImageDirectoryBlank() {
            when(mockProperties.getEformImageDirectory()).thenReturn("   ");

            deployer.afterPropertiesSet();

            verifyNoInteractions(mockServletContext);
        }

        @Test
        @DisplayName("Should create directory and deploy assets when image directory does not exist")
        void shouldCreateDirectoryAndDeployAssets_whenImageDirectoryDoesNotExist() {
            Path missingDir = tempDir.resolve("missing-eform-images");

            when(mockProperties.getEformImageDirectory()).thenReturn(missingDir.toString());
            stubAllAssets();

            deployer.afterPropertiesSet();

            assertThat(missingDir).isDirectory();
            assertThat(missingDir.resolve("editControl2.js")).isRegularFile();
            assertThat(missingDir.resolve("blank.rtl")).isRegularFile();
            assertThat(missingDir.resolve("editor_help.html")).isRegularFile();
        }


        @Test
        @DisplayName("Should apply owner-only permissions when POSIX permissions are supported")
        void shouldApplyOwnerOnlyPermissions_whenPosixPermissionsSupported() throws Exception {
            Assumptions.assumeTrue(Files.getFileStore(tempDir).supportsFileAttributeView("posix"));
            Path missingDir = tempDir.resolve("posix-eform-images");

            when(mockProperties.getEformImageDirectory()).thenReturn(missingDir.toString());
            stubAllAssets();

            deployer.afterPropertiesSet();

            Set<PosixFilePermission> actualPermissions = Files.getPosixFilePermissions(missingDir);
            assertThat(actualPermissions).containsExactlyInAnyOrder(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE);
        }

        @Test
        @DisplayName("Should skip deployment when image directory cannot be created")
        void shouldSkipDeployment_whenImageDirectoryCannotBeCreated() throws Exception {
            // Block mkdirs by placing a regular file where a directory component must be
            Path blocker = tempDir.resolve("blocked");
            Files.writeString(blocker, "file");
            Path blockedDir = blocker.resolve("eform-images");

            when(mockProperties.getEformImageDirectory()).thenReturn(blockedDir.toString());

            deployer.afterPropertiesSet();

            assertThat(blocker).isRegularFile();
            assertThat(Files.isDirectory(blockedDir)).isFalse();
            assertThat(tempDir.resolve("editControl2.js")).doesNotExist();
            assertThat(tempDir.resolve("blank.rtl")).doesNotExist();
            assertThat(tempDir.resolve("editor_help.html")).doesNotExist();
            verifyNoInteractions(mockServletContext);
        }

        @Test
        @DisplayName("Should skip deployment when image directory path points to a file")
        void shouldSkipDeployment_whenImageDirectoryPathPointsToFile() throws Exception {
            Path imagePath = tempDir.resolve("images-as-file");
            Files.writeString(imagePath, "not a directory");

            when(mockProperties.getEformImageDirectory()).thenReturn(imagePath.toString());

            deployer.afterPropertiesSet();

            assertThat(imagePath).isRegularFile();
            assertThat(Files.readString(imagePath)).isEqualTo("not a directory");
            verifyNoInteractions(mockServletContext);
        }
    }

    @Nested
    @Tag("unit")
    @Tag("eform")
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("Should not throw when bundled asset not found in WAR")
        void shouldNotThrow_whenBundledAssetNotFoundInWar() throws Exception {
            when(mockProperties.getEformImageDirectory()).thenReturn(tempDir.toString());
            when(mockServletContext.getResourceAsStream(anyString())).thenReturn(null);

            assertThatCode(() -> deployer.afterPropertiesSet()).doesNotThrowAnyException();

            // No files should have been created
            assertThat(tempDir.toFile().listFiles()).isEmpty();
        }

        @Test
        @DisplayName("Should continue deploying remaining assets when one is missing from WAR")
        void shouldContinueDeployment_whenOneAssetMissing() throws Exception {
            when(mockProperties.getEformImageDirectory()).thenReturn(tempDir.toString());

            // First asset: not found in WAR
            when(mockServletContext.getResourceAsStream(RESOURCE_EDITCONTROL)).thenReturn(null);
            // Second and third: available
            when(mockServletContext.getResourceAsStream(RESOURCE_BLANK)).thenReturn(toStream("blank"));
            when(mockServletContext.getResourceAsStream(RESOURCE_HELP)).thenReturn(toStream("help"));

            deployer.afterPropertiesSet();

            assertThat(new File(tempDir.toFile(), "editControl2.js")).doesNotExist();
            assertThat(new File(tempDir.toFile(), "blank.rtl")).exists();
            assertThat(new File(tempDir.toFile(), "editor_help.html")).exists();
        }

        @Test
        @DisplayName("Should continue deploying other assets when file copy throws IOException")
        void shouldContinueDeployment_whenFileCopyThrowsIOException() throws Exception {
            when(mockProperties.getEformImageDirectory()).thenReturn(tempDir.toString());

            // First asset: InputStream that throws on read (simulates disk full / permission denied)
            InputStream failingStream = mock(InputStream.class);
            when(failingStream.read(any(byte[].class), anyInt(), anyInt()))
                .thenThrow(new java.io.IOException("disk full"));
            when(failingStream.transferTo(any())).thenThrow(new java.io.IOException("disk full"));
            when(mockServletContext.getResourceAsStream(RESOURCE_EDITCONTROL)).thenReturn(failingStream);

            // Other two assets: available
            when(mockServletContext.getResourceAsStream(RESOURCE_BLANK)).thenReturn(toStream("blank"));
            when(mockServletContext.getResourceAsStream(RESOURCE_HELP)).thenReturn(toStream("help"));

            assertThatCode(() -> deployer.afterPropertiesSet()).doesNotThrowAnyException();

            // editControl2.js copy failed — must not produce a zero-byte or partial final file
            assertThat(new File(tempDir.toFile(), "editControl2.js")).doesNotExist();
            assertThat(tempDir.toFile().listFiles((dir, name) -> name.startsWith("editControl2.js.") && name.endsWith(".tmp")))
                .isEmpty();
            // blank.rtl and editor_help.html should have been deployed despite editControl2.js failure
            assertThat(new File(tempDir.toFile(), "blank.rtl")).exists();
            assertThat(new File(tempDir.toFile(), "editor_help.html")).exists();
        }

        @Test
        @DisplayName("Should not delete asset created concurrently when copy fails")
        void shouldNotDeleteAssetCreatedConcurrently_whenCopyFails() throws Exception {
            when(mockProperties.getEformImageDirectory()).thenReturn(tempDir.toString());
            File concurrentAsset = new File(tempDir.toFile(), "editControl2.js");

            InputStream failingStream = mock(InputStream.class);
            when(failingStream.read(any(byte[].class), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    Files.writeString(concurrentAsset.toPath(), "concurrent valid content");
                    throw new IOException("disk full");
                });
            when(failingStream.transferTo(any())).thenAnswer(invocation -> {
                Files.writeString(concurrentAsset.toPath(), "concurrent valid content");
                throw new IOException("disk full");
            });
            when(mockServletContext.getResourceAsStream(RESOURCE_EDITCONTROL)).thenReturn(failingStream);
            when(mockServletContext.getResourceAsStream(RESOURCE_BLANK)).thenReturn(toStream("blank"));
            when(mockServletContext.getResourceAsStream(RESOURCE_HELP)).thenReturn(toStream("help"));

            assertThatCode(() -> deployer.afterPropertiesSet()).doesNotThrowAnyException();

            assertThat(concurrentAsset).exists();
            assertThat(Files.readString(concurrentAsset.toPath())).isEqualTo("concurrent valid content");
            assertThat(tempDir.toFile().listFiles((dir, name) -> name.startsWith("editControl2.js.") && name.endsWith(".tmp")))
                .isEmpty();
            assertThat(new File(tempDir.toFile(), "blank.rtl")).exists();
            assertThat(new File(tempDir.toFile(), "editor_help.html")).exists();
        }
    }

    @Nested
    @Tag("unit")
    @Tag("eform")
    @DisplayName("ServletContextAware")
    class ServletContextAwareTests {

        @Test
        @DisplayName("Should accept servlet context via setServletContext")
        void shouldAcceptServletContext_viaSetServletContext() {
            ServletContext ctx = mock(ServletContext.class);
            EFormAssetDeployer d = new EFormAssetDeployer();

            assertThatCode(() -> d.setServletContext(ctx)).doesNotThrowAnyException();
        }
    }
}
