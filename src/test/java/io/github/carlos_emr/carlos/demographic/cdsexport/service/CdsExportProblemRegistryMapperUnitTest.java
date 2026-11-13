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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDiagnosisCodeReferenceDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.*;

/**
 * Disease Registry entries and coded episodes become ProblemList items with every captured field
 * accounted for: deleted ones are accounted and not exported, unknown states hold the patient,
 * and no code, description, date precision or note is dropped or clipped.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportProblemRegistryMapperUnitTest {
    private final CdsExportProblemRegistryMapper mapper = new CdsExportProblemRegistryMapper();

    static CdsExportCapturedTextRowDto registry(long id, Map<String, String> supplied) {
        var fields = new HashMap<String, String>();
        fields.put("association", "0");
        fields.put("coding_system", "icd9");
        fields.put("demographic_no", "27");
        fields.put("dxresearch_code", "2500");
        fields.put("dxresearch_no", Long.toString(id));
        fields.put("providerNo", "999998");
        fields.put("start_date", "2024-03-05");
        fields.put("status", "A");
        fields.put("update_date", "2024-06-01 10:11:12");
        fields.putAll(supplied);
        assertThat(fields.keySet()).containsExactlyInAnyOrderElementsOf(Plan.DISEASE_REGISTRY.columns());
        return new CdsExportCapturedTextRowDto(
                CdsExportProblemRegistrySources.REGISTRY,
                CdsExportPatientTableSourceReader.key(id),
                fields);
    }

    static CdsExportCapturedTextRowDto precision(long id, long owner, String field, String format) {
        var fields = new HashMap<String, String>();
        fields.put("field_name", field);
        fields.put("format", format);
        fields.put("id", Long.toString(id));
        fields.put("table_id", Long.toString(owner));
        fields.put("table_name", "3");
        return new CdsExportCapturedTextRowDto(
                CdsExportProblemRegistrySources.PRECISION,
                CdsExportPatientTableSourceReader.key(id),
                fields);
    }

    static CdsExportCapturedTextRowDto episode(long id, Map<String, String> supplied) {
        var fields = new HashMap<String, String>();
        fields.put("code", "72892002");
        fields.put("codingSystem", "SnomedCore");
        fields.put("demographicNo", "27");
        fields.put("description", "Normal");
        fields.put("endDate", null);
        fields.put("id", Long.toString(id));
        fields.put("lastUpdateTime", "2024-01-02 03:04:05");
        fields.put("lastUpdateUser", "999998");
        fields.put("notes", "");
        fields.put("startDate", "2024-01-01");
        fields.put("status", "Current");
        fields.putAll(supplied);
        assertThat(fields.keySet()).containsExactlyInAnyOrderElementsOf(Plan.EPISODES.columns());
        return new CdsExportCapturedTextRowDto(
                CdsExportProblemRegistrySources.EPISODES,
                CdsExportPatientTableSourceReader.key(id),
                fields);
    }

    static CdsExportCapturedTextRowDto reference(
            String system, String code, String table, String id, String referenceCode, String description) {
        var value =
                new CdsExportDiagnosisCodeReferenceDto(
                        system, code, table, id, referenceCode, description);
        var fields = new HashMap<String, String>();
        fields.put("code", code);
        fields.put("coding_system", system);
        fields.put("description", description);
        fields.put("reference_code", referenceCode);
        fields.put("reference_id", id);
        fields.put("reference_table", table);
        return new CdsExportCapturedTextRowDto(
                CdsExportProblemRegistrySources.REFERENCES,
                CdsExportDiagnosisCodeReferenceSourceReader.key(value),
                fields);
    }

    private static void requireAccounted(CdsExportProblemRegistryMapper.Result result)
            throws IOException {
        var checked =
                new CdsExportNativeFieldAssessmentValidator()
                        .validate(result.rows(), result.fields(), 1000, () -> {});
        assertThat(checked.representable()).isTrue();
        assertThat(result.fields().values())
                .noneMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
    }

    private static JsonNode metadata(CdsExportNativeHistoryDto value) throws IOException {
        assertThat(value.metadata()).doesNotContain(" ");
        return new ObjectMapper().readTree(value.metadata());
    }

    private static CdsExportFieldAssessmentDto field(
            CdsExportProblemRegistryMapper.Result result, CdsExportCapturedTextRowDto row, String column) {
        return result.fields()
                .get(new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column));
    }

    @Test
    void shouldExportAnActiveEntry_withADottedIcd9CodeAndItsTableDescription() throws Exception {
        var entry = registry(5, Map.of());
        var description = reference("icd9", "2500", "icd9", "1301", "2500", "DIABETES MELLITUS");
        var result = mapper.mapRegistry(27, entry, List.of(), List.of(description));
        requireAccounted(result);
        var value = result.value();
        assertThat(value.category()).isEqualTo(CdsCategory.PROBLEM_LIST);
        assertThat(value.text())
                .containsEntry("ProblemStatus", "Active")
                .containsEntry("ProblemDiagnosisDescription", "DIABETES MELLITUS")
                .doesNotContainKeys("ResolutionDate", "Notes", "ProblemDescription");
        assertThat(value.code())
                .isEqualTo(
                        new CdsExportNativeHistoryDto.Code("icd9", "250.0", "DIABETES MELLITUS"));
        assertThat(value.dates())
                .containsExactly(
                        Map.entry(
                                "OnsetDate",
                                new CdsExportDateDto(CdsExportDateDto.Precision.DATE, "2024-03-05")));
        var context = metadata(value).path("diseaseRegistry");
        // The raw stored code, provider, last change and association travel exactly.
        assertThat(context.path("entry").path("dxresearch_code").asText()).isEqualTo("2500");
        assertThat(context.path("entry").path("update_date").asText())
                .isEqualTo("2024-06-01 10:11:12");
        assertThat(context.path("entry").has("demographic_no")).isFalse();
        assertThat(context.path("entry").has("dxresearch_no")).isFalse();
        assertThat(context.path("references").get(0).path("description").asText())
                .isEqualTo("DIABETES MELLITUS");
        assertThat(field(result, entry, "demographic_no").outcome()).isEqualTo(Outcome.OPERATIONAL);
        assertThat(field(result, entry, "dxresearch_code").destination())
                .startsWith("ProblemList/DiagnosisCode");
        assertThat(field(result, description, "description").outcome()).isEqualTo(Outcome.MAPPED);
    }

    @Test
    void shouldExportAResolvedEntry_withTheDayItWasResolvedAsResolutionDate() throws Exception {
        var entry = registry(5, Map.of("status", "C"));
        var result = mapper.mapRegistry(27, entry, List.of(), List.of());
        requireAccounted(result);
        assertThat(result.value().text()).containsEntry("ProblemStatus", "Resolved");
        assertThat(result.value().dates())
                .containsEntry(
                        "ResolutionDate",
                        new CdsExportDateDto(CdsExportDateDto.Precision.DATE, "2024-06-01"))
                .containsKey("OnsetDate");
        assertThat(field(result, entry, "update_date").destination())
                .startsWith("ProblemList/ResolutionDate");
    }

    @Test
    void shouldAccountADeletedEntryAndItsPrecision_withoutExportingThem() throws Exception {
        var entry = registry(5, Map.of("status", "D", "start_date", "0000-00-00"));
        var date = precision(9, 5, "4", "YYYY");
        var result = mapper.mapRegistry(27, entry, List.of(date), List.of());
        assertThat(result.exported()).isFalse();
        requireAccounted(result);
        assertThat(result.fields()).hasSize(9 + 5);
        assertThat(result.fields().values())
                .allSatisfy(
                        field -> {
                            assertThat(field.outcome()).isEqualTo(Outcome.OPERATIONAL);
                            assertThat(field.rule())
                                    .isEqualTo(
                                            CdsExportProblemRegistryMapper.DELETED_REGISTRY_ENTRY);
                        });
    }

    @Test
    void shouldKeepAnIcd10CodeAsStored_withItsTableDescription() throws Exception {
        var entry = registry(5, Map.of("coding_system", "icd10", "dxresearch_code", "E119"));
        var result =
                mapper.mapRegistry(
                        27,
                        entry,
                        List.of(),
                        List.of(reference("icd10", "E119", "icd10", "7", "E119", "Type 2 diabetes")));
        requireAccounted(result);
        assertThat(result.value().code())
                .isEqualTo(new CdsExportNativeHistoryDto.Code("icd10", "E119", "Type 2 diabetes"));
    }

    @Test
    void shouldExportACodeOfAnUnlistedSystem_withAnEmptyStandardDescription() throws Exception {
        var entry =
                registry(5, Map.of("coding_system", "SnomedCore", "dxresearch_code", "44054006"));
        var result = mapper.mapRegistry(27, entry, List.of(), List.of());
        requireAccounted(result);
        assertThat(result.value().code())
                .isEqualTo(new CdsExportNativeHistoryDto.Code("SnomedCore", "44054006", ""));
        assertThat(result.value().text()).doesNotContainKey("ProblemDiagnosisDescription");
    }

    @Test
    void shouldExportACodeMissingFromItsTable_withAnEmptyStandardDescription() throws Exception {
        var result = mapper.mapRegistry(27, registry(5, Map.of("dxresearch_code", "9999")), List.of(), List.of());
        requireAccounted(result);
        assertThat(result.value().code())
                .isEqualTo(new CdsExportNativeHistoryDto.Code("icd9", "999.9", ""));
    }

    @Test
    void shouldRefuseCodeTableRows_forASystemWithoutAReviewedTable() {
        var entry = registry(5, Map.of("coding_system", "SnomedCore", "dxresearch_code", "1"));
        assertThatThrownBy(
                        () ->
                                mapper.mapRegistry(
                                        27,
                                        entry,
                                        List.of(),
                                        List.of(reference("SnomedCore", "1", "icd9", "1", "1", "x"))))
                .hasMessage("CDS_EXPORT_PROBLEM_REGISTRY_MAPPING_SCOPE_INVALID");
    }

    @Test
    void shouldHoldACodeWithConflictingTableDescriptions_forReview() {
        var entry = registry(5, Map.of("coding_system", "msp", "dxresearch_code", "V03"));
        assertThatThrownBy(
                        () ->
                                mapper.mapRegistry(
                                        27,
                                        entry,
                                        List.of(),
                                        List.of(
                                                reference("msp", "V03", "diagnosticcode", "10010", "V03", "NEED FOR VACCINATION"),
                                                reference("msp", "V03", "diagnosticcode", "10011", "V03", "DISEASES"))))
                .hasMessage("CDS_EXPORT_NATIVE_PROBLEM_CODE_REFERENCE_AMBIGUOUS");
    }

    @Test
    void shouldUseOneDescription_whenDuplicateTableRowsAgree() throws Exception {
        var entry = registry(5, Map.of("coding_system", "msp", "dxresearch_code", "250"));
        var result =
                mapper.mapRegistry(
                        27,
                        entry,
                        List.of(),
                        List.of(
                                reference("msp", "250", "diagnosticcode", "3", "250", "Diabetes   mellitus   "),
                                reference("msp", "250", "diagnosticcode", "4", "250", "Diabetes mellitus")));
        requireAccounted(result);
        assertThat(result.value().code().description()).isEqualTo("Diabetes mellitus");
        assertThat(result.value().code().value()).isEqualTo("250");
        assertThat(metadata(result.value()).path("diseaseRegistry").path("references")).hasSize(2);
    }

    @Test
    void shouldExportTheStartDate_atTheYearOrYearMonthTheRegistryShows() throws Exception {
        var year = mapper.mapRegistry(27, registry(5, Map.of()), List.of(precision(9, 5, "4", "YYYY")), List.of());
        requireAccounted(year);
        assertThat(year.value().dates().get("OnsetDate"))
                .isEqualTo(new CdsExportDateDto(CdsExportDateDto.Precision.YEAR, "2024"));
        var month = mapper.mapRegistry(27, registry(5, Map.of()), List.of(precision(9, 5, "4", "YYYY-MM")), List.of());
        assertThat(month.value().dates().get("OnsetDate"))
                .isEqualTo(new CdsExportDateDto(CdsExportDateDto.Precision.YEAR_MONTH, "2024-03"));
        var full = mapper.mapRegistry(27, registry(5, Map.of()), List.of(precision(9, 5, "4", null)), List.of());
        requireAccounted(full);
        assertThat(full.value().dates().get("OnsetDate"))
                .isEqualTo(new CdsExportDateDto(CdsExportDateDto.Precision.DATE, "2024-03-05"));
    }

    @Test
    void shouldUseTheNewestPrecisionRow_asTheRegistryListReadsIt() throws Exception {
        var older = precision(9, 5, "4", "YYYY");
        var newest = precision(12, 5, "4", "YYYY-MM");
        var result = mapper.mapRegistry(27, registry(5, Map.of()), List.of(newest, older), List.of());
        requireAccounted(result);
        assertThat(result.value().dates().get("OnsetDate"))
                .isEqualTo(new CdsExportDateDto(CdsExportDateDto.Precision.YEAR_MONTH, "2024-03"));
        assertThat(field(result, older, "format").destination())
                .isEqualTo(CdsExportProblemRegistryMapper.RESIDUAL);
        assertThat(metadata(result.value()).path("diseaseRegistry").path("precision")).hasSize(2);
    }

    @Test
    void shouldHoldAnUnknownPrecisionMarkerOrField_forReview() {
        assertThatThrownBy(
                        () ->
                                mapper.mapRegistry(
                                        27,
                                        registry(5, Map.of()),
                                        List.of(precision(9, 5, "4", "DD")),
                                        List.of()))
                .hasMessage("CDS_EXPORT_NATIVE_PROBLEM_REGISTRY_DATE_PRECISION_REQUIRES_REVIEW");
        assertThatThrownBy(
                        () ->
                                mapper.mapRegistry(
                                        27,
                                        registry(5, Map.of()),
                                        List.of(precision(9, 5, "7", "YYYY")),
                                        List.of()))
                .hasMessage("CDS_EXPORT_NATIVE_PROBLEM_REGISTRY_DATE_PRECISION_REQUIRES_REVIEW");
    }

    @Test
    void shouldHoldAnUnknownOrMissingStatus_forReview() {
        var missing = new HashMap<String, String>();
        missing.put("status", null);
        for (var status : List.of(Map.of("status", "X"), missing))
            assertThatThrownBy(() -> mapper.mapRegistry(27, registry(5, status), List.of(), List.of()))
                    .hasMessage("CDS_EXPORT_NATIVE_PROBLEM_REGISTRY_STATUS_REQUIRES_REVIEW");
        assertThatThrownBy(
                        () -> mapper.mapEpisode(27, episode(6, Map.of("status", "Inactive")), List.of()))
                .hasMessage("CDS_EXPORT_NATIVE_EPISODE_STATUS_REQUIRES_REVIEW");
    }

    @Test
    void shouldRefuseAnEntryOfAnotherPatient_forMappingScope() {
        assertThatThrownBy(() -> mapper.mapRegistry(28, registry(5, Map.of()), List.of(), List.of()))
                .hasMessage("CDS_EXPORT_PROBLEM_REGISTRY_MAPPING_SCOPE_INVALID");
        assertThatThrownBy(
                        () ->
                                mapper.mapRegistry(
                                        27,
                                        registry(5, Map.of()),
                                        List.of(precision(9, 6, "4", "YYYY")),
                                        List.of()))
                .hasMessage("CDS_EXPORT_PROBLEM_REGISTRY_MAPPING_SCOPE_INVALID");
    }

    @Test
    void shouldExportACurrentEpisodeWithoutAnEndDate_withItsDescriptionAsTheProblem() throws Exception {
        var row = episode(6, Map.of());
        var result = mapper.mapEpisode(27, row, List.of());
        requireAccounted(result);
        var value = result.value();
        assertThat(value.text())
                .containsEntry("ProblemStatus", "Active")
                .containsEntry("ProblemDiagnosisDescription", "Normal")
                .doesNotContainKeys("Notes", "ProblemDescription");
        assertThat(value.dates()).containsOnlyKeys("OnsetDate");
        assertThat(value.code())
                .isEqualTo(new CdsExportNativeHistoryDto.Code("SnomedCore", "72892002", ""));
        assertThat(metadata(value).path("episode").path("entry").path("lastUpdateUser").asText())
                .isEqualTo("999998");
        assertThat(field(result, row, "endDate").outcome()).isEqualTo(Outcome.MAPPED);
    }

    @Test
    void shouldExportACompleteEpisode_withItsEndDateAndNotes() throws Exception {
        var result =
                mapper.mapEpisode(
                        27,
                        episode(
                                6,
                                Map.of(
                                        "status", "Complete",
                                        "endDate", "2024-09-30",
                                        "notes", "Delivered at term.\nNo complications.")),
                        List.of());
        requireAccounted(result);
        assertThat(result.value().text())
                .containsEntry("ProblemStatus", "Resolved")
                .containsEntry("Notes", "Delivered at term.\nNo complications.");
        assertThat(result.value().dates())
                .containsEntry(
                        "ResolutionDate",
                        new CdsExportDateDto(CdsExportDateDto.Precision.DATE, "2024-09-30"));
    }

    @Test
    void shouldKeepTheEpisodeDescriptionAsProblemDescription_whenTheTableDescribesTheCode()
            throws Exception {
        var row =
                episode(
                        6,
                        Map.of(
                                "codingSystem", "icd9",
                                "code", "4019",
                                "description", "High blood pressure, home readings"));
        var result =
                mapper.mapEpisode(
                        27,
                        row,
                        List.of(reference("icd9", "4019", "icd9", "88", "4019", "HYPERTENSION NOS")));
        requireAccounted(result);
        assertThat(result.value().text())
                .containsEntry("ProblemDiagnosisDescription", "HYPERTENSION NOS")
                .containsEntry("ProblemDescription", "High blood pressure, home readings");
        assertThat(result.value().code())
                .isEqualTo(new CdsExportNativeHistoryDto.Code("icd9", "401.9", "HYPERTENSION NOS"));
    }

    @Test
    void shouldCarryAnOverlongDescriptionAndNotesAndCode_inTheResidualInsteadOfClipping()
            throws Exception {
        String description = "D".repeat(252), notes = "Line one\n" + "n ".repeat(40_000);
        String code = "LOCAL-CODE-" + "9".repeat(20);
        var row =
                episode(
                        6,
                        Map.of(
                                "description", description,
                                "notes", notes,
                                "code", code,
                                "codingSystem", "local"));
        var result = mapper.mapEpisode(27, row, List.of());
        requireAccounted(result);
        assertThat(result.value().text())
                .doesNotContainKeys("ProblemDiagnosisDescription", "ProblemDescription", "Notes");
        assertThat(result.value().code()).isNull();
        var carried = metadata(result.value()).path("episode").path("entry");
        assertThat(carried.path("description").asText()).isEqualTo(description);
        assertThat(carried.path("notes").asText()).isEqualTo(notes);
        assertThat(carried.path("code").asText()).isEqualTo(code);
        for (String column : List.of("description", "notes", "code", "codingSystem"))
            assertThat(field(result, row, column).destination())
                    .isEqualTo(CdsExportProblemRegistryMapper.RESIDUAL);
    }

    @Test
    void shouldAccountADeletedEpisode_withoutExportingIt() throws Exception {
        var result = mapper.mapEpisode(27, episode(6, Map.of("status", "Deleted")), List.of());
        assertThat(result.exported()).isFalse();
        requireAccounted(result);
        assertThat(result.fields().values())
                .allSatisfy(field -> assertThat(field.rule()).isEqualTo(CdsExportProblemRegistryMapper.DELETED_EPISODE));
    }

    @Test
    void shouldHoldAnEpisodeDateThatIsNotACalendarDay_forReview() {
        assertThatThrownBy(
                        () -> mapper.mapEpisode(27, episode(6, Map.of("startDate", "0000-00-00")), List.of()))
                .hasMessage("CDS_EXPORT_NATIVE_EPISODE_DATE_REQUIRES_REVIEW");
    }

    @Test
    void shouldDotIcd9Codes_asTheLegacyExporterFormattedThem() {
        assertThat(CdsExportProblemRegistryMapper.icd9("2500")).isEqualTo("250.0");
        assertThat(CdsExportProblemRegistryMapper.icd9("25000")).isEqualTo("250.00");
        assertThat(CdsExportProblemRegistryMapper.icd9("V700")).isEqualTo("V70.0");
        assertThat(CdsExportProblemRegistryMapper.icd9("1234567")).isEqualTo("123.456.7");
        assertThat(CdsExportProblemRegistryMapper.icd9("250")).isEqualTo("250");
        assertThat(CdsExportProblemRegistryMapper.icd9("250.0")).isEqualTo("250.0");
    }
}
