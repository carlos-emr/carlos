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


package ca.openosp.openo.eform;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.OscarProperties;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class EFormAttachDocs2Action
        extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute()

            throws ServletException, IOException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_eform", "u", null)) {
            throw new SecurityException("missing required sec object (_eform)");
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String provNo = providerNo;

        if (StringUtils.isEmpty(requestId)) {
            return SUCCESS;
        }
        if (!OscarProperties.getInstance().isPropertyActive("consultation_indivica_attachment_enabled")) {
            String[] arrDocs = attachedDocs;

            EFormAttachDocs Doc = new EFormAttachDocs(provNo, demoNo, requestId, arrDocs);
            Doc.attach(loggedInInfo);

            EFormAttachLabs Lab = new EFormAttachLabs(provNo, demoNo, requestId, arrDocs);
            Lab.attach(loggedInInfo);

            EFormAttachHRMReports hrmReports = new EFormAttachHRMReports(provNo, demoNo, requestId, arrDocs);
            hrmReports.attach();

            EFormAttachEForms eForms = new EFormAttachEForms(provNo, demoNo, requestId, arrDocs);
            eForms.attach(loggedInInfo);
            return SUCCESS;
        } else {
            String[] labs = request.getParameterValues("labNo");
            String[] docs = request.getParameterValues("docNo");
            String[] hrmReportIds = request.getParameterValues("hrmNo");
            String[] eFormIds = request.getParameterValues("eFormNo");

            if (labs == null) {
                labs = new String[]{};
            }
            if (docs == null) {
                docs = new String[]{};
            }
            if (hrmReportIds == null) {
                hrmReportIds = new String[]{};
            }
            if (eFormIds == null) {
                eFormIds = new String[]{};
            }

            EFormAttachDocs Doc = new EFormAttachDocs(provNo, demoNo, requestId, docs);
            Doc.attach(loggedInInfo);

            EFormAttachLabs Lab = new EFormAttachLabs(provNo, demoNo, requestId, labs);
            Lab.attach(loggedInInfo);
            EFormAttachHRMReports hrmReports = new EFormAttachHRMReports(provNo, demoNo, requestId, hrmReportIds);
            hrmReports.attach();
            EFormAttachEForms eForms = new EFormAttachEForms(provNo, demoNo, requestId, eFormIds);
            eForms.attach(loggedInInfo);
            return SUCCESS;
        }
    }

    private String requestId;
    private String demoNo;
    private String providerNo;
    private String[] attachedDocs;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getDemoNo() {
        return demoNo;
    }

    public void setDemoNo(String demoNo) {
        this.demoNo = demoNo;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public String[] getAttachedDocs() {
        return attachedDocs;
    }

    public void setAttachedDocs(String[] attachedDocs) {
        this.attachedDocs = attachedDocs;
    }
}
