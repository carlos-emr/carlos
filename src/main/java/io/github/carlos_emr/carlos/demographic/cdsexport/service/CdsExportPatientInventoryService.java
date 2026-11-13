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
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Expected occurrence identities from authenticated source metadata and native row witnesses.
 * This service never receives mapper receipts or serialized XML. It does not attest values,
 * resolve changed projections, or turn the registered reader set into a complete chart catalogue.
 */
@Service
public class CdsExportPatientInventoryService {
    private static final Map<String, CdsCategory> NATIVE =
            Map.of(
                    "demographic", CdsCategory.DEMOGRAPHICS,
                    "allergies", CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
                    "drugs", CdsCategory.MEDICATIONS_AND_TREATMENTS,
                    "preventions", CdsCategory.IMMUNIZATIONS,
                    // Every captured screening row is one PastHealth procedure occurrence.
                    CdsExportPreventionSources.SCREENINGS, CdsCategory.PAST_HEALTH,
                    "cds_laboratory_result", CdsCategory.LABORATORY_RESULTS,
                    "appointment", CdsCategory.APPOINTMENTS,
                    // Every current Disease Registry entry and episode is one ProblemList item.
                    CdsExportProblemRegistrySources.REGISTRY, CdsCategory.PROBLEM_LIST,
                    CdsExportProblemRegistrySources.EPISODES, CdsCategory.PROBLEM_LIST);
    private static final Set<String> PREVENTIONS =
            Set.of("preventions", CdsExportPreventionSources.SCREENINGS);
    private static final Set<CdsCategory> RETAINED =
            Set.of(CdsCategory.REPORTS, CdsCategory.CARE_ELEMENTS, CdsCategory.NEW_CATEGORY);
    private final CdsExportRetainedIndexService indices;
    private final CdsExportNativeRowReceiptReader rows;
    private final CdsExportHistoryInventoryService histories;
    private final CdsExportCareOccurrenceService care;
    private final CdsExportLaboratoryOccurrenceService laboratories;
    private final CdsExportPrescriptionOccurrenceService prescriptions;
    private final CdsExportDataSummaryOccurrenceService dataSummaries;
    private final CdsExportHospitalReportOccurrenceService hospitalReports;
    private final CdsExportNativeCaptureService captures;

    /**
     * Without data summaries or HRM reports: a capture that holds data-summary or HRM tables is
     * refused.
     */
    public CdsExportPatientInventoryService(
            CdsExportRetainedIndexService indices,
            CdsExportNativeRowReceiptReader rows,
            CdsExportHistoryInventoryService histories,
            CdsExportCareOccurrenceService care,
            CdsExportLaboratoryOccurrenceService laboratories,
            CdsExportPrescriptionOccurrenceService prescriptions) {
        this(indices, rows, histories, care, laboratories, prescriptions, null, null, null);
    }

    /** Without HRM reports: a capture that holds the HRM tables is refused. */
    public CdsExportPatientInventoryService(
            CdsExportRetainedIndexService indices,
            CdsExportNativeRowReceiptReader rows,
            CdsExportHistoryInventoryService histories,
            CdsExportCareOccurrenceService care,
            CdsExportLaboratoryOccurrenceService laboratories,
            CdsExportPrescriptionOccurrenceService prescriptions,
            CdsExportDataSummaryOccurrenceService dataSummaries) {
        this(
                indices,
                rows,
                histories,
                care,
                laboratories,
                prescriptions,
                dataSummaries,
                null,
                null);
    }

