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

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import java.io.IOException;
import java.util.*;

/**
 * Native vaccine interpretation after captured category/dependency closure. Every parent,
 * extension and precision field retains an explicit disposition. Duplicate keys, stale
 * imported date hints, archive/clinical fields without a carrier, unqualified coding and
 * unsupported native statuses require reconciliation; no first/last row silently wins.
 */
public final class CdsExportImmunizationMapper {
    private final java.time.ZoneId zone;

    public CdsExportImmunizationMapper() {
        this(null);
    }

    public CdsExportImmunizationMapper(java.time.ZoneId zone) {
        this.zone = zone;
    }

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
        var source = new CdsExportNativeFieldAssessments(row, decisions);
        for (String column :
                List.of("id", "demographic_no", "lastUpdateDate", "creation_date", "creator"))
            source.operational(column, "CAPTURED_NATIVE_IDENTITY_OR_STORAGE_AUDIT");
        if ("0".equals(source.raw("deleted")))
            source.operational("deleted", "EXPLICIT_ACTIVE_NATIVE_RECORD");
        else source.review("deleted", "ResidualInfo", "ARCHIVE_STATE_NEEDS_A_LOSSLESS_CARRIER");
        if ("0".equals(source.raw("never")))
            source.operational("never", "NO_NATIVE_REMINDER_SUPPRESSION");
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
                && !nativeType.equals("OtherA")
                && CdsExportNativeFieldAssessments.legal(nativeType, 120)) name = nativeType;
        if (name == null || name.isBlank())
            source.review("prevention_type", "ImmunizationName", "IMMUNIZATION_NAME_REQUIRED");
        boolean typeProven = true;
        String type = oneOf(byKey, List.of("cdsType"), "ImmunizationType", 20);
        if (!byKey.containsKey("cdsType")) {
            var definition = classifier.definition(nativeType);
            if (definition.isPresent()) type = definition.get().healthCanadaType();
            if (type != null && type.isEmpty()) type = null;
        }
        if (type != null && !CdsExportImmunizationVocabulary.TYPES.contains(type)) {
            typeProven = false;
            source.review("prevention_type", "ImmunizationType", "NO_PINNED_WIRE_TYPE_EQUIVALENCE");
            if (byKey.containsKey("cdsType"))
                for (var fields : byKey.get("cdsType"))
                    fields.review("val", "ImmunizationType", "NO_PINNED_WIRE_TYPE_EQUIVALENCE");
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
                    "ImmunizationName/ImmunizationType",
                    "EXACT_NATIVE_NAME_AND_CAPTURED_CONFIGURED_TYPE");
        boolean refused = false;
        String refusal = source.raw("refused");
        if ("0".equals(refusal) || "1".equals(refusal)) {
            refused = "1".equals(refusal);
            source.set("refused", Outcome.MAPPED, "RefusedFlag", "EXPLICIT_NATIVE_REFUSED_BOOLEAN");
        } else
            source.review(
                    "refused",
                    "RefusedFlag",
                    "INELIGIBLE_EXTERNAL_OR_UNKNOWN_STATUS_NEEDS_RECONCILIATION");
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
            var fields = new CdsExportNativeFieldAssessments(item, decisions);
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
                        oneOf(byKey, List.of("comments"), "Notes", 32000));
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
            var fields = new CdsExportNativeFieldAssessments(ext, decisions);
            fields.operational("id", "CAPTURED_EXTENSION_ROW_IDENTITY");
            fields.operational("prevention_id", "VERIFIED_IMMUNIZATION_PARENT");
            String key = fields.raw("keyval");
            if (key == null || key.isEmpty()) {
                fields.review("keyval", "", "UNKNOWN_NATIVE_EXTENSION_KEY");
                fields.review("val", "", "UNKNOWN_NATIVE_EXTENSION_KEY");
                continue;
            }
            byKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(fields);
            if (DESTINATIONS.containsKey(key))
                fields.operational("keyval", "EXPLICIT_NATIVE_IMMUNIZATION_FIELD");
            else fields.review("keyval", "", "UNMAPPED_NATIVE_EXTENSION_KEY");
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
