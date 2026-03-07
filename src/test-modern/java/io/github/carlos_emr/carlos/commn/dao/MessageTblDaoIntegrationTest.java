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
import io.github.carlos_emr.carlos.commn.model.MessageTbl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link MessageTblDao} covering internal
 * messaging CRUD, provider-based lookups, and batch ID queries.
 *
 * <p>Migrated from legacy {@code MessageTblDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see MessageTblDao
 */
@DisplayName("MessageTblDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("messaging")
@Transactional
public class MessageTblDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private MessageTblDao messageTblDao;

    private MessageTbl createMessage(String subject, String message, String sentBy, String sentByNo) {
        MessageTbl msg = new MessageTbl();
        msg.setSubject(subject);
        msg.setMessage(message);
        msg.setSentBy(sentBy);
        msg.setSentByNo(sentByNo);
        msg.setSentTo("100002");
        msg.setDate(new Date());
        msg.setTime(new Date());
        messageTblDao.persist(msg);
        return msg;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist message with generated ID")
        void shouldPersistMessage_whenValidDataProvided() {
            MessageTbl msg = createMessage("Lab Alert", "New lab results available", "Dr. Smith", "100001");
            assertThat(msg.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find message by ID")
        void shouldFindMessage_whenValidIdProvided() {
            MessageTbl saved = createMessage("Appointment Reminder", "Patient follow-up needed", "Dr. Jones", "100003");
            MessageTbl found = messageTblDao.find(saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getSubject()).isEqualTo("Appointment Reminder");
            assertThat(found.getMessage()).isEqualTo("Patient follow-up needed");
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should find messages by IDs")
        void shouldFindMessages_byIds() {
            MessageTbl msg1 = createMessage("Msg 1", "Body 1", "Sender", "100001");
            MessageTbl msg2 = createMessage("Msg 2", "Body 2", "Sender", "100001");
            MessageTbl msg3 = createMessage("Msg 3", "Body 3", "Sender", "100001");

            List<MessageTbl> results = messageTblDao.findByIds(Arrays.asList(msg1.getId(), msg3.getId()));
            assertThat(results).hasSize(2);
            assertThat(results).extracting(MessageTbl::getSubject)
                    .containsExactlyInAnyOrder("Msg 1", "Msg 3");
        }

        @Test
        @Tag("query")
        @DisplayName("should count all messages")
        void shouldCountAllMessages() {
            createMessage("Count Test", "Body", "Sender", "100001");
            long count = messageTblDao.getCountAll();
            assertThat(count).isGreaterThanOrEqualTo(1);
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list for non-existent IDs")
        void shouldReturnEmptyList_whenIdsNotFound() {
            List<MessageTbl> results = messageTblDao.findByIds(Arrays.asList(999998, 999999));
            assertThat(results).isEmpty();
        }
    }
}
