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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.SAXParseException;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.util.Terser;
import io.github.carlos_emr.carlos.commn.hl7.v2.Hl7XmlEntityAttackFixture.Attack;

/**
 * Tests the CARLOS boundary for HL7 v2 XML parsing (issue #5414).
 *
 * <p>HAPI 2.6.0 already turns off external entities, but it allows DOCTYPE declarations and only
 * logs a warning when the XML parser rejects one of its protective features. The hardened parser
 * refuses any DTD and refuses to parse at all when a protection cannot be applied.</p>
 */
@Tag("unit")
@Tag("fast")
@Tag("security")
@Tag("hl7")
@DisplayName("HardenedHl7XmlParser")
class HardenedHl7XmlParserUnitTest {

    @TempDir
    Path tempDir;

    private Hl7XmlEntityAttackFixture fixture;

    @BeforeEach
    void startObserver() throws Exception {
        fixture = new Hl7XmlEntityAttackFixture(tempDir);
    }

    @AfterEach
    void stopObserver() {
        fixture.close();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Attack.class)
    @DisplayName("should reject the message without external access for each XML entity attack")
    void shouldRejectMessageWithoutExternalAccess_forEachXmlEntityAttack(Attack attack) {
        HardenedHl7XmlParser parser = new HardenedHl7XmlParser();

        assertThatThrownBy(() -> parser.parse(fixture.payload(attack)))
                .isInstanceOf(HL7Exception.class)
                .hasMessageNotContaining(fixture.fileMarker());

        assertThat(fixture.requests()).isEmpty();
    }

    @Test
    @DisplayName("should parse fields when the XML message has no DOCTYPE")
    void shouldParseFields_whenXmlMessageHasNoDoctype() throws Exception {
        Message message = new HardenedHl7XmlParser().parse(Hl7XmlEntityAttackFixture.benignXml());

        Terser terser = new Terser(message);
        assertThat(terser.get("/MSH-9-1")).isEqualTo("ADT");
        assertThat(terser.get("/MSH-9-2")).isEqualTo("A01");
        assertThat(terser.get("/PID-5-1")).isEqualTo("SYNTHETIC");
        assertThat(terser.get("/PID-5-2")).isEqualTo("PATIENT");
        assertThat(terser.get("/PID-7-1")).isEqualTo("19800101");
    }

    @Test
    @DisplayName("should keep every field when a message round-trips through the XML encoding")
    void shouldKeepEveryField_whenMessageRoundTripsThroughXmlEncoding() throws Exception {
        String pipe = "MSH|^~\\&|PHS|STAR|CARLOS|CARLOS|20261029120000-0500||ADT^A08|SYNTH0003|P|2.3\r"
                + "EVN|A08|20261029120000\r"
                + "PID|1||SYN000123^^^PHS^MR~9999999901^^^ON^JHN||C\\T\\TÉ^HÉLÈNE^Q~ALIAS^ZOË||19800101|F|||"
                + "1 FAKE ST\\F\\UNIT 2^^MONTRÉAL^QC^H0H 0H0||555-0100~555-0101\r"
                + "PV1|1|O|CLINIC^^^STAR||||9013^FAKE^DOCTOR^^^DR\r";
        Message original = Hl7Parsers.newPipeParser().parse(pipe);
        HardenedHl7XmlParser xmlParser = new HardenedHl7XmlParser(Hl7Parsers.newParsingContext());

        Message reparsed = xmlParser.parse(xmlParser.encode(original));

        PipeParser encoder = Hl7Parsers.newEncodingPipeParser();
        assertThat(encoder.encode(reparsed)).isEqualTo(encoder.encode(original));
        assertThat(new Terser(reparsed).get("/PID-5-1")).isEqualTo("C&TÉ");
        assertThat(new Terser(reparsed).get("/PID-11-1")).isEqualTo("1 FAKE ST|UNIT 2");
    }

    @Test
    @DisplayName("should reject the message when XML is not well formed")
    void shouldRejectMessage_whenXmlIsNotWellFormed() {
        assertThatThrownBy(() -> new HardenedHl7XmlParser().parse("<ADT_A01 xmlns=\"urn:hl7-org:v2xml\"><MSH>"))
                .isInstanceOf(HL7Exception.class);
    }

