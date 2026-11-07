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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsAllergyDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Actual pinned parser/spool/normalizer/native-candidate tests for the coordinated archive marker. */
@Tag("unit")
class CdsAllergyArchiveStateUnitTest {
    @TempDir Path directory;
    private final CdsFieldParser fields = new CdsFieldParser();
    private final CdsClinicalNormalizationService normalization = new CdsClinicalNormalizationService(fields);

    @Test void shouldKeepArchivedAllergiesArchived_andExplicitActiveAllergiesActive() throws Exception {
        for (String value : List.of("true", "1", "false", "0")) {
            var source = parsed(marker("boolean", value));
            var dto = normalization.allergy(source); boolean expected = value.equals("true") || value.equals("1");
            assertThat(dto.archived()).isEqualTo(expected);
            var nativeRow = new CdsAllergyPersister(mock(AllergyDao.class), mock(PartialDateDao.class), normalization).candidate(27, dto);
            assertThat(nativeRow.getArchived()).isEqualTo(expected);
        }
    }
    @Test void shouldLeaveUnmarkedAndUnrelatedResidualRecordsOnTheirExistingImportPath() throws Exception {
        assertThat(normalization.allergy(parsed("")).archived()).isFalse();
        String unrelated = "<ResidualInfo><cdsd:DataElement><cdsd:Name>Unrelated native extension</cdsd:Name>"
                + "<cdsd:DataType>text</cdsd:DataType><cdsd:Content>" + "x".repeat(1_000_000) + "</cdsd:Content></cdsd:DataElement></ResidualInfo>";
        var source = parsed(unrelated);
        assertThat(normalization.allergy(source).archived()).isFalse();
        assertThat(Files.size(source.valuesFile())).isGreaterThan(1_000_000);
    }
    @Test void shouldBlockMalformedAndDuplicateKnownMarkers_insteadOfDefaultingThemToActive() throws Exception {
        for (String residual : List.of(marker("string", "true"), marker("boolean", "yes"),
                marker("boolean", "true").replace("</ResidualInfo>", "") + marker("boolean", "false").replace("<ResidualInfo>", ""))) {
            var source = parsed(residual);
            assertThatThrownBy(() -> normalization.allergy(source)).hasMessageContaining("CDS_ALLERGY_ARCHIVE_STATE_REQUIRES_REVIEW");
        }
    }
    @Test void shouldUseTheAuditedEffectiveCorrection_whenTheArchiveFlagIsChanged() throws Exception {
        var original = parsed(marker("boolean", "true"));
        var corrected = CdsClinicalFixture.corrected(original, "ResidualInfo/DataElement/Content", "false");
        assertThat(normalization.allergy(original).archived()).isTrue();
        assertThat(normalization.allergy(corrected).archived()).isFalse();
    }
    @Test void shouldKeepLegacyPreparedJsonUnchanged_whenTheNewFlagIsFalse() throws Exception {
        var legacy = new CdsAllergyDto("Synthetic agent", null, null, null, null, null, null, null, null, null, null);
        var json = new ObjectMapper(); String encoded = json.writeValueAsString(legacy);
        assertThat(json.readTree(encoded).has("archived")).isFalse();
        assertThat(json.readTree(encoded).has("ageOfOnset")).isFalse();
        assertThat(json.readTree(encoded).has("onsetOfReaction")).isFalse();
        assertThat(json.readValue(encoded, CdsAllergyDto.class).archived()).isFalse();
        var archived = new CdsAllergyDto("Synthetic agent", null, null, null, null, null, null, null, null, null, null, true);
        assertThat(json.readTree(json.writeValueAsString(archived)).path("archived").asBoolean()).isTrue();
        assertThat(json.readValue(json.writeValueAsString(archived), CdsAllergyDto.class).archived()).isTrue();
    }
    @Test void shouldPreserveExactAgeTextAndTheDefinedOnsetVocabulary_inNativeCandidates() throws Exception {
        for (var entry : CdsNativeExtensionContract.ALLERGY_ONSET_VALUES.entrySet()) {
            String residual = combined(element(CdsNativeExtensionContract.ALLERGY_ARCHIVED, "boolean", "true"),
                    element(CdsNativeExtensionContract.ALLERGY_AGE_OF_ONSET, "string", "0012"),
                    element(CdsNativeExtensionContract.ALLERGY_ONSET_OF_REACTION, "string", entry.getValue()));
            var dto = normalization.allergy(parsed(residual));
            assertThat(dto.archived()).isTrue(); assertThat(dto.ageOfOnset()).isEqualTo("0012");
            assertThat(dto.onsetOfReaction()).isEqualTo(entry.getKey());
            var row = new CdsAllergyPersister(mock(AllergyDao.class), mock(PartialDateDao.class), normalization).candidate(27, dto);
            assertThat(row.getAgeOfOnset()).isEqualTo("0012"); assertThat(row.getOnsetOfReaction()).isEqualTo(entry.getKey());
        }
        var legacy = normalization.allergy(parsed(""));
        assertThat(legacy.ageOfOnset()).isNull(); assertThat(legacy.onsetOfReaction()).isNull();
    }
    @Test void shouldBlockInvalidTypesDuplicateFieldsAndUnknownOnset_insteadOfDiscardingKnownNativeData() throws Exception {
        for (String value : List.of(
                combined(element(CdsNativeExtensionContract.ALLERGY_AGE_OF_ONSET, "integer", "12")),
                combined(element(CdsNativeExtensionContract.ALLERGY_AGE_OF_ONSET, "string", "12345")),
                combined(element(CdsNativeExtensionContract.ALLERGY_AGE_OF_ONSET, "string", "")),
                combined(element(CdsNativeExtensionContract.ALLERGY_ONSET_OF_REACTION, "string", "Unexpected")),
                combined(element(CdsNativeExtensionContract.ALLERGY_ONSET_OF_REACTION, "string", "Slow"),
                        element(CdsNativeExtensionContract.ALLERGY_ONSET_OF_REACTION, "string", "Slow")))) {
            var source = parsed(value);
            assertThatThrownBy(() -> normalization.allergy(source)).hasMessageContaining("CDS_ALLERGY_NATIVE_FIELD_REQUIRES_REVIEW");
        }
    }
    @Test void shouldApplyEffectiveNativeFieldCorrections_withoutMutatingTheRetainedOriginal() throws Exception {
        var source = parsed(combined(element(CdsNativeExtensionContract.ALLERGY_AGE_OF_ONSET, "string", "0012")));
        var corrected = CdsClinicalFixture.corrected(source, "ResidualInfo/DataElement/Content", "0013");
        assertThat(normalization.allergy(source).ageOfOnset()).isEqualTo("0012");
        assertThat(normalization.allergy(corrected).ageOfOnset()).isEqualTo("0013");
    }
    @Test void shouldVerifyOversizedUnrelatedNamesToEof_beforeIgnoringTheirContents() throws Exception {
        String padding = " ".repeat(10000);
        var source = parsed(marker("boolean", "true").replace(CdsNativeExtensionContract.ALLERGY_ARCHIVED,
                padding + CdsNativeExtensionContract.ALLERGY_ARCHIVED));
        byte[] original = Files.readAllBytes(source.valuesFile());
        var selected = new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto[1];
        fields.forEachField(source, field -> { if (CdsFieldParser.unindexed(field.path()).equals("ResidualInfo/DataElement/Name")) selected[0] = field; });
        assertThat(selected[0]).isNotNull();
        int offset = Math.toIntExact(selected[0].offset());
        java.util.Arrays.fill(original, offset, offset + 10000, (byte) 'X');
        Files.write(source.valuesFile(), original);
        assertThatThrownBy(() -> normalization.allergy(source)).hasMessageContaining("CDS_SPOOL_VALUE_HASH_MISMATCH");
    }
    private CdsRecordDto parsed(String residual) throws Exception {
        String fixture;
        try (var input = getClass().getResourceAsStream("/demographic/cds-import-all-categories.xml")) {
            fixture = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        int start = fixture.indexOf("<AllergiesAndAdverseReactions>"), end = fixture.indexOf("</AllergiesAndAdverseReactions>") + "</AllergiesAndAdverseReactions>".length();
        String xml = fixture.substring(0, start) + "<AllergiesAndAdverseReactions>" + residual
                + "<OffendingAgentDescription>Synthetic agent</OffendingAgentDescription></AllergiesAndAdverseReactions>" + fixture.substring(end);
        Path root = Files.createTempDirectory(directory, "archive-"); Path file = Files.writeString(root.resolve("patient.xml"), xml, StandardCharsets.UTF_8);
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser().parse(file, root.resolve("parsed"), CdsInputLimitsDto.defaults(), () -> false, records::add);
        return records.stream().filter(record -> record.section().equals("AllergiesAndAdverseReactions")).findFirst().orElseThrow();
    }
    private static String marker(String type, String value) {
        return combined(element(CdsNativeExtensionContract.ALLERGY_ARCHIVED, type, value));
    }
    private static String combined(String... elements) { return "<ResidualInfo>" + String.join("", elements) + "</ResidualInfo>"; }
    private static String element(String name, String type, String value) {
        return "<cdsd:DataElement><cdsd:Name>" + name
                + "</cdsd:Name><cdsd:DataType>" + type + "</cdsd:DataType><cdsd:Content>" + value + "</cdsd:Content></cdsd:DataElement>";
    }
}
