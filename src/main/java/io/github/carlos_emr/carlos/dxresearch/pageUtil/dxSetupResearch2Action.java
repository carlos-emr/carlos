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


package io.github.carlos_emr.carlos.dxresearch.pageUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.dxresearch.bean.dxQuickListBeanHandler;
import io.github.carlos_emr.carlos.dxresearch.bean.dxQuickListItemsHandler;
import io.github.carlos_emr.carlos.dxresearch.bean.dxResearchBeanHandler;
import io.github.carlos_emr.carlos.dxresearch.util.dxResearchCodingSystem;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public final class dxSetupResearch2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute()
            throws Exception {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_dxresearch", "r", null)) {
            throw new RuntimeException("missing required sec object (_dxresearch)");
        }

        // Validate demographicNo is a non-negative integer before crossing the trust boundary
        String demographicNoParam = request.getParameter("demographicNo");
        if (demographicNoParam == null || !demographicNoParam.matches("\\d+")) {
            return ERROR;
        }
        // Parse and re-stringify to produce a canonical integer string that breaks the CodeQL
        // taint chain: CodeQL does not treat regex matching alone as a sanitizer, but passing
        // through Integer.parseInt() confirms the value is a safe integer before session storage.
        String demographicNo = String.valueOf(Integer.parseInt(demographicNoParam));

        // Validate providerNo is numeric if supplied from the request
        String providerNo = request.getParameter("providerNo");
        if (providerNo != null && !providerNo.matches("\\d+")) {
            return ERROR;
        }

        // Validate quickList is numeric if supplied and non-empty
        String selectedQuickList = request.getParameter("quickList");
        if (selectedQuickList == null) {
            selectedQuickList = "";
        }
        if (!selectedQuickList.isEmpty() && !selectedQuickList.matches("\\d+")) {
            return ERROR;
        }

        dxResearchCodingSystem codingSys = new dxResearchCodingSystem();
        dxResearchBeanHandler hd = new dxResearchBeanHandler(demographicNo);

        dxQuickListBeanHandler quicklistHd;
        dxQuickListItemsHandler quicklistItemsHd;

        if (providerNo == null) {
            providerNo = loggedInInfo.getLoggedInProviderNo();
        }

        if (selectedQuickList.isEmpty()) {
            quicklistHd = new dxQuickListBeanHandler(providerNo);
            quicklistItemsHd = new dxQuickListItemsHandler(quicklistHd.getLastUsedQuickList(), providerNo);
        } else {
            quicklistItemsHd = new dxQuickListItemsHandler(selectedQuickList, providerNo);
            quicklistHd = new dxQuickListBeanHandler(providerNo);
        }

        HttpSession session = request.getSession();
        session.setAttribute("codingSystem", codingSys); // nosemgrep: tainted-session-from-http-request
        session.setAttribute("allQuickLists", quicklistHd); // nosemgrep: tainted-session-from-http-request
        session.setAttribute("allQuickListItems", quicklistItemsHd); // nosemgrep: tainted-session-from-http-request
        session.setAttribute("allDiagnostics", hd); // nosemgrep: tainted-session-from-http-request
        session.setAttribute("demographicNo", demographicNo); // nosemgrep: tainted-session-from-http-request
        session.setAttribute("providerNo", providerNo); // nosemgrep: tainted-session-from-http-request

        return SUCCESS;
    }
}
