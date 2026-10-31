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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HtmlAttributeNumbers}, the reader of HTML integer attributes.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("HtmlAttributeNumbers Unit Tests")
class HtmlAttributeNumbersUnitTest {

    @ParameterizedTest
    @CsvSource(value = {"12|12", "007|7", "0|0", " +9px|9", "3.5|3", "99999999999|99999999999", "'\t\n 4'|4"},
            delimiter = '|')
    @DisplayName("should read the leading digits of an attribute as a browser does")
    void shouldReadLeadingNumber_asABrowserDoes(String value, long expected) {
        assertThat(HtmlAttributeNumbers.leadingNumber(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "abc", "-3", "+", "px3", ".5"})
    @DisplayName("should report no number when the attribute does not start with digits")
    void shouldReportNoNumber_whenNoLeadingDigits(String value) {
        assertThat(HtmlAttributeNumbers.leadingNumber(value)).isEqualTo(-1);
    }

    @Test
    @DisplayName("should saturate instead of overflowing")
    void shouldSaturate_whenNumberIsHuge() {
        assertThat(HtmlAttributeNumbers.leadingNumber("99999999999999999999999999")).isEqualTo(Long.MAX_VALUE);
        assertThat(HtmlAttributeNumbers.leadingNumber("9223372036854775807")).isEqualTo(Long.MAX_VALUE);
        assertThat(HtmlAttributeNumbers.leadingNumber("9223372036854775808")).isEqualTo(Long.MAX_VALUE);
    }
}
