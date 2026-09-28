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

import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.documentManager.annotation.DocumentPatientLink;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code oscarMDS/Split.jsp}. Enforces {@code _lab}
 * {@code r} privilege before forwarding to the JSP at its
 * {@code /WEB-INF/jsp/oscarMDS/} location. Part of the oscarMDS (multi-
 * vendor lab inbox) security-hardening migration (2Action gate pattern
 * from #1109, #1629, #1632, #1644, #1662, #1663, #1665, #1666, #1667, #1668).
 *
 * <p>HTTP method is not enforced. Every patient linked to the document must be
 * accessible before the JSP reads its metadata or renders its page inventory.
 *
 * @since 2026-04-13
 */
public final class ViewSplit2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private CtlDocumentDao ctlDocumentDao = SpringUtils.getBean(CtlDocumentDao.class);

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", "r", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }

        HttpServletResponse response = ServletActionContext.getResponse();
        response.setHeader("Cache-Control", "no-store");
        int documentNo;
        try {
            documentNo = Integer.parseInt(request.getParameter("document"));
            if (documentNo <= 0) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
            }
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        try {
            DocumentPatientLink.requireAccess(loggedInInfo, documentNo, securityInfoManager, ctlDocumentDao);
        } catch (SecurityException e) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            throw e;
        }
        return SUCCESS;
    }
}
