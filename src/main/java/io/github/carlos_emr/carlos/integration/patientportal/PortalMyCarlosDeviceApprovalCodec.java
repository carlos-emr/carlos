/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.invalid;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Bytes;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Claims;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Decoded;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.DeviceKeys;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Envelope;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Kind;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Scope;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Strict flat M3 schemas; signatures and hashes always retain original bytes. */
final class PortalMyCarlosDeviceApprovalCodec {
    static final int MAX_COMPACT_BYTES = 8192;
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(4).maxStringLength(MAX_COMPACT_BYTES).maxNumberLength(20).build())
            .build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> COMMON = Set.of("v", "typ", "aud", "clinic_id", "recipient_id",
            "candidate_device_key_id", "candidate_x25519_public_key", "candidate_ed25519_public_key",
            "predecessor_device_key_id");

    private PortalMyCarlosDeviceApprovalCodec() {}

    static Envelope envelope(String compact) {
        if (compact == null || compact.length() > MAX_COMPACT_BYTES
                || !compact.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) {
            throw invalid();
        }
        int dot = compact.indexOf('.');
        byte[] payload = base64(compact.substring(0, dot), MAX_COMPACT_BYTES);
        byte[] signature = base64(compact.substring(dot + 1), 64);
        if (signature.length != 64) {
            throw invalid();
        }
        return new Envelope(compact, new Bytes(payload), new Bytes(signature));
    }

    static Decoded decode(Kind kind, String compact, Scope scope) {
        Envelope envelope = envelope(compact);
        return decodePayload(kind, envelope, scope);
    }

    private static Decoded decodePayload(Kind kind, Envelope envelope, Scope scope) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(envelope.payload().copy())).toString();
            JsonNode root = JSON.readTree(text);
            if (root == null || !root.isObject()) {
                throw invalid();
            }
            Set<String> names = new HashSet<>();
            root.fieldNames().forEachRemaining(names::add);
            if (!names.equals(fields(kind))) {
                throw invalid();
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (String field : names) {
                JsonNode value = root.get(field);
                if (value.isTextual()) {
                    utf8(value.textValue()); // Reject escaped unpaired UTF-16 surrogates too.
                    values.put(field, value.textValue());
                } else if (value.isIntegralNumber() && value.canConvertToLong()) {
                    values.put(field, value.longValue());
                } else if (value.isNull()) {
                    values.put(field, null);
                } else {
                    throw invalid();
                }
            }
            Claims claims = new Claims(values);
            if (!Long.valueOf(1).equals(values.get("v")) || !kind.typ.equals(claims.text("typ"))
                    || !kind.audience.equals(claims.text("aud"))
                    || (kind.deviceSignature && !"mycarlos".equals(claims.text("iss")))
                    || !scope.clinicId().equals(claims.text("clinic_id"))
                    || !scope.recipientId().equals(claims.text("recipient_id"))) {
                throw invalid();
            }
            if (names.contains("public_portal_base_url")
                    && !scope.canonicalPublicBase().equals(claims.text("public_portal_base_url"))) {
                throw invalid();
            }
            for (String field : Set.of("request_id", "jti")) {
                if (names.contains(field)) {
                    uuid4(claims.text(field));
                }
            }
            if (names.contains("challenge_id")) {
                exact32(claims.text("challenge_id"));
            }
            var candidate = new DeviceKeys(claims.text("candidate_device_key_id"),
                    new Bytes(exact32(claims.text("candidate_x25519_public_key"))),
                    new Bytes(exact32(claims.text("candidate_ed25519_public_key"))));
            String predecessor = claims.text("predecessor_device_key_id");
            if (predecessor != null) {
                manualPredecessorCode(predecessor);
            }
            for (String field : Set.of("predecessor_version", "accepted_version")) {
                if (names.contains(field) && values.get(field) != null) {
                    decimal(claims.text(field));
                }
            }
            for (String field : Set.of("candidate_intent_sha256", "challenge_sha256", "approval_sha256")) {
                if (names.contains(field)) {
                    if (values.get(field) == null && field.equals("approval_sha256")) {
                        continue;
                    }
                    String hash = claims.text(field);
                    if (hash == null || !hash.matches("[0-9a-f]{64}")) {
                        throw invalid();
                    }
                }
            }
            for (String field : Set.of("iat", "exp", "issued_at", "expires_at", "accepted_at")) {
                if (names.contains(field) && (!(values.get(field) instanceof Long value) || value < 0)) {
                    throw invalid();
                }
            }
            if (kind == Kind.INTENT) {
                interval(claims.epoch("iat"), claims.epoch("exp"));
            }
            if (kind == Kind.CHALLENGE || kind == Kind.ACCEPTANCE) {
                String method = claims.text("approval_kind");
                // Clinic code v1 (mycarlos#141): "code" has no predecessor, like "staff_first".
                boolean first = "staff_first".equals(method) || "code".equals(method);
                if (!(first || "accepted_device".equals(method)) || first != (predecessor == null)) {
                    throw invalid();
                }
                exact32(claims.text("kid"));
                if (kind == Kind.CHALLENGE) {
                    if (first != (values.get("predecessor_version") == null)) {
                        throw invalid();
                    }
                    interval(claims.epoch("issued_at"), claims.epoch("expires_at"));
                } else if (!"0".equals(claims.text("accepted_version"))
                        || first != (values.get("approval_sha256") == null)) {
                    throw invalid();
                }
            }
            if (kind == Kind.APPROVAL && (predecessor == null || values.get("predecessor_version") == null)) {
                throw invalid();
            }
            return new Decoded(kind, envelope, claims, candidate);
        } catch (IOException | ClassCastException | NullPointerException failure) {
            throw invalid();
        }
    }

    /** Admission only. Historical cryptographic checks deliberately do not call this method. */
    static void requireFreshForNewAction(Decoded object, long now) {
        if (now < 0) {
            throw invalid();
        }
        Claims claims = object.claims();
        if (object.kind() == Kind.INTENT) {
            long issued = claims.epoch("iat");
            if ((issued > now && issued - now > 30) || now >= claims.epoch("exp")) {
                throw invalid();
            }
        } else if (object.kind() == Kind.CHALLENGE && now >= claims.epoch("expires_at")) {
            throw invalid();
        }
    }

    /** No trim, case folding or separator removal: all 43 base64url characters are identity. */
    static String manualPredecessorCode(String entered) {
        exact32(entered);
        return entered;
    }

    static long decimal(String value) {
        if (value == null || value.length() > 19 || !value.matches("0|[1-9][0-9]*")) {
            throw invalid();
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException failure) {
            throw invalid();
        }
    }

    static byte[] exact32(String value) {
        if (value == null || value.length() != 43) {
            throw invalid();
        }
        byte[] bytes = base64(value, 32);
        if (bytes.length != 32) {
            throw invalid();
        }
        return bytes;
    }

    static byte[] base64(String value, int maximumBytes) {
        if (value == null || value.isEmpty() || value.length() > (maximumBytes * 4 + 2) / 3
                || !value.matches("[A-Za-z0-9_-]+")) {
            throw invalid();
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(value);
            if (bytes.length == 0 || bytes.length > maximumBytes || !base64(bytes).equals(value)) {
                throw invalid();
            }
            return bytes;
        } catch (IllegalArgumentException failure) {
            throw invalid();
        }
    }

    static String base64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static byte[] utf8(String text) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(text));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException failure) {
            throw invalid();
        }
    }

    /**
     * JCS for the frozen flat string/null/integer claim subset. Producer integers must be exactly
     * representable in IEEE-754; existing epoch seconds satisfy that bound. Verifiers accept the
     * complete signed long range and do not compare against any reserialized representation.
     */
    static byte[] canonicalPayload(Kind kind, Map<String, Object> values, Scope scope) {
        Claims checked = new Claims(values);
        for (Object value : checked.values().values()) {
            if (value instanceof Long number && (number < -9_007_199_254_740_991L
                    || number > 9_007_199_254_740_991L)) {
                throw invalid();
            }
            if (value instanceof String text) {
                utf8(text);
            }
        }
        StringBuilder encoded = new StringBuilder("{");
        boolean first = true;
        for (var entry : new TreeMap<>(checked.values()).entrySet()) {
            if (!first) {
                encoded.append(',');
            }
            first = false;
            appendJsonString(encoded, entry.getKey());
            encoded.append(':');
            if (entry.getValue() instanceof String text) {
                appendJsonString(encoded, text);
            } else {
                encoded.append(entry.getValue()); // Only null or a checked exact integer.
            }
        }
        byte[] payload = utf8(encoded.append('}').toString());
        var unsigned = new Envelope("", new Bytes(payload), new Bytes(new byte[64]));
        decodePayload(kind, unsigned, scope);
        return payload;
    }

    private static void appendJsonString(StringBuilder output, String value) {
        utf8(value);
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20) {
                        output.append("\\u00");
                        output.append("0123456789abcdef".charAt(character >>> 4));
                        output.append("0123456789abcdef".charAt(character & 15));
                    } else {
                        output.append(character);
                    }
                }
            }
        }
        output.append('"');
    }

    private static void interval(long issued, long expires) {
        if (expires <= issued || expires - issued > 600) {
            throw invalid();
        }
    }

    private static void uuid4(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value) || parsed.version() != 4 || parsed.variant() != 2) {
                throw invalid();
            }
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw invalid();
        }
    }

    private static Set<String> fields(Kind kind) {
        Set<String> fields = new HashSet<>(COMMON);
        switch (kind) {
            case INTENT -> fields.addAll(Set.of("iss", "request_id", "public_portal_base_url", "iat", "exp"));
            case CHALLENGE -> fields.addAll(Set.of("request_id", "public_portal_base_url", "challenge_id",
                    "candidate_intent_sha256", "approval_kind", "predecessor_version", "issued_at", "expires_at", "kid"));
            case APPROVAL -> fields.addAll(Set.of("iss", "jti", "challenge_id", "challenge_sha256",
                    "candidate_intent_sha256", "predecessor_version"));
            case ACCEPTANCE -> fields.addAll(Set.of("request_id", "public_portal_base_url", "challenge_id",
                    "challenge_sha256", "approval_kind", "approval_sha256", "accepted_at", "accepted_version", "kid"));
            default -> throw invalid();
        }
        return fields;
    }
}
