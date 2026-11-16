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

import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.Objects;

/**
 * Removes Flying Saucer's own CSS properties, those that begin with {@code -fs-}, from the CSS a
 * document brings: its {@code style} attributes and its {@code <style>} elements.
 *
 * <p>They are the renderer's controls, not a document's: a page has no use for
 * {@code -fs-page-orientation} when every page is Letter, and no business with
 * {@code -fs-table-cell-rowspan} and {@code -fs-table-cell-colspan}, which are what the {@code rowspan} and
 * {@code colspan} attributes become. {@code XhtmlCleanup} clamps those attributes because one of
 * 99,999,999,999 exhausts the heap; the properties are read after the clamp, so
 * {@code <td style="-fs-table-cell-rowspan: 99999999999">} would bring the server down (measured: a
 * 512 MB heap, and the packaged server exits on an {@code OutOfMemoryError}). The pipeline's own style
 * sheet, which uses {@code -fs-table-paginate}, is added after this step and is not touched.</p>
 *
 * <p>The scan follows CSS tokens, not text: it finds a declaration only where one can begin (at the start,
 * and after {@code {}, {@code ;} or {@code }}), skips comments, strings and parenthesised values (a
 * {@code url(data:...;base64,...)} holds semicolons), and drops the whole declaration. A property name
 * written with an escape ({@code \2d fs-...}) is dropped too, since no real style sheet writes one. One
 * pass, so a hostile style sheet costs time in proportion to its length.</p>
 *
 * @since 2026-10-30
 */
public final class EngineExtensions {

    private static final String PREFIX = "-fs-";

    private EngineExtensions() {
    }

    /**
     * Removes the extension properties from every {@code style} attribute and {@code <style>} element.
     *
     * @param document the document to change
     */
    public static void remove(Document document) {
        Objects.requireNonNull(document, "document");
        for (Element element : document.getAllElements()) {
            if (element.hasAttr("style")) {
                String style = element.attr("style");
                String cleaned = strip(style);
                if (!cleaned.equals(style)) {
                    if (cleaned.isBlank()) {
                        element.removeAttr("style");
                    } else {
                        element.attr("style", cleaned);
                    }
                }
            }
            if (element.normalName().equals("style")) {
                for (Node child : element.childNodes()) {
                    if (child instanceof DataNode data) {
                        data.setWholeData(strip(data.getWholeData()));
                    } else if (child instanceof TextNode text) {
                        text.text(strip(text.getWholeText()));
                    }
                }
            }
        }
    }

    /**
     * Removes the declarations of extension properties from CSS.
     *
     * @param css the text of a {@code style} attribute or a style sheet
     * @return the text without them, the same string when it had none
     */
    static String strip(String css) {
        String stripped = scan(css, true);
        // A parenthesis that never closes would hide every declaration after it, and a parser that recovers
        // at the next semicolon would still read them: scan again, taking parentheses for ordinary characters.
        return stripped != null ? stripped : scan(css, false);
    }

    /** One pass; {@code null} if parentheses were honoured and one never closed. */
    private static String scan(String css, boolean honourParentheses) {
        int length = css.length();
        StringBuilder out = null;
        int copied = 0;
        int depth = 0;
        int i = 0;
        boolean declarationMayStart = true;
        while (i < length) {
            char c = css.charAt(i);
            if (c == '/' && i + 1 < length && css.charAt(i + 1) == '*') {
                i = endOfComment(css, i);
            } else if (c == '"' || c == '\'') {
                i = endOfString(css, i);
                declarationMayStart = false;
            } else if (honourParentheses && c == '(') {
                depth++;
                i++;
                declarationMayStart = false;
            } else if (honourParentheses && c == ')') {
                depth = Math.max(0, depth - 1);
                i++;
            } else if (depth > 0) {
                i++;
            } else if (c == '{' || c == ';' || c == '}') {
                declarationMayStart = true;
                i++;
            } else if (declarationMayStart && !Character.isWhitespace(c)) {
                int colon = endOfProperty(css, i);
                if (colon >= 0 && isExtension(css, i, colon)) {
                    int end = endOfDeclaration(css, colon + 1, honourParentheses);
                    out = (out != null ? out : new StringBuilder(length)).append(css, copied, i);
                    copied = end;
                    i = end;
                } else {
                    declarationMayStart = false;
                    i++;
                }
            } else {
                i++;
            }
        }
        if (honourParentheses && depth > 0) {
            return null;
        }
        return out == null ? css : out.append(css, copied, length).toString();
    }

    /** The index of the colon that ends the property name beginning at {@code from}, or -1 if a delimiter comes first. */
    private static int endOfProperty(String css, int from) {
        int i = from;
        while (i < css.length()) {
            char c = css.charAt(i);
            if (c == '/' && i + 1 < css.length() && css.charAt(i + 1) == '*') {
                i = endOfComment(css, i);
            } else if (c == ':') {
                return i;
            } else if (c == ';' || c == '{' || c == '}') {
                return -1;
            } else {
                i++;
            }
        }
        return -1;
    }

    /** Whether the text before the colon, without its comments, is an extension property or is written with an escape. */
    private static boolean isExtension(String css, int from, int colon) {
        StringBuilder name = new StringBuilder();
        int i = from;
        while (i < colon) {
            if (css.charAt(i) == '/' && i + 1 < colon && css.charAt(i + 1) == '*') {
                i = endOfComment(css, i);
            } else {
                name.append(css.charAt(i++));
            }
        }
        String property = name.toString().strip();
        if (property.indexOf('\\') >= 0) {
            return true;
        }
        if (property.length() < PREFIX.length() || property.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        for (int k = 0; k < PREFIX.length(); k++) {
            char c = property.charAt(k);
            if (c >= 'A' && c <= 'Z') {
                c += 'a' - 'A';
            }
            if (c != PREFIX.charAt(k)) {
                return false;
            }
        }
        return true;
    }

    /** The end of the declaration whose value starts at {@code from}: after its semicolon, or at the brace that closes the block. */
    private static int endOfDeclaration(String css, int from, boolean honourParentheses) {
        int length = css.length();
        int depth = 0;
        int i = from;
        while (i < length) {
            char c = css.charAt(i);
            if (c == '/' && i + 1 < length && css.charAt(i + 1) == '*') {
                i = endOfComment(css, i);
            } else if (c == '"' || c == '\'') {
                i = endOfString(css, i);
            } else if (honourParentheses && c == '(') {
                depth++;
                i++;
            } else if (honourParentheses && c == ')') {
                depth = Math.max(0, depth - 1);
                i++;
            } else if (depth == 0 && c == ';') {
                return i + 1;
            } else if (depth == 0 && c == '}') {
                return i;
            } else {
                i++;
            }
        }
        return length;
    }

    private static int endOfComment(String css, int start) {
        int end = css.indexOf("*/", start + 2);
        return end < 0 ? css.length() : end + 2;
    }

    /** The index after the string that opens at {@code start}: after its closing quote, or where a newline ends it, or the end. */
    private static int endOfString(String css, int start) {
        char quote = css.charAt(start);
        int i = start + 1;
        while (i < css.length()) {
            char c = css.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (c == quote) {
                return i + 1;
            } else if (c == '\n' || c == '\r' || c == '\f') {
                return i;
            } else {
                i++;
            }
        }
        return css.length();
    }
}
