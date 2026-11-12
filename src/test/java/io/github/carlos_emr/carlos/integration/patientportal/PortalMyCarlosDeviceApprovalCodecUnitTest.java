/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Bytes;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Claims;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.DeviceKeys;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Flow;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Kind;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Scope;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.TrustedKeys;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Consumes the one immutable shared fixture; no reference-generator or product-state imitation. */
class PortalMyCarlosDeviceApprovalCodecUnitTest {
    /**
     * The shared fixture, byte for byte the copy in carlos-portal (tests/fixtures, from commit 42af5d0
     * on): seed texts and public values only, no private key bytes.
     */
    static final String FIXTURE_SHA256 = "b96d88a837cf52869bd73ba1df6fda00f1129a585f3386ab4505fa0d7679de3a";
    static final int FIXTURE_LENGTH = 223909;

    static JsonNode fixture() throws Exception {
        try (InputStream input = PortalMyCarlosDeviceApprovalCodecUnitTest.class.getResourceAsStream(
                "/patientportal/device-approval-contract-v1.json")) {
            if (input == null) {
                throw new IllegalStateException("shared M3 fixture missing");
            }
            byte[] bytes = input.readAllBytes();
            assertEquals(FIXTURE_LENGTH, bytes.length);
            assertEquals(FIXTURE_SHA256, PortalMyCarlosDeviceApprovalCrypto.sha256(bytes));
            return new ObjectMapper().readTree(bytes);
        }
    }

    static Scope scope(JsonNode fixture) {
        return new Scope(fixture.get("clinic_id").textValue(), fixture.get("recipient_id").textValue(),
                fixture.get("public_portal_base_url").textValue());
    }

    static DeviceKeys device(JsonNode key) {
        return new DeviceKeys(key.get("device_key_id").textValue(),
                new Bytes(PortalMyCarlosDeviceApprovalCodec.exact32(key.get("x25519_public_key").textValue())),
                new Bytes(PortalMyCarlosDeviceApprovalCodec.exact32(key.get("ed25519_public_key").textValue())));
    }

    static TrustedKeys trusted(JsonNode fixture, boolean clinicActive) {
        JsonNode clinic = fixture.get("keys").get("clinic");
        var key = new PatientPortalClinicKeysPublishRequest.Key(clinic.get("kid").textValue(),
                clinic.get("spki").textValue(), clinicActive ? "active" : "revoked", null, null);
        return new TrustedKeys(scope(fixture), Map.of(key.kid(), key), device(fixture.get("keys").get("predecessor")));
    }

    static Kind kind(String value) {
        return Kind.valueOf(value.toUpperCase(Locale.ROOT));
    }

