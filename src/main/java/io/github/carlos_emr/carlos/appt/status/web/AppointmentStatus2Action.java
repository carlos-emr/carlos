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

/**
 * Struts 2 action for managing appointment status configurations.
 *
 * <p>Handles viewing, modifying, resetting, and toggling active/inactive state of
 * appointment statuses. Uses method-based routing via the {@code dispatch} request
 * parameter to determine which operation to perform.</p>
 *
 * @since 2026-03-17
 */
public class AppointmentStatus2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final Logger logger = MiscUtils.getLogger();

    /**
     * Routes the request to the appropriate handler method based on the {@code dispatch} parameter.
     *
     * @return String the result name
     */
    public String execute() {
        String method = request.getParameter("dispatch");
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

    /**
     * Displays all appointment statuses.
     *
     * @return String the success result name
     */
    public String view() {
        logger.warn("view");
        populateAllStatus(request);
        return SUCCESS;
    }

    /**
     * Resets all appointment statuses to their default values.
     *
     * @return String the success result name
     */
    public String reset() {
        logger.warn("reset");
        AppointmentStatusMgr apptStatusMgr = getApptStatusMgr();
        apptStatusMgr.reset();
        populateAllStatus(request);
        return SUCCESS;
    }

    /**
     * Toggles the active/inactive state of an appointment status.
     *
     * @return String the success result name
     */
    public String changestatus() {
        logger.warn("changestatus");
        AppointmentStatusMgr apptStatusMgr = getApptStatusMgr();
        int ID = Integer.parseInt(request.getParameter("statusID"));
        int iActive = Integer.parseInt(request.getParameter("iActive"));
        apptStatusMgr.changeStatus(ID, iActive);
        populateAllStatus(request);
        return SUCCESS;
    }

    /**
     * Loads an appointment status for editing.
     *
     * @return String "edit" result name to display the edit form
     */
    public String modify() {
        logger.warn("modify");
        AppointmentStatusMgr apptStatusMgr = getApptStatusMgr();
        int ID = Integer.parseInt(request.getParameter("statusID"));
        AppointmentStatus appt = apptStatusMgr.getStatus(ID);

        this.setID(ID);
        this.setApptStatus(appt.getStatus());
        this.setApptDesc(appt.getDescription());
        this.setApptOldColor(appt.getColor());

        return "edit";
    }

    /**
     * Saves modifications to an appointment status description and color.
     *
     * @return String the success result name
     */
    public String update() {
        logger.warn("update");
        AppointmentStatusMgr apptStatusMgr = getApptStatusMgr();

        int ID = this.getID();
        String strDesc = this.getApptDesc();
        String strColor = this.getApptColor();
        if (null == strColor || strColor.equals(""))
            strColor = this.getApptOldColor();
        apptStatusMgr.modifyStatus(ID, strDesc, strColor);
        populateAllStatus(request);
        return SUCCESS;
    }

    /**
     * Returns the Spring web application context.
     *
     * @return WebApplicationContext the application context
     */
    public WebApplicationContext getApptContext() {
        return WebApplicationContextUtils.getRequiredWebApplicationContext(ServletActionContext.getServletContext());
    }

    /**
     * Creates and returns a new appointment status manager instance.
     *
     * @return AppointmentStatusMgr the status manager
     */
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

    private int ID;
    private String apptStatus;
    private String apptDesc;
    private String apptOldColor;
    private String apptColor;

    public int getID() {
        return ID;
    }

    @StrutsParameter
    public void setID(int ID) {
        this.ID = ID;
    }

    public String getApptStatus() {
        return apptStatus;
    }

    @StrutsParameter
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

    @StrutsParameter
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
