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
import io.github.carlos_emr.carlos.commn.model.CtlDocClass;
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
 * Integration tests for {@link CtlDocClassDao} covering document class
 * report class and subclass queries.
 *
 * <p>Migrated from legacy {@code CtlDocClassDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see CtlDocClassDao
 */
@DisplayName("CtlDocClassDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("document")
@Transactional
public class CtlDocClassDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private CtlDocClassDao ctlDocClassDao;

    private int nextId = 90001;

    private CtlDocClass createDocClass(String reportClass, String subClass) {
        CtlDocClass dc = new CtlDocClass();
        dc.setId(nextId++);
        dc.setReportClass(reportClass);
        dc.setSubClass(subClass);
        ctlDocClassDao.persist(dc);
        return dc;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist document class")
        void shouldPersistDocClass_whenValidDataProvided() {
            CtlDocClass dc = createDocClass("Clinical", "Lab");
            CtlDocClass found = ctlDocClassDao.find(dc.getId());
            assertThat(found).isNotNull();
            assertThat(found.getReportClass()).isEqualTo("Clinical");
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @BeforeEach
        void setUp() {
            createDocClass("Clinical", "Lab");
            createDocClass("Clinical", "Imaging");
            createDocClass("Clinical", "Pathology");
            createDocClass("Administrative", "Forms");
            createDocClass("Administrative", "Letters");
        }

        @Test
        @Tag("query")
        @DisplayName("should find unique report classes")
        void shouldFindUniqueReportClasses() {
            List<String> classes = ctlDocClassDao.findUniqueReportClasses();
            assertThat(classes).contains("Clinical", "Administrative");
        }

        @Test
        @Tag("query")
        @DisplayName("should find subclasses by report class")
        void shouldFindSubclasses_byReportClass() {
            List<String> subClasses = ctlDocClassDao.findSubClassesByReportClass("Clinical");
            assertThat(subClasses).containsExactlyInAnyOrder("Lab", "Imaging", "Pathology");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty for non-existent report class")
        void shouldReturnEmpty_whenReportClassNotFound() {
            List<String> subClasses = ctlDocClassDao.findSubClassesByReportClass("Nonexistent");
            assertThat(subClasses).isEmpty();
        }
    }
}
