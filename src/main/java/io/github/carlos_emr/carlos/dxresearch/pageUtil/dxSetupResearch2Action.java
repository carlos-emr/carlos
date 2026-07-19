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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.dxresearch.bean.dxQuickListBeanHandler;
import io.github.carlos_emr.carlos.dxresearch.bean.dxQuickListItemsHandler;
import io.github.carlos_emr.carlos.dxresearch.bean.dxResearchBeanHandler;
import io.github.carlos_emr.carlos.dxresearch.util.dxResearchCodingSystem;

import com.opensymphony.xwork2.ActionSupport;
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

        dxResearchCodingSystem codingSys = new dxResearchCodingSystem();
        String demographicNo = request.getParameter("demographicNo");
        String providerNo = request.getParameter("providerNo");
        String selectedQuickList = request.getParameter("quickList");
        dxResearchBeanHandler hd = new dxResearchBeanHandler(demographicNo);

        dxQuickListBeanHandler quicklistHd = null;
        dxQuickListItemsHandler quicklistItemsHd = null;

        if (providerNo == null) {
            providerNo = loggedInInfo.getLoggedInProviderNo();
        }
        if (selectedQuickList == null) {
            selectedQuickList = "";
        }
        if (selectedQuickList.equals("")) {
            quicklistHd = new dxQuickListBeanHandler(providerNo);
            quicklistItemsHd = new dxQuickListItemsHandler(quicklistHd.getLastUsedQuickList(), providerNo);
        } else {
            quicklistItemsHd = new dxQuickListItemsHandler(selectedQuickList, providerNo);
            quicklistHd = new dxQuickListBeanHandler(providerNo);
        }

        HttpSession session = request.getSession();
        session.setAttribute("codingSystem", codingSys);
        session.setAttribute("allQuickLists", quicklistHd);
        session.setAttribute("allQuickListItems", quicklistItemsHd);
        session.setAttribute("allDiagnostics", hd);
        session.setAttribute("demographicNo", demographicNo);
        session.setAttribute("providerNo", providerNo);

        return SUCCESS;
    }
}
