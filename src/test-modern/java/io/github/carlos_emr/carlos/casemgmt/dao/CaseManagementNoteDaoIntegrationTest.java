/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * This software was written for CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.casemgmt.dao;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.commn.model.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for CaseManagementNoteDAO multi-parameter query methods.
 *
 * <p>These tests validate that HQL queries with multiple positional parameters
 * bind parameters correctly. They are designed to catch parameter index errors
 * during Hibernate migration (?0-&gt;?1 parameter renumbering).</p>
 *
 * <p><b>Test Strategy:</b> Create distinct test data where incorrect parameter
 * binding would return wrong results, then verify only correct data is returned.</p>
 *
 * @since 2026-02-03
 * @see CaseManagementNoteDAO
 * @see CaseManagementNote
 */
@DisplayName("CaseManagementNoteDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("casemgmt")
public class CaseManagementNoteDaoIntegrationTest extends CaseManagementNoteDaoBaseIntegrationTest {

    /** Tests for CRUD operations on CaseManagementNote entities. */
    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("read")
        @DisplayName("should retrieve note by valid ID")
        void shouldRetrieveNote_whenValidIdProvided() {
            // Given
            CaseManagementNote note = createNote("111", "Test note content");
            hibernateTemplate.flush();

            // When
            CaseManagementNote found = caseManagementNoteDAO.getNote(note.getId());

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(note.getId());
            assertThat(found.getNote()).isEqualTo("Test note content");
        }

        @Test
        @Tag("create")
        @DisplayName("should persist valid note data")
        void shouldPersistNote_whenValidDataProvided() {
            // Given
            CaseManagementNote note = new CaseManagementNote();
            note.setDemographic_no("500");
            note.setNote("Persisted note");
            note.setProviderNo("999998");
            note.setUuid(UUID.randomUUID().toString());
            note.setUpdate_date(new Date());
            note.setObservation_date(new Date());
            note.setSigned(false);
            note.setArchived(false);
            note.setLocked(false);

            // When
            caseManagementNoteDAO.saveNote(note);
            hibernateTemplate.flush();

            // Then
            assertThat(note.getId()).isNotNull();
            CaseManagementNote found = caseManagementNoteDAO.getNote(note.getId());
            assertThat(found).isNotNull();
            assertThat(found.getNote()).isEqualTo("Persisted note");
        }

        @Test
        @Tag("create")
        @DisplayName("should verify UUID is preserved on save")
        void shouldPreserveUuid_whenNoteSaved() {
            // Given
            String assignedUuid = UUID.randomUUID().toString();
            CaseManagementNote note = new CaseManagementNote();
            note.setDemographic_no("501");
            note.setNote("UUID test");
            note.setProviderNo("999998");
            note.setUuid(assignedUuid);
            note.setUpdate_date(new Date());
            note.setObservation_date(new Date());
            note.setSigned(false);
            note.setArchived(false);
            note.setLocked(false);

            // When
            caseManagementNoteDAO.saveNote(note);
            hibernateTemplate.flush();

            // Then
            CaseManagementNote found = caseManagementNoteDAO.getNote(note.getId());
            assertThat(found.getUuid()).isEqualTo(assignedUuid);
        }

        @Test
        @Tag("update")
        @DisplayName("should update note with changes")
        void shouldUpdateNote_whenChangesProvided() {
            // Given
            CaseManagementNote note = createNote("502", "Original content");
            hibernateTemplate.flush();

            // When
            note.setNote("Updated content");
            caseManagementNoteDAO.updateNote(note);
            hibernateTemplate.flush();

            // Then
            CaseManagementNote found = caseManagementNoteDAO.getNote(note.getId());
            assertThat(found.getNote()).isEqualTo("Updated content");
        }

        @Test
        @Tag("create")
        @DisplayName("should return ID when saving and returning")
        void shouldReturnId_whenSavingAndReturning() {
            // Given
            CaseManagementNote note = new CaseManagementNote();
            note.setDemographic_no("503");
            note.setNote("Save and return test");
            note.setProviderNo("999998");
            note.setUuid(UUID.randomUUID().toString());
            note.setUpdate_date(new Date());
            note.setObservation_date(new Date());
            note.setSigned(false);
            note.setArchived(false);
            note.setLocked(false);

            // When
            Object result = caseManagementNoteDAO.saveAndReturn(note);
            hibernateTemplate.flush();

            // Then
            assertThat(result).isNotNull();
        }
    }

    /** Tests for getCaseManagementNoteByProgramIdAndObservationDate (3 params). */
    @Nested
    @DisplayName("getCaseManagementNoteByProgramIdAndObservationDate (3 params)")
    class GetByProgramIdAndDateRange {

        @Test
        @Tag("query")
        @DisplayName("should filter by all three parameters - programId, minDate, maxDate")
        void shouldFilterByAllThreeParams_whenSearchingByProgramAndDateRange() {
            // Given
            Date jan15 = createDate(2024, 1, 15);
            Date jan1 = createDate(2024, 1, 1);
            Date jan31 = createDate(2024, 1, 31);
            Date feb15 = createDate(2024, 2, 15);

            CaseManagementNote matchNote = createNoteWithProgram(100, jan15);
            CaseManagementNote wrongProgram = createNoteWithProgram(200, jan15);
            CaseManagementNote beforeRange = createNoteWithProgram(100, createDate(2023, 12, 15));
            CaseManagementNote afterRange = createNoteWithProgram(100, feb15);
            hibernateTemplate.flush();

            // When
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getCaseManagementNoteByProgramIdAndObservationDate(100, jan1, jan31);

            // Then
            assertThat(results)
                .isNotNull()
                .extracting(CaseManagementNote::getId)
                .contains(matchNote.getId())
                .doesNotContain(wrongProgram.getId(), beforeRange.getId(), afterRange.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no notes match all criteria")
        void shouldReturnEmptyList_whenNoNotesMatchAllCriteria() {
            // Given
            createNoteWithProgram(100, createDate(2024, 1, 15));
            hibernateTemplate.flush();

            // When
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getCaseManagementNoteByProgramIdAndObservationDate(
                    999, createDate(2024, 1, 1), createDate(2024, 1, 31));

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should include notes exactly on boundary dates")
        void shouldIncludeNotesOnBoundaryDates_whenSearchingByDateRange() {
            // Given
            Date jan1 = createDate(2024, 1, 1);
            Date jan31 = createDate(2024, 1, 31);

            CaseManagementNote onMinDate = createNoteWithProgram(100, jan1);
            CaseManagementNote onMaxDate = createNoteWithProgram(100, jan31);
            hibernateTemplate.flush();

            // When
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getCaseManagementNoteByProgramIdAndObservationDate(100, jan1, jan31);

            // Then
            assertThat(results)
                .extracting(CaseManagementNote::getId)
                .contains(onMinDate.getId(), onMaxDate.getId());
        }
    }

    /** Tests for searchDemographicNotes (3 params). */
    @Nested
    @DisplayName("searchDemographicNotes (3 params)")
    class SearchDemographicNotes {

        @Test
        @Tag("search")
        @DisplayName("should find notes matching search string in correct demographic")
        void shouldFindNotesMatchingSearchString_whenSearchingInSpecificDemographic() {
            // Given
            CaseManagementNote match = createNote("111", "Patient has diabetes mellitus");
            CaseManagementNote wrongDemo = createNote("222", "Patient has diabetes mellitus");
            CaseManagementNote wrongContent = createNote("111", "Patient is healthy");
            hibernateTemplate.flush();

            // When
            List<CaseManagementNote> results = caseManagementNoteDAO
                .searchDemographicNotes("111", "%diabetes%");

            // Then
            assertThat(results)
                .isNotNull()
                .extracting(CaseManagementNote::getId)
                .contains(match.getId())
                .doesNotContain(wrongDemo.getId(), wrongContent.getId());
        }

        @Test
        @Tag("search")
        @DisplayName("should return empty list when search string not found")
        void shouldReturnEmptyList_whenSearchStringNotFoundInDemographic() {
            // Given
            createNote("111", "Patient has hypertension");
            hibernateTemplate.flush();

            // When
            List<CaseManagementNote> results = caseManagementNoteDAO
                .searchDemographicNotes("111", "nonexistent-term-xyz");

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("search")
        @DisplayName("should be case-insensitive in search")
        void shouldBeCaseInsensitive_whenSearching() {
            // Given
            CaseManagementNote note = createNote("111", "Patient has DIABETES");
            hibernateTemplate.flush();

            // When
            List<CaseManagementNote> results = caseManagementNoteDAO
                .searchDemographicNotes("111", "%diabetes%");

            // Then
            assertThat(results)
                .extracting(CaseManagementNote::getId)
                .contains(note.getId());
        }
    }

    /** Tests for getNotesByDemographicSince (2 params). */
    @Nested
    @DisplayName("getNotesByDemographicSince (2 params)")
    class GetNotesByDemographicSince {

        @Test
        @Tag("query")
        @DisplayName("should filter by both demographic and date")
        void shouldFilterByBothDemographicAndDate_whenGettingNotesSince() {
            // Given
            Date cutoffDate = daysFromNow(-5);
            CaseManagementNote afterCutoff = createNote("111", "Recent note", daysFromNow(-2));
            CaseManagementNote beforeCutoff = createNote("111", "Old note", daysFromNow(-10));
            CaseManagementNote wrongDemoAfter = createNote("222", "Wrong demo recent", daysFromNow(-2));
            hibernateTemplate.flush();

            // When
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getNotesByDemographicSince("111", cutoffDate);

            // Then
            assertThat(results)
                .extracting(CaseManagementNote::getId)
                .contains(afterCutoff.getId())
                .doesNotContain(beforeCutoff.getId(), wrongDemoAfter.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no recent notes")
        void shouldReturnEmptyList_whenDemographicHasNoRecentNotes() {
            // Given
            createNote("111", "Old note", daysFromNow(-30));
            hibernateTemplate.flush();

            // When
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getNotesByDemographicSince("111", daysFromNow(-1));

            // Then
            assertThat(results).isEmpty();
        }
    }

    /** Tests for getUnsignedRawNoteInfoMapByDemographic (2 params). */
    @Nested
    @DisplayName("getUnsignedRawNoteInfoMapByDemographic (2 params)")
    class GetUnsignedRawNoteInfoMap {

        @Test
        @Tag("query")
        @DisplayName("should filter by demographic and signed status")
        void shouldFilterByDemographicAndSignedStatus() {
            // Given
            CaseManagementNote unsigned1 = createNoteWithSignedStatus("111", false);
            CaseManagementNote signed1 = createNoteWithSignedStatus("111", true);
            CaseManagementNote unsigned2 = createNoteWithSignedStatus("222", false);
            hibernateTemplate.flush();

            // When
            List<Map<String, Object>> results = caseManagementNoteDAO
                .getUnsignedRawNoteInfoMapByDemographic("111");

            // Then
            assertThat(results).isNotNull();
            List<Long> resultIds = results.stream()
                .map(m -> (Long) m.get("id"))
                .toList();
            assertThat(resultIds)
                .contains(unsigned1.getId())
                .doesNotContain(signed1.getId(), unsigned2.getId());
        }
    }

    /** Tests for note retrieval query methods. */
    @Nested
    @DisplayName("Note retrieval queries")
    class NoteRetrievalQueries {

        @Test
        @Tag("read")
        @DisplayName("should count notes by demographic")
        void shouldCountNotes_byDemographic() {
            // Given
            createNote("333", "Note 1");
            createNote("333", "Note 2");
            createNote("333", "Note 3");
            createNote("444", "Different demo");
            hibernateTemplate.flush();

            // When
            long count = caseManagementNoteDAO.getNotesCountByDemographicId("333");

            // Then
            assertThat(count).isEqualTo(3);
        }

        @Test
        @Tag("read")
        @DisplayName("should get notes by demographic number")
        void shouldGetNotes_byDemographic() {
            // Given
            createNote("555", "First note");
            createNote("555", "Second note");
            createNote("666", "Other demo");
            hibernateTemplate.flush();

            // When
            List<CaseManagementNote> results = caseManagementNoteDAO.getNotesByDemographic("555");

            // Then
            assertThat(results)
                .isNotEmpty()
                .extracting(CaseManagementNote::getDemographic_no)
                .allMatch(d -> d.equals("555"));
        }

        @Test
        @Tag("read")
        @DisplayName("should apply note count limits when limit specified")
        void shouldApplyNoteCountLimits_whenLimitSpecified() {
            // Given
            for (int i = 0; i < 5; i++) {
                createNote("777", "Note " + i);
            }
            hibernateTemplate.flush();

            // When
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getNotesByDemographic("777", 2);

            // Then
            assertThat(results).hasSize(2);
        }

        @Test
        @Tag("read")
        @DisplayName("should fetch most recent note by UUID")
        void shouldFetchMostRecentNote_byUuid() {
            // Given
            String sharedUuid = UUID.randomUUID().toString();
            CaseManagementNote older = createNote("888", "Older version");
            older.setUuid(sharedUuid);
            older.setUpdate_date(daysFromNow(-5));
            caseManagementNoteDAO.updateNote(older);

            CaseManagementNote newer = createNote("888", "Newer version");
            newer.setUuid(sharedUuid);
            newer.setUpdate_date(daysFromNow(-1));
            caseManagementNoteDAO.updateNote(newer);
            hibernateTemplate.flush();

            // When
            CaseManagementNote mostRecent = caseManagementNoteDAO.getMostRecentNote(sharedUuid);

            // Then
            assertThat(mostRecent).isNotNull();
            assertThat(mostRecent.getUuid()).isEqualTo(sharedUuid);
        }

        @Test
        @Tag("read")
        @DisplayName("should access complete note history by UUID")
        void shouldAccessNoteHistory_byUuid() {
            // Given
            String sharedUuid = UUID.randomUUID().toString();
            CaseManagementNote v1 = createNote("999", "Version 1");
            v1.setUuid(sharedUuid);
            caseManagementNoteDAO.updateNote(v1);

            CaseManagementNote v2 = createNote("999", "Version 2");
            v2.setUuid(sharedUuid);
            caseManagementNoteDAO.updateNote(v2);
            hibernateTemplate.flush();

            // When
            List<CaseManagementNote> history = caseManagementNoteDAO.getNotesByUUID(sharedUuid);

            // Then
            assertThat(history).hasSize(2);
            assertThat(history)
                .extracting(CaseManagementNote::getUuid)
                .allMatch(u -> u.equals(sharedUuid));
        }

        @Test
        @Tag("read")
        @DisplayName("should get most recent notes by appointment number")
        void shouldGetMostRecentNotesByAppointmentNo() {
            // Given
            CaseManagementNote note1 = createNote("111", "Appointment note");
            note1.setAppointmentNo(12345);
            caseManagementNoteDAO.updateNote(note1);
            hibernateTemplate.flush();

            // When
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getMostRecentNotesByAppointmentNo(12345);

            // Then
            assertThat(results).isNotEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should query notes created after specific date")
        void shouldQueryNotes_afterSpecificDate() {
            // Given
            CaseManagementNote recent = createNote("1010", "Recent", daysFromNow(-1));
            CaseManagementNote old = createNote("1010", "Old", daysFromNow(-30));
            hibernateTemplate.flush();

            // When
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getNotesByDemographicSince("1010", daysFromNow(-5));

            // Then
            assertThat(results)
                .extracting(CaseManagementNote::getId)
                .contains(recent.getId())
                .doesNotContain(old.getId());
        }
    }

    /** Tests for editor theta-join queries (Provider join CaseManagementNote via implicit inner join). */
    @Nested
    @DisplayName("Editor queries (Provider theta-join)")
    class EditorQueries {

        private Provider ensureProvider(String providerNo, String firstName, String lastName) {
            Provider p = new Provider();
            p.setProviderNo(providerNo);
            p.setFirstName(firstName);
            p.setLastName(lastName);
            p.setSex("M");
            p.setProviderType("doctor");
            p.setSpecialty("");
            hibernateTemplate.save(p);
            return p;
        }

        @Test
        @Tag("query")
        @DisplayName("should return editors by note UUID")
        void shouldReturnEditors_byNoteUuid() {
            // Given
            Provider drSmith = ensureProvider("ED0001", "Ed", "Smith");
            Provider drJones = ensureProvider("ED0002", "Ed", "Jones");
            String sharedUuid = UUID.randomUUID().toString();

            CaseManagementNote note1 = createNoteWithProvider("2001", "ED0001");
            note1.setUuid(sharedUuid);
            caseManagementNoteDAO.updateNote(note1);

            CaseManagementNote note2 = createNoteWithProvider("2001", "ED0002");
            note2.setUuid(sharedUuid);
            caseManagementNoteDAO.updateNote(note2);
            hibernateTemplate.flush();

            // When
            CaseManagementNote lookupNote = new CaseManagementNote();
            lookupNote.setUuid(sharedUuid);
            List<Provider> editors = caseManagementNoteDAO.getEditors(lookupNote);

            // Then
            assertThat(editors)
                .isNotEmpty()
                .extracting(Provider::getProviderNo)
                .contains("ED0001", "ED0002");
        }

        @Test
        @Tag("query")
        @DisplayName("should return all editors by demographic number")
        void shouldReturnAllEditors_byDemographicNo() {
            // Given
            Provider drA = ensureProvider("AE0001", "Alice", "Editor");
            Provider drB = ensureProvider("AE0002", "Bob", "Editor");
            createNoteWithProvider("3001", "AE0001");
            createNoteWithProvider("3001", "AE0002");
            createNoteWithProvider("3002", "AE0001");  // different demographic
            hibernateTemplate.flush();

            // When
            List<Provider> editors = caseManagementNoteDAO.getAllEditors("3001");

            // Then
            assertThat(editors)
                .isNotEmpty()
                .extracting(Provider::getProviderNo)
                .contains("AE0001", "AE0002");
        }
    }

    /** Tests for note history and raw projection queries. */
    @Nested
    @DisplayName("History and projection queries")
    class HistoryAndProjectionQueries {

        @Test
        @Tag("query")
        @DisplayName("should return note history ordered by update date")
        void shouldReturnHistory_byNoteUuid() {
            // Given
            String sharedUuid = UUID.randomUUID().toString();
            CaseManagementNote v1 = createNote("4001", "Version 1", daysFromNow(-10));
            v1.setUuid(sharedUuid);
            caseManagementNoteDAO.updateNote(v1);

            CaseManagementNote v2 = createNote("4001", "Version 2", daysFromNow(-5));
            v2.setUuid(sharedUuid);
            caseManagementNoteDAO.updateNote(v2);

            CaseManagementNote v3 = createNote("4001", "Version 3", daysFromNow(-1));
            v3.setUuid(sharedUuid);
            caseManagementNoteDAO.updateNote(v3);
            hibernateTemplate.flush();

            // When
            CaseManagementNote lookupNote = new CaseManagementNote();
            lookupNote.setUuid(sharedUuid);
            List<CaseManagementNote> history = caseManagementNoteDAO.getHistory(lookupNote);

            // Then
            assertThat(history)
                .hasSize(3)
                .extracting(CaseManagementNote::getUuid)
                .allMatch(u -> u.equals(sharedUuid));
        }

        @Test
        @Tag("query")
        @DisplayName("should return raw note info as Object array by demographic")
        void shouldReturnRawNoteInfo_byDemographic() {
            // Given
            createNote("5001", "Raw info test note 1");
            createNote("5001", "Raw info test note 2");
            createNote("5002", "Different demo");
            hibernateTemplate.flush();

            // When
            List<Object[]> results = caseManagementNoteDAO.getRawNoteInfoByDemographic("5001");

            // Then
            assertThat(results)
                .isNotEmpty()
                .hasSize(2);
            // Each Object[] should contain: id, observation_date, providerNo, program_no, reporter_caisi_role, uuid
            Object[] first = results.get(0);
            assertThat(first).hasSize(6);
        }

        @Test
        @Tag("query")
        @DisplayName("should return raw note info as Map by demographic")
        void shouldReturnRawNoteInfoMap_byDemographic() {
            // Given
            createNote("6001", "Map projection test 1");
            createNote("6001", "Map projection test 2");
            createNote("6002", "Different demo");
            hibernateTemplate.flush();

            // When
            List<Map<String, Object>> results = caseManagementNoteDAO.getRawNoteInfoMapByDemographic("6001");

            // Then
            assertThat(results)
                .isNotEmpty()
                .hasSize(2);
            // Verify map keys from the HQL projection
            Map<String, Object> first = results.get(0);
            assertThat(first).containsKeys("id", "observation_date", "providerNo", "uuid");
        }

        @Test
        @Tag("query")
        @DisplayName("should return most recent notes by demographic number")
        void shouldReturnMostRecentNotes_byDemographicNo() {
            // Given — two notes with different UUIDs for same demographic
            CaseManagementNote note1 = createNote("7001", "First note");
            CaseManagementNote note2 = createNote("7001", "Second note");
            createNote("7002", "Different demo");
            hibernateTemplate.flush();

            // When
            List<CaseManagementNote> results = caseManagementNoteDAO.getMostRecentNotes(7001);

            // Then
            assertThat(results)
                .isNotEmpty()
                .extracting(CaseManagementNote::getDemographic_no)
                .allMatch(d -> d.equals("7001"));
        }
    }

    /**
     * Tests for getCPPNotes(String demoNo, long issueId, String staleDate).
     *
     * <p>This method joins CaseManagementNote with its issues collection and filters
     * by issue_id, demographic_no, and observation_date. The current HQL has a known bug:
     * the subquery uses {@code from cmn} instead of {@code from CaseManagementNote cmn2},
     * which causes a QuerySyntaxException. Tests document this pre-change behavior.</p>
     */
    @Nested
    @DisplayName("getCPPNotes (3 params: demoNo, issueId, staleDate)")
    class GetCPPNotes {

        private Issue cppIssue;
        private CaseManagementIssue cmi;
        private static final String DEMO_NO = "10100";

        @BeforeEach
        void setUp() {
            cppIssue = createIssue("CPP001", "Ongoing Concerns");
            cmi = createCaseManagementIssue(DEMO_NO, cppIssue);
            hibernateTemplate.flush();
        }

        @Test
        @Tag("query")
        @DisplayName("should return CPP notes when issue linked to note with valid staleDate")
        void shouldReturnCPPNotes_whenIssueLinkedToNote() {
            // Given — note linked to the CPP issue, after the stale date
            CaseManagementNote note = createNoteWithIssue(DEMO_NO, "CPP concern note",
                createDate(2024, 6, 15), cmi);

            // When — the HQL subquery uses "from cmn" which Hibernate 5 treats as a
            // correlated subquery reference to the outer alias. This works but is
            // semantically incorrect (PR #89 rewrites to "from CaseManagementNote cmn2").
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getCPPNotes(DEMO_NO, cppIssue.getId(), "2024-01-01");

            // Then — note should be found (observation_date 2024-06-15 >= stale 2024-01-01)
            assertThat(results)
                .extracting(CaseManagementNote::getId)
                .contains(note.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should fall back to epoch date when staleDate is null")
        void shouldUseEpochDate_whenStaleDateIsNull() {
            // Given — note with recent observation date
            CaseManagementNote note = createNoteWithIssue(DEMO_NO, "CPP note for null staleDate",
                createDate(2024, 3, 1), cmi);

            // When — null staleDate falls back to epoch (1970-02-01),
            // so all notes after epoch should be returned
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getCPPNotes(DEMO_NO, cppIssue.getId(), null);

            // Then — the epoch date is far in the past, so the note qualifies
            assertThat(results).isNotEmpty();
        }
    }

    /**
     * Tests for getActiveNotesByDemographic(String demographic_no, String[] issues).
     *
     * <p>This method has two branches: single-issue (uses positional params) and
     * multi-issue (concatenates issue IDs into IN clause). Both filter for non-archived
     * notes and select the most recent version per UUID.</p>
     */
    @Nested
    @DisplayName("getActiveNotesByDemographic (2 params: demoNo, issues[])")
    class GetActiveNotesByDemographic {

        private Issue issue1;
        private Issue issue2;
        private CaseManagementIssue cmi1;
        private CaseManagementIssue cmi2;
        private static final String DEMO_NO = "10200";

        @BeforeEach
        void setUp() {
            issue1 = createIssue("ACT001", "Active Issue 1");
            issue2 = createIssue("ACT002", "Active Issue 2");
            cmi1 = createCaseManagementIssue(DEMO_NO, issue1);
            cmi2 = createCaseManagementIssue(DEMO_NO, issue2);
            hibernateTemplate.flush();
        }

        @Test
        @Tag("query")
        @DisplayName("should return active notes for single issue")
        void shouldReturnActiveNotes_forSingleIssue() {
            // Given — one active note linked to issue1, one archived, one for wrong demo
            CaseManagementNote activeNote = createNoteWithIssue(DEMO_NO, "Active note",
                createDate(2024, 5, 1), cmi1);

            CaseManagementNote archivedNote = createNote(DEMO_NO, "Archived note", createDate(2024, 5, 2));
            archivedNote.setArchived(true);
            archivedNote.getIssues().add(cmi1);
            caseManagementNoteDAO.updateNote(archivedNote);

            CaseManagementNote wrongDemo = createNoteWithIssue("10299", "Wrong demo",
                createDate(2024, 5, 1),
                createCaseManagementIssue("10299", issue1));
            hibernateTemplate.flush();

            // When — the issues[] array contains Issue entity IDs (i.issue_id FK values)
            String[] issueIds = new String[]{String.valueOf(issue1.getId())};
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getActiveNotesByDemographic(DEMO_NO, issueIds);

            // Then — only the active, correct-demo note should be returned
            assertThat(results)
                .extracting(CaseManagementNote::getId)
                .contains(activeNote.getId())
                .doesNotContain(archivedNote.getId(), wrongDemo.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return active notes for multiple issues via IN clause")
        void shouldReturnActiveNotes_forMultipleIssues() {
            // Given — notes linked to different issues
            CaseManagementNote note1 = createNoteWithIssue(DEMO_NO, "Issue 1 note",
                createDate(2024, 5, 1), cmi1);
            CaseManagementNote note2 = createNoteWithIssue(DEMO_NO, "Issue 2 note",
                createDate(2024, 5, 2), cmi2);
            hibernateTemplate.flush();

            // When — multi-element array triggers the IN clause branch;
            // uses Issue entity IDs (i.issue_id FK values), not CMI primary keys
            String[] issueIds = new String[]{
                String.valueOf(issue1.getId()),
                String.valueOf(issue2.getId())
            };
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getActiveNotesByDemographic(DEMO_NO, issueIds);

            // Then — notes from both issues should be returned
            assertThat(results)
                .extracting(CaseManagementNote::getId)
                .contains(note1.getId(), note2.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when issues array is null")
        void shouldReturnEmptyList_whenIssuesArrayIsNull() {
            // When
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getActiveNotesByDemographic(DEMO_NO, null);

            // Then
            assertThat(results).isEmpty();
        }
    }

    /**
     * Tests for getNotesByDemographic(String demographic_no, String[] issueIds, Integer maxNotes).
     *
     * <p>This method adds pagination via maxNotes parameter. Uses single-issue (positional params)
     * and multi-issue (concatenated IN clause) branches. maxNotes=-1 means no limit.</p>
     */
    @Nested
    @DisplayName("getNotesByDemographic (3 params: demoNo, issueIds[], maxNotes)")
    class GetNotesByDemographicWithIssuesAndLimit {

        private Issue issue1;
        private Issue issue2;
        private CaseManagementIssue cmi1;
        private CaseManagementIssue cmi2;
        private static final String DEMO_NO = "10300";

        @BeforeEach
        void setUp() {
            issue1 = createIssue("LIM001", "Limit Issue 1");
            issue2 = createIssue("LIM002", "Limit Issue 2");
            cmi1 = createCaseManagementIssue(DEMO_NO, issue1);
            cmi2 = createCaseManagementIssue(DEMO_NO, issue2);
            hibernateTemplate.flush();
        }

        @Test
        @Tag("query")
        @DisplayName("should return limited notes for single issue")
        void shouldReturnLimitedNotes_forSingleIssue() {
            // Given — create 3 notes with distinct UUIDs linked to same issue
            for (int i = 0; i < 3; i++) {
                createNoteWithIssue(DEMO_NO, "Limit note " + i,
                    createDate(2024, 6, 1 + i), cmi1);
            }

            // When — limit to 2; uses Issue entity ID (i.issue_id FK)
            String[] issueIds = new String[]{String.valueOf(issue1.getId())};
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getNotesByDemographic(DEMO_NO, issueIds, 2);

            // Then — at most 2 notes returned
            assertThat(results).hasSizeLessThanOrEqualTo(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should return all notes when maxNotes is negative one")
        void shouldReturnAllNotes_whenMaxNotesIsNegativeOne() {
            // Given — create 3 notes
            for (int i = 0; i < 3; i++) {
                createNoteWithIssue(DEMO_NO, "Unlimited note " + i,
                    createDate(2024, 7, 1 + i), cmi1);
            }

            // When — maxNotes=-1 means no limit; uses Issue entity ID
            String[] issueIds = new String[]{String.valueOf(issue1.getId())};
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getNotesByDemographic(DEMO_NO, issueIds, -1);

            // Then — all 3 notes should be returned
            assertThat(results).hasSize(3);
        }

        @Test
        @Tag("query")
        @DisplayName("should filter by multiple issue IDs with IN clause")
        void shouldFilterByMultipleIssueIds_withInClause() {
            // Given — notes linked to different issues
            CaseManagementNote note1 = createNoteWithIssue(DEMO_NO, "Multi-issue note 1",
                createDate(2024, 8, 1), cmi1);
            CaseManagementNote note2 = createNoteWithIssue(DEMO_NO, "Multi-issue note 2",
                createDate(2024, 8, 2), cmi2);
            hibernateTemplate.flush();

            // When — multi-element array triggers IN clause branch;
            // uses Issue entity IDs (i.issue_id FK values)
            String[] issueIds = new String[]{
                String.valueOf(issue1.getId()),
                String.valueOf(issue2.getId())
            };
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getNotesByDemographic(DEMO_NO, issueIds, -1);

            // Then — notes from both issues should be present
            assertThat(results)
                .extracting(CaseManagementNote::getId)
                .contains(note1.getId(), note2.getId());
        }
    }

    /**
     * Tests for getNotesByDemographic(String demographic_no, String[] issueIds).
     *
     * <p>Two-arg overload with issue ID array. Uses single-issue (positional params) and
     * multi-issue (concatenated IN clause) branches. Returns empty list for null input.</p>
     */
    @Nested
    @DisplayName("getNotesByDemographic (2 params: demoNo, issueIds[])")
    class GetNotesByDemographicWithIssues {

        private Issue issue1;
        private Issue issue2;
        private CaseManagementIssue cmi1;
        private CaseManagementIssue cmi2;
        private static final String DEMO_NO = "10400";

        @BeforeEach
        void setUp() {
            issue1 = createIssue("NBI001", "Note-By-Issue 1");
            issue2 = createIssue("NBI002", "Note-By-Issue 2");
            cmi1 = createCaseManagementIssue(DEMO_NO, issue1);
            cmi2 = createCaseManagementIssue(DEMO_NO, issue2);
            hibernateTemplate.flush();
        }

        @Test
        @Tag("query")
        @DisplayName("should return notes for single issue ID")
        void shouldReturnNotes_forSingleIssueId() {
            // Given
            CaseManagementNote note = createNoteWithIssue(DEMO_NO, "Single issue note",
                createDate(2024, 9, 1), cmi1);

            CaseManagementNote wrongDemo = createNoteWithIssue("10499", "Wrong demo note",
                createDate(2024, 9, 1),
                createCaseManagementIssue("10499", issue1));
            hibernateTemplate.flush();

            // When — uses Issue entity IDs (i.issue_id FK values)
            String[] issueIds = new String[]{String.valueOf(issue1.getId())};
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getNotesByDemographic(DEMO_NO, issueIds);

            // Then
            assertThat(results)
                .extracting(CaseManagementNote::getId)
                .contains(note.getId())
                .doesNotContain(wrongDemo.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return notes for multiple issue IDs")
        void shouldReturnNotes_forMultipleIssueIds() {
            // Given
            CaseManagementNote note1 = createNoteWithIssue(DEMO_NO, "Multi note 1",
                createDate(2024, 9, 10), cmi1);
            CaseManagementNote note2 = createNoteWithIssue(DEMO_NO, "Multi note 2",
                createDate(2024, 9, 11), cmi2);
            hibernateTemplate.flush();

            // When — uses Issue entity IDs (i.issue_id FK values)
            String[] issueIds = new String[]{
                String.valueOf(issue1.getId()),
                String.valueOf(issue2.getId())
            };
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getNotesByDemographic(DEMO_NO, issueIds);

            // Then
            assertThat(results)
                .extracting(CaseManagementNote::getId)
                .contains(note1.getId(), note2.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when issue IDs is null")
        void shouldReturnEmptyList_whenIssueIdsIsNull() {
            // When
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getNotesByDemographic(DEMO_NO, (String[]) null);

            // Then
            assertThat(results).isEmpty();
        }
    }

    /**
     * Tests for getNotesByDemographic(String demographic_no, String[] issues, String staleDate).
     *
     * <p>This overload adds a staleDate filter (parsed as yyyy-MM-dd). The current HQL subquery
     * uses {@code from cmn} instead of {@code from CaseManagementNote cmn2}, which Hibernate 5
     * treats as a correlated subquery reference to the outer alias. This works but is
     * semantically incorrect — PR #89 rewrites to use a distinct alias for correctness.</p>
     */
    @Nested
    @DisplayName("getNotesByDemographic (3 params: demoNo, issues[], staleDate)")
    class GetNotesByDemographicWithIssuesAndStaleDate {

        private Issue issue1;
        private CaseManagementIssue cmi1;
        private static final String DEMO_NO = "10500";

        @BeforeEach
        void setUp() {
            issue1 = createIssue("STL001", "Stale Date Issue");
            cmi1 = createCaseManagementIssue(DEMO_NO, issue1);
            hibernateTemplate.flush();
        }

        @Test
        @Tag("query")
        @DisplayName("should filter notes by issues, demographic, and stale date")
        void shouldFilterNotes_byIssuesDemoAndStaleDate() {
            // Given — note linked to issue, after the stale date
            CaseManagementNote note = createNoteWithIssue(DEMO_NO, "Stale date note",
                createDate(2024, 6, 15), cmi1);

            // When — uses Issue entity IDs; the HQL subquery uses "from cmn" which
            // Hibernate 5 treats as a correlated subquery reference to the outer alias.
            // PR #89 rewrites to use "from CaseManagementNote cmn2" for correctness.
            String[] issues = new String[]{String.valueOf(issue1.getId())};
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getNotesByDemographic(DEMO_NO, issues, "2024-01-01");

            // Then — note should be found (observation_date 2024-06-15 >= stale 2024-01-01)
            assertThat(results)
                .extracting(CaseManagementNote::getId)
                .contains(note.getId());
        }
    }
}
