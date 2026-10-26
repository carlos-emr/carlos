/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeliveryCodec.invalid;

import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Bytes;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.DeviceKeys;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.hpke.HPKE;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.crypto.util.PrivateKeyFactory;

/**
 * Pure producer for frozen M0/M4 bytes. The caller authorizes the patient, signs and durably
 * retains the original PDF, and selects current locally accepted keys before invoking this class.
 * No value returned here establishes local acceptance or authorizes HTTP.
 */
final class PortalMyCarlosDeliveryComposer {
    private static final byte[] INFO = "carlos-emr/mycarlos-delivery/v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] MAGIC = "mycarlos-delivery/1".getBytes(StandardCharsets.US_ASCII);

    private PortalMyCarlosDeliveryComposer() {}

    record Document(String clinicId, String clinicName, String providerName, String patientName,
                    String birthDate, String title, String type, String serviceDate) {
        Document {
            for (String value : new String[] {clinicId, clinicName, providerName, patientName,
                    birthDate, title, type, serviceDate}) {
                PortalMyCarlosDeliveryCodec.text(value);
            }
            if (clinicId.isEmpty() || clinicId.codePointCount(0, clinicId.length()) > 64) throw invalid();
        }
        @Override public String toString() { return "DeliveryDocument[REDACTED]"; }
    }

    record Copy(String deliveryId, String recipientId, DeviceKeys device, String signerKid,
                long createdAt, long expiresAt, Bytes originalPdf, String documentSha256,
                String manifest, Bytes sealed, Bytes uploadBody, String ciphertextSha256, String bodySha256) {
        @Override public String toString() { return "DeliveryCopy[REDACTED]"; }
    }

    @FunctionalInterface
    interface SignBytes {
        byte[] sign(byte[] exactPayload, String expectedKid) throws java.io.IOException;
    }

    static Copy compose(String deliveryId, String recipientId, DeviceKeys acceptedDevice,
            Document document, byte[] signedOriginalPdf, long createdAt, long expiresAt,
            PatientPortalClinicKeysPublishRequest.Key activeSigner, PrivateKey signingKey) {
        if (signingKey == null) throw invalid();
        return composeWithSigner(deliveryId, recipientId, acceptedDevice, document, signedOriginalPdf,
                createdAt, expiresAt, activeSigner, (payload, kid) -> PortalMyCarlosDeviceApprovalCodec
                        .envelope(sign(payload, activeSigner, signingKey)).signature().copy());
    }

    /** Production signing can retain private-key ownership in the existing validated PDF loader. */
    static Copy composeWithSigner(String deliveryId, String recipientId, DeviceKeys acceptedDevice,
            Document document, byte[] signedOriginalPdf, long createdAt, long expiresAt,
            PatientPortalClinicKeysPublishRequest.Key activeSigner, SignBytes signingKey) {
        PortalMyCarlosDeliveryCodec.uuid(deliveryId, true);
        PortalMyCarlosDeliveryCodec.recipient(recipientId);
        PortalMyCarlosDeliveryCodec.lifetime(createdAt, expiresAt);
        if (acceptedDevice == null || document == null || signedOriginalPdf == null
                || signedOriginalPdf.length == 0 || signedOriginalPdf.length > PortalMyCarlosDeliveryCodec.MAX_SEALED_BYTES
                || activeSigner == null || !"active".equals(activeSigner.status()) || signingKey == null) {
            throw invalid();
        }
        // Copy caller-owned mutable bytes before hashing, signing or encryption.
        byte[] pdf = signedOriginalPdf.clone();
        String documentHash = PortalMyCarlosDeviceApprovalCrypto.sha256(pdf);
        Map<String, Object> claims = claims(deliveryId, recipientId, acceptedDevice.kid(), document,
                pdf.length, documentHash, createdAt, expiresAt, activeSigner.kid());
        byte[] payload = PortalMyCarlosDeliveryCodec.canonical(claims);
        String manifest = signedManifest(payload, activeSigner, signingKey);
        byte[] plaintext = PortalMyCarlosDeviceApprovalCrypto.frame(MAGIC,
                manifest.getBytes(StandardCharsets.US_ASCII), pdf);
        if (plaintext.length > PortalMyCarlosDeliveryCodec.MAX_SEALED_BYTES - 48) throw invalid();
        byte[] aad = aad(deliveryId, recipientId, acceptedDevice.kid());
        byte[] sealed;
        try {
            HPKE hpke = hpke();
            var context = hpke.setupBaseS(hpke.deserializePublicKey(acceptedDevice.x25519().copy()), INFO);
            byte[] ciphertext = context.seal(aad, plaintext);
            sealed = ByteBuffer.allocate(32 + ciphertext.length)
                    .put(context.getEncapsulation()).put(ciphertext).array();
        } catch (Exception failure) {
            throw invalid();
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
        String ciphertextHash = PortalMyCarlosDeviceApprovalCrypto.sha256(sealed);
        byte[] outer = outer(deliveryId, recipientId, acceptedDevice.kid(), sealed.length,
                ciphertextHash, createdAt, expiresAt);
        byte[] body = PortalMyCarlosDeviceApprovalCrypto.frame(outer, sealed);
        if (outer.length > PortalMyCarlosDeliveryCodec.MAX_OUTER_BYTES
                || sealed.length > PortalMyCarlosDeliveryCodec.MAX_SEALED_BYTES
                || body.length > PortalMyCarlosDeliveryCodec.MAX_BODY_BYTES) throw invalid();
        return new Copy(deliveryId, recipientId, acceptedDevice, activeSigner.kid(), createdAt, expiresAt,
                new Bytes(pdf), documentHash, manifest, new Bytes(sealed), new Bytes(body),
                ciphertextHash, PortalMyCarlosDeviceApprovalCrypto.sha256(body));
    }

    static byte[] aad(String deliveryId, String recipientId, String deviceKid) {
        return PortalMyCarlosDeviceApprovalCrypto.frame(deliveryId.getBytes(StandardCharsets.UTF_8),
                recipientId.getBytes(StandardCharsets.UTF_8), deviceKid.getBytes(StandardCharsets.UTF_8));
    }

    static HPKE hpke() {
        return new HPKE(HPKE.mode_base, HPKE.kem_X25519_SHA256, HPKE.kdf_HKDF_SHA256, HPKE.aead_CHACHA20_POLY1305);
    }

    private static Map<String, Object> claims(String id, String recipient, String deviceKid, Document d,
            long size, String hash, long created, long expires, String signerKid) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("v", 1L);
        values.put("typ", "mycarlos-delivery-manifest");
        values.put("delivery_id", id);
        values.put("recipient_id", recipient);
        values.put("device_key_id", deviceKid);
        values.put("clinic_id", d.clinicId());
        values.put("clinic_name", d.clinicName());
        values.put("provider_name", d.providerName());
        values.put("patient", Map.of("name", d.patientName(), "birth_date", d.birthDate()));
        values.put("document", Map.of("title", d.title(), "type", d.type(), "service_date", d.serviceDate(),
                "media_type", "application/pdf", "size", size, "sha256", hash));
        values.put("created_at", created);
        values.put("expires_at", expires);
        values.put("kid", signerKid);
        return values;
    }

