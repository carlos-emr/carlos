/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Pure M3 values. None of these values establishes or changes local device acceptance. */
final class PortalMyCarlosDeviceApprovalTypes {
    private PortalMyCarlosDeviceApprovalTypes() {}

    enum Kind {
        INTENT("mycarlos-device-approval-intent", "carlos-mycarlos-device-approval-intent", true),
        CHALLENGE("mycarlos-device-approval-challenge", "carlos-mycarlos-device-approval", false),
        APPROVAL("mycarlos-device-approval", "carlos-mycarlos-device-approval", true),
        ACCEPTANCE("mycarlos-device-acceptance", "carlos-mycarlos-device-approval", false);

        final String typ;
        final String audience;
        final boolean deviceSignature;

        Kind(String typ, String audience, boolean deviceSignature) {
            this.typ = typ;
            this.audience = audience;
            this.deviceSignature = deviceSignature;
        }
    }

    /** Defensive arrays with content equality; records containing raw arrays would be unsafe. */
    static final class Bytes {
        private final byte[] value;

        Bytes(byte[] value) {
            this.value = Objects.requireNonNull(value).clone();
        }

        byte[] copy() {
            return value.clone();
        }

        int size() {
            return value.length;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Bytes bytes && Arrays.equals(value, bytes.value);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(value);
        }

        @Override
        public String toString() {
            return "ApprovalBytes[REDACTED]";
        }
    }

    /**
     * The caller supplies its saved/configured public base AFTER frozen QR normalization. This
     * slice compares exact canonical bytes; it neither implements a second URL normalizer nor
     * accepts an internal origin or a network destination from signed claims.
     */
    record Scope(String clinicId, String recipientId, String canonicalPublicBase) {
        Scope {
            if (clinicId == null || clinicId.isEmpty() || clinicId.codePointCount(0, clinicId.length()) > 64
                    || recipientId == null || !recipientId.matches("[\\x00-\\x7f]{1,64}")
                    || canonicalPublicBase == null || canonicalPublicBase.isEmpty()
                    || PortalMyCarlosDeviceApprovalCodec.utf8(canonicalPublicBase).length > 1024) {
                throw invalid();
            }
            PortalMyCarlosDeviceApprovalCodec.utf8(clinicId);
        }
    }

    record DeviceKeys(String kid, Bytes x25519, Bytes ed25519) {
        DeviceKeys {
            if (x25519 == null || ed25519 == null || x25519.size() != 32 || ed25519.size() != 32
                    || allZero(x25519.copy()) || allZero(ed25519.copy())
                    || !PortalMyCarlosDeviceApprovalCrypto.deviceKeyId(
                            x25519.copy(), ed25519.copy()).equals(kid)) {
                throw invalid();
            }
        }

        private static boolean allZero(byte[] value) {
            int bits = 0;
            for (byte octet : value) {
                bits |= octet;
            }
            return bits == 0;
        }

        @Override
        public String toString() {
            return "DeviceKeys[REDACTED]";
        }
    }

    /** Public keys come from the caller's already validated local catalogue, never the envelope. */
    record TrustedKeys(Scope scope, Map<String, PatientPortalClinicKeysPublishRequest.Key> clinicKeys,
                       DeviceKeys locallyAcceptedPredecessor) {
        TrustedKeys {
            Objects.requireNonNull(scope);
            clinicKeys = Map.copyOf(clinicKeys);
            for (var entry : clinicKeys.entrySet()) {
                if (!entry.getKey().equals(entry.getValue().kid())) {
                    throw invalid();
                }
            }
        }
    }

    record Envelope(String compact, Bytes payload, Bytes signature) {
        Envelope {
            Objects.requireNonNull(compact);
            Objects.requireNonNull(payload);
            Objects.requireNonNull(signature);
        }

        @Override
        public String toString() {
            return "ApprovalEnvelope[REDACTED]";
        }
    }

    /** Only flat immutable primitive values are allowed by the four frozen claim schemas. */
    record Claims(Map<String, Object> values) {
        Claims {
            for (var value : values.values()) {
                if (value != null && !(value instanceof String) && !(value instanceof Long)) {
                    throw invalid();
                }
            }
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }

        String text(String name) {
            return (String) values.get(name);
        }

        long epoch(String name) {
            return (Long) values.get(name);
        }

        @Override
        public String toString() {
            return "ApprovalClaims[REDACTED]";
        }
    }

    /** Strictly decoded claims and original signed bytes; cryptographic verification is separate. */
    record Decoded(Kind kind, Envelope envelope, Claims claims, DeviceKeys candidate) {}

    record Flow(String intent, String challenge, String approval, String acceptance) {}

    /** Historical proof only. A service must still authorize against current locked local state. */
    record VerifiedFlow(Decoded intent, Decoded challenge, Decoded approval, Decoded acceptance) {}

    static IllegalArgumentException invalid() {
        return new IllegalArgumentException("device approval proof is invalid");
    }
}
