<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

    This software was written for the
    Department of Family Medicine
    McMaster University
    Hamilton
    Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%--
    appointmenteditrepeatbooking.jsp - Group/recurring appointment booking form

    Purpose: Provides the UI for creating, updating, cancelling, and deleting recurring
    appointments in CARLOS EMR. Supports repeat scheduling by day, week, month, or year
    with a configurable end date.

    Features:
    - Create recurring appointment groups (Add Group Appointment)
    - Update all appointments in a group (Group Update)
    - Cancel all appointments in a group with status "C" (Group Cancel)
    - Delete all appointments in a group with confirmation dialog (Group Delete)
    - Bootstrap 5 responsive layout with JSTL/EL i18n
    - OWASP-encoded confirm dialogs for exit and delete actions

    Parameters (request):
    - appointment_no   (optional) — appointment ID; if present, form renders in edit mode
    - provider_no      — provider identifier
    - appointment_date — date of the first appointment
    - start_time       — appointment start time
    - end_time         — appointment end time
    - keyword          — appointment name/keyword
    - notes            — appointment notes
    - reason           — appointment reason text
    - location         — appointment location
    - resources        — appointment resources
    - type             — appointment type
    - style            — appointment style code
    - billing          — billing code
    - status           — appointment status
    - remarks          — remarks
    - demographic_no   — patient demographic number
    - urgency          — urgency flag
    - reasonCode       — reason code integer
    - everyNum         — repeat interval count (1–11)
    - everyUnit        — repeat interval unit: "day", "week", "month", or "year" (internal English keys)
    - endDate          — end date for the recurrence series (dd/MM/yyyy format)
    - groupappt        — action key submitted by buttons: "Add Group Appointment", "Group Update",
                         "Group Cancel", or "Group Delete"

    @since CARLOS 1.0 (Bootstrap 5 / i18n conversion, April 2026)
--%>
<%@ page import="java.sql.*" errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="io.github.carlos_emr.*" %>
<%@ page import="io.github.carlos_emr.carlos.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.appointment.service.RecurringAppointmentService" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<fmt:setBundle basename="oscarResources"/>
<fmt:message var="exitConfirmMsg" key="appointment.appointmentgrouprecords.msgExitConfirmation"/>
<fmt:message var="deleteConfirmMsg" key="appointment.appointmentgrouprecords.msgDeleteConfirmation"/>

<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
    boolean bEdit = request.getParameter("appointment_no") != null && !request.getParameter("appointment_no").isBlank();
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_appointment" rights='<%= bEdit ? "u" : "w" %>' reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_appointment");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%
    if (session.getAttribute("user") == null) response.sendRedirect(request.getContextPath() + "/logoutPage");
%>

<%
    String recurrenceMessage = null;
    boolean recurrenceSucceeded = false;
    if (request.getParameter("groupappt") != null) {
        Map<String, String> values = new HashMap<>();
        for (String key : request.getParameterMap().keySet()) values.put(key, request.getParameter(key));
        try {
            String program = (String) session.getAttribute("programId_oscarView");
            int count = SpringUtils.getBean(RecurringAppointmentService.class).apply(
                    LoggedInInfo.getLoggedInInfoFromSession(request), values,
                    program == null || program.isBlank() ? 0 : Integer.parseInt(program));
            String operation = request.getParameter("groupappt");
            String verb = "Add Group Appointment".equals(operation) ? "created"
                    : "Group Delete".equals(operation) ? "deleted"
                    : "Group Cancel".equals(operation) ? "cancelled" : "updated";
            recurrenceMessage = count == 0 ? "No appointments created: repeats already exist for the selected dates."
                    : count + " appointment(s) " + verb + ".";
            recurrenceSucceeded = true;
        } catch (IllegalArgumentException ex) {
            recurrenceMessage = ex.getMessage();
        } catch (SecurityException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            MiscUtils.getLogger().error("Recurring appointment operation failed; transaction rolled back", ex);
            recurrenceMessage = "The recurring appointment operation failed. No changes were saved. Please try again.";
        }
    }
    String selectedUnit = request.getParameter("everyUnit");
    String selectedInterval = request.getParameter("everyNum");
    String selectedEnd = request.getParameter("endDate");
    if (selectedEnd == null) {
        try {
            selectedEnd = java.time.LocalDate.parse(request.getParameter("appointment_date")).plusMonths(1)
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu"));
        } catch (RuntimeException ex) {
            selectedEnd = UtilDateUtilities.DateToString(new java.util.Date(), "dd/MM/yyyy");
        }
    }
