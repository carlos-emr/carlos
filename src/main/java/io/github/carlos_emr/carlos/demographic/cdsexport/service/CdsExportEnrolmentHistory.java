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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.io.IOException;
import java.util.*;

/**
 * Folds identical roster snapshots into evidenced clinical events, retaining each native
 * field's disposition. The latest recorded version of an episode (start date and physician) is
 * its truth; an earlier version of the same episode is a superseded snapshot. The current row's
 * event is always exported. An archived episode that the current state contradicts (one left
 * open while the patient is no longer, or differently, enrolled; one dated after the current
 * state) was superseded without a recorded termination: its snapshot stays in the approved
 * audit arrangement with the archive's other superseded columns, and no end date is invented.
 * Roster states that are not enrolment events are decided by {@link CdsExportEnrolmentMapper}.
 * This does not reconcile imported fields or certify the rest of a chart.
 */
public final class CdsExportEnrolmentHistory {
    public record Result(
            List<CdsExportEnrolmentEventDto> events,
            long sourceRows,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields) {
        public Result {
            events = List.copyOf(events);
            fields = Map.copyOf(fields);
        }

        @Override
        public String toString() {
            return "CdsExportEnrolmentHistory.Result[protected]";
        }
    }

    private record EpisodeKey(java.time.LocalDate start, String nativeProvider) {}

    /** One accepted row's mapping, in acceptance (archive key, then current) order. */
    private record Mapped(
            CdsExportCapturedTextRowDto row,
            CdsExportCapturedTextRowDto provider,
            CdsExportEnrolmentMapper.Result result) {}

    private static final String SUPERSEDED = "SUPERSEDED_ROSTER_SNAPSHOT_IN_APPROVED_AUDIT";
    private static final String SUPERSEDED_PROVIDER = "SUPERSEDED_ROSTER_PROVIDER_IN_APPROVED_AUDIT";

    private final int patient;
    private final int maximumRows;
    private final long maximumBytes;
    private final Runnable checkpoint;
    private final List<Mapped> rows = new ArrayList<>();
    private final Set<String> identities = new HashSet<>();
    private String archiveKey;
    private boolean current;
    private boolean failed;
    private boolean finished;
    private long bytes;

    public CdsExportEnrolmentHistory(
            int patient, int maximumRows, long maximumBytes, Runnable checkpoint) {
        if (patient < 1
                || maximumRows < 1
                || maximumRows > 65_536
                || maximumBytes < 1
                || maximumBytes > (64L << 20))
            throw new IllegalArgumentException("CDS_EXPORT_ENROLMENT_HISTORY_LIMITS_INVALID");
        this.patient = patient;
        this.maximumRows = maximumRows;
        this.maximumBytes = maximumBytes;
        this.checkpoint = Objects.requireNonNull(checkpoint);
    }

    /** Complete, increasing archive-key order followed by the one current demographic row. */
    public void accept(CdsExportCapturedTextRowDto row, CdsExportCapturedTextRowDto provider)
            throws IOException {
        try {
            active();
            checkpoint.run();
            CdsExportEnrolmentMapper.requireRow(patient, row);
            if (identities.size() >= maximumRows
                    || !identities.add(row.table() + "/" + row.recordKey())) invalid();
            if (row.table().equals("demographicArchive")) {
                if (current || archiveKey != null && row.recordKey().compareTo(archiveKey) <= 0)
                    invalid();
                archiveKey = row.recordKey();
            } else {
                if (current) invalid();
                current = true;
            }
            var result = new CdsExportEnrolmentMapper().map(patient, row, provider);
            // Only provider values no CDS name can hold remain; they hold this patient, never
            // the whole job.
            if (!result.representable())
                throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                        .CdsExportMappingReviewException(
                        "CDS_EXPORT_ENROLMENT_HISTORY_REQUIRES_REVIEW");
            rows.add(new Mapped(row, provider, result));
            for (var entry : result.fields().entrySet())
                bytes =
                        Math.addExact(
                                bytes,
                                512L
                                        + entry.getKey().recordKey().length() * 2L
                                        + entry.getValue().destination().length() * 2L
                                        + entry.getValue().rule().length() * 2L);
            bytes = Math.addExact(bytes, 1024L);
            if (bytes > maximumBytes) limit();
            checkpoint.run();
        } catch (IOException | RuntimeException failure) {
            failed = true;
            throw failure;
        }
    }

