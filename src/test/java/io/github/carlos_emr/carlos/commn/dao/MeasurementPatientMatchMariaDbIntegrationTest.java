/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementsExt;
import io.github.carlos_emr.carlos.util.persistence.OscarMySQL5Dialect;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import org.hibernate.cfg.Configuration;
import org.hibernate.JDBCException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Date;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the production DAO and dialect against physical MariaDB, not H2's MySQL mode.
 * CARLOS_TEST_MARIADB_URL is a server URL ending in / (no database or query string).
 * The supplied test account must be able to create/drop this suite's random database.
 * No existing schema is used. See docs/test/hibernate-mariadb-regressions.md.
 * @since 2026-10-28
 */
@Tag("integration")
@Tag("dao")
@Tag("mariadb-native")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_URL", matches = "jdbc:mysql:.*")
class MeasurementPatientMatchMariaDbIntegrationTest {
    private static final int PREVIOUS = 910001;
    private static final int PATIENT = 910002;
    private static final String LAB = "FAKE-5399-lab";
    private static final String DATABASE = "carlos_test_5399_" + UUID.randomUUID().toString().replace("-", "");
    private static final Date OBSERVED = new Date(1772620212000L);
    private static final Date ENTERED = new Date(1772623812000L);
    private static EntityManagerFactory factory;
    private static String serverUrl;
    private static Properties credentials;

    @BeforeAll
    static void openDatabase() throws SQLException {
        serverUrl = System.getenv("CARLOS_TEST_MARIADB_URL");
        assertThat(serverUrl).endsWith("/").doesNotContain("?");
        credentials = new Properties();
        credentials.setProperty("user", System.getenv().getOrDefault("CARLOS_TEST_MARIADB_USER", "root"));
        credentials.setProperty("password", System.getenv().getOrDefault("CARLOS_TEST_MARIADB_PASSWORD", ""));
        try (var connection = DriverManager.getConnection(serverUrl, credentials);
             var statement = connection.createStatement()) {
            assertThat(connection.getMetaData().getDatabaseProductVersion()).containsIgnoringCase("MariaDB");
            statement.executeUpdate("CREATE DATABASE " + DATABASE);
        }
        factory = new Configuration().addAnnotatedClass(Measurement.class).addAnnotatedClass(MeasurementsExt.class)
                .setProperty("hibernate.connection.driver_class", "com.mysql.cj.jdbc.Driver")
                .setProperty("hibernate.connection.url", serverUrl + DATABASE)
                .setProperty("hibernate.connection.username", credentials.getProperty("user"))
                .setProperty("hibernate.connection.password", credentials.getProperty("password"))
                .setProperty("hibernate.dialect", OscarMySQL5Dialect.class.getName())
                .setProperty("hibernate.hbm2ddl.auto", "create")
                .setProperty("hibernate.show_sql", "false")
                .buildSessionFactory();
    }

    @AfterAll
    static void closeDatabase() throws SQLException {
        try {
            if (factory != null) factory.close();
        } finally {
            if (serverUrl != null && credentials != null) {
                try (var connection = DriverManager.getConnection(serverUrl, credentials);
                     var statement = connection.createStatement()) {
                    statement.executeUpdate("DROP DATABASE IF EXISTS " + DATABASE);
                }
            }
        }
    }

    @BeforeEach
    void clearFixtures() {
        transaction(em -> {
            em.createQuery("delete from MeasurementsExt").executeUpdate();
            em.createQuery("delete from Measurement").executeUpdate();
            return null;
        });
    }

    @Test
    void shouldMoveOnlyTheSelectedMeasurementAndPreserveEveryValue_whenLabMatches() {
        int id = seed("lab_no", LAB);
        int neighbour = seed("lab_no", LAB);
        transaction(em -> {
            extension(em, id, "unit", "FAKE-mmol/L");
            extension(em, id, "lab_no", LAB); // Duplicate source rows must still update one measurement.
            Measurement measurement = em.find(Measurement.class, id);
            dao(em).reassignLabPatient(measurement, LAB, PATIENT);
            assertThat(measurement.getDemographicId()).isEqualTo(PATIENT); // refreshes the managed instance
            return null;
        });
        transaction(em -> {
            assertClinicalValues(em.find(Measurement.class, id), PATIENT);
            assertClinicalValues(em.find(Measurement.class, neighbour), PREVIOUS);
            assertThat(em.createQuery("select e.keyVal, e.val from MeasurementsExt e "
                            + "where e.measurementId=:id order by e.id", Object[].class)
                    .setParameter("id", id).getResultList())
                    .containsExactly(new Object[]{"lab_no", LAB}, new Object[]{"unit", "FAKE-mmol/L"},
                            new Object[]{"lab_no", LAB});
            return null;
        });
    }

