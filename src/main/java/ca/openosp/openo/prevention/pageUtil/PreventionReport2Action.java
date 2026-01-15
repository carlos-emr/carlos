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
 * PreventionReport2Action.java
 *
 * Created on May 30, 2005, 7:52 PM
 */

package ca.openosp.openo.prevention.pageUtil;


import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.prevention.reports.PreventionReport;
import ca.openosp.openo.prevention.reports.PreventionReportFactory;
import ca.openosp.openo.report.data.RptDemographicQueryBuilder;
import ca.openosp.openo.report.data.RptDemographicQueryLoader;
import ca.openosp.openo.report.pageUtil.RptDemographicReport2Form;
import ca.openosp.openo.util.UtilDateUtilities;

/**
 * @author Jay Gallagher
 */
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class PreventionReport2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger log = MiscUtils.getLogger();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public PreventionReport2Action() {
    }

    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_report", "r", null)) {
            throw new SecurityException("missing required sec object (_report)");
        }

        if (patientSet == null) patientSet = request.getParameter("patientSet");
        if (prevention == null) prevention = request.getParameter("prevention");
        if (asofDate == null) asofDate = request.getParameter("asofDate");
        Date asDate = UtilDateUtilities.getDateFromString(asofDate, "yyyy-MM-dd");

        RptDemographicReport2Form frm = new RptDemographicReport2Form();
        frm.setSavedQuery(patientSet);
        RptDemographicQueryLoader demoL = new RptDemographicQueryLoader();
        frm = demoL.queryLoader(frm);
        frm.addDemoIfNotPresent();
        frm.setAsofDate(asofDate);
        RptDemographicQueryBuilder demoQ = new RptDemographicQueryBuilder();
        ArrayList<ArrayList<String>> list = demoQ.buildQuery(loggedInInfo, frm, asofDate);

        log.debug("set size " + list.size());

        if (asDate == null) {
            Calendar today = Calendar.getInstance();
            asDate = today.getTime();
        }
        request.setAttribute("asDate", asDate);

        PreventionReport report = PreventionReportFactory.getPreventionReport(prevention);
        if (report == null) {
            return SUCCESS; // will stay on the same page if no report is found
        }

        if ("ChildImmunizations".equals(prevention)) {
            request.setAttribute("ReportType", prevention);
        }

        Hashtable h = report.runReport(loggedInInfo, list, asDate);
        request.setAttribute("up2date", h.get("up2date"));
        request.setAttribute("percent", h.get("percent"));
        request.setAttribute("percentWithGrace", h.get("percentWithGrace"));
        request.setAttribute("returnReport", h.get("returnReport"));
        request.setAttribute("inEligible", h.get("inEligible"));
        request.setAttribute("eformSearch", h.get("eformSearch"));
        request.setAttribute("followUpType", h.get("followUpType"));
        request.setAttribute("BillCode", h.get("BillCode"));

        request.setAttribute("prevType", prevention);
        request.setAttribute("patientSet", patientSet);
        request.setAttribute("prevention", prevention);

        log.debug("setting prevention type to " + prevention);

        return SUCCESS;
    }

    private String patientSet;
    private String prevention;
    private String asofDate;

    public String getPatientSet() {
        return patientSet;
    }

    public void setPatientSet(String patientSet) {
        this.patientSet = patientSet;
    }

    public String getPrevention() {
        return prevention;
    }

    public void setPrevention(String prevention) {
        this.prevention = prevention;
    }

    public String getAsofDate() {
        return asofDate;
    }

    public void setAsofDate(String asofDate) {
        this.asofDate = asofDate;
    }
}
