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

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import java.io.IOException;
import java.util.*;

/**
 * Native vaccine interpretation after captured category/dependency closure. Every parent,
 * extension and precision field retains an explicit disposition. The complete native parent,
 * extension and precision rows travel in the {@code CARLOS.Prevention.Source.v1} residual, so a
 * value CARLOS records with no CDS Immunizations element (the recording provider, a reminder
 * date, flu risk factors, a refusal reason, a vaccine type outside the CT-020 list) is carried
 * exactly instead of refusing the chart. Empty extension rows, which the prevention form saves
 * for every unfilled input, are not facts. Duplicate standard keys, stale imported date hints,
 * program restrictions, removed versions and unknown statuses still require reconciliation; no
 * first/last row silently wins.
 */
public final class CdsExportImmunizationMapper {
    private final java.time.ZoneId zone;

    public CdsExportImmunizationMapper() {
        this(null);
    }

    public CdsExportImmunizationMapper(java.time.ZoneId zone) {
        this.zone = zone;
    }

    /** The CDS 5.2 Immunizations residual; the carrier is the screening one, shared by name. */
    static final String RESIDUAL = "ResidualInfo";

    static final String CARRIED = "EXACT_NATIVE_PREVENTION_SOURCE_CONTEXT";

    /**
     * The catch-all injection type, and the CARLOS importer's fallback for a vaccine whose CDS
     * ImmunizationType is absent or not configured (CdsImmunizationPersister.preventionType).
     */
    static final String CATCH_ALL_TYPE = CdsExportPreventionClassifier.CATCH_ALL_TYPE;

    /**
     * ImmunizationName of a catch-all entry recorded without a name: the item's label on the
     * prevention screen. The name field is optional there; the entry's exact rows, the empty name
     * included, travel in the prevention residual.
     */
    static final String CATCH_ALL_LABEL = "Other immunization or injection";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * The prevention form's statuses that are neither "given here" (0) nor "refused" (1). A
     * vaccine completed elsewhere was administered; an ineligible one was not, so it is never
     * exported as given. The native label opens Notes and the exact code is in the residual.
     */
    private static final Map<String, String> STATUS_NOTE =
            Map.of("2", "Ineligible", "3", "Completed externally");

    /** CdsImmunizationPersister date provenance; meaningful only through the reconciler. */
    private static final Set<String> STALE_IMPORT_HINTS =
            Set.of("cdsDateOriginal", "cdsDatePrecision");

    private static final Map<String, String> DESTINATIONS =
            Map.ofEntries(
                    Map.entry("name", "ImmunizationName"),
                    Map.entry("cdsName", "ImmunizationName"),
                    Map.entry("cdsType", "ImmunizationType"),
                    Map.entry("manufacture", "Manufacturer"),
                    Map.entry("lot", "LotNumber"),
                    Map.entry("route", "Route"),
                    Map.entry("location", "Site"),
                    Map.entry("dose", "Dose"),
                    Map.entry("comments", "Notes"),
                    Map.entry("instructions", "Instructions"),
                    Map.entry("din", "ImmunizationCode"),
                    Map.entry("cdsCode", "ImmunizationCode"),
                    Map.entry("cdsCodeSystem", "ImmunizationCode"));

    public CdsExportImmunizationMappingDto map(
            int patient,
            CdsExportCapturedTextRowDto row,
            List<CdsExportCapturedTextRowDto> extensions,
            List<CdsExportCapturedTextRowDto> precision,
            CdsExportPreventionClassifier classifier)
            throws IOException {
        var candidate = candidate(patient, row, extensions, precision, classifier);
        boolean blocked =
                candidate.fields().values().stream()
                        .anyMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        return new CdsExportImmunizationMappingDto(
                blocked ? null : candidate.value(), candidate.fields());
    }

    record Candidate(
            CdsExportImmunizationDto value,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields) {
        Candidate {
            fields = Map.copyOf(fields);
        }

        @Override
        public String toString() {
            return "CdsExportImmunizationMapper.Candidate[protected]";
        }
    }

