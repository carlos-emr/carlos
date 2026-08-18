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

import io.github.carlos_emr.carlos.commn.model.ProviderInboxItem;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ProviderInboxRoutingDao} covering persist,
 * getProvidersWithRoutingForDocument, hasProviderBeenLinkedWithDocument,
 * howManyDocumentsLinkedWithAProvider, and findDocumentsLinkedWithProvider.
 *
 * <p>Migrated from legacy {@code ProviderInboxRoutingDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ProviderInboxRoutingDao
 */
@DisplayName("ProviderInboxItem Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class ProviderInboxItemDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ProviderInboxRoutingDao dao;

    private ProviderInboxItem createInboxItem(String providerNo, int labNo, String labType, String status) {
        ProviderInboxItem entity = new ProviderInboxItem();
        entity.setProviderNo(providerNo);
        entity.setLabNo(labNo);
        entity.setLabType(labType);
        entity.setStatus(status);
        entity.setTimestamp(new Date());
        dao.persist(entity);
        return entity;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist provider inbox item with generated ID")
        void shouldPersistProviderInboxItem_whenValidDataProvided() {
            ProviderInboxItem entity = createInboxItem("100001", 1, "DOC", "N");

            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find provider inbox item by ID with correct values")
        void shouldFindProviderInboxItem_whenValidIdProvided() {
            ProviderInboxItem saved = createInboxItem("100002", 2, "HL7", "A");

            ProviderInboxItem found = dao.find(saved.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getProviderNo()).isEqualTo("100002");
            assertThat(found.getLabNo()).isEqualTo(2);
            assertThat(found.getLabType()).isEqualTo("HL7");
            assertThat(found.getStatus()).isEqualTo("A");
        }
    }

    @Nested
    @DisplayName("getProvidersWithRoutingForDocument")
    class GetProvidersWithRoutingForDocument {

        @Test
        @Tag("query")
        @DisplayName("should return providers routed to matching document")
        void shouldReturnProviders_whenDocumentMatches() {
            createInboxItem("200001", 10, "DOC", "N");
            createInboxItem("200002", 10, "DOC", "N");
            createInboxItem("200003", 20, "DOC", "N");

            List<ProviderInboxItem> results = dao.getProvidersWithRoutingForDocument("DOC", 10);

            assertThat(results).hasSize(2);
            assertThat(results).extracting(ProviderInboxItem::getProviderNo)
                    .containsExactlyInAnyOrder("200001", "200002");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no routing exists for document")
        void shouldReturnEmptyList_whenNoRoutingExists() {
            List<ProviderInboxItem> results = dao.getProvidersWithRoutingForDocument("DOC", 99999);

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("hasProviderBeenLinkedWithDocument")
    class HasProviderBeenLinkedWithDocument {

        @Test
        @Tag("query")
        @DisplayName("should return true when provider is linked with document")
        void shouldReturnTrue_whenProviderIsLinked() {
            createInboxItem("300001", 30, "DOC", "N");

            boolean linked = dao.hasProviderBeenLinkedWithDocument("DOC", 30, "300001");

            assertThat(linked).isTrue();
        }

        @Test
        @Tag("query")
        @DisplayName("should return false when provider is not linked with document")
        void shouldReturnFalse_whenProviderIsNotLinked() {
            createInboxItem("300001", 30, "DOC", "N");

            boolean linked = dao.hasProviderBeenLinkedWithDocument("DOC", 30, "300002");

            assertThat(linked).isFalse();
        }
    }

    @Nested
    @DisplayName("howManyDocumentsLinkedWithAProvider")
    class HowManyDocumentsLinkedWithAProvider {

        @Test
        @Tag("query")
        @DisplayName("should return count of documents linked with provider")
        void shouldReturnCount_whenProviderHasDocuments() {
            createInboxItem("400001", 40, "DOC", "N");
            createInboxItem("400001", 41, "HL7", "N");
            createInboxItem("400001", 42, "DOC", "A");
            createInboxItem("400002", 43, "DOC", "N");

            int count = dao.howManyDocumentsLinkedWithAProvider("400001");

            assertThat(count).isEqualTo(3);
        }

        @Test
        @Tag("query")
        @DisplayName("should return zero when provider has no documents")
        void shouldReturnZero_whenProviderHasNoDocuments() {
            int count = dao.howManyDocumentsLinkedWithAProvider("999999");

            assertThat(count).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("findDocumentsLinkedWithProvider")
    class FindDocumentsLinkedWithProvider {

        @Test
        @Tag("query")
        @DisplayName("should return documents matching doc type, doc ID, and provider")
        void shouldReturnDocuments_whenAllParametersMatch() {
            ProviderInboxItem matching = createInboxItem("500001", 50, "DOC", "N");
            createInboxItem("500001", 51, "DOC", "N");
            createInboxItem("500002", 50, "DOC", "N");

            List<ProviderInboxItem> results = dao.findDocumentsLinkedWithProvider("DOC", 50, "500001");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(matching.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no matching documents")
        void shouldReturnEmptyList_whenNoMatchingDocuments() {
            List<ProviderInboxItem> results = dao.findDocumentsLinkedWithProvider("DOC", 99999, "999999");

            assertThat(results).isEmpty();
        }
    }
}
