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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPackageMemberDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPatientCaptureDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

/**
 * The resume state of one export attempt: its own finished-chart checkpoint, and the charts kept
 * by the job's earlier attempts that it may adopt instead of building them again.
 *
 * <p><b>What is adopted.</b> For each cohort patient the newest earlier attempt whose checkpoint
 * reached the patient decides: a chart it finished may be adopted; a patient it held, or did not
 * have in its cohort, is built again. A chart is adopted only when all of these hold: it was made
 * under the same build parameters ({@link CdsExportChartFingerprints#parameters}), its build read
 * nothing outside its capture ({@link CdsExportChartInputs}), and the fingerprint of the patient's
 * new capture, made exactly as for a chart that is built (same reads, authorization session and
 * audit), equals the fingerprint the chart was built from ({@link
 * CdsExportChartFingerprints#capture}). Never on a timestamp or a counter. The new capture is then
 * discarded and the chart's XML and attachments are copied, each read to its authenticated end,
 * into this attempt's own store, so its ownership, checkpoint and cleanup are this attempt's.
 * Authorization is never adopted: the packaging step's cohort passes check adopted charts exactly
 * as built ones.
 *
 * <p><b>What is erased.</b> An earlier attempt whose every kept chart this attempt has decided
 * again, by adopting or building it, is superseded when this attempt's checkpoint is next
 * recorded: the ledger makes it due for the retention sweep. A checkpoint that cannot be trusted
 * (its chain does not match its recorded pointer, a member it pins is missing or altered, the
 * job's key is gone) is rejected the same way. Should an earlier attempt's store fail while this
 * attempt reads it, nothing more is adopted by this attempt: every later chart is built.
 *
 * @since 2026-11-14
 */
@Service
public class CdsExportChartAdoptionService {
    private static final Logger LOG = LogManager.getLogger(CdsExportChartAdoptionService.class);

    private final CdsExportLedgerService ledger;
    private final CdsExportStagingFactory stores;

    public CdsExportChartAdoptionService(
            CdsExportLedgerService ledger, CdsExportStagingFactory stores) {
        this.ledger = Objects.requireNonNull(ledger);
        this.stores = Objects.requireNonNull(stores);
    }

    /**
     * Opens this attempt's resume state: its own checkpoint, written to {@code store}, and every
     * kept checkpoint of an earlier attempt of the job that was made under the same {@code
     * parameters}, each verified from the pointer the ledger recorded.
     */
    Session open(
            CdsExportLeaseDto lease,
            CdsExportStagingService store,
            String parameters,
            CdsExportRunControl control)
            throws IOException {
        var checkpoints =
                new CdsExportChartCheckpointPersister(
                        store, lease.runId(), lease.epoch(), parameters);
        var priors = new ArrayList<Prior>();
        var ignored = new ArrayList<Long>();
        for (var kept : ledger.keptCheckpoints(lease)) {
            control.checkpoint();
            try {
                var priorStore =
                        stores.openExisting(
                                lease.runId(),
                                kept.epoch(),
                                lease.document().storage(),
                                control::checkpoint);
                var chain =
                        CdsExportChartCheckpointReader.open(
                                priorStore,
                                lease.runId(),
                                kept.epoch(),
                                kept.pointer(),
                                control::checkpoint);
                if (!parameters.equals(chain.parameters())) {
                    // Made under other bounds, limits, zone or version: nothing of it is adopted.
                    LOG.info(
                            "CDS_EXPORT_CHECKPOINT_PARAMETERS_CHANGED run={} epoch={}",
                            lease.runId(),
                            kept.epoch());
                    ignored.add(kept.epoch());
                    continue;
                }
                priors.add(new Prior(kept.epoch(), chain));
            } catch (IOException failed) {
                // Codes only: a missing file's own message would name its path.
                boolean untrusted = CdsExportChartCheckpointReader.untrusted(failed);
                LOG.warn(
                        "CDS_EXPORT_CHECKPOINT_UNUSABLE run={} epoch={} untrusted={}",
                        lease.runId(),
                        kept.epoch(),
                        untrusted);
                if (untrusted) ledger.rejectCheckpoint(lease, kept.epoch());
                else ignored.add(kept.epoch());
            }
        }
        if (!priors.isEmpty())
            // Counts only.
            LOG.info(
                    "CDS_EXPORT_CHECKPOINTS_OPENED run={} epoch={} attempts={} charts={}",
                    lease.runId(),
                    lease.epoch(),
                    priors.size(),
                    priors.stream().mapToLong(prior -> prior.chain.charts()).sum());
        return new Session(lease, ledger, checkpoints, priors, ignored);
    }

