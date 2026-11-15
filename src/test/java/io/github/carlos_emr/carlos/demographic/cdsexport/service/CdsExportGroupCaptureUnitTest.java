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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.CdsExportRunDao;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDocumentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportSelectionDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStorageBindingDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * A patient file of charts linked by Merge Records is captured chart by chart, as each would be
 * alone, in one read-only snapshot; a lost snapshot reads the whole group again; and the file's
 * fingerprint is a single chart's own fingerprint unchanged.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportGroupCaptureUnitTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int HEAD = 27, MERGED = 34;

    private final String run = UUID.randomUUID().toString();
    private Path base, root;
    private CdsExportStagingFactory factory;
    private CdsExportStagingService store;
    private CdsExportLeaseDto lease;
    private CdsExportRunDao clock;
    private JdbcDataSource data;
    private GroupReader source;
    private CdsExportCohortService.Cursor cursor;
    private CdsExportCohortService.Patient head;

    @BeforeEach
    void sealOneGroup() throws Exception {
        // The store refuses an ancestor another user could write to, such as a shared temporary
        // directory: a private root (cds.export.test.documentRoot), else the process home.
        base =
                Files.createTempDirectory(
                        Path.of(
                                        System.getProperty(
                                                "cds.export.test.documentRoot",
                                                System.getProperty("user.home")))
                                .toAbsolutePath(),
                        "cds-export-group-",
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rwx------")));
        root = Files.createDirectory(base.resolve("store"));
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        data = new JdbcDataSource();
        data.setURL("jdbc:h2:mem:group_" + run);
        source = new GroupReader();
        factory = mock(CdsExportStagingFactory.class);
        clock = mock(CdsExportRunDao.class);
        when(clock.now()).thenAnswer(ignored -> Instant.now());
        // The job named the merged chart only; its head comes with it.
        var command =
                new CdsExportCommand(
                        "group-capture",
                        "999998",
                        null,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(MERGED),
                        Set.of(CdsCategory.DEMOGRAPHICS),
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
                                            0,
                                            call.getArgument(3));
                            return store;
                        });
        var ledger = mock(CdsExportLedgerService.class);
        var cohorts = new CdsExportCohortService(factory, ledger);
        var control = new Control();
        store = factory.openExisting(run, 1, storage, control::checkpoint);
        var inventory =
                store.stage(
                        "PATIENT_SELECTION",
                        output ->
                                output.write(
                                        ("{\"demographicNo\":" + MERGED + ",\"physicianNo\":null}\n")
                                                .getBytes(StandardCharsets.UTF_8)));
        var groups =
                store.stage(
                        "COHORT_GROUPS",
                        output ->
                                output.write(
                                        ("{\"head\":"
                                                        + HEAD
                                                        + ",\"physicianNo\":\"100\",\"members\":["
                                                        + MERGED
                                                        + "],\"selected\":["
                                                        + MERGED
                                                        + "],\"links\":[5],\"shape\":[]}\n")
                                                .getBytes(StandardCharsets.UTF_8)));
        var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var sealed =
                cohorts.prepare(
                        lease, new CdsExportSelectionDto(now, now, 1, inventory, 1L, groups), control);
        when(ledger.cohort(lease)).thenReturn(sealed.member());
        cursor = cohorts.open(sealed, control);
        head = cursor.next();
    }

    @AfterEach
    void eraseStore() throws IOException {
        if (cursor != null) cursor.close();
        if (base != null && Files.exists(base))
            try (var files = Files.walk(base)) {
                for (var path : files.sorted(Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(path);
            }
    }

    @Test
    void shouldCaptureEveryChartOfAGroup_inOneSnapshotWithItsOwnMembers() throws Exception {
        var counting = new CountingTransactions(data, 0, new int[1]);
        var service = new CdsExportNativeCaptureService(List.of(source), factory, clock, counting);

        var group =
                service.captureGroup(
                        lease, head, Set.of("demographic"), 1000, 8L << 20, new Control());

        // One snapshot read both charts, the head first; each chart's own transaction state
        // ended once it was read, before the next chart.
        assertThat(counting.begun).isEqualTo(1);
        assertThat(source.captured).containsExactly(HEAD, MERGED);
        assertThat(source.ended).containsExactly(HEAD, MERGED);
        assertThat(source.transactions).hasSize(1);
        assertThat(group.charts())
                .extracting(chart -> chart.capture().patient())
                .containsExactly(HEAD, MERGED);
        // Each chart has its own members and manifest.
        assertThat(group.charts().get(0).capture().values())
                .isNotEqualTo(group.charts().get(1).capture().values());
        try (var input = store.open(group.manifest())) {
            var manifest = JSON.readTree(input.readAllBytes());
            assertThat(manifest.path("head").intValue()).isEqualTo(HEAD);
            assertThat(manifest.path("charts")).hasSize(2);
            assertThat(manifest.path("charts").get(1).path("patient").intValue())
                    .isEqualTo(MERGED);
            assertThat(manifest.path("charts").get(1).path("fingerprint").textValue())
                    .isEqualTo(
                            CdsExportChartFingerprints.capture(
                                    group.charts().get(1).capture()));
            assertThat(manifest.path("planSha256").textValue()).matches("[0-9a-f]{64}");
        }
        // A chart alone keeps its own fingerprint exactly; a group's names every chart.
        var captures = group.charts().stream().map(chart -> chart.capture()).toList();
        assertThat(CdsExportChartFingerprints.group(List.of(captures.getFirst())))
                .isEqualTo(CdsExportChartFingerprints.capture(captures.getFirst()));
        String fingerprint = CdsExportChartFingerprints.group(captures);
        assertThat(fingerprint)
                .isNotEqualTo(CdsExportChartFingerprints.capture(captures.getFirst()));
        // A merged chart's changed record changes the file's fingerprint.
        source.names.put(MERGED, "FAKE-CDS changed");
        var again =
                service.captureGroup(
                        lease, head, Set.of("demographic"), 1000, 8L << 20, new Control());
        assertThat(
                        CdsExportChartFingerprints.group(
                                again.charts().stream().map(chart -> chart.capture()).toList()))
                .isNotEqualTo(fingerprint);
    }

    @Test
    void shouldReadTheWholeGroupAgain_whenItsSnapshotsConnectionIsLost() throws Exception {
        var begun = new int[1];
        var service =
                new CdsExportNativeCaptureService(
                        List.of(source), factory, clock, new CountingTransactions(data, 1, begun));
        var waited = new ArrayList<Long>();
        service.pause = waited::add;
        long before;
        try (var files = Files.list(root)) {
            before = files.count();
        }

        var group =
                service.captureGroup(
                        lease, head, Set.of("demographic"), 1000, 8L << 20, new Control());

        // The first reading of the group lost its connection; the second is complete, and both
        // charts were read again in it.
        assertThat(begun[0]).isEqualTo(2);
        assertThat(waited).containsExactly(1_000L);
        assertThat(source.captured).containsExactly(HEAD, MERGED, HEAD, MERGED);
        assertThat(group.charts()).hasSize(2);
        // Nothing of the lost reading is left: two charts of three members each, and the group
        // manifest.
        try (var files = Files.list(root)) {
            assertThat(files.count() - before).isEqualTo(7);
        }
    }

    /** Counts the snapshots begun; the first {@code losses} commits lose their connection. */
    private static final class CountingTransactions extends DataSourceTransactionManager {
        private final int losses;
        private final int[] committed;
        int begun;

        CountingTransactions(JdbcDataSource data, int losses, int[] committed) {
            super(data);
            this.losses = losses;
            this.committed = committed;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            super.doBegin(transaction, definition);
            begun++;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            super.doCommit(status);
            if (committed[0]++ < losses)
                throw new TransactionSystemException(
                        "SOURCE_CONNECTION_LOST",
                        new java.sql.SQLNonTransientConnectionException(
                                "Socket timout error", "08000"));
        }
    }

    /** One demographic row per chart, read only inside a read-only repeatable-read snapshot. */
    private static final class GroupReader implements CdsExportNativeSourceReader {
        final List<Integer> captured = new ArrayList<>();
        final List<Integer> ended = new ArrayList<>();
        final Set<Object> transactions = new java.util.HashSet<>();
        final java.util.Map<Integer, String> names =
                new java.util.HashMap<>(java.util.Map.of(HEAD, "FAKE-CDS Head", MERGED, "FAKE-CDS Merged"));

        @Override
        public String table() {
            return "demographic";
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
        public CdsExportTableSchemaDto schema() {
            inSnapshot();
            try (var input =
                    getClass().getResourceAsStream("/cds-export/source-schema/demographic.json")) {
                return JSON.readValue(input, CdsExportTableSchemaDto.class);
            } catch (IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        }

        @Override
        public long count(int patient) {
            inSnapshot();
            return 1;
        }

        @Override
        public void capture(int patient, CdsExportNativeCaptureComposer.Writer writer)
                throws IOException {
            inSnapshot();
            captured.add(patient);
            var fields = new LinkedHashMap<String, String>();
            for (String column : columns()) fields.put(column, null);
            fields.put("demographic_no", Integer.toString(patient));
            fields.put("first_name", names.get(patient));
            writer.row(CdsExportPatientTableSourceReader.key(patient), fields);
        }

        @Override
        public void endChart(int patient) {
            inSnapshot();
            // Ended right after its own rows, never before them or for another chart.
            assertThat(captured).last().isEqualTo(patient);
            ended.add(patient);
        }

        private void inSnapshot() {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                    .isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            transactions.add(TransactionSynchronizationManager.getResourceMap().values().iterator().next());
        }
    }

    /** Grants every check, outside the snapshot only. */
    private static final class Control implements CdsExportRunControl {
        @Override
        public void checkpoint() {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        }

        @Override
        public void captureCheckpoint() {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        }

        @Override
        public void authorizePatient(int patient) {
            assertThat(patient).isIn(HEAD, MERGED);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        }

        @Override
        public void authorizeCategory(int patient, CdsCategory category, boolean sensitive) {
            assertThat(patient).isIn(HEAD, MERGED);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        }
    }
}
