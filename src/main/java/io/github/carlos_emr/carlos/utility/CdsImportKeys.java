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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable length-delimited provenance keys; options and run IDs never define clinical identity. */
public final class CdsImportKeys {
    private CdsImportKeys() { }

    public static String hash(String... parts) {
        MessageDigest digest = digest();
        for (String part : parts) add(digest, part);
        return HexFormat.of().formatHex(digest.digest());
    }

    public static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }

    public static void add(MessageDigest digest, String value) {
        if (value==null || value.length()>65536) throw new IllegalArgumentException("Invalid digest component");
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
    }

    public static String required(String value, String name, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        return value;
    }

    public static String optional(String value, String name, int maximum) {
        if (value != null && value.length() > maximum) throw new IllegalArgumentException("Invalid " + name);
        return value;
    }

    /**
     * Whether two content digests are the same text: exactly {@code left.equals(right)} for any
     * two strings, so a null {@code left} still fails fast and a null {@code right} is unequal.
     * The comparison goes through {@link MessageDigest#isEqual}, the form static analysis expects
     * of a digest comparison (FindSecBugs UNSAFE_HASH_EQUALS), and the only form available inside
     * a lambda body, where SpotBugs cannot apply a per-site suppression. The digests compared in
     * CDS import and export are unkeyed SHA-256 values of files, rows and manifests, not
     * secrets; the constant-time comparison is incidental.
     */
    public static boolean sameDigest(String left, String right) {
        byte[] expected = utf16(left);
        return right != null && MessageDigest.isEqual(expected, utf16(right));
    }

    // Every char as two bytes. A charset encoder would replace an unpaired surrogate, which
    // could make two different strings compare equal.
    private static byte[] utf16(String value) {
        byte[] bytes = new byte[value.length() * 2];
        for (int i = 0; i < value.length(); i++) {
            char unit = value.charAt(i);
            bytes[2 * i] = (byte) (unit >>> 8);
            bytes[2 * i + 1] = (byte) unit;
        }
        return bytes;
    }

    public static String digestValue(String value, String name) {
        if (value == null || !value.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid " + name);
        return value;
    }

    public static String token(String value, String name, int maximum) {
        required(value, name, maximum);
        if (!value.matches("[A-Za-z0-9_.:-]+")) throw new IllegalArgumentException("Invalid " + name);
        return value;
    }

    public static int pageSize(int limit) {
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("Page size must be between 1 and 200");
        return limit;
    }
}
