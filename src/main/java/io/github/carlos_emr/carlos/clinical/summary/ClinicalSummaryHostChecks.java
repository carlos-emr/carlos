/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic checks that hold whichever model, provider or local server wrote the draft.
 *
 * <p>Models and serving stacks differ in what they drop or misdate. These checks use only facts the
 * host already has: each note's authoritative date and its verbatim text. They never rewrite model
 * prose. Mirrors {@code tools/ai-clinical-summary-draft/host_checks.py}; keep the two aligned.
 *
 * @since 2026-09-21
 */
public final class ClinicalSummaryHostChecks {
    /** A date a claim asserts that none of its cited notes carries. */
    public record DateFinding(String claimId, String asserted, List<String> allowed, List<String> sourceIds) { }

    /** Vital-sign measurements recorded close together, with their verbatim text. */
    public record ObservationSet(List<String[]> measurements, String text) { }

    private static final DateTimeFormatter SLASH = DateTimeFormatter.ofPattern("dd/MM/yy");
    private static final String RESULTS_ID = "results_observations";
    private static final String RESULTS_TITLE = "Results and observations";
    /** A label, an optional joining word or punctuation, then the recorded value. */
    private static final String JOIN = "\\s*(?:of|is|was|at|:|=|-)?\\s*";
    private static final String[][] MEASUREMENTS = {
        {"hr", "\\b(?:HR|heart rate|pulse)\\b", "\\d{1,3}"},
        {"bp", "\\b(?:BP|blood pressure)\\b", "\\d{2,3}/\\d{2,3}"},
        {"rr", "\\b(?:RR|resp(?:iratory)? rate)\\b", "\\d{1,2}"},
        {"temp", "\\b(?:temp(?:erature)?)\\b", "\\d{2}(?:\\.\\d)?"},
        {"spo2", "\\b(?:SpO2|SaO2|O2 sats?|sats?|oxygen saturations?)\\b", "\\d{2,3}"},
    };
    private static final Pattern FINDER;
    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    /** Characters between neighbouring measurements of one observation set. */
    private static final int MAX_GAP = 80;
    private static final int MIN_KINDS = 3;
    private static final Pattern SLASH_DATE = Pattern.compile("(?<![\\d/])(\\d{2})/(\\d{2})/(\\d{2})(?![\\d/])");
    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");
    private static final Pattern ANY_SLASH_DATE = Pattern.compile("\\b\\d{2}/\\d{2}/\\d{2}\\b");
    private static final Pattern TOMORROW = Pattern.compile(
            "tomorrow|tmrw|tmw|next day|following (morning|day)|24 hours", Pattern.CASE_INSENSITIVE);
    private static final Pattern TWO_DAYS = Pattern.compile("48 hours|two days|day after tomorrow", Pattern.CASE_INSENSITIVE);

    static {
        StringBuilder finder = new StringBuilder();
        for (String[] measurement : MEASUREMENTS) {
            LABELS.put(measurement[0], measurement[1]);
            if (finder.length() > 0) finder.append('|');
            finder.append("(?<").append(measurement[0]).append('>').append(measurement[1]).append(JOIN)
                    .append("(?<").append(measurement[0]).append("Value>").append(measurement[2])
                    // A value may end a sentence ("SpO2 98."), but "36" is never read out of "36.8".
                    .append(")(?![\\d/]|\\.\\d))");
        }
        FINDER = Pattern.compile(finder.toString(), Pattern.CASE_INSENSITIVE);
    }

    private ClinicalSummaryHostChecks() { }

    /** Dates a claim asserts, normalized to dd/mm/yy for comparison. */
    static Set<String> claimDates(String text) {
        Set<String> found = new LinkedHashSet<>();
        // A longer slash run such as "note-10/12/13/14" lists note IDs, and 14/15/18 is no calendar date.
        Matcher slash = SLASH_DATE.matcher(text);
        while (slash.find()) {
            int day = Integer.parseInt(slash.group(1));
            int month = Integer.parseInt(slash.group(2));
            if (day >= 1 && day <= 31 && month >= 1 && month <= 12) found.add(slash.group());
        }
        Matcher iso = ISO_DATE.matcher(text);
        while (iso.find()) found.add(iso.group(3) + "/" + iso.group(2) + "/" + iso.group(1).substring(2));
        return found;
    }

    /** The note's own date, any date written in it, and a stated tomorrow or 48 hours. */
    static Set<String> supportedDates(String date, String text) {
        Set<String> allowed = new LinkedHashSet<>(claimDates(text));
        LocalDate stamp = parse(date);
        if (stamp == null) return allowed;
        allowed.add(stamp.format(SLASH));
        // These records abbreviate heavily, so accept the shorthand forms too. Anything further adrift
        // is still unsupported, which keeps a misdating with no relative wording caught.
        if (TOMORROW.matcher(text).find()) allowed.add(stamp.plusDays(1).format(SLASH));
        if (TWO_DAYS.matcher(text).find()) allowed.add(stamp.plusDays(2).format(SLASH));
        return allowed;
    }

