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
 * Bounded, read-only, repeatable-read cohort discovery. Provider requests discover every
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
    private final DemographicDao demographics;
    private final CdsExportRunDao clock;
    private final TransactionTemplate read;

    public CdsExportSelectionService(DemographicDao demographics, CdsExportRunDao clock,
            PlatformTransactionManager transactions) {
        this.demographics = Objects.requireNonNull(demographics); this.clock = Objects.requireNonNull(clock);
        read = new TransactionTemplate(transactions); read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED); read.setTimeout(120);
    }

    /** Timestamp bounds describe the source read; patient membership is bound by the exact inventory digest. */
    // Cleanup only throws without a primary failure; otherwise its error is suppressed on that failure.
    @SuppressWarnings("java:S1143")
    public CdsExportSelectionDto capture(CdsExportCommand command, CdsExportStagingService staging,
            long maximumPatients, Runnable checkpoint) throws IOException {
        Objects.requireNonNull(command); Objects.requireNonNull(staging); Objects.requireNonNull(checkpoint);
        if (maximumPatients < 1 || maximumPatients > 2_000_000) throw new IllegalArgumentException("CDS_EXPORT_COHORT_LIMIT_INVALID");
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("CDS_EXPORT_AMBIENT_TRANSACTION_REFUSED");
        }
        io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto[] owned = {null};
        boolean complete = false; Throwable primary = null;
        try {
            CdsExportSelectionDto captured = read.execute(status -> {
                checkpoint.run(); Instant started = clock.now(); long[] count = {0};
                try {
                    var file = staging.stage("PATIENT_SELECTION", output -> {
                        if (command.selection() == CdsExportCommand.Selection.PHYSICIAN_ROSTER) {
                            roster(command.physicianNos(), output, maximumPatients, count, checkpoint);
                        } else selected(command.patientNos(), output, maximumPatients, count, checkpoint);
                    });
                    owned[0] = file;
                    checkpoint.run(); return new CdsExportSelectionDto(started, clock.now(), count[0], file);
                } catch (IOException failure) { throw new UncheckedIOException(failure); }
            });
            complete = true; return Objects.requireNonNull(captured);
        } catch (UncheckedIOException failure) { primary = failure.getCause(); throw failure.getCause(); }
        catch (RuntimeException | Error failure) { primary = failure; throw failure; }
        finally {
            if (!complete && owned[0] != null) try { staging.discardUnpublished(owned[0]); }
            catch (IOException cleanup) { if (primary != null) primary.addSuppressed(cleanup); else throw cleanup; }
        }
    }
    private void roster(List<String> physicians, OutputStream output, long maximum, long[] count, Runnable checkpoint)
            throws IOException {
        Integer after = null;
        while (true) {
            checkpoint.run(); List<CdsExportSelectionRowDto> page = demographics.findCdsExportRosterAfter(physicians, after, PAGE);
            if (page.size() > PAGE) throw new IOException("CDS_EXPORT_SELECTION_PAGE_INVALID");
            for (var row : page) {
                checkpoint.run();
                if (row.demographicNo() < 1 || after != null && row.demographicNo() <= after
                        || !physicians.contains(row.physicianNo())) throw new IOException("CDS_EXPORT_SELECTION_SCOPE_REQUIRES_REVIEW");
                append(row, output, maximum, count); after = row.demographicNo();
            }
            if (page.size() < PAGE) return;
        }
    }
    private void selected(List<Integer> patients, OutputStream output, long maximum, long[] count, Runnable checkpoint)
            throws IOException {
        for (int offset = 0; offset < patients.size(); offset += PAGE) {
            checkpoint.run(); List<Integer> requested = patients.subList(offset, Math.min(patients.size(), offset + PAGE));
            List<CdsExportSelectionRowDto> page = demographics.findCdsExportSelectedPatients(requested);
            if (page.size() != requested.size()) throw new IOException("CDS_EXPORT_SELECTED_PATIENT_MISSING");
            Set<Integer> seen = new HashSet<>(); int previous = 0;
            for (var row : page) {
                checkpoint.run();
                if (!requested.contains(row.demographicNo()) || row.demographicNo() <= previous || !seen.add(row.demographicNo())) {
                    throw new IOException("CDS_EXPORT_SELECTION_PAGE_INVALID");
                }
                append(row, output, maximum, count); previous = row.demographicNo();
            }
        }
    }
    private static void append(CdsExportSelectionRowDto row, OutputStream output, long maximum, long[] count) throws IOException {
        if (++count[0] > maximum) throw new IOException("CDS_EXPORT_COHORT_LIMIT_REQUIRES_REVIEW");
        if (row.physicianNo() != null && row.physicianNo().length() > 16) throw new IOException("CDS_EXPORT_SELECTION_SCOPE_REQUIRES_REVIEW");
        output.write(JSON.writeValueAsBytes(row)); output.write('\n');
    }
}
