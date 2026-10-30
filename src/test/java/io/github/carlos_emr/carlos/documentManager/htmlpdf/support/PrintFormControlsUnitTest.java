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
import org.jsoup.select.Elements;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link PrintFormControls}: one group per row of the spec's form-control table, plus
 * attribute copying, sizing, boolean attributes and legacy upper-case markup. Mark characters are
 * asserted by code point so a wrong glyph cannot hide behind the constants it is built from.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("PrintFormControls Unit Tests")
class PrintFormControlsUnitTest {

    private static final int BALLOT_BOX_WITH_X = 0x2612;
    private static final int BALLOT_BOX = 0x2610;
    private static final int FISHEYE = 0x25C9;
    private static final int WHITE_CIRCLE = 0x25CB;
    private static final int BULLET = 0x2022;

    private static Document replaced(String bodyHtml) {
        Document document = Jsoup.parse("<html><body>" + bodyHtml + "</body></html>");
        PrintFormControls.replaceControls(document);
        return document;
    }

    private static String chars(int... codePoints) {
        return new String(codePoints, 0, codePoints.length);
    }

    /** The text of each element, including the empty ones ({@code Elements.eachText()} skips them). */
    private static List<String> texts(Elements elements) {
        return elements.stream().map(Element::text).toList();
    }

    private static Element only(Document document, String selector) {
        assertThat(document.select(selector)).as(selector).hasSize(1);
        return document.select(selector).first();
    }

    // ---- text-like inputs -------------------------------------------------------------------

    @ParameterizedTest(name = "[{index}] \"{0}\" is a number")
    @ValueSource(strings = {"0", "7", "-7", "+7", "007", "7.", "7.5", "-7.5", ".5", "-.5", "+.5", "7e3", "7E3", "7e-3", "7e+3", ".5e3", "7.e3", "7.5E-10",
            "1e999999999", "123456789012345678901234567890"})
    @DisplayName("should take a decimal number")
    void shouldTakeDecimalNumber(String text) {
        assertThat(PrintFormControls.isDecimalNumber(text)).isTrue();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" is not a number")
    @ValueSource(strings = {"", " ", "+", "-", ".", "+.", "-.", "e3", ".e3", "7e", "7e+", "7e-", "7.5.5", "7,5", "7 ", " 7", "7x", "x7", "NaN", "Infinity", "0x10",
            "7e3.5", "7ee3", "--7", "+-7", "\u0667", "7\u0661", "1_000"})
    @DisplayName("should refuse text that is not a decimal number")
    void shouldRefuseNonNumber(String text) {
        assertThat(PrintFormControls.isDecimalNumber(text)).isFalse();
    }

    @Test
    @DisplayName("should take a number as the pattern it replaces did, for random text")
    void shouldMatchTheOldPattern_forRandomText() {
        Pattern old = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?");
        String[] pieces = {"+", "-", ".", "e", "E", "0", "7", "42", "x", " ", "", "\u0667"};
        Random random = new Random(20261030);
        for (int i = 0; i < 50_000; i++) {
            StringBuilder text = new StringBuilder();
            for (int n = random.nextInt(8); n > 0; n--) {
                text.append(pieces[random.nextInt(pieces.length)]);
            }
            assertThat(PrintFormControls.isDecimalNumber(text.toString())).as(text.toString()).isEqualTo(old.matcher(text).matches());
        }
    }

    @Nested
    @DisplayName("text-like inputs")
    class TextInputs {

        @ParameterizedTest
        @ValueSource(strings = {"text", "email", "tel", "number", "date", "time", "datetime-local", "month", "week",
                "url", "search", "color"})
        @DisplayName("should print the value in a text span for every text-like type")
        void shouldPrintValue_forTextLikeTypes(String type) {
            Document document = replaced("<input type=\"" + type + "\" value=\"FAKE-value\" name=\"secret-field\">");

            Element span = only(document, "span.carlos-pdf-text");
            assertThat(span.text()).isEqualTo("FAKE-value");
            assertThat(span.attr("data-input-type")).isEqualTo(type);
            assertThat(document.select("input")).isEmpty();
            assertThat(document.html()).doesNotContain("secret-field");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "foo", "range", "Text", "TEXT"})
        @DisplayName("should print an input of an unknown type, or none, as text")
        void shouldPrintAsText_forUnknownOrMissingType(String type) {
            String typeAttribute = type.isEmpty() ? "" : " type=\"" + type + "\"";
            Document document = replaced("<input" + typeAttribute + " value=\"FAKE-value\">");

            Element span = only(document, "span.carlos-pdf-text");
            assertThat(span.text()).isEqualTo("FAKE-value");
            assertThat(span.attr("data-input-type")).isEqualTo("text");
        }

