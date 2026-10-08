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
package io.github.carlos_emr.carlos.email.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("email")
@DisplayName("EmailFailureMessage")
class EmailFailureMessageUnitTest {

    private static final List<String> KEYS =
            List.of(EmailFailureMessage.EFORM_ATTACHMENTS_KEY, EmailFailureMessage.RESEND_ATTACHMENTS_KEY);
    private static final List<String> LOCALES = List.of("en", "fr", "es", "pl", "pt_BR");

    @Test
    @DisplayName("should give a fresh random reference for each failure")
    void shouldGiveFreshReference_forEachFailure() {
        String first = EmailFailureMessage.newReference();

        assertThat(first).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(EmailFailureMessage.newReference()).isNotEqualTo(first);
    }

    @Test
    @DisplayName("should word each message in the reader's language and end it with the reference")
    void shouldWordMessage_inReadersLanguage_withReference() {
        assertThat(EmailFailureMessage.format(Locale.ENGLISH, EmailFailureMessage.EFORM_ATTACHMENTS_KEY, "FAKE-ref"))
                .isEqualTo("This eForm could not be emailed because an attachment could not be prepared. "
                        + "Please try again. Reference for your administrator: FAKE-ref");
        assertThat(EmailFailureMessage.format(Locale.FRENCH, EmailFailureMessage.RESEND_ATTACHMENTS_KEY, "FAKE-ref"))
                .startsWith("Ce courriel d\u00e9j\u00e0 envoy\u00e9 ne peut pas \u00eatre rouvert")
                .endsWith("administrateur : FAKE-ref");
        assertThat(EmailFailureMessage.format(null, "no.such.key.for.this.test", "FAKE-ref"))
                .isEqualTo("The email attachments could not be prepared. Reference for your administrator: FAKE-ref");
    }

    @Test
    @DisplayName("should translate both messages in every shipped locale, keeping the reference placeholder")
    void shouldTranslateMessages_inEveryLocale() throws IOException {
        Properties english = loadBundle("en");
        for (String locale : LOCALES) {
            Properties bundle = loadBundle(locale);
            for (String key : KEYS) {
                String text = bundle.getProperty(key);
                assertThat(text).as("oscarResources_%s.properties defines %s", locale, key).isNotBlank();
                assertThat(text).as("%s %s keeps {0}", locale, key).contains("{0}").doesNotContain("{1}");
                // An ASCII apostrophe would be eaten by MessageFormat.
                assertThat(text).as("%s %s has no ASCII apostrophe", locale, key).doesNotContain("'");
                if (!"en".equals(locale)) {
                    assertThat(text).as("oscarResources_%s.properties translates %s", locale, key)
                            .isNotEqualTo(english.getProperty(key));
                }
                // Formats in that locale and ends with the reference (catches unbalanced braces).
                assertThat(EmailFailureMessage.format(Locale.forLanguageTag(locale.replace('_', '-')), key, "FAKE-ref"))
                        .as("%s %s formatted", locale, key).endsWith(" FAKE-ref").doesNotContain("{");
            }
        }
    }

    private static Properties loadBundle(String locale) throws IOException {
        Properties bundle = new Properties();
        Path bundlePath = Path.of("src/main/resources/oscarResources_" + locale + ".properties");
        try (var reader = Files.newBufferedReader(bundlePath, StandardCharsets.UTF_8)) {
            bundle.load(reader);
        }
        return bundle;
    }
}
