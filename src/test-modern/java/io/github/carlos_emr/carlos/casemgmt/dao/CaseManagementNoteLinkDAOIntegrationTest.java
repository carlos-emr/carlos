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

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link CaseManagementNoteLinkDAO} multi-parameter query methods.
 *
 * <p>These tests validate HQL queries with positional parameters (?0, ?1, ...)
 * bind correctly, ensuring safe migration to Hibernate 6 named parameter syntax.
 * Tests cover CRUD operations, multi-parameter searches, and edge cases including
 * ascending/descending ordering, three-parameter filtering with otherId, and
 * delegation methods that return the last element from ordered result sets.</p>
 *
 * @since 2026-02-26
 * @see CaseManagementNoteLinkDAO
 */
@DisplayName("CaseManagementNoteLinkDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("casemgmt")
@Transactional
public class CaseManagementNoteLinkDAOIntegrationTest extends CarlosTestBase {

    @Autowired
    @Qualifier("CaseManagementNoteLinkDAO")
    private CaseManagementNoteLinkDAO caseManagementNoteLinkDAO;

    /**
     * Creates and persists a {@link CaseManagementNoteLink} with the given parameters.
     *
     * @param tableName Integer the table name identifier for the linked entity type
     * @param tableId Long the primary key of the linked entity in the target table
     * @param noteId Long the ID of the associated case management note
     * @return CaseManagementNoteLink the persisted link entity with generated ID
     */
    private CaseManagementNoteLink createLink(Integer tableName, Long tableId, Long noteId) {
        CaseManagementNoteLink link = new CaseManagementNoteLink();
        link.setTableName(tableName);
        link.setTableId(tableId);
        link.setNoteId(noteId);
        caseManagementNoteLinkDAO.save(link);
        return link;
    }

    /**
     * Creates and persists a {@link CaseManagementNoteLink} with an additional otherId filter value.
     *
     * @param tableName Integer the table name identifier for the linked entity type
     * @param tableId Long the primary key of the linked entity in the target table
     * @param noteId Long the ID of the associated case management note
     * @param otherId String an additional identifier used for three-parameter query filtering
     * @return CaseManagementNoteLink the persisted link entity with generated ID
     */
    private CaseManagementNoteLink createLink(Integer tableName, Long tableId, Long noteId, String otherId) {
        CaseManagementNoteLink link = new CaseManagementNoteLink();
        link.setTableName(tableName);
        link.setTableId(tableId);
        link.setNoteId(noteId);
        link.setOtherId(otherId);
        caseManagementNoteLinkDAO.save(link);
        return link;
    }

    /**
     * Tests for {@code getLinkByTableId(Integer tableName, Long tableId)} - two-parameter
     * query filtering links by table name and table ID.
     */
    @Nested
    @DisplayName("getLinkByTableId (2 params: tableName, tableId)")
    class GetLinkByTableIdTwoParams {

        @Test
        @Tag("query")
        @DisplayName("should find links when both tableName and tableId match")
        void shouldFindLinks_whenBothTableNameAndTableIdMatch() {
            // Given
            CaseManagementNoteLink match1 = createLink(1, 100L, 1001L);
            CaseManagementNoteLink match2 = createLink(1, 100L, 1002L);
            CaseManagementNoteLink wrongTable = createLink(2, 100L, 1003L);  // Different tableName
            CaseManagementNoteLink wrongId = createLink(1, 200L, 1004L);     // Different tableId
            // Flush Hibernate session to sync HibernateDaoSupport writes to the database before querying
            hibernateTemplate.flush();

            // When
            List<CaseManagementNoteLink> results = caseManagementNoteLinkDAO.getLinkByTableId(1, 100L);

            // Then
            assertThat(results)
                .hasSize(2)
                .extracting(CaseManagementNoteLink::getId)
                .contains(match1.getId(), match2.getId())
                .doesNotContain(wrongTable.getId(), wrongId.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no matches")
        void shouldReturnEmptyList_whenNoMatches() {
            // Given
            createLink(1, 100L, 1001L);
            hibernateTemplate.flush();

            // When - Search for different table/id combination
            List<CaseManagementNoteLink> results = caseManagementNoteLinkDAO.getLinkByTableId(99, 999L);

            // Then
            assertThat(results).isEmpty();
        }
    }

    /**
     * Tests for {@code getLinkByTableId(Integer tableName, Long tableId, String otherId)} -
     * three-parameter query adding otherId filtering to the two-parameter variant.
     */
    @Nested
    @DisplayName("getLinkByTableId (3 params: tableName, tableId, otherId)")
    class GetLinkByTableIdThreeParams {

        @Test
        @Tag("query")
        @DisplayName("should find links when all three parameters match")
        void shouldFindLinks_whenAllThreeParamsMatch() {
            // Given
            CaseManagementNoteLink match = createLink(1, 100L, 1001L, "OTHER1");
            CaseManagementNoteLink wrongOther = createLink(1, 100L, 1002L, "OTHER2");  // Different otherId
            CaseManagementNoteLink wrongTable = createLink(2, 100L, 1003L, "OTHER1");  // Different tableName
            CaseManagementNoteLink wrongId = createLink(1, 200L, 1004L, "OTHER1");     // Different tableId
            hibernateTemplate.flush();

            // When
            List<CaseManagementNoteLink> results = caseManagementNoteLinkDAO
                .getLinkByTableId(1, 100L, "OTHER1");

            // Then - Only match should be returned
            assertThat(results)
                .hasSize(1)
                .extracting(CaseManagementNoteLink::getId)
                .containsExactly(match.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when otherId doesn't match")
        void shouldReturnEmpty_whenOtherIdDoesntMatch() {
            // Given
            createLink(1, 100L, 1001L, "EXISTING");
            hibernateTemplate.flush();

            // When
            List<CaseManagementNoteLink> results = caseManagementNoteLinkDAO
                .getLinkByTableId(1, 100L, "NONEXISTENT");

            // Then
            assertThat(results).isEmpty();
        }
    }

    /**
     * Tests for {@code getLinkByTableIdDesc(Integer tableName, Long tableId)} -
     * two-parameter query returning links in descending order by ID.
     */
    @Nested
    @DisplayName("getLinkByTableIdDesc (2 params)")
    class GetLinkByTableIdDesc {

        @Test
        @Tag("query")
        @DisplayName("should return links in descending order by ID")
        void shouldReturnLinks_inDescOrder() {
            // Given - flush between each insert to guarantee sequential ID generation
            CaseManagementNoteLink link1 = createLink(1, 100L, 1001L);
            hibernateTemplate.flush();
            CaseManagementNoteLink link2 = createLink(1, 100L, 1002L);
            hibernateTemplate.flush();
            CaseManagementNoteLink link3 = createLink(1, 100L, 1003L);
            hibernateTemplate.flush();

            // When
            List<CaseManagementNoteLink> results = caseManagementNoteLinkDAO.getLinkByTableIdDesc(1, 100L);

            // Then - Should be in descending order by ID
            assertThat(results).hasSize(3);
            assertThat(results.get(0).getId()).isGreaterThan(results.get(1).getId());
            assertThat(results.get(1).getId()).isGreaterThan(results.get(2).getId());
        }
    }

    /**
     * Tests for {@code getLinkByTableIdDesc(Integer tableName, Long tableId, String otherId)} -
     * three-parameter query returning filtered links in descending order by ID.
     */
    @Nested
    @DisplayName("getLinkByTableIdDesc (3 params)")
    class GetLinkByTableIdDescThreeParams {

        @Test
        @Tag("query")
        @DisplayName("should filter by all three params and return in desc order")
        void shouldFilterAndReturn_inDescOrder() {
            // Given - flush between each insert to guarantee sequential ID generation for ordering tests
            CaseManagementNoteLink link1 = createLink(1, 100L, 1001L, "A");
            hibernateTemplate.flush();
            CaseManagementNoteLink link2 = createLink(1, 100L, 1002L, "A");
            hibernateTemplate.flush();
            CaseManagementNoteLink differentOther = createLink(1, 100L, 1003L, "B");
            hibernateTemplate.flush();

            // When
            List<CaseManagementNoteLink> results = caseManagementNoteLinkDAO
                .getLinkByTableIdDesc(1, 100L, "A");

            // Then
            assertThat(results)
                .hasSize(2)
                .extracting(CaseManagementNoteLink::getId)
                .doesNotContain(differentOther.getId());

            // Verify descending order
            assertThat(results.get(0).getId()).isGreaterThan(results.get(1).getId());
        }
    }

    /**
     * Tests for {@code getLinkByNote(Long noteId)} - single-parameter baseline query
     * retrieving links by note ID.
     */
    @Nested
    @DisplayName("Single parameter queries (baseline)")
    class SingleParamQueries {

        @Test
        @Tag("read")
        @DisplayName("should get links by note ID")
        void shouldGetLinks_byNoteId() {
            // Given
            CaseManagementNoteLink link1 = createLink(1, 100L, 5555L);
            CaseManagementNoteLink link2 = createLink(2, 200L, 5555L);
            CaseManagementNoteLink differentNote = createLink(1, 100L, 6666L);
            hibernateTemplate.flush();

            // When
            List<CaseManagementNoteLink> results = caseManagementNoteLinkDAO.getLinkByNote(5555L);

            // Then
            assertThat(results)
                .hasSize(2)
                .extracting(CaseManagementNoteLink::getNoteId)
                .allMatch(id -> id.equals(5555L));
        }
    }

    /** Tests for getNoteLink(Long id) - basic read by primary key. */
    @Nested
    @DisplayName("getNoteLink (by primary key)")
    class GetNoteLink {

        @Test
        @Tag("read")
        @DisplayName("should return link when valid ID provided")
        void shouldReturnLink_whenValidIdProvided() {
            // Given
            CaseManagementNoteLink saved = createLink(1, 100L, 2001L);
            // Flush Hibernate session to sync HibernateDaoSupport writes before read query
            hibernateTemplate.flush();

            // When
            CaseManagementNoteLink found = caseManagementNoteLinkDAO.getNoteLink(saved.getId());

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getTableName()).isEqualTo(1);
            assertThat(found.getTableId()).isEqualTo(100L);
            assertThat(found.getNoteId()).isEqualTo(2001L);
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when ID does not exist")
        void shouldReturnNull_whenIdDoesNotExist() {
            // When
            CaseManagementNoteLink found = caseManagementNoteLinkDAO.getNoteLink(999999L);

            // Then
            assertThat(found).isNull();
        }
    }

    /** Tests for getLastLinkByTableId(Integer, Long) - delegation returning last element. */
    @Nested
    @DisplayName("getLastLinkByTableId (2 params: tableName, tableId)")
    class GetLastLinkByTableIdTwoParams {

        @Test
        @Tag("query")
        @DisplayName("should return last link ordered by ID ascending")
        void shouldReturnLastLink_orderedByIdAscending() {
            // Given - flush between inserts to guarantee sequential ID generation
            CaseManagementNoteLink first = createLink(3, 300L, 3001L);
            hibernateTemplate.flush();
            CaseManagementNoteLink second = createLink(3, 300L, 3002L);
            hibernateTemplate.flush();
            CaseManagementNoteLink third = createLink(3, 300L, 3003L);
            hibernateTemplate.flush();

            // When - delegates to getLinkByTableId (ordered by id asc), returns last
            CaseManagementNoteLink last = caseManagementNoteLinkDAO
                .getLastLinkByTableId(3, 300L);

            // Then - should be the one with highest ID (third)
            assertThat(last).isNotNull();
            assertThat(last.getId()).isEqualTo(third.getId());
            assertThat(last.getNoteId()).isEqualTo(3003L);
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when no matching links exist")
        void shouldReturnNull_whenNoMatchingLinksExist() {
            // When
            CaseManagementNoteLink last = caseManagementNoteLinkDAO
                .getLastLinkByTableId(99, 999L);

            // Then
            assertThat(last).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should return single link when only one match exists")
        void shouldReturnSingleLink_whenOnlyOneMatchExists() {
            // Given
            CaseManagementNoteLink only = createLink(4, 400L, 4001L);
            hibernateTemplate.flush();

            // When
            CaseManagementNoteLink last = caseManagementNoteLinkDAO
                .getLastLinkByTableId(4, 400L);

            // Then
            assertThat(last).isNotNull();
            assertThat(last.getId()).isEqualTo(only.getId());
        }
    }

    /** Tests for getLastLinkByTableId(Integer, Long, String) - delegation with otherId filter. */
    @Nested
    @DisplayName("getLastLinkByTableId (3 params: tableName, tableId, otherId)")
    class GetLastLinkByTableIdThreeParams {

        @Test
        @Tag("query")
        @DisplayName("should return last link matching all three params")
        void shouldReturnLastLink_matchingAllThreeParams() {
            // Given - flush between inserts to guarantee sequential ID generation;
            // links share tableName/tableId but have different otherIds
            CaseManagementNoteLink matchFirst = createLink(5, 500L, 5001L, "MATCH");
            hibernateTemplate.flush();
            CaseManagementNoteLink matchSecond = createLink(5, 500L, 5002L, "MATCH");
            hibernateTemplate.flush();
            CaseManagementNoteLink differentOther = createLink(5, 500L, 5003L, "OTHER");
            hibernateTemplate.flush();

            // When - delegates to getLinkByTableId(3 params) and returns last
            CaseManagementNoteLink last = caseManagementNoteLinkDAO
                .getLastLinkByTableId(5, 500L, "MATCH");

            // Then - should be matchSecond (last with otherId="MATCH")
            assertThat(last).isNotNull();
            assertThat(last.getId()).isEqualTo(matchSecond.getId());
            assertThat(last.getOtherId()).isEqualTo("MATCH");
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when otherId does not match any link")
        void shouldReturnNull_whenOtherIdDoesNotMatch() {
            // Given
            createLink(5, 500L, 5001L, "EXISTS");
            hibernateTemplate.flush();

            // When
            CaseManagementNoteLink last = caseManagementNoteLinkDAO
                .getLastLinkByTableId(5, 500L, "NOPE");

            // Then
            assertThat(last).isNull();
        }
    }

    /** Tests for getLastLinkByNote(Long noteId) - delegation returning last link for a note. */
    @Nested
    @DisplayName("getLastLinkByNote (noteId)")
    class GetLastLinkByNote {

        @Test
        @Tag("query")
        @DisplayName("should return last link for given note ID")
        void shouldReturnLastLink_forGivenNoteId() {
            // Given - flush between inserts to guarantee sequential ID generation;
            // multiple links pointing to same note
            CaseManagementNoteLink first = createLink(1, 100L, 7777L);
            hibernateTemplate.flush();
            CaseManagementNoteLink second = createLink(2, 200L, 7777L);
            hibernateTemplate.flush();
            CaseManagementNoteLink third = createLink(3, 300L, 7777L);
            hibernateTemplate.flush();

            // When - delegates to getLinkByNote (ordered by id asc), returns last
            CaseManagementNoteLink last = caseManagementNoteLinkDAO.getLastLinkByNote(7777L);

            // Then
            assertThat(last).isNotNull();
            assertThat(last.getId()).isEqualTo(third.getId());
            assertThat(last.getTableName()).isEqualTo(3);
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when note has no links")
        void shouldReturnNull_whenNoteHasNoLinks() {
            // When
            CaseManagementNoteLink last = caseManagementNoteLinkDAO.getLastLinkByNote(88888L);

            // Then
            assertThat(last).isNull();
        }
    }

    /** Tests for update(CaseManagementNoteLink) - update operation. */
    @Nested
    @DisplayName("update (CaseManagementNoteLink)")
    class UpdateLink {

        @Test
        @Tag("update")
        @DisplayName("should persist changes when link is updated")
        void shouldPersistChanges_whenLinkIsUpdated() {
            // Given
            CaseManagementNoteLink link = createLink(1, 100L, 9001L);
            hibernateTemplate.flush();

            // When - update the noteId
            link.setNoteId(9002L);
            link.setOtherId("UPDATED");
            caseManagementNoteLinkDAO.update(link);
            // Flush Hibernate session to persist the update before re-fetching
            hibernateTemplate.flush();

            // Then - re-fetch and verify changes
            CaseManagementNoteLink found = caseManagementNoteLinkDAO.getNoteLink(link.getId());
            assertThat(found).isNotNull();
            assertThat(found.getNoteId()).isEqualTo(9002L);
            assertThat(found.getOtherId()).isEqualTo("UPDATED");
        }
    }
}
