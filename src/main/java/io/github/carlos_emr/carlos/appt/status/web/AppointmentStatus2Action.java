/**
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.appt.status.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import io.github.carlos_emr.carlos.appt.status.service.AppointmentStatusMgr;
import io.github.carlos_emr.carlos.appt.status.service.impl.AppointmentStatusMgrImpl;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

public class AppointmentStatus2Action extends ActionSupport {
    private static final String EDIT = "edit";
    private static final int DESCRIPTION_MAX_LENGTH = 30;
    private static final String HEX_COLOR_PATTERN = "^#[0-9A-Fa-f]{6}$";
    private static final String DEFAULT_COLOR = "#FFFFFF";

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final Logger logger = MiscUtils.getLogger();

    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "w", null)) {
            throw new SecurityException("missing required sec object (_appointment)");
        }

        String method = request.getParameter("dispatch");
        if (isMutation(method) && !"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        if ("view".equals(method)) {
            return view();
        } else if ("reset".equals(method)) {
            return reset();
        } else if ("changestatus".equals(method)) {
            return changestatus();
        } else if ("modify".equals(method)) {
            return modify();
        } else if ("update".equals(method)) {
            return update();
        }
        return view();
    }

    public String view() {
        logger.warn("view");
        populateAllStatus(request);
        return SUCCESS;
    }

    public String reset() {
        logger.warn("reset");
        AppointmentStatusMgr apptStatusMgr = getApptStatusMgr();
        apptStatusMgr.reset();
        addActionMessage(getText("admin.appt.status.mgr.message.reset"));
        populateAllStatus(request);
        return SUCCESS;
    }

    @SuppressWarnings("java:S3516") // Struts actions must return a configured result name on every branch.
    public String changestatus() {
        logger.warn("changestatus");
        AppointmentStatusMgr apptStatusMgr = getApptStatusMgr();
        AppointmentStatus appointmentStatus = getExistingStatus(apptStatusMgr);
        if (appointmentStatus == null || (active == null || (active != 0 && active != 1))) {
            if (appointmentStatus != null) {
                addActionError(getText("admin.appt.status.mgr.error.invalidActive"));
            }
            populateAllStatus(request);
            return SUCCESS;
        }

        apptStatusMgr.changeStatus(id, active);
        addActionMessage(getText("admin.appt.status.mgr.message.activationUpdated"));
        populateAllStatus(request);
        return SUCCESS;
    }

    public String modify() {
        logger.warn("modify");
        AppointmentStatusMgr apptStatusMgr = getApptStatusMgr();
        AppointmentStatus appt = getExistingStatus(apptStatusMgr);
        if (appt == null) {
            populateAllStatus(request);
            return SUCCESS;
        }

        populateEditFields(appt, true);

        return EDIT;
    }

    public String update() {
        logger.warn("update");
        AppointmentStatusMgr apptStatusMgr = getApptStatusMgr();
        AppointmentStatus appointmentStatus = getExistingStatus(apptStatusMgr);
        if (appointmentStatus == null) {
            populateAllStatus(request);
            return SUCCESS;
        }

        validateUpdate();
        if (hasActionErrors()) {
            populateEditFields(appointmentStatus, false);
            return EDIT;
        }

        apptStatusMgr.modifyStatus(id, apptDesc.trim(), apptColor);
        addActionMessage(getText("admin.appt.status.mgr.message.updated"));
        populateAllStatus(request);
        return SUCCESS;
    }

    private boolean isMutation(String dispatch) {
        return "reset".equals(dispatch) || "changestatus".equals(dispatch) || "update".equals(dispatch);
    }

    private AppointmentStatus getExistingStatus(AppointmentStatusMgr apptStatusMgr) {
        if (id == null || id <= 0) {
            addActionError(getText("admin.appt.status.mgr.error.invalidId"));
            return null;
        }

        AppointmentStatus appointmentStatus = apptStatusMgr.getStatus(id);
        if (appointmentStatus == null) {
            addActionError(getText("admin.appt.status.mgr.error.notFound"));
        }
        return appointmentStatus;
    }

    private void validateUpdate() {
        if (apptDesc == null || apptDesc.trim().isEmpty()) {
            addActionError(getText("admin.appt.status.mgr.error.descriptionRequired"));
        } else if (apptDesc.trim().length() > DESCRIPTION_MAX_LENGTH) {
            addActionError(getText("admin.appt.status.mgr.error.descriptionLength"));
        }

        if (!isValidColor(apptColor)) {
            addActionError(getText("admin.appt.status.mgr.error.color"));
        }
    }

    private boolean isValidColor(String color) {
        return color != null && color.matches(HEX_COLOR_PATTERN);
    }

    private void populateEditFields(AppointmentStatus appointmentStatus, boolean includeEditableFields) {
        id = appointmentStatus.getId();
        apptStatus = appointmentStatus.getStatus();
        apptOldColor = appointmentStatus.getColor();
        if (includeEditableFields) {
            apptDesc = appointmentStatus.getDescription();
            apptColor = isValidColor(appointmentStatus.getColor())
                    ? appointmentStatus.getColor() : DEFAULT_COLOR;
        }
    }

    public WebApplicationContext getApptContext() {
        return WebApplicationContextUtils.getRequiredWebApplicationContext(ServletActionContext.getServletContext());
    }

    public AppointmentStatusMgr getApptStatusMgr() {
        return new AppointmentStatusMgrImpl();
    }

    private void populateAllStatus(HttpServletRequest request) {
        AppointmentStatusMgr apptStatusMgr = getApptStatusMgr();
        List allStatus = apptStatusMgr.getAllStatus();
        request.setAttribute("allStatus", allStatus);
        int iUseStatus = apptStatusMgr.checkStatusUsuage(allStatus);
        if (iUseStatus > 0) {
            request.setAttribute("useStatus", apptStatusMgr.getStatus(iUseStatus + 1).getStatus());
        }
    }

    private Integer id;
    private Integer active;
    private String apptStatus;
    private String apptDesc;
    private String apptOldColor;
    private String apptColor;

    public Integer getId() {
        return id;
    }

    @StrutsParameter
    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getActive() {
        return active;
    }

    @StrutsParameter
    public void setActive(Integer active) {
        this.active = active;
    }

    public String getApptStatus() {
        return apptStatus;
    }

    public void setApptStatus(String apptStatus) {
        this.apptStatus = apptStatus;
    }

    public String getApptDesc() {
        return apptDesc;
    }

    @StrutsParameter
    public void setApptDesc(String apptDesc) {
        this.apptDesc = apptDesc;
    }

    public String getApptOldColor() {
        return apptOldColor;
    }

    public void setApptOldColor(String apptOldColor) {
        this.apptOldColor = apptOldColor;
    }

    public String getApptColor() {
        return apptColor;
    }

    @StrutsParameter
    public void setApptColor(String apptColor) {
        this.apptColor = apptColor;
    }
}
