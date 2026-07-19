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

import io.github.carlos_emr.carlos.test.base.OpenOTestBase;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramClientStatus;
import io.github.carlos_emr.carlos.commn.model.Admission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
 * Integration tests for ProgramClientStatusDAO multi-parameter query methods.
 *
 * @since 2026-02-03
 * @see ProgramClientStatusDAO
 */
@DisplayName("ProgramClientStatusDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class ProgramClientStatusDAOIntegrationTest extends OpenOTestBase {

    @Autowired
    private ProgramClientStatusDAO programClientStatusDAO;

    @Autowired
    private HibernateTemplate hibernateTemplate;

    private Integer testProgramId1;
    private Integer testProgramId2;
    private Integer testStatusId1;
    private Integer testStatusId2;

    @BeforeEach
    void setUp() {
        int baseId = (int) (System.nanoTime() % 100000);
        testProgramId1 = 1000 + baseId;
        testProgramId2 = 2000 + baseId;

        // Create test client statuses
        ProgramClientStatus status1 = createClientStatus(testProgramId1, "Active");
        ProgramClientStatus status2 = createClientStatus(testProgramId1, "Pending");
        createClientStatus(testProgramId2, "Active");

        testStatusId1 = status1.getId();
        testStatusId2 = status2.getId();

        hibernateTemplate.flush();
    }

    private ProgramClientStatus createClientStatus(Integer programId, String name) {
        ProgramClientStatus status = new ProgramClientStatus();
        status.setProgramId(programId);
        status.setName(name);
        hibernateTemplate.save(status);
        return status;
    }

    @Nested
    @DisplayName("getAllClientsInStatus (2 params)")
    @Disabled("Requires Admission entity which has complex JPA relationships not available in test context")
    class GetAllClientsInStatus {

        @Test
        @Tag("query")
        @DisplayName("should return empty when no admissions exist")
        void shouldReturnEmpty_whenNoAdmissionsExist() {
            List<Admission> results = programClientStatusDAO.getAllClientsInStatus(testProgramId1, testStatusId1);
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when program doesn't match")
        void shouldReturnEmpty_whenProgramDoesntMatch() {
            List<Admission> results = programClientStatusDAO.getAllClientsInStatus(99999, testStatusId1);
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when status doesn't match")
        void shouldReturnEmpty_whenStatusDoesntMatch() {
            List<Admission> results = programClientStatusDAO.getAllClientsInStatus(testProgramId1, 99999);
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should throw exception for invalid program ID")
        void shouldThrow_whenProgramIdInvalid() {
            assertThatThrownBy(() -> programClientStatusDAO.getAllClientsInStatus(null, testStatusId1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("query")
        @DisplayName("should throw exception for invalid status ID")
        void shouldThrow_whenStatusIdInvalid() {
            assertThatThrownBy(() -> programClientStatusDAO.getAllClientsInStatus(testProgramId1, null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Single parameter queries (baseline)")
    class SingleParamQueries {

        @Test
        @Tag("read")
        @DisplayName("should get all statuses by program")
        void shouldGetByProgram() {
            List<ProgramClientStatus> results = programClientStatusDAO.getProgramClientStatuses(testProgramId1);
            assertThat(results)
                .hasSize(2)
                .allMatch(s -> s.getProgramId().equals(testProgramId1));
        }

        @Test
        @Tag("read")
        @DisplayName("should get status by ID")
        void shouldGetById() {
            ProgramClientStatus result = programClientStatusDAO.getProgramClientStatus(String.valueOf(testStatusId1));
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Active");
        }
    }
}
