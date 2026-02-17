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

import org.apache.commons.collections.OrderedMapIterator;
import org.apache.commons.collections.map.ListOrderedMap;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import io.github.carlos_emr.carlos.drools.DroolsHelper;
import io.github.carlos_emr.carlos.commn.dao.DxDao;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementsDataBean;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.MeasurementDSHelper;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.Recommendation;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.RuleBaseCreator;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.TargetColour;

/**
 * @author jay
 */
public class MeasurementFlowSheet {

    private static Logger log = MiscUtils.getLogger();
    String name = null;
    private String displayName = null;
    private String warningColour = null;
    private String recommendationColour = null;

    Hashtable<String, String> indicatorHash = new Hashtable<String, String>();   //color map for severity
    private String topHTMLFileName = null;
    private boolean universal;
    private boolean isMedical = true;
    private boolean custom = false;


    //ArrayList list = null;  // list of the measurements  ** replace with a asList Items. (maybe for first iteration condence down to string
    //ArrayList dsElements = null; //collection of ds xml elements  ** future replace function with just getted list
    //Hashtable dsHash = null; //hash of the rules  ** might not be needed if it's in the object
    KieBase ruleBase = null;   //ruleBase for the flowsheet   compile from list
    boolean rulesLoaded = false;   //flag to trigger loading the rules
    //Hashtable measurementsInfo = null;            //Colectopm of EctMeasurementType objects  ** GET RID OF THIS
    //Hashtable measurementsFlowSheetInfo = null;   //Collenction of hash tables with details  ** CAN now be from itemList (Hoping)
    //Hashtable dsRulesHash = new Hashtable();   //Each items Rules ** Not sure what to do with yet
    //String demographic = null;   //not used
    String[] dxTriggers = null; // triggers that will cause this flowsheet to show up in a patients profice
    String[] programTriggers = null;


    private ListOrderedMap itemList = new ListOrderedMap();

    public FlowSheetItem addListItem(FlowSheetItem item) {
        log.debug("ITEM " + item.getItemName());
        String dsRules = item.getAllFields().get("ds_rules");  //ds_rules=
        log.debug("DS RULES " + dsRules);
        if (dsRules != null && !dsRules.equals("")) {
            KieBase rb = loadMeasurementRuleBase(dsRules);
            item.setRuleBase(rb);
        } else if (item.getTargetColour() != null && item.getTargetColour().size() > 0) {
            KieBase rb = loadMeasuremntRuleBase(item.getTargetColour());
            item.setRuleBase(rb);
        }
        itemList.put(item.getItemName(), item);
        log.debug("ADDED " + item);
        return item;
    }

    private List<MeasurementTemplateFlowSheetConfig.Node> itemHeirarchy = new ArrayList<MeasurementTemplateFlowSheetConfig.Node>();

    public void setItemHeirarchy(List<MeasurementTemplateFlowSheetConfig.Node> itemHeirarchy) {
        this.itemHeirarchy = itemHeirarchy;
    }

    public List<MeasurementTemplateFlowSheetConfig.Node> getItemHeirarchy() {
        return this.itemHeirarchy;
    }

    public void parseDxTriggers(String s) {
        dxTriggers = s.split(","); //TODO: what do about different coding systems.
    }

    public void parseProgramTriggers(String s) {
        programTriggers = s.split(",");
    }

    public String[] getDxTriggers() {
        return dxTriggers;
    }

    public String[] getProgramTriggers() {
        return programTriggers;
    }

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
     * Creates a new instance of MeasurementFlowSheet
     */
    public MeasurementFlowSheet() {
    }

    public List<Recommendation> getDSElements(String measurement) {
        FlowSheetItem fsi = (FlowSheetItem) itemList.get(measurement);
        return fsi.getRecommendations();
    }

    public void addFlowSheetItem(int i, FlowSheetItem item) {
        itemList.put(i, item.getItemName(), item);
    }

