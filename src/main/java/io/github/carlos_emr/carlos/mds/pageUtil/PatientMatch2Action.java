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


package io.github.carlos_emr.carlos.mds.pageUtil;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;

import org.owasp.encoder.Encode;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Struts2 action that links a lab result to a demographic record and then
 * redirects the user to the patient's E-Chart view.
 *
 * <p>Invoked after {@code SearchPatient2Action} resolves (or the user manually
 * selects) a demographic match for an MDS lab. Updates the lab-to-demographic
 * routing via {@code CommonLabResultData.updatePatientLabRouting(...)} then
 * redirects to {@code /oscarMDS/ViewOpenEChart} (the {@code _lab r} gate
 * for {@code OpenEChart.jsp}) rather than self-redirecting.
 *
 * <p>Request parameters:
 * <ul>
 *   <li>{@code demographicNo} — target demographic id</li>
 *   <li>{@code labNo} — lab segment id being linked</li>
 *   <li>{@code labType} — lab result type (e.g. {@code HL7})</li>
 * </ul>
 *
 * <p>Security: requires {@code _lab} write privilege; throws {@code SecurityException}
 * on failure. All user-provided URL components are encoded via
 * {@link org.owasp.encoder.Encode#forUriComponent(String)}.
 *
 * @since 2004-02-04
 */
public class PatientMatch2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public PatientMatch2Action() {
    }

    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    public String execute()
            throws ServletException, IOException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_lab", "w", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }

        String demographicNo = request.getParameter("demographicNo");
        String labNo = request.getParameter("labNo");
        String labType = request.getParameter("labType");

        String newURL;

        try {
            CommonLabResultData.updatePatientLabRouting(labNo, demographicNo, labType);
            newURL = request.getContextPath() + "/oscarMDS/ViewOpenEChart"
                    + "?demographicNo=" + Encode.forUriComponent(demographicNo == null ? "" : demographicNo);
        } catch (Exception e) {
            MiscUtils.getLogger().error("exception in PatientMatch2Action", e);
            newURL = request.getContextPath() + "/errorpage";
        }

        response.sendRedirect(newURL);
        return NONE;
    }
}