    @Test
    @DisplayName("should report a rejection only through the exception when parsing fails")
    void shouldReportRejectionOnlyThroughException_whenParsingFails() throws Exception {
        // The platform parser's default error handler prints "[Fatal Error] ..." to standard error,
        // which lands in catalina.out outside the application's logging.
        PrintStream originalErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            HardenedHl7XmlParser parser = new HardenedHl7XmlParser();
            assertThatThrownBy(() -> parser.parse(fixture.payload(Attack.EXTERNAL_DTD_SYSTEM)))
                    .isInstanceOf(HL7Exception.class)
                    .hasRootCauseInstanceOf(SAXParseException.class);
            String truncated = Hl7XmlEntityAttackFixture.benignXml();
            String malformed = truncated.substring(0, truncated.length() - 12);
            assertThatThrownBy(() -> parser.parse(malformed))
                    .isInstanceOf(HL7Exception.class)
                    .hasRootCauseInstanceOf(SAXParseException.class);
        } finally {
            System.setErr(originalErr);
        }

        assertThat(captured.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "http://apache.org/xml/features/disallow-doctype-decl",
            "http://xml.org/sax/features/external-general-entities",
            "http://xml.org/sax/features/external-parameter-entities",
            "http://apache.org/xml/features/nonvalidating/load-external-dtd",
            XMLConstants.FEATURE_SECURE_PROCESSING})
    @DisplayName("should refuse to build a parser when a protective feature is unsupported")
    void shouldRefuseToBuildParser_whenProtectiveFeatureIsUnsupported(String feature) {
        DocumentBuilderFactory factory = new RefusingFactory(DocumentBuilderFactory.newInstance(), feature);

        assertThatThrownBy(() -> HardenedHl7XmlParser.harden(factory))
                .isInstanceOf(ParserConfigurationException.class);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {XMLConstants.ACCESS_EXTERNAL_DTD, XMLConstants.ACCESS_EXTERNAL_SCHEMA})
    @DisplayName("should refuse to build a parser when an external access limit is unsupported")
    void shouldRefuseToBuildParser_whenExternalAccessLimitIsUnsupported(String attribute) {
        DocumentBuilderFactory factory = new RefusingFactory(DocumentBuilderFactory.newInstance(), attribute);

        assertThatThrownBy(() -> HardenedHl7XmlParser.harden(factory))
                .isInstanceOf(ParserConfigurationException.class);
    }

    @Test
    @DisplayName("should apply every protection when the platform parser supports them")
    void shouldApplyEveryProtection_whenPlatformParserSupportsThem() throws Exception {
        DocumentBuilderFactory factory = HardenedHl7XmlParser.harden(DocumentBuilderFactory.newInstance());

        assertThat(factory.getFeature("http://apache.org/xml/features/disallow-doctype-decl")).isTrue();
        assertThat(factory.getFeature("http://xml.org/sax/features/external-general-entities")).isFalse();
        assertThat(factory.getFeature("http://xml.org/sax/features/external-parameter-entities")).isFalse();
        assertThat(factory.getFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd")).isFalse();
        assertThat(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING)).isTrue();
        assertThat(factory.getAttribute(XMLConstants.ACCESS_EXTERNAL_DTD)).isEqualTo("");
        assertThat(factory.getAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA)).isEqualTo("");
        assertThat(factory.isNamespaceAware()).isTrue();
        assertThat(factory.isXIncludeAware()).isFalse();
        assertThat(factory.isExpandEntityReferences()).isFalse();
    }

    /** A platform factory that rejects one named feature or attribute, like an older parser would. */
    private static final class RefusingFactory extends DocumentBuilderFactory {
        private final DocumentBuilderFactory delegate;
        private final String refused;

        RefusingFactory(DocumentBuilderFactory delegate, String refused) {
            this.delegate = delegate;
            this.refused = refused;
        }

        @Override
        public DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
            delegate.setNamespaceAware(isNamespaceAware());
            delegate.setXIncludeAware(isXIncludeAware());
            delegate.setExpandEntityReferences(isExpandEntityReferences());
            return delegate.newDocumentBuilder();
        }

        @Override
        public void setAttribute(String name, Object value) {
            if (refused.equals(name)) {
                throw new IllegalArgumentException("unsupported attribute " + name);
            }
            delegate.setAttribute(name, value);
        }

        @Override
        public Object getAttribute(String name) {
            return delegate.getAttribute(name);
        }

        @Override
        public void setFeature(String name, boolean value) throws ParserConfigurationException {
            if (refused.equals(name)) {
                throw new ParserConfigurationException("unsupported feature " + name);
            }
            delegate.setFeature(name, value);
        }

        @Override
        public boolean getFeature(String name) throws ParserConfigurationException {
            return delegate.getFeature(name);
        }
    }
}
