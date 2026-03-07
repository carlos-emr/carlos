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
import io.github.carlos_emr.carlos.commn.model.ScheduleTemplate;
import io.github.carlos_emr.carlos.commn.model.ScheduleTemplateCode;
import io.github.carlos_emr.carlos.commn.model.ScheduleTemplatePrimaryKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ScheduleTemplateDao} and {@link ScheduleTemplateCodeDao}
 * covering template CRUD, provider-based queries, and template code management.
 *
 * <p>Migrated from legacy {@code ScheduleTemplateDaoTest} and
 * {@code ScheduleTemplateCodeDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ScheduleTemplateDao
 * @see ScheduleTemplateCodeDao
 */
@DisplayName("ScheduleTemplate/Code Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("schedule")
@Transactional
public class ScheduleTemplateDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ScheduleTemplateDao scheduleTemplateDao;

    @Autowired
    private ScheduleTemplateCodeDao scheduleTemplateCodeDao;

    @Nested
    @DisplayName("ScheduleTemplateCode CRUD")
    class ScheduleTemplateCodeCrud {

        @Test
        @Tag("create")
        @DisplayName("should persist schedule template code")
        void shouldPersistTemplateCode_whenValidDataProvided() {
            ScheduleTemplateCode code = new ScheduleTemplateCode();
            code.setCode("A");
            code.setDescription("Available");
            code.setDuration("15");
            code.setColor("green");
            code.setConfirm("N");
            code.setBookinglimit(1);
            scheduleTemplateCodeDao.persist(code);
            assertThat(code.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find template code by ID")
        void shouldFindTemplateCode_whenValidIdProvided() {
            ScheduleTemplateCode code = new ScheduleTemplateCode();
            code.setCode("B");
            code.setDescription("Busy");
            code.setDuration("30");
            code.setColor("red");
            code.setConfirm("N");
            code.setBookinglimit(1);
            scheduleTemplateCodeDao.persist(code);

            ScheduleTemplateCode found = scheduleTemplateCodeDao.find(code.getId());
            assertThat(found).isNotNull();
            assertThat(found.getCode()).isEqualTo("B");
        }

        @Test
        @Tag("query")
        @DisplayName("should find all template codes")
        void shouldFindAllTemplateCodes() {
            ScheduleTemplateCode code1 = new ScheduleTemplateCode();
            code1.setCode("X");
            code1.setDescription("Test X");
            code1.setDuration("15");
            code1.setColor("blue");
            code1.setConfirm("N");
            code1.setBookinglimit(1);
            scheduleTemplateCodeDao.persist(code1);

            List<ScheduleTemplateCode> all = scheduleTemplateCodeDao.findAll(0, 100);
            assertThat(all).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("ScheduleTemplate CRUD")
    class ScheduleTemplateCrud {

        @Test
        @Tag("create")
        @DisplayName("should persist schedule template")
        void shouldPersistTemplate_whenValidDataProvided() {
            ScheduleTemplate template = new ScheduleTemplate();
            ScheduleTemplatePrimaryKey pk = new ScheduleTemplatePrimaryKey();
            pk.setProviderNo("999998");
            pk.setName("Morning Clinic");
            template.setId(pk);
            template.setTimecode("AAAAABBBB");
            template.setSummary("Morning");
            scheduleTemplateDao.persist(template);

            ScheduleTemplate found = scheduleTemplateDao.find(pk);
            assertThat(found).isNotNull();
            assertThat(found.getTimecode()).isEqualTo("AAAAABBBB");
        }

        @Test
        @Tag("query")
        @DisplayName("should count all schedule templates")
        void shouldCountAllTemplates() {
            ScheduleTemplate template = new ScheduleTemplate();
            ScheduleTemplatePrimaryKey pk = new ScheduleTemplatePrimaryKey();
            pk.setProviderNo("999998");
            pk.setName("Count Test");
            template.setId(pk);
            template.setTimecode("AAAA");
            template.setSummary("Count");
            scheduleTemplateDao.persist(template);

            long count = scheduleTemplateDao.getCountAll();
            assertThat(count).isEqualTo(1);
        }
    }
}
