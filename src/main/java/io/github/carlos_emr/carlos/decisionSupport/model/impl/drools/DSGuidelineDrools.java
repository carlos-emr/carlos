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

package io.github.carlos_emr.carlos.decisionSupport.model.impl.drools;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.PostUpdate;
import javax.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import io.github.carlos_emr.carlos.decisionSupport.model.DSCondition;
import io.github.carlos_emr.carlos.decisionSupport.model.DSConsequence;
import io.github.carlos_emr.carlos.decisionSupport.model.DSDemographicAccess;
import io.github.carlos_emr.carlos.decisionSupport.model.DSGuideline;
import io.github.carlos_emr.carlos.decisionSupport.model.DSParameter;
import io.github.carlos_emr.carlos.decisionSupport.model.DecisionSupportException;
import io.github.carlos_emr.carlos.drools.RuleBaseFactory;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.RuleBaseCreator;

/**
 * Drools-based clinical decision support guideline entity that programmatically generates
 * DRL (Drools Rule Language) rules from XML-stored conditions, parameters, and consequences.
 *
 * <p>This JPA entity is the concrete implementation of {@link DSGuideline} for the Drools rules
 * engine. It is persisted in the {@code dsGuidelines} table with a discriminator value of
 * {@code "drools"}, and its clinical logic (conditions, parameters, consequences) is stored as
 * XML in the {@code xml} LOB column. At evaluation time, the XML is parsed by
 * {@link io.github.carlos_emr.carlos.decisionSupport.model.DSGuidelineFactory DSGuidelineFactory}
 * into structured {@link DSCondition}, {@link DSParameter}, and {@link DSConsequence} objects,
 * which are then translated into DRL rule text and compiled into a {@link KieBase}.</p>
 *
 * <h3>DRL Generation Pipeline</h3>
 * <p>The class generates DRL rules dynamically using the following pipeline:</p>
 * <ol>
 *   <li><strong>Parse XML</strong>: The base class lazily parses the stored XML into lists of
 *       {@code DSCondition}, {@code DSParameter}, and {@code DSConsequence} objects.</li>
 *   <li><strong>Build DRL "when" clause</strong>: Each {@code DSCondition} is converted into a
 *       DRL eval expression via {@link #getDroolsCondition(DSCondition)}. Conditions
 *       invoke methods on the bound {@link DSDemographicAccess} fact object (bound as variable
 *       {@code a}), passing comma-joined condition values as a single string argument.</li>
 *   <li><strong>Build DRL parameter bindings</strong>: Each {@code DSParameter} is converted into
 *       a DRL fact binding via {@link #getDroolsParameter(DSParameter)}, using the parameter's
 *       fully-qualified class name (FQCN) directly in the DRL without an import statement.
 *       Drools resolves FQCNs inline, so no {@code import} directive is needed.</li>
 *   <li><strong>Build DRL "then" clause</strong>: Consequences are assembled via
 *       {@link #getDroolsConsequences(List)}, which always sets
 *       {@code a.setPassedGuideline(true)} and appends any {@code java}-type consequence text.</li>
 *   <li><strong>Compile to KieBase</strong>: The generated DRL string is compiled via
 *       {@link RuleBaseCreator} and {@link io.github.carlos_emr.carlos.drools.DroolsHelper DroolsHelper}
 *       into a {@link KieBase}.</li>
 * </ol>
 *
 * <h3>Generated DRL Structure</h3>
 * <p>A typical generated rule looks like:</p>
 * <pre>{@code
 * rule "DSGuidelineDrools:42.0"
 *     when
 *         a : io.github.carlos_emr.carlos.decisionSupport.model.DSDemographicAccess()
 *         myParam : com.example.SomeClass()
 *         param0 : java.util.Hashtable()
 *         eval( a.hasDxCodesAny("icd9:250,icd9:401") )
 *         eval( a.isAgeAll(">18y") )
 *     then
 *         a.setPassedGuideline(true);
 * end
 * }</pre>
 *
 * <p>Fact classes use FQCNs to avoid DRL imports.
 * Conditions with associated {@link java.util.Hashtable} parameters
 * (e.g., billing options like {@code payer=MSP, notInDays=365}) are bound as separate
 * Hashtable facts and passed to the method alongside the condition values.</p>
 *
 * <h3>Caching Strategy</h3>
 * <p>Compiled {@link KieBase} instances are cached in {@link RuleBaseFactory} using a key of
 * the form {@code "DSGuidelineDrools:<id>"} (or {@code "DSGuidelineDrools:<title>"} when the
 * entity has no persisted ID). The cache has a 24-hour TTL. When the entity is updated via JPA,
 * the {@link #afterSave()} callback (annotated with {@link javax.persistence.PostUpdate @PostUpdate})
 * invalidates the cached entry, forcing recompilation on the next evaluation.</p>
 *
 * <h3>Evaluation Flow</h3>
 * <p>The three {@code evaluate()} overloads create a new {@link KieSession}, insert the
 * {@link DSDemographicAccess} fact (which provides access to patient demographics, diagnoses,
 * prescriptions, billing, and clinical notes), insert any condition-associated Hashtable
 * parameters and DSParameter-defined objects, then fire all rules. If the
 * {@code DSDemographicAccess.passedGuideline} flag is set to {@code true} by the rule
 * consequence, the method returns the guideline's consequences; otherwise it returns
 * {@code null}.</p>
 *
 * <h3>Migration History</h3>
 * <p>Originally used the Drools 2.0 XML rule format with {@code org.drools.RuleBase}.
 * Migrated to programmatic DRL text generation with the KIE API ({@link KieBase},
 * {@link KieSession}) as part of the Drools 2.0 to 7.74.1.Final upgrade.</p>
 *
 * @since 2009-07-06
 * @see io.github.carlos_emr.carlos.drools.DroolsHelper
 * @see RuleBaseFactory
 * @see DSCondition
 * @see DSDemographicAccess
 * @see DSParameter
 * @see DSConsequence
 * @see RuleBaseCreator
 */
