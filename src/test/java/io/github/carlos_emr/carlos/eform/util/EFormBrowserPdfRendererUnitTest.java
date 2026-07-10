package io.github.carlos_emr.carlos.eform.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EFormBrowserPdfRenderer unit tests")
@Tag("unit")
@Tag("fast")
class EFormBrowserPdfRendererUnitTest {

    @Test
    @DisplayName("should build the dedicated browser-render servlet path")
    void shouldBuildAppPath_whenRenderingSavedEformPdf() {
        String appPath = EFormBrowserPdfRenderer.buildAppPath(187, "999998");

        assertThat(appPath)
                .startsWith("/eformViewForPdfGenerationServlet?")
                .contains("fdid=187")
                .contains("providerId=999998")
                .contains("providerId=999998");
    }


    @Test
    @DisplayName("should keep print-only cleanup rules in the bundled renderer script")
    void shouldKeepPrintCleanupRulesInBundledRendererScript() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(
                "io/github/carlos_emr/carlos/eform/browserpdf/eform-browser-pdf-render.js")) {
            assertThat(inputStream).isNotNull();
            String script = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(script)
                    .contains(".DoNotPrint")
                    .contains("#BaseSelect")
                    .contains("computeCaptureRegions")
                    .contains("unionRects")
                    .contains("document.fonts.ready instanceof Promise");
        }
    }

    @Test
    @DisplayName("should resolve the renderer temp root under base document directory when configured")
    void shouldResolveRendererTempRoot_underBaseDocumentDirectoryWhenConfigured() {
        Path root = EFormBrowserPdfRenderer.resolveRendererTempRoot(
                "/var/lib/carlos/documents",
                "/var/lib/tomcat10",
                "/tmp");

        assertThat(root)
                .isEqualTo(Paths.get("/var/lib/carlos/documents", "eform", "browser-pdf-temp"));
    }

    @Test
    @DisplayName("should resolve the renderer temp root under catalina base when base document directory is missing")
    void shouldResolveRendererTempRoot_underCatalinaBaseWhenBaseDocumentDirectoryMissing() {
        Path root = EFormBrowserPdfRenderer.resolveRendererTempRoot(
                null,
                "/var/lib/tomcat10",
                "/tmp");

        assertThat(root)
                .isEqualTo(Paths.get("/var/lib/tomcat10", "work", "carlos", "eform-browser-pdf-temp"));
    }

    @Test
    @DisplayName("should resolve the renderer temp root under a namespaced system temp fallback")
    void shouldResolveRendererTempRoot_underNamespacedSystemTempFallback() {
        Path root = EFormBrowserPdfRenderer.resolveRendererTempRoot(
                null,
                null,
                "/tmp");

        assertThat(root)
                .isEqualTo(Paths.get("/tmp", "carlos-eform-browser-pdf-temp"));
    }

    @Test
    @DisplayName("should create a secure temporary renderer directory inside the managed temp root")
    void shouldCreateSecureTemporaryRendererDirectory() throws IOException {
        Path root = Files.createTempDirectory("eform-browser-render-root-");
        Path directory = EFormBrowserPdfRenderer.createSecureTempDirectory(root, "eform-browser-render-test-");
        try {
            assertThat(Files.isDirectory(directory)).isTrue();
            assertThat(directory).hasParentRaw(root);
            if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
                assertThat(Files.getPosixFilePermissions(directory))
                        .containsExactlyInAnyOrder(
                                PosixFilePermission.OWNER_READ,
                                PosixFilePermission.OWNER_WRITE,
                                PosixFilePermission.OWNER_EXECUTE);
            }
        } finally {
            Files.deleteIfExists(directory);
            Files.deleteIfExists(root);
        }
    }

    @Test
    @DisplayName("should create a secure temporary renderer pdf file inside the managed temp root")
    void shouldCreateSecureTemporaryRendererPdfFile() throws IOException {
        Path root = Files.createTempDirectory("eform-browser-render-root-");
        Path file = EFormBrowserPdfRenderer.createSecureTempFile(root, "eform-browser-render-test-", ".pdf");
        try {
            assertThat(Files.isRegularFile(file)).isTrue();
            assertThat(file).hasParentRaw(root);
            if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
                Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
                assertThat(permissions)
                        .containsExactlyInAnyOrder(
                                PosixFilePermission.OWNER_READ,
                                PosixFilePermission.OWNER_WRITE);
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(root);
        }
    }

    @Test
    @DisplayName("should build a local base URL from project home")
    void shouldBuildLocalBaseUrl_whenNoOverrideIsProvided() {
        assertThat(EFormBrowserPdfRenderer.buildDefaultBaseUrl("carlos"))
                .isEqualTo("http://127.0.0.1:8080/carlos");
    }

    @Test
    @DisplayName("should build a local base URL from the active servlet context")
    void shouldBuildLocalBaseUrl_whenUsingTheActiveRequestContext() {
        assertThat(EFormBrowserPdfRenderer.buildLocalBaseUrl("http", 8080, "/carlos"))
                .isEqualTo("http://127.0.0.1:8080/carlos");
    }

    @Test
    @DisplayName("should reject non-local base URLs for the Playwright renderer")
    void shouldRejectNonLocalBaseUrl_whenBuildingRendererCommand() {
        assertThatThrownBy(EFormBrowserPdfRendererUnitTest::buildCommandWithInvalidBaseUrl)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("base URL");
    }

    @Test
    @DisplayName("should reject non-root-relative app paths for the Playwright renderer")
    void shouldRejectNonRootRelativeAppPath_whenBuildingRendererCommand() {
        assertThatThrownBy(EFormBrowserPdfRendererUnitTest::buildCommandWithInvalidAppPath)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Application path");
    }

    @Test
    @DisplayName("should build the node command for the Playwright renderer")
    void shouldBuildCommand_whenLaunchingRenderer() {
        List<String> command = EFormBrowserPdfRenderer.buildCommand(
                "node",
                Path.of("/tmp/carlos-develop-clean/scripts/eform-browser-pdf-render.js"),
                "http://127.0.0.1:8080/carlos",
                "/eformViewForPdfGenerationServlet?fdid=187&providerId=999998",
                Path.of("/tmp/rendered-output"),
                "/root/.cache/ms-playwright/chromium-1223/chrome-linux64/chrome",
                "JSESSIONID=abc123");

        assertThat(command).containsExactly(
                "node",
                "/tmp/carlos-develop-clean/scripts/eform-browser-pdf-render.js",
                "--base-url",
                "http://127.0.0.1:8080/carlos",
                "--app-path",
                "/eformViewForPdfGenerationServlet?fdid=187&providerId=999998",
                "--output-dir",
                "/tmp/rendered-output",
                "--cookie-header",
                "JSESSIONID=abc123",
                "--chrome-path",
                "/root/.cache/ms-playwright/chromium-1223/chrome-linux64/chrome");
    }
    private static List<String> buildCommandWithInvalidBaseUrl() {
        return EFormBrowserPdfRenderer.buildCommand(
                "node",
                Path.of("/tmp/carlos-develop-clean/scripts/eform-browser-pdf-render.js"),
                "https://evil.example/steal",
                "/eformViewForPdfGenerationServlet?fdid=187",
                Path.of("/tmp/rendered-output"),
                null,
                null);
    }

    private static List<String> buildCommandWithInvalidAppPath() {
        return EFormBrowserPdfRenderer.buildCommand(
                "node",
                Path.of("/tmp/carlos-develop-clean/scripts/eform-browser-pdf-render.js"),
                "http://127.0.0.1:8080/carlos",
                "https://evil.example/steal",
                Path.of("/tmp/rendered-output"),
                null,
                null);
    }

}
