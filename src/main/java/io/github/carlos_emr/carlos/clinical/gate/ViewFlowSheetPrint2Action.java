/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.gate;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/** Read-only print selection and POST preview; does not persist clinical data. */
public final class ViewFlowSheetPrint2Action extends ActionSupport {
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        String method = request.getMethod();
        if (!"GET".equals(method) && !"HEAD".equals(method) && !"POST".equals(method)) {
            response.setHeader("Allow", "GET, HEAD, POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null || !securityInfoManager.hasPrivilege(loggedInInfo, "_eChart", "r", null)) {
            throw new SecurityException("missing required sec object (_eChart)");
        }
        String demographicNo = request.getParameter("demographic_no");
        int patient;
        try {
            if (demographicNo == null || !demographicNo.matches("[1-9][0-9]*")) {
                throw new NumberFormatException("invalid patient number");
            }
            patient = Integer.parseInt(demographicNo);
        } catch (NumberFormatException invalidPatient) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        if (!securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, patient)
                || !securityInfoManager.hasPrivilege(loggedInInfo, "_eChart", "r", demographicNo)) {
            throw new SecurityException("access to patient record denied");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_flowsheet", "r", demographicNo)) {
            throw new SecurityException("missing required sec object (_flowsheet)");
        }
        return SUCCESS;
    }
}
