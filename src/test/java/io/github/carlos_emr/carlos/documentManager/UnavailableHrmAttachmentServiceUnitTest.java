/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.*;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.*;
import org.springframework.mock.web.MockHttpSession;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnavailableHrmAttachmentServiceUnitTest extends CarlosUnitTestBase {
    private ConsultDocsDao attachments;
    private HRMDocumentDao reports;
    private UnavailableHrmAttachmentService service;
    private LoggedInInfo actor;
    private MockHttpSession session;
    private List<ConsultDocs> rows;
    private EReferAttachmentDataDao pending;

    @BeforeEach void setup() {
        var parents = mock(ConsultationRequestDao.class); attachments = mock(ConsultDocsDao.class);
        reports = mock(HRMDocumentDao.class); var security = mock(SecurityInfoManager.class);
        var parent = new ConsultationRequest(); org.springframework.test.util.ReflectionTestUtils.setField(parent, "id", 42); parent.setDemographicId(5);
        when(parents.find(42)).thenReturn(parent); when(parents.lockForAttachmentSync(42)).thenReturn(parent);
        actor = mock(LoggedInInfo.class); when(actor.getLoggedInProviderNo()).thenReturn("7");
        when(security.hasPrivilege(actor, "_con", "w", "5")).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(actor, 5)).thenReturn(true);
        rows = new ArrayList<>(); rows.add(row(91));
        when(attachments.findByRequestIdDocType(42, "H")).thenAnswer(c -> rows.stream().filter(r -> r.getDeleted() == null).toList());
        when(attachments.findByRequestIdDocTypeForUpdate(42, "H")).thenAnswer(c -> rows.stream().filter(r -> r.getDeleted() == null).toList());
        pending = mock(EReferAttachmentDataDao.class);
        service = new UnavailableHrmAttachmentService(parents, attachments, reports, security, new RecordingTransactionManager(), pending);
        session = new MockHttpSession();
    }
    private ConsultDocs row(int id) { var r = new ConsultDocs(42, 17, "H", "7"); r.setId(id); return r; }

    @Test void shouldDetachOnlyOnce_whenTheSameSelectionIsSubmittedTwice() {
        String token = service.prepare(actor, 42, session);
        assertThat(token).matches("[a-f0-9-]{36}");
        service.remove(actor, token, session); service.remove(actor, token, session);
        assertThat(rows.getFirst().getDeleted()).isEqualTo("Y"); verify(attachments).merge(rows.getFirst());
        verify(pending).removeRecentByDocumentId(eq(17), eq("H"), eq(5), any(Date.class));
    }
    @Test void shouldKeepAReattachedRelationship_whenTheOldSelectionIsReplayed() {
        String token = service.prepare(actor, 42, session);
        rows.getFirst().setDeleted("Y"); rows.add(row(92));
        service.remove(actor, token, session);
        assertThat(rows.getLast().getDeleted()).isNull(); verify(attachments, never()).merge(any());
    }
    @Test void shouldPreserveHiddenButExistingReports_whenPreparingRemoval() {
        when(reports.find(17)).thenReturn(new HRMDocument());
        assertThat(service.prepare(actor, 42, session)).isEmpty();
        verify(attachments, never()).merge(any());
    }
    @Test void shouldRejectTheRemoval_whenTheReportReappearsAfterSelection() {
        String token = service.prepare(actor, 42, session);
        when(reports.find(17)).thenReturn(new HRMDocument());
        assertThatThrownBy(() -> service.remove(actor, token, session)).isInstanceOf(IllegalArgumentException.class);
        verify(attachments, never()).merge(any()); verifyNoInteractions(pending);
    }
    @Test void shouldRefuseAnUnissuedSelection_beforeWritingAnything() {
        assertThatThrownBy(() -> service.remove(actor, "forged", session)).isInstanceOf(IllegalArgumentException.class);
        verify(attachments, never()).merge(any());
    }

    @Test void shouldSerializeSelectionsOnTheStableMutex_whenTheContainerSuppliesOne() throws Exception {
        new org.springframework.web.util.HttpSessionMutexListener()
                .sessionCreated(new jakarta.servlet.http.HttpSessionEvent(session));
        Object mutex = org.springframework.web.util.WebUtils.getSessionMutex(session);
        assertThat(mutex).isNotSameAs(session);
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            java.util.concurrent.Future<String> future;
            synchronized (mutex) {
                future = executor.submit(() -> service.prepare(actor, 42, session));
                assertThatThrownBy(() -> future.get(200, java.util.concurrent.TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
            }
            String token = future.get(3, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(token).matches("[a-f0-9-]{36}");
            service.remove(actor, token, session);
            assertThat(rows.getFirst().getDeleted()).isEqualTo("Y");
        }
    }
}
