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
package io.github.carlos_emr.carlos.prevention;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
@Tag("fast")
@DisplayName("Add Prevention form route and dose-unit codes (#5432)")
class PreventionFormCodesUnitTest {

    @ParameterizedTest(name = "{0} is route {1}")
    @CsvSource({
            "SC, SC", "sc, SC", "SQ, SC", "sq, SC", "Subcutaneous, SC", "SUBCUT, SC",
            "ID, ID", "Intradermal, ID",
            "IM, IM", "im, IM", "Intramuscular, IM",
            "IN, IN", "Intranasal, IN", "INTRANASAL, IN",
            "IV, IV", "iv, IV", "Intravenous, IV",
            "PO, PO", "Oral, PO",
    })
    @DisplayName("should map a route in any letter case or spelling to the form's code")
    void shouldMapRoute_toFormCode(String given, String code) {
        assertThat(PreventionFormCodes.routeCode(given)).isEqualTo(code);
        assertThat(PreventionFormCodes.routeCode("  " + given + " ")).isEqualTo(code);
        assertThat(PreventionFormCodes.isOfferedRoute(code)).isTrue();
    }

    @ParameterizedTest(name = "{0} is unit {1}")
    @CsvSource({
            "mL, mL", "ML, mL", "ml, mL",
            "mg, mg", "MG, mg",
            "g, g", "G, g",
            "capsule, capsule", "CAPSULE, capsule",
            "vial, vial", "VIAL, vial", "Vial, vial",
            "dose, dose", "DOSE, dose",
    })
    @DisplayName("should map a dose unit in any letter case to the form's unit")
    void shouldMapDoseUnit_toFormUnit(String given, String unit) {
        assertThat(PreventionFormCodes.doseUnitCode(given)).isEqualTo(unit);
        assertThat(PreventionFormCodes.isOfferedDoseUnit(unit)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Topical", "intra-articular", "FAKE route"})
    @DisplayName("should keep a route the form has no code for as given")
    void shouldKeepRoute_whenFormHasNoEquivalent(String given) {
        assertThat(PreventionFormCodes.routeCode(given)).isEqualTo(given);
        assertThat(PreventionFormCodes.isOfferedRoute(given)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"tablet", "units", "FAKE unit"})
    @DisplayName("should keep a dose unit the form has no equivalent for as given")
    void shouldKeepDoseUnit_whenFormHasNoEquivalent(String given) {
        assertThat(PreventionFormCodes.doseUnitCode(given)).isEqualTo(given);
        assertThat(PreventionFormCodes.isOfferedDoseUnit(given)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("should give an empty code when no route or unit is given")
    void shouldGiveEmptyCode_whenNothingGiven(String given) {
        assertThat(PreventionFormCodes.routeCode(given)).isEmpty();
        assertThat(PreventionFormCodes.doseUnitCode(given)).isEmpty();
        assertThat(PreventionFormCodes.isOfferedRoute(given)).isFalse();
        assertThat(PreventionFormCodes.isOfferedDoseUnit(given)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"sc", "Sc", "VIAL", "ML"})
    @DisplayName("should offer only the form's own spelling of a code")
    void shouldOfferOnlyFormSpelling_forOtherLetterCase(String given) {
        assertThat(PreventionFormCodes.isOfferedRoute(given)).isFalse();
        assertThat(PreventionFormCodes.isOfferedDoseUnit(given)).isFalse();
    }
}
