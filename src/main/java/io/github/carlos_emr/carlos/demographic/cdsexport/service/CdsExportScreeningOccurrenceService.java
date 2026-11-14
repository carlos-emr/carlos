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
 * Emits every captured native screening (non-immunization prevention) as one PastHealth
 * occurrence, inside the history source session that owns the PastHealth wire position. The
 * capture closure is re-proved from the frozen classification first: every screening of the
 * partition must be captured, every captured row must be witnessed, and every field must have
 * a disposition before any receipt is accepted. A current screening is never silently
 * omitted: it is emitted or the patient is held for review. A superseded or removed version is
 * accounted field by field and not exported (see {@link CdsExportPreventionVersions}).
 */
@Service
public class CdsExportScreeningOccurrenceService {
    private final CdsExportNativeCaptureService captures;
    private final CdsExportNativeRowReceiptReader witnesses;
    private final CdsExportImmunizationCaptureValidator closure;
    private final CdsExportPreventionConfigurationService configurations;
    private final CdsExportCapturedTextRowsReader rows;

    @Autowired
    public CdsExportScreeningOccurrenceService(
            CdsExportNativeCaptureService captures,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportImmunizationCaptureValidator closure,
            CdsExportPreventionConfigurationService configurations) {
        this(
                captures,
                witnesses,
                closure,
                configurations,
                new CdsExportCapturedTextRowsReader(captures));
    }

    CdsExportScreeningOccurrenceService(
            CdsExportNativeCaptureService captures,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportImmunizationCaptureValidator closure,
            CdsExportPreventionConfigurationService configurations,
            CdsExportCapturedTextRowsReader rows) {
        this.captures = Objects.requireNonNull(captures);
        this.witnesses = Objects.requireNonNull(witnesses);
        this.closure = Objects.requireNonNull(closure);
        this.configurations = Objects.requireNonNull(configurations);
        this.rows = Objects.requireNonNull(rows);
    }

