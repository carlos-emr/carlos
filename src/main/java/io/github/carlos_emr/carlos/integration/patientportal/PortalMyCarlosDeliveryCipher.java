/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeliveryCodec.invalid;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Dedicated sender storage encryption. Uses provisioned AES-256 keys in a separate key domain
 * from email archives; it never creates/replaces a missing keyring or drops historical keys.
 * Database transaction durability is the store's responsibility, not an encryption guarantee.
 */
final class PortalMyCarlosDeliveryCipher {
    private static final byte[] MAGIC = "MYCOBX1\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] DOMAIN = "carlos-mycarlos-outbox-at-rest/v1".getBytes(StandardCharsets.US_ASCII);
    private static final int HEADER = 24;
    private static final int OVERHEAD = HEADER + 16;
    static final int MAX_PLAINTEXT = 32 * 1024 * 1024;
    private final Map<Integer, SecretKey> keys;
    private final int current;
    private final SecureRandom random = new SecureRandom();

    PortalMyCarlosDeliveryCipher(int current, Map<Integer, byte[]> material) {
        Map<Integer, SecretKey> copied = new HashMap<>();
        if (material == null || material.isEmpty() || material.size() > 128) throw invalid();
        material.forEach((id, bytes) -> {
            if (id == null || id <= 0 || bytes == null || bytes.length != 32) throw invalid();
            copied.put(id, new SecretKeySpec(bytes.clone(), "AES"));
        });
        if (!copied.containsKey(current)) throw invalid();
        this.current = current;
        this.keys = Map.copyOf(copied);
    }

    record Context(String clinicId, String objectId, String deviceKid, String artifact) {
        Context {
            if (clinicId == null || clinicId.isEmpty() || clinicId.codePointCount(0, clinicId.length()) > 64
                    || objectId == null || !objectId.matches("[A-Za-z0-9_-]{1,64}")
                    || deviceKid == null || !(deviceKid.isEmpty() || deviceKid.matches("[A-Za-z0-9_-]{43}"))
                    || artifact == null || !java.util.Set.of("original", "outbox", "event_page", "quarantine", "receipt").contains(artifact)) throw invalid();
            PortalMyCarlosDeliveryCodec.text(clinicId);
        }
        @Override public String toString() { return "DeliveryStorageContext[REDACTED]"; }
    }

    byte[] seal(Context context, byte[] plaintext) {
        if (context == null || plaintext == null || plaintext.length == 0 || plaintext.length > MAX_PLAINTEXT) throw invalid();
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        byte[] header = ByteBuffer.allocate(HEADER).put(MAGIC).putInt(current).put(nonce).array();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(current), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad(context, header));
            byte[] encrypted = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(header.length + encrypted.length).put(header).put(encrypted).array();
        } catch (GeneralSecurityException failure) {
            throw invalid();
        }
    }

    byte[] open(Context context, byte[] stored) {
        if (context == null || stored == null || stored.length <= OVERHEAD || stored.length > MAX_PLAINTEXT + OVERHEAD
                || !Arrays.equals(MAGIC, Arrays.copyOf(stored, MAGIC.length))) throw invalid();
        ByteBuffer bytes = ByteBuffer.wrap(stored);
        bytes.position(MAGIC.length);
        SecretKey key = keys.get(bytes.getInt());
        if (key == null) throw invalid();
        byte[] nonce = new byte[12];
        bytes.get(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad(context, Arrays.copyOf(stored, HEADER)));
            return cipher.doFinal(stored, HEADER, stored.length - HEADER);
        } catch (GeneralSecurityException failure) {
            throw invalid();
        }
    }

    private static byte[] aad(Context c, byte[] header) {
        return PortalMyCarlosDeviceApprovalCrypto.frame(DOMAIN, header,
                c.clinicId().getBytes(StandardCharsets.UTF_8), c.objectId().getBytes(StandardCharsets.US_ASCII),
                c.deviceKid().getBytes(StandardCharsets.US_ASCII), c.artifact().getBytes(StandardCharsets.US_ASCII));
    }

    @Override public String toString() { return "DeliveryStorageCipher[REDACTED]"; }
}
