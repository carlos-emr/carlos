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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression tests guarding against malformed entries in the {@code oscarResources_*.properties}
 * bundles.
 *
 * <p>Background: a malformed {@code &#92;uxxxx} escape in {@code oscarResources_pl.properties}
 * caused {@link Properties#load(InputStream)} to throw
 * {@link IllegalArgumentException}. When a Tomcat JVM was started with a Polish default locale
 * (e.g. {@code -Duser.language=pl}), the exception propagated out of
 * {@link ResourceBundle#getBundle(String, Locale)} calls in JSPs such as
 * {@code demographiceditdemographic.jsp}, producing an HTTP 500 for non-English users.</p>
 *
 * @since 2026-04-20
 */
@DisplayName("oscarResources bundle parse regression")
@Tag("unit")
@Tag("i18n")
class OscarResourcesBundleParseTest {

    private static final Path RESOURCE_DIR = Path.of("src/main/resources");
    private static final String[] LOCALES = {"en", "fr", "es", "pt_BR", "pl"};
    private static final String[] LOGIN_ERROR_KEYS = {
            "login.errorApplicationError",
            "login.errorAccountLocked",
            "login.errorUnableToProcess",
            "login.errorSecurityRecordMissing",
            "login.errorAccountInactive",
            "login.errorAccountExpired",
            "login.errorInvalidCredentials",
            "login.errorResetStaging",
            "login.errorResetPersistence"
    };

    @Test
    @DisplayName("should parse every oscarResources locale file without IllegalArgumentException")
    void shouldParseAllLocaleBundles_withoutException() {
        for (String locale : LOCALES) {
            String resource = "/oscarResources_" + locale + ".properties";
            assertThatCode(() -> {
                try (InputStream is = OscarResourcesBundleParseTest.class.getResourceAsStream(resource)) {
                    assertThat(is).as("resource %s must exist on the classpath", resource).isNotNull();
                    Properties p = new Properties();
                    p.load(is);
                    assertThat(p).as("%s should contain at least one key", resource).isNotEmpty();
                }
            }).as("Properties.load must succeed for %s (malformed \\uxxxx escapes cause 500s)",
                    resource).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("should load Polish bundle even when JVM default locale is Polish")
    void shouldLoadPolishBundle_whenDefaultLocaleIsPolish() {
        Locale originalDefault = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("pl"));
            ResourceBundle.clearCache();
            ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", new Locale("pl"));
            assertThat(bundle.getLocale().getLanguage()).isEqualTo("pl");
            // sanity check on a previously-broken key
            assertThat(bundle.getString("form.bcnewborn.retroplacentalClot"))
                    .isNotBlank();
        } finally {
            Locale.setDefault(originalDefault);
            ResourceBundle.clearCache();
        }
    }

    @Test
    @DisplayName("should define login error keys in every shipped locale bundle")
    void shouldDefineLoginErrorKeys_inEveryLocale() throws Exception {
        for (String locale : LOCALES) {
            Properties bundle = loadBundle(locale);
            for (String key : LOGIN_ERROR_KEYS) {
                assertThat(bundle.getProperty(key))
                        .as("oscarResources_%s.properties should define %s", locale, key)
                        .isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("should define every English key in every shipped locale bundle")
    void shouldDefineEnglishKeys_inEveryLocale() throws Exception {
        Properties english = loadBundle("en");
        for (String locale : LOCALES) {
            Properties bundle = loadBundle(locale);

            assertThat(bundle.stringPropertyNames())
                    .as("oscarResources_%s.properties should define every key from English", locale)
                    .containsAll(english.stringPropertyNames());
        }
    }

    @Test
    @DisplayName("should keep properties source files ASCII-only")
    void shouldKeepPropertiesSourceFiles_asciiOnly() throws Exception {
        for (String locale : LOCALES) {
            Path bundlePath = RESOURCE_DIR.resolve("oscarResources_" + locale + ".properties");
            byte[] bytes = Files.readAllBytes(bundlePath);
            List<String> nonAsciiBytes = new ArrayList<>();
            int line = 1;
            for (byte value : bytes) {
                int unsigned = Byte.toUnsignedInt(value);
                if (unsigned == '\n') {
                    line++;
                    continue;
                }
                if (unsigned > 0x7F) {
                    nonAsciiBytes.add(String.format(Locale.ROOT, "line %d byte 0x%02X", line, unsigned));
                }
            }

            assertThat(nonAsciiBytes)
                    .as("%s must use \\uXXXX escapes so Properties.load(InputStream) decodes consistently",
                            bundlePath)
                    .isEmpty();
        }
    }

    private Properties loadBundle(String locale) throws Exception {
        String resource = "/oscarResources_" + locale + ".properties";
        try (InputStream is = OscarResourcesBundleParseTest.class.getResourceAsStream(resource)) {
            assertThat(is).as("resource %s must exist on the classpath", resource).isNotNull();
            Properties p = new Properties();
            p.load(is);
            return p;
        }
    }
}
