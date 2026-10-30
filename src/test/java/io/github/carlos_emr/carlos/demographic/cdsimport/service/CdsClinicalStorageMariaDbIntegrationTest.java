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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;

/** Real native ORM storage and rollback proof against an explicitly selected synthetic schema. */
@Tag("integration")
class CdsClinicalStorageMariaDbIntegrationTest {
    private static SessionFactory factory;
    @TempDir Path directory;
    private final CdsFieldParser fields = new CdsFieldParser();
    private final CdsClinicalNormalizationService normalization =
            new CdsClinicalNormalizationService(fields);

    @BeforeAll
    static void openFactory() throws Exception {
        String selector = System.getProperty("cds.test.properties");
        Assumptions.assumeTrue(
                selector != null && !selector.isBlank(),
                "Explicit isolated MariaDB tier requires protected cds.test.properties");
        Properties properties = new Properties();
        try (var input = Files.newInputStream(Path.of(selector))) {
            properties.load(input);
        }
        String jdbcUrl = properties.getProperty("jdbcUrl").replace("jdbc:mariadb:", "jdbc:mysql:");
        var dataSource =
                new DriverManagerDataSource(
                        jdbcUrl,
                        properties.getProperty("jdbcUser"),
                        properties.getProperty("jdbcPassword"));
        try (var connection = dataSource.getConnection();
                var query = connection.createStatement();
                var result = query.executeQuery("SELECT DATABASE()")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).matches("[A-Za-z0-9_]*(test|fixture)[A-Za-z0-9_]*");
        }
        Configuration configuration = new Configuration();
        for (Class<?> model :
                new Class<?>[] {
                    Drug.class,
                    Contact.class,
                    DemographicContact.class,
                    Measurement.class,
                    io.github.carlos_emr.carlos.commn.model.MeasurementsDeleted.class,
                    MeasurementsExt.class,
                    MeasurementType.class,
                    Validations.class,
                    Prevention.class,
                    PreventionExt.class,
                    PartialDate.class
                }) configuration.addAnnotatedClass(model);
        configuration.getProperties().put("hibernate.connection.datasource", dataSource);
        configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect");
        configuration.setProperty("hibernate.hbm2ddl.auto", "none");
        configuration.setProperty("hibernate.boot.allow_jdbc_metadata_access", "false");
        configuration.setProperty("hibernate.jdbc.time_zone", "UTC");
        configuration.setProperty("hibernate.show_sql", "false");
        factory = configuration.buildSessionFactory();
    }

    @AfterAll
    static void closeFactory() {
        if (factory != null) factory.close();
    }

    @Test
    void shouldKeepLongMeasurementValues_whenArchivedThroughTheNativeOrm() {
        String value = "x".repeat(300);
        try (Session session = factory.openSession()) {
            var transaction = session.beginTransaction();
            try {
                var source = new Measurement();
                source.setDemographicId(974233);
                source.setType("WT");
                source.setProviderNo("999998");
                source.setCreateDate(new java.util.Date());
                source.setDateObserved(new java.util.Date());
                source.setDataField(value);
                session.persist(source);
                session.flush();
                var archived = new io.github.carlos_emr.carlos.commn.model.MeasurementsDeleted(source);
                session.persist(archived);
                session.flush();
                Integer id = archived.getId();
                session.clear();
                assertThat(session.find(io.github.carlos_emr.carlos.commn.model.MeasurementsDeleted.class, id)
                        .getDataField()).isEqualTo(value);
            } finally { transaction.rollback(); }
        }
    }

    @Test
    void shouldReplayArchiveCapacityMigration_withoutChangingRowsOrAdoptedMetadata() throws Exception {
        String migration;
        try (var input = getClass().getResourceAsStream(
                "/db/migration/common/V1.0.59.26__cds_measurements_deleted_capacity.sql")) {
            assertThat(input).isNotNull();
            migration = new String(input.readNBytes(16384), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(input.read()).isEqualTo(-1);
        }
        String fixture = "cds_archive_capacity_fixture";
        String replay = migration.replace("measurementsDeleted", fixture);
        try (Session session = factory.openSession()) {
            session.doWork(connection -> {
                try (var query = connection.createStatement()) {
                    query.execute("CREATE TABLE `cds_archive_capacity_fixture` LIKE `measurementsDeleted`");
                    try {
                        String attributes = " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'kept' COMMENT 'archive fixture'";
                        query.execute("ALTER TABLE `cds_archive_capacity_fixture` MODIFY `dataField` VARCHAR(255)" + attributes);
                        try (var insert = connection.prepareStatement("INSERT INTO `cds_archive_capacity_fixture`"
                                + " (demographicNo,dataField,dateEntered,dateDeleted,originalId,type,measuringInstruction) VALUES (?,?,?,?,?,?,?)")) {
                            insert.setInt(1, 974233);
                            insert.setString(2, "retained fixture");
                            insert.setTimestamp(3, java.sql.Timestamp.from(Instant.now()));
                            insert.setTimestamp(4, java.sql.Timestamp.from(Instant.now()));
                            insert.setInt(5, 1);
                            insert.setString(6, "WT");
                            insert.setString(7, "");
                            assertThat(insert.executeUpdate()).isEqualTo(1);
                        }
                        replayArchiveMigration(connection, replay);
                        String widened = archiveTableDefinition(connection);
                        assertThat(widened).contains("mediumtext", "utf8mb4_unicode_ci", "DEFAULT 'kept'", "archive fixture");
                        replayArchiveMigration(connection, replay);
                        assertThat(archiveTableDefinition(connection)).isEqualTo(widened);
                        try (var rows = query.executeQuery("SELECT dataField FROM `cds_archive_capacity_fixture` WHERE originalId=1")) {
                            assertThat(rows.next()).isTrue();
                            assertThat(rows.getString(1)).isEqualTo("retained fixture");
                        }
                        query.execute("ALTER TABLE `cds_archive_capacity_fixture` MODIFY `dataField` LONGTEXT" + attributes);
                        String adopted = archiveTableDefinition(connection);
                        replayArchiveMigration(connection, replay);
                        assertThat(archiveTableDefinition(connection)).isEqualTo(adopted);
                    } finally { query.execute("DROP TABLE `cds_archive_capacity_fixture`"); }
                }
            });
        }
    }

    private static void replayArchiveMigration(java.sql.Connection connection, String sql) throws java.sql.SQLException {
        for (String statement : sql.split(";")) {
            if (!statement.isBlank()) try (var query = connection.createStatement()) { query.execute(statement); }
        }
    }

    private static String archiveTableDefinition(java.sql.Connection connection) throws java.sql.SQLException {
        try (var query = connection.createStatement(); var rows = query.executeQuery("SHOW CREATE TABLE `cds_archive_capacity_fixture`")) {
            assertThat(rows.next()).isTrue();
            return rows.getString(2);
        }
    }

    @Test
    void shouldRefuseProjection_whenRepresentationIsMissingAfterRecordedReleaseMigration() {
        try (Session session = factory.openSession()) {
            var transaction = session.beginTransaction();
            try {
                var dao = new io.github.carlos_emr.carlos.commn.dao.CdsLegacyProjectionDao();
                ReflectionTestUtils.setField(dao, "entityManager", session);
                // The test table does not exist; the two real shipped Flyway versions do.
                // No existing table is renamed or dropped to exercise this recovery contract.
                for (String version : java.util.List.of("1.0.59.8", "1.0.59.10")) {
                    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(dao, "representationTableAvailable",
                            "cds_absent_projection_contract_test", version))
                            .isInstanceOf(io.github.carlos_emr.carlos.commn.model.CdsLegacyProjectionException.class);
                }
            } finally { transaction.rollback(); }
        }
    }

    @Test
    void shouldRoundTripCompleteMedicationStrengthAndSourceAuthor_withoutTruncationOrInventedDates()
            throws Exception {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("DrugName", "Synthetic source formulation");
        source.put("Strength/Amount", "A".repeat(32768));
        source.put("Strength/UnitOfMeasure", "U".repeat(32768));
        source.put("PrescribedBy/Name/FirstName", "F".repeat(50));
        source.put("PrescribedBy/Name/LastName", "L".repeat(50));
        source.put("Notes", "😀".repeat(32768));
        source.put("Dosage", "1.25");
        source.put("DosageUnitOfMeasure", "tablet");
        source.put("Route", "oral");
        source.put("Frequency", "OD");
        source.put("Quantity", "30");
        source.put("NumberOfRefills", "0");
        source.put("PrescriptionStatus", "Active");
        source.put("SubstitutionNotAllowed", "N");
        source.put("NonAuthoritativeIndicator", "N");
        var dto =
                normalization.medication(
                        CdsClinicalFixture.record(directory, "MedicationsAndTreatments", source));
        Drug drug =
                new CdsMedicationPersister(
                                mock(DrugDao.class), mock(PartialDateDao.class), normalization)
                        .candidate(42, dto);
        try (Session session = factory.openSession()) {
            var transaction = session.beginTransaction();
            try {
                strict(session);
                session.persist(drug);
                session.flush();
                int id = drug.getId();
                session.clear();
                Drug read = session.find(Drug.class, id);
                assertThat(read.getDosage())
                        .isEqualTo(
                                source.get("Strength/Amount")
                                        + " "
                                        + source.get("Strength/UnitOfMeasure"));
                assertThat(read.getComment()).isEqualTo(source.get("Notes"));
                assertThat(read.getOutsideProviderName()).hasSize(101);
                assertThat(read.getProviderNo()).isEmpty();
                assertThat(read.getCdsProjectionIncomplete()).isFalse();
                assertThat(read.getCdsDoseText()).isEqualTo("1.25");
                assertThat(read.getRxDate()).isNull();
                assertThat(read.getWrittenDate()).isNull();
                assertThat(read.getEndDate()).isNull();
                read.setCdsProjectionIncomplete(true);
                session.flush();
                session.clear();
                assertThat(session.find(Drug.class, id).getCdsProjectionIncomplete()).isTrue();
            } finally {
                transaction.rollback();
            }
            session.clear();
            assertThat(session.find(Drug.class, drug.getId())).isNull();
        }
    }

    @Test
    void shouldPreserveContactNamePhonesAndUnknownConsent_throughNativeOrmRoundTrip() throws Exception {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("Name/FirstName", "F".repeat(50));
        source.put("Name/MiddleName", "M".repeat(50));
        source.put("Name/LastName", "Synthetic family");
        source.put("PhoneNumber/@phoneNumberType", "R");
        source.put("PhoneNumber/phoneNumber", "4165551234");
        source.put("PhoneNumber/extension", "12345");
        var dto =
                new CdsContactNormalizationService(fields)
                        .normalize(CdsClinicalFixture.record(directory, "Contact", source));
        var persister =
                new CdsContactPersister(
                        mock(ContactDao.class),
                        mock(DemographicContactDao.class),
                        mock(CdsImportLedgerService.class));
        Contact contact = persister.candidate(dto);
        try (Session session = factory.openSession()) {
            var transaction = session.beginTransaction();
            try {
                strict(session);
                session.persist(contact);
                session.flush();
                DemographicContact association =
                        persister.association(
                                42, contact.getId(), dto.roles().getFirst(), command(), dto);
                session.persist(association);
                session.flush();
                int id = association.getId();
                session.clear();
                Contact read = session.find(Contact.class, contact.getId());
                assertThat(read.getFirstName()).hasSize(101);
                assertThat(read.getResidencePhone()).isEqualTo("4165551234 ext 12345");
                assertThat(read.getUpdateDate()).isNotNull();
                DemographicContact link = session.find(DemographicContact.class, id);
                assertThat(link.getConsentToContact()).isNull();
                assertThat(link.isConsentToContact()).isFalse();
                assertThat(link.getUpdateDate()).isNotNull();
                assertThat(link.getCreator()).isEqualTo("999998");
            } finally {
                transaction.rollback();
            }
            session.clear();
            assertThat(session.find(Contact.class, contact.getId())).isNull();
        }
    }

    @Test
    void shouldPreserveCareTextBeyond255CharactersAndDatePrecision_throughNativeStorage()
            throws Exception {
        var dto =
                new CdsCareNormalizationService(fields, normalization)
                        .normalize(
                                CdsClinicalFixture.record(
                                        directory,
                                        "DiabetesSelfManagementCollaborative",
                                        Map.of(
                                                "Date",
                                                "2020-01-01",
                                                "CodeValue",
                                                "44943-9",
                                                "DocumentedGoals",
                                                "😀".repeat(2049))),
                                command());
        var persister =
                new CdsCarePersister(
                        mock(MeasurementDao.class),
                        mock(MeasurementsExtDao.class),
                        mock(MeasurementTypeDao.class),
                        mock(CdsImportLedgerService.class));
        ReflectionTestUtils.setField(persister, "clinicalZone", "America/Toronto");
        Measurement measurement = persister.candidate(42, dto);
        try (Session session = factory.openSession()) {
            var transaction = session.beginTransaction();
            try {
                strict(session);
                session.persist(measurement);
                session.flush();
                MeasurementsExt precision = new MeasurementsExt();
                precision.setMeasurementId(measurement.getId());
                precision.setKeyVal("date_precision");
                precision.setVal("FULL_DATE");
                session.persist(precision);
                session.flush();
                int id = measurement.getId();
                session.clear();
                Measurement read = session.find(Measurement.class, id);
                assertThat(read.getDataField()).isEqualTo("😀".repeat(2049));
                assertThat(read.getProviderNo()).isEmpty();
                assertThat(read.getDateObserved().toInstant())
                        .isEqualTo(Instant.parse("2020-01-01T05:00:00Z"));
                assertThat(session.find(MeasurementsExt.class, precision.getId()).getVal())
                        .isEqualTo("FULL_DATE");
            } finally {
                transaction.rollback();
            }
            session.clear();
            assertThat(session.find(Measurement.class, measurement.getId())).isNull();
        }
    }

    @Test
    void shouldRejectNumericValidationAndWrongUnitMeaning_inBoundedNativeTypeQuery() {
        try (Session session = factory.openSession()) {
            var transaction = session.beginTransaction();
            try {
                strict(session);
                session.createMutationQuery("delete MeasurementType where type='CDS_CARE_VALUE'")
                        .executeUpdate();
                Validations validation = new Validations();
                validation.setName("CDS source text (no numeric inference)");
                validation.setMaxLength(null);
                session.persist(validation);
                session.flush();
                MeasurementType type = new MeasurementType();
                type.setType("CDS_CARE_VALUE");
                type.setTypeDisplayName("Synthetic source text");
                type.setTypeDescription("Synthetic source text");
                type.setMeasuringInstruction("Uninterpreted source care value");
                type.setValidation(validation.getId().toString());
                session.persist(type);
                session.flush();
                MeasurementTypeDaoImpl dao = new MeasurementTypeDaoImpl(
                        new ConcurrentMapCacheManager(io.github.carlos_emr.carlos.config.CacheConfig.MEASUREMENT_TYPES));
                ReflectionTestUtils.setField(dao, "entityManager", session);
                assertThat(
                                dao.hasCdsImportType(
                                        "CDS_CARE_VALUE", "Uninterpreted source care value"))
                        .isTrue();
                assertThat(dao.hasCdsImportType("CDS_CARE_VALUE", "Invented interpretation"))
                        .isFalse();
                validation.setNumeric(Boolean.TRUE);
                session.flush();
                assertThat(
                                dao.hasCdsImportType(
                                        "CDS_CARE_VALUE", "Uninterpreted source care value"))
                        .isFalse();
            } finally {
                transaction.rollback();
            }
        }
    }

    @Test
    void shouldPreserveExactUnicodeInActualProviderDao_againstLatin1Directory() throws Exception {
        try (Session session = factory.openSession()) {
            var transaction = session.beginTransaction();
            try {
                strict(session);
                // A connection-local table shadows the directory only for this test; no real
                // provider row or schema is changed.
                session.createNativeMutationQuery(
                                "CREATE TEMPORARY TABLE provider (provider_no VARCHAR(6) PRIMARY"
                                    + " KEY, first_name VARCHAR(30) CHARACTER SET latin1 COLLATE"
                                    + " latin1_swedish_ci NOT NULL, last_name VARCHAR(30) CHARACTER"
                                    + " SET latin1 COLLATE latin1_swedish_ci NOT NULL, INDEX"
                                    + " cds_provider_name(first_name,last_name,provider_no))"
                                    + " ENGINE=InnoDB")
                        .executeUpdate();
                session.createNativeMutationQuery(
                                "INSERT INTO provider(provider_no,first_name,last_name)"
                                    + " VALUES('T001',:first,:last)")
                        .setParameter("first", "Élodie")
                        .setParameter("last", "Müller")
                        .executeUpdate();
                var dao = new io.github.carlos_emr.carlos.PMmodule.dao.ProviderDaoImpl();
                ReflectionTestUtils.setField(dao, "entityManager", session);
                // information_schema describes the permanent table, so supply the actual temporary
                // table's known charset cache.
                // The complete production DAO query and its bound values still execute against real
                // MariaDB unchanged.
                var cache =
                        Class.forName(
                                        "io.github.carlos_emr.carlos.PMmodule.dao.ProviderDaoImpl$CdsNameCharsets")
                                .getDeclaredConstructor(String.class, String.class);
                cache.setAccessible(true);
                @SuppressWarnings("unchecked")
                var reference = (java.util.concurrent.atomic.AtomicReference<Object>)
                        ReflectionTestUtils.getField(dao, "cdsNameCharsets");
                assertThat(reference).isNotNull();
                reference.set(cache.newInstance("latin1", "latin1"));
                assertThat(dao.findCdsProviderNosByExactName("Élodie", "Müller"))
                        .containsExactly("T001");
                assertThat(dao.findCdsProviderNosByExactName("élodie", "Müller")).isEmpty();
                assertThat(dao.findCdsProviderNosByExactName("Elodie", "Müller")).isEmpty();
                assertThat(dao.findCdsProviderNosByExactName("Élodie ", "Müller")).isEmpty();
                assertThat(dao.findCdsProviderNosByExactName("中文", "Müller")).isEmpty();
                session.createNativeMutationQuery(
                                "UPDATE provider SET first_name=:first WHERE provider_no='T001'")
                        .setParameter("first", "Élodie ")
                        .executeUpdate();
                assertThat(dao.findCdsProviderNosByExactName("Élodie ", "Müller"))
                        .containsExactly("T001");
                assertThat(dao.findCdsProviderNosByExactName("Élodie", "Müller")).isEmpty();
            } finally {
                session.createNativeMutationQuery("DROP TEMPORARY TABLE IF EXISTS provider")
                        .executeUpdate();
                transaction.rollback();
            }
        }
    }

    @Test
    void shouldRetainUnknownAndYearPrecisionInImmunizationWriter_withAlphanumericAuditCreator()
            throws Exception {
        try (Session session = factory.openSession()) {
            var transaction = session.beginTransaction();
            boolean previous =
                    org.springframework.transaction.support.TransactionSynchronizationManager
                            .isActualTransactionActive();
            try {
                strict(session);
                var preventions = new PreventionMergedDemographicDaoImpl();
                var extensions = new PreventionExtDaoImpl();
                var dates = new PartialDateDaoImpl();
                for (Object dao : new Object[] {preventions, extensions, dates})
                    ReflectionTestUtils.setField(dao, "entityManager", session);
                var writer =
                        new CdsImmunizationPersister(
                                preventions, extensions, dates, normalization, "America/Toronto");
                org.springframework.transaction.support.TransactionSynchronizationManager
                        .setActualTransactionActive(true);
                for (String precision : java.util.List.of("UNKNOWN", "YEAR", "FULL_DATE")) {
                    Map<String, String> source = new LinkedHashMap<>();
                    source.put("ImmunizationName", "Synthetic vaccine");
                    source.put("RefusedFlag/ynIndicatorsimple", precision.equals("YEAR") ? "Y" : "N");
                    if (precision.equals("YEAR")) source.put("Date/YearOnly", "2019");
                    if (precision.equals("FULL_DATE")) source.put("Date/FullDate", "2019-01-01");
                    var normalized =
                            normalization.immunization(
                                    CdsClinicalFixture.record(directory, "Immunizations", source));
                    var receipt = writer.write(42, "T099", normalized);
                    session.flush();
                    session.clear();
                    Prevention stored =
                            session.find(
                                    Prevention.class, Integer.valueOf(receipt.get("preventions")));
                    assertThat(stored.getCreatorProviderNo()).isEqualTo("T099");
                    assertThat(stored.getProviderNo())
                            .isEmpty(); // Audit actor never becomes the administering clinician.
                    assertThat(stored.isRefused()).isEqualTo(precision.equals("YEAR"));
                    assertThat(stored.isNever()).isFalse();
                    assertThat(stored.isDeleted()).isFalse();
                    assertThat(stored.isIneligible()).isFalse();
                    assertThat(stored.isCompletedExternally()).isFalse();
                    if (precision.equals("UNKNOWN"))
                        assertThat(stored.getPreventionDate()).isNull();
                    else assertThat(stored.getPreventionDate()).isNotNull();
                    var read =
                            new CdsTemporalReadService(dates)
                                    .date(
                                            stored.getPreventionDate(),
                                            PartialDate.PREVENTION,
                                            stored.getId(),
                                            PartialDate.PREVENTION_PREVENTIONDATE);
                    assertThat(read.getPrecision()).isEqualTo(precision);
                    if (precision.equals("YEAR")) assertThat(read.getValue()).isEqualTo("2019");
                    if (precision.equals("FULL_DATE"))
                        assertThat(read.getValue()).isEqualTo("2019-01-01");
                }
            } finally {
                org.springframework.transaction.support.TransactionSynchronizationManager
                        .setActualTransactionActive(previous);
                transaction.rollback();
            }
        }
    }

    @Test
    @ResourceLock("java.util.TimeZone")
    void shouldPreserveEarlyCivilDatesAndRecordedPrecision_throughNativeImmunizationWriter()
            throws Exception {
        withImmunizationTransaction((session, writer, dates, extensions) -> {
            for (String[] choice : java.util.List.of(
                    new String[] {"YearOnly", "0001", "YEAR"},
                    new String[] {"YearMonth", "1500-02", "MONTH"},
                    new String[] {"FullDate", "0001-01-01", "FULL_DATE"},
                    new String[] {"FullDate", "0999-12-31", "FULL_DATE"},
                    new String[] {"FullDate", "1000-01-01", "FULL_DATE"},
                    new String[] {"FullDate", "1500-01-01", "FULL_DATE"},
                    new String[] {"FullDate", "1582-10-15", "FULL_DATE"},
                    new String[] {"FullDate", "9999-12-31", "FULL_DATE"})) {
                var source = immunizationDate(choice[0], choice[1]);
                writer.validate(source);
                var receipt = writer.write(42, "T099", source);
                session.flush();
                session.clear();
                int id = Integer.parseInt(receipt.get("preventions"));
                Prevention stored = session.find(Prevention.class, id);
                assertThat(stored.getCreatorProviderNo()).isEqualTo("T099");
                assertThat(nativePreventionDate(session, id))
                        .as("stored civil date for %s", choice[1])
                        .isEqualTo(source.date().earliest().toString());
                var read = new CdsTemporalReadService(dates).date(stored.getPreventionDate(),
                        PartialDate.PREVENTION, id, PartialDate.PREVENTION_PREVENTIONDATE);
                assertThat(read.getPrecision()).isEqualTo(choice[2]);
                assertThat(read.getValue()).isEqualTo(choice[1]);
                var preventionRead =
                        io.github.carlos_emr.carlos.prevention.dto.PreventionDateDTO.from(
                                stored.getPreventionDate(),
                                dates.getFormat(PartialDate.PREVENTION, id,
                                        PartialDate.PREVENTION_PREVENTIONDATE));
                assertThat(preventionRead.getOriginalValue()).isEqualTo(choice[1]);
                assertThat(extensions.getPreventionExt(id))
                        .containsEntry("cdsDateOriginal", choice[1])
                        .containsEntry("cdsDatePrecision", choice[2]);
            }
        });
    }

    @Test
    @ResourceLock("java.util.TimeZone")
    void shouldRefuseUnrepresentableCalendarDaysAndTimestampFolds_beforeNativeWriting()
            throws Exception {
        withImmunizationTransaction((session, writer, dates, extensions) -> {
            Map<String, Long> before = nativeTemporalRowCounts(session);
            for (String[] choice : java.util.List.of(
                    new String[] {"FullDate", "1582-10-10", "CDS_NATIVE_DATE_REQUIRES_REVIEW"},
                    new String[] {"FullDateTime", "2020-11-01T01:30:00-04:00",
                            "CDS_NATIVE_TIMESTAMP_REQUIRES_REVIEW"},
                    new String[] {"FullDateTime", "2020-11-01T01:30:00",
                            "CDS_LOCAL_TIME_REQUIRES_REVIEW"},
                    new String[] {"FullDateTime", "2020-03-08T02:30:00",
                            "CDS_LOCAL_TIME_REQUIRES_REVIEW"})) {
                var source = immunizationDate(choice[0], choice[1]);
                assertThatThrownBy(() -> writer.validate(source))
                        .isInstanceOf(io.github.carlos_emr.carlos.commn.model.CdsClinicalException.class)
                        .hasMessage(choice[2] + " (Date)");
                assertThatThrownBy(() -> writer.write(42, "T099", source))
                        .isInstanceOf(io.github.carlos_emr.carlos.commn.model.CdsClinicalException.class)
                        .hasMessage(choice[2] + " (Date)");
                assertThat(source.date().original()).isEqualTo(choice[1]);
                session.flush();
                assertThat(nativeTemporalRowCounts(session)).isEqualTo(before);
            }
        });
    }

    @Test
    @ResourceLock("java.util.TimeZone")
    void shouldFloorTimestampPrecisionBeforeDatetimeZeroRounding_withLaterFoldKeepingItsInstant()
            throws Exception {
        withImmunizationTransaction((session, writer, dates, extensions) -> {
            for (String[] choice : java.util.List.of(
                    new String[] {"2020-12-31T23:59:59.999999-05:00",
                            "2021-01-01T04:59:59Z", "2020-12-31"},
                    new String[] {"2020-11-01T01:30:00-05:00",
                            "2020-11-01T06:30:00Z", "2020-11-01"})) {
                var source = immunizationDate("FullDateTime", choice[0]);
                writer.validate(source);
                var receipt = writer.write(42, "T099", source);
                session.flush();
                session.clear();
                int id = Integer.parseInt(receipt.get("preventions"));
                Prevention stored = session.find(Prevention.class, id);
                assertThat(stored.getPreventionDate().toInstant()).isEqualTo(Instant.parse(choice[1]));
                assertThat(nativePreventionDate(session, id)).isEqualTo(choice[2]);
                assertThat(io.github.carlos_emr.carlos.prevention.dto.PreventionDateDTO
                                .from(stored.getPreventionDate(), null).getOriginalValue())
                        .isEqualTo(choice[2]);
                assertThat(extensions.getPreventionExt(id))
                        .containsEntry("cdsDateOriginal", choice[0])
                        .containsEntry("cdsDatePrecision", "OFFSET_DATE_TIME");
            }
        });
    }

    private io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImmunizationDto
            immunizationDate(String choice, String value) throws Exception {
        return normalization.immunization(CdsClinicalFixture.record(directory, "Immunizations",
                Map.of("ImmunizationName", "Synthetic calendar vaccine",
                        "RefusedFlag/ynIndicatorsimple", "N", "Date/" + choice, value)));
    }

    private static String nativePreventionDate(Session session, int id) {
        return session.createNativeQuery(
                        "SELECT DATE_FORMAT(prevention_date,'%Y-%m-%d') FROM preventions WHERE id=:id",
                        String.class)
                .setParameter("id", id).getSingleResult();
    }

    private static Map<String, Long> nativeTemporalRowCounts(Session session) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String model : java.util.List.of("Prevention", "PreventionExt", "PartialDate"))
            result.put(model, session.createQuery("select count(p) from " + model + " p", Long.class)
                    .getSingleResult());
        return result;
    }

    private void withImmunizationTransaction(ImmunizationStorageCheck check) throws Exception {
        TimeZone previousZone = TimeZone.getDefault();
        TimeZone clinicZone = TimeZone.getTimeZone("America/Toronto");
        try {
            TimeZone.setDefault(clinicZone);
            // The original fixture deliberately fixes JDBC to UTC. These cases exercise the
            // clinic Calendar used by the actual application and its unqualified DATETIME column.
            try (Session session = factory.withOptions().jdbcTimeZone(clinicZone).openSession()) {
                var transaction = session.beginTransaction();
                boolean previousActive = org.springframework.transaction.support.TransactionSynchronizationManager
                        .isActualTransactionActive();
                Map<String, Long> before;
                try {
                    before = nativeTemporalRowCounts(session);
                    session.createNativeMutationQuery(
                                    "SET SESSION sql_mode='STRICT_ALL_TABLES,NO_ENGINE_SUBSTITUTION,TIME_ROUND_FRACTIONAL'")
                            .executeUpdate();
                    var preventions = new PreventionMergedDemographicDaoImpl();
                    var extensions = new PreventionExtDaoImpl();
                    var dates = new PartialDateDaoImpl();
                    for (Object dao : new Object[] {preventions, extensions, dates})
                        ReflectionTestUtils.setField(dao, "entityManager", session);
                    var writer = new CdsImmunizationPersister(
                            preventions, extensions, dates, normalization, "America/Toronto");
                    org.springframework.transaction.support.TransactionSynchronizationManager
                            .setActualTransactionActive(true);
                    check.run(session, writer, dates, extensions);
                } finally {
                    org.springframework.transaction.support.TransactionSynchronizationManager
                            .setActualTransactionActive(previousActive);
                    transaction.rollback();
                }
                session.clear();
                assertThat(nativeTemporalRowCounts(session)).isEqualTo(before);
            }
        } finally {
            TimeZone.setDefault(previousZone);
        }
    }

    @FunctionalInterface
    private interface ImmunizationStorageCheck {
        void run(Session session, CdsImmunizationPersister writer, PartialDateDao dates,
                PreventionExtDao extensions) throws Exception;
    }

    private static void strict(Session session) {
        session.createNativeMutationQuery(
                        "SET SESSION sql_mode='STRICT_ALL_TABLES,NO_ENGINE_SUBSTITUTION'")
                .executeUpdate();
    }

    private CdsImportCommand command() {
        return new CdsImportCommand(
                directory,
                "clinical-storage",
                "999998",
                null,
                null,
                "synthetic-fixture",
                "cds-5.2",
                false,
                false,
                false,
                0,
                1,
                true);
    }
}
