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
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
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
 * Integration tests for {@link CtlDocumentDao} covering document
 * control CRUD and module-based lookups.
 *
 * <p>Migrated from legacy {@code CtlDocumentDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see CtlDocumentDao
 */
@DisplayName("CtlDocumentDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("document")
@Transactional
public class CtlDocumentDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private CtlDocumentDao ctlDocumentDao;

    private CtlDocument createCtlDocument(int documentNo, String module, String status) {
        CtlDocument doc = new CtlDocument();
        doc.getId().setDocumentNo(documentNo);
        doc.getId().setModule(module);
        doc.getId().setModuleId(0);
        doc.setStatus(status);
        ctlDocumentDao.persist(doc);
        return doc;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist control document")
        void shouldPersistCtlDocument_whenValidDataProvided() {
            CtlDocument doc = createCtlDocument(10001, "demographic", "A");
            CtlDocument found = ctlDocumentDao.find(doc.getId());
            assertThat(found).isNotNull();
            assertThat(found.getStatus()).isEqualTo("A");
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @BeforeEach
        void setUp() {
            createCtlDocument(20001, "demographic", "A");
            createCtlDocument(20002, "demographic", "A");
            createCtlDocument(20003, "encounter", "A");
        }

        @Test
        @Tag("query")
        @DisplayName("should find documents by document number and module")
        void shouldFindDocuments_byDocumentNoAndModule() {
            List<CtlDocument> results = ctlDocumentDao.findByDocumentNoAndModule(20001, "demographic");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getStatus()).isEqualTo("A");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty for non-existent document-module combination")
        void shouldReturnEmpty_whenDocumentModuleNotFound() {
            List<CtlDocument> results = ctlDocumentDao.findByDocumentNoAndModule(99999, "nonexistent");
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("Ownership lookup (issue #3867)")
    class OwnershipLookup {

        private void link(int documentNo, int demographicNo, String status) {
            CtlDocument doc = new CtlDocument();
            doc.getId().setDocumentNo(documentNo);
            doc.getId().setModule("demographic");
            doc.getId().setModuleId(demographicNo);
            doc.setStatus(status);
            ctlDocumentDao.persist(doc);
        }

        @Test
        @Tag("query")
        @DisplayName("should return only live documents linked to the patient")
        void shouldReturnOwnedLiveDocuments_forDemographic() {
            link(30001, 7001, "A");
            link(30002, 7001, "D");
            link(30003, 7002, "A");
            link(30004, 7001, null);

            List<Integer> owned = ctlDocumentDao.findDocumentNosForDemographic(7001, List.of(30001, 30002, 30003, 30004, 39999));

            assertThat(owned).containsExactlyInAnyOrder(30001, 30004);
        }

        @Test
        @Tag("query")
        @DisplayName("should ignore documents linked through another module")
        void shouldIgnoreNonDemographicModule_forDemographic() {
            CtlDocument doc = new CtlDocument();
            doc.getId().setDocumentNo(30010);
            doc.getId().setModule("provider");
            doc.getId().setModuleId(7001);
            doc.setStatus("A");
            ctlDocumentDao.persist(doc);

            assertThat(ctlDocumentDao.findDocumentNosForDemographic(7001, List.of(30010))).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty without querying for an empty id list")
        void shouldReturnEmpty_forEmptyIdList() {
            assertThat(ctlDocumentDao.findDocumentNosForDemographic(7001, List.of())).isEmpty();
            assertThat(ctlDocumentDao.findDocumentNosForDemographic(null, List.of(30001))).isEmpty();
        }
    }
}
