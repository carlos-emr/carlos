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
import io.github.carlos_emr.carlos.PMmodule.model.ClientReferral;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import io.github.carlos_emr.carlos.test.base.HibernateTemplate;
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
public class ClientReferralDAOIntegrationTest extends CarlosTestBase {

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

        testClientId1 = baseId * 100 + 1;
        testClientId2 = baseId * 100 + 2;

        Program program1 = createProgram("Test Program 1");
        Program program2 = createProgram("Test Program 2");
        testProgramId1 = (long) program1.getId();
        testProgramId2 = (long) program2.getId();

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
        // Set facilityId explicitly to 0 (no facility) so that programs created here
        // do not match the "s.facilityId is null" branch in getReferralsByFacility HQL.
        // Program.facilityId was changed from primitive int (implicit default 0) to
        // Integer (implicit default null) — restoring 0 preserves the original semantics.
        program.setFacilityId(0);
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
        ref.setReferralDate(new Date());
        hibernateTemplate.save(ref);
        return ref;
    }

    /** Tests for CRUD operations on ClientReferral entities. */
    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("read")
        @DisplayName("should retrieve referral by valid ID")
        void shouldRetrieveReferral_whenValidIdProvided() {
            // Given
            ClientReferral saved = createReferral(testClientId1, testProgramId1, testFacilityId1, "active");
            hibernateTemplate.flush();

            // When
            ClientReferral found = clientReferralDAO.getClientReferral(saved.getId());

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getClientId()).isEqualTo(testClientId1);
        }

        @Test
        @Tag("create")
        @DisplayName("should persist referral with valid data")
        void shouldPersistReferral_whenValidDataProvided() {
            // Given
            ClientReferral ref = new ClientReferral();
            ref.setClientId(testClientId1);
            ref.setProgramId(testProgramId1);
            ref.setFacilityId(testFacilityId1);
            ref.setStatus("active");
            ref.setReferralDate(new Date());
            ref.setNotes("Test referral note");

            // When
            clientReferralDAO.saveClientReferral(ref);
            hibernateTemplate.flush();

            // Then
            assertThat(ref.getId()).isNotNull();
            ClientReferral found = clientReferralDAO.getClientReferral(ref.getId());
            assertThat(found).isNotNull();
            assertThat(found.getNotes()).isEqualTo("Test referral note");
        }

        @Test
        @Tag("update")
        @DisplayName("should update referral with changes")
        void shouldUpdateReferral_whenChangesProvided() {
            // Given
            ClientReferral saved = createReferral(testClientId1, testProgramId1, testFacilityId1, "pending");
            hibernateTemplate.flush();

            // When
            saved.setStatus("active");
            saved.setNotes("Updated notes");
            hibernateTemplate.update(saved);
            hibernateTemplate.flush();

            // Then
            ClientReferral found = clientReferralDAO.getClientReferral(saved.getId());
            assertThat(found.getStatus()).isEqualTo("active");
            assertThat(found.getNotes()).isEqualTo("Updated notes");
        }
    }

    /** Tests for getReferrals by client ID (single param). */
    @Nested
    @DisplayName("getReferrals (single param: clientId)")
    class GetReferralsByClientId {

        @Test
        @Tag("query")
        @DisplayName("should filter referrals by client ID")
        void shouldFilterReferrals_byClientId() {
            // When
            List<ClientReferral> results = clientReferralDAO.getReferrals(testClientId1);

            // Then
            assertThat(results)
                .isNotEmpty()
                .allMatch(r -> r.getClientId().equals(testClientId1));
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when client doesn't exist")
        void shouldReturnEmpty_whenClientDoesntExist() {
            // When
            List<ClientReferral> results = clientReferralDAO.getReferrals(99999L);

            // Then
            assertThat(results).isEmpty();
        }
    }

    /** Tests for getReferralsByFacility (multi-param with subselect). */
    @Nested
    @DisplayName("getReferralsByFacility (2 params: clientId, facilityId)")
    class GetReferralsByFacility {

        @Test
        @Tag("query")
        @DisplayName("should filter referrals by client and facility")
        void shouldFilterReferrals_byClientAndFacility() {
            // When
            List<ClientReferral> results = clientReferralDAO.getReferralsByFacility(
                testClientId1, testFacilityId1);

            // Then
            assertThat(results)
                .isNotEmpty()
                .allMatch(r -> r.getClientId().equals(testClientId1))
                .allMatch(r -> r.getFacilityId().equals(testFacilityId1));
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when client doesn't match facility")
        void shouldReturnEmpty_whenClientDoesntMatchFacility() {
            // When
            List<ClientReferral> results = clientReferralDAO.getReferralsByFacility(
                99999L, testFacilityId1);

            // Then
            assertThat(results).isEmpty();
        }
    }

    /** Tests for getActiveReferrals (multi-param: clientId, facilityId, status values). */
    @Nested
    @DisplayName("getActiveReferrals (multi-param)")
    class GetActiveReferrals {

        @Test
        @Tag("query")
        @DisplayName("should find active referrals when client matches (null facility)")
        void shouldFindActive_whenClientMatches() {
            // When - null facilityId tests the 4-param branch
            List<ClientReferral> results = clientReferralDAO.getActiveReferrals(
                testClientId1, null);

            // Then
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

    /** Tests for getActiveReferralsByClientAndProgram (2 params). */
    @Nested
    @DisplayName("getActiveReferralsByClientAndProgram (2 params)")
    class GetActiveReferralsByClientAndProgram {

        @Test
        @Tag("query")
        @DisplayName("should find active referrals when client and program match")
        void shouldFind_whenClientAndProgramMatch() {
            // When
            List<ClientReferral> results = clientReferralDAO.getActiveReferralsByClientAndProgram(
                testClientId1, testProgramId1);

            // Then
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

    /** Tests for Criteria API and single parameter queries. */
    @Nested
    @DisplayName("Search and single parameter queries")
    class SearchAndSingleParamQueries {

        @Test
        @Tag("read")
        @DisplayName("should get referrals by program")
        void shouldGetReferrals_byProgram() {
            // When
            List<ClientReferral> results = clientReferralDAO.getClientReferralsByProgram(
                testProgramId1.intValue());

            // Then
            assertThat(results)
                .isNotEmpty()
                .allMatch(r -> r.getProgramId().equals(testProgramId1));
        }

        @Test
        @Tag("search")
        @DisplayName("should search referrals using Criteria API")
        void shouldSearchReferrals_usingCriteriaApi() {
            // Given
            ClientReferral searchCriteria = new ClientReferral();
            searchCriteria.setProgramId(testProgramId1);

            // When
            List<ClientReferral> results = clientReferralDAO.search(searchCriteria);

            // Then
            assertThat(results)
                .isNotEmpty()
                .allMatch(r -> r.getProgramId().equals(testProgramId1));
        }

        @Test
        @Tag("read")
        @DisplayName("should return all referrals when no filter applied")
        void shouldReturnAllReferrals_whenNoFilterApplied() {
            // When
            List<ClientReferral> results = clientReferralDAO.getReferrals();

            // Then - setUp creates 4 referrals
            assertThat(results)
                .hasSizeGreaterThanOrEqualTo(4)
                .extracting(ClientReferral::getClientId)
                .contains(testClientId1, testClientId2);
        }
    }
}
