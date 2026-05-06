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
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the high-traffic clinical JSP i18n rollout.
 *
 * @since 2026-05-05
 */
@DisplayName("Target clinical JSP i18n coverage")
@Tag("unit")
@Tag("i18n")
class TargetClinicalJspI18nTest {

    private static final String[] LOCALES = {"en", "fr", "es", "pt_BR", "pl"};
    private static final Pattern FMT_KEY_PATTERN = Pattern.compile("<fmt:message\\b[^>]*\\bkey=[\"']([^\"']+)[\"']");
    private static final List<Path> TARGET_ROOTS = List.of(
            Path.of("src/main/webapp/demographic"),
            Path.of("src/main/webapp/WEB-INF/jsp/demographic"),
            Path.of("src/main/webapp/appointment"),
            Path.of("src/main/webapp/WEB-INF/jsp/appointment"),
            Path.of("src/main/webapp/billing/CA/ON"),
            Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/ON"),
            Path.of("src/main/webapp/billing/CA/BC"),
            Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC"),
            Path.of("src/main/webapp/oscarRx"),
            Path.of("src/main/webapp/WEB-INF/jsp/rx")
    );

    @Test
    @DisplayName("should declare oscarResources bundle in target clinical JSPs")
    void shouldDeclareOscarResourcesBundle_inTargetClinicalJsps() throws IOException {
        List<Path> missingBundle = targetJsps()
                .filter(path -> !read(path).contains("<fmt:setBundle basename=\"oscarResources\"/>"))
                .toList();

        assertThat(missingBundle)
                .as("target clinical JSPs should initialize oscarResources for fmt:message lookups")
                .isEmpty();
    }

    @Test
    @DisplayName("should resolve appointment type list keys in every shipped locale")
    void shouldResolveAppointmentTypeListKeys_inEveryLocale() throws IOException {
        List<String> keys = fmtMessageKeys(Path.of("src/main/webapp/WEB-INF/jsp/appointment/appointmentTypeList.jsp"));

        for (String locale : LOCALES) {
            Properties bundle = loadBundle(locale);
            List<String> missing = keys.stream()
                    .filter(key -> !bundle.containsKey(key))
                    .toList();

            assertThat(missing)
                    .as("oscarResources_%s.properties should define appointment type list labels", locale)
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("should use translated appointment type list values in every shipped locale")
    void shouldUseTranslatedAppointmentTypeListValues_inEveryLocale() throws IOException {
        List<String> keys = fmtMessageKeys(Path.of("src/main/webapp/WEB-INF/jsp/appointment/appointmentTypeList.jsp"));

        for (String locale : LOCALES) {
            Properties bundle = loadBundle(locale);
            List<String> englishPlaceholders = keys.stream()
                    .filter(key -> bundle.getProperty(key, "").startsWith("[EN] "))
                    .toList();

            assertThat(englishPlaceholders)
                    .as("oscarResources_%s.properties should use translations, not [EN] placeholders", locale)
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("should keep appointment type list key values free of trailing colons")
    void shouldKeepAppointmentTypeListKeyValues_freeOfTrailingColons() throws IOException {
        Properties bundle = loadBundle("en");

        List<String> keysWithTrailingColons = bundle.stringPropertyNames().stream()
                .filter(key -> key.startsWith("appointment.appointmentTypeList."))
                .filter(key -> bundle.getProperty(key).endsWith(":"))
                .toList();

        assertThat(keysWithTrailingColons)
                .as("punctuation should live in JSP label markup, not translatable values")
                .isEmpty();
    }

    @Test
    @DisplayName("should not retain externalized appointment type list text")
    void shouldNotRetainExternalizedAppointmentTypeListText_inJsp() throws IOException {
        String content = read(Path.of("src/main/webapp/WEB-INF/jsp/appointment/appointmentTypeList.jsp"));

        assertThat(content)
                .doesNotContain("APPOINTMENT TYPES")
                .doesNotContain("EDIT APPOINTMENT TYPE")
                .doesNotContain("Please enter appointment type name")
                .doesNotContain("Please enter value in Names field")
                .doesNotContain("Type will be deleted! Are you sure?");
    }

    private List<String> fmtMessageKeys(Path jsp) throws IOException {
        Matcher matcher = FMT_KEY_PATTERN.matcher(read(jsp));
        Stream.Builder<String> keys = Stream.builder();
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys.build().distinct().toList();
    }

    private Stream<Path> targetJsps() throws IOException {
        Stream.Builder<Path> paths = Stream.builder();
        for (Path root : TARGET_ROOTS) {
            if (Files.isDirectory(root)) {
                try (Stream<Path> walk = Files.walk(root)) {
                    walk.filter(path -> path.toString().endsWith(".jsp"))
                            .forEach(paths);
                }
            }
        }
        Path billingRoot = Path.of("src/main/webapp/billing");
        if (Files.isDirectory(billingRoot)) {
            try (Stream<Path> walk = Files.list(billingRoot)) {
                walk.filter(path -> path.toString().endsWith(".jsp"))
                        .forEach(paths);
            }
        }
        return paths.build().distinct();
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private Properties loadBundle(String locale) throws IOException {
        String resource = "/oscarResources_" + locale + ".properties";
        try (InputStream is = TargetClinicalJspI18nTest.class.getResourceAsStream(resource)) {
            assertThat(is)
                    .as("resource %s must exist on the classpath", resource)
                    .isNotNull();
            Properties properties = new Properties();
            properties.load(is);
            return properties;
        }
    }
}
