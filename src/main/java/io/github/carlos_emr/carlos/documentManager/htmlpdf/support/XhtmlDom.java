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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.support;

import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.Objects;

/**
 * The DOM Flying Saucer lays out, parsed from the XHTML that {@link XhtmlCleanup#serialize} wrote.
 *
 * <p>Flying Saucer can read the string itself, but it does so through
 * {@code SAXParserFactory.newInstance()} with nothing but its own entity resolver set. Since
 * standalone Xerces left the class path that is the JDK parser with the limits of
 * {@code $JAVA_HOME/conf/jaxp.properties}, which in JDK 25 are an element depth of 100,
 * 200 attributes on an element and 100,000 characters of entity references, where every
 * {@code &amp;}, {@code &lt;} and {@code &gt;} of ordinary text counts as one. A form, report or
 * letter past any of them failed the layout and printed as plain text, or not at all. The service
 * therefore parses the document here, with the JDK's own parser chosen explicitly and every limit
 * that matters stated, and hands Flying Saucer the result.</p>
 *
 * <h2>What is refused</h2>
 *
 * <p>The document is trusted only as far as the pipeline made it: it is what Jsoup parsed and the
 * rules rewrote, so it is well formed and carries no script. The parser still refuses everything a
 * document type could bring. A document type declaration is not allowed at all
 * ({@code disallow-doctype-decl}), so there is no internal subset, no entity declaration and no
 * external DTD; external general and parameter entities, external DTD and schema access and
 * XInclude are switched off as well, and the entity resolver answers anything with an empty
 * document. The protections overlap on purpose, as they do in {@link SvgReferences}.</p>
 *
 * <p>The serialiser keeps the author's {@code <!DOCTYPE html>} (or a legacy XHTML one) at the head
 * of the string. Flying Saucer has no quirks mode and its DOM never held a document type node, so
 * the declaration is simply taken off before the parse. One with an internal subset is left where
 * it is and makes the parse fail: Jsoup never writes one, so it did not come from the pipeline.
 * Without a DTD the bundled XHTML DTDs' attribute defaults ({@code colspan="1"} and the like) are
 * no longer added; none of them changes how a page is laid out.</p>
 *
 * <h2>The limits, and why these numbers</h2>
 *
 * <ul>
 *   <li><b>Element depth {@value #MAX_ELEMENT_DEPTH}.</b> Jsoup, the stage before, builds no tree
 *       deeper than 513 elements, so the service's own documents stay well inside; twice that
 *       leaves room for the wrappers the rules add and still stops a string that did not come
 *       from the pipeline. (Layout recursion is the tighter bound on deep documents: a stack
 *       overflow there is an attempt failure the ladder already handles.)</li>
 *   <li><b>{@value #MAX_ATTRIBUTES_PER_ELEMENT} attributes on an element.</b> The JDK's default
 *       in JDK 21. Word and form-builder exports carry dozens of attributes on an element,
 *       never thousands.</li>
 *   <li><b>{@value #MAX_ENTITY_REFERENCE_CHARACTERS} characters of entity references.</b> With no
 *       document type the only references are the five predefined ones, one character each, so
 *       this counts the escaped {@code & < > " '} of the text and nothing can expand. The number
 *       is the JDK's total-entity-size default in JDK 21; a document needs more than
 *       200 MB of markup to reach it, far more than a render can hold in memory.</li>
 * </ul>
 *
 * <p>The other JDK limits keep their defaults: with no document type nothing can declare or expand
 * an entity, and a name longer than 1,000 characters is not HTML.</p>
 *
 * <p>The class is stateless and logs nothing; a parser message may quote the document, so the
 * caller reports a failure by its class only.</p>
 *
 * @since 2026-11-12
 */
public final class XhtmlDom {

    /** {@code jdk.xml.maxElementDepth}: twice the 513 levels Jsoup builds. */
    static final int MAX_ELEMENT_DEPTH = 1_024;

    /** {@code jdk.xml.elementAttributeLimit}: the JDK's default in JDK 21. */
    static final int MAX_ATTRIBUTES_PER_ELEMENT = 10_000;

