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

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for CaseManagementNoteExtDAO multi-parameter query methods.
 *
 * <p>These tests validate that HQL queries with multiple positional parameters
 * bind parameters correctly.</p>
 *
 * @since 2026-02-03
 * @see CaseManagementNoteExtDAO
 */
@DisplayName("CaseManagementNoteExtDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("casemgmt")
@Transactional
public class CaseManagementNoteExtDAOIntegrationTest extends CarlosTestBase {

    @Autowired
    @Qualifier("CaseManagementNoteExtDAO")
    private CaseManagementNoteExtDAO caseManagementNoteExtDAO;

    @Autowired
    @Qualifier("CaseManagementNoteDAO")
    private CaseManagementNoteDAO caseManagementNoteDAO;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /** Parent note IDs to satisfy FK constraint on casemgmt_note_ext.note_id. */
    private Long parentNoteId1;
    private Long parentNoteId2;

    @BeforeEach
    void setUp() {
        // Create parent CaseManagementNote records to satisfy FK constraint
        parentNoteId1 = createParentNote().getId();
        parentNoteId2 = createParentNote().getId();
        hibernateTemplate.flush();
    }

    private CaseManagementNote createParentNote() {
        CaseManagementNote note = new CaseManagementNote();
        note.setDemographic_no("1");
        note.setNote("Parent note for ext tests");
        note.setProviderNo("999998");
        note.setUuid(UUID.randomUUID().toString());
        note.setUpdate_date(new Date());
        note.setObservation_date(new Date());
        note.setSigned(false);
        note.setArchived(false);
        note.setLocked(false);
        caseManagementNoteDAO.saveNote(note);
        return note;
    }

    private CaseManagementNoteExt createNoteExt(Long noteId, String keyVal, String value) {
        CaseManagementNoteExt ext = new CaseManagementNoteExt();
        ext.setNoteId(noteId);
        ext.setKeyVal(keyVal);
        ext.setValue(value);
        caseManagementNoteExtDAO.save(ext);
        return ext;
    }

    private CaseManagementNoteExt createNoteExtWithDate(Long noteId, String keyVal, Date dateValue) {
        CaseManagementNoteExt ext = new CaseManagementNoteExt();
        ext.setNoteId(noteId);
        ext.setKeyVal(keyVal);
        ext.setDateValue(dateValue);
        caseManagementNoteExtDAO.save(ext);
        return ext;
    }

    private Date createDate(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, day, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    @Nested
    @DisplayName("getExtByValue (2 params: keyVal, value)")
    class GetExtByValue {

        @Test
        @Tag("query")
        @DisplayName("should find ext when both keyVal and value match")
        void shouldFindExt_whenBothKeyValAndValueMatch() {
            // Given
            CaseManagementNoteExt match = createNoteExt(parentNoteId1,"status", "active");
            CaseManagementNoteExt wrongKey = createNoteExt(parentNoteId1,"type", "active");     // Different key
            CaseManagementNoteExt wrongValue = createNoteExt(parentNoteId1,"status", "inactive"); // Different value
            hibernateTemplate.flush();

            // When
            List results = caseManagementNoteExtDAO.getExtByValue("status", "active");

            // Then
            assertThat(results)
                .isNotEmpty()
                .hasSize(1);
            CaseManagementNoteExt found = (CaseManagementNoteExt) results.get(0);
            assertThat(found.getId()).isEqualTo(match.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should support partial value matching with like")
        void shouldSupportPartialValueMatching() {
            // Given
            createNoteExt(parentNoteId1,"diagnosis", "diabetes mellitus type 1");
            createNoteExt(parentNoteId1,"diagnosis", "diabetes mellitus type 2");
            createNoteExt(parentNoteId1,"diagnosis", "hypertension");
            hibernateTemplate.flush();

            // When - Search with partial match
            List results = caseManagementNoteExtDAO.getExtByValue("diagnosis", "diabetes%");

            // Then
            assertThat(results).hasSize(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when no matches")
        void shouldReturnEmpty_whenNoMatches() {
            // Given
            createNoteExt(parentNoteId1,"status", "active");
            hibernateTemplate.flush();

            // When
            List results = caseManagementNoteExtDAO.getExtByValue("nonexistent", "value");

            // Then
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("getExtBeforeDate (2 params: keyVal, dateValue)")
    class GetExtBeforeDate {

        @Test
        @Tag("query")
        @DisplayName("should find ext when keyVal matches and dateValue is before cutoff")
        void shouldFindExt_whenKeyMatchesAndDateBeforeCutoff() {
            // Given
            Date jan15 = createDate(2024, 1, 15);
            Date jan10 = createDate(2024, 1, 10);
            Date jan20 = createDate(2024, 1, 20);

            CaseManagementNoteExt beforeDate = createNoteExtWithDate(parentNoteId1, "appointment", jan10);
            CaseManagementNoteExt afterDate = createNoteExtWithDate(parentNoteId1, "appointment", jan20);
            CaseManagementNoteExt wrongKey = createNoteExtWithDate(parentNoteId1, "other", jan10);
            hibernateTemplate.flush();

            // When
            List results = caseManagementNoteExtDAO.getExtBeforeDate("appointment", jan15);

            // Then - Only beforeDate should match
            assertThat(results)
                .hasSize(1);
            CaseManagementNoteExt found = (CaseManagementNoteExt) results.get(0);
            assertThat(found.getId()).isEqualTo(beforeDate.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should include dates exactly on cutoff")
        void shouldIncludeDatesOnCutoff() {
            // Given
            Date cutoff = createDate(2024, 1, 15);
            CaseManagementNoteExt onCutoff = createNoteExtWithDate(parentNoteId1, "due", cutoff);
            hibernateTemplate.flush();

            // When
            List results = caseManagementNoteExtDAO.getExtBeforeDate("due", cutoff);

            // Then
            assertThat(results).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getExtAfterDate (2 params: keyVal, dateValue)")
    class GetExtAfterDate {

        @Test
        @Tag("query")
        @DisplayName("should find ext when keyVal matches and dateValue is after cutoff")
        void shouldFindExt_whenKeyMatchesAndDateAfterCutoff() {
            // Given
            Date jan15 = createDate(2024, 1, 15);
            Date jan10 = createDate(2024, 1, 10);
            Date jan20 = createDate(2024, 1, 20);

            CaseManagementNoteExt beforeDate = createNoteExtWithDate(parentNoteId1, "followup", jan10);
            CaseManagementNoteExt afterDate = createNoteExtWithDate(parentNoteId1, "followup", jan20);
            CaseManagementNoteExt wrongKey = createNoteExtWithDate(parentNoteId1, "other", jan20);
            hibernateTemplate.flush();

            // When
            List results = caseManagementNoteExtDAO.getExtAfterDate("followup", jan15);

            // Then - Only afterDate should match
            assertThat(results)
                .hasSize(1);
            CaseManagementNoteExt found = (CaseManagementNoteExt) results.get(0);
            assertThat(found.getId()).isEqualTo(afterDate.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when no dates after cutoff for key")
        void shouldReturnEmpty_whenNoDatesAfterCutoff() {
            // Given
            Date jan10 = createDate(2024, 1, 10);
            Date jan20 = createDate(2024, 1, 20);
            createNoteExtWithDate(parentNoteId1, "reminder", jan10);
            hibernateTemplate.flush();

            // When - Search for dates after jan20
            List results = caseManagementNoteExtDAO.getExtAfterDate("reminder", jan20);

            // Then
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("Single parameter queries (baseline)")
    class SingleParamQueries {

        @Test
        @Tag("read")
        @DisplayName("should get extensions by note ID")
        void shouldGetExtByNoteId() {
            // Given
            createNoteExt(parentNoteId1, "key1", "value1");
            createNoteExt(parentNoteId1, "key2", "value2");
            createNoteExt(parentNoteId2, "key1", "value1");  // Different note
            hibernateTemplate.flush();

            // When
            List<CaseManagementNoteExt> results = caseManagementNoteExtDAO.getExtByNote(parentNoteId1);

            // Then
            assertThat(results)
                .hasSize(2)
                .allMatch(e -> e.getNoteId().equals(parentNoteId1));
        }

        @Test
        @Tag("read")
        @DisplayName("should get extensions by key value")
        void shouldGetExtByKeyVal() {
            // Given
            createNoteExt(parentNoteId1,"status", "active");
            createNoteExt(parentNoteId1, "status", "inactive");
            createNoteExt(parentNoteId1, "type", "active");  // Different key
            hibernateTemplate.flush();

            // When
            List results = caseManagementNoteExtDAO.getExtByKeyVal("status");

            // Then
            assertThat(results).hasSize(2);
        }
    }
}
