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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CdsXmlText}, the XML 1.0 character filter applied to OMD CDS export
 * values (#3946).
 *
 * @since 2026-09-26
 */
@Tag("unit")
@Tag("fast")
@Tag("demographic")
class CdsXmlTextUnitTest {

    @Test
    @DisplayName("should remove C0 control characters XML 1.0 forbids")
    void shouldRemoveControlCharacters_whenValueContainsThem() {
        assertThat(CdsXmlText.stripInvalidXmlCharacters("5.2\u000B mmol\u0000/L\u001B\u0008"))
                .isEqualTo("5.2 mmol/L");
    }

    @Test
    @DisplayName("should keep tab, line feed and carriage return")
    void shouldKeepWhitespaceControls_forTabNewlineAndReturn() {
        assertThat(CdsXmlText.stripInvalidXmlCharacters("a\tb\nc\rd")).isEqualTo("a\tb\nc\rd");
    }

    @Test
    @DisplayName("should keep supplementary-plane characters and drop lone surrogates")
    void shouldKeepSurrogatePairs_whenDroppingLoneSurrogates() {
        String pair = "😀";
        assertThat(CdsXmlText.stripInvalidXmlCharacters("x" + pair + "\uD800y\uDC00z"))
                .isEqualTo("x" + pair + "yz");
    }

    @Test
    @DisplayName("should remove the non-characters U+FFFE and U+FFFF")
    void shouldRemoveNonCharacters_forFffeAndFfff() {
        assertThat(CdsXmlText.stripInvalidXmlCharacters("a￾b￿c�d")).isEqualTo("abc�d");
    }

    @Test
    @DisplayName("should return the same instance for clean text and null for null")
    void shouldReturnInput_whenAlreadyClean() {
        String clean = "Glucose 5.2 mmol/L é";
        assertThat(CdsXmlText.stripInvalidXmlCharacters(clean)).isSameAs(clean);
        assertThat(CdsXmlText.stripInvalidXmlCharacters(null)).isNull();
    }

    @Test
    @DisplayName("should truncate by code point without splitting a surrogate pair")
    void shouldTruncateByCodePoint_withoutSplittingSurrogatePair() {
        String pair = "😀";
        String value = "ab" + pair + "cd";
        assertThat(CdsXmlText.truncate(value, 3)).isEqualTo("ab" + pair);
        assertThat(CdsXmlText.truncate(value, 5)).isSameAs(value);
        assertThat(CdsXmlText.truncate(null, 5)).isNull();
    }
}
