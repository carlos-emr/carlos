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
import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportMedicationMapperUnitTest.medication;
import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNativeHistoryMapperUnitTest.render;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportMedicationDto.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportNativeMedicationMapperUnitTest {
    final CdsExportNativeMedicationMapper mapper = new CdsExportNativeMedicationMapper();

    static Map<String, String> source() {
        var fields = CdsExportMedicationMapperUnitTest.baseline();
        fields.put("cds_currentness", null);
        fields.put("cds_dose_text", null);
        fields.put("rxStatus", null);
        fields.put("regional_identifier", "00123456");
        fields.put("dosage", "5 mg / 10 mL");
        fields.put("freqcode", "OD");
        fields.put("duration", "02");
        fields.put("durunit", "W");
        fields.put("takemin", "0");
        fields.put("takemax", "0");
        fields.put("script_no", "71");
        fields.put("pharmacyId", "9");
        fields.put("end_date", "2026-07-31");
        fields.put("hide_from_drug_profile", "1");
        return fields;
    }

    @Test
    void
            shouldPreserveTheEntireOrdinaryNativePrescription_withoutInventingCodeAuthorityOrCurrentness()
                    throws Exception {
        var fields = source();
        var result = mapper.map(27, medication(fields), List.of(), Map.of());
        assertThat(result.value().fields())
                .containsEntry(Field.DrugDescription, "5 mg / 10 mL")
                .containsEntry(Field.Duration, "14")
                .containsEntry(Field.Frequency, "daily")
                .doesNotContainKeys(
                        Field.DrugIdentificationNumber,
                        Field.PastMedications,
                        Field.PrescriptionStatus,
                        Field.Dosage);
        var json = JsonMapper.builder().build().readTree(result.metadata());
        assertThat(json.path("fields").size()).isEqualTo(fields.size());
        for (var entry : fields.entrySet()) {
            var actual = json.path("fields").get(entry.getKey());
            if (entry.getValue() == null) assertThat(actual.isNull()).isTrue();
            else assertThat(actual.textValue()).isEqualTo(entry.getValue());
        }
        assertThat(json.path("regionalIdentifierAuthority").textValue()).isEqualTo("UNQUALIFIED");
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(List.of(medication(fields)), result.fields(), 1000, () -> {});
        String xml =
                new String(
                        render(
                                CdsCategory.MEDICATIONS_AND_TREATMENTS,
                                x -> mapper.write(result, x)),
                        StandardCharsets.UTF_8);
        assertThat(xml)
                .contains(
                        "CARLOS.Medication.Source.v1",
                        "CARLOS.Medication.Archived.v1",
                        "5 mg / 10 mL");
        assertThat(xml.indexOf("ResidualInfo>")).isLessThan(xml.indexOf("DrugName>"));
        assertThat(xml)
                .doesNotContain(
                        "<cds:DrugIdentificationNumber>",
                        "<cds:Strength>",
                        "<cds:Dosage>",
                        "<cds:PastMedications>");
    }

    @Test
    void shouldKeepNativeMonthsAtThirtyDaysWithoutMultiplyingRefills() throws Exception {
        var fields = source();
        fields.put("duration", "2");
        fields.put("durunit", "M");
        fields.put("repeat", "5");
        assertThat(mapper.map(27, medication(fields), List.of(), Map.of()).value().fields())
                .containsEntry(Field.Duration, "60")
                .containsEntry(Field.NumberOfRefills, "5");
    }

    @Test
    void
            shouldKeepAnArchivedActivePrescriptionArchived_withoutConvertingArchiveIntoAClinicalStatus()
                    throws Exception {
        var fields = source();
        fields.put("archived", "1");
        fields.put("archived_reason", "Stopped display");
        fields.put("rxStatus", "Active");
        var result = mapper.map(27, medication(fields), List.of(), Map.of());
        assertThat(result.archived()).isTrue();
        assertThat(result.value().fields()).containsEntry(Field.PrescriptionStatus, "Active");
        render(CdsCategory.MEDICATIONS_AND_TREATMENTS, x -> mapper.write(result, x));
    }

    @Test
    void shouldUseTheAuthoredCustomName_andRetainTheSeparateBrandAndAmbiguousUnits()
            throws Exception {
        var fields = source();
        fields.put("BN", "Separate brand");
        fields.put("unitName", "different unit");
        var result = mapper.map(27, medication(fields), List.of(), Map.of());
        assertThat(result.value().fields())
                .containsEntry(Field.DrugName, "Synthetic café 😀")
                .doesNotContainKey(Field.DosageUnitOfMeasure);
        assertThat(result.metadata()).contains("Separate brand", "different unit");
    }

    @Test
    void shouldRefuseUnboundImportedAuthorityAndMalformedNativeRegimens() throws Exception {
        for (var changes :
                List.of(
                        Map.of("cds_currentness", "CURRENT"),
                        Map.of("cds_dose_text", "1"),
                        Map.of("cds_projection_incomplete", "1"),
                        Map.of("durunit", "unknown"),
                        Map.of("duration", "one"),
                        Map.of("rxStatus", "Completed", "past_med", "0"),
                        Map.of("archived", "yes"),
                        Map.of("customName", " "))) {
            var fields = source();
            fields.putAll(changes);
            assertThatThrownBy(() -> mapper.map(27, medication(fields), List.of(), Map.of()))
                    .isInstanceOf(java.io.IOException.class);
        }
    }

    @Test
    void shouldNotGuessAnOutsidePrescribersNameParts() throws Exception {
        var fields = source();
        fields.put("outside_provider_name", "Dr. Authored Full Name");
        fields.put("outside_provider_ohip", "123456");
        var result = mapper.map(27, medication(fields), List.of(), Map.of());
        assertThat(result.value().prescriber()).isNull();
        assertThat(result.metadata()).contains("Dr. Authored Full Name", "123456");
    }
}
