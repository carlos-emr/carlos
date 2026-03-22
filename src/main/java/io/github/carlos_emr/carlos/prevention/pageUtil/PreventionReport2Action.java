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


/*
 * PreventionReport2Action.java
 *
 * Created on May 30, 2005, 7:52 PM
 */

package io.github.carlos_emr.carlos.prevention.pageUtil;


import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.prevention.reports.PreventionReport;
import io.github.carlos_emr.carlos.prevention.reports.PreventionReportFactory;
import io.github.carlos_emr.carlos.report.data.RptDemographicQueryBuilder;
import io.github.carlos_emr.carlos.report.data.RptDemographicQueryLoader;
import io.github.carlos_emr.carlos.report.pageUtil.RptDemographicReport2Form;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

/**
 * Struts2 action that generates prevention compliance reports for a patient set.
 *
 * <p>Loads a saved demographic query, builds the patient list, then delegates to
 * the appropriate {@link PreventionReport} implementation (PAP, Mammogram, Flu,
 * Child Immunizations, or FOBT) to calculate compliance statistics and return
 * per-patient report data. Results are stored as request attributes for JSP rendering.</p>
 *
 * <p>Requires the {@code _report} read privilege.</p>
 *
 * @since 2005-05-30
 * @see io.github.carlos_emr.carlos.prevention.reports.PreventionReport
 * @see io.github.carlos_emr.carlos.prevention.reports.PreventionReportFactory
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

public class PreventionReport2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger log = MiscUtils.getLogger();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /** Default no-argument constructor. */
    public PreventionReport2Action() {
    }

    /**
     * Executes the prevention compliance report for the configured patient set and prevention type.
     *
     * @return String "success" with report data stored in request attributes
     * @throws SecurityException if the logged-in user lacks {@code _report} read privilege
     */
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
        // Use the overload without asofRosterDate so results are based on the demographic query and
        // asofDate only. The overload that accepts an asofRosterDate may additionally apply a post-query
        // rostering filter (skipping non-rostered patients) when an as-of roster date is provided and at
        // least one provider filter is specified.
        ArrayList<ArrayList<String>> list = demoQ.buildQuery(loggedInInfo, frm);

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

    /**
     * Returns the saved patient set query name.
     *
     * @return String the patient set name
     */
    public String getPatientSet() {
        return patientSet;
    }

    /**
     * Sets the saved patient set query name.
     *
     * @param patientSet String the patient set name
     */
    @StrutsParameter
    public void setPatientSet(String patientSet) {
        this.patientSet = patientSet;
    }

    /**
     * Returns the prevention type name for the report.
     *
     * @return String the prevention type (e.g., "PAP", "Flu", "Mammogram")
     */
    public String getPrevention() {
        return prevention;
    }

    /**
     * Sets the prevention type name for the report.
     *
     * @param prevention String the prevention type
     */
    @StrutsParameter
    public void setPrevention(String prevention) {
        this.prevention = prevention;
    }

    /**
     * Returns the as-of date for report calculation.
     *
     * @return String the date in "yyyy-MM-dd" format
     */
    public String getAsofDate() {
        return asofDate;
    }

    /**
     * Sets the as-of date for report calculation.
     *
     * @param asofDate String the date in "yyyy-MM-dd" format
     */
    @StrutsParameter
    public void setAsofDate(String asofDate) {
        this.asofDate = asofDate;
    }
}
