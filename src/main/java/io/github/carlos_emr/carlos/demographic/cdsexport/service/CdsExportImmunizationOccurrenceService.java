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
 * Assembles the selected frozen vaccine occurrences through the same receipt, retained-field
 * and XML pipeline as allergies and medications. Every parent, child and retained source field
 * must be accounted for before the private result may reach subsequent coverage checks.
 */
@Service
public class CdsExportImmunizationOccurrenceService
        implements CdsExportCategoryOccurrenceAssembler {
    @Override
    public CdsCategory category() {
        return CdsCategory.IMMUNIZATIONS;
    }

    private final CdsExportRetainedValuesService values;
    private final CdsExportRetainedReceiptVerificationService verification;
    private final CdsExportNativeRowReceiptReader witnesses;
    private final CdsExportNativeCaptureService captures;
    private final CdsExportImmunizationCaptureValidator closure;
    private final CdsExportPreventionConfigurationService configurations;
    private final CdsExportCapturedTextRowsReader rows;
    private final CdsExportClinicalZoneService zones;

    public CdsExportImmunizationOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures,
            CdsExportImmunizationCaptureValidator closure,
            CdsExportPreventionConfigurationService configurations) {
        this(
                values,
                verification,
                witnesses,
                captures,
                closure,
                configurations,
                new CdsExportCapturedTextRowsReader(captures));
    }

    @Autowired
    public CdsExportImmunizationOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures,
            CdsExportImmunizationCaptureValidator closure,
            CdsExportPreventionConfigurationService configurations,
            CdsExportClinicalZoneService zones) {
        this(
                values,
                verification,
                witnesses,
                captures,
                closure,
                configurations,
                new CdsExportCapturedTextRowsReader(captures),
                zones);
    }

    CdsExportImmunizationOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures,
            CdsExportImmunizationCaptureValidator closure,
            CdsExportPreventionConfigurationService configurations,
            CdsExportCapturedTextRowsReader rows) {
        this(
                values,
                verification,
                witnesses,
                captures,
                closure,
                configurations,
                rows,
                new CdsExportClinicalZoneService(captures));
    }

    CdsExportImmunizationOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures,
            CdsExportImmunizationCaptureValidator closure,
            CdsExportPreventionConfigurationService configurations,
            CdsExportCapturedTextRowsReader rows,
            CdsExportClinicalZoneService zones) {
        this.values = Objects.requireNonNull(values);
        this.verification = Objects.requireNonNull(verification);
        this.witnesses = Objects.requireNonNull(witnesses);
        this.captures = Objects.requireNonNull(captures);
        this.closure = Objects.requireNonNull(closure);
        this.configurations = Objects.requireNonNull(configurations);
        this.rows = Objects.requireNonNull(rows);
        this.zones = Objects.requireNonNull(zones);
    }

    public CdsExportClinicalOccurrenceService.Result append(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            String patientKey,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            CdsExportStagedFileDto payloadManifest,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            CdsExportXmlComposer.SectionEmitter xml,
            CdsExportClinicalOccurrenceService.Receipts output)
            throws IOException {
        Objects.requireNonNull(xml);
        var result = new CdsExportClinicalOccurrenceService.Result[1];
        xml.source(
                emitter ->
                        result[0] =
                                assemble(
                                        lease,
                                        patient,
                                        patientKey,
                                        requiredTables,
                                        source,
                                        payloadManifest,
                                        bounds,
                                        control,
                                        emitter,
                                        output));
        return Objects.requireNonNull(result[0]);
    }

    private CdsExportClinicalOccurrenceService.Result assemble(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            String patientKey,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            CdsExportStagedFileDto payloadManifest,
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
        var category = CdsCategory.IMMUNIZATIONS;
        if (!lease.document().command().categories().contains(category)
                || patientKey == null
                || !patientKey.matches("[0-9a-f]{64}")
                || !tables.equals(source.capture().tables().keySet())
                || !tables.containsAll(CdsExportImmunizationCaptureValidator.REQUIRED)) scope();
        if (source.capture().tables().get("preventions").rows() > bounds.nativeRows()) limit();
        var zone =
                tables.contains("clinical_timezone")
                        ? zones.read(
                                lease,
                                patient,
                                tables,
                                source,
                                bounds.fields(),
                                bounds.bytes(),
                                control)
                        : null;
        if (zone != null
                && !zone.sourceManifestSha256().equals(source.manifest().plaintextSha256()))
            scope();
        new CdsExportMergeScopeValidator(rows)
                .requireUnmerged(
                        lease, patient, tables, source, bounds.fields(), bounds.bytes(), control);
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
        // 0 emitted, 1 imported, 2 native-only, 3 retained fields, 4 assessed fields, 5 superseded
        long[] counts = new long[6];
        values.withValues(
                lease,
                patient,
                tables,
                source.manifest(),
                payloadManifest,
                bounds.fields(),
                bounds.bytes(),
                bounds.indexRows(),
                bounds.indexBytes(),
                control,
                (index, retained, streams) -> {
                    if (!index.sourceManifestSha256().equals(source.manifest().plaintextSha256()))
                        scope();
                    var checked =
                            verification.verify(
                                    lease,
                                    patient,
                                    tables,
                                    source,
                                    index,
                                    bounds.fields(),
                                    bounds.bytes(),
                                    1_000_000,
                                    control);
                    var imported = new HashMap<String, String>();
                    for (var unit : index.tables().get("cds_import_unit").values())
                        if (category.wireName().equals(unit.fields().get("category"))) {
                            control.checkpoint();
                            String address =
                                    unit.fields().get("run_id")
                                            + ":"
                                            + unit.fields().get("unit_key");
                            if (!index.appliedUnits().contains(address)) continue;
                            var refs =
                                    index.references().get(address).stream()
                                            .filter(ref -> ref.table().equals("preventions"))
                                            .toList();
                            if (refs.size() != 1) review();
                            if (imported.putIfAbsent(refs.getFirst().id(), address) != null)
                                ambiguous();
                        }
                    var extensions = new HashMap<String, List<CdsExportCapturedTextRowDto>>();
                    var precision = new HashMap<String, List<CdsExportCapturedTextRowDto>>();
                    var auxiliaryKeys = new HashSet<String>();
                    long[] lookup = new long[4];
                    var auxiliary =
                            rows.forEachTable(
                                    lease,
                                    patient,
                                    tables,
                                    Set.of("preventionsExt", "prevention_precision"),
                                    source.manifest(),
                                    bounds.fields(),
                                    bounds.bytes(),
                                    67_108_864,
                                    control,
                                    row -> {
                                        if (++lookup[0] > bounds.lookupRows()) limit();
                                        for (var value : row.fields().values())
                                            if (value != null)
                                                lookup[1] =
                                                        Math.addExact(
                                                                lookup[1],
                                                                value.getBytes(
                                                                                        StandardCharsets
                                                                                                .UTF_8)
                                                                                .length
                                                                        + 64L);
                                        if (lookup[1] > bounds.indexBytes()
                                                || !auxiliaryKeys.add(
                                                        key(
                                                                physical(row.table()),
                                                                row.fields().get("id")))) limit();
                                        boolean extension = row.table().equals("preventionsExt");
                                        if (!extension
                                                && !row.table().equals("prevention_precision"))
                                            scope();
                                        String owner =
                                                row.fields()
                                                        .get(
                                                                extension
                                                                        ? "prevention_id"
                                                                        : "table_id");
                                        if (!selection.immunizations().contains(integer(owner)))
                                            scope();
                                        (extension ? extensions : precision)
                                                .computeIfAbsent(
                                                        owner, ignored -> new ArrayList<>())
                                                .add(row);
                                        lookup[extension ? 2 : 3]++;
                                    });
                    if (!auxiliary.equals(source.capture())
                            || lookup[2] != auxiliary.tables().get("preventionsExt").rows()
                            || lookup[3] != auxiliary.tables().get("prevention_precision").rows())
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
                                        String key = key(witness.table(), witness.id());
                                        if (!witness.table().equals("preventions")
                                                && !auxiliaryKeys.contains(key)) return;
                                        if (exact.size()
                                                >= bounds.nativeRows() + (long) bounds.lookupRows())
                                            limit();
                                        witnessBytes[0] =
                                                Math.addExact(
                                                        witnessBytes[0],
                                                        1024L
                                                                + (witness.state() == null
                                                                        ? 0
                                                                        : witness.state()
                                                                                        .columns()
                                                                                        .size()
                                                                                * 256L));
                                        if (witnessBytes[0] > bounds.indexBytes()
                                                || exact.putIfAbsent(key, witness) != null) limit();
                                    });
                    if (!scan.sourceManifestSha256().equals(source.manifest().plaintextSha256())
                            || exact.size()
                                    != source.capture().tables().get("preventions").rows()
                                            + auxiliaryKeys.size()) scope();
                    var usedFields = new HashSet<String>();
                    var seen = new HashSet<Integer>();
                    // A vaccine shown only inside a program is written when a fresh check says
                    // every exporting identity works in it; a program nobody exporting works in
                    // holds the patient, and each program so granted is checked again after the
                    // vaccines are written.
                    var granted = new TreeSet<String>();
                    java.util.function.Predicate<String> readable =
                            program -> {
                                if (!control.restrictedProgramReadable(
                                        patient.patient(), category, program))
                                    throw CdsExportPreventionAccessService.hold();
                                granted.add(program);
                                return true;
                            };
                    var finished =
                            rows.forEach(
                                    lease,
                                    patient,
                                    tables,
                                    "preventions",
                                    source.manifest(),
                                    bounds.fields(),
                                    bounds.bytes(),
                                    4096,
                                    control,
                                    row -> {
                                        streams.requireActive();
                                        patient.require(lease);
                                        control.checkpoint();
                                        String id = row.fields().get("id");
                                        if (!seen.add(integer(id))
                                                || !selection.immunizations().contains(integer(id)))
                                            scope();
                                        var child = extensions.remove(id);
                                        if (child == null) child = List.of();
                                        var dates = precision.remove(id);
                                        if (dates == null) dates = List.of();
                                        var current = new ArrayList<CdsExportCapturedTextRowDto>();
                                        current.add(row);
                                        current.addAll(child);
                                        current.addAll(dates);
                                        var receiptRows =
                                                new ArrayList<CdsExportNativeRowReceiptDto>();
                                        for (var entry : current) {
                                            var witness =
                                                    exact.remove(
                                                            key(
                                                                    physical(entry.table()),
                                                                    entry.fields().get("id")));
                                            if (witness == null) scope();
                                            CdsExportImmunizationProjectionEvidence.verifyCurrent(
                                                    entry, witness);
                                            receiptRows.add(witness);
                                        }
                                        String importedUnit = imported.remove(id);
                                        if (importedUnit == null
                                                && CdsExportPreventionVersions.superseded(row)) {
                                            // An edited or deleted native version: accounted,
                                            // never exported, and no reason to hold the patient.
                                            var retired =
                                                    CdsExportPreventionVersions.dispositions(
                                                            current);
                                            new CdsExportNativeFieldAssessmentValidator()
                                                    .requireRepresentable(
                                                            current,
                                                            retired,
                                                            1_000_000,
                                                            control::checkpoint);
                                            if (retired.size()
                                                    != 16
                                                            + 4L * child.size()
                                                            + 5L * dates.size()) scope();
                                            counts[4] = Math.addExact(counts[4], retired.size());
                                            counts[5]++;
                                            return;
                                        }
                                        Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>
                                                fields;
                                        CdsExportXmlComposer.Content content;
                                        Set<String> kept = Set.of(), replaced = Set.of();
                                        if (importedUnit == null) {
                                            var mapping =
                                                    new CdsExportImmunizationMapper(
                                                                    zone == null
                                                                            ? null
                                                                            : zone.zone())
                                                            .map(
                                                                    patient.patient(),
                                                                    row,
                                                                    child,
                                                                    dates,
                                                                    definitions,
                                                                    readable);
                                            if (!mapping.nativeRowRepresentable()) review();
                                            fields = mapping.fields();
                                            content =
                                                    writer ->
                                                            new CdsExportImmunizationXmlWriter()
                                                                    .write(mapping.value(), writer);
                                        } else {
                                            var decision =
                                                    new CdsExportImmunizationFieldReconciler(
                                                                    zone == null
                                                                            ? null
                                                                            : zone.zone())
                                                            .reconcile(
                                                                    patient.patient(),
                                                                    index,
                                                                    importedUnit,
                                                                    checked,
                                                                    row,
                                                                    child,
                                                                    dates,
                                                                    receiptRows,
                                                                    definitions);
                                            if (!decision.mapping().nativeRowRepresentable())
                                                review();
                                            var plan =
                                                    new CdsExportImmunizationRetainedAssembler()
                                                            .prepare(
                                                                    patient.patient(),
                                                                    decision,
                                                                    index,
                                                                    checked,
                                                                    retained,
                                                                    streams,
                                                                    control::checkpoint);
                                            fields = decision.mapping().fields();
                                            content = plan.content();
                                            kept = plan.retainedFields();
                                            replaced = plan.replacedFields();
                                        }
                                        new CdsExportNativeFieldAssessmentValidator()
                                                .requireRepresentable(
                                                        current,
                                                        fields,
                                                        1_000_000,
                                                        control::checkpoint);
                                        if (fields.size()
                                                != 16 + 4L * child.size() + 5L * dates.size())
                                            scope();
                                        counts[4] = Math.addExact(counts[4], fields.size());
                                        for (String field : kept)
                                            if (!usedFields.add(field)) scope();
                                        for (String field : replaced)
                                            if (!usedFields.add(field)) scope();
                                        var identity =
                                                new CdsExportIdentityDto(
                                                        patientKey,
                                                        category,
                                                        CdsImportKeys.hash(
                                                                "carlos-native-occurrence-v1",
                                                                "preventions",
                                                                id));
                                        xml.emit(
                                                new CdsExportXmlComposer.Section(
                                                        identity, content));
                                        output.accept(
                                                new CdsExportClinicalOccurrenceService.Receipt(
                                                        identity,
                                                        index.sourceManifestSha256(),
                                                        "preventions",
                                                        id,
                                                        importedUnit,
                                                        fields,
                                                        kept,
                                                        replaced));
                                        counts[0]++;
                                        counts[importedUnit == null ? 2 : 1]++;
                                        counts[3] =
                                                Math.addExact(
                                                        counts[3],
                                                        kept.size() + (long) replaced.size());
                                    });
                    long expected =
                            Math.addExact(
                                    Math.multiplyExact(Math.addExact(counts[0], counts[5]), 16),
                                    Math.addExact(
                                            Math.multiplyExact(lookup[2], 4),
                                            Math.multiplyExact(lookup[3], 5)));
                    if (!finished.equals(source.capture())
                            || !seen.equals(selection.immunizations())
                            || !extensions.isEmpty()
                            || !precision.isEmpty()
                            || !exact.isEmpty()
                            || Math.addExact(counts[0], counts[5])
                                    != source.capture().tables().get("preventions").rows()
                            || counts[4] != expected) scope();
                    if (!imported.isEmpty()) review();
                    var expectedFields = new HashSet<String>();
                    for (var field : retained)
                        if (field.category() == category && !expectedFields.add(field.id()))
                            scope();
                    if (!usedFields.equals(expectedFields)) scope();
                    for (String program : granted)
                        if (!control.restrictedProgramReadable(
                                patient.patient(), category, program))
                            throw new SecurityException("CDS_EXPORT_ACCESS_CHANGED");
                    streams.requireActive();
                    captures.authorizeSources(lease, patient, tables, control);
                    patient.require(lease);
                    control.checkpoint();
                });
        return new CdsExportClinicalOccurrenceService.Result(
                source.manifest().plaintextSha256(),
                category,
                counts[0],
                counts[1],
                counts[2],
                counts[3]);
    }

    private static String physical(String table) {
        return table.equals("prevention_precision") ? "partial_date" : table;
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
            throw new IOException("CDS_EXPORT_IMMUNIZATION_OCCURRENCE_SCOPE_INVALID");
        }
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_IMMUNIZATION_OCCURRENCE_SCOPE_INVALID");
    }

    private static void limit() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.RESOURCE_LIMIT);
    }

    private static void review() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.UNMAPPED_DATA);
    }

    private static void ambiguous() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.AMBIGUOUS_SOURCE);
    }
}
