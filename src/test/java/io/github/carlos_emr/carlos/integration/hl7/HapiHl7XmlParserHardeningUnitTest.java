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
package io.github.carlos_emr.carlos.integration.hl7;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.InputSource;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.DefaultXMLParser;
import ca.uhn.hl7v2.parser.GenericParser;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.validation.impl.NoValidation;
import io.github.carlos_emr.carlos.test.hl7.HostileHl7Xml;

/**
 * Pins what the HAPI HL7 v2 library itself does with hostile XML, with none of CARLOS's own
 * checks in front of it (issue #5414). HAPI 1.0.1 followed external entities and DTDs; this fails
 * the build if a dependency change ever brings that back, whichever way HAPI chooses to handle
 * the message: rejecting it, or reading it with the external part left out.
 *
 * <p>Evidence of exposure is a request reaching the loopback observer or the marker file's text
 * appearing anywhere in what HAPI built. The first test shows the observer does see a parser that
 * follows external references, so a count of zero means something.
 */
@Tag("unit")
@Tag("security")
@Tag("hl7")
@DisplayName("HAPI HL7 v2 XML parser hardening")
class HapiHl7XmlParserHardeningUnitTest {

    private HostileHl7Xml hostile;

    @BeforeEach
    void setUp() throws Exception {
        hostile = HostileHl7Xml.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        hostile.close();
    }

    private Map<String, String> attacks() throws HL7Exception {
        Map<String, String> attacks = new LinkedHashMap<>();
        attacks.put("external file entity", hostile.externalFileEntity());
        attacks.put("external HTTP entity", hostile.externalHttpEntity());
        attacks.put("external parameter entity", hostile.externalParameterEntity());
        attacks.put("external DTD", hostile.externalDtd());
        attacks.put("file read through a parameter entity", hostile.externalFileParameterEntity());
        return attacks;
    }

    @Test
    @DisplayName("should show the observer sees a parser that follows external references")
    void shouldObserveRequest_whenParserFollowsExternalReferences() throws Exception {
        DocumentBuilderFactory unsafe = DocumentBuilderFactory.newInstance();
        // the JDK default: external DTDs and entities are loaded
        unsafe.setNamespaceAware(true);
        try {
            unsafe.newDocumentBuilder().parse(new InputSource(new StringReader(hostile.externalDtd())));
        } catch (Exception _) {
            // the DTD the observer serves does not declare the HL7 elements; the request is the point
        }
        assertThat(hostile.requestCount()).as("requests from a parser left at its defaults").isPositive();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GenericParser", "DefaultXMLParser"})
    @DisplayName("should neither read the marker file nor call out, whatever the parser does with the message")
    void shouldNotReachOutside_whenParsingHostileXml(String parserName) throws Exception {
        for (Map.Entry<String, String> attack : attacks().entrySet()) {
            Parser parser = parserName.equals("GenericParser") ? new GenericParser() : new DefaultXMLParser();
            parser.setValidationContext(new NoValidation());

            String content = "";
            try {
                Message parsed = parser.parse(attack.getValue());
                content = new DefaultXMLParser().encode(parsed);
            } catch (HL7Exception _) {
                // refusing the message is as good an answer as reading it without the reference
            }

            assertThat(hostile.requestCount())
                    .as("%s: requests made by %s", attack.getKey(), parserName).isZero();
            assertThat(content)
                    .as("%s: content built by %s", attack.getKey(), parserName)
                    .doesNotContain(HostileHl7Xml.MARKER);
        }
    }

    @Test
    @DisplayName("should still read a well-formed HL7 v2 XML message")
    void shouldParseMessage_whenXmlIsWellFormed() throws Exception {
        Parser parser = new GenericParser();
        Message message = parser.parse(hostile.validXml());
        assertThat(message.getName()).isEqualTo("ADT_A01");
        assertThat(new ca.uhn.hl7v2.util.Terser(message).get("/PID-5-1")).isEqualTo(HostileHl7Xml.FAMILY_NAME);
    }
}
