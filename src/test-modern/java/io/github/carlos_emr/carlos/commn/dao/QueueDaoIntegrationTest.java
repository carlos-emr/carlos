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
import io.github.carlos_emr.carlos.commn.model.Queue;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link QueueDao} covering
 * getHashMapOfQueues, getQueues, getLastId, getQueueName,
 * getQueueid, and addNewQueue.
 *
 * <p>Migrated from legacy {@code QueueDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see QueueDao
 */
@DisplayName("QueueDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("admin")
@Transactional
public class QueueDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private QueueDao dao;

    private Queue createQueue(String name) {
        Queue q = new Queue();
        EntityDataGenerator.generateTestDataForModelClass(q);
        q.setName(name);
        dao.persist(q);
        return q;
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should return hash map of queues with id-to-name mapping")
        void shouldReturnHashMap_whenGetHashMapOfQueuesCalled() {
            Queue q1 = createQueue("alpha");

            HashMap<Integer, String> expectedResult = new HashMap<>();
            expectedResult.put(q1.getId(), "alpha");

            HashMap<Integer, String> result = dao.getHashMapOfQueues();

            assertThat(result).isEqualTo(expectedResult);
        }

        @Test
        @Tag("query")
        @DisplayName("should return list of queues as hashtables")
        void shouldReturnQueueList_whenGetQueuesCalled() {
            createQueue("alpha");
            createQueue("bravo");

            List<Hashtable> result = dao.getQueues();

            assertThat(result).hasSize(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should return the last (max) queue ID")
        void shouldReturnLastId_whenGetLastIdCalled() {
            Queue q1 = createQueue("alpha");
            Queue q2 = createQueue("bravo");
            Queue q3 = createQueue("charlie");

            int latestId = Math.max(q1.getId(), Math.max(q2.getId(), q3.getId()));

            String result = dao.getLastId();

            assertThat(result).isEqualTo(String.valueOf(latestId));
        }

        @Test
        @Tag("query")
        @DisplayName("should return queue name by ID")
        void shouldReturnQueueName_byId() {
            createQueue("alpha");
            Queue q2 = createQueue("bravo");
            createQueue("charlie");

            String result = dao.getQueueName(q2.getId());

            assertThat(result).isEqualTo("bravo");
        }

        @Test
        @Tag("query")
        @DisplayName("should return queue ID by name")
        void shouldReturnQueueId_byName() {
            createQueue("alpha");
            Queue q2 = createQueue("10001");
            createQueue("charlie");

            String result = dao.getQueueid("10001");

            assertThat(result).isEqualTo(q2.getId().toString());
        }

        @Test
        @Tag("create")
        @DisplayName("should add new queue successfully")
        void shouldReturnTrue_whenAddNewQueueCalled() {
            boolean result = dao.addNewQueue("Sigma");

            assertThat(result).isTrue();
        }
    }
}
