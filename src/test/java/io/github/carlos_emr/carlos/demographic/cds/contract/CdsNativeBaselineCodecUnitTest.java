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
package io.github.carlos_emr.carlos.demographic.cds.contract;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class CdsNativeBaselineCodecUnitTest {
    private static final String EMPTY =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    void
            shouldBindFieldWitnessesToTheExactOriginalReceipt_withoutChangingTheIndependentLegacyVector()
                    throws Exception {
        var row = vector();
        assertThat(row.digest())
                .isEqualTo("8ed69a9427270643a2704375dc7afebbc6e35cc991ea3589fce34ab7b8230bc7");
        String receipt = receipt(row, true);
        String original = receipt;
        String encoded = CdsNativeBaselineCodec.encode(receipt, sha(receipt), List.of(row));
        var restored = CdsNativeBaselineCodec.decode(encoded, receipt, sha(receipt));
        assertThat(restored.rows()).containsExactly(row);
        assertThat(receipt).isEqualTo(original);
        assertThat(encoded).doesNotContain("café", "Literal clinical content");
        assertThat(row.toString()).isEqualTo("CdsNativeRowState[protected]");
    }

    @Test
    void shouldStoreOnlyASealedWitness_thatCarriesNoReversiblePerColumnDigest() throws Exception {
        // IMP-1: an unsalted per-column SHA-256 of a sex, birth date or health card column can be
        // reversed by enumeration, so the stored form must not contain any of them.
        var row = vector();
        String receipt = receipt(row, false);
        var keys = io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineTestKeys.keys();
        String sealed = CdsNativeBaselineCodec.seal(receipt, sha(receipt), List.of(row), keys);
        for (var cell : row.cells())
            if (cell.sha256() != null) assertThat(sealed).doesNotContain(cell.sha256());
        assertThat(sealed).contains("\"protocol\":3", "\"keyId\":\"test-active\"");
        assertThat(sealed).doesNotContain("\"rows\"", "\"cells\"", "\"bytes\"");
        assertThat(CdsNativeBaselineCodec.unseal(sealed, receipt, sha(receipt), keys).rows())
                .containsExactly(row);
        assertThat(
                        CdsNativeBaselineCodec.decode(
                                        CdsNativeBaselineCodec.unsealedText(
                                                sealed, receipt, sha(receipt), keys),
                                        receipt,
                                        sha(receipt))
                                .rows())
                .containsExactly(row);
        // Two seals of the same witness differ (fresh nonce); both open.
        String again = CdsNativeBaselineCodec.seal(receipt, sha(receipt), List.of(row), keys);
        assertThat(again).isNotEqualTo(sealed);
        assertThat(CdsNativeBaselineCodec.unseal(again, receipt, sha(receipt), keys).rows())
                .containsExactly(row);
    }

    @Test
    void shouldOpenAWitnessWithTheKeyItRecords_afterTheActiveKeyRotates() throws Exception {
        var row = vector();
        String receipt = receipt(row, false);
        var keys = io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineTestKeys.keys();
        String sealed = CdsNativeBaselineCodec.seal(receipt, sha(receipt), List.of(row), keys);
        var rotated =
                io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineTestKeys
                        .rotated();
        assertThat(CdsNativeBaselineCodec.unseal(sealed, receipt, sha(receipt), rotated).rows())
                .containsExactly(row);
        assertThat(CdsNativeBaselineCodec.seal(receipt, sha(receipt), List.of(row), rotated))
                .contains("\"keyId\":\"test-retired\"");
    }

    @Test
    void shouldRefuseClearly_whenTheInstallationKeyIsMissing() throws Exception {
        var row = vector();
        String receipt = receipt(row, false);
        var none = io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineTestKeys.none();
        assertThatThrownBy(
                        () -> CdsNativeBaselineCodec.seal(receipt, sha(receipt), List.of(row), none))
                .isInstanceOf(java.io.IOException.class)
                .hasMessage("CDS_NATIVE_BASELINE_KEY_UNAVAILABLE");
        String sealed =
                CdsNativeBaselineCodec.seal(
                        receipt,
                        sha(receipt),
                        List.of(row),
                        io.github.carlos_emr.carlos.demographic.cds.security
                                .CdsNativeBaselineTestKeys.keys());
        assertThatThrownBy(() -> CdsNativeBaselineCodec.unseal(sealed, receipt, sha(receipt), none))
                .isInstanceOf(java.io.IOException.class)
                .hasMessage("CDS_NATIVE_BASELINE_KEY_UNAVAILABLE");
        var properties = new java.util.Properties();
        properties.setProperty("cds.nativeBaseline.activeKey", "configured");
        assertThatThrownBy(
                        () ->
                                io.github.carlos_emr.carlos.demographic.cds.security
                                        .CdsNativeBaselineKeys.fromProperties(properties)
                                        .requireActiveKey())
                .hasMessage("CDS_NATIVE_BASELINE_KEY_UNAVAILABLE");
    }

    @Test
    void shouldRefuseUnsealedTamperedOrRebound_storedWitnesses() throws Exception {
        var row = vector();
        String receipt = receipt(row, false);
        String digest = sha(receipt);
        var keys = io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineTestKeys.keys();
        String plain = CdsNativeBaselineCodec.encode(receipt, digest, List.of(row));
        assertThatThrownBy(() -> CdsNativeBaselineCodec.unseal(plain, receipt, digest, keys))
                .isInstanceOf(IllegalArgumentException.class);
        String sealed = CdsNativeBaselineCodec.seal(receipt, digest, List.of(row), keys);
        var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(sealed);
        byte[] body = java.util.Base64.getDecoder().decode(tree.get("sealed").textValue());
        body[0] ^= 1;
        String tampered =
                sealed.replace(
                        tree.get("sealed").textValue(),
                        java.util.Base64.getEncoder().encodeToString(body));
        assertThatThrownBy(() -> CdsNativeBaselineCodec.unseal(tampered, receipt, digest, keys))
                .isInstanceOf(IllegalArgumentException.class);
        String relabelled = sealed.replace("\"keyId\":\"test-active\"", "\"keyId\":\"test-retired\"");
        assertThatThrownBy(() -> CdsNativeBaselineCodec.unseal(relabelled, receipt, digest, keys))
                .isInstanceOf(IllegalArgumentException.class);
        String other = receipt(row, true);
        assertThatThrownBy(() -> CdsNativeBaselineCodec.unseal(sealed, other, sha(other), keys))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldDetectOnlyTheChangedSqlProjection_andKeepNullDistinctFromEmpty() {
        var previous = vector();
        var values = new ArrayList<>(previous.cells());
        values.set(2, new CdsNativeRowState.Cell(false, 0, EMPTY));
        var current =
                new CdsNativeRowState(previous.table(), previous.id(), previous.columns(), values);
        var comparison = CdsNativeRowComparison.compare(previous, current);
        assertThat(comparison.changedColumns()).containsExactly("absent");
        assertThat(comparison.unqualifiedColumns()).isEmpty();
        assertThat(comparison.unchangedProjection()).isFalse();
        assertThat(comparison.schemaChanged()).isFalse();
        assertThat(CdsNativeRowComparison.compare(previous, previous).unchangedProjection())
                .isTrue();
    }

    @Test
    void
            shouldRequireReviewForSchemaChangesAndNonNullFloatOrTimestampDisplay_insteadOfClaimingNativeEquivalence() {
        for (String type : List.of("FLOAT", "DOUBLE", "REAL", "TIMESTAMP", "TIMESTAMP(6)")) {
            var column = new CdsNativeRowDigest.Column("value", type, 10, 0);
            var row =
                    new CdsNativeRowState(
                            "demographic",
                            "1",
                            List.of(column),
                            List.of(new CdsNativeRowState.Cell(false, 0, EMPTY)));
            assertThat(CdsNativeRowComparison.compare(row, row).unqualifiedColumns())
                    .containsExactly("value");
            assertThat(CdsNativeRowComparison.compare(row, row).unchangedProjection()).isFalse();
            var absent =
                    new CdsNativeRowState(
                            "demographic",
                            "1",
                            List.of(column),
                            List.of(new CdsNativeRowState.Cell(true, 0, null)));
            assertThat(CdsNativeRowComparison.compare(absent, absent).unchangedProjection())
                    .isTrue();
        }
        var row = vector();
        var columns = new ArrayList<>(row.columns());
        columns.set(0, new CdsNativeRowDigest.Column("id", "BIGINT", 19, 0));
        assertThat(
                        CdsNativeRowComparison.compare(
                                        row,
                                        new CdsNativeRowState(
                                                row.table(), row.id(), columns, row.cells()))
                                .schemaChanged())
                .isTrue();
    }

    @Test
    void shouldRefuseMissingExtraDuplicateOrChangedWitnessRows_andAnUnrelatedReceipt()
            throws Exception {
        var row = vector();
        String references = receipt(row, false);
        String digest = sha(references);
        String encoded = CdsNativeBaselineCodec.encode(references, digest, List.of(row));
        var cells = new ArrayList<>(row.cells());
        cells.set(0, new CdsNativeRowState.Cell(true, 0, null));
        var changed = new CdsNativeRowState(row.table(), row.id(), row.columns(), cells);
        for (var rows :
                List.of(
                        List.of(changed),
                        List.of(row, row),
                        List.of(
                                row,
                                new CdsNativeRowState(
                                        "allergies", "99", row.columns(), row.cells())))) {
            assertThatThrownBy(() -> CdsNativeBaselineCodec.encode(references, digest, rows))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> CdsNativeBaselineCodec.decode(encoded, references, "0".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CdsNativeBaselineCodec.decode(encoded, references + " ", digest))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CdsNativeBaselineCodec.decode(null, references, digest))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectMalformedOrCoercedMetadata_withoutDefaultingMissingCellState()
            throws Exception {
        var row = vector();
        String references = receipt(row, false);
        String digest = sha(references);
        String encoded = CdsNativeBaselineCodec.encode(references, digest, List.of(row));
        for (String changed :
                List.of(
                        encoded.replace("\"protocol\":2", "\"protocol\":\"2\""),
                        encoded.replaceFirst("\\\"sqlNull\\\":false,", ""),
                        encoded.replaceFirst("\\\"bytes\\\":2", "\"bytes\":\"2\""),
                        encoded.replace("\"protocol\":2", "\"protocol\":2,\"protocol\":2"),
                        encoded + " {}")) {
            assertThatThrownBy(() -> CdsNativeBaselineCodec.decode(changed, references, digest))
                    .isInstanceOf(java.io.IOException.class);
        }
    }

    @Test
    void shouldDetectAChangedExactFloat_whenBothValuesHaveTheSameLegacySqlText() throws Exception {
        var low = new CdsExactNumeric(CdsExactNumeric.Codec.IEEE754_BINARY32_V1, "3dcccccd");
        var high = new CdsExactNumeric(CdsExactNumeric.Codec.IEEE754_BINARY32_V1, "3dccccce");
        var columns = List.of(new CdsNativeRowDigest.Column("dose", "FLOAT", 12, 31));
        var before =
                new CdsNativeRowState(
                        "drugs",
                        "27",
                        columns,
                        List.of(new CdsNativeRowState.Cell(false, 3, sha("0.1"), low.witness())));
        var after =
                new CdsNativeRowState(
                        "drugs",
                        "27",
                        columns,
                        List.of(new CdsNativeRowState.Cell(false, 3, sha("0.1"), high.witness())));
        assertThat(before.digest()).isEqualTo(after.digest());
        assertThat(CdsNativeRowComparison.compare(before, after).changedColumns())
                .containsExactly("dose");
        assertThat(CdsNativeRowComparison.compare(before, after).unqualifiedColumns()).isEmpty();
        assertThat(CdsNativeRowComparison.compare(before, before).unchangedProjection()).isTrue();
        var legacy =
                new CdsNativeRowState(
                        "drugs",
                        "27",
                        columns,
                        List.of(new CdsNativeRowState.Cell(false, 3, sha("0.1"))));
        assertThat(CdsNativeRowComparison.compare(legacy, before).unqualifiedColumns())
                .containsExactly("dose");
        String refs = receipt(before, false);
        var encoded = CdsNativeBaselineCodec.encode(refs, sha(refs), List.of(before));
        assertThat(CdsNativeBaselineCodec.decode(encoded, refs, sha(refs)).rows())
                .containsExactly(before);
        assertThat(encoded).doesNotContain("3dcccccd", "0.1");
    }

    @Test
    void shouldReadExactOriginalVersionOneBaselines_withoutInventingNumericWitnesses()
            throws Exception {
        var row = vector();
        String refs = receipt(row, false);
        String old =
                CdsNativeBaselineCodec.encode(refs, sha(refs), List.of(row))
                        .replace("\"protocol\":2", "\"protocol\":1")
                        .replace(",\"exactNumeric\":null", "");
        assertThat(CdsNativeBaselineCodec.decode(old, refs, sha(refs)).rows()).containsExactly(row);
        assertThatThrownBy(
                        () ->
                                CdsNativeBaselineCodec.decode(
                                        old.replace(
                                                "\"sqlNull\":true",
                                                "\"exactNumeric\":null,\"sqlNull\":true"),
                                        refs,
                                        sha(refs)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectInvalidOrMismatchedNumericCodecs_withoutNormalizingNonfiniteValues()
            throws Exception {
        for (String bits : List.of("7f800000", "7fc00000", "ff800000", "3DCCCCCD", "123"))
            assertThatThrownBy(
                            () ->
                                    new CdsExactNumeric(
                                            CdsExactNumeric.Codec.IEEE754_BINARY32_V1, bits))
                    .isInstanceOf(IllegalArgumentException.class);
        var number =
                new CdsExactNumeric(CdsExactNumeric.Codec.IEEE754_BINARY64_V1, "3fb999999999999a");
        assertThat(number.decimal()).isEqualTo("0.1");
        assertThat(number.toString()).isEqualTo("CdsExactNumeric[protected]");
        assertThatThrownBy(
                        () ->
                                new CdsNativeRowState(
                                        "drugs",
                                        "27",
                                        List.of(
                                                new CdsNativeRowDigest.Column(
                                                        "dose", "FLOAT", 12, 31)),
                                        List.of(
                                                new CdsNativeRowState.Cell(
                                                        false, 3, sha("0.1"), number.witness()))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldSealAndOpenEveryFieldPageWitness_ofTheFullAllCategoriesFixture() throws Exception {
        // P1 regression: the stored envelope was read with the document's 65,536-character string
        // bound, so a witness over about 48 KiB (a Demographics field page of an ordinary chart)
        // could be sealed but not opened, and every such import stopped with
        // NATIVE_BASELINE_REQUIRES_REVIEW. Each 32-field page of the unmodified fixture becomes the
        // cds_import_field rows the importer writes, sealed and opened as the unit commit does.
        var keys =
                io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineTestKeys
                        .keys();
        var folder = java.nio.file.Files.createTempDirectory("cds-baseline-fixture-");
        try {
            var file = folder.resolve("patient.xml");
            try (var input =
                    getClass().getResourceAsStream("/demographic/cds-import-all-categories.xml")) {
                java.nio.file.Files.copy(java.util.Objects.requireNonNull(input), file);
            }
            var records =
                    new ArrayList<io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto>();
            new io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsXmlParser()
                    .parse(
                            file,
                            folder.resolve("parsed"),
                            io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto
                                    .defaults(),
                            () -> false,
                            records::add);
            var parser = new io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsFieldParser();
            int pages = 0, largest = 0;
            boolean demographics = false;
            for (var record : records) {
                var slices =
                        new ArrayList<io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto>();
                parser.forEachPage(record, 32, slices::add);
                for (var page : slices) {
                    var rows = new ArrayList<CdsNativeRowState>();
                    parser.forEachField(
                            page,
                            field -> {
                                try {
                                    rows.add(fieldRow(rows.size(), record, page, parser, field));
                                } catch (Exception failed) {
                                    throw new IllegalStateException(failed);
                                }
                            });
                    String references = receipt(rows);
                    String sealed =
                            CdsNativeBaselineCodec.seal(references, sha(references), rows, keys);
                    assertThat(
                                    CdsNativeBaselineCodec.unseal(
                                                    sealed, references, sha(references), keys)
                                            .rows())
                            .containsExactlyElementsOf(rows);
                    largest = Math.max(largest, sealed.length());
                    demographics |= record.section().equals("Demographics") && rows.size() == 32;
                    pages++;
                }
            }
            assertThat(pages).isGreaterThan(records.size());
            assertThat(demographics).isTrue();
            // The fixture's largest page is beyond the bound that refused it.
            assertThat(largest).isGreaterThan(65_536);
        } finally {
            try (var paths = java.nio.file.Files.walk(folder)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                    java.nio.file.Files.delete(path);
            }
        }
    }

    @Test
    void shouldSealAndOpenTheImportersLargestWitness_atTheDocumentAndEnvelopeMaxima()
            throws Exception {
        // 192 rows (the receipt reference bound) of the widest target table (drugs, 65 columns),
        // every column and cell at its validated maximum, the deepest cells holding exact
        // numeric witnesses (the six-level nesting bound).
        var rows = new ArrayList<CdsNativeRowState>();
        for (int row = 0; row < 192; row++) rows.add(widest(row, 65));
        String references = receipt(rows);
        assertThat(references.length()).isLessThanOrEqualTo(65_536);
        String document = CdsNativeBaselineCodec.encode(references, sha(references), rows);
        assertThat(document.length())
                .isGreaterThan(5 << 20)
                .isLessThanOrEqualTo(CdsNativeBaselineCodec.MAX_BYTES);
        var keys =
                io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineTestKeys
                        .keys();
        String sealed = CdsNativeBaselineCodec.seal(references, sha(references), rows, keys);
        assertThat(sealed.length()).isLessThanOrEqualTo(CdsNativeBaselineCodec.MAX_SEALED_BYTES);
        assertThat(
                        CdsNativeBaselineCodec.unseal(sealed, references, sha(references), keys)
                                .rows())
                .containsExactlyElementsOf(rows);
        // The envelope bound admits exactly the Base64 of a MAX_BYTES document and its tag.
        assertThat(CdsNativeBaselineCodec.MAX_SEALED_TEXT)
                .isEqualTo(
                        java.util.Base64.getEncoder()
                                .encodeToString(new byte[CdsNativeBaselineCodec.MAX_BYTES + 16])
                                .length())
                .isLessThan(CdsNativeBaselineCodec.MAX_SEALED_BYTES - 256);
        // A wider row set than any target table is refused when it is encoded, never truncated.
        var oversized = new ArrayList<CdsNativeRowState>();
        for (int row = 0; row < 192; row++) oversized.add(widest(row, 100));
        String wide = receipt(oversized);
        assertThatThrownBy(() -> CdsNativeBaselineCodec.encode(wide, sha(wide), oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CDS_NATIVE_BASELINE_REQUIRES_REVIEW");
    }

    @Test
    void shouldStillRefuseOversizedOrDeeperEnvelopesAndDocuments_beyondTheirDerivedBounds()
            throws Exception {
        var row = vector();
        String references = receipt(row, false), digest = sha(references);
        var keys =
                io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineTestKeys
                        .keys();
        String sealed = CdsNativeBaselineCodec.seal(references, digest, List.of(row), keys);
        var envelope =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(sealed);
        var overlong = envelope.deepCopy();
        overlong.put("sealed", "A".repeat(CdsNativeBaselineCodec.MAX_SEALED_TEXT + 4));
        assertThatThrownBy(
                        () ->
                                CdsNativeBaselineCodec.unseal(
                                        overlong.toString(), references, digest, keys))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CDS_NATIVE_BASELINE_REQUIRES_REVIEW");
        var nested = envelope.deepCopy();
        nested.putObject("keyId").put("id", "test-active");
        assertThatThrownBy(
                        () -> CdsNativeBaselineCodec.unseal(nested.toString(), references, digest, keys))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CDS_NATIVE_BASELINE_REQUIRES_REVIEW");
        String document = CdsNativeBaselineCodec.encode(references, digest, List.of(row));
        String longText = document.replace("\"absent\"", "\"" + "x".repeat(129) + "\"");
        assertThatThrownBy(() -> CdsNativeBaselineCodec.decode(longText, references, digest))
                .isInstanceOf(com.fasterxml.jackson.core.exc.StreamConstraintsException.class);
        var tree =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(document);
        ((com.fasterxml.jackson.databind.node.ObjectNode)
                        tree.withArray("rows").get(0).withArray("cells").get(0))
                .putObject("exactNumeric")
                .putObject("codec")
                .put("deeper", "IEEE754_BINARY64_V1");
        assertThatThrownBy(
                        () -> CdsNativeBaselineCodec.decode(tree.toString(), references, digest))
                .isInstanceOf(com.fasterxml.jackson.core.exc.StreamConstraintsException.class);
    }

    /** One row of the widest receipt target at every validated column and cell maximum. */
    private static CdsNativeRowState widest(int row, int width) {
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (int i = 0; i < width; i++) {
            boolean exact = i == 0;
            String name = String.format("%03d", i) + "n".repeat(125);
            columns.add(
                    new CdsNativeRowDigest.Column(
                            name,
                            exact ? "DOUBLE" : "T".repeat(128),
                            Integer.MAX_VALUE,
                            Integer.MAX_VALUE));
            cells.add(
                    new CdsNativeRowState.Cell(
                            false,
                            Long.MAX_VALUE,
                            "f".repeat(64),
                            exact
                                    ? new CdsExactNumeric.Witness(
                                            CdsExactNumeric.Codec.IEEE754_BINARY64_V1,
                                            "e".repeat(64))
                                    : null));
        }
        return new CdsNativeRowState(
                "t".repeat(64), String.format("%064d", row), columns, cells);
    }

    /**
     * The cds_import_field row CdsSourceFieldService writes for one source field, as the MariaDB
     * receipt reader reports its physical columns.
     */
    private static CdsNativeRowState fieldRow(
            int index,
            io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto record,
            io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto page,
            io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsFieldParser parser,
            io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto field)
            throws Exception {
        String text = field.kind().equals("BINARY") ? null : parser.readText(page, field, 32768);
        String logical =
                field.kind().equals("BINARY") ? null : parser.readLogicalText(page, field, 65536);
        String id = String.format("%064x", (long) record.ordinal() * 100_000 + index);
        var values = new java.util.LinkedHashMap<String, String>();
        values.put("source_namespace", field.namespace());
        values.put("id", id);
        values.put("run_id", "11111111-1111-4111-8111-111111111111");
        values.put("patient_key", "b".repeat(64));
        values.put("unit_key", "c".repeat(64));
        values.put("demographic_no", "27");
        values.put("category", record.section());
        values.put(
                "source_path",
                page.sourceXPath() + (field.path().isEmpty() ? "" : "/" + field.path()));
        values.put("occurrence", Long.toString(page.ordinal()));
        values.put("value_type", field.schemaType().isBlank() ? field.kind() : field.schemaType());
        values.put("date_precision", null);
        values.put("original_value", text);
        values.put("normalized_value", logical);
        values.put("payload_location", text == null ? "/var/lib/carlos-emr/values.bin" : null);
        values.put("payload_digest", text == null ? field.sha256() : null);
        values.put("payload_offset", text == null ? Long.toString(field.offset()) : "0");
        values.put("payload_length", text == null ? Long.toString(field.length()) : "0");
        var types =
                java.util.Map.ofEntries(
                        java.util.Map.entry("source_namespace", new Object[] {"VARCHAR", 250}),
                        java.util.Map.entry("id", new Object[] {"VARCHAR", 64}),
                        java.util.Map.entry("run_id", new Object[] {"VARCHAR", 36}),
                        java.util.Map.entry("patient_key", new Object[] {"VARCHAR", 64}),
                        java.util.Map.entry("unit_key", new Object[] {"VARCHAR", 64}),
                        java.util.Map.entry("demographic_no", new Object[] {"INTEGER", 11}),
                        java.util.Map.entry("category", new Object[] {"VARCHAR", 64}),
                        java.util.Map.entry("source_path", new Object[] {"VARCHAR", 2048}),
                        java.util.Map.entry("occurrence", new Object[] {"BIGINT", 20}),
                        java.util.Map.entry("value_type", new Object[] {"VARCHAR", 4608}),
                        java.util.Map.entry("date_precision", new Object[] {"VARCHAR", 32}),
                        java.util.Map.entry("original_value", new Object[] {"LONGTEXT", -1}),
                        java.util.Map.entry("normalized_value", new Object[] {"LONGTEXT", -1}),
                        java.util.Map.entry("payload_location", new Object[] {"VARCHAR", 1024}),
                        java.util.Map.entry("payload_digest", new Object[] {"VARCHAR", 64}),
                        java.util.Map.entry("payload_offset", new Object[] {"BIGINT", 20}),
                        java.util.Map.entry("payload_length", new Object[] {"BIGINT", 20}));
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (var entry : values.entrySet()) {
            var type = types.get(entry.getKey());
            int precision = (Integer) type[1];
            columns.add(
                    new CdsNativeRowDigest.Column(
                            entry.getKey(),
                            (String) type[0],
                            precision < 0 ? Integer.MAX_VALUE : precision,
                            0));
            String value = entry.getValue();
            byte[] bytes = value == null ? null : value.getBytes(StandardCharsets.UTF_8);
            cells.add(
                    value == null
                            ? new CdsNativeRowState.Cell(true, 0, null)
                            : new CdsNativeRowState.Cell(
                                    false,
                                    bytes.length,
                                    HexFormat.of()
                                            .formatHex(
                                                    MessageDigest.getInstance("SHA-256")
                                                            .digest(bytes))));
        }
        return new CdsNativeRowState("cds_import_field", id, columns, cells);
    }

    private static String receipt(List<CdsNativeRowState> rows) {
        var refs = new StringBuilder();
        for (var row : rows) {
            if (refs.length() > 0) refs.append(',');
            refs.append("{\"table\":\"")
                    .append(row.table())
                    .append("\",\"id\":\"")
                    .append(row.id())
                    .append("\",\"digest\":\"")
                    .append(row.digest())
                    .append("\"}");
        }
        return "{\"protocol\":2,\"refs\":[" + refs + "]}";
    }

    private static CdsNativeRowState vector() {
        var columns =
                List.of(
                        new CdsNativeRowDigest.Column("id", "INT", 10, 0),
                        new CdsNativeRowDigest.Column("note", "MEDIUMTEXT", 16777215, 0),
                        new CdsNativeRowDigest.Column("absent", "VARCHAR", 10, 0),
                        new CdsNativeRowDigest.Column("empty", "VARCHAR", 10, 0));
        return new CdsNativeRowState(
                "demographic",
                "27",
                columns,
                List.of(
                        new CdsNativeRowState.Cell(
                                false,
                                2,
                                "670671cd97404156226e507973f2ab8330d3022ca96e0c93bdbdb320c41adcaf"),
                        new CdsNativeRowState.Cell(
                                false,
                                12,
                                "d9f9ab3a08c8ce04a9e5a2971bc100f0a087af7bd73908121753a70070aa229b"),
                        new CdsNativeRowState.Cell(true, 0, null),
                        new CdsNativeRowState.Cell(false, 0, EMPTY)));
    }

    private static String receipt(CdsNativeRowState row, boolean duplicate) {
        String ref =
                "{\"table\":\""
                        + row.table()
                        + "\",\"id\":\""
                        + row.id()
                        + "\",\"digest\":\""
                        + row.digest()
                        + "\"}";
        return "{\"protocol\":2,\"refs\":[" + ref + (duplicate ? "," + ref : "") + "]}";
    }

    private static String sha(String value) throws Exception {
        return HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
