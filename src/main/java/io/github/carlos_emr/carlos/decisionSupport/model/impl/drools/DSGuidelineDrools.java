/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
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
 *
 * Modifications by CARLOS Contributors, 2026.
 */

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
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
 * @author apavel
 */
@Entity
@DiscriminatorValue("drools")
public class DSGuidelineDrools extends DSGuideline {
    private static final Logger log = MiscUtils.getLogger();

    private static final String demographicAccessObjectClassPath = "io.github.carlos_emr.carlos.decisionSupport.model.DSDemographicAccess";

    @Transient
    private KieBase _kieBase = null;
    @Transient
    int ruleCount = 0;

    public String getRuleBaseFactoryKey() {
        if (getId() != null)
            return ("DSGuidelineDrools:" + getId());
        else
            return "DSGuidelineDrools:" + title;

    }

    public List<DSConsequence> evaluate(LoggedInInfo loggedInInfo, String demographicNo) throws DecisionSupportException {
        if (_kieBase == null) generateRuleBase();
        //at this point _kieBase WILL be set or exception is thrown in generateRuleBase()
        KieSession kieSession = _kieBase.newKieSession();
        try {
            DSDemographicAccess dsDemographicAccess = new DSDemographicAccess(loggedInInfo, demographicNo);
            //put "bob" in working memory
            try {

                kieSession.insert(dsDemographicAccess);

                for (DSCondition dsc : this.getConditions()) {
                    if (dsc.getParam() != null && !dsc.getParam().isEmpty()) {
                        log.debug("PARAM:" + dsc.getParam().toString());
                        kieSession.insert(dsc.getParam());
                    }
                }

                List<DSParameter> lDSP = this.getParameters();
                if (lDSP != null) {
                    for (DSParameter dsp : lDSP) {
                        Class clas = Class.forName(dsp.getStrClass());
                        Constructor constructor = clas.getConstructor();
                        Object obj = constructor.newInstance();

                        kieSession.insert(obj);
                    }
                }

                kieSession.fireAllRules();
                if (dsDemographicAccess.isPassedGuideline()) {
                    List<DSConsequence> returnDsConsequences = new ArrayList<DSConsequence>();
                    if (this.getConsequences() == null) return returnDsConsequences;
                    else {
                        for (DSConsequence dsConsequence : this.getConsequences()) {
                            if (dsConsequence.getConsequenceType() != DSConsequence.ConsequenceType.java) {
                                returnDsConsequences.add(dsConsequence);
                            } else if (dsConsequence.getConsequenceType() == DSConsequence.ConsequenceType.java) {
                                @SuppressWarnings("unchecked")
                                List<Object> javaConsequences = new ArrayList<>(kieSession.getObjects());
                                dsConsequence.setObjConsequence(javaConsequences);
                                returnDsConsequences.add(dsConsequence);
                            }
                        }
                        return returnDsConsequences;
                    }
                } else {
                    return null;
                }
            } catch (RuntimeException factException) {
                throw new DecisionSupportException("Unable to assert guideline", factException);
            } catch (ClassNotFoundException e) {
                throw new DecisionSupportException("Unable to instantiate class", e);
            } catch (NoSuchMethodException e) {
                throw new DecisionSupportException("Unable to instantiate class", e);
            } catch (InstantiationException e) {
                throw new DecisionSupportException("Unable to instantiate class", e);
            } catch (IllegalAccessException e) {
                throw new DecisionSupportException("Unable to instantiate class", e);
            } catch (InvocationTargetException e) {
                throw new DecisionSupportException("Unable to instantiate class", e);
            }
        } finally {
            kieSession.dispose();
        }
    }

