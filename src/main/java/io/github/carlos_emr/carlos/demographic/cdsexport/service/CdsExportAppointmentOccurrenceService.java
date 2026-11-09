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

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto;
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
 * Exact native/retained appointment assembly under one frozen generation. The clinical zone
 * and rules version come from that capture; no default JVM zone or mutable setting is read.
 * Archive/history and other unrepresented native facts remain explicit review gates.
 */
@Service
public class CdsExportAppointmentOccurrenceService implements CdsExportCategoryOccurrenceAssembler {
    @Override
    public CdsCategory category() {
        return CdsCategory.APPOINTMENTS;
    }

    private static final Set<String> REQUIRED =
            Set.of(
                    "appointment",
                    "appointmentArchive",
                    "appointment_status",
                    "provider",
                    "demographic",
                    "demographic_merged",
                    "cds_import_field",
                    "cds_import_unit",
                    "cds_import_preparation",
                    "cds_import_dependency",
                    "cds_import_patient",
                    "cds_import_run",
                    "clinical_timezone");
    private final CdsExportRetainedValuesService values;
    private final CdsExportRetainedReceiptVerificationService verification;
    private final CdsExportNativeRowReceiptReader witnesses;
    private final CdsExportNativeCaptureService captures;
    private final CdsExportCapturedTextRowsReader rows;
    private final CdsExportClinicalZoneService zones;

