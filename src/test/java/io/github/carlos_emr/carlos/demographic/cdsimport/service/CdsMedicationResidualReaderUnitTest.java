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
class CdsMedicationResidualReaderUnitTest {
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
    void shouldReadOnlyTheExactKnownArchiveCarrier_andRejectMalformedOrDuplicateMarkers()
            throws Exception {
        String name = CdsNativeExtensionContract.MEDICATION_ARCHIVED;
        for (String value : List.of("true", "1", "false", "0")) {
            var record =
                    parsed(
                            "MedicationsAndTreatments",
                            "<ResidualInfo>" + element(name, "boolean", value) + "</ResidualInfo>");
            assertThat(new CdsMedicationResidualReader(fields).read(record).archived())
                    .isEqualTo(value.equals("true") || value.equals("1"));
        }
        for (String content :
                List.of(
                        element(name, "string", "true"),
                        element(name, "boolean", "yes"),
                        element(name, "boolean", "true") + element(name, "boolean", "false"))) {
            var record =
                    parsed(
                            "MedicationsAndTreatments",
                            "<ResidualInfo>" + content + "</ResidualInfo>");
            assertThatThrownBy(() -> new CdsMedicationResidualReader(fields).read(record))
                    .hasMessageContaining("CDS_MEDICATION_NATIVE_STATE_REQUIRES_REVIEW");
        }
        assertThat(
                        new CdsMedicationResidualReader(fields)
                                .read(
                                        parsed(
                                                "MedicationsAndTreatments",
                                                "<ResidualInfo>"
                                                        + element(
                                                                "Unknown.Vendor", "string", "true")
                                                        + "</ResidualInfo>"))
                                .archived())
                .isFalse();
    }

    @Test
    void shouldKeepLegacyPreparedBytesStable_andPreserveExplicitArchiveState() throws Exception {
        var mapper = new ObjectMapper();
        var legacy = new CdsMedicationNativeDto(null, null);
        assertThat(mapper.writeValueAsString(legacy)).doesNotContain("archived");
        var archived = new CdsMedicationNativeDto(null, null, true);
        assertThat(
                        mapper.readValue(
                                        mapper.writeValueAsString(archived),
                                        CdsMedicationNativeDto.class)
                                .archived())
                .isTrue();
        var defaults =
                io.github.carlos_emr.carlos.demographic.cds.contract.CdsPreparedHistoryDefaults
                        .apply(mapper.readTree("{\"source\":null,\"provider\":null}"));
        assertThat(mapper.treeToValue(defaults, CdsMedicationNativeDto.class).archived()).isFalse();
    }
}