    public FlowSheetItem getFlowSheetItem(String measurement) {
        MiscUtils.getLogger().debug("GETTING " + measurement + " ITEMS IN THE LIST " + itemList.size());
        FlowSheetItem item = (FlowSheetItem) itemList.get(measurement);

        return item;
    }


    public Map<String, String> getMeasurementFlowSheetInfo(String measurement) {
        Map<String, String> allFields = Collections.emptyMap();
        if (itemList == null) {
            //DO something
            itemList = new ListOrderedMap();
        }
        log.debug("GETTING " + measurement + " ITEMS IN THE LIST " + itemList.size());
        FlowSheetItem item = (FlowSheetItem) itemList.get(measurement);
        if (item != null && item.getAllFields() != null) {
            allFields = item.getAllFields();
        }
        return allFields;
    }

    //If measurement is null. Add item to the end of the flowsheet.
    public void addAfter(String measurement, FlowSheetItem item) {
        int placement = itemList.size();
        if (measurement != null) {
            placement = itemList.indexOf(measurement);
        }
        itemList.put(placement, item.getItemName(), item);
    }

    public void setToHidden(String measurement) {
        FlowSheetItem item = (FlowSheetItem) itemList.get(measurement);
        item.setHide(true);
    }


    public void updateMeasurementFlowSheetInfo(String measurement, Hashtable<String, String> h) {
        FlowSheetItem item = new FlowSheetItem(h);
        itemList.put(measurement, item);
    }

    public void updateMeasurementFlowSheetInfo(String measurement, FlowSheetItem item) {
        itemList.put(measurement, item);
    }


    public List<String> getMeasurementList() {
        return itemList.asList();
    }

    public List<String> getVisibleMeasurementList() {
        return getMeasurementList()
            .stream()
            .filter(id -> { 
                FlowSheetItem item = getFlowSheetItem(id);
                return item != null && !item.isHide();
            })
            .collect(Collectors.toList());
    }

    public String getName() {
        return name;
    }

    public void setName(String s) {
        name = s;
    }

    /////
    public String getTopHTMLStream() {
        StringBuilder sb = new StringBuilder();
        if (topHTMLFileName != null) {
            try {
                String measurementDirPath = OscarProperties.getInstance().getProperty("MEASUREMENT_DS_HTML_DIRECTORY");
                InputStream is = null;
                if (measurementDirPath != null) {
                    //if (measurementDirPath.charAt(measurementDirPath.length()) != /)
                    File file = new File(OscarProperties.getInstance().getProperty("MEASUREMENT_DS_HTML_DIRECTORY") + topHTMLFileName);
                    if (file.isFile() || file.canRead()) {
                        log.debug("Loading from file " + file.getName());
                        is = new FileInputStream(file);
                    }
                }

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
                MiscUtils.getLogger().error("Error", e);
            }
        }
        return sb.toString();
    }

    public static String getDSHTMLStream(String dsHTML) {
        StringBuilder sb = new StringBuilder();
        InputStream is = null;
        BufferedReader bReader = null;
        try {
            String measurementDirPath = OscarProperties.getInstance().getProperty("MEASUREMENT_DS_HTML_DIRECTORY");

            if (measurementDirPath != null) {
                File file = new File(OscarProperties.getInstance().getProperty("MEASUREMENT_DS_HTML_DIRECTORY") + dsHTML);
                if (file.isFile() || file.canRead()) {
                    log.debug("Loading from file " + file.getName());
                    is = new FileInputStream(file);
                }
            }

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
            MiscUtils.getLogger().error("Error", e);
        } finally {
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(bReader);
        }

        return sb.toString();
    }


