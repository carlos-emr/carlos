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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Emits every captured current Disease Registry entry and coded episode as one ProblemList
 * occurrence, inside the history source session that owns the ProblemList wire position, after
 * the CPP Concerns items. Every captured entry, episode and start-date precision row must be
 * witnessed and match its witness, every field gets a disposition before a receipt is accepted,
 * and every captured code-table row must belong to an exported item. A current entry is never
 * silently omitted: it is emitted or the patient is held for review. A deleted entry or episode is
 * accounted field by field and not exported (see {@link CdsExportProblemRegistrySources}).
 *
 * <p>The CARLOS CDS importer never writes {@code dxresearch} or {@code Episode}; an applied import
 * unit that names such a row would mean the row is already exported as something else, so it is
 * held as an ambiguous source rather than exported twice.
 */
@Service
public class CdsExportProblemRegistryOccurrenceService {
    private static final Set<String> PARENTS =
            Set.of(CdsExportProblemRegistrySources.REGISTRY, CdsExportProblemRegistrySources.EPISODES);
    private static final Set<String> WITNESSED =
            Set.of(
                    CdsExportProblemRegistrySources.REGISTRY,
                    CdsExportProblemRegistrySources.PRECISION,
                    CdsExportProblemRegistrySources.EPISODES);
    private final CdsExportNativeCaptureService captures;
    private final CdsExportNativeRowReceiptReader witnesses;
    private final CdsExportCapturedTextRowsReader rows;

    @Autowired
    public CdsExportProblemRegistryOccurrenceService(
            CdsExportNativeCaptureService captures, CdsExportNativeRowReceiptReader witnesses) {
        this(captures, witnesses, new CdsExportCapturedTextRowsReader(captures));
    }

    CdsExportProblemRegistryOccurrenceService(
            CdsExportNativeCaptureService captures,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportCapturedTextRowsReader rows) {
        this.captures = Objects.requireNonNull(captures);
        this.witnesses = Objects.requireNonNull(witnesses);
        this.rows = Objects.requireNonNull(rows);
    }

    /**
     * Appends one ProblemList section per current registry entry and episode.
     *
     * @param index the retained index of the same capture
     * @return the number of occurrences emitted
     */
    public long append(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            String patientKey,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            CdsExportRetainedIndexDto index,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            CdsExportXmlComposer.SectionEmitter xml,
            CdsExportClinicalOccurrenceService.Receipts output)
            throws IOException {
        Objects.requireNonNull(xml);
        long[] emitted = {-1};
        xml.source(
                emitter ->
                        emitted[0] =
                                assemble(
                                        lease,
                                        patient,
                                        patientKey,
                                        requiredTables,
                                        source,
                                        index,
                                        bounds,
                                        control,
                                        emitter,
                                        output));
        if (emitted[0] < 0) scope();
        return emitted[0];
    }

