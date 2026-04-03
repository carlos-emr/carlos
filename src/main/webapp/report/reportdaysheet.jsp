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
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_report,_admin.reporting" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_report&type=_admin.reporting");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<%@ page import="java.util.*, java.sql.*, io.github.carlos_emr.*, java.text.*" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Appointment" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.MyGroup" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.MyGroupDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ProviderData" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ProviderDataDao" %>
<%@ page import="io.github.carlos_emr.carlos.util.ConversionUtils" %>
<%@ page import="org.owasp.encoder.Encode" %>
<jsp:useBean id="daySheetBean" class="io.github.carlos_emr.AppointmentMainBean" scope="page"/>
<jsp:useBean id="myGroupBean" class="java.util.Properties" scope="page"/>
<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>

<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<%
    String curProvider_no = (String) session.getAttribute("user");
    String orderby = request.getParameter("orderby") != null ? request.getParameter("orderby") : ("start_time");

    java.util.Properties oscarVariables = io.github.carlos_emr.CarlosProperties.getInstance();

    SimpleDateFormat dayFormatter = new SimpleDateFormat("yyyy-MM-dd");
    int count = 0;

    AppointmentArchiveDao appointmentArchiveDao = SpringUtils.getBean(AppointmentArchiveDao.class);
    OscarAppointmentDao appointmentDao = SpringUtils.getBean(OscarAppointmentDao.class);
    MyGroupDao myGroupDao = SpringUtils.getBean(MyGroupDao.class);
    ProviderDataDao providerDataDao = SpringUtils.getBean(ProviderDataDao.class);

    String[][] dbQueries;
    dbQueries = new String[][]{
            {"search_daysheetall", "select concat(d.year_of_birth,'/',d.month_of_birth,'/',d.date_of_birth)as dob, d.family_doctor, a.appointment_date, a.provider_no, a.start_time, a.end_time, a.reason, a.name,a.bookingSource, p.last_name, p.first_name, d.sex, d.hin, d.ver, d.family_doctor, d.provider_no as doc_no, d.phone, d.roster_status, p2.last_name as doc_last_name, p2.first_name as doc_first_name, d.chart_no from (appointment a, provider p) left join demographic d on a.demographic_no=d.demographic_no left join provider p2 on d.provider_no=p2.provider_no where a.appointment_date>=? and a.appointment_date<=? and a.start_time>=? and a.end_time<? and a.provider_no=p.provider_no and BINARY a.status not like 'C%' order by p.last_name, p.first_name, a.appointment_date, " + orderby},
            {"search_daysheetsingleall", "select concat(d.year_of_birth,'/',d.month_of_birth,'/',d.date_of_birth)as dob, d.family_doctor, a.appointment_date, a.provider_no, a.start_time, a.end_time, a.reason, a.name,a.bookingSource, p.last_name, p.first_name, d.sex, d.hin, d.ver, d.family_doctor, d.provider_no as doc_no, d.phone, d.roster_status, p2.last_name as doc_last_name, p2.first_name as doc_first_name, d.chart_no  from (appointment a, provider p )left join demographic d on a.demographic_no=d.demographic_no left join provider p2 on d.provider_no=p2.provider_no where a.appointment_date>=? and a.appointment_date<=? and a.start_time>=? and a.end_time<? and a.provider_no=? and BINARY a.status not like 'C%' and a.provider_no=p.provider_no order by a.appointment_date," + orderby},
            {"search_daysheetnew", "select concat(d.year_of_birth,'/',d.month_of_birth,'/',d.date_of_birth)as dob, d.family_doctor, a.appointment_date, a.provider_no, a.start_time, a.end_time, a.reason, a.name,a.bookingSource, p.last_name, p.first_name, d.sex, d.hin, d.ver, d.family_doctor, d.provider_no as doc_no, d.phone, d.roster_status, p2.last_name as doc_last_name, p2.first_name as doc_first_name, d.chart_no  from (appointment a, provider p) left join demographic d on a.demographic_no=d.demographic_no left join provider p2 on d.provider_no=p2.provider_no where a.appointment_date=? and a.provider_no=p.provider_no and a.status like binary 't' order by p.last_name, p.first_name, a.appointment_date," + orderby},
            {"search_daysheetsinglenew", "select concat(d.year_of_birth,'/',d.month_of_birth,'/',d.date_of_birth)as dob, d.family_doctor, a.appointment_date, a.provider_no, a.start_time, a.end_time, a.reason, a.name,a.bookingSource, p.last_name, p.first_name, d.sex, d.hin, d.ver, d.family_doctor, d.provider_no as doc_no, d.phone, d.roster_status, p2.last_name as doc_last_name, p2.first_name as doc_first_name, d.chart_no  from (appointment a, provider p) left join demographic d on a.demographic_no=d.demographic_no left join provider p2 on d.provider_no=p2.provider_no where a.appointment_date=? and a.provider_no=? and a.status like binary 't' and a.provider_no=p.provider_no order by a.appointment_date," + orderby}
    };

    daySheetBean.doConfigure(dbQueries);

    boolean isSiteAccessPrivacy = false;
    boolean isTeamAccessPrivacy = false;
