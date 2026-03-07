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
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.MessageList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link MessageListDao} covering provider-and-message
 * filtering queries.
 *
 * <p>Migrated from legacy {@code MessageListDaoTest} (JUnit 4 / DaoTestFixtures)
 * with BDD-style naming and AssertJ assertions.</p>
 *
 * @since 2026-03-07
 * @see MessageListDao
 */
@DisplayName("MessageListDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("message")
@Transactional
public class MessageListDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private MessageListDao dao;

    private MessageList createMessageList(String providerNo, long messageNo) {
        MessageList ml = new MessageList();
        EntityDataGenerator.generateTestDataForModelClass(ml);
        ml.setProviderNo(providerNo);
        ml.setMessage(messageNo);
        dao.persist(ml);
        hibernateTemplate.flush();
        return ml;
    }

    @Test
    @Tag("query")
    @DisplayName("should return matching message lists when filtering by provider and message number")
    void shouldReturnMatchingMessageLists_whenFilteringByProviderAndMessageNo() {
        // Given
        String providerNo1 = "111";
        String providerNo2 = "222";
        long message1 = 101L;
        long message2 = 202L;

        MessageList ml1 = createMessageList(providerNo1, message1);
        createMessageList(providerNo2, message1);
        MessageList ml3 = createMessageList(providerNo1, message1);
        createMessageList(providerNo1, message2);

        // When
        List<MessageList> result = dao.findByProviderNoAndMessageNo(providerNo1, message1);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(MessageList::getId)
                .containsExactly(ml1.getId(), ml3.getId());
    }

    @Test
    @Tag("query")
    @DisplayName("should return empty list when no messages match provider and message number")
    void shouldReturnEmptyList_whenNoMessagesMatchProviderAndMessageNo() {
        // Given
        createMessageList("111", 101L);

        // When
        List<MessageList> result = dao.findByProviderNoAndMessageNo("999", 999L);

        // Then
        assertThat(result).isEmpty();
    }
}
