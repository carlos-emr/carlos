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
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
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
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for CaseManagementIssueDAO multi-parameter query methods.
 *
 * <p>These tests validate that HQL queries with multiple positional parameters
 * bind parameters correctly. Tests are designed to catch parameter index errors
 * during Hibernate migration.</p>
 *
 * @since 2026-02-03
 * @see CaseManagementIssueDAO
 */
@DisplayName("CaseManagementIssueDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("casemgmt")
@Transactional
public class CaseManagementIssueDAOIntegrationTest extends OpenOTestBase {

    @Autowired
    @Qualifier("CaseManagementIssueDAO")
    private CaseManagementIssueDAO caseManagementIssueDAO;

    @Autowired
    @Qualifier("IssueDAO")
    private IssueDAO issueDAO;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private Issue testIssue1;
    private Issue testIssue2;

    @BeforeEach
    void setUp() {
        // Create test issues
        testIssue1 = new Issue();
        testIssue1.setCode("TEST001");
        testIssue1.setDescription("Test Issue 1");
        testIssue1.setRole("doctor");
        testIssue1.setType("system");
        issueDAO.saveIssue(testIssue1);

        testIssue2 = new Issue();
        testIssue2.setCode("TEST002");
        testIssue2.setDescription("Test Issue 2");
        testIssue2.setRole("doctor");
        testIssue2.setType("system");
        issueDAO.saveIssue(testIssue2);

        entityManager.flush();
    }

    /**
     * Creates a CaseManagementIssue for testing.
     * Note: Model uses Integer for demographic_no, DAO methods use String.
     */
    private CaseManagementIssue createCaseManagementIssue(String demographicNo, Issue issue) {
        CaseManagementIssue cmi = new CaseManagementIssue();
        cmi.setDemographic_no(Integer.valueOf(demographicNo));
        cmi.setIssue_id(issue.getId());
        cmi.setAcute(false);
        cmi.setCertain(true);
        cmi.setMajor(false);
        cmi.setResolved(false);
        cmi.setUpdate_date(new Date());
        caseManagementIssueDAO.saveIssue(cmi);
        return cmi;
    }

    private CaseManagementIssue createCaseManagementIssue(String demographicNo, Issue issue, Date updateDate) {
        CaseManagementIssue cmi = new CaseManagementIssue();
        cmi.setDemographic_no(Integer.valueOf(demographicNo));
        cmi.setIssue_id(issue.getId());
        cmi.setAcute(false);
        cmi.setCertain(true);
        cmi.setMajor(false);
        cmi.setResolved(false);
        cmi.setUpdate_date(updateDate);
        caseManagementIssueDAO.saveIssue(cmi);
        return cmi;
    }

    private Date daysFromNow(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, days);
        return cal.getTime();
    }

    @Nested
    @DisplayName("getIssuebyId (2 params: demo, id)")
    class GetIssueById {

        @Test
        @Tag("query")
        @DisplayName("should find issue when both demographic and issue ID match")
        void shouldFindIssue_whenBothDemoAndIdMatch() {
            // Given
            createCaseManagementIssue("111", testIssue1);
            CaseManagementIssue issue2 = createCaseManagementIssue("222", testIssue1);  // Same issue, different demo
            CaseManagementIssue issue3 = createCaseManagementIssue("111", testIssue2);  // Same demo, different issue
            entityManager.flush();

            // When
            CaseManagementIssue found = caseManagementIssueDAO.getIssuebyId(
                "111", String.valueOf(testIssue1.getId()));

            // Then - Should find issue1 only
            assertThat(found).isNotNull();
            assertThat(found.getDemographic_no()).isEqualTo(111);
            assertThat(found.getIssue_id()).isEqualTo(testIssue1.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when demographic doesn't match")
        void shouldReturnNull_whenDemoDoesntMatch() {
            // Given
            createCaseManagementIssue("111", testIssue1);
            entityManager.flush();

            // When - Search with wrong demographic
            CaseManagementIssue found = caseManagementIssueDAO.getIssuebyId(
                "999", String.valueOf(testIssue1.getId()));

            // Then
            assertThat(found).isNull();
        }
    }

    @Nested
    @DisplayName("getIssuebyIssueCode (2 params: demo, issueCode)")
    class GetIssueByIssueCode {

        @Test
        @Tag("query")
        @DisplayName("should find issue when both demographic and issue code match")
        void shouldFindIssue_whenBothDemoAndCodeMatch() {
            // Given
            CaseManagementIssue cmi = createCaseManagementIssue("111", testIssue1);  // testIssue1 has code "TEST001"
            createCaseManagementIssue("222", testIssue1);  // Same code, different demo
            createCaseManagementIssue("111", testIssue2);  // Same demo, different code
            entityManager.flush();

            // When
            CaseManagementIssue found = caseManagementIssueDAO.getIssuebyIssueCode("111", "TEST001");

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getDemographic_no()).isEqualTo(111);
            assertThat(found.getId()).isEqualTo(cmi.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when issue code doesn't match")
        void shouldReturnNull_whenCodeDoesntMatch() {
            // Given
            createCaseManagementIssue("111", testIssue1);
            entityManager.flush();

            // When - Search with wrong code
            CaseManagementIssue found = caseManagementIssueDAO.getIssuebyIssueCode("111", "NONEXISTENT");

            // Then
            assertThat(found).isNull();
        }
    }

    @Nested
    @DisplayName("getIssuesByDemographicSince (2 params: demographicNo, date)")
    class GetIssuesByDemographicSince {

        @Test
        @Tag("query")
        @DisplayName("should filter by both demographic and date")
        void shouldFilterByBothDemoAndDate() {
            // Given
            Date cutoff = daysFromNow(-5);

            CaseManagementIssue recent = createCaseManagementIssue("111", testIssue1, daysFromNow(-2));
            CaseManagementIssue old = createCaseManagementIssue("111", testIssue2, daysFromNow(-10));
            CaseManagementIssue wrongDemo = createCaseManagementIssue("222", testIssue1, daysFromNow(-2));
            entityManager.flush();

            // When
            List<CaseManagementIssue> results = caseManagementIssueDAO
                .getIssuesByDemographicSince("111", cutoff);

            // Then - Only 'recent' should match both criteria
            assertThat(results)
                .extracting(CaseManagementIssue::getId)
                .contains(recent.getId())
                .doesNotContain(old.getId(), wrongDemo.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no issues match both criteria")
        void shouldReturnEmptyList_whenNoIssuesMatch() {
            // Given - Create old issue only
            createCaseManagementIssue("111", testIssue1, daysFromNow(-30));
            entityManager.flush();

            // When - Search for recent issues
            List<CaseManagementIssue> results = caseManagementIssueDAO
                .getIssuesByDemographicSince("111", daysFromNow(-1));

            // Then
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("Single parameter queries (baseline)")
    class SingleParamQueries {

        @Test
        @Tag("read")
        @DisplayName("should get issues by demographic")
        void shouldGetIssuesByDemographic() {
            // Given
            createCaseManagementIssue("333", testIssue1);
            createCaseManagementIssue("333", testIssue2);
            createCaseManagementIssue("444", testIssue1);  // Different demographic
            entityManager.flush();

            // When
            List<CaseManagementIssue> results = caseManagementIssueDAO.getIssuesByDemographic("333");

            // Then
            assertThat(results).hasSize(2);
            assertThat(results).allMatch(i -> i.getDemographic_no().equals(333));
        }
    }
}
