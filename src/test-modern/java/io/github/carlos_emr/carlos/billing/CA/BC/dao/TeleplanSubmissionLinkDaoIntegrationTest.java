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

import io.github.carlos_emr.carlos.billing.CA.BC.model.TeleplanSubmissionLink;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link TeleplanSubmissionLinkDao}.
 * <p>Tests inherited CRUD operations from AbstractDaoImpl since this DAO
 * has no custom query methods.</p>
 *
 * @since 2026-03-07
 */
@DisplayName("TeleplanSubmissionLink Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class TeleplanSubmissionLinkDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private TeleplanSubmissionLinkDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist entity with generated ID")
    void shouldPersistEntity_whenValidDataProvided() {
        TeleplanSubmissionLink entity = new TeleplanSubmissionLink();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setBillActivityId(100);
        entity.setBillingMasterNo(200);
        dao.persist(entity);
        assertThat(entity.getId()).isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should find entity by ID with correct field values")
    void shouldReturnEntity_whenValidIdProvided() {
        TeleplanSubmissionLink saved = new TeleplanSubmissionLink();
        EntityDataGenerator.generateTestDataForModelClass(saved);
        saved.setBillActivityId(555);
        saved.setBillingMasterNo(777);
        dao.persist(saved);

        TeleplanSubmissionLink found = dao.find(saved.getId());
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getBillActivityId()).isEqualTo(555);
        assertThat(found.getBillingMasterNo()).isEqualTo(777);
    }

    @Test
    @Tag("read")
    @DisplayName("should return null when entity not found by ID")
    void shouldReturnNull_whenEntityNotFound() {
        TeleplanSubmissionLink found = dao.find(999999);
        assertThat(found).isNull();
    }

    @Test
    @Tag("update")
    @DisplayName("should merge updated entity with new values")
    void shouldMergeUpdatedEntity_whenFieldsChanged() {
        TeleplanSubmissionLink entity = new TeleplanSubmissionLink();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setBillActivityId(100);
        entity.setBillingMasterNo(200);
        dao.persist(entity);

        entity.setBillActivityId(300);
        entity.setBillingMasterNo(400);
        dao.merge(entity);

        TeleplanSubmissionLink found = dao.find(entity.getId());
        assertThat(found.getBillActivityId()).isEqualTo(300);
        assertThat(found.getBillingMasterNo()).isEqualTo(400);
    }
}
