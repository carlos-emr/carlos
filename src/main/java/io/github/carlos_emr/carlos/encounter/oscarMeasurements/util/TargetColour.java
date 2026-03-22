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


package io.github.carlos_emr.carlos.encounter.oscarMeasurements.util;

import java.util.ArrayList;
import java.util.List;


import org.apache.logging.log4j.Logger;
import org.jdom2.Element;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Represents a colour-coded target range for a clinical measurement in the CARLOS EMR
 * clinical decision support system.
 *
 * <p>Each {@code TargetColour} maps a set of clinical measurement conditions (thresholds,
 * gender filters, value comparisons) to a visual indication colour. For example, a blood
 * pressure reading above 140/90 might be coloured "HIGH" (red), while a reading in the
 * normal range might be coloured "NORMAL" (green). These colour-coded targets are displayed
 * on measurement flowsheets to give clinicians an at-a-glance assessment of a patient's
 * clinical values.</p>
 *
 * <p>Instances are typically constructed by parsing {@code <rule>} elements from
 * flowsheet XML configuration files. Each {@code <rule>} element contains an
 * {@code indicationColor} attribute and one or more {@code <condition>} child elements
 * that are parsed into {@link TargetCondition} objects.</p>
 *
 * <p><b>Flowsheet XML example:</b></p>
 * <pre>{@code
 * <ruleset>
 *   <rule indicationColor="HIGH">
 *     <condition type="doubleValue" value=">=2.0"/>
 *     <condition type="isfemale"/>
 *   </rule>
 *   <rule indicationColor="HIGH">
 *     <condition type="doubleValue" value=">=2.0"/>
 *     <condition type="isMale"/>
 *   </rule>
 * </ruleset>
 * }</pre>
 *
 * <p>The key method {@link #getRuleBaseElement(String)} generates a Drools DRL rule string
 * by converting the contained {@link TargetCondition} objects into {@link DSCondition}
 * objects and delegating to {@link RuleBaseCreator#getRule(String, String, java.util.List, String)}
 * with {@link MeasurementDSHelper} as the fact class. When the generated rule fires in
 * the Drools engine, the consequence calls
 * {@link MeasurementDSHelper#setIndicationColor(String)} to set the colour on the
 * measurement display.</p>
 *
 * @see RuleBaseCreator
 * @see DSCondition
 * @see TargetCondition
 * @see MeasurementDSHelper
 * @see io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementFlowSheet
 * @since 2009-02-20
 */
/**
 * Generates Drools DRL rules from flowsheet XML for color-coded measurement targets.
 * Evaluates patient measurements against configured ranges to assign visual
 * indicators (green, yellow, red) in flowsheet displays.
 *
 * @since 2001-01-01
 */
public class TargetColour {
    private static final Logger log = MiscUtils.getLogger();

    /**
     * Optional additional DRL consequence code appended after the colour-setting statement.
     * Allows flowsheet definitions to inject extra actions when the rule fires.
     */
    private String additionConsequence = null;

    /**
     * The colour label to apply when all conditions match (e.g., "HIGH", "LOW", "NORMAL").
     * This value is passed to {@link MeasurementDSHelper#setIndicationColor(String)} in the
     * generated DRL rule consequence.
     */
    private String indicationColor = null;

    /**
     * The list of {@link TargetCondition} objects defining the thresholds and filters
     * (numeric ranges, gender checks, value comparisons) that must all be satisfied
     * for this colour to apply.
     */
    private List<TargetCondition> targetConditions = new ArrayList<TargetCondition>();


    /**
     * Returns a string representation of this target colour, showing the indication colour label.
     *
     * @return String a human-readable representation containing the indication colour value
     */
    public String toString() {
        return "indicationColor " + getIndicationColor();
    }

    /**
     * Constructs an empty {@code TargetColour} with no conditions or colour set.
     */
    public TargetColour() {

    }

    /**
     * Constructs a {@code TargetColour} by parsing a {@code <rule>} XML element from
     * a flowsheet configuration file.
     *
     * <p>Reads the {@code indicationColor} attribute and iterates over all
     * {@code <condition>} child elements, creating a {@link TargetCondition} for each.</p>
     *
     * @param recowarn Element the JDOM2 {@code <rule>} element containing the colour
     *                 attribute and condition child elements
     */
    public TargetColour(Element recowarn) {
        indicationColor = recowarn.getAttributeValue("indicationColor");
        @SuppressWarnings("unchecked")
        List<Element> cond = recowarn.getChildren("condition");
        for (Element ele : cond) {
            targetConditions.add(new TargetCondition(ele));
        }
    }


    /**
     * Generates a Drools DRL rule string for this target colour.
     *
     * <p>This method converts the flowsheet XML condition model into a DRL rule that
     * the Drools engine can evaluate against a {@link MeasurementDSHelper} fact object.
     * The generated rule, when fired, sets the indication colour on the measurement
     * display and optionally executes additional consequence code.</p>
     *
     * <p><b>DRL generation process:</b></p>
     * <ol>
     *   <li>Builds a list of {@link DSCondition} objects by iterating over each
     *       {@link TargetCondition} and calling
     *       {@link TargetCondition#getRuleBaseElement(java.util.ArrayList)}, which
     *       parses the condition type and value (e.g., {@code getDataAsDouble >= 2.0},
     *       {@code isMale == true}) into DSCondition entries.</li>
     *   <li>Assembles the DRL consequence string: if {@link #indicationColor} is set,
     *       creates {@code m.setIndicationColor("HIGH");} (or whichever colour label).
     *       If {@link #additionConsequence} is also set, it is appended to allow
     *       additional actions when the rule fires.</li>
     *   <li>Delegates to {@link RuleBaseCreator#getRule(String, String, java.util.List, String)}
     *       with the fully qualified class name of {@link MeasurementDSHelper} as the
     *       fact class. The RuleBaseCreator produces the complete DRL rule text with
     *       import, when/then blocks, and condition expressions for each DSCondition.</li>
     * </ol>
     *
     * @param ruleName String the unique name for the generated DRL rule (typically includes
     *                 the measurement type and a sequence number)
     * @return String the complete DRL rule definition text ready for compilation by the
     *         Drools engine
     * @see RuleBaseCreator#getRule(String, String, java.util.List, String)
     * @see DSCondition
     * @see MeasurementDSHelper
     */
    public String getRuleBaseElement(String ruleName) {

        // Build the list of DSCondition objects from each TargetCondition.
        // Each TargetCondition parses its type/value (e.g., "getDataAsDouble >= 2.0")
        // and appends one or more DSCondition entries to the list.
        ArrayList<DSCondition> list = new ArrayList<DSCondition>();

        // Assemble the DRL consequence (the "then" block of the rule)
        String consequence = "";
        for (TargetCondition cond : getTargetConditions()) {
            cond.getRuleBaseElement(list);
        }

        // Primary consequence: set the indication colour on the MeasurementDSHelper fact
        if (getIndicationColor() != null) {
            consequence = "m.setIndicationColor(\"" + getIndicationColor() + "\");";
        }

        // Append any additional consequence code (e.g., extra actions defined by the flowsheet)
        if (getAdditionConsequence() != null) {
            consequence += getAdditionConsequence();
        }

        log.debug("ruleName" + ruleName + " cond size " + getTargetConditions().size() + " list size " + list.size());

        // Delegate to RuleBaseCreator to produce the full DRL rule text.
        // Uses MeasurementDSHelper FQCN as the Drools fact type bound to variable "m".
        RuleBaseCreator rcb = new RuleBaseCreator();
        String ruleText = rcb.getRule(ruleName, "io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.MeasurementDSHelper", list, consequence);

        return ruleText;
    }


    /**
     * Serializes this {@code TargetColour} back into a JDOM2 {@code <rule>} XML element
     * suitable for writing to a flowsheet configuration file.
     *
     * <p>Creates a {@code <rule>} element with the {@code indicationColor} attribute
     * (if set) and appends each {@link TargetCondition} as a {@code <condition>} child
     * element via {@link TargetCondition#getFlowsheetXML()}.</p>
     *
     * <p><b>Example output:</b></p>
     * <pre>{@code
     * <rule indicationColor="HIGH">
     *   <condition type="doubleValue" value=">=2.0"/>
     *   <condition type="isfemale"/>
     * </rule>
     * }</pre>
     *
     * @return Element a JDOM2 {@code <rule>} element representing this target colour
     *         and all its conditions
     */
    public Element getFlowsheetXML() {
        Element e = new Element("rule");
        if (getIndicationColor() != null) {
            e.setAttribute("indicationColor", getIndicationColor());
        }
        for (TargetCondition cond : getTargetConditions()) {
            e.addContent(cond.getFlowsheetXML()); //a cond.getFlowsheetXML();
        }
        return e;
    }

    /**
     * Returns the indication colour label for this target range.
     *
     * <p>Common values include "HIGH", "LOW", "NORMAL", and other colour codes
     * defined in the flowsheet XML configuration.</p>
     *
     * @return String the indication colour label, or {@code null} if not set
     */
    public String getIndicationColor() {
        return indicationColor;
    }

    /**
     * Sets the indication colour label for this target range.
     *
     * @param indicationColor String the colour label (e.g., "HIGH", "LOW", "NORMAL")
     */
    public void setIndicationColor(String indicationColor) {
        this.indicationColor = indicationColor;
    }

    /**
     * Returns any additional DRL consequence code to be appended after the
     * colour-setting statement in the generated rule.
     *
     * @return String the additional consequence code, or {@code null} if none is set
     */
    public String getAdditionConsequence() {
        return additionConsequence;
    }

    /**
     * Returns the list of {@link TargetCondition} objects that define the measurement
     * thresholds and filters for this target colour.
     *
     * <p>All conditions in the list must be satisfied (logical AND) for the
     * indication colour to be applied.</p>
     *
     * @return List of {@link TargetCondition} objects defining the rule conditions
     */
    public List<TargetCondition> getTargetConditions() {
        return targetConditions;
    }

    /**
     * Replaces the entire list of target conditions for this colour rule.
     *
     * @param targetConditions List of {@link TargetCondition} objects to set
     */
    public void setTargetConditions(List<TargetCondition> targetConditions) {
        this.targetConditions = targetConditions;
    }

    /**
     * Sets additional DRL consequence code to be appended after the colour-setting
     * statement when the generated rule fires.
     *
     * @param additionConsequence String additional Java code for the DRL consequence block
     */
    public void setAdditionConsequence(String additionConsequence) {
        this.additionConsequence = additionConsequence;
    }

}
