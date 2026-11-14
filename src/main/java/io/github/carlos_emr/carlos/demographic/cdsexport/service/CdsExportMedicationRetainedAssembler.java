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
import java.util.regex.Pattern;

/** Reconciled standard prescription fields plus every untouched retained extension, in one authenticated source occurrence. */
public final class CdsExportMedicationRetainedAssembler {
    private static final Pattern DATA_ELEMENT =
            Pattern.compile("ResidualInfo\\[1\\]/DataElement\\[([1-9][0-9]{0,17})\\](?:/.*)?");

    /**
     * The prepared occurrence and whether it carries the CARLOS indication's code as a new
     * ProblemCode (false when there is none, or the imported medication keeps its own).
     */
    public record Assembly(
            CdsExportRetainedSectionWriter.Prepared prepared, boolean problemCodeWritten) {
        public Assembly {
            Objects.requireNonNull(prepared);
        }

        @Override
        public String toString() {
            return "CdsExportMedicationRetainedAssembler.Assembly[protected]";
        }
    }

    public CdsExportRetainedSectionWriter.Prepared prepare(
            int patient,
            CdsExportMedicationFieldReconciler.Result decision,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> allFields,
            CdsExportRetainedSectionWriter.Values values,
            Runnable checkpoint)
            throws IOException {
        return prepare(
                        patient,
                        decision,
                        CdsExportMedicationReasonMapper.Result.NONE,
                        index,
                        checked,
                        allFields,
                        values,
                        checkpoint)
                .prepared();
    }

    /**
     * As the reasonless form, with the indications recorded in CARLOS on the imported medication.
     * The CDS importer never writes {@code drugReason}, so every one was added after the import.
     * All of them are added to the original occurrence as one more
     * {@code CARLOS.Medication.Reasons.v1} residual element beside the sender's untouched
     * residual data, and the indication's ICD-9 code becomes ProblemCode only when the source
     * occurrence has none: an original ProblemCode is never replaced or repeated.
     */
    public Assembly prepare(
            int patient,
            CdsExportMedicationFieldReconciler.Result decision,
            CdsExportMedicationReasonMapper.Result reasons,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> allFields,
            CdsExportRetainedSectionWriter.Values values,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(reasons);
        Objects.requireNonNull(values);
        Objects.requireNonNull(checkpoint);
        values.requireActive();
        if (!decision.mapping().nativeRowRepresentable())
            review("CDS_EXPORT_MEDICATION_MAPPING_REQUIRES_REVIEW");
        if (!decision.sourceManifestSha256().equals(index.sourceManifestSha256())
                || !index.sourceManifestSha256().equals(checked.sourceManifestSha256())
                || !index.appliedUnits().contains(decision.unitAddress())) scope();
        try {
            CdsExportMedicationXmlWriter.validate(decision.mapping().value());
        } catch (io.github.carlos_emr.carlos.demographic.cdsexport.support
                        .CdsExportMappingReviewException held) {
            throw held;
        } catch (IOException unwritable) {
            // A value of this one prescription the writer cannot hold concerns this patient's
            // chart: the patient is held under a named code, the job is not stopped.
            review("CDS_EXPORT_MEDICATION_VALUES_REQUIRES_REVIEW");
        }
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
        if (decision.retainedStrengthRequired()
                && (!decision.preparedPaths().contains("Strength")
                        || current.strengthAmount() != null
                        || !relative.containsKey("Strength[1]/Amount[1]")
                        || !relative.containsKey("Strength[1]/UnitOfMeasure[1]"))) scope();
        var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        for (var part : parts(current).entrySet()) {
            // Proven unchanged projections authorize reuse of the original field pages,
            // including lexical precision and values too large for native preparation.
            String path = part.getKey() + "[1]";
            var prior = ids(relative, path);
            if (!prior.isEmpty() && preservedPart(decision.preparedPaths(), part.getKey()))
                continue;
            if (!prior.isEmpty() || part.getValue() != null)
                changes.add(
                        new CdsExportRetainedSectionWriter.Change(path, prior, part.getValue()));
        }
        boolean problemCodeWritten =
                reasons.problemCode() != null && ids(relative, "ProblemCode[1]").isEmpty();
        if (problemCodeWritten) {
            String code = reasons.problemCode();
            changes.add(
                    new CdsExportRetainedSectionWriter.Change(
                            "ProblemCode[1]", Set.of(), xml -> xml.text(code)));
        }
        var additions = new ArrayList<Marker>();
        if (reasons.residual() != null)
            additions.add(
                    new Marker(
                            CdsNativeExtensionContract.MEDICATION_REASONS,
                            CdsNativeExtensionContract.STRING_TYPE,
                            reasons.residual()));
        if (decision.currentSource() != null
                || decision.archived() != null
                || decision.supersedesSenderSource())
            current(decision, relative, values, changes, additions, checkpoint);
        if (!additions.isEmpty()) residual(relative, additions, changes, checkpoint);
        values.requireActive();
        checkpoint.run();
        return new Assembly(
                new CdsExportRetainedSectionWriter()
                        .prepareChanges(fields, values, changes, checkpoint),
                problemCodeWritten);
    }

