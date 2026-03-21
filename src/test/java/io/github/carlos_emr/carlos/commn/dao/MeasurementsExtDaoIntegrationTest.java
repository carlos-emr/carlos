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
import io.github.carlos_emr.carlos.commn.model.MeasurementsExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link MeasurementsExtDao} covering measurement
 * extension CRUD, measurement-ID-based lookups, and key-value queries.
 *
 * <p>Migrated from legacy {@code MeasurementsExtDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see MeasurementsExtDao
 */
@DisplayName("MeasurementsExtDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("measurement")
@Transactional
public class MeasurementsExtDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private MeasurementsExtDao measurementsExtDao;

    private MeasurementsExt createExt(int measurementId, String keyVal, String val) {
        MeasurementsExt ext = new MeasurementsExt();
        ext.setMeasurementId(measurementId);
        ext.setKeyVal(keyVal);
        ext.setVal(val);
        measurementsExtDao.persist(ext);
        return ext;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist measurement extension with generated ID")
        void shouldPersistMeasurementsExt_whenValidDataProvided() {
            MeasurementsExt ext = createExt(5001, "lab_no", "LAB-001");
            assertThat(ext.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find measurement extension by ID")
        void shouldFindMeasurementsExt_whenValidIdProvided() {
            MeasurementsExt saved = createExt(5002, "lab_no", "LAB-002");
            MeasurementsExt found = measurementsExtDao.find(saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getKeyVal()).isEqualTo("lab_no");
            assertThat(found.getVal()).isEqualTo("LAB-002");
        }
    }

    @Nested
    @DisplayName("Query by measurement ID")
    class QueryByMeasurementId {

        @BeforeEach
        void setUp() {
            createExt(6001, "lab_no", "LAB-100");
            createExt(6001, "test_name", "CBC");
            createExt(6001, "abnormal", "Y");
            createExt(6002, "lab_no", "LAB-200");
        }

        @Test
        @Tag("query")
        @DisplayName("should find extensions by measurement ID")
        void shouldFindExtensions_byMeasurementId() {
            List<MeasurementsExt> results = measurementsExtDao.getMeasurementsExtByMeasurementId(6001);
            assertThat(results).hasSize(3);
        }

        @Test
        @Tag("query")
        @DisplayName("should find extension by measurement ID and key")
        void shouldFindExtension_byMeasurementIdAndKey() {
            MeasurementsExt result = measurementsExtDao.getMeasurementsExtByMeasurementIdAndKeyVal(6001, "test_name");
            assertThat(result).isNotNull();
            assertThat(result.getVal()).isEqualTo("CBC");
        }

        @Test
        @Tag("query")
        @DisplayName("should get extensions map by measurement ID")
        void shouldGetExtensionsMap_byMeasurementId() {
            HashMap<String, MeasurementsExt> map = measurementsExtDao.getMeasurementsExtMapByMeasurementId(6001);
            assertThat(map).hasSize(3);
            assertThat(map).containsKey("lab_no");
            assertThat(map).containsKey("test_name");
            assertThat(map).containsKey("abnormal");
        }
    }

    @Nested
    @DisplayName("Key-value queries")
    class KeyValueQueries {

        @BeforeEach
        void setUp() {
            createExt(7001, "identifier", "ABC-123");
            createExt(7002, "identifier", "DEF-456");
            createExt(7003, "identifier", "ABC-123");
        }

        @Test
        @Tag("query")
        @DisplayName("should find measurement ID by key and value")
        void shouldFindMeasurementId_byKeyAndValue() {
            Integer id = measurementsExtDao.getMeasurementIdByKeyValue("identifier", "ABC-123");
            assertThat(id).isEqualTo(7001);
        }

        @Test
        @Tag("query")
        @DisplayName("should find extensions by key and value")
        void shouldFindExtensions_byKeyAndValue() {
            List<MeasurementsExt> results = measurementsExtDao.findByKeyValue("identifier", "ABC-123");
            assertThat(results).hasSize(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should return null for non-existent key-value")
        void shouldReturnNull_whenKeyValueNotFound() {
            Integer id = measurementsExtDao.getMeasurementIdByKeyValue("nonexistent", "value");
            assertThat(id).isNull();
        }
    }
}
