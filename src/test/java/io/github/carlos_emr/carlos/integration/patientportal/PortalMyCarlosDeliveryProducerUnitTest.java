/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Bytes;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.DeviceKeys;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.bouncycastle.crypto.hpke.HPKE;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

@Tag("unit")
class PortalMyCarlosDeliveryProducerUnitTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void shouldMatchExactSharedManifestAadAndPlaintext_whenUsingFrozenSender() throws Exception {
        JsonNode f = fixture();
        var copy = compose(f);
        assertEquals(f.at("/sender/manifest").textValue(), copy.manifest());
        assertEquals(f.at("/sender/manifest_claims/document/sha256").textValue(), copy.documentSha256());
        assertArrayEquals(hex(f.at("/sender/aad_hex")), PortalMyCarlosDeliveryComposer.aad(
                copy.deliveryId(), copy.recipientId(), copy.device().kid()));
        byte[] plaintext = open(copy, f);
        assertEquals(f.at("/sender/plaintext_sha256").textValue(), hash(plaintext));
        List<byte[]> bodyParts = frames(copy.uploadBody().copy());
        assertEquals(2, bodyParts.size());
        assertArrayEquals(copy.sealed().copy(), bodyParts.get(1));
        JsonNode outer = JSON.readTree(bodyParts.getFirst());
        assertEquals(9, outer.size());
        assertEquals(copy.ciphertextSha256(), outer.get("ciphertext_sha256").textValue());
        assertEquals(copy.sealed().size(), outer.get("size").intValue());
        assertEquals(copy.bodySha256(), hash(copy.uploadBody().copy()));
        // Expected outer bytes are built independently of the production encoder and field map.
        String expectedOuter = "{\"ciphertext_sha256\":\"" + copy.ciphertextSha256()
                + "\",\"created_at\":1792450800,\"delivery_id\":\"6f1c2a7e-0b9d-4e53-8a41-2d7c9b0e5f13\","
                + "\"device_key_id\":\"Ebz2sH2jVoqcdFhl1BmIOBRm1xuT-zW5bvFBcCeERPY\",\"expires_at\":1793055600,"
                + "\"recipient_id\":\"rcp-FAKE0001aaaaaaaaaaaa\",\"size\":" + copy.sealed().size()
                + ",\"suite\":{\"aead_id\":3,\"kdf_id\":1,\"kem_id\":32},\"v\":1}";
        assertArrayEquals(expectedOuter.getBytes(StandardCharsets.UTF_8), bodyParts.getFirst());
        assertEquals(JSON.readTree(expectedOuter), outer);

