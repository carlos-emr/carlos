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

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.ConsultationServiceDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationServices;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

public class EctConDeleteServices2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private ConsultationServiceDao consultationServiceDao = (ConsultationServiceDao) SpringUtils.getBean(ConsultationServiceDao.class);
    private static SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute()
            throws ServletException, IOException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_con", "u", null)) {
            throw new SecurityException("missing required sec object (_con)");
        }

        String servs[] = this.getService();
        if (servs.length > 0) {

            for (String serv : this.getService()) {
                ConsultationServices cs = consultationServiceDao.find(Integer.valueOf(serv));
                if (cs != null) {
                    cs.setActive(consultationServiceDao.INACTIVE);
                    consultationServiceDao.merge(cs);
                }
            }
        }
        EctConConstructSpecialistsScriptsFile constructSpecialistsScriptsFile = new EctConConstructSpecialistsScriptsFile();
        constructSpecialistsScriptsFile.makeString(request.getLocale());
        return SUCCESS;
    }
    public String[] getService() {
        if (service == null)
            service = new String[0];
        return service;
    }

    @StrutsParameter
    public void setService(String str[]) {
        service = str;
    }

    String service[];
}
