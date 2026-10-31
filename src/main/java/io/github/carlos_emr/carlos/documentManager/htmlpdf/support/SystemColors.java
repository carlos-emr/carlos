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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Replaces the CSS2 system color keywords ({@code windowtext}, {@code window}, {@code buttonface} and the
 * rest) in the colour properties of inline styles and style sheets with the colours a browser on Windows
 * shows (step o).
 *
 * <p>Word writes every table border as {@code border: solid windowtext 1.0pt}, and a report that began as
 * a Word document reaches CARLOS as HTML full of them. wkhtmltopdf's WebKit knew the keywords; Flying
 * Saucer 10.7.0 does not, drops the whole declaration, and the table prints without borders (measured:
 * {@code border: solid windowtext 1pt} draws nothing, {@code border: solid #000000 1pt} draws the box).
 * Replacing the keyword keeps the border and the colour a reader expects.</p>
 *
 * <p>Only the value of a colour-bearing property is touched, and only a keyword that stands on its own
 * outside quotes and parentheses: {@code font: menu}, {@code font-family: Window}, a selector or a class
 * called {@code window}, {@code url(window.png)} and {@code content: "windowtext"} stay as they are. The
 * scan understands strings, comments, parentheses and the braces of nested rules, so a {@code ;} inside a
 * {@code data:} URI does not split a declaration.</p>
 *
 * @since 2026-10-30
 */
public final class SystemColors {

    /** The keywords (lower case) and the colours of the default Windows theme, as browsers map them. */
    private static final Map<String, String> COLORS = Map.ofEntries(
            Map.entry("activeborder", "#b4b4b4"),
            Map.entry("activecaption", "#99b4d1"),
            Map.entry("appworkspace", "#ababab"),
            Map.entry("buttonface", "#f0f0f0"),
            Map.entry("buttonhighlight", "#ffffff"),
            Map.entry("buttonshadow", "#a0a0a0"),
            Map.entry("buttontext", "#000000"),
            Map.entry("captiontext", "#000000"),
            Map.entry("graytext", "#6d6d6d"),
            Map.entry("highlight", "#3399ff"),
            Map.entry("highlighttext", "#ffffff"),
            Map.entry("inactiveborder", "#f4f7fc"),
            Map.entry("inactivecaption", "#bfcddb"),
            Map.entry("inactivecaptiontext", "#434e54"),
            Map.entry("infobackground", "#ffffe1"),
            Map.entry("infotext", "#000000"),
            Map.entry("menu", "#f0f0f0"),
            Map.entry("menutext", "#000000"),
            Map.entry("scrollbar", "#c8c8c8"),
            Map.entry("threeddarkshadow", "#696969"),
            Map.entry("threedface", "#f0f0f0"),
            Map.entry("threedhighlight", "#ffffff"),
            Map.entry("threedlightshadow", "#e3e3e3"),
            Map.entry("threedshadow", "#a0a0a0"),
            Map.entry("window", "#ffffff"),
            Map.entry("windowframe", "#646464"),
            Map.entry("windowtext", "#000000"),
            Map.entry("canvas", "#ffffff"),
            Map.entry("canvastext", "#000000"),
            Map.entry("linktext", "#0000ee"),
            Map.entry("visitedtext", "#551a8b"),
            Map.entry("activetext", "#ff0000"),
            Map.entry("buttonborder", "#767676"),
            Map.entry("field", "#ffffff"),
            Map.entry("fieldtext", "#000000"),
            Map.entry("mark", "#ffff00"),
            Map.entry("marktext", "#000000"));

    /** The properties whose value can be a colour. */
    private static final Set<String> COLOR_PROPERTIES = Set.of("color", "background", "background-color", "border",
            "border-color", "border-top", "border-right", "border-bottom", "border-left", "border-top-color",
            "border-right-color", "border-bottom-color", "border-left-color", "outline", "outline-color", "fill", "stroke",
            "text-decoration-color", "column-rule", "column-rule-color");

    private SystemColors() {
    }

    /**
     * Replaces the keywords in every {@code style} attribute and every {@code <style>} element (step o).
     *
     * @param document the document to change
     */
    public static void replace(Document document) {
        Objects.requireNonNull(document, "document");
        for (Element element : document.getAllElements()) {
            if (element.hasAttr("style")) {
                String style = element.attr("style");
                String replaced = replaceIn(style);
                if (!replaced.equals(style)) {
                    element.attr("style", replaced);
                }
            }
            if (element.normalName().equals("style")) {
                for (Node child : List.copyOf(element.childNodes())) {
                    if (child instanceof DataNode data) {
                        String replaced = replaceIn(data.getWholeData());
                        if (!replaced.equals(data.getWholeData())) {
                            data.setWholeData(replaced);
                        }
                    } else if (child instanceof TextNode text) {
                        String replaced = replaceIn(text.getWholeText());
                        if (!replaced.equals(text.getWholeText())) {
                            text.text(replaced);
                        }
                    }
                }
            }
        }
    }

    /**
     * Replaces the keywords in a style attribute's declarations or in a style sheet's text.
     *
     * @param css the CSS
     * @return the CSS with every system colour keyword in a colour property replaced; the same string
     *         when there was none
     */
    static String replaceIn(String css) {
        StringBuilder out = new StringBuilder(css.length() + 16);
        int segmentStart = 0;
        int parentheses = 0;
        char quote = 0;
        int length = css.length();
        for (int i = 0; i < length; i++) {
            char c = css.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '/' && i + 1 < length && css.charAt(i + 1) == '*') {
                int end = css.indexOf("*/", i + 2);
                i = end < 0 ? length : end + 1;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '(') {
                parentheses++;
            } else if (c == ')') {
                parentheses = Math.max(0, parentheses - 1);
            } else if (parentheses == 0 && (c == ';' || c == '{' || c == '}')) {
                out.append(replaceInDeclaration(css.substring(segmentStart, i))).append(c);
                segmentStart = i + 1;
            }
        }
        out.append(replaceInDeclaration(css.substring(Math.min(segmentStart, length))));
        String result = out.toString();
        return result.equals(css) ? css : result;
    }

    /** One declaration (or a selector, or anything else between delimiters): changed only if it is a colour declaration. */
    // FindSecBugs IMPROPER_UNICODE: case-fold of a CSS property name (ASCII) to look it up in the list of colour properties; selects a rendering, not a security decision
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-fold of a CSS property name to look it up in a list of colour properties; selects a rendering, not a security decision")
    private static String replaceInDeclaration(String segment) {
        int colon = segment.indexOf(':');
        if (colon < 0) {
            return segment;
        }
        // A comment before the declaration (Word and editors write them) is not part of the property name.
        String property = withoutComments(segment.substring(0, colon)).strip().toLowerCase(Locale.ROOT);
        if (!COLOR_PROPERTIES.contains(property)) {
            return segment;
        }
        String value = segment.substring(colon + 1);
        String replaced = replaceKeywords(value);
        return replaced.equals(value) ? segment : segment.substring(0, colon + 1) + replaced;
    }

    /**
     * The text without its comments; an unterminated comment stays. Not a regular expression: {@code /\*.*?\*}{@code /}
     * starts again at every comment opener, and text made of openers with no end took minutes.
     */
    static String withoutComments(String text) {
        int start = text.indexOf("/*");
        if (start < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        int copied = 0;
        while (start >= 0) {
            int end = text.indexOf("*/", start + 2);
            if (end < 0) {
                // No later opener has a closer either.
                break;
            }
            out.append(text, copied, start);
            copied = end + 2;
            start = text.indexOf("/*", copied);
        }
        return out.append(text, copied, text.length()).toString();
    }

    /** Replaces each standalone keyword outside quotes and parentheses. */
    // FindSecBugs IMPROPER_UNICODE: case-fold of a CSS colour keyword (ASCII) to look up its colour; selects a rendering, not a security decision
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-fold of a CSS colour keyword to look up its colour; selects a rendering, not a security decision")
    private static String replaceKeywords(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        int length = value.length();
        int parentheses = 0;
        char quote = 0;
        int i = 0;
        while (i < length) {
            char c = value.charAt(i);
            if (quote != 0) {
                out.append(c);
                if (c == '\\' && i + 1 < length) {
                    out.append(value.charAt(++i));
                } else if (c == quote) {
                    quote = 0;
                }
                i++;
            } else if (c == '/' && i + 1 < length && value.charAt(i + 1) == '*') {
                int end = value.indexOf("*/", i + 2);
                int stop = end < 0 ? length : end + 2;
                out.append(value, i, stop);
                i = stop;
            } else if (c == '"' || c == '\'') {
                quote = c;
                out.append(c);
                i++;
            } else if (c == '(') {
                parentheses++;
                out.append(c);
                i++;
            } else if (c == ')') {
                parentheses = Math.max(0, parentheses - 1);
                out.append(c);
                i++;
            } else if (parentheses == 0 && isIdentifierPart(c)) {
                int end = i;
                while (end < length && isIdentifierPart(value.charAt(end))) {
                    end++;
                }
                String word = value.substring(i, end);
                String color = COLORS.get(word.toLowerCase(Locale.ROOT));
                out.append(color != null ? color : word);
                i = end;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static boolean isIdentifierPart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '#' || c > 0x7F;
    }
}
