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

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.util.Hashtable;

import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import io.github.carlos_emr.carlos.drools.DroolsHelper;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementFlowSheet;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.MeasurementDSHelper;

/**
 * File-based Drools rule numerator for CARLOS EMR clinical reporting.
 *
 * <p>This is the simplest variant in the DroolsNumerator family. It loads a
 * pre-authored DRL (Drools Rule Language) file from disk or the classpath and
 * evaluates it against a patient's measurements with <strong>no date range
 * filtering</strong>. The most recent measurement value for the patient is used.</p>
 *
 * <h3>Evaluation strategy</h3>
 * <ol>
 *   <li>Load a compiled {@link org.kie.api.KieBase} from the DRL file specified
 *       by {@link #file} (see {@link #loadMeasurementRuleBase(String)} for the
 *       two-tier file resolution priority).</li>
 *   <li>Create a {@link MeasurementDSHelper} for the patient (no date range).</li>
 *   <li>Insert the helper into a new {@link org.kie.api.runtime.KieSession},
 *       fire all rules, then dispose of the session.</li>
 *   <li>Return {@link MeasurementDSHelper#isInRange()} as the pass/fail result.</li>
 * </ol>
 *
 * <h3>Drools migration note</h3>
 * <p>Originally written for Drools 2.0, migrated to Drools 7.74.1 (KIE API).
 * The legacy {@code RuleBase} / {@code WorkingMemory} API has been replaced by
 * {@link org.kie.api.KieBase} / {@link org.kie.api.runtime.KieSession}.</p>
 *
 * @see Numerator
 * @see DroolsHelper
 * @see MeasurementDSHelper
 * @see MeasurementFlowSheet
 * @see DroolsNumerator2
 * @see DroolsNumerator3
 * @see DroolsNumerator4
 * @see DroolsNumerator5
 * @since 2006-06-17
 */
public class DroolsNumerator implements Numerator {

    /** Human-readable display name for this numerator (e.g. "Blood Pressure Check"). */
    String name = null;

    /** Unique identifier for this numerator within a clinical report definition. */
    String id = null;

    /** Filename of the DRL rule file to load (e.g. "bp_check.drl"). */
    String file = null;

    /** Parsed output field names extracted from a comma-separated configuration string. */
    String[] outputfields = null;

    /** Key-value map of output values produced by rule evaluation. */
    Hashtable outputValues = null;

    /**
     * Creates a new instance of DroolsNumerator with all fields defaulting to {@code null}.
     */
    public DroolsNumerator() {
    }

    /**
     * Returns the unique identifier for this numerator.
     *
     * @return String the numerator identifier, or {@code null} if not set
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the human-readable display name of this numerator.
     *
     * @return String the numerator name, or {@code null} if not set
     */
    public String getNumeratorName() {
        return name;
    }

    /**
     * Sets the human-readable display name of this numerator.
     *
     * @param name String the display name to assign
     */
    public void setNumeratorName(String name) {
        this.name = name;
    }

