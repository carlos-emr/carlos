//CHECKSTYLE:OFF
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
 */


package ca.openosp.openo.encounter.oscarMeasurements.util;

import java.util.ArrayList;
import java.util.List;


import org.apache.logging.log4j.Logger;
import org.jdom2.Element;
import ca.openosp.openo.utility.MiscUtils;

/**
 * <ruleset>
 * <rule indicationColor="HIGH">
 * <condition type="doubleValue"  value="&gt;=2.0"/>
 * <condition type="isfemale"/>
 * </rule>
 * &lt;rule consequence="m.setIndicationColor(\"HIGH\");"&gt;
 * <condition type="doubleValue"  value="&gt;=2.0"/>
 * <condition type="isMale"/>
 * </rule>
 * </ruleset>
 *
 * @author jaygallagher
 */
public class TargetColour {
    private static final Logger log = MiscUtils.getLogger();

    private String additionConsequence = null;
    private String indicationColor = null;
    private List<TargetCondition> targetConditions = new ArrayList<TargetCondition>();


    public String toString() {
        return "indicationColor " + getIndicationColor();//+" strength "+strength+" text "+text+" ruleName "+ruleName+" measurement "+measurement;
    }

    public TargetColour() {

    }

    public TargetColour(Element recowarn) {
        indicationColor = recowarn.getAttributeValue("indicationColor");
        @SuppressWarnings("unchecked")
        List<Element> cond = recowarn.getChildren("condition");
        for (Element ele : cond) {
            targetConditions.add(new TargetCondition(ele));
        }
    }


    public Element getRuleBaseElement(String ruleName) {//,String measurement){

        ArrayList<DSCondition> list = new ArrayList<DSCondition>();

        String consequence = "";
        for (TargetCondition cond : getTargetConditions()) {
            cond.getRuleBaseElement(list);
        }
        if (getIndicationColor() != null) {
            consequence = "m.setIndicationColor(\"" + getIndicationColor() + "\");";
        }
        if (getAdditionConsequence() != null) {
            consequence += getAdditionConsequence();
        }

        log.debug("ruleName" + ruleName + " cond size " + getTargetConditions().size() + " list size " + list.size());

        RuleBaseCreator rcb = new RuleBaseCreator();
        Element ruleElement = rcb.getRule(ruleName, "ca.openosp.openo.encounter.oscarMeasurements.util.MeasurementDSHelper", list, consequence);

        return ruleElement;
    }


    /*
    <rule indicationColor="HIGH">
        <condition type="doubleValue"  value="&gt;=2.0"/>
        <condition type="isfemale"/>
    </rule>
     */
    public Element getFlowsheetXML() {
        Element e = new Element("rule");
        if (getIndicationColor() != null) {
            e.setAttribute("indicationColor", getIndicationColor());
        }
        for (TargetCondition cond : getTargetConditions()) {
            e.addContent(cond.getFlowsheetXML());//a cond.getFlowsheetXML();
        }
        return e;
    }

    public String getIndicationColor() {
        return indicationColor;
    }

    public void setIndicationColor(String indicationColor) {
        this.indicationColor = indicationColor;
    }

    public String getAdditionConsequence() {
        return additionConsequence;
    }

    public List<TargetCondition> getTargetConditions() {
        return targetConditions;
    }

    public void setTargetConditions(List<TargetCondition> targetConditions) {
        this.targetConditions = targetConditions;
    }

    public void setAdditionConsequence(String additionConsequence) {
        this.additionConsequence = additionConsequence;
    }

}
