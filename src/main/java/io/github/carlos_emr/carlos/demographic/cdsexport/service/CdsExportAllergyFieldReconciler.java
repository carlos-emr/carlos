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
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsAllergyDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.Reason;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Fieldwise reconciliation of one imported allergy against authenticated native witnesses.
 * Historical values survive only when the original projection is proved and their own
 * dependencies are unchanged. A field changed in CARLOS since the import is exported as the
 * chart now holds it, by the same rule as on a never-imported allergy: a changed date with its
 * current precision rows (it supersedes a source clock or offset the column never held), a long
 * reaction in Notes, an unnamed agent under the native label, and every current value CDS has no
 * element for in {@code CARLOS.Allergy.Source.v1}, which then holds the complete current row.
 * Another field's edit cannot resurrect old content. Only what the import's receipt cannot
 * prove still holds the patient: a missing row, an alias, a missing baseline, and a sealed
 * preparation that does not describe the row the receipt says the import wrote
 * ({@code PREPARED_NATIVE_PROJECTION_NOT_PROVEN}: the retained source fields of that unit then
 * cannot be shown to belong to this row, so neither they nor a guess are exported).
 */
public final class CdsExportAllergyFieldReconciler {
    /**
     * @param currentSource the content of {@code CARLOS.Allergy.Source.v1} for the current row
     *     when any field was changed in CARLOS since the import, otherwise null. The assembler
     *     writes it when {@code carried} is set, and always replaces a sender's own carrier with
     *     it, because that carrier would restate the row as the sender held it.
     * @param carried whether a current value has no CDS element and travels only in {@code
     *     currentSource}
     */
    public record Result(
            String sourceManifestSha256,
            String unitAddress,
            CdsExportAllergyMappingDto mapping,
            Set<String> preparedPaths,
            Set<String> currentPaths,
            String currentSource,
            boolean carried) {
        public Result {
            preparedPaths = Set.copyOf(preparedPaths);
            currentPaths = Set.copyOf(currentPaths);
        }

        /** A reconciliation with nothing to carry beside the standard elements and markers. */
        public Result(
                String sourceManifestSha256,
                String unitAddress,
                CdsExportAllergyMappingDto mapping,
                Set<String> preparedPaths,
                Set<String> currentPaths) {
            this(sourceManifestSha256, unitAddress, mapping, preparedPaths, currentPaths, null, false);
        }

        @Override
        public String toString() {
            return "CdsExportAllergyFieldReconciler.Result[protected]";
        }
    }

    private enum Group {
        DESCRIPTION("OffendingAgentDescription", "DESCRIPTION"),
        PROPERTY("PropertyOfOffendingAgent", "nonDrug"),
        CODE(
                "Code",
                "regional_identifier",
                "TYPECODE",
                "drugref_id",
                "atc",
                "HICL_SEQNO",
                "HIC_SEQNO",
                "AGCSP",
                "AGCCS"),
        REACTION_TYPE("ReactionType", "reaction_type"),
        START("StartDate", "start_date"),
        LIFE("LifeStage", "life_stage"),
        SEVERITY("Severity", "severity_of_reaction"),
        REACTION("Reaction", "reaction"),
        RECORDED("RecordedDate", "entry_date"),
        ARCHIVED("ResidualInfo/" + CdsNativeExtensionContract.ALLERGY_ARCHIVED, "archived"),
        AGE("ResidualInfo/" + CdsNativeExtensionContract.ALLERGY_AGE_OF_ONSET, "age_of_onset"),
        ONSET(
                "ResidualInfo/" + CdsNativeExtensionContract.ALLERGY_ONSET_OF_REACTION,
                "onset_of_reaction");
        final String path;
        final Set<String> columns;

        Group(String path, String... columns) {
            this.path = path;
            this.columns = Set.of(columns);
        }
    }

