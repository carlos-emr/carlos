//CHECKSTYLE:OFF
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
 */


package ca.openosp.openo.facility;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.commn.dao.FacilityDao;
import ca.openosp.openo.commn.dao.IntegratorControlDao;
import ca.openosp.openo.commn.model.Facility;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SessionConstants;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.utility.WebUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class FacilityManager2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private FacilityDao facilityDao = (FacilityDao) SpringUtils.getBean(FacilityDao.class);
    private IntegratorControlDao integratorControlDao = (IntegratorControlDao) SpringUtils.getBean(IntegratorControlDao.class);

    private static final String FORWARD_EDIT = "edit";
    private static final String FORWARD_LIST = "list";
    private static final String BEAN_FACILITIES = "facilities";

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

        boolean removeDemoId = integratorControlDao.readRemoveDemographicIdentity(Integer.valueOf(id));
        this.setRemoveDemographicIdentity(removeDemoId);

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

        this.setRemoveDemographicIdentity(true);
        // Ronnie ((FacilityManagerForm) form).setUpdateInterval(0);

        return FORWARD_EDIT;
    }

    public String save() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);


        Facility facility = this.getFacility();

        boolean rdid = WebUtils.isChecked(request, "removeDemographicIdentity");
        if (request.getParameter("facility.hic") == null) facility.setHic(false);

        facility.setIntegratorEnabled(WebUtils.isChecked(request, "facility.integratorEnabled"));
        facility.setAllowSims(WebUtils.isChecked(request, "facility.allowSims"));
        facility.setEnableIntegratedReferrals(WebUtils.isChecked(request, "facility.enableIntegratedReferrals"));
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

        integratorControlDao.saveRemoveDemographicIdentity(facility.getId(), rdid);

        return list();
    }

    private Facility facility;
    private boolean removeDemographicIdentity;

    public Facility getFacility() {
        return facility;
    }

    public void setFacility(Facility facility) {
        this.facility = facility;
    }

    public boolean isRemoveDemographicIdentity() {
        return removeDemographicIdentity;
    }

    public void setRemoveDemographicIdentity(boolean removeDemographicIdentity) {
        this.removeDemographicIdentity = removeDemographicIdentity;
    }
}
