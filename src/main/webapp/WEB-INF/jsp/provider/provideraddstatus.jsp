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
  Purpose: Updates an appointment status from the provider schedule and redirects
  back to the provider control day view.

  Features:
  - Requires an authenticated provider session before processing.
  - Validates appointment and provider identifiers before applying updates.
  - Archives the appointment before merging the new status.
  - Publishes the appointment status-change event only after a successful update.
  - URI-encodes provider-control redirect parameters.

  Expected parameters:
  - appointment_no: numeric appointment identifier.
  - provider_no: numeric provider identifier.
  - status/statusch: status fragments combined for the new appointment status.
  - view: optional provider view flag, either 0 or 1.
  - year/month/day/viewall/x/y/viewWeek/curProvider/curProviderName: redirect context.

  Expected session attributes:
  - user: authenticated provider number.

  @since 2026-06-11
--%>

<%@ page
  import="java.sql.*, java.util.*, io.github.carlos_emr.MyDateFormat,io.github.carlos_emr.carlos.event.EventService" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@page import="io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Appointment" %>
<%@page import="io.github.carlos_emr.carlos.providers.gate.ProviderAddStatusValidator" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%
  AppointmentArchiveDao appointmentArchiveDao = (AppointmentArchiveDao) SpringUtils.getBean(AppointmentArchiveDao.class);
  OscarAppointmentDao appointmentDao = (OscarAppointmentDao) SpringUtils.getBean(OscarAppointmentDao.class);
%>
<%
  //if action is good, then give me the result
  String curUser_no = (String) session.getAttribute("user");

  if (curUser_no == null) {
    response.sendRedirect(request.getContextPath() + "/logout.htm");
    return;
  }
  String appointmentNoParam = request.getParameter("appointment_no");
  String status = request.getParameter("status");
  String statusch = request.getParameter("statusch");
  String providerNoParam = request.getParameter("provider_no");
  String appointmentStatus = ProviderAddStatusValidator.buildValidatedAppointmentStatus(status, statusch);

  if (appointmentStatus == null || providerNoParam == null) {
    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
    return;
  }

  int appointmentNo;
  int providerNo;

  try {
    appointmentNo = Integer.parseInt(appointmentNoParam);
  } catch (NumberFormatException e) {
    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
    return;
  }
  try {
    providerNo = Integer.parseInt(providerNoParam);
  } catch (NumberFormatException e) {
    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
    return;
  }

  Appointment appt = appointmentDao.find(appointmentNo);
  int rowsAffected = 0;
  int view = 0;

  String viewParam = request.getParameter("view");

  if (viewParam != null) {
    if (!"0".equals(viewParam) && !"1".equals(viewParam)) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    view = "1".equals(viewParam) ? 1 : 0;
  }

  if (appt != null) {
    appointmentArchiveDao.archiveAppointment(appt);
    appt.setStatus(appointmentStatus);
    appt.setLastUpdateUser(curUser_no);
    appointmentDao.merge(appt);
    rowsAffected = 1;
  }

  if (rowsAffected == 1) {//add_record
    EventService eventService = SpringUtils.getBean(EventService.class);//This is when the icon is clicked in the appt screen
    eventService.appointmentStatusChanged(
      this,
      String.valueOf(appointmentNo),
      String.valueOf(providerNo),
      statusch
    );
    String strView = (view == 0) ? "0"
      : ("1&curProvider=" + SafeEncode.forUriComponent(request.getParameter("curProvider"))
      + "&curProviderName=" + SafeEncode.forUriComponent(request.getParameter("curProviderName")));
    String strViewAll = request.getParameter("viewall") == null
      ? "0"
      : SafeEncode.forUriComponent(request.getParameter("viewall"));
    String displaypage = request.getContextPath()
      + "/provider/providercontrol?year=" + SafeEncode.forUriComponent(request.getParameter("year"))
      + "&month=" + SafeEncode.forUriComponent(request.getParameter("month"))
      + "&day=" + SafeEncode.forUriComponent(request.getParameter("day"))
      + "&view=" + strView
      + "&displaymode=day&dboperation=searchappointmentday"
      + "&viewall=" + strViewAll
      + "&x=" + SafeEncode.forUriComponent(request.getParameter("x"))
      + "&y=" + SafeEncode.forUriComponent(request.getParameter("y"));
    if (request.getParameter("viewWeek") != null) {
      displaypage += "&provider_no="
        + SafeEncode.forUriComponent(String.valueOf(providerNo));
    }
    out.clear();
    response.sendRedirect(displaypage);
    //pageContext.forward(displaypage); //forward request&response to the target page
    return;
  } else {
%>
<p>
<h1><fmt:message key="AddProviderStatus.msgAddFailure"/></h1>

<%
  }
%>
