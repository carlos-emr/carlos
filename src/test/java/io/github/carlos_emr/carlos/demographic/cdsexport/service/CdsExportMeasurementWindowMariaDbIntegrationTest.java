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
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportRawRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
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
 * Issue #6200 (CARE-5): a chart with more measurements than one export window holds. Its rows are
 * paged from MariaDB through the owning DAOs in one REPEATABLE READ snapshot (the capture path),
 * then read in windows against the database's own digests and planned without a hold. Before,
 * this chart was held with CDS_EXPORT_NATIVE_CARE_PATIENT_LIMIT_EXCEEDED.
 */
@Tag("integration")
@Tag("cds-export")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "cds.export.test.properties",
        matches = ".+")
class CdsExportMeasurementWindowMariaDbIntegrationTest {
    private static final int CURRENT = 8_400, DELETED = 250, EXTENDED = 100;

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {}

    private BasicDataSource source;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate sql;
    private TransactionTemplate read;
    private final int patient =
            560_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(9_000_000);
    /** A block of ten million ids that no seeded row and no other test uses. */
    private final long base =
            1_700_000_000L
                    + 10_000_000L * java.util.concurrent.ThreadLocalRandom.current().nextInt(40);
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
            throw new IllegalArgumentException("Owned disposable care database required");
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
        context.registerBean(
                "cacheManager",
                org.springframework.cache.CacheManager.class,
                org.springframework.cache.support.NoOpCacheManager::new);
        context.register(
                MeasurementDaoImpl.class,
                MeasurementsExtDaoImpl.class,
                MeasurementsDeletedDaoImpl.class,
                MeasurementTypeDaoImpl.class);
        context.registerBean(
                io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsTargetReceiptService.class,
                () ->
                        new io.github.carlos_emr.carlos.demographic.cdsimport.service
                                .CdsTargetReceiptService(
                                io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineTestKeys.keys()));
        context.refresh();
        read = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(300);
        for (String table : List.of("measurements", "measurementsDeleted", "measurementsExt"))
            assertThat(
                            sql.queryForObject(
                                    "select count(*) from " + table + " where id between ? and ?",
                                    Long.class,
                                    base,
                                    base + 9_999_999))
                    .as("unused id block")
                    .isZero();
        owned = true;
        // Years of flowsheet weights, deleted entries and the extensions they left behind.
        var rows = new ArrayList<Object[]>();
        for (int i = 0; i < CURRENT; i++)
            rows.add(new Object[] {base + i, patient, Integer.toString(60 + i % 40)});
        sql.batchUpdate(
                "insert into measurements(id,demographicNo,providerNo,type,dataField,measuringInstruction,dateObserved,dateEntered,comments,appointmentNo) values(?,?,'999998','WT',?,'in kg','2020-02-29 12:34:56','2020-02-29 12:35:00','',0)",
                rows);
        rows.clear();
        for (int j = 0; j < DELETED; j++)
            rows.add(new Object[] {base + 100_000 + j, base + 200_000 + j, patient});
        sql.batchUpdate(
                "insert into measurementsDeleted(id,originalId,demographicNo,providerNo,type,dataField,measuringInstruction,comments,dateObserved,dateEntered,dateDeleted) values(?,?,?,'999998','WT','800','in kg','entered in error','2020-02-29 12:34:56','2020-02-29 12:35:00','2020-03-01 09:00:00')",
                rows);
        rows.clear();
        for (int j = 0; j < DELETED; j++)
            rows.add(new Object[] {base + 300_000 + j, base + 200_000 + j, "scale not zeroed"});
        for (int k = 1; k <= EXTENDED; k++)
            rows.add(new Object[] {base + 400_000 + k, base + 80L * k, "after exercise"});
        sql.batchUpdate(
                "insert into measurementsExt(id,measurement_id,keyval,val) values(?,?,'comment',?)",
                rows);
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            if (owned) {
                sql.update(
                        "delete from measurementsExt where id between ? and ? and keyval='comment'",
                        base + 300_000,
                        base + 499_999);
                sql.update(
                        "delete from measurementsDeleted where id between ? and ? and demographicNo=?",
                        base,
                        base + 9_999_999,
                        patient);
                sql.update(
                        "delete from measurements where id between ? and ? and demographicNo=?",
                        base,
                        base + 9_999_999,
                        patient);
            }
        } finally {
            if (context != null) context.close();
            if (source != null) source.close();
        }
    }

    @Test
    void shouldCaptureAndPlanEveryMeasurement_ofAChartLargerThanOneExportWindow() throws Exception {
        var tables = new TreeMap<String, SortedMap<String, CdsExportCapturedTextRowDto>>();
        var witnesses = new HashMap<String, CdsExportNativeRowReceiptDto>();
        var counts = new HashMap<String, Long>();
        read.executeWithoutResult(
                status -> {
                    try {
                        page(context.getBean(MeasurementDao.class), "measurements", tables, witnesses, counts);
                        page(context.getBean(MeasurementsDeletedDao.class), "measurementsDeleted", tables, witnesses, counts);
                        page(context.getBean(MeasurementsExtDao.class), "measurementsExt", tables, witnesses, counts);
                        page(context.getBean(MeasurementTypeDao.class), "measurementType", tables, witnesses, counts);
                    } catch (Exception failure) {
                        throw new IllegalStateException(failure);
                    }
                });
        // Every row the chart owns, paged 32 at a time from one snapshot, and no other chart's.
        assertThat(tables.get("measurements")).hasSize(CURRENT);
        assertThat(tables.get("measurementsDeleted")).hasSize(DELETED);
        assertThat(tables.get("measurementsExt")).hasSize(DELETED + EXTENDED);
        assertThat(counts.get("measurements")).isEqualTo(CURRENT);
        assertThat(counts.get("measurementsDeleted")).isEqualTo(DELETED);
        assertThat(counts.get("measurementsExt")).isEqualTo(DELETED + EXTENDED);

        var demographic = demographic();
        tables.put("demographic", new TreeMap<>(Map.of(demographic.recordKey(), demographic)));
        witnesses.put("demographic:" + patient, witness(demographic));
        var scopes = new HashMap<String, CdsExportCapturedTableDto>();
        long fields = 0;
        for (var table : tables.entrySet()) {
            var columns = columns(table.getKey());
            scopes.put(table.getKey(), new CdsExportCapturedTableDto(columns, table.getValue().size(), HASH));
            fields += (long) columns.size() * table.getValue().size();
        }
        var capture =
                new CdsExportNativeCaptureDto(
                        2,
                        UUID.randomUUID().toString(),
                        1,
                        patient,
                        HASH,
                        Instant.EPOCH,
                        Instant.EPOCH,
                        scopes,
                        fields,
                        0,
                        new CdsExportStagedFileDto("1".repeat(64), "SOURCE_VALUES", HASH, 0),
                        new CdsExportStagedFileDto("2".repeat(64), "SOURCE_INDEX", HASH, 0));
        var reader =
                new CdsExportCapturedCareRowsReader(
                        patient,
                        rows -> {
                            for (var table : tables.values())
                                for (var row : table.values()) rows.accept(row);
                            return capture;
                        },
                        receipts -> {
                            for (var witness : witnesses.values()) receipts.accept(witness);
                            return new CdsExportNativeRowReceiptsDto(
                                    HASH, Map.of(), 0, 0, Set.of());
                        },
                        capture,
                        HASH,
                        () -> {});
        var windows = reader.capture(new CdsExportCapturedCareRowsReader.Limits(8192, 64L << 20));
        assertThat(windows.windowed()).isTrue();
        assertThat(windows.windows()).isEqualTo(2);
        var index = new CdsExportRetainedIndexDto(HASH, 0, 0, Map.of(), Set.of(), Map.of(), Map.of());
        var planned = new ArrayList<CdsExportNativeCarePlanner.Occurrence>();
        for (int window = 0; window < windows.windows(); window++)
            planned.addAll(
                    new CdsExportNativeCarePlanner()
                            .plan(index, windows.window(window).current(), () -> {}));
        var current = planned.stream().filter(occurrence -> !occurrence.deleted()).toList();
        assertThat(current).hasSize(CURRENT);
        assertThat(current).allSatisfy(o -> assertThat(o.element()).isEqualTo("Weight"));
        assertThat(current.stream().map(CdsExportNativeCarePlanner.Occurrence::id).toList())
                .isSortedAccordingTo(Comparator.naturalOrder());
        assertThat(planned.stream().filter(CdsExportNativeCarePlanner.Occurrence::deleted))
                .hasSize(DELETED)
                .allSatisfy(o -> assertThat(o.extensions()).hasSize(1));
        assertThat(current.stream().mapToLong(o -> o.extensions().size()).sum()).isEqualTo(EXTENDED);
    }

    private static final String HASH = "a".repeat(64);

    private void page(
            CdsExportPatientTableReadDao dao,
            String table,
            Map<String, SortedMap<String, CdsExportCapturedTextRowDto>> tables,
            Map<String, CdsExportNativeRowReceiptDto> witnesses,
            Map<String, Long> counts)
            throws Exception {
        counts.put(table, dao.countCdsExportRows(patient));
        var rows = tables.computeIfAbsent(table, ignored -> new TreeMap<>());
        Long after = null;
        while (true) {
            List<CdsExportRawRowDto> page = dao.findCdsExportRowsAfter(patient, after, 32);
            for (var raw : page) {
                var fields = new HashMap<String, String>();
                var columns = new ArrayList<CdsNativeRowDigest.Column>();
                var cells = new ArrayList<CdsNativeRowState.Cell>();
                for (var cell : new TreeMap<>(raw.cells()).entrySet()) {
                    var value = cell.getValue();
                    assertThat(value.sqlNull() || value.inlineBytes() != null).isTrue();
                    fields.put(
                            cell.getKey(),
                            value.sqlNull()
                                    ? null
                                    : new String(value.inlineBytes(), StandardCharsets.UTF_8));
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
                String id = Long.toString(raw.id());
                var state = new CdsNativeRowState(table, id, columns, cells);
                assertThat(rows.put(row.recordKey(), row)).isNull();
                witnesses.put(
                        table + ":" + id,
                        new CdsExportNativeRowReceiptDto(
                                table, id, row.recordKey(), state.digest(), state));
            }
            if (page.size() < 32) return;
            after = page.getLast().id();
        }
    }

    private static Set<String> columns(String table) {
        if (table.equals("demographic")) {
            var columns = new HashSet<>(CdsExportDemographicRowDto.COLUMNS);
            columns.add("demographic_no");
            return columns;
        }
        return Set.copyOf(
                switch (table) {
                    case "measurements" -> CdsExportPatientTableReadSupport.Plan.MEASUREMENTS_CURRENT.columns();
                    case "measurementsExt" -> CdsExportPatientTableReadSupport.Plan.MEASUREMENTS_EXT.columns();
                    case "measurementsDeleted" -> CdsExportPatientTableReadSupport.Plan.MEASUREMENTS_DELETED.columns();
                    default -> CdsExportPatientTableReadSupport.Plan.MEASUREMENT_TYPES.columns();
                });
    }

    private CdsExportCapturedTextRowDto demographic() {
        var fields = new HashMap<String, String>();
        for (String column : CdsExportDemographicRowDto.COLUMNS) fields.put(column, null);
        fields.put("demographic_no", Integer.toString(patient));
        return new CdsExportCapturedTextRowDto(
                "demographic", String.format(Locale.ROOT, "%010d", patient), fields);
    }

    private static CdsExportNativeRowReceiptDto witness(CdsExportCapturedTextRowDto row) {
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (var column : new TreeSet<>(row.fields().keySet())) {
            columns.add(new CdsNativeRowDigest.Column(column, "VARCHAR", 255, 0));
            String value = row.fields().get(column);
            cells.add(
                    value == null
                            ? new CdsNativeRowState.Cell(true, 0, null)
                            : new CdsNativeRowState.Cell(
                                    false,
                                    value.getBytes(StandardCharsets.UTF_8).length,
                                    HexFormat.of()
                                            .formatHex(
                                                    CdsImportKeys.digest()
                                                            .digest(
                                                                    value.getBytes(
                                                                            StandardCharsets
                                                                                    .UTF_8)))));
        }
        String id = row.fields().get("demographic_no");
        var state = new CdsNativeRowState(row.table(), id, columns, cells);
        return new CdsExportNativeRowReceiptDto(
                row.table(), id, row.recordKey(), state.digest(), state);
    }
}
