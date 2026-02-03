/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 */

package io.github.carlos_emr.carlos.managers;

import java.io.Serializable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionExtDao;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.interfaces.Immunization.ImmunizationProperty;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.commn.model.PreventionExt;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.prevention.PreventionDS;
import io.github.carlos_emr.carlos.prevention.PreventionData;
import io.github.carlos_emr.carlos.prevention.PreventionDisplayConfig;
import io.github.carlos_emr.carlos.util.StringUtils;

@Service
public class PreventionManagerImpl implements Serializable, PreventionManager {
    @Autowired
    private PreventionDao preventionDao;
    @Autowired
    private PreventionExtDao preventionExtDao;
    @Autowired
    private PropertyDao propertyDao;
    @Autowired
    private PreventionDS preventionDS;
    @Autowired
    private SecurityInfoManager securityInfoManager;
    private static final String HIDE_PREVENTION_ITEM = "hide_prevention_item";

    private ArrayList<String> preventionTypeList = new ArrayList<String>();

    private Set<String> listMatches;

    @Override
    public List<Prevention> getUpdatedAfterDate(LoggedInInfo loggedInInfo, Date updatedAfterThisDateExclusive,
                                                int itemsToReturn) {
        List<Prevention> results = preventionDao.findByUpdateDate(updatedAfterThisDateExclusive, itemsToReturn);

        LogAction.addLogSynchronous(loggedInInfo, "PreventionManager.getUpdatedAfterDate",
                "updatedAfterThisDateExclusive=" + updatedAfterThisDateExclusive);

        return (results);
    }

    @Override
    public List<Prevention> getByDemographicIdUpdatedAfterDate(LoggedInInfo loggedInInfo, Integer demographicId,
                                                               Date updatedAfterThisDateExclusive) {
        List<Prevention> results = preventionDao.findByDemographicIdAfterDatetimeExclusive(demographicId,
                updatedAfterThisDateExclusive);
        LogAction.addLogSynchronous(loggedInInfo, "PreventionManager.getByDemographicIdUpdatedAfterDate",
                "demographicId=" + demographicId + " updatedAfterThisDateExclusive=" + updatedAfterThisDateExclusive);

        return (results);
    }

    @Override
    public Prevention getPrevention(LoggedInInfo loggedInInfo, Integer id) {
        Prevention result = preventionDao.find(id);

        // --- log action ---
        if (result != null) {
            LogAction.addLogSynchronous(loggedInInfo, "PreventionManager.getPrevention", "id=" + id);
        }

        return (result);
    }

    @Override
    public List<PreventionExt> getPreventionExtByPrevention(LoggedInInfo loggedInInfo, Integer preventionId) {
        List<PreventionExt> results = preventionExtDao.findByPreventionId(preventionId);

        LogAction.addLogSynchronous(loggedInInfo, "PreventionManager.getPreventionExtByPrevention",
                "preventionId=" + preventionId);

        return (results);
    }

    @Override
    public ArrayList<String> getPreventionTypeList() {
        if (preventionTypeList.isEmpty()) {
            PreventionDisplayConfig pdc = PreventionDisplayConfig.getInstance();
            for (HashMap<String, String> prevTypeHash : pdc.getPreventions()) {
                if (prevTypeHash != null && StringUtils.filled(prevTypeHash.get("name"))) {
                    preventionTypeList.add(prevTypeHash.get("name").trim());
                }
            }
        }
        return preventionTypeList;
    }

    @Override
    public ArrayList<HashMap<String, String>> getPreventionTypeDescList() {
        PreventionDisplayConfig pdc = PreventionDisplayConfig.getInstance();
        ArrayList<HashMap<String, String>> preventionTypeDescList = pdc.getPreventions();

        return preventionTypeDescList;
    }

    @Override
    public boolean isHidePrevItemExist() {
        List<Property> props = propertyDao.findByName(HIDE_PREVENTION_ITEM);
        if (props.size() > 0) {
            return true;
        }
        return false;
    }

