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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadDao;
import io.github.carlos_emr.carlos.commn.dto.CdsExportRawCellDto;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("cds-export")
class CdsExportRawValueInputStreamUnitTest {
    @Test
    void shouldStreamExactLargeValues_inBoundedPatientAndRowBoundRanges() throws Exception {
        byte[] bytes = ("FAKE-CDS café 😀\r\n").repeat(10000).getBytes(StandardCharsets.UTF_8);
        var source = mock(CdsExportPatientTableReadDao.class);
        var calls = new AtomicInteger();
        when(source.readCdsExportValueChunk(eq(27), eq(-42L), eq("value"), anyLong(), anyInt()))
                .thenAnswer(
                        call -> {
                            long offset = call.getArgument(3);
                            int length = call.getArgument(4);
                            assertThat(length).isBetween(1, 65_536);
                            calls.incrementAndGet();
                            return Arrays.copyOfRange(
                                    bytes,
                                    Math.toIntExact(offset),
                                    Math.toIntExact(offset) + length);
                        });
        try (var input =
                new CdsExportRawValueInputStream(
                        source,
                        27,
                        -42,
                        "value",
                        new CdsExportRawCellDto(false, bytes.length, sha(bytes), null))) {
            assertThat(input.readAllBytes()).isEqualTo(bytes);
            assertThat(input.read()).isEqualTo(-1);
        }
        assertThat(calls.get()).isEqualTo((bytes.length + 65_535) / 65_536);
    }

    @Test
    void shouldVerifyInlineAndEmptyValues_andKeepNullDistinct() throws Exception {
        var source = mock(CdsExportPatientTableReadDao.class);
        byte[] bytes = "FAKE-CDS 😀".getBytes(StandardCharsets.UTF_8);
        var cell = new CdsExportRawCellDto(false, bytes.length, sha(bytes), bytes);
        bytes[0] ^= 1;
        byte[] returned = cell.inlineBytes();
        returned[0] ^= 1;
        try (var input = new CdsExportRawValueInputStream(source, 27, 1, "value", cell)) {
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("FAKE-CDS 😀");
        }
        try (var input =
                new CdsExportRawValueInputStream(
                        source,
                        27,
                        1,
                        "value",
                        new CdsExportRawCellDto(
                                false, 0, CdsExportRawCellDto.EMPTY_SHA256, new byte[0]))) {
            assertThat(input.read()).isEqualTo(-1);
        }
        assertThat(new CdsExportRawCellDto(false, cell.bytes(), cell.sha256(), cell.inlineBytes()))
                .isEqualTo(cell)
                .hasSameHashCodeAs(cell);
        assertThat(new CdsExportRawCellDto(true, 0, CdsExportRawCellDto.EMPTY_SHA256, null))
                .isNotEqualTo(
                        new CdsExportRawCellDto(
                                false, 0, CdsExportRawCellDto.EMPTY_SHA256, new byte[0]));
        assertThat(cell.toString()).doesNotContain("FAKE-CDS");
    }

    @Test
    void shouldRefuseShortNullAndChangedSourceRanges_andStayFailedAfterTheFirstError()
            throws Exception {
        for (int fault = 0; fault < 3; fault++) {
            var source = mock(CdsExportPatientTableReadDao.class);
            byte[] result = fault == 0 ? new byte[9] : fault == 1 ? null : new byte[10];
            when(source.readCdsExportValueChunk(eq(27), eq(1L), eq("value"), anyLong(), anyInt()))
                    .thenReturn(result);
            try (var input =
                    new CdsExportRawValueInputStream(
                            source,
                            27,
                            1,
                            "value",
                            new CdsExportRawCellDto(false, 10, "a".repeat(64), null))) {
                assertThatThrownBy(input::readAllBytes)
                        .hasMessage("CDS_EXPORT_NATIVE_VALUE_CHANGED");
                assertThatThrownBy(input::read).hasMessage("CDS_EXPORT_NATIVE_VALUE_STREAM_CLOSED");
            }
        }
    }

    @Test
    void shouldRefuseMalformedDescriptors_insteadOfInventingOrTruncatingBytes() {
        for (var request :
                List.<Runnable>of(
                        () ->
                                new CdsExportRawCellDto(
                                        true, 1, CdsExportRawCellDto.EMPTY_SHA256, null),
                        () ->
                                new CdsExportRawCellDto(
                                        false, 0, CdsExportRawCellDto.EMPTY_SHA256, null),
                        () ->
                                new CdsExportRawCellDto(
                                        false, 2, CdsExportRawCellDto.EMPTY_SHA256, new byte[1]),
                        () ->
                                new CdsExportRawCellDto(
                                        false,
                                        16_385,
                                        CdsExportRawCellDto.EMPTY_SHA256,
                                        new byte[16_385]))) {
            assertThatThrownBy(request::run).hasMessage("CDS_EXPORT_RAW_CELL_INVALID");
        }
    }

    @Test
    void shouldPreserveTheOrderingOfEverySignedNativeKey_withoutTruncation() {
        var ids = List.of(Long.MIN_VALUE, -42L, -1L, 0L, 1L, 42L, Long.MAX_VALUE);
        var keys = ids.stream().map(CdsExportPatientTableSourceReader::key).toList();
        assertThat(keys).isSorted().doesNotHaveDuplicates();
        assertThat(keys).allSatisfy(key -> assertThat(key).hasSize(20));
        assertThat(keys.getFirst()).isEqualTo("00000000000000000000");
        assertThat(keys.getLast()).isEqualTo("18446744073709551615");
    }

    @Test
    void shouldNeverTreatAPartialClosedStreamAsAVerifiedValue() throws Exception {
        var source = mock(CdsExportPatientTableReadDao.class);
        byte[] bytes = "partial".getBytes(StandardCharsets.UTF_8);
        var input =
                new CdsExportRawValueInputStream(
                        source,
                        27,
                        1,
                        "value",
                        new CdsExportRawCellDto(false, bytes.length, sha(bytes), bytes));
        assertThat(input.read()).isEqualTo('p');
        input.close();
        assertThatThrownBy(input::read)
                .isInstanceOf(IOException.class)
                .hasMessage("CDS_EXPORT_NATIVE_VALUE_STREAM_CLOSED");
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
