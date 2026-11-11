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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.DriverManager;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingEdtObecOutputSpecificationRecordDto;
import io.github.carlos_emr.carlos.commn.dao.BatchEligibilityDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDaoImpl;
import io.github.carlos_emr.carlos.commn.model.BatchEligibility;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * #6140 on a real database: applying an OBEC output file never cuts the patient alert and never fails
 * the batch on it. {@code demographiccust.cust3} is VARCHAR(255). Under a strict {@code sql_mode} a
 * longer alert failed the merge, and with it the whole batch; under the packaged empty
 * {@code sql_mode} the column cut it, losing the newest eligibility message. Each mode is checked:
 * first that the column really does refuse or cut a longer alert, then that the apply leaves the stored
 * alert whole and says so, and that an alert at the limit is written whole.
 *
 * <p>Runs on MariaDB when {@code CARLOS_TEST_MARIADB_URL} names a server (the MariaDB DAO CI job), on
 * H2 in MySQL mode otherwise (strict only: H2 always refuses a longer value).</p>
 *
 * @since 2026-11-12
 */
@Tag("integration")
@Tag("dao")
@Tag("mariadb-native")
@DisplayName("OBEC apply and the patient alert column, on the database")
class BillingObecAlertDaoMariaDbIntegrationTest {

    private static final int PATIENT = 42;
    private static final String MESSAGE = "Invalid old version code: ZZ\nReason: Expired card- Renew card\nResponse Code: 60";

    private SessionFactory factory;
    private TransactionTemplate tx;
    private DemographicCustDaoImpl custDao;
    private BillingObecOutputApplyService service;
    private String server;
    private String database;
    private final Properties credentials = new Properties();

    /** Builds the schema in a fresh database whose sessions use the given mode. */
    private void open(String mode) throws Exception {
        String configuredServer = System.getenv("CARLOS_TEST_MARIADB_URL");
        if (configuredServer != null && !configuredServer.matches("jdbc:mariadb://[^/@?#;\\s]+/")) {
            throw new IllegalArgumentException("These tests require a jdbc:mariadb server URL ending in / without a database or query");
        }
        server = configuredServer;
        Assumptions.assumeTrue(server != null || "strict".equals(mode),
                "The packaged empty sql_mode needs MariaDB; H2 always refuses a longer value");
        database = "carlos_test_6140_" + UUID.randomUUID().toString().replace("-", "");
        credentials.setProperty("user", System.getenv().getOrDefault("CARLOS_TEST_MARIADB_USER", "root"));
        credentials.setProperty("password", System.getenv().getOrDefault("CARLOS_TEST_MARIADB_PASSWORD", ""));
        String url = "jdbc:h2:mem:" + database + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        if (server != null) {
            try (var connection = DriverManager.getConnection(server, credentials); var statement = connection.createStatement()) {
                assertThat(connection.getMetaData().getDatabaseProductVersion()).contains("MariaDB");
                statement.executeUpdate("CREATE DATABASE " + database);
            }
            url = server + database + ("strict".equals(mode)
                    ? "?sessionVariables=sql_mode='STRICT_ALL_TABLES'"
                    : "?sessionVariables=sql_mode='';jdbcCompliantTruncation=false");
        }
        var configuration = new Configuration().addAnnotatedClass(DemographicCust.class)
                .setProperty("hibernate.connection.driver_class", server == null ? "org.h2.Driver" : "org.mariadb.jdbc.Driver")
                .setProperty("hibernate.connection.url", url)
                .setProperty("hibernate.dialect", server == null ? "org.hibernate.dialect.H2Dialect"
                        : "io.github.carlos_emr.carlos.util.persistence.OscarMySQL5Dialect")
                .setProperty("hibernate.connection.username", server == null ? "sa" : credentials.getProperty("user"))
                .setProperty("hibernate.connection.password", server == null ? "" : credentials.getProperty("password"))
                .setProperty("hibernate.connection.pool_size", "2")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop");
        var vendor = new org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter();
        configuration.getProperties().putAll(vendor.getJpaPropertyMap());
        factory = configuration.buildSessionFactory();
        if (server != null) {
            // The production column, whatever Hibernate's default for a String.
            try (var connection = DriverManager.getConnection(server + database, credentials); var statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE demographiccust MODIFY cust3 VARCHAR(255) NULL");
            }
        }
        var transactions = new JpaTransactionManager(factory);
        transactions.setJpaDialect(vendor.getJpaDialect());
        tx = new TransactionTemplate(transactions);
        custDao = new DemographicCustDaoImpl();
        ReflectionTestUtils.setField(custDao, "entityManager", SharedEntityManagerCreator.createSharedEntityManager(factory));

        BatchEligibilityDao eligibility = mock(BatchEligibilityDao.class);
        BatchEligibility expired = mock(BatchEligibility.class);
        when(expired.getMOHResponse()).thenReturn("Expired card");
        when(expired.getReason()).thenReturn("Renew card");
        when(eligibility.find(60)).thenReturn(expired);
        DemographicManager demographics = mock(DemographicManager.class);
        Demographic patient = new Demographic();
        patient.setDemographicNo(PATIENT);
        patient.setVer("ZZ");
        when(demographics.searchByHealthCard(any(), eq("1234567890"))).thenReturn(List.of(patient));
        service = new BillingObecOutputApplyService(eligibility, custDao, demographics);
    }

