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

    public CdsExportNewCategoryOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeCaptureService captures,
            CdsExportCareOccurrenceService care,
            CdsExportLaboratoryOccurrenceService laboratories,
            CdsExportPrescriptionOccurrenceService prescriptions) {
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
                bounds.indexRows(),
                bounds.indexBytes(),
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
                            parents(
                                    patient.patient(),
                                    index,
                                    checked,
                                    bounds.nativeRows(),
                                    control::checkpoint)) {
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
                                                fields, streams, List.of(), control::checkpoint);
                        var all = new HashSet<>(content.retainedFields());
                        all.addAll(content.replacedFields());
                        if (!content.replacedFields().isEmpty() || all.size() != fields.size())
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
                    for (var nativeSource :
                            care.nativeSources(
                                    lease, patient, tables, source, index, bounds, control)) {
                        var mapped =
                                new CdsExportNativeCareMapper()
                                        .map(
                                                patient.patient(),
                                                nativeSource.observation(),
                                                nativeSource.extensions(),
                                                nativeSource.definitions(),
                                                nativeSource.table().equals("measurementsDeleted"));
                        new CdsExportNativeFieldAssessmentValidator()
                                .requireRepresentable(
                                        mapped.rows(),
                                        mapped.fields(),
                                        1_000_000,
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
                    }
                    for (var nativeSource :
                            laboratories.nativeSources(
                                    lease, patient, tables, source, index, bounds, control)) {
                        var assessments = CdsExportNativeLaboratoryMapper.assessments(nativeSource);
                        new CdsExportNativeFieldAssessmentValidator()
                                .requireRepresentable(
                                        nativeSource.rows(),
                                        assessments,
                                        1_000_000,
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
                                                        .writeSupporting(nativeSource, body)));
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
                    }
                    for (var prescription :
                            prescriptions.sources(
                                    lease, patient, tables, source, bounds, control)) {
                        var assessments =
                                CdsExportPrescriptionOccurrenceService.assessments(prescription);
                        new CdsExportNativeFieldAssessmentValidator()
                                .requireRepresentable(
                                        prescription.rows(),
                                        assessments,
                                        1_000_000,
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
                    }
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
        return new CdsExportClinicalOccurrenceService.Result(
                source.manifest().plaintextSha256(),
                category(),
                counts[0],
                counts[0] - counts[2],
                counts[2],
                counts[1]);
    }

    /** NewCategory has no separate native clinical projection: its demographic receipt proves association, not field precedence. */
    static List<String> parents(
            int patient,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            int maximum,
            Runnable checkpoint)
            throws IOException {
        checkpoint.run();
        if (patient < 1
                || maximum < 1
                || maximum > 8192
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
                    || refs.size() != 1
                    || !"demographic".equals(refs.getFirst().table())
                    || !Integer.toString(patient).equals(refs.getFirst().id())
                    || proof == null
                    || !refs.equals(
                            proof.stream()
                                    .map(
                                            CdsExportRetainedReceiptVerificationDto.CheckedReference
                                                    ::expected)
                                    .toList())) invalid();
            if (proof.stream()
                    .anyMatch(
                            ref ->
                                    ref.reason()
                                                    != CdsExportRetainedReceiptVerificationDto
                                                            .Reason.UNCHANGED_ROW
                                            && ref.reason()
                                                    != CdsExportRetainedReceiptVerificationDto
                                                            .Reason.CHANGED_ROW))
                throw new CdsExportReviewException(CdsExportReviewException.Reason.UNMAPPED_DATA);
            if (selected.size() >= maximum)
                throw new CdsExportReviewException(CdsExportReviewException.Reason.RESOURCE_LIMIT);
            selected.add(address);
        }
        selected.sort(String::compareTo);
        checkpoint.run();
        return List.copyOf(selected);
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
