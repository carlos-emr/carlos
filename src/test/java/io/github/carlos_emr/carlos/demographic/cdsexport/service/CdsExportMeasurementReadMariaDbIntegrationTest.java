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
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
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

/** Real owning-DAO patient scopes, snapshot chunks, physical receipts and adverse deleted/current ownership. */
@Tag("integration")
@Tag("cds-export")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "cds.export.test.properties",
        matches = ".+")
class CdsExportMeasurementReadMariaDbIntegrationTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {}

    private BasicDataSource source;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate sql;
    private TransactionTemplate read;
    private MeasurementDao measurements;
    private MeasurementsExtDao extensions;
    private final int patient =
            530_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(9_000_000);
    private final String marker = "OwnedCare" + patient;
    private final String body = "Complete care 😀 & exact text\r\n".repeat(3000);
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
        // Sealed native-baseline witnesses need an installation key (IMP-1).
        context.registerBean(
                io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsTargetReceiptService.class,
                () ->
                        new io.github.carlos_emr.carlos.demographic.cdsimport.service
                                .CdsTargetReceiptService(
                                io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineTestKeys.keys()));
        context.refresh();
        measurements = context.getBean(MeasurementDao.class);
        extensions = context.getBean(MeasurementsExtDao.class);
        read = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(120);
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class))
                .executeWithoutResult(
                        status -> {
                            for (int i = 0; i < 4; i++)
                                sql.update(
                                        "insert into measurementType(id,type,typeDescription,typeDisplayName,measuringInstruction,validation,createDate) values(?,?,?,?,?,'',CURRENT_TIMESTAMP)",
                                        patient + i,
                                        i == 2 ? marker + "Other" : marker,
                                        marker,
                                        marker + i,
                                        i == 1 ? "Unrelated instruction" : "in kg");
                            for (int i = 0; i < 36; i++) {
                                sql.update(
                                        "insert into measurements(id,demographicNo,providerNo,type,dataField,measuringInstruction,dateObserved,dateEntered,comments) values(?,?,'',?,?,?,'2020-02-29 12:34:56',CURRENT_TIMESTAMP,?)",
                                        patient + i,
                                        i == 35 ? patient + 1 : patient,
                                        i == 35 ? marker + "Other" : marker,
                                        body,
                                        "in kg",
                                        i == 0 ? null : "");
                                sql.update(
                                        "insert into measurementsExt(id,measurement_id,keyval,val) values(?,?,'OwnedCareField',?)",
                                        patient + i,
                                        patient + i,
                                        body);
                            }
                        });
        owned = true;
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            if (owned) {
                sql.update(
                        "delete e from measurementsExt e join measurements m on m.id=e.measurement_id where m.id between ? and ? and m.type in (?,?) and e.keyval='OwnedCareField'",
                        patient,
                        patient + 35,
                        marker,
                        marker + "Other");
                sql.update(
                        "delete from measurementsDeleted where id=? and type=?", patient, marker);
                sql.update(
                        "delete from measurements where id between ? and ? and type in (?,?)",
                        patient,
                        patient + 35,
                        marker,
                        marker + "Other");
                sql.update(
                        "delete from measurementType where id between ? and ? and typeDescription=?",
                        patient,
                        patient + 3,
                        marker);
            }
        } finally {
            if (context != null) context.close();
            if (source != null) source.close();
        }
    }

    private List<CdsExportNativeSourceReader> readers() {
        return List.of(
                new CdsExportMeasurementSourceReader(measurements),
                new CdsExportMeasurementExtensionSourceReader(extensions),
                new CdsExportDeletedMeasurementSourceReader(
                        context.getBean(MeasurementsDeletedDao.class)),
                new CdsExportMeasurementTypeSourceReader(
                        context.getBean(MeasurementTypeDao.class)));
    }

    @Test
    void shouldPageEveryPatientOwnedObservation_withoutAnotherChartsRows() {
        read.executeWithoutResult(
                status -> {
                    assertThat(measurements.countCdsExportRows(patient)).isEqualTo(35);
                    var first = measurements.findCdsExportRowsAfter(patient, null, 32);
                    var last =
                            measurements.findCdsExportRowsAfter(patient, first.getLast().id(), 32);
                    assertThat(first).hasSize(32);
                    assertThat(last).hasSize(3);
                    assertThat(first.getFirst().id()).isEqualTo(patient);
                    assertThat(last.getLast().id()).isEqualTo(patient + 34);
                    assertThat(
                                    measurements.findCdsExportRowsAfter(
                                            patient, last.getLast().id(), 32))
                            .isEmpty();
                    assertThat(first)
                            .allSatisfy(
                                    row ->
                                            assertThat(row.cells().keySet())
                                                    .containsExactlyInAnyOrderElementsOf(
                                                            CdsExportPatientTableReadSupport.Plan
                                                                    .MEASUREMENTS_CURRENT
                                                                    .columns()));
                });
    }

    @Test
    void shouldPreserveNullEmptyAndTheCompleteObservationBody_underItsOriginalPatientScope() {
        read.executeWithoutResult(
                status -> {
                    var rows = measurements.findCdsExportRowsAfter(patient, null, 32);
                    assertThat(rows.getFirst().cells().get("comments").sqlNull()).isTrue();
                    assertThat(rows.get(1).cells().get("comments").sqlNull()).isFalse();
                    assertThat(rows.get(1).cells().get("comments").bytes()).isZero();
                    var cell = rows.getFirst().cells().get("dataField");
                    byte[] data = body.getBytes(StandardCharsets.UTF_8);
                    assertThat(cell.inlineBytes()).isNull();
                    assertThat(cell.bytes()).isEqualTo(data.length);
                    var hash = CdsImportKeys.digest();
                    for (int offset = 0; offset < data.length; offset += 65536)
                        hash.update(
                                measurements.readCdsExportValueChunk(
                                        patient,
                                        patient,
                                        "dataField",
                                        offset,
                                        Math.min(65536, data.length - offset)));
                    assertThat(HexFormat.of().formatHex(hash.digest())).isEqualTo(cell.sha256());
                });
        assertThatThrownBy(
                        () ->
                                read.execute(
                                        status ->
                                                measurements.readCdsExportValueChunk(
                                                        patient, patient + 35, "dataField", 0, 10)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRetainAllOwnedExtensions_andTheirCompleteValues() {
        read.executeWithoutResult(
                status -> {
                    assertThat(extensions.countCdsExportRows(patient)).isEqualTo(35);
                    var rows = extensions.findCdsExportRowsAfter(patient, null, 32);
                    assertThat(rows).hasSize(32);
                    assertThat(rows.getFirst().cells().get("val").inlineBytes()).isNull();
                    assertThat(
                                    new String(
                                            extensions.readCdsExportValueChunk(
                                                    patient, patient, "val", 0, 64),
                                            StandardCharsets.UTF_8))
                            .isEqualTo(
                                    new String(
                                            Arrays.copyOf(
                                                    body.getBytes(StandardCharsets.UTF_8), 64),
                                            StandardCharsets.UTF_8));
                });
        assertThatThrownBy(
                        () ->
                                read.execute(
                                        status ->
                                                extensions.readCdsExportValueChunk(
                                                        patient, patient + 35, "val", 0, 10)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldKeepEveryMatchingVocabularyRow_withoutUnrelatedInstructionsOrOtherPatientCodes() {
        read.executeWithoutResult(
                status -> {
                    var dao = context.getBean(MeasurementTypeDao.class);
                    assertThat(dao.countCdsExportRows(patient)).isEqualTo(2);
                    assertThat(dao.findCdsExportRowsAfter(patient, null, 32))
                            .extracting(CdsExportRawRowDto::id)
                            .containsExactly((long) patient, (long) patient + 3);
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
                                                        .verify(reader.schema(), reader.columns()))
                                .doesNotThrowAnyException();
                        assertThat(reader.receiptColumns())
                                .extracting(column -> column.name())
                                .containsExactlyInAnyOrderElementsOf(reader.columns());
                    }
                });
        assertThatThrownBy(() -> measurements.readCdsExportReceiptColumns())
                .isInstanceOf(
                        org.springframework.transaction.IllegalTransactionStateException.class);
    }

    private void deleted(int owner, int original) {
        sql.update(
                "insert into measurementsDeleted(id,originalId,demographicNo,providerNo,type,dataField,measuringInstruction,dateObserved,dateEntered,dateDeleted) values(?,?,?,'',?,?,'in kg','2020-02-29',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                patient,
                original,
                owner,
                marker,
                body);
    }

    @Test
    void shouldCaptureDeletedObservations_andTheExtensionsTheirDeletionLeftBehind() {
        // Before: the deleted row required a NewCategory carrier and its extensions, which
        // EctDeleteData2Action leaves keyed by the original id, were never captured at all.
        int original = patient + 36;
        deleted(patient, original);
        sql.update(
                "insert into measurementsExt(id,measurement_id,keyval,val) values(?,?,'OwnedCareField','kg')",
                original,
                original);
        try {
            read.executeWithoutResult(
                    status -> {
                        var reader =
                                new CdsExportDeletedMeasurementSourceReader(
                                        context.getBean(MeasurementsDeletedDao.class));
                        assertThat(reader.count(patient)).isEqualTo(1);
                        assertThat(extensions.countCdsExportRows(patient)).isEqualTo(36);
                        var rows = extensions.findCdsExportRowsAfter(patient, (long) patient + 34, 32);
                        assertThat(rows).extracting(CdsExportRawRowDto::id).containsExactly((long) original);
                        assertThat(
                                        new String(
                                                extensions.readCdsExportValueChunk(
                                                        patient, original, "val", 0, 2),
                                                StandardCharsets.UTF_8))
                                .isEqualTo("kg");
                        assertThat(extensions.countCdsExportRows(patient + 1)).isEqualTo(1);
                    });
        } finally {
            sql.update(
                    "delete from measurementsExt where id=? and measurement_id=? and keyval='OwnedCareField'",
                    original,
                    original);
        }
    }

    @Test
    void shouldRefuseADeletedIdentityThatAnotherChartAlsoDeleted_beforeReadingExtensions() {
        int original = patient + 36;
        deleted(patient, original);
        sql.update(
                "insert into measurementsDeleted(id,originalId,demographicNo,providerNo,type,dataField,measuringInstruction,dateObserved,dateEntered,dateDeleted) values(?,?,?,'',?,'1','in kg','2020-02-29',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                patient + 1,
                original,
                patient + 1,
                marker);
        try {
            assertThatThrownBy(
                            () ->
                                    read.execute(
                                            status -> extensions.countCdsExportRows(patient)))
                    .hasMessage("CDS_EXPORT_AMBIGUOUS_SOURCE");
        } finally {
            sql.update(
                    "delete from measurementsDeleted where id=? and type=?", patient + 1, marker);
        }
    }

    /**
     * One OBX result as Hl7textResultsData.fileMeasurements files it: provider "0", the mapped
     * flowsheet code (or empty), and the report's identity and context in extensions.
     */
    private void laboratoryFiled(int id, String labNo, boolean deletedVersion) {
        if (deletedVersion)
            sql.update(
                    "insert into measurementsDeleted(id,originalId,demographicNo,providerNo,type,dataField,measuringInstruction,dateObserved,dateEntered,dateDeleted) values(?,?,?,'0',?,'5.4','','2020-02-29',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    id,
                    id,
                    patient,
                    marker);
        else
            sql.update(
                    "insert into measurements(id,demographicNo,providerNo,type,dataField,measuringInstruction,dateObserved,dateEntered,comments,appointmentNo) values(?,?,'0',?,'5.4','','2020-02-29 12:34:56',CURRENT_TIMESTAMP,'',0)",
                    id,
                    patient,
                    marker);
        String[][] context = {
            {"lab_no", labNo}, {"abnormal", "N"}, {"identifier", "4548-4"}, {"name", "HbA1c"},
            {"labname", "Fixture lab"}, {"accession", "ACC-1"}, {"request_datetime", "2020-02-28"},
            {"datetime", "2020-02-29 12:34:56"}, {"unit", "%"}, {"range", "4.0-6.0"}
        };
        for (int i = 0; i < context.length; i++)
            sql.update(
                    "insert into measurementsExt(id,measurement_id,keyval,val) values(?,?,?,?)",
                    id + 1000L * (i + 1),
                    id,
                    context[i][0],
                    context[i][1]);
    }

    private void removeLaboratoryFiled(int id) {
        for (int i = 0; i < 10; i++)
            sql.update(
                    "delete from measurementsExt where id=? and measurement_id=?",
                    id + 1000L * (i + 1),
                    id);
        sql.update("delete from measurements where id=? and type=?", id, marker);
        sql.update("delete from measurementsDeleted where id=? and type=?", id, marker);
    }

    @Test
    void shouldLeaveLaboratoryFiledResultsToTheLaboratoryPath_insteadOfCapturingThemAsCare() {
        // Before: every OBX result filed as a measurement left as CareElements/NewCategory
        // supporting data under the measurement right alone, bypassing the LaboratoryResults
        // selection and the laboratory rights; the complete HL7 report is exported there.
        laboratoryFiled(patient + 40, "77", false);
        laboratoryFiled(patient + 41, "76", true);
        try {
            read.executeWithoutResult(
                    status -> {
                        assertThat(measurements.countCdsExportRows(patient)).isEqualTo(35);
                        assertThat(extensions.countCdsExportRows(patient)).isEqualTo(35);
                        assertThat(
                                        context.getBean(MeasurementsDeletedDao.class)
                                                .countCdsExportRows(patient))
                                .isZero();
                        assertThat(
                                        measurements.findCdsExportRowsAfter(
                                                patient, (long) patient + 34, 32))
                                .isEmpty();
                    });
            assertThatThrownBy(
                            () ->
                                    read.execute(
                                            status ->
                                                    measurements.readCdsExportValueChunk(
                                                            patient, patient + 40, "dataField", 0, 3)))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            removeLaboratoryFiled(patient + 40);
            removeLaboratoryFiled(patient + 41);
        }
    }

    @Test
    void shouldHoldThePatient_whenALaboratoryFiledResultNamesNoReportTheLaboratoryPathExports()
            throws Exception {
        laboratoryFiled(patient + 40, "77", false);
        var command =
                new io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand(
                        "laboratory-scope",
                        "999998",
                        null,
                        io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile
                                .ONTARIO_MD_5_2,
                        io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand
                                .Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(patient),
                        Set.of(
                                io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory
                                        .DEMOGRAPHICS,
                                io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory
                                        .CARE_ELEMENTS,
                                io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory
                                        .LABORATORY_RESULTS),
                        "receiver",
                        "audit");
        var withoutLabs =
                new io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand(
                        "laboratory-scope",
                        "999998",
                        null,
                        command.profile(),
                        command.selection(),
                        List.of(),
                        List.of(patient),
                        Set.of(
                                io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory
                                        .DEMOGRAPHICS,
                                io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory
                                        .CARE_ELEMENTS),
                        "receiver",
                        "audit");
        var reader = new CdsExportMeasurementSourceReader(measurements);
        try {
            // No HL7 report 77 is routed to this patient: neither path could carry the value.
            assertThatThrownBy(() -> read.execute(status -> capture(reader.forRequest(command))))
                    .hasRootCauseMessage("CDS_EXPORT_NATIVE_CARE_LABORATORY_RESULT_UNROUTED");
            // Laboratory results were not selected: the operator excluded them deliberately.
            assertThat((Long) read.execute(status -> capture(reader.forRequest(withoutLabs))))
                    .isEqualTo(35L);
            sql.update(
                    "insert into patientLabRouting(id,demographic_no,lab_no,lab_type,created) values(?,?,77,'HL7',CURRENT_TIMESTAMP)",
                    patient,
                    patient);
            assertThat((Long) read.execute(status -> capture(reader.forRequest(command))))
                    .isEqualTo(35L);
        } finally {
            sql.update(
                    "delete from patientLabRouting where id=? and demographic_no=? and lab_no=77",
                    patient,
                    patient);
            removeLaboratoryFiled(patient + 40);
        }
    }

    /** Captures one reader's rows for the fixture patient and returns how many it wrote. */
    private long capture(CdsExportNativeSourceReader reader) {
        long count = reader.count(patient);
        try {
            new CdsExportNativeCaptureComposer()
                    .compose(
                            new java.io.ByteArrayOutputStream(),
                            new java.io.ByteArrayOutputStream(),
                            Set.of(reader.table()),
                            100_000,
                            1L << 26,
                            () -> {},
                            out -> {
                                out.beginTable(reader.table(), reader.columns(), count);
                                reader.capture(patient, out);
                                out.endTable();
                            });
        } catch (java.io.IOException failed) {
            throw new java.io.UncheckedIOException(failed);
        }
        return count;
    }

    @Test
    void shouldRefuseSharedCurrentAndForeignDeletedIdentities_withoutReadingExtensions() {
        deleted(patient + 1, patient);
        assertThatThrownBy(
                        () ->
                                read.execute(
                                        status ->
                                                extensions.findCdsExportRowsAfter(
                                                        patient, null, 32)))
                .hasMessage("CDS_EXPORT_AMBIGUOUS_SOURCE");
    }

    @Test
    void shouldRefuseAnOwnedDeletedIdentityThatNowBelongs_toAnotherCurrentPatient() {
        deleted(patient, patient + 35);
        assertThatThrownBy(() -> read.execute(status -> measurements.countCdsExportRows(patient)))
                .hasMessage("CDS_EXPORT_AMBIGUOUS_SOURCE");
    }

    @Test
    void shouldKeepLargeBodyChunksInTheSameRepeatableReadSnapshot_afterAConcurrentEdit() {
        try {
            read.executeWithoutResult(
                    status -> {
                        var cell =
                                measurements
                                        .findCdsExportRowsAfter(patient, null, 32)
                                        .getFirst()
                                        .cells()
                                        .get("dataField");
                        try (var writer =
                                java.util.concurrent.Executors.newSingleThreadExecutor()) {
                            writer.submit(
                                            () ->
                                                    sql.update(
                                                            "update measurements set dataField='Owned concurrent edit' where id=? and demographicNo=? and type=?",
                                                            patient,
                                                            patient,
                                                            marker))
                                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
                        } catch (Exception failed) {
                            throw new IllegalStateException(failed);
                        }
                        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                        var hash = CdsImportKeys.digest();
                        for (int offset = 0; offset < bytes.length; offset += 65536)
                            hash.update(
                                    measurements.readCdsExportValueChunk(
                                            patient,
                                            patient,
                                            "dataField",
                                            offset,
                                            Math.min(65536, bytes.length - offset)));
                        assertThat(HexFormat.of().formatHex(hash.digest()))
                                .isEqualTo(cell.sha256());
                    });
        } finally {
            sql.update(
                    "update measurements set dataField=? where id=? and demographicNo=? and type=?",
                    body,
                    patient,
                    patient,
                    marker);
        }
    }

    @Test
    void shouldReproduceTheImportersWholeNativeReceipts_forBothObservationAndExtension() {
        read.executeWithoutResult(
                status -> {
                    var receipt =
                            context.getBean(
                                            io.github.carlos_emr.carlos.demographic.cdsimport
                                                    .service.CdsTargetReceiptService.class)
                                    .capture(
                                            Map.of(
                                                    "measurements",
                                                    Integer.toString(patient),
                                                    "measurementsExt",
                                                    Integer.toString(patient)));
                    try {
                        var refs =
                                new com.fasterxml.jackson.databind.ObjectMapper()
                                        .readTree(receipt.targetReferences())
                                        .get("refs");
                        assertThat(refs.size()).isEqualTo(2);
                        for (var ref : refs) {
                            var dao =
                                    ref.get("table").asText().equals("measurements")
                                            ? (CdsExportPatientTableReadDao) measurements
                                            : extensions;
                            var row = dao.findCdsExportRowsAfter(patient, null, 32).getFirst();
                            var hash = CdsNativeRowDigest.hasher(dao.readCdsExportReceiptColumns());
                            for (var column : dao.readCdsExportReceiptColumns()) {
                                var cell = row.cells().get(column.name());
                                hash.append(cell.sqlNull(), cell.bytes(), cell.sha256());
                            }
                            assertThat(hash.finish()).isEqualTo(ref.get("digest").asText());
                        }
                    } catch (java.io.IOException failed) {
                        throw new java.io.UncheckedIOException(failed);
                    }
                });
    }
}
