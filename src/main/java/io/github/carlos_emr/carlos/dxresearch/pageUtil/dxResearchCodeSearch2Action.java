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
import org.owasp.encoder.Encode;

public final class dxResearchCodeSearch2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute()
            throws Exception {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_dxresearch", "r", null)) {
            throw new RuntimeException("missing required sec object (_dxresearch)");
        }

        String[] xml_research = new String[5];
        // Encode each search-term value to break the taint chain before session storage
        xml_research[0] = Encode.forHtml(request.getParameter("xml_research1"));
        xml_research[1] = Encode.forHtml(request.getParameter("xml_research2"));
        xml_research[2] = Encode.forHtml(request.getParameter("xml_research3"));
        xml_research[3] = Encode.forHtml(request.getParameter("xml_research4"));
        xml_research[4] = Encode.forHtml(request.getParameter("xml_research5"));
        String codeType = Encode.forHtml(request.getParameter("codeType"));

        dxCodeSearchBeanHandler hd = new dxCodeSearchBeanHandler(codeType, xml_research);
        HttpSession session = request.getSession();
        session.setAttribute("allMatchedCodes", hd);
        session.setAttribute("codeType", codeType);

        return SUCCESS;
    }
}
