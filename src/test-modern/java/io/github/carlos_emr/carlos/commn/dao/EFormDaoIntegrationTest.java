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
 * with meaningful assertions verifying filtering and counting behavior.</p>
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

    private Integer activeFormId;
    private Integer inactiveFormId;

    @BeforeEach
    void setUp() throws Exception {
        // Create an active form (current=true)
        EForm activeForm = new EForm();
        EntityDataGenerator.generateTestDataForModelClass(activeForm);
        activeForm.setFormName("TESTFORM_ACTIVE");
        activeForm.setCurrent(true);
        dao.persist(activeForm);
        activeFormId = activeForm.getId();

        // Create an inactive form (current=false)
        EForm inactiveForm = new EForm();
        EntityDataGenerator.generateTestDataForModelClass(inactiveForm);
        inactiveForm.setFormName("TESTFORM_INACTIVE");
        inactiveForm.setCurrent(false);
        dao.persist(inactiveForm);
        inactiveFormId = inactiveForm.getId();

        hibernateTemplate.flush();
    }

    @Nested
    @DisplayName("findByStatus tests")
    @Tag("read")
    class FindByStatus {

        @Test
        @DisplayName("should return only active forms when status is true")
        void shouldReturnActiveForms_whenStatusTrue() throws Exception {
            List<EForm> activeForms = dao.findByStatus(true, EFormSortOrder.NAME);

            assertThat(activeForms).isNotEmpty();
            assertThat(activeForms).allMatch(EForm::isCurrent);
            assertThat(activeForms).anyMatch(f -> f.getId().equals(activeFormId));
            assertThat(activeForms).noneMatch(f -> f.getId().equals(inactiveFormId));
        }

        @Test
        @DisplayName("should return only inactive forms when status is false")
        void shouldReturnInactiveForms_whenStatusFalse() throws Exception {
            List<EForm> inactiveForms = dao.findByStatus(false);

            assertThat(inactiveForms).isNotEmpty();
            assertThat(inactiveForms).allMatch(f -> !f.isCurrent());
            assertThat(inactiveForms).anyMatch(f -> f.getId().equals(inactiveFormId));
            assertThat(inactiveForms).noneMatch(f -> f.getId().equals(activeFormId));
        }

        @Test
        @DisplayName("should return forms sorted by different sort orders without error")
        void shouldReturnForms_withDifferentSortOrders() throws Exception {
            List<EForm> byDate = dao.findByStatus(true, EFormSortOrder.DATE);
            List<EForm> byFileName = dao.findByStatus(true, EFormSortOrder.FILE_NAME);
            List<EForm> bySubject = dao.findByStatus(true, EFormSortOrder.SUBJECT);

            // All sort orders should return the same set of active forms
            assertThat(byDate).hasSameSizeAs(byFileName);
            assertThat(byDate).hasSameSizeAs(bySubject);
            assertThat(byDate).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("findMaxIdForActiveForm tests")
    @Tag("read")
    class FindMaxIdForActiveForm {

        @Test
        @DisplayName("should return the max ID for the active form with matching name")
        void shouldReturnMaxId_whenActiveFormExists() throws Exception {
            // Create a second active form with the same name
            EForm secondForm = new EForm();
            EntityDataGenerator.generateTestDataForModelClass(secondForm);
            secondForm.setFormName("TESTFORM_ACTIVE");
            secondForm.setCurrent(true);
            dao.persist(secondForm);
            hibernateTemplate.flush();

            Integer maxId = dao.findMaxIdForActiveForm("TESTFORM_ACTIVE");

            assertThat(maxId).isEqualTo(secondForm.getId());
        }

        @Test
        @DisplayName("should return null when no active form matches the name")
        void shouldReturnNull_whenNoActiveFormMatchesName() throws Exception {
            Integer id = dao.findMaxIdForActiveForm("NONEXISTENT_FORM_XYZ");

            assertThat(id).isNull();
        }
    }

    @Nested
    @DisplayName("countFormsOtherThanSpecified tests")
    @Tag("read")
    class CountFormsOtherThanSpecified {

        @Test
        @DisplayName("should return zero when only one active form exists with matching name")
        void shouldReturnZero_whenOnlyOneActiveFormWithName() throws Exception {
            Long count = dao.countFormsOtherThanSpecified("TESTFORM_ACTIVE", activeFormId);

            assertThat(count).isEqualTo(0L);
        }

        @Test
        @DisplayName("should count other active forms with same name excluding specified ID")
        void shouldCountOtherForms_whenMultipleActiveFormsExist() throws Exception {
            // Create a second active form with the same name
            EForm secondForm = new EForm();
            EntityDataGenerator.generateTestDataForModelClass(secondForm);
            secondForm.setFormName("TESTFORM_ACTIVE");
            secondForm.setCurrent(true);
            dao.persist(secondForm);
            hibernateTemplate.flush();

            Long count = dao.countFormsOtherThanSpecified("TESTFORM_ACTIVE", activeFormId);

            assertThat(count).isEqualTo(1L);
        }
    }
}
