/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.lab.gate;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.LinkedHashSet;
import java.util.Set;
import io.github.carlos_emr.carlos.lab.ca.all.Hl7textResultsData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.lab.service.LabPatientAccess;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Read gate for HL7 {@code lab/CA/ALL/labDisplay.jsp}. Enforces {@code _lab r}
 * so read-only providers (circle of care) can view HL7 lab results without
 * being routed through the write-gated {@code createLabLabel} action used
 * for label mutations.
 *
 * @since 2026-04-14
 */
public final class ViewLabDisplay2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    // FindSecBugs IMPROPER_UNICODE: "null".equalsIgnoreCase(all) matches the page's legacy literal sentinel, not an authorization decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive match of the page's legacy 'null' sentinel; not a security or authorization decision")
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", "r", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }

        String segment = request.getParameter("segmentID");
        Integer labNo = LabPatientAccess.positiveIdentity(segment);
        if (labNo == null) {
            ServletActionContext.getResponse().setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        PatientLabRoutingDao routings = SpringUtils.getBean(PatientLabRoutingDao.class);
        LabPatientAccess.requireLab(securityInfoManager, routings, loggedInInfo, labNo, "HL7");
        // The JSP can render supplied versions, the latest version, or comparison data from
        // matching versions. Authorize those actual lab identities before forwarding to it.
        String all = request.getParameter("all");
        String versions = all != null && !"null".equalsIgnoreCase(all)
                ? request.getParameter("multiID") : Hl7textResultsData.getMatchingLabs(segment);
        if (versions == null || versions.isEmpty()) {
            ServletActionContext.getResponse().setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        Set<Integer> checked = new LinkedHashSet<>();
        checked.add(labNo);
        for (String version : versions.split(",", -1)) {
            Integer versionNo = LabPatientAccess.positiveIdentity(version);
            if (versionNo == null) {
                ServletActionContext.getResponse().setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
            }
            if (checked.add(versionNo)) {
                LabPatientAccess.requireLab(securityInfoManager, routings, loggedInInfo, versionNo, "HL7");
            }
        }

        return SUCCESS;
    }
}
