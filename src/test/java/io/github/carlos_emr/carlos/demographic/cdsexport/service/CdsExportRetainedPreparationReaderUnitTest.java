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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsPreparationSeal;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsPreparedAllergyDefaults;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Original seals/default compatibility and transformed preparation values, without clinical database rereads. */
@Tag("unit") @Tag("cds-export")
class CdsExportRetainedPreparationReaderUnitTest {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final String run = UUID.randomUUID().toString(), unit = "a".repeat(64), manifest = "b".repeat(64);
    private final CdsExportRetainedPreparationReader reader = new CdsExportRetainedPreparationReader();
    @Test void everyPreparationRejectsASealedJsonNullRootWithTheSafeIntegrityCode() {
        String address = run + ":" + unit;
        assertThatThrownBy(() -> reader.allergy(fixture(CdsAllergyDto.class, "null", "AllergiesAndAdverseReactions"), address)).hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
        assertThatThrownBy(() -> reader.medication(fixture(CdsMedicationNativeDto.class, "null", "MedicationsAndTreatments"), address)).hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
        assertThatThrownBy(() -> reader.immunization(fixture(CdsImmunizationDto.class, "null", "Immunizations"), address)).hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
        assertThatThrownBy(() -> reader.appointment(fixture(CdsAppointmentNativeDto.class, "null", "Appointments"), address)).hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
        assertThatThrownBy(() -> reader.demographic(fixture(CdsDemographicNativeDto.class, "null", "Demographics"), address)).hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
        assertThatThrownBy(() -> reader.contact(fixture(CdsContactDto.class, "null", "Demographics_CONTACT"), address)).hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
    }
    @Test void contactChildrenUseTheirOwnExactSealedPreparationAndPreserveCombinedNativeNamesAndPurposeMarkers() throws Exception {
        var value = contactValue(); String payload = JSON.writeValueAsString(value);
        var source = fixture(CdsContactDto.class, payload, "Demographics_CONTACT"); var result = reader.contact(source, run + ":" + unit);
        assertThat(result.value()).isEqualTo(value); assertThat(result.sourceManifestSha256()).isEqualTo(manifest);
        assertThat(result.value().phones().get("R").extension()).isEqualTo("0012");
        assertThat(result.value().roles()).containsExactly("Multiple source purposes; see imported Contact details");
        assertThat(result.toString()).doesNotContain("Combined", "0012", "private", run);
        assertThat(source.tables().get("cds_import_preparation").values().iterator().next().fields().get("payload")).isEqualTo(payload);
    }
    @Test void contactCannotSubstituteItsParentCategoryAnotherPayloadTypeOrAnUnappliedUnit() throws Exception {
        String payload = JSON.writeValueAsString(contactValue());
        assertThatThrownBy(() -> reader.contact(fixture(CdsContactDto.class, payload, "Demographics"), run + ":" + unit)).hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
        assertThatThrownBy(() -> reader.contact(fixture(CdsDemographicNativeDto.class, payload, "Demographics_CONTACT"), run + ":" + unit)).hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
        var source = fixture(CdsContactDto.class, payload, "Demographics_CONTACT"); var nativeUnit = source.tables().get("cds_import_unit").values().iterator().next(); var changed = new LinkedHashMap<>(nativeUnit.fields()); changed.put("state", "REGISTERED");
        assertThatThrownBy(() -> reader.contact(replace(source, "cds_import_unit", nativeUnit.recordKey(), changed), run + ":" + unit)).hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
        assertThatThrownBy(() -> reader.contact(source, UUID.randomUUID() + ":" + unit)).hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
    }
    @Test void contactRequiresItsOriginalSealAndMatchingSourceOptionsAndRuleset() throws Exception {
        var source = fixture(CdsContactDto.class, JSON.writeValueAsString(contactValue()), "Demographics_CONTACT"); var prepared = source.tables().get("cds_import_preparation").values().iterator().next();
        for (var update : List.of(Map.of("payload", prepared.fields().get("payload") + " "), Map.of("source_digest", "f".repeat(64)), Map.of("options_digest", "f".repeat(64)), Map.of("ruleset_digest", "f".repeat(64)))) {
            var changed = new LinkedHashMap<>(prepared.fields()); changed.putAll(update);
            assertThatThrownBy(() -> reader.contact(replace(source, "cds_import_preparation", prepared.recordKey(), changed), run + ":" + unit)).hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
        }
    }
    @Test void contactRejectsMissingCreatorFieldsCoercedBooleansAndUnboundedRoleListsUnderRecomputedSeals() throws Exception {
        String payload = JSON.writeValueAsString(contactValue());
        for (String changed : List.of(payload.replace("\"emergencyContact\":true", "\"emergencyContact\":\"true\""), payload.replace("\"roles\":[\"Multiple source purposes; see imported Contact details\"]", "\"roles\":[\"A\",\"B\"]"), payload + " {}"))
            assertThatThrownBy(() -> reader.contact(fixture(CdsContactDto.class, changed, "Demographics_CONTACT"), run + ":" + unit)).hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
        var missing = JSON.valueToTree(contactValue()); ((com.fasterxml.jackson.databind.node.ObjectNode) missing).remove("substituteDecisionMaker");
        assertThatThrownBy(() -> reader.contact(fixture(CdsContactDto.class, JSON.writeValueAsString(missing), "Demographics_CONTACT"), run + ":" + unit)).hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
    }
    @Test void contactRejectsUnknownNativePhoneSlotsAndMissingNumbersRatherThanGuessing() throws Exception {
        var value = contactValue();
        for (var phones : List.of(Map.of("OTHER", new CdsContactDto.Phone("123", null)), Map.of("R", new CdsContactDto.Phone(null, "0012")))) {
            var invalid = new CdsContactDto(value.firstName(), value.lastName(), value.email(), value.note(), phones, value.roles(), value.emergencyContact(), value.substituteDecisionMaker(), value.diagnostics());
            assertThatThrownBy(() -> reader.contact(fixture(CdsContactDto.class, JSON.writeValueAsString(invalid), "Demographics_CONTACT"), run + ":" + unit)).hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
        }
    }
    private static CdsContactDto contactValue() {
        return new CdsContactDto("Combined first middle 😀", "Fixture", "source@example.test", "private source note", Map.of("R", new CdsContactDto.Phone("613-555-0101", "0012"), "W", new CdsContactDto.Phone("613-555-0102", "AB")), List.of("Multiple source purposes; see imported Contact details"), true, null, List.of());
    }
    @Test void shouldReadTheExactHistoricalAllergyPreparation_andApplyLegacyDefaultsOnlyAfterItsOriginalSeal() throws Exception {
        var value = new CdsAllergyDto("Synthetic agent", "DR", null, null, null, "Literal café 😀", "LT", null, "Source-only note", CdsTemporalDto.unknown(), CdsTemporalDto.unknown());
        var tree = JSON.valueToTree(value); ((com.fasterxml.jackson.databind.node.ObjectNode)tree).remove(List.of("archived", "ageOfOnset", "onsetOfReaction"));
        String original = JSON.writeValueAsString(tree); var source = fixture(CdsAllergyDto.class, original, "AllergiesAndAdverseReactions");
        var result = reader.allergy(source, run+":"+unit);
        assertThat(result.value()).isEqualTo(value); assertThat(result.value().archived()).isFalse();
        assertThat(source.tables().get("cds_import_preparation").values().iterator().next().fields().get("payload")).isEqualTo(original);
        assertThat(result.sourceManifestSha256()).isEqualTo(manifest); assertThat(result.unitAddress()).isEqualTo(run+":"+unit);
        assertThat(result.toString()).doesNotContain("Literal", run, "Source-only");
    }
    @Test void shouldRejectChangedBytesEvenWhenTheyDecodeToTheSameLogicalObject() throws Exception {
        var source = allergy(); var prepared = source.tables().get("cds_import_preparation").values().iterator().next();
        var changed = new LinkedHashMap<>(prepared.fields()); changed.put("payload", prepared.fields().get("payload")+" ");
        assertThatThrownBy(() -> reader.allergy(replace(source, "cds_import_preparation", prepared.recordKey(), changed), run+":"+unit))
                .hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
    }
    @Test void shouldRefuseForeignUnitSourceOptionsRulesetTypeAndUnappliedFacts() throws Exception {
        var source = allergy(); var prepared = source.tables().get("cds_import_preparation").values().iterator().next();
        for (var update : List.of(Map.of("run_id", UUID.randomUUID().toString()), Map.of("unit_key", "f".repeat(64)),
                Map.of("source_digest", "f".repeat(64)), Map.of("options_digest", "f".repeat(64)), Map.of("ruleset_digest", "f".repeat(64)),
                Map.of("payload_type", CdsMedicationNativeDto.class.getName()))) {
            var changed = new LinkedHashMap<>(prepared.fields()); changed.putAll(update);
            assertThatThrownBy(() -> reader.allergy(replace(source, "cds_import_preparation", prepared.recordKey(), changed), run+":"+unit))
                    .hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
        }
        var nativeUnit = source.tables().get("cds_import_unit").values().iterator().next(); var changed = new LinkedHashMap<>(nativeUnit.fields()); changed.put("state", "REGISTERED");
        assertThatThrownBy(() -> reader.allergy(replace(source, "cds_import_unit", nativeUnit.recordKey(), changed), run+":"+unit)).hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
        assertThatThrownBy(() -> reader.allergy(source, UUID.randomUUID()+":"+unit)).hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
        assertThatThrownBy(() -> reader.medication(source, run+":"+unit)).hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
    }
    @Test void shouldRejectDuplicateKeysTrailingValuesAndWrongOptionalTypes_evenUnderAValidRecomputedSeal() throws Exception {
        var source = allergy(); String payload = source.tables().get("cds_import_preparation").values().iterator().next().fields().get("payload");
        for (String changed : List.of(payload.substring(0, payload.length()-1)+",\"description\":\"Duplicate\"}", payload+" {}",
                payload.substring(0, payload.length()-1)+",\"archived\":\"true\"}", payload.substring(0, payload.length()-1)+",\"ageOfOnset\":12}")) {
            assertThatThrownBy(() -> reader.allergy(fixture(CdsAllergyDto.class, changed, "AllergiesAndAdverseReactions"), run+":"+unit))
                    .hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
        }
        var omitted = JSON.readTree(payload); ((com.fasterxml.jackson.databind.node.ObjectNode)omitted).remove("reaction");
        assertThatThrownBy(() -> reader.allergy(fixture(CdsAllergyDto.class, JSON.writeValueAsString(omitted), "AllergiesAndAdverseReactions"), run+":"+unit))
                .hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
    }
    @Test void shouldRetainTheAppliedSyntheticDateShift_andNotRevertToOriginalLogicalXml() throws Exception {
        var shifted = new CdsTemporalDto(CdsTemporalDto.Precision.FULL_DATE, "2020-02-29", java.time.LocalDate.of(2020, 3, 1),
                java.time.LocalDate.of(2020, 3, 1), null, null, 1);
        var value = new CdsAllergyDto("Synthetic", null, null, null, null, null, null, null, null, shifted, CdsTemporalDto.unknown());
        var result = reader.allergy(fixture(CdsAllergyDto.class, JSON.writeValueAsString(value), "AllergiesAndAdverseReactions"), run+":"+unit);
        assertThat(result.value().start().original()).isEqualTo("2020-02-29");
        assertThat(result.value().start().earliest()).isEqualTo(java.time.LocalDate.of(2020, 3, 1)); assertThat(result.value().start().appliedShiftDays()).isEqualTo(1);
    }
    @Test void shouldReadMedicationSourceAndTheOriginalPrescriberMatch_withoutTreatingTheExportActorAsAuthor() throws Exception {
        var source = new CdsMedicationDto("Synthetic", "00123456", null, "5", "mg", "0.5-1", "tablet", null, "PO", "BID", "30", null, "12", null, 2,
                true, false, null, null, null, "CHRON", "Active", CdsMedicationDto.Currentness.CURRENT, "Original Physician", "001234", "Original", "Physician", null, null, null, null, null, null,
                CdsTemporalDto.unknown(), CdsTemporalDto.unknown(), false, false, List.of());
        var value = new CdsMedicationNativeDto(source, new CdsProviderMatchDto("0é27", CdsProviderMatchDto.Outcome.MATCHED_IDENTIFIERS));
        var result = reader.medication(fixture(CdsMedicationNativeDto.class, JSON.writeValueAsString(value), "MedicationsAndTreatments"), run+":"+unit);
        assertThat(result.value()).isEqualTo(value); assertThat(result.value().source().prescriberFirstName()).isEqualTo("Original");
    }
    @Test void shouldLeaveTheInputTreeUnmodified_whenApplyingTheSharedLegacyDefaults() throws Exception {
        var tree = JSON.readTree("{\"description\":\"Synthetic\"}"); var result = CdsPreparedAllergyDefaults.apply(tree);
        assertThat(tree.has("archived")).isFalse(); assertThat(result.path("archived").isBoolean()).isTrue(); assertThat(result.path("archived").booleanValue()).isFalse();
        assertThat(result.has("ageOfOnset")).isTrue(); assertThat(result.get("ageOfOnset").isNull()).isTrue();
    }
    @Test void shouldReadExactSealedImmunizationDateAndRequiredRefusalFlag() throws Exception {
        var value=new CdsImmunizationDto("Original vaccine", "MMR", "Manufacturer", "00Lot", "PO", "Site", "0.5mL", "00123456", "DIN", true, "Series", "Note", CdsTemporalDto.unknown());
        var source=fixture(CdsImmunizationDto.class, JSON.writeValueAsString(value), "Immunizations");
        assertThat(reader.immunization(source, run+":"+unit).value()).isEqualTo(value);
        assertThatThrownBy(()-> reader.allergy(source, run+":"+unit)).hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
        assertThatThrownBy(()-> reader.immunization(allergy(), run+":"+unit)).hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
    }
    @Test void shouldRejectMissingNullOrCoercedRequiredImmunizationFactsUnderRecomputedSeals() throws Exception {
        var value=new CdsImmunizationDto("Original vaccine", null, null, null, null, null, null, null, null, true, null, null, CdsTemporalDto.unknown());
        String payload=JSON.writeValueAsString(value);
        for (String changed:List.of(payload.replace("\"refused\":true", "\"refused\":null"), payload.replace("\"refused\":true", "\"refused\":\"true\""),
                payload.replace("\"refused\":true,", ""), payload.replace("\"date\":"+JSON.writeValueAsString(value.date()), "\"date\":null"))) {
            assertThatThrownBy(()-> reader.immunization(fixture(CdsImmunizationDto.class, changed, "Immunizations"), run+":"+unit)).hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
        }
    }
    @Test void shouldRestoreTheExactPreparedDemographicDateAndMatchedClinicalRelationsWithoutCurrentOptions() throws Exception {
        var original = demographicValue(); String payload = JSON.writeValueAsString(original);
        var index = fixture(CdsDemographicNativeDto.class, payload, "Demographics");
        var result = reader.demographic(index, run + ":" + unit);
        assertThat(result.value()).isEqualTo(original); assertThat(result.value().birthDate()).isEqualTo("2020-03-01");
        assertThat(result.value().details().roster().enrolledProvider()).isEqualTo("0é27");
        assertThat(result.value().primaryProvider()).isEqualTo("0é27");
        assertThat(index.tables().get("cds_import_preparation").values().iterator().next().fields().get("payload")).isEqualTo(payload);
        assertThat(result.toString()).doesNotContain("Source-only", "2020", "0é27");
    }
    @Test void shouldRejectDemographicCategoryOrTypeSubstitutionInsteadOfReinterpretingOtherPreparations() throws Exception {
        String payload = JSON.writeValueAsString(demographicValue());
        assertThatThrownBy(() -> reader.demographic(fixture(CdsDemographicNativeDto.class, payload, "AllergiesAndAdverseReactions"), run + ":" + unit))
                .hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
        assertThatThrownBy(() -> reader.demographic(fixture(CdsAllergyDto.class, payload, "Demographics"), run + ":" + unit))
                .hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
        assertThatThrownBy(() -> reader.demographic(allergy(), run + ":" + unit)).hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
    }
    @Test void shouldRefuseIncompleteDemographicPreparationShapesEvenUnderRecomputedSeals() throws Exception {
        var original = JSON.valueToTree(demographicValue());
        for (String field : List.of("birthDate", "firstName", "scalars", "relations")) {
            var missing = original.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) missing).remove(field);
            assertThatThrownBy(() -> reader.demographic(fixture(CdsDemographicNativeDto.class, JSON.writeValueAsString(missing), "Demographics"), run + ":" + unit))
                    .hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
        }
    }
    @Test void shouldRefuseInvalidPreparedDemographicDatesRatherThanInventCalendarDefaults() throws Exception {
        String payload = JSON.writeValueAsString(demographicValue());
        for (String invalid : List.of("2020-02-30", "2020-03", "0000-01-01")) {
            String changed = payload.replace("2020-03-01", invalid);
            assertThatThrownBy(() -> reader.demographic(fixture(CdsDemographicNativeDto.class, changed, "Demographics"), run + ":" + unit))
                    .hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
        }
    }
    private CdsDemographicNativeDto demographicValue() {
        var details = new CdsDemographicDetailsDto("eng", "2024-02-29", "2025-01-01", "Source-only café 😀 note",
                new CdsDemographicDetailsDto.Enrollment("RO", "2020-03-01", null, null, "0é27"), 1, List.of());
        return new CdsDemographicNativeDto("old-vendor-27", "Synthetic", "Fixture", "2020-03-01", "0012345678", "ON", "U", "AC",
                Map.of("ChartNumber", "000123", "PreferredOfficialLanguage", "ENG", "HealthCard/Version", "AB"),
                null, null, null, null, null, "0é27", details, CdsDemographicRelationsDto.empty(), List.of());
    }
    private CdsExportRetainedIndexDto allergy() throws Exception {
        return fixture(CdsAllergyDto.class, JSON.writeValueAsString(new CdsAllergyDto("Synthetic", null, null, null, null, null, null, null, null, CdsTemporalDto.unknown(), CdsTemporalDto.unknown())), "AllergiesAndAdverseReactions");
    }
    private static CdsHistoryNativeDto historyValue(io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory category) {
        String issue = switch (category) {
            case PERSONAL_HISTORY -> "SocHistory"; case FAMILY_HISTORY -> "FamHistory"; case PAST_HEALTH -> "MedHistory";
            case PROBLEM_LIST -> "Concerns"; case RISK_FACTORS -> "RiskFactors"; case ALERTS_AND_SPECIAL_NEEDS -> "Reminders";
            case CLINICAL_NOTES -> null; default -> throw new IllegalArgumentException();
        };
        String field = switch (category) {
            case PERSONAL_HISTORY -> null; case FAMILY_HISTORY, RISK_FACTORS -> "StartDate"; case PAST_HEALTH -> "OnsetOrEventDate";
            case PROBLEM_LIST -> "OnsetDate"; case ALERTS_AND_SPECIAL_NEEDS -> "DateActive"; case CLINICAL_NOTES -> "EventDateTime";
            default -> throw new IllegalArgumentException();
        };
        return new CdsHistoryNativeDto("Complete historical café 😀", issue, null, null, null, field == null ? null : CdsTemporalDto.unknown(), field, List.of());
    }
    @Test void shouldReadEachSealedHistoryCategory_withoutTurningUnknownDatesIntoInventedNativeTimes() throws Exception {
        for (var category : List.of(io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory.PERSONAL_HISTORY,
                io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory.FAMILY_HISTORY,
                io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory.PAST_HEALTH,
                io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory.PROBLEM_LIST,
                io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory.RISK_FACTORS,
                io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory.ALERTS_AND_SPECIAL_NEEDS,
                io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory.CLINICAL_NOTES)) {
            var value = historyValue(category); var source = fixture(CdsHistoryNativeDto.class, JSON.writeValueAsString(value), category.wireName());
            var evidence = reader.history(source, run + ":" + unit, category);
            assertThat(evidence.value()).isEqualTo(value); assertThat(evidence.value().observationMillis()).isNull();
            assertThat(evidence.toString()).doesNotContain("historical", "café", run);
        }
    }
    @Test void shouldRefuseHistoryCategoryAndIssueSubstitution_evenUnderARecomputedPayloadSeal() throws Exception {
        var category = io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory.FAMILY_HISTORY;
        var value = historyValue(category); var changed = new CdsHistoryNativeDto(value.content(), "SocHistory", null, null, null, value.observationDate(), value.observationField(), List.of());
        assertThatThrownBy(() -> reader.history(fixture(CdsHistoryNativeDto.class, JSON.writeValueAsString(changed), category.wireName()), run + ":" + unit, category))
                .hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
        assertThatThrownBy(() -> reader.history(fixture(CdsHistoryNativeDto.class, JSON.writeValueAsString(value), "PersonalHistory"), run + ":" + unit, category))
                .hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
    }
    @Test void shouldRefuseMissingDateDependencies_andAnUnrelatedHistoryClockField() throws Exception {
        var category = io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory.CLINICAL_NOTES;
        for (String field : Arrays.asList(null, "", "ProcedureDate")) {
            var changed = new CdsHistoryNativeDto("note", null, null, null, null, CdsTemporalDto.unknown(), field, List.of());
            assertThatThrownBy(() -> reader.history(fixture(CdsHistoryNativeDto.class, JSON.writeValueAsString(changed), category.wireName()), run + ":" + unit, category))
                    .hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
        }
    }
    @Test void shouldRejectMissingCreatorFields_andJsonNullHistoryPreparations() throws Exception {
        var category = io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory.CLINICAL_NOTES;
        var missing = JSON.valueToTree(historyValue(category)); ((com.fasterxml.jackson.databind.node.ObjectNode) missing).remove("createdMillis");
        for (String payload : List.of("null", JSON.writeValueAsString(missing)))
            assertThatThrownBy(() -> reader.history(fixture(CdsHistoryNativeDto.class, payload, category.wireName()), run + ":" + unit, category))
                    .hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
    }
    @Test void shouldRequireOriginalHistorySeal_beforeDecodingTheCapturedPreparation() throws Exception {
        var category = io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory.CLINICAL_NOTES;
        var source = fixture(CdsHistoryNativeDto.class, JSON.writeValueAsString(historyValue(category)), category.wireName());
        var row = source.tables().get("cds_import_preparation").values().iterator().next(); var fields = new LinkedHashMap<>(row.fields()); fields.put("payload", fields.get("payload") + " ");
        assertThatThrownBy(() -> reader.history(replace(source, "cds_import_preparation", row.recordKey(), fields), run + ":" + unit, category))
                .hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
    }
    @Test void shouldRefuseNonHistoryCategories_beforeInterpretingAnyPreparation() {
        for (var category : List.of(io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory.DEMOGRAPHICS,
                io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory.REPORTS))
            assertThatThrownBy(() -> reader.history(null, run + ":" + unit, category)).hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
        assertThatThrownBy(() -> reader.history(null, run + ":" + unit, null)).hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
    }
    private CdsExportRetainedIndexDto fixture(Class<?> type, String payload, String category) {
        String identity=CdsImportKeys.hash(run, unit), source="c".repeat(64), options="d".repeat(64), ruleset="e".repeat(64);
        var prepared = new LinkedHashMap<String, String>(Map.of("id", identity, "run_id", run, "unit_key", unit, "payload_type", type.getName(), "payload", payload,
                "source_digest", source, "options_digest", options, "ruleset_digest", ruleset, "digest", CdsPreparationSeal.digest(type.getName(), source, options, ruleset, payload)));
        var tables = Map.of("cds_import_preparation", Map.of(identity, row("cds_import_preparation", identity, prepared)),
                "cds_import_unit", Map.of(identity, row("cds_import_unit", identity, Map.of("run_id", run, "unit_key", unit, "source_digest", source, "category", category, "state", "COMMITTED"))),
                "cds_import_run", Map.of(run, row("cds_import_run", run, Map.of("id", run, "options_digest", options, "ruleset_digest", ruleset))));
        return new CdsExportRetainedIndexDto(manifest, 3, payload.length(), tables, Set.of(run+":"+unit), Map.of(), Map.of());
    }
    private CdsExportRetainedIndexDto replace(CdsExportRetainedIndexDto source, String table, String key, Map<String, String> fields) {
        var tables = new LinkedHashMap<>(source.tables()); tables.put(table, Map.of(key, row(table, key, fields)));
        return new CdsExportRetainedIndexDto(source.sourceManifestSha256(), source.metadataRows(), source.metadataBytes(), tables, source.appliedUnits(), source.references(), source.nativeParents());
    }
    private static CdsExportCapturedTextRowDto row(String table, String key, Map<String, String> fields) { return new CdsExportCapturedTextRowDto(table, key, fields); }
}
