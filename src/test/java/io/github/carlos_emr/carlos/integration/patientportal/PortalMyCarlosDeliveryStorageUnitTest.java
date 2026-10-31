/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.junit.jupiter.api.Assertions.*;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Bytes;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PortalMyCarlosDeliveryStorageUnitTest {
    @Test
    void shouldRetainExactImmutableBodyKeysAndTarget_whenEncryptedSnapshotIsRestored() throws Exception {
        var snapshot = snapshot();
        var cipher = cipher();
        var context = context(snapshot, "outbox");
        byte[] exact = snapshot.encode();
        byte[] encrypted = cipher.seal(context, exact);
        assertFalse(Arrays.equals(exact, encrypted));
        assertEquals(exact.length + 40, encrypted.length);
        var restored = PortalMyCarlosDeliverySnapshot.decode(cipher.open(context, encrypted));
        assertEquals(snapshot, restored);
        assertArrayEquals(snapshot.body().copy(), restored.body().copy());
        assertEquals(snapshot.receiptBinding(), restored.receiptBinding());
        assertEquals(PortalMyCarlosDeliveryProducerUnitTest.compose(PortalMyCarlosDeliveryProducerUnitTest.fixture()).sealed().size(), restored.sealedSize());
        assertEquals("DeliverySnapshot[REDACTED]", restored.toString());
        assertFalse(new String(encrypted, java.nio.charset.StandardCharsets.ISO_8859_1).contains("portal.example.test"));
    }

    @Test
    void shouldRefuseSwappedRowsAndTampering_whenOpeningEncryptedArtifacts() throws Exception {
        var s = snapshot();
        var c = cipher();
        byte[] encrypted = c.seal(context(s, "outbox"), s.encode());
        for (var wrong : java.util.List.of(
                new PortalMyCarlosDeliveryCipher.Context("other-clinic", s.deliveryId(), s.device().kid(), "outbox"),
                new PortalMyCarlosDeliveryCipher.Context(s.clinicId(), "00000000-0000-4000-8000-000000000000", s.device().kid(), "outbox"),
                new PortalMyCarlosDeliveryCipher.Context(s.clinicId(), s.deliveryId(), "A".repeat(43), "outbox"),
                context(s, "receipt"))) {
            assertThrows(IllegalArgumentException.class, () -> c.open(wrong, encrypted));
        }
        for (int index : new int[] {0, 8, 12, 24, encrypted.length - 1}) {
            byte[] tampered = encrypted.clone();
            tampered[index] ^= 1;
            assertThrows(IllegalArgumentException.class, () -> c.open(context(s, "outbox"), tampered));
        }
        assertThrows(IllegalArgumentException.class, () -> c.open(context(s, "outbox"), Arrays.copyOf(encrypted, encrypted.length - 1)));
    }

    @Test
    void shouldRetainOldKeyAndUseFreshNonce_whenRotatingCurrentStorageKey() throws Exception {
        var s = snapshot();
        byte[] a = new byte[32];
        a[0] = 1;
        byte[] b = new byte[32];
        b[0] = 2;
        var old = new PortalMyCarlosDeliveryCipher(1, Map.of(1, a));
        byte[] encrypted = old.seal(context(s, "outbox"), s.encode());
        a[0] ^= 64; // The cipher owns a defensive copy.
        assertArrayEquals(s.encode(), old.open(context(s, "outbox"), encrypted));
        a[0] ^= 64;
        var rotated = new PortalMyCarlosDeliveryCipher(2, Map.of(1, a, 2, b));
        assertArrayEquals(s.encode(), rotated.open(context(s, "outbox"), encrypted));
        assertFalse(Arrays.equals(rotated.seal(context(s, "outbox"), s.encode()),
                rotated.seal(context(s, "outbox"), s.encode())));
        assertThrows(IllegalArgumentException.class, () -> new PortalMyCarlosDeliveryCipher(2, Map.of(2, b))
                .open(context(s, "outbox"), encrypted));
    }

    @Test
    void shouldRefuseChangedFrameAndIdentity_whenRestoringPersistedSnapshot() throws Exception {
        var s = snapshot();
        byte[] changed = s.encode();
        changed[changed.length - 1] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> PortalMyCarlosDeliverySnapshot.decode(changed));
        byte[] extra = Arrays.copyOf(s.encode(), s.encode().length + 1);
        assertThrows(IllegalArgumentException.class, () -> PortalMyCarlosDeliverySnapshot.decode(extra));
        byte[] overflow = s.encode();
        Arrays.fill(overflow, 0, 8, (byte) 0xff);
        assertThrows(IllegalArgumentException.class, () -> PortalMyCarlosDeliverySnapshot.decode(overflow));
        assertThrows(IllegalArgumentException.class, () -> new PortalMyCarlosDeliverySnapshot(s.clinicId(), s.demographicNo(),
                s.recipientId(), s.publicBase(), "https://portal.example.test/internal/carlos/patients/124/mycarlos/deliveries",
                s.configSha256(), s.acceptedDeviceId(), s.acceptanceVersion(), s.acceptanceSha256(), s.deliveryId(),
                s.device(), s.signerKid(), s.documentSha256(), s.ciphertextSha256(), s.createdAt(), s.expiresAt(), s.bodySha256(), s.body()));
    }

    static PortalMyCarlosDeliverySnapshot snapshot() throws Exception {
        var f = PortalMyCarlosDeliveryProducerUnitTest.fixture();
        var c = PortalMyCarlosDeliveryProducerUnitTest.compose(f);
        return new PortalMyCarlosDeliverySnapshot("clinic-a", 123, c.recipientId(), "https://portal.example.test",
                "https://portal.example.test/internal/carlos/patients/123/mycarlos/deliveries", "1".repeat(64),
                7, 0, "2".repeat(64), c.deliveryId(), c.device(), c.signerKid(), c.documentSha256(),
                c.ciphertextSha256(), c.createdAt(), c.expiresAt(), c.bodySha256(), c.uploadBody());
    }
    private static PortalMyCarlosDeliveryCipher cipher() { return new PortalMyCarlosDeliveryCipher(1, Map.of(1, new byte[32])); }
    private static PortalMyCarlosDeliveryCipher.Context context(PortalMyCarlosDeliverySnapshot s, String kind) {
        return new PortalMyCarlosDeliveryCipher.Context(s.clinicId(), s.deliveryId(), s.device().kid(), kind);
    }
}
