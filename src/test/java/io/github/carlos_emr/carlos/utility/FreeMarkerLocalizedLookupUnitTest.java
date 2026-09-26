/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.utility;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins the CVE-2026-84939 fix without disabling localized template rendering. */
@Tag("unit")
@Tag("security")
class FreeMarkerLocalizedLookupUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @ValueSource(strings = {"/../../private", "\\..\\private", "%2f..%2fprivate"})
    void shouldKeepLocaleInputOutOfTemplatePaths_whenLocaleContainsSeparators(String fragment) throws Exception {
        // Given a loader that records the actual storage names, including unsuccessful lookups.
        List<String> requested = new ArrayList<>();
        StringTemplateLoader loader = new StringTemplateLoader() {
            @Override
            public Object findTemplateSource(String name) {
                requested.add(name);
                return super.findTemplateSource(name);
            }
        };
        loader.putTemplate("report.ftl", "default report");
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_34);
        configuration.setTemplateLoader(loader);

        // When malformed input reaches any of the language, country or variant components.
        for (Locale locale : List.of(Locale.of(fragment), Locale.of("en", fragment),
                Locale.of("en", "CA", fragment))) {
            assertThat(configuration.getTemplate("report.ftl", locale).getSourceName()).isEqualTo("report.ftl");
        }

        // Then no directory separators or encoded path fragments reach the template loader.
        assertThat(requested).isNotEmpty().allSatisfy(name ->
                assertThat(name).doesNotContain("/", "\\", "%"));
    }

    @Test
    void shouldRenderLocalizedTemplate_whenLocaleIsValid() throws Exception {
        // Given the pre-update compatibility setting used by existing consumers.
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_34);
        StringTemplateLoader loader = new StringTemplateLoader();
        loader.putTemplate("report.ftl", "Default ${label}");
        loader.putTemplate("report_fr_CA.ftl", "Rapport ${label}");
        configuration.setTemplateLoader(loader);
        StringWriter output = new StringWriter();

        // When a supported locale renders its template.
        configuration.getTemplate("report.ftl", Locale.CANADA_FRENCH).process(Map.of("label", "fixture"), output);

        // Then localization and ordinary template expressions remain functional.
        assertThat(output.toString()).isEqualTo("Rapport fixture");
    }
}