    /**
     * Appends one PastHealth section per captured screening.
     *
     * @param index the retained index of the same capture; an applied import unit that names a
     *     screening row means the row was imported as something else, so it requires review
     * @return the number of screening occurrences emitted
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
        var category = CdsCategory.PAST_HEALTH;
        String parentTable = CdsExportPreventionSources.SCREENINGS,
                extensionTable = CdsExportPreventionSources.SCREENING_EXTENSIONS,
                precisionTable = CdsExportPreventionSources.SCREENING_PRECISION;
        if (!lease.document().command().categories().contains(category)
                || patientKey == null
                || !patientKey.matches("[0-9a-f]{64}")
                || !tables.equals(source.capture().tables().keySet())
                || !tables.containsAll(CdsExportPreventionSources.SCREENING_REQUIRED)
                || index == null
                || !index.sourceManifestSha256().equals(source.manifest().plaintextSha256()))
            scope();
        long parentRows = source.capture().tables().get(parentTable).rows();
        var selection =
                closure.verifyEmbedded(
                        lease, patient, tables, source, bounds.fields(), bounds.bytes(), control);
        var definitions =
                configurations.readEmbedded(
                        lease, patient, tables, source, bounds.fields(), bounds.bytes(), control);
        if (selection.patient() != patient.patient()
                || !selection.representable()
                || !selection.sourceManifestSha256().equals(source.manifest().plaintextSha256())
                || !definitions.sourceManifestSha256().equals(source.manifest().plaintextSha256())
                || !selection.configurationSha256().equals(definitions.configurationSha256())
                || !selection.catalogueSha256().equals(definitions.catalogueSha256())) scope();
        var screenings = selection.otherPreventions();
        var screeningIds = new HashSet<String>();
        for (int id : screenings) screeningIds.add(Integer.toString(id));
        // A screening row named by an applied import unit was imported as another category's
        // occurrence (a vaccine typed as a screening); emitting it here could duplicate it.
        for (String address : index.appliedUnits())
            for (var reference : index.references().getOrDefault(address, List.of()))
                if (reference.table().equals("preventions")
                        && screeningIds.contains(reference.id())) ambiguous();
        var seen = new HashSet<Integer>();
        // 0 emitted, 1 assessed fields, 2 superseded versions (accounted, not exported)
        long[] counts = new long[3];
        // Extension and precision rows the capture holds [0, 1] and those taken by the window of
        // their screening, over every window [2, 3].
        long[] children = new long[4];
        // A screening shown only inside a program is written when a fresh check says every
        // exporting identity works in it; a program nobody exporting works in holds the patient,
        // and each program so granted is checked again after the screenings are written.
        var granted = new TreeSet<String>();
        java.util.function.Predicate<String> readable =
                program -> {
                    if (!control.restrictedProgramReadable(patient.patient(), category, program))
                        throw CdsExportPreventionAccessService.hold();
                    granted.add(program);
                    return true;
                };
        // The chart is read whole when it fits one window, as it always was, and window by window
        // otherwise (CdsExportSectionWindowPlanner): the child rows, witnesses and rows of one
        // window are held at a time.
        CdsExportSectionWindowPlanner.forEachWindow(
                parentRows,
                bounds.window(),
                owners -> {
                    var listed =
                            rows.forEach(
                                    lease,
                                    patient,
                                    tables,
                                    parentTable,
                                    source.manifest(),
                                    bounds.fields(),
                                    bounds.bytes(),
                                    4096,
                                    control,
                                    row -> owners.add(row.fields().get("id")));
                    if (!listed.equals(source.capture())) scope();
                },
                (window, budget) -> {
        var extensions = new HashMap<String, List<CdsExportCapturedTextRowDto>>();
        var precision = new HashMap<String, List<CdsExportCapturedTextRowDto>>();
        var auxiliaryKeys = new HashSet<String>();
        // 0, 1 unused; 2 extension rows and 3 precision rows the capture holds; 4, 5 those taken
        long[] lookup = new long[6];
        var auxiliary =
                rows.forEachTable(
                        lease,
                        patient,
                        tables,
                        Set.of(extensionTable, precisionTable),
                        source.manifest(),
                        bounds.fields(),
                        bounds.bytes(),
                        67_108_864,
                        control,
                        row -> {
                            // A window found too large keeps nothing more; it is read again
                            // with fewer screenings.
                            if (budget.exceeded()) return;
                            boolean extension = row.table().equals(extensionTable);
                            if (!extension && !row.table().equals(precisionTable)) scope();
                            String owner =
                                    row.fields().get(extension ? "prevention_id" : "table_id");
                            if (!screenings.contains(integer(owner))) scope();
                            lookup[extension ? 2 : 3]++;
                            if (!window.contains(owner)) return;
                            long size = 0;
                            for (var value : row.fields().values())
                                if (value != null)
                                    size += value.getBytes(StandardCharsets.UTF_8).length + 64L;
                            if (!budget.add(0, size)) return;
                            // A repeated child row id is refused, whichever window meets it.
                            if (!auxiliaryKeys.add(key(row.table(), row.fields().get("id"))))
                                scope();
                            (extension ? extensions : precision)
                                    .computeIfAbsent(owner, ignored -> new ArrayList<>())
                                    .add(row);
                            lookup[extension ? 4 : 5]++;
                        });
        if (budget.exceeded()) return false;
        if (!auxiliary.equals(source.capture())
                || lookup[2] != auxiliary.tables().get(extensionTable).rows()
                || lookup[3] != auxiliary.tables().get(precisionTable).rows()) scope();
        var exact = new HashMap<String, CdsExportNativeRowReceiptDto>();
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
                            if (!CdsExportPreventionSources.SCREENING_TABLES.contains(
                                    witness.table())) return;
                            boolean parent = witness.table().equals(parentTable);
                            // The witness of a screening of this window, or of a child row
                            // this window took.
                            if (parent
                                    ? !window.contains(witness.id())
                                    : !auxiliaryKeys.contains(
                                            key(witness.table(), witness.id()))) return;
                            long size =
                                    1024L
                                            + (witness.state() == null
                                                    ? 0
                                                    : witness.state().columns().size() * 256L);
                            if (parent ? !budget.owner(size) : !budget.add(0, size)) return;
                            if (exact.putIfAbsent(key(witness.table(), witness.id()), witness)
                                    != null) scope();
                        });
        if (budget.exceeded()) return false;
        if (!scan.sourceManifestSha256().equals(source.manifest().plaintextSha256())
                || exact.size() != window.size() + auxiliaryKeys.size()) scope();
        children[0] = lookup[2];
        children[1] = lookup[3];
        children[2] = Math.addExact(children[2], lookup[4]);
        children[3] = Math.addExact(children[3], lookup[5]);
        long[] emitted = {0};
        var finished =
                rows.forEach(
                        lease,
                        patient,
                        tables,
                        parentTable,
                        source.manifest(),
                        bounds.fields(),
                        bounds.bytes(),
                        4096,
                        control,
                        row -> {
                            patient.require(lease);
                            control.checkpoint();
                            String id = row.fields().get("id");
                            if (!window.contains(id)) {
                                // A row with no id belongs to no window: refused.
                                if (id == null) scope();
                                return;
                            }
                            emitted[0]++;
                            if (!seen.add(integer(id)) || !screenings.contains(integer(id)))
                                scope();
                            var child = extensions.remove(id);
                            if (child == null) child = List.of();
                            var dates = precision.remove(id);
                            if (dates == null) dates = List.of();
                            var current = new ArrayList<CdsExportCapturedTextRowDto>();
                            current.add(row);
                            current.addAll(child);
                            current.addAll(dates);
                            for (var entry : current) {
                                var witness =
                                        exact.remove(key(entry.table(), entry.fields().get("id")));
                                if (witness == null) scope();
                                CdsExportImmunizationProjectionEvidence.verifyCurrent(
                                        entry, witness);
                            }
                            if (CdsExportPreventionVersions.superseded(row)) {
                                // An edited or deleted screening version: accounted, never
                                // exported, and no reason to hold the patient.
                                var retired = CdsExportPreventionVersions.dispositions(current);
                                new CdsExportNativeFieldAssessmentValidator()
                                        .requireRepresentable(
                                                current, retired, 1_000_000, control::checkpoint);
                                if (retired.size() != 16 + 4L * child.size() + 5L * dates.size())
                                    scope();
                                counts[1] = Math.addExact(counts[1], retired.size());
                                counts[2]++;
                                return;
                            }
                            var mapping =
                                    new CdsExportScreeningMapper()
                                            .map(
                                                    patient.patient(),
                                                    row,
                                                    child,
                                                    dates,
                                                    definitions,
                                                    readable);
                            if (!mapping.representable()) review();
                            new CdsExportNativeFieldAssessmentValidator()
                                    .requireRepresentable(
                                            current,
                                            mapping.fields(),
                                            1_000_000,
                                            control::checkpoint);
                            if (mapping.fields().size()
                                    != 16 + 4L * child.size() + 5L * dates.size()) scope();
                            counts[1] = Math.addExact(counts[1], mapping.fields().size());
                            var identity =
                                    new CdsExportIdentityDto(
                                            patientKey,
                                            category,
                                            CdsImportKeys.hash(
                                                    "carlos-native-occurrence-v1",
                                                    "preventions",
                                                    id));
                            control.authorizeCategory(patient.patient(), category, false);
                            xml.emit(
                                    new CdsExportXmlComposer.Section(
                                            identity,
                                            writer ->
                                                    new CdsExportScreeningXmlWriter()
                                                            .write(mapping.value(), writer)));
                            output.accept(
                                    new CdsExportClinicalOccurrenceService.Receipt(
                                            identity,
                                            index.sourceManifestSha256(),
                                            "preventions",
                                            id,
                                            null,
                                            mapping.fields(),
                                            Set.of(),
                                            Set.of()));
                            counts[0]++;
                        });
        // Every witness and child row this window loaded was used by one of its screenings.
        if (!finished.equals(source.capture())
                || !extensions.isEmpty()
                || !precision.isEmpty()
                || !exact.isEmpty()
                || emitted[0] != window.size()) scope();
        return true;
                });
        // Every child row was taken by the window of its screening: one whose screening is in no
        // window is taken by none and makes the count fall short.
        long expected =
                Math.addExact(
                        Math.multiplyExact(Math.addExact(counts[0], counts[2]), 16),
                        Math.addExact(
                                Math.multiplyExact(children[0], 4),
                                Math.multiplyExact(children[1], 5)));
        if (!seen.equals(screenings)
                || children[2] != children[0]
                || children[3] != children[1]
                || Math.addExact(counts[0], counts[2]) != parentRows
                || counts[1] != expected) scope();
        for (String program : granted)
            if (!control.restrictedProgramReadable(patient.patient(), category, program))
                throw new SecurityException("CDS_EXPORT_ACCESS_CHANGED");
        captures.authorizeSources(lease, patient, tables, control);
        patient.require(lease);
        control.checkpoint();
        return counts[0];
    }

    private static String key(String table, String id) {
        return table + "/" + id;
    }

    private static int integer(String text) throws IOException {
        try {
            int value = Integer.parseInt(text);
            if (!Integer.toString(value).equals(text)) scope();
            return value;
        } catch (NumberFormatException failed) {
            throw new IOException("CDS_EXPORT_SCREENING_OCCURRENCE_SCOPE_INVALID");
        }
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_SCREENING_OCCURRENCE_SCOPE_INVALID");
    }

    private static void review() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.UNMAPPED_DATA);
    }

    private static void ambiguous() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.AMBIGUOUS_SOURCE);
    }
}
