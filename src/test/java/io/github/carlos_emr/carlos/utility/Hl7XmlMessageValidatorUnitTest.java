/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.parser.GenericParser;
import ca.uhn.hl7v2.parser.Parser;
import io.github.carlos_emr.carlos.test.hl7.HostileHl7Xml;

/**
 * {@link Hl7XmlMessageValidator} stops HL7 v2 XML that declares a document type before HAPI reads
 * it. The hostile messages ask for a local file, loopback HTTP resources and an exponentially
 * growing internal entity; the loopback observer proves nothing was fetched.
 */
@Tag("unit")
@Tag("security")
@Tag("hl7")
@DisplayName("Hl7XmlMessageValidator")
class Hl7XmlMessageValidatorUnitTest {

    private HostileHl7Xml hostile;
    private Parser parser;

    @BeforeEach
    void setUp() throws Exception {
        hostile = HostileHl7Xml.start();
        parser = new GenericParser();
    }

    @AfterEach
    void tearDown() throws Exception {
        hostile.close();
    }

    private void assertRejected(ThrowingSupplier<String> message) {
        assertThatThrownBy(() -> Hl7XmlMessageValidator.requireNoDocumentType(parser, message.get()))
                .isInstanceOf(HL7Exception.class)
                .hasMessageContaining("document type")
                .hasMessageNotContaining(HostileHl7Xml.MARKER)
                .hasMessageNotContaining(HostileHl7Xml.FAMILY_NAME);
        assertThat(hostile.requestCount()).as("requests the parser made to the loopback observer").isZero();
    }

    @Test
    @DisplayName("should reject an external file entity without reading the file")
    void shouldRejectMessage_withExternalFileEntity() {
        assertRejected(hostile::externalFileEntity);
    }

    @Test
    @DisplayName("should reject an external HTTP entity without making a request")
    void shouldRejectMessage_withExternalHttpEntity() {
        assertRejected(hostile::externalHttpEntity);
    }

    @Test
    @DisplayName("should reject an external parameter entity without making a request")
    void shouldRejectMessage_withExternalParameterEntity() {
        assertRejected(hostile::externalParameterEntity);
    }

    @Test
    @DisplayName("should reject an external DTD without making a request")
    void shouldRejectMessage_withExternalDtd() {
        assertRejected(hostile::externalDtd);
    }

    @Test
    @DisplayName("should reject a file read through a parameter entity")
    void shouldRejectMessage_withExternalFileParameterEntity() {
        assertRejected(hostile::externalFileParameterEntity);
    }

    @Test
    @DisplayName("should reject a harmless internal entity because the DOCTYPE itself is refused")
    void shouldRejectMessage_withHarmlessInternalEntity() {
        assertRejected(hostile::harmlessInternalEntity);
    }

    @Test
    @DisplayName("should reject an exponential entity expansion before any of it is expanded")
    void shouldRejectMessage_withExponentialEntityExpansion() throws Exception {
        // Nine levels is ten to the ninth copies: an unguarded parser does not finish this.
        String attack = hostile.internalEntityExpansion(9);
        assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
                assertThatThrownBy(() -> Hl7XmlMessageValidator.requireNoDocumentType(parser, attack))
                        .isInstanceOf(HL7Exception.class)
                        .hasMessageContaining("document type"));
    }

    @Test
    @DisplayName("should reject XML that is not well-formed")
    void shouldRejectMessage_whenXmlIsMalformed() throws Exception {
        String malformed = hostile.validXml().replace("</ADT_A01>", "");
        assertThatThrownBy(() -> Hl7XmlMessageValidator.requireNoDocumentType(parser, malformed))
                .isInstanceOf(HL7Exception.class)
                .hasMessageContaining("well-formed");
    }

    @Test
    @DisplayName("should accept well-formed XML that has no DOCTYPE")
    void shouldAcceptMessage_whenXmlHasNoDocumentType() throws Exception {
        String xml = hostile.validXml();
        assertThatCode(() -> Hl7XmlMessageValidator.requireNoDocumentType(parser, xml)).doesNotThrowAnyException();
        // and HAPI goes on to read the very same text
        assertThat(parser.parse(xml)).isNotNull();
    }

    @Test
    @DisplayName("should leave pipe-delimited messages alone, even ones that mention DOCTYPE in a value")
    void shouldAcceptMessage_whenMessageIsPipeDelimited() {
        String pipe = hostile.validPipe()
                + "OBX|1|TX|NOTE^Note||<!DOCTYPE html> is quoted result text, as is <b> and &amp;||||||F\r";
        assertThatCode(() -> Hl7XmlMessageValidator.requireNoDocumentType(parser, pipe)).doesNotThrowAnyException();
        assertThat(hostile.requestCount()).isZero();
    }

    @Test
    @DisplayName("should ignore a null message and leave HAPI to report it")
    void shouldReturn_whenMessageIsNull() {
        assertThatCode(() -> Hl7XmlMessageValidator.requireNoDocumentType(parser, null)).doesNotThrowAnyException();
    }
}
