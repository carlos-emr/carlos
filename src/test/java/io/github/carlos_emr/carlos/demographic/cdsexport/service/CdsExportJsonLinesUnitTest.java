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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The internal JSON-lines transport reads lines in blocks but keeps every rule of the byte-at-a-time
 * reader it replaced: line feeds only, the length bound, a terminated last line, strict UTF-8 and
 * one JSON object per line.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportJsonLinesUnitTest {

    /** Returns at most {@code step} bytes per read, so lines straddle every read boundary. */
    private static InputStream trickle(byte[] bytes, int step) {
        return new ByteArrayInputStream(bytes) {
            @Override
            public synchronized int read(byte[] target, int offset, int count) {
                return super.read(target, offset, Math.min(step, count));
            }
        };
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void shouldReadEveryLine_whenLinesStraddleReadAndBufferBoundaries() throws Exception {
        String longValue = "é".repeat(70_000);
        byte[] input = utf8("{\"a\":1}\n{\"b\":\"" + longValue + "\"}\n{\"c\":\"😀\"}\n");
        for (int step : new int[] {1, 7, 65_536, input.length}) {
            var lines = new CdsExportJsonLines(trickle(input, step), 1_048_576, () -> {});

            assertThat(lines.required().get("a").intValue()).isEqualTo(1);
            assertThat(lines.required().get("b").textValue()).isEqualTo(longValue);
            assertThat(lines.required().get("c").textValue()).isEqualTo("😀");
            assertThat(lines.next()).isNull();
        }
    }

    @Test
    void shouldCheckpointThroughALongLine_atLeastOncePerEightKibibytes() throws Exception {
        byte[] input = utf8("{\"b\":\"" + "x".repeat(40_000) + "\"}\n");
        var checkpoints = new AtomicInteger();
        var lines = new CdsExportJsonLines(trickle(input, 4096), 1_048_576, checkpoints::incrementAndGet);

        lines.required();

        assertThat(checkpoints.get()).isGreaterThanOrEqualTo(1 + 40_000 / 8192);
    }

    @Test
    void shouldAcceptALineOfExactlyTheBound_andRefuseOneByteMore() throws Exception {
        String exact = "{\"a\":\"" + "x".repeat(92) + "\"}";
        assertThat(utf8(exact)).hasSize(100);
        var lines = new CdsExportJsonLines(new ByteArrayInputStream(utf8(exact + "\n")), 100, () -> {});
        assertThat(lines.required().get("a").textValue()).hasSize(92);

        String over = "{\"a\":\"" + "x".repeat(93) + "\"}\n";
        assertThatThrownBy(() -> new CdsExportJsonLines(new ByteArrayInputStream(utf8(over)), 100, () -> {}).next())
                .isInstanceOf(IOException.class)
                .hasMessage("CDS_EXPORT_JSON_LINES_INVALID");
    }

    @Test
    void shouldRefuseCarriageReturnsUnterminatedLinesAndNonObjects_forTheTransport() {
        for (String text : new String[] {"{\"a\":1}\r\n", "{\"a\":1}", "[1]\n", "\n", "{\"a\":1} 2\n"}) {
            assertThatThrownBy(() -> new CdsExportJsonLines(new ByteArrayInputStream(utf8(text)), 1024, () -> {}).next())
                    .as(text)
                    .isInstanceOf(IOException.class);
        }
    }

    @Test
    void shouldRefuseMalformedUtf8_andDuplicateMembers() {
        byte[] malformed = {'{', '"', 'a', '"', ':', '"', (byte) 0xC3, '"', '}', '\n'};
        assertThatThrownBy(() -> new CdsExportJsonLines(new ByteArrayInputStream(malformed), 1024, () -> {}).next())
                .isInstanceOf(IOException.class);
        assertThatThrownBy(
                        () ->
                                new CdsExportJsonLines(
                                                new ByteArrayInputStream(utf8("{\"a\":1,\"a\":2}\n")),
                                                1024,
                                                () -> {})
                                        .next())
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldReturnNull_atACleanEndOfInput() throws Exception {
        assertThat(new CdsExportJsonLines(new ByteArrayInputStream(new byte[0]), 1024, () -> {}).next()).isNull();
    }
}
