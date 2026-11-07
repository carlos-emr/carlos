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

import static org.assertj.core.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit") @Tag("cds-export")
class CdsExportAuthenticatedRangeInputStreamUnitTest {
    @Test void shouldExposeOnlyTheSelectedField_andAuthenticateTheWholeSourceOnEarlyClose() throws Exception {
        byte[] value = "café 😀 exact field".getBytes(StandardCharsets.UTF_8); var checked = new AtomicInteger(); var eof = new AtomicInteger();
        var source = new ByteArrayInputStream(join(value)) {
            @Override public synchronized int read(byte[] bytes, int offset, int length) { int count = super.read(bytes, offset, length); if (count == -1) eof.incrementAndGet(); return count; }
        };
        try (var range = new CdsExportAuthenticatedRangeInputStream(source, 6, value.length, sha(value), () -> {}, checked::incrementAndGet)) {
            assertThat(range.read()).isEqualTo(value[0]);
        }
        assertThat(checked).hasValue(1); assertThat(eof).hasValue(1);
        try (var range = new CdsExportAuthenticatedRangeInputStream(new ByteArrayInputStream(join(value)), 6, value.length, sha(value), () -> {}, () -> {})) {
            assertThat(range.readAllBytes()).isEqualTo(value); assertThat(range.read()).isEqualTo(-1);
        }
    }
    @Test void shouldRejectWrongFieldHashAndTruncation_includingAnEmptyField() throws Exception {
        for (byte[] value : new byte[][] { new byte[0], "field".getBytes(StandardCharsets.UTF_8) }) {
            var range = new CdsExportAuthenticatedRangeInputStream(new ByteArrayInputStream(join(value)), 6, value.length, "0".repeat(64), () -> {}, () -> {});
            assertThatThrownBy(range::close).hasMessage("CDS_EXPORT_VALUE_RANGE_DIGEST_MISMATCH");
        }
        assertThatThrownBy(() -> new CdsExportAuthenticatedRangeInputStream(new ByteArrayInputStream(new byte[1]), 6, 1, "0".repeat(64), () -> {}, () -> {}))
                .hasMessage("CDS_EXPORT_VALUE_RANGE_TRUNCATED");
        var shortValue = new CdsExportAuthenticatedRangeInputStream(new ByteArrayInputStream(new byte[6]), 6, 5, "0".repeat(64), () -> {}, () -> {});
        assertThatThrownBy(shortValue::close).hasMessage("CDS_EXPORT_VALUE_RANGE_TRUNCATED");
    }
    @Test void shouldRejectCorruptSuffixOrRevokedAuthority_atEofAndAtClose() throws Exception {
        byte[] value = "field".getBytes(StandardCharsets.UTF_8);
        for (boolean closeEarly : new boolean[] { true, false }) {
            var source = new ByteArrayInputStream(join(value)) {
                @Override public synchronized int read(byte[] bytes, int offset, int length) {
                    int count = super.read(bytes, offset, length); if (count == -1) throw new IllegalStateException("AUTHENTICATED_SUFFIX_CORRUPT"); return count;
                }
            };
            var range = new CdsExportAuthenticatedRangeInputStream(source, 6, value.length, sha(value), () -> {}, () -> {});
            assertThatThrownBy(() -> { if (closeEarly) range.close(); else range.readAllBytes(); }).hasMessage("AUTHENTICATED_SUFFIX_CORRUPT");
            range.close();
            var revoked = new CdsExportAuthenticatedRangeInputStream(new ByteArrayInputStream(join(value)), 6, value.length, sha(value), () -> {},
                    () -> { throw new IllegalStateException("AUTHORITY_REVOKED"); });
            assertThatThrownBy(() -> { if (closeEarly) revoked.close(); else revoked.readAllBytes(); }).hasMessage("AUTHORITY_REVOKED"); revoked.close();
        }
    }
    private static byte[] join(byte[] value) throws IOException {
        var output = new ByteArrayOutputStream(); output.write("prefix".getBytes(StandardCharsets.UTF_8)); output.write(value);
        output.write("private unrelated suffix".getBytes(StandardCharsets.UTF_8)); return output.toByteArray();
    }
    private static String sha(byte[] value) throws Exception { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value)); }
}