    /**
     * {@code jdk.xml.maxGeneralEntitySizeLimit} and {@code jdk.xml.totalEntitySizeLimit}: without a
     * document type, the number of predefined references ({@code &amp;} and the like) in the text.
     */
    static final int MAX_ENTITY_REFERENCE_CHARACTERS = 50_000_000;

    private static final String DOCTYPE_START = "<!DOCTYPE";

    private XhtmlDom() {
    }

    /**
     * Parses the serialised document.
     *
     * @param xhtml the XHTML {@link XhtmlCleanup#serialize} wrote
     * @return a namespace-aware DOM with no document type node, text and CDATA merged into text nodes
     * @throws SAXException if the document is not well formed, carries a document type with an
     *         internal subset, or is past a limit; the message may quote the document
     * @throws IOException never in practice: the source is a string
     * @throws ParserConfigurationException if the JDK parser does not support a required protection
     */
    public static Document parse(String xhtml) throws SAXException, IOException, ParserConfigurationException {
        Objects.requireNonNull(xhtml, "xhtml");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
        // Flying Saucer matches elements by local name and reads xmlns declarations: its own loader is
        // namespace-aware too.
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setXIncludeAware(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setAttribute("jdk.xml.maxElementDepth", String.valueOf(MAX_ELEMENT_DEPTH));
        factory.setAttribute("jdk.xml.elementAttributeLimit", String.valueOf(MAX_ATTRIBUTES_PER_ELEMENT));
        factory.setAttribute("jdk.xml.maxGeneralEntitySizeLimit", String.valueOf(MAX_ENTITY_REFERENCE_CHARACTERS));
        factory.setAttribute("jdk.xml.totalEntitySizeLimit", String.valueOf(MAX_ENTITY_REFERENCE_CHARACTERS));
        // The DOM is walked in full straight away, so building it lazily only costs memory. Merging
        // CDATA into text gives the tree Flying Saucer's own loader built from SAX events.
        factory.setFeature("http://apache.org/xml/features/dom/defer-node-expansion", false);
        factory.setCoalescing(true);
        factory.setExpandEntityReferences(true);
        factory.setIgnoringComments(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        // nosemgrep: java.lang.security.audit.xml-custom-entityresolver.xml-custom-entityresolver -- this resolver is a protection, not a loader: it answers every external entity and DTD with an empty document, so nothing is ever fetched
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        builder.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException e) {
                // Ignored, as Flying Saucer's loader ignored it: a warning does not make a document unusable.
            }

            @Override
            public void error(SAXParseException e) {
                // Ignored, as Flying Saucer's loader ignored it. Without a DTD or a schema the parser
                // has no recoverable errors to report: everything it refuses is fatal.
            }

            @Override
            public void fatalError(SAXParseException e) throws SAXException {
                throw e;
            }
        });
        return builder.parse(new InputSource(new StringReader(withoutDocumentType(xhtml))));
    }

    /**
     * Takes the document type declaration off the head of the document, when it has no internal subset.
     *
     * <p>Only white space, a byte order mark and an XML declaration may come before it, which is all the
     * serialiser can write there. Quoted identifiers are skipped as a whole, so a {@code >} or {@code [}
     * inside one ends nothing.</p>
     *
     * @param xhtml the serialised document
     * @return the document without its declaration, or unchanged when there is none at its head, it is
     *         not closed, or it opens an internal subset (which the parser then refuses)
     */
    static String withoutDocumentType(String xhtml) {
        int start = 0;
        int length = xhtml.length();
        if (start < length && xhtml.charAt(start) == '﻿') {
            start++;
        }
        start = skipWhitespace(xhtml, start);
        if (xhtml.startsWith("<?xml", start)) {
            int end = xhtml.indexOf("?>", start);
            if (end < 0) {
                return xhtml;
            }
            start = skipWhitespace(xhtml, end + 2);
        }
        if (!xhtml.startsWith(DOCTYPE_START, start)) {
            return xhtml;
        }
        char quote = 0;
        for (int i = start + DOCTYPE_START.length(); i < length; i++) {
            char c = xhtml.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '[') {
                return xhtml;
            } else if (c == '>') {
                return xhtml.substring(0, start) + xhtml.substring(i + 1);
            }
        }
        return xhtml;
    }

    private static int skipWhitespace(String text, int from) {
        int i = from;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }
}
