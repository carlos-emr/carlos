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
import java.io.Reader;
import java.util.*;
import java.util.regex.Pattern;

/** Reconciled standard allergy fields plus every untouched retained extension, in one authenticated source occurrence. */
public final class CdsExportAllergyRetainedAssembler {
    private static final Pattern MARKER =
            Pattern.compile("ResidualInfo\\[1\\]/DataElement\\[([1-9][0-9]{0,17})\\]/Name\\[1\\]");
    private static final Set<String> KNOWN =
            Set.of(
                    CdsNativeExtensionContract.ALLERGY_ARCHIVED,
                    CdsNativeExtensionContract.ALLERGY_AGE_OF_ONSET,
                    CdsNativeExtensionContract.ALLERGY_ONSET_OF_REACTION,
                    CdsNativeExtensionContract.ALLERGY_ATC,
                    CdsNativeExtensionContract.ALLERGY_SEVERITY,
                    CdsNativeExtensionContract.ALLERGY_REACTION);

    /** Encoded reaction carrier bound: six characters per encoded character of text32K. */
    private static final int REACTION_CARRIER_MAXIMUM = 6 * 32768 + 2;

    public CdsExportRetainedSectionWriter.Prepared prepare(
            int patient,
            CdsExportAllergyFieldReconciler.Result decision,
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
            review("CDS_EXPORT_ALLERGY_MAPPING_REQUIRES_REVIEW");
        if (!decision.sourceManifestSha256().equals(index.sourceManifestSha256())
                || !index.sourceManifestSha256().equals(checked.sourceManifestSha256())
                || !index.appliedUnits().contains(decision.unitAddress())) scope();
        CdsExportAllergyXmlWriter.validate(decision.mapping().value());
        var fields =
                new CdsExportRetainedOccurrenceFields()
                        .fieldsFor(
                                patient,
                                CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
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
        var preparedSource =
                new CdsExportRetainedPreparationReader()
                        .allergy(index, decision.unitAddress())
                        .value();
        var historical = new CdsExportPreparedAllergyMapper().map(preparedSource);
        var current = decision.mapping().value();
        // F9: a sender's standard Code with CodeType ATC (not CARLOS's ATC residual) stays the
        // standard Code subtree while the coded agent is unchanged; only a CARLOS-authored ATC
        // travels in CARLOS.Allergy.Atc.v1.
        boolean standardAtc =
                !preparedSource.atcResidual()
                        && "ATC".equals(preparedSource.codeSystem())
                        && "ATC".equals(current.codeType())
                        && Objects.equals(current.codeValue(), preparedSource.code());
        var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        for (var part : parts(current, standardAtc).entrySet()) {
            String path = part.getKey() + "[1]";
            var prior = ids(relative, path);
            if (!prior.isEmpty() || part.getValue() != null)
                changes.add(
                        new CdsExportRetainedSectionWriter.Change(path, prior, part.getValue()));
        }
        var existing = new HashMap<String, String>();
        long maximum = 0;
        for (var entry : relative.entrySet()) {
            checkpoint.run();
            var match = MARKER.matcher(entry.getKey());
            if (!match.matches()) continue;
            maximum = Math.max(maximum, Long.parseLong(match.group(1)));
            String name = token(values, entry.getValue(), checkpoint, 128);
            if (name == null || !KNOWN.contains(name)) continue;
            String path = entry.getKey().substring(0, entry.getKey().lastIndexOf('/'));
            if (existing.putIfAbsent(name, path) != null)
                review("CDS_EXPORT_ALLERGY_EXTENSION_DUPLICATE");
            var type = relative.get(path + "/DataType[1]");
            var content = relative.get(path + "/Content[1]");
            if (type == null
                    || content == null
                    || !markerType(name).equals(token(values, type, checkpoint, 128)))
                review("CDS_EXPORT_ALLERGY_EXTENSION_TYPE_REQUIRES_REVIEW");
            String prior =
                    token(
                            values,
                            content,
                            checkpoint,
                            name.equals(CdsNativeExtensionContract.ALLERGY_REACTION)
                                    ? REACTION_CARRIER_MAXIMUM
                                    : 128);
            if (name.equals(CdsNativeExtensionContract.ALLERGY_ARCHIVED)) {
                prior =
                        switch (prior == null ? "" : prior) {
                            case "true", "1" -> "true";
                            case "false", "0" -> "false";
                            default -> null;
                        };
            }
            if (prior == null || !Objects.equals(prior, sourceMarker(preparedSource, historical, name, standardAtc)))
                review("CDS_EXPORT_ALLERGY_EXTENSION_PROJECTION_CONFLICT");
        }
        var additions = new LinkedHashMap<String, String>();
        for (String name : KNOWN.stream().sorted().toList()) {
            String value = markerValue(current, name, standardAtc),
                    old = sourceMarker(preparedSource, historical, name, standardAtc),
                    path = existing.get(name);
            if (path != null) {
                if (value == null)
                    changes.add(
                            new CdsExportRetainedSectionWriter.Change(
                                    path, ids(relative, path), null));
                else if (!value.equals(old)) {
                    var content = relative.get(path + "/Content[1]");
                    changes.add(
                            new CdsExportRetainedSectionWriter.Change(
                                    path + "/Content[1]",
                                    Set.of(content.id()),
                                    xml -> xml.text(value)));
                }
            } else if (value != null) additions.put(name, value);
        }
        if (!additions.isEmpty()) {
            boolean residual =
                    relative.keySet().stream()
                            .anyMatch(
                                    path ->
                                            path.equals("ResidualInfo[1]")
                                                    || path.startsWith("ResidualInfo[1]/"));
            if (!residual || relative.containsKey("ResidualInfo[1]")) {
                changes.add(
                        new CdsExportRetainedSectionWriter.Change(
                                "ResidualInfo[1]",
                                ids(relative, "ResidualInfo[1]"),
                                xml -> {
                                    for (var marker : additions.entrySet()) {
                                        xml.start("cds_dt", "DataElement");
                                        marker(xml, marker.getKey(), marker.getValue());
                                        xml.end();
                                    }
                                }));
            } else
                for (var marker : additions.entrySet()) {
                    String path = "ResidualInfo[1]/DataElement[" + (++maximum) + "]";
                    changes.add(
                            new CdsExportRetainedSectionWriter.Change(
                                    path,
                                    Set.of(),
                                    xml -> marker(xml, marker.getKey(), marker.getValue())));
                }
        }
        values.requireActive();
        checkpoint.run();
        return new CdsExportRetainedSectionWriter()
                .prepareChanges(fields, values, changes, checkpoint);
    }

    private static Map<String, CdsExportXmlComposer.Content> parts(
            CdsExportAllergyDto value, boolean standardAtc) {
        var result = new LinkedHashMap<String, CdsExportXmlComposer.Content>();
        text(result, "OffendingAgentDescription", value.description());
        text(result, "PropertyOfOffendingAgent", value.property());
        result.put(
                "Code",
                value.codeValue() == null || "ATC".equals(value.codeType()) && !standardAtc
                        ? null
                        : xml -> {
                            xml.element("cds_dt", "CodeType", value.codeType());
                            xml.element("cds_dt", "CodeValue", value.codeValue());
                        });
        text(result, "ReactionType", value.reactionType());
        date(result, "StartDate", value.start());
        text(result, "LifeStage", value.lifeStage());
        text(result, "Severity", value.severity());
        text(result, "Reaction", value.reaction());
        date(result, "RecordedDate", value.recorded());
        text(result, "Notes", value.notes());
        return result;
    }

    private static void text(
            Map<String, CdsExportXmlComposer.Content> parts, String name, String value) {
        parts.put(name, value == null ? null : xml -> xml.text(value));
    }

    private static void date(
            Map<String, CdsExportXmlComposer.Content> parts, String name, CdsExportDateDto value) {
        parts.put(
                name,
                value.precision() == CdsExportDateDto.Precision.ABSENT
                        ? null
                        : xml ->
                                CdsExportAllergyXmlWriter.dateContent(
                                        xml, value, name.equals("RecordedDate")));
    }

    private static void marker(CdsExportXmlComposer.ContentWriter xml, String name, String value)
            throws IOException {
        CdsExportAllergyXmlWriter.residualFields(xml, name, markerType(name), value);
    }

    private static String markerType(String name) {
        return name.equals(CdsNativeExtensionContract.ALLERGY_ARCHIVED)
                ? CdsNativeExtensionContract.BOOLEAN_TYPE
                : CdsNativeExtensionContract.STRING_TYPE;
    }

    /**
     * The marker value the source itself carried. The archive marker is the source's assertion,
     * which differs from the prepared native state when the import kept the allergy active
     * (decision 9); every other marker is the prepared value.
     */
    private static String sourceMarker(
            io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsAllergyDto source,
            CdsExportAllergyDto historical,
            String name,
            boolean standardAtc) {
        if (name.equals(CdsNativeExtensionContract.ALLERGY_ARCHIVED))
            return Boolean.toString(source.archived());
        return markerValue(historical, name, standardAtc);
    }

    private static String markerValue(CdsExportAllergyDto value, String name, boolean standardAtc) {
        if (name.equals(CdsNativeExtensionContract.ALLERGY_ARCHIVED))
            return Boolean.toString(value.archived());
        if (name.equals(CdsNativeExtensionContract.ALLERGY_AGE_OF_ONSET)) return value.ageOfOnset();
        if (name.equals(CdsNativeExtensionContract.ALLERGY_REACTION))
            return value.longReaction() == null
                    ? null
                    : CdsNativeExtensionContract.encodeNarrative(value.longReaction());
        if (name.equals(CdsNativeExtensionContract.ALLERGY_ATC))
            return "ATC".equals(value.codeType()) && !standardAtc ? value.codeValue() : null;
        // A native "Severe" has no CDS Severity value; it travels only in its CARLOS carrier,
        // which is replaced or removed when the native severity changes.
        if (name.equals(CdsNativeExtensionContract.ALLERGY_SEVERITY))
            return value.nativeSeverity() == null
                    ? null
                    : CdsNativeExtensionContract.ALLERGY_SEVERITY_VALUES.get(value.nativeSeverity());
        return value.onsetOfReaction() == null
                ? null
                : CdsNativeExtensionContract.ALLERGY_ONSET_VALUES.get(value.onsetOfReaction());
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
                    field, CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS);
        } catch (IOException invalid) {
            scope();
            return null;
        }
    }

    /** Bounded comparison only; oversized unknown names remain intact in the retained subtree. */
    private static String token(
            CdsExportRetainedSectionWriter.Values values,
            CdsExportRetainedFieldDto field,
            Runnable checkpoint,
            int maximum)
            throws IOException {
        var result = new StringBuilder();
        boolean pending = false, overflow = false;
        char[] block = new char[4096];
        try (Reader input = values.text(field)) {
            int count;
            while ((count = input.read(block)) != -1) {
                checkpoint.run();
                if (count == 0) throw new IOException("CDS_EXPORT_RETAINED_SOURCE_STALLED");
                for (int i = 0; i < count; i++) {
                    char character = block[i];
                    if (character == ' '
                            || character == '\t'
                            || character == '\r'
                            || character == '\n') {
                        if (!result.isEmpty()) pending = true;
                        continue;
                    }
                    if (overflow) continue;
                    if (pending) {
                        result.append(' ');
                        pending = false;
                    }
                    result.append(character);
                    if (result.length() > maximum) overflow = true;
                }
            }
        } finally {
            Arrays.fill(block, '\0');
        }
        return overflow ? null : result.toString();
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_ALLERGY_RETAINED_ASSEMBLY_SCOPE_INVALID");
    }

    /** A patient-specific data condition: the patient is held for review, not an I/O failure. */
    private static void review(String code) throws IOException {
        throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                .CdsExportMappingReviewException(code);
    }
}
