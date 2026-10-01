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
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.TicklerDao;
import io.github.carlos_emr.carlos.commn.dao.TicklerDocsDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.commn.model.TicklerDocs;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.data.AttachmentLabResultData;
import io.github.carlos_emr.carlos.documentManager.data.TicklerAttachmentData;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TicklerAttachmentService}: the patient-ownership and per-type
 * privilege checks the parallel fork lacked, the per-type sync semantics, and name resolution
 * with redaction.
 *
 * @since 2026-09-26
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TicklerAttachmentService Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("tickler")
@Tag("security")
class TicklerAttachmentServiceUnitTest extends CarlosUnitTestBase {

    private static final int TICKLER_ID = 42;
    private static final int DEMOGRAPHIC_NO = 1001;
    private static final String PROVIDER_NO = "999998";

    @Mock private TicklerDocsDao ticklerDocsDao;
    @Mock private TicklerDao ticklerDao;
    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private DocumentDao documentDao;
    @Mock private PatientLabRoutingDao patientLabRoutingDao;
    @Mock private EFormDataDao eFormDataDao;
    @Mock private HRMDocumentToDemographicDao hrmDocumentToDemographicDao;
    @Mock private FormsManager formsManager;
    @Mock private DocumentAttachmentManager documentAttachmentManager;
    @Mock private TicklerDocumentAccess ticklerDocumentAccess;
    @Mock private LoggedInInfo loggedInInfo;

    private TicklerAttachmentService service;
    private Tickler tickler;

