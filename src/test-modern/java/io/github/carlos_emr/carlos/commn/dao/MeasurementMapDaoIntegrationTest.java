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
import io.github.carlos_emr.carlos.commn.model.MeasurementMap;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link MeasurementMapDao} covering
 * create, getAllMaps, getMapsByIdent, findByLoincCode,
 * findByLoincCodeAndLabType, findDistinctLabTypes, and findDistinctLoincCodes.
 *
 * <p>Migrated from legacy {@code MeasurementMapDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see MeasurementMapDao
 */
@DisplayName("MeasurementMapDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("measurement")
@Transactional
public class MeasurementMapDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private MeasurementMapDao dao;

    private MeasurementMap createMap() {
        MeasurementMap mm = new MeasurementMap();
        EntityDataGenerator.generateTestDataForModelClass(mm);
        dao.persist(mm);
        return mm;
    }

    private MeasurementMap createMapWithLoincAndLabType(String loincCode, String labType) {
        MeasurementMap mm = new MeasurementMap();
        EntityDataGenerator.generateTestDataForModelClass(mm);
        mm.setLoincCode(loincCode);
        mm.setLabType(labType);
        dao.persist(mm);
        return mm;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist measurement map with generated ID")
        void shouldPersistMeasurementMap_whenValidDataProvided() {
            MeasurementMap entity = new MeasurementMap();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isPositive();
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("read")
        @DisplayName("should return all measurement maps")
        void shouldReturnAllMaps_whenGetAllMapsCalled() {
            MeasurementMap mm1 = createMap();
            MeasurementMap mm2 = createMap();
            MeasurementMap mm3 = createMap();
            MeasurementMap mm4 = createMap();

            List<MeasurementMap> result = dao.getAllMaps();

            assertThat(result).hasSize(4);
            assertThat(result.get(0)).isEqualTo(mm1);
            assertThat(result.get(1)).isEqualTo(mm2);
            assertThat(result.get(2)).isEqualTo(mm3);
            assertThat(result.get(3)).isEqualTo(mm4);
        }

        @Test
        @Tag("query")
        @DisplayName("should find maps by ident code")
        void shouldFindMaps_byIdentCode() {
            MeasurementMap mm1 = new MeasurementMap();
            EntityDataGenerator.generateTestDataForModelClass(mm1);
            mm1.setIdentCode("101");
            dao.persist(mm1);

            MeasurementMap mm2 = new MeasurementMap();
            EntityDataGenerator.generateTestDataForModelClass(mm2);
            mm2.setIdentCode("202");
            dao.persist(mm2);

            MeasurementMap mm3 = new MeasurementMap();
            EntityDataGenerator.generateTestDataForModelClass(mm3);
            mm3.setIdentCode("101");
            dao.persist(mm3);

            List<MeasurementMap> result = dao.getMapsByIdent("101");

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isEqualTo(mm1);
            assertThat(result.get(1)).isEqualTo(mm3);
        }

        @Test
        @Tag("query")
        @DisplayName("should find maps by LOINC code")
        void shouldFindMaps_byLoincCode() {
            MeasurementMap mm1 = new MeasurementMap();
            EntityDataGenerator.generateTestDataForModelClass(mm1);
            mm1.setLoincCode("alpha");
            dao.persist(mm1);

            MeasurementMap mm2 = new MeasurementMap();
            EntityDataGenerator.generateTestDataForModelClass(mm2);
            mm2.setLoincCode("bravo");
            dao.persist(mm2);

            MeasurementMap mm3 = new MeasurementMap();
            EntityDataGenerator.generateTestDataForModelClass(mm3);
            mm3.setLoincCode("alpha");
            dao.persist(mm3);

            List<MeasurementMap> result = dao.findByLoincCode("alpha");

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isEqualTo(mm1);
            assertThat(result.get(1)).isEqualTo(mm3);
        }

        @Test
        @Tag("query")
        @DisplayName("should find maps by LOINC code and lab type")
        void shouldFindMaps_byLoincCodeAndLabType() {
            MeasurementMap mm1 = createMapWithLoincAndLabType("alpha", "sigma");
            createMapWithLoincAndLabType("bravo", "sigma");
            MeasurementMap mm3 = createMapWithLoincAndLabType("alpha", "sigma");
            createMapWithLoincAndLabType("alpha", "charlie");

            List<MeasurementMap> result = dao.findByLoincCodeAndLabType("alpha", "sigma");

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isEqualTo(mm1);
            assertThat(result.get(1)).isEqualTo(mm3);
        }

        @Test
        @Tag("query")
        @DisplayName("should find distinct lab types")
        void shouldReturnDistinctLabTypes() {
            createMapWithLoincAndLabType("a", "sigma");
            createMapWithLoincAndLabType("b", "sigma");
            createMapWithLoincAndLabType("c", "bravo");
            createMapWithLoincAndLabType("d", "charlie");

            List<String> result = dao.findDistinctLabTypes();

            assertThat(result).hasSize(3);
            assertThat(result).containsExactlyInAnyOrder("sigma", "bravo", "charlie");
        }

        @Test
        @Tag("query")
        @DisplayName("should find distinct LOINC codes")
        void shouldReturnDistinctLoincCodes() {
            createMapWithLoincAndLabType("alpha", "a");
            createMapWithLoincAndLabType("bravo", "b");
            createMapWithLoincAndLabType("alpha", "c");

            List<String> result = dao.findDistinctLoincCodes();

            assertThat(result).hasSize(2);
            assertThat(result).containsExactlyInAnyOrder("alpha", "bravo");
        }
    }
}
