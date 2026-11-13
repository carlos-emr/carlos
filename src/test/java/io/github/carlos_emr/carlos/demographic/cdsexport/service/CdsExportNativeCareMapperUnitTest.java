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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.*;
import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNativeHistoryMapperUnitTest.*;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportNativeCareMapperUnitTest {
    private final CdsExportNativeCareMapper mapper = new CdsExportNativeCareMapper();

    static CdsExportCapturedTextRowDto observation(String type, String value, String unit) {
        return row(
                        Plan.MEASUREMENTS_CURRENT,
                        91,
                        Map.of(
                                "demographicNo",
                                "27",
                                "type",
                                type,
                                "dataField",
                                value,
                                "measuringInstruction",
                                unit,
                                "dateObserved",
                                "2026-07-09 14:35:00",
                                "dateEntered",
                                "2026-07-10 12:30:00",
                                "comments",
                                "Exact clinical comment café 😀",
                                "providerNo",
                                "17"))
                .value();
    }

    private CdsExportNativeCareMapper.Result map(String type, String value, String unit)
            throws Exception {
        var observation = observation(type, value, unit);
        var definition =
                row(
                                Plan.MEASUREMENT_TYPES,
                                51,
                                Map.of(
                                        "type",
                                        type,
                                        "measuringInstruction",
                                        unit,
                                        "typeDescription",
                                        "Native " + type))
                        .value();
        var mapped = mapper.map(27, observation, List.of(), List.of(definition));
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(mapped.rows(), mapped.fields(), 10000, () -> {});
        return mapped;
    }

    @Test
    void shouldMapAllSixStandardNativeCareTypes_andValidateTheirWireUnitsAndDates()
            throws Exception {
        var cases =
                List.of(
                        List.of("WT", "70.000", "in kg", "Weight", "70"),
                        List.of("HT", "175", "cm", "Height", "175"),
                        List.of("WAIS", "90", "cm", "WaistCircumference", "90"),
                        List.of("BP", "120/80", "mmHg", "BloodPressure", "120"),
                        List.of("SKST", "Y", "", "SmokingStatus", "Y"),
                        List.of("POSK", "1.50", "pack/day", "SmokingPacks", "1.5"));
        for (var value : cases) {
            var mapped = map(value.get(0), value.get(1), value.get(2));
            assertThat(mapped.element()).isEqualTo(value.get(3));
            String xml =
                    new String(
                            render(
                                    CdsCategory.CARE_ELEMENTS,
                                    body -> mapper.writeCare(mapped, body)),
                            StandardCharsets.UTF_8);
            assertThat(xml).contains(value.get(4), "2026-07-09");
            String supporting =
                    new String(
                            render(
                                    CdsCategory.NEW_CATEGORY,
                                    body -> mapper.writeSupporting(mapped, body)),
                            StandardCharsets.UTF_8);
            assertThat(supporting)
                    .contains(
                            "Exact clinical comment café 😀",
                            value.get(1),
                            "2026-07-09 14:35:00",
                            "2026-07-10 12:30:00");
        }
    }

    @Test
    void shouldConvertOnlyExplicitUnits_withExactDecimalFactorsAndRetainTheOriginal()
            throws Exception {
        var weight = map("WT", "100", "lbs");
        assertThat(weight.values().get("Weight")).isEqualTo("45.359237");
        var height = map("HT", "70", "inches");
        assertThat(height.values().get("Height")).isEqualTo("177.8");
        assertThat(weight.metadata()).contains("100", "lbs");
        render(CdsCategory.CARE_ELEMENTS, body -> mapper.writeCare(weight, body));
        render(CdsCategory.CARE_ELEMENTS, body -> mapper.writeCare(height, body));
    }

    @Test
    void shouldPreserveOtherNativeObservationTypes_asSupportingClinicalData() throws Exception {
        var mapped = map("CUSTOM", "Full authored observation", "custom unit");
        assertThat(mapped.element()).isNull();
        assertThat(
                        new String(
                                render(
                                        CdsCategory.NEW_CATEGORY,
                                        body -> mapper.writeSupporting(mapped, body)),
                                StandardCharsets.UTF_8))
                .contains(
                        "Full authored observation",
                        "custom unit",
                        "Exact clinical comment café 😀");
    }

    /** A clinician's deletion as EctDeleteData2Action stores it: the row moves, its extensions stay. */
    static CdsExportCapturedTextRowDto deletedWeight(long id, String original) {
        return row(
                        Plan.MEASUREMENTS_DELETED,
                        id,
                        Map.of(
                                "demographicNo",
                                "27",
                                "type",
                                "WT",
                                "dataField",
                                "800",
                                "measuringInstruction",
                                "in kg",
                                "comments",
                                "entered in error",
                                "dateObserved",
                                "2026-07-09 14:35:00",
                                "dateEntered",
                                "2026-07-09 14:36:00",
                                "dateDeleted",
                                "2026-07-10 14:00:00",
                                "originalId",
                                original))
                .value();
    }

    static CdsExportCapturedTextRowDto extension(long id, String owner, String key, String value) {
        return row(
                        Plan.MEASUREMENTS_EXT,
                        id,
                        Map.of("measurement_id", owner, "keyval", key, "val", value))
                .value();
    }

    @Test
    void shouldAccountDeletedObservationsAndTheirLeftoverExtensions_withoutExportingThem()
            throws Exception {
        // Before: the deleted row was exported as NewCategory beside the current observations,
        // labelled like them, and the extensions its deletion left behind were never captured.
        var deleted = deletedWeight(101, "91");
        var unit = extension(201, "91", "unit", "kg");
        var comment = extension(202, "91", "comment", "scale not zeroed");
        var unrelated = extension(203, "92", "unit", "cm");
        var definition =
                row(Plan.MEASUREMENT_TYPES, 51, Map.of("type", "WT", "measuringInstruction", "in kg"))
                        .value();
        var current = new LinkedHashMap<String, CdsExportCapturedTextRowDto>();
        for (var value : List.of(deleted, unit, comment, unrelated, definition))
            current.put(value.table() + ":" + value.fields().get("id"), value);
        var plan = new CdsExportNativeCarePlanner().plan(index(), current, () -> {});
        assertThat(plan).hasSize(1);
        var occurrence = plan.getFirst();
        assertThat(occurrence.deleted()).isTrue();
        assertThat(occurrence.element()).isNull();
        assertThat(occurrence.extensions()).containsExactlyInAnyOrder(unit, comment);
        assertThat(occurrence.definitions()).isEmpty();
        var retired = CdsExportNativeCareMapper.retired(27, occurrence);
        var rows = List.of(deleted, unit, comment);
        var result =
                new CdsExportNativeFieldAssessmentValidator()
                        .validate(rows, retired, 10000, () -> {});
        assertThat(result.representable()).isTrue();
        assertThat(result.mapped()).isZero();
        assertThat(retired.values())
                .allSatisfy(
                        decision -> {
                            assertThat(decision.outcome())
                                    .isEqualTo(CdsExportFieldAssessmentDto.Outcome.OPERATIONAL);
                            assertThat(decision.rule())
                                    .isEqualTo("DELETED_OR_SUPERSEDED_OBSERVATION_VERSION");
                        });
        assertThatThrownBy(() -> CdsExportNativeCareMapper.retired(28, occurrence))
                .hasMessage("CDS_EXPORT_NATIVE_CARE_SCOPE_INVALID");
        // A deleted version is not a current observation and has no mapped form at all.
        assertThatThrownBy(() -> mapper.map(27, deleted, List.of(unit), List.of()))
                .hasMessage("CDS_EXPORT_NATIVE_CARE_SCOPE_INVALID");
    }

    @Test
    void shouldHoldThePatient_whenAnExtensionNamesBothACurrentAndADeletedObservation()
            throws Exception {
        var current = new LinkedHashMap<String, CdsExportCapturedTextRowDto>();
        var live = observation("WT", "70", "kg");
        var deleted = deletedWeight(101, "91");
        var unit = extension(201, "91", "unit", "kg");
        current.put("measurements:91", live);
        current.put("measurementsExt:201", unit);
        assertThatCode(() -> CdsExportCareOccurrenceService.requireOwners(27, current))
                .doesNotThrowAnyException();
        current.put("measurementsDeleted:101", deleted);
        assertThatThrownBy(() -> CdsExportCareOccurrenceService.requireOwners(27, current))
                .isInstanceOf(
                        io.github.carlos_emr.carlos.demographic.cdsexport.support
                                .CdsExportMappingReviewException.class)
                .hasMessage("CDS_EXPORT_NATIVE_CARE_EXTENSION_OWNER_AMBIGUOUS");
        current.remove("measurements:91");
        assertThatCode(() -> CdsExportCareOccurrenceService.requireOwners(27, current))
                .doesNotThrowAnyException();
        current.put("measurementsDeleted:102", deletedWeight(102, "91"));
        assertThatThrownBy(() -> CdsExportCareOccurrenceService.requireOwners(27, current))
                .hasMessage("CDS_EXPORT_NATIVE_CARE_EXTENSION_OWNER_AMBIGUOUS");
        current.remove("measurementsDeleted:102");
        current.remove("measurementsDeleted:101");
        assertThatThrownBy(() -> CdsExportCareOccurrenceService.requireOwners(27, current))
                .hasMessage("CDS_EXPORT_CARE_OCCURRENCE_INVALID");
    }

    private static CdsExportRetainedIndexDto index() {
        return new CdsExportRetainedIndexDto(
                "a".repeat(64), 0, 0, Map.of(), Set.of(), Map.of(), Map.of());
    }

    @Test
    void shouldKeepConflictingUnitsSupportingOnly_andRejectDuplicateExtensionKeys()
            throws Exception {
        var ext =
                row(
                                Plan.MEASUREMENTS_EXT,
                                101,
                                Map.of("measurement_id", "91", "keyval", "unit", "val", "lbs"))
                        .value();
        // Two different stated units: no standard element is guessed, and the full observation
        // with both units stays in the supporting NewCategory record.
        var conflicting =
                mapper.map(27, observation("WT", "80", "kg"), List.of(ext), List.of());
        assertThat(conflicting.element()).isNull();
        assertThat(conflicting.metadata()).contains("lbs", "\"kg\"", "80");
        assertThatThrownBy(
                        () ->
                                mapper.map(
                                        27,
                                        observation("WT", "80", "lbs"),
                                        List.of(ext, ext),
                                        List.of()))
                .hasMessage("CDS_EXPORT_NATIVE_CARE_EXTENSION_AMBIGUOUS");
    }

    @Test
    void shouldRefuseAnotherPatient_andKeepANonNumericValueSupportingOnlyWithoutInventingAZero()
            throws Exception {
        assertThatThrownBy(
                        () ->
                                mapper.map(
                                        28,
                                        observation("WT", "80", "kg"),
                                        List.of(),
                                        List.of()))
                .hasMessage("CDS_EXPORT_NATIVE_CARE_SCOPE_INVALID");
        for (var value : List.of(List.of("BP", "120/unknown"), List.of("WT", "70 kg"),
                List.of("SKST", "quit 2019"), List.of("HT", "175/180"))) {
            var mapped = map(value.get(0), value.get(1), "");
            assertThat(mapped.element()).isNull();
            assertThat(mapped.values()).isEmpty();
            assertThat(mapped.metadata()).contains(value.get(1));
        }
    }

    @Test
    void shouldMapTheInstructionTextCarlosActuallyStores_toStandardCareElements()
            throws Exception {
        // Seeded measurementType rows and the hypertension/diabetes/CHF flowsheets.
        var cases =
                List.of(
                        List.of("BP", "128/82", "sitting position", "BloodPressure", "128"),
                        List.of("BP", "128/82", "Sitting Position (nnn/nnn) Range:60-250", "BloodPressure", "82"),
                        List.of("BP", "128/82", "", "BloodPressure", "mmHg"),
                        List.of("WAIS", "90", "Waist Circum in cm", "WaistCircumference", "90"),
                        List.of("WAIS", "90", "in cm (nnn) Range:40-500 Interval:3mo", "WaistCircumference", "90"),
                        List.of("HT", "175", "in cm (nnn) Range:0-300", "Height", "175"),
                        List.of("WT", "70.5", "in kg (nnn.n) Range:0-300 Interval:3mo", "Weight", "70.5"),
                        List.of("WT", "70.5", "", "Weight", "kg"),
                        List.of("SKST", "yes", "Yes/No", "SmokingStatus", "Y"));
        for (var value : cases) {
            var mapped = map(value.get(0), value.get(1), value.get(2));
            assertThat(mapped.element()).as(value.toString()).isEqualTo(value.get(3));
            assertThat(mapped.values()).containsValue(value.get(4));
            render(CdsCategory.CARE_ELEMENTS, body -> mapper.writeCare(mapped, body));
        }
        // A posture or free-text instruction says nothing about weight units.
        assertThat(map("WT", "70", "standing, shoes off").element()).isNull();
        // An explicit non-mmHg pressure unit is never relabelled.
        var kpa =
                row(
                                Plan.MEASUREMENTS_EXT,
                                102,
                                Map.of("measurement_id", "91", "keyval", "unit", "val", "kPa"))
                        .value();
        assertThat(
                        mapper.map(27, observation("BP", "16/10", ""), List.of(kpa), List.of())
                                .element())
                .isNull();
    }

    @Test
    void shouldPlanExactlyTheElementTheMapperProduces_forEveryNativeObservation()
            throws Exception {
        for (var value :
                List.of(
                        List.of("BP", "128/82", "sitting position"),
                        List.of("BP", "128/82 R arm", "sitting position"),
                        List.of("WT", "70", "in kg"),
                        List.of("WT", "70", "standing"),
                        List.of("SKST", "former", ""))) {
            var observation = observation(value.get(0), value.get(1), value.get(2));
            var planned =
                    CdsExportNativeCareMapper.standard(observation.fields(), Map.of());
            var mapped = mapper.map(27, observation, List.of(), List.of());
            assertThat(planned == null ? null : planned.element())
                    .as(value.toString())
                    .isEqualTo(mapped.element());
        }
    }

    @Test
    void shouldPlanNativeAndDeletedMembership_independentlyOfMapperValuesAndRetainedParents()
            throws Exception {
        var nativeRow = observation("WT", "70", "kg");
        var imported =
                row(Plan.MEASUREMENTS_CURRENT, 92, Map.of("demographicNo", "27", "type", "HT"))
                        .value();
        var deleted =
                row(Plan.MEASUREMENTS_DELETED, 101, Map.of("demographicNo", "27", "type", "HT"))
                        .value();
        var current =
                Map.of(
                        "measurements/91",
                        nativeRow,
                        "measurements/92",
                        imported,
                        "measurementsDeleted/101",
                        deleted);
        String address = "11111111-1111-4111-8111-111111111111:" + "a".repeat(64);
        var index =
                new CdsExportRetainedIndexDto(
                        "a".repeat(64),
                        0,
                        0,
                        Map.of(),
                        Set.of(address),
                        Map.of(
                                address,
                                List.of(
                                        new CdsExportRetainedIndexDto.Reference(
                                                "measurements", "92", "b".repeat(64)))),
                        Map.of());
        var plan = new CdsExportNativeCarePlanner().plan(index, current, () -> {});
        assertThat(plan).hasSize(2);
        assertThat(
                        plan.stream()
                                .filter(p -> p.id().equals("91"))
                                .findFirst()
                                .orElseThrow()
                                .element())
                .isEqualTo("Weight");
        assertThat(
                        plan.stream()
                                .filter(p -> p.id().equals("101"))
                                .findFirst()
                                .orElseThrow()
                                .element())
                .isNull();
    }

    @Test
    void shouldKeepPartialDateAnchorsSupportingOnly_insteadOfPublishingAnInventedFullDate()
            throws Exception {
        var precision =
                row(
                                Plan.MEASUREMENTS_EXT,
                                101,
                                Map.of(
                                        "measurement_id",
                                        "91",
                                        "keyval",
                                        "date_precision",
                                        "val",
                                        "YEAR"))
                        .value();
        var mapped =
                mapper.map(27, observation("WT", "80", "kg"), List.of(precision), List.of());
        assertThat(mapped.element()).isNull();
        assertThat(mapped.values()).isEmpty();
        assertThat(mapped.metadata()).contains("date_precision", "YEAR");
    }
}
