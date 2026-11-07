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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.CheckedReference;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.Reason;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * Verifies historical receipt assertions against exact frozen rows. All requested references,
 * including duplicate references and replay aliases with different historical hashes, remain
 * distinct comparisons. A mismatch never selects source/current values automatically. A
 * missing adapter or receipt recipe is explicit review, never an unchanged projection claim.
 */
@Service
public class CdsExportRetainedReceiptVerificationService {
    private final CdsExportNativeRowReceiptReader rows;
    private final CdsExportNativeCaptureService captures;
    public CdsExportRetainedReceiptVerificationService(CdsExportNativeRowReceiptReader rows, CdsExportNativeCaptureService captures) {
        this.rows = java.util.Objects.requireNonNull(rows); this.captures = java.util.Objects.requireNonNull(captures);
    }
    public CdsExportRetainedReceiptVerificationDto verify(CdsExportLeaseDto lease, CdsExportCohortService.Patient patient,
            Set<String> requiredTables, CdsExportPatientCaptureDto source, CdsExportRetainedIndexDto retained,
            long maximumFields, long maximumBytes, int maximumReferences, CdsExportRunControl control) throws IOException {
        patient.require(lease); java.util.Objects.requireNonNull(control);
        if (maximumReferences < 1 || maximumReferences > 1_000_000 || !source.manifest().plaintextSha256().equals(retained.sourceManifestSha256())
                || !retained.references().keySet().equals(retained.appliedUnits())) scope();
        var historicalUnits = new HashMap<String, CdsExportCapturedTextRowDto>();
        for (var row : retained.tables().getOrDefault("cds_import_unit", java.util.Map.of()).values()) {
            String address = row.fields().get("run_id") + ":" + row.fields().get("unit_key");
            if (historicalUnits.putIfAbsent(address, row) != null) scope();
        }
        var addresses = new HashMap<Address, List<Slot>>(); var units = new TreeMap<String, List<Slot>>(); long count = 0;
        for (String unit : retained.appliedUnits()) {
            control.checkpoint(); var expected = retained.references().get(unit);
            if (expected == null || expected.isEmpty() || expected.size() > 192) scope();
            var slots = new ArrayList<Slot>(); units.put(unit, slots);
            var baselines = new HashMap<Address, io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState>();
            var historical = historicalUnits.get(unit);
            if (historical != null && historical.fields().get("native_baseline") != null) {
                var document = io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeBaselineCodec.decode(
                        historical.fields().get("native_baseline"), historical.fields().get("target_references"), historical.fields().get("target_digest"));
                for (var row : document.rows()) baselines.put(new Address(row.table(), row.id()), row);
            }
            for (var ref : expected) {
                control.checkpoint();
                if (++count > maximumReferences) throw new IOException("CDS_EXPORT_RETAINED_RECEIPT_RESOURCE_LIMIT");
                if (ref == null || ref.table() == null || !ref.table().matches("[A-Za-z0-9_]{1,64}")
                        || ref.id() == null || !ref.id().matches("[A-Za-z0-9_-]{1,64}") || ref.digest() == null || !ref.digest().matches("[0-9a-f]{64}")) scope();
                var slot = new Slot(ref, baselines.get(new Address(ref.table(), ref.id()))); slots.add(slot); addresses.computeIfAbsent(new Address(ref.table(), ref.id()), ignored -> new ArrayList<>()).add(slot);
            }
        }
        var completed = rows.scan(lease, patient, requiredTables, source, maximumFields, maximumBytes, control, actual -> {
            control.checkpoint();
            boolean currentUnqualified = actual.state() != null && !io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowComparison
                    .compare(actual.state(), actual.state()).unqualifiedColumns().isEmpty();
            // Equal legacy SQL digests may conceal different exact floating-point baselines.
            var compared = new HashMap<io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState,
                    io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowComparison>();
            for (var slot : addresses.getOrDefault(new Address(actual.table(), actual.id()), List.of())) {
                if (slot.seen) scope(); slot.seen = true; slot.current = actual.digest();
                if (actual.partial()) {
                    slot.reason = Reason.RECEIPT_PARTIAL_PROJECTION;
                    if (slot.baseline != null) slot.projection = CdsExportPartialRowComparator.compare(slot.baseline, actual.state());
                    continue;
                }
                if (slot.baseline != null && actual.state() != null) slot.fields =
                        compared.computeIfAbsent(slot.baseline, ignored -> io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowComparison.compare(slot.baseline, actual.state()));
                boolean missingNumericBaseline = slot.baseline == null && actual.state() != null
                        && java.util.stream.IntStream.range(0, actual.state().columns().size()).anyMatch(i ->
                                !actual.state().cells().get(i).sqlNull()
                                && io.github.carlos_emr.carlos.demographic.cds.contract.CdsExactNumeric.codec(actual.state().columns().get(i).type()) != null);
                slot.reason = actual.digest() == null ? Reason.RECEIPT_METADATA_UNAVAILABLE
                        : currentUnqualified || missingNumericBaseline || slot.fields != null && (!slot.fields.unqualifiedColumns().isEmpty() || slot.fields.schemaChanged())
                                ? Reason.RECEIPT_FIELD_CODEC_UNQUALIFIED
                        : slot.fields != null && !slot.fields.changedColumns().isEmpty() ? Reason.CHANGED_ROW
                        : actual.digest().equals(slot.reference.digest()) ? Reason.UNCHANGED_ROW : Reason.CHANGED_ROW;
            }
        });
        if (!completed.sourceManifestSha256().equals(source.manifest().plaintextSha256())) scope();
        var results = new TreeMap<String, List<CheckedReference>>(); var unchanged = new HashSet<String>(); var review = new HashSet<String>();
        for (var unit : units.entrySet()) {
            control.checkpoint(); var checked = new ArrayList<CheckedReference>(); boolean all = true;
            for (var slot : unit.getValue()) {
                control.checkpoint();
                if (!slot.seen) slot.reason = completed.rows().containsKey(slot.reference.table()) ? Reason.ROW_NOT_IN_CAPTURE : Reason.RECEIPT_ADAPTER_UNAVAILABLE;
                checked.add(new CheckedReference(slot.reference, slot.current, slot.reason, slot.fields, slot.projection)); all &= slot.reason == Reason.UNCHANGED_ROW;
            }
            results.put(unit.getKey(), checked); (all ? unchanged : review).add(unit.getKey());
        }
        captures.authorizeSources(lease, patient, requiredTables, control); patient.require(lease); control.checkpoint();
        return new CdsExportRetainedReceiptVerificationDto(source.manifest().plaintextSha256(), results, unchanged, review);
    }
    private record Address(String table, String id) { @Override public String toString() { return "CapturedReference[protected]"; } }
    private static final class Slot {
        private final CdsExportRetainedIndexDto.Reference reference;
        private final io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState baseline;
        private io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowComparison fields;
        private CdsExportPartialRowComparisonDto projection;
        private String current;
        private Reason reason;
        private boolean seen;
        private Slot(CdsExportRetainedIndexDto.Reference reference, io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState baseline) { this.reference = reference; this.baseline = baseline; }
    }
    private static void scope() throws IOException { throw new IOException("CDS_EXPORT_RETAINED_RECEIPT_SCOPE_INVALID"); }
}
