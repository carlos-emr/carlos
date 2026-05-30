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

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        // Create test issues with different types and codes
        createIssue("DIAB001", "Diabetes Type 1", "doctor", "medical");
        createIssue("DIAB002", "Diabetes Type 2", "doctor", "medical");
        createIssue("HYPER001", "Hypertension", "doctor", "medical");
        createIssue("PSYCH001", "Depression", "nurse", "mental");
        entityManager.flush();
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

    @Nested
    @DisplayName("findIssueByTypeAndCode (2 params)")
    class FindIssueByTypeAndCode {

        @Test
        @Tag("query")
        @DisplayName("should find issue when both type and code match")
        void shouldFindIssue_whenBothTypeAndCodeMatch() {
            // Given - Issues created in setUp

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
            // When - Search with wrong type
            Issue found = issueDAO.findIssueByTypeAndCode("mental", "DIAB001");

            // Then
            assertThat(found).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when code doesn't match")
        void shouldReturnNull_whenCodeDoesntMatch() {
            // When - Search with wrong code
            Issue found = issueDAO.findIssueByTypeAndCode("medical", "NONEXISTENT");

            // Then
            assertThat(found).isNull();
        }
    }

    @Nested
    @DisplayName("findIssueBySearch (2 params: code like, description like)")
    class FindIssueBySearch {

        @Test
        @Tag("search")
        @DisplayName("should find issues matching search term in code")
        void shouldFindIssues_whenSearchTermMatchesCode() {
            // When
            List<Issue> results = issueDAO.findIssueBySearch("DIAB");

            // Then - Should find both diabetes issues
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

    @Nested
    @DisplayName("searchNoRolesConcerned (uses ?0 and ?1 with same value)")
    class SearchNoRolesConcerned {

        @Test
        @Tag("search")
        @DisplayName("should search by code and description without role filter")
        @SuppressWarnings("unchecked")
        void shouldSearchByCodeAndDescription() {
            // When - Method returns raw List (List<Issue>)
            List results = issueDAO.searchNoRolesConcerned("Hyper");

            // Then
            assertThat(results).isNotEmpty();
            // Verify first result is the expected issue
            Issue first = (Issue) results.get(0);
            assertThat(first.getCode()).isEqualTo("HYPER001");
        }
    }

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

            // Then - Should contain our test issues
            assertThat(issues)
                .isNotEmpty()
                .extracting(Issue::getCode)
                .contains("DIAB001", "DIAB002", "HYPER001", "PSYCH001");
        }
    }
}