    Candidate candidate(
            int patient,
            CdsExportCapturedTextRowDto row,
            List<CdsExportCapturedTextRowDto> extensions,
            List<CdsExportCapturedTextRowDto> precision,
            CdsExportPreventionClassifier classifier)
            throws IOException {
        int id = verifiedPatientRow(patient, row, extensions, precision, classifier);
        var decisions =
                new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var source = carried(row, decisions);
        for (String column :
                List.of("id", "demographic_no", "lastUpdateDate", "creation_date", "creator"))
            source.operational(column, "CAPTURED_NATIVE_IDENTITY_OR_STORAGE_AUDIT");
        String deleted = source.raw("deleted");
        if ("0".equals(deleted)) source.operational("deleted", "EXPLICIT_ACTIVE_NATIVE_RECORD");
        // A legacy NULL flag records no deletion: the row is the current version.
        else if (deleted == null) source.operational("deleted", "NO_RECORDED_DELETION");
        // The occurrence service never maps a superseded native version; reaching here means an
        // imported occurrence whose row was later edited or removed, which needs review.
        else if ("1".equals(deleted))
            source.review("deleted", "", "SUPERSEDED_OR_REMOVED_VERSION_IS_NOT_CURRENT");
        else source.review("deleted", "ResidualInfo", "ARCHIVE_STATE_NEEDS_A_LOSSLESS_CARRIER");
        if ("0".equals(source.raw("never")))
            source.operational("never", "NO_NATIVE_REMINDER_SUPPRESSION");
        else if ("1".equals(source.raw("never")))
            source.set("never", Outcome.MAPPED, RESIDUAL, "NATIVE_NEVER_REMIND_CARRIED");
        // The recording provider, reminder date, free-text administering provider, program of a
        // previously authorized unrestricted row and the CVC concept have no CDS Immunizations
        // element; carried() already maps them exactly to the residual.
        String restriction = source.raw("restrictToProgram");
        if ("0".equals(restriction) || restriction == null && source.raw("programNo") == null)
            source.operational("restrictToProgram", "PREVIOUSLY_AUTHORIZED_UNRESTRICTED_SCOPE");
        else source.review("restrictToProgram", "", "PROGRAM_SCOPE_REQUIRES_REVIEW");
        String nativeType = source.raw("prevention_type");
        var category = classifier.classify(nativeType, source.raw("snomedId"));
        if (category.kind() != CdsExportPreventionClassifier.Kind.IMMUNIZATION)
            source.review(
                    "prevention_type",
                    "ImmunizationName",
                    "CAPTURED_TYPE_IS_NOT_PROVEN_IMMUNIZATION");
        var byKey = extensionFields(id, extensions, decisions);
        String name = oneOf(byKey, List.of("name", "cdsName"), "ImmunizationName", 120);
        if (!byKey.containsKey("name")
                && !byKey.containsKey("cdsName")
                && nativeType != null
                && !nativeType.equals(CATCH_ALL_TYPE)
                && CdsExportNativeFieldAssessments.legal(nativeType, 120)) name = nativeType;
        if ((name == null || name.isBlank())
                && category.rule() == CdsExportPreventionClassifier.Rule.CATCH_ALL_INJECTION
                && !byKey.containsKey("cdsName")) name = CATCH_ALL_LABEL;
        if (name == null || name.isBlank())
            source.review("prevention_type", "ImmunizationName", "IMMUNIZATION_NAME_REQUIRED");
        boolean typeProven = true;
        boolean typeCarried = false;
        String type = oneOf(byKey, List.of("cdsType"), "ImmunizationType", 20);
        if (!byKey.containsKey("cdsType")) {
            var definition = classifier.definition(nativeType);
            if (definition.isPresent()) type = definition.get().healthCanadaType();
            if (type != null && type.isEmpty()) type = null;
        }
        if (type != null && !CdsExportImmunizationVocabulary.TYPES.contains(type)) {
            if (byKey.containsKey("cdsType")) {
                // An explicit wire type outside the list came from an import; it needs review.
                typeProven = false;
                source.review(
                        "prevention_type", "ImmunizationType", "NO_PINNED_WIRE_TYPE_EQUIVALENCE");
                for (var fields : byKey.get("cdsType"))
                    fields.review("val", "ImmunizationType", "NO_PINNED_WIRE_TYPE_EQUIVALENCE");
            } else
                // A configured CARLOS vaccine (COVID-19, Pneu-C-20, RZV, ...) whose type is not
                // a CT-020 value: ImmunizationType is optional and omitted, the name is exported
                // and the native type is in the residual.
                typeCarried = true;
            type = null;
        }
        var configuredType =
                classifier
                        .definition(nativeType)
                        .map(CdsExportPreventionClassifier.Definition::healthCanadaType)
                        .orElse(null);
        if (byKey.containsKey("cdsType")
                && type != null
                && configuredType != null
                && !configuredType.isBlank()
                && !type.equals(configuredType)) {
            typeProven = false;
            source.review(
                    "prevention_type", "ImmunizationType", "CONFLICTING_NATIVE_IMMUNIZATION_TYPE");
            for (var fields : byKey.get("cdsType"))
                fields.review("val", "ImmunizationType", "CONFLICTING_NATIVE_IMMUNIZATION_TYPE");
        }
        if (category.kind() == CdsExportPreventionClassifier.Kind.IMMUNIZATION
                && name != null
                && !name.isBlank()
                && typeProven)
            source.set(
                    "prevention_type",
                    Outcome.MAPPED,
                    typeCarried
                            ? "ImmunizationName;" + RESIDUAL
                            : "ImmunizationName/ImmunizationType",
                    category.rule() == CdsExportPreventionClassifier.Rule.CATCH_ALL_INJECTION
                            ? "CATCH_ALL_INJECTION_AS_UNTYPED_IMMUNIZATION"
                            : typeCarried
                                    ? "NATIVE_TYPE_OUTSIDE_CT020_CARRIED_IN_RESIDUAL"
                                    : "EXACT_NATIVE_NAME_AND_CAPTURED_CONFIGURED_TYPE");
        boolean refused = false;
        String refusal = source.raw("refused"), statusNote = null;
        if ("0".equals(refusal) || "1".equals(refusal)) {
            refused = "1".equals(refusal);
            source.set("refused", Outcome.MAPPED, "RefusedFlag", "EXPLICIT_NATIVE_REFUSED_BOOLEAN");
        } else if (refusal != null && STATUS_NOTE.containsKey(refusal)) {
            // Completed externally was given (N); ineligible was not given (Y), never "given".
            refused = "2".equals(refusal);
            statusNote = STATUS_NOTE.get(refusal);
            source.set(
                    "refused",
                    Outcome.MAPPED,
                    "RefusedFlag;Notes;" + RESIDUAL,
                    "2".equals(refusal)
                            ? "INELIGIBLE_IS_NOT_ADMINISTERED"
                            : "COMPLETED_EXTERNALLY_IS_ADMINISTERED");
        } else
            source.review(
                    "refused", "RefusedFlag", "UNKNOWN_NATIVE_STATUS_NEEDS_RECONCILIATION");
        var dateRows = new ArrayList<CdsExportNativeFieldAssessments>();
        var seen = new HashSet<String>();
        for (var item : precision) {
            shape(
                    item,
                    "prevention_precision",
                    CdsExportPatientTableReadSupport.Plan.PREVENTION_PRECISION);
            if (!seen.add(item.recordKey())
                    || !item.recordKey()
                            .equals(
                                    CdsExportPatientTableSourceReader.key(
                                            integer(item.fields().get("id"))))
                    || !"4".equals(item.fields().get("table_name"))
                    || integer(item.fields().get("table_id")) != id) scope();
            var fields = carried(item, decisions);
            for (String column : List.of("id", "table_name", "table_id"))
                fields.operational(column, "VERIFIED_IMMUNIZATION_PRECISION_IDENTITY");
            if ("7".equals(fields.raw("field_name"))) {
                fields.operational("field_name", "EXPLICIT_IMMUNIZATION_EVENT_DATE");
                dateRows.add(fields);
            } else {
                fields.review("field_name", "", "UNSUPPORTED_OR_NONSTANDARD_IMMUNIZATION_DATE");
                fields.review("format", "", "UNSUPPORTED_OR_NONSTANDARD_IMMUNIZATION_DATE");
            }
        }
        CdsExportDateDto date = new CdsExportDateDto(CdsExportDateDto.Precision.ABSENT, null);
        try {
            var formats = new ArrayList<String>();
            for (var fields : dateRows) formats.add(fields.raw("format"));
            date =
                    new CdsExportNativePreventionDateMapper(zone)
                            .map(source.raw("prevention_date"), formats);
            source.set(
                    "prevention_date",
                    date.precision() == CdsExportDateDto.Precision.ABSENT
                            ? Outcome.ABSENT
                            : Outcome.MAPPED,
                    "Date",
                    "EXACT_NATIVE_EVENT_DATE_AND_PRECISION");
            for (var fields : dateRows)
                fields.set("format", Outcome.MAPPED, "Date", "EXPLICIT_NATIVE_DATE_PRECISION");
        } catch (IOException invalidDate) {
            source.review("prevention_date", "Date", "DATE_PRECISION_REQUIRES_REVIEW");
            for (var fields : dateRows)
                fields.review("format", "Date", "DATE_PRECISION_REQUIRES_REVIEW");
        }
        if (byKey.containsKey("din")
                && !byKey.containsKey("cdsCode")
                && !byKey.containsKey("cdsCodeSystem")
                && byKey.get("din").size() == 1
                && !byKey.get("din").getFirst().raw("val").matches("[0-9]{8}")) {
            // A DIN typed into the prevention form that is not an eight-digit DIN cannot be the
            // standard code; it is carried exactly, never exported as a DIN.
            var typed = byKey.remove("din").getFirst();
            typed.set("val", Outcome.MAPPED, RESIDUAL, "NATIVE_TEXT_IS_NOT_A_DIN_CARRIED");
        }
        String din = oneOf(byKey, List.of("din", "cdsCode"), "ImmunizationCode", 8);
        String system = oneOf(byKey, List.of("cdsCodeSystem"), "ImmunizationCode", 20);
        if (din != null) {
            if (!din.matches("[0-9]{8}")
                    || system != null && !system.equals("DIN")
                    || !byKey.containsKey("din") && !"DIN".equals(system)) {
                for (String key : List.of("din", "cdsCode", "cdsCodeSystem"))
                    for (var fields : byKey.getOrDefault(key, List.of()))
                        fields.review(
                                "val", "ImmunizationCode", "EXPLICIT_VALID_DIN_AUTHORITY_REQUIRED");
                din = null;
                system = null;
            } else system = "DIN";
        } else if (system != null)
            for (var fields : byKey.get("cdsCodeSystem"))
                fields.review("val", "ImmunizationCode", "CODE_AND_AUTHORITY_MUST_BE_PAIRED");
        var commentsFields = byKey.getOrDefault("comments", List.of());
        String notes = oneOf(byKey, List.of("comments"), "Notes", 32000);
        if (statusNote != null) {
            notes = notes == null ? statusNote : statusNote + "\n" + notes;
            if (!CdsExportNativeFieldAssessments.legal(notes, 32000)) {
                source.review("refused", "Notes", "IMMUNIZATION_NOTES_CAPACITY_REQUIRES_REVIEW");
                for (var fields : commentsFields)
                    fields.review("val", "Notes", "IMMUNIZATION_NOTES_CAPACITY_REQUIRES_REVIEW");
                notes = null;
            }
        }
        var context = new LinkedHashMap<String, Object>();
        context.put("version", 1);
        context.put("prevention", row.fields());
        context.put("extensions", sorted(extensions).stream().map(r -> r.fields()).toList());
        context.put("precision", sorted(precision).stream().map(r -> r.fields()).toList());
        var value =
                new CdsExportImmunizationDto(
                        name,
                        type,
                        oneOf(byKey, List.of("manufacture"), "Manufacturer", 120),
                        oneOf(byKey, List.of("lot"), "LotNumber", 120),
                        oneOf(byKey, List.of("route"), "Route", 120),
                        oneOf(byKey, List.of("location"), "Site", 120),
                        oneOf(byKey, List.of("dose"), "Dose", 120),
                        din,
                        din == null ? null : system,
                        date,
                        refused,
                        oneOf(byKey, List.of("instructions"), "Instructions", 250),
                        notes,
                        CdsExportScreeningMapper.tokenSafe(JSON.writeValueAsString(context)));
        return new Candidate(value, decisions);
    }

