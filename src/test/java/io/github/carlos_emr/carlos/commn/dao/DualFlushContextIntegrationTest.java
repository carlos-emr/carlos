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

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import io.github.carlos_emr.carlos.test.base.HibernateTemplate;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for the dual persistence context architecture in CARLOS EMR.
 *
 * <p>CARLOS EMR uses a permanent mixed-persistence architecture:
 * <ul>
 *   <li><b>JPA EntityManager</b> — used by DAOs extending {@link AbstractDaoImpl}</li>
 *   <li><b>Hibernate Session</b> — used by DAOs extending {@code HibernateDaoSupport}</li>
 * </ul>
 * Both share the same JDBC connection via {@code TransactionAwareDataSourceProxy},
 * but have separate persistence contexts. This means:
 * <ul>
 *   <li>{@code entityManager.flush()} only flushes JPA-managed entities</li>
 *   <li>{@code hibernateTemplate.flush()} only flushes Hibernate Session entities</li>
 *   <li>Data written via one context is visible to the other after flush (shared connection)</li>
 * </ul>
 *
 * <p>Hibernate 6 may change how these dual contexts interact, particularly around
 * transaction synchronization, auto-flush timing, and Session lifecycle.</p>
 *
 * @since 2026-03-05
 * @see CarlosTestBase
 */
@DisplayName("Dual Flush Context Integration Tests (EntityManager vs Session)")
@Tag("integration")
@Tag("dao")
@Tag("hibernate-migration")
@Transactional
public class DualFlushContextIntegrationTest extends CarlosTestBase {

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @Autowired
    private HibernateTemplate hibernateTemplate;

    /**
     * Tests that data written via JPA EntityManager is visible to Hibernate Session queries.
     */
    @Nested
    @DisplayName("JPA → Hibernate Session Visibility")
    class JpaToHibernateVisibility {

        @Test
        @Tag("create")
        @Tag("read")
        @DisplayName("should see JPA-persisted entity via Hibernate Session after JPA flush")
        void shouldSeeJpaPersistedEntity_viaHibernateSessionAfterJpaFlush() {
            // Given — persist via JPA EntityManager
            Facility facility = new Facility();
            facility.setName("JPA Persisted Facility");
            facility.setDescription("Testing cross-context visibility");
            entityManager.persist(facility);
            entityManager.flush();

            // When — query via Hibernate Session (native SQL through HibernateTemplate)
            @SuppressWarnings("unchecked")
            List<Facility> results = (List<Facility>) hibernateTemplate.find(
                    "FROM Facility f WHERE f.name = ?1", "JPA Persisted Facility");

            // Then — Hibernate Session should see the JPA-written data
            assertThat(results).isNotEmpty();
            assertThat(results.get(0).getName()).isEqualTo("JPA Persisted Facility");
        }

