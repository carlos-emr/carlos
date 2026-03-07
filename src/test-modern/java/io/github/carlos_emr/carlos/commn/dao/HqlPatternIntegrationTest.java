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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for HQL/JPQL patterns known to break in Hibernate 5→6 migration.
 *
 * <p>These tests pin down current Hibernate 5 behavior for patterns that Hibernate 6
 * changes semantics on. After migration, any test failure immediately identifies
 * which pattern broke and where.</p>
 *
 * <p><b>Patterns tested:</b></p>
 * <ul>
 *   <li>Implicit cross-joins (comma-separated FROM clauses)</li>
 *   <li>Composite ID path navigation (e.g., {@code x.id.fieldName})</li>
 *   <li>String literal comparisons against non-String columns</li>
 *   <li>LIKE operator behavior with and without wildcards</li>
 *   <li>Positional parameter binding (?1, ?2)</li>
 * </ul>
 *
 * @since 2026-03-05
 * @see AbstractDaoImpl
 */
@DisplayName("HQL Pattern Integration Tests (Hibernate 5→6 Breaking Patterns)")
@Tag("integration")
@Tag("dao")
@Tag("hibernate-migration")
@Transactional
public class HqlPatternIntegrationTest extends CarlosTestBase {

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    // Static HQL constants to avoid multi-line concatenation (blocked by SQL safety hook)
    private static final String IMPLICIT_JOIN_HQL =
            "SELECT d FROM Document d, CtlDocument c WHERE c.id.documentNo = d.documentNo AND c.id.module = 'demographic' AND c.id.moduleId = :moduleId";

    // Same query as IMPLICIT_JOIN_HQL — empty-result scenarios are distinguished by parameter values
    private static final String IMPLICIT_JOIN_EMPTY_HQL = IMPLICIT_JOIN_HQL;

    private static final String COMPOSITE_ID_SINGLE_HQL =
            "SELECT x FROM CtlDocument x WHERE x.id.documentNo = :docNo";

    private static final String COMPOSITE_ID_MULTI_HQL =
            "SELECT x FROM CtlDocument x WHERE x.id.documentNo = :docNo AND x.id.module = :module";

    private static final String COMPOSITE_ID_SELECT_HQL =
            "SELECT x.id.moduleId FROM CtlDocument x WHERE x.id.documentNo = :docNo";

    private static final String COMPOSITE_ID_LIKE_HQL =
            "SELECT x FROM CtlDocument x WHERE x.id.module LIKE :module";

    /**
     * Tests for implicit cross-join patterns (comma-separated FROM clauses).
     *
     * <p>Hibernate 6 changes how implicit joins are resolved. The comma syntax
     * {@code FROM A a, B b WHERE a.fk = b.pk} may behave differently than
     * explicit {@code FROM A a JOIN B b ON a.fk = b.pk}.</p>
     */
    @Nested
    @DisplayName("Implicit Cross-Joins (comma FROM syntax)")
    class ImplicitCrossJoins {

        @Test
        @Tag("query")
        @DisplayName("should execute comma-separated FROM with WHERE join condition")
        void shouldExecuteCommaSeparatedFrom_withWhereJoinCondition() {
            // Given — this pattern: FROM Document d, CtlDocument c WHERE c.id.documentNo = d.documentNo
            // Used extensively in DocumentDaoImpl
            Document doc = new Document();
            doc.setDoctype("consult");
            doc.setDoccreator("999998");
            doc.setResponsible("999998");
            doc.setContenttype("text/plain");
            doc.setStatus('A');
            entityManager.persist(doc);
            entityManager.flush();

            CtlDocument ctl = new CtlDocument();
            CtlDocumentPK pk = new CtlDocumentPK();
            pk.setModule("demographic");
            pk.setModuleId(100);
            pk.setDocumentNo(doc.getDocumentNo());
            ctl.setId(pk);
            ctl.setStatus("A");
            entityManager.persist(ctl);
            entityManager.flush();

            // When — comma-separated implicit cross-join pattern
            Query query = entityManager.createQuery(IMPLICIT_JOIN_HQL);
            query.setParameter("moduleId", 100);

            @SuppressWarnings("unchecked")
            List<Document> results = query.getResultList();

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getDocumentNo()).isEqualTo(doc.getDocumentNo());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list for implicit cross-join with no matching rows")
        void shouldReturnEmptyList_forImplicitCrossJoinWithNoMatch() {
            // When — no CtlDocument exists, so cross-join yields nothing
            Query query = entityManager.createQuery(IMPLICIT_JOIN_EMPTY_HQL);
            query.setParameter("moduleId", 99999);

            @SuppressWarnings("unchecked")
            List<Document> results = query.getResultList();

            // Then
            assertThat(results).isEmpty();
        }
    }

