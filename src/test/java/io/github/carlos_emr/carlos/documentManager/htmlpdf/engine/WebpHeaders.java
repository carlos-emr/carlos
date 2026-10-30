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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Test helper: builds lossless WebP files that <em>declare</em> an image size without holding any
 * decodable pixels, the WebP counterpart of {@link PngHeaders}.
 *
 * <p>A WebP of 5000 x 5000 pixels that the library would decode into a 100 MB raster can be
 * declared in 26 bytes. Tests of the size cap need exactly that: an input whose size is large on
 * paper and whose header the reader parses, so a code path that decodes instead of refusing
 * allocates and fails the test instead of passing by luck. The file is a RIFF container with one
 * {@code VP8L} chunk: the signature byte {@code 0x2F}, a little-endian word holding the width and
 * height minus one in 14 bits each (no alpha, version 0), and, if asked for, zero bytes after it.
 * It is public so that tests in the {@code service} package can use it as well.</p>
 *
 * @since 2026-10-29
 */
public final class WebpHeaders {

    private static final int MAX_DIMENSION = 1 << 14;
    private static final int HEADER_BYTES = 5;

    private WebpHeaders() {
    }

    /**
     * Returns a lossless WebP that declares the given size and holds nothing else: 26 bytes.
     *
     * @param width the declared width, 1 to 16384
     * @param height the declared height, 1 to 16384
     * @return the RIFF/VP8L bytes
     * @throws IllegalArgumentException if a dimension is outside 1 to 16384, the limit of the format
     */
    public static byte[] declaring(int width, int height) {
        return declaring(width, height, HEADER_BYTES);
    }

    /**
     * Returns a lossless WebP that declares the given size and whose {@code VP8L} chunk is
     * {@code chunkBytes} long, the bytes after the five of the header being zero.
     *
     * <p>The padding matters for the library's own limit on how much an input may expand: a
     * 26-byte file declaring 5000 x 5000 is refused by it, while one of 64 KB passes it, so only
     * the latter shows whether something else refused the declared size first.</p>
     *
     * @param width the declared width, 1 to 16384
     * @param height the declared height, 1 to 16384
     * @param chunkBytes the length of the {@code VP8L} chunk, at least 5
     * @return the RIFF/VP8L bytes
     * @throws IllegalArgumentException if a dimension is outside 1 to 16384 or {@code chunkBytes} is below 5
     */
    public static byte[] declaring(int width, int height, int chunkBytes) {
        if (width < 1 || width > MAX_DIMENSION || height < 1 || height > MAX_DIMENSION) {
            throw new IllegalArgumentException("a WebP dimension must be 1 to 16384");
        }
        if (chunkBytes < HEADER_BYTES) {
            throw new IllegalArgumentException("a VP8L chunk holds at least its 5 header bytes");
        }
        int padded = chunkBytes + (chunkBytes & 1); // RIFF chunks are padded to an even length
        ByteBuffer riff = ByteBuffer.allocate(8 + 4 + 8 + padded).order(ByteOrder.LITTLE_ENDIAN);
        riff.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        riff.putInt(4 + 8 + padded); // everything after these eight bytes
        riff.put("WEBP".getBytes(StandardCharsets.US_ASCII));
        riff.put("VP8L".getBytes(StandardCharsets.US_ASCII));
        riff.putInt(chunkBytes);
        riff.put((byte) 0x2F); // VP8L signature
        riff.putInt((width - 1) | ((height - 1) << 14)); // 14 bits each; alpha_is_used and version are 0
        return riff.array(); // the rest, including any padding byte, is zero
    }
}
