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
        verify(consultDocsDao, times(2)).findByRequestIdDocType(requestId, DocumentType.DOC.getType());
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
    @DisplayName("should reject archive-backed documents before consult attachment persistence")
    void shouldRejectArchiveDocsBeforeConsultAttach() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, 456)).thenReturn(true);
        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(321))).thenReturn(Set.of(321));

        assertThatThrownBy(() -> manager.attachToConsult(loggedInInfo, DocumentType.DOC,
                new String[] {"321"}, "999998", 123, 456))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verifyNoInteractions(consultDocsDao);
    }

    @Test
    @DisplayName("should reject archive-backed documents before Ocean consult attachment staging")
    void shouldRejectArchiveDocsBeforeOceanConsultAttach() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, 456)).thenReturn(true);
        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(321))).thenReturn(Set.of(321));

        assertThatThrownBy(() -> manager.attachToConsult(loggedInInfo, DocumentType.DOC,
                new String[] {"321"}, "999998", 123, 456, true))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verifyNoInteractions(consultDocsDao);
    }

    @Test
    @DisplayName("should reject archive-backed documents before eForm attachment persistence")
    void shouldRejectArchiveDocsBeforeEFormAttach() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.WRITE, 456)).thenReturn(true);
        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(321))).thenReturn(Set.of(321));

        assertThatThrownBy(() -> manager.attachToEForm(loggedInInfo, DocumentType.DOC,
                new String[] {"321"}, "999998", 123, 456))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verifyNoInteractions(eFormDocsDao);
    }

    @Test
    @DisplayName("should filter archive-backed documents from consult attachment reads")
    void shouldFilterArchiveDocsFromConsultAttachmentReads() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.READ, 456)).thenReturn(true);
        when(consultDocsDao.findByRequestIdDocType(123, DocumentType.DOC.getType())).thenReturn(List.of(
                consultDoc(321),
                consultDoc(654)));
        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(321, 654))).thenReturn(Set.of(321));

        assertThat(manager.getConsultAttachments(loggedInInfo, 123, DocumentType.DOC, 456))
                .containsExactly("654");
    }

    @Test
    @DisplayName("should filter archive-backed documents from eForm attachment reads")
    void shouldFilterArchiveDocsFromEFormAttachmentReads() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, 456)).thenReturn(true);
        when(eFormDocsDao.findByFdidIdDocType(123, DocumentType.DOC.getType())).thenReturn(List.of(
                eFormDoc(321),
                eFormDoc(654)));
        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(321, 654))).thenReturn(Set.of(321));

        assertThat(manager.getEFormAttachments(loggedInInfo, 123, DocumentType.DOC, 456))
                .containsExactly("654");
    }

    @Test
    @DisplayName("should preserve hidden archive-backed consult attachments during generic saves")
    void shouldPreserveHiddenArchiveDocs_whenSavingConsultAttachments() {
        ConsultDocs archiveAttachment = consultDoc(321);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, 456))
                .thenReturn(true);
        when(consultDocsDao.findByRequestIdDocType(123, DocumentType.DOC.getType()))
                .thenReturn(List.of(archiveAttachment));
        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(321))).thenReturn(Set.of(321));

        manager.attachToConsult(
                loggedInInfo, DocumentType.DOC, new String[0], "999998", 123, 456);

        assertThat(archiveAttachment.getDeleted()).isNull();
        verify(consultDocsDao, never()).merge(archiveAttachment);
    }

    private ConsultDocs consultDoc(int documentNo) {
        ConsultDocs consultDoc = new ConsultDocs();
        consultDoc.setDocumentNo(documentNo);
        consultDoc.setDocType(DocumentType.DOC.getType());
        return consultDoc;
    }

    private EFormDocs eFormDoc(int documentNo) {
        EFormDocs eFormDoc = new EFormDocs();
        eFormDoc.setDocumentNo(documentNo);
        eFormDoc.setDocType(DocumentType.DOC.getType());
        return eFormDoc;
    }
}
