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
package io.github.carlos_emr.carlos.daos.security;

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.model.security.Secuserrole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.hibernate5.HibernateTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link SecuserroleDao} multi-parameter query methods.
 *
 * <p>These tests validate HQL queries with positional parameters (?1, ?2, ...)
 * bind correctly, ensuring safe migration to Hibernate 6 named parameter syntax.
 * Tests cover CRUD operations, multi-parameter searches, and edge cases.</p>
 *
 * <p><b>Test Coverage Summary:</b></p>
 * <ul>
 *   <li><b>CRUD operations</b> - save, saveAll, update, delete, deleteById, merge</li>
 *   <li><b>Query operations</b> - findById, findByProviderNo, findByRoleName, findByActiveyn, findAll</li>
 *   <li><b>Delete operations</b> - deleteByOrgcd, deleteByProviderNo, deleteById</li>
 *   <li><b>findByExample()</b> - Hibernate Criteria API with {@code Example.create()}</li>
 *   <li><b>findByProperty()</b> - Dynamic HQL query: {@code from Secuserrole as model where model.<property> = ?1}</li>
 *   <li><b>Lifecycle methods</b> - attachDirty, attachClean, updateRoleName</li>
 * </ul>
 *
 * @since 2026-02-03
 * @see SecuserroleDao
 */
