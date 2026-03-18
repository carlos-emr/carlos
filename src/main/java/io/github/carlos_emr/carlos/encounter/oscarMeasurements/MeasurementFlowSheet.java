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


package io.github.carlos_emr.carlos.encounter.oscarMeasurements;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections4.OrderedMapIterator;
import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import io.github.carlos_emr.carlos.drools.DroolsHelper;
import io.github.carlos_emr.carlos.commn.dao.DxDao;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementsDataBean;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.MeasurementDSHelper;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.Recommendation;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.RuleBaseCreator;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.TargetColour;

/**
 * Central class for managing clinical measurement flowsheets in CARLOS EMR.
 *
 * <p>A flowsheet is a structured clinical tool that organizes related measurements (e.g., blood
 * glucose, HbA1c, blood pressure, lipid panels) into a single view for disease-specific monitoring.
 * Each flowsheet is defined by an XML configuration file (e.g., {@code diabetesFlowsheet.xml},
 * {@code hypertensionFlowsheet.xml}) that specifies which measurement items to display, their
 * clinical guidelines, visual indicators, and decision support rules.</p>
 *
 * <h3>Architecture Overview</h3>
 * <p>This class serves as the runtime representation of a parsed flowsheet XML configuration.
 * It operates at the intersection of three subsystems:</p>
 * <ol>
 *   <li><strong>Configuration</strong> - XML files define measurement items, display properties,
 *       diagnosis code triggers (ICD-9/ICD-10), and clinical guidelines</li>
 *   <li><strong>Decision Support</strong> - Drools DRL rule files evaluate patient measurement
 *       data against clinical thresholds to generate warnings and recommendations</li>
 *   <li><strong>Presentation</strong> - Indicator colours, display names, and HTML fragments
 *       provide the visual layer for the encounter flowsheet UI</li>
 * </ol>
 *
 * <h3>Drools Rule Loading</h3>
 * <p>Decision support rules are loaded through two distinct mechanisms:</p>
 * <ul>
 *   <li><strong>Static DRL files</strong> - Pre-written {@code .drl} files loaded from the
 *       filesystem ({@code MEASUREMENT_DS_DIRECTORY} property) or classpath
 *       ({@code /oscar/oscarEncounter/oscarMeasurements/flowsheets/}). The filesystem path
 *       takes priority, allowing site-specific rule customization without modifying the
 *       application. Used for both flowsheet-level rules (e.g., {@code diab.drl}) and
 *       per-item rules (e.g., {@code decisionSupport/diab-A1C.drl}).</li>
 *   <li><strong>Programmatic DRL</strong> - Generated at runtime from {@link TargetColour}
 *       and {@link Recommendation} objects parsed from the flowsheet XML. These objects
 *       produce DRL rule strings via their {@code getRuleBaseElement()} methods, which are
 *       then compiled into a {@link KieBase} by {@link RuleBaseCreator}.</li>
 * </ul>
 *
 * <h3>Two Rule Execution Contexts</h3>
 * <p>The class supports two distinct rule execution patterns, each using a different
 * fact object type:</p>
 * <ul>
 *   <li>{@link #runRulesForMeasurement(LoggedInInfo, EctMeasurementsDataBean)} - Executes
 *       per-item rules using {@link MeasurementDSHelper} as the fact object. These rules
 *       set indication colours on individual measurement values (e.g., marking a high
 *       blood pressure reading in red).</li>
 *   <li>{@link #getMessages(MeasurementInfo)} - Executes flowsheet-level rules using
 *       {@link MeasurementInfo} as the fact object. These rules generate clinical warnings
 *       and recommendations based on the overall patient measurement profile (e.g.,
 *       "HbA1c not measured in 6 months").</li>
 * </ul>
 *
 * <h3>KieSession Lifecycle</h3>
 * <p>All rule executions follow a strict lifecycle: create a new {@link KieSession} from the
 * compiled {@link KieBase}, insert the fact object, fire all rules, then dispose the session
 * in a {@code finally} block. Sessions are never reused. The {@link KieBase} is thread-safe
 * and can be shared, but each execution creates its own stateful session.</p>
 *
 * <h3>Flowsheet Configuration Example</h3>
 * <pre>{@code
 * <flowsheet name="diab"
 *            display_name="Diabetes Flowsheet"
 *            ds_rules="diab.drl"
 *            dxcode_triggers="icd9:250,icd10:E149"
 *            warning_colour="#E00000"
 *            recommendation_colour="yellow">
 *     <indicator key="HIGH" colour="orange"/>
 *     <item measurement_type="A1C"
 *           display_name="HBA1c"
 *           guideline="Target &lt;= 7.0%"
 *           ds_rules="decisionSupport/diab-A1C.drl"/>
 * </flowsheet>
 * }</pre>
 *
 * @since 2006-02-08
 * @see FlowSheetItem
 * @see DroolsHelper
 * @see RuleBaseCreator
 * @see MeasurementDSHelper
 * @see MeasurementInfo
 * @see TargetColour
 * @see Recommendation
 * @see MeasurementTemplateFlowSheetConfig
 */
public class MeasurementFlowSheet {

    private static Logger log = MiscUtils.getLogger();

    /** Internal identifier for this flowsheet, corresponding to the {@code name} attribute in the XML. */
    String name = null;

    /** Human-readable name shown in the UI (e.g., "Diabetes Flowsheet"). */
    private String displayName = null;

    /** CSS colour value used to render warning-level clinical alerts. */
    private String warningColour = null;

    /** CSS colour value used to render recommendation-level clinical suggestions. */
    private String recommendationColour = null;

