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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import jakarta.persistence.EntityManagerFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

/** Actual transaction lifecycle/config bytes with bounded DAO projections; no MariaDB or clinical authorization is mocked as a permission proof. */
class CdsExportPreventionSnapshotServiceUnitTest {
    @TempDir Path root;
    private final EntityManagerFactory factory = mock(EntityManagerFactory.class);
    private final CVCImmunizationDao vaccines = mock(CVCImmunizationDao.class);
    private final CVCMappingDao mappings = mock(CVCMappingDao.class);
    private final PreventionDao preventions = mock(PreventionDao.class);
    private CdsExportPreventionSnapshotService snapshots;
    private Path file;
    private TransactionTemplate read;
    private EntityManagerHolder holder;
    private static final String XML =
            "<preventions><item name='Td' layout='injection' healthCanadaType='Td'/><item name='PAP' layout='PAPMAM' headingName='Screenings'/></preventions>";

    @BeforeEach
    void prepare() throws Exception {
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        file = Files.writeString(root.resolve("preventions.xml"), XML);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        var data = new JdbcDataSource();
        data.setURL("jdbc:h2:mem:snapshot_" + UUID.randomUUID());
        read = new TransactionTemplate(new DataSourceTransactionManager(data));
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        holder = new EntityManagerHolder(mock(jakarta.persistence.EntityManager.class));
        holder.setSynchronizedWithTransaction(true);
        when(preventions.readCdsExportSourceSchema()).thenReturn(schema("preventions_scope"));
        when(vaccines.readCdsExportSourceSchema()).thenReturn(schema("CVCImmunization"));
        when(mappings.readCdsExportSourceSchema()).thenReturn(schema("CVCMapping"));
        when(vaccines.findCdsExportRowsAfter(eq(27), any(), eq(32))).thenReturn(List.of());
        when(mappings.findCdsExportRowsAfter(eq(27), any(), eq(32))).thenReturn(List.of());
        when(preventions.countCdsExportRows(27)).thenReturn(2L);
        when(preventions.findCdsExportRowsAfter(eq(27), any(), eq(32)))
                .thenReturn(List.of(raw(1, "Td"), raw(2, "PAP")));
        snapshots =
                new CdsExportPreventionSnapshotService(
                        factory,
                        new CdsExportPreventionConfigurationLoader(file.toString()),
                        vaccines,
                        mappings,
                        preventions);
    }

    @AfterEach
    void cleanup() {
        TransactionSynchronizationManager.unbindResourceIfPossible(factory);
    }

    private <T> T transaction(java.util.concurrent.Callable<T> body) {
        return read.execute(
                status -> {
                    TransactionSynchronizationManager.bindResource(factory, holder);
                    try {
                        return body.call();
                    } catch (RuntimeException | Error failed) {
                        throw failed;
                    } catch (Exception failed) {
                        throw new java.io.UncheckedIOException(
                                failed instanceof IOException io ? io : new IOException(failed));
                    } finally {
                        TransactionSynchronizationManager.unbindResourceIfPossible(factory);
                    }
                });
    }

    @Test
    void configurationAndSelectionAreFixedWithinTheOwningSnapshotAndReleasedAtCompletion()
            throws Exception {
        final CdsExportPreventionSnapshotService.State[] saved = {null};
        var probes = new AtomicInteger();
        transaction(
                () -> {
                    var state = snapshots.prepare(27, probes::incrementAndGet);
                    saved[0] = state;
                    assertThat(state.selection().immunizations()).containsExactly(1);
                    assertThat(state.selection().otherPreventions()).containsExactly(2);
                    Files.writeString(
                            file,
                            "<preventions><item name='Td' layout='PAPMAM'/><item name='PAP' layout='injection' healthCanadaType='Td'/></preventions>");
                    assertThat(snapshots.prepare(27, probes::incrementAndGet)).isSameAs(state);
                    assertThat(new String(state.xml().readAllBytes(), StandardCharsets.UTF_8))
                            .isEqualTo(XML);
                    return null;
                });
        assertThat(probes.get()).isGreaterThanOrEqualTo(4);
        assertThatThrownBy(() -> saved[0].selection())
                .hasMessage("CDS_EXPORT_PREVENTION_SNAPSHOT_SCOPE_INVALID");
        assertThatThrownBy(() -> snapshots.current(27))
                .hasMessage("CDS_EXPORT_PREVENTION_SNAPSHOT_REQUIRED");
        transaction(
                () -> {
                    assertThat(snapshots.prepare(27, () -> {}).selection().immunizations())
                            .containsExactly(2);
                    return null;
                });
    }

