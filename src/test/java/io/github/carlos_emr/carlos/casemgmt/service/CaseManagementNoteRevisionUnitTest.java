/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.service;

import io.github.carlos_emr.carlos.casemgmt.dao.*;
import io.github.carlos_emr.carlos.casemgmt.model.*;
import io.github.carlos_emr.carlos.commn.dao.EChartDao;
import io.github.carlos_emr.carlos.commn.dao.HashAuditDao;
import io.github.carlos_emr.carlos.commn.model.HashAudit;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for immutable note revisions.
 *
 * @since 2026-10-14
 */
@Tag("unit")
@Tag("casemgmt")
class CaseManagementNoteRevisionUnitTest extends CarlosUnitTestBase {
    private CaseManagementManagerImpl manager;
    private CaseManagementNoteDAO notes;
    private CaseManagementNoteExtDAO extensions;
    private CaseManagementNoteLinkDAO links;
    private HashAuditDao hashes;

    @BeforeEach
    void prepare() {
        manager = new CaseManagementManagerImpl();
        notes = mock(CaseManagementNoteDAO.class);
        extensions = mock(CaseManagementNoteExtDAO.class);
        links = mock(CaseManagementNoteLinkDAO.class);
        hashes = mock(HashAuditDao.class);
        manager.setCaseManagementNoteDAO(notes);
        manager.setCaseManagementNoteExtDAO(extensions);
        manager.setCaseManagementNoteLinkDAO(links);
        injectDependency(manager, "hashAuditDao", hashes);
        injectDependency(manager, "eChartDao", mock(EChartDao.class));
        doAnswer(call -> {
            CaseManagementNote saved = call.getArgument(0);
            assertThat(saved.getId()).as("edits must be inserted with a new identity").isNull();
            saved.setId(502L);
            return null;
        }).when(notes).saveNote(any());
    }

    private CaseManagementNote edit() {
        CaseManagementNote note = new CaseManagementNote();
        note.setId(501L);
        note.setUuid(UUID.randomUUID().toString());
        note.setDemographic_no("101");
        note.setProviderNo("999998");
        note.setNote("Revised assessment");
        note.setHistory("Original assessment");
        note.setObservation_date(new Date(1000));
        note.setUpdate_date(new Date(2000));
        note.setAppointmentNo(42);
        when(notes.getMostRecentNoteForUpdate(note.getUuid())).thenReturn(note);
        return note;
    }

    @Test
    void shouldSaveNewIdentityAndHistory_withoutMutatingSubmittedRevision() {
        CaseManagementNote submitted = edit();
        CaseManagementNote saved = manager.saveNoteRevision(null, submitted, "999998", "Doctor", "", "doctor").note();
        assertThat(saved).isNotSameAs(submitted);
        assertThat(saved.getId()).isEqualTo(502L);
        assertThat(saved.getUuid()).isEqualTo(submitted.getUuid());
        assertThat(saved.getHistory()).contains("Revised assessment", "Original assessment");
        assertThat(submitted.getId()).isEqualTo(501L);
        assertThat(submitted.getHistory()).isEqualTo("Original assessment");
        assertThat(saved.getAppointmentNo()).isEqualTo(42);
        saved.getObservation_date().setTime(9000);
        assertThat(submitted.getObservation_date()).isEqualTo(new Date(1000));
    }

    @Test
    void shouldCarryIndependentIssueCollections_whenSavingRevision() {
        CaseManagementNote submitted = edit();
        CaseManagementIssue issue = new CaseManagementIssue();
        issue.setId(7L);
        submitted.getIssues().add(issue);
        CaseManagementNote saved = manager.saveNoteRevisionSimple(submitted);
        assertThat(saved.getIssues()).containsExactly(issue);
        saved.getIssues().clear();
        assertThat(submitted.getIssues()).containsExactly(issue);
    }

