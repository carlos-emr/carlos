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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportChartCheckpointDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Seals one export attempt's finished charts, as they finish, into a chain of encrypted {@value
 * CdsExportChartCheckpointDto#PURPOSE} members of the attempt's own staging store, so that an
 * attempt that stops before its package is ready leaves an exact, authenticated list of what it
 * finished.
 *
 * <p>Each member is written whole, fsynced and read back by digest like every staged member, and
 * names the member before it (identifier, plaintext SHA-256 and length), so the newest member pins
 * the whole chain. The ledger records the newest member on the attempt row ({@code
 * CdsExportLedgerService#recordCheckpoint}); a later attempt trusts a chain only from that pointer
 * ({@link CdsExportChartCheckpointReader}). A member holds, in cohort order: one {@code CHART}
 * entry per finished chart (its patient, its capture fingerprint, whether it may be adopted at all,
 * and the chart record the packaging step spools: archive path and naming record, the XML member
 * and its validation counts, the attachment members and the ReadMe counts) and one {@code HELD}
 * entry per held patient. The chart's note scopes are not kept: an adopted chart takes them from
 * its new capture, whose fingerprint covers them.
 *
 * <p>Nothing here changes what an export produces. Memory is one batch of entries waiting to be
 * sealed: a member is sealed when the caller asks (at each lease heartbeat) or when the batch
 * reaches {@link CdsExportSpoolService#BATCH_BYTES}, whichever comes first; no count or size
 * refuses an entry. One thread owns a persister.
 *
 * @since 2026-11-14
 */
final class CdsExportChartCheckpointPersister {
    /** The format of a checkpoint member. */
    static final int PROTOCOL = 1;

    private final CdsExportStagingService store;
    private final String runId;
    private final long epoch;
    private final String parameters;
    private final List<byte[]> pending = new ArrayList<>();
    private long pendingBytes, pendingCharts, pendingHeld;
    private int pendingLast;
    private long sequence, charts, held;
    private int lastPatient;
    private CdsExportChartCheckpointDto latest;
    private boolean failed, recorded = true;

    /**
     * @param parameters the SHA-256 of the build parameters the charts were made under ({@link
     *     CdsExportChartFingerprints#parameters}); a later attempt adopts nothing from a chain
     *     whose parameters differ from its own
     */
    CdsExportChartCheckpointPersister(
            CdsExportStagingService store, String runId, long epoch, String parameters) {
        this.store = Objects.requireNonNull(store);
        this.runId = Objects.requireNonNull(runId);
        if (epoch < 1 || parameters == null || !parameters.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("CDS_EXPORT_CHART_CHECKPOINT_INVALID");
        this.epoch = epoch;
        this.parameters = parameters;
    }

    /**
     * One finished chart, in cohort order.
     *
     * @param reusable false when the chart's build read something its capture does not hold (a
     *     printed form's live reference rows), so no later attempt may adopt it
     * @param fingerprint the SHA-256 of the capture the chart was built from ({@link
     *     CdsExportChartFingerprints#capture})
     * @param chart the chart record, without its note scopes
     */
    void chart(int patient, boolean reusable, String fingerprint, JsonNode chart)
            throws IOException {
        if (fingerprint == null
                || !fingerprint.matches("[0-9a-f]{64}")
                || chart == null
                || !chart.isObject()
                || chart.path("patient").asInt(-1) != patient) invalid();
        ObjectNode entry = CdsExportChartCheckpointReader.JSON.createObjectNode();
        entry.put("kind", "CHART");
        entry.put("patient", patient);
        entry.put("reusable", reusable);
        entry.put("fingerprint", fingerprint);
        entry.set("chart", chart);
        append(patient, entry);
        pendingCharts++;
    }

    /** One patient held back from the package, in cohort order; a later attempt evaluates it again. */
    void held(int patient, String code) throws IOException {
        if (code == null || !code.matches("CDS_EXPORT_[A-Z0-9_]{1,85}")) invalid();
        ObjectNode entry = CdsExportChartCheckpointReader.JSON.createObjectNode();
        entry.put("kind", "HELD");
        entry.put("patient", patient);
        entry.put("code", code);
        append(patient, entry);
        pendingHeld++;
    }

    private void append(int patient, ObjectNode entry) throws IOException {
        usable();
        int previous = pending.isEmpty() ? lastPatient : pendingLast;
        if (patient < 1 || patient <= previous) invalid();
        byte[] bytes = CdsExportChartCheckpointReader.JSON.writeValueAsBytes(entry);
        pending.add(bytes);
        pendingBytes += bytes.length + 1L;
        pendingLast = patient;
    }

    /** Entries waiting to be sealed. */
    boolean pending() {
        return !pending.isEmpty();
    }

    /** True when the waiting batch has reached the size at which it is written. */
    boolean full() {
        return pendingBytes >= CdsExportSpoolService.BATCH_BYTES;
    }

    /**
     * Writes the waiting entries as the next member of the chain.
     *
     * @return the new member, which the caller owns (it belongs in the attempt's ownership
     *     manifest); null when nothing was waiting
     */
    CdsExportStagedFileDto seal() throws IOException {
        usable();
        if (pending.isEmpty()) return null;
        try {
            ObjectNode header = CdsExportChartCheckpointReader.JSON.createObjectNode();
            header.put("kind", "CHECKPOINT");
            header.put("protocol", PROTOCOL);
            header.put("runId", runId);
            header.put("epoch", epoch);
            header.put("sequence", sequence + 1);
            header.put("parameters", parameters);
            if (latest == null) header.putNull("previous");
            else {
                var previous = header.putObject("previous");
                previous.put("memberId", latest.memberId());
                previous.put("sha256", latest.sha256());
                previous.put("bytes", latest.bytes());
            }
            header.put("charts", charts);
            header.put("held", held);
            header.put("lastPatient", lastPatient);
            ObjectNode end = CdsExportChartCheckpointReader.JSON.createObjectNode();
            end.put("kind", "END");
            end.put("entries", pending.size());
            end.put("charts", charts + pendingCharts);
            end.put("held", held + pendingHeld);
            end.put("lastPatient", pendingLast);
            byte[] first = CdsExportChartCheckpointReader.JSON.writeValueAsBytes(header);
            byte[] last = CdsExportChartCheckpointReader.JSON.writeValueAsBytes(end);
            var member =
                    store.stage(
                            CdsExportChartCheckpointDto.PURPOSE,
                            output -> {
                                output.write(first);
                                output.write('\n');
                                for (byte[] entry : pending) {
                                    output.write(entry);
                                    output.write('\n');
                                }
                                output.write(last);
                                output.write('\n');
                            });
            sequence++;
            charts += pendingCharts;
            held += pendingHeld;
            lastPatient = pendingLast;
            latest =
                    new CdsExportChartCheckpointDto(
                            member.memberId(), member.plaintextSha256(), member.plaintextBytes(),
                            charts);
            recorded = false;
            pending.clear();
            pendingBytes = 0;
            pendingCharts = 0;
            pendingHeld = 0;
            return member;
        } catch (IOException | RuntimeException | Error failure) {
            // A member that could not be written leaves the chain as it was; the staging store
            // removed the partial file. Nothing more is appended to a chain whose state is unknown.
            failed = true;
            throw failure;
        }
    }

    /** The newest sealed member, or null before the first seal. */
    CdsExportChartCheckpointDto latest() {
        return latest;
    }

    /** The finished charts sealed so far. */
    long charts() {
        return charts;
    }

    /** The highest patient the sealed chain names; 0 before the first seal. */
    int lastPatient() {
        return lastPatient;
    }

    /** True when the ledger has recorded the newest sealed member (or nothing was sealed). */
    boolean recorded() {
        return recorded;
    }

    /** The ledger recorded the newest sealed member on the attempt row. */
    void markRecorded() {
        recorded = true;
    }

    private void usable() throws IOException {
        if (failed) throw new IOException("CDS_EXPORT_CHART_CHECKPOINT_FAILED");
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_CHART_CHECKPOINT_INVALID");
    }
}
