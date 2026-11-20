/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.eform.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.stream.Stream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.carlos_emr.carlos.commn.dao.EFormValueDao;
import io.github.carlos_emr.carlos.eform.EFormLoader;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * A select filled from a database tag ({@code oscarDB=...}) chooses the option a browser would submit as exactly
 * the database output: decoded, whole, never a prefix, never through a pattern built from the value, and never
 * through text that only looks like an option (a comment, a script, a title attribute). Synthetic values only.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("eForm select filled from a database tag")
class EFormSelectApPrefillUnitTest extends CarlosUnitTestBase {

    @BeforeEach
    void registerDependencies() {
        registerMock(NioFileManager.class, mock(NioFileManager.class));
        registerMock(EFormValueDao.class, mock(EFormValueDao.class));
    }

    /** The select markup with {@code " selected"} put in by {@link EForm#selectedOptionInsertAt}, or unchanged. */
    private static String choose(String select, String wanted) {
        int at = EForm.selectedOptionInsertAt(select, 0, select.length(), wanted);
        return at < 0 ? select : select.substring(0, at) + " selected" + select.substring(at);
    }

    static Stream<Arguments> options() {
        return Stream.of(
                Arguments.of("sanity prefix sibling",
                        "<option value=\"Yes, with help\">a</option><option value=\"Yes\">b</option>",
                        "Yes",
                        "<option value=\"Yes, with help\">a</option><option selected value=\"Yes\">b</option>"),
                Arguments.of("A trailing space then text with value=",
                        "<option value=\"A\" >x value=\"B\"</option><option value=\"B\">y</option>",
                        "B",
                        "<option value=\"A\" >x value=\"B\"</option><option selected value=\"B\">y</option>"),
                Arguments.of("A plain",
                        "<option value=\"A\" >x</option><option value=\"B\" >y</option>",
                        "B",
                        "<option value=\"A\" >x</option><option selected value=\"B\" >y</option>"),
                Arguments.of("B self-closing",
                        "<option value=\"A\"/>x<option value=\"B\"/>y",
                        "B",
                        "<option value=\"A\"/>x<option selected value=\"B\"/>y"),
                Arguments.of("C comment first",
                        "<!-- <option value=\"Yes\"> --><option value=\"Yes\">y</option>",
                        "Yes",
                        "<!-- <option value=\"Yes\"> --><option selected value=\"Yes\">y</option>"),
                Arguments.of("D commented old option",
                        "<option value=\"No\">n</option><!--<option value=\"Yes\">old</option>--><option value=\"Yes\">y</option>",
                        "Yes",
                        "<option value=\"No\">n</option><!--<option value=\"Yes\">old</option>--><option selected value=\"Yes\">y</option>"),
                Arguments.of("E title decoy",
                        "<option title=\"value=Yes\" value=\"No\">n</option><option value=\"Yes\">y</option>",
                        "Yes",
                        "<option title=\"value=Yes\" value=\"No\">n</option><option selected value=\"Yes\">y</option>"),
                Arguments.of("F > in quoted attr",
                        "<option title=\"a>b\" value=\"Yes\">y</option>",
                        "Yes",
                        "<option selected title=\"a>b\" value=\"Yes\">y</option>"),
                Arguments.of("G newline in tag",
                        "<option\n  value=\"Yes\"\n>y</option>",
                        "Yes",
                        "<option selected\n  value=\"Yes\"\n>y</option>"),
                Arguments.of("H uppercase",
                        "<OPTION VALUE=\"yes\">y</OPTION>",
                        "YES",
                        "<OPTION selected VALUE=\"yes\">y</OPTION>"),
                Arguments.of("I no value attr",
                        "<option>Yes</option><option value=\"No\">n</option>",
                        "Yes",
                        "<option selected>Yes</option><option value=\"No\">n</option>"),
                Arguments.of("J wanted literal &amp;",
                        "<option value=\"Smith &amp; Sons\">s</option>",
                        "Smith &amp; Sons",
                        "<option selected value=\"Smith &amp; Sons\">s</option>"),
                Arguments.of("K empty output, placeholder first",
                        "<option value=\"\">-- choose --</option><option value=\"Yes\">y</option>",
                        "",
                        "<option selected value=\"\">-- choose --</option><option value=\"Yes\">y</option>"),
                Arguments.of("K2 empty output, disabled placeholder first",
                        "<option disabled value=\"\">-- choose --</option><option value=\"Yes\">y</option>",
                        "",
                        "<option selected disabled value=\"\">-- choose --</option><option value=\"Yes\">y</option>"),
                Arguments.of("K3 empty output, value-less first option",
                        "<option>None</option><option value=\"A\">a</option>",
                        "",
                        "<option>None</option><option selected value=\"A\">a</option>"),
                Arguments.of("K4 empty output, plain first option",
                        "<option value=\"A\">a</option><option value=\"B\">b</option>",
                        "",
                        "<option selected value=\"A\">a</option><option value=\"B\">b</option>"),
                Arguments.of("L1 empty output, tab before value on the first option",
                        "<option\tvalue=\"A\">x</option><option value=\"B\">y</option>",
                        "",
                        "<option selected\tvalue=\"A\">x</option><option value=\"B\">y</option>"),
                Arguments.of("K5 empty output, no option has a value attribute",
                        "<option>None</option><option>A</option>",
                        "",
                        "<option>None</option><option>A</option>"),
                Arguments.of("L template selected earlier",
                        "<option value=\"No\" selected>n</option><option value=\"Yes\">y</option>",
                        "Yes",
                        "<option value=\"No\" selected>n</option><option selected value=\"Yes\">y</option>"),
                Arguments.of("M already selected same",
                        "<option value=\"Yes\" selected>y</option>",
                        "Yes",
                        "<option selected value=\"Yes\" selected>y</option>"),
                Arguments.of("N duplicate value attr",
                        "<option value=\"No\" value=\"Yes\">n</option><option value=\"Yes\">y</option>",
                        "Yes",
                        "<option value=\"No\" value=\"Yes\">n</option><option selected value=\"Yes\">y</option>"),
                Arguments.of("O boolean attr before value",
                        "<option disabled value=\"Yes\">y</option>",
                        "Yes",
                        "<option selected disabled value=\"Yes\">y</option>"),
                Arguments.of("P spaces around =",
                        "<option value = \"Yes\">y</option>",
                        "Yes",
                        "<option selected value = \"Yes\">y</option>"),
                Arguments.of("Q unquoted then />",
                        "<option value=Yes/>y",
                        "Yes",
                        "<option value=Yes/>y"),
                Arguments.of("R optgroup decoy",
                        "<optgroup value=\"Yes\" label=\"g\"><option value=\"No\">n</option></optgroup>",
                        "Yes",
                        "<optgroup value=\"Yes\" label=\"g\"><option value=\"No\">n</option></optgroup>"),
                Arguments.of("S unterminated comment",
                        "<!-- <option value=\"Yes\"><option value=\"Yes\">y</option>",
                        "Yes",
                        "<!-- <option value=\"Yes\"><option value=\"Yes\">y</option>"),
                Arguments.of("T script body",
                        "<script>var s=\"<option value=Yes>\";</script><option value=\"Yes\">y</option>",
                        "Yes",
                        "<script>var s=\"<option value=Yes>\";</script><option selected value=\"Yes\">y</option>"),
                Arguments.of("U text with entity",
                        "<option>Smith &amp; Sons</option>",
                        "Smith & Sons",
                        "<option selected>Smith &amp; Sons</option>"),
                Arguments.of("V text with spaces/newline",
                        "<option>\n  Yes,\n   with help </option>",
                        "Yes, with help",
                        "<option selected>\n  Yes,\n   with help </option>"),
                Arguments.of("W unterminated tag",
                        "<option value=\"Yes\"",
                        "Yes",
                        "<option value=\"Yes\""),
                Arguments.of("X stray equals sign before the attributes",
                        "<option =x value=\"Yes\">y</option>",
                        "Yes",
                        "<option selected =x value=\"Yes\">y</option>"),
                Arguments.of("Y style body hides an option",
                        "<style>.a{content:\"<option value=Yes>\"}</style><option value=\"Yes\">y</option>",
                        "Yes",
                        "<style>.a{content:\"<option value=Yes>\"}</style><option selected value=\"Yes\">y</option>"),
                Arguments.of("Z script never closed",
                        "<script>var s=1;<option value=\"Yes\">y</option>",
                        "Yes",
                        "<script>var s=1;<option value=\"Yes\">y</option>"),
                Arguments.of("Z2 script end tag without its closing bracket",
                        "<script>var s=1;</script",
                        "Yes",
                        "<script>var s=1;</script"),
                Arguments.of("Z3 attribute name runs to the end",
                        "<option value=\"No\" disabled",
                        "Yes",
                        "<option value=\"No\" disabled"),
                Arguments.of("Z4 equals sign is the last character",
                        "<option value=",
                        "Yes",
                        "<option value="),
                Arguments.of("Z5 quote never closed",
                        "<option value=\"Yes>y</option>",
                        "Yes",
                        "<option value=\"Yes>y</option>"),
                Arguments.of("Z6 unquoted value ends at the closing bracket",
                        "<option value=Yes>y</option>",
                        "Yes",
                        "<option selected value=Yes>y</option>"),
                Arguments.of("Z7 unquoted value ends at white space",
                        "<option value=Yes disabled>y</option>",
                        "Yes",
                        "<option selected value=Yes disabled>y</option>"),
                Arguments.of("Z8 option text runs to the end of the markup",
                        "<option>Yes",
                        "Yes",
                        "<option selected>Yes"),
                Arguments.of("Z9 a longer tag name is not an option",
                        "<options value=\"Yes\"><option value=\"No\">n</option>",
                        "Yes",
                        "<options value=\"Yes\"><option value=\"No\">n</option>"),
                Arguments.of("Z10 the markup ends right after the tag name",
                        "<option",
                        "Yes",
                        "<option"),
                Arguments.of("Z11 nothing follows the last option",
                        "<option value=\"No\">",
                        "Yes",
                        "<option value=\"No\">"),
                Arguments.of("Z12 self-closing right after the tag name",
                        "<option/>Yes",
                        "Yes",
                        "<option selected/>Yes"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("options")
    void shouldChooseTheOptionABrowserSubmitsAsTheOutput_orNone(String name, String select, String wanted,
            String expected) {
        assertThat(choose(select, wanted)).isEqualTo(expected);
    }

    @Test
    void shouldTreatQuotesAndRegexCharactersAsPlainText_whenTheyAreInTheValue() {
        String select = "<option value=\"a(b)[c]$1\\x\">x</option><option value='q\"r'>y</option>"
                + "<option value=\"1.5\">p</option><option value=\"105\">q</option>";

        assertThat(choose(select, "a(b)[c]$1\\x")).contains("<option selected value=\"a(b)[c]$1\\x\"");
        assertThat(choose(select, "q\"r")).contains("<option selected value='q\"r'");
        // "." is no wildcard: 1.5 does not choose 105.
        assertThat(choose(select, "1.5")).contains("<option selected value=\"1.5\"")
                .doesNotContain("<option selected value=\"105\"");
        assertThat(choose(select, "1x5")).isEqualTo(select);
    }

    @Test
    void shouldLeaveTheSelectUntouched_whenTheOutputIsNullOrNoOptionMatches() {
        String select = "<option value=\"A\">x</option><option value=\"B\">y</option><option>Z1</option>";

        assertThat(choose(select, "Z")).isEqualTo(select);
        assertThat(choose(select, null)).isEqualTo(select);
    }

    @Test
    void shouldOnlyLookInsideTheGivenRange_whenAnotherSelectFollows() {
        String first = "<option value=\"Yes\">a</option>";
        String both = first + "</select><select><option value=\"No\">b</option>";

        assertThat(EForm.selectedOptionInsertAt(both, 0, first.length(), "No")).isEqualTo(-1);
    }

    @Test
    void shouldPrefillTheRenderedSelect_throughTheDatabaseTagRun() throws Exception {
        assertThat(renderedSelection("Yes", "<option value=\"Yes, with help\">a</option><option value=\"Yes\">b</option>"))
                .isEqualTo("Yes");
        assertThat(renderedSelection("Smith & Sons", "<option value=\"Other\">o</option>"
                + "<option value=\"Smith &amp; Sons\">s</option>")).isEqualTo("Smith & Sons");
        assertThat(renderedSelection("Smith & Sons", "<option value=\"Other\">o</option>")).isNull();
    }

    /** The select markup after {@link EForm#selectedOptionEdit} is applied, or unchanged when no option matches. */
    private static String chooseOnly(String select, String wanted, boolean single) {
        EForm.SelectEdit edit = EForm.selectedOptionEdit(select, 0, select.length(), wanted, single);
        if (edit == null) return select;
        StringBuilder html = new StringBuilder(select);
        edit.applyTo(html);
        return html.toString();
    }

    static Stream<Arguments> singleSelects() {
        return Stream.of(
                Arguments.of("default after the match",
                        "<option value=\"Yes\">a</option><option value=\"No\" selected>b</option>",
                        "Yes",
                        "<option selected value=\"Yes\">a</option><option value=\"No\">b</option>"),
                Arguments.of("default before the match",
                        "<option value=\"No\" selected>b</option><option value=\"Yes\">a</option>",
                        "Yes",
                        "<option value=\"No\">b</option><option selected value=\"Yes\">a</option>"),
                Arguments.of("defaults before and after the match",
                        "<option value=\"A\" selected>a</option><option value=\"Yes\">b</option>"
                                + "<option value=\"C\" selected>c</option>",
                        "Yes",
                        "<option value=\"A\">a</option><option selected value=\"Yes\">b</option>"
                                + "<option value=\"C\">c</option>"),
                Arguments.of("two defaults before the match",
                        "<option value=\"A\" selected>a</option><option value=\"B\" selected>b</option>"
                                + "<option value=\"Yes\">y</option>",
                        "Yes",
                        "<option value=\"A\">a</option><option value=\"B\">b</option><option selected value=\"Yes\">y</option>"),
                Arguments.of("selected written as selected=selected, SELECTED, selected=\"\" and with spaces around =",
                        "<option value=\"Yes\">y</option><option value=\"No\" selected=\"selected\">n</option>"
                                + "<option SELECTED value=\"C\">c</option><option value=\"D\" selected=\"\">d</option>"
                                + "<option value=\"E\" selected = 'x'>e</option><option value=\"F\" selected=x>f</option>",
                        "Yes",
                        "<option selected value=\"Yes\">y</option><option value=\"No\">n</option>"
                                + "<option value=\"C\">c</option><option value=\"D\">d</option>"
                                + "<option value=\"E\">e</option><option value=\"F\">f</option>"),
                Arguments.of("the matching option already selected, and another",
                        "<option value=\"No\" selected>n</option><option value=\"Yes\" selected>y</option>",
                        "Yes",
                        "<option value=\"No\">n</option><option selected value=\"Yes\" selected>y</option>"),
                Arguments.of("a second option with the same value is not the match",
                        "<option value=\"Yes\">a</option><option value=\"yes\" selected>b</option>",
                        "Yes",
                        "<option selected value=\"Yes\">a</option><option value=\"yes\">b</option>"),
                Arguments.of("only the attribute goes: text, values and other attributes stay",
                        "<option value=\"No\" title=\"selected\" selected>selected</option><option value=\"Yes\">y</option>",
                        "Yes",
                        "<option value=\"No\" title=\"selected\">selected</option><option selected value=\"Yes\">y</option>"),
                Arguments.of("an option group's own attribute stays",
                        "<optgroup label=\"g\" selected><option value=\"No\" selected>n</option></optgroup>"
                                + "<option value=\"Yes\">y</option>",
                        "Yes",
                        "<optgroup label=\"g\" selected><option value=\"No\">n</option></optgroup>"
                                + "<option selected value=\"Yes\">y</option>"),
                Arguments.of("an option in a comment is not touched",
                        "<!-- <option value=\"No\" selected> --><option value=\"Yes\">y</option>"
                                + "<option value=\"C\" selected>c</option>",
                        "Yes",
                        "<!-- <option value=\"No\" selected> --><option selected value=\"Yes\">y</option>"
                                + "<option value=\"C\">c</option>"),
                Arguments.of("no option matches: the template's default stays",
                        "<option value=\"No\" selected>n</option><option value=\"Yes\">y</option>",
                        "Maybe",
                        "<option value=\"No\" selected>n</option><option value=\"Yes\">y</option>"),
                Arguments.of("an empty output marks the first valued option and takes nothing out",
                        "<option value=\"\">p</option><option value=\"A\" selected>a</option>",
                        "",
                        "<option selected value=\"\">p</option><option value=\"A\" selected>a</option>"),
                Arguments.of("markup that cannot be read before the match changes nothing",
                        "<option value=\"No\" selected>n</option><!-- never closed <option value=\"Yes\">y</option>",
                        "Yes",
                        "<option value=\"No\" selected>n</option><!-- never closed <option value=\"Yes\">y</option>"),
                Arguments.of("an unreadable tag after the match keeps the insert and removes nothing",
                        "<option value=\"Yes\">y</option><option value=\"No\" selected>n</option><option value=\"C\" selected",
                        "Yes",
                        "<option selected value=\"Yes\">y</option><option value=\"No\" selected>n</option>"
                                + "<option value=\"C\" selected"),
                Arguments.of("an unclosed comment after the match keeps the insert and removes nothing",
                        "<option value=\"Yes\">y</option><option value=\"No\" selected>n</option><!-- never closed",
                        "Yes",
                        "<option selected value=\"Yes\">y</option><option value=\"No\" selected>n</option><!-- never closed"),
                Arguments.of("an unclosed quote after the match keeps the insert and removes nothing",
                        "<option value=\"Yes\">y</option><option value=\"No\" selected>n</option><option value=\"C>c</option>",
                        "Yes",
                        "<option selected value=\"Yes\">y</option><option value=\"No\" selected>n</option>"
                                + "<option value=\"C>c</option>"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("singleSelects")
    void shouldLeaveOnlyTheMatchingOptionSelected_inASingleSelect(String name, String select, String wanted,
            String expected) {
        assertThat(chooseOnly(select, wanted, true)).isEqualTo(expected);
    }

    @Test
    void shouldLeaveEverySelectedAttributeAsItWas_inASelectWithMultiple() {
        String select = "<option value=\"Yes\">a</option><option value=\"No\" selected>b</option>";

        assertThat(chooseOnly(select, "Yes", false))
                .isEqualTo("<option selected value=\"Yes\">a</option><option value=\"No\" selected>b</option>");
    }

    @Test
    void shouldMarkADisabledMatchingOption_asItAlwaysHas() {
        // Today's behaviour, stated and not changed: a browser does not submit a disabled selected option.
        String select = "<option value=\"A\" disabled>x</option><option value=\"B\" selected>y</option>";

        assertThat(chooseOnly(select, "A", true))
                .isEqualTo("<option selected value=\"A\" disabled>x</option><option value=\"B\">y</option>");
        assertThat(choose("<option value=\"A\" disabled>x</option>", "A"))
                .isEqualTo("<option selected value=\"A\" disabled>x</option>");
    }

    @Test
    void shouldOnlyTakeSelectedOutOfTheGivenRange_whenAnotherSelectFollows() {
        String first = "<option value=\"Yes\">a</option><option value=\"No\" selected>b</option>";
        String both = first + "</select><select><option value=\"C\" selected>c</option>";
        EForm.SelectEdit edit = EForm.selectedOptionEdit(both, 0, first.length(), "Yes", true);
        StringBuilder html = new StringBuilder(both);
        edit.applyTo(html);

        assertThat(html.toString()).isEqualTo("<option selected value=\"Yes\">a</option><option value=\"No\">b</option>"
                + "</select><select><option value=\"C\" selected>c</option>");
    }

    @Test
    void shouldReadWhetherASelectIsSingle_orSayNoOnAnyDoubt() {
        String single = "<select name=\"a\" oscarDB=x class=\"c\"><option value=\"Yes\">y</option>";
        String multiple = "<select oscarDB=x MULTIPLE><option value=\"Yes\">y</option>";
        int pointer = single.indexOf("oscarDB=x") + 9;

        assertThat(EForm.isSingleSelect(single, pointer, single.length())).isTrue();
        assertThat(EForm.isSingleSelect(multiple, multiple.indexOf("oscarDB=x") + 9, multiple.length())).isFalse();
        // No tag before the pointer, a tag that is not a select, and a start tag that cannot be read.
        assertThat(EForm.isSingleSelect("oscarDB=x>", 9, 10)).isFalse();
        assertThat(EForm.isSingleSelect("<div oscarDB=x><option value=\"Yes\">", 14, 35)).isFalse();
        assertThat(EForm.isSingleSelect("<select oscarDB=x title=\"never closed><option>", 15, 47)).isFalse();
        assertThat(EForm.isSingleSelect("<select oscarDB=x", 17, 400)).isFalse();
    }

    @Test
    void shouldShowTheStoredValue_whenTheTemplateDefaultComesAfterIt() throws Exception {
        String options = "<option value=\"Yes\">a</option><option value=\"No\" selected>b</option>";

        assertThat(renderedSelectedValues("", "Yes", options)).containsExactly("Yes");
        assertThat(renderedSelectedValues(" class=\"c\"", "Yes", options)).containsExactly("Yes");
        // A select with multiple keeps the template's default beside the stored value.
        assertThat(renderedSelectedValues(" multiple", "Yes", options)).containsExactly("Yes", "No");
        assertThat(renderedSelectedValues(" MULTIPLE=\"multiple\"", "Yes", options)).containsExactly("Yes", "No");
        // No match: the template's default stays.
        assertThat(renderedSelectedValues("", "Maybe", options)).containsExactly("No");
    }

    /** The values of every selected option after the database tag run. */
    private static java.util.List<String> renderedSelectedValues(String selectAttributes, String output, String options)
            throws Exception {
        DatabaseAP ap = new DatabaseAP("choice", "", output);
        try (var loader = mockStatic(EFormLoader.class)) {
            loader.when(EFormLoader::getMarker).thenReturn("oscarDB");
            loader.when(() -> EFormLoader.getAP("choice")).thenReturn(ap);
            EForm form = new EForm();
            form.setDemographicNo("7");
            form.setFormHtml("<form><select name=\"Choice\" oscarDB=choice" + selectAttributes + ">" + options
                    + "</select></form>");
            form.setDatabaseAPs();

            return Jsoup.parse(form.getFormHtml()).select("option[selected]").eachAttr("value");
        }
    }

    /** The value of the selected option after the database tag run, or null when none is selected. */
    private static String renderedSelection(String output, String options) throws Exception {
        DatabaseAP ap = new DatabaseAP("choice", "", output);
        try (var loader = mockStatic(EFormLoader.class)) {
            loader.when(EFormLoader::getMarker).thenReturn("oscarDB");
            loader.when(() -> EFormLoader.getAP("choice")).thenReturn(ap);
            EForm form = new EForm();
            form.setDemographicNo("7");
            form.setFormHtml("<form><select name=\"Choice\" oscarDB=choice>" + options + "</select></form>");
            form.setDatabaseAPs();

            Document page = Jsoup.parse(form.getFormHtml());
            assertThat(page.select("option[selected]").size()).isLessThanOrEqualTo(1);
            return page.selectFirst("option[selected]") == null ? null : page.selectFirst("option[selected]").val();
        }
    }
}
