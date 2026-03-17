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

import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.CountryCode;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link CountryCodeDao} covering create,
 * getAllCountryCodes, and getCountryCode.
 *
 * <p>Migrated from legacy {@code CountryCodeDaoTest}
 * (JUnit 4 / DaoTestFixtures) with exact same test logic and assertions.</p>
 *
 * @since 2026-03-07
 * @see CountryCodeDao
 */
@DisplayName("CountryCodeDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("admin")
@Transactional
public class CountryCodeDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private CountryCodeDao dao;

    @Nested
    @DisplayName("create tests")
    @Tag("create")
    class Create {

        @Test
        @DisplayName("should persist entity with generated id")
        void shouldPersistEntity_withGeneratedId() throws Exception {
            CountryCode entity = new CountryCode();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);
            assertThat(entity.getId()).isPositive();
        }
    }

    @Nested
    @DisplayName("getAllCountryCodes tests")
    @Tag("read")
    class GetAllCountryCodes {

        @Test
        @DisplayName("should return increased count after persisting new country code")
        void shouldReturnIncreasedCount_afterPersistingNewCountryCode() throws Exception {
            int initialSize = dao.findAll().size();

            CountryCode entity = new CountryCode();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);
            hibernateTemplate.flush();

            assertThat(dao.findAll()).hasSize(initialSize + 1);
        }
    }

    @Nested
    @DisplayName("getCountryCode tests")
    @Tag("read")
    class GetCountryCode {

        @Test
        @DisplayName("should return country with correct name when code is CA")
        void shouldReturnCountryWithCorrectName_whenCodeIsCA() throws Exception {
            CountryCode country = new CountryCode();
            EntityDataGenerator.generateTestDataForModelClass(country);
            country.setCountryId("CA");
            country.setCountryName("Canada");
            dao.persist(country);
            hibernateTemplate.flush();

            CountryCode result = dao.getCountryCode("CA");
            assertThat(result.getCountryName()).isEqualTo("Canada");
        }
    }
}
