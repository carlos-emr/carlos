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

import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONFilename;
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
 * Integration tests for {@link BillingONFilenameDao}.
 *
 * <p>Tests persist, findByDiskId, findByDiskIdAndStatus, findByDiskIdAndProvider,
 * and findCurrentByDiskId methods with meaningful assertions.</p>
 *
 * @since 2026-03-07
 * @see BillingONFilenameDao
 */
@DisplayName("BillingONFilenameDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-on")
@Transactional
public class BillingONFilenameDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingONFilenameDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist entity with generated ID")
    void shouldPersistEntity_whenValidDataProvided() throws Exception {
        BillingONFilename entity = new BillingONFilename();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        assertThat(entity.getId()).isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should find all filenames by disk ID")
    void shouldFindByDiskId_whenMatchingRecordsExist() throws Exception {
        BillingONFilename f1 = new BillingONFilename();
        EntityDataGenerator.generateTestDataForModelClass(f1);
        f1.setDiskId(100);
        f1.setStatus("A");
        dao.persist(f1);

        BillingONFilename f2 = new BillingONFilename();
        EntityDataGenerator.generateTestDataForModelClass(f2);
        f2.setDiskId(100);
        f2.setStatus("B");
        dao.persist(f2);

        BillingONFilename other = new BillingONFilename();
        EntityDataGenerator.generateTestDataForModelClass(other);
        other.setDiskId(999);
        other.setStatus("A");
        dao.persist(other);

        List<BillingONFilename> results = dao.findByDiskId(100);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(BillingONFilename::getDiskId)
                .containsOnly(100);
    }

    @Test
    @Tag("read")
    @DisplayName("should find filenames by disk ID and status")
    void shouldFindByDiskIdAndStatus_whenMatchingRecordsExist() throws Exception {
        BillingONFilename active = new BillingONFilename();
        EntityDataGenerator.generateTestDataForModelClass(active);
        active.setDiskId(200);
        active.setStatus("A");
        dao.persist(active);

        BillingONFilename deleted = new BillingONFilename();
        EntityDataGenerator.generateTestDataForModelClass(deleted);
        deleted.setDiskId(200);
        deleted.setStatus("D");
        dao.persist(deleted);

        List<BillingONFilename> results = dao.findByDiskIdAndStatus(200, "A");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(active.getId());
        assertThat(results.get(0).getStatus()).isEqualTo("A");
    }

    @Test
    @Tag("read")
    @DisplayName("should find filenames by disk ID and provider")
    void shouldFindByDiskIdAndProvider_whenMatchingRecordsExist() throws Exception {
        BillingONFilename match = new BillingONFilename();
        EntityDataGenerator.generateTestDataForModelClass(match);
        match.setDiskId(300);
        match.setProviderNo("P001");
        dao.persist(match);

        BillingONFilename otherProvider = new BillingONFilename();
        EntityDataGenerator.generateTestDataForModelClass(otherProvider);
        otherProvider.setDiskId(300);
        otherProvider.setProviderNo("P002");
        dao.persist(otherProvider);

        List<BillingONFilename> results = dao.findByDiskIdAndProvider(300, "P001");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getProviderNo()).isEqualTo("P001");
        assertThat(results.get(0).getDiskId()).isEqualTo(300);
    }

    @Test
    @Tag("read")
    @DisplayName("should find current (non-deleted) filenames by disk ID")
    void shouldFindCurrentByDiskId_whenMixOfDeletedAndActiveExist() throws Exception {
        BillingONFilename active = new BillingONFilename();
        EntityDataGenerator.generateTestDataForModelClass(active);
        active.setDiskId(400);
        active.setStatus("A");
        dao.persist(active);

        BillingONFilename submitted = new BillingONFilename();
        EntityDataGenerator.generateTestDataForModelClass(submitted);
        submitted.setDiskId(400);
        submitted.setStatus("S");
        dao.persist(submitted);

        BillingONFilename deleted = new BillingONFilename();
        EntityDataGenerator.generateTestDataForModelClass(deleted);
        deleted.setDiskId(400);
        deleted.setStatus("D");
        dao.persist(deleted);

        List<BillingONFilename> results = dao.findCurrentByDiskId(400);

        // findCurrentByDiskId excludes status "D"
        assertThat(results).hasSize(2);
        assertThat(results).extracting(BillingONFilename::getStatus)
                .doesNotContain("D");
        assertThat(results).extracting(BillingONFilename::getId)
                .containsExactlyInAnyOrder(active.getId(), submitted.getId());
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when no filenames match disk ID")
    void shouldReturnEmptyList_whenNoFilenamesMatchDiskId() throws Exception {
        BillingONFilename entity = new BillingONFilename();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setDiskId(500);
        dao.persist(entity);

        List<BillingONFilename> results = dao.findByDiskId(9999);

        assertThat(results).isEmpty();
    }
}
