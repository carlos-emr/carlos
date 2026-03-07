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
<!DOCTYPE HTML>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_appointment" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_appointment");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<%@ page import="java.util.Set" %>
<%@ page import="java.util.HashSet" %>

<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.time.LocalDateTime" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.time.format.FormatStyle" %>
<%@ page import="java.time.ZoneId" %>

<%@ page import="java.util.*, java.lang.*, io.github.carlos_emr.carlos.appt.*" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>
<%@ page import="io.github.carlos_emr.carlos.appt.status.service.AppointmentStatusMgr" %>
<%@ page import="io.github.carlos_emr.carlos.appt.status.service.impl.AppointmentStatusMgrImpl" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.bc.decisionSupport.BillingGuidelines" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.data.EctFormData" %>
<%@ page import="io.github.carlos_emr.carlos.util.ConversionUtils" %>
<%@ page import="io.github.carlos_emr.OscarProperties" %>

<%@ page import="io.github.carlos_emr.carlos.commn.model.AppointmentStatus" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.DemographicCust" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DemographicCustDao" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DemographicDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.EncounterForm" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.EncounterFormDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Appointment" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.model.Program" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Facility" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.service.ProviderManager" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.service.ProgramManager" %>
<%@ page import="io.github.carlos_emr.carlos.managers.ProgramManager2" %>
<%@ page import="io.github.carlos_emr.carlos.decisionSupport.model.DSConsequence" %>

<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SessionConstants" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ProviderPreference" %>

<%@ page import="io.github.carlos_emr.carlos.managers.LookupListManager" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.LookupList" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.SiteDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Site" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.AppointmentTypeDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.AppointmentType" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>

<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.appt.JdbcApptImpl" %>
<%@ page import="io.github.carlos_emr.carlos.appt.ApptUtil" %>
<%@ page import="io.github.carlos_emr.carlos.appt.ApptData" %>
<%@ page import="io.github.carlos_emr.carlos.commn.IsPropertiesOn" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>

<fmt:setBundle basename="oscarResources"/>
<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>

<%

    String DONOTBOOK = "Do_Not_Book";
    String curProvider_no = request.getParameter("provider_no");
    String curDoctor_no = request.getParameter("doctor_no") != null ? request.getParameter("doctor_no") : "";
    String curUser_no = (String) session.getAttribute("user");
    String userfirstname = (String) session.getAttribute("userfirstname");
    String userlastname = (String) session.getAttribute("userlastname");

    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

    ProviderPreference providerPreference = (ProviderPreference) session.getAttribute(SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE);
    int everyMin = providerPreference.getEveryMin();

    boolean bFirstDisp = true; //this is the first time to display the window
    boolean bFromWL = false; //this is from waiting list page

    if (request.getParameter("bFirstDisp") != null) bFirstDisp = (request.getParameter("bFirstDisp")).equals("true");
    if (request.getParameter("demographic_no") != null) bFromWL = true;

    String duration = request.getParameter("duration") != null ? (request.getParameter("duration").equals(" ") || request.getParameter("duration").equals("") || request.getParameter("duration").equals("null") ? ("" + everyMin) : request.getParameter("duration")) : ("" + everyMin);

    //check for management fee code eligibility
    Set<String> billingRecommendations = new HashSet<String>();
    try {
        List<DSConsequence> list = BillingGuidelines.getInstance().evaluateAndGetConsequences(loggedInInfo, request.getParameter("demographic_no"), curProvider_no);

        for (DSConsequence dscon : list) {
            if (dscon.getConsequenceStrength().equals(DSConsequence.ConsequenceStrength.recommendation)) {
                String recommendation = new String(dscon.getText());
                billingRecommendations.add(recommendation);
            }
        }
    } catch (Exception e) {
        MiscUtils.getLogger().error("Error", e);
    }
%>


<%
    DemographicCustDao demographicCustDao = (DemographicCustDao) SpringUtils.getBean(DemographicCustDao.class);
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
    EncounterFormDao encounterFormDao = SpringUtils.getBean(EncounterFormDao.class);
    OscarAppointmentDao appointmentDao = SpringUtils.getBean(OscarAppointmentDao.class);

    ProviderManager providerManager = SpringUtils.getBean(ProviderManager.class);
    ProgramManager programManager = SpringUtils.getBean(ProgramManager.class);

    String providerNo = loggedInInfo.getLoggedInProviderNo();
    Facility facility = loggedInInfo.getCurrentFacility();

    List<Program> programs = programManager.getActiveProgramByFacility(providerNo, facility.getId());

    LookupListManager lookupListManager = SpringUtils.getBean(LookupListManager.class);
    LookupList reasonCodes = lookupListManager.findLookupListByName(loggedInInfo, "reasonCode");
    pageContext.setAttribute("reasonCodes", reasonCodes);

    int iPageSize = 5;

    ApptData apptObj = ApptUtil.getAppointmentFromSession(request);

    OscarProperties pros = OscarProperties.getInstance();
    String strEditable = pros.getProperty("ENABLE_EDIT_APPT_STATUS");
    Boolean isMobileOptimized = session.getAttribute("mobileOptimized") != null;

    AppointmentStatusMgr apptStatusMgr = new AppointmentStatusMgrImpl();
    List<AppointmentStatus> allStatus = apptStatusMgr.getAllActiveStatus();

    String useProgramLocation = OscarProperties.getInstance().getProperty("useProgramLocation");
    String moduleNames = OscarProperties.getInstance().getProperty("ModuleNames");
    boolean caisiEnabled = moduleNames != null && org.apache.commons.lang3.StringUtils.containsIgnoreCase(moduleNames, "Caisi");
    boolean locationEnabled = caisiEnabled && (useProgramLocation != null && useProgramLocation.equals("true"));

    ProgramManager2 programManager2 = SpringUtils.getBean(ProgramManager2.class);
%>

<html>
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <script src="${pageContext.request.contextPath}/library/jquery/jquery-ui-1.12.1.min.js"></script>
        <script src="${pageContext.request.contextPath}/js/checkDate.js"></script>
        <script src="${pageContext.request.contextPath}/share/javascript/Oscar.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.title"/></title>

        <style>