    @Test
    void configurationStreamsCannotEscapeTheirTransactionOrOwnerThread() {
        final InputStream[] saved = {null};
        transaction(
                () -> {
                    saved[0] = snapshots.prepare(27, () -> {}).xml();
                    var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
                    var thread =
                            new Thread(
                                    () -> {
                                        try {
                                            saved[0].read();
                                        } catch (Throwable failed) {
                                            failure.set(failed);
                                        }
                                    });
                    thread.start();
                    thread.join(5000);
                    assertThat(thread.isAlive()).isFalse();
                    assertThat(failure.get())
                            .hasMessage("CDS_EXPORT_PREVENTION_SNAPSHOT_SCOPE_INVALID");
                    return null;
                });
        assertThatThrownBy(() -> saved[0].read())
                .hasMessage("CDS_EXPORT_PREVENTION_SNAPSHOT_SCOPE_INVALID");
    }

    @Test
    void aDifferentPatientThreadOrJpaTransactionCannotReuseTheState() {
        transaction(
                () -> {
                    var state = snapshots.prepare(27, () -> {});
                    assertThatThrownBy(() -> snapshots.current(28))
                            .hasMessage("CDS_EXPORT_PREVENTION_SNAPSHOT_SCOPE_INVALID");
                    TransactionSynchronizationManager.unbindResource(factory);
                    var otherHolder =
                            new EntityManagerHolder(mock(jakarta.persistence.EntityManager.class));
                    otherHolder.setSynchronizedWithTransaction(true);
                    TransactionSynchronizationManager.bindResource(factory, otherHolder);
                    assertThatThrownBy(() -> state.selection())
                            .hasMessage("CDS_EXPORT_PREVENTION_SNAPSHOT_SCOPE_INVALID");
                    TransactionSynchronizationManager.unbindResource(factory);
                    TransactionSynchronizationManager.bindResource(factory, holder);
                    var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
                    var other =
                            new Thread(
                                    () -> {
                                        try {
                                            state.selection();
                                        } catch (Throwable failed) {
                                            failure.set(failed);
                                        }
                                    });
                    other.start();
                    other.join(5000);
                    assertThat(other.isAlive()).isFalse();
                    assertThat(failure.get())
                            .hasMessage("CDS_EXPORT_PREVENTION_SNAPSHOT_SCOPE_INVALID");
                    return null;
                });
    }

    @Test
    void noStateCanBeCreatedOutsideAnOwningReadonlyRepeatableReadJpaTransaction() {
        assertThatThrownBy(() -> snapshots.prepare(27, () -> {}))
                .hasMessage("CDS_EXPORT_PREVENTION_SNAPSHOT_REQUIRED");
        read.setReadOnly(false);
        assertThatThrownBy(() -> transaction(() -> snapshots.prepare(27, () -> {})))
                .hasMessage("CDS_EXPORT_PREVENTION_SNAPSHOT_REQUIRED");
    }

    @Test
    void readCommittedOrAnUnjoinedJpaHolderCannotStandInForTheOwnedSnapshot() {
        read.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        assertThatThrownBy(() -> transaction(() -> snapshots.prepare(27, () -> {})))
                .hasMessage("CDS_EXPORT_PREVENTION_SNAPSHOT_REQUIRED");
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        holder.setSynchronizedWithTransaction(false);
        assertThatThrownBy(() -> transaction(() -> snapshots.prepare(27, () -> {})))
                .hasMessage("CDS_EXPORT_PREVENTION_SNAPSHOT_REQUIRED");
    }

    @Test
    void programRestrictionsAreCheckedBeforeConfigurationOrTypeRowsAreRead() throws Exception {
        Files.delete(file);
        when(preventions.hasCdsExportUnresolvedProgramScope(27)).thenReturn(true);
        assertThatThrownBy(() -> transaction(() -> snapshots.prepare(27, () -> {})))
                .hasMessage("CDS_EXPORT_PREVENTION_PROGRAM_SCOPE");
        verify(preventions, never()).findCdsExportRowsAfter(anyInt(), any(), anyInt());
        verify(vaccines, never()).findCdsExportRowsAfter(anyInt(), any(), anyInt());
    }

    @Test
    void cancellationDuringDiscoveryCannotLeaveAUsableSnapshot() {
        var calls = new AtomicInteger();
        assertThatThrownBy(
                        () ->
                                transaction(
                                        () ->
                                                snapshots.prepare(
                                                        27,
                                                        () -> {
                                                            if (calls.incrementAndGet() == 2)
                                                                throw new java.util.concurrent
                                                                        .CancellationException(
                                                                        "CANCELLED_DISCOVERY");
                                                        })))
                .hasMessage("CANCELLED_DISCOVERY");
        assertThatThrownBy(() -> snapshots.current(27))
                .hasMessage("CDS_EXPORT_PREVENTION_SNAPSHOT_REQUIRED");
    }

