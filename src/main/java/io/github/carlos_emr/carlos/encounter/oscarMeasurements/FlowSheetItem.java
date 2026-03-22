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


package io.github.carlos_emr.carlos.encounter.oscarMeasurements;

import java.util.List;
import java.util.Map;

import org.kie.api.KieBase;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.Recommendation;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.TargetColour;

/**
 * Represents a single item (row) on a clinical measurement flowsheet in CARLOS EMR.
 *
 * <p>A {@code FlowSheetItem} models one tracked clinical indicator within a
 * {@link MeasurementFlowSheet}, such as a lab value (e.g., A1C, blood pressure),
 * a clinical observation (e.g., BMI, foot exam), or a preventive care action
 * (e.g., flu vaccine, eye exam). Each item corresponds to an {@code <item>}
 * element in a flowsheet XML definition file (e.g., {@code diabetesFlowsheet.xml},
 * {@code hypertensionFlowsheet.xml}).</p>
 *
 * <h3>Dual Type Nature</h3>
 * <p>Every flowsheet item is either a <strong>measurement type</strong> or a
 * <strong>prevention type</strong>, but never both simultaneously:</p>
 * <ul>
 *   <li><strong>Measurement type</strong> ({@code measurement_type} XML attribute) -- tracks
 *       clinical measurements with recorded values (e.g., A1C readings, blood glucose levels).
 *       The type code (e.g., "A1C", "BP", "REBG") maps to entries in the
 *       {@code measurementType} database table.</li>
 *   <li><strong>Prevention type</strong> ({@code prevention_type} XML attribute) -- tracks
 *       preventive care actions such as immunizations (e.g., "Flu", "Pneu"). These items
 *       link to the prevention/immunization tracking subsystem rather than the measurement
 *       subsystem.</li>
 * </ul>
 *
 * <h3>Drools Decision Support Integration</h3>
 * <p>Items can optionally have Drools-based clinical decision support rules attached via
 * the {@code ds_rules} XML attribute (e.g., {@code ds_rules="diab-A1C.drl"}). When present,
 * the {@link MeasurementFlowSheet#addListItem(FlowSheetItem)} method compiles the referenced
 * {@code .drl} file into a {@link KieBase} and assigns it to this item. At runtime, the
 * rule base is used to evaluate recorded measurement values and produce colour-coded
 * clinical indicators (normal, low, high) and {@link Recommendation} objects for the
 * clinician. Items may also derive their rule base from {@link TargetColour} definitions
 * when no explicit {@code ds_rules} file is specified.</p>
 *
 * <h3>XML Configuration Example</h3>
 * <pre>{@code
 * <!-- Measurement type item with decision support rules -->
 * <item measurement_type="A1C"
 *       display_name="A1C"
 *       guideline="Target < 7.0%"
 *       graphable="yes"
 *       value_name="A1C"
 *       ds_rules="diab-A1C.drl"/>
 *
 * <!-- Measurement type item with a constrained answer -->
 * <item measurement_type="DMME"
 *       display_name="Diabetes Education"
 *       guideline="Assess and discuss self-management challenges"
 *       graphable="no"
 *       value_name="Discussed"
 *       possible_answer="Yes"/>
 *
 * <!-- Prevention type item (immunization) -->
 * <item prevention_type="Flu"
 *       display_name="Flu Vaccine"
 *       guideline="Annually"
 *       graphable="no"/>
 * }</pre>
 *
 * @see MeasurementFlowSheet
 * @see Recommendation
 * @see TargetColour
 * @see KieBase
 * @since 2006-02-08 (upstream)
 */
/**
 * Represents a single item in a clinical flowsheet, containing measurement type,
 * display properties, and decision support rules.
 *
 * @since 2001-01-01
 */
public class FlowSheetItem {

    /** Raw map of all XML attribute key-value pairs from the {@code <item>} element. */
    Map<String, String> allFields = null;

    /** Clinical measurement type code (e.g., "A1C", "BP", "BMI"), or {@code null} for prevention items. */
    private String measurementType = null;

    /** Prevention/immunization type code (e.g., "Flu", "Pneu"), or {@code null} for measurement items. */
    private String preventionType = null;

    /** Human-readable label shown in the flowsheet UI column header. */
    private String displayName = null;

    /** Clinical guideline text displayed as tooltip or reference for the clinician. */
    private String guideline = null;

