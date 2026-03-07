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

import io.github.carlos_emr.carlos.commn.model.MsgDemoMap;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MsgDemoMapDao} covering create,
 * getMessagesAndDemographicsByMessageId, and getMapAndMessagesByDemographicNo.
 *
 * <p>Migrated from legacy {@code MsgDemoMapDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see MsgDemoMapDao
 */
@DisplayName("MsgDemoMap Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("messaging")
@Transactional
public class MsgDemoMapDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private MsgDemoMapDao dao;

    @Nested
    @DisplayName("Create operations")
    class CreateOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist msg demo map with generated ID")
        void shouldPersistMsgDemoMap_whenValidDataProvided() {
            MsgDemoMap entity = new MsgDemoMap();
            entity.setDemographic_no(1);
            entity.setMessageID(1);
            dao.persist(entity);

            assertThat(entity.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("getMessagesAndDemographicsByMessageId")
    class GetMessagesAndDemographicsByMessageId {

        @Test
        @Tag("read")
        @DisplayName("should return non-null result for any message ID")
        void shouldReturnNonNullResult_whenCalledWithMessageId() {
            List<Object[]> result = dao.getMessagesAndDemographicsByMessageId(100);

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("getMapAndMessagesByDemographicNo")
    class GetMapAndMessagesByDemographicNo {

        @Test
        @Tag("read")
        @DisplayName("should return non-null result for any demographic number")
        void shouldReturnNonNullResult_whenCalledWithDemographicNo() {
            List<Object[]> result = dao.getMapAndMessagesByDemographicNo(100);

            assertThat(result).isNotNull();
        }
    }
}
