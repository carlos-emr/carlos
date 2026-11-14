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
    void shouldReadTheRestorableSourceColumns_besideTheArchiveCarrier() throws Exception {
        // #6198: only quantity, unit, unitName and prn of the complete native row are read.
        var row = new LinkedHashMap<String, String>();
        row.put("BN", "Synthetic  brand");
        row.put("quantity", "100");
        row.put("unit", "");
        row.put("unitName", "mL");
        row.put("prn", "1");
        String content =
                new ObjectMapper()
                        .writeValueAsString(Map.of("version", 1, "fields", row))
                        .replace(" ", "\\u0020");
        var state =
                new CdsMedicationResidualReader(fields)
                        .read(
                                parsed(
                                        "MedicationsAndTreatments",
                                        "<ResidualInfo>"
                                                + element(
                                                        CdsNativeExtensionContract
                                                                .MEDICATION_SOURCE_CONTEXT,
                                                        "string",
                                                        content)
                                                + element(
                                                        CdsNativeExtensionContract
                                                                .MEDICATION_ARCHIVED,
                                                        "boolean",
                                                        "true")
                                                + "</ResidualInfo>"));
        assertThat(state.archived()).isTrue();
        assertThat(state.source().quantity()).isEqualTo("100");
        assertThat(state.source().quantityUnit()).isEqualTo("mL");
        assertThat(state.source().prn()).isEqualTo("1");
        assertThat(state.source().toString()).doesNotContain("mL");
        // A unitName equal to the dose unit is the importer's copy, not a quantity unit.
        assertThat(
                        new CdsMedicationResidualReader.SourceContext("100", "mL", "mL", "0")
                                .quantityUnit())
                .isNull();
    }

    @Test
    void shouldReadACarrierPastTheFormerSixteenMillionCharacterBound_withoutHoldingIt()
            throws Exception {
        // A prescription whose instructions fill a MEDIUMTEXT column makes a carrier longer than
        // the 16,777,216 characters both sides once allowed; the import refused the record. The
        // carrier is now read as a stream: the long column is skipped where it stands and the
        // four restorable columns are returned. The file is written in pieces so the test never
        // holds the carrier in memory either.
        long filler = 17_000_000L;
        String fixture;
        try (var input =
                getClass().getResourceAsStream("/demographic/cds-import-all-categories.xml")) {
            fixture = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String category = "MedicationsAndTreatments";
        int start = fixture.indexOf("<" + category + ">"),
                end = fixture.indexOf("</" + category + ">") + category.length() + 3;
        Path root = Files.createTempDirectory(directory, "large-");
        Path xml = root.resolve("patient.xml");
        try (var out = Files.newBufferedWriter(xml, StandardCharsets.UTF_8)) {
            out.write(fixture.substring(0, start));
            out.write("<" + category + "><ResidualInfo><cdsd:DataElement><cdsd:Name>");
            out.write(CdsNativeExtensionContract.MEDICATION_SOURCE_CONTEXT);
            out.write("</cdsd:Name><cdsd:DataType>string</cdsd:DataType><cdsd:Content>");
            out.write("{\"version\":1,\"fields\":{\"BN\":\"Synthetic\",\"special\":\"");
            char[] block = new char[1 << 16];
            Arrays.fill(block, 'x');
            for (long written = 0; written < filler; written += block.length)
                out.write(block, 0, (int) Math.min(block.length, filler - written));
            out.write(
                    "\",\"quantity\":\"100\",\"unit\":\"\",\"unitName\":\"mL\",\"prn\":\"1\"}}");
            out.write("</cdsd:Content></cdsd:DataElement></ResidualInfo></" + category + ">");
            out.write(fixture.substring(end));
        }
        assertThat(Files.size(xml)).isGreaterThan(1L << 24);
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        xml,
                        root.resolve("parsed"),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        var record =
                records.stream().filter(r -> r.section().equals(category)).findFirst().orElseThrow();
        var state = new CdsMedicationResidualReader(fields).read(record);
        assertThat(state.source().quantity()).isEqualTo("100");
        assertThat(state.source().quantityUnit()).isEqualTo("mL");
        assertThat(state.source().prn()).isEqualTo("1");
    }

    @Test
    void shouldRefuseARestorableColumnLongerThanAnyColumnCouldHold_notTheCarriersSize()
            throws Exception {
        // What is refused is a value the importer would restore that no column could hold (here
        // a "quantity" of 200,000 characters), which a CARLOS export cannot contain.
        String content =
                "{\"version\":1,\"fields\":{\"quantity\":\""
                        + "9".repeat(200_000)
                        + "\",\"unit\":\"\",\"unitName\":\"mL\",\"prn\":\"1\"}}";
        var record =
                parsed(
                        "MedicationsAndTreatments",
                        "<ResidualInfo>"
                                + element(
                                        CdsNativeExtensionContract.MEDICATION_SOURCE_CONTEXT,
                                        "string",
                                        content)
                                + "</ResidualInfo>");
        assertThatThrownBy(() -> new CdsMedicationResidualReader(fields).read(record))
                .hasMessageContaining("CDS_MEDICATION_NATIVE_STATE_REQUIRES_REVIEW");
        // A repeated restorable member and content after the document are still refused.
        for (String invalid :
                List.of(
                        "{\"version\":1,\"fields\":{\"quantity\":\"1\",\"quantity\":\"2\","
                                + "\"unit\":\"\",\"unitName\":\"mL\",\"prn\":\"1\"}}",
                        "{\"version\":1,\"fields\":{\"quantity\":\"1\",\"unit\":\"\","
                                + "\"unitName\":\"mL\",\"prn\":\"1\"}}{}",
                        "{\"version\":2,\"fields\":{\"quantity\":\"1\",\"unit\":\"\","
                                + "\"unitName\":\"mL\",\"prn\":\"1\"}}")) {
            var bad =
                    parsed(
                            "MedicationsAndTreatments",
                            "<ResidualInfo>"
                                    + element(
                                            CdsNativeExtensionContract.MEDICATION_SOURCE_CONTEXT,
                                            "string",
                                            invalid)
                                    + "</ResidualInfo>");
            assertThatThrownBy(() -> new CdsMedicationResidualReader(fields).read(bad))
                    .hasMessageContaining("CDS_MEDICATION_NATIVE_STATE_REQUIRES_REVIEW");
        }
    }

    @Test
    void shouldCollapseTokenWhitespaceAsAStream_exactlyAsTheBoundedReaderDoes() throws Exception {
        var record =
                parsed(
                        "MedicationsAndTreatments",
                        "<ResidualInfo>"
                                + element("Vendor.custom", "string", " \n a  b\t\tc \n")
                                + "</ResidualInfo>");
        var content = new java.util.concurrent.atomic.AtomicReference<CdsFieldDto>();
        fields.forEachField(
                record,
                field -> {
                    if (CdsFieldParser.unindexed(field.path())
                            .equals("ResidualInfo/DataElement/Content")) content.set(field);
                });
        var streamed = new StringBuilder();
        try (var reader = fields.openToken(record, content.get())) {
            char[] one = new char[1];
            while (reader.read(one, 0, 1) == 1) streamed.append(one[0]);
        }
        assertThat(streamed.toString())
                .isEqualTo("a b c")
                .isEqualTo(fields.readToken(record, content.get(), 100));
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
