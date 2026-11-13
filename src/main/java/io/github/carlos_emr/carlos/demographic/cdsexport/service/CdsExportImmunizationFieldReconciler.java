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
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImmunizationDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.Reason;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.util.*;

/**
 * Reconciles each standard vaccine field with its original sealed preparation and exact
 * parent/child witnesses. Another field's edit never justifies replaying the whole source.
 * Deletions, aliases, renamed keys, changed precision and missing historical zone evidence
 * remain review cases. Unknown native/source fields still need their independent carriers.
 */
public final class CdsExportImmunizationFieldReconciler {
    private final java.time.ZoneId zone;

    public CdsExportImmunizationFieldReconciler() {
        this(null);
    }

    public CdsExportImmunizationFieldReconciler(java.time.ZoneId zone) {
        this.zone = zone;
    }

    public record Result(
            String sourceManifestSha256,
            String unitAddress,
            CdsExportImmunizationMappingDto mapping,
            Set<String> preparedPaths,
            Set<String> currentPaths) {
        public Result {
            preparedPaths = Set.copyOf(preparedPaths);
            currentPaths = Set.copyOf(currentPaths);
        }

        @Override
        public String toString() {
            return "CdsExportImmunizationFieldReconciler.Result[protected]";
        }
    }

    private enum Part {
        NAME("ImmunizationName", "cdsName"),
        TYPE("ImmunizationType", "cdsType"),
        MANUFACTURER("Manufacturer", "manufacture"),
        LOT("LotNumber", "lot"),
        ROUTE("Route", "route"),
        SITE("Site", "location"),
        DOSE("Dose", "dose"),
        CODE("ImmunizationCode", "cdsCode", "cdsCodeSystem"),
        DATE("Date", "cdsDateOriginal", "cdsDatePrecision"),
        REFUSED("RefusedFlag"),
        INSTRUCTIONS("Instructions", "instructions"),
        NOTES("Notes", "comments");
        final String path;
        final Set<String> keys;

        Part(String path, String... keys) {
            this.path = path;
            this.keys = Set.of(keys);
        }
    }

    private record Row(
            CdsExportCapturedTextRowDto current,
            CdsNativeRowState baseline,
            CdsNativeRowComparison difference) {}

