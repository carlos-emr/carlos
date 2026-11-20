// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("fast")
class DocumentAttachLabUnitTest extends CarlosUnitTestBase {
    private ConsultDocsDao consultDocs;
    private EFormDocsDao eformDocs;
    private PatientLabRoutingDao routing;
    private ConsultationRequestDao consults;
    private EFormDataDao eforms;
    private PlatformTransactionManager transactions;
    private DocumentAttach attachments;

    @BeforeEach
    void setUp() {
        consultDocs = createAndRegisterMock(ConsultDocsDao.class);
        eformDocs = createAndRegisterMock(EFormDocsDao.class);
        routing = createAndRegisterMock(PatientLabRoutingDao.class);
        consults = createAndRegisterMock(ConsultationRequestDao.class);
        eforms = createAndRegisterMock(EFormDataDao.class);
        transactions = createAndRegisterMock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        attachments = new DocumentAttach(42, false);
    }

    private void consultOwner(int patient) {
        ConsultationRequest owner = new ConsultationRequest();
        owner.setDemographicId(patient);
        when(consults.lockForAttachmentSync(12)).thenReturn(owner);
    }

    private void eformOwner(int patient) {
        EFormData owner = new EFormData();
        owner.setDemographicId(patient);
        when(eforms.lockForAttachmentSync(12)).thenReturn(owner);
    }

    private void routed(String source, int id, int patient) {
        PatientLabRouting row = new PatientLabRouting();
        row.setDemographicNo(patient);
        when(routing.findByLabNoAndLabType(id, source)).thenReturn(List.of(row));
    }

    @Test
    void shouldPersistBothSources_whenConsultLabsHaveSameNumericId() {
        consultOwner(42);
        routed("HL7", 7, 42);
        routed("MDS", 7, 42);
        attachments.attachToConsult(new String[]{"HL7:7", "MDS:7", "HL7:7"}, DocumentType.LAB, "provider", 12);
        ArgumentCaptor<ConsultDocs> rows = ArgumentCaptor.forClass(ConsultDocs.class);
        verify(consultDocs, times(2)).persist(rows.capture());
        assertThat(rows.getAllValues()).extracting(ConsultDocs::getLabType).containsExactly("HL7", "MDS");
        assertThat(rows.getAllValues()).allSatisfy(row -> {
            assertThat(row.getDocumentNo()).isEqualTo(7);
            assertThat(row.getRequestId()).isEqualTo(12);
            assertThat(row.getProviderNo()).isEqualTo("provider");
        });
        verify(transactions).commit(any());
    }

    @Test
    void shouldDetachOnlySelectedSource_whenEformLabsHaveSameNumericId() {
        eformOwner(42);
        routed("MDS", 7, 42);
        EFormDocs hl7 = new EFormDocs(12, 7, "L", "provider");
        hl7.setId(1);
        hl7.setLabType("HL7");
        EFormDocs mds = new EFormDocs(12, 7, "L", "provider");
        mds.setId(2);
        mds.setLabType("MDS");
        when(eformDocs.findByFdidIdDocTypeForUpdate(12, "L")).thenReturn(List.of(hl7, mds));
        attachments.attachToEForm(new String[]{"MDS:7"}, DocumentType.LAB, "provider", 12);
        assertThat(hl7.getDeleted()).isEqualTo("Y");
        assertThat(mds.getDeleted()).isNull();
        verify(eformDocs).merge(hl7);
        verify(eformDocs, never()).merge(mds);
        verify(eformDocs, never()).persist(any());
    }

    @Test
    void shouldValidateWholeSelectionBeforeDetach_whenOneLabBelongsToAnotherPatient() {
        consultOwner(42);
        routed("HL7", 8, 99);
        ConsultDocs existing = new ConsultDocs(12, 7, "L", "provider");
        existing.setLabType("MDS");
        when(consultDocs.findByRequestIdDocTypeForUpdate(12, "L")).thenReturn(List.of(existing));
        assertThatThrownBy(() -> attachments.attachToConsult(new String[]{"HL7:8"}, DocumentType.LAB, "provider", 12))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(existing.getDeleted()).isNull();
        verify(consultDocs, never()).merge(any());
        verify(consultDocs, never()).persist(any());
        verify(transactions).rollback(any());
    }

