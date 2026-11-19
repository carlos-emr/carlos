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
package io.github.carlos_emr.carlos.eform.actions;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The 409 a refused eForm submission answers with (AddEForm2Action, {@code getText("eform.submitUnavailable")}).
 * Struts formats every message through {@link MessageFormat}, which drops a single quote, so the bundles must
 * escape the apostrophe in "patient's" for it to reach the clinician.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("eForm submit-unavailable message")
class EFormSubmitUnavailableMessageUnitTest {

    private static final String KEY = "eform.submitUnavailable";

    private static String raw(String locale) throws IOException {
        Properties bundle = new Properties();
        try (Reader in = Files.newBufferedReader(
                Path.of("src/main/resources/oscarResources_" + locale + ".properties"), StandardCharsets.UTF_8)) {
            bundle.load(in);
        }
        String message = bundle.getProperty(KEY);
        assertThat(message).as("%s in %s", KEY, locale).isNotBlank();
        return message;
    }

    private static String formatted(String locale, String message) {
        return new MessageFormat(message, Locale.forLanguageTag(locale.replace('_', '-'))).format(new Object[0]);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"en", "fr", "es", "pl", "pt_BR"})
    @DisplayName("should show every apostrophe the bundle escapes, and no other change, once formatted")
    void shouldShowBundleText_whenFormattedLikeGetText(String locale) throws IOException {
        String message = raw(locale);

        // Equal only when every single quote in the value is escaped as '' (a lone one would start a quoted section).
        assertThat(formatted(locale, message)).isEqualTo(message.replace("''", "'"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"en", "fr", "es", "pl", "pt_BR"})
    @DisplayName("should tell the clinician to check the patient's eForms")
    void shouldKeepApostrophe_inRecoveryGuidance(String locale) throws IOException {
        assertThat(formatted(locale, raw(locale))).contains("Check the patient's eForms");
    }
}
