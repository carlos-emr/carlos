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
import io.github.carlos_emr.carlos.commn.model.ProviderInboxItem;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.PersistenceException;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ProviderInboxRoutingDao} covering basic persistence
 * and provider inbox routing operations.
 *
 * <p>Migrated from legacy {@code ProviderInboxRoutingDaoTest} (JUnit 4 / DaoTestFixtures)
 * with BDD-style naming and AssertJ assertions.</p>
 *
 * @since 2026-03-07
 * @see ProviderInboxRoutingDao
 */
@DisplayName("ProviderInboxRoutingDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("inbox")
@Transactional
public class ProviderInboxRoutingDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ProviderInboxRoutingDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist provider inbox item with generated ID")
    void shouldPersistProviderInboxItem_whenValidDataProvided() throws Exception {
        // Given
        ProviderInboxItem entity = new ProviderInboxItem();
        EntityDataGenerator.generateTestDataForModelClass(entity);

        // When
        dao.persist(entity);
        hibernateTemplate.flush();

        // Then
        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("create")
    @DisplayName("should not throw PersistenceException when adding to provider inbox")
    void shouldNotThrowPersistenceException_whenAddingToProviderInbox() {
        // PersistenceException indicates a JPA configuration problem.
        // The method may throw other exceptions due to missing prerequisite data,
        // but a PersistenceException specifically means the JPA mapping is broken.
        try {
            dao.addToProviderInbox("1", 1, LabResultData.DOCUMENT);
        } catch (PersistenceException e) {
            fail("PersistenceException indicates JPA configuration issue: " + e.getMessage());
        }
    }
}