    public Result reconcile(
            int patient,
            CdsExportRetainedIndexDto index,
            String unitAddress,
            CdsExportRetainedReceiptVerificationDto checked,
            CdsExportCapturedTextRowDto current,
            CdsExportNativeRowReceiptDto witness,
            List<CdsExportCapturedTextRowDto> precision)
            throws IOException {
        var prepared = new CdsExportRetainedPreparationReader().allergy(index, unitAddress);
        if (!index.sourceManifestSha256().equals(checked.sourceManifestSha256())
                || !index.appliedUnits().equals(checked.units().keySet())) scope();
        var references = checked.units().get(unitAddress);
        if (references == null
                || !references.stream()
                        .map(CdsExportRetainedReceiptVerificationDto.CheckedReference::expected)
                        .toList()
                        .equals(index.references().get(unitAddress))) scope();
        var baseline =
                verifyNativeBaseline(patient, index, unitAddress, references, current, witness);
        var difference = CdsNativeRowComparison.compare(baseline, witness.state());
        if (difference.schemaChanged() || !difference.unqualifiedColumns().isEmpty())
            review("CDS_EXPORT_ALLERGY_FIELD_CODEC_REQUIRES_REVIEW");
        var source = prepared.value();
        var old = new CdsExportPreparedAllergyMapper().map(source);
        var candidate = new CdsExportAllergyMapper().candidate(patient, current, precision);
        var decisions = new LinkedHashMap<>(candidate.fields());
        var values = candidate.value();
        var expected = projection(patient, witness.id(), source);
        var choosePrepared = EnumSet.noneOf(Group.class);
        var preparedPaths = new HashSet<String>();
        var currentPaths = new HashSet<String>();
        var currentPrecisionIds = new HashSet<String>();
        for (var row : precision) if (!currentPrecisionIds.add(row.fields().get("id"))) scope();
        var oldPrecisionIds = new HashSet<String>();
        boolean precisionUnchanged = true;
        for (var ref : references)
            if (ref.expected().table().equals("partial_date")) {
                if (!oldPrecisionIds.add(ref.expected().id())) scope();
                precisionUnchanged &= ref.reason() == Reason.UNCHANGED_ROW;
            }
        precisionUnchanged &= oldPrecisionIds.equals(currentPrecisionIds);
        for (var group : Group.values()) {
            boolean proved = true;
            for (String column : group.columns)
                proved &= matches(baseline, column, expected.get(column));
            if (!proved) {
                mark(current, decisions, group, "PREPARED_NATIVE_PROJECTION_NOT_PROVEN");
                continue;
            }
            boolean unchanged = Collections.disjoint(group.columns, difference.changedColumns());
            if ((group == Group.START || group == Group.RECORDED) && !precisionUnchanged) {
                // The date's precision rows changed since the import: the current column and its
                // current precision rows are the date, as on a never-imported allergy.
                currentPaths.add(group.path);
                continue;
            }
            // A date changed in CARLOS is a plain calendar date: it supersedes a source clock or
            // offset the native column never held, as an edited prescription date does.
            if (unchanged && group == Group.ARCHIVED && source.archivedNotRestored()) {
                // The source marked the allergy archived but this import kept it active
                // (decision 9): the native state, not the source marker, is what CARLOS holds.
                currentPaths.add(group.path);
                continue;
            }
            if (unchanged) {
                choosePrepared.add(group);
                preparedPaths.add(group.path);
                for (String column : group.columns) {
                    var field =
                            new CdsExportNativeFieldKeyDto(
                                    current.table(), current.recordKey(), column);
                    decisions.put(
                            field,
                            new CdsExportFieldAssessmentDto(
                                    decisions.get(field).presence(),
                                    Outcome.MAPPED,
                                    group.path,
                                    "VERIFIED_UNCHANGED_PREPARED_FIELD_PROJECTION"));
                }
            } else currentPaths.add(group.path);
        }
        boolean archivedNow =
                choosePrepared.contains(Group.ARCHIVED) ? old.archived() : values.archived();
        // The imported Notes are kept (they have no native column); a reaction changed in CARLOS
        // to one too long for Reaction is written after them, as a never-imported allergy writes
        // it in Notes, and exactly in CARLOS.Allergy.Reaction.v1.
        String notes = notes(old.notes(), old.archived(), archivedNow);
        boolean carried = false;
        if (!choosePrepared.contains(Group.REACTION) && values.longReaction() != null) {
            String earlier =
                    old.longReaction() == null
                            ? null
                            : CdsExportAllergyMapper.LONG_REACTION_LABEL + old.longReaction();
            String kept = notes;
            // The sender's own statement of the earlier long reaction is replaced, not repeated.
            if (earlier != null && kept != null && kept.endsWith(earlier))
                kept = kept.substring(0, kept.length() - earlier.length()).stripTrailing();
            String statement = CdsExportAllergyMapper.LONG_REACTION_LABEL + values.longReaction();
            String combined = kept == null || kept.isEmpty() ? statement : kept + "\n\n" + statement;
            if (CdsExportNativeFieldAssessments.legal(combined, 32000)) {
                notes = combined;
                currentPaths.add("Notes");
            } else {
                // No room beside the imported Notes: the reaction is exact in its own carrier
                // and in the current row's source context; Notes are left as imported.
                carried = true;
                var key =
                        new CdsExportNativeFieldKeyDto(
                                current.table(), current.recordKey(), "reaction");
                decisions.put(
                        key,
                        new CdsExportFieldAssessmentDto(
                                decisions.get(key).presence(),
                                Outcome.MAPPED,
                                CdsExportAllergyMapper.SOURCE,
                                CdsExportAllergyMapper.CARRIED));
            }
        }
        if (!currentPaths.contains("Notes"))
            preparedPaths.add(
                    "Notes"); // This standard field was retained but has no native allergy column.
        String description =
                choosePrepared.contains(Group.DESCRIPTION)
                        ? old.description()
                        : values.description();
        String codeType = choosePrepared.contains(Group.CODE) ? old.codeType() : values.codeType();
        String code = choosePrepared.contains(Group.CODE) ? old.codeValue() : values.codeValue();
        if ((description == null || description.isBlank()) && code == null) {
            // As on a never-imported allergy: the entry says the agent is not recorded and the
            // complete current row travels in its source context.
            description = CdsExportAllergyMapper.UNNAMED_AGENT;
            carried = true;
        }
        // Every current value CDS has no element for (a code outside its list, a date without
        // established precision, a recording provider or First Databank identifier): carried in
        // CARLOS.Allergy.Source.v1 by the rule of a never-imported allergy, never a hold. A group
        // kept from the import was decided above and is not touched.
        for (var entry : new ArrayList<>(decisions.entrySet())) {
            var decision = entry.getValue();
            if (decision.outcome() != Outcome.REQUIRES_REVIEW) continue;
            boolean row = entry.getKey().table().equals(current.table());
            String stored = row ? current.fields().get(entry.getKey().column()) : null;
            boolean overlong =
                    "WIRE_TEXT_REQUIRES_REVIEW".equals(decision.rule())
                            && stored != null
                            && CdsExportNativeFieldAssessments.legal(stored, Integer.MAX_VALUE);
            if (!CdsExportAllergyMapper.CARRIED_RULES.contains(decision.rule()) && !overlong)
                continue;
            carried = true;
            decisions.put(
                    entry.getKey(),
                    new CdsExportFieldAssessmentDto(
                            decision.presence(),
                            Outcome.MAPPED,
                            CdsExportAllergyMapper.SOURCE,
                            CdsExportAllergyMapper.CARRIED));
        }
        var result =
                new CdsExportAllergyDto(
                        description,
                        choosePrepared.contains(Group.PROPERTY)
                                ? old.property()
                                : values.property(),
                        codeType,
                        code,
                        choosePrepared.contains(Group.REACTION_TYPE)
                                ? old.reactionType()
                                : values.reactionType(),
                        choosePrepared.contains(Group.START) ? old.start() : values.start(),
                        choosePrepared.contains(Group.LIFE) ? old.lifeStage() : values.lifeStage(),
                        choosePrepared.contains(Group.SEVERITY)
                                ? old.severity()
                                : values.severity(),
                        choosePrepared.contains(Group.REACTION)
                                ? old.reaction()
                                : values.reaction(),
                        choosePrepared.contains(Group.RECORDED)
                                ? old.recorded()
                                : values.recorded(),
                        notes,
                        archivedNow,
                        choosePrepared.contains(Group.AGE) ? old.ageOfOnset() : values.ageOfOnset(),
                        choosePrepared.contains(Group.ONSET)
                                ? old.onsetOfReaction()
                                : values.onsetOfReaction(),
                        // A natively changed "Severe" has no CDS value; its label travels in the
                        // CARLOS residual exactly as for a native allergy. An unchanged imported
                        // "Severe" (restored from that residual) keeps its carrier.
                        choosePrepared.contains(Group.SEVERITY)
                                ? old.nativeSeverity()
                                : values.nativeSeverity(),
                        // An unchanged reaction keeps its carrier; a changed long one replaces
                        // it and is written in Notes above.
                        choosePrepared.contains(Group.REACTION)
                                ? old.longReaction()
                                : values.longReaction());
        boolean blocked =
                decisions.values().stream()
                        .anyMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        // The current row's complete context: written when a value has no element, and the
        // replacement of a sender's own carrier whenever anything was changed in CARLOS.
        boolean changed = !currentPaths.isEmpty() || carried;
        return new Result(
                index.sourceManifestSha256(),
                unitAddress,
                new CdsExportAllergyMappingDto(blocked ? null : result, decisions),
                preparedPaths,
                currentPaths,
                changed && !blocked
                        ? CdsExportAllergyMapper.sourceContext(current, precision)
                        : null,
                carried && !blocked);
    }

