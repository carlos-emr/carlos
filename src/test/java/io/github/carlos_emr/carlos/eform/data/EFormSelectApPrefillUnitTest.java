/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.eform.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.List;
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

    private static final String OPEN = "<select name=\"f\" oscarDB=x";

    /** The edit for the select written as {@code OPEN + attributes + ">" + options + "</select>"}, applied, or null. */
    private static String editedSelect(String attributes, String options, String wanted) {
        String html = OPEN + attributes + ">" + options + "</select>";
        EFormSelectMarkup.SelectEdit edit = EFormSelectMarkup.edit(html, html.indexOf("oscarDB=x") + 9, wanted);
        if (edit == null) return null;
        StringBuilder out = new StringBuilder(html);
        edit.applyTo(out);
        return out.toString();
    }

    /** The options with {@code " selected"} put in by the parser-based edit (a single select), or unchanged. */
    private static String choose(String options, String wanted) {
        return chooseOnly(options, wanted, false);
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
                // The select's own end tag ends a tag left open, as a browser reads it: the option exists.
                Arguments.of("W unterminated tag closed by the select's end tag",
                        "<option value=\"Yes\"",
                        "Yes",
                        "<option selected value=\"Yes\""),
                Arguments.of("X stray equals sign before the attributes",
                        "<option =x value=\"Yes\">y</option>",
                        "Yes",
                        "<option selected =x value=\"Yes\">y</option>"),
                // A style inside a select is raw text to current browsers and markup to jsoup: not told apart,
                // so the select is left as the template has it rather than guessed at.
                Arguments.of("Y a style inside the select leaves it alone",
                        "<style>.a{content:\"<option value=Yes>\"}</style><option value=\"Yes\">y</option>",
                        "Yes",
                        "<style>.a{content:\"<option value=Yes>\"}</style><option value=\"Yes\">y</option>"),
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
                        "<option selected/>Yes"),
                // A browser keeps the first of repeated attributes: the bare value submits "", so the raw spelling
                // of the second one is no match.
                Arguments.of("Z13 a repeated value attribute: the first, bare one is the value",
                        "<option value value=\"Smith &amp; Sons\">a</option>",
                        "Smith &amp; Sons",
                        "<option value value=\"Smith &amp; Sons\">a</option>"),
                // Text that only LOOKS like a style tag is no style element (#6284 review): the edit is made.
                Arguments.of("Z14 a comment that mentions a style tag does not stop the edit",
                        "<!-- a <style> note --><option value=\"Yes\">y</option>",
                        "Yes",
                        "<!-- a <style> note --><option selected value=\"Yes\">y</option>"),
                Arguments.of("Z15 an option group label that holds style text does not stop the edit",
                        "<optgroup label=\"<style>\"><option value=\"Yes\">y</option></optgroup>",
                        "Yes",
                        "<optgroup label=\"<style>\"><option selected value=\"Yes\">y</option></optgroup>"),
                Arguments.of("Z16 a script that mentions a style tag does not stop the edit",
                        "<script>var s='<style>';</script><option value=\"Yes\">y</option>",
                        "Yes",
                        "<script>var s='<style>';</script><option selected value=\"Yes\">y</option>"),
                // A no-break space is part of the text, as a browser submits it: it is neither stripped nor the
                // same as a plain space, so an exact submitted value wins over it.
                Arguments.of("Z17 a no-break space option does not win over the exact text",
                        "<option>&nbsp;Yes</option><option>Yes</option>",
                        "Yes",
                        "<option>&nbsp;Yes</option><option selected>Yes</option>"),
                Arguments.of("Z18 the no-break space text is chosen when it is the wanted text",
                        "<option>&nbsp;Yes</option><option>Yes</option>",
                        " Yes",
                        "<option selected>&nbsp;Yes</option><option>Yes</option>"));
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
    void shouldPrefillTheRenderedSelect_throughTheDatabaseTagRun() throws Exception {
        assertThat(renderedSelection("Yes", "<option value=\"Yes, with help\">a</option><option value=\"Yes\">b</option>"))
                .isEqualTo("Yes");
        assertThat(renderedSelection("Smith & Sons", "<option value=\"Other\">o</option>"
                + "<option value=\"Smith &amp; Sons\">s</option>")).isEqualTo("Smith & Sons");
        assertThat(renderedSelection("Smith & Sons", "<option value=\"Other\">o</option>")).isNull();
    }

    /** The options after the edit for a single select, or for one with {@code multiple} (the template's other
     * defaults stay), or unchanged when no option matches. */
    private static String chooseOnly(String options, String wanted, boolean single) {
        String edited = editedSelect(single ? "" : " multiple", options, wanted);
        if (edited == null) return options;
        String head = OPEN + (single ? "" : " multiple") + ">";
        return edited.substring(head.length(), edited.length() - "</select>".length());
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
                Arguments.of("a tag that never closes its selected attribute is not selected, and is left as it is",
                        "<option value=\"Yes\">y</option><option value=\"No\" selected>n</option><option value=\"C\" selected",
                        "Yes",
                        "<option selected value=\"Yes\">y</option><option value=\"No\">n</option>"
                                + "<option value=\"C\" selected"),
                Arguments.of("an unclosed comment after the options leaves the options before it as they are read",
                        "<option value=\"Yes\">y</option><option value=\"No\" selected>n</option><!-- never closed",
                        "Yes",
                        "<option selected value=\"Yes\">y</option><option value=\"No\">n</option><!-- never closed"),
                Arguments.of("an unclosed quote makes no option, so only the real options are touched",
                        "<option value=\"Yes\">y</option><option value=\"No\" selected>n</option><option value=\"C>c</option>",
                        "Yes",
                        "<option selected value=\"Yes\">y</option><option value=\"No\">n</option>"
                                + "<option value=\"C>c</option>"),
                // Only HTML white space separates attributes: an em space starts the name "\u2003selected", and an
                // "=" before a name belongs to it ("=selected"); neither is the selected attribute, so both stay.
                Arguments.of("an em space before selected is part of another attribute's name, which stays",
                        "<option value=\"Yes\">y</option><option value=\"No\"\u2003selected>n</option>",
                        "Yes",
                        "<option selected value=\"Yes\">y</option><option value=\"No\"\u2003selected>n</option>"),
                Arguments.of("an attribute named =selected is not the selected attribute, and stays",
                        "<option value=\"Yes\">y</option><option value=\"No\" =selected>n</option>",
                        "Yes",
                        "<option selected value=\"Yes\">y</option><option value=\"No\" =selected>n</option>"),
                Arguments.of("selected right after a quoted value, and before a slash, comes out too",
                        "<option value=\"Yes\">y</option><option value=\"No\"selected>n</option>"
                                + "<option value=\"C\" selected/>c</option>",
                        "Yes",
                        "<option selected value=\"Yes\">y</option><option value=\"No\">n</option>"
                                + "<option value=\"C\"/>c</option>"),
                // With no white space after selected's quoted value, the white space before it is what keeps the
                // preceding unquoted value or bare attribute apart from the next attribute: it stays.
                Arguments.of("a selected attribute right before the next attribute leaves the white space that parts them",
                        "<option value=\"Yes\">y</option><option value=No selected=\"\"title=\"t\">n</option>"
                                + "<option disabled selected=''value=\"C\">c</option>",
                        "Yes",
                        "<option selected value=\"Yes\">y</option><option value=No title=\"t\">n</option>"
                                + "<option disabled value=\"C\">c</option>"));
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

    // ---- Review of the select prefill: each case is the markup and the browser behaviour that was reported ----

    /** The whole form's markup after the database tag run, as a browser would load it. */
    private static Document rendered(String formBody, String output) throws Exception {
        DatabaseAP ap = new DatabaseAP("choice", "", output);
        try (var loader = mockStatic(EFormLoader.class)) {
            loader.when(EFormLoader::getMarker).thenReturn("oscarDB");
            loader.when(() -> EFormLoader.getAP("choice")).thenReturn(ap);
            EForm form = new EForm();
            form.setDemographicNo("7");
            form.setFormHtml("<form>" + formBody + "</form>");
            form.setDatabaseAPs();
            return Jsoup.parse(form.getFormHtml());
        }
    }

    /** The value a dependent database tag would read for {@code {name}}: what the form recorded for the field. */
    private static String fieldValue(EForm form, String name) throws Exception {
        java.lang.reflect.Method read = EForm.class.getDeclaredMethod("findValueInForm", String.class);
        read.setAccessible(true);
        return (String) read.invoke(form, "{" + name + "}");
    }

    /** What a browser submits for the select named {@code name}: its first (single) selected option's value. */
    private static String submitted(Document page, String name) {
        org.jsoup.nodes.Element select = page.selectFirst("select[name=" + name + "]");
        org.jsoup.nodes.Element shown = null;
        for (org.jsoup.nodes.Element option : select.select("option[selected]")) shown = option;
        return shown == null ? null : EFormSelectMarkup.submittedValue(shown);
    }

    @Test
    void shouldLimitTheEditToTheOwningSelect_whenItsEndTagHasASpaceOrACommentHoldsAnEndTag() throws Exception {
        // `</select >` is a valid end tag; the second select's authored default must not be touched.
        String first = "<select name=\"a\" oscarDB=choice><option value=\"Yes\">y</option><option value=\"No\">n</option></select >";
        String second = "<select name=\"m\"><option value=\"Jan\">a</option><option value=\"Jun\" selected>b</option></select>";

        Document page = rendered(first + second, "Yes");

        assertThat(submitted(page, "a")).isEqualTo("Yes");
        assertThat(submitted(page, "m")).isEqualTo("Jun");
        // A `</select>` inside a comment does not end the select early.
        String commented = "<select name=\"c\" oscarDB=choice><option value=\"No\">n</option><!-- </select> -->"
                + "<option value=\"Yes\">y</option></select>";
        assertThat(submitted(rendered(commented, "Yes"), "c")).isEqualTo("Yes");
    }

    @Test
    void shouldIgnoreOptionLookingTextInAnOptgroupLabelAndInertTemplateContent() throws Exception {
        String label = "<select name=\"l\" oscarDB=choice><optgroup label=\"<option value=Yes>\">"
                + "<option value=\"No\">n</option></optgroup><option value=\"Yes\">y</option></select>";
        String template = "<select name=\"t\" oscarDB=choice><template><option value=\"Yes\">t</option></template>"
                + "<option value=\"No\">n</option><option value=\"Yes\">y</option></select>";

        assertThat(submitted(rendered(label, "Yes"), "l")).isEqualTo("Yes");
        Document page = rendered(template, "Yes");
        assertThat(submitted(page, "t")).isEqualTo("Yes");
        // The template's own content is intact: its option is still there and was not selected.
        assertThat(page.select("template option[value=Yes]").size()).isEqualTo(1);
        assertThat(page.select("template option[selected]").size()).isZero();
    }

    @Test
    void shouldConsumeTheWholeScriptElement_beforeLookingForOptions() throws Exception {
        String inert = "<select name=\"s\" oscarDB=choice><script type=\"application/json\">"
                + "{\"a\":\"</scripture>\",\"b\":\"<option value=Yes>\"}</script>"
                + "<option value=\"No\">n</option><option value=\"Yes\">y</option></select>";

        Document page = rendered(inert, "Yes");

        assertThat(submitted(page, "s")).isEqualTo("Yes");
        // The script text is as authored.
        assertThat(page.html()).contains("\"b\":\"<option value=Yes>\"");
    }

    @Test
    void shouldCompareTheCompleteSubmittedText_whenAnOptionHasACommentInsideItsText() throws Exception {
        String options = "<option>Yes<!-- annotation --> now</option><option value=\"Yes\">real</option>";

        // The first option submits "Yes now", not "Yes": the option whose value is Yes is the one chosen.
        assertThat(submitted(rendered("<select name=\"c\" oscarDB=choice>" + options + "</select>", "Yes"), "c"))
                .isEqualTo("Yes");
        // And "Yes now" is found by its whole submitted text.
        assertThat(submitted(rendered("<select name=\"c\" oscarDB=choice>" + options + "</select>", "Yes now"), "c"))
                .isEqualTo("Yes now");
    }

    @Test
    void shouldReadABareValueAttributeAsAnExplicitEmptyString() throws Exception {
        String options = "<option value>Yes</option><option value=\"Yes\">real</option>";

        Document page = rendered("<select name=\"b\" oscarDB=choice>" + options + "</select>", "Yes");

        // `<option value>` submits "", so the later option whose value is Yes is the one selected.
        assertThat(submitted(page, "b")).isEqualTo("Yes");
        assertThat(page.select("option[selected]").size()).isEqualTo(1);
        assertThat(page.selectFirst("option[selected]").text()).isEqualTo("real");
    }

    @Test
    void shouldPreferAnExactSubmittedValue_beforeAnyCompatibilityMatch() throws Exception {
        String ampersand = "<option value=\"Smith &amp;amp; Sons\">raw</option><option value=\"Smith &amp; Sons\">decoded</option>";
        // The stored text is the literal `Smith &amp; Sons`: the option that submits exactly that wins over the
        // one that only matches by its raw spelling.
        assertThat(rendered("<select name=\"a\" oscarDB=choice>" + ampersand + "</select>", "Smith &amp; Sons")
                .selectFirst("option[selected]").text()).isEqualTo("raw");
        // The other way round: the raw-spelling match comes FIRST and the exact submitted value LATER. A
        // first-match-by-any-rule implementation would pick the first option; the exact one still wins.
        String swapped = "<option value=\"Smith &amp; Sons\">decoded</option><option value=\"Smith &amp;amp; Sons\">raw</option>";
        assertThat(rendered("<select name=\"a\" oscarDB=choice>" + swapped + "</select>", "Smith &amp; Sons")
                .select("option[selected]").stream().map(org.jsoup.nodes.Element::text).toList()).containsExactly("raw");
        // `MG` before `mg`: the stored `mg` code is not changed to `MG`.
        String codes = "<option value=\"MG\">upper</option><option value=\"mg\">lower</option>";
        assertThat(rendered("<select name=\"c\" oscarDB=choice>" + codes + "</select>", "mg")
                .selectFirst("option[selected]").text()).isEqualTo("lower");
        // With no exact match the compatibility fallbacks still apply.
        assertThat(rendered("<select name=\"c\" oscarDB=choice>" + codes.replace("mg", "kg") + "</select>", "MG")
                .selectFirst("option[selected]").text()).isEqualTo("upper");
        assertThat(rendered("<select name=\"a\" oscarDB=choice><option value=\"Smith &amp; Sons\">d</option></select>",
                "Smith & Sons").selectFirst("option[selected]").text()).isEqualTo("d");
    }

    /**
     * A database tag that names {@code field} ({@code var$value="{field}"}), as a count of saved eForm values does:
     * the database tag run reads the form's field values for it.
     */
    private static String dependentTag(String field) {
        return "<input type=\"hidden\" oscarDB=\"e$count#x\" var$value=\"{" + field + "}\">";
    }

    /** The value a database tag that names {@code field} binds after the database tag run over {@code body}. */
    private static String boundValue(String body, String output, String field) throws Exception {
        DatabaseAP ap = new DatabaseAP("choice", "", output);
        try (var loader = mockStatic(EFormLoader.class)) {
            loader.when(EFormLoader::getMarker).thenReturn("oscarDB");
            loader.when(() -> EFormLoader.getAP("choice")).thenReturn(ap);
            EForm form = new EForm();
            form.setDemographicNo("7");
            form.setFormHtml(body + dependentTag(field));
            form.setDatabaseAPs();
            return fieldValue(form, field.toLowerCase(java.util.Locale.ROOT));
        }
    }

    @Test
    void shouldBindTheSubmittedValueInADependentDatabaseTag_notTheMarkupsRawSpelling() throws Exception {
        // The field a dependent lookup reads is the value a browser submits: the decoded text of a text-only
        // option, and the decoded value attribute.
        assertThat(boundValue("<form><select name=\"Pick\" oscarDB=choice><option>No</option><option>Yes</option></select></form>",
                "Yes", "Pick")).isEqualTo("Yes");
        assertThat(boundValue("<form><select name=\"Pick\" oscarDB=choice><option value=\"x\">x</option>"
                + "<option value=\"Smith &amp; Sons\">s</option></select></form>", "Smith & Sons", "Pick"))
                .isEqualTo("Smith & Sons");
    }

    @Test
    void shouldBindWhatChromiumSubmits_forEverySharedRead() throws Exception {
        // scripts/eform-select-prefill-fixture-check.js asserts each "submitted" in Chromium (FormData of the page's
        // first form, the one a save posts): a database tag that names the select binds that same value, and an
        // empty one when the browser submits none.
        com.fasterxml.jackson.databind.JsonNode reads = sharedFixtures().get("reads");
        assertThat(reads).isNotEmpty();
        for (com.fasterxml.jackson.databind.JsonNode read : reads) {
            String expected = read.get("submitted").isNull() ? "" : read.get("submitted").asText();
            assertThat(boundValue(read.get("form").asText(), "", "a")).as(read.get("name").asText()).isEqualTo(expected);
        }
    }

    @Test
    void shouldKeepTheLastNonBlankValueInDocumentOrder_acrossInputsAndSelects() throws Exception {
        String typed = "<input type=\"text\" name=\"Pick\" value=\"Typed\">";
        String chosen = "<select name=\"Pick\"><option>Chosen</option></select>";
        // A later select's submitted value wins over an earlier typed one, and the other way round.
        assertThat(boundValue("<form>" + typed + chosen + "</form>", "", "Pick")).isEqualTo("Chosen");
        assertThat(boundValue("<form>" + chosen + typed + "</form>", "", "Pick")).isEqualTo("Typed");
        // A later field with nothing to submit leaves the earlier value: a blank text box, a list box with none marked.
        assertThat(boundValue("<form>" + chosen + "<input type=\"text\" name=\"Pick\"></form>", "", "Pick"))
                .isEqualTo("Chosen");
        assertThat(boundValue("<form>" + typed + "<select name=\"Pick\" size=\"3\"><option>x</option></select></form>",
                "", "Pick")).isEqualTo("Typed");
        // Inputs alone are read without the HTML parser's select reading.
        assertThat(boundValue("<form>" + typed + "</form>", "", "Pick")).isEqualTo("Typed");
    }

    @Test
    void shouldReadEverySelectOfAPageWithoutAForm_asBefore() throws Exception {
        // With no form there is no save form to tell controls apart by: a select is read as before, unless it is
        // inert template content.
        assertThat(boundValue("<select name=\"Pick\"><option>Yes</option></select>", "", "Pick")).isEqualTo("Yes");
        assertThat(boundValue("<select name=\"Pick\"><option>Yes</option></select>"
                + "<template><select name=\"Pick\"><option selected>Archived</option></select></template>", "", "Pick"))
                .isEqualTo("Yes");
    }

    @Test
    void shouldReadOptionTextAsABrowserSubmitsIt_withOnlyHtmlWhiteSpaceCollapsed() {
        // Tab, line feed, form feed, carriage return and space are stripped and collapsed; a no-break space and an
        // em space are text. A comment's text is left out (the text on both sides joins), and so is the text of a
        // script and of template content.
        Document page = Jsoup.parse("<select><option>\t A\u2003 \r\n B&nbsp; \f<!-- c --><script>var s = 1;</script>"
                + "<template>t</template>C </option><option>&nbsp;Yes </option><option>  </option></select>");
        List<org.jsoup.nodes.Element> options = page.select("option");

        assertThat(EFormSelectMarkup.optionText(options.get(0))).isEqualTo("A\u2003 B\u00a0 C");
        assertThat(EFormSelectMarkup.optionText(options.get(1))).isEqualTo("\u00a0Yes");
        assertThat(EFormSelectMarkup.optionText(options.get(2))).isEmpty();
    }

    @Test
    void shouldNotReadAnySelectValue_whenNoDatabaseTagNamesAField() throws Exception {
        // The values are only for a database tag that names another field: without one, no select is parsed for its
        // value, however many there are (a large form such as the bundled Rourke Baby Record has none).
        StringBuilder body = new StringBuilder("<form><input type=\"hidden\" name=\"filled\" oscarDB=choice>");
        for (int i = 0; i < 40; i++) {
            body.append("<select name=\"s").append(i).append("\"><option>a</option><option selected>b</option></select>");
        }
        body.append("</form>");
        DatabaseAP ap = new DatabaseAP("choice", "", "Yes");
        try (var loader = mockStatic(EFormLoader.class);
                var markup = mockStatic(EFormSelectMarkup.class, CALLS_REAL_METHODS)) {
            loader.when(EFormLoader::getMarker).thenReturn("oscarDB");
            loader.when(() -> EFormLoader.getAP("choice")).thenReturn(ap);
            EForm form = new EForm();
            form.setDemographicNo("7");
            form.setFormHtml(body.toString());

            form.setDatabaseAPs();

            markup.verify(() -> EFormSelectMarkup.submittedValues(any(), any()), never());
            assertThat(form.getFormHtml()).contains("name=\"filled\" oscarDB=choice value=\"Yes\"");
        }
    }

    @Test
    void shouldReadEverySelectFromOneParse_whenADatabaseTagNamesAField() throws Exception {
        StringBuilder body = new StringBuilder("<form>");
        for (int i = 0; i < 40; i++) {
            body.append("<select name=\"s").append(i).append("\"><option>a</option><option selected>b").append(i)
                    .append("</option></select>");
        }
        body.append("</form>").append(dependentTag("s39"));
        try (var loader = mockStatic(EFormLoader.class);
                var markup = mockStatic(EFormSelectMarkup.class, CALLS_REAL_METHODS)) {
            loader.when(EFormLoader::getMarker).thenReturn("oscarDB");
            EForm form = new EForm();
            form.setDemographicNo("7");
            form.setFormHtml(body.toString());

            form.setDatabaseAPs();

            // One call for all forty selects, and that call parses the form once.
            markup.verify(() -> EFormSelectMarkup.submittedValues(any(), argThat(starts -> starts.size() == 40)), times(1));
            assertThat(fieldValue(form, "s39")).isEqualTo("b39");
            assertThat(fieldValue(form, "s0")).isEqualTo("b0");
        }
    }

    /** A form of {@code count} database-tagged inputs and selects, with text between them that moves every offset. */
    private static String manyTaggedSelects(int count) {
        StringBuilder body = new StringBuilder("<form>");
        for (int i = 0; i < count; i++) {
            body.append("<p>Café 😀 question ").append(i).append(" &amp; more text</p>\r\n");
            body.append("<input type=\"text\" name=\"t").append(i).append("\" oscarDB=choice>");
            body.append("<select name=\"s").append(i).append("\" oscarDB=choice><option value=\"\">--</option>")
                    .append("<option value=\"No\" selected>No</option><option value=\"Yes\">Yes</option>")
                    .append("<option value=\"Maybe\">Maybe</option></select>\r\n");
        }
        return body.append("</form>").toString();
    }

    @ParameterizedTest(name = "{0} database-tagged selects")
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 5, 150, 400})
    void shouldParseTheFormOnce_howeverManyDatabaseTaggedSelectsItHas(int count) throws Exception {
        // The work is counted, not timed: one parse for the whole pass, never one for each select (a 400-select
        // form took four seconds when every select parsed the whole form).
        DatabaseAP ap = new DatabaseAP("choice", "", "Yes");
        try (var loader = mockStatic(EFormLoader.class)) {
            loader.when(EFormLoader::getMarker).thenReturn("oscarDB");
            loader.when(() -> EFormLoader.getAP("choice")).thenReturn(ap);
            EForm form = new EForm();
            form.setDemographicNo("7");
            form.setFormHtml(manyTaggedSelects(count));
            int before = EFormSelectMarkup.parseCount();

            form.setDatabaseAPs();

            assertThat(EFormSelectMarkup.parseCount() - before).isLessThanOrEqualTo(1);
            Document page = Jsoup.parse(form.getFormHtml());
            // Every select shows Yes and only Yes (its template default came out), and every input got its value:
            // the edits landed in the right place although each one moved the text after it.
            assertThat(page.select("select").size()).isEqualTo(count);
            assertThat(page.select("select option[selected]").size()).isEqualTo(count);
            assertThat(page.select("select option[selected][value=Yes]").size()).isEqualTo(count);
            assertThat(page.select("input[name^=t][value=Yes]").size()).isEqualTo(count);
            assertThat(page.select("option[value=No][selected]")).isEmpty();
        }
    }

    @Test
    void shouldParseTheFormTwiceAtMost_whenADatabaseTagNamesAFieldAndManySelectsAreTagged() throws Exception {
        // One parse to fill the selects, one to read what they submit for the dependent tag: not one per select.
        DatabaseAP ap = new DatabaseAP("choice", "", "Yes");
        try (var loader = mockStatic(EFormLoader.class)) {
            loader.when(EFormLoader::getMarker).thenReturn("oscarDB");
            loader.when(() -> EFormLoader.getAP("choice")).thenReturn(ap);
            EForm form = new EForm();
            form.setDemographicNo("7");
            form.setFormHtml(manyTaggedSelects(400) + dependentTag("s399"));
            int before = EFormSelectMarkup.parseCount();

            form.setDatabaseAPs();

            assertThat(EFormSelectMarkup.parseCount() - before).isLessThanOrEqualTo(2);
            assertThat(fieldValue(form, "s399")).isEqualTo("Yes");
            assertThat(fieldValue(form, "s0")).isEqualTo("Yes");
        }
    }

    @Test
    void shouldSelectTheRightOption_whenTextBeforeAndBetweenTheSelectsShiftsOffsets() throws Exception {
        // Positions are read from the parser and used on the template's own text: non-ASCII letters, an emoji
        // (two UTF-16 units), CRLF line ends, and entities, before and between the selects, must not move them.
        String drift = "<p>Caf\u00e9 \u00fcber \u6f22\u5b57 \uD83D\uDE00 &amp; &lt;b&gt; &eacute;</p>\r\n\r\n";
        String body = drift
                + "<select name=\"first\" oscarDB=choice><option value=\"A\">a</option><option value=\"B\">b</option></select>\r\n"
                + drift + "<p>\uD83D\uDE00\uD83D\uDE00 &copy; &#128512;</p>\r\n"
                + "<select name=\"second\" oscarDB=choice><option value=\"B\">b</option><option value=\"A\" selected>a</option></select>\r\n"
                + drift;

        Document page = rendered(body, "B");

        assertThat(submitted(page, "first")).isEqualTo("B");
        assertThat(submitted(page, "second")).isEqualTo("B");
        // Only the intended options changed: one selected option in each select.
        assertThat(page.select("select[name=first] option[selected]").size()).isEqualTo(1);
        assertThat(page.select("select[name=second] option[selected]").size()).isEqualTo(1);
        // The text around them is byte for byte as the template had it.
        assertThat(page.body().html().replace("\n", "")).contains("Caf\u00e9 \u00fcber \u6f22\u5b57 \uD83D\uDE00");
    }

    @Test
    void shouldLeaveTheTemplateAsItIs_whenItsMarkupIsPathological() throws Exception {
        // Very deep nesting, an unterminated select and a select inside a tag that never closes: the form
        // still opens, the select keeps the template's own default, and nothing throws.
        String deep = "<div>".repeat(5_000);
        String body = deep + "<select name=\"deep\" oscarDB=choice><option value=\"A\">a</option>"
                + "<option value=\"B\" selected>b</option></select>";
        Document page = rendered(body, "A");
        assertThat(page.select("select[name=deep]").size()).isEqualTo(1);

        String unterminated = "<select name=\"open\" oscarDB=choice><option value=\"A\">a<option value=\"B\" selected>b";
        assertThat(rendered(unterminated, "A").select("select[name=open]").size()).isEqualTo(1);

        String brokenTag = "<select name=\"cut\" oscarDB=choice><option value=\"A\" title=\"unclosed>a</option>";
        assertThat(rendered(brokenTag, "A").select("select").size()).isEqualTo(1);
    }

    @Test
    void shouldFillManySelectsInALargeForm_withinAGenerousTime() throws Exception {
        // About 300 database-filled selects in about 300 KB: each is parsed once per fill, so the whole form
        // must still come up in seconds, not minutes.
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            body.append("<select name=\"s").append(i).append("\" oscarDB=choice>");
            for (int o = 0; o < 10; o++) {
                body.append("<option value=\"").append("Option number ").append(o).append("\">Option label ")
                        .append(o).append(" with some padding text to give it weight</option>");
            }
            body.append("</select>\n");
        }
        assertThat(body.length()).isBetween(250_000, 400_000);

        java.time.Duration[] took = new java.time.Duration[1];
        Document page = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(60), () -> {
            long start = System.nanoTime();
            Document result = rendered(body.toString(), "Option number 7");
            took[0] = java.time.Duration.ofNanos(System.nanoTime() - start);
            return result;
        });

        assertThat(page.select("select option[selected]").size()).isEqualTo(300);
        assertThat(took[0]).isLessThan(java.time.Duration.ofSeconds(60));
    }

    /** scripts/fixtures/eform/select-prefill-cases.json, which scripts/eform-select-prefill-fixture-check.js also reads. */
    private static com.fasterxml.jackson.databind.JsonNode sharedFixtures() throws Exception {
        java.nio.file.Path file = java.nio.file.Path.of("scripts", "fixtures", "eform", "select-prefill-cases.json");
        java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty("maven.multiModuleProjectDirectory",
                System.getProperty("user.dir"))).toAbsolutePath();
        while (dir != null && !java.nio.file.Files.exists(dir.resolve(file))) {
            dir = dir.getParent();
        }
        assertThat(dir).as("the shared fixture file").isNotNull();
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(java.nio.file.Files.readString(dir.resolve(file), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void shouldProduceExactlyTheSharedFixtureMarkup_thatChromiumReadsAsTheSubmittedValue() throws Exception {
        // The same file drives scripts/eform-select-prefill-fixture-check.js, which loads each "after" markup into
        // Chromium and asserts the value the browser submits: the server's parser and the browser agree.
        com.fasterxml.jackson.databind.JsonNode cases = sharedFixtures().get("cases");
        assertThat(cases).isNotEmpty();
        for (com.fasterxml.jackson.databind.JsonNode testCase : cases) {
            DatabaseAP ap = new DatabaseAP("choice", "", testCase.get("wanted").asText());
            try (var loader = mockStatic(EFormLoader.class)) {
                loader.when(EFormLoader::getMarker).thenReturn("oscarDB");
                loader.when(() -> EFormLoader.getAP("choice")).thenReturn(ap);
                EForm form = new EForm();
                form.setDemographicNo("7");
                form.setFormHtml("<form id=\"f\">" + testCase.get("before").asText() + "</form>");
                form.setDatabaseAPs();
                assertThat(form.getFormHtml()).as(testCase.get("name").asText())
                        .isEqualTo("<form id=\"f\">" + testCase.get("after").asText() + "</form>");
            }
        }
    }

    private static final String TEMPLATE_SELECT = "<select name=\"Pick\" oscarDB=choice>"
            + "<option value=\"No\" selected>No</option><option value=\"Yes\">Yes</option></select>";

    @Test
    void shouldLeaveTheSelectAsTheTemplateHasIt_whenTheParserFailsWhileChoosingTheOption() throws Exception {
        DatabaseAP ap = new DatabaseAP("choice", "", "Yes");
        try (var loader = mockStatic(EFormLoader.class);
                var markup = mockStatic(EFormSelectMarkup.class, CALLS_REAL_METHODS)) {
            loader.when(EFormLoader::getMarker).thenReturn("oscarDB");
            loader.when(() -> EFormLoader.getAP("choice")).thenReturn(ap);
            markup.when(() -> EFormSelectMarkup.edit(any(EFormSelectMarkup.ParsedForm.class), anyInt(), any()))
                    .thenThrow(new IllegalStateException("the parser failed"));
            EForm form = new EForm();
            form.setDemographicNo("7");
            form.setFormHtml("<form>" + TEMPLATE_SELECT + "</form>" + dependentTag("Pick"));

            form.setDatabaseAPs(); // never throws

            // The template's own default stays, and the dependent value is what the markup shows.
            assertThat(form.getFormHtml()).isEqualTo("<form>" + TEMPLATE_SELECT + "</form>" + dependentTag("Pick"));
            assertThat(fieldValue(form, "pick")).isEqualTo("No");
        }
    }

    @Test
    void shouldGoOnWithoutADependentValue_whenTheParserFailsWhileReadingTheSelectedOption() throws Exception {
        DatabaseAP ap = new DatabaseAP("choice", "", "Yes");
        try (var loader = mockStatic(EFormLoader.class);
                var markup = mockStatic(EFormSelectMarkup.class, CALLS_REAL_METHODS)) {
            loader.when(EFormLoader::getMarker).thenReturn("oscarDB");
            loader.when(() -> EFormLoader.getAP("choice")).thenReturn(ap);
            markup.when(() -> EFormSelectMarkup.submittedValues(any(), any()))
                    .thenThrow(new IllegalStateException("the parser failed"));
            EForm form = new EForm();
            form.setDemographicNo("7");
            form.setFormHtml("<form>" + TEMPLATE_SELECT + "</form>" + dependentTag("Pick"));

            form.setDatabaseAPs(); // never throws

            // The prefill itself worked; only the value a dependent tag would read is missing.
            markup.verify(() -> EFormSelectMarkup.submittedValues(any(), any()), times(1));
            assertThat(Jsoup.parse(form.getFormHtml()).selectFirst("option[selected]").text()).isEqualTo("Yes");
            assertThat(fieldValue(form, "pick")).isEmpty();
        }
    }

    @Test
    void shouldKeepNothingForAnAuthoredTextThatIsMissing_whenTheSubmittedValueIsBlank() throws Exception {
        // The marker guard is null-safe for authored text although its callers (an attribute value and a
        // control's text) never pass null; the guard is driven directly so that its null branch is held.
        java.lang.reflect.Method keeps = EForm.class.getDeclaredMethod("keepsFdidMarker", String.class, String.class);
        keeps.setAccessible(true);
        EForm form = new EForm();
        assertThat((Boolean) keeps.invoke(form, null, "")).isFalse();
        assertThat((Boolean) keeps.invoke(form, "no marker here", "")).isFalse();
        assertThat((Boolean) keeps.invoke(form, "text", "typed")).isFalse();
    }
}