    @Override
    public boolean hideItem(String item) {
        String itemsToRemove = null;
        Property p = propertyDao.checkByName(HIDE_PREVENTION_ITEM);

        if (p != null && p.getValue() != null) {
            itemsToRemove = p.getValue();
            List<String> items = Arrays.asList(itemsToRemove.split("\\s*,\\s*"));
            for (String i : items) {
                if (i.equals(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String getCustomPreventionItems() {
        String itemsToRemove = "";
        PropertyDao propertyDao = (PropertyDao) SpringUtils.getBean(PropertyDao.class);
        Property p = propertyDao.checkByName(HIDE_PREVENTION_ITEM);
        if (p != null && p.getValue() != null) {
            itemsToRemove = p.getValue();
        }
        return itemsToRemove;
    }

    @Override
    public void addCustomPreventionItems(String items) {
        boolean propertyExists = isHidePrevItemExist();
        if (propertyExists) {
            Property p = propertyDao.checkByName(HIDE_PREVENTION_ITEM);
            p.setValue(items);
            propertyDao.merge(p);
        } else {
            Property x = new Property();
            x.setName("hide_prevention_item");
            x.setValue(items);
            propertyDao.persist(x);
        }
    }

    @Override
    public void addPreventionWithExts(Prevention prevention, HashMap<String, String> exts) {
        if (prevention == null)
            return;

        preventionDao.persist(prevention);
        if (exts != null) {
            for (String keyval : exts.keySet()) {
                if (StringUtils.filled(keyval) && StringUtils.filled(exts.get(keyval))) {
                    PreventionExt preventionExt = new PreventionExt();
                    preventionExt.setPreventionId(prevention.getId());
                    preventionExt.setKeyval(keyval);
                    preventionExt.setVal(exts.get(keyval));
                    preventionExtDao.persist(preventionExt);
                }
            }
        }
    }

    /**
     * programId is ignored for now as oscar doesn't support it yet.
     */
    @Override
    public List<Prevention> getPreventionsByProgramProviderDemographicDate(LoggedInInfo loggedInInfo, Integer programId,
                                                                           String providerNo, Integer demographicId, Calendar updatedAfterThisDateExclusive, int itemsToReturn) {
        List<Prevention> results = preventionDao.findByProviderDemographicLastUpdateDate(providerNo, demographicId,
                updatedAfterThisDateExclusive.getTime(), itemsToReturn);

        LogAction.addLogSynchronous(loggedInInfo, "PreventionManager.getUpdatedAfterDate",
                "programId=" + programId + ", providerNo=" + providerNo + ", demographicId=" + demographicId
                        + ", updatedAfterThisDateExclusive=" + updatedAfterThisDateExclusive.getTime());

        return (results);
    }

    @Override
    public List<Prevention> getPreventionsByDemographicNo(LoggedInInfo loggedInInfo, Integer demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_prevention", SecurityInfoManager.READ, demographicNo)) {
            throw new RuntimeException("missing required sec object (_prevention)");
        }

        List<Prevention> results = preventionDao.findUniqueByDemographicId(demographicNo);

        LogAction.addLogSynchronous(loggedInInfo, "PreventionManager.getPreventionsByDemographicNo",
                "demographicNo=" + demographicNo);

        return (results);
    }

    @Override
    public String getWarnings(LoggedInInfo loggedInInfo, String demo) {
        io.github.carlos_emr.carlos.prevention.Prevention prev = PreventionData.getLocalandRemotePreventions(loggedInInfo,
                Integer.parseInt(demo));
        String message = "";

        try {
            /*
             * Get defined messages for each prevention from Drools
             * This method populates the given Prevention class
             */
            preventionDS.getMessages(prev);

            /*
             * get the populated prevention warnings from the Prevention class
             */
            Map<String, Object> warningMsgs = prev.getWarningMsgs();
            Set<String> keySet = warningMsgs.keySet();

            /*
             * check if display of each warning message has been disabled
             */
            String value;
            for (String key : keySet) {
                value = (String) warningMsgs.get(key);
                if (!isPrevDisabled(key)) {
                    message += "[" + key + "=" + value + "]" + "\n";
                }
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return message;

    }

    @Override
    public String checkNames(String k) {
        String rebuilt = "";
        Pattern pattern = Pattern.compile("(\\[)(.*?)(\\])");
        Matcher matcher = pattern.matcher(k);

        while (matcher.find()) {
            String[] key = matcher.group(2).split("=");
            boolean prevCheck = isPrevDisabled(key[0]);

            if (prevCheck == false) {
                rebuilt = rebuilt + "[" + key[1] + "]";
            }
        }

        return rebuilt;
    }

    /**
     * refresh the prevention stop sign list with the newest list from the
     * database and then check if the module is enabled.
     *
     * @return
     */
    @Override
    public boolean isDisabled() {
        this.listMatches = null;
        Set<String> preventionStopSigns = getPreventionStopSigns();
        // anyone up for a logic puzzle? I tried to keep existing code. But yikes.
        if (preventionStopSigns.contains("master")) {
            return true;
        }
        if (preventionStopSigns.contains("false")) {
            return false;
        }
        if (preventionStopSigns.size() == 0) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isCreated() {
        return getPreventionStopSigns().size() > 0;
    }

    /**
     * A value set in this set indicates the specified prevention is disabled.
     * These values are cached until GC or until reset by the prevention settings.
     * Use getDisabledPreventions() directly to get the values currently set in the
     * database.
     * <p>
     * Tried my best to refactor this using the current - odd - structure.
     */
    @Override
    public Set<String> getPreventionStopSigns() {
        if (this.listMatches == null) {
            listMatches = new HashSet<>();
            String preventionStopSigns = getDisabledPreventionList();

            /*
             * short circuit if "false" indicates that the
             * entire module is enabled with no restrictions.
             */
            if ("false".equals(preventionStopSigns)) {
                listMatches.add(preventionStopSigns);
                return listMatches;
            }

            /*
             * short circuit if "master" indicates that the
             * entire module is disabled.
             */
            if ("master".equals(preventionStopSigns)) {
                listMatches.add(preventionStopSigns);
                return listMatches;
            }

            /*
             * Values are stored in the database as a delim string.
             */
            Pattern pattern = Pattern.compile("(\\[)(.*?)(\\])");
            Matcher matcher = null;

            if (preventionStopSigns != null) {
                matcher = pattern.matcher(preventionStopSigns);
            }
            /*
             * This list should get loaded once.
             */
            while (matcher != null && matcher.find()) {
                listMatches.add(matcher.group(2));
            }
        }
        return listMatches;
    }

    /**
     * Check if a specific prevention warning stop sign
     * is disabled.
     *
     * @param name prevention name
     * @return boolean
     */
    @Override
    public boolean isPrevDisabled(String name) {
        return getPreventionStopSigns().contains(name);
    }

    /**
     * Call from the database
     *
     * @return String list of prevention values separated by []
     */
    private String getDisabledPreventionList() {
        List<Property> preventionStopSigns = propertyDao.findByName("hide_prevention_stop_signs");
        Iterator<Property> propertyIterator = preventionStopSigns.iterator();
        String disabledList = null;
        while (propertyIterator.hasNext()) {
            Property item = propertyIterator.next();
            disabledList = item.getValue();
        }
        return disabledList;
    }

    /**
     * Call list from database then return as a List Collection of
     * String values
     * This method signature pre-existed therefore could not
     * change the name to something more meaningful
     *
     * @return List of string values.
     */
    @Override
    public List<String> getDisabledPreventions() {
        String disabledPreventionList = getDisabledPreventionList();
        // remove '[' since it always precedes a name
        disabledPreventionList = disabledPreventionList.replace("[", "");
        // split on ']' since it always follows a name
        return Arrays.asList(disabledPreventionList.split("]"));
    }

    @Override
    public boolean setDisabledPreventions(List<String> newDisabledPreventions) {

        if (newDisabledPreventions == null) {
            return false;
        }

        propertyDao.removeByName("hide_prevention_stop_signs");

        if (newDisabledPreventions.get(0).equals("master") || newDisabledPreventions.get(0).equals("false")) {
            Property newProp = new Property();
            newProp.setName("hide_prevention_stop_signs");
            newProp.setValue(newDisabledPreventions.get(0));
            propertyDao.persist(newProp);
            return true;
        }

        String newDisabled = "";
        for (String preventionName : newDisabledPreventions) {

            if ((newDisabled + "[" + preventionName + "]").length() > 255) { // a value in the property table holds a
                // max of 255 characters

                Property newProp = new Property();
                newProp.setName("hide_prevention_stop_signs");
                newProp.setValue(newDisabled);
                propertyDao.persist(newProp);

                newDisabled = "[" + preventionName + "]";
            } else {
                newDisabled += "[" + preventionName + "]";
            }
        }
        if (!newDisabled.isEmpty()) {
            Property newProp = new Property();
            newProp.setName("hide_prevention_stop_signs");
            newProp.setValue(newDisabled);
            propertyDao.persist(newProp);
        }
        return true;
    }

    @Override
    public List<Prevention> getImmunizationsByDemographic(LoggedInInfo loggedInInfo, Integer demographicNo) {

        List<Prevention> results = getPreventionsByDemographicNo(loggedInInfo, demographicNo);

        // the ImmunizationInterface should really be located in Oscar/Common.
        List<Prevention> immunizations = new ArrayList<Prevention>();

        if (results == null) {
            results = Collections.emptyList();
        }

        for (Prevention prevention : results) {

            // this sets the PreventionExt values for use in the Immunization interface.
            prevention.setPreventionExtendedProperties();

            if (prevention.isImmunization() || prevention.getImmunizationProperty(ImmunizationProperty.dose) != null) {
                // this splits out a new Immunizations list of preventions.
                immunizations.add(prevention);
            }
        }

        LogAction.addLogSynchronous(loggedInInfo, "PreventionManager.getImmunizationsByDemographic",
                "demographicNo=" + demographicNo);

        return immunizations;
    }

}
