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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.kie.api.KieBase;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.XMLOutputter;
import io.github.carlos_emr.carlos.commn.dao.FlowSheetUserCreatedDao;
import io.github.carlos_emr.carlos.commn.dao.FlowsheetDao;
import io.github.carlos_emr.carlos.commn.model.FlowSheetCustomization;
import io.github.carlos_emr.carlos.commn.model.FlowSheetUserCreated;
import io.github.carlos_emr.carlos.commn.model.Flowsheet;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.beans.factory.InitializingBean;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementTypeBeanHandler;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementTypesBean;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.data.ExportMeasurementType;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.data.ImportMeasurementTypes;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.DSCondition;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.Recommendation;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.RuleBaseCreator;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.TargetColour;

/**
 * Central configuration manager for clinical measurement flowsheet templates in CARLOS EMR.
 *
 * <p>This singleton class is the primary entry point for loading, managing, and resolving
 * flowsheet configurations that define which clinical measurements (e.g., blood pressure,
 * blood glucose, HbA1c) appear on a patient's flowsheet view, and what decision support
 * rules apply to those measurements. Flowsheets are clinical tools that present a patient's
 * measurement data in a tabular, time-ordered format to support care planning.</p>
 *
 * <h3>Flowsheet Sources</h3>
 * <p>Flowsheet definitions are loaded from three sources, in order:</p>
 * <ol>
 *   <li><strong>File-based flowsheets</strong> -- XML files injected via Spring configuration
 *       (the {@code flowSheets} property). These are the built-in, system-supplied flowsheets
 *       (e.g., diabetes, hypertension).</li>
 *   <li><strong>User-created flowsheets</strong> -- Stored in the database via
 *       {@link FlowSheetUserCreated} and managed through the {@link FlowSheetUserCreatedDao}.
 *       These allow clinics to define custom flowsheets with their own diagnosis code triggers
 *       and measurement items.</li>
 *   <li><strong>Database-stored flowsheets</strong> -- Full XML content stored in the
 *       {@link Flowsheet} entity (non-external entries), enabling runtime-editable
 *       flowsheet definitions without file system access.</li>
 * </ol>
 *
 * <h3>Trigger System</h3>
 * <p>Each flowsheet can be activated by one of three mechanisms:</p>
 * <ul>
 *   <li><strong>Diagnosis code triggers</strong> -- ICD/diagnostic codes in the patient's
 *       problem list automatically surface the relevant flowsheet (e.g., diabetes diagnosis
 *       triggers the diabetes flowsheet).</li>
 *   <li><strong>Program triggers</strong> -- Enrollment in a clinical program (e.g., CAISI
 *       community programs) activates associated flowsheets.</li>
 *   <li><strong>Universal flag</strong> -- Flowsheets marked as universal appear for all
 *       patients regardless of diagnosis or program enrollment.</li>
 * </ul>
 *
 * <h3>Decision Support Integration</h3>
 * <p>Flowsheet items can include decision support rules that are compiled into Drools
 * {@link org.kie.api.KieBase} instances. These rules evaluate measurement data (via
 * {@link MeasurementInfo}) and generate clinical recommendations or warnings. The DRL
 * rule text is generated programmatically by {@link RuleBaseCreator} from
 * {@link DSCondition} objects parsed from the flowsheet XML.</p>
 *
 * <h3>Personalization</h3>
 * <p>Flowsheets can be customized at three scopes (checked in priority order):
 * patient-specific, provider-specific, and clinic-wide. Customizations allow adding,
 * updating, or removing individual measurement items from a base flowsheet via
 * {@link FlowSheetCustomization} records.</p>
 *
 * <p>This class implements {@link InitializingBean} to register itself as the singleton
 * instance after Spring initialization. It uses lazy loading: flowsheet definitions are
 * not parsed until the first call to {@link #getInstance()}.</p>
 *
 * @see MeasurementFlowSheet
 * @see FlowSheetItem
 * @see RuleBaseCreator
 * @see DSCondition
 * @since 2001-01-01
 */
public class MeasurementTemplateFlowSheetConfig implements InitializingBean {

    private static Logger log = MiscUtils.getLogger();

    /** List of XML flowsheet definition files injected by Spring configuration. */
    private List<File> flowSheets;

    /** All diagnosis codes that serve as triggers across all loaded flowsheets. */
    ArrayList<String> dxTriggers = new ArrayList<String>();

    /** All program IDs that serve as triggers across all loaded flowsheets. */
    ArrayList<String> programTriggers = new ArrayList<String>();

    /**
     * Maps each diagnosis code to the list of flowsheet names it triggers.
     * A single diagnosis code can activate multiple flowsheets.
     */
    Hashtable<String, ArrayList<String>> dxTrigHash = new Hashtable<String, ArrayList<String>>();

    /**
     * Maps each program ID to the list of flowsheet names it triggers.
     * A single program can activate multiple flowsheets.
     */
    HashMap<String, ArrayList<String>> programTrigHash = new HashMap<String, ArrayList<String>>();

    /** Maps flowsheet internal names to their human-readable display names. */
    Hashtable<String, String> flowsheetDisplayNames = new Hashtable<String, String>();

    /** Names of flowsheets that appear for all patients regardless of diagnosis or program. */
    ArrayList<String> universalFlowSheets = new ArrayList<String>();

    /** Maps flowsheet names to their source XML files (file-based flowsheets only). */
    Hashtable<String, File> flowsheetFiles = new Hashtable<String, File>();

    /** Singleton instance, set via {@link #afterPropertiesSet()} during Spring initialization. */
    static MeasurementTemplateFlowSheetConfig measurementTemplateFlowSheetConfig;

    /** Loaded and parsed flowsheet configurations, keyed by flowsheet name. Null until first load. */
    Hashtable<String, MeasurementFlowSheet> flowsheets = null;

    /** Database-level settings for each flowsheet (enabled/disabled state, external flag). */
    HashMap<String, Flowsheet> flowsheetSettings = null;

    /**
     * Returns the mapping of flowsheet names to their source XML files on disk.
     *
     * <p>This map only contains file-based flowsheets (not database-stored or user-created ones).</p>
     *
     * @return Hashtable&lt;String, File&gt; map of flowsheet names to their XML definition files
     */
    public Hashtable<String, File> getFileMap() {
        return flowsheetFiles;
    }

    /**
     * Spring {@link InitializingBean} callback that registers this instance as the singleton.
     *
     * <p>Called automatically by the Spring container after all properties have been set.
     * This establishes the static singleton reference used by {@link #getInstance()}.</p>
     *
     * @throws Exception if initialization fails
     */
    public void afterPropertiesSet() throws Exception {
        measurementTemplateFlowSheetConfig = this;
    }

    /**
     * Private constructor enforcing singleton pattern.
     *
     * <p>Instantiation is managed by the Spring container. Access the singleton
     * via {@link #getInstance()}.</p>
     */
    private MeasurementTemplateFlowSheetConfig() {
    }


    /**
     * Returns the singleton instance, loading all flowsheet definitions on first access.
     *
     * <p>If flowsheets have not yet been loaded (i.e., this is the first call or
     * {@link #reloadFlowsheets()} was invoked), this method triggers a full load
     * from all three sources: file-based, user-created, and database-stored flowsheets.</p>
     *
     * @return MeasurementTemplateFlowSheetConfig the singleton configuration manager
     */
    static public MeasurementTemplateFlowSheetConfig getInstance() {
        if (measurementTemplateFlowSheetConfig.flowsheets == null) {
            measurementTemplateFlowSheetConfig.loadFlowsheets();
        }
        return measurementTemplateFlowSheetConfig;
    }

    /**
     * Resolves which flowsheets should be displayed for a patient based on their diagnosis codes.
     *
     * <p>Iterates through all registered diagnosis code triggers and checks if any match
     * the patient's active diagnosis codes. When a match is found, the corresponding
     * flowsheet names are collected. Duplicate flowsheet names are suppressed so each
     * flowsheet appears at most once in the result, even if multiple diagnosis codes
     * trigger the same flowsheet.</p>
     *
     * <p>Note: This method does not distinguish between coding systems (e.g., ICD-9 vs ICD-10).
     * All diagnosis codes are compared as plain strings.</p>
     *
     * @param coll List of String diagnosis codes from the patient's problem list
     * @return ArrayList&lt;String&gt; list of unique flowsheet names triggered by the diagnosis codes
     * @see #getFlowsheetForDxCode(String)
     */
    public ArrayList<String> getFlowsheetsFromDxCodes(List coll) {
        ArrayList<String> alist = new ArrayList<String>();

        // Iterate through known triggers rather than patient codes to leverage the trigger index
        log.debug("Triggers size " + dxTriggers.size());
        for (int i = 0; i < dxTriggers.size(); i++) {
            String dx = dxTriggers.get(i);
            log.debug("Checking dx " + dx);
            if (coll.contains(dx) && !alist.contains(dx)) {
                log.debug("coll contains " + dx);
                ArrayList<String> flowsheets = getFlowsheetForDxCode(dx);
                log.debug("Size of flowsheets for " + dx + " is " + flowsheets.size());
                // Add each matching flowsheet, preventing duplicates
                for (int j = 0; j < flowsheets.size(); j++) {
                    String flowsheet = flowsheets.get(j);
                    if (!alist.contains(flowsheet)) {
                        log.debug("adding flowsheet " + flowsheet);
                        alist.add(flowsheet);
                    }
                }
            }
        }
        log.debug("alist size " + alist.size());
        return alist;
    }

