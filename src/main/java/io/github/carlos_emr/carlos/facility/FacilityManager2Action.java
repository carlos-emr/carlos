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

/**
 * Admin screen for the facility roster reached at {@code /FacilityManager}.
 *
 * <p>A single method-dispatch action with both read and write paths: {@code list}, {@code edit}
 * and {@code add} render pages, while {@code delete} and {@code save} persist. {@link #execute()}
 * therefore gates on {@code _admin} read rights for the renders and {@code _admin} write rights
 * for the two mutations, and rejects a non-POST mutation with 405 before any DAO call.
 *
 * <p>The action is a prototype-scoped Spring bean rather than a Struts-instantiated class so its
 * collaborators arrive by constructor injection; {@code struts-login.xml} maps the route to
 * {@link #SPRING_BEAN_NAME}.
 *
 * @since 2024-12-06
 */
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
    /**
     * Security object guarding the facility admin screens. {@code _admin} is the object that is
     * actually seeded (see the {@code secObjPrivilege} seed in the Flyway {@code V1.0.2} data
     * migrations) and it is what {@code admin/facility/ListFacilities.jsp} and
     * {@code admin/facility/EditFacility.jsp} already gate on, so the action check lines up with
     * the pages it renders. The sibling
     * {@link io.github.carlos_emr.carlos.PMmodule.web.admin.FacilityManager2Action} guards the
     * same domain with the same object.
     */
    private static final String ADMIN_SECURITY_OBJECT = "_admin";
    private static final String READ_PRIVILEGE = "r";
    private static final String WRITE_PRIVILEGE = "w";

    public FacilityManager2Action(FacilityDao facilityDao, SecurityInfoManager securityInfoManager) {
        this.facilityDao = facilityDao;
        this.securityInfoManager = securityInfoManager;
    }

    @Override
    public String execute() throws Exception {
        String method = request.getParameter("method");
        boolean mutatingMethod = isMutationMethod(method);
        String privilege = mutatingMethod ? WRITE_PRIVILEGE : READ_PRIVILEGE;

        if (!securityInfoManager.hasPrivilege(
                LoggedInInfo.getLoggedInInfoFromSession(request),
                ADMIN_SECURITY_OBJECT,
                privilege,
                null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }
        if (mutatingMethod && !"POST".equals(request.getMethod())) {
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

    /**
     * Methods that change persisted state and must therefore arrive by POST.
     *
     * <p>{@code add} is deliberately absent: it only instantiates a transient {@link Facility} and
     * renders the edit form, so it stays reachable by GET. The global
     * {@link io.github.carlos_emr.carlos.app.HttpMethodGuardFilter} excludes {@code method=add}
     * for the same reason and names this action while doing so; the persist happens on the
     * subsequent {@code method=save} POST.
     */
    private boolean isMutationMethod(String method) {
        return "delete".equals(method) || "save".equals(method);
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
            // nosemgrep: tainted-session-from-http-request -- facility fields from admin form, persisted/merged to DB above; execute() requires _admin write
            request.getSession().setAttribute(SessionConstants.CURRENT_FACILITY, facility); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): admin-persisted facility entity (DAO-sourced); execute() requires _admin write
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
