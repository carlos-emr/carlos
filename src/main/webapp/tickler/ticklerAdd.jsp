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

--%>
<%--
    ticklerAdd.jsp - Add a new tickler reminder

    Purpose:
    Provides a form for creating new tickler reminders for a patient, with support
    for quick-pick date selection, suggested text templates, and optional write-to-encounter.

    Features:
    - Accumulative quick-pick date selector (years, months, weeks, days offset)
    - Suggested text templates for common tickler messages
    - Write-to-encounter option for chart documentation
    - Multisite and CAISI program provider assignment support
    - Patient demographic search and selection

    Parameters:
    - demographic_no:       Patient demographic number
    - xml_appointment_date: Initial service/appointment date (YYYY-MM-DD)
    - taskTo:               Default task assignee provider number
    - priority:             Tickler priority (High/Normal/Low)
    - parentAjaxId:         Encounter navbar element ID for update notification
    - updateParent:         Whether to update the parent encounter window (true/false)
    - recall:               If present, marks this as a recall tickler
    - docType:              Optional document type for linking
    - docId:                Optional document ID for linking

--%>
<%@ page import="ca.openosp.openo.PMmodule.dao.ProgramProviderDAO" %>
<%@ page import="ca.openosp.openo.PMmodule.dao.ProviderDao" %>
<%@ page import="ca.openosp.openo.PMmodule.model.ProgramProvider" %>
<%@ page import="ca.openosp.openo.commn.dao.DemographicDao" %>
<%@ page import="ca.openosp.openo.commn.dao.OscarAppointmentDao" %>
<%@ page import="ca.openosp.openo.commn.dao.SiteDao" %>
<%@ page import="ca.openosp.openo.commn.dao.TicklerTextSuggestDao" %>
<%@ page import="ca.openosp.openo.commn.dao.UserPropertyDAO" %>
<%@ page import="ca.openosp.openo.commn.model.Appointment" %>
<%@ page import="ca.openosp.openo.commn.model.Demographic" %>
<%@ page import="ca.openosp.openo.commn.model.Provider" %>
<%@ page import="ca.openosp.openo.commn.model.Site" %>
<%@ page import="ca.openosp.openo.commn.model.TicklerTextSuggest" %>
<%@ page import="ca.openosp.openo.commn.model.UserProperty" %>
<%@ page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.MyDateFormat" %>
<%@ page import="ca.openosp.OscarProperties" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Calendar" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.GregorianCalendar" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Set" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_tickler" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_tickler");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%
    String user_no = (String) session.getAttribute("user");
    int nItems = 0;
    String strLimit1 = "0";
    String strLimit2 = "5";
    if (request.getParameter("limit1") != null) strLimit1 = request.getParameter("limit1");
    if (request.getParameter("limit2") != null) strLimit2 = request.getParameter("limit2");
    boolean bFirstDisp = true; //this is the first time to display the window
    if (request.getParameter("bFirstDisp") != null) bFirstDisp = (request.getParameter("bFirstDisp")).equals("true");
    String ChartNo;
    String demoMRP = "";
    String demoName = request.getParameter("name");
    String defaultTaskAssignee = "";

    DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
    Demographic demographic = demographicDao.getDemographic(request.getParameter("demographic_no"));
    if (demographic != null) {
        demoName = demographic.getFormattedName();
        demoMRP = demographic.getProviderNo();
        bFirstDisp = false;
    }

    if (demoName == null) {
        demoName = "";
    }

    Boolean writeToEncounter = false;
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    Boolean caisiEnabled = OscarProperties.getInstance().isPropertyActive("caisi");
    Integer defaultProgramId = null;
    List<ProgramProvider> programProviders = new ArrayList<ProgramProvider>();

    if (caisiEnabled) {
        ProgramProviderDAO programProviderDao = SpringUtils.getBean(ProgramProviderDAO.class);
        programProviders = programProviderDao.getProgramProviderByProviderNo(loggedInInfo.getLoggedInProviderNo());
        if (programProviders.size() == 1) {
            defaultProgramId = programProviders.get(0).getProgram().getId();
        }
    }

    String parentAjaxId;
    if (request.getParameter("parentAjaxId") != null)
        parentAjaxId = request.getParameter("parentAjaxId");
    else
        parentAjaxId = "";

    String updateParent;
    if (request.getParameter("updateParent") != null)
        updateParent = request.getParameter("updateParent");
    else
        updateParent = "true";

    boolean recall = false;
    String taskTo = user_no; //default current user
    String priority = "Normal";
    if (request.getParameter("taskTo") != null) taskTo = request.getParameter("taskTo");
    if (request.getParameter("priority") != null) priority = request.getParameter("priority");
    if (request.getParameter("recall") != null) recall = true;

    UserPropertyDAO propertyDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
    UserProperty prop = propertyDao.getProp(user_no, "tickler_task_assignee");

    //don't over ride taskTo query param
    if (request.getParameter("taskTo") == null) {

        if (prop != null) {
            defaultTaskAssignee = prop.getValue();
            if (!defaultTaskAssignee.equals("mrp")) {
                taskTo = defaultTaskAssignee;
            } else if (defaultTaskAssignee.equals("mrp")) {
                taskTo = demoMRP;
            }
        }

    }
