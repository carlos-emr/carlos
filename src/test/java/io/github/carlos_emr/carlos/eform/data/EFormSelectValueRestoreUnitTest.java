/**
 * Copyright (c) 2026 CARLOS Contributors
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * CARLOS EMR
 */
package io.github.carlos_emr.carlos.eform.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * Pins which {@code <option>} a saved eForm marks as selected.
 *
 * <p>An option's value is its {@code value} attribute when it has one and its text otherwise.
 * The save used to compare the submitted value with the attribute alone, which reads as an empty
 * string when the attribute is missing: a select submitted blank then marked every value-less
 * option, and the reopened, printed and faxed form showed the last of them.
 *
 * @since 2026-11-12
 */
@Tag("unit")
@Tag("fast")
@DisplayName("eForm select restore")
class EFormSelectValueRestoreUnitTest extends CarlosUnitTestBase {

    private static final String VALUELESS =
            "<select name=\"s\"><option></option><option>Yes</option><option>No</option></select>";

    @BeforeEach
    void registerDependencies() {
        registerMock(NioFileManager.class, mock(NioFileManager.class));
    }

    @Test
    @DisplayName("should select only the empty option when a select of value-less options is submitted blank")
    void shouldSelectOnlyEmptyOption_whenValuelessSelectSubmittedBlank() {
        Document saved = save(VALUELESS, "s", "");

        assertThat(selectedTexts(saved, "s")).containsExactly("");
        assertThat(saved.select("select[name=s] option")).hasSize(3);
    }

    @Test
    @DisplayName("should select the option whose text was submitted when it has no value attribute")
    void shouldSelectOptionByText_whenOptionHasNoValueAttribute() {
        assertThat(selectedTexts(save(VALUELESS, "s", "Yes"), "s")).containsExactly("Yes");
        assertThat(selectedTexts(save(VALUELESS, "s", "No"), "s")).containsExactly("No");
    }

    @Test
    @DisplayName("should compare the collapsed option text the browser submits")
    void shouldMatchCollapsedText_whenOptionTextHasSurroundingWhitespace() {
        String html = "<select name=\"s\"><option></option><option>\n   Not   sure \n</option></select>";

        assertThat(selectedTexts(save(html, "s", "Not sure"), "s")).containsExactly("Not sure");
    }

    @Test
    @DisplayName("should prefer the value attribute over the text in a select that mixes both")
    void shouldUseValueAttributeFirst_whenOptionsMixAttributeAndText() {
        String html = "<select name=\"s\"><option value=\"\">Choose</option><option value=\"y\">Yes</option>"
                + "<option>No</option><option value=\"No\">Other</option></select>";

        assertThat(selectedTexts(save(html, "s", "y"), "s")).containsExactly("Yes");
        // "Yes" is the label of an option whose value is "y": the browser never submits it.
        assertThat(selectedTexts(save(html, "s", "Yes"), "s")).isEmpty();
        // Two options carry the value "No"; a single select submits the first.
        assertThat(selectedTexts(save(html, "s", "No"), "s")).containsExactly("No");
        assertThat(selectedTexts(save(html, "s", ""), "s")).containsExactly("Choose");
    }

    @Test
    @DisplayName("should clear a template default when the select is submitted blank")
    void shouldClearTemplateDefault_whenSelectSubmittedBlank() {
        String html = "<select name=\"s\"><option value=\"\">--</option><option value=\"a\" selected=\"selected\">A</option>"
                + "<option selected>B</option></select>";

        assertThat(selectedTexts(save(html, "s", ""), "s")).containsExactly("--");
    }

    @Test
    @DisplayName("should select nothing when a blank submission matches no option")
    void shouldSelectNothing_whenBlankSubmissionMatchesNoOption() {
        String html = "<select name=\"s\"><option value=\"a\" selected>A</option><option>B</option></select>";

        assertThat(selectedTexts(save(html, "s", ""), "s")).isEmpty();
    }

    @Test
    @DisplayName("should select only the first of two options that share a value")
    void shouldSelectFirstMatchOnly_whenOptionsShareValue() {
        String html = "<select name=\"s\"><option value=\"x\">One</option><option value=\"x\">Two</option></select>";

        assertThat(selectedTexts(save(html, "s", "x"), "s")).containsExactly("One");
    }

