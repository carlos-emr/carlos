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

/** Reconciled standard event fields and all untouched retained source fields in their original source occurrence. */
public final class CdsExportImmunizationRetainedAssembler {
    public CdsExportRetainedSectionWriter.Prepared prepare(
            int patient,
            CdsExportImmunizationFieldReconciler.Result decision,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> allFields,
            CdsExportRetainedSectionWriter.Values values,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(values);
        Objects.requireNonNull(checkpoint);
        values.requireActive();
        if (!decision.mapping().nativeRowRepresentable())
            throw new IOException("CDS_EXPORT_IMMUNIZATION_MAPPING_REQUIRES_REVIEW");
        if (!decision.sourceManifestSha256().equals(index.sourceManifestSha256())
                || !index.sourceManifestSha256().equals(checked.sourceManifestSha256())
                || !index.appliedUnits().contains(decision.unitAddress())) scope();
        CdsExportImmunizationXmlWriter.validate(decision.mapping().value());
        var fields =
                new CdsExportRetainedOccurrenceFields()
                        .fieldsFor(
                                patient,
                                CdsCategory.IMMUNIZATIONS,
                                decision.unitAddress(),
                                index,
                                checked,
                                allFields,
                                checkpoint);
        String root = root(fields.getFirst());
        var relative = new LinkedHashMap<String, CdsExportRetainedFieldDto>();
        for (var field : fields) {
            if (!root.equals(root(field))) scope();
            String path =
                    field.sourcePath().length() == root.length()
                            ? ""
                            : field.sourcePath().substring(root.length() + 1);
            if (relative.putIfAbsent(path, field) != null) scope();
        }
        var historical =
                new CdsExportPreparedImmunizationMapper()
                        .map(
                                new CdsExportRetainedPreparationReader()
                                        .immunization(index, decision.unitAddress())
                                        .value());
        var current = decision.mapping().value();
        var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        for (var entry : parts(current).entrySet()) {
            checkpoint.run();
            String path = entry.getKey() + "[1]";
            if (entry.getKey().equals("ImmunizationCode")) {
                // Code also permits a source-only Description. Preserve the original
                // subtree when its coded identity is unchanged; a changed code cannot
                // silently discard or borrow the old description.
                if (Objects.equals(historical.code(), current.code())
                        && Objects.equals(historical.codeSystem(), current.codeSystem())) continue;
                if (relative.containsKey(path + "/Description[1]"))
                    throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                            .CdsExportReviewException(
                            io.github.carlos_emr.carlos.demographic.cdsexport.support
                                    .CdsExportReviewException.Reason.UNMAPPED_DATA);
            }
            var prior = new HashSet<String>();
            for (var field : relative.entrySet())
                if (field.getKey().equals(path) || field.getKey().startsWith(path + "/"))
                    prior.add(field.getValue().id());
            if (!prior.isEmpty() || entry.getValue() != null)
                changes.add(
                        new CdsExportRetainedSectionWriter.Change(path, prior, entry.getValue()));
        }
        values.requireActive();
        checkpoint.run();
        return new CdsExportRetainedSectionWriter()
                .prepareChanges(fields, values, changes, checkpoint);
    }

    private static Map<String, CdsExportXmlComposer.Content> parts(CdsExportImmunizationDto value) {
        var result = new LinkedHashMap<String, CdsExportXmlComposer.Content>();
        text(result, "ImmunizationName", value.name());
        text(result, "ImmunizationType", value.type());
        text(result, "Manufacturer", value.manufacturer());
        text(result, "LotNumber", value.lot());
        text(result, "Route", value.route());
        text(result, "Site", value.site());
        text(result, "Dose", value.dose());
        result.put(
                "ImmunizationCode",
                value.code() == null
                        ? null
                        : xml -> {
                            xml.element("cds_dt", "CodingSystem", value.codeSystem());
                            xml.element("cds_dt", "value", value.code());
                        });
        result.put(
                "Date",
                value.date().precision() == CdsExportDateDto.Precision.ABSENT
                        ? null
                        : xml -> CdsExportDateXmlWriter.content(xml, value.date(), true));
        result.put(
                "RefusedFlag",
                xml -> xml.element("cds_dt", "ynIndicatorsimple", value.refused() ? "Y" : "N"));
        text(result, "Instructions", value.instructions());
        text(result, "Notes", value.notes());
        return result;
    }

    private static void text(
            Map<String, CdsExportXmlComposer.Content> parts, String name, String value) {
        parts.put(name, value == null ? null : xml -> xml.text(value));
    }

    private static String root(CdsExportRetainedFieldDto field) throws IOException {
        try {
            return CdsExportRetainedOccurrencePath.root(field, CdsCategory.IMMUNIZATIONS);
        } catch (IOException invalid) {
            scope();
            return null;
        }
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_IMMUNIZATION_RETAINED_ASSEMBLY_SCOPE_INVALID");
    }
}
