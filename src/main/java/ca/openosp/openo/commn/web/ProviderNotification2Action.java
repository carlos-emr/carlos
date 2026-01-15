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

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.utility.MiscUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.UserDSMessagePrefsDao;
import ca.openosp.openo.commn.model.UserDSMessagePrefs;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

/**
 * Creates a providers notification record.
 */
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class ProviderNotification2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static Logger logger = MiscUtils.getLogger();

    private UserDSMessagePrefsDao userDsMessagePrefsDao = SpringUtils.getBean(UserDSMessagePrefsDao.class);

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String execute() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String resourceId = request.getParameter("id");
        String resourceType = request.getParameter("type");

        if (logger.isDebugEnabled()) {
            logger.debug("Processing notification");
        }

        List<UserDSMessagePrefs> prefs = userDsMessagePrefsDao.findMessages(providerNo, resourceType, resourceId, false);
        for (UserDSMessagePrefs p : prefs) {
            p.setArchived(true);
            userDsMessagePrefsDao.merge(p);
        }

        UserDSMessagePrefs pref = new UserDSMessagePrefs();
        pref.setProviderNo(providerNo);
        pref.setResourceId(resourceId);
        pref.setResourceType(resourceType);
        pref.setArchived(false);
        pref.setResourceUpdatedDate(new Date());

        userDsMessagePrefsDao.persist(pref);

        ObjectNode json = objectMapper.createObjectNode();
        json.put("id", pref.getId());
        json.put("status", "success");

        Writer writer = null;
        try {
            writer = new OutputStreamWriter(response.getOutputStream());
            writer.write(objectMapper.writeValueAsString(json));
            writer.flush();
        } finally {
            writer.close();
        }

        return null;
    }


}
