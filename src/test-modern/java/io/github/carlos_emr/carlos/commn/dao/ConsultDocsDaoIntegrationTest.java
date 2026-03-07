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
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
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
 * Integration tests for {@link ConsultDocsDao} covering consultation
 * document attachment CRUD and filtering by request/docType.
 *
 * <p>Migrated from legacy {@code ConsultDocsDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ConsultDocsDao
 */
@DisplayName("ConsultDocsDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("consultation")
@Transactional
public class ConsultDocsDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ConsultDocsDao consultDocsDao;

    private ConsultDocs createConsultDoc(int requestId, int documentNo, String docType, String deleted) {
        ConsultDocs doc = new ConsultDocs();
        doc.setRequestId(requestId);
        doc.setDocumentNo(documentNo);
        doc.setDocType(docType);
        doc.setDeleted(deleted);
        consultDocsDao.persist(doc);
        return doc;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist consult doc with generated ID")
        void shouldPersistConsultDoc_whenValidDataProvided() {
            ConsultDocs doc = createConsultDoc(1001, 2001, "D", null);
            assertThat(doc.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find consult doc by ID")
        void shouldFindConsultDoc_whenValidIdProvided() {
            ConsultDocs saved = createConsultDoc(1002, 2002, "L", null);
            ConsultDocs found = consultDocsDao.find(saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getRequestId()).isEqualTo(1002);
            assertThat(found.getDocType()).isEqualTo("L");
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @BeforeEach
        void setUp() {
            createConsultDoc(3001, 4001, "D", null);
            createConsultDoc(3001, 4002, "D", null);
            createConsultDoc(3001, 4003, "L", null);
            createConsultDoc(3001, 4004, "D", "Y");
            createConsultDoc(3002, 4005, "D", null);
        }

        @Test
        @Tag("query")
        @DisplayName("should find docs by request ID, doc no, and doc type excluding deleted")
        void shouldFindDocs_byRequestIdDocNoDocType() {
            List<ConsultDocs> results = consultDocsDao.findByRequestIdDocNoDocType(3001, 4001, "D");
            assertThat(results).hasSize(1);
        }

        @Test
        @Tag("query")
        @DisplayName("should find docs by request ID and doc type")
        void shouldFindDocs_byRequestIdAndDocType() {
            List<ConsultDocs> results = consultDocsDao.findByRequestIdDocType(3001, "D");
            assertThat(results).hasSize(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should find all docs by request ID")
        void shouldFindDocs_byRequestId() {
            List<ConsultDocs> results = consultDocsDao.findByRequestId(3001);
            assertThat(results).hasSizeGreaterThanOrEqualTo(3);
        }

        @Test
        @Tag("query")
        @DisplayName("should exclude deleted docs from results")
        void shouldExcludeDeletedDocs_fromResults() {
            List<ConsultDocs> results = consultDocsDao.findByRequestIdDocNoDocType(3001, 4004, "D");
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list for non-existent request")
        void shouldReturnEmptyList_whenRequestNotFound() {
            List<ConsultDocs> results = consultDocsDao.findByRequestId(99999);
            assertThat(results).isEmpty();
        }
    }
}
