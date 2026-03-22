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


import java.text.SimpleDateFormat;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.StringUtils;

/**
 * Displays appointment history entries for a patient in the encounter left navbar.
 *
 * @since 2001-01-01
 */
public class EctDisplayAppointmentHistory2Action extends EctDisplayAction {
    private static final String cmd = "appointmentHistory";


    public boolean getInfo(EctSessionBean bean, HttpServletRequest request, NavBarDisplayDAO Dao) {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_appointment", "r", null)) {
            throw new SecurityException("missing required sec object (_appointment)");
        }

        try {

            String cpp = request.getParameter("cpp");
            if (cpp == null) {
                cpp = new String();
            }

            //Set lefthand module heading and link
            String winName = "AppointmentHistory" + bean.demographicNo;
            String pathview, pathedit;

            pathview = request.getContextPath() + "/demographic/demographiccontrol.jsp?demographic_no=" + bean.demographicNo + "&orderby=appttime&displaymode=appt_history&dboperation=appt_history&limit1=0&limit2=25";

            String url = "popupPage(500,900,'" + winName + "','" + pathview + "')";
            Dao.setLeftHeading(getText("global.viewAppointmentHistory"));
            Dao.setLeftURL(url);

            //set right hand heading link
            Dao.setRightURL("return false;");
            Dao.setRightHeadingID(cmd); //no menu so set div id to unique id for this action


            ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);


            OscarAppointmentDao appointmentDao = (OscarAppointmentDao) SpringUtils.getBean(OscarAppointmentDao.class);
            List<Appointment> appts = appointmentDao.getAppointmentHistory(Integer.parseInt(bean.getDemographicNo()));

            int limit = 5;
            int index = 0;
            for (Appointment sh : appts) {
                if (index >= limit) break;
                NavBarDisplayDAO.Item item = NavBarDisplayDAO.Item();
                //item.setDate(sh.getAppointmentDate());
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

                //Demographic d = demographicDao.getClientByDemographicNo(sh.getDemographicNo());
                //Provider p = d.getProvider();
                Provider p = providerDao.getProvider(sh.getProviderNo());

                String title = formatter.format(sh.getAppointmentDate());
                title += " " + p.getTeam() + " " + sh.getReason();

                String itemHeader = StringUtils.maxLenString(title, MAX_LEN_TITLE, CROP_LEN_TITLE, ELLIPSES);
                item.setLinkTitle(itemHeader);
                item.setTitle(itemHeader);
                item.setURL("return false;");
                Dao.addItem(item);
                index++;
            }


            Dao.sortItems(NavBarDisplayDAO.DATESORT);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            return false;
        }
        return true;
        //}
    }

    public String getCmd() {
        return cmd;
    }
}
