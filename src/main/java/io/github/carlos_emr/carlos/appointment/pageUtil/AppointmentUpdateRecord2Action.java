/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.appointment.pageUtil;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.carlos.commn.OtherIdManager;
import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.event.EventService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Struts 2 action that handles appointment updates (migrated from appointmentupdatearecord.jsp).
 * Requires {@code _appointment} update privileges.
 *
 * @since 2026-04-05
 */
public final class AppointmentUpdateRecord2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final OscarAppointmentDao appointmentDao = SpringUtils.getBean(OscarAppointmentDao.class);
    private final AppointmentArchiveDao appointmentArchiveDao = SpringUtils.getBean(AppointmentArchiveDao.class);
    private final EventService eventService = SpringUtils.getBean(EventService.class);

    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "u", null)) {
            throw new SecurityException("missing required sec object (_appointment)");
        }

        String updateuser = (String) request.getSession().getAttribute("user");
        String apptNoStr = request.getParameter("appointment_no");
        if (StringUtils.isEmpty(apptNoStr)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "appointment_no required");
            return NONE;
        }

        Appointment appt = appointmentDao.find(Integer.parseInt(apptNoStr));
        if (appt == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Appointment not found");
            return NONE;
        }
        appointmentArchiveDao.archiveAppointment(appt);

        String changedStatus = null;
        int rowsAffected = 0;

        if (request.getParameter("buttoncancel") != null
                && (request.getParameter("buttoncancel").equals("Cancel Appt")
                || request.getParameter("buttoncancel").equals("No Show"))) {
            changedStatus = request.getParameter("buttoncancel").equals("Cancel Appt") ? "C" : "N";
            appt.setStatus(changedStatus);
            appt.setLastUpdateUser(updateuser);
            appointmentDao.merge(appt);
            rowsAffected = 1;
        } else {
            if (!appt.getStatus().equals(request.getParameter("status"))) {
                changedStatus = request.getParameter("status");
            }
            if (!StringUtils.isEmpty(request.getParameter("demographic_no"))) {
                appt.setDemographicNo(Integer.parseInt(request.getParameter("demographic_no")));
            } else {
                appt.setDemographicNo(0);
            }
            appt.setAppointmentDate(ConversionUtils.fromDateString(request.getParameter("appointment_date")));
            appt.setStartTime(ConversionUtils.fromTimeString(
                    MyDateFormat.getTimeXX_XX_XX(request.getParameter("start_time"))));
            appt.setEndTime(ConversionUtils.fromTimeString(
                    MyDateFormat.getTimeXX_XX_XX(request.getParameter("end_time"))));
            appt.setName(request.getParameter("keyword"));
            appt.setNotes(request.getParameter("notes"));
            appt.setReason(request.getParameter("reason"));
            appt.setLocation(request.getParameter("location"));
            appt.setResources(request.getParameter("resources"));
            appt.setType(request.getParameter("type"));
            appt.setStyle(request.getParameter("style"));
            appt.setBilling(request.getParameter("billing"));
            appt.setStatus(request.getParameter("status"));
            appt.setLastUpdateUser(updateuser);
            appt.setRemarks(request.getParameter("remarks"));
            appt.setUpdateDateTime(new java.util.Date());
            appt.setUrgency(request.getParameter("urgency") != null ? request.getParameter("urgency") : "");
            String rc = request.getParameter("reasonCode");
            if (!StringUtils.isEmpty(rc)) {
                appt.setReasonCode(Integer.parseInt(rc));
            }
            appointmentDao.merge(appt);
            rowsAffected = 1;
        }

        if (rowsAffected == 1) {
            String mcNumber = request.getParameter("appt_mc_number");
            OtherIdManager.saveIdAppointment(apptNoStr, "appt_mc_number", mcNumber);

            if (changedStatus != null) {
                eventService.appointmentStatusChanged(this, apptNoStr, appt.getProviderNo(), changedStatus);
            }
        }

        boolean printReceipt = "1".equals(request.getParameter("printReceipt"));
        request.setAttribute("success", rowsAffected == 1);
        request.setAttribute("appointmentNo", apptNoStr);
        request.setAttribute("printReceipt", printReceipt);

        return SUCCESS;
    }
}