@Entity
@DiscriminatorValue("drools")
public class DSGuidelineDrools extends DSGuideline {
    private static final Logger log = MiscUtils.getLogger();

    /**
     * Fully-qualified class name of {@link DSDemographicAccess}, used as the primary fact
     * type in generated DRL rules. Uses FQCN to avoid DRL import statements.
     */
    private static final String demographicAccessObjectClassPath = "io.github.carlos_emr.carlos.decisionSupport.model.DSDemographicAccess";

    /**
     * Cached compiled KieBase for this guideline instance. Lazily initialized on first
     * evaluation via {@link #generateRuleBase()}. Marked {@code @Transient} because it
     * is a runtime artifact and must not be persisted. Set to {@code null} to force
     * recompilation.
     */
    @Transient
    private volatile KieBase _kieBase = null;

    /**
     * Counter used to generate unique rule names. Incremented once per call to
     * {@link #generateRuleBase()}. If the same guideline instance is recompiled
     * (e.g., after cache eviction), the counter ensures a unique rule name such as
     * {@code "DSGuidelineDrools:42.1"} instead of reusing {@code "DSGuidelineDrools:42.0"}.
     * Marked {@code @Transient} because it is a runtime artifact.
     */
    @Transient
    private int ruleCount = 0;

    /**
     * Builds the cache key used to store and retrieve the compiled {@link KieBase}
     * in {@link RuleBaseFactory}.
     *
     * <p>If the entity has been persisted (has a non-null ID), the key is
     * {@code "DSGuidelineDrools:<id>"}. Otherwise, it falls back to
     * {@code "DSGuidelineDrools:<title>"}. This ensures that unsaved guidelines
     * (e.g., during testing or preview) can still participate in caching.</p>
     *
     * @return String cache key for {@link RuleBaseFactory} lookup
     */
    public String getRuleBaseFactoryKey() {
        if (getId() != null)
            return ("DSGuidelineDrools:" + getId());
        else
            return "DSGuidelineDrools:" + title;

    }

