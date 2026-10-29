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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.email.core.EmailComposeWorkingDirectory;
import io.github.carlos_emr.carlos.email.core.EmailConsentResolver;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailDocumentManifestComposer;
import io.github.carlos_emr.carlos.email.core.EmailSenderFactory;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.test.util.PdfSigningTestSupport;
import io.github.carlos_emr.carlos.utility.DocumentManifest;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
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
import static org.mockito.Mockito.when;

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
            config.when(PDFSigningConfig::fromCarlosProperties).thenReturn(disabledConfig());

            createEmailManager().signAttachments(emailData);

            assertThat(emailData.getAttachments().get(0).getFilePath()).isEqualTo(source.toString());
            // Signing is disabled and this send is unencrypted, so nothing has created one.
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
            config.when(PDFSigningConfig::fromCarlosProperties).thenReturn(PdfSigningTestSupport.stubbedEnabledConfig());
            paths.when(() -> PathValidationUtils.resolveTrustedPath(any())).thenAnswer(call -> call.getArgument(0));
            // Unencrypted send: no owner password may be handed to the signer, and this form
            // of signAttachments embeds no manifest.
            signer.when(() -> PDFSigningUtil.signPDF(eq(source), any(PDFSigningConfig.class), isNull(), isNull()))
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
            config.when(PDFSigningConfig::fromCarlosProperties).thenReturn(PdfSigningTestSupport.stubbedEnabledConfig());
            paths.when(() -> PathValidationUtils.resolveTrustedPath(any())).thenAnswer(call -> call.getArgument(0));
            signer.when(() -> PDFSigningUtil.signPDF(any(Path.class), any(PDFSigningConfig.class), any(), any()))
                    .thenReturn(signed);

            createEmailManager().signAttachments(emailData);

            // An encrypted PDF can only be modified with its owner password.
            signer.verify(() -> PDFSigningUtil.signPDF(
                    eq(source), any(PDFSigningConfig.class), eq("correct horse battery staple"), isNull()));
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
            config.when(PDFSigningConfig::fromCarlosProperties).thenReturn(PdfSigningTestSupport.stubbedEnabledConfig());
            paths.when(() -> PathValidationUtils.resolveTrustedPath(any())).thenAnswer(call -> call.getArgument(0));
            signer.when(() -> PDFSigningUtil.signPDF(any(Path.class), any(PDFSigningConfig.class), any(), any()))
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
            config.when(PDFSigningConfig::fromCarlosProperties).thenReturn(PdfSigningTestSupport.stubbedEnabledConfig());
            signer.when(() -> PDFSigningUtil.signPDF(any(Path.class), any(PDFSigningConfig.class), any(), any()))
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
            config.when(PDFSigningConfig::fromCarlosProperties).thenReturn(PdfSigningTestSupport.stubbedEnabledConfig());
            signer.when(() -> PDFSigningUtil.signPDF(any(Path.class), any(PDFSigningConfig.class), any(), any()))
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
        PDFSigningConfig realConfig = PdfSigningTestSupport.enabledRsaConfig(tempDir.resolve("pdf-signing.p12"));

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

            try (PDDocument opened = Loader.loadPDF(sent.toFile(), "correct horse battery staple")) {
                assertThat(opened.isEncrypted()).isTrue();
                assertThat(opened.getSignatureDictionaries()).hasSize(1);
            }
            // The signature must verify over the exact bytes that would be attached.
            assertThat(PdfSigningTestSupport.verifyDetachedSignature(sent, "correct horse battery staple")).isTrue();

            // The working directory owns every intermediate file, and the source is never touched.
            emailData.getWorkingDirectory().close();
            assertThat(sent).doesNotExist();
            assertThat(source).exists();
        } finally {
            closeWorkingDirectory(emailData);
        }
    }

    @Test
    @DisplayName("should hand each attachment's manifest to the signer when the send passes its context")
    void shouldPassEachAttachmentsManifest_whenSendPassesItsContext() throws Exception {
        Path source = writeSinglePagePdf("source.pdf");
        Path signed = writeSinglePagePdf("signed.pdf");
        EmailData emailData = emailDataWith(source);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        EmailLog emailLog = new EmailLog();
        DocumentManifest manifest = fakeManifest();
        EmailManager emailManager = createEmailManager(loggedInInfo, emailLog, attachment -> manifest);

        try (MockedStatic<PDFSigningConfig> config = mockStatic(PDFSigningConfig.class, CALLS_REAL_METHODS);
                MockedStatic<PDFSigningUtil> signer = mockStatic(PDFSigningUtil.class);
                MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS)) {
            config.when(PDFSigningConfig::fromCarlosProperties).thenReturn(PdfSigningTestSupport.stubbedEnabledConfig());
            paths.when(() -> PathValidationUtils.resolveTrustedPath(any())).thenAnswer(call -> call.getArgument(0));
            signer.when(() -> PDFSigningUtil.signPDF(eq(source), any(PDFSigningConfig.class), isNull(), eq(manifest)))
                    .thenReturn(signed);

            emailManager.signAttachments(emailData, loggedInInfo, emailLog);

            signer.verify(() -> PDFSigningUtil.signPDF(eq(source), any(PDFSigningConfig.class), isNull(), eq(manifest)));
        } finally {
            closeWorkingDirectory(emailData);
        }
    }

    @Test
    @DisplayName("should abort the send when the manifests cannot be prepared")
    void shouldAbortSend_whenManifestsCannotBePrepared() throws Exception {
        Path source = writeSinglePagePdf("source.pdf");
        EmailData emailData = emailDataWith(source);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        EmailLog emailLog = new EmailLog();
        EmailManager emailManager = createEmailManager(loggedInInfo, emailLog, null);

        try (MockedStatic<PDFSigningConfig> config = mockStatic(PDFSigningConfig.class, CALLS_REAL_METHODS);
                MockedStatic<PDFSigningUtil> signer = mockStatic(PDFSigningUtil.class)) {
            config.when(PDFSigningConfig::fromCarlosProperties).thenReturn(PdfSigningTestSupport.stubbedEnabledConfig());

            // Signing was asked for with a manifest: never send the PDFs without it.
            assertThatThrownBy(() -> emailManager.signAttachments(emailData, loggedInInfo, emailLog))
                    .isInstanceOf(EmailSendingException.class)
                    .hasMessage("Failed to sign email PDF attachment");
            signer.verifyNoInteractions();
            assertThat(emailData.getAttachments().get(0).getFilePath()).isEqualTo(source.toString());
        } finally {
            closeWorkingDirectory(emailData);
        }
    }

    @Test
    @DisplayName("should embed the manifest in a password-protected PDF through the real pipeline")
    void shouldEmbedManifestInEncryptedPdf_throughRealPipeline() throws Exception {
        Path source = writeSinglePagePdf("lab-result.pdf");
        EmailData emailData = emailDataWith(source);
        emailData.setIsEncrypted(true);
        emailData.setIsAttachmentEncrypted(true);
        emailData.setPassword("correct horse battery staple");
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        EmailLog emailLog = new EmailLog();
        EmailManager emailManager = createEmailManager(loggedInInfo, emailLog, attachment -> fakeManifest());
        PDFSigningConfig realConfig = PdfSigningTestSupport.enabledRsaConfig(tempDir.resolve("pdf-signing.p12"));

        try (MockedStatic<PDFSigningConfig> config = mockStatic(PDFSigningConfig.class, CALLS_REAL_METHODS)) {
            config.when(PDFSigningConfig::fromCarlosProperties).thenReturn(realConfig);

            emailManager.encryptEmail(emailData);
            emailManager.signAttachments(emailData, loggedInInfo, emailLog);

            Path sent = Path.of(emailData.getAttachments().get(0).getFilePath());
            try (PDDocument opened = Loader.loadPDF(sent.toFile(), "correct horse battery staple")) {
                assertThat(opened.getDocumentCatalog().getNames().getEmbeddedFiles().getNames())
                        .containsOnlyKeys(DocumentManifest.FILE_NAME);
            }
            assertThat(PdfSigningTestSupport.verifyDetachedSignature(sent, "correct horse battery staple")).isTrue();
        } finally {
            closeWorkingDirectory(emailData);
        }
    }

    @Test
    @DisplayName("should audit each signed attachment with its record, key and document ID")
    void shouldAuditEachSignedAttachment_whenSendPassesItsContext() throws Exception {
        Path source = writeSinglePagePdf("source.pdf");
        Path signed = writeSinglePagePdf("signed.pdf");
        EmailData emailData = emailDataWith(source);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        EmailLog emailLog = new EmailLog();
        ReflectionTestUtils.setField(emailLog, "id", 71);
        Demographic patient = new Demographic();
        patient.setDemographicNo(1001);
        emailLog.setDemographic(patient);
        String keyId = "FAKE-" + "k".repeat(59);
        String documentId = "FAKE-" + "d".repeat(59);
        DocumentManifest manifest = new DocumentManifest(documentId, 1, 1_792_454_400L, null, "FAKE Demo Clinic",
                "FAKE-Provider, Demo", null, "FAKE-Manifest-Patient, Jo", "1985-06-15", "FAKE CBC", "lab_report", null);
        String expectedAudit = "emailLogId=71&source=doc&recordId=1&kid=" + keyId
                + "&documentId=" + documentId + "&version=1&encrypted=false";
        EmailManager emailManager = createEmailManager(loggedInInfo, emailLog, attachment -> manifest);

        try (Connection auditDatabase = DriverManager.getConnection("jdbc:h2:mem:pdfSigningAudit");
                MockedStatic<PDFSigningConfig> config = mockStatic(PDFSigningConfig.class, CALLS_REAL_METHODS);
                MockedStatic<PDFSigningUtil> signer = mockStatic(PDFSigningUtil.class);
                MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS)) {
            config.when(PDFSigningConfig::fromCarlosProperties).thenReturn(PdfSigningTestSupport.stubbedEnabledConfig());
            paths.when(() -> PathValidationUtils.resolveTrustedPath(any())).thenAnswer(call -> call.getArgument(0));
            signer.when(() -> PDFSigningUtil.signingKeyId(any(PDFSigningConfig.class))).thenReturn(keyId);
            signer.when(() -> PDFSigningUtil.signPDF(any(Path.class), any(PDFSigningConfig.class), any(), any()))
                    .thenReturn(signed);

            // Exercise the real schema boundary: contentId is VARCHAR(80), while data is TEXT.
            // A mock-only assertion previously accepted a payload the database could not retain.
            try (var statement = auditDatabase.createStatement()) {
                statement.execute("CREATE TABLE signingAudit (contentId VARCHAR(80), data CLOB)");
            }
            logActionMock.when(() -> LogAction.addLog(eq(loggedInInfo), eq("EmailManager.signAttachment"),
                    eq("Email"), any(), eq("1001"), any())).thenAnswer(invocation -> {
                        try (var insert = auditDatabase.prepareStatement("INSERT INTO signingAudit VALUES (?, ?)")) {
                            insert.setString(1, invocation.getArgument(3));
                            insert.setString(2, invocation.getArgument(5));
                            insert.executeUpdate();
                        }
                        return null;
                    });

            emailManager.signAttachments(emailData, loggedInInfo, emailLog);

            // An opaque document ID is traced back to its record through this entry; no names or content.
            logActionMock.verify(() -> LogAction.addLog(loggedInInfo, "EmailManager.signAttachment", "Email",
                    "71", "1001", expectedAudit));
            try (var statement = auditDatabase.createStatement();
                    var row = statement.executeQuery("SELECT contentId, data FROM signingAudit")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("contentId")).isEqualTo("71");
                assertThat(row.getString("data")).isEqualTo(expectedAudit);
                assertThat(row.next()).isFalse();
            }
        } finally {
            closeWorkingDirectory(emailData);
        }
    }

    @Test
    @DisplayName("should not audit or read the key ID when signing without a send context")
    void shouldNotAudit_withoutASendContext() throws Exception {
        Path source = writeSinglePagePdf("source.pdf");
        Path signed = writeSinglePagePdf("signed.pdf");
        EmailData emailData = emailDataWith(source);

        try (MockedStatic<PDFSigningConfig> config = mockStatic(PDFSigningConfig.class, CALLS_REAL_METHODS);
                MockedStatic<PDFSigningUtil> signer = mockStatic(PDFSigningUtil.class);
                MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS)) {
            config.when(PDFSigningConfig::fromCarlosProperties).thenReturn(PdfSigningTestSupport.stubbedEnabledConfig());
            paths.when(() -> PathValidationUtils.resolveTrustedPath(any())).thenAnswer(call -> call.getArgument(0));
            signer.when(() -> PDFSigningUtil.signPDF(any(Path.class), any(PDFSigningConfig.class), any(), any()))
                    .thenReturn(signed);

            createEmailManager().signAttachments(emailData);

            logActionMock.verify(() -> LogAction.addLog(any(LoggedInInfo.class), any(), any(), any(), any(), any()),
                    org.mockito.Mockito.never());
            signer.verify(() -> PDFSigningUtil.signingKeyId(any()), org.mockito.Mockito.never());
        } finally {
            closeWorkingDirectory(emailData);
        }
    }

    private static DocumentManifest fakeManifest() {
        return new DocumentManifest("FAKE-document-id", 1, 1_792_454_400L, null, "FAKE Demo Clinic",
                "FAKE-Provider, Demo", null, "FAKE-Manifest-Patient, Jo", "1985-06-15", "FAKE CBC", "lab_report", null);
    }

    /**
     * An EmailManager whose composer returns {@code manifests} for this send, or fails as an
     * invalid document ID secret does when {@code manifests} is null.
     */
    private EmailManager createEmailManager(LoggedInInfo loggedInInfo, EmailLog emailLog,
            EmailDocumentManifestComposer.AttachmentManifests manifests) {
        EmailDocumentManifestComposer composer = mock(EmailDocumentManifestComposer.class);
        if (manifests == null) {
            when(composer.forSend(loggedInInfo, emailLog))
                    .thenThrow(new IllegalStateException("pdf.signing.document_id.secret is not base64"));
        } else {
            when(composer.forSend(loggedInInfo, emailLog)).thenReturn(manifests);
        }
        EmailManager emailManager = createEmailManager();
        ReflectionTestUtils.setField(emailManager, "documentManifestComposer", composer);
        return emailManager;
    }

    private EmailData emailDataWith(Path pdf) {
        EmailData emailData = new EmailData();
        emailData.setAttachments(new ArrayList<>(List.of(
                new EmailAttachment(pdf.getFileName().toString(), pdf.toString(), DocumentType.DOC, 1))));
        return emailData;
    }

    private static PDFSigningConfig disabledConfig() {
        return new PDFSigningConfig(false, null, null, null, null, null, null, null, null, null);
    }

    private Path writeSinglePagePdf(String name) throws IOException {
        return PdfSigningTestSupport.writeSinglePagePdf(tempDir.resolve(name));
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
