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
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.commn.model.PreventionExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link PreventionExtDao} covering prevention
 * extension CRUD, prevention-ID-based lookups, and key-value queries.
 *
 * <p>Migrated from legacy {@code PreventionExtDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see PreventionExtDao
 */
@DisplayName("PreventionExtDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("prevention")
@Transactional
public class PreventionExtDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private PreventionExtDao preventionExtDao;

    @Autowired
    private PreventionDao preventionDao;

    private Prevention createPrevention(int demographicNo, String type) {
        Prevention prev = new Prevention();
        prev.setDemographicId(demographicNo);
        prev.setPreventionType(type);
        prev.setPreventionDate(new Date());
        prev.setProviderNo("999998");
        prev.setCreatorProviderNo("999998");
        prev.setDeleted(false);
        prev.setRefused(false);
        preventionDao.persist(prev);
        return prev;
    }

    private PreventionExt createExt(Integer preventionId, String keyVal, String val) {
        PreventionExt ext = new PreventionExt();
        ext.setPreventionId(preventionId);
        ext.setKeyval(keyVal);
        ext.setVal(val);
        preventionExtDao.persist(ext);
        return ext;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist prevention extension with generated ID")
        void shouldPersistPreventionExt_whenValidDataProvided() {
            Prevention prev = createPrevention(90001, "Flu");
            PreventionExt ext = createExt(prev.getId(), "lot", "LOT-001");
            assertThat(ext.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find prevention extension by ID")
        void shouldFindPreventionExt_whenValidIdProvided() {
            Prevention prev = createPrevention(90002, "Tdap");
            PreventionExt saved = createExt(prev.getId(), "manufacturer", "Pfizer");
            PreventionExt found = preventionExtDao.find(saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getKeyval()).isEqualTo("manufacturer");
            assertThat(found.getVal()).isEqualTo("Pfizer");
        }
    }

    @Nested
    @DisplayName("Query by prevention ID")
    class QueryByPreventionId {

        private Prevention prevention;

        @BeforeEach
        void setUp() {
            prevention = createPrevention(90003, "MMR");
            createExt(prevention.getId(), "lot", "LOT-MMR-001");
            createExt(prevention.getId(), "route", "IM");
            createExt(prevention.getId(), "site", "Left deltoid");
        }

        @Test
        @Tag("query")
        @DisplayName("should find extensions by prevention ID")
        void shouldFindExtensions_byPreventionId() {
            List<PreventionExt> results = preventionExtDao.findByPreventionId(prevention.getId());
            assertThat(results).hasSize(3);
        }

        @Test
        @Tag("query")
        @DisplayName("should find extension by prevention ID and key")
        void shouldFindExtension_byPreventionIdAndKey() {
            List<PreventionExt> results = preventionExtDao.findByPreventionIdAndKey(prevention.getId(), "route");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getVal()).isEqualTo("IM");
        }

        @Test
        @Tag("query")
        @DisplayName("should get prevention ext as HashMap")
        void shouldGetPreventionExt_asHashMap() {
            HashMap<String, String> map = preventionExtDao.getPreventionExt(prevention.getId());
            assertThat(map).hasSize(3);
            assertThat(map).containsEntry("lot", "LOT-MMR-001");
            assertThat(map).containsEntry("route", "IM");
            assertThat(map).containsEntry("site", "Left deltoid");
        }
    }

    @Nested
    @DisplayName("Key-value queries")
    class KeyValueQueries {

        @BeforeEach
        void setUp() {
            Prevention p1 = createPrevention(90004, "Flu");
            Prevention p2 = createPrevention(90005, "Flu");
            createExt(p1.getId(), "lot", "SAME-LOT");
            createExt(p2.getId(), "lot", "SAME-LOT");
            createExt(p1.getId(), "lot", "DIFF-LOT");
        }

        @Test
        @Tag("query")
        @DisplayName("should find extensions by key and value")
        void shouldFindExtensions_byKeyAndValue() {
            List<PreventionExt> results = preventionExtDao.findByKeyAndValue("lot", "SAME-LOT");
            assertThat(results).hasSize(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list for non-existent key-value")
        void shouldReturnEmptyList_whenKeyValueNotFound() {
            List<PreventionExt> results = preventionExtDao.findByKeyAndValue("lot", "NONEXISTENT");
            assertThat(results).isEmpty();
        }
    }
}