%>
<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <meta charset="UTF-8">
        <title><fmt:message key="appointment.appointmentgrouprecords.title"/></title>
        <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>

        <!-- calendar stylesheet -->
        <link rel="stylesheet" type="text/css" media="all"
              href="${pageContext.request.contextPath}/share/calendar/calendar.css" title="win2k-cold-1"/>

        <!-- main calendar program -->
        <script type="text/javascript" src="${pageContext.request.contextPath}/share/calendar/calendar.js"></script>

        <!-- language for the calendar -->
        <script type="text/javascript"
                src="${pageContext.request.contextPath}/share/calendar/lang/<fmt:message key="global.javascript.calendar"/>"></script>

        <!-- the following script defines the Calendar.setup helper function, which makes
               adding a calendar a matter of 1 or 2 lines of code. -->
        <script type="text/javascript" src="${pageContext.request.contextPath}/share/calendar/calendar-setup.js"></script>

        <script type="text/javascript">
            function onCheck(a, b) {
                if (a.checked) {
                    document.getElementById("everyUnit").value = b;
                }
            }

            function onExit() {
                if (confirm("${carlos:forJavaScript(exitConfirmMsg)}")) {
                    window.close();
                }
            }
        </script>
    </head>

    <body>
    <div class="container">
        <% if (recurrenceMessage != null) { %>
        <div id="recurrence-result" role="<%= recurrenceSucceeded ? "status" : "alert" %>"
             class="alert <%= recurrenceSucceeded ? "alert-success" : "alert-danger" %>"><%= SafeEncode.forHtml(recurrenceMessage) %></div>
        <% } %>
        <% if (recurrenceSucceeded) { %>
        <button type="button" class="btn btn-primary" onclick="window.close()"><fmt:message key="global.btnClose"/></button>
        <script>
            if (window.opener && !window.opener.closed && typeof window.opener.refresh === 'function') {
                window.opener.refresh();
            }
        </script>
        <% } else { %>
        <p><fmt:message key='<%= bEdit ? "appointment.recurrence.guidance" : "appointment.recurrence.newGuidance" %>'/></p>

        <div id="jsAlertBanner"
             class="alert alert-danger alert-dismissible"
             style="display:none"
             role="alert">
            <span id="jsAlertText"></span>
            <button type="button"
                    class="btn-close"
                    onclick="this.closest('.alert').style.display='none'"
                    aria-label="Close"></button>
        </div>

        <div class="page-header-bar d-flex align-items-center py-2 mb-3 border-bottom" id="header">
            <div class="d-flex align-items-center gap-2">
                <i class="fa-regular fa-calendar" aria-hidden="true"></i>
                <span class="fw-semibold"><fmt:message key="appointment.appointmenteditrepeatbooking.title"/></span>
            </div>
        </div>

        <form name="groupappt" method="POST" action="<%=request.getContextPath() %>/appointment/appointmenteditrepeatbooking">
            <input type="hidden" name="groupappt" value="">

            <div class="bg-light border rounded p-3">

                <!-- How often -->
                <div class="mb-3">
                    <div class="form-label form-label-sm fw-semibold mb-2">
                        <fmt:message key="appointment.appointmenteditrepeatbooking.howoften"/>
                    </div>

                        <div class="form-check form-check-inline">
                   
                        <fmt:message key="appointment.appointmenteditrepeatbooking.every"/>

                        </div>
                        <div class="form-check form-check-inline">
                        <select name="everyNum" class="form-select form-select-sm" style="width: auto;">
                            <%
                                for (int i = 1; i < 12; i++) {
                            %>
                            <option value="<%=i%>" <%= String.valueOf(i).equals(selectedInterval) ? "selected" : "" %>><%=i%></option>
                            <%
                                }
                            %>
                        </select>
                        <input type="hidden" name="everyUnit" id="everyUnit"
                               class="form-control form-control-sm" style="width: 8rem;"
                               value="<%= SafeEncode.forHtmlAttribute(selectedUnit == null ? "day" : selectedUnit) %>" readonly>

                        </div>
                        <div class="form-check form-check-inline">
                            <input class="form-check-input" type="radio" name="dateUnit" id="dateUnitDay"
                                   value="day" <%= selectedUnit == null || "day".equals(selectedUnit) ? "checked" : "" %> onclick='onCheck(this, "day")'>
                            <label class="form-check-label" for="dateUnitDay">
                                <fmt:message key="day"/>
                            </label>
                        </div>
                        <div class="form-check form-check-inline">
                            <input class="form-check-input" type="radio" name="dateUnit" id="dateUnitWeek"
                                   value="week" <%= "week".equals(selectedUnit) ? "checked" : "" %> onclick='onCheck(this, "week")'>
                            <label class="form-check-label" for="dateUnitWeek">
                                <fmt:message key="week"/>
                            </label>
                        </div>
                        <div class="form-check form-check-inline">
                            <input class="form-check-input" type="radio" name="dateUnit" id="dateUnitMonth"
                                   value="month" <%= "month".equals(selectedUnit) ? "checked" : "" %> onclick='onCheck(this, "month")'>
                            <label class="form-check-label" for="dateUnitMonth">
                                <fmt:message key="month"/>
                            </label>
                        </div>
                        <div class="form-check form-check-inline">
                            <input class="form-check-input" type="radio" name="dateUnit" id="dateUnitYear"
                                   value="year" <%= "year".equals(selectedUnit) ? "checked" : "" %> onclick='onCheck(this, "year")'>
                            <label class="form-check-label" for="dateUnitYear">
                                <fmt:message key="year"/>
                            </label>
                        </div>
                    </div>


                <!-- End date -->
                <div class="mb-4 w-50">
                    <label for="endDate" class="form-label form-label-sm fw-semibold">
                        <fmt:message key="appointment.appointmenteditrepeatbooking.endon"/>
                    </label>
                    <div class="input-group">
                        <input type="text" name="endDate" id="endDate"
                               class="form-control form-control-sm" style="width: 9rem;"
                               value="<carlos:encode value='<%= selectedEnd %>' context="htmlAttribute"/>"
                               readonly>
                        
                          <button type="button" id="f_trigger_b" class="btn btn-outline-secondary btn-sm"><i class="fa fa-calendar" aria-hidden="true"></i></button>
                        
                    </div>
                    <div class="form-text"><fmt:message key="ddmmyyyy"/></div>
                </div>

                <!-- Action buttons -->
                <div class="d-flex justify-content-between align-items-center pt-2 border-top">
                    <div class="d-flex gap-2">
                        <button type="button" class="btn btn-primary btn-sm"
                                onclick="document.forms['groupappt'].groupappt.value='Add Group Appointment'; this.disabled=true; document.forms['groupappt'].submit();">
                            <fmt:message key="appointment.recurrence.create"/>
                        </button>
                        <% if (bEdit) { %>
                        <button type="button" class="btn btn-primary btn-sm"
                                onclick="document.forms['groupappt'].groupappt.value='Group Update'; document.forms['groupappt'].submit();">
                            <fmt:message key="appointment.appointmentgrouprecords.btnGroupUpdate"/>
                        </button>
                        <button type="button" class="btn btn-outline-secondary btn-sm"
                                onclick="document.forms['groupappt'].groupappt.value='Group Cancel'; document.forms['groupappt'].submit();">
                            <fmt:message key="appointment.appointmentgrouprecords.btnGroupCancel"/>
                        </button>
                        <button type="button" class="btn btn-danger btn-sm"
                                onclick="if (confirm('${carlos:forJavaScript(deleteConfirmMsg)}')) { document.forms['groupappt'].groupappt.value='Group Delete'; document.forms['groupappt'].submit(); }">
                            <fmt:message key="appointment.appointmentgrouprecords.btnGroupDelete"/>
                        </button>
                        <% } %>
                    </div>
                    <div class="d-flex gap-2">
                        <button type="button" class="btn btn-outline-secondary btn-sm"
                                onclick="window.history.go(-1); return false;">
                            <fmt:message key="global.btnBack"/>
                        </button>
                        <button type="button" class="btn btn-outline-secondary btn-sm"
                                onclick="onExit()">
                            <fmt:message key="global.btnExit"/>
                        </button>
                    </div>
                </div>

            </div><%-- end .bg-light --%>

            <%
                String temp = null;
                for (Enumeration paramNames = request.getParameterNames(); paramNames.hasMoreElements(); ) {
                    temp = paramNames.nextElement().toString();
                    if (Set.of("groupappt", "everyNum", "everyUnit", "dateUnit", "endDate").contains(temp)) continue;
                    if (temp.equals("dboperation") || temp.equals("displaymode") || temp.equals("search_mode") || temp.equals("chart_no"))
                        continue;
                    out.println("<input type='hidden' name='" + SafeEncode.forHtmlAttribute(temp) + "' value=\"" + SafeEncode.forHtmlAttribute(request.getParameter(temp) != null ? request.getParameter(temp) : "") + "\">");
                }
            %>
        </form>

        <% } %>
    </div><%-- end .container --%>

    <script type="text/javascript">
        if (document.getElementById("endDate")) Calendar.setup({
            inputField: "endDate",      // id of the input field
            ifFormat: "%d/%m/%Y",       // format of the input field
            showsTime: false,            // will display a time selector
            button: "f_trigger_b",   // trigger for the calendar (button ID)
            singleClick: true,           // double-click mode
            step: 1                // show all years in drop-down boxes (instead of every other year as default)
        });
    </script>

    </body>
</html>
