/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
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
package io.github.carlos_emr.carlos.eform.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
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
    void shouldKeepPrintCleanupRules_whenBundledRendererScriptLoaded() throws IOException {
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
                    .contains("installSameOriginRequestGuard")
                    .contains("isSevereConsoleMessage")
                    .contains("Console error while rendering eForm PDF")
                    .contains("ignoreHTTPSErrors")
                    .contains("Array.from(document.images).map")
                    .contains("[document.body, ...document.body.querySelectorAll('*')]")
                    .contains("url: baseUrl.href");
        }
    }

    @Test
    @DisplayName("should resolve the renderer temp parent under catalina work so fax path validation accepts the output")
    void shouldResolveRendererTempRoot_underCatalinaBaseWhenConfigured() {
        Path root = EFormBrowserPdfRenderer.resolveRendererTempRoot(
                "/var/lib/tomcat10",
                "/tmp");

        assertThat(root)
                .isEqualTo(Paths.get("/var/lib/tomcat10", "work"));
    }

    @Test
    @DisplayName("should resolve the renderer temp parent to the system temp fallback")
    void shouldResolveRendererTempRoot_underNamespacedSystemTempFallback() {
        Path root = EFormBrowserPdfRenderer.resolveRendererTempRoot(
                null,
                "/tmp");

        assertThat(root)
                .isEqualTo(Paths.get("/tmp"));
    }

    @Test
    @DisplayName("should accept only loopback hosts for the renderer host check")
    void shouldAcceptOnlyLoopbackHosts_forRendererHostCheck() {
        assertThat(EFormBrowserPdfRenderer.isLoopbackRendererHost("localhost")).isTrue();
        assertThat(EFormBrowserPdfRenderer.isLoopbackRendererHost("127.0.0.1")).isTrue();
        assertThat(EFormBrowserPdfRenderer.isLoopbackRendererHost("::1")).isTrue();
        assertThat(EFormBrowserPdfRenderer.isLoopbackRendererHost("10.0.0.5")).isFalse();
        assertThat(EFormBrowserPdfRenderer.isLoopbackRendererHost("192.168.1.20")).isFalse();
        assertThat(EFormBrowserPdfRenderer.isLoopbackRendererHost("172.16.0.1")).isFalse();
        assertThat(EFormBrowserPdfRenderer.isLoopbackRendererHost("host.docker.internal")).isFalse();
        assertThat(EFormBrowserPdfRenderer.isLoopbackRendererHost("carlos")).isFalse();
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
    void shouldCreateSecureDirectory_whenCreatingRendererTempDirectory() throws IOException {
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
    void shouldCreateSecurePdfFile_whenCreatingRendererTempFile() throws IOException {
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
    @DisplayName("should clean up only expired browser-rendered private roots in the managed temp parent")
    void shouldCleanupExpiredRendererRoots_whenManagedParentContainsOldOutputs() throws IOException {
        Path root = Files.createTempDirectory("eform-browser-render-cleanup-");
        Path staleDirectory = Files.createDirectory(root.resolve("carlos-eform-browser-pdf-stale"));
        Path recentDirectory = Files.createDirectory(root.resolve("carlos-eform-browser-pdf-recent"));
        Path unrelatedDirectory = Files.createDirectory(root.resolve("unrelated"));
        try {
            Files.createFile(staleDirectory.resolve("eform-browser-render-stale.pdf"));
            Files.createFile(recentDirectory.resolve("eform-browser-render-recent.pdf"));
            Files.createFile(unrelatedDirectory.resolve("unrelated.pdf"));
            Files.setLastModifiedTime(staleDirectory, FileTime.from(Instant.now().minus(Duration.ofHours(26))));
            Files.setLastModifiedTime(recentDirectory, FileTime.from(Instant.now()));
            Files.setLastModifiedTime(unrelatedDirectory, FileTime.from(Instant.now().minus(Duration.ofHours(26))));

            EFormBrowserPdfRenderer.cleanupExpiredRendererRoots(root, Duration.ofHours(24));

            assertThat(staleDirectory).doesNotExist();
            assertThat(recentDirectory).exists();
            assertThat(unrelatedDirectory).exists();
        } finally {
            deleteRecursivelyIfExists(staleDirectory);
            deleteRecursivelyIfExists(recentDirectory);
            deleteRecursivelyIfExists(unrelatedDirectory);
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
    @DisplayName("should build a local HTTPS base URL from the active servlet connector")
    void shouldBuildLocalBaseUrl_whenUsingHttpsConnector() {
        assertThat(EFormBrowserPdfRenderer.buildLocalBaseUrl("https", 8443, "/carlos"))
                .isEqualTo("https://127.0.0.1:8443/carlos");
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
    @DisplayName("should reject private-network base URLs for the Playwright renderer")
    void shouldRejectPrivateNetworkBaseUrl_whenApplyingRendererEnvironment() {
        assertThatThrownBy(EFormBrowserPdfRendererUnitTest::applyEnvironmentWithPrivateBaseUrl)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loopback");
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

    private static void applyEnvironmentWithPrivateBaseUrl() {
        EFormBrowserPdfRenderer.applyRendererEnvironment(
                new HashMap<>(),
                "http://10.0.0.5:8080/carlos",
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

    private static void deleteRecursivelyIfExists(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(candidate -> candidate.toFile().delete());
        }
    }

}
