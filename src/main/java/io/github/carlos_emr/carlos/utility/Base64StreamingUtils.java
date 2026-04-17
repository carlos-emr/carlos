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
        try (InputStream inputStream = Files.newInputStream(path)) {
            return encode(inputStream);
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
        ByteArrayOutputStream encodedOutput = new ByteArrayOutputStream();
        try (OutputStream base64OutputStream = Base64.getEncoder().wrap(encodedOutput)) {
            inputStream.transferTo(base64OutputStream);
        }
        return encodedOutput.toString(StandardCharsets.ISO_8859_1);
    }
}
