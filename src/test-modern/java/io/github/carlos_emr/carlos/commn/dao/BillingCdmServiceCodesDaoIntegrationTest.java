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
import io.github.carlos_emr.carlos.commn.model.BillingCdmServiceCodes;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link BillingCdmServiceCodesDao} covering full method coverage
 * matching the legacy {@code BillingCdmServiceCodesDaoTest}.
 *
 * <p>Tests cover persist (create) and findAll operations.</p>
 *
 * @since 2026-03-07
 * @see BillingCdmServiceCodesDao
 */
@DisplayName("BillingCdmServiceCodes Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class BillingCdmServiceCodesDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingCdmServiceCodesDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist entity with null ID and assign generated ID")
    void shouldPersistEntity_withGeneratedId() {
        BillingCdmServiceCodes entity = new BillingCdmServiceCodes();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setId(null);
        dao.persist(entity);

        assertThat(entity.getId()).isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should return all billing CDM service codes")
    void shouldReturnAllCodes_whenFindAllCalled() {
        BillingCdmServiceCodes bCSC1 = new BillingCdmServiceCodes();
        EntityDataGenerator.generateTestDataForModelClass(bCSC1);
        dao.persist(bCSC1);

        BillingCdmServiceCodes bCSC2 = new BillingCdmServiceCodes();
        EntityDataGenerator.generateTestDataForModelClass(bCSC2);
        dao.persist(bCSC2);

        BillingCdmServiceCodes bCSC3 = new BillingCdmServiceCodes();
        EntityDataGenerator.generateTestDataForModelClass(bCSC3);
        dao.persist(bCSC3);

        List<BillingCdmServiceCodes> expectedResult = Arrays.asList(bCSC1, bCSC2, bCSC3);
        List<BillingCdmServiceCodes> result = dao.findAll();

        assertThat(result).hasSameSizeAs(expectedResult);
        assertThat(result).containsExactlyElementsOf(expectedResult);
    }
}
