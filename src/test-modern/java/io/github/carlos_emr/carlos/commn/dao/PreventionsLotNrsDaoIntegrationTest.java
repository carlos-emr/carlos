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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.PreventionsLotNrs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link PreventionsLotNrsDao} covering lot number data
 * retrieval, name-based lookups, lot number string queries, and paged data access.
 *
 * <p>Migrated from legacy {@code PreventionsLotNrsDaoTest} (JUnit 4 / DaoTestFixtures)
 * with BDD-style naming and AssertJ assertions.</p>
 *
 * @since 2026-03-07
 * @see PreventionsLotNrsDao
 */
@DisplayName("PreventionsLotNrsDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("prevention")
@Transactional
public class PreventionsLotNrsDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private PreventionsLotNrsDao dao;

    private PreventionsLotNrs createLotNr(String prevention, String lotNr, boolean deleted) {
        PreventionsLotNrs p = new PreventionsLotNrs();
        EntityDataGenerator.generateTestDataForModelClass(p);
        p.setLotNr(lotNr);
        p.setPreventionType(prevention);
        p.setProviderNo("unit_tester");
        p.setCreationDate(new Date());
        p.setDeleted(deleted);
        dao.persist(p);
        hibernateTemplate.flush();
        return p;
    }

    @Nested
    @DisplayName("findLotNrData")
    class FindLotNrData {

        @Test
        @Tag("query")
        @DisplayName("should return non-deleted lot numbers when querying for active records")
        void shouldReturnNonDeletedLotNumbers_whenQueryingForActiveRecords() {
            // Given
            PreventionsLotNrs p1 = createLotNr("Flu", "abcdef1", false);
            PreventionsLotNrs p2 = createLotNr("Flu", "abcdef2", false);

            // When
            List<PreventionsLotNrs> result = dao.findLotNrData(false);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(PreventionsLotNrs::getId)
                    .containsExactly(p1.getId(), p2.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when querying for deleted records that do not exist")
        void shouldReturnEmptyList_whenQueryingForDeletedRecordsThatDoNotExist() {
            // Given
            createLotNr("Flu", "abcdef1", false);
            createLotNr("Flu", "abcdef2", false);

            // When
            List<PreventionsLotNrs> result = dao.findLotNrData(true);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByName")
    class FindByName {

        @Test
        @Tag("query")
        @DisplayName("should return deleted lot number when searching by prevention type and lot number")
        void shouldReturnDeletedLotNumber_whenSearchingByPreventionTypeAndLotNumber() {
            // Given
            createLotNr("Flu", "abcdef1", false);
            createLotNr("Flu", "abcdef2", false);
            PreventionsLotNrs p3 = createLotNr("Flu", "abcdef3", true);

            // When
            PreventionsLotNrs result = dao.findByName("Flu", "abcdef3", true);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(p3.getId());
        }
    }

    @Nested
    @DisplayName("findLotNrs")
    class FindLotNrs {

        @Test
        @Tag("query")
        @DisplayName("should return all lot number strings for prevention type regardless of deletion status")
        void shouldReturnAllLotNumberStrings_forPreventionTypeRegardlessOfDeletionStatus() {
            // Given
            createLotNr("Flu", "abcdef1", false);
            createLotNr("Flu", "abcdef2", false);
            createLotNr("Flu", "abcdef3", true);

            // When
            List<String> result = dao.findLotNrs("Flu", null);

            // Then
            assertThat(result).hasSize(3);
            assertThat(result).containsExactly("abcdef1", "abcdef2", "abcdef3");
        }
    }

    @Nested
    @DisplayName("findPagedData")
    class FindPagedData {

        @Test
        @Tag("query")
        @DisplayName("should return all records within page bounds for prevention type")
        void shouldReturnAllRecords_withinPageBoundsForPreventionType() {
            // Given
            createLotNr("Flu", "abcdef1", false);
            createLotNr("Flu", "abcdef2", false);
            createLotNr("Flu", "abcdef3", true);

            // When
            List<PreventionsLotNrs> result = dao.findPagedData("Flu", null, 0, 10);

            // Then
            assertThat(result).hasSize(3);
        }
    }
}
