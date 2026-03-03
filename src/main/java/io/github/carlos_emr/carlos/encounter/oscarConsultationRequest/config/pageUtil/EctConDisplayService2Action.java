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


package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.ServiceSpecialistsDao;
import io.github.carlos_emr.carlos.commn.model.ServiceSpecialists;
import io.github.carlos_emr.carlos.commn.model.ServiceSpecialistsPK;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

public class EctConDisplayService2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private ServiceSpecialistsDao dao = SpringUtils.getBean(ServiceSpecialistsDao.class);
    private static SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute()
            throws ServletException, IOException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_con", "r", null)) {
            throw new SecurityException("missing required sec object (_con)");
        }

        String serviceId = this.getServiceId();
        String specialists[] = this.getSpecialists();
        MiscUtils.getLogger().debug("service id ".concat(String.valueOf(String.valueOf(serviceId))));
        MiscUtils.getLogger().debug("num specs".concat(String.valueOf(specialists.length)));

        for (ServiceSpecialists s : dao.findByServiceId(Integer.parseInt(serviceId))) {
            dao.remove(s.getId());
        }
        for (String specialist : specialists) {
            ServiceSpecialists ss = new ServiceSpecialists();
            ss.setId(new ServiceSpecialistsPK(Integer.parseInt(serviceId), Integer.parseInt(specialist)));
            dao.persist(ss);
        }

        EctConConstructSpecialistsScriptsFile constructSpecialistsScriptsFile = new EctConConstructSpecialistsScriptsFile();
        constructSpecialistsScriptsFile.makeString(request.getLocale());
        return SUCCESS;
    }

    public String getServiceId() {
        if (serviceId == null)
            serviceId = new String();
        return serviceId;
    }

    @StrutsParameter
    public void setServiceId(String str) {
        serviceId = str;
    }

    public String[] getSpecialists() {
        if (specialists == null)
            specialists = new String[0];
        return specialists;
    }

    @StrutsParameter
    public void setSpecialists(String str[]) {
        specialists = str;
    }

    String serviceId;
    String specialists[];
}
