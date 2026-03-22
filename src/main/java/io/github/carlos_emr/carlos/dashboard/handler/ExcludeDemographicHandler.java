/**
 * Copyright (c) 2013-2015. Department of Computer Science, University of Victoria. All Rights Reserved.
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
 * Department of Computer Science
 * LeadLab
 * University of Victoria
 * Victoria, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.dashboard.handler;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.dashboard.display.beans.DrilldownBean;
import io.github.carlos_emr.carlos.managers.DashboardManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Manages patient exclusions from dashboard indicators. Exclusions are stored as
 * {@link DemographicExt} records with the key "excludeIndicator" and are valid
 * for one year from their creation date.
 *
 * <p>Supports excluding and un-excluding patients by demographic number (individually,
 * as lists, or as JSON arrays) for a specific indicator identified by a composite
 * name|subCategory|category string.</p>
 *
 * @since 2026-03-17
 */
public class ExcludeDemographicHandler {

    private static Logger logger = MiscUtils.getLogger();
    private ObjectMapper objectMapper = new ObjectMapper();

    private static DemographicExtDao demographicExtDao = SpringUtils.getBean(DemographicExtDao.class);
    private DashboardManager dashboardManager = SpringUtils.getBean(DashboardManager.class);

    List<Integer> demoIds;
    List<DemographicExt> demoExts;
    private LoggedInInfo loggedInInfo;
    private String excludeIndicator = "excludeIndicator";
    Date now = new java.util.Date();

    /**
     * Returns the demographic IDs currently excluded from the given indicator
     * for the current provider. Only returns exclusions that are still current
     * (less than one year old).
     *
     * @param indicatorName String the indicator identifier (name|subCategory|category)
     * @return List of Integer the excluded demographic IDs, or {@code null} if indicator name is blank
     */
    public List<Integer> getDemoIds(String indicatorName) {
        if (indicatorName == null || indicatorName.isEmpty()) return null;
        demoIds = new ArrayList<Integer>();
        List<DemographicExt> allProviderDemoExts = demographicExtDao.getDemographicExtByKeyAndValue(excludeIndicator, indicatorName);
        logger.debug("getDemosIds: " + allProviderDemoExts + " matching extensions for template " + indicatorName);
        String providerNo = getProviderNo();
        for (DemographicExt e : allProviderDemoExts) {
            if (e.getProviderNo().equals(providerNo) && isCurrentExclusion(e)) {
                demoIds.add(e.getDemographicNo());
                logger.debug("template: " + indicatorName + " getDemoIds returning: " + e.getDemographicNo());
            }
        }
        return demoIds;
    }

    /**
     * Returns the {@link DemographicExt} records for patients currently excluded from the
     * given indicator for the current provider. Only returns current exclusions.
     *
     * @param indicatorName String the indicator identifier (name|subCategory|category)
     * @return List of DemographicExt the exclusion records, or {@code null} if indicator name is blank
     */
    public List<DemographicExt> getDemoExts(String indicatorName) {
        if (indicatorName == null || indicatorName.isEmpty()) return null;
        demoExts = new ArrayList<DemographicExt>();
        List<DemographicExt> allProviderDemoExts = demographicExtDao.getDemographicExtByKeyAndValue(excludeIndicator, indicatorName);
        logger.debug("getDemosExts: " + allProviderDemoExts + " matching extensions for template " + indicatorName);
        String providerNo = getProviderNo();
        for (DemographicExt e : allProviderDemoExts) {
            if (e.getProviderNo().equals(providerNo) && isCurrentExclusion(e)) {
                demoExts.add(e);
                logger.debug("template: " + indicatorName + " getDemoExts returning: " + e.getDemographicNo());
            }
        }
        return demoExts;
    }

    /**
     * Excludes a single patient from a dashboard indicator.
     *
     * @param demographicNo Integer the demographic number to exclude
     * @param indicatorName String the indicator identifier
     */
    public void excludeDemoId(Integer demographicNo, String indicatorName) {
        if (demographicNo == null || indicatorName == null || indicatorName.isEmpty()) return;
        String providerNo = getProviderNo();
        // It is possible that there is already a exclusion present in demographicExt but the
        // exclusion is no longer current.  The old one could be removed, its creation date
        // could be updated to the current date, or we can just ignore it.  For the moment we
        // will ignore non-current entries.  There probably wouldn't be many and they serve as
        // a record that the patient was excluded from the indicator in the past.
        demographicExtDao.addKey(providerNo, demographicNo, excludeIndicator, indicatorName);
        logger.info("demo: " + demographicNo + " excluded from indicatorTemplate " + indicatorName);
    }

    /**
     * Excludes multiple patients from a dashboard indicator.
     *
     * @param demographicNos List of Integer the demographic numbers to exclude
     * @param indicatorName String the indicator identifier
     */
    public void excludeDemoIds(List<Integer> demographicNos, String indicatorName) {
        if (demographicNos == null || demographicNos.isEmpty() || indicatorName == null || indicatorName.isEmpty())
            return;
        String providerNo = getProviderNo();
        for (Integer demographicNo : demographicNos) {
            demographicExtDao.addKey(providerNo, demographicNo, excludeIndicator, indicatorName);
            logger.info("demo: " + demographicNo + " excluded from indicatorTemplate " + indicatorName);
        }
    }

