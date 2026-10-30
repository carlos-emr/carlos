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

import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.DataUris;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.ImageDecoding;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Element;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Removes from an SVG everything that points anywhere but at the SVG itself, so that drawing it
 * can never make a request or read a file.
 *
 * <p>Flying Saucer draws SVG with Batik, which follows {@code href}, {@code xlink:href}, CSS
 * {@code url()} and {@code @import} and, by default, may load from the same host and from
 * {@code file:} URLs; a hospital report or a form can carry an SVG whose author did not mean that
 * (or did). What stays: a fragment ({@code #id}, a reference inside the document) and a base64
 * {@code data:} URI of a PNG, JPEG or GIF image whose header can be read and declares at most
 * {@link #MAX_NESTED_RASTER_PIXELS} pixels (Batik decodes such an image in full: a 100 KB PNG can declare
 * 900 million pixels and take 3.6 GB). A {@code data:} URI of anything else goes: Batik reads
 * the content, not the media type, and an SVG inside a {@code data:} URI is a second document whose own
 * references this cleaner never sees (a {@code use} of one of its elements would draw a file from
 * anywhere). What goes:</p>
 *
 * <ul>
 *   <li>an {@code href}, {@code xlink:href} or {@code src} attribute with any other value, and the whole
 *       element when it exists to load something ({@code image}, {@code use}, {@code feImage},
 *       {@code cursor}, {@code font-face-uri}, {@code mpath}, {@code tref});</li>
 *   <li>{@code script} and {@code foreignObject} elements (the latter can hold HTML with its own
 *       references);</li>
 *   <li>CSS, in a {@code style} attribute, a {@code <style>} element or a presentation attribute:
 *       {@code @import} rules (string or {@code url()} form), and any {@code url()} that is not a
 *       fragment or a raster {@code data:} URI, which becomes {@code none}; CSS that contains a backslash
 *       (an escape could spell {@code url(} in a way the scan misses) is emptied;</li>
 *   <li>processing instructions such as {@code <?xml-stylesheet?>}, and the document type, so no
 *       external DTD is ever named.</li>
 * </ul>
 *
 * <p>A standalone SVG (a file or a {@code data:} payload) is parsed with a hardened XML parser: secure
 * processing, no external entities or DTD, no XInclude, a limited element depth, and a resolver that
 * opens nothing. An SVG that cannot be parsed under those settings is unusable. An inline
 * {@code <svg>} is part of an HTML document that is already parsed and gets the same rules applied to
 * its elements in place.</p>
 *
 * @since 2026-10-29
 */
public final class SvgReferences {

    /** The deepest element nesting accepted in a standalone SVG; a drawing never nears it. */
    private static final String MAX_ELEMENT_DEPTH = "512";

    /** Elements that exist to load something: removed whole when what they point at is not local. */
    private static final Set<String> LOADING_ELEMENTS =
            Set.of("image", "use", "feimage", "cursor", "font-face-uri", "mpath", "tref");

    /** Elements removed whatever they point at. */
    private static final Set<String> REMOVED_ELEMENTS = Set.of("script", "foreignobject");

    /** Attributes that name a resource. */
    private static final Set<String> REFERENCE_ATTRIBUTES = Set.of("href", "src");

    /**
     * What the base64 text of a PNG, a JPEG and a GIF begins with ({@code \x89PNG\r\n\x1a\n}, {@code FF D8 FF},
     * {@code GIF8}). Batik tells an image from an SVG by its content, so the payload decides whether a
     * {@code data:} URI is a picture, whatever media type it declares.
     */
    private static final List<String> RASTER_BASE64_PREFIXES = List.of("iVBORw0KGg", "/9j/", "R0lGOD");

    /**
     * The most pixels a raster image inside an SVG may declare. Batik decodes it whole, four bytes to the pixel,
     * and the SVG may hold many; a logo or a stamp is a few hundred thousand pixels, a photograph a few million.
     */
    public static final long MAX_NESTED_RASTER_PIXELS = 4_000_000L;

    /** The most bytes of one inline image: the SVG itself may not be larger. */
    private static final long MAX_NESTED_RASTER_BYTES = 16L << 20;

    private SvgReferences() {
    }

    /**
     * The cleaned SVG and whether anything had to be removed. The bytes are copied in and out, so a
     * holder cannot change what another reads.
     *
     * @param svg the cleaned document, UTF-8
     * @param removedReferences {@code true} if at least one reference was removed
     */
    public record Cleaned(byte[] svg, boolean removedReferences) {

        /** Keeps a copy of the bytes. */
        public Cleaned {
            svg = svg.clone();
        }

        /** {@return a copy of the cleaned document} */
        @Override
        public byte[] svg() {
            return svg.clone();
        }

        /** Equal when the documents hold the same bytes (a record would compare the array's identity). */
        @Override
        public boolean equals(Object other) {
            return other instanceof Cleaned that && removedReferences == that.removedReferences
                    && Arrays.equals(svg, that.svg);
        }

        @Override
        public int hashCode() {
            return 31 * Boolean.hashCode(removedReferences) + Arrays.hashCode(svg);
        }

        /** The size, never the document: it is a patient's record. */
        @Override
        public String toString() {
            return "Cleaned[svgBytes=" + svg.length + ", removedReferences=" + removedReferences + "]";
        }
    }

    // ---- standalone SVG ---------------------------------------------------------------------

    /**
     * Cleans a standalone SVG.
     *
     * @param svg the bytes of an SVG file or {@code data:} payload
     * @return the cleaned SVG, or empty if it cannot be parsed with the hardened parser
     */
    public static Optional<Cleaned> clean(byte[] svg) {
        Objects.requireNonNull(svg, "svg");
        try {
            Document document = parse(svg);
            boolean removed = clean(document);
            return Optional.of(new Cleaned(serialize(document), removed));
        } catch (ParserConfigurationException | SAXException | IOException | TransformerException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Document parse(byte[] svg) throws ParserConfigurationException, SAXException, IOException {
        // The protections overlap on purpose: the parser features, the JAXP access properties and the
        // entity resolver each stop an external DTD or entity from being read without the others.
        // load-external-dtd=false is also what lets the usual SVG 1.1 document type (an external DTD
        // that is never needed) parse at all; the access property alone would refuse such a file.
        DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", MAX_ELEMENT_DEPTH);
        factory.setXIncludeAware(false);
        // Internal entities are expanded (secure processing bounds the expansion) so the document can be
        // written back without its document type; external ones are never loaded.
        factory.setExpandEntityReferences(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        // nosemgrep: java.lang.security.audit.xml-custom-entityresolver.xml-custom-entityresolver -- this resolver is a protection, not a loader: it answers every external entity and DTD with an empty document, so nothing is ever fetched
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        builder.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException e) {
                // Ignored: warnings do not make a document unusable.
            }

            @Override
            public void error(SAXParseException e) throws SAXException {
                throw e;
            }

            @Override
            public void fatalError(SAXParseException e) throws SAXException {
                throw e;
            }
        });
        return builder.parse(new ByteArrayInputStream(svg));
    }

    private static byte[] serialize(Document document) throws TransformerException {
        TransformerFactory factory = TransformerFactory.newDefaultInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(document), new StreamResult(out));
        return out.toByteArray();
    }

    /** Walks the document in order without recursion, removing and rewriting as it goes. */
    private static boolean clean(Document document) {
        boolean removed = false;
        Node node = document.getFirstChild();
        while (node != null) {
            Node next;
            switch (node.getNodeType()) {
                case Node.DOCUMENT_TYPE_NODE, Node.PROCESSING_INSTRUCTION_NODE -> {
                    next = nextAfter(node);
                    node.getParentNode().removeChild(node);
                    removed |= node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE;
                    node = next;
                    continue;
                }
                case Node.ELEMENT_NODE -> {
                    org.w3c.dom.Element element = (org.w3c.dom.Element) node;
                    if (isRemoved(localName(element)) || (isLoading(localName(element)) && hasExternalReference(element))) {
                        next = nextAfter(node);
                        node.getParentNode().removeChild(node);
                        removed = true;
                        node = next;
                        continue;
                    }
                    removed |= cleanAttributes(element);
                    if (localName(element).equals("style")) {
                        removed |= cleanStyleText(element);
                    }
                }
                default -> {
                }
            }
            node = nextInDocumentOrder(node);
        }
        return removed;
    }

    private static boolean hasExternalReference(org.w3c.dom.Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            if (REFERENCE_ATTRIBUTES.contains(localName(attribute)) && !isLocal(attribute.getNodeValue())) {
                return true;
            }
        }
        return false;
    }

    private static boolean cleanAttributes(org.w3c.dom.Element element) {
        boolean changed = false;
        List<Node> attributes = new ArrayList<>();
        NamedNodeMap map = element.getAttributes();
        for (int i = 0; i < map.getLength(); i++) {
            attributes.add(map.item(i));
        }
        for (Node attribute : attributes) {
            String value = attribute.getNodeValue();
            if (REFERENCE_ATTRIBUTES.contains(localName(attribute)) && !isLocal(value)) {
                element.getAttributes().removeNamedItem(attribute.getNodeName());
                changed = true;
            } else if (containsAscii(value, "url(") || containsAscii(value, "@import")
                    || (attribute.getNodeName().equals("style") && value.indexOf('\\') >= 0)) {
                Css cleaned = cleanCss(value);
                if (cleaned.changed()) {
                    attribute.setNodeValue(cleaned.text());
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean cleanStyleText(org.w3c.dom.Element style) {
        boolean changed = false;
        for (Node child = style.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                Css cleaned = cleanCss(child.getNodeValue());
                if (cleaned.changed()) {
                    child.setNodeValue(cleaned.text());
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static String localName(Node node) {
        String local = node.getLocalName();
        String name = local != null ? local : node.getNodeName();
        int colon = name.indexOf(':');
        return lower(colon >= 0 ? name.substring(colon + 1) : name);
    }

    /** The node after this one in document order, not entering it. */
    private static Node nextAfter(Node node) {
        for (Node current = node; current != null; current = current.getParentNode()) {
            if (current.getNextSibling() != null) {
                return current.getNextSibling();
            }
        }
        return null;
    }

    private static Node nextInDocumentOrder(Node node) {
        return node.getFirstChild() != null ? node.getFirstChild() : nextAfter(node);
    }

    // ---- inline SVG -------------------------------------------------------------------------

    /**
     * Cleans an inline {@code <svg>} element of an HTML document, in place, by the same rules.
     *
     * @param svg the element; it and its descendants are changed
     * @return {@code true} if at least one reference was removed
     */
    public static boolean clean(Element svg) {
        Objects.requireNonNull(svg, "svg");
        boolean removed = false;
        for (Element element : svg.getAllElements()) {
            if (element.ownerDocument() == null) {
                continue; // inside an element removed a moment ago
            }
            String name = lower(element.normalName());
            if (element != svg && (isRemoved(name) || (isLoading(name) && hasExternalReference(element)))) {
                element.remove();
                removed = true;
                continue;
            }
            removed |= cleanAttributes(element);
            if (name.equals("style")) {
                Css cleaned = cleanCss(element.data().isEmpty() ? element.text() : element.data());
                if (cleaned.changed()) {
                    element.empty();
                    element.appendChild(new org.jsoup.nodes.DataNode(cleaned.text()));
                    removed = true;
                }
            }
        }
        return removed;
    }

    private static boolean hasExternalReference(Element element) {
        for (Attribute attribute : element.attributes()) {
            if (REFERENCE_ATTRIBUTES.contains(attributeLocalName(attribute.getKey())) && !isLocal(attribute.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static boolean cleanAttributes(Element element) {
        boolean changed = false;
        List<Attribute> attributes = new ArrayList<>();
        element.attributes().forEach(attributes::add);
        for (Attribute attribute : attributes) {
            String value = attribute.getValue();
            String key = attribute.getKey();
            if (REFERENCE_ATTRIBUTES.contains(attributeLocalName(key)) && !isLocal(value)) {
                element.removeAttr(key);
                changed = true;
            } else if (containsAscii(value, "url(") || containsAscii(value, "@import")
                    || (key.equals("style") && value.indexOf('\\') >= 0)) {
                Css cleaned = cleanCss(value);
                if (cleaned.changed()) {
                    element.attr(key, cleaned.text());
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static String attributeLocalName(String key) {
        int colon = key.indexOf(':');
        return lower(colon >= 0 ? key.substring(colon + 1) : key);
    }

    // ---- shared rules -----------------------------------------------------------------------

    private static boolean isRemoved(String lowerCaseLocalName) {
        return REMOVED_ELEMENTS.contains(lowerCaseLocalName);
    }

    private static boolean isLoading(String lowerCaseLocalName) {
        return LOADING_ELEMENTS.contains(lowerCaseLocalName);
    }

    /** A fragment inside the document, or an inline raster image: the only references that stay. */
    private static boolean isLocal(String reference) {
        String value = reference.strip();
        return value.startsWith("#") || isRasterDataUri(value);
    }

    /**
     * A base64 {@code data:} URI whose payload begins like a PNG, a JPEG or a GIF and whose header, read the way
     * the pipeline reads every image, declares a size within {@link #MAX_NESTED_RASTER_PIXELS}. Anything else
     * (an SVG under any media type, text, a URI without base64, an image that cannot be read or is too large)
     * is a document Batik would load and read the references of on its own, or an image it would decode in full.
     */
    private static boolean isRasterDataUri(String value) {
        if (!regionMatchesAscii(value, 0, "data:")) {
            return false;
        }
        int comma = value.indexOf(',');
        if (comma < 0 || !endsWithAscii(value, comma, ";base64")) {
            return false;
        }
        for (String prefix : RASTER_BASE64_PREFIXES) {
            if (value.startsWith(prefix, comma + 1)) {
                return DataUris.decodeBase64(value, MAX_NESTED_RASTER_BYTES)
                        .flatMap(ImageDecoding::readHeader)
                        .filter(header -> header.pixels() <= MAX_NESTED_RASTER_PIXELS)
                        .isPresent();
            }
        }
        return false;
    }

    private record Css(String text, boolean changed) {
    }

    /**
     * Removes {@code @import} rules and turns every {@code url()} that is not local into {@code none}.
     * One pass over the text: a CSS payload can be as large as an SVG file may be (16 MB), and a scan
     * that starts again at every {@code url(} would take hours on one without a closing parenthesis.
     */
    private static Css cleanCss(String css) {
        if (css.indexOf('\\') >= 0) {
            // A CSS escape can spell "url(" without those characters; do not try to read it.
            return new Css("", true);
        }
        int length = css.length();
        StringBuilder out = null;
        int copied = 0;
        int i = 0;
        while (i < length) {
            char c = css.charAt(i);
            if (c == '@' && regionMatchesAscii(css, i, "@import")) {
                int end = importEnd(css, i + "@import".length());
                out = copyTo(out, css, copied, i);
                copied = end;
                i = end;
            } else if ((c == 'u' || c == 'U') && regionMatchesAscii(css, i, "url(")) {
                int[] token = urlToken(css, i + "url(".length());
                if (isLocal(css.substring(token[0], token[1]))) {
                    i = token[2];
                } else {
                    out = copyTo(out, css, copied, i).append("none");
                    copied = token[2];
                    i = token[2];
                }
            } else {
                i++;
            }
        }
        if (out == null) {
            return new Css(css, false);
        }
        return new Css(out.append(css, copied, length).toString(), true);
    }

    private static StringBuilder copyTo(StringBuilder out, String css, int from, int to) {
        StringBuilder builder = out != null ? out : new StringBuilder(css.length());
        return builder.append(css, from, to);
    }

    /**
     * Where a {@code url(} that opens just before {@code open} has its content and where the token ends:
     * {content start, content end, token end}. A token that never closes runs to the end of the text,
     * which is also where the scan then stops.
     */
    private static int[] urlToken(String css, int open) {
        int length = css.length();
        int start = skipWhitespace(css, open);
        if (start < length && (css.charAt(start) == '"' || css.charAt(start) == '\'')) {
            int close = css.indexOf(css.charAt(start), start + 1);
            if (close >= 0) {
                int after = skipWhitespace(css, close + 1);
                if (after < length && css.charAt(after) == ')') {
                    return new int[]{start + 1, close, after + 1};
                }
            }
        }
        // Unquoted, or a quote that does not close properly: everything up to the next ')' is the reference.
        int paren = css.indexOf(')', start);
        return paren < 0 ? new int[]{start, length, length} : new int[]{start, paren, paren + 1};
    }

    /** The end of an {@code @import} rule that starts after the keyword: its url or string, its media list, its semicolon. */
    private static int importEnd(String css, int from) {
        int length = css.length();
        int i = skipWhitespace(css, from);
        if (regionMatchesAscii(css, i, "url(")) {
            i = urlToken(css, i + "url(".length())[2];
        } else if (i < length && (css.charAt(i) == '"' || css.charAt(i) == '\'')) {
            int close = css.indexOf(css.charAt(i), i + 1);
            i = close < 0 ? length : close + 1;
        }
        while (i < length) {
            char c = css.charAt(i);
            if (c == ';') {
                return i + 1;
            }
            if (c == '{' || c == '}') {
                return i;
            }
            i++;
        }
        return length;
    }

    private static int skipWhitespace(String text, int from) {
        int i = from;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    // ---- ASCII text -------------------------------------------------------------------------
    // CSS keywords, SVG names and URI schemes are ASCII. Folding only ASCII letters is what they mean, and
    // it leaves no Unicode case mapping (a dotted capital I, a Kelvin sign) to confuse a filter.

    /** Whether {@code text} holds {@code lowerNeedle}, which is lower-case ASCII, at {@code at}, whatever the case of its ASCII letters. */
    private static boolean regionMatchesAscii(String text, int at, String lowerNeedle) {
        if (at < 0 || at + lowerNeedle.length() > text.length()) {
            return false;
        }
        for (int i = 0; i < lowerNeedle.length(); i++) {
            char c = text.charAt(at + i);
            if (c >= 'A' && c <= 'Z') {
                c += 'a' - 'A';
            }
            if (c != lowerNeedle.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /** Whether the text before {@code end} finishes with {@code lowerSuffix}, whatever the case of its ASCII letters. */
    private static boolean endsWithAscii(String text, int end, String lowerSuffix) {
        return regionMatchesAscii(text, end - lowerSuffix.length(), lowerSuffix);
    }

    private static boolean containsAscii(String text, String lowerNeedle) {
        for (int i = 0; i + lowerNeedle.length() <= text.length(); i++) {
            if (regionMatchesAscii(text, i, lowerNeedle)) {
                return true;
            }
        }
        return false;
    }

    /** The name with its ASCII capitals made lower case. */
    private static String lower(String name) {
        StringBuilder folded = null;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                if (folded == null) {
                    folded = new StringBuilder(name);
                }
                folded.setCharAt(i, (char) (c + ('a' - 'A')));
            }
        }
        return folded == null ? name : folded.toString();
    }
}
