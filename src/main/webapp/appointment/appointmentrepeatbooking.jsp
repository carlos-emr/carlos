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

<%
    if (session.getAttribute("user") == null) {
        response.sendRedirect(request.getContextPath() + "/logout.jsp");
        return;
    }
    boolean bEdit = request.getParameter("appointment_no") != null ? true : false;
%>
<%@ page import="java.util.*, io.github.carlos_emr.*, io.github.carlos_emr.carlos.util.*"
         errorPage="/errorpage.jsp" %>
<%@ page import="org.owasp.csrfguard.CsrfGuard" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="https://www.owasp.org/index.php/OWASP_Java_Encoder_Project" prefix="e" %>

<%@page import="io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Appointment" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.util.ConversionUtils" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilMisc" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%
    AppointmentArchiveDao appointmentArchiveDao = (AppointmentArchiveDao) SpringUtils.getBean(AppointmentArchiveDao.class);
    OscarAppointmentDao appointmentDao = (OscarAppointmentDao) SpringUtils.getBean(OscarAppointmentDao.class);
%>
<%
    if (request.getParameter("groupappt") != null) {
        boolean bSucc = false;
        if (request.getParameter("groupappt").equals("Add Group Appointment")) {
            int rowsAffected = 0, datano = 0;
            String createdDateTime = UtilDateUtilities.DateToString(new Date(), "yyyy-MM-dd HH:mm:ss");
            String userName = (String) session.getAttribute("userlastname") + ", " + (String) session.getAttribute("userfirstname");
            String everyNum = request.getParameter("everyNum") != null ? request.getParameter("everyNum") : "0";
            String everyUnit = request.getParameter("everyUnit") != null ? request.getParameter("everyUnit") : "day";
            String endDate = request.getParameter("endDate") != null ? request.getParameter("endDate") : (request.getParameter("appointment_date") != null ? request.getParameter("appointment_date") : UtilDateUtilities.DateToString(new Date(), "yyyy-MM-dd"));
            int delta;
            try {
                delta = Integer.parseInt(everyNum);
            } catch (NumberFormatException nfe) {
                delta = 0;
            }
            if (everyUnit.equals("week")) {
                delta = delta * 7;
                everyUnit = "day";
            }
            GregorianCalendar gCalDate = new GregorianCalendar();
            GregorianCalendar gEndDate = (GregorianCalendar) gCalDate.clone();
            java.util.Date parsedEndDate = UtilDateUtilities.StringToDate(endDate, "yyyy-MM-dd");
            if (parsedEndDate == null) {
                parsedEndDate = new java.util.Date();
            }
            gEndDate.setTime(parsedEndDate);

            Date iDate = ConversionUtils.fromDateString(request.getParameter("appointment_date"));
            // repeat adding
            while (true) {
                Appointment a = new Appointment();
                a.setProviderNo(request.getParameter("provider_no"));
                a.setAppointmentDate(iDate);
                a.setStartTime(ConversionUtils.fromTimeStringNoSeconds(request.getParameter("start_time")));
                a.setEndTime(ConversionUtils.fromTimeStringNoSeconds(request.getParameter("end_time")));
                a.setName(request.getParameter("keyword"));
                a.setNotes(request.getParameter("notes"));
                a.setReason(request.getParameter("reason"));
                a.setLocation(request.getParameter("location"));
                a.setResources(request.getParameter("resources"));
                a.setType(request.getParameter("type"));
                a.setStyle(request.getParameter("style"));
                a.setBilling(request.getParameter("billing"));
                a.setStatus(request.getParameter("status"));
                a.setCreateDateTime(new java.util.Date());
                a.setCreator(userName);
                a.setRemarks(request.getParameter("remarks"));
                if (request.getParameter("demographic_no") != null && !(request.getParameter("demographic_no").equals(""))) {
                    a.setDemographicNo(Integer.parseInt(request.getParameter("demographic_no")));
                } else {
                    a.setDemographicNo(0);
                }

                a.setProgramId(Integer.parseInt((String) request.getSession().getAttribute("programId_oscarView")));
                a.setUrgency(null);

                appointmentDao.persist(a);


                gCalDate.setTime(a.getAppointmentDate());
                if (everyUnit.equals("day")) {
                    gCalDate.add(Calendar.DATE, delta);
                } else if (everyUnit.equalsIgnoreCase("month")) {
                    gCalDate.add(Calendar.MONTH, delta);
                } else if (everyUnit.equalsIgnoreCase("year")) {
                    gCalDate.add(Calendar.YEAR, delta);
                } else {
                    break;
                }

                if (gCalDate.after(gEndDate))
                    break;
                else
                    iDate = gCalDate.getTime();
            }

            bSucc = true;
        }


        if (request.getParameter("groupappt").equals("Group Update") || request.getParameter("groupappt").equals("Group Cancel") ||
                request.getParameter("groupappt").equals("Group Delete")) {
            int rowsAffected = 0, datano = 0;
            String createdDateTime = UtilDateUtilities.DateToString(new Date(), "yyyy-MM-dd HH:mm:ss");
            String userName = (String) session.getAttribute("userlastname") + ", " + (String) session.getAttribute("userfirstname");

            for (Enumeration e = request.getParameterNames(); e.hasMoreElements(); ) {
                StringBuffer strbuf = new StringBuffer(e.nextElement().toString());
                if (strbuf.toString().indexOf("one") == -1 && strbuf.toString().indexOf("two") == -1) continue;
                datano = Integer.parseInt(request.getParameter(strbuf.toString()));

                if (request.getParameter("groupappt").equals("Group Cancel")) {
                    Appointment appt = appointmentDao.find(Integer.parseInt(request.getParameter("appointment_no") + datano));
                    appointmentArchiveDao.archiveAppointment(appt);
                    if (appt != null) {
                        appt.setStatus("C");
                        appt.setLastUpdateUser(userName);
                        appointmentDao.merge(appt);
                        rowsAffected = 1;
                    }
                }

                //delete the selected appts
                if (request.getParameter("groupappt").equals("Group Delete")) {
                    Appointment appt = appointmentDao.find(Integer.parseInt(request.getParameter("appointment_no") + datano));
                    appointmentArchiveDao.archiveAppointment(appt);
                    rowsAffected = 0;
                    if (appt != null) {
                        appointmentDao.remove(appt.getId());
                        rowsAffected = 1;
                    }

                }

                if (request.getParameter("groupappt").equals("Group Update")) {
                    Appointment appt = appointmentDao.find(Integer.parseInt(request.getParameter("appointment_no") + datano));
                    appointmentArchiveDao.archiveAppointment(appt);
                    rowsAffected = 0;
                    if (appt != null) {
                        appointmentDao.remove(appt.getId());
                        rowsAffected = 1;
                    }

                    Appointment a = new Appointment();
                    a.setProviderNo(request.getParameter("provider_no") + datano);
                    a.setAppointmentDate(ConversionUtils.fromDateString(request.getParameter("appointment_date")));
                    a.setStartTime(ConversionUtils.fromTimeStringNoSeconds(request.getParameter("start_time")));
                    a.setEndTime(ConversionUtils.fromTimeStringNoSeconds(request.getParameter("end_time")));
                    a.setName(request.getParameter("keyword"));
                    a.setNotes(request.getParameter("notes"));
                    a.setReason(request.getParameter("reason"));
                    a.setLocation(request.getParameter("location"));
                    a.setResources(request.getParameter("resources"));
                    a.setType(request.getParameter("type"));
                    a.setStyle(request.getParameter("style"));
                    a.setBilling(request.getParameter("billing"));
                    a.setStatus(request.getParameter("status"));
                    a.setCreateDateTime(new java.util.Date());
                    a.setCreator(userName);
                    a.setRemarks(request.getParameter("remarks"));
                    if (!(request.getParameter("demographic_no").equals("")) && strbuf.toString().indexOf("one") != -1) {
                        a.setDemographicNo(Integer.parseInt(request.getParameter("demographic_no")));
                    } else {
                        a.setDemographicNo(0);
                    }

                    a.setProgramId(Integer.parseInt((String) request.getSession().getAttribute("programId_oscarView")));
                    a.setUrgency(request.getParameter("urgency"));

                    appointmentDao.persist(a);


                }
                if (rowsAffected != 1) break;
            }
            if (rowsAffected == 1) bSucc = true;
        }

        if (bSucc) {
%>
<h1><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.appointmentgrouprecords.msgAddSuccess"/></h1>
<script LANGUAGE="JavaScript">
    self.opener.refresh();
    self.close();
</script>
<%
} else {
%>
<p>
<h1><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.appointmentgrouprecords.msgAddFailure"/></h1>

<%
        }
        return;
    } // if (request.getParameter("groupappt") != null)
