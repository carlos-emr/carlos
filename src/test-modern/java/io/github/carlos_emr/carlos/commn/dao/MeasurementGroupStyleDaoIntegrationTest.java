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
import io.github.carlos_emr.carlos.commn.model.MeasurementGroupStyle;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MeasurementGroupStyleDao} covering
 * findAll, findByGroupName, and findByCssId.
 *
 * <p>Migrated from legacy {@code MeasurementGroupStyleDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see MeasurementGroupStyleDao
 */
@DisplayName("MeasurementGroupStyle Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("measurement")
@Transactional
public class MeasurementGroupStyleDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private MeasurementGroupStyleDao dao;

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @Disabled("Skipping until issue is resolved - mirrors legacy @Ignore")
        @Tag("read")
        @DisplayName("should return all persisted measurement group styles")
        void shouldReturnAllStyles_whenMultipleExist() {
            MeasurementGroupStyle mgs1 = new MeasurementGroupStyle();
            EntityDataGenerator.generateTestDataForModelClass(mgs1);
            dao.persist(mgs1);

            MeasurementGroupStyle mgs2 = new MeasurementGroupStyle();
            EntityDataGenerator.generateTestDataForModelClass(mgs2);
            dao.persist(mgs2);

            MeasurementGroupStyle mgs3 = new MeasurementGroupStyle();
            EntityDataGenerator.generateTestDataForModelClass(mgs3);
            dao.persist(mgs3);

            List<MeasurementGroupStyle> result = dao.findAll();

            assertThat(result).hasSize(3);
            assertThat(result).containsExactly(mgs1, mgs2, mgs3);
        }
    }

    @Nested
    @DisplayName("findByGroupName")
    class FindByGroupName {

        @Test
        @Tag("search")
        @DisplayName("should return styles matching the given group name")
        void shouldReturnStyles_whenGroupNameMatches() {
            String groupName1 = "alpha";
            String groupName2 = "bravo";

            MeasurementGroupStyle mgs1 = new MeasurementGroupStyle();
            EntityDataGenerator.generateTestDataForModelClass(mgs1);
            mgs1.setGroupName(groupName1);
            dao.persist(mgs1);

            MeasurementGroupStyle mgs2 = new MeasurementGroupStyle();
            EntityDataGenerator.generateTestDataForModelClass(mgs2);
            mgs2.setGroupName(groupName1);
            dao.persist(mgs2);

            MeasurementGroupStyle mgs3 = new MeasurementGroupStyle();
            EntityDataGenerator.generateTestDataForModelClass(mgs3);
            mgs3.setGroupName(groupName2);
            dao.persist(mgs3);

            MeasurementGroupStyle mgs4 = new MeasurementGroupStyle();
            EntityDataGenerator.generateTestDataForModelClass(mgs4);
            mgs4.setGroupName(groupName1);
            dao.persist(mgs4);

            List<MeasurementGroupStyle> result = dao.findByGroupName(groupName1);

            assertThat(result).hasSize(3);
            assertThat(result).containsExactly(mgs1, mgs2, mgs4);
        }
    }

    @Nested
    @DisplayName("findByCssId")
    class FindByCssId {

        @Test
        @Tag("search")
        @DisplayName("should return styles matching the given CSS ID")
        void shouldReturnStyles_whenCssIdMatches() {
            int cssId1 = 101;
            int cssId2 = 202;

            MeasurementGroupStyle mgs1 = new MeasurementGroupStyle();
            EntityDataGenerator.generateTestDataForModelClass(mgs1);
            mgs1.setCssId(cssId1);
            dao.persist(mgs1);

            MeasurementGroupStyle mgs2 = new MeasurementGroupStyle();
            EntityDataGenerator.generateTestDataForModelClass(mgs2);
            mgs2.setCssId(cssId1);
            dao.persist(mgs2);

            MeasurementGroupStyle mgs3 = new MeasurementGroupStyle();
            EntityDataGenerator.generateTestDataForModelClass(mgs3);
            mgs3.setCssId(cssId2);
            dao.persist(mgs3);

            MeasurementGroupStyle mgs4 = new MeasurementGroupStyle();
            EntityDataGenerator.generateTestDataForModelClass(mgs4);
            mgs4.setCssId(cssId1);
            dao.persist(mgs4);

            List<MeasurementGroupStyle> result = dao.findByCssId(cssId1);

            assertThat(result).hasSize(3);
            assertThat(result).containsExactly(mgs1, mgs2, mgs4);
        }
    }
}
