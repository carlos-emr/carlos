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
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The margin attributes of {@code <body>} that WebKit reads as presentational hints and Flying Saucer
 * ignores: {@code <body topmargin="0" leftmargin="0" rightmargin="0">}, which most of CARLOS's legacy
 * form pages and eForms begin with. wkhtmltopdf, which printed them, therefore printed such a page with
 * no body margin; Flying Saucer would give it the default 8 px (the Annual forms started 4.4 pt further
 * in and a little narrower).
 *
 * <p>The mapping is WebKit's: {@code leftmargin} and {@code marginwidth} set the left <em>and</em> right
 * margin, {@code topmargin} and {@code marginheight} the top and bottom margin, and {@code rightmargin}
 * and {@code bottommargin} (Internet Explorer's) are not read. When several name the same pair, the
 * last in the document wins. A value is a length in pixels, as WebKit read it; anything else is ignored.
 * The result is a rule for the style sheet the pipeline injects ahead of the author's, so, as a
 * presentational hint, it loses to any author rule of its own.</p>
 *
 * @since 2026-10-30
 */
public final class LegacyBodyMargins {

    /** A margin in pixels, as WebKit read it: digits, an optional fraction and an optional {@code px}. */
    private static final Pattern PIXELS = Pattern.compile("(?i)\\s*\\+?(\\d+)(?:\\.\\d*)?\\s*(?:px)?\\s*");

    /** A margin wider than this is not a margin any more; it would leave no room for the page. */
    static final long MAX_PIXELS = 1_000;

    private LegacyBodyMargins() {
    }

    /**
     * The CSS rule for the body's margin attributes.
     *
     * @param document the parsed document; its body's attributes are read, nothing is changed
     * @return for example {@code body { margin-left: 0px; margin-right: 0px; margin-top: 0px;
     *         margin-bottom: 0px }}, or empty when the body has none of the attributes (or none with a
     *         usable value)
     */
    public static Optional<String> rule(Document document) {
        Element body = document.body();
        if (body == null) {
            return Optional.empty();
        }
        long horizontal = -1;
        long vertical = -1;
        for (Attribute attribute : body.attributes()) {
            // Jsoup's HTML parser has already folded attribute names to lower case.
            switch (attribute.getKey()) {
                case "leftmargin", "marginwidth" -> horizontal = pixels(attribute.getValue(), horizontal);
                case "topmargin", "marginheight" -> vertical = pixels(attribute.getValue(), vertical);
                default -> {
                }
            }
        }
        if (horizontal < 0 && vertical < 0) {
            return Optional.empty();
        }
        StringBuilder rule = new StringBuilder("body {");
        if (horizontal >= 0) {
            rule.append(" margin-left: ").append(horizontal).append("px; margin-right: ").append(horizontal).append("px;");
        }
        if (vertical >= 0) {
            rule.append(" margin-top: ").append(vertical).append("px; margin-bottom: ").append(vertical).append("px;");
        }
        return Optional.of(rule.append(" }").toString());
    }

    /** The value in pixels, clamped, or {@code previous} when the text is not a length. */
    private static long pixels(String value, long previous) {
        Matcher matcher = PIXELS.matcher(value);
        if (!matcher.matches()) {
            return previous;
        }
        String digits = matcher.group(1);
        // More digits than MAX_PIXELS has is over the cap, whatever they are.
        return digits.length() > 4 ? MAX_PIXELS : Math.min(Long.parseLong(digits), MAX_PIXELS);
    }
}
