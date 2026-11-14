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
package io.github.carlos_emr.carlos.demographic.cdsexport.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsExactNumeric;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A row held packed for an in-memory index reads exactly as the row it was packed from, and a
 * derived column is computed from the stored text on every read without being kept.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportCapturedTextRowDtoUnitTest {
    private static CdsExportCapturedTextRowDto row() {
        var fields = new TreeMap<String, String>();
        fields.put("id", "7");
        fields.put("empty", "");
        fields.put("missing", null);
        fields.put("text", "naïve 😀 text");
        fields.put("amount", "0.1");
        fields.put("last", null);
        return new CdsExportCapturedTextRowDto(
                "cds_import_unit",
                "7",
                fields,
                Map.of(
                        "amount",
                        new CdsExactNumeric(CdsExactNumeric.Codec.IEEE754_BINARY32_V1, "3dcccccd")));
    }

    @Test
    void shouldReadAsTheOriginalRow_whenPacked() {
        var original = row();

        var packed = original.packed(null);

        assertThat(packed).isEqualTo(original);
        assertThat(packed.hashCode()).isEqualTo(original.hashCode());
        assertThat(packed.fields()).isEqualTo(original.fields());
        // The same sorted order, SQL null and empty text still distinct.
        assertThat(new ArrayList<>(packed.fields().keySet()))
                .containsExactly("amount", "empty", "id", "last", "missing", "text");
        assertThat(new ArrayList<>(packed.fields().entrySet()))
                .isEqualTo(new ArrayList<>(original.fields().entrySet()));
        assertThat(packed.fields().get("empty")).isEmpty();
        assertThat(packed.fields().get("missing")).isNull();
        assertThat(packed.fields().get("last")).isNull();
        assertThat(packed.fields().containsKey("missing")).isTrue();
        assertThat(packed.fields().containsKey("absent")).isFalse();
        assertThat(packed.fields().get("absent")).isNull();
        assertThat(packed.fields().get(7)).isNull();
        assertThat(packed.fields()).hasSize(6);
        assertThat(packed.exactNumbers()).isEqualTo(original.exactNumbers());
        assertThat(packed.packed(null)).isSameAs(packed);
        assertThatThrownBy(() -> packed.fields().put("id", "8"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> packed.fields().keySet().remove("id"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(packed.toString()).isEqualTo("CdsExportCapturedTextRowDto[protected]");
    }

    @Test
    void shouldKeepTheRowUnpacked_whenItsTextIsNotExactUnicode() {
        var fields = new TreeMap<String, String>();
        fields.put("id", "7");
        fields.put("text", "half \ud83d pair");
        var original = new CdsExportCapturedTextRowDto("cds_import_unit", "7", fields);

        var kept = original.packed(null);

        // Encoding would replace the unpaired surrogate, so the row is left as it is.
        assertThat(kept).isSameAs(original);
        assertThat(kept.fields().get("text")).isEqualTo("half \ud83d pair");
    }

    @Test
    void shouldComputeTheDerivedColumn_fromTheStoredTextOnEveryRead() {
        var original = row();
        var applied = new AtomicInteger();
        var seen = new ArrayList<String>();

        var derived =
                original.withDerived(
                        "text",
                        stored -> {
                            applied.incrementAndGet();
                            seen.add(stored);
                            return "opened:" + stored;
                        },
                        null);

        assertThat(applied).hasValue(0);
        assertThat(derived.fields().get("text")).isEqualTo("opened:naïve 😀 text");
        assertThat(derived.fields().get("text")).isEqualTo("opened:naïve 😀 text");
        assertThat(applied).hasValue(2);
        assertThat(seen).containsOnly("naïve 😀 text");
        // Other columns are untouched, and iteration applies the view as a read does.
        assertThat(derived.fields().get("id")).isEqualTo("7");
        assertThat(derived.fields().get("missing")).isNull();
        assertThat(new TreeMap<>(derived.fields()))
                .containsEntry("text", "opened:naïve 😀 text")
                .containsEntry("amount", "0.1")
                .hasSize(6);
        assertThat(derived.table()).isEqualTo("cds_import_unit");
        assertThat(derived.recordKey()).isEqualTo("7");
        assertThat(derived.exactNumbers()).isEqualTo(original.exactNumbers());
    }

    @Test
    void shouldLeaveASqlNullUnderived_whenTheDerivedColumnIsNull() {
        var derived =
                row().withDerived(
                                "missing",
                                stored -> {
                                    throw new AssertionError("a SQL null has no text to derive");
                                },
                                null);

        assertThat(derived.fields().get("missing")).isNull();
        assertThat(derived.fields().containsKey("missing")).isTrue();
    }

    @Test
    void shouldRefuseADerivedColumn_whenItIsAbsentNumericOrAlreadyDerived() {
        var original = row();
        var derived = original.withDerived("text", stored -> stored, null);

        for (Runnable refused :
                List.<Runnable>of(
                        () -> original.withDerived("absent", stored -> stored, null),
                        () -> original.withDerived("amount", stored -> stored, null),
                        () -> derived.withDerived("id", stored -> stored, null)))
            assertThatThrownBy(refused::run)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("CDS_EXPORT_NATIVE_ROW_INVALID");
        assertThatThrownBy(() -> original.withDerived("text", null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldReadEveryRowAlike_whenRowsShareOneLayout() {
        var first = row().packed(null);
        var fields = new TreeMap<>(row().fields());
        fields.put("id", "8");
        fields.put("text", "second");
        var second =
                new CdsExportCapturedTextRowDto("cds_import_unit", "8", fields).packed(first);
        // A row of other columns takes no layout from it.
        var other =
                new CdsExportCapturedTextRowDto("cds_import_run", "9", Map.of("id", "9"))
                        .packed(first);

        assertThat(second.fields())
                .containsEntry("id", "8")
                .containsEntry("text", "second")
                .containsEntry("empty", "");
        assertThat(first.fields()).containsEntry("id", "7").containsEntry("text", "naïve 😀 text");
        assertThat(other.fields()).containsOnly(Map.entry("id", "9"));
    }
}
