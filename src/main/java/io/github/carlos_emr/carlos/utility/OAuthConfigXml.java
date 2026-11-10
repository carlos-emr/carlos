/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility;

import java.io.StringReader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/** Shared safe XML mechanics; the application-client and inbound OAuth configuration schemas stay distinct. */
public final class OAuthConfigXml {
    private OAuthConfigXml() {}

    public static Element root(Object source) {
        if (source == null) throw invalid();
        Document document;
        if (source instanceof Document existing) {
            document = existing;
        } else {
            final javax.xml.parsers.DocumentBuilder builder;
            try {
                var factory = XmlUtils.createSecureDocumentBuilderFactory();
                // Keep critical policy explicit at this parser boundary as well as in the shared factory.
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                builder = factory.newDocumentBuilder();
            } catch (javax.xml.parsers.ParserConfigurationException | IllegalArgumentException unavailable) {
                throw new IllegalStateException("OAuth XML parser protections are unavailable");
            }
            try {
                builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                    @Override
                    public void error(SAXParseException error) throws SAXException {
                        throw new SAXException("Invalid OAuth configuration XML");
                    }
                    @Override
                    public void fatalError(SAXParseException error) throws SAXException {
                        throw new SAXException("Invalid OAuth configuration XML");
                    }
                });
                document = builder.parse(new InputSource(new StringReader(source.toString())));
            } catch (java.io.IOException | SAXException invalid) {
                // Parser diagnostics may contain credentials. Do not attach them to caller-visible failures.
                throw invalid();
            }
        }
        if (document.getDoctype() != null || document.getDocumentElement() == null) throw invalid();
        return document.getDocumentElement();
    }

    /** Direct leaf children only: no partial extraction from malformed, duplicate or nested credential fields. */
    public static String text(Element root, String name) {
        String found = null;
        boolean present = false;
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != Node.ELEMENT_NODE
                    || !(name.equals(child.getLocalName()) || name.equals(child.getNodeName()))) continue;
            if (present) throw invalid();
            present = true;
            for (Node value = child.getFirstChild(); value != null; value = value.getNextSibling()) {
                if (value.getNodeType() == Node.ELEMENT_NODE || value.getNodeType() == Node.ENTITY_REFERENCE_NODE) {
                    throw invalid();
                }
            }
            found = child.getTextContent();
        }
        return found;
    }

    public static String trimmedText(Element root, String name) {
        String text = text(root, name);
        return text == null ? null : text.trim();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid OAuth configuration XML");
    }
}
