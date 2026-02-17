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

import java.util.List;

import org.apache.logging.log4j.Logger;
import org.kie.api.KieBase;
import io.github.carlos_emr.carlos.drools.DroolsCompilationException;
import io.github.carlos_emr.carlos.drools.DroolsHelper;
import io.github.carlos_emr.carlos.drools.RuleBaseFactory;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Creates Drools 7.x DRL rule definitions programmatically and compiles them into KieBase instances.
 *
 * <p>Migrated from Drools 2.0 XML format to modern DRL text format as part of
 * the Drools 2.0 to 7.74.1.Final upgrade.</p>
 *
 * @since 2001-01-01
 */
public class RuleBaseCreator {
    private static final Logger log = MiscUtils.getLogger();

    /**
     * Tracks the incoming class from the last getRule() call so getRuleBase() can generate imports.
     */
    private String lastIncomingClass = null;

    /**
     * Compiles a list of DRL rule strings into a KieBase.
     *
     * <p>Wraps the rule strings with a package declaration and import statement,
     * then compiles via {@link DroolsHelper}. Results are cached in
     * {@link RuleBaseFactory} using the DRL content as the cache key.</p>
     *
     * @param rulesetName String name for the rule set package
     * @param ruleStrings List of String DRL rule definitions
     * @return KieBase the compiled knowledge base
     * @throws Exception if compilation fails
     */
    public KieBase getRuleBase(String rulesetName, List<String> ruleStrings) throws Exception {
        long timer = System.currentTimeMillis();
        try {
            StringBuilder drl = new StringBuilder();
            drl.append("package drools.generated;\n\n");

            if (lastIncomingClass != null) {
                drl.append("import ").append(lastIncomingClass).append(";\n\n");
            }

            for (String ruleText : ruleStrings) {
                drl.append(ruleText).append("\n");
            }

            String drlContent = drl.toString();
            log.debug(drlContent);

            KieBase kieBase = RuleBaseFactory.getRuleBase("RuleBaseCreator:" + drlContent);
            if (kieBase != null) return kieBase;

            kieBase = DroolsHelper.buildKieBase(drlContent);
            RuleBaseFactory.putRuleBase("RuleBaseCreator:" + drlContent, kieBase);
            return kieBase;
        } catch (DroolsCompilationException e) {
            throw new Exception("Failed to compile DRL rules", e);
        } finally {
            log.debug("generateRuleBase TimeMs : " + (System.currentTimeMillis() - timer));
        }
    }

    /**
     * Generates a DRL rule string from the given parameters.
     *
     * <p>Creates a modern DRL rule definition using {@code eval()} expressions
     * for conditions, replacing the legacy Drools 2.0 XML format.</p>
     *
     * @param ruleName String the rule name
     * @param incomingClass String fully qualified class name for the fact object
     * @param conditions List of DSCondition objects defining the rule conditions
     * @param consequence String the Java code to execute when the rule fires
     * @return String the DRL rule definition text
     */
    public String getRule(String ruleName, String incomingClass, List<DSCondition> conditions, String consequence) {
        lastIncomingClass = incomingClass;
        String simpleClassName = incomingClass.substring(incomingClass.lastIndexOf('.') + 1);

        StringBuilder sb = new StringBuilder();
        sb.append("rule \"").append(ruleName).append("\"\n");
        sb.append("    when\n");
        sb.append("        m : ").append(simpleClassName).append("()\n");

        for (DSCondition cond : conditions) {
            String condText = "m." + cond.getType();
            String comparison = cond.getComparision();
            String value = cond.getValue();
            if (comparison != null && !comparison.trim().isEmpty()) {
                condText = condText + " " + comparison + " " + value;
            }
            sb.append("        eval(").append(condText).append(")\n");
        }

        sb.append("    then\n");
        sb.append("        ").append(consequence).append("\n");
        sb.append("end\n");

        String ruleText = sb.toString();
        log.debug("Return Rule: " + ruleText);
        return ruleText;
    }
}
