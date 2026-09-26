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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private DocumentDao documentDao;
    @Mock private PatientLabRoutingDao patientLabRoutingDao;
    @Mock private EFormDataDao eFormDataDao;
    @Mock private HRMDocumentToDemographicDao hrmDocumentToDemographicDao;
    @Mock private FormsManager formsManager;
    @Mock private DocumentAttachmentManager documentAttachmentManager;
    @Mock private LoggedInInfo loggedInInfo;

    private TicklerAttachmentService service;
    private Tickler tickler;

    @BeforeEach
    void setUp() {
        service = new TicklerAttachmentService(ticklerDocsDao, securityInfoManager, documentDao,
                patientLabRoutingDao, eFormDataDao, hrmDocumentToDemographicDao, formsManager,
                documentAttachmentManager);
        tickler = new Tickler();
        tickler.setId(TICKLER_ID);
        tickler.setDemographicNo(DEMOGRAPHIC_NO);
        lenient().when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
        lenient().when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), anyString()))
                .thenReturn(true);
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

    private void labOwnedBy(int labNo, int demographicNo, String labType) {
        PatientLabRouting routing = new PatientLabRouting();
        routing.setLabNo(labNo);
        routing.setLabType(labType);
        routing.setDemographicNo(demographicNo);
        when(patientLabRoutingDao.findDemographicByLabId(labNo)).thenReturn(routing);
    }

    @Nested
    @DisplayName("syncAttachments")
    class SyncAttachments {

        @Test
        @DisplayName("should attach a patient's document with the session provider and audit it")
        void shouldAttachDocument_whenItBelongsToPatient() {
            documentOwnedBy(11, DEMOGRAPHIC_NO);
            when(ticklerDocsDao.findByTicklerIdDocType(TICKLER_ID, "D")).thenReturn(List.of());

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
        @DisplayName("should reject an unknown document id")
        void shouldRejectDocument_whenItDoesNotExist() {
            when(documentDao.findCtlDocsAndDocsByDocNo(11)).thenReturn(List.of());

            assertThatThrownBy(() -> service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.DOC, "11")))
                    .isInstanceOf(SecurityException.class);
            verify(ticklerDocsDao, never()).persist(any());
        }

        @Test
        @DisplayName("should record the lab source when attaching a lab")
        void shouldKeepLabType_whenAttachingLab() {
            labOwnedBy(77, DEMOGRAPHIC_NO, "MDS");
            when(ticklerDocsDao.findByTicklerIdDocType(TICKLER_ID, "L")).thenReturn(List.of());

            service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.LAB, "77"));

            ArgumentCaptor<TicklerDocs> captor = ArgumentCaptor.forClass(TicklerDocs.class);
            verify(ticklerDocsDao).persist(captor.capture());
            assertThat(captor.getValue().getLabType()).isEqualTo("MDS");
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
            when(ticklerDocsDao.findByTicklerIdDocType(TICKLER_ID, "H")).thenReturn(List.of());

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
            documentOwnedBy(11, DEMOGRAPHIC_NO);
            TicklerDocs kept = stored(11, "D");
            TicklerDocs removed = stored(12, "D");
            when(ticklerDocsDao.findByTicklerIdDocType(TICKLER_ID, "D")).thenReturn(List.of(kept, removed));

            service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.DOC, "11"));

            assertThat(removed.getDeleted()).isEqualTo(TicklerDocs.DELETED);
            assertThat(kept.getDeleted()).isNull();
            verify(ticklerDocsDao).merge(removed);
            verify(ticklerDocsDao, never()).persist(any());
            logActionMock.verify(() -> LogAction.addLogSynchronous(eq(loggedInInfo),
                    eq("TicklerAttachmentService.delete"), eq("ticklerId=42,type=D,documentNo=12")));
        }

        @Test
        @DisplayName("should leave types that were not submitted untouched")
        void shouldLeaveOtherTypesAlone_whenOnlyOneTypeSubmitted() {
            documentOwnedBy(11, DEMOGRAPHIC_NO);
            when(ticklerDocsDao.findByTicklerIdDocType(TICKLER_ID, "D")).thenReturn(List.of());

            service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.DOC, "11"));

            verify(ticklerDocsDao, never()).findByTicklerIdDocType(TICKLER_ID, "L");
            verify(ticklerDocsDao, never()).findByTicklerIdDocType(TICKLER_ID, "E");
            verify(ticklerDocsDao, never()).findByTicklerIdDocType(TICKLER_ID, "H");
            verify(ticklerDocsDao, never()).findByTicklerIdDocType(TICKLER_ID, "F");
        }

        @Test
        @DisplayName("should do nothing when no types are submitted")
        void shouldDoNothing_whenSubmissionIsEmpty() {
            service.syncAttachments(loggedInInfo, tickler, new EnumMap<>(DocumentType.class));
            service.syncAttachments(loggedInInfo, tickler, null);

            verify(ticklerDocsDao, never()).findByTicklerIdDocType(any(), any());
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

            assertThatThrownBy(() -> service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.LAB, "77")))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_lab)");
            verify(ticklerDocsDao, never()).persist(any());
        }

        @Test
        @DisplayName("should not detach items of a type the caller cannot read")
        void shouldLeaveTypeUntouched_whenTypeReadDeniedAndNothingSubmitted() {
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_lab", SecurityInfoManager.READ, "1001")).thenReturn(false);

            service.syncAttachments(loggedInInfo, tickler, submission(DocumentType.LAB));

            verify(ticklerDocsDao, never()).findByTicklerIdDocType(any(), any());
            verify(ticklerDocsDao, never()).merge(any());
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

            Document document = new Document();
            document.setDocdesc("Referral letter");
            when(documentDao.getDocument("11")).thenReturn(document);

            AttachmentLabResultData labData = new AttachmentLabResultData("77", "CBC", new Date());
            when(documentAttachmentManager.getAllLabsSortedByVersions(loggedInInfo, "1001")).thenReturn(List.of(labData));

            EFormData eFormData = new EFormData();
            eFormData.setFormName("Consent");
            when(eFormDataDao.find(5)).thenReturn(eFormData);

            when(formsManager.getEncounterFormsbyDemographicNumber(loggedInInfo, DEMOGRAPHIC_NO, true, false))
                    .thenReturn(List.of(new EctFormData.PatientForm("Rourke", 3, DEMOGRAPHIC_NO, new Date(), new Date())));

            List<TicklerAttachmentData> attachments = service.listAttachments(loggedInInfo, tickler);

            assertThat(attachments).extracting(TicklerAttachmentData::getDisplayName)
                    .containsExactly("Referral letter", "CBC", "Consent", "Rourke");
            assertThat(attachments).allMatch(TicklerAttachmentData::isViewable);
            assertThat(attachments.get(1).getLabType()).isEqualTo("HL7");
            assertThat(attachments.get(0).getParameterName()).isEqualTo("docNo");
        }

        @Test
        @DisplayName("should list but not name items of a type the caller cannot read")
        void shouldRedactName_whenTypeReadDenied() {
            when(ticklerDocsDao.findByTicklerId(TICKLER_ID)).thenReturn(List.of(stored(11, "D")));
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ, "1001")).thenReturn(false);

            List<TicklerAttachmentData> attachments = service.listAttachments(loggedInInfo, tickler);

            assertThat(attachments).hasSize(1);
            assertThat(attachments.get(0).isViewable()).isFalse();
            assertThat(attachments.get(0).getDisplayName()).isNull();
            assertThat(attachments.get(0).getDocumentId()).isEqualTo("11");
            verify(documentDao, never()).getDocument(anyString());
        }

        @Test
        @DisplayName("should fall back to a generic label when the item no longer resolves")
        void shouldUseGenericLabel_whenItemMissing() {
            when(ticklerDocsDao.findByTicklerId(TICKLER_ID)).thenReturn(List.of(stored(11, "D")));
            when(documentDao.getDocument("11")).thenReturn(null);

            List<TicklerAttachmentData> attachments = service.listAttachments(loggedInInfo, tickler);

            assertThat(attachments.get(0).getDisplayName()).isEqualTo("doc #11");
        }

        @Test
        @DisplayName("should require tickler read on the patient")
        void shouldThrowSecurityException_whenTicklerReadDenied() {
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", SecurityInfoManager.READ, "1001")).thenReturn(false);

            assertThatThrownBy(() -> service.listAttachments(loggedInInfo, tickler))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_tickler)");
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