    @Test
    void shouldRejectDifferentLab_whenMeasurementBelongsToAnotherSource() {
        assertSourceRejected(seed("lab_no", "FAKE-other-lab"));
    }

    @Test
    void shouldRejectDifferentKey_whenExtensionValueMatchesLabNumber() {
        assertSourceRejected(seed("unit", LAB));
    }

    @Test
    void shouldRejectMissingSource_whenAnotherMeasurementHasTheMatchingLab() {
        int id = seed(null, null);
        seed("lab_no", LAB);
        assertSourceRejected(id);
    }

    @Test
    void shouldRejectStaleOwnership_whenAnotherTransactionAlreadyMovedTheMeasurement() {
        int id = seed("lab_no", LAB);
        assertThatThrownBy(() -> transaction(em -> {
            Measurement stale = em.find(Measurement.class, id);
            transaction(other -> {
                dao(other).reassignLabPatient(other.find(Measurement.class, id), LAB, PATIENT);
                return null;
            });
            dao(em).reassignLabPatient(stale, LAB, 910003);
            return null;
        })).satisfies(failure -> {
            if (failure instanceof JDBCException jdbc) {
                // Under REPEATABLE READ, MariaDB may refuse the stale snapshot
                // before returning the update count. Both outcomes must fail closed.
                assertThat(jdbc.getErrorCode()).isEqualTo(1020);
                assertThat(jdbc.getSQLState()).isEqualTo("HY000");
            } else {
                assertThat(failure).isInstanceOf(IllegalStateException.class)
                        .hasMessage("Lab measurement ownership changed during correction");
            }
        });
        transaction(em -> {
            assertClinicalValues(em.find(Measurement.class, id), PATIENT);
            return null;
        });
    }

    @Test
    void shouldRollBackOwnershipAndCompanionWrites_whenLaterMatchingWorkFails() {
        int id = seed("lab_no", LAB);
        assertThatThrownBy(() -> transaction(em -> {
            extension(em, id, "FAKE-routing-marker", "FAKE-pending");
            dao(em).reassignLabPatient(em.find(Measurement.class, id), LAB, PATIENT);
            em.flush();
            throw new IllegalStateException("FAKE failure after correction");
        })).isInstanceOf(IllegalStateException.class).hasMessage("FAKE failure after correction");
        transaction(em -> {
            assertClinicalValues(em.find(Measurement.class, id), PREVIOUS);
            assertThat(em.createQuery("select count(e) from MeasurementsExt e", Long.class).getSingleResult())
                    .isEqualTo(1L);
            return null;
        });
    }

    @Test
    void shouldRejectUnmanagedRecordsAndInvalidPatients_whenCorrectionIsRequested() {
        int id = seed("lab_no", LAB);
        transaction(em -> {
            Measurement measurement = em.find(Measurement.class, id);
            MeasurementDaoImpl measurements = dao(em);
            for (int invalid : new int[]{0, -1}) {
                assertThatThrownBy(() -> measurements.reassignLabPatient(measurement, LAB, invalid))
                        .isInstanceOf(IllegalArgumentException.class);
            }
            assertThatThrownBy(() -> measurements.reassignLabPatient(null, LAB, PATIENT))
                    .isInstanceOf(IllegalArgumentException.class);
            em.detach(measurement);
            assertThatThrownBy(() -> measurements.reassignLabPatient(measurement, LAB, PATIENT))
                    .isInstanceOf(IllegalArgumentException.class);
            return null;
        });
    }

    @Test
    void shouldStillRejectClinicalEdits_whenMeasurementIsManaged() {
        int id = seed("lab_no", LAB);
        assertThatThrownBy(() -> transaction(em -> {
            em.find(Measurement.class, id).setDataField("999");
            em.flush();
            return null;
        })).isInstanceOf(UnsupportedOperationException.class);
        transaction(em -> {
            assertClinicalValues(em.find(Measurement.class, id), PREVIOUS);
            return null;
        });
    }

