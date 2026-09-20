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
package io.github.carlos_emr.carlos.casemgmt.dao;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteExt;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Pins how a CPP item's extension fields have to be written.
 *
 * <p>{@code CaseManagementEntry2Action.issueNoteSave()} writes one {@code casemgmt_note_ext}
 * row per extension key. {@code saveNoteExt()} bottoms out in a JPA {@code persist()}, whose
 * behaviour on an already-managed instance is the trap these tests exist to document: the
 * action used to hoist a single {@code CaseManagementNoteExt} out of the loop and re-save it
 * for every field, which stored only whichever key was written last. Setting a CPP item's
 * start date and resolution date together therefore kept the resolution date and silently
 * dropped the start date.
 *
 * <p>Allocating per key is only half of it. {@code saveNote()} merges an existing note rather
 * than revising it under a fresh id, so a note keeps its id across edits and a blind
 * {@code persist()} per save would pile a second row onto every key. {@code getExtByNote()}
 * orders {@code id desc} while consumers such as {@code NotesService.getNote()} assign from
 * every row they walk, so the oldest row wins and an edited value reads back as the one it
 * replaced. The write therefore has to update the row a key already has.
 *
 * @since 2026-09-20
 */
@DisplayName("CPP note extension persistence")
@Tag("integration")
@Tag("dao")
@Tag("casemgmt")
@Transactional
public class CaseManagementCppExtPersistenceIntegrationTest extends CarlosTestBase {

    private static final String START = "2020-01-01";
    private static final String RESOLUTION = "2021-02-02";
    private static final String EDITED_START = "2022-03-03";

    @Autowired
    @Qualifier("CaseManagementNoteExtDAO")
    private CaseManagementNoteExtDAO caseManagementNoteExtDAO;

    @Autowired
    @Qualifier("CaseManagementNoteDAO")
    private CaseManagementNoteDAO caseManagementNoteDAO;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private Long noteId;

    @BeforeEach
    void setUp() {
        CaseManagementNote note = new CaseManagementNote();
        note.setDemographic_no("1");
        note.setNote("CPP item under test");
        note.setProviderNo("999998");
        note.setUuid(UUID.randomUUID().toString());
        note.setUpdate_date(new Date());
        note.setObservation_date(new Date());
        note.setSigned(false);
        note.setArchived(false);
        note.setLocked(false);
        caseManagementNoteDAO.saveNote(note);
        noteId = note.getId();
        hibernateTemplate.flush();
    }

    private List<CaseManagementNoteExt> storedExtensions() {
        entityManager.flush();
        return entityManager
                .createQuery("select e from CaseManagementNoteExt e where e.noteId = :noteId",
                        CaseManagementNoteExt.class)
                .setParameter("noteId", noteId)
                .getResultList();
    }

    private CaseManagementNoteExt newExtension(String keyVal, String dateValue) {
        CaseManagementNoteExt ext = new CaseManagementNoteExt();
        ext.setNoteId(noteId);
        ext.setKeyVal(keyVal);
        ext.setDateValue(dateValue);
        return ext;
    }

    @Test
    @Tag("create")
    @DisplayName("should keep both dates when each extension gets its own entity (#3739)")
    void shouldKeepBothDates_whenEachExtensionGetsItsOwnEntity() {
        caseManagementNoteExtDAO.save(newExtension(CaseManagementNoteExt.STARTDATE, START));
        caseManagementNoteExtDAO.save(newExtension(CaseManagementNoteExt.RESOLUTIONDATE, RESOLUTION));

        assertThat(storedExtensions())
                .as("one row per extension key, so neither date is lost")
                .extracting(CaseManagementNoteExt::getKeyVal, CaseManagementNoteExt::getDateValueStr)
                .containsExactlyInAnyOrder(
                        tuple(CaseManagementNoteExt.STARTDATE, START),
                        tuple(CaseManagementNoteExt.RESOLUTIONDATE, RESOLUTION));
    }

    @Test
    @Tag("update")
    @DisplayName("should keep one row per key when the same note is saved again (#3739)")
    void shouldKeepOneRowPerKey_whenTheSameNoteIsSavedAgain() {
        caseManagementNoteExtDAO.save(newExtension(CaseManagementNoteExt.STARTDATE, START));
        caseManagementNoteExtDAO.save(newExtension(CaseManagementNoteExt.RESOLUTIONDATE, RESOLUTION));
        entityManager.flush();

        // The second save of the same note, the way the action performs it: index the rows the
        // note already has by key, newest first, then update in place instead of persisting.
        Map<String, CaseManagementNoteExt> extByKey = new HashMap<>();
        for (CaseManagementNoteExt existing : caseManagementNoteExtDAO.getExtByNote(noteId)) {
            extByKey.putIfAbsent(existing.getKeyVal(), existing);
        }
        CaseManagementNoteExt start = extByKey.get(CaseManagementNoteExt.STARTDATE);
        assertThat(start).as("the start date row from the first save is found by key").isNotNull();
        start.setDateValue(EDITED_START);
        caseManagementNoteExtDAO.update(start);

        assertThat(storedExtensions())
                .as("the edit replaces the start date rather than adding a second row for the key")
                .extracting(CaseManagementNoteExt::getKeyVal, CaseManagementNoteExt::getDateValueStr)
                .containsExactlyInAnyOrder(
                        tuple(CaseManagementNoteExt.STARTDATE, EDITED_START),
                        tuple(CaseManagementNoteExt.RESOLUTIONDATE, RESOLUTION));
    }

    @Test
    @Tag("create")
    @DisplayName("should collapse to the last key when one entity is reused across extensions (#3739)")
    void shouldCollapseToTheLastKey_whenOneEntityIsReusedAcrossExtensions() {
        // The shape the action used to have. This is not behaviour anyone wants; it is asserted
        // so the reason the entity must be allocated inside the loop stays visible, and so a
        // future persistence-provider upgrade that changes it is noticed here rather than in a
        // chart. Re-persisting a managed instance is a no-op, so the second save only mutates
        // the row the first one created.
        CaseManagementNoteExt shared = newExtension(CaseManagementNoteExt.STARTDATE, START);
        caseManagementNoteExtDAO.save(shared);
        Long firstRowId = shared.getId();

        shared.setKeyVal(CaseManagementNoteExt.RESOLUTIONDATE);
        shared.setDateValue(RESOLUTION);
        caseManagementNoteExtDAO.save(shared);

        assertThat(shared.getId())
                .as("re-persisting a managed instance does not allocate a second row")
                .isEqualTo(firstRowId);
        assertThat(storedExtensions())
                .as("the start date is gone: one row survives, holding the key written last")
                .extracting(CaseManagementNoteExt::getKeyVal, CaseManagementNoteExt::getDateValueStr)
                .containsExactly(tuple(CaseManagementNoteExt.RESOLUTIONDATE, RESOLUTION));
    }
}
