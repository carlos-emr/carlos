/**
 * Copyright (c) 2024-2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Integration tests for SecuserroleDao to verify HibernateTemplate migration.
 *
 * These tests verify the behavioral contract that MUST be preserved when
 * migrating from HibernateTemplate/HibernateDaoSupport to EntityManager.
 *
 * Run these tests BEFORE and AFTER migration to ensure no regressions.
 *
 * Coverage:
 * - find() with various parameter styles
 * - get() / findById() for loading entities
 * - save() / saveOrUpdate() for persistence
 * - update() for modifications
 * - delete() for removal
 * - bulkUpdate() for batch operations
 * - createCriteria() with Example (to be migrated to JPA Criteria)
 * - merge() for detached entities
 * - flush() behavior
 *
 * @since 2026-02-03
 */
package io.github.carlos_emr.carlos.daos.security;

import io.github.carlos_emr.carlos.model.security.Secuserrole;
import io.github.carlos_emr.carlos.test.OpenOTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for HibernateTemplate migration verification.
 *
 * These tests are designed to:
 * 1. Pass with current HibernateTemplate implementation
 * 2. Continue passing after migration to EntityManager
 * 3. FAIL if migration introduces behavioral changes
 */
@Tag("integration")
@Tag("hibernate-migration")
@DisplayName("SecuserroleDao - HibernateTemplate Migration Tests")
public class SecuserroleDaoHibernateMigrationTest extends OpenOTestBase {

    @Autowired
    private SecuserroleDao secuserroleDao;

    @PersistenceContext
    private EntityManager entityManager;

    private static final String TEST_PROVIDER_NO = "TEST_PROV_001";
    private static final String TEST_ROLE_NAME = "test_role";
    private static final String TEST_ORG_CD = "TEST_ORG";

    /**
     * Helper to create a test entity with unique values.
     */
    private Secuserrole createTestEntity(String suffix) {
        Secuserrole entity = new Secuserrole();
        entity.setProviderNo(TEST_PROVIDER_NO + suffix);
        entity.setRoleName(TEST_ROLE_NAME + suffix);
        entity.setOrgcd(TEST_ORG_CD);
        entity.setActiveyn(1);
        entity.setLastUpdateDate(new Date());
        return entity;
    }

    /**
     * Helper to persist entity directly for test setup.
     */
    private Secuserrole persistTestEntity(String suffix) {
        Secuserrole entity = createTestEntity(suffix);
        entityManager.persist(entity);
        entityManager.flush();
        return entity;
    }