%>

<%
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    OscarAppointmentDao appointmentDao = SpringUtils.getBean(OscarAppointmentDao.class);
%>

<%
    GregorianCalendar now = new GregorianCalendar();
    int curYear = now.get(Calendar.YEAR);
    int curMonth = (now.get(Calendar.MONTH) + 1);
    int curDay = now.get(Calendar.DAY_OF_MONTH);
%><%
    String xml_vdate = request.getParameter("xml_vdate") == null ? "" : request.getParameter("xml_vdate");
    String xml_appointment_date = request.getParameter("xml_appointment_date") == null ? MyDateFormat.getMysqlStandardDate(curYear, curMonth, curDay) : request.getParameter("xml_appointment_date");
%>

<!DOCTYPE html>
<html>
    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.title"/></title>

        <script>
        function pasteMessageText() {
            let selectedIdx = document.serviceform.suggestedText.selectedIndex;
            document.getElementById("ticklerMessage").value = document.serviceform.suggestedText.options[selectedIdx].text;
        }

        function addQuickPick() {

            const dateInput = document.querySelector('input[name="xml_appointment_date"]');
            const container = document.getElementById('quickPickDateOptions');
            if (!dateInput || !container) return;

            container.innerHTML = ''; // Clear existing buttons

            const optionsByRow = [
                // Years row
                [
                    { label: '1y', years: 1 },
                    { label: '2y', years: 2 },
                    { label: '3y', years: 3 },
                    { label: '5y', years: 5 },
                    { label: '10y', years: 10 },
                ],
                // Months row
                [
                    { label: '1m', months: 1 },
                    { label: '2m', months: 2 },
                    { label: '3m', months: 3 },
                    { label: '6m', months: 6 },
                ],
                // Weeks and Days row
                [
                    { label: '1w', weeks: 1 },
                    { label: '2w', weeks: 2 },
                    { label: '3w', weeks: 3 },
                    { label: '1d', days: 1 },
                    { label: '2d', days: 2 },
                    { label: '3d', days: 3 },
                    { label: 'Clear', isClear: true },
                ],
            ];

            let baseDate = null;
            // Accumulate all added offsets here, by type
            let totalYears = 0;
            let totalMonths = 0;
            let totalWeeks = 0;
            let totalDays = 0;

            const display = document.createElement('div');
            display.style.margin = '5px 0';
            display.style.fontSize = '0.9em';
            display.style.color = '#336';
            display.innerHTML = '&nbsp;'; // Reserve vertical space
            display.style.minHeight = '1.5em'; // Adjust height to match expected line height
            container.parentNode.insertBefore(display, container);

            function parseDateInput() {
                const val = dateInput.value;
                const d = new Date(val);
                return isNaN(d) ? new Date() : d;
            }
            baseDate = parseDateInput();

            function updateDisplayAndDate() {
                if (!baseDate) return;

                // Calculate total days from weeks + days
                const daysFromWeeks = totalWeeks * 7;
                let date = new Date(baseDate);

                // Add years
                date.setFullYear(date.getFullYear() + totalYears);
                // Add months
                date.setMonth(date.getMonth() + totalMonths);
                // Add weeks and days
                date.setDate(date.getDate() + daysFromWeeks + totalDays);

                dateInput.value = date.toISOString().split('T')[0];

                // Build display string for total offset
                const parts = [];
                if (totalYears) parts.push(totalYears + 'y');
                if (totalMonths) parts.push(totalMonths + 'm');
                if (totalWeeks) parts.push(totalWeeks + 'w');
                if (totalDays) parts.push(totalDays + 'd');
                if (parts.length === 0) parts.push('0d');

                display.innerHTML = "From " + baseDate.toISOString().split('T')[0] + ":&nbsp;&nbsp;&nbsp;&nbsp;<strong>" + parts.join(' ') + "</strong>";
            }

            function resetTotals() {
                totalYears = 0;
                totalMonths = 0;
                totalWeeks = 0;
                totalDays = 0;
            }

            optionsByRow.forEach(rowOptions => {
                const row = document.createElement('div');
                rowOptions.forEach(opt => {
                    const btn = document.createElement('button');
                    btn.textContent = opt.label;
                    btn.title = 'Click to add. Shift+Click or Right-click to subtract.';

                    const handleOffset = (delta) => {
                        if (baseDate === null) {
                            baseDate = parseDateInput();
                            resetTotals();
                        }
                        if (opt.isClear) {
                            const now = new Date();
                            const localDate = new Date(now.getFullYear(), now.getMonth(), now.getDate());
                            baseDate = localDate;
                            resetTotals();
                            dateInput.value = localDate.toISOString().split('T')[0];
                            display.innerHTML = 'Reset to today: <strong>0d</strong>';
                            return;
                        }

                        // Add or subtract from correct total
                        const sign = delta < 0 ? -1 : 1;
                        if (opt.years) totalYears += sign * Math.abs(delta);
                        if (opt.months) totalMonths += sign * Math.abs(delta);
                        if (opt.weeks) totalWeeks += sign * Math.abs(delta);
                        if (opt.days) totalDays += sign * Math.abs(delta);

                        updateDisplayAndDate();
                    };

                    btn.addEventListener('click', e => {
                        e.preventDefault();
                        const delta = e.shiftKey ? -1 : 1;
                        // delta multiplied by the actual unit amount
                        const multiplier = opt.years || opt.months || opt.weeks || opt.days || 0;
                        handleOffset(delta * multiplier);
                    });

                    btn.addEventListener('contextmenu', e => {
                        e.preventDefault();
                        if (opt.isClear) {
                            baseDate = null;
                            resetTotals();
                            const today = new Date();
                            dateInput.value = today.toISOString().split('T')[0];
                            display.innerHTML = 'Reset to today: <strong>0d</strong>';
                        } else {
                            // Subtract the value for this button
                            const multiplier = opt.years || opt.months || opt.weeks || opt.days || 0;
                            handleOffset(-1 * multiplier);
                        }
                    });

                    row.appendChild(btn);
                });
                container.appendChild(row);
            });
        }

        function popupPage(vheight, vwidth, varpage) { //open a new popup window
            var page = "" + varpage;
            windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes";
            var popup = window.open(page, "attachment", windowprops);
            if (popup != null) {
                if (popup.opener == null) {
                    popup.opener = self;
                }
            }
        }

        function selectprovider(s) {
            if (self.location.href.lastIndexOf("&providerview=") > 0) a = self.location.href.substring(0, self.location.href.lastIndexOf("&providerview="));
            else a = self.location.href;
            self.location.href = a + "&providerview=" + s.options[s.selectedIndex].value;
        }

        function openBrWindow(theURL, winName, features) {
            window.open(theURL, winName, features);
        }

        function setfocus() {
            this.focus();
            document.ADDAPPT.keyword.focus();
            document.ADDAPPT.keyword.select();
            addQuickPick();
        }

        function initResize() {
            window.addEventListener("resize", resizeTextMessage);
            resizeTextMessage();
        }

        /****
         * This function resizes the messageBox so that the overall browser window is filled.
         ****/
        function resizeTextMessage() {
            const messageBox = document.getElementById("ticklerMessage");
            //this formula checks for empty space at the bottom, less the 20 px that corresponds to the margin-bottom
            const newHeight = messageBox.offsetHeight + window.innerHeight - document.body.clientHeight - 20;
            //only resize if the new height will be greater than 50 pixels, the original default height.
            if (newHeight > 50) messageBox.style.height = newHeight + "px";
        }

        function validate(form, writeToEncounter = false) {
            if (validateDemoNo()<%= caisiEnabled ? " && validateSelectedProgram()" : "" %>) {
                if (writeToEncounter) {
                    form.action = "<%= request.getContextPath() %>/tickler/dbTicklerAdd.jsp?writeToEncounter=true";
                } else {
                    form.action = "<%= request.getContextPath() %>/tickler/dbTicklerAdd.jsp?updateTicklerNav=true";
                }
                form.submit();
            }
        }

        function validateSelectedProgram() {
            if (document.serviceform.program_assigned_to.value === "none") {
                document.getElementById("error").insertAdjacentText("beforeend", '<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.msgNoProgramSelected"/>');
                document.getElementById("error").style.display = 'block';
                return false;
            }
            return true;
        }

        function IsDate(value) {
            let dateWrapper = new Date(value);
            return !isNaN(dateWrapper.getDate());
        }

        function validateDemoNo() {
            if (document.serviceform.demographic_no.value == "") {
                document.getElementById("error").insertAdjacentText("beforeend", '<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.msgInvalidDemographic"/>');
                document.getElementById("error").style.display = 'block';
                return false;
            } else {
                if (document.serviceform.xml_appointment_date.value == "" || !IsDate(document.serviceform.xml_appointment_date.value)) {
                    document.getElementById("error").insertAdjacentText("beforeend", '<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.msgMissingDate"/>');
                    document.getElementById("error").style.display = 'block';
                    return false;
                }
                <% if (ca.openosp.openo.commn.IsPropertiesOn.isMultisitesEnable()) { %>
                else if (document.serviceform.site.value == "none" || document.serviceform.site.value == "0") {
                    document.getElementById("error").insertAdjacentText("beforeend", "Must assign task to a provider.");
                    document.getElementById("error").style.display = 'block';
                    return false;
                }
                <% } %>
                else {
                    return true;
                }
            }
        }

        function refresh() {
            var u = self.location.href;
            if (u.lastIndexOf("view=1") > 0) {
                self.location.href = u.substring(0, u.lastIndexOf("view=1")) + "view=0" + u.substring(u.lastIndexOf("view=1") + 6);
            } else {
                history.go(0);
            }
        }
        </script>

        <link href="<%=request.getContextPath() %>/library/bootstrap/3.0.0/css/bootstrap.css" rel="stylesheet"
              type="text/css">
        <style media="all">
            .tickler-label {
                color: #003366;
                font-weight: bold;
            }

            table {
                width: 100%;
            }

            * {
                font-size: 12px !important;
            }
            #quickPickDateOptions {
                display: block !important;
            }
            #quickPickDateOptions > div {
                display: flex;
                gap: 6px;
                margin-bottom: 6px;
            }
            #quickPickDateOptions button {
                font-size: 0.7em;
                padding: 3px 6px;
                cursor: pointer;
            }
        </style>
    </head>

    <body onload="setfocus();initResize()">
    <table>
        <tr style="background-color: black">
            <td class="table-condensed"
                style="text-align:left; padding:10px; font-weight: 900; height:40px; font-size: large; font-family: arial, sans-serif; color: white">
                Add <fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.msgTickler"/></td>
        </tr>
    </table>

    <div class="container-fluid well">
        <%
            String searchMode = request.getParameter("search_mode");
            if (searchMode == null || searchMode.isEmpty()) {
                searchMode = OscarProperties.getInstance().getProperty("default_search_mode", "search_name");
            }
            ChartNo = bFirstDisp ? "" : request.getParameter("chart_no") == null ? "" : request.getParameter("chart_no");
        %>
        <form name="ADDAPPT" method="post" action="<%= request.getContextPath() %>/appointment/appointmentcontrol.jsp">
            <input type="hidden" name="orderby" value="last_name">
            <input type="hidden" name="search_mode" value="<%=Encode.forHtmlAttribute(searchMode)%>">
            <input type="hidden" name="originalpage" value="<%= request.getContextPath() %>/tickler/ticklerAdd.jsp">
            <input type="hidden" name="limit1" value="0">
            <input type="hidden" name="limit2" value="5">
            <input type="hidden" name="displaymode" value="Search ">
            <input type="hidden" name="appointment_date" value="2002-10-01">
            <input type="hidden" name="status" value="t">
            <input type="hidden" name="start_time" value="10:45">
            <input type="hidden" name="type" value="">
            <input type="hidden" name="duration" value="15">
            <input type="hidden" name="end_time" value="10:59">
            <input type="hidden" name="demographic_no" readonly value="">
            <input type="hidden" name="location" tabindex="4" value="">
            <input type="hidden" name="resources" tabindex="5" value="">
            <input type="hidden" name="user_id" readonly value="oscardoc, doctor">
            <input type="hidden" name="dboperation" value="search_demorecord">
            <input type="hidden" name="createdatetime" readonly value="2002-10-1 17:53:50">
            <input type="hidden" name="provider_no" value="115">
            <input type="hidden" name="creator" value="oscardoc, doctor">
            <input type="hidden" name="remarks" value="">
            <input type="hidden" name="parentAjaxId" value="<%=Encode.forHtmlAttribute(parentAjaxId)%>">
            <input type="hidden" name="updateParent" value="<%=Encode.forHtmlAttribute(updateParent)%>">
            <table class="table-condensed">
                <tr>
                    <td colspan="2">
                        <div id="error" class="alert alert-error" style="display:none;"></div>
                    </td>
                </tr>
                <tr>
                    <td style="width: 35%;" class="tickler-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.formDemoName"/>:</td>
                    <td style="width: 65%;">

                        <div class="input-group">
                            <input type="text" class="form-control" name="keyword" placeholder="Search Demographic"
                                   size="25" value="<%=Encode.forHtmlAttribute(demoName)%>">
                            <span class="input-group-btn">
                                <input type="submit" name="Submit" class="btn btn-default"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.btnSearch"/>">
                            </span>
                        </div>

                    </td>
                </tr>
            </table>
        </form>
        <form name="serviceform" method="post">
            <input type="hidden" name="parentAjaxId" value="<%=Encode.forHtmlAttribute(parentAjaxId)%>">
            <input type="hidden" name="updateParent" value="<%=Encode.forHtmlAttribute(updateParent)%>">
            <input type="hidden" name="user_no" value="<%=Encode.forHtmlAttribute(user_no)%>">
            <input type="hidden" name="writeToEncounter" value="<%=Encode.forHtmlAttribute(writeToEncounter.toString())%>">
            <table class="table-condensed">

                <tr>
                    <td style="width: 35%;" class="tickler-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.formChartNo"/>:</td>
                    <td style="width: 65%;"><span><input type="hidden" name="demographic_no"
                                                 value="<%=bFirstDisp ? "" : request.getParameter("demographic_no").equals("") ? "" : Encode.forHtmlAttribute(request.getParameter("demographic_no"))%>"><%=Encode.forHtml(ChartNo)%></span>
                    </td>
                </tr>

                <tr>
                    <td class="tickler-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.formServiceDate"/></td>
                    <td><input type="date" class="form-control" name="xml_appointment_date"
                               value="<%=Encode.forHtmlAttribute(xml_appointment_date)%>">
                            <div id="quickPickDateOptions" class="grid">
                                <!-- Quick pick will be added here using JavaScript -->
                            </div>
                        </td>
                </tr>
                <tr>
                    <td class="tickler-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.Priority"/>:</td>
                    <td>
                        <select name="priority" class="form-control">
                            <option value="<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.priority.high"/>" <%=priority.equals("High")?"selected":""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.priority.high"/>
                            <option value="<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.priority.normal"/>" <%=priority.equals("Normal")?"selected":""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.priority.normal"/>
                            <option value="<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.priority.low"/>" <%=priority.equals("Low")?"selected":""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.priority.low"/>
                        </select>
                    </td>
                </tr>

                <tr>
                    <td class="tickler-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.assignTaskTo"/>:</td>
                    <td>
                        <% if (ca.openosp.openo.commn.IsPropertiesOn.isMultisitesEnable()) { // multisite start ==========================================
                            SiteDao siteDao = (SiteDao) WebApplicationContextUtils.getWebApplicationContext(application).getBean(SiteDao.class);
                            List<Site> sites = siteDao.getActiveSitesByProviderNo(user_no);
                            String appNo = (String) session.getAttribute("cur_appointment_no");
                            String location = null;
                            if (appNo != null) {
                                Appointment a = appointmentDao.find(Integer.parseInt(appNo));
                                if (a != null) {
                                    location = a.getLocation();
                                }

                            }
                        %>
                        <script>
                            var _providers = [];

                            <%
                            String taskToName = "";

                            if(defaultTaskAssignee.equals("mrp")) {
                                taskToName = "Preference set to MRP, attach a patient.";
                            }
                            if(!taskTo.isEmpty()) {
                                taskToName = providerDao.getProviderNameLastFirst(taskTo);
                            }
                            Site site = null;
                            for (int i=0; i<sites.size(); i++) {
                                Set<Provider> siteProviders = sites.get(i).getProviders();
                                List<Provider>  siteProvidersList = new ArrayList<Provider> (siteProviders);
                                 Collections.sort(siteProvidersList,(new Provider()).ComparatorName());%>
                            _providers["<%= sites.get(i).getName() %>"] = "<% Iterator<Provider> iter = siteProvidersList.iterator();
	while (iter.hasNext()) {
		Provider p=iter.next();
		if ("1".equals(p.getStatus())) {
	%><option value='<%= p.getProviderNo() %>'><%= p.getLastName() %>, <%= p.getFirstName() %></option><% }} %>";
                            <%
                                } %>

                            function changeSite(sel) {
                                sel.form.task_assigned_to.innerHTML = sel.value == "none" ? "" : _providers[sel.value];
                                sel.style.backgroundColor = sel.options[sel.selectedIndex].style.backgroundColor;
                            }
                        </script>

                        <div id="selectWrapper">
                            <select id="site" class="form-control" name="site" onchange="changeSite(this)">
                                <option value="none" style="background-color: white">---select clinic---</option>
                                <%
                                    for (int i = 0; i < sites.size(); i++) {
                                %>
                                <option value="<%=Encode.forHtmlAttribute(sites.get(i).getName())%>"
                                        style="background-color:<%=Encode.forCssString(sites.get(i).getBgColor())%>"><%=Encode.forHtmlContent(sites.get(i).getName())%>
                                </option>
                                <% } %>
                            </select>

                            <select name="task_assigned_to" id="task_assigned_to" class="form-control"></select>

                            <h4 id="preferenceLink" style="display:none"><small><a href="#" onclick="toggleWrappers()">[preference]</a></small>
                            </h4>
                        </div>

                        <div id="nameWrapper" style="display:none">
                            <h4><%=Encode.forHtml(taskToName)%> <small><a href="#" onclick="toggleWrappers()">[change]</a></small></h4>
                            <input type="hidden" id="taskToBin" value="<%=Encode.forHtmlAttribute(taskTo)%>">
                            <input type="hidden" id="taskToNameBin" value="<%=Encode.forHtmlAttribute(taskToName)%>">
                        </div>
                        <script>
                            document.getElementById("site").value = '<%= site==null?"none":site.getSiteId() %>';
                            changeSite(document.getElementById("site"));
                        </script>

                        <% if (prop != null) {%>
                        <script>
                            //prop exists so hide selectWrapper
                            document.getElementById("selectWrapper").style.display = "none";
                            document.getElementById("nameWrapper").style.display = "block";
                            document.getElementById("preferenceLink").style.display = "inline-block";

                            var taskToValue = document.getElementById("taskToBin").value;
                            var taskToName = document.getElementById('taskToNameBin').value;

                            function toggleWrappers() {
                                if (document.getElementById("selectWrapper").style.display == "none") {
                                    document.getElementById("selectWrapper").style.display = "block";
                                    document.getElementById("nameWrapper").style.display = "none";
                                } else {
                                    document.getElementById("selectWrapper").style.display = "none";
                                    document.getElementById("nameWrapper").style.display = "block";
                                }
                            }

                            _providers.push("<option value=\"" + taskToValue + "\" selected>" + taskToName + "</option>");

                            var newItemKey = _providers.length - 1;

                            var selSite = document.getElementById('site');
                            var optSite = document.createElement('option');
                            optSite.appendChild(document.createTextNode("** preference **"));
                            optSite.value = newItemKey;
                            optSite.setAttribute('selected', 'selected');
                            selSite.appendChild(optSite);
                            changeSite(selSite);
                        </script>
                        <%}%>

                        <% // multisite end ==========================================
                        } else {
                        %>

                        <select name="task_assigned_to" class="form-control">
                            <% String proFirst = "";
                                String proLast = "";
                                String proOHIP = "";

                                for (Provider p : providerDao.getActiveProviders()) {

                                    proFirst = p.getFirstName();
                                    proLast = p.getLastName();
                                    proOHIP = p.getProviderNo();

                            %>
                            <option value="<%=Encode.forHtmlAttribute(proOHIP)%>" <%=taskTo.equals(proOHIP) ? "selected" : ""%>><%=Encode.forHtmlContent(proLast)%>
                                , <%=Encode.forHtmlContent(proFirst)%>
                            </option>
                            <%
                                }
                            %>
                        </select>
                        <% } %>

                        <input type="hidden" name="docType" value="<%=Encode.forHtmlAttribute(request.getParameter("docType") != null ? request.getParameter("docType") : "")%>">
                        <input type="hidden" name="docId" value="<%=Encode.forHtmlAttribute(request.getParameter("docId") != null ? request.getParameter("docId") : "")%>">
                    </td>
                </tr>
                <tr>
                    <td class="tickler-label"><a href="#" onclick="openBrWindow('./ticklerSuggestedText.jsp','','width=680,height=400')" style="font-weight:bold"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerEdit.suggestedText"/></a>:</td>
                    <td>
                        <select name="suggestedText" class="form-control" onchange="pasteMessageText()">
                            <option value="">---</option>
                            <%
                                TicklerTextSuggestDao ticklerTextSuggestDao = SpringUtils.getBean(TicklerTextSuggestDao.class);
                                for (TicklerTextSuggest tTextSuggest : ticklerTextSuggestDao.getActiveTicklerTextSuggests()) {
                            %>
                            <option><%=Encode.forHtmlContent(tTextSuggest.getSuggestedText())%></option>
                            <% } %>
                        </select>
                    </td>
                </tr>

                <tr>
                    <td class="tickler-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.formReminder"/>:</td>
                    <td><textarea name="ticklerMessage" id="ticklerMessage" class="form-control"></textarea>
                    </td>
                </tr>
                <tr>
                    <td colspan="2"><input type="button" name="Button" class="btn btn-primary"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.btnSubmit"/>"
                               onclick="validate(this.form);">
                        <input type="button" name="Button" class="btn btn-secondary"
                               value="Write to eChart and submit"
                               onclick="validate(this.form, true)">
                        <input type="button" name="Button" class="btn btn-danger"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.btnCancel"/>"
                               onclick="window.close()">
                    </td>
                </tr>

            </table>
        </form>
    </div>
    </body>
</html>