    @Test
    void shouldUseMariaDbCompatibleLocks_whenLoadingMeasurementForCorrection() {
        int id = seed("lab_no", LAB);
        for (LockModeType mode : new LockModeType[]{LockModeType.PESSIMISTIC_READ, LockModeType.PESSIMISTIC_WRITE}) {
            transaction(em -> {
                assertClinicalValues(em.find(Measurement.class, id, mode), PREVIOUS);
                return null;
            });
        }
    }

    @Test
    void shouldKeepNumericBoundsAndTimestampBindings_whenReadingMeasurements() {
        int id = seed("lab_no", LAB);
        transaction(em -> {
            MeasurementDaoImpl measurements = dao(em);
            assertThat(measurements.findByDemoNoDateTypeMeasuringInstrAndDataField(
                    PREVIOUS, ENTERED, "GLU", "FAKE fasting", "6", "5")).hasSize(1);
            assertThat(measurements.findByDemoNoDateTypeAndDataField(
                    PREVIOUS, ENTERED, "GLU", "6", "5")).hasSize(1);
            assertThat(measurements.findByDemoNoDateTypeAndDataField(
                    PREVIOUS, ENTERED, "GLU", "5", "1")).isEmpty();
            assertThat(measurements.findByValue("lab_no", LAB)).extracting(Measurement::getId).containsExactly(id);
            assertThat(measurements.getDatesForMeasurements(PREVIOUS, new String[]{"GLU"}))
                    .extracting(Date::getTime).containsExactly(OBSERVED.getTime());
            return null;
        });
    }

    private static MeasurementDaoImpl dao(EntityManager em) {
        MeasurementDaoImpl dao = new MeasurementDaoImpl();
        dao.entityManager = em;
        return dao;
    }

    private static <T> T transaction(Function<EntityManager, T> action) {
        try (EntityManager em = factory.createEntityManager()) {
            var tx = em.getTransaction();
            tx.begin();
            try {
                T result = action.apply(em);
                tx.commit();
                return result;
            } catch (RuntimeException | Error failure) {
                if (tx.isActive()) tx.rollback();
                throw failure;
            }
        }
    }

    private static int seed(String key, String value) {
        return transaction(em -> {
            Measurement measurement = new Measurement();
            measurement.setDemographicId(PREVIOUS);
            measurement.setType("GLU");
            measurement.setDataField("5.4");
            measurement.setComments("FAKE annotation");
            measurement.setMeasuringInstruction("FAKE fasting");
            measurement.setProviderNo("999001");
            measurement.setAppointmentNo(910004);
            measurement.setDateObserved(OBSERVED);
            measurement.setCreateDate(ENTERED);
            em.persist(measurement);
            if (key != null) extension(em, measurement.getId(), key, value);
            return measurement.getId();
        });
    }

    private static void extension(EntityManager em, int id, String key, String value) {
        MeasurementsExt extension = new MeasurementsExt(id);
        extension.setKeyVal(key);
        extension.setVal(value);
        em.persist(extension);
    }

    private static void assertClinicalValues(Measurement measurement, int patient) {
        assertThat(measurement.getDemographicId()).isEqualTo(patient);
        assertThat(measurement.getType()).isEqualTo("GLU");
        assertThat(measurement.getDataField()).isEqualTo("5.4");
        assertThat(measurement.getComments()).isEqualTo("FAKE annotation");
        assertThat(measurement.getMeasuringInstruction()).isEqualTo("FAKE fasting");
        assertThat(measurement.getProviderNo()).isEqualTo("999001");
        assertThat(measurement.getAppointmentNo()).isEqualTo(910004);
        assertThat(measurement.getDateObserved().getTime()).isEqualTo(OBSERVED.getTime());
        assertThat(measurement.getCreateDate().getTime()).isEqualTo(ENTERED.getTime());
    }

    private static void assertSourceRejected(int id) {
        assertThatThrownBy(() -> transaction(em -> {
            dao(em).reassignLabPatient(em.find(Measurement.class, id), LAB, PATIENT);
            return null;
        })).isInstanceOf(IllegalStateException.class);
        transaction(em -> {
            assertClinicalValues(em.find(Measurement.class, id), PREVIOUS);
            return null;
        });
    }
}
