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
 * Originally written for the Department of Family Medicine, McMaster University.
 * Now maintained by the CARLOS EMR Project.
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
                            List<Object> javaConsequences = (List<Object>) new ArrayList<>(kieSession.getObjects());
                            dsConsequence.setObjConsequence(javaConsequences);
                            returnDsConsequences.add(dsConsequence);
                        }
                    }
                    return returnDsConsequences;
                }
            } else {
                return null;
            }
        } catch (RuntimeException runtimeException) {
            throw new DecisionSupportException("Unable to assert guideline", runtimeException);
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
        } finally {
            kieSession.dispose();
        }
    }

    public List<DSConsequence> evaluate(LoggedInInfo loggedInInfo, String demographicNo, String providerNo) throws DecisionSupportException {
        if (_kieBase == null) generateRuleBase();
        //at this point _kieBase WILL be set or exception is thrown in generateRuleBase()
        KieSession kieSession = _kieBase.newKieSession();
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
                            List<Object> javaConsequences = (List<Object>) new ArrayList<>(kieSession.getObjects());
                            dsConsequence.setObjConsequence(javaConsequences);
                            returnDsConsequences.add(dsConsequence);
                        }
                    }
                    return returnDsConsequences;
                }
            } else {
                return null;
            }
        } catch (RuntimeException runtimeException) {
            throw new DecisionSupportException("Unable to assert guideline", runtimeException);
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
        } finally {
            kieSession.dispose();
        }
    }

    public List<DSConsequence> evaluate(LoggedInInfo loggedInInfo, String demographicNo, String providerNo, List<Object> dynamicArgs) throws DecisionSupportException {
        if (_kieBase == null) generateRuleBase();
        //at this point _kieBase WILL be set or exception is thrown in generateRuleBase()
        KieSession kieSession = _kieBase.newKieSession();
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
                            List<Object> javaConsequences = (List<Object>) new ArrayList<>(kieSession.getObjects());
                            dsConsequence.setObjConsequence(javaConsequences);
                            returnDsConsequences.add(dsConsequence);
                        }
                    }
                    return returnDsConsequences;
                }
            } else {
                return null;
            }
        } catch (RuntimeException runtimeException) {
            throw new DecisionSupportException("Unable to assert guideline", runtimeException);
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
            ArrayList<String> conditionStrings = new ArrayList<String>();
            ArrayList<String> lParameterStrings = new ArrayList<String>();

            if (this.getParameters() != null) {
                for (DSParameter dsParameter : this.getParameters()) {
                    String parameterString = this.getDroolsParameter(dsParameter);
                    lParameterStrings.add(parameterString);
                }
            }
            int paramCount = 0;

            for (DSCondition condition : this.getConditions()) {
                if (condition.getParam() != null && !condition.getParam().isEmpty()) {
                    condition.setLabel("param" + paramCount);
                    paramCount++;
                }
                String conditionString = getDroolsCondition(condition);
                conditionStrings.add(conditionString);
            }

            String consequencesString = this.getDroolsConsequences(this.getConsequences());

            rules.add(this.getRule(conditionStrings, lParameterStrings, consequencesString, ruleCount++));

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

    private String getRule(List<String> conditionStrings, List<String> parameterStrings, String consequenceString, int ruleCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("rule \"").append(getRuleBaseFactoryKey()).append(".").append(ruleCount).append("\"\n");
        sb.append("    when\n");
        sb.append("        a : ").append("DSDemographicAccess").append("()\n");

        for (String param : parameterStrings) {
            sb.append("        ").append(param).append("\n");
        }

        for (DSCondition condition : this.getConditions()) {
            if (condition.getParam() != null && !condition.getParam().isEmpty()) {
                sb.append("        ").append(condition.getLabel()).append(" : java.util.Hashtable()\n");
            }
        }

        for (String cond : conditionStrings) {
            sb.append("        eval(").append(cond).append(")\n");
        }

        sb.append("    then\n");
        sb.append("        ").append(consequenceString).append("\n");
        sb.append("end\n");
        return sb.toString();
    }

    private String getRule(List<String> conditionStrings, String consequenceString, int ruleCount) {
        return getRule(conditionStrings, new ArrayList<String>(), consequenceString, ruleCount);
    }

    protected String getRule(String conditionString, String consequenceString, int ruleCounter) {
        List<String> conditionStrings = new ArrayList<String>();
        conditionStrings.add(conditionString);
        return getRule(conditionStrings, consequenceString, ruleCounter);
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

        return functionStr;
    }

    public String getDroolsParameter(DSParameter dsParameter) {
        return dsParameter.getStrAlias() + " : " + dsParameter.getStrClass() + "()";
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
