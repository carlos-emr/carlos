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

import io.github.carlos_emr.carlos.commn.model.ReportByExamples;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ReportByExamplesDao} covering persist and find operations.
 *
 * <p>Note: The {@code findReportsAndProviders} methods join with Provider, so they
 * require Provider records in the database. These tests verify basic persist/find
 * operations that do not require cross-entity joins.</p>
 *
 * <p>Migrated from legacy {@code ReportByExamplesDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ReportByExamplesDao
 */
@DisplayName("ReportByExamples Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class ReportByExamplesDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ReportByExamplesDao dao;

    private ReportByExamples createReport(String providerNo, String query, Date date) {
        ReportByExamples entity = new ReportByExamples();
        entity.setProviderNo(providerNo);
        entity.setQuery(query);
        entity.setDate(date);
        dao.persist(entity);
        return entity;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist report by examples with generated ID")
        void shouldPersistReportByExamples_whenValidDataProvided() {
            ReportByExamples entity = createReport("100001", "SELECT * FROM demographic", new Date());

            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find report by ID with correct field values")
        void shouldFindReport_whenValidIdProvided() {
            Date now = new Date();
            ReportByExamples saved = createReport("100002", "test query", now);

            ReportByExamples found = dao.find(saved.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getProviderNo()).isEqualTo("100002");
            assertThat(found.getQuery()).isEqualTo("test query");
            assertThat(found.getDate()).isNotNull();
        }
    }

    @Nested
    @DisplayName("findReportsAndProviders - no-arg")
    class FindReportsAndProvidersNoArg {

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no reports have matching providers")
        void shouldReturnEmptyList_whenNoReportsHaveMatchingProviders() {
            // Reports with non-existent provider numbers will not join with Provider
            createReport("NOPROV1", "query1", new Date());

            var results = dao.findReportsAndProviders();

            // No matching Provider record, so the join yields no results
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findReportsAndProviders - date range")
    class FindReportsAndProvidersDateRange {

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no reports match date range with providers")
        void shouldReturnEmptyList_whenNoReportsMatchDateRangeWithProviders() {
            Date now = new Date();
            createReport("NOPROV2", "query2", now);

            var results = dao.findReportsAndProviders(
                    new Date(now.getTime() - 86400000L),
                    new Date(now.getTime() + 86400000L)
            );

            // No matching Provider record, so the join yields no results
            assertThat(results).isEmpty();
        }
    }
}
