/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.mds.data;

import io.github.carlos_emr.carlos.commn.dao.InboxCategoryDao;
import io.github.carlos_emr.carlos.commn.dao.SystemPreferencesDao;
import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import io.github.carlos_emr.carlos.commn.service.InboxDateSearchTypeResolver;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Runs whole inbox populations of {@link CategoryData} against a real (H2, MySQL mode) database in
 * each {@code inboxDateSearchType} mode (issue #5772): the preference is read once per population,
 * and every lab, document and HRM count of that population, and the lazy abnormal count read after
 * it, compares the snapshot mode's date columns.
 *
 * <p>Each fixture item is inside the January window by exactly one of its two dates, so the two
 * modes count different, known sets. As in {@code CategoryDataNullHinCountUnitTest}, the only
 * dialect rewrite drops the MariaDB-only {@code SELECT HIGH_PRIORITY} hint, which H2 rejects; every
 * predicate, join and bound value is the production statement.</p>
 *
 * @since 2026-11-19
 */
@Tag("integration")
@Tag("lab")
@Tag("aggregate")
@DisplayName("CategoryData counts by the inbox date-search preference")
class CategoryDataDateSearchTypeIntegrationTest {
    private static final String PROVIDER = "999998";
    private static final int PATIENT = 601;
    private static final String RECEIVED = "receivedCreated";
    private static final String OBSERVATION = "serviceObservation";

    private final String url = "jdbc:h2:mem:categorydates_" + UUID.randomUUID().toString().replace("-", "")
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private Connection keepAlive;
    private org.hibernate.SessionFactory sessionFactory;
    private jakarta.persistence.EntityManager manager;
    private InboxCategoryDao dao;
    private SystemPreferencesDao preferences;

    @BeforeEach
    void setUpDatabase() throws SQLException {
        keepAlive = DriverManager.getConnection(url);
        try (Statement ddl = keepAlive.createStatement()) {
            ddl.execute("CREATE TABLE demographic (demographic_no INT PRIMARY KEY, last_name VARCHAR(30),"
                    + " first_name VARCHAR(30), hin VARCHAR(20))");
            ddl.execute("CREATE TABLE providerLabRouting (id INT AUTO_INCREMENT PRIMARY KEY, provider_no VARCHAR(6),"
                    + " lab_no INT, status CHAR(1), lab_type VARCHAR(3))");
            ddl.execute("CREATE TABLE patientLabRouting (id INT AUTO_INCREMENT PRIMARY KEY, demographic_no INT,"
                    + " lab_no INT, lab_type VARCHAR(3))");
            ddl.execute("CREATE TABLE hl7TextInfo (id INT AUTO_INCREMENT PRIMARY KEY, lab_no INT, result_status VARCHAR(1),"
                    + " accessionNum VARCHAR(20), obr_date VARCHAR(20))");
            ddl.execute("CREATE TABLE hl7TextMessage (lab_id INT PRIMARY KEY, created TIMESTAMP NOT NULL)");
            ddl.execute("CREATE TABLE ctl_document (module VARCHAR(30), module_id INT, document_no INT, status CHAR(1))");
            // Production observationdate is a DATE. MariaDB compares it with the DAO's
            // 'yyyy-MM-dd HH:mm:ss' bounds, but H2 cannot parse those as a DATE, so the fixture stores
            // the same calendar days as midnight timestamps. LegacyLabNativeMariaDbIntegrationTest
            // covers the physical DATE column.
            ddl.execute("CREATE TABLE document (document_no INT PRIMARY KEY, observationdate TIMESTAMP,"
                    + " contentdatetime TIMESTAMP, abnormal INT)");
            ddl.execute("CREATE TABLE HRMDocument (id INT PRIMARY KEY, timeReceived TIMESTAMP)");
            ddl.execute("CREATE TABLE HRMDocumentToProvider (id INT AUTO_INCREMENT PRIMARY KEY, providerNo VARCHAR(20),"
                    + " hrmDocumentId INT, signedOff INT)");
            ddl.execute("CREATE TABLE HRMDocumentToDemographic (id INT AUTO_INCREMENT PRIMARY KEY, demographicNo INT,"
                    + " hrmDocumentId INT)");
        }
        insert("INSERT INTO demographic VALUES (?, 'FAKE-Dates', 'Synthetic', NULL)", PATIENT);
        // Labs: 41 observed in January, received in March; 42 observed in December, received in
        // January (normal result); 43 is unmatched, observed in January, received in March.
        lab(41, "A", "2026-01-10", "2026-03-05 10:00:00", true);
        lab(42, null, "2025-12-01", "2026-01-10 09:00:00", true);
        lab(43, "A", "2026-01-10", "2026-03-05 10:00:00", false);
        // Documents: 51 observed in January, created in March; 52 observed in December, created in
        // January; 53 is unmatched, observed in March, created in January.
        document(51, PATIENT, "2026-01-10", "2026-03-05 10:00:00");
        document(52, PATIENT, "2025-12-01", "2026-01-10 09:00:00");
        document(53, -1, "2026-03-05", "2026-01-10 08:00:00");
        // HRM counts compare the received time in both modes: one matched, one unmatched report.
        insert("INSERT INTO HRMDocument VALUES (61, '2026-01-12 10:00:00')");
        insert("INSERT INTO HRMDocument VALUES (62, '2026-01-13 10:00:00')");
        insert("INSERT INTO HRMDocumentToProvider (providerNo, hrmDocumentId, signedOff) VALUES (?, 61, 0)", PROVIDER);
        insert("INSERT INTO HRMDocumentToProvider (providerNo, hrmDocumentId, signedOff) VALUES (?, 62, 0)", PROVIDER);
        insert("INSERT INTO HRMDocumentToDemographic (demographicNo, hrmDocumentId) VALUES (?, 61)", PATIENT);

        sessionFactory = new org.hibernate.cfg.Configuration()
                .setProperty("hibernate.connection.url", url)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setStatementInspector(sql -> sql.replace("SELECT HIGH_PRIORITY ", "SELECT "))
                .buildSessionFactory();
        manager = sessionFactory.createEntityManager();
        dao = new InboxCategoryDao();
        ReflectionTestUtils.setField(dao, "entityManager", manager);
        preferences = mock(SystemPreferencesDao.class);
    }

    private void lab(int labNo, String resultStatus, String observed, String received, boolean matched) throws SQLException {
        insert("INSERT INTO hl7TextInfo (lab_no, result_status, accessionNum, obr_date) VALUES (?, ?, ?, ?)",
                labNo, resultStatus, "FAKE-" + labNo, observed);
        insert("INSERT INTO hl7TextMessage VALUES (?, ?)", labNo, received);
        insert("INSERT INTO providerLabRouting (provider_no, lab_no, status, lab_type) VALUES (?, ?, 'N', 'HL7')",
                PROVIDER, labNo);
        if (matched) {
            insert("INSERT INTO patientLabRouting (demographic_no, lab_no, lab_type) VALUES (?, ?, 'HL7')", PATIENT, labNo);
        }
    }

    private void document(int documentNo, int moduleId, String observed, String created) throws SQLException {
        insert("INSERT INTO document VALUES (?, ?, ?, 0)", documentNo, observed, created);
        insert("INSERT INTO ctl_document VALUES ('demographic', ?, ?, 'A')", moduleId, documentNo);
        insert("INSERT INTO providerLabRouting (provider_no, lab_no, status, lab_type) VALUES (?, ?, 'N', 'DOC')",
                PROVIDER, documentNo);
    }

    private void insert(String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = keepAlive.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.executeUpdate();
        }
    }

    @AfterEach
    void tearDownDatabase() throws SQLException {
        manager.close();
        sessionFactory.close();
        try (Statement drop = keepAlive.createStatement()) {
            drop.execute("SHUTDOWN");
        }
        keepAlive.close();
    }

    /** Stores the preference values the successive reads return; null means no stored row. */
    private void preferenceReads(String first, String... later) {
        SystemPreferences firstRow = row(first);
        SystemPreferences[] laterRows = new SystemPreferences[later.length];
        for (int i = 0; i < later.length; i++) {
            laterRows[i] = row(later[i]);
        }
        when(preferences.findPreferenceByName(SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS.inboxDateSearchType))
                .thenReturn(firstRow, laterRows);
    }

    private static SystemPreferences row(String value) {
        if (value == null) {
            return null;
        }
        SystemPreferences preference = new SystemPreferences();
        preference.setValue(value);
        return preference;
    }

    private CategoryData januaryInbox() {
        return new CategoryData(dao, new InboxDateSearchTypeResolver(preferences), "", "", "", false, true, PROVIDER,
                "N", "all", "2026-01-01", "2026-01-31");
    }

    private void verifyPreferenceReads(int reads) {
        verify(preferences, times(reads))
                .findPreferenceByName(SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS.inboxDateSearchType);
    }

    @Test
    void shouldCountByObservationDates_whenNoPreferenceIsStored() throws SQLException {
        preferenceReads(null);
        CategoryData data = januaryInbox();

        data.populateCountsAndPatients();

        // Lab 41 (matched) and 43 (unmatched); document 51; HRM 61 and 62.
        assertThat(data.getTotalLabs()).isEqualTo(2);
        assertThat(data.getUnmatchedLabs()).isEqualTo(1);
        assertThat(data.getUnmatchedDocs()).isZero();
        assertThat(data.getMatchedHRMCount()).isEqualTo(1);
        assertThat(data.getUnmatchedHRMCount()).isEqualTo(1);
        assertThat(data.getTotalDocs()).isEqualTo(3);
        assertThat(data.getAbnormalCount()).isEqualTo(2);
        assertThat(data.getNormalCount()).isZero();
        assertThat(data.getDateSearchType()).isEqualTo(OBSERVATION);
        assertThat(data.getPatientList()).singleElement().satisfies(patient -> {
            assertThat(patient.getId()).isEqualTo(PATIENT);
            assertThat(patient.getLabCount()).isEqualTo(1);
            assertThat(patient.getDocCount()).isEqualTo(2); // document 51 plus HRM 61
            assertThat(patient.getHrmCount()).isEqualTo(1);
        });
        verifyPreferenceReads(1);
    }

    @Test
    void shouldCountByReceivedDates_whenPreferenceSelectsReceivedCreated() throws SQLException {
        preferenceReads(RECEIVED);
        CategoryData data = januaryInbox();

        data.populateCountsAndPatients();

        // Lab 42 (matched, normal); documents 52 and 53; HRM 61 and 62.
        assertThat(data.getTotalLabs()).isEqualTo(1);
        assertThat(data.getUnmatchedLabs()).isZero();
        assertThat(data.getUnmatchedDocs()).isEqualTo(1);
        assertThat(data.getTotalDocs()).isEqualTo(4);
        assertThat(data.getAbnormalCount()).isZero();
        assertThat(data.getNormalCount()).isEqualTo(1);
        assertThat(data.getDateSearchType()).isEqualTo(RECEIVED);
        assertThat(data.getPatientList()).singleElement().satisfies(patient -> {
            assertThat(patient.getLabCount()).isEqualTo(1);
            assertThat(patient.getDocCount()).isEqualTo(2); // document 52 plus HRM 61
        });
        verifyPreferenceReads(1);
    }

    @Test
    void shouldKeepOneMode_whenPreferenceChangesDuringAPopulationAndItsLazyCount() throws SQLException {
        // Every read after the first sees the other mode: a count that re-read the preference would
        // change the totals below, and normal = total - abnormal would go negative.
        preferenceReads(RECEIVED, OBSERVATION);
        CategoryData data = januaryInbox();

        data.populateCountsAndPatients();

        assertThat(data.getTotalLabs()).isEqualTo(1);
        assertThat(data.getTotalDocs()).isEqualTo(4);
        assertThat(data.getAbnormalCount()).isZero();
        assertThat(data.getNormalCount()).isEqualTo(1);
        verifyPreferenceReads(1);

        // The next population takes a new snapshot.
        data.populateCountsAndPatients();

        assertThat(data.getTotalLabs()).isEqualTo(2);
        assertThat(data.getTotalDocs()).isEqualTo(3);
        assertThat(data.getAbnormalCount()).isEqualTo(2);
        assertThat(data.getNormalCount()).isZero();
        assertThat(data.getDateSearchType()).isEqualTo(OBSERVATION);
        verifyPreferenceReads(2);
    }
}