    public Result finish(long expectedArchiveRows) throws IOException {
        try {
            active();
            checkpoint.run();
            if (!current || expectedArchiveRows < 0 || identities.size() != expectedArchiveRows + 1)
                invalid();
            var currentRow = rows.getLast();
            var currentEvent = currentRow.result().event();
            var currentEpisode =
                    currentEvent == null
                            ? null
                            : new EpisodeKey(currentEvent.start(), currentRow.result().nativeProvider());
            var episodes = new LinkedHashMap<EpisodeKey, List<Mapped>>();
            for (var mapped : rows)
                if (mapped.result().event() != null)
                    episodes.computeIfAbsent(
                                    new EpisodeKey(
                                            mapped.result().event().start(),
                                            mapped.result().nativeProvider()),
                                    ignored -> new ArrayList<>())
                            .add(mapped);
            var exported = new HashSet<Mapped>();
            for (var entry : episodes.entrySet()) {
                checkpoint.run();
                var versions = entry.getValue();
                var latest = versions.getLast().result().event();
                boolean open = latest.status().equals("1");
                // An episode left open while the current state is another episode or no
                // enrolment was superseded without a recorded termination.
                if (open && !entry.getKey().equals(currentEpisode)) continue;
                for (var mapped : versions) {
                    var event = mapped.result().event();
                    // An open episode's earlier terminations were withdrawn; a terminated
                    // episode keeps its start and its latest termination.
                    boolean keep =
                            open
                                    ? event.status().equals("1")
                                    : event.status().equals("1") || event.equals(latest);
                    if (keep
                            && !entry.getKey().equals(currentEpisode)
                            && currentEvent != null
                            && (effective(event).isAfter(effective(currentEvent))
                                    || effective(event).equals(effective(currentEvent))
                                            && !event.equals(currentEvent))) keep = false;
                    if (keep) exported.add(mapped);
                }
            }
            var emitted = new LinkedHashSet<CdsExportEnrolmentEventDto>();
            for (var mapped : rows) if (exported.contains(mapped)) emitted.add(mapped.result().event());
            var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
            for (var mapped : rows) {
                checkpoint.run();
                boolean audited = mapped.result().event() != null && !exported.contains(mapped);
                for (var entry : mapped.result().fields().entrySet()) {
                    var key = entry.getKey();
                    var decision = entry.getValue();
                    if (key.table().equals("provider")) continue;
                    if (audited && decision.outcome() != CdsExportFieldAssessmentDto.Outcome.ABSENT)
                        decision =
                                CdsExportEnrolmentMapper.superseded(
                                        mapped.row().fields().get(key.column()), SUPERSEDED);
                    if (fields.put(key, decision) != null) invalid();
                }
            }
            // A provider row's decision follows its uses: an exported event's physician, a
            // carried current state, or only superseded snapshots.
            var providerFields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
            for (int pass = 0; pass < 3; pass++)
                for (var mapped : rows) {
                    var provider = mapped.provider();
                    if (provider == null) continue;
                    boolean event = exported.contains(mapped);
                    boolean carried =
                            mapped.result().event() == null && mapped.row().table().equals("demographic");
                    if (pass == 0 && !event || pass == 1 && !carried) continue;
                    for (var entry : provider.fields().entrySet()) {
                        var key =
                                new CdsExportNativeFieldKeyDto(
                                        provider.table(), provider.recordKey(), entry.getKey());
                        var decision =
                                pass == 2
                                        ? CdsExportEnrolmentMapper.superseded(
                                                entry.getValue(), SUPERSEDED_PROVIDER)
                                        : mapped.result().fields().get(key);
                        if (decision == null) invalid();
                        var prior = providerFields.putIfAbsent(key, decision);
                        if (pass == 0 && prior != null && !prior.equals(decision)) invalid();
                    }
                }
            fields.putAll(providerFields);
            for (var entry : fields.entrySet()) {
                var key = entry.getKey();
                bytes =
                        Math.addExact(
                                bytes,
                                256L + key.recordKey().length() * 2L + entry.getValue().rule().length() * 2L);
            }
            if (bytes > maximumBytes) limit();
            finished = true;
            return new Result(List.copyOf(emitted), identities.size(), fields);
        } catch (IOException | RuntimeException failure) {
            failed = true;
            throw failure;
        }
    }

    private static java.time.LocalDate effective(CdsExportEnrolmentEventDto event) {
        return event.end() == null ? event.start() : event.end();
    }

    private void active() throws IOException {
        if (failed || finished) throw new IOException("CDS_EXPORT_ENROLMENT_HISTORY_CLOSED");
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_ENROLMENT_HISTORY_SCOPE_INVALID");
    }

    private static void limit() throws IOException {
        throw new IOException("CDS_EXPORT_ENROLMENT_HISTORY_RESOURCE_LIMIT");
    }
}
