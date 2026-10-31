/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalCodecUnitTest.claims;
import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalCodecUnitTest.device;
import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalCodecUnitTest.fixture;
import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalCodecUnitTest.flow;
import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalCodecUnitTest.kind;
import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalCodecUnitTest.scope;
import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalCodecUnitTest.trusted;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Flow;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Kind;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.TrustedKeys;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.EdECPrivateKey;
import java.security.interfaces.XECPrivateKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class PortalMyCarlosDeviceApprovalCryptoUnitTest {
    @TestFactory
    List<DynamicTest> consumesAll116SignedVectorsWithPhysicalAndSemanticOutcomesSeparated() throws Exception {
        JsonNode fixture = fixture();
        List<DynamicTest> tests = new ArrayList<>();
        int valid = 0;
        int authenticHostile = 0;
        int badOrDer = 0;
        int noncanonical = 0;
        for (JsonNode vector : fixture.get("signed_object_vectors")) {
            String id = vector.get("id").textValue();
            boolean badSignature = id.endsWith("_bad_signature") || id.endsWith("_der_not_p1363")
                    || id.equals("portal_only_rogue_signature_does_not_authorize_predecessor");
            boolean badEncoding = id.endsWith("_noncanonical_b64_padding");
            if (vector.get("valid").booleanValue()) {
                valid++;
            } else if (badEncoding) {
                noncanonical++;
            } else if (badSignature) {
                badOrDer++;
            } else {
                authenticHostile++;
            }
            tests.add(DynamicTest.dynamicTest(id, () -> {
                Kind kind = kind(vector.get("kind").textValue());
                String compact = vector.get("compact").textValue();
                assertEquals(vector.get("compact_sha256").textValue(),
                        PortalMyCarlosDeviceApprovalCrypto.sha256(compact.getBytes(StandardCharsets.US_ASCII)));
                PublicKey physicallyExpectedKey = signingPublicKey(fixture, kind, id.startsWith("first_"));
                if (badSignature || badEncoding) {
                    assertThrows(IllegalArgumentException.class,
                            () -> PortalMyCarlosDeviceApprovalCrypto.verifyPhysicalSignature(kind, compact, physicallyExpectedKey));
                } else {
                    // Includes all 75 genuinely signed semantic-invalid hostile inputs.
                    assertDoesNotThrow(() -> PortalMyCarlosDeviceApprovalCrypto.verifyPhysicalSignature(
                            kind, compact, physicallyExpectedKey));
                }
                TrustedKeys context = trusted(fixture, "active".equals(vector.get("clinic_key_status").textValue()));
                if (vector.get("valid").booleanValue()) {
                    var verified = PortalMyCarlosDeviceApprovalCrypto.verify(kind, compact, context);
                    PortalMyCarlosDeviceApprovalCodec.requireFreshForNewAction(verified, vector.get("now").longValue());
                    assertArrayEquals(PortalMyCarlosDeviceApprovalCodec.envelope(compact).payload().copy(),
                            verified.envelope().payload().copy());
                } else {
                    assertThrows(IllegalArgumentException.class, () -> {
                        var verified = PortalMyCarlosDeviceApprovalCrypto.verify(kind, compact, context);
                        PortalMyCarlosDeviceApprovalCodec.requireFreshForNewAction(verified, vector.get("now").longValue());
                    });
                }
            }));
        }
        assertEquals(116, tests.size());
        assertEquals(22, valid);
        assertEquals(75, authenticHostile);
        assertEquals(12, badOrDer);
        assertEquals(7, noncanonical);
        return tests;
    }

    @Test
    void verifiesAllSevenPrimaryObjectsAndBothCompleteHistoricalFlows() throws Exception {
        JsonNode fixture = fixture();
        TrustedKeys context = trusted(fixture, true);
        int count = 0;
        for (JsonNode source : fixture.get("flows")) {
            var verified = PortalMyCarlosDeviceApprovalCrypto.verifyFlow(flow(source), context);
            count += verified.approval() == null ? 3 : 4;
            assertEquals(source.get("challenge").get("claims").get("challenge_id").textValue(),
                    verified.challenge().claims().text("challenge_id"));
        }
        assertEquals(7, count);
    }

    @Test
    void consumesBothAuthenticCrossFlowVariantsAndRejectsTheirExactHashMismatch() throws Exception {
        JsonNode fixture = fixture();
        TrustedKeys context = trusted(fixture, true);
        int count = 0;
        for (JsonNode vector : fixture.get("flow_variants")) {
            JsonNode items = vector.get("flow");
            var fields = items.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                assertDoesNotThrow(() -> PortalMyCarlosDeviceApprovalCrypto.verify(kind(entry.getKey()),
                        entry.getValue().get("compact").textValue(), context));
            }
            assertEquals("invalid_proof", vector.get("expected").textValue());
            assertThrows(IllegalArgumentException.class,
                    () -> PortalMyCarlosDeviceApprovalCrypto.verifyFlow(flow(items), context));
            count++;
        }
        assertEquals(2, count);
    }

    @Test
    void usesStandardJcaFakePkcs8KeysToSignEachFrozenKindWithoutKeyLoadingOrTrustPromotion() throws Exception {
        JsonNode fixture = fixture();
        TrustedKeys context = trusted(fixture, true);
        int count = 0;
        var flows = fixture.get("flows").fields();
        while (flows.hasNext()) {
            var item = flows.next();
            boolean first = item.getKey().equals("first");
            var fields = item.getValue().fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                Kind kind = kind(entry.getKey());
                JsonNode keys = fixture.get("keys");
                String signerName = kind == Kind.INTENT ? (first ? "first" : "candidate")
                        : kind == Kind.APPROVAL ? "predecessor" : "clinic";
                // Derived at run time from the key's seed text; the fixture holds no private key.
                String field = kind.deviceSignature ? "FAKE_ed25519_private_pkcs8_b64url" : "FAKE_private_pkcs8_b64url";
                PrivateKey privateKey = PortalMyCarlosDeviceApprovalStateTestSupport.privateKey(
                        keys.get(signerName), field, kind.deviceSignature ? "Ed25519" : "EC");
                String signed = PortalMyCarlosDeviceApprovalCrypto.sign(
                        kind, claims(entry.getValue().get("claims")), scope(fixture), privateKey);
                var verified = PortalMyCarlosDeviceApprovalCrypto.verify(kind, signed, context);
                assertArrayEquals(PortalMyCarlosDeviceApprovalCodec.envelope(
                        entry.getValue().get("compact").textValue()).payload().copy(), verified.envelope().payload().copy());
                if (kind.deviceSignature) {
                    // Standard Ed25519 is deterministic; P-256 high/low-S remain equally valid.
                    assertEquals(entry.getValue().get("compact").textValue(), signed);
                }
                count++;
            }
        }
        assertEquals(7, count);
    }

    @Test
    void shouldHoldNoPrivateKey_andRebuildEveryPublicKeyFromItsSeedText() throws Exception {
        String raw;
        try (var input = getClass().getResourceAsStream("/patientportal/device-approval-contract-v1.json")) {
            raw = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        // Seed texts and public values only: no private key field and no PKCS#8 key header.
        assertFalse(raw.toLowerCase(Locale.ROOT).contains("private"));
        assertFalse(raw.contains("MC4CAQAw") || raw.contains("MIGHAgEA"));
        int rebuilt = 0;
        var keys = fixture().get("keys").fields();
        while (keys.hasNext()) {
            var entry = keys.next();
            JsonNode key = entry.getValue();
            if (entry.getKey().equals("clinic")) {
                byte[] spki = Base64.getUrlDecoder().decode(key.get("spki").textValue());
                assertEquals(key.get("kid").textValue(),
                        PortalMyCarlosDeviceApprovalCodec.base64(MessageDigest.getInstance("SHA-256").digest(spki)));
                // The derived P-256 key is the private half of the fixture's public key.
                PrivateKey clinic = PortalMyCarlosDeviceApprovalStateTestSupport.privateKey(key, "FAKE_private_pkcs8_b64url", "EC");
                byte[] message = "FAKE clinic key pair check".getBytes(StandardCharsets.US_ASCII);
                Signature signer = Signature.getInstance("SHA256withECDSA");
                signer.initSign(clinic);
                signer.update(message);
                Signature verifier = Signature.getInstance("SHA256withECDSA");
                verifier.initVerify(KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(spki)));
                verifier.update(message);
                assertTrue(verifier.verify(signer.sign()), "clinic");
            } else {
                byte[] edSeed = PortalMyCarlosDeviceApprovalStateTestSupport.seedBytes(key.get("ed25519_seed_text").textValue());
                byte[] xSeed = PortalMyCarlosDeviceApprovalStateTestSupport.seedBytes(key.get("x25519_seed_text").textValue());
                byte[] ed = new Ed25519PrivateKeyParameters(edSeed, 0).generatePublicKey().getEncoded();
                byte[] x = new X25519PrivateKeyParameters(xSeed, 0).generatePublicKey().getEncoded();
                assertEquals(key.get("ed25519_public_key").textValue(), PortalMyCarlosDeviceApprovalCodec.base64(ed), entry.getKey());
                assertEquals(key.get("x25519_public_key").textValue(), PortalMyCarlosDeviceApprovalCodec.base64(x), entry.getKey());
                assertEquals(key.get("device_key_id").textValue(), PortalMyCarlosDeviceApprovalCrypto.deviceKeyId(x, ed), entry.getKey());
                // The JCA keys the tests sign with hold exactly those bytes.
                var jcaEd = (EdECPrivateKey) PortalMyCarlosDeviceApprovalStateTestSupport.privateKey(key,
                        "FAKE_ed25519_private_pkcs8_b64url", "Ed25519");
                var jcaX = (XECPrivateKey) PortalMyCarlosDeviceApprovalStateTestSupport.privateKey(key,
                        "FAKE_x25519_private_pkcs8_b64url", "X25519");
                assertArrayEquals(edSeed, jcaEd.getBytes().orElseThrow(), entry.getKey());
                assertArrayEquals(xSeed, jcaX.getScalar().orElseThrow(), entry.getKey());
            }
            rebuilt++;
        }
        assertEquals(5, rebuilt);
    }

    @Test
    void historicCryptoRemainsValidAfterExpiryWhileFreshActionRejectsAtExactExpiry() throws Exception {
        JsonNode fixture = fixture();
        Flow flow = flow(fixture.get("flows").get("later"));
        TrustedKeys context = trusted(fixture, true);
        var historical = PortalMyCarlosDeviceApprovalCrypto.verifyFlow(flow, context);
        long expiry = historical.challenge().claims().epoch("expires_at");
        PortalMyCarlosDeviceApprovalCodec.requireFreshForNewAction(historical.intent(), expiry - 1);
        PortalMyCarlosDeviceApprovalCodec.requireFreshForNewAction(historical.challenge(), expiry - 1);
        assertThrows(IllegalArgumentException.class,
                () -> PortalMyCarlosDeviceApprovalCodec.requireFreshForNewAction(historical.intent(), expiry));
        assertThrows(IllegalArgumentException.class,
                () -> PortalMyCarlosDeviceApprovalCodec.requireFreshForNewAction(historical.challenge(), expiry));
        assertDoesNotThrow(() -> PortalMyCarlosDeviceApprovalCrypto.verifyFlow(flow, context));
        // This supplies historical proof only; consumed-idempotency/removal decisions belong to
        // the later authorized local state service, never to this codec or an acceptance receipt.
    }

    @Test
    void rogueSignedPortalClaimsCannotSubstituteForLocallySelectedPredecessor() throws Exception {
        JsonNode fixture = fixture();
        String rogueProof = null;
        for (JsonNode vector : fixture.get("signed_object_vectors")) {
            if (vector.get("id").textValue().equals("portal_only_rogue_signature_does_not_authorize_predecessor")) {
                rogueProof = vector.get("compact").textValue();
            }
        }
        String compact = java.util.Objects.requireNonNull(rogueProof);
        assertDoesNotThrow(() -> PortalMyCarlosDeviceApprovalCrypto.verifyPhysicalSignature(Kind.APPROVAL, compact,
                PortalMyCarlosDeviceApprovalCrypto.ed25519PublicKey(device(fixture.get("keys").get("rogue")).ed25519().copy())));
        assertThrows(IllegalArgumentException.class,
                () -> PortalMyCarlosDeviceApprovalCrypto.verify(Kind.APPROVAL, compact, trusted(fixture, true)));
        var noLocalPredecessor = new TrustedKeys(scope(fixture), trusted(fixture, true).clinicKeys(), null);
        assertThrows(IllegalArgumentException.class, () -> PortalMyCarlosDeviceApprovalCrypto.verify(
                Kind.APPROVAL, flow(fixture.get("flows").get("later")).approval(), noLocalPredecessor));
    }

    @Test
    void recomputesM0DeviceKeyIdsForEverySharedFakeDeviceWithoutChangingFraming() throws Exception {
        JsonNode fixture = fixture();
        int count = 0;
        for (String name : new String[] {"candidate", "first", "predecessor", "rogue"}) {
            var key = device(fixture.get("keys").get(name));
            assertEquals(key.kid(), PortalMyCarlosDeviceApprovalCrypto.deviceKeyId(key.x25519().copy(), key.ed25519().copy()));
            assertNotEquals(key.kid(), PortalMyCarlosDeviceApprovalCrypto.deviceKeyId(key.ed25519().copy(), key.x25519().copy()));
            count++;
        }
        assertEquals(4, count);
        assertNull(flow(fixture.get("flows").get("first")).approval());
    }

    private static PublicKey signingPublicKey(JsonNode fixture, Kind kind, boolean first) {
        if (kind == Kind.CHALLENGE || kind == Kind.ACCEPTANCE) {
            return PortalMyCarlosDeviceApprovalCrypto.clinicPublicKey(
                    trusted(fixture, true).clinicKeys().values().iterator().next());
        }
        String name = kind == Kind.APPROVAL ? "predecessor" : first ? "first" : "candidate";
        return PortalMyCarlosDeviceApprovalCrypto.ed25519PublicKey(device(fixture.get("keys").get(name)).ed25519().copy());
    }
}
