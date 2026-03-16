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
import io.github.carlos_emr.carlos.model.security.Secobjprivilege;
import io.github.carlos_emr.carlos.model.security.Secobjectname;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import io.github.carlos_emr.carlos.test.base.HibernateTemplate;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link SecobjprivilegeDao} verifying Hibernate mapping
 * and HQL query correctness for the security object privilege module.
 *
 * <p>The {@code Secobjprivilege} entity uses a <b>composite key</b> consisting of
 * {@code roleusergroup} (roleUserGroup column) and {@code objectname_code} (objectName column),
 * mapped via HBM XML ({@code Secobjprivilege.hbm.xml}). Additional mapped properties are
 * {@code privilege_code}, {@code priority}, and {@code providerNo}.</p>
 *
 * <p><b>Test Coverage Summary:</b></p>
 * <ul>
 *   <li><b>save()</b> - Verifies {@code HibernateTemplate.saveOrUpdate()} persists entities
 *       and validates null-argument rejection.</li>
 *   <li><b>delete()</b> - Verifies {@code HibernateTemplate.delete()} removes a single
 *       entity by instance reference.</li>
 *   <li><b>deleteByRoleName()</b> - Exercises the HQL bulk delete:
 *       {@code delete Secobjprivilege as model where model.roleusergroup =?0}</li>
 *   <li><b>getFunctionDesc()</b> - Exercises the HQL scalar select:
 *       {@code select description from Secobjectname obj where obj.objectname='...'}</li>
 *   <li><b>getByObjectNameAndRoles()</b> - Exercises the HQL entity query:
 *       {@code from Secobjprivilege obj where obj.objectname_code='...'}
 *       followed by Java-side filtering against a list of role names.</li>
 * </ul>
 *
 * <p><b>Methods intentionally NOT tested:</b></p>
 * <ul>
 *   <li>{@code update(Secobjprivilege)} - Uses raw {@code sessionFactory.getCurrentSession()}
 *       followed by {@code session.close()}, which conflicts with Spring's
 *       {@code @Transactional} test context that manages the session lifecycle.
 *       Calling {@code session.close()} inside a Spring-managed transaction causes
 *       session state exceptions.</li>
 *   <li>{@code findByProperty(String, Object)} - Same raw session with {@code session.close()}
 *       issue as {@code update()}.</li>
 *   <li>{@code getByRoles(List)} - Same raw session issue as above.</li>
 *   <li>{@code getFunctions(String)} - Delegates to {@code findByProperty()}, which has the
 *       raw session issue described above.</li>
 *   <li>{@code getAccessDesc(String)} - Queries the {@code Secprivilege} entity, which may
 *       not be mapped in the test persistence context.</li>
 * </ul>
 *
 * @since 2026-02-09
 * @see SecobjprivilegeDao
 * @see SecobjprivilegeDaoImpl
 * @see Secobjprivilege
 */
