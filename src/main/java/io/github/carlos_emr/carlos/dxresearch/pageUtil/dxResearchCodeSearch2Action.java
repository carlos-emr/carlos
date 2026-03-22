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

import io.github.carlos_emr.carlos.dxresearch.bean.dxCodeSearchBeanHandler;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action that performs a diagnosis code search and stores results in the session.
 *
 * <p>Accepts up to five diagnosis code keywords and a coding system type, searches
 * for matching codes, and stores the results in the HTTP session for the search
 * results JSP. Requires {@code _dxresearch} read privilege.</p>
 *
 * @since 2026-03-17
 */
public final class dxResearchCodeSearch2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Searches for diagnosis codes matching the submitted keywords and stores results in session.
     *
     * @return String "success" on successful search
     * @throws Exception if an error occurs during the search
     */
    public String execute()
            throws Exception {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_dxresearch", "r", null)) {
            throw new RuntimeException("missing required sec object (_dxresearch)");
        }

        String[] xml_research = new String[5];
        xml_research[0] = request.getParameter("xml_research1");
        xml_research[1] = request.getParameter("xml_research2");
        xml_research[2] = request.getParameter("xml_research3");
        xml_research[3] = request.getParameter("xml_research4");
        xml_research[4] = request.getParameter("xml_research5");
        String codeType = request.getParameter("codeType");

        dxCodeSearchBeanHandler hd = new dxCodeSearchBeanHandler(codeType, xml_research);
        HttpSession session = request.getSession();
        session.setAttribute("allMatchedCodes", hd);
        session.setAttribute("codeType", codeType);

        return SUCCESS;
    }
}
