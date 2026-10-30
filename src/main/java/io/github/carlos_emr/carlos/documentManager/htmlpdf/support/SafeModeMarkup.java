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
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Locale;
import java.util.Objects;

/**
 * The simplifications of safe mode, the second rung of the render ladder: the document printed
 * without the parts of it that most often make layout fail.
 *
 * <p>When normal rendering fails (an exception in layout, a page that cannot be placed, an output
 * that does not validate), the pipeline retries once with the author's presentation removed: every
 * {@code <style>} element, every {@code <link rel="stylesheet">}, every {@code style} attribute, and
 * every inline {@code <svg>}, which is replaced by the placeholder box ({@link ImageBox}) the pipeline
 * uses for an image that cannot be shown, the size of the graphic. The text, tables, form-control replacements and plain {@code <img>} elements
 * stay, so the record is complete even if less pretty. The document note says so.</p>
 *
 * <p>This runs first, before the other document rules, so the {@code style} attributes those rules
 * add themselves (the sizes of printed form controls, right-alignment of right-to-left text) are
 * not removed with the author's.</p>
 *
 * @since 2026-10-29
 */
public final class SafeModeMarkup {

    private SafeModeMarkup() {
    }

    /**
     * Removes the author's styles and boxes the inline SVG graphics.
     *
     * @param document the document to simplify
     */
    public static void simplify(Document document) {
        Objects.requireNonNull(document, "document");
        document.select("style").remove();
        for (Element link : document.select("link[rel]")) {
            if (isStylesheet(link.attr("rel"))) {
                link.remove();
            }
        }
        document.select("[style]").removeAttr("style");
        for (Element svg : document.select("svg")) {
            // An svg inside one that was replaced is gone with it.
            if (svg.ownerDocument() == null) {
                continue;
            }
            svg.replaceWith(ImageBox.replacing(svg));
        }
    }

    /** Whether the space-separated {@code rel} tokens include {@code stylesheet}, in any case. */
    // FindSecBugs IMPROPER_UNICODE: case-fold of an HTML link-relation keyword; selects a rendering, not a security decision
    @SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "case-fold of an HTML link-relation keyword; selects a rendering, not a security decision")
    private static boolean isStylesheet(String rel) {
        for (String token : rel.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (token.equals("stylesheet")) {
                return true;
            }
        }
        return false;
    }
}
