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

import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ProviderLabRoutingDao}.
 *
 * <p>Migrated from legacy {@code ProviderLabRoutingDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ProviderLabRoutingDao
 */
@DisplayName("ProviderLabRouting Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("provider")
@Transactional
public class ProviderLabRoutingDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ProviderLabRoutingDao dao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist provider lab routing with generated ID")
        void shouldPersistProviderLabRouting_whenValidDataProvided() throws Exception {
            ProviderLabRoutingModel entity = new ProviderLabRoutingModel();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should return provider lab routings by lab no and lab type")
        void shouldReturnProviderLabRoutings_byLabNoAndLabType() {
            assertThat(dao.getProviderLabRoutings(1, "HL7")).isNotNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should find routings by status and lab no type")
        void shouldFindRoutings_byStatusAndLabNoType() {
            assertThat(dao.findByStatusANDLabNoType(100, "HL7", "A")).isNotNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should find routings by provider no")
        void shouldFindRoutings_byProviderNo() {
            assertThat(dao.findByProviderNo("100", "N")).isNotNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should find routings by lab no type and status")
        void shouldFindRoutings_byLabNoTypeAndStatus() {
            assertThat(dao.findByLabNoTypeAndStatus(100, "BCP", "STS")).isNotNull();
        }
    }
}