    public Result reconcile(
            int patient,
            CdsExportRetainedIndexDto index,
            String address,
            CdsExportRetainedReceiptVerificationDto checked,
            CdsExportCapturedTextRowDto parent,
            List<CdsExportCapturedTextRowDto> extensions,
            List<CdsExportCapturedTextRowDto> precision,
            List<CdsExportNativeRowReceiptDto> witnesses,
            CdsExportPreventionClassifier definitions)
            throws IOException {
        if (definitions == null
                || !definitions.sourceManifestSha256().equals(index.sourceManifestSha256()))
            scope();
        var prepared =
                new CdsExportRetainedPreparationReader().immunization(index, address).value();
        if (!index.sourceManifestSha256().equals(checked.sourceManifestSha256())
                || !index.appliedUnits().equals(checked.units().keySet())) scope();
        var references = checked.units().get(address);
        if (references == null
                || !references.stream()
                        .map(CdsExportRetainedReceiptVerificationDto.CheckedReference::expected)
                        .toList()
                        .equals(index.references().get(address))) scope();
        var parentRefs =
                references.stream()
                        .filter(ref -> ref.expected().table().equals("preventions"))
                        .toList();
        if (parentRefs.size() != 1
                || references.stream()
                        .anyMatch(
                                ref ->
                                        !Set.of("preventions", "preventionsExt", "partial_date")
                                                .contains(ref.expected().table()))) scope();
        if (parent == null) review("CDS_EXPORT_IMMUNIZATION_DELETION_REQUIRES_REVIEW");
        String nativeId = parent.fields().get("id");
        if (!parentRefs.getFirst().expected().id().equals(nativeId)) scope();
        for (var entry : index.references().entrySet())
            if (!entry.getKey().equals(address)
                    && entry.getValue().stream()
                            .anyMatch(
                                    ref ->
                                            ref.table().equals("preventions")
                                                    && ref.id().equals(nativeId)))
                review("CDS_EXPORT_IMMUNIZATION_ALIAS_REQUIRES_REVIEW");
        var capturedRows = captureRows(index, address, parent, extensions, precision, witnesses);
        var current = capturedRows.current();
        var actual = capturedRows.actual();
        var baselines = capturedRows.baselines();
        var prior =
                verifyReferenceBaselines(patient, nativeId, references, current, actual, baselines);
        var candidate =
                new CdsExportImmunizationMapper(zone)
                        .candidate(patient, parent, extensions, precision, definitions);
        var fields = new LinkedHashMap<>(candidate.fields());
        // A reconciled imported occurrence is written from its retained original elements and
        // carries no CARLOS native residual, so a value the native mapper could only carry there
        // (a provider, reminder or status added in CARLOS after the import) needs review here.
        for (var entry : fields.entrySet())
            if (entry.getValue().outcome() == Outcome.MAPPED
                    && entry.getValue()
                            .destination()
                            .contains(CdsExportImmunizationMapper.RESIDUAL))
                entry.setValue(
                        new CdsExportFieldAssessmentDto(
                                entry.getValue().presence(),
                                Outcome.REQUIRES_REVIEW,
                                entry.getValue().destination(),
                                "IMPORTED_OCCURRENCE_HAS_NO_NATIVE_RESIDUAL"));
        var old = new CdsExportPreparedImmunizationMapper().map(prepared);
        var expected = CdsExportImmunizationProjectionEvidence.expectedExtensions(prepared);
        var originalByKey = originalExtensions(prior, expected, fields);
        var root = prior.get(key("preventions", nativeId));
        if (root == null) scope();
        var selected = EnumSet.noneOf(Part.class);
        var preparedPaths = new HashSet<String>();
        var currentPaths = new HashSet<String>();
        for (var part : Part.values()) {
            boolean proved = true, unchanged = true;
            for (String name : part.keys) {
                var previous = originalByKey.get(name);
                String value = expected.get(name);
                if (previous == null) {
                    proved &= value == null;
                    unchanged &=
                            extensions.stream()
                                    .noneMatch(row -> name.equals(row.fields().get("keyval")));
                } else {
                    proved &=
                            value != null
                                    && CdsExportImmunizationProjectionEvidence.matches(
                                            previous.baseline(), "val", value);
                    boolean sameKey = !previous.difference().changedColumns().contains("keyval");
                    if (!sameKey)
                        mark(
                                previous.current(),
                                fields,
                                Set.of("keyval", "val"),
                                part.path,
                                "RENAMED_IMPORTED_EXTENSION_REQUIRES_REVIEW");
                    unchanged &=
                            sameKey
                                    && Collections.disjoint(
                                            previous.difference().changedColumns(),
                                            Set.of("prevention_id", "val"));
                }
            }
            if (part == Part.CODE
                    && extensions.stream()
                            .anyMatch(row -> "din".equals(row.fields().get("keyval"))))
                unchanged = false;
            if (part == Part.NAME
                    && extensions.stream()
                            .anyMatch(row -> "name".equals(row.fields().get("keyval"))))
                unchanged = false;
            if (part == Part.TYPE) {
                String nativeType = parent.fields().get("prevention_type");
                var declared =
                        definitions
                                .definition(nativeType)
                                .map(CdsExportPreventionClassifier.Definition::healthCanadaType)
                                .orElse(null);
                // The importer writes the catch-all type for any incoming ImmunizationType that
                // its configuration did not map, absent included (review F10). The sealed baseline
                // then witnesses that projection itself: today's declaration for the catch-all
                // says nothing about the original element, which is exported unchanged. A native
                // type change still fails the baseline match below and holds the patient.
                boolean catchAll =
                        CdsExportImmunizationMapper.CATCH_ALL_TYPE.equals(nativeType);
                proved &=
                        (catchAll || Objects.equals(declared, prepared.type()))
                                && CdsExportImmunizationProjectionEvidence.matches(
                                        root.baseline(), "prevention_type", nativeType);
                unchanged &= !root.difference().changedColumns().contains("prevention_type");
            }
            if (part == Part.REFUSED) {
                proved &=
                        CdsExportImmunizationProjectionEvidence.matches(
                                root.baseline(), "refused", prepared.refused() ? "1" : "0");
                unchanged &= !root.difference().changedColumns().contains("refused");
            }
            if (part == Part.DATE) {
                var date = CdsExportImmunizationDateProjection.expected(prepared.date());
                proved &=
                        date.kind()
                                        != CdsExportImmunizationDateProjection.Kind
                                                .HISTORIC_ZONE_REQUIRED
                                && CdsExportImmunizationProjectionEvidence.matches(
                                        root.baseline(), "prevention_date", date.nativeAnchor());
                var oldDates =
                        prior.entrySet().stream()
                                .filter(entry -> entry.getKey().startsWith("partial_date/"))
                                .map(Map.Entry::getValue)
                                .toList();
                proved &= oldDates.size() == (date.partialFormat() == null ? 0 : 1);
                for (var previous : oldDates)
                    proved &=
                            CdsExportImmunizationProjectionEvidence.matches(
                                            previous.baseline(), "field_name", "7")
                                    && CdsExportImmunizationProjectionEvidence.matches(
                                            previous.baseline(), "format", date.partialFormat());
                boolean samePrecision =
                        oldDates.size() == precision.size()
                                && oldDates.stream()
                                        .allMatch(
                                                previous ->
                                                        previous.difference()
                                                                .unchangedProjection());
                if (!samePrecision) {
                    proved = false;
                    for (var row : precision)
                        mark(
                                row,
                                fields,
                                Set.of("field_name", "format"),
                                part.path,
                                "CHANGED_PRECISION_DEPENDENCY_REQUIRES_REVIEW");
                }
                boolean dateChanged =
                        root.difference().changedColumns().contains("prevention_date");
                if (!dateChanged && !unchanged) proved = false;
                unchanged &= !dateChanged;
                if (!unchanged && hasUnprojectedTime(old.date())) proved = false;
            }
            if (!proved) {
                markPart(
                        part,
                        parent,
                        originalByKey,
                        fields,
                        "PREPARED_NATIVE_PROJECTION_NOT_PROVEN");
                continue;
            }
            if (unchanged) {
                selected.add(part);
                preparedPaths.add(part.path);
                markPart(
                        part,
                        parent,
                        originalByKey,
                        fields,
                        "VERIFIED_UNCHANGED_PREPARED_FIELD_PROJECTION");
            } else currentPaths.add(part.path);
        }
        restored(prepared, parent, root, originalByKey, fields);
        var now = candidate.value();
        var value =
                new CdsExportImmunizationDto(
                        selected.contains(Part.NAME) ? old.name() : now.name(),
                        selected.contains(Part.TYPE) ? old.type() : now.type(),
                        selected.contains(Part.MANUFACTURER)
                                ? old.manufacturer()
                                : now.manufacturer(),
                        selected.contains(Part.LOT) ? old.lot() : now.lot(),
                        selected.contains(Part.ROUTE) ? old.route() : now.route(),
                        selected.contains(Part.SITE) ? old.site() : now.site(),
                        selected.contains(Part.DOSE) ? old.dose() : now.dose(),
                        selected.contains(Part.CODE) ? old.code() : now.code(),
                        selected.contains(Part.CODE) ? old.codeSystem() : now.codeSystem(),
                        selected.contains(Part.DATE) ? old.date() : now.date(),
                        selected.contains(Part.REFUSED) ? old.refused() : now.refused(),
                        selected.contains(Part.INSTRUCTIONS)
                                ? old.instructions()
                                : now.instructions(),
                        selected.contains(Part.NOTES) ? old.notes() : now.notes());
        boolean blocked =
                fields.values().stream()
                        .anyMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        if (!blocked) CdsExportImmunizationXmlWriter.validate(value);
        return new Result(
                index.sourceManifestSha256(),
                address,
                new CdsExportImmunizationMappingDto(blocked ? null : value, fields),
                preparedPaths,
                currentPaths);
    }

