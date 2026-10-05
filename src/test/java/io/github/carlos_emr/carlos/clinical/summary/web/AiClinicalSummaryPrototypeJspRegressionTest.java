/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source-level guard for the localized patient overview prototype. The page sets {@code <html lang>}
 * from the request locale, so its chrome must come from the bundle; host- and model-written English
 * must stay marked {@code lang="en"} (WCAG 3.1.1 and 3.1.2).
 *
 * @since 2026-10-05
 */
@DisplayName("AI clinical summary prototype JSP i18n")
@Tag("unit")
@Tag("i18n")
class AiClinicalSummaryPrototypeJspRegressionTest {
    private static final Path JSP = Path.of("src/main/webapp/WEB-INF/jsp/clinical/aiSummaryPrototype.jsp");
    private static final Path SCRIPT = Path.of("src/main/webapp/js/ai-summary-prototype.js");
    private static final Path ENCOUNTER_HEADER = Path.of("src/main/webapp/WEB-INF/jsp/casemgmt/newEncounterHeader.jsp");
    private static final String PREFIX = "clinical.aiSummaryPrototype.";
    private static final String[] LOCALES = {"en", "fr", "es", "pl", "pt_BR"};
    private static final Pattern FMT_KEY = Pattern.compile("<fmt:message\\b[^>]*\\bkey=\"([^\"]+)\"");

    @Test
    @DisplayName("should declare the oscarResources bundle and keep the request-locale html lang")
    void shouldDeclareOscarResourcesBundle_forLocalizedPage() throws IOException {
        String jsp = read(JSP);

        assertThat(jsp)
                .contains("<%@ taglib uri=\"jakarta.tags.fmt\" prefix=\"fmt\" %>")
                .contains("<fmt:setBundle basename=\"oscarResources\"/>")
                .contains("<html lang=\"${carlos:forHtmlAttribute(pageContext.request.locale.language)}\">")
                .doesNotContain("<html lang=\"en\"");
        assertThat(jsp.indexOf("<fmt:setBundle")).isLessThan(jsp.indexOf("<!DOCTYPE html>"));
    }

    @Test
    @DisplayName("should not reintroduce hard-coded English chrome in the page, script or encounter link")
    void shouldNotContainHardCodedEnglishChrome_inPageScriptOrLaunchLink() throws IOException {
        assertThat(read(JSP))
                .doesNotContain("Generate AI draft")
                .doesNotContain("Regenerate draft")
                .doesNotContain("Show source evidence")
                .doesNotContain("Close source evidence")
                .doesNotContain(">Patient overview<")
                .doesNotContain("Unverified AI draft")
                .doesNotContain("Choose a source document")
                .doesNotContain("Source ID:")
                .doesNotContain("sources in bundle");
        assertThat(read(SCRIPT))
                .doesNotContain("Generating draft")
                .doesNotContain("Preparing the summary")
                .doesNotContain("linked source")
                .doesNotContain("\"Source \"")
                .doesNotContain("% source evidence");
        assertThat(read(ENCOUNTER_HEADER))
                .doesNotContain(">Patient overview</a>")
                .contains("<fmt:message key=\"clinical.aiSummaryPrototype.labelPatientOverview\"/>");
    }

    @Test
    @DisplayName("should pass script text through encoded data attributes")
    void shouldPassScriptText_throughEncodedDataAttributes() throws IOException {
        String jsp = read(JSP);
        String script = read(SCRIPT);

        assertThat(jsp)
                .contains("data-msg-generating=\"${carlos:forHtmlAttribute(generatingLabel)}\"")
                .contains("data-msg-preparing=\"${carlos:forHtmlAttribute(preparingLabel)}\"")
                .contains("data-msg-source-position=\"${carlos:forHtmlAttribute(sourcePositionTemplate)}\"")
                .contains("data-msg-linked-sources=\"${carlos:forHtmlAttribute(linkedSourcesTemplate)}\"")
                .contains("data-msg-evidence-width=\"${carlos:forHtmlAttribute(evidenceWidthTemplate)}\"");
        assertThat(script)
                .contains("generationForm.dataset.msgGenerating")
                .contains("generationForm.dataset.msgPreparing")
                .contains("messages.msgSourcePosition")
                .contains("messages.msgLinkedSources")
                .contains("messages.msgEvidenceWidth");
    }

