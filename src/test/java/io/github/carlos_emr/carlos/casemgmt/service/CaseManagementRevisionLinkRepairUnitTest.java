/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.service;

import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteExtDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteLinkDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A revision save recovers the document or prescription association an older chain keeps only on its
 * first revision (#6132).
 *
 * <p>Opening a note to read no longer repairs it, and the classic editor's Edit button does not reach
 * the repair the chart's editor runs when it opens by POST, so an older chain whose current row has no
 * history and whose link sits only on its first row used to copy no link to the new revision. The
 * save itself now adds the link, in its own transaction, once and only when the revision holds none of
 * that kind. Every patient and note here is FAKE.</p>
 */
@Tag("unit")
@Tag("casemgmt")
class CaseManagementRevisionLinkRepairUnitTest extends CarlosUnitTestBase {
    private static final String UUID_OF_CHAIN = "FAKE-chain-uuid";
    private static final long FIRST = 1L;
    private static final long CURRENT = 3L;
    private static final long NEW_REVISION = 4L;

    private CaseManagementNoteDAO notes;
    private CaseManagementNoteLinkDAO links;
    private CaseManagementManagerImpl manager;
    /** The links as the database would hold them, by note id. */
    private final List<CaseManagementNoteLink> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        notes = mock(CaseManagementNoteDAO.class);
        links = createAndRegisterMock(CaseManagementNoteLinkDAO.class);
        CaseManagementNoteExtDAO extensions = mock(CaseManagementNoteExtDAO.class);
        when(extensions.getExtByNoteForUpdate(anyLong())).thenReturn(List.of());
        manager = new CaseManagementManagerImpl();
        injectDependency(manager, "caseManagementNoteDAO", notes);
        injectDependency(manager, "caseManagementNoteLinkDAO", links);
        injectDependency(manager, "caseManagementNoteExtDAO", extensions);

        CaseManagementNote first = chainNote(FIRST);
        CaseManagementNote current = chainNote(CURRENT);
        CaseManagementNote created = chainNote(NEW_REVISION);
        when(notes.getMostRecentNoteForUpdate(UUID_OF_CHAIN)).thenReturn(current);
        when(notes.getNotesByUUID(UUID_OF_CHAIN)).thenReturn(List.of(current, first, created));
        org.mockito.Mockito.doAnswer(call -> {
            call.<CaseManagementNote>getArgument(0).setId(NEW_REVISION);
            return null;
        }).when(notes).saveNote(any());
        when(links.getLinkByNoteForUpdate(anyLong())).thenAnswer(call -> {
            long noteId = call.getArgument(0);
            return stored.stream().filter(link -> link.getNoteId() == noteId).toList();
        });
        org.mockito.Mockito.doAnswer(call -> {
            stored.add(call.getArgument(0));
            return null;
        }).when(links).save(any());
        when(links.getLastLinkByNote(anyLong())).thenAnswer(call -> {
            long noteId = call.getArgument(0);
            return stored.stream().filter(link -> link.getNoteId() == noteId).reduce((a, b) -> b).orElse(null);
        });
    }

    @Test
    @DisplayName("should carry a prescription link kept only on the first revision to the new revision")
    void shouldCarryThePrescriptionLink_whenOnlyTheFirstRevisionHoldsIt() {
        stored.add(link(FIRST, CaseManagementNoteLink.DRUGS, 77L));

        manager.saveNoteRevisionSimple(submittedCurrent());

        assertThat(linksOf(NEW_REVISION)).singleElement().satisfies(link -> {
            assertThat(link.getTableName()).isEqualTo(CaseManagementNoteLink.DRUGS);
            assertThat(link.getTableId()).isEqualTo(77L);
        });
    }

    @Test
    @DisplayName("should carry a document link kept only on the first revision to the new revision")
    void shouldCarryTheDocumentLink_whenOnlyTheFirstRevisionHoldsIt() {
        stored.add(link(FIRST, CaseManagementNoteLink.DOCUMENT, 88L));

        manager.saveNoteRevisionSimple(submittedCurrent());

        assertThat(linksOf(NEW_REVISION)).singleElement().satisfies(link -> {
            assertThat(link.getTableName()).isEqualTo(CaseManagementNoteLink.DOCUMENT);
            assertThat(link.getTableId()).isEqualTo(88L);
        });
    }

    @Test
    @DisplayName("should add no second link when the previous revision's own link was copied")
    void shouldAddNothing_whenTheLinkWasAlreadyCopiedFromThePreviousRevision() {
        stored.add(link(FIRST, CaseManagementNoteLink.DRUGS, 77L));
        stored.add(link(CURRENT, CaseManagementNoteLink.DRUGS, 77L));

        manager.saveNoteRevisionSimple(submittedCurrent());

        assertThat(linksOf(NEW_REVISION)).hasSize(1);
    }

    @Test
    @DisplayName("should add no link to a chain that never had one")
    void shouldAddNothing_whenTheChainHasNoLink() {
        manager.saveNoteRevisionSimple(submittedCurrent());

        assertThat(stored).isEmpty();
    }

    @Test
    @DisplayName("should leave a single-revision note alone")
    void shouldAddNothing_whenTheNoteIsItsOwnFirstRevision() {
        stored.add(link(FIRST, CaseManagementNoteLink.DRUGS, 77L));
        when(notes.getNotesByUUID(UUID_OF_CHAIN)).thenReturn(List.of(chainNote(NEW_REVISION)));

        manager.saveNoteRevisionSimple(submittedCurrent());

        assertThat(linksOf(NEW_REVISION)).isEmpty();
    }

    private List<CaseManagementNoteLink> linksOf(long noteId) {
        return stored.stream().filter(link -> link.getNoteId() == noteId).toList();
    }

    private CaseManagementNote submittedCurrent() {
        CaseManagementNote submitted = chainNote(CURRENT);
        submitted.setNote("FAKE revised text");
        return submitted;
    }

    private static CaseManagementNote chainNote(long id) {
        CaseManagementNote note = new CaseManagementNote();
        note.setId(id);
        note.setUuid(UUID_OF_CHAIN);
        note.setDemographic_no("101");
        note.setProviderNo("999998");
        note.setNote("FAKE note text");
        return note;
    }

    private static CaseManagementNoteLink link(long noteId, Integer table, long tableId) {
        CaseManagementNoteLink link = new CaseManagementNoteLink();
        link.setNoteId(noteId);
        link.setTableName(table);
        link.setTableId(tableId);
        return link;
    }
}
