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

/**
 * Recognises raster images by their leading magic number.
 *
 * <p>{@code DigitalSignature.signatureImage} holds either a legacy plaintext image or raw
 * {@link EncryptionUtils#encrypt(byte[])} output, which carries no marker. The runtime decrypt
 * fallback uses this heuristic after a decrypt fails. It cannot establish that data is plaintext:
 * a random ciphertext IV may start with these same bytes. In particular, startup key-loss checks
 * must never use it to justify creating a replacement key.</p>
 *
 * @since 2026-09-29
 */
public final class ImageMagicNumbers {

    /** Bytes needed to recognise every format checked here. */
    public static final int PREFIX_BYTES = 4;

    private ImageMagicNumbers() {
    }

    /**
     * True when the bytes begin with a known raster-image magic number (JPEG, PNG, GIF, or BMP).
     * Only the first {@link #PREFIX_BYTES} bytes are read, so a prefix of a larger value is enough.
     *
     * @param bytes the value, or its first bytes; may be null
     * @return true when the bytes start like a JPEG, PNG, GIF or BMP image
     */
    public static boolean isKnownRasterImage(byte[] bytes) {
        if (bytes == null || bytes.length < PREFIX_BYTES) {
            return false;
        }
        int b0 = bytes[0] & 0xFF;
        int b1 = bytes[1] & 0xFF;
        int b2 = bytes[2] & 0xFF;
        int b3 = bytes[3] & 0xFF;
        boolean jpeg = b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF;
        boolean png = b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47;
        boolean gif = b0 == 0x47 && b1 == 0x49 && b2 == 0x46 && b3 == 0x38;
        boolean bmp = b0 == 0x42 && b1 == 0x4D;
        return jpeg || png || gif || bmp;
    }
}
