/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.all.parsers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.validation.impl.NoValidation;

/**
 * HAPI 2.x folds every run of adjacent same-type segments into repetitions of one name, where
 * HAPI 1.x folded only the first run of each type. {@link GenericSegmentNames} restores the 1.x
 * names so {@link IHAHandler} and {@link DefaultGenericHandler}, which look requests up as
 * {@code "OBR" + n}, keep counting every request (issue #5414). Without it
 * {@code CC63171.hl7}, whose 19th and 20th requests are back to back, reported 20 requests, not 21.
 */
@Tag("unit")
@Tag("lab")
@Tag("hl7")
@DisplayName("GenericSegmentNames (HAPI 1.x segment names on HAPI 2.x)")
class GenericSegmentNamesUnitTest {

    /** Parses {@code segments} (slash separated, after an MSH) as a generic message and names them. */
    private static GenericSegmentNames namesOf(String segments, String version) throws HL7Exception {
        String body = "MSH|^~\\&|A|B|C|D|20260315||ORU^O01|X|P|" + version + "\r" + segments.replace('/', '\r') + "\r";
        PipeParser parser = new PipeParser();
        parser.setValidationContext(new NoValidation());
        Message message = parser.parse(body);
        return GenericSegmentNames.of(new Terser(message).getFinder().getRoot());
    }

    /** "MSH OBR*2 OBX" style: each name, with the number of repetitions after * when it is more than 1. */
    private static String describe(GenericSegmentNames names) throws HL7Exception {
        List<String> parts = new ArrayList<>();
        for (String name : names.getNames()) {
            int reps = names.getAll(name).length;
            parts.add(reps == 1 ? name : name + "*" + reps);
        }
        return String.join(" ", parts);
    }

    static Stream<Arguments> hapi1Names() {
        return Stream.of(
                // the first run of a type is one name with repetitions
                Arguments.of("OBR|1/OBR|2", "MSH OBR*2"),
                Arguments.of("OBR|1/OBR|2/OBR|3", "MSH OBR*3"),
                Arguments.of("NTE|1/NTE|2", "MSH NTE*2"),
                Arguments.of("OBR|1/OBR|2/OBX|1", "MSH OBR*2 OBX"),
                // a later run is not folded: every segment gets a name of its own, adjacent or not
                Arguments.of("OBR|1/OBX|1/OBR|2/OBR|3", "MSH OBR OBX OBR2 OBR3"),
                Arguments.of("OBR|1/OBX|1/OBX|2/NTE|1/NTE|2/OBX|3/OBX|4", "MSH OBR OBX*2 NTE*2 OBX2 OBX3"),
                Arguments.of("OBR|1/NTE|1/NTE|2/OBX|1/OBX|2/OBR|2/OBX|1", "MSH OBR NTE*2 OBX*2 OBR2 OBX2"),
                Arguments.of("ZZZ|1/ZZZ|2/PID|1/ZZZ|3", "MSH ZZZ*2 PID ZZZ2"));
    }

    @ParameterizedTest(name = "{0} => {1}")
    @MethodSource("hapi1Names")
    @DisplayName("should name segments the way HAPI 1.x did, for HL7 2.3 and 2.3.1")
    void shouldNameSegments_likeHapi1(String segments, String expected) throws Exception {
        assertThat(describe(namesOf(segments, "2.3"))).isEqualTo(expected);
        assertThat(describe(namesOf(segments, "2.3.1"))).isEqualTo(expected);
    }

    @Test
    @DisplayName("should keep every repetition in message order under its name")
    void shouldKeepRepetitionsInOrder() throws Exception {
        GenericSegmentNames names = namesOf("OBR|1/OBX|1/OBX|2/OBR|2/OBR|3/OBX|3", "2.3");
        assertThat(names.getNames()).containsExactly("MSH", "OBR", "OBX", "OBR2", "OBR3", "OBX2");
        assertThat(setIds(names.getAll("OBX"))).containsExactly("1", "2");
        assertThat(names.get("OBR3").getName()).isEqualTo("OBR");
        assertThat(Terser.get(names.get("OBR3"), 1, 0, 1, 1)).isEqualTo("3");
    }

