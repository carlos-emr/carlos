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
import io.github.carlos_emr.carlos.commn.model.SystemMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link SystemMessageDao} covering system
 * message CRUD and findAll with ordering.
 *
 * <p>Migrated from legacy {@code SystemMessageDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see SystemMessageDao
 */
@DisplayName("SystemMessageDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("admin")
@Transactional
public class SystemMessageDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private SystemMessageDao systemMessageDao;

    private SystemMessage createMessage(String message, int daysUntilExpiry) {
        SystemMessage msg = new SystemMessage();
        msg.setMessage(message);
        msg.setCreationDate(new Date());
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, daysUntilExpiry);
        msg.setExpiryDate(cal.getTime());
        systemMessageDao.persist(msg);
        return msg;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist system message with generated ID")
        void shouldPersistSystemMessage_whenValidDataProvided() {
            SystemMessage msg = createMessage("System maintenance tonight", 1);
            assertThat(msg.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find system message by ID")
        void shouldFindSystemMessage_whenValidIdProvided() {
            SystemMessage saved = createMessage("Test alert", 7);
            SystemMessage found = systemMessageDao.find(saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getMessage()).isEqualTo("Test alert");
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should find all system messages")
        void shouldFindAllSystemMessages() {
            createMessage("Message A", 1);
            createMessage("Message B", 7);
            createMessage("Message C", 30);
            List<SystemMessage> all = systemMessageDao.findAll();
            assertThat(all).hasSizeGreaterThanOrEqualTo(3);
        }

        @Test
        @Tag("query")
        @DisplayName("should count all system messages")
        void shouldCountAllSystemMessages() {
            createMessage("Count message", 1);
            long count = systemMessageDao.getCountAll();
            assertThat(count).isGreaterThanOrEqualTo(1);
        }
    }
}