    /**
     * Resolves which flowsheets should be displayed for a patient based on their program enrollments.
     *
     * <p>Works identically to {@link #getFlowsheetsFromDxCodes(List)} but matches against
     * program IDs instead of diagnosis codes. Used for CAISI and other program-based care
     * models where flowsheet visibility is determined by program enrollment rather than
     * clinical diagnoses.</p>
     *
     * @param coll List&lt;String&gt; of program IDs the patient is enrolled in
     * @return ArrayList&lt;String&gt; list of unique flowsheet names triggered by program enrollment
     * @see #getFlowsheetForProgramId(String)
     */
    public ArrayList<String> getFlowsheetsFromPrograms(List<String> coll) {
        ArrayList<String> alist = new ArrayList<String>();

        log.debug("Triggers size " + programTriggers.size());
        for (int i = 0; i < programTriggers.size(); i++) {
            String programId = programTriggers.get(i);
            log.debug("Checking programId " + programId);
            if (coll.contains(programId) && !alist.contains(programId)) {
                ArrayList<String> flowsheets = getFlowsheetForProgramId(programId);
                log.debug("Size of flowsheets for " + programId + " is " + flowsheets.size());
                for (int j = 0; j < flowsheets.size(); j++) {
                    String flowsheet = flowsheets.get(j);
                    if (!alist.contains(flowsheet)) {
                        log.debug("adding flowsheet " + flowsheet);
                        alist.add(flowsheet);
                    }
                }
            }
        }
        log.debug("alist size " + alist.size());
        return alist;
    }

    /**
     * Returns the list of flowsheet names that are flagged as universal.
     *
     * <p>Universal flowsheets are displayed for every patient regardless of their
     * diagnosis codes or program enrollments.</p>
     *
     * @return ArrayList&lt;String&gt; names of all universal flowsheets
     */
    public ArrayList<String> getUniveralFlowsheets() {
        return universalFlowSheets;
    }

    /**
     * Returns the complete mapping of diagnosis codes to their triggered flowsheet names.
     *
     * @return Hashtable&lt;String, ArrayList&lt;String&gt;&gt; map where keys are diagnosis codes
     *         and values are lists of flowsheet names triggered by that code
     */
    public Hashtable<String, ArrayList<String>> getDxTrigHash() {
        return dxTrigHash;
    }

    /**
     * Returns the complete mapping of program IDs to their triggered flowsheet names.
     *
     * @return HashMap&lt;String, ArrayList&lt;String&gt;&gt; map where keys are program IDs
     *         and values are lists of flowsheet names triggered by that program
     */
    public HashMap<String, ArrayList<String>> getProgramTrigHash() {
        return programTrigHash;
    }

    /**
     * Returns the human-readable display name for a flowsheet.
     *
     * @param name String the internal flowsheet name (e.g., "DM" for diabetes mellitus)
     * @return String the display name shown in the UI, or {@code null} if not found
     */
    public String getDisplayName(String name) {
        return flowsheetDisplayNames.get(name);
    }

    /**
     * Returns the complete mapping of flowsheet internal names to display names.
     *
     * @return Hashtable&lt;String, String&gt; map of internal names to display names
     */
    public Hashtable<String, String> getFlowsheetDisplayNames() {
        return flowsheetDisplayNames;
    }


    /**
     * Registers a new flowsheet in the runtime configuration.
     *
     * <p>If the flowsheet has no name, an auto-generated name is assigned using the
     * pattern "U{n}" where n is the current number of loaded flowsheets plus one.
     * The flowsheet is added to the in-memory registry and its diagnosis code triggers
     * are indexed for later resolution.</p>
     *
     * @param m MeasurementFlowSheet the flowsheet to register
     * @return String the name assigned to the flowsheet (may be auto-generated)
     */
    public String addFlowsheet(MeasurementFlowSheet m) {
        if (m.getName() == null || m.getName().equals("")) {
            // Auto-generate a unique name for unnamed flowsheets
            m.setName("U" + (flowsheets.size() + 1));
        }

        flowsheets.put(m.getName(), m);
        flowsheetDisplayNames.put(m.getName(), m.getDisplayName());
        addTriggers(m.getDxTriggers(), m.getName());
        return m.getName();
    }

    /**
     * Enables a flowsheet by updating its database record and reloading all configurations.
     *
     * <p>If a database record exists for the flowsheet, its enabled flag is set to {@code true}.
     * If no record exists, a new one is created as an external, disabled flowsheet -- this
     * establishes the database tracking record for future enable/disable toggling.</p>
     *
     * @param name String the internal name of the flowsheet to enable
     */
    public void enableFlowsheet(String name) {
        FlowsheetDao flowsheetDao = (FlowsheetDao) SpringUtils.getBean(FlowsheetDao.class);
        Flowsheet fs = flowsheetDao.findByName(name);
        if (fs != null) {
            fs.setEnabled(true);
            flowsheetDao.merge(fs);
        } else {
            // Create a new tracking record for this externally-defined flowsheet
            fs = new Flowsheet();
            fs.setCreatedDate(new Date());
            fs.setEnabled(false);
            fs.setExternal(true);
            fs.setName(name);
            flowsheetDao.persist(fs);
        }
        reloadFlowsheets();
    }

    /**
     * Disables a flowsheet by updating its database record and reloading all configurations.
     *
     * <p>If a database record exists, its enabled flag is set to {@code false}. If no record
     * exists, a new disabled, external record is created for tracking purposes.</p>
     *
     * @param name String the internal name of the flowsheet to disable
     */
    public void disableFlowsheet(String name) {
        FlowsheetDao flowsheetDao = (FlowsheetDao) SpringUtils.getBean(FlowsheetDao.class);
        Flowsheet fs = flowsheetDao.findByName(name);
        if (fs != null) {
            fs.setEnabled(false);
            flowsheetDao.merge(fs);
        } else {
            // Create a new tracking record for this externally-defined flowsheet
            fs = new Flowsheet();
            fs.setCreatedDate(new Date());
            fs.setEnabled(false);
            fs.setExternal(true);
            fs.setName(name);
            flowsheetDao.persist(fs);
        }
        reloadFlowsheets();
    }

    /**
     * Clears all in-memory flowsheet data and triggers a complete reload from all sources.
     *
     * <p>This method resets all trigger indexes, display name mappings, and cached
     * flowsheet objects, then performs a fresh load. It should be called after any
     * change to flowsheet definitions (enable/disable, add, modify) to ensure
     * the runtime configuration is consistent with the persistent store.</p>
     */
    public void reloadFlowsheets() {
        dxTriggers = new ArrayList<String>();
        programTriggers = new ArrayList<String>();
        dxTrigHash = new Hashtable<String, ArrayList<String>>();
        programTrigHash = new HashMap<String, ArrayList<String>>();
        flowsheetDisplayNames = new Hashtable<String, String>();
        universalFlowSheets = new ArrayList<String>();
        flowsheets = null;
        flowsheetSettings = null;
        loadFlowsheets();
    }

