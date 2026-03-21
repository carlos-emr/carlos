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
package io.github.carlos_emr.carlos.billing.CA.ON.dao;

import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONFavourite;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BillingONFavouriteDao}.
 *
 * <p>Tests persist, findByName, findByNameAndProviderNo, and findCurrent
 * methods with meaningful assertions.</p>
 *
 * @since 2026-03-07
 * @see BillingONFavouriteDao
 */
@DisplayName("BillingONFavouriteDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-on")
@Transactional
public class BillingONFavouriteDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingONFavouriteDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist entity with generated ID")
    void shouldPersistEntity_whenValidDataProvided() throws Exception {
        BillingONFavourite entity = new BillingONFavourite();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        assertThat(entity.getId()).isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should find favourites by name")
    void shouldFindByName_whenMatchingRecordsExist() throws Exception {
        BillingONFavourite fav1 = new BillingONFavourite();
        EntityDataGenerator.generateTestDataForModelClass(fav1);
        fav1.setName("TestFav");
        fav1.setProviderNo("100");
        dao.persist(fav1);

        BillingONFavourite fav2 = new BillingONFavourite();
        EntityDataGenerator.generateTestDataForModelClass(fav2);
        fav2.setName("TestFav");
        fav2.setProviderNo("200");
        dao.persist(fav2);

        BillingONFavourite other = new BillingONFavourite();
        EntityDataGenerator.generateTestDataForModelClass(other);
        other.setName("OtherFav");
        other.setProviderNo("300");
        dao.persist(other);

        List<BillingONFavourite> results = dao.findByName("TestFav");

        assertThat(results).hasSize(2);
        assertThat(results).extracting(BillingONFavourite::getName)
                .containsOnly("TestFav");
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when no favourites match name")
    void shouldReturnEmptyList_whenNoFavouritesMatchName() throws Exception {
        BillingONFavourite fav = new BillingONFavourite();
        EntityDataGenerator.generateTestDataForModelClass(fav);
        fav.setName("Existing");
        dao.persist(fav);

        List<BillingONFavourite> results = dao.findByName("NonExistent");

        assertThat(results).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should find favourites by name and provider number")
    void shouldFindByNameAndProviderNo_whenMatchingRecordExists() throws Exception {
        BillingONFavourite fav1 = new BillingONFavourite();
        EntityDataGenerator.generateTestDataForModelClass(fav1);
        fav1.setName("SharedFav");
        fav1.setProviderNo("100");
        dao.persist(fav1);

        BillingONFavourite fav2 = new BillingONFavourite();
        EntityDataGenerator.generateTestDataForModelClass(fav2);
        fav2.setName("SharedFav");
        fav2.setProviderNo("200");
        dao.persist(fav2);

        List<BillingONFavourite> results = dao.findByNameAndProviderNo("SharedFav", "100");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getProviderNo()).isEqualTo("100");
        assertThat(results.get(0).getName()).isEqualTo("SharedFav");
    }

    @Test
    @Tag("read")
    @DisplayName("should find only current (non-deleted) favourites")
    void shouldFindCurrent_whenMixOfDeletedAndActiveExist() throws Exception {
        BillingONFavourite active1 = new BillingONFavourite();
        EntityDataGenerator.generateTestDataForModelClass(active1);
        active1.setDeleted(0);
        active1.setName("Active1");
        dao.persist(active1);

        BillingONFavourite active2 = new BillingONFavourite();
        EntityDataGenerator.generateTestDataForModelClass(active2);
        active2.setDeleted(0);
        active2.setName("Active2");
        dao.persist(active2);

        BillingONFavourite deleted = new BillingONFavourite();
        EntityDataGenerator.generateTestDataForModelClass(deleted);
        deleted.setDeleted(1);
        deleted.setName("Deleted");
        dao.persist(deleted);

        List<BillingONFavourite> results = dao.findCurrent();

        assertThat(results).hasSize(2);
        assertThat(results).extracting(BillingONFavourite::getDeleted)
                .containsOnly(0);
        assertThat(results).extracting(BillingONFavourite::getId)
                .containsExactlyInAnyOrder(active1.getId(), active2.getId());
    }
}
