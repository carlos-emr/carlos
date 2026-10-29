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

import java.io.IOException;
import java.io.StringReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.parser.DefaultXMLParser;

/**
 * HAPI's HL7 v2 XML parser with an XML reader CARLOS controls (issue #5414).
 *
 * <p>HL7 v2 XML never needs a DTD, so any {@code DOCTYPE} is refused, which also rules out
 * internal entity expansion. External general and parameter entities, external DTD loading,
 * XInclude and JAXP external DTD/schema access are switched off as well, and an entity resolver
 * that refuses every lookup backs them up. Unlike HAPI's own {@code XMLUtils}, which logs a
 * warning and carries on when the platform parser rejects one of these settings, this parser
 * refuses to parse at all: a message is never read by a reader that is only partly protected.</p>
 *
 * <p>Only the step that turns text into a DOM is replaced; mapping the DOM onto HAPI's message
 * model, and encoding messages as XML, are HAPI's {@link DefaultXMLParser} unchanged. Use
 * {@link UntrustedHl7MessageParser} when a body may be either pipe or XML encoded.</p>
 */
public class HardenedHl7XmlParser extends DefaultXMLParser {

    /*
     * Without a handler, the platform parser prints "[Fatal Error] ..." to standard error (Tomcat's
     * catalina.out) for every rejected message, outside the application's logging. Errors and fatal
     * errors end the parse through the exception instead; warnings, which never stop a parse, are
     * dropped as the default handler effectively did.
     */
    private static final ErrorHandler REJECTING_ERROR_HANDLER = new ErrorHandler() {
        @Override
        public void warning(SAXParseException exception) {
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    };

    /** Creates a parser with HAPI's default context. */
    public HardenedHl7XmlParser() {
        super();
    }

    /**
     * Creates a parser that shares {@code context}, normally {@link Hl7Parsers#newParsingContext()}.
     */
    public HardenedHl7XmlParser(HapiContext context) {
        super(context);
    }

    /**
     * Parses {@code message} into a DOM with every entity and DTD protection applied.
     *
     * @throws HL7Exception if the XML is not well formed, declares a DOCTYPE, or a protection
     *                      cannot be applied by the platform XML parser
     */
    @Override
    protected synchronized Document parseStringIntoDocument(String message) throws HL7Exception {
        try {
            DocumentBuilder builder = harden(DocumentBuilderFactory.newInstance()).newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("External entities are not allowed in HL7 XML messages");
            });
            builder.setErrorHandler(REJECTING_ERROR_HANDLER);
            // Reading from a String means the XML declaration's encoding is ignored: the text
            // has already been decoded by the upload path.
            return builder.parse(new InputSource(new StringReader(message)));
        } catch (ParserConfigurationException e) {
            throw new HL7Exception("HL7 XML parsing is unavailable: the XML parser cannot be secured", e);
        } catch (SAXException | IOException e) {
            // The cause can quote message text, so it is attached for the log but not repeated
            // in this message, which callers may show to the uploader.
            throw new HL7Exception("HL7 XML message rejected: it is not well-formed or it declares a DTD or entities", e);
        }
    }

    /**
     * Applies every protection to {@code factory}, failing rather than returning a factory that is
     * only partly protected.
     *
     * @param factory a newly created platform factory
     * @return the same factory, configured
     * @throws ParserConfigurationException if the platform parser rejects any protection
     */
    static DocumentBuilderFactory harden(DocumentBuilderFactory factory) throws ParserConfigurationException {
        // HAPI's DOM lookups are namespace-qualified (urn:hl7-org:v2xml).
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (IllegalArgumentException e) {
            ParserConfigurationException failure = new ParserConfigurationException(
                    "XML parser does not support JAXP external access limits");
            failure.initCause(e);
            throw failure;
        }
        return factory;
    }
}
