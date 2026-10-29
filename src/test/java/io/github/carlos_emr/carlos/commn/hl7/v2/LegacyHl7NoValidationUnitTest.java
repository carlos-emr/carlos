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
package io.github.carlos_emr.carlos.commn.hl7.v2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.util.Terser;

/**
 * Pins the whitespace handling CARLOS lab parsing had under HAPI HL7 v2 1.0.1 (issue #5414).
 *
 * <p>Expected values were recorded by parsing the same OBX segment with HAPI 1.0.1 and its
 * {@code NoValidation} context, which left-trimmed FT, ST and TX values and applied no format
 * checks. HAPI 2.6.0's {@code NoValidation} right-trims TX instead, which would move the leading
 * indentation of text lab results and drop their trailing padding.</p>
 */
@Tag("unit")
@Tag("fast")
@Tag("hl7")
@DisplayName("LegacyHl7NoValidation")
class LegacyHl7NoValidationUnitTest {

    private static String parsedObx5(String valueType, String value) throws Exception {
        PipeParser parser = new PipeParser();
        parser.setValidationContext(new LegacyHl7NoValidation());
        Message message = parser.parse("MSH|^~\\&|LAB|FAC|CARLOS|CARLOS|20261029120000||ORU^R01|C1|P|2.3\r"
                + "PID|1||9999999901||FAKE^PATIENT||19800101|F\r"
                + "OBR|1||ACC1|TEST^Test|||20261029\r"
                + "OBX|1|" + valueType + "|CODE^Name||" + value + "|u|0-1|H|||F\r");
        return new Terser(message).get("/.OBX-5");
    }

    @ParameterizedTest(name = "{0} [{1}] -> [{2}]")
    @CsvSource(delimiter = '|', quoteCharacter = '\'', value = {
            "TX | '  lead'   | 'lead'",
            "TX | 'trail  '  | 'trail  '",
            "TX | '  both  ' | 'both  '",
            "ST | '  lead'   | 'lead'",
            "ST | 'trail  '  | 'trail  '",
            "FT | '  both  ' | 'both  '"})
    @DisplayName("should left-trim text values like HAPI 1.0.1 for each text type")
    void shouldLeftTrimTextValuesLikeHapi101_forEachTextType(String type, String value, String expected) throws Exception {
        assertThat(parsedObx5(type, value)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} [{1}]")
    @CsvSource(delimiter = '|', value = {
            "NM | <5",
            "NM | 1,000",
            "NM | abc",
            "DT | 2026-10-29",
            "TM | 25:99",
            "TN | not a phone",
            "ID | x"})
    @DisplayName("should keep values the default rules would reject for each checked type")
    void shouldKeepValuesDefaultRulesWouldReject_forEachCheckedType(String type, String value) throws Exception {
        assertThat(parsedObx5(type, value)).isEqualTo(value);
    }
}