    /** A resume state with no earlier attempt to adopt from: the checkpoint alone. */
    static Session withoutPriors(
            CdsExportLeaseDto lease,
            CdsExportLedgerService ledger,
            CdsExportStagingService store,
            String parameters) {
        return new Session(
                lease,
                ledger,
                new CdsExportChartCheckpointPersister(
                        store, lease.runId(), lease.epoch(), parameters),
                List.of(),
                List.of());
    }

    /** One earlier attempt's verified checkpoint, read forward in cohort order. */
    private static final class Prior {
        private final long epoch;
        private final CdsExportChartCheckpointReader.Chain chain;
        private CdsExportChartCheckpointReader.Cursor cursor;
        private CdsExportChartCheckpointReader.Entry ahead;
        private boolean ended;

        Prior(long epoch, CdsExportChartCheckpointReader.Chain chain) {
            this.epoch = epoch;
            this.chain = chain;
        }

        /** The entry for {@code patient}, or null; patients are asked in ascending order. */
        CdsExportChartCheckpointReader.Entry at(int patient) throws IOException {
            if (cursor == null && !ended) cursor = chain.cursor();
            while (!ended && (ahead == null || ahead.patient() < patient)) {
                ahead = cursor.next();
                if (ahead == null) close();
            }
            if (ahead == null || ahead.patient() != patient) return null;
            var found = ahead;
            ahead = null;
            return found;
        }

        void close() throws IOException {
            ended = true;
            var open = cursor;
            cursor = null;
            if (open != null) open.close();
        }
    }

    /** A chart an earlier attempt finished for the patient, which this attempt may adopt. */
    static final class Candidate {
        private final Prior prior;
        private final CdsExportChartCheckpointReader.Entry entry;

        private Candidate(Prior prior, CdsExportChartCheckpointReader.Entry entry) {
            this.prior = prior;
            this.entry = entry;
        }

        int patient() {
            return entry.patient();
        }

        @Override
        public String toString() {
            return "CdsExportChartAdoptionService.Candidate[protected]";
        }
    }

    /** A chart adopted into this attempt: its record with this attempt's own members. */
    record Adopted(
            CdsExportClinicalPackageService.Chart chart,
            List<CdsExportStagedFileDto> members,
            long sourceEpoch) {
        @Override
        public String toString() {
            return "CdsExportChartAdoptionService.Adopted[protected]";
        }
    }

    /** An earlier attempt's store failed while it was read: nothing more is adopted from it. */
    private static final class PriorUnreadable extends IOException {
        private static final long serialVersionUID = 1L;

        PriorUnreadable(IOException cause) {
            super("CDS_EXPORT_CHART_CHECKPOINT_UNREADABLE", cause);
        }
    }

    /** One attempt's resume state; owned by the packaging step's thread. */
    static final class Session implements AutoCloseable {
        private final CdsExportLeaseDto lease;
        private final CdsExportLedgerService ledger;
        private final CdsExportChartCheckpointPersister checkpoints;
        private final List<Prior> priors;
        /** Kept attempts this attempt does not adopt from; released once its roster is decided. */
        private final List<Long> ignored;
        private long adopted;
        private boolean disabled;

        private Session(
                CdsExportLeaseDto lease,
                CdsExportLedgerService ledger,
                CdsExportChartCheckpointPersister checkpoints,
                List<Prior> priors,
                List<Long> ignored) {
            this.lease = lease;
            this.ledger = ledger;
            this.checkpoints = checkpoints;
            this.priors = new ArrayList<>(priors);
            this.ignored = new ArrayList<>(ignored);
        }