    /**
     * Maps severity indicator keys (e.g., "HIGH", "LOW") to CSS colour values.
     * Populated from {@code <indicator>} elements in the flowsheet XML configuration.
     * Used by the UI to colour-code individual measurement values based on their
     * clinical significance as determined by Drools rules.
     */
    Hashtable<String, String> indicatorHash = new Hashtable<String, String>();

    /** Filename of an optional HTML fragment displayed at the top of the flowsheet view. */
    private String topHTMLFileName = null;

    /** Whether this flowsheet appears for all patients regardless of diagnosis codes. */
    private boolean universal;

    /** Whether this flowsheet contains medical measurements (as opposed to social determinants). */
    private boolean isMedical = true;

    /** Whether this flowsheet was created/customized by the clinic rather than shipped with the application. */
    private boolean custom = false;

    /**
     * Compiled Drools knowledge base for flowsheet-level decision support rules.
     * Built from {@link Recommendation} objects across all items via {@link #loadRuleBase()},
     * or loaded from a static DRL file via {@link #loadRuleBase(String)}.
     * Used by {@link #getMessages(MeasurementInfo)} to generate clinical warnings
     * and recommendations for the entire flowsheet.
     */
    KieBase ruleBase = null;

    /**
     * Flag indicating whether the flowsheet-level rule base has been successfully compiled.
     * Must be {@code true} before {@link #getMessages(MeasurementInfo)} can be called.
     */
    boolean rulesLoaded = false;

    /**
     * ICD-9 and ICD-10 diagnosis code triggers that cause this flowsheet to appear
     * automatically in a patient's profile. Parsed from the {@code dxcode_triggers}
     * attribute in the flowsheet XML (e.g., "icd9:250,icd10:E149" for diabetes).
     */
    String[] dxTriggers = null;

    /** Program-based triggers that cause this flowsheet to appear for patients in specific programs. */
    String[] programTriggers = null;

    /**
     * Ordered collection of {@link FlowSheetItem} objects keyed by measurement type name.
     * Maintains insertion order to preserve the clinical display sequence defined in the
     * flowsheet XML configuration. Uses Apache Commons {@link ListOrderedMap} to support
     * both key-based lookup and index-based positional operations.
     */
    private ListOrderedMap itemList = new ListOrderedMap();

    /**
     * Adds a {@link FlowSheetItem} to this flowsheet and loads its per-item decision support rules.
     *
     * <p>This method determines the rule loading strategy based on the item's configuration:</p>
     * <ol>
     *   <li><strong>Static DRL file</strong> - If the item's {@code ds_rules} field is set
     *       (e.g., {@code "decisionSupport/diab-A1C.drl"}), the DRL file is loaded from the
     *       filesystem or classpath via {@link #loadMeasurementRuleBase(String)}.</li>
     *   <li><strong>Programmatic DRL from TargetColour</strong> - If no static DRL is specified
     *       but the item has {@link TargetColour} objects (parsed from {@code <ruleset>} XML
     *       elements), each TargetColour generates a DRL rule string via
     *       {@link TargetColour#getRuleBaseElement(String)} and the collection is compiled
     *       into a {@link KieBase} via {@link #loadMeasurementRuleBase(List)}.</li>
     *   <li><strong>No rules</strong> - If neither is present, the item has no per-item
     *       decision support and no rule base is set.</li>
     * </ol>
     *
     * <p>The compiled {@link KieBase} is stored on the item itself for later use by
     * {@link #runRulesForMeasurement(LoggedInInfo, EctMeasurementsDataBean)}.</p>
     *
     * @param item FlowSheetItem the measurement item to add, parsed from the flowsheet XML
     * @return FlowSheetItem the same item, now registered in this flowsheet with its rule base loaded
     * @see FlowSheetItem#setRuleBase(KieBase)
     */
    public FlowSheetItem addListItem(FlowSheetItem item) {
        log.debug("ITEM " + item.getItemName());
        // Check for a static DRL file reference in the item's configuration fields
        String dsRules = item.getAllFields().get("ds_rules");
        log.debug("DS RULES " + dsRules);
        if (dsRules != null && !dsRules.equals("")) {
            // Priority 1: Load pre-written DRL from filesystem or classpath
            KieBase rb = loadMeasurementRuleBase(dsRules);
            item.setRuleBase(rb);
        } else if (item.getTargetColour() != null && item.getTargetColour().size() > 0) {
            // Priority 2: Generate DRL programmatically from TargetColour XML definitions
            KieBase rb = loadMeasurementRuleBase(item.getTargetColour());
            item.setRuleBase(rb);
        }
        itemList.put(item.getItemName(), item);
        log.debug("ADDED " + item);
        return item;
    }

    /**
     * Hierarchical grouping of flowsheet items for structured display.
     * Allows items to be organized into sections/subsections in the UI.
     */
    private List<MeasurementTemplateFlowSheetConfig.Node> itemHeirarchy = new ArrayList<MeasurementTemplateFlowSheetConfig.Node>();

    /**
     * Sets the hierarchical item grouping structure for this flowsheet.
     *
     * @param itemHeirarchy List of {@link MeasurementTemplateFlowSheetConfig.Node} objects
     *        defining the display hierarchy
     */
    public void setItemHeirarchy(List<MeasurementTemplateFlowSheetConfig.Node> itemHeirarchy) {
        this.itemHeirarchy = itemHeirarchy;
    }

    /**
     * Returns the hierarchical item grouping structure for this flowsheet.
     *
     * @return List of {@link MeasurementTemplateFlowSheetConfig.Node} objects
     *         defining the display hierarchy
     */
    public List<MeasurementTemplateFlowSheetConfig.Node> getItemHeirarchy() {
        return this.itemHeirarchy;
    }

