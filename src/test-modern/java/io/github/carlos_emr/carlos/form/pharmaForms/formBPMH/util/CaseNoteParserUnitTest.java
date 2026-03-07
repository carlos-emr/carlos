/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CaseNoteParser}.
 *
 * <p>Tests XML-structured case note parsing for family doctor name,
 * phone number, and fax number extraction.</p>
 *
 * @since 2026-03-07
 */
@Tag("unit")
@DisplayName("CaseNoteParser")
class CaseNoteParserUnitTest {

    private static final String WELL_FORMED_NOTE =
            "<unotes>familyphysician:Dr. Smith, p:416-555-0100, f:416-555-0200</unotes>";

    @Nested
    @DisplayName("getFamilyDr")
    class GetFamilyDr {

        @Test
        @DisplayName("should extract family doctor name from well-formed note")
        void shouldExtractFamilyDr_fromWellFormedNote() {
            String result = CaseNoteParser.getFamilyDr(WELL_FORMED_NOTE);
            assertThat(result).isEqualTo("Dr. Smith");
        }

        @Test
        @DisplayName("should return empty string when note is null")
        void shouldReturnEmpty_whenNoteIsNull() {
            assertThat(CaseNoteParser.getFamilyDr(null)).isEmpty();
        }

        @Test
        @DisplayName("should return empty string when note is empty")
        void shouldReturnEmpty_whenNoteIsEmpty() {
            assertThat(CaseNoteParser.getFamilyDr("")).isEmpty();
        }

        @Test
        @DisplayName("should return empty string when no XML tags present")
        void shouldReturnEmpty_whenNoXmlTags() {
            assertThat(CaseNoteParser.getFamilyDr("plain text without tags")).isEmpty();
        }
    }

    @Nested
    @DisplayName("getPhoneNumber")
    class GetPhoneNumber {

        @Test
        @DisplayName("should extract phone number from well-formed note")
        void shouldExtractPhone_fromWellFormedNote() {
            String result = CaseNoteParser.getPhoneNumber(WELL_FORMED_NOTE);
            assertThat(result).isEqualTo("416-555-0100");
        }

        @Test
        @DisplayName("should handle phone with parenthesized area code")
        void shouldHandlePhone_withParenthesizedAreaCode() {
            String note = "<unotes>familyphysician:Dr. Jones, p:(416) 555-0100, f:416-555-0200</unotes>";
            String result = CaseNoteParser.getPhoneNumber(note);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should return empty string when note is null")
        void shouldReturnEmpty_whenNoteIsNull() {
            assertThat(CaseNoteParser.getPhoneNumber(null)).isEmpty();
        }

        @Test
        @DisplayName("should return empty string when no phone key present")
        void shouldReturnEmpty_whenNoPhoneKey() {
            String note = "<unotes>familyphysician:Dr. Smith</unotes>";
            assertThat(CaseNoteParser.getPhoneNumber(note)).isEmpty();
        }
    }

    @Nested
    @DisplayName("getFaxNumber")
    class GetFaxNumber {

        @Test
        @DisplayName("should extract fax number from well-formed note")
        void shouldExtractFax_fromWellFormedNote() {
            String result = CaseNoteParser.getFaxNumber(WELL_FORMED_NOTE);
            assertThat(result).isEqualTo("416-555-0200");
        }

        @Test
        @DisplayName("should return empty string when note is null")
        void shouldReturnEmpty_whenNoteIsNull() {
            assertThat(CaseNoteParser.getFaxNumber(null)).isEmpty();
        }

        @Test
        @DisplayName("should return empty string when no fax key present")
        void shouldReturnEmpty_whenNoFaxKey() {
            String note = "<unotes>familyphysician:Dr. Smith, p:416-555-0100</unotes>";
            assertThat(CaseNoteParser.getFaxNumber(note)).isEmpty();
        }
    }

    @Nested
    @DisplayName("getValue")
    class GetValue {

        @Test
        @DisplayName("should extract value by key from note")
        void shouldExtractValue_byKey() {
            String result = CaseNoteParser.getValue(WELL_FORMED_NOTE, "familyphysician");
            assertThat(result).isEqualTo("Dr. Smith");
        }

        @Test
        @DisplayName("should return empty string for non-existent key")
        void shouldReturnEmpty_forNonExistentKey() {
            assertThat(CaseNoteParser.getValue(WELL_FORMED_NOTE, "nonexistent")).isEmpty();
        }
    }
}
