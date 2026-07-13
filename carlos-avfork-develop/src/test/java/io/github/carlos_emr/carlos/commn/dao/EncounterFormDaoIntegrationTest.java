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
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link EncounterFormDao} covering full method coverage
 * matching the legacy {@code EncounterFormDaoTest}.
 *
 * <p>Tests cover findAll and findByFormName operations.</p>
 *
 * @since 2026-03-07
 * @see EncounterFormDao
 */
@DisplayName("EncounterForm Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class EncounterFormDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private EncounterFormDao dao;

    @Test
    @Tag("read")
    @DisplayName("should return all encounter forms")
    void shouldReturnAllForms_whenFindAllCalled() throws Exception {
        EncounterForm form1 = new EncounterForm();
        EntityDataGenerator.generateTestDataForModelClass(form1);
        form1.setFormName("FormA");
        form1.setFormValue("1");

        EncounterForm form2 = new EncounterForm();
        EntityDataGenerator.generateTestDataForModelClass(form2);
        form2.setFormName("FormC");
        form2.setFormValue("2");

        EncounterForm form3 = new EncounterForm();
        EntityDataGenerator.generateTestDataForModelClass(form3);
        form3.setFormName("FormB");
        form3.setFormValue("3");

        dao.persist(form1);
        dao.persist(form2);
        dao.persist(form3);

        List<EncounterForm> result = dao.findAll();
        List<EncounterForm> expectedResult = Arrays.asList(form1, form3, form2);

        assertThat(result).hasSameSizeAs(expectedResult);
        assertThat(result).containsAll(expectedResult);
    }

    @Test
    @Tag("read")
    @DisplayName("should return forms matching specified form name")
    void shouldReturnMatchingForms_whenSearchingByFormName() throws Exception {
        String formName = "EncounterForm";

        EncounterForm form1 = new EncounterForm();
        EntityDataGenerator.generateTestDataForModelClass(form1);
        form1.setFormName(formName);
        form1.setFormValue("1");

        EncounterForm form2 = new EncounterForm();
        EntityDataGenerator.generateTestDataForModelClass(form2);
        form2.setFormName("FormC");
        form2.setFormValue("2");

        EncounterForm form3 = new EncounterForm();
        EntityDataGenerator.generateTestDataForModelClass(form3);
        form3.setFormName(formName);
        form3.setFormValue("3");

        dao.persist(form1);
        dao.persist(form2);
        dao.persist(form3);

        List<EncounterForm> result = dao.findByFormName(formName);
        List<EncounterForm> expectedResult = Arrays.asList(form1, form3);

        assertThat(result).hasSameSizeAs(expectedResult);
        assertThat(result).containsExactlyElementsOf(expectedResult);
    }
}