@DisplayName("SecuserroleDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("security")
@Transactional
public class SecuserroleDaoIntegrationTest extends CarlosTestBase {

    /**
     * The DAO under test, autowired from the Spring test application context.
     * Backed by {@link SecuserroleDaoImpl}, which extends {@code HibernateDaoSupport}
     * and uses both HQL and Hibernate Criteria API for query operations.
     */
    @Autowired
    private SecuserroleDao secuserroleDao;

    /**
     * JPA {@link EntityManager} for direct database verification in tests,
     * bypassing the DAO layer to confirm actual persisted state.
     */
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /**
     * Flushes the Hibernate Session used by {@code SecuserroleDaoImpl} (HibernateDaoSupport).
     * Required because {@code entityManager.flush()} only flushes the JPA context,
     * not the standalone Hibernate Session used by HibernateDaoSupport-based DAOs.
     */
    @Autowired
    private HibernateTemplate hibernateTemplate;

    /**
     * Creates and persists a {@link Secuserrole} with minimal required fields.
     *
     * @param providerNo String the provider number
     * @param roleName   String the role name (e.g., "doctor", "nurse")
     * @param orgcd      String the organization code
     * @return Secuserrole the persisted entity with a generated ID
     */
    private Secuserrole createSecuserrole(String providerNo, String roleName, String orgcd) {
        Secuserrole role = new Secuserrole();
        role.setProviderNo(providerNo);
        role.setRoleName(roleName);
        role.setOrgcd(orgcd);
        secuserroleDao.save(role);
        return role;
    }

    /**
     * Creates and persists a {@link Secuserrole} with an explicit active/inactive status.
     *
     * @param providerNo String the provider number
     * @param roleName   String the role name
     * @param orgcd      String the organization code
     * @param activeyn   Integer the active status (1 = active, 0 = inactive)
     * @return Secuserrole the persisted entity
     */
    private Secuserrole createSecuserroleWithActive(String providerNo, String roleName,
                                                     String orgcd, Integer activeyn) {
        Secuserrole role = new Secuserrole();
        role.setProviderNo(providerNo);
        role.setRoleName(roleName);
        role.setOrgcd(orgcd);
        role.setActiveyn(activeyn);
        secuserroleDao.save(role);
        return role;
    }

    /**
     * Tests for CRUD persistence operations - covers {@code save()}, {@code saveAll()},
     * {@code updateRoleName()}, {@code delete()}, {@code deleteById()}, {@code merge()},
     * and {@code update()} methods of the {@link SecuserroleDao}.
     */
    @Nested
    @DisplayName("CRUD persistence operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist secuserrole with valid data")
        void shouldPersistSecuserrole_whenValidDataProvided() {
            // Given
            Secuserrole role = new Secuserrole();
            role.setProviderNo("P100");
            role.setRoleName("doctor");
            role.setOrgcd("ORG1");

            // When
            secuserroleDao.save(role);
            hibernateTemplate.flush();

            // Then
            assertThat(role.getId()).isNotNull();
            Secuserrole found = secuserroleDao.findById(role.getId());
            assertThat(found).isNotNull();
            assertThat(found.getProviderNo()).isEqualTo("P100");
        }

        @Test
        @Tag("create")
        @DisplayName("should save all secuserroles in batch")
        void shouldSaveAll_whenBatchProvided() {
            // Given
            List<Secuserrole> batch = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                Secuserrole role = new Secuserrole();
                role.setProviderNo("BATCH" + i);
                role.setRoleName("role" + i);
                role.setOrgcd("ORG1");
                batch.add(role);
            }

            // When - DAO interface accepts raw List for backward compatibility
            @SuppressWarnings("unchecked")
            List rawBatch = batch;
            secuserroleDao.saveAll(rawBatch);
            hibernateTemplate.flush();

            // Then
            List<Secuserrole> results = secuserroleDao.findAll();
            assertThat(results)
                .extracting(Secuserrole::getProviderNo)
                .contains("BATCH0", "BATCH1", "BATCH2");
        }

        @Test
        @Tag("update")
        @DisplayName("should update role name by ID")
        void shouldUpdateRoleName_byId() {
            // Given
            Secuserrole role = createSecuserrole("P200", "doctor", "ORG1");
            hibernateTemplate.flush();

            // When
            secuserroleDao.updateRoleName(role.getId(), "specialist");
            hibernateTemplate.flush();
            hibernateTemplate.clear();

            // Then
            Secuserrole found = secuserroleDao.findById(role.getId());
            assertThat(found.getRoleName()).isEqualTo("specialist");
        }

        @Test
        @Tag("delete")
        @DisplayName("should delete secuserrole by entity reference")
        void shouldDeleteSecuserrole_whenEntityProvided() {
            // Given
            Secuserrole role = createSecuserrole("P300", "doctor", "ORG1");
            hibernateTemplate.flush();
            Integer savedId = role.getId();

            // When
            secuserroleDao.delete(role);
            hibernateTemplate.flush();

            // Then
            Secuserrole found = secuserroleDao.findById(savedId);
            assertThat(found).isNull();
        }

        @Test
        @Tag("delete")
        @DisplayName("should delete secuserrole by ID")
        void shouldDeleteSecuserrole_byId() {
            // Given
            Secuserrole role = createSecuserrole("P301", "nurse", "ORG1");
            hibernateTemplate.flush();
            Integer savedId = role.getId();

            // When
            int deleted = secuserroleDao.deleteById(savedId);

            // Then
            assertThat(deleted).isEqualTo(1);
        }

        @Test
        @Tag("update")
        @DisplayName("should merge detached secuserrole instance")
        void shouldMergeSecuserrole_whenDetachedInstanceProvided() {
            // Given
            Secuserrole role = createSecuserrole("P400", "doctor", "ORG1");
            hibernateTemplate.flush();
            // Evict so the entity is actually detached before the merge test
            hibernateTemplate.evict(role);

            // When
            role.setRoleName("merged_role");
            Secuserrole merged = secuserroleDao.merge(role);
            hibernateTemplate.flush();

            // Then
            assertThat(merged).isNotNull();
            assertThat(merged.getRoleName()).isEqualTo("merged_role");
        }

        @Test
        @Tag("update")
        @DisplayName("should update active status via update method")
        void shouldUpdateActiveStatus_viaUpdateMethod() {
            // Given
            Secuserrole role = createSecuserroleWithActive("P500", "doctor", "ORG1", 1);
            hibernateTemplate.flush();

            // When
            role.setActiveyn(0);
            int rowsUpdated = secuserroleDao.update(role);
            hibernateTemplate.flush();

            // Then
            assertThat(rowsUpdated).isGreaterThanOrEqualTo(1);
        }
    }

    /**
     * Tests for query operations - covers {@code findById()}, {@code findByProviderNo()},
     * {@code findByRoleName()}, {@code findByActiveyn()}, and {@code findAll()} methods
     * that retrieve {@link Secuserrole} entities by various criteria.
     */
    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("read")
        @DisplayName("should find secuserrole by ID")
        void shouldFindById_whenValidIdProvided() {
            // Given
            Secuserrole saved = createSecuserrole("P600", "doctor", "ORG1");
            hibernateTemplate.flush();

            // When
            Secuserrole found = secuserroleDao.findById(saved.getId());

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getProviderNo()).isEqualTo("P600");
            assertThat(found.getRoleName()).isEqualTo("doctor");
        }

        @Test
        @Tag("read")
        @DisplayName("should return null for non-existent ID")
        void shouldReturnNull_whenIdDoesNotExist() {
            // When
            Secuserrole found = secuserroleDao.findById(999999);

            // Then
            assertThat(found).isNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find secuserroles by provider number")
        void shouldFindSecuserroles_byProviderNo() {
            // Given
            createSecuserrole("P700", "doctor", "ORG1");
            createSecuserrole("P700", "nurse", "ORG2");
            createSecuserrole("P701", "admin", "ORG1");
            hibernateTemplate.flush();

            // When - DAO interface returns raw List for backward compatibility
            @SuppressWarnings("unchecked")
            List<Secuserrole> results = secuserroleDao.findByProviderNo("P700");

            // Then
            assertThat(results)
                .hasSize(2)
                .allMatch(r -> r.getProviderNo().equals("P700"));
        }

        @Test
        @Tag("read")
        @DisplayName("should find secuserroles by role name")
        void shouldFindSecuserroles_byRoleName() {
            // Given
            createSecuserrole("P800", "specialist", "ORG1");
            createSecuserrole("P801", "specialist", "ORG2");
            createSecuserrole("P802", "nurse", "ORG1");
            hibernateTemplate.flush();

            // When - DAO interface returns raw List for backward compatibility
            @SuppressWarnings("unchecked")
            List<Secuserrole> results = secuserroleDao.findByRoleName("specialist");

            // Then
            assertThat(results)
                .hasSize(2)
                .allMatch(r -> r.getRoleName().equals("specialist"));
        }

        @Test
        @Tag("filter")
        @DisplayName("should filter by active status")
        void shouldFilterSecuserroles_byActiveStatus() {
            // Given
            createSecuserroleWithActive("P900", "doctor", "ORG1", 1);
            createSecuserroleWithActive("P901", "nurse", "ORG1", 0);
            hibernateTemplate.flush();

            // When - DAO interface returns raw List for backward compatibility
            @SuppressWarnings("unchecked")
            List<Secuserrole> activeResults = secuserroleDao.findByActiveyn(1);

            // Then
            assertThat(activeResults)
                .isNotEmpty()
                .allMatch(r -> r.getActiveyn() != null && r.getActiveyn().equals(1));
        }

        @Test
        @Tag("read")
        @DisplayName("should find all secuserroles")
        void shouldFindAll_whenQueried() {
            // Given
            createSecuserrole("P111", "doctor", "ORG1");
            createSecuserrole("P222", "nurse", "ORG2");
            hibernateTemplate.flush();

            // When
            List<Secuserrole> results = secuserroleDao.findAll();

            // Then
            assertThat(results).isNotEmpty();
        }
    }

    /**
     * Tests for bulk delete operations - covers {@code deleteByOrgcd(String)} and
     * {@code deleteByProviderNo(String)} HQL bulk deletes, plus {@code deleteById(Integer)}.
     * Verifies correct row counts and zero returns for non-existent values.
     */
    @Nested
    @DisplayName("Delete operations")
    class DeleteOperations {

        @Test
        @Tag("delete")
        @DisplayName("should delete secuserroles by orgcd")
        void shouldDeleteSecuserroles_byOrgcd() {
            // Given
            createSecuserrole("P001", "doctor", "ORG1");
            createSecuserrole("P002", "nurse", "ORG1");
            createSecuserrole("P003", "admin", "ORG2");
            hibernateTemplate.flush();

            // When
            int deleted = secuserroleDao.deleteByOrgcd("ORG1");

            // Then
            assertThat(deleted).isEqualTo(2);
        }

        @Test
        @Tag("delete")
        @DisplayName("should delete secuserroles by provider number")
        void shouldDeleteSecuserroles_byProviderNo() {
            // Given
            createSecuserrole("PDEL1", "doctor", "ORG1");
            createSecuserrole("PDEL1", "nurse", "ORG2");
            createSecuserrole("PDEL2", "admin", "ORG1");
            hibernateTemplate.flush();

            // When
            int deleted = secuserroleDao.deleteByProviderNo("PDEL1");

            // Then
            assertThat(deleted).isEqualTo(2);
        }

        @Test
        @Tag("delete")
        @DisplayName("should return zero when deleting by non-existent orgcd")
        void shouldReturnZero_whenDeletingByNonExistentOrgcd() {
            // When
            int deleted = secuserroleDao.deleteByOrgcd("NONEXIST_ORG");

            // Then
            assertThat(deleted).isEqualTo(0);
        }

        @Test
        @Tag("delete")
        @DisplayName("should return zero when deleting by non-existent provider number")
        void shouldReturnZero_whenDeletingByNonExistentProviderNo() {
            // When
            int deleted = secuserroleDao.deleteByProviderNo("NOPRV");

            // Then
            assertThat(deleted).isEqualTo(0);
        }

        @Test
        @Tag("delete")
        @DisplayName("should return zero when deleting by non-existent ID")
        void shouldReturnZero_whenDeletingByNonExistentId() {
            // When
            int deleted = secuserroleDao.deleteById(999999);

            // Then
            assertThat(deleted).isEqualTo(0);
        }
    }

    /**
     * Tests for the {@link SecuserroleDao#findByExample(Secuserrole)} method.
     *
     * <p>The {@code findByExample()} implementation uses Hibernate Criteria API
     * with {@code Example.create(instance)} to find entities matching non-null
     * property values of the provided example instance. This is a dynamic query
     * that automatically includes only populated fields in the WHERE clause.</p>
     */
    @Nested
    @DisplayName("findByExample() operations")
    class FindByExampleOperations {

        /**
         * Verifies that {@code findByExample()} returns matching entities when
         * the example instance has a single property set (providerNo).
         */
        @Test
        @Tag("read")
        @Tag("query")
        @DisplayName("should find secuserroles matching example by provider number")
        void shouldFindSecuserroles_byExampleProviderNo() {
            // Given
            createSecuserrole("EX100", "doctor", "ORG1");
            createSecuserrole("EX100", "nurse", "ORG2");
            createSecuserrole("EX200", "admin", "ORG1");
            hibernateTemplate.flush();

            Secuserrole example = new Secuserrole();
            example.setProviderNo("EX100");

            // When
            @SuppressWarnings("unchecked")
            List<Secuserrole> results = secuserroleDao.findByExample(example);

            // Then
            assertThat(results)
                .hasSize(2)
                .allMatch(r -> r.getProviderNo().equals("EX100"));
        }

        /**
         * Verifies that {@code findByExample()} returns matching entities when
         * the example instance has a single property set (roleName).
         */
        @Test
        @Tag("read")
        @Tag("query")
        @DisplayName("should find secuserroles matching example by role name")
        void shouldFindSecuserroles_byExampleRoleName() {
            // Given
            createSecuserrole("EX300", "physio", "ORG1");
            createSecuserrole("EX301", "physio", "ORG2");
            createSecuserrole("EX302", "nurse", "ORG1");
            hibernateTemplate.flush();

            Secuserrole example = new Secuserrole();
            example.setRoleName("physio");

            // When
            @SuppressWarnings("unchecked")
            List<Secuserrole> results = secuserroleDao.findByExample(example);

            // Then
            assertThat(results)
                .hasSize(2)
                .allMatch(r -> r.getRoleName().equals("physio"));
        }

        /**
         * Verifies that {@code findByExample()} can match on multiple properties
         * simultaneously (providerNo AND roleName AND orgcd).
         */
        @Test
        @Tag("read")
        @Tag("query")
        @DisplayName("should find secuserroles matching example with multiple properties")
        void shouldFindSecuserroles_byExampleWithMultipleProperties() {
            // Given
            createSecuserrole("EX400", "doctor", "ORG1");
            createSecuserrole("EX400", "doctor", "ORG2");
            createSecuserrole("EX400", "nurse", "ORG1");
            hibernateTemplate.flush();

            Secuserrole example = new Secuserrole();
            example.setProviderNo("EX400");
            example.setRoleName("doctor");
            example.setOrgcd("ORG1");

            // When
            @SuppressWarnings("unchecked")
            List<Secuserrole> results = secuserroleDao.findByExample(example);

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getProviderNo()).isEqualTo("EX400");
            assertThat(results.get(0).getRoleName()).isEqualTo("doctor");
            assertThat(results.get(0).getOrgcd()).isEqualTo("ORG1");
        }

        /**
         * Verifies that {@code findByExample()} returns an empty list when
         * no entities match the example.
         */
        @Test
        @Tag("read")
        @Tag("query")
        @DisplayName("should return empty list when no entities match example")
        void shouldReturnEmptyList_whenNoEntitiesMatchExample() {
            // Given
            createSecuserrole("EX500", "doctor", "ORG1");
            hibernateTemplate.flush();

            Secuserrole example = new Secuserrole();
            example.setProviderNo("NONEX");

            // When
            @SuppressWarnings("unchecked")
            List<Secuserrole> results = secuserroleDao.findByExample(example);

            // Then
            assertThat(results).isEmpty();
        }
    }

    /**
     * Tests for the {@link SecuserroleDao#findByProperty(String, Object)} method.
     *
     * <p>The {@code findByProperty()} implementation builds an HQL query dynamically:
     * {@code from Secuserrole as model where model.<propertyName> = ?1}.
     * While tested indirectly through {@code findByProviderNo()}, {@code findByRoleName()},
     * and {@code findByActiveyn()}, these tests exercise the method directly with
     * various property names.</p>
     */
    @Nested
    @DisplayName("findByProperty() operations")
    class FindByPropertyOperations {

        /**
         * Verifies that {@code findByProperty()} works with the orgcd property,
         * which is not covered by existing convenience method tests.
         */
        @Test
        @Tag("read")
        @Tag("query")
        @DisplayName("should find secuserroles by orgcd property")
        void shouldFindSecuserroles_byOrgcdProperty() {
            // Given
            createSecuserrole("FP100", "doctor", "FPORG");
            createSecuserrole("FP101", "nurse", "FPORG");
            createSecuserrole("FP102", "admin", "OTHER");
            hibernateTemplate.flush();

            // When
            @SuppressWarnings("unchecked")
            List<Secuserrole> results = secuserroleDao.findByProperty("orgcd", "FPORG");

            // Then
            assertThat(results)
                .hasSize(2)
                .allMatch(r -> r.getOrgcd().equals("FPORG"));
        }

        /**
         * Verifies that {@code findByProperty()} returns an empty list
         * when no entities match the property value.
         */
        @Test
        @Tag("read")
        @Tag("query")
        @DisplayName("should return empty list when property value has no matches")
        void shouldReturnEmptyList_whenPropertyValueHasNoMatches() {
            // Given
            createSecuserrole("FP200", "doctor", "ORG1");
            hibernateTemplate.flush();

            // When
            @SuppressWarnings("unchecked")
            List<Secuserrole> results = secuserroleDao.findByProperty("providerNo", "NONEXISTENT");

            // Then
            assertThat(results).isEmpty();
        }
    }

    /**
     * Tests for the {@link SecuserroleDao#attachDirty(Secuserrole)} method.
     *
     * <p>The {@code attachDirty()} implementation calls
     * {@code session.saveOrUpdate(instance)} after setting {@code lastUpdateDate}
     * to the current time. This re-attaches a detached entity to the session
     * and marks it as dirty for synchronization.</p>
     */
    @Nested
    @DisplayName("attachDirty() operations")
    class AttachDirtyOperations {

        /**
         * Verifies that {@code attachDirty()} persists a new (transient) entity
         * via {@code saveOrUpdate()}, which behaves like {@code save()} for new entities.
         */
        @Test
        @Tag("create")
        @DisplayName("should persist new entity via attachDirty")
        void shouldPersistNewEntity_viaAttachDirty() {
            // Given
            Secuserrole role = new Secuserrole();
            role.setProviderNo("AD100");
            role.setRoleName("doctor");
            role.setOrgcd("ORG1");

            // When
            secuserroleDao.attachDirty(role);
            hibernateTemplate.flush();

            // Then
            assertThat(role.getId()).isNotNull();
            assertThat(role.getLastUpdateDate()).isNotNull();
            Secuserrole found = secuserroleDao.findById(role.getId());
            assertThat(found).isNotNull();
            assertThat(found.getProviderNo()).isEqualTo("AD100");
        }

        /**
         * Verifies that {@code attachDirty()} updates the {@code lastUpdateDate}
         * when called on an existing entity.
         */
        @Test
        @Tag("update")
        @DisplayName("should update lastUpdateDate when attaching dirty entity")
        void shouldUpdateLastUpdateDate_whenAttachingDirtyEntity() {
            // Given - set a fixed past baseline date before saving so we can verify it was updated
            java.util.Date baseline = new java.util.Date(946684800000L); // 2000-01-01
            Secuserrole role = new Secuserrole();
            role.setProviderNo("AD200");
            role.setRoleName("nurse");
            role.setOrgcd("ORG1");
            role.setLastUpdateDate(baseline);
            secuserroleDao.save(role);
            hibernateTemplate.flush();
            hibernateTemplate.evict(role);

            // When - modify and re-attach
            role.setRoleName("specialist");
            secuserroleDao.attachDirty(role);
            hibernateTemplate.flush();

            // Then - lastUpdateDate must have been updated past the baseline
            Secuserrole found = secuserroleDao.findById(role.getId());
            assertThat(found.getRoleName()).isEqualTo("specialist");
            assertThat(found.getLastUpdateDate()).isAfter(baseline);
        }
    }

    /**
     * Tests for the {@link SecuserroleDao#attachClean(Secuserrole)} method.
     *
     * <p>The {@code attachClean()} implementation calls
     * {@code session.lock(instance, LockMode.NONE)} which re-associates a
     * detached entity with the session without forcing a database read or
     * marking it as dirty.</p>
     */
    @Nested
    @DisplayName("attachClean() operations")
    class AttachCleanOperations {

        /**
         * Verifies that {@code attachClean()} re-associates a detached entity
         * with the session. After calling attachClean, the entity should be
         * associated with the current session.
         */
        @Test
        @Tag("read")
        @DisplayName("should attach clean entity to session without modifying it")
        void shouldAttachCleanEntity_withoutModifyingIt() {
            // Given - create and flush an entity, then evict it from session
            Secuserrole role = createSecuserrole("AC100", "doctor", "ORG1");
            hibernateTemplate.flush();
            String originalRoleName = role.getRoleName();
            Integer originalId = role.getId();

            // Evict to make it detached
            hibernateTemplate.evict(role);

            // When - re-attach as clean
            secuserroleDao.attachClean(role);

            // Then - entity should retain its original values
            assertThat(role.getId()).isEqualTo(originalId);
            assertThat(role.getRoleName()).isEqualTo(originalRoleName);
            assertThat(role.getProviderNo()).isEqualTo("AC100");
        }
    }

    /**
     * Tests for the {@link SecuserroleDao#updateRoleName(Integer, String)} edge cases.
     *
     * <p>The {@code updateRoleName()} implementation first loads the entity via
     * {@code HibernateTemplate.get()}, then sets the new role name and
     * lastUpdateDate before calling {@code HibernateTemplate.update()}.</p>
     */
    @Nested
    @DisplayName("updateRoleName() edge cases")
    class UpdateRoleNameEdgeCases {

        /**
         * Verifies that {@code updateRoleName()} is a no-op when the ID does
         * not correspond to any existing entity. The implementation checks
         * for null return from {@code HibernateTemplate.get()} and silently
         * skips the update.
         */
        @Test
        @Tag("update")
        @DisplayName("should not throw when updating role name with non-existent ID")
        void shouldNotThrow_whenUpdatingRoleNameWithNonExistentId() {
            // When / Then - no exception should be thrown
            assertThatCode(() -> secuserroleDao.updateRoleName(999999, "newRole"))
                .doesNotThrowAnyException();
        }

        /**
         * Verifies that {@code updateRoleName()} also sets the lastUpdateDate
         * to a non-null value.
         */
        @Test
        @Tag("update")
        @DisplayName("should set lastUpdateDate when updating role name")
        void shouldSetLastUpdateDate_whenUpdatingRoleName() {
            // Given
            Secuserrole role = createSecuserrole("URN01", "doctor", "ORG1");
            hibernateTemplate.flush();

            // When
            secuserroleDao.updateRoleName(role.getId(), "surgeon");
            hibernateTemplate.flush();
            hibernateTemplate.clear();

            // Then
            Secuserrole found = secuserroleDao.findById(role.getId());
            assertThat(found.getRoleName()).isEqualTo("surgeon");
            assertThat(found.getLastUpdateDate()).isNotNull();
        }
    }

    /**
     * Tests for the {@link SecuserroleDao#update(Secuserrole)} method with
     * additional scenarios.
     *
     * <p>The {@code update()} method builds an HQL UPDATE statement using
     * string concatenation to match on providerNo, roleName, and orgcd.
     * It updates the activeyn and lastUpdateDate fields. Returns the count
     * of affected rows.</p>
     */
    @Nested
    @DisplayName("update() additional scenarios")
    class UpdateAdditionalScenarios {

        /**
         * Verifies that {@code update()} returns zero when the specified
         * combination of providerNo, roleName, and orgcd does not exist.
         */
        @Test
        @Tag("update")
        @DisplayName("should return zero when updating non-existent combination")
        void shouldReturnZero_whenUpdatingNonExistentCombination() {
            // Given - an entity with values that do not match any persisted record
            Secuserrole nonExistent = new Secuserrole();
            nonExistent.setProviderNo("NOEX");
            nonExistent.setRoleName("nonexistent");
            nonExistent.setOrgcd("NOORG");
            nonExistent.setActiveyn(1);

            // When
            int rowsUpdated = secuserroleDao.update(nonExistent);

            // Then
            assertThat(rowsUpdated).isEqualTo(0);
        }

        /**
         * Verifies that {@code update()} correctly matches on the combination
         * of providerNo, roleName, and orgcd and updates the activeyn value.
         */
        @Test
        @Tag("update")
        @DisplayName("should update activeyn for exact match of provider, role, and org")
        void shouldUpdateActiveyn_forExactMatchOfProviderRoleAndOrg() {
            // Given
            Secuserrole role = createSecuserroleWithActive("UP100", "doctor", "UPORG", 1);
            hibernateTemplate.flush();
            hibernateTemplate.clear();

            // When - construct update entity with same key fields but different activeyn
            Secuserrole updateEntity = new Secuserrole();
            updateEntity.setProviderNo("UP100");
            updateEntity.setRoleName("doctor");
            updateEntity.setOrgcd("UPORG");
            updateEntity.setActiveyn(0);
            int rowsUpdated = secuserroleDao.update(updateEntity);

            // Then
            assertThat(rowsUpdated).isEqualTo(1);
        }
    }

    /**
     * Tests for the {@link SecuserroleDao#merge(Secuserrole)} additional
     * scenarios.
     *
     * <p>The {@code merge()} implementation calls {@code session.merge()} and
     * sets the {@code lastUpdateDate} before merging. It returns the merged
     * (managed) entity instance.</p>
     */
    @Nested
    @DisplayName("merge() additional scenarios")
    class MergeAdditionalScenarios {

        /**
         * Verifies that {@code merge()} sets the lastUpdateDate on the merged entity.
         */
        @Test
        @Tag("update")
        @DisplayName("should set lastUpdateDate when merging entity")
        void shouldSetLastUpdateDate_whenMergingEntity() {
            // Given
            Secuserrole role = createSecuserrole("MG100", "doctor", "ORG1");
            hibernateTemplate.flush();

            // When
            role.setOrgcd("ORG2");
            Secuserrole merged = secuserroleDao.merge(role);
            hibernateTemplate.flush();

            // Then
            assertThat(merged.getLastUpdateDate()).isNotNull();
            assertThat(merged.getOrgcd()).isEqualTo("ORG2");
        }

        /**
         * Verifies that {@code merge()} can persist a new (transient) entity
         * that has no ID set. Hibernate's merge on a transient instance creates
         * a new persistent copy.
         */
        @Test
        @Tag("create")
        @DisplayName("should create new entity when merging transient instance")
        void shouldCreateNewEntity_whenMergingTransientInstance() {
            // Given
            Secuserrole role = new Secuserrole();
            role.setProviderNo("MG200");
            role.setRoleName("nurse");
            role.setOrgcd("ORG1");

            // When
            Secuserrole merged = secuserroleDao.merge(role);
            hibernateTemplate.flush();

            // Then
            assertThat(merged.getId()).isNotNull();
            assertThat(merged.getLastUpdateDate()).isNotNull();
        }
    }

    /**
     * Tests for the {@link SecuserroleDao#save(Secuserrole)} additional scenarios
     * verifying that lastUpdateDate is automatically set.
     */
    @Nested
    @DisplayName("save() lastUpdateDate behavior")
    class SaveLastUpdateDateBehavior {

        /**
         * Verifies that {@code save()} automatically sets the lastUpdateDate
         * to a non-null value.
         */
        @Test
        @Tag("create")
        @DisplayName("should auto-set lastUpdateDate when saving new entity")
        void shouldAutoSetLastUpdateDate_whenSavingNewEntity() {
            // Given
            Secuserrole role = new Secuserrole();
            role.setProviderNo("SV100");
            role.setRoleName("doctor");
            role.setOrgcd("ORG1");

            // When
            secuserroleDao.save(role);
            hibernateTemplate.flush();

            // Then
            assertThat(role.getLastUpdateDate()).isNotNull();
        }
    }
}
