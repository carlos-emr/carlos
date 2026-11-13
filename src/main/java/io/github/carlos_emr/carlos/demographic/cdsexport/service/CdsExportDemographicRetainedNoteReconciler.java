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

import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.Reason;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.util.*;

/** One patient-note decision; care-team carriers and unselected alerts never acquire a note or whole-row qualification. */
public final class CdsExportDemographicRetainedNoteReconciler {
    private static final String PATH =
            "/OmdCds[1]/PatientRecord[1]/Demographics[1]/NoteAboutPatient[1]";

    public record Result(
            String sourceManifestSha256,
            String unitAddress,
            Set<String> sourceFields,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            Set<CdsExportNativeFieldKeyDto> deferredNativeFields,
            Set<String> blockers,
            boolean alertsOmitted,
            List<CdsExportRetainedSectionWriter.Change> proposedChanges) {
        public Result {
            sourceFields = Set.copyOf(sourceFields);
            fields = Map.copyOf(fields);
            deferredNativeFields = Set.copyOf(deferredNativeFields);
            blockers = Set.copyOf(blockers);
            proposedChanges = List.copyOf(proposedChanges);
        }

        public boolean noteRepresentable() {
            return blockers.isEmpty()
                    && fields.values().stream()
                            .noneMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        }

        public List<CdsExportRetainedSectionWriter.Change> changes() throws IOException {
            if (!noteRepresentable())
                throw new IOException("CDS_EXPORT_DEMOGRAPHIC_RETAINED_NOTE_REQUIRES_REVIEW");
            return proposedChanges;
        }

        @Override
        public String toString() {
            return "CdsExportDemographicRetainedNoteReconciler.Result[protected]";
        }
    }

