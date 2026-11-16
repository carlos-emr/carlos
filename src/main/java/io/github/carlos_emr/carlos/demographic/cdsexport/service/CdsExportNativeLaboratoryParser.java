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

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.*;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.validation.impl.ValidationContextImpl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** Structural membership is determined before mapping: every OBR, OBX and value repetition counts. */
final class CdsExportNativeLaboratoryParser {
    record Observation(
            String ordinal, Segment order, Segment result, int repetition, List<Segment> comments) {
        Observation {
            comments = List.copyOf(comments);
        }

        @Override
        public String toString() {
            return "Observation[protected]";
        }
    }

    /**
     * @param observations every result value, mapped to LaboratoryResults
     * @param documents every embedded document (OBX-2 {@code ED}, one per value repetition),
     *     exported as a Reports entry linked to the report by its accession number
     * @param blockedMessage the laboratory marked the whole message "do not disclose" with the MDS
     *     ZPD-3 = Y indicator, as MDSHandler.isTestResultBlocked reads it
     * @param selection which part of the stored message is this chart's ({@link Selection})
     */
    record Parsed(
            Message message,
            List<Observation> observations,
            List<Observation> documents,
            boolean blockedMessage,
            Raw raw,
            Selection selection) {
        Parsed {
            observations = List.copyOf(observations);
            documents = List.copyOf(documents);
            Objects.requireNonNull(selection);
        }

        Parsed(
                Message message,
                List<Observation> observations,
                List<Observation> documents,
                boolean blockedMessage,
                Raw raw) {
            this(message, observations, documents, blockedMessage, raw, Selection.WHOLE);
        }

        @Override
        public String toString() {
            return "Parsed[protected]";
        }
    }

    /**
     * The chart a stored message is read for, as CARLOS stores its identity ({@code
     * demographic.hin}, {@code last_name}, {@code first_name} and the three birth columns).
     */
    record Chart(
            String healthNumber,
            String familyName,
            String givenName,
            String birthYear,
            String birthMonth,
            String birthDay) {
        /** No identity: no part of a message that names several patients is this chart's. */
        static final Chart UNKNOWN = new Chart(null, null, null, null, null, null);

        static Chart of(Map<String, String> demographic) {
            return new Chart(
                    demographic.get("hin"),
                    demographic.get("last_name"),
                    demographic.get("first_name"),
                    demographic.get("year_of_birth"),
                    demographic.get("month_of_birth"),
                    demographic.get("date_of_birth"));
        }

        @Override
        public String toString() {
            return "Chart[protected]";
        }
    }

    /** Which part of the stored message the export carries. */
    enum Part {
        /** The message names one patient (or none): it is exported whole, as stored. */
        WHOLE,
        /**
         * The message names several patients and some of its PID groups are this chart's: the
         * MSH segment and those groups are exported, as the elements and as the carried message,
         * never another patient's part (owner decision 2026-11-14).
         */
        THIS_PATIENT,
        /**
         * The message names several patients and none of them is this chart's: no part of it is
         * exported, and the ReadMe names the report.
         */
        NONE
    }

    /**
     * @param part which part is exported
     * @param groups the message's PID groups (a PID segment and every segment up to the next
     *     PID); 0 or 1 for a message exported whole
     * @param exported the ordinals (from 1) of the PID groups exported, in message order
     * @param carried for {@link Part#THIS_PATIENT}, the exported part as a stored message (UTF-8,
     *     Base64, as {@code hl7TextMessage.message} holds a message): the original text of the
     *     MSH segment and of each exported group, byte for byte; null otherwise
     */
    record Selection(Part part, int groups, List<Integer> exported, String carried) {
        static final Selection WHOLE = new Selection(Part.WHOLE, 0, List.of(), null);

        Selection {
            Objects.requireNonNull(part);
            exported = List.copyOf(exported);
            if ((part == Part.THIS_PATIENT) != (carried != null)
                    || part == Part.THIS_PATIENT && exported.isEmpty()
                    || part == Part.NONE && !exported.isEmpty())
                throw new IllegalArgumentException("CDS_EXPORT_NATIVE_LAB_SELECTION_INVALID");
        }