        /** This attempt's own checkpoint. */
        CdsExportChartCheckpointPersister checkpoints() {
            return checkpoints;
        }

        /** The charts this attempt adopted so far. */
        long adopted() {
            return adopted;
        }

        /**
         * The chart an earlier attempt finished for {@code patient} that this attempt may adopt,
         * or null when the patient is to be built: the newest earlier attempt whose checkpoint
         * reached the patient decides, and a patient it held or did not have is built again.
         * Patients are asked in ascending order.
         */
        Candidate candidate(int patient) throws IOException {
            if (disabled) return null;
            for (var prior : priors) {
                if (prior.chain.lastPatient() < patient) continue;
                CdsExportChartCheckpointReader.Entry entry;
                try {
                    entry = prior.at(patient);
                } catch (IOException failed) {
                    unusable(prior, failed);
                    return null;
                }
                if (entry == null || !entry.chartEntry() || !entry.reusable()) return null;
                return new Candidate(prior, entry);
            }
            return null;
        }

        /**
         * Adopts the candidate when the patient's new capture, whose fingerprint is {@code
         * fingerprint}, is exactly the one the chart was built from: copies the chart's members
         * into {@code store} and returns the chart with
         * them, with the new capture's notes and manifest digest. Null when the capture differs,
         * or when the earlier attempt's store cannot give the chart back intact; the chart is
         * then built from the new capture.
         *
         * @throws IOException when this attempt's own store fails
         */
        Adopted adopt(
                Candidate candidate,
                String fingerprint,
                CdsExportPatientCaptureDto source,
                CdsExportStagingService store)
                throws IOException {
            Objects.requireNonNull(candidate);
            if (disabled
                    || candidate.patient() != source.capture().patient()
                    || !candidate.entry.fingerprint().equals(fingerprint)) return null;
            CdsExportClinicalPackageService.Chart prior;
            try {
                prior =
                        CdsExportClinicalPackageService.checkpointChart(
                                candidate.entry.chart(), candidate.patient());
            } catch (IOException malformed) {
                unusable(candidate.prior, malformed);
                return null;
            }
            var copies = new ArrayList<CdsExportStagedFileDto>();
            try {
                for (var member : CdsExportClinicalPackageService.chartMembers(prior))
                    copies.add(copy(candidate.prior.chain.store(), member, store));
            } catch (PriorUnreadable unreadable) {
                discard(store, copies, unreadable);
                unusable(candidate.prior, (IOException) unreadable.getCause());
                return null;
            } catch (IOException | RuntimeException | Error failure) {
                discard(store, copies, failure);
                throw failure;
            }
            var attachments = new ArrayList<CdsExportPackageMemberDto>();
            for (int index = 0; index < prior.attachments().size(); index++)
                attachments.add(
                        new CdsExportPackageMemberDto(
                                prior.attachments().get(index).relativePath(),
                                copies.get(index + 1)));
            var noteScope = source.capture().tables().get("casemgmt_note");
            List<CdsExportNoteAccessDto> notes =
                    noteScope == null ? List.of() : noteScope.noteAccess();
            var chart =
                    new CdsExportClinicalPackageService.Chart(
                            prior.patient(),
                            source.manifest().plaintextSha256(),
                            prior.relative(),
                            prior.naming(),
                            copies.getFirst(),
                            List.copyOf(attachments),
                            prior.validation(),
                            prior.dataSummaries(),
                            prior.listed(),
                            prior.notExported(),
                            notes);
            return new Adopted(chart, List.copyOf(copies), candidate.prior.epoch);
        }

        /** The adopted chart was accepted into this attempt (its patient's session closed). */
        void accepted() {
            adopted++;
        }

