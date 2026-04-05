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
    - Form submission via hidden iframe ('ticklerSubmitFrame') to allow post-submit
      window manipulation. The iframe.onload callback reads a server-side success
      marker, then drives the opener refresh and window close. On success, broadcasts
      to BroadcastChannel('carlos_tickler_refresh_' + demographicNo) so ticklerMain.jsp and
      newEncounterLayout.jsp for the same patient can reload without a full page refresh.

    Parameters:
    - demographic_no:       Patient demographic number
    - xml_appointment_date: Initial service/appointment date (YYYY-MM-DD)
    - taskTo:               Default task assignee provider number
    - priority:             Tickler priority (High/Normal/Low)
    - parentAjaxId:         Encounter navbar element ID for update notification
    - updateParent:         Whether to update the parent encounter window (true/false)
    - recall:               If present, intended to mark this as a recall tickler.
                            NOTE: This parameter is currently read but not yet
                            propagated to the tickler model. See dbTicklerAdd.jsp.
    - docType:              Optional document type for linking
    - docId:                Optional document ID for linking

    @since CARLOS EMR 2026
--%>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DemographicDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.SiteDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.TicklerTextSuggestDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Appointment" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Site" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.TicklerTextSuggest" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.UserProperty" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.MyDateFormat" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Calendar" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.GregorianCalendar" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Set" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
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
    Boolean caisiEnabled = CarlosProperties.getInstance().isPropertyActive("caisi");
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

        <%
            java.util.ResourceBundle oscarBundle = java.util.ResourceBundle.getBundle("oscarResources", request.getLocale());
        %>
        <script>
        // i18n messages for JavaScript — encoded via Encode.forJavaScript() to prevent XSS and broken JS strings
        const i18nQuickPickFrom = '<%=Encode.forJavaScript(oscarBundle.getString("tickler.ticklerAdd.quickPickFrom"))%>';
        const i18nQuickPickReset = '<%=Encode.forJavaScript(oscarBundle.getString("tickler.ticklerAdd.quickPickReset"))%>';
        const i18nQuickPickTooltip = '<%=Encode.forJavaScript(oscarBundle.getString("tickler.ticklerAdd.quickPickBtnTooltip"))%>';
        const i18nSelectPreference = '<%=Encode.forJavaScript(oscarBundle.getString("tickler.ticklerAdd.selectPreference"))%>';

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
            display.style.color = '#337ab7';
            display.innerHTML = '&nbsp;'; // Reserve vertical space
            display.style.minHeight = '1.5em'; // Adjust height to match expected line height
            container.parentNode.insertBefore(display, container);

            // Parse a YYYY-MM-DD string as local midnight (not UTC midnight)
            function parseLocalDate(str) {
                const parts = str.split('-').map(Number);
                return new Date(parts[0], parts[1] - 1, parts[2]);
            }

            // Format a Date as YYYY-MM-DD using local date components (not UTC)
            function formatLocalDate(date) {
                const y = date.getFullYear();
                const m = String(date.getMonth() + 1).padStart(2, '0');
                const d = String(date.getDate()).padStart(2, '0');
                return y + '-' + m + '-' + d;
            }

            function parseDateInput() {
                const val = dateInput.value;
                if (!val) return new Date();
                const d = parseLocalDate(val);
                return isNaN(d) ? new Date() : d;
            }
            baseDate = parseDateInput();

            // Reset base date if user manually edits the date field,
            // so subsequent quick-pick clicks apply offsets from the new value.
            dateInput.addEventListener('change', () => {
                baseDate = parseDateInput();
                resetTotals();
                display.innerHTML = '&nbsp;';
            });

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

                dateInput.value = formatLocalDate(date);

                // Build display string for total offset
                const parts = [];
                if (totalYears) parts.push(totalYears + 'y');
                if (totalMonths) parts.push(totalMonths + 'm');
                if (totalWeeks) parts.push(totalWeeks + 'w');
                if (totalDays) parts.push(totalDays + 'd');
                if (parts.length === 0) parts.push('0d');

                display.innerHTML = i18nQuickPickFrom + " " + formatLocalDate(baseDate) + ":&nbsp;&nbsp;&nbsp;&nbsp;<strong>" + parts.join(' ') + "</strong>";
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
                    btn.title = i18nQuickPickTooltip;

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
                            dateInput.value = formatLocalDate(localDate);
                            display.innerHTML = i18nQuickPickReset + ': <strong>0d</strong>';
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
                            dateInput.value = formatLocalDate(today);
                            display.innerHTML = i18nQuickPickReset + ': <strong>0d</strong>';
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

        function selectprovider(s) {
            if (self.location.href.lastIndexOf("&providerview=") > 0) a = self.location.href.substring(0, self.location.href.lastIndexOf("&providerview="));
            else a = self.location.href;
            self.location.href = a + "&providerview=" + s.options[s.selectedIndex].value;
        }

        function openBrWindow(theURL, winName, features) {
            window.open(theURL, winName, features);
        }

        function initTicklerAdd() {
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

        function enableSubmitButtons() {
            var btns = document.querySelectorAll('.action-bar-bottom .btn-primary, .action-bar-bottom .btn-secondary');
            btns.forEach(function(b) { b.disabled = false; });
        }

        function validate(form, writeToEncounter) {
            writeToEncounter = writeToEncounter || false;
            if (validateDemoNo()<%= caisiEnabled ? " && validateSelectedProgram()" : "" %>) {
                // Disable submit buttons to prevent double-submit
                var btns = document.querySelectorAll('.action-bar-bottom .btn-primary, .action-bar-bottom .btn-secondary');
                btns.forEach(function(b) { b.disabled = true; });

                // Create iframe once; reassign onload every call to capture current writeToEncounter
                var submitTimeout;
                var iframe = document.getElementById('ticklerSubmitFrame');
                if (!iframe) {
                    iframe = document.createElement('iframe');
                    iframe.id = 'ticklerSubmitFrame';
                    iframe.name = 'ticklerSubmitFrame';
                    iframe.style.display = 'none';
                    document.body.appendChild(iframe);
                    // NOTE: onerror fires only for network-level failures (DNS, connection refused).
                    // HTTP 4xx/5xx responses trigger onload instead — error detection is handled there.
                    iframe.onerror = function() {
                        console.error('[ticklerAdd] iframe network error during form submission');
                        alert('<%= org.owasp.encoder.Encode.forJavaScript(oscarBundle.getString("tickler.ticklerAdd.errorNetworkFailed")) %>');
                        enableSubmitButtons();
                    };
                }

                // Reassign onload on every call so writeToEncounter is always current
                iframe.onload = function() {
                    clearTimeout(submitTimeout);
                    // Skip the initial about:blank load before the form posts
                    try {
                        if (iframe.contentWindow.location.href === 'about:blank') return;
                    } catch (e) {
                        // SecurityError: cross-origin redirect — almost certainly a session timeout
                        console.error('[ticklerAdd] iframe cross-origin access blocked — possible session expiry:', e);
                        alert('<%= org.owasp.encoder.Encode.forJavaScript(oscarBundle.getString("tickler.ticklerAdd.errorSessionExpired")) %>');
                        enableSubmitButtons();
                        return;
                    }
                    // Verify server confirmed save before proceeding
                    try {
                        var saveOk = iframe.contentDocument && iframe.contentDocument.getElementById('tickler-save-ok');
                        if (!saveOk) {
                            console.error('[ticklerAdd] Server did not confirm tickler save — possible server-side error');
                            alert('<%= org.owasp.encoder.Encode.forJavaScript(oscarBundle.getString("tickler.ticklerAdd.errorSaveFailed")) %>');
                            enableSubmitButtons();
                            return;
                        }
                        if (iframe.contentDocument.getElementById('tickler-save-ok-link-failed')) {
                            alert('<%= org.owasp.encoder.Encode.forJavaScript(oscarBundle.getString("tickler.ticklerAdd.warnLinkFailed")) %>');
                        }
                        // Warn if the encounter note write failed (tickler itself was saved OK)
                        if (iframe.contentDocument.getElementById('tickler-write-encounter-failed')) {
                            alert('<%= org.owasp.encoder.Encode.forJavaScript(oscarBundle.getString("tickler.ticklerAdd.warnEncounterWriteFailed")) %>');
                        }
                    } catch (e) {
                        console.error('[ticklerAdd] Cannot read iframe response body — possible session expiry:', e);
                        alert('<%= org.owasp.encoder.Encode.forJavaScript(oscarBundle.getString("tickler.ticklerAdd.errorSessionExpired")) %>');
                        enableSubmitButtons();
                        return;
                    }
                    if (writeToEncounter) {
                        // Write to encounter: navigate the opener window with updateParent flag
                        try {
                            if (window.opener && !window.opener.closed) {
                                var ref = window.opener.location.href;
                                if (ref.indexOf("updateParent") === -1) {
                                    ref = ref + (ref.indexOf("?") > -1 ? "&" : "?") + "updateParent=true";
                                }
                                window.opener.location = ref;
                            }
                        } catch (e) {
                            console.error('[ticklerAdd] Failed to reload opener for write-to-encounter:', e);
                        }
                    } else {
                        // Regular save: partial reload via reloadNav
                        try {
                            if (window.opener && !window.opener.closed &&
                                typeof window.opener.reloadNav === 'function') {
                                window.opener.reloadNav('tickler');
                            }
                        } catch (e) {
                            console.error('[ticklerAdd] Failed to call opener.reloadNav:', e);
                        }
                    }
                    // Always broadcast for cross-window listeners (e.g. ticklerMain.jsp)
                    try {
                        var demoNo = '<%=org.owasp.encoder.Encode.forJavaScript(request.getParameter("demographic_no") != null ? request.getParameter("demographic_no") : "0")%>';
                        var bc = new BroadcastChannel('carlos_tickler_refresh_' + demoNo);
                        bc.postMessage({ action: 'refresh' });
                        bc.close();
                    } catch (e) {
                        console.error('[ticklerAdd] BroadcastChannel broadcast failed:', e);
                        // Fallback: reload the opener directly so the tickler list stays current
                        try {
                            if (window.opener && !window.opener.closed) {
                                window.opener.location.reload();
                            }
                        } catch (fallbackErr) {
                            console.warn('[ticklerAdd] Could not reload opener — user may need to refresh manually:', fallbackErr);
                        }
                    }
                    setTimeout(function() { window.close(); }, 500);
                };

                // Set form action based on mode
                if (writeToEncounter) {
                    form.action = "<%= request.getContextPath() %>/tickler/DbTicklerAdd.do?writeToEncounter=true";
                } else {
                    form.action = "<%= request.getContextPath() %>/tickler/DbTicklerAdd.do";
                }
                form.target = 'ticklerSubmitFrame';
                form.submit();
                submitTimeout = setTimeout(function() {
                    console.error('[ticklerAdd] Form submission timed out after 30s');
                    alert('<%= org.owasp.encoder.Encode.forJavaScript(oscarBundle.getString("tickler.ticklerAdd.errorSaveFailed")) %>');
                    enableSubmitButtons();
                }, 30000);
            }
        }

        function validateSelectedProgram() {
            if (document.serviceform.program_assigned_to.value === "none") {
                document.getElementById("error").insertAdjacentText("beforeend", '<%=Encode.forJavaScript(oscarBundle.getString("tickler.ticklerAdd.msgNoProgramSelected"))%>');
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
                document.getElementById("error").insertAdjacentText("beforeend", '<%=Encode.forJavaScript(oscarBundle.getString("tickler.ticklerAdd.msgInvalidDemographic"))%>');
                document.getElementById("error").style.display = 'block';
                return false;
            } else {
                if (document.serviceform.xml_appointment_date.value == "" || !IsDate(document.serviceform.xml_appointment_date.value)) {
                    document.getElementById("error").insertAdjacentText("beforeend", '<%=Encode.forJavaScript(oscarBundle.getString("tickler.ticklerAdd.msgMissingDate"))%>');
                    document.getElementById("error").style.display = 'block';
                    return false;
                }
                <% if (io.github.carlos_emr.carlos.commn.IsPropertiesOn.isMultisitesEnable()) { %>
                else if (!document.serviceform.task_assigned_to ||
                         document.serviceform.task_assigned_to.options.length === 0 ||
                         document.serviceform.task_assigned_to.value === "") {
                    document.getElementById("error").insertAdjacentText("beforeend", '<%=Encode.forJavaScript(oscarBundle.getString("tickler.ticklerAdd.msgMustAssignProvider"))%>');
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

        <%@ include file="/includes/global-head.jspf" %>
        <style>
            /* Links — CARLOS primary blue */
            a { color: var(--carlos-primary); }
            a:hover { color: #28619a; }

            .tickler-label {
                color: var(--carlos-primary);
                font-weight: 600;
                font-size: 13px;
                white-space: nowrap;
            }

            /* Quick-pick date grid — CARLOS tokens */
            #quickPickDateOptions {
                display: block !important;
                margin-top: 6px;
            }
            #quickPickDateOptions > div {
                display: flex;
                gap: 4px;
                margin-bottom: 4px;
            }
            #quickPickDateOptions button {
                background-color: var(--carlos-bg-light);
                border: 1px solid var(--carlos-border);
                color: var(--carlos-text);
                font-size: 11px;
                padding: 3px 6px;
                cursor: pointer;
                border-radius: 3px;
                text-decoration: none;
            }
            #quickPickDateOptions button:hover {
                background-color: var(--carlos-primary);
                color: #fff;
            }

            /* Action bar */
            .action-bar-bottom {
                background: var(--carlos-bg-light);
                border-top: 1px solid var(--carlos-border);
                padding: 10px 15px;
                margin-top: 10px;
                display: flex;
                align-items: center;
                gap: 8px;
            }
        </style>
    </head>

    <body onload="initTicklerAdd();initResize()">
    <div class="container" style="max-width: 860px;">
        <div class="page-header-bar">
            <h2 class="page-header-title"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.titleHeading"/></h2>
        </div>
        <%
            String searchMode = request.getParameter("search_mode");
            if (searchMode == null || searchMode.isEmpty()) {
                searchMode = CarlosProperties.getInstance().getProperty("default_search_mode", "search_name");
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
            <table class="table table-sm">
                <tr>
                    <td colspan="2">
                        <div id="error" class="alert alert-danger" style="display:none;" role="alert"></div>
                    </td>
                </tr>
                <tr>
                    <td style="width: 35%;" class="tickler-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.formDemoName"/>:</td>
                    <td style="width: 65%;">

                        <div class="input-group">
                            <input type="text" class="form-control" name="keyword" placeholder="<fmt:message key='tickler.ticklerAdd.phSearchDemographic'/>"
                                   size="25" value="<%=Encode.forHtmlAttribute(demoName)%>">
                            <input type="submit" name="Submit" class="btn btn-primary"
                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.btnSearch"/>">
                        </div>

                    </td>
                </tr>
            </table>
        </form>
        <form name="serviceform" method="post" action="<%=request.getContextPath()%>/tickler/DbTicklerAdd.do">
            <input type="hidden" name="parentAjaxId" value="<%=Encode.forHtmlAttribute(parentAjaxId)%>">
            <input type="hidden" name="updateParent" value="<%=Encode.forHtmlAttribute(updateParent)%>">
            <input type="hidden" name="writeToEncounter" value="<%=Encode.forHtmlAttribute(writeToEncounter.toString())%>">
            <input type="hidden" name="user_no" value="<%=Encode.forHtmlAttribute(user_no)%>">

            <table class="table table-sm">

                <tr>
                    <td style="width: 35%;" class="tickler-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.formChartNo"/>:</td>
                    <%
                        String demoNoParam = request.getParameter("demographic_no");
                        String demoNoValue = (bFirstDisp || demoNoParam == null || demoNoParam.isEmpty()) ? "" : Encode.forHtmlAttribute(demoNoParam);
                    %>
                    <td style="width: 65%;"><span><input type="hidden" name="demographic_no"
                                                 value="<%=demoNoValue%>"><%=Encode.forHtml(ChartNo)%></span>
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
                        <select name="priority" class="form-select">
                            <option value="<%=Encode.forHtmlAttribute(oscarBundle.getString("tickler.ticklerMain.priority.high"))%>" <%=priority.equals("High")?"selected":""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.priority.high"/></option>
                            <option value="<%=Encode.forHtmlAttribute(oscarBundle.getString("tickler.ticklerMain.priority.normal"))%>" <%=priority.equals("Normal")?"selected":""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.priority.normal"/></option>
                            <option value="<%=Encode.forHtmlAttribute(oscarBundle.getString("tickler.ticklerMain.priority.low"))%>" <%=priority.equals("Low")?"selected":""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.priority.low"/></option>
                        </select>
                    </td>
                </tr>

                <tr>
                    <td class="tickler-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.assignTaskTo"/>:</td>
                    <td>
                        <% if (io.github.carlos_emr.carlos.commn.IsPropertiesOn.isMultisitesEnable()) { // multisite start ==========================================
                            SiteDao siteDao = (SiteDao) WebApplicationContextUtils.getWebApplicationContext(application).getBean(SiteDao.class);
                            List<Site> sites = siteDao.getActiveSitesByProviderNo(user_no);
                            String appNo = (String) session.getAttribute("cur_appointment_no");
                            String location = null;
                            if (appNo != null) {
                                try {
                                    Appointment a = appointmentDao.find(Integer.parseInt(appNo));
                                    if (a != null) {
                                        location = a.getLocation();
                                    }
                                } catch (NumberFormatException e) {
                                    // Malformed appointment number in session — skip location lookup
                                }
                            }
                        %>
                        <script>
                            var _providers = [];

                            <%
                            String taskToName = "";
                            boolean taskToIsMRP = false;

                            if(defaultTaskAssignee.equals("mrp")) {
                                taskToName = "Preference set to MRP, attach a patient.";
                                taskToIsMRP = true;
                            }
                            if(!taskTo.isEmpty()) {
                                taskToName = providerDao.getProviderNameLastFirst(taskTo);
                                taskToIsMRP = false;
                            }
                            Site site = null;
                            for (int i=0; i<sites.size(); i++) {
                                Set<Provider> siteProviders = sites.get(i).getProviders();
                                List<Provider>  siteProvidersList = new ArrayList<Provider> (siteProviders);
                                 Collections.sort(siteProvidersList,(new Provider()).ComparatorName());%>
                            _providers["<%= Encode.forJavaScript(sites.get(i).getName()) %>"] = "<% Iterator<Provider> iter = siteProvidersList.iterator();
	while (iter.hasNext()) {
		Provider p=iter.next();
		if ("1".equals(p.getStatus())) {
	%><option value='<%= Encode.forJavaScript(Encode.forHtmlAttribute(p.getProviderNo())) %>'><%= Encode.forJavaScript(Encode.forHtml(p.getLastName())) %>, <%= Encode.forJavaScript(Encode.forHtml(p.getFirstName())) %></option><% }} %>";
                            <%
                                } %>

                            function changeSite(sel) {
                                sel.form.task_assigned_to.innerHTML = sel.value == "none" ? "" : _providers[sel.value];
                                sel.style.backgroundColor = sel.options[sel.selectedIndex].style.backgroundColor;
                            }
                        </script>

                        <div id="selectWrapper">
                            <select id="site" class="form-select" name="site" onchange="changeSite(this)">
                                <option value="none" style="background-color: white"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.selectClinic"/></option>
                                <%
                                    for (int i = 0; i < sites.size(); i++) {
                                %>
                                <option value="<%=Encode.forHtmlAttribute(sites.get(i).getName())%>"
                                        style="background-color:'<%=Encode.forCssString(sites.get(i).getBgColor())%>'"><%=Encode.forHtmlContent(sites.get(i).getName())%>
                                </option>
                                <% } %>
                            </select>

                            <select name="task_assigned_to" id="task_assigned_to" class="form-select"></select>

                            <h4 id="preferenceLink" style="display:none"><small><a href="#" onclick="toggleWrappers()"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.linkPreference"/></a></small>
                            </h4>
                        </div>

                        <div id="nameWrapper" style="display:none">
                            <h4><% if (taskToIsMRP) { %><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.msgPreferenceMRP"/><% } else { %><%=Encode.forHtml(taskToName)%><% } %> <small><a href="#" onclick="toggleWrappers()"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.linkChange"/></a></small></h4>
                            <input type="hidden" id="taskToBin" value="<%=Encode.forHtmlAttribute(taskTo)%>">
                            <% String taskToNameBinValue = taskToIsMRP ? oscarBundle.getString("tickler.ticklerAdd.msgPreferenceMRP") : taskToName; %>
                            <input type="hidden" id="taskToNameBin" value="<%=Encode.forHtmlAttribute(taskToNameBinValue)%>">
                        </div>
                        <script>
                            document.getElementById("site").value = '<%= site==null?"none":Encode.forJavaScript(site.getName()) %>';
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

                            const prefOption = document.createElement('option');
                            prefOption.value = taskToValue;
                            prefOption.textContent = taskToName;
                            prefOption.selected = true;
                            _providers.push(prefOption.outerHTML);

                            var newItemKey = _providers.length - 1;

                            var selSite = document.getElementById('site');
                            var optSite = document.createElement('option');
                            optSite.appendChild(document.createTextNode(i18nSelectPreference));
                            optSite.value = newItemKey;
                            optSite.setAttribute('selected', 'selected');
                            selSite.appendChild(optSite);
                            changeSite(selSite);
                        </script>
                        <%}%>

                        <% // multisite end ==========================================
                        } else {
                        %>

                        <select name="task_assigned_to" class="form-select">
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
                        <select name="suggestedText" class="form-select" onchange="pasteMessageText()">
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

            </table>
            <div class="action-bar-bottom">
                <input type="button" name="Button" class="btn btn-primary"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.btnSubmit"/>"
                       onclick="validate(this.form);">
                <input type="button" name="Button" class="btn btn-secondary"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.btnWriteSubmit"/>"
                       onclick="validate(this.form, true)">
                <input type="button" name="Button" class="btn btn-secondary"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnBack"/>"
                       onclick="window.close()">
            </div>
        </form>
    </div>
    </body>
</html>