    @Test
    @DisplayName("should skip a disabled option that shares the submitted value")
    void shouldSkipDisabledOption_whenEnabledOptionSharesValue() {
        String html = "<select name=\"s\"><option value=\"\" disabled selected>Choose one</option>"
                + "<option value=\"\">None</option><option>Yes</option></select>";

        assertThat(selectedTexts(save(html, "s", ""), "s")).containsExactly("None");
    }

    @Test
    @DisplayName("should select every submitted option of a multiple select")
    void shouldSelectEverySubmittedOption_forMultipleSelect() {
        String html = "<select name=\"m\" multiple><option>a</option><option value=\"b\">B</option>"
                + "<option selected>c</option><option></option></select>";
        EForm form = formOf(html);

        form.setValues(list("m"), list("a"), Map.of("m", List.of("a", "b")));

        assertThat(selectedTexts(Jsoup.parse(form.getFormHtml()), "m")).containsExactly("a", "B");
    }

    @Test
    @DisplayName("should select the blank option of a multiple select only when it was submitted")
    void shouldSelectBlankOption_forMultipleSelectSubmittedBlank() {
        String html = "<select name=\"m\" multiple><option></option><option>a</option><option>b</option></select>";
        EForm form = formOf(html);

        form.setValues(list("m"), list(""), Map.of("m", List.of("", "b")));

        assertThat(selectedTexts(Jsoup.parse(form.getFormHtml()), "m")).containsExactly("", "b");
    }

    @Test
    @DisplayName("should select one option per submitted value when a multiple select repeats a value")
    void shouldSelectOneOptionPerValue_forMultipleSelectWithRepeatedValue() {
        String html = "<select name=\"m\" multiple><option value=\"x\">One</option><option value=\"x\">Two</option>"
                + "<option value=\"x\">Three</option></select>";
        EForm form = formOf(html);

        form.setValues(list("m"), list("x"), Map.of("m", List.of("x", "x")));

        assertThat(selectedTexts(Jsoup.parse(form.getFormHtml()), "m")).containsExactly("One", "Two");
    }

    @Test
    @DisplayName("should use the single value when no full submission is supplied for a multiple select")
    void shouldUseSingleValue_forMultipleSelectWithoutSubmittedValues() {
        String html = "<select name=\"m\" multiple><option selected>a</option><option>b</option></select>";

        assertThat(selectedTexts(save(html, "m", "b"), "m")).containsExactly("b");
    }

    @Test
    @DisplayName("should use only the first submitted value for a single select")
    void shouldUseFirstValueOnly_forSingleSelectWithSeveralSubmittedValues() {
        EForm form = formOf(VALUELESS);

        form.setValues(list("s"), list("Yes"), Map.of("s", List.of("Yes", "No")));

        assertThat(selectedTexts(Jsoup.parse(form.getFormHtml()), "s")).containsExactly("Yes");
    }

    @Test
    @DisplayName("should still clear a text, textarea and radio default that was submitted empty")
    void shouldClearOtherDefaults_whenSubmittedEmpty() {
        String html = "<input type=\"text\" name=\"t\" value=\"default\">"
                + "<textarea name=\"a\">default</textarea>"
                + "<input type=\"radio\" name=\"r\" value=\"\"><input type=\"radio\" name=\"r\" value=\"x\" checked>";
        EForm form = formOf(html);

        form.setValues(list("t", "a", "r"), list("", "", ""));

        Document saved = Jsoup.parse(form.getFormHtml());
        assertThat(saved.selectFirst("input[name=t]").attr("value")).isEmpty();
        assertThat(saved.selectFirst("textarea[name=a]").text()).isEmpty();
        assertThat(saved.select("input[name=r][checked]")).hasSize(1);
        assertThat(saved.selectFirst("input[name=r][checked]").attr("value")).isEmpty();
    }

    private static Document save(String controls, String name, String value) {
        EForm form = formOf(controls);
        form.setValues(list(name), list(value));
        return Jsoup.parse(form.getFormHtml());
    }

    private static EForm formOf(String controls) {
        EForm form = new EForm();
        form.setFormHtml("<html><head><title>FAKE form</title></head><body><form method=\"post\">"
                + controls + "</form></body></html>");
        return form;
    }

    private static List<String> selectedTexts(Document saved, String name) {
        return saved.select("select[name=" + name + "] option[selected]").stream().map(Element::text).toList();
    }

    private static ArrayList<String> list(String... values) {
        return new ArrayList<>(List.of(values));
    }
}
