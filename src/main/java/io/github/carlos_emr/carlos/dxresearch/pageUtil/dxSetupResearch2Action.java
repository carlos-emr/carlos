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

import io.github.carlos_emr.carlos.utility.PatientRecordAccess;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.commn.dao.QuickListDao;
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

        String demographicNo = canonicalPatientNo(request.getParameter("demographicNo"));
        if (demographicNo == null) {
            return ERROR;
        }
        PatientRecordAccess.require(securityInfoManager, loggedInInfo, demographicNo, "_dxresearch", "r");
        PatientRecordAccess.require(securityInfoManager, loggedInInfo, demographicNo, "_demographic", "r");

        // Validate providerNo is numeric if supplied from the request
        String providerNo = request.getParameter("providerNo");
        if (providerNo != null && !providerNo.matches("\\d+")) {
            return ERROR;
        }

        // The selector carries a list name, not a numeric identifier. Resolve it
        // against stored names before loading items or recording the last-used list.
        String requestedQuickList = request.getParameter("quickList");
        String selectedQuickList = "";
        if (requestedQuickList != null && !requestedQuickList.isEmpty()) {
            selectedQuickList = SpringUtils.getBean(QuickListDao.class).findDistinct().stream()
                    .filter(String.class::isInstance).map(String.class::cast)
                    .filter(requestedQuickList::equals).findFirst().orElse(null);
            if (selectedQuickList == null) {
                return ERROR;
            }
        }

        dxResearchCodingSystem codingSys = new dxResearchCodingSystem();

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
        session.setAttribute("codingSystem", codingSys); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        session.setAttribute("allQuickLists", quicklistHd); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        session.setAttribute("allQuickListItems", quicklistItemsHd); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        // The patient and its diagnoses belong to this request. In the session they were the
        // registry opened last in any window, and dxResearch.jsp's ${demographicNo} also leaked
        // into every other page that resolves that name from session scope (issue #4673).
        publishPatient(request, demographicNo, providerNo);

        return SUCCESS;
    }

    /** Returns the canonical positive Integer patient key, or null for absent or invalid input. */
    static String canonicalPatientNo(String value) {
        if (value == null || !value.matches("[0-9]{1,10}")) return null;
        try {
            int patient = Integer.parseInt(value);
            return patient > 0 ? Integer.toString(patient) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Publishes the registry page's patient, its diagnosis list and the provider as request
     * attributes, which is where {@code dxResearch.jsp} and its quick-list include read them.
     *
     * @param request the request rendering the registry page
     * @param demographicNo the canonical patient number the page is for
     * @param providerNo the provider the page acts for
     */
    static void publishPatient(HttpServletRequest request, String demographicNo, String providerNo) {
        request.setAttribute("demographicNo", demographicNo);
        request.setAttribute("allDiagnostics", new dxResearchBeanHandler(demographicNo));
        request.setAttribute("providerNo", providerNo);
    }
}
