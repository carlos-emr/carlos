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
import io.github.carlos_emr.carlos.model.security.Secrole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link SecroleDao} verifying Hibernate mapping
 * and HQL query correctness for the security role management module.
 *
 * <p>The {@code Secrole} entity is mapped to the {@code secRole} table via HBM XML
 * ({@code Secrole.hbm.xml}). Only three properties are actively mapped:
 * {@code id} (role_no), {@code roleName} (role_name), and {@code description}.
 * The fields {@code active}, {@code lastUpdateUser}, {@code lastUpdateDate}, and
 * {@code orderByIndex} exist in the Java class but are <b>commented out</b> in the
 * HBM mapping and are therefore not persisted.</p>
 *
 * <p><b>Note:</b> The {@code getDefaultRoles()} method is intentionally NOT tested
 * because it queries the {@code userDefined} property, which is not present in the
 * HBM mapping. Calling it would result in a Hibernate mapping exception.</p>
 *
 * <p>This test class extends {@link CarlosTestBase}, which provides the full Spring
 * application context, an H2 in-memory database, and SpringUtils initialization
 * required by the CARLOS EMR DAO layer. All tests run within a {@link Transactional}
 * boundary that is rolled back after each test method, ensuring test isolation.</p>
 *
 * <p><b>Test organization:</b> Tests are grouped into {@link Nested} inner classes
 * by DAO method, following the component-first naming convention used throughout
 * the CARLOS EMR modern test framework.</p>
 *
 * @since 2026-02-09
 * @see SecroleDao
 * @see SecroleDaoImpl
 * @see Secrole
 * @see CarlosTestBase
 */
