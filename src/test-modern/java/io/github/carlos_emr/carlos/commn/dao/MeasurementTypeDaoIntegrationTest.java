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
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link MeasurementTypeDao} covering measurement
 * type CRUD, type-based lookups, and compound queries.
 *
 * <p>Migrated from legacy {@code MeasurementTypeDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see MeasurementTypeDao
 */
@DisplayName("MeasurementTypeDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("measurement")
@Transactional
public class MeasurementTypeDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private MeasurementTypeDao measurementTypeDao;

    private MeasurementType createType(String type, String typeDisplayName, String measuringInstruction) {
        MeasurementType mt = new MeasurementType();
        mt.setType(type);
        mt.setTypeDisplayName(typeDisplayName);
        mt.setMeasuringInstruction(measuringInstruction);
        mt.setTypeDescription("Test description for " + type);
        measurementTypeDao.persist(mt);
        return mt;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist measurement type with generated ID")
        void shouldPersistMeasurementType_whenValidDataProvided() {
            MeasurementType mt = createType("BP", "Blood Pressure", "Sitting, left arm");
            assertThat(mt.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find measurement type by ID")
        void shouldFindMeasurementType_whenValidIdProvided() {
            MeasurementType saved = createType("HR", "Heart Rate", "At rest");
            MeasurementType found = measurementTypeDao.find(saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getType()).isEqualTo("HR");
            assertThat(found.getTypeDisplayName()).isEqualTo("Heart Rate");
        }
    }

    @Nested
    @DisplayName("Type-based queries")
    class TypeBasedQueries {

        @BeforeEach
        void setUp() {
            createType("WT", "Weight", "Standing, no shoes");
            createType("HT", "Height", "Standing, no shoes");
            createType("WT", "Weight (Alt)", "Sitting");
            createType("TEMP", "Temperature", "Oral");
        }

        @Test
        @Tag("query")
        @DisplayName("should find measurement types by type code")
        void shouldFindMeasurementTypes_byType() {
            List<MeasurementType> results = measurementTypeDao.findByType("WT");
            assertThat(results).hasSize(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should find all measurement types")
        void shouldFindAllMeasurementTypes() {
            List<MeasurementType> all = measurementTypeDao.findAll();
            assertThat(all).hasSizeGreaterThanOrEqualTo(4);
        }

        @Test
        @Tag("query")
        @DisplayName("should find by type display name")
        void shouldFindMeasurementTypes_byTypeDisplayName() {
            List<MeasurementType> results = measurementTypeDao.findByTypeDisplayName("Temperature");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getType()).isEqualTo("TEMP");
        }

        @Test
        @Tag("query")
        @DisplayName("should find by type and measuring instruction")
        void shouldFindMeasurementTypes_byTypeAndInstruction() {
            List<MeasurementType> results = measurementTypeDao.findByTypeAndMeasuringInstruction("WT", "Sitting");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getTypeDisplayName()).isEqualTo("Weight (Alt)");
        }

        @Test
        @Tag("query")
        @DisplayName("should find by measuring instruction and type display name")
        void shouldFindMeasurementTypes_byInstructionAndDisplayName() {
            List<MeasurementType> results = measurementTypeDao.findByMeasuringInstructionAndTypeDisplayName(
                    "Standing, no shoes", "Weight");
            assertThat(results).hasSize(1);
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list for non-existent type")
        void shouldReturnEmptyList_whenTypeNotFound() {
            List<MeasurementType> results = measurementTypeDao.findByType("NONEXISTENT");
            assertThat(results).isEmpty();
        }
    }
}