%>
<security:oscarSec objectName="_site_access_privacy" roleName="<%=roleName$%>" rights="r" reverse="false"><%isSiteAccessPrivacy = true; %></security:oscarSec>
<security:oscarSec objectName="_team_access_privacy" roleName="<%=roleName$%>" rights="r" reverse="false"><%isTeamAccessPrivacy = true;%></security:oscarSec>
<%
    List<ProviderData> pdList = null;
    HashMap<String, String> providerMap = new HashMap<String, String>();

//multisites function
    if (isSiteAccessPrivacy || isTeamAccessPrivacy) {

        if (isSiteAccessPrivacy)
            pdList = providerDataDao.findByProviderSite(curProvider_no);

        if (isTeamAccessPrivacy)
            pdList = providerDataDao.findByProviderTeam(curProvider_no);

        for (ProviderData providerData : pdList) {
            providerMap.put(providerData.getId(), "true");
        }
    }
%>
<html>
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportdaysheet.title"/></title>
        <!-- Prototype.js removed — using vanilla JS (Phase 1c migration) -->

        <script language="JavaScript">
            function hideOnSource() {
                var selfBooked = document.getElementById('onlySelfBooked');
                document.querySelectorAll('tr.oscar').forEach(function(el) {
                    el.style.display = selfBooked.checked ? 'none' : '';
                });
            }
        </script>

        <style type="text/css" media="print">
            .searchBox { display: none; }
        </style>
    </head>
    <%
        boolean bDob = oscarVariables.getProperty("daysheet_dob", "").equalsIgnoreCase("true") ? true : false;

        GregorianCalendar now = new GregorianCalendar();
        String createtime = now.get(Calendar.YEAR) + "-" + (now.get(Calendar.MONTH) + 1) + "-" + now.get(Calendar.DAY_OF_MONTH) + " " + now.get(Calendar.HOUR_OF_DAY) + ":" + now.get(Calendar.MINUTE);
        now.add(now.DATE, 1);
        int curYear = now.get(Calendar.YEAR);
        int curMonth = (now.get(Calendar.MONTH) + 1);
        int curDay = now.get(Calendar.DAY_OF_MONTH);

        String sdate = request.getParameter("sdate") != null ? request.getParameter("sdate") : (curYear + "-" + curMonth + "-" + curDay);
        String edate = request.getParameter("edate") != null ? request.getParameter("edate") : "";
        String sTime = request.getParameter("sTime") != null ? (request.getParameter("sTime") + ":00:00") : "00:00:00";
        String eTime = request.getParameter("eTime") != null ? (request.getParameter("eTime") + ":00:00") : "24:00:00";
        String provider_no = request.getParameter("provider_no") != null ? request.getParameter("provider_no") : "175";
        ResultSet rsdemo = null;

        //initial myGroupBean if neccessary
        if (provider_no.startsWith("_grp_")) {
            List<MyGroup> myGroups = myGroupDao.getGroupByGroupNo(provider_no.substring(5));
            for (MyGroup myGroup : myGroups) {
                myGroupBean.setProperty(myGroup.getId().getProviderNo(), "true");
            }
        }
    %>
    <body>
    <div class="container">
    <div class="searchBox">

        <div style="background:#f5f5f5; padding:8px 15px; border-bottom:1px solid #ddd; margin-bottom:10px;">
            <h4 style="margin:0; font-size:18px; display:inline-block;">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" viewBox="0 0 16 16" style="vertical-align:text-bottom">
                    <path d="M1 2.5A1.5 1.5 0 0 1 2.5 1h3A1.5 1.5 0 0 1 7 2.5v3A1.5 1.5 0 0 1 5.5 7h-3A1.5 1.5 0 0 1 1 5.5zM2.5 2a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5zm6.5.5A1.5 1.5 0 0 1 10.5 1h3A1.5 1.5 0 0 1 15 2.5v3A1.5 1.5 0 0 1 13.5 7h-3A1.5 1.5 0 0 1 9 5.5zm1.5-.5a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5zM1 10.5A1.5 1.5 0 0 1 2.5 9h3A1.5 1.5 0 0 1 7 10.5v3A1.5 1.5 0 0 1 5.5 15h-3A1.5 1.5 0 0 1 1 13.5zm1.5-.5a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5zm6.5.5A1.5 1.5 0 0 1 10.5 9h3a1.5 1.5 0 0 1 1.5 1.5v3a1.5 1.5 0 0 1-1.5 1.5h-3A1.5 1.5 0 0 1 9 13.5zm1.5-.5a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5z"/>
                </svg>
                &nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportdaysheet.msgMainLabel"/>
            </h4>
            <span style="margin-left:15px;">
                <input type="checkbox" onclick="hideOnSource();" id="onlySelfBooked"/>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportdaysheet.msgSelfBookedCheck"/>
            </span>
            <span style="float:right;">
                <span style="margin-right:10px; font-size:12px; color:#888;"><%=createtime%></span>
                <input type="button" class="btn btn-sm btn-secondary" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportdaysheet.btnPrint"/>" onClick="window.print()">
                <input type="button" class="btn btn-sm btn-secondary" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnExit"/>" onClick="window.close()">
            </span>
        </div>

    </div>

    <%
        boolean bFistL = true; //first line in a table for TH
        String strTemp = "";
        String dateTemp = "";
        String[] param = new String[3];
        param[0] = (String) session.getAttribute("user");
        param[1] = sdate;
        param[2] = provider_no;
        String[] parama = new String[5];
        parama[0] = sdate;
        parama[1] = edate;
        parama[2] = sTime;
        parama[3] = eTime;
        parama[4] = provider_no;
        if (request.getParameter("dsmode") != null && request.getParameter("dsmode").equals("all")) {
            if (!provider_no.equals("*") && !provider_no.startsWith("_grp_")) {
                rsdemo = daySheetBean.queryResults(parama, "search_daysheetsingleall");

            } else { //select all providers
                rsdemo = daySheetBean.queryResults(new String[]{parama[0], parama[1], sTime, eTime}, "search_daysheetall");
            }
        } else { //new appt, need to update status
            if (!provider_no.equals("*") && !provider_no.startsWith("_grp_")) {
                rsdemo = daySheetBean.queryResults(new String[]{param[1], param[2]}, "search_daysheetsinglenew");
                try {
                    List<Appointment> appts = appointmentDao.findByProviderDayAndStatus(param[2], dayFormatter.parse(param[1]), "t");
                    for (Appointment appt : appts) {
                        appointmentArchiveDao.archiveAppointment(appt);
                    }
                } catch (java.text.ParseException e) {
                    io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().error("Cannot archive appt", e);
                }
                for (Appointment a : appointmentDao.findByDayAndStatus(ConversionUtils.fromDateString(sdate), "t")) {
                    if (a.getProviderNo().equals(provider_no)) {
                        a.setStatus("T");
                        a.setLastUpdateUser((String) session.getAttribute("user"));
                        a.setUpdateDateTime(new java.util.Date());
                        appointmentDao.merge(a);
                    }
                }

            } else { //select all providers
                rsdemo = daySheetBean.queryResults(param[0], "search_daysheetnew");
                try {
                    List<Appointment> appts = appointmentDao.findByProviderDayAndStatus(param[2], dayFormatter.parse(param[1]), "t");
                    for (Appointment appt : appts) {
                        appointmentArchiveDao.archiveAppointment(appt);
                    }
                } catch (java.text.ParseException e) {
                    io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().error("Cannot archive appt", e);
                }
                for (Appointment a : appointmentDao.findByDayAndStatus(ConversionUtils.fromDateString(sdate), "t")) {
                    a.setStatus("T");
                    a.setLastUpdateUser((String) session.getAttribute("user"));
                    a.setUpdateDateTime(new java.util.Date());
                    appointmentDao.merge(a);
                }
            }
        }
        while (rsdemo.next()) {
            //if it is a group and a group member
            if (!myGroupBean.isEmpty()) {
                if (myGroupBean.getProperty(rsdemo.getString("provider_no")) == null) continue;
            }

            //multisites. skip record if not belong to same site/team
            if (isSiteAccessPrivacy || isTeamAccessPrivacy) {
                if (providerMap.get(rsdemo.getString("provider_no")) == null) continue;
            }

            if (!strTemp.equals(rsdemo.getString("provider_no")) || !dateTemp.equals(rsdemo.getString("appointment_date"))) { //new providers for a new table
                strTemp = rsdemo.getString("provider_no");
                dateTemp = rsdemo.getString("appointment_date");
                bFistL = true;
                out.println("</table>");
            }
            if (bFistL) {
                bFistL = false;
                String encodedProviderNo = Encode.forUriComponent(provider_no);
                String encodedSdate = Encode.forUriComponent(sdate);
                String encodedEdate = Encode.forUriComponent(edate);
                String encodedDsmode = request.getParameter("dsmode") != null ? "&dsmode=" + Encode.forUriComponent(request.getParameter("dsmode")) : "";
                String sortBaseUrl = "reportdaysheet.jsp?provider_no=" + encodedProviderNo + "&sdate=" + encodedSdate + "&edate=" + encodedEdate;
    %>
    <div class="section-header" style="font-weight:bold; font-size:14px; padding:6px 10px; background:#eee; border-bottom:1px solid #ddd; margin:15px 0 0 0;">
        <%=Encode.forHtml(providerBean.getProperty(rsdemo.getString("provider_no")) + " - " + dateTemp + (request.getParameter("sTime") != null ? (" " + sTime + "-" + eTime) : "")) %>
    </div>
    <table class="table table-sm table-bordered table-striped" style="font-size:13px; margin-bottom:0;">
        <thead>
        <tr>
            <th style="width:6%"><a href="<%=sortBaseUrl%>&orderby=start_time<%= Encode.forHtmlAttribute(encodedDsmode) %>"><fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportdaysheet.msgAppointmentTime"/></a></th>
            <th style="width:15%"><a href="<%=sortBaseUrl%>&orderby=name<%= Encode.forHtmlAttribute(encodedDsmode) %>"><fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportdaysheet.msgPatientLastName"/></a></th>
            <th style="width:10%"><a href="<%=sortBaseUrl%>&orderby=phone<%= Encode.forHtmlAttribute(encodedDsmode) %>">Phone</a></th>
            <th style="width:3%"><a href="<%=sortBaseUrl%>&orderby=sex<%= Encode.forHtmlAttribute(encodedDsmode) %>">Gender</a></th>
            <th style="width:9%"><a href="<%=sortBaseUrl%>&orderby=hin<%= Encode.forHtmlAttribute(encodedDsmode) %>">Health Card</a></th>
            <th style="width:5%"><a href="<%=sortBaseUrl%>&orderby=ver<%= Encode.forHtmlAttribute(encodedDsmode) %>">Version</a></th>
            <th style="width:6%"><a href="<%=sortBaseUrl%>&orderby=chart_no<%= Encode.forHtmlAttribute(encodedDsmode) %>"><fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportdaysheet.msgChartNo"/></a></th>
            <% if (!bDob) {%>
            <th style="width:6%"><a href="<%=sortBaseUrl%>&orderby=roster_status<%= Encode.forHtmlAttribute(encodedDsmode) %>"><fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportdaysheet.msgRosterStatus"/></a></th>
            <% } else {%>
            <th style="width:10%">DOB</th>
            <% }%>
            <th><fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportdaysheet.msgBookingStatus"/></th>
            <th style="width:30%"><fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportdaysheet.msgComments"/></th>
        </tr>
        </thead>
        <tbody>
        <%
            }
            count++;
        %>
        <tr class="<%=rsdemo.getString("bookingSource")==null?"oscar":"self"%>" id="r<%=count %>">
            <td title="<%=Encode.forHtmlAttribute("End Time: "+rsdemo.getString("end_time"))%>"><%=Encode.forHtml(rsdemo.getString("start_time").substring(0, 5))%></td>
            <td><%=rsdemo.getString("name") == null ? "." : ""%><%=Encode.forHtml(Misc.toUpperLowerCase(rsdemo.getString("name")))%></td>
            <td><%=Encode.forHtml(rsdemo.getString("phone") == null ? "" : rsdemo.getString("phone"))%></td>
            <td><%=Encode.forHtml(rsdemo.getString("sex") == null ? "" : rsdemo.getString("sex"))%></td>
            <td><%=Encode.forHtml(rsdemo.getString("hin") == null ? "" : rsdemo.getString("hin"))%></td>
            <td><%=Encode.forHtml(rsdemo.getString("ver") == null ? "" : rsdemo.getString("ver"))%></td>
            <td><%=Encode.forHtml(rsdemo.getString("chart_no") == null ? "" : rsdemo.getString("chart_no"))%></td>
            <% if (!bDob) {%>
            <td><%=Encode.forHtml(rsdemo.getString("roster_status") == null ? "" : rsdemo.getString("roster_status"))%></td>
            <% } else {
                String dob = rsdemo.getString("dob");
            %>
            <td><%=Encode.forHtml(dob == null ? "" : dob)%></td>
            <% }%>
            <td>
                <%if (rsdemo.getString("bookingSource") == null) {%>
                &nbsp;
                <%} else {%>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportdaysheet.msgSelfBooked"/>
                <%}%>
            </td>
            <td>
                <% if (rsdemo.getString("doc_no") != null && !daySheetBean.getString(rsdemo, "doc_no").equals("") && !daySheetBean.getString(rsdemo, "doc_no").equals(daySheetBean.getString(rsdemo, "provider_no"))) {
                    String doc_first_name = daySheetBean.getString(rsdemo, "doc_first_name");
                    char initial = 0x20;
                    if (doc_first_name.length() > 0) {
                        initial = doc_first_name.charAt(0);
                    }
                %>
                [<%=Encode.forHtml(daySheetBean.getString(rsdemo, "doc_last_name"))%>, <%=initial%>]
                &nbsp; <% } %> <% if (bDob && daySheetBean.getString(rsdemo, "family_doctor") != null) {
                String rd = SxmlMisc.getXmlContent(daySheetBean.getString(rsdemo, "family_doctor"), "rd");
                rd = rd != null ? rd : "";
            %> [<%=Encode.forHtml(rd)%>]&nbsp; <% } %> <%=Encode.forHtml(daySheetBean.getString(rsdemo, "reason"))%>&nbsp;
            </td>
        </tr>
        <%
            }
        %>
        </tbody>
    </table>

    </div>
    </body>
</html>
