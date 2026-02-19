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
package io.github.carlos_emr.carlos.report.ClinicalReports;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

import io.github.carlos_emr.carlos.drools.DroolsCompilationException;
import io.github.carlos_emr.carlos.drools.DroolsHelper;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.MeasurementDSHelper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the KIE session lifecycle pattern used by all five DroolsNumerator variants.
 *
 * <p>The DroolsNumerator family (1-5) all follow the same Drools session lifecycle:
 * compile a KieBase, create a KieSession, insert a {@link MeasurementDSHelper} fact,
 * fire all rules, dispose the session, and check {@code dshelper.isInRange()}. These
 * tests verify that lifecycle works correctly with the Drools 7.74.1 KIE API without
 * requiring database access or {@code LoggedInInfo}.</p>
 *
 * <p>The tests use {@link MeasurementDSHelper}'s no-arg constructor (which avoids
 * the database-dependent {@code DemographicData} lookups) and minimal inline DRL
 * rules that match the fact and set {@code inRange} directly.</p>
 *
 * @see DroolsNumerator
 * @see DroolsNumerator2
 * @see DroolsNumerator3
 * @see DroolsNumerator4
 * @see DroolsNumerator5
 * @see MeasurementDSHelper
 * @since 2026-02-17
 */
@Tag("unit")
@Tag("drools")
@DisplayName("DroolsNumerator KIE Session Lifecycle")
class DroolsNumeratorLifecycleTest {

    /**
     * DRL that unconditionally sets {@code inRange = true} on any
     * {@link MeasurementDSHelper} fact. Simulates the consequence pattern
     * used by DroolsNumerator 1, 2, 4, and 5 when a measurement matches.
     */
    private static final String DRL_SET_IN_RANGE =
            "package test;\n" +
            "import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.MeasurementDSHelper;\n" +
            "rule \"set-in-range\"\n" +
            "    when\n" +
            "        m : MeasurementDSHelper()\n" +
            "    then\n" +
            "        m.setInRange(true);\n" +
            "end\n";

    /**
     * DRL with an impossible condition so no rules fire. Used to verify
     * that {@code inRange} stays {@code false} when no rules match.
     */
    private static final String DRL_NO_MATCH =
            "package test;\n" +
            "import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.MeasurementDSHelper;\n" +
            "rule \"never-matches\"\n" +
            "    when\n" +
            "        m : MeasurementDSHelper(inRange == true)\n" +
            "    then\n" +
            "        m.setInRange(false);\n" +
            "end\n";

    /**
     * Tests for the core KIE session lifecycle: create session, insert
     * MeasurementDSHelper fact, fire rules, dispose, check result. This
     * is the pattern shared by DroolsNumerator 1, 2, 4, and 5.
     */
    @Nested
    @DisplayName("KieSession lifecycle with MeasurementDSHelper")
    class KieSessionLifecycle {

        /**
         * Verifies the complete lifecycle: KieBase compiles, KieSession creates,
         * MeasurementDSHelper inserts, rules fire setting inRange=true, session disposes.
         */
        @Test
        @DisplayName("should set inRange true when matching rule fires")
        void shouldSetInRangeTrue_whenMatchingRuleFires() throws DroolsCompilationException {
            KieBase kieBase = DroolsHelper.createKieBaseFromDrl(DRL_SET_IN_RANGE);
            MeasurementDSHelper helper = new MeasurementDSHelper();

            KieSession session = kieBase.newKieSession();
            try {
                session.insert(helper);
                session.fireAllRules();
            } finally {
                session.dispose();
            }

            assertThat(helper.isInRange()).isTrue();
        }

        /**
         * Verifies that when no rules match, the MeasurementDSHelper's inRange
         * field remains at its default value (false).
         */
        @Test
        @DisplayName("should leave inRange false when no rules match")
        void shouldLeaveInRangeFalse_whenNoRulesMatch() throws DroolsCompilationException {
            KieBase kieBase = DroolsHelper.createKieBaseFromDrl(DRL_NO_MATCH);
            MeasurementDSHelper helper = new MeasurementDSHelper();

            KieSession session = kieBase.newKieSession();
            try {
                session.insert(helper);
                session.fireAllRules();
            } finally {
                session.dispose();
            }

            assertThat(helper.isInRange()).isFalse();
        }

        /**
         * Verifies that the KieSession is properly disposed even after successful
         * rule execution, and that a new session can be created from the same KieBase.
         */
        @Test
        @DisplayName("should allow new session after disposing previous one")
        void shouldAllowNewSession_afterDisposingPreviousOne() throws DroolsCompilationException {
            KieBase kieBase = DroolsHelper.createKieBaseFromDrl(DRL_SET_IN_RANGE);

            // First session
            MeasurementDSHelper helper1 = new MeasurementDSHelper();
            KieSession session1 = kieBase.newKieSession();
            try {
                session1.insert(helper1);
                session1.fireAllRules();
            } finally {
                session1.dispose();
            }

            // Second session from the same KieBase
            MeasurementDSHelper helper2 = new MeasurementDSHelper();
            KieSession session2 = kieBase.newKieSession();
            try {
                session2.insert(helper2);
                session2.fireAllRules();
            } finally {
                session2.dispose();
            }

            assertThat(helper1.isInRange()).isTrue();
            assertThat(helper2.isInRange()).isTrue();
        }
    }

    /**
     * Tests for the production DRL files that are loaded by
     * {@link DroolsHelper#loadMeasurementRuleBase(String, Class)} and executed
     * by the DroolsNumerator family via KieSession.
     */
    @Nested
    @DisplayName("Production DRL execution with MeasurementDSHelper")
    class ProductionDrlExecution {

