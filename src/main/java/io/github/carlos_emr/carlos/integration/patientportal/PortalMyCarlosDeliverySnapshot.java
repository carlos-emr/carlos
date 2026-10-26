/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeliveryCodec.*;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Bytes;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.DeviceKeys;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** All immutable send-time identity, accepted keys, configuration and exact bytes, before HTTP. */
record PortalMyCarlosDeliverySnapshot(String clinicId, int demographicNo, String recipientId,
        String publicBase, String internalTarget, String configSha256, long acceptedDeviceId,
        long acceptanceVersion, String acceptanceSha256, String deliveryId, DeviceKeys device,
        String signerKid, String documentSha256, String ciphertextSha256, long createdAt,
        long expiresAt, String bodySha256, Bytes body, String sourceDocumentId) {
    private static final Set<String> FIELDS = Set.of("clinic_id", "demographic_no", "recipient_id", "public_base",
            "internal_target", "config_sha256", "accepted_device_id", "acceptance_version", "acceptance_sha256",
            "delivery_id", "device_key_id", "x25519_public_key", "ed25519_public_key", "signer_kid",
            "document_sha256", "ciphertext_sha256", "created_at", "expires_at", "body_sha256");

    PortalMyCarlosDeliverySnapshot(String clinicId, int demographicNo, String recipientId,
            String publicBase, String internalTarget, String configSha256, long acceptedDeviceId,
            long acceptanceVersion, String acceptanceSha256, String deliveryId, DeviceKeys device,
            String signerKid, String documentSha256, String ciphertextSha256, long createdAt,
            long expiresAt, String bodySha256, Bytes body) {
        this(clinicId, demographicNo, recipientId, publicBase, internalTarget, configSha256, acceptedDeviceId,
                acceptanceVersion, acceptanceSha256, deliveryId, device, signerKid, documentSha256,
                ciphertextSha256, createdAt, expiresAt, bodySha256, body, null);
    }

    PortalMyCarlosDeliverySnapshot {
        if (clinicId == null || clinicId.isEmpty() || clinicId.codePointCount(0, clinicId.length()) > 64
                || demographicNo <= 0 || acceptedDeviceId <= 0 || acceptanceVersion < 0 || device == null
                || body == null || body.size() > MAX_BODY_BYTES) throw invalid();
        if (sourceDocumentId != null) PortalMyCarlosDeviceApprovalCodec.exact32(sourceDocumentId);
        text(clinicId);
        recipient(recipientId);
        uuid(deliveryId, true);
        lifetime(createdAt, expiresAt);
        for (String hash : new String[] {configSha256, acceptanceSha256, documentSha256, ciphertextSha256, bodySha256}) hash(hash);
        PortalMyCarlosDeviceApprovalCodec.exact32(signerKid);
        // These are already normalized, locally configured destinations, never portal-provided URLs.
        for (String address : new String[] {publicBase, internalTarget}) {
            text(address);
            try {
                URI uri = URI.create(address);
                if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null
                        || uri.getRawQuery() != null || uri.getRawFragment() != null || address.length() > 2048
                        || !uri.toASCIIString().equals(address) || !uri.normalize().equals(uri)) throw invalid();
            } catch (IllegalArgumentException failure) { throw invalid(); }
        }
        if (!URI.create(internalTarget).getRawPath().endsWith("/internal/carlos/patients/" + demographicNo + "/mycarlos/deliveries")) throw invalid();
        if (!PortalMyCarlosDeviceApprovalCrypto.sha256(body.copy()).equals(bodySha256)) throw invalid();
        byte[][] parts = twoFrames(body.copy(), MAX_OUTER_BYTES, MAX_SEALED_BYTES);
        JsonNode outer = object(parts[0], MAX_OUTER_BYTES);
        fields(outer, Set.of("v", "suite", "delivery_id", "recipient_id", "device_key_id", "size",
                "ciphertext_sha256", "created_at", "expires_at"));
        fields(outer.get("suite"), Set.of("kem_id", "kdf_id", "aead_id"));
        if (epoch(outer, "v") != 1 || epoch(outer.get("suite"), "kem_id") != 32
                || epoch(outer.get("suite"), "kdf_id") != 1 || epoch(outer.get("suite"), "aead_id") != 3
                || !deliveryId.equals(text(outer, "delivery_id")) || !recipientId.equals(text(outer, "recipient_id"))
                || !device.kid().equals(text(outer, "device_key_id")) || epoch(outer, "size") != parts[1].length
                || parts[1].length < 49 || !ciphertextSha256.equals(text(outer, "ciphertext_sha256"))
                || !ciphertextSha256.equals(PortalMyCarlosDeviceApprovalCrypto.sha256(parts[1]))
                || epoch(outer, "created_at") != createdAt || epoch(outer, "expires_at") != expiresAt) throw invalid();
    }

    byte[] encode() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("clinic_id", clinicId);
        values.put("demographic_no", (long) demographicNo);
        values.put("recipient_id", recipientId);
        values.put("public_base", publicBase);
        values.put("internal_target", internalTarget);
        values.put("config_sha256", configSha256);
        // Versions and row IDs may span the signed long range; encode them as canonical decimals.
        values.put("accepted_device_id", Long.toString(acceptedDeviceId));
        values.put("acceptance_version", Long.toString(acceptanceVersion));
        values.put("acceptance_sha256", acceptanceSha256);
        values.put("delivery_id", deliveryId);
        values.put("device_key_id", device.kid());
        values.put("x25519_public_key", PortalMyCarlosDeviceApprovalCodec.base64(device.x25519().copy()));
        values.put("ed25519_public_key", PortalMyCarlosDeviceApprovalCodec.base64(device.ed25519().copy()));
        values.put("signer_kid", signerKid);
        values.put("document_sha256", documentSha256);
        values.put("ciphertext_sha256", ciphertextSha256);
        values.put("created_at", createdAt);
        values.put("expires_at", expiresAt);
        values.put("body_sha256", bodySha256);
        if (sourceDocumentId != null) values.put("source_document_id", sourceDocumentId);
        byte[] metadata = canonical(values);
        if (metadata.length > 16384) throw invalid();
        return PortalMyCarlosDeviceApprovalCrypto.frame(metadata, body.copy());
    }

    static PortalMyCarlosDeliverySnapshot decode(byte[] bytes) {
        byte[][] parts = twoFrames(bytes, 16384, MAX_BODY_BYTES);
        JsonNode m = object(parts[0], 16384);
        var expected = new java.util.HashSet<>(FIELDS);
        if (m.has("source_document_id")) expected.add("source_document_id");
        fields(m, expected);
        long demographic = epoch(m, "demographic_no");
        if (demographic > Integer.MAX_VALUE) throw invalid();
        var keys = new DeviceKeys(text(m, "device_key_id"),
                new Bytes(PortalMyCarlosDeviceApprovalCodec.exact32(text(m, "x25519_public_key"))),
                new Bytes(PortalMyCarlosDeviceApprovalCodec.exact32(text(m, "ed25519_public_key"))));
        return new PortalMyCarlosDeliverySnapshot(text(m, "clinic_id"), (int) demographic, text(m, "recipient_id"),
                text(m, "public_base"), text(m, "internal_target"), text(m, "config_sha256"),
                PortalMyCarlosDeliveryClient.cursor(text(m, "accepted_device_id")),
                PortalMyCarlosDeliveryClient.cursor(text(m, "acceptance_version")), text(m, "acceptance_sha256"),
                text(m, "delivery_id"), keys, text(m, "signer_kid"), text(m, "document_sha256"),
                text(m, "ciphertext_sha256"), epoch(m, "created_at"), epoch(m, "expires_at"),
                text(m, "body_sha256"), new Bytes(parts[1]), m.has("source_document_id") ? text(m, "source_document_id") : null);
    }

    int sealedSize() {
        ByteBuffer framed = ByteBuffer.wrap(body.copy());
        framed.position(Math.toIntExact(8 + framed.getLong()));
        return Math.toIntExact(framed.getLong());
    }

    PortalMyCarlosDeliveryReceiptVerifier.Binding receiptBinding() {
        return new PortalMyCarlosDeliveryReceiptVerifier.Binding(deliveryId, recipientId, device, ciphertextSha256, documentSha256);
    }

    private static byte[][] twoFrames(byte[] bytes, int maximumFirst, int maximumSecond) {
        if (bytes == null || bytes.length < 16 || bytes.length > maximumFirst + maximumSecond + 16) throw invalid();
        ByteBuffer data = ByteBuffer.wrap(bytes);
        byte[][] values = new byte[2][];
        for (int i = 0; i < 2; i++) {
            if (data.remaining() < 8) throw invalid();
            long length = data.getLong();
            if (length <= 0 || length > (i == 0 ? maximumFirst : maximumSecond) || length > data.remaining()) throw invalid();
            values[i] = new byte[(int) length];
            data.get(values[i]);
        }
        if (data.hasRemaining()) throw invalid();
        return values;
    }

    @Override public String toString() { return "DeliverySnapshot[REDACTED]"; }
}
