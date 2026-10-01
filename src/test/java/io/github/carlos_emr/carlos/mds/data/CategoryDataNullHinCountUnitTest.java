/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.mds.data;

import io.github.carlos_emr.carlos.commn.dao.SystemPreferencesDao;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Runs the inbox summary count queries of {@link CategoryData} against a real (H2, MySQL mode)
 * database so SQL {@code NULL} semantics are exercised, not just the SQL text.
 *
 * <p>A patient whose {@code demographic.hin} is NULL must be counted by a name search that
 * supplies no health number, since {@code d.hin LIKE '%%'} is never true for NULL; and must be
 * excluded once a non-empty health number is supplied. This mirrors the list-side coverage in
 * {@code InboxResultsDaoIntegrationTest} for the abnormal, lab and document summary counts.
 *
 * <p>The Spring/H2 integration context cannot run these statements as written, so the test
 * DataSource makes two dialect-only rewrites and nothing else: it drops the MySQL-only
 * {@code SELECT HIGH_PRIORITY} scheduling hint, and qualifies the lab query's bare
 * {@code GROUP BY demographic_no} as {@code d.demographic_no} (H2 reports the bare name as
 * ambiguous between {@code patientLabRouting} and {@code demographic}; the join condition and the
 * {@code d.last_name} filter make the two equal on every counted row). Every predicate, join and
 * bound value is the production statement.
 */
@Tag("unit")
@Tag("lab")
@Tag("aggregate")
@DisplayName("CategoryData summary counts for a patient without a HIN")
class CategoryDataNullHinCountUnitTest extends CarlosUnitTestBase {
    private static final String PROVIDER = "999998";
    private static final int PATIENT = 501;
    private static final int OTHER_PATIENT = 502;

    private final String url = "jdbc:h2:mem:categorydata_" + UUID.randomUUID().toString().replace("-", "")
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private Connection keepAlive;

    @BeforeEach
    void setUpDatabase() throws SQLException {
        keepAlive = DriverManager.getConnection(url);
        try (Statement ddl = keepAlive.createStatement()) {
            ddl.execute("CREATE TABLE demographic (demographic_no INT PRIMARY KEY, last_name VARCHAR(30),"
                    + " first_name VARCHAR(30), hin VARCHAR(20))");
            ddl.execute("CREATE TABLE ctl_document (module VARCHAR(30), module_id INT, document_no INT, status CHAR(1))");
            ddl.execute("CREATE TABLE providerLabRouting (id INT AUTO_INCREMENT PRIMARY KEY, provider_no VARCHAR(6),"
                    + " lab_no INT, status CHAR(1), lab_type VARCHAR(3))");
            ddl.execute("CREATE TABLE patientLabRouting (id INT AUTO_INCREMENT PRIMARY KEY, demographic_no INT,"
                    + " lab_no INT, lab_type VARCHAR(3))");
            ddl.execute("CREATE TABLE hl7TextInfo (id INT AUTO_INCREMENT PRIMARY KEY, lab_no INT, result_status VARCHAR(1),"
                    + " accessionNum VARCHAR(20), obr_date VARCHAR(20))");

            // The patient under test has no health number; a second patient with the same name has one.
            ddl.execute("INSERT INTO demographic VALUES (" + PATIENT + ", 'Synthetic', 'Nohin', NULL)");
            ddl.execute("INSERT INTO demographic VALUES (" + OTHER_PATIENT + ", 'Synthetic', 'Nohin', '9876543210')");
            // One document for each patient.
            ddl.execute("INSERT INTO ctl_document VALUES ('demographic', " + PATIENT + ", 11, 'A')");
            ddl.execute("INSERT INTO ctl_document VALUES ('demographic', " + OTHER_PATIENT + ", 12, 'A')");
            ddl.execute("INSERT INTO providerLabRouting (provider_no, lab_no, status, lab_type) VALUES ('" + PROVIDER + "', 11, 'N', 'DOC')");
            ddl.execute("INSERT INTO providerLabRouting (provider_no, lab_no, status, lab_type) VALUES ('" + PROVIDER + "', 12, 'N', 'DOC')");
            // One abnormal HL7 lab for each patient.
            ddl.execute("INSERT INTO patientLabRouting (demographic_no, lab_no, lab_type) VALUES (" + PATIENT + ", 21, 'HL7')");
            ddl.execute("INSERT INTO patientLabRouting (demographic_no, lab_no, lab_type) VALUES (" + OTHER_PATIENT + ", 22, 'HL7')");
            ddl.execute("INSERT INTO providerLabRouting (provider_no, lab_no, status, lab_type) VALUES ('" + PROVIDER + "', 21, 'N', 'HL7')");
            ddl.execute("INSERT INTO providerLabRouting (provider_no, lab_no, status, lab_type) VALUES ('" + PROVIDER + "', 22, 'N', 'HL7')");
            ddl.execute("INSERT INTO hl7TextInfo (lab_no, result_status, accessionNum) VALUES (21, 'A', 'ACC-21')");
            ddl.execute("INSERT INTO hl7TextInfo (lab_no, result_status, accessionNum) VALUES (22, 'A', 'ACC-22')");
        }
        registerMock(DataSource.class, hintStrippingDataSource());
        registerMock(SystemPreferencesDao.class, mock(SystemPreferencesDao.class));
        registerMock(EntityManagerFactory.class, mock(EntityManagerFactory.class));
    }

