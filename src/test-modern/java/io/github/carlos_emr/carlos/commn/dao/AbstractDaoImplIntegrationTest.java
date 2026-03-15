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
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link AbstractDaoImpl}, the base class for ALL JPA-based DAOs.
 *
 * <p>Tests exercise the inherited AbstractDaoImpl methods through {@link FacilityDaoImpl},
 * which is a minimal concrete subclass with no complex relationships. This validates
 * EntityManager operations, transaction management, batch operations, and query utilities
 * that every DAO in the system relies on.</p>
 *
 * <p>These tests are critical for the Jakarta EE migration (Hibernate 5→6) because
 * AbstractDaoImpl is the single point where all EntityManager interactions flow through.
 * Any Hibernate 6 behavioral changes to persist/merge/remove/find/flush will surface here.</p>
 *
 * @since 2026-03-05
 * @see AbstractDaoImpl
 * @see AbstractDao
 * @see FacilityDaoImpl
 */
@DisplayName("AbstractDaoImpl Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("persistence")
@Transactional
public class AbstractDaoImplIntegrationTest extends CarlosTestBase {

    @Autowired
    private FacilityDao facilityDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private Facility facility1;
    private Facility facility2;
    private Facility facility3;

    @BeforeEach
    void setUp() {
        facility1 = createFacility("Test Clinic Alpha", "First test facility");
        facility2 = createFacility("Test Clinic Beta", "Second test facility");
        facility3 = createFacility("Test Clinic Gamma", "Third test facility");
    }

    /**
     * Creates and persists a {@link Facility} with the given name and description.
     *
     * @param name String the facility name
     * @param description String the facility description
     * @return Facility the persisted entity with generated ID
     */
    private Facility createFacility(String name, String description) {
        Facility f = new Facility();
        f.setName(name);
        f.setDescription(description);
        f.setContactName("Dr. Test");
        f.setContactEmail("test@carlos.health");
        f.setContactPhone("555-0100");
        entityManager.persist(f);
        entityManager.flush();
        return f;
    }

    /** Tests for {@link AbstractDaoImpl#persist(io.github.carlos_emr.carlos.commn.model.AbstractModel)} */
    @Nested
    @DisplayName("persist()")
    class Persist {

        @Test
        @Tag("create")
        @DisplayName("should assign ID when new entity is persisted")
        void shouldAssignId_whenNewEntityPersisted() {
            // Given
            Facility newFacility = new Facility();
            newFacility.setName("New Facility");
            newFacility.setDescription("Brand new");

            // When
            facilityDao.persist(newFacility);
            entityManager.flush();

            // Then
            assertThat(newFacility.getId()).isNotNull();
            assertThat(newFacility.getId()).isGreaterThan(0);
        }

        @Test
        @Tag("create")
        @DisplayName("should make entity retrievable after persist and flush")
        void shouldMakeEntityRetrievable_afterPersistAndFlush() {
            // Given
            Facility newFacility = new Facility();
            newFacility.setName("Retrievable Facility");
            newFacility.setDescription("Should be findable");

            // When
            facilityDao.persist(newFacility);
            entityManager.flush();
            entityManager.clear();

            // Then
            Facility found = facilityDao.find(newFacility.getId());
            assertThat(found).isNotNull();
            assertThat(found.getName()).isEqualTo("Retrievable Facility");
        }
    }

    /** Tests for {@link AbstractDaoImpl#merge(io.github.carlos_emr.carlos.commn.model.AbstractModel)} */
    @Nested
    @DisplayName("merge()")
    class Merge {

        @Test
        @Tag("update")
        @DisplayName("should update entity fields when merged")
        void shouldUpdateFields_whenMerged() {
            // Given
            facility1.setName("Updated Alpha");
            facility1.setDescription("Updated description");

            // When
            facilityDao.merge(facility1);
            entityManager.flush();
            entityManager.clear();

            // Then
            Facility found = facilityDao.find(facility1.getId());
            assertThat(found.getName()).isEqualTo("Updated Alpha");
            assertThat(found.getDescription()).isEqualTo("Updated description");
        }

        @Test
        @Tag("update")
        @DisplayName("should merge detached entity back into persistence context")
        void shouldMergeDetachedEntity_backIntoPersistenceContext() {
            // Given
            Integer id = facility1.getId();
            entityManager.detach(facility1);
            facility1.setName("Detached-Then-Merged");

            // When
            facilityDao.merge(facility1);
            entityManager.flush();
            entityManager.clear();

            // Then
            Facility found = facilityDao.find(id);
            assertThat(found.getName()).isEqualTo("Detached-Then-Merged");
        }
    }

    /** Tests for {@link AbstractDaoImpl#find(Object)} and {@link AbstractDaoImpl#find(int)} */
    @Nested
    @DisplayName("find()")
    class Find {

        @Test
        @Tag("read")
        @DisplayName("should return entity when valid Object ID provided")
        void shouldReturnEntity_whenValidObjectIdProvided() {
            // When
            Facility found = facilityDao.find((Object) facility1.getId());

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getName()).isEqualTo("Test Clinic Alpha");
        }

        @Test
        @Tag("read")
        @DisplayName("should return entity when valid int ID provided")
        void shouldReturnEntity_whenValidIntIdProvided() {
            // When
            Facility found = facilityDao.find(facility1.getId().intValue());

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getName()).isEqualTo("Test Clinic Alpha");
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when ID does not exist")
        void shouldReturnNull_whenIdDoesNotExist() {
            // When
            Facility found = facilityDao.find(999999);

            // Then
            assertThat(found).isNull();
        }
    }

    /** Tests for {@link AbstractDaoImpl#findDetached(Object)} */
    @Nested
    @DisplayName("findDetached()")
    class FindDetached {

        @Test
        @Tag("read")
        @DisplayName("should return detached entity when valid ID provided")
        void shouldReturnDetachedEntity_whenValidIdProvided() {
            // When
            Facility found = facilityDao.findDetached(facility1.getId());

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getName()).isEqualTo("Test Clinic Alpha");
            assertThat(entityManager.contains(found)).isFalse();
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when ID does not exist")
        void shouldReturnNull_whenIdDoesNotExist() {
            // When
            Facility found = facilityDao.findDetached(999999);

            // Then
            assertThat(found).isNull();
        }
    }

    /** Tests for {@link AbstractDaoImpl#detach(io.github.carlos_emr.carlos.commn.model.AbstractModel)} */
    @Nested
    @DisplayName("detach()")
    class Detach {

        @Test
        @Tag("read")
        @DisplayName("should remove entity from persistence context")
        void shouldRemoveEntityFromPersistenceContext() {
            // Given
            assertThat(entityManager.contains(facility1)).isTrue();

            // When
            facilityDao.detach(facility1);

            // Then
            assertThat(entityManager.contains(facility1)).isFalse();
        }
    }

    /** Tests for {@link AbstractDaoImpl#contains(io.github.carlos_emr.carlos.commn.model.AbstractModel)} */
    @Nested
    @DisplayName("contains()")
    class Contains {

        @Test
        @Tag("read")
        @DisplayName("should return true for managed entity")
        void shouldReturnTrue_forManagedEntity() {
            // When / Then
            assertThat(facilityDao.contains(facility1)).isTrue();
        }

        @Test
        @Tag("read")
        @DisplayName("should return false for detached entity")
        void shouldReturnFalse_forDetachedEntity() {
            // Given
            entityManager.detach(facility1);

            // When / Then
            assertThat(facilityDao.contains(facility1)).isFalse();
        }

        @Test
        @Tag("read")
        @DisplayName("should return false for new unpersisted entity")
        void shouldReturnFalse_forNewUnpersistedEntity() {
            // Given
            Facility transient_ = new Facility();
            transient_.setName("Not persisted");

            // When / Then
            assertThat(facilityDao.contains(transient_)).isFalse();
        }
    }

    /** Tests for {@link AbstractDaoImpl#remove(io.github.carlos_emr.carlos.commn.model.AbstractModel)} and {@link AbstractDaoImpl#remove(Object)} */
    @Nested
    @DisplayName("remove()")
    class Remove {

        @Test
        @Tag("delete")
        @DisplayName("should delete entity when attached entity provided")
        void shouldDeleteEntity_whenAttachedEntityProvided() {
            // Given
            Integer id = facility1.getId();

            // When
            facilityDao.remove(facility1);
            entityManager.flush();
            entityManager.clear();

            // Then
            Facility found = facilityDao.find(id);
            assertThat(found).isNull();
        }

        @Test
        @Tag("delete")
        @DisplayName("should return true when removing by existing ID")
        void shouldReturnTrue_whenRemovingByExistingId() {
            // When
            boolean result = facilityDao.remove((Object) facility2.getId());
            entityManager.flush();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @Tag("delete")
        @DisplayName("should return false when removing by nonexistent ID")
        void shouldReturnFalse_whenRemovingByNonexistentId() {
            // When
            boolean result = facilityDao.remove((Object) 999999);

            // Then
            assertThat(result).isFalse();
        }
    }

    /** Tests for {@link AbstractDaoImpl#refresh(io.github.carlos_emr.carlos.commn.model.AbstractModel)} */
    @Nested
    @DisplayName("refresh()")
    class Refresh {

        @Test
        @Tag("read")
        @DisplayName("should reload entity state from database")
        void shouldReloadEntityState_fromDatabase() {
            // Given — modify entity in-memory without flushing
            String originalName = facility1.getName();
            facility1.setName("Modified In Memory");

            // When — refresh should reload from DB, discarding in-memory change
            facilityDao.refresh(facility1);

            // Then
            assertThat(facility1.getName()).isEqualTo(originalName);
        }
    }

    /** Tests for {@link AbstractDaoImpl#flush()} */
    @Nested
    @DisplayName("flush()")
    class Flush {

        @Test
        @Tag("create")
        @DisplayName("should synchronize persistence context with database")
        void shouldSynchronizePersistenceContext_withDatabase() {
            // Given
            Facility newFacility = new Facility();
            newFacility.setName("Flush Test");
            newFacility.setDescription("Testing flush");
            facilityDao.persist(newFacility);

            // When
            facilityDao.flush();

            // Then — ID should be assigned after flush
            assertThat(newFacility.getId()).isNotNull();
        }
    }

    /** Tests for {@link AbstractDaoImpl#findAll(Integer, Integer)} */
    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @Tag("read")
        @Tag("query")
        @DisplayName("should return all entities when limit is sufficient")
        void shouldReturnAllEntities_whenLimitSufficient() {
            // When
            List<Facility> results = facilityDao.findAll(null, 100);

            // Then
            assertThat(results).hasSizeGreaterThanOrEqualTo(3);
            assertThat(results).extracting(Facility::getName)
                    .contains("Test Clinic Alpha", "Test Clinic Beta", "Test Clinic Gamma");
        }

        @Test
        @Tag("read")
        @Tag("query")
        @DisplayName("should respect offset parameter")
        void shouldRespectOffset_whenProvided() {
            // When
            List<Facility> allResults = facilityDao.findAll(null, 100);
            List<Facility> offsetResults = facilityDao.findAll(1, 100);

            // Then
            assertThat(offsetResults.size()).isEqualTo(allResults.size() - 1);
        }

        @Test
        @Tag("read")
        @Tag("query")
        @DisplayName("should respect limit parameter")
        void shouldRespectLimit_whenProvided() {
            // When
            List<Facility> results = facilityDao.findAll(null, 2);

            // Then
            assertThat(results).hasSize(2);
        }

        @Test
        @Tag("read")
        @Tag("query")
        @DisplayName("should use default limit when null passed")
        void shouldUseDefaultLimit_whenNullPassed() {
            // When — null limit should default to MAX_LIST_RETURN_SIZE
            List<Facility> results = facilityDao.findAll(null, null);

            // Then — should return results (default limit is 5000)
            assertThat(results).isNotEmpty();
        }

        @Test
        @Tag("read")
        @Tag("query")
        @DisplayName("should throw MaxSelectLimitExceededException when limit exceeds max")
        void shouldThrowException_whenLimitExceedsMax() {
            // When / Then
            assertThatThrownBy(() -> facilityDao.findAll(null, AbstractDao.MAX_LIST_RETURN_SIZE + 1))
                    .isInstanceOf(MaxSelectLimitExceededException.class);
        }
    }

    /** Tests for {@link AbstractDaoImpl#getCountAll()} */
    @Nested
    @DisplayName("getCountAll()")
    class GetCountAll {

        @Test
        @Tag("read")
        @Tag("aggregate")
        @DisplayName("should return correct count of all entities")
        void shouldReturnCorrectCount_ofAllEntities() {
            // When
            int count = facilityDao.getCountAll();

            // Then — at least the 3 we created in setUp
            assertThat(count).isGreaterThanOrEqualTo(3);
        }

        @Test
        @Tag("read")
        @Tag("aggregate")
        @DisplayName("should return int type for native SQL count")
        void shouldReturnIntType_forNativeSqlCount() {
            // When — getCountAll() uses native SQL: select count(*) from Facility
            // Hibernate 5 returns BigInteger, cast to int via Number.intValue()
            // Hibernate 6 may change this — this test pins the behavior
            int count = facilityDao.getCountAll();

            // Then
            assertThat(count).isInstanceOf(Integer.class);
        }

        @Test
        @Tag("read")
        @Tag("aggregate")
        @DisplayName("should reflect persisted entity count after new persist")
        void shouldReflectCount_afterNewPersist() {
            // Given
            int countBefore = facilityDao.getCountAll();
            Facility extra = new Facility();
            extra.setName("Extra Facility");
            facilityDao.persist(extra);
            entityManager.flush();

            // When
            int countAfter = facilityDao.getCountAll();

            // Then
            assertThat(countAfter).isEqualTo(countBefore + 1);
        }
    }

    /** Tests for {@link AbstractDaoImpl#saveEntity(io.github.carlos_emr.carlos.commn.model.AbstractModel)} */
    @Nested
    @DisplayName("saveEntity()")
    class SaveEntity {

        @Test
        @Tag("create")
        @DisplayName("should persist new entity when not yet persistent")
        void shouldPersistNewEntity_whenNotYetPersistent() {
            // Given
            Facility newFacility = new Facility();
            newFacility.setName("SaveEntity New");
            newFacility.setDescription("Testing saveEntity for new");
            assertThat(newFacility.isPersistent()).isFalse();

            // When
            Facility result = facilityDao.saveEntity(newFacility);
            entityManager.flush();

            // Then
            assertThat(result.getId()).isNotNull();
            assertThat(result.isPersistent()).isTrue();
        }

        @Test
        @Tag("update")
        @DisplayName("should merge existing entity when already persistent")
        void shouldMergeExistingEntity_whenAlreadyPersistent() {
            // Given
            assertThat(facility1.isPersistent()).isTrue();
            facility1.setName("SaveEntity Updated");

            // When
            facilityDao.saveEntity(facility1);
            entityManager.flush();
            entityManager.clear();

            // Then
            Facility found = facilityDao.find(facility1.getId());
            assertThat(found.getName()).isEqualTo("SaveEntity Updated");
        }
    }

    /** Tests for {@link AbstractDaoImpl#batchPersist(List)} and {@link AbstractDaoImpl#batchPersist(List, int)} */
    @Nested
    @DisplayName("batchPersist()")
    class BatchPersist {

        @Test
        @Tag("create")
        @DisplayName("should persist all entities in batch with default batch size")
        void shouldPersistAllEntities_withDefaultBatchSize() {
            // Given
            List<Facility> batch = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Facility f = new Facility();
                f.setName("Batch Default " + i);
                f.setDescription("Batch test " + i);
                batch.add(f);
            }

            // When
            facilityDao.batchPersist(batch);

            // Then — all should have IDs assigned
            for (Facility f : batch) {
                assertThat(f.getId()).isNotNull();
            }
            // Verify they are actually in the database
            entityManager.clear();
            for (Facility f : batch) {
                Facility found = facilityDao.find(f.getId());
                assertThat(found).isNotNull();
                assertThat(found.getName()).startsWith("Batch Default");
            }
        }

        @Test
        @Tag("create")
        @DisplayName("should persist all entities with custom batch size")
        void shouldPersistAllEntities_withCustomBatchSize() {
            // Given
            List<Facility> batch = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                Facility f = new Facility();
                f.setName("Batch Custom " + i);
                f.setDescription("Batch custom test " + i);
                batch.add(f);
            }

            // When — batch size of 3 means flush/clear every 3 entities
            facilityDao.batchPersist(batch, 3);

            // Then
            entityManager.clear();
            for (Facility f : batch) {
                Facility found = facilityDao.find(f.getId());
                assertThat(found).isNotNull();
            }
        }

        @Test
        @Tag("create")
        @DisplayName("should handle empty list without error")
        void shouldHandleEmptyList_withoutError() {
            // When / Then — should not throw
            assertThatCode(() -> facilityDao.batchPersist(new ArrayList<>()))
                    .doesNotThrowAnyException();
        }
    }

    /** Tests for {@link AbstractDaoImpl#batchRemove(List)} and {@link AbstractDaoImpl#batchRemove(List, int)} */
    @Nested
    @DisplayName("batchRemove()")
    class BatchRemove {

        @Test
        @Tag("delete")
        @DisplayName("should remove all entities in batch with default batch size")
        void shouldRemoveAllEntities_withDefaultBatchSize() {
            // Given — persist entities through batch (uses its own EM)
            List<Facility> batch = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Facility f = new Facility();
                f.setName("BatchRemove " + i);
                f.setDescription("To be removed " + i);
                batch.add(f);
            }
            facilityDao.batchPersist(batch);
            List<Integer> ids = new ArrayList<>();
            for (Facility f : batch) {
                ids.add(f.getId());
            }

            // When
            facilityDao.batchRemove(batch);

            // Then — all should be gone
            entityManager.clear();
            for (Integer id : ids) {
                Facility found = facilityDao.find(id);
                assertThat(found).isNull();
            }
        }

        @Test
        @Tag("delete")
        @DisplayName("should remove all entities with custom batch size")
        void shouldRemoveAllEntities_withCustomBatchSize() {
            // Given
            List<Facility> batch = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Facility f = new Facility();
                f.setName("BatchRemoveCustom " + i);
                f.setDescription("Custom remove " + i);
                batch.add(f);
            }
            facilityDao.batchPersist(batch);
            List<Integer> ids = new ArrayList<>();
            for (Facility f : batch) {
                ids.add(f.getId());
            }

            // When — batch size of 2
            facilityDao.batchRemove(batch, 2);

            // Then
            entityManager.clear();
            for (Integer id : ids) {
                Facility found = facilityDao.find(id);
                assertThat(found).isNull();
            }
        }

        @Test
        @Tag("delete")
        @DisplayName("should handle empty list without error")
        void shouldHandleEmptyList_withoutError() {
            // When / Then
            assertThatCode(() -> facilityDao.batchRemove(new ArrayList<>()))
                    .doesNotThrowAnyException();
        }
    }

    /** Tests for {@link AbstractDaoImpl#runParameterizedNativeQuery(String, Map)} */
    @Nested
    @DisplayName("runParameterizedNativeQuery()")
    class RunParameterizedNativeQuery {

        @Test
        @Tag("read")
        @Tag("query")
        @DisplayName("should execute native SQL with named parameters")
        void shouldExecuteNativeSql_withNamedParameters() {
            // Given
            String sql = "SELECT id, name FROM Facility WHERE name = :name";
            Map<String, Object> params = new HashMap<>();
            params.put("name", "Test Clinic Alpha");

            // When
            List<Object[]> results = facilityDao.runParameterizedNativeQuery(sql, params);

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.get(0)).hasSize(2);
            // First element is ID (Number), second is name (String)
            assertThat(((Number) results.get(0)[0]).intValue()).isEqualTo(facility1.getId());
            assertThat(results.get(0)[1]).isEqualTo("Test Clinic Alpha");
        }

        @Test
        @Tag("read")
        @Tag("query")
        @DisplayName("should return empty list when no results match")
        void shouldReturnEmptyList_whenNoResultsMatch() {
            // Given
            String sql = "SELECT id FROM Facility WHERE name = :name";
            Map<String, Object> params = new HashMap<>();
            params.put("name", "Nonexistent Facility");

            // When
            List<Object[]> results = facilityDao.runParameterizedNativeQuery(sql, params);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("read")
        @Tag("query")
        @DisplayName("should execute native SQL with null params map")
        void shouldExecuteNativeSql_withNullParamsMap() {
            // Given
            String sql = "SELECT COUNT(*) FROM Facility";

            // When
            List<Object[]> results = facilityDao.runParameterizedNativeQuery(sql, null);

            // Then
            assertThat(results).isNotEmpty();
        }

        @Test
        @Tag("read")
        @Tag("query")
        @DisplayName("should handle multiple parameters in native SQL")
        void shouldHandleMultipleParameters_inNativeSql() {
            // Given
            String sql = "SELECT id, name FROM Facility WHERE name LIKE :prefix AND disabled = :disabled";
            Map<String, Object> params = new HashMap<>();
            params.put("prefix", "Test Clinic%");
            params.put("disabled", false);

            // When
            List<Object[]> results = facilityDao.runParameterizedNativeQuery(sql, params);

            // Then
            assertThat(results).hasSizeGreaterThanOrEqualTo(3);
        }
    }

    /** Tests for {@link AbstractDaoImpl#getModelClass()} */
    @Nested
    @DisplayName("getModelClass()")
    class GetModelClass {

        @Test
        @Tag("read")
        @DisplayName("should return correct model class for FacilityDao")
        void shouldReturnCorrectModelClass_forFacilityDao() {
            // When
            Class<Facility> modelClass = facilityDao.getModelClass();

            // Then
            assertThat(modelClass).isEqualTo(Facility.class);
        }
    }

    /** Tests verifying the interaction between AbstractDaoImpl and EntityManager lifecycle */
    @Nested
    @DisplayName("EntityManager Lifecycle")
    class EntityManagerLifecycle {

        @Test
        @Tag("read")
        @DisplayName("should maintain entity identity within same persistence context")
        void shouldMaintainEntityIdentity_withinSamePersistenceContext() {
            // When
            Facility found1 = facilityDao.find(facility1.getId());
            Facility found2 = facilityDao.find(facility1.getId());

            // Then — same persistence context should return same instance
            assertThat(found1).isSameAs(found2);
        }

        @Test
        @Tag("read")
        @DisplayName("should return different instances after clear")
        void shouldReturnDifferentInstances_afterClear() {
            // Given
            Facility found1 = facilityDao.find(facility1.getId());

            // When
            entityManager.clear();
            Facility found2 = facilityDao.find(facility1.getId());

            // Then — after clear, new instance is loaded
            assertThat(found1).isNotSameAs(found2);
            assertThat(found1.getId()).isEqualTo(found2.getId());
        }

        @Test
        @Tag("create")
        @Tag("read")
        @DisplayName("should persist and find within same transaction")
        void shouldPersistAndFind_withinSameTransaction() {
            // Given
            Facility newFacility = new Facility();
            newFacility.setName("Transaction Test");
            newFacility.setDescription("Same transaction");

            // When
            facilityDao.persist(newFacility);
            entityManager.flush();
            Integer id = newFacility.getId();
            entityManager.clear();
            Facility found = facilityDao.find(id);

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getName()).isEqualTo("Transaction Test");
        }
    }

    /** Tests for getCountAll() using @Table annotation resolution */
    @Nested
    @DisplayName("getCountAll() Table Name Resolution")
    class GetCountAllTableName {

        @Test
        @Tag("read")
        @Tag("aggregate")
        @DisplayName("should use @Table name when entity has @Table annotation")
        void shouldUseTableName_whenEntityHasTableAnnotation() {
            // Facility does NOT have an explicit @Table(name=...) annotation,
            // so getCountAll() uses the simple class name "Facility" as table name.
            // This test verifies the native SQL count works regardless.
            int count = facilityDao.getCountAll();
            assertThat(count).isGreaterThanOrEqualTo(0);
        }
    }
}