    /**
     * An imported allergy archived in CARLOS after import must not reach another EMR looking
     * current: the imported notes are kept and the archive statement appended once.
     */
    private static String notes(String imported, boolean wasArchived, boolean archived) {
        if (!archived || wasArchived) return imported;
        if (imported == null || imported.isEmpty()) return CdsExportAllergyMapper.ARCHIVED_NOTE;
        if (imported.endsWith(CdsExportAllergyMapper.ARCHIVED_NOTE)) return imported;
        return imported + "\n" + CdsExportAllergyMapper.ARCHIVED_NOTE;
    }

    private CdsNativeRowState verifyNativeBaseline(
            int patient,
            CdsExportRetainedIndexDto index,
            String unitAddress,
            List<CdsExportRetainedReceiptVerificationDto.CheckedReference> references,
            CdsExportCapturedTextRowDto current,
            CdsExportNativeRowReceiptDto witness)
            throws IOException {
        var allergyRefs =
                references.stream()
                        .filter(ref -> ref.expected().table().equals("allergies"))
                        .toList();
        if (allergyRefs.size() != 1
                || references.stream()
                        .anyMatch(
                                ref ->
                                        !Set.of("allergies", "partial_date")
                                                .contains(ref.expected().table()))) scope();
        var original = allergyRefs.getFirst();
        if (original.reason() == Reason.ROW_NOT_IN_CAPTURE)
            review("CDS_EXPORT_ALLERGY_DELETION_REQUIRES_REVIEW");
        if (current == null
                || witness == null
                || witness.state() == null
                || original.currentDigest() == null)
            review("CDS_EXPORT_ALLERGY_NATIVE_WITNESS_REQUIRED");
        if (!"allergies".equals(witness.table())
                || !original.expected().id().equals(witness.id())
                || !witness.id().equals(current.fields().get("allergyid"))
                || !current.recordKey().equals(witness.recordKey())
                || !original.currentDigest().equals(witness.digest())
                || !witness.digest().equals(witness.state().digest())) scope();
        for (var entry : index.references().entrySet())
            if (!entry.getKey().equals(unitAddress)
                    && entry.getValue().stream()
                            .anyMatch(
                                    ref ->
                                            ref.table().equals("allergies")
                                                    && ref.id().equals(witness.id())))
                review("CDS_EXPORT_ALLERGY_ALIAS_REQUIRES_REVIEW");
        if (!witness.id().equals(witness.state().id())
                || !witness.table().equals(witness.state().table())) scope();
        verifyCurrent(current, witness.state());
        String run = unitAddress.substring(0, 36), key = unitAddress.substring(37);
        var unit = index.tables().get("cds_import_unit").get(CdsImportKeys.hash(run, key));
        CdsNativeRowState baseline = null;
        if (unit.fields().get("native_baseline") != null) {
            try {
                var document =
                        CdsNativeBaselineCodec.decode(
                                unit.fields().get("native_baseline"),
                                unit.fields().get("target_references"),
                                unit.fields().get("target_digest"));
                baseline =
                        document.rows().stream()
                                .filter(
                                        row ->
                                                row.table().equals("allergies")
                                                        && row.id().equals(witness.id()))
                                .findFirst()
                                .orElse(null);
            } catch (IllegalArgumentException invalid) {
                scope();
            }
        } else if (original.reason() == Reason.UNCHANGED_ROW) baseline = witness.state();
        if (baseline == null || !baseline.digest().equals(original.expected().digest()))
            review("CDS_EXPORT_ALLERGY_FIELD_BASELINE_REQUIRED");
        if (!matches(baseline, "demographic_no", Integer.toString(patient))
                || !matches(baseline, "allergyid", witness.id())) scope();
        return baseline;
    }

