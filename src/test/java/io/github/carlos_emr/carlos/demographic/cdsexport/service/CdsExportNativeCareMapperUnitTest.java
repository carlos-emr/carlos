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
        var mapped = mapper.map(27, observation, List.of(), List.of(definition), false);
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

    @Test
    void shouldKeepDeletedObservationsOutOfCurrentCare_whileRetainingTheirFullHistoricalRecord()
            throws Exception {
        var deleted =
                row(
                                Plan.MEASUREMENTS_DELETED,
                                101,
                                Map.of(
                                        "demographicNo",
                                        "27",
                                        "type",
                                        "WT",
                                        "dataField",
                                        "80",
                                        "dateDeleted",
                                        "2026-07-10 14:00:00",
                                        "originalId",
                                        "91"))
                        .value();
        var mapped = mapper.map(27, deleted, List.of(), List.of(), true);
        assertThat(mapped.element()).isNull();
        assertThat(mapped.metadata()).contains("\"deleted\":true", "2026-07-10 14:00:00", "80");
        render(CdsCategory.NEW_CATEGORY, body -> mapper.writeSupporting(mapped, body));
    }

    @Test
    void shouldRejectConflictingUnitsAndDuplicateExtensionKeys_insteadOfGuessingPrecedence() {
        var ext =
                row(
                                Plan.MEASUREMENTS_EXT,
                                101,
                                Map.of("measurement_id", "91", "keyval", "unit", "val", "lbs"))
                        .value();
        assertThatThrownBy(
                        () ->
                                mapper.map(
                                        27,
                                        observation("WT", "80", "kg"),
                                        List.of(ext),
                                        List.of(),
                                        false))
                .hasMessage("CDS_EXPORT_NATIVE_CARE_UNITS_CONFLICT");
        assertThatThrownBy(
                        () ->
                                mapper.map(
                                        27,
                                        observation("WT", "80", "lbs"),
                                        List.of(ext, ext),
                                        List.of(),
                                        false))
                .hasMessage("CDS_EXPORT_NATIVE_CARE_EXTENSION_AMBIGUOUS");
    }

    @Test
    void shouldRefuseAnotherPatientAndAnInvalidNumericValue_withoutInventingAZero() {
        assertThatThrownBy(
                        () ->
                                mapper.map(
                                        28,
                                        observation("WT", "80", "kg"),
                                        List.of(),
                                        List.of(),
                                        false))
                .hasMessage("CDS_EXPORT_NATIVE_CARE_SCOPE_INVALID");
        assertThatThrownBy(() -> map("BP", "120/unknown", "mmHg"))
                .hasMessage("CDS_EXPORT_NATIVE_CARE_NUMERIC_VALUE_REQUIRES_REVIEW");
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
    void shouldRefusePartialDateAnchors_insteadOfPublishingAnInventedFullObservationDate() {
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
        assertThatThrownBy(
                        () ->
                                mapper.map(
                                        27,
                                        observation("WT", "80", "kg"),
                                        List.of(precision),
                                        List.of(),
                                        false))
                .hasMessage("CDS_EXPORT_NATIVE_CARE_DATE_PRECISION_REQUIRES_REVIEW");
    }
}
