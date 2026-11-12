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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The bundle text of the BC bill form's Continue refusals, now that the form shows them (issue #4864 item 10,
 * finding 432). The two patient-management messages had no text in any bundle, so they would have shown their
 * keys, and Polish wrote its letters as HTML entities, which the form, encoding each message once, would have
 * shown as entities.
 */
@Tag("unit")
@Tag("billing")
@DisplayName("BC bill form Continue refusal bundle text")
class BillingRefusalBundleTextUnitTest {

    private static final String PREFIX = "oscar.billing.CA.BC.billingBC.error.";
    private static final List<String> LOCALES = List.of("en", "fr", "es", "pl", "pt_BR");

    /** Every message Continue formats with getText (BillingCreateBilling2Action). */
    private static final List<String> KEYS = List.of("startTimeNeeded", "startTimeandEndNeeded", "invaliddxcode",
            "invalidAge", "invalidSex", "invalidsvccode", "noMore00120", "codeLastBilled", "codeNeverBilled", "codeCond",
            "patientManagementCodesDayUsed", "patientManagementCodesYearUsed");

    private static Properties bundle(String locale) throws IOException {
        Properties bundle = new Properties();
        try (var reader = Files.newBufferedReader(
                Path.of("src/main/resources/oscarResources_" + locale + ".properties"), StandardCharsets.UTF_8)) {
            bundle.load(reader);
        }
        return bundle;
    }

    static Stream<Arguments> patientManagementMessages() {
        return LOCALES.stream().flatMap(locale -> Stream.of("patientManagementCodesDayUsed", "patientManagementCodesYearUsed")
                .map(key -> Arguments.of(locale, key)));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("patientManagementMessages")
    @DisplayName("should have each patient-management message, every value filled in")
    void shouldFillEveryValue_forEachPatientManagementMessage(String locale, String key) throws IOException {
        String pattern = bundle(locale).getProperty(PREFIX + key);
        assertThat(pattern).as("%s %s", locale, key).isNotBlank();

        // Formatted as Continue formats it: the service code, the units on this bill, the units left.
        String formatted = MessageFormat.format(pattern, "14015", "3.0", "1.0");

        assertThat(formatted).as("%s %s", locale, key).contains("14015", "3.0", "1.0").doesNotContain("{");
        assertThat(formatted).as("%s %s keeps its apostrophes", locale, key)
                .isEqualTo(pattern.replace("''", "'").replace("{0}", "14015").replace("{1}", "3.0").replace("{2}", "1.0"));
    }

    @Test
    @DisplayName("should separate the code from the days in the Polish code-last-billed message")
    void shouldSeparateTheCode_inPolishCodeLastBilled() throws IOException {
        // pl defines this key twice; the later definition, the one shown, had a stray letter where its ":" belongs.
        String formatted = MessageFormat.format(bundle("pl").getProperty(PREFIX + "codeLastBilled"), "400", "14015");

        assertThat(formatted).isEqualTo("400 dni min\u0119\u0142o od ostatniego rozliczenia kodu us\u0142ugi: 14015<br/>");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"en", "fr", "es", "pl", "pt_BR"})
    @DisplayName("should write each refusal message's letters as characters, not HTML entities")
    void shouldWriteLettersAsCharacters_inEachRefusalMessage(String locale) throws IOException {
        Properties bundle = bundle(locale);
        for (String key : KEYS) {
            assertThat(bundle.getProperty(PREFIX + key)).as("%s %s", locale, key).isNotNull().doesNotContain("&#");
        }
    }
}
