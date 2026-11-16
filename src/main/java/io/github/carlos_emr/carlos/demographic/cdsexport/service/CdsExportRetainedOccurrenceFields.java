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
import java.io.IOException;
import java.util.*;

/** Resolves field-ID parent links to their separate applied preservation-unit receipt evidence. */
public final class CdsExportRetainedOccurrenceFields {
    public List<CdsExportRetainedFieldDto> fieldsFor(
            int patient,
            CdsCategory category,
            String nativeAddress,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> supplied,
            Runnable checkpoint)
            throws IOException {
        String prefix =
                switch (category) {
                    case DEMOGRAPHICS -> "CDS_EXPORT_DEMOGRAPHIC";
                    case ALLERGIES_AND_ADVERSE_REACTIONS -> "CDS_EXPORT_ALLERGY";
                    case MEDICATIONS_AND_TREATMENTS -> "CDS_EXPORT_MEDICATION";
                    case APPOINTMENTS -> "CDS_EXPORT_APPOINTMENT";
                    case IMMUNIZATIONS -> "CDS_EXPORT_IMMUNIZATION";
                    case LABORATORY_RESULTS -> "CDS_EXPORT_LABORATORY";
                    case NEW_CATEGORY -> "CDS_EXPORT_NEW_CATEGORY";
                    case CARE_ELEMENTS -> "CDS_EXPORT_CARE";
                    case REPORTS -> "CDS_EXPORT_REPORT";
                    case PERSONAL_HISTORY,
                            FAMILY_HISTORY,
                            PAST_HEALTH,
                            PROBLEM_LIST,
                            RISK_FACTORS,
                            ALERTS_AND_SPECIAL_NEEDS,
                            CLINICAL_NOTES ->
                            "CDS_EXPORT_HISTORY";
                    default ->
                            throw new IllegalArgumentException(
                                    "CDS_EXPORT_RETAINED_OCCURRENCE_CATEGORY_UNSUPPORTED");
                };
        if (!index.sourceManifestSha256().equals(checked.sourceManifestSha256())
                || !index.appliedUnits().contains(nativeAddress)
                || !index.appliedUnits().equals(checked.units().keySet())) scope(prefix);
        var metadata = index.tables().getOrDefault("cds_import_field", Map.of());
        var expected = new HashMap<String, CdsExportCapturedTextRowDto>();
        var verifiedPages = new HashSet<String>();
        // The fields a section receives from CdsExportRetainedValuesService know the fields of
        // each record: without that, every record costs a pass over every field of the chart
        // (4,096 records of a chart of 81,938 fields were 335 million steps a section).
        CdsExportRetainedFieldList known =
                supplied instanceof CdsExportRetainedFieldList list && list.index() == index
                        ? list
                        : null;
        if (known != null)
            for (String fieldId : known.idsOf(nativeAddress)) {
                checkpoint.run();
                expect(prefix, fieldId, metadata, index, checked, expected, verifiedPages);
            }
        else
            for (var parent : index.nativeParents().entrySet())
                if (nativeAddress.equals(parent.getValue())) {
                    checkpoint.run();
                    // nativeParents is field ID -> native unit address. A field's own unit is
                    // recorded in its row and has a different receipt from that native parent.
                    expect(
                            prefix,
                            parent.getKey(),
                            metadata,
                            index,
                            checked,
                            expected,
                            verifiedPages);
                }
        if (expected.isEmpty()) throw new IOException(prefix + "_SOURCE_FIELDS_REQUIRED");
        var result = new ArrayList<CdsExportRetainedFieldDto>();
        var mapper = new CdsExportRetainedFieldMapper();
        if (known != null) {
            // The same fields in the same order (the order of their ids), each decoded from its
            // row now and compared with the row this record expects.
            var ids = new ArrayList<>(expected.keySet());
            ids.sort(null);
            for (String id : ids) {
                checkpoint.run();
                var field = known.find(id);
                if (field == null
                        || field.category() != category
                        || !field.equals(mapper.map(patient, Set.of(category), expected.get(id))))
                    scope(prefix);
                result.add(field);
            }
            checkpoint.run();
            return List.copyOf(result);
        }
        var seen = new HashSet<String>();
        for (var field : supplied)
            if (expected.containsKey(field.id())) {
                checkpoint.run();
                if (!seen.add(field.id())
                        || field.category() != category
                        || !field.equals(
                                mapper.map(patient, Set.of(category), expected.get(field.id()))))
                    scope(prefix);
                result.add(field);
            }
        if (!seen.equals(expected.keySet())) scope(prefix);
        checkpoint.run();
        return List.copyOf(result);
    }

    /** Proves one field of the record against its page's verified receipt and expects its row. */
    private static void expect(
            String prefix,
            String fieldId,
            Map<String, CdsExportCapturedTextRowDto> metadata,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            Map<String, CdsExportCapturedTextRowDto> expected,
            Set<String> verifiedPages)
            throws IOException {
        var row = metadata.get(fieldId);
        if (row == null
                || !fieldId.equals(row.recordKey())
                || !fieldId.equals(row.fields().get("id"))) scope(prefix);
        String page = row.fields().get("run_id") + ":" + row.fields().get("unit_key");
        if (!index.appliedUnits().contains(page) || !checked.unchangedUnits().contains(page))
            throw new IOException(prefix + "_SOURCE_FIELD_EVIDENCE_CHANGED");
        var proof = checked.units().get(page);
        var original = index.references().get(page);
        if (proof == null
                || original == null
                || !proof.stream()
                        .map(CdsExportRetainedReceiptVerificationDto.CheckedReference::expected)
                        .toList()
                        .equals(original)
                || proof.stream()
                        .noneMatch(
                                ref ->
                                        ref.expected().table().equals("cds_import_field")
                                                && ref.expected().id().equals(fieldId)
                                                && ref.reason()
                                                        == CdsExportRetainedReceiptVerificationDto
                                                                .Reason.UNCHANGED_ROW))
            scope(prefix);
        if (verifiedPages.add(page)
                && proof.stream()
                        .anyMatch(
                                ref ->
                                        ref.reason()
                                                != CdsExportRetainedReceiptVerificationDto.Reason
                                                        .UNCHANGED_ROW)) scope(prefix);
        // No count of fields refuses a record: cds_import_field stores any number of them for
        // one unit, and these are references to rows the index already holds.
        if (expected.putIfAbsent(fieldId, row) != null) scope(prefix);
    }

    private static void scope(String prefix) throws IOException {
        throw new IOException(prefix + "_RETAINED_ASSEMBLY_SCOPE_INVALID");
    }
}
