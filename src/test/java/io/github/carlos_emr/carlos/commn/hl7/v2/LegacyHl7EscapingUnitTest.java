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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ca.uhn.hl7v2.parser.EncodingCharacters;

/**
 * Pins {@link LegacyHl7Escaping} to HAPI HL7 v2 1.0.1's {@code Escape} (issue #5414).
 *
 * <p>Every expected value below was produced by calling 1.0.1's {@code Escape.escape} and
 * {@code Escape.unescape} on the same input. The class was also checked against 1.0.1 on 200,000
 * random strings over the delimiter, escape and formatting-code alphabet with three sets of
 * encoding characters, with no difference.</p>
 */
@Tag("unit")
@Tag("fast")
@Tag("hl7")
@DisplayName("LegacyHl7Escaping")
class LegacyHl7EscapingUnitTest {

    private static final EncodingCharacters STANDARD = new EncodingCharacters('|', "^~\\&");
    private static final EncodingCharacters ALTERNATE = new EncodingCharacters('!', "$*/%");

    private final LegacyHl7Escaping escaping = new LegacyHl7Escaping();

    @ParameterizedTest(name = "[{0}] -> [{1}]")
    @CsvSource(delimiter = '|', quoteCharacter = '\'', value = {
            "'\\H\\high\\N\\'          | 'HhighN'",
            "'\\X0D0A\\'               | 'X0D0A'",
            "'\\.br\\next'             | '\\.br\\next'",
            "'\\F\\\\S\\\\T\\\\R\\\\E\\' | '|^&~\\'",
            "'\\Cxxyy\\'               | 'Cxxyy'",
            "'\\M123456\\'             | 'M123456'",
            "'\\Zabc\\'                | 'Zabc'",
            "'trailing\\'              | 'trailing'",
            "'\\.br'                   | '.br'",
            "'plain'                   | 'plain'"})
    @DisplayName("should unescape like HAPI 1.0.1 for each escape sequence")
    void shouldUnescapeLikeHapi101_forEachEscapeSequence(String encoded, String expected) {
        assertThat(escaping.unescape(encoded, STANDARD)).isEqualTo(expected);
    }

    @Test
    @DisplayName("should decode the carriage return escape when unescaping")
    void shouldDecodeCarriageReturnEscape_whenUnescaping() {
        assertThat(escaping.unescape("a\\X000d\\b", STANDARD)).isEqualTo("a\rb");
    }

    @ParameterizedTest(name = "[{0}] -> [{1}]")
    @CsvSource(delimiter = '|', quoteCharacter = '\'', value = {
            "'A|B^C&D~E\\F' | 'A\\F\\B\\S\\C\\T\\D\\R\\E\\E\\F'",
            "'\\.br\\ kept' | '\\.br\\ kept'",
            "'\\H\\hi\\N\\' | '\\E\\H\\E\\hi\\E\\N\\E\\'",
            "'\\X0D0A\\'    | '\\E\\X0D0A\\E\\'",
            "'#'            | '#'"})
    @DisplayName("should escape like HAPI 1.0.1 for each special character")
    void shouldEscapeLikeHapi101_forEachSpecialCharacter(String text, String expected) {
        assertThat(escaping.escape(text, STANDARD)).isEqualTo(expected);
    }

    @Test
    @DisplayName("should encode a carriage return as the hex escape when escaping")
    void shouldEncodeCarriageReturnAsHexEscape_whenEscaping() {
        assertThat(escaping.escape("line1\rline2", STANDARD)).isEqualTo("line1\\X000d\\line2");
    }

    @Test
    @DisplayName("should use the message's own delimiters when encoding characters are not standard")
    void shouldUseMessagesOwnDelimiters_whenEncodingCharactersAreNotStandard() {
        assertThat(escaping.unescape("/F//.br//H/x/N/", ALTERNATE)).isEqualTo("!/.br/HxN");
        assertThat(escaping.escape("a!b/c", ALTERNATE)).isEqualTo("a/F/b/E/c");
    }
}
