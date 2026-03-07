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
import io.github.carlos_emr.carlos.commn.model.MeasurementCSSLocation;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MeasurementCSSLocationDao} with full method coverage matching legacy tests.
 *
 * <p>Migrated from legacy {@code MeasurementCSSLocationDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see MeasurementCSSLocationDao
 */
@DisplayName("MeasurementCSSLocation Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class MeasurementCSSLocationDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private MeasurementCSSLocationDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist measurement CSS location with generated ID")
    void shouldPersistMeasurementCSSLocation_whenValidDataProvided() {
        MeasurementCSSLocation entity = new MeasurementCSSLocation();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);

        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return matching locations when filtered by location string")
    void shouldReturnMatchingLocations_whenFilteredByLocation() {
        String location1 = "alpha", location2 = "bravo";

        MeasurementCSSLocation mCSSL1 = new MeasurementCSSLocation();
        EntityDataGenerator.generateTestDataForModelClass(mCSSL1);
        mCSSL1.setLocation(location2);
        dao.persist(mCSSL1);

        MeasurementCSSLocation mCSSL2 = new MeasurementCSSLocation();
        EntityDataGenerator.generateTestDataForModelClass(mCSSL2);
        mCSSL2.setLocation(location1);
        dao.persist(mCSSL2);

        MeasurementCSSLocation mCSSL3 = new MeasurementCSSLocation();
        EntityDataGenerator.generateTestDataForModelClass(mCSSL3);
        mCSSL3.setLocation(location1);
        dao.persist(mCSSL3);

        MeasurementCSSLocation mCSSL4 = new MeasurementCSSLocation();
        EntityDataGenerator.generateTestDataForModelClass(mCSSL4);
        mCSSL4.setLocation(location2);
        dao.persist(mCSSL4);

        MeasurementCSSLocation mCSSL5 = new MeasurementCSSLocation();
        EntityDataGenerator.generateTestDataForModelClass(mCSSL5);
        mCSSL5.setLocation(location1);
        dao.persist(mCSSL5);

        List<MeasurementCSSLocation> expectedResult = Arrays.asList(mCSSL2, mCSSL3, mCSSL5);
        List<MeasurementCSSLocation> result = dao.findByLocation(location1);

        assertThat(result).hasSize(expectedResult.size());
        for (int i = 0; i < expectedResult.size(); i++) {
            assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
        }
    }
}
