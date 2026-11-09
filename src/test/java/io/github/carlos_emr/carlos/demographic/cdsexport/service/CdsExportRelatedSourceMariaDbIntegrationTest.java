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
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
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

/** Actual raw DAO ownership, malformed-reference, history, value and transaction behavior. No mapping/publication claim. */
@Tag("integration")
@Tag("cds-export")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "cds.export.test.properties",
        matches = ".+")
class CdsExportRelatedSourceMariaDbIntegrationTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {}

    private BasicDataSource source;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate sql;
    private TransactionTemplate read;
    private final int patient =
            430_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(10_000_000);
    private final String marker = "RelatedSourceFixture";
    private final Set<Integer> contacts = new HashSet<>(),
            relations = new HashSet<>(),
            pharmacies = new HashSet<>(),
            links = new HashSet<>();
    private boolean patientInserted;

    @BeforeEach
    void setup() throws Exception {
        var properties = new Properties();
        try (var input =
                Files.newInputStream(Path.of(System.getProperty("cds.export.test.properties")))) {
            properties.load(input);
        }
        if (!"true".equals(properties.getProperty("ownedDisposableDatabase"))
                || !properties
                        .getProperty("jdbcUrl", "")
                        .matches("jdbc:mysql://[^/]+/cds_export_test(?:\\?.*)?"))
            throw new IllegalArgumentException("Owned fixture database only");
        source = new BasicDataSource();
        source.setMaxTotal(4);
        source.setUrl(properties.getProperty("jdbcUrl"));
        source.setUsername(properties.getProperty("jdbcUser"));
        source.setPassword(properties.getProperty("jdbcPassword"));
        sql = new JdbcTemplate(source);
        assertThat(
                        sql.queryForObject(
                                "select count(*) from demographic where demographic_no=?",
                                Integer.class,
                                patient))
                .isZero();
        sql.update(
                "insert into demographic(demographic_no,first_name,last_name,patient_status,lastUpdateDate) values(?,'Synthetic',?,'IN',CURRENT_TIMESTAMP)",
                patient,
                marker);
        patientInserted = true;
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
        context.register(
                ContactDaoImpl.class,
                DemographicContactDaoImpl.class,
                PharmacyInfoDaoImpl.class,
                DemographicPharmacyDaoImpl.class);
        context.refresh();
        read = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(30);
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            if (sql != null) {
                for (int id : relations)
                    sql.update(
                            "delete from DemographicContact where id=? and creator='CDSTST'", id);
                for (int id : links)
                    sql.update(
                            "delete from demographicPharmacy where id=? and demographic_no in (?,?)",
                            id,
                            patient,
                            patient + 1);
                for (int id : contacts)
                    sql.update(
                            "delete from Contact where id=? and firstName='Synthetic' and lastName=?",
                            id,
                            marker);
                for (int id : pharmacies)
                    sql.update("delete from pharmacyInfo where recordID=? and name=?", id, marker);
                if (patientInserted)
                    sql.update(
                            "delete from demographic where demographic_no=? and first_name='Synthetic' and last_name=?",
                            patient,
                            marker);
            }
        } finally {
            if (context != null) context.close();
            if (source != null) source.close();
        }
    }

    @Test
    void exactExternalContactReferencesRejectNumericCoercionPaddingAndPatientLinks() {
        for (int i = 0; i < 4; i++) contact(patient + i, "Owned contact " + i, 0);
        relation(-patient, patient, Integer.toString(patient), 2, 0, 1);
        relation(-patient + 1, patient, "0" + (patient + 1), 2, 0, 1);
        relation(-patient + 2, patient, (patient + 2) + "x", 2, 0, 1);
        relation(-patient + 3, patient, Integer.toString(patient + 3), 1, 0, 1);
        relation(-patient + 4, patient + 1, Integer.toString(patient + 1), 2, 0, 1);
        var owner = context.getBean(ContactDao.class);
        var associations = context.getBean(DemographicContactDao.class);
        assertThat(read.<Long>execute(status -> owner.countCdsExportRows(patient))).isEqualTo(1);
        assertThat(
                        read.<List<CdsExportRawRowDto>>execute(
                                status -> owner.findCdsExportRowsAfter(patient, null, 10)))
                .extracting(CdsExportRawRowDto::id)
                .containsExactly((long) patient);
        assertThat(read.<Long>execute(status -> associations.countCdsExportRows(patient)))
                .isEqualTo(4);
        assertThat(read.<Long>execute(status -> owner.countCdsExportRows(patient + 1)))
                .isEqualTo(1);
    }

    @Test
    void deletedAndInactiveAssociationsAndContactsRemainAvailableForExplicitDisposition() {
        contact(patient, "Historical contact", 1);
        relation(-patient, patient, Integer.toString(patient), 2, 1, 0);
        var owner = context.getBean(ContactDao.class);
        var associations = context.getBean(DemographicContactDao.class);
        assertThat(read.<Long>execute(status -> owner.countCdsExportRows(patient))).isEqualTo(1);
        assertThat(read.<Long>execute(status -> associations.countCdsExportRows(patient)))
                .isEqualTo(1);
        var row =
                read.execute(status -> associations.findCdsExportRowsAfter(patient, null, 10))
                        .getFirst();
        assertThat(text(row, "deleted")).isEqualTo("1");
        assertThat(text(row, "active")).isEqualTo("0");
    }

    @Test
    void pharmacyClosureIncludesHistoryAndMissingLinksWithoutReadingForeignRows() {
        for (int i = 0; i < 3; i++) pharmacy(patient + i, i == 1 ? "0" : "1");
        link(-patient, patient, patient, "1");
        link(-patient + 1, patient, patient + 1, "0");
        link(-patient + 2, patient, patient + 9, "1");
        link(-patient + 3, patient + 1, patient + 2, "1");
        var owner = context.getBean(PharmacyInfoDao.class);
        var associations = context.getBean(DemographicPharmacyDao.class);
        assertThat(read.<Long>execute(status -> owner.countCdsExportRows(patient))).isEqualTo(2);
        assertThat(read.<Long>execute(status -> associations.countCdsExportRows(patient)))
                .isEqualTo(3);
        assertThat(
                        read.<List<CdsExportRawRowDto>>execute(
                                status -> owner.findCdsExportRowsAfter(patient, null, 10)))
                .extracting(CdsExportRawRowDto::id)
                .containsExactly((long) patient, (long) patient + 1);
    }

    @Test
    void fullUnicodeValuesRemainChunkedHashedAndPatientScoped() throws Exception {
        String note = "é😀".repeat(8000);
        byte[] bytes = note.getBytes(StandardCharsets.UTF_8);
        contact(patient, note, 0);
        relation(-patient, patient, Integer.toString(patient), 2, 0, 1);
        var owner = context.getBean(ContactDao.class);
        var value =
                read.execute(status -> owner.findCdsExportRowsAfter(patient, null, 10))
                        .getFirst()
                        .cells()
                        .get("note");
        assertThat(value.inlineBytes()).isNull();
        assertThat(value.bytes()).isEqualTo(bytes.length);
        assertThat(value.sha256())
                .isEqualTo(
                        HexFormat.of()
                                .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        byte[] actual =
                read.execute(
                        status -> {
                            var output = new ByteArrayOutputStream();
                            for (int offset = 0; offset < bytes.length; offset += 4096)
                                output.writeBytes(
                                        owner.readCdsExportValueChunk(
                                                patient,
                                                patient,
                                                "note",
                                                offset,
                                                Math.min(4096, bytes.length - offset)));
                            return output.toByteArray();
                        });
        assertThat(actual).isEqualTo(bytes);
        assertThatThrownBy(
                        () ->
                                read.execute(
                                        status ->
                                                owner.readCdsExportValueChunk(
                                                        patient + 1, patient, "note", 0, 10)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void captureTransactionKeepsTheOldVersionWhileAnotherTransactionUpdatesTheContact() {
        contact(patient, "Before capture", 0);
        relation(-patient, patient, Integer.toString(patient), 2, 0, 1);
        var owner = context.getBean(ContactDao.class);
        var write = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.execute(
                status -> {
                    assertThat(
                                    text(
                                            owner.findCdsExportRowsAfter(patient, null, 10)
                                                    .getFirst(),
                                            "note"))
                            .isEqualTo("Before capture");
                    write.execute(
                            inner -> {
                                sql.update(
                                        "update Contact set note='After capture' where id=? and lastName=?",
                                        patient,
                                        marker);
                                return null;
                            });
                    assertThat(
                                    text(
                                            owner.findCdsExportRowsAfter(patient, null, 10)
                                                    .getFirst(),
                                            "note"))
                            .isEqualTo("Before capture");
                    return null;
                });
        assertThat(
                        text(
                                read.execute(
                                                status ->
                                                        owner.findCdsExportRowsAfter(
                                                                patient, null, 10))
                                        .getFirst(),
                                "note"))
                .isEqualTo("After capture");
    }

    @Test
    void owningDaosRequireAnActualReadOnlyTransaction() {
        var owner = context.getBean(ContactDao.class);
        assertThatThrownBy(() -> owner.countCdsExportRows(patient))
                .isInstanceOf(
                        org.springframework.transaction.IllegalTransactionStateException.class);
        var write = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThatThrownBy(() -> write.execute(status -> owner.countCdsExportRows(patient)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void actualMetadataContractsAndReceiptRecipesCoverAllFourTables() {
        for (var entry :
                Map.of(
                                CdsExportPatientTableReadSupport.Plan.CONTACT,
                                (CdsExportPatientTableReadDao) context.getBean(ContactDao.class),
                                CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_CONTACT,
                                (CdsExportPatientTableReadDao)
                                        context.getBean(DemographicContactDao.class),
                                CdsExportPatientTableReadSupport.Plan.PHARMACY,
                                (CdsExportPatientTableReadDao)
                                        context.getBean(PharmacyInfoDao.class),
                                CdsExportPatientTableReadSupport.Plan.PHARMACY_LINK,
                                (CdsExportPatientTableReadDao)
                                        context.getBean(DemographicPharmacyDao.class))
                        .entrySet()) {
            var schema = read.execute(status -> entry.getValue().readCdsExportSourceSchema());
            assertThatCode(
                            () ->
                                    new CdsExportSourceSchemaValidator()
                                            .verify(schema, Set.copyOf(entry.getKey().columns())))
                    .doesNotThrowAnyException();
            var recipe = read.execute(status -> entry.getValue().readCdsExportReceiptColumns());
            assertThat(recipe).hasSize(entry.getKey().columns().size());
            assertThat(recipe.stream().map(column -> column.name()).toList())
                    .containsExactlyInAnyOrderElementsOf(entry.getKey().columns());
        }
    }

    private void contact(int id, String note, int deleted) {
        assertThat(sql.queryForObject("select count(*) from Contact where id=?", Integer.class, id))
                .isZero();
        sql.update(
                "insert into Contact(id,type,firstName,lastName,note,deleted) values(?,'contact','Synthetic',?,?,?)",
                id,
                marker,
                note,
                deleted);
        contacts.add(id);
    }

    private void relation(int id, int owner, String reference, int type, int deleted, int active) {
        assertThat(
                        sql.queryForObject(
                                "select count(*) from DemographicContact where id=?",
                                Integer.class,
                                id))
                .isZero();
        sql.update(
                "insert into DemographicContact(id,demographicNo,contactId,type,facilityId,creator,created,updateDate,deleted,active,category) values(?,?,?,?,0,'CDSTST','2024-01-01 12:00:00','2024-01-01 12:00:00',?,?,'personal')",
                id,
                owner,
                reference,
                type,
                deleted,
                active);
        relations.add(id);
    }

    private void pharmacy(int id, String status) {
        assertThat(
                        sql.queryForObject(
                                "select count(*) from pharmacyInfo where recordID=?",
                                Integer.class,
                                id))
                .isZero();
        sql.update(
                "insert into pharmacyInfo(recordID,name,uid,status) values(?,?,0,?)",
                id,
                marker,
                status);
        pharmacies.add(id);
    }

    private void link(int id, int owner, int reference, String status) {
        assertThat(
                        sql.queryForObject(
                                "select count(*) from demographicPharmacy where id=?",
                                Integer.class,
                                id))
                .isZero();
        sql.update(
                "insert into demographicPharmacy(id,demographic_no,pharmacyID,status) values(?,?,?,?)",
                id,
                owner,
                reference,
                status);
        links.add(id);
    }

    private static String text(CdsExportRawRowDto row, String column) {
        var cell = row.cells().get(column);
        return cell.sqlNull() ? null : new String(cell.inlineBytes(), StandardCharsets.UTF_8);
    }
}
