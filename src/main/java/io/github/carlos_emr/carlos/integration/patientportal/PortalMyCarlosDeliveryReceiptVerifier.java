/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeliveryCodec.invalid;

import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.DeviceKeys;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.util.Objects;
import java.util.Set;

/** Independent device proof check, using immutable send-time keys and original content hashes. */
final class PortalMyCarlosDeliveryReceiptVerifier {
    private static final Set<String> FIELDS = Set.of("typ", "aud", "delivery_id", "recipient_id", "device_key_id",
            "ciphertext_sha256", "document_sha256", "picked_up_at", "vault_head_link", "jti");

    private PortalMyCarlosDeliveryReceiptVerifier() {}

    /** Historical binding survives subsequent key removal and rotation. */
    record Binding(String deliveryId, String recipientId, DeviceKeys device,
                   String ciphertextSha256, String documentSha256) {
        Binding {
            PortalMyCarlosDeliveryCodec.uuid(deliveryId, true);
            PortalMyCarlosDeliveryCodec.recipient(recipientId);
            Objects.requireNonNull(device);
            PortalMyCarlosDeliveryCodec.hash(ciphertextSha256);
            PortalMyCarlosDeliveryCodec.hash(documentSha256);
        }
        @Override public String toString() { return "DeliveryReceiptBinding[REDACTED]"; }
    }

    record Verified(String compact, String jti, long pickedUpAt, String vaultHeadLink) {
        @Override public String toString() { return "VerifiedDeliveryReceipt[REDACTED]"; }
    }

    /** No current freshness/removal check: a valid prior pickup remains a valid historical proof. */
    static Verified verify(String compact, Binding immutableSend) {
        if (compact == null || compact.length() > PortalMyCarlosDeliveryCodec.MAX_RECEIPT_BYTES
                || immutableSend == null) throw invalid();
        var envelope = PortalMyCarlosDeviceApprovalCodec.envelope(compact);
        var claims = PortalMyCarlosDeliveryCodec.object(envelope.payload().copy(),
                PortalMyCarlosDeliveryCodec.MAX_RECEIPT_BYTES);
        PortalMyCarlosDeliveryCodec.fields(claims, FIELDS);
        requireEqual("mycarlos-delivery-receipt", PortalMyCarlosDeliveryCodec.text(claims, "typ"));
        requireEqual("carlos-delivery-receipt", PortalMyCarlosDeliveryCodec.text(claims, "aud"));
        requireEqual(immutableSend.deliveryId(), PortalMyCarlosDeliveryCodec.text(claims, "delivery_id"));
        requireEqual(immutableSend.recipientId(), PortalMyCarlosDeliveryCodec.text(claims, "recipient_id"));
        requireEqual(immutableSend.device().kid(), PortalMyCarlosDeliveryCodec.text(claims, "device_key_id"));
        requireEqual(immutableSend.ciphertextSha256(), PortalMyCarlosDeliveryCodec.text(claims, "ciphertext_sha256"));
        requireEqual(immutableSend.documentSha256(), PortalMyCarlosDeliveryCodec.text(claims, "document_sha256"));
        String jti = PortalMyCarlosDeliveryCodec.text(claims, "jti");
        PortalMyCarlosDeliveryCodec.uuid(jti, false);
        String anchor = PortalMyCarlosDeliveryCodec.text(claims, "vault_head_link");
        PortalMyCarlosDeliveryCodec.hash(anchor);
        long pickedUpAt = PortalMyCarlosDeliveryCodec.epoch(claims, "picked_up_at");
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(PortalMyCarlosDeviceApprovalCrypto.ed25519PublicKey(
                    immutableSend.device().ed25519().copy()));
            signature.update(envelope.payload().copy());
            if (!signature.verify(envelope.signature().copy())) throw invalid();
        } catch (GeneralSecurityException failure) {
            throw invalid();
        }
        return new Verified(compact, jti, pickedUpAt, anchor);
    }

    private static void requireEqual(String expected, String actual) {
        if (!expected.equals(actual)) throw invalid();
    }
}