    public Result reconcile(
            int patient,
            Set<CdsCategory> selected,
            String address,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> allFields,
            CdsExportCapturedTextRowDto demographic,
            CdsExportNativeRowReceiptDto demographicWitness,
            CdsExportCapturedTextRowDto current,
            CdsExportNativeRowReceiptDto witness,
            CdsExportRetainedSectionWriter.Values values,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(values);
        Objects.requireNonNull(checkpoint);
        values.requireActive();
        checkpoint.run();
        selected = Set.copyOf(selected);
        if (!selected.contains(CdsCategory.DEMOGRAPHICS)) scope();
        var evidence =
                new CdsExportDemographicProjectionEvidence()
                        .verify(
                                patient,
                                address,
                                index,
                                checked,
                                demographic,
                                demographicWitness,
                                checkpoint);
        var source =
                new CdsExportRetainedOccurrenceFields()
                                .fieldsFor(
                                        patient,
                                        CdsCategory.DEMOGRAPHICS,
                                        address,
                                        index,
                                        checked,
                                        allFields,
                                        checkpoint)
                                .stream()
                                .filter(field -> field.sourcePath().equals(PATH))
                                .toList();
        if (source.size() > 1) scope();
        var ids = new HashSet<String>();
        source.forEach(field -> ids.add(field.id()));
        var references =
                checked.units().get(address).stream()
                        .filter(ref -> ref.expected().table().equals("demographiccust"))
                        .toList();
        if (references.size() > 1) scope();
        var reference = references.isEmpty() ? null : references.getFirst();
        String prepared = evidence.preparation().details().patientNote();
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var deferred = new HashSet<CdsExportNativeFieldKeyDto>();
        var blockers = new HashSet<String>();
        var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        if (current == null) {
            if (witness != null) scope();
            if (reference != null)
                blockers.add("CDS_EXPORT_DEMOGRAPHIC_NOTE_ROW_DELETION_REQUIRES_REVIEW");
            else if (prepared != null || !source.isEmpty())
                blockers.add("CDS_EXPORT_DEMOGRAPHIC_NOTE_NATIVE_PROJECTION_NOT_PROVEN");
            values.requireActive();
            checkpoint.run();
            return new Result(
                    index.sourceManifestSha256(),
                    address,
                    ids,
                    fields,
                    deferred,
                    blockers,
                    !selected.contains(CdsCategory.ALERTS_AND_SPECIAL_NEEDS),
                    changes);
        }
        var mapping = new CdsExportPatientNoteMapper().map(patient, selected, current);
        requireWitness(patient, current, witness, checkpoint);
        var contentKey =
                new CdsExportNativeFieldKeyDto("demographiccust", current.recordKey(), "content");
        var ownerKey =
                new CdsExportNativeFieldKeyDto(
                        "demographiccust", current.recordKey(), "demographic_no");
        fields.put(ownerKey, mapping.fields().get(ownerKey));
        for (String column : current.fields().keySet())
            if (!column.equals("content") && !column.equals("demographic_no"))
                deferred.add(
                        new CdsExportNativeFieldKeyDto(
                                "demographiccust", current.recordKey(), column));
        CdsNativeRowState baseline = null;
        CdsNativeRowComparison difference = null;
        String rule;
        boolean review = false, unchanged = false;
        if (reference != null) {
            if (!Integer.toString(patient).equals(reference.expected().id())
                    || !Objects.equals(reference.currentDigest(), witness.digest())
                    || reference.reason() == Reason.ROW_NOT_IN_CAPTURE) scope();
            String run = address.substring(0, 36), key = address.substring(37);
            var unit = index.tables().get("cds_import_unit").get(CdsImportKeys.hash(run, key));
            if (unit.fields().get("native_baseline") != null)
                try {
                    baseline =
                            CdsNativeBaselineCodec.decode(
                                            unit.fields().get("native_baseline"),
                                            unit.fields().get("target_references"),
                                            unit.fields().get("target_digest"))
                                    .rows()
                                    .stream()
                                    .filter(
                                            row ->
                                                    row.table().equals("demographiccust")
                                                            && row.id()
                                                                    .equals(
                                                                            Integer.toString(
                                                                                    patient)))
                                    .findFirst()
                                    .orElse(null);
                } catch (IllegalArgumentException invalid) {
                    scope();
                }
            else if (reference.reason() == Reason.UNCHANGED_ROW && !witness.partial())
                baseline = witness.state();
            if (baseline == null || !baseline.digest().equals(reference.expected().digest()))
                throw new IOException("CDS_EXPORT_DEMOGRAPHIC_NOTE_FIELD_BASELINE_REQUIRED");
            if (witness.partial()) {
                var partial = CdsExportPartialRowComparator.compare(baseline, witness.state());
                if (reference.reason() != Reason.RECEIPT_PARTIAL_PROJECTION
                        || !partial.equals(reference.projection())) scope();
                difference = partial.fields();
            } else difference = CdsNativeRowComparison.compare(baseline, witness.state());
            if (difference.schemaChanged() || !difference.unqualifiedColumns().isEmpty())
                throw new IOException("CDS_EXPORT_DEMOGRAPHIC_NOTE_FIELD_CODEC_REQUIRES_REVIEW");
            for (var other : index.references().entrySet())
                if (!other.getKey().equals(address)
                        && other.getValue().stream()
                                .anyMatch(
                                        ref ->
                                                ref.table().equals("demographiccust")
                                                        && ref.id()
                                                                .equals(Integer.toString(patient))))
                    throw new IOException("CDS_EXPORT_DEMOGRAPHIC_NOTE_ALIAS_REQUIRES_REVIEW");
        }
        String expected = prepared == null ? null : DemographicCust.wrapPatientNotes(prepared);
        if (prepared != null && source.isEmpty()) {
            rule = "PREPARED_SOURCE_PATIENT_NOTE_REQUIRED";
            review = true;
        } else if (prepared == null && !source.isEmpty()) {
            rule = "PREPARED_SOURCE_PATIENT_NOTE_PROJECTION_MISSING";
            review = true;
        } else if (reference == null && (prepared != null || !source.isEmpty())
                || baseline != null
                        && !CdsExportCapturedTextWitness.matches(baseline, "content", expected)) {
            rule = "PREPARED_NATIVE_PATIENT_NOTE_PROJECTION_NOT_PROVEN";
            review = true;
        } else if (baseline != null && !difference.changedColumns().contains("content")) {
            rule = "VERIFIED_UNCHANGED_SOURCE_PATIENT_NOTE";
            unchanged = true;
        } else if (mapping.fields().get(contentKey).outcome() == Outcome.REQUIRES_REVIEW) {
            rule = "CURRENT_NATIVE_PATIENT_NOTE_REQUIRES_REVIEW";
            review = true;
        } else if (evidence.preparation().details().diagnostics().stream()
                .anyMatch(
                        diagnostic ->
                                diagnostic
                                        .code()
                                        .equals("CDS_PATIENT_NOTE_COMPLETE_TEXT_SOURCE_VIEW")))
            // The imported note was too long for the native column, which received a pointer to
            // the source view instead. A CARLOS edit of that column is the patient note now: it
            // is written, and the superseded complete source note stays in the import's retained
            // source and is listed as replaced in the receipt.
            rule = "EDITED_CURRENT_PATIENT_NOTE_SUPERSEDES_SOURCE_ONLY_NOTE";
        else rule = "EXACT_CURRENT_NATIVE_PATIENT_NOTE";
        var currentAssessment = mapping.fields().get(contentKey);
        fields.put(
                contentKey,
                new CdsExportFieldAssessmentDto(
                        currentAssessment.presence(),
                        review
                                ? Outcome.REQUIRES_REVIEW
                                : unchanged && !source.isEmpty()
                                        ? Outcome.MAPPED
                                        : currentAssessment.outcome(),
                        "NoteAboutPatient",
                        rule));
        if (!review && !unchanged && (!source.isEmpty() || mapping.text() != null))
            changes.add(
                    new CdsExportRetainedSectionWriter.Change(
                            "NoteAboutPatient[1]",
                            ids,
                            mapping.text() == null ? null : xml -> xml.text(mapping.text())));
        values.requireActive();
        checkpoint.run();
        return new Result(
                index.sourceManifestSha256(),
                address,
                ids,
                fields,
                deferred,
                blockers,
                witness.partial(),
                changes);
    }

    private static void requireWitness(
            int patient,
            CdsExportCapturedTextRowDto row,
            CdsExportNativeRowReceiptDto witness,
            Runnable checkpoint)
            throws IOException {
        if (witness == null
                || witness.state() == null
                || !"demographiccust".equals(witness.table())
                || !Integer.toString(patient).equals(witness.id())
                || !row.recordKey().equals(witness.recordKey())
                || !row.exactNumbers().isEmpty()
                || !row.fields()
                        .keySet()
                        .equals(
                                Set.copyOf(
                                        witness.state().columns().stream()
                                                .map(CdsNativeRowDigest.Column::name)
                                                .toList()))
                || witness.partial() != !row.fields().containsKey("cust3")) scope();
        for (var column : witness.state().columns()) {
            checkpoint.run();
            if (!CdsExportCapturedTextWitness.matches(
                    witness.state(), column.name(), row.fields().get(column.name()))) scope();
        }
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_DEMOGRAPHIC_RETAINED_NOTE_SCOPE_INVALID");
    }
}
