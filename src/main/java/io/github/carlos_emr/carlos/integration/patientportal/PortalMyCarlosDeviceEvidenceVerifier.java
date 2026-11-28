/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeliveryCodec.*;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.DeviceKeys;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.util.HashSet;
import java.util.Set;

/** Historical original device assertions; present-day freshness is deliberately not an evidence check. */
final class PortalMyCarlosDeviceEvidenceVerifier {
    private static final Set<String> BASE = Set.of("aud", "iss", "iat", "exp", "jti", "kid", "recipient_id", "request_hash");
    private PortalMyCarlosDeviceEvidenceVerifier() {}

    record Verified(String compact, String anchor) {
        @Override public String toString() { return "VerifiedDeviceEvidence[REDACTED]"; }
    }

    static Verified verify(String compact, DeviceKeys immutableDevice, String recipient) {
        if (compact == null || compact.length() > 4096 || immutableDevice == null) throw invalid();
        recipient(recipient);
        var envelope = PortalMyCarlosDeviceApprovalCodec.envelope(compact);
        var claims = object(envelope.payload().copy(), 4096);
        Set<String> expected = new HashSet<>(BASE);
        if (claims.has("vault_head_link")) expected.add("vault_head_link");
        fields(claims, expected);
        if (!"mycarlos".equals(text(claims, "iss"))
                || !"carlos-patient-portal-device-api".equals(text(claims, "aud"))
                || !immutableDevice.kid().equals(text(claims, "kid"))
                || !recipient.equals(text(claims, "recipient_id"))) throw invalid();
        long issued = epoch(claims, "iat"), expires = epoch(claims, "exp");
        if (expires <= issued || expires - issued > 120) throw invalid();
        uuid(text(claims, "jti"), false);
        hash(text(claims, "request_hash"));
        String anchor = claims.has("vault_head_link") ? text(claims, "vault_head_link") : null;
        if (anchor != null) hash(anchor);
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(PortalMyCarlosDeviceApprovalCrypto.ed25519PublicKey(immutableDevice.ed25519().copy()));
            verifier.update(envelope.payload().copy());
            if (!verifier.verify(envelope.signature().copy())) throw invalid();
        } catch (GeneralSecurityException failure) { throw invalid(); }
        // request_hash is signed evidence only. Arbitrary historical HTTP bytes are unavailable;
        // neither the request hash nor the per-link HMAC is claimed to have been recomputed here.
        return new Verified(compact, anchor);
    }
}
