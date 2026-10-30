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

import org.jsoup.nodes.Document;
import org.jsoup.nodes.DocumentType;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeFilter;
import org.jsoup.select.NodeTraversor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The last rung of the render ladder: a document's text, in reading order, as plain paragraphs.
 *
 * <p>When even safe mode cannot be rendered, or the HTML is too large to lay out safely, the
 * pipeline prints what is certain to print: the words. Block elements become paragraphs; a table row
 * becomes one line with its cells separated by " | " (an empty cell keeps its place); line breaks
 * start a new line; the lines of preformatted text and of a printed text area stay lines; the text of
 * the printed form controls ({@code PrintFormControls} has already replaced them) is kept; images,
 * graphics, scripts, styles and everything else without text are left out. Whitespace elsewhere is
 * collapsed as a browser does.</p>
 *
 * <p>The result is a new document with the original {@code <title>} and a {@code <p>} per line, no
 * classes, no styles and no attributes, so nothing in it can fail layout. It is built iteratively, so
 * a deeply nested source cannot overflow the stack.</p>
 *
 * @since 2026-10-29
 */
public final class PlainTextDocument {

    /** Elements whose content is not text to read: dropped with everything inside them. */
    private static final Set<String> NOT_TEXT = Set.of("head", "script", "style", "template", "noscript", "svg",
            "math", "img", "canvas", "video", "audio", "object", "embed", "iframe", "map", "picture", "source",
            "track", "param", "datalist");

    /** The table pieces that do not start a paragraph of their own: only a row does. */
    private static final Set<String> TABLE_PIECES = Set.of("table", "thead", "tbody", "tfoot", "caption", "colgroup", "col");

    private PlainTextDocument() {
    }

    /**
     * Builds the plain-text document for a (cleaned) document.
     *
     * @param source the document, after the other document rules
     * @return a new document with its title and one paragraph per line of text
     */
    public static Document from(Document source) {
        Objects.requireNonNull(source, "source");
        Reader reader = new Reader();
        Element body = source.body();
        if (body != null) {
            NodeTraversor.filter(reader, body);
            reader.flushLine();
        }
        Document result = Document.createShell("");
        result.prependChild(new DocumentType("html", "", ""));
        if (!source.title().isEmpty()) {
            result.title(source.title());
        }
        for (String line : reader.lines) {
            result.body().appendElement("p").appendText(line);
        }
        return result;
    }

    /** Walks the body once, collecting lines. */
    private static final class Reader implements NodeFilter {
        final List<String> lines = new ArrayList<>();
        private final StringBuilder line = new StringBuilder();
        private final List<String> cells = new ArrayList<>();
        private final StringBuilder cell = new StringBuilder();
        /** Open {@code <tr>} elements: the outermost one collects cells, deeper ones (nested tables) only add spaces. */
        private int rowDepth;
        private int cellDepth;
        /** Open elements whose line breaks are kept: {@code <pre>} and the printed text area. */
        private int preformattedDepth;

        @Override
        public FilterResult head(Node node, int depth) {
            if (node instanceof TextNode text) {
                append(text.getWholeText());
                return FilterResult.CONTINUE;
            }
            if (!(node instanceof Element element)) {
                return FilterResult.CONTINUE;
            }
            String name = element.normalName();
            if (NOT_TEXT.contains(name)) {
                return FilterResult.SKIP_ENTIRELY;
            }
            if (isPreformatted(element)) {
                preformattedDepth++;
            }
            if (name.equals("br")) {
                boundary();
            } else if (name.equals("tr")) {
                if (rowDepth++ == 0) {
                    flushLine();
                    cells.clear();
                } else {
                    space();
                }
            } else if (name.equals("td") || name.equals("th")) {
                if (rowDepth == 1 && cellDepth++ == 0) {
                    cell.setLength(0);
                } else {
                    space();
                }
            } else if (element.isBlock() || TABLE_PIECES.contains(name)) {
                boundary();
            }
            return FilterResult.CONTINUE;
        }

        @Override
        public FilterResult tail(Node node, int depth) {
            if (!(node instanceof Element element)) {
                return FilterResult.CONTINUE;
            }
            String name = element.normalName();
            if (name.equals("td") || name.equals("th")) {
                if (rowDepth == 1 && --cellDepth == 0) {
                    cells.add(cell.toString().strip());
                    cell.setLength(0);
                } else {
                    space();
                }
            } else if (name.equals("tr")) {
                if (--rowDepth == 0) {
                    endRow();
                } else {
                    space();
                }
            } else if (element.isBlock() || TABLE_PIECES.contains(name)) {
                boundary();
            }
            if (isPreformatted(element)) {
                preformattedDepth--;
            }
            return FilterResult.CONTINUE;
        }

        private static boolean isPreformatted(Element element) {
            return element.normalName().equals("pre") || element.hasClass("carlos-pdf-textarea");
        }

        /** Adds text to the current cell or line: line breaks kept in preformatted text, collapsed everywhere else. */
        private void append(String text) {
            if (preformattedDepth > 0 && rowDepth == 0) {
                String[] parts = text.split("\\R", -1);
                for (int i = 0; i < parts.length; i++) {
                    if (i > 0) {
                        flushLine();
                    }
                    line.append(parts[i]);
                }
                return;
            }
            StringBuilder target = rowDepth > 0 ? cellTarget() : line;
            if (target == null) {
                return;
            }
            boolean previousSpace = target.length() == 0 || Character.isWhitespace(target.charAt(target.length() - 1));
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (Character.isWhitespace(c)) {
                    if (!previousSpace) {
                        target.append(' ');
                        previousSpace = true;
                    }
                } else {
                    target.append(c);
                    previousSpace = false;
                }
            }
        }

        /** The buffer for text inside a row: the cell, or nothing for text between a row's cells. */
        private StringBuilder cellTarget() {
            return cellDepth > 0 ? cell : null;
        }

        private void space() {
            StringBuilder target = rowDepth > 0 ? cellTarget() : line;
            if (target != null && target.length() > 0 && target.charAt(target.length() - 1) != ' ') {
                target.append(' ');
            }
        }

        /** A line break or the edge of a block: a new line, or just a space inside a table row. */
        private void boundary() {
            if (rowDepth > 0) {
                space();
            } else {
                flushLine();
            }
        }

        private void endRow() {
            boolean anyText = cells.stream().anyMatch(text -> !text.isEmpty());
            if (anyText) {
                // An empty cell leaves "|  |"; one space reads the same and matches how it prints.
                lines.add(String.join(" | ", cells).replace("  ", " "));
            }
            cells.clear();
        }

        void flushLine() {
            String text = line.toString().strip();
            if (!text.isEmpty()) {
                lines.add(text);
            }
            line.setLength(0);
        }
    }
}
