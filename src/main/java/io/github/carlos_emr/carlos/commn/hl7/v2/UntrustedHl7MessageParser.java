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

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.PipeParser;

/**
 * Parses an HL7 v2 message that may arrive pipe (ER7) or XML encoded, such as a lab upload
 * (issue #5414).
 *
 * <p>It replaces HAPI's {@code GenericParser}, which picks the same two encodings but always
 * reads XML with HAPI's own parser. Pipe messages go to HAPI's {@link PipeParser} (as
 * {@link LegacyHl7PipeParser}, which keeps HAPI 1.0.1's names for the segments of a generic
 * message); XML messages go to {@link HardenedHl7XmlParser}; anything else is refused with an
 * {@link HL7Exception}, as {@code GenericParser} did. Both parsers share one
 * {@link Hl7Parsers#newParsingContext()} context, so values are trimmed, unescaped and left
 * unchecked exactly as before the HAPI upgrade.</p>
 *
 * <p>Instances are cheap and not thread-safe; create one per message.</p>
 *
 * @since 2026-10-29
 */
public final class UntrustedHl7MessageParser {

    private final PipeParser pipeParser;
    private final HardenedHl7XmlParser xmlParser;

    public UntrustedHl7MessageParser() {
        HapiContext context = Hl7Parsers.newParsingContext();
        this.pipeParser = new LegacyHl7PipeParser(context);
        this.xmlParser = new HardenedHl7XmlParser(context);
    }

    /**
     * Parses {@code message} with the parser that matches its encoding.
     *
     * @param message the complete message text
     * @return the parsed message
     * @throws HL7Exception if the encoding is neither pipe nor XML, or the message cannot be parsed
     */
    public Message parse(String message) throws HL7Exception {
        if (message == null) {
            throw new HL7Exception("HL7 message is empty");
        }
        if (pipeParser.getEncoding(message) != null) {
            return pipeParser.parse(message);
        }
        if (xmlParser.getEncoding(message) != null) {
            return xmlParser.parse(message);
        }
        throw new HL7Exception("HL7 message is neither pipe (ER7) nor XML encoded");
    }
}
