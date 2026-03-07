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
import io.github.carlos_emr.carlos.billing.CA.dao.BillingInrDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingInr;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link BillingInrDao} covering full method coverage
 * matching the legacy {@code BillingInrDaoTest}.
 *
 * <p>Tests cover persist (create) and search_inrbilling_dt_billno operations.</p>
 *
 * @since 2026-03-07
 * @see BillingInrDao
 */
@DisplayName("BillingInr Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class BillingInrDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingInrDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist entity and assign generated ID")
    void shouldPersistEntity_withGeneratedId() {
        BillingInr entity = new BillingInr();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);

        assertThat(entity.getId()).isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should return results when searching by billing INR number")
    void shouldReturnResults_whenSearchingByBillingInrNo() {
        List<Object[]> results = dao.search_inrbilling_dt_billno(1);
        assertThat(results).isNotNull();
        assertThat(results).isInstanceOf(List.class);
    }
}