    @BeforeEach
    void setUp() {
        service = new TicklerAttachmentService(ticklerDocsDao, securityInfoManager, documentDao,
                patientLabRoutingDao, eFormDataDao, hrmDocumentToDemographicDao, formsManager,
                documentAttachmentManager, ticklerDao, ticklerDocumentAccess);
        tickler = new Tickler();
        tickler.setId(TICKLER_ID);
        tickler.setDemographicNo(DEMOGRAPHIC_NO);
        lenient().when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
        lenient().when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), anyString()))
                .thenReturn(true);
        lenient().when(ticklerDocumentAccess.canRead(any(LoggedInInfo.class), anyInt())).thenReturn(true);
    }

    private static Map<DocumentType, Set<String>> submission(DocumentType type, String... ids) {
        Map<DocumentType, Set<String>> submitted = new EnumMap<>(DocumentType.class);
        submitted.put(type, Set.of(ids));
        return submitted;
    }

    private static TicklerDocs stored(int documentNo, String docType) {
        TicklerDocs row = new TicklerDocs(TICKLER_ID, documentNo, docType, PROVIDER_NO);
        row.setId(documentNo * 10);
        return row;
    }

    private void documentOwnedBy(int documentNo, int demographicNo) {
        CtlDocumentPK key = new CtlDocumentPK();
        key.setModule("demographic");
        key.setModuleId(demographicNo);
        CtlDocument ctlDocument = new CtlDocument();
        ctlDocument.setId(key);
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{new Document(), ctlDocument});
        when(documentDao.findCtlDocsAndDocsByDocNo(documentNo)).thenReturn(rows);
    }

    /** A document filed against the patient with the given document and ctl_document statuses. */
    private void documentLinkedWithStatus(int documentNo, int demographicNo, char documentStatus, String linkStatus) {
        CtlDocumentPK key = new CtlDocumentPK();
        key.setModule("demographic");
        key.setModuleId(demographicNo);
        CtlDocument ctlDocument = new CtlDocument();
        ctlDocument.setId(key);
        ctlDocument.setStatus(linkStatus);
        Document document = new Document();
        document.setStatus(documentStatus);
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{document, ctlDocument});
        when(documentDao.findCtlDocsAndDocsByDocNo(documentNo)).thenReturn(rows);
    }

    private void labOwnedBy(int labNo, int demographicNo, String labType) {
        PatientLabRouting routing = new PatientLabRouting();
        routing.setLabNo(labNo);
        routing.setLabType(labType);
        routing.setDemographicNo(demographicNo);
        when(patientLabRoutingDao.findByLabNoAndLabType(labNo, labType)).thenReturn(List.of(routing));
    }

    @Nested
    @DisplayName("syncAttachments")
    class SyncAttachments {

        @Test
        @DisplayName("should attach a patient's document with the session provider and audit it")
        void shouldAttachDocument_whenItBelongsToPatient() {
            documentOwnedBy(11, DEMOGRAPHIC_NO);
            when(ticklerDocsDao.findAllByTicklerIdForUpdate(TICKLER_ID)).thenReturn(List.of());

            service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.DOC, "11"));

            ArgumentCaptor<TicklerDocs> captor = ArgumentCaptor.forClass(TicklerDocs.class);
            verify(ticklerDocsDao).persist(captor.capture());
            assertThat(captor.getValue().getTicklerId()).isEqualTo(TICKLER_ID);
            assertThat(captor.getValue().getDocumentNo()).isEqualTo(11);
            assertThat(captor.getValue().getDocType()).isEqualTo("D");
            assertThat(captor.getValue().getProviderNo()).isEqualTo(PROVIDER_NO);
            assertThat(captor.getValue().getLabType()).isNull();
            logActionMock.verify(() -> LogAction.addLogSynchronous(eq(loggedInInfo),
                    eq("TicklerAttachmentService.add"), eq("ticklerId=42,type=D,documentNo=11")));
        }

        @Test
        @DisplayName("should reject a document that belongs to another patient before any write")
        void shouldRejectDocument_whenItBelongsToAnotherPatient() {
            documentOwnedBy(11, DEMOGRAPHIC_NO + 1);

            assertThatThrownBy(() -> service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.DOC, "11")))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("does not belong to the patient");

            verify(ticklerDocsDao, never()).persist(any());
            verify(ticklerDocsDao, never()).merge(any());
        }

        @Test
        @DisplayName("should refuse to attach a document whose patient link was deleted")
        void shouldRejectDocument_whenPatientLinkDeleted() {
            documentLinkedWithStatus(11, DEMOGRAPHIC_NO, 'A', "D");
            when(ticklerDocsDao.findAllByTicklerIdForUpdate(TICKLER_ID)).thenReturn(List.of());

            assertThatThrownBy(() -> service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.DOC, "11")))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("does not belong to the patient");
            verify(ticklerDocsDao, never()).persist(any());
            verify(ticklerDocsDao, never()).merge(any());
        }

        @Test
        @DisplayName("should refuse to attach a deleted document even through a live link")
        void shouldRejectDocument_whenDocumentDeleted() {
            documentLinkedWithStatus(11, DEMOGRAPHIC_NO, 'D', "A");
            when(ticklerDocsDao.findAllByTicklerIdForUpdate(TICKLER_ID)).thenReturn(List.of());

            assertThatThrownBy(() -> service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.DOC, "11")))
                    .isInstanceOf(SecurityException.class);
            verify(ticklerDocsDao, never()).persist(any());
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.CsvSource(value = {"A", "NULL"}, nullValues = "NULL")
        @DisplayName("should attach a document through a live or legacy NULL-status link")
        void shouldAttachDocument_whenPatientLinkLive(String linkStatus) {
            documentLinkedWithStatus(11, DEMOGRAPHIC_NO, 'A', linkStatus);
            when(ticklerDocsDao.findAllByTicklerIdForUpdate(TICKLER_ID)).thenReturn(List.of());

            service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.DOC, "11"));

            verify(ticklerDocsDao).persist(any(TicklerDocs.class));
        }

        @Test
        @DisplayName("should detach a stored document whose patient link has since been deleted")
        void shouldDetachStoredDocument_whenPatientLinkDeleted() {
            TicklerDocs storedDoc = stored(11, "D");
            documentLinkedWithStatus(11, DEMOGRAPHIC_NO, 'A', "D");
            when(ticklerDocsDao.findAllByTicklerIdForUpdate(TICKLER_ID)).thenReturn(List.of(storedDoc));

            service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.DOC, "11"));

            assertThat(storedDoc.getDeleted()).isEqualTo(TicklerDocs.DELETED_FLAG);
            verify(ticklerDocsDao).merge(storedDoc);
            verify(ticklerDocsDao, never()).persist(any());
        }

        @Test
        @DisplayName("should reject an unknown document id")
        void shouldRejectDocument_whenItDoesNotExist() {
            when(documentDao.findCtlDocsAndDocsByDocNo(11)).thenReturn(List.of());

            assertThatThrownBy(() -> service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.DOC, "11")))
                    .isInstanceOf(SecurityException.class);
            verify(ticklerDocsDao, never()).persist(any());
        }

        @Test
        @DisplayName("should accept a lab when a later source-qualified routing row belongs to the patient")
        void shouldAcceptLab_whenAnyMatchingRoutingRowBelongsToPatient() {
            var other = new PatientLabRouting(77, "MDS", DEMOGRAPHIC_NO + 1);
            var owned = new PatientLabRouting(77, "MDS", DEMOGRAPHIC_NO);
            when(patientLabRoutingDao.findByLabNoAndLabType(77, "MDS")).thenReturn(List.of(other, owned));
            when(ticklerDocsDao.findAllByTicklerIdForUpdate(TICKLER_ID)).thenReturn(List.of());

            service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.LAB, "MDS:77"));

            ArgumentCaptor<TicklerDocs> saved = ArgumentCaptor.forClass(TicklerDocs.class);
            verify(ticklerDocsDao).persist(saved.capture());
            assertThat(saved.getValue().getLabType()).isEqualTo("MDS");
            assertThat(saved.getValue().getDocumentNo()).isEqualTo(77);
        }

        @Test
        @DisplayName("should check a lab against its own source's routing and record that source")
        void shouldKeepLabType_whenAttachingLab() {
            labOwnedBy(77, DEMOGRAPHIC_NO, "MDS");
            when(ticklerDocsDao.findAllByTicklerIdForUpdate(TICKLER_ID)).thenReturn(List.of());

            service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.LAB, "MDS:77"));

            ArgumentCaptor<TicklerDocs> captor = ArgumentCaptor.forClass(TicklerDocs.class);
            verify(ticklerDocsDao).persist(captor.capture());
            assertThat(captor.getValue().getLabType()).isEqualTo("MDS");
            assertThat(captor.getValue().getDocumentNo()).isEqualTo(77);
            verify(patientLabRoutingDao, never()).findByLabNoAndLabType(anyInt(), eq("HL7"));
        }

        @Test
        @DisplayName("should read a bare lab id as an HL7 lab")
        void shouldDefaultToHl7_whenLabValueHasNoSource() {
            labOwnedBy(77, DEMOGRAPHIC_NO, "HL7");
            when(ticklerDocsDao.findAllByTicklerIdForUpdate(TICKLER_ID)).thenReturn(List.of());

            service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.LAB, "77"));

            ArgumentCaptor<TicklerDocs> captor = ArgumentCaptor.forClass(TicklerDocs.class);
            verify(ticklerDocsDao).persist(captor.capture());
            assertThat(captor.getValue().getLabType()).isEqualTo("HL7");
        }

        @Test
        @DisplayName("should reject a lab routed to another patient")
        void shouldRejectLab_whenRoutedToAnotherPatient() {
            labOwnedBy(77, DEMOGRAPHIC_NO + 5, "HL7");

            assertThatThrownBy(() -> service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.LAB, "77")))
                    .isInstanceOf(SecurityException.class);
            verify(ticklerDocsDao, never()).persist(any());
        }

        @Test
        @DisplayName("should not accept an HL7 lab's id under another source")
        void shouldRejectLab_whenSourceDoesNotRouteThatId() {
            // The patient's HL7 lab 77 exists, but nothing routes MDS lab 77 to anyone.
            PatientLabRouting hl7Routing = new PatientLabRouting();
            hl7Routing.setLabNo(77);
            hl7Routing.setLabType("HL7");
            hl7Routing.setDemographicNo(DEMOGRAPHIC_NO);
            lenient().when(patientLabRoutingDao.findByLabNoAndLabType(77, "HL7")).thenReturn(List.of(hl7Routing));

            assertThatThrownBy(() -> service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.LAB, "MDS:77")))
                    .isInstanceOf(SecurityException.class);
            verify(ticklerDocsDao, never()).persist(any());
        }

        @Test
        @DisplayName("should treat the same id under two sources as two different labs")
        void shouldKeepBothLabs_whenSourcesDiffer() {
            labOwnedBy(77, DEMOGRAPHIC_NO, "MDS");
            labOwnedBy(77, DEMOGRAPHIC_NO, "HL7");
            TicklerDocs storedHl7 = stored(77, "L");
            storedHl7.setLabType("HL7");
            when(ticklerDocsDao.findAllByTicklerIdForUpdate(TICKLER_ID)).thenReturn(List.of(storedHl7));

            service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.LAB, "HL7:77", "MDS:77"));

            ArgumentCaptor<TicklerDocs> captor = ArgumentCaptor.forClass(TicklerDocs.class);
            verify(ticklerDocsDao).persist(captor.capture());
            assertThat(captor.getValue().getLabType()).isEqualTo("MDS");
            assertThat(storedHl7.getDeleted()).isNull();
            verify(ticklerDocsDao, never()).merge(any());
        }

        @Test
        @DisplayName("should refuse a document id submitted under the DOC routing source as a lab")
        void shouldThrowIllegalArgument_whenLabSourceIsDocRoute() {
            assertThatThrownBy(() -> service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.LAB, "DOC:11")))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(patientLabRoutingDao, never()).findByLabNoAndLabType(anyInt(), anyString());
            verify(ticklerDocsDao, never()).persist(any());
        }

        @Test
        @DisplayName("should reject a malformed lab value")
        void shouldThrowIllegalArgument_whenLabValueMalformed() {
            assertThatThrownBy(() -> service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.LAB, "MDS:")))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(ticklerDocsDao, never()).persist(any());
        }

        @Test
        @DisplayName("should reject an eForm that belongs to another patient")
        void shouldRejectEForm_whenItBelongsToAnotherPatient() {
            EFormData eForm = new EFormData();
            eForm.setDemographicId(DEMOGRAPHIC_NO + 1);
            when(eFormDataDao.find(5)).thenReturn(eForm);

            assertThatThrownBy(() -> service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.EFORM, "5")))
                    .isInstanceOf(SecurityException.class);
            verify(ticklerDocsDao, never()).persist(any());
        }

        @Test
        @DisplayName("should attach an HRM report linked to the patient")
        void shouldAttachHrm_whenLinkedToPatient() {
            HRMDocumentToDemographic link = new HRMDocumentToDemographic();
            link.setDemographicNo(DEMOGRAPHIC_NO);
            when(hrmDocumentToDemographicDao.findByHrmDocumentId(9)).thenReturn(List.of(link));
            when(ticklerDocsDao.findAllByTicklerIdForUpdate(TICKLER_ID)).thenReturn(List.of());

            service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.HRM, "9"));

            verify(ticklerDocsDao).persist(any(TicklerDocs.class));
        }

        @Test
        @DisplayName("should reject an encounter form that is not among the patient's forms")
        void shouldRejectForm_whenNotThePatients() {
            when(formsManager.getEncounterFormsbyDemographicNumber(loggedInInfo, DEMOGRAPHIC_NO, true, false))
                    .thenReturn(List.of(new EctFormData.PatientForm("Rourke", 3, DEMOGRAPHIC_NO, new Date(), new Date())));

            assertThatThrownBy(() -> service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.FORM, "4")))
                    .isInstanceOf(SecurityException.class);
            verify(ticklerDocsDao, never()).persist(any());
        }

        @Test
        @DisplayName("should soft-delete stored items of a submitted type that were not resubmitted")
        void shouldDetachRemovedItems_whenTypeIsResubmitted() {
            // A resubmitted stored item is re-verified against the document's current filing.
            TicklerDocs kept = stored(11, "D");
            TicklerDocs removed = stored(12, "D");
            when(ticklerDocsDao.findAllByTicklerIdForUpdate(TICKLER_ID)).thenReturn(List.of(kept, removed));
            documentOwnedBy(11, DEMOGRAPHIC_NO);
            documentOwnedBy(12, DEMOGRAPHIC_NO);

            service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.DOC, "11"));

            assertThat(removed.getDeleted()).isEqualTo(TicklerDocs.DELETED_FLAG);
            assertThat(kept.getDeleted()).isNull();
            verify(ticklerDocsDao).merge(removed);
            verify(ticklerDocsDao, never()).persist(any());
            logActionMock.verify(() -> LogAction.addLogSynchronous(eq(loggedInInfo),
                    eq("TicklerAttachmentService.delete"), eq("ticklerId=42,type=D,documentNo=12")));
        }

        @Test
        @DisplayName("should preserve a document hidden by item-level access while removing a readable document")
        void shouldKeepRestrictedDocument_whenVisibleSelectionCleared() {
            TicklerDocs restricted = stored(11, "D");
            TicklerDocs visible = stored(12, "D");
            when(ticklerDocsDao.findAllByTicklerIdForUpdate(TICKLER_ID)).thenReturn(List.of(restricted, visible));
            documentOwnedBy(11, DEMOGRAPHIC_NO);
            documentOwnedBy(12, DEMOGRAPHIC_NO);
            when(ticklerDocumentAccess.canRead(loggedInInfo, 11)).thenReturn(false);

            service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.DOC));

            assertThat(restricted.getDeleted()).isNull();
            assertThat(visible.getDeleted()).isEqualTo(TicklerDocs.DELETED_FLAG);
            verify(ticklerDocsDao).merge(visible);
            verify(ticklerDocsDao, never()).merge(restricted);
        }

        @Test
        @DisplayName("should refuse a new document whose program or queue the caller cannot access")
        void shouldRefuseNewDocument_withoutItemAccess() {
            documentOwnedBy(11, DEMOGRAPHIC_NO);
            when(ticklerDocsDao.findAllByTicklerIdForUpdate(TICKLER_ID)).thenReturn(List.of());
            org.mockito.Mockito.doThrow(new SecurityException("Document access denied"))
                    .when(ticklerDocumentAccess).requireRead(loggedInInfo, 11);

            assertThatThrownBy(() -> service.syncAttachments(loggedInInfo, tickler,
                    submission(DocumentType.DOC, "11")))
                    .isInstanceOf(SecurityException.class);
            verify(ticklerDocsDao, never()).persist(any());
        }

        @Test
        @DisplayName("should detach a live item that has since moved to another patient, even when resubmitted")
        void shouldDetachStaleItem_whenResubmittedButNoLongerThePatients() {
            TicklerDocs moved = stored(11, "D");
            TicklerDocs kept = stored(12, "D");
            when(ticklerDocsDao.findAllByTicklerIdForUpdate(TICKLER_ID)).thenReturn(List.of(moved, kept));
            documentOwnedBy(11, DEMOGRAPHIC_NO + 1);
            documentOwnedBy(12, DEMOGRAPHIC_NO);

            service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.DOC, "11", "12"));

            assertThat(moved.getDeleted()).isEqualTo(TicklerDocs.DELETED_FLAG);
            assertThat(kept.getDeleted()).isNull();
            verify(ticklerDocsDao).merge(moved);
            verify(ticklerDocsDao, never()).persist(any());
            logActionMock.verify(() -> LogAction.addLogSynchronous(eq(loggedInInfo),
                    eq("TicklerAttachmentService.delete"), eq("ticklerId=42,type=D,documentNo=11")));
        }

        @Test
        @DisplayName("should leave types that were not submitted untouched")
        void shouldLeaveOtherTypesAlone_whenOnlyOneTypeSubmitted() {
            documentOwnedBy(11, DEMOGRAPHIC_NO);
            TicklerDocs storedLab = stored(77, "L");
            storedLab.setLabType("HL7");
            when(ticklerDocsDao.findAllByTicklerIdForUpdate(TICKLER_ID)).thenReturn(List.of(storedLab));

            service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.DOC, "11"));

            assertThat(storedLab.getDeleted()).isNull();
            verify(ticklerDocsDao, never()).merge(any());
            verify(ticklerDocsDao, times(1)).persist(any(TicklerDocs.class));
        }

        @Test
        @DisplayName("should lock the tickler before reading its rows, so concurrent syncs run one after the other")
        void shouldLockTickler_beforeReadingStoredRows() {
            documentOwnedBy(11, DEMOGRAPHIC_NO);
            when(ticklerDocsDao.findAllByTicklerIdForUpdate(TICKLER_ID)).thenReturn(List.of());

            service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.DOC, "11"));

            org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(ticklerDao, ticklerDocsDao);
            inOrder.verify(ticklerDao).lockForAttachmentSync(TICKLER_ID);
            inOrder.verify(ticklerDocsDao).findAllByTicklerIdForUpdate(TICKLER_ID);
            inOrder.verify(ticklerDocsDao).persist(any(TicklerDocs.class));
            verify(ticklerDocsDao, times(1)).findAllByTicklerIdForUpdate(TICKLER_ID);
        }

        @Test
        @DisplayName("should revive the detached row when an item is attached again")
        void shouldReviveDetachedRow_whenItemReattached() {
            // Ownership is re-proven for the re-attachment, as for any item not currently live.
            documentOwnedBy(11, DEMOGRAPHIC_NO);
            TicklerDocs detached = stored(11, "D");
            detached.setDeleted(TicklerDocs.DELETED_FLAG);
            detached.setProviderNo("000001");
            when(ticklerDocsDao.findAllByTicklerIdForUpdate(TICKLER_ID)).thenReturn(List.of(detached));

            service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.DOC, "11"));

            assertThat(detached.getDeleted()).isNull();
            assertThat(detached.getProviderNo()).isEqualTo(PROVIDER_NO);
            assertThat(detached.getAttachDate()).isNotNull();
            verify(ticklerDocsDao).merge(detached);
            verify(ticklerDocsDao, never()).persist(any());
            logActionMock.verify(() -> LogAction.addLogSynchronous(eq(loggedInInfo),
                    eq("TicklerAttachmentService.add"), eq("ticklerId=42,type=D,documentNo=11")));
        }

        @Test
        @DisplayName("should do nothing when no types are submitted")
        void shouldDoNothing_whenSubmissionIsEmpty() {
            service.syncAttachments(loggedInInfo, tickler, new EnumMap<>(DocumentType.class));
            service.syncAttachments(loggedInInfo, tickler, null);

            verify(ticklerDao, never()).lockForAttachmentSync(any());
            verify(ticklerDocsDao, never()).findAllByTicklerIdForUpdate(any());
            verify(ticklerDocsDao, never()).persist(any());
        }

        @Test
        @DisplayName("should require tickler write on the patient")
        void shouldThrowSecurityException_whenTicklerWriteDenied() {
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", SecurityInfoManager.WRITE, "1001")).thenReturn(false);

            assertThatThrownBy(() -> service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.DOC, "11")))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_tickler)");
            verify(ticklerDocsDao, never()).persist(any());
        }

        @Test
        @DisplayName("should refuse adding a type the caller cannot read")
        void shouldThrowSecurityException_whenTypeReadDeniedAndIdsSubmitted() {
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_lab", SecurityInfoManager.READ, "1001")).thenReturn(false);
            when(ticklerDocsDao.findAllByTicklerIdForUpdate(TICKLER_ID)).thenReturn(List.of());

            assertThatThrownBy(() -> service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.LAB, "77")))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_lab)");
            verify(ticklerDocsDao, never()).persist(any());
        }

        @Test
        @DisplayName("should preserve stored items when an unreadable type has no identifiers in the form")
        void shouldLeaveTypeUntouched_whenTypeReadDeniedAndNothingSubmitted() {
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_lab", SecurityInfoManager.READ, "1001")).thenReturn(false);
            TicklerDocs mds = stored(77, "L");
            mds.setLabType("MDS");
            when(ticklerDocsDao.findAllByTicklerIdForUpdate(TICKLER_ID)).thenReturn(List.of(mds));

            service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.LAB));

            assertThat(mds.getDeleted()).isNull();
            verify(ticklerDocsDao, never()).merge(any());
            verify(ticklerDocsDao, never()).persist(any());
        }

        @Test
        @DisplayName("should leave a type the caller cannot read alone when its stored set is resubmitted unchanged")
        void shouldLeaveTypeUntouched_whenTypeReadDeniedAndStoredSetResubmitted() {
            // A form opened before this change may still submit its restricted delegates.
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_lab", SecurityInfoManager.READ, "1001")).thenReturn(false);
            TicklerDocs mds = stored(77, "L");
            mds.setLabType("MDS");
            TicklerDocs legacyHl7 = stored(78, "L");
            when(ticklerDocsDao.findAllByTicklerIdForUpdate(TICKLER_ID)).thenReturn(List.of(mds, legacyHl7));
            labOwnedBy(77, DEMOGRAPHIC_NO, "MDS");
            labOwnedBy(78, DEMOGRAPHIC_NO, "HL7");

            service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.LAB, "MDS:77", "HL7:78"));

            verify(ticklerDocsDao, never()).merge(any());
            verify(ticklerDocsDao, never()).persist(any());
        }

        @Test
        @DisplayName("should leave a type the caller cannot read alone when only its rows still shown are resubmitted")
        void shouldLeaveTypeUntouched_whenTypeReadDeniedAndMovedRowOmitted() {
            // The Edit form never carried the row whose lab was re-routed to another patient
            // (listAttachments omits it whatever the caller's rights), so its absence from the
            // submission is not a change and must not fail the save.
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_lab", SecurityInfoManager.READ, "1001")).thenReturn(false);
            TicklerDocs shown = stored(77, "L");
            shown.setLabType("MDS");
            TicklerDocs moved = stored(78, "L");
            moved.setLabType("HL7");
            when(ticklerDocsDao.findAllByTicklerIdForUpdate(TICKLER_ID)).thenReturn(List.of(shown, moved));
            labOwnedBy(77, DEMOGRAPHIC_NO, "MDS");
            labOwnedBy(78, DEMOGRAPHIC_NO + 1, "HL7");

            service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.LAB, "MDS:77"));

            verify(ticklerDocsDao, never()).merge(any());
            verify(ticklerDocsDao, never()).persist(any());
            assertThat(moved.getDeleted()).isNull();
        }

        @Test
        @DisplayName("should refuse changing a type the caller cannot read")
        void shouldThrowSecurityException_whenTypeReadDeniedAndStoredSetChanged() {
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_lab", SecurityInfoManager.READ, "1001")).thenReturn(false);
            TicklerDocs mds = stored(77, "L");
            mds.setLabType("MDS");
            when(ticklerDocsDao.findAllByTicklerIdForUpdate(TICKLER_ID)).thenReturn(List.of(mds));
            // A forged nonempty selection is refused even when the caller cannot read the type.
            labOwnedBy(77, DEMOGRAPHIC_NO, "MDS");

            assertThatThrownBy(() -> service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.LAB, "MDS:77", "MDS:79")))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_lab)");
            service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.LAB));
            assertThat(mds.getDeleted()).isNull();
            verify(ticklerDocsDao, never()).merge(any());
            verify(ticklerDocsDao, never()).persist(any());
        }

        @Test
        @DisplayName("should reject a non-numeric id")
        void shouldThrowIllegalArgument_whenIdIsNotNumeric() {
            assertThatThrownBy(() -> service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.DOC, "abc")))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(ticklerDocsDao, never()).persist(any());
        }
    }

    @Nested
    @DisplayName("requireAttachable")
    class RequireAttachable {

        @Test
        @DisplayName("should accept the patient's lab under its source without writing")
        void shouldAccept_whenLabIsThePatients() {
            labOwnedBy(77, DEMOGRAPHIC_NO, "HL7");

            service.requireAttachable(loggedInInfo, DEMOGRAPHIC_NO, submission(DocumentType.LAB, "HL7:77"));

            verify(ticklerDocsDao, never()).persist(any());
            verify(ticklerDocsDao, never()).findAllByTicklerIdForUpdate(any());
        }

        @Test
        @DisplayName("should refuse a document whose patient link was deleted before any tickler exists")
        void shouldThrowSecurityException_whenDocumentLinkDeleted() {
            documentLinkedWithStatus(11, DEMOGRAPHIC_NO, 'A', "D");

            assertThatThrownBy(() -> service.requireAttachable(loggedInInfo, DEMOGRAPHIC_NO,
                    submission(DocumentType.DOC, "11"))).isInstanceOf(SecurityException.class);
            verify(ticklerDocsDao, never()).persist(any());
        }

        @Test
        @DisplayName("should refuse a lab routed to another patient before any tickler exists")
        void shouldThrowSecurityException_whenLabIsAnotherPatients() {
            labOwnedBy(77, DEMOGRAPHIC_NO + 5, "HL7");

            assertThatThrownBy(() -> service.requireAttachable(loggedInInfo, DEMOGRAPHIC_NO, submission(DocumentType.LAB, "HL7:77")))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("should refuse a document outside the caller's program or queue before saving a tickler")
        void shouldRefuseDocumentWithoutItemAccess_beforeTicklerCreation() {
            documentOwnedBy(11, DEMOGRAPHIC_NO);
            org.mockito.Mockito.doThrow(new SecurityException("Document access denied"))
                    .when(ticklerDocumentAccess).requireRead(loggedInInfo, 11);

            assertThatThrownBy(() -> service.requireAttachable(loggedInInfo, DEMOGRAPHIC_NO,
                    submission(DocumentType.DOC, "11")))
                    .isInstanceOf(SecurityException.class);
            verify(ticklerDocsDao, never()).persist(any());
        }

        @Test
        @DisplayName("should require tickler write on the patient and read on the type")
        void shouldThrowSecurityException_whenRightsMissing() {
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", SecurityInfoManager.WRITE, "1001")).thenReturn(false);
            assertThatThrownBy(() -> service.requireAttachable(loggedInInfo, DEMOGRAPHIC_NO, submission(DocumentType.LAB, "HL7:77")))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_tickler)");

            when(securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", SecurityInfoManager.WRITE, "1001")).thenReturn(true);
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_lab", SecurityInfoManager.READ, "1001")).thenReturn(false);
            assertThatThrownBy(() -> service.requireAttachable(loggedInInfo, DEMOGRAPHIC_NO, submission(DocumentType.LAB, "HL7:77")))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_lab)");
            verify(patientLabRoutingDao, never()).findByLabNoAndLabType(anyInt(), any());
        }
    }

    @Nested
    @DisplayName("listAttachments")
    class ListAttachments {

        @Test
        @DisplayName("should resolve display names per type")
        void shouldResolveNames_forEachType() {
            TicklerDocs doc = stored(11, "D");
            TicklerDocs lab = stored(77, "L");
            lab.setLabType("HL7");
            TicklerDocs eForm = stored(5, "E");
            TicklerDocs form = stored(3, "F");
            when(ticklerDocsDao.findByTicklerId(TICKLER_ID)).thenReturn(List.of(doc, lab, eForm, form));
            documentOwnedBy(11, DEMOGRAPHIC_NO);
            labOwnedBy(77, DEMOGRAPHIC_NO, "HL7");

            Document document = new Document();
            document.setDocdesc("Referral letter");
            when(documentDao.getDocument("11")).thenReturn(document);

            AttachmentLabResultData labData = new AttachmentLabResultData("77", "CBC", new Date(), "HL7");
            when(documentAttachmentManager.getAllLabsSortedByVersions(loggedInInfo, "1001")).thenReturn(List.of(labData));

            EFormData eFormData = new EFormData();
            eFormData.setFormName("Consent");
            eFormData.setDemographicId(DEMOGRAPHIC_NO);
            when(eFormDataDao.find(5)).thenReturn(eFormData);

            when(formsManager.getEncounterFormsbyDemographicNumber(loggedInInfo, DEMOGRAPHIC_NO, true, false))
                    .thenReturn(List.of(new EctFormData.PatientForm("Rourke", 3, DEMOGRAPHIC_NO, new Date(), new Date())));

            List<TicklerAttachmentData> attachments = service.listAttachments(loggedInInfo, tickler);

            assertThat(attachments).extracting(TicklerAttachmentData::getDisplayName)
                    .containsExactly("Referral letter", "CBC", "Consent", "Rourke");
            assertThat(attachments).allMatch(TicklerAttachmentData::isViewable);
            assertThat(attachments.get(1).getLabType()).isEqualTo("HL7");
            assertThat(attachments.get(1).getSubmissionValue()).isEqualTo("HL7:77");
            assertThat(attachments.get(3).getFormName()).isEqualTo("Rourke");
            verify(formsManager, times(1)).getEncounterFormsbyDemographicNumber(loggedInInfo, DEMOGRAPHIC_NO, true, false);
            assertThat(attachments.get(0).getParameterName()).isEqualTo("docNo");
            assertThat(attachments.get(0).getSubmissionValue()).isEqualTo("11");
        }

        @Test
        @DisplayName("should not render a stored document whose patient link was deleted")
        void shouldOmitDocument_whenPatientLinkDeleted() {
            when(ticklerDocsDao.findByTicklerId(TICKLER_ID)).thenReturn(List.of(stored(11, "D")));
            documentLinkedWithStatus(11, DEMOGRAPHIC_NO, 'A', "D");

            List<TicklerAttachmentData> attachments = service.listAttachments(loggedInInfo, tickler);

            assertThat(attachments).isEmpty();
            verify(documentDao, never()).getDocument("11");
        }

        @Test
        @DisplayName("should render a stored document through a live link")
        void shouldRenderDocument_whenPatientLinkLive() {
            when(ticklerDocsDao.findByTicklerId(TICKLER_ID)).thenReturn(List.of(stored(11, "D")));
            documentLinkedWithStatus(11, DEMOGRAPHIC_NO, 'A', "A");
            Document document = new Document();
            document.setDocdesc("Referral letter");
            when(documentDao.getDocument("11")).thenReturn(document);

            List<TicklerAttachmentData> attachments = service.listAttachments(loggedInInfo, tickler);

            assertThat(attachments).extracting(TicklerAttachmentData::getDisplayName).containsExactly("Referral letter");
            assertThat(attachments).allMatch(TicklerAttachmentData::isViewable);
        }

        @Test
        @DisplayName("should name a lab by its own source, not by an HL7 lab sharing the id")
        void shouldResolveLabName_bySource() {
            TicklerDocs mds = stored(77, "L");
            mds.setLabType("MDS");
            when(ticklerDocsDao.findByTicklerId(TICKLER_ID)).thenReturn(List.of(mds));
            labOwnedBy(77, DEMOGRAPHIC_NO, "MDS");
            when(documentAttachmentManager.getAllLabsSortedByVersions(loggedInInfo, "1001")).thenReturn(List.of(
                    new AttachmentLabResultData("77", "HL7 CBC", new Date(), "HL7"),
                    new AttachmentLabResultData("77", "MDS Lipids", new Date(), "MDS")));

            List<TicklerAttachmentData> attachments = service.listAttachments(loggedInInfo, tickler);

            assertThat(attachments).extracting(TicklerAttachmentData::getDisplayName).containsExactly("MDS Lipids");
            assertThat(attachments.get(0).getSubmissionValue()).isEqualTo("MDS:77");
        }

        @Test
        @DisplayName("should list but not name items of a type the caller cannot read")
        void shouldRedactName_whenTypeReadDenied() {
            when(ticklerDocsDao.findByTicklerId(TICKLER_ID)).thenReturn(List.of(stored(11, "D")));
            documentOwnedBy(11, DEMOGRAPHIC_NO);
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ, "1001")).thenReturn(false);

            List<TicklerAttachmentData> attachments = service.listAttachments(loggedInInfo, tickler);

            assertThat(attachments).hasSize(1);
            assertThat(attachments.get(0).isViewable()).isFalse();
            assertThat(attachments.get(0).getDisplayName()).isNull();
            assertThat(attachments.get(0).getDocumentId()).isEqualTo("11");
            verify(documentDao, never()).getDocument(anyString());
        }

        @Test
        @DisplayName("should list but not name a document whose document-level access is denied")
        void shouldRedactDocument_whenProgramOrQueueReadDenied() {
            when(ticklerDocsDao.findByTicklerId(TICKLER_ID)).thenReturn(List.of(stored(11, "D")));
            documentOwnedBy(11, DEMOGRAPHIC_NO);
            when(ticklerDocumentAccess.canRead(loggedInInfo, 11)).thenReturn(false);

            List<TicklerAttachmentData> attachments = service.listAttachments(loggedInInfo, tickler);

            assertThat(attachments).hasSize(1);
            assertThat(attachments.get(0).isViewable()).isFalse();
            assertThat(attachments.get(0).getDisplayName()).isNull();
            verify(documentDao, never()).getDocument(anyString());
        }

        @Test
        @DisplayName("should fall back to a generic label when the item no longer resolves")
        void shouldUseGenericLabel_whenItemMissing() {
            when(ticklerDocsDao.findByTicklerId(TICKLER_ID)).thenReturn(List.of(stored(11, "D")));
            documentOwnedBy(11, DEMOGRAPHIC_NO);
            when(documentDao.getDocument("11")).thenReturn(null);

            List<TicklerAttachmentData> attachments = service.listAttachments(loggedInInfo, tickler);

            assertThat(attachments.get(0).getDisplayName()).isEqualTo("doc #11");
        }

        @Test
        @DisplayName("should keep a form row restricted, without the authorising forms lookup, when form read is denied")
        void shouldKeepFormRestricted_whenFormReadDenied() {
            when(ticklerDocsDao.findByTicklerId(TICKLER_ID)).thenReturn(List.of(stored(3, "F")));
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, "1001")).thenReturn(false);

            List<TicklerAttachmentData> attachments = service.listAttachments(loggedInInfo, tickler);

            assertThat(attachments).hasSize(1);
            assertThat(attachments.get(0).isViewable()).isFalse();
            assertThat(attachments.get(0).getDisplayName()).isNull();
            assertThat(attachments.get(0).getFormName()).isNull();
            verify(formsManager, never()).getEncounterFormsbyDemographicNumber(any(), any(), anyBoolean(), anyBoolean());
        }

        @Test
        @DisplayName("should stamp the audit pair on attach, detach and revive")
        void shouldStampAuditPair_onEveryWrite() {
            documentOwnedBy(11, DEMOGRAPHIC_NO);
            documentOwnedBy(12, DEMOGRAPHIC_NO);
            TicklerDocs removed = stored(12, "D");
            removed.setLastUpdateUser("000001");
            when(ticklerDocsDao.findAllByTicklerIdForUpdate(TICKLER_ID)).thenReturn(List.of(removed));

            service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.DOC, "11"));

            ArgumentCaptor<TicklerDocs> captor = ArgumentCaptor.forClass(TicklerDocs.class);
            verify(ticklerDocsDao).persist(captor.capture());
            assertThat(captor.getValue().getLastUpdateUser()).isEqualTo(PROVIDER_NO);
            assertThat(captor.getValue().getLastUpdateDate()).isNotNull();
            assertThat(removed.getDeleted()).isEqualTo(TicklerDocs.DELETED_FLAG);
            assertThat(removed.getLastUpdateUser()).isEqualTo(PROVIDER_NO);
        }

        @Test
        @DisplayName("should leave out an attached document that has since been re-filed to another patient")
        void shouldOmitAttachment_whenItemNoLongerThePatients() {
            when(ticklerDocsDao.findByTicklerId(TICKLER_ID)).thenReturn(List.of(stored(11, "D"), stored(12, "D")));
            documentOwnedBy(11, DEMOGRAPHIC_NO + 1);
            documentOwnedBy(12, DEMOGRAPHIC_NO);
            Document document = new Document();
            document.setDocdesc("Still here");
            when(documentDao.getDocument("12")).thenReturn(document);

            List<TicklerAttachmentData> attachments = service.listAttachments(loggedInInfo, tickler);

            assertThat(attachments).extracting(TicklerAttachmentData::getDocumentId).containsExactly("12");
            verify(documentDao, never()).getDocument("11");
        }

        @Test
        @DisplayName("should require tickler read on the patient")
        void shouldThrowSecurityException_whenTicklerReadDenied() {
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", SecurityInfoManager.READ, "1001")).thenReturn(false);

            assertThatThrownBy(() -> service.listAttachments(loggedInInfo, tickler))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_tickler)");
        }

        @Test
        @DisplayName("should list a page of ticklers with one row query and one lab lookup per patient")
        void shouldBatchAttachments_forPageOfTicklers() {
            Tickler other = new Tickler();
            other.setId(TICKLER_ID + 1);
            other.setDemographicNo(DEMOGRAPHIC_NO);
            Tickler empty = new Tickler();
            empty.setId(TICKLER_ID + 2);
            empty.setDemographicNo(DEMOGRAPHIC_NO);
            TicklerDocs first = stored(77, "L");
            first.setLabType("HL7");
            TicklerDocs second = new TicklerDocs(TICKLER_ID + 1, 78, "L", PROVIDER_NO);
            second.setLabType("MDS");
            labOwnedBy(77, DEMOGRAPHIC_NO, "HL7");
            labOwnedBy(78, DEMOGRAPHIC_NO, "MDS");
            when(ticklerDocsDao.findByTicklerIds(List.of(TICKLER_ID, TICKLER_ID + 1, TICKLER_ID + 2)))
                    .thenReturn(List.of(first, second));
            when(documentAttachmentManager.getAllLabsSortedByVersions(loggedInInfo, "1001")).thenReturn(List.of(
                    new AttachmentLabResultData("77", "CBC", new Date(), "HL7"),
                    new AttachmentLabResultData("78", "Lipids", new Date(), "MDS")));

            Map<Integer, List<TicklerAttachmentData>> byTickler =
                    service.listAttachments(loggedInInfo, List.of(tickler, other, empty));

            assertThat(byTickler.keySet()).containsExactly(TICKLER_ID, TICKLER_ID + 1, TICKLER_ID + 2);
            assertThat(byTickler.get(TICKLER_ID)).extracting(TicklerAttachmentData::getDisplayName).containsExactly("CBC");
            assertThat(byTickler.get(TICKLER_ID + 1)).extracting(TicklerAttachmentData::getSubmissionValue).containsExactly("MDS:78");
            assertThat(byTickler.get(TICKLER_ID + 2)).isEmpty();
            verify(documentAttachmentManager, times(1)).getAllLabsSortedByVersions(loggedInInfo, "1001");
            verify(ticklerDocsDao, never()).findByTicklerId(any());
            verify(securityInfoManager, times(1)).hasPrivilege(loggedInInfo, "_tickler", SecurityInfoManager.READ, "1001");
        }

        @Test
        @DisplayName("should refuse the whole page when tickler read is denied for one of its patients")
        void shouldThrowSecurityException_whenPageHasPatientWithoutTicklerRead() {
            Tickler other = new Tickler();
            other.setId(TICKLER_ID + 1);
            other.setDemographicNo(DEMOGRAPHIC_NO + 1);
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", SecurityInfoManager.READ, "1002")).thenReturn(false);

            assertThatThrownBy(() -> service.listAttachments(loggedInInfo, List.of(tickler, other)))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_tickler)");
            verify(ticklerDocsDao, never()).findByTicklerIds(any());
        }

        @Test
        @DisplayName("should return an empty map for an empty page")
        void shouldReturnEmptyMap_whenPageIsEmpty() {
            assertThat(service.listAttachments(loggedInInfo, List.of())).isEmpty();
            verify(ticklerDocsDao, never()).findByTicklerIds(any());
        }
    }

    @Nested
    @DisplayName("formNamesByFormId")
    class FormNames {

        @Test
        @DisplayName("should drop form ids claimed by more than one form type")
        void shouldDropAmbiguousIds_whenTwoFormsShareAnId() {
            when(formsManager.getEncounterFormsbyDemographicNumber(loggedInInfo, DEMOGRAPHIC_NO, true, false))
                    .thenReturn(List.of(
                            new EctFormData.PatientForm("Rourke", 3, DEMOGRAPHIC_NO, new Date(), new Date()),
                            new EctFormData.PatientForm("BCAR", 3, DEMOGRAPHIC_NO, new Date(), new Date()),
                            new EctFormData.PatientForm("Annual", 8, DEMOGRAPHIC_NO, new Date(), new Date())));

            Map<String, String> names = service.formNamesByFormId(loggedInInfo, DEMOGRAPHIC_NO);

            assertThat(names).containsExactly(Map.entry("8", "Annual"));
        }

        @Test
        @DisplayName("should return nothing when form read is denied")
        void shouldReturnEmpty_whenFormReadDenied() {
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, "1001")).thenReturn(false);

            assertThat(service.formNamesByFormId(loggedInInfo, DEMOGRAPHIC_NO)).isEmpty();
            verify(formsManager, never()).getEncounterFormsbyDemographicNumber(any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean());
        }
    }
}
