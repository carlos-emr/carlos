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

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class CdsHistoryResidualReaderUnitTest {
    @TempDir Path directory;
    private final CdsFieldParser fields = new CdsFieldParser();

    private CdsRecordDto parsed(String category, String residual) throws Exception {
        String fixture;
        try (var input =
                getClass().getResourceAsStream("/demographic/cds-import-all-categories.xml")) {
            fixture = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        int start = fixture.indexOf("<" + category + ">"),
                end = fixture.indexOf("</" + category + ">") + category.length() + 3;
        String xml =
                fixture.substring(0, start)
                        + "<"
                        + category
                        + ">"
                        + residual
                        + "</"
                        + category
                        + ">"
                        + fixture.substring(end);
        Path root = Files.createTempDirectory(directory, "history-");
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        Files.writeString(root.resolve("patient.xml"), xml),
                        root.resolve("parsed"),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        return records.stream().filter(r -> r.section().equals(category)).findFirst().orElseThrow();
    }

    private static String element(String name, String type, String content) {
        return "<cdsd:DataElement><cdsd:Name>"
                + name
                + "</cdsd:Name><cdsd:DataType>"
                + type
                + "</cdsd:DataType><cdsd:Content>"
                + content
                + "</cdsd:Content></cdsd:DataElement>";
    }

    @Test
    void shouldPreserveExplicitArchivedAndActiveStates_forAllSixCppCategories() throws Exception {
        for (String category :
                List.of(
                        "PersonalHistory",
                        "FamilyHistory",
                        "PastHealth",
                        "ProblemList",
                        "RiskFactors",
                        "AlertsAndSpecialNeeds"))
            for (String value : List.of("true", "1", "false", "0")) {
                var record =
                        parsed(
                                category,
                                "<ResidualInfo>"
                                        + element(
                                                CdsNativeExtensionContract.HISTORY_ARCHIVED,
                                                "boolean",
                                                value)
                                        + "</ResidualInfo>");
                assertThat(new CdsHistoryResidualReader(fields).read(record).archived())
                        .isEqualTo(value.equals("true") || value.equals("1"));
            }
    }

    @Test
    void shouldRestoreTheExactPersonalNarrative_fromItsSchemaPermittedResidualCarrier()
            throws Exception {
        var record =
                parsed(
                        "PersonalHistory",
                        "<ResidualInfo>"
                                + element(
                                        CdsNativeExtensionContract.HISTORY_PERSONAL_NARRATIVE,
                                        "string",
                                        "Exact café 😀 &amp; clinical history")
                                + "</ResidualInfo>");
        var result = new CdsHistoryResidualReader(fields).read(record);
        assertThat(fields.readLogicalText(record, result.personalNarrative(), 1000))
                .isEqualTo("Exact café 😀 & clinical history");
    }

    @Test
    void shouldRefuseMalformedOrDuplicateKnownArchiveMarkers_insteadOfReactivatingTheRecord()
            throws Exception {
        var valid = element(CdsNativeExtensionContract.HISTORY_ARCHIVED, "boolean", "true");
        for (String content :
                List.of(
                        valid + valid,
                        element(CdsNativeExtensionContract.HISTORY_ARCHIVED, "string", "true"),
                        element(CdsNativeExtensionContract.HISTORY_ARCHIVED, "boolean", "yes"))) {
            var record = parsed("FamilyHistory", "<ResidualInfo>" + content + "</ResidualInfo>");
            assertThatThrownBy(() -> new CdsHistoryResidualReader(fields).read(record))
                    .hasMessageContaining("CDS_HISTORY_NATIVE_STATE_REQUIRES_REVIEW");
        }
    }

    @Test
    void shouldRejectAPersonalNarrativeCarrier_inAnotherHistoryCategory() throws Exception {
        var record =
                parsed(
                        "FamilyHistory",
                        "<ResidualInfo>"
                                + element(
                                        CdsNativeExtensionContract.HISTORY_PERSONAL_NARRATIVE,
                                        "string",
                                        "Personal text")
                                + "</ResidualInfo>");
        assertThatThrownBy(() -> new CdsHistoryResidualReader(fields).read(record))
                .hasMessageContaining("CDS_HISTORY_NATIVE_STATE_REQUIRES_REVIEW");
    }

    @Test
    void shouldRestoreANarrative_fromTheEncodedCarrierInEveryCppCategory() throws Exception {
        for (String category :
                List.of(
                        "PersonalHistory",
                        "FamilyHistory",
                        "PastHealth",
                        "ProblemList",
                        "RiskFactors")) {
            var record =
                    parsed(
                            category,
                            "<ResidualInfo>"
                                    + element(
                                            CdsNativeExtensionContract.HISTORY_NARRATIVE,
                                            "string",
                                            "Long native narrative café 😀")
                                    + "</ResidualInfo>");
            var result = new CdsHistoryResidualReader(fields).read(record);
            assertThat(fields.readLogicalText(record, result.narrative(), 1000))
                    .isEqualTo("Long native narrative café 😀");
            assertThat(result.personalNarrative()).isNull();
        }
    }

    @Test
    void shouldEncodeNarrativeWhitespace_soTokenNormalizationCannotChangeIt() {
        String text = "Line one\n\tLine  two\r\n \"quoted\" café 😀 ";
        String encoded = CdsNativeExtensionContract.encodeNarrative(text);
        assertThat(encoded).doesNotContain(" ", "\n", "\r", "\t");
        assertThat(CdsNativeExtensionContract.decodeNarrative(encoded)).isEqualTo(text);
        for (String malformed : List.of("", "plain text", "\"a\"\"b\"", "{\"a\":1}", "\"open", "42"))
            assertThatThrownBy(() -> CdsNativeExtensionContract.decodeNarrative(malformed))
                    .hasMessage("CDS_HISTORY_NARRATIVE_ENCODING_INVALID");
    }

    @Test
    void shouldKeepUnmarkedHistoricalImportsCompatible_andLeaveUnrelatedLargeResidualTextStreamed()
            throws Exception {
        var record =
                parsed(
                        "PersonalHistory",
                        "<ResidualInfo>"
                                + element("Foreign.Vendor.Value", "string", "x".repeat(1_000_000))
                                + "</ResidualInfo>");
        var state = new CdsHistoryResidualReader(fields).read(record);
        assertThat(state.archived()).isFalse();
        assertThat(state.personalNarrative()).isNull();
        assertThat(Files.size(record.valuesFile())).isGreaterThan(1_000_000);
    }

    @Test
    void shouldPreserveOldSealedJsonBytes_whenTheOptionalArchiveFlagIsAbsentOrFalse()
            throws Exception {
        var dto =
                new CdsHistoryNativeDto(
                        "Original", "FamHistory", null, null, null, null, null, List.of());
        var json = new ObjectMapper();
        String encoded = json.writeValueAsString(dto);
        assertThat(json.readTree(encoded).has("archived")).isFalse();
        assertThat(json.readValue(encoded, CdsHistoryNativeDto.class).archived()).isFalse();
        var archived =
                new CdsHistoryNativeDto(
                        "Original", "FamHistory", null, null, null, null, null, List.of(), true);
        assertThat(
                        json.readValue(json.writeValueAsString(archived), CdsHistoryNativeDto.class)
                                .archived())
                .isTrue();
    }

    @Test
    void shouldHonorAuditedEffectiveArchiveCorrections_withoutChangingTheOriginalSpool()
            throws Exception {
        var record =
                parsed(
                        "FamilyHistory",
                        "<ResidualInfo>"
                                + element(
                                        CdsNativeExtensionContract.HISTORY_ARCHIVED,
                                        "boolean",
                                        "true")
                                + "</ResidualInfo>");
        var corrected =
                CdsClinicalFixture.corrected(record, "ResidualInfo/DataElement/Content", "false");
        assertThat(new CdsHistoryResidualReader(fields).read(record).archived()).isTrue();
        assertThat(new CdsHistoryResidualReader(fields).read(corrected).archived()).isFalse();
    }
}