    @Test
    void unknownTypesRemainAReviewPartitionAndCannotSupplyClinicalIds() {
        when(preventions.findCdsExportRowsAfter(eq(27), any(), eq(32)))
                .thenReturn(List.of(raw(1, "UNCONFIGURED"), raw(2, "PAP")));
        transaction(
                () -> {
                    var state = snapshots.prepare(27, () -> {});
                    assertThat(state.selection().reviews())
                            .containsEntry(1, CdsExportPreventionScopePlanner.Review.UNKNOWN_TYPE);
                    assertThatThrownBy(state::clinicalIds).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
                    return null;
                });
    }

    @Test
    void countMismatchesAndCorruptInlineHashCannotSilentlySelectAPrefix() {
        when(preventions.countCdsExportRows(27)).thenReturn(3L);
        assertThatThrownBy(() -> transaction(() -> snapshots.prepare(27, () -> {})))
                .hasRootCauseMessage("CDS_EXPORT_PREVENTION_SELECTION_INVALID");
        when(preventions.countCdsExportRows(27)).thenReturn(2L);
        var first = raw(1, "Td");
        var cells = new TreeMap<>(first.cells());
        byte[] changed = "XX".getBytes(StandardCharsets.UTF_8);
        cells.put("prevention_type", new CdsExportRawCellDto(false, 2, "a".repeat(64), changed));
        when(preventions.findCdsExportRowsAfter(eq(27), any(), eq(32)))
                .thenReturn(List.of(new CdsExportRawRowDto(1, cells), raw(2, "PAP")));
        assertThatThrownBy(() -> transaction(() -> snapshots.prepare(27, () -> {})))
                .isInstanceOf(java.io.UncheckedIOException.class);
    }

    @Test
    void unboundDefinitionsCannotMasqueradeAsCapturedSourceEvidence() throws Exception {
        var value =
                CdsExportPreventionClassifier.forSelection(
                        XML.getBytes(StandardCharsets.UTF_8), List.of());
        assertThat(value.classify("Td", null).kind())
                .isEqualTo(CdsExportPreventionClassifier.Kind.IMMUNIZATION);
        assertThatThrownBy(value::sourceManifestSha256)
                .hasMessage("CDS_EXPORT_PREVENTION_CLASSIFIER_UNBOUND");
        assertThatThrownBy(() -> new CdsExportPreventionScopePlanner().plan(27, value, List.of()))
                .hasMessage("CDS_EXPORT_PREVENTION_CLASSIFIER_UNBOUND");
    }

    @Test
    void trustedLoaderPreservesBytesAndRejectsMissingOversizedAndLeafSymlinkFiles()
            throws Exception {
        assertThat(new CdsExportPreventionConfigurationLoader(file.toString()).load())
                .isEqualTo(XML.getBytes(StandardCharsets.UTF_8));
        Files.write(file, new byte[1_048_577]);
        assertThatThrownBy(() -> new CdsExportPreventionConfigurationLoader(file.toString()).load())
                .hasMessage("CDS_EXPORT_PREVENTION_CONFIG_UNAVAILABLE");
        Files.delete(file);
        Files.createSymbolicLink(file, Files.writeString(root.resolve("other.xml"), XML));
        assertThatThrownBy(() -> new CdsExportPreventionConfigurationLoader(file.toString()).load())
                .hasMessage("CDS_EXPORT_PREVENTION_CONFIG_UNAVAILABLE");
    }

    private static CdsExportTableSchemaDto schema(String name) throws IOException {
        try (var input =
                CdsExportPreventionSnapshotServiceUnitTest.class.getResourceAsStream(
                        "/cds-export/source-schema/" + name + ".json")) {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(input, CdsExportTableSchemaDto.class);
        }
    }

    private static CdsExportRawRowDto raw(int id, String type) {
        var values = new TreeMap<String, String>();
        values.put("id", Integer.toString(id));
        values.put("demographic_no", "27");
        values.put("prevention_type", type);
        values.put("programNo", null);
        values.put("restrictToProgram", null);
        values.put("snomedId", null);
        var cells = new TreeMap<String, CdsExportRawCellDto>();
        for (var entry : values.entrySet()) {
            byte[] bytes =
                    entry.getValue() == null
                            ? null
                            : entry.getValue().getBytes(StandardCharsets.UTF_8);
            cells.put(
                    entry.getKey(),
                    new CdsExportRawCellDto(
                            bytes == null,
                            bytes == null ? 0 : bytes.length,
                            bytes == null
                                    ? CdsExportRawCellDto.EMPTY_SHA256
                                    : java.util.HexFormat.of()
                                            .formatHex(
                                                    io.github.carlos_emr.carlos.utility
                                                            .CdsImportKeys.digest()
                                                            .digest(bytes)),
                            bytes));
        }
        return new CdsExportRawRowDto(id, cells);
    }
}
