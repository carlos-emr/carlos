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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.CdsFormOption;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link CdsFormOptionDao} covering findByVersionAndCategory
 * and findByVersion methods.
 *
 * <p>Note: {@link CdsFormOption} has no public setters and uses {@code @PreUpdate} /
 * {@code @PreRemove} to prevent modifications. Test data is inserted via native SQL
 * to bypass JPA lifecycle callbacks, allowing meaningful query filtering assertions.</p>
 *
 * @since 2026-03-07
 * @see CdsFormOptionDao
 */
@DisplayName("CdsFormOption Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class CdsFormOptionDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private CdsFormOptionDao dao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /**
     * Inserts a CdsFormOption row via native SQL to bypass the @PreUpdate/@PreRemove
     * JPA lifecycle callbacks that prevent normal JPA persist.
     */
    private void insertViaNativeSql(String version, String category, String categoryName) {
        entityManager.createNativeQuery(
                "INSERT INTO CdsFormOption (cdsFormVersion, cdsDataCategory, cdsDataCategoryName) VALUES (?1, ?2, ?3)")
                .setParameter(1, version)
                .setParameter(2, category)
                .setParameter(3, categoryName)
                .executeUpdate();
        entityManager.flush();
    }

    @Test
    @Tag("read")
    @DisplayName("should find options by version and category prefix")
    void shouldFindByVersionAndCategory_whenMatchingRecordsExist() {
        insertViaNativeSql("4", "016-01", "Eating Disorder Type A");
        insertViaNativeSql("4", "016-02", "Eating Disorder Type B");
        insertViaNativeSql("4", "017-01", "Other Disorder");
        insertViaNativeSql("5", "016-01", "Different Version");

        List<CdsFormOption> results = dao.findByVersionAndCategory("4", "016");

        assertThat(results).hasSize(2);
        assertThat(results).extracting(CdsFormOption::getCdsFormVersion)
                .containsOnly("4");
        assertThat(results).extracting(CdsFormOption::getCdsDataCategory)
                .allMatch(cat -> cat.startsWith("016"));
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when no options match version and category")
    void shouldReturnEmptyList_whenNoOptionsMatchVersionAndCategory() {
        insertViaNativeSql("4", "016-01", "Existing");

        List<CdsFormOption> results = dao.findByVersionAndCategory("4", "999");

        assertThat(results).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should find all options by version")
    void shouldFindByVersion_whenMatchingRecordsExist() {
        insertViaNativeSql("4", "016-01", "Category A");
        insertViaNativeSql("4", "017-01", "Category B");
        insertViaNativeSql("5", "016-01", "Version 5 Category");

        List<CdsFormOption> results = dao.findByVersion("4");

        assertThat(results).hasSize(2);
        assertThat(results).extracting(CdsFormOption::getCdsFormVersion)
                .containsOnly("4");
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when no options match version")
    void shouldReturnEmptyList_whenNoOptionsMatchVersion() {
        insertViaNativeSql("4", "016-01", "Only Version 4");

        List<CdsFormOption> results = dao.findByVersion("99");

        assertThat(results).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should return results ordered by ID")
    void shouldReturnResultsOrderedById_whenMultipleRecordsExist() {
        insertViaNativeSql("4", "016-03", "Third");
        insertViaNativeSql("4", "016-01", "First");
        insertViaNativeSql("4", "016-02", "Second");

        List<CdsFormOption> results = dao.findByVersion("4");

        assertThat(results).hasSize(3);
        // Verify ordered by ID (ascending, which matches insertion order)
        assertThat(results.get(0).getId()).isLessThan(results.get(1).getId());
        assertThat(results.get(1).getId()).isLessThan(results.get(2).getId());
    }
}