    public void loadRuleBase() {
        log.debug("LOADRULEBASE == " + name);
        ArrayList<String> dsElements = new ArrayList<String>();

        if (itemList != null) {
            OrderedMapIterator iter = itemList.orderedMapIterator();
            while (iter.hasNext()) {
                String key = (String) iter.next();  //returns the key not the value.  Needed to advance the iterator
                FlowSheetItem fsi = (FlowSheetItem) iter.getValue();
                List<Recommendation> rules = fsi.getRecommendations();
                if (rules != null) {
                    log.debug("# OF RULES FOR " + fsi.getItemName() + " " + rules.size() + " key " + key);
                    for (Object obj : rules) {
                        Recommendation rec = (Recommendation) obj;
                        ruleStrings.add(rec.getRuleBaseElement());
                    }
                } else {
                    log.debug("NO RULES FOR " + fsi.getItemName());
                }

            }
        }
        log.debug("LOADING RULES2" + name + " size + " + ruleStrings.size() + " rulebase " + ruleBase);
        if (ruleStrings != null && ruleStrings.size() > 0) {

            log.debug("LOADING RULES21" + ruleStrings.size());
            RuleBaseCreator rcb = new RuleBaseCreator();
            try {

                log.debug("LOADING RULES22");
                ruleBase = rcb.getRuleBase("rulesetName", ruleStrings);
                log.debug("LOADING RULES23");
                rulesLoaded = true;
            } catch (Exception e) {
                log.debug("LOADING EXEPTION");

                MiscUtils.getLogger().error("Error", e);
            }
        } else {
            //empty
        }
    }

