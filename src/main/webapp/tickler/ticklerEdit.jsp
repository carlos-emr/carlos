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
    ticklerEdit.jsp - Edit an existing tickler reminder

    Purpose:
    Provides a form for editing an existing tickler reminder, including updating
    the message, status, priority, assigned provider, and service date, with a
    full comment history display.

    Features:
    - Compact demographic card with patient contact details
    - Full tickler comment history display
    - Suggested text templates for common tickler messages
    - Quick-pick date selector (years, months, weeks, days offset)
    - Form submission via hidden iframe ('ticklerEditFrame') to allow post-submit
      window manipulation. The iframe.onload callback drives the opener refresh
      and window close. On success, broadcasts to BroadcastChannel(
      'carlos_tickler_refresh_<demographicNo>') so ticklerMain.jsp and
      newEncounterLayout.jsp for the same patient can reload without a full
      page refresh. The channel name is scoped per patient to prevent
      cross-patient refresh triggers.

    Parameters:
    - tickler_no:           ID of the tickler to edit (required)
    - parentAjaxId:         Encounter navbar element ID for reload notification

    @since CARLOS EMR 2026
--%>
<%@page import="java.util.Set" %>
<%@page import="java.util.List" %>
<%@page import="java.util.GregorianCalendar" %>
<%@page import="java.util.Calendar" %>
<%@page import="java.util.Locale" %>
<%@page import="java.text.DateFormat" %>
<%@page import="java.text.SimpleDateFormat" %>
<%@page import="org.owasp.encoder.Encode" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.TicklerTextSuggestDao" %>
<%@page import="io.github.carlos_emr.carlos.utility.LocaleUtils" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.TicklerTextSuggest" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Tickler" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.TicklerComment" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@page import="io.github.carlos_emr.carlos.managers.TicklerManager" %>
<%@page import="io.github.carlos_emr.carlos.managers.DemographicManager" %>
<%@page import="io.github.carlos_emr.CarlosProperties" %>
<%
    TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);
    DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib prefix="e" uri="owasp.encoder.jakarta" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>

<fmt:setBundle basename="oscarResources"/>

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
    boolean caisiEnabled = CarlosProperties.getInstance().isPropertyActive("caisi");
    String ticklerNoStr = request.getParameter("tickler_no");

    Integer ticklerNo = null;
    try {
        ticklerNo = Integer.valueOf(ticklerNoStr);
    } catch (NumberFormatException ignored) {
    }

    Tickler t = null;
    Demographic d = null;
    if (ticklerNo != null) {
        t = ticklerManager.getTickler(loggedInInfo, ticklerNo);
        if (t == null) {
            response.sendRedirect(request.getContextPath() + "/errorpage.jsp");
            return;
        }
        d = demographicManager.getDemographicWithExt(loggedInInfo, t.getDemographicNo());
    } else {
        response.sendRedirect(request.getContextPath() + "/errorpage.jsp");
        return;
    }

    java.util.Locale vLocale = request.getLocale();

    String selected = "";
    String stActive = LocaleUtils.getMessage(request.getLocale(), "tickler.ticklerMain.stActive");
    String stComplete = LocaleUtils.getMessage(request.getLocale(), "tickler.ticklerMain.stComplete");
    String stDeleted = LocaleUtils.getMessage(request.getLocale(), "tickler.ticklerMain.stDeleted");

    String prHigh = LocaleUtils.getMessage(request.getLocale(), "tickler.ticklerMain.priority.high");
    String prNormal = LocaleUtils.getMessage(request.getLocale(), "tickler.ticklerMain.priority.normal");
    String prLow = LocaleUtils.getMessage(request.getLocale(), "tickler.ticklerMain.priority.low");

    GregorianCalendar now = new GregorianCalendar();
    int curYear = now.get(Calendar.YEAR);
    int curMonth = (now.get(Calendar.MONTH) + 1);
    int curDay = now.get(Calendar.DAY_OF_MONTH);

    Locale locale = request.getLocale();
    DateFormat datetimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale);
    DateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd", locale);
    DateFormat timeOnlyFormat = new SimpleDateFormat("HH:mm:ss", locale);
