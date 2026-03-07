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
import io.github.carlos_emr.carlos.commn.model.ConsultationRequestExt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ConsultationRequestExtDao} covering
 * getConsultationRequestExts and getConsultationRequestExtsByKey.
 *
 * <p>Migrated from legacy {@code ConsultationRequestExtDaoTest} (JUnit 4 / DaoTestFixtures)
 * with expanded coverage and BDD-style naming.</p>
 *
 * @since 2026-03-07
 * @see ConsultationRequestExtDao
 */
@DisplayName("ConsultationRequestExtDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("consultation")
@Transactional
public class ConsultationRequestExtDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ConsultationRequestExtDao dao;

    @Nested
    @DisplayName("getConsultationRequestExts(requestId)")
    class GetByRequestId {

        @Test
        @Tag("query")
        @DisplayName("should return only extensions matching the request ID")
        void shouldReturnExtensions_matchingRequestId() throws Exception {
            int requestId = 10;

            ConsultationRequestExt ext1 = new ConsultationRequestExt();
            EntityDataGenerator.generateTestDataForModelClass(ext1);
            ext1.setRequestId(requestId);

            ConsultationRequestExt ext2 = new ConsultationRequestExt();
            EntityDataGenerator.generateTestDataForModelClass(ext2);
            ext2.setRequestId(9999);

            ConsultationRequestExt ext3 = new ConsultationRequestExt();
            EntityDataGenerator.generateTestDataForModelClass(ext3);
            ext3.setRequestId(requestId);

            dao.persist(ext1);
            dao.persist(ext2);
            dao.persist(ext3);

            List<ConsultationRequestExt> result = dao.getConsultationRequestExts(requestId);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactlyInAnyOrder(ext1, ext3);
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no extensions match the request ID")
        void shouldReturnEmptyList_whenNoExtensionsMatchRequestId() {
            List<ConsultationRequestExt> result = dao.getConsultationRequestExts(99999);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getConsultationRequestExtsByKey(requestId, key)")
    class GetByRequestIdAndKey {

        @Test
        @Tag("query")
        @DisplayName("should return value for matching request ID and key")
        void shouldReturnValue_forMatchingRequestIdAndKey() throws Exception {
            int requestId = 10;
            String key = "password";

            ConsultationRequestExt ext1 = new ConsultationRequestExt();
            EntityDataGenerator.generateTestDataForModelClass(ext1);
            ext1.setRequestId(requestId);
            ext1.setKey(key);
            ext1.setValue("value1");

            // Wrong request ID
            ConsultationRequestExt ext2 = new ConsultationRequestExt();
            EntityDataGenerator.generateTestDataForModelClass(ext2);
            ext2.setRequestId(9999);
            ext2.setKey(key);
            ext2.setValue("value2");

            // Wrong key
            ConsultationRequestExt ext3 = new ConsultationRequestExt();
            EntityDataGenerator.generateTestDataForModelClass(ext3);
            ext3.setRequestId(requestId);
            ext3.setKey("wrongKey");
            ext3.setValue("value3");

            ConsultationRequestExt ext4 = new ConsultationRequestExt();
            EntityDataGenerator.generateTestDataForModelClass(ext4);
            ext4.setRequestId(requestId);
            ext4.setKey(key);
            ext4.setValue("value4");

            dao.persist(ext1);
            dao.persist(ext2);
            dao.persist(ext3);
            dao.persist(ext4);

            String result = dao.getConsultationRequestExtsByKey(requestId, key);

            assertThat(result).isEqualTo("value1");
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when no matching request ID and key")
        void shouldReturnNull_whenNoMatchingRequestIdAndKey() {
            String result = dao.getConsultationRequestExtsByKey(99999, "nonexistent");

            assertThat(result).isNull();
        }
    }
}
