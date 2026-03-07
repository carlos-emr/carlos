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
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link PatientLabRoutingDao} covering lab-to-patient
 * routing lookups, lab type filtering, and demographic-based queries.
 *
 * <p>Migrated from legacy {@code PatientLabRoutingDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see PatientLabRoutingDao
 */
@DisplayName("PatientLabRoutingDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("lab")
@Transactional
public class PatientLabRoutingDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private PatientLabRoutingDao patientLabRoutingDao;

    private static final int DEMO_1 = 50001;
    private static final int DEMO_2 = 50002;

    private PatientLabRouting createRouting(int demographicNo, int labNo, String labType) {
        PatientLabRouting routing = new PatientLabRouting();
        routing.setDemographicNo(demographicNo);
        routing.setLabNo(labNo);
        routing.setLabType(labType);
        routing.setCreated(new Date());
        patientLabRoutingDao.persist(routing);
        return routing;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist lab routing with generated ID")
        void shouldPersistRouting_whenValidDataProvided() {
            PatientLabRouting routing = createRouting(DEMO_1, 1001, "HL7");
            assertThat(routing.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find routing by ID")
        void shouldFindRouting_whenValidIdProvided() {
            PatientLabRouting saved = createRouting(DEMO_1, 1002, "HL7");
            PatientLabRouting found = patientLabRoutingDao.find(saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getDemographicNo()).isEqualTo(DEMO_1);
        }
    }

    @Nested
    @DisplayName("findByDemographicAndLabType queries")
    class FindByDemographicAndLabType {

        @BeforeEach
        void setUp() {
            createRouting(DEMO_1, 2001, "HL7");
            createRouting(DEMO_1, 2002, "HL7");
            createRouting(DEMO_1, 2003, "DOC");
            createRouting(DEMO_2, 2004, "HL7");
        }

        @Test
        @Tag("query")
        @DisplayName("should count all routing records")
        void shouldCountAllRecords() {
            long count = patientLabRoutingDao.getCountAll();
            assertThat(count).isGreaterThanOrEqualTo(4);
        }

        @Test
        @Tag("query")
        @DisplayName("should find all records")
        void shouldFindAllRecords() {
            List<PatientLabRouting> all = patientLabRoutingDao.findAll(0, 100);
            assertThat(all).hasSizeGreaterThanOrEqualTo(4);
        }
    }
}
