/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.utility;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SignatureException;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.ess.ESSCertIDv2;
import org.bouncycastle.asn1.ess.SigningCertificateV2;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import io.github.carlos_emr.carlos.test.util.PdfSigningTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

@Tag("unit")
@Tag("fast")
@Tag("pdf")
@DisplayName("PDFSigningUtil")
class PDFSigningUtilUnitTest {
    @TempDir
    Path tempDir;

    private final List<Path> generatedOutputs = new ArrayList<>();

    @AfterEach
    void cleanUp() throws IOException {
        for (Path output : generatedOutputs) {
            Files.deleteIfExists(output);
        }
    }

    @Test
    @DisplayName("should refuse to sign when signing is disabled")
    void shouldRefuseToSign_whenSigningDisabled() throws Exception {
        // Whether to sign is the caller's decision. Handing the input back here would make a
        // returned path mean two different things, and only one of them is a file to own.
        Path source = writeSinglePagePdf();
        PDFSigningConfig config = new PDFSigningConfig(
                false, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> PDFSigningUtil.signPDF(source, config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PDF signing is not enabled");
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            assertThat(document.getSignatureDictionaries()).isEmpty();
        }
        // The shared verifier answers false for an unsigned file rather than throwing.
        assertThat(PdfSigningTestSupport.verifyDetachedSignature(source, null)).isFalse();
    }

    @Test
    @DisplayName("should add detached CMS signature to a PDF")
    void shouldAddDetachedCmsSignature_toPdf() throws Exception {
        Path source = writeSinglePagePdf();
        Path signed = PDFSigningUtil.signPDF(source, enabledConfig());
        generatedOutputs.add(signed);

        assertThat(signed).exists().isNotEqualTo(source);
        try (PDDocument document = Loader.loadPDF(signed.toFile())) {
            assertThat(document.getSignatureDictionaries()).hasSize(1);
            PDSignature signature = document.getSignatureDictionaries().get(0);
            assertThat(signature.getName()).isEqualTo("CARLOS Test Signer");
            assertThat(signature.getReason()).isEqualTo("Unit test signature");
        }
        assertThat(PdfSigningTestSupport.verifyDetachedSignature(signed, null)).isTrue();
    }

    @Test
    @DisplayName("should fail signature verification when signed content is tampered")
    void shouldFailVerification_whenSignedContentIsTampered() throws Exception {
        Path signed = PDFSigningUtil.signPDF(writeSinglePagePdf(), enabledConfig());
        generatedOutputs.add(signed);

        byte[] signedBytes = Files.readAllBytes(signed);
        PDSignature signature;
        try (PDDocument document = Loader.loadPDF(signed.toFile())) {
            signature = document.getSignatureDictionaries().get(0);
        }
        // Flip one byte inside the signed range, on a copy the verifier will read back. Past
        // the header, so the document still parses and only the signature check can fail.
        int firstSignedByte = (int) signature.getByteRange()[0];
        int signedLength = (int) signature.getByteRange()[1];
        int tamperIndex = firstSignedByte + Math.min(32, signedLength - 1);
        signedBytes[tamperIndex] = (byte) (signedBytes[tamperIndex] ^ 0x01);
        Path tampered = tempDir.resolve("tampered.pdf");
        Files.write(tampered, signedBytes);

        assertThat(PdfSigningTestSupport.verifyDetachedSignature(tampered, null)).isFalse();
    }

    @Test
    @DisplayName("should sign encrypted PDF using the PDF password")
    void shouldSignEncryptedPdf_usingPdfPassword() throws Exception {
        Path encrypted = PDFEncryptionUtil.encryptPDF(writeSinglePagePdf(), "s3cret");
        generatedOutputs.add(encrypted);

        Path signed = PDFSigningUtil.signPDF(encrypted, enabledConfig(), "s3cret");
        generatedOutputs.add(signed);

        assertThatThrownBy(() -> Loader.loadPDF(signed.toFile()).close())
                .isInstanceOf(InvalidPasswordException.class);
        try (PDDocument document = Loader.loadPDF(signed.toFile(), "s3cret")) {
            assertThat(document.isEncrypted()).isTrue();
            assertThat(document.getSignatureDictionaries()).hasSize(1);
        }
        assertThat(PdfSigningTestSupport.verifyDetachedSignature(signed, "s3cret")).isTrue();
    }

    @Test
    @DisplayName("should sign unencrypted PDF when a password is supplied")
    void shouldSignUnencryptedPdf_whenPasswordIsSupplied() throws Exception {
        Path signed = PDFSigningUtil.signPDF(
                writeSinglePagePdf(), enabledConfig(), "unused-password");
        generatedOutputs.add(signed);

        try (PDDocument document = Loader.loadPDF(signed.toFile())) {
            assertThat(document.isEncrypted()).isFalse();
            assertThat(document.getSignatureDictionaries()).hasSize(1);
        }
        assertThat(PdfSigningTestSupport.verifyDetachedSignature(signed, null)).isTrue();
    }

    @Test
    @DisplayName("should write the signature as definite-length DER")
    void shouldWriteSignatureAsDefiniteLengthDer_forStrictValidators() throws Exception {
        Path signed = PDFSigningUtil.signPDF(writeSinglePagePdf(), enabledConfig());
        generatedOutputs.add(signed);

        byte[] pdfBytes = Files.readAllBytes(signed);
        byte[] contents;
        try (PDDocument document = Loader.loadPDF(signed.toFile())) {
            contents = document.getSignatureDictionaries().get(0).getContents(pdfBytes);
        }
        // ISO 32000-1 12.8.3.3.1 requires DER. BouncyCastle's default is indefinite-length BER
        // (30 80 ...), which lenient readers accept and strict validators reject.
        assertThat(contents[0] & 0xff).isEqualTo(0x30);
        assertThat(contents[1] & 0xff).isNotEqualTo(0x80);
        ASN1Primitive cms;
        try (ASN1InputStream input = new ASN1InputStream(contents)) {
            cms = input.readObject();
        }
        byte[] asWritten = Arrays.copyOf(contents, cms.getEncoded().length);
        assertThat(cms.getEncoded("DER")).isEqualTo(asWritten);
    }

    @Test
    @DisplayName("should sign a third-party restricted PDF when handed an unrelated send password")
    void shouldSignThirdPartyRestrictedPdf_whenHandedUnrelatedSendPassword() throws Exception {
        // An uploaded document its author restricted: opens with no password, but has its own
        // owner password. The send password is for the encrypted message PDF, not for this file,
        // and offering it to PDFBox is rejected outright.
        Path restricted = tempDir.resolve("third-party-" + System.nanoTime() + ".pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.protect(new StandardProtectionPolicy("authors-owner-password", "", new AccessPermission()));
            document.save(restricted.toFile());
        }
        assertThatThrownBy(() -> Loader.loadPDF(restricted.toFile(), "send-password").close())
                .isInstanceOf(InvalidPasswordException.class);

        Path signed = PDFSigningUtil.signPDF(restricted, enabledConfig(), "send-password");
        generatedOutputs.add(signed);

        try (PDDocument document = Loader.loadPDF(signed.toFile())) {
            assertThat(document.getSignatureDictionaries()).hasSize(1);
        }
        assertThat(PdfSigningTestSupport.verifyDetachedSignature(signed, null)).isTrue();
    }

    @Test
    @DisplayName("should leave no output behind when PDFBox refuses the document")
    void shouldLeaveNoOutputBehind_whenPdfBoxRefusesTheDocument() throws Exception {
        // PDFBox rejects a page-less document with IllegalStateException, not IOException.
        Path empty = tempDir.resolve("empty-" + System.nanoTime() + ".pdf");
        try (PDDocument document = new PDDocument()) {
            document.save(empty.toFile());
        }
        Path output = tempDir.resolve("signed-output.pdf");
        PDFSigningConfig config = enabledConfig();

        try (MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS)) {
            paths.when(() -> PathValidationUtils.createSecureTempFile(anyString(), eq(".pdf")))
                    .thenAnswer(call -> Files.createFile(output).toFile());

            assertThatThrownBy(() -> PDFSigningUtil.signPDF(empty, config))
                    .isInstanceOf(IOException.class)
                    .hasMessage("Failed to sign PDF document");
        }
        assertThat(output).doesNotExist();
    }

