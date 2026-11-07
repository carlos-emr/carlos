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

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportMedicationDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportIdentityDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeFieldKeyDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportXmlLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsClinicalNormalizationService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsFieldParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsXmlParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit") @Tag("cds-export")
class CdsExportMedicationMapperUnitTest {
    @TempDir Path directory;
    private final CdsExportMedicationMapper mapper = new CdsExportMedicationMapper();
    @Test void shouldPreserveTheDistinctClinicalValuesPrecisionAndNullableFlags_throughSchemaAndReceivingImporter() throws Exception {
        var fields = baseline(); fields.putAll(Map.of("written_date", "1965-01-01", "rx_date", "2024-02-01", "long_term", "1",
                "patient_compliance", "0", "nosubs", "1", "non_authoritative", "0", "quantity", "12.5", "refill_duration", "30", "refill_quantity", "12"));
        var mapped = mapper.map(27, medication(fields), List.of(precision(11, 51, 3, "YYYY"), precision(12, 51, 5, "YYYY-MM")));
        assertThat(mapped.nativeRowRepresentable()).isTrue(); assertThat(mapped.fields()).hasSize(74);
        var patient = new LinkedHashMap<String, String>(); CdsExportDemographicRowDto.COLUMNS.forEach(column -> patient.put(column, null));
        patient.putAll(Map.of("first_name", "Synthetic", "last_name", "Fixture", "year_of_birth", "1980", "month_of_birth", "01",
                "date_of_birth", "01", "patient_status", "AC", "sex", "U"));
        var demographics = new CdsExportDemographicMapper().map(new CdsExportDemographicRowDto(27, patient));
        var sections = List.of(
                new CdsExportXmlComposer.Section(new CdsExportIdentityDto("a".repeat(64), CdsCategory.DEMOGRAPHICS, "b".repeat(64)),
                        writer -> new CdsExportDemographicXmlWriter().write(demographics, writer)),
                new CdsExportXmlComposer.Section(new CdsExportIdentityDto("a".repeat(64), CdsCategory.MEDICATIONS_AND_TREATMENTS, "c".repeat(64)),
                        writer -> new CdsExportMedicationXmlWriter().write(mapped.value(), writer)));
        var output = new ByteArrayOutputStream(); var selected = Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.MEDICATIONS_AND_TREATMENTS);
        new CdsExportXmlComposer().compose(output, "d".repeat(64), "a".repeat(64), "2026.08.0-test", CdsLogicalProfile.ONTARIO_MD_5_2,
                selected, sections.iterator(), ignored -> { }, 1L << 20, () -> { });
        new CdsExportXmlValidator().validate(new ByteArrayInputStream(output.toByteArray()), "27", selected, "2026.08.0-test",
                CdsLogicalProfile.ONTARIO_MD_5_2, CdsExportXmlLimitsDto.defaults(), ignored -> { }, () -> { });
        var file = Files.write(directory.resolve("patient.xml"), output.toByteArray()); var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser().parse(file, directory.resolve("parsed"), CdsInputLimitsDto.defaults(), () -> false, records::add);
        var received = new CdsClinicalNormalizationService(new CdsFieldParser()).medication(records.get(1));
        assertThat(received.name()).isEqualTo("Synthetic café 😀"); assertThat(received.din()).isNull();
        assertThat(received.start().precision()).isEqualTo(CdsTemporalDto.Precision.MONTH);
        assertThat(received.written().precision()).isEqualTo(CdsTemporalDto.Precision.YEAR);
        assertThat(received.dose()).isEqualTo("0.5-1"); assertThat(received.doseUnit()).isEqualTo("tablet");
        assertThat(received.quantity()).isEqualTo("12.5"); assertThat(received.refillQuantity()).isEqualTo(12);
        assertThat(received.durationDays()).isEqualTo("030"); assertThat(received.refillDurationDays()).isEqualTo(30);
        assertThat(received.providerNotes()).isEqualTo("Provider-only café 😀\r\nLiteral note");
        assertThat(received.pharmacyInstructions()).isEqualTo("Patient instructions");
        assertThat(received.longTerm()).isTrue(); assertThat(received.compliance()).isFalse();
        assertThat(received.substitutionNotAllowed()).isTrue(); assertThat(received.nonAuthoritative()).isFalse();
    }
    @Test void shouldBlockEveryAdditionalClinicalValue_insteadOfDroppingItIntoAnEquivalentResidualOrGuessingItsMeaning() throws Exception {
        for (var column : List.of("dosage", "takemin", "takemax", "end_date", "archived", "outside_provider_name", "provider_no",
                "GN", "ATC", "script_no", "pharmacyId", "demographic_contact_id", "method", "special", "last_refill_date")) {
            var fields = baseline(); fields.put(column, column.equals("archived") ? "1" : "Unresolved native clinical value");
            var mapped = mapper.map(27, medication(fields), List.of());
            assertThat(mapped.nativeRowRepresentable()).as(column).isFalse();
            assertThat(mapped.fields().get(key(column)).outcome()).as(column).isEqualTo(Outcome.REQUIRES_REVIEW);
        }
    }
    @Test void shouldNeverInferCurrentTreatmentFromVisibilityArchiveOrDates_andShouldExposeConflictingState() throws Exception {
        for (var update : List.of(Map.of("rxStatus", ""), Map.of("past_med", "1"), Map.of("cds_currentness", "HISTORICAL"),
                Map.of("rxStatus", "Completed", "past_med", "0"), Map.of("rxStatus", "FutureStatus"))) {
            var fields = baseline(); fields.putAll(update);
            assertThat(mapper.map(27, medication(fields), List.of()).nativeRowRepresentable()).as(update.toString()).isFalse();
        }
        var fields = baseline(); fields.put("rxStatus", "Suspended"); fields.put("cds_currentness", "SUSPENDED");
        assertThat(mapper.map(27, medication(fields), List.of()).value().fields().get(CdsExportMedicationDto.Field.PrescriptionStatus)).isEqualTo("Suspended");
    }
    @Test void shouldReviewAmbiguousNamesUnitsDurationsAndIdentifierAuthorities_withoutTruncationOrConversions() throws Exception {
        for (var update : List.of(Map.of("BN", "Another brand"), Map.of("unitName", "mL"), Map.of("duration", "3", "durunit", "M"),
                Map.of("regional_identifier", "1234567"), Map.of("quantity", "one"), Map.of("repeat", "-1"), Map.of("refill_quantity", "2147483648"),
                Map.of("customName", "x".repeat(121)), Map.of("duration", "0.5"), Map.of("nosubs", "true"))) {
            var fields = baseline(); fields.putAll(update);
            assertThat(mapper.map(27, medication(fields), List.of()).nativeRowRepresentable()).as(update.toString()).isFalse();
        }
    }
    @Test void shouldKeepMissingDateUnknown_andRejectSentinelsConflictsOrInventedPrecision() throws Exception {
        var fields = baseline(); var mapped = mapper.map(27, medication(fields), List.of());
        assertThat(mapped.value().written().precision()).isEqualTo(CdsExportDateDto.Precision.ABSENT);
        for (var update : List.of(Map.of("written_date", "0001-01-01"), Map.of("rx_date", "0000-00-00"), Map.of("start_date_unknown", "1"))) {
            var changed = baseline(); changed.putAll(update);
            assertThat(mapper.map(27, medication(changed), List.of()).nativeRowRepresentable()).isFalse();
        }
        fields.put("written_date", "1965-01-01");
        assertThat(mapper.map(27, medication(fields), List.of(precision(11, 51, 3, "YYYY"), precision(12, 51, 3, "YYYY"))).nativeRowRepresentable()).isFalse();
        assertThat(mapper.map(27, medication(fields), List.of(precision(11, 51, 9, "YYYY"))).nativeRowRepresentable()).isFalse();
    }
    @Test void shouldRequireExactPatientRowAndPrecisionParentIdentities() {
        assertThatThrownBy(() -> mapper.map(28, medication(baseline()), List.of())).hasMessage("CDS_EXPORT_MEDICATION_CAPTURE_SCOPE_INVALID");
        assertThatThrownBy(() -> mapper.map(27, medication(baseline()), List.of(precision(11, 52, 3, "YYYY")))).hasMessage("CDS_EXPORT_MEDICATION_CAPTURE_SCOPE_INVALID");
        var changed = baseline(); changed.put("drugid", "52");
        assertThatThrownBy(() -> mapper.map(27, medication(changed), List.of())).hasMessage("CDS_EXPORT_MEDICATION_CAPTURE_SCOPE_INVALID");
    }
    @Test void shouldUseOnlyAnExactCapturedPublicPrescriber_andBlockMissingReferencesOrConflictingOutsideHints() throws Exception {
        var fields = baseline(); fields.put("provider_no","0é27"); fields.put("outside_provider_name","Élodie Fixture"); fields.put("outside_provider_ohip","001234");
        var provider = new CdsExportCapturedTextRowDto("provider", CdsExportPublicProviderSourceReader.key("0é27"), Map.of(
                "provider_no","0é27","first_name","Élodie","last_name","Fixture","ohip_no","001234","practitionerNo","000987","practitionerNoType","CPSO"));
        var result = mapper.map(27,medication(fields),List.of(),Map.of("0é27",provider));
        assertThat(result.nativeRowRepresentable()).isTrue(); assertThat(result.fields()).hasSize(70);
        assertThat(result.value().prescriber().firstName()).isEqualTo("Élodie"); assertThat(result.value().prescriber().ohipNumber()).isEqualTo("001234");
        assertThat(mapper.map(27,medication(fields),List.of()).nativeRowRepresentable()).isFalse();
        for (var change : List.of(Map.of("outside_provider_name","Another physician"), Map.of("outside_provider_ohip","999999"), Map.of("provider_no","0É27"))) {
            var changed = new LinkedHashMap<>(fields); changed.putAll(change);
            assertThat(mapper.map(27,medication(changed),List.of(),Map.of("0é27",provider)).nativeRowRepresentable()).isFalse();
        }
    }
    @Test void shouldRejectBlankIdentity_andNeverReplaceItWithTheExportingClinician() throws Exception {
        var fields = baseline(); fields.put("customName"," "); fields.put("regional_identifier",null);
        assertThat(mapper.map(27,medication(fields),List.of()).nativeRowRepresentable()).isFalse();
    }
    @Test void shouldAccountForDerivedAsNeededAndShortTermFlags_onlyWhenTheirExplicitStandardMeaningsAgree() throws Exception {
        var fields = baseline(); fields.put("prn", "0"); fields.put("short_term", "0"); fields.put("eTreatmentType", "CHRON");
        assertThat(mapper.map(27, medication(fields), List.of()).nativeRowRepresentable()).isTrue();
        fields.put("prn", "1"); assertThat(mapper.map(27, medication(fields), List.of()).nativeRowRepresentable()).isFalse();
        fields.put("freqcode", "PRN"); assertThat(mapper.map(27, medication(fields), List.of()).nativeRowRepresentable()).isTrue();
        fields.put("short_term", "1"); assertThat(mapper.map(27, medication(fields), List.of()).nativeRowRepresentable()).isFalse();
        fields.put("eTreatmentType", "PRN Short-term"); assertThat(mapper.map(27, medication(fields), List.of()).nativeRowRepresentable()).isTrue();
    }
    @Test void shouldRequireAuthorityEvidenceForARegionalIdentifier_evenWhenItHasEightDigits() throws Exception {
        var fields = baseline(); fields.put("regional_identifier", "00123456");
        var result = mapper.map(27, medication(fields), List.of());
        assertThat(result.nativeRowRepresentable()).isFalse();
        assertThat(result.fields().get(key("regional_identifier")).rule()).isEqualTo("REGIONAL_DRUG_IDENTIFIER_AUTHORITY_REQUIRES_REVIEW");
    }
    static Map<String, String> baseline() {
        var fields = new LinkedHashMap<String, String>(); CdsExportPatientTableReadSupport.Plan.DRUGS.columns().forEach(column -> fields.put(column, null));
        fields.putAll(Map.of("drugid", "51", "demographic_no", "27", "customName", "Synthetic café 😀",
                "rxStatus", "Active", "past_med", "0", "cds_currentness", "CURRENT", "archived", "0", "cds_dose_text", "0.5-1", "unit", "tablet"));
        fields.putAll(Map.of("unitName", "tablet", "route", "PO", "freqcode", "BID", "duration", "030", "durunit", "D", "quantity", "12",
                "repeat", "2", "start_date_unknown", "0", "comment", "Provider-only café 😀\r\nLiteral note", "special_instruction", "Patient instructions"));
        return fields;
    }
    static CdsExportCapturedTextRowDto medication(Map<String, String> fields) {
        return new CdsExportCapturedTextRowDto("drugs", CdsExportPatientTableSourceReader.key(51), fields);
    }
    static CdsExportCapturedTextRowDto precision(int id, int parent, int field, String format) {
        var values = new LinkedHashMap<String, String>(); values.put("id", Integer.toString(id)); values.put("table_name", "2");
        values.put("table_id", Integer.toString(parent)); values.put("field_name", Integer.toString(field)); values.put("format", format);
        return new CdsExportCapturedTextRowDto("partial_date", CdsExportPatientTableSourceReader.key(id), values);
    }
    static CdsExportNativeFieldKeyDto key(String column) { return new CdsExportNativeFieldKeyDto("drugs", CdsExportPatientTableSourceReader.key(51), column); }
}
