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

import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for query return type behavior that changes between Hibernate 5 and 6.
 *
 * <p>Hibernate 6 changes the return types of aggregate functions and native SQL queries:
 * <ul>
 *   <li>{@code COUNT()} may return {@code Long} instead of {@code BigInteger} in native SQL</li>
 *   <li>{@code SUM()} return type depends on column type (Long for int columns, BigDecimal for decimal)</li>
 *   <li>{@code getSingleResult()} for native COUNT may return different Number subtypes</li>
 *   <li>{@code Object[]} element types may differ for multi-column SELECTs</li>
 * </ul>
 *
 * <p>These tests pin the current Hibernate 5 behavior so any changes after migration are detected.</p>
 *
 * @since 2026-03-05
 */
@DisplayName("Query Return Type Integration Tests (Hibernate 5→6 Type Changes)")
@Tag("integration")
@Tag("dao")
@Tag("hibernate-migration")
@Transactional
public class QueryReturnTypeIntegrationTest extends CarlosTestBase {

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        // Create several facilities for aggregate tests
        for (int i = 0; i < 5; i++) {
            Facility f = new Facility();
            f.setName("ReturnType Facility " + i);
            f.setDescription("Description " + i);
            f.setDisabled(i % 2 == 0); // Alternating enabled/disabled
            entityManager.persist(f);
        }
        entityManager.flush();
    }

    /** Tests for native SQL COUNT return type */
    @Nested
    @DisplayName("Native SQL COUNT() Return Type")
    class NativeSqlCountReturnType {

        @Test
        @Tag("aggregate")
        @DisplayName("should return Number subtype for native COUNT(*)")
        void shouldReturnNumberSubtype_forNativeCount() {
            // Given — this is the pattern from AbstractDaoImpl.getCountAll()
            Query query = entityManager.createNativeQuery("SELECT COUNT(*) FROM Facility");

            // When
            Object result = query.getSingleResult();

            // Then — Hibernate 5 with H2 returns BigInteger; MySQL may return Long
            // The key assertion: it must be a Number so .intValue() works
            assertThat(result).isInstanceOf(Number.class);
            int count = ((Number) result).intValue();
            assertThat(count).isGreaterThanOrEqualTo(5);
        }

        @Test
        @Tag("aggregate")
        @DisplayName("should cast native COUNT to int via Number.intValue()")
        void shouldCastNativeCount_toIntViaNumberIntValue() {
            // Given — exact pattern from AbstractDaoImpl.getCountAll():
            // ((Number) query.getSingleResult()).intValue()
            Query query = entityManager.createNativeQuery("SELECT COUNT(*) FROM Facility");

            // When — this is the production code pattern
            int result = ((Number) query.getSingleResult()).intValue();

            // Then
            assertThat(result).isGreaterThanOrEqualTo(5);
        }

        @Test
        @Tag("aggregate")
        @DisplayName("should return zero for native COUNT on empty result set")
        void shouldReturnZero_forNativeCountOnEmptyResultSet() {
            // When — count with impossible condition
            Query query = entityManager.createNativeQuery("SELECT COUNT(*) FROM Facility WHERE name = 'IMPOSSIBLE_NAME_12345'");
            int result = ((Number) query.getSingleResult()).intValue();

            // Then
            assertThat(result).isEqualTo(0);
        }
    }

    /** Tests for HQL COUNT return type */
    @Nested
    @DisplayName("HQL COUNT() Return Type")
    class HqlCountReturnType {

        @Test
        @Tag("aggregate")
        @DisplayName("should return Long for HQL COUNT()")
        void shouldReturnLong_forHqlCount() {
            // Given — HQL count pattern used in many DAOs
            Query query = entityManager.createQuery("SELECT COUNT(f) FROM Facility f");

            // When
            Object result = query.getSingleResult();

            // Then — Hibernate 5 HQL COUNT returns Long
            assertThat(result).isInstanceOf(Long.class);
            assertThat(((Long) result)).isGreaterThanOrEqualTo(5L);
        }

        @Test
        @Tag("aggregate")
        @DisplayName("should return Long for HQL COUNT with WHERE clause")
        void shouldReturnLong_forHqlCountWithWhereClause() {
            // When
            Query query = entityManager.createQuery("SELECT COUNT(f) FROM Facility f WHERE f.disabled = false");
            Object result = query.getSingleResult();

            // Then
            assertThat(result).isInstanceOf(Long.class);
        }
    }

    /** Tests for multi-column SELECT returning Object[] */
    @Nested
    @DisplayName("Object[] Return Type Handling")
    class ObjectArrayReturnType {

        @Test
        @Tag("query")
        @DisplayName("should return Object[] for multi-column HQL SELECT")
        void shouldReturnObjectArray_forMultiColumnHqlSelect() {
            // Given — pattern from BillingONCHeader1DaoImpl:
            // SELECT b.visitType, count(b) FROM ... GROUP BY b.visitType
            // We simulate with Facility: SELECT f.disabled, COUNT(f) FROM Facility f GROUP BY f.disabled
            Query query = entityManager.createQuery("SELECT f.disabled, COUNT(f) FROM Facility f GROUP BY f.disabled");

            // When
            @SuppressWarnings("unchecked")
            List<Object[]> results = query.getResultList();

            // Then
            assertThat(results).isNotEmpty();
            for (Object[] row : results) {
                assertThat(row).hasSize(2);
                // First element: Boolean (disabled flag)
                assertThat(row[0]).isInstanceOf(Boolean.class);
                // Second element: Long (count)
                assertThat(row[1]).isInstanceOf(Long.class);
            }
        }

        @Test
        @Tag("query")
        @DisplayName("should return Object[] for native SQL multi-column SELECT")
        void shouldReturnObjectArray_forNativeSqlMultiColumnSelect() {
            // Given — native SQL returns Object[] with database-native types
            Query query = entityManager.createNativeQuery("SELECT id, name, disabled FROM Facility");

            // When
            @SuppressWarnings("unchecked")
            List<Object[]> results = query.getResultList();

            // Then
            assertThat(results).isNotEmpty();
            for (Object[] row : results) {
                assertThat(row).hasSize(3);
                // ID is Number (may be Integer or BigInteger depending on driver)
                assertThat(row[0]).isInstanceOf(Number.class);
                // Name is String
                assertThat(row[1]).isInstanceOf(String.class);
                // Disabled is Boolean in H2, may be Integer/Short in MySQL
                assertThat(row[2]).isNotNull();
            }
        }

        @Test
        @Tag("query")
        @DisplayName("should return Document and CtlDocument in Object[] for cross-entity SELECT")
        void shouldReturnDocumentAndCtlDocument_inObjectArrayForCrossEntitySelect() {
            // Given — pattern from DocumentDaoImpl: SELECT d, c FROM Document d, CtlDocument c WHERE ...
            Document doc = new Document();
            doc.setDoctype("consult");
            doc.setDocdesc("");
            doc.setDocfilename("");
            doc.setDoccreator("999998");
            doc.setResponsible("999998");
            doc.setContenttype("text/plain");
            doc.setStatus('A');
            entityManager.persist(doc);
            entityManager.flush();

            CtlDocument ctl = new CtlDocument();
            CtlDocumentPK pk = new CtlDocumentPK();
            pk.setModule("demographic");
            pk.setModuleId(300);
            pk.setDocumentNo(doc.getDocumentNo());
            ctl.setId(pk);
            ctl.setStatus("A");
            entityManager.persist(ctl);
            entityManager.flush();

            // When — the production code pattern
            String hql = "SELECT d, c FROM Document d, CtlDocument c WHERE c.id.documentNo = d.documentNo AND c.id.moduleId = :moduleId";
            Query query = entityManager.createQuery(hql);
            query.setParameter("moduleId", 300);

            @SuppressWarnings("unchecked")
            List<Object[]> results = query.getResultList();

            // Then
            assertThat(results).hasSize(1);
            Object[] row = results.get(0);
            assertThat(row).hasSize(2);
            assertThat(row[0]).isInstanceOf(Document.class);
            assertThat(row[1]).isInstanceOf(CtlDocument.class);
        }
    }

    /** Tests for single-value query return types */
    @Nested
    @DisplayName("Single Value Return Types")
    class SingleValueReturnTypes {

        @Test
        @Tag("aggregate")
        @DisplayName("should return correct type for MAX() on Integer column")
        void shouldReturnCorrectType_forMaxOnIntegerColumn() {
            // When — MAX on integer ID column
            Query query = entityManager.createQuery("SELECT MAX(f.id) FROM Facility f");
            Object result = query.getSingleResult();

            // Then — Hibernate 5 returns Integer for MAX on Integer column
            assertThat(result).isInstanceOf(Integer.class);
        }

        @Test
        @Tag("aggregate")
        @DisplayName("should return correct type for MIN() on Integer column")
        void shouldReturnCorrectType_forMinOnIntegerColumn() {
            // When
            Query query = entityManager.createQuery("SELECT MIN(f.id) FROM Facility f");
            Object result = query.getSingleResult();

            // Then
            assertThat(result).isInstanceOf(Integer.class);
        }

        @Test
        @Tag("aggregate")
        @DisplayName("should return null for MAX/MIN on empty result set")
        void shouldReturnNull_forMaxMinOnEmptyResultSet() {
            // When — no matching rows
            Query query = entityManager.createQuery("SELECT MAX(f.id) FROM Facility f WHERE f.name = 'IMPOSSIBLE'");
            Object result = query.getSingleResult();

            // Then — aggregate on empty set returns null
            assertThat(result).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should return DISTINCT results with correct count")
        void shouldReturnDistinctResults_withCorrectCount() {
            // When — DISTINCT pattern used in many DAOs
            Query query = entityManager.createQuery("SELECT DISTINCT f.disabled FROM Facility f");

            @SuppressWarnings("unchecked")
            List<Boolean> results = query.getResultList();

            // Then — should have true and false
            assertThat(results).hasSize(2);
            assertThat(results).containsExactlyInAnyOrder(true, false);
        }
    }

    /** Tests for native SQL with table name annotation resolution */
    @Nested
    @DisplayName("Native SQL Table Name Resolution")
    class NativeSqlTableNameResolution {

        @Test
        @Tag("query")
        @DisplayName("should use @Table name for native SQL when entity has annotation")
        void shouldUseTableName_forNativeSqlWhenEntityHasAnnotation() {
            // Given — Document has @Table(name = "document")
            Document doc = new Document();
            doc.setDoctype("lab");
            doc.setDocdesc("");
            doc.setDocfilename("");
            doc.setDoccreator("999998");
            doc.setResponsible("999998");
            doc.setContenttype("text/plain");
            doc.setStatus('A');
            entityManager.persist(doc);
            entityManager.flush();

            // When — native SQL using actual table name
            Query query = entityManager.createNativeQuery("SELECT COUNT(*) FROM document");
            int count = ((Number) query.getSingleResult()).intValue();

            // Then
            assertThat(count).isEqualTo(1);
        }

        @Test
        @Tag("query")
        @DisplayName("should use simple class name for native SQL when no @Table annotation")
        void shouldUseSimpleClassName_forNativeSqlWhenNoTableAnnotation() {
            // Given — Facility has @Entity but no @Table(name=...) — defaults to "Facility"
            Query query = entityManager.createNativeQuery("SELECT COUNT(*) FROM Facility");
            int count = ((Number) query.getSingleResult()).intValue();

            // Then
            assertThat(count).isGreaterThanOrEqualTo(5);
        }
    }
}
