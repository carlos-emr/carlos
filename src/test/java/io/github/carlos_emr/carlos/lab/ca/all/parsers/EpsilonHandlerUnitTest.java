/**
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 * <p>
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.lab.ca.all.parsers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import ca.uhn.hl7v2.HL7Exception;

/**
 * Unit tests for {@link EpsilonHandler} result-row accessors.
 *
 * <p>{@code EpsilonHandler} parses into its own typed {@code ORU_R01}, but the inherited
 * {@code getOBRCount}, {@code getOBXCount}, {@code getOBXValueType} and the other
 * {@code DefaultGenericHandler} accessors read {@code obrGroups}/{@code terser}. These tests pin that
 * both views of the message agree, because the lab views and the CDS export loop over
 * {@code getOBRCount()} and {@code getOBXCount(i)}.</p>
 *
 * <p>The fixtures are repository test data (synthetic). Each fixture holds several patients behind
 * one MSH; upload separates them into one message per PID
 * ({@code Utilities.separateMessages}), so the tests do the same.</p>
 */
@Tag("unit")
@Tag("fast")
@Tag("lab")
@DisplayName("EpsilonHandler result-row accessors")
class EpsilonHandlerUnitTest {

    private static final String GENERAL = "labs/HL7-Epsilon/medhealth-general.hl7";
    private static final String PAP = "labs/HL7-Epsilon/medhealth-pap.hl7";

