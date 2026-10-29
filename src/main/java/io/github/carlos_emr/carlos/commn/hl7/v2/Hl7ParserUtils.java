/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada

 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.commn.hl7.v2;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;

/**
 * Shared HL7 parser utilities used across modules that need HL7 v2 message encoding/decoding.
 *
 * <p>Extracted from the removed Oscar-to-Oscar communication package to support
 * features like prescription QR code generation that use HL7 message formatting.
 *
 * @since 2026-04-03
 */
public final class Hl7ParserUtils {

    private Hl7ParserUtils() {
        // utility class
    }

    /**
     * Encodes a message CARLOS built, such as the prescription QR code, with no validation and
     * HAPI 1.0.1 escaping ({@link Hl7Parsers#newEncodingPipeParser()}). Each call uses its own
     * parser: HAPI does not document its parsers as safe to share between threads, and QR codes
     * are rendered for concurrent requests. Parse received HL7 with
     * {@link Hl7Parsers#newPipeParser()}.
     *
     * @param message a message built with {@link Hl7Parsers#forBuilding(Message)}
     * @return the pipe-encoded message
     * @throws HL7Exception if HAPI cannot encode the message
     */
    public static String encode(Message message) throws HL7Exception {
        return Hl7Parsers.newEncodingPipeParser().encode(message);
    }
}
