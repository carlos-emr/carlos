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
import java.util.List;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import io.github.carlos_emr.carlos.utility.MiscUtils;

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
import io.github.carlos_emr.carlos.utility.LocaleUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Struts 2 action that handles appointment updates (migrated from appointmentupdatearecord.jsp).
 * Requires {@code _appointment} write privileges.
 *
 * @since 2026-04-05
 */
public final class AppointmentUpdateRecord2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final OscarAppointmentDao appointmentDao = SpringUtils.getBean(OscarAppointmentDao.class);
    private final AppointmentArchiveDao appointmentArchiveDao = SpringUtils.getBean(AppointmentArchiveDao.class);
    private final EventService eventService = SpringUtils.getBean(EventService.class);

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

        boolean statusOnly = "Cancel Appt".equals(request.getParameter("buttoncancel"))
                || "No Show".equals(request.getParameter("buttoncancel"));
        AppointmentTextInput text = AppointmentTextInput.from(request, request.getParameter("keyword"));
        if (!statusOnly && text.rejectIfTooLong(request, response)) {
            return INPUT;
        }

        final int appointmentNo;
        try {
            appointmentNo = Integer.parseInt(apptNoStr);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid appointment_no");
            return NONE;
        }
        int[] completion = {TransactionSynchronization.STATUS_UNKNOWN};
        UpdateResult[] saved = {null};
        UpdateResult result;
        try {
            TransactionTemplate transaction = new TransactionTemplate(SpringUtils.getBean(PlatformTransactionManager.class));
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            result = transaction.execute(status -> {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int outcome) {
                        completion[0] = outcome;
                        if (outcome == TransactionSynchronization.STATUS_COMMITTED && saved[0] != null) {
                            publishCommittedStatus(apptNoStr, saved[0]);
                        }
                    }
                });
                Appointment appt = appointmentDao.findForUpdate(appointmentNo);
                if (appt == null) return new UpdateResult(HttpServletResponse.SC_NOT_FOUND, null, null);
                String originalVersion = request.getParameter(AppointmentEditVersion.PARAMETER);
                String currentVersion = AppointmentEditVersion.of(appt,
                        OtherIdManager.getApptOtherId(apptNoStr, "appt_mc_number"));
                if (!currentVersion.equals(originalVersion)) {
                    return new UpdateResult(HttpServletResponse.SC_CONFLICT, null, null);
                }
                appointmentArchiveDao.archiveAppointment(appt);
                saved[0] = updateLockedAppointment(request, appt, text, statusOnly, updateuser, apptNoStr);
                return saved[0];
            });
            if (completion[0] != TransactionSynchronization.STATUS_COMMITTED || result == null) {
                throw new IllegalStateException("Appointment transaction did not confirm a commit");
            }
        } catch (RuntimeException e) {
            MiscUtils.getLogger().error("Unable to confirm appointment update", e);
            return retainDraft(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "appointment.edit.msgUpdateUnconfirmed");
        }
        if (result.status() == HttpServletResponse.SC_NOT_FOUND) {
            return retainDraft(request, response, result.status(),
                    "appointment.edit.msgMissing");
        }
        if (result.status() == HttpServletResponse.SC_CONFLICT) {
            return retainDraft(request, response, result.status(),
                    "appointment.edit.msgStale");
        }
        boolean printReceipt = "1".equals(request.getParameter("printReceipt"));
        request.setAttribute("success", true);
        request.setAttribute("appointmentNo", apptNoStr);
        request.setAttribute("printReceipt", printReceipt);

        return SUCCESS;
    }

    private String retainDraft(HttpServletRequest request, HttpServletResponse response, int status, String messageKey) {
        response.setStatus(status);
        request.setAttribute("appointmentValidationErrors", List.of(LocaleUtils.getMessage(request, messageKey)));
        request.setAttribute("appointmentReviewRequired", status != HttpServletResponse.SC_NOT_FOUND);
        return INPUT;
    }

    private void publishCommittedStatus(String appointmentNo, UpdateResult result) {
        if (result.changedStatus() == null) return;
        try {
            eventService.appointmentStatusChanged(this, appointmentNo, result.providerNo(), result.changedStatus());
        } catch (RuntimeException e) {
            MiscUtils.getLogger().error("Unable to publish committed appointment update", e);
        }
    }

    private UpdateResult updateLockedAppointment(HttpServletRequest request, Appointment appt,
            AppointmentTextInput text, boolean statusOnly, String updateuser, String apptNoStr) {
        String changedStatus = null;

        if (statusOnly) {
            changedStatus = request.getParameter("buttoncancel").equals("Cancel Appt") ? "C" : "N";
            appt.setStatus(changedStatus);
            appt.setLastUpdateUser(updateuser);
        } else {
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
            appt.setNotes(text.notes());
            appt.setReason(text.reason());
            appt.setLocation(request.getParameter("location"));
            appt.setResources(text.resources());
            appt.setType(request.getParameter("type"));
            appt.setStyle(request.getParameter("style"));
            appt.setBilling(request.getParameter("billing"));
            appt.setStatus(request.getParameter("status"));
            appt.setLastUpdateUser(updateuser);
            appt.setRemarks(request.getParameter("remarks"));
            appt.setUrgency(request.getParameter("urgency") != null ? request.getParameter("urgency") : "");
            String rc = request.getParameter("reasonCode");
            if (!StringUtils.isEmpty(rc)) {
                appt.setReasonCode(ConversionUtils.fromIntString(rc));
            }
        }

        appt.setUpdateDateTime(new java.util.Date());
        appointmentDao.merge(appt);
        String mcNumber = request.getParameter("appt_mc_number");
        OtherIdManager.saveIdAppointment(apptNoStr, "appt_mc_number", mcNumber);

        return new UpdateResult(HttpServletResponse.SC_OK, appt.getProviderNo(), changedStatus);
    }

    private record UpdateResult(int status, String providerNo, String changedStatus) { }

}