    @Test
    @DisplayName("should refuse a name the message does not have")
    void shouldThrowHl7Exception_whenNameIsUnknown() throws Exception {
        GenericSegmentNames names = namesOf("OBR|1", "2.3");
        assertThatThrownBy(() -> names.get("OBR2")).isInstanceOf(HL7Exception.class);
        assertThatThrownBy(() -> names.getAll("NTE")).isInstanceOf(HL7Exception.class);
    }

    @Test
    @DisplayName("should list only MSH for a structured message, whose other segments sit inside groups")
    void shouldListOnlyMsh_whenMessageIsStructured() throws Exception {
        PipeParser parser = new PipeParser();
        parser.setValidationContext(new NoValidation());
        Message message = parser.parse("MSH|^~\\&|A|B|C|D|20260315||ORU^R01|X|P|2.3\rPID|1||1^^^X||DOE^JOHN\r"
                + "OBR|1|P|F|CBC^Cbc\rOBX|1|ST|A^a||v||||||F\r");
        GenericSegmentNames names = GenericSegmentNames.of(new Terser(message).getFinder().getRoot());
        assertThat(names.getNames()).containsExactly("MSH");
    }

    private static List<String> setIds(Segment[] segments) throws HL7Exception {
        List<String> ids = new ArrayList<>();
        for (Segment segment : segments) {
            ids.add(Terser.get(segment, 1, 0, 1, 1));
        }
        return ids;
    }

    /** The CML sample whose 19th and 20th requests have no results between them. */
    @Nested
    @DisplayName("IHAHandler on a lab with back-to-back requests")
    class IhaHandlerWithAdjacentRequests {

        private String cml(String name) throws IOException {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("labs/HL7-CML/" + name)) {
                assertThat(in).as("fixture %s", name).isNotNull();
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        private int[] observationsPerRequest(MessageHandler handler) {
            int[] counts = new int[handler.getOBRCount()];
            for (int i = 0; i < counts.length; i++) {
                counts[i] = handler.getOBXCount(i);
            }
            return counts;
        }

        @Test
        @DisplayName("should count all 21 requests and all 53 results of CC63171")
        void shouldCountEveryRequestAndResult_inCc63171() throws Exception {
            IHAHandler handler = new IHAHandler();
            handler.init(cml("CC63171.hl7"));

            assertThat(handler.getOBRCount()).isEqualTo(21);
            // the numbers HAPI 1.0.1 produced for the same file
            assertThat(observationsPerRequest(handler)).containsExactly(
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 5, 10, 20, 1, 1, 1, 1, 1, 1, 0, 2, 1);
            assertThat(handler.getOBRName(0)).isEqualTo("GLUCOSE FASTING");
            assertThat(handler.getOBRName(20)).isEqualTo("BLOOD FILM INTERPRETATION");
        }

        @Test
        @DisplayName("should count every request of CC63208, which ends with a request that has no result")
        void shouldCountEveryRequest_inCc63208() throws Exception {
            IHAHandler handler = new IHAHandler();
            handler.init(cml("CC63208.hl7"));

            assertThat(handler.getOBRCount()).isEqualTo(30);
            assertThat(observationsPerRequest(handler)).containsExactly(
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 16, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0);
            assertThat(handler.getOBRName(29)).isEqualTo("DOCUMENTATION FEE");
        }

        @Test
        @DisplayName("should return the message date for the request date of every request after the first, as with HAPI 1.x")
        void shouldReturnMessageDate_forRequestDateAfterFirst() throws Exception {
            IHAHandler handler = new IHAHandler();
            handler.init(cml("CC63171.hl7"));

            assertThat(handler.getRequestDate(1)).isEqualTo(handler.getMsgDate());
            assertThat(handler.getRequestDate(20)).isEqualTo(handler.getMsgDate());
        }
    }
}
