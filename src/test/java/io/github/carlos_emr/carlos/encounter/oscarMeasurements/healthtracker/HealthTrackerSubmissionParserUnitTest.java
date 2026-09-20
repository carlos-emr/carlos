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

package io.github.carlos_emr.carlos.encounter.oscarMeasurements.healthtracker;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementFlowSheet;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementTypeBeanHandler;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementTypesBean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HealthTrackerSubmissionParser}.
 *
 * <p>The parser is the join between what the Health Tracker JSP renders and what
 * the save action reads back, so these tests pin the field-name derivation as
 * hard as the behaviour that uses it.
 */
@Tag("unit")
@Tag("fast")
@Tag("measurement")
class HealthTrackerSubmissionParserUnitTest {

    private MeasurementFlowSheet flowSheet;
    private EctMeasurementTypeBeanHandler measurementTypes;
    private HealthTrackerSubmissionParser parser;

    @BeforeEach
    void setUp() {
        flowSheet = mock(MeasurementFlowSheet.class);
        measurementTypes = mock(EctMeasurementTypeBeanHandler.class);
        parser = new HealthTrackerSubmissionParser(measurementTypes);
    }

    @Test
    @DisplayName("should strip non-word characters from the display name")
    void shouldStripNonWordCharacters_forFieldName() {
        assertThat(HealthTrackerSubmissionParser.fieldNameFor("Weight (kg)")).isEqualTo("Weightkg");
        assertThat(HealthTrackerSubmissionParser.fieldNameFor("BP")).isEqualTo("BP");
        assertThat(HealthTrackerSubmissionParser.fieldNameFor("A1C %")).isEqualTo("A1C");
    }

    @Test
    @DisplayName("should return empty string when display name is null")
    void shouldReturnEmptyString_forNullDisplayName() {
        assertThat(HealthTrackerSubmissionParser.fieldNameFor(null)).isEmpty();
    }

    @Test
    @DisplayName("should parse a filled-in measurement into an entry")
    void shouldParseEntry_whenValueSubmitted() {
        givenMeasurement("WT", "Weight (kg)", "kg");

        Map<String, String> params = new HashMap<>();
        params.put("Weightkg", " 82.5 ");
        params.put("Weightkg_date", "2026-09-01");
        params.put("Weightkg_comments", "post-op");
        params.put("Weightkg_note", "addtonote");

        List<HealthTrackerEntry> entries = parser.parse(flowSheet, params::get, "2026-09-20");

        assertThat(entries).hasSize(1);
        HealthTrackerEntry entry = entries.get(0);
        assertThat(entry.measurementType()).isEqualTo("WT");
        assertThat(entry.fieldName()).isEqualTo("Weightkg");
        assertThat(entry.displayName()).isEqualTo("Weight (kg)");
        assertThat(entry.measuringInstruction()).isEqualTo("kg");
        assertThat(entry.value()).isEqualTo("82.5");
        assertThat(entry.comment()).isEqualTo("post-op");
        assertThat(entry.dateObserved()).isEqualTo("2026-09-01");
        assertThat(entry.addToNote()).isTrue();
    }

    @Test
    @DisplayName("should fall back to the form default when the row date is blank")
    void shouldUseDefaultDate_whenRowDateBlank() {
        givenMeasurement("WT", "Weight (kg)", "kg");

        Map<String, String> params = new HashMap<>();
        params.put("Weightkg", "80");
        params.put("Weightkg_date", "   ");

        List<HealthTrackerEntry> entries = parser.parse(flowSheet, params::get, "2026-09-20");

        assertThat(entries).singleElement()
                .extracting(HealthTrackerEntry::dateObserved).isEqualTo("2026-09-20");
    }

    @Test
    @DisplayName("should skip measurements the clinician left blank")
    void shouldSkipEntry_whenValueBlank() {
        givenMeasurement("WT", "Weight (kg)", "kg");

        Map<String, String> params = new HashMap<>();
        params.put("Weightkg", "   ");
        params.put("Weightkg_comments", "a comment with no value");

        assertThat(parser.parse(flowSheet, params::get, "2026-09-20")).isEmpty();
    }

    @Test
    @DisplayName("should skip prevention rows because they carry no measurement type")
    void shouldSkipEntry_forPreventionItem() {
        Map<String, String> info = new HashMap<>();
        info.put("prevention_type", "Flu");
        info.put("display_name", "Influenza");
        when(flowSheet.getMeasurementList()).thenReturn(List.of("Flu"));
        when(flowSheet.getMeasurementFlowSheetInfo("Flu")).thenReturn(info);

        Map<String, String> params = new HashMap<>();
        params.put("Influenza", "Yes");

        assertThat(parser.parse(flowSheet, params::get, "2026-09-20")).isEmpty();
    }

    @Test
    @DisplayName("should skip measurements with no measurementType row")
    void shouldSkipEntry_whenMeasurementTypeUnknown() {
        Map<String, String> info = new HashMap<>();
        info.put("measurement_type", "ZZZ");
        info.put("display_name", "Mystery");
        when(flowSheet.getMeasurementList()).thenReturn(List.of("ZZZ"));
        when(flowSheet.getMeasurementFlowSheetInfo("ZZZ")).thenReturn(info);
        when(measurementTypes.getMeasurementType(anyString())).thenReturn(null);

        Map<String, String> params = new HashMap<>();
        params.put("Mystery", "42");

        assertThat(parser.parse(flowSheet, params::get, "2026-09-20")).isEmpty();
    }

    @Test
    @DisplayName("should return no entries when the flowsheet is null")
    void shouldReturnEmptyList_forNullFlowsheet() {
        assertThat(parser.parse(null, key -> "value", "2026-09-20")).isEmpty();
    }

    @Test
    @DisplayName("should not flag the progress note when the checkbox was left off")
    void shouldNotFlagNote_whenCheckboxUnchecked() {
        givenMeasurement("WT", "Weight (kg)", "kg");

        Map<String, String> params = new HashMap<>();
        params.put("Weightkg", "80");

        assertThat(parser.parse(flowSheet, params::get, "2026-09-20"))
                .singleElement().extracting(HealthTrackerEntry::addToNote).isEqualTo(false);
    }

    private void givenMeasurement(String type, String displayName, String measuringInstruction) {
        Map<String, String> info = new HashMap<>();
        info.put("measurement_type", type);
        info.put("display_name", displayName);
        when(flowSheet.getMeasurementList()).thenReturn(List.of(type));
        when(flowSheet.getMeasurementFlowSheetInfo(type)).thenReturn(info);

        EctMeasurementTypesBean bean = new EctMeasurementTypesBean();
        bean.setType(type);
        bean.setMeasuringInstrc(measuringInstruction);
        when(measurementTypes.getMeasurementType(type)).thenReturn(bean);
    }
}
