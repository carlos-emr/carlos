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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.CdsExportRunDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dto.CdsExportSelectionRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportSelectionDto;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Paged, read-only, repeatable-read cohort discovery of a roster of any size. Provider requests discover every
 * matching patient status; selected requests retain exactly their explicit IDs or refuse
 * a missing member. All bytes are encrypted while streaming. This is the deliberate
 * bounded-spooling exception to the no-I/O transaction rule: no rendering/publication or
 * HTTP call occurs here. The caller supplies fresh authorization and a durable owner /
 * cancellation probe using an independent metadata-only connection. The returned file
 * is not a sealed job manifest until independently verified and durably published.
 */
@Service
public class CdsExportSelectionService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int PAGE = 256;
    /** Physicians named by one roster statement; the DAO accepts no more in one statement. */
    private static final int GROUP = 100;
    private final DemographicDao demographics;
    private final CdsExportRunDao clock;
    private final TransactionTemplate read;

    public CdsExportSelectionService(
            DemographicDao demographics,
            CdsExportRunDao clock,
            PlatformTransactionManager transactions) {
        this.demographics = Objects.requireNonNull(demographics);
        this.clock = Objects.requireNonNull(clock);
        read = new TransactionTemplate(transactions);
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        read.setTimeout(120);
    }

    /** Timestamp bounds describe the source read; patient membership is bound by the exact inventory digest. */
    // Cleanup only throws without a primary failure; otherwise its error is suppressed on that
    // failure.
    @SuppressWarnings("java:S1143")
    public CdsExportSelectionDto capture(
            CdsExportCommand command,
            CdsExportStagingService staging,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(command);
        Objects.requireNonNull(staging);
        Objects.requireNonNull(checkpoint);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("CDS_EXPORT_AMBIENT_TRANSACTION_REFUSED");
        }
        io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto[] owned = {
            null
        };
        boolean complete = false;
        Throwable primary = null;
        try {
            CdsExportSelectionDto captured =
                    read.execute(
                            status -> {
                                checkpoint.run();
                                Instant started = clock.now();
                                long[] count = {0};
                                try {
                                    var file =
                                            staging.stage(
                                                    "PATIENT_SELECTION",
                                                    output -> {
                                                        if (command.selection()
                                                                == CdsExportCommand.Selection
                                                                        .PHYSICIAN_ROSTER) {
                                                            roster(
                                                                    command.physicianNos(),
                                                                    output,
                                                                    count,
                                                                    checkpoint);
                                                        } else
                                                            selected(
                                                                    command.patientNos(),
                                                                    output,
                                                                    count,
                                                                    checkpoint);
                                                    });
                                    owned[0] = file;
                                    checkpoint.run();
                                    return new CdsExportSelectionDto(
                                            started, clock.now(), count[0], file);
                                } catch (IOException failure) {
                                    throw new UncheckedIOException(failure);
                                }
                            });
            complete = true;
            return Objects.requireNonNull(captured);
        } catch (UncheckedIOException failure) {
            primary = failure.getCause();
            throw failure.getCause();
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            if (!complete && owned[0] != null)
                try {
                    staging.discardUnpublished(owned[0]);
                } catch (IOException cleanup) {
                    if (primary != null) primary.addSuppressed(cleanup);
                    else throw cleanup;
                }
        }
    }

    /**
     * Every patient of the named physicians, in ascending patient number. The physicians are
     * asked for in groups of {@link #GROUP} (the roster query takes at most that many), each
     * group through its own keyset cursor, and the groups' pages are merged by patient number;
     * a patient has one physician, so no patient is in two groups. A list of any length is
     * therefore read with statements of a fixed size, and up to one group it is exactly the
     * single cursor it always was.
     */
    private void roster(
            List<String> physicians,
            OutputStream output,
            long[] count,
            Runnable checkpoint)
            throws IOException {
        var cursors =
                new java.util.PriorityQueue<RosterCursor>(
                        java.util.Comparator.comparingInt(RosterCursor::head));
        for (int offset = 0; offset < physicians.size(); offset += GROUP) {
            var cursor =
                    new RosterCursor(
                            physicians.subList(
                                    offset, Math.min(physicians.size(), offset + GROUP)));
            if (cursor.fill(checkpoint)) cursors.add(cursor);
        }
        Integer previous = null;
        while (!cursors.isEmpty()) {
            checkpoint.run();
            var cursor = cursors.poll();
            var row = cursor.take();
            if (previous != null && row.demographicNo() <= previous)
                throw new IOException("CDS_EXPORT_SELECTION_SCOPE_REQUIRES_REVIEW");
            append(row, output, count);
            previous = row.demographicNo();
            if (cursor.fill(checkpoint)) cursors.add(cursor);
        }
    }

    /** One group of physicians read by keyset pages; holds at most one page. */
    private final class RosterCursor {
        private final List<String> physicians;
        private final Set<String> members;
        private final java.util.ArrayDeque<CdsExportSelectionRowDto> page =
                new java.util.ArrayDeque<>();
        private Integer after;
        private boolean exhausted;

        RosterCursor(List<String> physicians) {
            this.physicians = physicians;
            // A HashSet answers a row with no physician; an immutable set would throw.
            members = new HashSet<>(physicians);
        }

        int head() {
            return page.element().demographicNo();
        }

        CdsExportSelectionRowDto take() {
            return page.remove();
        }

        /** Reads the next page when this one is used up; false when the group has no more. */
        boolean fill(Runnable checkpoint) throws IOException {
            if (!page.isEmpty()) return true;
            if (exhausted) return false;
            checkpoint.run();
            List<CdsExportSelectionRowDto> rows =
                    demographics.findCdsExportRosterAfter(physicians, after, PAGE);
            if (rows.size() > PAGE) throw new IOException("CDS_EXPORT_SELECTION_PAGE_INVALID");
            for (var row : rows) {
                checkpoint.run();
                if (row.demographicNo() < 1
                        || after != null && row.demographicNo() <= after
                        || !members.contains(row.physicianNo()))
                    throw new IOException("CDS_EXPORT_SELECTION_SCOPE_REQUIRES_REVIEW");
                page.add(row);
                after = row.demographicNo();
            }
            exhausted = rows.size() < PAGE;
            return !page.isEmpty();
        }
    }

    private void selected(
            List<Integer> patients,
            OutputStream output,
            long[] count,
            Runnable checkpoint)
            throws IOException {
        for (int offset = 0; offset < patients.size(); offset += PAGE) {
            checkpoint.run();
            List<Integer> requested =
                    patients.subList(offset, Math.min(patients.size(), offset + PAGE));
            List<CdsExportSelectionRowDto> page =
                    demographics.findCdsExportSelectedPatients(requested);
            if (page.size() != requested.size())
                throw new IOException("CDS_EXPORT_SELECTED_PATIENT_MISSING");
            Set<Integer> seen = new HashSet<>();
            int previous = 0;
            for (var row : page) {
                checkpoint.run();
                if (!requested.contains(row.demographicNo())
                        || row.demographicNo() <= previous
                        || !seen.add(row.demographicNo())) {
                    throw new IOException("CDS_EXPORT_SELECTION_PAGE_INVALID");
                }
                append(row, output, count);
                previous = row.demographicNo();
            }
        }
    }

    private static void append(
            CdsExportSelectionRowDto row, OutputStream output, long[] count)
            throws IOException {
        // Every selected patient is listed: the roster is streamed to protected storage and no
        // count refuses it.
        count[0] = Math.addExact(count[0], 1);
        if (row.physicianNo() != null && row.physicianNo().length() > 16)
            throw new IOException("CDS_EXPORT_SELECTION_SCOPE_REQUIRES_REVIEW");
        output.write(JSON.writeValueAsBytes(row));
        output.write('\n');
    }
}
