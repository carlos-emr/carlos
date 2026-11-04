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
    private static final byte[] AID_MAGIC = "mycarlos-reading-aid/1".getBytes(StandardCharsets.US_ASCII);
    /** The contract's limits for a reading aid: its JSON, and its sealed copy (answer: signed JSON). */
    static final int MAX_AID_JSON_BYTES = 64 * 1024;
    static final int MAX_AID_SEALED_BYTES = 1024 * 1024;

    private PortalMyCarlosDeliveryComposer() {}

    record Document(String clinicId, String clinicName, String providerName, String patientName,
                    String birthDate, String title, String type, String serviceDate) {
        Document {
            for (String value : new String[] {clinicId, clinicName, providerName, patientName,
                    birthDate, title, type, serviceDate}) {
                PortalMyCarlosDeliveryCodec.text(value);
            }
            label(clinicId, 64);
            for (String value : new String[] {clinicName, providerName, patientName, title}) label(value, 240);
            label(type, 64);
            date(birthDate);
            if (!serviceDate.isEmpty()) date(serviceDate);
        }
        @Override public String toString() { return "DeliveryDocument[REDACTED]"; }
    }

    /** Native M0 label and known-date policy, checked before signing or encryption. */
    private static void label(String value, int maximum) {
        if (value.isEmpty() || value.codePointCount(0, value.length()) > maximum
                || value.codePoints().anyMatch(Character::isISOControl)) throw invalid();
    }

    private static void date(String value) {
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid();
        try {
            var parsed = java.time.LocalDate.parse(value);
            if (parsed.getYear() <= 0 || !parsed.toString().equals(value)) throw invalid();
        } catch (java.time.DateTimeException failure) { throw invalid(); }
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
                ciphertextHash, createdAt, expiresAt, null);
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

    /** {@code kind} is absent on an original and "reading-aid/1" on an aid (contract, "Reading aids"). */
    private static byte[] outer(String id, String recipient, String kid, long size, String hash,
            long created, long expires, String kind) {
        Map<String, Object> values = new LinkedHashMap<>(Map.of("v", 1L,
                "suite", Map.of("kem_id", 32L, "kdf_id", 1L, "aead_id", 3L),
                "delivery_id", id, "recipient_id", recipient, "device_key_id", kid,
                "size", size, "ciphertext_sha256", hash, "created_at", created, "expires_at", expires));
        if (kind != null) values.put("kind", kind);
        return PortalMyCarlosDeliveryCodec.canonical(values);
    }

    /**
     * Seals an already signed reading aid for the accepted device, as its own delivery. The caller
     * built {@code readingAidJson} and the signed {@code compactManifest} (ReadingAidArtifact); this
     * only checks that the two agree with this delivery's identity and the contract's limits, then
     * seals {@code mycarlos-reading-aid/1 | manifest | json} exactly as an original is sealed. The
     * copy's "original PDF" bytes are the aid JSON, so its document hash is the JSON hash, which is
     * what the aid's receipt names.
     */
    static Copy composeAid(String deliveryId, String recipientId, DeviceKeys acceptedDevice,
            byte[] readingAidJson, String compactManifest, long createdAt, long expiresAt, String signerKid) {
        PortalMyCarlosDeliveryCodec.uuid(deliveryId, true);
        PortalMyCarlosDeliveryCodec.recipient(recipientId);
        PortalMyCarlosDeliveryCodec.lifetime(createdAt, expiresAt);
        if (acceptedDevice == null || readingAidJson == null || readingAidJson.length == 0
                || readingAidJson.length > MAX_AID_JSON_BYTES || compactManifest == null || signerKid == null) {
            throw invalid();
        }
        byte[] json = readingAidJson.clone();
        String jsonHash = PortalMyCarlosDeviceApprovalCrypto.sha256(json);
        var claims = PortalMyCarlosDeviceApprovalCodec.envelope(compactManifest).payload().copy();
        com.fasterxml.jackson.databind.JsonNode m = PortalMyCarlosDeliveryCodec.object(claims, 8192);
        com.fasterxml.jackson.databind.JsonNode aid = m.get("reading_aid");
        if (aid == null || m.get("pair") == null || PortalMyCarlosDeliveryCodec.epoch(m, "v") != 1
                || !"mycarlos-reading-aid-manifest".equals(PortalMyCarlosDeliveryCodec.text(m, "typ"))
                || !deliveryId.equals(PortalMyCarlosDeliveryCodec.text(m, "delivery_id"))
                || !recipientId.equals(PortalMyCarlosDeliveryCodec.text(m, "recipient_id"))
                || !acceptedDevice.kid().equals(PortalMyCarlosDeliveryCodec.text(m, "device_key_id"))
                || !signerKid.equals(PortalMyCarlosDeliveryCodec.text(m, "kid"))
                || !jsonHash.equals(PortalMyCarlosDeliveryCodec.text(aid, "sha256"))
                || PortalMyCarlosDeliveryCodec.epoch(aid, "size") != json.length
                || PortalMyCarlosDeliveryCodec.epoch(m, "created_at") != createdAt
                || PortalMyCarlosDeliveryCodec.epoch(m, "expires_at") != expiresAt) throw invalid();
        byte[] plaintext = PortalMyCarlosDeviceApprovalCrypto.frame(AID_MAGIC,
                compactManifest.getBytes(StandardCharsets.US_ASCII), json);
        byte[] sealed = seal(plaintext, acceptedDevice, aad(deliveryId, recipientId, acceptedDevice.kid()));
        if (sealed.length > MAX_AID_SEALED_BYTES) throw invalid();
        String ciphertextHash = PortalMyCarlosDeviceApprovalCrypto.sha256(sealed);
        byte[] outer = outer(deliveryId, recipientId, acceptedDevice.kid(), sealed.length, ciphertextHash,
                createdAt, expiresAt, PortalMyCarlosDeliverySnapshot.READING_AID_KIND);
        byte[] body = PortalMyCarlosDeviceApprovalCrypto.frame(outer, sealed);
        if (outer.length > PortalMyCarlosDeliveryCodec.MAX_OUTER_BYTES
                || body.length > PortalMyCarlosDeliveryCodec.MAX_BODY_BYTES) throw invalid();
        return new Copy(deliveryId, recipientId, acceptedDevice, signerKid, createdAt, expiresAt,
                new Bytes(json), jsonHash, compactManifest, new Bytes(sealed), new Bytes(body),
                ciphertextHash, PortalMyCarlosDeviceApprovalCrypto.sha256(body));
    }

    private static byte[] seal(byte[] plaintext, DeviceKeys device, byte[] aad) {
        try {
            HPKE hpke = hpke();
            var context = hpke.setupBaseS(hpke.deserializePublicKey(device.x25519().copy()), INFO);
            byte[] ciphertext = context.seal(aad, plaintext);
            return ByteBuffer.allocate(32 + ciphertext.length)
                    .put(context.getEncapsulation()).put(ciphertext).array();
        } catch (Exception failure) {
            throw invalid();
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
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
