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
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.EmailLogDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDeletionDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchive;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchiveAttachment;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchiveDeletion;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveAttachmentDto;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveDto;
import io.github.carlos_emr.carlos.encounter.data.EctFormData.PatientForm;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
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
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("OutboundEmailArchiveServiceImpl")
@Tag("unit")
class OutboundEmailArchiveServiceImplUnitTest extends CarlosUnitTestBase {

    private static final String PROVIDER_NO = "999998";
    private static final byte[] RFC822_BYTES = "Subject: Test\r\n\r\nBody".getBytes(StandardCharsets.UTF_8);

    private OutboundEmailArchiveDocumentPersister archiveDocumentPersister;
    private DocumentDao documentDao;
    private EmailLogDao emailLogDao;
    private OutboundEmailArchiveDao outboundEmailArchiveDao;
    private OutboundEmailArchiveDeletionDao outboundEmailArchiveDeletionDao;
    private CtlDocumentDao ctlDocumentDao;
    private EFormDataDao eFormDataDao;
    private PatientLabRoutingDao patientLabRoutingDao;
    private HRMDocumentToDemographicDao hrmDocumentToDemographicDao;
    private FormsManager formsManager;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private OutboundEmailArchiveServiceImpl service;

    @BeforeEach
    void setUp() {
        archiveDocumentPersister = mock(OutboundEmailArchiveDocumentPersister.class);
        documentDao = mock(DocumentDao.class);
        emailLogDao = mock(EmailLogDao.class);
        outboundEmailArchiveDao = mock(OutboundEmailArchiveDao.class);
        outboundEmailArchiveDeletionDao = mock(OutboundEmailArchiveDeletionDao.class);
        ctlDocumentDao = mock(CtlDocumentDao.class);
        eFormDataDao = mock(EFormDataDao.class);
        patientLabRoutingDao = mock(PatientLabRoutingDao.class);
        hrmDocumentToDemographicDao = mock(HRMDocumentToDemographicDao.class);
        formsManager = mock(FormsManager.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        service = new OutboundEmailArchiveServiceImpl(
                archiveDocumentPersister,
                documentDao,
                emailLogDao,
                outboundEmailArchiveDao,
                outboundEmailArchiveDeletionDao,
                ctlDocumentDao,
                eFormDataDao,
                patientLabRoutingDao,
                hrmDocumentToDemographicDao,
                formsManager,
                securityInfoManager);

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
        when(archiveDocumentPersister.persistArchiveDocument(eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES)))
                .thenReturn(savedDocument);
        doAnswer(invocation -> {
            OutboundEmailArchive archive = invocation.getArgument(0);
            archive.setId(888);
            return null;
        }).when(outboundEmailArchiveDao).persist(any(OutboundEmailArchive.class));

        OutboundEmailArchive archive = service.archive(loggedInInfo, request);

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(archiveDocumentPersister).persistArchiveDocument(eq(loggedInInfo), documentCaptor.capture(), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES));
        assertArchiveDocumentToCreate(documentCaptor.getValue());

