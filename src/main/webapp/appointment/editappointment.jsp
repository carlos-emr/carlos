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
<!DOCTYPE html>

<%@page import="ca.openosp.openo.casemgmt.service.CaseManagementManager" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
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

<%@page import="ca.openosp.openo.commn.dao.ProviderDataDao" %>
<%@page import="ca.openosp.openo.managers.DemographicManager" %>

<%@page import="ca.openosp.openo.appt.status.service.impl.AppointmentStatusMgrImpl" %>
<%
    if (session.getAttribute("user") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");

%>


<%@ page import="java.util.*" %>

<%@ page import="java.math.*" %>
<%@ page import="java.time.LocalDateTime" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.time.format.FormatStyle" %>
<%@ page import="java.time.ZoneId" %>
<%@ page import="ca.openosp.openo.appt.*" %>
<%@ page import="ca.openosp.openo.util.*" %>
<%@ page import="ca.openosp.openo.appt.status.service.AppointmentStatusMgr" %>
<%@ page import="ca.openosp.OscarProperties" %>
<%@ page import="ca.openosp.openo.commn.OtherIdManager" %>
<%@ page import="ca.openosp.openo.PMmodule.dao.ProviderDao" %>
<%@ page import="ca.openosp.openo.commn.model.*" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.utility.SessionConstants" %>
<%@ page import="ca.openosp.openo.commn.dao.BillingONCHeader1Dao" %>
<%@ page import="ca.openosp.openo.commn.dao.DemographicCustDao" %>
<%@ page import="ca.openosp.openo.commn.dao.EncounterFormDao" %>
<%@ page import="ca.openosp.openo.commn.dao.OscarAppointmentDao" %>
<%@ page import="ca.openosp.openo.commn.dao.SiteDao" %>
<%@ page import="ca.openosp.openo.commn.dao.BillingONExtDao" %>
<%@ page import="ca.openosp.openo.PMmodule.model.Program" %>
<%@ page import="ca.openosp.openo.PMmodule.service.ProviderManager" %>
<%@ page import="ca.openosp.openo.PMmodule.service.ProgramManager" %>
<%@ page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="ca.openosp.openo.managers.LookupListManager" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>
<%@ page import="ca.openosp.openo.encounter.data.EctFormData" %>
<%@ page import="ca.openosp.openo.billings.ca.on.data.BillingDataHlp" %>
<%@ page import="ca.openosp.openo.commn.dao.AppointmentTypeDao" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.appt.ApptUtil" %>
<%@ page import="ca.openosp.openo.appt.ApptData" %>
<%@ page import="ca.openosp.openo.demographic.data.DemographicData" %>
<%@ page import="ca.openosp.openo.casemgmt.model.CaseManagementNoteLink" %>
<%@ page import="ca.openosp.openo.util.ConversionUtils" %>
<%@ page import="ca.openosp.openo.commn.model.*" %>
<%@ page import="ca.openosp.openo.commn.IsPropertiesOn" %>


<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>
<%

    String curProvider_no = Encode.forHtmlAttribute(request.getParameter("provider_no"));
    String appointment_no = Encode.forHtmlAttribute(request.getParameter("appointment_no"));
    String curUser_no = (String) session.getAttribute("user");
    String userfirstname = (String) session.getAttribute("userfirstname");
    String userlastname = (String) session.getAttribute("userlastname");
    String deepcolor = "#CCCCFF", weakcolor = "#EEEEFF";
    String origDate = null;

    boolean bFirstDisp = true; //this is the first time to display the window
    if (request.getParameter("bFirstDisp") != null) bFirstDisp = ("true".equals(request.getParameter("bFirstDisp")));

    String mrpName = "";
    DemographicCustDao demographicCustDao = (DemographicCustDao) SpringUtils.getBean(DemographicCustDao.class);
    EncounterFormDao encounterFormDao = SpringUtils.getBean(EncounterFormDao.class);
    ProviderPreference providerPreference = (ProviderPreference) session.getAttribute(SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE);
    DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    OscarAppointmentDao appointmentDao = SpringUtils.getBean(OscarAppointmentDao.class);
    ProviderDataDao providerDao = SpringUtils.getBean(ProviderDataDao.class);
    SiteDao siteDao = SpringUtils.getBean(SiteDao.class);
    ProviderDao pDao = SpringUtils.getBean(ProviderDao.class);
    BillingONCHeader1Dao cheader1Dao = SpringUtils.getBean(BillingONCHeader1Dao.class);

    ProviderManager providerManager = SpringUtils.getBean(ProviderManager.class);
    ProgramManager programManager = SpringUtils.getBean(ProgramManager.class);
    //String demographic_nox = (String)session.getAttribute("demographic_nox");
    String demographic_nox = request.getParameter("demographic_no");
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    Demographic demographicTmp = demographicManager.getDemographic(loggedInInfo, demographic_nox);
    String proNoTmp = demographicTmp == null ? null : demographicTmp.getProviderNo();
    if (demographicTmp != null && proNoTmp != null && proNoTmp.length() > 0) {
        Provider providerTmp = pDao.getProvider(demographicTmp.getProviderNo());
        if (providerTmp != null) {
            mrpName = providerTmp.getFormattedName();
        }
    }

    String providerNo = loggedInInfo.getLoggedInProviderNo();
    Facility facility = loggedInInfo.getCurrentFacility();

    List<Program> programs = programManager.getActiveProgramByFacility(providerNo, facility.getId());

    LookupListManager lookupListManager = SpringUtils.getBean(LookupListManager.class);
    LookupList reasonCodes = lookupListManager.findLookupListByName(loggedInInfo, "reasonCode");
    pageContext.setAttribute("reasonCodes", reasonCodes);

    ApptData apptObj = ApptUtil.getAppointmentFromSession(request);

    List<BillingONCHeader1> cheader1s = null;
    if ("ON".equals(OscarProperties.getInstance().getProperty("billregion", "ON"))) {
        cheader1s = cheader1Dao.getBillCheader1ByDemographicNo(Integer.parseInt(demographic_nox));
    }

    BillingONExtDao billingOnExtDao = (BillingONExtDao) SpringUtils.getBean(BillingONExtDao.class);
    OscarProperties pros = OscarProperties.getInstance();
    String strEditable = pros.getProperty("ENABLE_EDIT_APPT_STATUS");
    String apptStatusHere = pros.getProperty("appt_status_here");

    AppointmentStatusMgr apptStatusMgr = new AppointmentStatusMgrImpl();
    List allStatus = apptStatusMgr.getAllActiveStatus();

    String useProgramLocation = OscarProperties.getInstance().getProperty("useProgramLocation");
    String moduleNames = OscarProperties.getInstance().getProperty("ModuleNames");
    boolean caisiEnabled = moduleNames != null && org.apache.commons.lang3.StringUtils.containsIgnoreCase(moduleNames, "Caisi");
    boolean locationEnabled = caisiEnabled && (useProgramLocation != null && useProgramLocation.equals("true"));

    String annotation_display = CaseManagementNoteLink.DISP_APPOINTMENT;
    CaseManagementManager caseManagementManager = (CaseManagementManager) SpringUtils.getBean(CaseManagementManager.class);


    boolean isSiteSelected = false;
    boolean bMultisites = IsPropertiesOn.isMultisitesEnable();
    List<Site> sites = siteDao.getActiveSitesByProviderNo((String) session.getAttribute("user"));


    Appointment appt = null;
    String demono = "", chartno = "", phone = "", rosterstatus = "", alert = "", doctorNo = "";
    String strApptDate = bFirstDisp ? "" : request.getParameter("appointment_date");

    if (bFirstDisp) {
        appt = appointmentDao.find(Integer.parseInt(appointment_no));
        pageContext.setAttribute("appointment", appt);
    }

    String statusCode = request.getParameter("status");
    String importedStatus = null;
    if (bFirstDisp) {
        statusCode = appt.getStatus();
        importedStatus = appt.getImportedStatus();
    }

    int curSelect = -1;
    String signOrVerify = "";
    if (statusCode.length() >= 2) {
        signOrVerify = statusCode.substring(1, 2);
        statusCode = statusCode.substring(0, 1);
    }
    if (bFirstDisp) {
        demono = String.valueOf(appt.getDemographicNo());
    } else if (request.getParameter("demographic_no") != null && !request.getParameter("demographic_no").equals("")) {
        demono = request.getParameter("demographic_no");
    }

    //get chart_no from demographic table if it exists
    if (!demono.equals("0") && !demono.equals("")) {
        Demographic d = demographicManager.getDemographic(loggedInInfo, demono);
        if (d != null) {
            chartno = d.getChartNo();
            phone = d.getPhone();
            rosterstatus = d.getRosterStatus();
        }

        DemographicCust demographicCust = demographicCustDao.find(Integer.parseInt(demono));
        if (demographicCust != null) {
            alert = demographicCust.getAlert();
        }

    }

    OscarProperties props = OscarProperties.getInstance();
    String displayStyle = "display:none";
    String myGroupNo = providerPreference.getMyGroupNo();
    boolean bMultipleSameDayGroupAppt = false;
%>

<html>
    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.editappointment.title"/></title>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">


    <link href="${pageContext.request.contextPath}/library/bootstrap/3.0.0/css/bootstrap.min.css" rel="stylesheet" type="text/css">
        <%--<link href="${pageContext.request.contextPath}/css/font-awesome.min.css" rel="stylesheet">--%>
    <link href="${pageContext.request.contextPath}/library/jquery/jquery-ui-1.12.1.min.css" rel="stylesheet">
        <script src="${ pageContext.request.contextPath }/library/jquery/jquery-3.6.4.min.js"></script>
        <script src="${ pageContext.request.contextPath }/library/jquery/jquery-ui-1.12.1.min.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/library/bootstrap/3.0.0/js/bootstrap.min.js" ></script>

    <script src="<%= request.getContextPath() %>/js/global.js"></script>
    <script src="<%= request.getContextPath() %>/js/checkDate.js"></script>
    <script src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>

        <style>

    :root *:not(h2):not(h4):not(.input-group-btn .btn) {
        font-family: Arial, "Helvetica Neue", Helvetica, sans-serif !important;
        font-size: 12px;
        overscroll-behavior: none;
        -webkit-font-smoothing: antialiased;
        -moz-osx-font-smoothing: grayscale;
        line-height: 1 !important;
    }

    .form-wrapper {
        display:flex;
    }

    * table tr td, * table {
        border:none !important;
    }

    .panel {
       margin: 0 !important;
    }

    .panel-body {
        padding: 10px !important;
    }

    .ui-selectmenu-button.ui-button {
        width: 100% !important;
    }

    .ui-button {
        padding:10px !important;
    }

    .ui-icon {
        width: 12px !important;
        height: 12px !important;
    }

    textarea {
        width: 100%;
    }

            body, html {
                --color: #945;
                --size: 2rem;
                --border: calc(var(--size) * 0.125);
                --borderRadius: calc(var(--size) * 0.5);
                --labelSize: calc(var(--size) * 0.75);
                --margin: calc(var(--size) * 0.25);
                --marginLeft: calc(var(--size) + calc(var(--size) * 0.5));
            }

            .time {
                background-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='40' height='40'><circle cx='20' cy='20' r='18.5' fill='none' stroke='%23222' stroke-width='3' /><path d='M20,4 20,8 M4,20 8,20 M36,20 32,20 M20,36 20,32' stroke='%23bbb' stroke-width='1' /><circle cx='20' cy='20' r='2' fill='%23222' stroke='%23222' stroke-width='2' /></svg>");
                background-position: var(--margin) 50%;
                background-repeat: no-repeat;
                background-size: var(--size) var(--size);
                border: var(--border);
                border-radius: var(--borderRadius);
                color: #222;
                font-size: var(--size);
                padding: var(--margin) var(--margin) var(--margin) var(--marginLeft);
                transition: backgroundImage 0.25s;
            }
        </style>

        <% if (bMultisites) { %>
        <style>
            <% for (Site s:sites) { %>
            .<%=s.getShortName()%> {
                background-color: <%=s.getBgColor()%>;
            }

            <% } %>
        </style>
        <% } %>
        <% if (strEditable != null && strEditable.equalsIgnoreCase("yes")) { %>
        <style>
            <% for (int i = 0; i < allStatus.size(); i++) {
                if (((AppointmentStatus)allStatus.get(i)).getStatus().equals(statusCode)) { curSelect=i;}

%>
            .<%=((AppointmentStatus)allStatus.get(i)).getStatus()%> {
                background-color: <%=((AppointmentStatus)allStatus.get(i)).getColor()%>;
            }

            <% } %>
        </style>
        <% } %>
        <script>
            function updateTime() {
                const reTime = /^([0-1][0-9]|2[0-3]):[0-5][0-9]$/;
                const time = document.EDITAPPT.start_time.value;
                console.log("time=" + time);
                if (reTime.exec(time)) {
                    const minute = Number(time.substring(3, 5));
                    const minuteDeg = Number(time.substring(3, 5)) * 360 / 60;
                    const hourDeg = (Number(time.substring(0, 2)) % 12 + (minute / 60)) * 360 / 12;
                    console.log("minute=" + minute + " minDeg =" + minuteDeg);
                    document.getElementById("header").style.backgroundImage = `url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='40' height='40'><circle cx='20' cy='20' r='18.5' fill='none' stroke='%23222' stroke-width='3' /><path d='M20,4 20,8 M4,20 8,20 M36,20 32,20 M20,36 20,32' stroke='%23bbb' stroke-width='1' /><circle cx='20' cy='20' r='2' fill='%23222' stroke='%23222' stroke-width='2' /></svg>"), url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='40' height='40'><path d='M18.5,24.5 19.5,4 20.5,4 21.5,24.5 Z' fill='%23222' style='transform:rotate(` + minuteDeg + `deg); transform-origin: 50% 50%;' /></svg>"), url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='40' height='40'><path d='M18.5,24.5 19.5,8.5 20.5,8.5 21.5,24.5 Z' style='transform:rotate(` + hourDeg + `deg); transform-origin: 50% 50%;' /></svg>")`;
                }
            }


            jQuery.noConflict();

            function toggleView() {
                showHideItem('editAppointment');
                showHideItem('viewAppointment');
            }

            function demographicdetail(vheight, vwidth) {
                if (document.forms['EDITAPPT'].demographic_no.value === "") return;
                self.close();
                var page = "<%=request.getContextPath() %>/demographic/demographiccontrol.jsp?demographic_no=" + document.forms['EDITAPPT'].demographic_no.value + "&displaymode=edit&dboperation=search_detail";
                //windowprops = "height="+vheight+",width="+vwidth+",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=600,screenY=200,top=0,left=0";
                var popup = window.open(page, "demographic")//, windowprops);
            }

            function onButRepeat() {
                if (calculateEndTime()) {
                    document.forms[0].action = "appointmenteditrepeatbooking.jsp";
                    document.forms[0].submit();
                }
            }

            var saveTemp = 0;

            function setfocus() {
                this.focus();
                document.EDITAPPT.keyword.focus();
                document.EDITAPPT.keyword.select();
            }

            function onBlockFieldFocus(obj) {
                obj.blur();
                document.EDITAPPT.keyword.focus();
                document.EDITAPPT.keyword.select();
                window.alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.msgFillNameField"/>");
            }

            function labelprint(vheight, vwidth, varpage) {
                var page = "" + varpage;
                windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=600,screenY=200,top=0,left=0";
                var popup = window.open(page, "encounterhist", windowprops);
            }

            function onButDelete() {
                saveTemp = 1;
            }

            function onButUpdate() {
                saveTemp = 2;
            }

            function onButCancel() {
                var aptStat = document.EDITAPPT.status.value;
                if (aptStat.indexOf('B') === 0) {
                    var agree = confirm("<fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.editappointment.msgCanceledBilledConfirmation"/>");
                    if (agree) {
                        window.location = 'appointmentcontrol.jsp?buttoncancel=Cancel Appt&displaymode=Update Appt&appointment_no=<%=appointment_no%>';
                    }
                } else {
                    window.location = 'appointmentcontrol.jsp?buttoncancel=Cancel Appt&displaymode=Update Appt&appointment_no=<%=appointment_no%>';
                }
            }

            function upCaseCtrl(ctrl) {
                ctrl.value = ctrl.value.toUpperCase();
            }

            function onSub() {
                if (saveTemp === 1) {
                    var aptStat = document.EDITAPPT.status.value;
                    if (aptStat.indexOf('B') === 0) {
                        return (confirm("<fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.editappointment.msgDeleteBilledConfirmation"/>"));
                    } else {
                        return (confirm("<fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.editappointment.msgDeleteConfirmation"/>"));
                    }
                }
                if (saveTemp === 2) {
                    return calculateEndTime();
                } else
                    return true;
            }

            function calculateEndTime() {
                var stime = document.EDITAPPT.start_time.value;
                var vlen = stime.indexOf(':') === -1 ? 1 : 2;

                if (vlen === 1 && stime.length === 4) {
                    document.EDITAPPT.start_time.value = stime.substring(0, 2) + ":" + stime.substring(2);
                    stime = document.EDITAPPT.start_time.value;
                }

                if (stime.length != 5) {
                    alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.msgInvalidDateFormat"/>");
                    return false;
                }

                var shour = stime.substring(0, 2);
                var smin = stime.substring(stime.length - vlen);
                var duration = document.EDITAPPT.duration.value;

                if (isNaN(duration)) {
                    alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.msgFillTimeField"/>");
                    return false;
                }

                if (eval(duration) === 0) {
                    duration = 1;
                }
                if (eval(duration) < 0) {
                    duration = Math.abs(duration);
                }

                var lmin = eval(smin) + eval(duration) - 1;
                var lhour = parseInt(lmin / 60);

                if ((lmin) > 59) {
                    shour = eval(shour) + eval(lhour);
                    shour = shour < 10 ? ("0" + shour) : shour;
                    smin = lmin - 60 * lhour;
                } else {
                    smin = lmin;
                }
                smin = smin < 10 ? ("0" + smin) : smin;
                document.EDITAPPT.end_time.value = shour + ":" + smin;
                if (shour > 23) {
                    alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.msgCheckDuration"/>");
                    return false;
                }
                return true;
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
                        if (ch === ":") {
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
                    alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.msgFillTimeField"/>");
                } else {
                    colonIdx = obj.value.indexOf(':');
                    if (colonIdx === -1) {
                        if (obj.value.length < 3) alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.msgFillValidTimeField"/>");
                        obj.value = obj.value.substring(0, obj.value.length - 2) + ":" + obj.value.substring(obj.value.length - 2);
                    }
                }

                var hours = "";
                var minutes = "";

                colonIdx = obj.value.indexOf(':');
                if (colonIdx < 1)
                    hours = "00";
                else if (colonIdx === 1)
                    hours = "0" + obj.value.substring(0, 1);
                else
                    hours = obj.value.substring(0, 2);

                minutes = obj.value.substring(colonIdx + 1, colonIdx + 3);
                if (minutes.length === 0)
                    minutes = "00";
                else if (minutes.length === 1)
                    minutes = "0" + minutes;
                else if (minutes > 59)
                    minutes = "00";

                obj.value = hours + ":" + minutes;
            }

            <% if (apptObj!=null) { %>

            function pasteAppt(multipleSameDayGroupAppt) {

                var warnMsgId = document.getElementById("tooManySameDayGroupApptWarning");

                if (multipleSameDayGroupAppt) {
                    warnMsgId.style.display = "block";
                    if (document.EDITAPPT.updateButton) {
                        document.EDITAPPT.updateButton.style.display = "none";
                    }
                    if (document.EDITAPPT.groupButton) {
                        document.EDITAPPT.groupButton.style.display = "none";
                    }
                    if (document.EDITAPPT.deleteButton) {
                        document.EDITAPPT.deleteButton.style.display = "none";
                    }
                    if (document.EDITAPPT.cancelButton) {
                        document.EDITAPPT.cancelButton.style.display = "none";
                    }
                    if (document.EDITAPPT.noShowButton) {
                        document.EDITAPPT.noShowButton.style.display = "none";
                    }
                    if (document.EDITAPPT.labelButton) {
                        document.EDITAPPT.labelButton.style.display = "none";
                    }
                    if (document.EDITAPPT.repeatButton) {
                        document.EDITAPPT.repeatButton.style.display = "none";
                    }
                }
                //else {
                //   warnMsgId.style.display = "none";
                //}
                document.EDITAPPT.status.value = "<%=Encode.forJavaScriptBlock(apptObj.getStatus())%>";
                document.EDITAPPT.duration.value = "<%=Encode.forJavaScriptBlock(apptObj.getDuration())%>";
                document.EDITAPPT.chart_no.value = "<%=Encode.forJavaScriptBlock(apptObj.getChart_no())%>";
                document.EDITAPPT.keyword.value = "<%=Encode.forJavaScriptBlock(apptObj.getName())%>";
                document.EDITAPPT.demographic_no.value = "<%=Encode.forJavaScriptBlock(apptObj.getDemographic_no())%>";
                document.forms[0].reason.value = "<%= Encode.forJavaScriptBlock(apptObj.getReason()) %>";
                document.forms[0].notes.value = "<%= Encode.forJavaScriptBlock(apptObj.getNotes()) %>";
                document.EDITAPPT.location.value = "<%=Encode.forJavaScriptBlock(apptObj.getLocation())%>";
                document.EDITAPPT.resources.value = "<%=Encode.forJavaScriptBlock(apptObj.getResources())%>";
                document.EDITAPPT.type.value = "<%=Encode.forJavaScriptBlock(apptObj.getType())%>";
                if ('<%=apptObj.getUrgency()%>' === 'critical') {
                    document.EDITAPPT.urgency.checked = "checked";
                }
            }

            <% } %>

            function onCut() {
                document.EDITAPPT.submit();
            }


            function openTypePopup() {
                windowprops = "height=250,width=500,location=no,scrollbars=no,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=100,left=100";
                var popup = window.open("appointmentType.jsp?type=" + document.forms['EDITAPPT'].type.value, "Appointment Type", windowprops);
                if (popup != null) {
                    if (popup.opener === null) {
                        popup.opener = self;
                    }
                    popup.focus();
                }
            }

            function setType(typeSel, reasonSel, locSel, durSel, notesSel, resSel) {
                document.forms['EDITAPPT'].type.value = typeSel;
                document.forms['EDITAPPT'].reason.value = reasonSel;
                document.forms['EDITAPPT'].duration.value = durSel;
                document.forms['EDITAPPT'].notes.value = notesSel;
                document.forms['EDITAPPT'].duration.value = durSel;
                document.forms['EDITAPPT'].resources.value = resSel;
                var loc = document.forms['EDITAPPT'].location;
                if (loc.nodeName.toUpperCase() === 'SELECT') {
                    for (c = 0; c < loc.length; c++) {
                        if (loc.options[c].innerHTML === locSel) {
                            loc.selectedIndex = c;
                            loc.style.backgroundColor = loc.options[loc.selectedIndex].style.backgroundColor;
                            break;
                        }
                    }
                } else if (loc.nodeName.toUpperCase() === "INPUT") {
                    document.forms['EDITAPPT'].location.value = locSel;
                }
            }

            function parseSearch() {
                // sane defaults
                document.getElementById("search_mode").value = 'search_name';

                var keyObj = document.forms['EDITAPPT'].keyword;
                var keyVal = keyObj.value;
                console.log(keyVal);

                // start with the loosest pattern
                // address pattern 293 Meridian
                const reAddr = /^\d{1,9}[\s]\w*/;
                if (reAddr.exec(keyVal)) {
                    document.getElementById("search_mode").value = "search_address";
                }

                //Ontario hin 10 didgits  MSP 9 didgits Regie 4 alpha + 8 digits
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
                if (keyVal.indexOf('%b610054') === 0 && keyVal.length > 18) {
                    keyObj.value = keyVal.substring(8, 18);
                    document.getElementById("search_mode").value = "search_hin";
                }
            }

            jQuery(document).ready(function () {
                var belowTbl = jQuery("#belowTbl");
                if (belowTbl != null && belowTbl.length > 0 && belowTbl.find("tr").length === 2) {
                    jQuery(belowTbl.find("tr")[1]).remove();
                }
            });
            jQuery(document).ready(function () {

                var url = "<%= request.getContextPath() %>/demographic/SearchDemographic.do?jqueryJSON=true&activeOnly=true";

                jQuery("#keyword").autocomplete({
                    source: url,
                    minLength: 2,

                    focus: function (event, ui) {
                        jQuery("#keyword").val(ui.item.formattedName);
                        return false;
                    },
                    select: function (event, ui) {
                        jQuery("#demographic_no").val(ui.item.value);
                        jQuery("#mrp").val(ui.item.provider);
                        jQuery("#keyword").val(ui.item.formattedName);
                        return false;
                    }
                })
                    .autocomplete("instance")._renderItem = function (ul, item) {
                    return jQuery("<li>")
                        .append("<div><b>" + item.label + "</b>" + "<br>" + item.provider + "</div>")
                        .appendTo(ul);
                };


                jQuery.widget('custom.myselectmenu', jQuery.ui.selectmenu, {

                    /**
                     * @see {@link https://api.jqueryui.com/selectmenu/#method-_renderItem}
                     */
                    _renderItem: function (ul, item) {
                        var string = "<div><b>" + item.label + "</b> "
                        if (item.element.attr("data-dur") && item.element.attr("data-dur").length > 0) {
                            string = string + item.element.attr("data-dur") + "&nbsp;<fmt:setBundle basename='oscarResources'/><fmt:message key='provider.preference.min'/>";
                        }
                        if (item.element.attr("data-notes") && item.element.attr("data-notes").length > 0) {
                            string = string + "&nbsp;&nbsp;" + "<span style='color:gray'> <i class='icon-pencil' title='" + "<fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formNotes"/>:&nbsp;" +
                                item.element.attr("data-notes") + "'></i></span>";
                        }
                        string = string + "<br>";
                        if (item.element.attr("data-reason") && item.element.attr("data-reason").length > 0) {
                            string = string + "<span style='color:gray'><i class='icon-tags' title='" + "<fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formReason"/>" + "'></i></span>&nbsp;&nbsp;" +
                                item.element.attr("data-reason");
                        }
                        if (item.element.attr("data-resources") && item.element.attr("data-resources").length > 0) {
                            string = string + "<br>" + "<span style='color:gray'><i class='icon-cog' title='" + "<fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formResources"/>" + "'></i></span>&nbsp;&nbsp;" +
                                item.element.attr("data-resources");
                        }
                        if (item.element.attr("data-loc") && item.element.attr("data-loc").length > 1) {
                            string = string + "<br>" + "<span style='color:gray'><i class='icon-home' title='" + "<fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formLocation"/>" + "'></i></span>&nbsp;&nbsp;" +
                                item.element.attr("data-loc");
                        }
                        string = string + "</div>";
                        return jQuery("<li>")
                            .append(string)
                            .appendTo(ul);

                    }
                });

                // render custom selectmenu
                jQuery('#type').myselectmenu({
                    change: function (event, data) {
                        label = data.item.value;
                        origReason = jQuery("textarea[name='reason']").val();
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

            function locale() {
                // add style for multisites location
                var loc = document.forms['EDITAPPT'].location;
                if (loc.nodeName.toUpperCase() == 'SELECT') loc.style.backgroundColor = loc.options[loc.selectedIndex].style.backgroundColor;
            }
        </script>
    </head>
    <body onload="setfocus();updateTime();locale()">

<div id="editAppointment" >
    <div class="container" >
<form name="EDITAPPT" METHOD="post" class="form-inline" ACTION="appointmentcontrol.jsp" onSubmit="return(onSub())">
    <input type="hidden" name="displaymode" value="">
            <div class="header deep">
        <div class="time" id="header"><h4>
                    <!-- We display a shortened title for the mobile version -->
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.editappointment.msgMainLabel"/>

                    <%

                        if (bFirstDisp) {

                            if (appt != null && !StringUtils.isEmpty(appt.getProviderNo())) {
                                ProviderData prov = providerDao.find(appt.getProviderNo());
                                if (prov != null) {
                                    String providerName = prov.getLastName() + ", " + prov.getFirstName();
                    %>

                    <%=providerName == "" ? "" : "(" + providerName + ")"%>

                    <% }
                    }
                    %>
</h4>
                </div>

            </div>

                    <%
		if (appt == null) {
%>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.editappointment.msgNoSuchAppointment"/>
                    <%
			return;
		}
	} else {

%>
</h4>
    </div>

    </div>
    <%
        }
    %>

    <%

        if (props.getProperty("allowMultipleSameDayGroupAppt", "").equalsIgnoreCase("no")) {

            if (!bFirstDisp && !demono.equals("0") && !demono.equals("")) {
                String[] sqlParam = new String[3];
                sqlParam[0] = myGroupNo; //schedule group
                sqlParam[1] = demono;
                sqlParam[2] = strApptDate;

                List<Appointment> aa = appointmentDao.search_group_day_appt(myGroupNo, Integer.parseInt(demono), ConversionUtils.fromDateString(strApptDate));

                long numSameDayGroupAppts = aa.size() > 0 ? new Long(aa.size()) : 0;
                bMultipleSameDayGroupAppt = (numSameDayGroupAppts > 0);
            }

            if (bMultipleSameDayGroupAppt) {
                displayStyle = "display:block";
            }
    %>
    <div id="tooManySameDayGroupApptWarning" style="<%=displayStyle%>">
        <div class="alert alert-error">
            <h4><fmt:setBundle basename='oscarResources'/><fmt:message key='appointment.addappointment.titleMultipleGroupDayBooking'/></h4>
            <fmt:setBundle basename='oscarResources'/><fmt:message key='appointment.addappointment.MultipleGroupDayBooking'/>
        </div>
    </div>
    <%
        }

        //RJ 07/12/2006
        //If page is loaded first time hit db for patient's family doctor
        //Else if we are coming back from search this has been done for us
        //Else how did we get here?
        if (bFirstDisp) {
            DemographicData dd = new DemographicData();
            Demographic demo = dd.getDemographic(loggedInInfo, String.valueOf(appt.getDemographicNo()));
            doctorNo = demo != null ? (demo.getProviderNo()) : "";
        } else if (!request.getParameter("doctor_no").equals("")) {
            doctorNo = request.getParameter("doctor_no");
        }

	/* null check because demo.getProvider and/or request.getParameter("doctor_no") can
     * BOTH return a null value that will cause the entire page to crash
     */
	if (doctorNo == null) {
		doctorNo = "";
    }
    %>




    <div class="well">
        <div class="form-wrapper">
    <table class="table table-condensed table-responsive">
                <tr>
                    <td>
                        <label for="date"><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formDate"/>:</label>
                    </td>
                    <td>
                <input type="date" class="form-control" name="appointment_date" id="date"
                               value="<%=bFirstDisp?ConversionUtils.toDateString(appt.getAppointmentDate()):strApptDate%>"
                        >
                    </td>
                </tr>
                <tr>
                    <td>
                        <label><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formStartTime"/>:</label>
                    </td>
                    <td>
                <input type="time" name="start_time" class="form-control"
                               value="<%=bFirstDisp?ConversionUtils.toTimeStringNoSeconds(appt.getStartTime()):request.getParameter("start_time")%>"
                               onChange="checkTimeTypeIn(this);updateTime();">
                    </td>
                </tr>
                <tr>
                    <td>
                        <label><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formDuration"/>:</label>
                    </td>
                    <td>
                        <%
                            int everyMin = 1;
                            StringBuilder nameSb = new StringBuilder();
                            if (bFirstDisp) {
                                Calendar startCal = Calendar.getInstance();
                                startCal.setTime(appt.getStartTime());
                                Calendar endCal = Calendar.getInstance();
                                endCal.setTime(appt.getEndTime());

                                int endtime = (endCal.get(Calendar.HOUR_OF_DAY)) * 60 + (endCal.get(Calendar.MINUTE));
                                int starttime = (startCal.get(Calendar.HOUR_OF_DAY)) * 60 + (startCal.get(Calendar.MINUTE));
                                everyMin = endtime - starttime + 1;

                                if (!demono.equals("0") && !demono.equals("") && (demographicManager != null)) {
                                    Demographic demo = demographicManager.getDemographic(loggedInInfo, demono);
                                    nameSb.append(demo.getLastName())
                                            .append(",")
                                            .append(demo.getFirstName());
                                } else {
                                    nameSb.append(appt.getName());
                                }
                            }
                        %> <input type="hidden" name="end_time"
                                  value="<%=bFirstDisp?ConversionUtils.toTimeStringNoSeconds(appt.getEndTime()):request.getParameter("end_time")%>"
                    >

				<input type="number" name="duration" id="duration" class="form-control"
                               value="<%=request.getParameter("duration")!=null?(request.getParameter("duration").equals(" ")||request.getParameter("duration").equals("")||request.getParameter("duration").equals("null")?(""+everyMin) :request.getParameter("duration")):(""+everyMin)%>"
                               onblur="calculateEndTime();">
                    </td>
                </tr>
                <tr>
                    <td>
                        <%
                            String searchMode = request.getParameter("search_mode");
                            if (searchMode == null || searchMode.isEmpty()) {
                                searchMode = OscarProperties.getInstance().getProperty("default_search_mode", "search_name");
                            }
                        %>
                        <input type="hidden" name="orderby" value="last_name, first_name">
                        <input type="hidden" name="search_mode" id="search_mode" value="<%=searchMode%>">
                        <input type="hidden" name="originalpage"
                               value="<%=request.getContextPath() %>/appointment/editappointment.jsp">
                        <input type="hidden" name="limit1" value="0">
                        <input type="hidden" name="limit2" value="5">
                        <input type="hidden" name="ptstatus" value="active">
                        <input type="submit" name="searchBtn" id="searchBtn" class="btn" style="margin-bottom:10px;"
                               onclick="parseSearch();document.forms['EDITAPPT'].displaymode.value='Search '"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.editappointment.btnSearch"/>">
                    </td>
                    <td>
            	<input type="text" name="keyword" id="keyword" class="form-control"
                               value="<%=Encode.forHtmlAttribute(bFirstDisp?nameSb.toString():request.getParameter("name"))%>"
                               placeholder="<fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formName"/>">
                    </td>
                </tr>
                <tr>
                    <td>
                        <label><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formReason"/>:</label>
                    </td>
                    <td>
				<select name="reasonCode" class="form-control">
                            <%
                                String rCode = bFirstDisp && appt.getReasonCode() != null ? appt.getReasonCode().toString() : request.getParameter("reasonCode");
                                pageContext.setAttribute("rCode", rCode);

                            %>
                            <c:choose>
                                <c:when test="${ not empty reasonCodes  }">
                                    <c:forEach items="${ reasonCodes.items }" var="reason">
                                        <c:if test="${ reason.active }">
                                            <option value="${ reason.id }"
                                                    id="${ reason.value }" ${ rCode eq reason.id ? 'selected="selected"' : '' } >
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
                    </td>
                </tr>
                <tr>
            <td></td><td>
				<textarea id="reason" class="form-control" name="reason" maxlength="80" rows="8" oninput='this.style.height = "";this.style.height = this.scrollHeight + "px"' onfocus='this.style.height = "";this.style.height = this.scrollHeight + "px"'><%=Encode.forHtmlContent(bFirstDisp?appt.getReason():request.getParameter("reason"))%></textarea>

                    </td>
                </tr>
                <tr>
                    <td>
                        <label><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formLocation"/>:</label>
                    </td>
                    <td>
                        <%


                            boolean bMoreAddr = bMultisites ? true : props.getProperty("scheduleSiteID", "").equals("") ? false : true;

                            String loc = bFirstDisp ? (appt.getLocation()) : request.getParameter("location");
                            String colo = bMultisites
                                    ? ApptUtil.getColorFromLocation(sites, loc)
                                    : bMoreAddr ? ApptUtil.getColorFromLocation(props.getProperty("scheduleSiteID", ""), props.getProperty("scheduleSiteColor", ""), loc) : "white";

                            if (bMultisites) { %>
				        <select tabindex="4" name="location" class="form-control" style="background-color: <%=colo%>" onchange='this.style.backgroundColor=this.options[this.selectedIndex].style.backgroundColor'>
                            <%
                                StringBuilder sb = new StringBuilder();
                                for (Site s : sites) {
                                    if (s.getName().equals(loc)) isSiteSelected = true;
                                    sb.append("<option value=\"").append(s.getName()).append("\" class=\"").append(s.getShortName()).append("\" style=\"background-color: ").append(s.getBgColor()).append("\" ").append(s.getName().equals(loc) ? "selected" : "").append(">").append(s.getName()).append("</option>");
                                }
                                if (isSiteSelected) {
                                    out.println(sb.toString());
                                } else {
                                    out.println("<option value='" + loc + "'>" + loc + "</option>");
                                }
                            %>

                        </select>
                        <% } else {
                            isSiteSelected = true;
                            if (locationEnabled) {
                        %>
		<select name="location" class="form-control">
                            <%
                                String location = Encode.forJava(bFirstDisp ? (appt.getLocation()) : request.getParameter("location"));
                                if (programs != null && !programs.isEmpty()) {
                                    for (Program program : programs) {
                                        String description = StringUtils.isBlank(program.getLocation()) ? program.getName() : program.getLocation();
                            %>
                            <option value="<%=program.getId()%>" <%=(program.getId().toString().equals(location) ? "selected='selected'" : "") %>><%=Encode.forHtmlContent(description)%>
                            </option>

                            <% }
                            }
                            %>
                        </select>
                        <% } else { %>
		        <input type="text" class="form-control" name="location" tabindex="4"
                       value="<%=Encode.forHtmlAttribute(bFirstDisp?appt.getLocation():request.getParameter("location"))%>" >
                        <% } %>
                        <% } %>
                    </td>
                </tr>
                <tr>
                    <td>
                        <label><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formCreator"/>:</label>
                    </td>
                    <td>
                        <% String lastCreatorNo = bFirstDisp ? (appt.getCreator()) : request.getParameter("user_id"); %>
                <input type="text" class="form-control" name="user_id" value="<%=Encode.forHtmlAttribute(lastCreatorNo)%>" readonly >
                    </td>
                </tr>
                <%
                    origDate = bFirstDisp ? ConversionUtils.toTimestampString(appt.getCreateDateTime()) : request.getParameter("createDate");
                    String lastDateTime = bFirstDisp ? ConversionUtils.toTimestampString(appt.getUpdateDateTime()) : request.getParameter("updatedatetime");
                    if (lastDateTime == null) {
                        lastDateTime = bFirstDisp ? ConversionUtils.toTimestampString(appt.getCreateDateTime()) : request.getParameter("createdatetime");
                    }

                    GregorianCalendar now = new GregorianCalendar();
                    String strDateTime = now.get(Calendar.YEAR) + "-" + (now.get(Calendar.MONTH) + 1) + "-" + now.get(Calendar.DAY_OF_MONTH) + " "
                            + now.get(Calendar.HOUR_OF_DAY) + ":" + now.get(Calendar.MINUTE) + ":" + now.get(Calendar.SECOND);

                    String remarks = "";
                    if (bFirstDisp && appt.getRemarks() != null) {
                        remarks = appt.getRemarks();
                    }
                    // Convert String to Java LocalDateTime
                    DateTimeFormatter pattern1 = DateTimeFormatter.ofPattern("yyyy-M-d H:m:s");
                    LocalDateTime origDT = LocalDateTime.parse(origDate, pattern1);
                    LocalDateTime lastDT = LocalDateTime.parse(lastDateTime, pattern1);
                    //LocalDateTime strDT = LocalDateTime.parse(strDateTime,pattern1);
                    // Get localized pattern for UI
                    DateTimeFormatter pattern2 = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(request.getLocale()).withZone(ZoneId.systemDefault());
                    String dateString1 = pattern2.format(origDT);
                    String dateString2 = pattern2.format(lastDT);
                    //String dateString3 = pattern2.format(strDT); watch for non padded seconds etc

                %>
                <tr>
                    <td>
                        <label><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.CreateDate"/>:</label>
                    </td>
                    <td>
                <div class="panel panel-default">
                    <div class="panel-body">
                        <input type="hidden" class="form-control" name="createDate" value="<%=origDate%>">
                        <%=dateString1%>
                    </div>
                </div>
                    </td>
                </tr>
                <% if (pros.isPropertyActive("mc_number")) {
                    String mcNumber = OtherIdManager.getApptOtherId(appointment_no, "appt_mc_number"); %>
                <tr>
                    <td>
                        <label for="appt_mc_number"><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formMC"/>:</label>
                    </td>
                    <td>
                <input type="text" class="form-control" name="appt_mc_number" id="appt_mc_number" value="<%=bFirstDisp?mcNumber:Encode.forHtmlAttribute(request.getParameter("appt_mc_number"))%>" />
                    </td>
                </tr>
                <% } %>

            </table>

    <table class="table table-condensed table-responsive">
                <tr>
                    <td>
                        <label><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formStatus"/>:</label>
                    </td>
                    <td>
                        <%

                            if (strEditable != null && strEditable.equalsIgnoreCase("yes")) { %>

                <select name="status" class="form-control" style="background-color:<%=((AppointmentStatus)allStatus.get(curSelect)).getColor()%>" onchange='this.style.backgroundColor=this.options[this.selectedIndex].style.backgroundColor' >
                            <% for (int i = 0; i < allStatus.size(); i++) { %>
                            <option class="<%=((AppointmentStatus)allStatus.get(i)).getStatus()%>"
                                    style="background-color:<%=((AppointmentStatus)allStatus.get(i)).getColor()%>"
                                    value="<%=((AppointmentStatus)allStatus.get(i)).getStatus()+signOrVerify%>"
                                    <%=((AppointmentStatus) allStatus.get(i)).getStatus().equals(statusCode) ? "SELECTED" : ""%>><%=((AppointmentStatus) allStatus.get(i)).getDescription()%>
                            </option>
                            <% } %>
                        </select> <%
                    } else {
                        if (importedStatus == null || importedStatus.trim().equals("")) { %>
              	<input type="text" class="form-control" name="status" value="<%=statusCode%>" > <%
                    } else { %>
                <input type="text" class="form-control" name="status" value="<%=statusCode%>" >
                <input type="text"  class="form-control" TITLE="Imported Status" value="<%=importedStatus%>" readonly> <%
                            }
                        }
                    %>
                    </td>
                </tr>
                <tr>
                    <td>
                        <label><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formType"/>:</label>
                    </td>
                    <td>
                        <select name="type" class="form-control" id="type"
                                title="<fmt:setBundle basename="oscarResources"/><fmt:message key="billing.billingCorrection.msgSelectVisitType"/>">
                        <option data-dur="" data-reason=""></option><!-- important leave a blank top entry  -->

        <% AppointmentTypeDao appDao = SpringUtils.getBean(AppointmentTypeDao.class);
                                List<AppointmentType> types = appDao.listAll();
                for(AppointmentType type : types) {
                            %>
                    <option data-dur="<%= type.getDuration() %>"
                            data-reason="<%= Encode.forHtmlAttribute(type.getReason()) %>"
                            data-loc="<%= Encode.forHtmlAttribute(type.getLocation()) %>"
                            data-notes="<%= Encode.forHtmlAttribute(type.getNotes()) %>"
                            data-resources="<%= Encode.forHtmlAttribute(type.getResources()) %>"
                            <%= type.getName().equalsIgnoreCase(appt.getType()) ? "selected" : ""%>>
                        <%=Encode.forHtml(type.getName())%>
                            </option>
                            <% } %>
                        </select>
                    </td>
                </tr>
                <tr>
                    <td>
                        <label><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formDoctor"/>:</label>
                    </td>
                    <td>
                <input type="text" readonly name="doctorNo" id="mrp" class="form-control"
                               value="<%=StringEscapeUtils.escapeHtml4(providerBean.getProperty(doctorNo,""))%>">
                    </td>
                </tr>
                <tr>
                    <td>
                        <label><a href="#" onclick="demographicdetail(550,700)" class="btn btn-link"
                                  style="padding-left:0px;">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="global.master"/></a></label>
                    </td>
                    <td>
                <input type="text" name="demographic_no" id="demographic_no" class="form-control"
                               ONFOCUS="onBlockFieldFocus(this)" readonly
                               value="<%=bFirstDisp?( (appt.getDemographicNo())==0?"":(""+appt.getDemographicNo()) ):request.getParameter("demographic_no")%>">
                    </td>
                </tr>
                <tr>
                    <td>
                        <label><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formChartNo"/>:</label>
                    </td>
                    <td>
                <input type="text" name="chart_no" class="form-control"
                    readonly value="<%=bFirstDisp?StringUtils.trimToEmpty(chartno):request.getParameter("chart_no")%>"
                        >
                    </td>
                </tr>
                <tr>
                    <td>
                        <label><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formNotes"/>:</label>
                    </td>
                    <td>
				<textarea name="notes" class="form-control" maxlength="255" rows="9" oninput='this.style.height = "";this.style.height = this.scrollHeight + "px"' onfocus='this.style.height = "";this.style.height = this.scrollHeight + "px"'><%=Encode.forHtmlContent(bFirstDisp?appt.getNotes():request.getParameter("notes"))%></textarea>
                    </td>
                </tr>
                <tr>
                    <td>
                        <label><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formResources"/>:</label>
                    </td>
                    <td>
                <input type="text" name="resources" tabindex="5" class="form-control"
                               value="<%=Encode.forHtmlAttribute(bFirstDisp?appt.getResources():request.getParameter("resources"))%>">
                    </td>
                </tr>
                <tr>
                    <td>
                        <label><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formLastCreator"/>:</label>
                    </td>
                    <td>
                        <% lastCreatorNo = request.getParameter("user_id");
                            if (bFirstDisp) {
                                if (appt.getLastUpdateUser() != null) {
                                    ProviderData provider = providerDao.findByProviderNo(appt.getLastUpdateUser());
                                    if (provider != null) {
                                        lastCreatorNo = provider.getLastName() + ", " + provider.getFirstName();
                                    }
                                } else {
                                    lastCreatorNo = appt.getCreator();
                                }
                            }
                        %>
                <input type="text" readonly class="form-control" value="<%=lastCreatorNo%>" >
                    </td>
                </tr>
                <tr>
                    <td>
                        <label><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formLastTime"/>:</label>
                    </td>
                    <td>
                <div class="panel panel-default">
                    <div class="panel-body">
                        <input type="hidden" name="lastcreatedatetime"
                               value="<%=Encode.forHtmlContent(bFirstDisp?lastDateTime:request.getParameter("lastcreatedatetime"))%>"
                        > <%=Encode.forHtmlContent(dateString2)%>
                        <input type="hidden" name="createdatetime" value="<%=strDateTime%>">
                        <input type="hidden" name="provider_no" value="<%=curProvider_no%>">
                        <input type="hidden" name="dboperation" value="">
                        <input type="hidden" name="creator"
                               value="<%=Encode.forHtmlAttribute(userlastname+", "+userfirstname)%>">
                        <input type="hidden" name="remarks" value="<%=Encode.forHtmlAttribute(remarks)%>">
                        <input type="hidden" name="appointment_no" value="<%=appointment_no%>">
                    </div>
                </div>
                    </td>
                </tr>
                <tr>
                    <td>
                        <label for="urgency"><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formCritical"/> <i
                                class="icon-warning-sign"></i>:</label>
                    </td>
                    <td>
                        <%
                            String urgencyChecked = new String();
                            if (bFirstDisp) {
                                if (appt.getUrgency() != null && appt.getUrgency().equals("critical")) {
                                    urgencyChecked = " checked=\"checked\" ";
                                }
                            } else {
                                if (request.getParameter("urgency") != null) {
                                    if (request.getParameter("urgency").equals("critical")) {
                                        urgencyChecked = " checked=\"checked\" ";
                                    }
                                }
                            }
                        %>
            	<input type="checkbox" class="checkbox-inline" name="urgency" id="urgency" value="critical" <%=urgencyChecked%> >
                    </td>
                </tr>
                <% String emailReminder = pros.getProperty("emailApptReminder");
                    if ((emailReminder != null) && emailReminder.equalsIgnoreCase("yes")) { %>
                <tr>
                    <td>
                        <label><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formEmailReminder"/>:</label>
                    </td>
                    <td>
                <input type="checkbox" class="checkbox-inline" name="emailPt" value="email reminder">
                    </td>
                </tr>
                <% } else { %>
                <tr>
                    <td></td>
                    <td></td>
                </tr>
                <% }%>
            </table>
        </div>
        <% if (isSiteSelected) { %>
        <table class="buttonBar deep">
            <tr>
                <% if (!bMultipleSameDayGroupAppt) { %>
                <td style="text-align: left;"><input type="submit" class="btn btn-primary" id="updateButton"
                                                     onclick="document.forms['EDITAPPT'].displaymode.value='Update Appt'; onButUpdate();"
                                                     value="<fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.editappointment.btnUpdateAppointment"/>">
                    <% if (!props.getProperty("allowMultipleSameDayGroupAppt", "").equalsIgnoreCase("no")) {%>
                    <input type="submit" id="groupButton" class="btn"
                           onclick="document.forms['EDITAPPT'].displaymode.value='Group Action'; onButUpdate();"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.editappointment.btnGroupAction"/>">
                    <% }%>
                    <input type="submit" id="printReceiptButton" class="btn"
                           onclick="document.forms['EDITAPPT'].displaymode.value='Update Appt';document.forms['EDITAPPT'].printReceipt.value='1';"
                           value="<fmt:setBundle basename='oscarResources'/><fmt:message key='appointment.editappointment.btnPrintReceipt'/>">
                    <input type="hidden" name="printReceipt" value="">
                    <input type="submit" class="btn btn-danger" id="deleteButton"
                           onclick="document.forms['EDITAPPT'].displaymode.value='Delete Appt'; onButDelete();"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.editappointment.btnDeleteAppointment"/>">
                    <input type="button" name="buttoncancel" id="cancelButton" class="btn btn-inverse"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.editappointment.btnCancelAppointment"/>"
                           onClick="onButCancel();">
                    <input type="button"
                           name="buttoncancel" id="noShowButton" class="btn"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.editappointment.btnNoShow"/>"
                           onClick="window.location='appointmentcontrol.jsp?buttoncancel=No Show&displaymode=Update Appt&appointment_no=<%=appointment_no%>'">
                    <br>
                    <a href="javascript:void(0);" title="Annotation"
                       onclick="window.open('<%=request.getContextPath()%>/annotation/annotation.jsp?display=<%=annotation_display%>&amp;table_id=<%=appointment_no%>&amp;demo='+document.EDITAPPT.demographic_no.value,'anwin','width=400,height=500');">
                        <img src="<%=request.getContextPath() %>/images/notes.gif" alt="Annotation" height="16"
                             width="13">
                    </a>
                    <a class="btn"
                       onClick="window.location='appointmentcontrol.jsp?displaymode=PrintCard&appointment_no=' + encodeURIComponent('<%=appointment_no%>')">
                        <i class="icon-print"></i>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.editappointment.btnPrintCard"/></a>
                    <a class="btn"
                       onClick="window.open('<%=request.getContextPath() %>/demographic/demographiclabelprintsetting.jsp?demographic_no=' + encodeURIComponent(document.EDITAPPT.demographic_no.value), 'labelprint','height=550,width=700,location=no,scrollbars=yes,menubars=no,toolbars=no')">
                        <i class="icon-print"></i>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.editappointment.btnLabelPrint"/></a>
                    <a class="btn"
                       onclick="document.forms['EDITAPPT'].displaymode.value='Cut';localStorage.setItem('copyPaste','1');document.forms['EDITAPPT'].submit();">
                        <i class="icon-cut"></i>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.appointmentedit.cut"/></a>
                    <a class="btn"
                       onclick="document.forms['EDITAPPT'].displaymode.value='Copy';localStorage.setItem('copyPaste','1');document.forms['EDITAPPT'].submit();">
                        <i class="icon-copy"></i>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.appointmentedit.copy"/> </a>
                    <% if (!props.getProperty("allowMultipleSameDayGroupAppt", "").equalsIgnoreCase("no")) {%>
                    <input type="button" id="repeatButton" class="btn"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.btnRepeat"/>"
                           onclick="onButRepeat()">
                    <% }%>
                    <input type="button" name="Button" class="btn btn-link" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnExit"/>"
                           onClick="self.close()"></td>
                <% }%>

            </tr>
        </table>
        <% } %>


    </div>

    <div id="bottomInfo">
        <table style="width:95%;">
            <tr>
                <td style="padding-left:10px;"><label><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.msgTelephone"/>:</label> <%= Encode.forHtmlContent(StringUtils.trimToEmpty(phone))%>
                    <br>
                    <label><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.msgRosterStatus"/>:</label> <%=Encode.forHtmlContent(StringUtils.trimToEmpty(rosterstatus))%>
                </td>
                <% if (alert != null && !alert.equals("")) { %>
                <td class="alert alert-error"><%=Encode.forHtmlContent(alert)%>
                </td>
                <% } %>
            </tr>
        </table>

        <% if (isSiteSelected) { %>
        <table style="width:95%; padding:3px;" id="belowTbl">
            <tr>
                <td colspan="4">
                    <%
                        if (bFirstDisp && apptObj != null) {

                            long numSameDayGroupApptsPaste = 0;

                            if (props.getProperty("allowMultipleSameDayGroupAppt", "").equalsIgnoreCase("no")) {

                                List<Appointment> aa = appointmentDao.search_group_day_appt(myGroupNo, Integer.parseInt(demono), appt.getAppointmentDate());

                                numSameDayGroupApptsPaste = aa.size() > 0 ? new Long(aa.size()) : 0;
                            }
                    %><a href=#
                         onclick="pasteAppt(<%=(numSameDayGroupApptsPaste > 0)%>);">Paste</a>
                    <% } %>
                </td>
            </tr>
            <%if (cheader1s != null && cheader1s.size() > 0) {%>
            <tr>
                <th style="color:red;">Outstanding 3rd Invoices</th>
                <th style="color:red;">Invoice Date</th>
                <th style="color:red;">Amount</th>
                <th style="color:red;">Balance</th>
            </tr>
            <%
                java.text.SimpleDateFormat fm = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                for (int i = 0; i < cheader1s.size(); i++) {
                    if (cheader1s.get(i).getPayProgram().matches(BillingDataHlp.BILLINGMATCHSTRING_3RDPARTY)) {
                        BigDecimal payment = billingOnExtDao.getAccountVal(cheader1s.get(i).getId(), billingOnExtDao.KEY_PAYMENT);
                        BigDecimal discount = billingOnExtDao.getAccountVal(cheader1s.get(i).getId(), billingOnExtDao.KEY_DISCOUNT);
                        BigDecimal credit = billingOnExtDao.getAccountVal(cheader1s.get(i).getId(), billingOnExtDao.KEY_CREDIT);
                        BigDecimal total = cheader1s.get(i).getTotal();
                        BigDecimal balance = total.subtract(payment).subtract(discount).add(credit);

                        if (balance.compareTo(BigDecimal.ZERO) != 0) { %>
            <tr>
                <td style="text-align: center; color:red;"><a href="javascript:void(0)"
                                                              onclick="popupPage(600,800, '<%=request.getContextPath() %>/billing/CA/ON/billingONCorrection.jsp?billing_no=<%=cheader1s.get(i).getId()%>')">Inv
                    #<%=cheader1s.get(i).getId() %>
                </a></td>
                <td style="text-align: center; color:red;"><%=fm.format(cheader1s.get(i).getTimestamp()) %>
                </td>
                <td style="text-align: center; color:red;">$<%=cheader1s.get(i).getTotal() %>
                </td>
                <td style="text-align: center; color:red;">$<%=balance %>
                </td>
            </tr>
            <%
                            }
                        }
                    }
                }
            %>
        </table>
        <% } %>


    </div>


    <%
        String formTblProp = props.getProperty("appt_formTbl");
        String[] formTblNames = formTblProp.split(";");

        int numForms = 0;
        for (String formTblName : formTblNames) {
            if ((formTblName != null) && !formTblName.equals("")) {
                //form table name defined
                for (EncounterForm ef : encounterFormDao.findByFormTable(formTblName)) {
                    String formName = ef.getFormName();
                    pageContext.setAttribute("formName", formName);
                    boolean formComplete = false;
                    EctFormData.PatientForm[] ptForms = EctFormData.getPatientFormsFromLocalAndRemote(loggedInInfo, demono, formTblName);

                    if (ptForms.length > 0) {
                        formComplete = true;
                    }
                    numForms++;
                    if (numForms == 1) {

    %>
    <table style="background-color: #e8e8e8; margin-left:auto; vertical-align: top; padding:3px">
        <tr style="background-color:#f3f6f9">
            <th colspan="2">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.msgFormsSaved"/>
            </th>
        </tr>
        <% } %>

        <tr style="background-color:#c0c0c0; text-align:left">
            <th style="padding-right: 20px"><c:out value="${formName}:"/></th>
            <% if (formComplete) { %>
            <td><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.msgFormCompleted"/></td>
            <% } else { %>
            <td><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.msgFormNotCompleted"/></td>
            <% } %>
        </tr>
        <%
                    }
                }
            }

            if (numForms > 0) { %>
    </table>
    <% } %>


        <%--<div id="viewAppointment" style="display:<%=(bFirstDisp && false) ? "block":"none"%>;">--%>
        <%--    <%--%>
        <%--        // Format date to be more readable--%>
        <%--        java.text.SimpleDateFormat inform = new java.text.SimpleDateFormat ("yyyy-MM-dd");--%>
        <%--        String strDate = bFirstDisp ? ConversionUtils.toDateString(appt.getAppointmentDate()) : request.getParameter("appointment_date");--%>
        <%--        java.util.Date d = inform.parse(strDate);--%>
        <%--        String formatDate = "";--%>
        <%--        try { // attempt to change string format--%>
        <%--        java.util.ResourceBundle prop = ResourceBundle.getBundle("oscarResources", request.getLocale());--%>
        <%--        formatDate = ca.openosp.openo.util.UtilDateUtilities.DateToString(d, prop.getString("date.EEEyyyyMMdd"));--%>
        <%--        } catch (Exception e) {--%>
        <%--            ca.openosp.openo.utility.MiscUtils.getLogger().error("Error", e);--%>
        <%--            formatDate = ca.openosp.openo.util.UtilDateUtilities.DateToString(inform.parse(strDate), "EEE, yyyy-MM-dd");--%>
        <%--        }--%>
        <%--    %>--%>
        <%--    <div class="header">--%>
        <%--        <div class="title" id="appointmentTitle">--%>
        <%--            <fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.editappointment.btnView"/>--%>
        <%--        </div>--%>
        <%--        <a href=# onclick="window.close();" id="backButton" class="leftButton top"><%= strDate%></a>--%>
        <%--        <a href="javascript:toggleView();" id="editButton" class="rightButton top">Edit</a>--%>
        <%--    </div>--%>
        <%--    <div id="info" class="panel">--%>
        <%--        <ul>--%>
        <%--            <li class="mainInfo"><a href="#" onclick="demographicdetail(550,700)">--%>
        <%--                <%--%>
        <%--                    String apptName = (bFirstDisp ? appt.getName() : request.getParameter("name")).toString();--%>
        <%--                    //If a comma exists, need to split name into first and last to prevent overflow--%>
        <%--                    int comma = apptName.indexOf(",");--%>
        <%--                    if (comma != -1)--%>
        <%--                        apptName = apptName.substring(0, comma) + ", " + apptName.substring(comma+1);--%>
        <%--                %>--%>
        <%--                <%=Encode.forHtmlContent(apptName)%>--%>
        <%--            </a></li>--%>
        <%--            <li><div class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formDate"/>: </div>--%>
        <%--                <div class="info"><%=formatDate%></div>--%>
        <%--            </li>--%>
        <%--            <% // Determine appointment status from code so we can access--%>
        <%--   // the description, colour, image, etc.--%>
        <%--      AppointmentStatus apptStatus = (AppointmentStatus)allStatus.get(0);--%>
        <%--      for (int i = 0; i < allStatus.size(); i++) {--%>
        <%--            AppointmentStatus st = (AppointmentStatus) allStatus.get(i);--%>
        <%--            if (st.getStatus().equals(statusCode)) {--%>
        <%--                apptStatus = st;--%>
        <%--                break;--%>
        <%--            }--%>
        <%--      }--%>
        <%--%>--%>
        <%--            <li><div class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formStatus"/>: </div>--%>
        <%--                <div class="info" style="background-color:<%=apptStatus.getColor()%>; font-weight:bold;">--%>
        <%--                    <img src="<%=request.getContextPath() %>/images/<%=apptStatus.getIcon()%>" alt="image">--%>
        <%--                    <%=Encode.forHtmlContent(apptStatus.getDescription())%>--%>
        <%--                </div>--%>
        <%--            </li>--%>
        <%--            <li><div class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.editappointment.msgTime"/>: </div>--%>
        <%--                <div class="info">From <%=bFirstDisp ? ConversionUtils.toTimeStringNoSeconds(appt.getStartTime()) : request.getParameter("start_time")%>--%>
        <%--                to <%=bFirstDisp ? ConversionUtils.toTimeStringNoSeconds(appt.getEndTime()) : request.getParameter("end_time")%></div>--%>
        <%--            </li>--%>
        <%--            <li><div class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formType"/>: </div>--%>
        <%--                <div class="info"><%=Encode.forHtmlContent(bFirstDisp ? appt.getType() : request.getParameter("type"))%></div>--%>
        <%--            </li>--%>
        <%--            <li><div class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formReason"/>: </div>--%>
        <%--                <div class="info"><%=Encode.forHtmlContent(bFirstDisp ? appt.getReason() : request.getParameter("reason"))%></div>--%>
        <%--            </li>--%>
        <%--            <li><div class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formLocation"/>: </div>--%>
        <%--                <div class="info"><%=Encode.forHtmlContent(bFirstDisp ? appt.getLocation() : request.getParameter("location"))%></div>--%>
        <%--            </li>--%>
        <%--            <li><div class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formResources"/>: </div>--%>
        <%--                <div class="info"><%=Encode.forHtmlContent(bFirstDisp ? appt.getResources() : request.getParameter("resources"))%></div>--%>
        <%--            </li>--%>
        <%--            <li>&nbsp;</li>--%>
        <%--            <li class="notes">--%>
        <%--                <div class="info"><%=Encode.forHtmlContent(bFirstDisp ? appt.getNotes() : request.getParameter("notes"))%></div>--%>
        <%--                <div class="info"><%=Encode.forHtml(bFirstDisp ? appt.getNotes() : request.getParameter("notes"))%></div>--%>
        <%--            </li>--%>
        <%--        </ul>--%>
        <%--    </div>--%>
        <%--</div>--%>

</div> <!-- end of edit appointment screen -->
</form>
    </body>

</html>
