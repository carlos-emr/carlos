/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsClinicalDiagnosticDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Restores the exact text of prescription, allergy and vaccine elements a CARLOS sender wrote
 * with U+FFFD, from the stored row its source carrier holds.
 *
 * <p>The sender writes each such element by one rule ({@link
 * CdsNativeExtensionContract#xmlElementText}: U+FFFD in place of each character XML 1.0 cannot
 * carry, one for one) from stored columns, alone or composed with fixed text, and carries the
 * whole row ({@code CARLOS.Medication.Source.v1} or, for a prescription it changed after its own
 * import, {@code CARLOS.Medication.CurrentSource.v1}; {@code CARLOS.Allergy.Source.v1}; {@code
 * CARLOS.Prevention.Source.v1}). Only an element that shows U+FFFD is considered. Each element
 * has the compositions the exporter can write it as; one whose U+FFFD form is exactly the element
 * ({@link CdsNativeExtensionContract#isElementTextOf}) is the element's stored text. One such
 * text replaces the element. A carrier that holds nothing the element could be written from, or
 * holds the element's own text, leaves it unchanged without a report (the U+FFFD is the sender's
 * own). Otherwise (no composition proves itself, two different ones do, the carrier is
 * unreadable) the element is kept and the report names the family's code with the element's
 * path. Nothing is guessed, and a file from another sender is never read this way.
 */
final class CdsCarriedTextResolver {
    static final String MEDICATION_NOT_APPLIED = "CDS_MEDICATION_EXACT_TEXT_NOT_APPLIED";
    static final String ALLERGY_NOT_APPLIED = "CDS_ALLERGY_EXACT_TEXT_NOT_APPLIED";
    static final String IMMUNIZATION_NOT_APPLIED = "CDS_IMMUNIZATION_EXACT_TEXT_NOT_APPLIED";

    private static final String U_FFFD = "�";

    private final CdsFieldParser fields;

    CdsCarriedTextResolver(CdsFieldParser fields) {
        this.fields = Objects.requireNonNull(fields);
    }

    /** The columns one element is composed from, and its compositions from a stored row. */
    private record Rule(Set<String> columns, Function<Map<String, String>, List<String>> compose) {}

    private static final Map<String, Rule> MEDICATION = medicationRules();
    private static final Map<String, Rule> ALLERGY =
            Map.of(
                    "OffendingAgentDescription", single("DESCRIPTION"),
                    "Reaction", single("reaction"));

    private static Map<String, Rule> medicationRules() {
        var rules = new LinkedHashMap<String, Rule>();
        // DrugName: the custom name when one is recorded, otherwise the brand name.
        rules.put("DrugName", new Rule(Set.of("customName", "BN"), row -> values(row, "customName", "BN")));
        rules.put("Form", single("drug_form"));
        rules.put("Route", single("route"));
        // Frequency: the stored code, followed by " PRN" when the as-needed box qualifies it.
        rules.put(
                "Frequency",
                new Rule(
                        Set.of("freqcode"),
                        row ->
                                row.get("freqcode") == null
                                        ? List.of()
                                        : List.of(row.get("freqcode"), row.get("freqcode") + " PRN")));
        rules.put("Dosage", single("cds_dose_text"));
        rules.put("DosageUnitOfMeasure", new Rule(Set.of("unit", "unitName"), row -> values(row, "unit", "unitName")));
        // Notes: the comment, or a name longer than DrugName leading it.
        rules.put(
                "Notes",
                new Rule(
                        Set.of("comment", "customName", "BN"),
                        row -> {
                            var result = new ArrayList<>(values(row, "comment"));
                            for (String name : values(row, "customName", "BN")) {
                                String lead = CdsNativeExtensionContract.MEDICATION_LONG_NAME_LABEL + name;
                                result.add(lead);
                                if (row.get("comment") != null) result.add(lead + "\n" + row.get("comment"));
                            }
                            return result;
                        }));
        // PrescriptionInstructions: the SIG, alone or followed by the pharmacy instructions; an
        // imported row's own pharmacy instructions.
        rules.put(
                "PrescriptionInstructions",
                new Rule(
                        Set.of("special", "special_instruction"),
                        row -> {
                            var result = new ArrayList<>(values(row, "special", "special_instruction"));
                            if (row.get("special") != null && row.get("special_instruction") != null)
                                result.add(
                                        row.get("special")
                                                + CdsNativeExtensionContract.MEDICATION_PHARMACY_INSTRUCTIONS_LABEL
                                                + row.get("special_instruction"));
                            return result;
                        }));
        rules.put("DispenseInterval", single("dispense_interval"));
        rules.put("ProtocolIdentifier", single("protocol"));
        rules.put("PriorPrescriptionReferenceIdentifier", single("priorRxProtocol"));
        return java.util.Collections.unmodifiableMap(rules);
    }

    private static Rule single(String column) {
        return new Rule(Set.of(column), row -> values(row, column));
    }

    private static List<String> values(Map<String, String> row, String... columns) {
        var result = new ArrayList<String>();
        for (String column : columns) {
            String value = row.get(column);
            if (value != null && !value.isEmpty()) result.add(value);
        }
        return result;
    }

    /**
     * The prescription's elements, each restored where its carrier proves it.
     *
     * @param elements element path to element text (null when absent), as read
     * @param carrier the content of the carrier stating the current row, or null
     */
    Map<String, String> medication(
            CdsRecordDto record,
            CdsFieldDto carrier,
            Map<String, String> elements,
            Consumer<CdsClinicalDiagnosticDto> notice)
            throws IOException {
        return restore(record, carrier, "fields", MEDICATION, elements, MEDICATION_NOT_APPLIED, notice);
    }

    /** The allergy's description and reaction, each restored where its carrier proves it. */
    Map<String, String> allergy(
            CdsRecordDto record,
            CdsFieldDto carrier,
            Map<String, String> elements,
            Consumer<CdsClinicalDiagnosticDto> notice)
            throws IOException {
        return restore(record, carrier, "fields", ALLERGY, elements, ALLERGY_NOT_APPLIED, notice);
    }

    private Map<String, String> restore(
            CdsRecordDto record,
            CdsFieldDto carrier,
            String row,
            Map<String, Rule> rules,
            Map<String, String> elements,
            String code,
            Consumer<CdsClinicalDiagnosticDto> notice)
            throws IOException {
        var result = new LinkedHashMap<>(elements);
        var shown = new LinkedHashSet<String>();
        for (var element : elements.entrySet())
            if (element.getValue() != null
                    && element.getValue().contains(U_FFFD)
                    && rules.containsKey(element.getKey())) shown.add(element.getKey());
        if (shown.isEmpty() || carrier == null) return result;
        var wanted = new HashMap<String, Integer>();
        for (String path : shown)
            for (String column : rules.get(path).columns())
                wanted.merge(row + "/" + column, elements.get(path).length(), Math::max);
        CdsCarriedValuesReader.Values read;
        try {
            read = CdsCarriedValuesReader.read(() -> fields.openToken(record, carrier), wanted, 0);
        } catch (IllegalArgumentException malformed) {
            for (String path : shown) notice.accept(new CdsClinicalDiagnosticDto(code, path));
            return result;
        }
        var stored = new HashMap<String, String>();
        read.values().forEach((path, value) -> stored.put(path.substring(row.length() + 1), value));
        for (String path : shown)
            decide(path, elements.get(path), rules.get(path).compose().apply(stored), code, result, notice);
        return result;
    }

    /** The element's stored text where exactly one composition proves itself; else a report. */
    private static void decide(
            String path,
            String element,
            List<String> compositions,
            String code,
            Map<String, String> result,
            Consumer<CdsClinicalDiagnosticDto> notice) {
        // Nothing the element could be written from, or the element itself: the sender's text.
        if (compositions.isEmpty() || compositions.contains(element)) return;
        var proven = new LinkedHashSet<String>();
        for (String exact : compositions)
            if (CdsNativeExtensionContract.isElementTextOf(element, exact)) proven.add(exact);
        if (proven.size() == 1) result.put(path, proven.iterator().next());
        else notice.accept(new CdsClinicalDiagnosticDto(code, path));
    }

    /**
     * The vaccine's elements, each restored where {@code CARLOS.Prevention.Source.v1} proves it.
     * Each element is one extension value (ImmunizationName also the prevention type the form
     * saved); Notes is the composition the exporter writes: the status label, the values too long
     * for their elements as labelled lines, the comments, whole parts only within 32,000
     * characters.
     */
    Map<String, String> immunization(
            CdsRecordDto record,
            CdsFieldDto carrier,
            Map<String, String> elements,
            Consumer<CdsClinicalDiagnosticDto> notice)
            throws IOException {
        var result = new LinkedHashMap<>(elements);
        var shown = new LinkedHashSet<String>();
        for (var element : elements.entrySet())
            if (element.getValue() != null
                    && element.getValue().contains(U_FFFD)
                    && IMMUNIZATION_KEYS.containsKey(element.getKey())) shown.add(element.getKey());
        if (shown.isEmpty() || carrier == null) return result;
        int units = 1;
        for (String path : shown) units = Math.max(units, elements.get(path).length());
        CdsCarriedValuesReader.Values read;
        try {
            read =
                    CdsCarriedValuesReader.read(
                            () -> fields.openToken(record, carrier),
                            Map.of(
                                    "prevention/prevention_type", units,
                                    "prevention/refused", 8),
                            units);
        } catch (IllegalArgumentException malformed) {
            for (String path : shown)
                notice.accept(new CdsClinicalDiagnosticDto(IMMUNIZATION_NOT_APPLIED, path));
            return result;
        }
        var byKey = new HashMap<String, List<String>>();
        var longerKeys = new java.util.HashSet<String>();
        for (int i = 0; i < read.extensions().size(); i++) {
            var row = read.extensions().get(i);
            if (row.getKey() == null) continue;
            if (read.longer().contains("extensions[" + i + "]/val")) longerKeys.add(row.getKey());
            byKey.computeIfAbsent(row.getKey(), ignored -> new ArrayList<>()).add(row.getValue());
        }
        for (String path : shown) {
            List<String> compositions;
            if (path.equals("Notes")) {
                String notes =
                        immunizationNotes(read.values().get("prevention/refused"), byKey, longerKeys);
                compositions = notes == null ? List.of() : List.of(notes);
            } else {
                compositions = new ArrayList<>();
                for (String key : IMMUNIZATION_KEYS.get(path))
                    for (String value : byKey.getOrDefault(key, List.of()))
                        if (value != null && !value.isEmpty()) compositions.add(value);
                if (path.equals("ImmunizationName")
                        && read.values().get("prevention/prevention_type") != null)
                    compositions.add(read.values().get("prevention/prevention_type"));
            }
            decide(path, elements.get(path), compositions, IMMUNIZATION_NOT_APPLIED, result, notice);
        }
        return result;
    }

    /** The extension keys each vaccine element is written from (CdsExportImmunizationMapper). */
    private static final Map<String, List<String>> IMMUNIZATION_KEYS =
            Map.of(
                    "ImmunizationName", List.of("name", "cdsName"),
                    "Manufacturer", List.of("manufacture"),
                    "LotNumber", List.of("lot"),
                    "Route", List.of("route"),
                    "Site", List.of("location"),
                    "Dose", List.of("dose"),
                    "Instructions", List.of("instructions"),
                    "Notes", List.of("comments"));

    /**
     * The vaccine's Notes as the exporter composes them from the stored values, or null when no
     * composition can be made (a value it needs is longer than the element).
     */
    private static String immunizationNotes(
            String refused, Map<String, List<String>> byKey, Set<String> longerKeys) {
        String status = CdsNativeExtensionContract.PREVENTION_STATUS_NOTES.get(refused);
        var moved = new ArrayList<String>();
        boolean movedLonger = false;
        for (var line : CdsNativeExtensionContract.PREVENTION_NOTES_LINES) {
            var present = new ArrayList<String>();
            boolean longer = false;
            for (String key : line.keys()) {
                present.addAll(byKey.getOrDefault(key, List.of()));
                longer |= longerKeys.contains(key);
            }
            if (present.size() != 1) continue;
            if (longer) movedLonger = true;
            String value = present.getFirst();
            if (value != null && value.codePointCount(0, value.length()) > line.maximum())
                moved.add(line.label() + ": " + value);
        }
        var comments = byKey.getOrDefault("comments", List.of());
        String comment =
                comments.size() == 1
                                && comments.getFirst() != null
                                && !longerKeys.contains("comments")
                                && comments.getFirst()
                                                .codePointCount(0, comments.getFirst().length())
                                        <= CdsNativeExtensionContract.PREVENTION_NOTES_LENGTH
                        ? comments.getFirst()
                        : null;
        // A moved value longer than the element cannot be in it: the exporter left the moved
        // lines out, and the comments before them, keeping the status label alone.
        if (movedLonger) return status;
        if (status == null && moved.isEmpty()) return comment;
        var lines = new ArrayList<String>();
        if (status != null) lines.add(status);
        lines.addAll(moved);
        if (comment != null) lines.add(comment);
        String all = String.join("\n", lines);
        if (all.codePointCount(0, all.length()) <= CdsNativeExtensionContract.PREVENTION_NOTES_LENGTH)
            return all;
        var kept = new ArrayList<String>();
        if (status != null) kept.add(status);
        kept.addAll(moved);
        String some = String.join("\n", kept);
        if (some.codePointCount(0, some.length()) > CdsNativeExtensionContract.PREVENTION_NOTES_LENGTH)
            kept.removeAll(moved);
        return kept.isEmpty() ? null : String.join("\n", kept);
    }
}
