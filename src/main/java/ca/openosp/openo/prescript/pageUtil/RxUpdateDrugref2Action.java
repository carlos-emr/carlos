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


/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.openosp.openo.prescript.pageUtil;

import ca.openosp.openo.prescript.util.RxDrugRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

public class RxUpdateDrugref2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String execute() throws Exception {
        if ("updateDB".equals(request.getParameter("method"))) {
            return updateDB();
        } else if ("verify".equals(request.getParameter("method"))) {
            return verify();
        }
        return getLastUpdate();
    }

    public String updateDB() throws Exception, ServletException {
        RxDrugRef drugref = new RxDrugRef();
        String s = drugref.updateDB();

        HashMap<String, Object> d = new HashMap<String, Object>();
        d.put("result", s);
        response.setContentType("text/x-json;charset=UTF-8");

        ObjectNode jsonArray = (ObjectNode) objectMapper.valueToTree(d);
        response.getWriter().write(jsonArray.toString());
        return null;
    }

    private String verify() throws Exception, ServletException {
        RxDrugRef drugref = new RxDrugRef();
        Map<String,String> verify = drugref.verify();
        response.setContentType("text/x-json;charset=UTF-8");
        ObjectNode jsonArray = (ObjectNode) objectMapper.valueToTree(verify);
        response.getWriter().write(jsonArray.toString());
        return null;
    }

    private String getLastUpdate() throws Exception, ServletException {
        RxDrugRef drugref = new RxDrugRef();
        String s = drugref.getLastUpdateTime();
        HashMap<String, String> d = new HashMap<String, String>();
        d.put("lastUpdate", s);
        response.setContentType("text/x-json;charset=UTF-8");
        ObjectNode jsonArray = (ObjectNode) objectMapper.valueToTree(d);
        response.getWriter().write(jsonArray.toString());
        return null;
    }
}
