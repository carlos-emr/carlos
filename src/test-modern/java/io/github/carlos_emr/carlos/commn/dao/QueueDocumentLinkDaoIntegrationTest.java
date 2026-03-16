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

import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.QueueDocumentLink;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link QueueDocumentLinkDao} covering
 * create, getQueueDocLinks, getActiveQueueDocLink, getQueueFromDocument,
 * getDocumentFromQueue, and hasQueueBeenLinkedWithDocument.
 *
 * <p>Migrated from legacy {@code QueueDocumentLinkDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see QueueDocumentLinkDao
 */
@DisplayName("QueueDocumentLinkDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("admin")
@Transactional
public class QueueDocumentLinkDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private QueueDocumentLinkDao dao;

    private QueueDocumentLink createLink() throws Exception {
        QueueDocumentLink qdl = new QueueDocumentLink();
        EntityDataGenerator.generateTestDataForModelClass(qdl);
        dao.persist(qdl);
        return qdl;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist queue document link with generated ID")
        void shouldPersistQueueDocumentLink_whenValidDataProvided() throws Exception {
            QueueDocumentLink entity = new QueueDocumentLink();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isPositive();
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("read")
        @DisplayName("should return all queue document links")
        void shouldReturnAllLinks_whenGetQueueDocLinksCalled() throws Exception {
            QueueDocumentLink qdl1 = createLink();
            QueueDocumentLink qdl2 = createLink();
            QueueDocumentLink qdl3 = createLink();
            QueueDocumentLink qdl4 = createLink();

            List<QueueDocumentLink> result = dao.getQueueDocLinks();

            assertThat(result).hasSize(4);
            assertThat(result.get(0)).isEqualTo(qdl1);
            assertThat(result.get(1)).isEqualTo(qdl2);
            assertThat(result.get(2)).isEqualTo(qdl3);
            assertThat(result.get(3)).isEqualTo(qdl4);
        }

        @Test
        @Tag("query")
        @DisplayName("should return only active queue document links")
        void shouldReturnActiveLinks_whenGetActiveQueueDocLinkCalled() throws Exception {
            QueueDocumentLink qdl1 = new QueueDocumentLink();
            EntityDataGenerator.generateTestDataForModelClass(qdl1);
            qdl1.setStatus("A");
            dao.persist(qdl1);

            QueueDocumentLink qdl2 = new QueueDocumentLink();
            EntityDataGenerator.generateTestDataForModelClass(qdl2);
            qdl2.setStatus("N");
            dao.persist(qdl2);

            QueueDocumentLink qdl3 = new QueueDocumentLink();
            EntityDataGenerator.generateTestDataForModelClass(qdl3);
            qdl3.setStatus("A");
            dao.persist(qdl3);

            QueueDocumentLink qdl4 = new QueueDocumentLink();
            EntityDataGenerator.generateTestDataForModelClass(qdl4);
            qdl4.setStatus("A");
            dao.persist(qdl4);

            List<QueueDocumentLink> result = dao.getActiveQueueDocLink();

            assertThat(result).hasSize(3);
            assertThat(result).containsExactlyInAnyOrder(qdl1, qdl3, qdl4);
        }

        @Test
        @Tag("query")
        @DisplayName("should find queue links by document ID")
        void shouldFindLinks_byDocumentId() throws Exception {
            QueueDocumentLink qdl1 = new QueueDocumentLink();
            EntityDataGenerator.generateTestDataForModelClass(qdl1);
            qdl1.setDocId(111);
            dao.persist(qdl1);

            QueueDocumentLink qdl2 = new QueueDocumentLink();
            EntityDataGenerator.generateTestDataForModelClass(qdl2);
            qdl2.setDocId(222);
            dao.persist(qdl2);

            QueueDocumentLink qdl3 = new QueueDocumentLink();
            EntityDataGenerator.generateTestDataForModelClass(qdl3);
            qdl3.setDocId(111);
            dao.persist(qdl3);

            QueueDocumentLink qdl4 = new QueueDocumentLink();
            EntityDataGenerator.generateTestDataForModelClass(qdl4);
            qdl4.setDocId(111);
            dao.persist(qdl4);

            List<QueueDocumentLink> result = dao.getQueueFromDocument(111);

            assertThat(result).hasSize(3);
            assertThat(result.get(0)).isEqualTo(qdl1);
            assertThat(result.get(1)).isEqualTo(qdl3);
            assertThat(result.get(2)).isEqualTo(qdl4);
        }

        @Test
        @Tag("query")
        @DisplayName("should find document links by queue ID")
        void shouldFindLinks_byQueueId() throws Exception {
            QueueDocumentLink qdl1 = new QueueDocumentLink();
            EntityDataGenerator.generateTestDataForModelClass(qdl1);
            qdl1.setQueueId(111);
            dao.persist(qdl1);

            QueueDocumentLink qdl2 = new QueueDocumentLink();
            EntityDataGenerator.generateTestDataForModelClass(qdl2);
            qdl2.setQueueId(222);
            dao.persist(qdl2);

            QueueDocumentLink qdl3 = new QueueDocumentLink();
            EntityDataGenerator.generateTestDataForModelClass(qdl3);
            qdl3.setQueueId(111);
            dao.persist(qdl3);

            QueueDocumentLink qdl4 = new QueueDocumentLink();
            EntityDataGenerator.generateTestDataForModelClass(qdl4);
            qdl4.setQueueId(111);
            dao.persist(qdl4);

            List<QueueDocumentLink> result = dao.getDocumentFromQueue(111);

            assertThat(result).hasSize(3);
            assertThat(result.get(0)).isEqualTo(qdl1);
            assertThat(result.get(1)).isEqualTo(qdl3);
            assertThat(result.get(2)).isEqualTo(qdl4);
        }

        @Test
        @Tag("query")
        @DisplayName("should return true when queue has been linked with document")
        void shouldReturnTrue_whenQueueLinkedWithDocument() throws Exception {
            QueueDocumentLink qdl1 = new QueueDocumentLink();
            EntityDataGenerator.generateTestDataForModelClass(qdl1);
            qdl1.setDocId(101);
            qdl1.setQueueId(111);
            dao.persist(qdl1);

            QueueDocumentLink qdl2 = new QueueDocumentLink();
            EntityDataGenerator.generateTestDataForModelClass(qdl2);
            qdl2.setDocId(202);
            qdl2.setQueueId(222);
            dao.persist(qdl2);

            boolean result = dao.hasQueueBeenLinkedWithDocument(101, 111);

            assertThat(result).isTrue();
        }
    }
}
