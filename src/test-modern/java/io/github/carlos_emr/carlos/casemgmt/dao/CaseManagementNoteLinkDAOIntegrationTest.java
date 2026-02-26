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
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for CaseManagementNoteLinkDAO multi-parameter query methods.
 *
 * <p>These tests validate that HQL queries with multiple positional parameters
 * bind parameters correctly.</p>
 *
 * @since 2026-02-03
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

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private CaseManagementNoteLink createLink(Integer tableName, Long tableId, Long noteId) {
        CaseManagementNoteLink link = new CaseManagementNoteLink();
        link.setTableName(tableName);
        link.setTableId(tableId);
        link.setNoteId(noteId);
        caseManagementNoteLinkDAO.save(link);
        return link;
    }

    private CaseManagementNoteLink createLink(Integer tableName, Long tableId, Long noteId, String otherId) {
        CaseManagementNoteLink link = new CaseManagementNoteLink();
        link.setTableName(tableName);
        link.setTableId(tableId);
        link.setNoteId(noteId);
        link.setOtherId(otherId);
        caseManagementNoteLinkDAO.save(link);
        return link;
    }

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
            entityManager.flush();

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
            entityManager.flush();

            // When - Search for different table/id combination
            List<CaseManagementNoteLink> results = caseManagementNoteLinkDAO.getLinkByTableId(99, 999L);

            // Then
            assertThat(results).isEmpty();
        }
    }

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
            entityManager.flush();

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
            entityManager.flush();

            // When
            List<CaseManagementNoteLink> results = caseManagementNoteLinkDAO
                .getLinkByTableId(1, 100L, "NONEXISTENT");

            // Then
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("getLinkByTableIdDesc (2 params)")
    class GetLinkByTableIdDesc {

        @Test
        @Tag("query")
        @DisplayName("should return links in descending order by ID")
        void shouldReturnLinksInDescOrder() {
            // Given
            CaseManagementNoteLink link1 = createLink(1, 100L, 1001L);
            entityManager.flush();
            CaseManagementNoteLink link2 = createLink(1, 100L, 1002L);
            entityManager.flush();
            CaseManagementNoteLink link3 = createLink(1, 100L, 1003L);
            entityManager.flush();

            // When
            List<CaseManagementNoteLink> results = caseManagementNoteLinkDAO.getLinkByTableIdDesc(1, 100L);

            // Then - Should be in descending order by ID
            assertThat(results).hasSize(3);
            assertThat(results.get(0).getId()).isGreaterThan(results.get(1).getId());
            assertThat(results.get(1).getId()).isGreaterThan(results.get(2).getId());
        }
    }

    @Nested
    @DisplayName("getLinkByTableIdDesc (3 params)")
    class GetLinkByTableIdDescThreeParams {

        @Test
        @Tag("query")
        @DisplayName("should filter by all three params and return in desc order")
        void shouldFilterAndReturnDescOrder() {
            // Given
            CaseManagementNoteLink link1 = createLink(1, 100L, 1001L, "A");
            entityManager.flush();
            CaseManagementNoteLink link2 = createLink(1, 100L, 1002L, "A");
            entityManager.flush();
            CaseManagementNoteLink differentOther = createLink(1, 100L, 1003L, "B");
            entityManager.flush();

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

    @Nested
    @DisplayName("Single parameter queries (baseline)")
    class SingleParamQueries {

        @Test
        @Tag("read")
        @DisplayName("should get links by note ID")
        void shouldGetLinksByNoteId() {
            // Given
            CaseManagementNoteLink link1 = createLink(1, 100L, 5555L);
            CaseManagementNoteLink link2 = createLink(2, 200L, 5555L);
            CaseManagementNoteLink differentNote = createLink(1, 100L, 6666L);
            entityManager.flush();

            // When
            List<CaseManagementNoteLink> results = caseManagementNoteLinkDAO.getLinkByNote(5555L);

            // Then
            assertThat(results)
                .hasSize(2)
                .extracting(CaseManagementNoteLink::getNoteId)
                .allMatch(id -> id.equals(5555L));
        }
    }
}