    /** Splits a fixture into one message per PID, repeating the MSH, as the upload path does. */
    private static List<String> separateMessages(String resource) throws IOException {
        String body;
        try (InputStream in = EpsilonHandlerUnitTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as("fixture %s", resource).isNotNull();
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        List<String> messages = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String msh = "";
        boolean seenPid = false;
        for (String line : body.split("\\R")) {
            if (line.length() <= 3) {
                continue;
            }
            if (line.startsWith("MSH")) {
                msh = line;
            } else if (line.startsWith("PID")) {
                if (seenPid) {
                    messages.add(current.toString());
                    current = new StringBuilder(msh).append("\r\n");
                }
                seenPid = true;
            }
            current.append(line).append("\r\n");
        }
        messages.add(current.toString());
        return messages;
    }

    private static long countSegments(String message, String name) {
        return message.lines().filter(l -> l.startsWith(name + "|")).count();
    }

    private static EpsilonHandler handlerFor(String message) throws HL7Exception {
        EpsilonHandler handler = new EpsilonHandler();
        handler.init(message);
        return handler;
    }

    static Stream<String> messages() throws IOException {
        List<String> all = new ArrayList<>(separateMessages(GENERAL));
        all.addAll(separateMessages(PAP));
        return all.stream();
    }

    @ParameterizedTest
    @MethodSource("messages")
    @DisplayName("should expose every OBR and OBX of the message to the inherited accessors")
    void shouldCountAllObrAndObx_forEachSeparatedMessage(String message) throws HL7Exception {
        EpsilonHandler handler = handlerFor(message);

        assertThat(handler.getOBRCount()).isEqualTo((int) countSegments(message, "OBR"));

        int obxTotal = 0;
        for (int i = 0; i < handler.getOBRCount(); i++) {
            obxTotal += handler.getOBXCount(i);
        }
        assertThat(obxTotal).isEqualTo((int) countSegments(message, "OBX"));
    }

    @ParameterizedTest
    @MethodSource("messages")
    @DisplayName("should not throw from the OBX accessors the lab views and exports call")
    void shouldNotThrow_whenReadingObxAccessors(String message) throws HL7Exception {
        EpsilonHandler handler = handlerFor(message);

        assertThat(handler.getOBRCount()).isPositive();
        for (int i = 0; i < handler.getOBRCount(); i++) {
            for (int j = 0; j < handler.getOBXCount(i); j++) {
                assertThat(handler.getOBXValueType(i, j)).isNotBlank();
                assertThat(handler.getOBXIdentifier(i, j)).isNotBlank();
                // The remaining accessors the Epsilon branches of both lab views call for each row.
                assertThat(handler.getOBXResult(i, j)).isNotNull();
                assertThat(handler.getOBXReferenceRange(i, j)).isNotNull();
                assertThat(handler.getOBXUnits(i, j)).isNotNull();
                assertThat(handler.getOBXAbnormalFlag(i, j)).isNotNull();
                assertThat(handler.getOBXResultStatus(i, j)).isNotNull();
                assertThat(handler.getOBXCommentCount(i, j)).isZero();
            }
        }
    }

    @ParameterizedTest
    @MethodSource("messages")
    @DisplayName("should list every observation header the display groups rows under")
    void shouldReturnHeaders_matchingObxIdentifiers(String message) throws HL7Exception {
        EpsilonHandler handler = handlerFor(message);

        List<String> headers = handler.getHeaders();
        assertThat(headers).isNotEmpty();
        assertThat(handler.getOBRCount()).isPositive();
        for (int i = 0; i < handler.getOBRCount(); i++) {
            for (int j = 0; j < handler.getOBXCount(i); j++) {
                assertThat(headers).contains(handler.getOBXIdentifier(i, j));
            }
        }
    }

    @Test
    @DisplayName("should read the first FOBT row identically through the typed and inherited accessors")
    void shouldReadFirstRow_throughInheritedAccessors() throws Exception {
        EpsilonHandler handler = handlerFor(separateMessages(GENERAL).get(0));

        assertThat(handler.getOBRCount()).isEqualTo(1);
        assertThat(handler.getOBXCount(0)).isEqualTo(3);
        assertThat(handler.getOBXValueType(0, 0)).isEqualTo("FT");
        assertThat(handler.getOBXIdentifier(0, 0)).isEqualTo("FOBT (ColonCancerCheck)");
        assertThat(handler.getOBXResult(0, 0)).isEqualTo("Negative");
        assertThat(handler.getHeaders()).containsExactly("FOBT (ColonCancerCheck)");
    }

    @Test
    @DisplayName("should keep the OBX rows of a multi-result message in order")
    void shouldKeepObxOrder_forChemistryMessage() throws Exception {
        EpsilonHandler handler = handlerFor(separateMessages(GENERAL).get(1));

        assertThat(handler.getOBRCount()).isEqualTo(1);
        assertThat(handler.getOBXCount(0)).isEqualTo(15);
        assertThat(handler.getOBXValueType(0, 0)).isEqualTo("NM");
        assertThat(handler.getOBXResult(0, 0)).isEqualTo("4.2");
        assertThat(handler.getOBXIdentifier(0, 9)).isEqualTo("GENERAL CHEMISTRY");
        assertThat(handler.getOBXName(0, 9)).isEqualTo("LDL");
        assertThat(handler.getOBXResult(0, 9)).isEqualTo("2.59");
    }

    /** OBR-4 split into components, read straight from the message text (not through the handler). */
    private static String[] obr4Components(String message) {
        String obr = message.lines().filter(l -> l.startsWith("OBR|")).findFirst().orElseThrow();
        String[] fields = obr.split("\\|", -1);
        return fields[4].split("\\^", -1);
    }

    @ParameterizedTest
    @MethodSource("messages")
    @DisplayName("should return the OBR name and identifier the lab view prints, as written in OBR-4")
    void shouldReturnObrNameAndIdentifier_asWrittenInObr4(String message) throws HL7Exception {
        String[] obr4 = obr4Components(message);
        String expectedId = obr4[0];
        // The name is OBR-4.2 and falls back to OBR-4.1, as the lab views expect.
        String expectedName = obr4.length > 1 && !obr4[1].isEmpty() ? obr4[1] : obr4[0];

        EpsilonHandler handler = handlerFor(message);

        assertThat(handler.getOBRCount()).isEqualTo(1);
        assertThat(handler.getOBRName(0)).isEqualTo(expectedName);
        assertThat(handler.getOBRIdentifier(0)).isEqualTo(expectedId);
    }

    @Test
    @DisplayName("should return the OBR-4 text for a cytology report and blanks where the sender leaves OBR-4 empty")
    void shouldReturnLiteralObrNames_forEachFixtureStyle() throws Exception {
        EpsilonHandler cytology = handlerFor(separateMessages(PAP).get(0));
        assertThat(cytology.getOBRName(0)).isEqualTo("CYTOLOGY");
        assertThat(cytology.getOBRIdentifier(0)).isEqualTo("CYT");

        EpsilonHandler general = handlerFor(separateMessages(GENERAL).get(0));
        assertThat(general.getOBRName(0)).isEmpty();
        assertThat(general.getOBRIdentifier(0)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("messages")
    @DisplayName("should not surface OBR-level comments that the fixtures do not contain")
    void shouldReturnBlankObrComments_forFixtureMessages(String message) throws HL7Exception {
        EpsilonHandler handler = handlerFor(message);

        for (int i = 0; i < handler.getOBRCount(); i++) {
            for (int k = 0; k < handler.getOBRCommentCount(i); k++) {
                // The CDS export writes a comment row for every non-blank OBR comment.
                assertThat(handler.getOBRComment(i, k)).isBlank();
            }
        }
    }

    @Test
    @DisplayName("should still report EPSILON as the message type")
    void shouldReturnMsgType_asEpsilon() throws Exception {
        assertThat(handlerFor(separateMessages(GENERAL).get(0)).getMsgType()).isEqualTo("EPSILON");
    }
}
