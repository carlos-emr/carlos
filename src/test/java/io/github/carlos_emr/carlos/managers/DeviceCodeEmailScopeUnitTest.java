/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.EmailLogDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDeletionDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveLegalHoldEventDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchive;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveAttachmentDto;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveDto;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveTestKeyrings;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * AUTHORIZATION EXCEPTION D141 (clinic code v1, mycarlos#141): the outbound email archive takes the
 * myCarlos device code email from the system device-code job without the eDoc write right, and
 * nothing else. Each case below changes one condition and must be refused.
 */
@Tag("unit")
@Tag("security")
class DeviceCodeEmailScopeUnitTest extends CarlosUnitTestBase {
    private static final int PATIENT = 123;
    private static final byte[] RFC822 = "Subject: Your myCarlos code\r\n\r\nBody".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDir;

    private DocumentManager documentManager;
    private EmailLogDao emailLogs;
    private OutboundEmailArchiveDao archives;
    private SecurityInfoManager security;
    private OutboundEmailArchiveServiceImpl service;
    private LoggedInInfo system;
    private LoggedInInfo staff;

    @BeforeEach
    void setUp() throws Exception {
        documentManager = mock(DocumentManager.class);
        emailLogs = mock(EmailLogDao.class);
        archives = mock(OutboundEmailArchiveDao.class);
        security = mock(SecurityInfoManager.class);
        var keyrings = mock(OutboundEmailArchiveKeyringService.class);
        when(keyrings.getKeyring()).thenReturn(OutboundEmailArchiveTestKeyrings.keyring(1, 1));
        service = new OutboundEmailArchiveServiceImpl(documentManager, emailLogs, archives,
                mock(OutboundEmailArchiveDeletionDao.class), mock(OutboundEmailArchiveLegalHoldEventDao.class),
                mock(CtlDocumentDao.class), security, mock(OutboundEmailArchiveReadAuditService.class), keyrings);
        system = mock(LoggedInInfo.class);
        when(system.getLoggedInProviderNo()).thenReturn(Provider.SYSTEM_PROVIDER_NO);
        staff = mock(LoggedInInfo.class);
        when(staff.getLoggedInProviderNo()).thenReturn("999998");
        // Nobody here holds the eDoc write right (a mock grants nothing); the patient's record is
        // open to both logins.
        when(security.isAllowedAccessToPatientRecord(any(), eq(PATIENT))).thenReturn(true);
        doAnswer(call -> {
            Document document = call.getArgument(1);
            // Stored only while the archive is storing it, inside the job's scope.
            assertThat(DeviceCodeEmailScope.coversDocument(call.getArgument(0), document)).isTrue();
            document.setDocumentNo(321);
            return document;
        }).when(documentManager).createDocument(any(), any(Document.class), anyInt(), anyString(), any(byte[].class));
        doAnswer(call -> {
            OutboundEmailArchive archive = call.getArgument(0);
            archive.setId(888);
            return null;
        }).when(archives).persist(any(OutboundEmailArchive.class));
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertThat(DeviceCodeEmailScope.isOpen()).isFalse();
    }

    private OutboundEmailArchiveDto request(EmailLog.TransactionType type, int patient) {
        var config = new EmailConfig(EmailConfig.EmailType.SMTP, EmailConfig.EmailProvider.GMAIL, "FAKE.clinic@example.com");
        injectDependency(config, "id", 7);
        var log = new EmailLog(config, "FAKE.clinic@example.com", new String[] {"fake.patient@example.com"},
                "Your myCarlos code", "Body", EmailLog.EmailStatus.PENDING);
        injectDependency(log, "id", 44);
        log.setDemographic(new Demographic(patient));
        log.setProvider(new Provider(Provider.SYSTEM_PROVIDER_NO));
        log.setTransactionType(type);
        when(emailLogs.find((Object) 44)).thenReturn(log);
        var request = new OutboundEmailArchiveDto();
        request.setEmailLog(log);
        request.setArtifactBytes(RFC822);
        request.setContentType("message/rfc822");
        return request;
    }

    @Test
    void shouldArchiveDeviceCodeEmail_forSystemJobInsideItsScope() throws Exception {
        try (var scope = DeviceCodeEmailScope.open(PATIENT)) {
            var archive = service.archive(system, request(EmailLog.TransactionType.MYCARLOS_CODE, PATIENT));
            assertThat(archive.getId()).isEqualTo(888);
        }
        verify(documentManager).createDocument(eq(system), any(Document.class), eq(PATIENT), eq(Provider.SYSTEM_PROVIDER_NO),
                any(byte[].class));
    }

    @Test
    void shouldRefuseDeviceCodeEmail_withoutTheJobsScope() {
        assertThatThrownBy(() -> service.archive(system, request(EmailLog.TransactionType.MYCARLOS_CODE, PATIENT)))
                .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_edoc w)");
        verify(emailLogs, never()).find((Object) 44);
    }

    @Test
    void shouldRefuseDeviceCodeEmail_fromAnyOtherLogin() {
        try (var scope = DeviceCodeEmailScope.open(PATIENT)) {
            assertThatThrownBy(() -> service.archive(staff, request(EmailLog.TransactionType.MYCARLOS_CODE, PATIENT)))
                    .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_edoc w)");
        }
    }

    @Test
    void shouldRefuseAnyOtherEmailType_fromTheJobInsideItsScope() {
        for (var type : new EmailLog.TransactionType[] {EmailLog.TransactionType.DIRECT,
                EmailLog.TransactionType.PORTAL_INVITE, EmailLog.TransactionType.EFORM, null}) {
            try (var scope = DeviceCodeEmailScope.open(PATIENT)) {
                assertThatThrownBy(() -> service.archive(system, request(type, PATIENT)))
                        .as(String.valueOf(type)).isInstanceOf(SecurityException.class);
            }
        }
    }

    @Test
    void shouldRefuseDeviceCodeEmail_forAnotherPatientThanTheScopes() {
        when(security.isAllowedAccessToPatientRecord(any(), eq(456))).thenReturn(true);
        try (var scope = DeviceCodeEmailScope.open(PATIENT)) {
            assertThatThrownBy(() -> service.archive(system, request(EmailLog.TransactionType.MYCARLOS_CODE, 456)))
                    .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_edoc w)");
        }
    }

    @Test
    void shouldRefuseDeviceCodeEmail_withAnAttachment() {
        try (var scope = DeviceCodeEmailScope.open(PATIENT)) {
            var request = request(EmailLog.TransactionType.MYCARLOS_CODE, PATIENT);
            request.setAttachments(List.of(new OutboundEmailArchiveAttachmentDto()));
            assertThatThrownBy(() -> service.archive(system, request))
                    .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_edoc w)");
        }
    }

    @Test
    void shouldStillCheckPatientRecordAccess_insideTheScope() {
        when(security.isAllowedAccessToPatientRecord(any(), eq(PATIENT))).thenReturn(false);
        try (var scope = DeviceCodeEmailScope.open(PATIENT)) {
            assertThatThrownBy(() -> service.archive(system, request(EmailLog.TransactionType.MYCARLOS_CODE, PATIENT)))
                    .isInstanceOf(SecurityException.class).hasMessage("not authorized for outbound email archive demographic");
        }
    }

    @Test
    void shouldExemptNothing_fromAScopeOpenOnAnotherThread() throws Exception {
        var opened = new CompletableFuture<DeviceCodeEmailScope>();
        var release = new CompletableFuture<Void>();
        var other = new Thread(() -> {
            try (var scope = DeviceCodeEmailScope.open(PATIENT)) {
                opened.complete(scope);
                release.join();
            }
        });
        other.start();
        var leaked = opened.get();
        try {
            assertThat(DeviceCodeEmailScope.isOpen()).isFalse();
            assertThatThrownBy(() -> service.archive(system, request(EmailLog.TransactionType.MYCARLOS_CODE, PATIENT)))
                    .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_edoc w)");
            assertThatThrownBy(() -> DeviceCodeEmailScope.archiving(() -> null)).isInstanceOf(IllegalStateException.class);
            assertThat(DeviceCodeEmailScope.coversDocument(system, new Document())).isFalse();
            assertThat(leaked).isNotNull();
        } finally {
            release.complete(null);
            other.join();
        }
    }

    @Test
    void shouldRefuseNestedOrUnownedScope_onOneThread() {
        try (var scope = DeviceCodeEmailScope.open(PATIENT)) {
            assertThatThrownBy(() -> DeviceCodeEmailScope.open(PATIENT)).isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(() -> DeviceCodeEmailScope.open(0)).isInstanceOf(IllegalArgumentException.class);
    }

    // --- document storage ---------------------------------------------------------------------

    private DocumentManagerImpl documents() {
        var manager = new DocumentManagerImpl();
        var documentDao = mock(DocumentDao.class);
        doAnswer(call -> {
            Document document = call.getArgument(0);
            document.setDocumentNo(321);
            return null;
        }).when(documentDao).persist(any(Document.class));
        injectDependency(manager, "outboundEmailArchiveDao", mock(OutboundEmailArchiveDao.class));
        injectDependency(manager, "securityInfoManager", security);
        injectDependency(manager, "documentDao", documentDao);
        injectDependency(manager, "ctlDocumentDao", mock(CtlDocumentDao.class));
        injectDependency(manager, "patientLabRoutingDao", mock(PatientLabRoutingDao.class));
        injectDependency(manager, "providerLabRoutingDao", mock(ProviderLabRoutingDao.class));
        return manager;
    }

    private static Document archiveDocument() {
        var document = new Document();
        document.setDocfilename("outbound-email-44.eml");
        document.setDoctype(OutboundEmailArchiveServiceImpl.DOCUMENT_DOCTYPE);
        document.setDocClass(OutboundEmailArchiveServiceImpl.DOCUMENT_CLASS);
        document.setSource(OutboundEmailArchiveServiceImpl.DOCUMENT_SOURCE);
        return document;
    }

    @Test
    void shouldStoreArchivedCodeEmailDocument_onlyWhileTheArchiveStoresIt() throws Exception {
        try (MockedStatic<CarlosProperties> properties = mockStatic(CarlosProperties.class)) {
            var carlos = mock(CarlosProperties.class);
            properties.when(CarlosProperties::getInstance).thenReturn(carlos);
            when(carlos.getProperty("DOCUMENT_DIR")).thenReturn(tempDir.toString());
            var manager = documents();
            byte[] bytes = "sealed".getBytes(StandardCharsets.UTF_8);
            // Outside any scope, and inside the scope but not while archiving: refused.
            assertThatThrownBy(() -> manager.createDocument(system, archiveDocument(), PATIENT, Provider.SYSTEM_PROVIDER_NO, bytes))
                    .hasMessageContaining("Write Access Denied _edoc");
            try (var scope = DeviceCodeEmailScope.open(PATIENT)) {
                assertThatThrownBy(() -> manager.createDocument(system, archiveDocument(), PATIENT, Provider.SYSTEM_PROVIDER_NO, bytes))
                        .hasMessageContaining("Write Access Denied _edoc");
                // While archiving: only the archive's own kind of document, and only for the system provider.
                var other = new Document();
                other.setDocfilename("letter.pdf");
                assertThatThrownBy(() -> DeviceCodeEmailScope.archiving(() ->
                        manager.createDocument(system, other, PATIENT, Provider.SYSTEM_PROVIDER_NO, bytes)))
                        .hasMessageContaining("Write Access Denied _edoc");
                assertThatThrownBy(() -> DeviceCodeEmailScope.archiving(() ->
                        manager.createDocument(staff, archiveDocument(), PATIENT, "999998", bytes)))
                        .hasMessageContaining("Write Access Denied _edoc");
                var stored = DeviceCodeEmailScope.archiving(() ->
                        manager.createDocument(system, archiveDocument(), PATIENT, Provider.SYSTEM_PROVIDER_NO, bytes));
                assertThat(stored.getDocumentNo()).isEqualTo(321);
            }
        }
    }
}
