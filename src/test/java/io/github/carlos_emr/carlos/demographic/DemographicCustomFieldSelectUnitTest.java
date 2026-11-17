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
    @DisplayName("should show the defect it fixes: matching the template's source text missed the option")
    void shouldNotFindTheOption_byItsSourceText() {
        // What the form did before: look for value="<saved text>" in the template.
        String literal = ENTITY_TEMPLATE.replace("value=\"A & B\"", "value=\"A & B\" selected");

        assertThat(posted(literal)).as("the first option is posted instead").isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "regular-expression characters | `<option value=\"a(b)[c]$1\\\\.*\">x</option>` | `a(b)[c]$1\\\\.*`",
            "a double quote, encoded       | `<option value=\"say &quot;hi&quot;\">x</option>` | `say \"hi\"`",
            "a double quote, single-quoted | `<option value='say \"hi\"'>x</option>` | `say \"hi\"`",
            "a numeric entity              | `<option value=\"A &#38; B\">x</option>` | `A & B`",
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
    @DisplayName("should read a missing or blank saved value as empty, and print nothing for no template")
    void shouldTreatMissingValuesAsTheFormDid_forNullInput() {
        assertThat(posted(DemographicCustomFieldSelect.markSaved(ENTITY_TEMPLATE, null))).isEmpty();
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
