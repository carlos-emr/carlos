/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

/**
 * Clinic code v1 (mycarlos#141): the 9-digit code a clinic emails a patient so their new myCarlos
 * device can be approved without staff. The code is {@code code_r} (6 random digits, which the
 * device sends back through the portal) followed by {@code check_t} (3 digits the device recomputes
 * with the clinic's signing key id, so a code shown by another portal does not match).
 *
 * <p>Never log a code, a {@code code_r} or a {@code check_t}.
 */
final class PortalMyCarlosClinicCode {
    static final String LABEL = "carlos-mycarlos-clinic-code-check-v1";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PortalMyCarlosClinicCode() {
    }

    /** @return a new {@code code_r}: 6 ASCII digits, uniform over 000000 to 999999 */
    static String newCodeR() {
        return newCodeR(RANDOM);
    }

    static String newCodeR(SecureRandom random) {
        return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    }

    /**
     * The 3-digit check: the first 8 bytes of SHA-256 over the label, clinic kid, device key id,
     * request id and {@code code_r}, joined by single line feeds, read as an unsigned big-endian
     * number, mod 1000, zero-padded.
     *
     * @throws IllegalArgumentException when a field is empty or holds a line feed, the request id
     *         is not a lowercase UUID, or {@code code_r} is not 6 ASCII digits
     */
    static String checkT(String clinicKid, String deviceKeyId, String requestId, String codeR) {
        field(clinicKid);
        field(deviceKeyId);
        field(requestId);
        if (!UUID.fromString(requestId).toString().equals(requestId)) {
            throw new IllegalArgumentException("clinic code request id must be a lowercase UUID");
        }
        if (!isCodeR(codeR)) throw new IllegalArgumentException("clinic code_r must be 6 digits");
        byte[] input = String.join("\n", LABEL, clinicKid, deviceKeyId, requestId, codeR)
                .getBytes(StandardCharsets.UTF_8);
        long first = ByteBuffer.wrap(sha256(input), 0, Long.BYTES).getLong();
        return String.format(Locale.ROOT, "%03d", Long.remainderUnsigned(first, 1000));
    }

    /** @return the 9-digit code the patient is sent: {@code code_r} then {@code check_t} */
    static String code(String clinicKid, String deviceKeyId, String requestId, String codeR) {
        return codeR + checkT(clinicKid, deviceKeyId, requestId, codeR);
    }

    /** @return a 9-digit code as the patient reads it, in groups of three: "482 193 644" */
    static String display(String code) {
        if (code == null || code.length() != 9 || !digits(code)) {
            throw new IllegalArgumentException("clinic code must be 9 digits");
        }
        return code.substring(0, 3) + ' ' + code.substring(3, 6) + ' ' + code.substring(6);
    }

    /** @return whether the value is exactly 6 ASCII digits */
    static boolean isCodeR(String value) {
        return value != null && value.length() == 6 && digits(value);
    }

    /**
     * Compares a submitted {@code code_r} with the one sent, in time that does not depend on
     * where they differ. Anything that is not 6 ASCII digits never matches.
     */
    static boolean sameCodeR(String sent, String submitted) {
        if (!isCodeR(sent) || !isCodeR(submitted)) return false;
        return MessageDigest.isEqual(sent.getBytes(StandardCharsets.US_ASCII),
                submitted.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * The key CARLOS keeps a code's hash under. It lives only in this server's memory, so the stored
     * hash alone cannot give a 6-digit code back (a plain hash could be reversed by trying all of them).
     * A restart makes a new key: codes sent before it can no longer match, and their requests expire.
     */
    record MacKey(byte[] id, byte[] secret) {
        MacKey {
            if (id == null || id.length != 16 || secret == null || secret.length != 32) {
                throw new IllegalArgumentException("clinic code key must be 16 + 32 bytes");
            }
            id = id.clone();
            secret = secret.clone();
        }

        @Override
        public byte[] id() {
            return id.clone();
        }

        @Override
        public byte[] secret() {
            return secret.clone();
        }

        @Override
        public String toString() {
            return "ClinicCodeKey[REDACTED]";
        }
    }

    private static final class ServerKey {
        private static final MacKey KEY = newKey(RANDOM);
    }

    /** @return this server's code key, made once per server start */
    static MacKey serverKey() {
        return ServerKey.KEY;
    }

    static MacKey newKey(SecureRandom random) {
        byte[] id = new byte[16];
        byte[] secret = new byte[32];
        random.nextBytes(id);
        random.nextBytes(secret);
        return new MacKey(id, secret);
    }

    /**
     * HMAC-SHA256 over a label, the request id and {@code code_r}: what CARLOS stores instead of the
     * code, and what a submitted code is compared with (in constant time).
     */
    static byte[] mac(MacKey key, String requestId, String codeR) {
        field(requestId);
        if (!isCodeR(codeR)) throw new IllegalArgumentException("clinic code_r must be 6 digits");
        try {
            javax.crypto.Mac hmac = javax.crypto.Mac.getInstance("HmacSHA256");
            hmac.init(new javax.crypto.spec.SecretKeySpec(key.secret, "HmacSHA256"));
            return hmac.doFinal(String.join("\n", "carlos-mycarlos-clinic-code-mac-v1", requestId, codeR)
                    .getBytes(StandardCharsets.US_ASCII));
        } catch (java.security.GeneralSecurityException missing) {
            throw new IllegalStateException("HmacSHA256 is unavailable", missing);
        }
    }

    /**
     * Whether a stored code hash was made under this server's key. One made before a restart (or
     * on another server) can't be checked here: it is neither right nor wrong.
     */
    static boolean sameKey(MacKey key, byte[] storedKeyId) {
        return storedKeyId != null && MessageDigest.isEqual(key.id, storedKeyId);
    }

    /**
     * Whether a submitted {@code code_r} is the one whose hash was stored. Anything that is not 6
     * ASCII digits, or a hash made under another key, never matches.
     */
    static boolean matches(MacKey key, byte[] storedKeyId, byte[] storedMac, String requestId, String submitted) {
        if (!isCodeR(submitted) || storedKeyId == null || storedMac == null) return false;
        boolean sameKey = MessageDigest.isEqual(key.id, storedKeyId);
        boolean sameMac = MessageDigest.isEqual(mac(key, requestId, submitted), storedMac);
        return sameKey & sameMac;
    }

    private static boolean digits(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    private static void field(String value) {
        if (value == null || value.isEmpty() || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("clinic code field must be non-empty, on one line");
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is unavailable", missing);
        }
    }
}