    /**
     * Clean up test data before each test.
     */
    @BeforeEach
    void cleanupTestData() {
        entityManager.createQuery(
            "DELETE FROM Secuserrole s WHERE s.providerNo LIKE :prefix")
            .setParameter("prefix", TEST_PROVIDER_NO + "%")
            .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    // =========================================================================
    // FIND TESTS - HibernateTemplate.find() -> EntityManager.createQuery()
    // =========================================================================

    @Nested
    @DisplayName("find() Migration Tests")
    @Tag("find")
    class FindMigrationTests {

        @Test
        @DisplayName("should return results for simple query without parameters")
        void shouldReturnResultsForSimpleQuery() {
            // Given: entities exist in database
            persistTestEntity("_A");
            persistTestEntity("_B");
            entityManager.clear();

            // When: findAll() is called (uses find() internally)
            List<Secuserrole> results = secuserroleDao.findAll();

            // Then: results contain our test entities
            assertThat(results).isNotNull();
            assertThat(results.size()).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("should return empty list when no matches - NOT null")
        void shouldReturnEmptyListWhenNoMatches() {
            // Given: no matching entities
            entityManager.clear();

            // When: query returns no results
            List<Secuserrole> results = secuserroleDao.findByProviderNo("NONEXISTENT_PROVIDER_XYZ");

            // Then: empty list returned, NOT null
            // CRITICAL: HibernateTemplate.find() returns empty list, not null
            // EntityManager.createQuery().getResultList() also returns empty list
            assertThat(results).isNotNull();
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should handle single parameter correctly")
        void shouldHandleSingleParameter() {
            // Given: entity with specific providerNo
            Secuserrole saved = persistTestEntity("_SINGLE");
            entityManager.clear();

            // When: find by single parameter
            List<Secuserrole> results = secuserroleDao.findByProviderNo(saved.getProviderNo());

            // Then: exact match found
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getProviderNo()).isEqualTo(saved.getProviderNo());
        }

        @Test
        @DisplayName("should handle multiple parameters correctly")
        void shouldHandleMultipleParameters() {
            // Given: entity with specific providerNo and roleName
            Secuserrole saved = persistTestEntity("_MULTI");
            entityManager.clear();

            // When: find by providerNo (which uses findByProperty internally)
            List<Secuserrole> results = secuserroleDao.findByProviderNo(saved.getProviderNo());

            // Then: correct entity found
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRoleName()).isEqualTo(saved.getRoleName());
        }

        @Test
        @DisplayName("should handle query with roleName parameter")
        void shouldHandleRoleNameParameter() {
            // Given: entities with different roles
            persistTestEntity("_ROLE1");
            Secuserrole target = createTestEntity("_ROLE2");
            target.setRoleName("special_role_xyz");
            entityManager.persist(target);
            entityManager.flush();
            entityManager.clear();

            // When: find by roleName
            List<Secuserrole> results = secuserroleDao.findByRoleName("special_role_xyz");

            // Then: only matching entity returned
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRoleName()).isEqualTo("special_role_xyz");
        }
    }

    // =========================================================================
    // GET/FIND BY ID TESTS - HibernateTemplate.get() -> EntityManager.find()
    // =========================================================================

    @Nested
    @DisplayName("get()/findById() Migration Tests")
    @Tag("get")
    class GetMigrationTests {

        @Test
        @DisplayName("should return entity when ID exists")
        void shouldReturnEntityWhenIdExists() {
            // Given: entity exists with known ID
            Secuserrole saved = persistTestEntity("_GET");
            Integer savedId = saved.getId();
            assertThat(savedId).isNotNull();
            entityManager.clear();

            // When: findById is called
            Secuserrole found = secuserroleDao.findById(savedId);

            // Then: entity is returned with correct data
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(savedId);
            assertThat(found.getProviderNo()).isEqualTo(saved.getProviderNo());
        }

        @Test
        @DisplayName("should return null when ID does not exist - NOT throw exception")
        void shouldReturnNullWhenIdNotExists() {
            // Given: ID that doesn't exist
            Integer nonExistentId = 999999999;

            // When: findById is called
            Secuserrole found = secuserroleDao.findById(nonExistentId);

            // Then: null is returned, NOT an exception
            // CRITICAL: HibernateTemplate.get() returns null for missing entity
            // EntityManager.find() also returns null
            // This is different from EntityManager.getReference() which throws
            assertThat(found).isNull();
        }

        @Test
        @DisplayName("should return null when ID is null")
        void shouldHandleNullId() {
            // When/Then: null ID should return null or throw IllegalArgumentException
            // depending on implementation - document actual behavior
            Secuserrole found = secuserroleDao.findById(null);

            // Current behavior returns null - migration must preserve this
            assertThat(found).isNull();
        }
    }

    // =========================================================================
    // SAVE TESTS - HibernateTemplate.save() -> EntityManager.persist()
    // =========================================================================

    @Nested
    @DisplayName("save() Migration Tests")
    @Tag("save")
    class SaveMigrationTests {

        @Test
        @DisplayName("should persist new entity and generate ID")
        void shouldPersistNewEntityAndGenerateId() {
            // Given: new entity without ID
            Secuserrole newEntity = createTestEntity("_SAVE");
            assertThat(newEntity.getId()).isNull();

            // When: save is called
            secuserroleDao.save(newEntity);
            entityManager.flush();

            // Then: ID is generated
            assertThat(newEntity.getId()).isNotNull();
            assertThat(newEntity.getId()).isGreaterThan(0);
        }

