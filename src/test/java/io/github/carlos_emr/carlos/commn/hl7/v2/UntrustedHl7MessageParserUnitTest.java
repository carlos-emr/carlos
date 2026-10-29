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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import io.github.carlos_emr.carlos.commn.hl7.v2.Hl7XmlEntityAttackFixture.Attack;

/**
 * Tests the encoding-sniffing parser CARLOS uses for HL7 bodies that may be pipe or XML encoded
 * (issue #5414): the replacement for HAPI's {@code GenericParser}, whose XML side cannot be
 * swapped for the hardened one.
 */
@Tag("unit")
@Tag("fast")
@Tag("security")
@Tag("hl7")
@DisplayName("UntrustedHl7MessageParser")
class UntrustedHl7MessageParserUnitTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("should parse a pipe encoded message with the pipe parser")
    void shouldParsePipeEncodedMessage_withPipeParser() throws Exception {
        Message message = new UntrustedHl7MessageParser().parse(Hl7XmlEntityAttackFixture.benignPipe());

        assertThat(new Terser(message).get("/PID-5-1")).isEqualTo("SYNTHETIC");
        assertThat(message.getVersion()).isEqualTo("2.3");
    }

    @Test
    @DisplayName("should parse an XML encoded message with the hardened parser")
    void shouldParseXmlEncodedMessage_withHardenedParser() throws Exception {
        Message message = new UntrustedHl7MessageParser().parse(Hl7XmlEntityAttackFixture.benignXml());

        assertThat(new Terser(message).get("/PID-5-1")).isEqualTo("SYNTHETIC");
        assertThat(new Terser(message).get("/MSH-10")).isEqualTo("SYNTH0001");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Attack.class)
    @DisplayName("should reject the message without external access for each XML entity attack")
    void shouldRejectMessageWithoutExternalAccess_forEachXmlEntityAttack(Attack attack) throws Exception {
        try (Hl7XmlEntityAttackFixture fixture = new Hl7XmlEntityAttackFixture(tempDir)) {
            assertThatThrownBy(() -> new UntrustedHl7MessageParser().parse(fixture.payload(attack)))
                    .isInstanceOf(HL7Exception.class);
            assertThat(fixture.requests()).isEmpty();
        }
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"", "   ", "not an HL7 message", "{\"resourceType\":\"Patient\"}"})
    @DisplayName("should reject the body when it is neither pipe nor XML encoded")
    void shouldRejectBody_whenNeitherPipeNorXmlEncoded(String body) {
        assertThatThrownBy(() -> new UntrustedHl7MessageParser().parse(body))
                .isInstanceOf(HL7Exception.class);
    }

    @Test
    @DisplayName("should left-trim text values like HAPI 1.0.1 when parsing a pipe message")
    void shouldLeftTrimTextValuesLikeHapi101_whenParsingPipeMessage() throws Exception {
        Message message = new UntrustedHl7MessageParser().parse(
                "MSH|^~\\&|PHS|STAR|CARLOS|CARLOS|20261029120000||ADT^A01|SYNTH0002|P|2.3\r"
                        + "EVN|A01|20261029120000\r"
                        + "PID|1||SYN000123^^^PHS^MR||  SYNTHETIC^PATIENT||19800101|F\r");

        assertThat(new Terser(message).get("/PID-5-1")).isEqualTo("SYNTHETIC");
    }
}