        @Test
        @DisplayName("should remove CR and LF from the value, as a browser does for these types")
        void shouldRemoveLineBreaks_fromValue() {
            Document document = replaced("<input type=\"text\" value=\"line one&#13;&#10;line&#10;two&#13;three\">");

            assertThat(only(document, "span.carlos-pdf-text").text()).isEqualTo("line oneline" + "twothree");
        }

        @Test
        @DisplayName("should print an empty span for an input without a value")
        void shouldPrintEmptySpan_whenNoValue() {
            Document document = replaced("<input type=\"text\">");

            Element span = only(document, "span.carlos-pdf-text");
            assertThat(span.text()).isEmpty();
            assertThat(span.childNodeSize()).isZero();
        }

        @Test
        @DisplayName("should keep markup in a value as text")
        void shouldEscapeValue_whenItLooksLikeMarkup() {
            Document document = replaced("<input type=\"text\" value=\"&lt;b&gt;bold&lt;/b&gt; &amp; more\">");

            Element span = only(document, "span.carlos-pdf-text");
            assertThat(span.text()).isEqualTo("<b>bold</b> & more");
            assertThat(span.select("b")).isEmpty();
        }
    }

    // ---- checkbox and radio -----------------------------------------------------------------

    @Nested
    @DisplayName("checkboxes and radio buttons")
    class Marks {

        @ParameterizedTest
        @ValueSource(strings = {"CHECKBOX", "Checkbox", "checkbox"})
        @DisplayName("should print the checked mark whatever the case of the type")
        void shouldPrintCheckedMark_forAnyTypeCase(String type) {
            Document document = replaced("<input TYPE=\"" + type + "\" CHECKED=\"checked\">");

            assertThat(only(document, "span.carlos-pdf-checkbox").text()).isEqualTo("☒");
        }

        @Test
        @DisplayName("should print U+2612 for a checked checkbox and U+2610 for an unchecked one")
        void shouldPrintCheckboxMarks_byCodePoint() {
            Document document = replaced("<input id=\"on\" type=\"checkbox\" checked><input id=\"off\" type=\"checkbox\">");

            assertThat(only(document, "#on").text()).isEqualTo(chars(BALLOT_BOX_WITH_X));
            assertThat(only(document, "#off").text()).isEqualTo(chars(BALLOT_BOX));
            assertThat(only(document, "#on").attr("data-input-type")).isEqualTo("checkbox");
        }

        @Test
        @DisplayName("should print U+25C9 for a selected radio button and U+25CB for the others")
        void shouldPrintRadioMarks_byCodePoint() {
            Document document = replaced("<input id=\"on\" type=\"radio\" checked><input id=\"off\" type=\"Radio\">");

            assertThat(only(document, "#on").text()).isEqualTo(chars(FISHEYE));
            assertThat(only(document, "#off").text()).isEqualTo(chars(WHITE_CIRCLE));
            assertThat(only(document, "#on").hasClass("carlos-pdf-radio")).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"checked=\"false\"", "checked=\"\"", "CHECKED", "checked=\"0\""})
        @DisplayName("should treat any checked attribute as checked, as HTML boolean attributes are")
        void shouldPrintCheckedMark_whenCheckedIsFalseString(String attribute) {
            Document document = replaced("<input type=\"checkbox\" " + attribute + ">");

            assertThat(only(document, "span.carlos-pdf-checkbox").text()).isEqualTo(chars(BALLOT_BOX_WITH_X));
        }

        @Test
        @DisplayName("should not size a mark with a minimum width")
        void shouldNotSizeMarks_withMinimumWidth() {
            Document document = replaced("<input type=\"checkbox\" size=\"30\">");

            assertThat(only(document, "span.carlos-pdf-checkbox").attr("style")).isEmpty();
        }
    }

    // ---- password ---------------------------------------------------------------------------

    @Nested
    @DisplayName("password inputs")
    class Passwords {

        @Test
        @DisplayName("should not reveal the length of a password value")
        void shouldNotRevealLength_forPasswordValue() {
            Document shortValue = replaced("<input type=\"password\" value=\"a\">");
            Document longValue = replaced("<input type=\"password\" value=\"FAKE-secret-1234\">");

            assertThat(only(shortValue, "span.carlos-pdf-password").text()).isEqualTo(chars(BULLET).repeat(8));
            assertThat(only(longValue, "span.carlos-pdf-password").text()).isEqualTo(chars(BULLET).repeat(8));
            assertThat(longValue.html()).doesNotContain("FAKE-secret-1234");
        }

        @Test
        @DisplayName("should print nothing for an empty or missing password value")
        void shouldPrintNothing_forEmptyPassword() {
            Document empty = replaced("<input type=\"password\" value=\"\">");
            Document missing = replaced("<input type=\"password\">");

            assertThat(only(empty, "span.carlos-pdf-password").text()).isEmpty();
            assertThat(only(missing, "span.carlos-pdf-password").text()).isEmpty();
        }
    }

    // ---- removed controls -------------------------------------------------------------------

    @Nested
    @DisplayName("controls that are removed")
    class Removed {

        @ParameterizedTest
        @ValueSource(strings = {"hidden", "submit", "button", "reset", "image", "file", "HIDDEN", "Submit"})
        @DisplayName("should remove hidden fields and buttons from the output")
        void shouldRemoveHiddenAndButtons_fromOutput(String type) {
            Document document = replaced("<p>before</p><input type=\"" + type + "\" name=\"FAKE-field\" value=\"FAKE-12345\">"
                    + "<p>after</p>");

            assertThat(document.text()).isEqualTo("before after");
            assertThat(document.html()).doesNotContain("FAKE-12345").doesNotContain("FAKE-field");
            assertThat(document.select("input")).isEmpty();
        }

        @Test
        @DisplayName("should remove every button, with its label")
        void shouldRemoveButtons_withTheirLabel() {
            Document document = replaced("<p>keep</p><button type=\"submit\">Save the FAKE record</button><button>Cancel</button>");

            assertThat(document.text()).isEqualTo("keep");
            assertThat(document.select("button")).isEmpty();
        }

        @Test
        @DisplayName("should cope with a control nested inside a button that is removed")
        void shouldNotFail_whenControlIsInsideRemovedButton() {
            Document document = replaced("<button><select><option selected>inside</option></select><input type=\"text\" value=\"x\"></button><p>ok</p>");

            assertThat(document.text()).isEqualTo("ok");
        }
    }

    // ---- select -----------------------------------------------------------------------------

    @Nested
    @DisplayName("select lists")
    class Selects {

        @Test
        @DisplayName("should print only the selected option of a single list")
        void shouldPrintSelectedOption_forSingleSelect() {
            Document document = replaced("<select><option>One</option><option selected>Two</option><option>Three</option></select>");

            Element span = only(document, "span.carlos-pdf-select");
            assertThat(span.text()).isEqualTo("Two");
            assertThat(span.attr("data-input-type")).isEqualTo("select");
            assertThat(document.html()).doesNotContain("One").doesNotContain("Three");
        }

        @Test
        @DisplayName("should print the first option when none is selected in a single list")
        void shouldPrintFirstOption_whenNoneSelected() {
            Document document = replaced("<select><option>One</option><option>Two</option></select>");

            assertThat(only(document, "span.carlos-pdf-select").text()).isEqualTo("One");
        }

        @Test
        @DisplayName("should print the last selected option when a single list has several")
        void shouldPrintLastSelected_whenSingleListHasSeveral() {
            Document document = replaced("<select><option selected>One</option><option SELECTED>Two</option><option>Three</option></select>");

            assertThat(only(document, "span.carlos-pdf-select").text()).isEqualTo("Two");
        }

        @Test
        @DisplayName("should join the selected options of a multiple list with a comma and a space")
        void shouldJoinSelectedOptions_forMultiSelect() {
            Document document = replaced("<select multiple><optgroup label=\"Group A\"><option selected>Alpha</option>"
                    + "<option>Beta</option></optgroup><optgroup label=\"Group B\"><option selected>Gamma</option>"
                    + "<option selected>Delta</option></optgroup></select>");

            Element span = only(document, "span.carlos-pdf-select");
            assertThat(span.text()).isEqualTo("Alpha, Gamma, Delta");
            assertThat(document.html()).doesNotContain("Beta").doesNotContain("Group A");
        }

        @Test
        @DisplayName("should print nothing when no option of a multiple list is selected")
        void shouldPrintNothing_whenMultipleListHasNoSelection() {
            Document document = replaced("<select multiple><option>One</option><option>Two</option></select>");

            assertThat(only(document, "span.carlos-pdf-select").text()).isEmpty();
        }

        @Test
        @DisplayName("should count options inside an optgroup")
        void shouldCountOptgroupOptions_forSingleSelect() {
            Document document = replaced("<select><optgroup label=\"G\"><option>First</option><option selected>Chosen</option></optgroup></select>");

            assertThat(only(document, "span.carlos-pdf-select").text()).isEqualTo("Chosen");
        }

        @Test
        @DisplayName("should print an option's label attribute, as a browser shows it, else its text")
        void shouldPrintLabel_whenOptionHasOne() {
            Document document = replaced("<select><option value=\"v1\" label=\"Shown label\" selected>Option text</option></select>"
                    + "<select><option value=\"v2\" label=\" \" selected>  Spaced   text </option></select>");

            assertThat(texts(document.select("span.carlos-pdf-select"))).containsExactly("Shown label", "Spaced text");
            assertThat(document.html()).doesNotContain("v1").doesNotContain("v2");
        }

        @Test
        @DisplayName("should print an empty span for a list without options")
        void shouldPrintEmptySpan_whenSelectHasNoOptions() {
            Document document = replaced("<select></select><select multiple></select>");

            assertThat(document.select("span.carlos-pdf-select")).hasSize(2);
            assertThat(texts(document.select("span.carlos-pdf-select"))).containsOnly("");
        }
    }

    // ---- textarea ---------------------------------------------------------------------------

    @Nested
    @DisplayName("text areas")
    class Textareas {

        @Test
        @DisplayName("should print a text area as a div that keeps the newlines")
        void shouldKeepNewlines_inTextareaDiv() {
            Document document = replaced("<textarea>line one\nline two\r\nline three\rline four</textarea>");

            Element div = only(document, "div.carlos-pdf-textarea");
            assertThat(div.wholeText()).isEqualTo("line one\nline two\nline three\nline four");
            assertThat(div.attr("data-input-type")).isEqualTo("textarea");
            assertThat(document.select("textarea")).isEmpty();
        }

        @Test
        @DisplayName("should print markup in a text area as text")
        void shouldEscapeTextarea_whenItLooksLikeMarkup() {
            Document document = replaced("<textarea>&lt;script&gt;x()&lt;/script&gt; &amp; y</textarea>");

            Element div = only(document, "div.carlos-pdf-textarea");
            assertThat(div.wholeText()).isEqualTo("<script>x()</script> & y");
            assertThat(div.select("script")).isEmpty();
        }

        @Test
        @DisplayName("should size a text area from cols and rows, with 20 and 2 as defaults")
        void shouldSizeTextarea_fromColsAndRows() {
            Document defaults = replaced("<textarea></textarea>");
            Document sized = replaced("<textarea cols=\"40\" rows=\"5\"></textarea>");

            assertThat(only(defaults, "div.carlos-pdf-textarea").attr("style")).isEqualTo("min-width: 11em; min-height: 2.4em");
            assertThat(only(sized, "div.carlos-pdf-textarea").attr("style")).isEqualTo("min-width: 22em; min-height: 6em");
        }

        @Test
        @DisplayName("should not set a minimum the text area's own inline width or height already decides")
        void shouldRespectInlineWidthAndHeight_forTextarea() {
            Document width = replaced("<textarea style=\"width: 300px\" cols=\"40\" rows=\"5\"></textarea>");
            Document height = replaced("<textarea style=\"height: 90px\" cols=\"40\" rows=\"5\"></textarea>");
            Document both = replaced("<textarea style=\"width:1px;height:2px\"></textarea>");
            Document lookAlike = replaced("<textarea style=\"min-width: 5px; line-height: 3\"></textarea>");

            assertThat(only(width, "div").attr("style")).isEqualTo("width: 300px; min-height: 6em");
            assertThat(only(height, "div").attr("style")).isEqualTo("height: 90px; min-width: 22em");
            assertThat(only(both, "div").attr("style")).isEqualTo("width:1px;height:2px");
            assertThat(only(lookAlike, "div").attr("style")).isEqualTo("min-width: 5px; line-height: 3; min-width: 11em; min-height: 2.4em");
        }
    }

    // ---- progress and meter -----------------------------------------------------------------

    @Nested
    @DisplayName("progress and meter")
    class ProgressAndMeter {

        @Test
        @DisplayName("should print value and max of a progress bar, with max defaulting to 1")
        void shouldPrintValueOverMax_forProgress() {
            Document document = replaced("<progress id=\"a\" value=\"30\" max=\"100\"></progress>"
                    + "<progress id=\"b\" value=\"0.5\"></progress>"
                    + "<progress id=\"c\" value=\"2\" max=\"0\"></progress>");

            assertThat(only(document, "#a").text()).isEqualTo("30/100");
            assertThat(only(document, "#b").text()).isEqualTo("0.5/1");
            assertThat(only(document, "#c").text()).isEqualTo("2/1");
            assertThat(only(document, "#a").hasClass("carlos-pdf-text")).isTrue();
            assertThat(only(document, "#a").attr("data-input-type")).isEqualTo("progress");
        }

        @Test
        @DisplayName("should print nothing for an indeterminate progress bar")
        void shouldPrintNothing_forIndeterminateProgress() {
            Document document = replaced("<progress></progress><progress value=\"abc\" max=\"10\"></progress>");

            assertThat(texts(document.select("span.carlos-pdf-text"))).containsExactly("", "");
        }

        @Test
        @DisplayName("should print the value of a meter")
        void shouldPrintValue_forMeter() {
            Document document = replaced("<meter id=\"a\" value=\"0.7\" min=\"0\" max=\"1\">70%</meter><meter id=\"b\">fallback</meter>");

            assertThat(only(document, "#a").text()).isEqualTo("0.7");
            assertThat(only(document, "#a").attr("data-input-type")).isEqualTo("meter");
            assertThat(only(document, "#b").text()).isEmpty();
            assertThat(document.html()).doesNotContain("fallback").doesNotContain("70%");
        }
    }

    // ---- attributes, sizing and order -------------------------------------------------------

    @Nested
    @DisplayName("attributes, sizing and order")
    class AttributesAndSizing {

        @Test
        @DisplayName("should copy id, class, style, title, dir and lang, and nothing else")
        void shouldCopyOnlyListedAttributes_toReplacement() {
            Document document = replaced("<input type=\"text\" id=\"patient-name\" class=\"wide narrow\" style=\"color: red\""
                    + " title=\"Name\" dir=\"rtl\" lang=\"he\" name=\"FAKE-name-field\" value=\"FAKE-value\""
                    + " onclick=\"x()\" placeholder=\"FAKE-hint\" maxlength=\"5\" data-extra=\"y\" readonly>");

            Element span = only(document, "span");
            assertThat(span.id()).isEqualTo("patient-name");
            assertThat(span.classNames()).containsExactly("wide", "narrow", "carlos-pdf-control", "carlos-pdf-text");
            assertThat(span.attr("style")).isEqualTo("color: red; min-width: 11em");
            assertThat(span.attr("title")).isEqualTo("Name");
            assertThat(span.attr("dir")).isEqualTo("rtl");
            assertThat(span.attr("lang")).isEqualTo("he");
            assertThat(span.attr("data-input-type")).isEqualTo("text");
            assertThat(span.attributes().asList().stream().map(a -> a.getKey()).toList())
                    .containsExactlyInAnyOrder("id", "class", "style", "title", "dir", "lang", "data-input-type");
            assertThat(document.html()).doesNotContain("FAKE-name-field").doesNotContain("FAKE-hint").doesNotContain("onclick");
        }

        @Test
        @DisplayName("should size a text input from size, with 20 as the default")
        void shouldSizeTextInput_fromSizeAttribute() {
            Document document = replaced("<input id=\"a\" type=\"text\"><input id=\"b\" type=\"text\" size=\"10\">"
                    + "<input id=\"c\" type=\"text\" size=\"15\"><input id=\"d\" type=\"text\" size=\"0\">"
                    + "<input id=\"e\" type=\"text\" size=\"abc\"><input id=\"f\" type=\"password\" size=\"10\">");

            assertThat(only(document, "#a").attr("style")).isEqualTo("min-width: 11em");
            assertThat(only(document, "#b").attr("style")).isEqualTo("min-width: 5.5em");
            assertThat(only(document, "#c").attr("style")).isEqualTo("min-width: 8.25em");
            assertThat(only(document, "#d").attr("style")).isEqualTo("min-width: 11em");
            assertThat(only(document, "#e").attr("style")).isEqualTo("min-width: 11em");
            assertThat(only(document, "#f").attr("style")).isEqualTo("min-width: 5.5em");
        }

        @Test
        @DisplayName("should not set a minimum width when the input's own style sets a width")
        void shouldRespectInlineWidth_forTextInput() {
            Document document = replaced("<input id=\"a\" type=\"text\" style=\"width: 120px\" size=\"40\">"
                    + "<input id=\"b\" type=\"text\" style=\"max-width: 120px\">");

            assertThat(only(document, "#a").attr("style")).isEqualTo("width: 120px");
            assertThat(only(document, "#b").attr("style")).isEqualTo("max-width: 120px; min-width: 11em");
        }

        @Test
        @DisplayName("should cap an absurd size, cols or rows so one attribute cannot widen the whole page")
        void shouldCapAbsurdSizes_forMinimums() {
            Document document = replaced("<input id=\"a\" type=\"text\" size=\"99999999999\">"
                    + "<textarea id=\"b\" cols=\"99999\" rows=\"99999\"></textarea>");

            assertThat(only(document, "#a").attr("style")).isEqualTo("min-width: 110em");
            assertThat(only(document, "#b").attr("style")).isEqualTo("min-width: 110em; min-height: 120em");
        }

        @Test
        @DisplayName("should keep document order and leave the surrounding markup alone")
        void shouldKeepDocumentOrder_whenReplacing() {
            Document document = replaced("<form action=\"/x\"><table><tr><td>Name</td><td><input type=\"text\" value=\"A\"></td></tr>"
                    + "<tr><td><input type=\"checkbox\" checked> agree</td><td><select><option selected>B</option></select></td></tr></table>"
                    + "<label>Notes <textarea>C</textarea></label><input type=\"submit\" value=\"Go\"></form>");

            assertThat(document.select("form").first().text()).isEqualTo("Name A " + chars(BALLOT_BOX_WITH_X) + " agree B Notes C");
            assertThat(document.select("table td")).hasSize(4);
            assertThat(document.select("form").attr("action")).isEqualTo("/x");
        }

        @Test
        @DisplayName("should leave a document without controls exactly as it was")
        void shouldChangeNothing_whenNoControls() {
            Document document = Jsoup.parse("<html><body><p>plain <b>text</b></p></body></html>");
            String before = document.html();

            PrintFormControls.replaceControls(document);

            assertThat(document.html()).isEqualTo(before);
        }

        @Test
        @DisplayName("should reject a null document")
        void shouldRejectNull_whenDocumentMissing() {
            assertThatNullPointerException().isThrownBy(() -> PrintFormControls.replaceControls(null));
        }
    }
}
