/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #6271 review: the Master Record edit form selects a patient's saved custom-field value by the option's
 * decoded value, so opening the form and saving it unchanged keeps the value. Each case renders the select
 * the way the form prints it, then "saves" it the way a browser does: the posted value is the decoded value
 * of the selected option, the last one marked selected, or the first option when none is.
 */
@Tag("unit")
@Tag("demographic")
@DisplayName("Master Record custom-field select")
class DemographicCustomFieldSelectUnitTest {

    /** A configured select whose saved option is not the first and holds an entity. */
    private static final String ENTITY_TEMPLATE = "<select name=\"FAKE_field\">"
            + "<option value=\"\">--</option>"
            + "<option value=\"Plain\">Plain</option>"
            + "<option value=\"A &amp; B\">A &amp; B</option>"
            + "</select>";

    /** What a browser posts for the rendered select. */
    private static String posted(String renderedSelect) {
        Elements options = Jsoup.parseBodyFragment(renderedSelect).select("option");
        Element chosen = null;
        for (Element option : options) {
            if (option.hasAttr("selected")) {
                chosen = option;
            }
        }
        if (chosen == null) {
            chosen = options.first();
        }
        return chosen == null ? "" : chosen.hasAttr("value") ? chosen.attr("value") : chosen.text();
    }

    @Test
    @DisplayName("should keep a saved value whose option holds an entity when the form is opened and saved")
    void shouldKeepTheSavedValue_whenItsOptionHoldsAnEntity() {
        String rendered = DemographicCustomFieldSelect.markSaved(ENTITY_TEMPLATE, "A & B");

        assertThat(posted(rendered)).isEqualTo("A & B");
        // Opened again after that save: still the same option, and only that one.
        String reopened = DemographicCustomFieldSelect.markSaved(ENTITY_TEMPLATE, posted(rendered));
        assertThat(posted(reopened)).isEqualTo("A & B");
        assertThat(Jsoup.parseBodyFragment(reopened).select("option[selected]")).hasSize(1);
    }

    @Test
    @DisplayName("should keep the template's default for a patient whose value was never saved")
    void shouldKeepTheTemplateDefault_whenNothingWasEverSaved() {
        String template = "<select name=\"FAKE_field\"><option value=\"\">--</option>"
                + "<option value=\"Plain\">Plain</option><option value=\"Default\" selected>Default</option></select>";

        assertThat(DemographicCustomFieldSelect.markSaved(template, null)).isEqualTo(template);
        assertThat(posted(DemographicCustomFieldSelect.markSaved(template, null))).isEqualTo("Default");
    }

    @Test
    @DisplayName("should reopen a cleared value cleared, over the template's default, when there is a blank option")
    void shouldSelectTheBlankOption_whenTheValueWasCleared() {
        String template = "<select name=\"FAKE_field\"><option value=\"\">--</option>"
                + "<option value=\"Plain\">Plain</option><option value=\"Default\" selected>Default</option></select>";

        assertThat(posted(DemographicCustomFieldSelect.markSaved(template, ""))).isEmpty();
        assertThat(posted(DemographicCustomFieldSelect.markSaved(template, "   "))).isEmpty();
        assertThat(DemographicCustomFieldSelect.markSaved(template, "")).contains("<option value=\"Default\">Default");
    }

    @Test
    @DisplayName("should leave the template as configured for a cleared value when it has no blank option")
    void shouldLeaveTheTemplateUnchanged_whenAClearedValueHasNoBlankOption() {
        String template = "<select name=\"FAKE_field\"><option value=\"Plain\">Plain</option>"
                + "<option value=\"Default\" selected>Default</option></select>";

        assertThat(DemographicCustomFieldSelect.markSaved(template, "")).isEqualTo(template);
    }

    @Test
    @DisplayName("should still show a saved value whose option the template now disables")
    void shouldSelectADisabledOption_whenItCarriesTheSavedValue() {
        String template = "<select name=\"FAKE_field\"><option value=\"\">--</option>"
                + "<option value=\"Retired\" disabled>Retired</option></select>";

        assertThat(DemographicCustomFieldSelect.markSaved(template, "Retired"))
                .contains("<option value=\"Retired\" selected disabled>Retired</option>");
    }

