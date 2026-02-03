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
import io.github.carlos_emr.carlos.PMmodule.model.ProgramQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for ProgramQueueDao multi-parameter query methods.
 *
 * <p>These tests validate that HQL queries with multiple positional parameters
 * bind parameters correctly. Tests are designed to catch parameter index errors
 * during Hibernate migration.</p>
 *
 * @since 2026-02-03
 * @see ProgramQueueDao
 */
@DisplayName("ProgramQueueDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class ProgramQueueDaoIntegrationTest extends OpenOTestBase {

    @Autowired
    private ProgramQueueDao programQueueDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private Long testProgramId1 = 100L;
    private Long testProgramId2 = 200L;
    private Long testClientId1 = 1001L;
    private Long testClientId2 = 1002L;

    @BeforeEach
    void setUp() {
        // Create test program queue entries (DAO filters for status='active', not 'A')
        createProgramQueue(testProgramId1, testClientId1, "active");  // Active
        createProgramQueue(testProgramId1, testClientId2, "active");  // Active, different client
        createProgramQueue(testProgramId2, testClientId1, "active");  // Active, different program
        createProgramQueue(testProgramId1, testClientId1, "inactive");  // Inactive, same program/client
    }

    private ProgramQueue createProgramQueue(Long programId, Long clientId, String status) {
        ProgramQueue pq = new ProgramQueue();
        pq.setProgramId(programId);
        pq.setClientId(clientId);
        pq.setStatus(status);
        pq.setReferralDate(new Date());
        pq.setProviderNo(999L);  // Required not-null field
        programQueueDao.saveProgramQueue(pq);
        return pq;
    }

    @Nested
    @DisplayName("getQueue (2 params: programId, clientId)")
    class GetQueue {

        @Test
        @Tag("query")
        @DisplayName("should find queue entry when both program and client match")
        void shouldFind_whenBothParamsMatch() {
            // When
            ProgramQueue result = programQueueDao.getQueue(testProgramId1, testClientId1);

            // Then - Should find one of the entries for this program/client combo
            assertThat(result).isNotNull();
            assertThat(result.getProgramId()).isEqualTo(testProgramId1);
            assertThat(result.getClientId()).isEqualTo(testClientId1);
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when program doesn't match")
        void shouldReturnNull_whenProgramDoesntMatch() {
            // When
            ProgramQueue result = programQueueDao.getQueue(99999L, testClientId1);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when client doesn't match")
        void shouldReturnNull_whenClientDoesntMatch() {
            // When
            ProgramQueue result = programQueueDao.getQueue(testProgramId1, 99999L);

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("getActiveProgramQueue (2 params: programId, demographicNo)")
    class GetActiveProgramQueue {

        @Test
        @Tag("query")
        @DisplayName("should find active queue entry when both params match")
        void shouldFindActive_whenBothParamsMatch() {
            // When
            ProgramQueue result = programQueueDao.getActiveProgramQueue(testProgramId1, testClientId1);

            // Then - Should find the active entry
            assertThat(result).isNotNull();
            assertThat(result.getProgramId()).isEqualTo(testProgramId1);
            assertThat(result.getClientId()).isEqualTo(testClientId1);
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when program doesn't match")
        void shouldReturnNull_whenProgramDoesntMatch() {
            // When
            ProgramQueue result = programQueueDao.getActiveProgramQueue(99999L, testClientId1);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when client doesn't match")
        void shouldReturnNull_whenClientDoesntMatch() {
            // When
            ProgramQueue result = programQueueDao.getActiveProgramQueue(testProgramId1, 99999L);

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Single parameter queries (baseline)")
    class SingleParamQueries {

        @Test
        @Tag("read")
        @DisplayName("should get queue entries by program ID")
        void shouldGetByProgramId() {
            // When
            List<ProgramQueue> results = programQueueDao.getProgramQueuesByProgramId(testProgramId1);

            // Then - Program 1 has 3 entries (2 for client1, 1 for client2)
            assertThat(results)
                .hasSize(3)
                .allMatch(pq -> pq.getProgramId().equals(testProgramId1));
        }
    }
}