    /**
     * Parses a comma-separated string of diagnosis code triggers and stores them.
     *
     * <p>Triggers use the format {@code "codingSystem:code"} (e.g., {@code "icd9:250,icd10:E149"}).
     * When a patient has a matching diagnosis code in their chart, this flowsheet will
     * automatically appear in their encounter view.</p>
     *
     * @param s String comma-separated diagnosis code triggers from the flowsheet XML
     *          {@code dxcode_triggers} attribute
     */
    public void parseDxTriggers(String s) {
        dxTriggers = s.split(","); //TODO: what do about different coding systems.
    }

    /**
     * Parses a comma-separated string of program triggers and stores them.
     *
     * @param s String comma-separated program identifiers that trigger this flowsheet
     */
    public void parseProgramTriggers(String s) {
        programTriggers = s.split(",");
    }

    /**
     * Returns the array of diagnosis code triggers for this flowsheet.
     *
     * @return String[] array of triggers in {@code "codingSystem:code"} format,
     *         or {@code null} if no triggers are configured
     */
    public String[] getDxTriggers() {
        return dxTriggers;
    }

    /**
     * Returns the array of program-based triggers for this flowsheet.
     *
     * @return String[] array of program identifiers, or {@code null} if none configured
     */
    public String[] getProgramTriggers() {
        return programTriggers;
    }

    /**
     * Returns the diagnosis code triggers as a comma-separated string.
     *
     * @return String comma-delimited trigger codes (e.g., {@code "icd9:250,icd10:E149"}),
     *         or an empty string if no triggers are configured
     */
    public String getDxTriggersString() {
        StringBuilder sb = new StringBuilder();
        boolean firstElement = true;
        if (dxTriggers != null) {
            for (String s : dxTriggers) {
                if (!firstElement) {
                    sb.append(",");
                }
                sb.append(s);
                firstElement = false;
            }
        }
        return sb.toString();
    }

    /**
     * Builds an HTML unordered list of diagnosis code trigger links for the flowsheet UI.
     *
     * <p>Each trigger is rendered as a clickable link that allows the provider to add the
     * corresponding diagnosis code to the patient's chart. The special code
     * {@code "OscarCode:CKDSCREEN"} is excluded from the rendered output as it is handled
     * separately.</p>
     *
     * @param demo String the patient demographic number
     * @param provider String the provider number of the logged-in clinician
     * @return String HTML markup containing an unordered list of diagnosis code links,
     *         or an empty string if no triggers are configured
     */
    public String getDxTriggersQueryBuilder(String demo, String provider) {
        StringBuilder sb = new StringBuilder();
        String query = "";
        String desc = "";

        DxDao dao = SpringUtils.getBean(DxDao.class);

        if (dxTriggers != null) {
            for (String s : dxTriggers) {
                if (!s.equals("OscarCode:CKDSCREEN")) {
                    String[] type = s.split(":");
                    desc = dao.getCodeDescription(type[0], type[1]);
                    sb.append("<li><a href='javascript:void(0);' id='dxlink" + type[1] + "' rel='selectedCodingSystem=" + type[0] + "&forward=" + type[1] + "&demographicNo=" + demo + "&providerNo=" + provider + "'>" + s + " " + desc + "</a></li>");
                }
            }

            query = sb.toString();
            query = "<ul>" + query + "</ul>";
        }
        return query;
    }

    /**
     * Returns the program triggers as a comma-separated string.
     *
     * @return String comma-delimited program identifiers, or an empty string if none configured
     */
    public String getProgramTriggersString() {
        StringBuilder sb = new StringBuilder();
        boolean firstElement = true;
        if (programTriggers != null) {
            for (String s : programTriggers) {
                if (!firstElement) {
                    sb.append(",");
                }
                sb.append(s);
            }
        }
        return sb.toString();
    }

    /**
     * Creates a new empty MeasurementFlowSheet.
     *
     * <p>After construction, the flowsheet must be populated by adding {@link FlowSheetItem}
     * objects via {@link #addListItem(FlowSheetItem)}, setting display properties, and
     * loading decision support rules via {@link #loadRuleBase()} or {@link #loadRuleBase(String)}.
     * This is typically performed by {@link MeasurementTemplateFlowSheetConfig} during
     * XML configuration parsing.</p>
     */
    public MeasurementFlowSheet() {
    }

    /**
     * Returns the decision support {@link Recommendation} objects for a specific measurement type.
     *
     * @param measurement String the measurement type name (e.g., "A1C", "BP")
     * @return List of {@link Recommendation} objects defining the clinical decision support
     *         rules for the specified measurement
     * @throws NullPointerException if the measurement type is not found in this flowsheet
     */
    public List<Recommendation> getDSElements(String measurement) {
        FlowSheetItem fsi = (FlowSheetItem) itemList.get(measurement);
        return fsi.getRecommendations();
    }

    /**
     * Inserts a {@link FlowSheetItem} at a specific position in the ordered item list.
     *
     * <p>Unlike {@link #addListItem(FlowSheetItem)}, this method does not load decision
     * support rules for the item. It is used for positional insertion during flowsheet
     * customization.</p>
     *
     * @param i int the zero-based index position at which to insert the item
     * @param item FlowSheetItem the measurement item to insert
     */
    public void addFlowSheetItem(int i, FlowSheetItem item) {
        itemList.put(i, item.getItemName(), item);
    }

    /**
     * Retrieves a {@link FlowSheetItem} by its measurement type name.
     *
     * @param measurement String the measurement type name (e.g., "A1C", "BP", "WGHT")
     * @return FlowSheetItem the matching item, or {@code null} if not found
     */
    public FlowSheetItem getFlowSheetItem(String measurement) {
        MiscUtils.getLogger().debug("GETTING " + measurement + " ITEMS IN THE LIST " + itemList.size());
        FlowSheetItem item = (FlowSheetItem) itemList.get(measurement);

        return item;
    }


