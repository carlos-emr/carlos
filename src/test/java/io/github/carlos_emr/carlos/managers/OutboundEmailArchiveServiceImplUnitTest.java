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

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.EmailLogDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDeletionDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveLegalHoldEventDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchive;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchiveAttachment;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchiveDeletion;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchiveLegalHoldEvent;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveAttachmentDto;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveDto;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("OutboundEmailArchiveServiceImpl")
@Tag("unit")
class OutboundEmailArchiveServiceImplUnitTest extends CarlosUnitTestBase {

    private static final String PROVIDER_NO = "999998";
    private static final byte[] RFC822_BYTES = "Subject: Test\r\n\r\nBody".getBytes(StandardCharsets.UTF_8);

    private DocumentManager documentManager;
    private EmailLogDao emailLogDao;
    private OutboundEmailArchiveDao outboundEmailArchiveDao;
    private OutboundEmailArchiveDeletionDao outboundEmailArchiveDeletionDao;
    private OutboundEmailArchiveLegalHoldEventDao outboundEmailArchiveLegalHoldEventDao;
    private CtlDocumentDao ctlDocumentDao;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private OutboundEmailArchiveServiceImpl service;

    @BeforeEach
    void setUp() {
        documentManager = mock(DocumentManager.class);
        emailLogDao = mock(EmailLogDao.class);
        outboundEmailArchiveDao = mock(OutboundEmailArchiveDao.class);
        outboundEmailArchiveDeletionDao = mock(OutboundEmailArchiveDeletionDao.class);
        outboundEmailArchiveLegalHoldEventDao = mock(OutboundEmailArchiveLegalHoldEventDao.class);
        ctlDocumentDao = mock(CtlDocumentDao.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        service = new OutboundEmailArchiveServiceImpl(documentManager, emailLogDao, outboundEmailArchiveDao, outboundEmailArchiveDeletionDao, outboundEmailArchiveLegalHoldEventDao, ctlDocumentDao, securityInfoManager);

        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
        allowControlledDeletion();
    }

    @AfterEach
    void tearDownTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("should store outbound artifact as patient eDoc and persist archive metadata")
    void shouldStoreArtifactAsPatientEdocAndPersistArchiveMetadata_whenRequestIsValid() throws Exception {
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);
        Document savedDocument = savedDocument();
        when(documentManager.createDocument(eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES)))
                .thenReturn(savedDocument);
        doAnswer(invocation -> {
            OutboundEmailArchive archive = invocation.getArgument(0);
            archive.setId(888);
            return null;
        }).when(outboundEmailArchiveDao).persist(any(OutboundEmailArchive.class));

