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
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.CustomFilter;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link CustomFilterDao} covering save and find with
 * program and assignee associations, and the explicit fetch plan from issue #5307.
 *
 * <p>Migrated from legacy {@code CustomFilterDaoTest} (JUnit 4 / DaoTestFixtures)
 * with expanded coverage and BDD-style naming.</p>
 *
 * <p>The fetch-plan tests clear the persistence context before reading, so each read starts
 * from the database, and again before touching the provider sets, so they see exactly what a
 * caller outside the DAO transaction sees: a detached filter. The test schema is generated
 * from the mappings; it makes {@code custom_filter_providers.provider_no} unique (the
 * production table has no such key), so each filter here gets its own providers.</p>
 *
 * @since 2026-03-07
 * @see CustomFilterDao
 */
@DisplayName("CustomFilterDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class CustomFilterDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private CustomFilterDao dao;

    @Autowired
    private ProviderDao providerDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private int nextProviderNo = 930000;

    @Nested
    @DisplayName("save and find")
    class SaveAndFind {

        @Test
        @Tag("create")
        @DisplayName("should persist custom filter with program and assignees")
        void shouldPersistCustomFilter_withProgramAndAssignees() throws Exception {
            // Use a dedicated provider rather than the seeded administrator.
            Provider p = new Provider();
            p.setProviderNo("900123");
            p.setFirstName("Test");
            p.setLastName("Provider");
            p.setProviderType("doctor");
            p.setSex("M");
            p.setSpecialty("");
            p.setStatus("1");
            p.setSignedConfidentiality(new Date());
            providerDao.saveProvider(p);

            CustomFilter entity = new CustomFilter();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            entity.setProviderNo("900123");
            entity.setProgramId("10015");

            entity.getAssignees().add(p);

            dao.persist(entity);

            CustomFilter cf = dao.find(entity.getId());

            assertThat(cf).isNotNull();
            assertThat(cf.getAssignees()).hasSize(1);
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when filter does not exist")
        void shouldReturnNull_whenFilterDoesNotExist() throws Exception {
            CustomFilter cf = dao.find(99999);

            assertThat(cf).isNull();
        }
    }

    @Nested
    @DisplayName("fetch plan (issue #5307)")
    class FetchPlan {

        @Test
        @Tag("read")
        @DisplayName("should load a provider's filters with their sets and programs in one statement")
        void shouldLoadFiltersWithSetsAndPrograms_inOneStatement() {
            Program program = persistProgram();
            for (int i = 0; i < 10; i++) {
                // Half the filters name a program that exists, half one that does not.
                persistFilter("list" + i, "930100", i % 2 == 0 ? String.valueOf(program.getId()) : "99999", 2, 1);
            }
            entityManager.flush();
            entityManager.clear();

            List<CustomFilter> filters = countingStatements(() -> dao.findByProviderNo("930100"), 1);

            entityManager.clear();
            assertThat(filters).hasSize(10).allSatisfy(filter -> {
                assertThat(filter.getProviders()).hasSize(2);
                assertThat(filter.getAssignees()).hasSize(1);
            });
            assertThat(filters).filteredOn(filter -> filter.getProgram() != null)
                    .hasSize(5)
                    .allSatisfy(filter -> assertThat(filter.getProgram().getName()).isEqualTo("Fetch plan program"));
        }

        @Test
        @Tag("read")
        @DisplayName("should load one filter by id in one statement")
        void shouldLoadFilterById_inOneStatement() {
            CustomFilter saved = persistFilter("by-id", "930200", null, 2, 2);
            entityManager.flush();
            entityManager.clear();

            CustomFilter found = countingStatements(() -> dao.find(saved.getId()), 1);

            entityManager.clear();
            assertThat(found.getProviders()).hasSize(2);
            assertThat(found.getAssignees()).hasSize(2);
        }

        @Test
        @Tag("read")
        @DisplayName("should return each filter once when its provider sets multiply the joined rows")
        void shouldReturnEachFilterOnce_whenProviderSetsMultiplyJoinedRows() {
            persistFilter("wide", "930300", null, 3, 3);
            persistFilter("narrow", "930300", null, 1, 0);
            entityManager.flush();
            entityManager.clear();

            List<CustomFilter> filters = dao.findByProviderNo("930300");

            assertThat(filters).extracting(CustomFilter::getName).containsExactly("wide", "narrow");
            assertThat(filters.get(0).getProviders()).hasSize(3);
            assertThat(filters.get(0).getAssignees()).hasSize(3);
            assertThat(filters.get(1).getAssignees()).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should hand back initialized sets from every read the DAO exposes")
        void shouldHandBackInitializedSets_fromEveryRead() {
            CustomFilter shortcut = persistFilter("detached", "930400", null, 1, 1);
            shortcut.setShortcut(true);
            entityManager.flush();
            entityManager.clear();
            Integer id = shortcut.getId();

            List<Supplier<List<CustomFilter>>> reads = List.of(
                    () -> List.of(dao.find(id)),
                    () -> List.of(dao.find(id.intValue())),
                    () -> List.of(dao.findDetached(id)),
                    () -> List.of(dao.findByName("detached")),
                    () -> List.of(dao.findByNameAndProviderNo("detached", "930400")),
                    () -> dao.findByProviderNo("930400"),
                    () -> dao.getCustomFilterWithShortCut("930400"),
                    () -> dao.getCustomFilters(),
                    () -> dao.findAll(0, 100));
            List<CustomFilter> detached = new ArrayList<>();
            for (Supplier<List<CustomFilter>> read : reads) {
                // A fresh persistence context per read, detached before the sets are touched.
                entityManager.clear();
                detached.addAll(read.get());
                entityManager.clear();
            }

            assertThat(detached).filteredOn(filter -> id.equals(filter.getId())).hasSize(reads.size()).allSatisfy(filter -> {
                assertThat(Hibernate.isInitialized(filter.getProviders())).isTrue();
                assertThat(Hibernate.isInitialized(filter.getAssignees())).isTrue();
                assertThat(filter.getProvider()).isEqualTo(filter.getProviders().iterator().next().getProviderNo());
                assertThat(filter.getAssignee()).isNotNull();
            });
        }

        @Test
        @Tag("read")
        @DisplayName("should return a null program instead of failing when the program row is missing")
        void shouldReturnNullProgram_whenProgramRowIsMissing() {
            CustomFilter saved = persistFilter("orphan", "930500", "99999", 1, 1);
            entityManager.flush();
            entityManager.clear();

            CustomFilter found = dao.findByName("orphan");

            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getProgramId()).isEqualTo("99999");
            assertThat(found.getProgram()).isNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should return the lowest id when two filters share a name")
        void shouldReturnLowestId_whenTwoFiltersShareAName() {
            CustomFilter first = persistFilter("shared", "930600", null, 1, 0);
            persistFilter("shared", "930600", null, 1, 0);
            entityManager.flush();
            entityManager.clear();

            assertThat(dao.findByName("shared").getId()).isEqualTo(first.getId());
            assertThat(dao.findByNameAndProviderNo("shared", "930600").getId()).isEqualTo(first.getId());
            assertThat(dao.findByName("absent")).isNull();
            assertThat(dao.findByNameAndProviderNo("shared", "939999")).isNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should page filters, not joined rows, when listing all")
        void shouldPageFilters_whenListingAll() {
            for (int i = 0; i < 4; i++) {
                persistFilter("page" + i, "930700", null, 2, 2);
            }
            entityManager.flush();
            entityManager.clear();
            List<Integer> ids = dao.findAll(0, 100).stream().map(CustomFilter::getId).toList();

            assertThat(dao.findAll(1, 2)).extracting(CustomFilter::getId).containsExactly(ids.get(1), ids.get(2));
            assertThat(dao.findAll(ids.size(), 10)).isEmpty();
            assertThatThrownBy(() -> dao.findAll(0, Integer.MAX_VALUE))
                    .isInstanceOf(MaxSelectLimitExceededException.class);
        }

        @Test
        @Tag("update")
        @DisplayName("should write the join rows of a merged detached filter")
        void shouldWriteJoinRows_whenDetachedFilterIsMerged() {
            CustomFilter saved = persistFilter("merge", "930800", null, 1, 1);
            Provider replacement = persistProvider();
            entityManager.flush();
            entityManager.clear();

            CustomFilter detached = dao.findDetached(saved.getId());
            detached.getProviders().clear();
            detached.getProviders().add(replacement);
            dao.merge(detached);
            entityManager.flush();
            entityManager.clear();

            assertThat(dao.find(saved.getId()).getProviders()).extracting(Provider::getProviderNo)
                    .containsExactly(replacement.getProviderNo());
        }

        @Test
        @Tag("delete")
        @DisplayName("should delete the join rows with the filter")
        void shouldDeleteJoinRows_withTheFilter() {
            CustomFilter saved = persistFilter("delete-me", "930900", null, 2, 1);
            entityManager.flush();
            entityManager.clear();

            dao.deleteCustomFilter("delete-me");
            entityManager.flush();

            assertThat(dao.find(saved.getId())).isNull();
            assertThat(countJoinRows("custom_filter_providers", saved.getId())).isZero();
            assertThat(countJoinRows("custom_filter_assignees", saved.getId())).isZero();
        }
    }

    private CustomFilter persistFilter(String name, String owner, String programId, int providers, int assignees) {
        CustomFilter filter = new CustomFilter();
        filter.setName(name);
        filter.setProviderNo(owner);
        filter.setDemographicNo("");
        filter.setPriority("");
        filter.setStartDate(new Date());
        filter.setProgramId(programId);
        for (int i = 0; i < providers; i++) {
            filter.getProviders().add(persistProvider());
        }
        for (int i = 0; i < assignees; i++) {
            filter.getAssignees().add(persistProvider());
        }
        dao.persist(filter);
        return filter;
    }

    private Provider persistProvider() {
        Provider provider = new Provider();
        provider.setProviderNo(String.valueOf(nextProviderNo++));
        provider.setFirstName("Fetch");
        provider.setLastName("Plan");
        provider.setProviderType("doctor");
        provider.setSex("F");
        provider.setSpecialty("");
        provider.setStatus("1");
        provider.setSignedConfidentiality(new Date());
        entityManager.persist(provider);
        return provider;
    }

    private Program persistProgram() {
        Program program = new Program();
        program.setName("Fetch plan program");
        program.setFacilityId(1);
        program.setMaxAllowed(10);
        program.setType("Service");
        program.setProgramStatus("active");
        entityManager.persist(program);
        return program;
    }

    private long countJoinRows(String table, Integer filterId) {
        return ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM " + table + " WHERE filter_id = :id")
                .setParameter("id", filterId).getSingleResult()).longValue();
    }

    /** Runs the read with Hibernate statistics on and asserts how many JDBC statements it prepared. */
    private <T> T countingStatements(Supplier<T> read, long expectedStatements) {
        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            T result = read.get();
            assertThat(statistics.getPrepareStatementCount()).as("JDBC statements prepared").isEqualTo(expectedStatements);
            assertThat(statistics.getCollectionFetchCount()).as("provider sets loaded by a later SELECT").isZero();
            return result;
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }
}
