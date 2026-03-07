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
import io.github.carlos_emr.carlos.commn.model.CtlBillingServiceAgeRules;
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
 * Integration tests for {@link CtlBillingServiceAgeRulesDao} covering full method coverage
 * matching the legacy {@code CtlBillingServiceAgeRulesDaoTest}.
 *
 * <p>Tests cover persist (create) and findByServiceCode operations.
 * Note: The legacy test persists a CtlBillingServiceSexRules entity in the create test,
 * which is replicated here for exact parity.</p>
 *
 * @since 2026-03-07
 * @see CtlBillingServiceAgeRulesDao
 */
@DisplayName("CtlBillingServiceAgeRules Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class CtlBillingServiceAgeRulesDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private CtlBillingServiceAgeRulesDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist sex rules entity via age rules dao and assign generated ID")
    void shouldPersistSexRulesEntity_withGeneratedId() {
        CtlBillingServiceSexRules entity = new CtlBillingServiceSexRules();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setId(null);
        dao.persist(entity);

        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return age rules matching service code")
    void shouldReturnMatchingRules_whenSearchingByServiceCode() {
        String serviceCode1 = "alpha";
        String serviceCode2 = "bravo";

        CtlBillingServiceAgeRules cBSAR1 = new CtlBillingServiceAgeRules();
        EntityDataGenerator.generateTestDataForModelClass(cBSAR1);
        cBSAR1.setServiceCode(serviceCode1);
        dao.persist(cBSAR1);

        CtlBillingServiceAgeRules cBSAR2 = new CtlBillingServiceAgeRules();
        EntityDataGenerator.generateTestDataForModelClass(cBSAR2);
        cBSAR2.setServiceCode(serviceCode1);
        dao.persist(cBSAR2);

        CtlBillingServiceAgeRules cBSAR3 = new CtlBillingServiceAgeRules();
        EntityDataGenerator.generateTestDataForModelClass(cBSAR3);
        cBSAR3.setServiceCode(serviceCode2);
        dao.persist(cBSAR3);

        CtlBillingServiceAgeRules cBSAR4 = new CtlBillingServiceAgeRules();
        EntityDataGenerator.generateTestDataForModelClass(cBSAR4);
        cBSAR4.setServiceCode(serviceCode1);
        dao.persist(cBSAR4);

        List<CtlBillingServiceAgeRules> expectedResult = Arrays.asList(cBSAR1, cBSAR2, cBSAR4);
        List<CtlBillingServiceAgeRules> result = dao.findByServiceCode(serviceCode1);

        assertThat(result).hasSameSizeAs(expectedResult);
        assertThat(result).containsExactlyElementsOf(expectedResult);
    }
}
