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

--%>


<%@ include file="/casemgmt/taglibs.jsp" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>

<!-- logic:redirect forward="/admissionListAction.admit" / -->

<%
    String useNewCaseMgmt;
    if ((useNewCaseMgmt = request.getParameter("newCaseManagement")) != null) {
        session.setAttribute("newCaseManagement", useNewCaseMgmt);
        ArrayList<String> users = (ArrayList<String>) session.getServletContext().getAttribute("CaseMgmtUsers");
        if (users != null) {
            users.add(request.getParameter("providerNo"));
            session.getServletContext().setAttribute("CaseMgmtUsers", users);
        }
    } else {
        useNewCaseMgmt = (String) session.getAttribute("newCaseManagement");
    }

    String redirectURL;
    if ("true".equals(useNewCaseMgmt)) {
        redirectURL = request.getContextPath() +
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
    } else {
        redirectURL = request.getContextPath() + "/CaseManagementView.do";
    }
    response.sendRedirect(redirectURL);
%>