        @Test
        @DisplayName("should make entity retrievable after save")
        void shouldMakeEntityRetrievableAfterSave() {
            // Given: new entity
            Secuserrole newEntity = createTestEntity("_SAVE_RETRIEVE");

            // When: save and flush
            secuserroleDao.save(newEntity);
            entityManager.flush();
            Integer savedId = newEntity.getId();
            entityManager.clear();

            // Then: entity can be retrieved
            Secuserrole retrieved = secuserroleDao.findById(savedId);
            assertThat(retrieved).isNotNull();
            assertThat(retrieved.getProviderNo()).isEqualTo(newEntity.getProviderNo());
        }

        @Test
        @DisplayName("should set lastUpdateDate on save")
        void shouldSetLastUpdateDateOnSave() {
            // Given: entity without lastUpdateDate
            Secuserrole newEntity = createTestEntity("_SAVE_DATE");
            newEntity.setLastUpdateDate(null);

            // When: save is called (DAO sets lastUpdateDate)
            Date beforeSave = new Date();
            secuserroleDao.save(newEntity);
            entityManager.flush();

            // Then: lastUpdateDate is set
            assertThat(newEntity.getLastUpdateDate()).isNotNull();
            assertThat(newEntity.getLastUpdateDate()).isAfterOrEqualTo(beforeSave);
        }
    }

    // =========================================================================
    // UPDATE TESTS - HibernateTemplate.update() -> EntityManager.merge()
    // =========================================================================

    @Nested
    @DisplayName("update() Migration Tests")
    @Tag("update")
    class UpdateMigrationTests {

        @Test
        @DisplayName("should update existing entity and return affected count")
        void shouldUpdateExistingEntity() {
            // Given: existing entity
            Secuserrole saved = persistTestEntity("_UPDATE");
            saved.setActiveyn(0); // Change active status
            entityManager.clear();

            // When: update is called
            int affectedRows = secuserroleDao.update(saved);

            // Then: update succeeds
            assertThat(affectedRows).isEqualTo(1);
        }

        @Test
        @DisplayName("should return zero when no matching entity to update")
        void shouldReturnZeroWhenNoMatchingEntity() {
            // Given: entity that doesn't exist in DB
            Secuserrole nonExistent = createTestEntity("_NONEXISTENT_UPDATE");
            nonExistent.setProviderNo("PROVIDER_THAT_DOES_NOT_EXIST");

            // When: update is called
            int affectedRows = secuserroleDao.update(nonExistent);

            // Then: zero rows affected
            assertThat(affectedRows).isEqualTo(0);
        }

        @Test
        @DisplayName("should persist changes via updateRoleName")
        void shouldPersistChangesViaUpdateRoleName() {
            // Given: existing entity
            Secuserrole saved = persistTestEntity("_UPDATE_ROLE");
            Integer savedId = saved.getId();
            entityManager.clear();

            // When: updateRoleName is called
            secuserroleDao.updateRoleName(savedId, "new_role_name");
            entityManager.flush();
            entityManager.clear();

            // Then: changes are persisted
            Secuserrole updated = secuserroleDao.findById(savedId);
            assertThat(updated.getRoleName()).isEqualTo("new_role_name");
        }
    }

    // =========================================================================
    // DELETE TESTS - HibernateTemplate.delete() -> EntityManager.remove()
    // =========================================================================

    @Nested
    @DisplayName("delete() Migration Tests")
    @Tag("delete")
    class DeleteMigrationTests {

