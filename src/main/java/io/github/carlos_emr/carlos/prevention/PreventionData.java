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

import java.util.*;

import javax.annotation.Nonnull;

import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import org.apache.commons.lang3.StringUtils;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionExtDao;
import io.github.carlos_emr.carlos.managers.DHIRSubmissionManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.providers.data.ProviderData;

import io.github.carlos_emr.carlos.util.UtilDateUtilities;

public class PreventionData {

    private static Logger log = MiscUtils.getLogger();
    private static PreventionDao preventionDao = (PreventionDao) SpringUtils.getBean(PreventionDao.class);
    private static PreventionExtDao preventionExtDao = (PreventionExtDao) SpringUtils.getBean(PreventionExtDao.class);
    private static PartialDateDao partialDateDao = SpringUtils.getBean(PartialDateDao.class);

    private PreventionData() {
        // prevent instantiation
    }

    private static Date stringToDate(String date) {
        if (date == null)
            return null;
        Date ret = UtilDateUtilities.StringToDate(date, "yyyy-MM-dd HH:mm");
        if (ret != null) {
            return ret;
        }
        ret = UtilDateUtilities.StringToDate(date, "yyyy-MM-dd");

        return ret;

    }

    private static PartialDate setPreventionDate(Prevention prevention, String date) {
        PartialDate pd = null;
        if (date.length() == 4) {
            pd = new PartialDate();
            pd.setTableName(PartialDate.PREVENTION);
            pd.setTableId(prevention.getId());
            pd.setFieldName(PartialDate.PREVENTION_PREVENTIONDATE);
            pd.setFormat("YYYY");
            date = date + "-01-01 00:00";
        } else if (date.length() == 7) {
            pd = new PartialDate();
            pd.setTableName(PartialDate.PREVENTION);
            pd.setTableId(prevention.getId());
            pd.setFieldName(PartialDate.PREVENTION_PREVENTIONDATE);
            pd.setFormat("YYYY-MM");
            date = date + "-01 00:00";
        }
        prevention.setPreventionDate(stringToDate(date));
        return pd;
    }

