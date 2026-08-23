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
  - Applies stale-status validation, archival, and update under one appointment row lock.
  - Publishes authoritative appointment provider/status values only after commit.
  - URI-encodes provider-control redirect parameters.
  - Returns the refreshed day-view URL for AJAX callers and redirects legacy callers.

  Expected parameters:
  - appointment_no: numeric appointment identifier.
  - provider_no: numeric provider identifier.
  - status/statusch: status fragments combined for the new appointment status.
  - currentstatus: appointment status rendered when the schedule link was generated.
  - view: optional provider view flag, either 0 or 1.
  - year/month/day/viewall/x/y/viewWeek/curProvider/curProviderName: redirect context.

  Expected session attributes:
  - user: authenticated provider number.

  @since 2026-06-11
--%>

<%@ page
  import="java.sql.*, java.util.*, io.github.carlos_emr.MyDateFormat" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@page import="io.github.carlos_emr.carlos.appointment.service.AppointmentStatusTransitionException" %>
<%@page import="io.github.carlos_emr.carlos.appointment.service.AppointmentStatusTransitionService" %>
<%@page import="io.github.carlos_emr.carlos.providers.gate.ProviderAddStatusValidator" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@page import="java.io.IOException" %>
<%@page import="jakarta.servlet.jsp.JspWriter" %>
<%!
  private void sendStatusError(
      HttpServletRequest request,
      HttpServletResponse response,
      JspWriter out,
      boolean ajaxRequest,
      int status) throws IOException {
    // providercontrol.jsp dispatches this JSP with RequestDispatcher.include().
    // Included resources cannot set their caller's status, so always expose the
    // intended status for the outer JSP to apply. The attribute is harmless for
    // direct /provider/AddStatus requests.
    request.setAttribute("providerAddStatusHttpStatus", status);
    if (ajaxRequest) {
      // Setting a status avoids Tomcat's HTML error-page dispatch, which can
      // turn a Fetch response into a misleading 200 after an internal forward.
      out.clear();
      response.setStatus(status);
      response.setContentType("text/plain;charset=UTF-8");
    } else {
      response.sendError(status);
    }
  }
%>
<%
  AppointmentStatusTransitionService appointmentStatusTransitionService =
    SpringUtils.getBean(AppointmentStatusTransitionService.class);
%>
<%
  //if action is good, then give me the result
  boolean ajaxRequest = "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
  if (ajaxRequest) {
    response.setContentType("text/plain;charset=UTF-8");
  }
  String curUser_no = (String) session.getAttribute("user");

  if (curUser_no == null) {
    response.sendRedirect(request.getContextPath() + "/logout.htm");
    return;
  }
  String appointmentNoParam = request.getParameter("appointment_no");
  String status = request.getParameter("status");
  String statusch = request.getParameter("statusch");
  String submittedCurrentStatus = request.getParameter("currentstatus");
  String providerNoParam = request.getParameter("provider_no");
  String appointmentStatus = ProviderAddStatusValidator.buildValidatedAppointmentStatus(status, statusch);

  if (appointmentStatus == null || submittedCurrentStatus == null || providerNoParam == null) {
    sendStatusError(request, response, out, ajaxRequest, HttpServletResponse.SC_BAD_REQUEST);
    return;
  }

  int appointmentNo;

  try {
    appointmentNo = Integer.parseInt(appointmentNoParam);
  } catch (NumberFormatException e) {
    sendStatusError(request, response, out, ajaxRequest, HttpServletResponse.SC_BAD_REQUEST);
    return;
  }
  try {
    Integer.parseInt(providerNoParam);
  } catch (NumberFormatException e) {
    sendStatusError(request, response, out, ajaxRequest, HttpServletResponse.SC_BAD_REQUEST);
    return;
  }

  int view = 0;

  String viewParam = request.getParameter("view");

  if (viewParam != null) {
    if (!"0".equals(viewParam) && !"1".equals(viewParam)) {
      sendStatusError(request, response, out, ajaxRequest, HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    view = "1".equals(viewParam) ? 1 : 0;
  }

  AppointmentStatusTransitionService.TransitionResult transitionResult;
  try {
    transitionResult = appointmentStatusTransitionService.transition(
      appointmentNo,
      providerNoParam,
      submittedCurrentStatus,
      appointmentStatus,
      curUser_no
    );
  } catch (AppointmentStatusTransitionException e) {
    if (e.getReason() == AppointmentStatusTransitionException.Reason.APPOINTMENT_NOT_FOUND) {
      sendStatusError(request, response, out, ajaxRequest, HttpServletResponse.SC_NOT_FOUND);
    } else if (e.getReason() == AppointmentStatusTransitionException.Reason.STALE_STATUS) {
      sendStatusError(request, response, out, ajaxRequest, HttpServletResponse.SC_CONFLICT);
    } else {
      sendStatusError(request, response, out, ajaxRequest, HttpServletResponse.SC_BAD_REQUEST);
    }
    return;
  }

  String appointmentProviderNo = transitionResult.providerNo();
  appointmentStatus = transitionResult.appointmentStatus();
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
      + SafeEncode.forUriComponent(appointmentProviderNo);
  }
  out.clear();
  // The outer providercontrol.jsp must perform this redirect when this JSP is
  // included. RequestDispatcher.include() deliberately ignores sendRedirect().
  request.setAttribute("providerAddStatusRedirectTarget", displaypage);
  if (ajaxRequest) {
    // In-place status update: return the refreshed day-view URL so the
    // browser navigates with a history-replacing GET instead of a
    // full-page POST/redirect that flashes a blank page.
    out.print(displaypage);
  } else {
    response.sendRedirect(displaypage);
  }
  //pageContext.forward(displaypage); //forward request&response to the target page
%>
