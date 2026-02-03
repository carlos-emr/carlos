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
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for ProgramProviderDAO multi-parameter query methods.
 *
 * <p>These tests validate that HQL queries with multiple positional parameters
 * bind parameters correctly. Tests are designed to catch parameter index errors
 * during Hibernate migration.</p>
 *
 * @since 2026-02-03
 * @see ProgramProviderDAO
 */
@DisplayName("ProgramProviderDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class ProgramProviderDAOIntegrationTest extends OpenOTestBase {

    @Autowired
    @Qualifier("programProviderDAO")
    private ProgramProviderDAO programProviderDAO;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private ProgramProvider createProgramProvider(String providerNo, Long programId) {
        ProgramProvider pp = new ProgramProvider();
        pp.setProviderNo(providerNo);
        pp.setProgramId(programId);
        programProviderDAO.saveProgramProvider(pp);
        return pp;
    }

    private ProgramProvider createProgramProvider(String providerNo, Long programId, Long roleId) {
        ProgramProvider pp = new ProgramProvider();
        pp.setProviderNo(providerNo);
        pp.setProgramId(programId);
        pp.setRoleId(roleId);
        programProviderDAO.saveProgramProvider(pp);
        return pp;
    }

    @Nested
    @DisplayName("getProgramProviderByProviderProgramId (2 params)")
    class GetByProviderProgramId {

        @Test
        @Tag("query")
        @DisplayName("should find program provider when both providerNo and programId match")
        void shouldFind_whenBothParamsMatch() {
            // Given
            ProgramProvider match = createProgramProvider("P001", 100L);
            ProgramProvider wrongProvider = createProgramProvider("P002", 100L);  // Different provider
            ProgramProvider wrongProgram = createProgramProvider("P001", 200L);    // Different program
            entityManager.flush();

            // When
            List<ProgramProvider> results = programProviderDAO
                .getProgramProviderByProviderProgramId("P001", 100L);

            // Then - Only match should be returned
            assertThat(results)
                .hasSize(1)
                .extracting(ProgramProvider::getId)
                .containsExactly(match.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when provider doesn't match")
        void shouldReturnEmpty_whenProviderDoesntMatch() {
            // Given
            createProgramProvider("P001", 100L);
            entityManager.flush();

            // When
            List<ProgramProvider> results = programProviderDAO
                .getProgramProviderByProviderProgramId("NONEXISTENT", 100L);

            // Then
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("getProgramProvider (2 params: providerNo, programId)")
    class GetProgramProviderTwoParams {

        @Test
        @Tag("query")
        @DisplayName("should return single program provider when both params match")
        void shouldReturnSingle_whenBothParamsMatch() {
            // Given
            ProgramProvider pp = createProgramProvider("P001", 100L);
            entityManager.flush();

            // When
            ProgramProvider found = programProviderDAO.getProgramProvider("P001", 100L);

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getProviderNo()).isEqualTo("P001");
            assertThat(found.getProgramId()).isEqualTo(100L);
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when no match")
        void shouldReturnNull_whenNoMatch() {
            // Given
            createProgramProvider("P001", 100L);
            entityManager.flush();

            // When
            ProgramProvider found = programProviderDAO.getProgramProvider("P001", 999L);

            // Then
            assertThat(found).isNull();
        }
    }

    @Nested
    @DisplayName("getProgramProvider (3 params: providerNo, programId, roleId)")
    class GetProgramProviderThreeParams {

        @Test
        @Tag("query")
        @DisplayName("should find when all three parameters match")
        void shouldFind_whenAllThreeParamsMatch() {
            // Given
            ProgramProvider match = createProgramProvider("P001", 100L, 10L);
            ProgramProvider wrongRole = createProgramProvider("P001", 100L, 20L);  // Different role
            ProgramProvider wrongProgram = createProgramProvider("P001", 200L, 10L);  // Different program
            entityManager.flush();

            // When
            ProgramProvider found = programProviderDAO.getProgramProvider("P001", 100L, 10L);

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(match.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when role doesn't match")
        void shouldReturnNull_whenRoleDoesntMatch() {
            // Given
            createProgramProvider("P001", 100L, 10L);
            entityManager.flush();

            // When
            ProgramProvider found = programProviderDAO.getProgramProvider("P001", 100L, 999L);

            // Then
            assertThat(found).isNull();
        }
    }

    @Nested
    @DisplayName("Single parameter queries (baseline)")
    class SingleParamQueries {

        @Test
        @Tag("read")
        @DisplayName("should get program providers by provider number")
        void shouldGetByProviderNo() {
            // Given
            createProgramProvider("P777", 100L);
            createProgramProvider("P777", 200L);
            createProgramProvider("P888", 100L);  // Different provider
            entityManager.flush();

            // When
            List<ProgramProvider> results = programProviderDAO.getProgramProviderByProviderNo("P777");

            // Then
            assertThat(results)
                .hasSize(2)
                .allMatch(pp -> pp.getProviderNo().equals("P777"));
        }

        @Test
        @Tag("read")
        @DisplayName("should get program providers by program ID")
        void shouldGetByProgramId() {
            // Given
            createProgramProvider("P001", 555L);
            createProgramProvider("P002", 555L);
            createProgramProvider("P003", 666L);  // Different program
            entityManager.flush();

            // When
            List<ProgramProvider> results = programProviderDAO.getProgramProviders(555L);

            // Then
            assertThat(results)
                .hasSize(2)
                .allMatch(pp -> pp.getProgramId().equals(555L));
        }
    }
}
