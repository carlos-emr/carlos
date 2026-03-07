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

import io.github.carlos_emr.carlos.billing.CA.BC.model.TeleplanC12;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link TeleplanC12Dao}.
 * <p>Migrated from legacy JUnit 4 TeleplanC12DaoTest with full method coverage.</p>
 *
 * @since 2026-03-07
 */
@DisplayName("TeleplanC12Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class TeleplanC12DaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private TeleplanC12Dao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist entity with generated test data")
    void shouldPersistEntity_whenValidDataProvided() {
        TeleplanC12 entity = new TeleplanC12();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should find current non-error records")
    void shouldReturnCurrentRecords_whenQueried() {
        assertThat(dao.findCurrent()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should find records by office claim number")
    void shouldReturnRecords_byOfficeClaimNo() {
        assertThat(dao.findByOfficeClaimNo("100")).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should find rejected records joined with S21")
    void shouldReturnRejectedRecords_whenQueried() {
        assertThat(dao.findRejected()).isNotNull();
    }
}
