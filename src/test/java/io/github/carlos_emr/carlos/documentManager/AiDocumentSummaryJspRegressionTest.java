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

    /** Textarea-closing payloads must stay plain text in both preview and correction controls. */
    @Test
    void shouldEscapeCorrectionsAndKeepContentUpdateSeparateFromAffirmation() throws IOException {
        String source = Files.readString(AI_DOCUMENT_SUMMARY_JSP, StandardCharsets.UTF_8);
        int route = source.indexOf("/documentManager/ReviseReviewedAiDocumentSummary");
        assertThat(route).isPositive().isEqualTo(source.lastIndexOf("/documentManager/ReviseReviewedAiDocumentSummary"));
        String form = source.substring(source.lastIndexOf("<form", route), source.indexOf("</form>", route));
        assertThat(form).contains("method=\"post\"", "<csrf:tokenname/>", "<csrf:tokenvalue/>", "name=\"draftId\"",
                "name=\"removePoint\"", "maxlength=\"4000\"", "maxlength=\"2000\"");
        assertThat(form).contains(
                "required><carlos:encode value=\"${documentSummaryCorrectionPending ? documentSummaryCorrectionOverview : documentSummaryOverview}\"/></textarea>",
                "class=\"form-control\"><carlos:encode value=\"${documentSummaryCorrectionPending ? documentSummaryCorrectionPoints[position.index] : point.text}\"/></textarea>",
                "value=\"${position.index}\"<c:if test=\"${documentSummaryCorrectionPending and documentSummaryCorrectionRemoved[position.index]}\"> checked</c:if>>");
        // Posted edits come back only through carlos:encode, and a tick is pre-set only from the server-side echo.
        assertThat(form).doesNotContain("${documentSummaryCorrectionOverview}", "${documentSummaryCorrectionPoints");
        assertThat(form.indexOf("checked")).isEqualTo(form.lastIndexOf("checked"));
        // A removed point's text may be cleared; the server validates kept points instead.
        assertThat(form).doesNotContain("name=\"pointText\" maxlength=\"2000\" rows=\"3\" class=\"form-control\" required");
        String notSaved = "<carlos:encode value=\"${documentSummaryCorrectionError}\"/>";
        assertThat(source.indexOf(notSaved)).isPositive().isLessThan(route);
        assertThat(source).contains("<c:if test=\"${not empty documentSummaryCorrectionError}\">",
                "<p class=\"alert alert-warning\" role=\"alert\">" + notSaved + "</p>");
        assertThat(form).doesNotContain("name=\"affirmReview\"", "name=\"evidence\"", "contenteditable", " checked>");
        assertThat(source).contains("<p class=\"document-summary-text\"><carlos:encode value=\"${documentSummaryOverview}\"/></p>",
                "name=\"affirmReview\" value=\"true\" required aria-describedby=\"summary-confirm-saved\"",
                "<p id=\"summary-confirm-saved\"><fmt:message key=\"documentSummary.review.confirmSaved\"/></p>",
                "aria-describedby=\"summary-confirm-saved\"> <fmt:message key=\"documentSummary.review.affirm\"/></label>");
        String encoded = io.github.carlos_emr.carlos.utility.SafeEncode.forHtmlContent(
                "FAKE document. </textarea><script>alert(1)</script>&");
        assertThat(encoded).doesNotContain("</textarea", "<script").contains("&lt;", "&amp;");
    }

    /** Every clinician-facing string on the page is a bundle key with a real translation in each locale. */
    @Test
    @DisplayName("should localize the review workflow through documentSummary keys present in every locale")
    void shouldLocalizeReviewWorkflow_throughBundleKeysInEveryLocale() throws IOException {
        String source = Files.readString(AI_DOCUMENT_SUMMARY_JSP, StandardCharsets.UTF_8);
        assertThat(source).contains("<fmt:setBundle basename=\"oscarResources\"/>");
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("<fmt:message\\b[^>]*\\bkey=\"([^\"$]+)\"").matcher(source);
        while (matcher.find()) keys.add(matcher.group(1));
        assertThat(keys).contains("documentSummary.review.prepare", "documentSummary.review.noticeLabel",
                "documentSummary.review.openOriginal", "documentSummary.review.generated", "documentSummary.review.revised",
                "documentSummary.review.downloadOriginal", "documentSummary.review.correctHeading",
                "documentSummary.review.correctHelp", "documentSummary.overview", "documentSummary.review.point",
                "documentSummary.review.removePoint", "documentSummary.review.save", "documentSummary.review.confirmSaved",
                "documentSummary.review.affirm", "documentSummary.review.record", "documentSummary.review.recorded",
                "documentSummary.review.deliveryUnavailable", "documentSummary.review.regenerate",
                "documentSummary.review.discard");
        // The action messages have no fmt:message tag; they resolve from the same bundle server-side.
        keys.addAll(java.util.List.of("documentSummary.review.unavailable", "documentSummary.review.correctionNotSaved",
                "documentSummary.changedDuringGeneration", "documentSummary.contentUnavailable"));
        java.util.Properties english = bundle("en");
        for (String locale : new String[] {"en", "fr", "es", "pl", "pt_BR"}) {
            java.util.Properties bundle = bundle(locale);
            for (String key : keys) {
                String value = bundle.getProperty(key);
                assertThat(value).as("%s in oscarResources_%s.properties", key, locale).isNotBlank();
                if (!"en".equals(locale) && !key.endsWith(".point")) {
                    assertThat(value).as("%s should be translated in %s", key, locale).isNotEqualTo(english.getProperty(key));
                }
            }
        }
        // No clinician-facing English sentence is left in the page source.
        assertThat(source).doesNotContain("Prepare a clinician-reviewed summary", "Open current original document",
                "Download the exact original", "Correct summary text", "Save corrections", "Record my clinician review",
                "Clinician review is recorded", "Summary delivery is unavailable", "Generate a new draft",
                "Discard this draft", "Remove point ${", ">Point ${", ">Overview<", "Generated: ", "Clinician text revision: ");
    }

    /** Bundle text inside an attribute must not close it, and {0} keys are exactly the ones given a fmt:param. */
    @Test
    @DisplayName("should keep attribute-bound values quote-free and parameterised keys matched to fmt:param")
    void shouldKeepAttributeValuesQuoteFree_andParameterisedKeysMatchedToFmtParam() throws IOException {
        String source = Files.readString(AI_DOCUMENT_SUMMARY_JSP, StandardCharsets.UTF_8);
        java.util.Set<String> attributeKeys = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher inAttribute = java.util.regex.Pattern
                .compile("=\"<fmt:message\\b[^>]*\\bkey=\"([^\"$]+)\"\\s*/>\"").matcher(source);
        while (inAttribute.find()) attributeKeys.add(inAttribute.group(1));
        assertThat(attributeKeys).contains("documentSummary.review.noticeLabel", "documentSummary.pending");
        java.util.Set<String> parameterised = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher withParam = java.util.regex.Pattern
                .compile("<fmt:message\\b[^>]*\\bkey=\"([^\"$]+)\"\\s*>\\s*<fmt:param\\b").matcher(source);
        while (withParam.find()) parameterised.add(withParam.group(1));
        assertThat(parameterised).containsExactlyInAnyOrder("documentSummary.review.point", "documentSummary.review.removePoint");
        for (String locale : new String[] {"en", "fr", "es", "pl", "pt_BR"}) {
            java.util.Properties bundle = bundle(locale);
            for (String key : attributeKeys) {
                assertThat(bundle.getProperty(key)).as("%s in oscarResources_%s.properties", key, locale)
                        .isNotBlank().doesNotContain("\"");
            }
            java.util.Set<String> placeholderKeys = new java.util.TreeSet<>();
            for (String name : bundle.stringPropertyNames()) {
                if (name.startsWith("documentSummary.") && bundle.getProperty(name).contains("{0}")) placeholderKeys.add(name);
            }
            assertThat(placeholderKeys).as("documentSummary keys with {0} in %s", locale)
                    .containsExactlyInAnyOrderElementsOf(parameterised);
            // A parameterised value goes through MessageFormat, where a lone apostrophe swallows text
            // up to the next one; it must be doubled ('') or absent.
            for (String key : parameterised) {
                assertThat(bundle.getProperty(key)).as("%s in %s must not hold a lone apostrophe", key, locale)
                        .doesNotMatch("(?s).*(?<!')'(?!').*");
            }
        }
    }

    private static java.util.Properties bundle(String locale) throws IOException {
        java.util.Properties properties = new java.util.Properties();
        try (var in = Files.newInputStream(resolveProjectPath(Path.of("src", "main", "resources",
                "oscarResources_" + locale + ".properties")))) {
            properties.load(in);
        }
        return properties;
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
