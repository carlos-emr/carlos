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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import java.io.IOException;
import java.util.*;

/** Preserves unchanged source address lines and type while reconciling explicit native address edits. */
final class CdsExportRetainedPharmacyAddressReconciler {
    private static final List<String> COLUMNS = List.of("address", "city", "province", "postalCode");
    private static final String STRUCTURED = "Address[1]/Structured[1]/";

    /** Null is an explicit review outcome; no partial address changes escape on that path. */
    List<CdsExportRetainedSectionWriter.Change> reconcile(CdsExportRetainedPharmacyBinding.Result binding,
            CdsExportCapturedTextRowDto row, CdsExportPharmacyMapper.Result mapping,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> decisions) throws IOException {
        var prepared = CdsExportRetainedPharmacyBinding.prepared(binding.preparation()); var changed = new HashSet<String>();
        for (String column : COLUMNS) if (!Objects.equals(prepared.get(column), row.fields().get(column))) changed.add(column);
        if (!changed.isEmpty() && mapping.preferred() == null) return null;
        boolean jurisdictionChanged = changed.contains("province") || changed.contains("postalCode");
        for (String column : COLUMNS) {
            var key = new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column); var nativeDecision = mapping.fields().get(key);
            if (changed.contains(column) && prepared.get(column) == null && !binding.idsForColumn(column).isEmpty()) return null;
            if (changed.contains(column) || jurisdictionChanged && Set.of("province", "postalCode").contains(column)) {
                if (nativeDecision.outcome() == Outcome.REQUIRES_REVIEW) return null;
            } else {
                decisions.put(key, new CdsExportFieldAssessmentDto(nativeDecision.presence(), Outcome.MAPPED, "PreferredPharmacy/Address", "VERIFIED_UNCHANGED_RETAINED_PHARMACY_GROUP"));
            }
        }
        if (changed.isEmpty()) return List.of();
        var address = mapping.preferred().address(); var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        boolean structuredChildren = binding.fieldIds().keySet().stream().anyMatch(path -> path.startsWith(STRUCTURED));
        if (!structuredChildren) {
            // Rebuilding a formatted/absent address needs every native component to be representable.
            for (String column : COLUMNS) if (mapping.fields().get(new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column)).outcome() == Outcome.REQUIRES_REVIEW) return null;
            var ids = binding.idsUnder("Address[1]"); String type = ids.isEmpty() ? "M" : binding.sourceValues().get("Address[1]/@addressType");
            if (type == null || !Set.of("M", "R").contains(type)) return null;
            if (address != null) changes.add(new CdsExportRetainedSectionWriter.Change("PreferredPharmacy[1]/Address[1]", ids, xml -> CdsExportPharmacyXmlWriter.writeAddress(address, type, xml)));
            else if (!ids.isEmpty()) changes.add(new CdsExportRetainedSectionWriter.Change("PreferredPharmacy[1]/Address[1]", ids, null));
            return changes;
        }
        if (changed.contains("address")) {
            scalar(changes, binding, STRUCTURED + "Line1[1]", address == null ? null : address.line1());
            scalar(changes, binding, STRUCTURED + "Line2[1]", null); scalar(changes, binding, STRUCTURED + "Line3[1]", null);
        }
        if (changed.contains("city")) scalar(changes, binding, STRUCTURED + "City[1]", address == null ? null : address.city());
        if (jurisdictionChanged) {
            if (changed.contains("province")) scalar(changes, binding, STRUCTURED + "CountrySubdivisionCode[1]", address == null ? null : address.subdivision());
            var ids = binding.idsUnder(STRUCTURED + "PostalZipCode[1]");
            if (address != null && address.postalCode() != null) changes.add(new CdsExportRetainedSectionWriter.Change("PreferredPharmacy[1]/" + STRUCTURED + "PostalZipCode[1]", ids,
                    xml -> xml.element("cds_dt", address.subdivision() != null && address.subdivision().startsWith("US-") ? "ZipCode" : "PostalCode", address.postalCode())));
            else if (!ids.isEmpty()) changes.add(new CdsExportRetainedSectionWriter.Change("PreferredPharmacy[1]/" + STRUCTURED + "PostalZipCode[1]", ids, null));
        }
        return changes;
    }
    private static void scalar(List<CdsExportRetainedSectionWriter.Change> changes, CdsExportRetainedPharmacyBinding.Result binding, String path, String value) {
        var ids = binding.idsUnder(path); if (value == null && ids.isEmpty()) return;
        changes.add(new CdsExportRetainedSectionWriter.Change("PreferredPharmacy[1]/" + path, ids, value == null ? null : xml -> xml.text(value)));
    }
}