    @Test
    @DisplayName("should not read options from short comments, text-only elements or after an unclosed comment")
    void shouldSkipNonMarkup_forShortCommentsTextBodiesAndUnclosedComments() {
        String shortComment = "<select name=\"FAKE_field\"><!--><option value=\"A\">A</option>"
                + "<textarea><option value=\"B\">B</option></textarea><option value=\"B\">B</option></select>";
        String rendered = DemographicCustomFieldSelect.markSaved(shortComment, "B");
        assertThat(rendered).contains("<textarea><option value=\"B\">B</option></textarea>")
                .contains("</textarea><option value=\"B\" selected>B</option>");

        String unclosed = "<select name=\"FAKE_field\"><option value=\"A\">A</option>"
                + "<!-- <option value=\"B\">B</option></select>";
        assertThat(DemographicCustomFieldSelect.markSaved(unclosed, "B")).isEqualTo(unclosed);
        assertThat(DemographicCustomFieldSelect.markSaved(unclosed, "A")).contains("<option value=\"A\" selected>A");
    }

    @Test
    @DisplayName("should match an option without a value attribute by its text, as a browser posts it")
    void shouldMatchAnOptionByItsText_whenItHasNoValueAttribute() {
        String template = "<select name=\"FAKE_field\"><option>--</option><option>A &amp; B</option>"
                + "<option>\n   Two   words <b>here</b>\n</option></select>";

        assertThat(posted(DemographicCustomFieldSelect.markSaved(template, "A & B"))).isEqualTo("A & B");
        assertThat(posted(DemographicCustomFieldSelect.markSaved(template, "Two words here"))).isEqualTo("Two words here");
    }

    @Test
    @DisplayName("should not take text inside another attribute, a comment or a script for an option's value")
    void shouldIgnoreValueLookalikes_insideAttributesCommentsAndScripts() {
        String template = "<select name=\"FAKE_field\">"
                + "<option title=\"pick value=Yes\" value=\"No\">No</option>"
                + "<!-- <option value=\"Yes\">old</option> -->"
                + "<script>var s = '<option value=\"Yes\">';</script>"
                + "<option value=\"Yes\">Yes</option></select>";

        String rendered = DemographicCustomFieldSelect.markSaved(template, "Yes");

        assertThat(posted(rendered)).isEqualTo("Yes");
        assertThat(rendered).contains("<option value=\"Yes\" selected>Yes</option>")
                .contains("<!-- <option value=\"Yes\">old</option> -->")
                .contains("title=\"pick value=Yes\" value=\"No\">");
    }

    @Test
    @DisplayName("should compare the option value and the saved text both trimmed")
    void shouldMatchAPaddedOptionValue_toTheTrimmedSavedText() {
        String template = "<select name=\"FAKE_field\"><option value=\"\">--</option>"
                + "<option value=\" Plain \">Plain</option></select>";

        assertThat(DemographicCustomFieldSelect.markSaved(template, "Plain")).contains("value=\" Plain \" selected");
    }

    @Test
    @DisplayName("should treat a no-break space as a letter, not as a space")
    void shouldMatchANoBreakSpace_onlyToANoBreakSpace() {
        String template = "<select name=\"FAKE_field\"><option value=\"\">--</option>"
                + "<option value=\"A&nbsp;B\">x</option></select>";

        assertThat(posted(DemographicCustomFieldSelect.markSaved(template, "A B"))).isEqualTo("A B");
        assertThat(DemographicCustomFieldSelect.markSaved(template, "A B")).isEqualTo(template);
    }

    @Test
    @DisplayName("should mark the first of two options with the same value, inside optgroups too")
    void shouldMarkTheFirstOption_whenTwoCarryTheSavedValue() {
        String template = "<select name=\"FAKE_field\" multiple><optgroup label=\"One\">"
                + "<option value=\"x\">first</option></optgroup><optgroup label=\"Two\">"
                + "<option value=\"x\" selected>second</option></optgroup></select>";

        String rendered = DemographicCustomFieldSelect.markSaved(template, "x");

        assertThat(rendered).contains("<option value=\"x\" selected>first</option>")
                .contains("<option value=\"x\">second</option>");
    }

