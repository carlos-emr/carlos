/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDao;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins that the outbound email archive guard actually engages in DocumentManagerImpl.
 *
 * <p>The guard's own recognition logic is covered by
 * {@code OutboundEmailArchiveDocumentGuardUnitTest}. What these tests exist for is the wiring:
 * a guard that is correct but not called is worth nothing, and a call site is easy to lose in a
 * merge. Each test drives a real entry point with an archive-backed document and asserts the
 * operation is refused rather than performed.</p>
 */
@DisplayName("DocumentManagerImpl outbound email archive guard")
@Tag("unit")
@Tag("security")
@Tag("email")
class DocumentManagerImplArchiveGuardUnitTest extends CarlosUnitTestBase {

    private static final Integer ARCHIVE_DOCUMENT_NO = 321;
    private static final Integer ORDINARY_DOCUMENT_NO = 999;
    private static final String ARCHIVE_MESSAGE =
            "Outbound email archive eDocs must be managed through the controlled archive workflow";

    private SecurityInfoManager securityInfoManager;
    private DocumentDao documentDao;
    private OutboundEmailArchiveDao outboundEmailArchiveDao;
    private LoggedInInfo loggedInInfo;
    private DocumentManagerImpl manager;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        documentDao = mock(DocumentDao.class);
        outboundEmailArchiveDao = mock(OutboundEmailArchiveDao.class);
        loggedInInfo = mock(LoggedInInfo.class);

        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), anyString(), anyString(), any()))
                .thenReturn(true);
        when(outboundEmailArchiveDao.existsByDocumentNo(ARCHIVE_DOCUMENT_NO)).thenReturn(true);
        when(outboundEmailArchiveDao.existsByDocumentNo(ORDINARY_DOCUMENT_NO)).thenReturn(false);

        manager = new DocumentManagerImpl();
        injectDependency(manager, "securityInfoManager", securityInfoManager);
        injectDependency(manager, "documentDao", documentDao);
        injectDependency(manager, "outboundEmailArchiveDao", outboundEmailArchiveDao);
    }

    @Test
    @DisplayName("should refuse to return an archive artifact from getDocument")
    void shouldRefuseToReturnArchiveArtifact_fromGetDocument() {
        when(documentDao.find(ARCHIVE_DOCUMENT_NO)).thenReturn(archiveDocument());

        assertThatThrownBy(() -> manager.getDocument(loggedInInfo, ARCHIVE_DOCUMENT_NO))
                .isInstanceOf(SecurityException.class)
                .hasMessage(ARCHIVE_MESSAGE);
    }

    @Test
    @DisplayName("should still return an ordinary document from getDocument")
    void shouldStillReturnOrdinaryDocument_fromGetDocument() {
        // The guard has to be narrow as well as present: a refusal that also caught ordinary
        // documents would be caught by users long before review, but a test says so up front.
        Document ordinary = new Document();
        ordinary.setDocumentNo(ORDINARY_DOCUMENT_NO);
        when(documentDao.find(ORDINARY_DOCUMENT_NO)).thenReturn(ordinary);

        assertThat(manager.getDocument(loggedInInfo, ORDINARY_DOCUMENT_NO)).isSameAs(ordinary);
    }

    @Test
    @DisplayName("should refuse to save over an archive artifact")
    void shouldRefuseToSaveOverArchiveArtifact() {
        assertThatThrownBy(() -> manager.saveDocument(loggedInInfo, archiveDocument(), null))
                .isInstanceOf(SecurityException.class)
                .hasMessage(ARCHIVE_MESSAGE);

        // The point of guarding before the write, not after: nothing may reach the DAO.
        verify(documentDao, never()).merge(any(Document.class));
        verify(documentDao, never()).persist(any(Document.class));
    }

    @Test
    @DisplayName("should refuse to move an archive artifact")
    void shouldRefuseToMoveArchiveArtifact() {
        assertThatThrownBy(() -> manager.moveDocument(loggedInInfo, archiveDocument(), "/from", "/to"))
                .isInstanceOf(SecurityException.class)
                .hasMessage(ARCHIVE_MESSAGE);
    }

    @Test
    @DisplayName("should refuse acknowledgement lookups for an archive artifact")
    void shouldRefuseAcknowledgementLookups_forArchiveArtifact() {
        assertThatThrownBy(() -> manager.getProvidersThatHaveAcknowledgedDocument(loggedInInfo, ARCHIVE_DOCUMENT_NO))
                .isInstanceOf(SecurityException.class)
                .hasMessage(ARCHIVE_MESSAGE);
    }

    @Test
    @DisplayName("should hide archive artifacts from a patient document listing")
    void shouldHideArchiveArtifacts_fromPatientDocumentListing() {
        // Filtered, not refused. A patient who has ever been emailed would otherwise have an
        // unusable document list, so the whole call must still succeed for the rest.
        Document ordinary = new Document();
        ordinary.setDocumentNo(ORDINARY_DOCUMENT_NO);
        when(documentDao.findByDemographicAndDoctype(123, DocumentDao.DocumentType.LAB))
                .thenReturn(List.of(archiveDocument(), ordinary));
        when(outboundEmailArchiveDao.findExistingDocumentNos(any()))
                .thenReturn(Set.of(ARCHIVE_DOCUMENT_NO));

        List<Document> documents = manager.getDemographicDocumentsByDocumentType(loggedInInfo, 123, DocumentDao.DocumentType.LAB);

        assertThat(documents).containsExactly(ordinary);
    }

    @Test
    @DisplayName("should leave a listing untouched when it holds no archive artifacts")
    void shouldLeaveListingUntouched_whenItHoldsNoArchiveArtifacts() {
        Document ordinary = new Document();
        ordinary.setDocumentNo(ORDINARY_DOCUMENT_NO);
        when(documentDao.findByDemographicAndDoctype(123, DocumentDao.DocumentType.LAB)).thenReturn(List.of(ordinary));
        when(outboundEmailArchiveDao.findExistingDocumentNos(any())).thenReturn(Set.of());

        assertThat(manager.getDemographicDocumentsByDocumentType(loggedInInfo, 123, DocumentDao.DocumentType.LAB))
                .containsExactly(ordinary);
    }

    private Document archiveDocument() {
        Document document = new Document();
        document.setDocumentNo(ARCHIVE_DOCUMENT_NO);
        document.setDocfilename("20260707120000_outbound-email-44.eml");
        return document;
    }
}
