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


package ca.openosp.openo.encounter.oscarConsultationRequest.config.pageUtil;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.commn.dao.ConsultationServiceDao;
import ca.openosp.openo.commn.dao.PropertyDao;
import ca.openosp.openo.commn.model.ConsultationServices;
import ca.openosp.openo.commn.model.Property;
import ca.openosp.openo.managers.ConsultationManager;
import ca.openosp.openo.managers.ConsultationManagerImpl;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class EctConEnableReqResp2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute()
            throws ServletException, IOException {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_con", "u", null)) {
            throw new SecurityException("missing required sec object (_con)");
        }

        PropertyDao propertyDao = (PropertyDao) SpringUtils.getBean(PropertyDao.class);
        ConsultationServiceDao serviceDao = (ConsultationServiceDao) SpringUtils.getBean(ConsultationServiceDao.class);

        ConsultationManager manager = new ConsultationManagerImpl();

        Property consultRequestEnabled = new Property(manager.CON_REQUEST_ENABLED);
        Property consultResponseEnabled = new Property(manager.CON_RESPONSE_ENABLED);

        List<Property> results = propertyDao.findByName(manager.CON_REQUEST_ENABLED);
        if (results.size() > 0) consultRequestEnabled = results.get(0);
        results = propertyDao.findByName(manager.CON_RESPONSE_ENABLED);
        if (results.size() > 0) consultResponseEnabled = results.get(0);

        consultRequestEnabled.setValue(this.isConsultRequestEnabled() ? manager.ENABLED_YES : null);
        consultResponseEnabled.setValue(this.isConsultResponseEnabled() ? manager.ENABLED_YES : null);

        propertyDao.merge(consultRequestEnabled);
        propertyDao.merge(consultResponseEnabled);

        ConsultationServices referringDocService = serviceDao.findReferringDoctorService(serviceDao.WITH_INACTIVE);
        if (referringDocService == null) referringDocService = new ConsultationServices(serviceDao.REFERRING_DOCTOR);
        if (this.isConsultResponseEnabled()) referringDocService.setActive(serviceDao.ACTIVE);
        else referringDocService.setActive(serviceDao.INACTIVE);

        serviceDao.merge(referringDocService);

        request.setAttribute("ENABLE_REQUEST_RESPONSE_UPDATED", "updated");
        return SUCCESS;
    }

    boolean consultRequestEnabled = false;
    boolean consultResponseEnabled = false;

    public boolean isConsultRequestEnabled() {
        return consultRequestEnabled;
    }

    public void setConsultRequestEnabled(boolean consultRequestEnabled) {
        this.consultRequestEnabled = consultRequestEnabled;
    }

    public boolean isConsultResponseEnabled() {
        return consultResponseEnabled;
    }

    public void setConsultResponseEnabled(boolean consultResponseEnabled) {
        this.consultResponseEnabled = consultResponseEnabled;
    }
}