%>
<html>
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.appointmentgrouprecords.title"/></title>
        <fmt:setBundle basename="oscarResources"/>
        <fmt:message key="appointment.appointmentgrouprecords.msgExitConfirmation" var="msgExitConfirmation"/>
        <fmt:message key="appointment.appointmentgrouprecords.msgDeleteConfirmation" var="msgDeleteConfirmation"/>
        <script type="text/javascript">
            function onCheck(a) {
                document.getElementById("everyUnit").value = a.value;
                document.getElementById("everyUnitLabel").textContent = a.dataset.labelPlural;
            }

            function onExit() {
                if (confirm('${e:forJavaScript(msgExitConfirmation)}')) {
                    window.close();
                }
            }

            var saveTemp = 0;

            function onButDelete() {
                saveTemp = 1;
            }

            function onSub() {
                if (saveTemp == 1) {
                    return (confirm('${e:forJavaScript(msgDeleteConfirmation)}'));
                }
            }
        </script>
    </head>

    <body onLoad="setfocus()">
    <div class="container-fluid p-3">

        <div class="page-header-bar">
            <h4 class="page-header-title">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" viewBox="0 0 16 16" class="page-header-icon">
                    <path d="M11 6.5a.5.5 0 0 1 .5-.5h1a.5.5 0 0 1 .5.5v1a.5.5 0 0 1-.5.5h-1a.5.5 0 0 1-.5-.5z"/>
                    <path d="M3.5 0a.5.5 0 0 1 .5.5V1h8V.5a.5.5 0 0 1 1 0V1h1a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H2a2 2 0 0 1-2-2V3a2 2 0 0 1 2-2h1V.5a.5.5 0 0 1 .5-.5M1 4v10a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V4z"/>
                </svg>
                &nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.appointmenteditrepeatbooking.title"/>
            </h4>
        </div>

        <form name="groupappt" method="POST"
              action="appointmentrepeatbooking.jsp" onSubmit="return onSub();">
            <input type="hidden" name="groupappt" value="">
            <input type="hidden" name="everyUnit" id="everyUnit" value="day">

            <div class="bg-light border rounded p-3 mb-3">
                <div class="mb-3">
                    <label class="form-label fw-bold"><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.appointmenteditrepeatbooking.howoften"/></label>
                    <div class="ms-2">
                        <fmt:setBundle basename="oscarResources"/>
                        <fmt:message key="day" var="labelDay"/>
                        <fmt:message key="week" var="labelWeek"/>
                        <fmt:message key="month" var="labelMonth"/>
                        <fmt:message key="year" var="labelYear"/>
                        <fmt:message key="day.plural" var="labelDayPlural"/>
                        <fmt:message key="week.plural" var="labelWeekPlural"/>
                        <fmt:message key="month.plural" var="labelMonthPlural"/>
                        <fmt:message key="year.plural" var="labelYearPlural"/>
                        <div class="form-check form-check-inline">
                            <input class="form-check-input" type="radio" name="dateUnit" id="dateUnit_day" value="day" checked
                                   data-label="${e:forHtmlAttribute(labelDay)}" data-label-plural="${e:forHtmlAttribute(labelDayPlural)}" onclick='onCheck(this)'>
                            <label class="form-check-label" for="dateUnit_day">${e:forHtml(labelDay)}</label>
                        </div>
                        <div class="form-check form-check-inline">
                            <input class="form-check-input" type="radio" name="dateUnit" id="dateUnit_week" value="week"
                                   data-label="${e:forHtmlAttribute(labelWeek)}" data-label-plural="${e:forHtmlAttribute(labelWeekPlural)}" onclick='onCheck(this)'>
                            <label class="form-check-label" for="dateUnit_week">${e:forHtml(labelWeek)}</label>
                        </div>
                        <div class="form-check form-check-inline">
                            <input class="form-check-input" type="radio" name="dateUnit" id="dateUnit_month" value="month"
                                   data-label="${e:forHtmlAttribute(labelMonth)}" data-label-plural="${e:forHtmlAttribute(labelMonthPlural)}" onclick='onCheck(this)'>
                            <label class="form-check-label" for="dateUnit_month">${e:forHtml(labelMonth)}</label>
                        </div>
                        <div class="form-check form-check-inline">
                            <input class="form-check-input" type="radio" name="dateUnit" id="dateUnit_year" value="year"
                                   data-label="${e:forHtmlAttribute(labelYear)}" data-label-plural="${e:forHtmlAttribute(labelYearPlural)}" onclick='onCheck(this)'>
                            <label class="form-check-label" for="dateUnit_year">${e:forHtml(labelYear)}</label>
                        </div>
                    </div>
                </div>

                <div class="row mb-3">
                    <label class="col-sm-3 col-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.appointmenteditrepeatbooking.every"/></label>
                    <div class="col-sm-9 d-flex align-items-center gap-2">
                        <select name="everyNum" class="form-select form-select-sm" style="width: 70px;">
                            <%
                                for (int i = 1; i < 12; i++) {
                            %>
                            <option value="<%=i%>"><%=i%></option>
                            <%
                                }
                            %>
                        </select>
                        <span id="everyUnitLabel" class="text-muted">${e:forHtml(labelDayPlural)}</span>
                    </div>
                </div>

                <div class="row mb-2">
                    <label class="col-sm-3 col-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.appointmenteditrepeatbooking.endon"/></label>
                    <div class="col-sm-9">
                        <input type="date" id="endDate" name="endDate" class="form-control form-control-sm" style="width: 170px;"
                               value="<%=request.getParameter("appointment_date") != null ? Encode.forHtmlAttribute(request.getParameter("appointment_date")) : UtilDateUtilities.DateToString(new Date(), "yyyy-MM-dd")%>">
                    </div>
                </div>
            </div>

            <div class="d-flex justify-content-between align-items-center">
                <div>
                    <% if (bEdit) { %>
                    <input type="button" class="btn btn-primary btn-sm"
                           onclick="document.forms['groupappt'].groupappt.value='Group Update'; document.forms['groupappt'].submit();"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.appointmentgrouprecords.btnGroupUpdate"/>">
                    <input type="button" class="btn btn-secondary btn-sm"
                           onclick="document.forms['groupappt'].groupappt.value='Group Cancel'; document.forms['groupappt'].submit();"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.appointmentgrouprecords.btnGroupCancel"/>">
                    <input type="button" class="btn btn-danger btn-sm"
                           onclick="onButDelete(); document.forms['groupappt'].groupappt.value='Group Delete'; document.forms['groupappt'].submit();"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.appointmentgrouprecords.btnGroupDelete"/>">
                    <% } else { %>
                    <input type="button" class="btn btn-primary btn-sm"
                           onclick="document.forms['groupappt'].groupappt.value='Add Group Appointment'; document.forms['groupappt'].submit();"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.appointmentgrouprecords.btnAddGroupAppt"/>">
                    <% } %>
                    <input type="button" class="btn btn-secondary btn-sm"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnBack"/>"
                           onClick="window.history.go(-1);return false;">
                </div>
            </div>

            <%
                String temp = null;
                String csrfTokenName = CsrfGuard.getInstance().getTokenName();
                for (Enumeration e = request.getParameterNames(); e.hasMoreElements(); ) {
                    temp = e.nextElement().toString();
                    if (temp.equals("dboperation") || temp.equals("displaymode") || temp.equals("search_mode") || temp.equals("chart_no") || temp.equals(csrfTokenName))
                        continue;
                    out.println("<input type=\"hidden\" name=\"" + Encode.forHtmlAttribute(temp) + "\" value=\"" + Encode.forHtmlAttribute(request.getParameter(temp) == null ? "" : request.getParameter(temp)) + "\">");
                }
            %>
        </form>
    </div>

    </body>
</html>
