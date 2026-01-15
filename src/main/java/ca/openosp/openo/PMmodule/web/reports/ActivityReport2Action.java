//CHECKSTYLE:OFF
/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 */

package ca.openosp.openo.PMmodule.web.reports;

import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.model.AdmissionSearchBean;
import ca.openosp.openo.PMmodule.model.ClientReferral;
import ca.openosp.openo.PMmodule.model.Program;
import ca.openosp.openo.PMmodule.service.AdmissionManager;
import ca.openosp.openo.PMmodule.service.ClientManager;
import ca.openosp.openo.PMmodule.service.ProgramManager;
import ca.openosp.openo.PMmodule.web.formbean.ActivityReportFormBean;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class ActivityReport2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger log = MiscUtils.getLogger();
    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

    private ProgramManager programManager = SpringUtils.getBean(ProgramManager.class);
    private AdmissionManager admissionManager = SpringUtils.getBean(AdmissionManager.class);
    private ClientManager clientManager = SpringUtils.getBean(ClientManager.class);

    public String execute() {
        if ("generate".equals(request.getParameter("method"))) {
            return generate();
        }
        return form();
    }

    public String form() {
        request.setAttribute("programs", programManager.getPrograms());

        return "form";
    }

    public String generate() {
        ActivityReportFormBean formBean = this.getForm();

        //# of new admissions
        AdmissionSearchBean searchBean = new AdmissionSearchBean();
        searchBean.setProgramId(formBean.getProgramId());
        try {
            searchBean.setStartDate(formatter.parse(formBean.getStartDate()));
            searchBean.setEndDate(formatter.parse(formBean.getEndDate()));
        } catch (Exception e) {
            log.error("Error", e);
        }

        Map summaryMap = new LinkedHashMap();

        List programs = programManager.getPrograms();
        for (Iterator iter = programs.iterator(); iter.hasNext(); ) {
            Program p = (Program) iter.next();

            //Don't report inactive program
            if (!p.isActive()) {
                continue;
            }
            searchBean.setProgramId(p.getId());
            List admissions = admissionManager.search(searchBean);
            int totalAdmissions = admissions.size();

            ClientReferral cr = new ClientReferral();
            cr.setProgramId(Long.valueOf(p.getId().longValue()));
            List referrals = clientManager.searchReferrals(cr);
            int totalReferrals = referrals.size();

            Long[] values = {Long.valueOf(totalAdmissions), Long.valueOf(totalReferrals)};
            summaryMap.put(p.getName(), values);
        }
        request.setAttribute("summary", summaryMap);

        return "report";
    }

    private ActivityReportFormBean form;

    public ActivityReportFormBean getForm() {
        return form;
    }

    public void setForm(ActivityReportFormBean form) {
        this.form = form;
    }
}