    @Test
    @DisplayName("should fail closed when enabled config is incomplete")
    void shouldFailClosed_whenEnabledConfigIsIncomplete() throws IOException {
        Path source = writeSinglePagePdf();
        PDFSigningConfig config = new PDFSigningConfig(
                true, null, null, null, PdfSigningTestSupport.KEY_ALIAS, null, null, null, null, null);

        assertThatThrownBy(() -> PDFSigningUtil.signPDF(source, config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pdf.signing.keystore.path");
    }

    @Test
    @DisplayName("should fail closed when private key does not match certificate")
    void shouldFailClosed_whenPrivateKeyDoesNotMatchCertificate() throws Exception {
        KeyPair privateKeyPair = PdfSigningTestSupport.createKeyPair("RSA", 2048);
        KeyPair certificateKeyPair = PdfSigningTestSupport.createKeyPair("RSA", 2048);
        PDFSigningConfig config = PdfSigningTestSupport.enabledConfig(tempDir.resolve("mismatch.p12"),
                privateKeyPair.getPrivate(),
                PdfSigningTestSupport.createSelfSignedCertificate(certificateKeyPair, "SHA256withRSA"));
        Path source = writeSinglePagePdf();

        assertThatThrownBy(() -> PDFSigningUtil.signPDF(source, config))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Failed to load PDF signing key material")
                .hasRootCauseInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("should fail closed when signing key algorithm is unsupported")
    void shouldFailClosed_whenSigningKeyAlgorithmIsUnsupported() throws Exception {
        KeyPair keyPair = PdfSigningTestSupport.createKeyPair("DSA", 2048);
        PDFSigningConfig config = PdfSigningTestSupport.enabledConfig(tempDir.resolve("dsa.p12"),
                keyPair.getPrivate(), PdfSigningTestSupport.createSelfSignedCertificate(keyPair, "SHA256withDSA"));
        Path source = writeSinglePagePdf();

        assertThatThrownBy(() -> PDFSigningUtil.signPDF(source, config))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Failed to load PDF signing key material")
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should sign as PAdES with the ETSI.CAdES.detached SubFilter")
    void shouldUsePadesSubFilter_forEverySignature() throws Exception {
        Path signed = PDFSigningUtil.signPDF(writeSinglePagePdf(), enabledConfig());
        generatedOutputs.add(signed);

        try (PDDocument document = Loader.loadPDF(signed.toFile())) {
            PDSignature signature = document.getSignatureDictionaries().get(0);
            assertThat(signature.getSubFilter()).isEqualTo("ETSI.CAdES.detached");
            assertThat(signature.getSignDate()).as("the signing time is the dictionary's /M").isNotNull();
        }
    }

    @Test
    @DisplayName("should carry signing-certificate-v2 and no CMS signing-time")
    void shouldCarrySigningCertificateV2_insteadOfSigningTime() throws Exception {
        Path signed = PDFSigningUtil.signPDF(writeSinglePagePdf(), enabledConfig());
        generatedOutputs.add(signed);

        CMSSignedData cms = signedData(signed, null);
        SignerInformation signer = cms.getSignerInfos().getSigners().iterator().next();
        X509CertificateHolder certificate = (X509CertificateHolder) cms.getCertificates()
                .getMatches(signer.getSID()).iterator().next();
        AttributeTable attributes = signer.getSignedAttributes();

        assertThat(attributes.get(CMSAttributes.contentType)).isNotNull();
        assertThat(attributes.get(CMSAttributes.messageDigest)).isNotNull();
        // PAdES baseline excludes the CMS signing-time; the time lives in the signature dictionary.
        assertThat(attributes.get(CMSAttributes.signingTime)).isNull();
        SigningCertificateV2 signingCertificate = SigningCertificateV2.getInstance(
                attributes.get(PKCSObjectIdentifiers.id_aa_signingCertificateV2).getAttrValues().getObjectAt(0));
        ESSCertIDv2 id = signingCertificate.getCerts()[0];
        assertThat(id.getHashAlgorithm().getAlgorithm()).isEqualTo(NISTObjectIdentifiers.id_sha256);
        assertThat(id.getCertHash()).isEqualTo(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
        assertThat(id.getIssuerSerial().getSerial().getValue()).isEqualTo(certificate.getSerialNumber());
    }

    @Test
    @DisplayName("should cover the whole file with the signed byte range")
    void shouldCoverTheWholeFile_withTheByteRange() throws Exception {
        Path plain = PDFSigningUtil.signPDF(writeSinglePagePdf(), enabledConfig());
        generatedOutputs.add(plain);
        Path encrypted = PDFEncryptionUtil.encryptPDF(writeSinglePagePdf(), "s3cret");
        generatedOutputs.add(encrypted);
        Path encryptedSigned = PDFSigningUtil.signPDF(encrypted, enabledConfig(), "s3cret");
        generatedOutputs.add(encryptedSigned);

        for (Path signed : List.of(plain, encryptedSigned)) {
            String password = signed.equals(plain) ? null : "s3cret";
            int[] byteRange;
            try (PDDocument document = password == null
                    ? Loader.loadPDF(signed.toFile()) : Loader.loadPDF(signed.toFile(), password)) {
                byteRange = document.getSignatureDictionaries().get(0).getByteRange();
            }
            // [start, length, start2, length2]: from byte 0 to the last byte, around /Contents only.
            assertThat(byteRange).hasSize(4);
            assertThat(byteRange[0]).isZero();
            assertThat((long) byteRange[2] + byteRange[3]).isEqualTo(Files.size(signed));
            assertGapIsExactlyTheContentsString(Files.readAllBytes(signed), byteRange[1], byteRange[2]);
        }
    }

    // The one unsigned gap must be the whole /Contents hex string and nothing else:
    // the /Contents key right before it, '<' and '>' at its ends, only hex digits between.
    private static void assertGapIsExactlyTheContentsString(byte[] pdf, int gapStart, int gapEnd) {
        assertThat(gapEnd - gapStart).isGreaterThan(2);
        assertThat((char) pdf[gapStart]).isEqualTo('<');
        assertThat((char) pdf[gapEnd - 1]).isEqualTo('>');
        assertThat((gapEnd - gapStart - 2) % 2).as("hex digits come in pairs").isZero();
        for (int i = gapStart + 1; i < gapEnd - 1; i++) {
            assertThat(Character.digit(pdf[i], 16)).as("hex digit at byte %d", i).isNotNegative();
        }
        int keyStart = Math.max(0, gapStart - 32);
        String beforeGap = new String(pdf, keyStart, gapStart - keyStart, java.nio.charset.StandardCharsets.US_ASCII);
        assertThat(beforeGap.stripTrailing()).endsWith("/Contents");
    }

    @Test
    @DisplayName("should leave only zeros after the CMS in /Contents, in plain and encrypted PDFs")
    void shouldPadTheSignatureWithZeros_inPlainAndEncryptedPdfs() throws Exception {
        Path plain = PDFSigningUtil.signPDF(writeSinglePagePdf(), enabledConfig());
        generatedOutputs.add(plain);
        Path encrypted = PDFEncryptionUtil.encryptPDF(writeSinglePagePdf(), "s3cret");
        generatedOutputs.add(encrypted);
        Path encryptedSigned = PDFSigningUtil.signPDF(encrypted, enabledConfig(), "s3cret");
        generatedOutputs.add(encryptedSigned);

        for (Path signed : List.of(plain, encryptedSigned)) {
            String password = signed.equals(plain) ? null : "s3cret";
            byte[] pdfBytes = Files.readAllBytes(signed);
            byte[] contents;
            try (PDDocument document = password == null
                    ? Loader.loadPDF(signed.toFile()) : Loader.loadPDF(signed.toFile(), password)) {
                contents = document.getSignatureDictionaries().get(0).getContents(pdfBytes);
            }
            int cmsLength;
            try (ASN1InputStream input = new ASN1InputStream(contents)) {
                cmsLength = input.readObject().getEncoded().length;
            }
            // A signature's /Contents is never encrypted (ISO 32000-1 7.6.1): PDFBox's encrypted
            // placeholder must not survive after the CMS, and the signature must still verify.
            assertThat(Arrays.copyOfRange(contents, cmsLength, contents.length)).as(signed.getFileName().toString())
                    .isNotEmpty().containsOnly((byte) 0);
            assertThat(PdfSigningTestSupport.verifyDetachedSignature(signed, password)).isTrue();
        }
    }

    @Test
    @DisplayName("should refuse to patch a byte range that does not frame the /Contents string")
    void shouldRefusePadding_whenTheByteRangeIsWrong() throws Exception {
        Path signed = PDFSigningUtil.signPDF(writeSinglePagePdf(), enabledConfig());
        generatedOutputs.add(signed);
        byte[] before = Files.readAllBytes(signed);

        assertThatThrownBy(() -> PDFSigningUtil.zeroSignaturePadding(signed, new int[] {0, 10, 20, 5}, 4))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> PDFSigningUtil.zeroSignaturePadding(signed, new int[] {1, 10, 20, 5}, 4))
                .isInstanceOf(IOException.class);
        assertThat(Files.readAllBytes(signed)).isEqualTo(before);
    }

    @Test
    @DisplayName("should sign and verify with an ECDSA P-256 key")
    void shouldSignAndVerify_withAP256Key() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();
        PDFSigningConfig config = PdfSigningTestSupport.enabledConfig(tempDir.resolve("p256.p12"),
                keyPair.getPrivate(), PdfSigningTestSupport.createSelfSignedCertificate(keyPair, "SHA256withECDSA"));

        Path signed = PDFSigningUtil.signPDF(writeSinglePagePdf(), config);
        generatedOutputs.add(signed);

        assertThat(PdfSigningTestSupport.verifyDetachedSignature(signed, null)).isTrue();
        SignerInformation signer = signedData(signed, null).getSignerInfos().getSigners().iterator().next();
        assertThat(signer.getEncryptionAlgOID()).isEqualTo("1.2.840.10045.4.3.2"); // ecdsa-with-SHA256
    }

    /** The CMS object in a signed PDF's first signature, read from a stream like the shared verifier. */
    private static CMSSignedData signedData(Path signedPdf, String password) throws Exception {
        byte[] pdfBytes = Files.readAllBytes(signedPdf);
        try (PDDocument document = password == null
                ? Loader.loadPDF(signedPdf.toFile()) : Loader.loadPDF(signedPdf.toFile(), password)) {
            PDSignature signature = document.getSignatureDictionaries().get(0);
            return new CMSSignedData(new CMSProcessableByteArray(signature.getSignedContent(pdfBytes)),
                    new ByteArrayInputStream(signature.getContents(pdfBytes)));
        }
    }

    private Path writeSinglePagePdf() throws IOException {
        return PdfSigningTestSupport.writeSinglePagePdf(tempDir.resolve("sample-" + System.nanoTime() + ".pdf"));
    }

    private PDFSigningConfig enabledConfig() throws Exception {
        return PdfSigningTestSupport.enabledRsaConfig(tempDir.resolve("pdf-signing-" + System.nanoTime() + ".p12"));
    }
}