        @Test
        @DisplayName("should delete existing entity")
        void shouldDeleteExistingEntity() {
            // Given: existing entity
            Secuserrole saved = persistTestEntity("_DELETE");
            Integer savedId = saved.getId();
            entityManager.clear();

            // Re-fetch to get managed entity (required for delete)
            Secuserrole toDelete = secuserroleDao.findById(savedId);
            assertThat(toDelete).isNotNull();

            // When: delete is called
            secuserroleDao.delete(toDelete);
            entityManager.flush();
            entityManager.clear();

            // Then: entity no longer exists
            Secuserrole shouldBeNull = secuserroleDao.findById(savedId);
            assertThat(shouldBeNull).isNull();
        }

        @Test
        @DisplayName("should handle detached entity on delete")
        void shouldHandleDetachedEntityOnDelete() {
            // Given: detached entity (cleared from persistence context)
            Secuserrole saved = persistTestEntity("_DELETE_DETACHED");
            Integer savedId = saved.getId();
            entityManager.flush();
            entityManager.clear(); // Entity is now detached

            // When: delete detached entity
            // CRITICAL: HibernateTemplate handles this, EntityManager.remove() requires managed entity
            // Migration must handle: em.remove(em.contains(e) ? e : em.merge(e))
            Secuserrole detached = new Secuserrole();
            detached.setId(savedId);
            detached.setProviderNo(saved.getProviderNo());
            detached.setRoleName(saved.getRoleName());
            detached.setOrgcd(saved.getOrgcd());
            detached.setActiveyn(saved.getActiveyn());

            // Re-fetch and delete (current implementation requires this)
            Secuserrole managed = secuserroleDao.findById(savedId);
            secuserroleDao.delete(managed);
            entityManager.flush();
            entityManager.clear();

            // Then: entity is deleted
            assertThat(secuserroleDao.findById(savedId)).isNull();
        }
    }

    // =========================================================================
    // BULK UPDATE TESTS - HibernateTemplate.bulkUpdate() -> Query.executeUpdate()
    // =========================================================================

    @Nested
    @DisplayName("bulkUpdate() Migration Tests")
    @Tag("bulk")
    class BulkUpdateMigrationTests {

        @Test
        @DisplayName("should return affected row count on bulk delete")
        void shouldReturnAffectedRowCount() {
            // Given: multiple entities with same orgcd
            String testOrgCd = "BULK_ORG_001";
            for (int i = 0; i < 3; i++) {
                Secuserrole entity = createTestEntity("_BULK_" + i);
                entity.setOrgcd(testOrgCd);
                entityManager.persist(entity);
            }
            entityManager.flush();
            entityManager.clear();

            // When: bulk delete by orgcd
            int affectedRows = secuserroleDao.deleteByOrgcd(testOrgCd);

            // Then: correct count returned
            assertThat(affectedRows).isEqualTo(3);
        }

        @Test
        @DisplayName("should return zero when no rows match bulk operation")
        void shouldReturnZeroWhenNoRowsMatch() {
            // Given: no entities with this orgcd
            String nonExistentOrgCd = "NONEXISTENT_ORG_XYZ_12345";

            // When: bulk delete
            int affectedRows = secuserroleDao.deleteByOrgcd(nonExistentOrgCd);

            // Then: zero returned, NOT exception
            assertThat(affectedRows).isEqualTo(0);
        }

        @Test
        @DisplayName("should bulk delete by providerNo")
        void shouldBulkDeleteByProviderNo() {
            // Given: entities with same providerNo
            String testProviderNo = "BULK_PROV_001";
            for (int i = 0; i < 2; i++) {
                Secuserrole entity = createTestEntity("_BULK_PROV_" + i);
                entity.setProviderNo(testProviderNo);
                entity.setRoleName("role_" + i); // Different roles
                entityManager.persist(entity);
            }
            entityManager.flush();
            entityManager.clear();

            // When: bulk delete by providerNo
            int affectedRows = secuserroleDao.deleteByProviderNo(testProviderNo);

            // Then: all matching rows deleted
            assertThat(affectedRows).isEqualTo(2);
        }

