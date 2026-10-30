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
import org.jsoup.nodes.Element;

import java.util.Objects;
import java.util.Optional;

/**
 * Removes the elements an author hid, for the rungs of the render ladder that drop the author's styles.
 *
 * <p>In normal mode Flying Saucer honours {@code style="display: none"} and the print style sheets of a page,
 * so what was hidden stays hidden. Safe mode strips every {@code style} attribute and style sheet, and
 * text-only mode keeps the text of everything, so the hidden parts of the document (a form's duplicate
 * for the screen, a banner, a scratch value) would print on a page the author never meant them to be
 * on. This removes, before the styles go, the elements that say so in markup or in their own
 * {@code style} attribute: those with the {@code hidden} attribute, with {@code display: none}, or with
 * {@code visibility: hidden} or {@code collapse}, and everything inside them.</p>
 *
 * <p>An element hidden by a rule of a style sheet (a class such as {@code hidePrint}, which {@code form/print.css}
 * hides for print) cannot be told without evaluating the sheets, and is still printed on these rungs
 * (documented in the guide).</p>
 *
 * @since 2026-10-30
 */
public final class HiddenContent {

    private HiddenContent() {
    }

    /**
     * Removes the elements that are hidden by markup or by their own inline style.
     *
     * @param document the document to change
     */
    public static void remove(Document document) {
        Objects.requireNonNull(document, "document");
        for (Element element : document.select("[hidden], [style]")) {
            if (isHidden(element)) {
                // Everything inside goes with it; an element of that kind found later is only taken out of the removed part.
                element.remove();
            }
        }
    }

    private static boolean isHidden(Element element) {
        if (isHtmlOrBody(element)) {
            return false;
        }
        if (element.hasAttr("hidden")) {
            return true;
        }
        String style = element.attr("style");
        if (style.isEmpty()) {
            return false;
        }
        Optional<String> display = InlineStyle.valueOf(style, "display");
        if (display.isPresent() && isKeyword(display.get(), "none")) {
            return true;
        }
        Optional<String> visibility = InlineStyle.valueOf(style, "visibility");
        return visibility.isPresent() && (isKeyword(visibility.get(), "hidden") || isKeyword(visibility.get(), "collapse"));
    }

    /**
     * The document's own structure is never removed, whatever an author wrote on it: a body hidden until a script
     * shows it would leave nothing to print.
     */
    private static boolean isHtmlOrBody(Element element) {
        String name = element.normalName();
        return name.equals("html") || name.equals("body");
    }

    private static boolean isKeyword(String value, String keyword) {
        return value.length() == keyword.length() && value.regionMatches(true, 0, keyword, 0, keyword.length());
    }
}
