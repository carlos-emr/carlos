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

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import jakarta.persistence.EntityManagerFactory;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;

/**
 * One patient/owning-JPA-transaction context. Configuration bytes, vocabulary and classification
 * are read once within the same bounded source snapshot. Nothing survives transaction completion;
 * nested or foreign transactions cannot reuse this state. This is selection, not captured evidence
 * or permission. Finished encrypted source must independently reprove the complete partition.
 *
 * <p>When Immunizations or PastHealth is selected, a row the classification cannot place is an
 * immunization (so never a screening) if
 * an authenticated import receipt proves it was imported from a CDS Immunizations element. The
 * receipts are the patient's {@code cds_import_unit} rows read through their owning DAO in this
 * same snapshot, the rows the capture itself copies, so the finished-capture reclassification
 * reaches the same partition ({@link CdsExportImmunizationImportReceipts}).
 */
@Service
public class CdsExportPreventionSnapshotService {
    private final Object key = new Object();
    private final EntityManagerFactory factory;
    private final CdsExportPreventionConfigurationLoader loader;
    private final CVCImmunizationDao vaccines;
    private final CVCMappingDao mappings;
    private final PreventionDao preventions;
    private final CdsImportUnitDao units;
    private final io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineKeys keys;

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportPreventionSnapshotService(
            @Qualifier("entityManagerFactory") EntityManagerFactory factory,
            CdsExportPreventionConfigurationLoader loader,
            CVCImmunizationDao vaccines,
            CVCMappingDao mappings,
            PreventionDao preventions,
            CdsImportUnitDao units,
            @Qualifier("cdsExportProperties") java.util.Properties settings) {
        this(
                factory,
                loader,
                vaccines,
                mappings,
                preventions,
                Objects.requireNonNull(units),
                io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineKeys
                        .fromProperties(settings));
    }

    /** Without import receipts: a receipt-proven imported vaccine stays a classification review. */
    CdsExportPreventionSnapshotService(
            EntityManagerFactory factory,
            CdsExportPreventionConfigurationLoader loader,
            CVCImmunizationDao vaccines,
            CVCMappingDao mappings,
            PreventionDao preventions) {
        this(
                factory,
                loader,
                vaccines,
                mappings,
                preventions,
                null,
                (io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineKeys) null);
    }

    CdsExportPreventionSnapshotService(
            EntityManagerFactory factory,
            CdsExportPreventionConfigurationLoader loader,
            CVCImmunizationDao vaccines,
            CVCMappingDao mappings,
            PreventionDao preventions,
            CdsImportUnitDao units,
            io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineKeys keys) {
        this.factory = Objects.requireNonNull(factory);
        this.loader = Objects.requireNonNull(loader);
        this.vaccines = Objects.requireNonNull(vaccines);
        this.mappings = Objects.requireNonNull(mappings);
        this.preventions = Objects.requireNonNull(preventions);
        if ((units == null) != (keys == null))
            throw new IllegalArgumentException("CDS_EXPORT_PREVENTION_SELECTION_INVALID");
        this.units = units;
        this.keys = keys;
    }

    State prepare(int patient, Runnable checkpoint) throws IOException {
        return prepare(patient, checkpoint, false);
    }