@DisplayName("SecobjprivilegeDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("security")
@Transactional
public class SecobjprivilegeDaoIntegrationTest extends CarlosTestBase {

    /**
     * The DAO under test, autowired from the Spring test application context.
     * Backed by {@link SecobjprivilegeDaoImpl}, which extends
     * {@code HibernateDaoSupport} and uses both {@code HibernateTemplate}
     * and raw {@code sessionFactory.getCurrentSession()} for different methods.
     */
    @Autowired
    private SecobjprivilegeDao secobjprivilegeDao;

    /**
     * JPA {@link EntityManager} used for test setup (inserting fixture data)
     * and verification (native SQL queries to confirm DAO behavior independently
     * of the DAO itself). Bound to the test persistence unit configured in
     * {@code test-context-full.xml}.
     */
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /**
     * HibernateTemplate for flushing the Hibernate Session.
     * SecobjprivilegeDaoImpl extends HibernateDaoSupport, so its writes go through
     * the standalone Hibernate Session, not the JPA EntityManager. Calling
     * entityManager.flush() only flushes the JPA persistence context, not the
     * Hibernate Session. We must flush via HibernateTemplate to push pending
     * Hibernate writes to the database before native SQL verification queries.
     */
    @Autowired
    private HibernateTemplate hibernateTemplate;

    /**
     * Native SQL that selects all columns from the {@code secObjPrivilege} table
     * for a given composite key (roleUserGroup + objectName). Used in save tests
     * to verify that the DAO correctly persisted all five mapped columns:
     * {@code roleUserGroup}, {@code objectName}, {@code privilege},
     * {@code priority}, and {@code provider_no}.
     */
    private static final String NATIVE_SELECT_PRIVILEGE =
            "SELECT roleUserGroup, objectName, privilege, priority, provider_no FROM secObjPrivilege WHERE roleUserGroup = ?1 AND objectName = ?2";

    /**
     * Native SQL that checks for existence of a row by composite key
     * (roleUserGroup + objectName). Returns the {@code roleUserGroup} column
     * to confirm presence or absence. Used in delete tests to verify that
     * the target row was removed while other rows remain intact.
     */
    private static final String NATIVE_CHECK_BY_ROLE_AND_OBJ =
            "SELECT roleUserGroup FROM secObjPrivilege WHERE roleUserGroup = ?1 AND objectName = ?2";

    /**
     * Native SQL that checks for existence of any rows matching a given
     * {@code roleUserGroup}. Returns all matching {@code roleUserGroup} values.
     * Used in bulk delete tests to verify that rows belonging to a different
     * role were not affected by the {@code deleteByRoleName()} operation.
     */
    private static final String NATIVE_CHECK_BY_ROLE =
            "SELECT roleUserGroup FROM secObjPrivilege WHERE roleUserGroup = ?1";

    /**
     * Native SQL that inserts a row into the {@code secObjectName} table,
     * which backs the {@link Secobjectname} entity. The {@code orgapplicable}
     * column is hardcoded to {@code 1} (applicable). Used to set up test
     * fixture data for {@code getFunctionDesc()} tests, which query
     * {@code secObjectName} to retrieve descriptions by object name.
     */
    private static final String NATIVE_INSERT_SECOBJECTNAME =
            "INSERT INTO secObjectName (objectName, description, orgapplicable) VALUES (?1, ?2, 1)";

    /**
     * Creates and persists a {@link Secobjprivilege} with the given composite key
     * and privilege details.
     *
     * <p>After calling {@code save()}, the method flushes the persistence context
     * to force an immediate SQL INSERT, ensuring the row is visible to subsequent
     * native SQL verification queries within the same transaction.</p>
     *
     * @param roleUserGroup String the role or user group name (part of composite key)
     * @param objectName    String the security object name (part of composite key)
     * @param privilege     String the privilege code (e.g., "r", "w", "x")
     * @param priority      Integer the priority level
     * @param providerNo    String the provider number
     * @return Secobjprivilege the persisted entity
     */
    private Secobjprivilege createAndSavePrivilege(String roleUserGroup, String objectName,
                                                    String privilege, Integer priority,
                                                    String providerNo) {
        Secobjprivilege priv = new Secobjprivilege(roleUserGroup, objectName,
                privilege, priority, providerNo);
        secobjprivilegeDao.save(priv);
        // Flush the Hibernate Session (not JPA EntityManager) to synchronize
        // HibernateDaoSupport writes with the database
        hibernateTemplate.flush();
        return priv;
    }

    /**
     * Inserts a {@link Secobjectname} record using native SQL.
     * This is needed for testing {@code getFunctionDesc()} which queries the
     * {@code secObjectName} table via the {@code Secobjectname} entity.
     *
     * <p>Native SQL is used instead of the DAO because {@code Secobjectname}
     * is not the entity under test and may not have a dedicated DAO wired
     * into the test context. The flush ensures immediate visibility.</p>
     *
     * @param objectName  String the object name (primary key)
     * @param description String the description to look up
     */
    private void insertSecobjectname(String objectName, String description) {
        entityManager.createNativeQuery(NATIVE_INSERT_SECOBJECTNAME)
                .setParameter(1, objectName)
                .setParameter(2, description)
                .executeUpdate();
        entityManager.flush();
    }

    /**
     * Tests for the {@link SecobjprivilegeDao#save(Secobjprivilege)} method.
     *
     * <p>The {@code save()} implementation delegates to
     * {@code HibernateTemplate.saveOrUpdate()}, which performs an INSERT for
     * new entities. These tests verify both successful persistence with all
     * five mapped columns and proper null-argument rejection.</p>
     */
    @Nested
    @DisplayName("save() operations")
    class SaveOperations {

        /**
         * Verifies that {@code save()} persists a new {@link Secobjprivilege} entity
         * with all five mapped columns correctly written to the database.
         *
         * <p><b>DAO method:</b> {@code save(Secobjprivilege)}</p>
         * <p><b>Hibernate operation:</b> {@code HibernateTemplate.saveOrUpdate()} which
         * generates an INSERT into the {@code secObjPrivilege} table.</p>
         * <p><b>Verification:</b> Uses native SQL ({@link #NATIVE_SELECT_PRIVILEGE})
         * to read back all five columns and assert each value independently,
         * bypassing the DAO to confirm actual database state.</p>
         */
        @Test
        @Tag("create")
        @DisplayName("should save secobjprivilege when valid data provided")
        void shouldSaveSecobjprivilege_whenValidDataProvided() {
            // Given
            Secobjprivilege priv = new Secobjprivilege("admin", "_demographic",
                    "r", 1, "999998");

            // When
            secobjprivilegeDao.save(priv);
            hibernateTemplate.flush();

            // Then - verify via native query to confirm persistence
            @SuppressWarnings("unchecked")
            List<Object[]> results = entityManager.createNativeQuery(NATIVE_SELECT_PRIVILEGE)
                    .setParameter(1, "admin")
                    .setParameter(2, "_demographic")
                    .getResultList();

            assertThat(results).hasSize(1);
            Object[] row = results.get(0);
            // Verify each column of the composite-keyed entity individually
            assertThat(row[0]).isEqualTo("admin");
            assertThat(row[1]).isEqualTo("_demographic");
            assertThat(row[2]).isEqualTo("r");
            // Priority is returned as a Number from native SQL; cast for comparison
            assertThat(((Number) row[3]).intValue()).isEqualTo(1);
            assertThat(row[4]).isEqualTo("999998");
        }

        /**
         * Verifies that {@code save()} rejects a null argument with an
         * {@link IllegalArgumentException}.
         *
         * <p><b>DAO method:</b> {@code save(null)}</p>
         * <p><b>Expected behavior:</b> The implementation has an explicit null check
         * that throws {@code IllegalArgumentException} before reaching
         * {@code HibernateTemplate.saveOrUpdate()}. No HQL is exercised.</p>
         */
        @Test
        @Tag("create")
        @DisplayName("should throw exception when save null")
        void shouldThrowException_whenSaveNull() {
            // When / Then
            assertThatThrownBy(() -> secobjprivilegeDao.save(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests for the {@link SecobjprivilegeDao#delete(Secobjprivilege)} method.
     *
     * <p>The {@code delete()} implementation delegates to
     * {@code HibernateTemplate.delete()}, which issues a SQL DELETE for the
     * given managed entity instance. These tests verify that the entity is
     * removed from the database after the operation.</p>
     */
    @Nested
    @DisplayName("delete() operations")
    class DeleteOperations {

        /**
         * Verifies that {@code delete()} removes a previously persisted
         * {@link Secobjprivilege} entity from the {@code secObjPrivilege} table.
         *
         * <p><b>DAO method:</b> {@code delete(Secobjprivilege)}</p>
         * <p><b>Hibernate operation:</b> {@code HibernateTemplate.delete()} which
         * generates a SQL DELETE targeting the row identified by the entity's
         * composite key (roleUserGroup + objectName).</p>
         * <p><b>Verification strategy:</b> Uses native SQL
         * ({@link #NATIVE_CHECK_BY_ROLE_AND_OBJ}) to confirm the row exists
         * before deletion and is absent afterward.</p>
         */
        @Test
        @Tag("delete")
        @DisplayName("should delete secobjprivilege when valid instance provided")
        void shouldDeleteSecobjprivilege_whenValidInstanceProvided() {
            // Given
            Secobjprivilege priv = createAndSavePrivilege("delRole", "_delObject",
                    "w", 2, "999997");

            // Verify it exists before deletion
            @SuppressWarnings("unchecked")
            List<Object[]> beforeDelete = entityManager
                    .createNativeQuery(NATIVE_CHECK_BY_ROLE_AND_OBJ)
                    .setParameter(1, "delRole")
                    .setParameter(2, "_delObject")
                    .getResultList();
            assertThat(beforeDelete).hasSize(1);

            // When
            secobjprivilegeDao.delete(priv);
            // Flush to force the DELETE SQL to execute immediately
            hibernateTemplate.flush();

            // Then - confirm the row no longer exists in the database
            @SuppressWarnings("unchecked")
            List<Object[]> afterDelete = entityManager
                    .createNativeQuery(NATIVE_CHECK_BY_ROLE_AND_OBJ)
                    .setParameter(1, "delRole")
                    .setParameter(2, "_delObject")
                    .getResultList();
            assertThat(afterDelete).isEmpty();
        }
    }

    /**
     * Tests for the {@link SecobjprivilegeDao#deleteByRoleName(String)} method.
     *
     * <p>The {@code deleteByRoleName()} implementation uses
     * {@code HibernateTemplate.bulkUpdate()} with the HQL statement:
     * {@code delete Secobjprivilege as model where model.roleusergroup =?0}.
     * This performs a bulk SQL DELETE removing all privilege rows for a
     * given role name and returns the count of deleted rows.</p>
     */
    @Nested
    @DisplayName("deleteByRoleName() operations")
    class DeleteByRoleNameOperations {

        /**
         * Verifies that {@code deleteByRoleName()} removes all privileges
         * associated with the specified role while leaving other roles' privileges
         * intact.
         *
         * <p><b>DAO method:</b> {@code deleteByRoleName(String)}</p>
         * <p><b>HQL exercised:</b>
         * {@code delete Secobjprivilege as model where model.roleusergroup =?0}</p>
         * <p><b>Verification strategy:</b> Creates two privileges for the target
         * role and one for a different role. After deletion, asserts that the
         * return count is 2 and that the other role's privilege remains via
         * native SQL ({@link #NATIVE_CHECK_BY_ROLE}).</p>
         */
        @Test
        @Tag("delete")
        @DisplayName("should delete by role name when role exists")
        void shouldDeleteByRoleName_whenRoleExists() {
            // Given - create multiple privileges for the same role
            createAndSavePrivilege("bulkDelRole", "_object1", "r", 1, "999996");
            createAndSavePrivilege("bulkDelRole", "_object2", "w", 2, "999996");
            // Create a privilege for a different role to verify it is unaffected
            createAndSavePrivilege("otherRole", "_object3", "x", 1, "999995");

            // When - bulk delete all privileges for "bulkDelRole"
            int deleted = secobjprivilegeDao.deleteByRoleName("bulkDelRole");

            // Then - exactly 2 rows should have been deleted
            assertThat(deleted).isEqualTo(2);

            // Verify the other role's privilege was not deleted
            hibernateTemplate.flush();
            // Clear the persistence context to force a fresh read from the database
            hibernateTemplate.clear();
            @SuppressWarnings("unchecked")
            List<Object[]> remaining = entityManager
                    .createNativeQuery(NATIVE_CHECK_BY_ROLE)
                    .setParameter(1, "otherRole")
                    .getResultList();
            assertThat(remaining).hasSize(1);
        }

        /**
         * Verifies that {@code deleteByRoleName()} returns zero when no
         * privileges exist for the specified role name.
         *
         * <p><b>DAO method:</b> {@code deleteByRoleName(String)}</p>
         * <p><b>HQL exercised:</b>
         * {@code delete Secobjprivilege as model where model.roleusergroup =?0}</p>
         * <p><b>Expected result:</b> Return value of 0 indicating no rows matched
         * the WHERE clause for the non-existent role name.</p>
         */
        @Test
        @Tag("delete")
        @DisplayName("should return zero when delete by non-existent role name")
        void shouldReturnZero_whenDeleteByNonExistentRoleName() {
            // When - attempt to delete privileges for a role that does not exist
            int deleted = secobjprivilegeDao.deleteByRoleName("nonExistentRole_XYZ");

            // Then - no rows should have been affected
            assertThat(deleted).isEqualTo(0);
        }
    }

    /**
     * Tests for the {@link SecobjprivilegeDao#getFunctionDesc(String)} method.
     *
     * <p>The {@code getFunctionDesc()} implementation queries the {@link Secobjectname}
     * entity (not {@code Secobjprivilege}) using the HQL:
     * {@code select description from Secobjectname obj where obj.objectname='...'}.
     * It returns the description string for the given object name, or an empty
     * string if no match is found.</p>
     *
     * <p>Test data for the {@code secObjectName} table is inserted via native SQL
     * using {@link #insertSecobjectname(String, String)} because {@code Secobjectname}
     * does not have a dedicated DAO wired into the test context.</p>
     */
    @Nested
    @DisplayName("getFunctionDesc() operations")
    class GetFunctionDescOperations {

        /**
         * Verifies that {@code getFunctionDesc()} returns the description
         * when a matching {@code Secobjectname} record exists.
         *
         * <p><b>DAO method:</b> {@code getFunctionDesc(String)}</p>
         * <p><b>HQL exercised:</b>
         * {@code select description from Secobjectname obj where obj.objectname='_testFunc1'}</p>
         * <p><b>Setup:</b> Inserts a {@code secObjectName} row via native SQL
         * with objectName {@code "_testFunc1"} and a known description.</p>
         */
        @Test
        @Tag("read")
        @DisplayName("should return function description when function code exists")
        void shouldReturnFunctionDescription_whenFunctionCodeExists() {
            // Given - insert a Secobjectname record via native SQL
            insertSecobjectname("_testFunc1", "Test Function One Description");

            // When - look up the description by object name
            String description = secobjprivilegeDao.getFunctionDesc("_testFunc1");

            // Then - the description should match what was inserted
            assertThat(description).isEqualTo("Test Function One Description");
        }

        /**
         * Verifies that {@code getFunctionDesc()} returns an empty string
         * when no {@code Secobjectname} record matches the given function code.
         *
         * <p><b>DAO method:</b> {@code getFunctionDesc(String)}</p>
         * <p><b>HQL exercised:</b>
         * {@code select description from Secobjectname obj where obj.objectname='_nonExistentFunc_XYZ'}</p>
         * <p><b>Expected result:</b> Empty string {@code ""} because the HQL
         * returns an empty list, triggering the {@code else} branch in the
         * implementation.</p>
         */
        @Test
        @Tag("read")
        @DisplayName("should return empty string when function code does not exist")
        void shouldReturnEmptyString_whenFunctionCodeDoesNotExist() {
            // When - look up a function code that has no matching record
            String description = secobjprivilegeDao.getFunctionDesc("_nonExistentFunc_XYZ");

            // Then - should return empty string per the implementation's else branch
            assertThat(description).isEmpty();
        }
    }

    /**
     * Tests for the {@link SecobjprivilegeDao#getByObjectNameAndRoles(String, List)} method.
     *
     * <p>The {@code getByObjectNameAndRoles()} implementation first retrieves all
     * {@code Secobjprivilege} entities matching a given {@code objectname_code}
     * using the HQL: {@code from Secobjprivilege obj where obj.objectname_code='...'}.
     * It then applies a <b>Java-side filter</b>, iterating over the results and
     * keeping only those whose {@code roleusergroup} is contained in the provided
     * list of role names.</p>
     *
     * <p>This two-step approach (HQL query + in-memory filtering) means the HQL
     * itself does not include a role filter clause; the role matching happens
     * entirely in Java via {@code roles.contains(p.getRoleusergroup())}.</p>
     */
    @Nested
    @DisplayName("getByObjectNameAndRoles() operations")
    class GetByObjectNameAndRolesOperations {

        /**
         * Verifies that {@code getByObjectNameAndRoles()} correctly filters
         * privileges by both object name (via HQL) and role membership
         * (via Java-side filtering).
         *
         * <p><b>DAO method:</b> {@code getByObjectNameAndRoles(String, List)}</p>
         * <p><b>HQL exercised:</b>
         * {@code from Secobjprivilege obj where obj.objectname_code='_rx'}</p>
         * <p><b>Java-side filter:</b> {@code roles.contains(p.getRoleusergroup())}
         * retains only entities whose role is in {@code ["doctorRole", "nurseRole"]}.</p>
         * <p><b>Setup:</b> Creates four privilege records across three roles and
         * two object names. Expects only the two records matching both
         * {@code objectname_code = "_rx"} AND role in the target list.</p>
         */
        @Test
        @Tag("read")
        @Tag("filter")
        @DisplayName("should filter by object name and roles when both match")
        void shouldFilterByObjectNameAndRoles_whenBothMatch() {
            // Given - create privileges across multiple roles and objects
            createAndSavePrivilege("doctorRole", "_rx", "r", 1, "999990");
            createAndSavePrivilege("nurseRole", "_rx", "r", 2, "999991");
            // adminRole has _rx but is not in the target roles list
            createAndSavePrivilege("adminRole", "_rx", "w", 1, "999992");
            // doctorRole has _lab but the query filters by _rx
            createAndSavePrivilege("doctorRole", "_lab", "r", 1, "999990");

            List<String> targetRoles = Arrays.asList("doctorRole", "nurseRole");

            // When - filter by objectName "_rx" and roles containing doctor and nurse
            List<Secobjprivilege> results = secobjprivilegeDao.getByObjectNameAndRoles(
                    "_rx", targetRoles);

            // Then - should return only the two matching doctor and nurse privileges for _rx
            assertThat(results).hasSize(2);
            assertThat(results)
                    .extracting(Secobjprivilege::getRoleusergroup)
                    .containsExactlyInAnyOrder("doctorRole", "nurseRole");
            // Verify all returned entities have the correct object name
            assertThat(results)
                    .allMatch(p -> "_rx".equals(p.getObjectname_code()));
        }
    }

    /**
     * Tests for the {@link SecobjprivilegeDao#save(Secobjprivilege)} additional scenarios.
     *
     * <p>Validates different data combinations and the saveOrUpdate behavior
     * for various privilege configurations.</p>
     */
    @Nested
    @DisplayName("save() additional scenarios")
    class SaveAdditionalScenarios {

        /**
         * Verifies that {@code save()} can persist multiple privilege entries
         * for the same role but different object names.
         */
        @Test
        @Tag("create")
        @DisplayName("should save multiple privileges for same role with different objects")
        void shouldSaveMultiplePrivileges_forSameRoleWithDifferentObjects() {
            // Given
            Secobjprivilege priv1 = new Secobjprivilege("multiRole", "_obj1", "r", 1, "999990");
            Secobjprivilege priv2 = new Secobjprivilege("multiRole", "_obj2", "w", 2, "999990");
            Secobjprivilege priv3 = new Secobjprivilege("multiRole", "_obj3", "x", 3, "999990");

            // When
            secobjprivilegeDao.save(priv1);
            secobjprivilegeDao.save(priv2);
            secobjprivilegeDao.save(priv3);
            hibernateTemplate.flush();

            // Then - verify all three were persisted via native query
            @SuppressWarnings("unchecked")
            List<Object[]> results = entityManager.createNativeQuery(NATIVE_CHECK_BY_ROLE)
                    .setParameter(1, "multiRole")
                    .getResultList();
            assertThat(results).hasSize(3);
        }

        /**
         * Verifies that {@code save()} persists the priority and providerNo fields
         * correctly, including null priority.
         */
        @Test
        @Tag("create")
        @DisplayName("should save privilege with null priority")
        void shouldSavePrivilege_withNullPriority() {
            // Given
            Secobjprivilege priv = new Secobjprivilege("nullPrioRole", "_objNP", "r", null, "999989");

            // When
            secobjprivilegeDao.save(priv);
            hibernateTemplate.flush();

            // Then - verify via native query
            @SuppressWarnings("unchecked")
            List<Object[]> results = entityManager.createNativeQuery(NATIVE_SELECT_PRIVILEGE)
                    .setParameter(1, "nullPrioRole")
                    .setParameter(2, "_objNP")
                    .getResultList();
            assertThat(results).hasSize(1);
            Object[] row = results.get(0);
            assertThat(row[0]).isEqualTo("nullPrioRole");
            assertThat(row[3]).isNull(); // priority should be null
        }
    }

    /**
     * Tests for the {@link SecobjprivilegeDao#delete(Secobjprivilege)} additional scenarios.
     */
    @Nested
    @DisplayName("delete() additional scenarios")
    class DeleteAdditionalScenarios {

        /**
         * Verifies that {@code delete()} only removes the target entity and leaves
         * other privileges for the same role intact.
         */
        @Test
        @Tag("delete")
        @DisplayName("should delete only target privilege and leave others for same role")
        void shouldDeleteOnlyTarget_whenMultiplePrivilegesExistForSameRole() {
            // Given
            Secobjprivilege priv1 = createAndSavePrivilege("delSelRole", "_delObj1", "r", 1, "999988");
            createAndSavePrivilege("delSelRole", "_delObj2", "w", 2, "999988");

            // When - delete only the first privilege
            secobjprivilegeDao.delete(priv1);
            hibernateTemplate.flush();

            // Then - first privilege should be gone
            @SuppressWarnings("unchecked")
            List<Object[]> deleted = entityManager
                    .createNativeQuery(NATIVE_CHECK_BY_ROLE_AND_OBJ)
                    .setParameter(1, "delSelRole")
                    .setParameter(2, "_delObj1")
                    .getResultList();
            assertThat(deleted).isEmpty();

            // Second privilege should still exist
            @SuppressWarnings("unchecked")
            List<Object[]> remaining = entityManager
                    .createNativeQuery(NATIVE_CHECK_BY_ROLE_AND_OBJ)
                    .setParameter(1, "delSelRole")
                    .setParameter(2, "_delObj2")
                    .getResultList();
            assertThat(remaining).hasSize(1);
        }
    }

    /**
     * Tests for the {@link SecobjprivilegeDao#deleteByRoleName(String)} additional scenarios.
     */
    @Nested
    @DisplayName("deleteByRoleName() additional scenarios")
    class DeleteByRoleNameAdditionalScenarios {

        /**
         * Verifies that {@code deleteByRoleName()} removes a single privilege
         * when only one exists for the role.
         */
        @Test
        @Tag("delete")
        @DisplayName("should delete single privilege when role has exactly one")
        void shouldDeleteSinglePrivilege_whenRoleHasExactlyOne() {
            // Given
            createAndSavePrivilege("singleDelRole", "_singleObj", "r", 1, "999987");

            // When
            int deleted = secobjprivilegeDao.deleteByRoleName("singleDelRole");

            // Then
            assertThat(deleted).isEqualTo(1);
        }
    }

    /**
     * Tests for the {@link SecobjprivilegeDao#getFunctionDesc(String)} additional scenarios.
     */
    @Nested
    @DisplayName("getFunctionDesc() additional scenarios")
    class GetFunctionDescAdditionalScenarios {

        /**
         * Verifies that {@code getFunctionDesc()} returns the correct description
         * when multiple Secobjectname records exist, selecting only the matching one.
         */
        @Test
        @Tag("read")
        @DisplayName("should return correct description when multiple secobjectnames exist")
        void shouldReturnCorrectDescription_whenMultipleSecobjectnamesExist() {
            // Given
            insertSecobjectname("_funcA", "Function A Description");
            insertSecobjectname("_funcB", "Function B Description");
            insertSecobjectname("_funcC", "Function C Description");

            // When
            String descB = secobjprivilegeDao.getFunctionDesc("_funcB");

            // Then
            assertThat(descB).isEqualTo("Function B Description");
        }
    }

    /**
     * Tests for the {@link SecobjprivilegeDao#getByObjectNameAndRoles(String, List)}
     * additional edge case scenarios.
     *
     * <p>These tests cover boundary conditions not addressed by the existing
     * filter test, including empty role lists, non-matching object names,
     * and single-role queries.</p>
     */
    @Nested
    @DisplayName("getByObjectNameAndRoles() additional scenarios")
    class GetByObjectNameAndRolesAdditionalScenarios {

        /**
         * Verifies that {@code getByObjectNameAndRoles()} returns an empty list
         * when the role list is empty (no roles to match against).
         */
        @Test
        @Tag("read")
        @Tag("filter")
        @DisplayName("should return empty list when roles list is empty")
        void shouldReturnEmptyList_whenRolesListIsEmpty() {
            // Given
            createAndSavePrivilege("anyRole", "_anyObj", "r", 1, "999986");

            // When
            List<Secobjprivilege> results = secobjprivilegeDao.getByObjectNameAndRoles(
                    "_anyObj", Collections.emptyList());

            // Then
            assertThat(results).isEmpty();
        }

        /**
         * Verifies that {@code getByObjectNameAndRoles()} returns an empty list
         * when the object name does not match any records.
         */
        @Test
        @Tag("read")
        @Tag("filter")
        @DisplayName("should return empty list when object name does not exist")
        void shouldReturnEmptyList_whenObjectNameDoesNotExist() {
            // Given
            createAndSavePrivilege("someRole", "_existingObj", "r", 1, "999985");

            // When
            List<Secobjprivilege> results = secobjprivilegeDao.getByObjectNameAndRoles(
                    "_nonExistentObj", Arrays.asList("someRole"));

            // Then
            assertThat(results).isEmpty();
        }

        /**
         * Verifies that {@code getByObjectNameAndRoles()} correctly handles the case
         * where the object name exists but none of the roles match.
         */
        @Test
        @Tag("read")
        @Tag("filter")
        @DisplayName("should return empty list when object exists but no roles match")
        void shouldReturnEmptyList_whenObjectExistsButNoRolesMatch() {
            // Given
            createAndSavePrivilege("roleX", "_objForNoMatch", "r", 1, "999984");

            // When
            List<Secobjprivilege> results = secobjprivilegeDao.getByObjectNameAndRoles(
                    "_objForNoMatch", Arrays.asList("roleY", "roleZ"));

            // Then
            assertThat(results).isEmpty();
        }

        /**
         * Verifies that {@code getByObjectNameAndRoles()} returns a single result
         * when exactly one role matches.
         */
        @Test
        @Tag("read")
        @Tag("filter")
        @DisplayName("should return single result when exactly one role matches")
        void shouldReturnSingleResult_whenExactlyOneRoleMatches() {
            // Given
            createAndSavePrivilege("matchRole", "_singleMatch", "r", 1, "999983");
            createAndSavePrivilege("otherRole", "_singleMatch", "w", 2, "999982");

            // When
            List<Secobjprivilege> results = secobjprivilegeDao.getByObjectNameAndRoles(
                    "_singleMatch", Arrays.asList("matchRole"));

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRoleusergroup()).isEqualTo("matchRole");
        }

        /**
         * Verifies that the privilege_code field is correctly preserved in the
         * returned entities after filtering.
         */
        @Test
        @Tag("read")
        @Tag("filter")
        @DisplayName("should preserve privilege code in returned entities")
        void shouldPreservePrivilegeCode_inReturnedEntities() {
            // Given
            createAndSavePrivilege("privRole", "_privObj", "rw", 5, "999981");

            // When
            List<Secobjprivilege> results = secobjprivilegeDao.getByObjectNameAndRoles(
                    "_privObj", Arrays.asList("privRole"));

            // Then
            assertThat(results).hasSize(1);
            Secobjprivilege result = results.get(0);
            assertThat(result.getPrivilege_code()).isEqualTo("rw");
            assertThat(result.getPriority()).isEqualTo(5);
            assertThat(result.getProviderNo()).isEqualTo("999981");
        }
    }

    /**
     * Tests for the {@link SecobjprivilegeDao#saveAll(List)} method.
     *
     * <p>The {@code saveAll()} implementation iterates through the list, calling
     * {@code update()} first on each entity. If the update returns 0 (no existing
     * row matched), it falls back to calling {@code save()} to insert a new row.
     * This implements an upsert pattern.</p>
     *
     * <p><b>Note:</b> The {@code update()} method internally uses
     * {@code sessionFactory.getCurrentSession()} with {@code session.close()} in
     * a finally block. Under Spring's transactional test management, this causes
     * a session lifecycle conflict. Therefore, the {@code saveAll()} method also
     * cannot be safely tested in this test context. These tests are included as
     * documentation of the expected behavior but may need to be enabled after
     * the DAO is refactored to remove explicit {@code session.close()} calls.</p>
     */
    /*
     * saveAll() tests cannot be enabled because saveAll() internally calls
     * update(), which closes the session prematurely. See the raw session
     * management note below for details.
     */

    /**
     * Tests for the {@link SecobjprivilegeDao#update(Secobjprivilege)} method.
     *
     * <p>The {@code update()} implementation uses {@code HqlQueryHelper.bulkUpdate()}
     * with the HQL: {@code update Secobjprivilege set providerNo = ?1 where
     * objectname_code = ?2 and privilege_code = ?3 and roleusergroup = ?4}.
     * It returns the count of rows updated, or 0 if {@code providerNo} is null
     * (early-return guard that routes the caller to {@code save()} instead).</p>
     *
     * <p>Note: {@code findByProperty()}, {@code getFunctions()}, {@code getByRoles()},
     * and {@code getAccessDesc()} are not tested here. {@code getAccessDesc()} requires
     * the {@code Secprivilege} entity which is not mapped in the test persistence unit.</p>
     */
    @Nested
    @DisplayName("update() operations")
    class UpdateOperations {

        @Test
        @Tag("update")
        @DisplayName("should return zero when providerNo is null (routes to save)")
        void shouldReturnZero_whenProviderNoIsNull() {
            Secobjprivilege instance = new Secobjprivilege("nullRole", "_obj", "r", 1, null);

            int result = secobjprivilegeDao.update(instance);

            assertThat(result).isEqualTo(0);
        }

        @Test
        @Tag("update")
        @DisplayName("should update providerNo and return row count when record exists")
        void shouldUpdateProviderNo_whenRecordExists() {
            // Given - persist a record with an initial providerNo
            createAndSavePrivilege("updateRole", "_updateObj", "w", 1, "111111");

            // When - update providerNo via the composite key
            Secobjprivilege toUpdate = new Secobjprivilege("updateRole", "_updateObj", "w", 1, "222222");
            int rowsUpdated = secobjprivilegeDao.update(toUpdate);

            // Then - exactly one row updated
            assertThat(rowsUpdated).isEqualTo(1);
        }

        @Test
        @Tag("update")
        @DisplayName("should return zero when no matching record exists")
        void shouldReturnZero_whenNoMatchingRecord() {
            Secobjprivilege instance = new Secobjprivilege("nonExistentRole", "_obj", "r", 1, "999999");

            int result = secobjprivilegeDao.update(instance);

            assertThat(result).isEqualTo(0);
        }
    }
}