    /**
     * Values the import restored from the sender's {@code CARLOS.Prevention.Source.v1} (#6198): the
     * free-text "Other" provider (provider {@code -1} and its {@code providerName} extension) and
     * the reminder date. CDS has no element for them, so the retained occurrence carries them only
     * in that original carrier, which the retained assembler re-exports unchanged. They are
     * accounted to it while the sealed baseline proves the import wrote them and they are
     * unchanged; a changed or unproven value requires review, because the retained carrier would
     * restate the old one.
     */
    private static void restored(
            CdsImmunizationDto prepared,
            CdsExportCapturedTextRowDto parent,
            Row root,
            Map<String, Row> originalByKey,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields)
            throws IOException {
        if (prepared.providerName() != null) {
            var row = originalByKey.get("providerName");
            boolean proved =
                    row != null
                            && CdsExportImmunizationProjectionEvidence.matches(
                                    row.baseline(), "val", prepared.providerName())
                            && CdsExportImmunizationProjectionEvidence.matches(
                                    root.baseline(), "provider_no", "-1");
            boolean unchanged =
                    !root.difference().changedColumns().contains("provider_no")
                            && row != null
                            && Collections.disjoint(
                                    row.difference().changedColumns(),
                                    Set.of("keyval", "prevention_id", "val"));
            String rule = restoredRule(proved, unchanged);
            mark(parent, fields, Set.of("provider_no"), CdsExportImmunizationMapper.RESIDUAL, rule);
            if (row != null)
                mark(
                        row.current(),
                        fields,
                        Set.of("keyval", "val"),
                        CdsExportImmunizationMapper.RESIDUAL,
                        rule);
        }
        if (prepared.reminderDate() != null) {
            boolean proved =
                    CdsExportImmunizationProjectionEvidence.matches(
                            root.baseline(), "next_date", prepared.reminderDate());
            boolean unchanged = !root.difference().changedColumns().contains("next_date");
            mark(
                    parent,
                    fields,
                    Set.of("next_date"),
                    CdsExportImmunizationMapper.RESIDUAL,
                    restoredRule(proved, unchanged));
        }
    }

