package io.github.carlos_emr.carlos.eform.util;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
    @DisplayName("should build the node command for the Playwright renderer")
    void shouldBuildCommand_whenLaunchingRenderer() {
        List<String> command = EFormBrowserPdfRenderer.buildCommand(
                "node",
                Path.of("/tmp/carlos-develop-clean/scripts/eform-browser-pdf-render.js"),
                "http://127.0.0.1:8080/carlos",
                "/eformViewForPdfGenerationServlet?fdid=187&providerId=999998",
                Path.of("/tmp/rendered-output"),
                "/root/.cache/ms-playwright/chromium-1223/chrome-linux64/chrome");

        assertThat(command).containsExactly(
                "node",
                "/tmp/carlos-develop-clean/scripts/eform-browser-pdf-render.js",
                "--base-url",
                "http://127.0.0.1:8080/carlos",
                "--app-path",
                "/eformViewForPdfGenerationServlet?fdid=187&providerId=999998",
                "--output-dir",
                "/tmp/rendered-output",
                "--chrome-path",
                "/root/.cache/ms-playwright/chromium-1223/chrome-linux64/chrome");
    }
}
