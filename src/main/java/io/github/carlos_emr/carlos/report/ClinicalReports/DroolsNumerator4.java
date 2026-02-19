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


package io.github.carlos_emr.carlos.report.ClinicalReports;

import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map.Entry;

import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import io.github.carlos_emr.carlos.drools.DroolsHelper;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementFlowSheet;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.MeasurementDSHelper;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.RuleBaseCreator;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.TargetColour;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.TargetCondition;
import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * Programmatic Drools rule numerator using {@code getDataAsDouble} comparison
 * with date range filtering for CARLOS EMR clinical reporting.
 *
 * <p>DroolsNumerator4 extends the approach of {@link DroolsNumerator2} by adding
 * date range filtering. It builds DRL rules programmatically using the
 * {@link RuleBaseCreator} / {@link TargetCondition} / {@link TargetColour}
 * framework and compares the patient's measurement as a numeric double value,
 * but only considers measurements recorded within the specified
 * {@code startDate} to {@code endDate} range.</p>
 *
 * <h3>Evaluation strategy</h3>
 * <ol>
 *   <li>Extract {@code "measurements"}, {@code "value"}, {@code "startDate"},
 *       and {@code "endDate"} from the {@link #replaceableValues} map.</li>
 *   <li>Parse dates using {@link ConversionUtils#fromDateString(String, String)}
 *       with the {@code "yyyy-MM-dd"} format.</li>
 *   <li>Build a {@link TargetCondition} of type {@code "getDataAsDouble"} to
 *       perform a numeric comparison of the measurement against the threshold value.</li>
 *   <li>Wrap the condition in a {@link TargetColour} with the consequence
 *       {@code "m.setInRange(true);"} and generate DRL via
 *       {@link TargetColour#getRuleBaseElement(String)}.</li>
 *   <li>Compile the generated DRL into a {@link org.kie.api.KieBase} using
 *       {@link RuleBaseCreator#getRuleBase(String, java.util.List)}.</li>
 *   <li>Create a {@link MeasurementDSHelper} for the patient <strong>with date
 *       range filtering</strong>, insert it into a {@link org.kie.api.runtime.KieSession},
 *       fire all rules, then dispose of the session.</li>
 *   <li>Return {@link MeasurementDSHelper#isInRange()} as the pass/fail result.</li>
 * </ol>
 *
 * <h3>Replaceable values</h3>
 * <p>This numerator requires four replaceable values to be injected before evaluation:</p>
 * <ul>
 *   <li>{@code "measurements"} -- the measurement type code (e.g. "HbA1c")</li>
 *   <li>{@code "value"} -- the numeric threshold for comparison (e.g. "7.0")</li>
 *   <li>{@code "startDate"} -- the start of the date range in {@code yyyy-MM-dd} format</li>
 *   <li>{@code "endDate"} -- the end of the date range in {@code yyyy-MM-dd} format</li>
 * </ul>
 *
 * <h3>Comparison with other variants</h3>
 * <ul>
 *   <li>{@link DroolsNumerator2} -- Same {@code getDataAsDouble} comparison but without
 *       date range filtering</li>
 *   <li>{@link DroolsNumerator5} -- Same date range filtering but uses
 *       {@code isDataEqualTo} instead of {@code getDataAsDouble}</li>
 * </ul>
 *
 * <h3>Drools migration note</h3>
 * <p>Originally written for Drools 2.0, migrated to Drools 7.74.1 (KIE API).
 * The legacy {@code RuleBase} / {@code WorkingMemory} API has been replaced by
 * {@link org.kie.api.KieBase} / {@link org.kie.api.runtime.KieSession}.</p>
 *
 * @see Numerator
 * @see DroolsHelper
 * @see RuleBaseCreator
 * @see TargetColour
 * @see TargetCondition
 * @see MeasurementDSHelper
 * @see MeasurementFlowSheet
 * @see DroolsNumerator2
 * @see DroolsNumerator5
 * @since 2006-07-28
 */
public class DroolsNumerator4 implements Numerator {

    /** Human-readable display name for this numerator. */
    String name = null;

    /** Unique identifier for this numerator within a clinical report definition. */
    String id = null;

    /**
     * DRL rule filename. Not used directly by this variant's {@link #evaluate} method
     * (which builds rules programmatically), but retained for the {@link Numerator} contract
     * and available via {@link #loadMeasurementRuleBase(String)} if needed.
     */
    String file = null;

    /** Parsed output field names extracted from a comma-separated configuration string. */
    String[] outputfields = null;

    /** Key-value map of output values produced by rule evaluation. */
    Hashtable outputValues = null;

    /**
     * Creates a new instance of DroolsNumerator4 with all fields defaulting to {@code null}.
     */
    public DroolsNumerator4() {
    }

    /** Returns the unique identifier for this numerator. */
    public String getId() {
        return id;
    }

    /** Returns the human-readable display name for this numerator. */
    public String getNumeratorName() {
        return name;
    }

    /** Sets the human-readable display name for this numerator. */
    public void setNumeratorName(String name) {
        this.name = name;
    }

    /** Sets the unique identifier for this numerator. */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Evaluates a programmatically-built Drools rule for a specific patient using
     * a {@code getDataAsDouble} numeric comparison with date range filtering.
     *
     * <p>The following replaceable values are extracted from {@link #replaceableValues}:</p>
     * <ul>
     *   <li>{@code "measurements"} -- the measurement type code</li>
     *   <li>{@code "value"} -- the numeric threshold for comparison</li>
     *   <li>{@code "startDate"} -- start of the date range ({@code yyyy-MM-dd})</li>
     *   <li>{@code "endDate"} -- end of the date range ({@code yyyy-MM-dd})</li>
     * </ul>
     *
     * @param loggedInInfo LoggedInInfo the authenticated session context for the current provider
     * @param demographicNo String the patient demographic number to evaluate
     * @return boolean {@code true} if the patient's measurement within the date range
     *         satisfies the numeric comparison (i.e. is "in range"), {@code false}
     *         otherwise or on error
     */
    public boolean evaluate(LoggedInInfo loggedInInfo, String demographicNo) {
        boolean evalTrue = false;
        try {

            if (replaceableValues == null) {
                MiscUtils.getLogger().error("Cannot evaluate DroolsNumerator4: replaceableValues not set. Call setReplaceableValues() before evaluate().");
                return evalTrue;
            }

            // Log all replaceable values for debugging purposes
            Iterator terator = replaceableValues.entrySet().iterator();
            while (terator.hasNext()) {
                Entry en = (Entry) terator.next();
                MiscUtils.getLogger().debug("IN DROOLS4 key " + en.getKey() + " val " + en.getValue());
            }

            // Extract measurement type, comparison value, and date range from replaceable values
            String measurement = (String) replaceableValues.get("measurements");
            String value = (String) replaceableValues.get("value");
            String startDate = (String) replaceableValues.get("startDate");
            String endDate = (String) replaceableValues.get("endDate");

            // Parse date strings into Date objects using yyyy-MM-dd format
            Date startDateAsDate = ConversionUtils.fromDateString(startDate, "yyyy-MM-dd");
            Date endDateAsDate = ConversionUtils.fromDateString(endDate, "yyyy-MM-dd");

            // Build a TargetCondition that uses "getDataAsDouble" to compare the
            // measurement's numeric value against the configured threshold
            TargetCondition tc = new TargetCondition();
            tc.setType("getDataAsDouble");
            tc.setParam(measurement);
            tc.setValue(value);

            // Wrap the condition in a TargetColour whose consequence sets inRange=true
            TargetColour tcolour = new TargetColour();
            tcolour.setAdditionConsequence("m.setInRange(true);");
            ArrayList<TargetCondition> list = new ArrayList<TargetCondition>();
            list.add(tc);
            tcolour.setTargetConditions(list);

            // Generate the DRL rule element and compile it into a KieBase
            ArrayList<String> list2 = new ArrayList<String>();
            list2.add(tcolour.getRuleBaseElement("ClinicalRule"));
            RuleBaseCreator rcb = new RuleBaseCreator();


            KieBase kieBase = rcb.getRuleBase("rulesetName", list2);
            if (kieBase == null) {
                MiscUtils.getLogger().error("Cannot evaluate clinical rules: programmatic rule compilation failed for demographic '{}'", demographicNo);
                return evalTrue;
            }

            // Create a measurement helper WITH date range filtering
            // Only measurements within [startDate, endDate] are considered
            MeasurementDSHelper dshelper = new MeasurementDSHelper(loggedInInfo, demographicNo);
            dshelper.setMeasurement(measurement, startDateAsDate, endDateAsDate);


            // KieSession lifecycle: create session, insert fact, fire rules, dispose
            MiscUtils.getLogger().debug("newKieSession");
            KieSession kieSession = kieBase.newKieSession();
            try {
                // Insert the measurement helper as a fact into the rule engine
                MiscUtils.getLogger().debug("insert");
                kieSession.insert(dshelper);

                // Execute all matching rules; rules set dshelper.inRange if criteria met
                MiscUtils.getLogger().debug("fireAllRules");
                kieSession.fireAllRules();
            } finally {
                // Always dispose the session to free Drools engine resources
                kieSession.dispose();
            }

            // After rules fire, check whether the measurement was flagged as in-range
            evalTrue = dshelper.isInRange();

            MiscUtils.getLogger().debug("right before catch");
        } catch (Exception e) {
            // demographicNo is an internal database sequence number, not PHI
            MiscUtils.getLogger().error("Failed to evaluate Drools rules for demographic '{}'", demographicNo, e);
        }
        return evalTrue;
    }

    /** Sets the DRL rule filename. */
    public void setFile(String file) {
        this.file = file;
    }

    /** Returns the DRL rule filename. */
    public String getFile() {
        return file;
    }


    /**
     * Loads a measurement decision support DRL file using the standard two-tier strategy.
     *
     * @param string the DRL filename to load
     * @return KieBase the compiled rule base, or {@code null} if loading fails
     * @see DroolsHelper#loadMeasurementRuleBase(String, Class)
     */
    public KieBase loadMeasurementRuleBase(String string) {
        return DroolsHelper.loadMeasurementRuleBase(string, MeasurementFlowSheet.class);
    }

    /** Returns the key-value map of output values produced by evaluation. */
    public Hashtable getOutputValues() {
        return outputValues;
    }

    /**
     * Parses a comma-separated string of output field names into the {@link #outputfields} array.
     *
     * <p>If the string contains commas, it is split on commas to produce multiple fields.
     * Otherwise, the entire string is treated as a single field name.</p>
     *
     * @param str String comma-separated output field names, or {@code null} to skip parsing
     */
    public void parseOutputFields(String str) {
        if (str != null) {
            try {
                if (str.indexOf(",") != -1) {
                    outputfields = str.split(",");
                } else {
                    outputfields = new String[1];
                    outputfields[0] = str;
                }
            } catch (Exception e) {
                MiscUtils.getLogger().error("Failed to parse output fields from string '{}'", str, e);
            }
        }
    }

    /** Returns the parsed output field names. */
    public String[] getOutputFields() {
        return outputfields;
    }


    /**
     * Keys identifying which values in {@link #replaceableValues} must be injected
     * into this numerator before evaluation. For DroolsNumerator4, the expected keys
     * are {@code "measurements"}, {@code "value"}, {@code "startDate"}, and {@code "endDate"}.
     */
    String[] replaceKeys = null;

    /**
     * Replaceable values map populated at runtime by the clinical report framework.
     * For DroolsNumerator4, this must contain:
     * <ul>
     *   <li>{@code "measurements"} -- the measurement type code</li>
     *   <li>{@code "value"} -- the numeric threshold for comparison</li>
     *   <li>{@code "startDate"} -- start of the date range ({@code yyyy-MM-dd})</li>
     *   <li>{@code "endDate"} -- end of the date range ({@code yyyy-MM-dd})</li>
     * </ul>
     */
    Hashtable replaceableValues = null;

    /** Returns the keys identifying which replaceable values must be injected before evaluation. */
    public String[] getReplaceableKeys() {
        return replaceKeys;
    }

    /**
     * Parses a comma-separated string of replaceable value keys into the
     * {@link #replaceKeys} array.
     *
     * <p>These keys define which runtime parameters the clinical report framework
     * must supply via {@link #setReplaceableValues(Hashtable)} before calling
     * {@link #evaluate(LoggedInInfo, String)}.</p>
     *
     * @param str String comma-separated key names
     *            (e.g. "measurements,value,startDate,endDate"),
     *            or {@code null} to skip parsing
     */
    public void parseReplaceValues(String str) {
        if (str != null) {
            try {
                MiscUtils.getLogger().debug("parsing string " + str);
                if (str.indexOf(",") != -1) {
                    replaceKeys = str.split(",");
                } else {
                    replaceKeys = new String[1];
                    replaceKeys[0] = str;
                }
            } catch (Exception e) {
                MiscUtils.getLogger().error("Failed to parse replaceable value keys from string '{}'", str, e);
            }
        }
    }

    /**
     * Checks whether this numerator expects replaceable values to be injected
     * before evaluation.
     *
     * @return boolean {@code true} if {@link #replaceKeys} has been configured,
     *         {@code false} otherwise
     */
    public boolean hasReplaceableValues() {
        boolean repVal = false;
        if (replaceKeys != null) {
            repVal = true;
        }
        return repVal;
    }

    /** Sets the replaceable values map for runtime parameter injection. */
    public void setReplaceableValues(Hashtable vals) {
        replaceableValues = vals;
    }

    /** Returns the replaceable values map. */
    public Hashtable getReplaceableValues() {
        return replaceableValues;
    }


}
