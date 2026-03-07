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
import io.github.carlos_emr.carlos.commn.model.ScratchPad;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ScratchPadDao} covering create, isScratchFilled, and findByProviderNo.
 *
 * <p>Migrated from legacy {@code ScratchPadDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ScratchPadDao
 */
@DisplayName("ScratchPad Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("admin")
@Transactional
public class ScratchPadDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ScratchPadDao dao;

    @Nested
    @DisplayName("Create operations")
    class CreateOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist scratch pad with generated ID")
        void shouldPersistScratchPad_whenValidDataProvided() {
            ScratchPad entity = new ScratchPad();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isPositive();
        }
    }

    @Nested
    @DisplayName("isScratchFilled")
    class IsScratchFilled {

        @Test
        @Tag("read")
        @DisplayName("should return true when provider has scratch pad entry")
        void shouldReturnTrue_whenProviderHasScratchPad() {
            String providerNo1 = "111";
            String providerNo2 = "222";

            ScratchPad sp1 = new ScratchPad();
            EntityDataGenerator.generateTestDataForModelClass(sp1);
            sp1.setProviderNo(providerNo1);
            dao.persist(sp1);

            ScratchPad sp2 = new ScratchPad();
            EntityDataGenerator.generateTestDataForModelClass(sp2);
            sp2.setProviderNo(providerNo2);
            dao.persist(sp2);

            boolean result = dao.isScratchFilled(providerNo1);

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("findByProviderNo")
    class FindByProviderNo {

        @Test
        @Tag("read")
        @DisplayName("should return scratch pad for the given provider")
        void shouldReturnScratchPad_whenProviderNoMatches() {
            String providerNo1 = "111";
            String providerNo2 = "222";

            ScratchPad sp1 = new ScratchPad();
            EntityDataGenerator.generateTestDataForModelClass(sp1);
            sp1.setProviderNo(providerNo1);
            dao.persist(sp1);

            ScratchPad sp2 = new ScratchPad();
            EntityDataGenerator.generateTestDataForModelClass(sp2);
            sp2.setProviderNo(providerNo2);
            dao.persist(sp2);

            ScratchPad result = dao.findByProviderNo(providerNo1);

            assertThat(result).isEqualTo(sp1);
        }
    }
}
