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
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.util;

import java.util.ArrayList;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RuleBaseCreator}, which programmatically generates DRL
 * rule text from {@link DSCondition} objects and compiles them into cached
 * {@link KieBase} instances.
 *
 * <p>{@code RuleBaseCreator} is the bridge between the flowsheet configuration
 * layer and the Drools engine. When a clinician configures measurement thresholds
 * (e.g., "flag blood pressure HIGH when systolic >= 140"), those thresholds are
 * stored as {@code DSCondition} objects. {@code RuleBaseCreator} transforms them
 * into DRL rules that the Drools engine can execute against patient data.</p>
 *
 * <h3>Key behaviors tested</h3>
 * <ul>
 *   <li>{@link RuleBaseCreator#getRule(String, String, List, String)} &mdash; generates
 *       a single DRL rule string from conditions and a consequence</li>
 *   <li>{@link RuleBaseCreator#getRuleBase(String, List)} &mdash; compiles multiple
 *       DRL rule strings into a single {@link KieBase}, with SHA-256 based caching
 *       via {@link RuleBaseFactory}</li>
 * </ul>
 *
 * <p>Tests are organized into two nested classes corresponding to these methods.
 * Each test starts with a clean cache to ensure isolation.</p>
 *
 * @see RuleBaseCreator
 * @see DSCondition
 * @see RuleBaseFactory
 * @since 2026-02-17
 */
@Tag("unit")
@Tag("drools")
@DisplayName("RuleBaseCreator")
class RuleBaseCreatorUnitTest {

    /**
     * Fully-qualified class name of the fact type used in flowsheet DRL rules.
     * {@link MeasurementDSHelper} is the runtime object that Drools pattern-matches
     * against when evaluating clinical measurement conditions.
     */
    private static final String FACT_CLASS = "io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.MeasurementDSHelper";

    private RuleBaseCreator creator;

    /**
     * Flush the KieBase cache and create a fresh {@code RuleBaseCreator} instance
     * before each test. The flush ensures that cached entries from previous tests
     * do not interfere with cache-hit/miss assertions.
     */
    @BeforeEach
    void setUp() {
        RuleBaseFactory.flushAllCached();
        creator = new RuleBaseCreator();
    }

    /**
     * Tests for {@link RuleBaseCreator#getRule(String, String, List, String)},
     * which generates a single DRL rule string from structured condition data.
     *
     * <p>The generated DRL includes an import statement for the fact class,
     * a rule declaration with a pattern-matching {@code when} clause built from
     * {@link DSCondition} objects, and a {@code then} clause containing the
     * provided consequence code.</p>
     */
    @Nested
    @DisplayName("getRule")
    class GetRule {

        /**
         * Verifies that a single condition produces a complete DRL rule with all
         * required structural elements: import, rule declaration, when/then/end blocks.
         */
        @Test
        @DisplayName("should generate DRL with import and rule from single condition")
        void shouldGenerateDrlWithImportAndRule_fromSingleCondition() {
            // Create a condition: getDataAsDouble() >= 140.0 (systolic BP threshold)
            DSCondition cond = new DSCondition("getDataAsDouble", "", ">=", "140.0");
            List<DSCondition> conditions = Collections.singletonList(cond);

            String rule = creator.getRule("BP_HIGH", FACT_CLASS, conditions, "m.setIndicationColor(\"HIGH\");");

            // Verify all structural DRL elements are present
            assertThat(rule).contains("import " + FACT_CLASS);
            assertThat(rule).contains("rule \"BP_HIGH\"");
            assertThat(rule).contains("when");
            assertThat(rule).contains("then");
            assertThat(rule).contains("end");
        }

        /**
         * Verifies that multiple conditions are all included in the generated DRL.
         * Each {@link DSCondition} should produce a separate constraint expression
         * in the {@code when} clause.
         */
        @Test
        @DisplayName("should include all conditions when multiple provided")
        void shouldIncludeAllConditions_whenMultipleProvided() {
            // Two conditions: BP >= 140 AND patient is male
            List<DSCondition> conditions = Arrays.asList(
                    new DSCondition("getDataAsDouble", "", ">=", "140.0"),
                    new DSCondition("isMale", "", "==", "true")
            );

            String rule = creator.getRule("BP_MALE_HIGH", FACT_CLASS, conditions, "m.setIndicationColor(\"HIGH\");");

            // Both condition expressions must appear in the generated DRL
            assertThat(rule).contains("m.getDataAsDouble() >= 140.0");
            assertThat(rule).contains("m.isMale() == true");
        }

        /**
         * Verifies that the fact class is referenced by its simple name (not FQCN)
         * in the pattern-matching clause. The FQCN is used only in the import
         * statement, while the {@code when} clause uses the simple name.
         */
        @Test
        @DisplayName("should extract simple class name from fully qualified name")
        void shouldExtractSimpleClassName_fromFullyQualifiedName() {
            DSCondition cond = new DSCondition("getDataAsDouble", "", ">=", "7.0");

            String rule = creator.getRule("TEST", FACT_CLASS, Collections.singletonList(cond), "");

            // The pattern match should use the simple class name "MeasurementDSHelper"
            assertThat(rule).contains("m : MeasurementDSHelper()");
        }

        /**
         * Verifies that the consequence string (the "then" block code) is included
         * verbatim in the generated DRL. The consequence is Java code that modifies
         * the matched fact (e.g., setting an indication color).
         */
        @Test
        @DisplayName("should include consequence in then block")
        void shouldIncludeConsequence_inThenBlock() {
            DSCondition cond = new DSCondition("getDataAsDouble", "", ">=", "7.0");
            String consequence = "m.setIndicationColor(\"HIGH\");";

            String rule = creator.getRule("TEST", FACT_CLASS, Collections.singletonList(cond), consequence);

            // The consequence must appear between "then" and "end"
            assertThat(rule).contains("then");
            assertThat(rule).contains(consequence);
        }

        /**
         * End-to-end compilation test: generates a DRL rule from typical conditions
         * and verifies that it compiles successfully with the Drools KIE compiler.
         *
         * <p>This catches subtle issues in DRL generation such as missing semicolons,
         * incorrect quoting, or invalid method call syntax that would only manifest
         * at compilation time.</p>
         */
        @Test
        @DisplayName("should produce compilable DRL from typical conditions")
        void shouldProduceCompilableDrl_fromTypicalConditions() throws Exception {
            List<DSCondition> conditions = Arrays.asList(
                    new DSCondition("getDataAsDouble", "", ">=", "140.0"),
                    new DSCondition("isMale", "", "==", "true")
            );
            String consequence = "m.setIndicationColor(\"HIGH\");";

            String rule = creator.getRule("COMPILE_TEST", FACT_CLASS, conditions, consequence);

            // Wrap with a package declaration to create a complete compilable DRL
            String fullDrl = "package test;\n" + rule;
            KieBase kieBase = DroolsHelper.createKieBaseFromDrl(fullDrl);
            assertThat(kieBase).isNotNull();
            assertThat(kieBase.getKiePackages()).hasSize(1);
            assertThat(kieBase.getKiePackages().iterator().next().getRules()).hasSize(1);
        }
    }

    /**
     * Tests for {@link RuleBaseCreator#getRuleBase(String, List)}, which compiles
     * one or more DRL rule strings into a single {@link KieBase} and caches the
     * result in {@link RuleBaseFactory}.
     *
     * <p>The caching key is a SHA-256 hash of the concatenated DRL content. This
     * means identical DRL text always hits the cache, while any change (even to
     * a single threshold value) produces a cache miss and re-compilation.</p>
     */
    @Nested
    @DisplayName("getRuleBase")
    class GetRuleBase {

        /**
         * Verifies that calling {@code getRuleBase()} twice with the same DRL content
         * returns the same cached {@link KieBase} instance (identity check).
         * The first call compiles and caches; the second call returns the cached entry.
         */
        @Test
        @DisplayName("should compile and cache KieBase from single rule")
        void shouldCompileAndCacheKieBase_fromSingleRule() throws Exception {
            DSCondition cond = new DSCondition("getDataAsDouble", "", ">=", "7.0");
            String rule = creator.getRule("R1", FACT_CLASS, Collections.singletonList(cond), "m.setIndicationColor(\"HIGH\");");

            // First call: compiles and caches
            KieBase first = creator.getRuleBase("testPkg", Collections.singletonList(rule));
            // Second call: should return the cached instance
            KieBase second = creator.getRuleBase("testPkg", Collections.singletonList(rule));

            assertThat(first).isNotNull();
            assertThat(first.getKiePackages()).isNotEmpty();
            // Same DRL content = same SHA-256 hash = same cached KieBase instance
            assertThat(second).isSameAs(first);
        }

        /**
         * Verifies that when multiple rules import the same fact class, the generated
         * DRL contains only one import statement (deduplication). Duplicate imports
         * would cause a Drools compilation error.
         */
        @Test
        @DisplayName("should deduplicate imports when multiple rules have same import")
        void shouldDeduplicateImports_whenMultipleRulesHaveSameImport() throws Exception {
            // Two rules that both import MeasurementDSHelper
            DSCondition cond1 = new DSCondition("getDataAsDouble", "", ">=", "7.0");
            DSCondition cond2 = new DSCondition("getDataAsDouble", "", "<", "4.0");
            String rule1 = creator.getRule("R1", FACT_CLASS, Collections.singletonList(cond1), "m.setIndicationColor(\"HIGH\");");
            String rule2 = creator.getRule("R2", FACT_CLASS, Collections.singletonList(cond2), "m.setIndicationColor(\"LOW\");");

            // Should compile without "duplicate import" errors
            KieBase kieBase = creator.getRuleBase("testPkg", Arrays.asList(rule1, rule2));

            assertThat(kieBase).isNotNull();
            // Both rules should be compiled into the KieBase
            int totalRules = kieBase.getKiePackages().stream().mapToInt(p -> p.getRules().size()).sum();
            assertThat(totalRules).isEqualTo(2);
        }

        /**
         * Verifies that changing the DRL content (e.g., modifying a threshold value)
         * produces a different SHA-256 cache key, causing a re-compilation rather
         * than returning the stale cached version.
         */
        @Test
        @DisplayName("should return different KieBase when rules change")
        void shouldReturnDifferentKieBase_whenRulesChange() throws Exception {
            // Rule A: threshold >= 7.0
            DSCondition cond1 = new DSCondition("getDataAsDouble", "", ">=", "7.0");
            String ruleA = creator.getRule("R1", FACT_CLASS, Collections.singletonList(cond1), "m.setIndicationColor(\"HIGH\");");

            // Rule B: threshold < 4.0 (different DRL content)
            DSCondition cond2 = new DSCondition("getDataAsDouble", "", "<", "4.0");
            String ruleB = creator.getRule("R1", FACT_CLASS, Collections.singletonList(cond2), "m.setIndicationColor(\"LOW\");");

            KieBase baseA = creator.getRuleBase("pkg", Collections.singletonList(ruleA));
            KieBase baseB = creator.getRuleBase("pkg", Collections.singletonList(ruleB));

            assertThat(baseA).isNotNull();
            assertThat(baseA.getKiePackages()).isNotEmpty();
            assertThat(baseB).isNotNull();
            assertThat(baseB.getKiePackages()).isNotEmpty();
            // Different DRL content should produce different cached entries
            assertThat(baseB).isNotSameAs(baseA);
        }

        /**
         * Verifies that the same DRL content always returns the same cached
         * {@link KieBase} instance, confirming the SHA-256 cache key is deterministic.
         */
        @Test
        @DisplayName("should return cached KieBase when same DRL provided")
        void shouldReturnCachedKieBase_whenSameDrlProvided() throws Exception {
            DSCondition cond = new DSCondition("getDataAsDouble", "", ">=", "7.0");
            String rule = creator.getRule("R1", FACT_CLASS, Collections.singletonList(cond), "m.setIndicationColor(\"HIGH\");");
            List<String> rules = Collections.singletonList(rule);

            KieBase first = creator.getRuleBase("pkg", rules);
            KieBase second = creator.getRuleBase("pkg", rules);

            // Identical DRL text = identical SHA-256 hash = cache hit
            assertThat(second).isSameAs(first);
        }

        /**
         * Verifies that malformed DRL rule text propagates the compilation error
         * as an exception rather than silently returning null or a broken KieBase.
         */
        @Test
        @DisplayName("should throw exception when DRL rules contain syntax error")
        void shouldThrowException_whenDrlRulesContainSyntaxError() {
            // Deliberately broken DRL with invalid syntax in the when clause
            String brokenRule = "rule \"BROKEN\"\n    when\n        INVALID SYNTAX\n    then\nend";

            assertThatThrownBy(() -> creator.getRuleBase("pkg", Collections.singletonList(brokenRule)))
                    .isInstanceOf(Exception.class);
        }
    }
}
