/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import javax.sql.DataSource;
import io.github.carlos_emr.carlos.test.base.IsolatedJpaTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RxFavoriteGroupIntegrationTest.Config.class)
@Tag("integration")
@Tag("mariadb-native")
class RxFavoriteGroupIntegrationTest {
    @Autowired private RxFavoriteGroupService manager;
    @Autowired private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach void seed() throws Exception {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE IF NOT EXISTS provider (provider_no VARCHAR(6) PRIMARY KEY, lastUpdateDate TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS favorites (favoriteid INT PRIMARY KEY, provider_no VARCHAR(6), favoritename VARCHAR(50), special VARCHAR(255), dispenseInternal TINYINT NOT NULL DEFAULT 0)");
        String ddl = java.nio.file.Files.readString(java.nio.file.Path.of(
                "database/mysql/migration/common/V1.0.59.35__rx_favorite_groups.sql"));
        // H2 has no engine conversions; the MariaDB adoption sentinel below exercises those.
        var migration = new ResourceDatabasePopulator(new org.springframework.core.io.ByteArrayResource(
                ddl.substring(ddl.indexOf("-- Provider-owned")).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        migration.execute(dataSource);
        migration.execute(dataSource); // The forward migration is safe when reapplied.
        jdbc.update("DELETE FROM rx_favorite_group");
        jdbc.update("DELETE FROM favorites WHERE favoriteid IN (700001,700002)");
        jdbc.update("DELETE FROM provider WHERE provider_no IN ('997001','997002')");
        jdbc.update("INSERT INTO provider(provider_no,lastUpdateDate) VALUES ('997001',CURRENT_TIMESTAMP),('997002',CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO favorites(favoriteid,provider_no,favoritename,special,dispenseInternal) VALUES (700001,'997001','Synthetic favourite','Keep dose and instructions',0),(700002,'997002','Private favourite','Private',0)");
    }

    @Test void shouldKeepTemplates_whenGroupsAreRenamedReorderedAndDeleted() throws Exception {
        verifyAdoptedEngines();
        manager.create("997001", "Second");
        manager.create("997001", "First");
        var groups = manager.view("997001").groups();
        int second = groups.get(0).id(), first = groups.get(1).id();
        manager.assign("997001", 700001, List.of(second, first, second));
        manager.move("997001", first, -1);
        manager.rename("997001", first, "First renamed");
        assertThat(manager.view("997001").groups()).extracting(RxFavoriteGroupService.Group::name)
                .containsExactly("First renamed", "Second");
        assertThat(manager.view("997001").favorites().getFirst().groupIds()).containsExactly(first, second);
        manager.delete("997001", first);
        manager.delete("997001", second);
        assertThat(manager.view("997001").favorites().getFirst().groupIds()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT special FROM favorites WHERE favoriteid=700001", String.class))
                .isEqualTo("Keep dose and instructions");
    }

    @Test void shouldRefuseForeignIdentifiers_beforeChangingExistingMembership() {
        manager.create("997001", "Owned");
        manager.create("997002", "Private");
        int owned = manager.view("997001").groups().getFirst().id();
        int foreign = manager.view("997002").groups().getFirst().id();
        manager.assign("997001", 700001, List.of(owned));
        assertThatThrownBy(() -> manager.assign("997001", 700001, List.of(owned, foreign)))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> manager.assign("997001", 700002, List.of(owned)))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> manager.rename("997001", foreign, "Stolen"))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> manager.delete("997001", foreign)).isInstanceOf(NoSuchElementException.class);
        assertThat(manager.view("997001").favorites()).hasSize(1);
        assertThat(manager.view("997001").favorites().getFirst().groupIds()).containsExactly(owned);
        assertThat(manager.view("997002").groups().getFirst().name()).isEqualTo("Private");
    }

    @Test void shouldRollbackAssignment_whenInsertFailsAfterDeletingPreviousLinks() {
        manager.create("997001", "One");
        manager.create("997001", "Two");
        int one = manager.view("997001").groups().get(0).id();
        int two = manager.view("997001").groups().get(1).id();
        manager.assign("997001", 700001, List.of(one));
        jdbc.execute("ALTER TABLE rx_favorite_group_member ADD CONSTRAINT synthetic_reject CHECK (group_id <> " + two + ")");
        try {
            assertThatThrownBy(() -> manager.assign("997001", 700001, List.of(two)))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(manager.view("997001").favorites().getFirst().groupIds()).containsExactly(one);
        } finally { jdbc.execute("ALTER TABLE rx_favorite_group_member DROP CONSTRAINT synthetic_reject"); }
    }

    @Test void shouldRejectInvalidNamesAndSelections_withoutWriting() {
        for (String name : Arrays.asList(null, " ", "x".repeat(81))) {
            assertThatThrownBy(() -> manager.create("997001", name)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> manager.assign("997001", 700001, Arrays.asList((Integer) null)))
                .isInstanceOf(IllegalArgumentException.class);
        manager.create("997001", "Case");
        assertThatThrownBy(() -> manager.create("997001", "Case"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(manager.view("997001").groups()).hasSize(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void shouldSerializeConcurrentGroupChanges_withoutDeadlocksOrTemplateChanges(boolean differentProviders) throws Exception {
        manager.create("997001", "Zeta"); manager.create("997001", "Alpha");
        manager.create("997002", "Zeta"); manager.create("997002", "Alpha");
        var first = manager.view("997001").groups().stream().map(RxFavoriteGroupService.Group::id).toList();
        var second = manager.view("997002").groups().stream().map(RxFavoriteGroupService.Group::id).toList();
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var assignment = pool.submit(() -> {
                for (int index = 0; index < 20; index++) {
                    barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                    manager.assign("997001", 700001, index % 2 == 0 ? first : List.of());
                }
                return true;
            });
            var competing = pool.submit(() -> {
                for (int index = 0; index < 20; index++) {
                    barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                    if (differentProviders) manager.assign("997002", 700002, index % 2 == 0 ? second : List.of());
                    else manager.move("997001", first.getFirst(), index % 2 == 0 ? 1 : -1);
                }
                return true;
            });
            assertThat(assignment.get(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(competing.get(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("SELECT special FROM favorites WHERE favoriteid=700001", String.class))
                    .isEqualTo("Keep dose and instructions");
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

    private void verifyAdoptedEngines() throws Exception {
        try (var connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().contains("MariaDB")) return;
        }
        try (var adopted = new IsolatedJpaTestDatabase("rx_adoption_engine")) {
            var target = new JdbcTemplate(adopted.dataSource());
            target.execute("CREATE TABLE favorites(favoriteid INT PRIMARY KEY) ENGINE=MyISAM");
            for (String table : List.of("drugs", "prescription", "log")) {
                target.execute("CREATE TABLE " + table + "(id INT PRIMARY KEY) ENGINE=MyISAM");
                target.update("INSERT INTO " + table + "(id) VALUES (1)");
            }
            new ResourceDatabasePopulator(new FileSystemResource(
                    "database/mysql/migration/common/V1.0.59.35__rx_favorite_groups.sql")).execute(adopted.dataSource());
            for (String table : List.of("drugs", "prescription", "log", "favorites")) {
                assertThat(target.queryForObject("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?",
                        String.class, table)).isEqualTo("InnoDB");
            }
            var transaction = new org.springframework.transaction.support.TransactionTemplate(
                    new DataSourceTransactionManager(adopted.dataSource()));
            transaction.executeWithoutResult(status -> {
                target.update("INSERT INTO drugs(id) VALUES (2)");
                target.update("INSERT INTO prescription(id) VALUES (2)");
                target.update("INSERT INTO log(id) VALUES (2)");
                status.setRollbackOnly();
            });
            for (String table : List.of("drugs", "prescription", "log")) {
                assertThat(target.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)).isEqualTo(1);
            }
        }
    }

    @Configuration @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean(destroyMethod = "close") IsolatedJpaTestDatabase database() throws Exception {
            return new IsolatedJpaTestDatabase("rx_favorite_groups");
        }
        @Bean DataSource dataSource(IsolatedJpaTestDatabase database) { return database.dataSource(); }
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean RxFavoriteGroupService manager(DataSource source) { return new RxFavoriteGroupService(source); }
    }
}
