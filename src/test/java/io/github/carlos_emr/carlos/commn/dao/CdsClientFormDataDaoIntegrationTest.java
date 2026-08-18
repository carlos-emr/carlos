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
import io.github.carlos_emr.carlos.commn.model.CdsClientFormData;
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
 * Integration tests for {@link CdsClientFormDataDao} covering full method coverage
 * matching the legacy {@code CdsClientFormDataDaoTest}.
 *
 * <p>Tests cover findByQuestion and findByAnswer operations.</p>
 *
 * @since 2026-03-07
 * @see CdsClientFormDataDao
 */
@DisplayName("CdsClientFormData Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class CdsClientFormDataDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private CdsClientFormDataDao dao;

    @Test
    @Tag("read")
    @DisplayName("should return form data matching clientFormId and question")
    void shouldReturnMatchingData_whenSearchingByQuestion() throws Exception {
        int cdsClientFormId = 10;
        String question = "Test question";

        CdsClientFormData formData1 = new CdsClientFormData();
        EntityDataGenerator.generateTestDataForModelClass(formData1);
        formData1.setCdsClientFormId(100);
        formData1.setQuestion("Another question");
        formData1.setAnswer("Test answer");

        CdsClientFormData formData2 = new CdsClientFormData();
        EntityDataGenerator.generateTestDataForModelClass(formData2);
        formData2.setCdsClientFormId(cdsClientFormId);
        formData2.setQuestion(question);
        formData2.setAnswer("Test answer");

        CdsClientFormData formData3 = new CdsClientFormData();
        EntityDataGenerator.generateTestDataForModelClass(formData3);
        formData3.setCdsClientFormId(cdsClientFormId);
        formData3.setQuestion(question);
        formData3.setAnswer("Test answer");

        dao.persist(formData1);
        dao.persist(formData2);
        dao.persist(formData3);

        List<CdsClientFormData> result = dao.findByQuestion(cdsClientFormId, question);
        List<CdsClientFormData> expectedResult = Arrays.asList(formData2, formData3);

        assertThat(result).hasSameSizeAs(expectedResult);
        assertThat(result).containsAll(expectedResult);
    }

    @Test
    @Tag("read")
    @DisplayName("should return form data matching clientFormId and answer")
    void shouldReturnMatchingData_whenSearchingByAnswer() throws Exception {
        int cdsClientFormId = 10;
        String answer = "Test answer";

        CdsClientFormData formData1 = new CdsClientFormData();
        EntityDataGenerator.generateTestDataForModelClass(formData1);
        formData1.setCdsClientFormId(100);
        formData1.setAnswer("Another answer");

        CdsClientFormData formData2 = new CdsClientFormData();
        EntityDataGenerator.generateTestDataForModelClass(formData2);
        formData2.setCdsClientFormId(cdsClientFormId);
        formData2.setAnswer(answer);

        dao.persist(formData1);
        dao.persist(formData2);

        CdsClientFormData result = dao.findByAnswer(cdsClientFormId, answer);
        CdsClientFormData expectedResult = formData2;

        assertThat(result).isEqualTo(expectedResult);
    }
}
