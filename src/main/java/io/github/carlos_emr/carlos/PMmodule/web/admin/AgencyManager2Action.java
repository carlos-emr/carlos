/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.PMmodule.web.admin;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.PMmodule.model.Agency;
import io.github.carlos_emr.carlos.PMmodule.service.AgencyManager;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.log.LogAction;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

public class AgencyManager2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final String FORWARD_EDIT = "edit";
    private static final String FORWARD_VIEW = "view";

    private static final String BEAN_AGENCY = "agency";

    private AgencyManager agencyManager = SpringUtils.getBean(AgencyManager.class);

    public String execute() {
        if ("edit".equals(request.getParameter("method"))) {
            return edit();
        }
        if ("save".equals(request.getParameter("method"))) {
            return save();
        }
        return view();
    }

    public String view() {

        request.setAttribute(BEAN_AGENCY, agencyManager.getLocalAgency());

        return FORWARD_VIEW;
    }

    public String edit() {

        Agency localAgency = agencyManager.getLocalAgency();

        this.setAgency(localAgency);

        request.setAttribute("id", localAgency.getId());

        return FORWARD_EDIT;
    }

    public String save() {


        agencyManager.saveAgency(agency);

        request.setAttribute("id", agency.getId());

        LogAction.log("write", "agency", agency.getId().toString(), request);

        return FORWARD_EDIT;
    }

    public void setAgencyManager(AgencyManager mgr) {
        this.agencyManager = mgr;
    }

    private Agency agency;

    @StrutsParameter(depth = 1)
    public Agency getAgency() {
        return agency;
    }

    @StrutsParameter
    public void setAgency(Agency agency) {
        this.agency = agency;
    }
}
