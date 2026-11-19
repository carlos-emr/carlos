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
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.dto.CdsExportRawRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.io.ByteArrayOutputStream;
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
 * A chart of native HL7 reports stored in MariaDB and read through the capture's own page reads
 * (the owning DAOs, one REPEATABLE READ snapshot, MariaDB's digests): its LaboratoryResults are
 * the same bytes and receipts read whole and read in several windows, each window's rows checked
 * against the database's own digests.
 */
@Tag("integration")
@Tag("cds-export")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "cds.export.test.properties",
        matches = ".+")
class CdsExportLaboratoryWindowMariaDbIntegrationTest extends CdsExportLaboratoryWindowFixture {
    private static final int REPORTS = 45;

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {}

    private BasicDataSource database;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate sql;
    private TransactionTemplate read;
    private final int chart =
            580_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(9_000_000);
    private final String marker = "OwnedLabWindow" + chart;
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
            throw new IllegalArgumentException("Owned disposable laboratory database required");
        database = new BasicDataSource();
        database.setMaxTotal(4);
        database.setUrl(settings.getProperty("jdbcUrl"));
        database.setUsername(settings.getProperty("jdbcUser"));
        database.setPassword(settings.getProperty("jdbcPassword"));
        sql = new JdbcTemplate(database);
        assertThat(sql.queryForObject("select version()", String.class)).contains("MariaDB");
        context = new AnnotationConfigApplicationContext();
        context.register(Config.class);
        context.registerBean("dataSource", DataSource.class, () -> database);
        context.registerBean(
                "entityManagerFactory",
                LocalContainerEntityManagerFactoryBean.class,
                () -> {
                    var factory = new LocalContainerEntityManagerFactoryBean();
                    factory.setDataSource(database);
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
                CdsLaboratoryResultDao.class,
                Hl7TextMessageDaoImpl.class,
                Hl7TextInfoDaoImpl.class,
                PatientLabRoutingDaoImpl.class,
                CdsExportReviewerReadDao.class);
        context.refresh();
        read = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(300);
        for (String check :
                List.of(
                        "select count(*) from hl7TextMessage where lab_id between ? and ?",
                        "select count(*) from hl7TextInfo where id between ? and ?",
                        "select count(*) from patientLabRouting where id between ? and ?",
                        "select count(*) from providerLabRouting where id between ? and ?"))
            assertThat(sql.queryForObject(check, Long.class, chart, chart + 9_999))
                    .as("unused id block")
                    .isZero();
        owned = true;
        var messages = new ArrayList<Object[]>();
        var information = new ArrayList<Object[]>();
        var routing = new ArrayList<Object[]>();
        var routes = new ArrayList<Object[]>();
        for (int report = 0; report < REPORTS; report++) {
            long lab = chart + report;
            messages.add(
                    new Object[] {
                        lab,
                        Base64.getEncoder()
                                .encodeToString(message(lab).getBytes(StandardCharsets.UTF_8)),
                        marker
                    });
            information.add(new Object[] {lab, lab, marker, "ACC-" + lab});
            routing.add(new Object[] {lab, chart, lab});
            if (report % 3 == 0)
                routes.add(
                        new Object[] {chart + 1_000 + report, lab, report % 6 == 0 ? "Repeat" : ""});
        }
        sql.batchUpdate(
                "insert into hl7TextMessage(lab_id,created,fileUploadCheck_id,message,serviceName,type)"
                        + " values(?,CURRENT_TIMESTAMP,0,?,?,'HL7')",
                messages);
        sql.batchUpdate(
                "insert into hl7TextInfo(id,lab_no,report_status,label,accessionNum,sending_facility)"
                        + " values(?,?,'F',?,?,'Native Lab')",
                information);
        sql.batchUpdate(
                "insert into patientLabRouting(id,created,demographic_no,lab_no,lab_type)"
                        + " values(?,CURRENT_TIMESTAMP,?,?,'HL7')",
                routing);
        sql.batchUpdate(
                "insert into providerLabRouting(id,lab_no,lab_type,provider_no,status,comment,timestamp)"
                        + " values(?,?,'HL7','101','A',?,'2026-07-10 16:45:00')",
                routes);
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            if (owned) {
                sql.update(
                        "delete from providerLabRouting where id between ? and ? and provider_no='101'",
                        chart + 1_000,
                        chart + 1_999);
                sql.update(
                        "delete from patientLabRouting where id between ? and ? and demographic_no=?",
                        chart,
                        chart + 999,
                        chart);
                sql.update(
                        "delete from hl7TextInfo where id between ? and ? and label=?",
                        chart,
                        chart + 999,
                        marker);
                sql.update(
                        "delete from hl7TextMessage where lab_id between ? and ? and serviceName=?",
                        chart,
                        chart + 999,
                        marker);
            }
        } finally {
            if (context != null) context.close();
            if (database != null) database.close();
        }
    }

    @Test
    void shouldEmitTheSameLaboratoryResults_whenAStoredChartIsReadInSeveralWindows()
            throws Exception {
        when(patient.patient()).thenReturn(chart);
        var counts = new HashMap<String, Long>();
        read.executeWithoutResult(
                status -> {
                    try {
                        // The structured results are keyed by their digest key: none here.
                        counts.put(
                                "cds_laboratory_result",
                                context.getBean(CdsLaboratoryResultDao.class)
                                        .countCdsExportRows(chart));
                        page(context.getBean(Hl7TextMessageDao.class), null, "hl7TextMessage", counts);
                        page(context.getBean(Hl7TextInfoDao.class), null, "hl7TextInfo", counts);
                        page(context.getBean(PatientLabRoutingDao.class), null, "patientLabRouting", counts);
                        page(
                                null,
                                context.getBean(CdsExportReviewerReadDao.class),
                                CdsExportLaboratoryOccurrenceService.ROUTES,
                                counts);
                    } catch (Exception failure) {
                        throw new IllegalStateException(failure);
                    }
                });
        // Every row the chart owns, paged 32 at a time from one snapshot, and no other chart's.
        assertThat(counts)
                .containsEntry("cds_laboratory_result", 0L)
                .containsEntry("hl7TextMessage", (long) REPORTS)
                .containsEntry("hl7TextInfo", (long) REPORTS)
                .containsEntry("patientLabRouting", (long) REPORTS)
                .containsEntry(CdsExportLaboratoryOccurrenceService.ROUTES, (long) (REPORTS + 2) / 3);
        for (var table : counts.entrySet())
            assertThat(tables.get(table.getKey())).hasSize(table.getValue().intValue());

        // Read whole: one pass over the report tables, the witnesses and the routes.
        var whole = compose();
        var expected = List.copyOf(receipts);
        assertThat(result.nativeOnlyRows()).isEqualTo(4L * REPORTS);
        assertThat(witnessPasses).isEqualTo(1);
        assertThat(messagePasses).isZero();
        // The reports leave in the order of their numbers as text.
        var numbers =
                expected.stream()
                        .map(receipt -> receipt.nativeId().substring(0, receipt.nativeId().indexOf(':')))
                        .distinct()
                        .toList();
        assertThat(numbers).hasSize(REPORTS).isSorted();
        assertThat(new String(whole, StandardCharsets.UTF_8)).contains("Ada101");

        // Read in windows of seven reports: every window's rows are checked against the
        // digests MariaDB computed, and the output is the same.
        bounds = bounds.withWindow(7, 64L << 20);
        assertThat(compose()).isEqualTo(whole);
        assertThat(receipts).isEqualTo(expected);
        assertThat(messagePasses).isEqualTo(1);
        assertThat(witnessPasses).isEqualTo((REPORTS + 6) / 7);
        assertThat(routePasses).isEqualTo((REPORTS + 6) / 7);
    }

    /** Pages one table of the chart as the capture does and keeps its rows and witnesses. */
    private void page(
            CdsExportPatientTableReadDao dao,
            CdsExportReviewerReadDao reviewers,
            String table,
            Map<String, Long> counts)
            throws Exception {
        var plan = CdsExportPatientTableReadSupport.Plan.LABORATORY_REVIEWERS;
        counts.put(
                table,
                dao != null ? dao.countCdsExportRows(chart) : reviewers.count(plan, chart));
        Long after = null;
        while (true) {
            List<CdsExportRawRowDto> page =
                    dao != null
                            ? dao.findCdsExportRowsAfter(chart, after, 32)
                            : reviewers.page(plan, chart, after, 32);
            for (var raw : page) {
                var fields = new HashMap<String, String>();
                var columns = new ArrayList<CdsNativeRowDigest.Column>();
                var cells = new ArrayList<CdsNativeRowState.Cell>();
                for (var cell : new TreeMap<>(raw.cells()).entrySet()) {
                    var value = cell.getValue();
                    byte[] bytes = value.sqlNull() ? null : value.inlineBytes();
                    if (!value.sqlNull() && bytes == null) {
                        // A value too long for the page is streamed, as the capture streams it.
                        var streamed = new ByteArrayOutputStream();
                        for (long offset = 0; offset < value.bytes(); offset += 65_536)
                            streamed.write(
                                    dao.readCdsExportValueChunk(
                                            chart,
                                            raw.id(),
                                            cell.getKey(),
                                            offset,
                                            (int) Math.min(65_536, value.bytes() - offset)));
                        bytes = streamed.toByteArray();
                    }
                    fields.put(
                            cell.getKey(),
                            bytes == null ? null : new String(bytes, StandardCharsets.UTF_8));
                    columns.add(new CdsNativeRowDigest.Column(cell.getKey(), "VARCHAR", 255, 0));
                    // The digest MariaDB computed (SHA2 in the page query), as the capture keeps it.
                    cells.add(
                            value.sqlNull()
                                    ? new CdsNativeRowState.Cell(true, 0, null)
                                    : new CdsNativeRowState.Cell(false, value.bytes(), value.sha256()));
                }
                var row =
                        new CdsExportCapturedTextRowDto(
                                table, CdsExportPatientTableSourceReader.key(raw.id()), fields);
                assertThat(tables.get(table).put(row.recordKey(), row)).isNull();
                if (!table.equals(CdsExportLaboratoryOccurrenceService.ROUTES)) {
                    String id = Long.toString(raw.id());
                    var state = new CdsNativeRowState(table, id, columns, cells);
                    witnesses.put(
                            table + ":" + id,
                            new CdsExportNativeRowReceiptDto(
                                    table, id, row.recordKey(), state.digest(), state));
                }
            }
            if (page.size() < 32) return;
            after = page.getLast().id();
        }
    }
}
