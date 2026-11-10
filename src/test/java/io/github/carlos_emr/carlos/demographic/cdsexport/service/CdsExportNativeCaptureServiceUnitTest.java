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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.carlos_emr.carlos.commn.dao.CdsExportRunDao;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDocumentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeCaptureDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStorageBindingDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.spec.SecretKeySpec;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("unit")
@Tag("cds-export")
class CdsExportNativeCaptureServiceUnitTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(
            value = CdsCategory.class,
            names = {
                "PERSONAL_HISTORY",
                "FAMILY_HISTORY",
                "PAST_HEALTH",
                "PROBLEM_LIST",
                "RISK_FACTORS",
                "ALERTS_AND_SPECIAL_NEEDS",
                "CLINICAL_NOTES",
                "REPORTS",
                "LABORATORY_RESULTS",
                "IMMUNIZATIONS",
                "APPOINTMENTS"
            })
    void shouldProjectTheSharedZoneIntoOnlySelectedCategories_whenResolvingTheActualCaptureRecipe(
            CdsCategory category) {
        var command =
                new CdsExportCommand(
                        "zone-proof",
                        "999998",
                        null,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(27),
                        Set.of(CdsCategory.DEMOGRAPHICS, category),
                        "receiver",
                        "audit");
        var reader = new CdsExportClinicalZoneSourceReader("America/Toronto");
        var projected = reader.forRequest(command);
        assertThat(projected.categories()).containsExactly(category);
        assertThat(reader.categories()).contains(CdsCategory.REPORTS, CdsCategory.APPOINTMENTS);
        var capture =
                new CdsExportNativeCaptureService(List.of(reader), factory, clock, transactions);
        assertThat(capture.requiredTables(command)).containsExactly("clinical_timezone");
    }

    @TempDir Path root;
    private static final int PATIENT = 27;
    private static final ObjectMapper JSON =
            new ObjectMapper().registerModule(new JavaTimeModule());
    private final String run = UUID.randomUUID().toString();
    private CdsExportStagingFactory factory;
    private CdsExportStagingService store;
    private CdsExportLeaseDto lease;
    private CdsExportRunDao clock;
    private DataSourceTransactionManager transactions;
    private FixtureReader source;
    private CdsExportCohortService.Cursor cursor;
    private CdsExportCohortService.Patient patientScope;
    private Set<Path> cohortMembers;

    @org.junit.jupiter.api.AfterEach
    void closeCohort() throws Exception {
        if (cursor != null) cursor.close();
    }

    @BeforeEach
    void prepare() throws Exception {
        prepare(Set.of(CdsCategory.DEMOGRAPHICS));
    }

    private void prepare(Set<CdsCategory> categories) throws Exception {
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        var data = new JdbcDataSource();
        data.setURL("jdbc:h2:mem:capture_" + run);
        transactions = new DataSourceTransactionManager(data);
        source = new FixtureReader();
        factory = mock(CdsExportStagingFactory.class);
        clock = mock(CdsExportRunDao.class);
        when(clock.now()).thenAnswer(ignored -> Instant.now());
        var command =
                new CdsExportCommand(
                        "capture-proof",
                        "999998",
                        null,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(PATIENT),
                        categories,
                        "receiver",
                        "audit");
        var storage = new CdsExportStorageBindingDto("primary", "primary", "a".repeat(64));
        lease =
                new CdsExportLeaseDto(
                        run,
                        1,
                        "b".repeat(64),
                        new CdsExportJobDocumentDto(
                                2,
                                new CdsExportActorDto("agent", "999998", null, null),
                                command,
                                "c".repeat(64),
                                "sender",
                                "d".repeat(64),
                                "e".repeat(64),
                                storage));
        when(factory.openExisting(eq(run), eq(1L), eq(storage), any()))
                .thenAnswer(
                        call -> {
                            store =
                                    new CdsExportStagingService(
                                            root,
                                            run,
                                            1,
                                            new SecretKeySpec(new byte[32], "AES"),
                                            8L << 20,
                                            0,
                                            call.getArgument(3));
                            return store;
                        });
        var cohortLedger = mock(CdsExportLedgerService.class);
        var cohorts = new CdsExportCohortService(factory, cohortLedger);
        var control = new Control();
        store = factory.openExisting(run, 1, storage, control::checkpoint);
        var selectionFile =
                store.stage(
                        "PATIENT_SELECTION",
                        output ->
                                output.write(
                                        ("{\"demographicNo\":27,\"physicianNo\":null}\n")
                                                .getBytes(
                                                        java.nio.charset.StandardCharsets.UTF_8)));
        var now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var selected =
                new io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportSelectionDto(
                        now, now, 1, selectionFile);
        var sealed = cohorts.prepare(lease, selected, control);
        when(cohortLedger.cohort(lease)).thenReturn(sealed.member());
        cursor = cohorts.open(sealed, control);
        patientScope = cursor.next();
        try (var members = Files.list(root)) {
            cohortMembers = members.collect(java.util.stream.Collectors.toSet());
        }
    }

    @Test
    void
            shouldReturnOnlyThreeClosedAuthenticatedMembers_afterOneReadOnlySnapshotAndIndependentReadback()
                    throws Exception {
        var control = new Control();
        var receipt =
                service()
                        .capture(
                                lease,
                                patientScope,
                                Set.of("demographic"),
                                1000,
                                8L << 20,
                                control);
        assertThat(source.captureTransactions).isEqualTo(3);
        assertThat(control.authorizations.get()).isEqualTo(3);
        assertThat(receipt.capture().fieldCount()).isEqualTo(60);
        assertThat(receipt.capture().patient()).isEqualTo(PATIENT);
        try (var files = Files.list(root)) {
            assertThat(files).hasSize(5);
        }
        try (var manifest = store.open(receipt.manifest())) {
            assertThat(JSON.readValue(manifest.readAllBytes(), CdsExportNativeCaptureDto.class))
                    .isEqualTo(receipt.capture());
        }
        try (var values = store.open(receipt.capture().values())) {
            String text =
                    new String(values.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(text).contains("FAKE-CDS 😀").doesNotContain("1900", "January");
        }
        assertThat(receipt.toString()).doesNotContain("27", "FAKE-CDS");
    }

    @Test
    void shouldRecomputeReceiptsFromEncryptedFrozenFields_withoutReopeningAnyMutableSource()
            throws Exception {
        source.receipts = true;
        var service = service();
        var tables = Set.of("demographic");
        var receipt = service.capture(lease, patientScope, tables, 1000, 8L << 20, new Control());
        var expected =
                io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest.hasher(
                        receipt.capture().tables().get("demographic").receiptColumns());
        for (var column : receipt.capture().tables().get("demographic").receiptColumns()) {
            String text =
                    switch (column.name()) {
                        case "demographic_no" -> "27";
                        case "first_name" -> "FAKE-CDS 😀";
                        case "email" -> "";
                        default -> null;
                    };
            byte[] bytes =
                    text == null
                            ? new byte[0]
                            : text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            expected.append(
                    text == null,
                    bytes.length,
                    java.util.HexFormat.of()
                            .formatHex(
                                    io.github.carlos_emr.carlos.utility.CdsImportKeys.digest()
                                            .digest(bytes)));
        }
        String baseline = expected.finish();
        source.fail = true;
        source.omit = true;
        source.engine = "Aria";
        var output =
                new ArrayList<
                        io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportNativeRowReceiptDto>();
        var completed =
                new CdsExportNativeRowReceiptReader(service)
                        .scan(
                                lease,
                                patientScope,
                                tables,
                                receipt,
                                1000,
                                8L << 20,
                                new Control(),
                                output::add);
        assertThat(completed.available()).isEqualTo(1);
        assertThat(completed.unavailable()).isZero();
        assertThat(output).hasSize(1);
        assertThat(output.getFirst().id()).isEqualTo("27");
        assertThat(output.getFirst().digest()).isEqualTo(baseline);
        assertThat(source.captureTransactions)
                .isEqualTo(3); // No schema/count/value or driver-metadata reread.
        var unit = "synthetic-applied-unit";
        var retained =
                new io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedIndexDto(
                        receipt.manifest().plaintextSha256(),
                        0,
                        0,
                        Map.of(),
                        Set.of(unit),
                        Map.of(
                                unit,
                                List.of(
                                        new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                                .CdsExportRetainedIndexDto.Reference(
                                                "demographic", "27", baseline))),
                        Map.of());
        var compared =
                new CdsExportRetainedReceiptVerificationService(
                                new CdsExportNativeRowReceiptReader(service), service)
                        .verify(
                                lease,
                                patientScope,
                                tables,
                                receipt,
                                retained,
                                1000,
                                8L << 20,
                                10,
                                new Control());
        assertThat(compared.unchangedUnits()).containsExactly(unit);
        assertThat(compared.reviewUnits()).isEmpty();
        var revoked = new Control();
        revoked.refuseAt = 2;
        assertThatThrownBy(
                        () ->
                                new CdsExportNativeRowReceiptReader(service)
                                        .scan(
                                                lease,
                                                patientScope,
                                                tables,
                                                receipt,
                                                1000,
                                                8L << 20,
                                                revoked,
                                                ignored -> {}))
                .hasMessage("ACCESS_REVOKED");
    }

    @Test
    void shouldKeepOldCapturesUnverifiable_insteadOfSubstitutingCurrentDriverMetadata()
            throws Exception {
        var service = service();
        var tables = Set.of("demographic");
        var receipt = service.capture(lease, patientScope, tables, 1000, 8L << 20, new Control());
        assertThat(receipt.capture().tables().get("demographic").receiptColumns()).isEmpty();
        source.receipts = true;
        source.fail = true;
        var output =
                new ArrayList<
                        io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportNativeRowReceiptDto>();
        var completed =
                new CdsExportNativeRowReceiptReader(service)
                        .scan(
                                lease,
                                patientScope,
                                tables,
                                receipt,
                                1000,
                                8L << 20,
                                new Control(),
                                output::add);
        assertThat(completed.unavailableTables()).containsExactly("demographic");
        assertThat(completed.available()).isZero();
        assertThat(output.getFirst().digest()).isNull();
    }

    @Test
    void shouldRetainAllExtensionColumns_whileReproducingTheExactLegacyVisibleColumnReceipt()
            throws Exception {
        var extensions = new ExtensionRows();
        extensions.receipts = true;
        var service =
                new CdsExportNativeCaptureService(
                        List.of(source, extensions), factory, clock, transactions);
        var tables = Set.of("demographic", "demographicExt");
        var receipt = service.capture(lease, patientScope, tables, 1000, 8L << 20, new Control());
        var metadata = receipt.capture().tables().get("demographicExt");
        assertThat(metadata.columns()).hasSize(8).contains("dashboard_single_value_key");
        assertThat(metadata.receiptColumns())
                .hasSize(7)
                .noneMatch(column -> column.name().equals("dashboard_single_value_key"));
        extensions.fail = true;
        source.fail = true;
        var output =
                new ArrayList<
                        io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportNativeRowReceiptDto>();
        var completed =
                new CdsExportNativeRowReceiptReader(service)
                        .scan(
                                lease,
                                patientScope,
                                tables,
                                receipt,
                                1000,
                                8L << 20,
                                new Control(),
                                output::add);
        assertThat(completed.available()).isEqualTo(2);
        assertThat(completed.unavailable()).isEqualTo(1);
        assertThat(output.stream().filter(row -> row.table().equals("demographicExt")).toList())
                .hasSize(2)
                .allMatch(row -> row.digest() != null);
    }

    @Test
    void shouldPreserveSelectedCustFieldEvidence_withoutReadingOrAttestingTheExcludedAlert()
            throws Exception {
        var owner = mock(io.github.carlos_emr.carlos.commn.dao.DemographicCustDao.class);
        var reader =
                new CdsExportDemographicCustSourceReader(owner)
                        .forRequest(lease.document().command());
        io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto schema;
        try (var input =
                getClass().getResourceAsStream("/cds-export/source-schema/demographiccust.json")) {
            schema =
                    JSON.readValue(
                            input,
                            io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto.class);
        }
        var columns =
                schema.columns().stream()
                        .map(
                                column ->
                                        new io.github.carlos_emr.carlos.demographic.cds.contract
                                                .CdsNativeRowDigest.Column(
                                                column.name(),
                                                column.name().equals("demographic_no")
                                                        ? "INT"
                                                        : "VARCHAR",
                                                255,
                                                0))
                        .toList();
        when(owner.readCdsExportSourceSchema()).thenReturn(schema);
        when(owner.readCdsExportReceiptColumns()).thenReturn(columns);
        when(owner.countCdsExportRows(27)).thenReturn(1L);
        var cells =
                new java.util.TreeMap<
                        String, io.github.carlos_emr.carlos.commn.dto.CdsExportRawCellDto>();
        for (String column : reader.columns()) {
            String text =
                    column.equals("demographic_no")
                            ? "27"
                            : column.equals("content") ? "Selected café 😀 note" : null;
            byte[] bytes =
                    text == null
                            ? new byte[0]
                            : text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String hash =
                    java.util.HexFormat.of()
                            .formatHex(
                                    io.github.carlos_emr.carlos.utility.CdsImportKeys.digest()
                                            .digest(bytes));
            cells.put(
                    column,
                    new io.github.carlos_emr.carlos.commn.dto.CdsExportRawCellDto(
                            text == null, bytes.length, hash, text == null ? null : bytes));
        }
        when(owner.findCdsExportDemographicRowsAfter(27, null, 32))
                .thenReturn(
                        List.of(
                                new io.github.carlos_emr.carlos.commn.dto.CdsExportRawRowDto(
                                        27, cells)));
        var service =
                new CdsExportNativeCaptureService(List.of(reader), factory, clock, transactions);
        var tables = Set.of("demographiccust");
        var captured = service.capture(lease, patientScope, tables, 1000, 8L << 20, new Control());
        assertThat(captured.capture().tables().get("demographiccust").receiptColumns())
                .hasSize(5)
                .noneMatch(column -> column.name().equals("cust3"));
        org.mockito.Mockito.verify(owner, org.mockito.Mockito.never())
                .findCdsExportRowsAfter(
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyInt());
        org.mockito.Mockito.reset(owner);
        var checked =
                new ArrayList<
                        io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportNativeRowReceiptDto>();
        var completed =
                new CdsExportNativeRowReceiptReader(service)
                        .scan(
                                lease,
                                patientScope,
                                tables,
                                captured,
                                1000,
                                8L << 20,
                                new Control(),
                                checked::add);
        assertThat(completed.available()).isZero();
        assertThat(completed.unavailable()).isZero();
        assertThat(completed.partialRows()).isEqualTo(1);
        assertThat(completed.unavailableTables()).isEmpty();
        assertThat(checked).hasSize(1);
        assertThat(checked.getFirst().partial()).isTrue();
        assertThat(checked.getFirst().state().columns())
                .hasSize(5)
                .noneMatch(column -> column.name().equals("cust3"));
        org.mockito.Mockito.verifyNoInteractions(owner);
    }

    @Test
    void shouldRestorePatientNoteText_fromTheOwnedGenerationWithoutOpeningMutableClinicalRows()
            throws Exception {
        var notes = new PatientNoteRows();
        var service =
                new CdsExportNativeCaptureService(List.of(notes), factory, clock, transactions);
        var tables = Set.of("demographiccust");
        var captured = service.capture(lease, patientScope, tables, 1000, 8L << 20, new Control());
        notes.text = "Changed after capture";
        notes.fail = true;
        var result =
                new CdsExportPatientNoteProjectionReader(service)
                        .read(
                                lease,
                                patientScope,
                                tables,
                                captured.manifest(),
                                1000,
                                8L << 20,
                                new Control());
        assertThat(result.rows()).isEqualTo(1);
        assertThat(result.sourceManifestSha256()).isEqualTo(captured.manifest().plaintextSha256());
        assertThat(result.value().text()).isEqualTo("Original café 😀 with literal </unotes> text");
        assertThat(result.value().representable()).isTrue();
    }

    @Test
    void shouldKeepAnAbsentCustRowDistinctFromAnEmptyNote_andRefuseDuplicateRows()
            throws Exception {
        var notes = new PatientNoteRows();
        notes.count = 0;
        var service =
                new CdsExportNativeCaptureService(
                        List.of(source, notes), factory, clock, transactions);
        var tables = Set.of("demographic", "demographiccust");
        var captured = service.capture(lease, patientScope, tables, 1000, 8L << 20, new Control());
        var result =
                new CdsExportPatientNoteProjectionReader(service)
                        .read(
                                lease,
                                patientScope,
                                tables,
                                captured.manifest(),
                                1000,
                                8L << 20,
                                new Control());
        assertThat(result.rows()).isZero();
        assertThat(result.value()).isNull();
        notes.count = 2;
        var duplicate = service.capture(lease, patientScope, tables, 1000, 8L << 20, new Control());
        assertThatThrownBy(
                        () ->
                                new CdsExportPatientNoteProjectionReader(service)
                                        .read(
                                                lease,
                                                patientScope,
                                                tables,
                                                duplicate.manifest(),
                                                1000,
                                                8L << 20,
                                                new Control()))
                .hasMessage("CDS_EXPORT_PATIENT_NOTE_DUPLICATE_ROW");
    }

    @Test
    void shouldRefuseNoteProjectionWhenFinalAuthorityIsLost_orTheCapturedTerminatorIsCorrupt()
            throws Exception {
        var notes = new PatientNoteRows();
        var service =
                new CdsExportNativeCaptureService(List.of(notes), factory, clock, transactions);
        var tables = Set.of("demographiccust");
        var captured = service.capture(lease, patientScope, tables, 1000, 8L << 20, new Control());
        var revoked = new Control();
        revoked.refuseAt = 2;
        assertThatThrownBy(
                        () ->
                                new CdsExportPatientNoteProjectionReader(service)
                                        .read(
                                                lease,
                                                patientScope,
                                                tables,
                                                captured.manifest(),
                                                1000,
                                                8L << 20,
                                                revoked))
                .hasMessage("ACCESS_REVOKED");
        Path path = root.resolve(captured.capture().values().memberId() + ".stage");
        byte[] encrypted = Files.readAllBytes(path);
        encrypted[encrypted.length - 1] ^= 1;
        Files.write(path, encrypted);
        assertThatThrownBy(
                        () ->
                                new CdsExportPatientNoteProjectionReader(service)
                                        .read(
                                                lease,
                                                patientScope,
                                                tables,
                                                captured.manifest(),
                                                1000,
                                                8L << 20,
                                                new Control()))
                .hasMessage("CDS_EXPORT_STAGED_AUTHENTICATION_FAILED");
    }

    @Test
    void shouldStreamAnOversizedNativeNote_toReviewOnlyAfterCompleteAuthentication()
            throws Exception {
        var notes = new PatientNoteRows();
        notes.text = "N".repeat(1_100_000);
        var service =
                new CdsExportNativeCaptureService(List.of(notes), factory, clock, transactions);
        var tables = Set.of("demographiccust");
        var captured = service.capture(lease, patientScope, tables, 1000, 8L << 20, new Control());
        notes.fail = true;
        var result =
                new CdsExportPatientNoteProjectionReader(service)
                        .read(
                                lease,
                                patientScope,
                                tables,
                                captured.manifest(),
                                1000,
                                8L << 20,
                                new Control());
        assertThat(result.value().text()).isNull();
        assertThat(result.value().representable()).isFalse();
        assertThat(
                        result.value().fields().entrySet().stream()
                                .filter(entry -> entry.getKey().column().equals("content"))
                                .findFirst()
                                .orElseThrow()
                                .getValue()
                                .rule())
                .isEqualTo("PATIENT_NOTE_WIRE_LENGTH_REQUIRES_REVIEW");
        Path path = root.resolve(captured.capture().values().memberId() + ".stage");
        byte[] encrypted = Files.readAllBytes(path);
        encrypted[encrypted.length - 1] ^= 1;
        Files.write(path, encrypted);
        assertThatThrownBy(
                        () ->
                                new CdsExportPatientNoteProjectionReader(service)
                                        .read(
                                                lease,
                                                patientScope,
                                                tables,
                                                captured.manifest(),
                                                1000,
                                                8L << 20,
                                                new Control()))
                .hasMessage("CDS_EXPORT_STAGED_AUTHENTICATION_FAILED");
    }

    @Test
    void shouldJoinBaseAndNote_fromTheSameManifestWithoutFillingMissingDemographicValues()
            throws Exception {
        var notes = new PatientNoteRows();
        var providers = new ProviderRows();
        var service =
                new CdsExportNativeCaptureService(
                        List.of(source, notes, providers), factory, clock, transactions);
        var tables = Set.of("demographic", "demographiccust", "provider");
        var captured = service.capture(lease, patientScope, tables, 1000, 8L << 20, new Control());
        source.fail = true;
        notes.fail = true;
        providers.fail = true;
        var result =
                new CdsExportDemographicProjectionService(service)
                        .projectWithPatientNote(
                                lease,
                                patientScope,
                                tables,
                                captured.manifest(),
                                1000,
                                8L << 20,
                                new Control());
        assertThat(result.sourceManifestSha256()).isEqualTo(captured.manifest().plaintextSha256());
        assertThat(result.noteRows()).isEqualTo(1);
        assertThat(result.note().representable()).isTrue();
        assertThat(result.representable()).isFalse();
        assertThat(result.demographic().birthDate()).isNull();
    }

    @Test
    void
            shouldVerifyInvokesAdditionalReadScopeChecksAtEveryLiveAuthorizationBoundary_forNativeCaptureServiceContract()
                    throws Exception {
        var control = new Control();
        service().capture(lease, patientScope, Set.of("demographic"), 1000, 8L << 20, control);
        assertThat(source.scopeChecks).isEqualTo(control.authorizations.get()).isEqualTo(3);
    }

    @Test
    void
            shouldVerifyASourceScopeRefusal_beforeCaptureReadsNoClinicalCellsAndCreatesNoPatientMembers() {
        source.denyScopeAt = 1;
        assertThatThrownBy(
                        () ->
                                service()
                                        .capture(
                                                lease,
                                                patientScope,
                                                Set.of("demographic"),
                                                1000,
                                                8L << 20,
                                                new Control()))
                .hasMessage("CDS_EXPORT_PREVENTION_PROGRAM_SCOPE");
        assertThat(source.captureTransactions).isZero();
        assertNoPatientMembers();
    }

    @Test
    void shouldVerifySourceScopeRefusals_afterCaptureOrReadbackEraseAllNewMembers() {
        for (int refusal : List.of(2, 3)) {
            source.scopeChecks = 0;
            source.denyScopeAt = refusal;
            assertThatThrownBy(
                            () ->
                                    service()
                                            .capture(
                                                    lease,
                                                    patientScope,
                                                    Set.of("demographic"),
                                                    1000,
                                                    8L << 20,
                                                    new Control()))
                    .hasMessage("CDS_EXPORT_PREVENTION_PROGRAM_SCOPE");
            assertNoPatientMembers();
        }
    }

    @Test
    void
            shouldVerifyCurrentSourceScopeRevocationPreventsReopeningFrozenClinicalValues_forNativeCaptureServiceContract()
                    throws Exception {
        var service = service();
        var captured =
                service.capture(
                        lease, patientScope, Set.of("demographic"), 1000, 8L << 20, new Control());
        source.denyScopeAt = source.scopeChecks + 1;
        assertThatThrownBy(
                        () ->
                                new CdsExportCapturedTextRowsReader(service)
                                        .forEach(
                                                lease,
                                                patientScope,
                                                Set.of("demographic"),
                                                "demographic",
                                                captured.manifest(),
                                                1000,
                                                8L << 20,
                                                16384,
                                                new Control(),
                                                ignored -> {
                                                    throw new AssertionError(
                                                            "Revoked source returned a row");
                                                }))
                .hasMessage("CDS_EXPORT_PREVENTION_PROGRAM_SCOPE");
    }

    @Test
    void shouldVerifyOrdinaryCategoryDenialRuns_beforeAdditionalSourceMetadataChecks() {
        var control =
                new Control() {
                    @Override
                    public void authorizeCategory(
                            int patient, CdsCategory category, boolean sensitive) {
                        throw new SecurityException("CATEGORY_DENIED");
                    }
                };
        assertThatThrownBy(
                        () ->
                                service()
                                        .capture(
                                                lease,
                                                patientScope,
                                                Set.of("demographic"),
                                                1000,
                                                8L << 20,
                                                control))
                .hasMessage("CATEGORY_DENIED");
        assertThat(source.scopeChecks).isZero();
        assertThat(source.captureTransactions).isZero();
        assertNoPatientMembers();
    }

    @Test
    void shouldVerifyDeclaredSourceNameMustMatchItsPinnedPhysicalSchema_beforeValueCapture() {
        source.declaredTable = "demographicExt";
        assertThatThrownBy(
                        () ->
                                service()
                                        .capture(
                                                lease,
                                                patientScope,
                                                Set.of("demographicExt"),
                                                1000,
                                                8L << 20,
                                                new Control()))
                .hasMessage("CDS_EXPORT_SOURCE_SCHEMA_REQUIRES_REVIEW");
        assertThat(source.captureTransactions).isEqualTo(1);
        assertNoPatientMembers();
    }

    @Test
    void shouldRefuseAnyOrdinaryMissingReceiptColumn_beforeWritingPatientMembers() {
        source.receipts = true;
        source.dropReceiptEmail = true;
        assertThatThrownBy(
                        () ->
                                service()
                                        .capture(
                                                lease,
                                                patientScope,
                                                Set.of("demographic"),
                                                1000,
                                                8L << 20,
                                                new Control()))
                .hasMessage("CDS_EXPORT_CAPTURE_RECEIPT_RECIPE_INVALID");
        assertNoPatientMembers();
    }

    @Test
    void shouldResolvePrimaryPhysicianFromEncryptedCapturedInputs_withoutClinicalOrLookupRereads()
            throws Exception {
        source.primaryProvider = "0é27";
        var providers = new ProviderRows();
        var service =
                new CdsExportNativeCaptureService(
                        List.of(source, providers), factory, clock, transactions);
        var tables = Set.of("demographic", "provider");
        var receipt = service.capture(lease, patientScope, tables, 1000, 8L << 20, new Control());
        source.fail = true;
        providers.fail = true;
        var result =
                new CdsExportDemographicProjectionService(service)
                        .project(
                                lease,
                                patientScope,
                                tables,
                                receipt.manifest(),
                                1000,
                                8L << 20,
                                new Control());
        assertThat(result.primaryPhysician().firstName()).isEqualTo("Captured physician");
        assertThat(result.primaryPhysician().ohipNumber()).isEqualTo("001234");
        assertThat(result.primaryPhysician().cpsoNumber()).isEqualTo("000123");
        assertThat(result.relatedFields()).hasSize(6);
        assertThat(result.fields().get("provider_no").outcome())
                .isEqualTo(
                        io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportDemographicMappingDto.Outcome.MAPPED);
        assertThat(result.baseRowRepresentable())
                .isFalse(); // The independently missing birth date still blocks the patient.
        var revoked = new Control();
        revoked.refuseAt = 4;
        assertThatThrownBy(
                        () ->
                                new CdsExportDemographicProjectionService(service)
                                        .project(
                                                lease,
                                                patientScope,
                                                tables,
                                                receipt.manifest(),
                                                1000,
                                                8L << 20,
                                                revoked))
                .hasMessage("ACCESS_REVOKED");
    }

    @Test
    void shouldEraseEveryMember_whenPostCaptureAuthorityIsRevoked() {
        for (int refusal : List.of(2, 3)) {
            var control = new Control();
            control.refuseAt = refusal;
            assertThatThrownBy(
                            () ->
                                    service()
                                            .capture(
                                                    lease,
                                                    patientScope,
                                                    Set.of("demographic"),
                                                    1000,
                                                    8L << 20,
                                                    control))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("ACCESS_REVOKED");
            assertNoPatientMembers();
        }
    }

    @Test
    void shouldEraseClosedMembers_whenTheSourceCommitFails() {
        var fail =
                new DataSourceTransactionManager(transactions.getDataSource()) {
                    @Override
                    protected void doCommit(DefaultTransactionStatus status) {
                        super.doCommit(status);
                        throw new TransactionSystemException("SOURCE_COMMIT_FAILED");
                    }
                };
        var service = new CdsExportNativeCaptureService(List.of(source), factory, clock, fail);
        assertThatThrownBy(
                        () ->
                                service.capture(
                                        lease,
                                        patientScope,
                                        Set.of("demographic"),
                                        1000,
                                        8L << 20,
                                        new Control()))
                .hasMessage("SOURCE_COMMIT_FAILED");
        assertNoPatientMembers();
    }

    @Test
    void shouldEraseNestedPartialFiles_whenAReaderFailsOrOmitsItsCountedRow() {
        source.fail = true;
        assertThatThrownBy(
                        () ->
                                service()
                                        .capture(
                                                lease,
                                                patientScope,
                                                Set.of("demographic"),
                                                1000,
                                                8L << 20,
                                                new Control()))
                .hasMessage("SOURCE_FAILED");
        assertNoPatientMembers();
        source.fail = false;
        source.omit = true;
        assertThatThrownBy(
                        () ->
                                service()
                                        .capture(
                                                lease,
                                                patientScope,
                                                Set.of("demographic"),
                                                1000,
                                                8L << 20,
                                                new Control()))
                .hasMessage("CDS_EXPORT_NATIVE_CAPTURE_INVALID");
        assertNoPatientMembers();
    }

    @Test
    void shouldRefuseAMissingPatientOrUnmappedSchema_beforePayloadCapture() {
        source.count = 0;
        assertThatThrownBy(
                        () ->
                                service()
                                        .capture(
                                                lease,
                                                patientScope,
                                                Set.of("demographic"),
                                                1000,
                                                8L << 20,
                                                new Control()))
                .hasMessage("CDS_EXPORT_SELECTED_PATIENT_MISSING");
        assertNoPatientMembers();
        source.count = 1;
        source.engine = "Aria";
        assertThatThrownBy(
                        () ->
                                service()
                                        .capture(
                                                lease,
                                                patientScope,
                                                Set.of("demographic"),
                                                1000,
                                                8L << 20,
                                                new Control()))
                .hasMessage("CDS_EXPORT_SOURCE_SCHEMA_REQUIRES_REVIEW");
        assertNoPatientMembers();
    }

    @Test
    void shouldRefuseAmbientTransactionsAndUndeclaredScopes_forNativeCaptureServiceContract() {
        var outer = new TransactionTemplate(transactions);
        assertThatThrownBy(
                        () ->
                                outer.execute(
                                        status -> {
                                            try {
                                                return service()
                                                        .capture(
                                                                lease,
                                                                patientScope,
                                                                Set.of("demographic"),
                                                                1000,
                                                                8L << 20,
                                                                new Control());
                                            } catch (IOException failure) {
                                                throw new java.io.UncheckedIOException(failure);
                                            }
                                        }))
                .hasMessage("CDS_EXPORT_AMBIENT_TRANSACTION_REFUSED");
        assertThatThrownBy(
                        () ->
                                service()
                                        .capture(
                                                lease,
                                                patientScope,
                                                Set.of("unreviewed"),
                                                1000,
                                                8L << 20,
                                                new Control()))
                .hasMessage("CDS_EXPORT_CAPTURE_SCOPE_INVALID");
        assertNoPatientMembers();
    }

    @Test
    void shouldKeepCancellationProbesIndependentInsideTheSourceSnapshot_andEraseOnCancellation() {
        var control = new Control();
        control.cancelDuringCapture = true;
        assertThatThrownBy(
                        () ->
                                service()
                                        .capture(
                                                lease,
                                                patientScope,
                                                Set.of("demographic"),
                                                1000,
                                                8L << 20,
                                                control))
                .hasMessage("CANCELLED");
        assertNoPatientMembers();
    }

    @Test
    void shouldRejectAmbiguousSourceReaderRegistration_forNativeCaptureServiceContract() {
        assertThatThrownBy(
                        () ->
                                new CdsExportNativeCaptureService(
                                        List.of(source, source), factory, clock, transactions))
                .hasMessage("CDS_EXPORT_SOURCE_READER_AMBIGUOUS");
    }

    @Test
    void shouldRefuseAnExpiredCohortPatientScope_beforeReadingTheChart() throws Exception {
        assertThat(cursor.next()).isNull();
        assertThatThrownBy(
                        () ->
                                service()
                                        .capture(
                                                lease,
                                                patientScope,
                                                Set.of("demographic"),
                                                1000,
                                                8L << 20,
                                                new Control()))
                .hasMessage("CDS_EXPORT_PATIENT_SCOPE_EXPIRED");
        assertThat(source.captureTransactions).isZero();
        assertNoPatientMembers();
    }

    @Test
    void shouldScanOnlyTheCapturedGeneration_withoutQueryingClinicalRowsAgain() throws Exception {
        var service = service();
        var receipt =
                service.capture(
                        lease, patientScope, Set.of("demographic"), 1000, 8L << 20, new Control());
        source.fail = true;
        var output = new java.io.ByteArrayOutputStream();
        var checked = new AtomicInteger();
        var result =
                service.scan(
                        lease,
                        patientScope,
                        Set.of("demographic"),
                        receipt.manifest(),
                        1000,
                        8L << 20,
                        new Control(),
                        new CdsExportNativeCaptureReader.Fields() {
                            @Override
                            public java.io.OutputStream output(
                                    io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                                    .CdsExportCapturedFieldDto
                                            field) {
                                return output;
                            }

                            @Override
                            public void verified(
                                    io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                                    .CdsExportCapturedFieldDto
                                            field) {
                                checked.incrementAndGet();
                            }
                        });
        assertThat(result).isEqualTo(receipt.capture());
        assertThat(checked.get()).isEqualTo(60);
        assertThat(output.toString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("FAKE-CDS 😀");
        assertThat(source.captureTransactions).isEqualTo(3);
    }

    @Test
    void shouldRefuseWrongCohortOrSchemaEvidence_beforeDeliveringAnyCapturedValue()
            throws Exception {
        var service = service();
        var receipt =
                service.capture(
                        lease, patientScope, Set.of("demographic"), 1000, 8L << 20, new Control());
        var original = receipt.capture();
        for (boolean wrongCohort : List.of(true, false)) {
            var tables = new java.util.TreeMap<>(original.tables());
            if (!wrongCohort) {
                var table = tables.get("demographic");
                tables.put(
                        "demographic",
                        new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportCapturedTableDto(
                                table.columns(), table.rows(), "f".repeat(64)));
            }
            var altered =
                    new CdsExportNativeCaptureDto(
                            2,
                            original.runId(),
                            original.epoch(),
                            original.patient(),
                            wrongCohort ? "f".repeat(64) : original.cohortSha256(),
                            original.readStartedAt(),
                            original.readCompletedAt(),
                            tables,
                            original.fieldCount(),
                            original.valueBytes(),
                            original.values(),
                            original.index());
            var json =
                    new ObjectMapper()
                            .registerModule(new JavaTimeModule())
                            .disable(
                                    com.fasterxml.jackson.databind.SerializationFeature
                                            .WRITE_DATES_AS_TIMESTAMPS);
            var member =
                    store.stage(
                            "SOURCE_MANIFEST",
                            output -> output.write(json.writeValueAsBytes(altered)));
            var values = new AtomicInteger();
            assertThatThrownBy(
                            () ->
                                    service.scan(
                                            lease,
                                            patientScope,
                                            Set.of("demographic"),
                                            member,
                                            1000,
                                            8L << 20,
                                            new Control(),
                                            field -> {
                                                values.incrementAndGet();
                                                return java.io.OutputStream.nullOutputStream();
                                            }))
                    .hasMessage(
                            wrongCohort
                                    ? "CDS_EXPORT_CAPTURE_SCOPE_MISMATCH"
                                    : "CDS_EXPORT_CAPTURE_SCHEMA_MISMATCH");
            assertThat(values).hasValue(0);
        }
    }

    @Test
    void shouldEraseUnpublishedMapperOutput_whenCurrentAuthorityIsRevokedAfterTheCapturedRead()
            throws Exception {
        var service = service();
        var receipt =
                service.capture(
                        lease, patientScope, Set.of("demographic"), 1000, 8L << 20, new Control());
        long before;
        try (var files = Files.list(root)) {
            before = files.count();
        }
        var control = new Control();
        control.refuseAt = 2;
        var target = store;
        assertThatThrownBy(
                        () ->
                                target.stage(
                                        "MAPPER_OUTPUT",
                                        output ->
                                                service.scan(
                                                        lease,
                                                        patientScope,
                                                        Set.of("demographic"),
                                                        receipt.manifest(),
                                                        1000,
                                                        8L << 20,
                                                        control,
                                                        field -> output)))
                .hasMessage("ACCESS_REVOKED");
        try (var files = Files.list(root)) {
            assertThat(files).hasSize(Math.toIntExact(before));
        }
    }

    @Test
    void
            shouldReadBoundedDemographicMapperInput_fromTheVerifiedCaptureWithoutRereadingClinicalRows()
                    throws Exception {
        var service = service();
        var receipt =
                service.capture(
                        lease, patientScope, Set.of("demographic"), 1000, 8L << 20, new Control());
        source.fail = true;
        var row =
                new CdsExportDemographicCaptureReader(service)
                        .read(
                                lease,
                                patientScope,
                                Set.of("demographic"),
                                receipt.manifest(),
                                1000,
                                8L << 20,
                                new Control());
        assertThat(row.demographicNo()).isEqualTo(PATIENT);
        assertThat(row.fields())
                .hasSize(59)
                .containsEntry("first_name", "FAKE-CDS 😀")
                .containsEntry("email", "")
                .containsEntry("date_of_birth", null);
        assertThat(source.captureTransactions).isEqualTo(3);
    }

    @Test
    void shouldRefuseMapperInput_whenAuthorityIsRevokedAfterTheAuthenticatedRead()
            throws Exception {
        var service = service();
        var receipt =
                service.capture(
                        lease, patientScope, Set.of("demographic"), 1000, 8L << 20, new Control());
        var control = new Control();
        control.refuseAt = 2;
        assertThatThrownBy(
                        () ->
                                new CdsExportDemographicCaptureReader(service)
                                        .read(
                                                lease,
                                                patientScope,
                                                Set.of("demographic"),
                                                receipt.manifest(),
                                                1000,
                                                8L << 20,
                                                control))
                .hasMessage("ACCESS_REVOKED");
    }

    @Test
    void shouldRefuseCapturedDemographicIdentity_fromAnotherPatient() throws Exception {
        source.capturedPatient = PATIENT + 1;
        var service = service();
        var receipt =
                service.capture(
                        lease, patientScope, Set.of("demographic"), 1000, 8L << 20, new Control());
        assertThatThrownBy(
                        () ->
                                new CdsExportDemographicCaptureReader(service)
                                        .read(
                                                lease,
                                                patientScope,
                                                Set.of("demographic"),
                                                receipt.manifest(),
                                                1000,
                                                8L << 20,
                                                new Control()))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_CAPTURE_INVALID");
    }

    @Test
    void shouldRefuseUnselectedClinicalTables_beforeReadingAnyRows() {
        var allergies = mock(io.github.carlos_emr.carlos.commn.dao.AllergyDao.class);
        var dates = mock(io.github.carlos_emr.carlos.commn.dao.PartialDateDao.class);
        var appointments = mock(io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao.class);
        var service =
                new CdsExportNativeCaptureService(
                        List.of(
                                source,
                                new CdsExportAllergySourceReader(allergies),
                                new CdsExportPartialDateSourceReader(dates),
                                new CdsExportAppointmentSourceReader(appointments)),
                        factory,
                        clock,
                        transactions);
        for (String table : Set.of("allergies", "partial_date", "appointment")) {
            assertThatThrownBy(
                            () ->
                                    service.capture(
                                            lease,
                                            patientScope,
                                            Set.of(table),
                                            1000,
                                            8L << 20,
                                            new Control()))
                    .hasMessage("CDS_EXPORT_CAPTURE_SCOPE_INVALID");
        }
        org.mockito.Mockito.verifyNoInteractions(allergies, dates, appointments);
    }

    @Test
    void shouldStreamMultipleCapturedTextRows_withoutRequeryingTheClinicalSource()
            throws Exception {
        var ext = new ExtensionRows();
        var service = new CdsExportNativeCaptureService(List.of(ext), factory, clock, transactions);
        var receipt =
                service.capture(
                        lease,
                        patientScope,
                        Set.of("demographicExt"),
                        1000,
                        8L << 20,
                        new Control());
        ext.fail = true;
        var rows =
                new java.util.ArrayList<
                        io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportCapturedTextRowDto>();
        var result =
                new CdsExportCapturedTextRowsReader(service)
                        .forEach(
                                lease,
                                patientScope,
                                Set.of("demographicExt"),
                                "demographicExt",
                                receipt.manifest(),
                                1000,
                                8L << 20,
                                1_048_576,
                                new Control(),
                                rows::add);
        assertThat(result).isEqualTo(receipt.capture());
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().fields())
                .containsEntry("value", "")
                .containsEntry("demographic_no", "27");
        assertThat(rows.get(1).fields()).containsEntry("value", null);
        assertThat(rows.getFirst().recordKey()).isLessThan(rows.get(1).recordKey());
        assertThatThrownBy(() -> rows.getFirst().fields().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldEraseDerivedRows_whenPostReadAuthorizationOrTheConsumerFails() throws Exception {
        var ext = new ExtensionRows();
        var service = new CdsExportNativeCaptureService(List.of(ext), factory, clock, transactions);
        var receipt =
                service.capture(
                        lease,
                        patientScope,
                        Set.of("demographicExt"),
                        1000,
                        8L << 20,
                        new Control());
        long before;
        try (var files = Files.list(root)) {
            before = files.count();
        }
        for (boolean revoked : List.of(true, false)) {
            var control = new Control();
            if (revoked) control.refuseAt = 2;
            var seen = new AtomicInteger();
            assertThatThrownBy(
                            () ->
                                    store.stage(
                                            "MAPPER_OUTPUT",
                                            output ->
                                                    new CdsExportCapturedTextRowsReader(service)
                                                            .forEach(
                                                                    lease,
                                                                    patientScope,
                                                                    Set.of("demographicExt"),
                                                                    "demographicExt",
                                                                    receipt.manifest(),
                                                                    1000,
                                                                    8L << 20,
                                                                    1_048_576,
                                                                    control,
                                                                    row -> {
                                                                        seen.incrementAndGet();
                                                                        output.write(1);
                                                                        if (!revoked)
                                                                            throw new IOException(
                                                                                    "CONSUMER_FAILED");
                                                                    })))
                    .hasMessage(revoked ? "ACCESS_REVOKED" : "CONSUMER_FAILED");
            assertThat(seen).hasValue(1);
            try (var files = Files.list(root)) {
                assertThat(files).hasSize(Math.toIntExact(before));
            }
        }
    }

    @Test
    void shouldRefuseAnOversizedTextRow_withoutReturningAPartialRow() throws Exception {
        var ext = new ExtensionRows();
        var service = new CdsExportNativeCaptureService(List.of(ext), factory, clock, transactions);
        var receipt =
                service.capture(
                        lease,
                        patientScope,
                        Set.of("demographicExt"),
                        1000,
                        8L << 20,
                        new Control());
        var rows = new AtomicInteger();
        assertThatThrownBy(
                        () ->
                                new CdsExportCapturedTextRowsReader(service)
                                        .forEach(
                                                lease,
                                                patientScope,
                                                Set.of("demographicExt"),
                                                "demographicExt",
                                                receipt.manifest(),
                                                1000,
                                                8L << 20,
                                                1,
                                                new Control(),
                                                row -> rows.incrementAndGet()))
                .hasMessage("CDS_EXPORT_NATIVE_ROW_INVALID");
        assertThat(rows).hasValue(0);
    }

    @Test
    void shouldReadRelatedTablesInOnePass_withoutMergingEqualKeysFromDifferentTables()
            throws Exception {
        var fields =
                new RetainedRows(
                        io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan
                                .IMPORT_FIELDS);
        var units =
                new RetainedRows(
                        io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan
                                .IMPORT_UNITS);
        var service =
                new CdsExportNativeCaptureService(
                        List.of(fields, units), factory, clock, transactions);
        var tables = Set.of(fields.table(), units.table());
        var control = new Control();
        var receipt = service.capture(lease, patientScope, tables, 1000, 8L << 20, control);
        fields.fail = true;
        units.fail =
                true; // Mapping must use the owned generation, never reread current rows/schema.
        var decoded =
                new ArrayList<
                        io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportCapturedTextRowDto>();
        new CdsExportCapturedTextRowsReader(service)
                .forEachTable(
                        lease,
                        patientScope,
                        tables,
                        tables,
                        receipt.manifest(),
                        1000,
                        8L << 20,
                        1_048_576,
                        control,
                        decoded::add);
        assertThat(decoded).hasSize(2);
        assertThat(decoded).extracting(row -> row.recordKey()).containsOnly("a".repeat(64));
        assertThat(decoded)
                .extracting(row -> row.table())
                .containsExactlyInAnyOrder("cds_import_field", "cds_import_unit");
        assertThat(decoded.getFirst().fields()).hasSize(17);
        assertThat(decoded.getLast().fields()).hasSize(14);
        assertThat(decoded.getLast().fields()).containsEntry("native_baseline", null);
    }

    @Test
    void shouldRequireTheUnionOfSensitiveReadDomains_oncePerFreshBoundary() throws Exception {
        cursor.close();
        prepare(Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.LABORATORY_RESULTS));
        int[] rights = {0, 0};
        var control =
                new Control() {
                    @Override
                    public void authorizeCategory(
                            int patient, CdsCategory category, boolean sensitive) {
                        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                                .isFalse();
                        if (category == CdsCategory.LABORATORY_RESULTS) {
                            assertThat(sensitive).isTrue();
                            rights[1]++;
                        } else {
                            assertThat(category).isEqualTo(CdsCategory.DEMOGRAPHICS);
                            assertThat(sensitive).isFalse();
                            rights[0]++;
                        }
                    }
                };
        var service =
                new CdsExportNativeCaptureService(
                        List.of(
                                laboratoryScope(source, false),
                                laboratoryScope(new ExtensionRows(), true)),
                        factory,
                        clock,
                        transactions);
        service.capture(
                lease,
                patientScope,
                Set.of("demographic", "demographicExt"),
                1000,
                8L << 20,
                control);
        assertThat(rights).containsExactly(3, 3);
    }

    @Test
    void shouldReadNoSourceValues_whenSensitiveRetainedAuthorityIsMissing() throws Exception {
        cursor.close();
        prepare(Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.LABORATORY_RESULTS));
        var service =
                new CdsExportNativeCaptureService(
                        List.of(laboratoryScope(source, true)), factory, clock, transactions);
        var control =
                new Control() {
                    @Override
                    public void authorizeCategory(
                            int patient, CdsCategory category, boolean sensitive) {
                        if (category == CdsCategory.LABORATORY_RESULTS) {
                            assertThat(sensitive).isTrue();
                            throw new SecurityException("SENSITIVE_READ_DENIED");
                        }
                    }
                };
        assertThatThrownBy(
                        () ->
                                service.capture(
                                        lease,
                                        patientScope,
                                        Set.of("demographic"),
                                        1000,
                                        8L << 20,
                                        control))
                .hasMessage("SENSITIVE_READ_DENIED");
        assertThat(source.captureTransactions).isZero();
        assertNoPatientMembers();
    }

    @Test
    void shouldVerifyRequiredSnapshotDependenciesCannotBeOmitted_beforeSourceReadsOrAuthorization()
            throws Exception {
        source.dependencies = Set.of("prevention_configuration");
        assertThatThrownBy(
                        () ->
                                service()
                                        .capture(
                                                lease,
                                                patientScope,
                                                Set.of("demographic"),
                                                1000,
                                                8L << 20,
                                                new Control()))
                .hasMessage("CDS_EXPORT_CAPTURE_SCOPE_INVALID");
        assertThatThrownBy(
                        () ->
                                service()
                                        .authorizeSources(
                                                lease,
                                                patientScope,
                                                Set.of("demographic"),
                                                new Control()))
                .hasMessage("CDS_EXPORT_CAPTURE_SCOPE_INVALID");
        assertThat(source.captureTransactions).isZero();
    }

    @Test
    void
            shouldVerifyFrozenReadsCannotBypassAReadersNewlyRequiredSnapshotDependency_forNativeCaptureServiceContract()
                    throws Exception {
        var captured =
                service()
                        .capture(
                                lease,
                                patientScope,
                                Set.of("demographic"),
                                1000,
                                8L << 20,
                                new Control());
        source.dependencies = Set.of("prevention_configuration");
        assertThatThrownBy(
                        () ->
                                service()
                                        .scan(
                                                lease,
                                                patientScope,
                                                Set.of("demographic"),
                                                captured.manifest(),
                                                1000,
                                                8L << 20,
                                                new Control(),
                                                field -> java.io.OutputStream.nullOutputStream()))
                .hasMessage("CDS_EXPORT_CAPTURE_SCOPE_INVALID");
    }

    @Test
    void shouldRefusePreparedCategorySubstitution_beforeEnteringTheSourceSnapshot() {
        source.preparedReader = laboratoryScope(source, false);
        assertThatThrownBy(
                        () ->
                                service()
                                        .capture(
                                                lease,
                                                patientScope,
                                                Set.of("demographic"),
                                                1000,
                                                8L << 20,
                                                new Control()))
                .hasMessage("CDS_EXPORT_CAPTURE_PREPARED_SCOPE_INVALID");
        assertThat(source.preparations).isEqualTo(1);
        assertThat(source.captureTransactions).isZero();
    }

    @Test
    void shouldAuthorizeCategories_beforePreparingPatientDependencies() {
        var control = new Control();
        control.refuseAt = 1;
        assertThatThrownBy(
                        () ->
                                service()
                                        .capture(
                                                lease,
                                                patientScope,
                                                Set.of("demographic"),
                                                1000,
                                                8L << 20,
                                                control))
                .hasMessage("ACCESS_REVOKED");
        assertThat(source.preparations).isZero();
        assertThat(source.captureTransactions).isZero();
    }

    @Test
    void shouldVerifyARevocation_duringTheFinalTextRowCallbackCannotReturnACompletedRead()
            throws Exception {
        var service = service();
        var tables = Set.of("demographic");
        var captured = service.capture(lease, patientScope, tables, 1000, 8L << 20, new Control());
        var callbacks = new AtomicInteger();
        assertThatThrownBy(
                        () ->
                                new CdsExportCapturedTextRowsReader(service)
                                        .forEach(
                                                lease,
                                                patientScope,
                                                tables,
                                                "demographic",
                                                captured.manifest(),
                                                1000,
                                                8L << 20,
                                                16384,
                                                new Control(),
                                                row -> {
                                                    callbacks.incrementAndGet();
                                                    source.denyScopeAt = source.scopeChecks + 1;
                                                }))
                .hasMessage("CDS_EXPORT_PREVENTION_PROGRAM_SCOPE");
        assertThat(callbacks.get()).isEqualTo(1);
    }

    @Test
    void shouldVerifyARevocation_duringTheFinalRowReceiptCallbackCannotReturnACompletedScan()
            throws Exception {
        source.receipts = true;
        var service = service();
        var tables = Set.of("demographic");
        var captured = service.capture(lease, patientScope, tables, 1000, 8L << 20, new Control());
        var callbacks = new AtomicInteger();
        assertThatThrownBy(
                        () ->
                                new CdsExportNativeRowReceiptReader(service)
                                        .scan(
                                                lease,
                                                patientScope,
                                                tables,
                                                captured,
                                                1000,
                                                8L << 20,
                                                new Control(),
                                                row -> {
                                                    callbacks.incrementAndGet();
                                                    source.denyScopeAt = source.scopeChecks + 1;
                                                }))
                .hasMessage("CDS_EXPORT_PREVENTION_PROGRAM_SCOPE");
        assertThat(callbacks.get()).isEqualTo(1);
    }

    private static CdsExportNativeSourceReader laboratoryScope(
            CdsExportNativeSourceReader reader, boolean sensitive) {
        return new CdsExportNativeSourceReader() {
            @Override
            public String table() {
                return reader.table();
            }

            @Override
            public Set<String> columns() {
                return reader.columns();
            }

            @Override
            public Set<CdsCategory> categories() {
                return Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.LABORATORY_RESULTS);
            }

            @Override
            public boolean requiresSensitiveLaboratoryRead() {
                return sensitive;
            }

            @Override
            public CdsExportTableSchemaDto schema() {
                return reader.schema();
            }

            @Override
            public long count(int patient) {
                return reader.count(patient);
            }

            @Override
            public void capture(int patient, CdsExportNativeCaptureComposer.Writer writer)
                    throws IOException {
                reader.capture(patient, writer);
            }
        };
    }

    private CdsExportNativeCaptureService service() {
        return new CdsExportNativeCaptureService(List.of(source), factory, clock, transactions);
    }

    @Test
    void shouldApplyTheConfiguredFiniteCaptureTimeout_toTheActualPatientTransaction()
            throws Exception {
        @SuppressWarnings("unchecked")
        var installed =
                (org.springframework.beans.factory.ObjectProvider<CdsExportNativeSourceReader>)
                        mock(org.springframework.beans.factory.ObjectProvider.class);
        when(installed.orderedStream()).thenAnswer(ignored -> java.util.stream.Stream.of(source));
        var settings = new java.util.Properties();
        settings.setProperty("cds.export.captureTimeoutSeconds", "600");
        var manager = spy(transactions);
        var observed = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(
                        call -> {
                            observed.set(
                                    ((org.springframework.transaction.TransactionDefinition)
                                                    call.getArgument(0))
                                            .getTimeout());
                            return call.callRealMethod();
                        })
                .when(manager)
                .getTransaction(any());
        var service =
                new CdsExportNativeCaptureService(
                        installed, factory, clock, manager, null, settings);
        service.capture(lease, patientScope, Set.of("demographic"), 1000, 8L << 20, new Control());
        assertThat(observed.get()).isEqualTo(600);
        assertThat(transactions.getDefaultTimeout())
                .isEqualTo(org.springframework.transaction.TransactionDefinition.TIMEOUT_DEFAULT);
    }

    private void assertNoPatientMembers() {
        try (var files = Files.list(root)) {
            assertThat(files.collect(java.util.stream.Collectors.toSet())).isEqualTo(cohortMembers);
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static class Control implements CdsExportRunControl {
        final AtomicInteger authorizations = new AtomicInteger();
        int refuseAt;
        boolean cancelDuringCapture;

        @Override
        public void checkpoint() {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        }

        @Override
        public void captureCheckpoint() {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            if (cancelDuringCapture) throw new IllegalStateException("CANCELLED");
        }

        @Override
        public void authorizePatient(int patient) {
            assertThat(patient).isEqualTo(PATIENT);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if (authorizations.incrementAndGet() == refuseAt)
                throw new SecurityException("ACCESS_REVOKED");
        }

        @Override
        public void authorizeCategory(int patient, CdsCategory category, boolean sensitive) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(patient).isEqualTo(PATIENT);
            assertThat(category).isEqualTo(CdsCategory.DEMOGRAPHICS);
            assertThat(sensitive).isFalse();
        }
    }

    private static final class RetainedRows implements CdsExportNativeSourceReader {
        private final io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan
                plan;
        private boolean fail;

        private RetainedRows(
                io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan plan) {
            this.plan = plan;
        }

        @Override
        public String table() {
            return plan.table();
        }

        @Override
        public Set<String> columns() {
            return Set.copyOf(plan.columns());
        }

        @Override
        public Set<CdsCategory> categories() {
            return Set.of(CdsCategory.DEMOGRAPHICS);
        }

        @Override
        public CdsExportTableSchemaDto schema() {
            if (fail) throw new AssertionError("CURRENT_SCHEMA_REREAD");
            try (var input =
                    getClass()
                            .getResourceAsStream(
                                    "/cds-export/source-schema/" + table() + ".json")) {
                return new ObjectMapper().readValue(input, CdsExportTableSchemaDto.class);
            } catch (IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        }

        @Override
        public long count(int patient) {
            if (fail) throw new AssertionError("CURRENT_COUNT_REREAD");
            return 1;
        }

        @Override
        public void capture(int patient, CdsExportNativeCaptureComposer.Writer output)
                throws IOException {
            if (fail) throw new AssertionError("CURRENT_VALUES_REREAD");
            var values = new LinkedHashMap<String, String>();
            columns().forEach(column -> values.put(column, null));
            values.put("id", "a".repeat(64));
            output.row("a".repeat(64), values);
        }
    }

    private static final class ExtensionRows implements CdsExportNativeSourceReader {
        private boolean fail, receipts;

        @Override
        public String table() {
            return "demographicExt";
        }

        @Override
        public Set<String> columns() {
            return Set.copyOf(
                    io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan
                            .DEMOGRAPHIC_EXT
                            .columns());
        }

        @Override
        public Set<CdsCategory> categories() {
            return Set.of(CdsCategory.DEMOGRAPHICS);
        }

        @Override
        public CdsExportTableSchemaDto schema() {
            try (var input =
                    getClass()
                            .getResourceAsStream("/cds-export/source-schema/demographicExt.json")) {
                return JSON.readValue(input, CdsExportTableSchemaDto.class);
            } catch (IOException invalid) {
                throw new java.io.UncheckedIOException(invalid);
            }
        }

        @Override
        public java.util.List<
                        io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest
                                .Column>
                receiptColumns() {
            if (fail) throw new AssertionError("CURRENT_RECEIPT_METADATA_REREAD");
            return receipts
                    ? columns().stream()
                            .filter(name -> !name.equals("dashboard_single_value_key"))
                            .sorted()
                            .map(
                                    name ->
                                            new io.github.carlos_emr.carlos.demographic.cds.contract
                                                    .CdsNativeRowDigest.Column(
                                                    name, "VARCHAR", 255, 0))
                            .toList()
                    : List.of();
        }

        @Override
        public long count(int patient) {
            return 2;
        }

        @Override
        public void capture(int patient, CdsExportNativeCaptureComposer.Writer writer)
                throws IOException {
            if (fail) throw new IOException("MUTABLE_SOURCE_REQUERIED");
            for (long id : new long[] {-1, 1}) {
                var row = new LinkedHashMap<String, String>();
                columns().forEach(column -> row.put(column, null));
                row.put("id", Long.toString(id));
                row.put("demographic_no", Integer.toString(patient));
                row.put("date_time", "2026-11-05 10:00:00");
                row.put("key_val", "synthetic");
                row.put("dashboard_single_value_key", "synthetic");
                row.put("value", id == -1 ? "" : null);
                writer.row(CdsExportPatientTableSourceReader.key(id), row);
            }
        }
    }

    private static final class ProviderRows implements CdsExportNativeSourceReader {
        private boolean fail;

        @Override
        public String table() {
            return "provider";
        }

        @Override
        public Set<String> columns() {
            return io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto.COLUMNS;
        }

        @Override
        public Set<CdsCategory> categories() {
            return Set.of(CdsCategory.DEMOGRAPHICS);
        }

        @Override
        public CdsExportTableSchemaDto schema() {
            if (fail) throw new AssertionError("CURRENT_PROVIDER_SCHEMA_REREAD");
            try (var input =
                    getClass().getResourceAsStream("/cds-export/source-schema/provider.json")) {
                return JSON.readValue(input, CdsExportTableSchemaDto.class);
            } catch (IOException error) {
                throw new java.io.UncheckedIOException(error);
            }
        }

        @Override
        public long count(int patient) {
            if (fail) throw new AssertionError("CURRENT_PROVIDER_COUNT_REREAD");
            return 2;
        }

        @Override
        public void capture(int patient, CdsExportNativeCaptureComposer.Writer output)
                throws IOException {
            if (fail) throw new AssertionError("CURRENT_PROVIDER_VALUES_REREAD");
            for (String id : List.of("0é27", "ZZ9999")) {
                var fields = new LinkedHashMap<String, String>();
                columns().forEach(name -> fields.put(name, null));
                fields.putAll(
                        Map.of(
                                "provider_no",
                                id,
                                "first_name",
                                id.equals("0é27") ? "Captured physician" : "Unrelated physician",
                                "last_name",
                                "Synthetic",
                                "ohip_no",
                                "001234",
                                "practitionerNoType",
                                "CPSO",
                                "practitionerNo",
                                "000123"));
                output.row(CdsExportPublicProviderSourceReader.key(id), fields);
            }
        }
    }

    private static final class PatientNoteRows implements CdsExportNativeSourceReader {
        String text =
                io.github.carlos_emr.carlos.commn.model.DemographicCust.wrapPatientNotes(
                        "Original café 😀 with literal </unotes> text");
        boolean fail;
        long count = 1;

        @Override
        public String table() {
            return "demographiccust";
        }

        @Override
        public Set<String> columns() {
            return Set.of("content", "cust1", "cust2", "cust4", "demographic_no");
        }

        @Override
        public Set<CdsCategory> categories() {
            return Set.of(CdsCategory.DEMOGRAPHICS);
        }

        @Override
        public CdsExportTableSchemaDto schema() {
            if (fail) throw new AssertionError("CURRENT_NOTE_SCHEMA_REREAD");
            try (var input =
                    getClass()
                            .getResourceAsStream(
                                    "/cds-export/source-schema/demographiccust.json")) {
                return JSON.readValue(input, CdsExportTableSchemaDto.class);
            } catch (IOException failed) {
                throw new java.io.UncheckedIOException(failed);
            }
        }

        @Override
        public long count(int patient) {
            if (fail) throw new AssertionError("CURRENT_NOTE_COUNT_REREAD");
            return count;
        }

        @Override
        public void capture(int patient, CdsExportNativeCaptureComposer.Writer writer)
                throws IOException {
            if (fail) throw new AssertionError("CURRENT_NOTE_VALUE_REREAD");
            for (int i = 0; i < count; i++) {
                var values = new LinkedHashMap<String, String>();
                for (String column : columns()) values.put(column, null);
                values.put("demographic_no", Integer.toString(patient + i));
                values.put("content", text);
                writer.row(CdsExportPatientTableSourceReader.key(patient + i), values);
            }
        }
    }

    private static final class FixtureReader implements CdsExportNativeSourceReader {
        int preparations;
        CdsExportNativeSourceReader preparedReader;

        @Override
        public CdsExportNativeSourceReader forCapture(int patient, CdsExportRunControl control) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            preparations++;
            return preparedReader == null ? this : preparedReader;
        }

        Set<String> dependencies = Set.of();

        @Override
        public Set<String> requiredTables() {
            return dependencies;
        }

        int scopeChecks, denyScopeAt;
        String declaredTable = "demographic";

        @Override
        public void authorizeReadScope(int patient, CdsExportRunControl control) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(patient).isEqualTo(PATIENT);
            if (++scopeChecks == denyScopeAt)
                throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                        .CdsExportReviewException(
                        io.github.carlos_emr.carlos.demographic.cdsexport.support
                                .CdsExportReviewException.Reason.PREVENTION_PROGRAM_SCOPE);
            control.checkpoint();
        }

        int captureTransactions;
        int capturedPatient = PATIENT;
        long count = 1;
        boolean fail, omit, receipts, dropReceiptEmail;
        String primaryProvider;
        String engine = "InnoDB";

        @Override
        public String table() {
            return declaredTable;
        }

        @Override
        public Set<CdsCategory> categories() {
            return Set.of(CdsCategory.DEMOGRAPHICS);
        }

        @Override
        public Set<String> columns() {
            var names =
                    new java.util.HashSet<>(
                            io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto
                                    .COLUMNS);
            names.add("demographic_no");
            return Set.copyOf(names);
        }

        @Override
        public java.util.List<
                        io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest
                                .Column>
                receiptColumns() {
            // Deliberately synthetic driver recipe. Actual Connector/J metadata is checked by the
            // native source suite.
            if (fail && receipts) throw new AssertionError("CURRENT_RECEIPT_METADATA_REREAD");
            return receipts
                    ? columns().stream()
                            .filter(name -> !dropReceiptEmail || !name.equals("email"))
                            .sorted(java.util.Comparator.reverseOrder())
                            .map(
                                    name ->
                                            new io.github.carlos_emr.carlos.demographic.cds.contract
                                                    .CdsNativeRowDigest.Column(
                                                    name, "VARCHAR", 255, 0))
                            .toList()
                    : List.of();
        }

        @Override
        public CdsExportTableSchemaDto schema() {
            inSnapshot();
            try (var input =
                    getClass().getResourceAsStream("/cds-export/source-schema/demographic.json")) {
                var expected = JSON.readValue(input, CdsExportTableSchemaDto.class);
                return new CdsExportTableSchemaDto(
                        expected.table(), engine, expected.columns(), expected.primaryKeyColumns());
            } catch (IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        }

        @Override
        public long count(int patient) {
            inSnapshot();
            return count;
        }

        @Override
        public void capture(int patient, CdsExportNativeCaptureComposer.Writer writer)
                throws IOException {
            inSnapshot();
            if (fail) throw new IOException("SOURCE_FAILED");
            if (omit) return;
            var fields = new LinkedHashMap<String, String>();
            for (String column : columns()) fields.put(column, null);
            fields.put("demographic_no", Integer.toString(capturedPatient));
            fields.put("first_name", "FAKE-CDS 😀");
            fields.put("email", "");
            fields.put("provider_no", primaryProvider);
            writer.row("0000000027", fields);
        }

        private void inSnapshot() {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                    .isEqualTo(
                            org.springframework.transaction.TransactionDefinition
                                    .ISOLATION_REPEATABLE_READ);
            captureTransactions++;
        }
    }
}