    public List<DSConsequence> evaluate(LoggedInInfo loggedInInfo, String demographicNo, String providerNo) throws DecisionSupportException {
        if (_kieBase == null) generateRuleBase();
        //at this point _kieBase WILL be set or exception is thrown in generateRuleBase()
        KieSession kieSession = _kieBase.newKieSession();
        try {
            DSDemographicAccess dsDemographicAccess = new DSDemographicAccess(loggedInInfo, demographicNo, providerNo);
            //put "bob" in working memory
            try {

                kieSession.insert(dsDemographicAccess);

                for (DSCondition dsc : this.getConditions()) {
                    if (dsc.getParam() != null && !dsc.getParam().isEmpty()) {
                        log.debug("PARAM:" + dsc.getParam().toString());
                        kieSession.insert(dsc.getParam());
                    }
                }

                List<DSParameter> lDSP = this.getParameters();
                if (lDSP != null) {
                    for (DSParameter dsp : lDSP) {
                        Class clas = Class.forName(dsp.getStrClass());
                        Constructor constructor = clas.getConstructor();
                        Object obj = constructor.newInstance();

                        kieSession.insert(obj);
                    }
                }

                kieSession.fireAllRules();
                if (dsDemographicAccess.isPassedGuideline()) {
                    List<DSConsequence> returnDsConsequences = new ArrayList<DSConsequence>();
                    if (this.getConsequences() == null) return returnDsConsequences;
                    else {
                        for (DSConsequence dsConsequence : this.getConsequences()) {
                            if (dsConsequence.getConsequenceType() != DSConsequence.ConsequenceType.java) {
                                returnDsConsequences.add(dsConsequence);
                            } else if (dsConsequence.getConsequenceType() == DSConsequence.ConsequenceType.java) {
                                @SuppressWarnings("unchecked")
                                List<Object> javaConsequences = new ArrayList<>(kieSession.getObjects());
                                dsConsequence.setObjConsequence(javaConsequences);
                                returnDsConsequences.add(dsConsequence);
                            }
                        }
                        return returnDsConsequences;
                    }
                } else {
                    return null;
                }
            } catch (RuntimeException factException) {
                throw new DecisionSupportException("Unable to assert guideline", factException);
            } catch (ClassNotFoundException e) {
                throw new DecisionSupportException("Unable to instantiate class", e);
            } catch (NoSuchMethodException e) {
                throw new DecisionSupportException("Unable to instantiate class", e);
            } catch (InstantiationException e) {
                throw new DecisionSupportException("Unable to instantiate class", e);
            } catch (IllegalAccessException e) {
                throw new DecisionSupportException("Unable to instantiate class", e);
            } catch (InvocationTargetException e) {
                throw new DecisionSupportException("Unable to instantiate class", e);
            }
        } finally {
            kieSession.dispose();
        }
    }

    public List<DSConsequence> evaluate(LoggedInInfo loggedInInfo, String demographicNo, String providerNo, List<Object> dynamicArgs) throws DecisionSupportException {
        if (_kieBase == null) generateRuleBase();
        //at this point _kieBase WILL be set or exception is thrown in generateRuleBase()
        KieSession kieSession = _kieBase.newKieSession();
        try {
            DSDemographicAccess dsDemographicAccess = new DSDemographicAccess(loggedInInfo, demographicNo, providerNo, dynamicArgs);
            //put "bob" in working memory
            try {

                kieSession.insert(dsDemographicAccess);

                for (DSCondition dsc : this.getConditions()) {
                    if (dsc.getParam() != null && !dsc.getParam().isEmpty()) {
                        log.debug("PARAM:" + dsc.getParam().toString());
                        kieSession.insert(dsc.getParam());
                    }
                }

                List<DSParameter> lDSP = this.getParameters();
                if (lDSP != null) {
                    for (DSParameter dsp : lDSP) {
                        Class clas = Class.forName(dsp.getStrClass());
                        Constructor constructor = clas.getConstructor();
                        Object obj = constructor.newInstance();

                        kieSession.insert(obj);
                    }
                }

                kieSession.fireAllRules();
                if (dsDemographicAccess.isPassedGuideline()) {
                    List<DSConsequence> returnDsConsequences = new ArrayList<DSConsequence>();
                    if (this.getConsequences() == null) return returnDsConsequences;
                    else {
                        for (DSConsequence dsConsequence : this.getConsequences()) {
                            if (dsConsequence.getConsequenceType() != DSConsequence.ConsequenceType.java) {
                                returnDsConsequences.add(dsConsequence);
                            } else if (dsConsequence.getConsequenceType() == DSConsequence.ConsequenceType.java) {
                                @SuppressWarnings("unchecked")
                                List<Object> javaConsequences = new ArrayList<>(kieSession.getObjects());
                                dsConsequence.setObjConsequence(javaConsequences);
                                returnDsConsequences.add(dsConsequence);
                            }
                        }
                        return returnDsConsequences;
                    }
                } else {
                    return null;
                }
            } catch (RuntimeException factException) {
                throw new DecisionSupportException("Unable to assert guideline", factException);
            } catch (ClassNotFoundException e) {
                throw new DecisionSupportException("Unable to instantiate class", e);
            } catch (NoSuchMethodException e) {
                throw new DecisionSupportException("Unable to instantiate class", e);
            } catch (InstantiationException e) {
                throw new DecisionSupportException("Unable to instantiate class", e);
            } catch (IllegalAccessException e) {
                throw new DecisionSupportException("Unable to instantiate class", e);
            } catch (InvocationTargetException e) {
                throw new DecisionSupportException("Unable to instantiate class", e);
            }
        } finally {
            kieSession.dispose();
        }
    }