    /**
     * Returns the configuration properties map for a specific measurement type.
     *
     * <p>The returned map contains all key-value pairs from the {@code <item>} element
     * in the flowsheet XML, including fields such as {@code measurement_type},
     * {@code display_name}, {@code guideline}, {@code graphable}, {@code ds_rules}, etc.</p>
     *
     * @param measurement String the measurement type name
     * @return Map of String key-value pairs containing the item's configuration fields,
     *         or an empty map if the measurement is not found or has no fields
     */
    public Map<String, String> getMeasurementFlowSheetInfo(String measurement) {
        Map<String, String> allFields = Collections.emptyMap();
        if (itemList == null) {
            // Lazy-initialize to prevent NPE when accessed before items are added
            itemList = new ListOrderedMap();
        }
        log.debug("GETTING " + measurement + " ITEMS IN THE LIST " + itemList.size());
        FlowSheetItem item = (FlowSheetItem) itemList.get(measurement);
        if (item != null && item.getAllFields() != null) {
            allFields = item.getAllFields();
        }
        return allFields;
    }

    /**
     * Inserts a {@link FlowSheetItem} after the specified measurement in the display order.
     *
     * <p>If the {@code measurement} parameter is {@code null}, the item is appended to the
     * end of the flowsheet. This is used during flowsheet customization to position new
     * items relative to existing ones.</p>
     *
     * @param measurement String the measurement type name after which to insert, or
     *        {@code null} to append at the end
     * @param item FlowSheetItem the measurement item to insert
     */
    public void addAfter(String measurement, FlowSheetItem item) {
        int placement = itemList.size();
        if (measurement != null) {
            placement = itemList.indexOf(measurement);
        }
        itemList.put(placement, item.getItemName(), item);
    }

    /**
     * Marks a measurement item as hidden so it will not appear in the flowsheet display.
     *
     * @param measurement String the measurement type name to hide
     */
    public void setToHidden(String measurement) {
        FlowSheetItem item = (FlowSheetItem) itemList.get(measurement);
        item.setHide(true);
    }


    /**
     * Replaces the flowsheet item for a measurement type with a new item built from the given properties.
     *
     * @param measurement String the measurement type name to update
     * @param h Hashtable of String key-value pairs containing the new item configuration
     */
    public void updateMeasurementFlowSheetInfo(String measurement, Hashtable<String, String> h) {
        FlowSheetItem item = new FlowSheetItem(h);
        itemList.put(measurement, item);
    }

    /**
     * Replaces the flowsheet item for a measurement type with the given item.
     *
     * @param measurement String the measurement type name to update
     * @param item FlowSheetItem the replacement item
     */
    public void updateMeasurementFlowSheetInfo(String measurement, FlowSheetItem item) {
        itemList.put(measurement, item);
    }


    /**
     * Returns an ordered list of all measurement type names in this flowsheet.
     *
     * <p>The list preserves the display order defined in the flowsheet XML configuration,
     * including both visible and hidden items.</p>
     *
     * @return List of String measurement type names in display order
     */
    public List<String> getMeasurementList() {
        return itemList.asList();
    }

    /**
     * Returns an ordered list of only the visible (non-hidden) measurement type names.
     *
     * <p>Filters out items that have been marked as hidden via {@link #setToHidden(String)}
     * or through the item's own hide flag. Used by the UI to determine which columns
     * to render in the flowsheet display.</p>
     *
     * @return List of String visible measurement type names in display order
     */
    public List<String> getVisibleMeasurementList() {
        return getMeasurementList()
            .stream()
            .filter(id -> { 
                FlowSheetItem item = getFlowSheetItem(id);
                return item != null && !item.isHide();
            })
            .collect(Collectors.toList());
    }

    /**
     * Returns the internal name identifier for this flowsheet.
     *
     * @return String the flowsheet name (e.g., "diab", "hypertension", "ckd")
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the internal name identifier for this flowsheet.
     *
     * @param s String the flowsheet name, matching the {@code name} attribute in the XML configuration
     */
    public void setName(String s) {
        name = s;
    }

    /**
     * Loads and returns the optional HTML content displayed at the top of this flowsheet.
     *
     * <p>The HTML file is resolved using the same filesystem-first, classpath-fallback
     * priority used for DRL files:</p>
     * <ol>
     *   <li>If the {@code MEASUREMENT_DS_HTML_DIRECTORY} property is set, attempts to load
     *       the file from that filesystem directory first.</li>
     *   <li>Falls back to the classpath resource at
     *       {@code /oscar/oscarEncounter/oscarMeasurements/flowsheets/html/}.</li>
     * </ol>
     *
     * @return String the HTML content to display at the top of the flowsheet,
     *         or an empty string if no HTML file is configured or the file cannot be read
     */
    public String getTopHTMLStream() {
        StringBuilder sb = new StringBuilder();
        if (topHTMLFileName != null) {
            try {
                // Priority 1: Check for HTML file on the filesystem (site-specific override)
                String measurementDirPath = OscarProperties.getInstance().getProperty("MEASUREMENT_DS_HTML_DIRECTORY");
                InputStream is = null;
                if (measurementDirPath != null) {
                    File file = PathValidationUtils.validatePath(topHTMLFileName, new File(measurementDirPath));
                    if (file.isFile() && file.canRead()) {
                        log.debug("Loading from file " + file.getName());
                        is = new FileInputStream(file);
                    }
                }

                // Priority 2: Fall back to classpath resource
                if (is == null) {
                    is = MeasurementFlowSheet.class.getResourceAsStream("/oscar/oscarEncounter/oscarMeasurements/flowsheets/html/" + topHTMLFileName);
                    log.debug("loading from stream ");
                }

                if (is != null) {
                    BufferedReader bReader = new BufferedReader(new InputStreamReader(is));
                    String str;
                    while ((str = bReader.readLine()) != null) {
                        sb.append(str);
                    }
                    bReader.close();
                }
            } catch (Exception e) {
                log.error("Failed to load top HTML file for flowsheet: {}", topHTMLFileName, e);
            }
        }
        return sb.toString();
    }