    public static Integer insertPreventionData(String creator, String demoNo, String date, String providerNo, String providerName, String preventionType, String refused, String nextDate, String neverWarn, ArrayList<Map<String, String>> list, String snomedId, String din) {
        Integer insertId = -1;
        try {
            Prevention prevention = new Prevention();
            prevention.setCreatorProviderNo(creator);
            prevention.setDemographicId(Integer.valueOf(demoNo));
            PartialDate pd = setPreventionDate(prevention, date);
            prevention.setProviderNo(providerNo);
            prevention.setPreventionType(preventionType);
            prevention.setNextDate(UtilDateUtilities.StringToDate(nextDate, "yyyy-MM-dd"));
            prevention.setNever(neverWarn.trim().equals("1"));
            if (refused.trim().equals("1")) prevention.setRefused(true);
            else if (refused.trim().equals("2")) prevention.setIneligible(true);
            else if (refused.trim().equals("3")) prevention.setCompletedExternally(true);
            prevention.setSnomedId(snomedId);
            preventionDao.persist(prevention);
            if (pd != null) {
                pd.setTableId(prevention.getId());
                partialDateDao.persist(pd);
            }
            if (prevention.getId() == null) return insertId;

            insertId = prevention.getId();
            for (int i = 0; i < list.size(); i++) {
                Map<String, String> h = list.get(i);
                for (Map.Entry<String, String> entry : h.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        addPreventionKeyValue("" + insertId, entry.getKey(), entry.getValue());
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return insertId;
    }

    public static void addPreventionKeyValue(String preventionId, String keyval, String val) {
        try {
            PreventionExt preventionExt = new PreventionExt();
            preventionExt.setPreventionId(Integer.valueOf(preventionId));
            preventionExt.setKeyval(keyval);
            preventionExt.setVal(val);

            preventionExtDao.persist(preventionExt);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public static Map<String, String> getPreventionKeyValues(String preventionId) {
        Map<String, String> h = new HashMap<String, String>();

        if (preventionId == null || preventionId.isEmpty()) {
            return h;
        }

        List<PreventionExt> preventionExts = preventionExtDao.findByPreventionId(Integer.valueOf(preventionId));

        if (preventionExts != null) {
            for (PreventionExt preventionExt : preventionExts) {
                h.put(preventionExt.getkeyval(), preventionExt.getVal());
            }
        }
        return h;
    }

    public static void deletePreventionData(String id) {
        try {
            Prevention prevention = preventionDao.find(Integer.valueOf(id));
            prevention.setDeleted(true);

            preventionDao.merge(prevention);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public static void setNextPreventionDate(String date, String id) {
        try {
            Prevention prevention = preventionDao.find(Integer.valueOf(id));
            prevention.setNextDate(UtilDateUtilities.StringToDate(date, "yyyy-MM-dd"));

            preventionDao.merge(prevention);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public static String getProviderName(Map<String, Object> hash) {
        String name = "";
        if (hash != null) {
            String proNum = (String) hash.get("provider_no");
            if (proNum == null || proNum.equals("-1")) {
                name = (String) hash.get("provider_name");
            } else {
                name = ProviderData.getProviderName(proNum);
            }
        }
        return name;
    }

    public static Integer updatetPreventionData(String id, String creator, String demoNo, String date, String providerNo, String providerName, String preventionType, String refused, String nextDate, String neverWarn, ArrayList<Map<String, String>> list, String snomedId) {
        deletePreventionData(id);
        return insertPreventionData(creator, demoNo, date, providerNo, providerName, preventionType, refused, nextDate, neverWarn, list, snomedId, null);
    }

    public static ArrayList<Map<String, Object>> getPreventionDataFromExt(String extKey, String extVal) {
        ArrayList<Map<String, Object>> list = new ArrayList<Map<String, Object>>();

        try {
            List<PreventionExt> preventionExts = preventionExtDao.findByKeyAndValue(extKey, extVal);
            for (PreventionExt preventionExt : preventionExts) {
                Map<String, Object> hash = getPreventionById(preventionDao.find(preventionExt.getPreventionId()).toString());
                if (hash.get("deleted") != null && ((String) hash.get("deleted")).equals("0")) {
                    list.add(hash);
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return list;
    }

    /*
     * Fetch one extended prevention key
     * Requires prevention id and keyval to return
     */
    public static String getExtValue(String id, String keyval) {
        try {
            List<PreventionExt> preventionExts = preventionExtDao.findByPreventionIdAndKey(Integer.valueOf(id), keyval);
            for (PreventionExt preventionExt : preventionExts) {
                return preventionExt.getVal();
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return "";
    }

    // //////

    /**
     * Method to get a list of (demographic #, prevention dates, and key values) of a certain type <injectionTppe> from a start Date to an end Date with a Ext key value EG get all
     * Rh injection's product #, from 2006-12-12 to 2006-12-18
     */
    public static ArrayList<Map<String, Object>> getExtValues(String injectionType, Date startDate, Date endDate, String keyVal) {
        ArrayList<Map<String, Object>> list = new ArrayList<Map<String, Object>>();

        List<Prevention> preventions = preventionDao.findByTypeAndDate(injectionType, startDate, endDate);
        for (Prevention prevention : preventions) {

            List<PreventionExt> preventionExts = preventionExtDao.findByPreventionIdAndKey(prevention.getId(), keyVal);
            try {
                for (PreventionExt preventionExt : preventionExts) {
                    Map<String, Object> h = new HashMap<String, Object>();
                    h.put("preventions_id", prevention.getId().toString());
                    h.put("demographic_no", prevention.getDemographicId().toString());
                    h.put("val", preventionExt.getVal());
                    h.put("prevention_date", prevention.getPreventionDate());
                    list.add(h);
                    break;
                }
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
        return list;
    }

    public static Date getDemographicDateOfBirth(LoggedInInfo loggedInInfo, Integer demoNo) {
        DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
        Demographic dd = demographicManager.getDemographic(loggedInInfo, demoNo);
        if (dd == null) return (null);
        Calendar bday = dd.getBirthDay();
        if (bday == null) return (null);
        return (bday.getTime());
    }

    public static ArrayList<Map<String, Object>> getPreventionData(LoggedInInfo loggedInInfo, Integer demoNo) {
        return getPreventionData(loggedInInfo, null, demoNo);
    }

    public static List<Prevention> getPrevention(LoggedInInfo loggedInInfo, String preventionType, Integer demographicId) {
        return preventionDao.findByTypeAndDemoNo(preventionType, demographicId);
    }

	@Nonnull
    public static ArrayList<Map<String, Object>> getPreventionData(LoggedInInfo loggedInInfo, String preventionType, Integer demographicId) {
        ArrayList<Map<String, Object>> list = new ArrayList<Map<String, Object>>();

        try {
            Date dob = getDemographicDateOfBirth(loggedInInfo, demographicId);
            List<Prevention> preventions = preventionType == null ? preventionDao.findNotDeletedByDemographicId(demographicId) : preventionDao.findByTypeAndDemoNo(preventionType, demographicId);
            for (Prevention prevention : preventions) {

                /*
                 * force case sensitive comparison of name; MySQL by default does case INsensitive
                 * DTaP and dTap are considered the same by MySQL
                 */
                if (preventionType != null && !prevention.getPreventionType().equals(preventionType)) {
                    continue;
                }

                Map<String, Object> h = new HashMap<String, Object>();
                h.put("id", prevention.getId().toString());
                h.put("refused", prevention.isRefused() ? "1" : prevention.isIneligible() ? "2" : prevention.isCompletedExternally() ? "3" : "0");
                h.put("type", prevention.getPreventionType());
                h.put("provider_no", prevention.getProviderNo());
                if (!StringUtils.isEmpty(prevention.getProviderNo())) {
                    if ("-1".equals(prevention.getProviderNo())) {
                        prevention.setPreventionExtendedProperties();
                        h.put("provider_name", prevention.getPreventionExtendedProperties().get("providerName"));
                    } else {
                        h.put("provider_name", ProviderData.getProviderName(prevention.getProviderNo()));
                    }
                }


                Date pDate = prevention.getPreventionDate();
                String d1 = UtilDateUtilities.DateToString(pDate, "yyyy-MM-dd HH:mm");
                String d2 = UtilDateUtilities.DateToString(pDate, "yyyy-MM-dd");

                d1 = partialDateDao.getDatePartial(d1, PartialDate.PREVENTION, prevention.getId(), PartialDate.PREVENTION_PREVENTIONDATE);
                d2 = partialDateDao.getDatePartial(d2, PartialDate.PREVENTION, prevention.getId(), PartialDate.PREVENTION_PREVENTIONDATE);


                h.put("prevention_date", blankIfNull(d1));
                h.put("prevention_date_asDate", pDate);
                h.put("prevention_date_no_time", blankIfNull(d2));

                h.put("comments", "");
                String age = "N/A";
                if (pDate != null) {
                    age = UtilDateUtilities.calcAgeAtDate(dob, pDate);
                }
                h.put("age", age);
                list.add(h);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return list;
    }

    public static String getPreventionComment(String id) {
        log.debug("Calling getPreventionComment " + id);
        String comment = null;

        try {
            List<PreventionExt> preventionExts = preventionExtDao.findByPreventionIdAndKey(Integer.valueOf(id), "comments");
            for (PreventionExt preventionExt : preventionExts) {
                comment = preventionExt.getVal();
                if (comment != null && comment.trim().equals("")) comment = null;
                break;
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return comment;
    }

    public static io.github.carlos_emr.carlos.prevention.Prevention getPrevention(LoggedInInfo loggedInInfo, Integer demoNo) {
        DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
        Demographic dd = demographicManager.getDemographic(loggedInInfo, demoNo);

        java.util.Date dob = null;
        String sex = null;
        if (dd != null) {
            Calendar temp = dd.getBirthDay();
            if (temp != null) dob = temp.getTime();
            sex = dd.getSex();
        }

        io.github.carlos_emr.carlos.prevention.Prevention p = new io.github.carlos_emr.carlos.prevention.Prevention(sex, dob);

        PreventionDao dao = SpringUtils.getBean(PreventionDao.class);
        for (Prevention pp : dao.findActiveByDemoId(demoNo)) {
            PreventionItem pi = new PreventionItem(pp);
            p.addPreventionItem(pi);
        }
        return p;
    }

    public static class PreventionsComparator implements Comparator<Map<String, Object>> {
        public int compare(Map<String, Object> o1, Map<String, Object> o2) {
            Comparable date1 = (Comparable) o1.get("prevention_date_asDate");
            Comparable date2 = (Comparable) o2.get("prevention_date_asDate");

            if (date1 != null && date2 != null) {
                if (date1 instanceof Calendar) {
                    date1 = ((Calendar) date1).getTime();
                }

                if (date2 instanceof Calendar) {
                    date2 = ((Calendar) date2).getTime();
                }

                return (date1.compareTo(date2));
            } else {
                return (0);
            }
        }
    }

    public static class PreventionComparator implements Comparator<Prevention> {
        public int compare(Prevention o1, Prevention o2) {
            Date date1 = o1.getPreventionDate();
            Date date2 = o2.getPreventionDate();

            if (date1 != null && date2 != null) {

                return (date1.compareTo(date2));
            } else {
                return (0);
            }
        }
    }

    public static Map<String, Object> getPreventionById(String id) {
        Map<String, Object> h = null;

        try {
            Prevention prevention = preventionDao.find(Integer.valueOf(id));
            if (prevention != null) {
                Map<String, String> ext = getPreventionKeyValues(prevention.getId().toString());

                h = new HashMap<String, Object>();
                String providerName = null;
                if (!"-1".equals(prevention.getProviderNo())) {
                    providerName = ProviderData.getProviderName(prevention.getProviderNo());
                } else {
                    providerName = ext.get("providerName") != null ? ext.get("providerName") : "";
                }

                String preventionDate = UtilDateUtilities.DateToString(prevention.getPreventionDate(), "yyyy-MM-dd HH:mm");
                String lastUpdateDate = UtilDateUtilities.DateToString(prevention.getLastUpdateDate(), "yyyy-MM-dd");
                @SuppressWarnings("deprecation")
                String creatorName = ProviderData.getProviderName(prevention.getCreatorProviderNo());

                addToHashIfNotNull(h, "id", prevention.getId().toString());
                addToHashIfNotNull(h, "demographicNo", prevention.getDemographicId().toString());
                addToHashIfNotNull(h, "provider_no", prevention.getProviderNo());
                addToHashIfNotNull(h, "providerName", providerName);
                addToHashIfNotNull(h, "creationDate", UtilDateUtilities.DateToString(prevention.getCreationDate(), "yyyy-MM-dd"));
                addToHashIfNotNull(h, "preventionDate", preventionDate);
                addToHashIfNotNull(h, "prevention_date_asDate", prevention.getPreventionDate());
                addToHashIfNotNull(h, "preventionType", prevention.getPreventionType());
                addToHashIfNotNull(h, "deleted", prevention.isDeleted() ? "1" : "0");
                addToHashIfNotNull(h, "refused", prevention.isRefused() ? "1" : prevention.isIneligible() ? "2" : prevention.isCompletedExternally() ? "3" : "0");
                addToHashIfNotNull(h, "next_date", UtilDateUtilities.DateToString(prevention.getNextDate(), "yyyy-MM-dd"));
                addToHashIfNotNull(h, "never", prevention.isNever() ? "1" : "0");
                addToHashIfNotNull(h, "creator", prevention.getCreatorProviderNo());
                addToHashIfNotNull(h, "snomedId", prevention.getSnomedId());
                String summary = "Prevention " + prevention.getPreventionType() + " provided by " + providerName + " on " + preventionDate;
                summary = summary + " entered by " + creatorName + " on " + lastUpdateDate;

                addToHashIfNotNull(h, "brandSnomedId", ext.get("brandSnomedId"));

                if (ext.containsKey("result")) { //This is a preventive Test
                    addToHashIfNotNull(h, "result", ext.get("result"));
                    summary += "\nResult: " + ext.get("result");
                    if (ext.containsKey("reason") && !ext.get("reason").equals("")) {
                        addToHashIfNotNull(h, "reason", ext.get("reason"));
                        summary += "\nReason: " + ext.get("reason");
                    }
                } else { //This is an immunization
                    if (ext.containsKey("name") && !ext.get("name").equals("")) {
                        addToHashIfNotNull(h, "name", ext.get("name"));
                        summary += "\nName: " + ext.get("name");
                    }
                    if (ext.containsKey("location") && !ext.get("location").equals("")) {
                        addToHashIfNotNull(h, "location", ext.get("location"));
                        summary += "\nLocation: " + ext.get("location");
                    }
                    if (ext.containsKey("expiryDate") && !ext.get("expiryDate").equals("")) {
                        addToHashIfNotNull(h, "expiryDate", ext.get("expiryDate"));
                        summary += "\nExpiryDate: " + ext.get("expiryDate");
                    }
                    if (ext.containsKey("route") && !ext.get("route").equals("")) {
                        addToHashIfNotNull(h, "route", ext.get("route"));
                        summary += "\nRoute: " + ext.get("route");
                    }
                    if (ext.containsKey("dose") && !ext.get("dose").equals("")) {
                        addToHashIfNotNull(h, "dose", ext.get("dose"));
                        summary += "\nDose: " + ext.get("dose");
                    }
                    if (ext.containsKey("lot") && !ext.get("lot").equals("")) {
                        addToHashIfNotNull(h, "lot", ext.get("lot"));
                        summary += "\nLot: " + ext.get("lot");
                    }
                    if (ext.containsKey("manufacture") && !ext.get("manufacture").equals("")) {
                        addToHashIfNotNull(h, "manufacture", ext.get("manufacture"));
                        summary += "\nManufacturer: " + ext.get("manufacture");
                    }
                    if (ext.containsKey("din") && !ext.get("din").equals("")) {
                        addToHashIfNotNull(h, "din", ext.get("din"));
                        summary += "\nDIN: " + ext.get("din");
                    }
                }
                if (ext.containsKey("comments") && !ext.get("comments").equals("")) {
                    addToHashIfNotNull(h, "comments", ext.get("comments"));
                    summary += "\nComments: " + ext.get("comments");
                }

                DHIRSubmissionManager dhirSubmissionManager = SpringUtils.getBean(DHIRSubmissionManager.class);
                List<DHIRSubmissionLog> dhirLogs = dhirSubmissionManager.findByPreventionId(prevention.getId());
                if (!dhirLogs.isEmpty()) {
                    summary += "\n\nDHIR Submission Transaction ID: " + dhirLogs.get(0).getTransactionId();
                    summary += "\nDHIR Submission Location ID: " + dhirLogs.get(0).getBundleId();
                }
                addToHashIfNotNull(h, "summary", summary);
                log.debug("1" + h.get("preventionType") + " " + h.size());
                log.debug("id" + h.get("id"));

            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return h;
    }

    private static void addToHashIfNotNull(Map<String, Object> h, String key, String val) {
        if (val != null && !val.equalsIgnoreCase("null")) {
            h.put(key, val);
        }
    }

    private static void addToHashIfNotNull(Map<String, Object> h, String key, Date val) {
        if (val != null) {
            h.put(key, val);
        }
    }

    private static String blankIfNull(String s) {
        if (s == null) return "";
        return s;
    }
}
