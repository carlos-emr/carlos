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

package io.github.carlos_emr.carlos.email.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.test.util.PdfSigningTestSupport;
import io.github.carlos_emr.carlos.utility.DocumentManifest;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFEncryptionUtil;
import io.github.carlos_emr.carlos.utility.PDFSigningConfig;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies the per-attachment sign, adopt, audit and clean-up sequence of
 * {@link PdfAttachmentSigningService}. The signature itself is covered by
 * {@code PDFSigningUtilUnitTest}, so the injected {@link PdfSignatureService} is a mock here; the
 * real encrypt, sign and adopt sequence runs end to end in
 * {@code EmailManagerSignedPdfPipelineUnitTest}. Moved from {@code EmailManagerPdfSigningUnitTest}
 * without its static mocks of the signing configuration, signer and path utilities
 * (carlos-emr/carlos#3851).
 *
 * @since 2026-09-21
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@Tag("security")
@DisplayName("PdfAttachmentSigningService")
class PdfAttachmentSigningServiceUnitTest extends CarlosUnitTestBase {

    private static final String SIGNING_FAILURE = "Failed to sign email PDF attachment";

    @TempDir
    Path tempDir;

    private PdfSignatureService signer;
    private EmailDocumentManifestComposer composer;
    private PdfAttachmentSigningService service;

    @BeforeEach
    void setUpService() {
        signer = mock(PdfSignatureService.class);
        composer = mock(EmailDocumentManifestComposer.class);
        service = new PdfAttachmentSigningService(composer, signer);
    }

    @Test
    @DisplayName("should leave attachments untouched when signing is disabled")
    void shouldLeaveAttachmentsUntouched_whenSigningIsDisabled() throws Exception {
        Path source = writeSinglePagePdf("source.pdf");
        EmailData emailData = emailDataWith(source);
        when(signer.currentConfiguration()).thenReturn(PdfSigningTestSupport.disabledConfig());

        service.signAttachments(emailData, mock(LoggedInInfo.class), new EmailLog());

        assertThat(emailData.getAttachments().get(0).getFilePath()).isEqualTo(source.toString());
        // Signing is disabled and this send is unencrypted, so nothing has created one.
        assertThat(emailData.getWorkingDirectory()).isNull();
        verify(signer, never()).sign(any(), any(), any(), any());
        verify(signer, never()).signingKeyId(any());
        verifyNoInteractions(composer);
    }

    @Test
    @DisplayName("should treat a missing configuration as signing disabled")
    void shouldLeaveAttachmentsUntouched_whenConfigurationIsMissing() throws Exception {
        Path source = writeSinglePagePdf("source.pdf");
        EmailData emailData = emailDataWith(source);
        when(signer.currentConfiguration()).thenReturn(null);

        service.signAttachments(emailData, null, null);

        assertThat(emailData.getAttachments().get(0).getFilePath()).isEqualTo(source.toString());
        assertThat(emailData.getWorkingDirectory()).isNull();
        verify(signer, never()).sign(any(), any(), any(), any());
    }

    @Test
    @DisplayName("should not read the configuration when the email has no attachments")
    void shouldNotReadConfiguration_whenEmailHasNoAttachments() throws Exception {
        EmailData emailData = new EmailData();

        service.signAttachments(emailData, mock(LoggedInInfo.class), new EmailLog());

        verifyNoInteractions(signer, composer);
        assertThat(emailData.getWorkingDirectory()).isNull();
    }

    @Test
    @DisplayName("should adopt each signed PDF into the working directory when signing is enabled")
    void shouldAdoptEachSignedPdf_whenSigningIsEnabled() throws Exception {
        Path source = writeSinglePagePdf("source.pdf");
        Path signed = writeSinglePagePdf("signed.pdf");
        EmailData emailData = emailDataWith(source);
        when(signer.currentConfiguration()).thenReturn(PdfSigningTestSupport.stubbedEnabledConfig());
        // Unencrypted send: no owner password may be handed to the signer, and a send with no
        // context embeds no manifest.
        when(signer.sign(eq(source.toRealPath()), any(PDFSigningConfig.class), isNull(), isNull()))
                .thenReturn(signed);

        try {
            service.signAttachments(emailData, null, null);

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
        when(signer.currentConfiguration()).thenReturn(PdfSigningTestSupport.stubbedEnabledConfig());
        when(signer.sign(any(Path.class), any(PDFSigningConfig.class), any(), any())).thenReturn(signed);

        try {
            service.signAttachments(emailData, null, null);

            // An encrypted PDF can only be modified with its owner password.
            verify(signer).sign(eq(source.toRealPath()), any(PDFSigningConfig.class),
                    eq("correct horse battery staple"), isNull());
        } finally {
            closeWorkingDirectory(emailData);
        }
    }

    @Test
    @DisplayName("should abort the send when an attachment cannot be signed")
    void shouldAbortSend_whenAttachmentCannotBeSigned() throws Exception {
        Path source = writeSinglePagePdf("source.pdf");
        EmailData emailData = emailDataWith(source);
        when(signer.currentConfiguration()).thenReturn(PdfSigningTestSupport.stubbedEnabledConfig());
        when(signer.sign(any(Path.class), any(PDFSigningConfig.class), any(), any()))
                .thenThrow(new IOException("keystore /etc/carlos-emr/pdf-signing.p12 unreadable"));

        try {
            // Fail closed: signing is enabled, so an unsigned attachment must never be delivered.
            assertThatThrownBy(() -> service.signAttachments(emailData, null, null))
                    .isInstanceOf(EmailSendingException.class)
                    .hasMessage(SIGNING_FAILURE)
                    .hasCauseInstanceOf(IOException.class);
            assertThat(emailData.getAttachments().get(0).getFilePath()).isEqualTo(source.toString());
        } finally {
            closeWorkingDirectory(emailData);
        }
    }

    @Test
    @DisplayName("should stop at the first attachment that cannot be signed")
    void shouldStopAtFirstFailure_whenALaterAttachmentWouldSign() throws Exception {
        Path first = writeSinglePagePdf("first.pdf");
        Path second = writeSinglePagePdf("second.pdf");
        EmailData emailData = emailDataWith(first);
        emailData.getAttachments().add(new EmailAttachment("second.pdf", second.toString(), DocumentType.DOC, 2));
        when(signer.currentConfiguration()).thenReturn(PdfSigningTestSupport.stubbedEnabledConfig());
        when(signer.sign(eq(first.toRealPath()), any(PDFSigningConfig.class), any(), any()))
                .thenThrow(new IOException("FAKE signing failure"));

        try {
            assertThatThrownBy(() -> service.signAttachments(emailData, null, null))
                    .isInstanceOf(EmailSendingException.class)
                    .hasMessage(SIGNING_FAILURE);
            // No partially signed set: the second attachment is never signed.
            verify(signer, never()).sign(eq(second.toRealPath()), any(), any(), any());
        } finally {
            closeWorkingDirectory(emailData);
        }
    }

    @Test
    @DisplayName("should delete the signed PDF when the working directory cannot adopt it")
    void shouldDeleteSignedPdf_whenWorkingDirectoryCannotAdoptIt() throws Exception {
        Path source = writeSinglePagePdf("source.pdf");
        // A real secure temp file, as the signer produces: it holds the patient's document, and if
        // adoption fails nothing else would ever remove it.
        Path signed = PathValidationUtils.createSecureTempFile("signedPDF_test", ".pdf").toPath();
        EmailData emailData = emailDataWith(source);
        EmailComposeWorkingDirectory closedDirectory = EmailComposeWorkingDirectory.create();
        closedDirectory.close();
        emailData.setWorkingDirectory(closedDirectory);
        when(signer.currentConfiguration()).thenReturn(PdfSigningTestSupport.stubbedEnabledConfig());
        when(signer.sign(any(Path.class), any(PDFSigningConfig.class), any(), any())).thenReturn(signed);

        try {
            assertThatThrownBy(() -> service.signAttachments(emailData, null, null))
                    .isInstanceOf(EmailSendingException.class)
                    .hasMessage(SIGNING_FAILURE);

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
        when(signer.currentConfiguration()).thenReturn(PdfSigningTestSupport.stubbedEnabledConfig());
        when(signer.sign(any(Path.class), any(PDFSigningConfig.class), any(), any()))
                .thenThrow(new ClassCastException("malformed dictionary"));

        try {
            // Escaping as a RuntimeException would skip the send's failure handling and strand the
            // EmailLog at PENDING behind a 500.
            assertThatThrownBy(() -> service.signAttachments(emailData, null, null))
                    .isInstanceOf(EmailSendingException.class)
                    .hasMessage(SIGNING_FAILURE)
                    .hasCauseInstanceOf(ClassCastException.class);
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
        when(composer.forSend(loggedInInfo, emailLog)).thenReturn(attachment -> manifest);
        when(signer.currentConfiguration()).thenReturn(PdfSigningTestSupport.stubbedEnabledConfig());
        when(signer.signingKeyId(any(PDFSigningConfig.class))).thenReturn("FAKE-key-id");
        when(signer.sign(eq(source.toRealPath()), any(PDFSigningConfig.class), isNull(), eq(manifest)))
                .thenReturn(signed);

        try {
            service.signAttachments(emailData, loggedInInfo, emailLog);

            verify(signer).sign(eq(source.toRealPath()), any(PDFSigningConfig.class), isNull(), eq(manifest));
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
        when(composer.forSend(loggedInInfo, emailLog))
                .thenThrow(new IllegalStateException("pdf.signing.document_id.secret is not base64"));
        when(signer.currentConfiguration()).thenReturn(PdfSigningTestSupport.stubbedEnabledConfig());

        try {
            // Signing was asked for with a manifest: never send the PDFs without it.
            assertThatThrownBy(() -> service.signAttachments(emailData, loggedInInfo, emailLog))
                    .isInstanceOf(EmailSendingException.class)
                    .hasMessage(SIGNING_FAILURE);
            verify(signer, never()).sign(any(), any(), any(), any());
            assertThat(emailData.getAttachments().get(0).getFilePath()).isEqualTo(source.toString());
        } finally {
            closeWorkingDirectory(emailData);
        }
    }

    @Test
    @DisplayName("should abort the send before signing when the signing key cannot be read for the audit")
    void shouldAbortSendBeforeSigning_whenSigningKeyIdCannotBeRead() throws Exception {
        Path source = writeSinglePagePdf("source.pdf");
        EmailData emailData = emailDataWith(source);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        EmailLog emailLog = new EmailLog();
        when(composer.forSend(loggedInInfo, emailLog))
                .thenReturn(EmailDocumentManifestComposer.AttachmentManifests.NONE);
        when(signer.currentConfiguration()).thenReturn(PdfSigningTestSupport.stubbedEnabledConfig());
        when(signer.signingKeyId(any(PDFSigningConfig.class)))
                .thenThrow(new IOException("Failed to load PDF signing key material"));

        try {
            // An unreadable keystore fails the send here, as signing would, so no signed PDF is
            // ever sent without its audit entry.
            assertThatThrownBy(() -> service.signAttachments(emailData, loggedInInfo, emailLog))
                    .isInstanceOf(EmailSendingException.class)
                    .hasMessage(SIGNING_FAILURE);
            verify(signer, never()).sign(any(), any(), any(), any());
            assertThat(emailData.getAttachments().get(0).getFilePath()).isEqualTo(source.toString());
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
        when(composer.forSend(loggedInInfo, emailLog)).thenReturn(attachment -> manifest);
        when(signer.currentConfiguration()).thenReturn(PdfSigningTestSupport.stubbedEnabledConfig());
        when(signer.signingKeyId(any(PDFSigningConfig.class))).thenReturn(keyId);
        when(signer.sign(any(Path.class), any(PDFSigningConfig.class), any(), any())).thenReturn(signed);

        try (Connection auditDatabase = DriverManager.getConnection("jdbc:h2:mem:pdfSigningAudit")) {
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

            service.signAttachments(emailData, loggedInInfo, emailLog);

            // An opaque document ID is traced back to its record through this entry; no names or
            // content. The action name is unchanged from when signing lived in EmailManager.
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
    @DisplayName("should audit the actual state of clear documents and a protected message in the same send")
    void shouldAuditEachPdfsActualEncryption_whenTheMessageAndAttachmentsDiffer() throws Exception {
        Path clear = writeSinglePagePdf("clear-document.pdf");
        String password = "FAKE mixed-send PDF passphrase";
        Path protectedMessage = PDFEncryptionUtil.encryptPDF(writeSinglePagePdf("message.pdf"), password);
        EmailData emailData = emailDataWith(clear);
        emailData.setIsEncrypted(true);
        emailData.setIsAttachmentEncrypted(false);
        emailData.setPassword(password);
        // The message PDF's sentinel record is the one EmailManager.createMessageAttachment uses.
        emailData.getAttachments().add(new EmailAttachment("message.pdf", protectedMessage.toString(), DocumentType.DOC, -1));
        LoggedInInfo actor = mock(LoggedInInfo.class);
        EmailLog emailLog = new EmailLog();
        ReflectionTestUtils.setField(emailLog, "id", 71);
        Demographic patient = new Demographic();
        patient.setDemographicNo(1001);
        emailLog.setDemographic(patient);
        String keyId = "FAKE-" + "k".repeat(59);
        when(composer.forSend(actor, emailLog)).thenReturn(attachment -> null);
        when(signer.currentConfiguration()).thenReturn(PdfSigningTestSupport.stubbedEnabledConfig());
        when(signer.signingKeyId(any(PDFSigningConfig.class))).thenReturn(keyId);
        // The crypto signature is covered by the real-pipeline cases; preserve real PDF bytes
        // here so the audit must distinguish the two actual protection states itself.
        when(signer.sign(any(Path.class), any(PDFSigningConfig.class), any(), any()))
                .thenAnswer(call -> call.getArgument(0));

        try {
            service.signAttachments(emailData, actor, emailLog);

            String prefix = "emailLogId=71&source=doc&recordId=";
            String suffix = "&kid=" + keyId + "&documentId=&version=&encrypted=";
            logActionMock.verify(() -> LogAction.addLog(actor, "EmailManager.signAttachment", "Email",
                    "71", "1001", prefix + "1" + suffix + "false"));
            logActionMock.verify(() -> LogAction.addLog(actor, "EmailManager.signAttachment", "Email",
                    "71", "1001", prefix + "-1" + suffix + "true"));
            try (PDDocument opened = Loader.loadPDF(Path.of(emailData.getAttachments().get(0).getFilePath()).toFile(), password)) {
                assertThat(opened.isEncrypted()).isFalse();
            }
            try (PDDocument opened = Loader.loadPDF(Path.of(emailData.getAttachments().get(1).getFilePath()).toFile(), password)) {
                assertThat(opened.isEncrypted()).isTrue();
            }
        } finally {
            closeWorkingDirectory(emailData);
            Files.deleteIfExists(protectedMessage);
        }
    }

    @Test
    @DisplayName("should not audit, read the key ID or build manifests when signing without a send context")
    void shouldNotAudit_withoutASendContext() throws Exception {
        Path source = writeSinglePagePdf("source.pdf");
        Path signed = writeSinglePagePdf("signed.pdf");
        EmailData emailData = emailDataWith(source);
        when(signer.currentConfiguration()).thenReturn(PdfSigningTestSupport.stubbedEnabledConfig());
        when(signer.sign(any(Path.class), any(PDFSigningConfig.class), any(), any())).thenReturn(signed);

        try {
            service.signAttachments(emailData, null, null);

            logActionMock.verify(() -> LogAction.addLog(any(LoggedInInfo.class), any(), any(), any(), any(), any()),
                    never());
            verify(signer, never()).signingKeyId(any());
            verifyNoInteractions(composer);
        } finally {
            closeWorkingDirectory(emailData);
        }
    }

    private static DocumentManifest fakeManifest() {
        return new DocumentManifest("FAKE-document-id", 1, 1_792_454_400L, null, "FAKE Demo Clinic",
                "FAKE-Provider, Demo", null, "FAKE-Manifest-Patient, Jo", "1985-06-15", "FAKE CBC", "lab_report", null);
    }

    private EmailData emailDataWith(Path pdf) {
        EmailData emailData = new EmailData();
        emailData.setAttachments(new ArrayList<>(List.of(
                new EmailAttachment(pdf.getFileName().toString(), pdf.toString(), DocumentType.DOC, 1))));
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
}