    public void generateRuleBase() throws DecisionSupportException {
        long timer = System.currentTimeMillis();
        try {
            String ruleBaseFactoryKey = getRuleBaseFactoryKey();

            KieBase result = RuleBaseFactory.getRuleBase(ruleBaseFactoryKey);
            if (result != null) {
                _kieBase = result;
                return;
            }

            ArrayList<String> rules = new ArrayList<String>();
            ArrayList<String> conditionElements = new ArrayList<String>();
            ArrayList<String> lParameterElements = new ArrayList<String>();

            if (this.getParameters() != null) {
                for (DSParameter dsParameter : this.getParameters()) {
                    String parameterElement = this.getDroolsParameter(dsParameter);
                    lParameterElements.add(parameterElement);
                }
            }
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

    private String getRule(List<String> conditionElements, List<String> parameterElements, String consequenceElement, int ruleCount) {
        StringBuilder rule = new StringBuilder();
        String ruleName = getRuleBaseFactoryKey() + "." + ruleCount;
        rule.append("rule \"").append(ruleName).append("\"\n");
        rule.append("    when\n");

        // Primary access class parameter binding
        rule.append("        a : ").append(demographicAccessObjectClassPath).append("()\n");

        // Additional parameter bindings
        for (String paramElement : parameterElements) {
            rule.append(paramElement).append("\n");
        }

        // Parameter bindings for conditions with params (Hashtable)
        for (DSCondition condition : this.getConditions()) {
            if (condition.getParam() != null && !condition.getParam().isEmpty()) {
                rule.append("        ").append(condition.getLabel()).append(" : java.util.Hashtable()\n");
            }
        }

        // Condition eval() clauses
        for (String conditionElement : conditionElements) {
            rule.append(conditionElement).append("\n");
        }

        rule.append("    then\n");
        rule.append("        ").append(consequenceElement).append("\n");
        rule.append("end");
        return rule.toString();
    }

    private String getRule(List<String> conditionElements, String consequenceElement, int ruleCount) {
        StringBuilder rule = new StringBuilder();
        String ruleName = getRuleBaseFactoryKey() + "." + ruleCount;
        rule.append("rule \"").append(ruleName).append("\"\n");
        rule.append("    when\n");

        // Primary access class parameter binding
        rule.append("        a : ").append(demographicAccessObjectClassPath).append("()\n");

        // Parameter bindings for conditions with params (Hashtable)
        for (DSCondition condition : this.getConditions()) {
            if (condition.getParam() != null && !condition.getParam().isEmpty()) {
                rule.append("        ").append(condition.getLabel()).append(" : java.util.Hashtable()\n");
            }
        }

        // Condition eval() clauses
        for (String conditionElement : conditionElements) {
            rule.append(conditionElement).append("\n");
        }

        rule.append("    then\n");
        rule.append("        ").append(consequenceElement).append("\n");
        rule.append("end");
        return rule.toString();
    }

    protected String getRule(String conditionElement, String consequenceElement, int ruleCounter) {
        List<String> conditionElements = new ArrayList<String>();
        conditionElements.add(conditionElement);
        return getRule(conditionElements, consequenceElement, ruleCounter);
    }

    //multiple conditions because to handle OR statements, need to have multiple
    public String getDroolsCondition(DSCondition condition) {
        String accessMethod = condition.getConditionType().getAccessMethod();
        String parameters = "\"" + StringUtils.join(condition.getValues(), ",") + "\"";
        accessMethod = accessMethod + StringUtils.capitalize(condition.getListOperator().name());
        String functionStr = "a." + accessMethod + "(" + parameters;
        if (condition.getParam() != null && !condition.getParam().isEmpty()) {
            functionStr += "," + condition.getLabel();
        }
        functionStr += ")";

        return "        eval( " + functionStr + " )";
    }

    public String getDroolsParameter(DSParameter dsParameter) {
        return "        " + dsParameter.getStrAlias() + " : " + dsParameter.getStrClass() + "()";
    }

    public String getDroolsConsequences(List<DSConsequence> consequences) {
        String consequencesStr = "a.setPassedGuideline(true);";
        for (DSConsequence consequence : consequences) {
            if (consequence.getConsequenceType() == DSConsequence.ConsequenceType.java) {
                consequencesStr = consequencesStr + "\n        " + consequence.getText();
            }
        }
        return consequencesStr;
    }

    @PostUpdate
    public void afterSave() {
        RuleBaseFactory.removeRuleBase(getRuleBaseFactoryKey());
    }
}
