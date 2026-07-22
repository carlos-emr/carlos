/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.utility;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.Store;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@Tag("fast")
@Tag("pdf")
@DisplayName("PDFSigningUtil")
class PDFSigningUtilUnitTest {
    private static final char[] KEYSTORE_PASSWORD = "changeit".toCharArray();
    private static final String KEY_ALIAS = "pdf-signing";
    private static final String BC_PROVIDER = BouncyCastleProvider.PROVIDER_NAME;

    @TempDir
    Path tempDir;

    private final List<Path> generatedOutputs = new java.util.ArrayList<>();

    @AfterEach
    void cleanUp() throws IOException {
        for (Path output : generatedOutputs) {
            Files.deleteIfExists(output);
        }
    }

    @Test
    @DisplayName("should leave source untouched when signing is disabled")
    void shouldReturnSourcePath_whenSigningDisabled() throws IOException {
        Path source = writeSinglePagePdf();
        PDFSigningConfig config = new PDFSigningConfig(
                false, null, null, null, null, null, null, null, null, null);

        Path result = PDFSigningUtil.signPDF(source, config);

        assertThat(result).isEqualTo(source);
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            assertThat(document.getSignatureDictionaries()).isEmpty();
        }
    }

    @Test
    @DisplayName("should add detached CMS signature to a PDF")
    void shouldAddDetachedCmsSignatureToPdf() throws Exception {
        Path source = writeSinglePagePdf();
        SigningFixture signingFixture = createSigningFixture();

        Path signed = PDFSigningUtil.signPDF(source, signingFixture.config());
        generatedOutputs.add(signed);

        assertThat(signed).exists().isNotEqualTo(source);
        try (PDDocument document = Loader.loadPDF(signed.toFile())) {
            assertThat(document.getSignatureDictionaries()).hasSize(1);
            PDSignature signature = document.getSignatureDictionaries().get(0);
            assertThat(signature.getName()).isEqualTo("CARLOS Test Signer");
            assertThat(signature.getReason()).isEqualTo("Unit test signature");
        }
        assertThat(verifySignature(signed)).isTrue();
    }

    @Test
    @DisplayName("should fail signature verification when signed content is tampered")
    void shouldFailVerification_whenSignedContentIsTampered() throws Exception {
        Path signed = PDFSigningUtil.signPDF(writeSinglePagePdf(), createSigningFixture().config());
        generatedOutputs.add(signed);

        byte[] signedBytes = Files.readAllBytes(signed);
        PDSignature signature;
        try (PDDocument document = Loader.loadPDF(signed.toFile())) {
            signature = document.getSignatureDictionaries().get(0);
        }
        byte[] signedContent = signature.getSignedContent(signedBytes);
        signedContent[0] = (byte) (signedContent[0] ^ 0x01);

        assertThat(verifyCmsSignature(signature.getContents(signedBytes), signedContent)).isFalse();
    }

    @Test
    @DisplayName("should sign encrypted PDF using the PDF password")
    void shouldSignEncryptedPdfUsingPdfPassword() throws Exception {
        Path encrypted = PDFEncryptionUtil.encryptPDF(writeSinglePagePdf(), "s3cret");
        generatedOutputs.add(encrypted);

        Path signed = PDFSigningUtil.signPDF(encrypted, createSigningFixture().config(), "s3cret");
        generatedOutputs.add(signed);

        assertThatThrownBy(() -> Loader.loadPDF(signed.toFile()).close())
                .isInstanceOf(InvalidPasswordException.class);
        try (PDDocument document = Loader.loadPDF(signed.toFile(), "s3cret")) {
            assertThat(document.isEncrypted()).isTrue();
            assertThat(document.getSignatureDictionaries()).hasSize(1);
        }
        assertThat(verifySignature(signed, "s3cret")).isTrue();
    }

    @Test
    @DisplayName("should fail closed when enabled config is incomplete")
    void shouldFailClosed_whenEnabledConfigIsIncomplete() throws IOException {
        Path source = writeSinglePagePdf();
        PDFSigningConfig config = new PDFSigningConfig(
                true, null, null, null, KEY_ALIAS, null, null, null, null, null);

        assertThatThrownBy(() -> PDFSigningUtil.signPDF(source, config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pdf.signing.keystore.path");
    }

    private Path writeSinglePagePdf() throws IOException {
        Path pdf = tempDir.resolve("sample-" + System.nanoTime() + ".pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(pdf.toFile());
        }
        return pdf;
    }

    private SigningFixture createSigningFixture() throws Exception {
        ensureBouncyCastleProvider();

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        X500Name subject = new X500Name("CN=CARLOS Test Signer");
        Instant now = Instant.now();
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BC_PROVIDER)
                .build(keyPair.getPrivate());
        X509CertificateHolder certificateHolder = new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(now.toEpochMilli()),
                Date.from(now.minus(1, ChronoUnit.DAYS)),
                Date.from(now.plus(365, ChronoUnit.DAYS)),
                subject,
                keyPair.getPublic())
                .build(signer);
        X509Certificate certificate = new JcaX509CertificateConverter()
                .setProvider(BC_PROVIDER)
                .getCertificate(certificateHolder);
        certificate.verify(keyPair.getPublic());

        Path keystorePath = tempDir.resolve("pdf-signing.p12");
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, KEYSTORE_PASSWORD);
        keyStore.setKeyEntry(KEY_ALIAS, keyPair.getPrivate(), KEYSTORE_PASSWORD, new Certificate[]{certificate});
        try (OutputStream output = Files.newOutputStream(keystorePath)) {
            keyStore.store(output, KEYSTORE_PASSWORD);
        }

        PDFSigningConfig config = new PDFSigningConfig(
                true,
                keystorePath.toString(),
                "PKCS12",
                KEYSTORE_PASSWORD,
                KEY_ALIAS,
                null,
                "CARLOS Test Signer",
                "Unit test signature",
                "Test Clinic",
                "test@example.com");
        return new SigningFixture(config);
    }

    private boolean verifySignature(Path signedPdf) throws Exception {
        return verifySignature(signedPdf, null);
    }

    private boolean verifySignature(Path signedPdf, String password) throws Exception {
        byte[] pdfBytes = Files.readAllBytes(signedPdf);
        try (PDDocument document = password == null
                ? Loader.loadPDF(signedPdf.toFile())
                : Loader.loadPDF(signedPdf.toFile(), password)) {
            PDSignature signature = document.getSignatureDictionaries().get(0);
            return verifyCmsSignature(signature.getContents(pdfBytes), signature.getSignedContent(pdfBytes));
        }
    }

    private boolean verifyCmsSignature(byte[] signatureContents, byte[] signedContent) throws Exception {
        CMSSignedData signedData = new CMSSignedData(new CMSProcessableByteArray(signedContent), signatureContents);
        SignerInformation signerInformation = signedData.getSignerInfos().getSigners().iterator().next();
        Store<X509CertificateHolder> certificates = signedData.getCertificates();
        Object certificateMatch = certificates.getMatches(signerInformation.getSID()).iterator().next();
        X509CertificateHolder certificateHolder = (X509CertificateHolder) certificateMatch;
        try {
            return signerInformation.verify(new org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder()
                    .setProvider(BC_PROVIDER)
                    .build(certificateHolder));
        } catch (CMSException e) {
            return false;
        }
    }

    private static void ensureBouncyCastleProvider() {
        if (Security.getProvider(BC_PROVIDER) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private record SigningFixture(PDFSigningConfig config) {
    }
}
