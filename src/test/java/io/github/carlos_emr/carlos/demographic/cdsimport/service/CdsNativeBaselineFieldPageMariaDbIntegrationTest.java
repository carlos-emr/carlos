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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeBaselineCodec;
import io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineTestKeys;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * P1 regression on real MariaDB metadata: the sealed native baseline of an ordinary chart's
 * Demographics field page (32 {@code cds_import_field} rows, as {@link CdsSourceFieldService}
 * writes them) must open again, as {@code CdsImportUnitService} requires before it commits the
 * unit. The rows live in a session-temporary table shadowing {@code cds_import_field}, inside a
 * rolled-back transaction, so the shared disposable database is never changed.
 */
@Tag("integration")
@EnabledIfSystemProperty(named = "cds.export.test.properties", matches = ".+")
class CdsNativeBaselineFieldPageMariaDbIntegrationTest {
    @TempDir Path directory;

    @Test
    void shouldOpenTheSealedBaselineOfARealDemographicsFieldPage_asTheUnitCommitRequires()
            throws Exception {
        var page = demographicsPage();
        var parser = new CdsFieldParser();
        try (Connection connection = connection();
                var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            // MariaDB refuses a temporary table LIKE the base table of the same name; copy the
            // migrated definition through a differently named temporary table first.
            statement.execute("CREATE TEMPORARY TABLE cds_field_page_shape LIKE cds_import_field");
            statement.execute("CREATE TEMPORARY TABLE cds_import_field LIKE cds_field_page_shape");
            try {
                var targets = new LinkedHashMap<String, String>();
                String run = "11111111-1111-4111-8111-111111111111", unit = "c".repeat(64);
                try (var insert =
                        connection.prepareStatement(
                                "INSERT INTO cds_import_field(id,run_id,patient_key,unit_key,"
                                        + "demographic_no,category,source_namespace,source_path,"
                                        + "occurrence,value_type,date_precision,original_value,"
                                        + "normalized_value,payload_location,payload_digest,"
                                        + "payload_offset,payload_length)"
                                        + " VALUES(?,?,?,?,?,?,?,?,?,?,NULL,?,?,NULL,NULL,0,0)")) {
                    parser.forEachField(
                            page,
                            field -> {
                                try {
                                    String path =
                                            page.sourceXPath()
                                                    + (field.path().isEmpty()
                                                            ? ""
                                                            : "/" + field.path());
                                    String id =
                                            CdsImportKeys.hash(
                                                    unit,
                                                    field.namespace(),
                                                    path,
                                                    Long.toString(page.ordinal()));
                                    insert.setString(1, id);
                                    insert.setString(2, run);
                                    insert.setString(3, "b".repeat(64));
                                    insert.setString(4, unit);
                                    insert.setInt(5, 27);
                                    insert.setString(6, "Demographics");
                                    insert.setString(7, field.namespace());
                                    insert.setString(8, path);
                                    insert.setLong(9, page.ordinal());
                                    insert.setString(
                                            10,
                                            field.schemaType().isBlank()
                                                    ? field.kind()
                                                    : field.schemaType());
                                    insert.setString(11, parser.readText(page, field, 32768));
                                    insert.setString(
                                            12, parser.readLogicalText(page, field, 65536));
                                    insert.executeUpdate();
                                    targets.put("cds_import_field#" + targets.size(), id);
                                } catch (Exception failed) {
                                    throw new IllegalStateException(failed);
                                }
                            });
                }
                assertThat(targets).hasSize(CdsSourceFieldService.PAGE_SIZE);
                var entityManager =
                        org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class);
                var session = org.mockito.Mockito.mock(org.hibernate.Session.class);
                org.mockito.Mockito.when(entityManager.unwrap(org.hibernate.Session.class))
                        .thenReturn(session);
                org.mockito.Mockito.when(
                                session.doReturningWork(org.mockito.ArgumentMatchers.any()))
                        .thenAnswer(
                                call ->
                                        ((org.hibernate.jdbc.ReturningWork<?>) call.getArgument(0))
                                                .execute(connection));
                var keys = CdsNativeBaselineTestKeys.keys();
                var receipts = new CdsTargetReceiptService(keys);
                org.springframework.test.util.ReflectionTestUtils.setField(
                        receipts, "entityManager", entityManager);
                var receipt = receipts.capture(targets);
                // Beyond the 65,536-character bound that refused the envelope before.
                assertThat(receipt.nativeBaseline().length()).isGreaterThan(65_536);
                var document =
                        CdsNativeBaselineCodec.unseal(
                                receipt.nativeBaseline(),
                                receipt.targetReferences(),
                                receipt.targetDigest(),
                                keys);
                assertThat(document.rows()).hasSize(CdsSourceFieldService.PAGE_SIZE);
                assertThat(receipts.verify(receipt)).isTrue();
            } finally {
                connection.rollback();
                statement.execute(
                        "DROP TEMPORARY TABLE IF EXISTS cds_import_field, cds_field_page_shape");
            }
        }
    }

    @Test
    void shouldWriteAndReopenEveryFieldUnitOfThePayload8Chart_withAnInlineAndAnExternalReport()
            throws Exception {
        // The installed scaling case payload-8 (scripts/ci/run-cds-performance.py): one patient
        // with an 8 MiB PDF as an external file and the same document inline as cdsd:Media. Each
        // of its field units runs through the real CdsSourceFieldService, receipt capture and
        // witness seal, and must open again as the unit commit and a resume require.
        var records = payload8();
        assertThat(records).extracting(CdsRecordDto::section).contains("Demographics", "Reports");
        var parser = new CdsFieldParser();
        try (Connection connection = connection();
                var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("CREATE TEMPORARY TABLE cds_field_page_shape LIKE cds_import_field");
            statement.execute("CREATE TEMPORARY TABLE cds_import_field LIKE cds_field_page_shape");
            try {
                var keys = CdsNativeBaselineTestKeys.keys();
                var receipts = receipts(connection, keys);
                var ledger = org.mockito.Mockito.mock(CdsImportLedgerService.class);
                var ledgerUnit = new io.github.carlos_emr.carlos.commn.model.CdsImportUnit();
                ledgerUnit.setPatientKey("b".repeat(64));
                org.mockito.Mockito.when(
                                ledger.unit(
                                        org.mockito.ArgumentMatchers.anyString(),
                                        org.mockito.ArgumentMatchers.anyString()))
                        .thenReturn(ledgerUnit);
                var preservation = org.mockito.Mockito.mock(CdsImportFieldPersister.class);
                var payloadRows = new java.util.concurrent.atomic.AtomicInteger();
                org.mockito.Mockito.when(
                                preservation.preserve(
                                        org.mockito.ArgumentMatchers.anyString(),
                                        org.mockito.ArgumentMatchers.anyLong(),
                                        org.mockito.ArgumentMatchers.anyString(),
                                        org.mockito.ArgumentMatchers.anyString(),
                                        org.mockito.ArgumentMatchers.anyLong(),
                                        org.mockito.ArgumentMatchers.anyString(),
                                        org.mockito.ArgumentMatchers.anyString(),
                                        org.mockito.ArgumentMatchers.any(),
                                        org.mockito.ArgumentMatchers.any(),
                                        org.mockito.ArgumentMatchers.any(),
                                        org.mockito.ArgumentMatchers.any(),
                                        org.mockito.ArgumentMatchers.any(),
                                        org.mockito.ArgumentMatchers.anyLong(),
                                        org.mockito.ArgumentMatchers.anyLong()))
                        .thenAnswer(
                                call -> {
                                    if (call.getArgument(10) != null) payloadRows.incrementAndGet();
                                    return insert(connection, call.getArguments());
                                });
                var service =
                        new CdsSourceFieldService(
                                parser,
                                preservation,
                                org.mockito.Mockito.mock(CdsImportFileService.class),
                                receipts,
                                ledger);
                String run = "11111111-1111-4111-8111-111111111111";
                int units = 0;
                for (var record : records) {
                    var pages = new ArrayList<CdsRecordDto>();
                    parser.forEachPage(record, CdsSourceFieldService.PAGE_SIZE, pages::add);
                    for (var page : pages) {
                        String unit =
                                CdsImportKeys.hash(record.section(), Long.toString(page.ordinal()),
                                        Integer.toString(units++));
                        var receipt = service.write(run, 1, unit, page);
                        CdsImportKeys.required(receipt.targetReferences(), "target references", 65536);
                        var document =
                                CdsNativeBaselineCodec.unseal(
                                        receipt.nativeBaseline(),
                                        receipt.targetReferences(),
                                        receipt.targetDigest(),
                                        keys);
                        assertThat(document.rows()).hasSize((int) page.fieldCount());
                        assertThat(receipts.verify(receipt)).isTrue();
                    }
                }
                assertThat(units).isGreaterThanOrEqualTo(3);
                // The 8 MiB Media is preserved as a reference into the values spool, never inline.
                assertThat(payloadRows.get()).isEqualTo(1);
            } finally {
                connection.rollback();
                statement.execute(
                        "DROP TEMPORARY TABLE IF EXISTS cds_import_field, cds_field_page_shape");
            }
        }
    }

    /** The row CdsImportFieldPersister writes for one preserved source field. */
    private static String insert(Connection connection, Object[] arguments) throws Exception {
        String unit = (String) arguments[2], path = (String) arguments[3];
        long occurrence = (Long) arguments[4];
        String namespace = (String) arguments[5];
        String id = CdsImportKeys.hash(unit, namespace, path, Long.toString(occurrence));
        try (var insert =
                connection.prepareStatement(
                        "INSERT INTO cds_import_field(id,run_id,patient_key,unit_key,"
                                + "demographic_no,category,source_namespace,source_path,occurrence,"
                                + "value_type,date_precision,original_value,normalized_value,"
                                + "payload_location,payload_digest,payload_offset,payload_length)"
                                + " VALUES(?,?,?,?,27,'Reports',?,?,?,?,?,?,?,?,?,?,?)")) {
            insert.setString(1, id);
            insert.setString(2, (String) arguments[0]);
            insert.setString(3, "b".repeat(64));
            insert.setString(4, unit);
            insert.setString(5, namespace);
            insert.setString(6, path);
            insert.setLong(7, occurrence);
            insert.setString(8, (String) arguments[6]);
            insert.setString(9, (String) arguments[7]);
            insert.setString(10, (String) arguments[8]);
            insert.setString(11, (String) arguments[9]);
            insert.setString(12, (String) arguments[10]);
            insert.setString(13, (String) arguments[11]);
            insert.setLong(14, (Long) arguments[12]);
            insert.setLong(15, (Long) arguments[13]);
            insert.executeUpdate();
        }
        return id;
    }

    private static CdsTargetReceiptService receipts(
            Connection connection,
            io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineKeys keys) {
        var entityManager = org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class);
        var session = org.mockito.Mockito.mock(org.hibernate.Session.class);
        org.mockito.Mockito.when(entityManager.unwrap(org.hibernate.Session.class))
                .thenReturn(session);
        org.mockito.Mockito.when(session.doReturningWork(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(
                        call ->
                                ((org.hibernate.jdbc.ReturningWork<?>) call.getArgument(0))
                                        .execute(connection));
        var receipts = new CdsTargetReceiptService(keys);
        org.springframework.test.util.ReflectionTestUtils.setField(
                receipts, "entityManager", entityManager);
        return receipts;
    }

    /**
     * The payload-8 chart exactly as run-cds-performance.py writes it: minimal Demographics, an
     * external PDF report and the same 8 MiB document inline as Base64 cdsd:Media.
     */
    private List<CdsRecordDto> payload8() throws Exception {
        var random = new java.util.Random(8);
        byte[] document = new byte[8 << 20];
        random.nextBytes(document);
        byte[] header = "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, document, 0, header.length);
        Files.write(directory.resolve("report-000000.pdf"), document);
        Path source = directory.resolve("patient-000000.xml");
        try (var output = Files.newOutputStream(source)) {
            output.write(
                    ("<?xml version=\"1.0\"?><OmdCds xmlns=\"cds\" xmlns:cdsd=\"cds_dt\">"
                                    + "<PatientRecord><Demographics><Names><cdsd:LegalName"
                                    + " namePurpose=\"L\"><cdsd:FirstName><cdsd:Part>Performance"
                                    + "</cdsd:Part><cdsd:PartType>GIV</cdsd:PartType>"
                                    + "</cdsd:FirstName><cdsd:LastName><cdsd:Part>FAKE-PPAYLOAD8"
                                    + "</cdsd:Part><cdsd:PartType>FAMC</cdsd:PartType>"
                                    + "</cdsd:LastName></cdsd:LegalName></Names>"
                                    + "<DateOfBirth>1971-03-04</DateOfBirth><Gender>F</Gender>"
                                    + "<UniqueVendorIdSequence>PPAYLOAD8</UniqueVendorIdSequence>"
                                    + "<PersonStatusCode><PersonStatusAsEnum>A"
                                    + "</PersonStatusAsEnum></PersonStatusCode></Demographics>"
                                    + "<Reports><Format>Binary</Format>"
                                    + "<FileExtensionAndVersion>PDF</FileExtensionAndVersion>"
                                    + "<FilePath>report-000000.pdf</FilePath>"
                                    + "<Class>Medical Records Report</Class></Reports>"
                                    + "<Reports><Format>Binary</Format>"
                                    + "<FileExtensionAndVersion>PDF</FileExtensionAndVersion>"
                                    + "<Content><cdsd:Media>")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.write(java.util.Base64.getEncoder().encode(document));
            output.write(
                    ("</cdsd:Media></Content><Class>Medical Records Report</Class></Reports>"
                                    + "</PatientRecord></OmdCds>")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        source,
                        directory.resolve("parsed"),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        return records;
    }

    /** The first 32-field page of the unmodified fixture's Demographics record. */
    private CdsRecordDto demographicsPage() throws Exception {
        Path source = directory.resolve("patient.xml");
        try (var input =
                getClass().getResourceAsStream("/demographic/cds-import-all-categories.xml")) {
            Files.copy(Objects.requireNonNull(input), source);
        }
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        source,
                        directory.resolve("parsed"),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        var demographics =
                records.stream()
                        .filter(record -> record.section().equals("Demographics"))
                        .findFirst()
                        .orElseThrow();
        var pages = new ArrayList<CdsRecordDto>();
        new CdsFieldParser()
                .forEachPage(demographics, CdsSourceFieldService.PAGE_SIZE, pages::add);
        return pages.getFirst();
    }

    private static Connection connection() throws Exception {
        var properties = new Properties();
        try (var input =
                Files.newInputStream(Path.of(System.getProperty("cds.export.test.properties")))) {
            properties.load(input);
        }
        if (!properties
                        .getProperty("jdbcUrl", "")
                        .matches("jdbc:mariadb://[^/]+/cds_export_test(?:\\?.*)?")
                || !"true".equals(properties.getProperty("ownedDisposableDatabase")))
            throw new IllegalArgumentException("Owned CDS database required");
        return DriverManager.getConnection(
                properties.getProperty("jdbcUrl"),
                properties.getProperty("jdbcUser"),
                properties.getProperty("jdbcPassword"));
    }
}
