/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * Attribute lookup in eForm tags ({@link EFormUtil#getAttribute}), which the eForm renderer uses to
 * read field names and values and its own {@code $} attributes (issue #4764).
 */
@Tag("unit")
@DisplayName("eForm tag attribute lookup")
class EFormUtilAttributeUnitTest extends CarlosUnitTestBase {

    private static final String SAVED_VALUE_FIELD = "<input type=\"text\" name=\"sourceLast\" oscarDB=e$last#note"
            + " eform$name=\"Source eForm\" var$value='FAKE note' ref$visit_type=\"first\">";

    @Test
    @DisplayName("should read the eform$name, var$value and ref$ attributes of a saved-value field")
    void shouldReadSavedValueAttributes_fromFieldTag() {
        assertThat(EFormUtil.getAttribute("eform$name", SAVED_VALUE_FIELD)).isEqualTo("Source eForm");
        assertThat(EFormUtil.getAttribute("var$value", SAVED_VALUE_FIELD)).isEqualTo("FAKE note");
        assertThat(EFormUtil.getAttribute("ref$", SAVED_VALUE_FIELD, true)).isEqualTo("ref$visit_type=\"first\"");
        assertThat(EFormUtil.getAttribute("oscarDB", SAVED_VALUE_FIELD)).isEqualTo("e$last#note");
        assertThat(EFormUtil.getAttribute("name", SAVED_VALUE_FIELD)).isEqualTo("sourceLast");
    }

    @Test
    @DisplayName("should find the link$eform attribute an oscarOPEN field stores")
    void shouldFindLinkEformAttribute_forOscarOpenField() {
        String tag = "<input type=\"button\" name=\"openOther\" oscarOPEN=\"Other eForm\" link$eform=\"123\">";
        int start = tag.indexOf("link$eform");

        assertThat(EFormUtil.getAttribute("link$eform", tag)).isEqualTo("123");
        assertThat(EFormUtil.getAttributePos("link$eform", tag)).isEqualTo(start);
        assertThat(EFormUtil.getAttributeEndPos("link$eform", tag)).isEqualTo(start + "link$eform=\"123\"".length());
    }

    @Test
    @DisplayName("should not read the second half of a $ attribute as the ordinary attribute of that name")
    void shouldReadOrdinaryAttributes_whenDollarAttributesComeFirst() {
        String tag = "<input eform$name=\"Source eForm\" var$value=\"FAKE count value\" name=\"real\" value=\"shown\">";

        assertThat(EFormUtil.getAttribute("name", tag)).isEqualTo("real");
        assertThat(EFormUtil.getAttribute("value", tag)).isEqualTo("shown");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            "type          | checkbox",
            "TYPE          | checkbox",
            "name          | field_1",
            "data-carlos-x | y z",
            "xml:lang      | en",
            "my_field      | 1",
            "checked       | checked",
            "missing       | NULL",
            "$x            | NULL",
    })
    @DisplayName("should read ordinary attribute names as before")
    void shouldReadOrdinaryAttributes_asBefore(String key, String expected) {
        String tag = "<INPUT TYPE=checkbox name=\"field_1\" data-carlos-x='y z' xml:lang=\"en\" my_field = 1 checked $x=\"2\">";

        assertThat(EFormUtil.getAttribute(key, tag)).isEqualTo(expected);
    }

    @Test
    @DisplayName("should return the first of a repeated attribute, as before")
    void shouldReturnFirstAttribute_whenNameRepeats() {
        String tag = "<input name=\"first\" type=text NAME='second' name=third>";

        assertThat(EFormUtil.getAttribute("name", tag)).isEqualTo("first");
        assertThat(EFormUtil.getAttributePos("name", tag)).isEqualTo(tag.indexOf("name=\"first\""));
        assertThat(EFormUtil.getAttributeEndPos("name", tag)).isEqualTo(tag.indexOf(" type"));
        assertThat(EFormUtil.getAttribute("na", tag, true)).isEqualTo("name=\"first\"");
    }

    @Test
    @DisplayName("should still return a whole attribute for a prefix lookup")
    void shouldReturnWholeAttribute_forPrefixLookup() {
        assertThat(EFormUtil.getAttribute("data-", "<input name=\"a\" data-carlos-x='y'>", true)).isEqualTo("data-carlos-x='y'");
    }

    /**
     * Counts the characters the regex engine reads, so the scan cost is measured in steps rather than
     * wall-clock time. Every scan below looks for an attribute that is absent, which makes
     * {@code getAttributeMatcher} visit every match in the tag, its worst case.
     */
    private static final class CountingSequence implements CharSequence {
        private final String text;
        private long reads;

        CountingSequence(String text) {
            this.text = text;
        }

        @Override
        public char charAt(int index) {
            reads++;
            return text.charAt(index);
        }

        @Override
        public int length() {
            return text.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return text.subSequence(start, end);
        }

        @Override
        public String toString() {
            return text;
        }
    }

    private static long readsToScan(Pattern pattern, String text) {
        CountingSequence counted = new CountingSequence(text);
        Matcher m = pattern.matcher(counted);
        while (m.find()) {
            // visit every match, as a lookup for an absent attribute does
        }
        return counted.reads;
    }

    private static long readsToScanAttributes(String text) {
        CountingSequence counted = new CountingSequence(text);
        EFormUtil.scanAttributes(counted);
        return counted.reads;
    }

    private static IntFunction<String> hostileTag(String shape) {
        return switch (shape) {
            case "one long $ name" -> n -> "<input " + "a$".repeat(n) + ">";
            case "names with = and no value" -> n -> "<input " + "a$= ".repeat(n) + ">";
            case "one unclosed double quote" -> n -> "<input a$=\"" + "a$=b ".repeat(n);
            case "one unclosed single quote" -> n -> "<input a$='" + "a$=b ".repeat(n);
            case "names followed by long whitespace" -> n -> "<input " + ("a$" + " ".repeat(20)).repeat(n) + ">";
            case "alternating quotes" -> n -> "<input " + "a$=\" b$=' ".repeat(n);
            case "equals and quotes only" -> n -> "<input a" + "=\"='".repeat(n);
            default -> throw new IllegalArgumentException(shape);
        };
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "one long $ name",
            "names with = and no value",
            "one unclosed double quote",
            "one unclosed single quote",
            "names followed by long whitespace",
            "alternating quotes",
            "equals and quotes only",
    })
    @DisplayName("should scan a hostile tag in a number of steps linear in its length")
    void shouldScanInLinearSteps_forHostileTag(String shape) {
        IntFunction<String> tag = hostileTag(shape);
        String small = tag.apply(2_000);
        String large = tag.apply(8_000);

        long smallReads = readsToScanAttributes(small);
        long largeReads = readsToScanAttributes(large);

        // Four times the input costs about four times the reads when the scan is linear and sixteen
        // times when it is quadratic; allow a margin for constant overheads.
        assertThat((double) largeReads / smallReads).isLessThan(5.0);
        assertThat(largeReads).isLessThan(8L * large.length());
        assertThat(EFormUtil.getAttribute("absent", large)).isNull();
    }

    @Test
    @DisplayName("should flag a quadratic pattern with the same step count, so the linear check has teeth")
    void shouldFlagQuadraticPattern_withSameStepCount() {
        Pattern quadratic = Pattern.compile("a*b");

        long smallReads = readsToScan(quadratic, "a".repeat(2_000));
        long largeReads = readsToScan(quadratic, "a".repeat(8_000));

        assertThat((double) largeReads / smallReads).isGreaterThan(12.0);
    }

    /**
     * The single pattern the name and value patterns replaced (#4770). Sonar flagged it as too complex;
     * it is kept here only to prove the split finds exactly the same attributes.
     */
    private static final Pattern COMBINED_ATTRIBUTE_PATTERN = Pattern.compile(
            "([a-zA-Z][a-zA-Z0-9_:$-]*)(\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+))?", Pattern.CASE_INSENSITIVE);

    private static List<String> combinedScan(String tag) {
        List<String> found = new ArrayList<>();
        Matcher m = COMBINED_ATTRIBUTE_PATTERN.matcher(tag);
        while (m.find()) {
            found.add(m.start() + "-" + m.end() + " " + m.group(1) + " " + m.group(3));
        }
        return found;
    }

    private static List<String> splitScan(String tag) {
        return EFormUtil.scanAttributes(tag).stream()
                .map(a -> a.start() + "-" + a.end() + " " + a.name() + " " + a.value())
                .toList();
    }

    static Stream<String> equivalenceTags() {
        Stream<String> written = Stream.of(
                "<INPUT TYPE=checkbox name=\"field_1\" data-carlos-x='y z' xml:lang=\"en\" my_field = 1 checked $x=\"2\">",
                SAVED_VALUE_FIELD,
                "<input type=\"button\" name=\"openLinked\" oscarOPEN=\"Linked eForm\" link$eform=\"55\">",
                "<input name=\"a\" data-carlos-x='y'>",
                "",
                "<>",
                "<input =x>",
                "<input a= >",
                "<input a=\"unclosed>",
                "<input a='x\"y' b=\"x'y\">",
                "<input 9a=1 _b=2 -c=3 a9:b-c$d_e=f>",
                "<input\ta\n=\n\"v\"\n>",
                "<input a=b>c d=e>",
                "<input A$B=1 a$b= 'two' x>",
                "<input \u212Aelvin=1 \u017Fx=2 \u00E9t\u00E9=3>",
                "<input name=\"first\" type=text NAME='second' name=third>");
        Stream<String> hostile = Stream.of("one long $ name", "names with = and no value", "one unclosed double quote",
                        "one unclosed single quote", "names followed by long whitespace", "alternating quotes",
                        "equals and quotes only")
                .map(shape -> hostileTag(shape).apply(50));
        return Stream.concat(written, hostile);
    }

    @ParameterizedTest
    @MethodSource("equivalenceTags")
    @DisplayName("should find exactly the attributes the combined pattern found")
    void shouldFindSameAttributes_asCombinedPattern(String tag) {
        assertThat(splitScan(tag)).isEqualTo(combinedScan(tag));
    }
}
