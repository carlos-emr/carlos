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


package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.lab.ca.all.Hl7textResultsData;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.util.DateUtils;
import io.github.carlos_emr.carlos.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Legacy lab display action for the encounter left navbar.
 * Delegates to {@link EctDisplayLabAction22Action} for the actual implementation.
 *
 * @since 2001-01-01
 */
public class EctDisplayLabAction2 extends EctDisplayAction {
    private static final Logger logger = MiscUtils.getLogger();
    private static final String cmd = "labs";

    public boolean getInfo(EctSessionBean bean, HttpServletRequest request, NavBarDisplayDAO Dao) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        logger.debug("EctDisplayLabAction2");
        OscarLogDao oscarLogDao = (OscarLogDao) SpringUtils.getBean(OscarLogDao.class);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", "r", null)) {
            return true; // Lab result link won't show up on new CME screen.
        } else {

            CommonLabResultData comLab = new CommonLabResultData();
            ArrayList<LabResultData> labs = comLab.populateLabResultsData(loggedInInfo, "", bean.demographicNo, "", "", "", "U");
            logger.debug("local labs found : " + labs.size());

            // set text for lefthand module title
            Dao.setLeftHeading(getText("oscarEncounter.LeftNavBar.Labs"));

            // set link for lefthand module title
            String winName = "Labs" + bean.demographicNo;
            String url = "popupPage(700,599,'" + winName + "','" + request.getContextPath() + "/lab/DemographicLab.jsp?demographicNo=" + bean.demographicNo + "'); return false;";
            Dao.setLeftURL(url);

            // we're going to display popup menu of 2 selections - row display and grid display
            String menuId = "2";
            Dao.setRightHeadingID(menuId);
            Dao.setRightURL("return !showMenu('" + menuId + "', event);");
            Dao.setMenuHeader(getText("oscarEncounter.LeftNavBar.LabMenuHeading"));

            winName = "AllLabs" + bean.demographicNo;

            if (CarlosProperties.getInstance().getBooleanProperty("HL7TEXT_LABS", "yes")) {
                url = "popupPage(700,1000, '" + winName + "','" + request.getContextPath() + "/lab/CumulativeLabValues3.jsp?demographic_no=" + bean.demographicNo + "')";
                Dao.addPopUpUrl(url);
                Dao.addPopUpText(getText("oscarEncounter.LeftNavBar.LabMenuItem1"));
                if (CarlosProperties.getInstance().getProperty("labs.hide_old_grid_display", "false").equals("false")) {
                    url = "popupPage(700,1000, '" + winName + "','" + request.getContextPath() + "/lab/CumulativeLabValues2.jsp?demographic_no=" + bean.demographicNo + "')";
                    Dao.addPopUpUrl(url);
                    Dao.addPopUpText(getText("oscarEncounter.LeftNavBar.LabMenuItem1") + "-OLD");
                }
            } else {
                url = "popupPage(700,1000, '" + winName + "','" + request.getContextPath() + "/lab/CumulativeLabValues2.jsp?demographic_no=" + bean.demographicNo + "')";
                Dao.addPopUpUrl(url);
                Dao.addPopUpText(getText("oscarEncounter.LeftNavBar.LabMenuItem1"));
            }
            url = "popupPage(700,1000, '" + winName + "','" + request.getContextPath() + "/lab/CumulativeLabValues.jsp?demographic_no=" + bean.demographicNo + "')";
            Dao.addPopUpUrl(url);
            Dao.addPopUpText(getText("oscarEncounter.LeftNavBar.LabMenuItem2"));

            // now we add individual module items
            LabResultData result;
            String labDisplayName, label;
            // String bgcolour = "FFFFCC";
            StringBuilder func;
            int hash;

            // Comment out this code and instead using the correct method to
            // get latest lab versions, which is the getLatestLabVersions() function below.
            // LinkedHashMap<String,LabResultData> accessionMap = new LinkedHashMap<String,LabResultData>();
            // for (int i = 0; i < labs.size(); i++) {
            // 	result = labs.get(i);
            // 	if (result.accessionNumber == null || result.accessionNumber.equals("")) {
            // 		accessionMap.put("noAccessionNum" + i + result.labType, result);
            // 	} else {
            // 		if (!accessionMap.containsKey(result.accessionNumber + result.labType)) accessionMap.put(result.accessionNumber + result.labType, result);
            // 	}
            // }
            // labs = new ArrayList<LabResultData>(accessionMap.values());

            //First, getting the latest versions of the lab results and then sorting them ensures
            //that they will be displayed in the correct date order in the encounter window.
            labs = getLatestLabVersions(labs);
            Collections.sort(labs);

            for (int j = 0; j < labs.size(); j++) {
                result = labs.get(j);
                Date date = getServiceDate(loggedInInfo, result);
                String formattedDate = "";
                if (date != null) {
                    DateUtils.getDate(date, "dd-MMM-yyyy", request.getLocale());
                }
                // String formattedDate = DateUtils.getDate(date);
                func = new StringBuilder("popupPage(700,960,'");
                label = result.getLabel();

                if (result.isMDS()) {
                    if (label == null || label.equals("")) labDisplayName = result.getDiscipline();
                    else labDisplayName = label;
                    url = request.getContextPath() + "/oscarMDS/SegmentDisplay.jsp?demographicId=" + bean.demographicNo + "&providerNo=" + bean.providerNo + "&segmentID=" + result.segmentID + "&multiID=" + result.multiLabId + "&status=" + result.getReportStatus();
                } else if (result.isCML()) {
                    if (label == null || label.equals("")) labDisplayName = result.getDiscipline();
                    else labDisplayName = label;
                    url = request.getContextPath() + "/lab/CA/ON/CMLDisplay.jsp?demographicId=" + bean.demographicNo + "&providerNo=" + bean.providerNo + "&segmentID=" + result.segmentID + "&multiID=" + result.multiLabId;
                } else if (result.isHL7TEXT()) {
                    if (label == null || label.equals("")) {
                        labDisplayName = result.getDiscipline();
                    } else {
                        labDisplayName = label;
                    }
                    url = request.getContextPath() + "/lab/CA/ALL/labDisplay.jsp?demographicId=" + bean.demographicNo + "&providerNo=" + bean.providerNo + "&segmentID=" + result.segmentID + "&multiID=" + result.multiLabId;
                } else {
                    if (label == null || label.equals("")) {
                        labDisplayName = result.getDiscipline();
                    } else {
                        labDisplayName = label;
                    }
                    url = request.getContextPath() + "/lab/CA/BC/labDisplay.jsp?demographicId=" + bean.demographicNo + "&segmentID=" + result.segmentID + "&providerNo=" + bean.providerNo + "&multiID=" + result.multiLabId;
                }
                String labRead = "";
                if (!oscarLogDao.hasRead(((String) request.getSession().getAttribute("user")), "lab", result.segmentID)) {
                    labRead = "*";
                }

                NavBarDisplayDAO.Item item = NavBarDisplayDAO.Item();

                item.setLinkTitle(labDisplayName + " " + formattedDate);
                labDisplayName = StringUtils.maxLenString(labDisplayName, MAX_LEN_TITLE, CROP_LEN_TITLE, ELLIPSES); // +" "+formattedDate;
                if (labDisplayName == null) {
                    labDisplayName = "";
                }
                hash = winName.hashCode();
                hash = hash < 0 ? hash * -1 : hash;
                func.append(hash + "','" + url + "'); return false;");

                item.setTitle(labRead + labDisplayName + labRead);
                item.setURL(func.toString());
                item.setDate(date);
                if (result.isAbnormal()) {
                    item.setColour("red");
                }


                // item.setBgColour(bgcolour);
                Dao.addItem(item);
            }

            return true;
        }
    }

    private ArrayList<LabResultData> getLatestLabVersions(ArrayList<LabResultData> labs) {
        List<String> allLabIds = new ArrayList<>();
        ArrayList<LabResultData> latestLabVersions = new ArrayList<>();
        for (LabResultData lab : labs) {
            if (allLabIds.contains(lab.getSegmentID())) {
                continue;
            }

            String[] allLabVersionIdsOfLab = Hl7textResultsData.getMatchingLabs(lab.getSegmentID()).split(",");
            allLabIds.addAll(Arrays.asList(allLabVersionIdsOfLab));

            for (LabResultData labResultData : labs) {
                if (allLabVersionIdsOfLab[allLabVersionIdsOfLab.length - 1].equals(labResultData.getSegmentID())) {
                    latestLabVersions.add(labResultData);
                    break;
                }
            }
        }
        return latestLabVersions;
    }

    public Date getServiceDate(LoggedInInfo loggedInInfo, LabResultData labData) {
        ServiceDateLoader loader = new ServiceDateLoader(labData);
        Date resultDate = loader.determineResultDate(loggedInInfo);
        if (resultDate != null) {
            return resultDate;
        }
        return labData.getDateObj();
    }

    public String getCmd() {
        return cmd;
    }

    /**
     * Attempts to determine service date for any given lab.
     */
    public static class ServiceDateLoader {

        private LabResultData labData;

        public ServiceDateLoader(LabResultData labData) {
            this.labData = labData;
        }

        /**
         * Attempts to determine service date for the aggregated
         * lab.
         *
         * @return Returns the service date or null if date can
         * not be determined
         */
        public Date determineResultDate(LoggedInInfo loggedInInfo) {
            String serviceDate = getServiceDate(loggedInInfo);
            if (serviceDate == null) {
                return null;
            }
            return parseServiceDate(serviceDate);
        }

        private Date parseServiceDate(String serviceDate) {
            Date result = null;
            String dateFormat[] = new String[]{"yyyy-MM-dd", "dd-MMM-yyyy"};
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat();

            for (int idx = 0; idx < dateFormat.length; ++idx) {
                try {

                    simpleDateFormat.applyPattern(dateFormat[idx]);
                    result = simpleDateFormat.parse(serviceDate);
                } catch (ParseException e) {

                }
            }
            return result;
        }

        private String getServiceDate(LoggedInInfo loggedInInfo) {
            String segmentId = labData.getSegmentID();
            MessageHandler handler = null;

            try {
                handler = getLocalHandler(segmentId);
            } catch (Exception e) {
                logger.error("Unable to get handler for " + labData, e);
            }

            if (handler == null) {
                return null;
            }

            String serviceDate = handler.getServiceDate();
            return serviceDate;
        }

        public MessageHandler getLocalHandler(String segmentId) {
            return Factory.getHandler(segmentId);
        }
    }

}
