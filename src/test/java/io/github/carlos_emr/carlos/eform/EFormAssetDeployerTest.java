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
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.io.ByteArrayInputStream;
import java.io.File;
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
        // Clean up temp directory
        if (tempDir != null) {
            File[] files = tempDir.toFile().listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
            tempDir.toFile().delete();
        }
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
        @DisplayName("Should skip deployment when image directory does not exist")
        void shouldSkipDeployment_whenImageDirectoryDoesNotExist() {
            when(mockProperties.getEformImageDirectory()).thenReturn("/nonexistent/path/eform/images");

            deployer.afterPropertiesSet();

            verifyNoInteractions(mockServletContext);
        }
    }

    @Nested
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

            // blank.rtl and editor_help.html should have been deployed despite editControl2.js failure
            assertThat(new File(tempDir.toFile(), "blank.rtl")).exists();
            assertThat(new File(tempDir.toFile(), "editor_help.html")).exists();
        }
    }

    @Nested
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
