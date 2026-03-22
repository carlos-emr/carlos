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


package io.github.carlos_emr.carlos.provider.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.time.DateUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.QueueCache;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.carlos_emr.carlos.prevention.Prevention;
import io.github.carlos_emr.carlos.prevention.PreventionDS;
import io.github.carlos_emr.carlos.prevention.PreventionData;


/**
 * Manages prevention (immunization) warning messages for patient demographics.
 *
 * <p>Provides cached retrieval of prevention warning strings for a given demographic number,
 * filtering out disabled preventions based on the {@code hide_prevention_stop_signs} system property.
 * Uses a {@link QueueCache} with a one-hour TTL to reduce repeated database and rule-engine lookups.</p>
 *
 * @deprecated Use {@link io.github.carlos_emr.carlos.managers.PreventionManager} instead.
 * @since 2026-03-17
 */
@Deprecated
// @Component - Removed to avoid Spring bean conflict with io.github.carlos_emr.carlos.managers.PreventionManager
public class PreventionManager {
    private static Logger logger = MiscUtils.getLogger();
    private static final QueueCache<String, String> dataCache = new QueueCache<String, String>(4, 500, DateUtils.MILLIS_PER_HOUR, null);

    @Autowired
    private PreventionDS pf = null;


    /**
     * Retrieves prevention warning messages for a patient demographic.
     *
     * <p>Results are cached for one hour per demographic number. Warning messages from
     * disabled preventions (as configured via {@code hide_prevention_stop_signs}) are excluded.</p>
     *
     * @param loggedInInfo {@link LoggedInInfo} the current session context for the logged-in provider
     * @param demo String the demographic number identifying the patient
     * @return String concatenated warning messages in {@code [key=value]} format, or empty string on error
     */
    public String getWarnings(LoggedInInfo loggedInInfo, String demo) {
        String ret = dataCache.get(demo);

        if (ret == null) {
            try {

                Prevention prev = PreventionData.getPrevention(loggedInInfo, Integer.parseInt(demo));
                pf.getMessages(prev);

                @SuppressWarnings("unchecked")
                Map<String, Object> m = prev.getWarningMsgs();

                @SuppressWarnings("rawtypes")
                Set set = m.entrySet();

                @SuppressWarnings("rawtypes")
                Iterator i = set.iterator();
                // Display elements
                String k = "";
                if (ret == null || ret.equals("null")) {
                    ret = "";
                }

                while (i.hasNext()) {
                    @SuppressWarnings("rawtypes")
                    Map.Entry me = (Map.Entry) i.next();

                    k = "[" + me.getKey() + "=" + me.getValue() + "]";
                    boolean prevCheck = PreventionManager.isPrevDisabled(me.getKey().toString());
                    if (prevCheck == false) {
                        ret = ret + k;
                    }

                }

                dataCache.put(demo, ret);

            } catch (Exception e) {
                ret = "";
                logger.error("Error", e);
            }

        }

        return ret;

    }

    /**
     * Removes the cached prevention warnings for the specified demographic number.
     *
     * @param demo String the demographic number whose cached warnings should be invalidated
     */
    public void removePrevention(String demo) {
        dataCache.remove(demo);
    }


    /**
     * Rebuilds a warning string, excluding any preventions that are disabled.
     *
     * <p>Parses bracketed {@code [key=value]} entries from the input string and reconstructs
     * the output with only the values of non-disabled preventions.</p>
     *
     * @param k String the raw warning string containing {@code [key=value]} entries
     * @return String the filtered warning string with only enabled prevention values
     */
    public static String checkNames(String k) {
        String rebuilt = "";
        Pattern pattern = Pattern.compile("(\\[)(.*?)(\\])");
        Matcher matcher = pattern.matcher(k);

        while (matcher.find()) {
            String[] key = matcher.group(2).split("=");
            boolean prevCheck = PreventionManager.isPrevDisabled(key[0]);

            if (prevCheck == false) {
                rebuilt = rebuilt + "[" + key[1] + "]";
            }
        }

        return rebuilt;
    }


    /**
     * Checks whether all prevention stop-sign warnings are globally disabled.
     *
     * <p>Returns {@code true} if the {@code hide_prevention_stop_signs} property value
     * is set to {@code "master"}, which disables all prevention warnings system-wide.</p>
     *
     * @return boolean {@code true} if all prevention warnings are disabled, {@code false} otherwise
     */
    public static boolean isDisabled() {
        String getStatus = "";
        PropertyDao propDao = (PropertyDao) SpringUtils.getBean(PropertyDao.class);
        List<Property> pList = propDao.findByName("hide_prevention_stop_signs");

        Iterator<Property> i = pList.iterator();

        while (i.hasNext()) {
            Property item = i.next();
            getStatus = item.getValue();

        }

        //disable all preventions warnings if result is master
        if (getStatus.equals("master")) {
            return true;
        } else {
            return false;
        }

    }


    /**
     * Checks whether the {@code hide_prevention_stop_signs} property exists in the database.
     *
     * @return boolean {@code true} if the property record exists, {@code false} otherwise
     */
    public static boolean isCreated() {
        String getStatus = "";
        PropertyDao propDao = (PropertyDao) SpringUtils.getBean(PropertyDao.class);
        List<Property> pList = propDao.findByName("hide_prevention_stop_signs");

        Iterator<Property> i = pList.iterator();

        while (i.hasNext()) {
            Property item = i.next();
            getStatus = item.getName();

        }

        if (getStatus.equals("hide_prevention_stop_signs")) {
            return true;
        } else {
            return false;
        }

    }

    /**
     * Checks whether a specific prevention type is disabled via the
     * {@code hide_prevention_stop_signs} property.
     *
     * <p>The property value contains bracketed prevention names (e.g., {@code [Flu][Td]}).
     * This method parses those entries and checks if the given name matches any of them.</p>
     *
     * @param name String the prevention type name to check
     * @return boolean {@code true} if the prevention is listed as disabled, {@code false} otherwise
     */
    public static boolean isPrevDisabled(String name) {
        String getStatus = "";
        PropertyDao propDao = (PropertyDao) SpringUtils.getBean(PropertyDao.class);
        List<Property> pList = propDao.findByName("hide_prevention_stop_signs");

        Iterator<Property> i = pList.iterator();

        while (i.hasNext()) {
            Property item = i.next();
            getStatus = item.getValue();

        }

        Pattern pattern = Pattern.compile("(\\[)(.*?)(\\])");
        Matcher matcher = pattern.matcher(getStatus);
        List<String> listMatches = new ArrayList<String>();

        while (matcher.find()) {

            listMatches.add(matcher.group(2));

        }

        int x = 0;
        for (String s : listMatches) {

            if (name.equals(s)) {
                x++;
            }

        }

        if (x > 0) {
            return true;
        } else {
            return false;
        }


    }


}
