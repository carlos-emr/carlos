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
import static io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryAgentProtocol.JSON;

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
                    // A value may end a sentence ("SpO2 98.") or a unit ("RR 16/min"), but "36" is never
                    // read out of "36.8" and "124" never out of "124/78".
                    .append(")(?!\\d|/\\d|\\.\\d))");
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
                    + Pattern.quote(measurement[1]) + "(?!\\d|/\\d|\\.\\d)", Pattern.CASE_INSENSITIVE);
            if (!stated.matcher(claimText).find()) return false;
        }
        return true;
    }

    /** Adds back, verbatim and labelled, each observation set the draft omitted. */
    public static ObjectNode restoreObservations(JsonNode generated, JsonNode sources) {
        if (!wellFormed(generated)) return (ObjectNode) generated;
        ObjectNode result = generated.deepCopy();
        ArrayNode claims = (ArrayNode) result.get("claims");
        boolean iso = writesIsoDates(claims);
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

    /** Follow the draft's own date format so a host statement never makes the formats mixed. */
    private static boolean writesIsoDates(JsonNode claims) {
        StringBuilder prose = new StringBuilder();
        claims.forEach(claim -> prose.append(claim.path("text").asText()).append(' '));
        return ISO_DATE.matcher(prose).find() && !ANY_SLASH_DATE.matcher(prose).find();
    }

    // ---- names and identifiers -------------------------------------------------------------

    private static final String TITLE = "(?:Nurse|Dr\\.?|Doctor|Consultant|Registrar|Therapist|Physio(?:therapist)?|Pharmacist|"
            + "Midwife|Sister|Surgeon|Anaesthetist|Radiographer|Dietitian|Paramedic|HCA|SHO|Mr|Mrs|Ms|Miss)";
    private static final String NAME_PART = "(?:[A-Z][A-Za-z'\\-]+|van|der|de|al)";
    private static final Pattern STAFF = Pattern.compile(TITLE + "[ \\t]+(" + NAME_PART + "(?:[ \\t]+" + NAME_PART + "){1,3})");
    private static final Pattern[] IDENTITY = {
        Pattern.compile("NHS\\s*(?:No\\.?|number)[:\\s]*(\\d{9,10})", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bDOB[:\\s]*(\\d{2}/\\d{2}/\\d{2,4})", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(\\d{1,3})[- ]year[- ]old\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(\\d{1,3})[ \\t]*[MF]\\b(?![a-z])"),
    };

    /** A claim that names a member of staff or carries a patient identifier. */
    public record NameFinding(String claimId, List<String> terms) { }

    /** Names that follow a professional title in the notes; the notes are the host's own record of staff. */
    static Set<String> staffNames(JsonNode sources) {
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode source : sources) {
            Matcher match = STAFF.matcher(source.path("text").asText());
            while (match.find()) names.add(match.group(1));
        }
        return names;
    }

    /** NHS numbers, dates of birth and ages written in the notes, plus the patient's name from the host. */
    static Set<String> identityTerms(JsonNode sources, String patientLabel) {
        Set<String> terms = new LinkedHashSet<>();
        for (JsonNode source : sources) {
            for (Pattern pattern : IDENTITY) {
                Matcher match = pattern.matcher(source.path("text").asText());
                while (match.find()) {
                    String value = match.group(1);
                    if (value.matches("\\d{1,3}")) {
                        terms.add(value + "-year-old");
                        terms.add(value + " year old");
                    } else {
                        terms.add(value);
                    }
                }
            }
        }
        if (patientLabel != null && !patientLabel.isBlank()) {
            String label = patientLabel.replaceFirst("^FAKE-\\w+\\s+", "");
            terms.add(label);
            int comma = label.indexOf(", ");
            if (comma > 0) terms.add(label.substring(comma + 2) + " " + label.substring(0, comma));
        }
        return terms;
    }

    /** Claims that name a member of staff or carry a patient identifier. */
    public static List<NameFinding> nameFindings(JsonNode generated, JsonNode sources, String patientLabel) {
        List<NameFinding> findings = new ArrayList<>();
        if (!wellFormed(generated)) return findings;
        Set<String> terms = new LinkedHashSet<>(staffNames(sources));
        terms.addAll(identityTerms(sources, patientLabel));
        for (JsonNode claim : generated.get("claims")) {
            String text = claim.path("text").asText();
            List<String> hit = new ArrayList<>(new TreeSet<>(terms.stream().filter(text::contains).toList()));
            if (!hit.isEmpty()) findings.add(new NameFinding(claim.path("id").asText(), hit));
        }
        return findings;
    }

    // ---- duplicate statements ----------------------------------------------------------------

    private static final Set<String> STOP = Set.of("about", "after", "also", "and", "are", "been", "being", "for",
            "from", "had", "has", "have", "into", "more", "new", "noted", "patient", "recorded", "report", "reported",
            "source", "that", "the", "their", "there", "this", "was", "were", "with", "without", "on", "of", "in",
            "to", "a", "an", "at", "by");
    private static final Pattern TOKEN = Pattern.compile("\\d{2}/\\d{2}/\\d{2,4}|[a-z]+|\\d+(?:\\.\\d+)?");

    /** A statement in one section that restates a statement in another. */
    public record Restatement(String longer, String shorter) { }

    /** Words, numbers with decimals, and whole dates; an ISO date is the same fact as its dd/mm/yy form. */
    static Set<String> tokens(String text) {
        String normalized = ISO_DATE.matcher(text).replaceAll(m -> m.group(3) + "/" + m.group(2) + "/" + m.group(1).substring(2));
        Set<String> found = new LinkedHashSet<>();
        Matcher match = TOKEN.matcher(normalized.toLowerCase());
        while (match.find()) {
            if (!STOP.contains(match.group())) found.add(match.group());
        }
        return found;
    }

    /** Fold statements with identical text into one carrying every citation. */
    public static ObjectNode mergeIdenticalClaims(JsonNode generated) {
        if (!wellFormed(generated)) return (ObjectNode) generated;
        ObjectNode result = generated.deepCopy();
        Map<String, ObjectNode> first = new LinkedHashMap<>();
        Set<String> dropped = new LinkedHashSet<>();
        for (JsonNode claim : result.get("claims")) {
            String key = claim.path("text").asText().replaceAll("\\s+", " ").strip().toLowerCase(java.util.Locale.ROOT);
            ObjectNode kept = first.get(key);
            if (kept == null) {
                first.put(key, (ObjectNode) claim);
            } else {
                addCitations(kept, claim);
                dropped.add(claim.path("id").asText());
            }
        }
        dropClaims(result, dropped);
        return result;
    }

    /**
     * Pairs of claims in different sections that restate one fact. Two statements restate one fact
     * only if the shorter's numbers and dates all appear in the longer; the same template of words
     * on two days with different values is two facts.
     */
    public static List<Restatement> nearDuplicates(JsonNode generated) {
        List<Restatement> pairs = new ArrayList<>();
        if (!wellFormed(generated)) return pairs;
        Map<String, String> sectionOf = new LinkedHashMap<>();
        for (JsonNode section : generated.get("sections")) {
            for (JsonNode id : section.path("claim_ids")) sectionOf.put(id.asText(), section.path("id").asText());
        }
        List<JsonNode> claims = new ArrayList<>();
        generated.get("claims").forEach(claims::add);
        for (int i = 0; i < claims.size(); i++) {
            for (int j = i + 1; j < claims.size(); j++) {
                JsonNode one = claims.get(i);
                JsonNode two = claims.get(j);
                if (java.util.Objects.equals(sectionOf.get(one.path("id").asText()), sectionOf.get(two.path("id").asText()))) continue;
                Set<String> a = tokens(one.path("text").asText());
                Set<String> b = tokens(two.path("text").asText());
                if (a.isEmpty() || b.isEmpty()) continue;
                Set<String> shared = new LinkedHashSet<>(a);
                shared.retainAll(b);
                Set<String> union = new LinkedHashSet<>(a);
                union.addAll(b);
                double jaccard = (double) shared.size() / union.size();
                double containment = (double) shared.size() / Math.min(a.size(), b.size());
                JsonNode longer = a.size() >= b.size() ? one : two;
                JsonNode shorter = longer == one ? two : one;
                Set<String> shorterNumbers = numbers(longer == one ? b : a);
                Set<String> longerNumbers = numbers(longer == one ? a : b);
                if (jaccard >= 0.30 && containment >= 0.60 && longerNumbers.containsAll(shorterNumbers)) {
                    pairs.add(new Restatement(longer.path("id").asText(), shorter.path("id").asText()));
                }
            }
        }
        return pairs;
    }

    private static Set<String> numbers(Set<String> tokens) {
        Set<String> result = new LinkedHashSet<>();
        for (String token : tokens) if (Character.isDigit(token.charAt(0))) result.add(token);
        return result;
    }

    private static void addCitations(ObjectNode kept, JsonNode from) {
        ArrayNode citations = (ArrayNode) kept.get("source_ids");
        Set<String> present = new LinkedHashSet<>();
        citations.forEach(id -> present.add(id.asText()));
        for (JsonNode id : from.path("source_ids")) if (present.add(id.asText())) citations.add(id.asText());
    }

    /** Removes the given claims and their section memberships; sections left empty are removed. */
    static void dropClaims(ObjectNode result, Set<String> dropped) {
        if (dropped.isEmpty()) return;
        ArrayNode claims = (ArrayNode) result.get("claims");
        for (int i = claims.size() - 1; i >= 0; i--) if (dropped.contains(claims.get(i).path("id").asText())) claims.remove(i);
        ArrayNode sections = (ArrayNode) result.get("sections");
        for (int s = sections.size() - 1; s >= 0; s--) {
            ArrayNode ids = (ArrayNode) sections.get(s).path("claim_ids");
            for (int i = ids.size() - 1; i >= 0; i--) if (dropped.contains(ids.get(i).asText())) ids.remove(i);
            if (ids.isEmpty()) sections.remove(s);
        }
    }

    // ---- same-class medication conflicts -----------------------------------------------------

    private static final String MEDICATIONS_ID = "medications_allergies";
    private static final String MEDICATIONS_TITLE = "Medications and allergies";
    /** An order, not a mention: a dose beside the name, or an ordering verb before it. */
    private static final Pattern DOSE = Pattern.compile("^[^.;\\n]{0,40}?\\d[\\d,.]*\\s*(?:mg|mcg|micrograms?|g|units?|iu|ml|mmol)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDERED = Pattern.compile("(?:start|commenc|prescrib|continu|give|administer|initiat)\\w*[^.;\\n]{0,30}?$", Pattern.CASE_INSENSITIVE);
    /** Anchored to the start of a word: an unanchored "stop" is found inside "postoperative". */
    private static final Pattern CHANGE = Pattern.compile("\\b(?:stop|discontinu|ceas|withh|held\\b|switch|chang|replac|instead of|convert|transition)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SWITCH = Pattern.compile("\\b(?:switch|chang|convert|transition|replac)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPORTED = Pattern.compile("conflict|discrepan|inconsisten|unresolved", Pattern.CASE_INSENSITIVE);
    /** Characters either side of a drug name in which a stop or switch counts as recorded for it. */
    private static final int NEAR = 60;
    private static final Map<Map<String, String>, Pattern> NAME_PATTERNS = new java.util.WeakHashMap<>();

    /** Two drugs of one ATC chemical subgroup, each ordered in the record, with no recorded stop or switch. */
    public record Conflict(String group, Map<String, List<String>> drugs) { }

    /** A claim that says one drug of a conflicting pair was switched or changed when no note records it. */
    public record ChangeFinding(String claimId, List<String> drugs, List<String> sourceIds) { }

    private static synchronized Pattern namePattern(Map<String, String> classes) {
        return NAME_PATTERNS.computeIfAbsent(classes, table -> {
            List<String> names = new ArrayList<>(table.keySet());
            names.sort(Comparator.comparingInt(String::length).reversed());
            StringBuilder alternation = new StringBuilder("\\b(");
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) alternation.append('|');
                alternation.append(Pattern.quote(names.get(i)));
            }
            return Pattern.compile(alternation.append(")\\b").toString(), Pattern.CASE_INSENSITIVE);
        });
    }

    /** The class table maps a lower-case drug name to its ATC code; the first five characters are the subgroup. */
    public static List<Conflict> medicationConflicts(JsonNode sources, Map<String, String> classes) {
        List<Conflict> conflicts = new ArrayList<>();
        if (classes == null || classes.isEmpty()) return conflicts;
        Map<String, Map<String, List<String>>> groups = new LinkedHashMap<>();
        Set<String> changed = new LinkedHashSet<>();
        Pattern names = namePattern(classes);
        for (JsonNode source : sources) {
            String text = source.path("text").asText();
            Matcher match = names.matcher(text);
            while (match.find()) {
                String name = match.group(1).toLowerCase(java.util.Locale.ROOT);
                String before = text.substring(Math.max(0, match.start() - NEAR), match.start());
                String after = text.substring(match.end(), Math.min(text.length(), match.end() + 80));
                boolean ordered = DOSE.matcher(after).find() || ORDERED.matcher(before).find();
                boolean change = CHANGE.matcher(before + after.substring(0, Math.min(after.length(), NEAR))).find();
                if (ordered) {
                    List<String> ids = groups.computeIfAbsent(classes.get(name).substring(0, 5), g -> new LinkedHashMap<>())
                            .computeIfAbsent(name, n -> new ArrayList<>());
                    if (!ids.contains(source.path("id").asText())) ids.add(source.path("id").asText());
                }
                if (change) changed.add(name);
            }
        }
        for (Map.Entry<String, Map<String, List<String>>> group : groups.entrySet()) {
            if (group.getValue().size() > 1 && group.getValue().keySet().stream().noneMatch(changed::contains)) {
                conflicts.add(new Conflict(group.getKey(), group.getValue()));
            }
        }
        return conflicts;
    }

    /** States each same-class conflict the draft did not report itself, without asserting a resolution. */
    public static ObjectNode noteMedicationConflicts(JsonNode generated, JsonNode sources, Map<String, String> classes) {
        if (!wellFormed(generated)) return (ObjectNode) generated;
        ObjectNode result = generated.deepCopy();
        List<ObjectNode> added = new ArrayList<>();
        boolean iso = writesIsoDates(result.get("claims"));
        Map<String, String> dates = new LinkedHashMap<>();
        for (JsonNode source : sources) {
            LocalDate stamp = parse(source.path("date").asText());
            dates.put(source.path("id").asText(), stamp == null ? "" : iso ? stamp.toString() : stamp.format(SLASH));
        }
        for (Conflict conflict : medicationConflicts(sources, classes)) {
            boolean reported = false;
            for (JsonNode claim : result.get("claims")) {
                String text = claim.path("text").asText();
                if (REPORTED.matcher(text).find() && conflict.drugs().keySet().stream().allMatch(name ->
                        Pattern.compile("\\b" + Pattern.quote(name) + "\\b", Pattern.CASE_INSENSITIVE).matcher(text).find())) {
                    reported = true;
                }
            }
            if (reported) continue;
            List<String> described = new ArrayList<>();
            Set<String> cited = new LinkedHashSet<>();
            for (Map.Entry<String, List<String>> drug : conflict.drugs().entrySet()) {
                Set<String> when = new LinkedHashSet<>();
                drug.getValue().forEach(id -> when.add(dates.get(id)));
                described.add(drug.getKey() + " (" + String.join(", ", when) + ")");
                cited.addAll(drug.getValue());
            }
            ObjectNode claim = result.objectNode().put("id", "host-med-" + (added.size() + 1));
            ArrayNode citations = claim.putArray("source_ids");
            cited.forEach(citations::add);
            claim.put("text", "Medication records conflict, as found by the host: " + String.join(" and ", described)
                    + " belong to the same drug class (ATC " + conflict.group() + ") and no note records either being "
                    + "stopped, so the record does not show which is intended.");
            added.add(claim);
        }
        if (added.isEmpty()) return result;
        ArrayNode sections = (ArrayNode) result.get("sections");
        ArrayNode members = null;
        for (JsonNode section : sections) {
            if (MEDICATIONS_ID.equals(section.path("id").asText()) && section.path("claim_ids").isArray()) members = (ArrayNode) section.get("claim_ids");
        }
        if (members == null) members = sections.addObject().put("id", MEDICATIONS_ID).put("title", MEDICATIONS_TITLE).putArray("claim_ids");
        for (ObjectNode claim : added) {
            ((ArrayNode) result.get("claims")).add(claim);
            members.add(claim.get("id").asText());
        }
        return result;
    }

    /** Claims that say one drug of a conflicting pair was switched or changed when no note records it. */
    public static List<ChangeFinding> undocumentedChanges(JsonNode generated, JsonNode sources, Map<String, String> classes) {
        List<ChangeFinding> findings = new ArrayList<>();
        if (!wellFormed(generated)) return findings;
        for (Conflict conflict : medicationConflicts(sources, classes)) {
            List<String> names = new ArrayList<>(new TreeSet<>(conflict.drugs().keySet()));
            for (JsonNode claim : generated.get("claims")) {
                if (claim.path("id").asText().startsWith("host-")) continue;
                String text = claim.path("text").asText();
                for (String name : names) {
                    Matcher match = Pattern.compile("\\b" + Pattern.quote(name) + "\\b", Pattern.CASE_INSENSITIVE).matcher(text);
                    if (match.find() && SWITCH.matcher(text.substring(Math.max(0, match.start() - NEAR),
                            Math.min(text.length(), match.end() + NEAR))).find()) {
                        List<String> cited = new ArrayList<>();
                        claim.path("source_ids").forEach(id -> cited.add(id.asText()));
                        findings.add(new ChangeFinding(claim.path("id").asText(), names, cited));
                        break;
                    }
                }
            }
        }
        return findings;
    }

    // ---- formatting faults settled instead of discarding the draft ---------------------------

    private static final Map<String, String> SECTION_TITLES = new LinkedHashMap<>();
    static {
        SECTION_TITLES.put("clinical_overview", "Clinical overview");
        SECTION_TITLES.put("active_problems", "Active problems");
        SECTION_TITLES.put("medications_allergies", "Medications and allergies");
        SECTION_TITLES.put("results_observations", "Results and observations");
        SECTION_TITLES.put("plan_follow_up", "Plan and follow-up");
    }

    /** A draft with its formatting faults settled, and one sentence per settlement for the validation record. */
    public record Tolerated(ObjectNode output, List<String> notes) { }

    /**
     * Settles the model's formatting faults that would otherwise discard a whole draft: a duplicate
     * claim ID is renumbered, a section outside the fixed five is folded into Clinical overview, a
     * wrong title or repeated membership is corrected, and a statement sharing no word with the notes
     * it cites is dropped and named. A reference to a statement that never existed is left for
     * validation to reject, and a statement under several headings for placement to resolve.
     */
    public static Tolerated tolerateStructure(JsonNode generated, JsonNode sources) {
        List<String> notes = new ArrayList<>();
        if (!wellFormed(generated)) return new Tolerated((ObjectNode) generated, notes);
        ObjectNode result = generated.deepCopy();
        Set<String> seen = new LinkedHashSet<>();
        Map<String, List<String>> renamed = new LinkedHashMap<>();
        for (JsonNode claim : result.get("claims")) {
            if (!claim.isObject() || !claim.path("id").isTextual()) return new Tolerated((ObjectNode) generated, List.of());
            String id = claim.get("id").asText();
            if (seen.contains(id)) {
                Set<String> taken = new LinkedHashSet<>(seen);
                renamed.values().forEach(taken::addAll);
                String candidate = id;
                for (int n = 2; taken.contains(candidate); n++) candidate = id + "-" + n;
                notes.add("Statement " + id + " shared its ID with another; the second is now " + candidate + ".");
                renamed.computeIfAbsent(id, key -> new ArrayList<>()).add(candidate);
                ((ObjectNode) claim).put("id", candidate);
            }
            seen.add(claim.get("id").asText());
        }
        Map<String, Set<String>> sourceWords = new LinkedHashMap<>();
        for (JsonNode source : sources) {
            sourceWords.put(source.path("id").asText(), ClinicalSummaryGenerationService.words(
                    source.path("title").asText() + " " + source.path("date").asText() + " " + source.path("text").asText()));
        }
        Set<String> dropped = new LinkedHashSet<>();
        for (JsonNode claim : result.get("claims")) {
            if (!claim.path("text").isTextual() || !claim.path("source_ids").isArray()) continue;
            // Host metadata echoed as prose is a scope fault validation must still reject, not settle.
            if (ClinicalSummaryGenerationService.SOURCE_METADATA.matcher(claim.get("text").asText()).find()) continue;
            Set<String> cited = new LinkedHashSet<>();
            claim.get("source_ids").forEach(id -> cited.addAll(sourceWords.getOrDefault(id.asText(), Set.of())));
            Set<String> own = ClinicalSummaryGenerationService.words(claim.get("text").asText());
            own.removeAll(ClinicalSummaryGenerationService.COMMON_WORDS);
            if (!own.isEmpty() && !cited.isEmpty() && own.stream().noneMatch(cited::contains)) {
                dropped.add(claim.get("id").asText());
                notes.add("Statement " + claim.get("id").asText() + " shared no word with the notes it cited and was dropped: \""
                        + claim.get("text").asText() + "\"");
            }
        }
        ArrayNode claims = (ArrayNode) result.get("claims");
        for (int i = claims.size() - 1; i >= 0; i--) if (dropped.contains(claims.get(i).path("id").asText())) claims.remove(i);
        ArrayNode sections = JSON.createArrayNode();
        List<String> overviewExtra = new ArrayList<>();
        for (JsonNode section : result.get("sections")) {
            if (!section.isObject() || !section.path("claim_ids").isArray()) return new Tolerated((ObjectNode) generated, List.of());
            List<String> ids = new ArrayList<>();
            for (JsonNode member : section.get("claim_ids")) {
                List<String> candidates = new ArrayList<>(List.of(member.asText()));
                candidates.addAll(renamed.getOrDefault(member.asText(), List.of()));
                for (String candidate : candidates) if (!dropped.contains(candidate) && !ids.contains(candidate)) ids.add(candidate);
            }
            String id = section.path("id").asText();
            if (!SECTION_TITLES.containsKey(id)) {
                if (!ids.isEmpty()) {
                    notes.add("Section " + id + " is not one of the five clinical sections; its statements are under Clinical overview.");
                }
                for (String member : ids) if (!overviewExtra.contains(member)) overviewExtra.add(member);
                continue;
            }
            if (!ids.isEmpty()) {
                ArrayNode members = sections.addObject().put("id", id).put("title", SECTION_TITLES.get(id)).putArray("claim_ids");
                ids.forEach(members::add);
            }
        }
        if (!overviewExtra.isEmpty()) {
            ArrayNode members = null;
            for (JsonNode section : sections) if ("clinical_overview".equals(section.path("id").asText())) members = (ArrayNode) section.get("claim_ids");
            if (members == null) {
                members = sections.addObject().put("id", "clinical_overview").put("title", SECTION_TITLES.get("clinical_overview")).putArray("claim_ids");
            }
            Set<String> present = new LinkedHashSet<>();
            members.forEach(member -> present.add(member.asText()));
            for (String member : overviewExtra) if (present.add(member)) members.add(member);
        }
        result.set("sections", sections);
        return new Tolerated(result, notes);
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
