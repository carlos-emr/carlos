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
package io.github.carlos_emr.carlos.casemgmt.common;

import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Each patient's chart keeps its working state under its own session attribute (issue #4673):
 * the attribute names for two patients never coincide, and a value that is not a patient number
 * never yields a name.
 *
 * @since 2026-10-16
 */
@Tag("unit")
@Tag("casemgmt")
@DisplayName("Per-patient chart session keys")
class ChartSessionKeysUnitTest {

    private static final List<Function<String, String>> KEYS = List.of(
            patient -> ChartSessionKeys.noteFilterHandoff("issues", patient),
            ChartSessionKeys::lastSavedNoteText,
            ChartSessionKeys::draftRestoring,
            ChartSessionKeys::currentAppointment);

    @Test
    void shouldNameDifferentAttributes_forDifferentPatients() {
        for (Function<String, String> key : KEYS) {
            assertThat(key.apply("101")).isNotEqualTo(key.apply("202"));
            // The old login-wide names must not be reused for any patient.
            assertThat(key.apply("101")).isNotIn("issues", "CaseManagementViewAction_filter_issues",
                    "lastSavedNoteString", "restoring", "cur_appointment_no");
        }
    }

    @Test
    void shouldNameTheSameAttribute_forTheSamePatientWrittenDifferently() {
        for (Function<String, String> key : KEYS) {
            assertThat(key.apply("0101")).isEqualTo(key.apply("101"));
        }
    }

    @Test
    void shouldKeepEachHandoffApart_forTheSamePatient() {
        assertThat(ChartSessionKeys.noteFilterHandoff("issues", "101"))
                .isNotEqualTo(ChartSessionKeys.noteFilterHandoff("note_sort", "101"))
                .isNotEqualTo(ChartSessionKeys.draftRestoring("101"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "abc", "-5", "1.5", "101 ", "12345678901", "101,202"})
    void shouldRefuseToNameAnAttribute_forAValueThatIsNotAPatientNumber(String value) {
        assertThat(ChartSessionKeys.isPatientNumber(value)).isFalse();
        for (Function<String, String> key : KEYS) {
            assertThatThrownBy(() -> key.apply(value)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void shouldAcceptPatientNumbers_upToTenDigits() {
        assertThat(ChartSessionKeys.isPatientNumber("1")).isTrue();
        assertThat(ChartSessionKeys.isPatientNumber("1234567890")).isTrue();
    }
}
