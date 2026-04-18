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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Streams binary content through a Base64 encoder without first loading the entire source file into heap.
 *
 * <p>This helper is intended for CARLOS EMR document, fax, and email workflows that must eventually
 * produce a Base64 string for external APIs, while avoiding an additional full-size raw byte array in
 * memory.</p>
 *
 * @since 2026-04-17
 */
public final class Base64StreamingUtils {

    private Base64StreamingUtils() {
    }

    /**
     * Reads a file as a stream and returns its Base64-encoded string form.
     *
     * @param path Path the file to encode
     * @return String the Base64-encoded file contents
     * @throws IOException if the file cannot be read
     */
    public static String encode(Path path) throws IOException {
        long fileSize = Files.size(path);
        try (InputStream inputStream = Files.newInputStream(path)) {
            return encode(inputStream, estimateEncodedSize(fileSize));
        }
    }

    /**
     * Reads binary content from an input stream and returns its Base64-encoded string form.
     *
     * @param inputStream InputStream the binary stream to encode
     * @return String the Base64-encoded stream contents
     * @throws IOException if the input stream cannot be read
     */
    public static String encode(InputStream inputStream) throws IOException {
        return encode(inputStream, 32);
    }

    private static String encode(InputStream inputStream, int initialCapacity) throws IOException {
        ByteArrayOutputStream encodedOutput = new ByteArrayOutputStream(initialCapacity);
        try (OutputStream base64OutputStream = Base64.getEncoder().wrap(encodedOutput)) {
            inputStream.transferTo(base64OutputStream);
        }
        return encodedOutput.toString(StandardCharsets.ISO_8859_1);
    }

    /**
     * Estimates the Base64-encoded output size for a given raw input size.
     *
     * <p>Base64 produces 4 output bytes for every 3 input bytes (rounded up). A small
     * floor/cap is applied to avoid a zero-capacity buffer and to bound the initial
     * allocation for extremely large inputs where the caller would run out of heap
     * before the allocation could succeed anyway.</p>
     *
     * @param rawSize long the raw input size in bytes
     * @return int a safe initial capacity for the encoded output buffer
     */
    private static int estimateEncodedSize(long rawSize) {
        if (rawSize <= 0) {
            return 32;
        }
        // Ceiling division: Base64 emits 4 output bytes per 3 input bytes; adding 2 before
        // dividing by 3 rounds up for non-multiple-of-3 inputs.
        long encoded = ((rawSize + 2) / 3) * 4;
        // Cap to a safe int value to avoid overflow for pathologically large inputs
        if (encoded > Integer.MAX_VALUE - 8) {
            return Integer.MAX_VALUE - 8;
        }
        return (int) encoded;
    }
}
