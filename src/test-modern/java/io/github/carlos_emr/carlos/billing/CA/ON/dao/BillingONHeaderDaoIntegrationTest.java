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
package io.github.carlos_emr.carlos.billing.CA.ON.dao;

import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONHeader;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BillingONHeaderDao}.
 *
 * <p>Tests persist and findByDiskIdAndProviderRegNum methods with
 * meaningful assertions including filtering verification.</p>
 *
 * @since 2026-03-07
 * @see BillingONHeaderDao
 */
@DisplayName("BillingONHeaderDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-on")
@Transactional
public class BillingONHeaderDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingONHeaderDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist entity with generated ID")
    void shouldPersistEntity_whenValidDataProvided() {
        BillingONHeader entity = new BillingONHeader();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should find headers by disk ID and provider registration number")
    void shouldFindByDiskIdAndProviderRegNum_whenMatchingRecordsExist() {
        BillingONHeader match = new BillingONHeader();
        EntityDataGenerator.generateTestDataForModelClass(match);
        match.setDiskId(100);
        match.setProviderRegNum("REG001");
        dao.persist(match);

        BillingONHeader wrongDisk = new BillingONHeader();
        EntityDataGenerator.generateTestDataForModelClass(wrongDisk);
        wrongDisk.setDiskId(999);
        wrongDisk.setProviderRegNum("REG001");
        dao.persist(wrongDisk);

        BillingONHeader wrongProvider = new BillingONHeader();
        EntityDataGenerator.generateTestDataForModelClass(wrongProvider);
        wrongProvider.setDiskId(100);
        wrongProvider.setProviderRegNum("REG002");
        dao.persist(wrongProvider);

        List<BillingONHeader> results = dao.findByDiskIdAndProviderRegNum(100, "REG001");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(match.getId());
        assertThat(results.get(0).getDiskId()).isEqualTo(100);
        assertThat(results.get(0).getProviderRegNum()).isEqualTo("REG001");
    }

    @Test
    @Tag("read")
    @DisplayName("should return multiple headers when several match disk ID and provider")
    void shouldReturnMultipleHeaders_whenSeveralMatchDiskIdAndProvider() {
        BillingONHeader h1 = new BillingONHeader();
        EntityDataGenerator.generateTestDataForModelClass(h1);
        h1.setDiskId(200);
        h1.setProviderRegNum("REG100");
        dao.persist(h1);

        BillingONHeader h2 = new BillingONHeader();
        EntityDataGenerator.generateTestDataForModelClass(h2);
        h2.setDiskId(200);
        h2.setProviderRegNum("REG100");
        dao.persist(h2);

        List<BillingONHeader> results = dao.findByDiskIdAndProviderRegNum(200, "REG100");

        assertThat(results).hasSize(2);
        assertThat(results).extracting(BillingONHeader::getId)
                .containsExactlyInAnyOrder(h1.getId(), h2.getId());
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when no headers match criteria")
    void shouldReturnEmptyList_whenNoHeadersMatchCriteria() {
        BillingONHeader entity = new BillingONHeader();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setDiskId(300);
        entity.setProviderRegNum("REG300");
        dao.persist(entity);

        List<BillingONHeader> results = dao.findByDiskIdAndProviderRegNum(300, "NOMATCH");

        assertThat(results).isEmpty();
    }
}
