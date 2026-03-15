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
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link OscarLogDao} covering demographic-based lookups
 * and read-status checks.
 *
 * <p>Migrated from legacy {@code OscarLogDaoTest} (JUnit 4 / DaoTestFixtures)
 * with BDD-style naming and AssertJ assertions.</p>
 *
 * @since 2026-03-07
 * @see OscarLogDao
 */
@DisplayName("OscarLogDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("log")
@Transactional
public class OscarLogDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private OscarLogDao dao;

    private OscarLog createOscarLog(Integer demographicId, String providerNo, String action,
                                     String content, String contentId) throws Exception {
        OscarLog log = new OscarLog();
        EntityDataGenerator.generateTestDataForModelClass(log);
        log.setDemographicId(demographicId);
        log.setProviderNo(providerNo);
        log.setAction(action);
        log.setContent(content);
        log.setContentId(contentId);
        dao.persist(log);
        hibernateTemplate.flush();
        return log;
    }

    @Nested
    @DisplayName("findByDemographicId")
    class FindByDemographicId {

        @Test
        @Tag("query")
        @DisplayName("should return logs for specific demographic ID")
        void shouldReturnLogs_forSpecificDemographicId() throws Exception {
            // Given
            int demoId1 = 100;
            int demoId2 = 200;

            OscarLog log1 = createOscarLog(demoId1, "prov1", "read", "content", "1");
            createOscarLog(demoId2, "prov1", "read", "content", "2");
            OscarLog log3 = createOscarLog(demoId1, "prov1", "read", "content", "3");

            // When
            List<OscarLog> result = dao.findByDemographicId(demoId1);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(OscarLog::getId)
                    .containsExactly(log1.getId(), log3.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no logs for demographic")
        void shouldReturnEmptyList_whenNoLogsForDemographic() throws Exception {
            // Given
            createOscarLog(100, "prov1", "read", "content", "1");

            // When
            List<OscarLog> result = dao.findByDemographicId(99999);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("hasRead")
    class HasRead {

        @Test
        @Tag("query")
        @DisplayName("should return true when provider has read the content")
        void shouldReturnTrue_whenProviderHasReadContent() throws Exception {
            // Given
            String providerNo = "100";
            String content = "epsilon";
            String contentId = "111";

            createOscarLog(null, providerNo, "read", content, contentId);
            createOscarLog(null, "200", "NotRead", "lambda", "222");
            createOscarLog(null, providerNo, "NotRead", content, contentId);

            // When
            boolean result = dao.hasRead(providerNo, content, contentId);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @Tag("query")
        @DisplayName("should return false when provider has not read the content")
        void shouldReturnFalse_whenProviderHasNotReadContent() throws Exception {
            // Given
            createOscarLog(null, "100", "write", "epsilon", "111");

            // When
            boolean result = dao.hasRead("100", "epsilon", "111");

            // Then
            assertThat(result).isFalse();
        }
    }
}
