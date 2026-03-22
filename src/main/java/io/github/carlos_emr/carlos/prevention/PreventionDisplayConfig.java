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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.prevention;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import org.apache.logging.log4j.Logger;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import io.github.carlos_emr.carlos.commn.dao.CVCImmunizationDao;
import io.github.carlos_emr.carlos.commn.dao.CVCMappingDao;
import io.github.carlos_emr.carlos.commn.dao.CVCMedicationDao;
import io.github.carlos_emr.carlos.commn.model.CVCImmunization;
import io.github.carlos_emr.carlos.commn.model.CVCMapping;
import io.github.carlos_emr.carlos.managers.CanadianVaccineCatalogueManager;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.demographic.data.DemographicData;

/**
 * Singleton configuration manager for prevention and immunization display items.
 *
 * <p>Loads prevention type definitions from an XML configuration file (default:
 * {@code PreventionItems.xml}) and merges them with entries from the Canadian Vaccine
 * Catalogue (CVC). Also loads prevention configuration sets from
 * {@code PreventionConfigSets.xml} that define age/sex-based display filtering rules.</p>
 *
 * <p>Prevention items are keyed by name and stored as attribute maps. CVC mappings
 * take precedence when {@code preferCVC} is set on a {@link CVCMapping} record.</p>
 *
 * @since 2001-2002
 * @see PreventionData
 * @see io.github.carlos_emr.carlos.managers.CanadianVaccineCatalogueManager
 */
public class PreventionDisplayConfig {
    private static Logger log = MiscUtils.getLogger();
    private static PreventionDisplayConfig preventionDisplayConfig = new PreventionDisplayConfig();

    private HashMap<String, HashMap<String, String>> prevHash = null;
    private ArrayList<HashMap<String, String>> prevList = null;

    private HashMap<String, Map<String, Object>> configHash = null;
    private ArrayList<Map<String, Object>> configList = null;

    static CVCImmunizationDao cvcImmunizationDao = SpringUtils.getBean(CVCImmunizationDao.class);
    static CVCMedicationDao cvcMedicationDao = null;

    static CanadianVaccineCatalogueManager cvcManager = SpringUtils.getBean(CanadianVaccineCatalogueManager.class);
    static CVCMappingDao cvcMapping = SpringUtils.getBean(CVCMappingDao.class);

    private PreventionDisplayConfig() {
        // use getInstance()
    }

    /**
     * Returns the singleton instance, loading prevention definitions on first access.
     *
     * @return PreventionDisplayConfig the singleton instance
     */
    static public PreventionDisplayConfig getInstance() {
        if (preventionDisplayConfig.prevList == null) {
            preventionDisplayConfig.loadPreventions();
        }
        return preventionDisplayConfig;
    }

    /**
     * Returns all configured prevention types as a list of attribute maps.
     *
     * @return ArrayList&lt;HashMap&lt;String, String&gt;&gt; the prevention type definitions
     */
    public ArrayList<HashMap<String, String>> getPreventions() {
        if (prevList == null) {
            loadPreventions();
        }
        return prevList;
    }

    /**
     * Returns the prevention definition for the given prevention type name.
     *
     * @param s String the prevention type name (e.g., "Flu", "DTaP-IPV")
     * @return HashMap&lt;String, String&gt; the attribute map, or {@code null} if not found
     */
    public HashMap<String, String> getPrevention(String s) {
        if (prevHash == null) {
            loadPreventions();
        }
        log.debug("getting " + s);
        return prevHash.get(s);
    }

