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

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeFieldKeyDto;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Medication reasons ({@code drugReason}) as CARLOS writes them: the Rx reason page
 * ({@code RxReason2Action}, primary checkbox, archive with a reason) and the prescription page
 * ({@code RxWriteScript2Action}, always unflagged, empty comments).
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportMedicationReasonMapperUnitTest {
    private final CdsExportMedicationReasonMapper mapper = new CdsExportMedicationReasonMapper();

    static CdsExportCapturedTextRowDto drug() {
        return CdsExportMedicationMapperUnitTest.medication(
                CdsExportMedicationMapperUnitTest.baseline());
    }

    /** One reason of drug 51 for patient 27, active, coded today by provider 999998. */
    static Map<String, String> reason(int id, String system, String code, String primary) {
        var fields = new LinkedHashMap<String, String>();
        CdsExportPatientTableReadSupport.Plan.DRUG_REASONS
                .columns()
                .forEach(column -> fields.put(column, null));
        fields.put("id", Integer.toString(id));
        fields.put("drugId", "51");
        fields.put("demographicNo", "27");
        fields.put("codingSystem", system);
        fields.put("code", code);
        fields.put("comments", "");
        fields.put("primaryReasonFlag", primary);
        fields.put("archivedFlag", "0");
        fields.put("providerNo", "999998");
        fields.put("dateCoded", "2026-11-13");
        return fields;
    }

    static CdsExportCapturedTextRowDto row(Map<String, String> fields) {
        return new CdsExportCapturedTextRowDto(
                "drugReason",
                CdsExportPatientTableSourceReader.key(Long.parseLong(fields.get("id"))),
                fields);
    }

    private static CdsExportNativeFieldKeyDto key(int id, String column) {
        return new CdsExportNativeFieldKeyDto(
                "drugReason", CdsExportPatientTableSourceReader.key(id), column);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> carried(String residual) throws Exception {
        assertThat(residual).doesNotContain(" ", "\t", "\n", "\r");
        var context = JsonMapper.builder().build().readValue(residual, Map.class);
        assertThat(context.get("version")).isEqualTo(1);
        return (List<Map<String, String>>) context.get("reasons");
    }

    @Test
    @DisplayName("should write the primary reason's ICD-9 code as ProblemCode and carry every reason")
    void shouldWriteThePrimaryReasonsIcd9Code_asProblemCodeAndCarryEveryReason() throws Exception {
        var primary = reason(7, "icd9", "250", "1");
        primary.put("comments", "Type 2  diabetes,\r\nreviewed");
        var other = reason(3, "icd9", "401", "0");
        var result = mapper.map(27, drug(), List.of(row(primary), row(other)));

        assertThat(result.problemCode()).isEqualTo("250");
        var reasons = carried(result.residual());
        // Id order; the row id, drug id and patient stay out of the exported context.
        assertThat(reasons).extracting(reason -> reason.get("code")).containsExactly("401", "250");
        assertThat(reasons.get(1))
                .containsEntry("comments", "Type 2  diabetes,\r\nreviewed")
                .containsEntry("codingSystem", "icd9")
                .containsEntry("primaryReasonFlag", "1")
                .containsEntry("archivedFlag", "0")
                .containsEntry("providerNo", "999998")
                .containsEntry("dateCoded", "2026-11-13")
                .containsEntry("archivedReason", null)
                .doesNotContainKeys("id", "drugId", "demographicNo");
        var fields = result.fields(true);
        assertThat(fields.get(key(7, "code")).destination()).isEqualTo("ProblemCode");
        assertThat(fields.get(key(3, "code")).destination())
                .isEqualTo(CdsExportMedicationReasonMapper.RESIDUAL);
        assertThat(fields.get(key(7, "comments")).outcome()).isEqualTo(Outcome.MAPPED);
        assertThat(fields.get(key(7, "demographicNo")).outcome()).isEqualTo(Outcome.OPERATIONAL);
        var validated =
                new CdsExportNativeFieldAssessmentValidator()
                        .validate(List.of(row(primary), row(other)), fields, 1000, () -> {});
        assertThat(validated.representable()).isTrue();
        assertThat(validated.fields()).isEqualTo(22);
    }

    @Test
    @DisplayName("should use the only reason as the indication when the Rx page filed it unflagged")
    void shouldUseTheOnlyActiveReason_whenNoReasonIsFlaggedPrimary() throws Exception {
        var result = mapper.map(27, drug(), List.of(row(reason(9, "icd9", "4019", "0"))));
        assertThat(result.problemCode()).isEqualTo("4019");
        assertThat(result.fields(true).get(key(9, "code")).rule())
                .isEqualTo(CdsExportMedicationReasonMapper.SELECTED);
    }

    @Test
    @DisplayName("should name no indication when several reasons compete for ProblemCode")
    void shouldNameNoIndication_whenSeveralReasonsCompeteForProblemCode() throws Exception {
        var unflagged =
                mapper.map(
                        27,
                        drug(),
                        List.of(row(reason(1, "icd9", "250", "0")), row(reason(2, "icd9", "401", "0"))));
        var twoPrimary =
                mapper.map(
                        27,
                        drug(),
                        List.of(row(reason(1, "icd9", "250", "1")), row(reason(2, "icd9", "401", "1"))));
        // A primary flag CARLOS never writes names no indication either.
        var unknownFlag =
                mapper.map(
                        27,
                        drug(),
                        List.of(row(reason(1, "icd9", "250", "1")), row(reason(2, "icd9", "401", "2"))));
        for (var result : List.of(unflagged, twoPrimary, unknownFlag)) {
            assertThat(result.problemCode()).isNull();
            assertThat(carried(result.residual())).hasSize(2);
            assertThat(result.fields(false).values())
                    .noneMatch(field -> field.destination().equals("ProblemCode"));
        }
        assertThat(carried(unknownFlag.residual()).get(1)).containsEntry("primaryReasonFlag", "2");
    }

    @Test
    @DisplayName("should account archived reasons without exporting them")
    void shouldAccountArchivedReasons_withoutExportingThem() throws Exception {
        var archived = reason(4, "icd9", "250", "1");
        archived.put("archivedFlag", "1");
        archived.put("archivedReason", "Wrong indication");
        var onlyArchived = mapper.map(27, drug(), List.of(row(archived)));
        assertThat(onlyArchived.problemCode()).isNull();
        assertThat(onlyArchived.residual()).isNull();
        assertThat(onlyArchived.fields(false))
                .hasSize(11)
                .allSatisfy(
                        (field, decision) -> {
                            assertThat(decision.outcome()).isEqualTo(Outcome.OPERATIONAL);
                            if (!Set.of("id", "drugId", "demographicNo").contains(field.column()))
                                assertThat(decision.rule())
                                        .isEqualTo(CdsExportMedicationReasonMapper.ARCHIVED);
                        });
        // The archived primary reason no longer competes: the active one is the indication.
        var withActive =
                mapper.map(27, drug(), List.of(row(archived), row(reason(5, "icd9", "401", "0"))));
        assertThat(withActive.problemCode()).isEqualTo("401");
        assertThat(carried(withActive.residual()))
                .singleElement()
                .satisfies(reason -> assertThat(reason).containsEntry("code", "401"));
    }

    @Test
    @DisplayName("should keep a code ProblemCode cannot carry exactly only in the residual")
    void shouldKeepACodeProblemCodeCannotCarry_onlyInTheResidual() throws Exception {
        for (var reason :
                List.of(
                        reason(1, "icd9", "12345678901", "1"),
                        reason(1, "icd9", "250 ", "1"),
                        reason(1, "icd9", "", "1"),
                        reason(1, "icd9", null, "1"),
                        reason(1, "icd10", "E11.9", "1"),
                        reason(1, "SnomedCore", "44054006", "1"),
                        reason(1, null, "250", "1"))) {
            var result = mapper.map(27, drug(), List.of(row(reason)));
            assertThat(result.problemCode()).isNull();
            var carried = carried(result.residual()).getFirst();
            assertThat(carried.get("code")).isEqualTo(reason.get("code"));
            assertThat(carried.get("codingSystem")).isEqualTo(reason.get("codingSystem"));
            assertThat(result.fields(false).get(key(1, "code")).destination())
                    .isEqualTo(CdsExportMedicationReasonMapper.RESIDUAL);
        }
        assertThat(CdsExportMedicationReasonMapper.problemCode("icd9", "V70.0")).isTrue();
        assertThat(CdsExportMedicationReasonMapper.problemCode("icd9", "1234567890")).isTrue();
    }

    @Test
    @DisplayName("should keep the indication in the residual when an imported ProblemCode is kept")
    void shouldKeepTheIndicationInTheResidual_whenAnOriginalProblemCodeIsKept() throws Exception {
        var result = mapper.map(27, drug(), List.of(row(reason(9, "icd9", "4019", "1"))));
        var kept = result.fields(false).get(key(9, "code"));
        assertThat(kept.destination()).isEqualTo(CdsExportMedicationReasonMapper.RESIDUAL);
        assertThat(kept.rule()).isEqualTo(CdsExportMedicationReasonMapper.RETAINED);
        assertThatThrownBy(() -> CdsExportMedicationReasonMapper.Result.NONE.fields(true))
                .isInstanceOf(IllegalStateException.class);
        assertThat(mapper.map(27, drug(), List.of())).isSameAs(CdsExportMedicationReasonMapper.Result.NONE);
    }

    @Test
    @DisplayName("should refuse a reason of another drug or patient as a capture scope failure")
    void shouldRefuseAReasonOfAnotherDrugOrPatient_asACaptureScopeFailure() {
        var foreignDrug = reason(1, "icd9", "250", "1");
        foreignDrug.put("drugId", "52");
        var foreignPatient = reason(1, "icd9", "250", "1");
        foreignPatient.put("demographicNo", "28");
        var renamed = reason(1, "icd9", "250", "1");
        var shifted =
                new CdsExportCapturedTextRowDto(
                        "drugReason", CdsExportPatientTableSourceReader.key(2), renamed);
        for (var rows :
                List.of(
                        List.of(row(foreignDrug)),
                        List.of(row(foreignPatient)),
                        List.of(shifted),
                        List.of(row(renamed), row(renamed))))
            assertThatThrownBy(() -> mapper.map(27, drug(), rows))
                    .hasMessage("CDS_EXPORT_MEDICATION_REASON_SCOPE_INVALID");
        assertThatThrownBy(() -> mapper.map(28, drug(), List.of(row(reason(1, "icd9", "250", "1")))))
                .hasMessage("CDS_EXPORT_MEDICATION_REASON_SCOPE_INVALID");
    }
}
