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

import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map.Entry;

import org.kie.api.KieBase;
import io.github.carlos_emr.carlos.drools.DroolsHelper;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementFlowSheet;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.MeasurementDSHelper;
import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * Date-range measurement existence numerator for CARLOS EMR clinical reporting.
 *
 * <p>Despite its name, DroolsNumerator3 does <strong>not</strong> use the Drools
 * rule engine at all. Instead, it directly checks whether a specified measurement
 * <strong>does NOT exist</strong> within a given date range for a patient. This
 * inverted logic (returning {@code !setMeasurement}) means the numerator returns
 * {@code true} when the measurement is <strong>absent</strong> in the date range,
 * which is useful for identifying patients who are <strong>missing</strong> a
 * required measurement (e.g. "patient has not had a blood test in the last year").</p>
 *
 * <h3>Evaluation strategy</h3>
 * <ol>
 *   <li>Extract {@code "measurements"}, {@code "startDate"}, and {@code "endDate"}
 *       from the {@link #replaceableValues} map.</li>
 *   <li>Parse dates using {@link ConversionUtils#fromDateString(String, String)}
 *       with the {@code "yyyy-MM-dd"} format.</li>
 *   <li>Call {@link MeasurementDSHelper#setMeasurement(String, java.util.Date, java.util.Date)}
 *       to check if the measurement exists in the date range.</li>
 *   <li>Return the <strong>negation</strong> of {@code setMeasurement}'s result:
 *       {@code true} when the measurement is absent, {@code false} when present.</li>
 * </ol>
 *
 * <h3>Replaceable values</h3>
 * <p>This numerator requires three replaceable values to be injected before evaluation:</p>
 * <ul>
 *   <li>{@code "measurements"} -- the measurement type code (e.g. "HbA1c")</li>
 *   <li>{@code "startDate"} -- the start of the date range in {@code yyyy-MM-dd} format</li>
 *   <li>{@code "endDate"} -- the end of the date range in {@code yyyy-MM-dd} format</li>
 * </ul>
 *
 * <h3>Note on naming</h3>
 * <p>The "Drools" prefix is a historical artifact. The {@link #loadMeasurementRuleBase(String)}
 * method is inherited from the family pattern but is never called by {@link #evaluate}.</p>
 *
 * @see Numerator
 * @see MeasurementDSHelper
 * @see MeasurementFlowSheet
 * @see ConversionUtils
 * @see DroolsNumerator
 * @see DroolsNumerator4
 * @since 2006-07-28
 */
public class DroolsNumerator3 implements Numerator {

    /** Human-readable display name for this numerator. */
    String name = null;

    /** Unique identifier for this numerator within a clinical report definition. */
    String id = null;

    /**
     * DRL rule filename. Not used by this variant's {@link #evaluate} method
     * (which does not invoke the Drools engine), but retained for the
     * {@link Numerator} interface contract and structural consistency.
     */
    String file = null;

    /** Parsed output field names extracted from a comma-separated configuration string. */
    String[] outputfields = null;

    /** Key-value map of output values produced by evaluation. */
    Hashtable outputValues = null;

    /**
     * Creates a new instance of DroolsNumerator3 with all fields defaulting to {@code null}.
     */
    public DroolsNumerator3() {
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
     * Evaluates whether a specified measurement is <strong>absent</strong> for a patient
     * within a given date range.
     *
     * <p>This method does not use the Drools rule engine. It directly calls
     * {@link MeasurementDSHelper#setMeasurement(String, java.util.Date, java.util.Date)}
     * and returns the negation of the result. This means:</p>
     * <ul>
     *   <li>{@code true} -- the measurement does <strong>not</strong> exist in the
     *       date range (patient is missing the measurement)</li>
     *   <li>{@code false} -- the measurement exists in the date range</li>
     * </ul>
     *
     * <p>The following replaceable values are extracted from {@link #replaceableValues}:</p>
     * <ul>
     *   <li>{@code "measurements"} -- the measurement type code</li>
     *   <li>{@code "startDate"} -- start of the date range ({@code yyyy-MM-dd})</li>
     *   <li>{@code "endDate"} -- end of the date range ({@code yyyy-MM-dd})</li>
     * </ul>
     *
     * @param loggedInInfo LoggedInInfo the authenticated session context for the current provider
     * @param demographicNo String the patient demographic number to evaluate
     * @return boolean {@code true} if the measurement is absent in the date range,
     *         {@code false} if the measurement exists or on error
     */
    public boolean evaluate(LoggedInInfo loggedInInfo, String demographicNo) {
        boolean evalTrue = false;
        try {

            if (replaceableValues == null) {
                MiscUtils.getLogger().error("Cannot evaluate DroolsNumerator3: replaceableValues not set. Call setReplaceableValues() before evaluate().");
                return evalTrue;
            }

            // Log all replaceable values for debugging purposes
            Iterator terator = replaceableValues.entrySet().iterator();
            while (terator.hasNext()) {
                Entry en = (Entry) terator.next();
                MiscUtils.getLogger().debug("IN DROOLS3 key " + en.getKey() + " val " + en.getValue());
            }

            // Extract measurement type and date range boundaries from replaceable values
            String measurement = (String) replaceableValues.get("measurements");
            String startDate = (String) replaceableValues.get("startDate");
            String endDate = (String) replaceableValues.get("endDate");

            // Parse date strings into Date objects using yyyy-MM-dd format
            Date startDateAsDate = ConversionUtils.fromDateString(startDate, "yyyy-MM-dd");
            Date endDateAsDate = ConversionUtils.fromDateString(endDate, "yyyy-MM-dd");


            // Check if the measurement exists in the date range
            MeasurementDSHelper dshelper = new MeasurementDSHelper(loggedInInfo, demographicNo);
            boolean a = dshelper.setMeasurement(measurement, startDateAsDate, endDateAsDate);

            // Return negation: true when measurement is ABSENT (useful for gap detection)
            return !a;


        } catch (Exception e) {
            MiscUtils.getLogger().error("Failed to evaluate measurement existence", e);
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
     * into this numerator before evaluation. For DroolsNumerator3, the expected keys
     * are {@code "measurements"}, {@code "startDate"}, and {@code "endDate"}.
     */
    String[] replaceKeys = null;

    /**
     * Replaceable values map populated at runtime by the clinical report framework.
     * For DroolsNumerator3, this must contain:
     * <ul>
     *   <li>{@code "measurements"} -- the measurement type code</li>
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
     * @param str String comma-separated key names (e.g. "measurements,startDate,endDate"),
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
