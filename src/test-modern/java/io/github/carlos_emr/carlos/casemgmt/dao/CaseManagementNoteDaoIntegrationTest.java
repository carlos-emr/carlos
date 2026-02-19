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

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
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
            assertThat(history).hasSizeGreaterThanOrEqualTo(2);
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
}