    private record Marker(String name, String type, String content) {
        @Override
        public String toString() {
            return "CdsExportMedicationRetainedAssembler.Marker[protected]";
        }
    }

    private static final Pattern MARKER_NAME =
            Pattern.compile("ResidualInfo\\[1\\]/DataElement\\[([1-9][0-9]{0,17})\\]/Name\\[1\\]");

    /**
     * The carriers of an imported prescription changed in CARLOS. Its complete current row
     * replaces the content of an earlier {@code CARLOS.Medication.CurrentSource.v1} of the same
     * occurrence (an earlier CARLOS export of the same edited chart) or is added; a sender's
     * {@code CARLOS.Medication.Archived.v1} is corrected when the archive columns changed, or
     * added when the prescription is now archived, so the occurrence never states a stale
     * archive state. Every other residual element stays untouched.
     */
    private static void current(
            CdsExportMedicationFieldReconciler.Result decision,
            Map<String, CdsExportRetainedFieldDto> relative,
            CdsExportRetainedSectionWriter.Values values,
            List<CdsExportRetainedSectionWriter.Change> changes,
            List<Marker> additions,
            Runnable checkpoint)
            throws IOException {
        var existing = new HashMap<String, String>();
        for (var entry : relative.entrySet()) {
            checkpoint.run();
            if (!MARKER_NAME.matcher(entry.getKey()).matches()) continue;
            String name =
                    CdsExportAllergyRetainedAssembler.token(
                            values, entry.getValue(), checkpoint, 128);
            if (!CdsNativeExtensionContract.MEDICATION_ARCHIVED.equals(name)
                    && !CdsNativeExtensionContract.MEDICATION_CURRENT_SOURCE.equals(name)
                    && !CdsNativeExtensionContract.MEDICATION_SOURCE_CONTEXT.equals(name)) continue;
            String path = entry.getKey().substring(0, entry.getKey().lastIndexOf('/'));
            if (existing.putIfAbsent(name, path) != null
                    || !relative.containsKey(path + "/DataType[1]")
                    || !relative.containsKey(path + "/Content[1]"))
                review("CDS_EXPORT_MEDICATION_EXTENSION_REQUIRES_REVIEW");
        }
        if (decision.supersedesSenderSource()) {
            // The sender's own complete-row carrier would restate a quantity unit or as-needed
            // flag that CARLOS has since changed, and a CARLOS importer would restore it: the
            // element is removed (its fields are listed as replaced) and the current row
            // travels in CARLOS.Medication.CurrentSource.v1.
            String path = existing.get(CdsNativeExtensionContract.MEDICATION_SOURCE_CONTEXT);
            if (path != null)
                changes.add(
                        new CdsExportRetainedSectionWriter.Change(path, ids(relative, path), null));
        }
        if (decision.archived() != null) {
            String state = Boolean.toString(decision.archived());
            String path = existing.get(CdsNativeExtensionContract.MEDICATION_ARCHIVED);
            if (path != null) {
                var content = relative.get(path + "/Content[1]");
                String prior =
                        CdsExportAllergyRetainedAssembler.token(values, content, checkpoint, 128);
                prior =
                        switch (prior == null ? "" : prior) {
                            case "true", "1" -> "true";
                            case "false", "0" -> "false";
                            default -> null;
                        };
                if (!state.equals(prior))
                    changes.add(
                            new CdsExportRetainedSectionWriter.Change(
                                    path + "/Content[1]",
                                    Set.of(content.id()),
                                    xml -> xml.text(state)));
            } else if (decision.archived())
                additions.add(
                        new Marker(
                                CdsNativeExtensionContract.MEDICATION_ARCHIVED,
                                CdsNativeExtensionContract.BOOLEAN_TYPE,
                                state));
        }
        if (decision.currentSource() != null) {
            String source = decision.currentSource();
            String path = existing.get(CdsNativeExtensionContract.MEDICATION_CURRENT_SOURCE);
            if (path != null) {
                var content = relative.get(path + "/Content[1]");
                changes.add(
                        new CdsExportRetainedSectionWriter.Change(
                                path + "/Content[1]",
                                Set.of(content.id()),
                                xml -> xml.text(source)));
            } else
                additions.add(
                        new Marker(
                                CdsNativeExtensionContract.MEDICATION_CURRENT_SOURCE,
                                CdsNativeExtensionContract.STRING_TYPE,
                                source));
        }
    }

