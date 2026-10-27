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
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

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
    void completeMedicationStrengthAndSourceAuthorRoundTripWithoutTruncationOrInventedDates()
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
    void contactNamePhonesAndUnknownConsentSurviveNativeOrmRoundTrip() throws Exception {
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
    void careTextBeyondLegacy255CharactersAndItsDatePrecisionMetadataSurviveNativeStorage()
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
    void boundedNativeTypeQueryRejectsNumericValidationAndWrongUnitMeaning() {
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
                MeasurementTypeDaoImpl dao = new MeasurementTypeDaoImpl();
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
    void actualProviderDaoPreservesExactUnicodeAgainstALatin1Directory() throws Exception {
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
                ReflectionTestUtils.setField(
                        dao, "cdsNameCharsets", cache.newInstance("latin1", "latin1"));
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
    void immunizationWriterRetainsUnknownAndYearPrecisionWithAlphanumericAuditCreator()
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
                    source.put("RefusedFlag", precision.equals("YEAR") ? "Y" : "N");
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
