/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform.actions;

import java.io.File;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DisplayImage2Action}.
 *
 * <p>Verifies the authorization matrix for eForm asset streaming, including the
 * prevention-specific exception that allows {@code vaccine-brands.json} to be
 * served to users with {@code _prevention} read access even when they do not
 * have general {@code _eform} read access.</p>
 */
@DisplayName("DisplayImage2Action Unit Tests")
@Tag("unit")
@Tag("eform")
class DisplayImage2ActionUnitTest extends CarlosUnitTestBase {
    private static final String VACCINE_BRANDS_JSON = "[{\"name\":\"Tdap\",\"value\":\"Adacel\"}]";

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    @Mock
    private CarlosProperties mockProperties;

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<CarlosProperties> carlosPropertiesMock;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private DisplayImage2Action action;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        carlosPropertiesMock = mockStatic(CarlosProperties.class);
        carlosPropertiesMock.when(CarlosProperties::getInstance).thenReturn(mockProperties);

        tempDir = Files.createTempDirectory("display-image-test-");
        when(mockProperties.getEformImageDirectory()).thenReturn(tempDir.toString());

        action = new DisplayImage2Action();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (carlosPropertiesMock != null) {
            carlosPropertiesMock.close();
        }
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (tempDir != null) {
            try (var paths = Files.walk(tempDir)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (Exception e) {
                                throw new RuntimeException("Failed to delete test temp path: " + path, e);
                            }
                        });
            }
        }
    }

    @Nested
    @DisplayName("Authorization matrix")
    class AuthorizationMatrix {

        @Test
        @DisplayName("should stream vaccine brands when prevention read privilege is granted")
        void shouldStreamVaccineBrands_whenPreventionReadPrivilegeGranted() throws Exception {
            mockRequest.setParameter("imagefile", DisplayImage2Action.VACCINE_BRANDS_FILE);
            Files.writeString(tempDir.resolve(DisplayImage2Action.VACCINE_BRANDS_FILE), VACCINE_BRANDS_JSON, StandardCharsets.UTF_8);

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(false);
            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_prevention"), eq("r"), isNull()))
                    .thenReturn(true);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getContentType()).isEqualTo("application/json");
            assertThat(mockResponse.getContentAsString()).contains("Adacel");
        }

        @Test
        @DisplayName("should throw SecurityException when non-vaccine asset requested without eform privilege")
        void shouldThrowSecurityException_whenNonVaccineAssetRequestedWithoutEformPrivilege() {
            mockRequest.setParameter("imagefile", "custom.json");

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(false);

            assertThatThrownBy(() -> action.execute())
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("_eform");

            verify(mockSecurityInfoManager, never()).hasPrivilege(eq(mockLoggedInInfo), eq("_prevention"), eq("r"), isNull());
            assertThat(mockResponse.getContentAsByteArray()).isEmpty();
        }

        @Test
        @DisplayName("should stream non-vaccine asset when eform read privilege is granted")
        void shouldStreamNonVaccineAsset_whenEformReadPrivilegeGranted() throws Exception {
            mockRequest.setParameter("imagefile", "custom.json");
            Files.writeString(tempDir.resolve("custom.json"), "{\"ok\":true}", StandardCharsets.UTF_8);

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(true);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getContentType()).isEqualTo("application/json");
            assertThat(mockResponse.getContentAsString()).isEqualTo("{\"ok\":true}");
        }

        @Test
        @DisplayName("should sandbox stored HTML assets served into the authenticated origin")
        void shouldSandboxHtmlAsset_whenServedToAuthenticatedSession() throws Exception {
            mockRequest.setParameter("imagefile", "legacy-help.html");
            Files.writeString(tempDir.resolve("legacy-help.html"),
                    "<html><body>help</body></html>", StandardCharsets.UTF_8);

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(true);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getContentType()).startsWith("text/html");
            // A stored asset served as text/html executes in the authenticated origin; the
            // sandbox directive (no allow-* tokens) strips scripts/forms/origin while keeping
            // passive embedding working — closing the stored-XSS channel without dropping
            // legacy html/rtl assets from the allowlist.
            assertThat(mockResponse.getHeader("Content-Security-Policy")).isEqualTo("sandbox");
            assertThat(mockResponse.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        }

        @Test
        @DisplayName("should set nosniff without a sandbox policy for passive image assets")
        void shouldSetNosniffOnly_forPassiveImageAsset() throws Exception {
            mockRequest.setParameter("imagefile", "bg.png");
            Files.write(tempDir.resolve("bg.png"), new byte[] {1, 2, 3});

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(true);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getContentType()).isEqualTo("image/png");
            assertThat(mockResponse.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(mockResponse.getHeader("Content-Security-Policy")).isNull();
        }


        @Test
        @DisplayName("should return 404 when requested asset file is missing")
        void shouldReturn404_whenRequestedAssetFileIsMissing() throws Exception {
            mockRequest.setParameter("imagefile", "consult_sig_999998.png");

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(true);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
            assertThat(mockResponse.getContentAsByteArray()).isEmpty();
        }

        @Test
        @DisplayName("should return 404 when asset disappears after validation but before streaming")
        void shouldReturn404_whenAssetDisappearsAfterValidation() throws Exception {
            mockRequest.setParameter("imagefile", "consult_sig_999998.png");
            Files.write(tempDir.resolve("consult_sig_999998.png"), new byte[] {1, 2, 3});

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(true);

            DisplayImage2Action spiedAction = spy(action);
            doThrow(new java.io.FileNotFoundException("vanished"))
                    .when(spiedAction).process(any(File.class), anyString());

            String result = spiedAction.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
            assertThat(mockResponse.getContentAsByteArray()).isEmpty();
        }

        @Test
        @DisplayName("should write HTML assets through writer when eform read privilege is granted")
        void shouldWriteHtmlAssetsThroughWriter_whenEformReadPrivilegeGranted() throws Exception {
            mockRequest.setParameter("imagefile", "custom.html");
            Files.writeString(tempDir.resolve("custom.html"), "<html><body>form</body></html>", StandardCharsets.UTF_8);
            MockHttpServletResponse trackingResponse = spy(new MockHttpServletResponse());
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(trackingResponse);
            action = new DisplayImage2Action();

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(true);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(trackingResponse.getContentType()).startsWith("text/html");
            assertThat(trackingResponse.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
            assertThat(trackingResponse.getContentAsString()).isEqualTo("<html><body>form</body></html>");
            verify(trackingResponse).getWriter();
            verify(trackingResponse, never()).getOutputStream();
        }

        @Test
        @DisplayName("should send error when binary stream cannot be opened")
        void shouldSendError_whenBinaryStreamCannotBeOpened() throws Exception {
            mockRequest.setParameter("imagefile", "custom.json");
            Files.writeString(tempDir.resolve("custom.json"), "{\"ok\":true}", StandardCharsets.UTF_8);
            MockHttpServletResponse trackingResponse = spy(new MockHttpServletResponse());
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(trackingResponse);
            action = new DisplayImage2Action();

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(true);
            doThrow(new java.io.IOException("stream unavailable")).when(trackingResponse).getOutputStream();

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(trackingResponse.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("should send error when HTML writer cannot be acquired")
        void shouldSendError_whenHtmlWriterCannotBeAcquired() throws Exception {
            mockRequest.setParameter("imagefile", "custom.html");
            Files.writeString(tempDir.resolve("custom.html"), "<html><body>form</body></html>", StandardCharsets.UTF_8);
            MockHttpServletResponse trackingResponse = spy(new MockHttpServletResponse());
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(trackingResponse);
            action = new DisplayImage2Action();

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(true);
            doThrow(new IllegalStateException("writer unavailable")).when(trackingResponse).getWriter();

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(trackingResponse.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("should throw SecurityException when vaccine brands requested without either privilege")
        void shouldThrowSecurityException_whenVaccineBrandsRequestedWithoutEitherPrivilege() {
            mockRequest.setParameter("imagefile", DisplayImage2Action.VACCINE_BRANDS_FILE);

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(false);
            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_prevention"), eq("r"), isNull()))
                    .thenReturn(false);

            assertThatThrownBy(() -> action.execute())
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("_eform or _prevention");
        }

        @Test
        @DisplayName("should return bad request when requested asset type is unsupported")
        void shouldReturnBadRequest_whenRequestedAssetTypeIsUnsupported() throws Exception {
            mockRequest.setParameter("imagefile", "custom.unsupported");
            Files.writeString(tempDir.resolve("custom.unsupported"), "data", StandardCharsets.UTF_8);

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(true);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            assertThat(mockResponse.getErrorMessage()).isEqualTo("Unsupported eform asset type");
        }

        @Test
        @DisplayName("should throw SecurityException when requested image path traverses outside allowed directory")
        void shouldThrowSecurityException_whenRequestedImagePathTraversesOutsideAllowedDirectory() {
            mockRequest.setParameter("imagefile", "../custom.json");

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(true);

            assertThatThrownBy(() -> action.execute())
                    .isInstanceOf(SecurityException.class);
        }
    }

    /**
     * The editor-asset split. {@code EFormAssetDeployer} treats {@code editControl2.js} as MANAGED
     * (replaced on startup when the on-disk bytes differ) but {@code blank.rtl} and
     * {@code editor_help.html} as SEEDED — written once if absent, then never touched, "because a
     * clinic is expected to customize them". Serving the WAR copy of a seeded asset therefore
     * discards clinic letterhead silently, which is what these tests pin.
     */
    @Nested
    @DisplayName("Editor asset resolution")
    class EditorAssetResolution {

        /** A fake exploded WAR so the tests can tell WHICH copy of an asset was served. */
        private void withBundledAsset(String fileName, String contents) throws Exception {
            Path warRoot = Files.createTempDirectory("display-image-war-");
            Path assets = warRoot.resolve("WEB-INF/eform-assets");
            Files.createDirectories(assets);
            Files.writeString(assets.resolve(fileName), contents, StandardCharsets.UTF_8);
            // "file:" prefix is required: MockServletContext resolves its base through a
            // DefaultResourceLoader, which treats a bare path as CLASSPATH-relative and would silently
            // resolve every getResourceAsStream to null — making these tests pass vacuously on an
            // empty response body rather than proving which copy was served.
            MockHttpServletRequest requestWithWar =
                    new MockHttpServletRequest(new MockServletContext("file:" + warRoot));
            requestWithWar.setParameter("imagefile", fileName);
            mockRequest = requestWithWar;
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
            action = new DisplayImage2Action();
        }

        @Test
        @DisplayName("should serve the clinic's customized blank.rtl instead of the bundled copy")
        void shouldServeClinicTemplate_whenBlankRtlExistsOnDisk() throws Exception {
            withBundledAsset("blank.rtl", "<html><body>SHIPPED BLANK</body></html>");
            // The clinic's letterhead, preserved on disk by the deployer's seed-once contract.
            Files.writeString(tempDir.resolve("blank.rtl"),
                    "<html><body>CLINIC LETTERHEAD</body></html>", StandardCharsets.UTF_8);

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(true);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getContentAsString())
                    .as("the on-disk clinic template must win over the WAR copy")
                    .contains("CLINIC LETTERHEAD")
                    .doesNotContain("SHIPPED BLANK");
            // Not sandboxed: the editor loads this into a frame and runs scripts in it. Writing this
            // directory needs _eform write, which already permits same-origin script via stored form
            // HTML, so the sandbox would break letterhead without denying anything.
            assertThat(mockResponse.getHeader("Content-Security-Policy")).isNull();
            assertThat(mockResponse.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        }

        @Test
        @DisplayName("should fall back to the bundled template when the clinic has none on disk")
        void shouldFallBackToBundledTemplate_whenBlankRtlAbsent() throws Exception {
            withBundledAsset("blank.rtl", "<html><body>SHIPPED BLANK</body></html>");
            // Deliberately no file in tempDir: a fresh install before the deployer has seeded it.

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(true);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getContentAsString()).contains("SHIPPED BLANK");
        }

        @Test
        @DisplayName("should serve the clinic's customized editor_help.html instead of the bundled copy")
        void shouldServeClinicHelp_whenEditorHelpExistsOnDisk() throws Exception {
            // SEEDED_EDITOR_ASSETS holds TWO files and only blank.rtl was covered. Verified by
            // mutation: moving editor_help.html back to BUNDLED_EDITOR_ASSETS — reintroducing this
            // commit's exact regression for the second file — left the whole class green.
            withBundledAsset("editor_help.html", "<html><body>SHIPPED HELP</body></html>");
            Files.writeString(tempDir.resolve("editor_help.html"),
                    "<html><body>CLINIC HELP</body></html>", StandardCharsets.UTF_8);

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(true);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getContentAsString())
                    .contains("CLINIC HELP")
                    .doesNotContain("SHIPPED HELP");
            assertThat(mockResponse.getHeader("Content-Security-Policy")).isNull();
        }

        @Test
        @DisplayName("should keep serving the bundled editControl2.js even when a local copy exists")
        void shouldServeBundledEditor_whenLocalEditControlExists() throws Exception {
            withBundledAsset("editControl2.js", "// SHIPPED EDITOR");
            // A local edit to a MANAGED asset is unsupported and reverted by the deployer on startup,
            // so the WAR copy must still win — this is the half of the split that must NOT change.
            Files.writeString(tempDir.resolve("editControl2.js"), "// LOCAL EDIT", StandardCharsets.UTF_8);

            when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq("_eform"), eq("r"), isNull()))
                    .thenReturn(true);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getContentAsString())
                    .contains("SHIPPED EDITOR")
                    .doesNotContain("LOCAL EDIT");
        }
    }
}
