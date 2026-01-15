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
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.services.security.SecurityManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.openosp.openo.PMmodule.web.forms.IntegratorPushItem;
import ca.openosp.openo.PMmodule.web.forms.IntegratorPushResponse;
import ca.openosp.openo.commn.dao.UserPropertyDAO;
import ca.openosp.openo.commn.model.IntegratorProgress;
import ca.openosp.openo.commn.model.UserProperty;
import ca.openosp.openo.managers.IntegratorPushManager;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import org.springframework.beans.BeanUtils;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class IntegratorPush2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private UserPropertyDAO userPropertyDao = SpringUtils.getBean(UserPropertyDAO.class);
    private IntegratorPushManager integratorPushManager = SpringUtils.getBean(IntegratorPushManager.class);

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String execute() {
        if ("getPushData".equals(request.getParameter("method"))) {
            return getPushData();
        }
        if ("disableNextAndFuturePushes".equals(request.getParameter("method"))) {
            return disableNextAndFuturePushes();
        }
        return togglePause();
    }

    public String getPushData() {

        ObjectNode json = null;

        SecurityManager securityMgr = new SecurityManager();
        if (securityMgr.hasReadAccess("_admin", request.getSession().getAttribute("userrole") + "," + request.getSession().getAttribute("user"))) {
            List<IntegratorProgress> ipList = integratorPushManager.findAll();
            List<IntegratorPushItem> results = new ArrayList<IntegratorPushItem>();
            for (IntegratorProgress ip : ipList) {
                IntegratorPushItem item = new IntegratorPushItem();
                BeanUtils.copyProperties(ip, item);
                item.setTotalDemographics(integratorPushManager.getTotalItemsForProgress(ip.getId()));
                item.setTotalOutstanding(integratorPushManager.getTotalOutstandingItemsForProgress(ip.getId()));
                results.add(item);
            }

            IntegratorPushResponse ipResponse = new IntegratorPushResponse();
            ipResponse.setItems(results);
            ipResponse.setPaused(integratorPushManager.isPauseFlagSet());
            //jsonArray = objectMapper.valueToTree( results );
            json = objectMapper.valueToTree(ipResponse);

        }

        try {
            response.getWriter().write(json.toString());
        } catch (IOException e) {
            MiscUtils.getLogger().error("Couldn't return result", e);
        }

        return null;
    }

    /**
     * We basically just set a system property in the DB so that the next time the job runs, it either, does or not based on the property.
     *
     * @return JSON result string
     */
    public String disableNextAndFuturePushes() {

        ObjectNode json = objectMapper.createObjectNode();

        SecurityManager securityMgr = new SecurityManager();
        if (securityMgr.hasReadAccess("_admin", request.getSession().getAttribute("userrole") + "," + request.getSession().getAttribute("user"))) {

            String strType = request.getParameter("type");
            Boolean type = Boolean.valueOf(strType);

            UserProperty prop = userPropertyDao.getProp(IntegratorPushManager.DISABLE_INTEGRATOR_PUSH_PROP);
            if (prop == null) {
                prop = new UserProperty();
                prop.setName(IntegratorPushManager.DISABLE_INTEGRATOR_PUSH_PROP);
                prop.setProviderNo(null);
                prop.setValue(type.toString());
                userPropertyDao.persist(prop);
            } else {
                prop.setValue(type.toString());
                userPropertyDao.merge(prop);
            }
            json.put("success", true);
            json.put("DISABLE_INTEGRATOR_PUSH_PROP", prop.getValue());

        } else {
            json.put("success", false);
            json.put("reason", "Access Denied");
        }

        try {
            response.getWriter().write(json.toString());
        } catch (IOException e) {
            MiscUtils.getLogger().error("Couldn't return result", e);
        }

        return null;
    }

    public String togglePause() {
        ObjectNode json = objectMapper.createObjectNode();

        String in = request.getParameter("pause");
        boolean doPause = false;

        if (in != null) {
            doPause = Boolean.valueOf(in);

            SecurityManager securityMgr = new SecurityManager();
            if (securityMgr.hasReadAccess("_admin", request.getSession().getAttribute("userrole") + "," + request.getSession().getAttribute("user"))) {

                UserProperty prop = userPropertyDao.getProp(IntegratorPushManager.INTEGRATOR_PAUSE_FULL_PUSH);
                if (prop == null) {
                    prop = new UserProperty();
                    prop.setName(IntegratorPushManager.INTEGRATOR_PAUSE_FULL_PUSH);
                    prop.setProviderNo(null);
                    prop.setValue(Boolean.valueOf(doPause).toString());
                    userPropertyDao.persist(prop);
                } else {
                    prop.setValue(Boolean.valueOf(doPause).toString());
                    userPropertyDao.merge(prop);
                }
                json.put("success", true);
                json.put("INTEGRATOR_PAUSE_FULL_PUSH", prop.getValue());

            } else {
                json.put("success", false);
                json.put("reason", "Access Denied");
            }
        }

        try {
            response.getWriter().write(json.toString());
        } catch (IOException e) {
            MiscUtils.getLogger().error("Couldn't return result", e);
        }

        return null;
    }

}
