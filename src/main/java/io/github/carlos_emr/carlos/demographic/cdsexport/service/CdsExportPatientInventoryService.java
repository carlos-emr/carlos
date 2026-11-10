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
                    "cds_laboratory_result", CdsCategory.LABORATORY_RESULTS,
                    "appointment", CdsCategory.APPOINTMENTS);
    private static final Set<CdsCategory> RETAINED =
            Set.of(CdsCategory.REPORTS, CdsCategory.CARE_ELEMENTS, CdsCategory.NEW_CATEGORY);
    private final CdsExportRetainedIndexService indices;
    private final CdsExportNativeRowReceiptReader rows;
    private final CdsExportHistoryInventoryService histories;

    public CdsExportPatientInventoryService(
            CdsExportRetainedIndexService indices, CdsExportNativeRowReceiptReader rows,
            CdsExportHistoryInventoryService histories) {
        this.indices = Objects.requireNonNull(indices);
        this.rows = Objects.requireNonNull(rows);
        this.histories = Objects.requireNonNull(histories);
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
        var sourceFields = new HashSet<String>();
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
                                add(
                                        expected,
                                        nativeIdentity(patientKey, category, row.table(), row.id()),
                                        budget);
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
        if (!Collections.disjoint(lease.document().command().categories(), CdsExportNoteSourceReader.CATEGORIES)) {
            var classified = new HashSet<String>();
            for (var occurrence : histories.plan(lease, patient, tables, source, index, bounds, control)) {
                if (!lease.document().command().categories().contains(occurrence.category())) invalid();
                for (long id : occurrence.revisions())
                    if (!classified.add(Long.toString(id))) invalid();
                add(expected, nativeIdentity(patientKey, occurrence.category(), occurrence.table(),
                        occurrence.nativeId()), budget);
            }
            if (!notes.equals(classified)) review();
        } else if (!notes.isEmpty()) invalid();
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
