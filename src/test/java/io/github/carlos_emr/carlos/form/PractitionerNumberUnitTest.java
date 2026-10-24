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
package io.github.carlos_emr.carlos.form;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PractitionerNumber Tests")
@Tag("unit")
@Tag("form")
class PractitionerNumberUnitTest {

    @Test
    @DisplayName("should build the full requisition number when both parts are known")
    void shouldBuildFullNumber_whenBothPartsKnown() {
        assertThat(PractitionerNumber.ohipRequisition("123456", "00")).isEqualTo("0000-123456-00");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    @DisplayName("should return an empty number when the billing number is missing")
    void shouldReturnEmpty_whenBillingNumberMissing(String billingNo) {
        assertThat(PractitionerNumber.ohipRequisition(billingNo, "00")).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("should omit the specialty segment when no specialty code is known")
    void shouldOmitSpecialtySegment_whenSpecialtyCodeMissing(String specialtyCode) {
        assertThat(PractitionerNumber.ohipRequisition("123456", specialtyCode)).isEqualTo("0000-123456");
    }

    @Test
    @DisplayName("should trim surrounding whitespace from both parts")
    void shouldTrimWhitespace_fromBothParts() {
        assertThat(PractitionerNumber.ohipRequisition("  123456 ", " 00 ")).isEqualTo("0000-123456-00");
    }

    /**
     * The regression this class exists for. A double hyphen opens a SQL line comment, so OWASP
     * CRS rule 942100 scores any posted form body containing one as an injection attempt and the
     * reverse proxy answers 403 before Tomcat sees the save (issue #3724). No combination of
     * missing parts may produce one.
     */
    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            "NULL, NULL",
            "NULL, 00",
            "'', 00",
            "'', ''",
            "' ', ' '",
            "123456, NULL",
            "123456, ''"
    })
    @DisplayName("should never emit a double hyphen for any combination of missing parts")
    void shouldNeverEmitDoubleHyphen_forMissingParts(String billingNo, String specialtyCode) {
        assertThat(PractitionerNumber.ohipRequisition(billingNo, specialtyCode)).doesNotContain("--");
    }
}