        /**
         * Whether the values the upload stored for the report ({@code hl7TextInfo}) are this
         * chart's: the upload's handler read them from the message's first patient, so they are
         * this chart's when the message names one patient or its first PID group is exported.
         */
        boolean storedInformation() {
            return part == Part.WHOLE || part == Part.THIS_PATIENT && exported.getFirst() == 1;
        }

        @Override
        public String toString() {
            return "Selection[" + part + "]";
        }
    }

    /**
     * The laboratory's own text of every OBR, OBX and NTE segment. HAPI keeps only one value for
     * a primitive's second subcomponent and second component (they share an extra-component
     * slot), so text a laboratory left unescaped ("A & B ^ C") cannot be rebuilt from the parsed
     * model. Each raw segment is aligned with HAPI's segment of the same name in order and the
     * pair is verified field by field before it is used.
     */
    static final class Raw {
        final char field, component, repetition, escape, subcomponent;
        private final Map<Segment, String[]> fields = new IdentityHashMap<>();
        private final List<Segment> orders = new ArrayList<>();

        private Raw(char field, char component, char repetition, char escape, char subcomponent) {
            this.field = field;
            this.component = component;
            this.repetition = repetition;
            this.escape = escape;
            this.subcomponent = subcomponent;
        }

        /** Every OBR segment in message order, including orders without results. */
        List<Segment> orders() {
            return Collections.unmodifiableList(orders);
        }

        /** The raw repetitions of one field, or an empty list when the field is absent. */
        List<String> repetitions(Segment segment, int field) throws IOException {
            String[] raw = fields.get(segment);
            if (raw == null) invalid();
            if (field >= raw.length || raw[field].isEmpty()) return List.of();
            return split(raw[field], repetition);
        }

        static List<String> split(String text, char separator) {
            var parts = new ArrayList<String>();
            int start = 0;
            for (int i = 0; i < text.length(); i++)
                if (text.charAt(i) == separator) {
                    parts.add(text.substring(start, i));
                    start = i + 1;
                }
            parts.add(text.substring(start));
            return parts;
        }

        @Override
        public String toString() {
            return "Raw[protected]";
        }
    }

    /** {@link #parse(String, Chart, Runnable)} for no known chart. */
    Parsed parse(String encoded, Runnable checkpoint) throws IOException {
        return parse(encoded, Chart.UNKNOWN, checkpoint);
    }

    /**
     * Parses a stored message for one chart. A message that names more than one patient (several
     * PID segments) is first reduced to this chart's part ({@link #select}): the MSH segment and
     * the PID groups whose identity matches the chart; nothing of another patient's part is read
     * further. A message with one PID is read whole, as before.
     */
    Parsed parse(String encoded, Chart chart, Runnable checkpoint) throws IOException {
        Objects.requireNonNull(chart);
        return parse(encoded, () -> chart, checkpoint);
    }

    /** The chart a message is read for, asked only when the message names several patients. */
    @FunctionalInterface
    interface Charts {
        Chart chart() throws IOException;
    }