    private long assemble(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            String patientKey,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            CdsExportRetainedIndexDto index,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            CdsExportXmlComposer.SectionEmitter xml,
            CdsExportClinicalOccurrenceService.Receipts output)
            throws IOException {
        Objects.requireNonNull(output);
        Objects.requireNonNull(bounds);
        patient.require(lease);
        control.checkpoint();
        var tables = Set.copyOf(requiredTables);
        var category = CdsCategory.PROBLEM_LIST;
        if (!lease.document().command().categories().contains(category)
                || patientKey == null
                || !patientKey.matches("[0-9a-f]{64}")
                || !tables.equals(source.capture().tables().keySet())
                || !tables.containsAll(CdsExportProblemRegistrySources.TABLES)
                || index == null
                || !index.sourceManifestSha256().equals(source.manifest().plaintextSha256()))
            scope();
        var captured = source.capture().tables();
        long parentRows =
                Math.addExact(
                        captured.get(CdsExportProblemRegistrySources.REGISTRY).rows(),
                        captured.get(CdsExportProblemRegistrySources.EPISODES).rows());
        if (parentRows > bounds.nativeRows()) limit();
        for (String address : index.appliedUnits())
            for (var reference : index.references().getOrDefault(address, List.of()))
                if (PARENTS.contains(reference.table())) ambiguous();
        var precision = new HashMap<String, List<CdsExportCapturedTextRowDto>>();
        var references = new HashMap<List<String>, List<CdsExportCapturedTextRowDto>>();
        var auxiliaryKeys = new HashSet<String>();
        long[] lookup = new long[4];
        var auxiliary =
                rows.forEachTable(
                        lease,
                        patient,
                        tables,
                        Set.of(
                                CdsExportProblemRegistrySources.PRECISION,
                                CdsExportProblemRegistrySources.REFERENCES),
                        source.manifest(),
                        bounds.fields(),
                        bounds.bytes(),
                        262_144,
                        control,
                        row -> {
                            if (++lookup[0] > bounds.lookupRows()) limit();
                            for (var value : row.fields().values())
                                if (value != null)
                                    lookup[1] =
                                            Math.addExact(
                                                    lookup[1],
                                                    value.getBytes(StandardCharsets.UTF_8).length
                                                            + 64L);
                            if (lookup[1] > bounds.indexBytes()
                                    || !auxiliaryKeys.add(key(row.table(), row.recordKey())))
                                limit();
                            if (row.table().equals(CdsExportProblemRegistrySources.PRECISION)) {
                                precision
                                        .computeIfAbsent(
                                                row.fields().get("table_id"),
                                                ignored -> new ArrayList<>())
                                        .add(row);
                                lookup[2]++;
                            } else if (row.table()
                                    .equals(CdsExportProblemRegistrySources.REFERENCES)) {
                                references
                                        .computeIfAbsent(
                                                lookupKey(
                                                        row.fields().get("coding_system"),
                                                        row.fields().get("code")),
                                                ignored -> new ArrayList<>())
                                        .add(row);
                                lookup[3]++;
                            } else scope();
                        });
        if (!auxiliary.equals(source.capture())
                || lookup[2] != captured.get(CdsExportProblemRegistrySources.PRECISION).rows()
                || lookup[3] != captured.get(CdsExportProblemRegistrySources.REFERENCES).rows())
            scope();
        var exact = new HashMap<String, CdsExportNativeRowReceiptDto>();
        long[] witnessBytes = {lookup[1]};
        var scan =
                witnesses.scan(
                        lease,
                        patient,
                        tables,
                        source,
                        bounds.fields(),
                        bounds.bytes(),
                        control,
                        witness -> {
                            if (!WITNESSED.contains(witness.table())) return;
                            if (exact.size() >= bounds.nativeRows() + (long) bounds.lookupRows())
                                limit();
                            witnessBytes[0] =
                                    Math.addExact(
                                            witnessBytes[0],
                                            1024L
                                                    + (witness.state() == null
                                                            ? 0
                                                            : witness.state().columns().size()
                                                                    * 256L));
                            if (witnessBytes[0] > bounds.indexBytes()
                                    || exact.putIfAbsent(
                                                    key(witness.table(), witness.recordKey()),
                                                    witness)
                                            != null) limit();
                        });
        if (!scan.sourceManifestSha256().equals(source.manifest().plaintextSha256())
                || exact.size() != Math.addExact(parentRows, lookup[2])) scope();
        var seen = new HashSet<String>();
        var used = new HashSet<List<String>>();
        // 0 emitted, 1 deleted (accounted, not exported)
        long[] counts = new long[2];
        var mapper = new CdsExportProblemRegistryMapper();
        var finished =
                rows.forEachTable(
                        lease,
                        patient,
                        tables,
                        PARENTS,
                        source.manifest(),
                        bounds.fields(),
                        bounds.bytes(),
                        67_108_864,
                        control,
                        row -> {
                            patient.require(lease);
                            control.checkpoint();
                            boolean registry =
                                    row.table().equals(CdsExportProblemRegistrySources.REGISTRY);
                            String id = row.fields().get(registry ? "dxresearch_no" : "id");
                            if (id == null || !seen.add(key(row.table(), id))) scope();
                            var dates = registry ? precision.remove(id) : null;
                            if (dates == null) dates = List.of();
                            var current = new ArrayList<CdsExportCapturedTextRowDto>();
                            current.add(row);
                            current.addAll(dates);
                            for (var entry : current) {
                                var witness = exact.remove(key(entry.table(), entry.recordKey()));
                                if (witness == null) scope();
                                verify(entry, witness);
                            }
                            boolean removed = CdsExportProblemRegistrySources.removed(row);
                            var lookupKey =
                                    registry
                                            ? lookupKey(
                                                    row.fields().get("coding_system"),
                                                    row.fields().get("dxresearch_code"))
                                            : lookupKey(
                                                    row.fields().get("codingSystem"),
                                                    row.fields().get("code"));
                            var named =
                                    removed
                                            ? List.<CdsExportCapturedTextRowDto>of()
                                            : references.getOrDefault(lookupKey, List.of());
                            var mapping =
                                    registry
                                            ? mapper.mapRegistry(
                                                    patient.patient(), row, dates, named)
                                            : mapper.mapEpisode(patient.patient(), row, named);
                            new CdsExportNativeFieldAssessmentValidator()
                                    .requireRepresentable(
                                            mapping.rows(),
                                            mapping.fields(),
                                            1_000_000,
                                            control::checkpoint);
                            if (mapping.exported() == removed) scope();
                            if (removed) {
                                counts[1]++;
                                return;
                            }
                            if (!named.isEmpty()) used.add(lookupKey);
                            var identity =
                                    new CdsExportIdentityDto(
                                            patientKey,
                                            category,
                                            CdsImportKeys.hash(
                                                    "carlos-native-occurrence-v1",
                                                    row.table(),
                                                    id));
                            control.authorizeCategory(patient.patient(), category, false);
                            xml.emit(
                                    new CdsExportXmlComposer.Section(
                                            identity,
                                            writer ->
                                                    new CdsExportNativeHistoryXmlWriter()
                                                            .write(mapping.value(), writer)));
                            output.accept(
                                    new CdsExportClinicalOccurrenceService.Receipt(
                                            identity,
                                            index.sourceManifestSha256(),
                                            row.table(),
                                            id,
                                            null,
                                            mapping.fields(),
                                            Set.of(),
                                            Set.of()));
                            counts[0]++;
                        });
        if (!finished.equals(source.capture())
                || !precision.isEmpty()
                || !exact.isEmpty()
                || !used.containsAll(references.keySet())
                || seen.size() != parentRows
                || Math.addExact(counts[0], counts[1]) != parentRows) scope();
        captures.authorizeSources(lease, patient, tables, control);
        patient.require(lease);
        control.checkpoint();
        return counts[0];
    }