        /**
         * Records this attempt's newest sealed checkpoint member on its attempt row, with the
         * earlier attempts it has now superseded: those whose every kept patient this attempt's
         * recorded checkpoint has decided again. {@code complete} at the end of the roster, when
         * every earlier attempt is superseded.
         */
        void record(boolean complete) {
            var latest = checkpoints.latest();
            if (latest == null) return;
            var released = new TreeSet<Long>();
            if (complete) released.addAll(ignored);
            for (var prior : priors)
                if (complete || prior.chain.lastPatient() <= checkpoints.lastPatient())
                    released.add(prior.epoch);
            ledger.recordCheckpoint(lease, latest, adopted, released, complete);
            checkpoints.markRecorded();
            if (complete) ignored.clear();
            for (var iterator = priors.iterator(); iterator.hasNext(); ) {
                var prior = iterator.next();
                if (released.contains(prior.epoch)) {
                    iterator.remove();
                    try {
                        prior.close();
                    } catch (IOException cleanup) {
                        // A read-only cursor of a superseded attempt; nothing depends on it.
                        LOG.warn(
                                "CDS_EXPORT_CHECKPOINT_CURSOR_NOT_CLOSED run={} epoch={}",
                                lease.runId(),
                                prior.epoch);
                    }
                }
            }
        }

        /** True when the ledger has recorded the newest sealed member. */
        boolean recorded() {
            return checkpoints.recorded();
        }

        /**
         * An earlier attempt's checkpoint or store failed while this attempt read it: nothing more
         * is adopted by this attempt, and a checkpoint that cannot be trusted is rejected. The
         * rejection is best effort: a kept attempt the ledger could not reject now is released
         * when this attempt's roster is decided, or at its own expiry.
         */
        private void unusable(Prior prior, IOException failed) {
            disabled = true;
            boolean untrusted = CdsExportChartCheckpointReader.untrusted(failed);
            LOG.warn(
                    "CDS_EXPORT_CHECKPOINT_UNUSABLE run={} epoch={} untrusted={}",
                    lease.runId(),
                    prior.epoch,
                    untrusted);
            if (!untrusted) return;
            try {
                ledger.rejectCheckpoint(lease, prior.epoch);
            } catch (RuntimeException unrecorded) {
                LOG.warn(
                        "CDS_EXPORT_CHECKPOINT_REJECTION_UNRECORDED run={} epoch={} failure={}",
                        lease.runId(),
                        prior.epoch,
                        unrecorded.getClass().getSimpleName());
            }
        }

        private static CdsExportStagedFileDto copy(
                CdsExportStagingService from,
                CdsExportStagedFileDto member,
                CdsExportStagingService to)
                throws IOException {
            var copy =
                    to.stage(
                            member.purpose(),
                            output -> {
                                InputStream input;
                                try {
                                    input = from.open(member);
                                } catch (IOException failed) {
                                    throw new PriorUnreadable(failed);
                                }
                                byte[] buffer = new byte[65_536];
                                try (input) {
                                    while (true) {
                                        int count;
                                        try {
                                            count = input.read(buffer);
                                        } catch (IOException failed) {
                                            throw new PriorUnreadable(failed);
                                        }
                                        if (count < 0) break;
                                        output.write(buffer, 0, count);
                                    }
                                } finally {
                                    java.util.Arrays.fill(buffer, (byte) 0);
                                }
                            });
            // The earlier member was read to its authenticated end; the copy is the same bytes.
            if (!copy.plaintextSha256().equals(member.plaintextSha256())
                    || copy.plaintextBytes() != member.plaintextBytes()) {
                to.discardUnpublished(copy);
                throw new IOException("CDS_EXPORT_CHART_ADOPTION_INVALID");
            }
            return copy;
        }

        private static void discard(
                CdsExportStagingService store,
                List<CdsExportStagedFileDto> copies,
                Throwable primary) {
            for (var copy : copies.reversed())
                try {
                    store.discardUnpublished(copy);
                } catch (IOException | RuntimeException cleanup) {
                    if (cleanup != primary) primary.addSuppressed(cleanup);
                }
        }

        /** Closes every earlier attempt's open cursor. */
        @Override
        public void close() {
            for (var prior : priors)
                try {
                    prior.close();
                } catch (IOException cleanup) {
                    LOG.warn(
                            "CDS_EXPORT_CHECKPOINT_CURSOR_NOT_CLOSED run={} epoch={}",
                            lease.runId(),
                            prior.epoch);
                }
        }

        @Override
        public String toString() {
            return "CdsExportChartAdoptionService.Session[protected]";
        }
    }
}