    /** Reports each date a claim asserts that none of its cited notes carries. */
    public static List<DateFinding> dateFindings(JsonNode generated, JsonNode sources) {
        List<DateFinding> findings = new ArrayList<>();
        if (!wellFormed(generated)) return findings;
        Map<String, Set<String>> allowed = new LinkedHashMap<>();
        for (JsonNode source : sources) {
            allowed.put(source.path("id").asText(), supportedDates(source.path("date").asText(), source.path("text").asText()));
        }
        Comparator<String> chronological = Comparator.comparing(value -> value.substring(6) + value.substring(3, 5) + value.substring(0, 2));
        for (JsonNode claim : generated.get("claims")) {
            Set<String> carried = new TreeSet<>(chronological);
            List<String> cited = new ArrayList<>();
            for (JsonNode reference : claim.path("source_ids")) {
                cited.add(reference.asText());
                carried.addAll(allowed.getOrDefault(reference.asText(), Set.of()));
            }
            Set<String> asserted = new TreeSet<>(claimDates(claim.path("text").asText()));
            asserted.removeAll(carried);
            for (String date : asserted) {
                findings.add(new DateFinding(claim.path("id").asText(), date, List.copyOf(carried), cited));
            }
        }
        return findings;
    }

    /** Clusters of at least three kinds of vital-sign measurement recorded close together. */
    static List<ObservationSet> observationSets(String text) {
        List<ObservationSet> sets = new ArrayList<>();
        List<String[]> current = new ArrayList<>();
        List<String> spans = new ArrayList<>();
        int previousEnd = -1;
        Matcher match = FINDER.matcher(text);
        while (match.find()) {
            if (previousEnd >= 0 && match.start() - previousEnd > MAX_GAP) {
                close(sets, current, spans);
                current = new ArrayList<>();
                spans = new ArrayList<>();
            }
            for (String kind : LABELS.keySet()) {
                if (match.group(kind) != null) {
                    current.add(new String[]{kind, match.group(kind + "Value")});
                    spans.add(match.group(kind).replaceAll("\\s+", " "));
                    break;
                }
            }
            previousEnd = match.end();
        }
        close(sets, current, spans);
        return sets;
    }

    private static void close(List<ObservationSet> sets, List<String[]> current, List<String> spans) {
        Set<String> kinds = new LinkedHashSet<>();
        current.forEach(measurement -> kinds.add(measurement[0]));
        if (kinds.size() >= MIN_KINDS) sets.add(new ObservationSet(List.copyOf(current), String.join("; ", spans)));
    }

    /** Whether one claim states every measurement of a set beside a recognizable label. */
    private static boolean reports(String claimText, List<String[]> measurements) {
        for (String[] measurement : measurements) {
            Pattern stated = Pattern.compile(LABELS.get(measurement[0]) + "[^.;]{0,40}?(?<![\\d/.])"
                    + Pattern.quote(measurement[1]) + "(?![\\d/])", Pattern.CASE_INSENSITIVE);
            if (!stated.matcher(claimText).find()) return false;
        }
        return true;
    }

    /** Adds back, verbatim and labelled, each observation set the draft omitted. */
    public static ObjectNode restoreObservations(JsonNode generated, JsonNode sources) {
        if (!wellFormed(generated)) return (ObjectNode) generated;
        ObjectNode result = generated.deepCopy();
        ArrayNode claims = (ArrayNode) result.get("claims");
        StringBuilder prose = new StringBuilder();
        claims.forEach(claim -> prose.append(claim.path("text").asText()).append(' '));
        // Follow the draft's own date format so a host statement never makes the formats mixed.
        boolean iso = ISO_DATE.matcher(prose).find() && !ANY_SLASH_DATE.matcher(prose).find();
        Map<String, ObjectNode> added = new LinkedHashMap<>();
        for (JsonNode source : sources) {
            String id = source.path("id").asText();
            LocalDate stamp = parse(source.path("date").asText());
            if (stamp == null) continue;
            List<String> citedBy = new ArrayList<>();
            for (JsonNode claim : claims) {
                for (JsonNode reference : claim.path("source_ids")) {
                    if (id.equals(reference.asText())) citedBy.add(claim.path("text").asText());
                }
            }
            String day = iso ? stamp.toString() : stamp.format(SLASH);
            for (ObservationSet set : observationSets(source.path("text").asText())) {
                if (citedBy.stream().anyMatch(text -> reports(text, set.measurements()))) continue;
                String text = "Observations recorded on " + day + ", restored verbatim by the host because the draft "
                        + "omitted them: " + set.text() + ".";
                ObjectNode existing = added.get(text);
                if (existing != null) {
                    ArrayNode citations = (ArrayNode) existing.get("source_ids");
                    boolean present = false;
                    for (JsonNode reference : citations) present |= id.equals(reference.asText());
                    if (!present) citations.add(id);
                    continue;
                }
                ObjectNode claim = result.objectNode().put("id", "host-obs-" + (added.size() + 1));
                claim.putArray("source_ids").add(id);
                claim.put("text", text);
                added.put(text, claim);
                citedBy.add(text);
            }
        }
        if (added.isEmpty()) return result;
        ArrayNode sections = (ArrayNode) result.get("sections");
        ArrayNode members = null;
        for (JsonNode section : sections) {
            if (RESULTS_ID.equals(section.path("id").asText()) && section.path("claim_ids").isArray()) {
                members = (ArrayNode) section.get("claim_ids");
            }
        }
        if (members == null) {
            members = sections.addObject().put("id", RESULTS_ID).put("title", RESULTS_TITLE).putArray("claim_ids");
        }
        for (ObjectNode claim : added.values()) {
            claims.add(claim);
            members.add(claim.get("id").asText());
        }
        return result;
    }

    private static boolean wellFormed(JsonNode generated) {
        return generated != null && generated.isObject() && generated.path("claims").isArray()
                && generated.path("sections").isArray();
    }

    private static LocalDate parse(String date) {
        try {
            return date == null || date.length() < 10 ? null : LocalDate.parse(date.substring(0, 10));
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }
}