    /** Whether this item's values can be rendered on a patient graph/chart. */
    private boolean graphable = false;

    /** Label for the value field shown in the data entry form (e.g., "A1C", "Reviewed", "Present"). */
    private String valueName = null;

    /** Constrained answer option for items with a fixed set of valid responses (e.g., "Yes"). */
    private String possibleAnswer = null;

    /** Clinical recommendations generated by the Drools decision support engine for this item. */
    private List<Recommendation> recommendations = null;

    /** Colour-coded target ranges used for visual clinical indicators (normal, low, high). */
    private List<TargetColour> targetColour = null;

    /** Compiled Drools rule base for evaluating clinical decision support rules against measurement values. */
    private KieBase kieBase = null;

    /** Filename of the Drools rule definition file (e.g., "diab-A1C.drl"), or {@code null} if no rules apply. */
    private String dsRulesFileName = null;

    /** Whether this item should be hidden from the flowsheet display. */
    private boolean hide = false;

    /**
     * Returns a diagnostic string representation of this flowsheet item, including
     * the measurement type, prevention type, decision support rules filename, and
     * compiled rule base reference.
     *
     * @return String a debug-oriented description of this item's key fields
     */
    @Override
    public String toString() {
        return " MEASUREMENT TYPE :" + measurementType + " PREV TYPE :" + preventionType + " dsRulesFileName :" + dsRulesFileName + " ruleBASE :" + kieBase;
    }

   /*
    <item
        measurement_type="EDGI"
        display_name="Autonomic Neuropathy"
        guideline="Erectile Dysfunction, gastrointestinal disturbance"
        graphable="no"
        value_name="Present"
        ds_rules="diab-C-yes-is-high.drl"/>
    <item
        measurement_type="DMME"
        display_name="Diabetes Education"
        guideline="Assess and discuss self-management challenges"
        graphable="no"
        value_name="Discussed"
        possible_answer="Yes"/>

    <item
        prevention_type="Flu"
        display_name="Flu Vaccine"
        guideline="Annually"
        graphable="no"/>
    */

    /**
     * Constructs an empty {@code FlowSheetItem} with all fields set to their defaults.
     *
     * <p>Fields must be populated individually via setter methods or by using the
     * {@link #FlowSheetItem(Map)} constructor instead.</p>
     */
    public FlowSheetItem() {

    }

    /**
     * Constructs a {@code FlowSheetItem} by extracting field values from a map of
     * XML attribute key-value pairs parsed from an {@code <item>} element in a
     * flowsheet definition file.
     *
     * <p>The following XML attributes are mapped to instance fields:</p>
     * <table>
     *   <caption>XML attribute to field mapping</caption>
     *   <tr><th>XML Attribute</th><th>Field</th><th>Description</th></tr>
     *   <tr><td>{@code measurement_type}</td><td>{@link #measurementType}</td>
     *       <td>Clinical measurement code (e.g., "A1C", "BP")</td></tr>
     *   <tr><td>{@code prevention_type}</td><td>{@link #preventionType}</td>
     *       <td>Prevention/immunization code (e.g., "Flu")</td></tr>
     *   <tr><td>{@code display_name}</td><td>{@link #displayName}</td>
     *       <td>Human-readable label for the flowsheet column</td></tr>
     *   <tr><td>{@code guideline}</td><td>{@link #guideline}</td>
     *       <td>Clinical guideline text for the clinician</td></tr>
     *   <tr><td>{@code graphable}</td><td>{@link #graphable}</td>
     *       <td>"yes" enables graphing; any other value or absence means not graphable</td></tr>
     *   <tr><td>{@code ds_rules}</td><td>{@link #dsRulesFileName}</td>
     *       <td>Drools rule file for clinical decision support (e.g., "diab-A1C.drl")</td></tr>
     *   <tr><td>{@code value_name}</td><td>{@link #valueName}</td>
     *       <td>Label for the measurement value input field</td></tr>
     *   <tr><td>{@code possible_answer}</td><td>{@link #possibleAnswer}</td>
     *       <td>Constrained answer option (e.g., "Yes")</td></tr>
     * </table>
     *
     * <p>Note: The {@link KieBase} rule base is not compiled here. It is set later by
     * {@link MeasurementFlowSheet#addListItem(FlowSheetItem)} after the item is added
     * to the flowsheet.</p>
     *
     * @param hashtable Map of XML attribute names to their string values, parsed from
     *                  an {@code <item>} element in a flowsheet XML definition file
     */
    public FlowSheetItem(Map<String, String> hashtable) {
        // Retain the full attribute map for later access by MeasurementFlowSheet
        allFields = hashtable;

        // Extract the item's type identity: either a measurement code or a prevention code
        measurementType = allFields.get("measurement_type");
        preventionType = allFields.get("prevention_type");

        // Extract display metadata for the flowsheet UI
        displayName = allFields.get("display_name");
        guideline = allFields.get("guideline");

        // Parse the "graphable" attribute: only the explicit value "yes" enables graphing
        String graph = allFields.get("graphable");
        if (graph != null && graph.equals("yes")) {
            graphable = true;
        }

        // Store the Drools rule filename for later compilation into a KieBase
        dsRulesFileName = allFields.get("ds_rules");

        // Extract data entry constraints for the measurement value input
        valueName = allFields.get("value_name");
        possibleAnswer = allFields.get("possible_answer");

    }