    /** {@link #parse(String, Chart, Runnable)}, asking for the chart only when it is needed. */
    // FindSecBugs IMPROPER_UNICODE: Matches the one-letter HL7 do-not-disclose indicator Y or y; only y and Y fold to it, and a match blocks the message, so folding can only withhold.
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "matches the one-letter HL7 do-not-disclose indicator Y or y; only y and Y fold to it, and a match blocks the message, so folding can only withhold")
    Parsed parse(String encoded, Charts charts, Runnable checkpoint) throws IOException {
        Objects.requireNonNull(charts);
        try {
            // No size is refused: a report with embedded documents is as long as the laboratory
            // made it, and one message is parsed at a time.
            if (encoded == null || encoded.isEmpty())
                review("CDS_EXPORT_NATIVE_LAB_MESSAGE_ENCODING");
            byte[] bytes = Base64.getDecoder().decode(encoded.replaceAll("[\\t\\r\\n ]", ""));
            String text;
            try {
                text =
                        StandardCharsets.UTF_8
                                .newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(bytes))
                                .toString();
            } finally {
                Arrays.fill(bytes, (byte) 0);
            }
            // Line breaks or spaces before the first segment belong to no field: an upload that
            // took the message from an XML element's text keeps the line break after the opening
            // tag, and DefaultGenericHandler, which parsed it for the chart, skips it the same way.
            text = text.stripLeading();
            // Every handler parses what MessageUploader stored, and Factory.getHandler retries a
            // message its typed handler refuses with DefaultGenericHandler; one that neither
            // parses was never stored, so these two codes guard rows written outside CARLOS.
            if (!text.startsWith("MSH") || text.length() < 8)
                review("CDS_EXPORT_NATIVE_LAB_MESSAGE_ENCODING");
            // A message that names several patients: only this chart's part is read from here on
            // (owner decision 2026-11-14), and the carried message is that part.
            var reading = select(text, charts, checkpoint);
            var selection = reading.selection();
            text = reading.text();
            // A stored message may hold a character XML 1.0 cannot carry (a control character a
            // sending system left in a field). It is not an HL7 delimiter, so the message parses
            // the same with U+FFFD in its place; every element then shows U+FFFD there, and the
            // stored message travels exactly in the report's CARLOS.NativeLaboratory.v1 record.
            text = segments(CdsExportXmlText.element(text));
            // PipeParser never accepts XML, entities, DTDs or network schema locations.
            var parser = new PipeParser();
            // Default primitive rules trim/correct authored text. Retain source lexical values;
            // interpreted fields and all destination constraints are qualified by the mapper.
            parser.setValidationContext(new ValidationContextImpl());
            Message message = parser.parse(text);
            var segments = new ArrayList<Segment>();
            collect(message, segments, checkpoint);
            if (segments.isEmpty() || !segments.getFirst().getName().equals("MSH")) invalid();
            // Every message CARLOS stored as a laboratory report is read as one, whatever MSH-9
            // names: its OBR and OBX segments are its orders and results, as the chart's lab
            // display reads them. The handlers parse some feeds under another type (SpireHandler
            // rewrites MDM^R01 to ORU^R01, IHAPOIHandler an O01 trigger to R01) but store the
            // message as received; HAPI parses a structure it has no class for as a generic
            // message, whose segments are read here in their order. "ORU" without a trigger
            // cannot be parsed by HAPI at all, so no handler stored such a message.
            var observations = new ArrayList<Observation>();
            Segment order = null, result = null;
            List<Segment> orderComments = new ArrayList<>(), resultComments = new ArrayList<>();
            var documents = new ArrayList<Observation>();
            int orders = 0, results = 0;
            boolean blockedMessage = false;
            // An order without results (a pending, cancelled or fee-only test: the CML samples
            // end with one) is not a result: it adds no observation, and its OBR and NTE
            // segments travel in the complete source message. A result the laboratory sent
            // before any order is still a result, of no order (ordinal 0:n): its order fields
            // are empty.
            for (var segment : segments) {
                checkpoint.run();
                switch (segment.getName()) {
                    case "PID" -> {
                        // A message that named several patients was reduced to this chart's PID
                        // groups before it was parsed (select), so every PID left is this
                        // chart's; the segment stays in the complete supporting context.
                    }
                    case "ORC" -> {
                        append(
                                observations,
                                documents,
                                order,
                                result,
                                orders,
                                results,
                                orderComments,
                                resultComments);
                        order = null;
                        result = null;
                        orderComments = new ArrayList<>();
                        resultComments = new ArrayList<>();
                    }
                    case "OBR" -> {
                        append(
                                observations,
                                documents,
                                order,
                                result,
                                orders,
                                results,
                                orderComments,
                                resultComments);
                        order = segment;
                        result = null;
                        orders++;
                        results = 0;
                        orderComments = new ArrayList<>();
                        resultComments = new ArrayList<>();
                    }
                    case "OBX" -> {
                        append(
                                observations,
                                documents,
                                order,
                                result,
                                orders,
                                results,
                                orderComments,
                                resultComments);
                        result = segment;
                        results++;
                        resultComments = new ArrayList<>();
                    }
                    case "NTE" -> {
                        if (result != null) resultComments.add(segment);
                        else if (order != null) orderComments.add(segment);
                        // Message-level comments remain in the complete supporting context.
                    }
                    case "ZPD" -> {
                        // MDS "do not disclose" indicator; the segment also stays in the
                        // complete supporting context.
                        if ("Y".equalsIgnoreCase(Terser.get(segment, 3, 0, 1, 1)))
                            blockedMessage = true;
                    }
                    default -> {
                        /* Every other segment remains in the exact supporting context. */
                    }
                }
            }
            // A message with no order exports no result, like one whose orders all wait for
            // results, and one with no PID is the report the chart routed to this patient: either
            // travels whole in its NewCategory supporting record.
            append(
                    observations,
                    documents,
                    order,
                    result,
                    orders,
                    results,
                    orderComments,
                    resultComments);
            return new Parsed(
                    message,
                    observations,
                    documents,
                    blockedMessage,
                    raw(text, segments),
                    selection);
        } catch (HL7Exception | IllegalArgumentException | CharacterCodingException failure) {
            review("CDS_EXPORT_NATIVE_LAB_MESSAGE_REQUIRES_REVIEW");
            return null;
        }
    }

    /** The part of a stored message that is read, and the text of it. */
    record Reading(Selection selection, String text) {
        @Override
        public String toString() {
            return "Reading[protected]";
        }
    }

    /** One segment of the stored text, as the lab handlers read it, and where its text lies. */
    private record Unit(String name, int start, int end) {}

    private static final java.util.regex.Pattern LINE_BREAK =
            java.util.regex.Pattern.compile("\r\n|\r|\n");

    /**
     * This chart's part of a stored message (owner decision 2026-11-14). A message with no more
     * than one PID segment is read whole. A message that names several patients is cut into PID
     * groups (a PID segment and every segment up to the next PID, line breaks read as {@link
     * #segments} reads them); a group is this chart's when its PID identity matches the chart
     * ({@link #matches}). What is read is the MSH segment followed by those groups, in message
     * order, each as its original text; when no group matches, the MSH segment alone, so that no
     * result and no document of another patient is read.
     *
     * @param text the stored message, starting with {@code MSH} and its five delimiters
     */
    static Reading select(String text, Charts charts, Runnable checkpoint) throws IOException {
        var units = units(text, checkpoint);
        var patients = new ArrayList<Integer>();
        for (int i = 0; i < units.size(); i++) if (units.get(i).name().equals("PID")) patients.add(i);
        if (patients.size() <= 1) return new Reading(Selection.WHOLE, text);
        var chart = Objects.requireNonNull(charts.chart());
        var raw = new Raw(text.charAt(3), text.charAt(4), text.charAt(5), text.charAt(6), text.charAt(7));
        var kept = new StringBuilder(text.length());
        kept.append(text, units.getFirst().start(), units.getFirst().end());
        var exported = new ArrayList<Integer>();
        for (int group = 0; group < patients.size(); group++) {
            checkpoint.run();
            var pid = units.get(patients.get(group));
            int end =
                    group + 1 < patients.size()
                            ? units.get(patients.get(group + 1)).start()
                            : text.length();
            if (matches(identity(segment(text, pid, raw.field), raw), chart)) {
                exported.add(group + 1);
                kept.append(text, pid.start(), end);
            }
        }
        if (exported.isEmpty())
            return new Reading(
                    new Selection(Part.NONE, patients.size(), List.of(), null),
                    kept.toString());
        String part = kept.toString();
        return new Reading(
                new Selection(
                        Part.THIS_PATIENT,
                        patients.size(),
                        exported,
                        Base64.getEncoder().encodeToString(part.getBytes(StandardCharsets.UTF_8))),
                part);
    }

    /**
     * The segments of the text with the slices that hold them: every line break ends a line, a
     * line that is empty or whose fourth character is not the field separator belongs to the
     * segment before it (as {@link #segments} joins it), and whitespace that starts a line is
     * not part of the segment's name.
     */
    private static List<Unit> units(String text, Runnable checkpoint) {
        char field = text.charAt(3);
        var units = new ArrayList<Unit>();
        var breaks = LINE_BREAK.matcher(text);
        int start = 0;
        while (start < text.length()) {
            checkpoint.run();
            int end, next;
            if (breaks.find(start)) {
                end = breaks.start();
                next = breaks.end();
            } else end = next = text.length();
            String line = text.substring(start, end).stripLeading();
            boolean continues =
                    line.isEmpty() || line.length() >= 4 && line.charAt(3) != field;
            if (continues && !units.isEmpty()) {
                var last = units.removeLast();
                units.add(new Unit(last.name(), last.start(), next));
            } else
                units.add(
                        new Unit(line.length() >= 3 ? line.substring(0, 3) : line, start, next));
            start = next;
        }
        return units;
    }

    /** A segment's text as {@link #segments} reads it: its lines joined by line feeds. */
    private static String segment(String text, Unit unit, char field) {
        var lines = LINE_BREAK.split(text.substring(unit.start(), unit.end()));
        var joined = new StringBuilder();
        for (String line : lines) {
            line = line.stripLeading();
            if (line.isEmpty()) continue;
            if (!joined.isEmpty()) joined.append('\n');
            joined.append(line);
        }
        return joined.toString();
    }

    /**
     * The identity one PID segment states: its health numbers (the first component of every
     * repetition of PID-2, PID-3, PID-4 and PID-19, the fields CARLOS's lab handlers read a health
     * number from, each as the HL7 matcher reads it, {@code LabRoutingLookupDao.hl7HealthNumber}),
     * the family and given names (PID-5) and the birth date (the first eight digits of PID-7).
     */
    record Identity(
            List<String> healthNumbers,
            String familyName,
            String givenName,
            String birthYear,
            String birthMonth,
            String birthDay) {
        Identity {
            healthNumbers = List.copyOf(healthNumbers);
        }

        @Override
        public String toString() {
            return "Identity[protected]";
        }
    }

    static Identity identity(String pid, Raw raw) {
        var fields = Raw.split(pid, raw.field);
        var numbers = new ArrayList<String>();
        for (int field : new int[] {2, 3, 4, 19}) {
            if (field >= fields.size() || fields.get(field).isEmpty()) continue;
            for (String repetition : Raw.split(fields.get(field), raw.repetition)) {
                String number =
                        io.github.carlos_emr.carlos.commn.dao.LabRoutingLookupDao.hl7HealthNumber(
                                component(repetition, 1, raw));
                if (number != null && !number.isBlank()) numbers.add(number);
            }
        }
        String name = fields.size() > 5 ? Raw.split(fields.get(5), raw.repetition).getFirst() : "";
        String birth = fields.size() > 7 ? component(Raw.split(fields.get(7), raw.repetition).getFirst(), 1, raw) : "";
        boolean dated = birth.length() >= 8 && birth.substring(0, 8).chars().allMatch(Character::isDigit);
        return new Identity(
                numbers,
                component(name, 1, raw),
                component(name, 2, raw),
                dated ? birth.substring(0, 4) : null,
                dated ? birth.substring(4, 6) : null,
                dated ? birth.substring(6, 8) : null);
    }

    /**
     * One component's first subcomponent, its delimiter escapes decoded as HAPI decodes them and
     * its surrounding spaces removed, as the lab handlers read a value ({@code getString}).
     */
    private static String component(String value, int component, Raw raw) {
        var components = Raw.split(value, raw.component);
        if (component > components.size()) return "";
        String first = Raw.split(components.get(component - 1), raw.subcomponent).getFirst();
        return unescapeDelimiters(first, raw).trim();
    }

    /**
     * Whether a PID group is this chart's, by the HL7 matcher's own comparison ({@code
     * LabRoutingLookupDao.findHl7Patients}). A group that states a health number matches by it
     * alone: one of its numbers, with the version ignored ({@code hl7HealthNumber}), equals the
     * chart's {@code hin}, letter case and trailing spaces aside, as the column's collation
     * compares them. A group that states none matches by family name, given name and birth date,
     * all three stated: the names equal the chart's ignoring letter case and trailing spaces and
     * the birth year, month and day equal the stored columns exactly. The database collation also
     * ignores accents; this comparison does not, so a name that differs only by an accent is not
     * taken for this chart's (its report is then named in the ReadMe instead).
     */
    // FindSecBugs IMPROPER_UNICODE: compares a stored health number and names with what a laboratory sent, ignoring letter case as the HL7 matcher's column collation does; equalsIgnoreCase folds case only, never accents, so it never takes another patient's group the database would refuse.
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "compares a stored health number and names with what a laboratory sent, ignoring letter case as the HL7 matcher's column collation does; equalsIgnoreCase folds case only, never accents, so it never takes another patient's group the database would refuse")
    static boolean matches(Identity group, Chart chart) {
        if (!group.healthNumbers().isEmpty()) {
            String stored = chart.healthNumber() == null ? "" : chart.healthNumber().stripTrailing();
            if (stored.isEmpty()) return false;
            for (String number : group.healthNumbers())
                if (number.stripTrailing().equalsIgnoreCase(stored)) return true;
            return false;
        }
        if (group.familyName().isEmpty() || group.givenName().isEmpty() || group.birthYear() == null)
            return false;
        return chart.familyName() != null
                && chart.givenName() != null
                && group.familyName().equalsIgnoreCase(chart.familyName().stripTrailing())
                && group.givenName().equalsIgnoreCase(chart.givenName().stripTrailing())
                && group.birthYear().equals(chart.birthYear())
                && group.birthMonth().equals(chart.birthMonth())
                && group.birthDay().equals(chart.birthDay());
    }

    private static void append(
            List<Observation> output,
            List<Observation> documents,
            Segment order,
            Segment result,
            int orderOrdinal,
            int resultOrdinal,
            List<Segment> orderComments,
            List<Segment> resultComments)
            throws HL7Exception, IOException {
        if (result == null) return;
        var comments = new ArrayList<>(orderComments);
        comments.addAll(resultComments);
        // NM, coded and date repetitions are separate values; TX and FT repetitions are the
        // continuation lines of one narrative, which the mapper joins into one result. Each
        // repetition of an ED value is one embedded document, exported as a Reports entry.
        String type = Terser.get(result, 2, 0, 1, 1);
        int repetitions =
                "TX".equals(type) || "FT".equals(type) ? 1 : Math.max(1, result.getField(5).length);
        var target = "ED".equals(type) ? documents : output;
        for (int i = 0; i < repetitions; i++)
            target.add(
                    new Observation(
                            orderOrdinal + ":" + resultOrdinal + ":" + i,
                            order,
                            result,
                            i,
                            comments));
    }

    private static Raw raw(String text, List<Segment> segments) throws IOException, HL7Exception {
        var raw = new Raw(text.charAt(3), text.charAt(4), text.charAt(5), text.charAt(6), text.charAt(7));
        var lines = new HashMap<String, ArrayDeque<String[]>>();
        for (String line : text.split("\r")) {
            if (line.length() < 3) continue;
            String name = line.substring(0, 3);
            if (Set.of("OBR", "OBX", "NTE").contains(name))
                lines.computeIfAbsent(name, ignored -> new ArrayDeque<>())
                        .add(Raw.split(line, raw.field).toArray(String[]::new));
        }
        for (var segment : segments) {
            String name = segment.getName();
            if (!Set.of("OBR", "OBX", "NTE").contains(name)) continue;
            var queue = lines.get(name);
            if (queue == null || queue.isEmpty()) review("CDS_EXPORT_NATIVE_LAB_SEGMENT_ALIGNMENT");
            String[] fields = queue.poll();
            // HAPI never loses the start of a field: its first component is the raw first
            // subcomponent (composites) or the whole raw first component, with an unescaped
            // subcomponent separator kept literally (primitives). Every field must agree before
            // the raw text of this segment is trusted.
            for (int f = 1; f < Math.max(fields.length, segment.numFields() + 1); f++) {
                // getField(int) never creates a repetition; Terser.get on an absent one would.
                String expected =
                        f > segment.numFields() || segment.getField(f).length == 0
                                ? null
                                : Terser.get(segment, f, 0, 1, 1);
                if (expected != null && expected.isEmpty()) expected = null;
                String whole = null, first = null;
                if (f < fields.length && !fields[f].isEmpty()) {
                    String component =
                            Raw.split(Raw.split(fields[f], raw.repetition).getFirst(), raw.component)
                                    .getFirst();
                    String sub = Raw.split(component, raw.subcomponent).getFirst();
                    whole = component.isEmpty() ? null : unescapeDelimiters(component, raw);
                    first = sub.isEmpty() ? null : unescapeDelimiters(sub, raw);
                }
                if (!Objects.equals(expected, first) && !Objects.equals(expected, whole))
                    review("CDS_EXPORT_NATIVE_LAB_SEGMENT_ALIGNMENT");
            }
            raw.fields.put(segment, fields);
            if (name.equals("OBR")) raw.orders.add(segment);
        }
        for (var queue : lines.values())
            if (!queue.isEmpty()) review("CDS_EXPORT_NATIVE_LAB_SEGMENT_ALIGNMENT");
        return raw;
    }

    /**
     * The stored message as CARLOS's own lab handlers read it, one segment per CR (HAPI's segment
     * separator). HAPI ends a segment only at CR, but a report uploaded inside an XML file
     * (Excelleris ON and PATHL7 wrap each message in CDATA) is stored as the XML parser returns
     * it, with LF between segments; ExcellerisOntarioHandler, PATHL7Handler, CMLHandler and the
     * others therefore parse {@code hl7Body.replaceAll("\n", "\r\n")}. Read as stored, such a
     * message is one long MSH segment with no PID or OBR. Every line break is therefore a segment
     * break, with two exceptions DefaultGenericHandler makes, which Factory.getHandler uses for a
     * message its typed handler refuses: whitespace that starts a line is dropped (HAPI drops it
     * too), and a line whose fourth character is not the field separator continues the segment
     * before it ("Labs often insert breaks into comments"), so the text keeps that line break
     * literally. HL7 v2 otherwise never carries a literal line break inside a field (it is escaped
     * as {@code \.br\} or {@code \X0A\}), so the conversion changes no other value. Lines left
     * empty are dropped. {@code text} starts with {@code MSH} and its field separator.
     */
    static String segments(String text) {
        char field = text.charAt(3);
        var out = new StringBuilder(text.length());
        for (String line : text.split("\r\n|\r|\n")) {
            line = line.stripLeading();
            if (line.isEmpty()) continue;
            if (line.length() >= 4 && line.charAt(3) != field && !out.isEmpty()) {
                out.setLength(out.length() - 1);
                out.append('\n').append(line).append('\r');
            } else out.append(line).append('\r');
        }
        return out.toString();
    }

    /** HAPI's own unescaping: only the delimiter escapes; formatting escapes stay as written. */
    static String unescapeDelimiters(String text, Raw raw) {
        var out = new StringBuilder(text.length());
        int index = 0;
        while (index < text.length()) {
            int end = text.charAt(index) == raw.escape ? text.indexOf(raw.escape, index + 1) : -1;
            if (end < 0) {
                out.append(text.charAt(index++));
                continue;
            }
            switch (text.substring(index + 1, end)) {
                case "F" -> out.append(raw.field);
                case "S" -> out.append(raw.component);
                case "T" -> out.append(raw.subcomponent);
                case "R" -> out.append(raw.repetition);
                case "E" -> out.append(raw.escape);
                default -> out.append(text, index, end + 1);
            }
            index = end + 1;
        }
        return out.toString();
    }

    private static void collect(Group group, List<Segment> output, Runnable checkpoint)
            throws HL7Exception, IOException {
        for (String name : group.getNames())
            for (var structure : group.getAll(name)) {
                checkpoint.run();
                if (structure instanceof Segment segment) output.add(segment);
                else if (structure instanceof Group child)
                    collect(child, output, checkpoint);
                else invalid();
            }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_NATIVE_LAB_STRUCTURE_INVALID");
    }

    private static void review(String code) throws CdsExportMappingReviewException {
        throw new CdsExportMappingReviewException(code);
    }
}
