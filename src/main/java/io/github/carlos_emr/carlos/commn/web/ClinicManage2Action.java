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


package io.github.carlos_emr.carlos.commn.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

public class ClinicManage2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private ClinicDAO clinicDAO = SpringUtils.getBean(ClinicDAO.class);

    @Override
    public String execute() throws Exception {
        if ("update".equals(request.getParameter("method"))) {
            return update();
        }
        return view();
    }

    public String view() {
        Clinic clinic = clinicDAO.getClinic();

        this.setClinic(clinic);
        request.setAttribute("clinicForm", clinic);
        return SUCCESS;
    }

    public String update() {
        Clinic clinic = this.getClinic();
        if (request.getParameter("clinic.id") != null && request.getParameter("clinic.id").length() > 0 && clinic.getId() == null) {
            clinic.setId(Integer.parseInt(request.getParameter("clinic.id")));
        }
        clinicDAO.save(clinic);
        request.setAttribute("clinicForm", clinic);

        return SUCCESS;
    }

    private Clinic clinic;

    @StrutsParameter(depth = 1)
    public Clinic getClinic() {
        return clinic;
    }

    @StrutsParameter
    public void setClinic(Clinic clinic) {
        this.clinic = clinic;
    }
}