    private static Map<String, String> projection(int patient, String id, CdsAllergyDto source) {
        var fields = new HashMap<String, String>();
        fields.put("allergyid", id);
        fields.put("demographic_no", Integer.toString(patient));
        fields.put(
                "DESCRIPTION",
                trim(source.description() == null ? source.code() : source.description()));
        fields.put(
                "nonDrug",
                "ND".equals(source.property()) ? "1" : "DR".equals(source.property()) ? "0" : null);
        fields.put("regional_identifier", trim(source.code()));
        fields.put("TYPECODE", "0");
        for (String column :
                List.of("drugref_id", "atc", "HICL_SEQNO", "HIC_SEQNO", "AGCSP", "AGCCS"))
            fields.put(column, null);
        if (source.atcResidual()) {
            fields.put("regional_identifier", null);
            fields.put("TYPECODE", "8");
            fields.put("atc", source.code());
        }
        fields.put("reaction_type", source.reactionType());
        fields.put("reaction", trim(source.reaction()));
        fields.put("life_stage", trim(source.lifeStage()));
        fields.put(
                "severity_of_reaction",
                source.nativeSeverity() != null
                        ? source.nativeSeverity()
                        : switch (source.severity() == null ? "" : source.severity()) {
                    case "NO" -> "5";
                    case "MI" -> "1";
                    case "MO" -> "2";
                    case "LT" -> "3";
                    default -> null;
                });
        fields.put(
                "start_date",
                source.start().earliest() == null ? null : source.start().earliest().toString());
        fields.put(
                "entry_date",
                source.recorded().earliest() == null
                        ? null
                        : source.recorded().earliest().toString());
        fields.put("archived", source.nativeArchived() ? "1" : "0");
        fields.put("age_of_onset", trim(source.ageOfOnset()));
        fields.put("onset_of_reaction", trim(source.onsetOfReaction()));
        return fields;
    }

