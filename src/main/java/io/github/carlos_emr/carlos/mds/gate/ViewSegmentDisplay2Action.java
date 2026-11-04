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
package io.github.carlos_emr.carlos.mds.gate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.lab.service.LabPatientAccess;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.mds.data.MDSResultsData;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code oscarMDS/SegmentDisplay.jsp}. Enforces {@code _lab}
 * {@code r} privilege before forwarding to the JSP at its
 * {@code /WEB-INF/jsp/oscarMDS/} location. Part of the oscarMDS (multi-
 * vendor lab inbox) security-hardening migration (2Action gate pattern
 * from #1109, #1629, #1632, #1644, #1662, #1663, #1665, #1666, #1667, #1668).
 *
 * <p>HTTP method is not enforced. Global {@code _lab} read does not override a patient lock
 * ({@code _demographic$N} or {@code _eChart$N}): the report's actual MDS routing owner, and the
 * owner of every version the JSP reads beside it, must be open to the caller. A non-numeric
 * {@code segmentID} is a 400.
 *
 * @since 2026-04-14
 */
public final class ViewSegmentDisplay2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() {
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
        LabPatientAccess.requireLab(securityInfoManager, routings, loggedInInfo, labNo, "MDS");
        // SegmentDisplay.jsp loads every matching version and renders the acknowledgement comments
        // of the selected and older ones. Authorize the same server-resolved chain it uses
        // (CommonLabResultData.getMatchingLabs(segmentID, "MDS")) before forwarding.
        String versions = new MDSResultsData().getMatchingLabs(segment);
        if (!LabPatientAccess.requireOtherVersions(securityInfoManager, routings, loggedInInfo, labNo, versions, "MDS")) {
            ServletActionContext.getResponse().setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }

        return SUCCESS;
    }
}
