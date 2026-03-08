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
import io.github.carlos_emr.carlos.commn.model.ScheduleTemplateCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ScheduleTemplateCodeDao} covering CRUD operations,
 * findAll queries, and code-based lookups.
 *
 * <p>Migrated from legacy {@code ScheduleTemplateCodeDaoTest} (JUnit 4 / DaoTestFixtures)
 * with BDD-style naming and AssertJ assertions.</p>
 *
 * @since 2026-03-07
 * @see ScheduleTemplateCodeDao
 */
@DisplayName("ScheduleTemplateCodeDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("schedule")
@Transactional
public class ScheduleTemplateCodeDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ScheduleTemplateCodeDao dao;

    private ScheduleTemplateCode createCode(char code) throws Exception {
        ScheduleTemplateCode entity = new ScheduleTemplateCode();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setCode(code);
        dao.persist(entity);
        hibernateTemplate.flush();
        return entity;
    }

    @Test
    @Tag("create")
    @DisplayName("should persist schedule template code with generated ID")
    void shouldPersistScheduleTemplateCode_whenValidDataProvided() throws Exception {
        // Given
        ScheduleTemplateCode entity = new ScheduleTemplateCode();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setCode('A');

        // When
        dao.persist(entity);
        hibernateTemplate.flush();

        // Then
        assertThat(entity.getId()).isNotNull();
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @Tag("read")
        @DisplayName("should return all persisted schedule template codes")
        void shouldReturnAllPersistedScheduleTemplateCodes_afterPersist() throws Exception {
            // Given
            ScheduleTemplateCode code1 = createCode('x');
            ScheduleTemplateCode code2 = createCode('y');
            ScheduleTemplateCode code3 = createCode('z');

            // When
            List<ScheduleTemplateCode> result = dao.findAll();

            // Then
            assertThat(result).hasSize(3);
            assertThat(result).extracting(ScheduleTemplateCode::getId)
                    .containsExactly(code1.getId(), code2.getId(), code3.getId());
        }
    }

    @Nested
    @DisplayName("getByCode (char)")
    class GetByCode {

        @Test
        @Tag("query")
        @DisplayName("should return matching template code when searching by char code")
        void shouldReturnMatchingTemplateCode_whenSearchingByCharCode() throws Exception {
            // Given
            createCode('s');
            ScheduleTemplateCode expected = createCode('a');
            createCode('b');

            // When
            ScheduleTemplateCode result = dao.getByCode('a');

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(expected.getId());
        }
    }

    @Nested
    @DisplayName("findByCode (String)")
    class FindByCode {

        @Test
        @Tag("query")
        @DisplayName("should return matching template code when searching by string code")
        void shouldReturnMatchingTemplateCode_whenSearchingByStringCode() throws Exception {
            // Given
            createCode('a');
            ScheduleTemplateCode expected = createCode('b');
            createCode('c');

            // When
            ScheduleTemplateCode result = dao.findByCode("b");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(expected.getId());
        }
    }
}
