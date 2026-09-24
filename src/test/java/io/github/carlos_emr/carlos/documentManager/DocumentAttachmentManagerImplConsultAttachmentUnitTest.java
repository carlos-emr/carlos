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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.dao.EReferAttachmentDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.EReferAttachmentDataDaoImpl;
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.encounter.oceanEReferal.pageUtil.OceanEReferralAttachmentUtil;
import io.github.carlos_emr.carlos.managers.ConsultationManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentAttachmentManagerImpl Unit Tests")
@Tag("unit")
@Tag("documentManager")
class DocumentAttachmentManagerImplConsultAttachmentUnitTest extends CarlosUnitTestBase {

    @Mock
    private SecurityInfoManager securityInfoManager;

    @Mock
    private ConsultDocsDao consultDocsDao;

    @Mock
    private EFormDocsDao eFormDocsDao;

    @Mock
    private LoggedInInfo loggedInInfo;

    @Mock
    private AttachmentOwnershipService attachmentOwnershipService;

    @Mock
    private ConsultationManager consultationManager;

    private DocumentAttachmentManagerImpl manager;

    @BeforeEach
    void setUp() {
        manager = new DocumentAttachmentManagerImpl();
        injectDependency(manager, "securityInfoManager", securityInfoManager);
        injectDependency(manager, "consultDocsDao", consultDocsDao);
        injectDependency(manager, "attachmentOwnershipService", attachmentOwnershipService);
        injectDependency(manager, "consultationManager", consultationManager);
        // Lenient: only the attach/verify/render paths consult it; the denial tests below override it.
        lenient().when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 123)).thenReturn(true);
        registerMock(ConsultDocsDao.class, consultDocsDao);
        registerMock(EFormDocsDao.class, eFormDocsDao);
    }

    @Test
    @DisplayName("should allow reading consult attachments with consult read privilege")
    void shouldAllowGetConsultAttachments_withConsultReadPrivilege() {
        int demographicNo = 123;
        int requestId = 456;
        ConsultDocs attachedDoc = new ConsultDocs(requestId, 789, DocumentType.DOC.getType(), "999");

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.READ, demographicNo))
                .thenReturn(true);
        when(consultDocsDao.findByRequestIdDocType(requestId, DocumentType.DOC.getType()))
                .thenReturn(List.of(attachedDoc));

        List<String> attachmentIds = manager.getConsultAttachments(loggedInInfo, requestId, DocumentType.DOC, demographicNo);

        assertThat(attachmentIds).containsExactly("789");
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.READ, demographicNo);
    }

    @Test
    @DisplayName("should attach documents to consult with consult write privilege")
    void shouldAttachToConsult_withConsultWritePrivilege() {
        int demographicNo = 123;
        int requestId = 456;

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, demographicNo))
                .thenReturn(true);
        when(consultDocsDao.findByRequestIdDocType(requestId, DocumentType.DOC.getType()))
                .thenReturn(List.of());
        when(attachmentOwnershipService.findAttachableIds(DocumentType.DOC, demographicNo, Set.of(789)))
                .thenReturn(Set.of(789));

        manager.attachToConsult(
                loggedInInfo,
                DocumentType.DOC,
                new String[] {"789"},
                "999",
                requestId,
                demographicNo);

        ArgumentCaptor<ConsultDocs> consultDocCaptor = ArgumentCaptor.forClass(ConsultDocs.class);
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, demographicNo);
        verify(consultDocsDao).findByRequestIdDocType(requestId, DocumentType.DOC.getType());
        verify(consultDocsDao).persist(consultDocCaptor.capture());
        ConsultDocs persisted = consultDocCaptor.getValue();
        assertThat(persisted.getRequestId()).isEqualTo(requestId);
        assertThat(persisted.getDocumentNo()).isEqualTo(789);
        assertThat(persisted.getDocType()).isEqualTo(DocumentType.DOC.getType());
        assertThat(persisted.getProviderNo()).isEqualTo("999");
    }

    @Test
    @DisplayName("should require consult read privilege before reading consult attachments")
    void shouldRequireConsultReadPrivilege_beforeGetConsultAttachments() {
        int demographicNo = 123;
        int requestId = 456;

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.READ, demographicNo))
                .thenReturn(false);

        assertThatThrownBy(() -> manager.getConsultAttachments(loggedInInfo, requestId, DocumentType.DOC, demographicNo))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_con)");

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.READ, demographicNo);
        verifyNoInteractions(consultDocsDao);
    }

    @Test
    @DisplayName("should require consult write privilege before attaching to consult")
    void shouldRequireConsultWritePrivilege_beforeAttachToConsult() {
        int demographicNo = 123;
        int requestId = 456;

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, demographicNo))
                .thenReturn(false);

        assertThatThrownBy(() -> manager.attachToConsult(
                loggedInInfo,
                DocumentType.DOC,
                new String[] {"789"},
                "999",
                requestId,
                demographicNo))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_con)");

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, demographicNo);
        verifyNoInteractions(consultDocsDao);
    }

    @Test
    @DisplayName("should reject a newly attached document that belongs to another patient before any write")
    void shouldRejectAttachToConsult_whenNewDocumentBelongsToAnotherPatient() {
        int demographicNo = 123;
        int requestId = 456;

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, demographicNo))
                .thenReturn(true);
        when(consultDocsDao.findByRequestIdDocType(requestId, DocumentType.DOC.getType()))
                .thenReturn(List.of());
        when(attachmentOwnershipService.findAttachableIds(DocumentType.DOC, demographicNo, Set.of(999)))
                .thenReturn(Set.of());

        assertThatThrownBy(() -> manager.attachToConsult(
                loggedInInfo, DocumentType.DOC, new String[] {"999"}, "999", requestId, demographicNo, Boolean.TRUE))
                .isInstanceOf(SecurityException.class);

        verify(consultDocsDao, never()).persist(any());
        verify(consultDocsDao, never()).merge(any());
    }

    @Test
    @DisplayName("should reject a non-numeric attachment id before any write")
    void shouldRejectAttachToConsult_whenAttachmentIdNotNumeric() {
        int demographicNo = 123;
        int requestId = 456;

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, demographicNo))
                .thenReturn(true);
        when(consultDocsDao.findByRequestIdDocType(requestId, DocumentType.LAB.getType()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> manager.attachToConsult(
                loggedInInfo, DocumentType.LAB, new String[] {"12x"}, "999", requestId, demographicNo))
                .isInstanceOf(SecurityException.class);

        verify(consultDocsDao, never()).persist(any());
        verifyNoInteractions(attachmentOwnershipService);
    }

    @Test
    @DisplayName("should keep an already attached id that still belongs to the patient")
    void shouldKeepExistingAttachment_whenStillOwnedByPatient() {
        int demographicNo = 123;
        int requestId = 456;
        ConsultDocs existing = new ConsultDocs(requestId, 789, DocumentType.DOC.getType(), "999");

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, demographicNo))
                .thenReturn(true);
        when(consultDocsDao.findByRequestIdDocType(requestId, DocumentType.DOC.getType()))
                .thenReturn(List.of(existing));
        when(attachmentOwnershipService.findAttachableIds(DocumentType.DOC, demographicNo, Set.of(789)))
                .thenReturn(Set.of(789));

        manager.attachToConsult(loggedInInfo, DocumentType.DOC, new String[] {"789"}, "999", requestId, demographicNo);

        verify(consultDocsDao, never()).persist(any());
        verify(consultDocsDao, never()).merge(any());
    }

    /**
     * A consult_docs row written before attach-time ownership checks existed (or a document deleted
     * or re-filed since) must not survive a re-save, but it must not block the save either: the
     * user cannot remove it from the form. It is detached and the save continues.
     */
    /**
     * Issue #3867 review: detaching a legacy foreign row on an Ocean edit must only touch this
     * patient's Ocean queue, never another patient's queue row for the same document id.
     */
    @Test
    @DisplayName("should scope the Ocean queue removal to the consultation patient when detaching on an Ocean edit")
    void shouldScopeOceanDetachToPatient_whenDetachingOnOceanEdit() {
        int demographicNo = 123;
        int requestId = 456;
        ConsultDocs legacyForeign = new ConsultDocs(requestId, 555, DocumentType.DOC.getType(), "999");

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, demographicNo))
                .thenReturn(true);
        when(consultDocsDao.findByRequestIdDocType(requestId, DocumentType.DOC.getType()))
                .thenReturn(List.of(legacyForeign));
        when(attachmentOwnershipService.findAttachableIds(DocumentType.DOC, demographicNo, Set.of(555)))
                .thenReturn(Set.of());
        when(consultDocsDao.findByRequestIdDocNoDocType(requestId, 555, DocumentType.DOC.getType()))
                .thenReturn(List.of(legacyForeign));

        // The utility resolves its DAOs in a static initializer that instrumentation runs.
        registerMock(EReferAttachmentDataDaoImpl.class, org.mockito.Mockito.mock(EReferAttachmentDataDaoImpl.class));
        registerMock(EReferAttachmentDaoImpl.class, org.mockito.Mockito.mock(EReferAttachmentDaoImpl.class));
        try (MockedStatic<OceanEReferralAttachmentUtil> ocean = mockStatic(OceanEReferralAttachmentUtil.class)) {
            manager.attachToConsult(loggedInInfo, DocumentType.DOC, new String[] {"555"}, "999", requestId, demographicNo, Boolean.TRUE);

            ocean.verify(() -> OceanEReferralAttachmentUtil.detachOceanEReferralConsult("555", demographicNo, DocumentType.DOC.getType()));
            ocean.verifyNoMoreInteractions();
        }
        assertThat(legacyForeign.getDeleted()).isEqualTo("Y");
    }

    @Test
    @DisplayName("should detach an already attached id that no longer belongs to the patient without failing the save")
    void shouldDetachExistingAttachment_whenNoLongerOwnedByPatient() {
        int demographicNo = 123;
        int requestId = 456;
        ConsultDocs legacyForeign = new ConsultDocs(requestId, 555, DocumentType.DOC.getType(), "999");
        ConsultDocs owned = new ConsultDocs(requestId, 789, DocumentType.DOC.getType(), "999");

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, demographicNo))
                .thenReturn(true);
        when(consultDocsDao.findByRequestIdDocType(requestId, DocumentType.DOC.getType()))
                .thenReturn(List.of(legacyForeign, owned));
        when(attachmentOwnershipService.findAttachableIds(DocumentType.DOC, demographicNo, Set.of(555, 789)))
                .thenReturn(Set.of(789));
        when(consultDocsDao.findByRequestIdDocNoDocType(requestId, 555, DocumentType.DOC.getType()))
                .thenReturn(List.of(legacyForeign));

        manager.attachToConsult(loggedInInfo, DocumentType.DOC, new String[] {"555", "789"}, "999", requestId, demographicNo);

        verify(consultDocsDao).merge(legacyForeign);
        assertThat(legacyForeign.getDeleted()).isEqualTo("Y");
        verify(consultDocsDao, never()).findByRequestIdDocNoDocType(requestId, 789, DocumentType.DOC.getType());
        verify(consultDocsDao, never()).persist(any());
    }

    @Test
    @DisplayName("should reject a newly attached foreign id during verification without writing anything")
    void shouldRejectVerifyConsultAttachments_whenNewIdBelongsToAnotherPatient() {
        int demographicNo = 123;
        int requestId = 456;

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, demographicNo))
                .thenReturn(true);
        when(consultDocsDao.findByRequestIdDocType(requestId, DocumentType.DOC.getType()))
                .thenReturn(List.of());
        when(consultDocsDao.findByRequestIdDocType(requestId, DocumentType.LAB.getType()))
                .thenReturn(List.of());
        when(attachmentOwnershipService.findAttachableIds(DocumentType.DOC, demographicNo, Set.of(789)))
                .thenReturn(Set.of(789));
        when(attachmentOwnershipService.findAttachableIds(DocumentType.LAB, demographicNo, Set.of(999)))
                .thenReturn(Set.of());

        Map<DocumentType, String[]> submitted = new EnumMap<>(DocumentType.class);
        submitted.put(DocumentType.DOC, new String[] {"789"});
        submitted.put(DocumentType.LAB, new String[] {"999"});
        submitted.put(DocumentType.FORM, new String[] {"42"});

        assertThatThrownBy(() -> manager.verifyConsultAttachments(loggedInInfo, requestId, demographicNo, submitted))
                .isInstanceOf(SecurityException.class);

        verify(consultDocsDao, never()).persist(any());
        verify(consultDocsDao, never()).merge(any());
    }

    @Test
    @DisplayName("should treat every id as new when verifying a consultation that is not saved yet")
    void shouldVerifyAllIdsAsNew_whenRequestIdIsNull() {
        int demographicNo = 123;

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, demographicNo))
                .thenReturn(true);
        when(attachmentOwnershipService.findAttachableIds(DocumentType.EFORM, demographicNo, Set.of(7)))
                .thenReturn(Set.of());

        Map<DocumentType, String[]> submitted = new EnumMap<>(DocumentType.class);
        submitted.put(DocumentType.EFORM, new String[] {"7"});

        assertThatThrownBy(() -> manager.verifyConsultAttachments(loggedInInfo, null, demographicNo, submitted))
                .isInstanceOf(SecurityException.class);

        verifyNoInteractions(consultDocsDao);
    }

    @Test
    @DisplayName("should deny verification before any lookup when consult write is missing for the patient")
    void shouldDenyVerifyConsultAttachments_withoutConsultWritePrivilege() {
        int demographicNo = 123;

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, demographicNo))
                .thenReturn(false);

        assertThatThrownBy(() -> manager.verifyConsultAttachments(loggedInInfo, 456, demographicNo,
                Map.of(DocumentType.DOC, new String[] {"789"})))
                .isInstanceOf(SecurityException.class);

        verifyNoInteractions(consultDocsDao, attachmentOwnershipService);
    }

    /**
     * Consultation print and fax resolve attached documents and eForms by consultation id alone, so
     * a legacy consult_docs row pointing at another patient's record must be left out of the PDF.
     */
    @Test
    @DisplayName("should omit attachments not owned by the consultation patient when rendering")
    void shouldOmitForeignAttachments_whenRenderingConsultation() {
        when(attachmentOwnershipService.findOwnedIds(DocumentType.DOC, 123, Set.of(1, 2)))
                .thenReturn(Set.of(1));

        List<String> retained = manager.retainOwnedAttachments(DocumentType.DOC, 123,
                List.of("1", "2", "not-a-number"), id -> id);

        assertThat(retained).containsExactly("1");
    }

    @Test
    @DisplayName("should omit every attachment when the consultation patient is unknown")
    void shouldOmitAllAttachments_whenDemographicUnknown() {
        when(attachmentOwnershipService.findOwnedIds(DocumentType.EFORM, null, Set.of(5)))
                .thenReturn(Set.of());

        assertThat(manager.retainOwnedAttachments(DocumentType.EFORM, null, List.of("5"), id -> id)).isEmpty();
    }
    /**
     * A patient-scoped {@code _con} write grant is not the circle-of-care check. Attaching a record
     * to a consultation queues it for print, fax and Ocean, so it also requires access to the
     * patient's record, before any ownership lookup or write.
     */
    @Test
    @DisplayName("should deny attaching before any lookup when patient record access is denied")
    void shouldDenyAttachToConsult_whenPatientRecordAccessDenied() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, 123)).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 123)).thenReturn(false);

        assertThatThrownBy(() -> manager.attachToConsult(
                loggedInInfo, DocumentType.DOC, new String[] {"789"}, "999", 456, 123, Boolean.TRUE))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_con)");

        verifyNoInteractions(consultDocsDao, attachmentOwnershipService);
    }

    @Test
    @DisplayName("should still allow a detach-only call when patient record access is denied")
    void shouldAllowDetachOnlyAttachToConsult_whenPatientRecordAccessDenied() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, 123)).thenReturn(true);
        when(consultDocsDao.findByRequestIdDocType(456, DocumentType.DOC.getType())).thenReturn(List.of());

        manager.attachToConsult(loggedInInfo, DocumentType.DOC, new String[0], "999", 456, 123);

        verify(securityInfoManager, never()).isAllowedAccessToPatientRecord(any(), any());
        verifyNoInteractions(attachmentOwnershipService);
    }

    @Test
    @DisplayName("should deny verification before any lookup when patient record access is denied")
    void shouldDenyVerifyConsultAttachments_whenPatientRecordAccessDenied() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, 123)).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 123)).thenReturn(false);

        assertThatThrownBy(() -> manager.verifyConsultAttachments(loggedInInfo, 456, 123,
                Map.of(DocumentType.LAB, new String[] {"20"})))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_con)");

        verifyNoInteractions(consultDocsDao, attachmentOwnershipService);
    }

    @Test
    @DisplayName("should refuse to render the consultation packet when patient record access is denied")
    void shouldDenyRenderConsultationForm_whenPatientRecordAccessDenied() {
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        org.springframework.mock.web.MockHttpSession session = new org.springframework.mock.web.MockHttpSession();
        LoggedInInfo.setLoggedInInfoIntoSession(session, loggedInInfo);
        request.setSession(session);
        request.setAttribute("reqId", "456");
        request.setAttribute("demographicId", "123");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.READ, 123)).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 123)).thenReturn(false);

        assertThatThrownBy(() -> manager.renderConsultationFormWithAttachments(request,
                new org.springframework.mock.web.MockHttpServletResponse()))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_con)");

        verifyNoInteractions(consultationManager, attachmentOwnershipService);
    }
    /**
     * Lab identifier consistency: stored ids are listed as {@code Integer.toString}, so a
     * resubmitted {@code "0789"} used to look new, detaching 789 and attaching it again.
     */
    @Test
    @DisplayName("should treat a non-canonical resubmitted id as the already attached id")
    void shouldKeepExistingAttachment_whenIdResubmittedWithLeadingZero() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, 123)).thenReturn(true);
        when(consultDocsDao.findByRequestIdDocType(456, DocumentType.LAB.getType()))
                .thenReturn(List.of(new ConsultDocs(456, 789, DocumentType.LAB.getType(), "999")));
        when(attachmentOwnershipService.findAttachableIds(DocumentType.LAB, 123, Set.of(789))).thenReturn(Set.of(789));

        manager.attachToConsult(loggedInInfo, DocumentType.LAB, new String[] {"0789", "789"}, "999", 456, 123);

        verify(consultDocsDao, never()).persist(any());
        verify(consultDocsDao, never()).merge(any());
    }

    /**
     * Lab identifier consistency: the Ocean feed sends only HL7 labs (findOwnedIds), and a new
     * Ocean referral refuses anything else. An Ocean edit attached a legacy CML/MDS/BCP lab to the
     * consultation (findAttachableIds) and also queued it for Ocean, where it was dropped at send
     * time. It is still attached to the consultation but no longer queued.
     */
    @Test
    @DisplayName("should queue only HL7 labs for Ocean while attaching every attachable lab on an Ocean edit")
    void shouldQueueOnlyHl7Labs_whenAttachingOnOceanEdit() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, 123)).thenReturn(true);
        when(consultDocsDao.findByRequestIdDocType(456, DocumentType.LAB.getType())).thenReturn(List.of());
        when(attachmentOwnershipService.findAttachableIds(DocumentType.LAB, 123, Set.of(30, 40))).thenReturn(Set.of(30, 40));
        when(attachmentOwnershipService.findOwnedIds(DocumentType.LAB, 123, Set.of(30, 40))).thenReturn(Set.of(30));

        registerMock(EReferAttachmentDataDaoImpl.class, org.mockito.Mockito.mock(EReferAttachmentDataDaoImpl.class));
        registerMock(EReferAttachmentDaoImpl.class, org.mockito.Mockito.mock(EReferAttachmentDaoImpl.class));
        try (MockedStatic<OceanEReferralAttachmentUtil> ocean = mockStatic(OceanEReferralAttachmentUtil.class)) {
            manager.attachToConsult(loggedInInfo, DocumentType.LAB, new String[] {"30", "40"}, "999", 456, 123, Boolean.TRUE);

            ocean.verify(() -> OceanEReferralAttachmentUtil.attachOceanEReferralConsult("30", 123, DocumentType.LAB.getType()));
            ocean.verifyNoMoreInteractions();
        }
        ArgumentCaptor<ConsultDocs> persisted = ArgumentCaptor.forClass(ConsultDocs.class);
        verify(consultDocsDao, org.mockito.Mockito.times(2)).persist(persisted.capture());
        assertThat(persisted.getAllValues()).extracting(ConsultDocs::getDocumentNo).containsExactly(30, 40);
    }
}
