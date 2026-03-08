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
package io.github.carlos_emr.carlos.billing.CA.BC.dao;

import io.github.carlos_emr.carlos.billing.CA.BC.model.TeleplanC12;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link TeleplanC12Dao}.
 * <p>Migrated from legacy JUnit 4 TeleplanC12DaoTest with full method coverage.</p>
 *
 * @since 2026-03-07
 */
@DisplayName("TeleplanC12Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class TeleplanC12DaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private TeleplanC12Dao dao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist entity with generated test data")
        void shouldPersistEntity_whenValidDataProvided() throws Exception {
            TeleplanC12 entity = new TeleplanC12();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);
            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find entity by ID with correct fields")
        void shouldReturnMatchingEntity_whenFoundById() throws Exception {
            TeleplanC12 entity = new TeleplanC12();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            entity.setOfficeFolioClaimNo("CLM-001");
            entity.setStatus('O');
            entity.setPractitionerNo("P123");
            dao.persist(entity);

            TeleplanC12 found = dao.find(entity.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(entity.getId());
            assertThat(found.getOfficeFolioClaimNo()).isEqualTo("CLM-001");
            assertThat(found.getStatus()).isEqualTo('O');
            assertThat(found.getPractitionerNo()).isEqualTo("P123");
        }
    }

    @Nested
    @DisplayName("findCurrent")
    class FindCurrent {

        @Test
        @Tag("read")
        @DisplayName("should return records with non-error status")
        void shouldReturnNonErrorRecords_whenQueried() throws Exception {
            TeleplanC12 openRecord = new TeleplanC12();
            EntityDataGenerator.generateTestDataForModelClass(openRecord);
            openRecord.setStatus('O');
            openRecord.setOfficeFolioClaimNo("CLM-100");
            dao.persist(openRecord);

            TeleplanC12 errorRecord = new TeleplanC12();
            EntityDataGenerator.generateTestDataForModelClass(errorRecord);
            errorRecord.setStatus('E');
            errorRecord.setOfficeFolioClaimNo("CLM-101");
            dao.persist(errorRecord);

            List<TeleplanC12> results = dao.findCurrent();

            assertThat(results).isNotEmpty();
            assertThat(results).extracting(TeleplanC12::getStatus)
                    .doesNotContain('E');
            assertThat(results).extracting(TeleplanC12::getOfficeFolioClaimNo)
                    .contains("CLM-100");
        }
    }

    @Nested
    @DisplayName("findByOfficeClaimNo")
    class FindByOfficeClaimNo {

        @Test
        @Tag("read")
        @DisplayName("should return records matching the office claim number")
        void shouldReturnMatchingRecords_whenClaimNoMatches() throws Exception {
            TeleplanC12 match = new TeleplanC12();
            EntityDataGenerator.generateTestDataForModelClass(match);
            match.setOfficeFolioClaimNo("CLM-200");
            dao.persist(match);

            TeleplanC12 nonMatch = new TeleplanC12();
            EntityDataGenerator.generateTestDataForModelClass(nonMatch);
            nonMatch.setOfficeFolioClaimNo("CLM-201");
            dao.persist(nonMatch);

            List<TeleplanC12> results = dao.findByOfficeClaimNo("CLM-200");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getOfficeFolioClaimNo()).isEqualTo("CLM-200");
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no records match")
        void shouldReturnEmptyList_whenNoClaimNoMatches() throws Exception {
            List<TeleplanC12> results = dao.findByOfficeClaimNo("NONEXISTENT");
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("select_c12_record")
    class SelectC12Record {

        @Test
        @Tag("read")
        @DisplayName("should return records matching both status and claim number")
        void shouldReturnMatchingRecords_whenStatusAndClaimNoMatch() throws Exception {
            TeleplanC12 entity = new TeleplanC12();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            entity.setStatus('O');
            entity.setOfficeFolioClaimNo("CLM-300");
            dao.persist(entity);

            TeleplanC12 differentStatus = new TeleplanC12();
            EntityDataGenerator.generateTestDataForModelClass(differentStatus);
            differentStatus.setStatus('E');
            differentStatus.setOfficeFolioClaimNo("CLM-300");
            dao.persist(differentStatus);

            List<TeleplanC12> results = dao.select_c12_record("O", "CLM-300");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getStatus()).isEqualTo('O');
            assertThat(results.get(0).getOfficeFolioClaimNo()).isEqualTo("CLM-300");
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no records match status and claim number")
        void shouldReturnEmptyList_whenNoMatch() throws Exception {
            List<TeleplanC12> results = dao.select_c12_record("X", "NONEXISTENT");
            assertThat(results).isEmpty();
        }
    }
}
