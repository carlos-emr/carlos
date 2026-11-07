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
package io.github.carlos_emr.carlos.demographic.cds.cli;

import io.github.carlos_emr.carlos.db.FlywaySchemaValidator;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsJdbcAdmissionInitializer;
import io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.time.Duration;
import java.util.Properties;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

/** Shared one-shot persistence factories; no servlet/feed/fax scheduler or implicit migration is started. */
public final class CdsProcessPersistence {
    private CdsProcessPersistence() { }
    public static OscarTrackingBasicDataSource dataSource(Properties properties) {
        OscarTrackingBasicDataSource source = new OscarTrackingBasicDataSource();
        source.setBoundedCdsConnections(true);
        source.setDriverClassName(required(properties, "db_driver"));
        String metadata = properties.getProperty("db_default_use_information_schema");
        if (metadata != null && !metadata.isBlank()) source.setDefaultUseInformationSchema(
                org.springframework.core.convert.support.DefaultConversionService.getSharedInstance().convert(metadata, Boolean.class));
        source.setUrl(required(properties, "db_uri") + required(properties, "db_name"));
        source.setUsername(required(properties, "db_username"));
        source.setPassword(required(properties, "db_password"));
        source.setDefaultAutoCommit(true);
        source.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        // This dedicated context changes transaction state through JDBC setters,
        // including Spring/Hibernate. Raw SQL must not change that cached state.
        source.addConnectionProperty("useLocalSessionState", "true");
        // This pool serves only one-shot data transfers. Establish an additive strict baseline once
        // per physical connection; guarded units still verify and restore sessions.
        var initialization=new java.util.ArrayList<String>();
        if (source.getConnectionInitSqls()!=null) initialization.addAll(source.getConnectionInitSqls());
        initialization.add("SET SESSION sql_mode=IF(FIND_IN_SET('STRICT_ALL_TABLES',@@SESSION.sql_mode)>0,"
                + "@@SESSION.sql_mode,CONCAT_WS(',',NULLIF(@@SESSION.sql_mode,''),'STRICT_ALL_TABLES'))");
        source.setConnectionInitSqls(initialization);
        // CLI has one foreground worker and a bounded progress reader, never the
        // web server's full pool or abandoned-transaction recovery heuristics.
        source.setMaxTotal(4); source.setMaxIdle(2); source.setMinIdle(0);
        source.setMaxWait(Duration.ofSeconds(30));
        source.setValidationQuery("SELECT 1"); source.setValidationQueryTimeout(Duration.ofSeconds(10));
        source.setTestOnBorrow(true); source.setPoolPreparedStatements(false);
        return source;
    }
    public static FlywaySchemaValidator schemaGate(OscarTrackingBasicDataSource source, Properties properties) {
        // Validation only: this command never applies an implicit migration.
        return new FlywaySchemaValidator(source, "validate", properties.getProperty("carlos.flyway.locations", ""),
                properties.getProperty("billregion", "ON"));
    }
    public static CdsJdbcAdmissionInitializer admissionInitializer(OscarTrackingBasicDataSource source) {
        // This dedicated runtime always understands the guard, including status
        // queries after admission is disabled. It never starts ordinary writers.
        return new CdsJdbcAdmissionInitializer(source, true, "validate");
    }
    public static LocalContainerEntityManagerFactoryBean entityManagerFactory(OscarTrackingBasicDataSource source, Properties properties) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(source); factory.setPersistenceUnitName("entityManagerFactory");
        // Use the same association-complete model packages as spring_jpa.xml.
        factory.setPackagesToScan("io.github.carlos_emr.carlos.commn.model", "io.github.carlos_emr.carlos.model",
                "io.github.carlos_emr.carlos.PMmodule.model", "io.github.carlos_emr.carlos.casemgmt.model",
                "io.github.carlos_emr.carlos.entities", "io.github.carlos_emr.carlos.billing",
                "io.github.carlos_emr.carlos.billings", "io.github.carlos_emr.carlos.hospitalReportManager.model",
                "io.github.carlos_emr.carlos.form.model", "io.github.carlos_emr.carlos.decisionSupport.model");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Properties jpa = new Properties();
        jpa.setProperty("hibernate.dialect", properties.getProperty("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect"));
        jpa.setProperty("hibernate.show_sql", "false"); jpa.setProperty("hibernate.generate_statistics", "false");
        jpa.setProperty("hibernate.cache.use_second_level_cache", "false");
        jpa.setProperty("hibernate.cache.use_query_cache", "false");
        jpa.setProperty("hibernate.hbm2ddl.auto", "none");
        jpa.setProperty("hibernate.max_fetch_depth", "3");
        factory.setJpaProperties(jpa); return factory;
    }
    public static PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
        return new JpaTransactionManager(factory);
    }
    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("CDS_DATABASE_CONFIGURATION_REQUIRED");
        return value;
    }
}