    public void loadRuleBase(String string) {
        try {
            boolean fileFound = false;
            String measurementDirPath = OscarProperties.getInstance().getProperty("MEASUREMENT_DS_DIRECTORY");

            if (measurementDirPath != null) {
                //if (measurementDirPath.charAt(measurementDirPath.length()) != /)
                File file = new File(OscarProperties.getInstance().getProperty("MEASUREMENT_DS_DIRECTORY") + string);
                if (file.isFile() || file.canRead()) {
                    log.debug("Loading from file " + file.getName());
                    FileInputStream fis = new FileInputStream(file);
                    ruleBase = DroolsHelper.loadFromInputStream(fis);
                    fileFound = true;
                }
            }

            if (!fileFound) {
                URL url = MeasurementFlowSheet.class.getResource("/oscar/oscarEncounter/oscarMeasurements/flowsheets/" + string);  //TODO: change this so it is configurable;
                log.debug("loading from URL {}", url.getFile());
                ruleBase = DroolsHelper.loadFromUrl(url);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        rulesLoaded = true;
    }

    public void loadRuleBase2(String string) {
        ruleBase = loadMeasurementRuleBase(string);
        if (ruleBase != null) {
            rulesLoaded = true;
        }
    }

    public KieBase loadMeasuremntRuleBase(List<TargetColour> targetColours) {
        KieBase measurementRuleBase = null;
        List<String> dsElements = new ArrayList<String>();
        RuleBaseCreator rcb = new RuleBaseCreator();
        try {
            int count = 0;
            for (TargetColour obj : targetColours) {
                TargetColour rec = obj;
                ruleStrings.add(rec.getRuleBaseElement("DD" + count));
                count++;
            }

            log.debug("loadMeasuremntRuleBase 1");
            measurementRuleBase = rcb.getRuleBase("rulesetName", ruleStrings);
            log.debug("loadMeasuremntRuleBase 2");
            rulesLoaded = true;
        } catch (Exception e) {
            log.debug("loadMeasuremntRuleBase EXEPTION");

            MiscUtils.getLogger().error("Error", e);
        }
        return measurementRuleBase;

    }


    public KieBase loadMeasurementRuleBase(String string) {
        KieBase measurementRuleBase = null;
        try {
            boolean fileFound = false;
            String measurementDirPath = OscarProperties.getInstance().getProperty("MEASUREMENT_DS_DIRECTORY");

            if (measurementDirPath != null) {
                //if (measurementDirPath.charAt(measurementDirPath.length()) != /)
                File file = new File(OscarProperties.getInstance().getProperty("MEASUREMENT_DS_DIRECTORY") + string);
                if (file.isFile() || file.canRead()) {
                    log.debug("Loading from file " + file.getName());
                    FileInputStream fis = new FileInputStream(file);
                    ruleBase = DroolsHelper.loadFromInputStream(fis);
                    fileFound = true;
                }
            }

            if (!fileFound) {
                URL url = MeasurementFlowSheet.class.getResource("/oscar/oscarEncounter/oscarMeasurements/flowsheets/decisionSupport/" + string);  //TODO: change this so it is configurable;
                log.debug("loading from URL {}", url.getFile());
                measurementRuleBase = DroolsHelper.loadFromUrl(url);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return measurementRuleBase;
    }


    public void runRulesForMeasurement(LoggedInInfo loggedInInfo, EctMeasurementsDataBean mdb) {

        String type = mdb.getType();
        log.debug("GETTING RULES FOR TYPE " + type);
        FlowSheetItem fs = (FlowSheetItem) itemList.get(type);
        KieBase rb = fs.getRuleBase();
        log.debug("RULEBASE FOR " + fs);
        //Is there a rule base for this
        if (rb != null) {
            KieSession kieSession = rb.newKieSession();
            try {
                KieSession kieSession = rb.newKieSession();
                try {
                    kieSession.insert(new MeasurementDSHelper(loggedInInfo, mdb));
                    kieSession.fireAllRules();
                } finally {
                    kieSession.dispose();
                }
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
                //throw new Exception("ERROR: Drools ",e);
            } finally {
                kieSession.dispose();
            }
        }
    }

    /////
    public MeasurementInfo getMessages(MeasurementInfo mi) throws Exception {
        if (!rulesLoaded) {
            throw new Exception("No Drools file loaded" + ruleBase);
            //loadRuleBase();
        }

        KieSession kieSession = ruleBase.newKieSession();
        try {
            KieSession kieSession = ruleBase.newKieSession();
            try {
                kieSession.insert(mi);
                kieSession.fireAllRules();
            } finally {
                kieSession.dispose();
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            //throw new Exception("ERROR: Drools ",e);
        } finally {
            kieSession.dispose();
        }
        return mi;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getWarningColour() {
        return warningColour;
    }

    public void setWarningColour(String warningColour) {
        this.warningColour = warningColour;
    }

    public String getRecommendationColour() {
        return recommendationColour;
    }

    public void setRecommendationColour(String recommendationColour) {
        this.recommendationColour = recommendationColour;
    }

    void AddIndicator(String key, String value) {
        if (key != null && value != null) {
            indicatorHash.put(key, value);
        }
    }

    public String getIndicatorColour(String key) {
        String ret = null;
        if (key != null) {
            ret = indicatorHash.get(key);
        }
        return ret;
    }

    public Hashtable<String, String> getIndicatorHashtable() {
        return new Hashtable<String, String>(indicatorHash);
    }

    public ArrayList sortToCurrentOrder(ArrayList nonOrderedList) {
        Collections.sort(nonOrderedList, new FlowSheetSort(getMeasurementList()));
        return nonOrderedList;
    }

    public void setUniversal(boolean universal) {
        this.universal = universal;
    }

    public boolean isUniversal() {
        return universal;
    }

    public boolean isMedical() {
        return isMedical;
    }

    public void setMedical(boolean medical) {
        isMedical = medical;
    }

    public String getTopHTMLFileName() {
        return topHTMLFileName;
    }

    public void setTopHTMLFileName(String topHTMLFileName) {
        this.topHTMLFileName = topHTMLFileName;
    }


    public boolean isCustom() {
        return custom;
    }

    public void setCustom(boolean custom) {
        this.custom = custom;
    }


    class FlowSheetSort implements Comparator {

        List list = null;

        public FlowSheetSort() {
        }

        public FlowSheetSort(List sortedList) {
            log.debug("SortedList " + sortedList);
            list = sortedList;
        }

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
