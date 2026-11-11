// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestExtDao;
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ConsultationHrmAttachmentPersisterUnitTest extends CarlosUnitTestBase {
    private ConsultationRequestDao requests;
    private ConsultDocsDao attachments;
    private ConsultationRequestExtDao extensions;
    private SecurityInfoManager security;
    private LoggedInInfo info;
    private ConsultationHrmAttachmentPersister persister;

    @BeforeEach
    void setUp() {
        requests = createAndRegisterMock(ConsultationRequestDao.class);
        attachments = createAndRegisterMock(ConsultDocsDao.class);
        extensions = createAndRegisterMock(ConsultationRequestExtDao.class);
        security = createAndRegisterMock(SecurityInfoManager.class);
        info = mock(LoggedInInfo.class);
        var owner = new ConsultationRequest(); owner.setDemographicId(42);
        when(requests.lockForAttachmentSync(11)).thenReturn(owner);
        lenient().when(security.hasPrivilege(info, "_con", "w", 42)).thenReturn(true);
        lenient().when(security.hasPrivilege(info, "_hrm", "r", (String) null)).thenReturn(true);
        persister = new ConsultationHrmAttachmentPersister(requests, attachments, extensions, security);
    }

    private ConsultDocs row(int id, int parent, int report) {
        var row = new ConsultDocs(parent, report, ConsultDocs.DOCTYPE_HRM, "999998");
        row.setId(id); return row;
    }

    @Test
    void shouldSoftDeleteOnlySelectedRelationships_withoutRequiringTheDeletedReport() {
        var first = row(7, 11, 128); var duplicate = row(8, 11, 128); var other = row(9, 11, 129);
        when(attachments.find(7)).thenReturn(first);
        when(attachments.findByRequestIdDocTypeForUpdate(11, ConsultDocs.DOCTYPE_HRM))
                .thenReturn(List.of(first, duplicate, other));
        {
            persister.detach(info, 11, 7);
            assertThat(first.getDeleted()).isEqualTo("Y"); assertThat(duplicate.getDeleted()).isEqualTo("Y");
            assertThat(other.getDeleted()).isNull();
            verify(attachments, never()).remove(any());
            logActionMock.verify(() -> LogAction.addLogStrict(info, "delete", "consultation HRM attachment", "7", "42", "explicit detach"));
        }
        verify(security, never()).isAllowedAccessToPatientRecord(any(), any());
    }

    @Test
    void shouldRejectForeignRelationship_withoutMutation() {
        var foreign = row(7, 99, 128);
        when(attachments.find(7)).thenReturn(foreign);
        assertThatThrownBy(() -> persister.detach(info, 11, 7)).isInstanceOf(NoSuchElementException.class);
        verify(attachments, never()).merge(any());
    }

    @Test
    void shouldKeepStoredLink_whenHrmPrivilegeIsDenied() {
        when(security.hasPrivilege(info, "_hrm", "r", (String) null)).thenReturn(false);
        assertThatThrownBy(() -> persister.detach(info, 11, 7)).isInstanceOf(SecurityException.class);
        verifyNoInteractions(attachments, extensions);
    }

    @Test
    void shouldKeepReplayIdempotent_whenRelationshipWasAlreadyRemoved() {
        var removed = row(7, 11, 128); removed.setDeleted("Y");
        when(attachments.find(7)).thenReturn(removed);
        {
            persister.detach(info, 11, 7);
            verify(attachments, never()).merge(any()); logActionMock.verifyNoInteractions();
        }
    }
}
