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
package io.github.carlos_emr.carlos.hospitalReportManager.dao;

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
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
 * Integration tests for {@link HRMDocumentDao} covering CRUD, hash-based
 * lookups, key-based queries, and paginated retrieval.
 *
 * <p>Migrated from legacy {@code HRMDocumentDaoTest} (JUnit 4 / DaoTestFixtures)
 * with expanded coverage for hash-based dedup and relationship queries.</p>
 *
 * @since 2026-03-07
 * @see HRMDocumentDao
 */
@DisplayName("HRMDocumentDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("hrm")
@Transactional
public class HRMDocumentDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private HRMDocumentDao hrmDocumentDao;

    private HRMDocument createDocument(String reportHash, String sourceFacility) {
        HRMDocument doc = new HRMDocument();
        doc.setReportHash(reportHash);
        doc.setSourceFacility(sourceFacility);
        doc.setSourceFacilityReportNo("RPT-" + System.nanoTime());
        doc.setTimeReceived(new Date());
        doc.setReportDate(new Date());
        doc.setReportStatus("A");
        doc.setReportType("Test Report");
        hrmDocumentDao.persist(doc);
        return doc;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist HRM document with generated ID")
        void shouldPersistDocument_whenValidDataProvided() {
            HRMDocument doc = createDocument("hash123", "TestHospital");
            assertThat(doc.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find HRM document by ID")
        void shouldFindDocument_whenValidIdProvided() {
            HRMDocument saved = createDocument("hash456", "Hospital A");
            List<HRMDocument> results = hrmDocumentDao.findById(saved.getId());
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getSourceFacility()).isEqualTo("Hospital A");
        }
    }

    @Nested
    @DisplayName("findAll (paginated)")
    class FindAllPaginated {

        @Test
        @Tag("query")
        @DisplayName("should return paginated results")
        void shouldReturnPaginatedResults_withOffsetAndLimit() {
            for (int i = 0; i < 5; i++) {
                createDocument("hash-page-" + i, "Hospital");
            }
            List<HRMDocument> results = hrmDocumentDao.findAll(0, 3);
            assertThat(results).hasSize(3);
        }

        @Test
        @Tag("query")
        @DisplayName("should return all documents without pagination")
        void shouldReturnAllDocuments_withoutPagination() {
            createDocument("hash-all-1", "Hospital");
            createDocument("hash-all-2", "Hospital");
            List<HRMDocument> results = hrmDocumentDao.findAll();
            assertThat(results).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("findByHash (deduplication)")
    class FindByHash {

        @Test
        @Tag("query")
        @DisplayName("should find document IDs by report hash")
        void shouldFindDocumentIds_byReportHash() {
            String uniqueHash = "UNIQUE-HASH-" + System.nanoTime();
            HRMDocument doc = createDocument(uniqueHash, "Hospital");
            hibernateTemplate.flush();

            List<Integer> ids = hrmDocumentDao.findByHash(uniqueHash);
            assertThat(ids).contains(doc.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty for non-existent hash")
        void shouldReturnEmpty_forNonExistentHash() {
            List<Integer> ids = hrmDocumentDao.findByHash("NONEXISTENT-HASH");
            assertThat(ids).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByKey (facility + report number + delivery)")
    class FindByKey {

        @Test
        @Tag("query")
        @DisplayName("should find documents by composite key")
        void shouldFindDocuments_byCompositeKey() {
            HRMDocument doc = createDocument("hash-key-1", "FacilityX");
            doc.setSourceFacilityReportNo("RPT-001");
            doc.setDeliverToUserId("DOC001");
            hrmDocumentDao.merge(doc);
            hibernateTemplate.flush();

            List<HRMDocument> results = hrmDocumentDao.findByKey("FacilityX", "RPT-001", "DOC001");
            assertThat(results).isNotEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when key does not match")
        void shouldReturnEmpty_whenKeyDoesNotMatch() {
            List<HRMDocument> results = hrmDocumentDao.findByKey("NONE", "NONE", "NONE");
            assertThat(results).isEmpty();
        }
    }
}
