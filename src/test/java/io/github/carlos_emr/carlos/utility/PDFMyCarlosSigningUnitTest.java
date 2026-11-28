/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.utility;

import static org.junit.jupiter.api.Assertions.*;

import io.github.carlos_emr.carlos.test.util.PdfSigningTestSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class PDFMyCarlosSigningUnitTest {
    @TempDir Path directory;

    @Test void shouldSignExactOriginalPayload_whenConfiguredP256IdentityMatches() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        var pair = generator.generateKeyPair();
        var certificate = PdfSigningTestSupport.createSelfSignedCertificate(pair, "SHA256withECDSA");
        var config = PdfSigningTestSupport.enabledConfig(directory.resolve("FAKE-p256.p12"), pair.getPrivate(), certificate);
        byte[] payload = "{\"typ\":\"FAKE-mycarlos-approval\"}".getBytes(StandardCharsets.UTF_8);
        byte[] signature = PDFSigningUtil.signMyCarlosPayload(payload, PDFSigningUtil.keyId(certificate), config);
        assertEquals(64, signature.length);
        var verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(pair.getPublic());
        verifier.update(payload);
        assertTrue(verifier.verify(signature));
        verifier.initVerify(pair.getPublic());
        verifier.update("different original bytes".getBytes(StandardCharsets.UTF_8));
        assertFalse(verifier.verify(signature));
    }
    @Test void shouldRefuseChangedSigner_whenExpectedPublicIdentityDiffers() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        var pair = generator.generateKeyPair();
        var config = PdfSigningTestSupport.enabledConfig(directory.resolve("FAKE-changed.p12"), pair.getPrivate(),
                PdfSigningTestSupport.createSelfSignedCertificate(pair, "SHA256withECDSA"));
        assertThrows(java.io.IOException.class, () -> PDFSigningUtil.signMyCarlosPayload(new byte[]{1}, "different", config));
    }
    @Test void shouldRefuseRsaForApproval_whenGeneralPdfSigningSupportsRsa() throws Exception {
        var config = PdfSigningTestSupport.enabledRsaConfig(directory.resolve("FAKE-rsa.p12"));
        var certificate = PDFSigningUtil.currentSigningCertificate(config);
        assertThrows(java.io.IOException.class, () -> PDFSigningUtil.signMyCarlosPayload(new byte[]{1}, PDFSigningUtil.keyId(certificate), config));
    }
}
