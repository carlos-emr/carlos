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
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

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
public class CaseManagementIssueDAOIntegrationTest extends CarlosTestBase {

    @Autowired
    @Qualifier("CaseManagementIssueDAO")
    private CaseManagementIssueDAO caseManagementIssueDAO;

    @Autowired
    @Qualifier("IssueDAO")
    private IssueDAO issueDAO;

    @Autowired
    @Qualifier("CaseManagementNoteDAO")
    private CaseManagementNoteDAO caseManagementNoteDAO;

    private Issue testIssue1;
    private Issue testIssue2;

    @BeforeEach
    void setUp() {
        testIssue1 = new Issue();
        testIssue1.setCode("TEST001");
        testIssue1.setDescription("Test Issue 1");
        testIssue1.setRole("doctor");
        testIssue1.setType("system");
        hibernateTemplate.save(testIssue1);

        testIssue2 = new Issue();
        testIssue2.setCode("TEST002");
        testIssue2.setDescription("Test Issue 2");
        testIssue2.setRole("doctor");
        testIssue2.setType("system");
        hibernateTemplate.save(testIssue2);

        hibernateTemplate.flush();
    }

    private CaseManagementIssue createCaseManagementIssue(String demographicNo, Issue issue) {
        CaseManagementIssue cmi = new CaseManagementIssue();
        cmi.setDemographic_no(Integer.valueOf(demographicNo));
        cmi.setIssue_id(issue.getId());
        cmi.setAcute(false);
        cmi.setCertain(true);
        cmi.setMajor(false);
        cmi.setResolved(false);
        cmi.setUpdate_date(new Date());
        hibernateTemplate.save(cmi);
        hibernateTemplate.flush();
        return cmi;
    }

    /**
     * Creates a CaseManagementIssue with a specific update date.
     * Uses hibernateTemplate.save() (persist) to properly populate the ID,
     * then sets the desired update_date and flushes.
     */
    private CaseManagementIssue createCaseManagementIssue(String demographicNo, Issue issue, Date updateDate) {
        CaseManagementIssue cmi = new CaseManagementIssue();
        cmi.setDemographic_no(Integer.valueOf(demographicNo));
        cmi.setIssue_id(issue.getId());
        cmi.setAcute(false);
        cmi.setCertain(true);
        cmi.setMajor(false);
        cmi.setResolved(false);
        cmi.setUpdate_date(updateDate);
        hibernateTemplate.save(cmi);
        hibernateTemplate.flush();
        return cmi;
    }

    private CaseManagementIssue createResolvedIssue(String demographicNo, Issue issue) {
        CaseManagementIssue cmi = new CaseManagementIssue();
        cmi.setDemographic_no(Integer.valueOf(demographicNo));
        cmi.setIssue_id(issue.getId());
        cmi.setAcute(false);
        cmi.setCertain(true);
        cmi.setMajor(false);
        cmi.setResolved(true);
        cmi.setUpdate_date(new Date());
        hibernateTemplate.save(cmi);
        hibernateTemplate.flush();
        return cmi;
    }

