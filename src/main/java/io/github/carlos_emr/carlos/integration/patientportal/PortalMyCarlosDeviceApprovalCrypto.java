/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.invalid;

import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Claims;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Decoded;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.DeviceKeys;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Envelope;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Flow;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Kind;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Scope;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.TrustedKeys;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.VerifiedFlow;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/** Standard JCA Ed25519/P-256 operations only; no key loading, persistence or trust promotion. */
final class PortalMyCarlosDeviceApprovalCrypto {
    private static final byte[] DEVICE_DOMAIN = "mycarlos-device-key-v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ED25519_SPKI_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");

    private PortalMyCarlosDeviceApprovalCrypto() {}

    static Decoded verify(Kind kind, String compact, TrustedKeys trusted) {
        Decoded object = PortalMyCarlosDeviceApprovalCodec.decode(kind, compact, trusted.scope());
        PublicKey key;
        if (kind == Kind.INTENT) {
            // Candidate possession is intentionally not a statement of local acceptance.
            key = ed25519PublicKey(object.candidate().ed25519().copy());
        } else if (kind == Kind.APPROVAL) {
            DeviceKeys predecessor = trusted.locallyAcceptedPredecessor();
            if (predecessor == null
                    || !predecessor.kid().equals(object.claims().text("predecessor_device_key_id"))) {
                throw invalid();
            }
            key = ed25519PublicKey(predecessor.ed25519().copy());
        } else {
            var clinicKey = trusted.clinicKeys().get(object.claims().text("kid"));
            if (clinicKey == null || !"active".equals(clinicKey.status())) {
                throw invalid();
            }
            key = clinicPublicKey(clinicKey);
        }
        verifyPhysicalSignature(kind, compact, key);
        return object;
    }

    /**
     * Physical signature only. An authentic signature over hostile claims is still an invalid
     * approval; callers needing a valid object must use verify(), not this diagnostic primitive.
     */
    static void verifyPhysicalSignature(Kind kind, String compact, PublicKey selectedPublicKey) {
        Envelope envelope = PortalMyCarlosDeviceApprovalCodec.envelope(compact);
        try {
            Signature signature = Signature.getInstance(algorithm(kind));
            signature.initVerify(selectedPublicKey);
            signature.update(envelope.payload().copy());
            if (!signature.verify(envelope.signature().copy())) {
                throw invalid();
            }
        } catch (GeneralSecurityException failure) {
            throw invalid();
        }
    }

    /** Caller supplies the selected key; signed bytes must later be persisted once by its owner. */
    static String sign(Kind kind, Map<String, Object> claims, Scope scope, PrivateKey selectedKey) {
        byte[] payload = PortalMyCarlosDeviceApprovalCodec.canonicalPayload(kind, claims, scope);
        try {
            Signature signature = Signature.getInstance(algorithm(kind));
            signature.initSign(selectedKey);
            signature.update(payload);
            byte[] signed = signature.sign();
            if (signed.length != 64) {
                throw invalid();
            }
            String compact = PortalMyCarlosDeviceApprovalCodec.base64(payload) + "."
                    + PortalMyCarlosDeviceApprovalCodec.base64(signed);
            PortalMyCarlosDeviceApprovalCodec.envelope(compact); // Includes total byte bound.
            return compact;
        } catch (GeneralSecurityException failure) {
            throw invalid();
        }
    }

    /** Historical consistency only; current accepted/removed/version/WRITE/CSRF checks are external. */
    static VerifiedFlow verifyFlow(Flow flow, TrustedKeys trusted) {
        Decoded intent = verify(Kind.INTENT, flow.intent(), trusted);
        Decoded challenge = verify(Kind.CHALLENGE, flow.challenge(), trusted);
        Decoded acceptance = verify(Kind.ACCEPTANCE, flow.acceptance(), trusted);
        Decoded approval = flow.approval() == null ? null : verify(Kind.APPROVAL, flow.approval(), trusted);
        Claims i = intent.claims();
        Claims c = challenge.claims();
        Claims a = acceptance.claims();
        for (String field : new String[] {"clinic_id", "recipient_id", "candidate_device_key_id",
                "candidate_x25519_public_key", "candidate_ed25519_public_key", "predecessor_device_key_id",
                "public_portal_base_url", "request_id"}) {
            requireEqual(i.values().get(field), c.values().get(field));
            requireEqual(c.values().get(field), a.values().get(field));
        }
        String intentHash = compactHash(flow.intent());
        String challengeHash = compactHash(flow.challenge());
        requireEqual(c.text("candidate_intent_sha256"), intentHash);
        requireEqual(a.text("challenge_sha256"), challengeHash);
        requireEqual(a.text("challenge_id"), c.text("challenge_id"));
        requireEqual(a.text("approval_kind"), c.text("approval_kind"));
        long issued = c.epoch("issued_at");
        long maximumExpiry = issued > Long.MAX_VALUE - 600 ? Long.MAX_VALUE : issued + 600;
        if (c.epoch("expires_at") != Math.min(maximumExpiry, i.epoch("exp"))) {
            throw invalid();
        }
        if ("staff_first".equals(c.text("approval_kind"))) {
            if (approval != null) {
                throw invalid();
            }
        } else {
            if (approval == null) {
                throw invalid();
            }
            Claims p = approval.claims();
            for (String field : new String[] {"clinic_id", "recipient_id", "candidate_device_key_id",
                    "candidate_x25519_public_key", "candidate_ed25519_public_key", "predecessor_device_key_id",
                    "challenge_id", "predecessor_version"}) {
                requireEqual(p.values().get(field), c.values().get(field));
            }
            requireEqual(p.text("candidate_intent_sha256"), intentHash);
            requireEqual(p.text("challenge_sha256"), challengeHash);
            requireEqual(a.text("approval_sha256"), compactHash(flow.approval()));
        }
        return new VerifiedFlow(intent, challenge, approval, acceptance);
    }

    static String deviceKeyId(byte[] rawX25519, byte[] rawEd25519) {
        if (rawX25519 == null || rawEd25519 == null || rawX25519.length != 32 || rawEd25519.length != 32) {
            throw invalid();
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.writeBytes(DEVICE_DOMAIN); // Existing M0 domain has no additional terminator.
        bytes.writeBytes(frame(rawX25519, rawEd25519));
        return PortalMyCarlosDeviceApprovalCodec.base64(digest(bytes.toByteArray()));
    }

    static byte[] frame(byte[]... values) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (byte[] value : values) {
            bytes.writeBytes(ByteBuffer.allocate(Long.BYTES).putLong(value.length).array());
            bytes.writeBytes(value);
        }
        return bytes.toByteArray();
    }

    /** Hash exact caller-supplied method/path/query/body bytes without parsing or normalization. */
    static String requestHash(String method, String rawPath, String rawQuery, byte[] exactBody) {
        return sha256(frame(PortalMyCarlosDeviceApprovalCodec.utf8(method),
                PortalMyCarlosDeviceApprovalCodec.utf8(rawPath),
                PortalMyCarlosDeviceApprovalCodec.utf8(rawQuery), exactBody));
    }

    static String compactHash(String compact) {
        PortalMyCarlosDeviceApprovalCodec.envelope(compact);
        return sha256(compact.getBytes(StandardCharsets.US_ASCII));
    }

    static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(digest(bytes));
    }

    static PublicKey ed25519PublicKey(byte[] raw) {
        if (raw == null || raw.length != 32) {
            throw invalid();
        }
        byte[] encoded = Arrays.copyOf(ED25519_SPKI_PREFIX, ED25519_SPKI_PREFIX.length + raw.length);
        System.arraycopy(raw, 0, encoded, ED25519_SPKI_PREFIX.length, raw.length);
        try {
            PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
            if (!Arrays.equals(key.getEncoded(), encoded)) {
                throw invalid();
            }
            return key;
        } catch (GeneralSecurityException failure) {
            throw invalid();
        }
    }

    static PublicKey clinicPublicKey(PatientPortalClinicKeysPublishRequest.Key validatedKey) {
        try {
            return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(
                    PortalMyCarlosDeviceApprovalCodec.base64(validatedKey.spki(), 512)));
        } catch (GeneralSecurityException failure) {
            throw invalid();
        }
    }

    private static byte[] digest(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (GeneralSecurityException failure) {
            throw invalid();
        }
    }

    private static String algorithm(Kind kind) {
        return kind.deviceSignature ? "Ed25519" : "SHA256withECDSAinP1363Format";
    }

    private static void requireEqual(Object left, Object right) {
        if (!Objects.equals(left, right)) {
            throw invalid();
        }
    }
}
