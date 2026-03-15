<%--


    Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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

    This software was written for
    Centre for Research on Inner City Health, St. Michael's Hospital,
    Toronto, Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>


<%@ include file="/casemgmt/taglibs.jsp" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>

<%
    String redirectURL = request.getContextPath() +
        "/CaseManagementEntry.do?method=setUpMainEncounter&from=casemgmt&chain=list" +
        "&demographicNo=" + request.getParameter("demographicNo") +
        "&providerNo=" + request.getParameter("providerNo") +
        "&reason=" + (request.getParameter("reason") != null ? URLEncoder.encode(request.getParameter("reason"), StandardCharsets.UTF_8) : "") +
        "&reasonCode=" + (request.getParameter("reasonCode") != null ? URLEncoder.encode(request.getParameter("reasonCode"), StandardCharsets.UTF_8) : "") +
        "&appointmentNo=" + (request.getParameter("appointmentNo") != null ? URLEncoder.encode(request.getParameter("appointmentNo"), StandardCharsets.UTF_8) : "") +
        "&appointmentDate=" + (request.getParameter("appointmentDate") != null ? URLEncoder.encode(request.getParameter("appointmentDate"), StandardCharsets.UTF_8) : "") +
        "&start_time=" + (request.getParameter("start_time") != null ? URLEncoder.encode(request.getParameter("start_time"), StandardCharsets.UTF_8) : "") +
        "&apptProvider=" + (request.getParameter("apptProvider") != null ? URLEncoder.encode(request.getParameter("apptProvider"), StandardCharsets.UTF_8) : "") +
        "&providerview=" + (request.getParameter("providerview") != null ? URLEncoder.encode(request.getParameter("providerview"), StandardCharsets.UTF_8) : "") +
        "&OscarMsgTypeLink=" + (request.getParameter("OscarMsgTypeLink") != null ? URLEncoder.encode(request.getParameter("OscarMsgTypeLink"), StandardCharsets.UTF_8) : "") +
        "&msgType=" + (request.getParameter("msgType") != null ? URLEncoder.encode(request.getParameter("msgType"), StandardCharsets.UTF_8) : "");
    response.sendRedirect(redirectURL);
%>
