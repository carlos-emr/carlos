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
     * @param blockedMessage the laboratory marked the whole message "do not disclose" with the MDS
     *     ZPD-3 = Y indicator, as MDSHandler.isTestResultBlocked reads it
     */
    record Parsed(Message message, List<Observation> observations, boolean blockedMessage) {
        Parsed {
            observations = List.copyOf(observations);
        }

        @Override
        public String toString() {
            return "Parsed[protected]";
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
            if (!"ORU".equals(Terser.get(msh, 9, 0, 1, 1))
                    || !"R01".equals(Terser.get(msh, 9, 0, 2, 1)))
                review("CDS_EXPORT_NATIVE_LAB_MESSAGE_TYPE_REQUIRES_REVIEW");
            var observations = new ArrayList<Observation>();
            Segment order = null, result = null;
            List<Segment> orderComments = new ArrayList<>(), resultComments = new ArrayList<>();
            int orders = 0, results = 0, patients = 0;
            boolean orderHasResult = false, blockedMessage = false;
            for (var segment : segments) {
                checkpoint.run();
                switch (segment.getName()) {
                    case "PID" -> {
                        if (++patients > 1) review("CDS_EXPORT_NATIVE_LAB_MULTIPLE_PATIENTS");
                    }
                    case "ORC" -> {
                        if (order != null && !orderHasResult)
                            review("CDS_EXPORT_NATIVE_LAB_ORDER_WITHOUT_RESULT");
                        append(
                                observations,
                                order,
                                result,
                                orders,
                                results,
                                orderComments,
                                resultComments);
                        order = null;
                        result = null;
                        orderHasResult = false;
                        orderComments = new ArrayList<>();
                        resultComments = new ArrayList<>();
                    }
                    case "OBR" -> {
                        if (order != null && !orderHasResult)
                            review("CDS_EXPORT_NATIVE_LAB_ORDER_WITHOUT_RESULT");
                        append(
                                observations,
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
                        orderHasResult = false;
                        orderComments = new ArrayList<>();
                        resultComments = new ArrayList<>();
                    }
                    case "OBX" -> {
                        if (order == null) review("CDS_EXPORT_NATIVE_LAB_RESULT_WITHOUT_ORDER");
                        append(
                                observations,
                                order,
                                result,
                                orders,
                                results,
                                orderComments,
                                resultComments);
                        result = segment;
                        results++;
                        orderHasResult = true;
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
            if (order == null || !orderHasResult || patients != 1)
                review("CDS_EXPORT_NATIVE_LAB_INCOMPLETE_MESSAGE");
            append(observations, order, result, orders, results, orderComments, resultComments);
            if (observations.isEmpty() || observations.size() > 8192)
                review("CDS_EXPORT_NATIVE_LAB_RESULT_CAPACITY");
            return new Parsed(message, observations, blockedMessage);
        } catch (HL7Exception | IllegalArgumentException | CharacterCodingException failure) {
            review("CDS_EXPORT_NATIVE_LAB_MESSAGE_REQUIRES_REVIEW");
            return null;
        }
    }

    private static void append(
            List<Observation> output,
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
        int repetitions = Math.max(1, result.getField(5).length);
        if (repetitions > 8192 || output.size() > 8192 - repetitions)
            review("CDS_EXPORT_NATIVE_LAB_RESULT_CAPACITY");
        for (int i = 0; i < repetitions; i++)
            output.add(
                    new Observation(
                            orderOrdinal + ":" + resultOrdinal + ":" + i,
                            order,
                            result,
                            i,
                            comments));
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
