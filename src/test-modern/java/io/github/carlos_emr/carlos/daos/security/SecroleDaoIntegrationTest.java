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
 * @since 2026-02-09
 * @see SecroleDao
 * @see SecroleDaoImpl
 * @see Secrole
 */
@DisplayName("SecroleDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("security")
@Transactional
public class SecroleDaoIntegrationTest extends OpenOTestBase {

    @Autowired
    private SecroleDao secroleDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /**
     * Creates and persists a {@link Secrole} with the given role name and description.
     *
     * @param roleName    String the role name
     * @param description String the role description
     * @return Secrole the persisted entity with a generated ID
     */
    private Secrole createAndSaveSecrole(String roleName, String description) {
        Secrole role = new Secrole(roleName, description);
        secroleDao.save(role);
        entityManager.flush();
        return role;
    }

    @Nested
    @DisplayName("save() operations")
    class SaveOperations {

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
            @SuppressWarnings("unchecked")
            List<Secrole> results = entityManager
                    .createQuery("from Secrole r where r.roleName = :name")
                    .setParameter("name", "TestAdmin")
                    .getResultList();

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRoleName()).isEqualTo("TestAdmin");
            assertThat(results.get(0).getDescription()).isEqualTo("Test Administrator Role");
            assertThat(results.get(0).getId()).isNotNull();
        }

        @Test
        @Tag("create")
        @DisplayName("should throw exception when save null")
        void shouldThrowException_whenSaveNull() {
            // When / Then
            assertThatThrownBy(() -> secroleDao.save(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("getRoles() operations")
    class GetRolesOperations {

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

    @Nested
    @DisplayName("getRole() operations")
    class GetRoleOperations {

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

        @Test
        @Tag("read")
        @DisplayName("should throw exception when getRole with null ID")
        void shouldThrowException_whenGetRoleWithNullId() {
            // When / Then - SecroleDaoImpl checks for null and throws IllegalArgumentException
            assertThatThrownBy(() -> secroleDao.getRole(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("getRoleByName() operations")
    class GetRoleByNameOperations {

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