    @AfterEach
    void drop() throws Exception {
        try {
            if (factory != null) factory.close();
        } finally {
            if (server != null && database != null) {
                try (var connection = DriverManager.getConnection(server, credentials); var statement = connection.createStatement()) {
                    statement.executeUpdate("DROP DATABASE IF EXISTS " + database);
                }
            }
        }
    }

    private void storeAlert(int patient, String alert) {
        tx.executeWithoutResult(status -> {
            DemographicCust cust = new DemographicCust();
            cust.setId(patient);
            cust.setAlert(alert);
            custDao.persist(cust);
        });
    }

    private String storedAlert(int patient) {
        return tx.execute(status -> custDao.find(patient).getAlert());
    }

    private BillingObecOutputApplyService.ApplyResult apply() {
        return tx.execute(status -> service.applyOutputSpec(mock(LoggedInInfo.class),
                List.of(new BillingEdtObecOutputSpecificationRecordDto("1234567890", "ZZ", "60"))));
    }

    /** Characters the alert reaches with the message appended, as the Master Record form counts it. */
    private static int formLength(String existing) {
        String alert = existing + "\r\n" + MESSAGE.replace("\n", "\r\n");
        return alert.codePointCount(0, alert.length());
    }

    @ParameterizedTest(name = "sql_mode {0}")
    @ValueSource(strings = {"strict", "packaged"})
    void shouldLeaveTheStoredAlertWhole_whenTheMessageWouldMakeItTooLong(String mode) throws Exception {
        open(mode);
        // The column itself, on another patient's row: a longer alert is refused (strict) or cut (packaged).
        int other = PATIENT + 1;
        String longer = "B".repeat(DemographicCust.ALERT_MAX_LENGTH + 1);
        if ("strict".equals(mode)) {
            assertThatThrownBy(() -> storeAlert(other, longer)).isInstanceOf(RuntimeException.class);
        } else {
            storeAlert(other, longer);
            assertThat(storedAlert(other)).hasSize(DemographicCust.ALERT_MAX_LENGTH);
        }

        String existing = "A".repeat(DemographicCust.ALERT_MAX_LENGTH - formLength("") + 1);
        storeAlert(PATIENT, existing);

        BillingObecOutputApplyService.ApplyResult result = apply();

        // Nothing is cut and the batch does not fail: the alert is as it was, and the result says why.
        assertThat(storedAlert(PATIENT)).isEqualTo(existing);
        assertThat(result.appliedCount()).isEqualTo(1);
        assertThat(result.reasons()).singleElement().asString().startsWith("Alert not updated for row 1:");
    }

    @ParameterizedTest(name = "sql_mode {0}")
    @ValueSource(strings = {"strict", "packaged"})
    void shouldStoreTheAlertWhole_whenItFillsTheLimitAsTheFormCountsIt(String mode) throws Exception {
        open(mode);
        String existing = "A".repeat(DemographicCust.ALERT_MAX_LENGTH - formLength(""));
        storeAlert(PATIENT, existing);

        BillingObecOutputApplyService.ApplyResult result = apply();

        assertThat(storedAlert(PATIENT)).isEqualTo(existing + "\n" + MESSAGE);
        assertThat(result.reasons()).isEmpty();
    }
}