    /**
     * Tests for composite ID path navigation in HQL.
     *
     * <p>Hibernate 6 changes how embedded ID properties are resolved in HQL.
     * Patterns like {@code x.id.fieldName} may require different handling.</p>
     */
    @Nested
    @DisplayName("Composite ID Path Navigation")
    class CompositeIdNavigation {

        private Document doc;
        private CtlDocument ctl;

        @BeforeEach
        void setUp() {
            doc = new Document();
            doc.setDoctype("lab");
            doc.setDoccreator("999998");
            doc.setResponsible("999998");
            doc.setContenttype("text/plain");
            doc.setStatus('A');
            entityManager.persist(doc);
            entityManager.flush();

            ctl = new CtlDocument();
            CtlDocumentPK pk = new CtlDocumentPK();
            pk.setModule("demographic");
            pk.setModuleId(200);
            pk.setDocumentNo(doc.getDocumentNo());
            ctl.setId(pk);
            ctl.setStatus("A");
            entityManager.persist(ctl);
            entityManager.flush();
        }

        @Test
        @Tag("query")
        @DisplayName("should resolve single composite ID field in WHERE clause")
        void shouldResolveSingleCompositeIdField_inWhereClause() {
            // When — pattern from CtlDocumentDaoImpl: x.id.documentNo = ?1
            Query query = entityManager.createQuery(COMPOSITE_ID_SINGLE_HQL);
            query.setParameter("docNo", doc.getDocumentNo());

            @SuppressWarnings("unchecked")
            List<CtlDocument> results = query.getResultList();

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId().getModule()).isEqualTo("demographic");
        }

        @Test
        @Tag("query")
        @DisplayName("should resolve multiple composite ID fields in WHERE clause")
        void shouldResolveMultipleCompositeIdFields_inWhereClause() {
            // When — pattern: x.id.documentNo = ?1 AND x.id.module = ?2
            Query query = entityManager.createQuery(COMPOSITE_ID_MULTI_HQL);
            query.setParameter("docNo", doc.getDocumentNo());
            query.setParameter("module", "demographic");

            @SuppressWarnings("unchecked")
            List<CtlDocument> results = query.getResultList();

            // Then
            assertThat(results).hasSize(1);
        }