    private static Map<String, List<CdsExportNativeFieldAssessments>> extensionFields(
            int id,
            List<CdsExportCapturedTextRowDto> extensions,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> decisions)
            throws IOException {
        var byKey = new HashMap<String, List<CdsExportNativeFieldAssessments>>();
        var seen = new HashSet<String>();
        for (var ext : extensions) {
            shape(ext, "preventionsExt", CdsExportPatientTableReadSupport.Plan.PREVENTIONS_EXT);
            if (!seen.add(ext.recordKey())
                    || !ext.recordKey()
                            .equals(
                                    CdsExportPatientTableSourceReader.key(
                                            integer(ext.fields().get("id"))))
                    || integer(ext.fields().get("prevention_id")) != id) scope();
            var fields = carried(ext, decisions);
            fields.operational("id", "CAPTURED_EXTENSION_ROW_IDENTITY");
            fields.operational("prevention_id", "VERIFIED_IMMUNIZATION_PARENT");
            String key = fields.raw("keyval");
            if (key == null || key.isEmpty()) {
                fields.review("keyval", "", "UNKNOWN_NATIVE_EXTENSION_KEY");
                fields.review("val", "", "UNKNOWN_NATIVE_EXTENSION_KEY");
                continue;
            }
            String value = fields.raw("val");
            if (value == null || value.isEmpty()) {
                // The prevention form saves one row per input, filled or not (addHashtoArray):
                // an empty value is not a recorded fact and never hides the type name.
                fields.operational("keyval", "EMPTY_NATIVE_EXTENSION_ROW");
                fields.set(
                        "val",
                        Outcome.ABSENT,
                        "",
                        value == null ? "SQL_NULL_EXTENSION_VALUE" : "EMPTY_EXTENSION_VALUE");
                continue;
            }
            if ("previousId".equals(key)) {
                // An edit's link to the version it superseded (CdsExportPreventionVersions).
                fields.operational("keyval", "SUPERSEDED_VERSION_LINK");
                fields.operational("val", "SUPERSEDED_VERSION_LINK");
                continue;
            }
            if (DESTINATIONS.containsKey(key)) {
                byKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(fields);
                fields.operational("keyval", "EXPLICIT_NATIVE_IMMUNIZATION_FIELD");
            } else if (STALE_IMPORT_HINTS.contains(key)) {
                // The importer's date provenance; on a row no applied import unit claims it is a
                // stale hint that could contradict the current event date.
                fields.review("keyval", "Date", "STALE_IMPORTED_DATE_HINT_REQUIRES_RECONCILIATION");
                fields.review("val", "Date", "STALE_IMPORTED_DATE_HINT_REQUIRES_RECONCILIATION");
            }
            // Every other key (location2, result, reason, neverReason, providerName, expiryDate,
            // flu risk factors, CVC brand, ...) stays MAPPED to the residual by carried().
        }
        for (var entry : byKey.entrySet())
            if (entry.getValue().size() > 1)
                for (var fields : entry.getValue()) {
                    fields.review(
                            "keyval",
                            DESTINATIONS.getOrDefault(entry.getKey(), ""),
                            "DUPLICATE_NATIVE_EXTENSION_REQUIRES_REVIEW");
                    fields.review(
                            "val",
                            DESTINATIONS.getOrDefault(entry.getKey(), ""),
                            "DUPLICATE_NATIVE_EXTENSION_REQUIRES_REVIEW");
                }
        return byKey;
    }