    /** The captured row and its witness carry the same identity and every same field value. */
    static void verify(CdsExportCapturedTextRowDto row, CdsExportNativeRowReceiptDto witness)
            throws IOException {
        if (witness.state() == null
                || witness.partial()
                || !row.exactNumbers().isEmpty()
                || !witness.table().equals(row.table())
                || !witness.recordKey().equals(row.recordKey())
                || !witness.state().table().equals(row.table())
                || !witness.state().digest().equals(witness.digest())
                || !row.fields()
                        .keySet()
                        .equals(
                                Set.copyOf(
                                        witness.state().columns().stream()
                                                .map(CdsNativeRowDigest.Column::name)
                                                .toList()))) scope();
        String id =
                row.fields()
                        .get(
                                row.table().equals(CdsExportProblemRegistrySources.REGISTRY)
                                        ? "dxresearch_no"
                                        : "id");
        if (!witness.id().equals(id) || !witness.state().id().equals(id)) scope();
        for (var column : witness.state().columns())
            if (!CdsExportImmunizationProjectionEvidence.matches(
                    witness.state(), column.name(), row.fields().get(column.name()))) scope();
    }

    private static List<String> lookupKey(String system, String code) {
        return Arrays.asList(system, code);
    }

    private static String key(String table, String recordKey) {
        return table + "/" + recordKey;
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_PROBLEM_REGISTRY_OCCURRENCE_SCOPE_INVALID");
    }

    private static void limit() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.RESOURCE_LIMIT);
    }

    private static void ambiguous() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.AMBIGUOUS_SOURCE);
    }
}
