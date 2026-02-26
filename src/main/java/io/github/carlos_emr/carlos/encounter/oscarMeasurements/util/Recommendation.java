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
 * Represents a clinical recommendation or warning for a measurement flowsheet item in the
 * CARLOS EMR clinical decision support system.
 *
 * <p>Recommendations are triggered when a clinical measurement meets certain conditions
 * (e.g., "A1C is overdue by 12+ months", "blood pressure not recorded in 6 months")
 * and are displayed on measurement flowsheets to prompt clinicians to take action. Each
 * recommendation has a strength level that determines how it is presented and a message
 * text that describes the clinical action needed.</p>
 *
 * <p>Instances are typically constructed by parsing {@code <recommendation>} elements
 * from flowsheet XML configuration files. Each element contains a {@code strength}
 * attribute, a {@code message} attribute, and one or more {@code <condition>} child
 * elements that are parsed into {@link RecommendationCondition} objects.</p>
 *
 * <p><b>Strength levels and their consequence methods:</b></p>
 * <ul>
 *   <li>{@code "hidden"} - Hides the measurement from the flowsheet display via
 *       {@link io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementInfo#addHidden(String, boolean)}</li>
 *   <li>{@code "warning"} - Displays a high-priority clinical warning via
 *       {@link io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementInfo#addWarning(String, String)}</li>
 *   <li>{@code "recommendation"} (or any other value) - Displays a standard clinical
 *       recommendation via
 *       {@link io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementInfo#addRecommendation(String, String)}</li>
 * </ul>
 *
 * <p>The key method {@link #getRuleBaseElement(String, String)} generates a Drools DRL
 * rule string by converting the contained {@link RecommendationCondition} objects into
 * {@link DSCondition} objects and delegating to
 * {@link RuleBaseCreator#getRule(String, String, java.util.List, String)} with
 * {@link io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementInfo MeasurementInfo}
 * as the fact class. The message text supports the {@code $NUMMONTHS} placeholder, which
 * is substituted at rule generation time with a dynamic call to
 * {@link io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementInfo#getLastDateRecordedInMonths(String)}.</p>
 *
 * @see RuleBaseCreator
 * @see DSCondition
 * @see RecommendationCondition
 * @see io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementInfo
 * @see io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementFlowSheet
 * @since 2009-02-20
 */
public class Recommendation {
    private static final Logger log = MiscUtils.getLogger();

    /**
     * The severity or display level of this recommendation.
     * Valid values are "hidden", "warning", or "recommendation".
     * Determines which consequence method is called in the generated DRL rule.
     */
    private String strength = null;

    /**
     * The human-readable message text displayed to the clinician when this recommendation fires.
     * Supports the {@code $NUMMONTHS} placeholder which is replaced with a dynamic call to
     * {@code m.getLastDateRecordedInMonths(measurement)} during DRL generation.
     */
    private String text = null;

    /**
     * The list of {@link RecommendationCondition} objects defining when this recommendation
     * should be triggered (e.g., month ranges since last recording, patient age, gender).
     */
    private List<RecommendationCondition> recommendationCondition = new ArrayList<RecommendationCondition>();

    /**
     * The unique name for the generated DRL rule, typically derived from the flowsheet
     * name and a sequence number.
     */
    private String ruleName = null;

    /**
     * The measurement type identifier (e.g., "A1C", "BP", "WT") that this recommendation
     * applies to. Used as the parameter in MeasurementInfo method calls within the
     * generated DRL consequence.
     */
    private String measurement = null;


    /**
     * Returns a string representation of this recommendation, showing its strength,
     * message text, rule name, and measurement type.
     *
     * @return String a human-readable representation of this recommendation's properties
     */
    public String toString() {
        return " strength " + strength + " text " + text + " ruleName " + ruleName + " measurement " + measurement;
    }

    /**
     * Constructs an empty {@code Recommendation} with no conditions, strength, or message set.
     */
    public Recommendation() {

    }

    /**
     * Constructs a {@code Recommendation} by parsing a {@code <recommendation>} XML element
     * from a flowsheet configuration file.
     *
     * <p>Reads the {@code strength} and {@code message} attributes and iterates over all
     * {@code <condition>} child elements, creating a {@link RecommendationCondition} for
     * each. The {@link #ruleName} and {@link #measurement} fields are not set by this
     * constructor and must be provided when calling
     * {@link #getRuleBaseElement(String, String)}.</p>
     *
     * @param recowarn Element the JDOM2 {@code <recommendation>} element containing the
     *                 strength, message attributes and condition child elements
     */
    public Recommendation(Element recowarn) {
        strength = recowarn.getAttributeValue("strength");
        text = recowarn.getAttributeValue("message");
        List<Element> cond = recowarn.getChildren("condition");
        for (Element ele : cond) {
            recommendationCondition.add(new RecommendationCondition(ele));
        }

    }

    /**
     * Constructs a {@code Recommendation} programmatically with a single month-range
     * condition.
     *
     * <p>Creates a {@link RecommendationCondition} of type "monthrange" with the
     * specified value and adds it to the condition list. This constructor is useful
     * for creating simple time-based recommendations without XML parsing.</p>
     *
     * @param measurement String the measurement type identifier (e.g., "A1C", "BP")
     * @param monthrange String the month range threshold value (e.g., "12", ">6", "3-6")
     * @param strength String the severity level ("hidden", "warning", or "recommendation")
     * @param text String the message text to display to the clinician
     */
    public Recommendation(String measurement, String monthrange, String strength, String text) {
        this.measurement = measurement;
        RecommendationCondition rec = new RecommendationCondition();
        rec.setType("monthrange");
        rec.setValue(monthrange);
        recommendationCondition.add(rec);
        this.strength = strength;
        this.text = text;
    }

    /**
     * Constructs a {@code Recommendation} by parsing a {@code <recommendation>} XML element,
     * with the rule name and measurement type provided directly.
     *
     * <p>This constructor is used when the recommendation is being loaded as part of a
     * specific measurement's flowsheet definition, where the rule name and measurement
     * type are known from the parent context.</p>
     *
     * @param recowarn Element the JDOM2 {@code <recommendation>} element containing the
     *                 strength, message attributes and condition child elements
     * @param ruleName String the unique name for the generated DRL rule
     * @param measurement String the measurement type identifier (e.g., "A1C", "BP")
     */
    public Recommendation(Element recowarn, String ruleName, String measurement) {
        strength = recowarn.getAttributeValue("strength");
        text = recowarn.getAttributeValue("message");

        this.ruleName = ruleName;
        this.measurement = measurement;

        List<Element> cond = recowarn.getChildren("condition");
        for (Element ele : cond) {
            recommendationCondition.add(new RecommendationCondition(ele));
        }

    }

    /**
     * Generates a Drools DRL rule string using the internally stored rule name and measurement.
     *
     * <p>Convenience method that delegates to {@link #getRuleBaseElement(String, String)}
     * using the {@link #ruleName} and {@link #measurement} fields set during construction.</p>
     *
     * @return String the complete DRL rule definition text
     * @see #getRuleBaseElement(String, String)
     */
    public String getRuleBaseElement() {
        return getRuleBaseElement(ruleName, measurement);
    }

    /**
     * Generates a Drools DRL rule string for this clinical recommendation.
     *
     * <p>This method converts the flowsheet XML recommendation model into a DRL rule that
     * the Drools engine can evaluate against a
     * {@link io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementInfo MeasurementInfo}
     * fact object. The generated rule, when fired, adds a recommendation, warning, or
     * hidden flag to the measurement info depending on the {@link #strength} level.</p>
     *
     * <p><b>DRL generation process:</b></p>
     * <ol>
     *   <li>Builds a list of {@link DSCondition} objects by iterating over each
     *       {@link RecommendationCondition} and calling
     *       {@link RecommendationCondition#getRuleBaseElement(java.util.ArrayList, String)},
     *       which parses the condition type and value (e.g., month ranges since last
     *       recording, patient age, gender) into DSCondition entries.</li>
     *   <li>Assembles the DRL consequence string based on the {@link #strength} level:
     *       <ul>
     *         <li>{@code "hidden"}: generates {@code m.addHidden("measurement", true);}</li>
     *         <li>{@code "warning"}: generates {@code m.addWarning("measurement", "message");}</li>
     *         <li>Any other value (default "recommendation"): generates
     *             {@code m.addRecommendation("measurement", "message");}</li>
     *       </ul>
     *       If no message text is provided, a default message is generated using
     *       {@code m.getLastDateRecordedInMonthsMsg(measurement)} to show elapsed time.</li>
     *   <li>Performs {@code $NUMMONTHS} placeholder substitution in the message text.
     *       The placeholder is replaced with a string-concatenation expression that calls
     *       {@code m.getLastDateRecordedInMonths("measurement")} at rule execution time,
     *       producing the actual number of months since the measurement was last recorded.</li>
     *   <li>Delegates to {@link RuleBaseCreator#getRule(String, String, java.util.List, String)}
     *       with the fully qualified class name of
     *       {@link io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementInfo MeasurementInfo}
     *       as the fact class. The RuleBaseCreator produces the complete DRL rule text with
     *       import, when/then blocks, and condition expressions for each DSCondition.</li>
     * </ol>
     *
     * @param ruleName String the unique name for the generated DRL rule
     * @param measurement String the measurement type identifier (e.g., "A1C", "BP")
     * @return String the complete DRL rule definition text ready for compilation by the
     *         Drools engine
     * @see RuleBaseCreator#getRule(String, String, java.util.List, String)
     * @see DSCondition
     * @see io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementInfo
     */
    public String getRuleBaseElement(String ruleName, String measurement) {

        log.debug("LOADING RULES - getRuleBaseElement" + measurement);

        // Build the list of DSCondition objects from each RecommendationCondition.
        // Each RecommendationCondition parses its type/value (e.g., "monthrange > 6",
        // "patientAge 40-65", "isMale") and appends one or more DSCondition entries.
        ArrayList<DSCondition> list = new ArrayList<DSCondition>();

        for (RecommendationCondition cond : getRecommendationCondition()) {
            cond.getRuleBaseElement(list, measurement);
        }

        // Assemble the DRL consequence string based on the strength level.
        // The consequence determines which method is called on the MeasurementInfo
        // fact object ("m") when the rule fires.
        String consequence = "";
        if (strength != null) {
            if ("hidden".equals(strength)) {
                // "hidden" strength: hide the measurement from the flowsheet entirely
                consequence = "m.addHidden(\"" + measurement + "\", true);";
            } else {
                // Determine the consequence method suffix: "Warning" or "Recommendation"
                String consequenceType = "Recommendation";
                if ("warning".equals(strength)) {
                    consequenceType = "Warning";
                }
                if (text == null || text.trim().equals("")) {
                    // No custom message text: generate a default message that includes
                    // the measurement name and elapsed months via getLastDateRecordedInMonthsMsg()
                    consequence = "m.add" + consequenceType + "(\"" + measurement + "\", \"" + measurement + "  \"+m.getLastDateRecordedInMonthsMsg(\"" + measurement + "\")+\" \");";
                } else if (text != null) {
                    String txt = text;
                    // Build the $NUMMONTHS replacement expression.
                    // This injects a string-concatenation break into the DRL consequence so that
                    // at rule execution time, m.getLastDateRecordedInMonths("measurement") is
                    // called and its integer return value is interpolated into the message string.
                    // For example, message text "Overdue by $NUMMONTHS months" becomes:
                    String NUMMONTHS = "\"+m.getLastDateRecordedInMonths(\"" + measurement + "\")+\"";
                    log.debug("TRY TO REPLACE $NUMMONTHS:" + txt.indexOf("$NUMMONTHS") + " WITH " + NUMMONTHS + " " + txt);

                    // Replace all occurrences of $NUMMONTHS in the message text
                    txt = txt.replaceAll("\\$NUMMONTHS", NUMMONTHS);
                    log.debug("TEXT " + txt);
                    consequence = "m.add" + consequenceType + "(\"" + measurement + "\", \"" + txt + "\");";
                }
            }
        }

        // Delegate to RuleBaseCreator to produce the full DRL rule text.
        // Uses MeasurementInfo FQCN as the Drools fact type bound to variable "m".
        RuleBaseCreator rcb = new RuleBaseCreator();
        String ruleText = rcb.getRule(ruleName, "io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementInfo", list, consequence);

        return ruleText;
    }

    /**
     * Returns the strength (severity level) of this recommendation.
     *
     * <p>Valid values are:</p>
     * <ul>
     *   <li>{@code "hidden"} - hides the measurement from the flowsheet</li>
     *   <li>{@code "warning"} - displays a high-priority clinical warning</li>
     *   <li>{@code "recommendation"} - displays a standard clinical recommendation</li>
     * </ul>
     *
     * @return String the strength level, or {@code null} if not set
     */
    public String getStrength() {
        return strength;
    }

    /**
     * Sets the strength (severity level) of this recommendation.
     *
     * @param strength String the severity level ("hidden", "warning", or "recommendation")
     */
    public void setStrength(String strength) {
        this.strength = strength;
    }

    /**
     * Returns the message text displayed to the clinician when this recommendation fires.
     *
     * <p>The text may contain the {@code $NUMMONTHS} placeholder, which is substituted
     * during DRL generation with a dynamic call to
     * {@code MeasurementInfo.getLastDateRecordedInMonths(measurement)}.</p>
     *
     * @return String the recommendation message text, or {@code null} if not set
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the message text displayed to the clinician when this recommendation fires.
     *
     * @param text String the recommendation message, optionally containing {@code $NUMMONTHS}
     */
    public void setText(String text) {
        this.text = text;
    }


    /**
     * Serializes this {@code Recommendation} back into a JDOM2 {@code <recommendation>}
     * XML element suitable for writing to a flowsheet configuration file.
     *
     * <p>Creates a {@code <recommendation>} element with the {@code strength} and
     * {@code message} attributes (if set) and appends each {@link RecommendationCondition}
     * as a {@code <condition>} child element via
     * {@link RecommendationCondition#getFlowsheetXML()}.</p>
     *
     * @return Element a JDOM2 {@code <recommendation>} element representing this
     *         recommendation and all its conditions
     */
    public Element getFlowsheetXML() {
        Element e = new Element("recommendation");
        if (strength != null) {
            e.setAttribute("strength", strength);
        }
        if (text != null) {
            e.setAttribute("message", text);
        }

        log.debug("Number of Conditions " + getRecommendationCondition().size());
        for (RecommendationCondition cond : getRecommendationCondition()) {
            e.addContent(cond.getFlowsheetXML());
        }

        return e;
    }

    /**
     * Returns the list of {@link RecommendationCondition} objects that define when
     * this recommendation should be triggered.
     *
     * <p>All conditions in the list must be satisfied (logical AND) for the
     * recommendation to fire.</p>
     *
     * @return List of {@link RecommendationCondition} objects defining the rule conditions
     */
    public List<RecommendationCondition> getRecommendationCondition() {
        return recommendationCondition;
    }

    /**
     * Replaces the entire list of recommendation conditions.
     *
     * @param recommendationCondition List of {@link RecommendationCondition} objects to set
     */
    public void setRecommendationCondition(List<RecommendationCondition> recommendationCondition) {
        this.recommendationCondition = recommendationCondition;
    }

}