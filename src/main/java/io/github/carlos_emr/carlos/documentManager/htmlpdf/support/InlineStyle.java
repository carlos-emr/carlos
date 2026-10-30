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

import java.util.regex.Pattern;

/**
 * Reads and extends the inline {@code style} attribute of an element, which the markup rules cannot
 * leave alone (a replacement must keep the author's style, and the author's own width wins over a
 * default one). It is a small scanner, not a CSS parser: it looks for a property name at the start of
 * a declaration, so {@code background-position} is not {@code position} and {@code line-height} is
 * not {@code height}.
 *
 * @since 2026-10-29
 */
final class InlineStyle {

    private static final Pattern FIXED_POSITION =
            Pattern.compile("(?i)((?<![\\w-])position\\s*:\\s*)fixed(?![\\w-])");

    /** An inline {@code list-style-type} or {@code list-style}, either of which wins over {@code <ol type>}. */
    private static final Pattern LIST_STYLE = Pattern.compile("(?i)(?<![\\w-])list-style(-type)?\\s*:");

    private static final Pattern WIDTH = Pattern.compile("(?i)(?<![\\w-])width\\s*:");
    private static final Pattern HEIGHT = Pattern.compile("(?i)(?<![\\w-])height\\s*:");

    private InlineStyle() {
    }

    /**
     * @param style an inline style
     * @return the style with every {@code position: fixed} written {@code position: absolute}
     */
    static String withFixedPositionAbsolute(String style) {
        return FIXED_POSITION.matcher(style).replaceAll("$1absolute");
    }

    /**
     * @param style an inline style
     * @return whether it sets {@code list-style-type} or the {@code list-style} shorthand
     */
    static boolean setsListStyle(String style) {
        return LIST_STYLE.matcher(style).find();
    }

    /**
     * @param style an inline style
     * @return whether it sets {@code width} itself ({@code min-width} and {@code max-width} do not count)
     */
    static boolean setsWidth(String style) {
        return WIDTH.matcher(style).find();
    }

    /**
     * @param style an inline style
     * @return whether it sets {@code height} itself ({@code min-height} and {@code line-height} do not count)
     */
    static boolean setsHeight(String style) {
        return HEIGHT.matcher(style).find();
    }

    /**
     * Adds a declaration after the existing ones.
     *
     * @param style an inline style, possibly empty or ending in a semicolon
     * @param declaration the declaration, without a trailing semicolon
     * @return the combined style
     */
    static String append(String style, String declaration) {
        String trimmed = style.strip();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).stripTrailing();
        }
        return trimmed.isEmpty() ? declaration : trimmed + "; " + declaration;
    }
}
