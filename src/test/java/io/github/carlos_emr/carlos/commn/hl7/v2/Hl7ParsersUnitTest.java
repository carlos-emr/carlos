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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v26.message.OMP_O09;
import ca.uhn.hl7v2.util.Terser;

/**
 * Tests the parser settings {@link Hl7Parsers} hands out (issue #5414). Expected values are what
 * HAPI HL7 v2 1.0.1 produced for the same input.
 */
@Tag("unit")
@Tag("fast")
@Tag("hl7")
@DisplayName("Hl7Parsers")
class Hl7ParsersUnitTest {

    private static final String LAB_RESULT = "MSH|^~\\&|LAB|FAC|CARLOS|CARLOS|20261029120000||ORU^R01|C1|P|2.3\r"
            + "PID|1||9999999901||FAKE^PATIENT||19800101|F\r"
            + "OBR|1||ACC1|TEST^Test|||20261029\r"
            + "OBX|1|TX|CODE^Name||  Indented \\H\\high\\N\\ text  |u|0-1|H|||F\r"
            + "OBX|2|NM|CODE^Name||<5|u|0-1|L|||F\r";

    @Test
    @DisplayName("should read text like HAPI 1.0.1 when parsing a received message")
    void shouldReadTextLikeHapi101_whenParsingReceivedMessage() throws Exception {
        Message message = Hl7Parsers.newPipeParser().parse(LAB_RESULT);
        Terser terser = new Terser(message);

        assertThat(terser.get("/.OBSERVATION(0)/OBX-5")).isEqualTo("Indented HhighN text  ");
        assertThat(terser.get("/.OBSERVATION(1)/OBX-5")).isEqualTo("<5");
    }

    @Test
    @DisplayName("should keep values exactly as set when a message is built for encoding")
    void shouldKeepValuesExactlyAsSet_whenMessageIsBuiltForEncoding() throws Exception {
        OMP_O09 message = Hl7Parsers.forBuilding(new OMP_O09());
        message.getMSH().getFieldSeparator().setValue("|");
        message.getMSH().getEncodingCharacters().setValue("^~\\&");
        message.getMSH().getVersionID().getVersionID().setValue("2.6");
        message.getPATIENT().getPID().getPatientName(0).getGivenName().setValue("  Padded  ");
        message.getORDER(0).getRXO().getRequestedGiveAmountMinimum().setValue("1-2");

        String encoded = Hl7Parsers.newEncodingPipeParser().encode(message);

        assertThat(encoded).contains("PID|||||^  Padded  \r");
        assertThat(encoded).contains("RXO||1-2\r");
    }

    @Test
    @DisplayName("should escape formatting codes like HAPI 1.0.1 when encoding")
    void shouldEscapeFormattingCodesLikeHapi101_whenEncoding() throws Exception {
        OMP_O09 message = Hl7Parsers.forBuilding(new OMP_O09());
        message.getMSH().getFieldSeparator().setValue("|");
        message.getMSH().getEncodingCharacters().setValue("^~\\&");
        message.getPATIENT().getPID().getPatientName(0).getGivenName().setValue("\\H\\x\\N\\ a|b");

        assertThat(Hl7Parsers.newEncodingPipeParser().encode(message))
                .contains("PID|||||^\\E\\H\\E\\x\\E\\N\\E\\ a\\F\\b\r");
    }
}
