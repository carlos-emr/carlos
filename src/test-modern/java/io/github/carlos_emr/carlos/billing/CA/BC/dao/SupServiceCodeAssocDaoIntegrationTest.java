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
package io.github.carlos_emr.carlos.billing.CA.BC.dao;

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.billing.CA.BC.model.BillingTrayFee;
import io.github.carlos_emr.carlos.billings.ca.bc.data.SupServiceCodeAssocDAO;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link SupServiceCodeAssocDAO}.
 * <p>Migrated from legacy JUnit 4 / DaoTestFixtures.</p>
 * @since 2026-03-07
 */
@DisplayName("SupServiceCodeAssoc Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class SupServiceCodeAssocDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private SupServiceCodeAssocDAO supServiceCodeAssocDAO;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist BillingTrayFee entity with generated ID")
        void shouldPersist_whenValidDataProvided() {
            BillingTrayFee entity = new BillingTrayFee("SVC001", "TRAY001");
            supServiceCodeAssocDAO.persist(entity);
            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find BillingTrayFee entity by ID with correct fields")
        void shouldReturnMatchingEntity_whenFoundById() {
            BillingTrayFee saved = new BillingTrayFee("SVC100", "TRAY100");
            supServiceCodeAssocDAO.persist(saved);

            BillingTrayFee found = supServiceCodeAssocDAO.find(saved.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getBillingServiceNo()).isEqualTo("SVC100");
            assertThat(found.getBillingServiceTrayNo()).isEqualTo("TRAY100");
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when entity not found by ID")
        void shouldReturnNull_whenEntityNotFound() {
            BillingTrayFee found = supServiceCodeAssocDAO.find(-999);
            assertThat(found).isNull();
        }

        @Test
        @Tag("update")
        @DisplayName("should update BillingTrayFee fields after merge")
        void shouldUpdateFields_whenMerged() {
            BillingTrayFee entity = new BillingTrayFee("SVC200", "TRAY200");
            supServiceCodeAssocDAO.persist(entity);

            entity.setBillingServiceTrayNo("TRAY999");
            supServiceCodeAssocDAO.merge(entity);
            supServiceCodeAssocDAO.flush();

            BillingTrayFee found = supServiceCodeAssocDAO.find(entity.getId());
            assertThat(found.getBillingServiceTrayNo()).isEqualTo("TRAY999");
        }

        @Test
        @Tag("delete")
        @DisplayName("should remove entity from database")
        void shouldRemoveEntity_whenDeleted() {
            BillingTrayFee entity = new BillingTrayFee("SVC300", "TRAY300");
            supServiceCodeAssocDAO.persist(entity);
            Integer savedId = entity.getId();
            assertThat(savedId).isNotNull();

            supServiceCodeAssocDAO.remove(entity);
            supServiceCodeAssocDAO.flush();

            BillingTrayFee found = supServiceCodeAssocDAO.find(savedId);
            assertThat(found).isNull();
        }
    }

    @Nested
    @DisplayName("deleteServiceCodeAssociation")
    class DeleteServiceCodeAssociation {

        @Test
        @Tag("delete")
        @DisplayName("should delete association by string ID")
        void shouldDeleteAssociation_whenValidIdProvided() {
            BillingTrayFee entity = new BillingTrayFee("SVC400", "TRAY400");
            supServiceCodeAssocDAO.persist(entity);
            supServiceCodeAssocDAO.flush();
            Integer savedId = entity.getId();

            supServiceCodeAssocDAO.deleteServiceCodeAssociation(String.valueOf(savedId));
            supServiceCodeAssocDAO.flush();

            BillingTrayFee found = supServiceCodeAssocDAO.find(savedId);
            assertThat(found).isNull();
        }
    }
}
