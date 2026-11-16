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

import io.github.carlos_emr.carlos.appt.ApptUtil;
import io.github.carlos_emr.carlos.appt.ApptData;
import io.github.carlos_emr.carlos.appt.AppointmentClipboardValidationException;
import io.github.carlos_emr.carlos.appt.location.AppointmentLocationUnavailableException;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.AppointmentUpdateException;
import io.github.carlos_emr.carlos.managers.ScheduleManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.ErrorPageMessage;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Struts 2 action that handles appointment cut/move (migrated from appointmentcutrecord.jsp).
 * Copies appointment data into session for later paste, then archives and removes the appointment.
 * Requires {@code _appointment} delete privileges.
 * This action adds the previously missing role-based authorization check.
 *
 * @since 2026-04-05
 */
public final class AppointmentCutRecord2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final ScheduleManager scheduleManager;

    /** Struts creates this action through its no-argument constructor. */
    public AppointmentCutRecord2Action() {
        this.scheduleManager = SpringUtils.getBean(ScheduleManager.class);
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "d", null)) {
            throw new SecurityException("missing required sec object (_appointment)");
        }

        String apptNoParam = request.getParameter("appointment_no");
        if (StringUtils.isEmpty(apptNoParam)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "appointment_no required");
            return NONE;
        }

        Integer apptNo;
        try {
            apptNo = Integer.valueOf(apptNoParam);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid appointment_no");
            return NONE;
        }

        // Lock, compare the version the edit form was rendered from, archive and remove in one
        // transaction. A form opened before another user changed the appointment is refused
        // (HTTP 409) instead of removing the newer appointment unseen; a post without a version
        // keeps the unconditional removal this route always had.
        Appointment appt;
        ApptData[] clipboard = new ApptData[1];
        try {
            appt = scheduleManager.removeAppointment(loggedInInfo, apptNo,
                    AppointmentEditConflict.submittedVersion(request),
                    source -> clipboard[0] = ApptUtil.prepareAppointmentClipboard(request, source));
        } catch (AppointmentUpdateException refused) {
            if (refused.getReason() == AppointmentUpdateException.Reason.STALE_VERSION) {
                return AppointmentEditConflict.refuse(request, response);
            }
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Appointment not found");
            return NONE;
        } catch (jakarta.ws.rs.ForbiddenException denied) {
            // The manager also enforces a patient-specific denial of the appointment's patient.
            throw new SecurityException("missing required sec object (_appointment)");
        } catch (jakarta.ws.rs.BadRequestException invalid) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid appointment_no");
            return NONE;
        } catch (AppointmentClipboardValidationException _) {
            ErrorPageMessage.sendError(request, response, HttpServletResponse.SC_CONFLICT,
                    AppointmentClipboardValidationException.MESSAGE_KEY);
            return NONE;
        } catch (AppointmentLocationUnavailableException _) {
            ErrorPageMessage.sendError(request, response, HttpServletResponse.SC_CONFLICT,
                    AppointmentLocationUnavailableException.MESSAGE_KEY);
            return NONE;
        } catch (IllegalArgumentException _) {
            // A stale/unavailable location must not remove the source appointment or replace
            // the prior clipboard. The helper's messages contain no request or patient data.
            ErrorPageMessage.sendError(request, response, HttpServletResponse.SC_CONFLICT,
                    "appointment.clipboard.sourceUnavailable");
            return NONE;
        }

        // The removal has committed. Publishing the already-validated clipboard needs no source
        // lookup, and a validation or commit failure leaves the previous clipboard intact.
        ApptUtil.setAppointmentIntoSession(request, clipboard[0]);

        // The action name and "id=N" data are the receipt the cut-recovery flow is checked by; the
        // row also names the appointment, its patient and the client address. It is written in this
        // thread once the appointment is really gone, so a refused (stale) cut leaves no removal row.
        LogAction.addLogSynchronous(loggedInInfo, "Appointment.cut", LogConst.CON_APPT, String.valueOf(appt.getId()),
                appt.getDemographicNo() > 0 ? String.valueOf(appt.getDemographicNo()) : null, "id=" + appt.getId());

        request.setAttribute("success", true);
        return SUCCESS;
    }
}
