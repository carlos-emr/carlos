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
package io.github.carlos_emr.carlos.encounter.display;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the per-provider eChart display settings: their OSCAR 19 property names, the defaults the
 * alpha tester asked for (everything on except Referring Doctor and Ocean), and how stored values
 * are read.
 */
@Tag("unit")
@Tag("fast")
@Tag("encounter")
@DisplayName("eChart display preferences")
class EchartDisplayPreferenceUnitTest extends CarlosUnitTestBase {

    @Nested
    @DisplayName("definitions")
    class Definitions {

        @Test
        @DisplayName("should list the settings in the Preferences page order with OSCAR 19 key names, click-to-dial after the cell")
        void shouldListSettings_inPreferencesPageOrder() {
            assertThat(Arrays.stream(EchartDisplayPreference.values()).map(EchartDisplayPreference::propertyName))
                    .containsExactly("echart_show_timer", "echart_show_group_document_by_type", "echart_show_DOB",
                            "echart_show_cell", "echart_phone_tel_links", "echart_show_HIN", "echart_email_indicator",
                            "echart_show_fam_doc_widget", "echart_show_ref_doc_widget", "echart_show_ocean");
        }

        @Test
        @DisplayName("should default to on except for click-to-dial phone links, the Referring Doctor panel and Ocean")
        void shouldDefaultToOn_exceptTelLinksReferringDoctorAndOcean() {
            for (EchartDisplayPreference preference : EchartDisplayPreference.values()) {
                boolean expected = preference != EchartDisplayPreference.PHONE_TEL_LINKS
                        && preference != EchartDisplayPreference.SHOW_REFERRING_DOCTOR
                        && preference != EchartDisplayPreference.SHOW_OCEAN;
                assertThat(preference.defaultValue()).as(preference.name()).isEqualTo(expected);
            }
        }

        @ParameterizedTest
        @EnumSource(EchartDisplayPreference.class)
        @DisplayName("should derive each label key from the property name")
        void shouldDeriveLabelKey_fromPropertyName(EchartDisplayPreference preference) {
            assertThat(preference.labelKey()).isEqualTo("provider.providerpreference.echart." + preference.propertyName());
        }
    }

    @Nested
    @DisplayName("stored values")
    class StoredValues {

        @Test
        @DisplayName("should parse exactly true and false, ignoring surrounding blanks")
        void shouldParseTrueAndFalse_withSurroundingBlanks() {
            assertThat(EchartDisplayPreference.parse("true")).isTrue();
            assertThat(EchartDisplayPreference.parse(" false ")).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "yes", "no", "on", "1", "TRUE", "False", "null"})
        @DisplayName("should reject anything that is not true or false")
        void shouldRejectValue_whenNotTrueOrFalse(String value) {
            assertThat(EchartDisplayPreference.parse(value)).isNull();
        }

        @Test
        @DisplayName("should read a missing or unreadable value as the default, not as off")
        void shouldResolveDefault_whenValueMissingOrUnreadable() {
            assertThat(EchartDisplayPreference.SHOW_HIN.resolve(null)).isTrue();
            assertThat(EchartDisplayPreference.SHOW_HIN.resolve("no")).isTrue();
            assertThat(EchartDisplayPreference.SHOW_OCEAN.resolve("garbage")).isFalse();
            assertThat(EchartDisplayPreference.SHOW_HIN.resolve("false")).isFalse();
            assertThat(EchartDisplayPreference.SHOW_OCEAN.resolve("true")).isTrue();
        }
    }

    @Nested
    @DisplayName("resolved set")
    class ResolvedSet {

        @Test
        @DisplayName("should expose the defaults for a provider who saved nothing")
        void shouldExposeDefaults_forProviderWithoutRows() {
            EchartDisplayPreferences preferences = EchartDisplayPreferences.defaults();

            assertThat(preferences.showTimer()).isTrue();
            assertThat(preferences.groupDocumentsByType()).isTrue();
            assertThat(preferences.showDob()).isTrue();
            assertThat(preferences.showCell()).isTrue();
            assertThat(preferences.phoneTelLinks()).isFalse();
            assertThat(preferences.showHin()).isTrue();
            assertThat(preferences.showEmail()).isTrue();
            assertThat(preferences.showFamilyDoctor()).isTrue();
            assertThat(preferences.showReferringDoctor()).isFalse();
            assertThat(preferences.showOcean()).isFalse();
        }

        @Test
        @DisplayName("should apply stored values and ignore unrelated properties")
        void shouldApplyStoredValues_andIgnoreUnrelatedProperties() {
            Map<String, String> stored = new HashMap<>();
            stored.put("echart_show_timer", "false");
            stored.put("echart_show_DOB", "false");
            stored.put("echart_show_ref_doc_widget", "true");
            stored.put("echart_show_ocean", "true");
            stored.put("echart_phone_tel_links", "true");
            stored.put("ProviderColour", "#123456");

            EchartDisplayPreferences preferences = EchartDisplayPreferences.fromStoredValues(stored);

            assertThat(preferences.showTimer()).isFalse();
            assertThat(preferences.showDob()).isFalse();
            assertThat(preferences.showReferringDoctor()).isTrue();
            assertThat(preferences.showOcean()).isTrue();
            assertThat(preferences.phoneTelLinks()).isTrue();
            assertThat(preferences.showHin()).isTrue();
        }

        @Test
        @DisplayName("should treat a null map as nothing stored")
        void shouldTreatNullMap_asNothingStored() {
            assertThat(EchartDisplayPreferences.fromStoredValues(null).showFamilyDoctor()).isTrue();
        }
    }
}