    private static int verifiedPatientRow(
            int patient,
            CdsExportCapturedTextRowDto row,
            List<CdsExportCapturedTextRowDto> extensions,
            List<CdsExportCapturedTextRowDto> precision,
            CdsExportPreventionClassifier classifier)
            throws IOException {
        shape(row, "preventions", CdsExportPatientTableReadSupport.Plan.PREVENTIONS_FULL);
        int id = integer(row.fields().get("id"));
        if (patient < 1
                || !Integer.toString(patient).equals(row.fields().get("demographic_no"))
                || !row.recordKey().equals(CdsExportPatientTableSourceReader.key(id))
                || extensions == null
                || extensions.size() > 1024
                || precision == null
                || precision.size() > 256
                || classifier == null) scope();
        classifier.sourceManifestSha256();
        return id;
    }

    /** Every captured field is carried exactly in the residual unless a rule says otherwise. */
    private static CdsExportNativeFieldAssessments carried(
            CdsExportCapturedTextRowDto row,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> decisions) {
        var fields = new CdsExportNativeFieldAssessments(row, decisions);
        for (var entry : row.fields().entrySet())
            if (entry.getValue() == null)
                fields.set(entry.getKey(), Outcome.ABSENT, "", "SQL_NULL_CARRIED_IN_RESIDUAL");
            else if (entry.getValue().isEmpty())
                fields.set(entry.getKey(), Outcome.ABSENT, "", "EMPTY_TEXT_CARRIED_IN_RESIDUAL");
            else if (CdsExportNativeFieldAssessments.legal(entry.getValue(), Integer.MAX_VALUE))
                fields.set(entry.getKey(), Outcome.MAPPED, RESIDUAL, CARRIED);
            else fields.review(entry.getKey(), RESIDUAL, "WIRE_TEXT_REQUIRES_REVIEW");
        return fields;
    }