    private static byte[] outer(String id, String recipient, String kid, long size, String hash,
            long created, long expires) {
        return PortalMyCarlosDeliveryCodec.canonical(Map.of("v", 1L,
                "suite", Map.of("kem_id", 32L, "kdf_id", 1L, "aead_id", 3L),
                "delivery_id", id, "recipient_id", recipient, "device_key_id", kid,
                "size", size, "ciphertext_sha256", hash, "created_at", created, "expires_at", expires));
    }

    private static String signedManifest(byte[] payload, PatientPortalClinicKeysPublishRequest.Key identity, SignBytes signer) {
        try {
            byte[] signature = signer.sign(payload.clone(), identity.kid());
            if (signature == null || signature.length != 64) throw invalid();
            Signature check = Signature.getInstance("SHA256withECDSAinP1363Format");
            check.initVerify(PortalMyCarlosDeviceApprovalCrypto.clinicPublicKey(identity));
            check.update(payload);
            if (!check.verify(signature)) throw invalid();
            return PortalMyCarlosDeviceApprovalCodec.base64(payload) + "." + PortalMyCarlosDeviceApprovalCodec.base64(signature);
        } catch (GeneralSecurityException | java.io.IOException failure) { throw invalid(); }
    }

    /** RFC 6979 via the existing Bouncy Castle implementation, with P1363 wire encoding. */
    private static String sign(byte[] payload, PatientPortalClinicKeysPublishRequest.Key publicIdentity,
            PrivateKey privateKey) {
        try {
            if (!(PrivateKeyFactory.createKey(privateKey.getEncoded()) instanceof ECPrivateKeyParameters key)) {
                throw invalid();
            }
            ECDSASigner signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()));
            signer.init(true, key);
            BigInteger[] rs = signer.generateSignature(MessageDigest.getInstance("SHA-256").digest(payload));
            byte[] signature = new byte[64];
            for (int i = 0; i < 2; i++) {
                byte[] integer = rs[i].toByteArray();
                int count = integer.length == 33 && integer[0] == 0 ? 32 : integer.length;
                if (count > 32) throw invalid();
                System.arraycopy(integer, integer.length - count, signature, (i + 1) * 32 - count, count);
            }
            // Existing public identity validation requires named P-256. Verify key pairing here too.
            Signature check = Signature.getInstance("SHA256withECDSAinP1363Format");
            check.initVerify(PortalMyCarlosDeviceApprovalCrypto.clinicPublicKey(publicIdentity));
            check.update(payload);
            if (!check.verify(signature)) throw invalid();
            return PortalMyCarlosDeviceApprovalCodec.base64(payload) + "."
                    + PortalMyCarlosDeviceApprovalCodec.base64(signature);
        } catch (GeneralSecurityException | java.io.IOException failure) {
            throw invalid();
        }
    }
}
