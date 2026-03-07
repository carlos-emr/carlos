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
import io.github.carlos_emr.carlos.commn.model.CtlBillingType;
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
 * Integration tests for {@link CtlBillingTypeDao} covering full method coverage
 * matching the legacy {@code CtlBillingTypeDaoTest}.
 *
 * <p>Tests cover persist (create) and findByServiceType operations.</p>
 *
 * @since 2026-03-07
 * @see CtlBillingTypeDao
 */
@DisplayName("CtlBillingType Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class CtlBillingTypeDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private CtlBillingTypeDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist entity with explicit string ID")
    void shouldPersistEntity_withExplicitStringId() {
        CtlBillingType entity = new CtlBillingType();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setId("test");
        dao.persist(entity);

        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return billing types matching service type")
    void shouldReturnMatchingTypes_whenSearchingByServiceType() {
        String id1 = "alpha";
        String id2 = "bravo";

        CtlBillingType cBT1 = new CtlBillingType();
        EntityDataGenerator.generateTestDataForModelClass(cBT1);
        cBT1.setId(id1);
        dao.persist(cBT1);

        CtlBillingType cBT2 = new CtlBillingType();
        EntityDataGenerator.generateTestDataForModelClass(cBT2);
        cBT2.setId(id2);
        dao.persist(cBT2);

        List<CtlBillingType> expectedResult = Arrays.asList(cBT1);
        List<CtlBillingType> result = dao.findByServiceType(id1);

        assertThat(result).hasSameSizeAs(expectedResult);
        assertThat(result).containsExactlyElementsOf(expectedResult);
    }
}
