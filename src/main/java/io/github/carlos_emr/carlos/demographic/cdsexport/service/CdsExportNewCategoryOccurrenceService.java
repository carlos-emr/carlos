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

import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Service;

/** Retained supporting categories and complete source context accompanying native clinical observations. */
@Service
public class CdsExportNewCategoryOccurrenceService implements CdsExportCategoryOccurrenceAssembler {
    private final CdsExportRetainedValuesService values;
    private final CdsExportRetainedReceiptVerificationService verification;
    private final CdsExportNativeCaptureService captures;
    private final CdsExportCapturedTextRowsReader rows;
    private final CdsExportCareOccurrenceService care;
    private final CdsExportLaboratoryOccurrenceService laboratories;
    private final CdsExportPrescriptionOccurrenceService prescriptions;
    private final CdsExportAppointmentOccurrenceService appointments;
    private final CdsExportHistoryInventoryService histories;

    /**
     * Without appointment source records: a capture that holds appointments is refused when the
     * document's expected occurrences are reconciled. Test fixtures without appointments only.
     */
    public CdsExportNewCategoryOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeCaptureService captures,
            CdsExportCareOccurrenceService care,
            CdsExportLaboratoryOccurrenceService laboratories,
            CdsExportPrescriptionOccurrenceService prescriptions) {
        this(values, verification, captures, care, laboratories, prescriptions, null);
    }

    /**
     * Without the chart's native issue record: a chart with a system marker that files no note is
     * refused when the document's expected occurrences are reconciled. Test fixtures only.
     */
    public CdsExportNewCategoryOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeCaptureService captures,
            CdsExportCareOccurrenceService care,
            CdsExportLaboratoryOccurrenceService laboratories,
            CdsExportPrescriptionOccurrenceService prescriptions,
            CdsExportAppointmentOccurrenceService appointments) {
        this(
                values,
                verification,
                captures,
                care,
                laboratories,
                prescriptions,
                appointments,
                null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportNewCategoryOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeCaptureService captures,
            CdsExportCareOccurrenceService care,
            CdsExportLaboratoryOccurrenceService laboratories,
            CdsExportPrescriptionOccurrenceService prescriptions,
            CdsExportAppointmentOccurrenceService appointments,
            CdsExportHistoryInventoryService histories) {
        this.appointments = appointments;
        this.histories = histories;
        this.values = Objects.requireNonNull(values);
        this.verification = Objects.requireNonNull(verification);
        this.captures = Objects.requireNonNull(captures);
        this.care = Objects.requireNonNull(care);
        this.laboratories = Objects.requireNonNull(laboratories);
        this.prescriptions = Objects.requireNonNull(prescriptions);
        rows = new CdsExportCapturedTextRowsReader(captures);
    }

    @Override
    public CdsCategory category() {
        return CdsCategory.NEW_CATEGORY;
    }

    @Override
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
        patient.require(lease);
        control.authorizeCategory(patient.patient(), category(), false);
        var tables = Set.copyOf(requiredTables);
        if (patientKey == null
                || !patientKey.matches("[0-9a-f]{64}")
                || !lease.document().command().categories().contains(category())
                || !tables.equals(source.capture().tables().keySet())
                || !tables.contains("demographic")) invalid();
        new CdsExportMergeScopeValidator(captures)
                .requireUnmerged(
                        lease, patient, tables, source, bounds.fields(), bounds.bytes(), control);
        long[] demographic = {0};
        var read =
                rows.forEach(
                        lease,
                        patient,
                        tables,
                        "demographic",
                        source.manifest(),
                        bounds.fields(),
                        bounds.bytes(),
                        1 << 20,
                        control,
                        row -> {
                            if (!row.fields().keySet().equals(demographicColumns())
                                    || !Integer.toString(patient.patient())
                                            .equals(row.fields().get("demographic_no"))
                                    || !String.format(Locale.ROOT, "%010d", patient.patient())
                                            .equals(row.recordKey())) invalid();
                            demographic[0]++;
                        });
        if (!read.equals(source.capture())
                || demographic[0] != 1
                || source.capture().tables().get("demographic").rows() != 1) invalid();
        long[] counts = {0, 0, 0};
        values.withValues(
                lease,
                patient,
                tables,
                source.manifest(),
                payloadManifest,
                bounds.fields(),
                bounds.bytes(),
                control,
                (index, retained, streams) -> {
                    if (!index.sourceManifestSha256().equals(source.manifest().plaintextSha256()))
                        invalid();
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
                    var consumed = new HashSet<String>();
                    for (String address :
                            parents(patient.patient(), index, checked, control::checkpoint)) {
                        control.authorizeCategory(patient.patient(), category(), false);
                        streams.requireActive();
                        var fields =
                                new CdsExportRetainedOccurrenceFields()
                                        .fieldsFor(
                                                patient.patient(),
                                                category(),
                                                address,
                                                index,
                                                checked,
                                                retained,
                                                control::checkpoint);
                        var content =
                                new CdsExportRetainedSectionWriter()
                                        .prepareChanges(
                                                fields, streams, markerSourceChanges(index.references().get(address), fields),
                                                control::checkpoint);
                        var all = new HashSet<>(content.retainedFields());
                        all.addAll(content.replacedFields());
                        if (all.size() != fields.size())
                            invalid();
                        for (String field : all) if (!consumed.add(field)) invalid();
                        var identity =
                                new CdsExportIdentityDto(
                                        patientKey,
                                        category(),
                                        CdsImportKeys.hash(
                                                "carlos-retained-occurrence-v1", address));
                        xml.emit(new CdsExportXmlComposer.Section(identity, content.content()));
                        output.accept(
                                new CdsExportClinicalOccurrenceService.Receipt(
                                        identity,
                                        index.sourceManifestSha256(),
                                        "cds_import_unit",
                                        address.substring(37),
                                        address,
                                        Map.of(),
                                        content.retainedFields(),
                                        content.replacedFields()));
                        control.authorizeCategory(patient.patient(), category(), false);
                        counts[0]++;
                        counts[1] = Math.addExact(counts[1], all.size());
                    }
                    // Demographic values with no Demographics element (preferred name, pronoun,
                    // care team, extension properties...): one record per patient that has any.
                    var mappedRecord =
                            CdsExportDemographicSupportingMapper.map(
                                    new CdsExportDemographicSupportingReader(captures)
                                            .rows(lease, patient, tables, source, bounds, control),
                                    control::checkpoint);
                    // After retained NewCategory records (a copy of an earlier CARLOS sender's
                    // native demographic record among them) this chart's own record always
                    // follows, even empty: a CARLOS importer restores from the last one.
                    var demographicRecord =
                            mappedRecord == null && counts[0] > 0
                                    ? CdsExportDemographicSupportingMapper.nothingCarried()
                                    : mappedRecord;
                    if (demographicRecord != null) {
                        var identity =
                                new CdsExportIdentityDto(
                                        patientKey,
                                        category(),
                                        CdsImportKeys.hash(
                                                "carlos-native-occurrence-v1",
                                                "demographic",
                                                Integer.toString(patient.patient())));
                        streams.requireActive();
                        control.authorizeCategory(patient.patient(), CdsCategory.DEMOGRAPHICS, false);
                        control.authorizeCategory(patient.patient(), category(), false);
                        xml.emit(
                                new CdsExportXmlComposer.Section(
                                        identity,
                                        body ->
                                                CdsExportDemographicSupportingMapper.write(
                                                        demographicRecord.json(), body)));
                        output.accept(
                                new CdsExportClinicalOccurrenceService.Receipt(
                                        identity,
                                        index.sourceManifestSha256(),
                                        "demographic",
                                        Integer.toString(patient.patient()),
                                        null,
                                        demographicRecord.fields(),
                                        Set.of(),
                                        Set.of()));
                        control.authorizeCategory(patient.patient(), CdsCategory.DEMOGRAPHICS, false);
                        control.authorizeCategory(patient.patient(), category(), false);
                        counts[0]++;
                        counts[2]++;
                    }
                    long markerCount = issueMarkers(
                            lease, patient, patientKey, tables, source, bounds, control,
                            index.sourceManifestSha256(), streams, xml, output);
                    counts[0] = Math.addExact(counts[0], markerCount);
                    counts[2] = Math.addExact(counts[2], markerCount);
                    // Window by window, never the whole chart's measurements at once.
                    care.forEachNativeSource(
                            lease,
                            patient,
                            tables,
                            source,
                            index,
                            bounds,
                            control,
                            nativeSource -> {
                                // Deleted and superseded versions are accounted by the CareElements
                                // assembly and never exported (CdsExportNativeCareMapper.retired).
                                if (nativeSource.deleted()) return;
                                var mapped =
                                        new CdsExportNativeCareMapper()
                                                .map(
                                                        patient.patient(),
                                                        nativeSource.observation(),
                                                        nativeSource.extensions(),
                                                        nativeSource.definitions());
                                new CdsExportNativeFieldAssessmentValidator()
                                        .requireRepresentable(
                                                mapped.rows(),
                                                mapped.fields(),
                                                control::checkpoint);
                                var identity =
                                        new CdsExportIdentityDto(
                                                patientKey,
                                                category(),
                                                CdsImportKeys.hash(
                                                        "carlos-native-occurrence-v1",
                                                        nativeSource.table(),
                                                        nativeSource.id()));
                                streams.requireActive();
                                control.authorizeCategory(
                                        patient.patient(), CdsCategory.CARE_ELEMENTS, false);
                                control.authorizeCategory(patient.patient(), category(), false);
                                xml.emit(
                                        new CdsExportXmlComposer.Section(
                                                identity,
                                                body ->
                                                        new CdsExportNativeCareMapper()
                                                                .writeSupporting(mapped, body)));
                                output.accept(
                                        new CdsExportClinicalOccurrenceService.Receipt(
                                                identity,
                                                index.sourceManifestSha256(),
                                                nativeSource.table(),
                                                nativeSource.id(),
                                                null,
                                                mapped.fields(),
                                                Set.of(),
                                                Set.of()));
                                control.authorizeCategory(
                                        patient.patient(), CdsCategory.CARE_ELEMENTS, false);
                                control.authorizeCategory(patient.patient(), category(), false);
                                counts[0]++;
                                counts[2]++;
                            });
                    // Each native report with its commented inbox routes, window by window
                    // (CdsExportLaboratoryOccurrenceService.forEachNativeSource): a commented
                    // route travels whole in its report's supporting record.
                    laboratories.forEachNativeSource(
                            lease,
                            patient,
                            tables,
                            source,
                            index,
                            bounds,
                            control,
                            (nativeSource, nativeRoutes) -> {
                        // No part of the message is this chart's: no record is written.
                        if (nativeSource.foreign()) return;
                        var carried = CdsExportInboxReviewMapper.carried(nativeRoutes);
                        var assessments =
                                new LinkedHashMap<
                                        CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>(
                                        CdsExportNativeLaboratoryMapper.assessments(nativeSource));
                        assessments.putAll(
                                CdsExportInboxReviewMapper.carriedFields(
                                        carried, "NewCategory/ResidualInfo"));
                        var assessedRows = new ArrayList<>(nativeSource.rows());
                        assessedRows.addAll(carried);
                        new CdsExportNativeFieldAssessmentValidator()
                                .requireRepresentable(
                                        assessedRows,
                                        assessments,
                                        control::checkpoint);
                        var identity =
                                new CdsExportIdentityDto(
                                        patientKey,
                                        category(),
                                        CdsImportKeys.hash(
                                                "carlos-native-occurrence-v1",
                                                "hl7TextMessage",
                                                nativeSource.id()));
                        streams.requireActive();
                        control.authorizeCategory(
                                patient.patient(), CdsCategory.LABORATORY_RESULTS, true);
                        control.authorizeCategory(patient.patient(), category(), false);
                        xml.emit(
                                new CdsExportXmlComposer.Section(
                                        identity,
                                        body ->
                                                new CdsExportNativeLaboratoryMapper()
                                                        .writeSupporting(
                                                                nativeSource, carried, body)));
                        output.accept(
                                new CdsExportClinicalOccurrenceService.Receipt(
                                        identity,
                                        index.sourceManifestSha256(),
                                        "hl7TextMessage",
                                        nativeSource.id(),
                                        null,
                                        assessments,
                                        Set.of(),
                                        Set.of()));
                        control.authorizeCategory(
                                patient.patient(), CdsCategory.LABORATORY_RESULTS, true);
                        control.authorizeCategory(patient.patient(), category(), false);
                        counts[0]++;
                        counts[2]++;
                            });
                    boolean importedLaboratories =
                            lease.document()
                                            .command()
                                            .categories()
                                            .contains(CdsCategory.LABORATORY_RESULTS)
                                    && index.tables()
                                            .getOrDefault("cds_import_unit", Map.of())
                                            .values()
                                            .stream()
                                            .anyMatch(
                                                    unit ->
                                                            CdsCategory.LABORATORY_RESULTS
                                                                    .wireName()
                                                                    .equals(
                                                                            unit.fields()
                                                                                    .get(
                                                                                            "category")));
                    // Inbox comments recorded in CARLOS on a result from an earlier CDS import:
                    // the result is exported from its imported elements, the comments here.
                    if (importedLaboratories)
                        laboratories.forEachImportedComments(
                                lease,
                                patient,
                                tables,
                                source,
                                index,
                                bounds,
                                control,
                                comments -> {
                        var assessments =
                                CdsExportInboxReviewMapper.carriedFields(
                                        comments.routes(), "NewCategory/ResidualInfo");
                        new CdsExportNativeFieldAssessmentValidator()
                                .requireRepresentable(
                                        comments.routes(),
                                        assessments,
                                        control::checkpoint);
                        var identity =
                                new CdsExportIdentityDto(
                                        patientKey,
                                        category(),
                                        CdsExportLaboratoryOccurrenceService.importedCommentsId(
                                                comments.result()));
                        streams.requireActive();
                        control.authorizeCategory(
                                patient.patient(), CdsCategory.LABORATORY_RESULTS, true);
                        control.authorizeCategory(patient.patient(), category(), false);
                        xml.emit(
                                new CdsExportXmlComposer.Section(
                                        identity,
                                        body ->
                                                CdsExportNativeLaboratoryMapper
                                                        .writeImportedComments(comments, body)));
                        output.accept(
                                new CdsExportClinicalOccurrenceService.Receipt(
                                        identity,
                                        index.sourceManifestSha256(),
                                        "cds_laboratory_result",
                                        comments.result(),
                                        null,
                                        assessments,
                                        Set.of(),
                                        Set.of()));
                        control.authorizeCategory(
                                patient.patient(), CdsCategory.LABORATORY_RESULTS, true);
                        control.authorizeCategory(patient.patient(), category(), false);
                        counts[0]++;
                        counts[2]++;
                                });
                    // The printed prescriptions, one at a time
                    // (CdsExportPrescriptionOccurrenceService.forEachSource).
                    prescriptions.forEachSource(
                            lease,
                            patient,
                            tables,
                            source,
                            bounds,
                            control,
                            prescription -> {
                        var assessments =
                                CdsExportPrescriptionOccurrenceService.assessments(prescription);
                        new CdsExportNativeFieldAssessmentValidator()
                                .requireRepresentable(
                                        prescription.rows(),
                                        assessments,
                                        control::checkpoint);
                        var identity =
                                new CdsExportIdentityDto(
                                        patientKey,
                                        category(),
                                        CdsImportKeys.hash(
                                                "carlos-native-occurrence-v1",
                                                "prescription",
                                                prescription
                                                        .prescription()
                                                        .fields()
                                                        .get("script_no")));
                        streams.requireActive();
                        control.authorizeCategory(
                                patient.patient(), CdsCategory.MEDICATIONS_AND_TREATMENTS, false);
                        control.authorizeCategory(patient.patient(), category(), false);
                        xml.emit(
                                new CdsExportXmlComposer.Section(
                                        identity,
                                        body ->
                                                CdsExportPrescriptionOccurrenceService.write(
                                                        prescription, body)));
                        output.accept(
                                new CdsExportClinicalOccurrenceService.Receipt(
                                        identity,
                                        index.sourceManifestSha256(),
                                        "prescription",
                                        prescription.prescription().fields().get("script_no"),
                                        null,
                                        assessments,
                                        Set.of(),
                                        Set.of()));
                        control.authorizeCategory(
                                patient.patient(), CdsCategory.MEDICATIONS_AND_TREATMENTS, false);
                        control.authorizeCategory(patient.patient(), category(), false);
                        counts[0]++;
                        counts[2]++;
                            });
                    var expected = new HashSet<String>();
                    for (var field : retained)
                        if (field.category() == category()) expected.add(field.id());
                    if (!expected.equals(consumed)) invalid();
                    streams.requireActive();
                    captures.authorizeSources(lease, patient, tables, control);
                    patient.require(lease);
                    control.authorizeCategory(patient.patient(), category(), false);
                    control.checkpoint();
                });
        // One source record per current appointment (CARLOS.Appointment.Source.v1): Appointments
        // has no ResidualInfo, so the complete stored row and the complete text of any element
        // written in a schema-valid form travel here. Decided by the Appointments assembly's own
        // mapping, in the order of its elements, after this category's retained values are closed.
        if (appointments != null) {
            appointments.forEachOccurrence(
                    lease,
                    patient,
                    patientKey,
                    tables,
                    source,
                    payloadManifest,
                    bounds,
                    control,
                    occurrence -> {
                        var carrier =
                                CdsExportAppointmentSourceMapper.map(
                                        occurrence.ordinal(),
                                        occurrence.row(),
                                        occurrence.lookups(),
                                        occurrence.complete());
                        new CdsExportNativeFieldAssessmentValidator()
                                .requireRepresentable(
                                        carrier.rows(),
                                        carrier.fields(),
                                        control::checkpoint);
                        var identity =
                                new CdsExportIdentityDto(
                                        patientKey,
                                        category(),
                                        CdsImportKeys.hash(
                                                "carlos-native-occurrence-v1",
                                                "appointment",
                                                occurrence.id()));
                        control.authorizeCategory(
                                patient.patient(), CdsCategory.APPOINTMENTS, false);
                        control.authorizeCategory(patient.patient(), category(), false);
                        xml.emit(
                                new CdsExportXmlComposer.Section(
                                        identity,
                                        body ->
                                                CdsExportAppointmentSourceMapper.write(
                                                        carrier, body)));
                        output.accept(
                                new CdsExportClinicalOccurrenceService.Receipt(
                                        identity,
                                        source.manifest().plaintextSha256(),
                                        "appointment",
                                        occurrence.id(),
                                        null,
                                        carrier.fields(),
                                        Set.of(),
                                        Set.of()));
                        control.authorizeCategory(
                                patient.patient(), CdsCategory.APPOINTMENTS, false);
                        control.authorizeCategory(patient.patient(), category(), false);
                        counts[0]++;
                        counts[2]++;
                    });
            captures.authorizeSources(lease, patient, tables, control);
            patient.require(lease);
            control.checkpoint();
        }
        return new CdsExportClinicalOccurrenceService.Result(
                source.manifest().plaintextSha256(),
                category(),
                counts[0],
                counts[0] - counts[2],
                counts[2],
                counts[1]);
    }

    /**
     * Whether an export with these categories and captured tables carries the chart's native
     * issue record: NewCategory with the seven history categories, whose capture holds the
     * patient issues, their catalogue entries and the notes' links to them.
     */
    static boolean carriesIssues(Set<CdsCategory> categories, Set<String> tables) {
        return categories.contains(CdsCategory.NEW_CATEGORY)
                && categories.containsAll(CdsExportNoteSourceReader.CATEGORIES)
                && tables.containsAll(Set.of("casemgmt_issue", "casemgmt_issue_notes", "issue"));
    }

    /**
     * One current marker per record, each independently authorized and receipted. Retained
     * carriers become source-only, including when history sections were not selected, so
     * deleted or excluded markers cannot be restored from stale source records.
     */
    private long issueMarkers(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            String patientKey,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            String manifest,
            CdsExportRetainedSectionWriter.Values streams,
            CdsExportXmlComposer.SectionEmitter xml,
            CdsExportClinicalOccurrenceService.Receipts output)
            throws IOException {
        if (histories == null
                || !carriesIssues(lease.document().command().categories(), tables)) return 0;
        long[] count = {0};
        histories.forEachMarker(lease, patient, tables, source, bounds, control, marker -> {
            var section = CdsExportNativeHistoryMapper.issueCategory(marker.definition());
            control.authorizeCategory(patient.patient(), section, false);
            var granted = new LinkedHashSet<String>();
            var mapped = CdsExportIssueMarkerMapper.map(
                    patient.patient(), marker,
                    program -> {
                        if (!control.issueProgramReadable(patient.patient(), section, program))
                            return false;
                        granted.add(program);
                        return true;
                    }, control::checkpoint);
            new CdsExportNativeFieldAssessmentValidator()
                    .requireRepresentable(mapped.rows(), mapped.fields(), control::checkpoint);
            String id = marker.issue().value().fields().get("id");
            var identity = new CdsExportIdentityDto(patientKey, category(),
                    CdsImportKeys.hash("carlos-native-occurrence-v1", "casemgmt_issue", id));
            streams.requireActive();
            control.authorizeCategory(patient.patient(), category(), false);
            xml.emit(new CdsExportXmlComposer.Section(identity,
                    body -> CdsExportIssueMarkerMapper.write(mapped.json(), body)));
            output.accept(new CdsExportClinicalOccurrenceService.Receipt(identity, manifest,
                    "casemgmt_issue", id, null, mapped.fields(), Set.of(), Set.of()));
            control.authorizeCategory(patient.patient(), section, false);
            for (String program : granted)
                if (!control.issueProgramReadable(patient.patient(), section, program))
                    throw new SecurityException("CDS_EXPORT_ACCESS_CHANGED");
            control.authorizeCategory(patient.patient(), category(), false);
            count[0] = Math.incrementExact(count[0]);
        });
        return count[0];
    }

    /** Preserve the earlier sender's complete payload without treating it as a current marker. */
    static List<CdsExportRetainedSectionWriter.Change> markerSourceChanges(
            List<CdsExportRetainedIndexDto.Reference> refs,
            List<io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedFieldDto> fields)
            throws IOException {
        if (refs == null || refs.size() <= 1) return List.of();
        if (!restoredIssues(refs)) invalid();
        var names = fields.stream()
                .filter(field -> field.sourcePath().endsWith("/CategoryName[1]"))
                .map(io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedFieldDto::id)
                .collect(java.util.stream.Collectors.toSet());
        if (names.size() != 1) invalid();
        return List.of(new CdsExportRetainedSectionWriter.Change("CategoryName[1]", names,
                xml -> xml.text(io.github.carlos_emr.carlos.demographic.cds.contract
                        .CdsNativeExtensionContract.NATIVE_ISSUES_SOURCE_CATEGORY)));
    }

    /**
     * NewCategory has no separate native clinical projection: its demographic receipt proves
     * association, not field precedence. Every applied NewCategory unit of the chart is
     * returned, however many: an address for each unit the retained index already holds.
     */
    static List<String> parents(
            int patient,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            Runnable checkpoint)
            throws IOException {
        checkpoint.run();
        if (patient < 1
                || !index.sourceManifestSha256().equals(checked.sourceManifestSha256())
                || !index.appliedUnits().equals(checked.units().keySet())) invalid();
        var selected = new ArrayList<String>();
        var seen = new HashSet<String>();
        for (var unit : index.tables().getOrDefault("cds_import_unit", Map.of()).values()) {
            checkpoint.run();
            if (!CdsCategory.NEW_CATEGORY.wireName().equals(unit.fields().get("category")))
                continue;
            String address = unit.fields().get("run_id") + ":" + unit.fields().get("unit_key");
            if (!index.appliedUnits().contains(address)) continue;
            var refs = index.references().get(address);
            var proof = checked.units().get(address);
            if (!address.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}:[0-9a-f]{64}")
                    || !seen.add(address)
                    || refs == null
                    || refs.isEmpty()
                    || !"demographic".equals(refs.getFirst().table())
                    || !Integer.toString(patient).equals(refs.getFirst().id())
                    || !restoredIssues(refs)
                    || proof == null
                    || !refs.equals(
                            proof.stream()
                                    .map(
                                            CdsExportRetainedReceiptVerificationDto.CheckedReference
                                                    ::expected)
                                    .toList())) invalid();
            // The demographic row proves the association. A marker issue the unit restored from
            // its native issue record may since have changed or gone: its
            // retained copy becomes source-only before current markers are emitted.
            var association = proof.getFirst().reason();
            if (association != CdsExportRetainedReceiptVerificationDto.Reason.UNCHANGED_ROW
                    && association != CdsExportRetainedReceiptVerificationDto.Reason.CHANGED_ROW)
                throw new CdsExportReviewException(CdsExportReviewException.Reason.UNMAPPED_DATA);
            selected.add(address);
        }
        selected.sort(String::compareTo);
        checkpoint.run();
        return List.copyOf(selected);
    }

    /**
     * Whether a NewCategory unit's references after its demographic one are only the marker
     * issues a CARLOS importer restores from a native issue record of a patient file
     * ({@code casemgmt_issue#<code>}); every other NewCategory unit names the demographic row
     * alone.
     */
    static boolean restoredIssues(List<CdsExportRetainedIndexDto.Reference> refs) {
        for (int i = 1; i < refs.size(); i++)
            if (!"casemgmt_issue".equals(refs.get(i).table())) return false;
        return true;
    }

    private static Set<String> demographicColumns() {
        var columns = new HashSet<>(CdsExportDemographicRowDto.COLUMNS);
        columns.add("demographic_no");
        return Set.copyOf(columns);
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_NEW_CATEGORY_OCCURRENCE_INVALID");
    }
}
