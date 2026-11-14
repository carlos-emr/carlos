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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportChartCheckpointDto;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The finished-chart checkpoint of an export attempt: sealed in cohort order into a chain of
 * authenticated staged members, and trusted by a later attempt only from the pointer the ledger
 * recorded.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportChartCheckpointUnitTest {
    private static final String PARAMETERS = "a".repeat(64);
    @TempDir Path temporary;
    private Path root;
    private final String run = UUID.randomUUID().toString();
    private CdsExportStagingService store;

    @BeforeEach
    void privateStore() throws IOException {
        root = temporary.resolve("attempt");
        Files.createDirectory(root);
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        root = root.toRealPath();
        store = store(1);
    }

    private CdsExportStagingService store(long epoch) {
        return new CdsExportStagingService(
                root, run, epoch, new SecretKeySpec(new byte[32], "AES"), 0L, () -> {});
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode chart(int patient) {
        var node = CdsExportChartCheckpointReader.JSON.createObjectNode();
        node.put("patient", patient);
        node.put("relative", "Physician/" + patient + ".xml");
        return node;
    }

    private static String fingerprint(int patient) {
        return String.format("%064x", patient);
    }

    private List<CdsExportChartCheckpointReader.Entry> entries(
            CdsExportChartCheckpointReader.Chain chain) throws IOException {
        var result = new ArrayList<CdsExportChartCheckpointReader.Entry>();
        try (var cursor = chain.cursor()) {
            CdsExportChartCheckpointReader.Entry entry;
            while ((entry = cursor.next()) != null) result.add(entry);
        }
        return result;
    }

    @Test
    void shouldReadBackEveryEntryInCohortOrder_fromTheRecordedPointer() throws Exception {
        var persister = new CdsExportChartCheckpointPersister(store, run, 1, PARAMETERS);
        persister.chart(3, true, fingerprint(3), chart(3));
        persister.held(7, "CDS_EXPORT_UNMAPPED_DATA");
        var first = persister.seal();
        persister.chart(9, false, fingerprint(9), chart(9));
        var second = persister.seal();

        assertThat(first.purpose()).isEqualTo(CdsExportChartCheckpointDto.PURPOSE);
        assertThat(persister.latest().memberId()).isEqualTo(second.memberId());
        assertThat(persister.latest().charts()).isEqualTo(2);
        var chain =
                CdsExportChartCheckpointReader.open(store, run, 1, persister.latest(), () -> {});
        assertThat(chain.charts()).isEqualTo(2);
        assertThat(chain.held()).isEqualTo(1);
        assertThat(chain.lastPatient()).isEqualTo(9);
        assertThat(chain.parameters()).isEqualTo(PARAMETERS);
        assertThat(chain.members()).containsExactly(first, second);
        var read = entries(chain);
        assertThat(read).extracting(CdsExportChartCheckpointReader.Entry::patient)
                .containsExactly(3, 7, 9);
        assertThat(read.get(0).chartEntry()).isTrue();
        assertThat(read.get(0).reusable()).isTrue();
        assertThat(read.get(0).fingerprint()).isEqualTo(fingerprint(3));
        assertThat(read.get(0).chart()).isEqualTo(chart(3));
        assertThat(read.get(1).chartEntry()).isFalse();
        assertThat(read.get(1).code()).isEqualTo("CDS_EXPORT_UNMAPPED_DATA");
        assertThat(read.get(2).reusable()).isFalse();
    }

    @Test
    void shouldSealNothing_whenNoChartOrHeldPatientIsWaiting() throws Exception {
        var persister = new CdsExportChartCheckpointPersister(store, run, 1, PARAMETERS);
        assertThat(persister.pending()).isFalse();
        assertThat(persister.seal()).isNull();
        assertThat(persister.latest()).isNull();
    }

    @Test
    void shouldRefuseAnEntryOutOfCohortOrder_beforeAnythingIsSealed() throws Exception {
        var persister = new CdsExportChartCheckpointPersister(store, run, 1, PARAMETERS);
        persister.chart(5, true, fingerprint(5), chart(5));
        persister.seal();
        assertThatThrownBy(() -> persister.held(5, "CDS_EXPORT_UNMAPPED_DATA"))
                .hasMessage("CDS_EXPORT_CHART_CHECKPOINT_INVALID");
        assertThatThrownBy(() -> persister.chart(4, true, fingerprint(4), chart(4)))
                .hasMessage("CDS_EXPORT_CHART_CHECKPOINT_INVALID");
        // A chart record that names another patient is never sealed.
        assertThatThrownBy(() -> persister.chart(6, true, fingerprint(6), chart(8)))
                .hasMessage("CDS_EXPORT_CHART_CHECKPOINT_INVALID");
    }

    @Test
    void shouldRefuseAChain_whoseNewestMemberIsNotTheOneTheLedgerRecorded() throws Exception {
        var persister = new CdsExportChartCheckpointPersister(store, run, 1, PARAMETERS);
        persister.chart(3, true, fingerprint(3), chart(3));
        var first = persister.seal();
        persister.chart(4, true, fingerprint(4), chart(4));
        persister.seal();
        // An older member with the newest member's count is not the recorded checkpoint.
        var stale =
                new CdsExportChartCheckpointDto(
                        first.memberId(), first.plaintextSha256(), first.plaintextBytes(), 2);
        assertThatThrownBy(() -> CdsExportChartCheckpointReader.open(store, run, 1, stale, () -> {}))
                .hasMessage("CDS_EXPORT_CHART_CHECKPOINT_INVALID");
        // A pointer whose digest does not match the member is refused by the store itself.
        var forged =
                new CdsExportChartCheckpointDto(
                        persister.latest().memberId(),
                        "b".repeat(64),
                        persister.latest().bytes(),
                        2);
        assertThatThrownBy(() -> CdsExportChartCheckpointReader.open(store, run, 1, forged, () -> {}))
                .isInstanceOf(IOException.class)
                .hasMessage("CDS_EXPORT_STAGED_IDENTITY_MISMATCH");
    }

    @Test
    void shouldRefuseAMemberOfAnotherAttempt_evenUnderTheSameRun() throws Exception {
        var persister = new CdsExportChartCheckpointPersister(store, run, 1, PARAMETERS);
        persister.chart(3, true, fingerprint(3), chart(3));
        persister.seal();
        // The member's key and header are bound to run/epoch 1: read as epoch 2 it is refused.
        assertThatThrownBy(
                        () ->
                                CdsExportChartCheckpointReader.open(
                                        store(2), run, 2, persister.latest(), () -> {}))
                .isInstanceOf(IOException.class)
                .hasMessage("CDS_EXPORT_STAGED_SCOPE_MISMATCH");
    }

    @Test
    void shouldRefuseAForgedChain_whoseLinkOrCountsDoNotAgree() throws Exception {
        var persister = new CdsExportChartCheckpointPersister(store, run, 1, PARAMETERS);
        persister.chart(3, true, fingerprint(3), chart(3));
        var first = persister.seal();
        // A second member written by hand that counts a chart its predecessor does not have.
        String header =
                "{\"kind\":\"CHECKPOINT\",\"protocol\":1,\"runId\":\""
                        + run
                        + "\",\"epoch\":1,\"sequence\":2,\"parameters\":\""
                        + PARAMETERS
                        + "\",\"previous\":{\"memberId\":\""
                        + first.memberId()
                        + "\",\"sha256\":\""
                        + first.plaintextSha256()
                        + "\",\"bytes\":"
                        + first.plaintextBytes()
                        + "},\"charts\":5,\"held\":0,\"lastPatient\":3}\n";
        String entry =
                "{\"kind\":\"CHART\",\"patient\":4,\"reusable\":true,\"fingerprint\":\""
                        + fingerprint(4)
                        + "\",\"chart\":{\"patient\":4}}\n";
        String end = "{\"kind\":\"END\",\"entries\":1,\"charts\":6,\"held\":0,\"lastPatient\":4}\n";
        var forged =
                store.stage(
                        CdsExportChartCheckpointDto.PURPOSE,
                        output ->
                                output.write(
                                        (header + entry + end).getBytes(StandardCharsets.UTF_8)));
        var pointer =
                new CdsExportChartCheckpointDto(
                        forged.memberId(), forged.plaintextSha256(), forged.plaintextBytes(), 6);
        assertThatThrownBy(() -> CdsExportChartCheckpointReader.open(store, run, 1, pointer, () -> {}))
                .hasMessage("CDS_EXPORT_CHART_CHECKPOINT_INVALID");
    }

    @Test
    void shouldSealABatch_onceItReachesTheSpoolBatchSize() throws Exception {
        var persister = new CdsExportChartCheckpointPersister(store, run, 1, PARAMETERS);
        var large = chart(1);
        large.put("padding", "x".repeat(CdsExportSpoolService.BATCH_BYTES));
        persister.chart(1, true, fingerprint(1), large);
        assertThat(persister.full()).isTrue();
        persister.seal();
        assertThat(persister.full()).isFalse();
        assertThat(persister.pending()).isFalse();
    }
}
