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
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.logging.log4j.Logger;
import org.kie.api.KieBase;
import io.github.carlos_emr.carlos.drools.DroolsHelper;
import io.github.carlos_emr.carlos.drools.RuleBaseFactory;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Programmatically generates Drools DRL (Drools Rule Language) rule strings from
 * {@link DSCondition} objects and compiles them into executable {@link KieBase} instances
 * for clinical measurement decision support.
 *
 * <p>This class is a core component of the CARLOS EMR measurement flowsheet subsystem.
 * It translates clinical decision support conditions (e.g., "blood pressure above 140")
 * defined as {@link DSCondition} objects into DRL rule text, then compiles and caches
 * the resulting {@link KieBase} for evaluation against patient measurement data.</p>
 *
 * <h3>DRL Generation Pipeline</h3>
 * <p>The generation process works in two stages:</p>
 * <ol>
 *   <li><strong>Rule text generation</strong> ({@link #getRule}): Each caller (e.g.,
 *       {@link TargetColour}, {@code Recommendation}, {@code MeasurementTemplateFlowSheetConfig})
 *       provides a list of {@link DSCondition} objects. {@code getRule()} converts them
 *       into a single DRL rule string with condition expressions that invoke methods
 *       on the fact object (typically {@code MeasurementDSHelper}).</li>
 *   <li><strong>Rule compilation and caching</strong> ({@link #getRuleBase}): Multiple
 *       DRL rule strings are assembled into a complete DRL file with a package declaration,
 *       then compiled via {@link DroolsHelper#createKieBaseFromDrl(String)} and cached
 *       in {@link RuleBaseFactory} to avoid expensive recompilation on subsequent requests.</li>
 * </ol>
 *
 * <h3>Caching Strategy</h3>
 * <p>Compiled {@link KieBase} instances are cached in {@link RuleBaseFactory} using a
 * SHA-256 hash of the full DRL string as the cache key (prefixed with {@code "RuleBaseCreator:"}).
 * This ensures that identical rule sets produce cache hits while different rule configurations
 * trigger fresh compilation, without the memory overhead of storing full DRL text as keys.
 * The cache has a 24-hour TTL managed by {@link RuleBaseFactory}.</p>
 *
 * <h3>Generated DRL Format</h3>
 * <p>The generated DRL follows this structure:</p>
 * <pre>{@code
 * import ...MeasurementDSHelper;
 * rule "ruleName"
 *     when
 *         m : MeasurementDSHelper()
 *         eval( m.doubleValue() >= 140 )
 *         eval( m.isMale() == true )
 *     then
 *         m.setIndicationColor("HIGH");
 * end
 * }</pre>
 *
 * <h3>Drools Migration History</h3>
 * <p>This class was originally written for Drools 2.0, which used an XML-based rule
 * format. As part of the Drools 2.0 &rarr; 7.74.1 &rarr; 10.0.0 migration, the XML
 * generation was replaced with DRL text generation. The {@code getRule()} method now
 * produces modern DRL syntax with condition expressions, and {@code getRuleBase()} uses
 * the standard KIE API via {@link DroolsHelper} instead of the legacy
 * {@code org.drools.io.RuleBaseLoader}.</p>
 *
 * <h3>Usage in CARLOS EMR</h3>
 * <p>This class is used by multiple subsystems:</p>
 * <ul>
 *   <li>{@link io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementFlowSheet MeasurementFlowSheet}
 *       - compiles flowsheet-level decision support rules for measurement display</li>
 *   <li>{@link TargetColour} - generates rules for colour-coded measurement indicators
 *       (e.g., HIGH, LOW, NORMAL)</li>
 *   <li>{@code Recommendation} - generates rules for clinical recommendations</li>
 *   <li>{@code MeasurementTemplateFlowSheetConfig} - builds rules from flowsheet
 *       template configurations</li>
 *   <li>{@code DroolsNumerator2/4/5} - clinical reporting numerator rules</li>
 *   <li>{@code DSPreventionDrools} - prevention schedule decision support rules</li>
 *   <li>{@code DSGuidelineDrools} - clinical guideline decision support rules</li>
 * </ul>
 *
 * @since 2009-02-20
 * @see DroolsHelper
 * @see RuleBaseFactory
 * @see DSCondition
 * @see TargetColour
 * @see io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementFlowSheet
 */
public class RuleBaseCreator {
    private static final Logger log = MiscUtils.getLogger();

    /**
     * Assembles multiple DRL rule strings into a complete DRL file and compiles
     * them into a {@link KieBase}, using {@link RuleBaseFactory} to cache the result.
     *
     * <p>This method performs the following steps:</p>
     * <ol>
     *   <li>Constructs a complete DRL string by prepending a package declaration
     *       (using {@code rulesetName}) and concatenating all individual rule strings</li>
     *   <li>Checks the {@link RuleBaseFactory} cache for an existing compiled
     *       {@link KieBase} matching the full DRL content</li>
     *   <li>On cache miss, compiles the DRL via
     *       {@link DroolsHelper#createKieBaseFromDrl(String)} and stores the result
     *       in the cache for future requests</li>
     * </ol>
     *
     * <p>The cache key is {@code "RuleBaseCreator:" + sha256(fullDrlString)}, which guarantees
     * that identical rule sets always produce cache hits regardless of how they were
     * constructed, while using a compact hash instead of the full DRL text.</p>
     *
     * @param rulesetName String used to derive the DRL package name in the generated
     *                    {@code package} declaration (e.g., {@code "testPkg"}).
     *                    Non-identifier characters (colons, spaces, etc.) are sanitized
     *                    to underscores to produce a valid DRL package name
     * @param drlRules List of String individual DRL rule definitions, each typically
     *                 produced by {@link #getRule}; may contain {@code import} statements
     *                 which are extracted and deduplicated at the package level
     * @return KieBase compiled rule base containing all provided rules, ready for
     *         creating stateful or stateless sessions for rule evaluation
     * @throws Exception if DRL compilation fails due to syntax errors or invalid
     *                   rule definitions; error details are logged by {@link DroolsHelper}
     */
    public KieBase getRuleBase(String rulesetName, List<String> drlRules) throws Exception {
        long timer = System.currentTimeMillis();
        try {
            // Assemble a complete DRL file by extracting and deduplicating import
            // statements from individual rule strings, then placing them once at
            // the package level for a cleaner generated DRL.
            Set<String> imports = new LinkedHashSet<>();
            StringBuilder rulesBody = new StringBuilder();
            for (String rule : drlRules) {
                // Separate import lines from the rule body
                for (String line : rule.split("\n")) {
                    if (line.startsWith("import ")) {
                        imports.add(line);
                    } else {
                        rulesBody.append(line).append("\n");
                    }
                }
                rulesBody.append("\n");
            }

            // Sanitize rulesetName to produce a valid DRL package identifier.
            // Callers like DSGuidelineDrools pass keys with colons (e.g., "DSGuidelineDrools:42")
            // which are not valid in DRL package declarations.
            String packageName = rulesetName.replaceAll("[^a-zA-Z0-9._]", "_");

            StringBuilder drl = new StringBuilder();
            drl.append("package ").append(packageName).append(";\n\n");
            for (String imp : imports) {
                drl.append(imp).append("\n");
            }
            drl.append("\n");
            drl.append(rulesBody);
            String drlString = drl.toString();
            log.debug(drlString);

            // Check the RuleBaseFactory cache first to avoid expensive recompilation.
            // A SHA-256 hash of the DRL content is used as the cache key, ensuring that
            // any change in rule content triggers a fresh compile while keeping keys compact.
            String cacheKey = "RuleBaseCreator:" + sha256(drlString);
            KieBase kieBase = RuleBaseFactory.getRuleBase(cacheKey);
            if (kieBase != null) return kieBase;

            // Cache miss: compile the DRL via KieHelper and store the result.
            // DroolsHelper throws DroolsCompilationException if the DRL contains
            // compilation errors.
            kieBase = DroolsHelper.createKieBaseFromDrl(drlString);
            RuleBaseFactory.putRuleBase(cacheKey, kieBase);
            return kieBase;
        } finally {
            log.debug("generateRuleBase TimeMs : " + (System.currentTimeMillis() - timer));
        }
    }

    /**
     * Generates a single DRL rule string from a list of {@link DSCondition} objects.
     *
     * <p>Produces a complete DRL rule definition including an import statement, a fact
     * pattern match, condition expressions derived from each {@link DSCondition},
     * and a consequence (action) block. The generated rule uses the modern DRL text
     * format, replacing the legacy Drools 2.0 XML rule representation.</p>
     *
     * <h4>Generated Structure</h4>
     * <p>For a call with {@code ruleName="BP_HIGH"}, {@code incomingClass=
     * "...MeasurementDSHelper"}, two conditions, and a consequence, the output is:</p>
     * <pre>{@code
     * import ...MeasurementDSHelper;
     * rule "BP_HIGH"
     *     when
     *         m : MeasurementDSHelper()
     *         eval( m.doubleValue() >= 140 )
     *         eval( m.isMale() == true )
     *     then
     *         m.setIndicationColor("HIGH");
     * end
     * }</pre>
     *
     * <h4>Condition Mapping</h4>
     * <p>Each {@link DSCondition} is translated into a Drools condition expression.
     * The condition's {@link DSCondition#getType() type} becomes a method call on the
     * bound fact variable {@code m}, the {@link DSCondition#getComparision() comparison}
     * becomes the operator (e.g., {@code >=}, {@code ==}), and the
     * {@link DSCondition#getValue() value} becomes the right-hand operand. The
     * {@code DSCondition.getType()} method handles appending parentheses and optional
     * parameters to form a valid method invocation.</p>
     *
     * @param ruleName String the unique name for this rule within the DRL package;
     *                 callers typically use sequential names like "DD0", "DD1", etc.
     * @param incomingClass String the fully qualified Java class name of the fact object
     *                      to match in the rule's {@code when} clause (e.g.,
     *                      {@code "io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.MeasurementDSHelper"})
     * @param conditions List of {@link DSCondition} objects defining the rule's
     *                   criteria; each condition generates one condition expression line
     * @param consequence String the Java code to execute in the rule's {@code then} block
     *                    when all conditions are satisfied (e.g.,
     *                    {@code "m.setIndicationColor(\"HIGH\");"})
     * @return String the complete DRL rule definition text, including the import statement,
     *         rule declaration, when/then blocks, and end marker
     * @see DSCondition#getType()
     * @see DSCondition#getComparision()
     * @see DSCondition#getValue()
     */
    public String getRule(String ruleName, String incomingClass, List<DSCondition> conditions, String consequence) {
        // Extract the simple class name from the fully qualified name for use
        // in the DRL pattern match (e.g., "MeasurementDSHelper" from the full path).
        String simpleClassName = incomingClass.substring(incomingClass.lastIndexOf('.') + 1);

        StringBuilder rule = new StringBuilder();

        // Import statement: required so the DRL engine can resolve the fact class.
        // Each rule string includes its own import because getRule() produces
        // self-contained DRL fragments that are later assembled by getRuleBase().
        rule.append("import ").append(incomingClass).append(";\n");

        // Rule header with quoted name
        rule.append("rule \"").append(ruleName).append("\"\n");

        // "when" clause: bind the fact object to variable "m" for use in conditions
        // and consequence. The pattern "m : ClassName()" matches any instance of the
        // class inserted into the Drools working memory, binding it to variable "m".
        rule.append("    when\n");
        rule.append("        m : ").append(simpleClassName).append("()\n");

        // Each DSCondition becomes a condition expression that calls a method on "m".
        // For example, DSCondition(type="doubleValue", comparison=">=", value="140")
        // produces: eval( m.doubleValue() >= 140 )
        // The DSCondition.getType() method handles method call formatting, including
        // appending "()" for no-arg methods or "(\"param\")" for parameterized methods.
        for (DSCondition cond : conditions) {
            rule.append("        eval( m.").append(cond.getType()).append(" ").append(cond.getComparision()).append(" ").append(cond.getValue()).append(" )\n");
        }

        // "then" clause: the consequence Java code that executes when all conditions match.
        // Typically sets an indication color on the MeasurementDSHelper fact object,
        // e.g., m.setIndicationColor("HIGH");
        rule.append("    then\n");
        rule.append("        ").append(consequence).append("\n");
        rule.append("end");
        log.debug("Return Rule: {}", rule);
        return rule.toString();
    }

    /**
     * Computes a SHA-256 hex digest of the given string for use as a compact cache key.
     */
    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in every JVM
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