    /**
     * Removes exclusions for the specified patients from a dashboard indicator.
     *
     * @param demographicNos List of Integer the demographic numbers to un-exclude
     * @param indicatorName String the indicator identifier
     */
    public void unExcludeDemoIds(List<Integer> demographicNos, String indicatorName) {
        if (demographicNos == null || demographicNos.isEmpty() || indicatorName == null || indicatorName.isEmpty())
            return;
        String providerNo = getProviderNo();
        List<DemographicExt> allProviderDemoExts = demographicExtDao.getDemographicExtByKeyAndValue(excludeIndicator, indicatorName);
        logger.debug("unExcludeDemoIds: " + allProviderDemoExts + " matching extensions for template " + indicatorName);
        for (DemographicExt e : allProviderDemoExts) {
            // remove exclusion if provider_no matches or is null and the demongraphic_no matches
            if (e.getProviderNo().equals(providerNo) && demographicNos.contains(e.getDemographicNo())) {
                demographicExtDao.removeDemographicExt(e.getId());
                logger.info("demo: " + e.getDemographicNo() + " unexcluded from indicatorTemplate " + indicatorName);
            }
        }
    }

    public void excludeDemoIds(String jsonString, String indicatorName) {
        String providerNo = getProviderNo();
        if (jsonString == null || jsonString.isEmpty() || indicatorName == null || indicatorName.isEmpty()) return;
        if (!jsonString.startsWith("[")) {
            jsonString = "[" + jsonString;
        }
        if (!jsonString.endsWith("]")) {
            jsonString = jsonString + "]";
        }
        try {
            ArrayNode jsonArray = (ArrayNode) objectMapper.readTree(jsonString);
            Integer arraySize = jsonArray.size();
            for (int i = 0; i < arraySize; i++) {
                demographicExtDao.addKey(providerNo, jsonArray.get(i).asInt(), excludeIndicator, indicatorName);
                logger.info("demo: " + jsonArray.get(i).asInt() + " excluded from indicatorTemplate " + indicatorName);
            }
        } catch (Exception e) {
            logger.error("Failed to parse JSON string: " + jsonString, e);
        }
    }

    public void unExcludeDemoIds(String jsonString, String indicatorName) {
        if (jsonString == null || jsonString.isEmpty() || indicatorName == null || indicatorName.isEmpty()) return;
        if (!jsonString.startsWith("[")) {
            jsonString = "[" + jsonString;
        }
        if (!jsonString.endsWith("]")) {
            jsonString = jsonString + "]";
        }
        try {
            ArrayNode jsonArray = (ArrayNode) objectMapper.readTree(jsonString);
            String providerNo = getProviderNo();

            Set<Integer> demoIds = new HashSet<>();
            for (JsonNode node : jsonArray) {
                demoIds.add(node.asInt());
            }

            List<DemographicExt> allProviderDemoExts = demographicExtDao.getDemographicExtByKeyAndValue(excludeIndicator, indicatorName);
            logger.debug("unExcludeDemoIds (json): " + allProviderDemoExts + " matching extensions for template " + indicatorName);
            for (DemographicExt e : allProviderDemoExts) {
                // remove exclusion if provider_no matches or is null and the demongraphic_no matches
                if (e.getProviderNo().equals(providerNo) && demoIds.contains(e.getDemographicNo())) {
                    demographicExtDao.removeDemographicExt(e.getId());
                    logger.info("demo: " + e.getDemographicNo() + " unexcluded from indicatorTemplate " + indicatorName);
                }
            }
        } catch (Exception ex) {
            logger.error("Failed to parse JSON string: " + jsonString, ex);
        }
    }

    public LoggedInInfo getLoggedinInfo() {
        return loggedInInfo;
    }

    public void setLoggedinInfo(LoggedInInfo loggedInInfo) {
        this.loggedInInfo = loggedInInfo;
    }

    private String getProviderNo() {
        String providerNo = null;
        if (loggedInInfo != null) {
            providerNo = getLoggedinInfo().getLoggedInProviderNo();
            String mrp = dashboardManager.getRequestedProviderNo(loggedInInfo);
            if (mrp != null && !mrp.isEmpty()) {
                providerNo = mrp;
            }
        }
        return providerNo;
    }

    // An exclusion is only valid for a finite interval.  The interval may need to be modified
    // based on user feedback.
    private Boolean isCurrentExclusion(DemographicExt de) {
        Boolean result = true;

        int MILLIS_IN_SECOND = 1000;
        int SECONDS_IN_MINUTE = 60;
        int MINUTES_IN_HOUR = 60;
        int HOURS_IN_DAY = 24;
        int DAYS_IN_YEAR = 365;
        long MILLISECONDS_IN_YEAR =
                (long) MILLIS_IN_SECOND * SECONDS_IN_MINUTE * MINUTES_IN_HOUR *
                        HOURS_IN_DAY * DAYS_IN_YEAR;

        if (now.getTime() - de.getDateCreated().getTime() > MILLISECONDS_IN_YEAR) {
            result = false;
        }

        return result;
    }

    public String getDrilldownIdentifier(int indicatorTemplateId) {
        logger.info("entering getDrilldownIdentifer with indicatorTemplateId=" + indicatorTemplateId);
        String identifier = null;
        DashboardManager dashboardManager = SpringUtils.getBean(DashboardManager.class);
        if (dashboardManager != null && loggedInInfo != null) {
            DrilldownBean drilldown = dashboardManager.getDrilldownData(loggedInInfo, indicatorTemplateId, "null");
            if (drilldown != null) {
                identifier = getDrilldownIdentifier(drilldown.getName(), drilldown.getSubCategory(), drilldown.getCategory());
            } else {
                logger.info("drilldown is null");
            }
        } else {
            logger.info("dashboardManager is null");
        }
        logger.info("getDrilldownIdentifer returning " + identifier + " for indicatorTemplateId " + indicatorTemplateId);
        return identifier;
    }

    public String getDrilldownIdentifier(String name, String category, String subCategory) {
        return name + "|" + subCategory + "|" + category;
    }
}