    /**
     * Returns the clinical measurement type code for this item.
     *
     * <p>The measurement type is a short code (e.g., "A1C", "BP", "BMI", "REBG")
     * that maps to a row in the {@code measurementType} database table. This value
     * is {@code null} for prevention-type items.</p>
     *
     * @return String the measurement type code, or {@code null} if this is a prevention-type item
     */
    public String getMeasurementType() {
        return measurementType;
    }

    /**
     * Returns the prevention/immunization type code for this item.
     *
     * <p>The prevention type is a short code (e.g., "Flu", "Pneu") that links to the
     * prevention/immunization tracking subsystem. This value is {@code null} for
     * measurement-type items.</p>
     *
     * @return String the prevention type code, or {@code null} if this is a measurement-type item
     */
    public String getPreventionType() {
        return preventionType;
    }

    /**
     * Returns the human-readable display name shown in the flowsheet UI.
     *
     * <p>This label appears as the column header or row label in the flowsheet view
     * (e.g., "A1C", "Review Blood Glucose Records", "Flu Vaccine").</p>
     *
     * @return String the display name for this flowsheet item
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the clinical guideline text associated with this item.
     *
     * <p>The guideline provides reference information for the clinician, such as
     * target ranges or recommended actions (e.g., "Target &lt; 7.0%",
     * "Annually", "Fasting or pre-meal glucose level 4-7").</p>
     *
     * @return String the clinical guideline text, or {@code null} if not specified
     */
    public String getGuideline() {
        return guideline;
    }

    /**
     * Returns whether this item's measurement values can be displayed on a patient graph.
     *
     * <p>Only items with numeric, time-series data (e.g., A1C, blood pressure)
     * are typically graphable. Items with categorical values (e.g., "Reviewed",
     * "Yes/No") are generally not graphable.</p>
     *
     * @return boolean {@code true} if this item supports graphical charting, {@code false} otherwise
     */
    public boolean isGraphable() {
        return graphable;
    }

    /**
     * Returns the label for the value input field in the data entry form.
     *
     * <p>This describes what value is being recorded for this measurement
     * (e.g., "A1C", "Reviewed", "Present", "Discussed").</p>
     *
     * @return String the value field label, or {@code null} if not specified
     */
    public String getValueName() {
        return valueName;
    }

    /**
     * Returns the constrained answer option for this item, if any.
     *
     * <p>When set, this restricts the valid input for the measurement to a specific
     * value (e.g., "Yes"). Used for items that have a fixed set of acceptable
     * responses rather than free-form numeric or text input.</p>
     *
     * @return String the constrained answer value, or {@code null} if free-form input is allowed
     */
    public String getPossibleAnswer() {
        return possibleAnswer;
    }

    /**
     * Returns the list of clinical recommendations generated by the Drools decision
     * support engine for this item.
     *
     * <p>Recommendations are produced when measurement values are evaluated against
     * the item's {@link KieBase} rules. They provide clinical guidance to the provider
     * based on the patient's recorded values.</p>
     *
     * @return List of {@link Recommendation} objects, or {@code null} if no recommendations
     *         have been generated
     * @see #setRecommendations(List)
     */
    public List<Recommendation> getRecommendations() {
        return recommendations;
    }

    /**
     * Sets the list of clinical recommendations for this item.
     *
     * <p>Recommendations are typically populated by the decision support engine after
     * evaluating the patient's measurement data against this item's Drools rules.</p>
     *
     * @param recommendations List of {@link Recommendation} objects to associate with this item
     * @see #getRecommendations()
     */
    public void setRecommendations(List<Recommendation> recommendations) {
        this.recommendations = recommendations;
    }

