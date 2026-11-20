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
package io.github.carlos_emr.carlos.demographic.cds.contract;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.*;

@Tag("unit")
class CdsPreparedMedicationDefaultsUnitTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void shouldTranslateDurationDaysToDayUnits_onlyInACopyOfTheVerifiedTree() throws Exception {
        var source = json.readTree("{\"source\":{\"durationDays\":\"030\"},\"provider\":null}");
        var result = CdsPreparedMedicationDefaults.apply(source);
        assertThat(result.path("source").path("duration").asText()).isEqualTo("030");
        assertThat(result.path("source").path("durationUnit").asText()).isEqualTo("D");
        assertThat(result.path("source").has("durationDays")).isFalse();
        assertThat(result.path("archived").asBoolean()).isFalse();
        assertThat(source.path("source").has("durationDays")).isTrue();
        assertThat(source.path("source").has("duration")).isFalse();
    }

    @Test
    void shouldKeepAnAbsentLegacyDuration_absentWithNoUnit() throws Exception {
        var result =
                CdsPreparedMedicationDefaults.apply(
                        json.readTree("{\"source\":{\"durationDays\":null}}"));
        assertThat(result.path("source").get("duration").isNull()).isTrue();
        assertThat(result.path("source").get("durationUnit").isNull()).isTrue();
    }

    @Test
    void shouldDefaultTheRestoredSourceContextValues_toNotRestoredForEarlierPayloads()
            throws Exception {
        // #6198: preparations sealed before the CARLOS source carrier was restored omit both.
        var legacy =
                CdsPreparedMedicationDefaults.apply(
                        json.readTree("{\"source\":{\"duration\":null,\"durationUnit\":null}}"));
        assertThat(legacy.path("source").get("quantityUnit").isNull()).isTrue();
        assertThat(legacy.path("source").get("asNeeded").isNull()).isTrue();
        var restored =
                CdsPreparedMedicationDefaults.apply(
                        json.readTree(
                                "{\"source\":{\"duration\":null,\"durationUnit\":null,"
                                        + "\"quantityUnit\":\"mL\",\"asNeeded\":false}}"));
        assertThat(restored.path("source").path("quantityUnit").asText()).isEqualTo("mL");
        assertThat(restored.path("source").path("asNeeded").isBoolean()).isTrue();
        for (String value :
                new String[] {
                    "{\"source\":{\"duration\":null,\"durationUnit\":null,\"quantityUnit\":1}}",
                    "{\"source\":{\"duration\":null,\"durationUnit\":null,\"asNeeded\":\"1\"}}"
                }) {
            var source = json.readTree(value);
            assertThatThrownBy(() -> CdsPreparedMedicationDefaults.apply(source))
                    .as(value)
                    .hasMessage("CDS_PREPARED_MEDICATION_DEFAULTS_INVALID");
        }
    }

    @Test
    void shouldAcceptEveryCurrentUnit_unchanged() throws Exception {
        for (String unit : new String[] {"D", "W", "M", "Y"}) {
            var result =
                    CdsPreparedMedicationDefaults.apply(
                            json.readTree(
                                    "{\"source\":{\"duration\":\"3\",\"durationUnit\":\""
                                            + unit
                                            + "\"},\"archived\":true}"));
            assertThat(result.path("source").path("durationUnit").asText()).isEqualTo(unit);
            assertThat(result.path("archived").asBoolean()).isTrue();
        }
    }

    @Test
    void shouldRefuseMixedMissingOrUnknownDurationShapes_insteadOfGuessingAUnit()
            throws Exception {
        for (String value :
                new String[] {
                    "null",
                    "{}",
                    "{\"source\":null}",
                    "{\"source\":{}}",
                    "{\"source\":{\"durationDays\":\"3\",\"duration\":\"3\",\"durationUnit\":\"D\"}}",
                    "{\"source\":{\"durationDays\":\"3\",\"durationUnit\":\"D\"}}",
                    "{\"source\":{\"durationDays\":3}}",
                    "{\"source\":{\"duration\":\"3\"}}",
                    "{\"source\":{\"duration\":\"3\",\"durationUnit\":null}}",
                    "{\"source\":{\"duration\":null,\"durationUnit\":\"D\"}}",
                    "{\"source\":{\"duration\":\"3\",\"durationUnit\":\"d\"}}",
                    "{\"source\":{\"duration\":\"1.5\",\"durationUnit\":\"W\"}}",
                    "{\"source\":{\"duration\":\"3\",\"durationUnit\":\"Q\"}}",
                    "{\"source\":{\"duration\":null,\"durationUnit\":null},\"archived\":\"yes\"}"
                }) {
            var source = json.readTree(value);
            assertThatThrownBy(() -> CdsPreparedMedicationDefaults.apply(source))
                    .as(value)
                    .hasMessage("CDS_PREPARED_MEDICATION_DEFAULTS_INVALID");
        }
    }
}