    /**
     * Loads prevention type definitions from the XML configuration file and merges
     * with Canadian Vaccine Catalogue (CVC) entries. Sources are checked in order:
     * the {@code PREVENTION_ITEMS} property, then the classpath default.
     */
    public void loadPreventions() {
        prevList = new ArrayList<HashMap<String, String>>();
        prevHash = new HashMap<String, HashMap<String, String>>();
        log.debug("STARTING2");

        InputStream is = null;
        try {
            if (CarlosProperties.getInstance().getProperty("PREVENTION_ITEMS") != null) {
                String filename = CarlosProperties.getInstance().getProperty("PREVENTION_ITEMS");
                if (filename.startsWith("classpath:")) {
                    is = this.getClass().getClassLoader().getResourceAsStream(filename.substring(10));
                } else {
                    is = new FileInputStream(filename);
                }
            } else {
                is = this.getClass().getClassLoader().getResourceAsStream("oscar/oscarPrevention/PreventionItems.xml");
            }

            List<String> addedSnomeds = new ArrayList<String>();

            SAXBuilder parser = new SAXBuilder();
            Document doc = parser.build(is);
            Element root = doc.getRootElement();
            List items = root.getChildren("item");
            for (int i = 0; i < items.size(); i++) {
                Element e = (Element) items.get(i);
                List attr = e.getAttributes();
                HashMap<String, String> h = new HashMap<String, String>();
                for (int j = 0; j < attr.size(); j++) {
                    Attribute att = (Attribute) attr.get(j);
                    h.put(att.getName(), att.getValue());
                }

                //Do we have a mapped CVC Entry?
                CVCMapping mapping = cvcMapping.findByOscarName(h.get("name"));
                if (mapping != null) {
                    if (mapping.getPreferCVC() != null && mapping.getPreferCVC()) {
                        continue;
                    }
                    CVCImmunization cvcImm = cvcImmunizationDao.findBySnomedConceptId(mapping.getCvcSnomedId());
                    if (cvcImm != null) {
                        h.put("snomedConceptCode", mapping.getCvcSnomedId());
                        h.put("cvcName", cvcImm.getPicklistName());
                        h.put("ispa", String.valueOf(cvcImm.isIspa()));
                        addedSnomeds.add(mapping.getCvcSnomedId());
                    }

                }

                if (h.get("name") != null) {
                    prevList.add(h);
                    prevHash.put(h.get("name"), h);
                }
            }

            for (CVCImmunization imm : cvcManager.getGenericImmunizationList()) {
                HashMap<String, String> h = new HashMap<String, String>();
                h.put("resultDesc", "");
                h.put("desc", imm.getDisplayName());
                h.put("name", imm.getPicklistName());
                h.put("cvcName", imm.getPicklistName());
                h.put("healthCanadaType", imm.getPicklistName());
                h.put("layout", "injection");
                h.put("atc", "");
                h.put("showIfMinRecordNum", "1");
                h.put("snomedConceptCode", imm.getSnomedConceptId());
                h.put("ispa", String.valueOf(imm.isIspa()));
                if (!addedSnomeds.contains(imm.getSnomedConceptId()) && imm.getPicklistName() != null) {
                    prevList.add(h);
                    prevHash.put(h.get("name"), h);
                }
            }


        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        } finally {
            try {
                if (is != null) is.close();
            } catch (IOException e) {
                log.error("Unexpected error", e);
            }
        }
    }


    /**
     * Returns all prevention configuration sets (age/sex display filter groups).
     *
     * @return ArrayList&lt;Map&lt;String, Object&gt;&gt; the configuration sets
     */
    public ArrayList<Map<String, Object>> getConfigurationSets() {
        log.debug("returning config sets");
        if (configList == null) {
            loadConfigurationSets();
        }
        log.debug("returning config sets" + configList);
        return configList;
    }


