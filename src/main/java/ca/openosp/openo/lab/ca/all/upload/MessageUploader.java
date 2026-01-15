//CHECKSTYLE:OFF
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
 */


/*
 * MessageUploader.java
 *
 * Created on June 18, 2007, 1:53 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ca.openosp.openo.lab.ca.all.upload;

import ca.openosp.Misc;
import ca.openosp.openo.commn.dao.*;
import ca.openosp.openo.commn.model.*;
import ca.openosp.openo.lab.ca.all.parsers.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.dao.ProviderDao;
import ca.openosp.openo.commn.OtherIdManager;
import ca.openosp.openo.managers.DemographicManager;
import ca.openosp.openo.olis.dao.OLISSystemPreferencesDao;
import ca.openosp.openo.olis.model.OLISSystemPreferences;
import ca.openosp.openo.utility.DbConnectionFilter;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.OscarProperties;
import ca.openosp.openo.demographic.data.DemographicMerged;
import ca.openosp.openo.lab.ca.all.Hl7textResultsData;
import ca.openosp.openo.util.UtilDateUtilities;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

public final class MessageUploader {

    private static final Logger logger = MiscUtils.getLogger();
    private static PatientLabRoutingDao patientLabRoutingDao = SpringUtils.getBean(PatientLabRoutingDao.class);
    private static ProviderLabRoutingDao providerLabRoutingDao = SpringUtils.getBean(ProviderLabRoutingDao.class);
    private static RecycleBinDao recycleBinDao = SpringUtils.getBean(RecycleBinDao.class);
    private static Hl7TextInfoDao hl7TextInfoDao = (Hl7TextInfoDao) SpringUtils.getBean(Hl7TextInfoDao.class);
    private static Hl7TextMessageDao hl7TextMessageDao = (Hl7TextMessageDao) SpringUtils.getBean(Hl7TextMessageDao.class);
    private static MeasurementsExtDao measurementsExtDao = SpringUtils.getBean(MeasurementsExtDao.class);
    private static MeasurementDao measurementDao = SpringUtils.getBean(MeasurementDao.class);
    private static FileUploadCheckDao fileUploadCheckDao = SpringUtils.getBean(FileUploadCheckDao.class);
    private static DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);

    private MessageUploader() {
        // there's no reason to instantiate a class with no fields.
    }

    public static String routeReport(LoggedInInfo loggedInInfo, String serviceName, String type, String hl7Body, int fileId) throws Exception {
        return routeReport(loggedInInfo, serviceName, type, hl7Body, fileId, null);
    }

    /**
     * Insert the lab into the proper tables of the database
     */
    public static String routeReport(LoggedInInfo loggedInInfo, String serviceName, String type, String hl7Body, int fileId, RouteReportResults results) throws Exception {
        return routeReport(loggedInInfo, serviceName, Factory.getHandler(type, hl7Body), hl7Body, fileId, results, type);
    }

    public static String routeReport(LoggedInInfo loggedInInfo, String serviceName, MessageHandler h, String hl7Body, int fileId, RouteReportResults results, String type) throws Exception {

        String retVal = "";

        if (h == null) {
            throw new Exception("Unabled to continue. No valid handler found.");
        }
        
        String firstName = h.getFirstName();
        String lastName = h.getLastName();
        String dob = h.getDOB();
        String sex = h.getSex();
        String hin = h.getHealthNum();
        String resultStatus = "";
        String priority = h.getMsgPriority();
        String requestingClient = h.getDocName();
        String reportStatus = h.getOrderStatus();
        String accessionNum = h.getAccessionNum();
        String fillerOrderNum = h.getFillerOrderNumber();
        String sendingFacility = h.getPatientLocation();
        ArrayList<String> docNums = h.getDocNums();
        int finalResultCount = h.getOBXFinalResultCount();


        /*
         * start with OBR date because the date in the MSH segment may not be related to
         * when the actual lab result was observed.
         * Sometimes the "message date" is altered in data transfers or HL7 re-issues.
         */
        String obrDate = h.getServiceDate();
        if (obrDate == null || obrDate.isEmpty()) {
            obrDate = h.getMsgDate();
        }

        /*
         * Temporary test for Excelleris labs.  This method should be added to the
         * interface if useful with other HL7 labs.
         */
        String label = "";
        if (h instanceof PATHL7Handler) {
            label = ((PATHL7Handler) h).getLabel();
        }

        if (h instanceof HHSEmrDownloadHandler) {
            try {
                String chartNo = ((HHSEmrDownloadHandler) h).getPatientIdByType("MR");
                if (chartNo != null) {
                    //let's get the hin
                    List<Demographic> clients = demographicManager.getDemosByChartNo(loggedInInfo, chartNo);
                    if (clients != null && clients.size() > 0) {
                        hin = clients.get(0).getHin();
                    }
                }
            } catch (Exception e) {
                logger.error("HHS ERROR", e);
            }
        }

        // get actual ohip numbers based on doctor first and last name for spire lab
        if (h instanceof SpireHandler) {
            List<String> docNames = ((SpireHandler) h).getDocNames();
            //logger.debug("docNames:");
            for (int i = 0; i < docNames.size(); i++) {
                logger.debug(i + " " + docNames.get(i));
            }
            if (docNames != null) {
                docNums = findProvidersForSpireLab(docNames);
            }
        }
        logger.debug("docNums:");
        for (int i = 0; i < docNums.size(); i++) {
            logger.debug(i + " " + docNums.get(i));
        }

        try {
            // reformat date
            String format = "yyyy-MM-dd HH:mm:ss".substring(0, obrDate.length() - 1);
            obrDate = UtilDateUtilities.DateToString(UtilDateUtilities.StringToDate(obrDate, format), "yyyy-MM-dd HH:mm:ss");
        } catch (Exception e) {
            logger.error("Error parsing obr date : ", e);
            throw e;
        }

        int i = 0;
        int j = 0;
        while (resultStatus.equals("") && i < h.getOBRCount()) {
            j = 0;
            while (resultStatus.equals("") && j < h.getOBXCount(i)) {
                if (h.isOBXAbnormal(i, j)) resultStatus = "A";
                j++;
            }
            i++;
        }

        ArrayList<String> disciplineArray = h.getHeaders();
        String next = "";

        if (disciplineArray != null && disciplineArray.size() > 0) {
            next = disciplineArray.get(0);
        }

        int sepMark;
        if ((sepMark = next.indexOf("<br />")) < 0) {
            if ((sepMark = next.indexOf(" ")) < 0) sepMark = next.length();
        }

        String discipline = next.substring(0, sepMark).trim();

        for (i = 1; i < disciplineArray.size(); i++) {

            next = disciplineArray.get(i);
            if ((sepMark = next.indexOf("<br />")) < 0) {
                if ((sepMark = next.indexOf(" ")) < 0) sepMark = next.length();
            }

            if (!next.trim().equals("")) {
                discipline = discipline + "/" + next.substring(0, sepMark);
            }
        }

        boolean isTDIS = "TDIS".equals(type);
        boolean hasBeenUpdated = false;
        Hl7TextMessage hl7TextMessage = new Hl7TextMessage();
        Hl7TextInfo hl7TextInfo = new Hl7TextInfo();


        if (h instanceof MEDITECHHandler) {
            discipline = ((MEDITECHHandler) h).getDiscipline();
        }

        if (isTDIS) {
            List<Hl7TextInfo> matchingTdisLab = hl7TextInfoDao.searchByFillerOrderNumber(fillerOrderNum, sendingFacility);
            if (matchingTdisLab.size() > 0) {

                hl7TextMessageDao.updateIfFillerOrderNumberMatches(new String(Base64.encodeBase64(hl7Body.getBytes(MiscUtils.DEFAULT_UTF8_ENCODING)), MiscUtils.DEFAULT_UTF8_ENCODING), fileId, matchingTdisLab.get(0).getLabNumber());

                hl7TextInfoDao.updateReportStatusByLabId(reportStatus, matchingTdisLab.get(0).getLabNumber());
                hasBeenUpdated = true;
            }
        }
        int insertID = 0;
        if (!isTDIS || !hasBeenUpdated) {
            hl7TextMessage.setFileUploadCheckId(fileId);
            hl7TextMessage.setType(type);
            hl7TextMessage.setBase64EncodedeMessage(new String(Base64.encodeBase64(hl7Body.getBytes(MiscUtils.DEFAULT_UTF8_ENCODING)), MiscUtils.DEFAULT_UTF8_ENCODING));
            hl7TextMessage.setServiceName(serviceName);
            hl7TextMessageDao.persist(hl7TextMessage);

            insertID = hl7TextMessage.getId();
            hl7TextInfo.setLabNumber(insertID);
            hl7TextInfo.setLastName(lastName);
            hl7TextInfo.setFirstName(firstName);
            hl7TextInfo.setSex(sex);
            hl7TextInfo.setHealthNumber(hin);
            hl7TextInfo.setResultStatus(resultStatus);
            hl7TextInfo.setFinalResultCount(finalResultCount);
            hl7TextInfo.setObrDate(obrDate.trim());
            hl7TextInfo.setPriority(priority);
            hl7TextInfo.setRequestingProvider(requestingClient);
            hl7TextInfo.setDiscipline(discipline);
            hl7TextInfo.setReportStatus(reportStatus);
            hl7TextInfo.setAccessionNumber(accessionNum);
            hl7TextInfo.setSendingFacility(sendingFacility);
            hl7TextInfo.setFillerOrderNum(fillerOrderNum);

            label = mergeLabLabels(hl7TextInfoDao.searchByAccessionNumberOrderByObrDate(accessionNum), label);
            hl7TextInfo.setLabel(label);

            hl7TextInfoDao.persist(hl7TextInfo);
        }

        String demProviderNo = null;

        try (Connection connection = DbConnectionFilter.getThreadLocalDbConnection()) {
            demProviderNo = patientRouteReport(loggedInInfo, type, insertID, lastName, firstName, sex, dob, hin, connection);
        }

		if("OLIS_HL7".equals(type) && "0".equals(demProviderNo)) {
			OLISSystemPreferencesDao olisPrefDao = (OLISSystemPreferencesDao)SpringUtils.getBean("OLISSystemPreferencesDao");
            OLISSystemPreferences olisPreferences = olisPrefDao.getPreferences();

            try (Connection connection = DbConnectionFilter.getThreadLocalDbConnection()) {
                if (olisPreferences.isFilterPatients()) {
                    //set as unclaimed
                    providerRouteReport(String.valueOf(insertID), null, connection, String.valueOf(0), type);
                } else {
                    providerRouteReport(String.valueOf(insertID), docNums, DbConnectionFilter.getThreadLocalDbConnection(), demProviderNo, type);
                }
            }
        } else {
            Integer limit = null;
            boolean orderByLength = false;
            String search = null;
			if ("Spire".equals(type)) {
                limit = Integer.valueOf(1);
                orderByLength = true;
                search = "provider_no";
            }

			if( "MEDITECH".equals(type) || "ExcellerisON".equals(type) ) {
				search = "practitionerNo"; // ie the college number <oscarDB>.Provider.practitionerNo
            }

            if ("IHAPOI".equals(type)) {
                search = "hso_no";
            }

            try (Connection connection = DbConnectionFilter.getThreadLocalDbConnection()) {
                providerRouteReport(String.valueOf(insertID), docNums, connection, demProviderNo, type, search, limit, orderByLength);
            }
        }
        retVal = h.audit();
        if (results != null) {
            results.segmentId = insertID;
        }

        return (retVal);

    }

    /**
     * Method findProvidersForSpireLab
     * Finds the providers that are associated with a spire lab.  (need to do this using doctor names, as
     * spire labs don't have a valid ohip number associated with them).
     */
    private static ArrayList<String> findProvidersForSpireLab(List<String> docNames) {
        List<String> docNums = new ArrayList<String>();
        ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);

        for (int i = 0; i < docNames.size(); i++) {
            String[] firstLastName = docNames.get(i).split("\\s");
            if (firstLastName != null && firstLastName.length >= 2) {
                //logger.debug("Searching for providers with first and last name: " + firstLastName[0] + " " + firstLastName[firstLastName.length-1]);
                List<Provider> provList = providerDao.getProviderLikeFirstLastName("%" + firstLastName[0] + "%", firstLastName[firstLastName.length - 1]);
                if (provList != null) {
                    int provIndex = findProviderWithShortestFirstName(provList);
                    if (provIndex != -1 && provList.size() >= 1 && !provList.get(provIndex).getProviderNo().equals("0")) {
                        docNums.add(provList.get(provIndex).getProviderNo());
                        //logger.debug("ADDED1: " + provList.get(provIndex).getProviderNo());
                    } else {
                        // prepend 'dr ' to first name and try again
                        provList = providerDao.getProviderLikeFirstLastName("dr " + firstLastName[0], firstLastName[1]);
                        if (provList != null) {
                            provIndex = findProviderWithShortestFirstName(provList);
                            if (provIndex != -1 && provList.size() == 1 && !provList.get(provIndex).getProviderNo().equals("0")) {
                                //logger.debug("ADDED2: " + provList.get(provIndex).getProviderNo());
                                docNums.add(provList.get(provIndex).getProviderNo());
                            }
                        }
                    }
                }
            }
        }

        return (ArrayList<String>) docNums;
    }

    /**
     * Method findProviderWithShortestFirstName
     * Finds the providers with the shortest first name in a list of providers.
     */
    private static int findProviderWithShortestFirstName(List<Provider> provList) {
        if (provList == null || provList.isEmpty())
            return -1;

        int index = 0;
        int shortestLength = provList.get(0).getFirstName().length();
        for (int i = 1; i < provList.size(); i++) {
            int curLength = provList.get(i).getFirstName().length();
            if (curLength < shortestLength) {
                index = i;
                shortestLength = curLength;
            }
        }

        return index;
    }

    /**
     * Attempt to match the doctors from the lab to a providers
     */
    private static void providerRouteReport(String labId, ArrayList<String> docNums, Connection conn, String altProviderNo, String labType, String search_on, Integer limit, boolean orderByLength) throws Exception {
        // Using HashSet to avoid duplicate providers numbers
        LinkedHashSet<String> providerNums = new LinkedHashSet<>();
        PreparedStatement pstmt;
        String sql = "";
        String sqlLimit = "";
        String sqlOrderByLength = "";
        String sqlSearchOn = "ohip_no";

        if (search_on != null && search_on.length() > 0) {
            sqlSearchOn = search_on;
        }

        if (limit != null && limit.intValue() > 0) {
            sqlLimit = " limit " + limit.toString();
        }

        if (orderByLength) {
            sqlOrderByLength = " order by length(first_name)";
        }

        if (docNums != null) {
            for (int i = 0; i < docNums.size(); i++) {

                if (docNums.get(i) != null && !((String) docNums.get(i)).trim().equals("")) {
                    if ("ON".equals(OscarProperties.getInstance().getProperty("billregion", "ON"))) {
                        StringBuilder practitionerNum = new StringBuilder(((String) docNums.get(i)).trim());
                        if (sqlSearchOn.equalsIgnoreCase("ohip_no")) {
                            while (practitionerNum.length() < 6) {
                                practitionerNum.insert(0, "0");
                            }
                        }
                        sql = "select provider_no from provider where " + sqlSearchOn + " = '" + practitionerNum.toString() + "'" + sqlOrderByLength + sqlLimit;
                    } else {
                        sql = "select provider_no from provider where " + sqlSearchOn + " LIKE '" + ((String) docNums.get(i)) + "'" + sqlOrderByLength + sqlLimit;
                    }
                    pstmt = conn.prepareStatement(sql);
                    ResultSet rs = pstmt.executeQuery();
                    while (rs.next()) {
                        providerNums.add(Misc.getString(rs, "provider_no"));
                    }
                    rs.close();
                    pstmt.close();

                    String otherIdMatchKey = OscarProperties.getInstance().getProperty("lab.other_id_matching", "");
                    if (otherIdMatchKey.length() > 0) {
                        OtherId otherId = OtherIdManager.searchTable(OtherIdManager.PROVIDER, otherIdMatchKey, (String) docNums.get(i));
                        if (otherId != null) {
                            providerNums.add(otherId.getTableId());
                        }
                    }

                }
            }
        }


        ProviderLabRouting routing = new ProviderLabRouting();
        if (providerNums.size() > 0) {
            for (String provider_no : providerNums) {
                routing.route(labId, provider_no, conn, "HL7");
            }
        } else {
            if (altProviderNo != null && !altProviderNo.equals("0")) {
                routing.route(labId, altProviderNo, conn, "HL7");
            } else {
                routing.route(labId, "0", conn, "HL7");
            }
        }
    }

    /**
     * Attempt to match the doctors from the lab to a providers
     */
    private static void providerRouteReport(String labId, ArrayList docNums, Connection conn, String altProviderNo, String labType) throws Exception {
        providerRouteReport(labId, docNums, conn, altProviderNo, labType, null, null, false);
    }


    public static Integer willOLISLabReportMatch(LoggedInInfo loggedInInfo, String lastName, String firstName, String sex, String dob, String hin) {
        Connection conn = null;
        PatientLabRoutingResult result = null;
        String sql = null;
        String demo = "0";
        String provider_no = "0";
        String dobYear = null;
        String dobMonth = null;
        String dobDay = null;
        String hinMod = null;

        try {
            conn = DbConnectionFilter.getThreadLocalDbConnection();

            if (hin != null) {
                hinMod = new String(hin);
                if (hinMod.length() == 12) {
                    hinMod = hinMod.substring(0, 10);
                }
            }
            if (dob != null && !dob.equals("")) {
                String[] dobArray = dob.trim().split("-");
                dobYear = dobArray[0];
                dobMonth = dobArray[1];
                dobDay = dobArray[2];
            }

            if (hinMod == null || dobYear == null || dobMonth == null || dobDay == null) {
                return null;
            }

            sql = "select demographic_no, provider_no from demographic where hin='" + hinMod + "' and " + " last_name = '" + lastName + "' and " + " year_of_birth = '" + dobYear + "' and " + " month_of_birth = '" + dobMonth + "' and " + " date_of_birth = '" + dobDay + "' and " + " sex = '" + sex + "' ";

            logger.debug(sql);
            PreparedStatement pstmt = conn.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery();
            int count = 0;

            while (rs.next()) {
                result = new PatientLabRoutingResult();
                demo = Misc.getString(rs, "demographic_no");
                provider_no = Misc.getString(rs, "provider_no");
                result.setDemographicNo(Integer.parseInt(demo));
                result.setProviderNo(provider_no);
                count++;
            }
            rs.close();
            pstmt.close();
            if (count > 1) {
                result = null;
            }

        } catch (SQLException sqlE) {
            return null;
        } finally {
            DbConnectionFilter.releaseThreadLocalDbConnection();
        }

        if (result != null) {
            DemographicMerged dm = new DemographicMerged();
            Integer headDemo = dm.getHead(result.getDemographicNo());
            if (headDemo != null && headDemo.intValue() != result.getDemographicNo()) {
                Demographic demoTmp = demographicManager.getDemographic(loggedInInfo, headDemo);
                if (demoTmp != null) {
                    result.setDemographicNo(demoTmp.getDemographicNo());
                    result.setProviderNo(demoTmp.getProviderNo());
                } else {
                    logger.info("Unable to load the head record of this patient record. (" + result.getDemographicNo() + ")");
                    result = null;
                }
            }
        }


        return result != null ? result.getDemographicNo() : null;
    }


    private static String patientRouteReportOLIS(LoggedInInfo loggedInInfo, int labId, String lastName, String sex, String dob, String hin, Connection conn) throws SQLException {
        PatientLabRoutingResult result = null;

        String sql = null;
        String demo = "0";
        String provider_no = "0";
        String dobYear = null;
        String dobMonth = null;
        String dobDay = null;
        String hinMod = null;

        try {
            if (hin != null) {
                hinMod = new String(hin);
                if (hinMod.length() == 12) {
                    hinMod = hinMod.substring(0, 10);
                }
            }
            if (dob != null && !dob.equals("")) {
                String[] dobArray = dob.trim().split("-");
                dobYear = dobArray[0];
                dobMonth = dobArray[1];
                dobDay = dobArray[2];
            }

            if (hinMod == null || dobYear == null || dobMonth == null || dobDay == null) {
                return null;
            }

            sql = "select demographic_no, provider_no from demographic where hin='" + hinMod + "' and " + " last_name = '" + lastName + "' and " + " year_of_birth = '" + dobYear + "' and " + " month_of_birth = '" + dobMonth + "' and " + " date_of_birth = '" + dobDay + "' and " + " sex = '" + sex + "' ";

            logger.debug(sql);
            PreparedStatement pstmt = conn.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery();
            int count = 0;

            while (rs.next()) {
                result = new PatientLabRoutingResult();
                demo = Misc.getString(rs, "demographic_no");
                provider_no = Misc.getString(rs, "provider_no");
                result.setDemographicNo(Integer.parseInt(demo));
                result.setProviderNo(provider_no);
                count++;
            }
            rs.close();
            pstmt.close();
            if (count > 1) {
                result = null;
            }

        } catch (SQLException sqlE) {
            throw sqlE;
        }


        try {
            //did this link a merged patient? if so, we need to make sure we are the head record, or update
            //result to be the head record.
            if (result != null) {
                DemographicMerged dm = new DemographicMerged();
                Integer headDemo = dm.getHead(result.getDemographicNo());
                if (headDemo != null && headDemo.intValue() != result.getDemographicNo()) {
                    Demographic demoTmp = demographicManager.getDemographic(loggedInInfo, headDemo);
                    if (demoTmp != null) {
                        result.setDemographicNo(demoTmp.getDemographicNo());
                        result.setProviderNo(demoTmp.getProviderNo());
                    } else {
                        logger.info("Unable to load the head record of this patient record. (" + result.getDemographicNo() + ")");
                        result = null;
                    }
                }
            }


            if (result == null) {
                logger.info("Could not find patient for lab: " + labId);
            } else {
                Hl7textResultsData.populateMeasurementsTable("" + labId, result.getDemographicNo().toString());
            }

            if (result != null) {
                sql = "insert into patientLabRouting (demographic_no, lab_no,lab_type,dateModified,created) values ('" + ((result != null && result.getDemographicNo() != null) ? result.getDemographicNo().toString() : "0") + "', '" + labId + "','HL7',now(),now())";
                Connection c = null;
                PreparedStatement pstmt = null;
                try {
                    c = DbConnectionFilter.getThreadLocalDbConnection();
                    pstmt = c.prepareStatement(sql);
                    pstmt.executeUpdate();

                } finally {
                    try {
                        pstmt.close();
                        c.close();
                    } catch (SQLException e) {

                    }
                }

            }
        } catch (SQLException sqlE) {
            logger.info("NO MATCHING PATIENT FOR LAB id =" + labId);
            throw sqlE;
        }

        return (result != null) ? result.getProviderNo() : "0";
    }

    /**
     * Attempt to match the patient from the lab to a demographic, return the patients providers which is to be used then no other providers can be found to match the patient to.
     */
    private static String patientRouteReport(LoggedInInfo loggedInInfo, String labType, int labId, String lastName, String firstName, String sex, String dob, String hin, Connection conn) throws SQLException {

        if ("OLIS_HL7".equals(labType)) {
            return patientRouteReportOLIS(loggedInInfo, labId, lastName, sex, dob, hin, conn);
        }
        PatientLabRoutingResult result = null;

        String sql = null;
        String demo = "0";
        String provider_no = "0";
        String dobYear = "%";
        String dobMonth = "%";
        String dobDay = "%";
        String hinMod = null;


			try {
				if (hin.equalsIgnoreCase("UNKNOWN")) { hin = ""; }
				if (hin != null) {
					hinMod = new String(hin);
					if (hinMod.length() == 12) {
						hinMod = hinMod.substring(0, 10);
					}
				}

				if (dob != null && !dob.equals("") && !dob.equalsIgnoreCase("UNKNOWN")) {
					String[] dobArray = dob.trim().split("-");
					dobYear = dobArray[0];
					dobMonth = dobArray[1];
					dobDay = dobArray[2];
				}

				// only the first letter of names
				if (!firstName.equals("")) firstName = firstName.substring(0, 1);
				if (!lastName.equals("")) lastName = lastName.substring(0, 1);


				// HIN is ALWAYS required for lab matching. Please do not revert this code. Previous iterations have caused fatal patient miss-matches.
				if(hinMod != null && !hinMod.trim().isEmpty()) {
					if (OscarProperties.getInstance().getBooleanProperty("LAB_NOMATCH_NAMES", "yes")) {
						sql = "select demographic_no, provider_no from demographic where hin='" + hinMod + "' and " + " year_of_birth like '" + dobYear + "' and " + " month_of_birth like '" + dobMonth + "' and " + " date_of_birth like '" + dobDay + "' and " + " sex like '" + sex + "%' ";
					} else {
						sql = "select demographic_no, provider_no from demographic where hin='" + hinMod + "' and " + " last_name like '" + lastName + "%' and " + " first_name like '" + firstName + "%' and " + " year_of_birth like '" + dobYear + "' and " + " month_of_birth like '" + dobMonth + "' and " + " date_of_birth like '" + dobDay + "' and " + " sex like '" + sex + "%' ";
					}
				}

				if( sql != null ) {
					logger.debug(sql);
					PreparedStatement pstmt = conn.prepareStatement(sql);
					ResultSet rs = pstmt.executeQuery();
					int count = 0;

					while (rs.next()) {
						result = new PatientLabRoutingResult();
						demo = Misc.getString(rs, "demographic_no");
						provider_no = Misc.getString(rs, "provider_no");
						result.setDemographicNo(Integer.parseInt(demo));
						result.setProviderNo(provider_no);
						count++;
					}
					rs.close();
					pstmt.close();
					if(count > 1) {
						result = null;
					}
				}
			} catch (SQLException sqlE) {
				throw sqlE;
			}


        if (result != null) {
            DemographicMerged dm = new DemographicMerged();
            Integer headDemo = dm.getHead(result.getDemographicNo());
            if (headDemo != null && headDemo.intValue() != result.getDemographicNo()) {
                Demographic demoTmp = demographicManager.getDemographic(loggedInInfo, headDemo);
                if (demoTmp != null) {
                    result.setDemographicNo(demoTmp.getDemographicNo());
                    result.setProviderNo(demoTmp.getProviderNo());
                } else {
                    logger.info("Unable to load the head record of this patient record. (" + result.getDemographicNo() + ")");
                    result = null;
                }
            }
        }


        if (result == null) {
            logger.info("Could not find patient for lab: " + labId);
        } else {
            Hl7textResultsData.populateMeasurementsTable("" + labId, result.getDemographicNo().toString());
        }

        if (result != null) {
            PatientLabRouting patientLabRouting = new PatientLabRouting();
            PatientLabRoutingDao patientLabRoutingDao = SpringUtils.getBean(PatientLabRoutingDao.class);
            String demographicNo = result.getDemographicNo() != null ? result.getDemographicNo().toString() : "0";

            patientLabRouting.setDemographicNo(Integer.parseInt(demographicNo));
            patientLabRouting.setLabNo(labId);
            patientLabRouting.setLabType("HL7");
            patientLabRouting.setDateModified(new Date());
            patientLabRouting.setCreated(new Date());

            patientLabRoutingDao.persist(patientLabRouting);

        }

        return (result != null) ? result.getProviderNo() : "0";
    }

    /**
     * Used when errors occur to clean the database of labs that have not been inserted into all of the necessary tables
     */
    public static void clean(int fileId) {

        List<Hl7TextMessage> results = hl7TextMessageDao.findByFileUploadCheckId(fileId);


        for (Hl7TextMessage result : results) {
            int lab_id = result.getId();

            Hl7TextInfo hti = hl7TextInfoDao.findLabId(lab_id);
            if (hti != null) {
                RecycleBin rb = new RecycleBin();
                rb.setProviderNo("0");
                rb.setUpdateDateTime(new Date());
                rb.setTableName("hl7TextInfo");
                rb.setKeyword(String.valueOf(lab_id));
                rb.setTableContent("<id>" + hti.getId() + "</id>" + "<lab_no>" + lab_id + "</lab_no>" + "<sex>" + hti.getSex() + "</sex>" + "<health_no>" + hti.getHealthNumber() + "</health_no>" + "<result_status>"
                        + hti.getResultStatus() + "</result_status>" + "<final_result_count>" + hti.getFinalResultCount() + "</final_result_count>" + "<obr_date>" + hti.getObrDate() + "</obr_date>" + "<priority>" + hti.getPriority() + "</priority>" + "<requesting_client>" + hti.getRequestingProvider() + "</requesting_client>" + "<discipline>" + hti.getDiscipline() + "</discipline>"
                        + "<last_name>" + hti.getLastName() + "</last_name>" + "<first_name>" + hti.getFirstName() + "</first_name>" + "<report_status>" + hti.getReportStatus() + "</report_status>" + "<accessionNum>" + hti.getAccessionNumber() + "</accessionNum>'");
                recycleBinDao.persist(rb);

                hl7TextInfoDao.remove(hl7TextInfoDao.findLabId(lab_id).getId());
            }


            Hl7TextMessage htm = hl7TextMessageDao.find(lab_id);
            if (htm != null) {
                RecycleBin rb = new RecycleBin();
                rb.setProviderNo("0");
                rb.setUpdateDateTime(new Date());
                rb.setTableName("hl7TextMessage");
                rb.setKeyword(String.valueOf(lab_id));
                rb.setTableContent("<lab_id>" + htm.getId() + "</lab_id>" + "<message>" + htm.getBase64EncodedeMessage() + "</message>" + "<type>" + htm.getType() + "</type>" + "<fileUploadCheck_id>" + htm.getFileUploadCheckId() + "</fileUploadCheck_id>");
                recycleBinDao.persist(rb);

                hl7TextMessageDao.remove(lab_id);
            }


            for (ProviderLabRoutingModel plr : providerLabRoutingDao.findByLabNoIncludingPotentialDuplicates(lab_id)) {
                RecycleBin rb = new RecycleBin();
                rb.setProviderNo("0");
                rb.setUpdateDateTime(new Date());
                rb.setTableName("providerLabRouting");
                rb.setKeyword(String.valueOf(lab_id));
                rb.setTableContent("<provider_no>" + plr.getProviderNo() + "</provider_no>" + "<lab_no>" + plr.getLabNo() + "</lab_no>" + "<status>" + plr.getStatus() + "</status>" + "<comment>" + plr.getComment()
                        + "</comment>" + "<timestamp>" + plr.getTimestamp() + "</timestamp>" + "<lab_type>" + plr.getLabType() + "</lab_type>" + "<id>" + plr.getId() + "</id>");
                recycleBinDao.persist(rb);

                providerLabRoutingDao.remove(plr.getId());
            }

            PatientLabRouting lr = patientLabRoutingDao.findByLabNo(lab_id);
            if (lr != null) {
                RecycleBin rb = new RecycleBin();
                rb.setProviderNo("0");
                rb.setUpdateDateTime(new Date());
                rb.setTableName("patientLabRouting");
                rb.setKeyword(String.valueOf(lab_id));
                rb.setTableContent("<demographic_no>" + lr.getDemographicNo() + "</demographic_no>" + "<lab_no>" + lr.getLabNo() + "</lab_no>" + "<lab_type>" + lr.getLabType() + "</lab_type>" + "<id>" + lr.getId() + "</id>");
                recycleBinDao.persist(rb);

                patientLabRoutingDao.remove(lr.getId());
            }

            List<MeasurementsExt> measurementExts = measurementsExtDao.findByKeyValue("lab_no", String.valueOf(lab_id));
            for (MeasurementsExt me : measurementExts) {
                Measurement m = measurementDao.find(me.getMeasurementId());
                if (m != null) {
                    RecycleBin rb = new RecycleBin();
                    rb.setProviderNo("0");
                    rb.setUpdateDateTime(new Date());
                    rb.setTableName("measurements");
                    rb.setKeyword(String.valueOf(me.getMeasurementId()));
                    rb.setTableContent("<id>" + m.getId() + "</id>" + "<type>" + m.getType() + "</type>" + "<demographicNo>" + m.getDemographicId() + "</demographicNo>" + "<providerNo>" + m.getProviderNo() + "</providerNo>" + "<dataField>"
                            + m.getDataField() + "</dataField>" + "<measuringInstruction>" + m.getMeasuringInstruction() + "</measuringInstruction>" + "<comments>" + m.getComments() + "</comments>" + "<dateObserved>" + m.getDateObserved() + "</dateObserved>" + "<dateEntered>" + m.getCreateDate() + "</dateEntered>");
                    recycleBinDao.persist(rb);

                    measurementDao.remove(m.getId());
                }

                List<MeasurementsExt> mes = measurementsExtDao.getMeasurementsExtByMeasurementId(me.getMeasurementId());
                for (MeasurementsExt me1 : mes) {
                    RecycleBin rb = new RecycleBin();
                    rb.setProviderNo("0");
                    rb.setUpdateDateTime(new Date());
                    rb.setTableName("measurementsExt");
                    rb.setKeyword(String.valueOf(me.getMeasurementId()));
                    rb.setTableContent("<id>" + me1.getId() + "</id>" + "<measurement_id>" + me1.getMeasurementId() + "</measurement_id>" + "<keyval>" + me1.getKeyVal() + "</keyval>" + "<val>" + me1.getVal() + "</val>");
                    recycleBinDao.persist(rb);

                    measurementsExtDao.remove(me1.getId());
                }

            }


        }


        FileUploadCheck fuc = fileUploadCheckDao.find(fileId);
        if (fuc != null) {
            RecycleBin rb = new RecycleBin();
            rb.setProviderNo("0");
            rb.setUpdateDateTime(new Date());
            rb.setTableName("fileUploadCheck");
            rb.setKeyword(String.valueOf(fileId));
            rb.setTableContent("<id>" + fuc.getId() + "</id>" + "<provider_no>" + fuc.getProviderNo() + "</provider_no>" + "<filename>" + fuc.getFilename() + "</filename>" + "<md5sum>" + fuc.getMd5sum() + "</md5sum>" + "<datetime>" + fuc.getDateTime() + "</datetime>");
            recycleBinDao.persist(rb);

            fileUploadCheckDao.remove(fuc.getId());
        }
    }

    /**
     * Merge lab label arrays into one array.
     * String arrays are delineated with a pipe |
     */
    public static String mergeLabLabels(List<Hl7TextInfo> currentLabs, String incoming) {
        // If a past lab with the same AccessionNumber exist carry over the label
        String mergedLabel = StringUtils.trimToEmpty(incoming);
        if (currentLabs == null) {
            currentLabs = Collections.emptyList();
        }
        for (Hl7TextInfo matchingLab : currentLabs) {
            String currentLabel = matchingLab.getLabel();
            // if the lab has an entered label to carry over
            if (!StringUtils.isBlank(currentLabel) && !StringUtils.isBlank(mergedLabel)) {
                // compare labels and eliminate duplicates.
                String[] labelArray = mergedLabel.split("\\s?\\|\\s?");
                for (String labelItem : labelArray) {
                    if (!labelItem.isEmpty()) {
                        String regex = Pattern.quote(labelItem) + "\\s?\\|?\\s?";
                        currentLabel = currentLabel.replaceAll(regex, "");
                    }
                }
                currentLabel = StringUtils.trimToEmpty(currentLabel);

                if (!currentLabel.isEmpty()) {
                    mergedLabel = currentLabel + " | " + mergedLabel;
                }

                if (mergedLabel.startsWith("|")) {
                    mergedLabel = mergedLabel.substring(1);
                    mergedLabel = mergedLabel.trim();
                }
            }
        }
        return mergedLabel;
    }
}