    /**
     * Adds carriers after the occurrence's own residual elements, or as a new ResidualInfo when
     * it has none (an empty source ResidualInfo is replaced, as the allergy assembler does).
     * Existing elements, an earlier CARLOS export's reasons included, stay untouched.
     */
    private static void residual(
            Map<String, CdsExportRetainedFieldDto> relative,
            List<Marker> additions,
            List<CdsExportRetainedSectionWriter.Change> changes,
            Runnable checkpoint) {
        boolean present = false;
        long maximum = 0;
        for (String path : relative.keySet()) {
            checkpoint.run();
            if (!path.equals("ResidualInfo[1]") && !path.startsWith("ResidualInfo[1]/")) continue;
            present = true;
            var match = DATA_ELEMENT.matcher(path);
            if (match.matches()) maximum = Math.max(maximum, Long.parseLong(match.group(1)));
        }
        if (!present || relative.containsKey("ResidualInfo[1]"))
            changes.add(
                    new CdsExportRetainedSectionWriter.Change(
                            "ResidualInfo[1]",
                            ids(relative, "ResidualInfo[1]"),
                            xml -> {
                                for (var marker : additions) {
                                    xml.start("cds_dt", "DataElement");
                                    marker(xml, marker);
                                    xml.end();
                                }
                            }));
        else
            for (var marker : additions)
                changes.add(
                        new CdsExportRetainedSectionWriter.Change(
                                "ResidualInfo[1]/DataElement[" + (++maximum) + "]",
                                Set.of(),
                                xml -> marker(xml, marker)));
    }

    private static void marker(CdsExportXmlComposer.ContentWriter xml, Marker marker)
            throws IOException {
        xml.element("cds_dt", "Name", marker.name());
        xml.element("cds_dt", "DataType", marker.type());
        xml.element("cds_dt", "Content", marker.content());
    }

    private static boolean preservedPart(Set<String> prepared, String part) {
        if (prepared.contains(part)) return true;
        return switch (part) {
            case "Frequency", "TreatmentType" -> prepared.contains("Frequency/TreatmentType");
            case "PrescriptionStatus", "PastMedications" ->
                    prepared.contains("PrescriptionStatus/PastMedications");
            default -> false;
        };
    }

    private static Map<String, CdsExportXmlComposer.Content> parts(CdsExportMedicationDto value) {
        var result = new LinkedHashMap<String, CdsExportXmlComposer.Content>();
        date(result, "PrescriptionWrittenDate", value.written(), true);
        date(result, "StartDate", value.start(), false);
        for (var field : CdsExportMedicationDto.Field.values()) {
            // ProblemCode is standard source-only content not represented by the historical
            // medication preparation (the importer writes no drugReason), and the reconciled
            // value never carries one; preserve the original field rather than deleting it. A
            // reason recorded in CARLOS later is added by prepare(), never over the original.
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

    /** A patient-specific data condition: the patient is held for review, not an I/O failure. */
    private static void review(String code) throws IOException {
        throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                .CdsExportMappingReviewException(code);
    }
}
