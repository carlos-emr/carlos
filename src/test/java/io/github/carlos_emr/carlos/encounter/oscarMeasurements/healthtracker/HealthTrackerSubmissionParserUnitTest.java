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
import static org.assertj.core.api.Assertions.tuple;
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
    @DisplayName("should name the field after the measurement type")
    void shouldNameField_byMeasurementType() {
        assertThat(HealthTrackerSubmissionParser.fieldNameFor("WT")).isEqualTo("WT");
        assertThat(HealthTrackerSubmissionParser.fieldNameFor("BP")).isEqualTo("BP");
        assertThat(HealthTrackerSubmissionParser.fieldNameFor("A1C2")).isEqualTo("A1C2");
    }

    @Test
    @DisplayName("should escape a type reversibly rather than stripping characters")
    void shouldEscapeField_forTypeWithPunctuation() {
        // EctAddMeasurementType2Action accepts ^[\w\s,.?]*$, so an administrator can
        // create all of these. Stripping would collapse them onto one field name and
        // one posted parameter; escaping keeps every type distinct.
        assertThat(HealthTrackerSubmissionParser.fieldNameFor("FOO BAR")).isEqualTo("FOO_0020BAR");
        assertThat(HealthTrackerSubmissionParser.fieldNameFor("FOO_BAR")).isEqualTo("FOO__BAR");
        assertThat(HealthTrackerSubmissionParser.fieldNameFor("A1C,2")).isEqualTo("A1C_002C2");
        assertThat(HealthTrackerSubmissionParser.fieldNameFor("HGB?")).isEqualTo("HGB_003F");
    }

    @Test
    @DisplayName("should give every type in an administrator-typable alphabet its own field")
    void shouldKeepFieldsDistinct_forCollidableTypes() {
        List<String> types = List.of("FOO BAR", "FOOBAR", "FOO_BAR", "FOO.BAR", "FOO,BAR", "FOO?BAR", "FOO__BAR");

        assertThat(types.stream().map(HealthTrackerSubmissionParser::fieldNameFor).distinct().count())
                .isEqualTo(types.size());
    }

    @Test
    @DisplayName("should return empty string when the measurement type is null")
    void shouldReturnEmptyString_forNullMeasurementType() {
        assertThat(HealthTrackerSubmissionParser.fieldNameFor(null)).isEmpty();
    }

    @Test
    @DisplayName("should keep two items apart when their display names collide")
    void shouldKeepFieldsDistinct_whenDisplayNamesCollide() {
        // A clinician can rename flowsheet items freely, so two items can end up
        // with one display name -- and "A/B" and "AB" sanitize to the same string.
        // Naming the fields after the display name would give both rows one
        // parameter, and the value would land under whichever type was read last.
        Map<String, String> weight = new HashMap<>();
        weight.put("measurement_type", "WT");
        weight.put("display_name", "Daily/Weight");
        Map<String, String> education = new HashMap<>();
        education.put("measurement_type", "CEDW");
        education.put("display_name", "DailyWeight");

        when(flowSheet.getMeasurementList()).thenReturn(List.of("WT", "CEDW"));
        when(flowSheet.getMeasurementFlowSheetInfo("WT")).thenReturn(weight);
        when(flowSheet.getMeasurementFlowSheetInfo("CEDW")).thenReturn(education);
        for (String type : List.of("WT", "CEDW")) {
            EctMeasurementTypesBean bean = new EctMeasurementTypesBean();
            bean.setType(type);
            when(measurementTypes.getMeasurementType(type)).thenReturn(bean);
        }

        Map<String, String> params = new HashMap<>();
        params.put("WT", "82.5");
        params.put("CEDW", "Yes");

        assertThat(parser.parse(flowSheet, params::get, "2026-09-20"))
                .extracting(HealthTrackerEntry::measurementType, HealthTrackerEntry::value)
                .containsExactly(tuple("WT", "82.5"), tuple("CEDW", "Yes"));
    }

    @Test
    @DisplayName("should parse a filled-in measurement into an entry")
    void shouldParseEntry_whenValueSubmitted() {
        givenMeasurement("WT", "Weight (kg)", "kg");

        Map<String, String> params = new HashMap<>();
        params.put("WT", " 82.5 ");
        params.put("WT_date", "2026-09-01");
        params.put("WT_comments", "post-op");
        params.put("WT_note", "addtonote");

        List<HealthTrackerEntry> entries = parser.parse(flowSheet, params::get, "2026-09-20");

        assertThat(entries).hasSize(1);
        HealthTrackerEntry entry = entries.get(0);
        assertThat(entry.measurementType()).isEqualTo("WT");
        assertThat(entry.fieldName()).isEqualTo("WT");
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
        params.put("WT", "80");
        params.put("WT_date", "   ");

        List<HealthTrackerEntry> entries = parser.parse(flowSheet, params::get, "2026-09-20");

        assertThat(entries).singleElement()
                .extracting(HealthTrackerEntry::dateObserved).isEqualTo("2026-09-20");
    }

    @Test
    @DisplayName("should skip measurements the clinician left blank")
    void shouldSkipEntry_whenValueBlank() {
        givenMeasurement("WT", "Weight (kg)", "kg");

        Map<String, String> params = new HashMap<>();
        params.put("WT", "   ");
        params.put("WT_comments", "a comment with no value");

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
        params.put("Flu", "Yes");

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
        params.put("ZZZ", "42");

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
        params.put("WT", "80");

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