    @Test
    void shouldRejectWrongParentPatient_whenSubmittingEformAttachments() {
        eformOwner(99);
        assertThatThrownBy(() -> attachments.attachToEForm(new String[]{}, DocumentType.LAB, "provider", 12))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(eformDocs, routing);
    }

    @Test
    void shouldKeepUnresolvedLegacyRow_whenAlreadyAttachedAndUnchanged() {
        consultOwner(42);
        ConsultDocs existing = new ConsultDocs(12, 7, "L", "provider");
        when(consultDocs.findByRequestIdDocTypeForUpdate(12, "L")).thenReturn(List.of(existing));
        attachments.attachToConsult(new String[]{"UNRESOLVED:7"}, DocumentType.LAB, "provider", 12);
        verify(consultDocs, never()).merge(any());
        verify(consultDocs, never()).persist(any());
        assertThat(existing.getLabType()).isNull();
    }

    @Test
    void shouldRejectInventedUnresolvedRow_whenNoSuchAttachmentExists() {
        consultOwner(42);
        assertThatThrownBy(() -> attachments.attachToConsult(new String[]{"UNRESOLVED:7"}, DocumentType.LAB, "provider", 12))
                .isInstanceOf(IllegalArgumentException.class);
        verify(consultDocs, never()).persist(any());
    }

    @Test
    void shouldRejectNonHl7BeforeWrites_whenOceanCannotCarryLabSource() {
        consultOwner(42);
        routed("MDS", 7, 42);
        DocumentAttach ocean = new DocumentAttach(42, true);
        assertThatThrownBy(() -> ocean.attachToConsult(new String[]{"MDS:7"}, DocumentType.LAB, "provider", 12))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Ocean");
        verify(consultDocs, never()).persist(any());
        verify(consultDocs, never()).merge(any());
    }
    @Test
    void shouldRollbackBeforeDetaching_whenDocumentOwnershipValidationFails() {
        consultOwner(42);
        var info = mock(io.github.carlos_emr.carlos.utility.LoggedInInfo.class);
        var access = createAndRegisterMock(AttachmentSelectionAccess.class);
        ConsultDocs existing = new ConsultDocs(12, 7, "D", "provider");
        when(consultDocs.findByRequestIdDocTypeForUpdate(12, "D")).thenReturn(List.of(existing));
        when(access.validate(eq(info), eq(DocumentType.DOC), eq(42), any(), any()))
                .thenThrow(new IllegalArgumentException("Attachment does not belong to this patient"));
        DocumentAttach secured = new DocumentAttach(info, 42, false);
        assertThatThrownBy(() -> secured.attachToConsult(new String[]{"8"}, DocumentType.DOC, "provider", 12))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(existing.getDeleted()).isNull();
        verify(consultDocs, never()).persist(any());
        verify(consultDocs, never()).merge(any());
        verify(transactions).rollback(any());
    }

    @Test
    void shouldPreserveRestrictedEformDocuments_withoutWriting() {
        eformOwner(42);
        var info = mock(io.github.carlos_emr.carlos.utility.LoggedInInfo.class);
        var access = createAndRegisterMock(AttachmentSelectionAccess.class);
        EFormDocs existing = new EFormDocs(12, 7, "D", "provider");
        when(eformDocs.findByFdidIdDocTypeForUpdate(12, "D")).thenReturn(List.of(existing));
        when(access.validate(info, DocumentType.DOC, 42, List.of("7"), List.of("7"))).thenReturn(false);
        new DocumentAttach(info, 42, false).attachToEForm(new String[]{"7"}, DocumentType.DOC, "provider", 12);
        verify(eformDocs, never()).persist(any());
        verify(eformDocs, never()).merge(any());
        assertThat(existing.getDeleted()).isNull();
    }

}
