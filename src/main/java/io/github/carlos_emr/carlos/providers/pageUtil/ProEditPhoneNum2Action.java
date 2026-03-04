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


package io.github.carlos_emr.carlos.providers.pageUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;


import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

public class ProEditPhoneNum2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    public String execute() throws Exception {
        String providerNo = LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo();
        if (providerNo == null)
            return "eject";

        UserPropertyDAO propertyDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
        UserProperty prop = propertyDao.getProp(providerNo, "rxPhone");
        if (prop != null) {
            prop.setValue(faxNumber);
        } else {
            prop = new UserProperty();
            prop.setName("rxPhone");
            prop.setProviderNo(providerNo);
            prop.setValue(faxNumber);
        }
        propertyDao.saveProp(prop);
        request.setAttribute("status", "complete");
        return SUCCESS;
    }

    private String faxNumber;

    public String getFaxNumber() {
        return faxNumber;
    }

    @StrutsParameter
    public void setFaxNumber(String faxNumber) {
        this.faxNumber = faxNumber;
    }
}
