/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.junit.jupiter.api.Assertions.*;

import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Bytes;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.DeviceKeys;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Sealing a signed reading aid as its own delivery: the frame, the outer kind and the contract's limits. */
@Tag("unit")
class PortalMyCarlosReadingAidComposerUnitTest {
    private static final String AID_ID = "00000000-0000-4000-8000-000000000002";
    private static final String ORIGINAL_ID = "00000000-0000-4000-8000-000000000001";
    private static final String RECIPIENT = "recipient-a";
    private static final String SIGNER = base64(new byte[32]);
    private static final long CREATED = 1792454400L;
    private static final long EXPIRES = CREATED + 3600;
    private static final byte[] INFO = "carlos-emr/mycarlos-delivery/v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] JSON = "{\"v\":1,\"FAKE\":\"reading aid\"}".getBytes(StandardCharsets.US_ASCII);

    private final org.bouncycastle.crypto.hpke.HPKE hpke = PortalMyCarlosDeliveryComposer.hpke();
    private final AsymmetricCipherKeyPair keyPair = hpke.generatePrivateKey();
    private final byte[] x25519 = hpke.serializePublicKey(keyPair.getPublic());
    private final byte[] ed25519 = marker();
    private final DeviceKeys device = new DeviceKeys(PortalMyCarlosDeviceApprovalCrypto.deviceKeyId(x25519, ed25519),
            new Bytes(x25519), new Bytes(ed25519));

    private String manifest(byte[] json, String delivery, String recipient, String kid, String typ, long size) {
        String claims = "{\"v\":1,\"typ\":\"" + typ + "\",\"delivery_id\":\"" + delivery + "\",\"recipient_id\":\"" + recipient
                + "\",\"device_key_id\":\"" + kid + "\",\"kid\":\"" + SIGNER + "\",\"created_at\":" + CREATED
                + ",\"expires_at\":" + EXPIRES + ",\"pair\":{\"original_delivery_id\":\"" + ORIGINAL_ID
                + "\",\"original_document_sha256\":\"" + "e".repeat(64) + "\"},\"reading_aid\":{\"sha256\":\""
                + PortalMyCarlosDeviceApprovalCrypto.sha256(json) + "\",\"size\":" + size + "}}";
        return base64(claims.getBytes(StandardCharsets.US_ASCII)) + "." + base64(new byte[64]);
    }
    private String good() { return manifest(JSON, AID_ID, RECIPIENT, device.kid(), "mycarlos-reading-aid-manifest", JSON.length); }

    @Test void shouldSealTheFramedAidForTheDevice_withTheKindOnlyInTheOuterRecord() {
        var copy = PortalMyCarlosDeliveryComposer.composeAid(AID_ID, RECIPIENT, device, JSON, good(), CREATED, EXPIRES, SIGNER);
        assertEquals(PortalMyCarlosDeviceApprovalCrypto.sha256(JSON), copy.documentSha256());
        assertArrayEquals(JSON, copy.originalPdf().copy());
        // The device opens the seal with its private key and gets label, manifest and JSON back.
        byte[] sealed = copy.sealed().copy();
        byte[] encapsulation = java.util.Arrays.copyOfRange(sealed, 0, 32);
        byte[] ciphertext = java.util.Arrays.copyOfRange(sealed, 32, sealed.length);
        byte[] plain;
        try {
            plain = hpke.setupBaseR(encapsulation, keyPair, INFO)
                    .open(PortalMyCarlosDeliveryComposer.aad(AID_ID, RECIPIENT, device.kid()), ciphertext);
        } catch (Exception failure) { throw new AssertionError(failure); }
        var parts = frames(plain);
        assertEquals("mycarlos-reading-aid/1", new String(parts.get(0), StandardCharsets.US_ASCII));
        assertEquals(good(), new String(parts.get(1), StandardCharsets.US_ASCII));
        assertArrayEquals(JSON, parts.get(2));
        // The outer record names the kind, and the snapshot gate accepts it as an aid of the original.
        var outer = frames(copy.uploadBody().copy()).get(0);
        assertTrue(new String(outer, StandardCharsets.US_ASCII).contains("\"kind\":\"reading-aid/1\""));
    }

    @Test void shouldRefuseAManifestThatDoesNotMatchTheDeliveryOrTheJson() {
        String wrongHash = manifest("other".getBytes(StandardCharsets.US_ASCII), AID_ID, RECIPIENT, device.kid(), "mycarlos-reading-aid-manifest", JSON.length);
        for (String bad : new String[] {
                wrongHash,
                manifest(JSON, ORIGINAL_ID, RECIPIENT, device.kid(), "mycarlos-reading-aid-manifest", JSON.length),
                manifest(JSON, AID_ID, "recipient-b", device.kid(), "mycarlos-reading-aid-manifest", JSON.length),
                manifest(JSON, AID_ID, RECIPIENT, base64(new byte[32]), "mycarlos-reading-aid-manifest", JSON.length),
                manifest(JSON, AID_ID, RECIPIENT, device.kid(), "mycarlos-delivery-manifest", JSON.length),
                manifest(JSON, AID_ID, RECIPIENT, device.kid(), "mycarlos-reading-aid-manifest", JSON.length + 1)}) {
            assertThrows(IllegalArgumentException.class,
                    () -> PortalMyCarlosDeliveryComposer.composeAid(AID_ID, RECIPIENT, device, JSON, bad, CREATED, EXPIRES, SIGNER));
        }
        assertThrows(IllegalArgumentException.class,
                () -> PortalMyCarlosDeliveryComposer.composeAid(AID_ID, RECIPIENT, device, JSON, good(), CREATED, EXPIRES + 1, SIGNER));
        // A manifest with no pair claims, or another version, is refused before sealing.
        String noPair = good().replaceFirst("^[^.]+", base64(new String(Base64.getUrlDecoder().decode(good().split("\\.")[0]),
                StandardCharsets.US_ASCII).replaceFirst(",\"pair\":\\{[^}]*\\}", "").getBytes(StandardCharsets.US_ASCII)));
        String otherVersion = good().replaceFirst("^[^.]+", base64(new String(Base64.getUrlDecoder().decode(good().split("\\.")[0]),
                StandardCharsets.US_ASCII).replace("\"v\":1", "\"v\":2").getBytes(StandardCharsets.US_ASCII)));
        for (String bad : new String[] {noPair, otherVersion}) {
            assertThrows(IllegalArgumentException.class,
                    () -> PortalMyCarlosDeliveryComposer.composeAid(AID_ID, RECIPIENT, device, JSON, bad, CREATED, EXPIRES, SIGNER));
        }
    }

    @Test void shouldRefuseAnAidOverTheContractsJsonLimitOrWithNoJson() {
        byte[] big = new byte[PortalMyCarlosDeliveryComposer.MAX_AID_JSON_BYTES + 1];
        java.util.Arrays.fill(big, (byte) 'x');
        assertThrows(IllegalArgumentException.class, () -> PortalMyCarlosDeliveryComposer.composeAid(AID_ID, RECIPIENT, device, big,
                manifest(big, AID_ID, RECIPIENT, device.kid(), "mycarlos-reading-aid-manifest", big.length), CREATED, EXPIRES, SIGNER));
        assertThrows(IllegalArgumentException.class, () -> PortalMyCarlosDeliveryComposer.composeAid(AID_ID, RECIPIENT, device,
                new byte[0], good(), CREATED, EXPIRES, SIGNER));
    }

    private static java.util.List<byte[]> frames(byte[] framed) {
        var data = ByteBuffer.wrap(framed);
        var parts = new java.util.ArrayList<byte[]>();
        while (data.hasRemaining()) {
            byte[] part = new byte[Math.toIntExact(data.getLong())];
            data.get(part);
            parts.add(part);
        }
        return parts;
    }
    private static byte[] marker() { byte[] bytes = new byte[32]; bytes[0] = 2; return bytes; }
    private static String base64(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
}
