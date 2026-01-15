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


package ca.openosp.openo.provider.web;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import ca.openosp.openo.commn.dao.UserPropertyDAO;
import ca.openosp.openo.commn.model.UserProperty;
import ca.openosp.openo.olis.dao.OLISProviderPreferencesDao;
import ca.openosp.openo.olis.model.OLISProviderPreferences;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class OlisPreferences2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private UserPropertyDAO dao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
    private OLISProviderPreferencesDao olisProviderPreferencesDao = SpringUtils.getBean(OLISProviderPreferencesDao.class);

    @Override
    public String execute() {
        if ("save".equals(request.getParameter("method"))) {
            return save();
        }
        return view();
    }

    public String view() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        UserProperty prop = dao.getProp(providerNo, "olis_reportingLab");
        if (prop != null)
            request.setAttribute("reportingLaboratory", prop.getValue());

        prop = dao.getProp(providerNo, "olis_exreportingLab");
        if (prop != null)
            request.setAttribute("excludeReportingLaboratory", prop.getValue());

        prop = dao.getProp(providerNo, "olis_polling_frequency");
        if (prop != null)
            request.setAttribute("pollingFrequency", prop.getValue());

        OLISProviderPreferences olisPref = olisProviderPreferencesDao.findById(providerNo);
        if (olisPref != null) {
            request.setAttribute("olis_provider_start_time", olisPref.getStartTime());
        }

        return "form";
    }

    public String save() {
        String reportingLab = request.getParameter("reportingLaboratory");
        String excludeReportingLab = request.getParameter("excludeReportingLaboratory");
        String pollingFrequency = request.getParameter("pollingFrequency");
        String providerStartTime = request.getParameter("providerStartTime");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        if (reportingLab != null) {
            UserProperty prop = dao.getProp(providerNo, "olis_reportingLab");
            if (prop == null) {
                prop = new UserProperty();
                prop.setName("olis_reportingLab");
                prop.setProviderNo(providerNo);
            }
            prop.setValue(reportingLab);
            dao.saveProp(prop);
        }

        if (excludeReportingLab != null) {
            UserProperty prop = dao.getProp(providerNo, "olis_exreportingLab");
            if (prop == null) {
                prop = new UserProperty();
                prop.setName("olis_exreportingLab");
                prop.setProviderNo(providerNo);
            }
            prop.setValue(excludeReportingLab);
            dao.saveProp(prop);
        }

        if (pollingFrequency != null) {
            UserProperty prop = dao.getProp(providerNo, "olis_polling_frequency");
            if (prop == null) {
                prop = new UserProperty();
                prop.setName("olis_polling_frequency");
                prop.setProviderNo(providerNo);
            }
            prop.setValue(pollingFrequency);
            dao.saveProp(prop);
        }

        if (providerStartTime != null) {
            //validate the time
            DateTimeFormatter input = DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss Z");
            DateTimeFormatter output = DateTimeFormat.forPattern("YYYYMMddHHmmssZ");
            try {
                DateTime date = input.parseDateTime(providerStartTime);
                providerStartTime = date.toString(output);
                OLISProviderPreferences pref = olisProviderPreferencesDao.findById(providerNo);
                if (pref == null) {
                    pref = new OLISProviderPreferences();
                    pref.setProviderId(providerNo);
                    pref.setStartTime(providerStartTime);
                    olisProviderPreferencesDao.persist(pref);
                } else {
                    pref.setStartTime(providerStartTime);
                    olisProviderPreferencesDao.merge(pref);
                }

            } catch (RuntimeException e) {

            }

        }


        return view();
    }
}
