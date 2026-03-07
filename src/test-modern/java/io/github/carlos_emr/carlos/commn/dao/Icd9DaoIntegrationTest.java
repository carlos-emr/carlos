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
import io.github.carlos_emr.carlos.commn.model.AbstractCodeSystemModel;
import io.github.carlos_emr.carlos.commn.model.Icd9;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link Icd9Dao} covering persist, find, search, and lookup operations.
 *
 * <p>Migrated from legacy {@code Icd9DaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see Icd9Dao
 */
@DisplayName("Icd9 Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class Icd9DaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private Icd9Dao dao;

    /**
     * Helper to create and persist an Icd9 entity.
     */
    private Icd9 createIcd9(String code, String description) {
        Icd9 entity = new Icd9();
        entity.setIcd9(code);
        entity.setDescription(description);
        dao.persist(entity);
        return entity;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist ICD-9 code with generated ID")
        void shouldPersistIcd9_whenValidDataProvided() {
            Icd9 entity = new Icd9();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find ICD-9 by ID with correct fields")
        void shouldFindIcd9_whenValidIdProvided() {
            Icd9 saved = createIcd9("250.0", "Diabetes mellitus");
            hibernateTemplate.flush();

            Icd9 found = dao.find(saved.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getIcd9()).isEqualTo("250.0");
            assertThat(found.getDescription()).isEqualTo("Diabetes mellitus");
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should find ICD-9 codes by exact code match")
        void shouldFindIcd9Codes_byExactCode() {
            createIcd9("401.1", "Hypertension benign");
            createIcd9("401.9", "Hypertension unspecified");
            hibernateTemplate.flush();

            List<Icd9> result = dao.getIcd9Code("401.1");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getIcd9()).isEqualTo("401.1");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when code does not exist")
        void shouldReturnEmptyList_whenCodeDoesNotExist() {
            List<Icd9> result = dao.getIcd9Code("999.99");

            assertThat(result).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should search ICD-9 by code or description using LIKE")
        void shouldSearchIcd9_byCodeOrDescription() {
            createIcd9("780.6", "Fever of unknown origin");
            createIcd9("780.7", "Malaise and fatigue");
            createIcd9("250.0", "Diabetes mellitus type 2");
            hibernateTemplate.flush();

            List<Icd9> resultByCode = dao.getIcd9("780");

            assertThat(resultByCode).hasSize(2);
            assertThat(resultByCode).allMatch(i -> i.getIcd9().contains("780"));

            List<Icd9> resultByDesc = dao.getIcd9("Diabetes");

            assertThat(resultByDesc).hasSize(1);
            assertThat(resultByDesc.get(0).getDescription()).contains("Diabetes");
        }

        @Test
        @Tag("query")
        @DisplayName("should find by code returning exact match or null")
        void shouldFindByCode_returningExactMatchOrNull() {
            createIcd9("427.31", "Atrial fibrillation");
            hibernateTemplate.flush();

            Icd9 found = dao.findByCode("427.31");
            Icd9 notFound = dao.findByCode("999.99");

            assertThat(found).isNotNull();
            assertThat(found.getIcd9()).isEqualTo("427.31");
            assertThat(notFound).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should find by coding system using LIKE match")
        void shouldFindByCodingSystem_usingLikeMatch() {
            createIcd9("493.0", "Extrinsic asthma");
            hibernateTemplate.flush();

            AbstractCodeSystemModel<?> result = dao.findByCodingSystem("493%");

            assertThat(result).isNotNull();
            assertThat(result.getCode()).isEqualTo("493.0");
        }

        @Test
        @Tag("query")
        @DisplayName("should return null from findByCodingSystem when no match")
        void shouldReturnNull_whenFindByCodingSystemHasNoMatch() {
            AbstractCodeSystemModel<?> result = dao.findByCodingSystem("ZZZZZ");

            assertThat(result).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should search code using searchCode method")
        void shouldSearchCode_usingSearchCodeMethod() {
            createIcd9("715.0", "Osteoarthrosis generalized");
            createIcd9("715.1", "Osteoarthrosis localized");
            hibernateTemplate.flush();

            List<Icd9> result = dao.searchCode("Osteoarthrosis");

            assertThat(result).hasSize(2);
        }
    }
}
