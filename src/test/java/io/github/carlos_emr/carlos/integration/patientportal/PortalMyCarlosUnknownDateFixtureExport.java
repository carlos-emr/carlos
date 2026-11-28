/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Bytes;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.DeviceKeys;
import io.github.carlos_emr.carlos.test.util.PdfSigningTestSupport;
import io.github.carlos_emr.carlos.utility.DocumentManifest;
import io.github.carlos_emr.carlos.utility.PDFSigningUtil;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import org.apache.pdfbox.Loader;

/** Manual export of public FAKE interoperability packets; never contacts a server or vault. */
public final class PortalMyCarlosUnknownDateFixtureExport {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String FAKE_SEED = "FAKE delivery contract test key: clinic-a signing key 1";

    private PortalMyCarlosUnknownDateFixtureExport() {}

    /** Arguments: a new output directory, then canonical or legacy. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2 || !(arguments[1].equals("canonical") || arguments[1].equals("legacy"))) {
            throw new IllegalArgumentException("Use: <new-output-directory> <canonical|legacy>");
        }
        boolean legacy = arguments[1].equals("legacy");
        JsonNode contract = resource("delivery-contract.json");
        JsonNode signed = resource("mycarlos-signed-pdf-fixtures.json");
        JsonNode embedded = JSON.readTree(Base64.getDecoder().decode(
                signed.at("/documents/0/manifest_bytes_b64").textValue()));
        JsonNode signer = contract.at("/keys/clinic_a");
        if (!FAKE_SEED.equals(signer.get("seed_text").textValue())) {
            throw new IllegalStateException("Expected the shared public FAKE signing fixture");
        }
        var parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec curve = parameters.getParameterSpec(ECParameterSpec.class);
        byte[] seed = MessageDigest.getInstance("SHA-256").digest(FAKE_SEED.getBytes(StandardCharsets.UTF_8));
        BigInteger scalar = new BigInteger(1, seed).mod(curve.getOrder().subtract(BigInteger.ONE)).add(BigInteger.ONE);
        var factory = KeyFactory.getInstance("EC");
        var privateKey = factory.generatePrivate(new ECPrivateKeySpec(scalar, curve));
        var identity = new PatientPortalClinicKeysPublishRequest.Key(signer.get("kid").textValue(),
                signer.get("spki").textValue(), "active", null, null);
        var pair = new KeyPair(factory.generatePublic(new X509EncodedKeySpec(
                Base64.getUrlDecoder().decode(identity.spki()))), privateKey);
        JsonNode device = contract.at("/keys/device");
        var keys = new DeviceKeys(device.get("device_key_id").textValue(),
                new Bytes(PortalMyCarlosDeviceApprovalCodec.exact32(device.get("x25519_public_key").textValue())),
                new Bytes(PortalMyCarlosDeviceApprovalCodec.exact32(device.get("ed25519_public_key").textValue())));
        Path destination = Files.createDirectory(Path.of(arguments[0]));
        Path fakeKeystore = destination.resolve("FAKE-signing.p12");
        Path signedPath = null;
        try {
            var certificate = PdfSigningTestSupport.createSelfSignedCertificate(pair, "SHA256withECDSA");
            var config = PdfSigningTestSupport.enabledConfig(fakeKeystore, privateKey, certificate);
            var original = new DocumentManifest(embedded.get("document_id").textValue(), 1, 1792450800L,
                    embedded.get("clinic_id").textValue(), embedded.get("clinic_name").textValue(),
                    embedded.get("provider_name").textValue(), embedded.get("recipient_id").textValue(),
                    embedded.at("/patient/name").textValue(), embedded.at("/patient/birth_date").textValue(),
                    embedded.at("/document/title").textValue(), embedded.at("/document/type").textValue(), legacy ? "" : null);
            byte[] manifest = original.toCanonicalJson(identity.kid());
            Path rendered = PdfSigningTestSupport.writeSinglePagePdf(destination.resolve("FAKE-source.pdf"));
            signedPath = PDFSigningUtil.signPDF(rendered, config, null, original);
            byte[] pdf = Files.readAllBytes(signedPath);
            PortalMyCarlosSignedOriginal.verify(pdf, original, identity);
            var document = PortalMyCarlosDeliveryApplicationService.deliveryDocument(original);
            String id = legacy ? "6f1c2a7e-0b9d-4e53-8a41-2d7c9b0e5f15" : "6f1c2a7e-0b9d-4e53-8a41-2d7c9b0e5f14";
            var copy = PortalMyCarlosDeliveryComposer.composeWithSigner(id, original.recipientId(), keys, document,
                    pdf, 1792450800L, 1793055600L, identity,
                    (payload, kid) -> PDFSigningUtil.signMyCarlosPayload(payload, kid, config));
            writePacket(destination, legacy, contract, device, manifest, pdf, copy);
            System.out.println("FAKE fixture exported; original SHA256=" + copy.documentSha256());
        } finally {
            Files.deleteIfExists(fakeKeystore);
            if (signedPath != null) Files.deleteIfExists(signedPath);
        }
    }

    private static JsonNode resource(String name) throws Exception {
        try (var input = PortalMyCarlosUnknownDateFixtureExport.class.getResourceAsStream("/patientportal/" + name)) {
            if (input == null) throw new IllegalStateException("Shared FAKE test resource is missing");
            return JSON.readTree(input);
        }
    }

    private static void writePacket(Path destination, boolean legacy, JsonNode contract, JsonNode device,
            byte[] manifest, byte[] pdf, PortalMyCarlosDeliveryComposer.Copy copy) throws Exception {
        Files.write(destination.resolve("upload.bin"), copy.uploadBody().copy());
        Files.write(destination.resolve("sealed.bin"), copy.sealed().copy());
        Files.write(destination.resolve("signed-original.pdf"), pdf);
        var buffer = ByteBuffer.wrap(copy.uploadBody().copy());
        byte[] outer = new byte[Math.toIntExact(buffer.getLong())];
        buffer.get(outer);
        ObjectNode packet = JSON.createObjectNode();
        packet.put("description", "FAKE actual Java PAdES + configured P256 signer + HPKE; no approval/network/custody claim");
        packet.put("pdf_date_encoding", legacy ? "legacy-empty-string" : "canonical-null");
        packet.set("outer", JSON.readTree(outer));
        packet.put("blob", Base64.getUrlEncoder().withoutPadding().encodeToString(copy.sealed().copy()));
        packet.put("now", 1792454400L);
        packet.put("expected_document_sha256", copy.documentSha256());
        packet.put("expected_body_sha256", copy.bodySha256());
        packet.put("pdf_base64", Base64.getEncoder().encodeToString(pdf));
        packet.put("embedded_manifest_base64", Base64.getEncoder().encodeToString(manifest));
        try (var document = Loader.loadPDF(pdf)) {
            packet.set("byte_ranges", JSON.valueToTree(document.getSignatureDictionaries().getFirst().getByteRange()));
        }
        packet.set("embedded_manifest", JSON.readTree(manifest));
        packet.set("device_public", ((ObjectNode) device.deepCopy()).without(List.of("x25519_seed_text", "ed25519_seed_text")));
        packet.set("receiver", contract.get("receiver"));
        Files.writeString(destination.resolve("package.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(packet) + "\n");
    }
}
