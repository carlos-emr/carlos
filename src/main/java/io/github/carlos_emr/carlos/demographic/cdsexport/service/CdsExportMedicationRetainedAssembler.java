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
import java.io.IOException;
import java.util.*;

/** Reconciled standard prescription fields plus every untouched retained extension, in one authenticated source occurrence. */
public final class CdsExportMedicationRetainedAssembler {
    public CdsExportRetainedSectionWriter.Prepared prepare(
            int patient,
            CdsExportMedicationFieldReconciler.Result decision,
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
            review("CDS_EXPORT_MEDICATION_MAPPING_REQUIRES_REVIEW");
        if (!decision.sourceManifestSha256().equals(index.sourceManifestSha256())
                || !index.sourceManifestSha256().equals(checked.sourceManifestSha256())
                || !index.appliedUnits().contains(decision.unitAddress())) scope();
        CdsExportMedicationXmlWriter.validate(decision.mapping().value());
        var fields =
                new CdsExportRetainedOccurrenceFields()
                        .fieldsFor(
                                patient,
                                CdsCategory.MEDICATIONS_AND_TREATMENTS,
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
        var current = decision.mapping().value();
        var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        for (var part : parts(current).entrySet()) {
            String path = part.getKey() + "[1]";
            var prior = ids(relative, path);
            if (!prior.isEmpty() || part.getValue() != null)
                changes.add(
                        new CdsExportRetainedSectionWriter.Change(path, prior, part.getValue()));
        }
        values.requireActive();
        checkpoint.run();
        return new CdsExportRetainedSectionWriter()
                .prepareChanges(fields, values, changes, checkpoint);
    }

    private static Map<String, CdsExportXmlComposer.Content> parts(CdsExportMedicationDto value) {
        var result = new LinkedHashMap<String, CdsExportXmlComposer.Content>();
        date(result, "PrescriptionWrittenDate", value.written(), true);
        date(result, "StartDate", value.start(), false);
        for (var field : CdsExportMedicationDto.Field.values()) {
            // ProblemCode is standard source-only content not represented by the historical
            // medication preparation; preserve its original field rather than deleting it.
            if (field == CdsExportMedicationDto.Field.ProblemCode) continue;
            String text = value.fields().get(field);
            result.put(
                    field.name(),
                    text == null
                            ? null
                            : xml -> {
                                if (field.indicator())
                                    xml.element("cds_dt", "ynIndicatorsimple", text);
                                else xml.text(text);
                            });
        }
        result.put(
                "Strength",
                value.strengthAmount() == null
                        ? null
                        : xml -> {
                            xml.element("cds_dt", "Amount", value.strengthAmount());
                            xml.element("cds_dt", "UnitOfMeasure", value.strengthUnit());
                        });
        result.put(
                "PrescribedBy",
                value.prescriber() == null
                        ? null
                        : xml -> {
                            var provider = value.prescriber();
                            xml.start("cds", "Name");
                            xml.element("cds_dt", "FirstName", provider.firstName());
                            xml.element("cds_dt", "LastName", provider.lastName());
                            xml.end();
                            if (provider.ohipNumber() != null)
                                xml.element("cds", "OHIPPhysicianId", provider.ohipNumber());
                        });
        return result;
    }

    private static void date(
            Map<String, CdsExportXmlComposer.Content> result,
            String name,
            CdsExportDateDto value,
            boolean withClock) {
        result.put(
                name,
                value.precision() == CdsExportDateDto.Precision.ABSENT
                        ? null
                        : xml -> {
                            String child =
                                    switch (value.precision()) {
                                        case YEAR -> "YearOnly";
                                        case YEAR_MONTH -> "YearMonth";
                                        case DATE -> "FullDate";
                                        case DATE_TIME -> {
                                            if (!withClock)
                                                throw new IOException(
                                                        "CDS_EXPORT_DATE_CLOCK_NOT_ALLOWED");
                                            yield "FullDateTime";
                                        }
                                        default ->
                                                throw new IOException(
                                                        "CDS_EXPORT_NATIVE_DATE_INVALID");
                                    };
                            xml.element("cds_dt", child, value.lexical());
                        });
    }

    private static Set<String> ids(Map<String, CdsExportRetainedFieldDto> fields, String root) {
        var ids = new HashSet<String>();
        fields.forEach(
                (path, field) -> {
                    if (path.equals(root) || path.startsWith(root + "/")) ids.add(field.id());
                });
        return ids;
    }

    private static String root(CdsExportRetainedFieldDto field) throws IOException {
        try {
            return CdsExportRetainedOccurrencePath.root(
                    field, CdsCategory.MEDICATIONS_AND_TREATMENTS);
        } catch (IOException invalid) {
            scope();
            return null;
        }
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_MEDICATION_RETAINED_ASSEMBLY_SCOPE_INVALID");
    }

    private static void review(String code) throws IOException {
        throw new IOException(code);
    }
}