    private static String restoredRule(boolean proved, boolean unchanged) {
        return !proved
                ? "PREPARED_NATIVE_PROJECTION_NOT_PROVEN"
                : unchanged
                        ? "VERIFIED_UNCHANGED_PREPARED_FIELD_PROJECTION"
                        : "RESTORED_SOURCE_CONTEXT_CHANGED_REQUIRES_REVIEW";
    }

    private Map<String, Row> originalExtensions(
            Map<String, Row> prior,
            Map<String, String> expected,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields)
            throws IOException {
        var originalByKey = new HashMap<String, Row>();
        for (var entry : prior.entrySet())
            if (entry.getKey().startsWith("preventionsExt/")) {
                String found = null;
                for (String name : expected.keySet())
                    if (CdsExportImmunizationProjectionEvidence.matches(
                            entry.getValue().baseline(), "keyval", name)) {
                        found = name;
                        break;
                    }
                if (found == null) {
                    mark(
                            entry.getValue().current(),
                            fields,
                            Set.of("keyval", "val"),
                            "",
                            "UNMAPPED_IMPORTED_EXTENSION_PROJECTION");
                    continue;
                }
                if (originalByKey.putIfAbsent(found, entry.getValue()) != null)
                    review("CDS_EXPORT_IMMUNIZATION_DUPLICATE_BASELINE_EXTENSION");
            }
        return originalByKey;
    }

    private Map<String, Row> verifyReferenceBaselines(
            int patient,
            String nativeId,
            List<CdsExportRetainedReceiptVerificationDto.CheckedReference> references,
            Map<String, CdsExportCapturedTextRowDto> current,
            Map<String, CdsExportNativeRowReceiptDto> actual,
            Map<String, CdsNativeRowState> baselines)
            throws IOException {
        var prior = new HashMap<String, Row>();
        for (var reference : references) {
            var ref = reference.expected();
            String key = key(ref.table(), ref.id());
            var row = current.get(key);
            var witness = actual.get(key);
            if (reference.reason() == Reason.ROW_NOT_IN_CAPTURE || row == null)
                review("CDS_EXPORT_IMMUNIZATION_DELETION_REQUIRES_REVIEW");
            if (witness == null || witness.state() == null || reference.currentDigest() == null)
                review("CDS_EXPORT_IMMUNIZATION_NATIVE_WITNESS_REQUIRED");
            if (!witness.digest().equals(reference.currentDigest())) scope();
            var baseline = baselines.get(key);
            if (baseline == null && reference.reason() == Reason.UNCHANGED_ROW)
                baseline = witness.state();
            if (baseline == null || !baseline.digest().equals(ref.digest()))
                review("CDS_EXPORT_IMMUNIZATION_FIELD_BASELINE_REQUIRED");
            var difference = CdsNativeRowComparison.compare(baseline, witness.state());
            if (difference.schemaChanged() || !difference.unqualifiedColumns().isEmpty())
                review("CDS_EXPORT_IMMUNIZATION_FIELD_CODEC_REQUIRES_REVIEW");
            if (!CdsExportImmunizationProjectionEvidence.matches(baseline, "id", ref.id())) scope();
            if (ref.table().equals("preventions")) {
                if (!CdsExportImmunizationProjectionEvidence.matches(
                        baseline, "demographic_no", Integer.toString(patient))) scope();
            } else if (ref.table().equals("preventionsExt")) {
                if (!CdsExportImmunizationProjectionEvidence.matches(
                        baseline, "prevention_id", nativeId)) scope();
            } else if (!CdsExportImmunizationProjectionEvidence.matches(baseline, "table_name", "4")
                    || !CdsExportImmunizationProjectionEvidence.matches(
                            baseline, "table_id", nativeId)) scope();
            if (prior.putIfAbsent(key, new Row(row, baseline, difference)) != null) scope();
        }
        return prior;
    }

    private record CapturedRows(
            Map<String, CdsExportCapturedTextRowDto> current,
            Map<String, CdsExportNativeRowReceiptDto> actual,
            Map<String, CdsNativeRowState> baselines) {
        @Override
        public String toString() {
            return "CapturedRows[protected]";
        }
    }

