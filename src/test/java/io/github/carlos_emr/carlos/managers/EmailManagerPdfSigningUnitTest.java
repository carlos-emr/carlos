/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

package io.github.carlos_emr.carlos.managers;

import java.io.ByteArrayInputStream;
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
import java.util.ArrayList;
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
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.email.core.EmailComposeWorkingDirectory;
import io.github.carlos_emr.carlos.email.core.EmailConsentResolver;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailSenderFactory;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.PDFSigningConfig;
import io.github.carlos_emr.carlos.utility.PDFSigningUtil;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Verifies how {@link EmailManager} wires PDF signing into a send. The signature itself is
 * covered by {@code PDFSigningUtilUnitTest}, so most tests here stub the signer; one runs the
 * real encrypt, sign and adopt sequence end to end.
 *
 * @since 2026-09-21
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@Tag("security")
@DisplayName("EmailManager PDF signing")
class EmailManagerPdfSigningUnitTest extends CarlosUnitTestBase {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("should leave attachments untouched when signing is disabled")
    void shouldLeaveAttachmentsUntouched_whenSigningIsDisabled() throws Exception {
        Path source = writeSinglePagePdf("source.pdf");
        EmailData emailData = emailDataWith(source);

        try (MockedStatic<PDFSigningConfig> config = mockStatic(PDFSigningConfig.class, CALLS_REAL_METHODS);
                MockedStatic<PDFSigningUtil> signer = mockStatic(PDFSigningUtil.class)) {
            config.when(PDFSigningConfig::fromCarlosProperties).thenReturn(signingConfig(false));

            createEmailManager().signAttachments(emailData);

            assertThat(emailData.getAttachments().get(0).getFilePath()).isEqualTo(source.toString());
            // Signing is the only reason an unencrypted send needs a working directory.
            assertThat(emailData.getWorkingDirectory()).isNull();
            signer.verifyNoInteractions();
        }
    }

