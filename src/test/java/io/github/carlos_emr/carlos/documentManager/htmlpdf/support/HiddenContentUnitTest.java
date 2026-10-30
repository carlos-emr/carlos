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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link HiddenContent}: what the markup hid is removed, and nothing else is.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("HiddenContent Unit Tests")
class HiddenContentUnitTest {

    private static Document cleaned(String body) {
        Document document = Jsoup.parse("<html><head></head><body>" + body + "</body></html>");
        HiddenContent.remove(document);
        return document;
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"<p hidden>FAKE-Hidden</p>", "<p HIDDEN=\"\">FAKE-Hidden</p>", "<p hidden=\"until-found\">FAKE-Hidden</p>",
            "<p style=\"display:none\">FAKE-Hidden</p>", "<p style=\"DISPLAY : NONE\">FAKE-Hidden</p>",
            "<p style=\"color: red; display: none !important\">FAKE-Hidden</p>", "<p style=\"display:block; display:none\">FAKE-Hidden</p>",
            "<p style=\"visibility:hidden\">FAKE-Hidden</p>", "<p style=\"visibility: collapse\">FAKE-Hidden</p>",
            "<div><p style=\"display:none\"><b>FAKE-Hidden</b></p></div>", "<table><tr><td style=\"display:none\">FAKE-Hidden</td><td>x</td></tr></table>",
            "<span hidden style=\"color:red\">FAKE-Hidden</span>", "<div hidden><p style=\"display:none\">FAKE-Hidden</p></div>"})
    @DisplayName("should remove an element the markup hides, with everything inside it")
    void shouldRemoveHiddenElements(String markup) {
        Document document = cleaned("<p>FAKE-Shown</p>" + markup);

        assertThat(document.body().text()).contains("FAKE-Shown").doesNotContain("FAKE-Hidden");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"<p>FAKE-Shown</p>", "<p style=\"display:block\">FAKE-Shown</p>", "<p style=\"display:none; display:block\">FAKE-Shown</p>",
            "<p style=\"visibility:visible\">FAKE-Shown</p>", "<p style=\"color:red\">FAKE-Shown</p>", "<p style=\"display: noneish\">FAKE-Shown</p>",
            "<p style=\"background: url(display:none)\">FAKE-Shown</p>", "<p style=\"max-display:none\">FAKE-Shown</p>",
            "<p style=\"text-visibility:hidden\">FAKE-Shown</p>", "<p style=\"visibility: hidden-ish\">FAKE-Shown</p>",
            "<p class=\"hidePrint\">FAKE-Shown</p>", "<p aria-hidden=\"true\">FAKE-Shown</p>", "<input type=\"hidden\" value=\"FAKE-Shown\">"})
    @DisplayName("should keep what is not hidden by the markup, including what a style sheet might hide")
    void shouldKeepVisibleElements(String markup) {
        Document document = cleaned(markup);
        String before = Jsoup.parse("<html><head></head><body>" + markup + "</body></html>").body().html();

        assertThat(document.body().html()).isEqualTo(before);
    }

    @Test
    @DisplayName("should never remove the html or body element, though an author hid it until a script shows it")
    void shouldKeepHtmlAndBody_whateverTheyAreHiddenWith() {
        Document document = Jsoup.parse("<html hidden><head></head><body style=\"display:none\" hidden><p>FAKE-Shown</p></body></html>");

        HiddenContent.remove(document);

        assertThat(document.body()).isNotNull();
        assertThat(document.body().text()).isEqualTo("FAKE-Shown");
    }

    @Test
    @DisplayName("should remove a hidden element nested in a hidden one once, and keep its visible siblings")
    void shouldRemoveNested_andKeepSiblings() {
        Document document = cleaned("<div hidden><span style=\"display:none\">FAKE-Inner</span><i hidden>FAKE-Deep</i></div><p>FAKE-One</p><p>FAKE-Two</p>");

        assertThat(document.body().children().eachText()).containsExactly("FAKE-One", "FAKE-Two");
    }

    @Test
    @DisplayName("should refuse a null document")
    void shouldRefuse_nullDocument() {
        assertThatNullPointerException().isThrownBy(() -> HiddenContent.remove(null));
    }
}
