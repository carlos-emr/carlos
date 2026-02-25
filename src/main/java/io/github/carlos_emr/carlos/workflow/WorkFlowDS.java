/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * Originally written for the Department of Family Medicine, McMaster University.
 * Now maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 *
 * Modifications by CARLOS Contributors, 2026.
 */


package io.github.carlos_emr.carlos.workflow;

import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Executes compiled Drools rules against {@link WorkFlowInfo} fact objects for
 * clinical workflow decision support.
 *
 * <p>This class wraps a compiled {@link KieBase} (Drools knowledge base) and provides
 * a single entry point ({@link #getMessages(WorkFlowInfo)}) to evaluate DRL rules against
 * a workflow fact object. The rules inspect the {@code WorkFlowInfo} state (workflow type,
 * current state, gestation age, etc.) and may modify it by setting properties such as
 * {@code colour} or {@code currentState} to communicate decisions back to the caller.</p>
 *
 * <h3>Typical Usage</h3>
 * <pre>{@code
 * // 1. Obtain a WorkFlowDS instance from the factory (which loads and compiles DRL rules)
 * WorkFlowDS workflowDS = WorkFlowDSFactory.getWorkFlowDS("prenatal.drl");
 *
 * // 2. Create and populate a WorkFlowInfo fact object with workflow data
 * WorkFlowInfo info = new WorkFlowInfo(workflowData);
 *
 * // 3. Execute rules - the WorkFlowInfo object is modified in place by the rules
 * WorkFlowInfo result = workflowDS.getMessages(info);
 *
 * // 4. Read decision results from the modified WorkFlowInfo (e.g., colour, currentState)
 * String colour = result.getColour();
 * }</pre>
 *
 * <h3>KieSession Lifecycle</h3>
 * <p>Each call to {@link #getMessages(WorkFlowInfo)} creates a new stateful
 * {@link KieSession}, inserts the fact, fires all matching rules, and disposes
 * the session in a {@code finally} block to prevent resource leaks. The session
 * is never reused across invocations.</p>
 *
 * <h3>Migration Note</h3>
 * <p>Migrated from legacy Drools 2.0 API ({@code WorkingMemory}, {@code RuleBase})
 * to the modern KIE API (Drools 7.74.1) using {@link KieBase} and {@link KieSession}.
 * The rule execution semantics remain unchanged.</p>
 *
 * @since 2006-03-21
 * @see WorkFlowDSFactory
 * @see WorkFlowInfo
 * @see io.github.carlos_emr.carlos.drools.DroolsHelper
 */
public class WorkFlowDS {

    /**
     * The compiled Drools knowledge base containing the DRL rules to execute.
     * Set via the constructor by {@link WorkFlowDSFactory#getWorkFlowDS(String)}.
     */
    private final KieBase kieBase;

    /**
     * Creates a new WorkFlowDS instance backed by the specified compiled knowledge base.
     *
     * <p>This is the primary constructor used by {@link WorkFlowDSFactory} after loading
     * and compiling DRL rules via {@link io.github.carlos_emr.carlos.drools.DroolsHelper}.</p>
     *
     * @param kb KieBase the compiled Drools knowledge base containing the rules to execute;
     *           must not be {@code null}
     * @throws IllegalArgumentException if {@code kb} is {@code null}
     */
    public WorkFlowDS(KieBase kb) {
        if (kb == null) {
            throw new IllegalArgumentException("KieBase must not be null");
        }
        this.kieBase = kb;
    }

    /**
     * Executes the compiled Drools rules against the provided workflow information object.
     *
     * <p>This method follows the standard KieSession lifecycle:</p>
     * <ol>
     *   <li><strong>Create</strong> - A new stateful {@link KieSession} is created from the
     *       {@link KieBase}</li>
     *   <li><strong>Insert facts</strong> - The {@link WorkFlowInfo} object is inserted into
     *       working memory as a fact for rule pattern matching</li>
     *   <li><strong>Fire rules</strong> - All matching rules are evaluated and fired, which may
     *       modify the {@code WorkFlowInfo} properties (e.g., colour, currentState)</li>
     *   <li><strong>Dispose</strong> - The session is disposed in a {@code finally} block to
     *       release resources, regardless of whether rule execution succeeded or failed</li>
     * </ol>
     *
     * <p>The same {@code WorkFlowInfo} instance passed in is returned after rule execution,
     * with any modifications applied by the fired rules.</p>
     *
     * @param w WorkFlowInfo the workflow fact object to evaluate rules against; its properties
     *          may be modified by the DRL rules during execution
     * @return WorkFlowInfo the same object passed in, potentially modified by rule execution
     *         (e.g., colour set to indicate urgency, currentState updated)
     * @throws RuntimeException if a rule's consequence throws a runtime error (caught, logged,
     *         and re-thrown)
     * @throws Exception declared for compatibility; currently only {@code RuntimeException}
     *         is thrown from this method
     */
    public WorkFlowInfo getMessages(WorkFlowInfo w) throws Exception {
        // Create a new stateful KieSession for this rule evaluation
        KieSession kieSession = kieBase.newKieSession();
        try {
            // Insert the WorkFlowInfo as a fact into working memory so rules can match against it
            kieSession.insert(w);

            // Fire all rules whose conditions match the inserted facts.
            // Rules may modify the WorkFlowInfo object in their "then" clause.
            kieSession.fireAllRules();
        } catch (RuntimeException e) {
            MiscUtils.getLogger().error("Failed to evaluate workflow Drools rules", e);
            throw e;
        } finally {
            // Always dispose the session to release resources, even if rule execution failed.
            // KieSessions hold references to facts and internal state that must be cleaned up.
            kieSession.dispose();
        }
        return w;
    }


}