    @Test
    @DisplayName("should mark host- and model-written text as English")
    void shouldMarkHostAndModelText_asEnglish() throws IOException {
        String jsp = read(JSP);
        // Each element is found by its class, id or encoded content; attribute order does not matter.
        Map<String, String> englishElements = new LinkedHashMap<>();
        englishElements.put("source manifest", "<ul\\b(?=[^>]*\\bclass=\"source-manifest\")(?=[^>]*\\blang=\"en\")[^>]*>");
        englishElements.put("chart-scope findings", "<p\\b(?=[^>]*\\blang=\"en\")[^>]*>\\s*<carlos:encode value=\"\\$\\{finding\\.message\\}\"");
        englishElements.put("section titles", "<h3\\b(?=[^>]*\\blang=\"en\")[^>]*>\\s*<carlos:encode value=\"\\$\\{section\\.title\\}\"");
        englishElements.put("claim text", "<span\\b(?=[^>]*\\bclass=\"claim-text\")(?=[^>]*\\blang=\"en\")[^>]*>");
        englishElements.put("ledger rows", "<tbody\\b(?=[^>]*\\blang=\"en\")[^>]*>\\s*<c:forEach items=\"\\$\\{summaryArtifact\\.fact_ledger\\}\"");
        englishElements.put("coverage reasons", "<p\\b(?=[^>]*\\blang=\"en\")[^>]*>\\s*<carlos:encode value=\"\\$\\{entry\\.reason\\}\"");
        englishElements.put("validation findings", "<article\\b(?=[^>]*\\bclass=\"finding\\b)(?=[^>]*\\blang=\"en\")[^>]*>");
        englishElements.put("artifact footer", "<footer\\b(?=[^>]*\\bclass=\"artifact-footer\")(?=[^>]*\\blang=\"en\")[^>]*>");
        englishElements.put("generation error", "<p\\b(?=[^>]*\\bclass=\"generation-error\\b)(?=[^>]*\\blang=\"en\")[^>]*>");
        englishElements.put("source titles", "<summary\\b(?=[^>]*\\blang=\"en\")[^>]*>\\s*<strong>\\s*<carlos:encode value=\"\\$\\{source\\.title\\}\"");
        englishElements.put("source text", "<p\\b(?=[^>]*\\blang=\"en\")[^>]*>\\s*<carlos:encode value=\"\\$\\{source\\.text\\}\"");
        englishElements.put("selected statement", "\\bid=\"selected-statement\"[^>]*>(?:(?!</div>).)*?<p\\b(?=[^>]*\\blang=\"en\")[^>]*>");

        englishElements.forEach((element, regex) -> assertThat(jsp).as("%s carry lang=\"en\"", element)
                .containsPattern(Pattern.compile(regex, Pattern.DOTALL)));
    }

    @Test
    @DisplayName("should define every page key with a translation in every shipped locale")
    void shouldDefineEveryPageKey_inEveryShippedLocale() throws IOException {
        Set<String> keys = pageKeys();
        assertThat(keys).hasSizeGreaterThan(50).allSatisfy(key -> assertThat(key).startsWith(PREFIX));
        Properties english = bundle("en");

        for (String locale : LOCALES) {
            Properties bundle = bundle(locale);
            List<String> missing = new ArrayList<>();
            List<String> untranslated = new ArrayList<>();
            for (String key : keys) {
                String value = bundle.getProperty(key);
                if (value == null || value.isBlank()) missing.add(key);
                else if (!"en".equals(locale) && value.equals(english.getProperty(key))) untranslated.add(key);
            }
            assertThat(missing).as("oscarResources_%s.properties should define every page key", locale).isEmpty();
            // A few labels are the same word in English and French ("Sources", "Validation").
            assertThat(untranslated).as("oscarResources_%s.properties should translate the page", locale)
                    .hasSizeLessThan(keys.size() / 10);
        }
    }

    @Test
    @DisplayName("should keep script placeholders after MessageFormat quoting")
    void shouldKeepScriptPlaceholders_afterMessageFormatQuoting() throws IOException {
        for (String locale : LOCALES) {
            Properties bundle = bundle(locale);
            for (String key : List.of("msgSourcePosition", "msgLinkedSources", "msgEvidenceWidth", "labelSourcesInBundle")) {
                String pattern = bundle.getProperty(PREFIX + key);
                // Formatting a placeholder with itself is the identity, apart from MessageFormat quoting.
                String formatted = new MessageFormat(pattern).format(new Object[] {"{0}", "{1}"});
                assertThat(formatted).as("%s in %s", key, locale).isEqualTo(pattern.replace("''", "'"));
            }
        }
    }

    private static Set<String> pageKeys() throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        for (Path path : List.of(JSP, ENCOUNTER_HEADER)) {
            Matcher matcher = FMT_KEY.matcher(read(path));
            while (matcher.find()) {
                if (matcher.group(1).startsWith(PREFIX) || path.equals(JSP)) keys.add(matcher.group(1));
            }
        }
        return keys;
    }

    private static Properties bundle(String locale) throws IOException {
        String resource = "/oscarResources_" + locale + ".properties";
        try (InputStream stream = AiClinicalSummaryPrototypeJspRegressionTest.class.getResourceAsStream(resource)) {
            assertThat(stream).as("resource %s must exist on the classpath", resource).isNotNull();
            Properties properties = new Properties();
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            return properties;
        }
    }

    private static String read(Path relativePath) throws IOException {
        Path current = Path.of(System.getProperty("basedir", System.getProperty("user.dir"))).toAbsolutePath().normalize();
        for (int checkedParents = 0; current != null && checkedParents < 6; checkedParents++) {
            Path candidate = current.resolve(relativePath).normalize();
            if (Files.isRegularFile(candidate)) return Files.readString(candidate, StandardCharsets.UTF_8);
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate " + relativePath);
    }
}
