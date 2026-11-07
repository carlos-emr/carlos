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
package io.github.carlos_emr.carlos.demographic.cdsexport.dto;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Historical whole-row reference comparisons against one captured generation. An unchanged
 * reference set does not qualify the projection mapping, resolve aliases, justify replay of
 * original values or establish field-level/whole-patient clinical equivalence.
 */
public record CdsExportRetainedReceiptVerificationDto(String sourceManifestSha256,
        Map<String, List<CheckedReference>> units, Set<String> unchangedUnits, Set<String> reviewUnits) {
    public enum Reason { UNCHANGED_ROW, CHANGED_ROW, ROW_NOT_IN_CAPTURE, RECEIPT_METADATA_UNAVAILABLE, RECEIPT_ADAPTER_UNAVAILABLE, RECEIPT_FIELD_CODEC_UNQUALIFIED, RECEIPT_PARTIAL_PROJECTION }
    public record CheckedReference(CdsExportRetainedIndexDto.Reference expected, String currentDigest, Reason reason,
            io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowComparison fields, CdsExportPartialRowComparisonDto projection) {
        public CheckedReference(CdsExportRetainedIndexDto.Reference expected, String currentDigest, Reason reason) { this(expected, currentDigest, reason, null, null); }
        public CheckedReference(CdsExportRetainedIndexDto.Reference expected, String currentDigest, Reason reason,
                io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowComparison fields) { this(expected, currentDigest, reason, fields, null); }
        public CheckedReference {
            if (expected == null || expected.digest() == null || !expected.digest().matches("[0-9a-f]{64}") || reason == null
                    || currentDigest != null && !currentDigest.matches("[0-9a-f]{64}")
                    || (reason == Reason.UNCHANGED_ROW || reason == Reason.CHANGED_ROW || reason == Reason.RECEIPT_FIELD_CODEC_UNQUALIFIED || reason == Reason.RECEIPT_PARTIAL_PROJECTION) != (currentDigest != null)
                    || reason == Reason.RECEIPT_PARTIAL_PROJECTION && (fields != null || !"demographiccust".equals(expected.table()))
                    || projection != null && reason != Reason.RECEIPT_PARTIAL_PROJECTION
                    || projection != null && (!projection.comparedColumns().equals(Set.of("content", "cust1", "cust2", "cust4", "demographic_no"))
                            || !projection.omittedColumns().equals(Set.of("cust3")))
                    || reason == Reason.UNCHANGED_ROW && !expected.digest().equals(currentDigest)
                    || reason == Reason.CHANGED_ROW && expected.digest().equals(currentDigest)
                            && (fields == null || fields.schemaChanged() || !fields.unqualifiedColumns().isEmpty() || fields.changedColumns().isEmpty())
                    || reason == Reason.UNCHANGED_ROW && fields != null && !fields.unchangedProjection()) {
                throw new IllegalArgumentException("CDS_EXPORT_RETAINED_RECEIPT_CHECK_INVALID");
            }
        }
        @Override public String toString() { return "CdsExportRetainedReceiptVerificationDto.CheckedReference[protected]"; }
    }
    public CdsExportRetainedReceiptVerificationDto {
        if (sourceManifestSha256 == null || !sourceManifestSha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("CDS_EXPORT_RETAINED_RECEIPT_VERIFICATION_INVALID");
        unchangedUnits = Set.copyOf(unchangedUnits); reviewUnits = Set.copyOf(reviewUnits);
        var copied = new TreeMap<String, List<CheckedReference>>(); units.forEach((unit, refs) -> copied.put(unit, List.copyOf(refs)));
        units = Collections.unmodifiableMap(copied); var all = new java.util.HashSet<>(unchangedUnits); all.addAll(reviewUnits);
        if (!Collections.disjoint(unchangedUnits, reviewUnits) || !all.equals(units.keySet())) throw new IllegalArgumentException("CDS_EXPORT_RETAINED_RECEIPT_VERIFICATION_INVALID");
        for (var unit : units.entrySet()) {
            if (unit.getValue().isEmpty() || unit.getValue().size() > 192
                    || unchangedUnits.contains(unit.getKey()) != unit.getValue().stream().allMatch(ref -> ref.reason() == Reason.UNCHANGED_ROW)) {
                throw new IllegalArgumentException("CDS_EXPORT_RETAINED_RECEIPT_VERIFICATION_INVALID");
            }
        }
    }
    @Override public String toString() { return "CdsExportRetainedReceiptVerificationDto[protected]"; }
}