    /**
     * @param receipts whether a prevention category (Immunizations or PastHealth) is selected,
     *     so the capture copies the patient's Immunizations import units and an import receipt may
     *     prove an unplaced row
     */
    State prepare(int patient, Runnable checkpoint, boolean receipts) throws IOException {
        Object owner = transaction();
        if (patient < 1 || checkpoint == null) invalid();
        var existing = TransactionSynchronizationManager.getResource(key);
        if (existing != null) {
            if (!(existing instanceof State)) invalid();
            var state = (State) existing;
            state.require(patient);
            if (state.receipts != receipts) invalid();
            return state;
        }
        checkpoint.run();
        if (preventions.hasCdsExportUnresolvedProgramScope(patient)) program();
        var validator = new CdsExportSourceSchemaValidator();
        validator.verify(
                "preventions_scope",
                preventions.readCdsExportSourceSchema(),
                CdsExportPreventionScopePlanner.COLUMNS);
        validator.verify(
                vaccines.readCdsExportSourceSchema(),
                Set.copyOf(CdsExportPatientTableReadSupport.Plan.CVC_IMMUNIZATION.columns()));
        validator.verify(
                mappings.readCdsExportSourceSchema(),
                Set.copyOf(CdsExportPatientTableReadSupport.Plan.CVC_MAPPING.columns()));
        byte[] xml = loader.load();
        boolean complete = false;
        try {
            var catalogue = new ArrayList<CdsExportCapturedTextRowDto>();
            catalogue.addAll(
                    read(
                            vaccines,
                            "CVCImmunization",
                            patient,
                            Set.copyOf(
                                    CdsExportPatientTableReadSupport.Plan.CVC_IMMUNIZATION
                                            .columns()),
                            checkpoint));
            catalogue.addAll(
                    read(
                            mappings,
                            "CVCMapping",
                            patient,
                            Set.copyOf(CdsExportPatientTableReadSupport.Plan.CVC_MAPPING.columns()),
                            checkpoint));
            var metadata =
                    read(
                            preventions,
                            "preventions_scope",
                            patient,
                            CdsExportPreventionScopePlanner.COLUMNS,
                            checkpoint);
            var definitions = CdsExportPreventionClassifier.forSelection(xml, catalogue);
            var selected =
                    new CdsExportPreventionScopePlanner()
                            .select(
                                    patient,
                                    definitions,
                                    metadata,
                                    receipts && units != null
                                            ? candidates ->
                                                    imported(patient, candidates, checkpoint)
                                            : CdsExportPreventionScopePlanner.ImportedImmunizations
                                                    .NONE);
            checkpoint.run();
            var state =
                    new State(
                            patient,
                            owner,
                            xml,
                            definitions.configurationSha256(),
                            definitions.catalogueSha256(),
                            selected,
                            receipts);
            TransactionSynchronizationManager.bindResource(key, state);
            try {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCompletion(int status) {
                                if (TransactionSynchronizationManager.getResource(key) == state)
                                    TransactionSynchronizationManager.unbindResource(key);
                                state.close();
                            }
                        });
            } catch (RuntimeException | Error failed) {
                TransactionSynchronizationManager.unbindResourceIfPossible(key);
                state.close();
                throw failed;
            }
            complete = true;
            return state;
        } finally {
            if (!complete) Arrays.fill(xml, (byte) 0);
        }
    }

    State current(int patient) {
        var value = TransactionSynchronizationManager.getResource(key);
        if (!(value instanceof State))
            throw new IllegalStateException("CDS_EXPORT_PREVENTION_SNAPSHOT_REQUIRED");
        var state = (State) value;
        state.require(patient);
        return state;
    }

    private Object transaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Integer.valueOf(TransactionDefinition.ISOLATION_REPEATABLE_READ)
                        .equals(
                                TransactionSynchronizationManager
                                        .getCurrentTransactionIsolationLevel()))
            throw new IllegalStateException("CDS_EXPORT_PREVENTION_SNAPSHOT_REQUIRED");
        Object holder = TransactionSynchronizationManager.getResource(factory);
        if (!(holder instanceof EntityManagerHolder)
                || !((EntityManagerHolder) holder).isSynchronizedWithTransaction())
            throw new IllegalStateException("CDS_EXPORT_PREVENTION_SNAPSHOT_REQUIRED");
        return holder;
    }

    final class State {
        private final int patient;
        private final Object owner;
        private final Thread thread = Thread.currentThread();
        private final byte[] xml;
        private final String configurationSha256, catalogueSha256;
        private final CdsExportPreventionScopePlanner.Selection selection;
        private final boolean receipts;
        private boolean closed;

        private State(
                int patient,
                Object owner,
                byte[] xml,
                String configurationSha256,
                String catalogueSha256,
                CdsExportPreventionScopePlanner.Selection selection,
                boolean receipts) {
            this.patient = patient;
            this.owner = owner;
            this.xml = xml;
            this.configurationSha256 = configurationSha256;
            this.catalogueSha256 = catalogueSha256;
            this.selection = selection;
            this.receipts = receipts;
        }

        private void require(int requested) {
            if (closed
                    || requested != patient
                    || thread != Thread.currentThread()
                    || transaction() != owner
                    || TransactionSynchronizationManager.getResource(key) != this)
                throw new IllegalStateException("CDS_EXPORT_PREVENTION_SNAPSHOT_SCOPE_INVALID");
        }

        InputStream xml() {
            require(patient);
            return new FilterInputStream(new ByteArrayInputStream(xml)) {
                private boolean streamClosed;

                private void check() {
                    if (streamClosed)
                        throw new IllegalStateException(
                                "CDS_EXPORT_PREVENTION_SNAPSHOT_SCOPE_INVALID");
                    require(patient);
                }

                @Override
                public int read() throws IOException {
                    check();
                    return in.read();
                }

                @Override
                public int read(byte[] data, int offset, int length) throws IOException {
                    check();
                    return in.read(data, offset, length);
                }

                @Override
                public long skip(long length) throws IOException {
                    check();
                    return in.skip(length);
                }

                @Override
                public int available() throws IOException {
                    check();
                    return in.available();
                }

                @Override
                public void close() throws IOException {
                    streamClosed = true;
                    in.close();
                }
            };
        }

        String configurationSha256() {
            require(patient);
            return configurationSha256;
        }

        String catalogueSha256() {
            require(patient);
            return catalogueSha256;
        }

        CdsExportPreventionScopePlanner.Selection selection() {
            require(patient);
            return selection;
        }

        Set<Integer> clinicalIds() {
            require(patient);
            if (!selection.representable())
                throw new CdsExportReviewException(CdsExportReviewException.Reason.UNMAPPED_DATA);
            return selection.immunizations();
        }

        /**
         * Non-immunization (screening) rows, captured only for PastHealth. An unclassifiable or
         * program-restricted row could be a screening, so it holds the patient for review here
         * too rather than letting a PastHealth-only export pass without it.
         */
        Set<Integer> screeningIds() {
            require(patient);
            if (!selection.representable())
                throw new CdsExportReviewException(CdsExportReviewException.Reason.UNMAPPED_DATA);
            return selection.otherPreventions();
        }

        private void close() {
            closed = true;
            Arrays.fill(xml, (byte) 0);
        }

        @Override
        public String toString() {
            return "CdsExportPreventionSnapshotService.State[protected]";
        }
    }

    private static List<CdsExportCapturedTextRowDto> read(
            CdsExportPatientTableReadDao source,
            String table,
            int patient,
            Set<String> columns,
            Runnable checkpoint)
            throws IOException {
        long count = source.countCdsExportRows(patient);
        if (count < 0 || count > 10_000) invalid();
        var result = new ArrayList<CdsExportCapturedTextRowDto>();
        Long after = null;
        long bytes = 0;
        while (true) {
            checkpoint.run();
            var page = source.findCdsExportRowsAfter(patient, after, 32);
            if (page.size() > 32) invalid();
            for (var row : page) {
                if (after != null && row.id() <= after
                        || !row.cells().keySet().equals(columns)
                        || result.size() >= 10_000) invalid();
                var fields = new TreeMap<String, String>();
                for (var entry : row.cells().entrySet()) {
                    var cell = entry.getValue();
                    if (cell.exactNumeric() != null || cell.bytes() > 4096) invalid();
                    if (cell.sqlNull()) {
                        fields.put(entry.getKey(), null);
                        continue;
                    }
                    if (cell.inlineBytes() == null) invalid();
                    byte[] value;
                    try (var input =
                            new CdsExportRawValueInputStream(
                                    cell,
                                    (offset, length) -> {
                                        throw new IOException(
                                                "CDS_EXPORT_PREVENTION_METADATA_NOT_INLINE");
                                    })) {
                        value = input.readNBytes(4097);
                        if (value.length != cell.bytes() || input.read() != -1) invalid();
                    }
                    bytes += value.length;
                    if (bytes > 4_194_304) invalid();
                    try {
                        fields.put(
                                entry.getKey(),
                                StandardCharsets.UTF_8
                                        .newDecoder()
                                        .onMalformedInput(CodingErrorAction.REPORT)
                                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                                        .decode(ByteBuffer.wrap(value))
                                        .toString());
                    } catch (CharacterCodingException failed) {
                        throw new IOException("CDS_EXPORT_PREVENTION_METADATA_UTF8_INVALID");
                    }
                }
                result.add(
                        new CdsExportCapturedTextRowDto(
                                table, CdsExportPatientTableSourceReader.key(row.id()), fields));
                after = row.id();
            }
            if (page.size() < 32) break;
        }
        if (result.size() != count) invalid();
        return List.copyOf(result);
    }

    /**
     * Candidate rows proved by the patient's applied Immunizations import receipts. The unit rows
     * are the ones the capture copies from this snapshot; a large sealed witness is read only for
     * a unit whose receipt names a candidate.
     */
    private Set<Integer> imported(int patient, Set<Integer> candidates, Runnable checkpoint)
            throws IOException {
        var proof = new CdsExportImmunizationImportReceipts.Proof(patient, candidates, keys);
        var categories = Set.of(io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory
                .IMMUNIZATIONS.wireName());
        String after = null;
        long rows = 0, bytes = 0;
        while (true) {
            checkpoint.run();
            var page = units.findCdsExportRetainedRowsAfter(patient, categories, after, 32);
            if (page.size() > 32) invalid();
            for (var row : page) {
                if (after != null && row.id().compareTo(after) <= 0
                        || !row.cells().keySet().equals(CdsExportImmunizationImportReceipts.COLUMNS)
                        || ++rows > 1_000_000) invalid();
                String references =
                        text(
                                patient,
                                categories,
                                row,
                                "target_references",
                                CdsExportImmunizationImportReceipts.MAX_REFERENCE_BYTES);
                String targetDigest = text(patient, categories, row, "target_digest", 64);
                Integer id =
                        proof.named(
                                text(patient, categories, row, "category", 255),
                                text(patient, categories, row, "state", 255),
                                references,
                                targetDigest,
                                !row.cells().get("native_baseline").sqlNull());
                if (id != null) {
                    String sealed =
                            text(
                                    patient,
                                    categories,
                                    row,
                                    "native_baseline",
                                    io.github.carlos_emr.carlos.demographic.cds.contract
                                            .CdsNativeBaselineCodec.MAX_SEALED_BYTES);
                    if (sealed != null) {
                        bytes += sealed.length();
                        if (bytes > 64L << 20) invalid();
                    }
                    proof.witness(id, references, targetDigest, sealed);
                }
                after = row.id();
            }
            if (page.size() < 32) break;
        }
        return proof.proven();
    }

    /**
     * Exact strict-UTF-8 text of one unit cell, or {@code null} for SQL NULL and for a value over
     * its bound: an oversized value cannot prove anything here or in the captured copy.
     */
    private String text(
            int patient,
            Set<String> categories,
            io.github.carlos_emr.carlos.commn.dto.CdsExportRawKeyRowDto row,
            String column,
            int maximum)
            throws IOException {
        var cell = row.cells().get(column);
        if (cell.sqlNull() || cell.bytes() > maximum) return null;
        byte[] value;
        try (var input =
                new CdsExportRawValueInputStream(
                        cell,
                        (offset, length) ->
                                units.readCdsExportRetainedChunk(
                                        patient, categories, row.id(), column, offset, length))) {
            value = input.readNBytes(maximum + 1);
            if (value.length != cell.bytes() || input.read() != -1) invalid();
        }
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value))
                    .toString();
        } catch (CharacterCodingException failed) {
            throw new IOException("CDS_EXPORT_PREVENTION_METADATA_UTF8_INVALID");
        }
    }

    private static void program() {
        throw new CdsExportReviewException(
                CdsExportReviewException.Reason.PREVENTION_PROGRAM_SCOPE);
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_PREVENTION_SELECTION_INVALID");
    }
}
