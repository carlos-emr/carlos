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

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.metamodel.EntityType;

import org.hibernate.Session;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import io.github.carlos_emr.carlos.commn.dao.paging.PagedTraversalCompositeKey;
import io.github.carlos_emr.carlos.commn.dao.paging.PagedTraversalCompositeRecord;
import io.github.carlos_emr.carlos.commn.dao.paging.PagedTraversalRecord;
import io.github.carlos_emr.carlos.commn.model.AbstractModel;
import io.github.carlos_emr.carlos.commn.model.EReferAttachment;
import io.github.carlos_emr.carlos.commn.model.EReferAttachmentData;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Paged traversal through {@link AbstractDaoImpl#findAll(Integer, Integer)} on a real database
 * (issue #5308).
 *
 * <p>Runs on H2 in the ordinary suite and, through its {@code *Dao*IntegrationTest} name, on a
 * physical MariaDB in the {@code mariadb-dao-tests} CI lane, where an unordered query really does
 * come back in secondary-index order. The synthetic tables in {@code commn.dao.paging} are built so
 * that happens: their only non-key column has an index that covers the table, and the fixtures
 * store sort values in the opposite order to the keys.</p>
 *
 * <p>Those synthetic tables exist only in the Hibernate-generated schema, so the class does not run
 * against the Flyway-migrated MariaDB schema ({@code CARLOS_TEST_MARIADB_SCHEMA=flyway}).</p>
 *
 * @since 2026-10-29
 */
@DisabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_SCHEMA", matches = "flyway",
        disabledReason = "synthetic paging tables exist only in the Hibernate-generated test schema")
@DisplayName("AbstractDaoImpl.findAll stable paging")
@Tag("integration")
@Tag("dao")
@Tag("read")
@Tag("query")
class AbstractDaoFindAllOrderingIntegrationTest extends CarlosTestBase {

    private static final int PAGE_SIZE = 5;

    /**
     * Entity tables the Hibernate-generated MariaDB test schema cannot create. The Rourke forms map
     * 130 ({@code formRourke2009}) and 267 ({@code formRourke2020}) string columns, which Hibernate
     * generates as {@code VARCHAR(255)}; under utf8mb4 that exceeds InnoDB's 65,535-byte row limit,
     * so MariaDB refuses the tables (error 1118). The Flyway schema the application installs defines
     * both. Their queries are still compiled; any other missing table fails the test.
     */
    private static final Set<String> KNOWN_GENERATED_SCHEMA_GAPS = Set.of("formRourke2009", "formRourke2020");

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @Nested
    @DisplayName("single generated identifier")
    class SingleIdentifier {

        @Test
        @DisplayName("should visit every row once in identifier order when an index covers the table")
        void shouldVisitEveryRowOnceInIdentifierOrder_whenSecondaryIndexCoversTable() {
            // Given 23 rows whose indexed sort keys descend while their generated ids ascend
            List<Integer> insertedIds = new ArrayList<>();
            for (int i = 0; i < 23; i++) {
                PagedTraversalRecord row = new PagedTraversalRecord(String.format("key-%03d", 900 - i));
                entityManager.persist(row);
                entityManager.flush();
                insertedIds.add(row.getId());
            }
            entityManager.clear();
            AbstractDaoImpl<PagedTraversalRecord> dao = daoFor(PagedTraversalRecord.class);

            // When
            List<Integer> visited = traverse(dao, PagedTraversalRecord::getId);

            // Then: each id once, ascending, and nothing but the rows this test inserted
            assertThat(visited).containsExactlyElementsOf(insertedIds.stream().sorted().toList());
        }

        @Test
        @DisplayName("should read the fixture out of identifier order without ORDER BY on MariaDB")
        void shouldReadFixtureOutOfIdentifierOrder_withoutOrderByOnMariaDb() {
            // Guards the regression above against becoming vacuous: on MariaDB the fixture must
            // really defeat an unordered query, or the paged test would pass with or without the fix.
            for (int i = 0; i < 6; i++) {
                entityManager.persist(new PagedTraversalRecord("key-" + (9 - i)));
            }
            entityManager.flush();
            entityManager.clear();

            List<Integer> unordered = entityManager
                    .createQuery("SELECT r.id FROM PagedTraversalRecord r", Integer.class)
                    .getResultList();

            assertThat(unordered).hasSize(6).doesNotHaveDuplicates();
            if (isMariaDb()) {
                // MariaDB scans the covering sort_key index: descending sort keys mean descending ids.
                assertThat(unordered).isSortedAccordingTo(Comparator.reverseOrder());
            }
        }

        @Test
        @DisplayName("should order by the mapped identifier attribute even when it is not named id")
        void shouldOrderByMappedIdentifier_forFaxConfigCapitalisedId() {
            AbstractDaoImpl<FaxConfig> dao = daoFor(FaxConfig.class);

            assertThat(dao.findAllQuery()).isEqualTo("SELECT e FROM FaxConfig e ORDER BY e.Id ASC");
            assertThat(dao.findAll(0, 1)).hasSizeLessThanOrEqualTo(1);
        }
    }

    @Nested
    @DisplayName("composite identifiers")
    class CompositeIdentifiers {

        @Test
        @DisplayName("should page an embedded id by its components sorted by attribute name")
        void shouldPageEmbeddedId_byComponentsSortedByAttributeName() {
            // Given rows inserted in neither key order nor sort-key order
            String[][] keys = {{"b", "3"}, {"a", "1"}, {"b", "1"}, {"a", "3"}, {"b", "2"}, {"a", "2"}, {"c", "1"}};
            for (int i = 0; i < keys.length; i++) {
                PagedTraversalCompositeKey key = new PagedTraversalCompositeKey(keys[i][0], Integer.valueOf(keys[i][1]));
                entityManager.persist(new PagedTraversalCompositeRecord(key, "key-" + (90 - i)));
            }
            entityManager.flush();
            entityManager.clear();
            AbstractDaoImpl<PagedTraversalCompositeRecord> dao = daoFor(PagedTraversalCompositeRecord.class);

            // When
            List<String> visited = traverse(dao, row -> row.getId().toString());

            // Then: seq before zone, because "seq" sorts before "zone"
            assertThat(dao.findAllQuery()).endsWith("ORDER BY e.id.seq ASC, e.id.zone ASC");
            assertThat(visited).containsExactly("a/1", "b/1", "c/1", "a/2", "b/2", "a/3", "b/3");
        }

        @Test
        @DisplayName("should page an IdClass key whose first part is an association by the target id")
        void shouldPageIdClassKey_byAssociationTargetIdentifier() {
            // Given two attachments and their lab rows, inserted out of key order
            EReferAttachment first = persistAttachment(101);
            EReferAttachment second = persistAttachment(102);
            entityManager.persist(new EReferAttachmentData(second, 5, "HL7"));
            entityManager.persist(new EReferAttachmentData(first, 9, "DOC"));
            entityManager.persist(new EReferAttachmentData(first, 2, "HL7"));
            entityManager.persist(new EReferAttachmentData(second, 5, "DOC"));
            entityManager.persist(new EReferAttachmentData(first, 2, "DOC"));
            entityManager.flush();
            entityManager.clear();
            AbstractDaoImpl<EReferAttachmentData> dao = daoFor(EReferAttachmentData.class);
            Integer firstId = first.getId();
            Integer secondId = second.getId();

            // When
            List<String> visited = traverse(dao, row -> {
                Integer attachmentId = row.geteReferAttachment().getId();
                String owner = attachmentId.equals(firstId) ? "first" : attachmentId.equals(secondId) ? "second" : "other";
                return owner + "/" + row.getLabId() + "/" + row.getLabType();
            });

            // Then
            assertThat(dao.findAllQuery())
                    .endsWith("ORDER BY e.eReferAttachment.id ASC, e.labId ASC, e.labType ASC");
            assertThat(visited.stream().filter(row -> !row.startsWith("other")).toList()).containsExactly(
                    "first/2/DOC", "first/2/HL7", "first/9/DOC", "second/5/DOC", "second/5/HL7");
            assertThat(visited).doesNotHaveDuplicates();
        }

        private EReferAttachment persistAttachment(int demographicNo) {
            EReferAttachment attachment = new EReferAttachment(demographicNo);
            entityManager.persist(attachment);
            entityManager.flush();
            return attachment;
        }
    }

    @Nested
    @DisplayName("every mapped entity")
    class EveryMappedEntity {

        @Test
        @DisplayName("should build and run the ordered findAll query for every AbstractModel entity")
        void shouldRunOrderedFindAll_forEveryMappedAbstractModelEntity() {
            // A mapping the identifier resolver cannot express, or a path the database rejects,
            // would otherwise surface only when some screen first pages that entity in production.
            Set<String> tables = existingTablesLowerCase();
            Map<String, String> failures = new LinkedHashMap<>();
            Set<String> missingTables = new TreeSet<>();
            int executed = 0;
            for (EntityType<?> entity : entityManager.getMetamodel().getEntities()) {
                Class<?> javaType = entity.getJavaType();
                if (!AbstractModel.class.isAssignableFrom(javaType)) {
                    continue;
                }
                try {
                    AbstractDaoImpl<?> dao = daoFor(asModelClass(javaType));
                    String jpql = dao.findAllQuery();
                    if (!jpql.contains(" ORDER BY e.")) {
                        failures.put(javaType.getName(), "no identifier order: " + jpql);
                        continue;
                    }
                    // Hibernate resolves every attribute path when the query is created, so the
                    // order is validated even where the table itself is unavailable.
                    entityManager.createQuery(jpql, javaType);
                    String table = tableOf(javaType);
                    if (!tables.contains(table.toLowerCase(Locale.ROOT))) {
                        missingTables.add(table);
                        continue;
                    }
                    dao.findAll(0, 1);
                    executed++;
                } catch (RuntimeException e) {
                    failures.put(javaType.getName(), e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }

            assertThat(failures).as("entities whose findAll query failed").isEmpty();
            assertThat(missingTables).as("entity tables absent from the test schema")
                    .isSubsetOf(KNOWN_GENERATED_SCHEMA_GAPS);
            assertThat(executed).as("findAll queries executed against the database").isGreaterThan(300);
        }
    }

    /** Reads every page in turn, the way paging callers such as HRMFixMissingReportHelper do. */
    private static <T extends AbstractModel<?>, K> List<K> traverse(AbstractDaoImpl<T> dao, Function<T, K> key) {
        List<K> visited = new ArrayList<>();
        int offset = 0;
        while (true) {
            List<T> page = dao.findAll(offset, PAGE_SIZE);
            page.forEach(row -> visited.add(key.apply(row)));
            if (page.size() < PAGE_SIZE) {
                return visited;
            }
            offset += PAGE_SIZE;
        }
    }

    /** A plain AbstractDaoImpl for {@code type}, wired with this context's shared EntityManager. */
    private <T extends AbstractModel<?>> AbstractDaoImpl<T> daoFor(Class<T> type) {
        AbstractDaoImpl<T> dao = new AbstractDaoImpl<T>(type) {
        };
        applicationContext.getAutowireCapableBeanFactory().autowireBean(dao);
        return dao;
    }

    @SuppressWarnings("unchecked")
    private static Class<AbstractModel<?>> asModelClass(Class<?> javaType) {
        return (Class<AbstractModel<?>>) javaType;
    }

    /** The primary table Hibernate maps {@code entityClass} to, without quoting or a schema prefix. */
    private String tableOf(Class<?> entityClass) {
        EntityPersister persister = entityManager.getEntityManagerFactory().unwrap(SessionFactoryImplementor.class)
                .getMappingMetamodel().getEntityDescriptor(entityClass);
        String table = ((AbstractEntityPersister) persister).getTableName();
        return table.substring(table.lastIndexOf('.') + 1).replaceAll("[`\"\\[\\]]", "");
    }

    /** Names of the tables and views in the connected test schema, lower-cased (H2 stores them upper-case). */
    private Set<String> existingTablesLowerCase() {
        return entityManager.unwrap(Session.class).doReturningWork(connection -> {
            Set<String> names = new HashSet<>();
            try (ResultSet rows = connection.getMetaData().getTables(connection.getCatalog(), connection.getSchema(),
                    "%", new String[]{"TABLE", "VIEW"})) {
                while (rows.next()) {
                    names.add(rows.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
                }
            }
            return names;
        });
    }

    private boolean isMariaDb() {
        return entityManager.unwrap(Session.class)
                .doReturningWork(connection -> connection.getMetaData().getDatabaseProductVersion())
                .contains("MariaDB");
    }
}