        verify(outboundEmailArchiveDao).persist(archive);
        assertArchiveMetadata(archive, emailLog, savedDocument);
    }

    @Test
    @DisplayName("should load persisted email log before deriving archive demographics")
    void shouldLoadPersistedEmailLog_beforeDerivingArchiveDemographics() throws Exception {
        EmailLog requestedEmailLog = emailLog();
        requestedEmailLog.setDemographic(new Demographic(456));
        requestedEmailLog.setProvider(new Provider("111111"));
        EmailConfig requestedEmailConfig = new EmailConfig(EmailConfig.EmailType.SMTP, EmailConfig.EmailProvider.GMAIL, "requested@example.com");
        injectDependency(requestedEmailConfig, "id", 55);
        requestedEmailLog.setEmailConfig(requestedEmailConfig);
        OutboundEmailArchiveDto request = archiveRequest(requestedEmailLog);

        EmailLog persistedEmailLog = emailLog();
        EmailConfig persistedEmailConfig = new EmailConfig(EmailConfig.EmailType.API, EmailConfig.EmailProvider.SENDGRID, "persisted@example.com");
        Provider persistedProvider = new Provider("222222");
        injectDependency(persistedEmailConfig, "id", 66);
        persistedEmailLog.setDemographic(new Demographic(789));
        persistedEmailLog.setProvider(persistedProvider);
        persistedEmailLog.setEmailConfig(persistedEmailConfig);
        Document savedDocument = savedDocument();
        when(emailLogDao.find((Object) Integer.valueOf(44))).thenReturn(persistedEmailLog);
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 789)).thenReturn(true);
        when(archiveDocumentPersister.persistArchiveDocument(eq(loggedInInfo), any(Document.class), eq(789), eq(PROVIDER_NO), eq(RFC822_BYTES)))
                .thenReturn(savedDocument);

        OutboundEmailArchive archive = service.archive(loggedInInfo, request);

        verify(emailLogDao).find((Object) Integer.valueOf(44));
        // The archive is filed against the reloaded email log's demographic (789), never the
        // request-supplied one (456) - prevents a confused-deputy write to the wrong chart.
        verify(archiveDocumentPersister).persistArchiveDocument(eq(loggedInInfo), any(Document.class), eq(789), eq(PROVIDER_NO), eq(RFC822_BYTES));
        verify(archiveDocumentPersister, never()).persistArchiveDocument(eq(loggedInInfo), any(Document.class), eq(456), eq(PROVIDER_NO), eq(RFC822_BYTES));
        assertThat(archive.getEmailLog()).isSameAs(persistedEmailLog);
        assertThat(archive.getDemographic().getDemographicNo()).isEqualTo(789);
        assertThat(archive.getProvider()).isSameAs(persistedProvider);
        assertThat(archive.getEmailConfig()).isSameAs(persistedEmailConfig);
        assertThat(archive.getTransportType()).isEqualTo("API");
        assertThat(archive.getProviderName()).isEqualTo("SENDGRID");
    }

    @Test
    @DisplayName("should choose file extension from media type with parameters")
    void shouldChooseFileExtension_whenMediaTypeHasParameters() throws Exception {
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);
        request.setContentType("Message/RFC822; charset=UTF-8");
        when(archiveDocumentPersister.persistArchiveDocument(eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES)))
                .thenReturn(savedDocument());

        service.archive(loggedInInfo, request);

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(archiveDocumentPersister).persistArchiveDocument(eq(loggedInInfo), documentCaptor.capture(), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES));
        assertThat(documentCaptor.getValue().getDocfilename()).startsWith("outbound-email-44-").endsWith(".eml");
    }

    @Test
    @DisplayName("should use generated eDoc filename and retain caller filename as metadata")
    void shouldUseGeneratedEdocFilename_whenCallerFilenameProvided() throws Exception {
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);
        request.setFileName("Jane-Smith-1970-01-01-referral.eml");
        when(archiveDocumentPersister.persistArchiveDocument(eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES)))
                .thenReturn(savedDocument());

        OutboundEmailArchive archive = service.archive(loggedInInfo, request);

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(archiveDocumentPersister).persistArchiveDocument(eq(loggedInInfo), documentCaptor.capture(), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES));
        assertThat(documentCaptor.getValue().getDocfilename()).startsWith("outbound-email-44-").endsWith(".eml");
        assertThat(archive.getOriginalFileName()).isEqualTo("Jane-Smith-1970-01-01-referral.eml");
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
            }).when(archiveDocumentPersister).persistArchiveDocument(eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES));

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
    @DisplayName("should defer archive audit until transaction commit")
    void shouldDeferArchiveAudit_untilTransactionCommit() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);
        Document savedDocument = savedDocument();
        when(archiveDocumentPersister.persistArchiveDocument(eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES)))
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
    @DisplayName("should delete eDoc file when archive transaction rolls back")
    void shouldDeleteEdocFile_whenArchiveTransactionRollsBack(@TempDir Path documentDir) throws Exception {
        CarlosProperties props = CarlosProperties.getInstance();
        boolean hadDocumentDir = props.containsKey("DOCUMENT_DIR");
        Object originalDocumentDir = props.get("DOCUMENT_DIR");
        props.setProperty("DOCUMENT_DIR", documentDir.toString());
        TransactionSynchronizationManager.initSynchronization();

        try {
            EmailLog emailLog = emailLog();
            OutboundEmailArchiveDto request = archiveRequest(emailLog);
            Document savedDocument = savedDocument();
            Path archivedFile = documentDir.resolve(savedDocument.getDocfilename());
            when(archiveDocumentPersister.persistArchiveDocument(eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES)))
                    .thenAnswer(invocation -> {
                        Files.write(archivedFile, RFC822_BYTES);
                        return savedDocument;
                    });

            service.archive(loggedInInfo, request);

            assertThat(archivedFile).exists();
            logActionMock.verifyNoInteractions();
            runAfterRollbackSynchronizations();
            assertThat(archivedFile).doesNotExist();
            logActionMock.verifyNoInteractions();
        } finally {
            if (hadDocumentDir) {
                props.put("DOCUMENT_DIR", originalDocumentDir);
            } else {
                props.remove("DOCUMENT_DIR");
            }
        }
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
        when(archiveDocumentPersister.persistArchiveDocument(eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES)))
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

        when(archiveDocumentPersister.persistArchiveDocument(eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES)))
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
        verifyNoInteractions(eFormDataDao);
        verifyNoInteractions(patientLabRoutingDao);
        verifyNoInteractions(hrmDocumentToDemographicDao);
        verifyNoInteractions(formsManager);
    }

    @Test
    @DisplayName("should validate known attachment source metadata against archive demographic")
    void shouldValidateKnownAttachmentSourceMetadata_againstArchiveDemographic() throws Exception {
        byte[] attachmentBytes = "attachment bytes".getBytes(StandardCharsets.UTF_8);
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);
        request.addAttachment(attachmentMetadata("eform.pdf", "EFORM", 501, attachmentBytes));
        request.addAttachment(attachmentMetadata("lab.pdf", "LAB", 502, attachmentBytes));
        request.addAttachment(attachmentMetadata("hrm.pdf", "HRM", 503, attachmentBytes));
        request.addAttachment(attachmentMetadata("form.pdf", "FORM", 504, attachmentBytes));

        EFormData eFormData = new EFormData();
        eFormData.setDemographicId(123);
        HRMDocumentToDemographic hrmMapping = new HRMDocumentToDemographic();
        hrmMapping.setDemographicNo(123);
        when(eFormDataDao.findByFormDataId(501)).thenReturn(eFormData);
        when(patientLabRoutingDao.findAllByLabNo(502)).thenReturn(List.of(new PatientLabRouting(502, "HL7", 123)));
        when(hrmDocumentToDemographicDao.findByHrmDocumentId(503)).thenReturn(List.of(hrmMapping));
        when(formsManager.getEncounterFormsbyDemographicNumber(loggedInInfo, 123, true, true))
                .thenReturn(List.of(new PatientForm("formTable", "Form", 504, 123)));
        when(archiveDocumentPersister.persistArchiveDocument(eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES)))
                .thenReturn(savedDocument());

        OutboundEmailArchive archive = service.archive(loggedInInfo, request);

        assertThat(archive.getAttachments()).hasSize(4);
    }

    @Test
    @DisplayName("should reject attachment source ID without source type")
    void shouldRejectAttachmentSourceIdWithoutSourceType_beforeStoringEdoc() {
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);
        OutboundEmailArchiveAttachmentDto attachmentRequest = attachmentMetadata("unknown.pdf", null, 501, "attachment bytes".getBytes(StandardCharsets.UTF_8));
        request.addAttachment(attachmentRequest);

        assertThatThrownBy(() -> service.archive(loggedInInfo, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source document type");

        verifyNoInteractions(archiveDocumentPersister);
    }

    @Test
    @DisplayName("should reject document source metadata from a different demographic")
    void shouldRejectDocumentSourceMetadataFromDifferentDemographic_beforeStoringEdoc() {
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);
        OutboundEmailArchiveAttachmentDto attachmentRequest = attachmentMetadata("document.pdf", "DOC", 777, "attachment bytes".getBytes(StandardCharsets.UTF_8));
        request.addAttachment(attachmentRequest);
        when(ctlDocumentDao.findByDocumentNoAndModule(777, "demographic")).thenReturn(List.of(ctlDocument(456, 777)));

        assertThatThrownBy(() -> service.archive(loggedInInfo, request))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("source document");

        verifyNoInteractions(archiveDocumentPersister);
    }

    @Test
    @DisplayName("should prevent callers from mutating archive attachments directly")
    void shouldPreventDirectAttachmentMutation_whenAttachmentsAreExposed() throws Exception {
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);
        when(archiveDocumentPersister.persistArchiveDocument(eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES)))
                .thenReturn(savedDocument());

        OutboundEmailArchive archive = service.archive(loggedInInfo, request);
        List<OutboundEmailArchiveAttachment> exposedAttachments = archive.getAttachments();
        OutboundEmailArchiveAttachment attemptedMutation = new OutboundEmailArchiveAttachment();

        assertThatThrownBy(() -> exposedAttachments.add(attemptedMutation))
                .isInstanceOf(UnsupportedOperationException.class);
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
        verifyNoInteractions(archiveDocumentPersister);
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
        verifyNoInteractions(archiveDocumentPersister);
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
        verifyNoInteractions(archiveDocumentPersister);
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
        verifyNoInteractions(archiveDocumentPersister);
    }

    @Test
    @DisplayName("should reject archive without email send authority")
    void shouldRejectArchive_whenCallerLacksEmailWriteAuthority() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(false);
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);

        assertThatThrownBy(() -> service.archive(loggedInInfo, request))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_email)");

        verifyNoInteractions(archiveDocumentPersister);
    }

    @Test
    @DisplayName("should archive for an authorized sender lacking eDoc write")
    void shouldArchive_whenSenderHasEmailWriteButLacksEdoc() throws Exception {
        // Front-desk case: holds _email (may send) and default patient-record access, but not _edoc.
        // The archive is a mandatory system control and must still be written via persistArchiveDocument.
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.WRITE, null)).thenReturn(false);
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);
        when(archiveDocumentPersister.persistArchiveDocument(eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES)))
                .thenReturn(savedDocument());

        OutboundEmailArchive archive = service.archive(loggedInInfo, request);

        assertThat(archive).isNotNull();
        verify(archiveDocumentPersister).persistArchiveDocument(eq(loggedInInfo), any(Document.class), eq(123), eq(PROVIDER_NO), eq(RFC822_BYTES));
    }

    @Test
    @DisplayName("should reject archive into a chart locked to the sender")
    void shouldRejectArchive_whenPatientRecordAccessDenied() {
        // Patient-record access is default-allow; a false result means the chart is explicitly locked
        // to the caller, so the outbound PHI must not be archived into it.
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 123)).thenReturn(false);
        EmailLog emailLog = emailLog();
        OutboundEmailArchiveDto request = archiveRequest(emailLog);

        assertThatThrownBy(() -> service.archive(loggedInInfo, request))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("archive demographic");

        verifyNoInteractions(archiveDocumentPersister);
    }

    @Test
    @DisplayName("should mark archive deleted and persist tombstone")
    void shouldMarkArchiveDeletedAndPersistTombstone_whenDeletionAllowed() {
        OutboundEmailArchive archive = archiveForDeletion();
        Document archivedDocument = archive.getDocument();
        when(outboundEmailArchiveDao.findForUpdate(888)).thenReturn(archive);

        OutboundEmailArchiveDeletion deletion = service.recordControlledDeletion(loggedInInfo, 888, "Patient requested cleanup");

        verify(outboundEmailArchiveDao).findForUpdate(888);
        verify(documentDao).merge(archivedDocument);
        verify(outboundEmailArchiveDao).merge(archive);
        verify(outboundEmailArchiveDeletionDao).persist(deletion);
        assertThat(archive.isDeleted()).isTrue();
        assertThat(archive.getDeletedByProviderNo()).isEqualTo(PROVIDER_NO);
        assertThat(archive.getDeleteReason()).isEqualTo("Patient requested cleanup");
        assertThat(archivedDocument.getStatus()).isEqualTo(Document.STATUS_DELETED);
        assertThat(archivedDocument.getUpdatedatetime()).isEqualTo(archive.getDeletedAt());
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
        when(outboundEmailArchiveDao.findForUpdate(888)).thenReturn(archive);

        OutboundEmailArchiveDeletion deletion = service.recordControlledDeletion(loggedInInfo, 888, " ".repeat(1000) + "Meaningful reason");

        assertThat(deletion.getDeleteReason()).isEqualTo("Meaningful reason");
        assertThat(archive.getDeleteReason()).isEqualTo("Meaningful reason");
    }

    @Test
    @DisplayName("should defer controlled deletion audit until transaction commit")
    void shouldDeferControlledDeletionAudit_untilTransactionCommit() {
        TransactionSynchronizationManager.initSynchronization();
        OutboundEmailArchive archive = archiveForDeletion();
        when(outboundEmailArchiveDao.findForUpdate(888)).thenReturn(archive);

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
    @DisplayName("should reject controlled deletion without eDoc delete authority")
    void shouldRejectDeletion_whenCallerLacksEdocDeleteAuthority() {
        OutboundEmailArchive archive = archiveForDeletion();
        when(outboundEmailArchiveDao.findForUpdate(888)).thenReturn(archive);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.edocdelete", SecurityInfoManager.WRITE, null)).thenReturn(false);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.WRITE, null)).thenReturn(false);

        assertThatThrownBy(() -> service.recordControlledDeletion(loggedInInfo, 888, "cleanup"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin.edocdelete");

        verify(outboundEmailArchiveDao, never()).merge(any(OutboundEmailArchive.class));
        verifyNoInteractions(outboundEmailArchiveDeletionDao);
    }

    @Test
    @DisplayName("should reject controlled deletion without patient access")
    void shouldRejectDeletion_whenCallerCannotAccessPatientRecord() {
        OutboundEmailArchive archive = archiveForDeletion();
        when(outboundEmailArchiveDao.findForUpdate(888)).thenReturn(archive);
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 123)).thenReturn(false);

        assertThatThrownBy(() -> service.recordControlledDeletion(loggedInInfo, 888, "cleanup"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("archive demographic");

        verify(outboundEmailArchiveDao, never()).merge(any(OutboundEmailArchive.class));
        verifyNoInteractions(outboundEmailArchiveDeletionDao);
    }

    @Test
    @DisplayName("should reject controlled deletion while legal hold is active")
    void shouldRejectDeletion_whenLegalHoldIsActive() {
        OutboundEmailArchive archive = archiveForDeletion();
        archive.setLegalHold(true);
        when(outboundEmailArchiveDao.findForUpdate(888)).thenReturn(archive);

        assertThatThrownBy(() -> service.recordControlledDeletion(loggedInInfo, 888, "cleanup"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("legal hold");
    }

    @Test
    @DisplayName("should reject repeated controlled deletion")
    void shouldRejectDeletion_whenArchiveAlreadyDeleted() {
        OutboundEmailArchive archive = archiveForDeletion();
        archive.markDeleted(PROVIDER_NO, "previous cleanup");
        when(outboundEmailArchiveDao.findForUpdate(888)).thenReturn(archive);

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
        try {
            for (TransactionSynchronization synchronization : synchronizations) {
                synchronization.beforeCommit(false);
            }
            for (TransactionSynchronization synchronization : synchronizations) {
                synchronization.beforeCompletion();
            }
            for (TransactionSynchronization synchronization : synchronizations) {
                synchronization.afterCommit();
            }
            for (TransactionSynchronization synchronization : synchronizations) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void runAfterRollbackSynchronizations() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).isNotEmpty();
        try {
            for (TransactionSynchronization synchronization : synchronizations) {
                synchronization.beforeCompletion();
            }
            for (TransactionSynchronization synchronization : synchronizations) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
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

    private OutboundEmailArchiveAttachmentDto attachmentMetadata(String fileName, String sourceDocumentType, Integer sourceDocumentId, byte[] attachmentBytes) {
        OutboundEmailArchiveAttachmentDto attachmentRequest = new OutboundEmailArchiveAttachmentDto();
        attachmentRequest.setFileName(fileName);
        attachmentRequest.setContentType("application/pdf");
        attachmentRequest.setSha256Hash(sha256Hex(attachmentBytes));
        attachmentRequest.setByteSize((long) attachmentBytes.length);
        attachmentRequest.setSourceDocumentType(sourceDocumentType);
        attachmentRequest.setSourceDocumentId(sourceDocumentId);
        return attachmentRequest;
    }

    private OutboundEmailArchive archiveForDeletion() {
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

    private String sha256Hex(byte[] input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void allowControlledDeletion() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.edocdelete", SecurityInfoManager.WRITE, null)).thenReturn(false);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.WRITE, null)).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 123)).thenReturn(true);
    }
}
