/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Licensed under the GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.utility;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** JDK content digests compatible with the persisted Commons Codec digest values. */
public final class ContentDigests {
    private ContentDigests() {
    }

    /** Hashes text as UTF-8, including the empty string; null is rejected as before. */
    public static byte[] sha256(String value) {
        return digest("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    }

    /** Returns lowercase, zero-padded hexadecimal of the UTF-8 content digest. */
    public static String sha256Hex(String value) {
        return HexFormat.of().formatHex(sha256(value));
    }

    /**
     * Reads to EOF without closing the caller-owned stream.
     *
     * @param input InputStream the caller-owned stream to hash
     * @return String the lowercase SHA-256 hexadecimal digest
     * @throws IOException if reading the stream fails
     */
    public static String sha256Hex(InputStream input) throws IOException {
        return hexDigest("SHA-256", input);
    }

    /**
     * Legacy lab duplicate key only, never a password or security integrity check.
     * Existing stored MD5 keys must continue to match uploaded bytes.
     *
     * @param input InputStream the caller-owned stream to hash, left open
     * @return String the lowercase MD5 hexadecimal digest
     * @throws IOException if reading the stream fails
     */
    public static String md5Hex(InputStream input) throws IOException {
        return hexDigest("MD5", input);
    }

    private static String hexDigest(String algorithm, InputStream input) throws IOException {
        MessageDigest digest = digest(algorithm);
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 and MD5 are required algorithms in every Java implementation.
            throw new IllegalArgumentException(e);
        }
    }
}