    @Test
    void shouldCopyExtensionsAndAttachmentLinks_withoutMovingHistoricalRows() {
        CaseManagementNoteExt old = new CaseManagementNoteExt();
        old.setId(10L);
        old.setNoteId(501L);
        old.setKeyVal("startDate");
        old.setValue("2020");
        old.setDateValue(new Date(1000));
        when(extensions.getExtByNoteForUpdate(501L)).thenReturn(List.of(old));
        CaseManagementNoteLink link = new CaseManagementNoteLink(CaseManagementNoteLink.DOCUMENT, 77L, 501L);
        link.setOtherId("attachment");
        when(links.getLinkByNoteForUpdate(501L)).thenReturn(List.of(link));

        CaseManagementNote saved = manager.saveNoteRevisionSimple(edit());

        ArgumentCaptor<CaseManagementNoteExt> extension = ArgumentCaptor.forClass(CaseManagementNoteExt.class);
        verify(extensions).save(extension.capture());
        CaseManagementNoteExt copy = extension.getValue();
        assertThat(copy.getId()).isNull();
        assertThat(copy.getNoteId()).isEqualTo(502L);
        assertThat(copy.getKeyVal()).isEqualTo(old.getKeyVal());
        assertThat(copy.getValue()).isEqualTo(old.getValue());
        assertThat(copy.getDateValue()).isEqualTo(old.getDateValue()).isNotSameAs(old.getDateValue());
        assertThat(old.getNoteId()).isEqualTo(501L);
        assertThat(saved.getExtend()).containsExactly(copy);
        ArgumentCaptor<CaseManagementNoteLink> attachment = ArgumentCaptor.forClass(CaseManagementNoteLink.class);
        verify(links).save(attachment.capture());
        assertThat(attachment.getValue().getNoteId()).isEqualTo(502L);
        assertThat(attachment.getValue().getTableId()).isEqualTo(77L);
        assertThat(attachment.getValue().getOtherId()).isEqualTo("attachment");
        assertThat(link.getNoteId()).isEqualTo(501L);
        verify(extensions, never()).getExtByNote(anyLong());
        verify(links, never()).getLinkByNote(anyLong());
    }

    @Test
    void shouldAssociateSignatureHashWithNewRevision_whenSavingSignedEdit() {
        CaseManagementNote submitted = edit();
        submitted.setSigned(true);
        submitted.setSigning_provider_no("999998");
        CaseManagementNote saved = manager.saveNoteRevision(null, submitted, "999998", "Doctor", "", "doctor").note();
        ArgumentCaptor<HashAudit> hash = ArgumentCaptor.forClass(HashAudit.class);
        verify(hashes).persist(hash.capture());
        assertThat(hash.getValue().getId2()).isEqualTo("502");
        assertThat(saved.isSigned()).isTrue();
        assertThat(saved.getSigning_provider_no()).isEqualTo("999998");
    }

    @Test
    void shouldPropagateHashFailure_whenRevisionCannotBeAudited() {
        CaseManagementNote submitted = edit();
        submitted.setSigned(true);
        doThrow(new IllegalStateException("audit unavailable")).when(hashes).persist(any());
        assertThatThrownBy(() -> manager.saveNoteRevision(null, submitted, "999998", "Doctor", "", "doctor"))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(extensions, links);
    }

    @Test
    void shouldRejectStaleRevision_beforePersistingAnything() {
        CaseManagementNote submitted = edit();
        CaseManagementNote newer = submitted.copyForRevision();
        newer.setId(503L);
        when(notes.getMostRecentNoteForUpdate(submitted.getUuid())).thenReturn(newer);
        assertThatThrownBy(() -> manager.saveNoteRevisionSimple(submitted))
                .isInstanceOf(StaleNoteRevisionException.class);
        verify(notes, never()).saveNote(any());
        verifyNoInteractions(extensions, links, hashes);
    }

    @Test
    void shouldRejectStaleRevision_beforeAssigningOrPersistingPendingIssues() {
        CaseManagementNote submitted = edit();
        CaseManagementIssue pending = new CaseManagementIssue();
        pending.setDemographic_no(101);
        submitted.getIssues().add(pending);
        CaseManagementIssueDAO issues = mock(CaseManagementIssueDAO.class);
        manager.setCaseManagementIssueDAO(issues);
        CaseManagementNote newer = submitted.copyForRevision();
        newer.setId(503L);
        when(notes.getMostRecentNoteForUpdate(submitted.getUuid())).thenReturn(newer);

        assertThatThrownBy(() -> manager.saveNoteRevision(null, submitted, "999998", "Doctor", "", null))
                .isInstanceOf(StaleNoteRevisionException.class);

        assertThat(pending.getId()).isNull();
        verifyNoInteractions(issues, extensions, links, hashes);
        verify(notes, never()).saveNote(any());
    }

    @Test
    void shouldRejectAnotherPatientsUuid_beforeCopyingClinicalMetadata() {
        CaseManagementNote submitted = edit();
        CaseManagementNote other = submitted.copyForRevision();
        other.setId(501L);
        other.setDemographic_no("102");
        when(notes.getMostRecentNoteForUpdate(submitted.getUuid())).thenReturn(other);
        assertThatThrownBy(() -> manager.saveNoteRevisionSimple(submitted)).isInstanceOf(SecurityException.class);
        verify(notes, never()).saveNote(any());
        verifyNoInteractions(extensions, links, hashes);
    }

    @Test
    void shouldCopyAttachmentForUuidOnlyClient_whenRevisingRestAnnotation() {
        CaseManagementNote previous = edit();
        CaseManagementNote submitted = previous.copyForRevision();
        CaseManagementNoteLink link = new CaseManagementNoteLink(CaseManagementNoteLink.DOCUMENT, 77L, 501L);
        when(links.getLinkByNoteForUpdate(501L)).thenReturn(List.of(link));
        manager.saveNoteRevisionSimple(submitted);
        ArgumentCaptor<CaseManagementNoteLink> copied = ArgumentCaptor.forClass(CaseManagementNoteLink.class);
        verify(links).save(copied.capture());
        assertThat(copied.getValue().getNoteId()).isEqualTo(502L);
        assertThat(copied.getValue().getTableId()).isEqualTo(77L);
        assertThat(link.getNoteId()).isEqualTo(501L);
    }

