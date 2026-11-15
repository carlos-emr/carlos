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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.dao.DemographicDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.AdmissionDaoImpl;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;

import org.apache.commons.dbcp2.BasicDataSource;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** Real ORM insertion catches field-access NULLs hidden by presentation getters and DB defaults. */
@Tag("integration")
class CdsDemographicStorageMariaDbIntegrationTest {
    @TempDir Path temporary;

    @Test
    void shouldPersistAbsentPreferredNameAsEmpty_underActualStrictMariaDbFieldMapping()
            throws Exception {
        String selector = System.getProperty("cds.test.properties");
        Assumptions.assumeTrue(
                selector != null && !selector.isBlank(),
                "Explicit isolated MariaDB tier requires protected cds.test.properties");
        Properties jdbc = new Properties();
        try (var input = Files.newInputStream(Path.of(selector))) {
            jdbc.load(input);
        }
        assertThat(jdbc.getProperty("jdbcUrl"))
                .matches(
                        "jdbc:(mysql|mariadb)://[^/]+/(carlos_cds_test|cds_full_test|cds_ledger_test)(\\?.*)?");
        try (var dataSource = new BasicDataSource()) {
            dataSource.setUrl(jdbc.getProperty("jdbcUrl"));
            dataSource.setUsername(jdbc.getProperty("jdbcUser"));
            dataSource.setPassword(jdbc.getProperty("jdbcPassword"));
            dataSource.setMaxTotal(2);
            Configuration configuration =
                    new Configuration()
                            .addAnnotatedClass(Demographic.class)
                            .addAnnotatedClass(DemographicCust.class)
                            .addAnnotatedClass(Provider.class);
            configuration.getProperties().put("hibernate.connection.datasource", dataSource);
            configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect");
            configuration.setProperty("hibernate.hbm2ddl.auto", "none");
            try (var factory = configuration.buildSessionFactory();
                    var session = factory.openSession()) {
                var transaction = session.beginTransaction();
                try {
                    session.doWork(
                            connection -> {
                                try (var query = connection.createStatement();
                                        var rows = query.executeQuery("SELECT @@sql_mode")) {
                                    assertThat(rows.next()).isTrue();
                                    assertThat(rows.getString(1)).contains("STRICT_");
                                }
                            });
                    var command =
                            new CdsImportCommand(
                                    temporary,
                                    "strict-native-demographic",
                                    "999998",
                                    null,
                                    null,
                                    "browser-synthetic",
                                    "pcon-2024-02",
                                    false,
                                    false,
                                    false,
                                    0,
                                    null,
                                    false);
                    String marker = "FAKE-CDS-ORM-" + UUID.randomUUID();
                    String patientNote = "Literal </unotes> & café 🩺";
                    String middleNames = "É".repeat(50) + " " + "𐐀".repeat(49);
                    var details =
                            new CdsDemographicDetailsDto(
                                    "English",
                                    "2024-03-04",
                                    "2028-01-31",
                                    patientNote,
                                    new CdsDemographicDetailsDto.Enrollment(
                                            "RO", "2020-05-06", null, null, "999998"),
                                    1,
                                    List.of());
                    var prepared =
                            new CdsDemographicNativeDto(
                                    "strict-source",
                                    "Import",
                                    marker,
                                    "1971-03-04",
                                    null,
                                    null,
                                    "F",
                                    "AC",
                                    Map.of(CdsDemographicNativeDto.MIDDLE_NAMES_SCALAR, middleNames),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    "999998",
                                    details,
                                    CdsDemographicRelationsDto.empty(),
                                    List.of());
                    var source = mock(CdsRecordDto.class);
                    when(source.effectiveSha256()).thenReturn("a".repeat(64));
                    var preparation = mock(CdsNativePreparationService.class);
                    when(preparation.load(
                                    anyString(),
                                    anyLong(),
                                    anyString(),
                                    anyString(),
                                    eq(command),
                                    eq(CdsDemographicNativeDto.class)))
                            .thenReturn(prepared);
                    var identity = mock(CdsPatientIdentityResolver.class);
                    when(identity.resolve(any(CdsPatientIdentityDto.class), eq(command)))
                            .thenReturn(
                                    new CdsIdentityDecisionDto(
                                            CdsIdentityDecisionDto.Outcome.NO_MATCH, null));
                    var demographics = new DemographicDaoImpl();
                    var custom = new DemographicCustDaoImpl();
                    var extensions = new DemographicExtDaoImpl();
                    var admissions = new AdmissionDaoImpl();
                    for (Object dao : List.of(demographics, custom, extensions, admissions)) {
                        ReflectionTestUtils.setField(dao, "entityManager", session);
                    }
                    var writer =
                            new CdsDemographicPersister(
                                    mock(CdsFieldParser.class),
                                    identity,
                                    mock(CdsProviderResolver.class),
                                    preparation,
                                    mock(CdsDemographicDetailsService.class),
                                    mock(CdsDemographicRelationsService.class),
                                    demographics,
                                    custom,
                                    extensions,
                                    admissions);
                    var targets =
                            writer.write(
                                    "11111111-1111-4111-8111-111111111111",
                                    1,
                                    "unit",
                                    source,
                                    command,
                                    notice -> {});
                    int id = Integer.parseInt(targets.get("demographic"));
                    session.flush();
                    session.clear();
                    assertThat(
                                    ReflectionTestUtils.getField(
                                            session.find(Demographic.class, id), "prefName"))
                            .isEqualTo("");
                    var stored = session.find(Demographic.class, id);
                    assertThat(stored.getMiddleNames()).isEqualTo(middleNames);
                    assertThat(stored.getSpokenLanguage()).isEqualTo("English");
                    assertThat(stored.getPatientStatusDate().toString()).startsWith("2024-03-04");
                    assertThat(stored.getHcRenewDate().toString()).startsWith("2028-01-31");
                    assertThat(stored.getRosterStatus()).isEqualTo("RO");
                    assertThat(stored.getRosterEnrolledTo()).isEqualTo("999998");
                    assertThat(targets).containsEntry("demographiccust", Integer.toString(id));
                    assertThat(
                                    DemographicCust.patientNotesText(
                                            session.find(DemographicCust.class, id).getNotes()))
                            .isEqualTo(patientNote);
                    session.doWork(
                            connection -> {
                                try (var query =
                                        connection.prepareStatement(
                                                "SELECT pref_name,last_name,middleNames FROM demographic WHERE"
                                                        + " demographic_no=?")) {
                                    query.setInt(1, id);
                                    try (var rows = query.executeQuery()) {
                                        assertThat(rows.next()).isTrue();
                                        assertThat(rows.getString(1)).isEmpty();
                                        assertThat(rows.getString(2)).isEqualTo(marker);
                                        assertThat(rows.getString(3)).isEqualTo(middleNames);
                                    }
                                }
                            });
                } finally {
                    transaction.rollback();
                }
            }
        }
    }

