/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.encounter.oscarMeasurements.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.kie.api.KieBase;
import io.github.carlos_emr.carlos.drools.DroolsHelper;
import io.github.carlos_emr.carlos.drools.RuleBaseFactory;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Class used to create Drools DRL rules
 *
 * @author jaygallagher
 */
public class RuleBaseCreator {
    private static final Logger log = MiscUtils.getLogger();

    public KieBase getRuleBase(String rulesetName, List<String> drlRules) throws Exception {
        long timer = System.currentTimeMillis();
        try {
            StringBuilder drl = new StringBuilder();
            drl.append("package ").append(rulesetName).append(";\n\n");
            for (String rule : drlRules) {
                drl.append(rule).append("\n\n");
            }
            String drlString = drl.toString();
            log.debug(drlString);

            KieBase kieBase = RuleBaseFactory.getRuleBase("RuleBaseCreator:" + drlString);
            if (kieBase != null) return kieBase;

            kieBase = DroolsHelper.createKieBaseFromDrl(drlString);
            RuleBaseFactory.putRuleBase("RuleBaseCreator:" + drlString, kieBase);
            return kieBase;
        } finally {
            log.debug("generateRuleBase TimeMs : " + (System.currentTimeMillis() - timer));
        }
    }

    public void test() {

        ArrayList<String> elementList = new ArrayList<String>();
        ArrayList list = new ArrayList();

        list.add(new DSCondition("getLastDateRecordedInMonths", "REBG", ">=", "3"));
        list.add(new DSCondition("getLastDateRecordedInMonths", "REBG", "<", "6"));

        String ruleText = getRule("REBG1", "io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementInfo", list, "MiscUtils.getLogger().debug(\"REBG 1 getting called\");");
        elementList.add(ruleText);

        list = new ArrayList();
        list.add(new DSCondition("getLastDateRecordedInMonths", "REBG", ">", "6"));
        ruleText = getRule("REBG2", "io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementInfo", list, "MiscUtils.getLogger().debug(\"REBG 1 getting called\");");
        elementList.add(ruleText);

        list = new ArrayList();
        list.add(new DSCondition("getLastDateRecordedInMonths", "REBG", "==", "-1"));
        ruleText = getRule("REBG3", "io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementInfo", list, "MiscUtils.getLogger().debug(\"REBG 1 getting called\");");
        elementList.add(ruleText);
    }

    public String getRule(String ruleName, String incomingClass, List<DSCondition> conditions, String consequence) {
        String simpleClassName = incomingClass.substring(incomingClass.lastIndexOf('.') + 1);
        StringBuilder rule = new StringBuilder();
        rule.append("import ").append(incomingClass).append(";\n");
        rule.append("rule \"").append(ruleName).append("\"\n");
        rule.append("    when\n");
        rule.append("        m : ").append(simpleClassName).append("()\n");
        for (DSCondition cond : conditions) {
            rule.append("        eval( m.").append(cond.getType()).append(" ").append(cond.getComparision()).append(" ").append(cond.getValue()).append(" )\n");
        }
        rule.append("    then\n");
        rule.append("        ").append(consequence).append("\n");
        rule.append("end");
        log.debug("Return Rule: {}", rule);
        return rule.toString();
    }
}