    private static boolean matches(CdsNativeRowState baseline, String column, String expected)
            throws IOException {
        int index = -1;
        for (int i = 0; i < baseline.columns().size(); i++)
            if (baseline.columns().get(i).name().equals(column)) index = i;
        if (index < 0) scope();
        var cell = baseline.cells().get(index);
        if (expected == null) return cell.sqlNull();
        byte[] bytes = expected.getBytes(StandardCharsets.UTF_8);
        return !cell.sqlNull()
                && cell.bytes() == bytes.length
                && cell.sha256()
                        .equals(
                                java.util.HexFormat.of()
                                        .formatHex(CdsImportKeys.digest().digest(bytes)));
    }

    private static void verifyCurrent(CdsExportCapturedTextRowDto row, CdsNativeRowState state)
            throws IOException {
        if (!row.table().equals(state.table())
                || !row.fields()
                        .keySet()
                        .equals(
                                Set.copyOf(
                                        state.columns().stream()
                                                .map(CdsNativeRowDigest.Column::name)
                                                .toList()))) scope();
        for (var column : state.columns())
            if (!matches(state, column.name(), row.fields().get(column.name()))) scope();
    }

    private static void mark(
            CdsExportCapturedTextRowDto row,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> decisions,
            Group group,
            String rule) {
        for (String column : group.columns) {
            var field = new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column);
            decisions.put(
                    field,
                    new CdsExportFieldAssessmentDto(
                            decisions.get(field).presence(),
                            Outcome.REQUIRES_REVIEW,
                            group.path,
                            rule));
        }
    }

    private static String trim(String value) {
        return org.apache.commons.lang3.StringUtils.trimToNull(value);
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_ALLERGY_RECONCILIATION_SCOPE_INVALID");
    }

    /** A patient-specific data condition: the patient is held for review, not an I/O failure. */
    private static void review(String code) throws IOException {
        throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                .CdsExportMappingReviewException(code);
    }
}
