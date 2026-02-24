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
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.model.security.Secrole;
import io.github.carlos_emr.carlos.daos.security.SecroleDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for IssueDAO multi-parameter query methods.
 *
 * <p>These tests validate that HQL queries with multiple positional parameters
 * bind parameters correctly. Tests are designed to catch parameter index errors
 * during Hibernate migration.</p>
 *
 * @since 2026-02-03
 * @see IssueDAO
 */
@DisplayName("IssueDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("casemgmt")
@Transactional
public class IssueDAOIntegrationTest extends OpenOTestBase {

    @Autowired
    @Qualifier("IssueDAO")
    private IssueDAO issueDAO;

    @Autowired
    private SecroleDao secroleDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        createIssue("DIAB001", "Diabetes Type 1", "doctor", "medical");
        createIssue("DIAB002", "Diabetes Type 2", "doctor", "medical");
        createIssue("HYPER001", "Hypertension", "doctor", "medical");
        createIssue("PSYCH001", "Depression", "nurse", "mental");
        hibernateTemplate.flush();
    }

    private Issue createIssue(String code, String description, String role, String type) {
        Issue issue = new Issue();
        issue.setCode(code);
        issue.setDescription(description);
        issue.setRole(role);
        issue.setType(type);
        issueDAO.saveIssue(issue);
        return issue;
    }

    /** Tests for CRUD operations on Issue entities. */
    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("read")
        @DisplayName("should retrieve issue by valid ID")
        void shouldRetrieveIssue_whenValidIdProvided() {
            // Given
            Issue saved = createIssue("CRUD001", "CRUD Test Issue", "doctor", "medical");
            hibernateTemplate.flush();

            // When
            Issue found = issueDAO.getIssue(saved.getId());

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getCode()).isEqualTo("CRUD001");
            assertThat(found.getDescription()).isEqualTo("CRUD Test Issue");
        }

        @Test
        @Tag("create")
        @DisplayName("should persist valid issue data")
        void shouldPersistIssue_whenValidDataProvided() {
            // Given
            Issue issue = new Issue();
            issue.setCode("NEW001");
            issue.setDescription("New Issue");
            issue.setRole("doctor");
            issue.setType("medical");

            // When
            issueDAO.saveIssue(issue);
            hibernateTemplate.flush();

            // Then
            assertThat(issue.getId()).isNotNull();
            Issue found = issueDAO.getIssue(issue.getId());
            assertThat(found).isNotNull();
            assertThat(found.getCode()).isEqualTo("NEW001");
        }

        @Test
        @Tag("update")
        @DisplayName("should update issue with changes")
        void shouldUpdateIssue_whenChangesProvided() {
            // Given
            Issue issue = createIssue("UPD001", "Original", "doctor", "medical");
            hibernateTemplate.flush();

            // When
            issue.setDescription("Updated Description");
            issueDAO.saveIssue(issue);
            hibernateTemplate.flush();

            // Then
            Issue found = issueDAO.getIssue(issue.getId());
            assertThat(found.getDescription()).isEqualTo("Updated Description");
        }
    }

    /** Tests for findIssueByTypeAndCode (2 params). */
    @Nested
    @DisplayName("findIssueByTypeAndCode (2 params)")
    class FindIssueByTypeAndCode {

        @Test
        @Tag("query")
        @DisplayName("should find issue when both type and code match")
        void shouldFindIssue_whenBothTypeAndCodeMatch() {
            // When
            Issue found = issueDAO.findIssueByTypeAndCode("medical", "DIAB001");

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getCode()).isEqualTo("DIAB001");
            assertThat(found.getType()).isEqualTo("medical");
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when type doesn't match")
        void shouldReturnNull_whenTypeDoesntMatch() {
            // When
            Issue found = issueDAO.findIssueByTypeAndCode("mental", "DIAB001");

            // Then
            assertThat(found).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when code doesn't match")
        void shouldReturnNull_whenCodeDoesntMatch() {
            // When
            Issue found = issueDAO.findIssueByTypeAndCode("medical", "NONEXISTENT");

            // Then
            assertThat(found).isNull();
        }
    }

    /** Tests for findIssueByCode with code array (IN clause). */
    @Nested
    @DisplayName("findIssueByCode (String[] codes)")
    class FindIssueByCodeArray {

        @Test
        @Tag("query")
        @DisplayName("should handle code array parameters")
        void shouldHandleCodeArrayParams_withMultipleCodes() {
            // Given
            String[] codes = new String[]{"DIAB001", "HYPER001"};

            // When
            List<Issue> results = issueDAO.findIssueByCode(codes);

            // Then
            assertThat(results)
                .isNotEmpty()
                .extracting(Issue::getCode)
                .contains("DIAB001", "HYPER001")
                .doesNotContain("PSYCH001");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when no codes match")
        void shouldReturnEmpty_whenNoCodesMatch() {
            // Given
            String[] codes = new String[]{"NONEXISTENT1", "NONEXISTENT2"};

            // When
            List<Issue> results = issueDAO.findIssueByCode(codes);

            // Then
            assertThat(results).isEmpty();
        }
    }

    /** Tests for findIssueBySearch (2 params: code like, description like). */
    @Nested
    @DisplayName("findIssueBySearch (2 params)")
    class FindIssueBySearch {

        @Test
        @Tag("search")
        @DisplayName("should find issues matching search term in code")
        void shouldFindIssues_whenSearchTermMatchesCode() {
            // When
            List<Issue> results = issueDAO.findIssueBySearch("DIAB");

            // Then
            assertThat(results)
                .hasSize(2)
                .extracting(Issue::getCode)
                .allMatch(code -> code.contains("DIAB"));
        }

        @Test
        @Tag("search")
        @DisplayName("should find issues matching search term in description")
        void shouldFindIssues_whenSearchTermMatchesDescription() {
            // When
            List<Issue> results = issueDAO.findIssueBySearch("Diabetes");

            // Then
            assertThat(results)
                .isNotEmpty()
                .allMatch(i -> i.getDescription().toLowerCase().contains("diabetes"));
        }

        @Test
        @Tag("search")
        @DisplayName("should return empty list when no matches found")
        void shouldReturnEmptyList_whenNoMatches() {
            // When
            List<Issue> results = issueDAO.findIssueBySearch("NONEXISTENT_XYZ");

            // Then
            assertThat(results).isEmpty();
        }
    }

    /** Tests for searchNoRolesConcerned. */
    @Nested
    @DisplayName("searchNoRolesConcerned (uses ?0 and ?1 with same value)")
    class SearchNoRolesConcerned {

        @Test
        @Tag("search")
        @DisplayName("should search by code and description without role filter")
        @SuppressWarnings("unchecked")
        void shouldSearchByCodeAndDescription() {
            // When
            List results = issueDAO.searchNoRolesConcerned("Hyper");

            // Then
            assertThat(results).isNotEmpty();
            Issue first = (Issue) results.get(0);
            assertThat(first.getCode()).isEqualTo("HYPER001");
        }
    }

    /** Tests for role-based filtering and pagination. */
    @Nested
    @DisplayName("Role-based and paginated queries")
    class RoleBasedQueries {

        @Test
        @Tag("filter")
        @DisplayName("should implement paginated search results")
        void shouldImplementPaginatedSearchResults() {
            // Given
            for (int i = 0; i < 5; i++) {
                createIssue("PAGE" + i, "Pagination Test " + i, "doctor", "medical");
            }
            hibernateTemplate.flush();

            Secrole role = new Secrole("doctor", "Doctor role");
            secroleDao.save(role);
            hibernateTemplate.flush();
            List<Secrole> roles = new ArrayList<>();
            roles.add(role);

            // When
            List<Issue> results = issueDAO.search("PAGE", roles, 0, 2);

            // Then
            assertThat(results).hasSize(2);
        }

        @Test
        @Tag("aggregate")
        @DisplayName("should count search results for matching roles")
        void shouldCountSearchResults_forMatchingRoles() {
            // Given
            Secrole role = new Secrole("doctor", "Doctor role");
            secroleDao.save(role);
            hibernateTemplate.flush();
            List<Secrole> roles = new ArrayList<>();
            roles.add(role);

            // When
            Integer count = issueDAO.searchCount("DIAB", roles);

            // Then
            assertThat(count).isGreaterThanOrEqualTo(2);
        }

        @Test
        @Tag("filter")
        @DisplayName("should retrieve community-specific codes by type")
        void shouldRetrieveCodes_forCommunityType() {
            // Given
            createIssue("COMM001", "Community Issue", "doctor", "community");
            hibernateTemplate.flush();

            // When - pass uppercase to verify DAO lowercases input before binding
            List<String> codes = issueDAO.getLocalCodesByCommunityType("COMMUNITY");

            // Then
            assertThat(codes)
                .isNotEmpty()
                .contains("COMM001")
                .doesNotContain("DIAB001");
        }

        @Test
        @Tag("filter")
        @DisplayName("should return empty list for blank community type")
        void shouldReturnEmpty_forBlankCommunityType() {
            List<String> codes = issueDAO.getLocalCodesByCommunityType("");
            assertThat(codes).isEmpty();
        }

        @Test
        @Tag("filter")
        @DisplayName("should return empty list for null community type")
        void shouldReturnEmpty_forNullCommunityType() {
            List<String> codes = issueDAO.getLocalCodesByCommunityType(null);
            assertThat(codes).isEmpty();
        }
    }

    /** Tests for single parameter queries (baseline). */
    @Nested
    @DisplayName("Single parameter queries (baseline)")
    class SingleParamQueries {

        @Test
        @Tag("read")
        @DisplayName("should find issue by code")
        void shouldFindIssueByCode() {
            // When
            Issue found = issueDAO.findIssueByCode("DIAB001");

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getCode()).isEqualTo("DIAB001");
        }

        @Test
        @Tag("read")
        @DisplayName("should get all issues")
        void shouldGetAllIssues() {
            // When
            List<Issue> issues = issueDAO.getIssues();

            // Then
            assertThat(issues)
                .isNotEmpty()
                .extracting(Issue::getCode)
                .contains("DIAB001", "DIAB002", "HYPER001", "PSYCH001");
        }

        @Test
        @Tag("read")
        @DisplayName("should match issues by code string")
        void shouldMatchIssues_byCodeString() {
            // When
            Issue found = issueDAO.findIssueByCode("PSYCH001");

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getType()).isEqualTo("mental");
            assertThat(found.getRole()).isEqualTo("nurse");
        }
    }
}
