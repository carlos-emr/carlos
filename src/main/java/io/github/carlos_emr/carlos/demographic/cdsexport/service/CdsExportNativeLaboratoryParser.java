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
     */
    record Parsed(
            Message message,
            List<Observation> observations,
            List<Observation> documents,
            boolean blockedMessage,
            Raw raw) {
        Parsed {
            observations = List.copyOf(observations);
            documents = List.copyOf(documents);
        }

        @Override
        public String toString() {
            return "Parsed[protected]";
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

    Parsed parse(String encoded, Runnable checkpoint) throws IOException {
        try {
            if (encoded == null || encoded.isEmpty() || encoded.length() > 2_800_000)
                review("CDS_EXPORT_NATIVE_LAB_MESSAGE_CAPACITY");
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
            if (!text.startsWith("MSH") || !CdsExportNativeFieldAssessments.legal(text, 2_100_000))
                review("CDS_EXPORT_NATIVE_LAB_MESSAGE_ENCODING");
            // PipeParser never accepts XML, entities, DTDs or network schema locations.
            var parser = new PipeParser();
            // Default primitive rules trim/correct authored text. Retain source lexical values;
            // interpreted fields and all destination constraints are qualified by the mapper.
            parser.setValidationContext(new ValidationContextImpl());
            Message message = parser.parse(text);
            var segments = new ArrayList<Segment>();
            collect(message, segments, 0, checkpoint);
            if (segments.isEmpty() || !segments.getFirst().getName().equals("MSH")) invalid();
            var msh = segments.getFirst();
            // "ORU" without its R01 trigger never gets here: HAPI cannot tell the structure from
            // it, so CARLOS's own handlers (CMLHandler and the others parse with PipeParser) never
            // stored such a message, and parse() above refuses it the same way.
            if (!"ORU".equals(Terser.get(msh, 9, 0, 1, 1))
                    || !"R01".equals(Terser.get(msh, 9, 0, 2, 1)))
                review("CDS_EXPORT_NATIVE_LAB_MESSAGE_TYPE_REQUIRES_REVIEW");
            var observations = new ArrayList<Observation>();
            Segment order = null, result = null;
            List<Segment> orderComments = new ArrayList<>(), resultComments = new ArrayList<>();
            var documents = new ArrayList<Observation>();
            int orders = 0, results = 0, patients = 0;
            boolean blockedMessage = false;
            // An order without results (a pending, cancelled or fee-only test: the CML samples
            // end with one) is not a result: it adds no observation, and its OBR and NTE
            // segments travel in the complete source message.
            for (var segment : segments) {
                checkpoint.run();
                switch (segment.getName()) {
                    case "PID" -> {
                        if (++patients > 1) review("CDS_EXPORT_NATIVE_LAB_MULTIPLE_PATIENTS");
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
                        if (order == null) review("CDS_EXPORT_NATIVE_LAB_RESULT_WITHOUT_ORDER");
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
            // A message with no order, or for no patient, is not a laboratory report. One whose
            // orders all wait for results exports no result; it travels whole in its NewCategory
            // supporting record.
            if (orders == 0 || patients != 1) review("CDS_EXPORT_NATIVE_LAB_INCOMPLETE_MESSAGE");
            append(
                    observations,
                    documents,
                    order,
                    result,
                    orders,
                    results,
                    orderComments,
                    resultComments);
            if (observations.size() + documents.size() > 8192)
                review("CDS_EXPORT_NATIVE_LAB_RESULT_CAPACITY");
            return new Parsed(
                    message, observations, documents, blockedMessage, raw(text, segments));
        } catch (HL7Exception | IllegalArgumentException | CharacterCodingException failure) {
            review("CDS_EXPORT_NATIVE_LAB_MESSAGE_REQUIRES_REVIEW");
            return null;
        }
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
        if (repetitions > 8192 || output.size() + documents.size() > 8192 - repetitions)
            review("CDS_EXPORT_NATIVE_LAB_RESULT_CAPACITY");
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
        if (text.length() < 8) review("CDS_EXPORT_NATIVE_LAB_MESSAGE_ENCODING");
        var raw = new Raw(text.charAt(3), text.charAt(4), text.charAt(5), text.charAt(6), text.charAt(7));
        var lines = new HashMap<String, ArrayDeque<String[]>>();
        for (String line : text.split("\r\n|\r|\n")) {
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

    private static void collect(Group group, List<Segment> output, int depth, Runnable checkpoint)
            throws HL7Exception, IOException {
        if (depth > 32) review("CDS_EXPORT_NATIVE_LAB_MESSAGE_DEPTH");
        for (String name : group.getNames())
            for (var structure : group.getAll(name)) {
                checkpoint.run();
                if (structure instanceof Segment segment) output.add(segment);
                else if (structure instanceof Group child)
                    collect(child, output, depth + 1, checkpoint);
                else invalid();
                if (output.size() > 32768) review("CDS_EXPORT_NATIVE_LAB_SEGMENT_CAPACITY");
            }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_NATIVE_LAB_STRUCTURE_INVALID");
    }

    private static void review(String code) throws CdsExportMappingReviewException {
        throw new CdsExportMappingReviewException(code);
    }
}