    /**
     * Loads prevention configuration sets from the XML configuration file.
     * Sources are checked in order: the {@code PREVENTION_CONFIG_SETS} property,
     * then the classpath default.
     */
    public void loadConfigurationSets() {
        getPreventions();
        configHash = new HashMap<String, Map<String, Object>>();
        configList = new ArrayList<Map<String, Object>>();

        InputStream is = null;
        try {
            if (CarlosProperties.getInstance().getProperty("PREVENTION_CONFIG_SETS") != null) {
                String filename = CarlosProperties.getInstance().getProperty("PREVENTION_CONFIG_SETS");
                if (filename.startsWith("classpath:")) {
                    is = this.getClass().getClassLoader().getResourceAsStream(filename.substring(10));
                } else {
                    is = new FileInputStream(filename);
                }
            } else {
                is = this.getClass().getClassLoader().getResourceAsStream("oscar/oscarPrevention/PreventionConfigSets.xml");
            }

            SAXBuilder parser = new SAXBuilder();
            Document doc = parser.build(is);
            Element root = doc.getRootElement();
            List items = root.getChildren("set");
            for (int i = 0; i < items.size(); i++) {
                Element e = (Element) items.get(i);
                List attr = e.getAttributes();
                Map<String, Object> h = new HashMap<String, Object>();
                for (int j = 0; j < attr.size(); j++) {
                    Attribute att = (Attribute) attr.get(j);
                    if (att.getName().equals("prevList")) {
                        h.put(att.getName(), att.getValue().split(","));
                    } else {
                        h.put(att.getName(), att.getValue());
                    }

                }
                configList.add(h);
                configHash.put((String) h.get("title"), h);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        } finally {
            try {
                if (is != null) is.close();
            } catch (IOException e) {
                log.error("Unexpected error", e);
            }
        }

    }


    /**
     * Returns a CSS display style attribute based on whether the patient matches
     * the age and sex criteria defined in the configuration set.
     *
     * @param loggedInInfo LoggedInInfo the logged-in session context
     * @param setHash Map&lt;String, Object&gt; the configuration set with minAge, maxAge, sex keys
     * @param Demographic_no String the patient's demographic number
     * @return String empty string if visible, or {@code style="display:none;"} if hidden
     */
    public String getDisplay(LoggedInInfo loggedInInfo, Map<String, Object> setHash, String Demographic_no) {
        String display = "style=\"display:none;\"";
        DemographicData dData = new DemographicData();
        log.debug("demoage " + Demographic_no);
        Demographic demograph = dData.getDemographic(loggedInInfo, Demographic_no);
        try {
            String minAgeStr = (String) setHash.get("minAge");
            String maxAgeStr = (String) setHash.get("maxAge");
            String sex = (String) setHash.get("sex");
            int demoAge = demograph.getAgeInYears();
            String demoSex = demograph.getSex();
            boolean inAgeGroup = true;
            log.debug("min age " + minAgeStr + " max age " + maxAgeStr + " sex " + sex + " demoAge " + demoAge
                    + " demoSex " + demoSex);
            if (minAgeStr != null && maxAgeStr != null) { // between ages
                log.debug("HERE1");
                int minAge = Integer.parseInt(minAgeStr);
                int maxAge = Integer.parseInt(maxAgeStr);
                if (minAge <= demoAge && maxAge >= demoAge) {
                    display = "";
                } else {
                    inAgeGroup = false;
                }
            } else if (minAgeStr != null) { // older than
                log.debug("HERE2");
                int minAge = Integer.parseInt(minAgeStr);
                if (minAge <= demoAge) {
                    display = "";
                } else {
                    inAgeGroup = false;
                }
            } else if (maxAgeStr != null) { // younger than
                log.debug("HERE3");
                int maxAge = Integer.parseInt(maxAgeStr);
                if (maxAge >= demoAge) {
                    display = "";
                } else {
                    inAgeGroup = false;
                }
            }// else? neither defined should the default be to display it or
            // not?

            if (sex != null && inAgeGroup) {
                log.debug("HERE4");
                if (sex.equals(demoSex)) {
                    display = "";
                } else {
                    display = "style=\"display:none;\"";
                }
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return display;
    }

    /**
     * Determines whether a prevention item should be displayed for the given patient,
     * considering age, sex, minimum record count, and hidden-item configuration.
     *
     * @param loggedInInfo LoggedInInfo the logged-in session context
     * @param setHash Map&lt;String, String&gt; the prevention item attributes
     * @param Demographic_no String the patient's demographic number
     * @param numberOfPrevs int the number of existing prevention records for this type
     * @return boolean {@code true} if the prevention item should be displayed
     */
    public boolean display(LoggedInInfo loggedInInfo, Map<String, String> setHash, String Demographic_no, int numberOfPrevs) {
        boolean display = false;
        PreventionManager preventionManager = SpringUtils.getBean(PreventionManager.class);
        DemographicData dData = new DemographicData();
        log.debug("demoage " + Demographic_no);
        Demographic demograph = dData.getDemographic(loggedInInfo, Demographic_no);
        try {
            if (preventionManager.hideItem(setHash.get("name")) && numberOfPrevs == 0) {
                //move to hidden list
                display = false;
            } else {
                String minAgeStr = setHash.get("minAge");
                String maxAgeStr = setHash.get("maxAge");
                String sex = setHash.get("sex");
                String minNumPrevs = setHash.get("showIfMinRecordNum");
                int demoAge = demograph.getAgeInYears();
                String demoSex = demograph.getSex();
                boolean inAgeGroup = true;
                //log.debug("min age " + minAgeStr + " max age " + maxAgeStr + " sex " + sex + " demoAge " + demoAge
                //        + " demoSex " + demoSex);

                if (minNumPrevs != null) {
                    int minNum = Integer.parseInt(minNumPrevs);
                    if (numberOfPrevs >= minNum) {
                        display = true;
                    }
                }

                if (!display) {

                    if (minAgeStr != null && maxAgeStr != null) { // between ages
                        //log.debug("HERE1");
                        int minAge = Integer.parseInt(minAgeStr);
                        int maxAge = Integer.parseInt(maxAgeStr);
                        if (minAge <= demoAge && maxAge >= demoAge) {
                            display = true;
                        } else {
                            inAgeGroup = false;
                        }
                    } else if (minAgeStr != null) { // older than
                        //log.debug("HERE2");
                        int minAge = Integer.parseInt(minAgeStr);
                        if (minAge <= demoAge) {
                            display = true;
                        } else {
                            inAgeGroup = false;
                        }
                    } else if (maxAgeStr != null) { // younger than
                        //log.debug("HERE3");
                        int maxAge = Integer.parseInt(maxAgeStr);
                        if (maxAge >= demoAge) {
                            display = true;
                        } else {
                            inAgeGroup = false;
                        }
                    }// else? neither defined should the default be to display it or
                    // not?

                    if (sex != null && inAgeGroup) {
                        //log.debug("HERE4");
                        if (sex.equals(demoSex)) {
                            display = true;
                        } else {
                            display = false;
                        }
                    }
                }
            }//end check if prevention has been hidden
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return display;
    }
}
