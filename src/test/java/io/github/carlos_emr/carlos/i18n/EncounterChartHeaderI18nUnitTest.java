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
package io.github.carlos_emr.carlos.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * i18n regression coverage for the encounter chart header and its note-template search.
 *
 * <p>Background: phc007 reported that the chart header, the "Template Search" legend and both
 * template-search placeholders stayed in English on a translated chart. Two distinct causes:
 * literal English text that was never tagged for translation, and Java-rendered header labels
 * resolved against the <em>server's</em> locale rather than the browser's.</p>
 *
 * <p>This test pins the first cause (every user-facing string on these pages goes through
 * {@code <fmt:message>}, and every key it names exists in every shipped bundle). The second is
 * pinned by {@code DemographicStandardIdentificationHtmlUnitTest} and
 * {@code LocaleUtilsResolveBundleLocaleUnitTest}.</p>
 *
 * @since 2026-09-20
 */
@DisplayName("Encounter chart header i18n coverage")
@Tag("unit")
@Tag("i18n")
class EncounterChartHeaderI18nUnitTest {

    private static final String[] LOCALES = {"en", "fr", "es", "pt_BR", "pl"};

    private static final Path MODULE_ROOT = Path.of(System.getProperty("basedir", "")).toAbsolutePath();

    private static final Path HEADER_JSP =
            MODULE_ROOT.resolve("src/main/webapp/WEB-INF/jsp/casemgmt/newEncounterHeader.jsp");
    private static final Path CHART_NOTES_JSP =
            MODULE_ROOT.resolve("src/main/webapp/WEB-INF/jsp/casemgmt/ChartNotes.jsp");
    private static final Path LAYOUT_JS_JSP =
            MODULE_ROOT.resolve("src/main/webapp/WEB-INF/jsp/casemgmt/newEncounterLayout.js.jsp");

    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern JSP_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);
    private static final Pattern FMT_KEY = Pattern.compile(
            "<fmt:message\\b[^>]*\\bkey\\s*=\\s*(['\"])([^'\"${}]+)\\1", Pattern.DOTALL);

    /** Strings the report named explicitly; each must now reach the page through the bundle. */
    private static final String[] REPORTED_KEYS = {
            "encounter.templateSearch.legend",
            "encounter.templateSearch.namePlaceholder",
            "encounter.templateSearch.overlayPlaceholder",
            "global.copiedToClipboard",
            "global.viewAppointmentHistory"
    };

    // Only these newly introduced strings are pending verified translations.
    // Equality with English is not a general test for translation correctness.
    private static final Set<String> PENDING_TRANSLATION_KEYS = Set.of(
            "encounter.templateSearch.legend",
            "encounter.templateSearch.namePlaceholder",
            "encounter.templateSearch.overlayPlaceholder",
            "global.copiedToClipboard");

    @Test
    @DisplayName("should resolve every chart header and template search key in every shipped locale")
    void shouldResolveEveryChartHeaderKey_inEveryLocale() throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        for (Path jsp : List.of(HEADER_JSP, CHART_NOTES_JSP, LAYOUT_JS_JSP)) {
            keys.addAll(fmtMessageKeys(jsp));
        }
        keys.addAll(List.of(REPORTED_KEYS));
        assertThat(keys).as("the chart header JSPs should reference i18n keys").isNotEmpty();

        for (String locale : LOCALES) {
            Properties bundle = loadBundle(locale);
            List<String> missing = new ArrayList<>();
            for (String key : keys) {
                if (!bundle.containsKey(key)) {
                    missing.add(key);
                }
            }
            assertThat(missing)
                    .as("oscarResources_%s.properties is missing chart header keys; "
                            + "they render as ???key??? for a %s browser", locale, locale)
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("should translate or explicitly mark every reported key in each non-English bundle")
    void shouldTranslateOrMarkReportedKeys_forEveryNonEnglishLocale() throws IOException {
        Properties english = loadBundle("en");
        for (String locale : new String[] {"fr", "es", "pt_BR", "pl"}) {
            Properties bundle = loadBundle(locale);
            for (String key : REPORTED_KEYS) {
                assertThat(bundle.getProperty(key))
                        .as("oscarResources_%s.properties should translate %s", locale, key)
                        .isNotBlank()
                        .doesNotStartWith("[EN] ");
                if (PENDING_TRANSLATION_KEYS.contains(key)) {
                    assertThat(bundle.getProperty(key)).isEqualTo(english.getProperty(key));
                    String source = Files.readString(MODULE_ROOT.resolve(
                            "src/main/resources/oscarResources_" + locale + ".properties"));
                    assertThat(Pattern.compile("(?m)^\\h*# TODO: translate\\h*\\R(?:\\h*\\R)*\\h*" + Pattern.quote(key) + "\\h*[=:]")
                            .matcher(source).find())
                            .as("English placeholder %s in %s needs a translation marker", key, locale)
                            .isTrue();
                }
            }
        }
    }

    @Test
    @DisplayName("should keep the reported literals out of the chart header markup")
    void shouldKeepReportedLiterals_outOfChartHeaderMarkup() throws IOException {
        assertThat(stripComments(read(CHART_NOTES_JSP)))
                .as("the Template Search legend and placeholder must come from the bundle")
                .doesNotContain("<legend>Template Search</legend>")
                .doesNotContain("placeholder=\"template name\"");

        assertThat(stripComments(read(LAYOUT_JS_JSP)))
                .as("the template shortcut overlay placeholder must come from the bundle")
                .doesNotContain("'Search templates");

        assertThat(stripComments(read(HEADER_JSP)))
                .as("the copy-to-clipboard confirmation must come from the bundle")
                .doesNotContain("'Copied!'");
    }

    @Test
    @DisplayName("should hand the browser locale to the Java-rendered identity block")
    void shouldHandBrowserLocale_toJavaRenderedIdentityBlock() throws IOException {
        String header = stripComments(read(HEADER_JSP));

        // The header is half JSP tags and half a Java-built HTML string. If the Java half is
        // not given the request's locale it silently falls back to the server's, which is the
        // "header is not i18n even though the page is" symptom in the report.
        assertThat(header)
                .contains("LocaleUtils.resolveBundleLocale(request)")
                .contains("getStandardIdentificationHtml(request.getContextPath(), browserLocale)")
                .doesNotContain("LocaleContextHolder");
        assertThat(stripComments(read(MODULE_ROOT.resolve("src/main/webapp/WEB-INF/jsp/demographic/edit-view.jsp"))))
                .contains("getRosterStatusDisplay(LocaleUtils.resolveBundleLocale(request))")
                .doesNotContain("getRosterStatusDisplay(request.getLocale())");
    }

    @Test
    @DisplayName("should set the negotiated JSTL locale before loading each translated fragment's bundle")
    void shouldShareLocale_betweenJavaAndJstl() throws IOException {
        for (Path jsp : List.of(HEADER_JSP, CHART_NOTES_JSP, LAYOUT_JS_JSP,
                MODULE_ROOT.resolve("src/main/webapp/WEB-INF/jsp/demographic/edit-view.jsp"))) {
            String source = stripComments(read(jsp));
            int locale = source.indexOf("<fmt:setLocale value=\"<%= LocaleUtils.resolveBundleLocale(request) %>\"/>");
            int bundle = source.indexOf("<fmt:setBundle");
            assertThat(locale).as("negotiated locale in %s", jsp).isGreaterThanOrEqualTo(0);
            assertThat(bundle).as("bundle must load after the locale in %s", jsp).isGreaterThan(locale);
        }
    }

    private static List<String> fmtMessageKeys(Path jsp) throws IOException {
        assertThat(Files.exists(jsp)).as("%s should exist; update this test if it was renamed", jsp).isTrue();
        List<String> keys = new ArrayList<>();
        Matcher m = FMT_KEY.matcher(stripComments(read(jsp)));
        while (m.find()) {
            keys.add(m.group(2));
        }
        return keys;
    }

    private static String read(Path path) throws IOException {
        assertThat(Files.isRegularFile(path)).as("JSP source %s must exist", path).isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String stripComments(String content) {
        return JSP_COMMENT.matcher(HTML_COMMENT.matcher(content).replaceAll("")).replaceAll("");
    }

    private static Properties loadBundle(String locale) throws IOException {
        String resource = "/oscarResources_" + locale + ".properties";
        try (InputStream is = EncounterChartHeaderI18nUnitTest.class.getResourceAsStream(resource)) {
            assertThat(is).as("resource %s must exist on the classpath", resource).isNotNull();
            Properties p = new Properties();
            p.load(is);
            return p;
        }
    }
}
