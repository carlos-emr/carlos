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


package ca.openosp.openo.encounter.pageUtil;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import javax.servlet.http.HttpServletRequest;

import ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil.EctViewConsultationRequestsUtil;
import ca.openosp.openo.commn.dao.UserPropertyDAO;
import ca.openosp.openo.commn.model.UserProperty;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import org.springframework.web.context.support.WebApplicationContextUtils;

import ca.openosp.openo.util.DateUtils;
import ca.openosp.openo.util.StringUtils;


/**
 * Retrieves consultation requests for demographic
 */

public class EctDisplayConsult2Action extends EctDisplayAction {
    private String cmd = "consultation";

    public boolean getInfo(EctSessionBean bean, HttpServletRequest request, NavBarDisplayDAO Dao) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String appointmentNo = bean.appointmentNo;

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_con", "r", null)) {
            return true; //Consultations link won't show up on new CME screen.
        } else {
            //set lefthand module heading and link
            String winName = "Consultation" + bean.demographicNo;
            String url = "popupPage(700,960,'" + winName + "','" + request.getContextPath() + "/oscarEncounter/oscarConsultationRequest/DisplayDemographicConsultationRequests.jsp?de=" + bean.demographicNo + "')";
            Dao.setLeftHeading(getText("oscarEncounter.LeftNavBar.Consult"));
            Dao.setLeftURL(url);

            //set the right hand heading link\
            winName = "newConsult" + bean.demographicNo;
            url = "popupPage(700,960,'" + winName + "','" + request.getContextPath() + "/oscarEncounter/oscarConsultationRequest/ConsultationFormRequest.jsp?de=" + bean.demographicNo + "&teamVar=&appNo=" + appointmentNo + "'); return false;";
            Dao.setRightURL(url);
            Dao.setRightHeadingID(cmd);  //no menu so set div id to unique id for this action 

            //grab all consultations for patient and add list item for each
            EctViewConsultationRequestsUtil theRequests;
            theRequests = new EctViewConsultationRequestsUtil();
            theRequests.estConsultationVecByDemographic(loggedInInfo, bean.demographicNo);

            //determine cut off period for highlighting
            UserPropertyDAO pref = (UserPropertyDAO) WebApplicationContextUtils.getWebApplicationContext(request.getSession().getServletContext()).getBean(UserPropertyDAO.class);

            UserProperty up = pref.getProp(bean.providerNo, UserProperty.CONSULTATION_TIME_PERIOD_WARNING);
            String timeperiod = null;

            if (up != null && up.getValue() != null && !up.getValue().trim().equals("")) {
                timeperiod = up.getValue();
            }

            Calendar cal = Calendar.getInstance();
            int countback = -1;
            if (timeperiod != null) {
                countback = Integer.parseInt(timeperiod);
                countback = countback * -1;
            }
            cal.add(Calendar.MONTH, countback);
            Date cutoffDate = cal.getTime();

            String red = "red";
            String dbFormat = "yyyy-MM-dd";
            String serviceDateStr;
            Date date;
            for (int idx = theRequests.ids.size() - 1; idx >= 0; --idx) {
                NavBarDisplayDAO.Item item = NavBarDisplayDAO.Item();

                String service = ""; 
                String specialist = "";
                String dateStr = "";
                String status = "";

                if (theRequests.service != null && !theRequests.service.isEmpty()) {
                    service = theRequests.service.get(idx);
                }
                if(theRequests.vSpecialist != null && !theRequests.vSpecialist.isEmpty()) {
                    specialist =  theRequests.vSpecialist.get(idx);
                }
                if(theRequests.date != null && !theRequests.date.isEmpty()) {
                    dateStr = theRequests.date.get(idx);
                }
                if (theRequests.status != null && !theRequests.status.isEmpty()) {
                    status = theRequests.status.get(idx);
                }

                DateFormat formatter = new SimpleDateFormat(dbFormat);
                try {
                    date = formatter.parse(dateStr);
                    serviceDateStr = DateUtils.formatDate(date, request.getLocale());
                    //if we are after cut off date and not completed set to red
                    if (date.before(cutoffDate) && !status.equals("4")) {
                        item.setColour(red);
                    }
                } catch (ParseException ex) {
                    MiscUtils.getLogger().debug("EctDisplayConsultationAction: Error creating date " + ex.getMessage());
                    serviceDateStr = "Error";
                    date = null;
                }
                url = "popupPage(700,960,'" + winName + "','" + request.getContextPath() + "/oscarEncounter/ViewRequest.do?de=" + bean.demographicNo + "&requestId=" + theRequests.ids.get(idx) + "'); return false;";
                
                item.setLinkTitle(service + "(" + specialist + ") " + serviceDateStr);
                service = StringUtils.maxLenString(service, MAX_LEN_TITLE, CROP_LEN_TITLE, ELLIPSES);
                item.setTitle(service);
                item.setURL(url);
                item.setDate(date);
                Dao.addItem(item);
            }

            return true;
        }
    }

    public String getCmd() {
        return cmd;
    }
}
