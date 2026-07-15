package io.github.carlos_emr.carlos.eform.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                .startsWith("/EFormViewForPdfGenerationServlet?")
                .contains("fdid=187")
                .contains("providerId=999998")
                .contains("browserRender=true");
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
                    .contains("backgroundCandidates")
                    .contains("document.fonts.ready instanceof Promise")
                    .contains("url: baseUrl.href");
        }
    }

    @Test
    @DisplayName("should resolve the renderer temp root under catalina base so fax path validation accepts the output")
    void shouldResolveRendererTempRoot_underCatalinaBaseWhenConfigured() {
        Path root = EFormBrowserPdfRenderer.resolveRendererTempRoot(
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
                "/tmp");

        assertThat(root)
                .isEqualTo(Paths.get("/tmp", "carlos-eform-browser-pdf-temp"));
    }

    @Test
    @DisplayName("should accept only complete private IPv4 literals for the renderer host check")
    void shouldAcceptOnlyPrivateIpv4Literals_forRendererHostCheck() {
        assertThat(EFormBrowserPdfRenderer.isLocalRendererHost("10.0.0.5")).isTrue();
        assertThat(EFormBrowserPdfRenderer.isLocalRendererHost("192.168.1.20")).isTrue();
        assertThat(EFormBrowserPdfRenderer.isLocalRendererHost("172.16.0.1")).isTrue();
        assertThat(EFormBrowserPdfRenderer.isLocalRendererHost("10.attacker.example")).isFalse();
        assertThat(EFormBrowserPdfRenderer.isLocalRendererHost("192.168.evil.example")).isFalse();
        assertThat(EFormBrowserPdfRenderer.isLocalRendererHost("10.0.0.999")).isFalse();
        assertThat(EFormBrowserPdfRenderer.isLocalRendererHost("172.32.0.1")).isFalse();
    }

    @Test
    @DisplayName("should keep the bundled renderer scripts identical to the checkout scripts")
    void shouldKeepBundledRendererScripts_identicalToCheckoutScripts() throws IOException {
        for (String scriptName : List.of("eform-browser-pdf-render.js", "eform-local-playwright-utils.js")) {
            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(
                    "io/github/carlos_emr/carlos/eform/browserpdf/" + scriptName)) {
                assertThat(inputStream).as("bundled resource %s", scriptName).isNotNull();
                String bundled = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                String checkout = Files.readString(Paths.get("scripts", scriptName));
                assertThat(bundled)
                        .as("bundled %s must stay in sync with scripts/%s", scriptName, scriptName)
                        .isEqualTo(checkout);
            }
        }
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
    @DisplayName("should build URL context paths with forward slashes")
    void shouldBuildLocalBaseUrl_whenContextPathDoesNotStartWithSlash() {
        assertThat(EFormBrowserPdfRenderer.buildLocalBaseUrl("http", 8080, "carlos"))
                .isEqualTo("http://127.0.0.1:8080/carlos");
    }

    @Test
    @DisplayName("should reject non-local base URLs for the Playwright renderer")
    void shouldRejectNonLocalBaseUrl_whenApplyingRendererEnvironment() {
        assertThatThrownBy(EFormBrowserPdfRendererUnitTest::applyEnvironmentWithInvalidBaseUrl)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("base URL");
    }

    @Test
    @DisplayName("should reject non-root-relative app paths for the Playwright renderer")
    void shouldRejectNonRootRelativeAppPath_whenApplyingRendererEnvironment() {
        assertThatThrownBy(EFormBrowserPdfRendererUnitTest::applyEnvironmentWithInvalidAppPath)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Application path");
    }

    @Test
    @DisplayName("should build the node command for the Playwright renderer without request-derived argv")
    void shouldBuildCommand_whenLaunchingRenderer() {
        List<String> command = EFormBrowserPdfRenderer.buildCommand(
                "node",
                Path.of("/tmp/carlos-develop-clean/scripts/eform-browser-pdf-render.js"),
                Path.of("/tmp/rendered-output"));

        assertThat(command).containsExactly(
                "node",
                "/tmp/carlos-develop-clean/scripts/eform-browser-pdf-render.js",
                "--output-dir",
                "/tmp/rendered-output");
    }

    @Test
    @DisplayName("should accept a direct node_modules directory candidate for Playwright resolution")
    void shouldAcceptNodeModulesDirectoryCandidate_whenResolvingPlaywrightModules() throws IOException {
        Path root = Files.createTempDirectory("playwright-modules-root-");
        Path nodeModules = Files.createDirectories(root.resolve("node_modules"));
        Path playwright = Files.createDirectories(nodeModules.resolve("playwright"));
        try {
            assertThat(EFormBrowserPdfRenderer.findNodeModulesDirectory(List.of(nodeModules)))
                    .isEqualTo(nodeModules);
        } finally {
            Files.deleteIfExists(playwright);
            Files.deleteIfExists(nodeModules);
            Files.deleteIfExists(root);
        }
    }

    @Test
    @DisplayName("should accept a checkout root candidate for Playwright resolution")
    void shouldAcceptCheckoutRootCandidate_whenResolvingPlaywrightModules() throws IOException {
        Path root = Files.createTempDirectory("playwright-checkout-root-");
        Path nodeModules = Files.createDirectories(root.resolve("node_modules"));
        Path playwright = Files.createDirectories(nodeModules.resolve("playwright"));
        try {
            assertThat(EFormBrowserPdfRenderer.findNodeModulesDirectory(List.of(root)))
                    .isEqualTo(nodeModules);
        } finally {
            Files.deleteIfExists(playwright);
            Files.deleteIfExists(nodeModules);
            Files.deleteIfExists(root);
        }
    }

    @Test
    @DisplayName("should ignore root candidates when resolving Playwright modules")
    void shouldIgnoreRootCandidate_whenResolvingPlaywrightModules() {
        assertThat(EFormBrowserPdfRenderer.findNodeModulesDirectory(List.of(Path.of("/")))).isNull();
    }

    @Test
    @DisplayName("should parse path lists using the current platform separator")
    void shouldParsePathList_whenNodePathContainsMultipleEntries() {
        String rawPaths = "/usr/lib/node_modules" + java.io.File.pathSeparator + "/usr/local/lib/node_modules";

        assertThat(EFormBrowserPdfRenderer.parsePathList(rawPaths))
                .containsExactly(
                        Path.of("/usr/lib/node_modules"),
                        Path.of("/usr/local/lib/node_modules"));
    }

    @Test
    @DisplayName("should apply validated renderer settings to the child process environment")
    void shouldApplyRendererEnvironment_whenLaunchingRenderer() {
        Map<String, String> environment = new HashMap<>();

        EFormBrowserPdfRenderer.applyRendererEnvironment(
                environment,
                "http://127.0.0.1:8080/carlos/",
                "/EFormViewForPdfGenerationServlet?fdid=187&providerId=999998&browserRender=true",
                "JSESSIONID=abc123",
                "/root/.cache/ms-playwright/chromium-1223/chrome-linux64/chrome");

        assertThat(environment)
                .containsEntry("CARLOS_EFORM_RENDER_BASE_URL", "http://127.0.0.1:8080/carlos")
                .containsEntry("CARLOS_EFORM_RENDER_APP_PATH", "/EFormViewForPdfGenerationServlet?fdid=187&providerId=999998&browserRender=true")
                .containsEntry("CARLOS_EFORM_RENDER_COOKIE_HEADER", "JSESSIONID=abc123")
                .containsEntry("CARLOS_EFORM_RENDER_CHROME_PATH", "/root/.cache/ms-playwright/chromium-1223/chrome-linux64/chrome");
    }

    private static void applyEnvironmentWithInvalidBaseUrl() {
        EFormBrowserPdfRenderer.applyRendererEnvironment(
                new HashMap<>(),
                "https://evil.example/steal",
                "/EFormViewForPdfGenerationServlet?fdid=187&browserRender=true",
                null,
                null);
    }

    private static void applyEnvironmentWithInvalidAppPath() {
        EFormBrowserPdfRenderer.applyRendererEnvironment(
                new HashMap<>(),
                "http://127.0.0.1:8080/carlos",
                "https://evil.example/steal",
                null,
                null);
    }

}
