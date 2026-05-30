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

import io.github.carlos_emr.carlos.test.base.OpenOTestBase;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteExt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

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
public class CaseManagementNoteExtDAOIntegrationTest extends OpenOTestBase {

    @Autowired
    @Qualifier("CaseManagementNoteExtDAO")
    private CaseManagementNoteExtDAO caseManagementNoteExtDAO;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

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
            CaseManagementNoteExt match = createNoteExt(1001L, "status", "active");
            CaseManagementNoteExt wrongKey = createNoteExt(1002L, "type", "active");     // Different key
            CaseManagementNoteExt wrongValue = createNoteExt(1003L, "status", "inactive"); // Different value
            entityManager.flush();

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
            createNoteExt(1001L, "diagnosis", "diabetes mellitus type 1");
            createNoteExt(1002L, "diagnosis", "diabetes mellitus type 2");
            createNoteExt(1003L, "diagnosis", "hypertension");
            entityManager.flush();

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
            createNoteExt(1001L, "status", "active");
            entityManager.flush();

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

            CaseManagementNoteExt beforeDate = createNoteExtWithDate(1001L, "appointment", jan10);
            CaseManagementNoteExt afterDate = createNoteExtWithDate(1002L, "appointment", jan20);
            CaseManagementNoteExt wrongKey = createNoteExtWithDate(1003L, "other", jan10);
            entityManager.flush();

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
            CaseManagementNoteExt onCutoff = createNoteExtWithDate(1001L, "due", cutoff);
            entityManager.flush();

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

            CaseManagementNoteExt beforeDate = createNoteExtWithDate(1001L, "followup", jan10);
            CaseManagementNoteExt afterDate = createNoteExtWithDate(1002L, "followup", jan20);
            CaseManagementNoteExt wrongKey = createNoteExtWithDate(1003L, "other", jan20);
            entityManager.flush();

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
            createNoteExtWithDate(1001L, "reminder", jan10);
            entityManager.flush();

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
            createNoteExt(8888L, "key1", "value1");
            createNoteExt(8888L, "key2", "value2");
            createNoteExt(9999L, "key1", "value1");  // Different note
            entityManager.flush();

            // When
            List<CaseManagementNoteExt> results = caseManagementNoteExtDAO.getExtByNote(8888L);

            // Then
            assertThat(results)
                .hasSize(2)
                .allMatch(e -> e.getNoteId().equals(8888L));
        }

        @Test
        @Tag("read")
        @DisplayName("should get extensions by key value")
        void shouldGetExtByKeyVal() {
            // Given
            createNoteExt(1001L, "status", "active");
            createNoteExt(1002L, "status", "inactive");
            createNoteExt(1003L, "type", "active");  // Different key
            entityManager.flush();

            // When
            List results = caseManagementNoteExtDAO.getExtByKeyVal("status");

            // Then
            assertThat(results).hasSize(2);
        }
    }
}
