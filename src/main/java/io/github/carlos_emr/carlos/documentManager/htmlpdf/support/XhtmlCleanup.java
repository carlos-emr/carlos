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

import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeFilter;
import org.jsoup.select.NodeTraversor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The rules that turn an HTML document into valid, printable XHTML for Flying Saucer (steps a, b,
 * d to g, i and m of the render pipeline). Each rule is a static method that changes the Jsoup
 * {@link Document} it is given; the composer applies them in pipeline order.
 *
 * <p>Flying Saucer reads the document as XML with a namespace-aware parser, so one construct that
 * is legal HTML and illegal XML (an XML-invalid character, a comment with two hyphens, a prefix
 * nobody declared) fails the whole render. {@link #serialize(Document)} therefore guarantees
 * well-formed output whatever the input, and the other rules keep Flying Saucer's layout close to a
 * browser's for the markup real clinical documents contain.</p>
 *
 * <p>Nothing here logs: a document's text can identify a patient.</p>
 *
 * @since 2026-10-29
 */
public final class XhtmlCleanup {

    /**
     * The class the injected stylesheet turns into repeated table headers and paginated rows
     * (step g). It is added to a table only when pagination cannot break a row span.
     */
    public static final String PAGINATE_CLASS = "carlos-pdf-paginate";

    /**
     * The largest {@code colspan} kept, as in the HTML table model. Flying Saucer allocates in
     * proportion to a span: a {@code colspan} of 1,000,000 takes 0.4 s to lay out and one of
     * 99,999,999,999 exhausts a 512 MB heap, which on a server started with
     * {@code -XX:+ExitOnOutOfMemoryError} restarts the whole application.
     */
    static final int MAX_COLSPAN = 1000;

    /** {@code position: fixed} in an inline style, but not {@code background-position} or the like. */
    private static final Pattern FIXED_POSITION =
            Pattern.compile("(?i)((?<![\\w-])position\\s*:\\s*)fixed(?![\\w-])");

    /** An inline {@code list-style-type} or {@code list-style}, either of which wins over {@code <ol type>}. */
    private static final Pattern LIST_STYLE = Pattern.compile("(?i)(?<![\\w-])list-style(-type)?\\s*:");

    /** The {@code type} values of {@code <ol>} (case-sensitive, as in HTML) and their CSS equivalents. */
    private static final Map<String, String> LIST_STYLE_TYPES = Map.of(
            "a", "lower-alpha", "A", "upper-alpha", "i", "lower-roman", "I", "upper-roman", "1", "decimal");

    /** The one prefix, besides {@code xml} and {@code xmlns}, that SVG markup uses for attributes ({@code xlink:href}). */
    private static final String XLINK_PREFIX = "xlink";
    private static final String XLINK_NAMESPACE = "http://www.w3.org/1999/xlink";

    private XhtmlCleanup() {
    }

    // ---- a. scripts and comments ------------------------------------------------------------

    /**
     * Removes every {@code <script>} element and every comment node (step a). Scripts cannot run,
     * and comments never render; an HTML comment may legally contain two hyphens while an XML
     * comment may not, so one such comment would otherwise fail the whole render.
     *
     * @param document the document to clean
     */
    public static void removeScriptsAndComments(Document document) {
        Objects.requireNonNull(document, "document");
        document.select("script").remove();
        NodeTraversor.filter(new NodeFilter() {
            @Override
            public FilterResult head(org.jsoup.nodes.Node node, int depth) {
                return node instanceof Comment ? FilterResult.REMOVE : FilterResult.CONTINUE;
            }

            @Override
            public FilterResult tail(org.jsoup.nodes.Node node, int depth) {
                return FilterResult.CONTINUE;
            }
        }, document);
    }

    // ---- b. invisible and XML-invalid characters --------------------------------------------

    /**
     * Removes, from every text node and from every {@code value} attribute, the characters that
     * must not reach the printed page or the XML parser (step b):
     *
     * <ul>
     *   <li>soft hyphens (U+00AD), which Flying Saucer prints as visible hyphens;</li>
     *   <li>invisible bidirectional formatting characters (U+200E, U+200F, U+061C, U+202A to U+202E,
     *       U+2066 to U+2069);</li>
     *   <li>characters XML 1.0 forbids: the control characters U+0000 to U+0008, U+000B, U+000C and
     *       U+000E to U+001F, the noncharacters U+FFFE and U+FFFF, and unpaired surrogates, as
     *       pasted into form values or hospital text. The document prints without them.</li>
     * </ul>
     *
     * Zero-width joiners (U+200C, U+200D) stay: Arabic and Indic shaping needs them.
     *
     * @param document the document to clean
     */
    public static void removeInvisibleCharacters(Document document) {
        Objects.requireNonNull(document, "document");
        document.traverse((node, depth) -> {
            if (node instanceof TextNode text) {
                String whole = text.getWholeText();
                String clean = strip(whole, true);
                if (!clean.equals(whole)) {
                    text.text(clean);
                }
            } else if (node instanceof Element element && element.hasAttr("value")) {
                String value = element.attr("value");
                String clean = strip(value, true);
                if (!clean.equals(value)) {
                    element.attr("value", clean);
                }
            }
        });
    }

    /**
     * Removes the invisible characters and the XML-invalid ones from a string.
     *
     * @param text the text
     * @param invisibleToo {@code false} removes only the characters XML forbids
     * @return the text itself when nothing needed removing
     */
    private static String strip(String text, boolean invisibleToo) {
        int length = text.length();
        int first = firstRemovable(text, invisibleToo);
        if (first < 0) {
            return text;
        }
        StringBuilder kept = new StringBuilder(length);
        kept.append(text, 0, first);
        for (int i = first; i < length; i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < length && Character.isLowSurrogate(text.charAt(i + 1))) {
                    kept.append(c).append(text.charAt(++i));
                }
                // An unpaired surrogate is not a character: dropped.
            } else if (!Character.isLowSurrogate(c) && !isRemovable(c, invisibleToo)) {
                kept.append(c);
            }
        }
        return kept.toString();
    }

    private static int firstRemovable(String text, boolean invisibleToo) {
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < length && Character.isLowSurrogate(text.charAt(i + 1))) {
                    i++;
                } else {
                    return i;
                }
            } else if (Character.isLowSurrogate(c) || isRemovable(c, invisibleToo)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isRemovable(char c, boolean invisibleToo) {
        boolean xmlInvalid = (c < 0x20 && c != '\t' && c != '\n' && c != '\r') || c == 0xFFFE || c == 0xFFFF;
        return xmlInvalid || (invisibleToo && isInvisible(c));
    }

    private static boolean isInvisible(char c) {
        return c == 0xAD || c == 0x200E || c == 0x200F || c == 0x061C
                || (c >= 0x202A && c <= 0x202E) || (c >= 0x2066 && c <= 0x2069);
    }

    // ---- d. fixed positioning ---------------------------------------------------------------

    /**
     * Rewrites {@code position: fixed} in an inline {@code style} to {@code position: absolute}
     * (step d). Flying Saucer repeats a fixed element on every page, where it overprints the body
     * text; an absolute one is printed once.
     *
     * @param document the document to clean
     */
    public static void rewriteFixedPositioning(Document document) {
        Objects.requireNonNull(document, "document");
        for (Element element : document.select("[style]")) {
            String style = element.attr("style");
            String rewritten = FIXED_POSITION.matcher(style).replaceAll("$1absolute");
            if (!rewritten.equals(style)) {
                element.attr("style", rewritten);
            }
        }
    }

    // ---- e. ordered-list types --------------------------------------------------------------

    /**
     * Sets {@code list-style-type} from {@code <ol type="...">} (step e), which Flying Saucer
     * ignores: {@code a} gives {@code lower-alpha}, {@code A} {@code upper-alpha}, {@code i}
     * {@code lower-roman}, {@code I} {@code upper-roman} and {@code 1} {@code decimal}. A list whose
     * inline style already sets {@code list-style-type} or {@code list-style} is left alone, as the
     * author's style wins over the attribute in a browser.
     *
     * @param document the document to clean
     */
    public static void mapOrderedListTypes(Document document) {
        Objects.requireNonNull(document, "document");
        for (Element list : document.select("ol[type]")) {
            String listStyleType = LIST_STYLE_TYPES.get(list.attr("type"));
            if (listStyleType == null) {
                continue;
            }
            String style = list.attr("style");
            if (LIST_STYLE.matcher(style).find()) {
                continue;
            }
            String trimmed = style.strip();
            while (trimmed.endsWith(";")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1).stripTrailing();
            }
            list.attr("style", trimmed.isEmpty()
                    ? "list-style-type: " + listStyleType
                    : trimmed + "; list-style-type: " + listStyleType);
        }
    }

    // ---- f. spans ---------------------------------------------------------------------------

    /**
     * Clamps {@code rowspan} to the rows its row group has left (step f). A {@code td} or {@code th}
     * whose {@code rowspan} is 0, or larger than the number of rows from its row to the end of its
     * row group ({@code thead}, {@code tbody}, {@code tfoot}, or the table itself), gets that number,
     * as in the HTML table model. Flying Saucer takes the value literally: a cell spanning past the
     * end of its group is drawn over the table that follows, and an absurd value (a rowspan of
     * 99,999,999,999 exhausts the heap) is a denial of service.
     *
     * @param document the document to clean
     */
    public static void clampRowspans(Document document) {
        Objects.requireNonNull(document, "document");
        for (Element table : document.select("table")) {
            for (List<Element> rows : rowGroups(table)) {
                for (int index = 0; index < rows.size(); index++) {
                    int remaining = rows.size() - index;
                    for (Element cell : cellsOf(rows.get(index))) {
                        if (!cell.hasAttr("rowspan")) {
                            continue;
                        }
                        long span = leadingNumber(cell.attr("rowspan"));
                        if (span == 0 || span > remaining) {
                            cell.attr("rowspan", Integer.toString(remaining));
                        }
                    }
                }
            }
        }
    }

    /**
     * Clamps {@code colspan} to {@value #MAX_COLSPAN}, the HTML limit.
     *
     * <p>Not one of the spec's steps: probing Flying Saucer 10.7.0 showed the same heap exhaustion
     * for a huge {@code colspan} as for a huge {@code rowspan} (see {@link #MAX_COLSPAN}).</p>
     *
     * @param document the document to clean
     */
    public static void clampColspans(Document document) {
        Objects.requireNonNull(document, "document");
        for (Element cell : document.select("td[colspan], th[colspan]")) {
            if (leadingNumber(cell.attr("colspan")) > MAX_COLSPAN) {
                cell.attr("colspan", Integer.toString(MAX_COLSPAN));
            }
        }
    }

    /**
     * The value of an HTML non-negative integer attribute as a browser reads it: optional leading
     * white space and plus sign, then digits, anything after them ignored. It saturates instead of
     * overflowing.
     *
     * @return the number, {@link Long#MAX_VALUE} when it does not fit, or {@code -1} when there is none
     */
    private static long leadingNumber(String value) {
        int i = 0;
        int length = value.length();
        while (i < length && isAsciiWhitespace(value.charAt(i))) {
            i++;
        }
        if (i < length && value.charAt(i) == '+') {
            i++;
        }
        if (i >= length || value.charAt(i) < '0' || value.charAt(i) > '9') {
            return -1;
        }
        long number = 0;
        for (; i < length && value.charAt(i) >= '0' && value.charAt(i) <= '9'; i++) {
            number = number > (Long.MAX_VALUE - 9) / 10 ? Long.MAX_VALUE : number * 10 + (value.charAt(i) - '0');
        }
        return number;
    }

    private static boolean isAsciiWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\f' || c == '\r';
    }

    // ---- g. paginated tables ----------------------------------------------------------------

    /**
     * Adds {@value #PAGINATE_CLASS} to each table that has a direct {@code <thead>} child and no
     * cell of its own with a {@code rowspan} other than 1 (step g). Flying Saucer's table
     * pagination, which repeats the header on every page, breaks the heights of row spans, so a
     * table with spans is left unpaginated. Run it after {@link #clampRowspans(Document)}.
     *
     * @param document the document to clean
     */
    public static void markPaginatedTables(Document document) {
        Objects.requireNonNull(document, "document");
        for (Element table : document.select("table")) {
            if (table.children().stream().noneMatch(child -> child.normalName().equals("thead"))) {
                continue;
            }
            boolean hasRowSpan = false;
            for (List<Element> rows : rowGroups(table)) {
                for (Element row : rows) {
                    for (Element cell : cellsOf(row)) {
                        hasRowSpan |= spansRows(cell);
                    }
                }
            }
            if (!hasRowSpan) {
                table.addClass(PAGINATE_CLASS);
            }
        }
    }

    /** Whether a cell has a {@code rowspan} that is a number other than 1 (an invalid one counts as 1). */
    private static boolean spansRows(Element cell) {
        if (!cell.hasAttr("rowspan")) {
            return false;
        }
        long span = leadingNumber(cell.attr("rowspan"));
        return span >= 0 && span != 1;
    }

    /**
     * The row groups of a table: the rows of each direct {@code thead}, {@code tbody} and
     * {@code tfoot} child, and the rows that are direct children of the table itself. Rows of a
     * nested table belong to that table, which is visited on its own.
     */
    private static List<List<Element>> rowGroups(Element table) {
        List<List<Element>> groups = new ArrayList<>();
        List<Element> loose = new ArrayList<>();
        for (Element child : table.children()) {
            switch (child.normalName()) {
                case "thead", "tbody", "tfoot" -> groups.add(rowsOf(child));
                case "tr" -> loose.add(child);
                default -> { }
            }
        }
        if (!loose.isEmpty()) {
            groups.add(loose);
        }
        return groups;
    }

    private static List<Element> rowsOf(Element group) {
        List<Element> rows = new ArrayList<>();
        for (Element child : group.children()) {
            if (child.normalName().equals("tr")) {
                rows.add(child);
            }
        }
        return rows;
    }

    private static List<Element> cellsOf(Element row) {
        List<Element> cells = new ArrayList<>();
        for (Element child : row.children()) {
            String name = child.normalName();
            if (name.equals("td") || name.equals("th")) {
                cells.add(child);
            }
        }
        return cells;
    }

    // ---- i. image alternative text ----------------------------------------------------------

    /**
     * Gives every {@code <img>} without an {@code alt} attribute an empty one (step i), as XHTML
     * requires.
     *
     * @param document the document to clean
     */
    public static void addMissingAltText(Document document) {
        Objects.requireNonNull(document, "document");
        document.select("img:not([alt])").attr("alt", "");
    }

    // ---- m. serialisation -------------------------------------------------------------------

    /**
     * Serialises the document as XHTML: XML syntax, XHTML character escaping, UTF-8, no pretty
     * printing (step m). A {@code <!DOCTYPE html>} is kept, so Flying Saucer lays the page out in
     * standards mode, as wkhtmltopdf did.
     *
     * <p>The result always parses with a namespace-aware XML parser, which is how Flying Saucer
     * reads it. To guarantee that, the method first renames any element or attribute whose name is
     * not a valid XML name (a prefix nobody declared, as in Word's {@code <o:p>}; a leading colon; a
     * stray symbol), changing the names in the given document, and finally drops any XML-invalid
     * character that is left (jsoup already drops them from text and attribute values, but not from
     * a style sheet). Output settings of the document are restored afterwards.</p>
     *
     * @param document the document, after all other rules
     * @return well-formed XHTML
     */
    public static String serialize(Document document) {
        Objects.requireNonNull(document, "document");
        makeNamesXmlSafe(document);
        Document.OutputSettings original = document.outputSettings();
        document.outputSettings(original.clone()
                .syntax(Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.xhtml)
                .charset(StandardCharsets.UTF_8)
                .prettyPrint(false));
        try {
            return strip(document.outerHtml(), false);
        } finally {
            document.outputSettings(original);
        }
    }

    /**
     * Renames what an XML parser would reject. A colon in a name means a namespace prefix, which is
     * bound only by an {@code xmlns:} attribute, and HTML markup rarely declares one; the prefix is
     * folded into the name instead ({@code o:p} becomes the unknown inline element {@code o_p}).
     * Attributes in the {@code xml}, {@code xmlns} and {@code xlink} prefixes are real XML and stay,
     * with {@code xmlns:xlink} declared on the root when something uses {@code xlink:}.
     */
    private static void makeNamesXmlSafe(Document document) {
        boolean usesXlink = false;
        for (Element element : document.getAllElements()) {
            if (element instanceof Document) {
                continue;
            }
            String name = element.tagName();
            String safeName = toXmlName(name);
            if (!safeName.equals(name)) {
                element.tagName(safeName);
            }
            List<Attribute> dropped = new ArrayList<>();
            List<Attribute> renamed = new ArrayList<>();
            for (Attribute attribute : element.attributes()) {
                switch (fateOf(attribute)) {
                    case KEEP_XLINK -> usesXlink = true;
                    case DROP -> dropped.add(attribute);
                    case RENAME -> renamed.add(attribute);
                    case KEEP -> { }
                }
            }
            for (Attribute attribute : dropped) {
                element.attributes().remove(attribute.getKey());
            }
            for (Attribute attribute : renamed) {
                element.attributes().remove(attribute.getKey());
                element.attr(toXmlName(attribute.getKey()), attribute.getValue());
            }
        }
        if (usesXlink) {
            Element root = document.firstElementChild();
            if (root != null && !root.hasAttr("xmlns:xlink")) {
                root.attr("xmlns:xlink", XLINK_NAMESPACE);
            }
        }
    }

    /** What {@link #makeNamesXmlSafe} does with an attribute. */
    private enum AttributeFate {
        /** An ordinary name, or a prefix XML binds itself or an {@code xmlns:} declaration binds. */
        KEEP,
        /** An {@code xlink:} attribute: kept, and the prefix is declared on the root. */
        KEEP_XLINK,
        /** An {@code xmlns:} declaration XML forbids (an empty URI, or a rebinding of {@code xml} or {@code xmlns}). */
        DROP,
        /** An undeclared prefix, or a leading colon: folded into a plain name. */
        RENAME
    }

    private static AttributeFate fateOf(Attribute attribute) {
        String key = attribute.getKey();
        int colon = key.indexOf(':');
        if (colon < 0) {
            return AttributeFate.KEEP;
        }
        boolean hasLocalName = colon + 1 < key.length();
        switch (key.substring(0, colon)) {
            case XLINK_PREFIX:
                return hasLocalName ? AttributeFate.KEEP_XLINK : AttributeFate.RENAME;
            case "xml":
                return hasLocalName ? AttributeFate.KEEP : AttributeFate.RENAME;
            case "xmlns":
                boolean declaresPrefix = hasLocalName && !attribute.getValue().isEmpty()
                        && !key.equals("xmlns:xmlns") && !key.equals("xmlns:xml");
                return declaresPrefix ? AttributeFate.KEEP : AttributeFate.DROP;
            default:
                return AttributeFate.RENAME;
        }
    }

    /**
     * A valid XML name without a colon: every character XML does not allow in a name becomes an
     * underscore, and a name that does not start with a name-start character gets an underscore in
     * front. A name that is already valid is returned as it is.
     */
    private static String toXmlName(String name) {
        if (name.isEmpty()) {
            return "_";
        }
        StringBuilder safe = null;
        int index = 0;
        for (int offset = 0; offset < name.length(); ) {
            int codePoint = name.codePointAt(offset);
            int width = Character.charCount(codePoint);
            boolean valid = index == 0 ? isNameStart(codePoint) : isNamePart(codePoint);
            if (!valid && safe == null) {
                safe = new StringBuilder(name.length() + 1).append(name, 0, offset);
            }
            if (safe != null) {
                if (index == 0 && !isNameStart(codePoint)) {
                    safe.append('_');
                    if (isNamePart(codePoint)) {
                        safe.appendCodePoint(codePoint);
                    }
                } else {
                    safe.appendCodePoint(valid ? codePoint : '_');
                }
            }
            offset += width;
            index++;
        }
        return safe == null ? name : safe.toString();
    }

    /** XML 1.0 NameStartChar, without the colon. */
    private static boolean isNameStart(int c) {
        return (c >= 'A' && c <= 'Z') || c == '_' || (c >= 'a' && c <= 'z')
                || (c >= 0xC0 && c <= 0xD6) || (c >= 0xD8 && c <= 0xF6) || (c >= 0xF8 && c <= 0x2FF)
                || (c >= 0x370 && c <= 0x37D) || (c >= 0x37F && c <= 0x1FFF) || (c >= 0x200C && c <= 0x200D)
                || (c >= 0x2070 && c <= 0x218F) || (c >= 0x2C00 && c <= 0x2FEF) || (c >= 0x3001 && c <= 0xD7FF)
                || (c >= 0xF900 && c <= 0xFDCF) || (c >= 0xFDF0 && c <= 0xFFFD) || (c >= 0x10000 && c <= 0xEFFFF);
    }

    /** XML 1.0 NameChar, without the colon. */
    private static boolean isNamePart(int c) {
        return isNameStart(c) || c == '-' || c == '.' || (c >= '0' && c <= '9') || c == 0xB7
                || (c >= 0x300 && c <= 0x36F) || (c >= 0x203F && c <= 0x2040);
    }
}
