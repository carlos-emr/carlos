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
package io.github.carlos_emr.carlos.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

import io.github.carlos_emr.carlos.drools.DroolsCompilationException;
import io.github.carlos_emr.carlos.drools.DroolsHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link WorkFlowDS}, which wraps a Drools {@link KieBase} to
 * execute workflow automation rules against {@link WorkFlowInfo} facts.
 *
 * <p>{@code WorkFlowDS} is the runtime engine for the CARLOS EMR workflow subsystem.
 * It creates a short-lived {@link KieSession} for each workflow evaluation: the session
 * is created, a {@code WorkFlowInfo} fact is inserted, all matching rules fire, and the
 * session is disposed. The modified {@code WorkFlowInfo} is returned to the caller with
 * any state changes applied by the rules (e.g., colour indicators, state transitions).</p>
 *
 * <h3>Production usage</h3>
 * <p>In production, {@code WorkFlowDS} is created by {@code WorkFlowDSFactory}, which
 * loads the DRL file for a specific workflow type (e.g., "Rh_workflow") and compiles it
 * into a {@code KieBase}. The {@code Rh_workflow.drl} file implements prenatal Rh factor
 * incompatibility follow-up rules.</p>
 *
 * <h3>Test approach</h3>
 * <p>Tests use minimal inline DRL strings to create controlled rule scenarios without
 * depending on production DRL files. This allows testing specific behaviors (rule fires,
 * rule doesn't fire, exception handling) in isolation.</p>
 *
 * @see WorkFlowDS
 * @see WorkFlowInfo
 * @see io.github.carlos_emr.carlos.workflow.WorkFlowDSFactory
 * @since 2026-02-17
 */
@Tag("unit")
@Tag("drools")
@DisplayName("WorkFlowDS")
class WorkFlowDSUnitTest {

    /**
     * DRL that matches a {@link WorkFlowInfo} with {@code currentState == "pending"}
     * and sets its colour to "RED". Used to verify that rules can modify fact properties
     * when conditions are met.
     */
    private static final String WORKFLOW_DRL_MATCH =
            "package test;\n" +
            "import io.github.carlos_emr.carlos.workflow.WorkFlowInfo;\n" +
            "rule \"set-colour\"\n" +
            "    when\n" +
            "        w : WorkFlowInfo(currentState == \"pending\")\n" +
            "    then\n" +
            "        w.setColour(\"RED\");\n" +
            "end\n";

    /**
     * DRL that matches a {@link WorkFlowInfo} with an impossible state value.
     * Used to verify that when no rules match, the fact remains unmodified.
     */
    private static final String WORKFLOW_DRL_NO_MATCH =
            "package test;\n" +
            "import io.github.carlos_emr.carlos.workflow.WorkFlowInfo;\n" +
            "rule \"never-matches\"\n" +
            "    when\n" +
            "        w : WorkFlowInfo(currentState == \"IMPOSSIBLE_STATE_XYZ\")\n" +
            "    then\n" +
            "        w.setColour(\"BLUE\");\n" +
            "end\n";

    /**
     * Compiles a DRL string into a {@link KieBase} for test use.
     *
     * @param drl the DRL rule text to compile
     * @return the compiled {@link KieBase}
     * @throws DroolsCompilationException if the DRL has syntax errors
     */
    private KieBase compileTestDrl(String drl) throws DroolsCompilationException {
        return DroolsHelper.createKieBaseFromDrl(drl);
    }

    /**
     * Verifies that passing {@code null} to the constructor throws
     * {@link IllegalArgumentException}, enforcing the non-null invariant
     * on the {@code kieBase} field.
     */
    @Test
    @DisplayName("should throw IllegalArgumentException when KieBase is null")
    void shouldThrowIllegalArgumentException_whenKieBaseIsNull() {
        assertThatThrownBy(() -> new WorkFlowDS(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("KieBase must not be null");
    }

    /**
     * Verifies that {@link WorkFlowDS#getMessages(WorkFlowInfo)} returns the
     * exact same {@link WorkFlowInfo} instance that was passed in (identity check).
     *
     * <p>The method modifies the fact in-place via the Drools session, then returns
     * it. Callers depend on getting the same object back (not a copy).</p>
     */
    @Test
    @DisplayName("should return same fact after firing rules")
    void shouldReturnSameFact_afterFiringRules() throws Exception {
        KieBase kieBase = compileTestDrl(WORKFLOW_DRL_MATCH);
        WorkFlowDS ds = new WorkFlowDS(kieBase);

        // Set the state that the rule matches against
        WorkFlowInfo info = new WorkFlowInfo();
        info.setCurrentState("pending");

        WorkFlowInfo result = ds.getMessages(info);

        // Must be the exact same object, not a clone or copy
        assertThat(result).isSameAs(info);
    }

    /**
     * Verifies that when a rule's condition matches the inserted fact, its
     * consequence executes and modifies the fact's properties.
     *
     * <p>The test DRL sets {@code colour} to "RED" when {@code currentState}
     * is "pending". After firing rules, the colour should be updated.</p>
     */
    @Test
    @DisplayName("should modify fact when rule fires")
    void shouldModifyFact_whenRuleFires() throws Exception {
        KieBase kieBase = compileTestDrl(WORKFLOW_DRL_MATCH);
        WorkFlowDS ds = new WorkFlowDS(kieBase);

        // The rule matches currentState == "pending" and sets colour to "RED"
        WorkFlowInfo info = new WorkFlowInfo();
        info.setCurrentState("pending");

        ds.getMessages(info);

        // Verify the rule's consequence executed
        assertThat(info.getColour()).isEqualTo("RED");
    }

    /**
     * Verifies that when no rules match the inserted fact, the fact's properties
     * remain unchanged. This is the "no-op" scenario that occurs when a patient's
     * workflow data doesn't trigger any automation rules.
     */
    @Test
    @DisplayName("should not modify fact when no rules match")
    void shouldNotModifyFact_whenNoRulesMatch() throws Exception {
        KieBase kieBase = compileTestDrl(WORKFLOW_DRL_NO_MATCH);
        WorkFlowDS ds = new WorkFlowDS(kieBase);

        // Set a state that does NOT match the rule's condition
        WorkFlowInfo info = new WorkFlowInfo();
        info.setCurrentState("pending");
        info.setColour(null);

        ds.getMessages(info);

        // Colour should remain null because no rule matched
        assertThat(info.getColour()).isNull();
    }

    /**
     * Verifies that the {@link KieSession} is properly disposed even when a rule's
     * consequence throws a {@link RuntimeException}.
     *
     * <p>Session disposal is critical to prevent resource leaks in the Drools engine.
     * The production code uses a try-finally block to ensure disposal. This test
     * verifies that pattern by confirming that after an exception, we can still
     * create a new session from the same {@code KieBase} without issues.</p>
     */
    @Test
    @DisplayName("should dispose session even when rule throws")
    void shouldDisposeSession_evenWhenRuleThrows() throws DroolsCompilationException {
        // DRL with a rule that deliberately throws a RuntimeException
        String throwingDrl =
                "package test;\n" +
                "import io.github.carlos_emr.carlos.workflow.WorkFlowInfo;\n" +
                "rule \"throw-rule\"\n" +
                "    when\n" +
                "        w : WorkFlowInfo()\n" +
                "    then\n" +
                "        throw new RuntimeException(\"rule error\");\n" +
                "end\n";

        KieBase kieBase = compileTestDrl(throwingDrl);
        WorkFlowDS ds = new WorkFlowDS(kieBase);
        WorkFlowInfo info = new WorkFlowInfo();

        // The rule should fire and throw; verify the original RuntimeException
        // propagates directly without wrapping in a generic Exception
        assertThatThrownBy(() -> ds.getMessages(info))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("rule error");

        // If the session was not disposed properly, creating a new session might
        // fail or leak resources. Verify we can still create and dispose cleanly.
        KieSession newSession = kieBase.newKieSession();
        newSession.dispose();
    }

    /**
     * Verifies that the constructor accepts a valid {@link KieBase} and produces
     * a functional instance that can execute rules.
     */
    @Test
    @DisplayName("should accept valid KieBase from constructor")
    void shouldAcceptValidKieBase_fromConstructor() throws Exception {
        KieBase kieBase = compileTestDrl(WORKFLOW_DRL_MATCH);

        WorkFlowDS ds = new WorkFlowDS(kieBase);

        // Verify the instance is functional by executing rules
        WorkFlowInfo info = new WorkFlowInfo();
        info.setCurrentState("pending");
        ds.getMessages(info);
        assertThat(info.getColour()).isEqualTo("RED");
    }

    /**
     * Verifies that passing {@code null} as the {@link WorkFlowInfo} fact does not
     * cause an exception. Drools 7.x accepts null facts in {@code insert()}, and
     * no rules will match a null object. The method returns {@code null} since the
     * input was null.
     */
    @Test
    @DisplayName("should return null when null WorkFlowInfo passed")
    void shouldReturnNull_whenNullWorkFlowInfoPassed() throws Exception {
        KieBase kieBase = compileTestDrl(WORKFLOW_DRL_NO_MATCH);
        WorkFlowDS ds = new WorkFlowDS(kieBase);

        WorkFlowInfo result = ds.getMessages(null);

        assertThat(result).isNull();
    }

    /**
     * Tests for {@link WorkFlowDSFactory}, verifying the fail-fast behavior
     * when a DRL file cannot be loaded or compiled.
     *
     * <p>The factory was fixed to throw {@link IllegalStateException} immediately
     * when the rule base cannot be loaded, rather than silently passing a null
     * {@code KieBase} to the {@link WorkFlowDS} constructor. The previous code
     * had a self-suppressing exception bug: {@code loadRuleBase()} threw
     * {@code IllegalStateException} for missing classpath resources, then
     * immediately caught it with {@code catch (Exception e)}, returning null.</p>
     */
    @Nested
    @DisplayName("WorkFlowDSFactory")
    class WorkFlowDSFactoryTest {

        /**
         * Verifies that requesting a non-existent DRL file throws
         * {@link IllegalStateException} with a descriptive message, rather than
         * silently returning a broken {@link WorkFlowDS} with a null KieBase.
         */
        @Test
        @DisplayName("should throw IllegalStateException for non-existent DRL")
        void shouldThrowIllegalStateException_forNonExistentDrl() {
            assertThatThrownBy(() -> WorkFlowDSFactory.getWorkFlowDS("nonexistent_workflow.drl"))
                    .isInstanceOf(IllegalStateException.class);
        }

        /**
         * Verifies that {@code loadRuleBase()} throws {@link IllegalStateException}
         * when the DRL file cannot be found on the classpath, since the catch clause
         * was narrowed to {@code IOException | DroolsCompilationException} and no
         * longer suppresses the {@code IllegalStateException}.
         */
        @Test
        @DisplayName("should throw IllegalStateException from loadRuleBase for non-existent DRL")
        void shouldThrowIllegalStateException_fromLoadRuleBaseForNonExistentDrl() {
            assertThatThrownBy(() -> WorkFlowDSFactory.loadRuleBase("nonexistent_workflow.drl"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not found on classpath");
        }

        /**
         * Verifies that a valid production DRL file (Rh_workflow.drl) can be
         * loaded through the factory and produces a functional {@link WorkFlowDS}.
         */
        @Test
        @DisplayName("should create functional WorkFlowDS from Rh_workflow.drl")
        void shouldCreateFunctionalWorkFlowDS_fromRhWorkflowDrl() throws Exception {
            WorkFlowDS ds = WorkFlowDSFactory.getWorkFlowDS("Rh_workflow.drl");

            assertThat(ds).isNotNull();
            // Verify the instance is functional by executing rules without error
            WorkFlowInfo info = new WorkFlowInfo();
            WorkFlowInfo result = ds.getMessages(info);
            assertThat(result).isSameAs(info);
        }
    }
}
