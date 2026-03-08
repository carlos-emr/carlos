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
import io.github.carlos_emr.carlos.casemgmt.model.ProviderExt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ProviderExtDao} covering provider extension
 * data CRUD and provider-specific queries.
 *
 * <p>Migrated from legacy {@code ProviderExtDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ProviderExtDao
 */
@DisplayName("ProviderExtDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("provider")
@Transactional
public class ProviderExtDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ProviderExtDao providerExtDao;

    private ProviderExt createProviderExt(String providerNo, String signature) {
        ProviderExt ext = new ProviderExt();
        ext.setProviderNo(providerNo);
        ext.setSignature(signature);
        providerExtDao.persist(ext);
        return ext;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist provider extension with provider number as ID")
        void shouldPersistProviderExt_whenValidDataProvided() {
            ProviderExt ext = createProviderExt("100001", "test-signature");
            assertThat(ext.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find provider extension by ID")
        void shouldFindProviderExt_whenValidIdProvided() {
            ProviderExt saved = createProviderExt("100002", "dark-sig");
            ProviderExt found = providerExtDao.find(saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getProviderNo()).isEqualTo("100002");
            assertThat(found.getSignature()).isEqualTo("dark-sig");
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should find all provider extensions")
        void shouldFindAllExtensions() {
            createProviderExt("200001", "sig1");
            createProviderExt("200002", "sig2");
            createProviderExt("200003", "sig3");

            List<ProviderExt> all = providerExtDao.findAll(0, 100);
            assertThat(all).hasSizeGreaterThanOrEqualTo(3);
        }

        @Test
        @Tag("query")
        @DisplayName("should count all provider extensions")
        void shouldCountAllExtensions() {
            createProviderExt("300001", "count-sig");
            long count = providerExtDao.getCountAll();
            assertThat(count).isEqualTo(1);
        }
    }
}
