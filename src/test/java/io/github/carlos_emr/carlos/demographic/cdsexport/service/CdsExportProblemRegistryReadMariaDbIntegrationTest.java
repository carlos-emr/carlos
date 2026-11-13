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

import static org.assertj.core.api.Assertions.*;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.dto.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Real owning-DAO read plans for the Disease Registry, its start-date precision rows, episodes
 * and the allowlisted code-table descriptions they name, against the pinned physical schema:
 * patient scope, deleted rows kept in capture, and no other chart's rows or codes.
 */
@Tag("integration")
@Tag("cds-export")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "cds.export.test.properties",
        matches = ".+")
class CdsExportProblemRegistryReadMariaDbIntegrationTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {}

    private BasicDataSource source;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate sql;
    private TransactionTemplate read;
    private DxresearchDAO registry;
    private EpisodeDao episodes;
    private PartialDateDao precision;
    private final int patient =
            540_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(9_000_000);
    private final int entry =
            1_500_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(400_000_000);
    private final String notes = "Delivered at term 😀 & well\r\n".repeat(3000);
    private boolean owned;

    @BeforeEach
    void setup() throws Exception {
        var settings = new Properties();
        try (var input =
                Files.newInputStream(Path.of(System.getProperty("cds.export.test.properties")))) {
            settings.load(input);
        }
        if (!"true".equals(settings.getProperty("ownedDisposableDatabase"))
                || !settings.getProperty("jdbcUrl", "")
                        .matches("jdbc:mariadb://[^/]+/cds_export_test(?:\\?.*)?"))
            throw new IllegalArgumentException("Owned disposable registry database required");
        source = new BasicDataSource();
        source.setMaxTotal(4);
        source.setUrl(settings.getProperty("jdbcUrl"));
        source.setUsername(settings.getProperty("jdbcUser"));
        source.setPassword(settings.getProperty("jdbcPassword"));
        sql = new JdbcTemplate(source);
        assertThat(sql.queryForObject("select version()", String.class)).contains("MariaDB");
        context = new AnnotationConfigApplicationContext();
        context.register(Config.class);
        context.registerBean("dataSource", DataSource.class, () -> source);
        context.registerBean(
                "entityManagerFactory",
                LocalContainerEntityManagerFactoryBean.class,
                () -> {
                    var factory = new LocalContainerEntityManagerFactoryBean();
                    factory.setDataSource(source);
                    factory.setPersistenceUnitName("entityManagerFactory");
                    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
                    factory.setManagedTypes(PersistenceManagedTypes.of());
                    factory.setJpaPropertyMap(
                            Map.of(
                                    "hibernate.hbm2ddl.auto",
                                    "none",
                                    "hibernate.dialect",
                                    "org.hibernate.dialect.MariaDBDialect",
                                    "hibernate.show_sql",
                                    "false"));
                    return factory;
                });
        context.registerBean(PersistenceAnnotationBeanPostProcessor.class);
        context.registerBean(
                "transactionManager",
                JpaTransactionManager.class,
                () ->
                        new JpaTransactionManager(
                                context.getBean(jakarta.persistence.EntityManagerFactory.class)));
        context.register(DxresearchDAOImpl.class, EpisodeDaoImpl.class, PartialDateDaoImpl.class);
        context.refresh();
        registry = context.getBean(DxresearchDAO.class);
        episodes = context.getBean(EpisodeDao.class);
        precision = context.getBean(PartialDateDao.class);
        read = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(120);
        owned = true;
        // This patient: an active ICD-9 entry (stored upper-case system), a resolved ICHPPC
        // entry, a deleted entry whose code must not be looked up, and an entry of a system
        // without a reviewed code table. Another chart holds the last entry.
        String insert =
                "insert into dxresearch(dxresearch_no,demographic_no,start_date,update_date,status,"
                        + "dxresearch_code,coding_system,association,providerNo) values(?,?,?,?,?,?,?,?,?)";
        sql.update(insert, entry, patient, "2020-02-29", "2024-06-01 10:11:12", "A", "250", "ICD9", 1, "999998");
        sql.update(insert, entry + 1, patient, "2019-05-01", "2024-07-01 08:00:00", "C", "001", "ichppccode", 0, "999998");
        sql.update(insert, entry + 2, patient, "2018-01-01", "2024-01-01 00:00:00", "D", "4019", "icd9", 0, "999998");
        sql.update(insert, entry + 3, patient, null, "2024-01-02 00:00:00", "A", "44054006", "SnomedCore", 0, null);
        sql.update(insert, entry + 4, patient + 1, "2018-01-01", "2024-01-01 00:00:00", "A", "2500", "icd9", 0, "999998");
        for (int owner : List.of(entry, entry + 4))
            sql.update(
                    "insert into partial_date(table_name,table_id,field_name,format) values(3,?,4,'YYYY')",
                    owner);
        String episode =
                "insert into Episode(id,demographicNo,startDate,endDate,code,codingSystem,description,"
                        + "status,lastUpdateUser,notes) values(?,?,?,?,?,?,?,?,?,?)";
        sql.update(episode, entry, patient, "2024-01-01", "2024-09-30", "E119", "icd10", "Pregnancy", "Complete", "999998", notes);
        sql.update(episode, entry + 1, patient, "2023-01-01", null, "2500", "icd9", "Deleted one", "Deleted", "999998", "");
        sql.update(episode, entry + 2, patient + 1, "2023-01-01", null, "4019", "icd9", "Other chart", "Current", "999998", "");
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            if (owned) {
                sql.update(
                        "delete from partial_date where table_name=3 and table_id in (?,?)",
                        entry,
                        entry + 4);
                sql.update(
                        "delete from dxresearch where dxresearch_no between ? and ? and demographic_no in (?,?)",
                        entry,
                        entry + 4,
                        patient,
                        patient + 1);
                sql.update(
                        "delete from Episode where id between ? and ? and demographicNo in (?,?)",
                        entry,
                        entry + 2,
                        patient,
                        patient + 1);
            }
        } finally {
            if (context != null) context.close();
            if (source != null) source.close();
        }
    }

    private List<CdsExportNativeSourceReader> readers() {
        return List.of(
                new CdsExportDiseaseRegistrySourceReader(registry),
                new CdsExportDiseaseRegistryPrecisionSourceReader(precision),
                new CdsExportEpisodeSourceReader(episodes));
    }

    @Test
    void shouldReadEveryOwnedEntryEpisodeAndPrecisionRow_includingDeletedOnes() {
        read.executeWithoutResult(
                status -> {
                    assertThat(registry.countCdsExportRows(patient)).isEqualTo(4);
                    assertThat(registry.findCdsExportRowsAfter(patient, null, 32))
                            .extracting(CdsExportRawRowDto::id)
                            .containsExactly(
                                    (long) entry, entry + 1L, entry + 2L, entry + 3L);
                    assertThat(episodes.countCdsExportRows(patient)).isEqualTo(2);
                    assertThat(precision.countCdsExportDiseaseRegistryRows(patient)).isEqualTo(1);
                    var date = precision.findCdsExportDiseaseRegistryRowsAfter(patient, null, 32);
                    assertThat(date).hasSize(1);
                    assertThat(
                                    new String(
                                            date.getFirst().cells().get("table_id").inlineBytes(),
                                            StandardCharsets.UTF_8))
                            .isEqualTo(Integer.toString(entry));
                    var startDate =
                            registry.findCdsExportRowsAfter(patient, entry + 2L, 32)
                                    .getFirst()
                                    .cells()
                                    .get("start_date");
                    assertThat(startDate.sqlNull()).isTrue();
                });
    }

    @Test
    void shouldStreamTheCompleteEpisodeNotes_underTheEpisodesOwnPatient() {
        read.executeWithoutResult(
                status -> {
                    var cell =
                            episodes.findCdsExportRowsAfter(patient, null, 32)
                                    .getFirst()
                                    .cells()
                                    .get("notes");
                    byte[] data = notes.getBytes(StandardCharsets.UTF_8);
                    assertThat(cell.inlineBytes()).isNull();
                    assertThat(cell.bytes()).isEqualTo(data.length);
                    assertThat(
                                    episodes.readCdsExportValueChunk(
                                            patient, entry, "notes", 0, 64))
                            .isEqualTo(Arrays.copyOf(data, 64));
                });
        assertThatThrownBy(
                        () ->
                                read.execute(
                                        status ->
                                                episodes.readCdsExportValueChunk(
                                                        patient, entry + 2, "notes", 0, 10)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldLookUpOnlyTheCurrentCodesOfThisChart_throughTheReviewedCodeTables() {
        var references =
                read.execute(status -> registry.readCdsExportDiagnosisCodeReferences(patient));
        assertThat(references)
                .extracting(
                        CdsExportDiagnosisCodeReferenceDto::codingSystem,
                        CdsExportDiagnosisCodeReferenceDto::code,
                        CdsExportDiagnosisCodeReferenceDto::referenceTable)
                .containsExactlyInAnyOrder(
                        tuple("ICD9", "250", "icd9"),
                        tuple("ichppccode", "001", "ichppccode"),
                        tuple("icd10", "E119", "icd10"));
        assertThat(references)
                .filteredOn(reference -> reference.code().equals("250"))
                .singleElement()
                .satisfies(
                        reference -> {
                            assertThat(reference.referenceCode()).isEqualTo("250");
                            assertThat(reference.description()).isNotBlank();
                        });
    }

    @Test
    void shouldMatchAllPinnedPhysicalColumns_andTheirActualOrderedJdbcReceiptMetadata() {
        read.executeWithoutResult(
                status -> {
                    for (var reader : readers()) {
                        assertThatCode(
                                        () ->
                                                new CdsExportSourceSchemaValidator()
                                                        .verify(
                                                                reader.table(),
                                                                reader.schema(),
                                                                reader.columns()))
                                .doesNotThrowAnyException();
                        assertThat(reader.receiptColumns())
                                .extracting(column -> column.name())
                                .containsExactlyInAnyOrderElementsOf(reader.columns());
                    }
                });
    }
}
