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
import io.github.carlos_emr.carlos.PMmodule.model.ClientReferral;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.hibernate5.HibernateTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for ClientReferralDAO multi-parameter query methods.
 *
 * <p>These tests validate that HQL queries with multiple positional parameters
 * bind parameters correctly. Tests are designed to catch parameter index errors
 * during Hibernate migration.</p>
 *
 * @since 2026-02-03
 * @see ClientReferralDAO
 */
@DisplayName("ClientReferralDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class ClientReferralDAOIntegrationTest extends OpenOTestBase {

    @Autowired
    private ClientReferralDAO clientReferralDAO;

    @Autowired
    private HibernateTemplate hibernateTemplate;

    private Long testClientId1;
    private Long testClientId2;
    private Long testProgramId1;
    private Long testProgramId2;
    private Integer testFacilityId1 = 1;
    private Integer testFacilityId2 = 2;

    @BeforeEach
    void setUp() {
        long nanoTime = System.nanoTime();
        long positiveNanoTime = Math.abs(nanoTime);
        long baseId = positiveNanoTime % 10000L;

        // Use unique client IDs derived arithmetically to avoid parsing errors
        testClientId1 = baseId * 100 + 1;
        testClientId2 = baseId * 100 + 2;

        // Create programs for referrals
        Program program1 = createProgram("Test Program 1");
        Program program2 = createProgram("Test Program 2");
        testProgramId1 = (long) program1.getId();
        testProgramId2 = (long) program2.getId();

        // Create test referrals with various combinations
        // Note: Not setting providerNo to avoid triggering formula subquery against provider table
        createReferral(testClientId1, testProgramId1, testFacilityId1, "active");
        createReferral(testClientId1, testProgramId2, testFacilityId1, "active");
        createReferral(testClientId1, testProgramId1, testFacilityId2, "pending");
        createReferral(testClientId2, testProgramId1, testFacilityId1, "active");

        hibernateTemplate.flush();
    }

    private Program createProgram(String name) {
        Program program = new Program();
        program.setName(name);
        program.setType("community");
        program.setProgramStatus("active");
        hibernateTemplate.save(program);
        return program;
    }

    private ClientReferral createReferral(Long clientId, Long programId,
                                          Integer facilityId, String status) {
        ClientReferral ref = new ClientReferral();
        ref.setClientId(clientId);
        ref.setProgramId(programId);
        ref.setFacilityId(facilityId);
        ref.setStatus(status);
        // Not setting providerNo to avoid formula subquery issues
        ref.setReferralDate(new Date());
        hibernateTemplate.save(ref);
        return ref;
    }

    @Nested
    @DisplayName("getActiveReferrals (multi-param: clientId, facilityId, 3 status values)")
    class GetActiveReferrals {

        @Test
        @Tag("query")
        @DisplayName("should find active referrals when client matches")
        void shouldFindActive_whenClientMatches() {
            // When - null facilityId tests the 4-param branch
            List<ClientReferral> results = clientReferralDAO.getActiveReferrals(
                testClientId1, null);

            // Then - Should find active referrals for client 1
            assertThat(results)
                .isNotEmpty()
                .allMatch(r -> r.getClientId().equals(testClientId1));
        }

        @Test
        @Tag("query")
        @DisplayName("should find active referrals when client and facility match")
        void shouldFindActive_whenClientAndFacilityMatch() {
            // When - non-null facilityId tests the 6-param branch
            List<ClientReferral> results = clientReferralDAO.getActiveReferrals(
                testClientId1, testFacilityId1);

            // Then
            assertThat(results)
                .isNotEmpty()
                .allMatch(r -> r.getClientId().equals(testClientId1));
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when client doesn't match")
        void shouldReturnEmpty_whenClientDoesntMatch() {
            // When
            List<ClientReferral> results = clientReferralDAO.getActiveReferrals(
                99999L, testFacilityId1);

            // Then
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("getActiveReferralsByClientAndProgram (2 params: clientId, programId)")
    class GetActiveReferralsByClientAndProgram {

        @Test
        @Tag("query")
        @DisplayName("should find active referrals when client and program match")
        void shouldFind_whenClientAndProgramMatch() {
            // When
            List<ClientReferral> results = clientReferralDAO.getActiveReferralsByClientAndProgram(
                testClientId1, testProgramId1);

            // Then - Only active referrals should be returned
            assertThat(results)
                .isNotEmpty()
                .allMatch(r -> r.getClientId().equals(testClientId1))
                .allMatch(r -> r.getProgramId().equals(testProgramId1));
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when client doesn't match")
        void shouldReturnEmpty_whenClientDoesntMatch() {
            // When
            List<ClientReferral> results = clientReferralDAO.getActiveReferralsByClientAndProgram(
                99999L, testProgramId1);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when program doesn't match")
        void shouldReturnEmpty_whenProgramDoesntMatch() {
            // When
            List<ClientReferral> results = clientReferralDAO.getActiveReferralsByClientAndProgram(
                testClientId1, 99999L);

            // Then
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("Single parameter queries (baseline)")
    class SingleParamQueries {

        @Test
        @Tag("read")
        @DisplayName("should get referral by ID")
        void shouldGetById() {
            // Given - Create and save a referral
            ClientReferral saved = createReferral(testClientId1, testProgramId1, testFacilityId1, "active");
            hibernateTemplate.flush();
            Long savedId = saved.getId();

            // When
            ClientReferral result = clientReferralDAO.getClientReferral(savedId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(savedId);
            assertThat(result.getClientId()).isEqualTo(testClientId1);
        }

        @Test
        @Tag("read")
        @DisplayName("should get referrals by program")
        void shouldGetByProgram() {
            // When
            List<ClientReferral> results = clientReferralDAO.getClientReferralsByProgram(
                testProgramId1.intValue());

            // Then - Program 1 has referrals from multiple clients
            assertThat(results)
                .isNotEmpty()
                .allMatch(r -> r.getProgramId().equals(testProgramId1));
        }
    }
}
