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

import io.github.carlos_emr.carlos.billing.CA.ON.model.Billing3rdPartyAddress;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link Billing3rdPartyAddressDao} covering create,
 * findByCompanyName, and findAddresses.
 *
 * <p>Migrated from legacy {@code Billing3rdPartyAddressDaoTest}
 * (JUnit 4 / DaoTestFixtures) with exact same test logic and assertions.</p>
 *
 * @since 2026-03-07
 * @see Billing3rdPartyAddressDao
 */
@DisplayName("Billing3rdPartyAddressDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing")
@Transactional
public class Billing3rdPartyAddressDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private Billing3rdPartyAddressDao dao;

    @Nested
    @DisplayName("create tests")
    @Tag("create")
    class Create {

        @Test
        @DisplayName("should persist entity with generated id")
        void shouldPersistEntity_withGeneratedId() {
            Billing3rdPartyAddress entity = new Billing3rdPartyAddress();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);
            assertThat(entity.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("findByCompanyName tests")
    @Tag("read")
    class FindByCompanyName {

        @Test
        @DisplayName("should return all addresses matching company name")
        void shouldReturnAllAddresses_whenCompanyNameMatches() {
            String companyName1 = "sigma";
            String companyName2 = "epsilon";

            Billing3rdPartyAddress addr1 = new Billing3rdPartyAddress();
            EntityDataGenerator.generateTestDataForModelClass(addr1);
            addr1.setCompanyName(companyName1);
            dao.persist(addr1);

            Billing3rdPartyAddress addr2 = new Billing3rdPartyAddress();
            EntityDataGenerator.generateTestDataForModelClass(addr2);
            addr2.setCompanyName(companyName2);
            dao.persist(addr2);

            Billing3rdPartyAddress addr3 = new Billing3rdPartyAddress();
            EntityDataGenerator.generateTestDataForModelClass(addr3);
            addr3.setCompanyName(companyName2);
            dao.persist(addr3);

            Billing3rdPartyAddress addr4 = new Billing3rdPartyAddress();
            EntityDataGenerator.generateTestDataForModelClass(addr4);
            addr4.setCompanyName(companyName1);
            dao.persist(addr4);

            Billing3rdPartyAddress addr5 = new Billing3rdPartyAddress();
            EntityDataGenerator.generateTestDataForModelClass(addr5);
            addr5.setCompanyName(companyName1);
            dao.persist(addr5);
            hibernateTemplate.flush();

            List<Billing3rdPartyAddress> expectedResult = Arrays.asList(addr1, addr4, addr5);
            List<Billing3rdPartyAddress> result = dao.findByCompanyName(companyName1);

            assertThat(result).hasSameSizeAs(expectedResult);
            for (int i = 0; i < expectedResult.size(); i++) {
                assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
            }
        }

        @Test
        @DisplayName("should return empty list when no addresses match company name")
        void shouldReturnEmptyList_whenNoCompanyNameMatches() {
            Billing3rdPartyAddress addr = new Billing3rdPartyAddress();
            EntityDataGenerator.generateTestDataForModelClass(addr);
            addr.setCompanyName("ExistingCorp");
            dao.persist(addr);
            hibernateTemplate.flush();

            List<Billing3rdPartyAddress> result = dao.findByCompanyName("NonExistentCorp");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should not return addresses with different company name")
        void shouldExcludeAddresses_whenCompanyNameDiffers() {
            String targetName = "TargetCorp";
            String otherName = "OtherCorp";

            Billing3rdPartyAddress target = new Billing3rdPartyAddress();
            EntityDataGenerator.generateTestDataForModelClass(target);
            target.setCompanyName(targetName);
            dao.persist(target);

            Billing3rdPartyAddress other = new Billing3rdPartyAddress();
            EntityDataGenerator.generateTestDataForModelClass(other);
            other.setCompanyName(otherName);
            dao.persist(other);
            hibernateTemplate.flush();

            List<Billing3rdPartyAddress> result = dao.findByCompanyName(targetName);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCompanyName()).isEqualTo(targetName);
        }
    }

    @Nested
    @DisplayName("findAddresses tests")
    @Tag("read")
    class FindAddresses {

        @Test
        @DisplayName("should return empty list when no data exists and all parameters are null")
        void shouldReturnEmptyList_whenAllParametersAreNullAndNoData() {
            List<Billing3rdPartyAddress> result = dao.findAddresses(null, null, null, null, null);
            assertThat(result).isEmpty();
        }
    }
}
