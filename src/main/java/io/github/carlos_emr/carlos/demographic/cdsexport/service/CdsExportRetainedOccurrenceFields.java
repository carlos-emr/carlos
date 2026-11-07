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
    public List<CdsExportRetainedFieldDto> fieldsFor(int patient, CdsCategory category, String nativeAddress,
            CdsExportRetainedIndexDto index, CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> supplied, Runnable checkpoint) throws IOException {
        String prefix = switch (category) {
            case DEMOGRAPHICS -> "CDS_EXPORT_DEMOGRAPHIC";
            case ALLERGIES_AND_ADVERSE_REACTIONS -> "CDS_EXPORT_ALLERGY";
            case MEDICATIONS_AND_TREATMENTS -> "CDS_EXPORT_MEDICATION";
            case APPOINTMENTS -> "CDS_EXPORT_APPOINTMENT";
            case IMMUNIZATIONS -> "CDS_EXPORT_IMMUNIZATION";
            default -> throw new IllegalArgumentException("CDS_EXPORT_RETAINED_OCCURRENCE_CATEGORY_UNSUPPORTED");
        };
        if (!index.sourceManifestSha256().equals(checked.sourceManifestSha256()) || !index.appliedUnits().contains(nativeAddress)
                || !index.appliedUnits().equals(checked.units().keySet())) scope(prefix);
        var metadata = index.tables().getOrDefault("cds_import_field", Map.of());
        var expected = new HashMap<String, CdsExportCapturedTextRowDto>(); var verifiedPages = new HashSet<String>();
        for (var parent : index.nativeParents().entrySet()) if (nativeAddress.equals(parent.getValue())) {
            checkpoint.run();
            // nativeParents is field ID -> native unit address. A field's own unit is
            // recorded in its row and has a different receipt from that native parent.
            String fieldId = parent.getKey(); var row = metadata.get(fieldId);
            if (row == null || !fieldId.equals(row.recordKey()) || !fieldId.equals(row.fields().get("id"))) scope(prefix);
            String page = row.fields().get("run_id") + ":" + row.fields().get("unit_key");
            if (!index.appliedUnits().contains(page) || !checked.unchangedUnits().contains(page))
                throw new IOException(prefix + "_SOURCE_FIELD_EVIDENCE_CHANGED");
            var proof = checked.units().get(page); var original = index.references().get(page);
            if (proof == null || original == null || !proof.stream().map(CdsExportRetainedReceiptVerificationDto.CheckedReference::expected).toList().equals(original)
                    || proof.stream().noneMatch(ref -> ref.expected().table().equals("cds_import_field") && ref.expected().id().equals(fieldId)
                            && ref.reason() == CdsExportRetainedReceiptVerificationDto.Reason.UNCHANGED_ROW)) scope(prefix);
            if (verifiedPages.add(page) && proof.stream().anyMatch(ref -> ref.reason() != CdsExportRetainedReceiptVerificationDto.Reason.UNCHANGED_ROW)) scope(prefix);
            if (expected.putIfAbsent(fieldId, row) != null) scope(prefix);
            if (expected.size() > 65_536) throw new IOException(prefix + "_SOURCE_FIELDS_REQUIRED");
        }
        if (expected.isEmpty()) throw new IOException(prefix + "_SOURCE_FIELDS_REQUIRED");
        var result = new ArrayList<CdsExportRetainedFieldDto>(); var seen = new HashSet<String>(); var mapper = new CdsExportRetainedFieldMapper();
        for (var field : supplied) if (expected.containsKey(field.id())) {
            checkpoint.run();
            if (!seen.add(field.id()) || field.category() != category || !field.equals(mapper.map(patient, Set.of(category), expected.get(field.id())))) scope(prefix);
            result.add(field);
        }
        if (!seen.equals(expected.keySet())) scope(prefix); checkpoint.run(); return List.copyOf(result);
    }
    private static void scope(String prefix) throws IOException { throw new IOException(prefix + "_RETAINED_ASSEMBLY_SCOPE_INVALID"); }
}
