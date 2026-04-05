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


package io.github.carlos_emr.carlos.report.ClinicalReports.PageUtil;

import java.util.ArrayList;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class RemoveClinicalReportFromHistory2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    public String execute() {

        // Handle "clear all" request
        String clear = request.getParameter("clear");
        if ("yes".equals(clear)) {
            request.getSession().removeAttribute("ClinicalReports");
            return SUCCESS;
        }

        String id = request.getParameter("id");
        int nid = -1;
        try {
            nid = Integer.parseInt(id);
        } catch (Exception e) {
        }

        //Could be a concurrency issue here if they opened up more than one report screen
        ArrayList<Integer> arrList = (ArrayList<Integer>) request.getSession().getAttribute("ClinicalReports");
        if (arrList != null && nid != -1) {
            arrList.remove(Integer.parseInt(id));
        }
        if (arrList != null && arrList.size() == 0) {
            request.getSession().removeAttribute("ClinicalReports");
        }
        //request.getSession().setAttribute("ClinicalReports",arrList);

        return SUCCESS;
    }
}
