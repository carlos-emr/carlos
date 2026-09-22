/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.utility;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.SignatureException;
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
    void shouldRefuseToSign_whenSigningDisabled() throws IOException {
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
        // Flip one byte inside the signed range, on a copy the verifier will read back.
        int firstSignedByte = (int) signature.getByteRange()[0];
        signedBytes[firstSignedByte] = (byte) (signedBytes[firstSignedByte] ^ 0x01);
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

    private Path writeSinglePagePdf() throws IOException {
        return PdfSigningTestSupport.writeSinglePagePdf(tempDir.resolve("sample-" + System.nanoTime() + ".pdf"));
    }

    private PDFSigningConfig enabledConfig() throws Exception {
        return PdfSigningTestSupport.enabledRsaConfig(tempDir.resolve("pdf-signing-" + System.nanoTime() + ".p12"));
    }
}
