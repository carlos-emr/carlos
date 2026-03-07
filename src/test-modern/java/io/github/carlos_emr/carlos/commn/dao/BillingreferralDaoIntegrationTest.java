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
import io.github.carlos_emr.carlos.commn.model.Billingreferral;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link BillingreferralDao} covering full method coverage
 * matching the legacy {@code BillingreferralDaoTest}.
 *
 * <p>Tests cover updateBillingreferral (create) and searchReferralCode operations.</p>
 *
 * @since 2026-03-07
 * @see BillingreferralDao
 */
@DisplayName("Billingreferral Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class BillingreferralDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingreferralDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist entity via updateBillingreferral and assign generated ID")
    void shouldPersistEntity_whenUpdateBillingreferralCalled() {
        Billingreferral entity = new Billingreferral();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setBillingreferralNo(null);
        dao.updateBillingreferral(entity);

        assertThat(entity.getBillingreferralNo()).isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should find referral by referral code, last name, and first name")
    void shouldReturnResults_whenSearchingByReferralCode() {
        Billingreferral entity = new Billingreferral();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setBillingreferralNo(null);
        entity.setReferralNo("123456");
        entity.setLastName("Smith");
        entity.setFirstName("John");
        dao.updateBillingreferral(entity);

        assertThat(dao.searchReferralCode("123456", "123456", "123456",
                "Smith", "John", "Smith", "John", "Smith", "John")).hasSize(1);
    }
}
