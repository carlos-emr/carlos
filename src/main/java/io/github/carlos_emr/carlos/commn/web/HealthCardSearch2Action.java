/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.commn.web;

import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.JsDateSerializer;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class HealthCardSearch2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        SimpleModule module = new SimpleModule();
        module.addSerializer(java.sql.Date.class, new JsDateSerializer());
        objectMapper.registerModule(module);
    }

    public String execute() throws Exception {
        String hin = request.getParameter("hin");
        String ver = request.getParameter("ver");
        String issueDate = request.getParameter("issueDate");
        String hinExp = request.getParameter("hinExp");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "r", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
        List<Demographic> matches = demographicDao.getDemographicsByHealthNum(hin);

        HashMap<String, Object> hashMap = new HashMap<String, Object>();

        if (matches != null) {
            if (matches.size() != 1) {
                hashMap.put("match", false);
            } else {
                hashMap.put("match", true);
                Demographic d = matches.get(0);
                hashMap.put("demoNo", d.getDemographicNo());
                hashMap.put("lastName", d.getLastName());
                hashMap.put("firstName", d.getFirstName());
                hashMap.put("hin", d.getHin());
                hashMap.put("hinVer", d.getVer());
                hashMap.put("phone", d.getPhone());

                String address = "";
                if (d.getAddress() != null && d.getAddress().trim().length() > 0)
                    address += d.getAddress().trim() + "\n";
                if (d.getCity() != null && d.getCity().trim().length() > 0)
                    address += d.getCity().trim();
                if (d.getProvince() != null && d.getProvince().trim().length() > 0)
                    address += (d.getCity() != null && d.getCity().trim().length() > 0 ? ", " : "") + d.getProvince().trim();

                hashMap.put("address", address);
            }
        }

        String json = objectMapper.writeValueAsString(hashMap);
        response.getOutputStream().write(json.getBytes());


        return null;

    }
}
