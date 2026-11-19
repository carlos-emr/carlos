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
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.carlos.commn.OtherIdManager;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.event.EventService;
import io.github.carlos_emr.carlos.managers.AppointmentUpdateException;
import io.github.carlos_emr.carlos.managers.ScheduleManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Struts 2 action that handles appointment updates (migrated from appointmentupdatearecord.jsp).
 * Requires {@code _appointment} write privileges.
 *
 * <p>The edit form posts the whole appointment, so the write goes through
 * {@link ScheduleManager#updateAppointment(LoggedInInfo, Integer, String, java.util.function.Consumer)}:
 * the row is locked, the version the form was rendered from
 * ({@link AppointmentEditConflict#PARAMETER}) is compared with it, and the archive row and the
 * update commit together. A form opened before another user's change is refused with HTTP 409 and
 * the {@link AppointmentEditConflict#RESULT} page instead of writing its stale fields back over
 * that change (for example un-cancelling the appointment). A stale form whose fields the
 * appointment already holds (a second click after the first one saved) writes nothing and ends
 * as the first submission did. A post that carries no version keeps the previous
 * last-writer-wins behaviour.</p>
 *
 * @since 2026-04-05
 */
public final class AppointmentUpdateRecord2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final OscarAppointmentDao appointmentDao = SpringUtils.getBean(OscarAppointmentDao.class);
    private final EventService eventService = SpringUtils.getBean(EventService.class);
    private final ScheduleManager scheduleManager;

    /** Struts creates this action through its no-argument constructor. */
    public AppointmentUpdateRecord2Action() {
        this.scheduleManager = SpringUtils.getBean(ScheduleManager.class);
    }

    /** A text field the locked row no longer accepts; unwinds the manager's transaction. */
    private static final class TextRefusedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final transient AppointmentTextBounds.Violation violation;

        TextRefusedException(AppointmentTextBounds.Violation violation) {
            super("Appointment text refused");
            this.violation = violation;
        }
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
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "w", null)) {
            throw new SecurityException("missing required sec object (_appointment)");
        }

        String updateuser = (String) request.getSession().getAttribute("user");
        String apptNoStr = request.getParameter("appointment_no");
        if (StringUtils.isEmpty(apptNoStr)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "appointment_no required");
            return NONE;
        }

        Integer apptNo;
        Appointment appt;
        try {
            apptNo = Integer.valueOf(apptNoStr);
            appt = appointmentDao.find(apptNo.intValue());
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid appointment_no");
            return NONE;
        }
        if (appt == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Appointment not found");
            return NONE;
        }
        boolean statusOnly = "Cancel Appt".equals(request.getParameter("buttoncancel"))
                || "No Show".equals(request.getParameter("buttoncancel"));
        if (!statusOnly) {
            // Refuse over-long or malformed text before the manager archives anything.
            AppointmentTextBounds.Values text = AppointmentTextBounds.submitted(request, request.getParameter("keyword"), appt);
            AppointmentTextBounds.Violation invalidText = AppointmentTextBounds.validate(text, appt);
            if (invalidText != null) {
                return AppointmentTextBounds.refuse(request, response, invalidText);
            }
        }

        // Written by the edit callback, which runs on the locked row inside the manager's transaction.
        String[] changedStatus = new String[1];
        Appointment saved;
        try {
            saved = scheduleManager.updateAppointment(loggedInInfo, apptNo,
                    AppointmentEditConflict.submittedVersion(request), current -> {
                        if (statusOnly) {
                            changedStatus[0] = "Cancel Appt".equals(request.getParameter("buttoncancel")) ? "C" : "N";
                            current.setStatus(changedStatus[0]);
                            current.setLastUpdateUser(updateuser);
                        } else {
                            changedStatus[0] = applySubmittedFields(request, current, updateuser);
                        }
                    });
        } catch (AppointmentUpdateException refused) {
            if (refused.getReason() == AppointmentUpdateException.Reason.STALE_VERSION) {
                // Nothing was archived or written: the manager's transaction rolled back.
                Appointment stored = appointmentDao.find(apptNo.intValue());
                if (stored != null && alreadyStored(request, stored, statusOnly)) {
                    // The appointment already holds exactly what this form posts: a second click on
                    // the same form whose first submission was saved (the browser shows this later
                    // response). There is nothing to write and nothing to lose, so it ends as the
                    // first submission did instead of reporting a conflict with the user's own save.
                    return saved(request, apptNoStr);
                }
                // Another user changed (or cancelled) the appointment after this form was opened.
                return AppointmentEditConflict.refuse(request, response);
            }
            response.sendError(refused.getReason() == AppointmentUpdateException.Reason.NOT_FOUND
                    ? HttpServletResponse.SC_NOT_FOUND : HttpServletResponse.SC_BAD_REQUEST,
                    refused.getReason() == AppointmentUpdateException.Reason.NOT_FOUND
                            ? "Appointment not found" : "Invalid appointment update");
            return NONE;
        } catch (TextRefusedException refused) {
            return AppointmentTextBounds.refuse(request, response, refused.violation);
        } catch (jakarta.ws.rs.ForbiddenException denied) {
            // The manager also enforces a patient-specific denial of the appointment's patient.
            throw new SecurityException("missing required sec object (_appointment)");
        } catch (jakarta.ws.rs.BadRequestException invalid) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid appointment update");
            return NONE;
        }

        String mcNumber = request.getParameter("appt_mc_number");
        OtherIdManager.saveIdAppointment(apptNoStr, "appt_mc_number", mcNumber);

        if (changedStatus[0] != null) {
            eventService.appointmentStatusChanged(this, apptNoStr, saved.getProviderNo(), changedStatus[0]);
        }

        // The manager has audited the edit under the appointment and its patient, naming a changed
        // status; a second row here would record one edit twice.
        return saved(request, apptNoStr);
    }

    /** The result page of a saved update: it refreshes the day sheet, opens the receipt if asked and closes the popup. */
    private static String saved(HttpServletRequest request, String apptNoStr) {
        boolean printReceipt = "1".equals(request.getParameter("printReceipt"));
        request.setAttribute("success", true);
        request.setAttribute("appointmentNo", apptNoStr);
        request.setAttribute("printReceipt", printReceipt);

        return SUCCESS;
    }

    /**
     * Whether applying the posted form to {@code stored} would change nothing, field for field as
     * {@link #applySubmittedFields} writes them (or, for the Cancel / No Show buttons, the status
     * alone). Used only to recognise a repeat of an already saved form; a difference in any field
     * leaves the stale form refused.
     */
    private static boolean alreadyStored(HttpServletRequest request, Appointment stored, boolean statusOnly) {
        if (statusOnly) {
            return ("Cancel Appt".equals(request.getParameter("buttoncancel")) ? "C" : "N").equals(stored.getStatus());
        }
        AppointmentTextBounds.Values text = AppointmentTextBounds.submitted(request, request.getParameter("keyword"), stored);
        String reasonCode = request.getParameter("reasonCode");
        return stored.getDemographicNo() == ConversionUtils.fromIntString(request.getParameter("demographic_no"))
                && ConversionUtils.toDateString(stored.getAppointmentDate()).equals(ConversionUtils.toDateString(
                        ConversionUtils.fromDateString(request.getParameter("appointment_date"))))
                && sameTime(stored.getStartTime(), request.getParameter("start_time"))
                && sameTime(stored.getEndTime(), request.getParameter("end_time"))
                && Objects.equals(stored.getName(), text.name())
                && Objects.equals(stored.getNotes(), text.notes())
                && Objects.equals(stored.getReason(), text.reason())
                && Objects.equals(stored.getLocation(), request.getParameter("location"))
                && Objects.equals(stored.getResources(), text.resources())
                // getType() reads a stored NULL as the empty string.
                && Objects.equals(stored.getType(), StringUtils.defaultString(request.getParameter("type")))
                && Objects.equals(stored.getStyle(), request.getParameter("style"))
                && Objects.equals(stored.getBilling(), request.getParameter("billing"))
                && Objects.equals(stored.getStatus(), request.getParameter("status"))
                && Objects.equals(stored.getRemarks(), request.getParameter("remarks"))
                && Objects.equals(stored.getUrgency(), request.getParameter("urgency") != null ? request.getParameter("urgency") : "")
                && (StringUtils.isEmpty(reasonCode)
                        || Objects.equals(stored.getReasonCode(), ConversionUtils.fromIntString(reasonCode)));
    }

    private static boolean sameTime(java.util.Date stored, String posted) {
        return ConversionUtils.toTimeString(stored).equals(ConversionUtils.toTimeString(
                ConversionUtils.fromTimeString(MyDateFormat.getTimeXX_XX_XX(posted))));
    }

    /**
     * Copies the posted form onto the locked appointment.
     *
     * @return the posted status when it differs from the stored one, otherwise {@code null}
     * @throws TextRefusedException when a text field no longer fits the locked row
     */
    private static String applySubmittedFields(HttpServletRequest request, Appointment appt, String updateuser) {
        AppointmentTextBounds.Values text = AppointmentTextBounds.submitted(request, request.getParameter("keyword"), appt);
        AppointmentTextBounds.Violation invalidText = AppointmentTextBounds.validate(text, appt);
        if (invalidText != null) {
            throw new TextRefusedException(invalidText);
        }
        String changedStatus = null;
        if (!StringUtils.equals(appt.getStatus(), request.getParameter("status"))) {
            changedStatus = request.getParameter("status");
        }
        appt.setDemographicNo(ConversionUtils.fromIntString(request.getParameter("demographic_no")));
        appt.setAppointmentDate(ConversionUtils.fromDateString(request.getParameter("appointment_date")));
        appt.setStartTime(ConversionUtils.fromTimeString(
                MyDateFormat.getTimeXX_XX_XX(request.getParameter("start_time"))));
        appt.setEndTime(ConversionUtils.fromTimeString(
                MyDateFormat.getTimeXX_XX_XX(request.getParameter("end_time"))));
        appt.setName(text.name());
        // Hibernate loads legacy CR/CRLF directly into fields. Leave logically unchanged
        // text there: the setters normalize new writes and would rewrite those stored bytes.
        if (!StringUtils.equals(appt.getNotes(), text.notes())) appt.setNotes(text.notes());
        if (!StringUtils.equals(appt.getReason(), text.reason())) appt.setReason(text.reason());
        appt.setLocation(request.getParameter("location"));
        appt.setResources(text.resources());
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
            appt.setReasonCode(ConversionUtils.fromIntString(rc));
        }
        return changedStatus;
    }
}
