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
    private static final String EMPTY = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    @Test void shouldBindFieldWitnessesToTheExactOriginalReceipt_withoutChangingTheIndependentLegacyVector() throws Exception {
        var row = vector(); assertThat(row.digest()).isEqualTo("8ed69a9427270643a2704375dc7afebbc6e35cc991ea3589fce34ab7b8230bc7");
        String receipt = receipt(row, true); String original = receipt;
        String encoded = CdsNativeBaselineCodec.encode(receipt, sha(receipt), List.of(row));
        var restored = CdsNativeBaselineCodec.decode(encoded, receipt, sha(receipt));
        assertThat(restored.rows()).containsExactly(row); assertThat(receipt).isEqualTo(original);
        assertThat(encoded).doesNotContain("café", "Literal clinical content");
        assertThat(row.toString()).isEqualTo("CdsNativeRowState[protected]");
    }
    @Test void shouldDetectOnlyTheChangedSqlProjection_andKeepNullDistinctFromEmpty() {
        var previous = vector(); var values = new ArrayList<>(previous.cells()); values.set(2, new CdsNativeRowState.Cell(false, 0, EMPTY));
        var current = new CdsNativeRowState(previous.table(), previous.id(), previous.columns(), values);
        var comparison = CdsNativeRowComparison.compare(previous, current);
        assertThat(comparison.changedColumns()).containsExactly("absent"); assertThat(comparison.unqualifiedColumns()).isEmpty();
        assertThat(comparison.unchangedProjection()).isFalse(); assertThat(comparison.schemaChanged()).isFalse();
        assertThat(CdsNativeRowComparison.compare(previous, previous).unchangedProjection()).isTrue();
    }
    @Test void shouldRequireReviewForSchemaChangesAndNonNullFloatOrTimestampDisplay_insteadOfClaimingNativeEquivalence() {
        for (String type : List.of("FLOAT", "DOUBLE", "REAL", "TIMESTAMP", "TIMESTAMP(6)")) {
            var column = new CdsNativeRowDigest.Column("value", type, 10, 0);
            var row = new CdsNativeRowState("demographic", "1", List.of(column), List.of(new CdsNativeRowState.Cell(false, 0, EMPTY)));
            assertThat(CdsNativeRowComparison.compare(row, row).unqualifiedColumns()).containsExactly("value");
            assertThat(CdsNativeRowComparison.compare(row, row).unchangedProjection()).isFalse();
            var absent = new CdsNativeRowState("demographic", "1", List.of(column), List.of(new CdsNativeRowState.Cell(true, 0, null)));
            assertThat(CdsNativeRowComparison.compare(absent, absent).unchangedProjection()).isTrue();
        }
        var row = vector(); var columns = new ArrayList<>(row.columns()); columns.set(0, new CdsNativeRowDigest.Column("id", "BIGINT", 19, 0));
        assertThat(CdsNativeRowComparison.compare(row, new CdsNativeRowState(row.table(), row.id(), columns, row.cells())).schemaChanged()).isTrue();
    }
    @Test void shouldRefuseMissingExtraDuplicateOrChangedWitnessRows_andAnUnrelatedReceipt() throws Exception {
        var row = vector(); String references = receipt(row, false); String digest = sha(references);
        String encoded = CdsNativeBaselineCodec.encode(references, digest, List.of(row));
        var cells = new ArrayList<>(row.cells()); cells.set(0, new CdsNativeRowState.Cell(true, 0, null));
        var changed = new CdsNativeRowState(row.table(), row.id(), row.columns(), cells);
        for (var rows : List.of(List.of(changed), List.of(row, row), List.of(row, new CdsNativeRowState("allergies", "99", row.columns(), row.cells())))) {
            assertThatThrownBy(() -> CdsNativeBaselineCodec.encode(references, digest, rows)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> CdsNativeBaselineCodec.decode(encoded, references, "0".repeat(64))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CdsNativeBaselineCodec.decode(encoded, references + " ", digest)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CdsNativeBaselineCodec.decode(null, references, digest)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void shouldRejectMalformedOrCoercedMetadata_withoutDefaultingMissingCellState() throws Exception {
        var row = vector(); String references = receipt(row, false); String digest = sha(references);
        String encoded = CdsNativeBaselineCodec.encode(references, digest, List.of(row));
        for (String changed : List.of(encoded.replace("\"protocol\":2", "\"protocol\":\"2\""),
                encoded.replaceFirst("\\\"sqlNull\\\":false,", ""), encoded.replaceFirst("\\\"bytes\\\":2", "\"bytes\":\"2\""),
                encoded.replace("\"protocol\":2", "\"protocol\":2,\"protocol\":2"), encoded + " {}")) {
            assertThatThrownBy(() -> CdsNativeBaselineCodec.decode(changed, references, digest)).isInstanceOf(java.io.IOException.class);
        }
    }
    @Test void shouldDetectAChangedExactFloat_whenBothValuesHaveTheSameLegacySqlText() throws Exception {
        var low = new CdsExactNumeric(CdsExactNumeric.Codec.IEEE754_BINARY32_V1, "3dcccccd");
        var high = new CdsExactNumeric(CdsExactNumeric.Codec.IEEE754_BINARY32_V1, "3dccccce");
        var columns = List.of(new CdsNativeRowDigest.Column("dose", "FLOAT", 12, 31));
        var before = new CdsNativeRowState("drugs", "27", columns, List.of(new CdsNativeRowState.Cell(false, 3, sha("0.1"), low.witness())));
        var after = new CdsNativeRowState("drugs", "27", columns, List.of(new CdsNativeRowState.Cell(false, 3, sha("0.1"), high.witness())));
        assertThat(before.digest()).isEqualTo(after.digest());
        assertThat(CdsNativeRowComparison.compare(before, after).changedColumns()).containsExactly("dose");
        assertThat(CdsNativeRowComparison.compare(before, after).unqualifiedColumns()).isEmpty();
        assertThat(CdsNativeRowComparison.compare(before, before).unchangedProjection()).isTrue();
        var legacy = new CdsNativeRowState("drugs", "27", columns, List.of(new CdsNativeRowState.Cell(false, 3, sha("0.1"))));
        assertThat(CdsNativeRowComparison.compare(legacy, before).unqualifiedColumns()).containsExactly("dose");
        String refs = receipt(before, false); var encoded = CdsNativeBaselineCodec.encode(refs, sha(refs), List.of(before));
        assertThat(CdsNativeBaselineCodec.decode(encoded, refs, sha(refs)).rows()).containsExactly(before);
        assertThat(encoded).doesNotContain("3dcccccd", "0.1");
    }
    @Test void shouldReadExactOriginalVersionOneBaselines_withoutInventingNumericWitnesses() throws Exception {
        var row = vector(); String refs = receipt(row, false);
        String old = CdsNativeBaselineCodec.encode(refs, sha(refs), List.of(row)).replace("\"protocol\":2", "\"protocol\":1").replace(",\"exactNumeric\":null", "");
        assertThat(CdsNativeBaselineCodec.decode(old, refs, sha(refs)).rows()).containsExactly(row);
        assertThatThrownBy(() -> CdsNativeBaselineCodec.decode(old.replace("\"sqlNull\":true", "\"exactNumeric\":null,\"sqlNull\":true"), refs, sha(refs)))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void shouldRejectInvalidOrMismatchedNumericCodecs_withoutNormalizingNonfiniteValues() throws Exception {
        for (String bits : List.of("7f800000", "7fc00000", "ff800000", "3DCCCCCD", "123"))
            assertThatThrownBy(() -> new CdsExactNumeric(CdsExactNumeric.Codec.IEEE754_BINARY32_V1, bits)).isInstanceOf(IllegalArgumentException.class);
        var number = new CdsExactNumeric(CdsExactNumeric.Codec.IEEE754_BINARY64_V1, "3fb999999999999a");
        assertThat(number.decimal()).isEqualTo("0.1"); assertThat(number.toString()).isEqualTo("CdsExactNumeric[protected]");
        assertThatThrownBy(() -> new CdsNativeRowState("drugs", "27", List.of(new CdsNativeRowDigest.Column("dose", "FLOAT", 12, 31)),
                List.of(new CdsNativeRowState.Cell(false, 3, sha("0.1"), number.witness())))).isInstanceOf(IllegalArgumentException.class);
    }
    private static CdsNativeRowState vector() {
        var columns = List.of(new CdsNativeRowDigest.Column("id", "INT", 10, 0), new CdsNativeRowDigest.Column("note", "MEDIUMTEXT", 16777215, 0),
                new CdsNativeRowDigest.Column("absent", "VARCHAR", 10, 0), new CdsNativeRowDigest.Column("empty", "VARCHAR", 10, 0));
        return new CdsNativeRowState("demographic", "27", columns, List.of(
                new CdsNativeRowState.Cell(false, 2, "670671cd97404156226e507973f2ab8330d3022ca96e0c93bdbdb320c41adcaf"),
                new CdsNativeRowState.Cell(false, 12, "d9f9ab3a08c8ce04a9e5a2971bc100f0a087af7bd73908121753a70070aa229b"),
                new CdsNativeRowState.Cell(true, 0, null), new CdsNativeRowState.Cell(false, 0, EMPTY)));
    }
    private static String receipt(CdsNativeRowState row, boolean duplicate) {
        String ref = "{\"table\":\"" + row.table() + "\",\"id\":\"" + row.id() + "\",\"digest\":\"" + row.digest() + "\"}";
        return "{\"protocol\":2,\"refs\":[" + ref + (duplicate ? "," + ref : "") + "]}";
    }
    private static String sha(String value) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
}
