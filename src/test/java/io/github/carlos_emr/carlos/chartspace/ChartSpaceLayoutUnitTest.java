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
package io.github.carlos_emr.carlos.chartspace;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.model.SystemPreferences.ECHART_PREFERENCE_KEYS;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link ChartSpaceLayout}.
 *
 * @since 2026-10-23
 */
@Tag("unit")
@Tag("fast")
@DisplayName("ChartSpaceLayout")
class ChartSpaceLayoutUnitTest {

    @ParameterizedTest
    @CsvSource({"familyPractice, FAMILY_PRACTICE", "eyeCare, EYE_CARE"})
    @DisplayName("should map each persisted id string to its layout")
    void shouldMapPersistedId_toLayout(String id, ChartSpaceLayout expected) {
        // Pins the literal strings stored in the database and used by PRESETS in chartspace-layout.js.
        assertThat(ChartSpaceLayout.fromId(id)).isEqualTo(Optional.of(expected));
    }

    @ParameterizedTest
    @EnumSource(ChartSpaceLayout.class)
    @DisplayName("should return the layout for each known id")
    void shouldReturnLayout_forKnownIds(ChartSpaceLayout layout) {
        assertThat(ChartSpaceLayout.fromId(layout.id())).isEqualTo(Optional.of(layout));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "EyeCare", " eyeCare", "eyecare", "<script>", "__proto__"})
    @DisplayName("should return empty for ids outside the allow-list")
    void shouldReturnEmpty_forUnknownIds(String id) {
        assertThat(ChartSpaceLayout.fromId(id)).isEmpty();
    }

    @Test
    @DisplayName("should default to family practice")
    void shouldDefaultToFamilyPractice_forDefaultConstant() {
        assertThat(ChartSpaceLayout.DEFAULT).isEqualTo(ChartSpaceLayout.FAMILY_PRACTICE);
    }

    @Test
    @DisplayName("should expose the ChartSpace preference key")
    void shouldExposeChartSpaceKey_inEchartPreferenceKeys() {
        assertThat(ECHART_PREFERENCE_KEYS.valueOf("chartspace_default_layout"))
                .isEqualTo(ECHART_PREFERENCE_KEYS.chartspace_default_layout);
    }
}