    static Map<String, Object> claims(JsonNode object) {
        Map<String, Object> result = new LinkedHashMap<>();
        object.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value.isNull()) {
                result.put(entry.getKey(), null);
            } else if (value.isIntegralNumber()) {
                result.put(entry.getKey(), value.longValue());
            } else if (value.isTextual()) {
                result.put(entry.getKey(), value.textValue());
            } else {
                throw new IllegalArgumentException("unsupported fixture claim");
            }
        });
        return result;
    }

    static Flow flow(JsonNode value) {
        return new Flow(value.get("intent").get("compact").textValue(),
                value.get("challenge").get("compact").textValue(),
                value.has("approval") ? value.get("approval").get("compact").textValue() : null,
                value.get("acceptance").get("compact").textValue());
    }

    @Test
    void consumesAllSevenPrimaryObjectsAndExactJcsProducerPayloads() throws Exception {
        JsonNode fixture = fixture();
        int count = 0;
        for (JsonNode flow : fixture.get("flows")) {
            var items = flow.fields();
            while (items.hasNext()) {
                var entry = items.next();
                Kind kind = kind(entry.getKey());
                JsonNode source = entry.getValue();
                var decoded = PortalMyCarlosDeviceApprovalCodec.decode(
                        kind, source.get("compact").textValue(), scope(fixture));
                assertEquals(claims(source.get("claims")), decoded.claims().values());
                assertArrayEquals(decoded.envelope().payload().copy(),
                        PortalMyCarlosDeviceApprovalCodec.canonicalPayload(kind, decoded.claims().values(), scope(fixture)));
                assertEquals(source.get("compact_sha256").textValue(),
                        PortalMyCarlosDeviceApprovalCrypto.compactHash(decoded.envelope().compact()));
                count++;
            }
        }
        assertEquals(7, count);
    }

    @Test
    void consumesAllFiveManualCodesWithoutTrimmingFoldingOrRemovingHyphens() throws Exception {
        JsonNode fixture = fixture();
        String accepted = fixture.get("keys").get("predecessor").get("device_key_id").textValue();
        int count = 0;
        for (JsonNode vector : fixture.get("manual_code_vectors")) {
            String entered = vector.get("entered").textValue();
            if (vector.get("format_valid").booleanValue()) {
                String parsed = PortalMyCarlosDeviceApprovalCodec.manualPredecessorCode(entered);
                assertEquals(vector.get("expected_parsed").textValue(), parsed);
                assertEquals(entered, parsed);
                assertEquals(vector.get("matches_locally_accepted_predecessor").booleanValue(), accepted.equals(parsed));
            } else {
                assertThrows(IllegalArgumentException.class,
                        () -> PortalMyCarlosDeviceApprovalCodec.manualPredecessorCode(entered), vector.get("id").textValue());
            }
            assertFalse(vector.get("normalization_applied").booleanValue());
            count++;
        }
        assertEquals(5, count);
    }

    @Test
    void consumesAllFiveExactRequestBodyAndLengthFramedHashes() throws Exception {
        int count = 0;
        for (JsonNode vector : fixture().get("request_body_vectors")) {
            byte[] body = HexFormat.of().parseHex(vector.get("body_utf8_hex").textValue());
            String method = vector.get("method").textValue();
            String path = vector.get("raw_path").textValue();
            String query = vector.get("raw_query").textValue();
            String hash = PortalMyCarlosDeviceApprovalCrypto.requestHash(method, path, query, body);
            assertEquals(vector.get("body_sha256").textValue(), PortalMyCarlosDeviceApprovalCrypto.sha256(body));
            assertEquals(vector.get("request_hash").textValue(), hash);
            assertNotEquals(hash, PortalMyCarlosDeviceApprovalCrypto.requestHash(method, path + "/", query, body));
            assertNotEquals(hash, PortalMyCarlosDeviceApprovalCrypto.requestHash(method, path, query + "x", body));
            count++;
        }
        assertEquals(5, count);
        assertFalse(java.util.Arrays.equals(
                PortalMyCarlosDeviceApprovalCrypto.frame(new byte[] {1}, new byte[] {2, 3}),
                PortalMyCarlosDeviceApprovalCrypto.frame(new byte[] {1, 2}, new byte[] {3})));
    }

    @Test
    void byteAndClaimValuesAreDefensiveAndCompareByContent() throws Exception {
        byte[] input = {1, 2, 3};
        Bytes value = new Bytes(input);
        int hash = value.hashCode();
        input[0] = 9;
        value.copy()[1] = 9;
        assertEquals(new Bytes(new byte[] {1, 2, 3}), value);
        assertEquals(hash, value.hashCode());
        Map<String, Object> original = new HashMap<>();
        original.put("nullable", null);
        original.put("text", "saved");
        Claims claims = new Claims(original);
        original.put("text", "changed");
        assertEquals("saved", claims.text("text"));
        assertThrows(UnsupportedOperationException.class, () -> claims.values().put("text", "changed"));
        assertEquals(claims, new Claims(claims.values()));
        TrustedKeys keys = trusted(fixture(), true);
        assertThrows(UnsupportedOperationException.class, () -> keys.clinicKeys().clear());
    }

    @Test
    void rejectsNoncanonicalVersionsUuidsBase64MalformedUtf8AndWrongPrimitiveTypes() throws Exception {
        assertEquals(Long.MAX_VALUE, PortalMyCarlosDeviceApprovalCodec.decimal("9223372036854775807"));
        for (String value : new String[] {"00", "+1", "-1", " 1", "1.0", "9223372036854775808"}) {
            assertThrows(IllegalArgumentException.class, () -> PortalMyCarlosDeviceApprovalCodec.decimal(value));
        }
        JsonNode fixture = fixture();
        String signature = PortalMyCarlosDeviceApprovalCodec.base64(new byte[64]);
        String malformed = PortalMyCarlosDeviceApprovalCodec.base64(new byte[] {(byte) 0xc3, 0x28}) + "." + signature;
        assertThrows(IllegalArgumentException.class,
                () -> PortalMyCarlosDeviceApprovalCodec.decode(Kind.INTENT, malformed, scope(fixture)));
        String payload = new String(PortalMyCarlosDeviceApprovalCodec.envelope(
                flow(fixture.get("flows").get("later")).intent()).payload().copy(), StandardCharsets.UTF_8);
        for (String changed : new String[] {payload.replace("\"v\":1", "\"v\":1.0"),
                payload.replace("1792454390", "true"), payload + " {}",
                payload.replace("80c0c784-e8e2-47b7-9763-d029ff0e6d12", "80C0C784-E8E2-47B7-9763-D029FF0E6D12"),
                payload.replace("FAKE-m3-clinic", "\\ud800")}) {
            String compact = PortalMyCarlosDeviceApprovalCodec.base64(changed.getBytes(StandardCharsets.UTF_8)) + "." + signature;
            assertThrows(IllegalArgumentException.class,
                    () -> PortalMyCarlosDeviceApprovalCodec.decode(Kind.INTENT, compact, scope(fixture)));
        }
        assertThrows(IllegalArgumentException.class,
                () -> PortalMyCarlosDeviceApprovalCodec.envelope("x".repeat(8193)));
        assertEquals("opaque recipient", new Scope("clinic", "opaque recipient", "https://example.invalid").recipientId());
        assertThrows(IllegalArgumentException.class, () -> new Scope("clinic", "é", "https://example.invalid"));
        assertThrows(IllegalArgumentException.class,
                () -> new Scope("clinic", "x".repeat(65), "https://example.invalid"));
    }

    @Test
    void producerEscapesJcsControlsAndUnicodeAndRejectsUnsafeNumericOrSurrogateInput() throws Exception {
        JsonNode fixture = fixture();
        var values = claims(fixture.get("flows").get("first").get("intent").get("claims"));
        String clinic = "é\u000f\n\"\\";
        values.put("clinic_id", clinic);
        Scope scope = new Scope(clinic, fixture.get("recipient_id").textValue(), fixture.get("public_portal_base_url").textValue());
        String json = new String(PortalMyCarlosDeviceApprovalCodec.canonicalPayload(Kind.INTENT, values, scope), StandardCharsets.UTF_8);
        assertTrue(json.contains("é\\u000f\\n\\\"\\\\"));
        values.put("iat", Long.MAX_VALUE - 1);
        values.put("exp", Long.MAX_VALUE);
        assertThrows(IllegalArgumentException.class,
                () -> PortalMyCarlosDeviceApprovalCodec.canonicalPayload(Kind.INTENT, values, scope));
        assertThrows(IllegalArgumentException.class, () -> PortalMyCarlosDeviceApprovalCodec.utf8("\ud800"));
    }
}
