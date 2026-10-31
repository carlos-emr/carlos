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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsMedicationDto.Currentness;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto.Precision;
import io.github.carlos_emr.carlos.commn.model.CdsClinicalException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class CdsClinicalNormalizationServiceUnitTest {
    @TempDir Path directory;
    private final CdsClinicalNormalizationService service = new CdsClinicalNormalizationService(new CdsFieldParser());

    @ParameterizedTest
    @CsvSource({"New,NEW", "Active,CURRENT", "Suspended,SUSPENDED", "Aborted,HISTORICAL",
            "Completed,HISTORICAL", "Obsolete,HISTORICAL", "Nullified,HISTORICAL"})
    void shouldRetainEachLifecycleMeaning_whenStatusIsSupplied(String status, Currentness expected) throws Exception {
        var value = service.medication(medication(Map.of("PrescriptionStatus", status)));
        assertThat(value.prescriptionStatus()).isEqualTo(status);
        assertThat(value.currentness()).isEqualTo(expected);
        assertThat(value.start().precision()).isEqualTo(Precision.UNKNOWN);
        assertThat(value.past()).isNull();
    }

    @Test
    void shouldKeepUnknownCurrentness_whenDatesAndHistoryAreMissing() throws Exception {
        var value = service.medication(medication(Map.of()));
        assertThat(value.currentness()).isEqualTo(Currentness.UNKNOWN);
        assertThat(value.longTerm()).isNull();
        assertThat(value.compliance()).isNull();
        assertThat(value.substitutionNotAllowed()).isNull();
        assertThat(value.nonAuthoritative()).isNull();
        assertThat(value.diagnostics()).extracting("code").contains("CDS_RX_CURRENTNESS_UNKNOWN");
    }

    @Test
    void shouldRefuseContradictoryHistory_whenActiveAndPastAreBothAsserted() throws Exception {
        var source = medication(Map.of("PrescriptionStatus", "Active", "PastMedications/ynIndicatorsimple", "Y"));
        assertThatThrownBy(() -> service.medication(source)).isInstanceOf(CdsClinicalException.class)
                .hasMessageContaining("CDS_MEDICATION_STATE_CONFLICT");
    }

    @Test
    void shouldPreserveIndependentMedicationDimensions_whenSeveralIndicatorsAreSupplied() throws Exception {
        var value = service.medication(medication(Map.of("PrescriptionStatus", "Active",
                "PatientCompliance/boolean", "false", "NonAuthoritativeIndicator", "y",
                "LongTermMedication/ynIndicatorsimple", "Y", "TreatmentType", "PRN Long-term")));
        assertThat(value.currentness()).isEqualTo(Currentness.CURRENT);
        assertThat(value.compliance()).isFalse();
        assertThat(value.nonAuthoritative()).isTrue();
        assertThat(value.longTerm()).isTrue();
        assertThat(value.treatmentType()).isEqualTo("PRN Long-term");
    }

    @Test
    void shouldNotExtendAuthorityToBooleanLexicals_whenIndicatorIsPlainText() throws Exception {
        assertThatThrownBy(() -> service.medication(medication(Map.of("NonAuthoritativeIndicator", "1"))))
                .isInstanceOf(CdsClinicalException.class).hasMessageContaining("CDS_INDICATOR_INVALID");
    }

    @Test
    void shouldRetainDoseAndDurationsIndependently_whenFractionsAndRefillsAreSupplied() throws Exception {
        var value = service.medication(medication(Map.of("Dosage", "1/2–1", "DosageUnitOfMeasure", "tablet",
                "Duration", "30", "RefillDuration", "15", "Quantity", "12.5", "RefillQuantity", "12",
                "NumberOfRefills", "3", "Strength/Amount", "10/5", "Strength/UnitOfMeasure", "mg/mL")));
        assertThat(value.dose()).isEqualTo("1/2–1");
        assertThat(value.strengthAmount()).isEqualTo("10/5");
        assertThat(value.quantity()).isEqualTo("12.5");
        assertThat(value.durationDays()).isEqualTo("30");
        assertThat(value.refillDurationDays()).isEqualTo(15);
        assertThat(value.refillQuantity()).isEqualTo(12);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2 weeks", "10mg", "1-2", "-1"})
    void shouldRefuseNoncoreDuration_whenNoVendorAdapterWasSelected(String duration) throws Exception {
        assertThatThrownBy(() -> service.medication(medication(Map.of("Duration", duration))))
                .isInstanceOf(CdsClinicalException.class).hasMessageContaining("CDS_NUMERIC_VALUE_REQUIRES_REVIEW");
    }

    @Test
    void shouldRefuseNumericOverflow_whenTargetCannotRepresentRefills() throws Exception {
        assertThatThrownBy(() -> service.medication(medication(Map.of("NumberOfRefills", "99999999999999999999"))))
                .isInstanceOf(CdsClinicalException.class).hasMessageContaining("CDS_TARGET_NUMBER_CAPACITY_EXCEEDED");
    }

    @ParameterizedTest
    @CsvSource({"AL,DR", "AR,ND", "AR,UK"})
    void shouldRetainReactionAndPropertySeparately_whenAllergySemanticsAreSupplied(String type, String property) throws Exception {
        var source = CdsClinicalFixture.record(directory, "AllergiesAndAdverseReactions", Map.of(
                "OffendingAgentDescription", "Synthetic agent", "ReactionType", type,
                "PropertyOfOffendingAgent", property, "Severity", "MI", "Code/CodeType", "DIN", "Code/CodeValue", "01234567"));
        var value = service.allergy(source);
        assertThat(value.reactionType()).isEqualTo(type);
        assertThat(value.property()).isEqualTo(property);
        assertThat(value.severity()).isEqualTo("MI");
        assertThat(value.code()).isEqualTo("01234567");
        assertThat(value.codeSystem()).isEqualTo("DIN");
    }

    @Test
    void shouldNotDefaultReactionType_whenSourceOmitsIt() throws Exception {
        var value = service.allergy(CdsClinicalFixture.record(directory, "AllergiesAndAdverseReactions",
                Map.of("OffendingAgentDescription", "Synthetic agent")));
        assertThat(value.reactionType()).isNull();
        assertThat(value.property()).isNull();
        assertThat(value.start().precision()).isEqualTo(Precision.UNKNOWN);
    }

    @Test
    void shouldRejectSeverityAsProperty_whenWorkbookCrossReferenceIsWrong() throws Exception {
        var source = CdsClinicalFixture.record(directory, "AllergiesAndAdverseReactions", Map.of(
                "OffendingAgentDescription", "Synthetic agent", "PropertyOfOffendingAgent", "MI"));
        assertThatThrownBy(() -> service.allergy(source)).isInstanceOf(CdsClinicalException.class)
                .hasMessageContaining("CDS_CODE_INVALID");
    }

    @ParameterizedTest
    @CsvSource({"N,false", "false,false", "0,false", "Y,true", "true,true", "1,true"})
    void shouldRetainAdministrationAssertion_whenDateIsUnknown(String value, boolean refused) throws Exception {
        String branch = value.equals("Y") || value.equals("N") ? "ynIndicatorsimple" : "boolean";
        var source = CdsClinicalFixture.record(directory, "Immunizations", Map.of(
                "ImmunizationName", "Synthetic vaccine", "RefusedFlag/" + branch, value));
        var normalized = service.immunization(source);
        assertThat(normalized.refused()).isEqualTo(refused);
        assertThat(normalized.date().precision()).isEqualTo(Precision.UNKNOWN);
    }

    @Test
    void shouldRefuseMissingAdministrationFlag_whenDateIsPresent() throws Exception {
        var source = CdsClinicalFixture.record(directory, "Immunizations", Map.of(
                "ImmunizationName", "Synthetic vaccine", "Date/FullDate", "2020-01-01"));
        assertThatThrownBy(() -> service.immunization(source)).isInstanceOf(CdsClinicalException.class)
                .hasMessageContaining("CDS_INDICATOR_REQUIRED");
    }

    @Test
    void shouldCollapseOnlyTokenWhitespace_whenIndicatorAndDateUseTokenTypes() throws Exception {
        var source = CdsClinicalFixture.record(directory, "Immunizations", Map.of(
                "ImmunizationName", "Synthetic  vaccine", "RefusedFlag/ynIndicatorsimple", " \tN\n ",
                "Date/YearOnly", " 2020\n"));
        var value = service.immunization(source);
        assertThat(value.refused()).isFalse();
        assertThat(value.name()).isEqualTo("Synthetic  vaccine");
        assertThat(value.date().original()).isEqualTo("2020");
        assertThat(value.date().precision()).isEqualTo(Precision.YEAR);
    }

    @Test
    void shouldRetainValidUnicodeCapacity_whenNotesContainSupplementaryCharacters() throws Exception {
        String notes = "\uD83D\uDE00".repeat(32768);
        var value = service.medication(medication(Map.of("Notes", notes)));
        assertThat(value.providerNotes()).isEqualTo(notes);
        assertThat(value.providerNotes().codePointCount(0, value.providerNotes().length())).isEqualTo(32768);
    }

    private io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto medication(Map<String, String> extra)
            throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("DrugName", "Synthetic source medicine");
        values.putAll(extra);
        return CdsClinicalFixture.record(directory, "MedicationsAndTreatments", values);
    }
}
