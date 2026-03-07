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
import io.github.carlos_emr.carlos.commn.model.CtlBillingServiceSexRules;
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
 * Integration tests for {@link CtlBillingServiceSexRulesDao} covering full method coverage
 * matching the legacy {@code CtlBillingServiceSexRulesDaoTest}.
 *
 * <p>Tests cover persist (create) and findByServiceCode operations.</p>
 *
 * @since 2026-03-07
 * @see CtlBillingServiceSexRulesDao
 */
@DisplayName("CtlBillingServiceSexRules Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class CtlBillingServiceSexRulesDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private CtlBillingServiceSexRulesDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist entity and assign generated ID")
    void shouldPersistEntity_withGeneratedId() {
        CtlBillingServiceSexRules entity = new CtlBillingServiceSexRules();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setId(null);
        dao.persist(entity);

        assertThat(entity.getId()).isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should return sex rules matching service code")
    void shouldReturnMatchingRules_whenSearchingByServiceCode() {
        String serviceCode1 = "alpha";
        String serviceCode2 = "bravo";

        CtlBillingServiceSexRules cBSSR1 = new CtlBillingServiceSexRules();
        EntityDataGenerator.generateTestDataForModelClass(cBSSR1);
        cBSSR1.setServiceCode(serviceCode1);
        dao.persist(cBSSR1);

        CtlBillingServiceSexRules cBSSR2 = new CtlBillingServiceSexRules();
        EntityDataGenerator.generateTestDataForModelClass(cBSSR2);
        cBSSR2.setServiceCode(serviceCode1);
        dao.persist(cBSSR2);

        CtlBillingServiceSexRules cBSSR3 = new CtlBillingServiceSexRules();
        EntityDataGenerator.generateTestDataForModelClass(cBSSR3);
        cBSSR3.setServiceCode(serviceCode2);
        dao.persist(cBSSR3);

        CtlBillingServiceSexRules cBSSR4 = new CtlBillingServiceSexRules();
        EntityDataGenerator.generateTestDataForModelClass(cBSSR4);
        cBSSR4.setServiceCode(serviceCode1);
        dao.persist(cBSSR4);

        List<CtlBillingServiceSexRules> expectedResult = Arrays.asList(cBSSR1, cBSSR2, cBSSR4);
        List<CtlBillingServiceSexRules> result = dao.findByServiceCode(serviceCode1);

        assertThat(result).hasSameSizeAs(expectedResult);
        assertThat(result).containsExactlyElementsOf(expectedResult);
    }
}
