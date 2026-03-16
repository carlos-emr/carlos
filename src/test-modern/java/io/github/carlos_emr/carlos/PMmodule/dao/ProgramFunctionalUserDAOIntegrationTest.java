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
package io.github.carlos_emr.carlos.PMmodule.dao;

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramFunctionalUser;
import io.github.carlos_emr.carlos.PMmodule.model.FunctionalUserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import io.github.carlos_emr.carlos.test.base.HibernateTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ProgramFunctionalUserDAO} multi-parameter query methods.
 *
 * <p>These tests validate HQL queries with positional parameters (?1, ?2, ...)
 * bind correctly, ensuring safe migration to Hibernate 6 named parameter syntax.
 * Tests cover CRUD operations for both ProgramFunctionalUser and FunctionalUserType
 * entities, multi-parameter lookups, and input validation.</p>
 *
 * @since 2026-02-26
 * @see ProgramFunctionalUserDAO
 */
@DisplayName("ProgramFunctionalUserDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class ProgramFunctionalUserDAOIntegrationTest extends CarlosTestBase {

    @Autowired
    private ProgramFunctionalUserDAO programFunctionalUserDAO;

    @Autowired
    private HibernateTemplate hibernateTemplate;

    private Long testProgramId1;
    private Long testProgramId2;
    private Long testUserTypeId1;
    private Long testUserTypeId2;
    private ProgramFunctionalUser testFunctionalUser1;

    @BeforeEach
    void setUp() {
        // Generate unique IDs from nanosecond timestamp to avoid conflicts across test runs
        long baseId = System.nanoTime() % 100000;
        testProgramId1 = 1000L + baseId;
        testProgramId2 = 2000L + baseId;

        // Create test functional user types
        FunctionalUserType userType1 = createFunctionalUserType("Admin");
        FunctionalUserType userType2 = createFunctionalUserType("Viewer");
        testUserTypeId1 = userType1.getId();
        testUserTypeId2 = userType2.getId();

        // Create test functional users
        testFunctionalUser1 = createFunctionalUser(testProgramId1, testUserTypeId1);
        createFunctionalUser(testProgramId1, testUserTypeId2);
        createFunctionalUser(testProgramId2, testUserTypeId1);

        hibernateTemplate.flush();
    }

    /**
     * Creates a new FunctionalUserType with the given name and persists it.
     *
     * @param name String the display name for the functional user type
     * @return FunctionalUserType the persisted entity with generated ID
     */
    private FunctionalUserType createFunctionalUserType(String name) {
        FunctionalUserType userType = new FunctionalUserType();
        userType.setName(name);
        hibernateTemplate.save(userType);
        return userType;
    }

    /**
     * Creates a new ProgramFunctionalUser linking a program to a user type and persists it.
     *
     * @param programId Long the program ID to associate
     * @param userTypeId Long the functional user type ID to associate
     * @return ProgramFunctionalUser the persisted entity with generated ID
     */
    private ProgramFunctionalUser createFunctionalUser(Long programId, Long userTypeId) {
        ProgramFunctionalUser pfu = new ProgramFunctionalUser();
        pfu.setProgramId(programId);
        pfu.setUserTypeId(userTypeId);
        hibernateTemplate.save(pfu);
        return pfu;
    }

    /**
     * Tests for {@code getFunctionalUserByUserType(Long programId, Long userTypeId)} - returns
     * the program ID when a functional user matching both program and user type exists.
     */
    @Nested
    @DisplayName("getFunctionalUserByUserType (2 params)")
    class GetFunctionalUserByUserType {

        @Test
        @Tag("query")
        @DisplayName("should find user when both program and user type match")
        void shouldFind_whenBothParamsMatch() {
            Long result = programFunctionalUserDAO.getFunctionalUserByUserType(testProgramId1, testUserTypeId1);
            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(testFunctionalUser1.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when program doesn't match")
        void shouldReturnNull_whenProgramDoesntMatch() {
            Long result = programFunctionalUserDAO.getFunctionalUserByUserType(99999L, testUserTypeId1);
            assertThat(result).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when user type doesn't match")
        void shouldReturnNull_whenUserTypeDoesntMatch() {
            Long result = programFunctionalUserDAO.getFunctionalUserByUserType(testProgramId1, 99999L);
            assertThat(result).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should throw exception for invalid program ID")
        void shouldThrow_whenProgramIdInvalid() {
            assertThatThrownBy(() -> programFunctionalUserDAO.getFunctionalUserByUserType(null, testUserTypeId1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("query")
        @DisplayName("should throw exception for invalid user type ID")
        void shouldThrow_whenUserTypeIdInvalid() {
            assertThatThrownBy(() -> programFunctionalUserDAO.getFunctionalUserByUserType(testProgramId1, null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests for single-parameter query methods as baseline coverage, including
     * {@code getFunctionalUsers(Long)}, {@code getFunctionalUserType(Long)},
     * and {@code getFunctionalUserTypes()}.
     */
    @Nested
    @DisplayName("Single parameter queries (baseline)")
    class SingleParamQueries {

        @Test
        @Tag("read")
        @DisplayName("should get functional user types linked to program")
        void shouldGetFunctionalUsers_byProgram() {
            // Note: getFunctionalUsers(Long programId) returns List<FunctionalUserType> —
            // the user types associated with the program, not ProgramFunctionalUser records.
            List<FunctionalUserType> results = programFunctionalUserDAO.getFunctionalUsers(testProgramId1);
            assertThat(results).hasSize(2);
        }

        @Test
        @Tag("read")
        @DisplayName("should get functional user type by ID")
        void shouldGetUserType_byId() {
            FunctionalUserType result = programFunctionalUserDAO.getFunctionalUserType(testUserTypeId1);
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Admin");
        }

        @Test
        @Tag("read")
        @DisplayName("should get all functional user types")
        void shouldGetAllUserTypes_whenQueried() {
            List<FunctionalUserType> results = programFunctionalUserDAO.getFunctionalUserTypes();
            assertThat(results)
                .isNotEmpty()
                .anyMatch(ut -> ut.getName().equals("Admin"));
        }
    }

    /**
     * Tests for {@code getFunctionalUser(Long id)} - single entity lookup by primary key
     * for ProgramFunctionalUser (not FunctionalUserType).
     */
    @Nested
    @DisplayName("getFunctionalUser (by ID)")
    class GetFunctionalUser {

        @Test
        @Tag("read")
        @DisplayName("should return functional user when valid ID is provided")
        void shouldReturnFunctionalUser_whenValidIdProvided() {
            // Given
            ProgramFunctionalUser pfu = createFunctionalUser(testProgramId1, testUserTypeId1);
            hibernateTemplate.flush();
            Long savedId = pfu.getId();

            // When
            ProgramFunctionalUser result = programFunctionalUserDAO.getFunctionalUser(savedId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(savedId);
            assertThat(result.getProgramId()).isEqualTo(testProgramId1);
            assertThat(result.getUserTypeId()).isEqualTo(testUserTypeId1.longValue());
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when functional user ID does not exist")
        void shouldReturnNull_whenIdNotFound() {
            // When
            ProgramFunctionalUser result = programFunctionalUserDAO.getFunctionalUser(999999L);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should throw exception for null ID")
        void shouldThrow_whenIdIsNull() {
            assertThatThrownBy(() -> programFunctionalUserDAO.getFunctionalUser(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("read")
        @DisplayName("should throw exception for zero or negative ID")
        void shouldThrow_whenIdIsZeroOrNegative() {
            assertThatThrownBy(() -> programFunctionalUserDAO.getFunctionalUser(0L))
                .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> programFunctionalUserDAO.getFunctionalUser(-1L))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests for {@code deleteFunctionalUserType(Long id)} - deletes a FunctionalUserType record.
     */
    @Nested
    @DisplayName("deleteFunctionalUserType")
    class DeleteFunctionalUserType {

        @Test
        @Tag("delete")
        @DisplayName("should delete functional user type when valid ID is provided")
        void shouldDeleteFunctionalUserType_whenValidIdProvided() {
            // Given - Create a dedicated user type for deletion
            FunctionalUserType toDelete = createFunctionalUserType("ToDelete");
            hibernateTemplate.flush();
            Long deleteId = toDelete.getId();

            // Verify it exists
            assertThat(programFunctionalUserDAO.getFunctionalUserType(deleteId)).isNotNull();

            // When
            programFunctionalUserDAO.deleteFunctionalUserType(deleteId);
            hibernateTemplate.flush();

            // Then
            assertThat(programFunctionalUserDAO.getFunctionalUserType(deleteId)).isNull();
        }

        @Test
        @Tag("delete")
        @DisplayName("should throw exception for null ID")
        void shouldThrow_whenIdIsNull() {
            assertThatThrownBy(() -> programFunctionalUserDAO.deleteFunctionalUserType(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("delete")
        @DisplayName("should throw exception for zero or negative ID")
        void shouldThrow_whenIdIsZeroOrNegative() {
            assertThatThrownBy(() -> programFunctionalUserDAO.deleteFunctionalUserType(0L))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> programFunctionalUserDAO.deleteFunctionalUserType(-1L))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests for {@code deleteFunctionalUser(Long id)} - deletes a ProgramFunctionalUser record.
     */
    @Nested
    @DisplayName("deleteFunctionalUser")
    class DeleteFunctionalUser {

        @Test
        @Tag("delete")
        @DisplayName("should delete functional user when valid ID is provided")
        void shouldDeleteFunctionalUser_whenValidIdProvided() {
            // Given - Create a dedicated user for deletion
            ProgramFunctionalUser toDelete = createFunctionalUser(testProgramId1, testUserTypeId1);
            hibernateTemplate.flush();
            Long deleteId = toDelete.getId();

            // Verify it exists
            assertThat(programFunctionalUserDAO.getFunctionalUser(deleteId)).isNotNull();

            // When
            programFunctionalUserDAO.deleteFunctionalUser(deleteId);
            hibernateTemplate.flush();

            // Then
            assertThat(programFunctionalUserDAO.getFunctionalUser(deleteId)).isNull();
        }

        @Test
        @Tag("delete")
        @DisplayName("should throw exception for null ID")
        void shouldThrow_whenIdIsNull() {
            assertThatThrownBy(() -> programFunctionalUserDAO.deleteFunctionalUser(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("delete")
        @DisplayName("should throw exception for zero or negative ID")
        void shouldThrow_whenIdIsZeroOrNegative() {
            assertThatThrownBy(() -> programFunctionalUserDAO.deleteFunctionalUser(0L))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> programFunctionalUserDAO.deleteFunctionalUser(-1L))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests for {@code saveFunctionalUser(ProgramFunctionalUser)} - persist and update operations.
     */
    @Nested
    @DisplayName("saveFunctionalUser")
    class SaveFunctionalUser {

        @Test
        @Tag("create")
        @DisplayName("should persist new functional user and assign ID")
        void shouldPersistNewFunctionalUser_withGeneratedId() {
            // Given
            ProgramFunctionalUser pfu = new ProgramFunctionalUser();
            pfu.setProgramId(testProgramId2);
            pfu.setUserTypeId(testUserTypeId2);

            // When
            programFunctionalUserDAO.saveFunctionalUser(pfu);
            hibernateTemplate.flush();

            // Then
            assertThat(pfu.getId()).isNotNull();
            assertThat(pfu.getId()).isGreaterThan(0L);

            ProgramFunctionalUser found = programFunctionalUserDAO.getFunctionalUser(pfu.getId());
            assertThat(found).isNotNull();
            assertThat(found.getProgramId()).isEqualTo(testProgramId2);
            assertThat(found.getUserTypeId()).isEqualTo(testUserTypeId2.longValue());
        }

        @Test
        @Tag("create")
        @DisplayName("should throw exception for null input")
        void shouldThrow_whenInputIsNull() {
            assertThatThrownBy(() -> programFunctionalUserDAO.saveFunctionalUser(null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests for {@code saveFunctionalUserType(FunctionalUserType)} - persist and update operations.
     */
    @Nested
    @DisplayName("saveFunctionalUserType")
    class SaveFunctionalUserType {

        @Test
        @Tag("create")
        @DisplayName("should persist new functional user type and assign ID")
        void shouldPersistNewFunctionalUserType_withGeneratedId() {
            // Given
            FunctionalUserType fut = new FunctionalUserType();
            fut.setName("Manager");

            // When
            programFunctionalUserDAO.saveFunctionalUserType(fut);
            hibernateTemplate.flush();

            // Then
            assertThat(fut.getId()).isNotNull();
            assertThat(fut.getId()).isGreaterThan(0L);

            FunctionalUserType found = programFunctionalUserDAO.getFunctionalUserType(fut.getId());
            assertThat(found).isNotNull();
            assertThat(found.getName()).isEqualTo("Manager");
        }

        @Test
        @Tag("update")
        @DisplayName("should update existing functional user type name")
        void shouldUpdateExistingFunctionalUserType_whenChangesProvided() {
            // Given
            FunctionalUserType fut = createFunctionalUserType("OldName");
            hibernateTemplate.flush();
            Long savedId = fut.getId();

            // When
            fut.setName("NewName");
            programFunctionalUserDAO.saveFunctionalUserType(fut);
            hibernateTemplate.flush();

            // Then
            FunctionalUserType updated = programFunctionalUserDAO.getFunctionalUserType(savedId);
            assertThat(updated).isNotNull();
            assertThat(updated.getName()).isEqualTo("NewName");
        }

        @Test
        @Tag("create")
        @DisplayName("should throw exception for null input")
        void shouldThrow_whenInputIsNull() {
            assertThatThrownBy(() -> programFunctionalUserDAO.saveFunctionalUserType(null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