    private static List<CdsExportCapturedTextRowDto> sorted(
            List<CdsExportCapturedTextRowDto> rows) {
        return rows.stream()
                .sorted(Comparator.comparing(CdsExportCapturedTextRowDto::recordKey))
                .toList();
    }

    private static String oneOf(
            Map<String, List<CdsExportNativeFieldAssessments>> source,
            List<String> keys,
            String destination,
            int maximum) {
        var present = new ArrayList<CdsExportNativeFieldAssessments>();
        for (String key : keys) present.addAll(source.getOrDefault(key, List.of()));
        if (present.isEmpty()) return null;
        if (present.size() != 1) {
            for (var fields : present) {
                fields.review("keyval", destination, "CONFLICTING_NATIVE_FIELD_ALIASES");
                fields.review("val", destination, "CONFLICTING_NATIVE_FIELD_ALIASES");
            }
            return null;
        }
        var fields = present.getFirst();
        String value = fields.raw("val");
        if (value == null) return null;
        if (!CdsExportNativeFieldAssessments.legal(value, maximum)) {
            fields.review("val", destination, "WIRE_TEXT_REQUIRES_REVIEW");
            return null;
        }
        fields.set("val", Outcome.MAPPED, destination, "EXACT_NATIVE_EXTENSION_TEXT");
        return value;
    }

    private static void shape(
            CdsExportCapturedTextRowDto row,
            String table,
            CdsExportPatientTableReadSupport.Plan plan)
            throws IOException {
        if (row == null
                || !table.equals(row.table())
                || !row.fields().keySet().equals(Set.copyOf(plan.columns()))
                || !row.exactNumbers().isEmpty()) scope();
    }

    private static int integer(String text) throws IOException {
        try {
            int id = Integer.parseInt(text);
            if (!Integer.toString(id).equals(text)) scope();
            return id;
        } catch (NumberFormatException failed) {
            throw new IOException("CDS_EXPORT_IMMUNIZATION_MAPPING_SCOPE_INVALID");
        }
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_IMMUNIZATION_MAPPING_SCOPE_INVALID");
    }
}
