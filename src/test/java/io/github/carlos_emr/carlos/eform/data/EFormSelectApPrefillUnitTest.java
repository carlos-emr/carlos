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
