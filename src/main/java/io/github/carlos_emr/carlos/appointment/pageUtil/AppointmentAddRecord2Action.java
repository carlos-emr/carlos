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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.OtherIdManager;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.WaitingListName;
import io.github.carlos_emr.carlos.demographic.data.DemographicData;
import io.github.carlos_emr.carlos.demographic.data.DemographicMerged;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.event.EventService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.utility.FormSubmissionGuard;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.waitinglist.WaitingList;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Struts 2 action that handles appointment creation (migrated from appointmentaddarecord.jsp).
 * Requires {@code _appointment} write privileges.
 *
 * @since 2026-04-05
 */
public final class AppointmentAddRecord2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final OscarAppointmentDao appointmentDao = SpringUtils.getBean(OscarAppointmentDao.class);
    private final WaitingListDao waitingListDao = SpringUtils.getBean(WaitingListDao.class);
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

        String createDateTime = UtilDateUtilities.DateToString(new java.util.Date(), "yyyy-MM-dd HH:mm:ss");

        // Resolve demographic and name
        String demographicNoStr = request.getParameter("demographic_no");
        int demographicNo = 0;
        String appointmentName = request.getParameter("keyword");

        if (!StringUtils.isEmpty(demographicNoStr)) {
            DemographicMerged dmDAO = new DemographicMerged();
            String resolvedHead = dmDAO.getHead(demographicNoStr);
            demographicNoStr = resolvedHead != null ? resolvedHead : demographicNoStr;
            demographicNo = ConversionUtils.fromIntString(demographicNoStr);
            DemographicData demData = new DemographicData();
            Demographic demo = demData.getDemographic(loggedInInfo, demographicNoStr);
            if (demo != null) {
                appointmentName = demo.getLastName() + "," + demo.getFirstName();
            }
        } else {
            demographicNoStr = "0";
        }

        Appointment a = new Appointment();
        a.setProviderNo(request.getParameter("provider_no"));
        a.setAppointmentDate(ConversionUtils.fromDateString(request.getParameter("appointment_date")));
        a.setStartTime(ConversionUtils.fromTimeStringNoSeconds(request.getParameter("start_time")));
        a.setEndTime(ConversionUtils.fromTimeStringNoSeconds(request.getParameter("end_time")));
        a.setName(appointmentName);
        a.setNotes(request.getParameter("notes"));
        a.setReason(request.getParameter("reason"));
        a.setLocation(request.getParameter("location"));
        a.setResources(request.getParameter("resources"));
        a.setType(request.getParameter("type"));
        a.setStyle(request.getParameter("style"));
        a.setBilling(request.getParameter("billing"));
        a.setStatus(request.getParameter("status"));
        a.setCreateDateTime(ConversionUtils.fromTimestampString(createDateTime));
        a.setCreator(loggedInInfo.getLoggedInProviderNo());
        a.setRemarks(request.getParameter("remarks"));
        a.setDemographicNo(demographicNo);
        String programIdStr = (String) request.getSession().getAttribute("programId_oscarView");
        if (!StringUtils.isEmpty(programIdStr)) {
            int programId = ConversionUtils.fromIntString(programIdStr);
            if (programId > 0) {
                a.setProgramId(programId);
            }
        }
        a.setUrgency(request.getParameter("urgency") != null ? request.getParameter("urgency") : "");
        String rc = request.getParameter("reasonCode");
        if (!StringUtils.isEmpty(rc)) {
            a.setReasonCode(ConversionUtils.fromIntString(rc));
        }

        // One rendered Add Appointment form books once (issue #4225): a double click, double Enter
        // or a re-click while a slow save is out repeats the same token, and the repeat ends as the
        // first booking did instead of booking the slot a second time. A post without a token
        // keeps the previous behaviour.
        FormSubmissionGuard.Attempt attempt = FormSubmissionGuard.attempt(request.getSession(),
                request.getParameter(FormSubmissionGuard.PARAMETER), FormSubmissionGuard.Scope.APPOINTMENT_ADD,
                null, FormSubmissionGuard.DEFAULT_WAIT_MILLIS);
        switch (attempt.verdict()) {
            case PROCEED, ABSENT -> { }
            case ALREADY_SAVED -> {
                return showAdded(request, ConversionUtils.fromIntString(attempt.result()), demographicNo, demographicNoStr);
            }
            default -> {
                return FormSubmissionGuard.refuse(request, response, attempt.verdict());
            }
        }

        int apptId;
        try (FormSubmissionGuard.Claim claim = attempt.claim()) {
            String mcNumber = request.getParameter("appt_mc_number");
            // The appointment and its MC number commit together, so the claim learns whether
            // anything was stored and a failed booking can be retried from the same form.
            new TransactionTemplate(SpringUtils.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
                if (claim != null) {
                    claim.storageStarted();
                }
                appointmentDao.persist(a);
                // Use the persisted ID directly; avoids a secondary lookup and prevents race conditions
                if (a.getId() != null && a.getId() > 0) {
                    OtherIdManager.saveIdAppointment(a.getId(), "appt_mc_number", mcNumber);
                }
            });
            apptId = a.getId() == null ? 0 : a.getId();
            if (claim != null && apptId > 0) {
                claim.saved(String.valueOf(apptId));
            }
            if (apptId > 0) {
                eventService.appointmentCreated(this, String.valueOf(apptId), request.getParameter("provider_no"));
            }
        }

        return showAdded(request, apptId, demographicNo, demographicNoStr);
    }

    /**
     * The result page for appointment {@code apptId}: the success message that refreshes the day
     * sheet and closes the popup, the optional receipt, and the waiting-list removal prompt. A
     * repeated submission of an already-booked form ends here too, so the user sees the same page
     * whichever of the two responses the browser kept.
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private String showAdded(HttpServletRequest request, int apptId, int demographicNo, String demographicNoStr) {
        boolean printReceipt = "1".equals(request.getParameter("printReceipt"));
        request.setAttribute("success", true);
        request.setAttribute("apptId", apptId);
        request.setAttribute("printReceipt", printReceipt);

        // Waiting list removal prompt
        CarlosProperties pros = CarlosProperties.getInstance();
        String strMWL = pros.getProperty("MANUALLY_CLEANUP_WL");
        if (strMWL == null || !strMWL.equalsIgnoreCase("yes")) {
            WaitingList wl = WaitingList.getInstance();
            if (wl.getFound() && demographicNo > 0) {
                List<Object[]> wlEntries = waitingListDao.findByDemographic(demographicNo);
                if (!wlEntries.isEmpty()) {
                    WaitingListName wln = (WaitingListName) wlEntries.get(0)[0];
                    io.github.carlos_emr.carlos.commn.model.WaitingList wl1 =
                            (io.github.carlos_emr.carlos.commn.model.WaitingList) wlEntries.get(0)[1];
                    request.setAttribute("waitingListName", wln.getName());
                    request.setAttribute("waitingListId", wl1.getListId());
                    request.setAttribute("waitingListDemographicNo", demographicNoStr);
                }
            }
        }

        return SUCCESS;
    }
}
