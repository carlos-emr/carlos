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
package io.github.carlos_emr.carlos.commn.hl7.v2;

import java.util.HashSet;
import java.util.Set;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.GenericMessage;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.parser.EncodingCharacters;
import ca.uhn.hl7v2.parser.PipeParser;

/**
 * HAPI's pipe parser, naming the segments of a generic message as HAPI 1.0.1 did (issue #5414).
 *
 * <p>A message whose type the structures jar does not define is parsed into HAPI's generic model,
 * where every segment hangs off the root under a name. Several lab handlers rely on this on
 * purpose (IHA and the generic handler rewrite ORU^R01 to ORU^O01 to get it) and walk the root by
 * name: {@code OBR}, {@code OBR2}, {@code OBR3}, ... The first run of a segment type is one name
 * with a repetition per segment in both versions. A later run differs: HAPI 1.0.1 gave every
 * segment of it its own number, HAPI 2.x folds the run into repetitions of one number. Two
 * adjacent OBRs after the first therefore became one {@code OBR2} with two repetitions, and the
 * handlers, which count names, lost a whole request and shifted every later one.</p>
 *
 * <p>For generic messages this parser keeps 1.0.1's names: the first run of a type stays one
 * name, every later segment of that type gets the next number. Each segment's text is still parsed
 * by HAPI 2.x's own segment parser, exactly as {@link PipeParser} would, so values, escaping and
 * repetitions within fields are unchanged; only the names the segments sit under differ. Messages
 * of a defined type are parsed by {@link PipeParser} unchanged.</p>
 *
 * @since 2026-10-29
 */
final class LegacyHl7PipeParser extends PipeParser {

    private static final String SEGMENT_DELIMITER = "\r";

    LegacyHl7PipeParser(HapiContext context) {
        super(context);
    }

    @Override
    public void parse(Message message, String string) throws HL7Exception {
        if (message instanceof GenericMessage) {
            parseGeneric(message, string);
        } else {
            super.parse(message, string);
        }
    }

    /**
     * Mirrors {@link PipeParser#parse(Message, String)} (segment splitting, leading whitespace,
     * names, the repetition number handed to the segment parser), placing each segment under its
     * HAPI 1.0.1 name instead of where HAPI 2.x's message iterator would put it.
     */
    private void parseGeneric(Message message, String string) throws HL7Exception {
        message.setParser(this);
        String[] segments = split(string, SEGMENT_DELIMITER);
        // Same checks as PipeParser, without repeating the message text (patient data) in the
        // exception message.
        if (segments.length == 0 || segments[0] == null || segments[0].length() < 4 || string.length() < 9) {
            throw new HL7Exception("Invalid message content: the message does not start with a complete MSH segment");
        }
        EncodingCharacters encodingCharacters = new EncodingCharacters(string.charAt(3), string.substring(4, 9));

        LegacyNames names = new LegacyNames(message);
        char delimiter = '|';
        String previousName = null;
        int repetition = 1;
        for (int i = 0; i < segments.length; i++) {
            String segment = withoutLeadingWhitespace(segments[i]);
            if (segment == null || segment.length() < 3) {
                continue;
            }
            if (i == 0) {
                delimiter = segment.charAt(3);
            }
            String name = segmentName(segment, i == 0, delimiter);
            repetition = name.equals(previousName) ? repetition + 1 : 1;
            previousName = name;
            parse(names.next(name), segment, encodingCharacters, repetition);
        }
    }

    private static String withoutLeadingWhitespace(String segment) {
        if (segment != null && !segment.isEmpty() && Character.isWhitespace(segment.charAt(0))) {
            return stripLeadingWhitespace(segment);
        }
        return segment;
    }

    /**
     * The first segment (MSH) is named by its first three characters, any other by its text up to
     * the field delimiter.
     */
    private static String segmentName(String segment, boolean first, char delimiter) {
        if (first) {
            return segment.substring(0, 3);
        }
        int end = segment.indexOf(delimiter);
        return end >= 0 ? segment.substring(0, end) : segment;
    }

    /**
     * HAPI 1.0.1's naming: the first run of a segment type is one name with a repetition per
     * segment; every later segment of that type, adjacent or not, gets the next numbered name.
     */
    private static final class LegacyNames {
        private final Message message;
        private final Set<String> typesSeen = new HashSet<>();
        /** The type whose first run is still open, or null once any other type has followed it. */
        private String openFirstRun;

        LegacyNames(Message message) {
            this.message = message;
        }

        Segment next(String type) throws HL7Exception {
            String name;
            int repetition;
            if (typesSeen.add(type)) {
                // GenericMessage starts with an empty MSH; the first MSH fills it.
                name = "MSH".equals(type) && message.getAll("MSH").length == 0
                        ? "MSH"
                        : message.addNonstandardSegment(type);
                repetition = 0;
                openFirstRun = type;
            } else if (type.equals(openFirstRun)) {
                name = type;
                repetition = message.getAll(type).length;
            } else {
                name = message.addNonstandardSegment(type);
                repetition = 0;
                openFirstRun = null;
            }
            return (Segment) message.get(name, repetition);
        }
    }
}