    /**
     * Evaluates this clinical guideline against a patient's data and returns applicable consequences.
     *
     * <p>Creates a {@link DSDemographicAccess} fact for the given patient, inserts it along with
     * any condition-associated Hashtable parameters and DSParameter-defined objects into a new
     * {@link KieSession}, fires all rules, and checks whether the guideline passed. If it passed,
     * returns the list of consequences (with java-type consequences populated with all objects
     * from working memory). If it did not pass, returns {@code null}.</p>
     *
     * <p>The KieBase is lazily compiled on first invocation via {@link #generateRuleBase()} and
     * cached for subsequent calls. The KieSession is always disposed in a finally block to
     * prevent memory leaks.</p>
     *
     * @param loggedInInfo LoggedInInfo session information for the evaluating provider
     * @param demographicNo String patient identifier used to retrieve clinical data
     * @return List of {@link DSConsequence} objects representing triggered clinical recommendations
     *         or warnings; {@code null} if the guideline conditions were not met
     * @throws DecisionSupportException if rule base compilation fails, fact insertion fails,
     *         or a DSParameter class cannot be instantiated via reflection
     */
    public List<DSConsequence> evaluate(LoggedInInfo loggedInInfo, String demographicNo) throws DecisionSupportException {
        return executeRules(new DSDemographicAccess(loggedInInfo, demographicNo));
    }

    /**
     * Evaluates this clinical guideline with provider-specific context and returns applicable consequences.
     *
     * <p>Identical to {@link #evaluate(LoggedInInfo, String)} but additionally passes a provider
     * number to the {@link DSDemographicAccess} fact, enabling provider-specific evaluations such
     * as flowsheet-based conditions that depend on provider-customized measurement configurations.</p>
     *
     * @param loggedInInfo LoggedInInfo session information for the evaluating provider
     * @param demographicNo String patient identifier used to retrieve clinical data
     * @param providerNo String provider identifier for provider-specific evaluation context
     * @return List of {@link DSConsequence} objects representing triggered clinical recommendations
     *         or warnings; {@code null} if the guideline conditions were not met
     * @throws DecisionSupportException if rule base compilation fails, fact insertion fails,
     *         or a DSParameter class cannot be instantiated via reflection
     */
    public List<DSConsequence> evaluate(LoggedInInfo loggedInInfo, String demographicNo, String providerNo) throws DecisionSupportException {
        return executeRules(new DSDemographicAccess(loggedInInfo, demographicNo, providerNo));
    }

    /**
     * Evaluates this clinical guideline with dynamic arguments and returns applicable consequences.
     *
     * <p>This is the most flexible evaluation overload. In addition to provider context, it passes
     * a list of dynamic arguments (e.g., active ATC codes from the current prescription context)
     * to the {@link DSDemographicAccess} fact. These dynamic arguments are used by specialized
     * condition methods such as {@code hasATCcodeAny()} and {@code hasRxClassNotany()} that
     * compare guideline criteria against runtime-provided data rather than database lookups.</p>
     *
     * @param loggedInInfo LoggedInInfo session information for the evaluating provider
     * @param demographicNo String patient identifier used to retrieve clinical data
     * @param providerNo String provider identifier for provider-specific evaluation context
     * @param dynamicArgs List of Object parameters for specialized evaluation logic, typically
     *                    ATC codes or other runtime values passed from the calling context
     * @return List of {@link DSConsequence} objects representing triggered clinical recommendations
     *         or warnings; {@code null} if the guideline conditions were not met
     * @throws DecisionSupportException if rule base compilation fails, fact insertion fails,
     *         or a DSParameter class cannot be instantiated via reflection
     */
    public List<DSConsequence> evaluate(LoggedInInfo loggedInInfo, String demographicNo, String providerNo, List<Object> dynamicArgs) throws DecisionSupportException {
        return executeRules(new DSDemographicAccess(loggedInInfo, demographicNo, providerNo, dynamicArgs));
    }

