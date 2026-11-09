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

import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.Reason;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Reconciles current appointment fields against proved import projections. Effective captured
 * calendar/clock values replace source timing so prior import options cannot be undone.
 * Unknown native extras, calendar history, aliases and missing projection evidence need review.
 */
public final class CdsExportAppointmentFieldReconciler {
    public record Result(
            String sourceManifestSha256,
            String unitAddress,
            CdsExportAppointmentMappingDto mapping,
            Set<String> preparedPaths,
            Set<String> currentPaths) {
        public Result {
            preparedPaths = Set.copyOf(preparedPaths);
            currentPaths = Set.copyOf(currentPaths);
        }

        @Override
        public String toString() {
            return "CdsExportAppointmentFieldReconciler.Result[protected]";
        }
    }

    public Result reconcile(
            int patient,
            CdsExportRetainedIndexDto index,
            String address,
            CdsExportRetainedReceiptVerificationDto checked,
            CdsExportCapturedTextRowDto row,
            CdsExportNativeRowReceiptDto witness,
            CdsExportAppointmentLookupsDto lookups,
            CdsExportCapturedTextRowDto demographic,
            ZoneId clinicalZone,
            List<CdsExportRetainedFieldDto> retained,
            CdsExportRetainedSectionWriter.Values values,
            Runnable checkpoint)
            throws IOException {
        checkpoint.run();
        var prepared = new CdsExportRetainedPreparationReader().appointment(index, address).value();
        if (!index.sourceManifestSha256().equals(checked.sourceManifestSha256())
                || !index.sourceManifestSha256().equals(lookups.sourceManifestSha256())
                || !index.appliedUnits().equals(checked.units().keySet())) scope();
        var comparisons = checked.units().get(address);
        var refs = index.references().get(address);
        if (comparisons == null
                || refs == null
                || refs.size() != 1
                || !comparisons.stream()
                        .map(CdsExportRetainedReceiptVerificationDto.CheckedReference::expected)
                        .toList()
                        .equals(refs)
                || !refs.getFirst().table().equals("appointment")) scope();
        var ref = refs.getFirst();
        if (row == null || comparisons.getFirst().reason() == Reason.ROW_NOT_IN_CAPTURE)
            review("CDS_EXPORT_APPOINTMENT_DELETION_REQUIRES_REVIEW");
        CdsExportAppointmentProjectionEvidence.verifyCurrent(row, witness);
        if (!ref.id().equals(row.fields().get("appointment_no"))
                || !Integer.toString(patient).equals(row.fields().get("demographic_no"))
                || !Objects.equals(witness.digest(), comparisons.getFirst().currentDigest()))
            scope();
        for (var entry : index.references().entrySet())
            if (!entry.getKey().equals(address)
                    && entry.getValue().stream()
                            .anyMatch(
                                    other ->
                                            other.table().equals("appointment")
                                                    && other.id().equals(ref.id())))
                review("CDS_EXPORT_APPOINTMENT_ALIAS_REQUIRES_REVIEW");
        var unit =
                index.tables()
                        .get("cds_import_unit")
                        .get(CdsImportKeys.hash(address.substring(0, 36), address.substring(37)));
        CdsNativeRowState baseline = null;
        if (unit.fields().get("native_baseline") != null)
            try {
                var old =
                        CdsNativeBaselineCodec.decode(
                                unit.fields().get("native_baseline"),
                                unit.fields().get("target_references"),
                                unit.fields().get("target_digest"));
                if (old.rows().size() != 1) scope();
                baseline = old.rows().getFirst();
            } catch (IllegalArgumentException failed) {
                scope();
            }
        if (baseline == null && comparisons.getFirst().reason() == Reason.UNCHANGED_ROW)
            baseline = witness.state();
        if (baseline == null)
            baseline =
                    CdsExportAppointmentProjectionEvidence.legacyNullLocationBaseline(
                            witness.state(), ref.digest());
        if (baseline == null
                || !baseline.digest().equals(ref.digest())
                || !baseline.table().equals("appointment")
                || !baseline.id().equals(ref.id()))
            review("CDS_EXPORT_APPOINTMENT_FIELD_BASELINE_REQUIRED");
        var difference = CdsExportAppointmentProjectionEvidence.compare(baseline, witness.state());
        if (difference.schemaChanged() || !difference.unqualifiedColumns().isEmpty())
            review("CDS_EXPORT_APPOINTMENT_FIELD_CODEC_REQUIRES_REVIEW");
        var expected = new LinkedHashMap<String, String>();
        expected.put("appointment_no", ref.id());
        expected.put("demographic_no", Integer.toString(patient));
        expected.put("appointment_date", prepared.date());
        expected.put("start_time", clock(prepared.start()));
        expected.put("end_time", clock(prepared.end()));
        expected.put("provider_no", prepared.providerNo() == null ? "" : prepared.providerNo());
        expected.put("reason", prepared.purpose());
        expected.put("notes", prepared.notes());
        expected.put("imported_status", prepared.status());
        expected.put("status", "");
        for (var entry : expected.entrySet())
            if (!CdsExportAppointmentProjectionEvidence.matches(
                    baseline, entry.getKey(), entry.getValue()))
                review("CDS_EXPORT_APPOINTMENT_PREPARED_PROJECTION_NOT_PROVEN");
        var original =
                new CdsExportAppointmentOriginalValues()
                        .read(patient, address, index, checked, retained, values, checkpoint);
        var candidate =
                new CdsExportAppointmentMapper()
                        .candidate(patient, row, lookups, clinicalZone, demographic);
        var fields = new LinkedHashMap<>(candidate.fields());
        var prior = new HashSet<String>();
        var current = new HashSet<String>();
        String purpose = candidate.value() == null ? null : candidate.value().purpose();
        if (!difference.changedColumns().contains("reason")) {
            if (prepared.diagnostics().stream()
                    .anyMatch(
                            notice ->
                                    notice.code().equals("CDS_APPOINTMENT_PURPOSE_SOURCE_VIEW"))) {
                if (!"CDS: complete appointment purpose is available in CDS source fields."
                                .equals(prepared.purpose())
                        || original.purpose() == null)
                    review("CDS_EXPORT_APPOINTMENT_ORIGINAL_PURPOSE_REQUIRED");
                purpose = original.purpose();
            } else {
                if (!Objects.equals(original.purpose(), prepared.purpose()))
                    review("CDS_EXPORT_APPOINTMENT_ORIGINAL_PURPOSE_REQUIRED");
                purpose = prepared.purpose();
            }
            prior.add("AppointmentPurpose");
            mark(
                    row,
                    fields,
                    "reason",
                    Outcome.MAPPED,
                    "AppointmentPurpose",
                    "VERIFIED_UNCHANGED_PREPARED_PURPOSE");
        } else current.add("AppointmentPurpose");
        String notes = candidate.value() == null ? null : candidate.value().notes();
        if (!difference.changedColumns().contains("notes")) {
            if (!Objects.equals(original.notes(), prepared.notes()))
                review("CDS_EXPORT_APPOINTMENT_ORIGINAL_NOTES_REQUIRED");
            notes = prepared.notes();
            prior.add("AppointmentNotes");
            mark(
                    row,
                    fields,
                    "notes",
                    Outcome.MAPPED,
                    "AppointmentNotes",
                    "VERIFIED_UNCHANGED_PREPARED_NOTES");
        } else current.add("AppointmentNotes");
        var provider = candidate.value() == null ? null : candidate.value().provider();
        if (!difference.changedColumns().contains("provider_no")) {
            if (prepared.providerNo() != null
                    && !prepared.providerNo().isEmpty()
                    && original.provider() == null)
                review("CDS_EXPORT_APPOINTMENT_ORIGINAL_PROVIDER_REQUIRED");
            provider = original.provider();
            prior.add("Provider");
            mark(
                    row,
                    fields,
                    "provider_no",
                    Outcome.MAPPED,
                    "Provider",
                    "VERIFIED_UNCHANGED_RECORDED_PROVIDER");
            for (var entry : new ArrayList<>(fields.entrySet()))
                if (entry.getKey().table().equals("provider"))
                    fields.put(
                            entry.getKey(),
                            new CdsExportFieldAssessmentDto(
                                    entry.getValue().presence(),
                                    Outcome.OPERATIONAL,
                                    "Provider",
                                    "ORIGINAL_RECORDED_PROVIDER_PRESERVED"));
        } else current.add("Provider");
        String status = candidate.value() == null ? null : candidate.value().status();
        String code = row.fields().get("status");
        if (code == null || code.isEmpty()) {
            status = row.fields().get("imported_status");
            if (status != null && !CdsExportNativeFieldAssessments.legal(status, 250))
                review("CDS_EXPORT_APPOINTMENT_STATUS_REQUIRES_REVIEW");
            mark(
                    row,
                    fields,
                    "imported_status",
                    status == null || status.isEmpty() ? Outcome.ABSENT : Outcome.MAPPED,
                    "AppointmentStatus",
                    "EXACT_CURRENT_RECORDED_IMPORTED_STATUS");
            (difference.changedColumns().contains("imported_status") ? current : prior)
                    .add("AppointmentStatus");
        } else if (!difference.changedColumns().contains("imported_status")) {
            mark(
                    row,
                    fields,
                    "imported_status",
                    Outcome.OPERATIONAL,
                    "AppointmentStatus",
                    "VERIFIED_PRIOR_IMPORTED_STATUS_AFTER_CURRENT_STATUS_EDIT");
            current.add("AppointmentStatus");
        }
        boolean blocked =
                candidate.value() == null
                        || fields.values().stream()
                                .anyMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        CdsExportAppointmentDto result =
                blocked
                        ? null
                        : new CdsExportAppointmentDto(
                                candidate.value().timing(), status, provider, purpose, notes);
        values.requireActive();
        checkpoint.run();
        return new Result(
                index.sourceManifestSha256(),
                address,
                new CdsExportAppointmentMappingDto(result, fields),
                prior,
                current);
    }

    private static String clock(String value) throws IOException {
        try {
            return LocalTime.parse(value)
                    .format(DateTimeFormatter.ofPattern("HH:mm:ss", java.util.Locale.ROOT));
        } catch (RuntimeException failed) {
            scope();
            return null;
        }
    }

    private static void mark(
            CdsExportCapturedTextRowDto row,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            String column,
            Outcome outcome,
            String path,
            String rule)
            throws IOException {
        var key = new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column);
        var old = fields.get(key);
        if (old == null) scope();
        fields.put(key, new CdsExportFieldAssessmentDto(old.presence(), outcome, path, rule));
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_APPOINTMENT_RECONCILIATION_SCOPE_INVALID");
    }

    private static void review(String code) throws IOException {
        throw new IOException(code);
    }
}