    private Date daysFromNow(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, days);
        return cal.getTime();
    }

    /** Tests for CRUD operations. */
    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist case management issue with valid data")
        void shouldPersistIssue_whenValidDataProvided() {
            // Given
            CaseManagementIssue cmi = new CaseManagementIssue();
            cmi.setDemographic_no(600);
            cmi.setIssue_id(testIssue1.getId());
            cmi.setAcute(true);
            cmi.setCertain(false);
            cmi.setMajor(true);
            cmi.setResolved(false);
            cmi.setUpdate_date(new Date());

            // When
            hibernateTemplate.save(cmi);
            hibernateTemplate.flush();

            // Then
            assertThat(cmi.getId()).isPositive();
        }

        @Test
        @Tag("delete")
        @DisplayName("should delete issue by entity reference")
        void shouldDeleteIssue_whenValidIssueProvided() {
            // Given
            CaseManagementIssue cmi = createCaseManagementIssue("601", testIssue1);
            hibernateTemplate.flush();
            Long savedId = cmi.getId();

            // When
            caseManagementIssueDAO.deleteIssueById(cmi);
            hibernateTemplate.flush();

            // Then - verify via HibernateTemplate (same persistence context as DAO)
            @SuppressWarnings("unchecked")
            List<CaseManagementIssue> results = (List<CaseManagementIssue>) hibernateTemplate
                .find("from CaseManagementIssue where id = ?1", savedId);
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("create")
        @DisplayName("should batch save issue list")
        void shouldBatchSave_whenIssueListProvided() {
            // Given
            List<CaseManagementIssue> issues = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                CaseManagementIssue cmi = new CaseManagementIssue();
                cmi.setDemographic_no(700 + i);
                cmi.setIssue_id(testIssue1.getId());
                cmi.setAcute(false);
                cmi.setCertain(true);
                cmi.setMajor(false);
                cmi.setResolved(false);
                cmi.setUpdate_date(new Date());
                issues.add(cmi);
            }

            // When
            caseManagementIssueDAO.saveAndUpdateCaseIssues(issues);
            hibernateTemplate.flush();

            // Then - each demographic should have exactly 1 issue persisted
            assertThat(caseManagementIssueDAO.getIssuesByDemographic("700")).hasSize(1);
            assertThat(caseManagementIssueDAO.getIssuesByDemographic("701")).hasSize(1);
            assertThat(caseManagementIssueDAO.getIssuesByDemographic("702")).hasSize(1);
            // Verify all 3 issues received generated IDs
            assertThat(issues).allSatisfy(i -> assertThat(i.getId()).isPositive());
        }
    }

    /** Tests for getIssuebyId (2 params: demo, id). */
    @Nested
    @DisplayName("getIssuebyId (2 params: demo, id)")
    class GetIssueById {

        @Test
        @Tag("query")
        @DisplayName("should find issue when both demographic and issue ID match")
        void shouldFindIssue_whenBothDemoAndIdMatch() {
            // Given
            createCaseManagementIssue("111", testIssue1);
            createCaseManagementIssue("222", testIssue1);
            createCaseManagementIssue("111", testIssue2);
            hibernateTemplate.flush();

            // When
            CaseManagementIssue found = caseManagementIssueDAO.getIssuebyId(
                "111", String.valueOf(testIssue1.getId()));

            // Then
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
            hibernateTemplate.flush();

            // When
            CaseManagementIssue found = caseManagementIssueDAO.getIssuebyId(
                "999", String.valueOf(testIssue1.getId()));

            // Then
            assertThat(found).isNull();
        }
    }

    /** Tests for getIssuebyIssueCode (2 params: demo, issueCode). */
    @Nested
    @DisplayName("getIssuebyIssueCode (2 params: demo, issueCode)")
    class GetIssueByIssueCode {

        @Test
        @Tag("query")
        @DisplayName("should find issue when both demographic and issue code match")
        void shouldFindIssue_whenBothDemoAndCodeMatch() {
            // Given
            CaseManagementIssue cmi = createCaseManagementIssue("111", testIssue1);
            createCaseManagementIssue("222", testIssue1);
            createCaseManagementIssue("111", testIssue2);
            hibernateTemplate.flush();

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
            hibernateTemplate.flush();

            // When
            CaseManagementIssue found = caseManagementIssueDAO.getIssuebyIssueCode("111", "NONEXISTENT");

            // Then
            assertThat(found).isNull();
        }
    }

    /** Tests for getIssuesByDemographicSince (2 params). */
    @Nested
    @DisplayName("getIssuesByDemographicSince (2 params)")
    class GetIssuesByDemographicSince {

        @Test
        @Tag("query")
        @DisplayName("should filter by both demographic and date")
        void shouldFilter_byBothDemoAndDate() {
            // Given
            Date cutoff = daysFromNow(-5);
            CaseManagementIssue recent = createCaseManagementIssue("50111", testIssue1, daysFromNow(-2));
            CaseManagementIssue old = createCaseManagementIssue("50111", testIssue2, daysFromNow(-10));
            CaseManagementIssue wrongDemo = createCaseManagementIssue("50222", testIssue1, daysFromNow(-2));
            hibernateTemplate.flush();

            // When
            List<CaseManagementIssue> results = caseManagementIssueDAO
                .getIssuesByDemographicSince("50111", cutoff);

            // Then
            assertThat(results)
                .extracting(CaseManagementIssue::getId)
                .contains(recent.getId())
                .doesNotContain(old.getId(), wrongDemo.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no issues match")
        void shouldReturnEmptyList_whenNoIssuesMatch() {
            // Given
            createCaseManagementIssue("50333", testIssue1, daysFromNow(-30));
            hibernateTemplate.flush();

            // When
            List<CaseManagementIssue> results = caseManagementIssueDAO
                .getIssuesByDemographicSince("50333", daysFromNow(-1));

            // Then
            assertThat(results).isEmpty();
        }
    }

    /** Tests for query operations including resolved/unresolved filtering. */
    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("read")
        @DisplayName("should get issues by demographic")
        void shouldGetIssues_byDemographic() {
            // Given
            createCaseManagementIssue("333", testIssue1);
            createCaseManagementIssue("333", testIssue2);
            createCaseManagementIssue("444", testIssue1);
            hibernateTemplate.flush();

            // When
            List<CaseManagementIssue> results = caseManagementIssueDAO.getIssuesByDemographic("333");

            // Then
            assertThat(results).hasSize(2);
            assertThat(results).allMatch(i -> i.getDemographic_no().equals(333));
        }

        @Test
        @Tag("filter")
        @DisplayName("should filter unresolved issues by boolean parameter")
        void shouldFilterUnresolvedIssues_byBooleanParameter() {
            // Given
            createCaseManagementIssue("800", testIssue1);
            createResolvedIssue("800", testIssue2);
            hibernateTemplate.flush();

            // When
            List<CaseManagementIssue> unresolvedResults = caseManagementIssueDAO
                .getIssuesByDemographicOrderActive(800, false);

            // Then
            assertThat(unresolvedResults)
                .isNotEmpty()
                .allMatch(i -> !i.isResolved());
        }

        @Test
        @Tag("filter")
        @DisplayName("should filter resolved issues by boolean parameter")
        void shouldFilterResolvedIssues_byBooleanParameter() {
            // Given
            createCaseManagementIssue("801", testIssue1);
            createResolvedIssue("801", testIssue2);
            hibernateTemplate.flush();

            // When
            List<CaseManagementIssue> resolvedResults = caseManagementIssueDAO
                .getIssuesByDemographicOrderActive(801, true);

            // Then
            assertThat(resolvedResults)
                .isNotEmpty()
                .allMatch(CaseManagementIssue::isResolved);
        }

        @Test
        @Tag("read")
        @DisplayName("should return all certain issues when queried")
        void shouldReturnAllCertainIssues_whenQueried() {
            // Given
            CaseManagementIssue cmi = createCaseManagementIssue("900", testIssue1);
            hibernateTemplate.flush();

            // When
            List<CaseManagementIssue> results = caseManagementIssueDAO.getAllCertainIssues();

            // Then
            assertThat(results)
                .isNotEmpty()
                .extracting(CaseManagementIssue::getId)
                .contains(cmi.getId());
        }

    }

    /**
     * Tests for getIssueByCmnId(Integer cmnIssueId).
     *
     * <p>This method selects the {@code issue} property (many-to-one to Issue entity)
     * from a CaseManagementIssue by its primary key. PR #89 converts the positional
     * parameter from ?0 to ?1.</p>
     */
    @Nested
    @DisplayName("getIssueByCmnId (1 param: cmnIssueId)")
    class GetIssueByCmnId {

        @Test
        @Tag("query")
        @DisplayName("should return Issue when CMI ID exists")
        void shouldReturnIssue_whenCmnIdExists() {
            // Given
            CaseManagementIssue cmi = createCaseManagementIssue("20100", testIssue1);
            hibernateTemplate.flush();

            // When
            Issue found = caseManagementIssueDAO.getIssueByCmnId(cmi.getId().intValue());

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(testIssue1.getId());
            assertThat(found.getCode()).isEqualTo("TEST001");
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when CMI ID does not exist")
        void shouldReturnNull_whenCmnIdDoesNotExist() {
            // When
            Issue found = caseManagementIssueDAO.getIssueByCmnId(999999);

            // Then
            assertThat(found).isNull();
        }
    }

    /**
     * Tests for getIssuesByNote(Integer noteId, Boolean resolved).
     *
     * <p>This method navigates the CaseManagementIssue → notes collection
     * (via casemgmt_issue_notes join table) to find issues linked to a given note.
     * The current HQL uses {@code cmi.notes.id} which is an illegal dereference of a
     * collection property in Hibernate 5 — you cannot navigate through a collection
     * to access element properties without an explicit join. This causes a QueryException.
     * PR #89 rewrites the HQL to use proper join syntax.</p>
     */
    @Nested
    @DisplayName("getIssuesByNote (2 params: noteId, resolved)")
    class GetIssuesByNote {

        private CaseManagementNote createAndLinkNote(String demoNo, CaseManagementIssue... cmis) {
            CaseManagementNote note = new CaseManagementNote();
            note.setDemographic_no(demoNo);
            note.setNote("Test note for issue link");
            note.setProviderNo("999998");
            note.setUuid(UUID.randomUUID().toString());
            note.setUpdate_date(new Date());
            note.setObservation_date(new Date());
            note.setSigned(false);
            note.setArchived(false);
            note.setLocked(false);
            caseManagementNoteDAO.saveNote(note);

            for (CaseManagementIssue cmi : cmis) {
                note.getIssues().add(cmi);
            }
            caseManagementNoteDAO.updateNote(note);
            hibernateTemplate.flush();
            return note;
        }

        @Test
        @Tag("query")
        @DisplayName("should throw IllegalArgumentException due to illegal collection dereference")
        void shouldThrowIllegalArgumentException_whenDereferencingCollectionProperty() {
            // Given — create a note linked to testIssue1 via CMI
            CaseManagementIssue cmi = createCaseManagementIssue("20200", testIssue1);
            hibernateTemplate.flush();
            CaseManagementNote note = createAndLinkNote("20200", cmi);

            // When/Then — the HQL "cmi.notes.id" illegally dereferences a collection
            // property. Hibernate cannot navigate through a Set to access element
            // properties without an explicit join. This documents the pre-change bug.
            // Hibernate 5's ExceptionConverterImpl.convert() wraps QueryException as
            // IllegalArgumentException (JPA convention) rather than HibernateQueryException.
            assertThatThrownBy(() ->
                caseManagementIssueDAO.getIssuesByNote(note.getId().intValue(), null)
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("filter")
        @DisplayName("should throw IllegalArgumentException with resolved filter due to same collection bug")
        void shouldThrowIllegalArgumentException_whenFilteringByResolved() {
            // Given — create resolved + unresolved CMIs linked to same note
            CaseManagementIssue unresolvedCmi = createCaseManagementIssue("20300", testIssue1);
            CaseManagementIssue resolvedCmi = createResolvedIssue("20300", testIssue2);
            hibernateTemplate.flush();
            CaseManagementNote note = createAndLinkNote("20300", unresolvedCmi, resolvedCmi);

            // When/Then — same collection dereference bug regardless of resolved filter
            // Hibernate 5's ExceptionConverterImpl.convert() wraps QueryException as
            // IllegalArgumentException (JPA convention) rather than HibernateQueryException.
            assertThatThrownBy(() ->
                caseManagementIssueDAO.getIssuesByNote(note.getId().intValue(), false)
            ).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