    /**
     * CARLOS rows, then export, then import, then the same rows: a chart as the demographic forms
     * store it (a province and an issuer with no address or card, newsletter "Unknown", country
     * of origin "-1", the empty referral-doctor wrapper, a fee-for-service roster) with the
     * columns no Demographics element states (Name Used, pronoun, gender identity, Date Joined,
     * the effective and end dates...). The exporter's document is read by the importer, whose
     * persister writes the new chart through the real patient DAO, and both rows are read as the
     * export captures them. Every value the sender stored is the receiving chart's, except the
     * sender's own provider numbers and the import's audit columns.
     */
    @Test
    void shouldStoreEveryDemographicValue_ofAChartSentFromAnotherCarlos() throws Exception {
        String selector = System.getProperty("cds.test.properties");
        Assumptions.assumeTrue(
                selector != null && !selector.isBlank(),
                "Explicit isolated MariaDB tier requires protected cds.test.properties");
        Properties jdbc = new Properties();
        try (var input = Files.newInputStream(Path.of(selector))) {
            jdbc.load(input);
        }
        assertThat(jdbc.getProperty("jdbcUrl"))
                .matches(
                        "jdbc:(mysql|mariadb)://[^/]+/(carlos_cds_test|cds_full_test|cds_ledger_test)(\\?.*)?");
        try (var dataSource = new BasicDataSource()) {
            dataSource.setUrl(jdbc.getProperty("jdbcUrl"));
            dataSource.setUsername(jdbc.getProperty("jdbcUser"));
            dataSource.setPassword(jdbc.getProperty("jdbcPassword"));
            dataSource.setMaxTotal(2);
            Configuration configuration =
                    new Configuration()
                            .addAnnotatedClass(Demographic.class)
                            .addAnnotatedClass(DemographicCust.class)
                            .addAnnotatedClass(Provider.class);
            configuration.getProperties().put("hibernate.connection.datasource", dataSource);
            configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect");
            configuration.setProperty("hibernate.hbm2ddl.auto", "none");
            try (var factory = configuration.buildSessionFactory();
                    var session = factory.openSession()) {
                var transaction = session.beginTransaction();
                try {
                    var sender = new Demographic();
                    sender.setFirstName("Synthetic");
                    sender.setLastName("FAKE-CDS-ROUND-" + UUID.randomUUID().toString().substring(0, 8));
                    sender.setSex("F");
                    sender.setYearOfBirth("1971");
                    sender.setMonthOfBirth("03");
                    sender.setDateOfBirth("04");
                    sender.setPatientStatus("AC");
                    sender.setPatientStatusDate(java.sql.Date.valueOf("2024-03-04"));
                    sender.setOfficialLanguage("English");
                    sender.setSpokenLanguage("English");
                    sender.setHin("");
                    sender.setVer("");
                    sender.setHcType("ON");
                    sender.setProvince("ON");
                    sender.setResidentialProvince("OT");
                    sender.setNewsletter("Unknown");
                    sender.setCountryOfOrigin("-1");
                    sender.setFamilyDoctor("<rdohip></rdohip><rd></rd>");
                    sender.setAlias("Robbie-Lee called Bob by friends and family alike");
                    sender.setPrefName(Demographic.prefNameForNameUsed(sender.getAlias()));
                    sender.setPronoun("they/them");
                    sender.setGender("Two-Spirit");
                    sender.setDateJoined(java.sql.Date.valueOf("2009-04-01"));
                    sender.setEffDate(java.sql.Date.valueOf("2024-01-15"));
                    sender.setEndDate(java.sql.Date.valueOf("2031-12-31"));
                    sender.setAnonymous("one-time");
                    sender.setChildren("Two");
                    sender.setCitizenship("Canadian");
                    sender.setConsentToUseEmailForCare(true);
                    sender.setPcnIndicator("PCN-7");
                    sender.setPreviousAddress("1 Old Road");
                    sender.setSourceOfIncome("Pension");
                    sender.setRosterStatus("FS");
                    sender.setRosterDate(java.sql.Date.valueOf("2021-05-06"));
                    sender.setProviderNo("");
                    sender.setLastUpdateUser("999998");
                    sender.setLastUpdateDate(new java.util.Date());
                    session.persist(sender);
                    session.flush();
                    var sent = captured(session, sender.getDemographicNo());
                    byte[] document =
                            io.github.carlos_emr.carlos.demographic.cdsexport.service
                                    .CdsExportDemographicDocumentFixture.document(sent, List.of());
                    Path root = Files.createTempDirectory(temporary, "round-trip-");
                    var records = new java.util.ArrayList<CdsRecordDto>();
                    new CdsXmlParser()
                            .parse(
                                    Files.write(root.resolve("patient.xml"), document),
                                    root.resolve("parsed"),
                                    CdsInputLimitsDto.defaults(),
                                    () -> false,
                                    records::add);
                    var record =
                            records.stream()
                                    .filter(found -> found.section().equals("Demographics"))
                                    .findFirst()
                                    .orElseThrow();
                    var command =
                            new CdsImportCommand(
                                    temporary,
                                    "carlos-round-trip",
                                    "999998",
                                    null,
                                    null,
                                    "browser-synthetic",
                                    "pcon-2024-02",
                                    false,
                                    false,
                                    false,
                                    0,
                                    null,
                                    false);
                    var fields = new CdsFieldParser();
                    var providers = mock(CdsProviderResolver.class);
                    when(providers.resolve(any(), any(), any(), any(), anyBoolean()))
                            .thenReturn(
                                    new CdsProviderMatchDto(
                                            null,
                                            CdsProviderMatchDto.Outcome.SOURCE_IDENTITY_ABSENT));
                    var identity =
                            new CdsPatientIdentityResolver(fields) {
                                @Override
                                public CdsIdentityDecisionDto resolve(
                                        CdsPatientIdentityDto source, CdsImportCommand ignored) {
                                    return new CdsIdentityDecisionDto(
                                            CdsIdentityDecisionDto.Outcome.NO_MATCH, null);
                                }
                            };
                    var preparation = mock(CdsNativePreparationService.class);
                    var prepared =
                            new java.util.concurrent.atomic.AtomicReference<
                                    CdsDemographicNativeDto>();
                    doAnswer(
                                    call -> {
                                        prepared.set(call.getArgument(5));
                                        return null;
                                    })
                            .when(preparation)
                            .store(any(), anyLong(), any(), any(), any(), any());
                    var demographics = new DemographicDaoImpl();
                    var custom = new DemographicCustDaoImpl();
                    var extensions = new DemographicExtDaoImpl();
                    var admissions = new AdmissionDaoImpl();
                    for (Object dao : List.of(demographics, custom, extensions, admissions))
                        ReflectionTestUtils.setField(dao, "entityManager", session);
                    var persister =
                            new CdsDemographicPersister(
                                    fields,
                                    identity,
                                    providers,
                                    preparation,
                                    new CdsDemographicDetailsService(fields, providers),
                                    new CdsDemographicRelationsService(
                                            fields,
                                            mock(io.github.carlos_emr.carlos.commn.dao
                                                    .ProfessionalContactDao.class),
                                            mock(io.github.carlos_emr.carlos.commn.dao
                                                    .DemographicContactDao.class),
                                            mock(io.github.carlos_emr.carlos.commn.dao
                                                    .PharmacyInfoDao.class),
                                            mock(io.github.carlos_emr.carlos.commn.dao
                                                    .DemographicPharmacyDao.class)),
                                    demographics,
                                    custom,
                                    extensions,
                                    admissions);
                    persister.validate(record, command);
                    persister.prepare(
                            "11111111-1111-4111-8111-111111111111", 1, "unit", record, command);
                    when(preparation.load(
                                    anyString(),
                                    anyLong(),
                                    anyString(),
                                    anyString(),
                                    eq(command),
                                    eq(CdsDemographicNativeDto.class)))
                            .thenReturn(prepared.get());
                    var targets =
                            persister.write(
                                    "11111111-1111-4111-8111-111111111111",
                                    1,
                                    "unit",
                                    record,
                                    command,
                                    notice -> {});
                    session.flush();
                    session.clear();
                    var received =
                            captured(session, Integer.parseInt(targets.get("demographic")));
                    var compared = new java.util.TreeSet<String>();
                    for (var column : sent.entrySet()) {
                        String value = column.getValue();
                        // The sender's provider numbers and the import's own audit columns.
                        if (value == null
                                || value.isEmpty()
                                || java.util.Set.of(
                                                "lastUpdateDate",
                                                "lastUpdateUser",
                                                "provider_no",
                                                "roster_enrolled_to",
                                                "cds_identity_hin",
                                                "cds_identity_issuer")
                                        .contains(column.getKey())) continue;
                        compared.add(column.getKey());
                        assertThat(received.get(column.getKey()))
                                .as(column.getKey())
                                .isEqualTo(value);
                    }
                    assertThat(compared)
                            .contains(
                                    "province",
                                    "residentialProvince",
                                    "hc_type",
                                    "newsletter",
                                    "country_of_origin",
                                    "family_doctor",
                                    "alias",
                                    "pref_name",
                                    "pronoun",
                                    "gender",
                                    "date_joined",
                                    "eff_date",
                                    "end_date",
                                    "consentToUseEmailForCare",
                                    "roster_status",
                                    "roster_date");
                } finally {
                    transaction.rollback();
                }
            }
        }
    }

    /** The demographic row as the export captures it ({@code findCdsExportRawDemographic}). */
    private static Map<String, String> captured(org.hibernate.Session session, int patient) {
        var columns = io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto.COLUMNS;
        var values = new java.util.LinkedHashMap<String, String>();
        session.doWork(
                connection -> {
                    String projection =
                            columns.stream()
                                    .map(column -> "CAST(d.`" + column + "` AS BINARY)")
                                    .collect(java.util.stream.Collectors.joining(","));
                    try (var query =
                            connection.prepareStatement(
                                    "select "
                                            + projection
                                            + " from demographic d where d.demographic_no=?")) {
                        query.setInt(1, patient);
                        try (var rows = query.executeQuery()) {
                            assertThat(rows.next()).isTrue();
                            for (int index = 0; index < columns.size(); index++) {
                                byte[] bytes = rows.getBytes(index + 1);
                                values.put(
                                        columns.get(index),
                                        bytes == null
                                                ? null
                                                : new String(
                                                        bytes,
                                                        java.nio.charset.StandardCharsets.UTF_8));
                            }
                        }
                    }
                });
        return values;
    }
}