    @Test
    @DisplayName("should adopt each signed PDF into the working directory when signing is enabled")
    void shouldAdoptEachSignedPdf_whenSigningIsEnabled() throws Exception {
        Path source = writeSinglePagePdf("source.pdf");
        Path signed = writeSinglePagePdf("signed.pdf");
        EmailData emailData = emailDataWith(source);

        try (MockedStatic<PDFSigningConfig> config = mockStatic(PDFSigningConfig.class, CALLS_REAL_METHODS);
                MockedStatic<PDFSigningUtil> signer = mockStatic(PDFSigningUtil.class);
                MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS)) {
            config.when(PDFSigningConfig::fromCarlosProperties).thenReturn(signingConfig(true));
            paths.when(() -> PathValidationUtils.resolveTrustedPath(any())).thenAnswer(call -> call.getArgument(0));
            // Unencrypted send: no owner password may be handed to the signer.
            signer.when(() -> PDFSigningUtil.signPDF(eq(source), any(PDFSigningConfig.class), isNull()))
                    .thenReturn(signed);

            createEmailManager().signAttachments(emailData);

            Path sent = Path.of(emailData.getAttachments().get(0).getFilePath());
            assertThat(sent).exists().isNotEqualTo(source);
            // Owned by the working directory, so closing it is what cleans the signed copy up.
            emailData.getWorkingDirectory().close();
            assertThat(sent).doesNotExist();
            assertThat(source).exists();
        } finally {
            closeWorkingDirectory(emailData);
        }
    }

    @Test
    @DisplayName("should sign with the PDF password when the attachment was encrypted")
    void shouldSignWithPdfPassword_whenAttachmentWasEncrypted() throws Exception {
        Path source = writeSinglePagePdf("encrypted.pdf");
        Path signed = writeSinglePagePdf("signed.pdf");
        EmailData emailData = emailDataWith(source);
        emailData.setIsEncrypted(true);
        emailData.setPassword("correct horse battery staple");

        try (MockedStatic<PDFSigningConfig> config = mockStatic(PDFSigningConfig.class, CALLS_REAL_METHODS);
                MockedStatic<PDFSigningUtil> signer = mockStatic(PDFSigningUtil.class);
                MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS)) {
            config.when(PDFSigningConfig::fromCarlosProperties).thenReturn(signingConfig(true));
            paths.when(() -> PathValidationUtils.resolveTrustedPath(any())).thenAnswer(call -> call.getArgument(0));
            signer.when(() -> PDFSigningUtil.signPDF(any(Path.class), any(PDFSigningConfig.class), any()))
                    .thenReturn(signed);

            createEmailManager().signAttachments(emailData);

            // An encrypted PDF can only be modified with its owner password.
            signer.verify(() -> PDFSigningUtil.signPDF(
                    eq(source), any(PDFSigningConfig.class), eq("correct horse battery staple")));
        } finally {
            closeWorkingDirectory(emailData);
        }
    }

    @Test
    @DisplayName("should abort the send when an attachment cannot be signed")
    void shouldAbortSend_whenAttachmentCannotBeSigned() throws Exception {
        Path source = writeSinglePagePdf("source.pdf");
        EmailData emailData = emailDataWith(source);

        try (MockedStatic<PDFSigningConfig> config = mockStatic(PDFSigningConfig.class, CALLS_REAL_METHODS);
                MockedStatic<PDFSigningUtil> signer = mockStatic(PDFSigningUtil.class);
                MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS)) {
            config.when(PDFSigningConfig::fromCarlosProperties).thenReturn(signingConfig(true));
            paths.when(() -> PathValidationUtils.resolveTrustedPath(any())).thenAnswer(call -> call.getArgument(0));
            signer.when(() -> PDFSigningUtil.signPDF(any(Path.class), any(PDFSigningConfig.class), any()))
                    .thenThrow(new IOException("keystore /etc/carlos-emr/pdf-signing.p12 unreadable"));

            // Fail closed: signing is enabled, so an unsigned attachment must never be delivered.
            assertThatThrownBy(() -> createEmailManager().signAttachments(emailData))
                    .isInstanceOf(EmailSendingException.class)
                    .hasMessage("Failed to sign email PDF attachment");
            assertThat(emailData.getAttachments().get(0).getFilePath()).isEqualTo(source.toString());
        } finally {
            closeWorkingDirectory(emailData);
        }
    }

    @Test
    @DisplayName("should delete the signed PDF when the working directory cannot adopt it")
    void shouldDeleteSignedPdf_whenWorkingDirectoryCannotAdoptIt() throws Exception {
        Path source = writeSinglePagePdf("source.pdf");
        // A real secure temp file, as signPDF produces: it holds the patient's document, and if
        // adoption fails nothing else would ever remove it.
        Path signed = PathValidationUtils.createSecureTempFile("signedPDF_test", ".pdf").toPath();
        EmailData emailData = emailDataWith(source);
        EmailComposeWorkingDirectory closedDirectory = EmailComposeWorkingDirectory.create();
        closedDirectory.close();
        emailData.setWorkingDirectory(closedDirectory);

        try (MockedStatic<PDFSigningConfig> config = mockStatic(PDFSigningConfig.class, CALLS_REAL_METHODS);
                MockedStatic<PDFSigningUtil> signer = mockStatic(PDFSigningUtil.class)) {
            config.when(PDFSigningConfig::fromCarlosProperties).thenReturn(signingConfig(true));
            signer.when(() -> PDFSigningUtil.signPDF(any(Path.class), any(PDFSigningConfig.class), any()))
                    .thenReturn(signed);

            assertThatThrownBy(() -> createEmailManager().signAttachments(emailData))
                    .isInstanceOf(EmailSendingException.class);

            assertThat(signed).doesNotExist();
            assertThat(source).exists();
        } finally {
            Files.deleteIfExists(signed);
        }
    }

    @Test
    @DisplayName("should report a send failure when the signer fails unchecked")
    void shouldReportSendFailure_whenSignerFailsUnchecked() throws Exception {
        EmailData emailData = emailDataWith(writeSinglePagePdf("source.pdf"));

        try (MockedStatic<PDFSigningConfig> config = mockStatic(PDFSigningConfig.class, CALLS_REAL_METHODS);
                MockedStatic<PDFSigningUtil> signer = mockStatic(PDFSigningUtil.class)) {
            config.when(PDFSigningConfig::fromCarlosProperties).thenReturn(signingConfig(true));
            signer.when(() -> PDFSigningUtil.signPDF(any(Path.class), any(PDFSigningConfig.class), any()))
                    .thenThrow(new ClassCastException("malformed dictionary"));

            // Escaping as a RuntimeException would skip completeFailedSend and strand the
            // EmailLog at PENDING behind a 500.
            assertThatThrownBy(() -> createEmailManager().signAttachments(emailData))
                    .isInstanceOf(EmailSendingException.class)
                    .hasMessage("Failed to sign email PDF attachment");
        } finally {
            closeWorkingDirectory(emailData);
        }
    }

    @Test
    @DisplayName("should deliver a password-protected PDF with a verifiable signature through the real pipeline")
    void shouldDeliverEncryptedAndVerifiablySignedPdf_throughRealPipeline() throws Exception {
        // Nothing is stubbed except where the configuration comes from: the real PDFEncryptionUtil,
        // PDFSigningUtil, PathValidationUtils and working directory run in production order.
        Path source = writeSinglePagePdf("lab-result.pdf");
        EmailData emailData = emailDataWith(source);
        emailData.setIsEncrypted(true);
        emailData.setIsAttachmentEncrypted(true);
        emailData.setPassword("correct horse battery staple");
        PDFSigningConfig realConfig = selfSignedSigningConfig();

        try (MockedStatic<PDFSigningConfig> config = mockStatic(PDFSigningConfig.class, CALLS_REAL_METHODS)) {
            config.when(PDFSigningConfig::fromCarlosProperties).thenReturn(realConfig);
            EmailManager emailManager = createEmailManager();

            emailManager.encryptEmail(emailData);
            emailManager.signAttachments(emailData);

            Path sent = Path.of(emailData.getAttachments().get(0).getFilePath());
            assertThat(sent).exists().isNotEqualTo(source);
            // Still password-protected: signing must not strip the encryption.
            assertThatThrownBy(() -> Loader.loadPDF(sent.toFile()).close())
                    .isInstanceOf(InvalidPasswordException.class);

            byte[] sentBytes = Files.readAllBytes(sent);
            try (PDDocument opened = Loader.loadPDF(sent.toFile(), "correct horse battery staple")) {
                assertThat(opened.isEncrypted()).isTrue();
                assertThat(opened.getSignatureDictionaries()).hasSize(1);
                PDSignature signature = opened.getSignatureDictionaries().get(0);
                // The signature must verify over the exact bytes that would be attached.
                assertThat(verifiesAgainstEmbeddedCertificate(
                        signature.getContents(sentBytes), signature.getSignedContent(sentBytes))).isTrue();
            }

            // The working directory owns every intermediate file, and the source is never touched.
            emailData.getWorkingDirectory().close();
            assertThat(sent).doesNotExist();
            assertThat(source).exists();
        } finally {
            closeWorkingDirectory(emailData);
        }
    }

    private EmailData emailDataWith(Path pdf) {
        EmailData emailData = new EmailData();
        emailData.setAttachments(new ArrayList<>(List.of(
                new EmailAttachment(pdf.getFileName().toString(), pdf.toString(), DocumentType.DOC, 1))));
        return emailData;
    }

    private PDFSigningConfig signingConfig(boolean enabled) {
        return new PDFSigningConfig(enabled, "unused.p12", "PKCS12", "changeit".toCharArray(),
                null, null, null, null, null, null);
    }

    private PDFSigningConfig selfSignedSigningConfig() throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        X500Name subject = new X500Name("CN=CARLOS Test Signer");
        Instant now = Instant.now();
        X509CertificateHolder holder = new JcaX509v3CertificateBuilder(
                subject, BigInteger.valueOf(now.toEpochMilli()),
                Date.from(now.minus(1, ChronoUnit.DAYS)), Date.from(now.plus(30, ChronoUnit.DAYS)),
                subject, keyPair.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withRSA")
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME).build(keyPair.getPrivate()));
        X509Certificate certificate = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate(holder);

        char[] keystorePassword = "changeit".toCharArray();
        Path keystorePath = tempDir.resolve("pdf-signing.p12");
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, keystorePassword);
        keyStore.setKeyEntry("signer", keyPair.getPrivate(), keystorePassword, new Certificate[]{certificate});
        try (OutputStream output = Files.newOutputStream(keystorePath)) {
            keyStore.store(output, keystorePassword);
        }
        return new PDFSigningConfig(true, keystorePath.toString(), "PKCS12", keystorePassword,
                "signer", null, "CARLOS Test Signer", "Unit test signature", null, null);
    }

    private boolean verifiesAgainstEmbeddedCertificate(byte[] signatureContents, byte[] signedContent)
            throws Exception {
        // Stream constructor: bytes follow the CMS blob in its fixed-size /Contents slot (zeros,
        // or ciphertext in an encrypted PDF), and the byte[] constructor rejects them.
        CMSSignedData signedData = new CMSSignedData(
                new CMSProcessableByteArray(signedContent), new ByteArrayInputStream(signatureContents));
        SignerInformation signer = signedData.getSignerInfos().getSigners().iterator().next();
        X509CertificateHolder certificate = (X509CertificateHolder)
                signedData.getCertificates().getMatches(signer.getSID()).iterator().next();
        return signer.verify(new JcaSimpleSignerInfoVerifierBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME).build(certificate));
    }

    private Path writeSinglePagePdf(String name) throws IOException {
        Path pdf = tempDir.resolve(name);
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(pdf.toFile());
        }
        assertThat(Files.size(pdf)).isPositive();
        return pdf;
    }

    private static void closeWorkingDirectory(EmailData emailData) {
        if (emailData.getWorkingDirectory() != null) {
            emailData.getWorkingDirectory().close();
        }
    }

    private EmailManager createEmailManager() {
        return new EmailManager(
                mock(EmailConsentResolver.class), mock(EmailSenderFactory.class),
                mock(SecurityInfoManager.class), mock(OutboundEmailArchiveService.class));
    }
}