    /**
     * Without demographic capture access: the native demographic NewCategory record and the
     * patient alert occurrence are not predicted, so a capture whose patient has either is
     * refused when the occurrence is emitted. Test fixtures without those rows only.
     */
    public CdsExportPatientInventoryService(
            CdsExportRetainedIndexService indices,
            CdsExportNativeRowReceiptReader rows,
            CdsExportHistoryInventoryService histories,
            CdsExportCareOccurrenceService care,
            CdsExportLaboratoryOccurrenceService laboratories,
            CdsExportPrescriptionOccurrenceService prescriptions,
            CdsExportDataSummaryOccurrenceService dataSummaries,
            CdsExportHospitalReportOccurrenceService hospitalReports) {
        this(
                indices,
                rows,
                histories,
                care,
                laboratories,
                prescriptions,
                dataSummaries,
                hospitalReports,
                null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportPatientInventoryService(
            CdsExportRetainedIndexService indices,
            CdsExportNativeRowReceiptReader rows,
            CdsExportHistoryInventoryService histories,
            CdsExportCareOccurrenceService care,
            CdsExportLaboratoryOccurrenceService laboratories,
            CdsExportPrescriptionOccurrenceService prescriptions,
            CdsExportDataSummaryOccurrenceService dataSummaries,
            CdsExportHospitalReportOccurrenceService hospitalReports,
            CdsExportNativeCaptureService captures) {
        this.captures = captures;
        this.dataSummaries = dataSummaries;
        this.hospitalReports = hospitalReports;
        this.indices = Objects.requireNonNull(indices);
        this.rows = Objects.requireNonNull(rows);
        this.histories = Objects.requireNonNull(histories);
        this.care = Objects.requireNonNull(care);
        this.laboratories = Objects.requireNonNull(laboratories);
        this.prescriptions = Objects.requireNonNull(prescriptions);
    }

    public Inventory capture(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            String patientKey,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        patient.require(lease);
        if (!tables.equals(source.capture().tables().keySet())
                || !lease.runId().equals(source.capture().runId())
                || lease.epoch() != source.capture().epoch()
                || patient.patient() != source.capture().patient()) invalid();
        var index =
                indices.index(
                        lease,
                        patient,
                        tables,
                        source.manifest(),
                        bounds.fields(),
                        bounds.bytes(),
                        bounds.indexRows(),
                        bounds.indexBytes(),
                        control);
        if (!index.sourceManifestSha256().equals(source.manifest().plaintextSha256())) invalid();
        var expected = new TreeSet<CdsExportIdentityDto>();
        var notes = new HashSet<String>();
        var documents = new HashSet<String>();
        // A soft-deleted document or deleted patient link is captured and accounted by the
        // report occurrence service but is not in the chart, so it is never an occurrence.
        var removedDocuments = new HashSet<String>();
        var sourceFields = new HashSet<String>();
        var superseded = new HashSet<String>();
        var budget = new Budget(bounds.indexRows(), bounds.indexBytes());
        var actual =
                rows.scan(
                        lease,
                        patient,
                        tables,
                        source,
                        bounds.fields(),
                        bounds.bytes(),
                        control,
                        row -> {
                            control.checkpoint();
                            var category = NATIVE.get(row.table());
                            if (category != null) {
                                if (!lease.document().command().categories().contains(category))
                                    invalid();
                                // An edited or deleted prevention version is captured and
                                // accounted by its occurrence service but never exported.
                                if (PREVENTIONS.contains(row.table())
                                        && CdsExportPreventionVersions.superseded(row)) {
                                    if (!superseded.add(row.table() + "/" + row.id())) invalid();
                                    budget.add();
                                    return;
                                }
                                // A deleted registry entry or episode is captured and accounted
                                // by its occurrence service but is not a current chart fact.
                                if (category == CdsCategory.PROBLEM_LIST
                                        && CdsExportProblemRegistrySources.removed(row)) {
                                    if (!superseded.add(row.table() + "/" + row.id())) invalid();
                                    budget.add();
                                    return;
                                }
                                // A screening's identity names its physical prevention row.
                                add(
                                        expected,
                                        nativeIdentity(
                                                patientKey,
                                                category,
                                                row.table().equals(CdsExportPreventionSources.SCREENINGS)
                                                        ? "preventions"
                                                        : row.table(),
                                                row.id()),
                                        budget);
                            } else if (row.table().equals("document")) {
                                if (!documents.add(row.id())) invalid();
                                if (CdsExportDocumentRepresentation.removed(row))
                                    removedDocuments.add(row.id());
                                budget.add();
                            } else if (row.table().equals("ctl_document")) {
                                // The link id is "<patient>_<document>"; only this patient's
                                // own links are captured (Plan.DOCUMENT_LINKS).
                                String prefix = patient.patient() + "_";
                                if (!row.id().startsWith(prefix)) invalid();
                                if (CdsExportDocumentRepresentation.removed(row)) {
                                    removedDocuments.add(row.id().substring(prefix.length()));
                                    budget.add();
                                }
                            } else if (row.table().equals("casemgmt_note")) {
                                if (!notes.add(row.id())) invalid();
                                budget.add();
                            }
                        });
        if (!actual.sourceManifestSha256().equals(source.manifest().plaintextSha256())) invalid();
        for (var unit : index.tables().getOrDefault("cds_import_unit", Map.of()).values()) {
            control.checkpoint();
            String wire = unit.fields().get("category");
            if (!CdsCategory.wireNames().contains(wire)) continue;
            var category = CdsCategory.fromWireName(wire);
            String address = unit.fields().get("run_id") + ":" + unit.fields().get("unit_key");
            if (!index.appliedUnits().contains(address)) continue;
            if (!lease.document().command().categories().contains(category)) invalid();
            if (RETAINED.contains(category)) {
                add(
                        expected,
                        new CdsExportIdentityDto(
                                patientKey,
                                category,
                                CdsImportKeys.hash("carlos-retained-occurrence-v1", address)),
                        budget);
            }
        }
        for (String address : index.appliedUnits())
            for (var reference : index.references().getOrDefault(address, List.of()))
                if (reference.table().equals("document")) documents.remove(reference.id());
        documents.removeAll(removedDocuments);
        for (String document : documents) {
            if (!lease.document().command().categories().contains(CdsCategory.REPORTS)) invalid();
            add(
                    expected,
                    nativeIdentity(patientKey, CdsCategory.REPORTS, "document", document),
                    budget);
        }
        if (!Collections.disjoint(
                lease.document().command().categories(), CdsExportNoteSourceReader.CATEGORIES)) {
            var classified = new HashSet<String>();
            for (var occurrence :
                    histories.plan(lease, patient, tables, source, index, bounds, control)) {
                if (!lease.document().command().categories().contains(occurrence.category()))
                    invalid();
                for (long id : occurrence.revisions())
                    if (!classified.add(Long.toString(id))) invalid();
                add(
                        expected,
                        nativeIdentity(
                                patientKey,
                                occurrence.category(),
                                occurrence.table(),
                                occurrence.nativeId()),
                        budget);
            }
            if (!notes.equals(classified)) review();
        } else if (!notes.isEmpty()) invalid();
        // The native demographic NewCategory record and the patient alert: each is one
        // occurrence when the patient has a value it carries (CdsExportDemographicSupportingMapper,
        // CdsExportPatientAlertMapper), decided from the same captured rows the emitters read.
        if (captures != null) {
            var demographic = new CdsExportDemographicSupportingReader(captures);
            String id = Integer.toString(patient.patient());
            if (lease.document().command().categories().contains(CdsCategory.NEW_CATEGORY)
                    && CdsExportDemographicSupportingMapper.map(
                                    demographic.rows(
                                            lease, patient, tables, source, bounds, control),
                                    control::checkpoint)
                            != null)
                add(
                        expected,
                        nativeIdentity(patientKey, CdsCategory.NEW_CATEGORY, "demographic", id),
                        budget);
            if (lease.document()
                            .command()
                            .categories()
                            .contains(CdsCategory.ALERTS_AND_SPECIAL_NEEDS)
                    && CdsExportPatientAlertMapper.map(
                                    patient.patient(),
                                    demographic.custom(
                                            lease, patient, tables, source, bounds, control))
                            != null)
                add(
                        expected,
                        nativeIdentity(
                                patientKey,
                                CdsCategory.ALERTS_AND_SPECIAL_NEEDS,
                                CdsExportPatientAlertMapper.TABLE,
                                id),
                        budget);
        }
        for (var observation :
                care.nativeSources(lease, patient, tables, source, index, bounds, control)) {
            // A deleted or superseded version is accounted, never an expected occurrence.
            if (observation.deleted()) continue;
            if (!lease.document().command().categories().contains(CdsCategory.NEW_CATEGORY))
                review();
            if (observation.element() != null)
                add(
                        expected,
                        nativeIdentity(
                                patientKey,
                                CdsCategory.CARE_ELEMENTS,
                                observation.table(),
                                observation.id()),
                        budget);
            add(
                    expected,
                    nativeIdentity(
                            patientKey,
                            CdsCategory.NEW_CATEGORY,
                            observation.table(),
                            observation.id()),
                    budget);
        }
        for (var laboratory :
                laboratories.nativeSources(
                        lease, patient, tables, source, index, bounds, control)) {
            if (!lease.document().command().categories().contains(CdsCategory.NEW_CATEGORY))
                review();
            // An embedded document is a Reports entry (CdsExportLaboratoryDocumentMapper);
            // excluding Reports would leave it unexported, so the selection needs review.
            if (!laboratory.parsed().documents().isEmpty()
                    && !lease.document().command().categories().contains(CdsCategory.REPORTS))
                review();
            for (var document : laboratory.parsed().documents())
                add(
                        expected,
                        nativeIdentity(
                                patientKey,
                                CdsCategory.REPORTS,
                                "hl7TextMessage",
                                laboratory.id() + ":" + document.ordinal()),
                        budget);
            for (var observation : laboratory.parsed().observations())
                add(
                        expected,
                        nativeIdentity(
                                patientKey,
                                CdsCategory.LABORATORY_RESULTS,
                                "hl7TextMessage",
                                laboratory.id() + ":" + observation.ordinal()),
                        budget);
            add(
                    expected,
                    nativeIdentity(
                            patientKey,
                            CdsCategory.NEW_CATEGORY,
                            "hl7TextMessage",
                            laboratory.id()),
                    budget);
        }
        for (var prescription :
                prescriptions.sources(lease, patient, tables, source, bounds, control)) {
            add(
                    expected,
                    nativeIdentity(
                            patientKey,
                            CdsCategory.NEW_CATEGORY,
                            "prescription",
                            prescription.prescription().fields().get("script_no")),
                    budget);
        }
        // HRM reports: one Reports occurrence per report linked to the patient.
        if (CdsExportHospitalReportOccurrenceService.captured(tables)) {
            if (hospitalReports == null
                    || !lease.document().command().categories().contains(CdsCategory.REPORTS))
                invalid();
            for (var identity :
                    hospitalReports.identities(
                            lease, patient, patientKey, tables, source, bounds, control))
                add(expected, identity, budget);
        }
        // Data-summary records (registration point): one Reports occurrence per planned record.
        if (CdsExportDataSummaryOccurrenceService.captured(tables)) {
            if (dataSummaries == null
                    || !lease.document().command().categories().contains(CdsCategory.REPORTS))
                invalid();
            for (var identity :
                    dataSummaries.identities(
                            lease, patient, patientKey, tables, source, bounds, control))
                add(expected, identity, budget);
        }
        var mapper = new CdsExportRetainedFieldMapper();
        for (var row : index.tables().getOrDefault("cds_import_field", Map.of()).values()) {
            control.checkpoint();
            var field = mapper.map(patient.patient(), lease.document().command().categories(), row);
            if (!sourceFields.add(field.id())) invalid();
            budget.add();
        }
        if (expected.stream()
                        .filter(identity -> identity.category() == CdsCategory.DEMOGRAPHICS)
                        .count()
                != 1) invalid();
        control.authorizePatient(patient.patient());
        patient.require(lease);
        control.checkpoint();
        return new Inventory(
                source.manifest().plaintextSha256(), patientKey, expected, sourceFields);
    }

    private static CdsExportIdentityDto nativeIdentity(
            String patient, CdsCategory category, String table, String id) {
        return new CdsExportIdentityDto(
                patient, category, CdsImportKeys.hash("carlos-native-occurrence-v1", table, id));
    }

    private static void add(
            Set<CdsExportIdentityDto> identities, CdsExportIdentityDto identity, Budget budget)
            throws IOException {
        if (!identities.add(identity)) invalid();
        budget.add();
    }

    private static final class Budget {
        private final long rows, bytes;
        private long used;

        Budget(long rows, long bytes) {
            this.rows = rows;
            this.bytes = bytes;
        }

        void add() {
            if (++used > rows || used > bytes / 512)
                throw new CdsExportReviewException(CdsExportReviewException.Reason.RESOURCE_LIMIT);
        }
    }

    /** Created only after complete authenticated inventory reads. */
    public static final class Inventory {
        private final String sourceManifestSha256, patientKey;
        private final SortedSet<CdsExportIdentityDto> identities;
        private final Set<String> retainedFields;

        private Inventory(
                String source,
                String patient,
                SortedSet<CdsExportIdentityDto> identities,
                Set<String> fields) {
            sourceManifestSha256 = source;
            patientKey = patient;
            this.identities = Collections.unmodifiableSortedSet(new TreeSet<>(identities));
            retainedFields = Set.copyOf(fields);
        }

        public String sourceManifestSha256() {
            return sourceManifestSha256;
        }

        public String patientKey() {
            return patientKey;
        }

        public SortedSet<CdsExportIdentityDto> identities() {
            return identities;
        }

        public Set<String> retainedFields() {
            return retainedFields;
        }

        @Override
        public String toString() {
            return "CdsExportPatientInventory[protected]";
        }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_PATIENT_INVENTORY_INVALID");
    }

    private static void review() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.UNMAPPED_DATA);
    }
}
