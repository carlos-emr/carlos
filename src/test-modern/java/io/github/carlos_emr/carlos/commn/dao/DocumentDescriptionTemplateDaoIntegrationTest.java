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

import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.DocumentDescriptionTemplate;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link DocumentDescriptionTemplateDao} covering create,
 * find, and findByDocTypeAndProviderNo.
 *
 * <p>Migrated from legacy {@code DocumentDescriptionTemplateDaoTest}
 * (JUnit 4 / DaoTestFixtures) with exact same test logic and assertions.</p>
 *
 * @since 2026-03-07
 * @see DocumentDescriptionTemplateDao
 */
@DisplayName("DocumentDescriptionTemplateDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("document")
@Transactional
public class DocumentDescriptionTemplateDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private DocumentDescriptionTemplateDao dao;

    @Nested
    @DisplayName("create tests")
    @Tag("create")
    class Create {

        @Test
        @DisplayName("should persist entity with generated id")
        void shouldPersistEntity_withGeneratedId() {
            DocumentDescriptionTemplate entity = new DocumentDescriptionTemplate();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);
            assertThat(entity.getId()).isPositive();
        }
    }

    @Nested
    @DisplayName("find tests")
    @Tag("read")
    class Find {

        @Test
        @DisplayName("should return correct entity when found by id")
        void shouldReturnCorrectEntity_whenFoundById() {
            DocumentDescriptionTemplate ddt1 = new DocumentDescriptionTemplate();
            EntityDataGenerator.generateTestDataForModelClass(ddt1);
            ddt1.setDescription("alpha");
            ddt1.setDescriptionShortcut("alpha");
            ddt1.setDocType("alpha");
            ddt1.setProviderNo("123456");
            dao.persist(ddt1);

            DocumentDescriptionTemplate ddt2 = new DocumentDescriptionTemplate();
            EntityDataGenerator.generateTestDataForModelClass(ddt2);
            ddt2.setDescription("bravo");
            ddt2.setDescriptionShortcut("bravo");
            ddt2.setDocType("bravo");
            ddt2.setProviderNo(null);
            dao.persist(ddt2);

            int id = ddt2.getId();

            DocumentDescriptionTemplate ddt3 = new DocumentDescriptionTemplate();
            EntityDataGenerator.generateTestDataForModelClass(ddt3);
            dao.persist(ddt3);
            hibernateTemplate.flush();

            DocumentDescriptionTemplate result = dao.find(id);
            assertThat(result).isEqualTo(ddt2);
        }
    }

    @Nested
    @DisplayName("findByDocTypeAndProviderNo tests")
    @Tag("read")
    class FindByDocTypeAndProviderNo {

        @Test
        @DisplayName("should return matching templates when docType and providerNo match")
        void shouldReturnMatchingTemplates_whenDocTypeAndProviderNoMatch() {
            String docType = "mylab";
            String providerNo = "123456";

            DocumentDescriptionTemplate ddt1 = new DocumentDescriptionTemplate();
            EntityDataGenerator.generateTestDataForModelClass(ddt1);
            ddt1.setDescription("alpha");
            ddt1.setDescriptionShortcut("a");
            ddt1.setDocType(docType);
            ddt1.setProviderNo(providerNo);
            dao.persist(ddt1);

            DocumentDescriptionTemplate ddt2 = new DocumentDescriptionTemplate();
            EntityDataGenerator.generateTestDataForModelClass(ddt2);
            ddt2.setDescription("bravo");
            ddt2.setDescriptionShortcut("b");
            ddt2.setDocType("bravo");
            ddt2.setProviderNo(null);
            dao.persist(ddt2);

            DocumentDescriptionTemplate ddt3 = new DocumentDescriptionTemplate();
            EntityDataGenerator.generateTestDataForModelClass(ddt3);
            ddt3.setDescription("charlie");
            ddt3.setDescriptionShortcut("c");
            ddt3.setDocType(docType);
            ddt3.setProviderNo(providerNo);
            dao.persist(ddt3);
            hibernateTemplate.flush();

            List<DocumentDescriptionTemplate> expectedResult = Arrays.asList(ddt1, ddt3);
            List<DocumentDescriptionTemplate> result = dao.findByDocTypeAndProviderNo(docType, providerNo);

            assertThat(result).hasSameSizeAs(expectedResult);
            for (int i = 0; i < expectedResult.size(); i++) {
                assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
            }
        }
    }
}