    @Autowired
    public CdsExportAppointmentOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures,
            CdsExportClinicalZoneService zones) {
        this(
                values,
                verification,
                witnesses,
                captures,
                new CdsExportCapturedTextRowsReader(captures),
                zones);
    }

    CdsExportAppointmentOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures,
            CdsExportCapturedTextRowsReader rows,
            CdsExportClinicalZoneService zones) {
        this.values = Objects.requireNonNull(values);
        this.verification = Objects.requireNonNull(verification);
        this.witnesses = Objects.requireNonNull(witnesses);
        this.captures = Objects.requireNonNull(captures);
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
        var category = CdsCategory.APPOINTMENTS;
        if (patientKey == null
                || !patientKey.matches("[0-9a-f]{64}")
                || !lease.document().command().categories().contains(category)
                || !tables.equals(source.capture().tables().keySet())
                || !tables.containsAll(REQUIRED)) scope();
        if (source.capture().tables().get("appointment").rows() > bounds.nativeRows()) limit();
        new CdsExportMergeScopeValidator(rows)
                .requireUnmerged(
                        lease, patient, tables, source, bounds.fields(), bounds.bytes(), control);
        var zone =
                zones.read(
                        lease, patient, tables, source, bounds.fields(), bounds.bytes(), control);
        if (!zone.sourceManifestSha256().equals(source.manifest().plaintextSha256())) scope();
        var clinicalZone = zone.zone();
        // The preceding full-source scan authenticates all fields and final authority before even
        // an adverse history result.
        if (source.capture().tables().get("appointmentArchive").rows() != 0) review();
        long[] counts = new long[4];
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
                                            .filter(ref -> ref.table().equals("appointment"))
                                            .toList();
                            if (refs.size() != 1) review();
                            if (imported.putIfAbsent(refs.getFirst().id(), address) != null)
                                ambiguous();
                        }
                    var lookupByKey = new HashMap<String, CdsExportCapturedTextRowDto>();
                    var providers = new HashMap<String, CdsExportCapturedTextRowDto>();
                    var statuses = new HashMap<String, List<CdsExportCapturedTextRowDto>>();
                    var demographic = new CdsExportCapturedTextRowDto[1];
                    long[] lookup = new long[5];
                    var first =
                            rows.forEachTable(
                                    lease,
                                    patient,
                                    tables,
                                    Set.of("provider", "appointment_status", "demographic"),
                                    source.manifest(),
                                    bounds.fields(),
                                    bounds.bytes(),
                                    1_048_576,
                                    control,
                                    row -> {
                                        if (++lookup[0] > bounds.lookupRows()) limit();
                                        lookup[1] =
                                                Math.addExact(
                                                        lookup[1],
                                                        1024L + row.fields().size() * 256L);
                                        for (String value : row.fields().values())
                                            if (value != null)
                                                lookup[1] =
                                                        Math.addExact(
                                                                lookup[1],
                                                                value.getBytes(
                                                                                        StandardCharsets
                                                                                                .UTF_8)
                                                                                .length
                                                                        + 64L);
                                        if (lookup[1] > bounds.indexBytes()) limit();
                                        if (!row.table().equals("demographic")
                                                && lookupByKey.putIfAbsent(
                                                                row.table() + "/" + row.recordKey(),
                                                                row)
                                                        != null) scope();
                                        switch (row.table()) {
                                            case "provider" -> {
                                                String id = row.fields().get("provider_no");
                                                if (id == null
                                                        || !row.fields()
                                                                .keySet()
                                                                .equals(
                                                                        CdsExportProviderRowDto
                                                                                .COLUMNS)
                                                        || !CdsExportPublicProviderSourceReader.key(
                                                                        id)
                                                                .equals(row.recordKey())
                                                        || providers.putIfAbsent(id, row) != null)
                                                    scope();
                                                lookup[2]++;
                                            }
                                            case "appointment_status" -> {
                                                String code = row.fields().get("status");
                                                if (code == null
                                                        || !row.fields()
                                                                .keySet()
                                                                .equals(
                                                                        Set.copyOf(
                                                                                CdsExportPatientTableReadSupport
                                                                                        .Plan
                                                                                        .APPOINTMENT_STATUSES
                                                                                        .columns())))
                                                    scope();
                                                statuses.computeIfAbsent(
                                                                code, ignored -> new ArrayList<>())
                                                        .add(row);
                                                lookup[3]++;
                                            }
                                            case "demographic" -> {
                                                if (demographic[0] != null
                                                        || !Integer.toString(patient.patient())
                                                                .equals(
                                                                        row.fields()
                                                                                .get(
                                                                                        "demographic_no")))
                                                    scope();
                                                demographic[0] = row;
                                                lookup[4]++;
                                            }
                                            default -> scope();
                                        }
                                    });
                    if (!first.equals(source.capture())
                            || lookup[2] != first.tables().get("provider").rows()
                            || lookup[3] != first.tables().get("appointment_status").rows()
                            || lookup[4] != 1
                            || lookup[4] != first.tables().get("demographic").rows()) scope();
                    var lookups =
                            new CdsExportAppointmentLookupsDto(
                                    index.sourceManifestSha256(), providers, statuses);
                    var exact = new HashMap<String, CdsExportNativeRowReceiptDto>();
                    long[] witnessBytes = {lookup[1]};
                    var receiptScan =
                            witnesses.scan(
                                    lease,
                                    patient,
                                    tables,
                                    source,
                                    bounds.fields(),
                                    bounds.bytes(),
                                    control,
                                    witness -> {
                                        if (!witness.table().equals("appointment")) return;
                                        if (exact.size() >= bounds.nativeRows()) limit();
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
                                                || exact.putIfAbsent(witness.id(), witness) != null)
                                            limit();
                                    });
                    if (!receiptScan.sourceManifestSha256().equals(index.sourceManifestSha256())
                            || exact.size() != source.capture().tables().get("appointment").rows())
                        scope();
                    var used = new HashSet<String>();
                    var seen = new HashSet<String>();
                    var actual =
                            rows.forEach(
                                    lease,
                                    patient,
                                    tables,
                                    "appointment",
                                    source.manifest(),
                                    bounds.fields(),
                                    bounds.bytes(),
                                    1_048_576,
                                    control,
                                    row -> {
                                        streams.requireActive();
                                        patient.require(lease);
                                        control.checkpoint();
                                        String id = row.fields().get("appointment_no");
                                        if (id == null || !seen.add(id)) scope();
                                        var witness = exact.remove(id);
                                        if (witness == null) scope();
                                        CdsExportAppointmentProjectionEvidence.verifyCurrent(
                                                row, witness);
                                        String address = imported.remove(id);
                                        Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>
                                                fields;
                                        CdsExportXmlComposer.Content content;
                                        Set<String> kept = Set.of(), replaced = Set.of();
                                        if (address == null) {
                                            var mapped =
                                                    new CdsExportAppointmentMapper()
                                                            .map(
                                                                    patient.patient(),
                                                                    row,
                                                                    lookups,
                                                                    clinicalZone,
                                                                    demographic[0]);
                                            if (!mapped.nativeRowRepresentable()) review();
                                            fields = mapped.fields();
                                            content =
                                                    writer ->
                                                            new CdsExportAppointmentXmlWriter()
                                                                    .write(mapped.value(), writer);
                                        } else {
                                            var decision =
                                                    new CdsExportAppointmentFieldReconciler()
                                                            .reconcile(
                                                                    patient.patient(),
                                                                    index,
                                                                    address,
                                                                    checked,
                                                                    row,
                                                                    witness,
                                                                    lookups,
                                                                    demographic[0],
                                                                    clinicalZone,
                                                                    retained,
                                                                    streams,
                                                                    control::checkpoint);
                                            if (!decision.mapping().nativeRowRepresentable())
                                                review();
                                            var prepared =
                                                    new CdsExportAppointmentRetainedAssembler()
                                                            .prepare(
                                                                    patient.patient(),
                                                                    decision,
                                                                    index,
                                                                    checked,
                                                                    retained,
                                                                    streams,
                                                                    control::checkpoint);
                                            fields = decision.mapping().fields();
                                            content = prepared.content();
                                            kept = prepared.retainedFields();
                                            replaced = prepared.replacedFields();
                                        }
                                        var inputs =
                                                new LinkedHashMap<
                                                        String, CdsExportCapturedTextRowDto>();
                                        inputs.put("appointment/" + row.recordKey(), row);
                                        for (var key : fields.keySet())
                                            if (!key.table().equals("appointment")) {
                                                var lookupRow =
                                                        lookupByKey.get(
                                                                key.table()
                                                                        + "/"
                                                                        + key.recordKey());
                                                if (lookupRow == null) scope();
                                                inputs.put(
                                                        key.table() + "/" + key.recordKey(),
                                                        lookupRow);
                                            }
                                        new CdsExportNativeFieldAssessmentValidator()
                                                .requireRepresentable(
                                                        List.copyOf(inputs.values()),
                                                        fields,
                                                        1_000_000,
                                                        control::checkpoint);
                                        for (String field : kept) if (!used.add(field)) scope();
                                        for (String field : replaced) if (!used.add(field)) scope();
                                        var identity =
                                                new CdsExportIdentityDto(
                                                        patientKey,
                                                        category,
                                                        CdsImportKeys.hash(
                                                                "carlos-native-occurrence-v1",
                                                                "appointment",
                                                                id));
                                        xml.emit(
                                                new CdsExportXmlComposer.Section(
                                                        identity, content));
                                        output.accept(
                                                new CdsExportClinicalOccurrenceService.Receipt(
                                                        identity,
                                                        index.sourceManifestSha256(),
                                                        "appointment",
                                                        id,
                                                        address,
                                                        fields,
                                                        kept,
                                                        replaced));
                                        counts[0]++;
                                        counts[address == null ? 2 : 1]++;
                                        counts[3] =
                                                Math.addExact(
                                                        counts[3],
                                                        kept.size() + (long) replaced.size());
                                    });
                    if (!actual.equals(source.capture())
                            || !exact.isEmpty()
                            || counts[0] != actual.tables().get("appointment").rows()) scope();
                    if (!imported.isEmpty()) review();
                    var expected = new HashSet<String>();
                    for (var field : retained)
                        if (field.category() == category && !expected.add(field.id())) scope();
                    if (!used.equals(expected)) scope();
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

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_APPOINTMENT_OCCURRENCE_SCOPE_INVALID");
    }

    private static void review() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.UNMAPPED_DATA);
    }

    private static void ambiguous() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.AMBIGUOUS_SOURCE);
    }

    private static void limit() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.RESOURCE_LIMIT);
    }
}