    /**
     * Loads and returns decision support HTML content from a specified file.
     *
     * <p>Uses the same filesystem-first, classpath-fallback resolution as
     * {@link #getTopHTMLStream()}, checking the {@code MEASUREMENT_DS_HTML_DIRECTORY}
     * property first, then falling back to the classpath at
     * {@code /oscar/oscarEncounter/oscarMeasurements/flowsheets/html/}.</p>
     *
     * @param dsHTML String the filename of the HTML file to load
     * @return String the HTML content, or an empty string if the file cannot be found or read
     */
    public static String getDSHTMLStream(String dsHTML) {
        StringBuilder sb = new StringBuilder();
        InputStream is = null;
        BufferedReader bReader = null;
        try {
            // Priority 1: Check for HTML file on the filesystem (site-specific override)
            String measurementDirPath = OscarProperties.getInstance().getProperty("MEASUREMENT_DS_HTML_DIRECTORY");

            if (measurementDirPath != null) {
                File file = PathValidationUtils.validatePath(dsHTML, new File(measurementDirPath));
                if (file.isFile() && file.canRead()) {
                    log.debug("Loading from file " + file.getName());
                    is = new FileInputStream(file);
                }
            }

            // Priority 2: Fall back to classpath resource
            if (is == null) {
                is = MeasurementFlowSheet.class.getResourceAsStream("/oscar/oscarEncounter/oscarMeasurements/flowsheets/html/" + dsHTML);
                log.debug("loading from stream ");
            }

            if (is != null) {
                bReader = new BufferedReader(new InputStreamReader(is));
                String str;
                while ((str = bReader.readLine()) != null) {
                    sb.append(str);
                }
                bReader.close();
            }

        } catch (Exception e) {
            log.error("Failed to load decision support HTML file: {}", dsHTML, e);
        } finally {
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(bReader);
        }

        return sb.toString();
    }


    /**
     * Loads the flowsheet-level decision support rule base by aggregating programmatic DRL
     * from all items' {@link Recommendation} objects.
     *
     * <p>This method iterates over every {@link FlowSheetItem} in this flowsheet, collects
     * the DRL rule strings generated by each item's {@link Recommendation#getRuleBaseElement()}
     * method, and compiles them into a single {@link KieBase} via {@link RuleBaseCreator}.
     * The resulting rule base is stored in the {@link #ruleBase} field and is used by
     * {@link #getMessages(MeasurementInfo)} to evaluate patient measurement data and
     * produce clinical warnings and recommendations.</p>
     *
     * <p>Each {@link Recommendation} object generates a DRL rule string that uses
     * {@link MeasurementInfo} as the fact object type. The rule conditions check
     * measurement recency (e.g., months since last recorded) and the consequences add
     * warnings or recommendations to the {@link MeasurementInfo} object.</p>
     *
     * <p>After successful compilation, the {@link #rulesLoaded} flag is set to {@code true}.
     * If no recommendations exist across any items, the rule base remains {@code null}.</p>
     *
     * @see Recommendation#getRuleBaseElement()
     * @see RuleBaseCreator#getRuleBase(String, List)
     * @see #getMessages(MeasurementInfo)
     */
    public void loadRuleBase() {
        log.debug("LOADRULEBASE == " + name);
        ArrayList<String> dsElements = new ArrayList<String>();

        // Iterate all items and collect DRL rule strings from their Recommendation objects
        if (itemList != null) {
            OrderedMapIterator iter = itemList.mapIterator();
            while (iter.hasNext()) {
                // iter.next() returns the key and advances the iterator
                String key = (String) iter.next();
                FlowSheetItem fsi = (FlowSheetItem) iter.getValue();
                List<Recommendation> rules = fsi.getRecommendations();
                if (rules != null) {
                    log.debug("# OF RULES FOR " + fsi.getItemName() + " " + rules.size() + " key " + key);
                    for (Object obj : rules) {
                        Recommendation rec = (Recommendation) obj;
                        // Each Recommendation generates a DRL rule string with MeasurementInfo as the fact type
                        dsElements.add(rec.getRuleBaseElement());
                    }
                } else {
                    log.debug("NO RULES FOR " + fsi.getItemName());
                }

            }
        }
        log.debug("LOADING RULES2" + name + " size + " + dsElements.size() + " rulebase " + ruleBase);
        if (dsElements != null && dsElements.size() > 0) {

            log.debug("LOADING RULES21" + dsElements.size());
            // Compile all collected DRL rule strings into a single KieBase
            RuleBaseCreator rcb = new RuleBaseCreator();
            try {

                log.debug("LOADING RULES22");
                ruleBase = rcb.getRuleBase("rulesetName", dsElements);
                log.debug("LOADING RULES23");
                rulesLoaded = true;
            } catch (Exception e) {
                log.debug("LOADING EXEPTION");

                log.error("Failed to compile programmatic rule base for flowsheet: {}", name, e);
            }
        }
    }

