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

import io.github.carlos_emr.carlos.commn.dao.EFormDao.EFormSortOrder;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
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
 * Integration tests for {@link EFormDao} covering findByStatus,
 * findMaxIdForActiveForm, and countFormsOtherThanSpecified.
 *
 * <p>Migrated from legacy {@code EFormDaoTest} (JUnit 4 / DaoTestFixtures)
 * with exact same test logic and assertions.</p>
 *
 * @since 2026-03-07
 * @see EFormDao
 */
@DisplayName("EFormDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("eform")
@Transactional
public class EFormDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private EFormDao dao;

    private Integer populatedFormId;

    @BeforeEach
    void setUp() {
        EForm eform = new EForm();
        EntityDataGenerator.generateTestDataForModelClass(eform);
        eform.setFormName("NUVASHENAH");
        dao.persist(eform);
        populatedFormId = eform.getId();
        hibernateTemplate.flush();
    }

    @Nested
    @DisplayName("findByStatus tests")
    @Tag("read")
    class FindByStatus {

        @Test
        @DisplayName("should return non-empty list when active forms sorted by DATE")
        void shouldReturnForms_whenStatusTrueAndSortByDate() {
            List<EForm> eforms = dao.findByStatus(true, EFormSortOrder.DATE);
            assertThat(eforms).isNotEmpty();
        }

        @Test
        @DisplayName("should return non-empty list when active forms sorted by FILE_NAME")
        void shouldReturnForms_whenStatusTrueAndSortByFileName() {
            List<EForm> eforms = dao.findByStatus(true, EFormSortOrder.FILE_NAME);
            assertThat(eforms).isNotEmpty();
        }

        @Test
        @DisplayName("should return non-empty list when active forms sorted by NAME")
        void shouldReturnForms_whenStatusTrueAndSortByName() {
            List<EForm> eforms = dao.findByStatus(true, EFormSortOrder.NAME);
            assertThat(eforms).isNotEmpty();
        }

        @Test
        @DisplayName("should return non-empty list when active forms sorted by SUBJECT")
        void shouldReturnForms_whenStatusTrueAndSortBySubject() {
            List<EForm> eforms = dao.findByStatus(true, EFormSortOrder.SUBJECT);
            assertThat(eforms).isNotEmpty();
        }

        @Test
        @DisplayName("should return non-null result when status is false")
        void shouldReturnResult_whenStatusFalse() {
            List<EForm> eforms = dao.findByStatus(false);
            assertThat(eforms).isNotNull();
        }
    }

    @Nested
    @DisplayName("findMaxIdForActiveForm tests")
    @Tag("read")
    class FindMaxIdForActiveForm {

        @Test
        @DisplayName("should return positive id when active form exists with matching name")
        void shouldReturnPositiveId_whenActiveFormExists() {
            Integer id = dao.findMaxIdForActiveForm("NUVASHENAH");
            assertThat(id).isNotNull();
            assertThat(id).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("countFormsOtherThanSpecified tests")
    @Tag("read")
    class CountFormsOtherThanSpecified {

        @Test
        @DisplayName("should return non-negative count when form name and id provided")
        void shouldReturnNonNegativeCount_whenFormNameAndIdProvided() {
            Long count = dao.countFormsOtherThanSpecified("NUVASHENAH", populatedFormId);
            assertThat(count).isNotNull();
            assertThat(count).isGreaterThanOrEqualTo(0);
        }
    }
}