.ui-selectmenu-button.ui-button {
                width: 100% !important;
            }

            .ui-button {
                padding: 10px !important;
            }

            .ui-icon {
                width: 12px !important;
                height: 12px !important;
            }

            textarea {
                width: 100%;
            }

        </style>
        <%
            // multisites start ==================
            SiteDao siteDao = (SiteDao) SpringUtils.getBean("siteDao");
            List<Site> sites = siteDao.getActiveSitesByProviderNo((String) session.getAttribute("user"));
            boolean bMultisites = IsPropertiesOn.isMultisitesEnable();
            // multisites end ==================
            if (bMultisites) { %>
        <style>
            <% for (Site s:sites) { %>
            .<%=s.getShortName()%> {
                background-color: <%=s.getBgColor()%>;
            }

            <% } %>
        </style>
        <% } %>
        <style>
            <% for (int i = 0; i < allStatus.size(); i++) {%>
            .<%=(allStatus.get(i)).getStatus()%> {
                background-color: <%=(allStatus.get(i)).getColor()%>;
            }

            <% } %>
        </style>

        <script>

            function updateTime() {
                const reTime = /^([0-1][0-9]|2[0-3]):[0-5][0-9]$/;
                const time = document.ADDAPPT.start_time.value;
                if (reTime.exec(time)) {
                    const minute = Number(time.substring(3, 5));
                    const minuteDeg = Number(time.substring(3, 5)) * 360 / 60;
                    const hourDeg = (Number(time.substring(0, 2)) % 12 + (minute / 60)) * 360 / 12;
                    console.log("minute=" + minute + " minDeg =" + minuteDeg);
                    document.getElementById("header").style.backgroundImage = `url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='40' height='40'><circle cx='20' cy='20' r='18.5' fill='none' stroke='%23222' stroke-width='3' /><path d='M20,4 20,8 M4,20 8,20 M36,20 32,20 M20,36 20,32' stroke='%23bbb' stroke-width='1' /><circle cx='20' cy='20' r='2' fill='%23222' stroke='%23222' stroke-width='2' /></svg>"), url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='40' height='40'><path d='M18.5,24.5 19.5,4 20.5,4 21.5,24.5 Z' fill='%23222' style='transform:rotate(` + minuteDeg + `deg); transform-origin: 50% 50%;' /></svg>"), url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='40' height='40'><path d='M18.5,24.5 19.5,8.5 20.5,8.5 21.5,24.5 Z' style='transform:rotate(` + hourDeg + `deg); transform-origin: 50% 50%;' /></svg>")`;
                }
            }

            function onAdd() {
                return calculateEndTime();
            }

            function setfocus() {
                this.focus();
                document.ADDAPPT.keyword.focus();
                document.ADDAPPT.keyword.select();
            }

            function moveAppt() {
                var determinator = 0;
                determinator = localStorage.getItem('copyPaste');
                if (determinator == 1) {  //This means we are moving an appt
                    pasteAppt(false);
                    document.forms['ADDAPPT'].displaymode.value = 'Add Appointment';
                    //$("#pasteButton").trigger( "click" );
                    //$("#addButton").trigger( "click" );
                    localStorage.setItem('copyPaste', '0');  //reset
                }
            }

            function upCaseCtrl(ctrl) {
                ctrl.value = ctrl.value.toUpperCase();
            }

            function showJSAlert(msg) {
                var el = document.getElementById('jsAlertBanner');
                el.querySelector('#jsAlertText').textContent = msg;
                el.style.display = '';
            }

            function onBlockFieldFocus(obj) {
                obj.blur();
                document.ADDAPPT.keyword.focus();
                document.ADDAPPT.keyword.select();
                showJSAlert("<fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.msgFillNameField"/>");
            }

            function checkTypeNum(typeIn) {
                var typeInOK = true;
                var i = 0;
                var length = typeIn.length;
                var ch;

                // walk through a string and find a number
                if (length >= 1) {
                    while (i < length) {
                        ch = typeIn.substring(i, i + 1);
                        if (ch == ":") {
                            i++;
                            continue;
                        }
                        if ((ch < "0") || (ch > "9")) {
                            typeInOK = false;
                            break;
                        }
                        i++;
                    }
                } else typeInOK = false;
                return typeInOK;
            }

            function checkTimeTypeIn(obj) {
                var colonIdx;
                if (!checkTypeNum(obj.value)) {
                    showJSAlert("<fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.msgFillTimeField"/>");
                } else {
                    colonIdx = obj.value.indexOf(':');
                    if (colonIdx == -1) {
                        if (obj.value.length < 3) showJSAlert("<fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.msgFillValidTimeField"/>");
                        obj.value = obj.value.substring(0, obj.value.length - 2) + ":" + obj.value.substring(obj.value.length - 2);
                    }
                }

                var hours = "";
                var minutes = "";

                colonIdx = obj.value.indexOf(':');
                if (colonIdx < 1)
                    hours = "00";
                else if (colonIdx == 1)
                    hours = "0" + obj.value.substring(0, 1);
                else
                    hours = obj.value.substring(0, 2);

                minutes = obj.value.substring(colonIdx + 1, colonIdx + 3);
                if (minutes.length == 0)
                    minutes = "00";
                else if (minutes.length == 1)
                    minutes = "0" + minutes;
                else if (minutes > 59)
                    minutes = "00";

                obj.value = hours + ":" + minutes;
            }

            var readOnly = false;

            function checkDateTypeIn(obj) {
                if (obj.value == '') {
                    showJSAlert("Date cannot be empty");
                    return false;
                } else {
                    obj.value = obj.value.replace(/\//g, "-");
                    if (!check_date(obj.name))
                        return false;
                }
            }

            function calculateEndTime() {
                var stime = document.ADDAPPT.start_time.value;
                var vlen = stime.indexOf(':') == -1 ? 1 : 2;
                var shour = stime.substring(0, 2);
                var smin = stime.substring(stime.length - vlen);
                var duration = document.ADDAPPT.duration.value;

                if (isNaN(duration)) {
                    showJSAlert("<fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.msgFillTimeField"/>");
                    return false;
                }

                if (parseInt(duration, 10) == 0) {
                    duration = 1;
                }
                if (parseInt(duration, 10) < 0) {
                    duration = Math.abs(parseInt(duration, 10));
                }

                var lmin = parseInt(smin, 10) + parseInt(duration, 10) - 1;
                var lhour = parseInt(lmin / 60);

                if ((lmin) > 59) {
                    shour = parseInt(shour, 10) + lhour;
                    shour = shour < 10 ? ("0" + shour) : shour;
                    smin = lmin - 60 * lhour;
                } else {
                    smin = lmin;
                }

                smin = smin < 10 ? ("0" + smin) : smin;
                document.ADDAPPT.end_time.value = shour + ":" + smin;

                if (shour > 23) {
                    showJSAlert("<fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.msgCheckDuration"/>");
                    return false;
                }

                //no show
                if (document.ADDAPPT.keyword.value.substring(0, 1) === "." && document.ADDAPPT.demographic_no.value === "") {
                    document.ADDAPPT.status.value = 'N';
                }

                return true;
            }

            function onNotBook() {
                document.forms[0].keyword.value = "<%=DONOTBOOK%>";
            }

            function onButRepeat() {
                document.forms[0].action = "appointmentrepeatbooking.jsp";
                if (calculateEndTime()) {
                    document.forms[0].submit();
                }
            }

            <% if(apptObj!=null) { %>

            function pasteAppt(multipleSameDayGroupAppt) {

                var warnMsgId = document.getElementById("tooManySameDayGroupApptWarning");

                if (multipleSameDayGroupAppt) {
                    warnMsgId.style.display = "block";
                    if (document.forms[0].groupButton) {
                        document.forms[0].groupButton.style.display = "none";
                    }
                    document.forms[0].addButton.style.display = "none";

                    if (document.forms[0].pasteButton) {
                        document.forms[0].pasteButton.style.display = "none";
                    }

                    if (document.forms[0].apptRepeatButton) {
                        document.forms[0].apptRepeatButton.style.display = "none";
                    }
                } else {
                    warnMsgId.style.display = "none";
                }

                document.forms[0].duration.value = "<%=Encode.forJavaScriptBlock(apptObj.getDuration())%>";
                //document.forms[0].chart_no.value = "<%=Encode.forJavaScriptBlock(apptObj.getChart_no())%>";
                document.forms[0].keyword.value = "<%=Encode.forJavaScriptBlock(apptObj.getName())%>";
                document.forms[0].demographic_no.value = "<%=Encode.forJavaScriptBlock(apptObj.getDemographic_no())%>";
                document.forms[0].reason.value = "<%= Encode.forJavaScriptBlock(apptObj.getReason()) %>";
                document.forms[0].reasonCode.value = "<%= Encode.forJavaScriptBlock(apptObj.getReasonCode()) %>";
                document.forms[0].notes.value = "<%= Encode.forJavaScriptBlock(apptObj.getNotes()) %>";
                document.forms[0].resources.value = "<%=Encode.forJavaScriptBlock(apptObj.getResources())%>";
                document.forms[0].type.value = "<%=Encode.forJavaScriptBlock(apptObj.getType())%>";
                document.forms[0].location.value = "<%=Encode.forJavaScriptBlock(apptObj.getLocation())%>";
                if ('<%=apptObj.getUrgency()%>' == 'critical') {
                    document.forms[0].urgency.checked = "checked";
                }
                document.forms[0].reasonCode.value = "<%=apptObj.getReasonCode() %>";

                <%if("true".equals(pros.getProperty("appointment.paste.status","false"))) {%>
                var statusCode = "<%=Encode.forJavaScriptBlock(apptObj.getStatus())%>";
                statusCode = statusCode.substring(0, 1); //the selector only supports setting the first status
                document.forms[0].status.value = statusCode;
                <%}%>
                <%if("true".equals(pros.getProperty("appointment.paste.location","false"))) {%>
                document.forms[0].location.value = "<%=Encode.forJavaScriptBlock(apptObj.getLocation())%>";
                <%}%>


            }

            <% } %>


            function openTypePopup() {
                windowprops = "height=230,width=500,location=no,scrollbars=no,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=100,left=100";
                var popup = window.open("appointmentType.jsp?type=" + document.forms['ADDAPPT'].type.value, "Appointment Type", windowprops);
                if (popup != null) {
                    if (popup.opener == null) {
                        popup.opener = self;
                    }
                    popup.focus();
                }
            }

            function setType(typeSel, reasonSel, locSel, durSel, notesSel, resSel) {
                document.forms['ADDAPPT'].type.value = typeSel;
                document.forms['ADDAPPT'].reason.value = reasonSel;
                document.forms['ADDAPPT'].duration.value = durSel;
                document.forms['ADDAPPT'].notes.value = notesSel;
                document.forms['ADDAPPT'].duration.value = durSel;
                document.forms['ADDAPPT'].resources.value = resSel;
                var loc = document.forms['ADDAPPT'].location;
                if (loc.nodeName === 'SELECT') {
                    for (c = 0; c < loc.length; c++) {
                        if (loc.options[c].innerHTML == locSel) {
                            loc.selectedIndex = c;
                            loc.style.backgroundColor = loc.options[loc.selectedIndex].style.backgroundColor;
                            break;
                        }
                    }
                } else if (loc.nodeName === "INPUT") {
                    document.forms['ADDAPPT'].location.value = locSel;
                }
            }


            $(document).ready(function () {
		// $( document ).tooltip();

                var url = "<%= request.getContextPath() %>/demographic/SearchDemographic.do?jqueryJSON=true&activeOnly=true";

                $("#keyword").autocomplete({
                    source: url,
                    minLength: 2,

                    focus: function (event, ui) {
                        $("#keyword").val(ui.item.formattedName);
                        return false;
                    },
                    select: function (event, ui) {
                        $("#demographic_no").val(ui.item.value);
                        $("#mrp").val(ui.item.provider);
                        $("#keyword").val(ui.item.formattedName);

                        // Show patient alert banner if the selected patient has an alert
                        var patientAlert = ui.item.alert || "";
                        var alertBanner = document.getElementById('patientAlertBanner');
                        if (patientAlert) {
                            // Use textContent to safely set content and prevent XSS
                            document.getElementById('patientAlertText').textContent = patientAlert;
                            alertBanner.style.display = '';
                        } else {
                            alertBanner.style.display = 'none';
                        }

                        // Show patient status banner if the selected patient has a non-default status
                        var rawStatus = ui.item.status || "";
                        var rawRoster = ui.item.rosterStatus || "";
                        // Normalize: AC (active) and RO (rostered) are the expected defaults — hide banner for these
                        var displayStatus = (!rawStatus || rawStatus === "AC") ? "" : rawStatus;
                        var displayRoster = (!rawRoster || rawRoster === "RO") ? "" : rawRoster;
                        var statusBanner = document.getElementById('patientStatusBanner');
                        var statusTextEl = document.getElementById('patientStatusText');
                        if (displayStatus || displayRoster) {
                            var rosterLabel = statusBanner ? (statusBanner.getAttribute('data-roster-label') || '') : '';
                            var parts = [];
                            if (displayStatus) parts.push(displayStatus);
                            if (displayRoster) parts.push(rosterLabel + ":\u00a0" + displayRoster);
                            statusTextEl.textContent = parts.join("\u00a0");
                            statusBanner.style.display = '';
                        } else {
                            statusBanner.style.display = 'none';
                        }

                        return false;
                    }
                })
                    .autocomplete("instance")._renderItem = function (ul, item) {
                    var $b = $("<b>").text(item.label || "");
                    var $div = $("<div>").append($b).append("<br>").append(document.createTextNode(item.provider || ""));
                    return $("<li>").append($div).appendTo(ul);
                };


                $.widget('custom.myselectmenu', $.ui.selectmenu, {

                    /**
                     * @see {@link https://api.jqueryui.com/selectmenu/#method-_renderItem}
                     */
                    _renderItem: function (ul, item) {
                        var $div = $("<div>");
                        var $header = $("<b>").text(item.label || "");
                        $div.append($header);
                        var dur = item.element.attr("data-dur");
                        if (dur && dur.length > 0) {
                            $div.append(document.createTextNode("\u00a0" + dur + "\u00a0"));
                            $div.append($("<span>").html("<fmt:setBundle basename='oscarResources'/><fmt:message key='provider.preference.min'/>"));
                        }
                        var notesVal = item.element.attr("data-notes");
                        if (notesVal && notesVal.length > 0) {
                            $div.append($("<span>").html("&nbsp;&nbsp;"));
                            var $notesIcon = $("<span>").css("color", "gray").append(
                                $("<i>").addClass("fa-solid fa-pencil").attr("title", "<fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formNotes"/>:\u00a0" + notesVal)
                            );
                            $div.append($notesIcon);
                        }
                        $div.append($("<br>"));
                        var reasonVal = item.element.attr("data-reason");
                        if (reasonVal && reasonVal.length > 0) {
                            var $reasonIcon = $("<span>").css("color", "gray").append(
                                $("<i>").addClass("fa-solid fa-tags").attr("title", "<fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formReason"/>")
                            );
                            $div.append($reasonIcon).append($("<span>").html("&nbsp;&nbsp;")).append(document.createTextNode(reasonVal));
                        }
                        var resourcesVal = item.element.attr("data-resources");
                        if (resourcesVal && resourcesVal.length > 0) {
                            $div.append($("<br>"));
                            var $resourcesIcon = $("<span>").css("color", "gray").append(
                                $("<i>").addClass("fa-solid fa-gear").attr("title", "<fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formResources"/>")
                            );
                            $div.append($resourcesIcon).append($("<span>").html("&nbsp;&nbsp;")).append(document.createTextNode(resourcesVal));
                        }
                        var locVal = item.element.attr("data-loc");
                        if (locVal && locVal.length > 1) {
                            $div.append($("<br>"));
                            var $locIcon = $("<span>").css("color", "gray").append(
                                $("<i>").addClass("fa-solid fa-house").attr("title", "<fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formLocation"/>")
                            );
                            $div.append($locIcon).append($("<span>").html("&nbsp;&nbsp;")).append(document.createTextNode(locVal));
                        }
                        return $("<li>").append($div).appendTo(ul);
                    }
                });

                // render custom selectmenu
                $('#type').myselectmenu({
                    change: function (event, data) {
                        label = data.item.value;
                        origReason = $("textarea[name='reason']").val();
                        reason = data.item.element.attr("data-reason");
                        if (origReason.length > 0) {
                            reason = reason.concat(" -- ".concat(origReason));
                        }
                        loc = data.item.element.attr("data-loc");
                        dur = data.item.element.attr("data-dur");
                        notes = data.item.element.attr("data-notes");
                        resources = data.item.element.attr("data-resources");
                        setType(label, reason, loc, dur, notes, resources);
                    }

                });


            });

            // stop javascript -->

        </script>

        <%

            SimpleDateFormat fullform = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            SimpleDateFormat inform = new SimpleDateFormat("yyyy-MM-dd");
            SimpleDateFormat outform = new SimpleDateFormat("EEE");

            java.util.Date apptDate;

            if (request.getParameter("year") == null || request.getParameter("month") == null || request.getParameter("day") == null) {
                Calendar cal = Calendar.getInstance();
                String sDay = String.valueOf(cal.get(Calendar.DATE));
                String sMonth = String.valueOf(cal.get(Calendar.MONTH) + 1);
                String sYear = String.valueOf(cal.get(Calendar.YEAR));
                String sTime = (request.getParameter("start_time") == null) ? "00:00AM" : request.getParameter("start_time");
                apptDate = fullform.parse(bFirstDisp ? (sYear + "-" + sMonth + "-" + sDay + " " + sTime) :
                        (request.getParameter("appointment_date") + " " + sTime));
            } else if (request.getParameter("start_time") == null) {
                apptDate = fullform.parse(bFirstDisp ? (request.getParameter("year") + "-" + request.getParameter("month") + "-" + request.getParameter("day") + " " + "00:00 AM") :
                        (request.getParameter("appointment_date") + " " + "00:00AM"));
            } else {
                apptDate = fullform.parse(bFirstDisp ? (request.getParameter("year") + "-" + request.getParameter("month") + "-" + request.getParameter("day") + " " + request.getParameter("start_time")) :
                        (request.getParameter("appointment_date") + " " + request.getParameter("start_time")));
            }


// Get localized pattern for UI
            DateTimeFormatter pattern2 = DateTimeFormatter.ofPattern("EEE").withLocale(request.getLocale());
// Convert Java Date to Java LocalDateTime
            LocalDateTime apptd = apptDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

            //String dateString1 = outform.format(apptDate );
            String dateString1 = pattern2.format(apptd);
            String dateString2 = inform.format(apptDate);

            GregorianCalendar caltime = new GregorianCalendar();
            caltime.setTime(apptDate);
            caltime.add(GregorianCalendar.MINUTE, Integer.parseInt(duration) - 1);

            java.util.Date startTime = ConversionUtils.fromDateString(request.getParameter("start_time"), "HH:mm");
            java.util.Date endTime = ConversionUtils.fromDateString(caltime.get(Calendar.HOUR_OF_DAY) + ":" + caltime.get(Calendar.MINUTE), "HH:mm");

            List<Appointment> appts = appointmentDao.search_appt(apptDate, curProvider_no, startTime, endTime, startTime, endTime, startTime, endTime, Integer.parseInt((String) request.getSession().getAttribute("programId_oscarView")));

            long apptnum = appts.size() > 0 ? new Long(appts.size()) : 0;

            OscarProperties props = OscarProperties.getInstance();

            String timeoutSeconds = props.getProperty("appointment_locking_timeout", "0");
            int timeoutSecs = 0;
            try {
                timeoutSecs = Integer.parseInt(timeoutSeconds);
            } catch (NumberFormatException e) {/*empty*/}

            int hourInt = caltime.get(Calendar.HOUR_OF_DAY);
            String hour = String.valueOf(hourInt);
            if (hour.length() == 0)
                hour = "00";
            else if (hour.length() == 1)
                hour = "0" + hour;

            int minuteInt = caltime.get(Calendar.MINUTE);
            String minute = String.valueOf(minuteInt);
            if (minute.length() == 0)
                minute = "00";
            else if (minute.length() == 1)
                minute = "0" + minute;

            if (timeoutSecs > 0) {
        %>

        <script>
            var timers = new Array();

            $(document).ready(function () {

                $(window).on('beforeunload', function () {
                    cancelPageLock();
                });
                //cancel any page view/locks held by providers on clicking 'X'
                $("form#addappt").on("submit", function () {
                    $(window).off('beforeunload');
                });

                calculateEndTime();
                var endTime = document.forms[0].end_time.value;
                var startTime = document.forms[0].start_time.value;
                var apptDate = document.forms[0].appointment_date.value;
                updatePageLock(100, apptDate, startTime, endTime);
            });

            function checkPageLock() {
                $("#searchBtn").attr("disabled", "disabled");
                calculateEndTime();
                var endTime = document.forms[0].end_time.value;
                var startTime = document.forms[0].start_time.value;
                var apptDate = document.forms[0].appointment_date.value;
                updatePageLock(100, apptDate, startTime, endTime);

            }

            function updatePageLock(timeout, apptDate, startTime, endTime) {

                for (var i = 0; i < timers.length; i++) {
                    clearTimeout(timers[i]);
                }

                haveLock = false;
                $.ajax({
                        type: "POST",
                        url: "<%=request.getContextPath()%>/PageMonitoringService.do",
                        data: {
                            method: "update",
                            page: "addappointment",
                            pageId: "<%=curProvider_no%>|" + apptDate + "|" + startTime + "|" + endTime,
                            lock: true,
                            timeout: <%=timeoutSeconds%>,
                            cleanupExisting: true
                        },
                        dataType: 'json',
                        async: false,
                        success: function (data, textStatus) {
                            lockData = data;
                            var locked = false;
                            var lockedProviderName = '';
                            var providerNames = '';
                            haveLock = false;
                            $.each(data, function (key, val) {
                                if (val.locked) {
                                    locked = true;
                                    lockedProviderName = val.providerName;
                                }
                                if (val.locked == true && val.self == true) {
                                    haveLock = true;
                                }
                                if (providerNames.length > 0)
                                    providerNames += ",";
                                providerNames += val.providerName;

                            });

                            var lockedMsg = locked ? '<span style="color:red" title="' + lockedProviderName + '">&nbsp(locked)</span>' : '';
                            $("#lock_notification").html(
                                '<span title="' + providerNames + '">Viewers:' + data.length + lockedMsg + '</span>'
                            );


                            if (haveLock == true) { //i have the lock
                                $("#addButton").show();
                                $("#pasteButton").show();
                                $("#apptRepeatButton").show();
                            } else if (locked && !haveLock) { //someone else has lock.
                                $("#addButton").hide();
                                $("#pasteButton").hide();
                                $("#apptRepeatButton").hide();
                            } else { //no lock
                                $("#addButton").show();
                                $("#pasteButton").show();
                                $("#apptRepeatButton").show();
                            }
                            $("#searchBtn").removeAttr("disabled");
                        }
                    }
                );

                timers.push(setTimeout(function () {
                    updatePageLock(5000, apptDate, startTime, endTime)
                }, timeout));
            }

            function cancelPageLock() {
                calculateEndTime();
                var endTime = document.forms[0].end_time.value;
                var startTime = document.forms[0].start_time.value;
                var apptDate = document.forms[0].appointment_date.value;

                for (var i = 0; i < timers.length; i++) {
                    clearTimeout(timers[i]);
                }

                $.ajax({
                    type: "POST",
                    url: "<%=request.getContextPath()%>/PageMonitoringService.do",
                    data: {
                        method: "cancel",
                        page: "addappointment",
                        pageId: "<%=curProvider_no%>|" + apptDate + "|" + startTime + "|" + endTime
                    },
                    dataType: 'json',
                    async: false,
                    success: function (data, textStatus) {
                    }
                });
            }

        </script>

        <%
        } else {
        %>
        <script>
            function checkPageLock() { //don't do anything unless timeout/locking is enabled.
            }

            function cancelPageLock() { //don't do anything unless timeout/locking is enabled.
            }
        </script>

        <%
            }
            String deepcolor = apptnum == 0 ? "#E8E8E8" : "gold", weakcolor = apptnum == 0 ? "#f3f6f9" : "ivory";

            boolean bDnb = false;
            for (Appointment a : appts) {
                String apptName = a.getName();
                if (apptName.equalsIgnoreCase(DONOTBOOK)) bDnb = true;
            }


            // select providers lastname & firstname
            String pLastname = "";
            String pFirstname = "";
            Provider p = providerDao.getProvider(curProvider_no);
            if (p != null) {
                pLastname = p.getLastName();
                pFirstname = p.getFirstName();
            }
        %>

        <script>
            function parseSearch() {
                // sane defaults
                document.forms['ADDAPPT'].displaymode.value = 'Search ';
                document.getElementById("search_mode").value = 'search_name';

                var keyObj = document.forms['ADDAPPT'].keyword;
                var keyVal = keyObj.value;
                console.log(keyVal);

                // start with the loosest pattern
                // address pattern 293 Meridian
                const reAddr = /^\d{1,9}[\s]\w*/;
                if (reAddr.exec(keyVal)) {
                    document.getElementById("search_mode").value = "search_address";
                }

                // hin OHIP 10 didgits  MSP 9 didgits Regie 4 alpha + 8 digits
                const reHIN = /^\d{9,10}$/;
                if (reHIN.exec(keyVal)) {
                    document.getElementById("search_mode").value = "search_hin";
                }
                const reRegie = /^[A-Z]{4}\d{8}$/;
                if (reRegie.exec(keyVal)) {
                    document.getElementById("search_mode").value = "search_hin";
                }

                //phone xxx-xxx-xxxx with varying delimiters
                const rePhone = /^\d{3}[-\s.]\d{3}[-\s.]\d{4}$/;
                if (rePhone.exec(keyVal)) {
                    const area = keyVal.substring(0, 3);
                    const p1 = keyVal.substring(4, 7);
                    const p2 = keyVal.substring(8);
                    const phone = area + "-" + p1 + "-" + p2;
                    keyObj.value = phone;
                    document.getElementById("search_mode").value = "search_phone";
                }

                // DOB yyyy-mm-dd with varying delimiters
                const reDOB = /^(19|20)\d\d([\/.-\s])(0[1-9]|1[012])[\/.-\s](0[1-9]|[12]\d|3[01])$/;
                if (reDOB.exec(keyVal)) {
                    const yyyy = keyVal.substring(0, 4);
                    const mm = keyVal.substring(5, 7);
                    const dd = keyVal.substring(8);
                    const dob = yyyy + "-" + mm + "-" + dd;
                    keyObj.value = dob;
                    document.getElementById("search_mode").value = "search_dob";
                }

                //swipe pattern
                if (keyVal.indexOf('%b610054') == 0 && keyVal.length > 18) {
                    keyObj.value = keyVal.substring(8, 18);
                    document.getElementById("search_mode").value = "search_hin";
                }
            }

            function locale() {
                // add style for multisites location
                var loc = document.forms['ADDAPPT'].location;
                if (loc.nodeName.toUpperCase() == 'SELECT') loc.style.backgroundColor = loc.options[loc.selectedIndex].style.backgroundColor;
            }

        </script>
    </head>
    <body onLoad="setfocus(); moveAppt(); locale();">
    <div class="container">
        <% if (timeoutSecs > 0) { %>
        <div id="lock_notification">
            <span title="">Viewers: N/A</span>
        </div>
        <% } %>
        <%
            String patientStatus = "";
            String rosterStatus = "";
            String disabled = "";
            String address = "";
            String province = "";
            String city = "";
            String postal = "";
            String phone = "";
            String phone2 = "";
            String email = "";
            String hin = "";
            String dob = "";
            String sex = "";
            String alert = "";

            //to show Alert msg

            boolean bMultipleSameDayGroupAppt = false;
            String displayStyle = "display:none";
            String myGroupNo = providerPreference.getMyGroupNo();

            if (props.getProperty("allowMultipleSameDayGroupAppt", "").equalsIgnoreCase("no")) {

                String demographicNo = request.getParameter("demographic_no");

                if (!bFirstDisp && (demographicNo != null) && (!demographicNo.equals(""))) {


                    appts = appointmentDao.search_group_day_appt(myGroupNo, Integer.parseInt(demographicNo), apptDate);

                    long numSameDayGroupAppts = appts.size() > 0 ? new Long(appts.size()) : 0;
                    bMultipleSameDayGroupAppt = (numSameDayGroupAppts > 0);
                }

                if (bMultipleSameDayGroupAppt) {
                    displayStyle = "display:block";
                }
            }
        %>
        <div id="jsAlertBanner" class="alert alert-danger alert-dismissible" style="display:none" role="alert">
            <span id="jsAlertText"></span>
            <button type="button" class="btn-close" onclick="this.closest('.alert').style.display='none'" aria-label="Close"></button>
        </div>
        <div id="tooManySameDayGroupApptWarning" style="<%=displayStyle%>">
            <div class="alert alert-danger alert-dismissible" role="alert">
                <h4><fmt:setBundle basename='oscarResources'/><fmt:message key='appointment.addappointment.titleMultipleGroupDayBooking'/></h4>
                <fmt:setBundle basename='oscarResources'/><fmt:message key='appointment.addappointment.MultipleGroupDayBooking'/>
                <button type="button" class="btn-close" onclick="document.getElementById('tooManySameDayGroupApptWarning').style.display='none'" aria-label="Close"></button>
            </div>
        </div>
        <%
            if (!bFirstDisp && request.getParameter("demographic_no") != null && !request.getParameter("demographic_no").equals("")) {
                Demographic d = demographicDao.getDemographic(request.getParameter("demographic_no"));
                if (d != null) {
                    patientStatus = d.getPatientStatus();
                    address = d.getAddress();
                    city = d.getCity();
                    province = d.getProvince();
                    postal = d.getPostal();
                    phone = d.getPhone();
                    phone2 = d.getPhone2();
                    email = d.getEmail();
                    String year_of_birth = d.getYearOfBirth();
                    String month_of_birth = d.getMonthOfBirth();
                    String date_of_birth = d.getDateOfBirth();
                    dob = "(" + year_of_birth + "-" + month_of_birth + "-" + date_of_birth + ")";
                    sex = d.getSex();
                    hin = d.getHin();
                    String ver = d.getVer();
                    hin = hin + " " + ver;

                    DemographicCust demographicCust = demographicCustDao.find(Integer.parseInt(request.getParameter("demographic_no")));
                    if (demographicCust != null) alert = demographicCust.getAlert();

                    if (patientStatus == null || patientStatus.equalsIgnoreCase("AC")) {
                        patientStatus = "";
                    } else if (patientStatus.equalsIgnoreCase("FI") || patientStatus.equalsIgnoreCase("DE") || patientStatus.equalsIgnoreCase("IN")) {
                        disabled = "disabled";
                    }

                    rosterStatus = d.getRosterStatus();
                    if (rosterStatus == null || rosterStatus.equalsIgnoreCase("RO")) {
                        rosterStatus = "";
                    }
                }
            }
        %>
        <%-- Patient status banner: always rendered so JavaScript can show/hide when patient is selected via autocomplete --%>
        <%
            String statusExp = " null-undefined\n IN-inactive ID-deceased OP-out patient\n NR-not signed\n FS-fee for service\n TE-terminated\n SP-self pay\n TP-third party";
            boolean showStatusBanner = !patientStatus.equals("") || !rosterStatus.equals("");
        %>
        <fmt:setBundle basename="oscarResources"/>
        <div id="patientStatusBanner" class="alert alert-info alert-dismissible"
             title='<%=Encode.forHtmlAttribute(statusExp)%>'
             data-roster-label="<fmt:message key="Appointment.msgRosterStatus"/>"
             style="<%= showStatusBanner ? "" : "display:none" %>" role="alert">
            <span id="patientStatusText"><%=Encode.forHtmlContent(patientStatus)%>&nbsp;<fmt:message key="Appointment.msgRosterStatus"/>:&nbsp;<%=Encode.forHtmlContent(rosterStatus)%></span>
            <button type="button" class="btn-close" onclick="this.closest('.alert').style.display='none'" aria-label="Close"></button>
        </div>
        <%-- Patient alert banner: always rendered so JavaScript can show/hide it when patient is selected via autocomplete --%>
        <div id="patientAlertBanner" class="alert alert-warning alert-dismissible"<%= (alert == null || alert.isEmpty()) ? " style=\"display:none\"" : "" %> role="alert">
            <span id="patientAlertText"><%=Encode.forHtmlContent(alert != null ? alert : "")%></span>
            <button type="button" class="btn-close" onclick="this.closest('.alert').style.display='none'" aria-label="Close"></button>
        </div>
        <%


            if (apptnum != 0) {

        %>
        <div class="alert alert-danger alert-dismissible" role="alert">
            <h4><fmt:setBundle basename='oscarResources'/><fmt:message key='appointment.addappointment.msgDoubleBooking'/></h4>
            <%
                if (bDnb) out.println("<br/>You CANNOT book an appointment on this time slot.");
            %>
            <button type="button" class="btn-close" onclick="this.closest('.alert').style.display='none'" aria-label="Close"></button>
        </div>


        <% } %>

        <% if (billingRecommendations.size() > 0) { %>
        <table width="100%" class="alert alert-info">
            <% for (String recommendation : billingRecommendations) { %>
            <tr>
                <td><%=Encode.forHtmlContent(recommendation)%>
                </td>
            </tr>
            <% } %>
        </table>
        <% } %>

        <form name="ADDAPPT" id="addappt" method="post"
              action="<%=request.getContextPath()%>/appointment/appointmentcontrol.jsp"
              onsubmit="return(onAdd())">
            <input type="hidden" name="displaymode" value="">
            <input type="hidden" name="year" value="<%=request.getParameter("year") %>">
            <input type="hidden" name="month" value="<%=request.getParameter("month") %>">
            <input type="hidden" name="day" value="<%=request.getParameter("day") %>">
            <input type="hidden" name="fromAppt" value="1">


            <div class="page-header-bar" id="header">
                <h4 class="page-header-title">
                    <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" viewBox="0 0 16 16" class="page-header-icon">
                        <path d="M3.5 0a.5.5 0 0 1 .5.5V1h8V.5a.5.5 0 0 1 1 0V1h1a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H2a2 2 0 0 1-2-2V3a2 2 0 0 1 2-2h1V.5a.5.5 0 0 1 .5-.5M1 4v10a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V4z"/>
                    </svg>
                    &nbsp;<% if (isMobileOptimized) { %><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.msgMainLabelMobile"/>
                    <% } else { %><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.msgMainLabel"/>
                    <% out.println("(" + Encode.forHtmlContent(pFirstname) + " " + Encode.forHtmlContent(pLastname) + ")"); %>
                    <% } %>
                </h4>
            </div>
            <div class="bg-light border rounded p-2">
                <div class="row">
                    <%-- Left column: Date, Time, Duration, Patient, Reason, Location, Creator --%>
                    <div class="col-md-6">
                        <div class="mb-2 row">
                            <label class="col-sm-4 col-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formDate"/>&nbsp;<span style="color:brown;">(<%=dateString1%>)</span>:</label>
                            <div class="col-sm-8">
                                <input type="date" class="form-control form-control-sm" name="appointment_date"
                                       value="<%=dateString2%>"
                                       onChange="checkDateTypeIn(this);checkPageLock()">
                            </div>
                        </div>
                        <div class="mb-2 row">
                            <label class="col-sm-4 col-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formStartTime"/>:</label>
                            <div class="col-sm-8">
                                <input type="time" name="start_time" class="form-control form-control-sm"
                                       value='<%=request.getParameter("start_time")%>'
                                       onChange="checkTimeTypeIn(this);checkPageLock()">
                            </div>
                        </div>
                        <div class="mb-2 row">
                            <label class="col-sm-4 col-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formDuration"/>:</label>
                            <div class="col-sm-8">
                                <input type="number" name="duration" id="duration" class="form-control form-control-sm"
                                       value="<%=duration%>" onChange="checkPageLock()" onblur="calculateEndTime();">
                                <input type="hidden" name="end_time"
                                       value='<%=request.getParameter("end_time")%>'
                                       onChange="checkTimeTypeIn(this)">
                            </div>
                        </div>
                        <div class="mb-2 row">
                            <label class="col-sm-4 col-form-label" for="keyword">
                                Patient:
                                <input type="button" value="(<fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.doNotBook"/>)"
                                   class="btn btn-link btn-sm p-0" onclick="onNotBook();">
                            </label>
                            <div class="col-sm-8">
                                <div class="input-group input-group-sm">
                                <%
                                    String name = "";
                                    name = String.valueOf((bFirstDisp && !bFromWL) ? "" : request.getParameter("name") == null ? session.getAttribute("appointmentname") == null ? "" : session.getAttribute("appointmentname") : request.getParameter("name"));
                                %>
                                    <input type="hidden" name="demographic_no" id="demographic_no"
                                           value='<%=(bFirstDisp && !bFromWL) ? "" : Encode.forHtmlAttribute(StringUtils.defaultString(request.getParameter("demographic_no")))%>'>
                                    <input type="text" name="keyword" id="keyword" class="form-control form-control-sm"
                                        value="<%=Encode.forHtmlAttribute(name)%>"
                                        placeholder="<fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formNamePlaceholder"/>">
                                    <button type="submit" name="searchBtn" id="searchBtn" class="btn btn-secondary btn-sm"
                                           onclick="parseSearch(); document.forms['ADDAPPT'].displaymode.value='Search ';"
                                           title="<fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.btnSearch"/>"><i class="fa-solid fa-magnifying-glass"></i></button>
                                </div>
                            </div>
                        </div>
                        <div class="mb-2 row">
                            <label class="col-sm-4 col-form-label" for="reasonCode"><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formReason"/>:</label>
                            <div class="col-sm-8">
                                <select name="reasonCode" id="reasonCode" class="form-select form-select-sm">
                                    <c:choose>
                                        <c:when test="${ not empty reasonCodes  }">
                                            <c:forEach items="${ reasonCodes.items }" var="reason">
                                                <c:if test="${ reason.active }">
                                                    <option value="${ reason.id }" id="${ reason.value }">
                                                        <c:out value="${ reason.label }"/>
                                                    </option>
                                                </c:if>
                                            </c:forEach>
                                        </c:when>
                                        <c:otherwise>
                                            <option value="-1">Other</option>
                                        </c:otherwise>
                                    </c:choose>
                                </select>
                                <textarea id="reason" name="reason" class="form-control form-control-sm mt-1" tabindex="2" rows="2"
                                          style="resize:none;"
                                          placeholder="<fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formReason"/>"
                                          maxlength="80"><%=bFirstDisp ? "" : "".equals(request.getParameter("reason")) ? "" : Encode.forHtmlContent(request.getParameter("reason"))%></textarea>
                            </div>
                        </div>
                        <%
                            boolean bMoreAddr = bMultisites ? true : props.getProperty("scheduleSiteID", "").equals("") ? false : true;
                            String tempLoc = "";
                            if (bFirstDisp && bMoreAddr) {
                                tempLoc = (new JdbcApptImpl()).getLocationFromSchedule(dateString2, curProvider_no);
                            }
                            String loc = bFirstDisp ? tempLoc : request.getParameter("location");
                            String colo = bMultisites
                                    ? ApptUtil.getColorFromLocation(sites, loc)
                                    : bMoreAddr ? ApptUtil.getColorFromLocation(props.getProperty("scheduleSiteID", ""), props.getProperty("scheduleSiteColor", ""), loc) : "white";
                        %>
                        <div class="mb-2 row">
                            <label class="col-sm-4 col-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formLocation"/>:</label>
                            <div class="col-sm-8">
                                <% if (bMultisites) { %>
                                <select tabindex="4" class="form-select form-select-sm" name="location"
                                        style="background-color: <%=colo%>"
                                        onchange='this.style.backgroundColor=this.options[this.selectedIndex].style.backgroundColor'>
                                    <% for (Site s : sites) { %>
                                    <option value="<%=Encode.forHtmlAttribute(s.getName())%>"
                                            class="<%=s.getShortName()%>"
                                            style="background-color: <%=s.getBgColor()%>" <%=s.getName().equals(loc) ? "selected" : "" %>><%=Encode.forHtmlContent(s.getName())%>
                                    </option>
                                    <% } %>
                                </select>
                                <% } else if (locationEnabled) { %>
                                <select name="location" class="form-select form-select-sm">
                                    <%
                                        String sessionLocation = "";
                                        ProgramProvider programProvider = programManager2.getCurrentProgramInDomain(loggedInInfo, loggedInInfo.getLoggedInProviderNo());
                                        if (programProvider != null && programProvider.getProgram() != null) {
                                            sessionLocation = programProvider.getProgram().getId().toString();
                                        }
                                        if (programs != null && !programs.isEmpty()) {
                                            for (Program program : programs) {
                                                String description = StringUtils.isBlank(program.getLocation()) ? program.getName() : program.getLocation();
                                    %>
                                    <option value="<%=program.getId()%>" <%=program.getId().toString().equals(sessionLocation) ? "selected='selected'" : ""%>><%=Encode.forHtmlAttribute(description)%>
                                    </option>
                                    <% }
                                    }
                                    %>
                                </select>
                                <% } else { %>
                                <input type="text" name="location" tabindex="4" value="<%=Encode.forHtmlAttribute(loc != null ? loc : "")%>" class="form-control form-control-sm">
                                <% } %>
                            </div>
                        </div>
                        <div class="mb-2 row">
                            <label class="col-sm-4 col-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formCreator"/>:</label>
                            <div class="col-sm-8">
                                <input type="text" name="user_id" class="form-control form-control-sm"
                                       value='<%=bFirstDisp?(Encode.forHtmlAttribute(userlastname)+", "+Encode.forHtmlAttribute(userfirstname)):"".equals(request.getParameter("user_id"))?"Unknown":Encode.forHtmlAttribute(request.getParameter("user_id"))%>'
                                       readonly="readonly">
                            </div>
                        </div>
                        <% if (pros.isPropertyActive("mc_number")) { %>
                        <div class="mb-2 row">
                            <label class="col-sm-4 col-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formMC"/>:</label>
                            <div class="col-sm-8">
                                <input type="text" name="appt_mc_number" tabindex="5" class="form-control form-control-sm"/>
                            </div>
                        </div>
                        <% } %>
                    </div>

                    <%-- Right column: Status, Type, Doctor, Notes, Resources, DateTime, Critical, Email --%>
                    <div class="col-md-6">
                        <div class="mb-2 row">
                            <label class="col-sm-4 col-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formStatus"/>:</label>
                            <div class="col-sm-8">
                                <% if (strEditable != null && strEditable.equalsIgnoreCase("yes")) { %>
                                <select class="form-select form-select-sm" name="status" style="background-color:<%=(allStatus.get(0)).getColor()%>" onchange='this.style.backgroundColor=this.options[this.selectedIndex].style.backgroundColor'>
                                    <% for (int i = 0; i < allStatus.size(); i++) { %>
                                    <option class="<%=(allStatus.get(i)).getStatus()%>"
                                            style="background-color:<%=(allStatus.get(i)).getColor()%>"
                                            value="<%=(allStatus.get(i)).getStatus()%>"
                                            <%=(allStatus.get(i)).getStatus().equals(request.getParameter("status")) ? "SELECTED" : ""%>><%=(allStatus.get(i)).getDescription()%>
                                    </option>
                                    <% } %>
                                </select>
                                <% } else { %>
                                <input type="text" name="status" class="form-control form-control-sm"
                                       value='<%=bFirstDisp?"t":request.getParameter("status")==null?"":request.getParameter("status").equals("")?"":request.getParameter("status")%>'>
                                <% } %>
                            </div>
                        </div>
                        <div class="mb-2 row">
                            <label class="col-sm-4 col-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formType"/>:</label>
                            <div class="col-sm-8">
                                <select class="form-select form-select-sm" name="type" id="type"
                                        title="<fmt:setBundle basename="oscarResources"/><fmt:message key="billing.billingCorrection.msgSelectVisitType"/>">
                                <option data-dur="" data-reason=""></option>
                                <% AppointmentTypeDao appDao = SpringUtils.getBean(AppointmentTypeDao.class);
                                    List<AppointmentType> types = appDao.listAll();
                                    for (int j = 0; j < types.size(); j++) {
                                %>
                                    <option data-dur="<%= types.get(j).getDuration() %>"
                                            data-reason="<%= Encode.forHtmlAttribute(types.get(j).getReason()) %>"
                                            data-loc="<%= Encode.forHtmlAttribute(types.get(j).getLocation()) %>"
                                            data-notes="<%= Encode.forHtmlAttribute(types.get(j).getNotes()) %>"
                                            data-resources="<%= Encode.forHtmlAttribute(types.get(j).getResources()) %>">
                                        <%=Encode.forHtml(types.get(j).getName()) %>
                                    </option>
                                <% } %>
                                </select>
                            </div>
                        </div>
                        <div class="mb-2 row">
                            <label class="col-sm-4 col-form-label" for="mrp"><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formDoctor"/>:</label>
                            <div class="col-sm-8">
                                <input type="text" id="mrp" class="form-control form-control-sm"
                                       value="<%=bFirstDisp ? "" : StringEscapeUtils.escapeHtml4(providerBean.getProperty(curDoctor_no,""))%>" readonly="readonly">
                            </div>
                        </div>
                        <div class="mb-2 row">
                            <label class="col-sm-4 col-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formNotes"/>:</label>
                            <div class="col-sm-8">
                                <textarea class="form-control form-control-sm" name="notes" tabindex="3" rows="2" style="resize:none;"
                                          placeholder="<fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formNotes"/>"
                                          maxlength="255"><%=bFirstDisp ? "" : Encode.forHtmlContent(StringUtils.defaultString(request.getParameter("notes")))%></textarea>
                            </div>
                        </div>
                        <div class="mb-2 row">
                            <label class="col-sm-4 col-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formResources"/>:</label>
                            <div class="col-sm-8">
                                <input type="text" name="resources" class="form-control form-control-sm"
                                       tabindex="6"
                                       value='<%=bFirstDisp?"":"".equals(request.getParameter("resources"))?"": Encode.forHtmlAttribute(StringUtils.defaultString(request.getParameter("resources")))%>'>
                            </div>
                        </div>
                        <div class="mb-2 row">
                            <label class="col-sm-4 col-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formDateTime"/>:</label>
                            <div class="col-sm-8">
                                <%
                                    GregorianCalendar now = new GregorianCalendar();
                                    GregorianCalendar cal = (GregorianCalendar) now.clone();
                                    cal.add(GregorianCalendar.DATE, 1);
                                    String strDateTime = now.get(Calendar.YEAR) + "-" + (now.get(Calendar.MONTH) + 1) + "-" + now.get(Calendar.DAY_OF_MONTH) + " "
                                            + now.get(Calendar.HOUR_OF_DAY) + ":" + now.get(Calendar.MINUTE) + ":" + now.get(Calendar.SECOND);
                                    LocalDateTime create = now.toZonedDateTime().toLocalDateTime();
                                    DateTimeFormatter pattern = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(request.getLocale()).withZone(ZoneId.systemDefault());
                                %>
                                <input type="hidden" name="createdatetime" value="<%=strDateTime%>">
                                <span class="form-control-plaintext form-control-sm"><%=create.format(pattern)%></span>
                                <input type="hidden" name="provider_no" value="<%=curProvider_no%>">
                                <input type="hidden" name="dboperation" value="search_titlename">
                                <input type="hidden" name="creator"
                                       value='<%=Encode.forHtmlAttribute(userlastname)+", "+Encode.forHtmlAttribute(userfirstname)%>'>
                                <input type="hidden" name="remarks" value="">
                            </div>
                        </div>
                        <div class="mb-2 row">
                            <label class="col-sm-4 col-form-label" for="urgency">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formCritical"/>
                                <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" fill="currentColor" class="bi bi-shield-exclamation" viewBox="0 0 16 16">
                                    <path d="M5.338 1.59a61 61 0 0 0-2.837.856.48.48 0 0 0-.328.39c-.554 4.157.726 7.19 2.253 9.188a10.7 10.7 0 0 0 2.287 2.233c.346.244.652.42.893.533q.18.085.293.118a1 1 0 0 0 .101.025 1 1 0 0 0 .1-.025q.114-.034.294-.118c.24-.113.547-.29.893-.533a10.7 10.7 0 0 0 2.287-2.233c1.527-1.997 2.807-5.031 2.253-9.188a.48.48 0 0 0-.328-.39c-.651-.213-1.75-.56-2.837-.855C9.552 1.29 8.531 1.067 8 1.067c-.53 0-1.552.223-2.662.524zM5.072.56C6.157.265 7.31 0 8 0s1.843.265 2.928.56c1.11.3 2.229.655 2.887.87a1.54 1.54 0 0 1 1.044 1.262c.596 4.477-.787 7.795-2.465 9.99a11.8 11.8 0 0 1-2.517 2.453 7 7 0 0 1-1.048.625c-.28.132-.581.24-.829.24s-.548-.108-.829-.24a7 7 0 0 1-1.048-.625 11.8 11.8 0 0 1-2.517-2.453C1.928 10.487.545 7.169 1.141 2.692A1.54 1.54 0 0 1 2.185 1.43 63 63 0 0 1 5.072.56"></path>
                                    <path d="M7.001 11a1 1 0 1 1 2 0 1 1 0 0 1-2 0M7.1 4.995a.905.905 0 1 1 1.8 0l-.35 3.507a.553.553 0 0 1-1.1 0z"></path>
                                </svg>:
                            </label>
                            <div class="col-sm-8">
                                <div class="form-check mt-1">
                                    <input type="checkbox" class="form-check-input" id="urgency" name="urgency" value="critical">
                                </div>
                            </div>
                        </div>
                        <% String emailReminder = pros.getProperty("emailApptReminder");
                            if ((emailReminder != null) && emailReminder.equalsIgnoreCase("yes")) { %>
                        <div class="mb-2 row">
                            <label class="col-sm-4 col-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formEmailReminder"/>:</label>
                            <div class="col-sm-8">
                                <div class="form-check mt-1">
                                    <input type="checkbox" class="form-check-input" name="emailPt" value="email reminder">
                                </div>
                            </div>
                        </div>
                        <% } %>
                        <% if (pros.isPropertyActive("mc_number")) { %>
                        <div class="mb-2 row">
                            <label class="col-sm-4 col-form-label"><fmt:message key="Appointment.formMC"/>:</label>
                            <div class="col-sm-8">
                                <input type="text" name="appt_mc_number" class="form-control form-control-sm"/>
                            </div>
                        </div>
                        <% } %>
                    </div>
                </div>
                <input type="hidden" name="orderby" value="last_name, first_name">
                <%
                    String searchMode = request.getParameter("search_mode");
                    if (searchMode == null || searchMode.isEmpty()) {
                        searchMode = OscarProperties.getInstance().getProperty("default_search_mode", "search_name");
                    }
                %>
                <input type="hidden" name="search_mode" id="search_mode" value="<%=searchMode%>">
                <input type="hidden" name="originalpage"
                       value="<%=request.getContextPath() %>/appointment/addappointment.jsp">
                <input type="hidden" name="limit1" value="0">
                <input type="hidden" name="limit2" value="5">
                <input type="hidden" name="ptstatus" value="active">
                <input type="hidden" name="outofdomain"
                       value="<%=OscarProperties.getInstance().getProperty("pmm.client.search.outside.of.domain.enabled","true")%>">
                <!--input type="hidden" name="displaymode" value="Search " -->


                <%String demoNo = request.getParameter("demographic_no");%>
                <div>


                    <% if (!(bDnb || bMultipleSameDayGroupAppt)) { %>

                    <% if (!props.getProperty("allowMultipleSameDayGroupAppt", "").equalsIgnoreCase("no")) {%>
                    <input type="submit" id="addButton" class="btn btn-primary"
                           onclick="document.forms['ADDAPPT'].displaymode.value='Add Appointment'"
                           tabindex="7"
                           value="<% if (isMobileOptimized) { %><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.btnAddAppointmentMobile"/>
                   <% } else { %><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.btnAddAppointment"/><% } %>"
                            <%=disabled%>>
                    <input type="submit" id="groupButton" class="btn"
                           onclick="document.forms['ADDAPPT'].displaymode.value='Group Appt'"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.btnGroupAppt"/>"
                            <%=disabled%>>
                    <% }


                    <% } %>


                    <%
                        if (bFirstDisp && apptObj != null) {

                            long numSameDayGroupApptsPaste = 0;

                            if (props.getProperty("allowMultipleSameDayGroupAppt", "").equalsIgnoreCase("no")) {
                                String[] sqlParam = new String[3];
                                sqlParam[0] = myGroupNo; //schedule group


                                //convert empty string to placeholder demographic number "0" to prevent NumberFormatException when cutting/copying an empty appointmnet.
                                if (StringUtils.isBlank(apptObj.getDemographic_no())) {
                                    apptObj.setDemographic_no("0");//demographic numbers start at 1
                                }
                                sqlParam[1] = apptObj.getDemographic_no();
                                sqlParam[2] = dateString2;
                                appts = appointmentDao.search_group_day_appt(myGroupNo, Integer.parseInt(apptObj.getDemographic_no()), apptDate);
                                numSameDayGroupApptsPaste = appts.size() > 0 ? new Long(appts.size()) : 0;
                            }
                    %>
                    <input type="button" id="pasteButton" value="Paste" class="btn"
                           onclick="pasteAppt(<%=(numSameDayGroupApptsPaste > 0)%>);">
                    <% }%>

                    <% if (!props.getProperty("allowMultipleSameDayGroupAppt", "").equalsIgnoreCase("no")) {%>
                    <input type="button" id="apptRepeatButton" class="btn"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.btnRepeat"/>"
                           onclick="onButRepeat()" <%=disabled%>>
                    <% } %>
                    <input type="RESET" id="backButton" class="btn btn-link"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnCancel"/>" onClick="cancelPageLock();window.close();">

                </div>
            </div>

</form>

        <div class="row mt-3 g-3">
            <%if (bFromWL && demoNo != null && demoNo.length() > 0) {%>
            <div class="col-md-4">
                <div class="card">
                    <div class="card-header">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.msgDemgraphics"/>
                        <a title="Master File"
                           onclick="popup(700,1000,'<%=request.getContextPath() %>/demographic/demographiccontrol.jsp?demographic_no=<%=demoNo%>&amp;displaymode=edit&amp;dboperation=search_detail','master')"
                           href="javascript: function myFunction() {return false; }"><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.btnEdit"/></a>
                        &nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.msgSex"/>: <%=sex%>
                        &nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.msgDOB"/>: <%=dob%>
                    </div>
                    <ul class="list-group list-group-flush">
                        <li class="list-group-item"><strong><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.msgHin"/>:</strong> <%=Encode.forHtmlContent(hin.replace("null", ""))%></li>
                        <li class="list-group-item"><strong><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.msgAddress"/>:</strong> <%=Encode.forHtmlContent(StringUtils.trimToEmpty(address))%>, <%=Encode.forHtmlContent(StringUtils.trimToEmpty(city))%>, <%=Encode.forHtmlContent(StringUtils.trimToEmpty(province))%>, <%=Encode.forHtmlContent(StringUtils.trimToEmpty(postal))%></li>
                        <li class="list-group-item"><strong><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.msgPhone"/>:</strong> <b><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.msgH"/></b>: <%=Encode.forHtmlContent(StringUtils.trimToEmpty(phone))%> <b><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.msgW"/></b>: <%=Encode.forHtmlContent(StringUtils.trimToEmpty(phone2))%></li>
                        <li class="list-group-item"><strong><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.msgEmail"/>:</strong> <%=Encode.forHtmlContent(StringUtils.trimToEmpty(email))%></li>
                    </ul>
                </div>
            </div>
            <%}%>

            <%
                String formTblProp = props.getProperty("appt_formTbl", "");
                String[] formTblNames = formTblProp.split(";");
                int numForms = 0;
                List<String[]> formResults = new ArrayList<>();
                for (String formTblName : formTblNames) {
                    if ((formTblName != null) && !formTblName.equals("")) {
                        for (EncounterForm ef : encounterFormDao.findByFormTable(formTblName)) {
                            String formName = ef.getFormName();
                            boolean formComplete = false;
                            EctFormData.PatientForm[] ptForms = EctFormData.getPatientFormsFromLocalAndRemote(loggedInInfo, demoNo, formTblName);
                            if (ptForms.length > 0) {
                                formComplete = true;
                            }
                            formResults.add(new String[]{formName, formComplete ? "true" : "false"});
                            numForms++;
                        }
                    }
                }
                if (numForms > 0) {
            %>
            <div class="col-md-4">
                <div class="card">
                    <div class="card-header"><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.msgFormsSaved"/></div>
                    <ul class="list-group list-group-flush">
                        <% for (String[] fr : formResults) { %>
                        <li class="list-group-item"><strong><%=Encode.forHtmlContent(fr[0])%>:</strong>
                            <% if ("true".equals(fr[1])) { %>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.msgFormCompleted"/>
                            <% } else { %>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.msgFormNotCompleted"/>
                            <% } %>
                        </li>
                        <% } %>
                    </ul>
                </div>
            </div>
            <% } %>

            <div class="<%= (bFromWL && demoNo != null && demoNo.length() > 0) ? "col-md-4" : "col-md-12" %>">
                <div class="card">
                    <div class="card-header"><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.msgOverview"/></div>
                    <div class="card-body p-0">
                        <table class="table table-sm table-striped mb-0">
                            <thead>
                                <tr>
                                    <th><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formDate"/></th>
                                    <th><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formStartTime"/></th>
                                    <th><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.msgProvider"/></th>
                                    <th><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.msgComments"/></th>
                                </tr>
                            </thead>
                            <tbody>
                            <%
                                int iRow = 0;
                                if (bFromWL && demoNo != null && demoNo.length() > 0) {
                                    Object[] param2 = new Object[3];
                                    param2[0] = demoNo;
                                    Calendar cal2 = Calendar.getInstance();
                                    param2[1] = new java.sql.Date(cal2.getTime().getTime());
                                    java.util.Date start = cal2.getTime();
                                    cal2.add(Calendar.YEAR, 1);
                                    java.util.Date end = cal2.getTime();
                                    param2[2] = new java.sql.Date(cal2.getTime().getTime());

                                    for (Object[] result : appointmentDao.search_appt_future(Integer.parseInt(demoNo), start, end)) {
                                        Appointment a = (Appointment) result[0];
                                        p = (Provider) result[1];
                                        iRow++;
                                        if (iRow > iPageSize) break;
                            %>
                                <tr>
                                    <td><%=ConversionUtils.toDateString(a.getAppointmentDate())%></td>
                                    <td><%=ConversionUtils.toTimeString(a.getStartTime())%></td>
                                    <td><%=p.getFormattedName()%></td>
                                    <td><%=a.getStatus() == null ? "" : (a.getStatus().startsWith("N") ? "No Show" : (a.getStatus().startsWith("C") ? "Cancelled" : ""))%></td>
                                </tr>
                            <%
                                    }
                                    iRow = 0;
                                    cal2 = Calendar.getInstance();
                                    cal2.add(Calendar.YEAR, -1);

                                    for (Object[] result : appointmentDao.search_appt_past(Integer.parseInt(demoNo), start, cal2.getTime())) {
                                        Appointment a = (Appointment) result[0];
                                        p = (Provider) result[1];
                                        iRow++;
                                        if (iRow > iPageSize) break;
                            %>
                                <tr>
                                    <td><%=ConversionUtils.toDateString(a.getAppointmentDate())%></td>
                                    <td><%=ConversionUtils.toTimeString(a.getStartTime())%></td>
                                    <td><%=p.getFormattedName()%></td>
                                    <td><%=a.getStatus() == null ? "" : (a.getStatus().startsWith("N") ? "No Show" : (a.getStatus().startsWith("C") ? "Cancelled" : ""))%></td>
                                </tr>
                            <%
                                    }
                                }
                            %>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </div>
    </div>
    </body>

</html>
