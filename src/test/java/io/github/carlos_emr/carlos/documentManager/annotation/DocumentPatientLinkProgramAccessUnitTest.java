/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager.annotation;

import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.QueueDocumentLink;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Document;
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
class DocumentPatientLinkProgramAccessUnitTest extends CarlosUnitTestBase {
    private final DocumentDao documents = mock(DocumentDao.class);
    private final CtlDocumentDao links = mock(CtlDocumentDao.class);
    private final PatientLabRoutingDao routes = mock(PatientLabRoutingDao.class);
    private final QueueDocumentLinkDao queues = mock(QueueDocumentLinkDao.class);
    private final ProgramManager2 programs = mock(ProgramManager2.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final LoggedInInfo info = mock(LoggedInInfo.class);
    private final Document document = new Document();

    @BeforeEach void setUpDocument() {
        registerMock(DocumentDao.class, documents);
        registerMock(PatientLabRoutingDao.class, routes);
        registerMock(QueueDocumentLinkDao.class, queues);
        registerMock(ProgramManager2.class, programs);
        when(documents.find(42)).thenReturn(document);
        when(info.getLoggedInProviderNo()).thenReturn("999998");
    }

    private void authorize() {DocumentPatientLink.requireAccess(info, 42, security, links);}
    private ProgramProvider program(int id) {ProgramProvider p = new ProgramProvider(); p.setProgramId((long) id); return p;}

    @Test void inaccessibleProgramIsDeniedEvenWithoutPatientLinks() {
        document.setRestrictToProgram(true); document.setProgramId(17);
        when(programs.getProgramDomain(info, "999998")).thenReturn(List.of(program(18)));
        assertThatThrownBy(this::authorize).isInstanceOf(SecurityException.class).hasMessageContaining("program");
    }

    @Test void matchingPersistedProgramAllowsAccess() {
        document.setRestrictToProgram(true); document.setProgramId(17);
        when(programs.getProgramDomain(info, "999998")).thenReturn(List.of(program(18), program(17)));
        assertThatCode(this::authorize).doesNotThrowAnyException();
    }

    @Test void missingDocumentCannotAuthorizeProtectedRead() {
        when(documents.find(42)).thenReturn(null);
        assertThatThrownBy(this::authorize).isInstanceOf(SecurityException.class);
    }

    @Test void missingProgramDomainDoesNotMakeRestrictedDocumentPublic() {
        document.setRestrictToProgram(true); document.setProgramId(17);
        when(programs.getProgramDomain(info, "999998")).thenReturn(null);
        assertThatThrownBy(this::authorize).isInstanceOf(SecurityException.class);
    }

    @Test void unrestrictedDocumentDoesNotRequireProgramMembership() {
        document.setRestrictToProgram(false); document.setProgramId(17);
        assertThatCode(this::authorize).doesNotThrowAnyException(); verifyNoInteractions(programs);
    }

    @Test void nullRestrictionPreservesLegacyUnrestrictedBehavior() {
        document.setRestrictToProgram(null); document.setProgramId(17);
        assertThatCode(this::authorize).doesNotThrowAnyException(); verifyNoInteractions(programs);
    }

    @Test void nullProgramPreservesDocumentListSemantics() {
        document.setRestrictToProgram(true); document.setProgramId(null);
        assertThatCode(this::authorize).doesNotThrowAnyException(); verifyNoInteractions(programs);
    }

    @Test void minusOneProgramPreservesDocumentListSemantics() {
        document.setRestrictToProgram(true); document.setProgramId(-1);
        assertThatCode(this::authorize).doesNotThrowAnyException(); verifyNoInteractions(programs);
    }

    @Test void statusIsStillDecidedByTheCallingWorkflow() {
        document.setStatus('D'); document.setRestrictToProgram(false);
        assertThatCode(this::authorize).doesNotThrowAnyException();
    }

    @Test void routeOnlyPatientDenialPrecedesProgramMetadataAccess() {
        when(routes.findByLabNoAndLabType(42, "DOC")).thenReturn(List.of(new PatientLabRouting(42, "DOC", 10)));
        assertThatThrownBy(this::authorize).isInstanceOf(SecurityException.class).hasMessageContaining("patient record");
        verify(security).isAllowedAccessToPatientRecord(info, 10);
        verifyNoInteractions(documents, programs);
    }

    @Test void mixedLinksAndDocRoutesRequireAllDistinctPatientsOnce() {
        CtlDocument patient = new CtlDocument();
        patient.setId(new CtlDocumentPK("demographic", 10, 42));
        when(links.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(patient, patient));
        when(routes.findByLabNoAndLabType(42, "DOC")).thenReturn(List.of(
                new PatientLabRouting(42, "DOC", 10), new PatientLabRouting(42, "DOC", 20),
                new PatientLabRouting(42, "DOC", 20)));
        when(security.isAllowedAccessToPatientRecord(info, 10)).thenReturn(true);
        assertThatThrownBy(this::authorize).isInstanceOf(SecurityException.class).hasMessageContaining("patient record");
        verify(security, times(1)).isAllowedAccessToPatientRecord(info, 10);
        verify(security, times(1)).isAllowedAccessToPatientRecord(info, 20);
        verifyNoInteractions(documents, programs);
    }

    @Test void anotherSessionCannotReuseSuccessfulRoutedPatientAuthorization() {
        LoggedInInfo secondSession = mock(LoggedInInfo.class);
        when(routes.findByLabNoAndLabType(42, "DOC")).thenReturn(List.of(new PatientLabRouting(42, "DOC", 10)));
        when(security.isAllowedAccessToPatientRecord(info, 10)).thenReturn(true);
        assertThatCode(this::authorize).doesNotThrowAnyException();
        assertThatThrownBy(() -> DocumentPatientLink.requireAccess(secondSession, 42, security, links))
                .isInstanceOf(SecurityException.class).hasMessageContaining("patient record");
        verify(security).isAllowedAccessToPatientRecord(secondSession, 10);
        verify(routes, times(2)).findByLabNoAndLabType(42, "DOC");
    }

    @Test void newDocRouteIsRecheckedAfterEarlierSuccessfulRead() {
        when(routes.findByLabNoAndLabType(42, "DOC")).thenReturn(List.of(new PatientLabRouting(42, "DOC", 10)));
        when(security.isAllowedAccessToPatientRecord(info, 10)).thenReturn(true);
        authorize();
        when(routes.findByLabNoAndLabType(42, "DOC")).thenReturn(List.of(
                new PatientLabRouting(42, "DOC", 10), new PatientLabRouting(42, "DOC", 20)));
        assertThatThrownBy(this::authorize).isInstanceOf(SecurityException.class).hasMessageContaining("patient record");
        verify(security).isAllowedAccessToPatientRecord(info, 20);
    }

    @Test void sentinelDocRoutesAreNotPatientsAndOtherLabTypesAreNotQueried() {
        when(routes.findByLabNoAndLabType(42, "DOC")).thenReturn(List.of(
                new PatientLabRouting(42, "DOC", null), new PatientLabRouting(42, "DOC", 0),
                new PatientLabRouting(42, "DOC", -1)));
        authorize();
        verifyNoInteractions(security);
        verify(routes).findByLabNoAndLabType(42, "DOC");
        verifyNoMoreInteractions(routes);
    }


    private QueueDocumentLink queue(int id, String status) {
        QueueDocumentLink row = new QueueDocumentLink();
        row.setDocId(42); row.setQueueId(id); row.setStatus(status); return row;
    }

    @Test void activeNamedQueueDeniesDirectReadWithoutQueuePrivilege() {
        when(queues.getQueueFromDocument(42)).thenReturn(List.of(queue(7, "A")));
        assertThatThrownBy(this::authorize).isInstanceOf(SecurityException.class).hasMessageContaining("source document queue");
        verify(security).hasPrivilege(info, "_queue.7", SecurityInfoManager.READ, (String) null);
    }

    @Test void actualNamedQueuePrivilegeAllowsDirectRead() {
        when(queues.getQueueFromDocument(42)).thenReturn(List.of(queue(7, "A")));
        when(security.hasPrivilege(info, "_queue.7", SecurityInfoManager.READ, (String) null)).thenReturn(true);
        assertThatCode(this::authorize).doesNotThrowAnyException();
    }

    @Test void oneVisibleActiveQueueIsEnoughAmongDeniedQueues() {
        when(queues.getQueueFromDocument(42)).thenReturn(List.of(queue(7, "A"), queue(8, "A")));
        when(security.hasPrivilege(info, "_queue.8", SecurityInfoManager.READ, (String) null)).thenReturn(true);
        assertThatCode(this::authorize).doesNotThrowAnyException();
    }

    @Test void activeDefaultQueuePreservesSharedInboxSemantics() {
        when(queues.getQueueFromDocument(42)).thenReturn(List.of(queue(1, "A")));
        assertThatCode(this::authorize).doesNotThrowAnyException();
        verifyNoInteractions(security);
    }

    @Test void inactiveQueueDoesNotRestrictAnOtherwiseAssignedDocument() {
        when(queues.getQueueFromDocument(42)).thenReturn(List.of(queue(7, "I"), queue(8, null)));
        assertThatCode(this::authorize).doesNotThrowAnyException();
        verifyNoInteractions(security);
    }

    @Test void inactiveDefaultQueueCannotAuthorizeAnActiveDeniedNamedQueue() {
        when(queues.getQueueFromDocument(42)).thenReturn(List.of(queue(1, "I"), queue(7, "A")));
        assertThatThrownBy(this::authorize).isInstanceOf(SecurityException.class);
    }

    @Test void queueAccessIsRecheckedForEveryIndependentSessionAndAfterLinkChanges() {
        LoggedInInfo otherSession = mock(LoggedInInfo.class);
        when(queues.getQueueFromDocument(42)).thenReturn(List.of(queue(7, "A")));
        when(security.hasPrivilege(info, "_queue.7", SecurityInfoManager.READ, (String) null)).thenReturn(true);
        authorize();
        assertThatThrownBy(() -> DocumentPatientLink.requireAccess(otherSession, 42, security, links))
                .isInstanceOf(SecurityException.class).hasMessageContaining("source document queue");
        when(queues.getQueueFromDocument(42)).thenReturn(List.of(queue(8, "A")));
        assertThatThrownBy(this::authorize).isInstanceOf(SecurityException.class);
        verify(queues, times(3)).getQueueFromDocument(42);
    }

}