%>
<!DOCTYPE html>
<html>
    <head>
        <title><fmt:message key="tickler.ticklerEdit.title"/></title>
        <%@ include file="/includes/global-head.jspf" %>
        <style>
            /* Links — CARLOS primary blue */
            a { color: var(--carlos-primary); }
            a:hover { color: #28619a; }

            /* Section headers — CARLOS primary */
            .section-header {
                background-color: var(--carlos-primary);
                color: #fff;
                font-weight: 600;
                padding: 6px 10px;
                margin-top: 10px;
                margin-bottom: 0;
                font-size: 14px;
                border-radius: 3px 3px 0 0;
            }

            /* Demographic card */
            .demo-card {
                background: #fff;
                border: 1px solid var(--carlos-border);
                border-radius: 4px;
                padding: 10px 14px;
                margin-bottom: 10px;
                font-size: 13px;
                line-height: 1.5;
            }
            .demo-card .demo-label {
                font-weight: 600;
                color: #666;
                font-size: 11px;
                text-transform: uppercase;
                margin-bottom: 1px;
            }
            .demo-card .demo-value { font-size: 13px; }
            .demo-card .demo-name { font-size: 15px; font-weight: 600; }

            /* Message history */
            .msg-table { font-size: 13px; }
            .msg-table td { vertical-align: top; }
            .msg-table .msg-original { font-weight: 600; }
            .msg-table tr.tickler-comment-row:nth-child(odd) td { background-color: #fafafa; }
            .msg-table tr.tickler-comment-row:nth-child(even) td { background-color: #fff; }

            /* Right-side controls — compact spacing */
            .edit-controls label { font-weight: 600; font-size: 13px; margin-bottom: 2px; display: block; }
            .edit-controls .form-select, .edit-controls .form-control { margin-bottom: 10px; }

            /* Quick-pick date grid */
            .grid {
                display: grid;
                grid-template-columns: repeat(10, 1fr);
                grid-gap: 2px;
                width: 270px;
            }

            .grid a, .today-button {
                background-color: var(--carlos-bg-light);
                border: 1px solid var(--carlos-border);
                text-align: center;
                padding: 2px;
                margin: 1px;
                display: flex;
                justify-content: center;
                text-decoration: none;
                color: var(--carlos-text);
                font-size: 11px;
                border-radius: 3px;
            }

            .grid a:hover, .today-button:hover {
                background-color: var(--carlos-primary);
                color: #fff;
            }

            .today-button {
                width: 125px;
                cursor: pointer;
            }

            /* Action bar */
            .action-bar-bottom {
                background: var(--carlos-bg-light);
                border-top: 1px solid var(--carlos-border);
                padding: 10px 15px;
                margin-top: 15px;
                display: flex;
                justify-content: flex-end;
                align-items: center;
                gap: 8px;
                border-radius: 0 0 4px 4px;
            }
        </style>
        <%
            java.util.ResourceBundle oscarBundle = java.util.ResourceBundle.getBundle("oscarResources", request.getLocale());
        %>
        <script type="application/javascript">
            //open a new popup window
            function popupPage(vheight, vwidth, varpage) {
                var page = "" + varpage;
                windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes";
                var popup = window.open(page, "attachment", windowprops);
                if (popup != null) {
                    if (popup.opener == null) {
                        popup.opener = self;
                    }
                }
            }

            function pasteMessageText() {
                var selectedIdx = document.serviceform.suggestedText.selectedIndex;
                document.serviceform.newMessage.value = document.serviceform.suggestedText.options[selectedIdx].text;
            }

            function openBrWindow(theURL, winName, features) {
                window.open(theURL, winName, features);
            }

            // Add options 1 to 10 for days, weeks, months, and years
            function addQuickPick() {
                const quickPickDiv = document.getElementById('quickPickDateOptions');
                for (let i = 0; i < 40; i++) {
                    const linkButton = document.createElement('a');
                    linkButton.href = '#';
                    switch (Math.floor(i / 10)) {
                        case 0:
                            linkButton.innerText = (i % 10) + 1 + "d";
                            linkButton.onclick = function () {
                                addTime((i % 10) + 1, "days");
                            };
                            break;//1 through 10 days
                        case 1:
                            linkButton.innerText = (i % 10) + 1 + "w";
                            linkButton.onclick = function () {
                                addTime(((i % 10) + 1) * 7, "days");
                            };
                            break;//1 through 10 weeks
                        case 2:
                            linkButton.innerText = (i % 10) + 1 + "m";
                            linkButton.onclick = function () {
                                addTime((i % 10) + 1, "months");
                            };
                            break;//1 through 10 months
                        case 3:
                            linkButton.innerText = (i % 10) + 1 + "y";
                            linkButton.onclick = function () {
                                addTime(((i % 10) + 1) * 12, "months");
                            };
                            break;//1 through 10 years
                    }
                    quickPickDiv.appendChild(linkButton);
                }
            }

            function addTime(num, type) {
                let currentDate = new Date();
                if (type === "months") {
                    currentDate.setMonth(currentDate.getMonth() + num);
                } else {
                    currentDate.setDate(currentDate.getDate() + num);
                }
                const year = currentDate.getFullYear();
                const month = String(currentDate.getMonth() + 1).padStart(2, '0');
                const day = String(currentDate.getDate()).padStart(2, '0');
                document.serviceform.xml_appointment_date.value = year + "-" + month + "-" + day;
            }

            function DateAdd(startDate, numDays, numMonths, numYears) {
                var returnDate = new Date(startDate.getTime());
                var yearsToAdd = numYears;

                var month = returnDate.getMonth() + numMonths;
                if (month > 11) {
                    yearsToAdd = Math.floor((month + 1) / 12);
                    month -= 12 * yearsToAdd;
                    yearsToAdd += numYears;
                }
                returnDate.setMonth(month);
                returnDate.setFullYear(returnDate.getFullYear() + yearsToAdd);

                returnDate.setTime(returnDate.getTime() + 60000 * 60 * 24 * numDays);

                return returnDate;
            }

            Date.prototype.addMonths = function (months) {
                var dat = new Date(this.valueOf());
                dat.setMonth(dat.getMonth() + months);
                return dat;
            }

            function enableSubmitButtons() {
                var btn = document.querySelector('.action-bar-bottom [name="updateTickler"]');
                if (btn) { btn.disabled = false; }
            }

            function validateSelectedProgram() {
                if (document.serviceform.program_assigned_to && document.serviceform.program_assigned_to.value === "none") {
                    document.getElementById("error").insertAdjacentText("beforeend", '<%=Encode.forJavaScript(oscarBundle.getString("tickler.ticklerAdd.msgNoProgramSelected"))%>');
                    document.getElementById("error").style.display = 'block';
                    return false;
                }
                return true;
            }

            function validate(form) {
                if (validateDate(form) <%=caisiEnabled?"&& validateSelectedProgram()":""%>) {
                    // Disable update button to prevent double-submit
                    var btn = document.querySelector('.action-bar-bottom [name="updateTickler"]');
                    if (btn) { btn.disabled = true; }

                    // Create iframe once; reassign onload every call
                    var submitTimeout;
                    var iframe = document.getElementById('ticklerEditFrame');
                    if (!iframe) {
                        iframe = document.createElement('iframe');
                        iframe.id = 'ticklerEditFrame';
                        iframe.name = 'ticklerEditFrame';
                        iframe.style.display = 'none';
                        document.body.appendChild(iframe);
                        // NOTE: onerror fires only for network-level failures (DNS, connection refused).
                        // HTTP 4xx/5xx responses trigger onload instead — error detection is handled there.
                        iframe.onerror = function() {
                            console.error('[ticklerEdit] iframe network error during form submission');
                            alert('<%=Encode.forJavaScript(oscarBundle.getString("tickler.ticklerAdd.errorNetworkFailed"))%>');
                            enableSubmitButtons();
                        };
                    }

                    // Reassign onload on every call
                    iframe.onload = function() {
                        clearTimeout(submitTimeout);
                        // Skip the initial about:blank load before the form posts
                        try {
                            if (iframe.contentWindow.location.href === 'about:blank') return;
                        } catch (e) {
                            // SecurityError: cross-origin redirect — almost certainly a session timeout
                            console.error('[ticklerEdit] iframe cross-origin access blocked — possible session expiry:', e);
                            alert('<%=Encode.forJavaScript(oscarBundle.getString("tickler.ticklerAdd.errorSessionExpired"))%>');
                            enableSubmitButtons();
                            return;
                        }
                        // Verify success by checking for sentinel element in the response
                        var saveOk = iframe.contentDocument && iframe.contentDocument.getElementById('tickler-edit-ok');
                        if (!saveOk) {
                            console.error('[ticklerEdit] Server did not return expected success response (missing #tickler-edit-ok)');
                            alert('<%=Encode.forJavaScript(oscarBundle.getString("tickler.ticklerAdd.errorSaveFailed"))%>');
                            enableSubmitButtons();
                            return;
                        }
                        try {
                            if (window.opener && !window.opener.closed &&
                                typeof window.opener.reloadNav === 'function') {
                                window.opener.reloadNav('tickler');
                            }
                        } catch (e) {
                            console.error('[ticklerEdit] Failed to call opener.reloadNav:', e);
                        }
                        try {
                            var demoNo = '<%=Encode.forJavaScript(String.valueOf(t.getDemographicNo()))%>';
                            var bc = new BroadcastChannel('carlos_tickler_refresh_' + demoNo);
                            bc.postMessage({ action: 'refresh' });
                            bc.close();
                        } catch (e) {
                            console.error('[ticklerEdit] BroadcastChannel broadcast failed:', e);
                            // Fallback: reload the opener directly so the tickler list stays current
                            try {
                                if (window.opener && !window.opener.closed) {
                                    window.opener.location.reload();
                                }
                            } catch (fallbackErr) {
                                console.warn('[ticklerEdit] Could not reload opener — user may need to refresh manually:', fallbackErr);
                            }
                        }
                        setTimeout(function() { window.close(); }, 500);
                    };

                    form.target = 'ticklerEditFrame';
                    form.submit();
                    submitTimeout = setTimeout(function() {
                        console.error('[ticklerEdit] Form submission timed out after 30s');
                        alert('<%=Encode.forJavaScript(oscarBundle.getString("tickler.ticklerAdd.errorSaveFailed"))%>');
                        enableSubmitButtons();
                    }, 30000);
                    return true;
                }
                return false;
            }

            function IsDate(value) {
                let dateWrapper = new Date(value);
                return !isNaN(dateWrapper.getDate());
            }

            function validateDate(form) {
                if (form.xml_appointment_date.value === "" || !IsDate(form.xml_appointment_date.value)) {
                    document.getElementById("error").insertAdjacentText("beforeend", '<%=Encode.forJavaScript(oscarBundle.getString("tickler.ticklerAdd.msgMissingDate"))%>');
                    document.getElementById("error").style.display = 'block';
                    return false;
                } else {
                    return true;
                }
            }
        </script>

    </head>

    <body onLoad="addQuickPick()">
    <div class="container" style="max-width: 860px;">
        <form name="serviceform" action="<%=request.getContextPath()%>/tickler/EditTickler.do" method="post">
            <input type="hidden" name="method" value="editTickler"/>
            <input type="hidden" name="ticklerNo" value="<%=ticklerNo%>"/>
            <input type="hidden" name="parentAjaxId" value="<%=Encode.forHtmlAttribute(request.getParameter("parentAjaxId") != null ? request.getParameter("parentAjaxId") : "")%>"/>
            <div class="page-header-bar">
                <h2 class="page-header-title"><fmt:message key="tickler.ticklerEdit.title"/></h2>
            </div>
            <div id="error" class="alert alert-danger" style="display:none;"></div>

            <%-- 1. Compact demographic card --%>
            <div class="demo-card">
                <div class="row">
                    <div class="col-sm-4">
                        <div class="demo-label"><fmt:message key="tickler.ticklerEdit.demographicName"/></div>
                        <div class="demo-name"><a href="javascript:void(0)"
                            onClick="popupPage(600,800,'<%=request.getContextPath()%>/demographic/DemographicEdit.do?demographic_no=<%=d.getDemographicNo()%>')"><%=Encode.forHtmlContent(d.getLastName())%>, <%=Encode.forHtmlContent(d.getFirstName())%></a></div>
                        <div class="demo-value"><%=Encode.forHtmlContent(d.getAge())%> (<%=Encode.forHtmlContent(d.getFormattedDob())%>)</div>
                    </div>
                    <div class="col-sm-4">
                        <div class="demo-label"><fmt:message key="tickler.ticklerEdit.phoneNumbers"/></div>
                        <div class="demo-value">(H) <%=Encode.forHtmlContent(d.getPhone())%></div>
                        <div class="demo-value">(W) <%=Encode.forHtmlContent(d.getPhone2())%></div>
                        <div class="demo-value">(C) <%=Encode.forHtmlContent(d.getCellPhone())%></div>
                    </div>
                    <div class="col-sm-4">
                        <div class="demo-label"><fmt:message key="tickler.ticklerEdit.chartNo"/></div>
                        <div class="demo-value"><%=d.getChartNo() != null ? Encode.forHtmlContent(d.getChartNo()) : ""%></div>
                        <div class="demo-label mt-1"><fmt:message key="tickler.ticklerEdit.email"/></div>
                        <div class="demo-value"><%=Encode.forHtmlContent(d.getEmail())%></div>
                    </div>
                </div>
            </div>

            <%-- 2. Message history --%>
            <div class="section-header"><fmt:message key="tickler.ticklerEdit.messages"/></div>
            <table class="table table-sm msg-table mb-0" style="border: 1px solid var(--carlos-border); border-top: none;">
                <thead>
                    <tr>
                        <th style="width:50%"><fmt:message key="tickler.ticklerEdit.messages"/></th>
                        <th><fmt:message key="tickler.ticklerEdit.addedBy"/></th>
                        <th><fmt:message key="tickler.ticklerEdit.dateAdded"/></th>
                    </tr>
                </thead>
                <tbody>
                    <tr class="msg-original">
                        <td style="white-space:pre-wrap;"><%=Encode.forHtmlContent(t.getMessage())%></td>
                        <td><%=Encode.forHtmlContent(t.getProvider().getLastName())%>, <%=Encode.forHtmlContent(t.getProvider().getFirstName())%></td>
                        <td><%=datetimeFormat.format(t.getCreateDate())%></td>
                    </tr>
                    <%
                        Set<TicklerComment> tComments = t.getComments();
                        for (TicklerComment tc : tComments) {
                    %>
                    <tr class="tickler-comment-row">
                        <td style="white-space:pre-wrap;"><%=Encode.forHtmlContent(tc.getMessage())%></td>
                        <td><%=Encode.forHtmlContent(tc.getProvider().getLastName())%>, <%=Encode.forHtmlContent(tc.getProvider().getFirstName())%></td>
                        <td><%=datetimeFormat.format(tc.getUpdateDate())%></td>
                    </tr>
                    <%}%>
                </tbody>
            </table>

            <%-- 3. Edit area — two-column layout --%>
            <div class="row mt-3">
                <%-- Left column: suggested text + message textarea --%>
                <div class="col-md-7">
                    <div class="section-header"><fmt:message key="tickler.ticklerEdit.newMessage"/></div>
                    <div style="border: 1px solid var(--carlos-border); border-top: none; padding: 10px;">
                        <div class="d-flex align-items-center gap-2 mb-2">
                            <label for="suggestedText" class="mb-0" style="white-space: nowrap; font-size: 13px;">
                                <a href="javascript:void(0)"
                                   onclick="openBrWindow('./ticklerSuggestedText.jsp','tickler_suggested_text','width=680,height=400')"
                                   style="font-weight:bold"><fmt:message key="tickler.ticklerEdit.suggestedText"/></a>:
                            </label>
                            <select class="form-select form-select-sm" name="suggestedText" id="suggestedText" style="flex:1;">
                                <option value="">---</option>
                                <%
                                    TicklerTextSuggestDao ticklerTextSuggestDao = (TicklerTextSuggestDao) SpringUtils.getBean(TicklerTextSuggestDao.class);
                                    for (TicklerTextSuggest tTextSuggest : ticklerTextSuggestDao.getActiveTicklerTextSuggests()) { %>
                                <option><%=Encode.forHtmlContent(tTextSuggest.getSuggestedText())%></option>
                                <% } %>
                            </select>
                            <input type="button" class="btn btn-outline-secondary btn-sm" name="pasteMessage" onclick="pasteMessageText()"
                                   value="<fmt:message key="tickler.ticklerEdit.pasteMessage"/>"/>
                        </div>
                        <textarea class="form-control" rows="10" id="newMessage" name="newMessage"></textarea>
                    </div>
                </div>

                <%-- Right column: status, priority, assigned to, service date, quick-pick --%>
                <div class="col-md-5 edit-controls">
                    <div class="section-header"><fmt:message key="tickler.ticklerEdit.status"/> / <fmt:message key="tickler.ticklerEdit.priority"/></div>
                    <div style="border: 1px solid var(--carlos-border); border-top: none; padding: 10px;">
                        <label for="status"><fmt:message key="tickler.ticklerEdit.status"/></label>
                        <select class="form-select" name="status" id="status">
                            <% if (t.getStatusDesc(vLocale).equals(stActive)){selected="selected";}else{selected="";}%>
                            <option <%=selected%> value="A"><fmt:message key="tickler.ticklerMain.stActive"/></option>
                            <% if (t.getStatusDesc(vLocale).equals(stComplete)){selected="selected";}else{selected="";}%>
                            <option <%=selected%> value="C"><fmt:message key="tickler.ticklerMain.stComplete"/></option>
                            <% if (t.getStatusDesc(vLocale).equals(stDeleted)){selected="selected";}else{selected="";}%>
                            <option <%=selected%> value="D"><fmt:message key="tickler.ticklerMain.stDeleted"/></option>
                        </select>

                        <label for="priority"><fmt:message key="tickler.ticklerEdit.priority"/></label>
                        <select class="form-select" name="priority" id="priority">
                            <% if (t.getPriorityWeb().equals(prHigh)) { selected = "selected"; } else { selected = ""; }%>
                            <option <%=selected%> value="<fmt:message key="tickler.ticklerMain.priority.high"/>"><fmt:message key="tickler.ticklerMain.priority.high"/></option>
                            <% if (t.getPriorityWeb().equals(prNormal)) { selected = "selected"; } else { selected = ""; }%>
                            <option <%=selected%> value="<fmt:message key="tickler.ticklerMain.priority.normal"/>"><fmt:message key="tickler.ticklerMain.priority.normal"/></option>
                            <% if (t.getPriorityWeb().equals(prLow)) { selected = "selected"; } else { selected = ""; }%>
                            <option <%=selected%> value="<fmt:message key="tickler.ticklerMain.priority.low"/>"><fmt:message key="tickler.ticklerMain.priority.low"/></option>
                        </select>

                        <label for="assignedToProviders"><fmt:message key="tickler.ticklerEdit.assignedTo"/></label>
                        <select class="form-select" name="assignedToProviders" id="assignedToProviders">
                            <%
                                ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
                                List<Provider> providers = providerDao.getActiveProviders();
                                for (Provider p : providers) {
                                    if (p.equals(t.getAssignee())) { selected = "selected"; } else { selected = ""; }
                            %>
                            <option <%=selected%> value="<%=Encode.forHtmlAttribute(p.getProviderNo())%>"><%=Encode.forHtmlContent(p.getLastName())%>, <%=Encode.forHtmlContent(p.getFirstName())%></option>
                            <% } %>
                        </select>

                        <%
                            DateFormat dateformat = new SimpleDateFormat("yyyy-MM-dd");
                            String strDate = dateformat.format(t.getServiceDate());
                        %>
                        <label for="xml_appointment_date"><fmt:message key="tickler.ticklerEdit.serviceDate"/></label>
                        <input name="xml_appointment_date" class="form-control" id="xml_appointment_date" type="date"
                               maxlength="10" value="<%=strDate%>"/>

                        <div id="todayButton" class="today-button mt-2" onclick="addTime(0, 'days')"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerEdit.btnToday"/></div>
                        <div id="quickPickDateOptions" class="grid"></div>
                    </div>
                </div>
            </div>

            <%-- 4. Sticky action bar --%>
            <div class="action-bar-bottom">
                <oscar:oscarPropertiesCheck property="tickler_email_enabled" value="true">
                    <label class="mb-0 me-2" style="font-size:13px;">
                        <input type="checkbox" name="emailDemographic" value="true" class="form-check-input me-1"/>
                        <fmt:message key="tickler.ticklerEdit.emailDemographic"/>
                    </label>
                </oscar:oscarPropertiesCheck>
                <input type="button" class="btn btn-primary" name="updateTickler"
                       value="<fmt:message key="tickler.ticklerEdit.update"/>" onClick="validate(this.form)"/>
                <input type="button" class="btn btn-secondary" name="cancelChangeTickler"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnBack"/>" onClick="window.close()"/>
            </div>
        </form>
    </div>

    </body>
</html>
