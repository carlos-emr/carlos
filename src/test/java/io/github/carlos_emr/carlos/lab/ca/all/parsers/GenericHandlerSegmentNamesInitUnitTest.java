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
package io.github.carlos_emr.carlos.lab.ca.all.parsers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every {@link DefaultGenericHandler} subclass that replaces {@code init} must also build the
 * {@code segmentNames} the inherited comment and note lookups read (issue #5414). A subclass that
 * forgets leaves the field {@code null}: the lookups catch the {@link NullPointerException}, log an
 * error for every result and answer zero, so nothing visibly fails.
 */
@Tag("unit")
@Tag("lab")
@Tag("hl7")
@DisplayName("Generic handler subclasses build their segment names in init")
class GenericHandlerSegmentNamesInitUnitTest {

    private static String resource(String name) throws IOException {
        try (InputStream in = GenericHandlerSegmentNamesInitUnitTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(in).as("fixture %s", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String oru(String version) {
        return String.join("\r",
                "MSH|^~\\&|GENLAB|CARLOSTEST|HTTPCLIENT|carlos|20261001101500||ORU^R01|PW5414MSG|P|" + version + "|||ER|AL",
                "PID||9999999999|5414||PLAYWRIGHT^HANDLERS||19800102|F",
                "ORC|RE||ACC5414|||||||||99999^FAKE-ORDERING^DOC",
                "OBR|1|PL5414|ACC5414|RPT^Report||20261001100000|20261001100000|||||||20261001100000"
                        + "||99999^FAKE-ORDERING^DOC||||||20261001100000||CHEM4|F",
                "OBX|1|NM|GLU^Glucose||5.2|mmol/L|3.3-7.7|N|||F|||20261001100000",
                "NTE|1||FAKE comment") + "\r";
    }

    static Stream<Arguments> subclasses() throws IOException {
        return Stream.of(
                Arguments.of(Named.of("DefaultGenericHandler", (Supplier<DefaultGenericHandler>) DefaultGenericHandler::new),
                        oru("2.3")),
                Arguments.of(Named.of("AlphaHandler", (Supplier<DefaultGenericHandler>) AlphaHandler::new), oru("2.3")),
                Arguments.of(Named.of("EpsilonHandler", (Supplier<DefaultGenericHandler>) EpsilonHandler::new),
                        resource("labs/HL7-Epsilon/medhealth-pap.hl7")),
                Arguments.of(Named.of("HHSEmrDownloadHandler", (Supplier<DefaultGenericHandler>) HHSEmrDownloadHandler::new),
                        oru("2.3")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("subclasses")
    @DisplayName("should build the segment names the inherited lookups read")
    void shouldBuildSegmentNames_inInit(Supplier<DefaultGenericHandler> factory, String message) throws Exception {
        DefaultGenericHandler handler = factory.get();
        handler.init(message);

        assertThat(handler.segmentNames).isNotNull();
        assertThat(handler.segmentNames.getNames()).isNotEmpty();
    }
}
