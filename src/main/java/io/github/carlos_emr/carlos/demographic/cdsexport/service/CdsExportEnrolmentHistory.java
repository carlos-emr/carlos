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
 * field's disposition. Distinct episodes retain their explicit dates and physician identity;
 * conflicting versions of one termination, reopened episodes and stale effective dates
 * require review. This does not reconcile imported fields or certify the rest of a chart.
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

    private record EventKey(CdsExportEnrolmentEventDto value, String nativeProvider) {}

    private record EpisodeKey(java.time.LocalDate start, String nativeProvider) {}

    private final int patient;
    private final int maximumRows;
    private final long maximumBytes;
    private final Runnable checkpoint;
    private final Map<EventKey, CdsExportEnrolmentEventDto> events = new LinkedHashMap<>();
    private final Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields =
            new LinkedHashMap<>();
    private final Map<EpisodeKey, CdsExportEnrolmentEventDto> terminated = new HashMap<>();
    private final Set<EpisodeKey> started = new HashSet<>();
    private CdsExportEnrolmentEventDto currentEvent;
    private String currentState;
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
            if (!result.representable())
                throw new IOException("CDS_EXPORT_ENROLMENT_HISTORY_REQUIRES_REVIEW");
            if (result.event() != null) {
                var episode = new EpisodeKey(result.event().start(), result.nativeProvider());
                if (result.event().status().equals("0")) {
                    var prior = terminated.putIfAbsent(episode, result.event());
                    if (prior != null && !prior.equals(result.event())) ambiguous();
                } else {
                    if (terminated.containsKey(episode)) ambiguous();
                    for (var prior : started)
                        if (!prior.equals(episode) && !terminated.containsKey(prior)) ambiguous();
                    started.add(episode);
                }
                events.putIfAbsent(
                        new EventKey(result.event(), result.nativeProvider()), result.event());
            }
            if (row.table().equals("demographic")) {
                currentEvent = result.event();
                currentState = row.fields().get("roster_status");
            }
            for (var entry : result.fields().entrySet()) {
                var previous = fields.putIfAbsent(entry.getKey(), entry.getValue());
                if (previous != null && !previous.equals(entry.getValue())) invalid();
                if (previous == null)
                    bytes =
                            Math.addExact(
                                    bytes,
                                    512L
                                            + entry.getKey().recordKey().length() * 2L
                                            + entry.getValue().destination().length() * 2L
                                            + entry.getValue().rule().length() * 2L);
            }
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
            if (!events.isEmpty()) {
                if (currentEvent == null) {
                    if (!"NR".equals(currentState) || !terminated.keySet().containsAll(started))
                        ambiguous();
                } else {
                    java.time.LocalDate effective = effective(currentEvent);
                    for (var event : events.values())
                        if (effective(event).isAfter(effective)
                                || effective(event).equals(effective)
                                        && !event.equals(currentEvent)) ambiguous();
                }
            }
            finished = true;
            return new Result(List.copyOf(events.values()), identities.size(), fields);
        } catch (IOException | RuntimeException failure) {
            failed = true;
            throw failure;
        }
    }

    private static java.time.LocalDate effective(CdsExportEnrolmentEventDto event) {
        return event.end() == null ? event.start() : event.end();
    }

    private static void ambiguous() throws IOException {
        throw new IOException("CDS_EXPORT_ENROLMENT_HISTORY_AMBIGUOUS_VERSION");
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