    @Test
    @DisplayName("should take off a selected attribute written against the quote before it")
    void shouldRemoveAGluedSelected_fromAnotherOption() {
        String template = "<select name=\"FAKE_field\"><option value=\"A\">A</option>"
                + "<option value=\"B\"selected>B</option></select>";

        String rendered = DemographicCustomFieldSelect.markSaved(template, "A");

        assertThat(posted(rendered)).isEqualTo("A");
        assertThat(rendered).contains("<option value=\"B\">B</option>");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "regular-expression characters | `<option value=\"a(b)[c]$1\\\\.*\">x</option>` | `a(b)[c]$1\\\\.*`",
            "a double quote, encoded       | `<option value=\"say &quot;hi&quot;\">x</option>` | `say \"hi\"`",
            "a double quote, single-quoted | `<option value='say \"hi\"'>x</option>` | `say \"hi\"`",
            "a numeric entity              | `<option value=\"A &#38; B\">x</option>` | `A & B`",
            "a hexadecimal entity          | `<option value=\"A &#x26; B\">x</option>` | `A & B`",
            "an accented letter            | `<option value=\"Montr&eacute;al\">x</option>` | `Montréal`",
            "a bare value                  | `<option value=Plain>x</option>` | `Plain`",
            "upper-case markup             | `<OPTION VALUE=\"Plain\">x</OPTION>` | `Plain`"
    })
    @DisplayName("should select the saved option however its value is written")
    void shouldSelectTheSavedOption_forEveryWayAValueIsWritten(String name, String savedOption, String saved) {
        String template = "<select name=\"FAKE_field\"><option value=\"\">--</option>" + savedOption + "</select>";

        assertThat(posted(DemographicCustomFieldSelect.markSaved(template, saved))).isEqualTo(saved);
    }

    @Test
    @DisplayName("should let the saved option win over a template default that comes after it")
    void shouldSelectOnlyTheSavedOption_whenTheTemplateHasALaterDefault() {
        String template = "<select name=\"FAKE_field\"><option value=\"A &amp; B\">A</option>"
                + "<option value=\"Default\" selected=\"selected\">Default</option></select>";

        String rendered = DemographicCustomFieldSelect.markSaved(template, "A & B");

        assertThat(posted(rendered)).isEqualTo("A & B");
        assertThat(rendered).contains("<option value=\"Default\">Default</option>");
    }

    @Test
    @DisplayName("should leave the word selected inside a quoted attribute alone")
    void shouldKeepALabel_thatContainsTheWordSelected() {
        String template = "<select name=\"FAKE_field\"><option value=\"\" title=\"not selected yet\">--</option>"
                + "<option value=\"Plain\">Plain</option></select>";

        String rendered = DemographicCustomFieldSelect.markSaved(template, "Plain");

        assertThat(rendered).contains("title=\"not selected yet\"");
        assertThat(posted(rendered)).isEqualTo("Plain");
    }

    @Test
    @DisplayName("should change nothing for a saved value no option carries")
    void shouldLeaveTheTemplateUnchanged_whenNoOptionCarriesTheSavedValue() {
        assertThat(DemographicCustomFieldSelect.markSaved(ENTITY_TEMPLATE, "FAKE retired option"))
                .isEqualTo(ENTITY_TEMPLATE);
    }

    @Test
    @DisplayName("should leave the template as it is for no saved value, trim a saved one, and print nothing for no template")
    void shouldTreatMissingValuesAsTheFormDid_forNullInput() {
        assertThat(DemographicCustomFieldSelect.markSaved(ENTITY_TEMPLATE, null)).isEqualTo(ENTITY_TEMPLATE);
        assertThat(posted(DemographicCustomFieldSelect.markSaved(ENTITY_TEMPLATE, "  Plain  "))).isEqualTo("Plain");
        assertThat(DemographicCustomFieldSelect.markSaved(null, "Plain")).isEmpty();
    }

    @Test
    @DisplayName("should never write the saved text into the page")
    void shouldNotCopyTheSavedText_intoTheTemplate() {
        String rendered = DemographicCustomFieldSelect.markSaved(ENTITY_TEMPLATE, "A & B");

        // Only " selected" is added; the saved text itself never reaches the HTML.
        assertThat(rendered.length()).isEqualTo(ENTITY_TEMPLATE.length() + " selected".length());
        assertThat(rendered).doesNotContain("value=\"A & B\"");
    }
}