    @AfterEach
    void tearDownDatabase() throws SQLException {
        LegacyJdbcQuery.releaseThreadResources();
        try (Statement drop = keepAlive.createStatement()) {
            drop.execute("SHUTDOWN");
        }
        keepAlive.close();
    }

    @Test
    @DisplayName("should count a patient without a HIN when the search supplies no HIN")
    void shouldCountPatientWithoutHin_whenNoHinSupplied() throws SQLException {
        CategoryData data = search("");

        assertThat(data.getDocumentCountForPatientSearch()).isEqualTo(2);
        assertThat(data.getLabCountForPatientSearch()).isEqualTo(2);
        assertThat(data.getAbnormalCount(true)).isEqualTo(2);
        assertThat(data.getPatientList()).extracting(PatientInfo::getId)
                .containsExactlyInAnyOrder(PATIENT, OTHER_PATIENT);
    }

    @Test
    @DisplayName("should exclude a patient without a HIN when the search supplies a HIN")
    void shouldExcludePatientWithoutHin_whenHinSupplied() throws SQLException {
        CategoryData data = search("9876543210");

        assertThat(data.getDocumentCountForPatientSearch()).isEqualTo(1);
        assertThat(data.getLabCountForPatientSearch()).isEqualTo(1);
        assertThat(data.getAbnormalCount(true)).isEqualTo(1);
        assertThat(data.getPatientList()).extracting(PatientInfo::getId).containsExactly(OTHER_PATIENT);
    }

    @Test
    @DisplayName("should count nothing when the supplied HIN matches no patient")
    void shouldCountNothing_whenSuppliedHinMatchesNoPatient() throws SQLException {
        CategoryData data = search("1111111111");

        assertThat(data.getDocumentCountForPatientSearch()).isZero();
        assertThat(data.getLabCountForPatientSearch()).isZero();
        assertThat(data.getAbnormalCount(true)).isZero();
        assertThat(data.getPatientList()).isEmpty();
    }

    private static CategoryData search(String hin) {
        return new CategoryData("Synthetic", "Nohin", hin, true, true, PROVIDER, "N", "all", null, null);
    }

    /** Opens real H2 connections whose statements carry only the dialect rewrites described above. */
    private DataSource hintStrippingDataSource() {
        DataSource dataSource = mock(DataSource.class, invocation -> {
            if (!"getConnection".equals(invocation.getMethod().getName())) return null;
            Connection real = DriverManager.getConnection(url);
            return Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                    (proxy, method, args) -> {
                        if ("prepareStatement".equals(method.getName()) && args != null && args[0] instanceof String sql) {
                            args[0] = sql.replace("SELECT HIGH_PRIORITY ", "SELECT ")
                                    .replace("GROUP BY demographic_no,", "GROUP BY d.demographic_no,");
                        }
                        try {
                            return method.invoke(real, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        });
        return dataSource;
    }
}
