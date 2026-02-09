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

import io.github.carlos_emr.carlos.test.base.OpenOTestBase;
import io.github.carlos_emr.carlos.model.security.Secobjprivilege;
import io.github.carlos_emr.carlos.model.security.Secobjectname;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Arrays;
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
public class SecobjprivilegeDaoIntegrationTest extends OpenOTestBase {

    @Autowired
    private SecobjprivilegeDao secobjprivilegeDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private static final String NATIVE_SELECT_PRIVILEGE =
            "SELECT roleUserGroup, objectName, privilege, priority, provider_no FROM secObjPrivilege WHERE roleUserGroup = ?1 AND objectName = ?2";

    private static final String NATIVE_CHECK_BY_ROLE_AND_OBJ =
            "SELECT roleUserGroup FROM secObjPrivilege WHERE roleUserGroup = ?1 AND objectName = ?2";

    private static final String NATIVE_CHECK_BY_ROLE =
            "SELECT roleUserGroup FROM secObjPrivilege WHERE roleUserGroup = ?1";

    private static final String NATIVE_INSERT_SECOBJECTNAME =
            "INSERT INTO secObjectName (objectName, description, orgapplicable) VALUES (?1, ?2, 1)";

    /**
     * Creates and persists a {@link Secobjprivilege} with the given composite key
     * and privilege details.
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
        entityManager.flush();
        return priv;
    }

    /**
     * Inserts a {@link Secobjectname} record using native SQL.
     * This is needed for testing {@code getFunctionDesc()} which queries the
     * {@code secObjectName} table via the {@code Secobjectname} entity.
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

    @Nested
    @DisplayName("save() operations")
    class SaveOperations {

        @Test
        @Tag("create")
        @DisplayName("should save secobjprivilege when valid data provided")
        void shouldSaveSecobjprivilege_whenValidDataProvided() {
            // Given
            Secobjprivilege priv = new Secobjprivilege("admin", "_demographic",
                    "r", 1, "999998");

            // When
            secobjprivilegeDao.save(priv);
            entityManager.flush();

            // Then - verify via native query to confirm persistence
            @SuppressWarnings("unchecked")
            List<Object[]> results = entityManager.createNativeQuery(NATIVE_SELECT_PRIVILEGE)
                    .setParameter(1, "admin")
                    .setParameter(2, "_demographic")
                    .getResultList();

            assertThat(results).hasSize(1);
            Object[] row = results.get(0);
            assertThat(row[0]).isEqualTo("admin");
            assertThat(row[1]).isEqualTo("_demographic");
            assertThat(row[2]).isEqualTo("r");
            assertThat(((Number) row[3]).intValue()).isEqualTo(1);
            assertThat(row[4]).isEqualTo("999998");
        }

        @Test
        @Tag("create")
        @DisplayName("should throw exception when save null")
        void shouldThrowException_whenSaveNull() {
            // When / Then
            assertThatThrownBy(() -> secobjprivilegeDao.save(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("delete() operations")
    class DeleteOperations {

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
            entityManager.flush();

            // Then
            @SuppressWarnings("unchecked")
            List<Object[]> afterDelete = entityManager
                    .createNativeQuery(NATIVE_CHECK_BY_ROLE_AND_OBJ)
                    .setParameter(1, "delRole")
                    .setParameter(2, "_delObject")
                    .getResultList();
            assertThat(afterDelete).isEmpty();
        }
    }

    @Nested
    @DisplayName("deleteByRoleName() operations")
    class DeleteByRoleNameOperations {

        @Test
        @Tag("delete")
        @DisplayName("should delete by role name when role exists")
        void shouldDeleteByRoleName_whenRoleExists() {
            // Given - create multiple privileges for the same role
            createAndSavePrivilege("bulkDelRole", "_object1", "r", 1, "999996");
            createAndSavePrivilege("bulkDelRole", "_object2", "w", 2, "999996");
            createAndSavePrivilege("otherRole", "_object3", "x", 1, "999995");

            // When
            int deleted = secobjprivilegeDao.deleteByRoleName("bulkDelRole");

            // Then
            assertThat(deleted).isEqualTo(2);

            // Verify the other role's privilege was not deleted
            entityManager.flush();
            entityManager.clear();
            @SuppressWarnings("unchecked")
            List<Object[]> remaining = entityManager
                    .createNativeQuery(NATIVE_CHECK_BY_ROLE)
                    .setParameter(1, "otherRole")
                    .getResultList();
            assertThat(remaining).hasSize(1);
        }

        @Test
        @Tag("delete")
        @DisplayName("should return zero when delete by non-existent role name")
        void shouldReturnZero_whenDeleteByNonExistentRoleName() {
            // When
            int deleted = secobjprivilegeDao.deleteByRoleName("nonExistentRole_XYZ");

            // Then
            assertThat(deleted).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getFunctionDesc() operations")
    class GetFunctionDescOperations {

        @Test
        @Tag("read")
        @DisplayName("should return function description when function code exists")
        void shouldReturnFunctionDescription_whenFunctionCodeExists() {
            // Given - insert a Secobjectname record via native SQL
            insertSecobjectname("_testFunc1", "Test Function One Description");

            // When
            String description = secobjprivilegeDao.getFunctionDesc("_testFunc1");

            // Then
            assertThat(description).isEqualTo("Test Function One Description");
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty string when function code does not exist")
        void shouldReturnEmptyString_whenFunctionCodeDoesNotExist() {
            // When
            String description = secobjprivilegeDao.getFunctionDesc("_nonExistentFunc_XYZ");

            // Then
            assertThat(description).isEmpty();
        }
    }

    @Nested
    @DisplayName("getByObjectNameAndRoles() operations")
    class GetByObjectNameAndRolesOperations {

        @Test
        @Tag("read")
        @Tag("filter")
        @DisplayName("should filter by object name and roles when both match")
        void shouldFilterByObjectNameAndRoles_whenBothMatch() {
            // Given - create privileges across multiple roles and objects
            createAndSavePrivilege("doctorRole", "_rx", "r", 1, "999990");
            createAndSavePrivilege("nurseRole", "_rx", "r", 2, "999991");
            createAndSavePrivilege("adminRole", "_rx", "w", 1, "999992");
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
            assertThat(results)
                    .allMatch(p -> "_rx".equals(p.getObjectname_code()));
        }
    }

    /*
     * Methods intentionally NOT tested due to raw session management:
     *
     * - update(Secobjprivilege): Uses sessionFactory.getCurrentSession() and calls
     *   session.close() in a finally block. This conflicts with Spring @Transactional
     *   test management, which expects to control the session lifecycle. Calling
     *   session.close() within a Spring-managed transaction results in
     *   SessionException or TransactionException.
     *
     * - findByProperty(String, Object): Same raw session pattern with session.close()
     *   in the finally block. Would cause the same session lifecycle conflicts.
     *
     * - getFunctions(String): Delegates directly to findByProperty(), inheriting
     *   the same raw session issue.
     *
     * - getByRoles(List<String>): Uses sessionFactory.getCurrentSession() without
     *   closing, but does not use HibernateTemplate. May behave unpredictably
     *   when the session is managed by Spring's transaction infrastructure.
     *
     * - getAccessDesc(String): Queries the Secprivilege entity which may not be
     *   available in the test persistence context.
     *
     * These methods would need to be refactored to use HibernateTemplate (or
     * the session without explicit close()) before they can be reliably
     * integration-tested within Spring's transactional test framework.
     */
}
