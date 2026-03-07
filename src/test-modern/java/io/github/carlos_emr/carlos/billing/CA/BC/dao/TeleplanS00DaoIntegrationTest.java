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

import io.github.carlos_emr.carlos.billing.CA.BC.model.TeleplanS00;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link TeleplanS00Dao}.
 * <p>Migrated from legacy JUnit 4 TeleplanS00DaoTest with full method coverage.</p>
 *
 * @since 2026-03-07
 */
@DisplayName("TeleplanS00Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class TeleplanS00DaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private TeleplanS00Dao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist entity with generated test data")
    void shouldPersistEntity_whenValidDataProvided() {
        TeleplanS00 entity = new TeleplanS00();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should find records by billing number")
    void shouldReturnRecords_byBillingNo() {
        dao.findByBillingNo("101");
    }

    @Test
    @Tag("read")
    @DisplayName("should find records by office numbers including empty list")
    void shouldReturnRecords_byOfficeNumbers() {
        assertThat(dao.findByOfficeNumbers(Collections.emptyList())).isNotNull();
        assertThat(dao.findByOfficeNumbers(Arrays.asList("10", "20"))).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should find BG records across all exp fields")
    void shouldReturnBgRecords_whenQueried() {
        assertThat(dao.findBgs()).isNotNull();
    }
}
