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
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import jakarta.servlet.ServletContext;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
        @DisplayName("Should skip deployment when directory cannot be created")
        void shouldSkipDeployment_whenDirectoryCannotBeCreated() throws Exception {
            // Place a regular file where a parent directory is needed — mkdirs() fails even as root
            // because a file cannot contain a subdirectory
            Path blockingFile = tempDir.resolve("blocker");
            Files.writeString(blockingFile, "I am a file, not a directory");
            Path uncreatable = blockingFile.resolve("images");

            when(mockProperties.getEformImageDirectory()).thenReturn(uncreatable.toString());

            deployer.afterPropertiesSet();

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

            // editControl2.js copy failed — must not produce a zero-byte or partial file
            assertThat(new File(tempDir.toFile(), "editControl2.js")).doesNotExist();
            // blank.rtl and editor_help.html should have been deployed despite editControl2.js failure
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

    @Nested
    @Tag("unit")
    @Tag("eform")
    @DisplayName("Directory Creation")
    class DirectoryCreation {

        @Test
        @DisplayName("Should create missing directory and deploy all assets")
        void shouldCreateDirectoryAndDeployAllAssets_whenDirectoryDoesNotExist() throws IOException {
            Path newDir = tempDir.resolve("missing-eform-images");
            // newDir does not exist yet — deployer must create it
            when(mockProperties.getEformImageDirectory()).thenReturn(newDir.toString());
            stubAllAssets();

            deployer.afterPropertiesSet();

            assertThat(newDir).isDirectory();
            assertThat(new File(newDir.toFile(), "editControl2.js")).exists();
            assertThat(new File(newDir.toFile(), "blank.rtl")).exists();
            assertThat(new File(newDir.toFile(), "editor_help.html")).exists();
        }

        @Test
        @DisplayName("Should log info message when directory is created")
        void shouldLogInfo_whenDirectoryIsCreated() {
            Path newDir = tempDir.resolve("log-test-images");
            when(mockProperties.getEformImageDirectory()).thenReturn(newDir.toString());
            when(mockServletContext.getResourceAsStream(anyString())).thenReturn(null);

            try (LogCapture log = LogCapture.forLogger(EFormAssetDeployer.class)) {
                deployer.afterPropertiesSet();

                assertThat(log.events())
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.INFO);
                        assertThat(event.getMessage().getFormattedMessage())
                            .contains("Created eForm image directory");
                    });
            }
        }

        @Test
        @DisplayName("Should skip deployment when configured path exists as a regular file")
        void shouldSkipDeployment_whenConfiguredPathIsExistingFile() throws Exception {
            // resolveConfiguredDirectory throws SecurityException when the path is an existing file
            File existingFile = tempDir.resolve("not-a-directory.txt").toFile();
            Files.writeString(existingFile.toPath(), "I am a file");

            when(mockProperties.getEformImageDirectory()).thenReturn(existingFile.toString());

            deployer.afterPropertiesSet();

            verifyNoInteractions(mockServletContext);
        }

        @Test
        @DisplayName("Should not throw when directory creation succeeds and no assets are in WAR")
        void shouldNotThrow_whenDirectoryCreatedButNoAssetsInWar() {
            Path newDir = tempDir.resolve("empty-war-test");
            when(mockProperties.getEformImageDirectory()).thenReturn(newDir.toString());
            when(mockServletContext.getResourceAsStream(anyString())).thenReturn(null);

            assertThatCode(() -> deployer.afterPropertiesSet()).doesNotThrowAnyException();
            assertThat(newDir).isDirectory();
        }

        @Test
        @DisplayName("Should log warning with specific failed op names when permission setting fails after directory creation")
        void shouldLogWarningWithFailedOps_whenPermissionSettingFails() throws Exception {
            // Spy a real directory so new File(spiedDir, filename) can access the internal path field,
            // then override permission methods to return false — exercises the warning branch
            // without needing to run as a non-root user or modify real filesystem permissions.
            Path permTestDir = tempDir.resolve("perms-spy-test");
            Files.createDirectory(permTestDir);
            File spiedDir = spy(permTestDir.toFile());
            doReturn(false).when(spiedDir).isDirectory();      // forces entry into creation block
            doReturn(true).when(spiedDir).mkdirs();            // mkdirs reports success
            doReturn(false).when(spiedDir).setReadable(true, true);
            doReturn(true).when(spiedDir).setWritable(true, true);
            doReturn(false).when(spiedDir).setExecutable(true, true);

            when(mockServletContext.getResourceAsStream(anyString())).thenReturn(null);

            try (MockedStatic<PathValidationUtils> pvMock = mockStatic(PathValidationUtils.class);
                 LogCapture log = LogCapture.forLogger(EFormAssetDeployer.class)) {

                pvMock.when(() -> PathValidationUtils.resolveConfiguredDirectory(anyString(), anyString()))
                      .thenReturn(spiedDir);
                when(mockProperties.getEformImageDirectory()).thenReturn(permTestDir.toString());

                deployer.afterPropertiesSet();

                assertThat(log.events()).anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getMessage().getFormattedMessage())
                        .contains("Could not set owner-only permissions")
                        .contains("setReadable")
                        .contains("setExecutable")
                        .doesNotContain("setWritable");
                });
            }
        }
    }
}