        @Test
        @Tag("query")
        @DisplayName("should access composite ID fields in SELECT clause")
        void shouldAccessCompositeIdFields_inSelectClause() {
            // When — pattern: SELECT x.id.moduleId FROM CtlDocument x WHERE ...
            Query query = entityManager.createQuery(COMPOSITE_ID_SELECT_HQL);
            query.setParameter("docNo", doc.getDocumentNo());

            @SuppressWarnings("unchecked")
            List<Integer> results = query.getResultList();

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.get(0)).isEqualTo(200);
        }

        @Test
        @Tag("query")
        @DisplayName("should use composite ID field with LIKE operator")
        void shouldUseCompositeIdField_withLikeOperator() {
            // When — pattern from DocumentDaoImpl: c.id.module LIKE 'demographic'
            Query query = entityManager.createQuery(COMPOSITE_ID_LIKE_HQL);
            query.setParameter("module", "demographic");

            @SuppressWarnings("unchecked")
            List<CtlDocument> results = query.getResultList();

            // Then
            assertThat(results).isNotEmpty();
        }
    }

    /**
     * Tests for string literal comparisons against typed columns.
     *
     * <p>Hibernate 6 enforces stricter type checking. Comparing string literals
     * like {@code 'C'} or {@code '1'} against char/boolean/integer columns may fail.</p>
     */
    @Nested
    @DisplayName("String-to-Type Coercion")
    class StringToTypeCoercion {

        @Test
        @Tag("query")
        @DisplayName("should compare string literal against char status column")
        void shouldCompareStringLiteral_againstCharStatusColumn() {
            // Given — Appointment.status is a String column, compared to string literal 'C'
            // Pattern from OscarAppointmentDaoImpl: a.status <> 'C'
            Appointment appt = new Appointment();
            appt.setProviderNo("999998");
            appt.setDemographicNo(1);
            appt.setAppointmentDate(new Date());
            appt.setStartTime(new Date());
            appt.setEndTime(new Date());
            appt.setStatus("t");
            appt.setName("Test Patient");
            entityManager.persist(appt);
            entityManager.flush();

            // When — string literal comparison
            Query query = entityManager.createQuery("SELECT a FROM Appointment a WHERE a.status <> 'C'");

            @SuppressWarnings("unchecked")
            List<Appointment> results = query.getResultList();

            // Then
            assertThat(results).isNotEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should compare boolean column with boolean literal in HQL")
        void shouldCompareBooleanColumn_withBooleanLiteralInHql() {
            // Given
            Facility f = new Facility();
            f.setName("Bool Test");
            f.setDisabled(false);
            entityManager.persist(f);
            entityManager.flush();

            // When — boolean comparison (Hibernate 6 is stricter about boolean types)
            Query query = entityManager.createQuery("SELECT f FROM Facility f WHERE f.disabled = false");

            @SuppressWarnings("unchecked")
            List<Facility> results = query.getResultList();

            // Then
            assertThat(results).isNotEmpty();
        }
    }

    /**
     * Tests for LIKE operator behavior with and without wildcard characters.
     *
     * <p>HQL LIKE does NOT auto-add {@code %} wildcards. Without wildcards,
     * LIKE behaves identically to {@code =}. This tests both behaviors.</p>
     */
    @Nested
    @DisplayName("LIKE Operator Behavior")
    class LikeOperatorBehavior {

        @BeforeEach
        void setUp() {
            Facility f1 = new Facility();
            f1.setName("Alpha Clinic");
            f1.setDescription("First");
            entityManager.persist(f1);

            Facility f2 = new Facility();
            f2.setName("Alpha Medical Center");
            f2.setDescription("Second");
            entityManager.persist(f2);

            Facility f3 = new Facility();
            f3.setName("Beta Hospital");
            f3.setDescription("Third");
            entityManager.persist(f3);

            entityManager.flush();
        }

        @Test
        @Tag("query")
        @DisplayName("should match exact string when LIKE used without wildcards")
        void shouldMatchExactString_whenLikeUsedWithoutWildcards() {
            // When — LIKE without % behaves as = (exact match)
            Query query = entityManager.createQuery("SELECT f FROM Facility f WHERE f.name LIKE :name");
            query.setParameter("name", "Alpha Clinic");

            @SuppressWarnings("unchecked")
            List<Facility> results = query.getResultList();

            // Then — only exact match
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getName()).isEqualTo("Alpha Clinic");
        }

        @Test
        @Tag("query")
        @DisplayName("should match partial string when LIKE used with both wildcards")
        void shouldMatchPartialString_whenLikeUsedWithBothWildcards() {
            // When
            Query query = entityManager.createQuery("SELECT f FROM Facility f WHERE f.name LIKE :name");
            query.setParameter("name", "%Alpha%");

            @SuppressWarnings("unchecked")
            List<Facility> results = query.getResultList();

            // Then — should match both Alpha entries
            assertThat(results).hasSize(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should return no matches for LIKE without wildcard on partial value")
        void shouldReturnNoMatches_forLikeWithoutWildcardOnPartialValue() {
            // When — LIKE 'Alpha' without % should NOT match 'Alpha Clinic'
            Query query = entityManager.createQuery("SELECT f FROM Facility f WHERE f.name LIKE :name");
            query.setParameter("name", "Alpha");

            @SuppressWarnings("unchecked")
            List<Facility> results = query.getResultList();

            // Then
            assertThat(results).isEmpty();
        }
    }

    /**
     * Tests for positional parameter binding (?1, ?2, ...) vs named parameters.
     *
     * <p>Hibernate 6 deprecates positional parameters in favor of named parameters.
     * These tests verify positional parameters still work.</p>
     */
    @Nested
    @DisplayName("Positional Parameter Binding")
    class PositionalParameterBinding {

        @Test
        @Tag("query")
        @DisplayName("should bind single positional parameter")
        void shouldBindSinglePositionalParameter() {
            // Given
            Facility f = new Facility();
            f.setName("Positional Test");
            entityManager.persist(f);
            entityManager.flush();

            // When
            Query query = entityManager.createQuery("SELECT f FROM Facility f WHERE f.name = ?1");
            query.setParameter(1, "Positional Test");

            @SuppressWarnings("unchecked")
            List<Facility> results = query.getResultList();

            // Then
            assertThat(results).hasSize(1);
        }

        @Test
        @Tag("query")
        @DisplayName("should bind multiple positional parameters")
        void shouldBindMultiplePositionalParameters() {
            // Given
            Facility f = new Facility();
            f.setName("Multi Param");
            f.setDescription("Test Description");
            entityManager.persist(f);
            entityManager.flush();

            // When
            Query query = entityManager.createQuery("SELECT f FROM Facility f WHERE f.name = ?1 AND f.description = ?2");
            query.setParameter(1, "Multi Param");
            query.setParameter(2, "Test Description");

            @SuppressWarnings("unchecked")
            List<Facility> results = query.getResultList();

            // Then
            assertThat(results).hasSize(1);
        }
    }
}
