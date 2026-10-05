/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the AI document-summary page's chart-update assets.
 *
 * @since 2026-10-05
 */
@DisplayName("aiDocumentSummary.jsp regressions")
@Tag("unit")
@Tag("documentManager")
class AiDocumentSummaryJspRegressionTest {

    /**
     * The only launch link is gated, so with the feature off the dialog, script and stylesheet
     * would only be dead weight on every summary page.
     */
    @Test
    @DisplayName("should load the chart-update link, dialog and assets only when the feature is enabled")
    void shouldLoadChartUpdateAssets_onlyWhenFeatureEnabled() throws IOException {
        String source = Files.readString(AI_DOCUMENT_SUMMARY_JSP, StandardCharsets.UTF_8);
        assertThat(source).contains("<%@ taglib uri=\"jakarta.tags.core\" prefix=\"c\" %>");
        for (String asset : new String[] {"/css/ai-chart-updates-navigation.css\"",
                "chart-update-document-link",
                "<%@ include file=\"/WEB-INF/jspf/chart-update-error-dialog.jspf\" %>",
                "/js/ai-chart-updates-navigation.js\""}) {
            int at = source.indexOf(asset);
            assertThat(at).as(asset).isPositive().isEqualTo(source.lastIndexOf(asset));
            int guard = source.lastIndexOf("<c:if test=\"${chartUpdatesEnabled}\">", at);
            assertThat(guard).as("%s must sit inside the chartUpdatesEnabled guard", asset).isPositive();
            assertThat(source.indexOf("</c:if>", guard)).as("%s must sit inside the chartUpdatesEnabled guard", asset)
                    .isGreaterThan(at);
        }
    }

    /** The summary form's double-submit protection must load whether or not chart updates are on. */
    @Test
    @DisplayName("should load the summary script outside the chart-update guard")
    void shouldLoadSummaryScript_outsideChartUpdateGuard() throws IOException {
        String source = Files.readString(AI_DOCUMENT_SUMMARY_JSP, StandardCharsets.UTF_8);
        String script = "/js/ai-document-summary.js\"";
        int at = source.indexOf(script);
        assertThat(at).as(script).isPositive().isEqualTo(source.lastIndexOf(script));
        int guard = source.lastIndexOf("<c:if test=\"${chartUpdatesEnabled}\">", at);
        if (guard >= 0) {
            assertThat(source.indexOf("</c:if>", guard)).as("%s must sit outside the chartUpdatesEnabled guard", script)
                    .isBetween(guard, at);
        }
    }

    private static final Path AI_DOCUMENT_SUMMARY_JSP =
            resolveProjectPath(Path.of("src", "main", "webapp", "WEB-INF", "jsp", "documentManager",
                    "aiDocumentSummary.jsp"));

    /**
     * Resolves a repo-relative fixture by walking up from the working directory, so the test
     * passes whether Surefire or an IDE picked the module root or a parent as {@code user.dir}.
     */
    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty("basedir", System.getProperty("user.dir")))
                .toAbsolutePath()
                .normalize();
        for (int checkedParents = 0; current != null && checkedParents < 6; checkedParents++) {
            Path candidate = current.resolve(relativePath).normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate " + relativePath);
    }
}
