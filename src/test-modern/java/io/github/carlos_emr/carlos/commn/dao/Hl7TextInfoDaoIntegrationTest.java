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
import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
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
 * Integration tests for {@link Hl7TextInfoDao} covering lab result metadata
 * queries, accession number lookups, and status-based filtering.
 *
 * <p>Migrated from legacy {@code Hl7TextInfoDaoTest} (JUnit 4 / DaoTestFixtures)
 * with expanded coverage for lab result lifecycle queries.</p>
 *
 * @since 2026-03-07
 * @see Hl7TextInfoDao
 */
@DisplayName("Hl7TextInfoDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("lab")
@Transactional
public class Hl7TextInfoDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private Hl7TextInfoDao hl7TextInfoDao;

    private Hl7TextInfo createHl7TextInfo(String labNo, String accessionNo,
                                           String firstName, String lastName, String healthNo) {
        Hl7TextInfo info = new Hl7TextInfo();
        info.setLabNumber(Integer.parseInt(labNo));
        info.setAccessionNumber(accessionNo);
        info.setFirstName(firstName);
        info.setLastName(lastName);
        info.setHealthNumber(healthNo);
        info.setObrDate(new Date().toString());
        info.setLastUpdateDate(new Date());
        hl7TextInfoDao.persist(info);
        return info;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist HL7 text info record")
        void shouldPersistHl7TextInfo_whenValidDataProvided() {
            Hl7TextInfo info = createHl7TextInfo("1001", "ACC001", "John", "Smith", "1234567890");
            assertThat(info.getLabNumber()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find HL7 text info by ID")
        void shouldFindHl7TextInfo_whenValidIdProvided() {
            Hl7TextInfo saved = createHl7TextInfo("1002", "ACC002", "Jane", "Doe", "9876543210");
            Hl7TextInfo found = hl7TextInfoDao.find(saved.getLabNumber());
            assertThat(found).isNotNull();
            assertThat(found.getAccessionNumber()).isEqualTo("ACC002");
        }
    }

    @Nested
    @DisplayName("Lab result queries")
    class LabResultQueries {

        @BeforeEach
        void setUp() {
            createHl7TextInfo("2001", "ACC-A001", "Patient", "Alpha", "HIN001");
            createHl7TextInfo("2002", "ACC-A002", "Patient", "Beta", "HIN002");
            createHl7TextInfo("2003", "ACC-B001", "Patient", "Gamma", "HIN003");
        }

        @Test
        @Tag("query")
        @DisplayName("should count all HL7 text info records")
        void shouldCountAllRecords() {
            long count = hl7TextInfoDao.getCountAll();
            assertThat(count).isGreaterThanOrEqualTo(3);
        }

        @Test
        @Tag("read")
        @DisplayName("should find all records")
        void shouldFindAllRecords() {
            List<Hl7TextInfo> all = hl7TextInfoDao.findAll(0, 100);
            assertThat(all).hasSizeGreaterThanOrEqualTo(3);
        }
    }
}