    /**
     * Loads the flowsheet-level decision support rule base from a static DRL file.
     *
     * <p>This is used when the flowsheet XML specifies a {@code ds_rules} attribute at the
     * top level (e.g., {@code ds_rules="diab.drl"}), as opposed to per-item rules or
     * programmatic DRL from {@link Recommendation} objects.</p>
     *
     * <p><strong>DRL file resolution priority:</strong></p>
     * <ol>
     *   <li><strong>Filesystem first</strong> - If the {@code MEASUREMENT_DS_DIRECTORY}
     *       property is configured, the DRL file is loaded from that directory. This allows
     *       clinics to customize rules without modifying the deployed application.</li>
     *   <li><strong>Classpath fallback</strong> - If the file is not found on the filesystem,
     *       it is loaded from the classpath at
     *       {@code /oscar/oscarEncounter/oscarMeasurements/flowsheets/}.</li>
     * </ol>
     *
     * <p>The compiled {@link KieBase} is stored in the {@link #ruleBase} field and the
     * {@link #rulesLoaded} flag is set to {@code true}. Note that {@code rulesLoaded}
     * is set even if an exception occurs during compilation.</p>
     *
     * @param string String the DRL filename (e.g., {@code "diab.drl"}, {@code "hypertension.drl"})
     * @see DroolsHelper#loadFromInputStream(java.io.InputStream)
     * @see DroolsHelper#loadFromUrl(URL)
     */
    public void loadRuleBase(String string) {
        try {
            boolean fileFound = false;
            // Priority 1: Check for DRL file on the filesystem (allows site-specific customization)
            String measurementDirPath = OscarProperties.getInstance().getProperty("MEASUREMENT_DS_DIRECTORY");

            if (measurementDirPath != null) {
                File file = PathValidationUtils.validatePath(string, new File(measurementDirPath));
                if (file.isFile() && file.canRead()) {
                    log.debug("Loading DRL from file: {}", file.getName());
                    try (FileInputStream fis = new FileInputStream(file)) {
                        ruleBase = DroolsHelper.loadFromInputStream(fis);
                    }
                    fileFound = true;
                }
            }

            // Priority 2: Fall back to classpath resource bundled with the application
            if (!fileFound) {
                URL url = MeasurementFlowSheet.class.getResource("/oscar/oscarEncounter/oscarMeasurements/flowsheets/" + string);  //TODO: change this so it is configurable;
                if (url == null) {
                    log.warn("DRL resource not found on classpath for flowsheet rule: {}", string);
                    return;
                }
                log.debug("loading from URL {}", url.getFile());
                ruleBase = DroolsHelper.loadFromUrl(url);
            }
        } catch (Exception e) {
            log.error("Failed to load flowsheet rule base from DRL file: {}", string, e);
        }
        rulesLoaded = (ruleBase != null);
    }

    /**
     * Loads the flowsheet-level rule base from a static DRL file and stores the result.
     *
     * <p>Delegates to {@link #loadMeasurementRuleBase(String)} for the actual file loading,
     * then stores the result in the flowsheet-level {@link #ruleBase} field and sets the
     * {@link #rulesLoaded} flag. Unlike {@link #loadRuleBase(String)}, this method uses
     * the {@code decisionSupport/} classpath subdirectory as the fallback location.</p>
     *
     * @param string String the DRL filename to load
     * @see #loadMeasurementRuleBase(String)
     */
    public void loadRuleBase2(String string) {
        ruleBase = loadMeasurementRuleBase(string);
        if (ruleBase != null) {
            rulesLoaded = true;
        }
    }

    /**
     * Compiles a {@link KieBase} from programmatically generated DRL rules based on
     * {@link TargetColour} objects.
     *
     * <p>Each {@link TargetColour} represents a colour-coding rule from the flowsheet XML
     * (e.g., "mark values above 2.0 as HIGH in orange"). This method converts each
     * TargetColour into a DRL rule string via {@link TargetColour#getRuleBaseElement(String)},
     * using {@link MeasurementDSHelper} as the fact object type. The generated rules
     * set indication colours on measurement values when conditions are met.</p>
     *
     * <p>Rule names are auto-generated as "DD0", "DD1", "DD2", etc. to ensure uniqueness
     * within the compiled rule base.</p>
     *
     * @param targetColours List of {@link TargetColour} objects defining colour-coding rules
     * @return KieBase the compiled rule base, or {@code null} if compilation fails
     * @see TargetColour#getRuleBaseElement(String)
     * @see RuleBaseCreator#getRuleBase(String, List)
     * @see MeasurementDSHelper
     */
    public KieBase loadMeasurementRuleBase(List<TargetColour> targetColours) {
        KieBase measurementRuleBase = null;
        List<String> dsElements = new ArrayList<String>();
        RuleBaseCreator rcb = new RuleBaseCreator();
        try {
            // Convert each TargetColour into a DRL rule string with auto-generated rule names
            int count = 0;
            for (TargetColour obj : targetColours) {
                TargetColour rec = obj;
                // getRuleBaseElement() generates DRL with MeasurementDSHelper as the fact type
                dsElements.add(rec.getRuleBaseElement("DD" + count));
                count++;
            }

            log.debug("loadMeasurementRuleBase 1");
            // Compile all generated DRL strings into a single KieBase
            measurementRuleBase = rcb.getRuleBase("rulesetName", dsElements);
            log.debug("loadMeasurementRuleBase 2");
        } catch (Exception e) {
            log.debug("loadMeasurementRuleBase EXCEPTION");

            log.error("Failed to compile TargetColour rule base for measurement", e);
        }
        return measurementRuleBase;

    }


    /**
     * Loads a per-item decision support DRL file using the standard two-tier strategy.
     *
     * <p>Called during {@link #addListItem(FlowSheetItem)} when a {@link FlowSheetItem}
     * specifies a {@code ds_rules} field.</p>
     *
     * @param string the DRL filename (e.g., {@code "decisionSupport/diab-A1C.drl"})
     * @return KieBase the compiled rule base, or {@code null} if loading fails
     * @see DroolsHelper#loadMeasurementRuleBase(String, Class)
     */
    public KieBase loadMeasurementRuleBase(String string) {
        return DroolsHelper.loadMeasurementRuleBase(string, MeasurementFlowSheet.class);
    }


