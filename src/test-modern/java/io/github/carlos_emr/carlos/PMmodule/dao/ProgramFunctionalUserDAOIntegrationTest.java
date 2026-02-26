/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * This software was written for CARLOS EMR Project
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
import org.springframework.orm.hibernate5.HibernateTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for ProgramFunctionalUserDAO multi-parameter query methods.
 *
 * @since 2026-02-03
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

    @BeforeEach
    void setUp() {
        long baseId = System.nanoTime() % 100000;
        testProgramId1 = 1000L + baseId;
        testProgramId2 = 2000L + baseId;

        // Create test functional user types
        FunctionalUserType userType1 = createFunctionalUserType("Admin");
        FunctionalUserType userType2 = createFunctionalUserType("Viewer");
        testUserTypeId1 = userType1.getId();
        testUserTypeId2 = userType2.getId();

        // Create test functional users
        createFunctionalUser(testProgramId1, testUserTypeId1);
        createFunctionalUser(testProgramId1, testUserTypeId2);
        createFunctionalUser(testProgramId2, testUserTypeId1);

        hibernateTemplate.flush();
    }

    private FunctionalUserType createFunctionalUserType(String name) {
        FunctionalUserType userType = new FunctionalUserType();
        userType.setName(name);
        hibernateTemplate.save(userType);
        return userType;
    }

    private ProgramFunctionalUser createFunctionalUser(Long programId, Long userTypeId) {
        ProgramFunctionalUser pfu = new ProgramFunctionalUser();
        pfu.setProgramId(programId);
        pfu.setUserTypeId(userTypeId);
        hibernateTemplate.save(pfu);
        return pfu;
    }

    @Nested
    @DisplayName("getFunctionalUserByUserType (2 params)")
    class GetFunctionalUserByUserType {

        @Test
        @Tag("query")
        @DisplayName("should find user when both program and user type match")
        void shouldFind_whenBothParamsMatch() {
            Long result = programFunctionalUserDAO.getFunctionalUserByUserType(testProgramId1, testUserTypeId1);
            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(testProgramId1);
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

    @Nested
    @DisplayName("Single parameter queries (baseline)")
    class SingleParamQueries {

        @Test
        @Tag("read")
        @DisplayName("should get all functional users by program")
        void shouldGetByProgram() {
            List<FunctionalUserType> results = programFunctionalUserDAO.getFunctionalUsers(testProgramId1);
            assertThat(results).hasSize(2);
        }

        @Test
        @Tag("read")
        @DisplayName("should get functional user type by ID")
        void shouldGetUserTypeById() {
            FunctionalUserType result = programFunctionalUserDAO.getFunctionalUserType(testUserTypeId1);
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Admin");
        }

        @Test
        @Tag("read")
        @DisplayName("should get all functional user types")
        void shouldGetAllUserTypes() {
            List<FunctionalUserType> results = programFunctionalUserDAO.getFunctionalUserTypes();
            assertThat(results)
                .isNotEmpty()
                .anyMatch(ut -> ut.getName().equals("Admin"));
        }
    }
}
