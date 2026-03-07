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
import io.github.carlos_emr.carlos.commn.model.FavoritesPrivilege;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link FavoritesPrivilegeDao} covering create,
 * getProviders, and findByProviderNo.
 *
 * <p>Migrated from legacy {@code FavoritesPrivilegeDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see FavoritesPrivilegeDao
 */
@DisplayName("FavoritesPrivilege Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("admin")
@Transactional
public class FavoritesPrivilegeDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private FavoritesPrivilegeDao dao;

    @Nested
    @DisplayName("Create operations")
    class CreateOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist favorites privilege with generated ID")
        void shouldPersistFavoritesPrivilege_whenValidDataProvided() {
            FavoritesPrivilege entity = new FavoritesPrivilege();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isPositive();
        }
    }

    @Nested
    @DisplayName("getProviders")
    class GetProviders {

        @Test
        @Tag("read")
        @DisplayName("should return provider numbers where open to public is true")
        void shouldReturnPublicProviders_whenOpenToPublicIsTrue() {
            String providerNo1 = "111";
            String providerNo2 = "222";
            String providerNo3 = "333";

            FavoritesPrivilege fp1 = new FavoritesPrivilege();
            EntityDataGenerator.generateTestDataForModelClass(fp1);
            fp1.setOpenToPublic(true);
            fp1.setProviderNo(providerNo1);
            dao.persist(fp1);

            FavoritesPrivilege fp2 = new FavoritesPrivilege();
            EntityDataGenerator.generateTestDataForModelClass(fp2);
            fp2.setOpenToPublic(false);
            fp2.setProviderNo(providerNo2);
            dao.persist(fp2);

            FavoritesPrivilege fp3 = new FavoritesPrivilege();
            EntityDataGenerator.generateTestDataForModelClass(fp3);
            fp3.setOpenToPublic(true);
            fp3.setProviderNo(providerNo3);
            dao.persist(fp3);

            List<String> result = dao.getProviders();

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(providerNo1, providerNo3);
        }
    }

    @Nested
    @DisplayName("findByProviderNo")
    class FindByProviderNo {

        @Test
        @Tag("read")
        @DisplayName("should return favorites privilege for the given provider")
        void shouldReturnFavoritesPrivilege_whenProviderNoMatches() {
            String providerNo1 = "111";
            String providerNo2 = "222";
            String providerNo3 = "333";

            FavoritesPrivilege fp1 = new FavoritesPrivilege();
            EntityDataGenerator.generateTestDataForModelClass(fp1);
            fp1.setProviderNo(providerNo1);
            dao.persist(fp1);

            FavoritesPrivilege fp2 = new FavoritesPrivilege();
            EntityDataGenerator.generateTestDataForModelClass(fp2);
            fp2.setProviderNo(providerNo2);
            dao.persist(fp2);

            FavoritesPrivilege fp3 = new FavoritesPrivilege();
            EntityDataGenerator.generateTestDataForModelClass(fp3);
            fp3.setProviderNo(providerNo3);
            dao.persist(fp3);

            FavoritesPrivilege result = dao.findByProviderNo(providerNo2);

            assertThat(result).isEqualTo(fp2);
        }
    }
}
