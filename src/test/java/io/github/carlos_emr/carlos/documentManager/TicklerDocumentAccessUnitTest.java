/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tickler document links must not authorize soft-deleted documents, even though the shared
 * viewer gate ({@code DocumentPatientLink.requireAccess}) leaves status to the calling workflow.
 */
@Tag("unit")
@Tag("fast")
class TicklerDocumentAccessUnitTest extends CarlosUnitTestBase {
    private static final int DOCUMENT_NO = 42;
    private static final int PATIENT = 10;
    private static final int OTHER_PATIENT = 20;

    private final DocumentDao documents = mock(DocumentDao.class);
    private final CtlDocumentDao links = mock(CtlDocumentDao.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final LoggedInInfo info = mock(LoggedInInfo.class);
    private final Document document = new Document();
    private final List<CtlDocument> patientLinks = new ArrayList<>();
    private TicklerDocumentAccess access;

    private static CtlDocument link(int patient, String status) {
        CtlDocument link = new CtlDocument();
        link.setId(new CtlDocumentPK("demographic", patient, DOCUMENT_NO));
        link.setStatus(status);
        return link;
    }

    @BeforeEach
    void setUpAccess() {
        registerMock(DocumentDao.class, documents);
        registerMock(PatientLabRoutingDao.class, mock(PatientLabRoutingDao.class));
        registerMock(QueueDocumentLinkDao.class, mock(QueueDocumentLinkDao.class));
        registerMock(ProgramManager2.class, mock(ProgramManager2.class));
        patientLinks.add(link(PATIENT, null));
        // Both lookups read the same stored links, as ctl_document does in production.
        when(links.findByDocumentNoAndModule(DOCUMENT_NO, "demographic")).thenAnswer(call -> List.copyOf(patientLinks));
        when(documents.findCtlDocsAndDocsByDocNo(DOCUMENT_NO)).thenAnswer(call -> {
            List<Object[]> rows = new ArrayList<>();
            for (CtlDocument stored : patientLinks) rows.add(new Object[]{document, stored});
            return rows;
        });
        when(documents.find(DOCUMENT_NO)).thenReturn(document);
        access = new TicklerDocumentAccess(security, links, documents);
    }

    @Test
    @DisplayName("should allow an active document the viewer would authorize")
    void shouldAllowRead_whenDocumentActiveAndPatientAuthorized() {
        document.setStatus('A');
        when(security.isAllowedAccessToPatientRecord(info, PATIENT)).thenReturn(true);

        assertThatCode(() -> access.requireRead(info, DOCUMENT_NO)).doesNotThrowAnyException();
        assertThat(access.canRead(info, DOCUMENT_NO)).isTrue();
    }

    @Test
    @DisplayName("should refuse a soft-deleted document even for an authorized user")
    void shouldDenyRead_whenDocumentDeleted() {
        document.setStatus('D');
        when(security.isAllowedAccessToPatientRecord(info, PATIENT)).thenReturn(true);

        assertThatThrownBy(() -> access.requireRead(info, DOCUMENT_NO))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not available");
        assertThat(access.canRead(info, DOCUMENT_NO)).isFalse();
    }

    @Test
    @DisplayName("should refuse an active document whose every patient link was deleted")
    void shouldDenyRead_whenOnlyDeletedPatientLinksRemain() {
        document.setStatus('A');
        patientLinks.clear();
        patientLinks.add(link(PATIENT, "D"));
        when(security.isAllowedAccessToPatientRecord(info, PATIENT)).thenReturn(true);

        assertThatThrownBy(() -> access.requireRead(info, DOCUMENT_NO))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not available");
        assertThat(access.canRead(info, DOCUMENT_NO)).isFalse();
    }

    @Test
    @DisplayName("should allow a document with one live link and one deleted link, gated on the live patient")
    void shouldAllowRead_whenOneLivePatientLinkRemains() {
        document.setStatus('A');
        patientLinks.clear();
        patientLinks.add(link(PATIENT, "A"));
        patientLinks.add(link(OTHER_PATIENT, "D"));
        when(security.isAllowedAccessToPatientRecord(info, PATIENT)).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(info, OTHER_PATIENT)).thenReturn(true);

        assertThatCode(() -> access.requireRead(info, DOCUMENT_NO)).doesNotThrowAnyException();
        verify(security).isAllowedAccessToPatientRecord(info, PATIENT);
    }

    @Test
    @DisplayName("should refuse when the live link's patient is not accessible, despite a deleted link")
    void shouldDenyRead_whenLiveLinkPatientDenied() {
        document.setStatus('A');
        patientLinks.clear();
        patientLinks.add(link(PATIENT, "A"));
        patientLinks.add(link(OTHER_PATIENT, "D"));
        when(security.isAllowedAccessToPatientRecord(info, PATIENT)).thenReturn(false);
        when(security.isAllowedAccessToPatientRecord(info, OTHER_PATIENT)).thenReturn(true);

        assertThatThrownBy(() -> access.requireRead(info, DOCUMENT_NO))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("patient record");
    }

    @Test
    @DisplayName("should refuse a document that no longer exists")
    void shouldDenyRead_whenDocumentMissing() {
        when(documents.find(DOCUMENT_NO)).thenReturn(null);
        when(security.isAllowedAccessToPatientRecord(info, PATIENT)).thenReturn(true);

        assertThat(access.canRead(info, DOCUMENT_NO)).isFalse();
    }

    @Test
    @DisplayName("should deny on patient access before consulting document status")
    void shouldDenyBeforeStatusLookup_whenPatientAccessDenied() {
        document.setStatus('A');
        when(security.isAllowedAccessToPatientRecord(info, PATIENT)).thenReturn(false);

        assertThatThrownBy(() -> access.requireRead(info, DOCUMENT_NO))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("patient record");
        verify(documents, never()).find(DOCUMENT_NO);
    }
}
