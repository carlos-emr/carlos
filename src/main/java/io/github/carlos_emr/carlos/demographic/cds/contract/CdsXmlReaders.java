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
package io.github.carlos_emr.carlos.demographic.cds.contract;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Objects;
import java.util.function.Function;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/** Maintained-JDK XML security and bounded markup; transport encodings remain directional policy. */
public final class CdsXmlReaders {
    private CdsXmlReaders() {}

    public static XMLReader reader(
            int maxNameCharacters,
            int maxNamespaceUriCharacters,
            int maxDepth,
            int maxAttributes,
            int maxNamespaceAliases)
            throws ParserConfigurationException, SAXException {
        if (maxNameCharacters < 1
                || maxNamespaceUriCharacters < 1
                || maxDepth < 1
                || maxAttributes < 1
                || maxNamespaceAliases < 1) {
            throw new IllegalArgumentException("CDS_XML_READER_LIMITS_INVALID");
        }
        // Use the maintained JDK implementation with explicit external-resource controls.
        // Audit: throw-only resolver returns no InputSource and performs no I/O; DTD/entity
        // controls remain fail-closed.
        SAXParserFactory factory = SAXParserFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        XMLReader reader = factory.newSAXParser().getXMLReader();
        // Let CDS classify the first policy violation before the JDK's finite backstop.
        reader.setProperty(
                "jdk.xml.maxXMLNameLimit",
                String.valueOf(
                        Math.min(
                                Integer.MAX_VALUE,
                                1L + Math.max(maxNameCharacters, maxNamespaceUriCharacters))));
        reader.setProperty(
                "jdk.xml.maxElementDepth",
                String.valueOf(Math.min(Integer.MAX_VALUE, 1L + maxDepth)));
        // The JDK counts xmlns declarations as attributes (default ceiling 200); CDS
        // separately bounds ordinary attributes and document-wide namespace aliases.
        reader.setProperty(
                "jdk.xml.elementAttributeLimit",
                String.valueOf(
                        Math.min(Integer.MAX_VALUE, 1L + maxAttributes + maxNamespaceAliases)));
        // The JDK also counts every predefined reference (&amp;, &lt;, ...) in the document
        // entity against these entity-size ceilings (default 100,000), so ordinary escaped
        // clinical text would be refused part way through a large source. DOCTYPE is refused
        // above, leaving only the five one-character predefined entities: no expansion is
        // possible and maxSourceBytes already bounds the count. No finite int ceiling fits
        // every permitted source size, so 0 (no limit) is the only lossless setting here.
        reader.setProperty("jdk.xml.maxGeneralEntitySizeLimit", "0");
        reader.setProperty("jdk.xml.totalEntitySizeLimit", "0");
        reader.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
        reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        // DOCTYPE and external entity features are disabled above; this resolver always refuses.
        // nosemgrep: java.lang.security.audit.xml-custom-entityresolver.xml-custom-entityresolver
        reader.setEntityResolver(
                (publicId, systemId) -> {
                    throw new SAXException("CDS_EXTERNAL_ENTITY_BLOCKED");
                });
        return reader;
    }

    public static Reader markupReader(
            Reader input, int maximum, Function<String, IOException> failures) {
        if (maximum < 1) throw new IllegalArgumentException("CDS_XML_MARKUP_LIMIT_INVALID");
        return new MarkupReader(
                Objects.requireNonNull(input), maximum, Objects.requireNonNull(failures));
    }

    private static final class MarkupReader extends FilterReader {
        private final int maximum;
        private final Function<String, IOException> failures;
        private int state;
        private int markupCharacters;
        private char quote;
        private char previous;
        private char beforePrevious;
        private final StringBuilder prefix = new StringBuilder(9);

        MarkupReader(Reader input, int maximum, Function<String, IOException> failures) {
            super(input);
            this.maximum = maximum;
            this.failures = failures;
        }

        @Override
        public int read(char[] buffer, int offset, int count) throws IOException {
            int n = super.read(buffer, offset, count);
            for (int i = 0; i < n; i++) inspect(buffer[offset + i]);
            return n;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) inspect((char) value);
            return value;
        }

        private void inspect(char value) throws IOException {
            if (state == 0) {
                if (value == '<') {
                    state = 1;
                    markupCharacters = 1;
                    quote = 0;
                    prefix.setLength(0);
                    prefix.append(value);
                }
            } else if (state == 2) {
                if (beforePrevious == ']' && previous == ']' && value == '>') state = 0;
            } else {
                if (++markupCharacters > maximum) throw failures.apply("CDS_XML_MARKUP_LIMIT");
                if (state == 3) {
                    if (beforePrevious == '-' && previous == '-' && value == '>') state = 0;
                } else if (state == 4) {
                    if (previous == '?' && value == '>') state = 0;
                } else {
                    if (prefix.length() < 9) {
                        prefix.append(value);
                        if (prefix.toString().equals("<![CDATA[")) state = 2;
                        else if (prefix.toString().equals("<!--")) state = 3;
                        else if (prefix.toString().equals("<?")) state = 4;
                    }
                    if (state == 1) {
                        if (quote != 0) {
                            if (value == quote) quote = 0;
                        } else if (value == '\'' || value == '"') quote = value;
                        else if (value == '>') state = 0;
                    }
                }
            }
            beforePrevious = previous;
            previous = value;
        }
    }
}
