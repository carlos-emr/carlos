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

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for CaseManagementNoteDAO multi-parameter query methods.
 *
 * <p>These tests validate that HQL queries with multiple positional parameters
 * bind parameters correctly. They are designed to catch parameter index errors
 * during Hibernate migration (?0→?1 parameter renumbering).</p>
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

    @Nested
    @DisplayName("getCaseManagementNoteByProgramIdAndObservationDate (3 params)")
    class GetByProgramIdAndDateRange {

        @Test
        @Tag("query")
        @DisplayName("should filter by all three parameters - programId, minDate, maxDate")
        void shouldFilterByAllThreeParams_whenSearchingByProgramAndDateRange() {
            // Given - Create notes with different programs and dates
            Date jan15 = createDate(2024, 1, 15);
            Date jan1 = createDate(2024, 1, 1);
            Date jan31 = createDate(2024, 1, 31);
            Date feb15 = createDate(2024, 2, 15);

            CaseManagementNote matchNote = createNoteWithProgram(100, jan15);       // Should match
            CaseManagementNote wrongProgram = createNoteWithProgram(200, jan15);    // Wrong program
            CaseManagementNote beforeRange = createNoteWithProgram(100, createDate(2023, 12, 15)); // Before range
            CaseManagementNote afterRange = createNoteWithProgram(100, feb15);      // After range
            entityManager.flush();

            // When
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getCaseManagementNoteByProgramIdAndObservationDate(100, jan1, jan31);

            // Then - Only matchNote should be returned
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
            // Given - Create note that matches some but not all criteria
            Date jan15 = createDate(2024, 1, 15);
            createNoteWithProgram(100, jan15);  // Program 100, Jan 15
            entityManager.flush();

            // When - Search for different program
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getCaseManagementNoteByProgramIdAndObservationDate(
                    999,  // Different program
                    createDate(2024, 1, 1),
                    createDate(2024, 1, 31));

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

            CaseManagementNote onMinDate = createNoteWithProgram(100, jan1);   // Exactly on min date
            CaseManagementNote onMaxDate = createNoteWithProgram(100, jan31);  // Exactly on max date
            entityManager.flush();

            // When
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getCaseManagementNoteByProgramIdAndObservationDate(100, jan1, jan31);

            // Then - Both boundary notes should be included
            assertThat(results)
                .extracting(CaseManagementNote::getId)
                .contains(onMinDate.getId(), onMaxDate.getId());
        }
    }

    @Nested
    @DisplayName("searchDemographicNotes (3 params: demographicNo, demographicNo, searchString)")
    class SearchDemographicNotes {

        @Test
        @Tag("search")
        @DisplayName("should find notes matching search string in correct demographic")
        void shouldFindNotesMatchingSearchString_whenSearchingInSpecificDemographic() {
            // Given - Create notes with different demographics and content
            CaseManagementNote match = createNote("111", "Patient has diabetes mellitus");
            CaseManagementNote wrongDemo = createNote("222", "Patient has diabetes mellitus");  // Same content, different demo
            CaseManagementNote wrongContent = createNote("111", "Patient is healthy");  // Same demo, different content
            entityManager.flush();

            // When
            List<CaseManagementNote> results = caseManagementNoteDAO
                .searchDemographicNotes("111", "diabetes");

            // Then - Only match should be returned
            assertThat(results)
                .isNotNull()
                .extracting(CaseManagementNote::getId)
                .contains(match.getId())
                .doesNotContain(wrongDemo.getId(), wrongContent.getId());
        }

        @Test
        @Tag("search")
        @DisplayName("should return empty list when search string not found in demographic")
        void shouldReturnEmptyList_whenSearchStringNotFoundInDemographic() {
            // Given
            createNote("111", "Patient has hypertension");
            entityManager.flush();

            // When - Search for term that doesn't exist
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
            entityManager.flush();

            // When - Search with different case
            List<CaseManagementNote> results = caseManagementNoteDAO
                .searchDemographicNotes("111", "diabetes");

            // Then
            assertThat(results)
                .extracting(CaseManagementNote::getId)
                .contains(note.getId());
        }
    }

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
            entityManager.flush();

            // When
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getNotesByDemographicSince("111", cutoffDate);

            // Then - Only afterCutoff should match both criteria
            assertThat(results)
                .extracting(CaseManagementNote::getId)
                .contains(afterCutoff.getId())
                .doesNotContain(beforeCutoff.getId(), wrongDemoAfter.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when demographic has no recent notes")
        void shouldReturnEmptyList_whenDemographicHasNoRecentNotes() {
            // Given - Create old note only
            createNote("111", "Old note", daysFromNow(-30));
            entityManager.flush();

            // When - Search for notes since yesterday
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getNotesByDemographicSince("111", daysFromNow(-1));

            // Then
            assertThat(results).isEmpty();
        }
    }

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
            CaseManagementNote unsigned2 = createNoteWithSignedStatus("222", false);  // Different demo
            entityManager.flush();

            // When
            List<Map<String, Object>> results = caseManagementNoteDAO
                .getUnsignedRawNoteInfoMapByDemographic("111");

            // Then - Only unsigned notes for demo 111 should be returned
            assertThat(results).isNotNull();
            // Results should contain unsigned1's id but not signed1's or unsigned2's
            List<Long> resultIds = results.stream()
                .map(m -> (Long) m.get("id"))
                .toList();
            assertThat(resultIds)
                .contains(unsigned1.getId())
                .doesNotContain(signed1.getId(), unsigned2.getId());
        }
    }

    @Nested
    @DisplayName("Single parameter queries (baseline coverage)")
    class SingleParamQueries {

        @Test
        @Tag("read")
        @DisplayName("should find note by ID")
        void shouldFindNoteById() {
            // Given
            CaseManagementNote note = createNote("111", "Test note content");
            entityManager.flush();

            // When
            CaseManagementNote found = caseManagementNoteDAO.getNote(note.getId());

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(note.getId());
            assertThat(found.getNote()).isEqualTo("Test note content");
        }

        @Test
        @Tag("read")
        @DisplayName("should count notes by demographic")
        void shouldCountNotesByDemographic() {
            // Given
            createNote("333", "Note 1");
            createNote("333", "Note 2");
            createNote("333", "Note 3");
            createNote("444", "Different demo");  // Different demographic
            entityManager.flush();

            // When
            long count = caseManagementNoteDAO.getNotesCountByDemographicId("333");

            // Then
            assertThat(count).isEqualTo(3);
        }

        @Test
        @Tag("read")
        @DisplayName("should get most recent notes by appointment number")
        void shouldGetMostRecentNotesByAppointmentNo() {
            // Given
            CaseManagementNote note1 = createNote("111", "Appointment note");
            note1.setAppointmentNo(12345);
            caseManagementNoteDAO.updateNote(note1);
            entityManager.flush();

            // When
            List<CaseManagementNote> results = caseManagementNoteDAO
                .getMostRecentNotesByAppointmentNo(12345);

            // Then
            assertThat(results).isNotEmpty();
        }
    }
}
