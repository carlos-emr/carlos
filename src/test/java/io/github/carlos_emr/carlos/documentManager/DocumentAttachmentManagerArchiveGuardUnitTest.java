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
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.EFormDocs;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentAttachmentManagerImpl outbound email archive guard")
@Tag("unit")
@Tag("documentManager")
class DocumentAttachmentManagerArchiveGuardUnitTest extends CarlosUnitTestBase {

    private static final int DEMOGRAPHIC_NO = 123;
    private static final int PARENT_ID = 456;

    @Mock
    private SecurityInfoManager securityInfoManager;

    @Mock
    private ConsultDocsDao consultDocsDao;

    @Mock
    private EFormDocsDao eFormDocsDao;

    @Mock
    private OutboundEmailArchiveDao outboundEmailArchiveDao;

    @Mock
    private LoggedInInfo loggedInInfo;

    private DocumentAttachmentManagerImpl manager;

    /*
     * The plain consult read/write privilege tests live in DocumentAttachmentManagerConsultAccessUnitTest;
     * this class covers only the archive refusal and preservation rules. The transactional
     * DocumentAttach writer locks the parent and validates the selection through Spring beans, so
     * those collaborators are registered leniently for the tests that reach a write.
     */
    @BeforeEach
    void setUp() {
        manager = new DocumentAttachmentManagerImpl();
        injectDependency(manager, "securityInfoManager", securityInfoManager);
        injectDependency(manager, "consultDocsDao", consultDocsDao);
        injectDependency(manager, "eFormDocsDao", eFormDocsDao);
        injectDependency(manager, "outboundEmailArchiveDao", outboundEmailArchiveDao);
        registerMock(ConsultDocsDao.class, consultDocsDao);
        registerMock(EFormDocsDao.class, eFormDocsDao);
        registerMock(OutboundEmailArchiveDao.class, outboundEmailArchiveDao);

        lenient().when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, DEMOGRAPHIC_NO)).thenReturn(true);
        PlatformTransactionManager transactions = createAndRegisterMock(PlatformTransactionManager.class);
        lenient().when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        ConsultationRequest consultation = new ConsultationRequest();
        consultation.setDemographicId(DEMOGRAPHIC_NO);
        lenient().when(createAndRegisterMock(ConsultationRequestDao.class).lockForAttachmentSync(PARENT_ID))
                .thenReturn(consultation);
        EFormData eForm = new EFormData();
        eForm.setDemographicId(DEMOGRAPHIC_NO);
        lenient().when(createAndRegisterMock(EFormDataDao.class).lockForAttachmentSync(PARENT_ID)).thenReturn(eForm);
        AttachmentSelectionAccess selectionAccess = createAndRegisterMock(AttachmentSelectionAccess.class);
        lenient().when(selectionAccess.validate(eq(loggedInInfo), any(), eq(DEMOGRAPHIC_NO), any(), any()))
                .thenReturn(true);
    }

    @Test
    @DisplayName("should not perform an archive preservation query for non-document consultation attachments")
    void shouldNotPerformArchivePreservationQuery_forNonDocumentConsultationAttachments() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, DEMOGRAPHIC_NO))
                .thenReturn(true);
        when(consultDocsDao.findByRequestIdDocTypeForUpdate(PARENT_ID, DocumentType.LAB.getType()))
                .thenReturn(List.of());
        createAndRegisterMock(PatientLabRoutingDao.class);

        manager.attachToConsult(
                loggedInInfo, DocumentType.LAB, new String[0], "999", PARENT_ID, DEMOGRAPHIC_NO);

        // Only the transactional differ reads the current lab set; the unlocked archive
        // preservation read is reserved for eDocs.
        verify(consultDocsDao).findByRequestIdDocTypeForUpdate(PARENT_ID, DocumentType.LAB.getType());
        verify(consultDocsDao, never()).findByRequestIdDocType(PARENT_ID, DocumentType.LAB.getType());
        verify(outboundEmailArchiveDao, never()).findExistingDocumentNos(any());
    }

    @Test
    @DisplayName("should refuse archive eDocs before attaching them to a consult")
    void shouldRefuseArchiveEdocs_beforeAttachToConsult() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, DEMOGRAPHIC_NO))
                .thenReturn(true);
        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(789)))
                .thenReturn(Set.of(789));

        assertThatThrownBy(() -> manager.attachToConsult(
                loggedInInfo, DocumentType.DOC, new String[] {"789"}, "999", PARENT_ID, DEMOGRAPHIC_NO))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verify(consultDocsDao, never()).persist(any());
        verify(consultDocsDao, never()).merge(any());
    }

    @Test
    @DisplayName("should refuse archive eDocs before attaching them to an Ocean consult")
    void shouldRefuseArchiveEdocs_beforeAttachToOceanConsult() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, DEMOGRAPHIC_NO))
                .thenReturn(true);
        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(789)))
                .thenReturn(Set.of(789));

        assertThatThrownBy(() -> manager.attachToConsult(
                loggedInInfo, DocumentType.DOC, new String[] {"789"}, "999",
                PARENT_ID, DEMOGRAPHIC_NO, Boolean.TRUE))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verify(consultDocsDao, never()).persist(any());
        verify(consultDocsDao, never()).merge(any());
    }

    @Test
    @DisplayName("should refuse archive eDocs during consultation attachment verification")
    void shouldRefuseArchiveEdocs_duringVerifyConsultAttachments() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, DEMOGRAPHIC_NO))
                .thenReturn(true);
        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(789)))
                .thenReturn(Set.of(789));

        assertThatThrownBy(() -> manager.verifyConsultAttachments(loggedInInfo, PARENT_ID, DEMOGRAPHIC_NO,
                Map.of(DocumentType.DOC, new String[] {"789"})))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verify(consultDocsDao, never()).persist(any());
        verify(consultDocsDao, never()).merge(any());
    }

    @Test
    @DisplayName("should preserve an existing archive eDoc omitted from a consultation update")
    void shouldPreserveExistingArchiveEdoc_omittedFromConsultationUpdate() {
        ConsultDocs archiveDocument = new ConsultDocs(
                PARENT_ID, 789, DocumentType.DOC.getType(), "999");

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, DEMOGRAPHIC_NO))
                .thenReturn(true);
        when(consultDocsDao.findByRequestIdDocType(PARENT_ID, DocumentType.DOC.getType()))
                .thenReturn(List.of(archiveDocument));
        when(consultDocsDao.findByRequestIdDocTypeForUpdate(PARENT_ID, DocumentType.DOC.getType()))
                .thenReturn(List.of(archiveDocument));
        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(790)))
                .thenReturn(Set.of());
        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(789)))
                .thenReturn(Set.of(789));

        manager.attachToConsult(
                loggedInInfo, DocumentType.DOC, new String[] {"790"}, "999", PARENT_ID, DEMOGRAPHIC_NO);

        verify(consultDocsDao, never()).merge(archiveDocument);
        ArgumentCaptor<ConsultDocs> persistedDocument = ArgumentCaptor.forClass(ConsultDocs.class);
        verify(consultDocsDao).persist(persistedDocument.capture());
        assertThat(persistedDocument.getValue().getDocumentNo()).isEqualTo(790);
    }

    @Test
    @DisplayName("should refuse a new archive eDoc on an eForm")
    void shouldRefuseNewArchiveEdoc_onEform() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.WRITE, DEMOGRAPHIC_NO))
                .thenReturn(true);
        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(791)))
                .thenReturn(Set.of(791));

        assertThatThrownBy(() -> manager.attachToEForm(
                loggedInInfo, DocumentType.DOC, new String[] {"791"}, "999", PARENT_ID, DEMOGRAPHIC_NO))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");
    }

    @Test
    @DisplayName("should preserve an existing archive eDoc on an eForm")
    void shouldPreserveExistingArchiveEdoc_onEform() {
        EFormDocs archiveDocument = new EFormDocs(PARENT_ID, 789, DocumentType.DOC.getType(), "999");

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.WRITE, DEMOGRAPHIC_NO))
                .thenReturn(true);
        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(790)))
                .thenReturn(Set.of());
        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(789)))
                .thenReturn(Set.of(789));
        when(eFormDocsDao.findByFdidIdDocType(PARENT_ID, DocumentType.DOC.getType()))
                .thenReturn(List.of(archiveDocument));
        when(eFormDocsDao.findByFdidIdDocTypeForUpdate(PARENT_ID, DocumentType.DOC.getType()))
                .thenReturn(List.of(archiveDocument));

        manager.attachToEForm(
                loggedInInfo, DocumentType.DOC, new String[] {"790"}, "999", PARENT_ID, DEMOGRAPHIC_NO);

        verify(eFormDocsDao, never()).merge(archiveDocument);
        ArgumentCaptor<EFormDocs> persistedDocument = ArgumentCaptor.forClass(EFormDocs.class);
        verify(eFormDocsDao).persist(persistedDocument.capture());
        assertThat(persistedDocument.getValue().getDocumentNo()).isEqualTo(790);
    }
}
