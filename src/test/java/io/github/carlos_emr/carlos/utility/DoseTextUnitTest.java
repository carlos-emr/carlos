/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * ISMP Canada's <i>Do Not Use</i> list bans a trailing zero ("1.0 mg" is read as 10 mg) and a
 * missing leading zero (".5 mg" is read as 5 mg). {@link DoseText} is the one place that turns a
 * dose, strength or quantity number into text, so these cases are pinned here once (#6185).
 */
@Tag("unit")
@Tag("prescription")
@DisplayName("DoseText")
class DoseTextUnitTest {

    @Nested
    @DisplayName("a float dose, as the drugs table stores it")
    class FloatDose {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
            "1.0, 1",
            "0.5, 0.5",
            "0.0625, 0.0625",
            "500.0, 500",
            "1.0E7, 10000000",
            "0.0, 0",
            "2.5, 2.5",
            "0.1, 0.1",
            "0.125, 0.125",
            "100.0, 100",
            "1000.0, 1000",
            "30000.0, 30000",
            "1.0E-4, 0.0001",
            "1.0E10, 10000000000",
            "12.5, 12.5"
        })
        @DisplayName("should write the plain decimal with no trailing zero, exponent or digit grouping")
        void shouldWritePlainDecimal_forFloatDose(float dose, String expected) {
            assertThat(DoseText.of(dose)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
            "0.0625, 0.0625",
            "0.3125, 0.3125",
            "0.1875, 0.1875",
            "1.0625, 1.0625",
            "0.0004, 0.0004",
            "0.0005, 0.0005",
            "1.2345, 1.2345"
        })
        @DisplayName("should keep every decimal place of the stored dose rather than round it")
        void shouldKeepAllDecimalPlaces_forDoseFinerThanThousandths(float dose, String expected) {
            assertThat(DoseText.of(dose)).isEqualTo(expected);
        }

        @Test
        @DisplayName("should write the decimal a person typed, not the float's binary expansion")
        void shouldWriteShortestDecimal_forFloatWithoutExactBinaryForm() {
            // new BigDecimal(0.1f) is 0.100000001490116119384765625; the dose typed was 0.1.
            assertThat(DoseText.of(0.1f)).isEqualTo("0.1");
            assertThat(DoseText.of(0.3f)).isEqualTo("0.3");
        }

        @Test
        @DisplayName("should write zero without a sign for negative zero")
        void shouldWriteUnsignedZero_forNegativeZero() {
            assertThat(DoseText.of(-0.0f)).isEqualTo("0");
        }

        @Test
        @DisplayName("should keep the sign and the leading zero of a negative fraction")
        void shouldKeepSignAndLeadingZero_forNegativeFraction() {
            assertThat(DoseText.of(-0.5f)).isEqualTo("-0.5");
        }

        @Test
        @DisplayName("should name a value that is not a number rather than invent a dose")
        void shouldNameNonFiniteValue_forNaNAndInfinity() {
            assertThat(DoseText.of(Float.NaN)).isEqualTo("NaN");
            assertThat(DoseText.of(Float.POSITIVE_INFINITY)).isEqualTo("Infinity");
            assertThat(DoseText.of(Float.NEGATIVE_INFINITY)).isEqualTo("-Infinity");
        }

        @Test
        @DisplayName("should write the same text whatever the server's default locale is")
        void shouldIgnoreDefaultLocale_forDecimalSeparatorAndGrouping() {
            Locale original = Locale.getDefault();
            try {
                // French Canada groups thousands with a space and uses a decimal comma.
                Locale.setDefault(Locale.CANADA_FRENCH);
                assertThat(DoseText.of(1234.5f)).isEqualTo("1234.5");
                assertThat(DoseText.of(0.5f)).isEqualTo("0.5");
            } finally {
                Locale.setDefault(original);
            }
        }
    }

    @Nested
    @DisplayName("a double strength, as DrugRef serves it")
    class DoubleStrength {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
            "1.0, 1",
            "0.5, 0.5",
            "0.0625, 0.0625",
            "500.0, 500",
            "1.0E7, 10000000",
            "0.0, 0",
            "0.1, 0.1",
            "0.025, 0.025",
            "1.0E-7, 0.0000001",
            "1.0E21, 1000000000000000000000"
        })
        @DisplayName("should write the plain decimal with no trailing zero or exponent")
        void shouldWritePlainDecimal_forDoubleStrength(double strength, String expected) {
            assertThat(DoseText.of(strength)).isEqualTo(expected);
        }

        @Test
        @DisplayName("should write zero without a sign for negative zero")
        void shouldWriteUnsignedZero_forNegativeZero() {
            assertThat(DoseText.of(-0.0d)).isEqualTo("0");
        }

        @Test
        @DisplayName("should name a value that is not a number rather than invent a strength")
        void shouldNameNonFiniteValue_forNaNAndInfinity() {
            assertThat(DoseText.of(Double.NaN)).isEqualTo("NaN");
            assertThat(DoseText.of(Double.POSITIVE_INFINITY)).isEqualTo("Infinity");
        }
    }

    @Nested
    @DisplayName("an exact decimal")
    class ExactDecimal {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
            "1.0, 1",
            "1.50, 1.5",
            "0.50, 0.5",
            ".5, 0.5",
            "0.0625, 0.0625",
            "500.0, 500",
            "1.0E7, 10000000",
            "1E+7, 10000000",
            "100, 100",
            "0.0, 0",
            "0E-10, 0",
            "0E+3, 0",
            "-0.0, 0",
            "-.5, -0.5"
        })
        @DisplayName("should write the plain decimal with a leading zero and no trailing zero")
        void shouldWritePlainDecimal_forExactDecimal(String decimal, String expected) {
            assertThat(DoseText.of(new BigDecimal(decimal))).isEqualTo(expected);
        }

        @Test
        @DisplayName("should refuse a missing decimal rather than print the word null as a dose")
        void shouldThrowNullPointerException_forNullDecimal() {
            assertThatThrownBy(() -> DoseText.of((BigDecimal) null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("a number that arrives as text")
    class NumericText {

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @CsvSource({
            "250.0, 250",
            "1.0, 1",
            "0.5, 0.5",
            ".5, 0.5",
            "0.0625, 0.0625",
            "500.0, 500",
            "1.0E7, 10000000",
            "2.50, 2.5",
            "30, 30",
            "0.0, 0",
            "-0.0, 0"
        })
        @DisplayName("should rewrite text that is one decimal number as its plain decimal")
        void shouldWritePlainDecimal_forTextThatIsOneNumber(String text, String expected) {
            assertThat(DoseText.ofNumericText(text)).isEqualTo(expected);
        }

        @Test
        @DisplayName("should ignore blanks around the number")
        void shouldIgnoreSurroundingBlanks_forTextThatIsOneNumber() {
            assertThat(DoseText.ofNumericText(" 250.0 ")).isEqualTo("250");
        }

        @ParameterizedTest(name = "\"{0}\" is left alone")
        @CsvSource({
            "2.5 mg/mL",
            "250.0 MG",
            "1/2",
            "1-2",
            "'1,000'",
            "NaN",
            "Infinity",
            "one",
            "0x1F",
            "1.0.0"
        })
        @DisplayName("should return text that is not one decimal number exactly as given")
        void shouldReturnTextUnchanged_whenItIsNotOneNumber(String text) {
            assertThat(DoseText.ofNumericText(text)).isEqualTo(text);
        }

        @Test
        @DisplayName("should return blank and missing text as given")
        void shouldReturnTextUnchanged_whenBlankOrMissing() {
            assertThat(DoseText.ofNumericText("")).isEmpty();
            assertThat(DoseText.ofNumericText("  ")).isEqualTo("  ");
            assertThat(DoseText.ofNumericText(null)).isNull();
        }

        @Test
        @DisplayName("should leave a number too large to be a dose as given instead of expanding it")
        void shouldReturnTextUnchanged_whenExponentIsHuge() {
            // "1E999999999" is a valid decimal whose plain form is a billion digits long. Text from
            // another system must not be able to make the server build that string.
            assertThat(DoseText.ofNumericText("1E999999999")).isEqualTo("1E999999999");
            assertThat(DoseText.ofNumericText("1E-999999999")).isEqualTo("1E-999999999");
            assertThat(DoseText.ofNumericText("1E400")).isEqualTo("1E400");
        }

        @Test
        @DisplayName("should leave digits too long to be a dose as given")
        void shouldReturnTextUnchanged_whenTextIsLongerThanAnyDose() {
            String digits = "1".repeat(65) + ".0";
            assertThat(DoseText.ofNumericText(digits)).isEqualTo(digits);
            assertThat(DoseText.ofNumericText("1".repeat(60) + ".50")).isEqualTo("1".repeat(60) + ".5");
        }
    }

    @Nested
    @DisplayName("text that holds numbers")
    class NumbersInText {

        @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
        @CsvSource(delimiter = '|', value = {
            "amoxicillin 250.0mg capsule | amoxicillin 250mg capsule",
            "250.0mg | 250mg",
            "5.0 mg / 325.0 mg | 5 mg / 325 mg",
            "250.0mg/5.0ml | 250mg/5ml",
            "12.50mg | 12.5mg",
            "0.50 mg | 0.5 mg",
            "1.0mg | 1mg",
            "0.0625mg | 0.0625mg",
            "1.0E7unit | 10000000unit",
            "1.0E7IU | 10000000IU",
            "penicillin g 1.0E7unit powder | penicillin g 10000000unit powder",
            "1.0E-4mg | 0.0001mg",
            "2.0each | 2each",
            "amoxicillin 250mg capsule | amoxicillin 250mg capsule",
            "hydrochlorothiazide 12.5mg tablet / ramipril 5mg tablet | hydrochlorothiazide 12.5mg tablet / ramipril 5mg tablet",
            "vitamin b12 | vitamin b12"
        })
        @DisplayName("should write every decimal number in the text without its trailing zeros")
        void shouldWritePlainDecimals_forEveryNumberInText(String text, String expected) {
            assertThat(DoseText.withPlainNumbers(text)).isEqualTo(expected);
        }

        @Test
        @DisplayName("should keep different strengths different")
        void shouldKeepDifferentNumbersDifferent_whenComparingTexts() {
            assertThat(DoseText.withPlainNumbers("250.0mg")).isNotEqualTo(DoseText.withPlainNumbers("25.0mg"));
            assertThat(DoseText.withPlainNumbers("2.5mg")).isNotEqualTo(DoseText.withPlainNumbers("25mg"));
            assertThat(DoseText.withPlainNumbers("250.5mg")).isNotEqualTo(DoseText.withPlainNumbers("250mg"));
            assertThat(DoseText.withPlainNumbers("10.0mg")).isNotEqualTo(DoseText.withPlainNumbers("1mg"));
            assertThat(DoseText.withPlainNumbers("1.0E7unit")).isNotEqualTo(DoseText.withPlainNumbers("1.0E6unit"));
        }

        @Test
        @DisplayName("should compare a strength stored with an exponent as equal to its plain form")
        void shouldGiveSameText_forExponentAndPlainFormsOfOneStrength() {
            // Float.toString writes ten million as 1.0E7: a 10 000 000 unit product was stored as
            // "1.0E7unit" and is stored as "10000000unit" now.
            assertThat(DoseText.withPlainNumbers("1.0E7unit")).isEqualTo(DoseText.withPlainNumbers("10000000unit"));
            assertThat(DoseText.withPlainNumbers("1.0E-4mg")).isEqualTo(DoseText.withPlainNumbers("0.0001mg"));
        }

        @Test
        @DisplayName("should return blank and missing text as given")
        void shouldReturnTextUnchanged_whenBlankOrMissing() {
            assertThat(DoseText.withPlainNumbers("")).isEmpty();
            assertThat(DoseText.withPlainNumbers(null)).isNull();
        }
    }

    @Nested
    @DisplayName("a dose range")
    class Range {

        @Test
        @DisplayName("should join the two bounds with a hyphen when they differ")
        void shouldJoinBounds_whenLowAndHighDiffer() {
            assertThat(DoseText.range(1.0f, 2.0f)).isEqualTo("1-2");
            assertThat(DoseText.range(0.5f, 1.0f)).isEqualTo("0.5-1");
            assertThat(DoseText.range(0.0625f, 0.125f)).isEqualTo("0.0625-0.125");
        }

        @Test
        @DisplayName("should write a single dose when both bounds are the same")
        void shouldWriteSingleDose_whenLowEqualsHigh() {
            assertThat(DoseText.range(1.0f, 1.0f)).isEqualTo("1");
            assertThat(DoseText.range(0.5f, 0.5f)).isEqualTo("0.5");
            assertThat(DoseText.range(0.0f, 0.0f)).isEqualTo("0");
        }

        @Test
        @DisplayName("should keep the stored order of the bounds")
        void shouldKeepStoredOrder_whenLowIsGreaterThanHigh() {
            // A legacy row can hold take 2-1; reordering it here would hide that from the reader.
            assertThat(DoseText.range(2.0f, 1.0f)).isEqualTo("2-1");
        }
    }

    @Test
    @DisplayName("should never produce a do-not-use form across the doses seen in practice")
    void shouldAvoidDoNotUseForms_forCommonDoses() {
        float[] doses = {0.0625f, 0.125f, 0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 2.5f, 5f, 7.5f, 10f, 12.5f,
            25f, 50f, 100f, 250f, 500f, 1000f, 5000f, 10000f, 100000f, 1000000f, 10000000f};
        for (float dose : doses) {
            String text = DoseText.of(dose);
            assertThat(text).as("dose %s", dose).doesNotEndWith(".0").doesNotStartWith(".")
                    .doesNotContain("E").doesNotContain(",").doesNotContain(" ");
            assertThat(Float.parseFloat(text)).as("dose %s reads back", dose).isEqualTo(dose);
        }
    }
}
