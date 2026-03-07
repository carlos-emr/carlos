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
import io.github.carlos_emr.carlos.commn.model.CtlDocType;
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
 * Integration tests for {@link CtlDocTypeDao} covering document type
 * CRUD, status/module filtering, and module listing.
 *
 * <p>Migrated from legacy {@code CtlDocTypeDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see CtlDocTypeDao
 */
@DisplayName("CtlDocTypeDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("document")
@Transactional
public class CtlDocTypeDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private CtlDocTypeDao ctlDocTypeDao;

    private CtlDocType createDocType(String docType, String module, String status) {
        CtlDocType dt = new CtlDocType();
        dt.setDocType(docType);
        dt.setModule(module);
        dt.setStatus(status);
        ctlDocTypeDao.persist(dt);
        return dt;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist document type with generated ID")
        void shouldPersistDocType_whenValidDataProvided() {
            CtlDocType dt = createDocType("Lab Report", "labs", "A");
            assertThat(dt.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find document type by ID")
        void shouldFindDocType_whenValidIdProvided() {
            CtlDocType saved = createDocType("Prescription", "rx", "A");
            CtlDocType found = ctlDocTypeDao.find(saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getDocType()).isEqualTo("Prescription");
            assertThat(found.getModule()).isEqualTo("rx");
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @BeforeEach
        void setUp() {
            createDocType("Consult Letter", "consultation", "A");
            createDocType("Referral Form", "consultation", "A");
            createDocType("Archived Form", "consultation", "D");
            createDocType("Lab Result", "labs", "A");
            createDocType("Old Lab", "labs", "D");
        }

        @Test
        @Tag("query")
        @DisplayName("should find doc types by status and module")
        void shouldFindDocTypes_byStatusAndModule() {
            List<CtlDocType> results = ctlDocTypeDao.findByStatusAndModule(
                    new String[]{"A"}, "consultation");
            assertThat(results).hasSize(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should find doc types by multiple statuses")
        void shouldFindDocTypes_byMultipleStatuses() {
            List<CtlDocType> results = ctlDocTypeDao.findByStatusAndModule(
                    new String[]{"A", "D"}, "consultation");
            assertThat(results).hasSize(3);
        }

        @Test
        @Tag("query")
        @DisplayName("should find doc types by doc type and module")
        void shouldFindDocTypes_byDocTypeAndModule() {
            List<CtlDocType> results = ctlDocTypeDao.findByDocTypeAndModule("Lab Result", "labs");
            assertThat(results).hasSize(1);
        }

        @Test
        @Tag("query")
        @DisplayName("should find all modules")
        void shouldFindAllModules() {
            List<String> modules = ctlDocTypeDao.findModules();
            assertThat(modules).contains("consultation", "labs");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty for non-existent module")
        void shouldReturnEmpty_whenModuleNotFound() {
            List<CtlDocType> results = ctlDocTypeDao.findByStatusAndModule(
                    new String[]{"A"}, "nonexistent");
            assertThat(results).isEmpty();
        }
    }
}
