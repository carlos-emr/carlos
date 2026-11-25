/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.schedule.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.carlos_emr.RscheduleBean;
import io.github.carlos_emr.carlos.commn.model.ScheduleDate;
import io.github.carlos_emr.carlos.schedule.service.ScheduleDateOverrideService;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Saves or deletes a provider's schedule override for one date: the target of the
 * scheduledatepopup.jsp form. Enforces appointment write privilege and POST-only access.
 *
 * <p>The whole command is validated before anything is written, and the current override is
 * retired and its replacement inserted in one transaction ({@link ScheduleDateOverrideService}).
 * The JSP used to retire the override first and then validate, so a malformed or failed Save left
 * the date with no override (#5297).</p>
 *
 * <p>The popup works on the calendar open in the session: its rule schedule supplies Delete's
 * fallback, and {@link ScheduleCalendarCache} holds the days the month view draws. When either now
 * belongs to another provider, the request is refused with 409 before anything is written. After
 * the commit the day is refreshed from the database, and a failed refresh leaves the calendar to
 * reload rather than failing a save that has already committed. The result JSP only redisplays the
 * calendar and closes the popup.</p>
 *
 * @since 2026-04-05
 */
public final class ScheduleDateSave2Action extends ActionSupport {

    /** The popup's hidden {@code date} field: {@code year-month-day}, month and day unpadded. */
    private static final Pattern CALENDAR_DATE = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})");

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private transient ScheduleDateOverrideService overrideService;

    private ScheduleDateOverrideService overrideService() {
        if (overrideService == null) {
            overrideService = SpringUtils.getBean(ScheduleDateOverrideService.class);
        }
        return overrideService;
    }

    /**
     * Validates appointment write privilege and enforces POST-only access for the save endpoint.
     *
     * @return String {@link #SUCCESS} when access is allowed, {@link #NONE} when the request is non-POST
     * @throws SecurityException when the required privilege is missing
     * @throws Exception if writing the 405 response fails
     * @since 2026-04-05
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "w", null)) {
            throw new SecurityException("missing required sec object (_appointment)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            ServletActionContext.getResponse().sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        // Validate the whole command before anything is written.
        String providerNo = request.getParameter("provider_no");
        String dateParam = request.getParameter("date");
        if (providerNo == null || providerNo.isBlank() || dateParam == null || dateParam.isBlank()) {
            return badRequest(request, "error.refusal.scheduleDateIncomplete", "The schedule date or provider is missing.");
        }
        java.sql.Date date = calendarDate(dateParam);
        if (date == null) {
            return badRequest(request, "error.refusal.scheduleDateInvalid", "The schedule date is not a calendar date.");
        }
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", request.getLocale());
        String submit = request.getParameter("Submit");
        boolean save = bundle.getString("schedule.scheduledatepopup.btnSave").equals(submit);
        boolean delete = bundle.getString("schedule.scheduledatepopup.btnDelete").equals(submit);
        if (!save && !delete) {
            // The popup always names Save or Delete; anything else (an empty Submit from a stray
            // Enter key, or a hand-built request) used to delete the override without asking.
            return badRequest(request, "error.refusal.scheduleSaveOrDelete", "Choose Save or Delete.");
        }

        HttpSession session = request.getSession();
        RscheduleBean ruleSchedule = ScheduleCalendarCache.openRule(session, providerNo);
        if (ruleSchedule == null) {
            return refuse(request, HttpServletResponse.SC_CONFLICT, "error.refusal.scheduleCalendarChanged",
                    "The calendar open in this session is for another provider.");
        }

        // The validated date as the month view keys it (year-MM-dd, as schedulecreatedate.jsp loads
        // and looks up its days), not the popup's unpadded year-month-day.
        String day = ConversionUtils.toDateString(date);
        String creator = session.getAttribute("userlastname") + "," + session.getAttribute("userfirstname");
        ScheduleDate replacement = null;
        if (save) {
            String available = request.getParameter("available");
            if (!"1".equals(available) && !"0".equals(available)) {
                return badRequest(request, "error.refusal.scheduleAvailability",
                        "Choose whether the provider is available on this date.");
            }
            replacement = override(date, providerNo, available.charAt(0), 'c', request.getParameter("reason"),
                    request.getParameter("hour"), creator, ruleSchedule.active.charAt(0));
        } else if (ruleSchedule.getDateAvail(day)) {
            // Delete falls back to the provider's rule schedule for the date, when it has one.
            replacement = override(date, providerNo, '1', 'b', "", ruleSchedule.getDateAvailHour(day), creator,
                    ruleSchedule.active.charAt(0));
        }

        if (!overrideService().replaceOverride(providerNo, date, replacement)) {
            return badRequest(request, "error.refusal.scheduleDateIncomplete", "The schedule's provider does not exist.");
        }

        // Committed. Refresh the calendar's copy of the day from the database; if that read fails, the
        // copy is marked for the month view to reload and the save is still acknowledged.
        ScheduleCalendarCache.refreshDay(session, providerNo, date, () -> overrideService().currentOverride(providerNo, date));
        return SUCCESS;
    }

    /**
     * The popup's {@code year-month-day} as a date, or {@code null} unless it names a real calendar
     * day. {@code MyDateFormat.getSysDate} is lenient and rolled an impossible day forward, so a Save
     * for February 30 changed March 2.
     */
    static java.sql.Date calendarDate(String value) {
        Matcher parts = CALENDAR_DATE.matcher(value.trim());
        if (!parts.matches()) {
            return null;
        }
        try {
            return java.sql.Date.valueOf(LocalDate.of(Integer.parseInt(parts.group(1)),
                    Integer.parseInt(parts.group(2)), Integer.parseInt(parts.group(3))));
        } catch (DateTimeException impossible) {
            return null;
        }
    }

    private static ScheduleDate override(java.sql.Date date, String providerNo, char available, char priority,
                                         String reason, String hour, String creator, char status) {
        ScheduleDate replacement = new ScheduleDate();
        replacement.setDate(date);
        replacement.setProviderNo(providerNo);
        replacement.setAvailable(available);
        replacement.setPriority(priority);
        replacement.setReason(reason);
        replacement.setHour(hour);
        replacement.setCreator(creator);
        replacement.setStatus(status);
        return replacement;
    }

    private String badRequest(HttpServletRequest request, String messageKey, String message) throws java.io.IOException {
        return refuse(request, HttpServletResponse.SC_BAD_REQUEST, messageKey, message);
    }

    private String refuse(HttpServletRequest request, int status, String messageKey, String message)
            throws java.io.IOException {
        io.github.carlos_emr.carlos.utility.RefusalNotice.send(request, ServletActionContext.getResponse(),
                status, messageKey, message);
        return NONE;
    }
}
