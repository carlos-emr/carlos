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


/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.prescript.util.RxDrugRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

public class RxUpdateDrugref2Action extends ActionSupport {
    private static final Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String execute() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "w", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }

        if ("updateDB".equals(request.getParameter("method"))) {
            return updateDB();
        } else if ("verify".equals(request.getParameter("method"))) {
            return verify();
        }
        return getLastUpdate();
    }

    public String updateDB() throws Exception, ServletException {
        HashMap<String, Object> d = new HashMap<String, Object>();
        try {
            RxDrugRef drugref = new RxDrugRef();
            String s = drugref.updateDB();
            d.put("result", s);
        } catch (Exception e) {
            // DrugRef service unavailable — log and return a JSON error payload so the
            // client can render a friendly "unavailable" message instead of a 500 page.
            logger.warn("DrugRef updateDB failed; treating service as unavailable", e);
            d.put("result", null);
        }
        response.setContentType("text/x-json;charset=UTF-8");

        ObjectNode jsonArray = (ObjectNode) objectMapper.valueToTree(d);
        response.getWriter().write(jsonArray.toString());
        return null;
    }

    private String verify() throws Exception, ServletException {
        Map<String, String> verify;
        try {
            RxDrugRef drugref = new RxDrugRef();
            verify = drugref.verify();
        } catch (Exception e) {
            // DrugRef service unavailable — log and return a JSON payload with null fields.
            // The existing clients (TopLinks2.jspf, updateDrugref.jsp) treat a null
            // lastUpdate as "DrugRef unavailable" and show a friendly banner instead
            // of a 500 error page in the Rx print-preview modal.
            logger.warn("DrugRef verify failed; treating service as unavailable", e);
            verify = new HashMap<>();
            verify.put("lastUpdate", null);
            verify.put("drugDatabase", null);
            verify.put("version", null);
        }
        response.setContentType("text/x-json;charset=UTF-8");
        ObjectNode jsonArray = (ObjectNode) objectMapper.valueToTree(verify);
        response.getWriter().write(jsonArray.toString());
        return null;
    }

    private String getLastUpdate() throws Exception, ServletException {
        HashMap<String, String> d = new HashMap<String, String>();
        try {
            RxDrugRef drugref = new RxDrugRef();
            String s = drugref.getLastUpdateTime();
            d.put("lastUpdate", s);
        } catch (Exception e) {
            // DrugRef service unavailable — return a null lastUpdate so clients can
            // render a friendly unavailable message rather than a 500 error page.
            logger.warn("DrugRef getLastUpdateTime failed; treating service as unavailable", e);
            d.put("lastUpdate", null);
        }
        response.setContentType("text/x-json;charset=UTF-8");
        ObjectNode jsonArray = (ObjectNode) objectMapper.valueToTree(d);
        response.getWriter().write(jsonArray.toString());
        return null;
    }
}
