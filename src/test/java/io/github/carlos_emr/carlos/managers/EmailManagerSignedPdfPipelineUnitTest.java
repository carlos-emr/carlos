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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.email.core.EmailConsentResolver;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailDocumentManifestComposer;
import io.github.carlos_emr.carlos.email.core.EmailSenderFactory;
import io.github.carlos_emr.carlos.email.core.PdfAttachmentSigningService;
import io.github.carlos_emr.carlos.email.core.PdfSignatureService;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.test.util.PdfSigningTestSupport;
import io.github.carlos_emr.carlos.utility.DocumentManifest;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFSigningConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Runs the real encrypt, sign and adopt sequence of an email send end to end: the manager's
 * {@code encryptEmail}, then {@link PdfAttachmentSigningService} with the real
 * {@link PdfSignatureService}, {@code PDFEncryptionUtil}, {@code PDFSigningUtil},
 * {@code PathValidationUtils} and working directory, in production order. Only where the signing
 * configuration comes from is replaced, by stubbing the injected service's
 * {@code currentConfiguration()} instead of statically mocking {@code PDFSigningConfig}
 * (carlos-emr/carlos#3851). The unit-level cases are in {@code PdfAttachmentSigningServiceUnitTest}.
 *
 * @since 2026-09-21
 */
@Tag("unit")
@Tag("email")
@Tag("security")
@DisplayName("EmailManager signed PDF pipeline")
class EmailManagerSignedPdfPipelineUnitTest extends CarlosUnitTestBase {

    private static final String PASSWORD = "correct horse battery staple";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("should deliver a password-protected PDF with a verifiable signature through the real pipeline")
    void shouldDeliverEncryptedAndVerifiablySignedPdf_throughRealPipeline() throws Exception {
        Path source = writeSinglePagePdf("lab-result.pdf");
        EmailData emailData = encryptedEmailDataWith(source);
        PdfAttachmentSigningService signing = realSigningService(
                PdfSigningTestSupport.enabledRsaConfig(tempDir.resolve("pdf-signing.p12")),
                mock(EmailDocumentManifestComposer.class));
        EmailManager emailManager = createEmailManager(signing);

        try {
            emailManager.encryptEmail(emailData);
            signing.signAttachments(emailData, null, null);

            Path sent = Path.of(emailData.getAttachments().get(0).getFilePath());
            assertThat(sent).exists().isNotEqualTo(source);
            // Still password-protected: signing must not strip the encryption.
            assertThatThrownBy(() -> Loader.loadPDF(sent.toFile()).close())
                    .isInstanceOf(InvalidPasswordException.class);

            try (PDDocument opened = Loader.loadPDF(sent.toFile(), PASSWORD)) {
                assertThat(opened.isEncrypted()).isTrue();
                assertThat(opened.getSignatureDictionaries()).hasSize(1);
            }
            // The signature must verify over the exact bytes that would be attached.
            assertThat(PdfSigningTestSupport.verifyDetachedSignature(sent, PASSWORD)).isTrue();

            // The working directory owns every intermediate file, and the source is never touched.
            emailData.getWorkingDirectory().close();
            assertThat(sent).doesNotExist();
            assertThat(source).exists();
        } finally {
            closeWorkingDirectory(emailData);
        }
    }

    @Test
    @DisplayName("should embed the manifest in a password-protected PDF through the real pipeline")
    void shouldEmbedManifestInEncryptedPdf_throughRealPipeline() throws Exception {
        Path source = writeSinglePagePdf("lab-result.pdf");
        EmailData emailData = encryptedEmailDataWith(source);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        EmailLog emailLog = new EmailLog();
        EmailDocumentManifestComposer composer = mock(EmailDocumentManifestComposer.class);
        when(composer.forSend(loggedInInfo, emailLog)).thenReturn(attachment -> fakeManifest());
        PdfAttachmentSigningService signing = realSigningService(
                PdfSigningTestSupport.enabledRsaConfig(tempDir.resolve("pdf-signing.p12")), composer);
        EmailManager emailManager = createEmailManager(signing);

        try {
            emailManager.encryptEmail(emailData);
            signing.signAttachments(emailData, loggedInInfo, emailLog);

            Path sent = Path.of(emailData.getAttachments().get(0).getFilePath());
            try (PDDocument opened = Loader.loadPDF(sent.toFile(), PASSWORD)) {
                assertThat(opened.getDocumentCatalog().getNames().getEmbeddedFiles().getNames())
                        .containsOnlyKeys(DocumentManifest.FILE_NAME);
            }
            assertThat(PdfSigningTestSupport.verifyDetachedSignature(sent, PASSWORD)).isTrue();
        } finally {
            closeWorkingDirectory(emailData);
        }
    }

    /** The real signing service and signer, reading {@code config} instead of carlos.properties. */
    private static PdfAttachmentSigningService realSigningService(PDFSigningConfig config,
            EmailDocumentManifestComposer composer) {
        PdfSignatureService signer = spy(new PdfSignatureService());
        doReturn(config).when(signer).currentConfiguration();
        return new PdfAttachmentSigningService(composer, signer);
    }

    private static DocumentManifest fakeManifest() {
        return new DocumentManifest("FAKE-document-id", 1, 1_792_454_400L, null, "FAKE Demo Clinic",
                "FAKE-Provider, Demo", null, "FAKE-Manifest-Patient, Jo", "1985-06-15", "FAKE CBC", "lab_report", null);
    }

    private static EmailData encryptedEmailDataWith(Path pdf) {
        EmailData emailData = new EmailData();
        emailData.setAttachments(new ArrayList<>(List.of(
                new EmailAttachment(pdf.getFileName().toString(), pdf.toString(), DocumentType.DOC, 1))));
        emailData.setIsEncrypted(true);
        emailData.setIsAttachmentEncrypted(true);
        emailData.setPassword(PASSWORD);
        return emailData;
    }

    private Path writeSinglePagePdf(String name) throws IOException {
        return PdfSigningTestSupport.writeSinglePagePdf(tempDir.resolve(name));
    }

    private static void closeWorkingDirectory(EmailData emailData) {
        if (emailData.getWorkingDirectory() != null) {
            emailData.getWorkingDirectory().close();
        }
    }

    private static EmailManager createEmailManager(PdfAttachmentSigningService signing) {
        return new EmailManager(
                mock(EmailConsentResolver.class), mock(EmailSenderFactory.class),
                mock(SecurityInfoManager.class), mock(OutboundEmailArchiveService.class), signing);
    }
}