        /**
         * Loads a production decision support DRL (diab-A1C), inserts a
         * default MeasurementDSHelper, fires rules, and verifies no
         * runtime exception occurs. With no measurement data set,
         * inRange should remain false.
         */
        @Test
        @DisplayName("should execute diab-A1C DRL without error")
        void shouldExecuteDiabA1cDrl_withoutError() {
            KieBase kieBase = DroolsHelper.loadMeasurementRuleBase("diab-A1C.drl", DroolsHelper.class);
            assertThat(kieBase).as("diab-A1C.drl should compile from classpath").isNotNull();

            MeasurementDSHelper helper = new MeasurementDSHelper();
            KieSession session = kieBase.newKieSession();
            try {
                session.insert(helper);
                session.fireAllRules();
            } finally {
                session.dispose();
            }

            // No measurement data → rules should not set inRange
            assertThat(helper.isInRange()).isFalse();
        }

        /**
         * Loads the INR decision support DRL, inserts a default
         * MeasurementDSHelper, and fires rules. Before the null guard fix
         * this threw NPE from {@code setIndicationColor()} on null mdb.
         */
        @Test
        @DisplayName("should execute INR DRL without NPE on null mdb")
        void shouldExecuteInrDrl_withoutNpeOnNullMdb() {
            KieBase kieBase = DroolsHelper.loadMeasurementRuleBase("INR.drl", DroolsHelper.class);
            assertThat(kieBase).as("INR.drl should compile from classpath").isNotNull();

            MeasurementDSHelper helper = new MeasurementDSHelper();
            KieSession session = kieBase.newKieSession();
            try {
                session.insert(helper);
                session.fireAllRules();
            } finally {
                session.dispose();
            }
        }

        /**
         * Loads the BMI decision support DRL, inserts a default
         * MeasurementDSHelper, and fires rules. The DRL has a rule "BMI 3"
         * that matches unconditionally and calls {@code setIndicationColor()}.
         * Before the null guard fix in {@code MeasurementDSHelper}, this
         * threw NPE because {@code mdb} was null. With the fix, the call
         * is safely skipped.
         */
        @Test
        @DisplayName("should execute diab-BMI DRL without NPE on null mdb")
        void shouldExecuteDiabBmiDrl_withoutNpeOnNullMdb() {
            KieBase kieBase = DroolsHelper.loadMeasurementRuleBase("diab-BMI.drl", DroolsHelper.class);
            assertThat(kieBase).as("diab-BMI.drl should compile from classpath").isNotNull();

            MeasurementDSHelper helper = new MeasurementDSHelper();
            KieSession session = kieBase.newKieSession();
            try {
                session.insert(helper);
                session.fireAllRules();
            } finally {
                session.dispose();
            }

            // No measurement data → inRange stays false, but no NPE
            assertThat(helper.isInRange()).isFalse();
        }
    }

    /**
     * Tests for the DroolsNumerator class accessors that are pure Java logic
     * (no database or Spring dependency).
     */
    @Nested
    @DisplayName("DroolsNumerator pure Java methods")
    class PureJavaMethods {

        /**
         * Verifies {@link DroolsNumerator#parseOutputFields(String)} splits
         * comma-separated fields into an array.
         */
        @Test
        @DisplayName("should parse comma-separated output fields")
        void shouldParseCommaSeparatedOutputFields() {
            DroolsNumerator num = new DroolsNumerator();
            num.parseOutputFields("systolic,diastolic,pulse");

            assertThat(num.getOutputFields()).containsExactly("systolic", "diastolic", "pulse");
        }

        /**
         * Verifies that a single field (no comma) produces a one-element array.
         */
        @Test
        @DisplayName("should parse single output field")
        void shouldParseSingleOutputField() {
            DroolsNumerator num = new DroolsNumerator();
            num.parseOutputFields("A1C");

            assertThat(num.getOutputFields()).containsExactly("A1C");
        }

        /**
         * Verifies null input leaves outputfields as null.
         */
        @Test
        @DisplayName("should leave outputfields null when input is null")
        void shouldLeaveOutputfieldsNull_whenInputIsNull() {
            DroolsNumerator num = new DroolsNumerator();
            num.parseOutputFields(null);

            assertThat(num.getOutputFields()).isNull();
        }

        /**
         * Verifies {@link DroolsNumerator#parseReplaceValues(String)} splits
         * comma-separated keys into an array.
         */
        @Test
        @DisplayName("should parse comma-separated replace keys")
        void shouldParseCommaSeparatedReplaceKeys() {
            DroolsNumerator2 num = new DroolsNumerator2();
            num.parseReplaceValues("measurements,value");

            assertThat(num.getReplaceableKeys()).containsExactly("measurements", "value");
        }

        /**
         * Verifies {@link DroolsNumerator#hasReplaceableValues()} returns false
         * before parsing and true after.
         */
        @Test
        @DisplayName("should report hasReplaceableValues correctly")
        void shouldReportHasReplaceableValues_correctly() {
            DroolsNumerator num = new DroolsNumerator();

            assertThat(num.hasReplaceableValues()).isFalse();

            num.parseReplaceValues("measurements,value");

            assertThat(num.hasReplaceableValues()).isTrue();
        }

        /**
         * Verifies that the file-based DroolsNumerator can load a production
         * DRL via its {@link DroolsNumerator#loadMeasurementRuleBase(String)} method.
         */
        @Test
        @DisplayName("should load measurement rule base from DroolsNumerator")
        void shouldLoadMeasurementRuleBase_fromDroolsNumerator() {
            DroolsNumerator num = new DroolsNumerator();
            KieBase kieBase = num.loadMeasurementRuleBase("diab-A1C.drl");

            assertThat(kieBase).isNotNull();
        }
    }
}
