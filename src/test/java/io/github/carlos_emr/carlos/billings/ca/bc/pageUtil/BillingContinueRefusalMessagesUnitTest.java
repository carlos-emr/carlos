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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The BC bill form's Continue refusal messages ({@link BillingCreateBilling2Action}), as their bundles format them
 * (issue #4946). Struts formats each with {@link MessageFormat}, where a lone apostrophe quotes the rest of the
 * message: the apostrophe is dropped and every value after it is printed as {@code {1}}, never filled in.
 *
 * <p>Each message, in each locale, must come out as written: every {@code ''} as one apostrophe and every
 * placeholder filled. English {@code invaliddxcode} and {@code invalidsvccode} are #4943's, which checks them in
 * {@code ServiceCodeAssocEditorPageUnitTest}.</p>
 */
@Tag("unit")
@Tag("billing")
@DisplayName("BC bill form Continue refusal messages")
class BillingContinueRefusalMessagesUnitTest {

    private static final List<String> LOCALES = List.of("en", "fr", "es", "pl", "pt_BR");

    /** Every message Continue formats with getText (BillingCreateBilling2Action). */
    private static final List<String> KEYS = List.of("startTimeNeeded", "startTimeandEndNeeded", "invaliddxcode",
            "invalidAge", "invalidSex", "invalidsvccode", "noMore00120", "codeLastBilled", "codeNeverBilled", "codeCond");

    private static final Object[] VALUES = {"VALUE0", "VALUE1", "VALUE2"};

    static Stream<Arguments> messages() {
        return LOCALES.stream().flatMap(locale -> KEYS.stream()
                .filter(key -> !(locale.equals("en") && (key.equals("invaliddxcode") || key.equals("invalidsvccode"))))
                .map(key -> Arguments.of(locale, key)));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("messages")
    @DisplayName("should format each refusal message as written, every value filled in")
    void shouldFormatAsWritten_forEachRefusalMessage(String locale, String key) throws IOException {
        Properties bundle = new Properties();
        try (var reader = Files.newBufferedReader(
                Path.of("src/main/resources/oscarResources_" + locale + ".properties"), StandardCharsets.UTF_8)) {
            bundle.load(reader);
        }
        String pattern = bundle.getProperty("oscar.billing.CA.BC.billingBC.error." + key);
        assertThat(pattern).as("%s %s", locale, key).isNotNull();

        // As written: a doubled apostrophe is one apostrophe, and each placeholder its value.
        String written = pattern.replace("''", "'");
        for (int i = 0; i < VALUES.length; i++) {
            written = written.replace("{" + i + "}", (String) VALUES[i]);
        }

        assertThat(MessageFormat.format(pattern, VALUES)).as("%s %s", locale, key).isEqualTo(written);
    }
}
