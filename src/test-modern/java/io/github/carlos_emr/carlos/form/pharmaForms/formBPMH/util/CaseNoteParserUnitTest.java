/**
 * Copyright (c) 2015-2019. The Pharmacists Clinic, Faculty of Pharmaceutical Sciences, University of British Columbia. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * The Pharmacists Clinic
 * Faculty of Pharmaceutical Sciences
 * University of British Columbia
 * Vancouver, British Columbia, Canada
 *
 * <p>
 * Migrated from legacy JUnit 4 CaseNoteParserTest to JUnit 5 for the CARLOS EMR project (2026).
 */
package io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CaseNoteParser}.
 *
 * <p>Tests parsing of family physician contact information from case note strings,
 * including doctor name, phone number, and fax number extraction.
 * Migrated from legacy JUnit 4 CaseNoteParserTest.
 *
 * @since 2014-11-01 (original)
 */
@Tag("unit")
@DisplayName("CaseNoteParser unit tests")
class CaseNoteParserUnitTest {

    @Test
    @DisplayName("should parse doctor name, phone, and fax from well-formed XML note")
    void shouldParseDoctorNamePhoneAndFax_fromWellFormedXmlNote() {
        String note = "<unotes>Family Physician : Dr. Joe, p:604-789-3652, f:037-286-3753</unotes>";

        assertThat(CaseNoteParser.getFamilyDr(note)).isEqualTo("Dr. Joe");
        assertThat(CaseNoteParser.getPhoneNumber(note)).isEqualTo("604-789-3652");
        assertThat(CaseNoteParser.getFaxNumber(note)).isEqualTo("037-286-3753");
    }

    @Test
    @DisplayName("should parse note with missing fax and parenthesized phone")
    void shouldParseNote_withMissingFaxAndParenthesizedPhone() {
        String note = "<unotes>Family Physician: Dr. Iglesias, p:(250)957-2332</unotes>";

        assertThat(CaseNoteParser.getFamilyDr(note)).isEqualTo("Dr. Iglesias");
        assertThat(CaseNoteParser.getPhoneNumber(note)).isEqualTo("(250)957-2332");
        assertThat(CaseNoteParser.getFaxNumber(note)).isEmpty();
    }

    @Test
    @DisplayName("should include phone in doctor name when no p: prefix")
    void shouldIncludePhoneInDoctorName_whenNoPPrefix() {
        String note = "<unotes>Family Physician: Dr. Iglesias (250)957-2332</unotes>";

        assertThat(CaseNoteParser.getFamilyDr(note)).isEqualTo("Dr. Iglesias (250)957-2332");
        assertThat(CaseNoteParser.getPhoneNumber(note)).isEmpty();
        assertThat(CaseNoteParser.getFaxNumber(note)).isEmpty();
    }

    @Test
    @DisplayName("should parse note without XML tags")
    void shouldParseNote_withoutXmlTags() {
        String note = "Family Physician : Dr. Joe, p:604-789-3652, f:037-286-3753";

        assertThat(CaseNoteParser.getFamilyDr(note)).isEqualTo("Dr. Joe");
        assertThat(CaseNoteParser.getPhoneNumber(note)).isEqualTo("604-789-3652");
        assertThat(CaseNoteParser.getFaxNumber(note)).isEqualTo("037-286-3753");
    }

    @Test
    @DisplayName("should return empty strings for empty note")
    void shouldReturnEmptyStrings_forEmptyNote() {
        assertThat(CaseNoteParser.getFamilyDr("")).isEmpty();
        assertThat(CaseNoteParser.getPhoneNumber("")).isEmpty();
        assertThat(CaseNoteParser.getFaxNumber("")).isEmpty();
    }

    @Test
    @DisplayName("should return empty strings for null note")
    void shouldReturnEmptyStrings_forNullNote() {
        assertThat(CaseNoteParser.getFamilyDr(null)).isEmpty();
        assertThat(CaseNoteParser.getPhoneNumber(null)).isEmpty();
        assertThat(CaseNoteParser.getFaxNumber(null)).isEmpty();
    }
}