@DisplayName("SecroleDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("security")
@Transactional
public class SecroleDaoIntegrationTest extends CarlosTestBase {

    /**
     * The {@link SecroleDao} instance under test, injected by the Spring context.
     *
     * <p>This is wired to the {@link SecroleDaoImpl} bean, which extends
     * {@code HibernateDaoSupport} and provides CRUD operations and query
     * methods for the {@link Secrole} entity.</p>
     *
     * @see SecroleDaoImpl
     */
    @Autowired
    private SecroleDao secroleDao;

    /**
     * JPA {@link EntityManager} used for direct database verification in tests.
     *
     * <p>This EntityManager shares the same transactional context as the DAO under
     * test, allowing tests to flush pending changes and verify persisted state
     * independently of the DAO's own query methods. The unit name
     * {@code "entityManagerFactory"} matches the persistence unit configured in
     * the test Spring context ({@code test-context-full.xml}).</p>
     *
     * @see javax.persistence.PersistenceContext
     */
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /**
     * Creates and persists a {@link Secrole} with the given role name and description.
     *
     * <p>This helper method constructs a new {@link Secrole} using the two-argument
     * constructor, persists it via {@link SecroleDao#save(Secrole)}, and then flushes
     * the EntityManager to synchronize the persistence context with the underlying
     * H2 database. This ensures the entity is fully persisted and its generated
     * {@code id} (role_no) is available before subsequent read operations.</p>
     *
     * @param roleName    String the role name to assign (maps to {@code role_name} column)
     * @param description String the role description to assign (maps to {@code description} column)
     * @return Secrole the persisted entity with a generated ID
     * @see SecroleDao#save(Secrole)
     */
    private Secrole createAndSaveSecrole(String roleName, String description) {
        Secrole role = new Secrole(roleName, description);
        secroleDao.save(role);
        // Flush to synchronize the persistence context with the database,
        // ensuring the auto-generated ID is assigned before assertions.
        entityManager.flush();
        return role;
    }

    /**
     * Tests for {@link SecroleDao#save(Secrole)}.
     *
     * <p>Verifies that the DAO correctly persists new {@link Secrole} entities to the
     * {@code secRole} table and enforces null-safety by rejecting null arguments.
     * The {@code save()} method delegates to Hibernate's {@code saveOrUpdate()},
     * meaning it can handle both inserts and updates depending on whether the
     * entity has a pre-existing ID.</p>
     *
     * @see SecroleDao#save(Secrole)
     * @see SecroleDaoImpl#save(Secrole)
     */
    @Nested
    @DisplayName("save() operations")
    class SaveOperations {

        /**
         * Verifies that a {@link Secrole} entity with valid role name and description
         * is correctly persisted to the database.
         *
         * <p>This test creates a new {@link Secrole}, saves it via the DAO, then
         * independently queries the database through the {@link EntityManager} to
         * confirm the entity was actually written. Using a separate HQL query
         * (rather than the DAO's own retrieval methods) avoids circular validation
         * and provides an independent confirmation of persistence.</p>
         *
         * <p>Assertions verify that:
         * <ul>
         *   <li>Exactly one matching record exists in the database</li>
         *   <li>The persisted {@code roleName} matches the input value</li>
         *   <li>The persisted {@code description} matches the input value</li>
         *   <li>A non-null auto-generated {@code id} (role_no) was assigned</li>
         * </ul>
         * </p>
         *
         * @see SecroleDao#save(Secrole)
         */
        @Test
        @Tag("create")
        @DisplayName("should save secrole when valid data provided")
        void shouldSaveSecrole_whenValidDataProvided() {
            // Given
            Secrole role = new Secrole("TestAdmin", "Test Administrator Role");

            // When
            secroleDao.save(role);
            entityManager.flush();

            // Then - verify via entityManager query to confirm persistence
            // independently of the DAO's own read methods
            @SuppressWarnings("unchecked")
            List<Secrole> results = entityManager
                    .createQuery("from Secrole r where r.roleName = :name")
                    .setParameter("name", "TestAdmin")
                    .getResultList();

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRoleName()).isEqualTo("TestAdmin");
            assertThat(results.get(0).getDescription()).isEqualTo("Test Administrator Role");
            // The id (role_no) is auto-generated and must be assigned after save
            assertThat(results.get(0).getId()).isNotNull();
        }

        /**
         * Verifies that {@link SecroleDao#save(Secrole)} throws an
         * {@link IllegalArgumentException} when called with a {@code null} argument.
         *
         * <p>The {@link SecroleDaoImpl#save(Secrole)} implementation explicitly checks
         * for null and throws {@link IllegalArgumentException} before delegating to
         * Hibernate's {@code saveOrUpdate()}, providing a clear and consistent
         * error for invalid input.</p>
         *
         * @see SecroleDaoImpl#save(Secrole)
         */
        @Test
        @Tag("create")
        @DisplayName("should throw exception when save null")
        void shouldThrowException_whenSaveNull() {
            // When / Then
            assertThatThrownBy(() -> secroleDao.save(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests for {@link SecroleDao#getRoles()}.
     *
     * <p>Verifies that the DAO correctly retrieves all {@link Secrole} entities from
     * the {@code secRole} table, ordered alphabetically by {@code roleName}. The
     * underlying HQL query is {@code "from Secrole r order by roleName"}, which
     * returns results sorted in ascending lexicographic order.</p>
     *
     * @see SecroleDao#getRoles()
     * @see SecroleDaoImpl#getRoles()
     */
    @Nested
    @DisplayName("getRoles() operations")
    class GetRolesOperations {

        /**
         * Verifies that {@link SecroleDao#getRoles()} returns all persisted roles
         * in alphabetical order by {@code roleName}.
         *
         * <p>This test inserts three roles with names chosen to have a known
         * alphabetical ordering ("AlphaRole", "BetaRole", "GammaRole"), then
         * retrieves all roles and validates both the presence and ordering of
         * the results.</p>
         *
         * <p>Assertions verify that:
         * <ul>
         *   <li>The result list is not empty</li>
         *   <li>At least three roles are returned (test roles plus any pre-existing data)</li>
         *   <li>All role names in the result list are in sorted (ascending) order</li>
         *   <li>The three test roles are present in the result set</li>
         * </ul>
         * </p>
         *
         * <p>The use of {@code isGreaterThanOrEqualTo(3)} rather than {@code hasSize(3)}
         * accounts for the possibility that pre-existing seed data may already contain
         * roles in the H2 test database.</p>
         *
         * @see SecroleDao#getRoles()
         */
        @Test
        @Tag("read")
        @DisplayName("should return all roles when multiple roles exist")
        void shouldReturnAllRoles_whenMultipleRolesExist() {
            // Given - insert roles with names that sort alphabetically
            createAndSaveSecrole("AlphaRole", "First role");
            createAndSaveSecrole("BetaRole", "Second role");
            createAndSaveSecrole("GammaRole", "Third role");

            // When
            List<Secrole> roles = secroleDao.getRoles();

            // Then - should contain at least our 3 test roles, ordered by roleName
            assertThat(roles).isNotEmpty();
            // Use greaterThanOrEqualTo to account for any pre-existing seed data
            assertThat(roles.size()).isGreaterThanOrEqualTo(3);

            // Verify ordering: extract role names and confirm sorted order
            List<String> roleNames = roles.stream()
                    .map(Secrole::getRoleName)
                    .toList();
            assertThat(roleNames).isSorted();

            // Verify our test roles are present
            assertThat(roleNames).contains("AlphaRole", "BetaRole", "GammaRole");
        }
    }

    /**
     * Tests for {@link SecroleDao#getRole(Integer)}.
     *
     * <p>Verifies that the DAO correctly retrieves a single {@link Secrole} entity
     * by its primary key. The {@link SecroleDaoImpl#getRole(Integer)} implementation
     * converts the {@link Integer} parameter to {@link Long} before delegating to
     * Hibernate's {@code get()} method, since the {@code id} field on {@link Secrole}
     * is typed as {@code Long}. It also performs null and non-positive value validation,
     * throwing {@link IllegalArgumentException} for invalid input.</p>
     *
     * @see SecroleDao#getRole(Integer)
     * @see SecroleDaoImpl#getRole(Integer)
     */
    @Nested
    @DisplayName("getRole() operations")
    class GetRoleOperations {

        /**
         * Verifies that {@link SecroleDao#getRole(Integer)} returns the correct
         * {@link Secrole} entity when called with a valid, existing ID.
         *
         * <p>This test first persists a role via the helper method, then retrieves it
         * by ID. The {@code intValue()} conversion on the saved ID mirrors the
         * Integer-to-Long conversion that happens inside the DAO implementation,
         * ensuring the type coercion pathway is exercised.</p>
         *
         * <p>Assertions verify that:
         * <ul>
         *   <li>The returned entity is not null</li>
         *   <li>The returned entity's ID matches the saved entity's ID</li>
         *   <li>The {@code roleName} and {@code description} fields are correctly populated</li>
         * </ul>
         * </p>
         *
         * @see SecroleDao#getRole(Integer)
         */
        @Test
        @Tag("read")
        @DisplayName("should return role when valid ID provided")
        void shouldReturnRole_whenValidIdProvided() {
            // Given
            Secrole saved = createAndSaveSecrole("FindByIdRole", "Role for ID lookup");

            // When - getRole accepts Integer, internally converts to Long
            Secrole found = secroleDao.getRole(saved.getId().intValue());

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getRoleName()).isEqualTo("FindByIdRole");
            assertThat(found.getDescription()).isEqualTo("Role for ID lookup");
        }

        /**
         * Verifies that {@link SecroleDao#getRole(Integer)} throws an
         * {@link IllegalArgumentException} when called with a {@code null} ID.
         *
         * <p>The {@link SecroleDaoImpl#getRole(Integer)} implementation checks
         * {@code if (id == null || id.intValue() <= 0)} and throws
         * {@link IllegalArgumentException} before attempting any database access.
         * This guard prevents {@link NullPointerException} from propagating to
         * Hibernate's {@code get()} method.</p>
         *
         * @see SecroleDaoImpl#getRole(Integer)
         */
        @Test
        @Tag("read")
        @DisplayName("should throw exception when getRole with null ID")
        void shouldThrowException_whenGetRoleWithNullId() {
            // When / Then - SecroleDaoImpl checks for null and throws IllegalArgumentException
            assertThatThrownBy(() -> secroleDao.getRole(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests for {@link SecroleDao#getRoleByName(String)}.
     *
     * <p>Verifies that the DAO correctly retrieves a {@link Secrole} entity by its
     * {@code roleName} property. The underlying implementation in
     * {@link SecroleDaoImpl#getRoleByName(String)} uses an HQL query filtered by
     * role name. If multiple roles share the same name, only the first result is
     * returned.</p>
     *
     * <p><b>Known issue:</b> The current implementation in {@link SecroleDaoImpl}
     * uses string concatenation for the HQL query parameter rather than
     * parameterized binding, which is a SQL injection vulnerability. This test
     * verifies functional correctness only and does not exercise the injection
     * vector.</p>
     *
     * @see SecroleDao#getRoleByName(String)
     * @see SecroleDaoImpl#getRoleByName(String)
     */
    @Nested
    @DisplayName("getRoleByName() operations")
    class GetRoleByNameOperations {

        /**
         * Verifies that {@link SecroleDao#getRoleByName(String)} returns the correct
         * {@link Secrole} entity when called with an existing role name.
         *
         * <p>This test persists a role with a unique name, then retrieves it by
         * that exact name. The name "UniqueNameRole" is chosen to avoid collisions
         * with any pre-existing seed data in the H2 test database.</p>
         *
         * <p>Assertions verify that:
         * <ul>
         *   <li>The returned entity is not null</li>
         *   <li>The returned entity's ID matches the originally saved entity's ID</li>
         *   <li>The {@code roleName} matches the search parameter</li>
         *   <li>The {@code description} is correctly populated from the persisted data</li>
         * </ul>
         * </p>
         *
         * @see SecroleDao#getRoleByName(String)
         */
        @Test
        @Tag("read")
        @DisplayName("should return role by name when name exists")
        void shouldReturnRoleByName_whenNameExists() {
            // Given
            Secrole saved = createAndSaveSecrole("UniqueNameRole", "Role found by name");

            // When
            Secrole found = secroleDao.getRoleByName("UniqueNameRole");

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getRoleName()).isEqualTo("UniqueNameRole");
            assertThat(found.getDescription()).isEqualTo("Role found by name");
        }
    }

    /*
     * NOTE: getDefaultRoles() is intentionally NOT tested.
     *
     * The method executes the HQL query "from Secrole r where r.userDefined=0",
     * but the 'userDefined' property is commented out in the Hibernate mapping
     * file (Secrole.hbm.xml). Executing this query would result in a
     * QuerySyntaxException because Hibernate does not know about the
     * 'userDefined' property. This is a pre-existing issue in the DAO
     * implementation that predates the Hibernate migration.
     */
}