        String outerText = new String(bodyParts.getFirst(), StandardCharsets.UTF_8);
        assertFalse(outerText.contains("FAKE-Jones"));
        assertFalse(outerText.contains("report"));
    }

    @Test
    void shouldMatchRfc9180A21_whenUsingExistingBouncyCastleHpke() throws Exception {
        JsonNode vector = fixture().get("hpke_rfc9180_a2_1");
        HPKE hpke = PortalMyCarlosDeliveryComposer.hpke();
        var recipient = hpke.deriveKeyPair(hex(vector.get("ikmR")));
        var ephemeral = hpke.deriveKeyPair(hex(vector.get("ikmE")));
        var sender = hpke.setupBaseS(recipient.getPublic(), hex(vector.get("info")), ephemeral);
        assertArrayEquals(hex(vector.get("enc")), sender.getEncapsulation());
        var message = vector.at("/encryptions/0");
        byte[] sealed = sender.seal(hex(message.get("aad")), hex(message.get("pt")));
        assertArrayEquals(hex(message.get("ct")), sealed);
        assertArrayEquals(hex(message.get("pt")), hpke.setupBaseR(sender.getEncapsulation(), recipient,
                hex(vector.get("info"))).open(hex(message.get("aad")), sealed));
    }

    @Test
    void shouldUseFreshEphemeralAndDefensiveBytes_whenComposingSameDocumentTwice() throws Exception {
        JsonNode f = fixture();
        var a = compose(f);
        var b = compose(f);
        assertEquals(a.manifest(), b.manifest());
        assertFalse(Arrays.equals(a.sealed().copy(), b.sealed().copy()));
        byte[] original = a.uploadBody().copy();
        byte[] changed = a.uploadBody().copy();
        changed[0] ^= 1;
        assertArrayEquals(original, a.uploadBody().copy());
        assertArrayEquals(open(a, f), open(b, f));
        assertEquals("DeliveryCopy[REDACTED]", a.toString());
    }

    @TestFactory
    List<DynamicTest> shouldCheckOriginalProofAndBindings_whenConsumingSharedReceipts() throws Exception {
        JsonNode f = fixture();
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode vector : f.at("/portal/receipts")) {
            tests.add(DynamicTest.dynamicTest(vector.get("name").textValue(), () -> {
                String compact = vector.get("receipt").textValue();
                var binding = binding(f, f.at("/sender/manifest_claims/delivery_id").textValue());
                // A different valid receipt is cryptographically valid; durable first-proof policy
                // belongs to the ledger, and portal state never substitutes for cryptographic proof.
                boolean authentic = vector.get("expect").textValue().equals("accept")
                        || vector.get("name").textValue().equals("a different receipt for a copy already picked up");
                if (authentic) {
                    var proof = PortalMyCarlosDeliveryReceiptVerifier.verify(compact, binding);
                    assertEquals(compact, proof.compact());
                    assertEquals(1792454400L, proof.pickedUpAt());
                } else {
                    assertThrows(IllegalArgumentException.class,
                            () -> PortalMyCarlosDeliveryReceiptVerifier.verify(compact, binding));
                }
            }));
        }
        assertEquals(13, tests.size());
        return tests;
    }

    @Test
    void shouldRefuseWrongOriginalHashAndWrongAcceptedKey_whenReceiptSignatureIsValid() throws Exception {
        JsonNode f = fixture();
        String receipt = f.at("/portal/receipts/0/receipt").textValue();
        var binding = binding(f, f.at("/sender/manifest_claims/delivery_id").textValue());
        assertThrows(IllegalArgumentException.class, () -> PortalMyCarlosDeliveryReceiptVerifier.verify(receipt,
                new PortalMyCarlosDeliveryReceiptVerifier.Binding(binding.deliveryId(), binding.recipientId(),
                        binding.device(), binding.ciphertextSha256(), "0".repeat(64))));
        assertThrows(IllegalArgumentException.class, () -> PortalMyCarlosDeliveryReceiptVerifier.verify(receipt,
                new PortalMyCarlosDeliveryReceiptVerifier.Binding(binding.deliveryId(), binding.recipientId(),
                        device(f.get("keys").get("other_device")), binding.ciphertextSha256(), binding.documentSha256())));
    }

    @TestFactory
    List<DynamicTest> shouldRefuseAuthenticHostileClaims_whenReceiptSchemaIsInvalid() throws Exception {
        JsonNode f = fixture();
        var binding = binding(f, f.at("/sender/manifest_claims/delivery_id").textValue());
        String original = f.at("/portal/receipts/0/receipt").textValue();
        ObjectNode claims = (ObjectNode) JSON.readTree(PortalMyCarlosDeviceApprovalCodec.envelope(original).payload().copy());
        List<String> invalidPayloads = new ArrayList<>();
        invalidPayloads.add(claims.deepCopy().put("extra", "unexpected").toString());
        invalidPayloads.add(claims.deepCopy().put("picked_up_at", true).toString());
        invalidPayloads.add(claims.deepCopy().put("picked_up_at", 1792454400.0).toString());
        invalidPayloads.add(claims.deepCopy().put("picked_up_at", -1).toString());
        invalidPayloads.add(claims.deepCopy().putNull("picked_up_at").toString());
        invalidPayloads.add(claims.toString().replace("1792454400", "9223372036854775808"));
        invalidPayloads.add(claims.toString() + " {}");
        invalidPayloads.add("{\"aud\":\"carlos-delivery-receipt\"," + claims.toString().substring(1));
        invalidPayloads.add(claims.deepCopy().put("vault_head_link", "A".repeat(64)).toString());
        invalidPayloads.add(claims.deepCopy().put("jti", "not-a-uuid").toString());
        List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < invalidPayloads.size(); i++) {
            String compact = signDevicePayload(invalidPayloads.get(i).getBytes(StandardCharsets.UTF_8), f);
            tests.add(DynamicTest.dynamicTest("authentic hostile receipt " + i, () -> assertThrows(
                    IllegalArgumentException.class, () -> PortalMyCarlosDeliveryReceiptVerifier.verify(compact, binding))));
        }
        return tests;
    }

    @Test
    void shouldAcceptExactNoncanonicalJsonBytes_whenSignatureAndClaimsAreValid() throws Exception {
        JsonNode f = fixture();
        String original = f.at("/portal/receipts/0/receipt").textValue();
        byte[] exact = (" \n" + new String(PortalMyCarlosDeviceApprovalCodec.envelope(original).payload().copy(),
                StandardCharsets.UTF_8) + "\n").getBytes(StandardCharsets.UTF_8);
        String compact = signDevicePayload(exact, f);
        assertEquals(compact, PortalMyCarlosDeliveryReceiptVerifier.verify(compact,
                binding(f, f.at("/sender/manifest_claims/delivery_id").textValue())).compact());
    }

    @Test
    void shouldRefuseInvalidIdsLifetimeOversizeAndSigner_whenPreparingCopy() throws Exception {
        JsonNode f = fixture();
        var copy = compose(f);
        var signer = signer(f);
        PrivateKey key = clinicPrivate(f);
        for (String id : List.of(copy.deliveryId().toUpperCase(), "00000000-0000-1000-8000-000000000000", "bad")) {
            assertThrows(IllegalArgumentException.class, () -> PortalMyCarlosDeliveryComposer.compose(id,
                    copy.recipientId(), copy.device(), document(), copy.originalPdf().copy(), copy.createdAt(),
                    copy.expiresAt(), signer, key));
        }
        assertThrows(IllegalArgumentException.class, () -> PortalMyCarlosDeliveryComposer.compose(copy.deliveryId(),
                copy.recipientId(), copy.device(), document(), new byte[PortalMyCarlosDeliveryCodec.MAX_SEALED_BYTES],
                copy.createdAt(), copy.expiresAt(), signer, key));
        assertThrows(IllegalArgumentException.class, () -> PortalMyCarlosDeliveryComposer.compose(copy.deliveryId(),
                copy.recipientId(), copy.device(), document(), copy.originalPdf().copy(), 0, 604801, signer, key));
        var wrong = f.at("/keys/clinic_b");
        assertThrows(IllegalArgumentException.class, () -> PortalMyCarlosDeliveryComposer.compose(copy.deliveryId(),
                copy.recipientId(), copy.device(), document(), copy.originalPdf().copy(), copy.createdAt(), copy.expiresAt(),
                new PatientPortalClinicKeysPublishRequest.Key(wrong.get("kid").textValue(), wrong.get("spki").textValue(),
                        "active", null, null), key));
    }

    @Test
    void shouldEnforceExactSealedCap_whenPlaintextAndManifestOverheadReachBoundary() throws Exception {
        JsonNode f = fixture();
        var small = compose(f);
        byte[] payload = PortalMyCarlosDeviceApprovalCodec.envelope(small.manifest()).payload().copy();
        String largeClaims = new String(payload, StandardCharsets.UTF_8).replace("\"size\":193", "\"size\":20000000");
        int manifestLength = (largeClaims.getBytes(StandardCharsets.UTF_8).length * 4 + 2) / 3 + 1 + 86;
        int pdfLength = PortalMyCarlosDeliveryCodec.MAX_SEALED_BYTES - 48 - 24
                - "mycarlos-delivery/1".length() - manifestLength;
        byte[] pdf = new byte[pdfLength];
        var largest = PortalMyCarlosDeliveryComposer.compose(small.deliveryId(), small.recipientId(), small.device(),
                document(), pdf, small.createdAt(), small.expiresAt(), signer(f), clinicPrivate(f));
        assertEquals(PortalMyCarlosDeliveryCodec.MAX_SEALED_BYTES, largest.sealed().size());
        PrivateKey key = clinicPrivate(f);
        assertThrows(IllegalArgumentException.class, () -> PortalMyCarlosDeliveryComposer.compose(small.deliveryId(),
                small.recipientId(), small.device(), document(), new byte[pdfLength + 1], small.createdAt(),
                small.expiresAt(), signer(f), key));
    }

    @Test
    void shouldPreserveUnicodeJcsAndRefuseUnpairedSurrogates_whenEncodingManifestValues() {
        assertEquals("{\"a\":\"é😀\\u000f\\n\",\"z\":1}", new String(PortalMyCarlosDeliveryCodec.canonical(
                Map.of("z", 1L, "a", "é😀\u000f\n")), StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class,
                () -> PortalMyCarlosDeliveryCodec.canonical(Map.of("a", "\ud800")));
        assertThrows(IllegalArgumentException.class,
                () -> PortalMyCarlosDeliveryCodec.canonical(Map.of("a", "\udc00")));
    }

    @Test
    void shouldRejectMalformedKnownDatesAndLabelsThatNativeM0Refuses_beforeCreatingAnyDeliveryCopy() {
        for (String invalidDate : new String[] {"2026-2-03", "2026-02-29", "2026-04-31", "0000-01-01", "2026-10-26T00:00:00Z"}) {
            assertThrows(IllegalArgumentException.class, () -> new PortalMyCarlosDeliveryComposer.Document("clinic-a", "FAKE Clinic", "FAKE Provider",
                    "FAKE Patient", "1990-01-01", "FAKE document", "document", invalidDate));
            assertThrows(IllegalArgumentException.class, () -> new PortalMyCarlosDeliveryComposer.Document("clinic-a", "FAKE Clinic", "FAKE Provider",
                    "FAKE Patient", invalidDate, "FAKE document", "document", "2026-10-17"));
        }
        for (String invalidLabel : new String[] {"", "FAKE\nClinic", "FAKE\u0085Clinic", "x".repeat(241)}) {
            assertThrows(IllegalArgumentException.class, () -> new PortalMyCarlosDeliveryComposer.Document("clinic-a", invalidLabel, "FAKE Provider",
                    "FAKE Patient", "1990-01-01", "FAKE document", "document", "2026-10-17"));
        }
        assertDoesNotThrow(() -> new PortalMyCarlosDeliveryComposer.Document("clinic-a", "😀".repeat(240), "FAKE Provider",
                "FAKE Patient", "2000-02-29", "FAKE document", "document", "2024-02-29"));
    }

    @Test
    void shouldEncodeExplicitUnknownDateAsEmptyString_inActualSignedDeliveryManifest() throws Exception {
        var f = fixture();
        var expected = compose(f);
        var known = document();
        var unknown = new PortalMyCarlosDeliveryComposer.Document(known.clinicId(), known.clinicName(), known.providerName(),
                known.patientName(), known.birthDate(), known.title(), known.type(), "");
        var copy = PortalMyCarlosDeliveryComposer.compose(expected.deliveryId(), expected.recipientId(), expected.device(), unknown,
                expected.originalPdf().copy(), expected.createdAt(), expected.expiresAt(), signer(f), clinicPrivate(f));
        var actual = JSON.readTree(PortalMyCarlosDeviceApprovalCodec.envelope(copy.manifest()).payload().copy());
        assertTrue(actual.at("/document/service_date").isTextual());
        assertEquals("", actual.at("/document/service_date").textValue());
        assertEquals("1985-06-15", actual.at("/patient/birth_date").textValue());
        assertThrows(IllegalArgumentException.class, () -> new PortalMyCarlosDeliveryComposer.Document("clinic-a", "FAKE Clinic", "FAKE Provider",
                "FAKE Patient", "", "FAKE document", "document", ""));
    }

    static PortalMyCarlosDeliveryComposer.Copy compose(JsonNode f) throws Exception {
        var c = f.at("/sender/manifest_claims");
        return PortalMyCarlosDeliveryComposer.compose(c.get("delivery_id").textValue(),
                c.get("recipient_id").textValue(), device(f.at("/keys/device")), document(),
                PortalMyCarlosDeviceApprovalCodec.base64(f.at("/sender/document_b64url").textValue(), 2048),
                c.get("created_at").longValue(), c.get("expires_at").longValue(), signer(f), clinicPrivate(f));
    }

    private static PortalMyCarlosDeliveryComposer.Document document() {
        return new PortalMyCarlosDeliveryComposer.Document("clinic-a", "FAKE Demo Clinic", "FAKE-Provider, Demo",
                "FAKE-Jones, FAKE-Jacky", "1985-06-15", "FAKE chest X-ray report", "imaging_report", "2026-10-18");
    }

    private static PatientPortalClinicKeysPublishRequest.Key signer(JsonNode f) {
        var c = f.at("/keys/clinic_a");
        return new PatientPortalClinicKeysPublishRequest.Key(c.get("kid").textValue(), c.get("spki").textValue(),
                "active", null, null);
    }

    private static PrivateKey clinicPrivate(JsonNode f) throws Exception {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec curve = parameters.getParameterSpec(ECParameterSpec.class);
        BigInteger scalar = new BigInteger(1, seed(f.at("/keys/clinic_a/seed_text").textValue()))
                .mod(curve.getOrder().subtract(BigInteger.ONE)).add(BigInteger.ONE);
        return KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(scalar, curve));
    }

    private static DeviceKeys device(JsonNode key) {
        return new DeviceKeys(key.get("device_key_id").textValue(),
                new Bytes(PortalMyCarlosDeviceApprovalCodec.exact32(key.get("x25519_public_key").textValue())),
                new Bytes(PortalMyCarlosDeviceApprovalCodec.exact32(key.get("ed25519_public_key").textValue())));
    }

    private static PortalMyCarlosDeliveryReceiptVerifier.Binding binding(JsonNode f, String deliveryId) {
        return new PortalMyCarlosDeliveryReceiptVerifier.Binding(deliveryId,
                f.at("/sender/manifest_claims/recipient_id").textValue(), device(f.at("/keys/device")),
                f.at("/packages/accept/0/outer/ciphertext_sha256").textValue(),
                f.at("/sender/manifest_claims/document/sha256").textValue());
    }

    private static byte[] open(PortalMyCarlosDeliveryComposer.Copy copy, JsonNode f) throws Exception {
        byte[] privateBytes = seed(f.at("/keys/device/x25519_seed_text").textValue());
        var privateKey = new X25519PrivateKeyParameters(privateBytes);
        var keyPair = new AsymmetricCipherKeyPair(new X25519PublicKeyParameters(copy.device().x25519().copy()), privateKey);
        byte[] sealed = copy.sealed().copy();
        return PortalMyCarlosDeliveryComposer.hpke().setupBaseR(Arrays.copyOf(sealed, 32), keyPair,
                "carlos-emr/mycarlos-delivery/v1".getBytes(StandardCharsets.US_ASCII))
                .open(PortalMyCarlosDeliveryComposer.aad(copy.deliveryId(), copy.recipientId(), copy.device().kid()),
                        Arrays.copyOfRange(sealed, 32, sealed.length));
    }

    static String signDevicePayload(byte[] exact, JsonNode f) throws Exception {
        byte[] prefix = HexFormat.of().parseHex("302e020100300506032b657004220420");
        byte[] secret = seed(f.at("/keys/device/ed25519_seed_text").textValue());
        PrivateKey key = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(
                ByteBuffer.allocate(prefix.length + secret.length).put(prefix).put(secret).array()));
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(key);
        signature.update(exact);
        return PortalMyCarlosDeviceApprovalCodec.base64(exact) + "."
                + PortalMyCarlosDeviceApprovalCodec.base64(signature.sign());
    }

    private static List<byte[]> frames(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        List<byte[]> frames = new ArrayList<>();
        while (buffer.hasRemaining()) {
            int size = Math.toIntExact(buffer.getLong());
            byte[] value = new byte[size];
            buffer.get(value);
            frames.add(value);
        }
        return frames;
    }

    private static byte[] seed(String text) throws Exception { return MessageDigest.getInstance("SHA-256")
            .digest(text.getBytes(StandardCharsets.UTF_8)); }
    private static String hash(byte[] bytes) { return PortalMyCarlosDeviceApprovalCrypto.sha256(bytes); }
    private static byte[] hex(JsonNode node) { return HexFormat.of().parseHex(node.textValue()); }
    static JsonNode fixture() throws Exception {
        try (var stream = PortalMyCarlosDeliveryProducerUnitTest.class.getResourceAsStream("/patientportal/delivery-contract.json")) {
            return JSON.readTree(stream);
        }
    }
}
