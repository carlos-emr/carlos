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
import io.github.carlos_emr.carlos.commn.model.ConsultResponseDoc;
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
 * Integration tests for {@link ConsultResponseDocDao} covering consultation
 * response document attachment CRUD and filtering queries.
 *
 * <p>Migrated from legacy {@code ConsultResponseDocDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ConsultResponseDocDao
 */
@DisplayName("ConsultResponseDocDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("consultation")
@Transactional
public class ConsultResponseDocDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ConsultResponseDocDao consultResponseDocDao;

    private ConsultResponseDoc createResponseDoc(int responseId, int documentNo, String docType, String deleted) {
        ConsultResponseDoc doc = new ConsultResponseDoc();
        doc.setResponseId(responseId);
        doc.setDocumentNo(documentNo);
        doc.setDocType(docType);
        doc.setDeleted(deleted);
        consultResponseDocDao.persist(doc);
        return doc;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist consult response doc with generated ID")
        void shouldPersistConsultResponseDoc_whenValidDataProvided() {
            ConsultResponseDoc doc = createResponseDoc(5001, 6001, "D", null);
            assertThat(doc.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find consult response doc by ID")
        void shouldFindConsultResponseDoc_whenValidIdProvided() {
            ConsultResponseDoc saved = createResponseDoc(5002, 6002, "L", null);
            ConsultResponseDoc found = consultResponseDocDao.find(saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getResponseId()).isEqualTo(5002);
            assertThat(found.getDocType()).isEqualTo("L");
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @BeforeEach
        void setUp() {
            createResponseDoc(7001, 8001, "D", null);
            createResponseDoc(7001, 8002, "D", null);
            createResponseDoc(7001, 8003, "L", null);
            createResponseDoc(7001, 8004, "D", "Y");
            createResponseDoc(7002, 8005, "D", null);
        }

        @Test
        @Tag("query")
        @DisplayName("should find response doc by response ID, doc no, and doc type")
        void shouldFindResponseDoc_byResponseIdDocNoDocType() {
            ConsultResponseDoc found = consultResponseDocDao.findByResponseIdDocNoDocType(7001, 8001, "D");
            assertThat(found).isNotNull();
            assertThat(found.getDocumentNo()).isEqualTo(8001);
        }

        @Test
        @Tag("query")
        @DisplayName("should find all response docs by response ID")
        void shouldFindResponseDocs_byResponseId() {
            List<ConsultResponseDoc> results = consultResponseDocDao.findByResponseId(7001);
            assertThat(results).hasSizeGreaterThanOrEqualTo(3);
        }

        @Test
        @Tag("query")
        @DisplayName("should exclude deleted docs from findByResponseIdDocNoDocType")
        void shouldExcludeDeletedDocs_fromSingleResult() {
            ConsultResponseDoc found = consultResponseDocDao.findByResponseIdDocNoDocType(7001, 8004, "D");
            assertThat(found).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should return null for non-existent response-doc combination")
        void shouldReturnNull_whenResponseDocNotFound() {
            ConsultResponseDoc found = consultResponseDocDao.findByResponseIdDocNoDocType(99999, 1, "D");
            assertThat(found).isNull();
        }
    }
}
