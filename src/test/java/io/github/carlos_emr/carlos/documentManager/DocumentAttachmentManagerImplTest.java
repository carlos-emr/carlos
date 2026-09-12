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
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.managers.LabManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import java.util.List;

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
    private LoggedInInfo loggedInInfo;

    @Mock
    private LabManager labManager;

    private DocumentAttachmentManagerImpl manager;

    @BeforeEach
    void setUp() {
        manager = new DocumentAttachmentManagerImpl(labManager);
        injectDependency(manager, "securityInfoManager", securityInfoManager);
        injectDependency(manager, "consultDocsDao", consultDocsDao);
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
}
