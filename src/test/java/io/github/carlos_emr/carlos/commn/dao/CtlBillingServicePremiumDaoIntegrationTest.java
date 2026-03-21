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
import io.github.carlos_emr.carlos.commn.model.CtlBillingServicePremium;
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
 * Integration tests for {@link CtlBillingServicePremiumDao} covering full method coverage
 * matching the legacy {@code CtlBillingServicePremiumDaoTest}.
 *
 * <p>Tests cover persist (create) and findByServiceCode operations.</p>
 *
 * @since 2026-03-07
 * @see CtlBillingServicePremiumDao
 */
@DisplayName("CtlBillingServicePremium Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class CtlBillingServicePremiumDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private CtlBillingServicePremiumDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist entity and assign generated ID")
    void shouldPersistEntity_withGeneratedId() throws Exception {
        CtlBillingServicePremium entity = new CtlBillingServicePremium();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);

        assertThat(entity.getId()).isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should return premiums matching service code")
    void shouldReturnMatchingPremiums_whenSearchingByServiceCode() throws Exception {
        String serviceCode1 = "alpha";
        String serviceCode2 = "bravo";

        CtlBillingServicePremium cBSP1 = new CtlBillingServicePremium();
        EntityDataGenerator.generateTestDataForModelClass(cBSP1);
        cBSP1.setServiceCode(serviceCode1);
        dao.persist(cBSP1);

        CtlBillingServicePremium cBSP2 = new CtlBillingServicePremium();
        EntityDataGenerator.generateTestDataForModelClass(cBSP2);
        cBSP2.setServiceCode(serviceCode1);
        dao.persist(cBSP2);

        CtlBillingServicePremium cBSP3 = new CtlBillingServicePremium();
        EntityDataGenerator.generateTestDataForModelClass(cBSP3);
        cBSP3.setServiceCode(serviceCode2);
        dao.persist(cBSP3);

        CtlBillingServicePremium cBSP4 = new CtlBillingServicePremium();
        EntityDataGenerator.generateTestDataForModelClass(cBSP4);
        cBSP4.setServiceCode(serviceCode1);
        dao.persist(cBSP4);

        List<CtlBillingServicePremium> expectedResult = Arrays.asList(cBSP1, cBSP2, cBSP4);
        List<CtlBillingServicePremium> result = dao.findByServiceCode(serviceCode1);

        assertThat(result).hasSameSizeAs(expectedResult);
        assertThat(result).containsExactlyElementsOf(expectedResult);
    }
}
