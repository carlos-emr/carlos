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
package io.github.carlos_emr.carlos.decisionSupport.prevention;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;

import io.github.carlos_emr.carlos.drools.DroolsHelper;
import io.github.carlos_emr.carlos.drools.RuleBaseFactory;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.DSCondition;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.RuleBaseCreator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the condition processing logic in {@link DSPreventionDrools}.
 *
 * <p>Since {@code DSPreventionDrools.getConditions()} is a private static method, these
 * tests exercise the condition processors indirectly by constructing {@link DSCondition}
 * objects that mirror the expected output of each condition type processor, then verifying
 * that the generated DRL text via {@link RuleBaseCreator#getRule(String, String, List, String)}
 * contains correct Drools condition expressions.</p>
 *
 * <p>The tests also verify that generated DRL compiles successfully through
 * {@link DroolsHelper#createKieBaseFromDrl(String)}, catching syntax errors in the
 * generated rule text.</p>
 *
 * <p>No Spring context is required because all methods under test are static utilities
 * with no external dependencies beyond JDOM2 and the Drools KIE runtime.</p>
 *
 * @see DSPreventionDrools
 * @see DSCondition
 * @see RuleBaseCreator
 * @since 2026-02-18
 */
@Tag("unit")
@Tag("drools")
@Tag("prevention")
@DisplayName("DSPreventionDrools - Condition Processors")
class DSPreventionDroolsUnitTest {

    /**
     * Fully qualified class path for the Prevention fact object, matching
     * {@link DSPreventionDrools#preventionObjectClassPath}.
     */
    private static final String PREVENTION_CLASS = DSPreventionDrools.preventionObjectClassPath;

    private RuleBaseCreator creator;

    /**
     * Flush the KieBase cache and create a fresh {@code RuleBaseCreator} before each test
     * to ensure test isolation.
     */
    @BeforeEach
    void setUp() {
        RuleBaseFactory.flushAllCached();
        creator = new RuleBaseCreator();
    }

    /**
     * Helper that generates a DRL rule string from the given conditions using the
     * Prevention fact class.
     *
     * @param conditions list of DSCondition objects representing parsed conditions
     * @return the generated DRL rule text
     */
    private String generateRule(List<DSCondition> conditions) {
        return creator.getRule("TEST-0", PREVENTION_CLASS, conditions, "m.addWarning(\"DTaP\",\"test\");");
    }

    /**
     * Helper that wraps a single DRL rule fragment with a package declaration to
     * produce a complete compilable DRL string.
     *
     * @param rule the rule text from {@link RuleBaseCreator#getRule}
     * @return complete DRL with package declaration
     */
    private String wrapForCompilation(String rule) {
        return "package test;\n" + rule;
    }

    /**
     * Tests for the {@code age} condition type processor ({@code processAgeElement}).
     *
     * <p>The age processor translates XML age expressions into calls to
     * {@code getAgeInMonths()} (when the value has an "m" suffix) or
     * {@code getAgeInYears()} (when the value has a "y" suffix or no suffix).</p>
     */
    @Nested
    @DisplayName("age condition type")
    class AgeCondition {

        /**
         * Verifies that an age range in months (e.g., "2m-72m") generates two
         * DSConditions that produce: {@code getAgeInMonths() >= 2} and
         * {@code getAgeInMonths() <= 72}.
         */
        @Test
        @DisplayName("should generate age range in months from between-style value")
        void shouldGenerateAgeRangeInMonths_fromBetweenStyleValue() {
            List<DSCondition> conditions = Arrays.asList(
                    new DSCondition("getAgeInMonths", "", ">=", "2"),
                    new DSCondition("getAgeInMonths", "", "<=", "72")
            );

            String rule = generateRule(conditions);

            // Drools condition expression format: eval( m.methodCall() operator value )
            assertThat(rule).contains("m.getAgeInMonths() >= 2");
            assertThat(rule).contains("m.getAgeInMonths() <= 72");
        }

        /**
         * Verifies that an age range in years (e.g., "4-65") generates two
         * conditions using {@code getAgeInYears()}.
         */
        @Test
        @DisplayName("should generate age range in years from between-style value without suffix")
        void shouldGenerateAgeRangeInYears_fromBetweenStyleValueWithoutSuffix() {
            List<DSCondition> conditions = Arrays.asList(
                    new DSCondition("getAgeInYears", "", ">=", "4"),
                    new DSCondition("getAgeInYears", "", "<=", "65")
            );

            String rule = generateRule(conditions);

            assertThat(rule).contains("m.getAgeInYears() >= 4");
            assertThat(rule).contains("m.getAgeInYears() <= 65");
        }

        /**
         * Verifies that a greater-than age in months (e.g., ">4m") generates
         * a single condition {@code getAgeInMonths() >= 4}.
         * Note: the processor uses >= (not strict >) per the implementation.
         */
        @Test
        @DisplayName("should generate greater-or-equal condition for months")
        void shouldGenerateGreaterOrEqualCondition_forMonths() {
            DSCondition condition = new DSCondition("getAgeInMonths", "", ">=", "4");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("m.getAgeInMonths() >= 4");
        }

        /**
         * Verifies that a less-than age in years (e.g., "<65") generates
         * a single condition {@code getAgeInYears() <= 65}.
         * Note: the processor uses <= (not strict <) per the implementation.
         */
        @Test
        @DisplayName("should generate less-or-equal condition for years")
        void shouldGenerateLessOrEqualCondition_forYears() {
            DSCondition condition = new DSCondition("getAgeInYears", "", "<=", "65");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("m.getAgeInYears() <= 65");
        }

        /**
         * Verifies that a not-equal age in months (e.g., "!=12m") generates
         * {@code getAgeInMonths() != 12}.
         */
        @Test
        @DisplayName("should generate not-equal condition for months")
        void shouldGenerateNotEqualCondition_forMonths() {
            DSCondition condition = new DSCondition("getAgeInMonths", "", "!=", "12");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("m.getAgeInMonths() != 12");
        }

        /**
         * Verifies that an exact age in months (e.g., "4m") generates
         * {@code getAgeInMonths() == 4}.
         */
        @Test
        @DisplayName("should generate exact match condition for months")
        void shouldGenerateExactMatchCondition_forMonths() {
            DSCondition condition = new DSCondition("getAgeInMonths", "", "==", "4");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("m.getAgeInMonths() == 4");
        }

        /**
         * Verifies that an age range with mixed units (months lower, years upper)
         * generates the correct method calls for each bound independently.
         */
        @Test
        @DisplayName("should handle mixed month and year units in range")
        void shouldHandleMixedMonthAndYearUnits_inRange() {
            // Represents an age condition like "6m-5" (6 months to 5 years)
            List<DSCondition> conditions = Arrays.asList(
                    new DSCondition("getAgeInMonths", "", ">=", "6"),
                    new DSCondition("getAgeInYears", "", "<=", "5")
            );

            String rule = generateRule(conditions);

            assertThat(rule).contains("m.getAgeInMonths() >= 6");
            assertThat(rule).contains("m.getAgeInYears() <= 5");
        }

        /**
         * Verifies that an age range in months compiles to a valid KieBase via Drools.
         */
        @Test
        @DisplayName("should produce compilable DRL from age range conditions")
        void shouldProduceCompilableDrl_fromAgeRangeConditions() throws Exception {
            List<DSCondition> conditions = Arrays.asList(
                    new DSCondition("getAgeInMonths", "", ">=", "2"),
                    new DSCondition("getAgeInMonths", "", "<=", "72")
            );

            String rule = generateRule(conditions);
            KieBase kieBase = DroolsHelper.createKieBaseFromDrl(wrapForCompilation(rule));

            assertThat(kieBase).isNotNull();
            assertThat(kieBase.getKiePackages()).hasSize(1);
            assertThat(kieBase.getKiePackages().iterator().next().getRules()).hasSize(1);
        }
    }

    /**
     * Tests for the {@code numberOfPreventions} condition type processor
     * ({@code processGenericNumberValues} with method "getNumberOfPreventionType").
     *
     * <p>This processor handles counting how many times a specific prevention type
     * has been administered, supporting range, greater-than, less-than, not-equal,
     * and exact match formats.</p>
     */
    @Nested
    @DisplayName("numberOfPreventions condition type")
    class NumberOfPreventionsCondition {

        /**
         * Verifies that an exact count (e.g., value="0") generates
         * {@code getNumberOfPreventionType("DTaP-IPV") == 0}.
         */
        @Test
        @DisplayName("should generate exact match with param from value")
        void shouldGenerateExactMatch_withParamFromValue() {
            DSCondition condition = new DSCondition("getNumberOfPreventionType", "DTaP-IPV", "==", "0");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("m.getNumberOfPreventionType(\"DTaP-IPV\") == 0");
        }

        /**
         * Verifies that a range (e.g., value="1-3") generates two conditions:
         * {@code >= 1} and {@code <= 3}.
         */
        @Test
        @DisplayName("should generate range conditions from between-style value")
        void shouldGenerateRangeConditions_fromBetweenStyleValue() {
            List<DSCondition> conditions = Arrays.asList(
                    new DSCondition("getNumberOfPreventionType", "DTaP", ">=", "1"),
                    new DSCondition("getNumberOfPreventionType", "DTaP", "<=", "3")
            );

            String rule = generateRule(conditions);

            assertThat(rule).contains("m.getNumberOfPreventionType(\"DTaP\") >= 1");
            assertThat(rule).contains("m.getNumberOfPreventionType(\"DTaP\") <= 3");
        }

        /**
         * Verifies that a greater-than value (e.g., ">4") generates
         * {@code getNumberOfPreventionType("Flu") >= 4}.
         */
        @Test
        @DisplayName("should generate greater-or-equal condition from greater-than value")
        void shouldGenerateGreaterOrEqualCondition_fromGreaterThanValue() {
            DSCondition condition = new DSCondition("getNumberOfPreventionType", "Flu", ">=", "4");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("m.getNumberOfPreventionType(\"Flu\") >= 4");
        }

        /**
         * Verifies that a not-equal value (e.g., "!=0") generates
         * {@code getNumberOfPreventionType("Td") != 0}.
         */
        @Test
        @DisplayName("should generate not-equal condition from not-equal value")
        void shouldGenerateNotEqualCondition_fromNotEqualValue() {
            DSCondition condition = new DSCondition("getNumberOfPreventionType", "Td", "!=", "0");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("m.getNumberOfPreventionType(\"Td\") != 0");
        }

        /**
         * Verifies that a numberOfPreventions condition compiles to valid DRL.
         */
        @Test
        @DisplayName("should produce compilable DRL from numberOfPreventions condition")
        void shouldProduceCompilableDrl_fromNumberOfPreventionsCondition() throws Exception {
            DSCondition condition = new DSCondition("getNumberOfPreventionType", "DTaP-IPV", "==", "0");

            String rule = generateRule(Collections.singletonList(condition));
            KieBase kieBase = DroolsHelper.createKieBaseFromDrl(wrapForCompilation(rule));

            assertThat(kieBase).isNotNull();
            assertThat(kieBase.getKiePackages()).hasSize(1);
            assertThat(kieBase.getKiePackages().iterator().next().getRules()).hasSize(1);
        }
    }

    /**
     * Tests for the {@code numberOfMonthsSinceLast} condition type processor
     * ({@code processGenericNumberValues} with method "getHowManyMonthsSinceLast").
     */
    @Nested
    @DisplayName("numberOfMonthsSinceLast condition type")
    class NumberOfMonthsSinceLastCondition {

        /**
         * Verifies that an exact month count generates
         * {@code getHowManyMonthsSinceLast("Flu") == 6}.
         */
        @Test
        @DisplayName("should generate exact match for months since last prevention")
        void shouldGenerateExactMatch_forMonthsSinceLastPrevention() {
            DSCondition condition = new DSCondition("getHowManyMonthsSinceLast", "Flu", "==", "6");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("m.getHowManyMonthsSinceLast(\"Flu\") == 6");
        }

        /**
         * Verifies that a greater-or-equal month count generates the correct expression.
         */
        @Test
        @DisplayName("should generate greater-or-equal for months since last")
        void shouldGenerateGreaterOrEqual_forMonthsSinceLast() {
            DSCondition condition = new DSCondition("getHowManyMonthsSinceLast", "Td", ">=", "120");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("m.getHowManyMonthsSinceLast(\"Td\") >= 120");
        }

        /**
         * Verifies that a range of months since last generates two conditions.
         */
        @Test
        @DisplayName("should generate range conditions for months since last")
        void shouldGenerateRangeConditions_forMonthsSinceLast() {
            List<DSCondition> conditions = Arrays.asList(
                    new DSCondition("getHowManyMonthsSinceLast", "DTaP", ">=", "6"),
                    new DSCondition("getHowManyMonthsSinceLast", "DTaP", "<=", "12")
            );

            String rule = generateRule(conditions);

            assertThat(rule).contains("m.getHowManyMonthsSinceLast(\"DTaP\") >= 6");
            assertThat(rule).contains("m.getHowManyMonthsSinceLast(\"DTaP\") <= 12");
        }

        /**
         * Verifies that the generated DRL for months-since-last compiles to a valid KieBase.
         */
        @Test
        @DisplayName("should produce compilable DRL from months-since-last condition")
        void shouldProduceCompilableDrl_fromMonthsSinceLastCondition() throws Exception {
            DSCondition condition = new DSCondition("getHowManyMonthsSinceLast", "Flu", ">=", "12");

            String rule = generateRule(Collections.singletonList(condition));
            KieBase kieBase = DroolsHelper.createKieBaseFromDrl(wrapForCompilation(rule));

            assertThat(kieBase).isNotNull();
            assertThat(kieBase.getKiePackages()).hasSize(1);
            assertThat(kieBase.getKiePackages().iterator().next().getRules()).hasSize(1);
        }
    }

    /**
     * Tests for the {@code numberOfDaysSinceLast} condition type processor
     * ({@code processGenericNumberValues} with method "getHowManyDaysSinceLast").
     */
    @Nested
    @DisplayName("numberOfDaysSinceLast condition type")
    class NumberOfDaysSinceLastCondition {

        /**
         * Verifies that a days-since-last condition generates the correct expression.
         */
        @Test
        @DisplayName("should generate condition for days since last prevention")
        void shouldGenerateCondition_forDaysSinceLastPrevention() {
            DSCondition condition = new DSCondition("getHowManyDaysSinceLast", "DTaP", ">=", "28");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("m.getHowManyDaysSinceLast(\"DTaP\") >= 28");
        }

        /**
         * Verifies that the generated DRL for days-since-last compiles successfully.
         */
        @Test
        @DisplayName("should produce compilable DRL from days-since-last condition")
        void shouldProduceCompilableDrl_fromDaysSinceLastCondition() throws Exception {
            DSCondition condition = new DSCondition("getHowManyDaysSinceLast", "Flu", "<=", "365");

            String rule = generateRule(Collections.singletonList(condition));
            KieBase kieBase = DroolsHelper.createKieBaseFromDrl(wrapForCompilation(rule));

            assertThat(kieBase).isNotNull();
            assertThat(kieBase.getKiePackages()).hasSize(1);
            assertThat(kieBase.getKiePackages().iterator().next().getRules()).hasSize(1);
        }
    }

    /**
     * Tests for boolean condition types: {@code isMale}, {@code isFemale},
     * {@code isNextDateSet}, and their negated forms.
     *
     * <p>The boolean processors produce DSConditions with either empty comparison
     * (truthy evaluation) or {@code == false} (negated evaluation). When the
     * comparison and value are both empty strings, the Drools condition expression
     * evaluates the boolean return value directly.</p>
     */
    @Nested
    @DisplayName("boolean condition types")
    class BooleanConditions {

        /**
         * Verifies that {@code isMale} generates a truthy condition expression with
         * no parameters (null param maps to parameterless method call in DSCondition,
         * producing {@code isMale()} with no argument).
         */
        @Test
        @DisplayName("should generate parameterless isMale condition expression")
        void shouldGenerateParameterlessIsMaleConditionExpression() {
            // isMale passes null as paramDefaultIfNull, so param is null
            DSCondition condition = new DSCondition("isMale", null, "", "");

            String rule = generateRule(Collections.singletonList(condition));

            // With null param, DSCondition.getType() returns "isMale()"
            assertThat(rule).contains("m.isMale()");
            // Should NOT contain a string parameter in the method call
            assertThat(rule).doesNotContain("isMale(\"");
        }

        /**
         * Verifies that {@code isFemale} generates a truthy condition expression with
         * no parameters, analogous to isMale.
         */
        @Test
        @DisplayName("should generate parameterless isFemale condition expression")
        void shouldGenerateParameterlessIsFemaleConditionExpression() {
            DSCondition condition = new DSCondition("isFemale", null, "", "");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("m.isFemale()");
            assertThat(rule).doesNotContain("isFemale(\"");
        }

        /**
         * Verifies that {@code isNextDateSet} generates a truthy condition expression
         * with the prevention type as parameter.
         */
        @Test
        @DisplayName("should generate isNextDateSet with prevention type param")
        void shouldGenerateIsNextDateSet_withPreventionTypeParam() {
            // isNextDateSet uses the prevention type as default param
            DSCondition condition = new DSCondition("isNextDateSet", "DTaP", "", "");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("m.isNextDateSet(\"DTaP\")");
        }

        /**
         * Verifies that {@code !isNextDateSet} generates a negated condition expression
         * with {@code == false}.
         */
        @Test
        @DisplayName("should generate negated isNextDateSet with false comparison")
        void shouldGenerateNegatedIsNextDateSet_withFalseComparison() {
            DSCondition condition = new DSCondition("isNextDateSet", "DTaP", "==", "false");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("m.isNextDateSet(\"DTaP\") == false");
        }

        /**
         * Verifies that {@code isPassedNextDate} generates a truthy condition expression.
         */
        @Test
        @DisplayName("should generate isPassedNextDate with prevention type param")
        void shouldGenerateIsPassedNextDate_withPreventionTypeParam() {
            DSCondition condition = new DSCondition("isPassedNextDate", "Flu", "", "");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("m.isPassedNextDate(\"Flu\")");
        }

        /**
         * Verifies that {@code !isPassedNextDate} generates a negated condition expression.
         */
        @Test
        @DisplayName("should generate negated isPassedNextDate with false comparison")
        void shouldGenerateNegatedIsPassedNextDate_withFalseComparison() {
            DSCondition condition = new DSCondition("isPassedNextDate", "Flu", "==", "false");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("m.isPassedNextDate(\"Flu\") == false");
        }

        /**
         * Verifies that {@code isPreventionNever} generates a truthy condition expression.
         */
        @Test
        @DisplayName("should generate isPreventionNever with prevention type param")
        void shouldGenerateIsPreventionNever_withPreventionTypeParam() {
            DSCondition condition = new DSCondition("isPreventionNever", "DTaP", "", "");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("m.isPreventionNever(\"DTaP\")");
        }

        /**
         * Verifies that {@code !isPreventionNever} generates a negated condition expression.
         */
        @Test
        @DisplayName("should generate negated isPreventionNever with false comparison")
        void shouldGenerateNegatedIsPreventionNever_withFalseComparison() {
            DSCondition condition = new DSCondition("isPreventionNever", "DTaP", "==", "false");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("m.isPreventionNever(\"DTaP\") == false");
        }

        /**
         * Verifies that {@code isInelligible} (note: misspelling preserved from
         * original codebase for backward compatibility with existing XML guideline
         * files) generates a truthy condition expression.
         */
        @Test
        @DisplayName("should generate isInelligible with prevention type param")
        void shouldGenerateIsInelligible_withPreventionTypeParam() {
            DSCondition condition = new DSCondition("isInelligible", "Flu", "", "");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("m.isInelligible(\"Flu\")");
        }

        /**
         * Verifies that {@code !isInelligible} generates a negated condition expression.
         */
        @Test
        @DisplayName("should generate negated isInelligible with false comparison")
        void shouldGenerateNegatedIsInelligible_withFalseComparison() {
            DSCondition condition = new DSCondition("isInelligible", "Flu", "==", "false");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("m.isInelligible(\"Flu\") == false");
        }

        /**
         * Verifies that boolean conditions with empty comparison and value produce
         * DRL that compiles successfully. The Drools engine treats the boolean
         * method return as the condition result directly.
         */
        @Test
        @DisplayName("should produce compilable DRL from truthy boolean condition")
        void shouldProduceCompilableDrl_fromTruthyBooleanCondition() throws Exception {
            // A truthy boolean condition with empty comparison and value is valid DRL:
            // the Drools engine evaluates the boolean return directly
            DSCondition condition = new DSCondition("isMale", null, "", "");

            String rule = generateRule(Collections.singletonList(condition));
            KieBase kieBase = DroolsHelper.createKieBaseFromDrl(wrapForCompilation(rule));

            assertThat(kieBase).isNotNull();
            assertThat(kieBase.getKiePackages()).hasSize(1);
            assertThat(kieBase.getKiePackages().iterator().next().getRules()).hasSize(1);
        }

        /**
         * Verifies that negated boolean conditions produce compilable DRL.
         */
        @Test
        @DisplayName("should produce compilable DRL from negated boolean condition")
        void shouldProduceCompilableDrl_fromNegatedBooleanCondition() throws Exception {
            DSCondition condition = new DSCondition("isNextDateSet", "DTaP", "==", "false");

            String rule = generateRule(Collections.singletonList(condition));
            KieBase kieBase = DroolsHelper.createKieBaseFromDrl(wrapForCompilation(rule));

            assertThat(kieBase).isNotNull();
            assertThat(kieBase.getKiePackages()).hasSize(1);
            assertThat(kieBase.getKiePackages().iterator().next().getRules()).hasSize(1);
        }
    }

    /**
     * Tests for the {@code todayIsInDateRange} condition type processor
     * and its negated form {@code !todayIsInDateRange}.
     *
     * <p>The date range processor splits a comma-separated value into start and end
     * dates and builds a multi-argument parameter string for the
     * {@code isTodayinDateRange()} method.</p>
     */
    @Nested
    @DisplayName("todayIsInDateRange condition type")
    class TodayIsInDateRangeCondition {

        /**
         * Verifies that the date range condition generates a DSCondition that calls
         * {@code isTodayinDateRange} with two string arguments assembled as a
         * multi-argument param: {@code startDate","endDate}.
         */
        @Test
        @DisplayName("should generate isTodayinDateRange with multi-argument param")
        void shouldGenerateIsTodayInDateRange_withMultiArgumentParam() {
            // The processor assembles the param as: "2024-09-01","2025-04-30"
            // which DSCondition.getType() wraps into: isTodayinDateRange("2024-09-01","2025-04-30")
            String multiParam = "2024-09-01\",\"2025-04-30";
            DSCondition condition = new DSCondition("isTodayinDateRange", multiParam, "", "");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("m.isTodayinDateRange(\"2024-09-01\",\"2025-04-30\")");
        }

        /**
         * Verifies that the negated date range condition generates {@code == false}.
         */
        @Test
        @DisplayName("should generate negated isTodayinDateRange with false comparison")
        void shouldGenerateNegatedIsTodayInDateRange_withFalseComparison() {
            String multiParam = "2024-09-01\",\"2025-04-30";
            DSCondition condition = new DSCondition("isTodayinDateRange", multiParam, "==", "false");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("m.isTodayinDateRange(\"2024-09-01\",\"2025-04-30\") == false");
        }

        /**
         * Verifies that the generated DRL for a date range condition compiles.
         */
        @Test
        @DisplayName("should produce compilable DRL from todayIsInDateRange condition")
        void shouldProduceCompilableDrl_fromTodayIsInDateRangeCondition() throws Exception {
            String multiParam = "2024-09-01\",\"2025-04-30";
            DSCondition condition = new DSCondition("isTodayinDateRange", multiParam, "", "");

            String rule = generateRule(Collections.singletonList(condition));
            KieBase kieBase = DroolsHelper.createKieBaseFromDrl(wrapForCompilation(rule));

            assertThat(kieBase).isNotNull();
            assertThat(kieBase.getKiePackages()).hasSize(1);
            assertThat(kieBase.getKiePackages().iterator().next().getRules()).hasSize(1);
        }
    }

    /**
     * Tests for the {@code lastPreventionIsWithinRange} condition type processor
     * and its negated form {@code !lastPreventionIsWithinRange}.
     *
     * <p>This processor assembles a three-argument parameter string from the
     * {@code param} and comma-separated {@code value} attributes for the
     * {@code isLastPreventionWithinRange()} method.</p>
     */
    @Nested
    @DisplayName("lastPreventionIsWithinRange condition type")
    class LastPreventionIsWithinRangeCondition {

        /**
         * Verifies that the processor assembles a three-argument param:
         * {@code preventionType","startDate","endDate}.
         */
        @Test
        @DisplayName("should generate isLastPreventionWithinRange with three-argument param")
        void shouldGenerateIsLastPreventionWithinRange_withThreeArgumentParam() {
            // The processor builds: param + '","' + startDate + '","' + endDate
            // For param="Flu" and value="2024-09-01,2025-04-30":
            // Result param string: Flu","2024-09-01","2025-04-30
            String multiParam = "Flu\",\"2024-09-01\",\"2025-04-30";
            DSCondition condition = new DSCondition("isLastPreventionWithinRange", multiParam, "", "");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("m.isLastPreventionWithinRange(\"Flu\",\"2024-09-01\",\"2025-04-30\")");
        }

        /**
         * Verifies that the negated form generates {@code == false}.
         */
        @Test
        @DisplayName("should generate negated isLastPreventionWithinRange with false comparison")
        void shouldGenerateNegatedIsLastPreventionWithinRange_withFalseComparison() {
            String multiParam = "Flu\",\"2024-09-01\",\"2025-04-30";
            DSCondition condition = new DSCondition("isLastPreventionWithinRange", multiParam, "==", "false");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("m.isLastPreventionWithinRange(\"Flu\",\"2024-09-01\",\"2025-04-30\") == false");
        }

        /**
         * Verifies that the generated DRL for lastPreventionIsWithinRange compiles.
         */
        @Test
        @DisplayName("should produce compilable DRL from lastPreventionIsWithinRange condition")
        void shouldProduceCompilableDrl_fromLastPreventionIsWithinRangeCondition() throws Exception {
            String multiParam = "Flu\",\"2024-09-01\",\"2025-04-30";
            DSCondition condition = new DSCondition("isLastPreventionWithinRange", multiParam, "", "");

            String rule = generateRule(Collections.singletonList(condition));
            KieBase kieBase = DroolsHelper.createKieBaseFromDrl(wrapForCompilation(rule));

            assertThat(kieBase).isNotNull();
            assertThat(kieBase.getKiePackages()).hasSize(1);
            assertThat(kieBase.getKiePackages().iterator().next().getRules()).hasSize(1);
        }
    }

    /**
     * Tests for the {@code numberOfAgeInMonthsSinceLastPreventionTypeGiven} condition type
     * ({@code processGenericNumberValues} with method "getAgeInMonthsLastPreventionTypeGiven").
     */
    @Nested
    @DisplayName("numberOfAgeInMonthsSinceLastPreventionTypeGiven condition type")
    class AgeInMonthsSinceLastPreventionCondition {

        /**
         * Verifies that this condition type generates the correct method call.
         */
        @Test
        @DisplayName("should generate getAgeInMonthsLastPreventionTypeGiven condition expression")
        void shouldGenerateGetAgeInMonthsLastPreventionTypeGivenConditionExpression() {
            DSCondition condition = new DSCondition("getAgeInMonthsLastPreventionTypeGiven", "DTaP", ">=", "18");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("m.getAgeInMonthsLastPreventionTypeGiven(\"DTaP\") >= 18");
        }

        /**
         * Verifies that this condition type generates compilable DRL.
         */
        @Test
        @DisplayName("should produce compilable DRL from ageInMonthsSinceLastPrevention condition")
        void shouldProduceCompilableDrl_fromAgeInMonthsSinceLastPreventionCondition() throws Exception {
            DSCondition condition = new DSCondition("getAgeInMonthsLastPreventionTypeGiven", "DTaP", ">=", "18");

            String rule = generateRule(Collections.singletonList(condition));
            KieBase kieBase = DroolsHelper.createKieBaseFromDrl(wrapForCompilation(rule));

            assertThat(kieBase).isNotNull();
            assertThat(kieBase.getKiePackages()).hasSize(1);
            assertThat(kieBase.getKiePackages().iterator().next().getRules()).hasSize(1);
        }
    }

    /**
     * Tests for unrecognized condition types and edge cases.
     *
     * <p>The {@code getConditions()} method logs an error for unrecognized types
     * but does not throw an exception, allowing partial rule processing to continue.
     * These tests verify that the system handles such scenarios gracefully.</p>
     */
    @Nested
    @DisplayName("edge cases and unrecognized types")
    class EdgeCases {

        /**
         * Verifies that an empty condition list produces a valid DRL rule with only
         * the fact binding in the when clause (no condition expressions).
         */
        @Test
        @DisplayName("should generate valid DRL with no conditions when list is empty")
        void shouldGenerateValidDrl_withNoConditions_whenListIsEmpty() {
            String rule = generateRule(Collections.emptyList());

            assertThat(rule).contains("rule \"TEST-0\"");
            assertThat(rule).contains("m : Prevention()");
        }

        /**
         * Verifies that a DRL rule with no conditions compiles successfully.
         * This represents the case where an unrecognized condition type was encountered
         * and no DSConditions were added to the list.
         */
        @Test
        @DisplayName("should produce compilable DRL when condition list is empty")
        void shouldProduceCompilableDrl_whenConditionListIsEmpty() throws Exception {
            String rule = generateRule(Collections.emptyList());
            KieBase kieBase = DroolsHelper.createKieBaseFromDrl(wrapForCompilation(rule));

            assertThat(kieBase).isNotNull();
            assertThat(kieBase.getKiePackages()).hasSize(1);
            assertThat(kieBase.getKiePackages().iterator().next().getRules()).hasSize(1);
        }

        /**
         * Verifies that the DSCondition constructor correctly handles the param-to-method
         * mapping: null param produces a parameterless method call, while a non-null param
         * produces a parameterized method call with the string quoted.
         */
        @Test
        @DisplayName("should produce parameterless method call when param is null")
        void shouldProduceParameterlessMethodCall_whenParamIsNull() {
            DSCondition condition = new DSCondition("getAgeInMonths", null, ">=", "12");

            // DSCondition.getType() should return "getAgeInMonths()" when param is null
            assertThat(condition.getType()).isEqualTo("getAgeInMonths()");
        }

        /**
         * Verifies that DSCondition.getType() produces a parameterized method call
         * when param is a non-empty string.
         */
        @Test
        @DisplayName("should produce parameterized method call when param is provided")
        void shouldProduceParameterizedMethodCall_whenParamIsProvided() {
            DSCondition condition = new DSCondition("getNumberOfPreventionType", "DTaP", "==", "0");

            assertThat(condition.getType()).isEqualTo("getNumberOfPreventionType(\"DTaP\")");
        }

        /**
         * Verifies that DSCondition.getType() treats an empty string param the same as
         * a null param, producing a parameterless method call.
         */
        @Test
        @DisplayName("should produce parameterless method call when param is empty string")
        void shouldProduceParameterlessMethodCall_whenParamIsEmptyString() {
            DSCondition condition = new DSCondition("getAgeInYears", "", "<=", "65");

            assertThat(condition.getType()).isEqualTo("getAgeInYears()");
        }

        /**
         * Verifies that the preventionObjectClassPath constant is set to the correct
         * fully qualified class name.
         */
        @Test
        @DisplayName("should have correct Prevention class path constant")
        void shouldHaveCorrectPreventionClassPathConstant() {
            assertThat(DSPreventionDrools.preventionObjectClassPath)
                    .isEqualTo("io.github.carlos_emr.carlos.prevention.Prevention");
        }
    }

    /**
     * Tests for combining multiple condition types in a single rule, verifying that
     * the DRL generation pipeline handles realistic prevention recommendation
     * configurations with mixed condition types.
     */
    @Nested
    @DisplayName("combined condition types")
    class CombinedConditions {

        /**
         * Verifies that a realistic DTaP prevention recommendation with age range,
         * prevention count, and negated isNextDateSet conditions generates correct DRL.
         */
        @Test
        @DisplayName("should generate DRL with multiple condition types combined")
        void shouldGenerateDrl_withMultipleConditionTypesCombined() {
            // Realistic DTaP prevention rule:
            // Age between 2 and 72 months, 0 prior DTaP-IPV, and next date not set
            List<DSCondition> conditions = Arrays.asList(
                    new DSCondition("getAgeInMonths", "", ">=", "2"),
                    new DSCondition("getAgeInMonths", "", "<=", "72"),
                    new DSCondition("getNumberOfPreventionType", "DTaP-IPV", "==", "0"),
                    new DSCondition("isNextDateSet", "DTaP", "==", "false")
            );

            String rule = generateRule(conditions);

            assertThat(rule).contains("m.getAgeInMonths() >= 2");
            assertThat(rule).contains("m.getAgeInMonths() <= 72");
            assertThat(rule).contains("m.getNumberOfPreventionType(\"DTaP-IPV\") == 0");
            assertThat(rule).contains("m.isNextDateSet(\"DTaP\") == false");
        }

        /**
         * Verifies that a Flu prevention rule combining sex check, age, date range,
         * and prevention count generates correct DRL.
         */
        @Test
        @DisplayName("should generate DRL with sex, age, date range, and count conditions")
        void shouldGenerateDrl_withSexAgeDateRangeAndCountConditions() {
            List<DSCondition> conditions = Arrays.asList(
                    new DSCondition("isFemale", null, "", ""),
                    new DSCondition("getAgeInYears", "", ">=", "65"),
                    new DSCondition("isTodayinDateRange", "2024-09-01\",\"2025-04-30", "", ""),
                    new DSCondition("getNumberOfPreventionType", "Flu", "==", "0")
            );

            String rule = generateRule(conditions);

            assertThat(rule).contains("m.isFemale()");
            assertThat(rule).contains("m.getAgeInYears() >= 65");
            assertThat(rule).contains("m.isTodayinDateRange(\"2024-09-01\",\"2025-04-30\")");
            assertThat(rule).contains("m.getNumberOfPreventionType(\"Flu\") == 0");
        }

        /**
         * Verifies that a complex combined rule with multiple condition types compiles
         * to a valid KieBase.
         */
        @Test
        @DisplayName("should produce compilable DRL from combined conditions")
        void shouldProduceCompilableDrl_fromCombinedConditions() throws Exception {
            List<DSCondition> conditions = Arrays.asList(
                    new DSCondition("getAgeInMonths", "", ">=", "2"),
                    new DSCondition("getAgeInMonths", "", "<=", "72"),
                    new DSCondition("getNumberOfPreventionType", "DTaP-IPV", "==", "0"),
                    new DSCondition("isMale", null, "", ""),
                    new DSCondition("isPreventionNever", "DTaP", "==", "false")
            );

            String rule = generateRule(conditions);
            KieBase kieBase = DroolsHelper.createKieBaseFromDrl(wrapForCompilation(rule));

            assertThat(kieBase).isNotNull();
        }

        /**
         * Verifies that multiple rules with different condition types can be compiled
         * together into a single KieBase via {@link RuleBaseCreator#getRuleBase}.
         */
        @Test
        @DisplayName("should compile multiple rules into single KieBase")
        void shouldCompileMultipleRules_intoSingleKieBase() throws Exception {
            // Rule 1: DTaP for infants
            List<DSCondition> rule1Conditions = Arrays.asList(
                    new DSCondition("getAgeInMonths", "", ">=", "2"),
                    new DSCondition("getAgeInMonths", "", "<=", "72"),
                    new DSCondition("getNumberOfPreventionType", "DTaP", "==", "0")
            );
            String rule1 = creator.getRule("DTaP-0", PREVENTION_CLASS, rule1Conditions,
                    "m.addWarning(\"DTaP\",\"Needs first DTaP dose\");");

            // Rule 2: Flu for elderly
            List<DSCondition> rule2Conditions = Arrays.asList(
                    new DSCondition("getAgeInYears", "", ">=", "65"),
                    new DSCondition("getNumberOfPreventionType", "Flu", "==", "0")
            );
            String rule2 = creator.getRule("Flu-1", PREVENTION_CLASS, rule2Conditions,
                    "m.addWarning(\"Flu\",\"Annual flu shot recommended\");");

            KieBase kieBase = creator.getRuleBase("preventions", Arrays.asList(rule1, rule2));

            assertThat(kieBase).isNotNull();
        }
    }

    /**
     * Tests verifying the structural integrity of the generated DRL rule text,
     * ensuring all required DRL elements are present.
     */
    @Nested
    @DisplayName("DRL structure")
    class DrlStructure {

        /**
         * Verifies that the generated DRL contains the import statement for the
         * Prevention fact class.
         */
        @Test
        @DisplayName("should include import statement for Prevention class")
        void shouldIncludeImportStatement_forPreventionClass() {
            DSCondition condition = new DSCondition("getAgeInMonths", "", ">=", "2");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("import " + PREVENTION_CLASS);
        }

        /**
         * Verifies that the generated DRL binds the Prevention fact to variable "m"
         * in the when clause.
         */
        @Test
        @DisplayName("should bind Prevention fact to variable m")
        void shouldBindPreventionFact_toVariableM() {
            DSCondition condition = new DSCondition("getAgeInMonths", "", ">=", "2");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("m : Prevention()");
        }

        /**
         * Verifies that the generated DRL contains all structural elements:
         * rule declaration, when, then, and end.
         */
        @Test
        @DisplayName("should contain all DRL structural elements")
        void shouldContainAllDrlStructuralElements() {
            DSCondition condition = new DSCondition("isMale", null, "", "");

            String rule = generateRule(Collections.singletonList(condition));

            assertThat(rule).contains("rule \"TEST-0\"");
            assertThat(rule).contains("when");
            assertThat(rule).contains("then");
            assertThat(rule).contains("end");
        }

        /**
         * Verifies that the consequence text is included in the then block.
         */
        @Test
        @DisplayName("should include consequence in then block")
        void shouldIncludeConsequence_inThenBlock() {
            DSCondition condition = new DSCondition("isMale", null, "", "");
            String consequence = "m.addWarning(\"DTaP\",\"test warning\");";

            String rule = creator.getRule("TEST-0", PREVENTION_CLASS,
                    Collections.singletonList(condition), consequence);

            assertThat(rule).contains(consequence);
        }
    }
}