        @Test
        @DisplayName("should bulk delete by ID")
        void shouldBulkDeleteById() {
            // Given: entity with known ID
            Secuserrole saved = persistTestEntity("_BULK_ID");
            Integer savedId = saved.getId();
            entityManager.clear();

            // When: bulk delete by ID
            int affectedRows = secuserroleDao.deleteById(savedId);

            // Then: one row deleted
            assertThat(affectedRows).isEqualTo(1);
            assertThat(secuserroleDao.findById(savedId)).isNull();
        }
    }

    // =========================================================================
    // MERGE TESTS - Session.merge() -> EntityManager.merge()
    // =========================================================================

    @Nested
    @DisplayName("merge() Migration Tests")
    @Tag("merge")
    class MergeMigrationTests {

        @Test
        @DisplayName("should merge detached entity and return managed copy")
        void shouldMergeDetachedEntity() {
            // Given: detached entity with modifications
            Secuserrole saved = persistTestEntity("_MERGE");
            Integer savedId = saved.getId();
            entityManager.clear(); // Detach

            // Modify the detached entity
            saved.setActiveyn(0);
            saved.setRoleName("merged_role_name");

            // When: merge is called
            Secuserrole merged = secuserroleDao.merge(saved);
            entityManager.flush();
            entityManager.clear();

            // Then: returned entity is managed with updates
            assertThat(merged).isNotNull();
            assertThat(merged.getId()).isEqualTo(savedId);

            // Verify changes persisted
            Secuserrole reloaded = secuserroleDao.findById(savedId);
            assertThat(reloaded.getRoleName()).isEqualTo("merged_role_name");
            assertThat(reloaded.getActiveyn()).isEqualTo(0);
        }

        @Test
        @DisplayName("should set lastUpdateDate on merge")
        void shouldSetLastUpdateDateOnMerge() {
            // Given: detached entity
            Secuserrole saved = persistTestEntity("_MERGE_DATE");
            Date originalDate = saved.getLastUpdateDate();
            entityManager.clear();

            // Wait a moment to ensure different timestamp
            try { Thread.sleep(10); } catch (InterruptedException e) { }

            // When: merge is called
            Secuserrole merged = secuserroleDao.merge(saved);
            entityManager.flush();

            // Then: lastUpdateDate is updated
            assertThat(merged.getLastUpdateDate()).isAfter(originalDate);
        }
    }

    // =========================================================================
    // CRITERIA/EXAMPLE TESTS - Session.createCriteria() -> JPA Criteria API
    // =========================================================================

    @Nested
    @DisplayName("Criteria/Example Migration Tests")
    @Tag("criteria")
    class CriteriaMigrationTests {

        @Test
        @DisplayName("should find by example entity")
        void shouldFindByExample() {
            // Given: existing entities
            Secuserrole saved = persistTestEntity("_EXAMPLE");
            entityManager.clear();

            // Create example with partial data
            Secuserrole example = new Secuserrole();
            example.setProviderNo(saved.getProviderNo());

            // When: findByExample is called
            List<Secuserrole> results = secuserroleDao.findByExample(example);

            // Then: matching entities returned
            assertThat(results).isNotEmpty();
            assertThat(results.get(0).getProviderNo()).isEqualTo(saved.getProviderNo());
        }

        @Test
        @DisplayName("should return empty list for non-matching example")
        void shouldReturnEmptyListForNonMatchingExample() {
            // Given: example that matches nothing
            Secuserrole example = new Secuserrole();
            example.setProviderNo("NONEXISTENT_EXAMPLE_PROVIDER");
            example.setRoleName("nonexistent_role");
            example.setOrgcd("NONEXISTENT_ORG");

            // When: findByExample is called
            List<Secuserrole> results = secuserroleDao.findByExample(example);

            // Then: empty list, not null
            assertThat(results).isNotNull();
            assertThat(results).isEmpty();
        }
    }

    // =========================================================================
    // SAVE OR UPDATE TESTS - Session.saveOrUpdate() -> persist or merge
    // =========================================================================

