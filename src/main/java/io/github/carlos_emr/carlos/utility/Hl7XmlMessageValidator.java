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
 *
 */
package io.github.carlos_emr.carlos.utility;

import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.parser.Parser;

/**
 * Refuses HL7 v2 XML messages that carry a document type declaration, before HAPI parses them.
 *
 * <p>HL7 v2 XML encoding has no use for a DTD, and a DTD is how an XML document asks the parser
 * for files, network resources and exponentially growing entities (CWE-611, CWE-776). HAPI HL7 v2
 * 1.0.1 followed all of it. HAPI 2.x switches off external general and parameter entities and
 * external DTD loading, which closes the file and network reads, but it still accepts a
 * {@code DOCTYPE} and expands the internal entities it declares, so a small "billion laughs"
 * message costs the server time and memory. This validator closes that remaining gap by parsing
 * the text first with the project's hardened SAX parser ({@link XmlUtils#createSecureSAXParserFactory()}),
 * which rejects a {@code DOCTYPE} outright and so never reaches entity expansion.
 *
 * <p>Pipe-delimited ({@code MSH|...}) messages are not touched. Whether a message is XML is
 * decided by the same {@link Parser#getEncoding(String)} call HAPI makes when it picks a parser,
 * so a message cannot be classed as pipe-delimited here and as XML there.
 *
 * <p>Call {@link #requireNoDocumentType(Parser, String)} before {@code Parser.parse} on any text
 * that can come from outside CARLOS and may be XML. The only such call today is
 * {@code PhsStarHandler.init}, which uses {@code GenericParser}; the lab handlers use
 * {@code PipeParser}, which never reads XML.
 *
 * @since 2026-10-29
 */
public final class Hl7XmlMessageValidator {

    private static final String XML_ENCODING = "XML";

    private Hl7XmlMessageValidator() {
    }

    /**
     * Throws if {@code message} is XML that HAPI would parse and it is not well-formed or declares
     * a document type. Returns without reading anything when the message is not XML.
     *
     * <p>The exception text is fixed and carries no part of the message, so it is safe to log and
     * to show. The parse error that caused it is chained as the cause.
     *
     * @param parser the HAPI parser that will parse {@code message}, used only to decide whether
     *               the text is XML
     * @param message the raw message text
     * @throws HL7Exception if the message is XML and is malformed or has a {@code DOCTYPE}
     */
    public static void requireNoDocumentType(Parser parser, String message) throws HL7Exception {
        if (message == null || !XML_ENCODING.equals(parser.getEncoding(message))) {
            return;
        }
        try {
            // A streaming parse: only well-formedness and the DOCTYPE matter here, so no DOM is
            // built for a message HAPI is about to parse again. DefaultHandler throws on a fatal
            // error and stays quiet otherwise, so a rejected message is not also printed to stderr.
            SAXParser saxParser = XmlUtils.createSecureSAXParserFactory().newSAXParser();
            saxParser.parse(new InputSource(new StringReader(message)), new DefaultHandler());
        } catch (SAXException | IOException | ParserConfigurationException e) {
            throw new HL7Exception(
                    "HL7 XML message rejected: it must be well-formed and must not declare a document type", e);
        }
    }
}