    /**
     * Returns the canonical identifier for this flowsheet item.
     *
     * <p>This is the primary key used to look up the item within a
     * {@link MeasurementFlowSheet}. For measurement-type items, it returns the
     * {@link #measurementType} code. For prevention-type items, it returns the
     * {@link #preventionType} code. Since every item must be one or the other,
     * this method always returns a non-null value for properly constructed items.</p>
     *
     * @return String the measurement type code if set, otherwise the prevention type code
     */
    public String getItemName() {
        if (measurementType != null) {
            return measurementType;
        }
        return preventionType;
    }

    /**
     * Returns the complete map of XML attribute key-value pairs from the original
     * {@code <item>} element.
     *
     * <p>This provides access to all parsed attributes, including any custom or
     * extension attributes not directly mapped to named fields. Used by
     * {@link MeasurementFlowSheet#addListItem(FlowSheetItem)} to access the
     * {@code ds_rules} attribute during rule base compilation.</p>
     *
     * @return Map of all XML attribute names to their string values, or {@code null}
     *         if this item was created via the no-argument constructor
     */
    public Map<String, String> getAllFields() {
        return allFields;
    }

    /**
     * Returns the compiled Drools rule base for clinical decision support evaluation.
     *
     * <p>The {@link KieBase} contains compiled decision support rules (from a {@code .drl}
     * file or from {@link TargetColour} definitions) that can be used to create
     * {@code KieSession} instances for evaluating patient measurement data. The rule base
     * is set by {@link MeasurementFlowSheet#addListItem(FlowSheetItem)} after the item
     * is added to the flowsheet.</p>
     *
     * @return KieBase the compiled Drools rule base, or {@code null} if no decision
     *         support rules are configured for this item
     * @see #setRuleBase(KieBase)
     */
    public KieBase getRuleBase() {
        return kieBase;
    }

    /**
     * Sets the compiled Drools rule base for this item.
     *
     * <p>Typically called by {@link MeasurementFlowSheet#addListItem(FlowSheetItem)}
     * after compiling the item's {@code ds_rules} file or {@link TargetColour}
     * definitions into a {@link KieBase}.</p>
     *
     * @param kieBase KieBase the compiled Drools rule base to assign to this item
     * @see #getRuleBase()
     */
    public void setRuleBase(KieBase kieBase) {
        this.kieBase = kieBase;
    }

    /**
     * Returns whether this item is hidden from the flowsheet display.
     *
     * <p>Hidden items are still part of the flowsheet data model but are not rendered
     * in the UI. This allows flowsheet configurations to suppress certain items
     * without removing them from the underlying definition.</p>
     *
     * @return boolean {@code true} if this item is hidden, {@code false} if visible
     * @see #setHide(boolean)
     */
    public boolean isHide() {
        return hide;
    }

    /**
     * Sets whether this item should be hidden from the flowsheet display.
     *
     * @param hide boolean {@code true} to hide this item from the UI, {@code false} to show it
     * @see #isHide()
     * @see MeasurementFlowSheet#setToHidden(String)
     */
    public void setHide(boolean hide) {
        this.hide = hide;
    }

    /**
     * Returns the list of target colour definitions for this item.
     *
     * <p>Target colours define value ranges and their associated colour-coded indicators
     * (e.g., green for normal, orange for high, red for critically high). These are used
     * both for visual display in the flowsheet and as an alternative source for Drools
     * rule base compilation when no explicit {@code ds_rules} file is specified.</p>
     *
     * @return List of {@link TargetColour} definitions, or {@code null} if no target
     *         colours are configured
     * @see #setTargetColour(List)
     */
    public List<TargetColour> getTargetColour() {
        return targetColour;
    }

    /**
     * Sets the list of target colour definitions for this item.
     *
     * <p>When target colours are set and no {@code ds_rules} file is specified,
     * {@link MeasurementFlowSheet#addListItem(FlowSheetItem)} will compile these
     * colour definitions into a {@link KieBase} for decision support evaluation.</p>
     *
     * @param targetColour List of {@link TargetColour} definitions to associate with this item
     * @see #getTargetColour()
     */
    public void setTargetColour(List<TargetColour> targetColour) {
        this.targetColour = targetColour;
    }


}
