/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("fast")
class StoredDocumentWriteAccessUnitTest extends CarlosUnitTestBase {
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final LoggedInInfo info = mock(LoggedInInfo.class);
    private final DocumentDao documents = mock(DocumentDao.class);
    private final CtlDocumentDao links = mock(CtlDocumentDao.class);
    private final PatientLabRoutingDao routes = mock(PatientLabRoutingDao.class);
    private final QueueDocumentLinkDao queues = mock(QueueDocumentLinkDao.class);
    private final Document document = new Document();

    @BeforeEach void setUpDocument() {
        registerMock(DocumentDao.class, documents); registerMock(CtlDocumentDao.class, links);
        registerMock(PatientLabRoutingDao.class, routes); registerMock(QueueDocumentLinkDao.class, queues);
        when(security.hasPrivilege(info, "_edoc", "w", (String) null)).thenReturn(true);
        when(documents.find(42)).thenReturn(document);
    }
    private void authorize() {IncomingDocumentCapacityResponse.requireStoredDocumentWriteAccess(security, info, "42");}
    private CtlDocument link(int patient) {
        CtlDocument link = new CtlDocument(); link.setId(new CtlDocumentPK("demographic", patient, 42)); return link;
    }
    private PatientLabRouting route(int patient) {
        PatientLabRouting route = new PatientLabRouting(); route.setDemographicNo(patient); route.setLabType("DOC"); return route;
    }
    private void allow(int patient) {
        when(security.isAllowedAccessToPatientRecord(info, patient)).thenReturn(true);
        when(security.hasPrivilege(info, "_edoc", "w", Integer.toString(patient))).thenReturn(true);
    }

    @Test void secondPersistedPatientCannotBeBypassedByFirstAccessiblePatient() {
        when(links.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(link(100), link(200)));
        allow(100);
        assertThatThrownBy(this::authorize).isInstanceOf(SecurityException.class);
        verify(security).isAllowedAccessToPatientRecord(info, 200);
    }

    @Test void docRoutingPatientIsCheckedEvenWithoutCtlDocumentPatient() {
        when(routes.findDocByDemographic(42)).thenReturn(List.of(route(200)));
        assertThatThrownBy(this::authorize).isInstanceOf(SecurityException.class);
    }

    @Test void chartAccessAloneDoesNotGrantPatientSpecificWritePermission() {
        when(links.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(link(100)));
        when(security.isAllowedAccessToPatientRecord(info, 100)).thenReturn(true);
        assertThatThrownBy(this::authorize).isInstanceOf(SecurityException.class).hasMessageContaining("write");
    }

    @Test void allAuthorizedPatientsAndRoutingSucceed() {
        when(links.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(link(100)));
        when(routes.findDocByDemographic(42)).thenReturn(List.of(route(200), route(100)));
        allow(100); allow(200);
        assertThatCode(this::authorize).doesNotThrowAnyException();
    }

    @Test void positiveUnfiledSentinelsAreNotInventedPatients() {
        when(links.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(link(0), link(-1)));
        when(routes.findDocByDemographic(42)).thenReturn(List.of(route(0)));
        assertThatCode(this::authorize).doesNotThrowAnyException();
        verify(security, never()).isAllowedAccessToPatientRecord(any(), anyInt());
    }

    @Test void inaccessiblePersistedNamedQueueIsDenied() {
        QueueDocumentLink queue = new QueueDocumentLink(); queue.setQueueId(17); queue.setStatus("A");
        when(queues.getQueueFromDocument(42)).thenReturn(List.of(queue));
        assertThatThrownBy(this::authorize).isInstanceOf(SecurityException.class).hasMessageContaining("queue");
    }

    @Test void allowedNamedQueuePreservesSharedQueueVisibilitySemantics() {
        QueueDocumentLink queue = new QueueDocumentLink(); queue.setQueueId(17); queue.setStatus("A");
        when(queues.getQueueFromDocument(42)).thenReturn(List.of(queue));
        when(security.hasPrivilege(info, "_queue.17", "r", (String) null)).thenReturn(true);
        assertThatCode(this::authorize).doesNotThrowAnyException();
    }

    @Test void accessiblePatientDoesNotOverrideRestrictedDocumentProgram() {
        when(links.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(link(100)));
        allow(100); document.setRestrictToProgram(true); document.setProgramId(17);
        ProgramManager2 programs = mock(ProgramManager2.class); registerMock(ProgramManager2.class, programs);
        assertThatThrownBy(this::authorize).isInstanceOf(SecurityException.class).hasMessageContaining("program");
    }

    @Test void malformedSourceIdCannotFallThroughToADifferentDocument() {
        assertThatThrownBy(() -> IncomingDocumentCapacityResponse.requireStoredDocumentWriteAccess(security, info, "42x"))
            .isInstanceOf(SecurityException.class);
        verifyNoInteractions(documents, links, routes, queues);
    }

    @Test void globalPermissionRemainsRequired() {
        when(security.hasPrivilege(info, "_edoc", "w", (String) null)).thenReturn(false);
        assertThatThrownBy(this::authorize).isInstanceOf(SecurityException.class);
        verifyNoInteractions(documents, links, routes, queues);
    }
}