    /**
     * Executes per-item decision support rules against a single measurement data bean.
     *
     * <p>This method runs the Drools rules associated with a specific measurement type
     * (e.g., the rules for "A1C" or "BP") to evaluate whether the measurement value
     * falls within clinical thresholds. The rules may set indication colours on the
     * {@link MeasurementDSHelper} fact object, which are then used by the UI to
     * colour-code the measurement value (e.g., orange for "HIGH", red for "HIGH 1").</p>
     *
     * <p><strong>KieSession lifecycle:</strong> A new stateful {@link KieSession} is created
     * from the item's compiled {@link KieBase}, a {@link MeasurementDSHelper} fact is
     * inserted wrapping the measurement data and patient context, all rules are fired,
     * and the session is disposed in the {@code finally} block. The session is never reused.</p>
     *
     * <p>If the item has no associated rule base (no {@code ds_rules} and no
     * {@link TargetColour} definitions), this method is a no-op.</p>
     *
     * @param loggedInInfo LoggedInInfo the current session context with provider information
     * @param mdb EctMeasurementsDataBean the measurement data bean containing the value,
     *        type, and date to evaluate
     * @see MeasurementDSHelper
     * @see FlowSheetItem#getRuleBase()
     */
    public void runRulesForMeasurement(LoggedInInfo loggedInInfo, EctMeasurementsDataBean mdb) {

        String type = mdb.getType();
        log.debug("GETTING RULES FOR TYPE " + type);
        FlowSheetItem fs = (FlowSheetItem) itemList.get(type);
        KieBase rb = fs.getRuleBase();
        log.debug("RULEBASE FOR " + fs);
        // Only execute rules if the item has a compiled rule base
        if (rb != null) {
            // Create a new stateful session for this single rule execution
            KieSession kieSession = rb.newKieSession();
            try {
                // Insert the measurement data wrapped in MeasurementDSHelper as the Drools fact
                kieSession.insert(new MeasurementDSHelper(loggedInInfo, mdb));
                kieSession.fireAllRules();
            } catch (Exception e) {
                log.error("Failed to execute decision support rules for measurement type: {}", type, e);
            } finally {
                // Always dispose the session to release resources
                kieSession.dispose();
            }
        }
    }

    /**
     * Executes the flowsheet-level decision support rules to generate clinical warnings
     * and recommendations for a patient.
     *
     * <p>This method uses the flowsheet-level {@link #ruleBase} (compiled from
     * {@link Recommendation} objects or a static DRL file) to evaluate the patient's
     * overall measurement profile. Unlike {@link #runRulesForMeasurement(LoggedInInfo,
     * EctMeasurementsDataBean)} which evaluates individual values, this method operates
     * on the aggregate {@link MeasurementInfo} object which contains all measurement
     * data for a patient across all items in this flowsheet.</p>
     *
     * <p>The rules typically check measurement recency (e.g., "HbA1c not measured in
     * 6 months") and add warnings or recommendations to the {@link MeasurementInfo}
     * object. The modified {@link MeasurementInfo} is returned with its warning and
     * recommendation lists populated by the fired rules.</p>
     *
     * <p><strong>KieSession lifecycle:</strong> A new stateful {@link KieSession} is created,
     * the {@link MeasurementInfo} fact is inserted, all rules are fired, and the session
     * is disposed in the {@code finally} block.</p>
     *
     * @param mi MeasurementInfo the patient measurement data to evaluate; modified in-place
     *        by the rules to add warnings and recommendations
     * @return MeasurementInfo the same object, now populated with any clinical messages
     *         generated by the rules
     * @throws Exception if the rule base has not been loaded (i.e., neither
     *         {@link #loadRuleBase()} nor {@link #loadRuleBase(String)} has been called)
     * @see MeasurementInfo
     * @see #loadRuleBase()
     * @see #loadRuleBase(String)
     */
    public MeasurementInfo getMessages(MeasurementInfo mi) throws Exception {
        if (!rulesLoaded) {
            throw new IllegalStateException(
                    "Flowsheet '" + name + "' has no loaded Drools rule base; "
                    + "check logs for prior compilation errors during startup");
        }

        // Create a new stateful session for this evaluation
        KieSession kieSession = ruleBase.newKieSession();
        try {
            // Insert the patient's measurement data as the Drools fact
            kieSession.insert(mi);
            kieSession.fireAllRules();
        } catch (Exception e) {
            log.error("Failed to execute flowsheet decision support rules for flowsheet: {}", name, e);
            throw e;
        } finally {
            // Always dispose the session to release resources
            kieSession.dispose();
        }
        return mi;
    }

    /**
     * Returns the human-readable display name for this flowsheet.
     *
     * @return String the display name (e.g., "Diabetes Flowsheet"), or {@code null} if not set
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Sets the human-readable display name for this flowsheet.
     *
     * @param displayName String the display name from the flowsheet XML {@code display_name} attribute
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the CSS colour value used for warning-level clinical alerts.
     *
     * @return String the warning colour (e.g., {@code "#E00000"}), or {@code null} if not set
     */
    public String getWarningColour() {
        return warningColour;
    }

    /**
     * Sets the CSS colour value used for warning-level clinical alerts.
     *
     * @param warningColour String the warning colour from the flowsheet XML
     *        {@code warning_colour} attribute
     */
    public void setWarningColour(String warningColour) {
        this.warningColour = warningColour;
    }

    /**
     * Returns the CSS colour value used for recommendation-level clinical suggestions.
     *
     * @return String the recommendation colour (e.g., {@code "yellow"}), or {@code null} if not set
     */
    public String getRecommendationColour() {
        return recommendationColour;
    }

    /**
     * Sets the CSS colour value used for recommendation-level clinical suggestions.
     *
     * @param recommendationColour String the recommendation colour from the flowsheet XML
     *        {@code recommendation_colour} attribute
     */
    public void setRecommendationColour(String recommendationColour) {
        this.recommendationColour = recommendationColour;
    }