        @Test
        @Tag("update")
        @Tag("read")
        @DisplayName("should see JPA-updated entity via Hibernate Session after JPA flush")
        void shouldSeeJpaUpdatedEntity_viaHibernateSessionAfterJpaFlush() {
            // Given — persist and flush initial state
            Facility facility = new Facility();
            facility.setName("Original Name");
            entityManager.persist(facility);
            entityManager.flush();

            // Update via JPA
            facility.setName("Updated Via JPA");
            entityManager.flush();

            // When — query via Hibernate Session
            @SuppressWarnings("unchecked")
            List<Facility> results = (List<Facility>) hibernateTemplate.find(
                    "FROM Facility f WHERE f.name = ?1", "Updated Via JPA");

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getName()).isEqualTo("Updated Via JPA");
        }
    }

    /**
     * Tests that data written via Hibernate Session is visible to JPA EntityManager queries.
     */
    @Nested
    @DisplayName("Hibernate Session → JPA Visibility")
    class HibernateToJpaVisibility {

        @Test
        @Tag("create")
        @Tag("read")
        @DisplayName("should see Hibernate-saved HBM entity via JPA native SQL after Hibernate flush")
        void shouldSeeHibernateSavedEntity_viaJpaNativeSqlAfterHibernateFlush() {
            // Given — Provider is HBM-mapped, saved via HibernateTemplate
            Provider provider = new Provider();
            provider.setProviderNo("DFC01");
            provider.setLastName("DualFlush");
            provider.setFirstName("Test");
            provider.setProviderType("doctor");
            provider.setSpecialty("GP");
            provider.setSex("");
            provider.setStatus("1");
            hibernateTemplate.save(provider);
            hibernateTemplate.flush();

            // When — query via JPA EntityManager native SQL
            Query query = entityManager.createNativeQuery("SELECT provider_no FROM provider WHERE last_name = :lastName");
            query.setParameter("lastName", "DualFlush");

            @SuppressWarnings("unchecked")
            List<Object> results = query.getResultList();

            // Then — JPA should see the Hibernate-written data
            assertThat(results).isNotEmpty();
            assertThat(results.get(0).toString()).isEqualTo("DFC01");
        }

        @Test
        @Tag("create")
        @Tag("read")
        @DisplayName("should see Hibernate-saved Demographic via JPA native SQL after flush")
        void shouldSeeHibernateSavedDemographic_viaJpaNativeSqlAfterFlush() {
            // Given — Demographic is HBM-mapped
            Demographic demo = new Demographic();
            demo.setLastName("DualFlushDemo");
            demo.setFirstName("Test");
            demo.setSex("M");
            demo.setProviderNo("999998");
            hibernateTemplate.save(demo);
            hibernateTemplate.flush();

            // When — JPA native SQL query
            Query query = entityManager.createNativeQuery("SELECT demographic_no FROM demographic WHERE last_name = :lastName");
            query.setParameter("lastName", "DualFlushDemo");

            @SuppressWarnings("unchecked")
            List<Object> results = query.getResultList();

            // Then
            assertThat(results).hasSize(1);
            assertThat(((Number) results.get(0)).intValue()).isEqualTo(demo.getDemographicNo());
        }
    }

    /**
     * Tests verifying that entityManager.flush() does NOT flush Hibernate Session writes.
     */
    @Nested
    @DisplayName("Flush Context Isolation")
    class FlushContextIsolation {

        @Test
        @Tag("create")
        @Tag("read")
        @DisplayName("should NOT see Hibernate-saved entity via JPA before Hibernate flush")
        void shouldNotSeeHibernateSavedEntity_viaJpaBeforeHibernateFlush() {
            // Given — save via Hibernate but do NOT flush Hibernate Session
            Provider provider = new Provider();
            provider.setProviderNo("DFC02");
            provider.setLastName("NotFlushed");
            provider.setFirstName("Test");
            provider.setProviderType("doctor");
            provider.setSpecialty("GP");
            provider.setSex("M");
            provider.setStatus("1");
            hibernateTemplate.save(provider);
            // intentionally NOT calling hibernateTemplate.flush()

            // Flush only the JPA context
            entityManager.flush();

            // When — native SQL query via JPA (reads from DB, not from Hibernate Session)
            Query query = entityManager.createNativeQuery("SELECT COUNT(*) FROM provider WHERE provider_no = :provNo");
            query.setParameter("provNo", "DFC02");
            int count = ((Number) query.getSingleResult()).intValue();

            // Then — in Hibernate 5 with our dual-context setup, a save via Hibernate
            // Session without explicit hibernateTemplate.flush() is NOT visible via JPA
            // native SQL, even after entityManager.flush(). This pins the current behavior
            // so Hibernate 6 migration will surface any change in auto-flush semantics.
            assertThat(count).isEqualTo(0);
        }
    }

    /**
     * Tests for mixed persistence operations within the same transaction.
     */
    @Nested
    @DisplayName("Mixed Persistence Operations")
    class MixedPersistenceOperations {

        @Test
        @Tag("create")
        @Tag("read")
        @DisplayName("should handle JPA persist followed by Hibernate query in same transaction")
        void shouldHandleJpaPersistFollowedByHibernateQuery_inSameTransaction() {
            // Given — persist via JPA
            Facility facility = new Facility();
            facility.setName("Mixed Ops Test");
            facility.setDescription("JPA then Hibernate");
            entityManager.persist(facility);
            entityManager.flush();

            // When — count via Hibernate
            @SuppressWarnings("unchecked")
            List<Long> counts = (List<Long>) hibernateTemplate.find(
                    "SELECT COUNT(f) FROM Facility f WHERE f.name = ?1", "Mixed Ops Test");

            // Then
            assertThat(counts).isNotEmpty();
            assertThat(counts.get(0)).isEqualTo(1L);
        }

        @Test
        @Tag("create")
        @Tag("read")
        @DisplayName("should handle Hibernate save followed by JPA query in same transaction")
        void shouldHandleHibernateSaveFollowedByJpaQuery_inSameTransaction() {
            // Given — save HBM entity via Hibernate
            Provider provider = new Provider();
            provider.setProviderNo("DFC03");
            provider.setLastName("MixedOps");
            provider.setFirstName("Test");
            provider.setProviderType("doctor");
            provider.setSpecialty("GP");
            provider.setSex("M");
            provider.setStatus("1");
            hibernateTemplate.save(provider);
            hibernateTemplate.flush();

            // When — JPA native SQL query
            Query query = entityManager.createNativeQuery("SELECT last_name FROM provider WHERE provider_no = :provNo");
            query.setParameter("provNo", "DFC03");

            @SuppressWarnings("unchecked")
            List<Object> results = query.getResultList();

            // Then
            assertThat(results).isNotEmpty();
            assertThat(results.get(0)).isEqualTo("MixedOps");
        }

        @Test
        @Tag("create")
        @Tag("delete")
        @DisplayName("should handle interleaved JPA and Hibernate operations")
        void shouldHandleInterleavedJpaAndHibernateOperations() {
            // Given — JPA persist
            Facility f1 = new Facility();
            f1.setName("Interleaved 1");
            entityManager.persist(f1);
            entityManager.flush();

            // Hibernate save
            Provider p1 = new Provider();
            p1.setProviderNo("DFC04");
            p1.setLastName("Interleaved");
            p1.setFirstName("One");
            p1.setProviderType("doctor");
            p1.setSpecialty("GP");
            p1.setSex("M");
            p1.setStatus("1");
            hibernateTemplate.save(p1);
            hibernateTemplate.flush();

            // More JPA operations
            Facility f2 = new Facility();
            f2.setName("Interleaved 2");
            entityManager.persist(f2);
            entityManager.flush();

            // When — verify both contexts wrote successfully
            Query facilityQuery = entityManager.createQuery("SELECT COUNT(f) FROM Facility f WHERE f.name LIKE :pattern");
            facilityQuery.setParameter("pattern", "Interleaved%");
            Long facilityCount = (Long) facilityQuery.getSingleResult();

            Query providerQuery = entityManager.createNativeQuery("SELECT COUNT(*) FROM provider WHERE last_name = :lastName");
            providerQuery.setParameter("lastName", "Interleaved");
            int providerCount = ((Number) providerQuery.getSingleResult()).intValue();

            // Then
            assertThat(facilityCount).isEqualTo(2L);
            assertThat(providerCount).isEqualTo(1);
        }
    }
}
