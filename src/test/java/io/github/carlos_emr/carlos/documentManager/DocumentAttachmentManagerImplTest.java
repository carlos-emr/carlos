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
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDao;
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.EFormDocs;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentAttachmentManagerImpl Unit Tests")
@Tag("unit")
@Tag("documentManager")
class DocumentAttachmentManagerImplTest extends CarlosUnitTestBase {

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

        manager.attachToConsult(
                loggedInInfo,
                DocumentType.DOC,
                new String[] {"789"},
                "999",
                requestId,
                demographicNo);

        ArgumentCaptor<ConsultDocs> consultDocCaptor = ArgumentCaptor.forClass(ConsultDocs.class);
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, demographicNo);
        // Once to preserve any archive relationship omitted by the UI, then once in the legacy
        // DocumentAttach differ that applies the submitted relationship set.
        verify(consultDocsDao, times(2)).findByRequestIdDocType(requestId, DocumentType.DOC.getType());
        verify(consultDocsDao).persist(consultDocCaptor.capture());
        ConsultDocs persisted = consultDocCaptor.getValue();
        assertThat(persisted.getRequestId()).isEqualTo(requestId);
        assertThat(persisted.getDocumentNo()).isEqualTo(789);
        assertThat(persisted.getDocType()).isEqualTo(DocumentType.DOC.getType());
        assertThat(persisted.getProviderNo()).isEqualTo("999");
    }

    @Test
    @DisplayName("should not perform an archive preservation query for non-document consultation attachments")
    void shouldNotPerformArchivePreservationQuery_forNonDocumentConsultationAttachments() {
        int demographicNo = 123;
        int requestId = 456;
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, demographicNo))
                .thenReturn(true);
        when(consultDocsDao.findByRequestIdDocType(requestId, DocumentType.LAB.getType()))
                .thenReturn(List.of());

        manager.attachToConsult(
                loggedInInfo, DocumentType.LAB, new String[] {"789"}, "999", requestId, demographicNo);

        verify(consultDocsDao).findByRequestIdDocType(requestId, DocumentType.LAB.getType());
        verify(outboundEmailArchiveDao, never()).findExistingDocumentNos(org.mockito.ArgumentMatchers.any());
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
    @DisplayName("should refuse archive eDocs before attaching them to a consult")
    void shouldRefuseArchiveEdocs_beforeAttachToConsult() {
        int demographicNo = 123;
        int requestId = 456;
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, demographicNo))
                .thenReturn(true);
        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(789)))
                .thenReturn(java.util.Set.of(789));

        assertThatThrownBy(() -> manager.attachToConsult(
                loggedInInfo, DocumentType.DOC, new String[] {"789"}, "999", requestId, demographicNo))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verify(consultDocsDao, never()).persist(org.mockito.ArgumentMatchers.any());
        verify(consultDocsDao, never()).merge(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("should refuse archive eDocs before attaching them to an Ocean consult")
    void shouldRefuseArchiveEdocs_beforeAttachToOceanConsult() {
        int demographicNo = 123;
        int requestId = 456;
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, demographicNo))
                .thenReturn(true);
        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(789)))
                .thenReturn(java.util.Set.of(789));

        assertThatThrownBy(() -> manager.attachToConsult(
                loggedInInfo, DocumentType.DOC, new String[] {"789"}, "999",
                requestId, demographicNo, Boolean.TRUE))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verify(consultDocsDao, never()).persist(org.mockito.ArgumentMatchers.any());
        verify(consultDocsDao, never()).merge(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("should preserve an existing archive eDoc omitted from a consultation update")
    void shouldPreserveExistingArchiveEdoc_omittedFromConsultationUpdate() {
        int demographicNo = 123;
        int requestId = 456;
        ConsultDocs archiveDocument = new ConsultDocs(
                requestId, 789, DocumentType.DOC.getType(), "999");

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, demographicNo))
                .thenReturn(true);
        when(consultDocsDao.findByRequestIdDocType(requestId, DocumentType.DOC.getType()))
                .thenReturn(List.of(archiveDocument));
        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(790)))
                .thenReturn(Set.of());
        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(789)))
                .thenReturn(Set.of(789));

        manager.attachToConsult(
                loggedInInfo, DocumentType.DOC, new String[] {"790"}, "999", requestId, demographicNo);

        verify(consultDocsDao, never()).merge(archiveDocument);
        ArgumentCaptor<ConsultDocs> persistedDocument = ArgumentCaptor.forClass(ConsultDocs.class);
        verify(consultDocsDao).persist(persistedDocument.capture());
        assertThat(persistedDocument.getValue().getDocumentNo()).isEqualTo(790);
    }

    @Test
    @DisplayName("should refuse new archive eDocs and preserve existing ones on an eForm")
    void shouldRefuseNewArchiveEdocs_andPreserveExistingOnEform() {
        int demographicNo = 123;
        int fdid = 456;
        EFormDocs archiveDocument = new EFormDocs(fdid, 789, DocumentType.DOC.getType(), "999");

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.WRITE, demographicNo))
                .thenReturn(true);
        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(791)))
                .thenReturn(Set.of(791));

        assertThatThrownBy(() -> manager.attachToEForm(
                loggedInInfo, DocumentType.DOC, new String[] {"791"}, "999", fdid, demographicNo))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(790)))
                .thenReturn(Set.of());
        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(789)))
                .thenReturn(Set.of(789));
        when(eFormDocsDao.findByFdidIdDocType(fdid, DocumentType.DOC.getType()))
                .thenReturn(List.of(archiveDocument));

        manager.attachToEForm(
                loggedInInfo, DocumentType.DOC, new String[] {"790"}, "999", fdid, demographicNo);

        verify(eFormDocsDao, never()).merge(archiveDocument);
        ArgumentCaptor<EFormDocs> persistedDocument = ArgumentCaptor.forClass(EFormDocs.class);
        verify(eFormDocsDao).persist(persistedDocument.capture());
        assertThat(persistedDocument.getValue().getDocumentNo()).isEqualTo(790);
    }
}
