/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.lab.ca.all.util;

import io.github.carlos_emr.carlos.commn.dao.OscarKeyDao;
import io.github.carlos_emr.carlos.commn.dao.OscarKeyDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.PublicKeyDao;
import io.github.carlos_emr.carlos.commn.dao.PublicKeyDaoImpl;
import io.github.carlos_emr.carlos.commn.model.OscarKey;
import io.github.carlos_emr.carlos.commn.model.PublicKey;
import io.github.carlos_emr.carlos.lab.ca.all.util.KeyPairGen.ClientKeyStatus;
import io.github.carlos_emr.carlos.lab.ca.all.util.KeyPairGen.ClientKeys;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Integration test for {@link KeyPairGen#createClientKeys(String, String)} against a real
 * database: refusing a client key because the CARLOS ("oscar") key is missing must leave no
 * {@code publicKeys} row, so a retry with the same service name succeeds (issue #6009).
 *
 * <p>Like {@code PregnancyTransactionIntegrationTest}, this uses an isolated harness instead of
 * the full {@code CarlosTestBase} context, whose persistence unit does not map {@link OscarKey}:
 * a unique H2 database (MySQL mode), the production DAO classes behind real Spring transaction
 * proxies, and real commits. Only the static {@link SpringUtils} lookup that {@link KeyPairGen}
 * uses is substituted, and it answers with those real DAOs.</p>
 *
 * <p>The two tables are the {@code CREATE TABLE} statements of the Flyway baseline, as in
 * {@code FrmRecordCheckboxRoundTripIntegrationTest}, not a schema Hibernate generates from the
 * entities: the entities declare no column lengths, so a generated {@code oscarKeys.privKey}
 * would be {@code VARCHAR(255)} and refuse the RSA-2048 key the shipped {@code TEXT} column
 * holds. The database is destroyed when the class closes its last connection.</p>
 *
 * @since 2026-11-09
 */
@Tag("integration")
@Tag("lab")
@DisplayName("KeyPairGen.createClientKeys against a real database")
class KeyPairGenCreateClientKeysIntegrationTest {

    private static final String SERVICE = "fake-lab.integration";
    private static final Path BASELINE = Path.of("database/mysql/migration/common/V1__baseline_schema.sql");

    /** Held open for the whole class: the in-memory database lives until it closes. */
    private static Connection keeper;
    private static EntityManagerFactory factory;
    private static AnnotationConfigApplicationContext context;

    private MockedStatic<SpringUtils> springUtils;

    /** Turns on the {@code @Transactional} proxies the DAO classes rely on in production. */
    @Configuration
    @EnableTransactionManagement
    static class TransactionConfig {
    }

    @BeforeAll
    static void openDatabase() throws SQLException, IOException {
        String url = "jdbc:h2:mem:keypairgen_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false";
        keeper = DriverManager.getConnection(url, "sa", "");
        createBaselineTables("oscarKeys", "publicKeys");

        factory = new org.hibernate.cfg.Configuration()
                .addAnnotatedClass(OscarKey.class)
                .addAnnotatedClass(PublicKey.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", url)
                .setProperty("hibernate.connection.username", "sa")
                .setProperty("hibernate.connection.password", "")
                .setProperty("hibernate.hbm2ddl.auto", "none")
                .setProperty("hibernate.show_sql", "false")
                .buildSessionFactory();

        context = new AnnotationConfigApplicationContext();
        // The DAOs inject @PersistenceContext(unitName = "entityManagerFactory"), resolved by bean name.
        context.getBeanFactory().registerSingleton("entityManagerFactory", factory);
        context.registerBean("transactionManager", JpaTransactionManager.class, () -> new JpaTransactionManager(factory));
        context.register(TransactionConfig.class);
        context.registerBean(PublicKeyDaoImpl.class);
        context.registerBean(OscarKeyDaoImpl.class);
        context.refresh();
    }

    @AfterAll
    static void closeDatabase() throws SQLException {
        if (context != null) {
            context.close();
        }
        if (factory != null) {
            factory.close();
        }
        if (keeper != null) {
            keeper.close();
        }
    }

    /**
     * Creates each named table from its {@code CREATE TABLE} statement in the baseline schema. A
     * statement runs from its opening line to the first line that starts with the closing
     * parenthesis; the trailing {@code ENGINE=...} options are MySQL-only and are dropped.
     */
    private static void createBaselineTables(String... tables) throws IOException, SQLException {
        String baseline = Files.readString(BASELINE, StandardCharsets.UTF_8);
        try (Statement statement = keeper.createStatement()) {
            for (String table : tables) {
                int start = baseline.indexOf("CREATE TABLE `" + table + "` (");
                assertThat(start).as("CREATE TABLE of " + table + " in the baseline schema").isGreaterThanOrEqualTo(0);
                int end = baseline.indexOf("\n)", start);
                assertThat(end).as("end of the CREATE TABLE of " + table).isGreaterThan(start);
                statement.execute(baseline.substring(start, end + 2));
            }
        }
    }

    @BeforeEach
    void routeSpringLookupsToRealDaos() {
        springUtils = mockStatic(SpringUtils.class);
        springUtils.when(() -> SpringUtils.getBean(PublicKeyDao.class)).thenReturn(context.getBean(PublicKeyDao.class));
        springUtils.when(() -> SpringUtils.getBean(OscarKeyDao.class)).thenReturn(context.getBean(OscarKeyDao.class));
    }

    @AfterEach
    void clearKeys() {
        springUtils.close();
        var em = factory.createEntityManager();
        try {
            em.getTransaction().begin();
            em.createQuery("DELETE FROM PublicKey").executeUpdate();
            em.createQuery("DELETE FROM OscarKey").executeUpdate();
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }

    private static long clientKeyRows() {
        var em = factory.createEntityManager();
        try {
            return em.createQuery("SELECT COUNT(k) FROM PublicKey k WHERE k.service = :service", Long.class)
                    .setParameter("service", SERVICE).getSingleResult();
        } finally {
            em.close();
        }
    }

    @Test
    @DisplayName("should leave no client key row and allow a retry once the CARLOS key exists")
    void shouldAllowRetry_whenFirstAttemptFoundNoCarlosKey() {
        ClientKeys refused = KeyPairGen.createClientKeys(SERVICE, "CML");

        assertThat(refused.status()).isEqualTo(ClientKeyStatus.CARLOS_KEY_MISSING);
        assertThat(clientKeyRows()).isZero();
        assertThat(KeyPairGen.checkName(SERVICE)).as("the refused name must still be free").isFalse();

        assertThat(KeyPairGen.createKeys("oscar", null)).isEqualTo("success");
        ClientKeys retried = KeyPairGen.createClientKeys(SERVICE, "CML");

        assertThat(retried.status()).isEqualTo(ClientKeyStatus.CREATED);
        assertThat(clientKeyRows()).isOne();
        assertThat(KeyPairGen.checkName(SERVICE)).isTrue();

        var em = factory.createEntityManager();
        try {
            PublicKey stored = em.find(PublicKey.class, SERVICE);
            OscarKey carlos = em.find(OscarKey.class, "oscar");
            assertThat(retried.clientPrivateKey()).isEqualTo(stored.getBase64EncodedPrivateKey());
            assertThat(retried.carlosPublicKey()).isNotBlank().isEqualTo(carlos.getPublicKey());
            assertThat(stored.getType()).isEqualTo("CML");
        } finally {
            em.close();
        }
    }

    @Test
    @DisplayName("should report a failure and keep the first key when the service name is already stored")
    void shouldReportFailure_whenServiceNameAlreadyStored() {
        assertThat(KeyPairGen.createKeys("oscar", null)).isEqualTo("success");
        ClientKeys first = KeyPairGen.createClientKeys(SERVICE, "CML");

        // The page checks the name first; this is the race a double submission can still reach.
        ClientKeys second = KeyPairGen.createClientKeys(SERVICE, "CML");

        assertThat(first.status()).isEqualTo(ClientKeyStatus.CREATED);
        assertThat(second.status()).isEqualTo(ClientKeyStatus.FAILED);
        assertThat(clientKeyRows()).isOne();
        var em = factory.createEntityManager();
        try {
            assertThat(em.find(PublicKey.class, SERVICE).getBase64EncodedPrivateKey()).isEqualTo(first.clientPrivateKey());
        } finally {
            em.close();
        }
    }
}
