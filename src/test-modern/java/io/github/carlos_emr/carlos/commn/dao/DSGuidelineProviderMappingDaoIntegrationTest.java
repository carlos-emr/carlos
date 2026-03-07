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
import io.github.carlos_emr.carlos.decisionSupport.model.DSGuidelineProviderMapping;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link DSGuidelineProviderMappingDao} covering create,
 * getMappingsByProvider, and mappingExists.
 *
 * <p>Migrated from legacy {@code DSGuidelineProviderMappingDaoTest}
 * (JUnit 4 / DaoTestFixtures) with exact same test logic and assertions.</p>
 *
 * @since 2026-03-07
 * @see DSGuidelineProviderMappingDao
 */
@DisplayName("DSGuidelineProviderMappingDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("clinical")
@Transactional
public class DSGuidelineProviderMappingDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private DSGuidelineProviderMappingDao dao;

    @Nested
    @DisplayName("create tests")
    @Tag("create")
    class Create {

        @Test
        @DisplayName("should persist entity with generated id")
        void shouldPersistEntity_withGeneratedId() {
            DSGuidelineProviderMapping dsGPM = new DSGuidelineProviderMapping();
            EntityDataGenerator.generateTestDataForModelClass(dsGPM);
            dao.persist(dsGPM);
            assertThat(dsGPM.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("getMappingsByProvider tests")
    @Tag("read")
    class GetMappingsByProvider {

        @Test
        @DisplayName("should return mappings for matching provider number")
        void shouldReturnMappings_forMatchingProviderNo() {
            String providerNo1 = "101";
            String providerNo2 = "202";

            DSGuidelineProviderMapping dsGPM1 = new DSGuidelineProviderMapping();
            EntityDataGenerator.generateTestDataForModelClass(dsGPM1);
            dsGPM1.setProviderNo(providerNo1);
            dao.persist(dsGPM1);

            DSGuidelineProviderMapping dsGPM2 = new DSGuidelineProviderMapping();
            EntityDataGenerator.generateTestDataForModelClass(dsGPM2);
            dsGPM2.setProviderNo(providerNo2);
            dao.persist(dsGPM2);

            DSGuidelineProviderMapping dsGPM3 = new DSGuidelineProviderMapping();
            EntityDataGenerator.generateTestDataForModelClass(dsGPM3);
            dsGPM3.setProviderNo(providerNo1);
            dao.persist(dsGPM3);
            hibernateTemplate.flush();

            List<DSGuidelineProviderMapping> expectedResult = Arrays.asList(dsGPM1, dsGPM3);
            List<DSGuidelineProviderMapping> result = dao.getMappingsByProvider(providerNo1);

            assertThat(result).hasSameSizeAs(expectedResult);
            for (int i = 0; i < expectedResult.size(); i++) {
                assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
            }
        }
    }

    @Nested
    @DisplayName("mappingExists tests")
    @Tag("read")
    class MappingExists {

        @Test
        @DisplayName("should return true when mapping exists with matching provider and guideline")
        void shouldReturnTrue_whenMappingExists() {
            String providerNo1 = "101";
            String providerNo2 = "202";
            String guidelineUUID1 = "alpha";
            String guidelineUUID2 = "bravo";

            DSGuidelineProviderMapping dsGPM1 = new DSGuidelineProviderMapping();
            EntityDataGenerator.generateTestDataForModelClass(dsGPM1);
            dsGPM1.setProviderNo(providerNo1);
            dsGPM1.setGuidelineUUID(guidelineUUID1);
            dao.persist(dsGPM1);

            DSGuidelineProviderMapping dsGPM2 = new DSGuidelineProviderMapping();
            EntityDataGenerator.generateTestDataForModelClass(dsGPM2);
            dsGPM2.setProviderNo(providerNo2);
            dsGPM2.setGuidelineUUID(guidelineUUID2);
            dao.persist(dsGPM2);

            DSGuidelineProviderMapping dsGPM3 = new DSGuidelineProviderMapping();
            EntityDataGenerator.generateTestDataForModelClass(dsGPM3);
            dsGPM3.setProviderNo(providerNo1);
            dsGPM3.setGuidelineUUID(guidelineUUID2);
            dao.persist(dsGPM3);
            hibernateTemplate.flush();

            assertThat(dao.mappingExists(dsGPM1)).isTrue();
        }
    }
}