        OutboundEmailArchive archive = service.archive(loggedInInfo, request);

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentManager).createDocument(eq(loggedInInfo), documentCaptor.capture(), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES));
        assertArchiveDocumentToCreate(documentCaptor.getValue());

        verify(outboundEmailArchiveDao).persist(archive);
        assertArchiveMetadata(archive, emailLog, savedDocument);
    }

    @Test
    @DisplayName("should load persisted email log before deriving archive demographics")
    void shouldLoadPersistedEmailLog_beforeDerivingArchiveDemographics() throws Exception {
        EmailLog requestedEmailLog = emailLog();
        requestedEmailLog.setDemographic(new Demographic(456));
        OutboundEmailArchiveDto request = archiveRequest(requestedEmailLog);
        EmailLog persistedEmailLog = emailLog();
        Document savedDocument = savedDocument();
        when(emailLogDao.find((Object) Integer.valueOf(44))).thenReturn(persistedEmailLog);
        when(documentManager.createDocument(eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES)))
                .thenReturn(savedDocument);

        OutboundEmailArchive archive = service.archive(loggedInInfo, request);

        verify(emailLogDao).find((Object) Integer.valueOf(44));
        verify(documentManager).createDocument(eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES));
        assertThat(archive.getEmailLog()).isSameAs(persistedEmailLog);
        assertThat(archive.getDemographic().getDemographicNo()).isEqualTo(123);
    }

    @Test
    @DisplayName("should choose file extension from media type with parameters")
    void shouldChooseFileExtension_whenMediaTypeHasParameters() throws Exception {
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);
        request.setContentType("Message/RFC822; charset=UTF-8");
        when(documentManager.createDocument(eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES)))
                .thenReturn(savedDocument());

        service.archive(loggedInInfo, request);

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentManager).createDocument(eq(loggedInInfo), documentCaptor.capture(), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES));
        assertThat(documentCaptor.getValue().getDocfilename()).startsWith("outbound-email-44-").endsWith(".eml");
    }

    @Test
    @DisplayName("should store the bare media type when MIME parameters would overflow the archive limit")
    void shouldStoreBareMediaType_whenMimeMetadataExceedsArchiveLimit() throws Exception {
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);
        // Truncating this at 100 chars would persist an unterminated quoted string
        // into document.contenttype, which every eDoc viewer reads back.
        request.setContentType("message/rfc822; name=\"" + "x".repeat(200) + "\"");
        when(documentManager.createDocument(
                eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES)))
                .thenReturn(savedDocument());

        OutboundEmailArchive archive = service.archive(loggedInInfo, request);

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentManager).createDocument(
                eq(loggedInInfo), documentCaptor.capture(), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES));
        assertThat(documentCaptor.getValue().getContenttype()).isEqualTo("message/rfc822");
        assertThat(archive.getContentType()).isEqualTo("message/rfc822");
    }

    @Test
    @DisplayName("should fall back to the default media type when the content type is only parameters")
    void shouldFallBackToDefaultMediaType_whenContentTypeIsOnlyParameters() throws Exception {
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);
        request.setContentType("; charset=UTF-8");
        when(documentManager.createDocument(
                eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES)))
                .thenReturn(savedDocument());

        OutboundEmailArchive archive = service.archive(loggedInInfo, request);

        assertThat(archive.getContentType()).isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("should use generated eDoc filename and retain caller filename as metadata")
    void shouldUseGeneratedEdocFilename_whenCallerFilenameProvided() throws Exception {
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);
        request.setFileName("Jane-Smith-1970-01-01-referral.eml");
        when(documentManager.createDocument(eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES)))
                .thenReturn(savedDocument());

        OutboundEmailArchive archive = service.archive(loggedInInfo, request);

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentManager).createDocument(eq(loggedInInfo), documentCaptor.capture(), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES));
        assertThat(documentCaptor.getValue().getDocfilename()).startsWith("outbound-email-44-").endsWith(".eml");
        assertThat(archive.getOriginalFileName()).isEqualTo("Jane-Smith-1970-01-01-referral.eml");
    }

    @Test
    @DisplayName("should truncate archive metadata without splitting Unicode surrogate pairs")
    void shouldTruncateArchiveMetadata_withoutSplittingUnicodeSurrogatePairs() throws Exception {
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);
        request.setProviderResponse("a".repeat(999) + "\uD83D\uDE00" + "trailing");
        when(documentManager.createDocument(eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES)))
                .thenReturn(savedDocument());

        OutboundEmailArchive archive = service.archive(loggedInInfo, request);

        assertThat(archive.getProviderResponse()).isEqualTo("a".repeat(999) + "\uD83D\uDE00");
        assertThat(archive.getProviderResponse().codePointCount(0, archive.getProviderResponse().length()))
                .isEqualTo(1000);
    }

    @Test
    @DisplayName("should delete final eDoc file when createDocument fails after filename normalization")
    void shouldDeleteFinalEdocFile_whenCreateDocumentFailsAfterFilenameNormalization(@TempDir Path documentDir) throws Exception {
        CarlosProperties props = CarlosProperties.getInstance();
        boolean hadDocumentDir = props.containsKey("DOCUMENT_DIR");
        Object originalDocumentDir = props.get("DOCUMENT_DIR");
        props.setProperty("DOCUMENT_DIR", documentDir.toString());

        try {
            EmailLog emailLog = emailLog();
            OutboundEmailArchiveDto request = archiveRequest(emailLog);
            AtomicReference<Path> createdFile = new AtomicReference<>();
            doAnswer(invocation -> {
                Document documentToCreate = invocation.getArgument(1);
                documentToCreate.setDocfilename("20260707120000_" + documentToCreate.getDocfilename());
                Path file = documentDir.resolve(documentToCreate.getDocfilename());
                Files.write(file, RFC822_BYTES);
                createdFile.set(file);
                throw new RuntimeException("document database failed");
            }).when(documentManager).createDocument(eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES));

            assertThatThrownBy(() -> service.archive(loggedInInfo, request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("document database failed");

            Path file = createdFile.get();
            assertThat(file).isNotNull();
            assertThat(file).doesNotExist();
            verify(outboundEmailArchiveDao, never()).persist(any(OutboundEmailArchive.class));
        } finally {
            if (hadDocumentDir) {
                props.put("DOCUMENT_DIR", originalDocumentDir);
            } else {
                props.remove("DOCUMENT_DIR");
            }
        }
    }

    @Test
    @DisplayName("should delete the archived artifact when the transaction rolls back")
    void shouldDeleteArchivedArtifact_whenTransactionRollsBack(@TempDir Path documentDir) throws Exception {
        withDocumentDir(documentDir, () -> {
            Path artifact = stageArchiveForCompletion(documentDir);

            runAfterCompletionSynchronizations(TransactionSynchronization.STATUS_ROLLED_BACK);

            assertThat(artifact).doesNotExist();
        });
    }

    @Test
    @DisplayName("should keep the archived artifact when the transaction completion status is unknown")
    void shouldKeepArchivedArtifact_whenCompletionStatusIsUnknown(@TempDir Path documentDir) throws Exception {
        withDocumentDir(documentDir, () -> {
            Path artifact = stageArchiveForCompletion(documentDir);

            // STATUS_UNKNOWN is heuristic or mixed completion: the archive row may well have
            // committed. Unlinking here would leave a row asserting an email was sent whose bytes
            // can never be verified against the tombstone hash again -- strictly worse than an
            // orphaned file, which is reconcilable.
            runAfterCompletionSynchronizations(TransactionSynchronization.STATUS_UNKNOWN);

            assertThat(artifact).exists();
        });
    }

    @Test
    @DisplayName("should resolve controlled deletion audit content before the transaction completes")
    void shouldResolveControlledDeletionAuditContent_beforeTransactionCompletes() {
        TransactionSynchronizationManager.initSynchronization();
        OutboundEmailArchive archive = archiveForDeletion();
        // Document maps its @Id on the field, so getId() on a lazy proxy initializes it rather
        // than reading the foreign key. Deferring that read into afterCommit would issue a SELECT
        // after the transaction closed; this mock fails exactly the way a closed EntityManager
        // would, so the test fails if the service resolves the audit content inside the lambda.
        Document document = mock(Document.class);
        when(document.getId()).thenReturn(321);
        archive.setDocument(document);
        stubArchiveLookup(archive);

        service.recordControlledDeletion(loggedInInfo, 888, "Patient requested cleanup");

        doThrow(new LazyInitializationException("could not initialize proxy - no Session"))
                .when(document).getId();
        runAfterCommitSynchronizations();

        logActionMock.verify(() -> LogAction.addLog(loggedInInfo,
                "OutboundEmailArchiveService.recordControlledDeletion",
                "Outbound email archive tombstone",
                "archiveId=888 documentNo=321",
                "123",
                ""));
    }

    @Test
    @DisplayName("should defer archive audit until transaction commit")
    void shouldDeferArchiveAudit_untilTransactionCommit() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);
        Document savedDocument = savedDocument();
        when(documentManager.createDocument(eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES)))
                .thenReturn(savedDocument);
        doAnswer(invocation -> {
            OutboundEmailArchive archive = invocation.getArgument(0);
            archive.setId(888);
            return null;
        }).when(outboundEmailArchiveDao).persist(any(OutboundEmailArchive.class));

        service.archive(loggedInInfo, request);

        logActionMock.verifyNoInteractions();
        runAfterCommitSynchronizations();
        logActionMock.verify(() -> LogAction.addLog(loggedInInfo,
                "OutboundEmailArchiveService.archive",
                "Outbound email archive",
                "archiveId=888 emailLogId=44 documentNo=321",
                "123",
                ""));
    }

    @Test
    @DisplayName("should strip MIME parameters from an attachment content type")
    void shouldStripMimeParameters_fromAttachmentContentType() throws Exception {
        // The raw value exceeds the 100-character column, so truncating it verbatim would
        // store a content type cut mid-parameter. Normalising first keeps the media type
        // intact and drops the parameters, matching how the main artifact is handled.
        byte[] attachmentBytes = "pdf bytes".getBytes(StandardCharsets.UTF_8);
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);
        OutboundEmailArchiveAttachmentDto attachmentRequest = new OutboundEmailArchiveAttachmentDto();
        Document attachmentDocument = attachmentDocument();
        attachmentRequest.setFileName("report.pdf");
        attachmentRequest.setContentType("application/pdf; name=\"" + "x".repeat(200) + "\"");
        attachmentRequest.setArtifactBytes(attachmentBytes);
        attachmentRequest.setSourceDocumentType("DOCUMENT");
        attachmentRequest.setSourceDocumentId(777);
        attachmentRequest.setDocument(attachmentDocument);
        request.addAttachment(attachmentRequest);

        when(ctlDocumentDao.findByDocumentNoAndModule(777, "demographic")).thenReturn(List.of(ctlDocument(123, 777)));
        when(documentManager.createDocument(eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES)))
                .thenReturn(savedDocument());

        OutboundEmailArchive archive = service.archive(loggedInInfo, request);

        assertThat(archive.getAttachments()).hasSize(1);
        assertThat(archive.getAttachments().get(0).getContentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("should calculate final attachment hashes from supplied bytes")
    void shouldCalculateAttachmentHashes_whenBytesSupplied() throws Exception {
        byte[] attachmentBytes = "encrypted pdf bytes".getBytes(StandardCharsets.UTF_8);
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);
        OutboundEmailArchiveAttachmentDto attachmentRequest = new OutboundEmailArchiveAttachmentDto();
        Document attachmentDocument = attachmentDocument();
        attachmentRequest.setFileName("message.pdf");
        attachmentRequest.setContentType("application/pdf");
        attachmentRequest.setArtifactBytes(attachmentBytes);
        attachmentRequest.setSourceDocumentType("DOCUMENT");
        attachmentRequest.setSourceDocumentId(777);
        attachmentRequest.setDocument(attachmentDocument);
        request.addAttachment(attachmentRequest);

        when(ctlDocumentDao.findByDocumentNoAndModule(777, "demographic")).thenReturn(List.of(ctlDocument(123, 777)));
        when(documentManager.createDocument(eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES)))
                .thenReturn(savedDocument());

        OutboundEmailArchive archive = service.archive(loggedInInfo, request);

        assertThat(archive.getAttachments()).hasSize(1);
        OutboundEmailArchiveAttachment attachment = archive.getAttachments().get(0);
        assertThat(attachment.getArchive()).isSameAs(archive);
        assertThat(attachment.getFileName()).isEqualTo("message.pdf");
        assertThat(attachment.getContentType()).isEqualTo("application/pdf");
        assertThat(attachment.getSha256Hash()).isEqualTo(sha256Hex(attachmentBytes));
        assertThat(attachment.getByteSize()).isEqualTo((long) attachmentBytes.length);
        assertThat(attachment.getSourceDocumentType()).isEqualTo("DOCUMENT");
        assertThat(attachment.getSourceDocumentId()).isEqualTo(777);
        assertThat(attachment.getDocument()).isSameAs(attachmentDocument);
        assertThat(attachment.getLastUpdateUser()).isEqualTo(PROVIDER_NO);
    }

    @Test
    @DisplayName("should archive attachment metadata without document bytes or source document")
    void shouldArchiveAttachmentMetadata_whenNoDocumentOrBytesSupplied() throws Exception {
        byte[] attachmentBytes = "external attachment bytes".getBytes(StandardCharsets.UTF_8);
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);
        OutboundEmailArchiveAttachmentDto attachmentRequest = new OutboundEmailArchiveAttachmentDto();
        attachmentRequest.setFileName("external-result.pdf");
        attachmentRequest.setContentType("application/pdf");
        attachmentRequest.setSha256Hash(sha256Hex(attachmentBytes));
        attachmentRequest.setByteSize((long) attachmentBytes.length);
        attachmentRequest.setSourceDocumentType("EXTERNAL");
        attachmentRequest.setSourceDocumentId(9901);
        request.addAttachment(attachmentRequest);

        when(documentManager.createDocument(eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES)))
                .thenReturn(savedDocument());

        OutboundEmailArchive archive = service.archive(loggedInInfo, request);

        assertThat(archive.getAttachments()).hasSize(1);
        OutboundEmailArchiveAttachment attachment = archive.getAttachments().get(0);
        assertThat(attachment.getArchive()).isSameAs(archive);
        assertThat(attachment.getFileName()).isEqualTo("external-result.pdf");
        assertThat(attachment.getContentType()).isEqualTo("application/pdf");
        assertThat(attachment.getSha256Hash()).isEqualTo(sha256Hex(attachmentBytes));
        assertThat(attachment.getByteSize()).isEqualTo((long) attachmentBytes.length);
        assertThat(attachment.getSourceDocumentType()).isEqualTo("EXTERNAL");
        assertThat(attachment.getSourceDocumentId()).isEqualTo(9901);
        assertThat(attachment.getDocument()).isNull();
        assertThat(attachment.getLastUpdateUser()).isEqualTo(PROVIDER_NO);
        verifyNoInteractions(ctlDocumentDao);
    }

    @Test
    @DisplayName("should reject attachment document from a different demographic before storing the eDoc")
    void shouldRejectAttachmentDocumentFromDifferentDemographic_beforeStoringEdoc() {
        byte[] attachmentBytes = "encrypted pdf bytes".getBytes(StandardCharsets.UTF_8);
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);
        OutboundEmailArchiveAttachmentDto attachmentRequest = new OutboundEmailArchiveAttachmentDto();
        attachmentRequest.setFileName("message.pdf");
        attachmentRequest.setContentType("application/pdf");
        attachmentRequest.setArtifactBytes(attachmentBytes);
        attachmentRequest.setDocument(attachmentDocument());
        request.addAttachment(attachmentRequest);
        when(ctlDocumentDao.findByDocumentNoAndModule(777, "demographic")).thenReturn(List.of(ctlDocument(456, 777)));

        assertThatThrownBy(() -> service.archive(loggedInInfo, request))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("attachment document");
        verifyNoInteractions(documentManager);
    }

    @Test
    @DisplayName("should reject attachment whose sourceDocumentId contradicts its linked document")
    void shouldRejectAttachment_whenSourceDocumentIdContradictsLinkedDocument() {
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);
        OutboundEmailArchiveAttachmentDto attachmentRequest = new OutboundEmailArchiveAttachmentDto();
        attachmentRequest.setFileName("message.pdf");
        attachmentRequest.setContentType("application/pdf");
        attachmentRequest.setArtifactBytes("encrypted pdf bytes".getBytes(StandardCharsets.UTF_8));
        attachmentRequest.setDocument(attachmentDocument());
        // documentNo 777 is demographic-checked; sourceDocumentId is not and has no FK,
        // so a disagreeing pair would record provenance from a document we never verified.
        attachmentRequest.setSourceDocumentId(9901);
        request.addAttachment(attachmentRequest);
        when(ctlDocumentDao.findByDocumentNoAndModule(777, "demographic")).thenReturn(List.of(ctlDocument(123, 777)));

        assertThatThrownBy(() -> service.archive(loggedInInfo, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceDocumentId does not match");
        verifyNoInteractions(documentManager);
    }

    @Test
    @DisplayName("should reject attachment bytes without a persisted document before storing the eDoc")
    void shouldRejectAttachmentBytesWithoutPersistedDocument_beforeStoringEdoc() {
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);
        OutboundEmailArchiveAttachmentDto attachmentRequest = new OutboundEmailArchiveAttachmentDto();
        attachmentRequest.setFileName("message.pdf");
        attachmentRequest.setContentType("application/pdf");
        attachmentRequest.setArtifactBytes("encrypted pdf bytes".getBytes(StandardCharsets.UTF_8));
        request.addAttachment(attachmentRequest);

        assertThatThrownBy(() -> service.archive(loggedInInfo, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Persisted attachment document is required");
        verifyNoInteractions(documentManager);
    }

    @Test
    @DisplayName("should reject invalid attachment metadata before storing the eDoc")
    void shouldRejectInvalidAttachmentMetadata_beforeStoringEdoc() {
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);
        OutboundEmailArchiveAttachmentDto attachmentRequest = new OutboundEmailArchiveAttachmentDto();
        attachmentRequest.setFileName("message.pdf");
        attachmentRequest.setContentType("application/pdf");
        attachmentRequest.setSha256Hash("not-a-sha256-hash");
        attachmentRequest.setByteSize(42L);
        request.addAttachment(attachmentRequest);

        assertThatThrownBy(() -> service.archive(loggedInInfo, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SHA-256 hash");
        verifyNoInteractions(documentManager);
    }

    @Test
    @DisplayName("should reject unpersisted email logs")
    void shouldRejectEmailArchive_whenEmailLogIsUnpersisted() {
        EmailLog emailLog = emailLog();
        injectDependency(emailLog, "id", null);
        OutboundEmailArchiveDto request = archiveRequest(emailLog);

        assertThatThrownBy(() -> service.archive(loggedInInfo, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Persisted EmailLog is required");
    }

    @Test
    @DisplayName("should reject archive when logged-in provider is missing")
    void shouldRejectArchive_whenLoggedInProviderMissing() {
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(" ");
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);

        assertThatThrownBy(() -> service.archive(loggedInInfo, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Provider number is required");
        verifyNoInteractions(documentManager);
    }

    @Test
    @DisplayName("should reject archive without eDoc write authority before reading the email log")
    void shouldRejectArchive_whenCallerLacksEdocWriteAuthority() {
        when(securityInfoManager.hasPrivilege(
                loggedInInfo, "_edoc", SecurityInfoManager.WRITE, null)).thenReturn(false);
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);

        assertThatThrownBy(() -> service.archive(loggedInInfo, request))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_edoc w");

        // The authority gate must precede the lookup: otherwise the "EmailLog not found"
        // message tells an unauthorized caller which email log ids exist.
        verify(emailLogDao, never()).find(any());
        verifyNoInteractions(documentManager);
    }

    @Test
    @DisplayName("should reject archive without patient access")
    void shouldRejectArchive_whenCallerCannotAccessPatientRecord() {
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 123)).thenReturn(false);
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);

        assertThatThrownBy(() -> service.archive(loggedInInfo, request))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("archive demographic");

        verifyNoInteractions(documentManager);
    }

    @Test
    @DisplayName("should mark archive deleted and persist tombstone")
    void shouldMarkArchiveDeletedAndPersistTombstone_whenDeletionAllowed() {
        OutboundEmailArchive archive = archiveForDeletion();
        stubArchiveLookup(archive);

        OutboundEmailArchiveDeletion deletion = service.recordControlledDeletion(loggedInInfo, 888, "Patient requested cleanup");

        verify(outboundEmailArchiveDao).findForUpdate(888);
        verify(outboundEmailArchiveDao).merge(archive);
        verify(outboundEmailArchiveDeletionDao).persist(deletion);
        // The concurrency guard the lock exists for: a plain find() anywhere before
        // findForUpdate leaves the row managed, so the locked read hands back pre-lock
        // legalHold/deleted and two concurrent callers can both succeed. Asserted on the
        // success path, because that is where a "simplification" would actually land.
        verify(outboundEmailArchiveDao, never()).find(any());

        assertThat(archive.isDeleted()).isTrue();
        assertThat(archive.getDeletedByProviderNo()).isEqualTo(PROVIDER_NO);
        assertThat(archive.getDeleteReason()).isEqualTo("Patient requested cleanup");
        assertThat(deletion.getArchive()).isSameAs(archive);
        assertThat(deletion.getEmailLog()).isSameAs(archive.getEmailLog());
        assertThat(deletion.getDemographic()).isSameAs(archive.getDemographic());
        assertThat(deletion.getDocument()).isSameAs(archive.getDocument());
        assertThat(deletion.getFileName()).isEqualTo("20260707120000_outbound-email-44.eml");
        assertThat(deletion.getSha256Hash()).isEqualTo(archive.getSha256Hash());
        assertThat(deletion.getDeletedByProviderNo()).isEqualTo(PROVIDER_NO);
        assertThat(archive.getDeletedAt()).isNotNull();
        assertThat(deletion.getDeletedAt()).isNotNull();
        assertThat(deletion.getDeletedAt()).isEqualTo(archive.getDeletedAt());
        assertThat(deletion.getDeleteReason()).isEqualTo("Patient requested cleanup");
        assertThat(deletion.getLastUpdateUser()).isEqualTo(PROVIDER_NO);
    }

    @Test
    @DisplayName("should trim controlled deletion reason before truncating")
    void shouldTrimControlledDeletionReason_beforeTruncating() {
        OutboundEmailArchive archive = archiveForDeletion();
        stubArchiveLookup(archive);

        OutboundEmailArchiveDeletion deletion = service.recordControlledDeletion(loggedInInfo, 888, " ".repeat(1000) + "Meaningful reason");

        assertThat(deletion.getDeleteReason()).isEqualTo("Meaningful reason");
        assertThat(archive.getDeleteReason()).isEqualTo("Meaningful reason");
    }

    @Test
    @DisplayName("should defer controlled deletion audit until transaction commit")
    void shouldDeferControlledDeletionAudit_untilTransactionCommit() {
        TransactionSynchronizationManager.initSynchronization();
        OutboundEmailArchive archive = archiveForDeletion();
        stubArchiveLookup(archive);

        service.recordControlledDeletion(loggedInInfo, 888, "Patient requested cleanup");

        logActionMock.verifyNoInteractions();
        runAfterCommitSynchronizations();
        logActionMock.verify(() -> LogAction.addLog(loggedInInfo,
                "OutboundEmailArchiveService.recordControlledDeletion",
                "Outbound email archive tombstone",
                "archiveId=888 documentNo=321",
                "123",
                ""));
    }

    @Test
    @DisplayName("should reject controlled deletion for a caller holding only eDoc write")
    void shouldRejectDeletion_whenCallerLacksEdocDeleteAuthority() {
        // _edoc w is the right needed to create an archive. If it also admitted deletion,
        // retiring an evidentiary record would be no harder than writing one.
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.edocdelete", SecurityInfoManager.WRITE, null)).thenReturn(false);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.WRITE, null)).thenReturn(true);

        assertThatThrownBy(() -> service.recordControlledDeletion(loggedInInfo, 888, "cleanup"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin.edocdelete w");

        // findForUpdate issues SELECT ... FOR UPDATE. An unauthorized caller must not be
        // able to take that row lock, nor probe archive ids via the "not found" message.
        verifyNoInteractions(outboundEmailArchiveDao);
        verifyNoInteractions(outboundEmailArchiveDeletionDao);
    }

    @Test
    @DisplayName("should reject controlled deletion without patient access")
    void shouldRejectDeletion_whenCallerCannotAccessPatientRecord() {
        OutboundEmailArchive archive = archiveForDeletion();
        stubArchiveLookup(archive);
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 123)).thenReturn(false);

        assertThatThrownBy(() -> service.recordControlledDeletion(loggedInInfo, 888, "cleanup"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("archive demographic");

        // The patient-record gate runs on an unlocked read, so a caller who holds
        // _admin.edocdelete but not this patient never reaches SELECT ... FOR UPDATE.
        verify(outboundEmailArchiveDao, never()).findForUpdate(any());
        // Also never loads the entity: doing so would poison the persistence context and
        // make the later locked read return stale state.
        verify(outboundEmailArchiveDao, never()).find(any());
        verify(outboundEmailArchiveDao, never()).merge(any(OutboundEmailArchive.class));
        verifyNoInteractions(outboundEmailArchiveDeletionDao);
    }

    @Test
    @DisplayName("should reject controlled deletion while the default legal hold is still active")
    void shouldRejectDeletion_whenLegalHoldIsActive() {
        // No releaseLegalHold call: a freshly created archive is on hold, so this is the
        // out-of-the-box behaviour rather than a state the test had to arrange.
        OutboundEmailArchive archive = archiveUnderLegalHold();
        stubArchiveLookup(archive);

        assertThatThrownBy(() -> service.recordControlledDeletion(loggedInInfo, 888, "cleanup"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("legal hold");

        verify(outboundEmailArchiveDao, never()).merge(any(OutboundEmailArchive.class));
        verifyNoInteractions(outboundEmailArchiveDeletionDao);
    }

    @Test
    @DisplayName("should place every new archive under legal hold")
    void shouldPlaceArchiveUnderLegalHold_whenCreated() throws Exception {
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);
        when(documentManager.createDocument(eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES)))
                .thenReturn(savedDocument());

        OutboundEmailArchive archive = service.archive(loggedInInfo, request);

        assertThat(archive.isLegalHold()).isTrue();
    }

    @Test
    @DisplayName("should release legal hold and record who did it")
    void shouldReleaseLegalHold_andRecordResponsibleProvider() {
        OutboundEmailArchive archive = archiveUnderLegalHold();
        stubArchiveLookup(archive);

        OutboundEmailArchiveLegalHoldEvent event =
                service.releaseLegalHold(loggedInInfo, 888, "  Counsel authorised release  ");

        verify(outboundEmailArchiveDao).merge(archive);
        verify(outboundEmailArchiveLegalHoldEventDao).persist(event);
        assertThat(archive.isLegalHold()).isFalse();
        assertThat(event.getAction()).isEqualTo(OutboundEmailArchiveLegalHoldEvent.ACTION_RELEASED);
        assertThat(event.getProviderNo()).isEqualTo(PROVIDER_NO);
        assertThat(event.getReason()).isEqualTo("Counsel authorised release");
        assertThat(event.getArchive()).isSameAs(archive);
        assertThat(event.getLastUpdateUser()).isEqualTo(PROVIDER_NO);
        // Same concurrency guard as the deletion path: no pre-lock find().
        verify(outboundEmailArchiveDao, never()).find(any());
    }

    @Test
    @DisplayName("should re-apply legal hold and record who did it")
    void shouldPlaceLegalHold_andRecordResponsibleProvider() {
        OutboundEmailArchive archive = archiveForDeletion();
        stubArchiveLookup(archive);

        OutboundEmailArchiveLegalHoldEvent event = service.placeLegalHold(loggedInInfo, 888, "Litigation opened");

        assertThat(archive.isLegalHold()).isTrue();
        assertThat(event.getAction()).isEqualTo(OutboundEmailArchiveLegalHoldEvent.ACTION_PLACED);
        assertThat(event.getProviderNo()).isEqualTo(PROVIDER_NO);
        verify(outboundEmailArchiveLegalHoldEventDao).persist(event);
        // Same concurrency guard as the deletion path: no pre-lock find().
        verify(outboundEmailArchiveDao, never()).find(any());
    }

    @Test
    @DisplayName("should reject a deletion when the locked row belongs to a different patient than the one authorized")
    void shouldRejectDeletion_whenLockedRowDemographicDiffersFromAuthorized() {
        // The two reads in lockArchiveForAuthorizedCaller are separate statements, so the row can
        // in principle change between the scalar demographic read the patient gate ran on and the
        // FOR UPDATE read that actually gets mutated. Drive them apart deliberately: authorize
        // patient 123, then hand back a locked row belonging to patient 456. Without the post-lock
        // re-check the service would retire another patient's archive under an authorization that
        // was never granted for it -- and no other test can tell, because stubArchiveLookup
        // derives both values from the same object.
        OutboundEmailArchive otherPatientsArchive = archiveForDeletion();
        otherPatientsArchive.setDemographic(new Demographic(456));
        when(outboundEmailArchiveDao.findDemographicNoById(888)).thenReturn(123);
        when(outboundEmailArchiveDao.findForUpdate(888)).thenReturn(otherPatientsArchive);

        assertThatThrownBy(() -> service.recordControlledDeletion(loggedInInfo, 888, "cleanup"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("archive demographic");

        assertThat(otherPatientsArchive.isDeleted()).isFalse();
        verify(outboundEmailArchiveDao, never()).merge(any(OutboundEmailArchive.class));
        verifyNoInteractions(outboundEmailArchiveDeletionDao);
    }

    @Test
    @DisplayName("should reject legal hold release without admin authority before locking the row")
    void shouldRejectLegalHoldRelease_whenCallerLacksAdminAuthority() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.edocdelete", SecurityInfoManager.WRITE, null)).thenReturn(false);

        assertThatThrownBy(() -> service.releaseLegalHold(loggedInInfo, 888, "cleanup"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin.edocdelete w");

        verifyNoInteractions(outboundEmailArchiveDao);
        verifyNoInteractions(outboundEmailArchiveLegalHoldEventDao);
    }

    @Test
    @DisplayName("should reject legal hold release without a reason")
    void shouldRejectLegalHoldRelease_whenReasonIsBlank() {
        assertThatThrownBy(() -> service.releaseLegalHold(loggedInInfo, 888, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Legal hold reason is required");

        verifyNoInteractions(outboundEmailArchiveDao);
        verifyNoInteractions(outboundEmailArchiveLegalHoldEventDao);
    }

    @Test
    @DisplayName("should reject releasing a legal hold that is not active")
    void shouldRejectLegalHoldRelease_whenNoHoldIsActive() {
        OutboundEmailArchive archive = archiveForDeletion();
        stubArchiveLookup(archive);

        assertThatThrownBy(() -> service.releaseLegalHold(loggedInInfo, 888, "cleanup"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not under legal hold");

        verifyNoInteractions(outboundEmailArchiveLegalHoldEventDao);
    }

    @Test
    @DisplayName("should defer legal hold audit until transaction commit")
    void shouldDeferLegalHoldAudit_untilTransactionCommit() {
        TransactionSynchronizationManager.initSynchronization();
        OutboundEmailArchive archive = archiveUnderLegalHold();
        stubArchiveLookup(archive);

        service.releaseLegalHold(loggedInInfo, 888, "Counsel authorised release");

        logActionMock.verifyNoInteractions();
        runAfterCommitSynchronizations();
        logActionMock.verify(() -> LogAction.addLog(loggedInInfo,
                "OutboundEmailArchiveService.changeLegalHold",
                "Outbound email archive legal hold RELEASED",
                "archiveId=888 documentNo=321",
                "123",
                ""));
    }

    @Test
    @DisplayName("should reject repeated controlled deletion")
    void shouldRejectDeletion_whenArchiveAlreadyDeleted() {
        OutboundEmailArchive archive = archiveForDeletion();
        archive.markDeleted(PROVIDER_NO, "previous cleanup");
        stubArchiveLookup(archive);

        assertThatThrownBy(() -> service.recordControlledDeletion(loggedInInfo, 888, "cleanup"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already deleted");

        verify(outboundEmailArchiveDao, never()).merge(any(OutboundEmailArchive.class));
        verifyNoInteractions(outboundEmailArchiveDeletionDao);
    }

    private OutboundEmailArchiveDto archiveRequest(EmailLog emailLog) {
        if (emailLog != null && emailLog.getId() != null) {
            when(emailLogDao.find((Object) emailLog.getId())).thenReturn(emailLog);
        }
        OutboundEmailArchiveDto request = new OutboundEmailArchiveDto();
        request.setEmailLog(emailLog);
        request.setArtifactBytes(RFC822_BYTES);
        request.setContentType("message/rfc822");
        return request;
    }

    private void runAfterCommitSynchronizations() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).isNotEmpty();
        for (TransactionSynchronization synchronization : synchronizations) {
            synchronization.afterCommit();
        }
    }

    private void runAfterCompletionSynchronizations(int status) {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).isNotEmpty();
        for (TransactionSynchronization synchronization : synchronizations) {
            synchronization.afterCompletion(status);
        }
    }

    /**
     * Drives a successful {@code archive()} under an active synchronization, with the artifact the
     * eDoc store would have written already on disk.
     *
     * @return the staged artifact path, so completion handling can be asserted against it
     */
    private Path stageArchiveForCompletion(Path documentDir) throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        OutboundEmailArchiveDto request = archiveRequest(emailLog());
        Document savedDocument = savedDocument();
        Path artifact = documentDir.resolve(savedDocument.getDocfilename());
        Files.write(artifact, RFC822_BYTES);
        when(documentManager.createDocument(eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES)))
                .thenReturn(savedDocument);

        service.archive(loggedInInfo, request);

        assertThat(artifact).exists();
        return artifact;
    }

    /**
     * Points DOCUMENT_DIR at {@code documentDir} for the duration of {@code body}, restoring the
     * process-wide {@link CarlosProperties} singleton afterwards so the value cannot leak into
     * another test running in the same JVM.
     */
    private void withDocumentDir(Path documentDir, ThrowingBody body) throws Exception {
        CarlosProperties props = CarlosProperties.getInstance();
        boolean hadDocumentDir = props.containsKey("DOCUMENT_DIR");
        Object originalDocumentDir = props.get("DOCUMENT_DIR");
        props.setProperty("DOCUMENT_DIR", documentDir.toString());
        try {
            body.run();
        } finally {
            if (hadDocumentDir) {
                props.put("DOCUMENT_DIR", originalDocumentDir);
            } else {
                props.remove("DOCUMENT_DIR");
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingBody {
        void run() throws Exception;
    }

    private void assertArchiveDocumentToCreate(Document documentToCreate) {
        assertThat(documentToCreate.getDocfilename()).startsWith("outbound-email-44-").endsWith(".eml");
        assertThat(documentToCreate.getDocdesc()).isEqualTo("Outbound email archive 44");
        assertThat(documentToCreate.getDoctype()).isEqualTo("email");
        assertThat(documentToCreate.getDocClass()).isEqualTo("EMAIL");
        assertThat(documentToCreate.getDocSubClass()).isEqualTo("OUTBOUND");
        assertThat(documentToCreate.getDoccreator()).isEqualTo(PROVIDER_NO);
        assertThat(documentToCreate.getResponsible()).isEqualTo(PROVIDER_NO);
        assertThat(documentToCreate.getContenttype()).isEqualTo("message/rfc822");
        assertThat(documentToCreate.getStatus()).isEqualTo(Document.STATUS_ACTIVE);
    }

    private void assertArchiveMetadata(OutboundEmailArchive archive, EmailLog emailLog, Document savedDocument) {
        assertThat(archive.getId()).isEqualTo(888);
        assertThat(archive.getEmailLog()).isSameAs(emailLog);
        assertThat(archive.getDemographic().getDemographicNo()).isEqualTo(123);
        assertThat(archive.getDocument()).isSameAs(savedDocument);
        assertThat(archive.getArtifactType()).isEqualTo(OutboundEmailArchive.ARTIFACT_TYPE_SMTP_RFC822);
        assertThat(archive.getTransportType()).isEqualTo("SMTP");
        assertThat(archive.getProviderName()).isEqualTo("GMAIL");
        assertThat(archive.getContentType()).isEqualTo("message/rfc822");
        assertThat(archive.getOriginalFileName()).startsWith("outbound-email-44-").endsWith(".eml");
        assertThat(archive.getFileName()).isEqualTo("20260707120000_outbound-email-44.eml");
        assertThat(archive.getSha256Hash()).isEqualTo(sha256Hex(RFC822_BYTES));
        assertThat(archive.getByteSize()).isEqualTo((long) RFC822_BYTES.length);
        assertThat(archive.getRetentionPolicy()).isEqualTo(OutboundEmailArchive.RETENTION_POLICY_PERMANENT);
        assertThat(archive.getStorageType()).isEqualTo(OutboundEmailArchive.STORAGE_TYPE_EDOC);
        assertThat(archive.getLastUpdateUser()).isEqualTo(PROVIDER_NO);
    }

    private EmailLog emailLog() {
        EmailConfig emailConfig = new EmailConfig(EmailConfig.EmailType.SMTP, EmailConfig.EmailProvider.GMAIL, "clinic@example.com");
        injectDependency(emailConfig, "id", 7);

        EmailLog emailLog = new EmailLog(emailConfig, "clinic@example.com", new String[]{"patient@example.com"}, "Test", "Body", EmailLog.EmailStatus.FAILED);
        injectDependency(emailLog, "id", 44);
        emailLog.setDemographic(new Demographic(123));
        emailLog.setProvider(new Provider(PROVIDER_NO));
        return emailLog;
    }

    private Document savedDocument() {
        Document document = new Document();
        document.setDocumentNo(321);
        document.setDocfilename("20260707120000_outbound-email-44.eml");
        document.setContenttype("message/rfc822");
        return document;
    }

    private Document attachmentDocument() {
        Document document = new Document();
        document.setDocumentNo(777);
        document.setDocfilename("message.pdf");
        document.setContenttype("application/pdf");
        return document;
    }

    private CtlDocument ctlDocument(Integer demographicNo, Integer documentNo) {
        CtlDocument ctlDocument = new CtlDocument();
        ctlDocument.setId(new CtlDocumentPK("demographic", demographicNo, documentNo));
        return ctlDocument;
    }

    /** An archive in its as-created state: under legal hold, so not yet deletable. */
    private OutboundEmailArchive archiveUnderLegalHold() {
        OutboundEmailArchive archive = new OutboundEmailArchive();
        archive.setId(888);
        archive.setEmailLog(emailLog());
        archive.setDemographic(new Demographic(123));
        archive.setDocument(savedDocument());
        archive.setFileName("20260707120000_outbound-email-44.eml");
        archive.setContentType("message/rfc822");
        archive.setSha256Hash(sha256Hex(RFC822_BYTES));
        archive.setByteSize((long) RFC822_BYTES.length);
        return archive;
    }

    /**
     * An archive whose legal hold an admin has already released — the only state from
     * which controlled deletion can succeed.
     */
    private OutboundEmailArchive archiveForDeletion() {
        OutboundEmailArchive archive = archiveUnderLegalHold();
        archive.releaseLegalHold(PROVIDER_NO);
        return archive;
    }

    // --- read API ---------------------------------------------------------------------------

    @Test
    @DisplayName("should authorize the caller before loading the archive row")
    void shouldAuthorizeCaller_beforeLoadingTheArchiveRow() {
        // The ordering property, pinned deliberately. Loading first and authorizing from the
        // loaded row would still reject, but it would hydrate a row -- and on the artifact path
        // take its lock -- for a caller with no right to the patient. The scalar demographic
        // read exists so the gate runs before either.
        when(outboundEmailArchiveDao.findDemographicNoById(888)).thenReturn(123);
        allowArchiveRead();
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 123)).thenReturn(false);

        assertThatThrownBy(() -> service.getActiveArchive(loggedInInfo, 888))
                .isInstanceOf(SecurityException.class);

        verify(outboundEmailArchiveDao, never()).findForRead(anyInt());
        verify(outboundEmailArchiveDao, never()).findForUpdate(anyInt());
    }

    @Test
    @DisplayName("should reject archive metadata read without eDoc read authority")
    void shouldRejectArchiveRead_withoutEdocReadAuthority() {
        when(outboundEmailArchiveDao.findDemographicNoById(888)).thenReturn(123);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ, null)).thenReturn(false);

        assertThatThrownBy(() -> service.getActiveArchive(loggedInInfo, 888))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_edoc)");

        verify(outboundEmailArchiveDao, never()).findForRead(anyInt());
    }

    @Test
    @DisplayName("should return archive metadata for an authorized caller")
    void shouldReturnArchiveMetadata_forAuthorizedCaller() {
        OutboundEmailArchive archive = archiveUnderLegalHold();
        stubArchiveRead(archive);

        assertThat(service.getActiveArchive(loggedInInfo, 888)).isSameAs(archive);
        // Metadata reads take no lock; only artifact reads do.
        verify(outboundEmailArchiveDao, never()).findForUpdate(anyInt());
    }

    @Test
    @DisplayName("should reject archive metadata read when the archive is deleted")
    void shouldRejectArchiveRead_whenArchiveIsDeleted() {
        OutboundEmailArchive archive = archiveForDeletion();
        archive.markDeleted(PROVIDER_NO, "duplicate send");
        stubArchiveRead(archive);

        assertThatThrownBy(() -> service.getActiveArchive(loggedInInfo, 888))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("should read the archived artifact under lock when size and hash match")
    void shouldReadArchivedArtifact_whenSizeAndHashMatch(@TempDir Path documentDir) throws Exception {
        OutboundEmailArchive archive = archiveUnderLegalHold();
        stubArchiveArtifactRead(archive);
        Files.write(documentDir.resolve(archive.getDocument().getDocfilename()), RFC822_BYTES);

        withDocumentDir(documentDir, () -> {
            assertThat(service.readArchivedArtifact(loggedInInfo, 888)).isEqualTo(RFC822_BYTES);
            // Locked, so a controlled deletion cannot remove the file mid-read.
            verify(outboundEmailArchiveDao).findForUpdate(888);
        });
    }

    @Test
    @DisplayName("should refuse the artifact when stored bytes no longer match the recorded hash")
    void shouldRefuseArtifact_whenStoredBytesDoNotMatchRecordedHash(@TempDir Path documentDir) throws Exception {
        OutboundEmailArchive archive = archiveUnderLegalHold();
        stubArchiveArtifactRead(archive);
        // Same length, different content: only the hash can catch this, which is the point of
        // recording one. A size check alone would hand back tampered PHI.
        byte[] tampered = new byte[RFC822_BYTES.length];
        System.arraycopy(RFC822_BYTES, 0, tampered, 0, RFC822_BYTES.length);
        tampered[0] = (byte) (tampered[0] ^ 0xFF);
        Files.write(documentDir.resolve(archive.getDocument().getDocfilename()), tampered);

        withDocumentDir(documentDir, () ->
                assertThatThrownBy(() -> service.readArchivedArtifact(loggedInInfo, 888))
                        .isInstanceOf(IOException.class)
                        .hasMessage("Archived artifact hash does not match archive metadata"));
    }

    @Test
    @DisplayName("should refuse the artifact when stored size does not match archive metadata")
    void shouldRefuseArtifact_whenStoredSizeDoesNotMatchMetadata(@TempDir Path documentDir) throws Exception {
        OutboundEmailArchive archive = archiveUnderLegalHold();
        stubArchiveArtifactRead(archive);
        Files.write(documentDir.resolve(archive.getDocument().getDocfilename()), "truncated".getBytes(StandardCharsets.UTF_8));

        withDocumentDir(documentDir, () ->
                assertThatThrownBy(() -> service.readArchivedArtifact(loggedInInfo, 888))
                        .isInstanceOf(IOException.class)
                        .hasMessage("Archived artifact size does not match archive metadata"));
    }

    @Test
    @DisplayName("should refuse the artifact when it exceeds the maximum read size")
    void shouldRefuseArtifact_whenItExceedsMaximumReadSize(@TempDir Path documentDir) throws Exception {
        OutboundEmailArchiveServiceImpl boundedService = new OutboundEmailArchiveServiceImpl(
                documentManager, emailLogDao, outboundEmailArchiveDao, outboundEmailArchiveDeletionDao,
                outboundEmailArchiveLegalHoldEventDao, ctlDocumentDao, securityInfoManager, 4L);
        OutboundEmailArchive archive = archiveUnderLegalHold();
        stubArchiveArtifactRead(archive);
        Files.write(documentDir.resolve(archive.getDocument().getDocfilename()), RFC822_BYTES);

        withDocumentDir(documentDir, () ->
                assertThatThrownBy(() -> boundedService.readArchivedArtifact(loggedInInfo, 888))
                        .isInstanceOf(IOException.class)
                        .hasMessageContaining("exceeds maximum read size"));
    }

    @Test
    @DisplayName("should refuse the artifact when the stored file is missing")
    void shouldRefuseArtifact_whenStoredFileIsMissing(@TempDir Path documentDir) throws Exception {
        OutboundEmailArchive archive = archiveUnderLegalHold();
        stubArchiveArtifactRead(archive);

        withDocumentDir(documentDir, () ->
                assertThatThrownBy(() -> service.readArchivedArtifact(loggedInInfo, 888))
                        .isInstanceOf(IOException.class));
    }

    private void allowArchiveRead() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ, null)).thenReturn(true);
    }

    /** Stubs the demographic gate and the unlocked row read used by metadata reads. */
    private void stubArchiveRead(OutboundEmailArchive archive) {
        allowArchiveRead();
        when(outboundEmailArchiveDao.findDemographicNoById(888)).thenReturn(123);
        when(outboundEmailArchiveDao.findForRead(888)).thenReturn(archive);
    }

    /** Stubs the demographic gate and the locked row read used by artifact reads. */
    private void stubArchiveArtifactRead(OutboundEmailArchive archive) {
        allowArchiveRead();
        when(outboundEmailArchiveDao.findDemographicNoById(888)).thenReturn(123);
        when(outboundEmailArchiveDao.findForUpdate(888)).thenReturn(archive);
    }

    private String sha256Hex(byte[] input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Stubs both reads the service performs for a privileged archive change: the scalar
     * demographic read that the patient-record gate runs on, then the locked row.
     */
    private void stubArchiveLookup(OutboundEmailArchive archive) {
        // Deliberately findDemographicNoById, not find: the service must not hydrate the
        // archive before findForUpdate, or the locked read returns pre-lock state.
        when(outboundEmailArchiveDao.findDemographicNoById(888)).thenReturn(
                archive.getDemographic() != null ? archive.getDemographic().getDemographicNo() : null);
        when(outboundEmailArchiveDao.findForUpdate(888)).thenReturn(archive);
    }

    private void allowControlledDeletion() {
        // Deletion and legal hold changes are admin-only: plain _edoc w is the right
        // needed to CREATE an archive and is deliberately not sufficient to retire one.
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.edocdelete", SecurityInfoManager.WRITE, null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.WRITE, null)).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 123)).thenReturn(true);
    }
}
