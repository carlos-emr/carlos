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
package ca.openosp.openo.commn.web;

import java.io.IOException;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import ca.openosp.openo.commn.dao.DemographicExtDao;
import ca.openosp.openo.commn.model.DemographicExt;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.log.LogAction;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.util.LabelValueBean;

public class DemographicExtService2Action extends ActionSupport {
    private HttpServletRequest request = ServletActionContext.getRequest();
    private HttpServletResponse response = ServletActionContext.getResponse();
    private DemographicExtDao demographicExtDao = SpringUtils.getBean(DemographicExtDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String execute() throws Exception {
        return saveNewValue();
    }

    public String saveNewValue() throws IOException {
        String demographicNo = request.getParameter("demographicNo");
        String key = request.getParameter("key");
        String value = request.getParameter("value");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "w", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }


        DemographicExt existing = demographicExtDao.getDemographicExt(Integer.parseInt(demographicNo), key);
        Integer id = null;
        if (existing == null) {
            DemographicExt d = new DemographicExt();
            d.setDateCreated(new Date());
            d.setDemographicNo(Integer.parseInt(demographicNo));
            d.setKey(key);
            d.setValue(value);
            d.setProviderNo(providerNo);

            demographicExtDao.persist(d);
            id = d.getId();
        } else {
            existing.setValue(value);
            demographicExtDao.merge(existing);
            id = existing.getId();
        }


        LogAction.addLog(providerNo, "write", id.toString(), value);

        String json = objectMapper.writeValueAsString(new LabelValueBean("id", String.valueOf(id)));
        response.getWriter().println(json);
        return null;
    }
}