    /**
     * Loads all flowsheet definitions from the three configuration sources.
     *
     * <p>This method performs the complete flowsheet initialization in three phases:</p>
     * <ol>
     *   <li><strong>Phase 1 -- File-based flowsheets:</strong> Reads each XML file from the
     *       Spring-injected {@code flowSheets} list, parses it into a {@link MeasurementFlowSheet},
     *       and categorizes it as universal, diagnosis-triggered, or program-triggered.</li>
     *   <li><strong>Phase 2 -- User-created flowsheets:</strong> Loads simplified flowsheet
     *       definitions from the database via {@link FlowSheetUserCreatedDao}. These have
     *       fewer configuration options and always receive default indicator colours.</li>
     *   <li><strong>Phase 3 -- Database-stored flowsheets:</strong> Loads full XML flowsheet
     *       content stored in the {@link Flowsheet} entity. External flowsheets (those whose
     *       definitions come from files, not the database) are skipped.</li>
     * </ol>
     *
     * <p>For each loaded flowsheet, the method also checks for an associated {@link Flowsheet}
     * database record to track enabled/disabled state.</p>
     *
     * @see #createflowsheet(EctMeasurementTypeBeanHandler, InputStream)
     */
    void loadFlowsheets() {
        FlowsheetDao flowsheetDao = (FlowsheetDao) SpringUtils.getBean(FlowsheetDao.class);
        FlowSheetUserCreatedDao flowSheetUserCreatedDao = (FlowSheetUserCreatedDao) SpringUtils.getBean(FlowSheetUserCreatedDao.class);

        flowsheets = new Hashtable<String, MeasurementFlowSheet>();
        flowsheetSettings = new HashMap<String, Flowsheet>();

        EctMeasurementTypeBeanHandler mType = new EctMeasurementTypeBeanHandler();

        // Phase 1: Load file-based flowsheet definitions from XML files on disk
        log.debug("LOADING FLOWSSHEETS");
        for (File flowSheet : flowSheets) {
            InputStream is = null;
            try {
                is = new FileInputStream(flowSheet);
                MeasurementFlowSheet d = createflowsheet(mType, is);
                flowsheets.put(d.getName(), d);

                // Categorize the flowsheet by its trigger type
                if (d.isUniversal())
                    universalFlowSheets.add(d.getName());
                else if (d.getDxTriggers() != null && d.getDxTriggers().length > 0) {
                    String[] dxTrig = d.getDxTriggers();
                    addTriggers(dxTrig, d.getName());
                } else if (d.getProgramTriggers() != null && d.getProgramTriggers().length > 0) {
                    String[] programTrig = d.getProgramTriggers();
                    addProgramTriggers(programTrig, d.getName());
                }

                flowsheetDisplayNames.put(d.getName(), d.getDisplayName());

                // Link to database settings record if one exists
                Flowsheet tmp = flowsheetDao.findByName(d.getName());
                if (tmp != null) {
                    flowsheetSettings.put(d.getName(), tmp);
                }
            } catch (Exception e) {
                MiscUtils.getLogger().error("error", e);
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        MiscUtils.getLogger().error("error", e);
                    }
                }
            }
        }

        // Phase 2: Load user-created flowsheets from database records
        // These are simpler definitions without full XML, using only basic properties
        List<FlowSheetUserCreated> flowSheetUserCreateds = flowSheetUserCreatedDao.getAllUserCreatedFlowSheets();
        for (FlowSheetUserCreated flowSheetUserCreated : flowSheetUserCreateds) {

            MeasurementFlowSheet m = new MeasurementFlowSheet();
            m.setName(flowSheetUserCreated.getName());
            m.parseDxTriggers(flowSheetUserCreated.getDxcodeTriggers());
            m.setDisplayName(flowSheetUserCreated.getDisplayName());
            m.setWarningColour(flowSheetUserCreated.getWarningColour());
            m.setRecommendationColour(flowSheetUserCreated.getRecommendationColour());
            flowsheets.put(m.getName(), m);
            String[] dxTrig = m.getDxTriggers();
            // Apply default indicator colours (HIGH_1, HIGH, LOW) to user-created flowsheets
            addIndicatorsInCustomFlowsheet(m);
            addTriggers(dxTrig, m.getName());
            flowsheetDisplayNames.put(m.getName(), m.getDisplayName());
            Flowsheet tmp = flowsheetDao.findByName(m.getName());
            if (tmp != null) {
                flowsheetSettings.put(m.getName(), tmp);
            }
        }

        // Phase 3: Load database-stored flowsheets with full XML content
        // Skip external flowsheets (those are file-based and already loaded in Phase 1)
        for (Flowsheet fs : flowsheetDao.findAll()) {
            if (fs.isExternal()) {
                continue;
            }
            String data = fs.getContent();
            InputStream is = null;
            try {
                is = new ByteArrayInputStream(data.getBytes("UTF-8"));
            } catch (UnsupportedEncodingException e) {
                MiscUtils.getLogger().error("error", e);
                continue;
            }
            MeasurementFlowSheet d = createflowsheet(mType, is);
            flowsheets.put(d.getName(), d);
            if (d.isUniversal())
                universalFlowSheets.add(d.getName());
            else if (d.getDxTriggers() != null && d.getDxTriggers().length > 0) {
                String[] dxTrig = d.getDxTriggers();
                addTriggers(dxTrig, d.getName());
            } else if (d.getProgramTriggers() != null && d.getProgramTriggers().length > 0) {
                String[] programTrig = d.getProgramTriggers();
                addProgramTriggers(programTrig, d.getName());
            }
            flowsheetDisplayNames.put(d.getName(), d.getDisplayName());
            flowsheetSettings.put(d.getName(), fs);
        }

    }

    /**
     * Returns the database-level settings for all loaded flowsheets.
     *
     * <p>These settings include enabled/disabled state and the external flag indicating
     * whether the flowsheet definition comes from a file or the database.</p>
     *
     * @return HashMap&lt;String, Flowsheet&gt; map of flowsheet names to their database settings
     */
    public HashMap<String, Flowsheet> getFlowsheetSettings() {
        return flowsheetSettings;
    }

    /**
     * Returns the list of flowsheet names triggered by a specific diagnosis code.
     *
     * @param code String the diagnosis code (e.g., ICD-9 or ICD-10 code)
     * @return ArrayList&lt;String&gt; list of flowsheet names, or {@code null} if no flowsheets
     *         are triggered by this code
     */
    public ArrayList<String> getFlowsheetForDxCode(String code) {
        return dxTrigHash.get(code);
    }

    /**
     * Returns the list of flowsheet names triggered by a specific program ID.
     *
     * @param code String the program ID
     * @return ArrayList&lt;String&gt; list of flowsheet names, or {@code null} if no flowsheets
     *         are triggered by this program
     */
    public ArrayList<String> getFlowsheetForProgramId(String code) {
        return programTrigHash.get(code);
    }

    /**
     * Indexes diagnosis code triggers for a flowsheet into the trigger lookup structures.
     *
     * <p>Each diagnosis code is added to the master trigger list (if not already present)
     * and mapped to the flowsheet name in the trigger hash. A single code can map to
     * multiple flowsheets, and duplicates are prevented in both structures.</p>
     *
     * @param dxTrig String array of diagnosis codes that trigger the flowsheet
     * @param name String the internal name of the flowsheet being triggered
     */
    private void addTriggers(String[] dxTrig, String name) {
        if (dxTrig != null) {
            for (String aDxTrig : dxTrig) {
                // Add to master list of all known trigger codes
                if (!dxTriggers.contains(aDxTrig)) {
                    dxTriggers.add(aDxTrig);
                }
                if (dxTrigHash.containsKey(aDxTrig)) {
                    ArrayList<String> l = dxTrigHash.get(aDxTrig);
                    if (!l.contains(name)) {
                        l.add(name);
                    }
                } else {
                    ArrayList<String> l = new ArrayList<String>();
                    l.add(name);
                    dxTrigHash.put(aDxTrig, l);
                }
            }
        }
    }

    /**
     * Indexes program triggers for a flowsheet into the program trigger lookup structures.
     *
     * <p>Works identically to {@link #addTriggers(String[], String)} but for program IDs
     * instead of diagnosis codes.</p>
     *
     * @param programTrig String array of program IDs that trigger the flowsheet
     * @param name String the internal name of the flowsheet being triggered
     */
    private void addProgramTriggers(String[] programTrig, String name) {
        if (programTrig != null) {
            for (String aProgramTrig : programTrig) {
                if (!programTriggers.contains(aProgramTrig)) {
                    programTriggers.add(aProgramTrig);
                }
                if (programTrigHash.containsKey(aProgramTrig)) {
                    ArrayList<String> l = programTrigHash.get(aProgramTrig);
                    if (!l.contains(name)) {
                        l.add(name);
                    }
                } else {
                    ArrayList<String> l = new ArrayList<String>();
                    l.add(name);
                    programTrigHash.put(aProgramTrig, l);
                }
            }
        }
    }


    /**
     * Creates a flowsheet from an XML input stream and registers it in the runtime configuration.
     *
     * <p>This is a convenience method that creates a new {@link EctMeasurementTypeBeanHandler},
     * delegates to the private {@link #createflowsheet(EctMeasurementTypeBeanHandler, InputStream)}
     * method, and then registers the resulting flowsheet in the in-memory maps.</p>
     *
     * @param is InputStream containing the flowsheet XML definition
     * @return MeasurementFlowSheet the parsed and registered flowsheet
     */
    public MeasurementFlowSheet createflowsheet(InputStream is) {
        EctMeasurementTypeBeanHandler mType = new EctMeasurementTypeBeanHandler();
        MeasurementFlowSheet d = createflowsheet(mType, is);


        flowsheets.put(d.getName(), d);
        flowsheetDisplayNames.put(d.getName(), d.getDisplayName());
        return d;
    }

    /**
     * Recursively processes XML elements to build the hierarchical item tree for a flowsheet.
     *
     * <p>This method handles two types of XML elements:</p>
     * <ul>
     *   <li><strong>{@code <header>}</strong> -- Creates a grouping node with children. Headers
     *       provide visual organization in the flowsheet UI (e.g., grouping related measurements
     *       like "Cardiovascular" or "Metabolic").</li>
     *   <li><strong>{@code <item>}</strong> -- Creates a leaf node representing an individual
     *       clinical measurement or prevention item. Items can include two types of decision
     *       support rules:
     *       <ul>
     *         <li><strong>{@code <rules>/<recommendation>}</strong> -- Time-based recommendations
     *             that fire when a measurement has not been recorded within a specified period
     *             (parsed into {@link Recommendation} objects).</li>
     *         <li><strong>{@code <ruleset>/<rule>}</strong> -- Value-based rules that color-code
     *             measurement values based on clinical thresholds (parsed into
     *             {@link TargetColour} objects and compiled into Drools rules).</li>
     *       </ul>
     *   </li>
     * </ul>
     *
     * @param elements List&lt;Element&gt; the XML child elements to process
     * @param aLevels List&lt;Node&gt; the output list of nodes at the current level of the hierarchy
     * @param parent Node the parent node (null for top-level elements)
     * @param mFlowSheet MeasurementFlowSheet the flowsheet being populated with items
     */
    private void processItems(List<Element> elements, List<Node> aLevels, Node parent, MeasurementFlowSheet mFlowSheet) {
        for (Element e : elements) {
            // Extract all XML attributes into a properties map for the FlowSheetItem
            Hashtable<String, String> h = new Hashtable<String, String>();
            List<Attribute> attr = e.getAttributes();
            for (Attribute att : attr) {
                h.put(att.getName(), att.getValue());
            }
            FlowSheetItem item = new FlowSheetItem(h);
            Node node = new Node();
            node.flowSheetItem = item;
            node.parent = parent;
            if (e.getName().equalsIgnoreCase("header")) {
                // Header elements are grouping containers -- recurse into their children
                List<Element> children = e.getChildren();
                node.children = new ArrayList<Node>();
                aLevels.add(node);
                processItems(children, node.children, node, mFlowSheet);
            } else if (e.getName().equalsIgnoreCase("item")) {

                // Determine the item type: either a clinical measurement or a prevention item
                String item_type = h.get("measurement_type");
                if (item_type == null) item_type = h.get("prevention_type");

                if (item_type != null) {

                    log.debug("ADDING " + item_type);

                    // Parse time-based recommendation rules (e.g., "review overdue" alerts)
                    // XML structure:
                    //   <rules>
                    //     <recommendation between="3m-6m">Message text</recommendation>
                    //     <recommendation gt="6m">Warning text</recommendation>
                    //   </rules>
                    int ruleCount = 0;
                    Element rules = e.getChild("rules");
                    if (rules != null) {
                        List<Element> recomends = rules.getChildren("recommendation");
                        List<Recommendation> ds = new ArrayList<Recommendation>();
                        for (Element reco : recomends) {
                            ruleCount++;
                            // Each recommendation gets a unique rule name: itemType + sequential number
                            ds.add(new Recommendation(reco, item_type + ruleCount, item_type));
                        }
                        MiscUtils.getLogger().debug("" + item_type + " adding ds  " + ds);
                        item.setRecommendations(ds);
                    }
                    //<rules>
                    //  <recommendation between="3m-6m">Blood Glucose hasn't been reviewed in $NUMMONTHS months"</recommendation>
                    //  <warning gt="6m">Blood Glucose hasn't been reviewed in $NUMMONTHS months</warning>
                    //  <warning eq="-1">Blood Glucose hasn't been reviewed</warning>
                    //</rules>

                    // Parse value-based threshold rules for color-coding measurement values.
                    // These define clinical ranges that map to indicator colours (e.g., HIGH, LOW).
                    // XML structure:
                    //   <ruleset>
                    //     <rule indicationColor="HIGH">
                    //       <condition type="getDataAsDouble" value="&gt;= 7" />
                    //     </rule>
                    //   </ruleset>
                    Element rulesets = e.getChild("ruleset");
                    List<TargetColour> rs = new ArrayList<TargetColour>();
                    if (rulesets != null) {
                        List<Element> rulez = rulesets.getChildren("rule");
                        if (rulez != null) {
                            for (Element r : rulez) {
                                rs.add(new TargetColour(r));
                            }
                        }

                    }

                    log.debug(" meas " + item_type + "  size " + rs.size());

                    if (rs.size() > 0) {
                        item.setTargetColour(rs);
                    }

                }

                // Register the item with the flowsheet and update the node reference
                // (addListItem may compile Drools rules and return an enriched item)
                item = mFlowSheet.addListItem(item);
                node.flowSheetItem = item;
                aLevels.add(node);
            }
        }

    }

    /**
     * Represents a node in the hierarchical tree structure of flowsheet items.
     *
     * <p>Flowsheet items are organized into a tree where {@code <header>} elements create
     * branch nodes with children, and {@code <item>} elements create leaf nodes. This
     * tree structure enables grouped display of measurements in the flowsheet UI
     * (e.g., all cardiovascular measurements under a single expandable header).</p>
     *
     * <p>The node provides stateful iteration via {@link #numSibling}, allowing sequential
     * traversal through children using {@link #getFirstChild()} followed by repeated
     * calls to {@link #getNextSibling()}.</p>
     *
     * @see FlowSheetItem
     * @see MeasurementFlowSheet#setItemHeirarchy(List)
     */
    public class Node {
        /** Reference to the parent node, or {@code null} for top-level nodes. */
        public Node parent;

        /** Child nodes (for header/grouping nodes), or {@code null} for leaf item nodes. */
        public List<Node> children;

        /** The flowsheet item data associated with this node. */
        public FlowSheetItem flowSheetItem;

        /**
         * Stateful sibling index used for iteration. Tracks the current position
         * within the parent's children list during traversal. Initialized to -1
         * (no iteration in progress).
         */
        public int numSibling = -1;

        /**
         * Returns the first child node and resets the sibling iteration counter.
         *
         * @return Node the first child, or {@code null} if this node has no children
         */
        public Node getFirstChild() {
            if (children != null && children.size() > 0) {
                numSibling = 0;
                return children.get(numSibling);
            }

            return null;
        }

        /**
         * Returns the next sibling node by advancing the parent's sibling counter.
         *
         * <p>This method modifies the parent's {@link #numSibling} state. It should be
         * called after {@link #getFirstChild()} to iterate through remaining siblings.</p>
         *
         * @return Node the next sibling, or {@code null} if no more siblings exist
         *         or this is a root node
         */
        public Node getNextSibling() {

            if (parent != null) {
                ++parent.numSibling;
                if (parent.numSibling < parent.children.size()) {
                    return parent.children.get(parent.numSibling);
                }
            }

            return null;
        }

        /**
         * Checks whether there are more siblings after the current position.
         *
         * @return boolean {@code true} if there is at least one more sibling to visit,
         *         {@code false} if this is a root node or all siblings have been visited
         */
        public boolean hasNextSibling() {
            if (parent == null) {
                return false;
            }

            return (parent.numSibling < parent.children.size() - 1);
        }
    }

    /**
     * Parses a flowsheet XML definition from an input stream into a {@link MeasurementFlowSheet}.
     *
     * <p>This is the core parsing method that converts XML into the runtime flowsheet model.
     * It performs the following steps:</p>
     * <ol>
     *   <li>Ensures all referenced measurement types exist in the database (via
     *       {@link ImportMeasurementTypes}).</li>
     *   <li>Parses colour indicators that map severity keys (e.g., "HIGH", "LOW") to
     *       CSS colour values.</li>
     *   <li>Recursively processes item and header elements via
     *       {@link #processItems(List, List, Node, MeasurementFlowSheet)}.</li>
     *   <li>Extracts flowsheet-level attributes: name, display name, HTML header file,
     *       decision support rules file, trigger codes, colours, and flags.</li>
     *   <li>Compiles all item-level decision support rules into a Drools
     *       {@link org.kie.api.KieBase} via {@link MeasurementFlowSheet#loadRuleBase()}.</li>
     * </ol>
     *
     * @param mType EctMeasurementTypeBeanHandler handler for resolving measurement type metadata
     * @param is InputStream containing the flowsheet XML definition
     * @return MeasurementFlowSheet the fully parsed flowsheet with compiled decision support rules
     */
    private MeasurementFlowSheet createflowsheet(final EctMeasurementTypeBeanHandler mType, InputStream is) {
        MeasurementFlowSheet d = new MeasurementFlowSheet();

        try {
            SAXBuilder parser = new SAXBuilder();
            Document doc = parser.build(is);
            Element root = doc.getRootElement();

            XMLOutputter outp = new XMLOutputter();

            // Ensure all measurement types referenced in this flowsheet exist in the database.
            // This auto-creates any missing measurement type definitions.
            ImportMeasurementTypes importMeasurementTypes = new ImportMeasurementTypes();
            importMeasurementTypes.importMeasurements(root);

            // Parse indicator colour mappings (e.g., key="LOW" colour="blue")
            List indi = root.getChildren("indicator");
            for (int i = 0; i < indi.size(); i++) {
                Element e = (Element) indi.get(i);
                d.AddIndicator(e.getAttributeValue("key"), e.getAttributeValue("colour"));
            }

            // Build the hierarchical item tree from all child elements
            List<Element> elements = root.getChildren();
            List<Element> items = root.getChildren("item");
            List<Node> aItems = new ArrayList<Node>();

            processItems(elements, aItems, null, d);
            d.setItemHeirarchy(aItems);

            // Extract flowsheet-level attributes from the root element
            if (root.getAttribute("name") != null) {
                d.setName(root.getAttribute("name").getValue());
            }
            if (root.getAttribute("display_name") != null) {
                d.setDisplayName(root.getAttribute("display_name").getValue());
            }

            if (root.getAttribute("top_HTML") != null) {
                d.setTopHTMLFileName(root.getAttribute("top_HTML").getValue());
            }

            // Load external DRL decision support rules file if specified
            if (root.getAttribute("ds_rules") != null && root.getAttribute("ds_rules").getValue().length() > 0) {
                d.loadRuleBase(root.getAttribute("ds_rules").getValue());
            }

            // Parse trigger codes that determine when this flowsheet appears
            if (root.getAttribute("dxcode_triggers") != null) {
                d.parseDxTriggers(root.getAttribute("dxcode_triggers").getValue());
            }

            if (root.getAttribute("program_triggers") != null) {
                d.parseProgramTriggers(root.getAttribute("program_triggers").getValue());
            }

            // Parse UI colour settings for warnings and recommendations
            if (root.getAttribute("warning_colour") != null) {
                d.setWarningColour(root.getAttribute("warning_colour").getValue());
            }
            if (root.getAttribute("recommendation_colour") != null) {
                d.setRecommendationColour(root.getAttribute("recommendation_colour").getValue());
            }

            // Parse boolean flags controlling flowsheet visibility and classification
            if (root.getAttribute("is_universal") != null) {
                d.setUniversal("true".equals(root.getAttribute("is_universal").getValue()));
            }
            if (root.getAttribute("is_medical") != null) {
                d.setMedical("true".equals(root.getAttribute("is_medical").getValue()));
            }

        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        // Compile all item-level decision support rules into the Drools KieBase
        d.loadRuleBase();
        return d;
    }

    /**
     * Validates a flowsheet XML string by attempting to parse it into a {@link MeasurementFlowSheet}.
     *
     * <p>This method performs the same parsing as
     * {@link #createflowsheet(EctMeasurementTypeBeanHandler, InputStream)} but does not
     * register the result in the runtime configuration. It is used to verify that user-supplied
     * or database-stored flowsheet XML is syntactically and structurally valid before saving.</p>
     *
     * <p>Unlike {@link #createflowsheet(InputStream)}, this method returns {@code null} on any
     * parsing error rather than throwing an exception, making it safe for validation workflows.</p>
     *
     * @param data String the flowsheet XML content to validate
     * @return MeasurementFlowSheet the parsed flowsheet if valid, or {@code null} if the XML
     *         cannot be parsed or contains structural errors
     */
    public MeasurementFlowSheet validateFlowsheet(String data) {
        InputStream is = null;
        try {
            is = new ByteArrayInputStream(data.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            MiscUtils.getLogger().error("error", e);
            return null;
        }

        MeasurementFlowSheet d = new MeasurementFlowSheet();

        try {
            SAXBuilder parser = new SAXBuilder();
            Document doc = parser.build(is);
            Element root = doc.getRootElement();

            XMLOutputter outp = new XMLOutputter();

            // Ensure all referenced measurement types exist in the database
            ImportMeasurementTypes importMeasurementTypes = new ImportMeasurementTypes();
            importMeasurementTypes.importMeasurements(root);

            // Parse indicator colour mappings
            List indi = root.getChildren("indicator");
            for (int i = 0; i < indi.size(); i++) {
                Element e = (Element) indi.get(i);
                d.AddIndicator(e.getAttributeValue("key"), e.getAttributeValue("colour"));
            }

            // Build the hierarchical item tree
            List<Element> elements = root.getChildren();
            List<Element> items = root.getChildren("item");
            List<Node> aItems = new ArrayList<Node>();

            processItems(elements, aItems, null, d);
            d.setItemHeirarchy(aItems);

            // Extract flowsheet-level attributes
            if (root.getAttribute("name") != null) {
                d.setName(root.getAttribute("name").getValue());
            }
            if (root.getAttribute("display_name") != null) {
                d.setDisplayName(root.getAttribute("display_name").getValue());
            }

            if (root.getAttribute("top_HTML") != null) {
                d.setTopHTMLFileName(root.getAttribute("top_HTML").getValue());
            }

            if (root.getAttribute("ds_rules") != null) {
                d.loadRuleBase(root.getAttribute("ds_rules").getValue());
            }
            if (root.getAttribute("dxcode_triggers") != null) {
                d.parseDxTriggers(root.getAttribute("dxcode_triggers").getValue());
            }

            if (root.getAttribute("program_triggers") != null) {
                d.parseProgramTriggers(root.getAttribute("program_triggers").getValue());
            }

            if (root.getAttribute("warning_colour") != null) {
                d.setWarningColour(root.getAttribute("warning_colour").getValue());
            }
            if (root.getAttribute("recommendation_colour") != null) {
                d.setRecommendationColour(root.getAttribute("recommendation_colour").getValue());
            }
            if (root.getAttribute("is_universal") != null) {
                d.setUniversal("true".equals(root.getAttribute("is_universal").getValue()));
            }
            if (root.getAttribute("is_medical") != null) {
                d.setMedical("true".equals(root.getAttribute("is_medical").getValue()));
            }

        } catch (Exception e) {
            MiscUtils.getLogger().error("error", e);
            return null;
        }

        // Compile decision support rules even during validation to catch DRL compilation errors
        d.loadRuleBase();
        return d;
    }

    /**
     * Generates a Drools DRL rule string for a time-based clinical recommendation or warning.
     *
     * <p>This method translates the XML-based recommendation/warning configuration into a
     * DRL (Drools Rule Language) rule that checks against a {@link MeasurementInfo} fact
     * object. The generated rule checks how many months have elapsed since the measurement
     * was last recorded and fires an appropriate recommendation or warning.</p>
     *
     * <h4>Month Range Parsing</h4>
     * <p>The {@code monthrange} value from the {@code recowarn} hashtable supports four formats:</p>
     * <ul>
     *   <li><strong>Between (e.g., "3-6"):</strong> Fires when months since last recording
     *       is between the two values (inclusive). Creates two {@link DSCondition} objects
     *       with {@code >=} and {@code <=} operators.</li>
     *   <li><strong>Greater than (e.g., "&gt;6" or ">6"):</strong> Fires when months exceed
     *       the threshold. Handles both XML-escaped and literal {@code >} characters.</li>
     *   <li><strong>Less than (e.g., "&lt;3" or "<3"):</strong> Fires when months are below
     *       the threshold. Handles both XML-escaped and literal {@code <} characters.</li>
     *   <li><strong>Exact match (e.g., "-1"):</strong> Fires when months equals the value
     *       exactly. A value of {@code -1} typically indicates "never recorded".</li>
     * </ul>
     *
     * <h4>DRL Generation</h4>
     * <p>The method delegates to {@link RuleBaseCreator#getRule(String, String, List, String)}
     * to produce the final DRL text. The generated rule uses condition expressions
     * that call {@code MeasurementInfo.getLastDateRecordedInMonths()} for each condition.
     * The consequence calls either {@code addRecommendation()} or {@code addWarning()}
     * on the {@link MeasurementInfo} fact.</p>
     *
     * <p>If the {@code recowarn} hashtable contains a "text" key, that custom message is
     * used as the consequence text. The placeholder {@code $NUMMONTHS} in the text is
     * replaced with a dynamic expression that inserts the actual elapsed months at runtime.</p>
     *
     * @param ruleName String unique name for the generated DRL rule
     * @param measurement String the measurement type code (e.g., "BP", "HBA1C")
     * @param recowarn Hashtable&lt;String, String&gt; containing rule parameters:
     *        "monthrange" (required) -- the time threshold expression,
     *        "strength" (optional) -- "warning" for warnings, defaults to recommendation,
     *        "text" (optional) -- custom message text with optional $NUMMONTHS placeholder
     * @return String the complete DRL rule definition text ready for compilation
     * @see RuleBaseCreator#getRule(String, String, List, String)
     * @see DSCondition
     * @see MeasurementInfo
     */
    protected String getRuleBaseElement(String ruleName, String measurement, Hashtable<String, String> recowarn) {

        log.debug("LOADING RULES - getRuleBaseElement");
        ArrayList<DSCondition> list = new ArrayList<DSCondition>();
        String toParse = recowarn.get("monthrange");

        // Determine consequence type: defaults to "Recommendation" unless strength is "warning"
        String consequenceType = "Recommendation";
        if (recowarn.get("strength") != null) {
            if (recowarn.get("strength").equals("warning")) {
                consequenceType = "Warning";
            }
        }
        String consequence = "";

        // Build the dynamic $NUMMONTHS replacement expression.
        // When inserted into the DRL consequence string, this expression will be evaluated
        // at runtime to produce the actual number of months since last recording.
        String NUMMONTHS = "\"+m.getLastDateRecordedInMonths(\"" + measurement + "\")+\"";

        // Parse the monthrange value to determine which comparison operators to use.
        // The format determines the type of time-based condition:
        //   "3-6"   -> between (3 <= months <= 6)
        //   ">6"    -> greater than 6 months
        //   "<3"    -> less than 3 months
        //   "-1"    -> exact match (never recorded)
        if (toParse.indexOf("-") != -1 && toParse.indexOf("-") != 0) {
            // Between style: contains "-" but not at position 0 (to exclude negative numbers)
            String[] betweenVals = toParse.split("-");
            if (betweenVals.length == 2) {
                list.add(new DSCondition("getLastDateRecordedInMonths", measurement, ">=", betweenVals[0]));
                list.add(new DSCondition("getLastDateRecordedInMonths", measurement, "<=", betweenVals[1]));
                if (recowarn.get("text") == null) {
                    consequence = "m.add" + consequenceType + "(\"" + measurement + "\",\"" + measurement + "\" 1 hasn't been reviewed in \"+m.getLastDateRecordedInMonths(\"" + measurement + "\")+\" months\";";
                }
            }

        } else if (toParse.indexOf("&gt;") != -1 || toParse.indexOf(">") != -1) {
            // Greater than style: handles both XML-escaped "&gt;" and literal ">"
            toParse = toParse.replaceFirst("&gt;", "");
            toParse = toParse.replaceFirst(">", "");

            int gt = Integer.parseInt(toParse);

            list.add(new DSCondition("getLastDateRecordedInMonths", measurement, ">", "" + gt));
            if (recowarn.get("text") == null) {
                consequence = "m.add" + consequenceType + "(\"" + measurement + "\",\"" + measurement + "\" 2 hasn't been reviewed in \"+m.getLastDateRecordedInMonths(\"" + measurement + "\")+\" months\";";
            }
        } else if (toParse.indexOf("&lt;") != -1 || toParse.indexOf("<") != -1) {
            // Less than style: handles both XML-escaped "&lt;" and literal "<"
            toParse = toParse.replaceFirst("&lt;", "");
            toParse = toParse.replaceFirst("<", "");

            int lt = Integer.parseInt(toParse);
            list.add(new DSCondition("getLastDateRecordedInMonths", measurement, "<=", "" + lt));
            if (recowarn.get("text") == null) {
                consequence = "m.add" + consequenceType + "(\"" + measurement + "\",\"" + measurement + "\" 3 hasn't been reviewed in \"+m.getLastDateRecordedInMonths(\"" + measurement + "\")+\" months\";";
            }

        } else if (!toParse.isEmpty()) {
            // Exact match style: matches a specific month value (e.g., -1 for "never recorded")
            int eq = Integer.parseInt(toParse);
            list.add(new DSCondition("getLastDateRecordedInMonths", measurement, "==", "" + eq));
            if (recowarn.get("text") == null) {
                consequence = "m.add" + consequenceType + "(\"" + measurement + "\",\"" + measurement + "\" 4 hasn'taaaaa been reviewed in \"+m.getLastDateRecordedInMonths(\"" + measurement + "\")+\" months\";";
            }
        }

        // If custom text was provided, use it as the consequence message.
        // Replace the $NUMMONTHS placeholder with the dynamic runtime expression
        // that resolves to the actual elapsed months.
        if (recowarn.get("text") != null) {
            String txt = recowarn.get("text");
            log.debug("TRY TO REPLACE $NUMMONTHS:" + txt.indexOf("$NUMMONTHS") + " WITH " + NUMMONTHS + " " + txt);

            txt = txt.replaceAll("\\$NUMMONTHS", NUMMONTHS);
            log.debug("TEXT " + txt);
            consequence = "m.add" + consequenceType + "(\"" + measurement + "\",\"" + txt + "\");";
        }

        // Delegate to RuleBaseCreator to assemble the final DRL rule text.
        // The rule checks MeasurementInfo facts and fires the consequence
        // when all DSCondition predicates are satisfied.
        RuleBaseCreator rcb = new RuleBaseCreator();
        String ruleText = rcb.getRule(ruleName, "io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementInfo", list, consequence);


        return ruleText;
    }


    /**
     * Returns a personalized flowsheet by applying a list of customizations to a base flowsheet.
     *
     * <p>Creates a deep copy of the base flowsheet (via XML serialization/deserialization round-trip)
     * and then applies each {@link FlowSheetCustomization} in order. Supported customization
     * actions are:</p>
     * <ul>
     *   <li>{@link FlowSheetCustomization#ADD} -- Adds a new measurement item after the
     *       specified measurement position. The item's XML payload is parsed and its
     *       value-based rules are compiled.</li>
     *   <li>{@link FlowSheetCustomization#UPDATE} -- Replaces an existing measurement item's
     *       configuration (display name, guidelines, rules, etc.).</li>
     *   <li>{@link FlowSheetCustomization#DELETE} -- Hides a measurement from the flowsheet
     *       display without removing it from the underlying data.</li>
     * </ul>
     *
     * <p>If the customization list is empty, or if any error occurs during customization,
     * the unmodified base flowsheet is returned as a fallback.</p>
     *
     * @param flowsheetName String the internal name of the base flowsheet to customize
     * @param list List&lt;FlowSheetCustomization&gt; the customizations to apply
     * @return MeasurementFlowSheet the personalized flowsheet, or the base flowsheet on error
     * @see FlowSheetCustomization
     */
    public MeasurementFlowSheet getFlowSheet(String flowsheetName, List<FlowSheetCustomization> list) {
        log.debug("IN CUSTOMIZED FLOWSHEET ");
        if (list.size() > 0) {
            log.debug("IN CUSTOMIZED FLOWSHEET " + list.size());
            try {
                // Create a deep copy of the base flowsheet via XML round-trip
                MeasurementFlowSheet personalizedFlowsheet = makeNewFlowsheet(getFlowSheet(flowsheetName));

                // Apply each customization action in order
                for (FlowSheetCustomization cust : list) {
                    if (FlowSheetCustomization.ADD.equals(cust.getAction())) {
                        log.debug(" CUST ADDING");
                        FlowSheetItem item = getItemFromString(cust.getPayload());
                        // Compile value-based threshold rules for the new item if present
                        if (item.getTargetColour() != null && item.getTargetColour().size() > 0) {
                            KieBase rb = personalizedFlowsheet.loadMeasurementRuleBase(item.getTargetColour());
                            item.setRuleBase(rb);
                        }
                        personalizedFlowsheet.addAfter(cust.getMeasurement(), item);
                    } else if (FlowSheetCustomization.UPDATE.equals(cust.getAction())) {
                        log.debug(" CUST UPDATING");
                        FlowSheetItem item = getItemFromString(cust.getPayload());
                        if (item.getTargetColour() != null && item.getTargetColour().size() > 0) {
                            KieBase rb = personalizedFlowsheet.loadMeasurementRuleBase(item.getTargetColour());
                            item.setRuleBase(rb);
                        }
                        personalizedFlowsheet.updateMeasurementFlowSheetInfo(cust.getMeasurement(), item);


                    } else if (FlowSheetCustomization.DELETE.equals(cust.getAction())) {
                        personalizedFlowsheet.setToHidden(cust.getMeasurement());
                        log.debug(" CUST DELETE");
                    } else {
                        log.debug("ERR" + cust);
                    }
                }
                // Recompile the Drools rule base after all customizations are applied
                personalizedFlowsheet.loadRuleBase();
                return personalizedFlowsheet;
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
            }
        }
        log.debug("Returning normal flowsheet");
        return getFlowSheet(flowsheetName);
    }

    /**
     * Returns a flowsheet resolved with scope-based overrides (patient, provider, then clinic).
     *
     * <p>Looks up custom flowsheet definitions using a cascading scope resolution strategy.
     * The scopes are checked in priority order:</p>
     * <ol>
     *   <li><strong>Patient scope</strong> -- A flowsheet customized specifically for this patient.</li>
     *   <li><strong>Provider scope</strong> -- A flowsheet customized by the provider for all
     *       their patients.</li>
     *   <li><strong>Clinic scope</strong> -- A flowsheet customized at the clinic level for
     *       all patients.</li>
     * </ol>
     *
     * <p>If no custom definition is found at any scope, the built-in (out-of-the-box)
     * flowsheet is returned. Custom flowsheet XML has its legacy namespace stripped
     * before parsing to ensure compatibility.</p>
     *
     * @param flowsheetName String the internal name of the base flowsheet
     * @param providerNo String the provider number for provider-scope lookup
     * @param demographicNo Integer the patient demographic number for patient-scope lookup;
     *        if {@code null}, scope resolution is skipped
     * @return MeasurementFlowSheet the resolved flowsheet at the most specific scope available
     */
    public MeasurementFlowSheet getFlowSheet(String flowsheetName, String providerNo, Integer demographicNo) {
        FlowSheetUserCreatedDao flowSheetUserCreatedDao = (FlowSheetUserCreatedDao) SpringUtils.getBean(FlowSheetUserCreatedDao.class);

        MeasurementFlowSheet m = null;
        FlowSheetUserCreated fsuc = null;

        // Cascading scope resolution: patient -> provider -> clinic
        if (demographicNo != null) {
            fsuc = flowSheetUserCreatedDao.findByPatientScope(flowsheetName, demographicNo);
            if (fsuc == null) {
                fsuc = flowSheetUserCreatedDao.findByProviderScope(flowsheetName, providerNo);
                if (fsuc == null) {
                    fsuc = flowSheetUserCreatedDao.findByClinicScope(flowsheetName);
                }
            }
        }

        if (fsuc != null) {
            // Use the custom flowsheet, stripping the legacy XML namespace for compatibility
            InputStream targetStream = IOUtils.toInputStream(fsuc.getXmlContent().replaceAll("<flowsheet xmlns=\"flowsheets.oscarehr.org\"", "<flowsheet"));
            EctMeasurementTypeBeanHandler mType = new EctMeasurementTypeBeanHandler();
            m = createflowsheet(mType, targetStream);

        } else {
            // No custom definition found; fall back to the built-in flowsheet
            m = getFlowSheet(flowsheetName);
        }

        return m;
    }

    /**
     * Returns a custom flowsheet by display name with scope-based resolution, or {@code null} if none exists.
     *
     * <p>Similar to {@link #getFlowSheet(String, String, Integer)} but uses the flowsheet's
     * display name for lookup instead of its internal identifier. This supports scenarios where
     * the user searches for a flowsheet by its human-readable name.</p>
     *
     * <p>Unlike the ID-based variant, this method returns {@code null} (rather than a built-in
     * fallback) when no custom flowsheet is found at any scope, since display name lookups
     * may not correspond to a built-in flowsheet.</p>
     *
     * @param flowsheetName String the display name of the flowsheet to look up
     * @param providerNo String the provider number for provider-scope lookup
     * @param demographicNo Integer the patient demographic number for patient-scope lookup;
     *        if {@code null}, scope resolution is skipped
     * @return MeasurementFlowSheet the resolved custom flowsheet, or {@code null} if no
     *         custom definition exists at any scope
     */
    public MeasurementFlowSheet getFlowSheetByName(String flowsheetName, String providerNo, Integer demographicNo) {
        FlowSheetUserCreatedDao flowSheetUserCreatedDao = (FlowSheetUserCreatedDao) SpringUtils.getBean(FlowSheetUserCreatedDao.class);

        MeasurementFlowSheet m = null;
        FlowSheetUserCreated fsuc = null;

        // Cascading scope resolution by display name: patient -> provider -> clinic
        if (demographicNo != null) {
            fsuc = flowSheetUserCreatedDao.findByPatientScopeName(flowsheetName, demographicNo);
            if (fsuc == null) {
                fsuc = flowSheetUserCreatedDao.findByProviderScopeName(flowsheetName, providerNo);
                if (fsuc == null) {
                    fsuc = flowSheetUserCreatedDao.findByClinicScopeName(flowsheetName);
                }
            }
        }

        if (fsuc != null) {
            // Use the custom flowsheet, stripping the legacy XML namespace for compatibility
            InputStream targetStream = IOUtils.toInputStream(fsuc.getXmlContent().replaceAll("<flowsheet xmlns=\"flowsheets.oscarehr.org\"", "<flowsheet"));
            EctMeasurementTypeBeanHandler mType = new EctMeasurementTypeBeanHandler();
            m = createflowsheet(mType, targetStream);

        }

        return m;
    }

    /**
     * Returns the base (non-customized) flowsheet by its internal name.
     *
     * @param flowsheetName String the internal name of the flowsheet (e.g., "DM", "HTN")
     * @return MeasurementFlowSheet the flowsheet, or {@code null} if no flowsheet with that
     *         name is loaded
     */
    public MeasurementFlowSheet getFlowSheet(String flowsheetName) {
        log.debug("GET FLOWSHEET " + flowsheetName + "  " + flowsheets.get(flowsheetName));
        return flowsheets.get(flowsheetName);
    }

    /**
     * Returns the list of XML flowsheet definition files injected by Spring.
     *
     * @return List&lt;File&gt; the file-based flowsheet definition files
     */
    public List<File> getFlowSheets() {
        return flowSheets;
    }

    /**
     * Sets the list of XML flowsheet definition files, called by Spring during bean initialization.
     *
     * <p>This is a Spring property setter. The flowsheet files are typically configured in the
     * Spring application context XML with a list of classpath or filesystem references to
     * flowsheet XML definitions.</p>
     *
     * @param flowSheets List&lt;File&gt; the flowsheet definition files to load
     */
    public void setFlowSheets(List<File> flowSheets) {
        log.debug("SETTING FLOWSHEETS");
        this.flowSheets = flowSheets;
    }


    /**
     * Creates a deep copy of a flowsheet via XML serialization and re-parsing.
     *
     * <p>This method serializes the given flowsheet to XML using
     * {@link #getExportFlowsheet(MeasurementFlowSheet)}, then re-parses that XML into
     * a new {@link MeasurementFlowSheet} instance. This XML round-trip ensures a fully
     * independent copy that can be safely modified (e.g., for per-patient customizations)
     * without affecting the cached base flowsheet.</p>
     *
     * @param mFlowsheet MeasurementFlowSheet the source flowsheet to copy
     * @return MeasurementFlowSheet a new independent copy of the flowsheet
     * @throws Exception if XML serialization or parsing fails
     */
    public MeasurementFlowSheet makeNewFlowsheet(MeasurementFlowSheet mFlowsheet) throws Exception {
        XMLOutputter outp = new XMLOutputter();
        Element va = getExportFlowsheet(mFlowsheet);

        // Serialize to XML bytes and re-parse to create an independent copy
        ByteArrayOutputStream byteArrayout = new ByteArrayOutputStream();
        outp.output(va, byteArrayout);

        InputStream is = new ByteArrayInputStream(byteArrayout.toByteArray());

        EctMeasurementTypeBeanHandler mType = new EctMeasurementTypeBeanHandler();
        MeasurementFlowSheet d = createflowsheet(mType, is);

        return d;

    }

    /**
     * Parses an XML string representation of a single flowsheet item into a {@link FlowSheetItem}.
     *
     * <p>Used to deserialize flowsheet item payloads stored in {@link FlowSheetCustomization}
     * records. The XML string should represent a single {@code <item>} element with optional
     * nested {@code <rules>} and {@code <ruleset>} children for decision support rules.</p>
     *
     * <p>The method parses both time-based recommendations ({@code <rules>/<recommendation>})
     * and value-based threshold rules ({@code <ruleset>/<rule>}) from the XML, identical to
     * the parsing done in {@link #processItems(List, List, Node, MeasurementFlowSheet)}.</p>
     *
     * @param s String the XML representation of a flowsheet item
     * @return FlowSheetItem the parsed item with its rules and attributes, or {@code null}
     *         if parsing fails
     * @see FlowSheetCustomization#getPayload()
     */
    public FlowSheetItem getItemFromString(String s) {
        log.debug("->>>" + s);
        FlowSheetItem item = null;
        try {
            SAXBuilder parser = new SAXBuilder();
            Document doc = parser.build(new StringReader(s));
            Element root = doc.getRootElement();

            // Extract all XML attributes into a properties map
            List<Attribute> attr = root.getAttributes();
            Hashtable<String, String> h = new Hashtable<String, String>();
            for (Attribute att : attr) {
                h.put(att.getName(), att.getValue());
            }
            item = new FlowSheetItem(h);

            // Parse time-based recommendation rules
            int ruleCount = 0;
            Element rules = root.getChild("rules");

            if (rules != null) {
                List<Element> recomends = rules.getChildren("recommendation");
                List<Recommendation> ds = new ArrayList<Recommendation>();
                for (Element reco : recomends) {
                    ruleCount++;
                    ds.add(new Recommendation(reco, "" + h.get("measurement_type") + ruleCount, "" + h.get("measurement_type")));
                }
                log.debug("" + h.get("measurement_type") + " adding ds  " + ds);
                item.setRecommendations(ds);
            }

            // Parse value-based threshold rules (colour-coding for clinical ranges)
            Element rulesets = root.getChild("ruleset");
            List<TargetColour> rs = new ArrayList<TargetColour>();
            if (rulesets != null) {
                List<Element> rulez = rulesets.getChildren("rule");
                if (rulez != null) {
                    for (Element r : rulez) {
                        rs.add(new TargetColour(r));
                    }
                }

            }

            log.debug(" meas " + h.get("measurement_type") + "  size " + rs.size());

            if (rs.size() > 0) {
                item.setTargetColour(rs);

            }


        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return item;
    }


    /**
     * Serializes a {@link FlowSheetItem} into a JDOM2 XML {@link Element} for export.
     *
     * <p>This is the inverse of {@link #getItemFromString(String)}. It converts a runtime
     * {@link FlowSheetItem} back into XML format suitable for storage in the database
     * or for inclusion in an exported flowsheet definition. All item attributes, time-based
     * recommendation rules, and value-based threshold rules are serialized.</p>
     *
     * @param fsi FlowSheetItem the item to serialize
     * @return Element the JDOM2 XML element representing the item
     * @see #getItemFromString(String)
     */
    public Element getItemFromObject(FlowSheetItem fsi) {
        Element item = new Element("item");

        Map h2 = fsi.getAllFields();

        // Serialize item attributes, skipping any that are null
        addAttributeifValueNotNull(item, "prevention_type", fsi.getPreventionType());
        addAttributeifValueNotNull(item, "measurement_type", fsi.getMeasurementType());
        addAttributeifValueNotNull(item, "display_name", fsi.getDisplayName());
        addAttributeifValueNotNull(item, "guideline", fsi.getGuideline());
        addAttributeifValueNotNull(item, "graphable", (String) h2.get("graphable"));
        addAttributeifValueNotNull(item, "ds_rules", (String) h2.get("ds_rules"));
        addAttributeifValueNotNull(item, "value_name", fsi.getValueName());

        // Serialize decision support rules only for measurement items (not prevention items)
        if (h2.get("measurement_type") != null) {
            log.debug("MEASUREMENT TYPE " + (String) h2.get("measurement_type"));

            // Serialize time-based recommendations into <rules> element
            List<Recommendation> dsR = fsi.getRecommendations();
            log.debug(h2.get("measurement_type") + " LIST DSR " + dsR);
            if (dsR != null) {
                Element rules = new Element("rules");
                for (Recommendation e : dsR) {
                    log.debug("BEFORE ADDING ");
                    rules.addContent(e.getFlowsheetXML());
                    log.debug(rules);
                }
                item.addContent(rules);
            }

            // Serialize value-based threshold rules into <ruleset> element
            List<TargetColour> targetColour = fsi.getTargetColour();
            log.debug("TARGET COLOURS" + targetColour);

            if (targetColour != null) {
                Element ruleset = new Element("ruleset");

                for (TargetColour t : targetColour) {
                    ruleset.addContent(t.getFlowsheetXML());
                }
                item.addContent(ruleset);
            }
        }
        return item;
    }


    /**
     * Exports an entire flowsheet to a JDOM2 XML {@link Element} for serialization.
     *
     * <p>This method produces a complete XML representation of a {@link MeasurementFlowSheet},
     * including:</p>
     * <ul>
     *   <li>Flowsheet-level attributes (name, display name, colours, triggers, HTML header).</li>
     *   <li>Indicator colour definitions (e.g., HIGH_1 = red, LOW = blue).</li>
     *   <li>All measurement and prevention items with their rules.</li>
     *   <li>Measurement type definitions via {@link ExportMeasurementType}, allowing the
     *       exported XML to be self-contained and importable on another system.</li>
     * </ul>
     *
     * <p>This method is used by {@link #makeNewFlowsheet(MeasurementFlowSheet)} for deep
     * copying and can also be used for flowsheet export/backup functionality.</p>
     *
     * @param mFlowsheet MeasurementFlowSheet the flowsheet to export
     * @return Element the root JDOM2 {@code <flowsheet>} element containing the full definition
     */
    public Element getExportFlowsheet(MeasurementFlowSheet mFlowsheet) {
        EctMeasurementTypeBeanHandler mType = new EctMeasurementTypeBeanHandler();
        Element va = new Element("flowsheet");


        // Serialize flowsheet-level attributes
        addAttributeifValueNotNull(va, "name", mFlowsheet.getName());
        addAttributeifValueNotNull(va, "display_name", mFlowsheet.getDisplayName());
        addAttributeifValueNotNull(va, "warning_colour", mFlowsheet.getWarningColour());
        addAttributeifValueNotNull(va, "recommendation_colour", mFlowsheet.getRecommendationColour());
        addAttributeifValueNotNull(va, "top_HTML", mFlowsheet.getTopHTMLStream());
        addAttributeifValueNotNull(va, "dxcode_triggers", mFlowsheet.getDxTriggersString());
        addAttributeifValueNotNull(va, "program_triggers", mFlowsheet.getProgramTriggersString());

        // Serialize indicator colour mappings (severity key -> CSS colour)
        Hashtable indicatorHash = mFlowsheet.getIndicatorHashtable();
        Enumeration enu = indicatorHash.keys();
        while (enu.hasMoreElements()) {
            String key = (String) enu.nextElement();
            Element ind = new Element("indicator");
            addAttributeifValueNotNull(ind, "key", key);
            addAttributeifValueNotNull(ind, "colour", (String) indicatorHash.get(key));
            va.addContent(ind);
        }

        List<String> measurements = mFlowsheet.getMeasurementList();

        log.debug("SET HAS MEASUREMENTS" + measurements);
        int count = 0;
        if (measurements != null) {
            for (String mstring : measurements) {

                EctMeasurementTypesBean measurementTypesBean = mType.getMeasurementType(mstring);

                Map h2 = mFlowsheet.getMeasurementFlowSheetInfo(mstring);

                Element item = new Element("item");


                addAttributeifValueNotNull(item, "prevention_type", (String) h2.get("prevention_type"));
                addAttributeifValueNotNull(item, "measurement_type", (String) h2.get("measurement_type"));
                addAttributeifValueNotNull(item, "display_name", (String) h2.get("display_name"));
                addAttributeifValueNotNull(item, "guideline", (String) h2.get("guideline"));
                addAttributeifValueNotNull(item, "graphable", (String) h2.get("graphable"));
                addAttributeifValueNotNull(item, "value_name", (String) h2.get("value_name"));
                addAttributeifValueNotNull(item, "ds_rules", (String) h2.get("ds_rules"));
                if (h2.get("measurement_type") != null) {
                    log.debug("MEASUREMENT TYPE " + (String) h2.get("measurement_type"));

                    // Serialize time-based recommendations into <rules> child element
                    List<Recommendation> dsR = mFlowsheet.getDSElements((String) h2.get("measurement_type"));
                    log.debug(h2.get("measurement_type") + " LIST DSR " + dsR);
                    if (dsR != null) {
                        Element rules = new Element("rules");
                        for (Recommendation e : dsR) {
                            log.debug("BEFORE ADDING ");
                            rules.addContent(e.getFlowsheetXML());
                            log.debug(rules);
                        }
                        item.addContent(rules);
                    }

                    // Serialize value-based threshold rules into <ruleset> child element
                    FlowSheetItem fsi = mFlowsheet.getFlowSheetItem(mstring);  //TODO: MOVE THIS UP AND REPLACE THE CODE ABOVE
                    List<TargetColour> targetColour = fsi.getTargetColour();
                    log.debug("TARGET COLOURS" + targetColour);

                    if (targetColour != null) {
                        Element ruleset = new Element("ruleset");

                        for (TargetColour t : targetColour) {
                            ruleset.addContent(t.getFlowsheetXML());
                        }
                        item.addContent(ruleset);
                    }

                }

                va.addContent(item);
                count++;


                // Include the measurement type definition for self-contained export
                if (measurementTypesBean != null) {
                    ExportMeasurementType emt = new ExportMeasurementType();
                    Element export = emt.exportElement(measurementTypesBean);
                    va.addContent(export);
                } else {
                    log.debug("--- not loaded --- " + mstring);
                }
            }
        }
        return va;
    }

    /**
     * Conditionally sets an XML attribute on an element, skipping null values.
     *
     * <p>Helper method to avoid setting attributes with null values, which would
     * cause a JDOM2 {@link org.jdom2.IllegalDataException}.</p>
     *
     * @param element Element the JDOM2 element to set the attribute on
     * @param attr String the attribute name
     * @param value String the attribute value, or {@code null} to skip
     */
    private void addAttributeifValueNotNull(Element element, String attr, String value) {
        if (value != null) {
            element.setAttribute(attr, value);
        }
    }

    /**
     * Adds default indicator colour mappings to a user-created flowsheet.
     *
     * <p>User-created flowsheets (from {@link FlowSheetUserCreated} records) do not include
     * indicator definitions in their simplified data model, so this method applies a standard
     * set of clinical severity indicators:</p>
     * <ul>
     *   <li><strong>HIGH_1</strong> -- Critical high, displayed as red (#E00000)</li>
     *   <li><strong>HIGH</strong> -- Above normal, displayed as orange</li>
     *   <li><strong>LOW</strong> -- Below normal, displayed as light blue (#9999FF)</li>
     * </ul>
     *
     * @param m MeasurementFlowSheet the user-created flowsheet to add indicators to
     */
    public void addIndicatorsInCustomFlowsheet(MeasurementFlowSheet m) {
        m.AddIndicator("HIGH_1", "#E00000");
        m.AddIndicator("HIGH", "orange");
        m.AddIndicator("LOW", "#9999FF");
    }


}
