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
import io.github.carlos_emr.carlos.commn.model.DxAssociation;
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
 * Integration tests for {@link DxDao} covering removeAssociations,
 * findAssociation (via findAllAssociations), and findAllAssociations ordering.
 *
 * <p>Migrated from legacy {@code DxDaoTest} (JUnit 4 / DaoTestFixtures)
 * with exact same test logic and assertions.</p>
 *
 * @since 2026-03-07
 * @see DxDao
 */
@DisplayName("DxDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("clinical")
@Transactional
public class DxDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private DxDao dao;

    @Nested
    @DisplayName("removeAssociations tests")
    @Tag("delete")
    class RemoveAssociations {

        /**
         * Ensures that removeAssociations() deletes associations from the table.
         */
        @Test
        @DisplayName("should remove all associations when records exist")
        void shouldRemoveAllAssociations_whenRecordsExist() {
            DxAssociation dx1 = new DxAssociation();
            EntityDataGenerator.generateTestDataForModelClass(dx1);
            dx1.setDxCodeType("A");
            dx1.setDxCode("A");

            DxAssociation dx2 = new DxAssociation();
            EntityDataGenerator.generateTestDataForModelClass(dx2);
            dx2.setDxCodeType("C");
            dx2.setDxCode("B");

            DxAssociation dx3 = new DxAssociation();
            EntityDataGenerator.generateTestDataForModelClass(dx3);
            dx3.setDxCodeType("B");
            dx3.setDxCode("C");

            DxAssociation dx4 = new DxAssociation();
            EntityDataGenerator.generateTestDataForModelClass(dx4);
            dx4.setDxCodeType("B");
            dx4.setDxCode("B");

            dao.persist(dx1);
            dao.persist(dx2);
            dao.persist(dx3);
            dao.persist(dx4);
            hibernateTemplate.flush();

            assertThat(dao.removeAssociations()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("findAssociation tests")
    @Tag("read")
    class FindAssociation {

        /**
         * Ensures that findAllAssociations() returns records where
         * both the dx_code and dx_codetype match when persisted.
         */
        @Test
        @DisplayName("should return matching associations when code and codeType match")
        void shouldReturnMatchingAssociations_whenCodeAndCodeTypeMatch() {
            String codeType = "A";
            String code = "A1";

            DxAssociation dx1 = new DxAssociation();
            EntityDataGenerator.generateTestDataForModelClass(dx1);
            dx1.setDxCodeType(codeType);
            dx1.setDxCode(code);

            DxAssociation dx4 = new DxAssociation();
            EntityDataGenerator.generateTestDataForModelClass(dx4);
            dx4.setDxCodeType(codeType);
            dx4.setDxCode(code);

            dao.persist(dx1);
            dao.persist(dx4);
            hibernateTemplate.flush();

            List<DxAssociation> result = dao.findAllAssociations();
            List<DxAssociation> expectedResult = Arrays.asList(dx1, dx4);

            assertThat(result).hasSameSizeAs(expectedResult);
            assertThat(result).containsAll(expectedResult);
        }
    }

    @Nested
    @DisplayName("findAllAssociations tests")
    @Tag("read")
    class FindAllAssociations {

        /**
         * Ensures that findAllAssociations() returns all records
         * ordered by the codetype and code.
         */
        @Test
        @DisplayName("should return all associations ordered by codeType and code")
        void shouldReturnAllAssociations_orderedByCodeTypeAndCode() {
            DxAssociation dx1 = new DxAssociation();
            EntityDataGenerator.generateTestDataForModelClass(dx1);
            dx1.setDxCodeType("A");
            dx1.setDxCode("D");

            DxAssociation dx2 = new DxAssociation();
            EntityDataGenerator.generateTestDataForModelClass(dx2);
            dx2.setDxCodeType("A");
            dx2.setDxCode("B");

            DxAssociation dx3 = new DxAssociation();
            EntityDataGenerator.generateTestDataForModelClass(dx3);
            dx3.setDxCodeType("A");
            dx3.setDxCode("C");

            DxAssociation dx4 = new DxAssociation();
            EntityDataGenerator.generateTestDataForModelClass(dx4);
            dx4.setDxCodeType("A");
            dx4.setDxCode("A");

            dao.persist(dx1);
            dao.persist(dx2);
            dao.persist(dx3);
            dao.persist(dx4);
            hibernateTemplate.flush();

            List<DxAssociation> result = dao.findAllAssociations();
            List<DxAssociation> expectedResult = Arrays.asList(dx4, dx2, dx3, dx1);

            assertThat(result).hasSameSizeAs(expectedResult);
            for (int i = 0; i < expectedResult.size(); i++) {
                assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
            }
        }
    }
}