    @Test
    void shouldPersistNewNoteWithoutCopyingMetadata_whenThereIsNoPreviousRevision() {
        CaseManagementNote fresh = edit();
        fresh.setId(null);
        when(notes.getMostRecentNoteForUpdate(fresh.getUuid())).thenReturn(null);
        assertThat(manager.saveNoteRevisionSimple(fresh)).isSameAs(fresh);
        verifyNoInteractions(extensions, links);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldInitializeLegacyChain_withoutMergingDraftIntoOriginal(String legacyUuid) {
        CaseManagementNote submitted = edit();
        submitted.setUuid(legacyUuid);
        CaseManagementNote original = submitted.copyForRevision();
        original.setId(501L);
        original.setNote("Original assessment");
        original.setSigned(true);
        original.setSigning_provider_no("999997");
        when(notes.getNoteForUpdate(501L)).thenReturn(original);

        CaseManagementNote saved = manager.saveNoteRevisionSimple(submitted);

        assertThat(UUID.fromString(saved.getUuid())).hasToString(saved.getUuid());
        assertThat(original.getUuid()).isEqualTo(saved.getUuid());
        assertThat(original.getNote()).isEqualTo("Original assessment");
        assertThat(original.getHistory()).isEqualTo("Original assessment");
        assertThat(original.getUpdate_date()).isEqualTo(new Date(2000));
        assertThat(original.isSigned()).isTrue();
        assertThat(original.getSigning_provider_no()).isEqualTo("999997");
        assertThat(saved.getNote()).isEqualTo("Revised assessment");
        assertThat(submitted.getUuid()).isEqualTo(legacyUuid);
        verify(notes, never()).saveNote(original);
        verify(notes, never()).updateNote(any());
        verify(extensions).getExtByNoteForUpdate(501L);
        verify(links).getLinkByNoteForUpdate(501L);
    }

    @Test
    void shouldReuseAlreadyInitializedChain_whenLegacyClientOmitsUuid() {
        CaseManagementNote original = edit();
        CaseManagementNote submitted = original.copyForRevision();
        submitted.setId(original.getId());
        submitted.setUuid(null);
        when(notes.getNoteForUpdate(501L)).thenReturn(original);

        CaseManagementNote saved = manager.saveNoteRevision(null, submitted, "999998", "Doctor", "", "doctor").note();

        assertThat(saved.getUuid()).isEqualTo(original.getUuid());
        assertThat(submitted.getUuid()).isNull();
        verify(notes).getMostRecentNoteForUpdate(original.getUuid());
    }

    @Test
    void shouldRejectStaleLegacyDraft_whenAnotherWriterAlreadyInitializedAndRevisedIt() {
        CaseManagementNote original = edit();
        CaseManagementNote submitted = original.copyForRevision();
        submitted.setId(501L);
        submitted.setUuid(null);
        CaseManagementNote latest = original.copyForRevision();
        latest.setId(503L);
        when(notes.getNoteForUpdate(501L)).thenReturn(original);
        when(notes.getMostRecentNoteForUpdate(original.getUuid())).thenReturn(latest);

        assertThatThrownBy(() -> manager.saveNoteRevisionSimple(submitted)).isInstanceOf(StaleNoteRevisionException.class);
        assertThat(submitted.getUuid()).isNull();
        verify(notes, never()).saveNote(any());
        verifyNoInteractions(extensions, links, hashes);
    }

    @Test
    void shouldRejectAnotherPatientsLegacyRow_beforeAssigningItsUuid() {
        CaseManagementNote submitted = edit();
        submitted.setUuid(null);
        CaseManagementNote original = submitted.copyForRevision();
        original.setId(501L);
        original.setDemographic_no("102");
        when(notes.getNoteForUpdate(501L)).thenReturn(original);

        assertThatThrownBy(() -> manager.saveNoteRevisionSimple(submitted)).isInstanceOf(SecurityException.class);
        assertThat(original.getUuid()).isNull();
        verify(notes, never()).saveNote(any());
        verifyNoInteractions(extensions, links, hashes);
    }

    @Test
    void shouldRejectMissingLegacyRow_withoutCreatingAnotherNote() {
        CaseManagementNote submitted = edit();
        submitted.setUuid(null);
        assertThatThrownBy(() -> manager.saveNoteRevisionSimple(submitted)).isInstanceOf(StaleNoteRevisionException.class);
        verify(notes, never()).saveNote(any());
        verifyNoInteractions(extensions, links, hashes);
    }
}
