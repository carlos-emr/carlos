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

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link SafeModeMarkup}.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("SafeModeMarkup Unit Tests")
class SafeModeMarkupUnitTest {

    private static Document simplified(String html) {
        Document document = Jsoup.parse(html);
        SafeModeMarkup.simplify(document);
        return document;
    }

    @Test
    @DisplayName("should remove style elements, stylesheet links and style attributes")
    void shouldRemoveAuthorStyles_inSafeMode() {
        Document document = simplified("<html><head><style>p { color: red }</style>"
                + "<link rel=\"stylesheet\" href=\"a.css\"><link rel=\"Alternate StyleSheet\" href=\"b.css\">"
                + "<link rel=\"icon\" href=\"i.png\"></head><body style=\"margin: 0\">"
                + "<p id=\"p\" style=\"color: blue\">text <b style=\"font-size: 30px\">bold</b></p>"
                + "<style>td { color: green }</style></body></html>");

        assertThat(document.select("style")).isEmpty();
        assertThat(document.select("link[rel*=tylesheet]")).isEmpty();
        assertThat(document.select("link[rel=icon]")).hasSize(1);
        assertThat(document.select("[style]")).isEmpty();
        assertThat(document.getElementById("p").text()).isEqualTo("text bold");
    }

    @Test
    @DisplayName("should keep presentational attributes, which are not styles")
    void shouldKeepPresentationalAttributes_inSafeMode() {
        Document document = simplified("<table id=\"t\" border=\"1\" width=\"50%\" bgcolor=\"#eee\"><tr><td align=\"right\">x</td></tr></table>");

        assertThat(document.getElementById("t").attr("border")).isEqualTo("1");
        assertThat(document.getElementById("t").attr("width")).isEqualTo("50%");
        assertThat(document.select("td").attr("align")).isEqualTo("right");
    }

    @Test
    @DisplayName("should replace an inline SVG with the unavailable-image box")
    void shouldBoxInlineSvg_inSafeMode() {
        Document document = simplified("<p>before <svg width=\"10\" height=\"10\"><rect width=\"5\" height=\"5\"/>"
                + "<svg><circle r=\"1\"/></svg></svg> after</p>");

        assertThat(document.select("svg")).isEmpty();
        Element box = document.select("p > span.carlos-pdf-image-unavailable").first();
        assertThat(box.text()).isEqualTo("[image unavailable]");
        assertThat(document.select("span.carlos-pdf-image-unavailable")).hasSize(1);
        assertThat(document.select("p").text()).isEqualTo("before [image unavailable] after");
    }

    @Test
    @DisplayName("should leave images, tables and text alone")
    void shouldLeaveOtherMarkup_inSafeMode() {
        Document document = simplified("<body><img src=\"a.png\" alt=\"\"><table><tr><td>x</td></tr></table><p>y</p></body>");

        assertThat(document.select("img")).hasSize(1);
        assertThat(document.select("td").text()).isEqualTo("x");
        assertThat(document.select("p").text()).isEqualTo("y");
    }

    @Test
    @DisplayName("should reject a null document")
    void shouldRejectNull_whenDocumentMissing() {
        assertThatNullPointerException().isThrownBy(() -> SafeModeMarkup.simplify(null));
    }
}
