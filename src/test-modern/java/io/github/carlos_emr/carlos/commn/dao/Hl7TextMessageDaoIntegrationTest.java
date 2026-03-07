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
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link Hl7TextMessageDao} covering
 * findByFileUploadCheckId queries.
 *
 * <p>Migrated from legacy {@code Hl7TextMessageDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see Hl7TextMessageDao
 */
@DisplayName("Hl7TextMessageDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("hl7")
@Transactional
public class Hl7TextMessageDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private Hl7TextMessageDao dao;

    @Test
    @Tag("query")
    @DisplayName("should return messages matching file upload check ID")
    void shouldReturnMessages_forMatchingFileUploadCheckId() {
        int fileUploadCheckId1 = 1111;
        int fileUploadCheckId2 = 2222;

        Hl7TextMessage msg1 = new Hl7TextMessage();
        EntityDataGenerator.generateTestDataForModelClass(msg1);
        msg1.setFileUploadCheckId(fileUploadCheckId1);
        dao.persist(msg1);

        Hl7TextMessage msg2 = new Hl7TextMessage();
        EntityDataGenerator.generateTestDataForModelClass(msg2);
        msg2.setFileUploadCheckId(fileUploadCheckId2);
        dao.persist(msg2);

        Hl7TextMessage msg3 = new Hl7TextMessage();
        EntityDataGenerator.generateTestDataForModelClass(msg3);
        msg3.setFileUploadCheckId(fileUploadCheckId1);
        dao.persist(msg3);

        hibernateTemplate.flush();

        List<Hl7TextMessage> result = dao.findByFileUploadCheckId(fileUploadCheckId1);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Hl7TextMessage::getId)
                .containsExactlyInAnyOrder(msg1.getId(), msg3.getId());
    }

    @Test
    @Tag("query")
    @DisplayName("should return empty list when no messages match file upload check ID")
    void shouldReturnEmptyList_whenNoMessagesMatchFileUploadCheckId() {
        List<Hl7TextMessage> result = dao.findByFileUploadCheckId(99999);

        assertThat(result).isEmpty();
    }
}
