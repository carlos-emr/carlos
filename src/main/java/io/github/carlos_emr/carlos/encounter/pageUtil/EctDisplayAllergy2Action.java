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

import java.util.Date;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.provider.web.CppPreferencesUIBean;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import io.github.carlos_emr.carlos.prescript.data.RxPatientData;
import io.github.carlos_emr.carlos.util.DateUtils;

/**
 * retrieves info to display Disease entries for demographic
 */

public class EctDisplayAllergy2Action extends EctDisplayAction {

    private String cmd = "allergies";

    public boolean getInfo(EctSessionBean bean, HttpServletRequest request, NavBarDisplayDAO Dao) {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_allergy", "r", null)) {
            return true; // Allergies link won't show up on new CME screen.
        } else {

            // set lefthand module heading and link
            String winName = "Allergy" + bean.demographicNo;
            String url = "popupPage(580,900,'" + winName + "','" + request.getContextPath() + "/oscarRx/showAllergy.do?demographicNo=" + bean.demographicNo + "')";
            Dao.setLeftHeading(getText("oscarEncounter.NavBar.Allergy"));
            Dao.setLeftURL(url);

            // set righthand link to same as left so we have visual consistency with other modules
            url += "; return false;";
            Dao.setRightURL(url);
            Dao.setRightHeadingID(cmd); // no menu so set div id to unique id for this action

            // grab all of the diseases associated with patient and add a list item for each

            Allergy[] allergies;

            Integer demographicId = Integer.parseInt(bean.demographicNo);
            Locale locale = request.getLocale();

            allergies = RxPatientData.getPatient(loggedInInfo, demographicId).getActiveAllergies();

            CppPreferencesUIBean prefsBean = new CppPreferencesUIBean(loggedInInfo.getLoggedInProviderNo());
            prefsBean.loadValues();

            // --- get local allergies ---
            for (int idx = 0; idx < allergies.length; ++idx) {
                Date date = allergies[idx].getEntryDate();
                Date startDate = allergies[idx].getStartDate();
                String severity = allergies[idx].getSeverityOfReactionDesc();

                NavBarDisplayDAO.Item item = makeItem(date, allergies[idx].getDescription(), allergies[idx].getSeverityOfReaction(), locale, prefsBean, startDate, severity);
                Dao.addItem(item);
            }

            // --- sort all results ---
            Dao.sortItems(NavBarDisplayDAO.DATESORT_ASC);

            return true;
        }
    }

    private static NavBarDisplayDAO.Item makeItem(Date entryDate, String description, String severity, Locale locale, CppPreferencesUIBean prefsBean, Date startDate, String severityDescription) {
        NavBarDisplayDAO.Item item = NavBarDisplayDAO.Item();
        if (severity != null && severity.equals("3")) {
            item.setColour("red");
        } else if (severity != null && severity.equals("2")) {
            item.setColour("orange");
        }

        String customDescription = description;
        if (prefsBean != null && "on".equals(prefsBean.getAllergyStartDate())) {
            customDescription = customDescription + " Start Date:" + DateUtils.formatDate(startDate, locale);
        }
        if (prefsBean != null && "on".equals(prefsBean.getAllergySeverity())) {
            customDescription = customDescription + " Severity:" + severityDescription;
        }
        item.setTitle(customDescription);
        item.setLinkTitle(customDescription);
        item.setURL("return false;");

        return (item);
    }

    public String getCmd() {
        return cmd;
    }
}
