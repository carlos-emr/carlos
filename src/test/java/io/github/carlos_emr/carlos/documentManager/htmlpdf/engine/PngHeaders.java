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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.engine;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Test helper: builds PNG files that <em>declare</em> an image size without containing any pixels.
 *
 * <p>A real 20 000 x 20 000 PNG is a gigabyte-scale decode, but its header is 25 bytes. Tests of
 * the header guards (the 250-megapixel limit, the decode caps) need exactly that: an input whose
 * size is huge on paper and that cannot be decoded at all, so a code path that decodes instead of
 * only reading the header fails the test instead of exhausting the heap. The file is the PNG
 * signature, a valid {@code IHDR} chunk with its CRC, and {@code IEND}; there is no {@code IDAT}.
 * It is public so that tests in the {@code service} package can use it as well.</p>
 *
 * @since 2026-10-29
 */
public final class PngHeaders {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private PngHeaders() {
    }

    /**
     * Returns a PNG whose header declares the given size and that has no pixel data.
     *
     * <p>The values are written as given and not validated, so {@code declaring(0, 0)} yields the
     * kind of header a reader must reject. The declared image is 8-bit grayscale, so even a reader
     * that wrongly tried to allocate it would ask for one byte per pixel, not three.</p>
     *
     * @param width the declared width in pixels
     * @param height the declared height in pixels
     * @return the PNG bytes: signature, {@code IHDR} and {@code IEND}
     */
    public static byte[] declaring(int width, int height) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(45);
            DataOutputStream out = new DataOutputStream(bytes);
            out.write(SIGNATURE);

            ByteArrayOutputStream header = new ByteArrayOutputStream(13);
            DataOutputStream ihdr = new DataOutputStream(header);
            ihdr.writeInt(width);
            ihdr.writeInt(height);
            ihdr.writeByte(8); // bit depth
            ihdr.writeByte(0); // colour type 0: grayscale
            ihdr.writeByte(0); // compression method
            ihdr.writeByte(0); // filter method
            ihdr.writeByte(0); // interlace method: none
            writeChunk(out, "IHDR", header.toByteArray());

            writeChunk(out, "IEND", new byte[0]);
            return bytes.toByteArray();
        } catch (IOException e) {
            // Writing to a byte array cannot fail.
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Writes one PNG chunk: length, type, data and the CRC over type and data. Package-private so
     * that tests in this package can build chunks this class does not, such as {@code IDAT}.
     */
    static void writeChunk(DataOutputStream out, String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.writeInt(data.length);
        out.write(typeBytes);
        out.write(data);
        // The CRC covers the chunk type and data, not the length.
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.writeInt((int) crc.getValue());
    }
}
