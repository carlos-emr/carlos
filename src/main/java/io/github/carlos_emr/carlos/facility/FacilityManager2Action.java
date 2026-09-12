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


package io.github.carlos_emr.carlos.facility;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.FacilityDao;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SessionConstants;
import io.github.carlos_emr.carlos.utility.WebUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component(FacilityManager2Action.SPRING_BEAN_NAME)
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class FacilityManager2Action extends ActionSupport {
    public static final String SPRING_BEAN_NAME = "facilityManager2Action";

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private final transient FacilityDao facilityDao;
    private final transient SecurityInfoManager securityInfoManager;

    private static final String FORWARD_EDIT = "edit";
    private static final String FORWARD_LIST = "list";
    private static final String BEAN_FACILITIES = "facilities";
    private static final String FACILITY_ADMIN_SECURITY_OBJECT = "_admin.facility";
    private static final String WRITE_PRIVILEGE = "w";

    public FacilityManager2Action(FacilityDao facilityDao, SecurityInfoManager securityInfoManager) {
        this.facilityDao = facilityDao;
        this.securityInfoManager = securityInfoManager;
    }

    @Override
    public String execute() throws Exception {
        String method = request.getParameter("method");
        if (!securityInfoManager.hasPrivilege(
                LoggedInInfo.getLoggedInInfoFromSession(request),
                FACILITY_ADMIN_SECURITY_OBJECT,
                WRITE_PRIVILEGE,
                null)) {
            throw new SecurityException("missing required sec object (_admin.facility)");
        }
        if (isMutationMethod(method) && !"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        if ("edit".equals(method)) {
            return edit();
        } else if ("delete".equals(method)) {
            return delete();
        } else if ("add".equals(method)) {
            return add();
        } else if ("save".equals(method)) {
            return save();
        }
        return list();
    }

    private boolean isMutationMethod(String method) {
        return "delete".equals(method) || "save".equals(method) || "add".equals(method);
    }

    public String list() {
        List<Facility> facilities = facilityDao.findAll(true);
        request.setAttribute(BEAN_FACILITIES, facilities);

        return FORWARD_LIST;
    }

    public String edit() {
        String id = request.getParameter("id");
        Facility facility = facilityDao.find(Integer.valueOf(id));

        this.setFacility(facility);

        request.setAttribute("id", facility.getId());
        request.setAttribute("orgId", facility.getOrgId());
        request.setAttribute("sectorId", facility.getSectorId());

        return FORWARD_EDIT;
    }

    public String delete() {
        String id = request.getParameter("id");
        Facility facility = facilityDao.find(Integer.valueOf(id));
        facility.setDisabled(true);
        facilityDao.merge(facility);

        return list();
    }

    public String add() {
        Facility facility = new Facility("", "");
        this.setFacility(facility);

        return FORWARD_EDIT;
    }

    public String save() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);


        Facility facility = this.getFacility();

        if (request.getParameter("facility.hic") == null) facility.setHic(false);

        facility.setEnableHealthNumberRegistry(WebUtils.isChecked(request, "facility.enableHealthNumberRegistry"));
        facility.setEnableDigitalSignatures(WebUtils.isChecked(request, "facility.enableDigitalSignatures"));
        if (facility.getId() == null || facility.getId() == 0) facilityDao.persist(facility);
        else facilityDao.merge(facility);

        // if we just updated our current facility, refresh local cached data in the session / thread local variable
        if (loggedInInfo.getCurrentFacility().getId().intValue() == facility.getId().intValue()) {
            // nosemgrep: tainted-session-from-http-request -- facility fields from admin form, persisted/merged to DB above; execute() requires _admin.facility write
            request.getSession().setAttribute(SessionConstants.CURRENT_FACILITY, facility); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): admin-persisted facility entity (DAO-sourced); execute() requires _admin.facility write
            loggedInInfo.setCurrentFacility(facility);
        }
        addActionMessage(getText("facility.saved", facility.getName()));
        request.setAttribute("id", facility.getId());

        return list();
    }

    private Facility facility;

    @StrutsParameter(depth = 1)
    public Facility getFacility() {
        return facility;
    }

    @StrutsParameter
    public void setFacility(Facility facility) {
        this.facility = facility;
    }
}