    /**
     * Sets the unique identifier for this numerator.
     *
     * @param id String the identifier to assign
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Evaluates the clinical rule for a specific patient by loading a DRL file
     * and running it against the patient's most recent measurement data.
     *
     * <p>No date range is applied; the {@link MeasurementDSHelper} retrieves
     * the latest measurement value regardless of when it was recorded.</p>
     *
     * @param loggedInInfo LoggedInInfo the authenticated session context for the current provider
     * @param demographicNo String the patient demographic number to evaluate
     * @return boolean {@code true} if the patient's measurement satisfies the rule
     *         (i.e. is "in range"), {@code false} otherwise or on error
     */
    public boolean evaluate(LoggedInInfo loggedInInfo, String demographicNo) {
        boolean evalTrue = false;
        try {
            // Load the DRL rule file into a compiled KieBase
            MiscUtils.getLogger().debug("going to load " + file);
            KieBase kieBase = loadMeasurementRuleBase(file);

            // Create a measurement helper for the patient with no date range filtering
            MeasurementDSHelper dshelper = new MeasurementDSHelper(loggedInInfo, demographicNo);

            // KieSession lifecycle: create session, insert fact, fire rules, dispose
            MiscUtils.getLogger().debug("new working mem");
            KieSession kieSession = kieBase.newKieSession();
            try {
                // Insert the measurement helper as a fact into the rule engine
                MiscUtils.getLogger().debug("assertObject");
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
            MiscUtils.getLogger().error("Error", e);
        }
        return evalTrue;
    }

    /**
     * Sets the DRL rule filename used for evaluation.
     *
     * @param file String the filename (not full path) of the DRL file
     */
    public void setFile(String file) {
        this.file = file;
    }

    /**
     * Returns the DRL rule filename used for evaluation.
     *
     * @return String the DRL filename, or {@code null} if not set
     */
    public String getFile() {
        return file;
    }


    /**
     * Loads a DRL rule file and compiles it into a {@link KieBase} using a two-tier
     * resolution strategy.
     *
     * <p><strong>File resolution priority:</strong></p>
     * <ol>
     *   <li><strong>External filesystem</strong> -- If the {@code MEASUREMENT_DS_DIRECTORY}
     *       property is configured in {@link OscarProperties}, the DRL file is loaded
     *       from that directory. This allows site-specific rule customization without
     *       redeploying the application.</li>
     *   <li><strong>Classpath fallback</strong> -- If no external file is found, the DRL
     *       is loaded from the classpath at
     *       {@code /oscar/oscarEncounter/oscarMeasurements/flowsheets/decisionSupport/}.
     *       This provides the bundled default rules.</li>
     * </ol>
     *
     * @param string String the DRL filename to load (e.g. "bp_check.drl")
     * @return KieBase the compiled rule base, or {@code null} if loading fails
     * @see DroolsHelper#loadFromInputStream(java.io.InputStream)
     * @see DroolsHelper#loadFromUrl(URL)
     */
    public KieBase loadMeasurementRuleBase(String string) {
        KieBase measurementRuleBase = null;
        try {
            boolean fileFound = false;

            // Priority 1: Try loading from the external MEASUREMENT_DS_DIRECTORY
            String measurementDirPath = OscarProperties.getInstance().getProperty("MEASUREMENT_DS_DIRECTORY");

            if (measurementDirPath != null) {
                //if (measurementDirPath.charAt(measurementDirPath.length()) != /)
                File file = new File(OscarProperties.getInstance().getProperty("MEASUREMENT_DS_DIRECTORY") + string);
                if (file.isFile() || file.canRead()) {
                    MiscUtils.getLogger().debug("Loading from file " + file.getName());
                    FileInputStream fis = new FileInputStream(file);
                    measurementRuleBase = DroolsHelper.loadFromInputStream(fis);
                    fileFound = true;
                }
            }

            // Priority 2: Fall back to classpath-bundled DRL resource
            if (!fileFound) {
                URL url = MeasurementFlowSheet.class.getResource("/oscar/oscarEncounter/oscarMeasurements/flowsheets/decisionSupport/" + string);  //TODO: change this so it is configurable;
                MiscUtils.getLogger().debug("loading from URL " + url.getFile());
                measurementRuleBase = DroolsHelper.loadFromUrl(url);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return measurementRuleBase;
    }

    /**
     * Returns the output values map produced by rule evaluation.
     *
     * @return Hashtable the output key-value pairs, or {@code null} if not populated
     */
    public Hashtable getOutputValues() {
        return outputValues;
    }

    /**
     * Parses a comma-separated string of output field names into the {@link #outputfields} array.
     *
     * <p>If the string contains commas, it is split on commas to produce multiple fields.
     * Otherwise, the entire string is treated as a single field name.</p>
     *
     * @param str String comma-separated output field names (e.g. "systolic,diastolic"),
     *            or {@code null} to skip parsing
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
                MiscUtils.getLogger().error("Error", e);
            }
        }
    }

    /**
     * Returns the parsed output field names.
     *
     * @return String[] the output field name array, or {@code null} if not parsed
     */
    public String[] getOutputFields() {
        return outputfields;
    }

    /**
     * Keys identifying which values in {@link #replaceableValues} can be injected
     * into this numerator's evaluation. Parsed from a comma-separated configuration string.
     */
    String[] replaceKeys = null;

    /**
     * Replaceable values map populated at runtime by the clinical report framework.
     * Contains runtime parameters keyed by the names in {@link #replaceKeys}, such as
     * measurement type names, threshold values, or date range boundaries.
     */
    Hashtable replaceableValues = null;

    /**
     * Returns the array of replaceable value keys expected by this numerator.
     *
     * @return String[] the keys identifying expected replaceable values,
     *         or {@code null} if none are configured
     */
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
     * @param str String comma-separated key names (e.g. "measurements,value"),
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
                MiscUtils.getLogger().error("Error", e);
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

    /**
     * Sets the replaceable values map containing runtime parameters for this numerator.
     *
     * <p>The map should contain entries keyed by the names returned by
     * {@link #getReplaceableKeys()}. For DroolsNumerator (file-based), these values
     * are not directly used in evaluation since the DRL file contains all logic,
     * but they are available via the {@link Numerator} interface contract.</p>
     *
     * @param vals Hashtable the runtime parameter map to inject
     */
    public void setReplaceableValues(Hashtable vals) {
        replaceableValues = vals;
    }

    /**
     * Returns the current replaceable values map.
     *
     * @return Hashtable the replaceable values, or {@code null} if not set
     */
    public Hashtable getReplaceableValues() {
        return replaceableValues;
    }
}
