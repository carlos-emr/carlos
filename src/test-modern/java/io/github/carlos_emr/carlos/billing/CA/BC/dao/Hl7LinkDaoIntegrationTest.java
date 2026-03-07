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

import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Link;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link Hl7LinkDao}.
 * <p>
 * Note: Many Hl7LinkDao methods involve complex cross-entity joins
 * (Hl7Pid, Hl7Obr, Demographic, Provider, Hl7Message). Tests for those
 * methods verify empty-result behavior when no matching data exists.
 * </p>
 *
 * @since 2026-03-07
 */
@DisplayName("Hl7LinkDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class Hl7LinkDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private Hl7LinkDao dao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist entity with explicitly set ID")
        void shouldPersistEntity_whenValidDataProvided() {
            Hl7Link entity = new Hl7Link();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            entity.setId(1);
            entity.setDemographicNo(100);
            entity.setStatus("P");
            entity.setProviderNo("DOC01");
            dao.persist(entity);

            Hl7Link found = dao.find(1);
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(1);
            assertThat(found.getDemographicNo()).isEqualTo(100);
            assertThat(found.getStatus()).isEqualTo("P");
            assertThat(found.getProviderNo()).isEqualTo("DOC01");
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when entity not found by ID")
        void shouldReturnNull_whenInvalidIdProvided() {
            Hl7Link found = dao.find(-999);
            assertThat(found).isNull();
        }
    }

    @Nested
    @DisplayName("findLabs")
    class FindLabs {

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no linked labs exist")
        void shouldReturnEmptyList_whenNoLinkedLabsExist() {
            List<Object[]> results = dao.findLabs();
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findMagicLinks")
    class FindMagicLinks {

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no magic links exist")
        void shouldReturnEmptyList_whenNoMagicLinksExist() {
            List<Object[]> results = dao.findMagicLinks();
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findLinksAndRequestDates")
    class FindLinksAndRequestDates {

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no links exist for demographic")
        void shouldReturnEmptyList_whenNoDemographicMatches() {
            List<Object[]> results = dao.findLinksAndRequestDates(-999);
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findReports")
    class FindReports {

        @Test
        @Tag("read")
        @DisplayName("should return empty list when command is null")
        void shouldReturnEmptyList_whenCommandIsNull() {
            List<Object[]> results = dao.findReports(new Date(), new Date(), "-ULL", "patient_name", null);
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when command is empty string")
        void shouldReturnEmptyList_whenCommandIsEmpty() {
            List<Object[]> results = dao.findReports(new Date(), new Date(), "-APL", "patient_name", "");
            assertThat(results).isEmpty();
        }
    }
}