    /**
     * Core rule execution logic shared by all evaluate() overloads.
     *
     * <p>Creates a KieSession, inserts the DSDemographicAccess fact along with any condition
     * parameters and DSParameter-defined objects, fires all rules, and collects consequences
     * if the guideline conditions were met.</p>
     *
     * @param dsDemographicAccess the primary fact object providing access to patient data
     * @return List of triggered consequences, or {@code null} if conditions were not met
     * @throws DecisionSupportException if rule compilation, fact insertion, rule execution,
     *         or DSParameter class instantiation via reflection fails. The exception message
     *         includes the guideline title and the original exception's class name for diagnostics.
     */
    private List<DSConsequence> executeRules(DSDemographicAccess dsDemographicAccess) throws DecisionSupportException {
        if (_kieBase == null) generateRuleBase();
        KieBase localKieBase = _kieBase;
        if (localKieBase == null) {
            throw new DecisionSupportException("Rule base compilation failed for guideline '" + this.getTitle() + "'");
        }
        KieSession kieSession = localKieBase.newKieSession();
        try {
            // Insert the primary fact object that provides access to all patient data
            kieSession.insert(dsDemographicAccess);

            // Insert any Hashtable parameters associated with conditions (e.g., billing options).
            // These are bound in the DRL as "param0 : java.util.Hashtable()" and passed
            // to condition methods like billedForAny(searchStrings, options).
            for (DSCondition dsc : this.getConditions()) {
                if (dsc.getParam() != null && !dsc.getParam().isEmpty()) {
                    log.debug("PARAM:" + dsc.getParam().toString());
                    kieSession.insert(dsc.getParam());
                }
            }

            // Instantiate and insert DSParameter-defined objects via reflection.
            // These are additional fact types declared in the guideline XML that
            // the DRL rules may reference (e.g., custom data access objects).
            List<DSParameter> lDSP = this.getParameters();
            if (lDSP != null) {
                for (DSParameter dsp : lDSP) {
                    Class clas = Class.forName(dsp.getStrClass());
                    Constructor constructor = clas.getConstructor();
                    Object obj = constructor.newInstance();
                    kieSession.insert(obj);
                }
            }

            // Fire all compiled rules; if conditions match, the rule consequence
            // sets dsDemographicAccess.passedGuideline = true
            kieSession.fireAllRules();
            if (dsDemographicAccess.isPassedGuideline()) {
                List<DSConsequence> returnDsConsequences = new ArrayList<DSConsequence>();
                if (this.getConsequences() == null) return returnDsConsequences;
                for (DSConsequence dsConsequence : this.getConsequences()) {
                    if (dsConsequence.getConsequenceType() != DSConsequence.ConsequenceType.java) {
                        // Warning-type consequences are returned as-is with their text
                        returnDsConsequences.add(dsConsequence);
                    } else {
                        // Java-type consequences receive all objects currently in working memory,
                        // including the injected dsDemographicAccess, condition Hashtable parameters,
                        // DSParameter-instantiated objects, and any objects added by rule "then" blocks
                        @SuppressWarnings("unchecked")
                        List<Object> javaConsequences = new ArrayList<>(kieSession.getObjects());
                        dsConsequence.setObjConsequence(javaConsequences);
                        returnDsConsequences.add(dsConsequence);
                    }
                }
                return returnDsConsequences;
            } else {
                return null;
            }
        } catch (RuntimeException e) {
            throw new DecisionSupportException("Guideline evaluation failed for '"
                    + this.getTitle() + "': " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException
                 | IllegalAccessException | InvocationTargetException e) {
            throw new DecisionSupportException("Unable to instantiate class", e);
        } finally {
            kieSession.dispose();
        }
    }

    /**
     * Generates and caches the {@link KieBase} for this guideline by translating its XML-defined
     * conditions, parameters, and consequences into a DRL rule string and compiling it.
     *
     * <p>This method implements a two-level caching strategy:</p>
     * <ol>
     *   <li><strong>RuleBaseFactory cache</strong>: First checks {@link RuleBaseFactory} for a
     *       previously compiled KieBase using the guideline's cache key. If found, assigns it
     *       directly to the instance field and returns immediately.</li>
     *   <li><strong>RuleBaseCreator cache</strong>: If not in the factory cache, generates a DRL
     *       string and delegates to {@link RuleBaseCreator#getRuleBase(String, List)} (where the
     *       first parameter is used as the DRL package name, not as a cache key). RuleBaseCreator
     *       has its own secondary cache keyed by {@code "RuleBaseCreator:" + sha256(fullDrlString)}.
     *       The compiled result is then stored in RuleBaseFactory under the guideline's own key
     *       (by this method).</li>
     * </ol>
     *
     * <p>The DRL generation pipeline proceeds as follows:</p>
     * <ol>
     *   <li>Convert each {@link DSParameter} into a DRL fact-binding line</li>
     *   <li>Assign sequential labels ({@code param0}, {@code param1}, ...) to conditions that
     *       carry Hashtable parameters, so the DRL can bind them as named facts</li>
     *   <li>Convert each {@link DSCondition} into a DRL eval expression</li>
     *   <li>Assemble the DRL consequence block from the guideline's {@link DSConsequence} list</li>
     *   <li>Combine all elements into a complete DRL rule via
     *       {@link #getRule(List, List, String, int)}</li>
     * </ol>
     *
     * @throws DecisionSupportException if the DRL cannot be compiled into a valid rule base,
     *         wrapping the underlying compilation or parsing exception
     */
    public void generateRuleBase() throws DecisionSupportException {
        long timer = System.currentTimeMillis();
        try {
            String ruleBaseFactoryKey = getRuleBaseFactoryKey();

            // Check the RuleBaseFactory cache first to avoid recompilation
            KieBase result = RuleBaseFactory.getRuleBase(ruleBaseFactoryKey);
            if (result != null) {
                _kieBase = result;
                return;
            }

            ArrayList<String> rules = new ArrayList<String>();
            ArrayList<String> conditionElements = new ArrayList<String>();
            ArrayList<String> lParameterElements = new ArrayList<String>();

            // Convert each DSParameter into a DRL fact-binding line, e.g.:
            //   "myAlias : com.example.SomeClass()"
            if (this.getParameters() != null) {
                for (DSParameter dsParameter : this.getParameters()) {
                    String parameterElement = this.getDroolsParameter(dsParameter);
                    lParameterElements.add(parameterElement);
                }
            }

            // Assign sequential labels to conditions that carry Hashtable parameters.
            // The label (e.g., "param0") becomes both the DRL variable name for the
            // Hashtable fact binding and the argument passed to the condition's access
            // method in the eval expression.
            int paramCount = 0;

            for (DSCondition condition : this.getConditions()) {
                if (condition.getParam() != null && !condition.getParam().isEmpty()) {
                    condition.setLabel("param" + paramCount);
                    paramCount++;
                }
                String conditionElement = getDroolsCondition(condition);
                conditionElements.add(conditionElement);
            }

            String consequencesElement = this.getDroolsConsequences(this.getConsequences());

            rules.add(this.getRule(conditionElements, lParameterElements, consequencesElement, ruleCount++));

            // Compile the DRL via RuleBaseCreator, which delegates to DroolsHelper
            // for KIE API compilation, and cache the result in RuleBaseFactory
            RuleBaseCreator ruleBaseCreator = new RuleBaseCreator();
            try {
                _kieBase = ruleBaseCreator.getRuleBase(ruleBaseFactoryKey, rules);
                RuleBaseFactory.putRuleBase(ruleBaseFactoryKey, _kieBase);
            } catch (Exception e) {
                throw new DecisionSupportException("Could not create a rule base for guideline '" + this.getTitle() + "'", e);
            }
        } finally {
            log.debug("generateRuleBase TimeMs : " + (System.currentTimeMillis() - timer));
        }
    }

    /**
     * Generates a complete DRL rule string with parameter bindings, condition bindings, and
     * consequence block.
     *
     * <p>This is the primary rule generation method that produces the full DRL rule text,
     * including:</p>
     * <ul>
     *   <li>The primary {@link DSDemographicAccess} fact binding (variable {@code a}) using
     *       the FQCN so no DRL import is needed</li>
     *   <li>{@link DSParameter}-defined fact bindings (e.g., {@code myAlias : com.example.Foo()})</li>
     *   <li>{@link java.util.Hashtable} fact bindings for conditions that carry parameters
     *       (e.g., {@code param0 : java.util.Hashtable()}), matched by Drools against
     *       Hashtable objects inserted into working memory</li>
     *   <li>DRL eval expressions for each condition</li>
     *   <li>The consequence (then) block</li>
     * </ul>
     *
     * @param conditionElements List of String DRL eval expressions, one per {@link DSCondition}
     * @param parameterElements List of String DRL fact-binding lines, one per {@link DSParameter}
     * @param consequenceElement String the DRL consequence block content
     * @param ruleCount int counter appended to the rule name for uniqueness
     * @return String the complete DRL rule definition
     */
    private String getRule(List<String> conditionElements, List<String> parameterElements, String consequenceElement, int ruleCount) {
        StringBuilder rule = new StringBuilder();
        String ruleName = getRuleBaseFactoryKey() + "." + ruleCount;
        rule.append("rule \"").append(ruleName).append("\"\n");
        rule.append("    when\n");

        // Bind the DSDemographicAccess fact as variable "a" using its FQCN
        rule.append("        a : ").append(demographicAccessObjectClassPath).append("()\n");

        // Append DSParameter-defined fact bindings (e.g., "myAlias : com.example.SomeClass()")
        for (String paramElement : parameterElements) {
            rule.append(paramElement).append("\n");
        }

        // Bind Hashtable facts for conditions that carry parameter maps.
        // Each condition with a non-empty param Hashtable gets a named binding
        // (e.g., "param0 : java.util.Hashtable()"). When the rule fires, Drools
        // matches these bindings against the Hashtable objects inserted into
        // working memory during evaluate(). The label (param0, param1, ...) is
        // then passed as an argument to the condition's access method in the
        // eval expression, allowing methods like billedForAny(codes, options)
        // to receive the Hashtable containing options like payer and notInDays.
        for (DSCondition condition : this.getConditions()) {
            if (condition.getParam() != null && !condition.getParam().isEmpty()) {
                rule.append("        ").append(condition.getLabel()).append(" : java.util.Hashtable()\n");
            }
        }

        // Append eval expressions for each condition
        for (String conditionElement : conditionElements) {
            rule.append(conditionElement).append("\n");
        }

        rule.append("    then\n");
        rule.append("        ").append(consequenceElement).append("\n");
        rule.append("end");
        return rule.toString();
    }

    /**
     * Generates a complete DRL rule string without explicit parameter bindings.
     *
     * <p>This overload omits the {@link DSParameter}-based fact bindings but still includes
     * Hashtable bindings for conditions that carry parameters. It is used as a delegate by
     * {@link #getRule(String, String, int)} for single-condition rules.</p>
     *
     * @param conditionElements List of String DRL eval expressions, one per {@link DSCondition}
     * @param consequenceElement String the DRL consequence block content
     * @param ruleCount int counter appended to the rule name for uniqueness
     * @return String the complete DRL rule definition
     */
    private String getRule(List<String> conditionElements, String consequenceElement, int ruleCount) {
        StringBuilder rule = new StringBuilder();
        String ruleName = getRuleBaseFactoryKey() + "." + ruleCount;
        rule.append("rule \"").append(ruleName).append("\"\n");
        rule.append("    when\n");

        // Bind the DSDemographicAccess fact as variable "a" using its FQCN
        rule.append("        a : ").append(demographicAccessObjectClassPath).append("()\n");

        // Bind Hashtable facts for conditions that carry parameter maps
        for (DSCondition condition : this.getConditions()) {
            if (condition.getParam() != null && !condition.getParam().isEmpty()) {
                rule.append("        ").append(condition.getLabel()).append(" : java.util.Hashtable()\n");
            }
        }

        // Append eval expressions for each condition
        for (String conditionElement : conditionElements) {
            rule.append(conditionElement).append("\n");
        }

        rule.append("    then\n");
        rule.append("        ").append(consequenceElement).append("\n");
        rule.append("end");
        return rule.toString();
    }

    /**
     * Convenience method that generates a DRL rule from a single condition string.
     *
     * <p>Wraps the single condition in a list and delegates to
     * {@link #getRule(List, String, int)}. This is useful for guidelines with
     * exactly one condition or for testing purposes.</p>
     *
     * @param conditionElement String a single DRL eval expression
     * @param consequenceElement String the DRL consequence block content
     * @param ruleCounter int counter appended to the rule name for uniqueness
     * @return String the complete DRL rule definition
     */
    protected String getRule(String conditionElement, String consequenceElement, int ruleCounter) {
        List<String> conditionElements = new ArrayList<String>();
        conditionElements.add(conditionElement);
        return getRule(conditionElements, consequenceElement, ruleCounter);
    }

    /**
     * Converts a {@link DSCondition} into a DRL eval expression string that invokes the
     * corresponding access method on the bound {@link DSDemographicAccess} fact.
     *
     * <p>The generated eval expression follows this pattern:</p>
     * <pre>
     *     eval( a.{accessMethod}{ListOperator}("{value1,value2,...}") )
     * </pre>
     *
     * <p>The method name is assembled by concatenating:</p>
     * <ol>
     *   <li>The condition type's base access method (from {@link DSDemographicAccess.Module},
     *       e.g., {@code "hasDxCodes"}, {@code "isAge"}, {@code "billedFor"})</li>
     *   <li>The capitalized list operator name (e.g., {@code "Any"}, {@code "All"},
     *       {@code "Not"}, {@code "Notany"}, {@code "Notall"})</li>
     * </ol>
     * <p>This produces method names like {@code hasDxCodesAny}, {@code isAgeAll},
     * {@code billedForNotany}, which are defined on {@link DSDemographicAccess}.</p>
     *
     * <p>The condition's values (from the XML {@code <value>} elements) are joined with
     * commas and passed as a single quoted string argument. If the condition carries a
     * Hashtable parameter map (e.g., billing options), the parameter's label variable
     * (e.g., {@code param0}) is appended as a second unquoted argument, referencing the
     * Hashtable fact bound earlier in the DRL "when" clause.</p>
     *
     * <p>Example outputs:</p>
     * <ul>
     *   <li>{@code eval( a.hasDxCodesAny("icd9:250,icd9:401") )}</li>
     *   <li>{@code eval( a.billedForAny("13050,14050",param0) )}</li>
     * </ul>
     *
     * @param condition DSCondition the clinical condition to convert into a DRL expression
     * @return String the formatted DRL eval expression line (indented with 8 spaces)
     * @see DSDemographicAccess for the available access methods that are invoked
     */
    public String getDroolsCondition(DSCondition condition) {
        // Get the base method name from the condition's module type
        // (e.g., Module.dxcodes -> "hasDxCodes", Module.age -> "isAge")
        String accessMethod = condition.getConditionType().getAccessMethod();

        // Join all condition values into a single comma-separated, quoted string argument
        // (e.g., "icd9:250,icd9:401" or ">18y")
        String joined = StringUtils.join(condition.getValues(), ",");
        String parameters = "\"" + joined.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";

        // Append the capitalized list operator to form the full method name
        // (e.g., "hasDxCodes" + "Any" -> "hasDxCodesAny")
        accessMethod = accessMethod + StringUtils.capitalize(condition.getListOperator().name());

        // Build the method call on the bound DSDemographicAccess fact variable "a"
        String functionStr = "a." + accessMethod + "(" + parameters;

        // If the condition carries a Hashtable parameter map, append the label as a
        // second argument (unquoted, since it references a DRL-bound variable)
        if (condition.getParam() != null && !condition.getParam().isEmpty()) {
            functionStr += "," + condition.getLabel();
        }
        functionStr += ")";

        return "        eval( " + functionStr + " )";
    }

    /**
     * Converts a {@link DSParameter} into a DRL fact-binding line.
     *
     * <p>Generates a DRL pattern that binds an instance of the parameter's class to
     * a named variable. The fully-qualified class name (FQCN) is used directly in the
     * DRL, avoiding the need for a separate import statement. At runtime, Drools matches
     * this pattern against objects inserted into the KieSession's working memory.</p>
     *
     * <p>Example output: {@code "        myList : java.util.ArrayList()"}</p>
     *
     * @param dsParameter DSParameter the parameter definition containing the alias and
     *                    fully-qualified class name
     * @return String the formatted DRL fact-binding line (indented with 8 spaces)
     */
    public String getDroolsParameter(DSParameter dsParameter) {
        String safeAlias = dsParameter.getStrAlias().replaceAll("[^a-zA-Z0-9_]", "_");
        return "        " + safeAlias + " : " + dsParameter.getStrClass() + "()";
    }

    /**
     * Builds the DRL consequence (then) block from the guideline's consequence definitions.
     *
     * <p>The consequence block always begins with {@code a.setPassedGuideline(true)}, which
     * signals to the calling {@code evaluate()} method that the guideline's conditions were
     * satisfied. Any consequences of type {@link DSConsequence.ConsequenceType#java} have
     * their text appended as additional Java statements in the consequence block.</p>
     *
     * <p>Warning-type consequences are not included in the DRL output; they are handled
     * separately in the {@code evaluate()} method after rule execution, where they are
     * returned directly to the caller as-is.</p>
     *
     * <p>Example output:</p>
     * <pre>
     * a.setPassedGuideline(true);
     *         someObject.doSomething();
     * </pre>
     *
     * @param consequences List of {@link DSConsequence} objects to include in the rule's
     *                     "then" block
     * @return String the assembled DRL consequence block content
     */
    public String getDroolsConsequences(List<DSConsequence> consequences) {
        if (consequences == null) {
            throw new IllegalStateException(
                    "Consequences list is null for guideline '" + this.getTitle()
                    + "'; the guideline XML may have failed to parse");
        }
        // Always mark the guideline as passed; this is the signal to evaluate()
        // that conditions were met and consequences should be returned.
        String passedMarker = "a.setPassedGuideline(true);";
        StringBuilder result = new StringBuilder(passedMarker);
        for (DSConsequence consequence : consequences) {
            // Only java-type consequences are embedded in the DRL "then" block;
            // warning-type consequences are returned directly by evaluate()
            if (consequence.getConsequenceType() == DSConsequence.ConsequenceType.java) {
                result.append("\n        ").append(consequence.getText());
            }
        }
        return result.toString();
    }

    /**
     * JPA lifecycle callback that invalidates the cached {@link KieBase} when this
     * guideline entity is updated in the database.
     *
     * <p>Annotated with {@link PostUpdate}, this method is automatically invoked by the
     * JPA persistence provider after a successful update of this entity. It removes the
     * compiled rule base from the {@link RuleBaseFactory} cache, forcing recompilation
     * from the updated XML on the next call to {@link #evaluate(LoggedInInfo, String)}
     * or any of its overloads.</p>
     *
     * <p>Both the shared {@link RuleBaseFactory} cache entry and the instance-level
     * {@code _kieBase} field are cleared, ensuring that even if the same Java object
     * instance is reused after the update (within the same persistence context), the
     * stale in-memory KieBase will not be used.</p>
     */
    @PostUpdate
    public void afterSave() {
        RuleBaseFactory.removeRuleBase(getRuleBaseFactoryKey());
        _kieBase = null;
    }
}
