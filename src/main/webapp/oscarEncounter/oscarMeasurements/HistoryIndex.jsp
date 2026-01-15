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

<%@page import="ca.openosp.openo.utility.WebUtils" %>
<%@page import="ca.openosp.openo.utility.LocaleUtils" %>
<%@page import="ca.openosp.openo.utility.WebUtils" %>
<%
    if (session.getValue("user") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");
%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>


<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ page import="ca.openosp.openo.encounter.pageUtil.*" %>
<%@ page import="ca.openosp.openo.encounter.oscarMeasurements.pageUtil.*" %>
<%@ page import="ca.openosp.openo.encounter.oscarMeasurements.bean.*" %>
<%@ page import="ca.openosp.openo.encounter.oscarMeasurements.data.*" %>
<%@ page import="java.util.Vector" %>
<%@ page import="ca.openosp.openo.encounter.pageUtil.EctSessionBean" %>
<%@ page import="ca.openosp.openo.encounter.oscarMeasurements.data.MeasurementMapConfig" %>
<%
    EctSessionBean bean = (EctSessionBean) request.getSession().getAttribute("EctSessionBean");
    MeasurementMapConfig measurementMapConfig = new MeasurementMapConfig();
%>

<html>

    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.oldMeasurements"/>
        </title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

    </head>


    <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/oscarEncounter/encounterStyles.css">
    <style type="text/css" media="print">
        .noprint {
            display: none;
        }

    </style>
    <body topmargin="0" leftmargin="0" vlink="#0000FF"
          onload="window.focus();">
    <% 
    java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute("actionErrors");
    if (actionErrors != null && !actionErrors.isEmpty()) {
%>
    <div class="action-errors">
        <ul>
            <% for (String error : actionErrors) { %>
                <li><%= error %></li>
            <% } %>
        </ul>
    </div>
<% } %>
    <%=WebUtils.popErrorAndInfoMessagesAsHtml(session)%>

    <div style="display:inline-block; text-align:center">
        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.oldmesurementindex"/>

        <table>
            <tr>
                <th align="left" class="Header" width="20"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.displayHistory.headingType"/>
                </th>
                <th align="left" class="Header" width="200"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.typedescription"/></th>
                <th align="left" class="Header" width="50"></th>
            </tr>
            <c:if test="${not empty measurementsData}">
                <c:forEach var="data" items="${measurementsData.measurementsDataVector}" varStatus="ctr">
                    <tr class="data">
                        <td width="20">${data.type}</td>
                        <td width="200">${data.typeDescription}</td>
                        <td width="50"><a href="#"
                                          name='<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.oldMeasurements"/>'
                                          onClick="popupPage(300,800,'oscarEncounter/oscarMeasurements/SetupDisplayHistory.do?type=${data.type}'); return false;">more...</a></td>
                    </tr>
                </c:forEach>
            </c:if>
        </table>

        <input type="button" name="Button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnPrint"/>" onClick="window.print()">
        <input type="button" name="Button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/>" onClick="window.close()">
        <c:if test="${not empty type}">
            <input type="hidden" name="type" value="${type}"/>
        </c:if>


    </div>

    </body>
</html>
