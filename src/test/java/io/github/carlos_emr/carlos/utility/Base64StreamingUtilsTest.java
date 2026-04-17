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
package io.github.carlos_emr.carlos.utility;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Unit tests for {@link Base64StreamingUtils}.
 *
 * <p>Verifies the streaming encoder matches JDK Base64 output for both file-backed and
 * in-memory input streams, including multi-chunk payloads that would otherwise require
 * full raw-file buffering.</p>
 *
 * @since 2026-04-17
 */
@Tag("unit")
@DisplayName("Base64StreamingUtils")
class Base64StreamingUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("should encode file contents when payload spans multiple transfer chunks")
    void shouldEncodeFileContents_whenPayloadSpansMultipleTransferChunks() throws IOException {
        byte[] data = new byte[32 * 1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 251);
        }

        Path file = tempDir.resolve("document.bin");
        Files.write(file, data);

        String encoded = Base64StreamingUtils.encode(file);

        assertThat(encoded).isEqualTo(Base64.getEncoder().encodeToString(data));
    }

    @Test
    @DisplayName("should encode stream contents when input stream is provided directly")
    void shouldEncodeStreamContents_whenInputStreamProvidedDirectly() throws IOException {
        byte[] data = "CARLOS EMR streaming base64".getBytes(StandardCharsets.UTF_8);

        String encoded = Base64StreamingUtils.encode(new ByteArrayInputStream(data));

        assertThat(encoded).isEqualTo(Base64.getEncoder().encodeToString(data));
    }

    @Test
    @DisplayName("should return empty string when encoding empty stream")
    void shouldReturnEmptyString_whenEncodingEmptyStream() throws IOException {
        String encoded = Base64StreamingUtils.encode(new ByteArrayInputStream(new byte[0]));

        assertThat(encoded).isEmpty();
    }
}
