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

import ca.uhn.hl7v2.model.GenericMessage;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v26.message.OMP_O09;
import ca.uhn.hl7v2.parser.PipeParser;
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

    /*
     * A message type the structures jar does not define (here ORU^O01, which IHAHandler and
     * DefaultGenericHandler force on purpose) is parsed into HAPI's generic model, where every
     * segment hangs off the root by name. The expected names are HAPI 1.0.1's output for the same
     * text: a later run of a segment type gets a new number per segment, where 2.x would fold the
     * run into repetitions of one name and the handlers, which count names, would lose an OBR.
     */
    private static final String GENERIC_RESULT = "MSH|^~\\&|LAB|FAC|CARLOS|CARLOS|20261029120000||ORU^O01|C1|P|2.3\r"
            + "PID|1\rOBR|2\rOBX|3\rOBX|4\rNTE|5\rNTE|6\rOBR|7\rOBR|8\rOBX|9\rNTE|10\r";

    @Test
    @DisplayName("should name repeated segments like HAPI 1.0.1 when a message falls back to the generic model")
    void shouldNameRepeatedSegmentsLikeHapi101_whenMessageFallsBackToGenericModel() throws Exception {
        Message message = Hl7Parsers.newPipeParser().parse(GENERIC_RESULT);

        assertThat(message).isInstanceOf(GenericMessage.class);
        assertThat(message.getNames())
                .containsExactly("MSH", "PID", "OBR", "OBX", "NTE", "OBR2", "OBR3", "OBX2", "NTE2");
        assertThat(message.getAll("OBX")).hasSize(2);
        assertThat(message.getAll("NTE")).hasSize(2);
        assertThat(message.getAll("OBR2")).hasSize(1);
        assertThat(new Terser(message).get("/.OBR3-1")).isEqualTo("8");
        assertThat(new Terser(message).get("/.NTE2-1")).isEqualTo("10");
    }

    @Test
    @DisplayName("should keep every value of a generic message when its segments are renamed")
    void shouldKeepEveryValue_whenGenericSegmentsAreRenamed() throws Exception {
        String text = "MSH|^~\\&|LAB|FAC|CARLOS|CARLOS|20261029120000||ORU^O01|C2|P|2.3\r"
                + "PID|1||9999999901^^^ON~ALT^^^MR||FAKE\\T\\NAME^ZOË||19800101|F\r"
                + "OBR|1||ACC1|TEST^Test\r"
                + "OBX|1|TX|CODE^Name||  line \\.br\\ two \\H\\x\\N\\ a&b ||||||F\r"
                + "OBR|2||ACC2|TEST^Test\r"
                + "OBR|3||ACC3|TEST^Test\r"
                + "  OBX|1|NM|CODE^Name||<5|u|0-1|L|||F\r"
                + "\r"
                + "ZZZ|custom^segment~rep\r";
        PipeParser legacy = Hl7Parsers.newPipeParser();
        PipeParser plain = new PipeParser(Hl7Parsers.newParsingContext());

        Message renamed = legacy.parse(text);
        Message folded = plain.parse(text);

        assertThat(legacy.encode(renamed)).isEqualTo(plain.encode(folded));
        assertThat(renamed.getNames()).containsExactly("MSH", "PID", "OBR", "OBX", "OBR2", "OBR3", "OBX2", "ZZZ");
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