    private CapturedRows captureRows(
            CdsExportRetainedIndexDto index,
            String address,
            CdsExportCapturedTextRowDto parent,
            List<CdsExportCapturedTextRowDto> extensions,
            List<CdsExportCapturedTextRowDto> precision,
            List<CdsExportNativeRowReceiptDto> witnesses)
            throws IOException {
        var current = new HashMap<String, CdsExportCapturedTextRowDto>();
        var actual = new HashMap<String, CdsExportNativeRowReceiptDto>();
        for (var witness : witnesses)
            if (Set.of("preventions", "preventionsExt", "partial_date").contains(witness.table())
                    && actual.putIfAbsent(key(witness.table(), witness.id()), witness) != null)
                scope();
        var inputs = new ArrayList<CdsExportCapturedTextRowDto>();
        inputs.add(parent);
        inputs.addAll(extensions);
        inputs.addAll(precision);
        for (var row : inputs) {
            String physical = physical(row.table()), id = row.fields().get("id");
            if (current.putIfAbsent(key(physical, id), row) != null) scope();
            CdsExportImmunizationProjectionEvidence.verifyCurrent(
                    row, actual.get(key(physical, id)));
        }
        String run = address.substring(0, 36), unitKey = address.substring(37);
        var unit = index.tables().get("cds_import_unit").get(CdsImportKeys.hash(run, unitKey));
        var baselines = new HashMap<String, CdsNativeRowState>();
        if (unit.fields().get("native_baseline") != null)
            try {
                for (var state :
                        CdsNativeBaselineCodec.decode(
                                        unit.fields().get("native_baseline"),
                                        unit.fields().get("target_references"),
                                        unit.fields().get("target_digest"))
                                .rows())
                    if (baselines.putIfAbsent(key(state.table(), state.id()), state) != null)
                        scope();
            } catch (IllegalArgumentException invalid) {
                scope();
            }
        return new CapturedRows(current, actual, baselines);
    }

    private static boolean hasUnprojectedTime(CdsExportDateDto date) {
        if (date.lexical() == null) return false;
        return switch (date.precision()) {
            case DATE_TIME ->
                    date.lexical().contains(".")
                            || date.lexical().matches(".*(?:Z|[+-][0-9]{2}:[0-9]{2})$");
            case YEAR -> date.lexical().length() > 4;
            case YEAR_MONTH -> date.lexical().length() > 7;
            case DATE -> date.lexical().length() > 10;
            case ABSENT -> false;
        };
    }

    private static void markPart(
            Part part,
            CdsExportCapturedTextRowDto parent,
            Map<String, Row> prior,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            String rule) {
        if (part == Part.TYPE) mark(parent, fields, Set.of("prevention_type"), part.path, rule);
        if (part == Part.REFUSED) mark(parent, fields, Set.of("refused"), part.path, rule);
        if (part == Part.DATE) mark(parent, fields, Set.of("prevention_date"), part.path, rule);
        for (String name : part.keys) {
            var row = prior.get(name);
            if (row != null) mark(row.current(), fields, Set.of("keyval", "val"), part.path, rule);
        }
    }

    private static void mark(
            CdsExportCapturedTextRowDto row,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            Set<String> columns,
            String path,
            String rule) {
        for (String column : columns) {
            var key = new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column);
            var old = fields.get(key);
            if (old == null)
                throw new IllegalArgumentException("CDS_EXPORT_IMMUNIZATION_FIELD_SCOPE_INVALID");
            boolean proved = rule.equals("VERIFIED_UNCHANGED_PREPARED_FIELD_PROJECTION");
            boolean metadata =
                    row.table().equals("preventionsExt")
                            && Set.of("cdsDateOriginal", "cdsDatePrecision")
                                    .contains(row.fields().get("keyval"));
            fields.put(
                    key,
                    new CdsExportFieldAssessmentDto(
                            old.presence(),
                            proved
                                    ? (metadata ? Outcome.OPERATIONAL : Outcome.MAPPED)
                                    : Outcome.REQUIRES_REVIEW,
                            path,
                            rule));
        }
    }

    private static String physical(String table) {
        return table.equals("prevention_precision") ? "partial_date" : table;
    }

    private static String key(String table, String id) {
        return table + "/" + id;
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_IMMUNIZATION_RECONCILIATION_SCOPE_INVALID");
    }

    /** A patient-specific data condition: the patient is held for review, not an I/O failure. */
    private static void review(String code) throws IOException {
        throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                .CdsExportMappingReviewException(code);
    }
}
