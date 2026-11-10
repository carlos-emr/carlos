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

@Tag("unit")
@Tag("cds-export")
class CdsExportAllergyMapperUnitTest {
    @TempDir Path directory;
    private final CdsExportAllergyMapper mapper = new CdsExportAllergyMapper();

    @Test
    void shouldMapTheCapturedClinicalValuesAndLinkedPrecision_throughActualSchemaAndReceiver()
            throws Exception {
        var nativeFields = baseline();
        nativeFields.putAll(Map.of("age_of_onset", "0012", "onset_of_reaction", "1"));
        var result =
                mapper.map(
                        27,
                        allergy(nativeFields),
                        List.of(precision(11, 51, 1, "YYYY"), precision(12, 51, 2, "YYYY-MM")));
        assertThat(result.nativeRowRepresentable()).isTrue();
        assertThat(result.fields()).hasSize(34);
        assertThat(result.value().archived()).isTrue();
        assertThat(result.value().property()).isEqualTo("UK");
        assertThat(result.value().reaction()).isEqualTo("café 😀\r\nSynthetic reaction");
        assertThat(result.value().severity()).isEqualTo("MI");
        assertThat(result.value().start().lexical()).isEqualTo("1965");
        assertThat(result.value().recorded().lexical()).isEqualTo("2024-02");
        var patient = new LinkedHashMap<String, String>();
        CdsExportDemographicRowDto.COLUMNS.forEach(column -> patient.put(column, null));
        patient.putAll(
                Map.of(
                        "first_name",
                        "Synthetic",
                        "last_name",
                        "Fixture",
                        "year_of_birth",
                        "1980",
                        "month_of_birth",
                        "01",
                        "date_of_birth",
                        "01",
                        "patient_status",
                        "AC",
                        "sex",
                        "U"));
        var demographics =
                new CdsExportDemographicMapper().map(new CdsExportDemographicRowDto(27, patient));
        var sections =
                List.of(
                        new CdsExportXmlComposer.Section(
                                new CdsExportIdentityDto(
                                        "a".repeat(64), CdsCategory.DEMOGRAPHICS, "b".repeat(64)),
                                writer ->
                                        new CdsExportDemographicXmlWriter()
                                                .write(demographics, writer)),
                        new CdsExportXmlComposer.Section(
                                new CdsExportIdentityDto(
                                        "a".repeat(64),
                                        CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
                                        "c".repeat(64)),
                                writer ->
                                        new CdsExportAllergyXmlWriter()
                                                .write(result.value(), writer)));
        var output = new ByteArrayOutputStream();
        var selected =
                Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS);
        new CdsExportXmlComposer()
                .compose(
                        output,
                        "d".repeat(64),
                        "a".repeat(64),
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        selected,
                        sections.iterator(),
                        ignored -> {},
                        1L << 20,
                        () -> {});
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(output.toByteArray()),
                        "27",
                        selected,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
        var file = Files.write(directory.resolve("patient.xml"), output.toByteArray());
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        file,
                        directory.resolve("parsed"),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        var received =
                new CdsClinicalNormalizationService(new CdsFieldParser()).allergy(records.get(1));
        assertThat(received.archived()).isTrue();
        assertThat(received.property()).isEqualTo("UK");
        assertThat(received.start().precision()).isEqualTo(CdsTemporalDto.Precision.YEAR);
        assertThat(received.recorded().precision()).isEqualTo(CdsTemporalDto.Precision.MONTH);
        assertThat(received.reaction()).isEqualTo("café 😀\r\nSynthetic reaction");
        assertThat(received.ageOfOnset()).isEqualTo("0012");
        assertThat(received.onsetOfReaction()).isEqualTo("1");
    }

    @Test
    void shouldNeverTurnNativeSevereIntoLifeThreatening_withoutReconciledSourceEvidence()
            throws Exception {
        var fields = baseline();
        fields.put("severity_of_reaction", "3");
        var result = mapper.map(27, allergy(fields), List.of());
        assertThat(result.nativeRowRepresentable()).isFalse();
        assertThat(result.value()).isNull();
        assertThat(result.fields().get(key("severity_of_reaction")).rule())
                .isEqualTo("SEVERE_IS_NOT_EVIDENCE_OF_LIFE_THREATENING");
        fields.put("severity_of_reaction", "0");
        var unknown = mapper.map(27, allergy(fields), List.of());
        assertThat(unknown.nativeRowRepresentable()).isTrue();
        assertThat(unknown.value().severity()).isNull();
        assertThat(unknown.fields().get(key("severity_of_reaction")).outcome())
                .isEqualTo(Outcome.ABSENT);
    }

    @Test
    void shouldDistinguishUnknownDrugPropertyFromDrugAndNonDrug_withoutTruthyOrEmptyCoercion()
            throws Exception {
        var fields = baseline();
        assertThat(mapper.map(27, allergy(fields), List.of()).value().property()).isEqualTo("UK");
        fields.put("nonDrug", "0");
        assertThat(mapper.map(27, allergy(fields), List.of()).value().property()).isEqualTo("DR");
        fields.put("nonDrug", "1");
        assertThat(mapper.map(27, allergy(fields), List.of()).value().property()).isEqualTo("ND");
        for (String invalid : List.of("", "true", "2")) {
            fields.put("nonDrug", invalid);
            assertThat(mapper.map(27, allergy(fields), List.of()).nativeRowRepresentable())
                    .isFalse();
        }
    }

    @Test
    void shouldKeepUnresolvedDrugLookupsAndAdditionalFieldsAsExplicitBlockers_forAllergyMapperContract() throws Exception {
        var fields = baseline();
        fields.putAll(
                Map.of(
                        "TYPECODE",
                        "13",
                        "drugref_id",
                        "01234567",
                        "regional_identifier",
                        "01234567",
                        "HIC_SEQNO",
                        "123",
                        "age_of_onset",
                        "12",
                        "onset_of_reaction",
                        "1",
                        "providerNo",
                        "000027"));
        var result = mapper.map(27, allergy(fields), List.of());
        assertThat(result.nativeRowRepresentable()).isFalse();
        for (String column :
                List.of(
                        "TYPECODE",
                        "drugref_id",
                        "regional_identifier",
                        "HIC_SEQNO",
                        "providerNo")) {
            assertThat(result.fields().get(key(column)).outcome())
                    .isEqualTo(Outcome.REQUIRES_REVIEW);
        }
        for (String column : List.of("age_of_onset", "onset_of_reaction")) {
            assertThat(result.fields().get(key(column)).outcome()).isEqualTo(Outcome.MAPPED);
        }
        assertThat(result.fields()).hasSize(24);
    }

    @Test
    void shouldPreserveAnExplicitSingleAtcCode_withoutTreatingAnotherIdentifierAsEquivalent()
            throws Exception {
        var fields = baseline();
        fields.put("atc", "B01AB01");
        fields.put("TYPECODE", "8");
        var result = mapper.map(27, allergy(fields), List.of());
        assertThat(result.nativeRowRepresentable()).isTrue();
        assertThat(result.value().codeType()).isEqualTo("ATC");
        assertThat(result.value().codeValue()).isEqualTo("B01AB01");
        fields.put("regional_identifier", "B01AB01");
        assertThat(mapper.map(27, allergy(fields), List.of()).nativeRowRepresentable()).isFalse();
    }

    @Test
    void shouldBlockReactionCapacityAndPrecisionConflicts_withoutTruncatingOrMovingDataIntoNotes()
            throws Exception {
        var fields = baseline();
        fields.put("reaction", "x".repeat(121));
        var longReaction = mapper.map(27, allergy(fields), List.of());
        assertThat(longReaction.nativeRowRepresentable()).isFalse();
        assertThat(longReaction.fields().get(key("reaction")).outcome())
                .isEqualTo(Outcome.REQUIRES_REVIEW);
        var duplicate =
                mapper.map(
                        27,
                        allergy(baseline()),
                        List.of(precision(11, 51, 1, "YYYY"), precision(12, 51, 1, "YYYY")));
        assertThat(duplicate.nativeRowRepresentable()).isFalse();
        assertThat(duplicate.fields().get(key("start_date")).outcome())
                .isEqualTo(Outcome.REQUIRES_REVIEW);
        var unknown = mapper.map(27, allergy(baseline()), List.of(precision(11, 51, 3, "YYYY")));
        assertThat(unknown.nativeRowRepresentable()).isFalse();
    }

    @Test
    void shouldRefuseForeignPatientParentAndRecordIdentities_beforeMappingTheirValues() {
        var row = allergy(baseline());
        assertThatThrownBy(() -> mapper.map(28, row, List.of()))
                .hasMessage("CDS_EXPORT_ALLERGY_CAPTURE_SCOPE_INVALID");
        assertThatThrownBy(() -> mapper.map(27, row, List.of(precision(11, 52, 1, "YYYY"))))
                .hasMessage("CDS_EXPORT_ALLERGY_CAPTURE_SCOPE_INVALID");
        var fields = baseline();
        fields.put("allergyid", "52");
        assertThatThrownBy(() -> mapper.map(27, allergy(fields), List.of()))
                .hasMessage("CDS_EXPORT_ALLERGY_CAPTURE_SCOPE_INVALID");
    }

    @Test
    void shouldLeaveOptionalRecordedDateUnknown_insteadOfUsingTheModificationClock()
            throws Exception {
        var fields = baseline();
        fields.put("entry_date", null);
        var result = mapper.map(27, allergy(fields), List.of());
        assertThat(result.nativeRowRepresentable()).isTrue();
        assertThat(result.value().recorded().precision())
                .isEqualTo(CdsExportDateDto.Precision.ABSENT);
        assertThat(result.fields().get(key("lastUpdateDate")).outcome())
                .isEqualTo(Outcome.OPERATIONAL);
    }

    @Test
    void shouldKeepNativeOnsetDefaultsAndRefuseUnknownOrNormalizingValues_forAllergyMapperContract() throws Exception {
        for (String code : List.of("0", "1", "2", "3")) {
            var fields = baseline();
            fields.putAll(Map.of("age_of_onset", "0", "onset_of_reaction", code));
            var mapped = mapper.map(27, allergy(fields), List.of());
            assertThat(mapped.nativeRowRepresentable()).isTrue();
            assertThat(mapped.value().ageOfOnset()).isEqualTo("0");
            assertThat(mapped.value().onsetOfReaction()).isEqualTo(code);
        }
        for (var invalid :
                List.of(
                        Map.of("onset_of_reaction", "9"),
                        Map.of("age_of_onset", " 12"),
                        Map.of("age_of_onset", "12\t"),
                        Map.of("age_of_onset", "12345"))) {
            var fields = baseline();
            fields.putAll(invalid);
            assertThat(mapper.map(27, allergy(fields), List.of()).nativeRowRepresentable())
                    .isFalse();
        }
    }

    private static Map<String, String> baseline() {
        var fields = new LinkedHashMap<String, String>();
        CdsExportPatientTableReadSupport.Plan.ALLERGIES
                .columns()
                .forEach(column -> fields.put(column, null));
        fields.putAll(
                Map.of(
                        "allergyid",
                        "51",
                        "demographic_no",
                        "27",
                        "DESCRIPTION",
                        "FAKE-CDS agent",
                        "archived",
                        "1",
                        "TYPECODE",
                        "0",
                        "position",
                        "0",
                        "lastUpdateDate",
                        "2026-11-05 10:00:00",
                        "severity_of_reaction",
                        "1",
                        "reaction_type",
                        "AL",
                        "life_stage",
                        "A"));
        fields.putAll(
                Map.of(
                        "reaction",
                        "café 😀\r\nSynthetic reaction",
                        "start_date",
                        "1965-01-01",
                        "entry_date",
                        "2024-02-01"));
        return fields;
    }

    private static CdsExportCapturedTextRowDto allergy(Map<String, String> fields) {
        return new CdsExportCapturedTextRowDto(
                "allergies", CdsExportPatientTableSourceReader.key(51), fields);
    }

    private static CdsExportCapturedTextRowDto precision(
            int id, int parent, int field, String format) {
        var values = new LinkedHashMap<String, String>();
        values.put("id", Integer.toString(id));
        values.put("table_name", "1");
        values.put("table_id", Integer.toString(parent));
        values.put("field_name", Integer.toString(field));
        values.put("format", format);
        return new CdsExportCapturedTextRowDto(
                "partial_date", CdsExportPatientTableSourceReader.key(id), values);
    }

    private static CdsExportNativeFieldKeyDto key(String column) {
        return new CdsExportNativeFieldKeyDto(
                "allergies", CdsExportPatientTableSourceReader.key(51), column);
    }
}
