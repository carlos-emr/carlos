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
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SessionConstants;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.WebUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * Struts2 action for managing healthcare facilities in the CARLOS EMR administration interface.
 *
 * <p>Provides CRUD operations for {@link Facility} entities including listing, creating,
 * editing, saving, and soft-deleting facilities. Uses method-based routing via the
 * {@code method} request parameter to dispatch to the appropriate handler.</p>
 *
 * @see Facility
 * @see FacilityDao
 * @since 2026-03-17
 */
public class FacilityManager2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private FacilityDao facilityDao = (FacilityDao) SpringUtils.getBean(FacilityDao.class);

    private static final String FORWARD_EDIT = "edit";
    private static final String FORWARD_LIST = "list";
    private static final String BEAN_FACILITIES = "facilities";

    /**
     * Routes the request to the appropriate handler method based on the {@code method} parameter.
     *
     * @return String the Struts2 result name for view resolution
     */
    @Override
    public String execute() {
        String method = request.getParameter("method");
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
     * Lists all active facilities and sets them as a request attribute.
     *
     * @return String the "list" result name for the facility listing view
     */
    public String list() {
        List<Facility> facilities = facilityDao.findAll(true);
        request.setAttribute(BEAN_FACILITIES, facilities);

        return FORWARD_LIST;
    }

    /**
     * Loads a facility by its ID for editing and sets it as the action's facility property.
     *
     * @return String the "edit" result name for the facility edit form view
     */
    public String edit() {
        String id = request.getParameter("id");
        Facility facility = facilityDao.find(Integer.valueOf(id));

        this.setFacility(facility);

        request.setAttribute("id", facility.getId());
        request.setAttribute("orgId", facility.getOrgId());
        request.setAttribute("sectorId", facility.getSectorId());

        return FORWARD_EDIT;
    }

    /**
     * Soft-deletes a facility by setting its disabled flag to {@code true}.
     *
     * @return String the result of {@link #list()} to redisplay the facility listing
     */
    public String delete() {
        String id = request.getParameter("id");
        Facility facility = facilityDao.find(Integer.valueOf(id));
        facility.setDisabled(true);
        facilityDao.merge(facility);

        return list();
    }

    /**
     * Initializes a new empty facility for creation and displays the edit form.
     *
     * @return String the "edit" result name for the new facility form view
     */
    public String add() {
        Facility facility = new Facility("", "");
        this.setFacility(facility);

        return FORWARD_EDIT;
    }

    /**
     * Persists or merges the facility entity and refreshes the session cache if the
     * saved facility is the currently active one.
     *
     * @return String the result of {@link #list()} to redisplay the facility listing
     */
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
            request.getSession().setAttribute(SessionConstants.CURRENT_FACILITY, facility);
            loggedInInfo.setCurrentFacility(facility);
        }
        addActionMessage(getText("facility.saved", facility.getName()));
        request.setAttribute("id", facility.getId());

        return list();
    }

    private Facility facility;

    /**
     * Returns the facility being edited or created.
     *
     * @return Facility the current facility entity
     */
    @StrutsParameter(depth = 1)
    public Facility getFacility() {
        return facility;
    }

    /**
     * Sets the facility entity, typically populated by Struts2 parameter injection.
     *
     * @param facility Facility the facility entity to set
     */
    @StrutsParameter
    public void setFacility(Facility facility) {
        this.facility = facility;
    }
}
