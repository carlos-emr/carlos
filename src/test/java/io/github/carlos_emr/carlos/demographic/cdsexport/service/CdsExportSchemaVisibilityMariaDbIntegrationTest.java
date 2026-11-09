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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.commn.dao.DemographicDaoImpl;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.commn.model.Provider;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A real column-only grant can hide a future field from information_schema. The source
 * schema probe must refuse that account instead of qualifying the incomplete catalogue.
 * Creates an empty owned clone, never modifies the source demographic table or its data.
 */
@Tag("integration")
@Tag("cds-export")
@Isolated("Owned temporary database and database-account grants")
@EnabledIfSystemProperty(named = "cds.export.test.properties", matches = ".+")
class CdsExportSchemaVisibilityMariaDbIntegrationTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Transactions {}

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRefuseAHiddenFutureColumn_evenWhenTheVisibleCatalogueExactlyMatchesThePinnedSchema(
            boolean invisible) throws Exception {
        var properties = new Properties();
        try (var input =
                Files.newInputStream(Path.of(System.getProperty("cds.export.test.properties")))) {
            properties.load(input);
        }
        String url = properties.getProperty("jdbcUrl", "");
        if (!url.matches("jdbc:mysql://[^/]+/cds_export_test(?:\\?.*)?")
                || !"true".equals(properties.getProperty("ownedDisposableDatabase"))) {
            throw new IllegalArgumentException(
                    "An owned disposable cds_export_test database is required");
        }
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String clone = "cds_visibility_" + suffix,
                user = "cdsv_" + suffix,
                password = UUID.randomUUID().toString();
        try (var admin =
                source(
                        url,
                        properties.getProperty("jdbcUser"),
                        properties.getProperty("jdbcPassword"))) {
            var sql = new JdbcTemplate(admin);
            assertThat(sql.queryForObject("select VERSION()", String.class)).contains("MariaDB");
            boolean databaseCreated = false, userCreated = false;
            try {
                sql.execute("create database `" + clone + "`");
                databaseCreated = true;
                sql.execute(
                        "create table `"
                                + clone
                                + "`.demographic like cds_export_test.demographic");
                sql.execute(
                        "alter table `"
                                + clone
                                + "`.demographic add column future_clinical_field varchar(20) null"
                                + (invisible ? " invisible" : ""));
                sql.execute("create user '" + user + "'@'%' identified by '" + password + "'");
                userCreated = true;
                var columns = new HashSet<>(CdsExportDemographicRowDto.COLUMNS);
                columns.add("demographic_no");
                String granted =
                        String.join(
                                ",",
                                columns.stream()
                                        .sorted()
                                        .map(column -> "`" + column + "`")
                                        .toList());
                sql.execute(
                        "grant select("
                                + granted
                                + ") on `"
                                + clone
                                + "`.demographic to '"
                                + user
                                + "'@'%'");
                try (var limited =
                        source(url.replace("/cds_export_test", "/" + clone), user, password)) {
                    var visible =
                            new JdbcTemplate(limited)
                                    .queryForList(
                                            "select COLUMN_NAME from information_schema.COLUMNS "
                                                    + "where TABLE_SCHEMA=DATABASE() and TABLE_NAME='demographic'",
                                            String.class);
                    assertThat(new HashSet<>(visible))
                            .isEqualTo(columns)
                            .doesNotContain("future_clinical_field");
                    // This was the old probe: it succeeds while the new clinical field is hidden.
                    assertThat(
                                    new JdbcTemplate(limited)
                                            .queryForList("select 1 from demographic limit 0"))
                            .isEmpty();
                    try (var context = context(limited)) {
                        var transaction =
                                new TransactionTemplate(
                                        context.getBean(PlatformTransactionManager.class));
                        transaction.setReadOnly(true);
                        transaction.setTimeout(15);
                        assertThatThrownBy(
                                        () ->
                                                transaction.execute(
                                                        status -> {
                                                            var schema =
                                                                    context.getBean(
                                                                                    DemographicDaoImpl
                                                                                            .class)
                                                                            .readCdsExportDemographicSchema();
                                                            try {
                                                                return new CdsExportSourceSchemaValidator()
                                                                        .verify(schema, columns);
                                                            } catch (java.io.IOException invalid) {
                                                                throw new java.io
                                                                        .UncheckedIOException(
                                                                        invalid);
                                                            }
                                                        }))
                                .satisfies(
                                        failure -> {
                                            Throwable current = failure;
                                            while (current.getCause() != null
                                                    && !(current instanceof SQLException))
                                                current = current.getCause();
                                            assertThat(current).isInstanceOf(SQLException.class);
                                            assertThat(((SQLException) current).getErrorCode())
                                                    .isIn(1142, 1143);
                                        });
                    }
                }
            } finally {
                try {
                    if (userCreated) sql.execute("drop user '" + user + "'@'%'");
                } finally {
                    if (databaseCreated) sql.execute("drop database `" + clone + "`");
                }
            }
        }
    }

    private static BasicDataSource source(String url, String user, String password) {
        var source = new BasicDataSource();
        source.setUrl(url);
        source.setUsername(user);
        source.setPassword(password);
        source.setMaxTotal(2);
        return source;
    }

    private static AnnotationConfigApplicationContext context(DataSource source) {
        var context = new AnnotationConfigApplicationContext();
        context.register(Transactions.class);
        context.registerBean("dataSource", DataSource.class, () -> source);
        context.registerBean(
                "entityManagerFactory",
                LocalContainerEntityManagerFactoryBean.class,
                () -> {
                    var factory = new LocalContainerEntityManagerFactoryBean();
                    factory.setPersistenceUnitName("entityManagerFactory");
                    factory.setDataSource(source);
                    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
                    factory.setManagedTypes(
                            PersistenceManagedTypes.of(
                                    Demographic.class.getName(),
                                    DemographicCust.class.getName(),
                                    DemographicExt.class.getName(),
                                    Provider.class.getName()));
                    factory.setJpaPropertyMap(
                            Map.of(
                                    "hibernate.hbm2ddl.auto",
                                    "none",
                                    "hibernate.dialect",
                                    "org.hibernate.dialect.MariaDBDialect",
                                    "hibernate.jdbc.time_zone",
                                    "UTC"));
                    return factory;
                });
        context.registerBean(
                "transactionManager",
                PlatformTransactionManager.class,
                () -> new JpaTransactionManager(context.getBean(EntityManagerFactory.class)));
        context.registerBean(PersistenceAnnotationBeanPostProcessor.class);
        context.register(DemographicDaoImpl.class);
        context.refresh();
        return context;
    }
}