    /**
     * Registers an indicator key-colour mapping for measurement value colour-coding.
     *
     * <p>Indicators are defined in the flowsheet XML as {@code <indicator>} elements
     * (e.g., {@code <indicator key="HIGH" colour="orange"/>}). When Drools rules set
     * an indication colour on a measurement value, the UI uses this mapping to determine
     * the actual CSS colour to render.</p>
     *
     * @param key String the indicator severity key (e.g., "HIGH", "LOW", "HIGH 1")
     * @param value String the CSS colour value (e.g., "orange", "#E00000", "#9999FF")
     */
    void AddIndicator(String key, String value) {
        if (key != null && value != null) {
            indicatorHash.put(key, value);
        }
    }

    /**
     * Returns the CSS colour value for a given indicator severity key.
     *
     * @param key String the indicator key (e.g., "HIGH", "LOW")
     * @return String the CSS colour value, or {@code null} if the key is not found or is {@code null}
     */
    public String getIndicatorColour(String key) {
        String ret = null;
        if (key != null) {
            ret = indicatorHash.get(key);
        }
        return ret;
    }

    /**
     * Returns a defensive copy of the indicator key-colour mapping.
     *
     * @return Hashtable a new Hashtable containing all indicator key-colour pairs
     */
    public Hashtable<String, String> getIndicatorHashtable() {
        return new Hashtable<String, String>(indicatorHash);
    }

    /**
     * Sorts the given list to match the display order of measurements in this flowsheet.
     *
     * <p>Uses the internal measurement order defined by the flowsheet configuration as
     * the reference sort order. Items not found in the flowsheet will be sorted to the
     * end (index -1 from {@code List.indexOf()}).</p>
     *
     * @param nonOrderedList ArrayList the list to sort in-place
     * @return ArrayList the same list, now sorted to match the flowsheet display order
     */
    public ArrayList sortToCurrentOrder(ArrayList nonOrderedList) {
        Collections.sort(nonOrderedList, new FlowSheetSort(getMeasurementList()));
        return nonOrderedList;
    }

    /**
     * Sets whether this flowsheet is universal (appears for all patients).
     *
     * @param universal boolean {@code true} if this flowsheet should appear regardless
     *        of diagnosis codes
     */
    public void setUniversal(boolean universal) {
        this.universal = universal;
    }

    /**
     * Returns whether this flowsheet is universal (appears for all patients).
     *
     * @return boolean {@code true} if this flowsheet appears regardless of diagnosis codes
     */
    public boolean isUniversal() {
        return universal;
    }

    /**
     * Returns whether this flowsheet contains medical measurements.
     *
     * @return boolean {@code true} if the flowsheet is medical (default),
     *         {@code false} for social determinant flowsheets
     */
    public boolean isMedical() {
        return isMedical;
    }

    /**
     * Sets whether this flowsheet contains medical measurements.
     *
     * @param medical boolean {@code true} for medical flowsheets,
     *        {@code false} for social determinant flowsheets (e.g., housing, finances)
     */
    public void setMedical(boolean medical) {
        isMedical = medical;
    }

    /**
     * Returns the filename of the optional HTML fragment displayed at the top of this flowsheet.
     *
     * @return String the HTML filename, or {@code null} if no top HTML is configured
     */
    public String getTopHTMLFileName() {
        return topHTMLFileName;
    }

    /**
     * Sets the filename of the optional HTML fragment displayed at the top of this flowsheet.
     *
     * @param topHTMLFileName String the HTML filename from the flowsheet XML configuration
     */
    public void setTopHTMLFileName(String topHTMLFileName) {
        this.topHTMLFileName = topHTMLFileName;
    }

    /**
     * Returns whether this flowsheet was created or customized by the clinic.
     *
     * @return boolean {@code true} if this is a clinic-customized flowsheet,
     *         {@code false} if it is a standard flowsheet shipped with the application
     */
    public boolean isCustom() {
        return custom;
    }

    /**
     * Sets whether this flowsheet was created or customized by the clinic.
     *
     * @param custom boolean {@code true} for clinic-customized flowsheets
     */
    public void setCustom(boolean custom) {
        this.custom = custom;
    }


    /**
     * Comparator that sorts items according to the display order defined in the flowsheet.
     *
     * <p>Uses the ordered measurement list from the flowsheet configuration as the reference
     * order. Elements are compared by their index position in that list. Items not present
     * in the reference list will have an index of -1 and will sort before all other items.</p>
     */
    class FlowSheetSort implements Comparator {

        /** Reference list defining the expected sort order. */
        List list = null;

        /**
         * Creates a FlowSheetSort with no reference order.
         */
        public FlowSheetSort() {
        }

        /**
         * Creates a FlowSheetSort using the specified list as the reference display order.
         *
         * @param sortedList List the reference order to sort by (typically from
         *        {@link MeasurementFlowSheet#getMeasurementList()})
         */
        public FlowSheetSort(List sortedList) {
            log.debug("SortedList " + sortedList);
            list = sortedList;
        }

        /**
         * Compares two objects by their position in the reference measurement list.
         *
         * @param o1 Object the first element to compare
         * @param o2 Object the second element to compare
         * @return int negative if o1 appears before o2, zero if same position, positive if after
         */
        public int compare(Object o1, Object o2) {
            log.debug(" o1 " + o1 + " o2 " + o2);
            int n1 = list.indexOf(o1);
            int n2 = list.indexOf(o2);
            // If this < o, return a negative
            if (n1 < n2) {
                return -1;
            } else if (n1 == n2) // If this = o, return 0
            {
                return 0;
            } else // If this > o, return a positive value
            {
                return 1;
            }

        }
    }
}
