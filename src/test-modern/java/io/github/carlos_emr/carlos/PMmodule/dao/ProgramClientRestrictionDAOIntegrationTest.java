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
import io.github.carlos_emr.carlos.PMmodule.model.ProgramClientRestriction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Collection;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for ProgramClientRestrictionDAO multi-parameter query methods.
 *
 * <p>These tests validate that HQL queries with multiple positional parameters
 * bind parameters correctly. Tests are designed to catch parameter index errors
 * during Hibernate migration.</p>
 *
 * <p>HQL queries tested:
 * <ul>
 *   <li>{@code find(programId, demographicNo)}: "from ProgramClientRestriction pcr where pcr.enabled = true and pcr.programId = ?0 and pcr.demographicNo = ?1"</li>
 *   <li>{@code findForClient(demographicNo, facilityId)}: "from ProgramClientRestriction pcr where pcr.enabled = true and pcr.demographicNo = ?0 and pcr.programId in (select s.id from Program s where s.facilityId = ?1 or s.facilityId is null)"</li>
 * </ul>
 * </p>
 *
 * @since 2026-02-03
 * @see ProgramClientRestrictionDAO
 */
@DisplayName("ProgramClientRestrictionDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class ProgramClientRestrictionDAOIntegrationTest extends OpenOTestBase {

    @Autowired
    private ProgramClientRestrictionDAO programClientRestrictionDAO;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private int testProgramId1;
    private int testProgramId2;
    private int testDemoNo1;
    private int testDemoNo2;

    @BeforeEach
    void setUp() {
        // Use unique IDs based on timestamp to avoid conflicts
        int baseId = (int) (System.nanoTime() % 100000);
        testProgramId1 = 100 + baseId;
        testProgramId2 = 200 + baseId;
        testDemoNo1 = 1001 + baseId;
        testDemoNo2 = 1002 + baseId;

        // Create test restrictions - these are the entities the HQL queries will find
        // Restriction for program1, demo1 (enabled)
        createRestriction(testProgramId1, testDemoNo1, true);
        // Restriction for program1, demo2 (enabled)
        createRestriction(testProgramId1, testDemoNo2, true);
        // Restriction for program2, demo1 (enabled)
        createRestriction(testProgramId2, testDemoNo1, true);
        // Restriction for program1, demo1 (disabled - should NOT be found)
        createRestriction(testProgramId1, testDemoNo1, false);
    }

    private ProgramClientRestriction createRestriction(int programId, int demographicNo, boolean enabled) {
        ProgramClientRestriction pcr = new ProgramClientRestriction();
        pcr.setProgramId(programId);
        pcr.setDemographicNo(demographicNo);
        pcr.setEnabled(enabled);
        pcr.setProviderNo("999");
        pcr.setStartDate(new Date());
        pcr.setEndDate(new Date(System.currentTimeMillis() + 86400000)); // +1 day
        pcr.setComments("Test restriction");
        pcr.setCommentId("test-" + System.nanoTime());  // Required non-null field
        programClientRestrictionDAO.save(pcr);
        return pcr;
    }

    @Nested
    @DisplayName("find (2 params: programId, demographicNo)")
    class FindByProgramAndDemo {

        @Test
        @Tag("query")
        @DisplayName("should find restriction when both program and demographic match")
        void shouldFind_whenBothParamsMatch() {
            // When - Query with both params that should match
            Collection<ProgramClientRestriction> results = programClientRestrictionDAO.find(
                testProgramId1, testDemoNo1);

            // Then - Should find exactly 1 enabled restriction
            assertThat(results)
                .hasSize(1)
                .first()
                .satisfies(pcr -> {
                    assertThat(pcr.getProgramId()).isEqualTo(testProgramId1);
                    assertThat(pcr.getDemographicNo()).isEqualTo(testDemoNo1);
                    assertThat(pcr.isEnabled()).isTrue();
                });
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when program doesn't match")
        void shouldReturnEmpty_whenProgramDoesntMatch() {
            // When - Program 999999 doesn't exist
            Collection<ProgramClientRestriction> results = programClientRestrictionDAO.find(
                999999, testDemoNo1);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when demographic doesn't match")
        void shouldReturnEmpty_whenDemoDoesntMatch() {
            // When - Demographic 999999 doesn't exist
            Collection<ProgramClientRestriction> results = programClientRestrictionDAO.find(
                testProgramId1, 999999);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should verify parameter order - swapping params should return different results")
        void shouldVerifyParameterOrder_swappingParamsShouldReturnDifferent() {
            // This test catches parameter binding errors where ?0 and ?1 are swapped
            // If parameters were incorrectly bound, we'd get unexpected results

            // When - Search for program2, demo1 (should find 1)
            Collection<ProgramClientRestriction> results1 = programClientRestrictionDAO.find(
                testProgramId2, testDemoNo1);

            // When - Search for program1, demo2 (should also find 1, but different)
            Collection<ProgramClientRestriction> results2 = programClientRestrictionDAO.find(
                testProgramId1, testDemoNo2);

            // Then - Both should have exactly 1 result
            assertThat(results1).hasSize(1);
            assertThat(results2).hasSize(1);

            // And the results should be different (different program/demo combos)
            ProgramClientRestriction pcr1 = results1.iterator().next();
            ProgramClientRestriction pcr2 = results2.iterator().next();

            assertThat(pcr1.getProgramId()).isEqualTo(testProgramId2);
            assertThat(pcr1.getDemographicNo()).isEqualTo(testDemoNo1);

            assertThat(pcr2.getProgramId()).isEqualTo(testProgramId1);
            assertThat(pcr2.getDemographicNo()).isEqualTo(testDemoNo2);
        }
    }

    @Nested
    @DisplayName("Single parameter queries (baseline)")
    class SingleParamQueries {

        @Test
        @Tag("read")
        @DisplayName("should find all restrictions for client")
        void shouldFindAll_whenRestrictionsExist() {
            // When
            Collection<ProgramClientRestriction> results = programClientRestrictionDAO.findForClient(testDemoNo1);

            // Then - Demo1 has enabled restrictions in both programs
            assertThat(results)
                .hasSize(2)
                .allMatch(pcr -> pcr.getDemographicNo() == testDemoNo1)
                .allMatch(ProgramClientRestriction::isEnabled);
        }

        @Test
        @Tag("read")
        @DisplayName("should find restrictions for specific program")
        void shouldFindByProgram() {
            // When
            Collection<ProgramClientRestriction> results = programClientRestrictionDAO.findForProgram(testProgramId1);

            // Then - Program1 has 2 enabled restrictions (demo1 and demo2)
            assertThat(results)
                .hasSize(2)
                .allMatch(pcr -> pcr.getProgramId() == testProgramId1)
                .allMatch(ProgramClientRestriction::isEnabled);
        }
    }
}
