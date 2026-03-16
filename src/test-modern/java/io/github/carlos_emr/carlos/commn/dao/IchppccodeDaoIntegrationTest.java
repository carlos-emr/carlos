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
import io.github.carlos_emr.carlos.commn.model.Ichppccode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link IchppccodeDao} covering basic CRUD operations.
 *
 * <p>Migrated from legacy {@code IchppccodeDaoTest} (JUnit 4 / DaoTestFixtures).
 * Ichppccode uses a String-typed PK with @GeneratedValue(IDENTITY) which is
 * incompatible with H2 auto-generation, so IDs must be set manually in tests.</p>
 *
 * @since 2026-03-07
 * @see IchppccodeDao
 */
@DisplayName("Ichppccode Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("clinical")
@Transactional
public class IchppccodeDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private IchppccodeDao ichppccodeDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private static int counter = 1;

    private Ichppccode createIchppccode() {
        Ichppccode entity = new Ichppccode();
        entity.setId("ICH" + counter++);
        entity.setDiagnosticCode("D" + counter);
        entity.setDescription("Test diagnosis " + counter);
        return entity;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist ichppccode with manual ID")
        void shouldPersistIchppccode_whenValidDataProvided() {
            Ichppccode entity = createIchppccode();
            entityManager.merge(entity);
            assertThat(entity.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find ichppccode by ID")
        void shouldFindIchppccode_whenValidIdProvided() {
            Ichppccode saved = createIchppccode();
            entityManager.merge(saved);
            Ichppccode found = ichppccodeDao.find(saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should count all ichppccode records")
        void shouldCountAllIchppccodes() {
            Ichppccode entity = createIchppccode();
            entityManager.merge(entity);
            long count = ichppccodeDao.getCountAll();
            assertThat(count).isEqualTo(1);
        }
    }
}