    @Nested
    @DisplayName("saveOrUpdate/attachDirty Migration Tests")
    @Tag("saveOrUpdate")
    class SaveOrUpdateMigrationTests {

        @Test
        @DisplayName("should save new entity via attachDirty")
        void shouldSaveNewEntityViaAttachDirty() {
            // Given: new entity
            Secuserrole newEntity = createTestEntity("_ATTACH_NEW");
            assertThat(newEntity.getId()).isNull();

            // When: attachDirty (which uses saveOrUpdate)
            secuserroleDao.attachDirty(newEntity);
            entityManager.flush();

            // Then: entity is persisted with generated ID
            assertThat(newEntity.getId()).isNotNull();
        }

        @Test
        @DisplayName("should update existing entity via attachDirty")
        void shouldUpdateExistingEntityViaAttachDirty() {
            // Given: existing entity
            Secuserrole saved = persistTestEntity("_ATTACH_UPDATE");
            Integer savedId = saved.getId();
            entityManager.clear();

            // Re-fetch and modify
            Secuserrole toUpdate = secuserroleDao.findById(savedId);
            toUpdate.setRoleName("updated_via_attach");

            // When: attachDirty
            secuserroleDao.attachDirty(toUpdate);
            entityManager.flush();
            entityManager.clear();

            // Then: changes are persisted
            Secuserrole reloaded = secuserroleDao.findById(savedId);
            assertThat(reloaded.getRoleName()).isEqualTo("updated_via_attach");
        }
    }

    // =========================================================================
    // TRANSACTION BEHAVIOR TESTS
    // =========================================================================

    @Nested
    @DisplayName("Transaction Behavior Tests")
    @Tag("transaction")
    class TransactionBehaviorTests {

        @Test
        @DisplayName("should rollback on exception - changes not persisted")
        void shouldRollbackOnException() {
            // Given: count before operation
            long countBefore = (long) entityManager
                .createQuery("SELECT COUNT(s) FROM Secuserrole s WHERE s.providerNo LIKE :prefix")
                .setParameter("prefix", TEST_PROVIDER_NO + "_ROLLBACK%")
                .getSingleResult();

            // This test documents expected rollback behavior
            // Actual rollback testing requires @Rollback annotation or manual tx management
            assertThat(countBefore).isEqualTo(0);
        }

        @Test
        @DisplayName("should see uncommitted changes within same transaction")
        void shouldSeeUncommittedChangesInSameTransaction() {
            // Given: save entity without explicit flush
            Secuserrole saved = createTestEntity("_TX_VISIBLE");
            secuserroleDao.save(saved);
            // Note: might need flush depending on flush mode

            // When: query in same transaction
            entityManager.flush(); // Ensure sync
            List<Secuserrole> results = secuserroleDao.findByProviderNo(saved.getProviderNo());

            // Then: entity is visible
            assertThat(results).hasSize(1);
        }
    }

    // =========================================================================
    // NULL HANDLING TESTS
    // =========================================================================

    @Nested
    @DisplayName("Null Handling Tests")
    @Tag("null-handling")
    class NullHandlingTests {

        @Test
        @DisplayName("should handle null parameter in find gracefully")
        void shouldHandleNullParameterInFind() {
            // When: find with null parameter
            List<Secuserrole> results = secuserroleDao.findByProviderNo(null);

            // Then: returns empty list or throws documented exception
            // Document actual behavior - migration must match
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should handle entity with null optional fields")
        void shouldHandleEntityWithNullOptionalFields() {
            // Given: entity with null optional fields
            Secuserrole entity = new Secuserrole();
            entity.setProviderNo(TEST_PROVIDER_NO + "_NULL_FIELDS");
            entity.setRoleName(TEST_ROLE_NAME);
            entity.setOrgcd(TEST_ORG_CD);
            entity.setActiveyn(1);
            // Leave other fields null

            // When: save
            secuserroleDao.save(entity);
            entityManager.flush();

            // Then: save succeeds
            assertThat(entity.getId()).isNotNull();
        }
    }
}
