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
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Exact native/retained appointment assembly under one frozen generation. The clinical zone
 * and rules version come from that capture; no default JVM zone or mutable setting is read.
 * Archive rows are superseded or deleted versions ({@link CdsExportAppointmentVersions}): every
 * field is assessed, none is exported and none holds the patient. An imported appointment deleted
 * or cut in CARLOS is not in the chart and is not exported either: its retained source fields are
 * accounted as removed once CARLOS's archive copy proves the removal. Other unrepresented native
 * facts remain explicit review gates.
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
                    "LookupListItem",
                    "provider",
                    "demographic",
                    "demographicArchive",
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
        Objects.requireNonNull(output);
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
                                        occurrence -> {
                                            emitter.emit(
                                                    new CdsExportXmlComposer.Section(
                                                            occurrence.identity(),
                                                            occurrence.content()));
                                            output.accept(
                                                    new CdsExportClinicalOccurrenceService.Receipt(
                                                            occurrence.identity(),
                                                            source.manifest().plaintextSha256(),
                                                            "appointment",
                                                            occurrence.id(),
                                                            occurrence.importedUnit(),
                                                            occurrence.fields(),
                                                            occurrence.retainedFields(),
                                                            occurrence.replacedFields()));
                                        }));
        return Objects.requireNonNull(result[0]);
    }

    /**
     * One current appointment, decided once for both of its records: the Appointments element
     * (content, field decisions, retained fields) and its NewCategory source record
     * ({@link CdsExportAppointmentSourceMapper}), which needs the element's position, the stored
     * row, the lookups and the complete text of every element written in a schema-valid form.
     *
     * @param ordinal 1-based position of the element among the patient's Appointments elements
     * @param identity the Appointments occurrence identity
     * @param importedUnit address of the applied import unit, or null for a native booking
     */
    public record Occurrence(
            long ordinal,
            String id,
            CdsExportIdentityDto identity,
            CdsExportCapturedTextRowDto row,
            String importedUnit,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            CdsExportXmlComposer.Content content,
            Set<String> retainedFields,
            Set<String> replacedFields,
            Map<String, String> complete,
            CdsExportAppointmentLookupsDto lookups) {
        @Override
        public String toString() {
            return "CdsExportAppointmentOccurrenceService.Occurrence[protected]";
        }
    }

    /** Receives every current appointment in element order. */
    @FunctionalInterface
    public interface Visitor {
        void accept(Occurrence occurrence) throws IOException;
    }

    /**
     * Every current appointment of the capture, in the order of its Appointments elements, decided
     * by the same mapping and reconciliation the Appointments category emits. The NewCategory
     * assembly uses it to write each appointment's source record; a capture without appointments
     * (Appointments not selected) has none.
     */
    public void forEachOccurrence(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            String patientKey,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            CdsExportStagedFileDto payloadManifest,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            Visitor visitor)
            throws IOException {
        Objects.requireNonNull(visitor);
        if (!requiredTables.contains("appointment")) return;
        assemble(
                lease,
                patient,
                patientKey,
                requiredTables,
                source,
                payloadManifest,
                bounds,
                control,
                visitor);
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
            Visitor output)
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
        long appointmentRows = source.capture().tables().get("appointment").rows();
        new CdsExportMergeScopeValidator(rows)
                .requireUnmerged(
                        lease, patient, tables, source, bounds.fields(), bounds.bytes(), control);
        var zone =
                zones.read(
                        lease, patient, tables, source, bounds.fields(), bounds.bytes(), control);
        if (!zone.sourceManifestSha256().equals(source.manifest().plaintextSha256())) scope();
        var clinicalZone = zone.zone();
        // Without NewCategory the appointment's source record is not written: the values only it
        // would hold are decided OPERATIONAL (not selected) instead of holding the chart.
        boolean sourceRecords =
                lease.document().command().categories().contains(CdsCategory.NEW_CATEGORY);
        // Decision 17: appointment edit history is superseded. Archive rows no longer hold the
        // patient here; each one is read and assessed below, after the current rows.
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
                    var reasonCodes = new HashMap<String, List<CdsExportCapturedTextRowDto>>();
                    var earlierDisplays = new HashSet<String>();
                    var demographic = new CdsExportCapturedTextRowDto[1];
                    // 2 providers, 3 statuses, 4 demographic, 5 reason-list items, 6 demographic
                    // archive snapshots. These are the clinic's vocabulary and the patient's own
                    // record, not rows that grow with the appointments: held for every window
                    // and not counted against it.
                    long[] lookup = new long[7];
                    var first =
                            rows.forEachTable(
                                    lease,
                                    patient,
                                    tables,
                                    Set.of(
                                            "provider",
                                            "appointment_status",
                                            "LookupListItem",
                                            "demographic",
                                            "demographicArchive"),
                                    source.manifest(),
                                    bounds.fields(),
                                    bounds.bytes(),
                                    1_048_576,
                                    control,
                                    row -> {
                                        if (row.table().equals("demographicArchive")) {
                                            // Only earlier display names are kept; the snapshot
                                            // itself belongs to the Demographics assembler.
                                            earlierDisplays.addAll(
                                                    CdsExportAppointmentNativeMetadata
                                                            .archivedDisplays(
                                                                    patient.patient(), row));
                                            lookup[6]++;
                                            return;
                                        }
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
                                            case "LookupListItem" -> {
                                                String id = row.fields().get("id");
                                                if (id == null
                                                        || !row.fields()
                                                                .keySet()
                                                                .equals(
                                                                        Set.copyOf(
                                                                                CdsExportPatientTableReadSupport
                                                                                        .Plan
                                                                                        .APPOINTMENT_REASON_CODES
                                                                                        .columns())))
                                                    scope();
                                                reasonCodes
                                                        .computeIfAbsent(
                                                                id, ignored -> new ArrayList<>())
                                                        .add(row);
                                                lookup[5]++;
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
                            || lookup[4] != first.tables().get("demographic").rows()
                            || lookup[5] != first.tables().get("LookupListItem").rows()
                            || lookup[6] != first.tables().get("demographicArchive").rows())
                        scope();
                    var lookups =
                            new CdsExportAppointmentLookupsDto(
                                    index.sourceManifestSha256(),
                                    providers,
                                    statuses,
                                    reasonCodes,
                                    earlierDisplays);
                    var used = new HashSet<String>();
                    var seen = new HashSet<String>();
                    // The chart is read whole when it fits one window, as it always was, and
                    // window by window otherwise (CdsExportSectionWindowPlanner): the witnesses
                    // and rows of one window are held at a time.
                    CdsExportSectionWindowPlanner.forEachWindow(
                            appointmentRows,
                            bounds.window(),
                            owners -> {
                                var listed =
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
                                                row ->
                                                        owners.add(
                                                                row.fields().get("appointment_no")));
                                if (!listed.equals(source.capture())) scope();
                            },
                            (window, budget) -> {
                    var exact = new HashMap<String, CdsExportNativeRowReceiptDto>();
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
                                        if (!witness.table().equals("appointment")
                                                || !window.contains(witness.id())
                                                || !budget.owner(
                                                        1024L
                                                                + (witness.state() == null
                                                                        ? 0
                                                                        : witness.state()
                                                                                        .columns()
                                                                                        .size()
                                                                                * 256L))) return;
                                        if (exact.putIfAbsent(witness.id(), witness) != null)
                                            scope();
                                    });
                    if (budget.exceeded()) return false;
                    if (!receiptScan.sourceManifestSha256().equals(index.sourceManifestSha256())
                            || exact.size() != window.size()) scope();
                    long[] emitted = {0};
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
                                        // A row with no number belongs to no window: refused.
                                        if (id == null) scope();
                                        if (!window.contains(id)) return;
                                        emitted[0]++;
                                        if (!seen.add(id)) scope();
                                        var witness = exact.remove(id);
                                        if (witness == null) scope();
                                        CdsExportAppointmentProjectionEvidence.verifyCurrent(
                                                row, witness);
                                        String address = imported.remove(id);
                                        Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>
                                                fields;
                                        CdsExportXmlComposer.Content content;
                                        Map<String, String> complete;
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
                                            if (!mapped.nativeRowRepresentable())
                                                hold(mapped.fields());
                                            fields = mapped.fields();
                                            complete = mapped.complete();
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
                                                hold(decision.mapping().fields());
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
                                            complete = decision.mapping().complete();
                                            content = prepared.content();
                                            kept = prepared.retainedFields();
                                            replaced = prepared.replacedFields();
                                        }
                                        fields =
                                                CdsExportDemographicSupportingMapper.withSelection(
                                                        fields, sourceRecords);
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
                                        output.accept(
                                                new Occurrence(
                                                        counts[0] + 1,
                                                        id,
                                                        identity,
                                                        row,
                                                        address,
                                                        fields,
                                                        content,
                                                        kept,
                                                        replaced,
                                                        complete,
                                                        lookups));
                                        counts[0]++;
                                        counts[address == null ? 2 : 1]++;
                                        counts[3] =
                                                Math.addExact(
                                                        counts[3],
                                                        kept.size() + (long) replaced.size());
                                    });
                    // Every witness this window loaded was used by one of its rows.
                    if (!actual.equals(source.capture())
                            || !exact.isEmpty()
                            || emitted[0] != window.size()) scope();
                    return true;
                            });
                    if (counts[0] != appointmentRows) scope();
                    // Every current appointment number, known once every window is emitted.
                    var currentNumbers = Set.copyOf(seen);
                    // An imported appointment that is no longer in this chart was deleted or cut
                    // to be pasted elsewhere: the day sheet and the appointment history no longer
                    // show it, so it is not exported. Its retained source fields are accounted as
                    // not exported, and CARLOS's own archive copy of the booking must exist.
                    var removedFields = new HashSet<String>();
                    for (var entry : imported.entrySet()) {
                        control.checkpoint();
                        var comparisons = checked.units().get(entry.getValue());
                        if (comparisons == null
                                || comparisons.stream()
                                                .filter(
                                                        ref ->
                                                                ref.expected().table().equals("appointment"))
                                                .filter(
                                                        ref ->
                                                                ref.reason()
                                                                        == CdsExportRetainedReceiptVerificationDto
                                                                                .Reason
                                                                                .ROW_NOT_IN_CAPTURE)
                                                .count()
                                        != 1) deletion();
                        for (var field :
                                new CdsExportRetainedOccurrenceFields()
                                        .fieldsFor(
                                                patient.patient(),
                                                category,
                                                entry.getValue(),
                                                index,
                                                checked,
                                                retained,
                                                control::checkpoint))
                            if (!removedFields.add(field.id())) scope();
                    }
                    var archivedNumbers = new HashSet<String>();
                    long[] versions = {0};
                    var archived =
                            rows.forEach(
                                    lease,
                                    patient,
                                    tables,
                                    "appointmentArchive",
                                    source.manifest(),
                                    bounds.fields(),
                                    bounds.bytes(),
                                    1_048_576,
                                    control,
                                    row -> {
                                        streams.requireActive();
                                        patient.require(lease);
                                        control.checkpoint();
                                        new CdsExportNativeFieldAssessmentValidator()
                                                .requireRepresentable(
                                                        List.of(row),
                                                        CdsExportAppointmentVersions.dispositions(
                                                                patient.patient(),
                                                                row,
                                                                currentNumbers,
                                                                imported.keySet()),
                                                        1_000_000,
                                                        control::checkpoint);
                                        archivedNumbers.add(row.fields().get("appointment_no"));
                                        versions[0]++;
                                    });
                    if (!archived.equals(source.capture())
                            || versions[0] != archived.tables().get("appointmentArchive").rows())
                        scope();
                    // A booking that vanished without CARLOS's archive copy was not removed
                    // through the schedule; its absence is not explained.
                    if (!archivedNumbers.containsAll(imported.keySet())) deletion();
                    var expected = new HashSet<String>();
                    for (var field : retained)
                        if (field.category() == category && !expected.add(field.id())) scope();
                    if (!Collections.disjoint(used, removedFields)) scope();
                    var accounted = new HashSet<>(used);
                    accounted.addAll(removedFields);
                    if (!accounted.equals(expected)) scope();
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

    /**
     * A booking that cannot be exported. A stored date or start that is not a calendar date or a
     * time of day is held under its own name; anything else is unmapped data.
     */
    private static void hold(Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields)
            throws IOException {
        for (var field : fields.values())
            if (field.outcome() == CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW
                    && CdsExportAppointmentMapper.NOT_A_CALENDAR_TIME_RULE.equals(field.rule()))
                throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                        .CdsExportMappingReviewException(
                        CdsExportAppointmentTimingMapper.NOT_A_CALENDAR_TIME);
        review();
    }

    private static void deletion() throws IOException {
        throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                .CdsExportMappingReviewException("CDS_EXPORT_APPOINTMENT_DELETION_REQUIRES_REVIEW");
    }

    private static void ambiguous() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.AMBIGUOUS_SOURCE);
    }
}
